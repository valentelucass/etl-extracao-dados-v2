-- MAT-04 local: one synthetic title root, issue date is an attribute, never a second key.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.expansion_lab_materialization_receipt (
 receipt_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,kind VARCHAR(8) NOT NULL,
 reference_revision INT NOT NULL,mode VARCHAR(16) NOT NULL,full_scope BIT NOT NULL,
 window_start DATE NOT NULL,window_end_exclusive DATE NOT NULL,business_date DATE NOT NULL,
 candidates BIGINT NOT NULL,inserts BIGINT NOT NULL,updates BIGINT NOT NULL,noops BIGINT NOT NULL,
 ready BIGINT NOT NULL,blocked BIGINT NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_material_receipt_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT CK_exp_material_receipt CHECK(kind IN('INVOICE','REVENUE') AND reference_revision BETWEEN 1 AND 1000
 AND mode IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') AND window_start<window_end_exclusive
 AND candidates=inserts+updates+noops AND candidates=ready+blocked)
);
CREATE TABLE mart.expansion_lab_invoice (
 root_id BIGINT NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 issue_date DATE NULL,due_date DATE NULL,paid_date DATE NULL,base_date DATE NULL,monthly_reference_date DATE NULL,
 client_key NVARCHAR(1100) COLLATE Latin1_General_100_BIN2 NULL,client_provenance VARCHAR(32) NULL,
 has_invoice BIT NULL,process_state NVARCHAR(32) NULL,payment_state VARCHAR(24) NULL,days_past_due INT NULL,
 operational_value DECIMAL(28,8) NULL,currency CHAR(3) NOT NULL,unit VARCHAR(16) NOT NULL,
 disposition VARCHAR(32) NOT NULL,component_count BIGINT NOT NULL,document_count BIGINT NOT NULL,freight_count BIGINT NOT NULL,
 reference_revision INT NOT NULL,label_release BIGINT NULL,business_date DATE NOT NULL,last_receipt UNIQUEIDENTIFIER NOT NULL,
 CONSTRAINT FK_exp_invoice_root FOREIGN KEY(root_id) REFERENCES core.expansion_lab_root(root_id),
 CONSTRAINT FK_exp_invoice_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT FK_exp_invoice_labels FOREIGN KEY(label_release) REFERENCES ref.reference_release(reference_release_id),
 CONSTRAINT CK_exp_invoice_currency CHECK(unit='MAJOR' AND currency COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z]%'),
 CONSTRAINT CK_exp_invoice_state CHECK(disposition IN('READY','INACTIVE','FIELD_CONFLICT','FISCAL_UNRESOLVED','DEPENDENCY_MISSING','NULL_DATE','REFERENCE_MISSING','CLIENT_MISSING'))
);
CREATE INDEX IX_exp_invoice_partition ON mart.expansion_lab_invoice(run_id,issue_date,root_id) INCLUDE(disposition,operational_value,currency,unit);
CREATE TABLE mart.expansion_lab_invoice_history (
 receipt_id UNIQUEIDENTIFIER NOT NULL,root_id BIGINT NOT NULL,action VARCHAR(8) NOT NULL,
 old_issue_date DATE NULL,issue_date DATE NULL,old_value DECIMAL(28,8) NULL,operational_value DECIMAL(28,8) NULL,
 old_disposition VARCHAR(32) NULL,disposition VARCHAR(32) NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT PK_exp_invoice_history PRIMARY KEY(receipt_id,root_id),
 CONSTRAINT FK_exp_invoice_history_root FOREIGN KEY(root_id) REFERENCES mart.expansion_lab_invoice(root_id),
 CONSTRAINT FK_exp_invoice_history_receipt FOREIGN KEY(receipt_id) REFERENCES ctl.expansion_lab_materialization_receipt(receipt_id)
);
CREATE TABLE mart.expansion_lab_invoice_lineage (
 receipt_id UNIQUEIDENTIFIER NOT NULL,root_id BIGINT NOT NULL,component_id BIGINT NOT NULL,observation_id BIGINT NOT NULL,
 CONSTRAINT PK_exp_invoice_lineage PRIMARY KEY(receipt_id,root_id,component_id),
 CONSTRAINT FK_exp_invoice_lineage_receipt FOREIGN KEY(receipt_id) REFERENCES ctl.expansion_lab_materialization_receipt(receipt_id),
 CONSTRAINT FK_exp_invoice_lineage_obs FOREIGN KEY(observation_id) REFERENCES stg.expansion_lab_observation(observation_id)
);
GO
CREATE PROCEDURE mart.usp_materialize_expansion_invoices @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'MATERIALIZE';
 DECLARE @business_date DATE;
 SELECT @business_date=business_date FROM ctl.expansion_lab_run WHERE run_id=@run_id AND @start>=window_start AND @end<=window_end_exclusive;
 IF @business_date IS NULL OR @start>=@end OR @reference_revision NOT BETWEEN 1 AND 1000
 OR @mode NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') THROW 53450,N'EXP_INVOICE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='INVOICE'
   AND reference_revision=@reference_revision AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end AND business_date=@business_date)
  THROW 53451,N'EXP_MATERIALIZATION_RETRY_DIVERGENT',1;
  SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id;RETURN;
 END;
 SELECT r.root_id,r.currency,r.unit,r.active INTO #roots FROM core.expansion_lab_root r
 WHERE r.run_id=@run_id AND r.vertical='FAT' AND(@full=1
 OR EXISTS(SELECT 1 FROM stg.expansion_lab_observation o JOIN ctl.expansion_lab_capture c ON c.execution_id=o.execution_id
 WHERE o.run_id=r.run_id AND o.vertical=r.vertical AND o.root_type=r.root_type AND o.root_key=r.root_key AND c.state='COMPLETE' AND c.partition_start>=@start AND c.partition_start<@end)
 OR EXISTS(SELECT 1 FROM mart.expansion_lab_invoice prior WHERE prior.root_id=r.root_id AND prior.issue_date>=@start AND prior.issue_date<@end));
 SELECT f.* INTO #lines FROM pub.ufn_expansion_fat(@run_id,@reference_revision) f JOIN #roots r ON r.root_id=f.root_id;
 -- A title invariant must agree across every component, including explicit nulls.
 SELECT DISTINCT root_id,issue_date,due_date,paid_date,client_key COLLATE Latin1_General_100_BIN2 client_key,client_provenance,has_invoice,
 COALESCE(title_value,freight_value,CONVERT(DECIMAL(28,8),0)) proposed_value,label_release,
 COALESCE(issue_date,CONVERT(DATE,DATEADD(SECOND,CONVERT(INT,cte_issue_second%86400),DATEADD(DAY,CONVERT(INT,cte_issue_second/86400),CONVERT(DATETIME2,'19700101',112)))
 AT TIME ZONE 'UTC' AT TIME ZONE 'E. South America Standard Time')) monthly_reference_date
 INTO #variants FROM #lines;
 SELECT root_id,COUNT_BIG(*) variant_count,
 CASE WHEN COUNT_BIG(*)=1 THEN MIN(issue_date) END issue_date,CASE WHEN COUNT_BIG(*)=1 THEN MIN(due_date) END due_date,
 CASE WHEN COUNT_BIG(*)=1 THEN MIN(paid_date) END paid_date,CASE WHEN COUNT_BIG(*)=1 THEN MIN(monthly_reference_date) END monthly_reference_date,
 CASE WHEN COUNT_BIG(*)=1 THEN MIN(client_key) END client_key,CASE WHEN COUNT_BIG(*)=1 THEN MIN(client_provenance) END client_provenance,
 CASE WHEN COUNT_BIG(*)=1 THEN MIN(CONVERT(INT,has_invoice)) END has_invoice,
 CASE WHEN COUNT_BIG(*)=1 THEN MIN(proposed_value) END proposed_value,CASE WHEN COUNT_BIG(*)=1 THEN MIN(label_release) END label_release
 INTO #values FROM #variants GROUP BY root_id;
 SELECT root_id,COUNT_BIG(*) component_count,MAX(CONVERT(INT,unresolved_conflict)) conflict,
 MAX(CASE WHEN fiscal_state<>'RESOLVED' THEN 1 ELSE 0 END) fiscal_unresolved,
 MAX(CASE WHEN amount_state='CONFLICT' THEN 1 ELSE 0 END) amount_conflict
 INTO #flags FROM #lines GROUP BY root_id;
 SELECT l.root_id,COUNT(DISTINCT l.target_dependency_id) freight_count,COUNT_BIG(*) relation_count,
 MAX(CASE WHEN l.state<>'RESOLVED' THEN 1 ELSE 0 END) unresolved
 INTO #relationships FROM core.expansion_lab_link l JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id
 JOIN #roots r ON r.root_id=l.root_id WHERE l.run_id=@run_id AND b.kind='FAT_DOCUMENT_FREIGHT' AND l.state NOT IN('SUPERSEDED','INACTIVE') GROUP BY l.root_id;
 SELECT root_id,COUNT_BIG(*) document_count INTO #documents FROM(
 SELECT DISTINCT l.root_id,b.document_type,b.document_key FROM core.expansion_lab_link l JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id
 JOIN #roots r ON r.root_id=l.root_id WHERE l.run_id=@run_id AND b.kind='FAT_DOCUMENT_FREIGHT' AND l.state='RESOLVED') d GROUP BY root_id;
 SELECT r.root_id,@run_id run_id,v.issue_date,v.due_date,v.paid_date,v.issue_date base_date,v.monthly_reference_date,
 v.client_key,v.client_provenance,CONVERT(BIT,v.has_invoice) has_invoice,
 CONVERT(NVARCHAR(32),CASE v.has_invoice WHEN 1 THEN N'Faturado' WHEN 0 THEN N'Aguardando Faturamento' END) process_state,
 CONVERT(VARCHAR(24),CASE WHEN v.has_invoice=0 THEN 'sem_fatura' WHEN v.paid_date IS NOT NULL THEN 'baixado' WHEN v.due_date IS NULL THEN 'sem_vencimento'
 WHEN v.due_date<@business_date THEN 'vencido' ELSE 'a_vencer' END) payment_state,
 CASE WHEN v.has_invoice=1 AND v.paid_date IS NULL AND v.due_date IS NOT NULL THEN CASE WHEN v.due_date<@business_date THEN DATEDIFF(day,v.due_date,@business_date) ELSE 0 END END days_past_due,
 CASE WHEN r.active=0 THEN CONVERT(DECIMAL(28,8),0) ELSE v.proposed_value END operational_value,r.currency,r.unit,
 CONVERT(VARCHAR(32),CASE WHEN r.active=0 THEN 'INACTIVE' WHEN v.variant_count<>1 OR f.conflict=1 OR f.amount_conflict=1 THEN 'FIELD_CONFLICT'
 WHEN f.fiscal_unresolved=1 THEN 'FISCAL_UNRESOLVED'
 WHEN rel.root_id IS NULL OR rel.unresolved=1 OR EXISTS(SELECT 1 FROM #lines line WHERE line.root_id=r.root_id AND NOT EXISTS(
 SELECT 1 FROM core.expansion_lab_link link JOIN stg.expansion_lab_relation binding ON binding.relation_id=link.relation_id
 WHERE link.component_id=line.component_id AND link.state='RESOLVED' AND binding.kind='FAT_DOCUMENT_FREIGHT')) THEN 'DEPENDENCY_MISSING'
 WHEN v.issue_date IS NULL THEN 'NULL_DATE' WHEN v.label_release IS NULL THEN 'REFERENCE_MISSING' WHEN v.client_key IS NULL THEN 'CLIENT_MISSING' ELSE 'READY' END) disposition,
 COALESCE(f.component_count,0) component_count,COALESCE(d.document_count,0) document_count,COALESCE(rel.freight_count,0) freight_count,
 @reference_revision reference_revision,v.label_release,@business_date business_date
 INTO #result FROM #roots r LEFT JOIN #values v ON v.root_id=r.root_id LEFT JOIN #flags f ON f.root_id=r.root_id
 LEFT JOIN #relationships rel ON rel.root_id=r.root_id LEFT JOIN #documents d ON d.root_id=r.root_id;
 SELECT s.*,CONVERT(VARCHAR(8),CASE WHEN t.root_id IS NULL THEN 'INSERT' WHEN EXISTS(
 SELECT s.issue_date,s.due_date,s.paid_date,s.base_date,s.monthly_reference_date,s.client_key,s.client_provenance,s.has_invoice,s.process_state,s.payment_state,s.days_past_due,
 s.operational_value,s.disposition,s.component_count,s.document_count,s.freight_count,s.reference_revision,s.label_release,s.business_date
 EXCEPT SELECT t.issue_date,t.due_date,t.paid_date,t.base_date,t.monthly_reference_date,t.client_key,t.client_provenance,t.has_invoice,t.process_state,t.payment_state,t.days_past_due,
 t.operational_value,t.disposition,t.component_count,t.document_count,t.freight_count,t.reference_revision,t.label_release,t.business_date) THEN 'UPDATE' ELSE 'NOOP' END) action,
 t.issue_date old_issue_date,t.operational_value old_value,t.disposition old_disposition
 INTO #decisions FROM #result s LEFT JOIN mart.expansion_lab_invoice t ON t.root_id=s.root_id;
 INSERT ctl.expansion_lab_materialization_receipt
 SELECT @receipt_id,@run_id,'INVOICE',@reference_revision,@mode,@full,@start,@end,@business_date,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='READY' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition<>'READY' THEN 1 ELSE 0 END)),0),@now FROM #decisions;
 INSERT mart.expansion_lab_invoice
 SELECT root_id,run_id,issue_date,due_date,paid_date,base_date,monthly_reference_date,client_key,client_provenance,has_invoice,process_state,payment_state,days_past_due,
 operational_value,currency,unit,disposition,component_count,document_count,freight_count,reference_revision,label_release,business_date,@receipt_id FROM #decisions WHERE action='INSERT';
 UPDATE t SET issue_date=s.issue_date,due_date=s.due_date,paid_date=s.paid_date,base_date=s.base_date,monthly_reference_date=s.monthly_reference_date,
 client_key=s.client_key,client_provenance=s.client_provenance,has_invoice=s.has_invoice,process_state=s.process_state,payment_state=s.payment_state,days_past_due=s.days_past_due,
 operational_value=s.operational_value,disposition=s.disposition,component_count=s.component_count,document_count=s.document_count,freight_count=s.freight_count,
 reference_revision=s.reference_revision,label_release=s.label_release,business_date=s.business_date,last_receipt=@receipt_id
 FROM mart.expansion_lab_invoice t JOIN #decisions s ON s.root_id=t.root_id WHERE s.action='UPDATE';
 INSERT mart.expansion_lab_invoice_history SELECT @receipt_id,root_id,action,old_issue_date,issue_date,old_value,operational_value,old_disposition,disposition,@now FROM #decisions WHERE action IN('INSERT','UPDATE');
 INSERT mart.expansion_lab_invoice_lineage SELECT @receipt_id,root_id,component_id,observation_id FROM #lines;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id;
END;
GO

-- Correct MAT-03 typed text comparison across reference/term collations; preserve exact bytes.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
ALTER PROCEDURE mart.usp_materialize_expansion_revenue @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'MATERIALIZE';
 DECLARE @business_date DATE;
 SELECT @business_date=business_date FROM ctl.expansion_lab_run WHERE run_id=@run_id AND @start>=window_start AND @end<=window_end_exclusive;
 IF @business_date IS NULL OR @start>=@end OR @reference_revision NOT BETWEEN 1 AND 1000
 OR @mode NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY') THROW 53470,N'EXP_REVENUE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='REVENUE'
   AND reference_revision=@reference_revision AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end AND business_date=@business_date)
  THROW 53451,N'EXP_MATERIALIZATION_RETRY_DIVERGENT',1;
  SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id;RETURN;
 END;
 SELECT d.* INTO #roots FROM core.expansion_lab_dependency d WHERE d.run_id=@run_id AND d.entity='FRETE' AND(@full=1
 OR EXISTS(SELECT 1 FROM stg.expansion_lab_dependency_observation o JOIN ctl.expansion_lab_dependency_capture c ON c.execution_id=o.execution_id
 WHERE o.run_id=d.run_id AND o.entity=d.entity AND o.source_key=d.source_key AND c.state='COMPLETE' AND c.partition_date>=@start AND c.partition_date<@end)
 OR EXISTS(SELECT 1 FROM mart.expansion_lab_revenue prior WHERE prior.dependency_id=d.dependency_id AND prior.original_reference_date>=@start AND prior.original_reference_date<@end));
 SELECT l.target_dependency_id dependency_id,f.*,b.document_type,b.document_key INTO #lines
 FROM core.expansion_lab_link l JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id
 JOIN pub.ufn_expansion_fat(@run_id,@reference_revision) f ON f.component_id=l.component_id
 JOIN #roots d ON d.dependency_id=l.target_dependency_id WHERE l.run_id=@run_id AND l.state='RESOLVED' AND b.kind='FAT_DOCUMENT_FREIGHT';
 SELECT dependency_id,COUNT(DISTINCT root_id) invoice_count,
 MAX(CONVERT(INT,unresolved_conflict)) conflict,MAX(CASE WHEN fiscal_state<>'RESOLVED' THEN 1 ELSE 0 END) fiscal_unresolved,
 MAX(CASE WHEN cte_number IS NOT NULL THEN 1 ELSE 0 END) has_cte,
 MAX(CASE WHEN CONCAT(cte_status_raw,N' ',cte_status_result) COLLATE Latin1_General_100_CI_AI LIKE N'%cancelad%'
 OR CONCAT(cte_status_raw,N' ',cte_status_result) COLLATE Latin1_General_100_CI_AI LIKE N'%cancell%' THEN 1 ELSE 0 END) cancelled
 INTO #fat FROM #lines GROUP BY dependency_id;
 SELECT dependency_id,COUNT_BIG(*) document_count INTO #docs FROM(SELECT DISTINCT dependency_id,root_id,document_type,document_key FROM #lines) d GROUP BY dependency_id;
 SELECT l.target_dependency_id dependency_id,COUNT(DISTINCT loc.dependency_id) location_count,
 CASE WHEN COUNT(DISTINCT loc.dependency_id)=1 THEN MIN(loc.stage_record_id) END stage_record_id,
 MAX(CASE WHEN loc.state<>'VALID' THEN 1 ELSE 0 END) conflict
 INTO #locations FROM core.expansion_lab_link l JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id
 JOIN core.expansion_lab_dependency loc ON loc.dependency_id=l.source_dependency_id JOIN #roots d ON d.dependency_id=l.target_dependency_id
 WHERE l.run_id=@run_id AND b.kind='LOC_FREIGHT' AND l.state IN('RESOLVED','CONFLICT') GROUP BY l.target_dependency_id;
 SELECT d.dependency_id,d.run_id,d.source_key,d.stage_record_id freight_stage_id,t.term_id,t.revision term_revision,t.billing_reference_date original_reference_date,
 calendar.billing_reference_date,assignment.branch_code,cal.reference_release_id calendar_release,branch.reference_release_id branch_release,payer.reference_release_id payer_release,
 TRY_CONVERT(DECIMAL(28,8),JSON_VALUE(freight.financial_json,'$.total.typedDecimal')) source_value,t.currency,t.unit,
 CONVERT(BIT,CASE WHEN fat.cancelled=1 OR(fat.has_cte=1 AND freight.status_code IN(N'canceled',N'cancelled')) THEN 1 ELSE 0 END) cancelled,
 CONVERT(VARCHAR(32),CASE WHEN fat.cancelled=1 AND fat.has_cte=1 AND freight.status_code IN(N'canceled',N'cancelled') THEN 'FAT_AND_FRETE_CTE'
 WHEN fat.cancelled=1 THEN 'FAT_STATUS_OR_RESULT' WHEN fat.has_cte=1 AND freight.status_code IN(N'canceled',N'cancelled') THEN 'FRETE_WITH_EXPLICIT_CTE' ELSE 'NONE' END) cancellation_provenance,
 CONVERT(BIT,CASE WHEN t.classification COLLATE Latin1_General_100_CI_AI LIKE N'%bloqueio%'
 AND(t.classification COLLATE Latin1_General_100_CI_AI LIKE N'%anulacao%' OR t.classification COLLATE Latin1_General_100_CI_AI LIKE N'%isolamento%') THEN 1 ELSE 0 END) billing_block,
 t.courtesy,t.eligible,t.active,COALESCE(location.invoices_volumes_typed,t.fallback_volumes) volumes,
 CONVERT(VARCHAR(32),CASE WHEN location.invoices_volumes_typed IS NOT NULL THEN 'LOCALIZACAO_CAPTURE'
 WHEN t.fallback_volumes IS NOT NULL THEN 'FRETE_SYNTHETIC_TERMS' ELSE 'MISSING' END) volume_provenance,loc.stage_record_id location_stage_id,
 COALESCE(fat.invoice_count,0) invoice_count,COALESCE(docs.document_count,0) document_count,
 t.terms_state,d.state dependency_state,fat.conflict fat_conflict,fat.fiscal_unresolved,loc.conflict loc_conflict,loc.location_count,
 @reference_revision reference_revision,@business_date business_date
 INTO #input FROM #roots d JOIN stg.frete_record freight ON freight.stage_record_id=d.stage_record_id
 LEFT JOIN core.ufn_expansion_freight_terms(@run_id) t ON t.source_key=d.source_key
 LEFT JOIN #fat fat ON fat.dependency_id=d.dependency_id LEFT JOIN #docs docs ON docs.dependency_id=d.dependency_id
 LEFT JOIN #locations loc ON loc.dependency_id=d.dependency_id LEFT JOIN stg.localizacao_carga_record location ON location.stage_record_id=loc.stage_record_id
 OUTER APPLY ref.ufn_expansion_reference(@run_id,@reference_revision,'CALENDAR',t.billing_reference_date) cal
 LEFT JOIN ref.calendario calendar ON calendar.reference_release_id=cal.reference_release_id AND calendar.calendar_date=t.billing_reference_date
 OUTER APPLY ref.ufn_expansion_reference(@run_id,@reference_revision,'BRANCH',t.billing_reference_date) branch
 OUTER APPLY ref.ufn_expansion_reference(@run_id,@reference_revision,'PAYER',t.billing_reference_date) payer
 LEFT JOIN ref.atribuicao_filial assignment ON assignment.reference_release_id=payer.reference_release_id
 AND assignment.payer_document_token=t.payer_token AND assignment.token_scheme_version=N'synthetic-expansion-token-v1'
 AND assignment.branch_reference_release_id=branch.reference_release_id AND assignment.valid_from<=t.billing_reference_date AND assignment.valid_to_exclusive>t.billing_reference_date;
 SELECT i.*,CONVERT(VARCHAR(32),CASE WHEN terms_state IS NULL THEN 'TERMS_MISSING' WHEN terms_state='CONFLICT' OR dependency_state<>'VALID' OR fat_conflict=1 THEN 'DATA_CONFLICT'
 WHEN invoice_count=0 THEN 'DEPENDENCY_MISSING' WHEN fiscal_unresolved=1 THEN 'FISCAL_UNRESOLVED'
 WHEN loc_conflict=1 OR location_count>1 THEN 'LOCATION_CONFLICT'
 WHEN original_reference_date IS NULL THEN 'NULL_DATE' WHEN active=0 THEN 'INACTIVE' WHEN cancelled=1 THEN 'CANCELLED'
 WHEN courtesy=1 THEN 'COURTESY' WHEN billing_block=1 THEN 'BILLING_BLOCK'
 WHEN eligible IS NULL OR courtesy IS NULL THEN 'POLICY_MISSING' WHEN eligible=0 THEN 'INELIGIBLE'
 WHEN calendar_release IS NULL OR billing_reference_date IS NULL OR payer_release IS NULL OR branch_release IS NULL THEN 'REFERENCE_MISSING'
 WHEN branch_code IS NULL THEN 'PAYER_UNMAPPED' WHEN source_value IS NULL THEN 'VALUE_MISSING' ELSE 'READY' END) disposition
 INTO #classified FROM #input i;
 SELECT c.*,CONVERT(DECIMAL(28,8),CASE WHEN terms_state IS NULL OR terms_state='CONFLICT' OR dependency_state<>'VALID' THEN NULL
 WHEN original_reference_date IS NULL OR active=0 OR cancelled=1 OR courtesy=1 OR billing_block=1 OR eligible=0 THEN 0
 WHEN disposition<>'READY' THEN NULL ELSE source_value END) revenue_value INTO #result FROM #classified c;
 SELECT s.*,CONVERT(VARCHAR(8),CASE WHEN t.dependency_id IS NULL THEN 'INSERT' WHEN EXISTS(
 SELECT s.freight_stage_id,s.term_id,s.term_revision,s.original_reference_date,s.billing_reference_date,CONVERT(VARBINARY(256),s.branch_code),s.calendar_release,s.branch_release,s.payer_release,s.source_value,s.revenue_value,CONVERT(VARBINARY(256),s.currency),CONVERT(VARBINARY(256),s.unit),s.cancelled,CONVERT(VARBINARY(256),s.cancellation_provenance),s.billing_block,s.courtesy,s.eligible,s.active,s.volumes,CONVERT(VARBINARY(256),s.volume_provenance),s.location_stage_id,CONVERT(VARBINARY(256),s.disposition),s.invoice_count,s.document_count,s.reference_revision,s.business_date EXCEPT SELECT t.freight_stage_id,t.term_id,t.term_revision,t.original_reference_date,t.billing_reference_date,CONVERT(VARBINARY(256),t.branch_code),t.calendar_release,t.branch_release,t.payer_release,t.source_value,t.revenue_value,CONVERT(VARBINARY(256),t.currency),CONVERT(VARBINARY(256),t.unit),t.cancelled,CONVERT(VARBINARY(256),t.cancellation_provenance),t.billing_block,t.courtesy,t.eligible,t.active,t.volumes,CONVERT(VARBINARY(256),t.volume_provenance),t.location_stage_id,CONVERT(VARBINARY(256),t.disposition),t.invoice_count,t.document_count,t.reference_revision,t.business_date) THEN 'UPDATE' ELSE 'NOOP' END) action,
 t.original_reference_date old_reference_date,t.revenue_value old_value,t.disposition old_disposition INTO #decisions FROM #result s LEFT JOIN mart.expansion_lab_revenue t ON t.dependency_id=s.dependency_id;
 INSERT ctl.expansion_lab_materialization_receipt SELECT @receipt_id,@run_id,'REVENUE',@reference_revision,@mode,@full,@start,@end,@business_date,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='READY' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition<>'READY' THEN 1 ELSE 0 END)),0),@now FROM #decisions;
 INSERT mart.expansion_lab_revenue SELECT dependency_id,run_id,source_key,freight_stage_id,term_id,term_revision,original_reference_date,billing_reference_date,branch_code,calendar_release,branch_release,payer_release,source_value,revenue_value,currency,unit,cancelled,cancellation_provenance,billing_block,courtesy,eligible,active,volumes,volume_provenance,location_stage_id,disposition,invoice_count,document_count,reference_revision,business_date,@receipt_id FROM #decisions WHERE action='INSERT';
 UPDATE t SET freight_stage_id=s.freight_stage_id,term_id=s.term_id,term_revision=s.term_revision,original_reference_date=s.original_reference_date,billing_reference_date=s.billing_reference_date,branch_code=s.branch_code,calendar_release=s.calendar_release,branch_release=s.branch_release,payer_release=s.payer_release,source_value=s.source_value,revenue_value=s.revenue_value,currency=s.currency,unit=s.unit,cancelled=s.cancelled,cancellation_provenance=s.cancellation_provenance,billing_block=s.billing_block,courtesy=s.courtesy,eligible=s.eligible,active=s.active,volumes=s.volumes,volume_provenance=s.volume_provenance,location_stage_id=s.location_stage_id,disposition=s.disposition,invoice_count=s.invoice_count,document_count=s.document_count,reference_revision=s.reference_revision,business_date=s.business_date,last_receipt=@receipt_id FROM mart.expansion_lab_revenue t JOIN #decisions s ON s.dependency_id=t.dependency_id WHERE s.action='UPDATE';
 INSERT mart.expansion_lab_revenue_history SELECT @receipt_id,dependency_id,action,old_reference_date,original_reference_date,old_value,revenue_value,old_disposition,disposition,@now FROM #decisions WHERE action IN('INSERT','UPDATE');
 INSERT mart.expansion_lab_revenue_lineage SELECT DISTINCT @receipt_id,dependency_id,component_id,observation_id FROM #lines;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.expansion_lab_materialization_receipt WHERE receipt_id=@receipt_id;
END;
GO

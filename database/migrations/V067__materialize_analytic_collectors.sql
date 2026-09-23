-- MAT02: canonical contributions first, then affected daily branches, preserving prior partitions.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE mart.analytic_collector_contribution_observation(
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,
 receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_materialization_receipt(receipt_id),
 run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_run(run_id),
 entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contribution VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NULL,
 manifest_snapshot_id BIGINT NULL,
 inventory_observation_id BIGINT NULL,
 reference_revision INT NOT NULL,
 reference_release_id BIGINT NULL,
 source_state_id BIGINT NULL,
 reference_date DATE NULL,
 branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 branch_binding_id BIGINT NULL,
 branch_provenance VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fallback_dependency_id BIGINT NULL,
 quantity BIGINT NOT NULL,
 incomplete BIGINT NOT NULL,
 disposition VARCHAR(40) COLLATE Latin1_General_100_BIN2 NOT NULL,
 extracted_at DATETIME2(7) NOT NULL,
 action VARCHAR(8) NOT NULL,prior_observation_id BIGINT NULL,
 old_reference_date DATE NULL,old_branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT UQ_analytic_collector_contribution UNIQUE(receipt_id,entity,source_key,contribution),
 CONSTRAINT UQ_analytic_collector_pointer UNIQUE(run_id,entity,source_key,contribution,observation_id),
 CONSTRAINT FK_analytic_collector_manifest FOREIGN KEY(manifest_snapshot_id) REFERENCES core.analytic_manifest_snapshot(snapshot_id),
 CONSTRAINT FK_analytic_collector_inventory FOREIGN KEY(inventory_observation_id) REFERENCES stg.expansion_lab_observation(observation_id),
 CONSTRAINT FK_analytic_collector_binding FOREIGN KEY(branch_binding_id) REFERENCES ref.analytic_lab_dimension_binding(binding_id),
 CONSTRAINT CK_analytic_collector_contribution CHECK(entity IN('MAN','INV') AND contribution IN('ISSUED','UNLOADED','SCANNED')
 AND quantity IN(0,1) AND incomplete BETWEEN 0 AND quantity AND action IN('INSERT','UPDATE','NOOP')
 AND(quantity=0 OR(disposition='READY' AND reference_date IS NOT NULL AND branch_key IS NOT NULL)))
);
CREATE INDEX IX_analytic_collector_contribution_receipt ON mart.analytic_collector_contribution_observation(receipt_id,entity,source_key,contribution);
GO
CREATE TABLE mart.analytic_collector_contribution(
 run_id UNIQUEIDENTIFIER NOT NULL,entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,contribution VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
 observation_id BIGINT NOT NULL,reference_date DATE NULL,branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 CONSTRAINT PK_analytic_collector_contribution PRIMARY KEY(run_id,entity,source_key,contribution),
 CONSTRAINT FK_analytic_collector_current FOREIGN KEY(run_id,entity,source_key,contribution,observation_id)
 REFERENCES mart.analytic_collector_contribution_observation(run_id,entity,source_key,contribution,observation_id)
);
CREATE INDEX IX_analytic_collector_contribution_partition ON mart.analytic_collector_contribution(run_id,reference_date,branch_key) INCLUDE(observation_id);
GO
CREATE TABLE mart.analytic_collector_daily_observation(
 observation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 receipt_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.analytic_lab_materialization_receipt(receipt_id),
 reference_date DATE NOT NULL,branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 classification NVARCHAR(16) NOT NULL,issued BIGINT NOT NULL,unloaded BIGINT NOT NULL,scanned BIGINT NOT NULL,
 incomplete BIGINT NOT NULL,total BIGINT NOT NULL,percentage DECIMAL(28,8) NOT NULL,has_inventory BIT NOT NULL,
 active BIT NOT NULL,reference_revision INT NOT NULL,reference_release_id BIGINT NULL,
 CONSTRAINT UQ_analytic_collector_daily_observation UNIQUE(receipt_id,reference_date,branch_key),
 CONSTRAINT UQ_analytic_collector_daily_pointer UNIQUE(run_id,reference_date,branch_key,observation_id),
 CONSTRAINT CK_analytic_collector_daily CHECK(classification=N'Geral' AND issued>=0 AND unloaded>=0 AND scanned>=0
 AND incomplete BETWEEN 0 AND scanned AND total=issued+unloaded AND percentage>=0)
);
GO
CREATE TABLE mart.analytic_collector_daily(
 run_id UNIQUEIDENTIFIER NOT NULL,reference_date DATE NOT NULL,branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 observation_id BIGINT NOT NULL,CONSTRAINT PK_analytic_collector_daily PRIMARY KEY(run_id,reference_date,branch_key),
 CONSTRAINT FK_analytic_collector_daily_current FOREIGN KEY(run_id,reference_date,branch_key,observation_id)
 REFERENCES mart.analytic_collector_daily_observation(run_id,reference_date,branch_key,observation_id)
);
GO
CREATE TRIGGER mart.trg_analytic_collector_contribution_observation ON mart.analytic_collector_contribution_observation AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53620,N'ANA_COLLECTOR_OBSERVATION_IMMUTABLE',1; END;
GO
CREATE TRIGGER mart.trg_analytic_collector_daily_observation ON mart.analytic_collector_daily_observation AFTER UPDATE,DELETE
AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM deleted) THROW 53620,N'ANA_COLLECTOR_OBSERVATION_IMMUTABLE',1; END;
GO
CREATE VIEW pub.analytic_lab_collectors AS
 SELECT o.* FROM mart.analytic_collector_daily c JOIN mart.analytic_collector_daily_observation o ON o.observation_id=c.observation_id WHERE o.active=1;
GO
CREATE PROCEDURE mart.usp_materialize_analytic_collectors @run_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,
 @reference_revision INT,@mode VARCHAR(16),@full BIT,@start DATE,@end DATE,@now DATETIME2(7)
AS BEGIN SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT02';
 DECLARE @exp UNIQUEIDENTIFIER,@rel UNIQUEIDENTIFIER,@zone SYSNAME,@max INT,@run_created DATETIME2(7);
 SELECT @exp=g.expansion_run,@rel=g.relational_run,@zone=CASE r.zone_id WHEN 'UTC' THEN 'UTC' ELSE 'E. South America Standard Time' END,@max=r.maximum_rows,@run_created=r.created_at
 FROM ctl.analytic_lab_run r JOIN ctl.analytic_lab_source_group g ON g.run_id=r.run_id
 WHERE r.run_id=@run_id AND @start>=r.window_start AND @end<=r.window_end_exclusive
 AND(@full=0 OR(@start=r.window_start AND @end=r.window_end_exclusive));
 IF @exp IS NULL OR @receipt_id IS NULL OR @now IS NULL OR @start IS NULL OR @end IS NULL OR @start>=@end
 OR ISNULL(@reference_revision,0) NOT BETWEEN 1 AND 100000 OR ISNULL(@mode,'') NOT IN('BOOTSTRAP','INCREMENTAL','BACKFILL','REPLAY')
 OR @full IS NULL THROW 53621,N'ANA_COLLECTORS_SCOPE',1;
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT05';
 EXEC ctl.usp_expansion_lab_lock @exp,'RELATION';
 EXEC ctl.usp_relational_lab_lock @rel;
 EXEC ctl.usp_analytic_lab_lock @run_id,'DIMENSION';
 IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos')
 OR NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c WHERE c.run_id=@exp AND c.vertical='INV' AND c.state='COMPLETE')
 THROW 53622,N'ANA_COLLECTORS_INPUT_CAPTURE_MISSING',1;
 -- Every selected manifest capture must have completed analytical preparation, including empty captures.
 IF EXISTS(SELECT 1 FROM ctl.relational_lab_capture c WHERE c.run_id=@rel AND c.entity_name=N'manifestos'
 AND(@full=1 OR(c.business_date>=@start AND c.business_date<@end))
 AND NOT EXISTS(SELECT 1 FROM ctl.analytic_manifest_preparation p WHERE p.run_id=@run_id AND p.execution_id=c.execution_id))
 THROW 53623,N'ANA_COLLECTORS_MANIFEST_PREPARATION_MISSING',1;
 SELECT m.*,CONVERT(DATE,m.created_at AT TIME ZONE @zone) business_date INTO #manifestos
 FROM core.analytic_lab_manifest_projection m WHERE m.run_id=@run_id AND(@full=1
 OR EXISTS(SELECT 1 FROM ctl.relational_lab_capture c JOIN stg.manifesto_observation o ON o.execution_id=c.execution_id
 WHERE c.run_id=@rel AND c.entity_name=N'manifestos' AND o.source_key COLLATE Latin1_General_100_BIN2=m.source_key AND c.business_date>=@start AND c.business_date<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_collector_contribution p WHERE p.run_id=@run_id AND p.entity='MAN' AND p.source_key=m.source_key AND p.reference_date>=@start AND p.reference_date<@end));
 -- INV common root attributes are reduced only inside one current freshness/revision cohort.
 SELECT i.*,DENSE_RANK() OVER(PARTITION BY i.root_id ORDER BY i.fresh_second DESC,i.fresh_nano DESC,i.fresh_inclusive DESC,i.revision DESC) cohort_rank
 INTO #inventory_all FROM core.expansion_lab_current_input i WHERE i.run_id=@exp AND i.vertical='INV';
 SELECT i.root_id,MIN(i.observation_id) observation_id,MAX(CONVERT(INT,i.unresolved_conflict)) source_conflict,
 CASE WHEN COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.started_at_p,':',f.started_at_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.finished_at_p,':',f.finished_at_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.type_p,':',f.type_raw)))>1
 OR COUNT(DISTINCT CONVERT(VARBINARY(8000),CONCAT(f.cnr_crn_psn_nickname_p,':',f.cnr_crn_psn_nickname_raw)))>1 THEN 1 ELSE 0 END attribute_conflict
 INTO #inventory_cohort FROM #inventory_all i JOIN stg.expansion_lab_inv f ON f.execution_id=i.execution_id AND f.occurrence=i.occurrence
 WHERE i.cohort_rank=1 GROUP BY i.root_id;
 SELECT r.root_id,CONVERT(NVARCHAR(256),CONCAT(r.root_type,':',r.root_key)) COLLATE Latin1_General_100_BIN2 source_key,
 o.execution_id,o.observation_id,c.attribute_conflict,c.source_conflict,r.active,
 f.type,f.finished_at,f.finished_at_p,f.cnr_crn_psn_nickname,
 CONVERT(DATE,core.ufn_analytic_iso_time(f.started_at_raw) AT TIME ZONE @zone) business_date,
 capture.sealed_at extracted_at
 INTO #inventories FROM core.expansion_lab_root r
 LEFT JOIN #inventory_cohort c ON c.root_id=r.root_id
 LEFT JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 LEFT JOIN stg.expansion_lab_inv f ON f.execution_id=o.execution_id AND f.occurrence=o.occurrence
 LEFT JOIN ctl.expansion_lab_capture capture ON capture.execution_id=o.execution_id
 WHERE r.run_id=@exp AND r.vertical='INV' AND(@full=1 OR EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c2
 JOIN stg.expansion_lab_observation o2 ON o2.execution_id=c2.execution_id WHERE c2.run_id=@exp AND o2.root_type=r.root_type AND o2.root_key=r.root_key AND o2.vertical='INV'
 AND c2.state='COMPLETE' AND c2.partition_start>=@start AND c2.partition_start<@end)
 OR EXISTS(SELECT 1 FROM mart.analytic_collector_contribution p WHERE p.run_id=@run_id AND p.entity='INV' AND p.source_key=CONCAT(r.root_type,':',r.root_key) COLLATE Latin1_General_100_BIN2 AND p.reference_date>=@start AND p.reference_date<@end));
 IF (SELECT COUNT_BIG(*) FROM #manifestos)+(SELECT COUNT_BIG(*) FROM #inventories)>@max THROW 53624,N'ANA_COLLECTORS_ROOT_BOUND',1;
 CREATE TABLE #candidate(entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contribution VARCHAR(12) COLLATE Latin1_General_100_BIN2 NOT NULL,
 source_execution UNIQUEIDENTIFIER NULL,
 manifest_snapshot_id BIGINT NULL,
 inventory_observation_id BIGINT NULL,
 reference_revision INT NOT NULL,
 reference_release_id BIGINT NULL,
 source_state_id BIGINT NULL,
 reference_date DATE NULL,
 branch_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
 branch_binding_id BIGINT NULL,
 branch_provenance VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
 fallback_dependency_id BIGINT NULL,
 quantity BIGINT NOT NULL,
 incomplete BIGINT NOT NULL,
 disposition VARCHAR(40) COLLATE Latin1_General_100_BIN2 NOT NULL,
 extracted_at DATETIME2(7) NOT NULL);
 INSERT #candidate
 SELECT 'MAN',m.source_key,k.contribution,m.execution_id,m.snapshot_id,NULL,@reference_revision,selected.reference_release_id,state.state_id,
 m.business_date,branch.entity_key,branch.binding_id,
 CASE k.contribution WHEN 'ISSUED' THEN 'MANIFEST_ISSUER_BINDING' ELSE 'EXPLICIT_UNLOADING_BINDING' END,NULL,
 CASE WHEN reason.disposition='READY' THEN 1 ELSE 0 END,0,reason.disposition,m.extracted_at
 FROM #manifestos m CROSS APPLY(VALUES('ISSUED','BRANCH'),('UNLOADED','UNLOADING_BRANCH')) k(contribution,role)
 LEFT JOIN ref.analytic_lab_manifest_state_current state ON state.run_id=@run_id AND state.source_key=m.source_key
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(m.business_date,@start)) selected
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(m.business_date,@start)) d
 WHERE d.entity='MAN' AND d.source_key=m.source_key AND d.role=k.role COLLATE Latin1_General_100_BIN2) branch
 CROSS APPLY(SELECT CONVERT(VARCHAR(40),CASE WHEN m.disposition<>'READY' THEN m.disposition WHEN state.state_id IS NULL THEN 'SOURCE_STATE_MISSING'
 WHEN state.active=0 THEN 'SOURCE_INACTIVE' WHEN m.business_date IS NULL THEN 'CREATED_DATE_MISSING'
 WHEN selected.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN selected.branch_policy<>'EXPLICIT_ASSIGNMENT' THEN 'BRANCH_UNRESOLVED'
 WHEN EXISTS(SELECT 1 FROM ref.analytic_lab_exclusion x WHERE x.reference_release_id=selected.reference_release_id AND x.kind='OPERATION_PREFIX'
 AND LEFT(LTRIM(m.mft_man_name),LEN(x.match_value)) COLLATE Latin1_General_100_CI_AI=x.match_value COLLATE Latin1_General_100_CI_AI) THEN 'OPERATION_EXCLUDED'
 WHEN k.contribution='UNLOADED' AND NOT EXISTS(SELECT 1 FROM OPENJSON(COALESCE(m.mft_mte_unloading_recipient_names,N'[]')) j WHERE NULLIF(LTRIM(RTRIM(j.value)),N'') IS NOT NULL) THEN 'NO_UNLOADING_SOURCE'
 WHEN branch.binding_id IS NULL THEN 'BRANCH_BINDING_MISSING' WHEN branch.disposition<>'RESOLVED' THEN branch.disposition ELSE 'READY' END) disposition) reason;
 SELECT i.root_id,COUNT(DISTINCT l.target_dependency_id) targets,
 CASE WHEN COUNT(DISTINCT l.target_dependency_id)=1 THEN MIN(l.target_dependency_id) END target_dependency_id,
 MAX(CASE WHEN l.state<>'RESOLVED' THEN 1 ELSE 0 END) conflict INTO #fallback
 FROM #inventories i JOIN core.expansion_lab_link l ON l.run_id=@exp AND l.root_id=i.root_id
 JOIN stg.expansion_lab_relation b ON b.relation_id=l.relation_id AND b.kind='INV_FREIGHT' AND b.active=1
 WHERE l.state NOT IN('INACTIVE','SUPERSEDED') GROUP BY i.root_id;
 INSERT #candidate
 SELECT 'INV',i.source_key,'SCANNED',i.execution_id,NULL,i.observation_id,@reference_revision,selected.reference_release_id,NULL,
 i.business_date,CASE WHEN direct.binding_id IS NOT NULL THEN direct.entity_key ELSE freight.entity_key END,
 CASE WHEN direct.binding_id IS NOT NULL THEN direct.binding_id ELSE freight.binding_id END,
 CASE WHEN direct.binding_id IS NOT NULL THEN 'INVENTORY_BINDING' ELSE 'EXPLICIT_FREIGHT_FALLBACK' END,
 CASE WHEN direct.binding_id IS NULL THEN links.target_dependency_id END,
 CASE WHEN reason.disposition='READY' THEN 1 ELSE 0 END,
 CASE WHEN reason.disposition='READY' AND i.finished_at IS NULL THEN 1 ELSE 0 END,reason.disposition,COALESCE(i.extracted_at,@run_created)
 FROM #inventories i LEFT JOIN #fallback links ON links.root_id=i.root_id
 LEFT JOIN core.expansion_lab_dependency f ON f.dependency_id=links.target_dependency_id
 OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,COALESCE(i.business_date,@start)) selected
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.business_date,@start)) d WHERE d.entity='INV' AND d.source_key=i.source_key AND d.role='BRANCH') direct
 OUTER APPLY(SELECT * FROM ref.ufn_analytic_dimension(@run_id,@reference_revision,COALESCE(i.business_date,@start)) d WHERE d.entity='FRETE' AND d.source_key=f.source_key AND d.role='BRANCH') freight
 CROSS APPLY(SELECT CONVERT(VARCHAR(40),CASE WHEN i.active=0 THEN 'SOURCE_INACTIVE' WHEN i.source_conflict=1 OR i.attribute_conflict=1 THEN 'INVENTORY_ROOT_CONFLICT'
 WHEN i.observation_id IS NULL THEN 'INVENTORY_SOURCE_MISSING' WHEN i.business_date IS NULL THEN 'STARTED_DATE_MISSING'
 WHEN selected.reference_release_id IS NULL THEN 'REFERENCE_MISSING' WHEN selected.branch_policy<>'EXPLICIT_ASSIGNMENT' THEN 'BRANCH_UNRESOLVED'
 WHEN i.type IS NULL OR NOT EXISTS(SELECT 1 FROM ref.analytic_lab_label label WHERE label.reference_release_id=selected.reference_release_id
 AND label.category='INV_TYPE' AND label.raw_value=CASE WHEN LEFT(i.type,16)=N'CheckIn::Order::' THEN SUBSTRING(i.type,17,1024) ELSE i.type END COLLATE Latin1_General_100_BIN2
 AND label.raw_value IN(N'Picking',N'Return',N'Receipt',N'Loading',N'Unloading')) THEN 'TYPE_EXCLUDED'
 WHEN direct.binding_id IS NOT NULL AND direct.disposition<>'RESOLVED' THEN direct.disposition
 WHEN direct.binding_id IS NULL AND NULLIF(LTRIM(RTRIM(i.cnr_crn_psn_nickname)),N'') IS NOT NULL THEN 'BRANCH_BINDING_MISSING'
 WHEN direct.binding_id IS NULL AND(COALESCE(links.targets,0)<>1 OR links.conflict=1 OR f.state<>'VALID') THEN 'FREIGHT_FALLBACK_AMBIGUOUS'
 WHEN direct.binding_id IS NULL AND freight.binding_id IS NULL THEN 'FREIGHT_BRANCH_MISSING'
 WHEN direct.binding_id IS NULL AND freight.disposition<>'RESOLVED' THEN freight.disposition ELSE 'READY' END) disposition) reason;
 IF EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id)
 BEGIN
 IF NOT EXISTS(SELECT 1 FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id AND run_id=@run_id AND kind='MAT02'
 AND reference_revision=@reference_revision AND mode=@mode AND full_scope=@full AND window_start=@start AND window_end_exclusive=@end)
 OR EXISTS(SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM #candidate EXCEPT SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id)
 OR EXISTS(SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id EXCEPT SELECT entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at FROM #candidate)
 THROW 53625,N'ANA_COLLECTORS_RECEIPT_INPUT_CHANGED',1;
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id; RETURN;
 END;
 SELECT n.*,o.observation_id prior_observation_id,o.reference_date old_reference_date,o.branch_key old_branch_key,
 CONVERT(VARCHAR(8),CASE WHEN o.observation_id IS NULL THEN 'INSERT'
 WHEN EXISTS(SELECT n.entity,n.source_key,n.contribution,n.source_execution,n.manifest_snapshot_id,n.inventory_observation_id,n.reference_revision,n.reference_release_id,n.source_state_id,n.reference_date,n.branch_key,n.branch_binding_id,n.branch_provenance,n.fallback_dependency_id,n.quantity,n.incomplete,n.disposition,n.extracted_at EXCEPT SELECT o.entity,o.source_key,o.contribution,o.source_execution,o.manifest_snapshot_id,o.inventory_observation_id,o.reference_revision,o.reference_release_id,o.source_state_id,o.reference_date,o.branch_key,o.branch_binding_id,o.branch_provenance,o.fallback_dependency_id,o.quantity,o.incomplete,o.disposition,o.extracted_at) THEN 'UPDATE' ELSE 'NOOP' END) action
 INTO #decisions FROM #candidate n LEFT JOIN mart.analytic_collector_contribution c ON c.run_id=@run_id AND c.entity=n.entity AND c.source_key=n.source_key AND c.contribution=n.contribution
 LEFT JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observation_id;
 INSERT ctl.analytic_lab_materialization_receipt SELECT @receipt_id,@run_id,'MAT02',@reference_revision,@mode,@full,@start,@end,COUNT_BIG(*),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='INSERT' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='UPDATE' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action='NOOP' THEN 1 ELSE 0 END)),0),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition='READY' THEN 1 ELSE 0 END)),0),
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition<>'READY' THEN 1 ELSE 0 END)),0),@now FROM #decisions;
 INSERT mart.analytic_collector_contribution_observation(receipt_id,run_id,entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key)
 SELECT @receipt_id,@run_id,entity,source_key,contribution,source_execution,manifest_snapshot_id,inventory_observation_id,reference_revision,reference_release_id,source_state_id,reference_date,branch_key,branch_binding_id,branch_provenance,fallback_dependency_id,quantity,incomplete,disposition,extracted_at,action,prior_observation_id,old_reference_date,old_branch_key FROM #decisions;
 INSERT mart.analytic_collector_contribution SELECT @run_id,entity,source_key,contribution,observation_id,reference_date,branch_key
 FROM mart.analytic_collector_contribution_observation WHERE receipt_id=@receipt_id AND action='INSERT';
 UPDATE c SET observation_id=o.observation_id,reference_date=o.reference_date,branch_key=o.branch_key
 FROM mart.analytic_collector_contribution c JOIN mart.analytic_collector_contribution_observation o
 ON o.run_id=c.run_id AND o.entity=c.entity AND o.source_key=c.source_key AND o.contribution=c.contribution
 WHERE o.receipt_id=@receipt_id AND o.action='UPDATE';
 SELECT reference_date,branch_key INTO #partitions FROM #decisions WHERE reference_date IS NOT NULL AND branch_key IS NOT NULL AND action<>'NOOP'
 UNION SELECT old_reference_date,old_branch_key FROM #decisions WHERE old_reference_date IS NOT NULL AND old_branch_key IS NOT NULL AND action<>'NOOP';
 SELECT p.reference_date,p.branch_key,
 COALESCE(SUM(CASE WHEN o.contribution='ISSUED' THEN o.quantity ELSE 0 END),0) issued,
 COALESCE(SUM(CASE WHEN o.contribution='UNLOADED' THEN o.quantity ELSE 0 END),0) unloaded,
 COALESCE(SUM(CASE WHEN o.contribution='SCANNED' THEN o.quantity ELSE 0 END),0) scanned,
 COALESCE(SUM(o.incomplete),0) incomplete
 INTO #daily FROM #partitions p LEFT JOIN mart.analytic_collector_contribution c ON c.run_id=@run_id AND c.reference_date=p.reference_date AND c.branch_key=p.branch_key
 LEFT JOIN mart.analytic_collector_contribution_observation o ON o.observation_id=c.observation_id GROUP BY p.reference_date,p.branch_key;
 INSERT mart.analytic_collector_daily_observation(run_id,receipt_id,reference_date,branch_key,classification,issued,unloaded,scanned,incomplete,total,percentage,has_inventory,active,reference_revision,reference_release_id)
 SELECT @run_id,@receipt_id,d.reference_date,d.branch_key,N'Geral',d.issued,d.unloaded,d.scanned,d.incomplete,d.issued+d.unloaded,
 CONVERT(DECIMAL(28,8),CASE WHEN d.issued+d.unloaded=0 THEN 0 ELSE CONVERT(DECIMAL(28,8),d.scanned)*100/CONVERT(DECIMAL(28,8),d.issued+d.unloaded) END),
 CASE WHEN d.scanned>0 THEN 1 ELSE 0 END,CASE WHEN d.issued+d.unloaded+d.scanned>0 THEN 1 ELSE 0 END,@reference_revision,s.reference_release_id
 FROM #daily d OUTER APPLY ref.ufn_analytic_reference(@run_id,@reference_revision,d.reference_date) s;
 UPDATE c SET observation_id=o.observation_id FROM mart.analytic_collector_daily c JOIN mart.analytic_collector_daily_observation o
 ON o.run_id=c.run_id AND o.reference_date=c.reference_date AND o.branch_key=c.branch_key WHERE o.receipt_id=@receipt_id;
 INSERT mart.analytic_collector_daily SELECT o.run_id,o.reference_date,o.branch_key,o.observation_id FROM mart.analytic_collector_daily_observation o
 WHERE o.receipt_id=@receipt_id AND NOT EXISTS(SELECT 1 FROM mart.analytic_collector_daily c WHERE c.run_id=o.run_id AND c.reference_date=o.reference_date AND c.branch_key=o.branch_key);
 SELECT candidates,inserts,updates,noops,ready,blocked FROM ctl.analytic_lab_materialization_receipt WHERE receipt_id=@receipt_id;
END;
GO

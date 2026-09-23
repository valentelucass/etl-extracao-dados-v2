-- Additive review of capture fences and query conflict disposition. Existing migrations remain immutable.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.expansion_lab_contract (
 run_id UNIQUEIDENTIFIER NOT NULL,entity VARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
 contract_version VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 CONSTRAINT PK_exp_contract PRIMARY KEY(run_id,entity),
 CONSTRAINT FK_exp_contract_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT CK_exp_contract CHECK(entity IN('CAP','FAT','INV','SIN','FRETE','LOC') AND LEN(contract_fingerprint)=64
 AND contract_fingerprint NOT LIKE '%[^0-9a-f]%' AND contract_version IN('expansion-synthetic-v1','expansion-dependency-v1'))
);
GO
CREATE TRIGGER ctl.trg_expansion_contract_immutable ON ctl.expansion_lab_contract AFTER UPDATE,DELETE
AS
BEGIN
 THROW 53440,N'EXP_CONTRACT_IMMUTABLE',1;
END;
GO
CREATE PROCEDURE ctl.usp_assert_expansion_capture_scope @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@entity VARCHAR(8)
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 THROW 53440,N'EXP_TRANSACTION_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_run r JOIN ctl.expansion_lab_contract expected ON expected.run_id=r.run_id AND expected.entity=@entity
 JOIN ctl.execution_attempt e ON e.execution_id=@execution_id AND e.contract_version=expected.contract_version
 AND e.contract_fingerprint COLLATE Latin1_General_100_BIN2=expected.contract_fingerprint
 JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 JOIN ctl.execution_source_protocol source ON source.execution_id=e.execution_id AND source.source_kind=N'DATA_EXPORT'
 JOIN ctl.execution_audit a ON a.execution_id=e.execution_id
 WHERE r.run_id=@run_id AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=r.source_instance AND p.tenant_scope=r.tenant_scope
 AND p.entity_name=CASE @entity WHEN 'FRETE' THEN N'fretes' WHEN 'LOC' THEN N'localizacao_cargas' ELSE CONVERT(NVARCHAR(8),@entity) END COLLATE Latin1_General_100_BIN2
 AND p.execution_mode IN(N'BOOTSTRAP',N'INCREMENTAL',N'BACKFILL',N'REPLAY')
 AND e.current_state IN(N'EXTRACTING',N'STAGED',N'DEGRADED')
 AND a.status=N'COMPLETED' AND a.business_window_start=a.business_window_end
 AND a.business_window_start>=r.window_start AND a.business_window_start<r.window_end_exclusive
 AND p.partition_start_utc=CONVERT(DATETIME2(3),CONVERT(DATETIME2,a.business_window_start) AT TIME ZONE 'E. South America Standard Time' AT TIME ZONE 'UTC')
 AND p.partition_end_exclusive_utc=CONVERT(DATETIME2(3),CONVERT(DATETIME2,DATEADD(day,1,a.business_window_start)) AT TIME ZONE 'E. South America Standard Time' AT TIME ZONE 'UTC')
 AND a.template_id=CASE @entity WHEN 'CAP' THEN 8636 WHEN 'FAT' THEN 4924 WHEN 'INV' THEN 10633 WHEN 'SIN' THEN 6392 WHEN 'FRETE' THEN 6389 ELSE 8656 END
 AND a.pages_fetched=a.terminal_page AND a.pages_fetched<=r.maximum_pages AND a.records_delivered<=r.maximum_rows
 AND a.pages_fetched=(SELECT COUNT_BIG(*) FROM ctl.page_audit WHERE execution_id=@execution_id)
 AND a.records_delivered=(SELECT COALESCE(SUM(CONVERT(BIGINT,record_count)),0) FROM ctl.page_audit WHERE execution_id=@execution_id)
 AND EXISTS(SELECT 1 FROM ctl.page_audit WHERE execution_id=@execution_id AND page_number=a.terminal_page AND record_count=0 AND is_terminal=1)
 AND ((p.execution_mode<>N'REPLAY' AND e.replay_of_execution_id IS NULL) OR(p.execution_mode=N'REPLAY' AND
 (EXISTS(SELECT 1 FROM ctl.expansion_lab_capture prior WHERE prior.run_id=r.run_id AND prior.execution_id=e.replay_of_execution_id AND prior.vertical=@entity AND prior.state='COMPLETE')
 OR EXISTS(SELECT 1 FROM ctl.expansion_lab_dependency_capture prior WHERE prior.run_id=r.run_id AND prior.execution_id=e.replay_of_execution_id AND prior.entity=@entity AND prior.state='COMPLETE')))))
 THROW 53441,N'EXP_CAPTURE_SCOPE_OR_COMPLETENESS',1;
END;
GO
ALTER VIEW core.expansion_lab_current_input AS
 SELECT r.run_id,r.root_id,r.vertical,r.root_type,r.root_key,r.currency,r.unit,r.amount root_amount,r.amount_state,
 c.component_id,c.part_type,c.part_key,c.component_type,c.component_key,c.proof_attached,
 o.execution_id,o.occurrence,o.observation_id,o.revision,o.fresh_second,o.fresh_nano,o.fresh_inclusive,o.part_amount,o.allocation_amount,o.additive_allocation,
 capture.partition_start,
 CONVERT(BIT,CASE WHEN EXISTS(SELECT 1 FROM stg.expansion_lab_observation q JOIN ctl.expansion_lab_capture qc ON qc.execution_id=q.execution_id
 WHERE q.run_id=r.run_id AND q.vertical=r.vertical AND q.root_type=r.root_type AND q.root_key=r.root_key
 AND q.part_type=c.part_type AND q.part_key=c.part_key AND q.component_type=c.component_type AND q.component_key=c.component_key
 AND q.disposition LIKE 'QUARANTINE_%' AND qc.state='COMPLETE'
 AND(q.fresh_second>o.fresh_second OR(q.fresh_second=o.fresh_second AND q.fresh_nano>o.fresh_nano)
 OR(q.fresh_second=o.fresh_second AND q.fresh_nano=o.fresh_nano AND q.fresh_inclusive>o.fresh_inclusive)
 OR(q.fresh_second=o.fresh_second AND q.fresh_nano=o.fresh_nano AND q.fresh_inclusive=o.fresh_inclusive AND q.revision>=o.revision))) THEN 1 ELSE 0 END) unresolved_conflict
 FROM core.expansion_lab_root r JOIN core.expansion_lab_component c ON c.root_id=r.root_id
 JOIN stg.expansion_lab_observation o ON o.observation_id=c.observation_id
 JOIN ctl.expansion_lab_capture capture ON capture.execution_id=o.execution_id
 WHERE r.active=1 AND c.active=1 AND capture.state='COMPLETE';
GO
ALTER PROCEDURE ctl.usp_seal_expansion_lab_capture @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT=0 THROW 53300,N'EXP_TRANSACTION_REQUIRED',1;
 DECLARE @entity VARCHAR(8)=(SELECT vertical FROM ctl.expansion_lab_capture WHERE execution_id=@execution_id AND run_id=@run_id);
 EXEC ctl.usp_assert_expansion_capture_scope @run_id,@execution_id,@entity;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c JOIN ctl.expansion_lab_contract expected ON expected.run_id=c.run_id AND expected.entity=c.vertical COLLATE Latin1_General_100_BIN2
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
 WHERE c.execution_id=@execution_id AND(c.contract_fingerprint<>expected.contract_fingerprint OR c.mode<>p.execution_mode COLLATE Latin1_General_100_BIN2
 OR ISNULL(c.replay_of,'00000000-0000-0000-0000-000000000000')<>ISNULL(e.replay_of_execution_id,'00000000-0000-0000-0000-000000000000')))
 THROW 53442,N'EXP_CAPTURE_BINDING_MISMATCH',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_capture c JOIN ctl.execution_audit a ON a.execution_id=c.execution_id
 JOIN ctl.expansion_lab_run r ON r.run_id=c.run_id WHERE c.run_id=@run_id AND c.execution_id=@execution_id
 AND c.state='CAPTURING' AND a.status=N'COMPLETED' AND a.terminal_page IS NOT NULL
 AND a.business_window_start=c.partition_start AND DATEADD(day,1,a.business_window_end)=c.partition_end_exclusive
 AND a.records_delivered=(SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation o WHERE o.execution_id=@execution_id)
 AND a.records_delivered<=r.maximum_rows AND a.pages_fetched<=r.maximum_pages)
 THROW 53301,N'EXP_COMPLETE_CAPTURE_REQUIRED',1;
 UPDATE ctl.expansion_lab_capture SET state='COMPLETE',sealed_at=@now,physical_rows=
 (SELECT COUNT_BIG(*) FROM stg.expansion_lab_observation WHERE execution_id=@execution_id) WHERE execution_id=@execution_id;
END;
GO
ALTER PROCEDURE core.usp_apply_expansion_dependencies @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'DEPENDENCY';
 DECLARE @entity VARCHAR(8),@observed BIGINT;
 SELECT @entity=c.entity,@observed=a.records_delivered FROM ctl.expansion_lab_dependency_capture c
 JOIN ctl.execution_audit a ON a.execution_id=c.execution_id JOIN ctl.expansion_lab_run r ON r.run_id=c.run_id
 WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND a.status=N'COMPLETED' AND a.terminal_page IS NOT NULL
 AND a.template_id=c.template_id AND a.business_window_start=c.partition_date AND a.business_window_end=c.partition_date
 AND c.partition_date>=r.window_start AND c.partition_date<r.window_end_exclusive
 AND a.records_delivered<=r.maximum_rows AND a.terminal_page<=r.maximum_pages;
 IF @entity IS NULL THROW 53402,N'EXP_DEP_INCOMPLETE',1;
 EXEC ctl.usp_assert_expansion_capture_scope @run_id,@execution_id,@entity;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_dependency_capture c JOIN ctl.expansion_lab_contract expected ON expected.run_id=c.run_id AND expected.entity=c.entity COLLATE Latin1_General_100_BIN2
 WHERE c.execution_id=@execution_id AND c.contract_fingerprint<>expected.contract_fingerprint) THROW 53442,N'EXP_CAPTURE_BINDING_MISMATCH',1;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_dependency_capture WHERE execution_id=@execution_id AND state='COMPLETE')
 BEGIN
  SELECT observed,inserts,updates,noops,stale,quarantine,duplicates FROM ctl.expansion_lab_dependency_capture WHERE execution_id=@execution_id;
  RETURN;
 END;
 IF @observed<>(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=@execution_id)
 OR @observed<>(SELECT COUNT_BIG(*) FROM stg.expansion_lab_dependency_time WHERE execution_id=@execution_id)
 THROW 53403,N'EXP_DEP_COUNT_MISMATCH',1;
 SELECT s.stage_record_id,s.source_key,p.payload_json,t.fresh_second,t.fresh_nano,t.service_second,t.service_nano,
 CONVERT(VARCHAR(16),CASE WHEN s.validation_disposition<>N'VALID' OR t.fresh_second IS NULL THEN 'QUARANTINE' ELSE 'PENDING' END) disposition
 INTO #raw FROM stg.execution_record s JOIN stg.expansion_lab_dependency_time t ON t.execution_id=s.execution_id
 AND t.batch_number=s.input_batch_number AND t.input_ordinal=s.input_record_ordinal
 LEFT JOIN stg.expansion_lab_dependency_payload p ON p.stage_record_id=s.stage_record_id WHERE s.execution_id=@execution_id;
 IF EXISTS(SELECT 1 FROM #raw WHERE DATALENGTH(payload_json)>131072) THROW 53404,N'EXP_DEP_PAYLOAD_BOUND',1;
 SELECT *,DENSE_RANK() OVER(PARTITION BY source_key ORDER BY fresh_second DESC,fresh_nano DESC) freshness_rank
 INTO #ranked FROM #raw WHERE disposition='PENDING';
 UPDATE r SET disposition='STALE' FROM #raw r JOIN #ranked n ON n.stage_record_id=r.stage_record_id WHERE n.freshness_rank>1;
 UPDATE r SET disposition='QUARANTINE' FROM #raw r JOIN #ranked n ON n.stage_record_id=r.stage_record_id
 WHERE n.freshness_rank=1 AND EXISTS(SELECT 1 FROM #ranked x WHERE x.source_key=n.source_key AND x.freshness_rank=1
 AND CONVERT(VARBINARY(MAX),x.payload_json)<>CONVERT(VARBINARY(MAX),n.payload_json));
 -- Only after exact cohort equivalence may an ordinal select a physical lineage pointer.
 SELECT source_key,MIN(stage_record_id) stage_record_id INTO #winner FROM #raw WHERE disposition='PENDING' GROUP BY source_key;
 UPDATE r SET disposition='DUPLICATE' FROM #raw r JOIN #winner w ON w.source_key=r.source_key
 WHERE r.disposition='PENDING' AND r.stage_record_id<>w.stage_record_id;
 UPDATE r SET disposition=CASE WHEN d.dependency_id IS NULL THEN 'INSERTED'
 WHEN r.fresh_second<old.fresh_second OR(r.fresh_second=old.fresh_second AND r.fresh_nano<old.fresh_nano) THEN 'STALE'
 WHEN r.fresh_second=old.fresh_second AND r.fresh_nano=old.fresh_nano THEN
 CASE WHEN CONVERT(VARBINARY(MAX),r.payload_json)=CONVERT(VARBINARY(MAX),prior.payload_json) AND d.state='VALID' THEN 'NOOP' ELSE 'QUARANTINE' END
 ELSE 'UPDATED' END FROM #raw r LEFT JOIN core.expansion_lab_dependency d ON d.run_id=@run_id AND d.entity=@entity AND d.source_key=r.source_key
 LEFT JOIN stg.expansion_lab_dependency_observation old ON old.stage_record_id=d.stage_record_id
 LEFT JOIN stg.expansion_lab_dependency_payload prior ON prior.stage_record_id=d.stage_record_id WHERE r.disposition='PENDING';
 INSERT stg.expansion_lab_dependency_observation
 SELECT stage_record_id,@execution_id,@run_id,@entity,source_key,fresh_second,fresh_nano,service_second,service_nano,disposition FROM #raw;
 INSERT core.expansion_lab_dependency(run_id,entity,source_key,stage_record_id,state)
 SELECT @run_id,@entity,source_key,stage_record_id,'VALID' FROM #raw WHERE disposition='INSERTED';
 INSERT core.expansion_lab_dependency_history(dependency_id,prior_stage_id,stage_record_id,action,recorded_at)
 SELECT d.dependency_id,CASE WHEN r.disposition='UPDATED' THEN d.stage_record_id END,r.stage_record_id,r.disposition,@now
 FROM #raw r JOIN core.expansion_lab_dependency d ON d.run_id=@run_id AND d.entity=@entity AND d.source_key=r.source_key
 WHERE r.disposition IN('INSERTED','UPDATED');
 UPDATE d SET stage_record_id=r.stage_record_id,state='VALID' FROM core.expansion_lab_dependency d JOIN #raw r ON r.source_key=d.source_key
 WHERE d.run_id=@run_id AND d.entity=@entity AND r.disposition='UPDATED';
 UPDATE d SET state='CONFLICT' FROM core.expansion_lab_dependency d JOIN stg.expansion_lab_dependency_observation old ON old.stage_record_id=d.stage_record_id
 WHERE d.run_id=@run_id AND d.entity=@entity AND EXISTS(SELECT 1 FROM #raw r WHERE r.source_key=d.source_key AND r.disposition='QUARANTINE'
 AND(r.fresh_second>old.fresh_second OR(r.fresh_second=old.fresh_second AND r.fresh_nano>=old.fresh_nano)));
 UPDATE ctl.expansion_lab_dependency_capture SET state='COMPLETE',observed=@observed,
 inserts=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='INSERTED'),updates=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='UPDATED'),
 noops=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='NOOP'),stale=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='STALE'),
 quarantine=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='QUARANTINE'),duplicates=(SELECT COUNT_BIG(*) FROM #raw WHERE disposition='DUPLICATE'),recorded_at=@now
 WHERE execution_id=@execution_id;
 SELECT observed,inserts,updates,noops,stale,quarantine,duplicates FROM ctl.expansion_lab_dependency_capture WHERE execution_id=@execution_id;
END;
GO
ALTER PROCEDURE ctl.usp_finish_expansion_queue @run_id UNIQUEIDENTIFIER,@queue_id BIGINT,@owner UNIQUEIDENTIFIER,
 @outcome VARCHAR(16),@execution_id UNIQUEIDENTIFIER=NULL,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'QUEUE';
 IF @outcome NOT IN('CAPTURED','EMPTY','TEMPORARY','INVALID','CONFLICT','ABANDONED') THROW 53415,N'EXP_QUEUE_OUTCOME',1;
 IF EXISTS(SELECT 1 FROM ctl.expansion_lab_queue q JOIN ctl.expansion_lab_queue_attempt a ON a.queue_id=q.queue_id AND a.attempt=q.attempts
 WHERE q.run_id=@run_id AND q.queue_id=@queue_id AND a.owner_id=@owner AND a.outcome=@outcome AND a.finished_at IS NOT NULL
 AND ISNULL(a.execution_id,'00000000-0000-0000-0000-000000000000')=ISNULL(@execution_id,'00000000-0000-0000-0000-000000000000')) RETURN;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_queue WHERE queue_id=@queue_id AND run_id=@run_id AND owner_id=@owner AND state='CLAIMED' AND lease_until>@now)
 THROW 53416,N'EXP_QUEUE_OWNER_OR_LEASE',1;
 IF @outcome IN('CAPTURED','EMPTY') AND NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_dependency_capture WHERE run_id=@run_id AND execution_id=@execution_id AND state='COMPLETE' AND entity='FRETE')
 THROW 53417,N'EXP_QUEUE_CAPTURE_REQUIRED',1;
 IF @outcome='CAPTURED' AND NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_queue q JOIN core.expansion_lab_dependency d ON d.run_id=q.run_id AND d.entity='FRETE' AND d.source_key=q.target_key
 JOIN stg.expansion_lab_dependency_observation o ON o.run_id=q.run_id AND o.source_key=q.target_key AND o.entity='FRETE'
 WHERE q.queue_id=@queue_id AND d.state='VALID' AND o.execution_id=@execution_id AND o.disposition IN('INSERTED','UPDATED','NOOP','DUPLICATE','STALE'))
 THROW 53418,N'EXP_QUEUE_TARGET_NOT_CAPTURED',1;
 IF @outcome='EMPTY' AND EXISTS(SELECT 1 FROM stg.expansion_lab_dependency_observation o JOIN ctl.expansion_lab_queue q ON q.run_id=o.run_id AND q.target_key=o.source_key
 WHERE q.queue_id=@queue_id AND o.execution_id=@execution_id) THROW 53419,N'EXP_QUEUE_FALSE_EMPTY',1;
 UPDATE a SET finished_at=@now,outcome=@outcome,execution_id=@execution_id FROM ctl.expansion_lab_queue_attempt a JOIN ctl.expansion_lab_queue q
 ON q.queue_id=a.queue_id AND q.attempts=a.attempt WHERE q.queue_id=@queue_id;
 UPDATE ctl.expansion_lab_queue SET state=CASE WHEN @outcome='CAPTURED' THEN 'RESOLVED'
 WHEN @outcome IN('EMPTY','TEMPORARY') AND attempts>=maximum_attempts THEN 'ABANDONED' ELSE @outcome END,
 cause=@outcome,next_at=DATEADD(second,10*attempts,@now),last_execution_id=@execution_id,owner_id=NULL,lease_until=NULL WHERE queue_id=@queue_id;
END;
GO

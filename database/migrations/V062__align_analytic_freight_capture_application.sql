-- ANA-12: application uses the same declared extension and checks capture against actual execution fingerprint.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
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
 JOIN ctl.execution_attempt actual ON actual.execution_id=c.execution_id
 WHERE c.execution_id=@execution_id AND(c.contract_fingerprint<>actual.contract_fingerprint COLLATE Latin1_General_100_BIN2
 OR(c.contract_fingerprint<>expected.contract_fingerprint AND NOT(c.entity='FRETE' AND EXISTS(
 SELECT 1 FROM ctl.analytic_lab_freight_contract extension JOIN ctl.analytic_lab_source_group declared ON declared.run_id=extension.run_id
 WHERE declared.expansion_run=c.run_id AND extension.contract_version=actual.contract_version
 AND extension.contract_fingerprint=c.contract_fingerprint))))) THROW 53442,N'EXP_CAPTURE_BINDING_MISMATCH',1;
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

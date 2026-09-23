-- ADR0049: captured dependencies retain their original staging and exact typed clock.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE ctl.expansion_lab_dependency_capture (
 execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY, run_id UNIQUEIDENTIFIER NOT NULL,
 entity VARCHAR(8) NOT NULL, template_id INT NOT NULL, partition_date DATE NOT NULL,
 contract_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 state VARCHAR(12) NOT NULL DEFAULT 'CAPTURING', observed BIGINT NULL,
 inserts BIGINT NULL,updates BIGINT NULL,noops BIGINT NULL,stale BIGINT NULL,quarantine BIGINT NULL,duplicates BIGINT NULL,
 recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_dep_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT CK_exp_dep_kind CHECK((entity='FRETE' AND template_id=6389) OR(entity='LOC' AND template_id=8656)),
 CONSTRAINT CK_exp_dep_state CHECK((state='CAPTURING' AND observed IS NULL) OR
 (state='COMPLETE' AND observed=inserts+updates+noops+stale+quarantine+duplicates)),
 CONSTRAINT CK_exp_dep_fingerprint CHECK(LEN(contract_fingerprint)=64 AND contract_fingerprint NOT LIKE '%[^0-9a-f]%')
);
CREATE TABLE stg.expansion_lab_dependency_time (
 execution_id UNIQUEIDENTIFIER NOT NULL,batch_number INT NOT NULL,input_ordinal INT NOT NULL,
 fresh_second BIGINT NULL,fresh_nano INT NULL,service_second BIGINT NULL,service_nano INT NULL,
 CONSTRAINT PK_exp_dep_time PRIMARY KEY(execution_id,batch_number,input_ordinal),
 CONSTRAINT FK_exp_dep_time_capture FOREIGN KEY(execution_id) REFERENCES ctl.expansion_lab_dependency_capture(execution_id),
 CONSTRAINT CK_exp_dep_time CHECK(batch_number BETWEEN 1 AND 10000 AND input_ordinal BETWEEN 1 AND 100
 AND(fresh_nano IS NULL OR fresh_nano BETWEEN 0 AND 999999999) AND(service_nano IS NULL OR service_nano BETWEEN 0 AND 999999999))
);
CREATE TABLE stg.expansion_lab_dependency_observation (
 stage_record_id BIGINT NOT NULL PRIMARY KEY, execution_id UNIQUEIDENTIFIER NOT NULL,
 run_id UNIQUEIDENTIFIER NOT NULL,entity VARCHAR(8) NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NULL,
 fresh_second BIGINT NULL,fresh_nano INT NULL,service_second BIGINT NULL,service_nano INT NULL,
 disposition VARCHAR(16) NOT NULL,
 CONSTRAINT FK_exp_dep_obs_capture FOREIGN KEY(execution_id) REFERENCES ctl.expansion_lab_dependency_capture(execution_id),
 CONSTRAINT FK_exp_dep_obs_stage FOREIGN KEY(stage_record_id) REFERENCES stg.execution_record(stage_record_id)
);
CREATE INDEX IX_exp_dep_obs_capture ON stg.expansion_lab_dependency_observation(execution_id,disposition);
CREATE TABLE core.expansion_lab_dependency (
 dependency_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,entity VARCHAR(8) NOT NULL,
 source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,stage_record_id BIGINT NOT NULL,
 state VARCHAR(12) NOT NULL,
 CONSTRAINT FK_exp_dep_current_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT FK_exp_dep_current_stage FOREIGN KEY(stage_record_id) REFERENCES stg.expansion_lab_dependency_observation(stage_record_id),
 CONSTRAINT UQ_exp_dep_current UNIQUE(run_id,entity,source_key),
 CONSTRAINT CK_exp_dep_current_state CHECK(state IN('VALID','CONFLICT'))
);
CREATE TABLE core.expansion_lab_dependency_history (
 history_id BIGINT IDENTITY NOT NULL PRIMARY KEY,dependency_id BIGINT NOT NULL,
 prior_stage_id BIGINT NULL,stage_record_id BIGINT NOT NULL,action VARCHAR(16) NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_dep_history FOREIGN KEY(dependency_id) REFERENCES core.expansion_lab_dependency(dependency_id)
);
GO
CREATE VIEW stg.expansion_lab_dependency_payload AS
 SELECT stage_record_id,payload_json FROM stg.frete_record
 UNION ALL SELECT stage_record_id,payload_json FROM stg.localizacao_carga_record;
GO
CREATE PROCEDURE ctl.usp_expansion_lab_lock @run_id UNIQUEIDENTIFIER,@lane VARCHAR(16)
AS
BEGIN
 SET NOCOUNT ON;
 IF @@TRANCOUNT=0 OR @lane NOT IN('DEPENDENCY','RELATION','QUEUE','MATERIALIZE','PLAN') THROW 53400,N'EXP_TRANSACTION_OR_LANE',1;
 DECLARE @result INT,@resource NVARCHAR(255)=CONCAT(N'EXP_',@lane,N'_',CONVERT(NVARCHAR(36),@run_id));
 EXEC @result=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1200;
 IF @result<0 THROW 53401,N'EXP_LOCK_BUSY',1;
END;
GO
CREATE PROCEDURE core.usp_apply_expansion_dependencies @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@now DATETIME2(7)
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

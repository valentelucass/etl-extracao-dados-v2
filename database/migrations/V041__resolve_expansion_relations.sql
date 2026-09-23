-- Explicit synthetic relations and bounded hydration; no alias matching or grants.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO
CREATE TYPE stg.expansion_lab_relation_batch AS TABLE (
 binding_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,revision INT NOT NULL,
 kind VARCHAR(24) NOT NULL,root_type VARCHAR(8) NOT NULL,root_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 part_type VARCHAR(8) NOT NULL,part_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 component_type VARCHAR(8) NOT NULL,component_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 document_type VARCHAR(8) NOT NULL,document_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 target_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,target_date DATE NOT NULL,
 cardinality VARCHAR(16) NOT NULL,active BIT NOT NULL,evidence VARCHAR(64) NOT NULL,
 PRIMARY KEY(binding_key,revision)
);
GO
CREATE TABLE stg.expansion_lab_relation (
 relation_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,
 binding_key VARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,revision INT NOT NULL,
 kind VARCHAR(24) NOT NULL,root_type VARCHAR(8) NOT NULL,root_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 part_type VARCHAR(8) NOT NULL,part_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 component_type VARCHAR(8) NOT NULL,component_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 document_type VARCHAR(8) NOT NULL,document_key NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 target_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,target_date DATE NOT NULL,
 cardinality VARCHAR(16) NOT NULL,active BIT NOT NULL,evidence VARCHAR(64) NOT NULL,
 recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_relation_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT UQ_exp_relation UNIQUE(run_id,binding_key,revision),
 CONSTRAINT CK_exp_relation CHECK(revision BETWEEN 1 AND 1000 AND kind IN('FAT_DOCUMENT_FREIGHT','INV_FREIGHT','SIN_FREIGHT','LOC_FREIGHT')
 AND cardinality IN('ONE_TO_ONE','ONE_TO_MANY','MANY_TO_MANY') AND root_type IN('INTEGER','STRING') AND part_type IN('INTEGER','STRING')
 AND component_type IN('INTEGER','STRING') AND document_type IN('INTEGER','STRING')
 AND LEN(root_key)>0 AND LEN(part_key)>0 AND LEN(component_key)>0 AND LEN(document_key)>0
 AND DATALENGTH(root_key)=DATALENGTH(LTRIM(RTRIM(root_key))) AND DATALENGTH(part_key)=DATALENGTH(LTRIM(RTRIM(part_key)))
 AND DATALENGTH(component_key)=DATALENGTH(LTRIM(RTRIM(component_key))) AND DATALENGTH(document_key)=DATALENGTH(LTRIM(RTRIM(document_key)))
 AND binding_key LIKE 'synthetic-%' AND evidence LIKE 'synthetic-%'
 AND target_key LIKE N'INTEGER:[1-9]%' AND SUBSTRING(target_key,9,256) NOT LIKE N'%[^0-9]%'
 AND DATALENGTH(target_key)=DATALENGTH(RTRIM(target_key)))
);
CREATE INDEX IX_exp_relation_target ON stg.expansion_lab_relation(run_id,target_key) INCLUDE(kind,revision,binding_key);
CREATE TABLE core.expansion_lab_link (
 relation_id BIGINT NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,root_id BIGINT NULL,component_id BIGINT NULL,
 source_dependency_id BIGINT NULL,target_dependency_id BIGINT NULL,state VARCHAR(24) NOT NULL,recorded_at DATETIME2(7) NOT NULL,
 CONSTRAINT FK_exp_link_relation FOREIGN KEY(relation_id) REFERENCES stg.expansion_lab_relation(relation_id),
 CONSTRAINT FK_exp_link_root FOREIGN KEY(root_id) REFERENCES core.expansion_lab_root(root_id),
 CONSTRAINT FK_exp_link_component FOREIGN KEY(component_id) REFERENCES core.expansion_lab_component(component_id),
 CONSTRAINT FK_exp_link_source FOREIGN KEY(source_dependency_id) REFERENCES core.expansion_lab_dependency(dependency_id),
 CONSTRAINT FK_exp_link_target FOREIGN KEY(target_dependency_id) REFERENCES core.expansion_lab_dependency(dependency_id),
 CONSTRAINT CK_exp_link_state CHECK(state IN('RESOLVED','MISSING_TARGET','MISSING_SOURCE','CONFLICT','SUPERSEDED','INACTIVE'))
);
CREATE INDEX IX_exp_link_consumer ON core.expansion_lab_link(run_id,state,root_id) INCLUDE(component_id,target_dependency_id,source_dependency_id);
CREATE TABLE ctl.expansion_lab_queue (
 queue_id BIGINT IDENTITY NOT NULL PRIMARY KEY,run_id UNIQUEIDENTIFIER NOT NULL,target_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
 target_date DATE NOT NULL,state VARCHAR(16) NOT NULL,cause VARCHAR(32) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,maximum_attempts INT NOT NULL DEFAULT 3,next_at DATETIME2(7) NOT NULL,
 owner_id UNIQUEIDENTIFIER NULL,lease_until DATETIME2(7) NULL,last_execution_id UNIQUEIDENTIFIER NULL,
 CONSTRAINT FK_exp_queue_run FOREIGN KEY(run_id) REFERENCES ctl.expansion_lab_run(run_id),
 CONSTRAINT UQ_exp_queue UNIQUE(run_id,target_key),
 CONSTRAINT CK_exp_queue CHECK(state IN('READY','CLAIMED','EMPTY','TEMPORARY','RESOLVED','INVALID','CONFLICT','ABANDONED')
 AND attempts BETWEEN 0 AND 3 AND maximum_attempts=3 AND ((state='CLAIMED' AND owner_id IS NOT NULL AND lease_until IS NOT NULL) OR state<>'CLAIMED'))
);
CREATE INDEX IX_exp_queue_claim ON ctl.expansion_lab_queue(run_id,state,next_at,queue_id) INCLUDE(target_key,target_date,attempts,lease_until);
CREATE TABLE ctl.expansion_lab_queue_attempt (
 queue_id BIGINT NOT NULL,attempt INT NOT NULL,owner_id UNIQUEIDENTIFIER NOT NULL,
 claimed_at DATETIME2(7) NOT NULL,lease_until DATETIME2(7) NOT NULL,finished_at DATETIME2(7) NULL,
 outcome VARCHAR(24) NULL,execution_id UNIQUEIDENTIFIER NULL,
 CONSTRAINT PK_exp_queue_attempt PRIMARY KEY(queue_id,attempt),
 CONSTRAINT FK_exp_queue_attempt FOREIGN KEY(queue_id) REFERENCES ctl.expansion_lab_queue(queue_id)
);
GO
CREATE PROCEDURE stg.usp_bind_expansion_relations @run_id UNIQUEIDENTIFIER,@batch stg.expansion_lab_relation_batch READONLY,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'RELATION';
 IF (SELECT COUNT_BIG(*) FROM @batch)>100 THROW 53410,N'EXP_RELATION_BATCH_BOUND',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_run WHERE run_id=@run_id) THROW 53411,N'EXP_RELATION_RUN',1;
 IF EXISTS(SELECT 1 FROM @batch b JOIN ctl.expansion_lab_run r ON r.run_id=@run_id
 WHERE b.target_date<r.window_start OR b.target_date>=r.window_end_exclusive) THROW 53412,N'EXP_RELATION_DATE_SCOPE',1;
 IF EXISTS(SELECT 1 FROM @batch b JOIN stg.expansion_lab_relation r ON r.run_id=@run_id AND r.binding_key=b.binding_key AND r.revision=b.revision
 WHERE EXISTS(SELECT b.kind,b.root_type,b.root_key,b.part_type,b.part_key,b.component_type,b.component_key,b.document_type,b.document_key,
 b.target_key,b.target_date,b.cardinality,b.active,b.evidence EXCEPT SELECT r.kind,r.root_type,r.root_key,r.part_type,r.part_key,r.component_type,
 r.component_key,r.document_type,r.document_key,r.target_key,r.target_date,r.cardinality,r.active,r.evidence))
 THROW 53413,N'EXP_RELATION_REVISION_DIVERGENT',1;
 INSERT stg.expansion_lab_relation(run_id,binding_key,revision,kind,root_type,root_key,part_type,part_key,component_type,component_key,
 document_type,document_key,target_key,target_date,cardinality,active,evidence,recorded_at)
 SELECT @run_id,b.*,@now FROM @batch b WHERE NOT EXISTS(SELECT 1 FROM stg.expansion_lab_relation r
 WHERE r.run_id=@run_id AND r.binding_key=b.binding_key AND r.revision=b.revision);
END;
GO
CREATE PROCEDURE core.usp_resolve_expansion_relations @run_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'RELATION';
 EXEC ctl.usp_expansion_lab_lock @run_id,'QUEUE';
 SELECT b.relation_id,b.kind,b.binding_key,b.revision,b.active,b.cardinality,b.document_type,b.document_key,b.target_key,b.target_date,
 r.root_id,c.component_id,src.dependency_id source_dependency_id,d.dependency_id target_dependency_id,
 CONVERT(VARCHAR(24),CASE WHEN EXISTS(SELECT 1 FROM stg.expansion_lab_relation newer WHERE newer.run_id=@run_id AND newer.binding_key=b.binding_key AND newer.revision>b.revision) THEN 'SUPERSEDED'
 WHEN b.active=0 THEN 'INACTIVE'
 WHEN(b.kind='LOC_FREIGHT' AND src.dependency_id IS NULL) OR(b.kind<>'LOC_FREIGHT' AND(c.component_id IS NULL OR r.active=0 OR c.active=0)) THEN 'MISSING_SOURCE'
 WHEN d.state='CONFLICT' OR src.state='CONFLICT' THEN 'CONFLICT'
 WHEN d.dependency_id IS NULL THEN 'MISSING_TARGET' ELSE 'RESOLVED' END) state
 INTO #links FROM stg.expansion_lab_relation b
 LEFT JOIN core.expansion_lab_root r ON r.run_id=b.run_id AND r.vertical=LEFT(b.kind,3) AND r.root_type=b.root_type AND r.root_key=b.root_key
 LEFT JOIN core.expansion_lab_component c ON c.root_id=r.root_id AND c.part_type=b.part_type AND c.part_key=b.part_key
 AND c.component_type=b.component_type AND c.component_key=b.component_key
 LEFT JOIN core.expansion_lab_dependency src ON src.run_id=b.run_id AND src.entity='LOC' AND src.source_key=CONCAT(b.root_type,N':',b.root_key) COLLATE Latin1_General_100_BIN2 AND b.kind='LOC_FREIGHT'
 LEFT JOIN core.expansion_lab_dependency d ON d.run_id=b.run_id AND d.entity='FRETE' AND d.source_key=b.target_key WHERE b.run_id=@run_id;
 -- ONE_TO_ONE constrains both directions. Declared N:M preserves every explicit edge.
 UPDATE l SET state='CONFLICT' FROM #links l WHERE l.state IN('RESOLVED','MISSING_TARGET') AND EXISTS(SELECT 1 FROM #links x
 WHERE x.relation_id<>l.relation_id AND x.kind=l.kind AND x.state IN('RESOLVED','MISSING_TARGET','CONFLICT')
 AND ((l.component_id=x.component_id OR l.source_dependency_id=x.source_dependency_id) AND
 (l.cardinality<>x.cardinality OR (l.cardinality='ONE_TO_ONE' AND(l.target_key<>x.target_key OR l.document_type<>x.document_type OR l.document_key<>x.document_key)))
 OR (l.cardinality<>'MANY_TO_MANY' AND x.target_key=l.target_key AND (ISNULL(l.component_id,-1)<>ISNULL(x.component_id,-1) OR ISNULL(l.source_dependency_id,-1)<>ISNULL(x.source_dependency_id,-1)))));
 UPDATE l SET state='CONFLICT' FROM #links l WHERE l.state IN('RESOLVED','MISSING_TARGET') AND EXISTS(SELECT 1 FROM #links x WHERE x.target_key=l.target_key
 AND x.state IN('RESOLVED','MISSING_TARGET','CONFLICT') AND x.target_date<>l.target_date);
 INSERT core.expansion_lab_link SELECT relation_id,@run_id,root_id,component_id,source_dependency_id,target_dependency_id,state,@now FROM #links s
 WHERE NOT EXISTS(SELECT 1 FROM core.expansion_lab_link d WHERE d.relation_id=s.relation_id);
 UPDATE d SET root_id=s.root_id,component_id=s.component_id,source_dependency_id=s.source_dependency_id,target_dependency_id=s.target_dependency_id,
 state=s.state,recorded_at=@now FROM core.expansion_lab_link d JOIN #links s ON s.relation_id=d.relation_id;
 INSERT ctl.expansion_lab_queue(run_id,target_key,target_date,state,cause,next_at)
 SELECT @run_id,target_key,MIN(target_date),'READY','MISSING_REFERENCE',@now FROM #links l WHERE state='MISSING_TARGET'
 AND NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_queue q WHERE q.run_id=@run_id AND q.target_key=l.target_key) GROUP BY target_key;
 UPDATE q SET state='RESOLVED',cause='CAPTURED_REFERENCE',owner_id=NULL,lease_until=NULL FROM ctl.expansion_lab_queue q
 WHERE q.run_id=@run_id AND q.state<>'CLAIMED' AND EXISTS(SELECT 1 FROM #links l WHERE l.target_key=q.target_key AND l.state='RESOLVED')
 AND NOT EXISTS(SELECT 1 FROM #links l WHERE l.target_key=q.target_key AND l.state IN('MISSING_TARGET','CONFLICT'));
 SELECT COUNT_BIG(*) candidates,COALESCE(SUM(CONVERT(BIGINT,CASE WHEN state='RESOLVED' THEN 1 ELSE 0 END)),0) resolved,
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN state='MISSING_TARGET' THEN 1 ELSE 0 END)),0) missing,
 COALESCE(SUM(CONVERT(BIGINT,CASE WHEN state='CONFLICT' THEN 1 ELSE 0 END)),0) conflicts FROM #links WHERE state NOT IN('SUPERSEDED','INACTIVE');
END;
GO
CREATE PROCEDURE ctl.usp_claim_expansion_queue @run_id UNIQUEIDENTIFIER,@owner UNIQUEIDENTIFIER,@take INT,@lease_seconds INT,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @take NOT BETWEEN 1 AND 100 OR @lease_seconds NOT BETWEEN 1 AND 60 OR @owner IS NULL THROW 53414,N'EXP_QUEUE_BOUND',1;
 EXEC ctl.usp_expansion_lab_lock @run_id,'QUEUE';
 UPDATE a SET finished_at=@now,outcome='LEASE_EXPIRED' FROM ctl.expansion_lab_queue_attempt a JOIN ctl.expansion_lab_queue q
 ON q.queue_id=a.queue_id AND q.attempts=a.attempt WHERE q.run_id=@run_id AND q.state='CLAIMED' AND q.lease_until<=@now AND a.finished_at IS NULL;
 UPDATE ctl.expansion_lab_queue SET state=CASE WHEN attempts>=maximum_attempts THEN 'ABANDONED' ELSE 'TEMPORARY' END,
 cause='LEASE_EXPIRED',next_at=@now,owner_id=NULL,lease_until=NULL WHERE run_id=@run_id AND state='CLAIMED' AND lease_until<=@now;
 DECLARE @claimed TABLE(queue_id BIGINT,target_key NVARCHAR(256),target_date DATE,attempt INT,lease_until DATETIME2(7));
 ;WITH eligible AS(SELECT TOP(@take) * FROM ctl.expansion_lab_queue WITH(UPDLOCK,READPAST,ROWLOCK)
 WHERE run_id=@run_id AND state IN('READY','EMPTY','TEMPORARY') AND next_at<=@now AND attempts<maximum_attempts ORDER BY next_at,queue_id)
 UPDATE eligible SET state='CLAIMED',cause='HYDRATION_CLAIM',owner_id=@owner,attempts=attempts+1,lease_until=DATEADD(second,@lease_seconds,@now)
 OUTPUT inserted.queue_id,inserted.target_key,inserted.target_date,inserted.attempts,inserted.lease_until INTO @claimed;
 INSERT ctl.expansion_lab_queue_attempt(queue_id,attempt,owner_id,claimed_at,lease_until)
 SELECT queue_id,attempt,@owner,@now,lease_until FROM @claimed;
 SELECT queue_id,target_key,target_date,attempt,lease_until FROM @claimed ORDER BY queue_id;
END;
GO
CREATE PROCEDURE ctl.usp_finish_expansion_queue @run_id UNIQUEIDENTIFIER,@queue_id BIGINT,@owner UNIQUEIDENTIFIER,
 @outcome VARCHAR(16),@execution_id UNIQUEIDENTIFIER=NULL,@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'QUEUE';
 IF @outcome NOT IN('CAPTURED','EMPTY','TEMPORARY','INVALID','CONFLICT','ABANDONED') THROW 53415,N'EXP_QUEUE_OUTCOME',1;
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

-- Versioned temporal decisions and bounded reconciliation. No scheduler or operational grant.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE ctl.runtime_temporal_policy (
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_material NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_runtime_temporal_policy PRIMARY KEY(policy_version,policy_fingerprint),
    CONSTRAINT CK_runtime_temporal_policy CHECK(policy_fingerprint NOT LIKE '%[^a-f0-9]%' AND ISJSON(policy_material)=1)
);
CREATE TABLE ctl.runtime_temporal_window (
    plan_id UNIQUEIDENTIFIER NOT NULL,
    ordinal INT NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_runtime_temporal_occurrence UNIQUE,
    namespace_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    partition_start_utc DATETIME2(3) NOT NULL,
    partition_end_exclusive_utc DATETIME2(3) NOT NULL,
    extraction_start_utc DATETIME2(3) NOT NULL,
    due_at_utc DATETIME2(3) NOT NULL,
    deadline_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_runtime_temporal_window PRIMARY KEY(plan_id,ordinal),
    CONSTRAINT UQ_runtime_temporal_partition UNIQUE(namespace_fingerprint,partition_start_utc,partition_end_exclusive_utc),
    CONSTRAINT FK_runtime_temporal_window_policy FOREIGN KEY(policy_version,policy_fingerprint)
        REFERENCES ctl.runtime_temporal_policy(policy_version,policy_fingerprint),
    CONSTRAINT CK_runtime_temporal_window CHECK(ordinal BETWEEN 1 AND 64 AND partition_start_utc<partition_end_exclusive_utc
        AND extraction_start_utc<=partition_start_utc AND due_at_utc<deadline_at_utc)
);
GO
CREATE TRIGGER ctl.tr_runtime_temporal_policy_immutable ON ctl.runtime_temporal_policy
INSTEAD OF UPDATE,DELETE AS THROW 52420,N'TEMPORAL_POLICY_IMMUTABLE',1;
GO
CREATE TRIGGER ctl.tr_runtime_temporal_window_immutable ON ctl.runtime_temporal_window
INSTEAD OF UPDATE,DELETE AS THROW 52420,N'TEMPORAL_WINDOW_IMMUTABLE',1;
GO
CREATE PROCEDURE ctl.usp_runtime_temporal_plan
    @plan_id UNIQUEIDENTIFIER,@namespace_fingerprint NVARCHAR(MAX),@policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),@policy_material NVARCHAR(MAX),@windows NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;SET XACT_ABORT ON;
    IF @plan_id IS NULL OR @policy_material IS NULL OR DATALENGTH(@policy_material)>8000 OR ISJSON(@policy_material)<>1
        OR @windows IS NULL OR DATALENGTH(@windows)>65536 OR ISJSON(@windows)<>1 OR LEFT(LTRIM(@windows),1)<>N'['
        OR @policy_version IS NULL OR DATALENGTH(@policy_version) NOT BETWEEN 2 AND 256
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128
        OR @policy_fingerprint<>LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@policy_material),2))
        OR @namespace_fingerprint IS NULL OR DATALENGTH(@namespace_fingerprint)<>128 OR @namespace_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'
        THROW 52421,N'TEMPORAL_PLAN_INVALID',1;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@policy_material))<>15
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) GROUP BY [key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) WHERE [key] COLLATE Latin1_General_100_BIN2 NOT IN
            (N'version',N'zone',N'mode',N'strategy',N'cadence',N'boundary',N'lookback',N'stabilization',N'sla',N'deadline',
             N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded',N'blackouts'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@policy_material) WHERE
            ([key] IN(N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded') AND type<>2)
            OR ([key]=N'blackouts' AND type<>4)
            OR ([key] NOT IN(N'concurrency',N'maximumBacklog',N'maximumReconciliation',N'maximumDegraded',N'blackouts')
                AND (type<>1 OR DATALENGTH(value)=0)))
        OR JSON_VALUE(@policy_material,'$.version') COLLATE Latin1_General_100_BIN2<>@policy_version
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.concurrency')),0) NOT BETWEEN 1 AND 4
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumBacklog')),0) NOT BETWEEN 1 AND 64
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumReconciliation')),0) NOT BETWEEN 1 AND 64
        OR ISNULL(TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumDegraded')),-1) NOT BETWEEN 0 AND 64
        OR TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumDegraded'))>TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumReconciliation'))
        OR JSON_VALUE(@policy_material,'$.strategy') COLLATE Latin1_General_100_BIN2<>N'INTERVAL'
        OR JSON_VALUE(@policy_material,'$.cadence') COLLATE Latin1_General_100_BIN2 NOT IN(N'CIVIL_DAY',N'CIVIL_MONTH')
        OR JSON_VALUE(@policy_material,'$.mode') COLLATE Latin1_General_100_BIN2 NOT IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY')
        OR JSON_QUERY(@policy_material,'$.blackouts') IS NULL
        THROW 52421,N'TEMPORAL_POLICY_SCHEMA_INVALID',1;
    DECLARE @bounded TABLE(ordinal INT NOT NULL PRIMARY KEY,execution_id UNIQUEIDENTIFIER NOT NULL UNIQUE,
        start_utc DATETIME2(3) NOT NULL,end_utc DATETIME2(3) NOT NULL,extraction_utc DATETIME2(3) NOT NULL,
        due_utc DATETIME2(3) NOT NULL,deadline_utc DATETIME2(3) NOT NULL);
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@windows)) NOT BETWEEN 1 AND 64 THROW 52421,N'TEMPORAL_WINDOW_LIMIT',1;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@windows))>TRY_CONVERT(INT,JSON_VALUE(@policy_material,'$.maximumBacklog'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j WHERE j.type<>5 OR (SELECT COUNT_BIG(*) FROM OPENJSON(j.value))<>6)
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) v
            WHERE v.type<>1 OR v.[key] COLLATE Latin1_General_100_BIN2 NOT IN(N'execution',N'start',N'endExclusive',N'extractionStart',N'due',N'deadline'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) v GROUP BY j.[key],v.[key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        THROW 52421,N'TEMPORAL_WINDOW_SCHEMA_INVALID',1;
    INSERT @bounded SELECT CONVERT(INT,j.[key])+1,v.execution_id,v.start_utc,v.end_utc,v.extraction_utc,v.due_utc,v.deadline_utc
    FROM OPENJSON(@windows) j CROSS APPLY OPENJSON(j.value) WITH(execution_id UNIQUEIDENTIFIER '$.execution',
        start_utc DATETIME2(3) '$.start',end_utc DATETIME2(3) '$.endExclusive',extraction_utc DATETIME2(3) '$.extractionStart',
        due_utc DATETIME2(3) '$.due',deadline_utc DATETIME2(3) '$.deadline') v;
    IF EXISTS(SELECT 1 FROM @bounded WHERE start_utc>=end_utc OR extraction_utc>start_utc OR due_utc>=deadline_utc)
        OR EXISTS(SELECT 1 FROM @bounded a JOIN @bounded b ON b.ordinal=a.ordinal+1 WHERE a.end_utc<>b.start_utc)
        THROW 52421,N'TEMPORAL_WINDOW_NOT_CONTIGUOUS',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock INT,@resource NVARCHAR(255)=N'V2_TEMPORAL_'+@namespace_fingerprint;
        EXEC @lock=sys.sp_getapplock @Resource=@resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @lock<0 THROW 52422,N'TEMPORAL_PLAN_LOCK',1;
        IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WITH(UPDLOCK,HOLDLOCK) WHERE policy_version=@policy_version
            AND policy_fingerprint=@policy_fingerprint AND (policy_material<>@policy_material OR DATALENGTH(policy_material)<>DATALENGTH(@policy_material)))
            THROW 52423,N'TEMPORAL_POLICY_CONFLICT',1;
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WITH(UPDLOCK,HOLDLOCK) WHERE policy_version=@policy_version AND policy_fingerprint=@policy_fingerprint)
            INSERT ctl.runtime_temporal_policy VALUES(@policy_version,@policy_fingerprint,@policy_material,SYSUTCDATETIME());
        IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_window WITH(UPDLOCK,HOLDLOCK) WHERE plan_id=@plan_id)
        BEGIN
            IF (SELECT COUNT_BIG(*) FROM ctl.runtime_temporal_window WHERE plan_id=@plan_id)<>(SELECT COUNT_BIG(*) FROM @bounded)
                OR EXISTS(SELECT 1 FROM @bounded b LEFT JOIN ctl.runtime_temporal_window w ON w.plan_id=@plan_id AND w.ordinal=b.ordinal
                    WHERE w.execution_id IS NULL OR w.execution_id<>b.execution_id OR w.namespace_fingerprint<>@namespace_fingerprint
                        OR w.policy_version<>@policy_version OR w.policy_fingerprint<>@policy_fingerprint OR w.partition_start_utc<>b.start_utc
                        OR w.partition_end_exclusive_utc<>b.end_utc OR w.extraction_start_utc<>b.extraction_utc OR w.due_at_utc<>b.due_utc OR w.deadline_at_utc<>b.deadline_utc)
                THROW 52423,N'TEMPORAL_PLAN_CONFLICT',1;
        END
        ELSE INSERT ctl.runtime_temporal_window SELECT @plan_id,ordinal,execution_id,@namespace_fingerprint,@policy_version,@policy_fingerprint,
            start_utc,end_utc,extraction_utc,due_utc,deadline_utc FROM @bounded;
        COMMIT TRANSACTION;
        SELECT COUNT_BIG(*) persisted_windows FROM ctl.runtime_temporal_window WHERE plan_id=@plan_id;
    END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION;THROW;END CATCH;
END;
GO
CREATE PROCEDURE ctl.usp_runtime_temporal_gaps @namespace_fingerprint CHAR(64),@maximum INT,@after_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    IF @maximum NOT BETWEEN 1 AND 64 OR @namespace_fingerprint IS NULL OR @after_utc IS NULL
        THROW 52424,N'TEMPORAL_RECONCILIATION_LIMIT',1;
    SELECT TOP(@maximum) w.plan_id,w.ordinal,w.execution_id,w.partition_start_utc,w.partition_end_exclusive_utc,
        CASE WHEN p.execution_id IS NOT NULL THEN N'PUBLISHED' WHEN a.execution_id IS NULL THEN N'NOT_STARTED'
            ELSE a.current_state END state,w.policy_version,w.policy_fingerprint
    FROM ctl.runtime_temporal_window w LEFT JOIN ctl.execution_attempt a ON a.execution_id=w.execution_id
    LEFT JOIN ctl.execution_publication_event p ON p.execution_id=w.execution_id
    WHERE w.namespace_fingerprint=@namespace_fingerprint AND w.partition_end_exclusive_utc>@after_utc
    ORDER BY w.partition_start_utc,w.partition_end_exclusive_utc;
END;
GO

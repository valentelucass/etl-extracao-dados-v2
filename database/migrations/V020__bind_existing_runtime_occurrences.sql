-- B54 pending: close existing-occurrence claims and initial-frontier scope gaps.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE OR ALTER FUNCTION ctl.fn_runtime_consumed_scope(@actual NVARCHAR(MAX), @write BIT,
    @namespace CHAR(64)=NULL) RETURNS BIT
AS
BEGIN
    -- Database administrators already own the catalog and synthetic migration validations.
    -- They remain forbidden by the runtime authority; this is not an application credential.
    IF IS_SRVROLEMEMBER(N'sysadmin')=1 OR IS_MEMBER(N'db_owner')=1 RETURN 1;
    IF ISJSON(@actual)<>1 OR DATALENGTH(@actual)>8000 RETURN 0;
    IF ORIGINAL_LOGIN()<>SUSER_SNAME() OR SUSER_SID(ORIGINAL_LOGIN())<>SUSER_SID() RETURN 0;
    IF CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') RETURN 0;
    IF EXISTS(
        SELECT 1 FROM ctl.runtime_authorization_decision d
        JOIN ctl.runtime_authorization_consumption c ON c.invocation_id=d.invocation_id
        JOIN ctl.runtime_identity_mapping m ON m.audit_reference=d.audit_reference
        JOIN ctl.runtime_authority_configuration a ON a.singleton=1 AND a.enabled=1
        JOIN ctl.runtime_identity_scope s ON s.original_sid=m.original_sid
            AND s.environment_name=JSON_VALUE(d.scope_material,'$.environment')
            AND s.source_instance=JSON_VALUE(d.scope_material,'$.source')
            AND s.tenant_scope=JSON_VALUE(d.scope_material,'$.tenant')
            AND s.workload=JSON_VALUE(d.scope_material,'$.workload')
            AND s.mode=JSON_VALUE(d.scope_material,'$.mode')
        WHERE m.original_sid=SUSER_SID(ORIGINAL_LOGIN()) AND m.revoked=0 AND s.revoked=0
            AND m.mapping_version=d.mapping_version AND s.scope_version=d.scope_version
            AND m.valid_from_utc<=SYSUTCDATETIME() AND m.valid_until_utc>SYSUTCDATETIME()
            AND d.authorized_at_utc<=SYSUTCDATETIME() AND d.valid_until_utc>SYSUTCDATETIME()
            AND d.authority_id=a.authority_id AND d.policy_fingerprint=a.policy_fingerprint
            AND s.policy_fingerprint=a.policy_fingerprint AND d.decision='ALLOW'
            AND (d.action=N'STATUS' AND @write=0 AND m.observer=1
                OR d.action=N'RUN' AND m.executor=1
                OR d.action=N'REPLAY' AND m.executor=1 AND m.replay=1
                OR d.action=N'FORCE_RUN' AND m.executor=1 AND m.force_run=1)
            AND (@namespace IS NULL OR @namespace=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',
                CONCAT(JSON_VALUE(d.scope_material,'$.environment'),N'|',JSON_VALUE(d.scope_material,'$.source'),
                N'|',JSON_VALUE(d.scope_material,'$.tenant'),N'|',JSON_VALUE(d.scope_material,'$.workload'),
                N'|',JSON_VALUE(d.scope_material,'$.mode'))),2)))
            -- V020: bind an existing occurrence to SQL-owned material, not just caller claims.
            AND NOT EXISTS(
                SELECT 1 FROM ctl.execution_attempt e
                JOIN ctl.execution_partition p ON p.partition_id=e.partition_id
                JOIN ctl.execution_cycle ec ON ec.cycle_id=e.cycle_id
                WHERE e.execution_id=d.execution_id AND (
                    p.partition_start_utc<>TRY_CONVERT(DATETIME2(3),JSON_VALUE(d.scope_material,'$.start'))
                    OR p.partition_end_exclusive_utc<>TRY_CONVERT(DATETIME2(3),JSON_VALUE(d.scope_material,'$.endExclusive'))
                    OR EXISTS(SELECT 1 FROM (VALUES
                    (p.environment_name,JSON_VALUE(d.scope_material,'$.environment')),
                    (p.source_instance,JSON_VALUE(d.scope_material,'$.source')),
                    (p.tenant_scope,JSON_VALUE(d.scope_material,'$.tenant')),
                    (p.entity_name,JSON_VALUE(d.scope_material,'$.entity')),
                    (p.entity_name,JSON_VALUE(d.scope_material,'$.workload')),
                    (p.execution_mode,JSON_VALUE(d.scope_material,'$.mode')),
                    (e.contract_version,JSON_VALUE(d.scope_material,'$.contractVersion')),
                    (e.contract_fingerprint,JSON_VALUE(d.scope_material,'$.contractHash')),
                    (e.configuration_version,JSON_VALUE(d.scope_material,'$.configurationVersion')),
                    (e.configuration_fingerprint,JSON_VALUE(d.scope_material,'$.configurationHash')),
                    (e.window_strategy,JSON_VALUE(d.scope_material,'$.strategy')),
                    (e.idempotency_key,JSON_VALUE(d.scope_material,'$.idempotency')),
                    (LOWER(COALESCE(CONVERT(NVARCHAR(36),e.replay_of_execution_id),N'')),JSON_VALUE(d.scope_material,'$.replayOf')),
                    (LOWER(CONVERT(NVARCHAR(36),e.cycle_id)),JSON_VALUE(d.scope_material,'$.cycle')),
                    (ec.plan_version,JSON_VALUE(d.scope_material,'$.planVersion')),
                    (ec.plan_fingerprint,JSON_VALUE(d.scope_material,'$.planHash'))
                    ) frozen(actual,declared) WHERE frozen.declared IS NULL
                        OR DATALENGTH(frozen.actual)<>DATALENGTH(frozen.declared)
                        OR frozen.actual COLLATE Latin1_General_100_BIN2<>frozen.declared COLLATE Latin1_General_100_BIN2)))
            AND NOT EXISTS(SELECT 1 FROM OPENJSON(@actual) x LEFT JOIN OPENJSON(d.scope_material) y
                ON x.[key] COLLATE Latin1_General_100_BIN2=y.[key] COLLATE Latin1_General_100_BIN2
                AND DATALENGTH(x.[key])=DATALENGTH(y.[key])
                WHERE y.[key] IS NULL OR x.type<>1 OR y.type<>1
                    OR (x.[key] IN(N'start',N'endExclusive') AND
                        (TRY_CONVERT(DATETIME2(3),x.value) IS NULL
                         OR TRY_CONVERT(DATETIME2(3),x.value)<>TRY_CONVERT(DATETIME2(3),y.value)))
                    OR (x.[key] NOT IN(N'start',N'endExclusive') AND
                        (DATALENGTH(x.value)<>DATALENGTH(y.value)
                         OR x.value COLLATE Latin1_General_100_BIN2<>y.value COLLATE Latin1_General_100_BIN2))))
        RETURN 1;
    RETURN 0;
END;
GO
CREATE OR ALTER PROCEDURE ctl.usp_control_plane_register_incremental_frontier
    @environment_name NVARCHAR(MAX),
    @source_instance NVARCHAR(MAX),
    @tenant_scope NVARCHAR(MAX),
    @entity_name NVARCHAR(MAX),
    @initial_contiguous_end_utc DATETIME2(3),
    @registered_at_utc DATETIME2(3)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT @environment_name AS [environment],@source_instance AS [source],@tenant_scope AS [tenant],@entity_name AS [entity],N'INCREMENTAL' AS [mode],CONVERT(NVARCHAR(23),@initial_contiguous_end_utc,126) AS [start] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,NULL)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF DATALENGTH(@environment_name) > 64
        OR DATALENGTH(@source_instance) > 256
        OR DATALENGTH(@tenant_scope) > 256
        OR DATALENGTH(@entity_name) > 256
        THROW 51321, N'Fronteira incremental excede o limite textual.', 1;

    SET @environment_name = LTRIM(RTRIM(@environment_name));
    SET @source_instance = LTRIM(RTRIM(@source_instance));
    SET @tenant_scope = LTRIM(RTRIM(@tenant_scope));
    SET @entity_name = LTRIM(RTRIM(@entity_name));

    IF NULLIF(LTRIM(RTRIM(@environment_name)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@source_instance)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@tenant_scope)), N'') IS NULL
        OR NULLIF(LTRIM(RTRIM(@entity_name)), N'') IS NULL
        OR @initial_contiguous_end_utc IS NULL
        OR @registered_at_utc IS NULL
        THROW 51321, N'Fronteira incremental inválida.', 1;

    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.source_catalog WHERE source_instance = @source_instance AND active = 1
    )
        THROW 51322, N'Fonte inativa não pode ter watermark operacional.', 1;

    DECLARE @existing_end DATETIME2(3);
    SELECT @existing_end = contiguous_partition_end_utc
    FROM ctl.incremental_publication_watermark WITH (UPDLOCK, HOLDLOCK)
    WHERE environment_name = @environment_name
      AND source_instance = @source_instance
      AND tenant_scope = @tenant_scope
      AND entity_name = @entity_name;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF @existing_end IS NULL
    BEGIN
        INSERT INTO ctl.incremental_publication_watermark (
            environment_name, source_instance, tenant_scope, entity_name,
            contiguous_partition_end_utc, registered_at_utc
        ) VALUES (
            @environment_name, @source_instance, @tenant_scope, @entity_name,
            @initial_contiguous_end_utc, @database_now_utc
        );
    END
    ELSE IF @existing_end <> @initial_contiguous_end_utc
    BEGIN
        THROW 51323, N'Fronteira incremental já existe e é imutável.', 1;
    END;

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_runtime_temporal_plan
    @plan_id UNIQUEIDENTIFIER,@namespace_fingerprint NVARCHAR(MAX),@policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX),@policy_material NVARCHAR(MAX),@windows NVARCHAR(MAX)
AS
BEGIN
    -- B54: validate current SQL authority consumption before any effect/read.
    DECLARE @b54_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@plan_id)) AS [cycle],@policy_version AS [contractVersion],@policy_fingerprint AS [contractHash],JSON_VALUE(@windows,'$[0].execution') AS [execution],JSON_VALUE(@windows,'$[0].start') AS [start],JSON_VALUE(@windows,'$[0].endExclusive') AS [endExclusive],JSON_VALUE(@policy_material,'$.mode') AS [mode],JSON_VALUE(@policy_material,'$.strategy') AS [strategy] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES);
    IF IS_SRVROLEMEMBER(N'sysadmin')<>1 AND IS_MEMBER(N'db_owner')<>1 AND (ISJSON(@windows)<>1 OR (SELECT COUNT(*) FROM OPENJSON(@windows))<>1) THROW 52500,N'RUNTIME_TEMPORAL_SINGLE_AUTHORIZED_WINDOW_REQUIRED',1;
    IF ctl.fn_runtime_consumed_scope(@b54_actual,1,@namespace_fingerprint)<>1 THROW 52500,N'RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED',1;

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

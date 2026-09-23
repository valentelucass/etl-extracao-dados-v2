-- Block 53. Administrative installation only. No identity seed or operational grant.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE ctl.runtime_authority_configuration (
    singleton TINYINT NOT NULL CONSTRAINT PK_runtime_authority_configuration PRIMARY KEY,
    authority_id UNIQUEIDENTIFIER NOT NULL,
    server_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    database_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    capability_seconds INT NOT NULL,
    enabled BIT NOT NULL,
    CONSTRAINT CK_runtime_authority_configuration CHECK(singleton=1 AND capability_seconds BETWEEN 1 AND 60
        AND policy_fingerprint NOT LIKE '%[^a-f0-9]%')
);
CREATE TABLE ctl.runtime_identity_mapping (
    original_sid VARBINARY(85) NOT NULL CONSTRAINT PK_runtime_identity_mapping PRIMARY KEY,
    audit_reference UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_runtime_identity_audit_reference UNIQUE,
    principal_kind VARCHAR(8) NOT NULL,
    mapping_version BIGINT NOT NULL,
    valid_from_utc DATETIME2(3) NOT NULL,
    valid_until_utc DATETIME2(3) NOT NULL,
    revoked BIT NOT NULL,
    observer BIT NOT NULL, executor BIT NOT NULL, replay BIT NOT NULL, force_run BIT NOT NULL,
    CONSTRAINT CK_runtime_identity_mapping CHECK(principal_kind IN('SERVICE','OPERATOR') AND mapping_version>0
        AND valid_from_utc<valid_until_utc)
);
CREATE TABLE ctl.runtime_identity_scope (
    scope_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_runtime_identity_scope PRIMARY KEY,
    original_sid VARBINARY(85) NOT NULL,
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    source_instance NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    tenant_scope NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    workload NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_version BIGINT NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    revoked BIT NOT NULL,
    CONSTRAINT UQ_runtime_identity_scope UNIQUE NONCLUSTERED(original_sid,environment_name,source_instance,tenant_scope,workload,mode),
    CONSTRAINT FK_runtime_identity_scope_mapping FOREIGN KEY(original_sid) REFERENCES ctl.runtime_identity_mapping(original_sid),
    CONSTRAINT CK_runtime_identity_scope CHECK(scope_version>0 AND policy_fingerprint NOT LIKE '%[^a-f0-9]%'
        AND mode IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY'))
);
CREATE TABLE ctl.runtime_authorization_decision (
    invocation_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_runtime_authorization_decision PRIMARY KEY,
    receipt_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_runtime_authorization_receipt UNIQUE,
    authority_id UNIQUEIDENTIFIER NOT NULL,
    action NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    scope_material NVARCHAR(4000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_hash CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    audit_reference UNIQUEIDENTIFIER NULL,
    mapping_version BIGINT NULL, scope_version BIGINT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    decision VARCHAR(5) NOT NULL, reason VARCHAR(40) NOT NULL,
    authorized_at_utc DATETIME2(3) NOT NULL, valid_until_utc DATETIME2(3) NOT NULL,
    CONSTRAINT CK_runtime_authorization_decision CHECK(decision IN('ALLOW','DENY') AND authorized_at_utc<valid_until_utc
        AND (decision='DENY' OR audit_reference IS NOT NULL AND mapping_version IS NOT NULL AND scope_version IS NOT NULL))
);
CREATE TABLE ctl.runtime_authorization_consumption (
    invocation_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_runtime_authorization_consumption PRIMARY KEY,
    fence BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT UQ_runtime_authorization_fence UNIQUE,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    consumed_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT FK_runtime_authorization_consumption_decision FOREIGN KEY(invocation_id)
        REFERENCES ctl.runtime_authorization_decision(invocation_id)
);
CREATE INDEX IX_runtime_authorization_consumption_execution ON ctl.runtime_authorization_consumption(execution_id,fence);
GO
CREATE TRIGGER ctl.tr_runtime_authorization_decision_immutable ON ctl.runtime_authorization_decision
INSTEAD OF UPDATE,DELETE AS THROW 52410,N'AUTHORIZATION_AUDIT_IMMUTABLE',1;
GO
CREATE TRIGGER ctl.tr_runtime_authorization_consumption_immutable ON ctl.runtime_authorization_consumption
INSTEAD OF UPDATE,DELETE AS THROW 52410,N'AUTHORIZATION_CONSUMPTION_IMMUTABLE',1;
GO
CREATE PROCEDURE ctl.usp_runtime_authorization
    @operation NVARCHAR(MAX), @invocation_id UNIQUEIDENTIFIER, @authority_id UNIQUEIDENTIFIER,
    @action NVARCHAR(MAX), @execution_id UNIQUEIDENTIFIER, @scope_material NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX), @receipt_id UNIQUEIDENTIFIER=NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2 NOT IN(N'AUTHORIZE',N'CONSUME')
        OR DATALENGTH(@operation)<>2*LEN(@operation) OR @invocation_id IS NULL OR @execution_id IS NULL
        OR @authority_id IS NULL OR @action IS NULL OR @action COLLATE Latin1_General_100_BIN2 NOT IN(N'RUN',N'REPLAY',N'FORCE_RUN',N'STATUS')
        OR DATALENGTH(@action)<>2*LEN(@action)
        OR @scope_material IS NULL OR DATALENGTH(@scope_material)>8000 OR ISJSON(@scope_material)<>1
        OR @policy_fingerprint IS NULL OR DATALENGTH(@policy_fingerprint)<>128 OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'
        THROW 52411,N'AUTHORIZATION_REQUEST_INVALID',1;
    DECLARE @keys TABLE(name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 PRIMARY KEY,maximum INT NOT NULL);
    INSERT @keys VALUES(N'version',32),(N'invocation',36),(N'action',32),(N'execution',36),(N'environment',32),
        (N'source',128),(N'tenant',128),(N'workload',128),(N'entity',128),(N'mode',16),(N'start',30),(N'endExclusive',30),
        (N'strategy',32),(N'idempotency',200),(N'replayOf',36),(N'cycle',36),(N'planVersion',128),(N'planHash',64),
        (N'contractVersion',128),(N'contractHash',64),(N'configurationVersion',128),(N'configurationHash',64);
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@scope_material))<>22
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) j LEFT JOIN @keys k ON k.name=j.[key] COLLATE Latin1_General_100_BIN2
            WHERE k.name IS NULL OR j.type<>1 OR DATALENGTH(j.value)>2*k.maximum
                OR (j.[key]<>N'replayOf' AND DATALENGTH(j.value)=0))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) GROUP BY [key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)<>1)
        OR JSON_VALUE(@scope_material,'$.version') COLLATE Latin1_General_100_BIN2<>N'runtime-scope-v1'
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')) IS NULL
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')) IS NULL
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.invocation')),'00000000-0000-0000-0000-000000000000')<>@invocation_id
        OR ISNULL(TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.execution')),'00000000-0000-0000-0000-000000000000')<>@execution_id
        OR JSON_VALUE(@scope_material,'$.action') COLLATE Latin1_General_100_BIN2<>@action
        OR TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.cycle')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive')) IS NULL
        OR TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.start'))>=TRY_CONVERT(DATETIME2(3),JSON_VALUE(@scope_material,'$.endExclusive'))
        OR EXISTS(SELECT 1 FROM OPENJSON(@scope_material) WHERE [key] IN(N'planHash',N'contractHash',N'configurationHash')
            AND (DATALENGTH(value)<>128 OR value COLLATE Latin1_General_100_BIN2 LIKE '%[^a-f0-9]%'))
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@until DATETIME2(3),@reason VARCHAR(40)='AUTHORITY_UNCONFIGURED',
        @sid VARBINARY(85)=SUSER_SID(ORIGINAL_LOGIN()),@audit UNIQUEIDENTIFIER,@mapping BIGINT,@scope_version BIGINT,
        @hash CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@scope_material),2)),@seconds INT=1,
        @environment NVARCHAR(32)=JSON_VALUE(@scope_material,'$.environment'),
        @source NVARCHAR(128)=JSON_VALUE(@scope_material,'$.source'),@tenant NVARCHAR(128)=JSON_VALUE(@scope_material,'$.tenant'),
        @workload NVARCHAR(128)=JSON_VALUE(@scope_material,'$.workload'),@mode NVARCHAR(16)=JSON_VALUE(@scope_material,'$.mode');
    IF @environment IS NULL OR @source IS NULL OR @tenant IS NULL OR @workload IS NULL OR @mode IS NULL
        OR @mode COLLATE Latin1_General_100_BIN2 NOT IN(N'INCREMENTAL',N'BACKFILL',N'BOOTSTRAP',N'REPLAY')
        OR (@action=N'REPLAY' AND @mode<>N'REPLAY') OR (@mode=N'REPLAY' AND @action NOT IN(N'REPLAY',N'STATUS'))
        OR (@mode=N'REPLAY' AND TRY_CONVERT(UNIQUEIDENTIFIER,JSON_VALUE(@scope_material,'$.replayOf')) IS NULL)
        OR (@mode<>N'REPLAY' AND DATALENGTH(JSON_VALUE(@scope_material,'$.replayOf'))<>0)
        THROW 52411,N'AUTHORIZATION_SCOPE_INVALID',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock_result INT,@lock_resource NVARCHAR(255)=N'V2_AUTH_'+CONVERT(NVARCHAR(36),@invocation_id);
        EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
        IF @lock_result<0 THROW 52412,N'AUTHORIZATION_LOCK_UNAVAILABLE',1;
        SET @now=SYSUTCDATETIME();
        IF EXISTS(SELECT 1 FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1 AND enabled=1
            AND authority_id=@authority_id AND server_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2
            AND database_name=DB_NAME() COLLATE Latin1_General_100_BIN2 AND policy_fingerprint=@policy_fingerprint)
        BEGIN
            SELECT @seconds=capability_seconds FROM ctl.runtime_authority_configuration WITH(HOLDLOCK) WHERE singleton=1;
            SET @reason='IDENTITY_UNMAPPED';
            IF ISNULL(CONVERT(NVARCHAR(40),CONNECTIONPROPERTY('auth_scheme')),N'') NOT IN(N'NTLM',N'KERBEROS') SET @reason='AUTHENTICATION_INVALID';
            ELSE IF @sid IS NULL OR SUSER_SID()<>@sid OR ORIGINAL_LOGIN()<>SUSER_SNAME() SET @reason='CONTEXT_CHANGED';
            ELSE IF IS_SRVROLEMEMBER('sysadmin')=1 OR IS_MEMBER('db_owner')=1
                OR HAS_PERMS_BY_NAME(NULL,NULL,'CONTROL SERVER')=1 OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')=1
                OR HAS_PERMS_BY_NAME(N'ctl.runtime_identity_mapping','OBJECT','SELECT')=1
                OR HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY ROLE')=1 SET @reason='PRIVILEGE_EXCESSIVE';
            ELSE IF NOT EXISTS(SELECT 1 FROM sys.server_principals WHERE sid=@sid AND type IN('U','G') AND is_disabled=0)
                SET @reason='IDENTITY_DISABLED_OR_UNVERIFIABLE';
            ELSE
            BEGIN
                SELECT @audit=audit_reference,@mapping=mapping_version,@until=valid_until_utc
                FROM ctl.runtime_identity_mapping WITH(HOLDLOCK) WHERE original_sid=@sid AND revoked=0
                    AND valid_from_utc<=@now AND valid_until_utc>@now
                    AND ((@action=N'STATUS' AND observer=1) OR (@action=N'RUN' AND executor=1)
                        OR (@action=N'REPLAY' AND executor=1 AND replay=1) OR (@action=N'FORCE_RUN' AND executor=1 AND force_run=1));
                IF @audit IS NOT NULL
                BEGIN
                    SET @reason='SCOPE_REJECTED';
                    SELECT @scope_version=scope_version FROM ctl.runtime_identity_scope WITH(HOLDLOCK)
                    WHERE original_sid=@sid AND environment_name=@environment AND source_instance=@source AND tenant_scope=@tenant
                        AND workload=@workload AND mode=@mode AND policy_fingerprint=@policy_fingerprint AND revoked=0;
                    IF @scope_version IS NOT NULL SET @reason='AUTHORIZED';
                END;
            END;
        END;
        IF @until IS NULL OR @until>DATEADD(SECOND,@seconds,@now) SET @until=DATEADD(SECOND,@seconds,@now);
        IF @operation=N'AUTHORIZE'
        BEGIN
            IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
                AND (authority_id<>@authority_id OR action<>@action OR execution_id<>@execution_id OR scope_hash<>@hash
                    OR scope_material<>@scope_material OR DATALENGTH(scope_material)<>DATALENGTH(@scope_material)
                    OR policy_fingerprint<>@policy_fingerprint OR ISNULL(audit_reference,'00000000-0000-0000-0000-000000000000')
                        <>ISNULL(@audit,'00000000-0000-0000-0000-000000000000')))
                THROW 52413,N'AUTHORIZATION_INVOCATION_CONFLICT',1;
            IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
                INSERT ctl.runtime_authorization_decision VALUES(@invocation_id,NEWID(),@authority_id,@action,@execution_id,
                    @scope_material,@hash,@audit,@mapping,@scope_version,@policy_fingerprint,
                    CASE WHEN @reason='AUTHORIZED' THEN 'ALLOW' ELSE 'DENY' END,@reason,@now,@until);
            COMMIT TRANSACTION;
            SELECT receipt_id,decision,reason,audit_reference,mapping_version,scope_version,policy_fingerprint,
                authorized_at_utc,valid_until_utc,scope_hash,CAST(NULL AS BIGINT) fence
            FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation_id;
            RETURN;
        END;
        IF @reason<>'AUTHORIZED' THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id
            AND receipt_id=@receipt_id AND decision='ALLOW' AND authority_id=@authority_id AND action=@action AND execution_id=@execution_id
            AND scope_hash=@hash AND scope_material=@scope_material AND DATALENGTH(scope_material)=DATALENGTH(@scope_material)
            AND mapping_version=@mapping AND scope_version=@scope_version AND audit_reference=@audit
            AND policy_fingerprint=@policy_fingerprint AND authorized_at_utc<=@now AND valid_until_utc>@now)
            THROW 52414,N'AUTHORIZATION_CONSUMPTION_REJECTED',1;
        IF EXISTS(SELECT 1 FROM ctl.runtime_authorization_consumption WITH(UPDLOCK,HOLDLOCK) WHERE invocation_id=@invocation_id)
            THROW 52415,N'AUTHORIZATION_ALREADY_CONSUMED',1;
        -- Intent links the exact existing runtime occurrence. Recovery reads that occurrence;
        -- NOT_FOUND permits only its original idempotent start, never a replacement execution_id.
        INSERT ctl.runtime_authorization_consumption(invocation_id,execution_id,consumed_at_utc) VALUES(@invocation_id,@execution_id,@now);
        COMMIT TRANSACTION;
        SELECT d.receipt_id,d.decision,d.reason,d.audit_reference,d.mapping_version,d.scope_version,d.policy_fingerprint,
            d.authorized_at_utc,d.valid_until_utc,d.scope_hash,c.fence
        FROM ctl.runtime_authorization_decision d JOIN ctl.runtime_authorization_consumption c ON c.invocation_id=d.invocation_id
        WHERE d.invocation_id=@invocation_id;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
-- Exact observer entrypoint; granting STATUS never grants SEAL/RESUME.
CREATE PROCEDURE ctl.usp_runtime_status
    @execution_id UNIQUEIDENTIFIER,@operation NVARCHAR(MAX),@expected_identity NVARCHAR(MAX),
    @contract_material NVARCHAR(MAX),@policy_version NVARCHAR(MAX),@policy_fingerprint NVARCHAR(MAX),
    @expected_revision NVARCHAR(MAX),@entity NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    IF @operation IS NULL OR @operation COLLATE Latin1_General_100_BIN2<>N'READ' OR DATALENGTH(@operation)<>8
        THROW 52416,N'STATUS_CANNOT_MUTATE_RUNTIME',1;
    EXEC ctl.usp_runtime_recovery @execution_id,N'READ',@expected_identity,@contract_material,
        @policy_version,@policy_fingerprint,@expected_revision,@entity;
END;
GO

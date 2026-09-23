:On Error exit
SET NOCOUNT ON;SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52430,N'LOCAL_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
GO
DECLARE @authority UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000005300',
    @invocation UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000005301',
    @execution UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000005302',@policy CHAR(64)=REPLICATE('a',64);
DECLARE @scope NVARCHAR(MAX)=N'{"version":"runtime-scope-v1","invocation":"00000000-0000-0000-0000-000000005301","action":"RUN","execution":"00000000-0000-0000-0000-000000005302","environment":"LOCAL_SHADOW","source":"SYNTHETIC_B53_AUTH","tenant":"SYNTHETIC_B53_AUTH","workload":"coletas","entity":"coletas","mode":"BACKFILL","start":"2024-02-01T00:00:00Z","endExclusive":"2024-03-01T00:00:00Z","strategy":"INTERVAL","idempotency":"synthetic53","replayOf":"","cycle":"00000000-0000-0000-0000-000000005302","planVersion":"synthetic1","planHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","contractVersion":"synthetic1","contractHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","configurationVersion":"synthetic1","configurationHash":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}';
EXEC ctl.usp_runtime_authorization N'AUTHORIZE',@invocation,@authority,N'RUN',@execution,@scope,@policy;
EXEC ctl.usp_runtime_authorization N'AUTHORIZE',@invocation,@authority,N'RUN',@execution,@scope,@policy;
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation)<>1
    OR EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation AND (decision<>'DENY' OR reason<>'AUTHORITY_UNCONFIGURED'))
    THROW 52430,N'UNCONFIGURED_OR_IDEMPOTENCY_FAILED',1;
-- Only synthetic authority configuration; no mapping, principal or operational permission.
INSERT ctl.runtime_authority_configuration VALUES(1,@authority,CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')),DB_NAME(),@policy,30,1);
SET @invocation='00000000-0000-0000-0000-000000005303';
SET @scope=JSON_MODIFY(JSON_MODIFY(@scope,'$.invocation',CONVERT(NVARCHAR(36),@invocation)),'$.action',N'STATUS');
EXEC ctl.usp_runtime_authorization N'AUTHORIZE',@invocation,@authority,N'STATUS',@execution,@scope,@policy;
IF IS_SRVROLEMEMBER('sysadmin')=1 AND NOT EXISTS(SELECT 1 FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation AND decision='DENY' AND reason='PRIVILEGE_EXCESSIVE')
    THROW 52430,N'ADMINISTRATIVE_IDENTITY_MUST_NOT_AUTHORIZE',1;
IF EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping) OR EXISTS(SELECT 1 FROM ctl.runtime_authorization_consumption)
    THROW 52430,N'NO_IDENTITY_OR_CONSUMPTION_ALLOWED',1;
DECLARE @plan UNIQUEIDENTIFIER='00000000-0000-0000-0000-000000005304',@namespace CHAR(64)=REPLICATE('b',64),
    @material NVARCHAR(MAX)=N'{"version":"synthetic-temporal-v1","zone":"Etc/UTC","mode":"BACKFILL","strategy":"INTERVAL","cadence":"CIVIL_MONTH","boundary":"00:00","lookback":"PT2H","stabilization":"PT1H","sla":"PT4H","deadline":"PT2H","concurrency":1,"maximumBacklog":2,"maximumReconciliation":4,"maximumDegraded":0,"blackouts":[]}',@hash CHAR(64),
    @windows NVARCHAR(MAX)=N'[{"execution":"00000000-0000-0000-0000-000000005305","start":"2024-02-01T00:00:00Z","endExclusive":"2024-03-01T00:00:00Z","extractionStart":"2024-01-31T22:00:00Z","due":"2024-03-01T01:00:00Z","deadline":"2024-03-01T03:00:00Z"}]';
SET @hash=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@material),2));
EXEC ctl.usp_runtime_temporal_plan @plan,@namespace,N'synthetic-temporal-v1',@hash,@material,@windows;
EXEC ctl.usp_runtime_temporal_plan @plan,@namespace,N'synthetic-temporal-v1',@hash,@material,@windows;
EXEC ctl.usp_runtime_temporal_gaps @namespace,1,'2024-01-01T00:00:00';
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_temporal_window WHERE plan_id=@plan)<>1 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_temporal_policy WHERE policy_version=N'synthetic-temporal-v1' AND policy_fingerprint=@hash)<>1
    THROW 52430,N'TEMPORAL_IDEMPOTENCY_FAILED',1;
IF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 52430,N'ROLLBACK_SCOPE_LOST',1;
ROLLBACK TRANSACTION;
PRINT N'WINDOWS_AUTHORITY_DENIAL_AND_TEMPORAL_SQL_PASS_ROLLBACK_ONLY';

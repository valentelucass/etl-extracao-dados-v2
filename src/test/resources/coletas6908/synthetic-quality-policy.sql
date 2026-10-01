SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @version NVARCHAR(128) = ?, @source NVARCHAR(128) = ?, @tenant NVARCHAR(128) = ?;
IF DB_NAME() <> N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT < 2 OR XACT_STATE() <> 1
    THROW 53571, N'COL_6908_POLICY_TRIAL_SCOPE', 1;
IF @source IS NULL OR DATALENGTH(@source) = 0 OR @tenant IS NULL OR DATALENGTH(@tenant) = 0
    THROW 53571, N'COL_6908_POLICY_BINDING', 1;
IF EXISTS (SELECT 1 FROM ctl.data_quality_policy WHERE policy_version = @version)
    THROW 53571, N'COL_6908_POLICY_VERSION_REUSED', 1;

DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @entity NVARCHAR(128) = N'coletas';
DECLARE @mode NVARCHAR(16) = N'BACKFILL';
DECLARE @owner NVARCHAR(64) = N'coletas-pilot-owner';
DECLARE @retention NVARCHAR(128) = N'coletas-rollback-only-v1';
DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @scope_material NVARCHAR(2000) = CONCAT(
    N'dq-scope-v1|',
    DATALENGTH(@environment), N':', @environment, N'|',
    DATALENGTH(@source), N':', @source, N'|',
    DATALENGTH(@tenant), N':', @tenant, N'|',
    DATALENGTH(@entity), N':', @entity, N'|',
    DATALENGTH(@mode), N':', @mode
);
DECLARE @scope CHAR(64) = LOWER(CONVERT(CHAR(64),
    HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @scope_material)), 2));
DECLARE @policy_material NVARCHAR(4000) = CONCAT(
    N'dq-policy-v1|', DATALENGTH(@version), N':', @version, N'|',
    @scope, N'|4|3600|',
    DATALENGTH(@owner), N':', @owner, N'|',
    DATALENGTH(@owner), N':', @owner, N'|',
    DATALENGTH(@retention), N':', @retention, N'|',
    DATALENGTH(@owner), N':', @owner, N'|',
    CONVERT(NVARCHAR(33), @now, 126), N'|',
    N'1:COUNT_EQUATION:0:0:', DATALENGTH(@owner), N':', @owner, N'|',
    N'2:PAGE_TERMINALITY:0:0:', DATALENGTH(@owner), N':', @owner, N'|',
    N'3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(@owner), N':', @owner, N'|',
    N'4:QUARANTINE_SLA:0:0:', DATALENGTH(@owner), N':', @owner
);
DECLARE @fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64),
    HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @policy_material)), 2));

INSERT INTO ctl.data_quality_policy (
    policy_version, policy_fingerprint, scope_fingerprint, expected_checks,
    quarantine_sla_seconds, threshold_owner_role, quarantine_sla_owner_role,
    retention_policy_version, retention_owner_role, policy_state,
    effective_from_utc, recorded_at_utc
) VALUES (
    @version, @fingerprint, @scope, 4, 3600, @owner, @owner,
    @retention, @owner, N'RATIFIED', @now, @now
);
INSERT INTO ctl.data_quality_check_policy (
    policy_version, policy_fingerprint, check_ordinal, check_code,
    maximum_failed_rows, maximum_failure_basis_points, threshold_owner_role
) VALUES
    (@version, @fingerprint, 1, N'COUNT_EQUATION', 0, 0, @owner),
    (@version, @fingerprint, 2, N'PAGE_TERMINALITY', 0, 0, @owner),
    (@version, @fingerprint, 3, N'PROMOTION_RECONCILIATION', 0, 0, @owner),
    (@version, @fingerprint, 4, N'QUARANTINE_SLA', 0, 0, @owner);

SELECT @version, @fingerprint, @scope,
    (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy
     WHERE policy_version = @version AND policy_fingerprint = @fingerprint
       AND scope_fingerprint = @scope),
    (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy
     WHERE policy_version = @version AND policy_fingerprint = @fingerprint);

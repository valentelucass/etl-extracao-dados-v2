SET NOCOUNT ON; SET XACT_ABORT ON; DECLARE @namespace NVARCHAR(128)=?, @entity NVARCHAR(128)=?; BEGIN TRY BEGIN TRANSACTION;
DECLARE @policy_effective DATETIME2(3) = CONVERT(DATETIME2(3),'2000-01-01T00:00:00.001');
DECLARE @policy_environment NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @policy_source NVARCHAR(128) = @namespace;
DECLARE @policy_tenant NVARCHAR(128) = @namespace;
DECLARE @policy_entity NVARCHAR(128) = @entity;
DECLARE @policy_mode NVARCHAR(16) = ?;
DECLARE @scope_fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-scope-v1|', DATALENGTH(@policy_environment), N':', @policy_environment,
        N'|', DATALENGTH(@policy_source), N':', @policy_source,
        N'|', DATALENGTH(@policy_tenant), N':', @policy_tenant,
        N'|', DATALENGTH(@policy_entity), N':', @policy_entity,
        N'|', DATALENGTH(@policy_mode), N':', @policy_mode))), 2));
DECLARE @policy_version NVARCHAR(128) = @namespace+N'_'+@entity+N'_'+@policy_mode;
DECLARE @policy_fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-policy-v1|', DATALENGTH(@policy_version), N':', @policy_version,
        N'|', @scope_fingerprint, N'|4|3600|',
        DATALENGTH(N'quality-owner'), N':quality-owner|',
        DATALENGTH(N'quarantine-owner'), N':quarantine-owner|',
        DATALENGTH(N'synthetic-coletas-retention-v1'), N':synthetic-coletas-retention-v1|',
        DATALENGTH(N'retention-owner'), N':retention-owner|',
        CONVERT(NVARCHAR(33), @policy_effective, 126),
        N'|1:COUNT_EQUATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|2:PAGE_TERMINALITY:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|4:QUARANTINE_SLA:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner'))), 2));
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=@policy_version AND policy_fingerprint=@policy_fingerprint)
BEGIN
INSERT INTO ctl.data_quality_policy VALUES (
    @policy_version, @policy_fingerprint, @scope_fingerprint, 4, 3600,
    N'quality-owner', N'quarantine-owner', N'synthetic-coletas-retention-v1',
    N'retention-owner', N'RATIFIED', @policy_effective, SYSUTCDATETIME());
INSERT INTO ctl.data_quality_check_policy VALUES
    (@policy_version, @policy_fingerprint, 1, N'COUNT_EQUATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 2, N'PAGE_TERMINALITY', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 3, N'PROMOTION_RECONCILIATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 4, N'QUARANTINE_SLA', 0, 0, N'quality-owner');

END;
COMMIT TRANSACTION; SELECT @policy_version,@policy_fingerprint; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;

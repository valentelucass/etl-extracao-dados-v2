-- Exercício negativo, sintético e rollback-only da dupla ratificação do V2-045a.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51620, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

DECLARE @policy_fingerprint NVARCHAR(64) = REPLICATE(N'a', 64);
DECLARE @shared_evidence NVARCHAR(64) = REPLICATE(N'b', 64);
DECLARE @independence_error INT = NULL;
BEGIN TRY
    EXEC ctl.usp_approve_staging_retention_policy
        '00000000-0000-0000-0000-000000000539',
        N'LOCAL_SHADOW', N'SYNTHETIC_RATIFICATION_SOURCE',
        N'SYNTHETIC_RATIFICATION_TENANT', N'SYNTHETIC_RATIFICATION_ENTITY',
        N'NON_PUBLISHED_TERMINAL', N'synthetic-shared-ratification-v1',
        @policy_fingerprint, 1,
        @shared_evidence, N'shared-owner-role',
        @shared_evidence, N'shared-owner-role';
END TRY
BEGIN CATCH
    SET @independence_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @independence_error IS NULL OR @independence_error <> 51503
    THROW 51623, N'Política aceitou a mesma evidência/papel como dupla ratificação.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51624, N'O rollback da ratificação não independente não encerrou o escopo.', 1;
GO

BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_RATIFICATION_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_RATIFICATION_TENANT';
DECLARE @entity NVARCHAR(128) = N'SYNTHETIC_RATIFICATION_ENTITY';
DECLARE @policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000540';
DECLARE @plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000541';
DECLARE @policy_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @data_owner_evidence CHAR(64) = REPLICATE('b', 64);
DECLARE @compliance_evidence CHAR(64) = REPLICATE('c', 64);

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @now;
EXEC ctl.usp_approve_staging_retention_policy
    @policy, N'LOCAL_SHADOW', @source, @tenant, @entity,
    N'NON_PUBLISHED_TERMINAL', N'synthetic-ratification-v1',
    @policy_fingerprint, 1,
    @data_owner_evidence, N'data-owner', @compliance_evidence, N'compliance';

UPDATE ctl.staging_retention_policy_event
SET authority_evidence_fingerprint = REPLICATE('d', 64)
WHERE policy_id = @policy AND event_action = N'DATA_OWNER_APPROVED';

DECLARE @ratification_error INT = NULL;
BEGIN TRY
    EXEC stg.usp_plan_staging_lifecycle
        @plan, @policy, N'synthetic-ratification-v1', @policy_fingerprint,
        N'LOCAL_SHADOW', @source, @tenant, @entity,
        NULL, NULL,
        1, 1, 1, 1, 1, 1, 10, 1;
END TRY
BEGIN CATCH
    SET @ratification_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @ratification_error IS NULL OR @ratification_error <> 51524
    THROW 51621, N'Planner não falhou fechado diante de ratificação divergente.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51622, N'O rollback da ratificação não encerrou o escopo sintético.', 1;

PRINT N'Ratificação não independente/divergente bloqueada e estado revertido com sucesso.';

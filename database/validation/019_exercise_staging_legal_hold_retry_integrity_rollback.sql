-- Exercício negativo, sintético e rollback-only do retry de legal hold ativo do V2-045a.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51670, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_HOLD_RETRY_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_HOLD_RETRY_TENANT';
DECLARE @entity NVARCHAR(128) = N'SYNTHETIC_HOLD_RETRY_ENTITY';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000590';
DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000591';
DECLARE @hold UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000592';
DECLARE @contract_fingerprint NVARCHAR(64) = REPLICATE(N'a', 64);
DECLARE @configuration_fingerprint NVARCHAR(64) = REPLICATE(N'b', 64);
DECLARE @cycle_fingerprint NVARCHAR(64) = REPLICATE(N'c', 64);
DECLARE @hold_evidence NVARCHAR(64) = REPLICATE(N'd', 64);
DECLARE @interval_start_utc DATETIME2(3) = DATEADD(HOUR, -2, @now);
DECLARE @interval_end_utc DATETIME2(3) = DATEADD(HOUR, -1, @now);

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle, N'hold-retry-cycle-v1', @cycle_fingerprint, @now;
EXEC ctl.usp_control_plane_start_execution
    @execution, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @interval_start_utc, @interval_end_utc, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-hold-retry', NULL, 3600, @now;

EXEC ctl.usp_place_staging_legal_hold
    @hold, @execution, N'LEGAL_REVIEW', @hold_evidence, N'compliance';

INSERT INTO ctl.staging_legal_hold_event (
    hold_id, execution_id, event_action, reason_code,
    authority_evidence_fingerprint, owner_role, hold_scope, occurred_at_utc
)
SELECT hold_id, execution_id, N'RELEASED', reason_code,
       REPLICATE('e', 64), N'spurious-release-role', hold_scope,
       DATEADD(MILLISECOND, 1, held_at_utc)
FROM ctl.staging_legal_hold
WHERE hold_id = @hold AND released_at_utc IS NULL;

DECLARE @retry_error INT = NULL;
BEGIN TRY
    EXEC ctl.usp_place_staging_legal_hold
        @hold, @execution, N'LEGAL_REVIEW', @hold_evidence, N'compliance';
END TRY
BEGIN CATCH
    SET @retry_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @retry_error IS NULL OR @retry_error <> 51526
    THROW 51671, N'Retry exato aceitou RELEASED espúrio enquanto o hold permanecia ativo.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51672, N'O rollback do retry íntegro de legal hold não encerrou o escopo.', 1;

PRINT N'Retry de legal hold ativo falhou fechado diante de RELEASED espúrio; estado revertido.';

-- Negative gate isolado: generic-only terminal nunca pode receber sidecar tipado tardio.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51830, N'O gate de sidecar tardio aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @cycle_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000730';
DECLARE @execution_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000731';
DECLARE @source_key NVARCHAR(256) = N'INTEGER:900';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('d', 64);
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @identity_row_hash CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'usuarios-identity-envelope-v1|', DATALENGTH(@source_key), N'|', @source_key
    ))
), 2));
DECLARE @identity_presence_hash CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), N'usuarios-identity-presence-v1')
), 2));

EXEC ctl.usp_control_plane_register_source N'SYNTHETIC_LATE_SIDECAR_SOURCE', N'GRAPHQL', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle_id, N'synthetic-late-sidecar-plan-v1', @plan_fingerprint, @now;
EXEC ctl.usp_control_plane_start_execution
    @execution_id, @cycle_id, N'LOCAL_SHADOW', N'SYNTHETIC_LATE_SIDECAR_SOURCE',
    N'SYNTHETIC_LATE_SIDECAR_TENANT', N'usuarios', N'BACKFILL',
    '2026-01-01T08:00:00', '2026-01-01T09:00:00',
    N'GRAPHQL_RESTART_FROM_BEGINNING', N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint, N'users-late-sidecar', NULL, 3600, @now;

-- Simula o bypass genérico permitido ao runtime, sem usar o wrapper vertical.
EXEC stg.usp_stage_record
    @execution_id, 1, 1, @source_key,
    N'usuarios-identity-envelope-v1', @identity_row_hash,
    N'usuarios-identity-presence-v1', @identity_presence_hash,
    NULL, N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @execution_id, N'EXTRACTING', N'BLOCKED', N'SYNTHETIC_LATE_SIDECAR_BLOCK', @now;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @execution_id AND current_state = N'BLOCKED'
) OR NOT EXISTS (
    SELECT 1 FROM stg.execution_record WHERE execution_id = @execution_id
) OR EXISTS (
    SELECT 1 FROM stg.usuario_record WHERE execution_id = @execution_id
)
    THROW 51831, N'A fixture generic-only terminal divergiu.', 1;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51832, N'A fixture consumiu o escopo rollback-only antes do negative gate.', 1;

-- Último comando deliberadamente falho: THROW/XACT_ABORT pode invalidar a transação.
DECLARE @late_sidecar_error INT = NULL;
BEGIN TRY
    EXEC stg.usp_stage_usuario_record
        @execution_id, 1, 1, @source_key, N'INTEGER', N'VALUE',
        N'Sidecar tardio recusado', N'VALID', NULL, @now;
END TRY
BEGIN CATCH
    SET @late_sidecar_error = ERROR_NUMBER();
END CATCH;

IF @late_sidecar_error <> 51703
BEGIN
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW 51833, N'O sidecar tardio não falhou com o fence esperado.', 1;
END;

IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 51834, N'O rollback do gate de sidecar tardio não encerrou a transação.', 1;

PRINT N'Sidecar tipado tardio bloqueado e estado revertido com sucesso.';

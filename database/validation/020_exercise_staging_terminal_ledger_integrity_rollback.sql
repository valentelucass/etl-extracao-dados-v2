-- Exercício negativo, sintético e rollback-only da prova terminal do V2-045a.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51680, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @old_start DATETIME2(3) = DATEADD(DAY, -3, @now);
DECLARE @old_terminal DATETIME2(3) = DATEADD(DAY, -2, @now);
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_TERMINAL_LEDGER_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_TERMINAL_LEDGER_TENANT';
DECLARE @entity NVARCHAR(128) = N'SYNTHETIC_TERMINAL_LEDGER_ENTITY';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000600';
DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000601';
DECLARE @policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000602';
DECLARE @plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000603';
DECLARE @contract_fingerprint NVARCHAR(64) = REPLICATE(N'a', 64);
DECLARE @configuration_fingerprint NVARCHAR(64) = REPLICATE(N'b', 64);
DECLARE @cycle_fingerprint NVARCHAR(64) = REPLICATE(N'c', 64);
DECLARE @policy_fingerprint NVARCHAR(64) = REPLICATE(N'd', 64);
DECLARE @owner_evidence NVARCHAR(64) = REPLICATE(N'e', 64);
DECLARE @compliance_evidence NVARCHAR(64) = REPLICATE(N'f', 64);
DECLARE @row_hash NVARCHAR(64) = REPLICATE(N'1', 64);
DECLARE @presence_hash NVARCHAR(64) = REPLICATE(N'2', 64);
DECLARE @interval_start_utc DATETIME2(3) = DATEADD(HOUR, -8, @now);
DECLARE @interval_end_utc DATETIME2(3) = DATEADD(HOUR, -7, @now);

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @old_start;
EXEC ctl.usp_control_plane_start_cycle
    @cycle, N'terminal-ledger-cycle-v1', @cycle_fingerprint, @old_start;
EXEC ctl.usp_control_plane_start_execution
    @execution, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @interval_start_utc, @interval_end_utc, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-terminal-ledger', NULL, 3600, @now;
EXEC stg.usp_stage_record
    @execution, 1, 1, N'terminal-ledger-key', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @old_terminal, N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTED', N'STAGED', N'STAGE_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'STAGED', N'FAILED', N'SYNTHETIC_FAILURE', @now;

UPDATE ctl.execution_attempt
SET started_at_utc = @old_start, terminal_at_utc = @old_terminal
WHERE execution_id = @execution;
UPDATE ctl.execution_state_event
SET transitioned_at_utc = CASE
        WHEN transition_sequence IN (1, 2) THEN @old_start
        ELSE @old_terminal
    END
WHERE execution_id = @execution;
-- Mantém cardinalidade, continuidade, relógio e último evento coerentes, mas corrompe o grafo:
-- EXTRACTING -> PROMOTED -> STAGED jamais é produzido pelos entrypoints V003/V004.
UPDATE ctl.execution_state_event
SET next_state = N'PROMOTED'
WHERE execution_id = @execution AND transition_sequence = 3;
UPDATE ctl.execution_state_event
SET previous_state = N'PROMOTED'
WHERE execution_id = @execution AND transition_sequence = 4;
UPDATE stg.execution_record
SET staged_at_utc = @old_terminal
WHERE execution_id = @execution;

EXEC ctl.usp_approve_staging_retention_policy
    @policy, N'LOCAL_SHADOW', @source, @tenant, @entity,
    N'NON_PUBLISHED_TERMINAL', N'terminal-ledger-policy-v1', @policy_fingerprint, 1,
    @owner_evidence, N'data-owner', @compliance_evidence, N'compliance';

DECLARE @terminal_ledger_error INT = NULL;
BEGIN TRY
    EXEC stg.usp_plan_staging_lifecycle
        @plan, @policy, N'terminal-ledger-policy-v1', @policy_fingerprint,
        N'LOCAL_SHADOW', @source, @tenant, @entity, NULL, NULL,
        1, 1, 1, 1, 1, 1, 10, 100000;
END TRY
BEGIN CATCH
    SET @terminal_ledger_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @terminal_ledger_error IS NULL OR @terminal_ledger_error <> 51527
    THROW 51681, N'Planner aceitou aresta semanticamente impossível na trilha terminal.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51682, N'O rollback da prova terminal não encerrou o escopo sintético.', 1;

PRINT N'Aresta semanticamente impossível da trilha terminal bloqueada; estado revertido.';

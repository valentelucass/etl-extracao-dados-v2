-- Exercício sintético, rollback-only, do hard gate de contrato/configuração de V2-044.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51510, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET XACT_ABORT ON;
SET NOCOUNT ON;

-- OUTSIDE nasce fora das transações. Cada marcador PENDING precisa sumir no rollback do cenário.
CREATE TABLE #contract_permit_transaction_sentinel (
    marker NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
);
INSERT INTO #contract_permit_transaction_sentinel (marker) VALUES (N'OUTSIDE');

-- Cenário 1: permit divergente em STAGED falha antes de criar candidate set ou auditoria.
BEGIN TRANSACTION;
INSERT INTO #contract_permit_transaction_sentinel (marker) VALUES (N'PENDING_PREPARE');

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "007_validate_staging_promotion_kernel.sql"
GO

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
   )
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_PREPARE'
   )
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51511, N'O baseline ou validator rompeu a transação sentinel de STAGED.', 1;
END;

DECLARE @prepare_now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @prepare_window_start DATETIME2(3) = DATEADD(HOUR, -1, @prepare_now);
DECLARE @prepare_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000441';
DECLARE @prepare_cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000442';
DECLARE @prepare_contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @prepare_configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @prepare_plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @prepare_row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @prepare_presence_fingerprint CHAR(64) = REPLICATE('e', 64);
DECLARE @prepare_mismatched_fingerprint CHAR(64) = REPLICATE('f', 64);
DECLARE @prepare_error INT = NULL;
DECLARE @prepare_error_xact_state INT = NULL;
DECLARE @prepare_error_trancount INT = NULL;

EXEC ctl.usp_control_plane_register_source
    N'SYNTHETIC_CONTRACT_GATE_PREPARE', N'SYNTHETIC', @prepare_now;
EXEC ctl.usp_control_plane_start_cycle
    @prepare_cycle, N'synthetic-contract-gate-plan-v1', @prepare_plan_fingerprint, @prepare_now;
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @prepare_execution,
    @cycle_id = @prepare_cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = N'SYNTHETIC_CONTRACT_GATE_PREPARE',
    @tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT',
    @entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @prepare_window_start,
    @partition_end_exclusive_utc = @prepare_now,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @prepare_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @prepare_configuration_fingerprint,
    @idempotency_key = N'synthetic-contract-gate-prepare-001',
    @lease_seconds = 3600,
    @started_at_utc = @prepare_now;
EXEC stg.usp_stage_record
    @prepare_execution, 1, 1, N'synthetic-key', N'row-v1', @prepare_row_hash,
    N'presence-v1', @prepare_presence_fingerprint, @prepare_window_start, N'VALID', NULL,
    @prepare_now;
EXEC ctl.usp_control_plane_transition_execution
    @prepare_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @prepare_now;
EXEC ctl.usp_control_plane_transition_execution
    @prepare_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @prepare_now;

DECLARE @prepare_state_before NVARCHAR(32) = (
    SELECT current_state FROM ctl.execution_attempt WHERE execution_id = @prepare_execution
);
DECLARE @prepare_partition_state_before NVARCHAR(32) = (
    SELECT partition.current_state
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @prepare_execution
);
DECLARE @prepare_sequence_before INT = (
    SELECT next_transition_sequence FROM ctl.execution_attempt WHERE execution_id = @prepare_execution
);
DECLARE @prepare_candidate_before BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @prepare_execution
);
DECLARE @prepare_quarantine_before BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id = @prepare_execution
);
DECLARE @prepare_count_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count
    WHERE execution_id = @prepare_execution AND count_phase = N'STAGING_KERNEL'
);
DECLARE @prepare_result_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @prepare_execution
);
DECLARE @prepare_event_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event
    WHERE execution_id = @prepare_execution AND reason_code = N'CANDIDATE_SET_PREPARED'
);
DECLARE @prepare_lease_before DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @prepare_execution
);

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51512, N'O setup de STAGED rompeu a transação externa.', 1;
END;

BEGIN TRY
    EXEC core.usp_prepare_staged_execution
        @execution_id = @prepare_execution,
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @prepare_contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @prepare_mismatched_fingerprint;
END TRY
BEGIN CATCH
    SELECT
        @prepare_error = ERROR_NUMBER(),
        @prepare_error_xact_state = XACT_STATE(),
        @prepare_error_trancount = @@TRANCOUNT;
END CATCH;

DECLARE @prepare_state_after NVARCHAR(32) = (
    SELECT current_state FROM ctl.execution_attempt WHERE execution_id = @prepare_execution
);
DECLARE @prepare_partition_state_after NVARCHAR(32) = (
    SELECT partition.current_state
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @prepare_execution
);
DECLARE @prepare_sequence_after INT = (
    SELECT next_transition_sequence FROM ctl.execution_attempt WHERE execution_id = @prepare_execution
);
DECLARE @prepare_candidate_after BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @prepare_execution
);
DECLARE @prepare_quarantine_after BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id = @prepare_execution
);
DECLARE @prepare_count_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count
    WHERE execution_id = @prepare_execution AND count_phase = N'STAGING_KERNEL'
);
DECLARE @prepare_result_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @prepare_execution
);
DECLARE @prepare_event_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event
    WHERE execution_id = @prepare_execution AND reason_code = N'CANDIDATE_SET_PREPARED'
);
DECLARE @prepare_lease_after DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @prepare_execution
);

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF @prepare_error IS NULL OR @prepare_error <> 51418
    OR @prepare_error_xact_state <> -1 OR @prepare_error_trancount < 1
    OR @prepare_state_before IS NULL OR @prepare_state_after IS NULL
    OR @prepare_partition_state_before IS NULL OR @prepare_partition_state_after IS NULL
    OR @prepare_sequence_before IS NULL OR @prepare_sequence_after IS NULL
    OR @prepare_state_before COLLATE Latin1_General_100_BIN2 <> N'STAGED'
    OR @prepare_state_after COLLATE Latin1_General_100_BIN2 <> @prepare_state_before
    OR @prepare_partition_state_after COLLATE Latin1_General_100_BIN2
        <> @prepare_partition_state_before COLLATE Latin1_General_100_BIN2
    OR @prepare_sequence_after <> @prepare_sequence_before
    OR @prepare_candidate_after <> @prepare_candidate_before
    OR @prepare_quarantine_after <> @prepare_quarantine_before
    OR @prepare_count_after <> @prepare_count_before
    OR @prepare_result_after <> @prepare_result_before
    OR @prepare_event_after <> @prepare_event_before
    OR @prepare_lease_before IS NOT NULL OR @prepare_lease_after IS NOT NULL
    THROW 51513, N'Permit divergente alterou o candidate set ou a promoção em STAGED.', 1;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51514, N'O cenário STAGED terminou com transação aberta.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
)
   OR EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_PREPARE'
   )
    THROW 51515, N'O sentinel detectou commit no cenário STAGED.', 1;
GO

-- Cenário 2: permit divergente em PROMOTED falha antes de core/recon/publicação.
BEGIN TRANSACTION;
INSERT INTO #contract_permit_transaction_sentinel (marker) VALUES (N'PENDING_APPLY');

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "009_validate_atomic_publication_protocol.sql"
GO

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
   )
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_APPLY'
   )
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51516, N'O baseline ou validator rompeu a transação sentinel de PROMOTED.', 1;
END;

DECLARE @promoted_now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @promoted_window_start DATETIME2(3) = DATEADD(HOUR, -1, @promoted_now);
DECLARE @promoted_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000445';
DECLARE @promoted_cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000446';
DECLARE @promoted_contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @promoted_configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @promoted_plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @promoted_row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @promoted_presence_fingerprint CHAR(64) = REPLICATE('e', 64);
DECLARE @promoted_mismatched_fingerprint CHAR(64) = REPLICATE('f', 64);
DECLARE @promoted_partition_id BIGINT;
DECLARE @promoted_error INT = NULL;
DECLARE @promoted_error_xact_state INT = NULL;
DECLARE @promoted_error_trancount INT = NULL;

EXEC ctl.usp_control_plane_register_source
    N'SYNTHETIC_CONTRACT_GATE_PROMOTED', N'SYNTHETIC', @promoted_now;
EXEC ctl.usp_control_plane_start_cycle
    @promoted_cycle, N'synthetic-contract-gate-plan-v1', @promoted_plan_fingerprint, @promoted_now;
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @promoted_execution,
    @cycle_id = @promoted_cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = N'SYNTHETIC_CONTRACT_GATE_PROMOTED',
    @tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT',
    @entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @promoted_window_start,
    @partition_end_exclusive_utc = @promoted_now,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @promoted_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @promoted_configuration_fingerprint,
    @idempotency_key = N'synthetic-contract-gate-promoted-001',
    @lease_seconds = 3600,
    @started_at_utc = @promoted_now;
EXEC stg.usp_stage_record
    @promoted_execution, 1, 1, N'synthetic-key', N'row-v1', @promoted_row_hash,
    N'presence-v1', @promoted_presence_fingerprint, @promoted_window_start, N'VALID', NULL,
    @promoted_now;
EXEC ctl.usp_control_plane_transition_execution
    @promoted_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @promoted_now;
EXEC ctl.usp_control_plane_transition_execution
    @promoted_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @promoted_now;
EXEC core.usp_prepare_staged_execution
    @execution_id = @promoted_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @promoted_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @promoted_configuration_fingerprint;

SELECT @promoted_partition_id = partition_id
FROM ctl.execution_attempt
WHERE execution_id = @promoted_execution;

DECLARE @promoted_state_before NVARCHAR(32) = (
    SELECT current_state FROM ctl.execution_attempt WHERE execution_id = @promoted_execution
);
DECLARE @promoted_partition_state_before NVARCHAR(32) = (
    SELECT partition.current_state
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @promoted_execution
);
DECLARE @promoted_sequence_before INT = (
    SELECT next_transition_sequence FROM ctl.execution_attempt WHERE execution_id = @promoted_execution
);
DECLARE @promoted_candidate_before BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @promoted_execution
);
DECLARE @promoted_count_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count WHERE execution_id = @promoted_execution
);
DECLARE @promoted_result_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @promoted_execution
);
DECLARE @promoted_event_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event WHERE execution_id = @promoted_execution
);
DECLARE @promoted_application_before BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @promoted_execution
);
DECLARE @promoted_reconciliation_before BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result
    WHERE execution_id = @promoted_execution
);
DECLARE @promoted_publication_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id = @promoted_execution
);
DECLARE @promoted_core_before BIGINT = (
    SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @promoted_execution
);
DECLARE @promoted_pointer_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.partition_publication_pointer
    WHERE partition_id = @promoted_partition_id AND published_execution_id = @promoted_execution
);
DECLARE @promoted_watermark_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_CONTRACT_GATE_PROMOTED'
      AND tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT'
      AND entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY'
);
DECLARE @promoted_lease_before DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @promoted_execution
);

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51517, N'O setup de PROMOTED rompeu a transação externa.', 1;
END;

BEGIN TRY
    EXEC core.usp_apply_reconcile_publish_execution
        @execution_id = @promoted_execution,
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @promoted_contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @promoted_mismatched_fingerprint;
END TRY
BEGIN CATCH
    SELECT
        @promoted_error = ERROR_NUMBER(),
        @promoted_error_xact_state = XACT_STATE(),
        @promoted_error_trancount = @@TRANCOUNT;
END CATCH;

DECLARE @promoted_state_after NVARCHAR(32) = (
    SELECT current_state FROM ctl.execution_attempt WHERE execution_id = @promoted_execution
);
DECLARE @promoted_partition_state_after NVARCHAR(32) = (
    SELECT partition.current_state
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @promoted_execution
);
DECLARE @promoted_sequence_after INT = (
    SELECT next_transition_sequence FROM ctl.execution_attempt WHERE execution_id = @promoted_execution
);
DECLARE @promoted_candidate_after BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @promoted_execution
);
DECLARE @promoted_count_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count WHERE execution_id = @promoted_execution
);
DECLARE @promoted_result_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @promoted_execution
);
DECLARE @promoted_event_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event WHERE execution_id = @promoted_execution
);
DECLARE @promoted_application_after BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @promoted_execution
);
DECLARE @promoted_reconciliation_after BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result
    WHERE execution_id = @promoted_execution
);
DECLARE @promoted_publication_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id = @promoted_execution
);
DECLARE @promoted_core_after BIGINT = (
    SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @promoted_execution
);
DECLARE @promoted_pointer_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.partition_publication_pointer
    WHERE partition_id = @promoted_partition_id AND published_execution_id = @promoted_execution
);
DECLARE @promoted_watermark_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_CONTRACT_GATE_PROMOTED'
      AND tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT'
      AND entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY'
);
DECLARE @promoted_lease_after DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @promoted_execution
);

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF @promoted_error IS NULL OR @promoted_error <> 51418
    OR @promoted_error_xact_state <> -1 OR @promoted_error_trancount < 1
    OR @promoted_state_before IS NULL OR @promoted_state_after IS NULL
    OR @promoted_partition_state_before IS NULL OR @promoted_partition_state_after IS NULL
    OR @promoted_sequence_before IS NULL OR @promoted_sequence_after IS NULL
    OR @promoted_state_before COLLATE Latin1_General_100_BIN2 <> N'PROMOTED'
    OR @promoted_state_after COLLATE Latin1_General_100_BIN2 <> @promoted_state_before
    OR @promoted_partition_state_after COLLATE Latin1_General_100_BIN2
        <> @promoted_partition_state_before COLLATE Latin1_General_100_BIN2
    OR @promoted_sequence_after <> @promoted_sequence_before
    OR @promoted_candidate_after <> @promoted_candidate_before
    OR @promoted_count_after <> @promoted_count_before
    OR @promoted_result_after <> @promoted_result_before
    OR @promoted_event_after <> @promoted_event_before
    OR @promoted_application_after <> @promoted_application_before
    OR @promoted_reconciliation_after <> @promoted_reconciliation_before
    OR @promoted_publication_after <> @promoted_publication_before
    OR @promoted_core_after <> @promoted_core_before
    OR @promoted_pointer_after <> @promoted_pointer_before
    OR @promoted_watermark_after <> @promoted_watermark_before
    OR @promoted_lease_before IS NOT NULL OR @promoted_lease_after IS NOT NULL
    THROW 51518, N'Permit divergente alterou dados em PROMOTED.', 1;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51519, N'O cenário PROMOTED terminou com transação aberta.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
)
   OR EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_APPLY'
   )
    THROW 51520, N'O sentinel detectou commit no cenário PROMOTED.', 1;
GO

-- Cenário 3: até o retry PUBLISHED confere o permit antes de devolver evidência imutável.
BEGIN TRANSACTION;
INSERT INTO #contract_permit_transaction_sentinel (marker) VALUES (N'PENDING_RETRY');

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "009_validate_atomic_publication_protocol.sql"
GO

-- O cenário PUBLISHED continua isolando o permit de contrato. O hard gate DQ possui seu próprio
-- exercício rollback-only em 022_exercise_observability_data_quality_rollback.sql.
DISABLE TRIGGER ctl.trg_execution_publication_requires_data_quality
    ON ctl.execution_publication_event;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
   )
   OR NOT EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_RETRY'
   )
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51521, N'O baseline ou validator rompeu a transação sentinel de PUBLISHED.', 1;
END;

DECLARE @apply_now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @apply_window_start DATETIME2(3) = DATEADD(HOUR, -1, @apply_now);
DECLARE @apply_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000443';
DECLARE @apply_cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000444';
DECLARE @apply_contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @apply_configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @apply_plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @apply_row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @apply_presence_fingerprint CHAR(64) = REPLICATE('e', 64);
DECLARE @apply_partition_id BIGINT;
DECLARE @apply_error INT = NULL;
DECLARE @apply_error_xact_state INT = NULL;
DECLARE @apply_error_trancount INT = NULL;

EXEC ctl.usp_control_plane_register_source
    N'SYNTHETIC_CONTRACT_GATE_APPLY', N'SYNTHETIC', @apply_now;
EXEC ctl.usp_control_plane_start_cycle
    @apply_cycle, N'synthetic-contract-gate-plan-v1', @apply_plan_fingerprint, @apply_now;
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @apply_execution,
    @cycle_id = @apply_cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = N'SYNTHETIC_CONTRACT_GATE_APPLY',
    @tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT',
    @entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @apply_window_start,
    @partition_end_exclusive_utc = @apply_now,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @apply_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @apply_configuration_fingerprint,
    @idempotency_key = N'synthetic-contract-gate-apply-001',
    @lease_seconds = 3600,
    @started_at_utc = @apply_now;
EXEC stg.usp_stage_record
    @apply_execution, 1, 1, N'synthetic-key', N'row-v1', @apply_row_hash,
    N'presence-v1', @apply_presence_fingerprint, @apply_window_start, N'VALID', NULL,
    @apply_now;
EXEC ctl.usp_control_plane_transition_execution
    @apply_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @apply_now;
EXEC ctl.usp_control_plane_transition_execution
    @apply_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @apply_now;
EXEC core.usp_prepare_staged_execution
    @execution_id = @apply_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @apply_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @apply_configuration_fingerprint;
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @apply_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @apply_contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @apply_configuration_fingerprint;

SELECT @apply_partition_id = partition_id
FROM ctl.execution_attempt
WHERE execution_id = @apply_execution;

DECLARE @apply_state_before NVARCHAR(32);
DECLARE @apply_partition_state_before NVARCHAR(32);
DECLARE @apply_sequence_before INT;
DECLARE @apply_terminal_before DATETIME2(3);
SELECT
    @apply_state_before = attempt.current_state,
    @apply_partition_state_before = partition.current_state,
    @apply_sequence_before = attempt.next_transition_sequence,
    @apply_terminal_before = attempt.terminal_at_utc
FROM ctl.execution_attempt AS attempt
INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
WHERE attempt.execution_id = @apply_execution;

DECLARE @apply_candidate_before BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @apply_execution
);
DECLARE @apply_count_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count WHERE execution_id = @apply_execution
);
DECLARE @apply_result_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @apply_execution
);
DECLARE @apply_event_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event WHERE execution_id = @apply_execution
);
DECLARE @apply_application_before BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @apply_execution
);
DECLARE @apply_reconciliation_before BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result
    WHERE execution_id = @apply_execution
);
DECLARE @apply_publication_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id = @apply_execution
);
DECLARE @apply_core_before BIGINT = (
    SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @apply_execution
);
DECLARE @apply_core_promoted_before DATETIME2(3) = (
    SELECT MAX(last_promoted_at_utc) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @apply_execution
);
DECLARE @apply_pointer_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.partition_publication_pointer
    WHERE partition_id = @apply_partition_id AND published_execution_id = @apply_execution
);
DECLARE @apply_pointer_published_before DATETIME2(3) = (
    SELECT published_at_utc FROM ctl.partition_publication_pointer
    WHERE partition_id = @apply_partition_id AND published_execution_id = @apply_execution
);
DECLARE @apply_watermark_before BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_CONTRACT_GATE_APPLY'
      AND tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT'
      AND entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY'
);
DECLARE @apply_lease_before DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @apply_execution
);

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51522, N'O setup de PUBLISHED rompeu a transação externa.', 1;
END;

BEGIN TRY
    EXEC core.usp_apply_reconcile_publish_execution
        @execution_id = @apply_execution,
        @contract_version = N'synthetic-contract-v2',
        @contract_fingerprint = @apply_contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @apply_configuration_fingerprint;
END TRY
BEGIN CATCH
    SELECT
        @apply_error = ERROR_NUMBER(),
        @apply_error_xact_state = XACT_STATE(),
        @apply_error_trancount = @@TRANCOUNT;
END CATCH;

DECLARE @apply_state_after NVARCHAR(32);
DECLARE @apply_partition_state_after NVARCHAR(32);
DECLARE @apply_sequence_after INT;
DECLARE @apply_terminal_after DATETIME2(3);
SELECT
    @apply_state_after = attempt.current_state,
    @apply_partition_state_after = partition.current_state,
    @apply_sequence_after = attempt.next_transition_sequence,
    @apply_terminal_after = attempt.terminal_at_utc
FROM ctl.execution_attempt AS attempt
INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
WHERE attempt.execution_id = @apply_execution;

DECLARE @apply_candidate_after BIGINT = (
    SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @apply_execution
);
DECLARE @apply_count_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_count WHERE execution_id = @apply_execution
);
DECLARE @apply_result_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result WHERE execution_id = @apply_execution
);
DECLARE @apply_event_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event WHERE execution_id = @apply_execution
);
DECLARE @apply_application_after BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @apply_execution
);
DECLARE @apply_reconciliation_after BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result
    WHERE execution_id = @apply_execution
);
DECLARE @apply_publication_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id = @apply_execution
);
DECLARE @apply_core_after BIGINT = (
    SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @apply_execution
);
DECLARE @apply_core_promoted_after DATETIME2(3) = (
    SELECT MAX(last_promoted_at_utc) FROM core.entity_record_state
    WHERE last_promoted_execution_id = @apply_execution
);
DECLARE @apply_pointer_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.partition_publication_pointer
    WHERE partition_id = @apply_partition_id AND published_execution_id = @apply_execution
);
DECLARE @apply_pointer_published_after DATETIME2(3) = (
    SELECT published_at_utc FROM ctl.partition_publication_pointer
    WHERE partition_id = @apply_partition_id AND published_execution_id = @apply_execution
);
DECLARE @apply_watermark_after BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_CONTRACT_GATE_APPLY'
      AND tenant_scope = N'SYNTHETIC_CONTRACT_GATE_TENANT'
      AND entity_name = N'SYNTHETIC_CONTRACT_GATE_ENTITY'
);
DECLARE @apply_lease_after DATETIME2(3) = (
    SELECT released_at_utc FROM ctl.execution_lease WHERE execution_id = @apply_execution
);

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF @apply_error IS NULL OR @apply_error <> 51418
    OR @apply_error_xact_state <> -1 OR @apply_error_trancount < 1
    OR @apply_state_before IS NULL OR @apply_state_after IS NULL
    OR @apply_partition_state_before IS NULL OR @apply_partition_state_after IS NULL
    OR @apply_sequence_before IS NULL OR @apply_sequence_after IS NULL
    OR @apply_terminal_before IS NULL OR @apply_terminal_after IS NULL
    OR @apply_core_promoted_before IS NULL OR @apply_core_promoted_after IS NULL
    OR @apply_pointer_published_before IS NULL OR @apply_pointer_published_after IS NULL
    OR @apply_lease_before IS NULL OR @apply_lease_after IS NULL
    OR @apply_state_before COLLATE Latin1_General_100_BIN2 <> N'PUBLISHED'
    OR @apply_state_after COLLATE Latin1_General_100_BIN2 <> @apply_state_before
    OR @apply_partition_state_after COLLATE Latin1_General_100_BIN2
        <> @apply_partition_state_before COLLATE Latin1_General_100_BIN2
    OR @apply_sequence_after <> @apply_sequence_before
    OR @apply_terminal_after <> @apply_terminal_before
    OR @apply_candidate_after <> @apply_candidate_before
    OR @apply_count_after <> @apply_count_before
    OR @apply_result_after <> @apply_result_before
    OR @apply_event_after <> @apply_event_before
    OR @apply_application_after <> @apply_application_before
    OR @apply_reconciliation_after <> @apply_reconciliation_before
    OR @apply_publication_after <> @apply_publication_before
    OR @apply_core_after <> @apply_core_before
    OR @apply_core_promoted_after <> @apply_core_promoted_before
    OR @apply_pointer_after <> @apply_pointer_before
    OR @apply_pointer_published_after <> @apply_pointer_published_before
    OR @apply_watermark_after <> @apply_watermark_before
    OR @apply_lease_after <> @apply_lease_before
    THROW 51523, N'Permit divergente alterou ou reutilizou evidência em PUBLISHED.', 1;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51524, N'O cenário PUBLISHED terminou com transação aberta.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'OUTSIDE'
)
   OR EXISTS (
        SELECT 1 FROM #contract_permit_transaction_sentinel WHERE marker = N'PENDING_RETRY'
   )
    THROW 51525, N'O sentinel detectou commit no cenário PUBLISHED.', 1;

DROP TABLE #contract_permit_transaction_sentinel;

PRINT N'Hard gate de contrato/configuração exercitado em STAGED, PROMOTED e PUBLISHED e integralmente revertido.';

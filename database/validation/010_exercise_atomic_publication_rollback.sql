-- Exercício sintético, rollback-only, do protocolo atômico de aplicação e publicação.
-- A falha esperada de quarantine é executada por último para que XACT_ABORT nunca deixe
-- uma transação externa sem rollback explícito.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51470, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET XACT_ABORT ON;
SET NOCOUNT ON;

-- A tabela temporária nasce fora da transação. O marcador PENDING precisa desaparecer no
-- rollback final, enquanto OUTSIDE precisa sobreviver. Isso detecta commit indevido do caller.
CREATE TABLE #atomic_publication_transaction_sentinel (
    marker NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
);
INSERT INTO #atomic_publication_transaction_sentinel (marker) VALUES (N'OUTSIDE');

CREATE TABLE #atomic_publication_capture (
    capture_id BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    scenario_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NULL,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    candidate_rows BIGINT NOT NULL,
    inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,
    noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,
    reconciled_at_utc DATETIME2(3) NOT NULL,
    published_at_utc DATETIME2(3) NOT NULL,
    incremental_frontier_before_utc DATETIME2(3) NULL,
    incremental_frontier_after_utc DATETIME2(3) NULL
);

BEGIN TRANSACTION;
INSERT INTO #atomic_publication_transaction_sentinel (marker) VALUES (N'PENDING');

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "009_validate_atomic_publication_protocol.sql"
GO

-- Este exercício preserva o foco de V2-020/V2-021. O gate DQ é exercitado, inclusive com
-- rollback do apply, por 022_exercise_observability_data_quality_rollback.sql.
DISABLE TRIGGER ctl.trg_execution_publication_requires_data_quality
    ON ctl.execution_publication_event;

IF XACT_STATE() <> 1
   OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #atomic_publication_transaction_sentinel WHERE marker = N'PENDING'
   )
BEGIN
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW 51471, N'O baseline ou validador rompeu a transação externa sentinel.', 1;
END;

DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_ATOMIC_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_ATOMIC_TENANT';
DECLARE @main_entity NVARCHAR(128) = N'SYNTHETIC_ATOMIC_ENTITY';
DECLARE @gap_entity NVARCHAR(128) = N'SYNTHETIC_ATOMIC_GAP_ENTITY';
DECLARE @backfill_entity NVARCHAR(128) = N'SYNTHETIC_ATOMIC_BACKFILL_ENTITY';
DECLARE @quarantine_entity NVARCHAR(128) = N'SYNTHETIC_ATOMIC_QUARANTINE_ENTITY';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000310';
DECLARE @main_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000301';
DECLARE @gap_late_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000302';
DECLARE @gap_early_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000303';
DECLARE @backfill_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000304';
DECLARE @quarantine_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000305';
DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @main_start DATETIME2(3) = DATEADD(DAY, -4, @now);
DECLARE @main_end DATETIME2(3) = DATEADD(HOUR, 1, @main_start);
DECLARE @gap_start DATETIME2(3) = DATEADD(DAY, -3, @now);
DECLARE @gap_middle DATETIME2(3) = DATEADD(HOUR, 1, @gap_start);
DECLARE @gap_end DATETIME2(3) = DATEADD(HOUR, 2, @gap_start);
DECLARE @backfill_start DATETIME2(3) = DATEADD(DAY, -2, @now);
DECLARE @backfill_end DATETIME2(3) = DATEADD(HOUR, 1, @backfill_start);
DECLARE @quarantine_start DATETIME2(3) = DATEADD(DAY, -1, @now);
DECLARE @quarantine_end DATETIME2(3) = DATEADD(HOUR, 1, @quarantine_start);
DECLARE @historical_at DATETIME2(3) = DATEADD(DAY, -10, @now);
DECLARE @caller_timestamp DATETIME2(3) = '2000-01-01T00:00:00.000';
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @hash_a CHAR(64) = REPLICATE('a', 64);
DECLARE @hash_b CHAR(64) = REPLICATE('b', 64);
DECLARE @hash_c CHAR(64) = REPLICATE('c', 64);
DECLARE @hash_d CHAR(64) = REPLICATE('d', 64);
DECLARE @hash_e CHAR(64) = REPLICATE('e', 64);
DECLARE @hash_f CHAR(64) = REPLICATE('f', 64);
DECLARE @presence_1 CHAR(64) = REPLICATE('1', 64);
DECLARE @presence_2 CHAR(64) = REPLICATE('2', 64);
DECLARE @presence_3 CHAR(64) = REPLICATE('3', 64);
DECLARE @presence_4 CHAR(64) = REPLICATE('4', 64);
DECLARE @presence_5 CHAR(64) = REPLICATE('5', 64);
DECLARE @presence_6 CHAR(64) = REPLICATE('6', 64);

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @caller_timestamp;
EXEC ctl.usp_control_plane_start_cycle
    @cycle, N'synthetic-atomic-plan-v1', @plan_fingerprint, @caller_timestamp;

EXEC ctl.usp_control_plane_register_incremental_frontier
    @environment_name = @environment,
    @source_instance = @source,
    @tenant_scope = @tenant,
    @entity_name = @main_entity,
    @initial_contiguous_end_utc = @main_start,
    @registered_at_utc = @caller_timestamp;

EXEC ctl.usp_control_plane_start_execution
    @execution_id = @main_execution,
    @cycle_id = @cycle,
    @environment_name = @environment,
    @source_instance = @source,
    @tenant_scope = @tenant,
    @entity_name = @main_entity,
    @execution_mode = N'INCREMENTAL',
    @partition_start_utc = @main_start,
    @partition_end_exclusive_utc = @main_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-atomic-main-001',
    @lease_seconds = 86400,
    @started_at_utc = @now;

-- Quatro estados anteriores fecham UPDATE, REACTIVATED, NO_OP e STALE_NO_OP.
-- A quinta chave inexiste e deve ser INSERTED.
INSERT INTO core.entity_record_state (
    environment_name, source_instance, tenant_scope, entity_name, source_key,
    row_fingerprint_version, source_row_hash,
    presence_fingerprint_version, presence_fingerprint,
    source_freshness_at_utc, active,
    first_promoted_execution_id, last_promoted_execution_id,
    first_promoted_at_utc, last_promoted_at_utc
)
VALUES
    (
        @environment, @source, @tenant, @main_entity, N'key-update',
        N'row-v0', @hash_f, N'presence-v0', @presence_6,
        DATEADD(MINUTE, -10, @main_start), 1,
        @main_execution, @main_execution, @historical_at, @historical_at
    ),
    (
        @environment, @source, @tenant, @main_entity, N'key-reactivate',
        N'row-v0', @hash_f, N'presence-v0', @presence_6,
        DATEADD(MINUTE, -10, @main_start), 0,
        @main_execution, @main_execution, @historical_at, @historical_at
    ),
    (
        @environment, @source, @tenant, @main_entity, N'key-noop',
        N'row-v1', @hash_d, N'presence-v1', @presence_4,
        @main_start, 1,
        @main_execution, @main_execution, @historical_at, @historical_at
    ),
    (
        @environment, @source, @tenant, @main_entity, N'key-stale',
        N'row-v2', @hash_f, N'presence-v2', @presence_6,
        DATEADD(MINUTE, 10, @main_start), 1,
        @main_execution, @main_execution, @historical_at, @historical_at
    );

EXEC stg.usp_stage_record @main_execution, 1, 1, N'key-insert', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @main_start, N'VALID', NULL, @caller_timestamp;
EXEC stg.usp_stage_record @main_execution, 1, 2, N'key-update', N'row-v1', @hash_b,
    N'presence-v1', @presence_2, @main_start, N'VALID', NULL, @caller_timestamp;
EXEC stg.usp_stage_record @main_execution, 1, 3, N'key-reactivate', N'row-v1', @hash_c,
    N'presence-v1', @presence_3, @main_start, N'VALID', NULL, @caller_timestamp;
EXEC stg.usp_stage_record @main_execution, 1, 4, N'key-noop', N'row-v1', @hash_d,
    N'presence-v1', @presence_4, @main_start, N'VALID', NULL, @caller_timestamp;
EXEC stg.usp_stage_record @main_execution, 1, 5, N'key-stale', N'row-v1', @hash_e,
    N'presence-v1', @presence_5, @main_start, N'VALID', NULL, @caller_timestamp;

EXEC ctl.usp_control_plane_transition_execution
    @main_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @caller_timestamp;
EXEC ctl.usp_control_plane_transition_execution
    @main_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @caller_timestamp;
EXEC core.usp_prepare_staged_execution
    @execution_id = @main_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

DECLARE @main_partition_id BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @main_execution
);

IF NOT EXISTS (
    SELECT 1
    FROM ctl.execution_attempt
    WHERE execution_id = @main_execution
      AND current_state = N'PROMOTED'
      AND next_transition_sequence = 6
)
    THROW 51472, N'O fixture principal não alcançou PROMOTED de forma evidence-bound.', 1;

-- A primeira execução positiva é revertida até o savepoint. Tudo que compõe o protocolo final
-- deve desaparecer junto, enquanto candidate set/PROMOTED, criados antes dele, permanecem.
SAVE TRANSACTION atomic_publication_before_apply;
DECLARE @savepoint_apply_before DATETIME2(3) = SYSUTCDATETIME();

INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @main_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51473, N'A procedure final consumiu ou invalidou a transação externa.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @main_execution AND current_state = N'PUBLISHED'
)
   OR NOT EXISTS (
        SELECT 1 FROM recon.execution_reconciliation_result
        WHERE execution_id = @main_execution
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_publication_event
        WHERE execution_id = @main_execution
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer
        WHERE partition_id = @main_partition_id
          AND published_execution_id = @main_execution
   )
    THROW 51474, N'O savepoint não observou a unidade positiva completa.', 1;

ROLLBACK TRANSACTION atomic_publication_before_apply;

IF XACT_STATE() <> 1
   OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #atomic_publication_transaction_sentinel WHERE marker = N'PENDING'
   )
    THROW 51475, N'O rollback ao savepoint rompeu a transação externa sentinel.', 1;

IF EXISTS (
    SELECT 1 FROM #atomic_publication_capture WHERE execution_id = @main_execution
)
   OR EXISTS (
        SELECT 1 FROM recon.execution_candidate_application
        WHERE execution_id = @main_execution
   )
   OR EXISTS (
        SELECT 1 FROM recon.execution_reconciliation_result
        WHERE execution_id = @main_execution
   )
   OR EXISTS (
        SELECT 1 FROM ctl.execution_publication_event
        WHERE execution_id = @main_execution
   )
   OR EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer
        WHERE published_execution_id = @main_execution
   )
    THROW 51476, N'O rollback parcial deixou evidência de aplicação/publicação órfã.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @main_execution
      AND current_state = N'PROMOTED'
      AND next_transition_sequence = 6
)
   OR (SELECT COUNT_BIG(*) FROM ctl.execution_state_event
       WHERE execution_id = @main_execution) <> 5
   OR (SELECT COUNT_BIG(*) FROM stg.execution_candidate
       WHERE execution_id = @main_execution) <> 5
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_lease
        WHERE execution_id = @main_execution AND released_at_utc IS NULL
   )
    THROW 51477, N'O rollback parcial não restaurou exatamente o estado PROMOTED.', 1;

IF (SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE environment_name = @environment
      AND source_instance = @source
      AND tenant_scope = @tenant
      AND entity_name = @main_entity) <> 4
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-update'
          AND row_fingerprint_version = N'row-v0'
          AND source_row_hash = @hash_f
          AND last_promoted_at_utc = @historical_at
   )
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-reactivate'
          AND active = 0
          AND last_promoted_at_utc = @historical_at
   )
    THROW 51478, N'O rollback parcial não restaurou o core anterior.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.incremental_publication_watermark
    WHERE environment_name = @environment
      AND source_instance = @source
      AND tenant_scope = @tenant
      AND entity_name = @main_entity
      AND contiguous_partition_end_utc = @main_start
      AND last_partition_id IS NULL
      AND advanced_at_utc IS NULL
)
    THROW 51479, N'O rollback parcial deixou avanço de watermark.', 1;

-- Aplicação real dentro do caller: o COMMIT interno deve preservar @@TRANCOUNT = 1.
DECLARE @main_apply_before DATETIME2(3) = SYSUTCDATETIME();
INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @main_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;
DECLARE @main_apply_after DATETIME2(3) = SYSUTCDATETIME();

UPDATE #atomic_publication_capture
SET scenario_name = N'MAIN_APPLY'
WHERE execution_id = @main_execution AND scenario_name IS NULL;

IF XACT_STATE() <> 1
   OR @@TRANCOUNT <> 1
   OR NOT EXISTS (
        SELECT 1 FROM #atomic_publication_transaction_sentinel WHERE marker = N'PENDING'
   )
    THROW 51480, N'O commit positivo escapou da transação externa sentinel.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM #atomic_publication_capture
    WHERE scenario_name = N'MAIN_APPLY'
      AND execution_id = @main_execution
      AND candidate_rows = 5
      AND inserted_rows = 1
      AND updated_rows = 1
      AND reactivated_rows = 1
      AND noop_rows = 2
      AND stale_noop_rows = 1
      AND reconciled_at_utc >= @main_apply_before
      AND reconciled_at_utc <= @main_apply_after
      AND published_at_utc >= reconciled_at_utc
      AND published_at_utc <= @main_apply_after
      AND incremental_frontier_before_utc = @main_start
      AND incremental_frontier_after_utc = @main_end
)
    THROW 51481, N'O retorno agregado ou o relógio SQL da aplicação divergiu.', 1;

IF (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @main_execution) <> 5
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution AND application_disposition = N'INSERTED') <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution AND application_disposition = N'UPDATED') <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution AND application_disposition = N'REACTIVATED') <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution AND application_disposition = N'NO_OP') <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution AND application_disposition = N'STALE_NO_OP') <> 1
    THROW 51482, N'As disposições por candidato não são exclusivas ou completas.', 1;

IF NOT EXISTS (
    SELECT 1 FROM recon.execution_reconciliation_result
    WHERE execution_id = @main_execution
      AND candidate_rows = 5
      AND inserted_rows = 1
      AND updated_rows = 1
      AND reactivated_rows = 1
      AND noop_rows = 2
      AND stale_noop_rows = 1
      AND candidate_rows = inserted_rows + updated_rows + reactivated_rows + noop_rows
)
    THROW 51483, N'O fechamento persistido da reconciliação não confere.', 1;

IF (SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE environment_name = @environment
      AND source_instance = @source
      AND tenant_scope = @tenant
      AND entity_name = @main_entity) <> 5
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-insert'
          AND row_fingerprint_version = N'row-v1'
          AND source_row_hash = @hash_a
          AND active = 1
   )
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-update'
          AND row_fingerprint_version = N'row-v1'
          AND source_row_hash = @hash_b
          AND active = 1
   )
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-reactivate'
          AND row_fingerprint_version = N'row-v1'
          AND source_row_hash = @hash_c
          AND active = 1
   )
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-noop'
          AND source_row_hash = @hash_d
          AND last_promoted_at_utc = @historical_at
   )
   OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND source_key = N'key-stale'
          AND row_fingerprint_version = N'row-v2'
          AND source_row_hash = @hash_f
          AND source_freshness_at_utc = DATEADD(MINUTE, 10, @main_start)
          AND last_promoted_at_utc = @historical_at
   )
    THROW 51484, N'O core aplicado regrediu, perdeu reativação ou alterou um no-op.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @main_execution
      AND current_state = N'PUBLISHED'
      AND next_transition_sequence = 8
      AND terminal_at_utc IS NOT NULL
)
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_state_event
        WHERE execution_id = @main_execution
          AND transition_sequence = 6
          AND previous_state = N'PROMOTED'
          AND next_state = N'RECONCILED'
          AND reason_code = N'CANDIDATE_SET_RECONCILED'
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_state_event
        WHERE execution_id = @main_execution
          AND transition_sequence = 7
          AND previous_state = N'RECONCILED'
          AND next_state = N'PUBLISHED'
          AND reason_code = N'RECONCILIATION_PUBLISHED'
   )
    THROW 51485, N'RECONCILED/PUBLISHED não foram registrados como transições dedicadas.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM ctl.partition_publication_pointer
    WHERE partition_id = @main_partition_id
      AND published_execution_id = @main_execution
)
   OR NOT EXISTS (
        SELECT 1
        FROM ctl.execution_publication_event AS publication
        INNER JOIN recon.execution_reconciliation_result AS result
            ON result.execution_id = publication.execution_id
        WHERE publication.execution_id = @main_execution
          AND publication.partition_id = @main_partition_id
          AND publication.incremental_frontier_before_utc = @main_start
          AND publication.incremental_frontier_after_utc = @main_end
          AND publication.watermark_last_partition_id = @main_partition_id
          AND publication.published_at_utc = result.published_at_utc
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.incremental_publication_watermark
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @main_entity
          AND contiguous_partition_end_utc = @main_end
          AND last_partition_id = @main_partition_id
          AND advanced_at_utc IS NOT NULL
   )
   OR NOT EXISTS (
        SELECT 1
        FROM ctl.execution_lease AS lease
        INNER JOIN recon.execution_reconciliation_result AS result
            ON result.execution_id = lease.execution_id
        WHERE lease.execution_id = @main_execution
          AND lease.released_at_utc = result.published_at_utc
   )
    THROW 51486, N'Pointer, watermark, evento e lease não fecharam no mesmo commit.', 1;

-- Confirmação perdida: mesmo com lease expirado e core mutável posterior, o retry deve responder
-- exclusivamente da evidência imutável, sem qualquer nova mutação.
UPDATE core.entity_record_state
SET source_row_hash = @hash_f
WHERE environment_name = @environment
  AND source_instance = @source
  AND tenant_scope = @tenant
  AND entity_name = @main_entity
  AND source_key = N'key-insert';

UPDATE ctl.execution_lease
SET expires_at_utc = DATEADD(MILLISECOND, 1, acquired_at_utc)
WHERE execution_id = @main_execution;

DECLARE @events_before_retry BIGINT = (
    SELECT COUNT_BIG(*) FROM ctl.execution_state_event WHERE execution_id = @main_execution
);
DECLARE @applications_before_retry BIGINT = (
    SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
    WHERE execution_id = @main_execution
);
DECLARE @core_before_retry BIGINT = (
    SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE environment_name = @environment
      AND source_instance = @source
      AND tenant_scope = @tenant
      AND entity_name = @main_entity
);

INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @main_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

UPDATE #atomic_publication_capture
SET scenario_name = N'MAIN_RETRY'
WHERE execution_id = @main_execution AND scenario_name IS NULL;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51487, N'O retry exato rompeu a transação externa sentinel.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM #atomic_publication_capture AS first_result
    INNER JOIN #atomic_publication_capture AS retry_result
        ON retry_result.execution_id = first_result.execution_id
       AND retry_result.scenario_name = N'MAIN_RETRY'
    WHERE first_result.scenario_name = N'MAIN_APPLY'
      AND first_result.execution_id = @main_execution
      AND retry_result.candidate_rows = first_result.candidate_rows
      AND retry_result.inserted_rows = first_result.inserted_rows
      AND retry_result.updated_rows = first_result.updated_rows
      AND retry_result.reactivated_rows = first_result.reactivated_rows
      AND retry_result.noop_rows = first_result.noop_rows
      AND retry_result.stale_noop_rows = first_result.stale_noop_rows
      AND retry_result.reconciled_at_utc = first_result.reconciled_at_utc
      AND retry_result.published_at_utc = first_result.published_at_utc
      AND retry_result.incremental_frontier_before_utc
            = first_result.incremental_frontier_before_utc
      AND retry_result.incremental_frontier_after_utc
            = first_result.incremental_frontier_after_utc
)
    THROW 51488, N'O retry PUBLISHED não devolveu exatamente a evidência imutável.', 1;

IF (SELECT COUNT_BIG(*) FROM ctl.execution_state_event
    WHERE execution_id = @main_execution) <> @events_before_retry
   OR (SELECT COUNT_BIG(*) FROM recon.execution_candidate_application
       WHERE execution_id = @main_execution) <> @applications_before_retry
   OR (SELECT COUNT_BIG(*) FROM recon.execution_reconciliation_result
       WHERE execution_id = @main_execution) <> 1
   OR (SELECT COUNT_BIG(*) FROM ctl.execution_publication_event
       WHERE execution_id = @main_execution) <> 1
   OR (SELECT COUNT_BIG(*) FROM ctl.partition_publication_pointer
       WHERE published_execution_id = @main_execution) <> 1
   OR (SELECT COUNT_BIG(*) FROM core.entity_record_state
       WHERE environment_name = @environment
         AND source_instance = @source
         AND tenant_scope = @tenant
         AND entity_name = @main_entity) <> @core_before_retry
    THROW 51489, N'O retry exato duplicou ou reaplicou evidência.', 1;

-- Três fixtures adicionais: publicação fora de ordem, BACKFILL e quarantine fail-closed.
EXEC ctl.usp_control_plane_register_incremental_frontier
    @environment_name = @environment,
    @source_instance = @source,
    @tenant_scope = @tenant,
    @entity_name = @gap_entity,
    @initial_contiguous_end_utc = @gap_start,
    @registered_at_utc = @caller_timestamp;

DECLARE @single_candidate_fixtures TABLE (
    fixture_order INT NOT NULL PRIMARY KEY,
    execution_id UNIQUEIDENTIFIER NOT NULL,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    partition_start_utc DATETIME2(3) NOT NULL,
    partition_end_utc DATETIME2(3) NOT NULL,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    validation_disposition NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    source_row_hash CHAR(64) NOT NULL,
    presence_fingerprint CHAR(64) NOT NULL,
    idempotency_key NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL
);

INSERT INTO @single_candidate_fixtures (
    fixture_order, execution_id, entity_name, execution_mode,
    partition_start_utc, partition_end_utc, source_key,
    validation_disposition, quarantine_reason_code,
    source_row_hash, presence_fingerprint, idempotency_key
)
VALUES
    (1, @gap_late_execution, @gap_entity, N'INCREMENTAL',
        @gap_middle, @gap_end, N'gap-late-key', N'VALID', NULL,
        @hash_a, @presence_1, N'synthetic-atomic-gap-late-001'),
    (2, @gap_early_execution, @gap_entity, N'INCREMENTAL',
        @gap_start, @gap_middle, N'gap-early-key', N'VALID', NULL,
        @hash_b, @presence_2, N'synthetic-atomic-gap-early-001'),
    (3, @backfill_execution, @backfill_entity, N'BACKFILL',
        @backfill_start, @backfill_end, N'backfill-key', N'VALID', NULL,
        @hash_c, @presence_3, N'synthetic-atomic-backfill-001'),
    (4, @quarantine_execution, @quarantine_entity, N'BACKFILL',
        @quarantine_start, @quarantine_end, N'quarantine-key', N'QUARANTINE',
        N'SYNTHETIC_INVALID', @hash_d, @presence_4,
        N'synthetic-atomic-quarantine-001');

DECLARE @fixture_execution UNIQUEIDENTIFIER;
DECLARE @fixture_entity NVARCHAR(128);
DECLARE @fixture_mode NVARCHAR(16);
DECLARE @fixture_start DATETIME2(3);
DECLARE @fixture_end DATETIME2(3);
DECLARE @fixture_source_key NVARCHAR(256);
DECLARE @fixture_disposition NVARCHAR(16);
DECLARE @fixture_reason NVARCHAR(64);
DECLARE @fixture_hash CHAR(64);
DECLARE @fixture_presence CHAR(64);
DECLARE @fixture_idempotency_key NVARCHAR(128);

DECLARE fixture_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT
    execution_id, entity_name, execution_mode, partition_start_utc, partition_end_utc,
    source_key, validation_disposition, quarantine_reason_code,
    source_row_hash, presence_fingerprint, idempotency_key
FROM @single_candidate_fixtures
ORDER BY fixture_order;

OPEN fixture_cursor;
FETCH NEXT FROM fixture_cursor INTO
    @fixture_execution, @fixture_entity, @fixture_mode, @fixture_start, @fixture_end,
    @fixture_source_key, @fixture_disposition, @fixture_reason,
    @fixture_hash, @fixture_presence, @fixture_idempotency_key;

WHILE @@FETCH_STATUS = 0
BEGIN
    DECLARE @fixture_now DATETIME2(3) = SYSUTCDATETIME();

    EXEC ctl.usp_control_plane_start_execution
        @execution_id = @fixture_execution,
        @cycle_id = @cycle,
        @environment_name = @environment,
        @source_instance = @source,
        @tenant_scope = @tenant,
        @entity_name = @fixture_entity,
        @execution_mode = @fixture_mode,
        @partition_start_utc = @fixture_start,
        @partition_end_exclusive_utc = @fixture_end,
        @window_strategy = N'SYNTHETIC_INTERVAL',
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @configuration_fingerprint,
        @idempotency_key = @fixture_idempotency_key,
        @lease_seconds = 86400,
        @started_at_utc = @fixture_now;

    EXEC stg.usp_stage_record
        @execution_id = @fixture_execution,
        @input_batch_number = 1,
        @input_record_ordinal = 1,
        @source_key = @fixture_source_key,
        @row_fingerprint_version = N'row-v1',
        @source_row_hash = @fixture_hash,
        @presence_fingerprint_version = N'presence-v1',
        @presence_fingerprint = @fixture_presence,
        @source_freshness_at_utc = @fixture_start,
        @validation_disposition = @fixture_disposition,
        @quarantine_reason_code = @fixture_reason,
        @staged_at_utc = @caller_timestamp;

    EXEC ctl.usp_control_plane_transition_execution
        @fixture_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @caller_timestamp;
    EXEC ctl.usp_control_plane_transition_execution
        @fixture_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @caller_timestamp;
    EXEC core.usp_prepare_staged_execution
        @execution_id = @fixture_execution,
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @configuration_fingerprint;

    FETCH NEXT FROM fixture_cursor INTO
        @fixture_execution, @fixture_entity, @fixture_mode, @fixture_start, @fixture_end,
        @fixture_source_key, @fixture_disposition, @fixture_reason,
        @fixture_hash, @fixture_presence, @fixture_idempotency_key;
END;

CLOSE fixture_cursor;
DEALLOCATE fixture_cursor;

IF (SELECT COUNT_BIG(*) FROM ctl.execution_attempt
    WHERE execution_id IN (
        @gap_late_execution, @gap_early_execution,
        @backfill_execution, @quarantine_execution
    ) AND current_state = N'PROMOTED') <> 4
    THROW 51490, N'Os fixtures auxiliares não alcançaram PROMOTED.', 1;

-- Publica [T1,T2) primeiro: pointer e PUBLISHED existem, mas a fronteira continua em T0.
INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @gap_late_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

UPDATE #atomic_publication_capture
SET scenario_name = N'GAP_LATE'
WHERE execution_id = @gap_late_execution AND scenario_name IS NULL;

DECLARE @gap_late_partition_id BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @gap_late_execution
);

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51491, N'A publicação fora de ordem rompeu a transação externa.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #atomic_publication_capture
    WHERE scenario_name = N'GAP_LATE'
      AND execution_id = @gap_late_execution
      AND candidate_rows = 1
      AND inserted_rows = 1
      AND updated_rows = 0
      AND reactivated_rows = 0
      AND noop_rows = 0
      AND stale_noop_rows = 0
      AND incremental_frontier_before_utc = @gap_start
      AND incremental_frontier_after_utc = @gap_start
)
   OR NOT EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer
        WHERE partition_id = @gap_late_partition_id
          AND published_execution_id = @gap_late_execution
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.incremental_publication_watermark
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @gap_entity
          AND contiguous_partition_end_utc = @gap_start
          AND last_partition_id IS NULL
          AND advanced_at_utc IS NULL
   )
    THROW 51492, N'Uma lacuna avançou o watermark ou perdeu seu pointer auditável.', 1;

-- Ao publicar [T0,T1), o fechamento deve atravessar a publicação anterior até T2.
INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @gap_early_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

UPDATE #atomic_publication_capture
SET scenario_name = N'GAP_CLOSE'
WHERE execution_id = @gap_early_execution AND scenario_name IS NULL;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51493, N'O fechamento contíguo rompeu a transação externa.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #atomic_publication_capture
    WHERE scenario_name = N'GAP_CLOSE'
      AND execution_id = @gap_early_execution
      AND incremental_frontier_before_utc = @gap_start
      AND incremental_frontier_after_utc = @gap_end
)
   OR NOT EXISTS (
        SELECT 1 FROM ctl.incremental_publication_watermark
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @gap_entity
          AND contiguous_partition_end_utc = @gap_end
          AND last_partition_id = @gap_late_partition_id
          AND advanced_at_utc IS NOT NULL
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_publication_event
        WHERE execution_id = @gap_early_execution
          AND incremental_frontier_before_utc = @gap_start
          AND incremental_frontier_after_utc = @gap_end
          AND watermark_last_partition_id = @gap_late_partition_id
   )
    THROW 51494, N'O fechamento contíguo não atravessou a publicação fora de ordem.', 1;

-- BACKFILL publica pointer/evento, mas não cria nem avança watermark incremental.
INSERT INTO #atomic_publication_capture (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc
)
EXEC core.usp_apply_reconcile_publish_execution
    @execution_id = @backfill_execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

UPDATE #atomic_publication_capture
SET scenario_name = N'BACKFILL'
WHERE execution_id = @backfill_execution AND scenario_name IS NULL;

DECLARE @backfill_partition_id BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @backfill_execution
);

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51495, N'A publicação BACKFILL rompeu a transação externa.', 1;

IF NOT EXISTS (
    SELECT 1 FROM #atomic_publication_capture
    WHERE scenario_name = N'BACKFILL'
      AND execution_id = @backfill_execution
      AND candidate_rows = 1
      AND inserted_rows = 1
      AND incremental_frontier_before_utc IS NULL
      AND incremental_frontier_after_utc IS NULL
)
   OR NOT EXISTS (
        SELECT 1 FROM ctl.partition_publication_pointer
        WHERE partition_id = @backfill_partition_id
          AND published_execution_id = @backfill_execution
   )
   OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_publication_event
        WHERE execution_id = @backfill_execution
          AND incremental_frontier_before_utc IS NULL
          AND incremental_frontier_after_utc IS NULL
          AND watermark_last_partition_id IS NULL
   )
   OR EXISTS (
        SELECT 1 FROM ctl.incremental_publication_watermark
        WHERE environment_name = @environment
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = @backfill_entity
   )
    THROW 51496, N'BACKFILL tocou watermark ou deixou de publicar pointer/evento.', 1;

-- Última ação dentro do caller: quarantine deve falhar em 51427. O CATCH sempre executa
-- ROLLBACK da transação inteira antes de qualquer nova asserção.
DECLARE @quarantine_error_number INT = NULL;
DECLARE @quarantine_error_message NVARCHAR(4000) = NULL;

BEGIN TRY
    EXEC core.usp_apply_reconcile_publish_execution
        @execution_id = @quarantine_execution,
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @configuration_fingerprint;
END TRY
BEGIN CATCH
    SET @quarantine_error_number = ERROR_NUMBER();
    SET @quarantine_error_message = ERROR_MESSAGE();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51497, N'O exercício terminou com transação externa aberta.', 1;

IF @quarantine_error_number IS NULL OR @quarantine_error_number <> 51427
BEGIN
    DECLARE @unexpected_quarantine_error NVARCHAR(2048) = CONCAT(
        N'Quarantine não falhou fechado em 51427. Erro observado: ',
        COALESCE(CONVERT(NVARCHAR(20), @quarantine_error_number), N'nenhum'),
        N' - ', COALESCE(@quarantine_error_message, N'sem mensagem')
    );
    THROW 51498, @unexpected_quarantine_error, 1;
END;

IF NOT EXISTS (
    SELECT 1 FROM #atomic_publication_transaction_sentinel WHERE marker = N'OUTSIDE'
)
   OR EXISTS (
        SELECT 1 FROM #atomic_publication_transaction_sentinel WHERE marker = N'PENDING'
   )
    THROW 51499, N'O sentinel detectou commit do caller ou rollback fora do limite.', 1;

IF EXISTS (SELECT 1 FROM #atomic_publication_capture)
    THROW 51500, N'O rollback final não removeu as capturas transacionais.', 1;

DROP TABLE #atomic_publication_capture;
DROP TABLE #atomic_publication_transaction_sentinel;

PRINT N'Protocolo atômico exercitado e integralmente revertido com sucesso.';

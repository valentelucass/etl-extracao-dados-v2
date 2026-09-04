-- Exercício sintético, fail-closed e rollback-only de V2-020.
-- Execute no diretório database\validation contra o alvo local explicitamente autorizado.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51350, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "001_validate_schema_foundation.sql"
GO
:r "003_validate_control_plane.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @window_start DATETIME2(3) = DATEADD(HOUR, -1, @now);
DECLARE @window_end DATETIME2(3) = @now;
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_CONTROL_SOURCE';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000100';
DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000101';
DECLARE @replay_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000102';
DECLARE @mismatched_replay_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000103';
DECLARE @case_distinct_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000104';
DECLARE @recovery_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000105';
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @caller_technical_time DATETIME2(3) = '2000-01-01T00:00:00.000';
DECLARE @catalog_cycle_before DATETIME2(3) = SYSUTCDATETIME();

EXEC ctl.usp_control_plane_register_source
    @source_instance = N'SYNTHETIC_CONTROL_SOURCE   ',
    @source_kind = N'DATA_EXPORT   ',
    @registered_at_utc = @caller_technical_time;
-- Retry exato ignora o relógio técnico do caller e não altera o registro original.
EXEC ctl.usp_control_plane_register_source @source, N'DATA_EXPORT', @now;
EXEC ctl.usp_control_plane_register_source N'COLLATION_KeyA', N'SYNTHETIC', @caller_technical_time;
EXEC ctl.usp_control_plane_register_source N'COLLATION_keya', N'SYNTHETIC', @caller_technical_time;
EXEC ctl.usp_control_plane_register_source N'COLLATION_acao', N'SYNTHETIC', @caller_technical_time;
EXEC ctl.usp_control_plane_register_source N'COLLATION_ação', N'SYNTHETIC', @caller_technical_time;
DECLARE @tabbed_source NVARCHAR(128) = NCHAR(9) + N'COLLATION_Tab' + NCHAR(9);
EXEC ctl.usp_control_plane_register_source @tabbed_source, N'SYNTHETIC', @caller_technical_time;

IF (SELECT COUNT_BIG(*) FROM ctl.source_catalog
    WHERE source_instance IN (
        N'COLLATION_KeyA', N'COLLATION_keya', N'COLLATION_acao', N'COLLATION_ação'
    )) <> 4
    THROW 51361, N'A collation semântica colapsou caixa ou acento no catálogo de fontes.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.source_catalog WHERE source_instance = @tabbed_source
)
    THROW 51367, N'LTRIM/RTRIM removeu TAB que deveria permanecer dado opaco BIN2.', 1;

EXEC ctl.usp_control_plane_start_cycle
    @cycle_id = @cycle,
    @plan_version = N'synthetic-plan-v1   ',
    @plan_fingerprint = @plan_fingerprint,
    @planned_at_utc = @caller_technical_time;
EXEC ctl.usp_control_plane_start_cycle
    @cycle_id = @cycle,
    @plan_version = N'synthetic-plan-v1',
    @plan_fingerprint = @plan_fingerprint,
    @planned_at_utc = @now;
DECLARE @catalog_cycle_after DATETIME2(3) = SYSUTCDATETIME();

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_cycle
    WHERE cycle_id = @cycle
      AND plan_version = N'synthetic-plan-v1'
      AND plan_fingerprint = @plan_fingerprint
      AND planned_at_utc BETWEEN @catalog_cycle_before AND @catalog_cycle_after
      AND planned_at_utc <> @caller_technical_time
)
    THROW 51351, N'Versão e fingerprint do plano não foram persistidos juntos.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.source_catalog
    WHERE source_instance = @source
      AND source_kind = N'DATA_EXPORT'
      AND registered_at_utc BETWEEN @catalog_cycle_before AND @catalog_cycle_after
      AND registered_at_utc <> @caller_technical_time
)
    THROW 51363, N'Registro de fonte ou planejamento confiaram no relógio do caller.', 1;

DECLARE @start_before DATETIME2(3) = SYSUTCDATETIME();
DECLARE @caller_started_at DATETIME2(3) = DATEADD(MINUTE, 4, @start_before);
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW   ',
    @source_instance = N'SYNTHETIC_CONTROL_SOURCE   ',
    @tenant_scope = N'SYNTHETIC_TENANT   ',
    @entity_name = N'SYNTHETIC_ENTITY   ',
    @execution_mode = N'BACKFILL   ',
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL   ',
    @contract_version = N'synthetic-contract-v1   ',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1   ',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-control-containment-001   ',
    @lease_seconds = 3600,
    @started_at_utc = @caller_started_at;
DECLARE @start_after DATETIME2(3) = SYSUTCDATETIME();

IF NOT EXISTS (
    SELECT 1
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    INNER JOIN ctl.execution_lease AS lease ON lease.execution_id = attempt.execution_id
    WHERE attempt.execution_id = @execution
      AND attempt.started_at_utc BETWEEN @start_before AND @start_after
      AND attempt.started_at_utc <> @caller_started_at
      AND lease.acquired_at_utc = attempt.started_at_utc
      AND lease.heartbeat_at_utc = attempt.started_at_utc
      AND lease.expires_at_utc = DATEADD(SECOND, 3600, attempt.started_at_utc)
      AND attempt.window_strategy = N'SYNTHETIC_INTERVAL'
      AND attempt.contract_version = N'synthetic-contract-v1'
      AND attempt.configuration_version = N'synthetic-config-v1'
      AND attempt.idempotency_key = N'synthetic-control-containment-001'
      AND DATALENGTH(attempt.idempotency_key)
          = DATALENGTH(LTRIM(RTRIM(attempt.idempotency_key)))
      AND partition.environment_name = N'LOCAL_SHADOW'
      AND partition.source_instance = @source
      AND partition.tenant_scope = N'SYNTHETIC_TENANT'
      AND partition.entity_name = N'SYNTHETIC_ENTITY'
      AND partition.execution_mode = N'BACKFILL'
)
    THROW 51357, N'O início ou a lease confiaram no timestamp futuro do caller.', 1;

-- A única diferença é a caixa do tenant; deve existir outra partição com lease simultânea.
DECLARE @case_started_at DATETIME2(3) = SYSUTCDATETIME();
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @case_distinct_execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = @source,
    @tenant_scope = N'synthetic_tenant',
    @entity_name = N'SYNTHETIC_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'SYNTHETIC-CONTROL-CONTAINMENT-001',
    @lease_seconds = 3600,
    @started_at_utc = @case_started_at;

IF (SELECT COUNT(DISTINCT partition_id) FROM ctl.execution_attempt
    WHERE execution_id IN (@execution, @case_distinct_execution)) <> 2
    OR (SELECT COUNT_BIG(*) FROM ctl.execution_lease
        WHERE execution_id IN (@execution, @case_distinct_execution)
          AND released_at_utc IS NULL) <> 2
    THROW 51362, N'Partições/leases distintas por caixa foram colapsadas.', 1;

DECLARE @heartbeat_before DATETIME2(3) = SYSUTCDATETIME();
EXEC ctl.usp_control_plane_heartbeat_lease
    @execution_id = @execution,
    @heartbeat_at_utc = '2000-01-01T00:00:00.000',
    @lease_seconds = 3600,
    @expires_at_utc = '2000-01-01T01:00:00.000';
DECLARE @heartbeat_after DATETIME2(3) = SYSUTCDATETIME();

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_lease
    WHERE execution_id = @execution
      AND heartbeat_at_utc BETWEEN @heartbeat_before AND @heartbeat_after
      AND expires_at_utc = DATEADD(SECOND, 3600, heartbeat_at_utc)
      AND heartbeat_at_utc <> '2000-01-01T00:00:00.000'
)
    THROW 51355, N'Heartbeat confiou no relógio do caller em vez do SQL Server.', 1;

EXEC ctl.usp_control_plane_record_page
    @execution_id = @execution,
    @page_number = 1,
    @page_attempt = 1,
    @requested_page_size = 3,
    @physical_rows = 3,
    @distinct_root_keys = 2,
    @response_bytes = 512,
    @terminal_empty_page = 0,
    @read_at_utc = '2000-01-01T00:00:00.000';

EXEC ctl.usp_control_plane_record_counts
    @execution_id = @execution,
    @count_phase = N'SYNTHETIC_STAGE',
    @physical_rows = 3,
    @distinct_root_keys = 2,
    @duplicate_rows = 1,
    @valid_rows = 1,
    @quarantined_root_keys = 1,
    @recorded_at_utc = '2000-01-01T00:00:00.000',
    @unidentified_quarantine_rows = 0;

-- A reserva é BIN2 e exata: a variante minúscula permanece um token externo distinto.
EXEC ctl.usp_control_plane_record_counts
    @execution_id = @execution,
    @count_phase = N'staging_kernel',
    @physical_rows = 3,
    @distinct_root_keys = 2,
    @duplicate_rows = 1,
    @valid_rows = 1,
    @quarantined_root_keys = 1,
    @recorded_at_utc = '2000-01-01T00:00:00.000',
    @unidentified_quarantine_rows = 0;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_count
    WHERE execution_id = @execution AND count_phase = N'staging_kernel'
)
    OR EXISTS (
        SELECT 1 FROM ctl.execution_count
        WHERE execution_id = @execution AND count_phase = N'STAGING_KERNEL'
    )
    THROW 51368, N'A reserva exata de STAGING_KERNEL sofreu case-fold.', 1;

EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', '2000-01-01T00:00:00.000';
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTED', N'STAGED', N'STAGE_OK', '2000-01-01T00:00:00.000';

DECLARE @event_clock_after DATETIME2(3) = SYSUTCDATETIME();
IF EXISTS (
    SELECT 1 FROM ctl.execution_page_audit
    WHERE execution_id = @execution
      AND (read_at_utc < @start_before OR read_at_utc > @event_clock_after
           OR read_at_utc = '2000-01-01T00:00:00.000')
)
    OR EXISTS (
        SELECT 1 FROM ctl.execution_count
        WHERE execution_id = @execution
          AND (recorded_at_utc < @start_before OR recorded_at_utc > @event_clock_after
               OR recorded_at_utc = '2000-01-01T00:00:00.000')
    )
    OR EXISTS (
        SELECT 1 FROM ctl.execution_state_event
        WHERE execution_id = @execution
          AND reason_code IN (N'EXTRACTION_OK', N'STAGE_OK')
          AND (transitioned_at_utc < @start_before OR transitioned_at_utc > @event_clock_after
               OR transitioned_at_utc = '2000-01-01T00:00:00.000')
    )
    THROW 51358, N'Page, contagens ou transições confiaram no relógio do caller.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution
      AND attempt.next_transition_sequence = 5
      AND partition.next_attempt_number = 2
)
    THROW 51356, N'Os contadores atômicos não avançaram deterministicamente.', 1;

IF EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 1
      AND permission_definition.state IN ('G', 'W')
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.major_id IN (
          OBJECT_ID(N'ctl.usp_control_plane_register_incremental_frontier', N'P'),
          OBJECT_ID(N'ctl.usp_control_plane_publish_execution', N'P')
      )
)
    THROW 51352, N'Runtime ainda possui atalho administrativo ou de publicação.', 1;

IF EXISTS (SELECT 1 FROM ctl.partition_publication_pointer)
    OR EXISTS (SELECT 1 FROM ctl.incremental_publication_watermark WHERE advanced_at_utc IS NOT NULL)
    THROW 51353, N'O protocolo contido não pode criar pointer ou avançar watermark.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%@next_state COLLATE Latin1_General_100_BIN2%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%IN (N''PROMOTED'', N''RECONCILED'', N''PUBLISHED'')%'
    THROW 51354, N'O atalho manual STAGED para PROMOTED deveria falhar.', 1;

DECLARE @terminal_before DATETIME2(3) = SYSUTCDATETIME();
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'STAGED', N'FAILED', N'SYNTHETIC_FAILURE', '2000-01-01T00:00:00.000';
DECLARE @terminal_after DATETIME2(3) = SYSUTCDATETIME();

-- Retries exatos depois do commit incerto retornam antes do fencing da lease já liberada.
EXEC ctl.usp_control_plane_record_page
    @execution_id = @execution,
    @page_number = 1,
    @page_attempt = 1,
    @requested_page_size = 3,
    @physical_rows = 3,
    @distinct_root_keys = 2,
    @response_bytes = 512,
    @terminal_empty_page = 0,
    @read_at_utc = '1999-01-01T00:00:00.000';
EXEC ctl.usp_control_plane_record_counts
    @execution_id = @execution,
    @count_phase = N'SYNTHETIC_STAGE',
    @physical_rows = 3,
    @distinct_root_keys = 2,
    @duplicate_rows = 1,
    @valid_rows = 1,
    @quarantined_root_keys = 1,
    @recorded_at_utc = '1999-01-01T00:00:00.000',
    @unidentified_quarantine_rows = 0;
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'STAGED', N'FAILED', N'SYNTHETIC_FAILURE', '1999-01-01T00:00:00.000';

IF NOT EXISTS (
    SELECT 1
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_lease AS lease ON lease.execution_id = attempt.execution_id
    INNER JOIN ctl.execution_state_event AS event ON event.execution_id = attempt.execution_id
    WHERE attempt.execution_id = @execution
      AND attempt.current_state = N'FAILED'
      AND event.reason_code = N'SYNTHETIC_FAILURE'
      AND attempt.terminal_at_utc BETWEEN @terminal_before AND @terminal_after
      AND event.transitioned_at_utc = attempt.terminal_at_utc
      AND lease.released_at_utc = attempt.terminal_at_utc
      AND attempt.terminal_at_utc <> '2000-01-01T00:00:00.000'
)
    THROW 51359, N'Terminal, evento ou liberação de lease confiaram no relógio do caller.', 1;

IF (SELECT COUNT_BIG(*) FROM ctl.execution_page_audit
    WHERE execution_id = @execution AND page_number = 1 AND page_attempt = 1) <> 1
    OR (SELECT COUNT_BIG(*) FROM ctl.execution_count
        WHERE execution_id = @execution AND count_phase = N'SYNTHETIC_STAGE') <> 1
    OR (SELECT COUNT_BIG(*) FROM ctl.execution_state_event
        WHERE execution_id = @execution AND reason_code = N'SYNTHETIC_FAILURE') <> 1
    THROW 51364, N'Retry tardio exato duplicou page, count ou transição terminal.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
       NOT LIKE N'%@count_phase COLLATE Latin1_General_100_BIN2 = N''STAGING_KERNEL''%'
    THROW 51365, N'A API runtime de contagens não reserva STAGING_KERNEL.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
       NOT LIKE N'%THROW 51334%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
       NOT LIKE N'%THROW 51335%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%@next_transition_sequence - 1%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%@reason_code COLLATE Latin1_General_100_BIN2 LIKE%'
    THROW 51366, N'Retry divergente não permanece fail-closed.', 1;

IF CHARINDEX(
       N'LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE ''[A-Z]''',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
   ) = 0
    OR EXISTS (
        SELECT 1
        FROM (VALUES
            (N'_X'),
            (N'1X'),
            (N'xX'),
            (NCHAR(9) + N'X'),
            (N'ßX')
        ) AS invalid_reason (reason_code)
        WHERE LEFT(invalid_reason.reason_code, 1) COLLATE Latin1_General_100_BIN2
                  LIKE N'[A-Z]'
          AND invalid_reason.reason_code COLLATE Latin1_General_100_BIN2
                  NOT LIKE N'%[^A-Z0-9_]%'
          AND LEN(invalid_reason.reason_code) BETWEEN 2 AND 64
    )
    THROW 51369, N'Reason code inválido passou pela gramática canônica BIN2.', 1;

-- Recovery não recebe instante do caller: expiração, terminal, evento e release usam uma única
-- amostra do relógio SQL e a procedure devolve somente a contagem agregada.
DECLARE @recovery_window_start DATETIME2(3) = DATEADD(HOUR, -3, @window_start);
DECLARE @recovery_window_end DATETIME2(3) = DATEADD(HOUR, -2, @window_start);
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @recovery_execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = @source,
    @tenant_scope = N'SYNTHETIC_TENANT',
    @entity_name = N'SYNTHETIC_RECOVERY_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @recovery_window_start,
    @partition_end_exclusive_utc = @recovery_window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-control-recovery-001',
    @lease_seconds = 3600,
    @started_at_utc = @now;

DECLARE @forced_stale_clock DATETIME2(3) = SYSUTCDATETIME();
UPDATE ctl.execution_lease
SET acquired_at_utc = DATEADD(HOUR, -2, @forced_stale_clock),
    heartbeat_at_utc = DATEADD(HOUR, -2, @forced_stale_clock),
    expires_at_utc = DATEADD(HOUR, -1, @forced_stale_clock)
WHERE execution_id = @recovery_execution;

DECLARE @recovery_result TABLE (recovered_executions BIGINT NOT NULL);
DECLARE @recovery_before DATETIME2(3) = SYSUTCDATETIME();
INSERT INTO @recovery_result (recovered_executions)
EXEC ctl.usp_control_plane_recover_stale_executions;
DECLARE @recovery_after DATETIME2(3) = SYSUTCDATETIME();

IF (SELECT COUNT_BIG(*) FROM @recovery_result WHERE recovered_executions = 1) <> 1
    OR NOT EXISTS (
        SELECT 1
        FROM ctl.execution_attempt AS attempt
        INNER JOIN ctl.execution_partition AS partition
            ON partition.partition_id = attempt.partition_id
        INNER JOIN ctl.execution_lease AS lease
            ON lease.execution_id = attempt.execution_id
        INNER JOIN ctl.execution_state_event AS event
            ON event.execution_id = attempt.execution_id
           AND event.reason_code = N'STALE_LEASE_RECOVERED'
        WHERE attempt.execution_id = @recovery_execution
          AND attempt.current_state = N'FAILED'
          AND partition.current_state = N'FAILED'
          AND attempt.terminal_at_utc BETWEEN @recovery_before AND @recovery_after
          AND event.transitioned_at_utc = attempt.terminal_at_utc
          AND lease.released_at_utc = attempt.terminal_at_utc
    )
    THROW 51370, N'Recovery não usou relógio SQL ou não fechou execução/partição/lease.', 1;

DELETE FROM @recovery_result;
INSERT INTO @recovery_result (recovered_executions)
EXEC ctl.usp_control_plane_recover_stale_executions;
IF (SELECT COUNT_BIG(*) FROM @recovery_result WHERE recovered_executions = 0) <> 1
    THROW 51371, N'Retry de recovery sem leases stale não retornou zero de forma idempotente.', 1;

DECLARE @replay_started_at DATETIME2(3) = SYSUTCDATETIME();
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @replay_execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = @source,
    @tenant_scope = N'SYNTHETIC_TENANT',
    @entity_name = N'SYNTHETIC_ENTITY',
    @execution_mode = N'REPLAY',
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-control-replay-001',
    @replay_of_execution_id = @execution,
    @lease_seconds = 3600,
    @started_at_utc = @replay_started_at;

-- Retry tardio exato deve retornar pelo fast-path; o timestamp técnico do caller não é identidade.
EXEC ctl.usp_control_plane_start_execution
    @execution_id = @replay_execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = @source,
    @tenant_scope = N'SYNTHETIC_TENANT',
    @entity_name = N'SYNTHETIC_ENTITY',
    @execution_mode = N'REPLAY',
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-control-replay-001',
    @replay_of_execution_id = @execution,
    @lease_seconds = 3600,
    @started_at_utc = '2000-01-01T00:00:00.000';

DECLARE @replay_mismatch_rejected BIT = 0;
DECLARE @mismatch_started_at DATETIME2(3) = SYSUTCDATETIME();
IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51363, N'O escopo rollback-only não estava íntegro antes do replay negativo final.', 1;
BEGIN TRY
    EXEC ctl.usp_control_plane_start_execution
        @execution_id = @mismatched_replay_execution,
        @cycle_id = @cycle,
        @environment_name = N'LOCAL_SHADOW',
        @source_instance = @source,
        @tenant_scope = N'OTHER_SYNTHETIC_TENANT',
        @entity_name = N'SYNTHETIC_ENTITY',
        @execution_mode = N'REPLAY',
        @partition_start_utc = @window_start,
        @partition_end_exclusive_utc = @window_end,
        @window_strategy = N'SYNTHETIC_INTERVAL',
        @contract_version = N'synthetic-contract-v1',
        @contract_fingerprint = @contract_fingerprint,
        @configuration_version = N'synthetic-config-v1',
        @configuration_fingerprint = @configuration_fingerprint,
        @idempotency_key = N'synthetic-control-replay-mismatch-001',
        @replay_of_execution_id = @execution,
        @lease_seconds = 3600,
        @started_at_utc = @mismatch_started_at;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51333
        SET @replay_mismatch_rejected = 1;
    ELSE
        THROW;
END CATCH;

IF @replay_mismatch_rejected = 0
    THROW 51360, N'Replay de outro namespace semântico deveria falhar.', 1;

-- O replay negativo pode deixar uma transação interna aberta e todo o escopo não comittable sob
-- XACT_ABORT. O escopo externo provado acima não pode desaparecer; ROLLBACK encerra todos os níveis.
IF XACT_STATE() NOT IN (1, -1) OR @@TRANCOUNT < 1
    THROW 51361, N'O escopo rollback-only do control plane foi consumido ou invalidado.', 1;

ROLLBACK TRANSACTION;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51362, N'O rollback do control plane não encerrou o escopo sintético.', 1;

PRINT N'Exercício rollback-only do control plane contido concluído com sucesso.';

-- Validador somente leitura de V2-020. Não retorna payload, IDs de negócio, URL ou segredo.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_tables TABLE (object_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_tables (object_name)
VALUES
    (N'source_catalog'),
    (N'execution_cycle'),
    (N'execution_partition'),
    (N'execution_attempt'),
    (N'execution_state_event'),
    (N'execution_lease'),
    (N'execution_page_audit'),
    (N'execution_count'),
    (N'source_watermark_observation'),
    (N'partition_publication_pointer'),
    (N'execution_publication_event'),
    (N'incremental_publication_watermark');

INSERT INTO @failures (category, object_name, detail)
SELECT N'TABLE', expected.object_name, N'Tabela obrigatória do control plane ausente.'
FROM @expected_tables AS expected
WHERE OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'U') IS NULL;

DECLARE @expected_procedures TABLE (object_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_procedures (object_name)
VALUES
    (N'usp_control_plane_register_source'),
    (N'usp_control_plane_start_cycle'),
    (N'usp_control_plane_start_execution'),
    (N'usp_control_plane_heartbeat_lease'),
    (N'usp_control_plane_record_page'),
    (N'usp_control_plane_record_counts'),
    (N'usp_control_plane_transition_execution'),
    (N'usp_control_plane_register_incremental_frontier'),
    (N'usp_control_plane_publish_execution'),
    (N'usp_control_plane_recover_stale_executions');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', expected.object_name, N'Procedure obrigatória do control plane ausente.'
FROM @expected_procedures AS expected
WHERE OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P') IS NULL;

DECLARE @expected_partition_columns TABLE (column_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_partition_columns (column_name)
VALUES
    (N'environment_name'),
    (N'source_instance'),
    (N'tenant_scope'),
    (N'entity_name'),
    (N'execution_mode'),
    (N'partition_start_utc'),
    (N'partition_end_exclusive_utc');

INSERT INTO @failures (category, object_name, detail)
SELECT N'KEY_COLUMN', expected.column_name, N'Coluna da chave semântica ausente.'
FROM @expected_partition_columns AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.columns AS column_definition
    WHERE column_definition.object_id = OBJECT_ID(N'ctl.execution_partition', N'U')
      AND column_definition.name = expected.column_name
);

DECLARE @expected_binary_columns TABLE (
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL,
    PRIMARY KEY (table_name, column_name)
);
INSERT INTO @expected_binary_columns (table_name, column_name)
VALUES
    (N'source_catalog', N'source_instance'),
    (N'execution_partition', N'environment_name'),
    (N'execution_partition', N'source_instance'),
    (N'execution_partition', N'tenant_scope'),
    (N'execution_partition', N'entity_name'),
    (N'execution_partition', N'execution_mode'),
    (N'execution_attempt', N'idempotency_key'),
    (N'incremental_publication_watermark', N'environment_name'),
    (N'incremental_publication_watermark', N'source_instance'),
    (N'incremental_publication_watermark', N'tenant_scope'),
    (N'incremental_publication_watermark', N'entity_name');

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(N'ctl.', expected.table_name, N'.', expected.column_name),
       N'Boundary textual precisa de comparação binária, case-sensitive e accent-sensitive.'
FROM @expected_binary_columns AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(CONCAT(N'ctl.', expected.table_name), N'U')
      AND name = expected.column_name
      AND collation_name = N'Latin1_General_100_BIN2'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(N'ctl.', table_definition.name, N'.', column_definition.name),
       N'Toda coluna NVARCHAR persistida do control plane deve usar BIN2 explícita.'
FROM sys.tables AS table_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = table_definition.schema_id
INNER JOIN sys.columns AS column_definition
    ON column_definition.object_id = table_definition.object_id
INNER JOIN sys.types AS type_definition
    ON type_definition.user_type_id = column_definition.user_type_id
WHERE schema_definition.name = N'ctl'
  AND type_definition.name = N'nvarchar'
  AND column_definition.collation_name <> N'Latin1_General_100_BIN2';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', N'ctl.idempotent_label_comparison',
       N'Comparações via variáveis precisam preservar BIN2 para source_kind e plan_version.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'))
          NOT LIKE N'%@existing_source_kind COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P'))
          NOT LIKE N'%@existing_version COLLATE Latin1_General_100_BIN2%';

DECLARE @expected_wide_text_parameters TABLE (
    procedure_name SYSNAME NOT NULL,
    parameter_name SYSNAME NOT NULL,
    PRIMARY KEY (procedure_name, parameter_name)
);
INSERT INTO @expected_wide_text_parameters (procedure_name, parameter_name)
VALUES
    (N'usp_control_plane_register_source', N'@source_instance'),
    (N'usp_control_plane_register_source', N'@source_kind'),
    (N'usp_control_plane_start_cycle', N'@plan_version'),
    (N'usp_control_plane_start_cycle', N'@plan_fingerprint'),
    (N'usp_control_plane_start_execution', N'@environment_name'),
    (N'usp_control_plane_start_execution', N'@source_instance'),
    (N'usp_control_plane_start_execution', N'@tenant_scope'),
    (N'usp_control_plane_start_execution', N'@entity_name'),
    (N'usp_control_plane_start_execution', N'@execution_mode'),
    (N'usp_control_plane_start_execution', N'@window_strategy'),
    (N'usp_control_plane_start_execution', N'@contract_version'),
    (N'usp_control_plane_start_execution', N'@contract_fingerprint'),
    (N'usp_control_plane_start_execution', N'@configuration_version'),
    (N'usp_control_plane_start_execution', N'@configuration_fingerprint'),
    (N'usp_control_plane_start_execution', N'@idempotency_key'),
    (N'usp_control_plane_record_counts', N'@count_phase'),
    (N'usp_control_plane_transition_execution', N'@expected_current_state'),
    (N'usp_control_plane_transition_execution', N'@next_state'),
    (N'usp_control_plane_transition_execution', N'@reason_code');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', CONCAT(N'ctl.', expected.procedure_name, N'.', expected.parameter_name),
       N'Entrada textual precisa ser MAX para impedir truncamento antes do corpo da procedure.'
FROM @expected_wide_text_parameters AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.parameters AS parameter_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = parameter_definition.user_type_id
    WHERE parameter_definition.object_id =
          OBJECT_ID(CONCAT(N'ctl.', expected.procedure_name), N'P')
      AND parameter_definition.name = expected.parameter_name
      AND parameter_definition.max_length = -1
      AND type_definition.name = N'nvarchar'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'NORMALIZATION', CONCAT(N'ctl.', expected.procedure_name, N'.', expected.parameter_name),
       N'O limite precisa ser validado com DATALENGTH antes de normalizar a entrada.'
FROM @expected_wide_text_parameters AS expected
CROSS APPLY (
    SELECT OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.procedure_name), N'P')) AS definition
) AS procedure_definition
WHERE procedure_definition.definition NOT LIKE
      N'%DATALENGTH(' + expected.parameter_name + N')%';

DECLARE @normalization_order TABLE (
    procedure_name SYSNAME NOT NULL PRIMARY KEY,
    first_normalization NVARCHAR(128) NOT NULL
);
INSERT INTO @normalization_order (procedure_name, first_normalization)
VALUES
    (N'usp_control_plane_register_source', N'SET @source_instance = LTRIM'),
    (N'usp_control_plane_start_cycle', N'SET @plan_version = LTRIM'),
    (N'usp_control_plane_start_execution', N'SET @environment_name = LTRIM'),
    (N'usp_control_plane_record_counts', N'SET @count_phase = LTRIM'),
    (N'usp_control_plane_transition_execution', N'SET @expected_current_state = LTRIM');

INSERT INTO @failures (category, object_name, detail)
SELECT N'NORMALIZATION', CONCAT(N'ctl.', expected.procedure_name),
       N'DATALENGTH precisa executar antes de qualquer trim/lower da entrada textual.'
FROM @normalization_order AS expected
CROSS APPLY (
    SELECT OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.procedure_name), N'P')) AS definition
) AS procedure_definition
WHERE CHARINDEX(N'IF DATALENGTH(', procedure_definition.definition) = 0
   OR CHARINDEX(expected.first_normalization, procedure_definition.definition) = 0
   OR CHARINDEX(N'IF DATALENGTH(', procedure_definition.definition)
      >= CHARINDEX(expected.first_normalization, procedure_definition.definition);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'UQ_ctl_execution_partition_semantic_key',
       N'A chave semântica única não possui as sete colunas canônicas.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    INNER JOIN sys.index_columns AS index_column
        ON index_column.object_id = index_definition.object_id
       AND index_column.index_id = index_definition.index_id
    INNER JOIN sys.columns AS column_definition
        ON column_definition.object_id = index_column.object_id
       AND column_definition.column_id = index_column.column_id
    WHERE index_definition.object_id = OBJECT_ID(N'ctl.execution_partition', N'U')
      AND index_definition.name = N'UQ_ctl_execution_partition_semantic_key'
      AND index_definition.is_unique = 1
    GROUP BY index_definition.index_id
    HAVING COUNT(*) = 7
       AND SUM(CASE WHEN column_definition.name IN (
            N'environment_name', N'source_instance', N'tenant_scope', N'entity_name',
            N'execution_mode', N'partition_start_utc', N'partition_end_exclusive_utc'
       ) THEN 1 ELSE 0 END) = 7
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'CK_ctl_execution_count_equation',
       N'As duas equações canônicas de contagem estão ausentes ou divergentes.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'ctl.execution_count', N'U')
      AND name = N'CK_ctl_execution_count_equation'
      AND definition LIKE N'%physical_rows%=%distinct_root_keys%+%duplicate_rows%+%unidentified_quarantine_rows%'
      AND definition LIKE N'%distinct_root_keys%=%valid_rows%+%quarantined_root_keys%'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', N'ctl.execution_count.unidentified_quarantine_rows',
       N'Quarantine físico sem chave precisa de contador próprio e obrigatório.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ctl.execution_count', N'U')
      AND name = N'unidentified_quarantine_rows'
      AND is_nullable = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', N'ctl.execution_cycle.plan_version',
       N'A versão imutável do plano está ausente ou permite NULL.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ctl.execution_cycle', N'U')
      AND name = N'plan_version'
      AND is_nullable = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', N'ctl.usp_control_plane_start_cycle.@plan_version',
       N'O start de ciclo não persiste plan_version.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.parameters
    WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P')
      AND name = N'@plan_version'
      AND parameter_id = 2
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COUNTER', N'ctl.execution_partition.next_attempt_number',
       N'O attempt_number não possui alocador atômico persistido.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ctl.execution_partition', N'U')
      AND name = N'next_attempt_number'
      AND is_nullable = 0
)
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
       NOT LIKE N'%OUTPUT deleted.next_attempt_number INTO @allocated_attempt%'
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
       LIKE N'%MAX(attempt_number)%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COUNTER', N'ctl.execution_attempt.next_transition_sequence',
       N'A sequência de transição não possui alocador atômico persistido.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ctl.execution_attempt', N'U')
      AND name = N'next_transition_sequence'
      AND is_nullable = 0
)
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%OUTPUT deleted.next_transition_sequence%'
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P'))
       NOT LIKE N'%deleted.next_transition_sequence%'
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       LIKE N'%MAX(transition_sequence)%'
OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P'))
       LIKE N'%MAX(transition_sequence)%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'LEASE_FENCING', N'ctl.usp_control_plane_heartbeat_lease',
       N'Heartbeat deve travar execução corrente e usar o relógio do banco.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%partition.current_execution_id = attempt.execution_id%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%@lease_expires_at_utc = lease.expires_at_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%@lease_expires_at_utc <= @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%heartbeat_at_utc = @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
          NOT LIKE N'%expires_at_utc = DATEADD(SECOND, @lease_seconds, @database_now_utc)%';

IF CHARINDEX(
       N'@lease_expires_at_utc = lease.expires_at_utc',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
   ) >= CHARINDEX(
       N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_heartbeat_lease', N'P'))
   )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CLOCK_AUTHORITY', N'ctl.usp_control_plane_heartbeat_lease',
        N'O relógio do heartbeat precisa ser capturado depois de adquirir os locks da lease.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'ctl.usp_control_plane_start_execution',
       N'O início deve limitar skew do caller e usar exclusivamente o relógio do banco para tentativa e lease.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%ABS(DATEDIFF_BIG(SECOND, @started_at_utc, @database_now_utc)) > 300%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%@idempotency_key, @replay_of_execution_id, N''EXTRACTING'', 3, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%DATEADD(SECOND, @lease_seconds, @database_now_utc)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          LIKE N'%expires_at_utc > @started_at_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          LIKE N'%expires_at_utc <= @started_at_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'ctl.usp_control_plane_register_source',
       N'Registro de fonte deve persistir relógio do banco capturado depois do lock da chave.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'))
          NOT LIKE N'%VALUES (@source_instance, @source_kind, 1, @database_now_utc)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'))
          LIKE N'%VALUES (@source_instance, @source_kind, 1, @registered_at_utc)%'
   OR CHARINDEX(
          N'FROM ctl.source_catalog WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'))
      ) >= CHARINDEX(
          N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'ctl.usp_control_plane_start_cycle',
       N'Ciclo deve persistir relógio do banco após o lock e retry não pode comparar planned_at do caller.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P'))
          NOT LIKE N'%VALUES (@cycle_id, @plan_version, LOWER(@plan_fingerprint), @database_now_utc)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P'))
          LIKE N'%@existing_planned_at_utc%'
   OR CHARINDEX(
          N'FROM ctl.execution_cycle WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P'))
      ) >= CHARINDEX(
          N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_cycle', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'REPLAY_FENCING', N'ctl.usp_control_plane_start_execution',
       N'Replay precisa de origem anterior não-REPLAY no mesmo namespace e janela, sem autorreferência.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%@replay_of_execution_id = @execution_id%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%replay_origin.execution_id = @replay_of_execution_id%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.environment_name = @environment_name%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.source_instance = @source_instance%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.tenant_scope = @tenant_scope%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.entity_name = @entity_name%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.execution_mode IN%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.partition_start_utc = @partition_start_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%origin_partition.partition_end_exclusive_utc = @partition_end_exclusive_utc%';

DECLARE @fenced_event_procedures TABLE (object_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @fenced_event_procedures (object_name)
VALUES (N'usp_control_plane_record_page'), (N'usp_control_plane_record_counts');

INSERT INTO @failures (category, object_name, detail)
SELECT N'LEASE_FENCING', expected.object_name,
       N'Evento deve ser inserido na mesma transação que trava attempt, partition e lease ativa.'
FROM @fenced_event_procedures AS expected
WHERE OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%BEGIN TRANSACTION%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%partition.current_execution_id = @execution_id%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%@lease_released_at_utc = lease.released_at_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%@lease_released_at_utc IS NOT NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%@lease_expires_at_utc <= @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
          NOT LIKE N'%COMMIT TRANSACTION%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'IDEMPOTENCY', N'ctl.usp_control_plane_record_page',
       N'Retry exato de página deve travar attempt→page, ignorar clock do caller e retornar antes da lease.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          NOT LIKE N'%@existing_page_audit_id IS NOT NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          NOT LIKE N'%THROW 51334%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          LIKE N'%@existing_read_at_utc%'
   OR CHARINDEX(
          N'FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
      )
   OR CHARINDEX(
          N'FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'IDEMPOTENCY', N'ctl.usp_control_plane_record_counts',
       N'Retry exato de contagem deve travar attempt→count, ignorar clock do caller e retornar antes da lease.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
          NOT LIKE N'%@existing_count_id IS NOT NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
          NOT LIKE N'%THROW 51335%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
          LIKE N'%@existing_recorded_at_utc%'
   OR CHARINDEX(
          N'FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
      )
   OR CHARINDEX(
          N'FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'COUNT_NAMESPACE', N'ctl.usp_control_plane_record_counts',
       N'STAGING_KERNEL deve ser reservado por comparação BIN2 exata na API runtime.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
          NOT LIKE N'%@count_phase COLLATE Latin1_General_100_BIN2 = N''STAGING_KERNEL''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'IDEMPOTENCY', N'ctl.usp_control_plane_transition_execution',
       N'Retry exato de transição deve travar attempt→evento e retornar antes da lease; conflito deve falhar.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@event_sequence_to_lock%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@next_transition_sequence - 1%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@existing_event_previous_state COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%O slot imutável da próxima transição já está ocupado.%'
   OR CHARINDEX(
          N'FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', N'ctl.runtime_inputs',
       N'Tokens, estados e regex de runtime devem usar BIN2 explícito, sem depender da collation do banco.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%@execution_mode COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_start_execution', N'P'))
          NOT LIKE N'%@contract_fingerprint COLLATE Latin1_General_100_BIN2 LIKE%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@current_state COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@reason_code COLLATE Latin1_General_100_BIN2 LIKE%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'REASON_GRAMMAR', N'ctl.execution_state_event.reason_code',
       N'Reason code deve começar com [A-Z], continuar em [A-Z0-9_] e ter 2..64 caracteres.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'ctl.execution_state_event', N'U')
      AND name = N'CK_ctl_execution_state_event_reason_code'
      AND LOWER(definition) LIKE N'%left%'
      AND definition LIKE N'%Latin1_General_100_BIN2%'
      AND CHARINDEX(N'[A-Z]', definition) > 0
)
OR CHARINDEX(
       N'LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE ''[A-Z]''',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
   ) = 0;

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', expected.object_name,
       N'O relógio técnico precisa ser capturado depois de adquirir os locks da lease.'
FROM @fenced_event_procedures AS expected
WHERE CHARINDEX(
          N'@lease_expires_at_utc = lease.expires_at_utc',
          OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
      ) >= CHARINDEX(
          N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
          OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'ctl.runtime_events',
       N'Page, counts e transição devem persistir timestamps técnicos do SQL Server após o fencing.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          NOT LIKE N'%@terminal_empty_page, @terminal_evidence_kind,%@database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P'))
          NOT LIKE N'%@unidentified_quarantine_rows, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%THEN @database_now_utc ELSE NULL END%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@reason_code, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%released_at_utc = @database_now_utc%';

IF CHARINDEX(
       N'@lease_expires_at_utc = lease.expires_at_utc',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
   ) >= CHARINDEX(
       N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
       OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
   )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CLOCK_AUTHORITY', N'ctl.usp_control_plane_transition_execution',
        N'O relógio da transição precisa ser capturado depois de adquirir os locks da lease.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', N'ctl.usp_control_plane_record_page',
       N'A assinatura de página deve persistir evidência terminal tipada.'
WHERE (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P')) <> 10
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P')
         AND parameter_id = 8 AND name = N'@terminal_empty_page'
   )
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P')
         AND parameter_id = 10 AND name = N'@terminal_evidence_kind'
   )
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          NOT LIKE N'%N''NONE'', N''DATA_EXPORT_EMPTY_PAGE'', N''GRAPHQL_PAGE_INFO''%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_record_page', N'P'))
          NOT LIKE N'%@existing_terminal_evidence_kind COLLATE Latin1_General_100_BIN2%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', N'ctl.usp_control_plane_record_counts',
       N'A assinatura de contagens deve separar raízes em quarantine de linhas sem chave.'
WHERE (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P')) <> 9
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P')
         AND parameter_id = 7 AND name = N'@quarantined_root_keys'
   )
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_record_counts', N'P')
         AND parameter_id = 9 AND name = N'@unidentified_quarantine_rows'
   );

INSERT INTO @failures (category, object_name, detail)
SELECT N'RECOVERY', N'ctl.usp_control_plane_recover_stale_executions',
       N'Recovery deve aceitar zero parâmetros, usar relógio SQL e retornar uma contagem O(1).'
WHERE (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P')) <> 0
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P'))
          NOT LIKE N'%DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P'))
          NOT LIKE N'%lease.expires_at_utc <= @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_recover_stale_executions', N'P'))
          NOT LIKE N'%SELECT @recovered_executions AS recovered_executions%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PUBLICATION_EVIDENCE', N'ctl.execution_publication_event',
       N'O evento imutável de publicação ou seus vínculos partição/execução estão ausentes.'
WHERE NOT EXISTS (
        SELECT 1 FROM sys.objects
        WHERE parent_object_id = OBJECT_ID(N'ctl.execution_publication_event', N'U')
          AND name = N'PK_ctl_execution_publication_event' AND type = N'PK'
    )
   OR NOT EXISTS (
        SELECT 1 FROM sys.objects
        WHERE parent_object_id = OBJECT_ID(N'ctl.execution_publication_event', N'U')
          AND name = N'FK_ctl_execution_publication_event_partition_execution' AND type = N'F'
    )
   OR NOT EXISTS (
        SELECT 1 FROM sys.objects
        WHERE parent_object_id = OBJECT_ID(N'ctl.partition_publication_pointer', N'U')
          AND name = N'FK_ctl_partition_publication_pointer_partition_execution' AND type = N'F'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'UX_ctl_execution_lease_active_partition',
       N'A lease ativa única por partição está ausente.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'ctl.execution_lease', N'U')
      AND name = N'UX_ctl_execution_lease_active_partition'
      AND is_unique = 1
      AND has_filter = 1
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', N'v2_runtime:ctl',
       N'Runtime precisa de deny explícito para DML e SELECT direto em ctl.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 3
      AND permission_definition.major_id = SCHEMA_ID(N'ctl')
      AND permission_definition.state = 'D'
      AND permission_definition.permission_name = N'INSERT'
)
OR NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 3
      AND permission_definition.major_id = SCHEMA_ID(N'ctl')
      AND permission_definition.state = 'D'
      AND permission_definition.permission_name = N'UPDATE'
)
OR NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 3
      AND permission_definition.major_id = SCHEMA_ID(N'ctl')
      AND permission_definition.state = 'D'
      AND permission_definition.permission_name = N'DELETE'
)
OR NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 3
      AND permission_definition.major_id = SCHEMA_ID(N'ctl')
      AND permission_definition.state = 'D'
      AND permission_definition.permission_name = N'SELECT'
);

DECLARE @expected_runtime_procedures TABLE (object_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_runtime_procedures (object_name)
VALUES
    (N'usp_control_plane_register_source'),
    (N'usp_control_plane_start_cycle'),
    (N'usp_control_plane_start_execution'),
    (N'usp_control_plane_heartbeat_lease'),
    (N'usp_control_plane_record_page'),
    (N'usp_control_plane_record_counts'),
    (N'usp_control_plane_transition_execution'),
    (N'usp_control_plane_recover_stale_executions');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', expected.object_name,
       N'Runtime não possui EXECUTE na procedure permitida.'
FROM @expected_runtime_procedures AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 1
      AND permission_definition.major_id = OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P')
      AND permission_definition.state = 'G'
      AND permission_definition.permission_name = N'EXECUTE'
);

DECLARE @owner_only_procedures TABLE (object_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @owner_only_procedures (object_name)
VALUES
    (N'usp_control_plane_register_incremental_frontier'),
    (N'usp_control_plane_publish_execution');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', expected.object_name,
       N'Runtime possui EXECUTE em procedure administrativa ou de publicação.'
FROM @owner_only_procedures AS expected
WHERE EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 1
      AND permission_definition.major_id = OBJECT_ID(CONCAT(N'ctl.', expected.object_name), N'P')
      AND permission_definition.state IN ('G', 'W')
      AND permission_definition.permission_name = N'EXECUTE'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.usp_control_plane_transition_execution',
       N'A transição genérica ainda permite promoção ou reconciliação sem evidência.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%@next_state COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          NOT LIKE N'%IN (N''PROMOTED'', N''RECONCILED'', N''PUBLISHED'')%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          LIKE N'%@current_state = N''STAGED'' AND @next_state IN (N''PROMOTED''%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
          LIKE N'%@current_state = N''PROMOTED'' AND @next_state IN (N''RECONCILED''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.usp_control_plane_publish_execution',
       N'A publicação deve permanecer fail-closed até existir reconciliação e commit atômico.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_publish_execution', N'P'))
          NOT LIKE N'%Publicação indisponível sem evidência de reconciliação e commit atômico%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'LEAKAGE', CONCAT(schema_definition.name, N'.', table_definition.name, N'.', column_definition.name),
       N'Control plane não pode persistir payload, URL ou token.'
FROM sys.tables AS table_definition
INNER JOIN sys.schemas AS schema_definition ON schema_definition.schema_id = table_definition.schema_id
INNER JOIN sys.columns AS column_definition ON column_definition.object_id = table_definition.object_id
WHERE schema_definition.name = N'ctl'
  AND (
      LOWER(column_definition.name) LIKE N'%payload%'
      OR LOWER(column_definition.name) LIKE N'%token%'
      OR LOWER(column_definition.name) LIKE N'%url%'
      OR LOWER(column_definition.name) LIKE N'%header%'
  );

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 51329, N'Control plane V2 divergente do contrato.', 1;
END;

PRINT N'Control plane V2 validado com sucesso.';

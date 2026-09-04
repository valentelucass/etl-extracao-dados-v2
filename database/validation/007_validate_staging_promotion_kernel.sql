-- Validador somente leitura de V2-021. Requer a fundação e o control plane já existentes.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_tables TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, table_name)
);

INSERT INTO @expected_tables (schema_name, table_name)
VALUES
    (N'ctl', N'execution_promotion_result'),
    (N'stg', N'execution_record'),
    (N'stg', N'execution_candidate'),
    (N'core', N'entity_record_state'),
    (N'recon', N'quarantine_record'),
    (N'recon', N'execution_candidate_application'),
    (N'recon', N'execution_reconciliation_result');

INSERT INTO @failures (category, object_name, detail)
SELECT N'TABLE', CONCAT(expected.schema_name, N'.', expected.table_name),
       N'Tabela obrigatória do kernel ausente.'
FROM @expected_tables AS expected
WHERE OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name), N'U') IS NULL;

DECLARE @expected_procedures TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    procedure_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, procedure_name)
);

INSERT INTO @expected_procedures (schema_name, procedure_name)
VALUES
    (N'stg', N'usp_stage_record'),
    (N'core', N'usp_prepare_staged_execution'),
    (N'core', N'usp_apply_reconcile_publish_execution');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', CONCAT(expected.schema_name, N'.', expected.procedure_name),
       N'Procedure obrigatória do kernel ausente.'
FROM @expected_procedures AS expected
WHERE OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P') IS NULL;

DECLARE @expected_constraints TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    constraint_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, table_name, constraint_name)
);

INSERT INTO @expected_constraints (schema_name, table_name, constraint_name, object_type)
VALUES
    (N'stg', N'execution_record', N'PK_stg_execution_record', N'PK'),
    (N'stg', N'execution_record', N'UQ_stg_execution_record_input', N'UQ'),
    (N'stg', N'execution_record', N'FK_stg_execution_record_execution', N'F'),
    (N'stg', N'execution_record', N'CK_stg_execution_record_batch_limits', N'C'),
    (N'stg', N'execution_record', N'CK_stg_execution_record_disposition', N'C'),
    (N'stg', N'execution_record', N'CK_stg_execution_record_fingerprints', N'C'),
    (N'stg', N'execution_record', N'CK_stg_execution_record_validity', N'C'),
    (N'stg', N'execution_candidate', N'PK_stg_execution_candidate', N'PK'),
    (N'stg', N'execution_candidate', N'UQ_stg_execution_candidate_winner', N'UQ'),
    (N'stg', N'execution_candidate', N'FK_stg_execution_candidate_execution', N'F'),
    (N'stg', N'execution_candidate', N'FK_stg_execution_candidate_winner', N'F'),
    (N'stg', N'execution_candidate', N'CK_stg_execution_candidate_non_blank', N'C'),
    (N'stg', N'execution_candidate', N'CK_stg_execution_candidate_fingerprints', N'C'),
    (N'recon', N'quarantine_record', N'PK_recon_quarantine_record', N'PK'),
    (N'recon', N'quarantine_record', N'UQ_recon_quarantine_record_stage', N'UQ'),
    (N'recon', N'quarantine_record', N'FK_recon_quarantine_record_execution', N'F'),
    (N'recon', N'quarantine_record', N'CK_recon_quarantine_record_reason_code', N'C'),
    (N'recon', N'quarantine_record', N'CK_recon_quarantine_record_source_key', N'C'),
    (N'recon', N'quarantine_record', N'CK_recon_quarantine_record_fingerprints', N'C'),
    (N'core', N'entity_record_state', N'PK_core_entity_record_state', N'PK'),
    (N'core', N'entity_record_state', N'UQ_core_entity_record_state_source', N'UQ'),
    (N'core', N'entity_record_state', N'FK_core_entity_record_state_source', N'F'),
    (N'core', N'entity_record_state', N'FK_core_entity_record_state_first_execution', N'F'),
    (N'core', N'entity_record_state', N'FK_core_entity_record_state_last_execution', N'F'),
    (N'core', N'entity_record_state', N'CK_core_entity_record_state_non_blank', N'C'),
    (N'core', N'entity_record_state', N'CK_core_entity_record_state_fingerprints', N'C'),
    (N'core', N'entity_record_state', N'CK_core_entity_record_state_promoted_at', N'C'),
    (N'ctl', N'execution_promotion_result', N'PK_ctl_execution_promotion_result', N'PK'),
    (N'ctl', N'execution_promotion_result', N'FK_ctl_execution_promotion_result_execution', N'F'),
    (N'ctl', N'execution_promotion_result', N'CK_ctl_execution_promotion_result_counts', N'C'),
    (N'recon', N'execution_candidate_application',
        N'PK_recon_execution_candidate_application', N'PK'),
    (N'recon', N'execution_candidate_application',
        N'UQ_recon_execution_candidate_application_record', N'UQ'),
    (N'recon', N'execution_candidate_application',
        N'FK_recon_execution_candidate_application_record', N'F'),
    (N'recon', N'execution_candidate_application',
        N'CK_recon_execution_candidate_application_disposition', N'C'),
    (N'recon', N'execution_candidate_application',
        N'CK_recon_execution_candidate_application_fingerprints', N'C'),
    (N'recon', N'execution_reconciliation_result',
        N'PK_recon_execution_reconciliation_result', N'PK'),
    (N'recon', N'execution_reconciliation_result',
        N'FK_recon_execution_reconciliation_result_execution', N'F'),
    (N'recon', N'execution_reconciliation_result',
        N'FK_recon_execution_reconciliation_result_candidate_set', N'F'),
    (N'recon', N'execution_reconciliation_result',
        N'CK_recon_execution_reconciliation_result_counts', N'C'),
    (N'recon', N'execution_reconciliation_result',
        N'CK_recon_execution_reconciliation_result_times', N'C');

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.constraint_name),
       N'Constraint obrigatória ausente ou divergente.'
FROM @expected_constraints AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS object_definition
    INNER JOIN sys.schemas AS schema_definition
        ON schema_definition.schema_id = object_definition.schema_id
    WHERE schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
      AND OBJECT_NAME(object_definition.parent_object_id) COLLATE DATABASE_DEFAULT = expected.table_name
      AND object_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
      AND object_definition.type COLLATE DATABASE_DEFAULT = expected.object_type
);

DECLARE @expected_fingerprint_columns TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT INTO @expected_fingerprint_columns (schema_name, table_name, column_name)
VALUES
    (N'stg', N'execution_record', N'row_fingerprint_version'),
    (N'stg', N'execution_record', N'presence_fingerprint_version'),
    (N'stg', N'execution_candidate', N'row_fingerprint_version'),
    (N'stg', N'execution_candidate', N'presence_fingerprint_version'),
    (N'recon', N'quarantine_record', N'row_fingerprint_version'),
    (N'recon', N'quarantine_record', N'presence_fingerprint_version'),
    (N'recon', N'execution_candidate_application', N'result_row_fingerprint_version'),
    (N'recon', N'execution_candidate_application', N'result_presence_fingerprint_version'),
    (N'core', N'entity_record_state', N'row_fingerprint_version'),
    (N'core', N'entity_record_state', N'presence_fingerprint_version');

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.column_name),
       N'A versão do fingerprint não é persistida.'
FROM @expected_fingerprint_columns AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name), N'U')
      AND name COLLATE DATABASE_DEFAULT = expected.column_name
);

DECLARE @expected_binary_columns TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);
INSERT INTO @expected_binary_columns (schema_name, table_name, column_name)
VALUES
    (N'stg', N'execution_record', N'source_key'),
    (N'stg', N'execution_candidate', N'source_key'),
    (N'recon', N'quarantine_record', N'source_key'),
    (N'recon', N'execution_candidate_application', N'source_key'),
    (N'recon', N'execution_candidate_application', N'application_disposition'),
    (N'core', N'entity_record_state', N'environment_name'),
    (N'core', N'entity_record_state', N'source_instance'),
    (N'core', N'entity_record_state', N'tenant_scope'),
    (N'core', N'entity_record_state', N'entity_name'),
    (N'core', N'entity_record_state', N'source_key');

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.column_name),
       N'Boundary textual precisa de comparação binária, case-sensitive e accent-sensitive.'
FROM @expected_binary_columns AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name), N'U')
      AND name = expected.column_name
      AND collation_name = N'Latin1_General_100_BIN2'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(schema_definition.name, N'.', table_definition.name, N'.', column_definition.name),
       N'Toda coluna NVARCHAR persistida do kernel deve usar BIN2 explícita.'
FROM sys.tables AS table_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = table_definition.schema_id
INNER JOIN sys.columns AS column_definition
    ON column_definition.object_id = table_definition.object_id
INNER JOIN sys.types AS type_definition
    ON type_definition.user_type_id = column_definition.user_type_id
INNER JOIN @expected_tables AS expected
    ON expected.schema_name = schema_definition.name COLLATE DATABASE_DEFAULT
   AND expected.table_name = table_definition.name COLLATE DATABASE_DEFAULT
WHERE type_definition.name = N'nvarchar'
  AND column_definition.collation_name <> N'Latin1_General_100_BIN2';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', N'stg.usp_stage_record',
       N'A assinatura de staging deve ter 12 parâmetros com as versões nas posições canônicas.'
WHERE (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'stg.usp_stage_record', N'P')) <> 12
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'stg.usp_stage_record', N'P')
         AND parameter_id = 5 AND name = N'@row_fingerprint_version'
   )
   OR NOT EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'stg.usp_stage_record', N'P')
         AND parameter_id = 7 AND name = N'@presence_fingerprint_version'
   );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', N'core.publication_protocol',
       N'Preparação e publicação devem receber execution_id e o permit de contrato/configuração.'
WHERE (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'core.usp_prepare_staged_execution', N'P')) <> 5
   OR (SELECT COUNT(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P')) <> 5;

DECLARE @expected_contract_permit_parameters TABLE (
    parameter_id INT NOT NULL PRIMARY KEY,
    parameter_name SYSNAME NOT NULL,
    type_name SYSNAME NOT NULL,
    max_length SMALLINT NOT NULL
);
INSERT INTO @expected_contract_permit_parameters (
    parameter_id, parameter_name, type_name, max_length
)
VALUES
    (1, N'@execution_id', N'uniqueidentifier', 16),
    (2, N'@contract_version', N'nvarchar', -1),
    (3, N'@contract_fingerprint', N'nvarchar', -1),
    (4, N'@configuration_version', N'nvarchar', -1),
    (5, N'@configuration_fingerprint', N'nvarchar', -1);

DECLARE @contract_permit_procedures TABLE (procedure_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @contract_permit_procedures (procedure_name)
VALUES
    (N'core.usp_prepare_staged_execution'),
    (N'core.usp_apply_reconcile_publish_execution');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', CONCAT(procedure_name, N'.', expected.parameter_name),
       N'Nome, ordem, tipo ou largura do permit de contrato/configuração diverge.'
FROM @contract_permit_procedures
CROSS JOIN @expected_contract_permit_parameters AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.parameters AS parameter_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = parameter_definition.user_type_id
    WHERE parameter_definition.object_id = OBJECT_ID(procedure_name, N'P')
      AND parameter_definition.parameter_id = expected.parameter_id
      AND parameter_definition.name = expected.parameter_name
      AND type_definition.name = expected.type_name
      AND parameter_definition.max_length = expected.max_length
      AND parameter_definition.is_output = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'NORMALIZATION', procedure_name,
       N'O permit deve validar largura antes de trim/lower e comparar quatro campos sob BIN2.'
FROM @contract_permit_procedures
WHERE OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%DATALENGTH(@contract_version)%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%DATALENGTH(@configuration_fingerprint)%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%SET @contract_version = LTRIM(RTRIM(@contract_version))%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%SET @contract_fingerprint = LOWER(@contract_fingerprint)%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%@persisted_contract_version COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%@persisted_contract_fingerprint COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%@persisted_configuration_version COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%@persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%IF @persisted_contract_version IS NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%OR @persisted_configuration_fingerprint IS NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P')) NOT LIKE N'%THROW 51418%'
   OR CHARINDEX(
          N'DATALENGTH(@contract_version)',
          OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P'))
      ) >= CHARINDEX(
          N'SET @contract_version = LTRIM(RTRIM(@contract_version))',
          OBJECT_DEFINITION(OBJECT_ID(procedure_name, N'P'))
      );

DECLARE @prepare_permit_definition NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'));
DECLARE @apply_permit_definition NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P'));

IF @prepare_permit_definition IS NULL
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @prepare_permit_definition
      ) = 0
   OR CHARINDEX(N'IF @persisted_contract_version IS NULL', @prepare_permit_definition) = 0
   OR CHARINDEX(N'THROW 51418', @prepare_permit_definition) = 0
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @prepare_permit_definition
      ) >= CHARINDEX(N'IF @persisted_contract_version IS NULL', @prepare_permit_definition)
   OR CHARINDEX(N'IF @persisted_contract_version IS NULL', @prepare_permit_definition)
      >= CHARINDEX(N'THROW 51418', @prepare_permit_definition)
   OR CHARINDEX(N'THROW 51418', @prepare_permit_definition)
      >= CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PROMOTED''',
          @prepare_permit_definition
      )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONTRACT_PERMIT', N'core.usp_prepare_staged_execution',
        N'O permit deve ser lido sob lock e rejeitado antes do retry PROMOTED ou de mutação.'
    );

IF @apply_permit_definition IS NULL
   OR CHARINDEX(N'EXEC @application_lock_result = sys.sp_getapplock', @apply_permit_definition) = 0
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @apply_permit_definition
      ) = 0
   OR CHARINDEX(N'IF @persisted_contract_version IS NULL', @apply_permit_definition) = 0
   OR CHARINDEX(N'THROW 51418', @apply_permit_definition) = 0
   OR CHARINDEX(N'EXEC @application_lock_result = sys.sp_getapplock', @apply_permit_definition)
      >= CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @apply_permit_definition
      )
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          @apply_permit_definition
      ) >= CHARINDEX(N'IF @persisted_contract_version IS NULL', @apply_permit_definition)
   OR CHARINDEX(N'IF @persisted_contract_version IS NULL', @apply_permit_definition)
      >= CHARINDEX(N'THROW 51418', @apply_permit_definition)
   OR CHARINDEX(N'THROW 51418', @apply_permit_definition)
      >= CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PUBLISHED''',
          @apply_permit_definition
      )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CONTRACT_PERMIT', N'core.usp_apply_reconcile_publish_execution',
        N'O permit deve ser lido após applock, sob row lock, antes do retry PUBLISHED ou de mutação.'
    );

DECLARE @expected_stage_text_parameters TABLE (parameter_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_stage_text_parameters (parameter_name)
VALUES
    (N'@source_key'),
    (N'@row_fingerprint_version'),
    (N'@source_row_hash'),
    (N'@presence_fingerprint_version'),
    (N'@presence_fingerprint'),
    (N'@validation_disposition'),
    (N'@quarantine_reason_code');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', CONCAT(N'stg.usp_stage_record.', expected.parameter_name),
       N'Entrada textual precisa ser MAX para impedir truncamento antes do corpo da procedure.'
FROM @expected_stage_text_parameters AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.parameters AS parameter_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = parameter_definition.user_type_id
    WHERE parameter_definition.object_id = OBJECT_ID(N'stg.usp_stage_record', N'P')
      AND parameter_definition.name = expected.parameter_name
      AND parameter_definition.max_length = -1
      AND type_definition.name = N'nvarchar'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'NORMALIZATION', CONCAT(N'stg.usp_stage_record.', expected.parameter_name),
       N'O limite precisa ser validado com DATALENGTH antes de normalizar a entrada.'
FROM @expected_stage_text_parameters AS expected
WHERE OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P')) NOT LIKE
      N'%DATALENGTH(' + expected.parameter_name + N')%';

IF CHARINDEX(
       N'IF DATALENGTH(@source_key)',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   ) >= CHARINDEX(
       N'SET @source_key = NULLIF(LTRIM',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'NORMALIZATION', N'stg.usp_stage_record',
        N'DATALENGTH precisa executar antes de qualquer trim/lower da entrada textual.'
    );

DECLARE @expected_promotion_count_columns TABLE (column_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @expected_promotion_count_columns (column_name)
VALUES
    (N'quarantined_root_keys'),
    (N'unidentified_quarantine_rows'),
    (N'quarantined_stage_rows');

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', CONCAT(N'ctl.execution_promotion_result.', expected.column_name),
       N'O resultado da promoção não separa grão raiz de artefato físico de quarantine.'
FROM @expected_promotion_count_columns AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'ctl.execution_promotion_result', N'U')
      AND name = expected.column_name
      AND is_nullable = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'CK_ctl_execution_promotion_result_counts',
       N'As equações do resultado devem separar raízes identificadas, duplicatas e quarantine sem chave.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'ctl.execution_promotion_result', N'U')
      AND name = N'CK_ctl_execution_promotion_result_counts'
      AND definition LIKE N'%physical_rows%=%distinct_root_keys%+%duplicate_rows%+%unidentified_quarantine_rows%'
      AND definition LIKE N'%distinct_root_keys%=%candidate_rows%+%quarantined_root_keys%'
      AND definition LIKE N'%quarantined_stage_rows%>=%quarantined_root_keys%+%unidentified_quarantine_rows%'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'core.usp_prepare_staged_execution',
       N'A promoção não está isolada em candidate set ou ainda altera core antes da publicação.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%INSERT INTO stg.execution_candidate%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%CANDIDATE_SET_PREPARED%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@lease_expires_at_utc <= @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%OUTPUT deleted.next_transition_sequence%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%MAX(transition_sequence)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%UPDATE core.entity_record_state%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%INSERT INTO core.entity_record_state%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%partition_publication_pointer%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%incremental_publication_watermark%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.usp_stage_record',
       N'O staging não aplica teto SQL de 10.000 ou fencing de lease/execução corrente.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 10000%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%partition.current_execution_id = @execution_id%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@lease_expires_at_utc <= @database_now_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'IDEMPOTENCY', N'stg.usp_stage_record',
       N'Retry exato deve travar attempt→stage row e retornar antes da lease; divergência deve falhar.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%THROW 51403%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          LIKE N'%persisted.staged_at_utc <> @staged_at_utc%'
   OR CHARINDEX(
          N'FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
      ) >= CHARINDEX(
          N'FROM stg.execution_record AS persisted WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
      )
   OR CHARINDEX(
          N'FROM stg.execution_record AS persisted WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'IDEMPOTENCY', N'core.usp_prepare_staged_execution',
       N'Retry PROMOTED exige permit e evidência completa antes da lease; só STAGED inicia mutação.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@execution_state COLLATE Latin1_General_100_BIN2 = N''PROMOTED''%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%Execução promovida sem candidate set e auditoria coerentes.%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%count_audit.count_phase = N''STAGING_KERNEL''%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%promotion_event.transition_sequence = @next_transition_sequence - 1%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%promotion_partition.current_execution_id = @execution_id%'
   OR CHARINDEX(
          N'THROW 51418',
          OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
      ) >= CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PROMOTED''',
          OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
      )
   OR CHARINDEX(
          N'IF @execution_state COLLATE Latin1_General_100_BIN2 = N''PROMOTED''',
          OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
      ) >= CHARINDEX(
          N'FROM ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)',
          OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'COUNT_NAMESPACE', N'core.usp_prepare_staged_execution',
       N'STAGING_KERNEL deve ser persistido internamente, sem invocar a API pública reservada.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%INSERT INTO ctl.execution_count%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%EXEC ctl.usp_control_plane_record_counts%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', N'stg.runtime_inputs',
       N'Disposition, reason e hashes devem ser validados sob BIN2 explícito.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@validation_disposition COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@execution_state COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@quarantine_reason_code COLLATE Latin1_General_100_BIN2%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@source_row_hash COLLATE Latin1_General_100_BIN2 LIKE%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'REASON_GRAMMAR', N'stg.execution_record.quarantine_reason_code',
       N'Motivo de quarantine deve começar com [A-Z], continuar em [A-Z0-9_] e ter 2..64 caracteres.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'stg.execution_record', N'U')
      AND name = N'CK_stg_execution_record_validity'
      AND LOWER(definition) LIKE N'%left%quarantine_reason_code%'
      AND definition LIKE N'%Latin1_General_100_BIN2%'
      AND CHARINDEX(N'[A-Z]', definition) > 0
)
OR CHARINDEX(
       N'LEFT(@quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   ) = 0;

INSERT INTO @failures (category, object_name, detail)
SELECT N'REASON_GRAMMAR', N'recon.quarantine_record.reason_code',
       N'O ledger de quarantine deve impor a mesma gramática canônica de reason code.'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'recon.quarantine_record', N'U')
      AND name = N'CK_recon_quarantine_record_reason_code'
      AND LOWER(definition) LIKE N'%left%reason_code%'
      AND definition LIKE N'%Latin1_General_100_BIN2%'
      AND CHARINDEX(N'[A-Z]', definition) > 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'stg.usp_stage_record',
       N'O timestamp técnico de staging deve vir do SQL Server e não integrar a identidade do retry.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          NOT LIKE N'%@quarantine_reason_code, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
          LIKE N'%persisted.staged_at_utc <> @staged_at_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'CLOCK_AUTHORITY', N'core.usp_prepare_staged_execution',
       N'Candidate, quarantine, resultado e evento de promoção devem compartilhar o relógio do banco.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%winner.source_freshness_at_utc, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@quarantined_stage_rows, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%N''CANDIDATE_SET_PREPARED'', @database_now_utc%';

IF CHARINDEX(
       N'@lease_expires_at_utc = lease.expires_at_utc',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   ) >= CHARINDEX(
       N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CLOCK_AUTHORITY', N'stg.usp_stage_record',
        N'O relógio de staging precisa ser capturado depois de adquirir os locks da lease.'
    );

IF CHARINDEX(
       N'@lease_expires_at_utc = lease.expires_at_utc',
       OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
   ) >= CHARINDEX(
       N'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME()',
       OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
   )
    INSERT INTO @failures (category, object_name, detail)
    VALUES (
        N'CLOCK_AUTHORITY', N'core.usp_prepare_staged_execution',
        N'O relógio de promoção precisa ser capturado depois de adquirir os locks da lease.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'core.usp_prepare_staged_execution',
       N'Conflitos de frescor igual/desconhecido não estão fail-closed em quarantine.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%EQUAL_FRESHNESS_CONFLICT%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%UNKNOWN_FRESHNESS_CONFLICT%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COUNT_GRAIN', N'core.usp_prepare_staged_execution',
       N'Quarantine sem chave não pode virar distinct_root_key nem deixar de ser materializado.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@unidentified_quarantine_rows = COUNT_BIG(*)%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%source_key IS NULL%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@duplicate_rows = @physical_rows - @distinct_rows - @unidentified_quarantine_rows%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%INSERT INTO ctl.execution_count%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%@candidate_rows, @quarantined_root_keys, @unidentified_quarantine_rows, @database_now_utc%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          LIKE N'%distinct_root_keys não pode ser inferido quando quarantine não possui source_key%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', N'core.usp_prepare_staged_execution',
       N'Table variables de dedupe/quarantine precisam preservar a collation binária da source_key.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
          NOT LIKE N'%source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,%';

DECLARE @expected_indexes TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    table_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    index_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, table_name, index_name)
);

INSERT INTO @expected_indexes (schema_name, table_name, index_name)
VALUES
    (N'stg', N'execution_record', N'IX_stg_execution_record_dedupe'),
    (N'recon', N'quarantine_record', N'IX_recon_quarantine_record_execution'),
    (N'recon', N'execution_candidate_application',
        N'IX_recon_execution_candidate_application_record'),
    (N'core', N'entity_record_state', N'IX_core_entity_record_state_active');

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.index_name),
       N'Índice obrigatório do kernel ausente.'
FROM @expected_indexes AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name), N'U')
      AND index_definition.name COLLATE DATABASE_DEFAULT = expected.index_name
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'LEAKAGE', CONCAT(schema_definition.name, N'.', table_definition.name, N'.', column_definition.name),
       N'Kernel não pode persistir payload, token, URL ou header.'
FROM sys.tables AS table_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = table_definition.schema_id
INNER JOIN sys.columns AS column_definition
    ON column_definition.object_id = table_definition.object_id
INNER JOIN @expected_tables AS expected
    ON expected.schema_name = schema_definition.name COLLATE DATABASE_DEFAULT
   AND expected.table_name = table_definition.name COLLATE DATABASE_DEFAULT
WHERE LOWER(column_definition.name) LIKE N'%payload%'
   OR LOWER(column_definition.name) LIKE N'%token%'
   OR LOWER(column_definition.name) LIKE N'%url%'
   OR LOWER(column_definition.name) LIKE N'%header%';

DECLARE @expected_permissions TABLE (
    permission_class TINYINT NOT NULL,
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    permission_state CHAR(1) COLLATE DATABASE_DEFAULT NOT NULL,
    permission_name NVARCHAR(128) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (permission_class, schema_name, object_name, permission_state, permission_name)
);

INSERT INTO @expected_permissions (
    permission_class, schema_name, object_name, permission_state, permission_name
)
VALUES
    (3, N'stg', N'', N'D', N'SELECT'),
    (3, N'stg', N'', N'D', N'INSERT'),
    (3, N'stg', N'', N'D', N'UPDATE'),
    (3, N'stg', N'', N'D', N'DELETE'),
    (3, N'core', N'', N'D', N'SELECT'),
    (3, N'core', N'', N'D', N'INSERT'),
    (3, N'core', N'', N'D', N'UPDATE'),
    (3, N'core', N'', N'D', N'DELETE'),
    (3, N'recon', N'', N'D', N'SELECT'),
    (3, N'recon', N'', N'D', N'INSERT'),
    (3, N'recon', N'', N'D', N'UPDATE'),
    (3, N'recon', N'', N'D', N'DELETE'),
    (1, N'stg', N'usp_stage_record', N'G', N'EXECUTE'),
    (1, N'core', N'usp_prepare_staged_execution', N'G', N'EXECUTE'),
    (1, N'core', N'usp_apply_reconcile_publish_execution', N'G', N'EXECUTE');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(expected.schema_name, N'.', expected.object_name, N':', expected.permission_name),
       N'Permissão mínima do runtime ausente.'
FROM @expected_permissions AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name COLLATE DATABASE_DEFAULT = N'v2_runtime'
      AND permission_definition.class = expected.permission_class
      AND permission_definition.state COLLATE DATABASE_DEFAULT = expected.permission_state
      AND permission_definition.permission_name COLLATE DATABASE_DEFAULT = expected.permission_name
      AND (
          (expected.permission_class = 3 AND permission_definition.major_id = SCHEMA_ID(expected.schema_name))
          OR (
              expected.permission_class = 1
              AND permission_definition.major_id = OBJECT_ID(
                  CONCAT(expected.schema_name, N'.', expected.object_name), N'P'
              )
          )
      )
);

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 51430, N'Kernel de staging e promoção V2 divergente do contrato.', 1;
END;

PRINT N'Kernel de staging e promoção V2 validado com sucesso.';

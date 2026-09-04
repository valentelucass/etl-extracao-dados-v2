-- Validação estrutural, sanitizada e somente leitura da vertical V2-033.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51770, N'O validator de Usuários aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @failures TABLE (
    category NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_name NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    detail NVARCHAR(512) NOT NULL
);

DECLARE @expected_objects TABLE (
    schema_name SYSNAME NOT NULL,
    object_name SYSNAME NOT NULL,
    object_type CHAR(2) NOT NULL,
    PRIMARY KEY (schema_name, object_name)
);

INSERT INTO @expected_objects (schema_name, object_name, object_type) VALUES
    (N'stg', N'usuario_record', 'U'),
    (N'recon', N'usuario_quarantine', 'U'),
    (N'ctl', N'usuario_promotion_result', 'U'),
    (N'core', N'usuario', 'U'),
    (N'core', N'usuario_history', 'U'),
    (N'recon', N'usuario_apply_authorization', 'U'),
    (N'recon', N'usuario_candidate_application', 'U'),
    (N'recon', N'usuario_reconciliation_result', 'U'),
    (N'recon', N'usuario_stage_disposal_evidence', 'U'),
    (N'recon', N'usuario_stage_archive', 'U'),
    (N'recon', N'usuario_stage_restore', 'U'),
    (N'recon', N'ufn_staging_lifecycle_extension_archive_budget', 'IF'),
    (N'stg', N'usp_stage_usuario_record', 'P'),
    (N'core', N'usp_apply_reconcile_publish_usuarios', 'P'),
    (N'ctl', N'trg_usuario_prepare_candidate_set', 'TR'),
    (N'ctl', N'trg_usuario_publication_requires_apply_wrapper', 'TR'),
    (N'ctl', N'trg_usuario_lifecycle_plan_budget', 'TR'),
    (N'recon', N'trg_usuario_data_quality_requires_typed_pass', 'TR'),
    (N'recon', N'trg_usuario_stage_archive_from_generic', 'TR'),
    (N'recon', N'trg_usuario_stage_restore_from_generic', 'TR'),
    (N'stg', N'trg_execution_record_delete_usuario_stage', 'TR'),
    (N'recon', N'trg_usuario_apply_authorization_immutable', 'TR'),
    (N'core', N'trg_usuario_history_immutable', 'TR'),
    (N'recon', N'trg_usuario_application_immutable', 'TR'),
    (N'recon', N'trg_usuario_result_immutable', 'TR'),
    (N'recon', N'trg_usuario_quarantine_immutable', 'TR'),
    (N'ctl', N'trg_usuario_promotion_result_immutable', 'TR'),
    (N'recon', N'trg_usuario_stage_archive_immutable', 'TR'),
    (N'recon', N'trg_usuario_stage_restore_immutable', 'TR'),
    (N'recon', N'trg_usuario_stage_disposal_immutable', 'TR');

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', CONCAT(expected.schema_name, N'.', expected.object_name),
       N'Objeto obrigatório ausente ou com tipo divergente.'
FROM @expected_objects AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS actual
    INNER JOIN sys.schemas AS schema_definition
        ON schema_definition.schema_id = actual.schema_id
    WHERE schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
      AND actual.name COLLATE DATABASE_DEFAULT = expected.object_name
      AND actual.type COLLATE DATABASE_DEFAULT = expected.object_type
);

DECLARE @expected_columns TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL,
    type_name SYSNAME NOT NULL,
    max_length SMALLINT NULL,
    is_nullable BIT NOT NULL,
    PRIMARY KEY (schema_name, table_name, column_name)
);

INSERT INTO @expected_columns VALUES
    (N'stg', N'usuario_record', N'stage_record_id', N'bigint', 8, 0),
    (N'stg', N'usuario_record', N'execution_id', N'uniqueidentifier', 16, 0),
    (N'stg', N'usuario_record', N'source_key', N'nvarchar', 512, 0),
    (N'stg', N'usuario_record', N'source_key_wire_type', N'nvarchar', 32, 0),
    (N'stg', N'usuario_record', N'name_presence', N'nvarchar', 16, 0),
    (N'stg', N'usuario_record', N'usuario_name', N'nvarchar', 510, 1),
    (N'core', N'usuario', N'usuario_id', N'bigint', 8, 0),
    (N'core', N'usuario', N'environment_name', N'nvarchar', 64, 0),
    (N'core', N'usuario', N'source_instance', N'nvarchar', 256, 0),
    (N'core', N'usuario', N'tenant_scope', N'nvarchar', 256, 0),
    (N'core', N'usuario', N'source_key', N'nvarchar', 512, 0),
    (N'core', N'usuario', N'observation_order_at_utc', N'datetime2', 7, 0),
    (N'core', N'usuario', N'observation_order_execution_id', N'uniqueidentifier', 16, 0),
    (N'core', N'usuario_history', N'usuario_history_id', N'bigint', 8, 0),
    (N'core', N'usuario_history', N'change_kind', N'nvarchar', 32, 0),
    (N'core', N'usuario_history', N'observation_order_execution_id', N'uniqueidentifier', 16, 0);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', CONCAT(expected.schema_name, N'.', expected.table_name, N'.', expected.column_name),
       N'Coluna obrigatória ausente ou com shape divergente.'
FROM @expected_columns AS expected
LEFT JOIN sys.tables AS table_definition
    ON table_definition.name COLLATE DATABASE_DEFAULT = expected.table_name
   AND table_definition.schema_id = SCHEMA_ID(expected.schema_name)
LEFT JOIN sys.columns AS column_definition
    ON column_definition.object_id = table_definition.object_id
   AND column_definition.name COLLATE DATABASE_DEFAULT = expected.column_name
LEFT JOIN sys.types AS type_definition
    ON type_definition.user_type_id = column_definition.user_type_id
WHERE column_definition.column_id IS NULL
   OR type_definition.name COLLATE DATABASE_DEFAULT <> expected.type_name
   OR column_definition.max_length <> expected.max_length
   OR column_definition.is_nullable <> expected.is_nullable;

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(schema_definition.name, N'.', table_definition.name, N'.', column_definition.name),
       N'Texto semântico da vertical não usa BIN2.'
FROM sys.columns AS column_definition
INNER JOIN sys.tables AS table_definition ON table_definition.object_id = column_definition.object_id
INNER JOIN sys.schemas AS schema_definition ON schema_definition.schema_id = table_definition.schema_id
WHERE (
        schema_definition.name = N'stg'
        AND table_definition.name = N'usuario_record'
      OR schema_definition.name = N'ctl'
        AND table_definition.name = N'usuario_promotion_result'
      OR schema_definition.name = N'core'
        AND table_definition.name IN (N'usuario', N'usuario_history')
      OR schema_definition.name = N'recon'
        AND table_definition.name IN (
            N'usuario_quarantine', N'usuario_apply_authorization',
            N'usuario_candidate_application', N'usuario_reconciliation_result',
            N'usuario_stage_archive', N'usuario_stage_restore'
        )
      )
  AND column_definition.collation_name IS NOT NULL
  AND column_definition.collation_name <> N'Latin1_General_100_BIN2';

IF COLUMNPROPERTY(OBJECT_ID(N'core.usuario'), N'usuario_id', N'IsIdentity') <> 1
    INSERT INTO @failures VALUES
        (N'IDENTITY', N'core.usuario.usuario_id', N'O canonical_id deve ser BIGINT IDENTITY.');

DECLARE @required_indexes TABLE (
    object_name NVARCHAR(256) NOT NULL,
    index_name SYSNAME NOT NULL,
    is_unique BIT NOT NULL
);
INSERT INTO @required_indexes VALUES
    (N'stg.execution_record', N'UX_stg_execution_record_usuario_binding', 1),
    (N'core.entity_record_state', N'UX_core_entity_record_state_usuario_binding', 1),
    (N'core.usuario', N'UQ_core_usuario_source', 1),
    (N'core.usuario', N'IX_core_usuario_active', 0),
    (N'core.usuario_history', N'UQ_core_usuario_history_execution', 1),
    (N'core.usuario_history', N'IX_core_usuario_history_timeline', 0),
    (N'recon.usuario_candidate_application', N'PK_recon_usuario_candidate_application', 1),
    (N'recon.usuario_quarantine', N'UQ_recon_usuario_quarantine_execution_source', 1);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', CONCAT(expected.object_name, N'.', expected.index_name),
       N'Índice obrigatório ausente ou com unicidade divergente.'
FROM @required_indexes AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.indexes AS actual
    WHERE actual.object_id = OBJECT_ID(expected.object_name)
      AND actual.name COLLATE DATABASE_DEFAULT = expected.index_name
      AND actual.is_unique = expected.is_unique
);

DECLARE @required_index_keys TABLE (
    object_name NVARCHAR(256) NOT NULL,
    index_name SYSNAME NOT NULL,
    key_ordinal TINYINT NOT NULL,
    column_name SYSNAME NOT NULL,
    is_descending_key BIT NOT NULL,
    PRIMARY KEY (object_name, index_name, key_ordinal)
);

INSERT INTO @required_index_keys VALUES
    (N'stg.usuario_record', N'IX_stg_usuario_record_execution_source', 1, N'execution_id', 0),
    (N'stg.usuario_record', N'IX_stg_usuario_record_execution_source', 2, N'source_key', 0),
    (N'stg.usuario_record', N'IX_stg_usuario_record_execution_source', 3, N'attribute_hash', 0),
    (N'stg.usuario_record', N'IX_stg_usuario_record_execution_source', 4, N'name_presence_hash', 0),
    (N'core.usuario', N'UQ_core_usuario_source', 1, N'environment_name', 0),
    (N'core.usuario', N'UQ_core_usuario_source', 2, N'source_instance', 0),
    (N'core.usuario', N'UQ_core_usuario_source', 3, N'tenant_scope', 0),
    (N'core.usuario', N'UQ_core_usuario_source', 4, N'entity_name', 0),
    (N'core.usuario', N'UQ_core_usuario_source', 5, N'source_key', 0),
    (N'core.usuario_history', N'IX_core_usuario_history_timeline', 1, N'usuario_id', 0),
    (N'core.usuario_history', N'IX_core_usuario_history_timeline', 2, N'observation_order_at_utc', 1),
    (N'core.usuario_history', N'IX_core_usuario_history_timeline', 3, N'observation_order_execution_id', 1),
    (N'core.usuario_history', N'IX_core_usuario_history_timeline', 4, N'usuario_history_id', 1),
    (N'core.usuario_history', N'UQ_core_usuario_history_execution', 1, N'execution_id', 0),
    (N'core.usuario_history', N'UQ_core_usuario_history_execution', 2, N'usuario_id', 0),
    (N'recon.usuario_candidate_application', N'PK_recon_usuario_candidate_application', 1, N'execution_id', 0),
    (N'recon.usuario_candidate_application', N'PK_recon_usuario_candidate_application', 2, N'source_key', 0);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX_KEY', CONCAT(expected.object_name, N'.', expected.index_name),
       N'A ordem, direção ou cardinalidade das chaves do índice diverge do contrato.'
FROM (
    SELECT DISTINCT object_name, index_name
    FROM @required_index_keys
) AS expected
WHERE EXISTS (
    SELECT required.key_ordinal,
           required.column_name COLLATE Latin1_General_100_BIN2,
           required.is_descending_key
    FROM @required_index_keys AS required
    WHERE required.object_name = expected.object_name
      AND required.index_name = expected.index_name
    EXCEPT
    SELECT actual_key.key_ordinal,
           actual_column.name COLLATE Latin1_General_100_BIN2,
           actual_key.is_descending_key
    FROM sys.indexes AS actual_index
    INNER JOIN sys.index_columns AS actual_key
        ON actual_key.object_id = actual_index.object_id
       AND actual_key.index_id = actual_index.index_id
       AND actual_key.key_ordinal > 0
    INNER JOIN sys.columns AS actual_column
        ON actual_column.object_id = actual_key.object_id
       AND actual_column.column_id = actual_key.column_id
    WHERE actual_index.object_id = OBJECT_ID(expected.object_name)
      AND actual_index.name COLLATE DATABASE_DEFAULT = expected.index_name
)
OR EXISTS (
    SELECT actual_key.key_ordinal,
           actual_column.name COLLATE Latin1_General_100_BIN2,
           actual_key.is_descending_key
    FROM sys.indexes AS actual_index
    INNER JOIN sys.index_columns AS actual_key
        ON actual_key.object_id = actual_index.object_id
       AND actual_key.index_id = actual_index.index_id
       AND actual_key.key_ordinal > 0
    INNER JOIN sys.columns AS actual_column
        ON actual_column.object_id = actual_key.object_id
       AND actual_column.column_id = actual_key.column_id
    WHERE actual_index.object_id = OBJECT_ID(expected.object_name)
      AND actual_index.name COLLATE DATABASE_DEFAULT = expected.index_name
    EXCEPT
    SELECT required.key_ordinal,
           required.column_name COLLATE Latin1_General_100_BIN2,
           required.is_descending_key
    FROM @required_index_keys AS required
    WHERE required.object_name = expected.object_name
      AND required.index_name = expected.index_name
);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(N'recon.usuario_candidate_application')
      AND index_definition.name COLLATE DATABASE_DEFAULT =
          N'PK_recon_usuario_candidate_application'
      AND index_definition.is_unique = 1
      AND index_definition.is_primary_key = 1
)
    INSERT INTO @failures VALUES
        (N'INDEX', N'recon.usuario_candidate_application.PK_recon_usuario_candidate_application',
         N'A chave de idempotência da aplicação deve ser PK única.');

DECLARE @required_constraints TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    constraint_name SYSNAME NOT NULL,
    constraint_type CHAR(2) NOT NULL,
    PRIMARY KEY (schema_name, table_name, constraint_name)
);

INSERT INTO @required_constraints VALUES
    (N'stg', N'usuario_record', N'FK_stg_usuario_record_stage', N'F'),
    (N'stg', N'usuario_record', N'CK_stg_usuario_record_wire_type', N'C'),
    (N'stg', N'usuario_record', N'CK_stg_usuario_record_name_presence', N'C'),
    (N'stg', N'usuario_record', N'CK_stg_usuario_record_fingerprints', N'C'),
    (N'recon', N'usuario_quarantine', N'FK_recon_usuario_quarantine_execution', N'F'),
    (N'recon', N'usuario_quarantine', N'CK_recon_usuario_quarantine_values', N'C'),
    (N'ctl', N'usuario_promotion_result', N'FK_ctl_usuario_promotion_result_generic', N'F'),
    (N'ctl', N'usuario_promotion_result', N'CK_ctl_usuario_promotion_result_counts', N'C'),
    (N'core', N'usuario', N'FK_core_usuario_record_binding', N'F'),
    (N'core', N'usuario', N'FK_core_usuario_first_execution', N'F'),
    (N'core', N'usuario', N'FK_core_usuario_last_execution', N'F'),
    (N'core', N'usuario', N'FK_core_usuario_changed_execution', N'F'),
    (N'core', N'usuario', N'FK_core_usuario_order_execution', N'F'),
    (N'core', N'usuario', N'CK_core_usuario_entity', N'C'),
    (N'core', N'usuario', N'CK_core_usuario_wire_type', N'C'),
    (N'core', N'usuario', N'CK_core_usuario_name_presence', N'C'),
    (N'core', N'usuario', N'CK_core_usuario_fingerprints', N'C'),
    (N'core', N'usuario', N'CK_core_usuario_times', N'C'),
    (N'core', N'usuario_history', N'FK_core_usuario_history_usuario', N'F'),
    (N'core', N'usuario_history', N'FK_core_usuario_history_execution', N'F'),
    (N'core', N'usuario_history', N'FK_core_usuario_history_order_execution', N'F'),
    (N'core', N'usuario_history', N'CK_core_usuario_history_change', N'C'),
    (N'core', N'usuario_history', N'CK_core_usuario_history_name_presence', N'C'),
    (N'core', N'usuario_history', N'CK_core_usuario_history_fingerprints', N'C'),
    (N'recon', N'usuario_apply_authorization',
        N'FK_recon_usuario_apply_authorization_validation', N'F'),
    (N'recon', N'usuario_apply_authorization',
        N'CK_recon_usuario_apply_authorization_values', N'C'),
    (N'recon', N'usuario_candidate_application',
        N'FK_recon_usuario_candidate_application_execution', N'F'),
    (N'recon', N'usuario_candidate_application',
        N'FK_recon_usuario_candidate_application_usuario', N'F'),
    (N'recon', N'usuario_candidate_application',
        N'FK_recon_usuario_candidate_application_order_execution', N'F'),
    (N'recon', N'usuario_candidate_application',
        N'CK_recon_usuario_candidate_application_values', N'C'),
    (N'recon', N'usuario_reconciliation_result',
        N'FK_recon_usuario_reconciliation_result_authorization', N'F'),
    (N'recon', N'usuario_reconciliation_result',
        N'CK_recon_usuario_reconciliation_result_counts', N'C'),
    (N'recon', N'usuario_stage_disposal_evidence',
        N'FK_recon_usuario_stage_disposal_manifest', N'F'),
    (N'recon', N'usuario_stage_disposal_evidence',
        N'FK_recon_usuario_stage_disposal_item', N'F'),
    (N'recon', N'usuario_stage_disposal_evidence',
        N'CK_recon_usuario_stage_disposal_counts', N'C'),
    (N'recon', N'usuario_stage_archive', N'FK_recon_usuario_stage_archive_generic', N'F'),
    (N'recon', N'usuario_stage_archive', N'FK_recon_usuario_stage_archive_plan_item', N'F'),
    (N'recon', N'usuario_stage_archive', N'CK_recon_usuario_stage_archive_values', N'C'),
    (N'recon', N'usuario_stage_restore', N'FK_recon_usuario_stage_restore_generic', N'F'),
    (N'recon', N'usuario_stage_restore', N'CK_recon_usuario_stage_restore_values', N'C');

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', CONCAT(expected.schema_name, N'.', expected.constraint_name),
       N'Constraint obrigatória ausente, não confiável, desabilitada ou ligada à tabela errada.'
FROM @required_constraints AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS constraint_definition
    INNER JOIN sys.tables AS parent_table
        ON parent_table.object_id = constraint_definition.parent_object_id
    WHERE constraint_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
      AND constraint_definition.type COLLATE DATABASE_DEFAULT = expected.constraint_type
      AND parent_table.object_id = OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name))
      AND (
          expected.constraint_type = N'F' AND EXISTS (
              SELECT 1 FROM sys.foreign_keys AS foreign_key
              WHERE foreign_key.object_id = constraint_definition.object_id
                AND foreign_key.is_disabled = 0
                AND foreign_key.is_not_trusted = 0
          )
          OR expected.constraint_type = N'C' AND EXISTS (
              SELECT 1 FROM sys.check_constraints AS check_definition
              WHERE check_definition.object_id = constraint_definition.object_id
                AND check_definition.is_disabled = 0
                AND check_definition.is_not_trusted = 0
          )
      )
);

DECLARE @stage_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'stg.usp_stage_usuario_record', N'P'));
DECLARE @apply_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'core.usp_apply_reconcile_publish_usuarios', N'P'));
DECLARE @common_apply_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'core.usp_apply_reconcile_publish_execution', N'P'));
DECLARE @lifecycle_plan_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'stg.usp_plan_staging_lifecycle_at', N'P'));
DECLARE @lifecycle_budget_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF'));
DECLARE @lifecycle_guard_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'ctl.trg_usuario_lifecycle_plan_budget', N'TR'));
DECLARE @candidate_declaration_position INT = CHARINDEX(
    N'DECLARE @candidate_rows', @apply_definition);
DECLARE @typed_apply_lock_position INT = CHARINDEX(
    N'EXEC core.usp_apply_reconcile_publish_execution',
    @apply_definition,
    @candidate_declaration_position
);
DECLARE @current_insert_position INT = CHARINDEX(
    N'INSERT INTO core.usuario (', @apply_definition);
DECLARE @current_update_position INT = CHARINDEX(
    N'UPDATE current_record', @apply_definition);
DECLARE @history_insert_position INT = CHARINDEX(
    N'INSERT INTO core.usuario_history', @apply_definition);
DECLARE @late_sidecar_fence_position INT = CHARINDEX(N'THROW 51703', @stage_definition);
DECLARE @generic_stage_call_position INT = CHARINDEX(
    N'EXEC stg.usp_stage_record', @stage_definition);

IF @stage_definition IS NULL
   OR @stage_definition NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 20%'
   OR @stage_definition NOT LIKE N'%source_key_wire_type%INTEGER%STRING%'
   OR @stage_definition NOT LIKE N'%usuarios-identity-presence-v1%'
   OR @stage_definition NOT LIKE N'%DATALENGTH(@usuario_name) > 510%'
   OR @stage_definition NOT LIKE N'%forbidden(code_point)%'
   OR @stage_definition NOT LIKE N'%(127)%'
   OR @stage_definition NOT LIKE N'%(159)%'
   OR @stage_definition NOT LIKE N'%FROM stg.usuario_record WITH (UPDLOCK, HOLDLOCK)%'
   OR @stage_definition NOT LIKE
      N'%FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)%'
   OR @stage_definition NOT LIKE N'%@preexisting_stage_record_id IS NOT NULL%'
   OR @late_sidecar_fence_position <= 0
   OR @generic_stage_call_position <= @late_sidecar_fence_position
    INSERT INTO @failures VALUES
        (N'STAGING_CONTRACT', N'stg.usp_stage_usuario_record',
         N'O boundary não prova teto 20, wire type, hashes e fence contra sidecar tardio.');

IF @apply_definition IS NULL
   OR @apply_definition NOT LIKE N'%BEGIN TRANSACTION%'
   OR @apply_definition NOT LIKE N'%EXEC core.usp_apply_reconcile_publish_execution%'
   OR @apply_definition NOT LIKE N'%@environment_name%current_record.environment_name%'
   OR @apply_definition NOT LIKE N'%INDEX(UQ_core_usuario_source), FORCESEEK%'
   OR @apply_definition NOT LIKE N'%INSERT INTO core.usuario_history%'
   OR @typed_apply_lock_position <= 0
   OR @current_insert_position <= @typed_apply_lock_position
   OR @current_update_position <= @typed_apply_lock_position
   OR @history_insert_position <= @typed_apply_lock_position
   OR @apply_definition NOT LIKE N'%STALE_NO_OP%'
   OR @apply_definition NOT LIKE N'%REACTIVATED%'
   OR @apply_definition NOT LIKE N'%observation_order_execution_id%'
   OR @apply_definition LIKE N'%MERGE %'
   OR @apply_definition LIKE N'%CURSOR%'
   OR @apply_definition LIKE N'%DEACTIVATED%'
    INSERT INTO @failures VALUES
        (N'APPLICATION_CONTRACT', N'core.usp_apply_reconcile_publish_usuarios',
         N'A aplicação não preserva isolamento, set-based, total order ou ausência sem sweep.');

IF @common_apply_definition IS NULL
   OR @common_apply_definition NOT LIKE N'%DATALENGTH(@environment_name)%'
   OR @common_apply_definition NOT LIKE N'%DATALENGTH(@source_instance)%'
   OR @common_apply_definition NOT LIKE N'%DATALENGTH(@tenant_scope)%'
   OR @common_apply_definition NOT LIKE N'%DATALENGTH(@entity_name)%'
   OR @common_apply_definition NOT LIKE N'%N''V2_APPLY_''%'
   OR @common_apply_definition NOT LIKE N'%sys.sp_getapplock%'
   OR @common_apply_definition NOT LIKE N'%@LockOwner = ''Transaction''%'
    INSERT INTO @failures VALUES
        (N'APPLICATION_LOCK', N'core.usp_apply_reconcile_publish_execution',
         N'O wrapper de Usuários não herda o lock transacional do namespace completo.');

IF @lifecycle_plan_definition IS NULL OR @lifecycle_budget_definition IS NULL
   OR @lifecycle_guard_definition IS NULL
   OR @lifecycle_budget_definition NOT LIKE N'%TOP (@maximum_extension_rows + 1)%'
   OR @lifecycle_budget_definition NOT LIKE N'%UQ_stg_usuario_record_execution_stage%FORCESEEK%'
   OR @lifecycle_budget_definition NOT LIKE N'%typed.execution_id = @execution_id%'
   OR @lifecycle_budget_definition NOT LIKE N'%COUNT_BIG(*) AS extension_rows%'
   OR @lifecycle_budget_definition NOT LIKE N'%discarded_name_bytes%'
   OR @lifecycle_plan_definition NOT LIKE
      N'%CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget%'
   OR @lifecycle_plan_definition NOT LIKE N'%extension_budget.additional_archive_bytes%'
   OR @lifecycle_plan_definition NOT LIKE N'%extension_budget.extension_rows > @maximum_stage_rows%'
   OR CHARINDEX(
        N'CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget',
        @lifecycle_plan_definition
      ) >= CHARINDEX(N'SELECT TOP (@maximum_executions)', @lifecycle_plan_definition)
   OR @lifecycle_guard_definition NOT LIKE N'%THROW 51731%'
   OR @lifecycle_guard_definition NOT LIKE N'%THROW 51732%'
   OR @lifecycle_guard_definition NOT LIKE N'%THROW 51733%'
   OR @lifecycle_guard_definition LIKE N'%UPDATE planned%'
   OR @lifecycle_guard_definition LIKE N'%DELETE planned%'
    INSERT INTO @failures VALUES
        (N'LIFECYCLE_BUDGET', N'recon.ufn_staging_lifecycle_extension_archive_budget',
         N'O custo tipado não é classificado antes de oversized/TOP/cumulativos.');

IF EXISTS (
    SELECT 1 FROM sys.views AS view_definition
    WHERE view_definition.name LIKE N'%usuario%'
      AND view_definition.schema_id = SCHEMA_ID(N'pub')
)
    INSERT INTO @failures VALUES
        (N'PUBLICATION', N'pub', N'V2-033 não autoriza view publicada de Usuários.');

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.class = 1
      AND permission_definition.major_id = OBJECT_ID(N'stg.usp_stage_usuario_record')
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state IN (N'G', N'W')
)
    INSERT INTO @failures VALUES
        (N'PERMISSION', N'stg.usp_stage_usuario_record', N'EXECUTE do runtime ausente.');

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.class = 1
      AND permission_definition.major_id = OBJECT_ID(N'core.usp_apply_reconcile_publish_usuarios')
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state IN (N'G', N'W')
)
    INSERT INTO @failures VALUES
        (N'PERMISSION', N'core.usp_apply_reconcile_publish_usuarios',
         N'EXECUTE do runtime ausente.');

IF EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN (N'G', N'W')
      AND permission_definition.class = 1
      AND permission_definition.major_id IN (
          OBJECT_ID(N'stg.usuario_record'), OBJECT_ID(N'core.usuario'),
          OBJECT_ID(N'core.usuario_history'), OBJECT_ID(N'recon.usuario_quarantine'),
          OBJECT_ID(N'ctl.usuario_promotion_result')
      )
      AND permission_definition.permission_name IN (N'SELECT', N'INSERT', N'UPDATE', N'DELETE')
)
    INSERT INTO @failures VALUES
        (N'PERMISSION', N'v2_runtime', N'DML direto positivo foi concedido à vertical.');

IF NOT EXISTS (
    SELECT 1 FROM sys.triggers AS trigger_definition
    WHERE trigger_definition.parent_id = OBJECT_ID(N'ctl.execution_publication_event')
      AND trigger_definition.name = N'trg_usuario_publication_requires_apply_wrapper'
      AND trigger_definition.is_disabled = 0
)
    INSERT INTO @failures VALUES
        (N'FAIL_CLOSED', N'ctl.execution_publication_event',
         N'O bypass do wrapper tipado não está bloqueado.');

DECLARE @typed_candidate_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'ctl.trg_usuario_prepare_candidate_set', N'TR'));
DECLARE @typed_dq_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.trg_usuario_data_quality_requires_typed_pass', N'TR'));

IF @typed_candidate_definition IS NULL
   OR @typed_candidate_definition NOT LIKE N'%usuario_promotion_result%'
   OR @typed_candidate_definition NOT LIKE N'%validation_state%'
   OR @typed_candidate_definition NOT LIKE N'%N''BLOCKED''%'
   OR @typed_dq_definition IS NULL
   OR @typed_dq_definition NOT LIKE N'%execution_data_quality_evaluation%'
   OR @typed_dq_definition NOT LIKE N'%usuario_promotion_result%'
   OR @typed_dq_definition NOT LIKE N'%validation_state <> N''PASSED''%'
   OR @typed_dq_definition NOT LIKE N'%THROW 51729%'
    INSERT INTO @failures VALUES
        (N'DATA_QUALITY', N'usuarios',
         N'O resultado tipado BLOCKED não está ligado ao DQ fail-closed.');

DECLARE @required_ledger_triggers TABLE (
    trigger_schema SYSNAME NOT NULL,
    trigger_name SYSNAME NOT NULL,
    parent_object NVARCHAR(256) NOT NULL,
    PRIMARY KEY (trigger_schema, trigger_name)
);

INSERT INTO @required_ledger_triggers VALUES
    (N'recon', N'trg_usuario_apply_authorization_immutable',
        N'recon.usuario_apply_authorization'),
    (N'core', N'trg_usuario_history_immutable', N'core.usuario_history'),
    (N'recon', N'trg_usuario_application_immutable',
        N'recon.usuario_candidate_application'),
    (N'recon', N'trg_usuario_result_immutable', N'recon.usuario_reconciliation_result'),
    (N'recon', N'trg_usuario_quarantine_immutable', N'recon.usuario_quarantine'),
    (N'ctl', N'trg_usuario_promotion_result_immutable', N'ctl.usuario_promotion_result'),
    (N'recon', N'trg_usuario_stage_archive_immutable', N'recon.usuario_stage_archive'),
    (N'recon', N'trg_usuario_stage_restore_immutable', N'recon.usuario_stage_restore'),
    (N'recon', N'trg_usuario_stage_disposal_immutable',
        N'recon.usuario_stage_disposal_evidence');

INSERT INTO @failures (category, object_name, detail)
SELECT N'IMMUTABILITY', CONCAT(expected.trigger_schema, N'.', expected.trigger_name),
       N'Controle tipado ausente, desabilitado ou associado ao ledger errado.'
FROM @required_ledger_triggers AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.triggers AS trigger_definition
    WHERE OBJECT_SCHEMA_NAME(trigger_definition.object_id) COLLATE DATABASE_DEFAULT =
          expected.trigger_schema
      AND trigger_definition.name COLLATE DATABASE_DEFAULT = expected.trigger_name
      AND trigger_definition.parent_id = OBJECT_ID(expected.parent_object)
      AND trigger_definition.parent_class = 1
      AND trigger_definition.is_disabled = 0
);

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51771, N'Contrato estrutural de Usuários divergiu.', 1;
END;

PRINT N'Usuários/current/history V2 validados com sucesso.';

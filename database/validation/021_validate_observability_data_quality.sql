-- Validação estrutural somente leitura de V2-023 após V001-V006.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_objects TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    object_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, object_name)
);
INSERT INTO @expected_objects (schema_name, object_name, object_type)
VALUES
    (N'ctl', N'data_quality_policy', N'U'),
    (N'ctl', N'data_quality_check_policy', N'U'),
    (N'recon', N'execution_data_quality_evaluation', N'U'),
    (N'recon', N'execution_data_quality_check_result', N'U'),
    (N'recon', N'execution_metric_snapshot', N'U'),
    (N'recon', N'observability_alert', N'U'),
    (N'recon', N'usp_evaluate_execution_data_quality', N'P'),
    (N'recon', N'usp_observe_execution_data_quality', N'P'),
    (N'recon', N'usp_record_execution_metric', N'P'),
    (N'recon', N'usp_raise_observability_alert', N'P'),
    (N'ctl', N'usp_observe_platform_health', N'P'),
    (N'ctl', N'trg_data_quality_policy_immutable', N'TR'),
    (N'ctl', N'trg_data_quality_check_policy_immutable', N'TR'),
    (N'ctl', N'trg_execution_publication_requires_data_quality', N'TR');

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', CONCAT(expected.schema_name, N'.', expected.object_name),
       N'Objeto obrigatório ausente ou de tipo divergente.'
FROM @expected_objects AS expected
LEFT JOIN sys.schemas AS schema_definition
    ON schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
LEFT JOIN sys.objects AS object_definition
    ON object_definition.schema_id = schema_definition.schema_id
   AND object_definition.name COLLATE DATABASE_DEFAULT = expected.object_name
   AND object_definition.type COLLATE DATABASE_DEFAULT = expected.object_type
WHERE object_definition.object_id IS NULL;

DECLARE @expected_constraints TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    parent_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    constraint_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    constraint_type CHAR(2) COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, parent_name, constraint_name)
);
INSERT INTO @expected_constraints
VALUES
    (N'ctl', N'data_quality_policy', N'PK_ctl_data_quality_policy', N'PK'),
    (N'ctl', N'data_quality_policy', N'CK_ctl_data_quality_policy_fingerprint', N'C'),
    (N'ctl', N'data_quality_policy', N'CK_ctl_data_quality_policy_values', N'C'),
    (N'ctl', N'data_quality_policy', N'CK_ctl_data_quality_policy_owner_roles', N'C'),
    (N'ctl', N'data_quality_check_policy', N'PK_ctl_data_quality_check_policy', N'PK'),
    (N'ctl', N'data_quality_check_policy', N'UQ_ctl_data_quality_check_policy_code', N'UQ'),
    (N'ctl', N'data_quality_check_policy', N'FK_ctl_data_quality_check_policy_policy', N'F'),
    (N'ctl', N'data_quality_check_policy', N'CK_ctl_data_quality_check_policy_values', N'C'),
    (N'ctl', N'data_quality_check_policy', N'CK_ctl_data_quality_check_policy_owner', N'C'),
    (N'recon', N'execution_data_quality_evaluation',
        N'PK_recon_execution_data_quality_evaluation', N'PK'),
    (N'recon', N'execution_data_quality_evaluation',
        N'FK_recon_execution_data_quality_evaluation_execution', N'F'),
    (N'recon', N'execution_data_quality_evaluation',
        N'FK_recon_execution_data_quality_evaluation_policy', N'F'),
    (N'recon', N'execution_data_quality_evaluation',
        N'CK_recon_execution_data_quality_evaluation_fingerprint', N'C'),
    (N'recon', N'execution_data_quality_evaluation',
        N'CK_recon_execution_data_quality_evaluation_counts', N'C'),
    (N'recon', N'execution_data_quality_check_result',
        N'PK_recon_execution_data_quality_check_result', N'PK'),
    (N'recon', N'execution_data_quality_check_result',
        N'UQ_recon_execution_data_quality_check_result_code', N'UQ'),
    (N'recon', N'execution_data_quality_check_result',
        N'FK_recon_execution_data_quality_check_result_evaluation', N'F'),
    (N'recon', N'execution_data_quality_check_result',
        N'CK_recon_execution_data_quality_check_result_values', N'C'),
    (N'recon', N'execution_metric_snapshot', N'PK_recon_execution_metric_snapshot', N'PK'),
    (N'recon', N'execution_metric_snapshot',
        N'FK_recon_execution_metric_snapshot_execution', N'F'),
    (N'recon', N'execution_metric_snapshot',
        N'CK_recon_execution_metric_snapshot_values', N'C'),
    (N'recon', N'observability_alert', N'PK_recon_observability_alert', N'PK'),
    (N'recon', N'observability_alert', N'CK_recon_observability_alert_reference', N'C'),
    (N'recon', N'observability_alert', N'CK_recon_observability_alert_values', N'C');

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', expected.constraint_name,
       N'Constraint obrigatória ausente, de tipo divergente ou no parent errado.'
FROM @expected_constraints AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS constraint_definition
    INNER JOIN sys.objects AS parent_definition
        ON parent_definition.object_id = constraint_definition.parent_object_id
    INNER JOIN sys.schemas AS schema_definition
        ON schema_definition.schema_id = parent_definition.schema_id
    WHERE schema_definition.name COLLATE DATABASE_DEFAULT = expected.schema_name
      AND parent_definition.name COLLATE DATABASE_DEFAULT = expected.parent_name
      AND constraint_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
      AND constraint_definition.type COLLATE DATABASE_DEFAULT = expected.constraint_type
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT_TRUST', expected.constraint_name,
       N'CHECK/FK obrigatória está desabilitada ou não confiável.'
FROM @expected_constraints AS expected
WHERE expected.constraint_type IN (N'C', N'F')
  AND (
      EXISTS (
          SELECT 1 FROM sys.check_constraints AS check_definition
          WHERE check_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
            AND (check_definition.is_disabled = 1 OR check_definition.is_not_trusted = 1)
      )
      OR EXISTS (
          SELECT 1 FROM sys.foreign_keys AS foreign_key_definition
          WHERE foreign_key_definition.name COLLATE DATABASE_DEFAULT = expected.constraint_name
            AND (foreign_key_definition.is_disabled = 1
                 OR foreign_key_definition.is_not_trusted = 1)
      )
  );

DECLARE @expected_indexes TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    parent_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    index_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY,
    is_unique BIT NOT NULL,
    has_filter BIT NOT NULL
);
INSERT INTO @expected_indexes
VALUES
    (N'ctl', N'data_quality_policy', N'UX_ctl_data_quality_policy_scope_effective', 1, 0),
    (N'recon', N'execution_data_quality_evaluation',
        N'IX_recon_execution_data_quality_evaluation_state', 0, 0),
    (N'ctl', N'execution_attempt', N'IX_ctl_execution_attempt_health_state_started', 0, 0),
    (N'ctl', N'execution_promotion_result',
        N'IX_ctl_execution_promotion_result_health_promoted', 0, 0);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', expected.index_name, N'Índice obrigatório ausente, desabilitado ou divergente.'
FROM @expected_indexes AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(
            CONCAT(expected.schema_name, N'.', expected.parent_name), N'U'
        )
      AND index_definition.name COLLATE DATABASE_DEFAULT = expected.index_name
      AND index_definition.is_unique = expected.is_unique
      AND index_definition.has_filter = expected.has_filter
      AND index_definition.is_disabled = 0
);

DECLARE @expected_index_columns TABLE (
    index_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    column_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    key_ordinal TINYINT NOT NULL,
    is_included BIT NOT NULL,
    PRIMARY KEY (index_name, column_name)
);
INSERT INTO @expected_index_columns
VALUES
    (N'UX_ctl_data_quality_policy_scope_effective', N'scope_fingerprint', 1, 0),
    (N'UX_ctl_data_quality_policy_scope_effective', N'effective_from_utc', 2, 0),
    (N'IX_recon_execution_data_quality_evaluation_state', N'evaluation_state', 1, 0),
    (N'IX_recon_execution_data_quality_evaluation_state', N'evaluated_at_utc', 2, 0),
    (N'IX_recon_execution_data_quality_evaluation_state', N'execution_id', 3, 0),
    (N'IX_ctl_execution_attempt_health_state_started', N'current_state', 1, 0),
    (N'IX_ctl_execution_attempt_health_state_started', N'started_at_utc', 2, 0),
    (N'IX_ctl_execution_attempt_health_state_started', N'execution_id', 3, 0),
    (N'IX_ctl_execution_attempt_health_state_started', N'partition_id', 0, 1),
    (N'IX_ctl_execution_promotion_result_health_promoted', N'promoted_at_utc', 1, 0),
    (N'IX_ctl_execution_promotion_result_health_promoted', N'execution_id', 2, 0);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX_COLUMNS', expected_index.index_name,
       N'Chaves/includes do índice obrigatório divergem do contrato.'
FROM @expected_indexes AS expected_index
WHERE EXISTS (
    SELECT 1
    FROM @expected_index_columns AS expected_column
    WHERE expected_column.index_name = expected_index.index_name
      AND NOT EXISTS (
          SELECT 1
          FROM sys.indexes AS index_definition
          INNER JOIN sys.index_columns AS index_column
              ON index_column.object_id = index_definition.object_id
             AND index_column.index_id = index_definition.index_id
          INNER JOIN sys.columns AS column_definition
              ON column_definition.object_id = index_column.object_id
             AND column_definition.column_id = index_column.column_id
          WHERE index_definition.name COLLATE DATABASE_DEFAULT = expected_index.index_name
            AND column_definition.name COLLATE DATABASE_DEFAULT = expected_column.column_name
            AND index_column.key_ordinal = expected_column.key_ordinal
            AND index_column.is_included_column = expected_column.is_included
      )
) OR EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    INNER JOIN sys.index_columns AS index_column
        ON index_column.object_id = index_definition.object_id
       AND index_column.index_id = index_definition.index_id
    INNER JOIN sys.columns AS column_definition
        ON column_definition.object_id = index_column.object_id
       AND column_definition.column_id = index_column.column_id
    WHERE index_definition.name COLLATE DATABASE_DEFAULT = expected_index.index_name
      AND (index_column.key_ordinal > 0 OR index_column.is_included_column = 1)
      AND NOT EXISTS (
          SELECT 1
          FROM @expected_index_columns AS expected_column
          WHERE expected_column.index_name = expected_index.index_name
            AND expected_column.column_name = column_definition.name COLLATE DATABASE_DEFAULT
            AND expected_column.key_ordinal = index_column.key_ordinal
            AND expected_column.is_included = index_column.is_included_column
      )
);

DECLARE @expected_triggers TABLE (
    trigger_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL PRIMARY KEY,
    parent_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    expected_events TINYINT NOT NULL
);
INSERT INTO @expected_triggers
VALUES
    (N'trg_data_quality_policy_immutable', N'data_quality_policy', 2),
    (N'trg_data_quality_check_policy_immutable', N'data_quality_check_policy', 2),
    (N'trg_execution_publication_requires_data_quality', N'execution_publication_event', 1);

INSERT INTO @failures (category, object_name, detail)
SELECT N'TRIGGER', expected.trigger_name,
       N'Trigger ausente, desabilitado, NOT FOR REPLICATION ou no parent errado.'
FROM @expected_triggers AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.triggers AS trigger_definition
    WHERE trigger_definition.object_id = OBJECT_ID(CONCAT(N'ctl.', expected.trigger_name), N'TR')
      AND trigger_definition.parent_id = OBJECT_ID(CONCAT(N'ctl.', expected.parent_name), N'U')
      AND trigger_definition.is_disabled = 0
      AND trigger_definition.is_instead_of_trigger = 0
      AND trigger_definition.is_not_for_replication = 0
      AND (SELECT COUNT_BIG(*) FROM sys.trigger_events AS trigger_event
           WHERE trigger_event.object_id = trigger_definition.object_id) = expected.expected_events
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'TRIGGER_EVENT', expected.trigger_name, N'Eventos do trigger divergem do contrato.'
FROM @expected_triggers AS expected
WHERE (expected.trigger_name = N'trg_execution_publication_requires_data_quality' AND (
        NOT EXISTS (SELECT 1 FROM sys.trigger_events
                    WHERE object_id = OBJECT_ID(CONCAT(N'ctl.', expected.trigger_name), N'TR')
                      AND type_desc = N'INSERT')
        OR EXISTS (SELECT 1 FROM sys.trigger_events
                   WHERE object_id = OBJECT_ID(CONCAT(N'ctl.', expected.trigger_name), N'TR')
                     AND type_desc <> N'INSERT')
    ))
   OR (expected.trigger_name <> N'trg_execution_publication_requires_data_quality' AND (
        NOT EXISTS (SELECT 1 FROM sys.trigger_events
                    WHERE object_id = OBJECT_ID(CONCAT(N'ctl.', expected.trigger_name), N'TR')
                      AND type_desc = N'UPDATE')
        OR NOT EXISTS (SELECT 1 FROM sys.trigger_events
                       WHERE object_id = OBJECT_ID(CONCAT(N'ctl.', expected.trigger_name), N'TR')
                         AND type_desc = N'DELETE')
    ));

DECLARE @evaluate_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.usp_evaluate_execution_data_quality', N'P')
);
DECLARE @observe_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.usp_observe_execution_data_quality', N'P')
);
DECLARE @metric_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.usp_record_execution_metric', N'P')
);
DECLARE @alert_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'recon.usp_raise_observability_alert', N'P')
);
DECLARE @health_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'ctl.usp_observe_platform_health', N'P')
);
DECLARE @publication_gate_definition NVARCHAR(MAX) = OBJECT_DEFINITION(
    OBJECT_ID(N'ctl.trg_execution_publication_requires_data_quality', N'TR')
);
DECLARE @metric_constraint_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'recon.execution_metric_snapshot', N'U')
      AND name = N'CK_recon_execution_metric_snapshot_values'
);
DECLARE @check_policy_constraint_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'ctl.data_quality_check_policy', N'U')
      AND name = N'CK_ctl_data_quality_check_policy_values'
);
DECLARE @metric_constraint_canonical NVARCHAR(MAX) = LOWER(REPLACE(REPLACE(REPLACE(
    REPLACE(REPLACE(@metric_constraint_definition, N'[', N''), N']', N''),
    N' ', N''), N'(', N''), N')', N''));
DECLARE @check_policy_constraint_canonical NVARCHAR(MAX) = LOWER(REPLACE(REPLACE(REPLACE(
    REPLACE(REPLACE(@check_policy_constraint_definition, N'[', N''), N']', N''),
    N' ', N''), N'(', N''), N')', N''));

IF @evaluate_definition IS NULL
   OR @evaluate_definition NOT LIKE N'%sys.sp_getapplock%'
   OR @evaluate_definition NOT LIKE N'%V2_APPLY_%'
   OR CHARINDEX(N'sys.sp_getapplock', @evaluate_definition)
        >= CHARINDEX(N'WITH (UPDLOCK, HOLDLOCK)', @evaluate_definition)
   OR @evaluate_definition NOT LIKE N'%dq-scope-v1|%'
   OR @evaluate_definition NOT LIKE N'%dq-policy-v1|%'
   OR @evaluate_definition NOT LIKE N'%@calculated_policy_fingerprint%'
   OR @evaluate_definition NOT LIKE N'%newer.effective_from_utc > policy.effective_from_utc%'
   OR @evaluate_definition NOT LIKE N'%@database_now_utc = SYSUTCDATETIME()%'
   OR @evaluate_definition NOT LIKE N'%@non_terminal_empty_pages = 0%'
   OR @evaluate_definition NOT LIKE N'%@oversized_root_pages = 0%'
   OR @evaluate_definition NOT LIKE N'%@candidate_rows = @count_valid%'
   OR @evaluate_definition NOT LIKE N'%CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000%'
   OR @evaluate_definition NOT LIKE N'%A execução parcial dos checks foi recusada%'
   OR @evaluate_definition LIKE N'%sp_executesql%'
   OR @evaluate_definition LIKE N'%EXECUTE(%'
    INSERT INTO @failures VALUES (
        N'DATA_QUALITY', N'recon.usp_evaluate_execution_data_quality',
        N'A avaliação não preserva fence, scope/hash vigente, equações, threshold ou recusa parcial.'
    );

IF @check_policy_constraint_canonical IS NULL
   OR @check_policy_constraint_canonical NOT LIKE N'%check_code=n''quarantine_sla''%'
   OR @check_policy_constraint_canonical NOT LIKE N'%maximum_failed_rows=0%'
   OR @check_policy_constraint_canonical NOT LIKE N'%maximum_failure_basis_points=0%'
    INSERT INTO @failures VALUES (
        N'POLICY', N'CK_ctl_data_quality_check_policy_values',
        N'Checks estruturais precisam de threshold zero e só SLA pode tolerar falha.'
    );

IF @metric_constraint_canonical IS NULL
   OR @metric_constraint_canonical NOT LIKE N'%physical_rows=distinct_root_keys+duplicate_rows+unidentified_quarantine_rows%'
   OR @metric_constraint_canonical NOT LIKE N'%distinct_root_keys=valid_rows+quarantined_root_keys%'
   OR @metric_constraint_canonical NOT LIKE N'%candidate_rows=valid_rows%'
   OR @metric_constraint_canonical NOT LIKE N'%candidate_rows=inserted_rows+updated_rows+reactivated_rows+noop_rows%'
   OR @metric_constraint_canonical NOT LIKE N'%stale_noop_rows<=noop_rows%'
    INSERT INTO @failures VALUES (
        N'METRIC', N'CK_recon_execution_metric_snapshot_values',
        N'As equações fixas da métrica não reconciliam staging e aplicação.'
    );

IF @observe_definition IS NULL
   OR @observe_definition NOT LIKE N'%TOP (@maximum_sanitized_samples)%'
   OR @observe_definition NOT LIKE N'%@maximum_sanitized_samples IS NULL%'
   OR @observe_definition NOT LIKE N'%@maximum_sanitized_samples NOT BETWEEN 1 AND 32%'
   OR @observe_definition LIKE N'%source_key%'
   OR @observe_definition LIKE N'%payload%'
    INSERT INTO @failures VALUES (
        N'EVIDENCE', N'recon.usp_observe_execution_data_quality',
        N'A amostra não está limitada, sanitizada e fail-closed para NULL.'
    );

IF @metric_definition IS NULL
   OR @metric_definition NOT LIKE N'%@stale_noop_rows BIGINT%'
   OR @metric_definition NOT LIKE N'%@source_lag_milliseconds BIGINT%'
   OR @metric_definition NOT LIKE N'%@watermark_utc DATETIME2(3)%'
   OR @metric_definition NOT LIKE N'%A métrica exige o shape fixo completo%'
   OR @metric_definition NOT LIKE N'%(watermark_utc IS NULL AND @watermark_utc IS NOT NULL)%'
    INSERT INTO @failures VALUES (
        N'METRIC', N'recon.usp_record_execution_metric',
        N'O entrypoint de métrica não preserva shape fixo, NULL guard e retry exato.'
    );

IF @alert_definition IS NULL
   OR @alert_definition NOT LIKE N'%@correlation_reference NVARCHAR(MAX)%'
   OR @alert_definition NOT LIKE N'%@severity NVARCHAR(MAX)%'
   OR @alert_definition NOT LIKE N'%@alert_code NVARCHAR(MAX)%'
   OR @alert_definition NOT LIKE N'%@owner_role NVARCHAR(MAX)%'
   OR @alert_definition NOT LIKE N'%O alerta exige o shape canônico completo%'
    INSERT INTO @failures VALUES (
        N'ALERT', N'recon.usp_raise_observability_alert',
        N'O alerta não rejeita NULL, truncamento e aliases antes da persistência.'
    );

IF @health_definition IS NULL
   OR @health_definition NOT LIKE N'%@maximum_running_age_seconds IS NULL%'
   OR @health_definition NOT LIKE N'%promotion.promoted_at_utc < @running_cutoff_utc%'
   OR @health_definition NOT LIKE N'%sla_check.maximum_failed_rows%'
   OR @health_definition NOT LIKE N'%sla_check.maximum_failure_basis_points%'
   OR @health_definition NOT LIKE N'%newer.effective_from_utc > policy.effective_from_utc%'
   OR @health_definition NOT LIKE N'%expected_check.check_ordinal = result.check_ordinal%'
   OR @health_definition NOT LIKE N'%QUARANTINE_SLA_EXCEEDED%'
    INSERT INTO @failures VALUES (
        N'HEALTH', N'ctl.usp_observe_platform_health',
        N'O health não revalida DQ vigente, shape exato e SLA temporal por threshold.'
    );

IF @publication_gate_definition IS NULL
   OR @publication_gate_definition NOT LIKE N'%@publication_gate_now_utc%'
   OR @publication_gate_definition NOT LIKE N'%evaluation.expected_checks <> policy.expected_checks%'
   OR @publication_gate_definition NOT LIKE N'%expected_check.check_ordinal = result.check_ordinal%'
   OR @publication_gate_definition NOT LIKE N'%sla_check.maximum_failure_basis_points%'
   OR @publication_gate_definition NOT LIKE N'%policy.scope_fingerprint <> LOWER(CONVERT(%'
   OR @publication_gate_definition NOT LIKE N'%promotion.candidate_rows <> evaluation.candidate_rows%'
    INSERT INTO @failures VALUES (
        N'PUBLICATION_GATE', N'ctl.trg_execution_publication_requires_data_quality',
        N'O trigger não revalida PASS, checks, SLA atual, scope, policy e candidate set.'
    );

IF (SELECT COUNT_BIG(*) FROM sys.parameters
    WHERE object_id = OBJECT_ID(N'recon.usp_evaluate_execution_data_quality', N'P')) <> 3
   OR EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'recon.usp_evaluate_execution_data_quality', N'P')
         AND parameter_id IN (2, 3)
         AND (TYPE_NAME(user_type_id) <> N'nvarchar' OR max_length <> -1)
   )
   OR (SELECT COUNT_BIG(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'recon.usp_record_execution_metric', N'P')) <> 23
   OR (SELECT COUNT_BIG(*) FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'recon.usp_raise_observability_alert', N'P')) <> 7
   OR EXISTS (
       SELECT 1 FROM sys.parameters
       WHERE object_id = OBJECT_ID(N'recon.usp_raise_observability_alert', N'P')
         AND parameter_id IN (1, 3, 4, 5)
         AND (TYPE_NAME(user_type_id) <> N'nvarchar' OR max_length <> -1)
   )
    INSERT INTO @failures VALUES (
        N'PARAMETER_SHAPE', N'V2-023 entrypoints',
        N'Quantidade ou largura de parâmetros diverge do contrato sem truncamento.'
    );

IF (SELECT COUNT_BIG(*) FROM sys.columns
    WHERE object_id = OBJECT_ID(N'recon.execution_metric_snapshot', N'U')) <> 23
   OR NOT EXISTS (
       SELECT 1 FROM sys.columns
       WHERE object_id = OBJECT_ID(N'ctl.data_quality_policy', N'U')
         AND name = N'scope_fingerprint' AND TYPE_NAME(user_type_id) = N'char'
         AND max_length = 64 AND is_nullable = 0
   )
   OR NOT EXISTS (
       SELECT 1 FROM sys.columns
       WHERE object_id = OBJECT_ID(N'recon.execution_data_quality_evaluation', N'U')
         AND name = N'scope_fingerprint' AND TYPE_NAME(user_type_id) = N'char'
         AND max_length = 64 AND is_nullable = 0
   )
    INSERT INTO @failures VALUES (
        N'COLUMN_SHAPE', N'V2-023 tables',
        N'Scope ou shape métrico fixo diverge do contrato.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'MODULE_SECURITY', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Módulo V2-023 não pode usar EXECUTE AS.'
FROM sys.sql_modules AS module_definition
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = module_definition.object_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN @expected_objects AS expected
    ON expected.schema_name = schema_definition.name COLLATE DATABASE_DEFAULT
   AND expected.object_name = object_definition.name COLLATE DATABASE_DEFAULT
WHERE module_definition.execute_as_principal_id IS NOT NULL;

DECLARE @expected_runtime_grants TABLE (
    schema_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    procedure_name SYSNAME COLLATE DATABASE_DEFAULT NOT NULL,
    PRIMARY KEY (schema_name, procedure_name)
);
INSERT INTO @expected_runtime_grants
VALUES
    (N'recon', N'usp_evaluate_execution_data_quality'),
    (N'recon', N'usp_observe_execution_data_quality'),
    (N'recon', N'usp_record_execution_metric'),
    (N'recon', N'usp_raise_observability_alert'),
    (N'ctl', N'usp_observe_platform_health');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(expected.schema_name, N'.', expected.procedure_name),
       N'EXECUTE mínimo do runtime ausente.'
FROM @expected_runtime_grants AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.class = 1
      AND permission_definition.major_id = OBJECT_ID(
          CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P'
      )
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state = N'G'
      AND permission_definition.minor_id = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Grant produtivo V2-023 fora dos cinco entrypoints.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = permission_definition.major_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
  AND permission_definition.class = 1
  AND permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN (N'G', N'W')
  AND object_definition.name COLLATE DATABASE_DEFAULT IN (
      SELECT object_name FROM @expected_objects WHERE object_type = N'P'
  )
  AND NOT EXISTS (
      SELECT 1 FROM @expected_runtime_grants AS expected
      WHERE expected.schema_name = schema_definition.name COLLATE DATABASE_DEFAULT
        AND expected.procedure_name = object_definition.name COLLATE DATABASE_DEFAULT
  );

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51620, N'Framework de observabilidade e Data Quality diverge do contrato.', 1;
END;

PRINT N'Observabilidade e Data Quality V2 validadas com sucesso.';

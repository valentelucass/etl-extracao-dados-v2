-- Validador somente leitura do lifecycle local V2-045a. Requer V001-V005 aplicadas.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_tables TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    PRIMARY KEY (schema_name, table_name)
);
INSERT INTO @expected_tables (schema_name, table_name)
VALUES
    (N'ctl', N'staging_retention_policy'),
    (N'ctl', N'staging_retention_policy_event'),
    (N'ctl', N'staging_legal_hold'),
    (N'ctl', N'staging_legal_hold_event'),
    (N'ctl', N'staging_lifecycle_plan'),
    (N'ctl', N'staging_lifecycle_plan_item'),
    (N'ctl', N'staging_lifecycle_purge_event'),
    (N'ctl', N'execution_state_event'),
    (N'ctl', N'execution_page_audit'),
    (N'ctl', N'execution_count'),
    (N'ctl', N'execution_publication_event'),
    (N'ctl', N'execution_promotion_result'),
    (N'stg', N'execution_record'),
    (N'stg', N'execution_candidate'),
    (N'recon', N'quarantine_record'),
    (N'recon', N'execution_candidate_application'),
    (N'recon', N'execution_reconciliation_result'),
    (N'recon', N'staging_lifecycle_archive_manifest'),
    (N'recon', N'staging_record_archive'),
    (N'recon', N'staging_candidate_archive'),
    (N'recon', N'quarantine_record_archive'),
    (N'recon', N'execution_candidate_application_archive'),
    (N'recon', N'execution_reconciliation_result_archive'),
    (N'recon', N'execution_state_event_archive'),
    (N'recon', N'execution_page_audit_archive'),
    (N'recon', N'execution_count_archive'),
    (N'recon', N'execution_publication_event_archive'),
    (N'recon', N'execution_promotion_result_archive'),
    (N'recon', N'staging_restore_session'),
    (N'recon', N'staging_restore_record'),
    (N'recon', N'staging_restore_candidate');

INSERT INTO @failures (category, object_name, detail)
SELECT N'TABLE', CONCAT(expected.schema_name, N'.', expected.table_name),
       N'Tabela obrigatória do lifecycle ausente.'
FROM @expected_tables AS expected
WHERE OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.table_name), N'U') IS NULL;

IF OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF') IS NULL
    INSERT INTO @failures VALUES (
        N'FUNCTION', N'recon.ufn_staging_lifecycle_extension_archive_budget',
        N'Contrato set-based de extensão do budget ausente.'
    );

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns AS column_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = column_definition.user_type_id
    WHERE column_definition.object_id =
          OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF')
      AND column_definition.name = N'extension_rows'
      AND type_definition.name = N'bigint'
) OR NOT EXISTS (
    SELECT 1
    FROM sys.columns AS column_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = column_definition.user_type_id
    WHERE column_definition.object_id =
          OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF')
      AND column_definition.name = N'additional_archive_bytes'
      AND type_definition.name = N'bigint'
)
    INSERT INTO @failures VALUES (
        N'FUNCTION', N'recon.ufn_staging_lifecycle_extension_archive_budget',
        N'Contrato da extensão deve expor extension_rows e additional_archive_bytes tipados.'
    );

IF (SELECT COUNT_BIG(*)
    FROM sys.parameters
    WHERE object_id = OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF')) <> 2
   OR EXISTS (
       SELECT expected.parameter_id, expected.parameter_name, expected.type_name
       FROM (VALUES
           (1, N'@execution_id', N'uniqueidentifier'),
           (2, N'@maximum_extension_rows', N'bigint')
       ) AS expected(parameter_id, parameter_name, type_name)
       EXCEPT
       SELECT parameter_definition.parameter_id, parameter_definition.name,
              type_definition.name
       FROM sys.parameters AS parameter_definition
       INNER JOIN sys.types AS type_definition
           ON type_definition.user_type_id = parameter_definition.user_type_id
       WHERE parameter_definition.object_id =
             OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF')
   )
    INSERT INTO @failures VALUES (
        N'FUNCTION', N'recon.ufn_staging_lifecycle_extension_archive_budget',
        N'Parâmetros da extensão de budget divergem do contrato limitado por execução.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIMARY_KEY', expected.constraint_name,
       N'Primary key do lifecycle ausente ou associada ao objeto incorreto.'
FROM (VALUES
    (N'PK_ctl_staging_retention_policy', N'ctl.staging_retention_policy'),
    (N'PK_ctl_staging_retention_policy_event', N'ctl.staging_retention_policy_event'),
    (N'PK_ctl_staging_legal_hold', N'ctl.staging_legal_hold'),
    (N'PK_ctl_staging_legal_hold_event', N'ctl.staging_legal_hold_event'),
    (N'PK_ctl_staging_lifecycle_plan', N'ctl.staging_lifecycle_plan'),
    (N'PK_ctl_staging_lifecycle_plan_item', N'ctl.staging_lifecycle_plan_item'),
    (N'PK_ctl_staging_lifecycle_purge_event', N'ctl.staging_lifecycle_purge_event'),
    (N'PK_recon_staging_lifecycle_archive_manifest',
        N'recon.staging_lifecycle_archive_manifest'),
    (N'PK_recon_staging_record_archive', N'recon.staging_record_archive'),
    (N'PK_recon_staging_candidate_archive', N'recon.staging_candidate_archive'),
    (N'PK_recon_quarantine_record_archive', N'recon.quarantine_record_archive'),
    (N'PK_recon_execution_candidate_application_archive',
        N'recon.execution_candidate_application_archive'),
    (N'PK_recon_execution_reconciliation_result_archive',
        N'recon.execution_reconciliation_result_archive'),
    (N'PK_recon_execution_state_event_archive', N'recon.execution_state_event_archive'),
    (N'PK_recon_execution_page_audit_archive', N'recon.execution_page_audit_archive'),
    (N'PK_recon_execution_count_archive', N'recon.execution_count_archive'),
    (N'PK_recon_execution_publication_event_archive',
        N'recon.execution_publication_event_archive'),
    (N'PK_recon_execution_promotion_result_archive',
        N'recon.execution_promotion_result_archive'),
    (N'PK_recon_staging_restore_session', N'recon.staging_restore_session'),
    (N'PK_recon_staging_restore_record', N'recon.staging_restore_record'),
    (N'PK_recon_staging_restore_candidate', N'recon.staging_restore_candidate')
) AS expected (constraint_name, object_name)
WHERE NOT EXISTS (
    SELECT 1 FROM sys.key_constraints AS key_definition
    WHERE key_definition.name = expected.constraint_name
      AND key_definition.type = N'PK'
      AND key_definition.parent_object_id = OBJECT_ID(expected.object_name, N'U')
);

DECLARE @expected_index_keys TABLE (
    object_name NVARCHAR(256) NOT NULL,
    index_name SYSNAME NOT NULL,
    key_ordinal INT NOT NULL,
    column_name SYSNAME NOT NULL,
    unique_index BIT NOT NULL,
    primary_key BIT NOT NULL,
    unique_constraint BIT NOT NULL,
    is_clustered BIT NOT NULL,
    filter_column SYSNAME NULL,
    PRIMARY KEY (object_name, index_name, key_ordinal)
);
INSERT INTO @expected_index_keys (
    object_name, index_name, key_ordinal, column_name, unique_index,
    primary_key, unique_constraint, is_clustered, filter_column
)
VALUES
    (N'ctl.staging_retention_policy', N'PK_ctl_staging_retention_policy', 1,
        N'policy_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_retention_policy_event', N'PK_ctl_staging_retention_policy_event', 1,
        N'policy_event_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_legal_hold', N'PK_ctl_staging_legal_hold', 1,
        N'hold_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_legal_hold_event', N'PK_ctl_staging_legal_hold_event', 1,
        N'hold_event_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_lifecycle_plan', N'PK_ctl_staging_lifecycle_plan', 1,
        N'plan_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_lifecycle_plan_item', N'PK_ctl_staging_lifecycle_plan_item', 1,
        N'plan_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_lifecycle_plan_item', N'PK_ctl_staging_lifecycle_plan_item', 2,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_lifecycle_archive_manifest',
        N'PK_recon_staging_lifecycle_archive_manifest', 1,
        N'plan_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_record_archive', N'PK_recon_staging_record_archive', 1,
        N'stage_record_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_candidate_archive', N'PK_recon_staging_candidate_archive', 1,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_candidate_archive', N'PK_recon_staging_candidate_archive', 2,
        N'source_key', 1, 1, 0, 1, NULL),
    (N'recon.quarantine_record_archive', N'PK_recon_quarantine_record_archive', 1,
        N'quarantine_record_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_candidate_application_archive',
        N'PK_recon_execution_candidate_application_archive', 1,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_candidate_application_archive',
        N'PK_recon_execution_candidate_application_archive', 2,
        N'source_key', 1, 1, 0, 1, NULL),
    (N'recon.execution_reconciliation_result_archive',
        N'PK_recon_execution_reconciliation_result_archive', 1,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_state_event_archive', N'PK_recon_execution_state_event_archive', 1,
        N'state_event_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_page_audit_archive', N'PK_recon_execution_page_audit_archive', 1,
        N'page_audit_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_count_archive', N'PK_recon_execution_count_archive', 1,
        N'count_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_publication_event_archive',
        N'PK_recon_execution_publication_event_archive', 1,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.execution_promotion_result_archive',
        N'PK_recon_execution_promotion_result_archive', 1,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'ctl.staging_lifecycle_purge_event', N'PK_ctl_staging_lifecycle_purge_event', 1,
        N'plan_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_session', N'PK_recon_staging_restore_session', 1,
        N'restore_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_record', N'PK_recon_staging_restore_record', 1,
        N'restore_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_record', N'PK_recon_staging_restore_record', 2,
        N'stage_record_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_candidate', N'PK_recon_staging_restore_candidate', 1,
        N'restore_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_candidate', N'PK_recon_staging_restore_candidate', 2,
        N'execution_id', 1, 1, 0, 1, NULL),
    (N'recon.staging_restore_candidate', N'PK_recon_staging_restore_candidate', 3,
        N'source_key', 1, 1, 0, 1, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 1,
        N'environment_name', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 2,
        N'source_instance', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 3,
        N'tenant_scope', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 4,
        N'entity_name', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 5,
        N'terminal_outcome_class', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy', N'UQ_ctl_staging_retention_policy_version', 6,
        N'policy_version', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy_event',
        N'UQ_ctl_staging_retention_policy_event_action', 1,
        N'policy_id', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy_event',
        N'UQ_ctl_staging_retention_policy_event_action', 2,
        N'event_action', 1, 0, 1, 0, NULL),
    (N'ctl.staging_legal_hold_event', N'UQ_ctl_staging_legal_hold_event_action', 1,
        N'hold_id', 1, 0, 1, 0, NULL),
    (N'ctl.staging_legal_hold_event', N'UQ_ctl_staging_legal_hold_event_action', 2,
        N'event_action', 1, 0, 1, 0, NULL),
    (N'recon.staging_record_archive', N'UQ_recon_staging_record_archive_plan_stage', 1,
        N'plan_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_record_archive', N'UQ_recon_staging_record_archive_plan_stage', 2,
        N'stage_record_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_record_archive', N'UQ_recon_staging_record_archive_input', 1,
        N'execution_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_record_archive', N'UQ_recon_staging_record_archive_input', 2,
        N'input_batch_number', 1, 0, 1, 0, NULL),
    (N'recon.staging_record_archive', N'UQ_recon_staging_record_archive_input', 3,
        N'input_record_ordinal', 1, 0, 1, 0, NULL),
    (N'recon.staging_candidate_archive',
        N'UQ_recon_staging_candidate_archive_plan_candidate', 1,
        N'plan_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_candidate_archive',
        N'UQ_recon_staging_candidate_archive_plan_candidate', 2,
        N'execution_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_candidate_archive',
        N'UQ_recon_staging_candidate_archive_plan_candidate', 3,
        N'source_key', 1, 0, 1, 0, NULL),
    (N'recon.staging_candidate_archive', N'UQ_recon_staging_candidate_archive_winner', 1,
        N'winner_stage_record_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_restore_session',
        N'UQ_recon_staging_restore_session_materialization', 1,
        N'plan_id', 1, 0, 1, 0, NULL),
    (N'recon.staging_restore_session',
        N'UQ_recon_staging_restore_session_materialization', 2,
        N'execution_id', 1, 0, 1, 0, NULL),
    (N'ctl.staging_retention_policy',
        N'UX_ctl_staging_retention_policy_active_scope',
        1, N'environment_name', 1, 0, 0, 0, N'revoked_at_utc'),
    (N'ctl.staging_retention_policy',
        N'UX_ctl_staging_retention_policy_active_scope',
        2, N'source_instance', 1, 0, 0, 0, N'revoked_at_utc'),
    (N'ctl.staging_retention_policy',
        N'UX_ctl_staging_retention_policy_active_scope',
        3, N'tenant_scope', 1, 0, 0, 0, N'revoked_at_utc'),
    (N'ctl.staging_retention_policy',
        N'UX_ctl_staging_retention_policy_active_scope',
        4, N'entity_name', 1, 0, 0, 0, N'revoked_at_utc'),
    (N'ctl.staging_retention_policy',
        N'UX_ctl_staging_retention_policy_active_scope',
        5, N'terminal_outcome_class', 1, 0, 0, 0, N'revoked_at_utc'),
    (N'ctl.staging_legal_hold', N'UX_ctl_staging_legal_hold_active_execution',
        1, N'execution_id', 1, 0, 0, 0, N'released_at_utc'),
    (N'ctl.execution_attempt', N'IX_ctl_execution_attempt_lifecycle', 1,
        N'partition_id', 0, 0, 0, 0, NULL),
    (N'ctl.execution_attempt', N'IX_ctl_execution_attempt_lifecycle', 2,
        N'current_state', 0, 0, 0, 0, NULL),
    (N'ctl.execution_attempt', N'IX_ctl_execution_attempt_lifecycle', 3,
        N'terminal_at_utc', 0, 0, 0, 0, NULL),
    (N'ctl.execution_attempt', N'IX_ctl_execution_attempt_lifecycle', 4,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_legal_hold', N'IX_ctl_staging_legal_hold_execution', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_legal_hold', N'IX_ctl_staging_legal_hold_execution', 2,
        N'hold_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution', 2,
        N'hold_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution', 3,
        N'event_action', 0, 0, 0, 0, NULL),
    (N'stg.execution_record', N'IX_stg_execution_record_lifecycle', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'stg.execution_record', N'IX_stg_execution_record_lifecycle', 2,
        N'stage_record_id', 0, 0, 0, 0, NULL),
    (N'recon.quarantine_record', N'IX_recon_quarantine_record_lifecycle', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'recon.quarantine_record', N'IX_recon_quarantine_record_lifecycle', 2,
        N'quarantine_record_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_lifecycle_plan_item', N'IX_ctl_staging_lifecycle_plan_item_execution', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'ctl.staging_lifecycle_plan_item', N'IX_ctl_staging_lifecycle_plan_item_execution', 2,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.staging_record_archive', N'IX_recon_staging_record_archive_execution', 1,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'recon.staging_record_archive', N'IX_recon_staging_record_archive_execution', 2,
        N'stage_record_id', 0, 0, 0, 0, NULL),
    (N'recon.quarantine_record_archive', N'IX_recon_quarantine_record_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.quarantine_record_archive', N'IX_recon_quarantine_record_archive_plan', 2,
        N'quarantine_record_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_candidate_application_archive',
        N'IX_recon_candidate_application_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_candidate_application_archive',
        N'IX_recon_candidate_application_archive_plan', 2,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_candidate_application_archive',
        N'IX_recon_candidate_application_archive_plan', 3,
        N'source_key', 0, 0, 0, 0, NULL),
    (N'recon.execution_reconciliation_result_archive',
        N'IX_recon_reconciliation_result_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_reconciliation_result_archive',
        N'IX_recon_reconciliation_result_archive_plan', 2,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_state_event_archive', N'IX_recon_execution_state_event_archive_plan',
        1, N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_state_event_archive', N'IX_recon_execution_state_event_archive_plan',
        2, N'state_event_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_page_audit_archive', N'IX_recon_execution_page_audit_archive_plan',
        1, N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_page_audit_archive', N'IX_recon_execution_page_audit_archive_plan',
        2, N'page_audit_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_count_archive', N'IX_recon_execution_count_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_count_archive', N'IX_recon_execution_count_archive_plan', 2,
        N'count_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_publication_event_archive',
        N'IX_recon_publication_event_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_publication_event_archive',
        N'IX_recon_publication_event_archive_plan', 2,
        N'execution_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_promotion_result_archive',
        N'IX_recon_promotion_result_archive_plan', 1,
        N'plan_id', 0, 0, 0, 0, NULL),
    (N'recon.execution_promotion_result_archive',
        N'IX_recon_promotion_result_archive_plan', 2,
        N'execution_id', 0, 0, 0, 0, NULL);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX_SHAPE', expected.index_name,
       N'PK/UQ/IX perdeu tipo, unicidade, filtro ou shape exato das chaves.'
FROM (
    SELECT object_name, index_name,
           MAX(CONVERT(INT, unique_index)) AS unique_index,
           MAX(CONVERT(INT, primary_key)) AS primary_key,
           MAX(CONVERT(INT, unique_constraint)) AS unique_constraint,
           MAX(CONVERT(INT, is_clustered)) AS is_clustered,
           MAX(filter_column) AS filter_column, COUNT_BIG(*) AS expected_key_count
    FROM @expected_index_keys
    GROUP BY object_name, index_name
) AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(expected.object_name, N'U')
      AND index_definition.name = expected.index_name
      AND index_definition.is_unique = expected.unique_index
      AND index_definition.is_disabled = 0
      AND index_definition.is_hypothetical = 0
      AND index_definition.is_primary_key = expected.primary_key
      AND index_definition.is_unique_constraint = expected.unique_constraint
      AND index_definition.type = CASE WHEN expected.is_clustered = 1 THEN 1 ELSE 2 END
      AND (expected.filter_column IS NULL
           OR (index_definition.has_filter = 1
               AND REPLACE(REPLACE(REPLACE(REPLACE(
                       index_definition.filter_definition,
                       N'[', N''), N']', N''), N'(', N''), N')', N'') =
                   expected.filter_column + N' IS NULL'))
      AND (expected.filter_column IS NOT NULL OR index_definition.has_filter = 0)
      AND (SELECT COUNT_BIG(*) FROM sys.index_columns AS actual_count
           WHERE actual_count.object_id = index_definition.object_id
             AND actual_count.index_id = index_definition.index_id
             AND actual_count.key_ordinal > 0) = expected.expected_key_count
      AND NOT EXISTS (
          SELECT 1 FROM sys.index_columns AS included_column
          WHERE included_column.object_id = index_definition.object_id
            AND included_column.index_id = index_definition.index_id
            AND included_column.is_included_column = 1
      )
      AND NOT EXISTS (
          SELECT 1
          FROM @expected_index_keys AS expected_key
          WHERE expected_key.object_name = expected.object_name
            AND expected_key.index_name = expected.index_name
            AND NOT EXISTS (
                SELECT 1
                FROM sys.index_columns AS actual_key
                INNER JOIN sys.columns AS actual_column
                    ON actual_column.object_id = actual_key.object_id
                   AND actual_column.column_id = actual_key.column_id
                WHERE actual_key.object_id = index_definition.object_id
                  AND actual_key.index_id = index_definition.index_id
                  AND actual_key.key_ordinal = expected_key.key_ordinal
                  AND actual_key.is_descending_key = 0
                  AND actual_column.name = expected_key.column_name
            )
      )
);

DECLARE @public_procedures TABLE (
    schema_name SYSNAME NOT NULL,
    procedure_name SYSNAME NOT NULL,
    role_name SYSNAME NOT NULL,
    PRIMARY KEY (schema_name, procedure_name)
);
INSERT INTO @public_procedures (schema_name, procedure_name, role_name)
VALUES
    (N'ctl', N'usp_approve_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_revoke_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_place_staging_legal_hold', N'v2_retention_governor'),
    (N'ctl', N'usp_release_staging_legal_hold', N'v2_retention_governor'),
    (N'stg', N'usp_plan_staging_lifecycle', N'v2_lifecycle_reviewer'),
    (N'stg', N'usp_archive_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'stg', N'usp_purge_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'recon', N'usp_restore_staging_archive', N'v2_archive_restorer');

DECLARE @internal_procedures TABLE (
    schema_name SYSNAME NOT NULL,
    procedure_name SYSNAME NOT NULL,
    PRIMARY KEY (schema_name, procedure_name)
);
INSERT INTO @internal_procedures (schema_name, procedure_name)
VALUES
    (N'stg', N'usp_plan_staging_lifecycle_at'),
    (N'recon', N'usp_compute_live_staging_content_root_internal'),
    (N'recon', N'usp_compute_archive_content_root_internal'),
    (N'recon', N'usp_verify_staging_archive_internal'),
    (N'recon', N'usp_verify_staging_restore_internal');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', CONCAT(expected.schema_name, N'.', expected.procedure_name),
       N'Procedure obrigatória do lifecycle ausente.'
FROM (
    SELECT schema_name, procedure_name FROM @public_procedures
    UNION ALL
    SELECT schema_name, procedure_name FROM @internal_procedures
) AS expected
WHERE OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P') IS NULL;

DECLARE @expected_fingerprint_parameters TABLE (
    object_name NVARCHAR(256) NOT NULL,
    parameter_name SYSNAME NOT NULL,
    PRIMARY KEY (object_name, parameter_name)
);
INSERT INTO @expected_fingerprint_parameters (object_name, parameter_name)
VALUES
    (N'ctl.usp_approve_staging_retention_policy', N'@policy_fingerprint'),
    (N'ctl.usp_approve_staging_retention_policy', N'@data_owner_evidence_fingerprint'),
    (N'ctl.usp_approve_staging_retention_policy', N'@compliance_evidence_fingerprint'),
    (N'ctl.usp_revoke_staging_retention_policy', N'@policy_fingerprint'),
    (N'ctl.usp_revoke_staging_retention_policy', N'@authority_evidence_fingerprint'),
    (N'ctl.usp_place_staging_legal_hold', N'@authority_evidence_fingerprint'),
    (N'ctl.usp_release_staging_legal_hold', N'@authority_evidence_fingerprint'),
    (N'stg.usp_plan_staging_lifecycle_at', N'@policy_fingerprint'),
    (N'stg.usp_plan_staging_lifecycle', N'@policy_fingerprint'),
    (N'stg.usp_archive_staging_lifecycle', N'@policy_fingerprint'),
    (N'stg.usp_purge_staging_lifecycle', N'@policy_fingerprint'),
    (N'stg.usp_purge_staging_lifecycle', N'@archive_fingerprint'),
    (N'recon.usp_restore_staging_archive', N'@archive_fingerprint');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PARAMETER', CONCAT(expected.object_name, expected.parameter_name),
       N'Fingerprint/evidência externa deve chegar como NVARCHAR(MAX), sem best-fit ANSI.'
FROM @expected_fingerprint_parameters AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.parameters AS parameter_definition
    INNER JOIN sys.types AS type_definition
        ON type_definition.user_type_id = parameter_definition.user_type_id
    WHERE parameter_definition.object_id = OBJECT_ID(expected.object_name, N'P')
      AND parameter_definition.name = expected.parameter_name
      AND type_definition.name = N'nvarchar'
      AND parameter_definition.max_length = -1
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'FUNCTION', N'recon.ufn_archive_row_attestation',
       N'Função criptográfica de atestado por linha ausente.'
WHERE OBJECT_ID(N'recon.ufn_archive_row_attestation', N'FN') IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'FUNCTION', N'ctl.ufn_invalid_staging_legal_hold',
       N'Função fail-closed de integridade do ledger de legal hold ausente.'
WHERE OBJECT_ID(N'ctl.ufn_invalid_staging_legal_hold', N'IF') IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'FUNCTION', N'ctl.ufn_invalid_execution_state_ledger',
       N'Função fail-closed da trilha contígua de estados ausente.'
WHERE OBJECT_ID(N'ctl.ufn_invalid_execution_state_ledger', N'IF') IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'TRIGGER', expected.trigger_name, N'Trigger de lineage ausente ou desabilitado.'
FROM (VALUES
    (N'recon.trg_quarantine_record_staging_lineage'),
    (N'recon.trg_candidate_application_staging_lineage'),
    (N'stg.trg_execution_record_lifecycle_delete_guard'),
    (N'stg.trg_execution_candidate_lifecycle_delete_guard')
) AS expected (trigger_name)
WHERE OBJECT_ID(expected.trigger_name, N'TR') IS NULL
   OR EXISTS (SELECT 1 FROM sys.triggers WHERE object_id = OBJECT_ID(expected.trigger_name)
              AND is_disabled = 1);

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', expected.constraint_name,
       N'Constraint ausente, desabilitada ou não confiável.'
FROM (VALUES
    (N'CK_ctl_execution_attempt_terminal_timestamp'),
    (N'CK_ctl_execution_attempt_transition_sequence_lifecycle_bound'),
    (N'CK_ctl_execution_state_event_lifecycle_bound'),
    (N'CK_ctl_staging_retention_policy_text'),
    (N'CK_ctl_staging_retention_policy_fingerprint'),
    (N'CK_ctl_staging_retention_policy_days'),
    (N'CK_ctl_staging_legal_hold_scope'),
    (N'CK_ctl_staging_legal_hold_release'),
    (N'CK_ctl_staging_legal_hold_event_scope'),
    (N'CK_ctl_staging_lifecycle_plan_limits'),
    (N'CK_recon_staging_lifecycle_archive_manifest_fingerprint'),
    (N'CK_ctl_staging_lifecycle_purge_event_counts'),
    (N'CK_recon_staging_restore_session_counts')
) AS expected (constraint_name)
LEFT JOIN sys.check_constraints AS definition
    ON definition.name = expected.constraint_name
WHERE definition.object_id IS NULL OR definition.is_disabled = 1 OR definition.is_not_trusted = 1;

DECLARE @terminal_constraint NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_execution_attempt_terminal_timestamp'
);
INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'CK_ctl_execution_attempt_terminal_timestamp',
       N'Constraint terminal não garante cronologia e estados sem carga.'
WHERE @terminal_constraint IS NULL
   OR @terminal_constraint NOT LIKE N'%terminal_at_utc%>=%started_at_utc%'
   OR @terminal_constraint NOT LIKE N'%SKIPPED%'
   OR @terminal_constraint NOT LIKE N'%NOT_APPLICABLE%';

DECLARE @attempt_sequence_bound NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_execution_attempt_transition_sequence_lifecycle_bound'
);
DECLARE @event_sequence_bound NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_execution_state_event_lifecycle_bound'
);
INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'ctl.execution_state_event.lifecycle_bound',
       N'Ledger de estados não está fisicamente limitado a sete eventos por execução.'
WHERE @attempt_sequence_bound IS NULL OR @event_sequence_bound IS NULL
   OR @attempt_sequence_bound NOT LIKE N'%next_transition_sequence%3%8%'
   OR @event_sequence_bound NOT LIKE N'%transition_sequence%1%7%';

DECLARE @policy_text_constraint NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_staging_retention_policy_text'
);
DECLARE @policy_fingerprint_constraint NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_staging_retention_policy_fingerprint'
);
INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'ctl.staging_retention_policy.independent_ratification',
       N'Dupla ratificação não exige evidências e papéis independentes em BIN2.'
WHERE @policy_text_constraint IS NULL OR @policy_fingerprint_constraint IS NULL
   OR @policy_text_constraint NOT LIKE
       N'%data_owner_role%Latin1_General_100_BIN2%<>%compliance_owner_role%'
   OR @policy_fingerprint_constraint NOT LIKE
       N'%data_owner_evidence_fingerprint%Latin1_General_100_BIN2%<>%compliance_evidence_fingerprint%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', N'ctl.staging_legal_hold.release_authority',
       N'Estado base não persiste evidência e papel exatos da liberação.'
WHERE NOT EXISTS (
          SELECT 1 FROM sys.columns
          WHERE object_id = OBJECT_ID(N'ctl.staging_legal_hold', N'U')
            AND name = N'release_authority_evidence_fingerprint'
            AND system_type_id = TYPE_ID(N'char') AND max_length = 64 AND is_nullable = 1
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys.columns
          WHERE object_id = OBJECT_ID(N'ctl.staging_legal_hold', N'U')
            AND name = N'release_owner_role'
            AND system_type_id = TYPE_ID(N'nvarchar') AND max_length = 256 AND is_nullable = 1
      );

DECLARE @hold_release_constraint NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_staging_legal_hold_release'
);
INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'CK_ctl_staging_legal_hold_release',
       N'Constraint não vincula timestamp, evidência e papel da liberação.'
WHERE @hold_release_constraint IS NULL
   OR @hold_release_constraint NOT LIKE N'%release_authority_evidence_fingerprint%'
   OR @hold_release_constraint NOT LIKE N'%release_owner_role%'
   OR @hold_release_constraint NOT LIKE N'%released_at_utc%IS NULL%'
   OR @hold_release_constraint NOT LIKE N'%released_at_utc%IS NOT NULL%';

DECLARE @plan_limit_constraint NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE name = N'CK_ctl_staging_lifecycle_plan_limits'
);
INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', N'CK_ctl_staging_lifecycle_plan_limits',
       N'Constraint do plano não vincula cursor, truncamento e próximo cursor.'
WHERE @plan_limit_constraint IS NULL
   OR @plan_limit_constraint NOT LIKE N'%scan_after_terminal_at_utc%'
   OR @plan_limit_constraint NOT LIKE N'%scan_after_execution_id%'
   OR @plan_limit_constraint NOT LIKE N'%next_scan_after_terminal_at_utc%'
   OR @plan_limit_constraint NOT LIKE N'%next_scan_after_execution_id%'
   OR @plan_limit_constraint NOT LIKE N'%scan_truncated%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', expected.index_name, N'Índice bounded/SARGable do lifecycle ausente.'
FROM (VALUES
    (N'ctl.execution_attempt', N'IX_ctl_execution_attempt_lifecycle'),
    (N'ctl.staging_legal_hold', N'IX_ctl_staging_legal_hold_execution'),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution'),
    (N'stg.execution_record', N'IX_stg_execution_record_lifecycle'),
    (N'recon.quarantine_record', N'IX_recon_quarantine_record_lifecycle'),
    (N'ctl.staging_lifecycle_plan_item', N'IX_ctl_staging_lifecycle_plan_item_execution'),
    (N'recon.quarantine_record_archive', N'IX_recon_quarantine_record_archive_plan'),
    (N'recon.execution_candidate_application_archive',
        N'IX_recon_candidate_application_archive_plan'),
    (N'recon.execution_reconciliation_result_archive',
        N'IX_recon_reconciliation_result_archive_plan'),
    (N'recon.execution_state_event_archive',
        N'IX_recon_execution_state_event_archive_plan'),
    (N'recon.execution_page_audit_archive',
        N'IX_recon_execution_page_audit_archive_plan'),
    (N'recon.execution_count_archive', N'IX_recon_execution_count_archive_plan'),
    (N'recon.execution_publication_event_archive',
        N'IX_recon_publication_event_archive_plan'),
    (N'recon.execution_promotion_result_archive',
        N'IX_recon_promotion_result_archive_plan')
) AS expected (table_name, index_name)
WHERE NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(expected.table_name, N'U')
      AND name = expected.index_name AND is_disabled = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', expected.index_name,
       N'Índice de ledger de legal hold não começa por execution/hold/action.'
FROM (VALUES
    (N'ctl.staging_legal_hold', N'IX_ctl_staging_legal_hold_execution', 1, N'execution_id'),
    (N'ctl.staging_legal_hold', N'IX_ctl_staging_legal_hold_execution', 2, N'hold_id'),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution',
        1, N'execution_id'),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution',
        2, N'hold_id'),
    (N'ctl.staging_legal_hold_event', N'IX_ctl_staging_legal_hold_event_execution',
        3, N'event_action')
) AS expected (table_name, index_name, key_ordinal, column_name)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    INNER JOIN sys.index_columns AS index_column
        ON index_column.object_id = index_definition.object_id
       AND index_column.index_id = index_definition.index_id
    INNER JOIN sys.columns AS column_definition
        ON column_definition.object_id = index_column.object_id
       AND column_definition.column_id = index_column.column_id
    WHERE index_definition.object_id = OBJECT_ID(expected.table_name, N'U')
      AND index_definition.name = expected.index_name
      AND index_column.key_ordinal = expected.key_ordinal
      AND column_definition.name = expected.column_name
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', index_definition.name,
       N'Índice de archive não começa por plan_id.'
FROM sys.indexes AS index_definition
WHERE index_definition.name IN (
        N'IX_recon_quarantine_record_archive_plan',
        N'IX_recon_candidate_application_archive_plan',
        N'IX_recon_reconciliation_result_archive_plan',
        N'IX_recon_execution_state_event_archive_plan',
        N'IX_recon_execution_page_audit_archive_plan',
        N'IX_recon_execution_count_archive_plan',
        N'IX_recon_publication_event_archive_plan',
        N'IX_recon_promotion_result_archive_plan'
    )
  AND NOT EXISTS (
      SELECT 1 FROM sys.index_columns AS index_column
      INNER JOIN sys.columns AS column_definition
          ON column_definition.object_id = index_column.object_id
         AND column_definition.column_id = index_column.column_id
      WHERE index_column.object_id = index_definition.object_id
        AND index_column.index_id = index_definition.index_id
        AND index_column.key_ordinal = 1
        AND column_definition.name = N'plan_id'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', N'IX_ctl_execution_attempt_lifecycle',
       N'Índice de seleção não começa por partition/state/terminal/execution.'
WHERE EXISTS (
    SELECT 1
    FROM (VALUES (1, N'partition_id'), (2, N'current_state'),
                 (3, N'terminal_at_utc'), (4, N'execution_id')) AS expected (ordinal, column_name)
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys.indexes AS index_definition
        INNER JOIN sys.index_columns AS index_column
            ON index_column.object_id = index_definition.object_id
           AND index_column.index_id = index_definition.index_id
        INNER JOIN sys.columns AS column_definition
            ON column_definition.object_id = index_column.object_id
           AND column_definition.column_id = index_column.column_id
        WHERE index_definition.object_id = OBJECT_ID(N'ctl.execution_attempt', N'U')
          AND index_definition.name = N'IX_ctl_execution_attempt_lifecycle'
          AND index_column.key_ordinal = expected.ordinal
          AND column_definition.name = expected.column_name
    )
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'FOREIGN_KEY', expected.constraint_name, N'FK antiga impede purge após archive tipado.'
FROM (VALUES
    (N'FK_recon_quarantine_record_stage'),
    (N'FK_recon_execution_candidate_application_candidate')
) AS expected (constraint_name)
WHERE EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = expected.constraint_name);

DECLARE @expected_foreign_key_columns TABLE (
    parent_object_name NVARCHAR(256) NOT NULL,
    constraint_name SYSNAME NOT NULL,
    constraint_column_id INT NOT NULL,
    parent_column_name SYSNAME NOT NULL,
    referenced_object_name NVARCHAR(256) NOT NULL,
    referenced_column_name SYSNAME NOT NULL,
    PRIMARY KEY (parent_object_name, constraint_name, constraint_column_id)
);
INSERT INTO @expected_foreign_key_columns (
    parent_object_name, constraint_name, constraint_column_id, parent_column_name,
    referenced_object_name, referenced_column_name
)
VALUES
    (N'ctl.staging_retention_policy', N'FK_ctl_staging_retention_policy_source', 1,
        N'source_instance', N'ctl.source_catalog', N'source_instance'),
    (N'ctl.staging_retention_policy_event',
        N'FK_ctl_staging_retention_policy_event_policy', 1,
        N'policy_id', N'ctl.staging_retention_policy', N'policy_id'),
    (N'ctl.staging_legal_hold', N'FK_ctl_staging_legal_hold_execution', 1,
        N'execution_id', N'ctl.execution_attempt', N'execution_id'),
    (N'ctl.staging_legal_hold_event', N'FK_ctl_staging_legal_hold_event_hold', 1,
        N'hold_id', N'ctl.staging_legal_hold', N'hold_id'),
    (N'ctl.staging_legal_hold_event', N'FK_ctl_staging_legal_hold_event_execution', 1,
        N'execution_id', N'ctl.execution_attempt', N'execution_id'),
    (N'ctl.staging_lifecycle_plan', N'FK_ctl_staging_lifecycle_plan_policy', 1,
        N'policy_id', N'ctl.staging_retention_policy', N'policy_id'),
    (N'ctl.staging_lifecycle_plan_item', N'FK_ctl_staging_lifecycle_plan_item_plan', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan', N'plan_id'),
    (N'ctl.staging_lifecycle_plan_item', N'FK_ctl_staging_lifecycle_plan_item_execution', 1,
        N'execution_id', N'ctl.execution_attempt', N'execution_id'),
    (N'recon.staging_lifecycle_archive_manifest',
        N'FK_recon_staging_lifecycle_archive_manifest_plan', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan', N'plan_id'),
    (N'recon.staging_record_archive', N'FK_recon_staging_record_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.staging_record_archive', N'FK_recon_staging_record_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.staging_record_archive', N'FK_recon_staging_record_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.staging_candidate_archive',
        N'FK_recon_staging_candidate_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.staging_candidate_archive',
        N'FK_recon_staging_candidate_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.staging_candidate_archive',
        N'FK_recon_staging_candidate_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.staging_candidate_archive', N'FK_recon_staging_candidate_archive_winner', 1,
        N'plan_id', N'recon.staging_record_archive', N'plan_id'),
    (N'recon.staging_candidate_archive', N'FK_recon_staging_candidate_archive_winner', 2,
        N'winner_stage_record_id', N'recon.staging_record_archive', N'stage_record_id'),
    (N'recon.quarantine_record_archive',
        N'FK_recon_quarantine_record_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.quarantine_record_archive',
        N'FK_recon_quarantine_record_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.quarantine_record_archive',
        N'FK_recon_quarantine_record_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.quarantine_record_archive', N'FK_recon_quarantine_record_archive_stage', 1,
        N'plan_id', N'recon.staging_record_archive', N'plan_id'),
    (N'recon.quarantine_record_archive', N'FK_recon_quarantine_record_archive_stage', 2,
        N'stage_record_id', N'recon.staging_record_archive', N'stage_record_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_candidate', 1,
        N'plan_id', N'recon.staging_candidate_archive', N'plan_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_candidate', 2,
        N'execution_id', N'recon.staging_candidate_archive', N'execution_id'),
    (N'recon.execution_candidate_application_archive',
        N'FK_recon_execution_candidate_application_archive_candidate', 3,
        N'source_key', N'recon.staging_candidate_archive', N'source_key'),
    (N'recon.execution_reconciliation_result_archive',
        N'FK_recon_execution_reconciliation_result_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_reconciliation_result_archive',
        N'FK_recon_execution_reconciliation_result_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_reconciliation_result_archive',
        N'FK_recon_execution_reconciliation_result_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_state_event_archive',
        N'FK_recon_execution_state_event_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_state_event_archive',
        N'FK_recon_execution_state_event_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_state_event_archive',
        N'FK_recon_execution_state_event_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_page_audit_archive',
        N'FK_recon_execution_page_audit_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_page_audit_archive',
        N'FK_recon_execution_page_audit_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_page_audit_archive',
        N'FK_recon_execution_page_audit_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_count_archive', N'FK_recon_execution_count_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_count_archive', N'FK_recon_execution_count_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_count_archive', N'FK_recon_execution_count_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_publication_event_archive',
        N'FK_recon_execution_publication_event_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_publication_event_archive',
        N'FK_recon_execution_publication_event_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_publication_event_archive',
        N'FK_recon_execution_publication_event_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.execution_promotion_result_archive',
        N'FK_recon_execution_promotion_result_archive_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.execution_promotion_result_archive',
        N'FK_recon_execution_promotion_result_archive_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.execution_promotion_result_archive',
        N'FK_recon_execution_promotion_result_archive_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'ctl.staging_lifecycle_purge_event',
        N'FK_ctl_staging_lifecycle_purge_event_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.staging_restore_session', N'FK_recon_staging_restore_session_manifest', 1,
        N'plan_id', N'recon.staging_lifecycle_archive_manifest', N'plan_id'),
    (N'recon.staging_restore_session', N'FK_recon_staging_restore_session_plan_item', 1,
        N'plan_id', N'ctl.staging_lifecycle_plan_item', N'plan_id'),
    (N'recon.staging_restore_session', N'FK_recon_staging_restore_session_plan_item', 2,
        N'execution_id', N'ctl.staging_lifecycle_plan_item', N'execution_id'),
    (N'recon.staging_restore_record', N'FK_recon_staging_restore_record_session', 1,
        N'restore_id', N'recon.staging_restore_session', N'restore_id'),
    (N'recon.staging_restore_candidate', N'FK_recon_staging_restore_candidate_session', 1,
        N'restore_id', N'recon.staging_restore_session', N'restore_id'),
    (N'recon.staging_restore_candidate', N'FK_recon_staging_restore_candidate_winner', 1,
        N'restore_id', N'recon.staging_restore_record', N'restore_id'),
    (N'recon.staging_restore_candidate', N'FK_recon_staging_restore_candidate_winner', 2,
        N'winner_stage_record_id', N'recon.staging_restore_record', N'stage_record_id');

INSERT INTO @failures (category, object_name, detail)
SELECT N'FOREIGN_KEY_SHAPE', expected.constraint_name,
       N'FK perdeu lineage, ordem, trust, habilitação ou NO_ACTION.'
FROM (
    SELECT parent_object_name, constraint_name, referenced_object_name,
           COUNT_BIG(*) AS expected_column_count
    FROM @expected_foreign_key_columns
    GROUP BY parent_object_name, constraint_name, referenced_object_name
) AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys AS foreign_key
    WHERE foreign_key.parent_object_id = OBJECT_ID(expected.parent_object_name, N'U')
      AND foreign_key.referenced_object_id = OBJECT_ID(expected.referenced_object_name, N'U')
      AND foreign_key.name = expected.constraint_name
      AND foreign_key.is_disabled = 0
      AND foreign_key.is_not_trusted = 0
      AND foreign_key.is_not_for_replication = 0
      AND foreign_key.update_referential_action = 0
      AND foreign_key.delete_referential_action = 0
      AND (SELECT COUNT_BIG(*) FROM sys.foreign_key_columns AS actual_count
           WHERE actual_count.constraint_object_id = foreign_key.object_id)
          = expected.expected_column_count
      AND NOT EXISTS (
          SELECT 1
          FROM @expected_foreign_key_columns AS expected_column
          WHERE expected_column.parent_object_name = expected.parent_object_name
            AND expected_column.constraint_name = expected.constraint_name
            AND NOT EXISTS (
                SELECT 1
                FROM sys.foreign_key_columns AS actual_column
                INNER JOIN sys.columns AS parent_column
                    ON parent_column.object_id = actual_column.parent_object_id
                   AND parent_column.column_id = actual_column.parent_column_id
                INNER JOIN sys.columns AS referenced_column
                    ON referenced_column.object_id = actual_column.referenced_object_id
                   AND referenced_column.column_id = actual_column.referenced_column_id
                WHERE actual_column.constraint_object_id = foreign_key.object_id
                  AND actual_column.constraint_column_id =
                      expected_column.constraint_column_id
                  AND parent_column.name = expected_column.parent_column_name
                  AND referenced_column.name = expected_column.referenced_column_name
            )
      )
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'FOREIGN_KEY_EXTRA', foreign_key.name,
       N'FK não declarada apareceu em tabela do lifecycle.'
FROM sys.foreign_keys AS foreign_key
WHERE EXISTS (
          SELECT 1 FROM @expected_foreign_key_columns AS expected_parent
          WHERE OBJECT_ID(expected_parent.parent_object_name, N'U') =
              foreign_key.parent_object_id
      )
  AND NOT EXISTS (
          SELECT 1 FROM @expected_foreign_key_columns AS expected_key
          WHERE expected_key.parent_object_name =
                    OBJECT_SCHEMA_NAME(foreign_key.parent_object_id) + N'.'
                    + OBJECT_NAME(foreign_key.parent_object_id)
            AND expected_key.constraint_name = foreign_key.name
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', CONCAT(expected.object_name, N'.', expected.column_name),
       N'Coluna crítica do contrato ausente.'
FROM (VALUES
    (N'ctl.staging_legal_hold', N'hold_scope'),
    (N'ctl.staging_legal_hold_event', N'hold_scope'),
    (N'ctl.staging_lifecycle_plan', N'scan_after_terminal_at_utc'),
    (N'ctl.staging_lifecycle_plan', N'scan_after_execution_id'),
    (N'ctl.staging_lifecycle_plan', N'next_scan_after_terminal_at_utc'),
    (N'ctl.staging_lifecycle_plan', N'next_scan_after_execution_id'),
    (N'ctl.staging_lifecycle_plan', N'maximum_examined_executions'),
    (N'ctl.staging_lifecycle_plan', N'maximum_evidence_rows'),
    (N'ctl.staging_lifecycle_plan', N'maximum_content_rows'),
    (N'ctl.staging_lifecycle_plan', N'maximum_probe_rows'),
    (N'ctl.staging_lifecycle_plan', N'planned_content_root_version'),
    (N'ctl.staging_lifecycle_plan', N'planned_content_root'),
    (N'recon.staging_lifecycle_archive_manifest', N'content_root_version'),
    (N'recon.staging_lifecycle_archive_manifest', N'content_root'),
    (N'recon.staging_restore_session', N'maximum_stage_rows'),
    (N'recon.staging_restore_session', N'maximum_candidate_rows')
) AS expected (object_name, column_name)
WHERE COL_LENGTH(expected.object_name, expected.column_name) IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLUMN', CONCAT(expected.table_name, N'.archive_row_attestation'),
       N'Archive tipado sem atestado criptográfico por linha.'
FROM (VALUES
    (N'recon.staging_record_archive'),
    (N'recon.staging_candidate_archive'),
    (N'recon.quarantine_record_archive'),
    (N'recon.execution_candidate_application_archive'),
    (N'recon.execution_reconciliation_result_archive'),
    (N'recon.execution_state_event_archive'),
    (N'recon.execution_page_audit_archive'),
    (N'recon.execution_count_archive'),
    (N'recon.execution_publication_event_archive'),
    (N'recon.execution_promotion_result_archive')
) AS expected (table_name)
WHERE COL_LENGTH(expected.table_name, N'archive_row_attestation') IS NULL;

DECLARE @plan_public NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_plan_staging_lifecycle', N'P'));
DECLARE @plan_core NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_plan_staging_lifecycle_at', N'P'));
DECLARE @archive NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_archive_staging_lifecycle', N'P'));
DECLARE @purge NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_purge_staging_lifecycle', N'P'));
DECLARE @purge_delete_statement_count INT = CASE WHEN @purge IS NULL THEN NULL ELSE
    (LEN(UPPER(@purge)) - LEN(REPLACE(UPPER(@purge), N'DELETE', N''))) / LEN(N'DELETE') END;
DECLARE @restore NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_restore_staging_archive', N'P'));
DECLARE @archive_verifier NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_verify_staging_archive_internal', N'P'));
DECLARE @restore_verifier NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_verify_staging_restore_internal', N'P'));
DECLARE @live_root NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_compute_live_staging_content_root_internal', N'P'));
DECLARE @archive_root NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.usp_compute_archive_content_root_internal', N'P'));
DECLARE @place_hold NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_place_staging_legal_hold', N'P'));
DECLARE @release_hold NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_release_staging_legal_hold', N'P'));
DECLARE @approve_policy NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_approve_staging_retention_policy', N'P'));
DECLARE @revoke_policy NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_revoke_staging_retention_policy', N'P'));
DECLARE @hold_integrity NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.ufn_invalid_staging_legal_hold', N'IF'));
DECLARE @state_ledger_integrity NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'ctl.ufn_invalid_execution_state_ledger', N'IF'));
DECLARE @record_delete_guard NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.trg_execution_record_lifecycle_delete_guard', N'TR'));
DECLARE @candidate_delete_guard NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'stg.trg_execution_candidate_lifecycle_delete_guard', N'TR'));
DECLARE @extension_budget NVARCHAR(MAX) =
    OBJECT_DEFINITION(OBJECT_ID(N'recon.ufn_staging_lifecycle_extension_archive_budget', N'IF'));

INSERT INTO @failures (category, object_name, detail)
SELECT N'VISIBILITY', expected.object_name, N'Definição SQL indisponível; gate falha fechado.'
FROM (VALUES
    (N'stg.usp_plan_staging_lifecycle', @plan_public),
    (N'stg.usp_plan_staging_lifecycle_at', @plan_core),
    (N'stg.usp_archive_staging_lifecycle', @archive),
    (N'stg.usp_purge_staging_lifecycle', @purge),
    (N'recon.usp_restore_staging_archive', @restore),
    (N'recon.usp_verify_staging_archive_internal', @archive_verifier),
    (N'recon.usp_verify_staging_restore_internal', @restore_verifier),
    (N'recon.usp_compute_live_staging_content_root_internal', @live_root),
    (N'recon.usp_compute_archive_content_root_internal', @archive_root),
    (N'ctl.usp_approve_staging_retention_policy', @approve_policy),
    (N'ctl.usp_revoke_staging_retention_policy', @revoke_policy),
    (N'ctl.usp_place_staging_legal_hold', @place_hold),
    (N'ctl.usp_release_staging_legal_hold', @release_hold),
    (N'ctl.ufn_invalid_staging_legal_hold', @hold_integrity),
    (N'ctl.ufn_invalid_execution_state_ledger', @state_ledger_integrity),
    (N'stg.trg_execution_record_lifecycle_delete_guard', @record_delete_guard),
    (N'stg.trg_execution_candidate_lifecycle_delete_guard', @candidate_delete_guard),
    (N'recon.ufn_staging_lifecycle_extension_archive_budget', @extension_budget)
) AS expected (object_name, definition)
WHERE expected.definition IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', CONCAT(public_entry.schema_name, N'.', public_entry.procedure_name),
       N'Entry point público não compartilha o applock transacional com principal explícito.'
FROM @public_procedures AS public_entry
WHERE OBJECT_DEFINITION(OBJECT_ID(CONCAT(
          public_entry.schema_name, N'.', public_entry.procedure_name), N'P')) NOT LIKE
          N'%V2_STAGING_LIFECYCLE%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(
          public_entry.schema_name, N'.', public_entry.procedure_name), N'P')) NOT LIKE
          N'%@LockOwner = N''Transaction''%'
   OR OBJECT_DEFINITION(OBJECT_ID(CONCAT(
          public_entry.schema_name, N'.', public_entry.procedure_name), N'P')) NOT LIKE
          N'%@DbPrincipal = N''public''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.usp_place_staging_legal_hold',
       N'Legal hold não distingue proteção de staging+archive e archive pós-purge.'
WHERE @place_hold IS NULL
   OR @place_hold NOT LIKE N'%STAGING_AND_ARCHIVE%'
   OR @place_hold NOT LIKE N'%ARCHIVE_ONLY%'
   OR @place_hold NOT LIKE N'%staging_lifecycle_purge_event%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.staging_legal_hold_ledger',
       N'Legal hold não vincula estado, PLACED/RELEASED e retries pelo ledger exato.'
WHERE @hold_integrity IS NULL OR @release_hold IS NULL
   OR @hold_integrity NOT LIKE N'%placed_event.occurred_at_utc = hold_definition.held_at_utc%'
   OR @hold_integrity NOT LIKE N'%released_event.occurred_at_utc = hold_definition.released_at_utc%'
   OR @hold_integrity NOT LIKE N'%hold_definition.release_authority_evidence_fingerprint%'
   OR @hold_integrity NOT LIKE N'%hold_definition.release_owner_role%'
   OR @place_hold NOT LIKE N'%placed_event.owner_role = hold_definition.owner_role%'
   OR @release_hold NOT LIKE N'%release_authority_evidence_fingerprint = @authority_evidence_fingerprint%'
   OR @release_hold NOT LIKE N'%release_owner_role = @owner_role%'
   OR @place_hold NOT LIKE N'%ctl.ufn_invalid_staging_legal_hold(@execution_id)%'
   OR CHARINDEX(N'ctl.ufn_invalid_staging_legal_hold(@execution_id)', @place_hold) >
       CHARINDEX(N'CAST(1 AS BIT) AS exact_retry', @place_hold)
   OR @release_hold NOT LIKE N'%ctl.ufn_invalid_staging_legal_hold(@execution_id)%'
   OR @release_hold NOT LIKE N'%occurred_at_utc = @released_at_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.ufn_invalid_execution_state_ledger',
       N'Trilha terminal não exige origem, grafo, cardinalidade, relógio e último evento exatos.'
WHERE @state_ledger_integrity IS NULL
   OR @state_ledger_integrity NOT LIKE N'%@execution_id UNIQUEIDENTIFIER%'
   OR @state_ledger_integrity NOT LIKE N'%attempt.execution_id = @execution_id%'
   OR @state_ledger_integrity NOT LIKE N'%transition_sequence < 1%'
   OR @state_ledger_integrity NOT LIKE
       N'%transition_sequence >=%attempt.next_transition_sequence%'
   OR @state_ledger_integrity NOT LIKE
       N'%COUNT_BIG(*)%attempt.next_transition_sequence - 1%'
   OR @state_ledger_integrity NOT LIKE N'%first_event.previous_state IS NULL%'
   OR @state_ledger_integrity NOT LIKE N'%first_event.next_state = N''PLANNED''%'
   OR @state_ledger_integrity NOT LIKE N'%first_event.reason_code = N''EXECUTION_PLANNED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%first_event.transitioned_at_utc = attempt.started_at_utc%'
   OR @state_ledger_integrity NOT LIKE N'%lease_event.transition_sequence = 2%'
   OR @state_ledger_integrity NOT LIKE N'%lease_event.previous_state = N''PLANNED''%'
   OR @state_ledger_integrity NOT LIKE N'%lease_event.next_state = N''EXTRACTING''%'
   OR @state_ledger_integrity NOT LIKE N'%lease_event.reason_code = N''LEASE_ACQUIRED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%lease_event.transitioned_at_utc = attempt.started_at_utc%'
   OR @state_ledger_integrity NOT LIKE
       N'%previous_event.next_state = current_event.previous_state%'
   OR @state_ledger_integrity NOT LIKE
       N'%previous_event.transitioned_at_utc <=%current_event.transitioned_at_utc%'
   OR @state_ledger_integrity NOT LIKE
       N'%semantic_event.previous_state = N''EXTRACTING''%semantic_event.next_state IN%'
   OR @state_ledger_integrity NOT LIKE
       N'%semantic_event.previous_state = N''STAGED''%semantic_event.next_state = N''PROMOTED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%semantic_event.reason_code = N''CANDIDATE_SET_PREPARED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%semantic_event.reason_code = N''CANDIDATE_SET_RECONCILED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%semantic_event.reason_code = N''RECONCILIATION_PUBLISHED''%'
   OR @state_ledger_integrity NOT LIKE
       N'%terminal_event.next_state = attempt.current_state%'
   OR @state_ledger_integrity NOT LIKE
       N'%terminal_event.transitioned_at_utc = attempt.terminal_at_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.staging_legal_hold.bounded_ledger',
       N'Ledger de legal hold não está parametrizado, indexado e limitado por TOP/orçamento.'
WHERE @hold_integrity IS NULL
   OR @hold_integrity NOT LIKE N'%@execution_id UNIQUEIDENTIFIER%'
   OR @hold_integrity NOT LIKE N'%hold_definition.execution_id = @execution_id%'
   OR @place_hold NOT LIKE N'%@maximum_hold_ledger_rows_per_execution BIGINT = 4096%'
   OR @place_hold NOT LIKE N'%TOP (@maximum_hold_ledger_rows_per_execution + 1)%'
   OR @place_hold NOT LIKE
       N'%@hold_ledger_base_rows + @hold_ledger_event_rows >%@maximum_hold_ledger_rows_per_execution - 3%'
   OR @release_hold NOT LIKE N'%@maximum_hold_ledger_rows_per_execution BIGINT = 4096%'
   OR @release_hold NOT LIKE N'%TOP (@remaining_hold_ledger_rows + 1)%'
   OR @release_hold NOT LIKE
       N'%@hold_ledger_base_rows + @hold_ledger_event_rows >=%@maximum_hold_ledger_rows_per_execution%'
   OR @plan_core NOT LIKE N'%TOP (@maximum_probe_rows + 1)%'
   OR @plan_core NOT LIKE N'%TOP (@remaining_hold_ledger_probe_rows + 1)%'
   OR @plan_core NOT LIKE
       N'%@remaining_hold_ledger_probe_rows BIGINT =%@maximum_probe_rows - @hold_ledger_base_probe_rows%'
   OR @plan_core NOT LIKE
       N'%@hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows%'
   OR @archive NOT LIKE N'%TOP (@maximum_probe_rows + 1)%'
   OR @archive NOT LIKE N'%TOP (@remaining_hold_ledger_probe_rows + 1)%'
   OR @archive NOT LIKE
       N'%@remaining_hold_ledger_probe_rows BIGINT =%@maximum_probe_rows - @hold_ledger_base_probe_rows%'
   OR @archive NOT LIKE
       N'%@hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows%'
   OR @purge NOT LIKE N'%TOP (@maximum_probe_rows + 1)%'
   OR @purge NOT LIKE N'%TOP (@remaining_hold_ledger_probe_rows + 1)%'
   OR @purge NOT LIKE
       N'%@remaining_hold_ledger_probe_rows BIGINT =%@maximum_probe_rows - @hold_ledger_base_probe_rows%'
   OR @purge NOT LIKE
       N'%@hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'archive-content-set-v1',
       N'Helpers de conteúdo vivo/archive não compartilham o mesmo domínio criptográfico.'
WHERE @live_root IS NULL OR @archive_root IS NULL
   OR @live_root NOT LIKE N'%archive-content-set-v1|%'
   OR @archive_root NOT LIKE N'%archive-content-set-v1|%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.lifecycle_delete_guards',
       N'Delete direto em staging não exige archive tipado correspondente.'
WHERE @record_delete_guard IS NULL OR @candidate_delete_guard IS NULL
   OR @record_delete_guard NOT LIKE N'%staging_record_archive%'
   OR @candidate_delete_guard NOT LIKE N'%staging_candidate_archive%'
   OR @record_delete_guard NOT LIKE N'%THROW%'
   OR @candidate_delete_guard NOT LIKE N'%THROW%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.usp_plan_staging_lifecycle',
       N'Entry point do plano não usa lock público comum, relógio SQL e core interno.'
WHERE @plan_public IS NULL
   OR @plan_public NOT LIKE N'%V2_STAGING_LIFECYCLE%'
   OR @plan_public NOT LIKE N'%@DbPrincipal = N''public''%'
   OR @plan_public NOT LIKE N'%SYSUTCDATETIME()%'
   OR @plan_public NOT LIKE N'%usp_plan_staging_lifecycle_at%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.usp_approve_staging_retention_policy',
       N'Retry da policy não vincula os dois eventos à identidade ratificada.'
WHERE @approve_policy IS NULL
   OR @approve_policy NOT LIKE N'%owner_event.authority_evidence_fingerprint%policy.data_owner_evidence_fingerprint%'
   OR @approve_policy NOT LIKE N'%owner_event.owner_role = policy.data_owner_role%'
   OR @approve_policy NOT LIKE N'%compliance_event.authority_evidence_fingerprint%policy.compliance_evidence_fingerprint%'
   OR @approve_policy NOT LIKE N'%compliance_event.owner_role = policy.compliance_owner_role%'
   OR @approve_policy NOT LIKE N'%revoked_event.event_action = N''REVOKED''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'ctl.usp_revoke_staging_retention_policy',
       N'Retry de revogação não vincula o evento ao timestamp selado.'
WHERE @revoke_policy IS NULL
   OR @revoke_policy NOT LIKE N'%occurred_at_utc = @revoked_at_utc%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.usp_plan_staging_lifecycle_at',
       N'Core do plano não prova ratificação, cutoff fechado, holds, leases e admissão limitada.'
WHERE @plan_core IS NULL
   OR @plan_core NOT LIKE N'%DATA_OWNER_APPROVED%'
   OR @plan_core NOT LIKE N'%COMPLIANCE_APPROVED%'
   OR @plan_core NOT LIKE N'%owner_event.authority_evidence_fingerprint%policy.data_owner_evidence_fingerprint%'
   OR @plan_core NOT LIKE N'%owner_event.owner_role = policy.data_owner_role%'
   OR @plan_core NOT LIKE N'%compliance_event.authority_evidence_fingerprint%policy.compliance_evidence_fingerprint%'
   OR @plan_core NOT LIKE N'%compliance_event.owner_role = policy.compliance_owner_role%'
   OR @plan_core NOT LIKE N'%revoked_event.event_action = N''REVOKED''%'
   OR @plan_core NOT LIKE N'%terminal_at_utc <= @cutoff_at_utc%'
   OR @plan_core NOT LIKE N'%@scan_after_terminal_at_utc%'
   OR @plan_core NOT LIKE N'%attempt.execution_id > @scan_after_execution_id%'
   OR @plan_core NOT LIKE N'%next_scan_after_terminal_at_utc%'
   OR @plan_core NOT LIKE N'%maximum_probe_rows%'
   OR @plan_core NOT LIKE N'%maximum_content_rows%'
   OR @plan_core NOT LIKE N'%ctl.ufn_invalid_staging_legal_hold(examined.execution_id)%'
   OR @plan_core NOT LIKE
       N'%ctl.ufn_invalid_execution_state_ledger(examined.execution_id)%'
   OR @plan_core NOT LIKE N'%CONVERT(DECIMAL(38, 0), stage_summary.stage_rows)%'
   OR @plan_core NOT LIKE N'%released_at_utc IS NULL%'
   OR @plan_core NOT LIKE N'%usp_compute_live_staging_content_root_internal%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'recon.ufn_staging_lifecycle_extension_archive_budget',
       N'Budget vertical não participa da classificação antes do TOP e dos cumulativos.'
WHERE @plan_core IS NULL OR @extension_budget IS NULL
   OR @plan_core NOT LIKE N'%extension_budget.additional_archive_bytes%'
   OR @plan_core NOT LIKE N'%extension_budget.extension_rows > @maximum_stage_rows%'
   OR @plan_core NOT LIKE N'%CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget%'
   OR CHARINDEX(N'CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget', @plan_core) = 0
   OR CHARINDEX(N'SELECT TOP (@maximum_executions)', @plan_core) = 0
   OR CHARINDEX(N'CROSS APPLY recon.ufn_staging_lifecycle_extension_archive_budget', @plan_core) >=
      CHARINDEX(N'SELECT TOP (@maximum_executions)', @plan_core);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.usp_archive_staging_lifecycle',
       N'Archive não cobre dez fontes, locks, roots e verificação independente.'
WHERE @archive IS NULL
   OR @archive NOT LIKE N'%INSERT INTO recon.staging_record_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.staging_candidate_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.quarantine_record_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_candidate_application_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_reconciliation_result_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_state_event_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_page_audit_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_count_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_publication_event_archive%'
   OR @archive NOT LIKE N'%INSERT INTO recon.execution_promotion_result_archive%'
   OR @archive NOT LIKE N'%usp_compute_live_staging_content_root_internal%'
   OR @archive NOT LIKE N'%usp_verify_staging_archive_internal%'
   OR @archive NOT LIKE N'%owner_event.authority_evidence_fingerprint%policy.data_owner_evidence_fingerprint%'
   OR @archive NOT LIKE N'%compliance_event.authority_evidence_fingerprint%policy.compliance_evidence_fingerprint%'
   OR @archive NOT LIKE N'%revoked_event.event_action = N''REVOKED''%'
   OR @archive NOT LIKE N'%ctl.ufn_invalid_staging_legal_hold(item.execution_id)%'
   OR @archive NOT LIKE
       N'%ctl.ufn_invalid_execution_state_ledger(attempt.execution_id)%'
   OR @archive NOT LIKE N'%WITH (UPDLOCK, HOLDLOCK)%'
   OR @archive NOT LIKE N'%@DbPrincipal = N''public''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'recon.usp_verify_staging_archive_internal',
       N'Verificador não recalcula attestations, content root e fingerprint do manifesto.'
WHERE @archive_verifier IS NULL
   OR @archive_verifier NOT LIKE N'%ufn_archive_row_attestation%'
   OR @archive_verifier NOT LIKE N'%usp_compute_archive_content_root_internal%'
   OR @archive_verifier NOT LIKE N'%planned_content_root%'
   OR @archive_verifier NOT LIKE N'%staging-archive-manifest-v3%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'stg.usp_purge_staging_lifecycle',
       N'Purge não está selado, limitado ao staging e protegido por hold/lock/root.'
WHERE @purge IS NULL
   OR @purge NOT LIKE N'%archive_fingerprint%'
   OR @purge NOT LIKE N'%usp_compute_live_staging_content_root_internal%'
   OR @purge NOT LIKE N'%usp_verify_staging_archive_internal%'
   OR @purge NOT LIKE N'%owner_event.authority_evidence_fingerprint%policy.data_owner_evidence_fingerprint%'
   OR @purge NOT LIKE N'%compliance_event.authority_evidence_fingerprint%policy.compliance_evidence_fingerprint%'
   OR @purge NOT LIKE N'%revoked_event.event_action = N''REVOKED''%'
   OR @purge NOT LIKE N'%ctl.ufn_invalid_staging_legal_hold(item.execution_id)%'
   OR @purge NOT LIKE
       N'%ctl.ufn_invalid_execution_state_ledger(attempt.execution_id)%'
   OR @purge NOT LIKE N'%Retry de purge encontrou staging reaparecido%'
   OR @purge NOT LIKE N'%DELETE stage_candidate%'
   OR @purge NOT LIKE N'%DELETE stage_record%'
   OR @purge_delete_statement_count <> 2
   OR CHARINDEX(N'DELETE stage_candidate', @purge) > CHARINDEX(N'DELETE stage_record', @purge)
   OR @purge LIKE N'%DELETE FROM core.%'
   OR @purge LIKE N'%DELETE FROM recon.%'
   OR @purge LIKE N'%DELETE FROM ctl.%'
   OR @purge LIKE N'%TRUNCATE%'
   OR @purge LIKE N'%CASCADE%'
   OR @purge NOT LIKE N'%@DbPrincipal = N''public''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'recon.usp_restore_staging_archive',
       N'Restore não é read-only, limitado, selado, persistente único e verificável em retry.'
WHERE @restore IS NULL
   OR @restore NOT LIKE N'%INSERT INTO recon.staging_restore_record%'
   OR @restore NOT LIKE N'%INSERT INTO recon.staging_restore_candidate%'
   OR @restore NOT LIKE N'%maximum_stage_rows = @maximum_stage_rows%'
   OR @restore NOT LIKE N'%usp_verify_staging_archive_internal%'
   OR @restore NOT LIKE N'%usp_verify_staging_restore_internal%'
   OR @restore NOT LIKE N'%CAST(1 AS BIT) AS read_only%'
   OR @restore LIKE N'%INSERT INTO stg.%'
   OR @restore LIKE N'%UPDATE stg.%'
   OR @restore LIKE N'%DELETE%stg.%'
   OR @restore NOT LIKE N'%@DbPrincipal = N''public''%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'ATTACK_SURFACE', unexpected.object_name,
       N'Restore read-only não pode expor conteúdo por entry point sem receipt de leitura.'
FROM (VALUES
    (N'recon.usp_read_staging_restore_record_page'),
    (N'recon.usp_read_staging_restore_candidate_page')
) AS unexpected (object_name)
WHERE OBJECT_ID(unexpected.object_name, N'P') IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'recon.usp_verify_staging_restore_internal',
       N'Retry de restore não executa read-back bidirecional e bounded.'
WHERE @restore_verifier IS NULL
   OR @restore_verifier NOT LIKE N'%TOP (@stage_rows + 1)%'
   OR @restore_verifier NOT LIKE N'%EXCEPT%';

DECLARE @lifecycle_roles TABLE (role_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @lifecycle_roles (role_name)
VALUES (N'v2_retention_governor'), (N'v2_lifecycle_reviewer'),
       (N'v2_lifecycle_operator'), (N'v2_archive_restorer');

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE', expected.role_name, N'Role de lifecycle ausente ou não é DATABASE_ROLE.'
FROM @lifecycle_roles AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.database_principals
    WHERE name = expected.role_name AND type = N'R'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(expected.role_name, N':', expected.schema_name, N'.',
                            expected.procedure_name), N'Grant EXECUTE mínimo ausente.'
FROM @public_procedures AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = expected.role_name
      AND permission_definition.class = 1
      AND permission_definition.major_id =
          OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P')
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state = 'G'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':',
                            OBJECT_SCHEMA_NAME(permission_definition.major_id), N'.',
                            OBJECT_NAME(permission_definition.major_id)),
       N'Grant em entry point não corresponde ao mapping role/procedure exato.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE permission_definition.class = 1
  AND permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN ('G', 'W')
  AND EXISTS (
      SELECT 1 FROM @public_procedures AS public_entry
      WHERE permission_definition.major_id =
          OBJECT_ID(CONCAT(public_entry.schema_name, N'.', public_entry.procedure_name), N'P')
  )
  AND NOT EXISTS (
      SELECT 1 FROM @public_procedures AS expected
      WHERE expected.role_name = principal_definition.name
        AND permission_definition.major_id =
            OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P')
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':BROAD_EXECUTE'),
       N'Grant EXECUTE amplo por database/schema expõe entry points de lifecycle.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN ('G', 'W')
  AND principal_definition.name <> N'v2_migrator'
  AND (permission_definition.class = 0
       OR (permission_definition.class = 3
           AND permission_definition.major_id IN (
               SCHEMA_ID(N'ctl'), SCHEMA_ID(N'stg'), SCHEMA_ID(N'recon')
           )));

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':EXECUTE_DENY'),
       N'DENY ancestral torna ineficaz o grant mínimo de lifecycle.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
INNER JOIN @lifecycle_roles AS lifecycle_role
    ON lifecycle_role.role_name = principal_definition.name
WHERE permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state = 'D'
  AND (permission_definition.class = 0
       OR (permission_definition.class = 3
           AND permission_definition.major_id IN (
               SCHEMA_ID(N'ctl'), SCHEMA_ID(N'stg'), SCHEMA_ID(N'recon')
           ))
       OR (permission_definition.class = 1 AND EXISTS (
           SELECT 1 FROM @public_procedures AS public_entry
           WHERE permission_definition.major_id = OBJECT_ID(CONCAT(
               public_entry.schema_name, N'.', public_entry.procedure_name), N'P')
       )));

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':DIRECT_DATA'),
       N'Principal operacional recebeu acesso direto a dados do lifecycle.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name IN (
        N'public', N'v2_runtime', N'v2_retention_governor',
        N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer'
    )
  AND permission_definition.state IN ('G', 'W')
  AND permission_definition.permission_name IN (
      N'SELECT', N'INSERT', N'UPDATE', N'DELETE', N'CONTROL', N'ALTER', N'TAKE OWNERSHIP'
  )
  AND (
      permission_definition.class = 0
      OR (permission_definition.class = 3
          AND permission_definition.major_id IN (
              SCHEMA_ID(N'ctl'), SCHEMA_ID(N'stg'), SCHEMA_ID(N'recon')
          ))
      OR (permission_definition.class = 1 AND EXISTS (
          SELECT 1 FROM @expected_tables AS lifecycle_table
          WHERE permission_definition.major_id = OBJECT_ID(CONCAT(
              lifecycle_table.schema_name, N'.', lifecycle_table.table_name), N'U')
      ))
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':',
                            OBJECT_SCHEMA_NAME(permission_definition.major_id), N'.',
                            OBJECT_NAME(permission_definition.major_id)),
       N'Helper interno do lifecycle recebeu grant explícito.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE permission_definition.class = 1
  AND permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN ('G', 'W')
  AND EXISTS (
      SELECT 1 FROM @internal_procedures AS internal_entry
      WHERE permission_definition.major_id =
          OBJECT_ID(CONCAT(internal_entry.schema_name, N'.', internal_entry.procedure_name), N'P')
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':', permission_definition.permission_name),
       N'Role de lifecycle possui permissão além dos EXECUTE de objeto mapeados.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
INNER JOIN @lifecycle_roles AS lifecycle_role
    ON lifecycle_role.role_name = principal_definition.name
WHERE permission_definition.state IN ('G', 'W')
  AND NOT (
      permission_definition.class = 0
      AND permission_definition.permission_name = N'CONNECT'
      AND permission_definition.state = 'G'
  )
  AND NOT EXISTS (
      SELECT 1 FROM @public_procedures AS expected
      WHERE expected.role_name = principal_definition.name
        AND permission_definition.class = 1
        AND permission_definition.major_id =
            OBJECT_ID(CONCAT(expected.schema_name, N'.', expected.procedure_name), N'P')
        AND permission_definition.permission_name = N'EXECUTE'
        AND permission_definition.state = 'G'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_MEMBERSHIP', CONCAT(member_definition.name, N'->', role_definition.name),
       N'Role de lifecycle não pode herdar outra role, inclusive db_owner.'
FROM sys.database_role_members AS membership
INNER JOIN sys.database_principals AS role_definition
    ON role_definition.principal_id = membership.role_principal_id
INNER JOIN sys.database_principals AS member_definition
    ON member_definition.principal_id = membership.member_principal_id
INNER JOIN @lifecycle_roles AS lifecycle_role
    ON lifecycle_role.role_name = member_definition.name;

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_MEMBERSHIP', CONCAT(member_definition.name, N'->', role_definition.name),
       N'Role de lifecycle não pode possuir membros semeados; associação exige gate externo.'
FROM sys.database_role_members AS membership
INNER JOIN sys.database_principals AS role_definition
    ON role_definition.principal_id = membership.role_principal_id
INNER JOIN sys.database_principals AS member_definition
    ON member_definition.principal_id = membership.member_principal_id
INNER JOIN @lifecycle_roles AS lifecycle_role
    ON lifecycle_role.role_name = role_definition.name;

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51580, N'Lifecycle governado de staging divergente do manifesto.', 1;
END;

PRINT N'Lifecycle governado de staging V2 validado com sucesso.';

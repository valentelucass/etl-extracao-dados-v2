-- Prepared readback only. Run before/after a separately authorized V105 gate.
-- Returns aggregate metadata; no payload, business identifier or module body.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit
SET NOCOUNT ON;
IF DB_NAME() <> N'$(DatabaseName)'
    THROW 54080, N'V105_SNAPSHOT_EXACT_SHADOW_REQUIRED', 1;
IF CONNECTIONPROPERTY('net_transport') <> N'Shared memory'
    OR CONNECTIONPROPERTY('auth_scheme') NOT IN (N'NTLM', N'KERBEROS')
    THROW 54081, N'V105_SNAPSHOT_LOCAL_WINDOWS_REQUIRED', 1;

SELECT N'EPOCH_SNAPSHOT' AS marker,
    (SELECT COUNT_BIG(*) FROM ctl.flyway_schema_history) AS history_rows,
    (SELECT COUNT_BIG(*) FROM ctl.flyway_schema_history WHERE success = 1 AND type = N'SQL') AS sql_migrations,
    (SELECT COUNT_BIG(*) FROM ctl.flyway_schema_history WHERE success = 0) AS failed_migrations,
    (SELECT COUNT_BIG(*) FROM sys.objects WHERE is_ms_shipped = 0) AS user_objects,
    (SELECT COUNT_BIG(*) FROM sys.tables WHERE is_ms_shipped = 0) AS user_tables,
    (SELECT COALESCE(SUM(p.rows), 0) FROM sys.tables AS t
        JOIN sys.partitions AS p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
        WHERE t.is_ms_shipped = 0) AS all_table_rows,
    (SELECT COUNT_BIG(*) FROM sys.schemas WHERE name IN
        (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
        AND principal_id = DATABASE_PRINCIPAL_ID(N'v2_schema_owner')) AS v2_owned_schemas,
    (SELECT COUNT_BIG(*) FROM sys.database_principals WHERE name = N'v2_schema_owner'
        OR name LIKE N'v2[_]%') AS v2_principals;

SELECT N'V105_AGGREGATES' AS marker,
    (SELECT COUNT_BIG(*) FROM ctl.execution_audit) AS audit_rows,
    (SELECT COUNT_BIG(*) FROM ctl.page_audit) AS page_audit_rows,
    (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(status))
        + COALESCE(DATALENGTH(traversal_verification), 0)
        + COALESCE(DATALENGTH(failure_category), 0)), 0)
        FROM ctl.execution_audit) AS audit_text_bytes,
    (SELECT COUNT_BIG(*) FROM ref.expansion_lab_label) AS label_rows,
    (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(label))), 0)
        FROM ref.expansion_lab_label) AS label_text_bytes,
    (SELECT COUNT_BIG(*) FROM ref.reference_release
        WHERE family_code = N'EXPANSION_LABELS') AS expansion_release_rows,
    (SELECT COUNT_BIG(*) FROM ref.reference_import_receipt AS receipt
        JOIN ref.reference_release AS release_definition
          ON release_definition.reference_release_id = receipt.reference_release_id
        WHERE release_definition.family_code = N'EXPANSION_LABELS') AS expansion_receipt_rows;

SELECT N'V105_COLLATION' AS marker,
    (SELECT COUNT_BIG(*) FROM sys.columns AS c
        JOIN sys.tables AS t ON t.object_id = c.object_id
        JOIN sys.schemas AS s ON s.schema_id = t.schema_id
        WHERE s.name IN (N'ctl', N'ref') AND c.system_type_id = TYPE_ID(N'nvarchar')
          AND c.collation_name <> N'Latin1_General_100_BIN2'
          AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history')) AS persisted_non_bin2,
    (SELECT COUNT_BIG(*) FROM sys.table_types AS t
        JOIN sys.columns AS c ON c.object_id = t.type_table_object_id
        WHERE t.user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
          AND c.name = N'label' AND c.collation_name = N'Latin1_General_100_BIN2') AS tvp_label_bin2,
    (SELECT COUNT_BIG(*) FROM sys.database_permissions AS p
        WHERE (p.class = 1 AND p.major_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P'))
           OR (p.class = 6 AND p.major_id = TYPE_ID(N'ref.expansion_lab_label_batch'))) AS direct_proc_type_permissions;

SELECT N'V105_REVIEWED_DEFINITIONS' AS marker,
    (SELECT COUNT_BIG(*) FROM sys.sql_modules
        WHERE object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')
          AND DATALENGTH(definition) = 9342
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0x3E94EF77EF79F58BBE38E68BDD965270F5F6A13DFF1A0AC9F3D2EC58869110AA)
        AS procedure_v042_bytes_match,
    (SELECT COUNT_BIG(*) FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
          AND name = N'CK_ctl_execution_audit_status'
          AND DATALENGTH(definition) = 136
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0xE10FF7DCE79EA0B13851DA9F0E329388F4303B92038C21E3861AF5D3613D29F6)
        AS status_check_v025_match,
    (SELECT COUNT_BIG(*) FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
          AND name = N'CK_exp_label'
          AND DATALENGTH(definition) = 450
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0x499FFDF7169311C0AD586B2C4E79041305A4224897DA2E5DA92CC4A971B9E95F)
        AS label_check_v042_match,
    (SELECT COUNT_BIG(*) FROM sys.indexes AS i
        WHERE i.object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
          AND i.name = N'IX_ctl_execution_audit_status_started_at'
          AND i.type = 2 AND i.is_unique = 0 AND i.has_filter = 0
          AND i.is_padded = 0 AND i.fill_factor = 0 AND i.ignore_dup_key = 0
          AND i.allow_row_locks = 1 AND i.allow_page_locks = 1
          AND i.optimize_for_sequential_key = 0 AND i.suppress_dup_key_messages = 0
          AND i.is_primary_key = 0 AND i.is_unique_constraint = 0 AND i.data_space_id = 1
          AND i.is_disabled = 0 AND i.is_hypothetical = 0
          AND (SELECT COUNT_BIG(*) FROM sys.partitions AS p
               WHERE p.object_id = i.object_id AND p.index_id = i.index_id
                 AND p.partition_number = 1 AND p.data_compression = 0) = 1
          AND (SELECT COUNT_BIG(*) FROM sys.partitions AS p
               WHERE p.object_id = i.object_id AND p.index_id = i.index_id) = 1)
        AS status_index_v025_options_match;

-- Metadata only. Any non-index statistic on one of the four changed columns
-- must be reviewed before V105; auto-created statistics are counted as well.
WITH affected AS (
    SELECT object_id, name FROM sys.columns WHERE
        (object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND name IN
            (N'status', N'traversal_verification', N'failure_category'))
        OR (object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND name = N'label')
), affecting_stats AS (
    SELECT DISTINCT st.object_id, st.stats_id, st.auto_created, st.user_created
    FROM sys.stats AS st
    JOIN sys.stats_columns AS sc ON sc.object_id = st.object_id AND sc.stats_id = st.stats_id
    JOIN sys.columns AS c ON c.object_id = sc.object_id AND c.column_id = sc.column_id
    JOIN affected AS a ON a.object_id = c.object_id AND a.name = c.name
    LEFT JOIN sys.indexes AS i ON i.object_id = st.object_id
        AND i.index_id = st.stats_id AND i.name = st.name
    WHERE i.object_id IS NULL OR i.object_id <> OBJECT_ID(N'ctl.execution_audit', N'U')
        OR i.name <> N'IX_ctl_execution_audit_status_started_at'
)
SELECT N'V105_STATISTICS_BLOCKERS' AS marker,
    (SELECT COUNT_BIG(*) FROM affecting_stats) AS affecting_stats,
    (SELECT COUNT_BIG(*) FROM affecting_stats WHERE auto_created = 1) AS auto_created_stats,
    (SELECT COUNT_BIG(*) FROM affecting_stats WHERE user_created = 1) AS user_created_stats,
    (SELECT COUNT_BIG(*) FROM sys.stats AS st
        LEFT JOIN sys.indexes AS i ON i.object_id = st.object_id
            AND i.index_id = st.stats_id AND i.name = st.name
        WHERE st.object_id IN (OBJECT_ID(N'ctl.execution_audit', N'U'),
                              OBJECT_ID(N'ref.expansion_lab_label', N'U'))
          AND st.has_filter = 1 AND i.object_id IS NULL) AS standalone_filtered_stats;

SELECT N'V105_TYPE_DEPENDENCIES' AS marker,
    (SELECT COUNT_BIG(*) FROM sys.parameters
        WHERE user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
          AND object_id <> OBJECT_ID(N'ref.usp_import_expansion_references', N'P'))
        AS other_typed_parameters,
    (SELECT COUNT_BIG(*) FROM sys.columns
        WHERE user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
          AND object_id <> (SELECT type_table_object_id FROM sys.table_types
              WHERE user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')))
        AS typed_columns,
    (SELECT COUNT_BIG(*) FROM sys.sql_expression_dependencies AS d
        WHERE d.referenced_class = 6
          AND (d.referenced_id = TYPE_ID(N'ref.expansion_lab_label_batch')
            OR (d.referenced_entity_name = N'expansion_lab_label_batch'
                AND (d.referenced_schema_name = N'ref'
                     OR d.referenced_schema_name IS NULL)))
          AND d.referencing_id <> OBJECT_ID(N'ref.usp_import_expansion_references', N'P'))
        AS other_expression_references;

SELECT N'V105_COLUMN_DEPENDENCIES' AS marker,
    (SELECT COUNT_BIG(*) FROM sys.foreign_key_columns AS fk
        JOIN sys.columns AS c ON
          (c.object_id = fk.parent_object_id AND c.column_id = fk.parent_column_id)
          OR (c.object_id = fk.referenced_object_id AND c.column_id = fk.referenced_column_id)
        WHERE (c.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND c.name = N'label'))
        AS foreign_key_columns,
    (SELECT COUNT_BIG(*) FROM sys.default_constraints AS df
        JOIN sys.columns AS c ON c.object_id = df.parent_object_id
            AND c.column_id = df.parent_column_id
        WHERE (c.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND c.name = N'label'))
        AS default_constraints,
    (SELECT COUNT_BIG(*) FROM sys.fulltext_index_columns AS ft
        JOIN sys.columns AS c ON c.object_id = ft.object_id AND c.column_id = ft.column_id
        WHERE (c.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND c.name = N'label'))
        AS fulltext_columns,
    (SELECT COUNT_BIG(*) FROM sys.computed_columns
        WHERE object_id IN (OBJECT_ID(N'ctl.execution_audit', N'U'),
                            OBJECT_ID(N'ref.expansion_lab_label', N'U'))) AS computed_columns,
    (SELECT COUNT_BIG(*) FROM sys.check_constraints
        WHERE (parent_object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND name NOT IN
            (N'CK_ctl_execution_audit_template', N'CK_ctl_execution_audit_status',
             N'CK_ctl_execution_audit_business_window', N'CK_ctl_execution_audit_updated_window',
             N'CK_ctl_execution_audit_volumes', N'CK_ctl_execution_audit_terminal_page'))
          OR (parent_object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
              AND name <> N'CK_exp_label')) AS unexpected_checks,
    (SELECT COUNT_BIG(*) FROM sys.index_columns AS ic
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ((c.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND c.name IN
                    (N'status', N'traversal_verification', N'failure_category')
                    AND ic.index_id <> (SELECT index_id FROM sys.indexes
                        WHERE object_id = c.object_id
                          AND name = N'IX_ctl_execution_audit_status_started_at'))
            OR (c.object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND c.name = N'label')))
        AS other_index_columns,
    (SELECT COUNT_BIG(*) FROM sys.sql_expression_dependencies AS d
        WHERE d.is_schema_bound_reference = 1
          AND ((d.referenced_id = OBJECT_ID(N'ctl.execution_audit', N'U')
                AND (d.referenced_minor_id = 0 OR d.referenced_minor_id IN
                    (SELECT column_id FROM sys.columns
                     WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
                       AND name IN (N'status', N'traversal_verification', N'failure_category')))
                AND d.referencing_id <> OBJECT_ID(N'ctl.CK_ctl_execution_audit_status', N'C'))
            OR (d.referenced_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
                AND (d.referenced_minor_id = 0 OR d.referenced_minor_id =
                    (SELECT column_id FROM sys.columns
                     WHERE object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U') AND name = N'label'))
                AND d.referencing_id <> OBJECT_ID(N'ref.CK_exp_label', N'C'))))
        AS other_schema_bound_references;

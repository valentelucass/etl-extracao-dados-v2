-- P08 V105: correct four persisted semantic collations and the dependent TVP.
-- The reviewed caller contract remains ref.expansion_lab_label_batch and the
-- ten-parameter ref.usp_import_expansion_references from V042. No data DML.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 10000;
IF DB_NAME() <> N'ETL_SISTEMA_V2_SHADOW'
    THROW 54050, N'V105_EXACT_SHADOW_REQUIRED', 1;
BEGIN TRANSACTION;

DECLARE @audit INT = OBJECT_ID(N'ctl.execution_audit', N'U');
DECLARE @labels INT = OBJECT_ID(N'ref.expansion_lab_label', N'U');
DECLARE @procedure INT = OBJECT_ID(N'ref.usp_import_expansion_references', N'P');
DECLARE @type INT = TYPE_ID(N'ref.expansion_lab_label_batch');
DECLARE @type_table INT = (
    SELECT type_table_object_id FROM sys.table_types WHERE user_type_id = @type
);
IF @audit IS NULL OR @labels IS NULL OR @procedure IS NULL OR @type IS NULL
    OR @type_table IS NULL OR SCHEMA_ID(N'ctl') IS NULL OR SCHEMA_ID(N'ref') IS NULL
    THROW 54051, N'V105_REQUIRED_OBJECT_MISSING', 1;

-- A baseline V104 and the already-applied shadow both enter with CI inherited
-- from the database default on exactly these fields. Any other shape stops.
IF (SELECT COUNT_BIG(*) FROM sys.columns
    WHERE object_id = @audit AND (
        (name = N'status' AND max_length = 40 AND is_nullable = 0)
        OR (name = N'traversal_verification' AND max_length = 128 AND is_nullable = 1)
        OR (name = N'failure_category' AND max_length = 200 AND is_nullable = 1)
    ) AND system_type_id = TYPE_ID(N'nvarchar')
      AND collation_name = N'Latin1_General_100_CI_AS_SC') <> 3
    OR (SELECT COUNT_BIG(*) FROM sys.columns
        WHERE object_id = @labels AND name = N'label' AND max_length = 256
          AND is_nullable = 0 AND system_type_id = TYPE_ID(N'nvarchar')
          AND collation_name = N'Latin1_General_100_CI_AS_SC') <> 1
    THROW 54052, N'V105_PERSISTED_COLUMN_DRIFT', 1;

IF (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = @type_table) <> 3
    OR (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = @type_table AND (
        (column_id = 1 AND name = N'category' AND system_type_id = TYPE_ID(N'varchar')
            AND max_length = 16 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_BIN2')
        OR (column_id = 2 AND name = N'raw_value' AND system_type_id = TYPE_ID(N'nvarchar')
            AND max_length = 256 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_BIN2')
        OR (column_id = 3 AND name = N'label' AND system_type_id = TYPE_ID(N'nvarchar')
            AND max_length = 256 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_CI_AS_SC')
    )) <> 3
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = @type_table
        AND is_primary_key = 1 AND is_unique = 1)
    OR (SELECT COUNT_BIG(*) FROM sys.indexes WHERE object_id = @type_table AND index_id > 0) <> 1
    OR (SELECT COUNT_BIG(*) FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        WHERE ic.object_id = @type_table AND i.is_primary_key = 1) <> 2
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @type_table AND i.is_primary_key = 1
          AND ic.key_ordinal = 1 AND c.name = N'category')
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @type_table AND i.is_primary_key = 1
          AND ic.key_ordinal = 2 AND c.name = N'raw_value')
    THROW 54053, N'V105_TVP_SHAPE_DRIFT', 1;

-- The V042 procedure is the sole typed consumer. Direct grants, ownership,
-- signatures or properties would not survive DROP/CREATE, so refuse them.
DECLARE @expected_parameters TABLE (
    parameter_id INT PRIMARY KEY, parameter_name SYSNAME,
    system_type_id TINYINT, max_length SMALLINT, scale TINYINT
);
INSERT @expected_parameters VALUES
    (1, N'@run_id', TYPE_ID(N'uniqueidentifier'), 16, 0),
    (2, N'@revision', TYPE_ID(N'int'), 4, 0),
    (3, N'@start', TYPE_ID(N'date'), 3, 0),
    (4, N'@end', TYPE_ID(N'date'), 3, 0),
    (5, N'@fingerprint', TYPE_ID(N'char'), 64, 0),
    (7, N'@branch_code', TYPE_ID(N'nvarchar'), 64, 0),
    (8, N'@branch_label', TYPE_ID(N'nvarchar'), 256, 0),
    (9, N'@payer_token', TYPE_ID(N'char'), 64, 0),
    (10, N'@now', TYPE_ID(N'datetime2'), 8, 7);
IF (SELECT COUNT_BIG(*) FROM sys.parameters WHERE object_id = @procedure) <> 10
    OR EXISTS (SELECT 1 FROM @expected_parameters AS e
        LEFT JOIN sys.parameters AS p ON p.object_id = @procedure
            AND p.parameter_id = e.parameter_id
        WHERE p.name COLLATE Latin1_General_100_BIN2 <> e.parameter_name
           OR p.system_type_id <> e.system_type_id OR p.max_length <> e.max_length
           OR p.scale <> e.scale OR p.name IS NULL)
    OR NOT EXISTS (SELECT 1 FROM sys.parameters WHERE object_id = @procedure
        AND parameter_id = 6 AND name = N'@labels' AND user_type_id = @type
        AND is_readonly = 1)
    OR EXISTS (SELECT 1 FROM sys.parameter_type_usages
        WHERE user_type_id = @type AND object_id <> @procedure)
    OR EXISTS (SELECT 1 FROM sys.parameters
        WHERE user_type_id = @type AND object_id <> @procedure)
    OR EXISTS (SELECT 1 FROM sys.columns
        WHERE user_type_id = @type AND object_id <> @type_table)
    OR EXISTS (SELECT 1 FROM sys.sql_expression_dependencies AS d
        WHERE d.referenced_class = 6
          AND (d.referenced_id = @type
            OR (d.referenced_entity_name = N'expansion_lab_label_batch'
                AND (d.referenced_schema_name = N'ref'
                     OR d.referenced_schema_name IS NULL)))
          AND d.referencing_id <> @procedure)
    OR EXISTS (SELECT 1 FROM sys.database_permissions
        WHERE (class = 1 AND major_id = @procedure)
           OR (class = 6 AND major_id = @type))
    OR EXISTS (SELECT 1 FROM sys.extended_properties
        WHERE (class = 1 AND major_id = @procedure)
           OR (class = 6 AND major_id = @type))
    OR EXISTS (SELECT 1 FROM sys.crypt_properties WHERE major_id = @procedure)
    OR EXISTS (SELECT 1 FROM sys.objects WHERE object_id = @procedure
        AND principal_id IS NOT NULL)
    OR NOT EXISTS (SELECT 1 FROM sys.sql_modules WHERE object_id = @procedure
        AND uses_ansi_nulls = 1 AND uses_quoted_identifier = 1
        AND execute_as_principal_id IS NULL
        AND DATALENGTH(definition) = 9342
        AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
            0x3E94EF77EF79F58BBE38E68BDD965270F5F6A13DFF1A0AC9F3D2EC58869110AA)
    THROW 54054, N'V105_TVP_PROCEDURE_OR_PRIVILEGE_DRIFT', 1;

IF EXISTS (SELECT 1 FROM ctl.execution_audit
    WHERE status COLLATE Latin1_General_100_BIN2
          NOT IN (N'STARTED', N'COMPLETED', N'FAILED'))
    THROW 54055, N'V105_EXISTING_STATUS_NOT_BIN2_CANONICAL', 1;

DECLARE @status_index INT = (
    SELECT index_id FROM sys.indexes WHERE object_id = @audit
      AND name = N'IX_ctl_execution_audit_status_started_at'
      AND type = 2 AND is_unique = 0 AND has_filter = 0 AND is_disabled = 0
      AND is_hypothetical = 0 AND fill_factor = 0
      AND is_padded = 0 AND ignore_dup_key = 0
      AND allow_row_locks = 1 AND allow_page_locks = 1
      AND optimize_for_sequential_key = 0 AND suppress_dup_key_messages = 0
      AND is_primary_key = 0 AND is_unique_constraint = 0 AND data_space_id = 1
);
IF @status_index IS NULL
    OR (SELECT COUNT_BIG(*) FROM sys.partitions
        WHERE object_id = @audit AND index_id = @status_index
          AND partition_number = 1 AND data_compression = 0) <> 1
    OR (SELECT COUNT_BIG(*) FROM sys.partitions
        WHERE object_id = @audit AND index_id = @status_index) <> 1
    OR (SELECT COUNT_BIG(*) FROM sys.index_columns
        WHERE object_id = @audit AND index_id = @status_index) <> 2
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @audit AND ic.index_id = @status_index
          AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'status')
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @audit AND ic.index_id = @status_index
          AND ic.key_ordinal = 2 AND ic.is_descending_key = 1 AND c.name = N'started_at')
    THROW 54056, N'V105_AUDIT_INDEX_DRIFT', 1;

IF EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c
    ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE ic.object_id = @audit
      AND c.name IN (N'status', N'traversal_verification', N'failure_category')
      AND ic.index_id <> @status_index)
    OR EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c
        ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @labels AND c.name = N'label')
    OR EXISTS (SELECT 1 FROM sys.foreign_key_columns AS fk
        JOIN sys.columns AS c ON
          (c.object_id = fk.parent_object_id AND c.column_id = fk.parent_column_id)
          OR (c.object_id = fk.referenced_object_id AND c.column_id = fk.referenced_column_id)
        WHERE (c.object_id = @audit AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = @labels AND c.name = N'label'))
    OR EXISTS (SELECT 1 FROM sys.default_constraints AS df
        JOIN sys.columns AS c ON c.object_id = df.parent_object_id
            AND c.column_id = df.parent_column_id
        WHERE (c.object_id = @audit AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = @labels AND c.name = N'label'))
    OR EXISTS (SELECT 1 FROM sys.fulltext_index_columns AS ft
        JOIN sys.columns AS c ON c.object_id = ft.object_id AND c.column_id = ft.column_id
        WHERE (c.object_id = @audit AND c.name IN
            (N'status', N'traversal_verification', N'failure_category'))
          OR (c.object_id = @labels AND c.name = N'label'))
    OR EXISTS (SELECT 1 FROM sys.computed_columns
        WHERE object_id IN (@audit, @labels))
    OR EXISTS (SELECT 1 FROM sys.check_constraints
        WHERE (parent_object_id = @audit AND name NOT IN
            (N'CK_ctl_execution_audit_template', N'CK_ctl_execution_audit_status',
             N'CK_ctl_execution_audit_business_window', N'CK_ctl_execution_audit_updated_window',
             N'CK_ctl_execution_audit_volumes', N'CK_ctl_execution_audit_terminal_page'))
          OR (parent_object_id = @labels AND name <> N'CK_exp_label'))
    OR EXISTS (SELECT 1 FROM sys.sql_expression_dependencies AS d
        WHERE d.is_schema_bound_reference = 1
          AND ((d.referenced_id = @audit AND
                (d.referenced_minor_id = 0 OR d.referenced_minor_id IN
                    (SELECT column_id FROM sys.columns WHERE object_id = @audit AND name IN
                        (N'status', N'traversal_verification', N'failure_category')))
                AND d.referencing_id <> OBJECT_ID(N'ctl.CK_ctl_execution_audit_status', N'C'))
            OR (d.referenced_id = @labels AND
                (d.referenced_minor_id = 0 OR d.referenced_minor_id =
                    (SELECT column_id FROM sys.columns WHERE object_id = @labels AND name = N'label'))
                AND d.referencing_id <> OBJECT_ID(N'ref.CK_exp_label', N'C'))))
    THROW 54065, N'V105_UNREVIEWED_COLUMN_DEPENDENCY', 1;

DECLARE @status_check NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = @audit AND name = N'CK_ctl_execution_audit_status'
      AND is_disabled = 0 AND is_not_trusted = 0
);
DECLARE @label_check NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = @labels AND name = N'CK_exp_label'
      AND is_disabled = 0 AND is_not_trusted = 0
);
-- SQL Server normalizes the V025/V042 CHECK expressions on creation. These
-- lengths and hashes are the reviewed V104 catalog form of those sources.
IF @status_check IS NULL OR @label_check IS NULL
    OR DATALENGTH(@status_check) <> 136
    OR HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @status_check)) <>
        0xE10FF7DCE79EA0B13851DA9F0E329388F4303B92038C21E3861AF5D3613D29F6
    OR DATALENGTH(@label_check) <> 450
    OR HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @label_check)) <>
        0x499FFDF7169311C0AD586B2C4E79041305A4224897DA2E5DA92CC4A971B9E95F
    THROW 54057, N'V105_CHECK_CONSTRAINT_DRIFT', 1;

CREATE TABLE #v105_before (
    audit_rows BIGINT NOT NULL,
    audit_text_bytes BIGINT NOT NULL,
    label_rows BIGINT NOT NULL,
    label_text_bytes BIGINT NOT NULL,
    release_rows BIGINT NOT NULL,
    receipt_rows BIGINT NOT NULL,
    status_definition NVARCHAR(MAX) NOT NULL,
    label_definition NVARCHAR(MAX) NOT NULL
);
INSERT #v105_before VALUES (
    (SELECT COUNT_BIG(*) FROM ctl.execution_audit),
    (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(status))
        + COALESCE(DATALENGTH(traversal_verification), 0)
        + COALESCE(DATALENGTH(failure_category), 0)), 0) FROM ctl.execution_audit),
    (SELECT COUNT_BIG(*) FROM ref.expansion_lab_label),
    (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(label))), 0) FROM ref.expansion_lab_label),
    (SELECT COUNT_BIG(*) FROM ref.reference_release WHERE family_code = N'EXPANSION_LABELS'),
    (SELECT COUNT_BIG(*) FROM ref.reference_import_receipt AS r
        JOIN ref.reference_release AS v ON v.reference_release_id = r.reference_release_id
        WHERE v.family_code = N'EXPANSION_LABELS'),
    @status_check, @label_check
);

-- SQL Server may auto-drop automatic statistics on a collation change. Do
-- not silently lose even optimizer metadata: only the reviewed status index
-- and its index statistic may be rebuilt. Any other statistic touching a
-- changed column, or filtered statistic on either table, needs a new plan.
IF EXISTS (SELECT 1 FROM sys.stats AS st
    JOIN sys.stats_columns AS sc ON sc.object_id = st.object_id AND sc.stats_id = st.stats_id
    JOIN sys.columns AS c ON c.object_id = sc.object_id AND c.column_id = sc.column_id
    LEFT JOIN sys.indexes AS i ON i.object_id = st.object_id
        AND i.index_id = st.stats_id AND i.name = st.name
    WHERE ((c.object_id = @audit AND c.name IN
                (N'status', N'traversal_verification', N'failure_category'))
           OR (c.object_id = @labels AND c.name = N'label'))
      AND (i.object_id IS NULL OR i.object_id <> @audit
           OR i.name <> N'IX_ctl_execution_audit_status_started_at'))
    OR EXISTS (SELECT 1 FROM sys.stats AS st
        LEFT JOIN sys.indexes AS i ON i.object_id = st.object_id
            AND i.index_id = st.stats_id AND i.name = st.name
        WHERE st.object_id IN (@audit, @labels) AND st.has_filter = 1
          AND i.object_id IS NULL)
    THROW 54066, N'V105_UNREVIEWED_COLUMN_STATISTICS', 1;

DROP PROCEDURE ref.usp_import_expansion_references;
DROP TYPE ref.expansion_lab_label_batch;
DROP INDEX IX_ctl_execution_audit_status_started_at ON ctl.execution_audit;
ALTER TABLE ctl.execution_audit DROP CONSTRAINT CK_ctl_execution_audit_status;
ALTER TABLE ref.expansion_lab_label DROP CONSTRAINT CK_exp_label;

ALTER TABLE ctl.execution_audit
    ALTER COLUMN status NVARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL;
ALTER TABLE ctl.execution_audit
    ALTER COLUMN traversal_verification NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NULL;
ALTER TABLE ctl.execution_audit
    ALTER COLUMN failure_category NVARCHAR(100) COLLATE Latin1_General_100_BIN2 NULL;
ALTER TABLE ref.expansion_lab_label
    ALTER COLUMN label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL;

DECLARE @recreate_status NVARCHAR(MAX) = N'ALTER TABLE ctl.execution_audit WITH CHECK ADD CONSTRAINT '
    + QUOTENAME(N'CK_ctl_execution_audit_status') + N' CHECK ' +
    (SELECT status_definition FROM #v105_before);
DECLARE @recreate_label NVARCHAR(MAX) = N'ALTER TABLE ref.expansion_lab_label WITH CHECK ADD CONSTRAINT '
    + QUOTENAME(N'CK_exp_label') + N' CHECK ' +
    (SELECT label_definition FROM #v105_before);
EXEC sys.sp_executesql @recreate_status;
EXEC sys.sp_executesql @recreate_label;
CREATE INDEX IX_ctl_execution_audit_status_started_at
    ON ctl.execution_audit (status, started_at DESC);
GO

CREATE TYPE ref.expansion_lab_label_batch AS TABLE (
    category VARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    raw_value NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (category, raw_value)
);
GO

CREATE PROCEDURE ref.usp_import_expansion_references @run_id UNIQUEIDENTIFIER,@revision INT,@start DATE,@end DATE,
 @fingerprint CHAR(64),@labels ref.expansion_lab_label_batch READONLY,@branch_code NVARCHAR(32),@branch_label NVARCHAR(128),
 @payer_token CHAR(64),@now DATETIME2(7)
AS
BEGIN
 SET NOCOUNT ON;SET XACT_ABORT ON;
 EXEC ctl.usp_expansion_lab_lock @run_id,'MATERIALIZE';
 IF @revision NOT BETWEEN 1 AND 1000 OR DATEDIFF(day,@start,@end) NOT BETWEEN 1 AND 62 OR (SELECT COUNT_BIG(*) FROM @labels) NOT BETWEEN 1 AND 100
 OR NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_run WHERE run_id=@run_id AND @start>=DATEADD(day,-31,window_start) AND @end<=DATEADD(day,31,window_end_exclusive))
 THROW 53435,N'EXP_REF_BOUND',1;
 DECLARE @scope NVARCHAR(128)=CONCAT(N'SYNTHETIC_EXPANSION_LAB:',CONVERT(NVARCHAR(36),@run_id)),
 @version NVARCHAR(64)=CONCAT(N'expansion-references-v1-r',@revision),@outcome NVARCHAR(16),@label_id BIGINT,@calendar_id BIGINT,@branch_id BIGINT,@payer_id BIGINT;
 DECLARE @label_count BIGINT=(SELECT COUNT_BIG(*) FROM @labels),@calendar_count BIGINT,@calendar_start DATE;
 SELECT * INTO #calendar FROM ref.ufn_calendar_seed_candidate_v1(@start,@end);
 SELECT @calendar_count=COUNT_BIG(*),@calendar_start=MIN(calendar_date) FROM #calendar;
 IF @calendar_count=0 THROW 53436,N'EXP_CALENDAR_EMPTY',1;
 EXEC ref.usp_register_reference_release N'EXPANSION_LABELS',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_FIXTURE_V1',@fingerprint,@label_count,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@label_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.expansion_lab_label SELECT @label_id,N'EXPANSION_LABELS',* FROM @labels;
  INSERT ref.reference_import_receipt VALUES(@label_id,N'governed-references-v1',@fingerprint,@label_count,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'CALENDAR',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_CALENDAR_V1',@fingerprint,@calendar_count,@calendar_start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@calendar_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.calendario SELECT @calendar_id,N'CALENDAR',* FROM #calendar;
  INSERT ref.reference_import_receipt VALUES(@calendar_id,N'governed-references-v1',@fingerprint,@calendar_count,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'BRANCH_OPERATIONS',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_BRANCH_V1',@fingerprint,1,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@branch_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.filial_operacional VALUES(@branch_id,N'BRANCH_OPERATIONS',@branch_code,@branch_label,N'SYNTHETIC_ONLY');
  INSERT ref.reference_import_receipt VALUES(@branch_id,N'governed-references-v1',@fingerprint,1,1,N'LABORATORY_IMPORTER',@now);
 END;
 EXEC ref.usp_register_reference_release N'BRANCH_ATTRIBUTION',@scope,@version,1,N'DETERMINISTIC_LOCAL',N'EXPANSION_PAYER_V1',@fingerprint,1,@start,@end,N'SYNTHETIC_ONLY',N'LABORATORY_AUTHOR',@payer_id OUTPUT,@outcome OUTPUT;
 IF @outcome=N'CREATED'
 BEGIN
  INSERT ref.atribuicao_filial VALUES(@payer_id,N'BRANCH_ATTRIBUTION',@payer_token,N'synthetic-expansion-token-v1',@branch_id,@branch_code,@start,@end,N'SYNTHETIC_ONLY');
  INSERT ref.reference_import_receipt VALUES(@payer_id,N'governed-references-v1',@fingerprint,1,1,N'LABORATORY_IMPORTER',@now);
 END;
 IF NOT EXISTS(SELECT 1 FROM ref.filial_operacional WHERE reference_release_id=@branch_id AND branch_code=@branch_code AND branch_label=@branch_label)
 OR NOT EXISTS(SELECT 1 FROM ref.atribuicao_filial WHERE reference_release_id=@payer_id AND payer_document_token=@payer_token AND branch_reference_release_id=@branch_id AND branch_code=@branch_code)
 THROW 53437,N'EXP_REF_CONTENT_DIVERGENT',1;
 IF EXISTS(SELECT category,raw_value,label FROM ref.expansion_lab_label WHERE reference_release_id=@label_id EXCEPT SELECT category,raw_value,label FROM @labels)
 OR EXISTS(SELECT category,raw_value,label FROM @labels EXCEPT SELECT category,raw_value,label FROM ref.expansion_lab_label WHERE reference_release_id=@label_id)
 THROW 53437,N'EXP_REF_CONTENT_DIVERGENT',1;
 INSERT ctl.expansion_lab_reference_selection
 SELECT @run_id,@revision,purpose,@start,@end,release_id FROM (VALUES('LABELS',@label_id),('CALENDAR',@calendar_id),('BRANCH',@branch_id),('PAYER',@payer_id)) x(purpose,release_id)
 WHERE NOT EXISTS(SELECT 1 FROM ctl.expansion_lab_reference_selection s WHERE s.run_id=@run_id AND s.revision=@revision AND s.purpose=x.purpose AND s.valid_from=@start);
 SELECT @label_id label_release,@calendar_id calendar_release,@branch_id branch_release,@payer_id payer_release;
END;
GO

IF (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
    AND name IN (N'status', N'traversal_verification', N'failure_category')
    AND collation_name = N'Latin1_General_100_BIN2') <> 3
    OR (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
        AND name = N'label' AND collation_name = N'Latin1_General_100_BIN2') <> 1
    OR (SELECT COUNT_BIG(*) FROM sys.columns AS c JOIN sys.table_types AS t
        ON t.type_table_object_id = c.object_id WHERE t.user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
        AND c.name = N'label' AND c.collation_name = N'Latin1_General_100_BIN2') <> 1
    OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
        AND name = N'CK_ctl_execution_audit_status' AND is_not_trusted = 0 AND is_disabled = 0)
    OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
        AND name = N'CK_exp_label' AND is_not_trusted = 0 AND is_disabled = 0)
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
        AND name = N'IX_ctl_execution_audit_status_started_at' AND is_disabled = 0
        AND type = 2 AND is_unique = 0 AND has_filter = 0 AND is_hypothetical = 0
        AND is_padded = 0 AND fill_factor = 0 AND ignore_dup_key = 0
        AND allow_row_locks = 1 AND allow_page_locks = 1
        AND optimize_for_sequential_key = 0 AND suppress_dup_key_messages = 0
        AND is_primary_key = 0 AND is_unique_constraint = 0 AND data_space_id = 1)
    OR (SELECT COUNT_BIG(*) FROM sys.partitions AS p JOIN sys.indexes AS i
        ON i.object_id = p.object_id AND i.index_id = p.index_id
        WHERE i.object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
          AND i.name = N'IX_ctl_execution_audit_status_started_at'
          AND p.partition_number = 1 AND p.data_compression = 0) <> 1
    OR NOT EXISTS (SELECT 1 FROM sys.parameters WHERE object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')
        AND parameter_id = 6 AND name = N'@labels' AND user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
        AND is_readonly = 1)
    OR NOT EXISTS (SELECT 1 FROM sys.sql_modules
        WHERE object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')
          AND DATALENGTH(definition) = 9342
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0x3E94EF77EF79F58BBE38E68BDD965270F5F6A13DFF1A0AC9F3D2EC58869110AA)
    OR NOT EXISTS (SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
          AND name = N'CK_ctl_execution_audit_status' AND DATALENGTH(definition) = 136
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0xE10FF7DCE79EA0B13851DA9F0E329388F4303B92038C21E3861AF5D3613D29F6)
    OR NOT EXISTS (SELECT 1 FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
          AND name = N'CK_exp_label' AND DATALENGTH(definition) = 450
          AND HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), definition)) =
              0x499FFDF7169311C0AD586B2C4E79041305A4224897DA2E5DA92CC4A971B9E95F)
    THROW 54058, N'V105_POSTCONDITION_DRIFT', 1;

IF EXISTS (SELECT 1 FROM #v105_before AS b WHERE
    b.audit_rows <> (SELECT COUNT_BIG(*) FROM ctl.execution_audit)
    OR b.audit_text_bytes <> (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(status))
        + COALESCE(DATALENGTH(traversal_verification), 0)
        + COALESCE(DATALENGTH(failure_category), 0)), 0) FROM ctl.execution_audit)
    OR b.label_rows <> (SELECT COUNT_BIG(*) FROM ref.expansion_lab_label)
    OR b.label_text_bytes <> (SELECT COALESCE(SUM(CONVERT(BIGINT, DATALENGTH(label))), 0)
        FROM ref.expansion_lab_label)
    OR b.release_rows <> (SELECT COUNT_BIG(*) FROM ref.reference_release WHERE family_code = N'EXPANSION_LABELS')
    OR b.receipt_rows <> (SELECT COUNT_BIG(*) FROM ref.reference_import_receipt AS r
        JOIN ref.reference_release AS v ON v.reference_release_id = r.reference_release_id
        WHERE v.family_code = N'EXPANSION_LABELS'))
    THROW 54059, N'V105_AGGREGATE_DATA_DRIFT', 1;

DROP TABLE #v105_before;
COMMIT TRANSACTION;
SET LOCK_TIMEOUT -1;

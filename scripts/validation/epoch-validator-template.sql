-- P08 structural epoch validator. Generated inventory comes only from the
-- reviewed baseline and V001-V105 migrations; keep 003/005/030 untouched.
-- V104 intentionally reports pending BIN2 repairs; V105 can pass after DDL.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET NOCOUNT ON;
IF DB_NAME() <> N'$(DatabaseName)'
    THROW 54060, N'EPOCH_EXACT_SHADOW_REQUIRED', 1;
IF OBJECT_ID(N'ctl.flyway_schema_history', N'U') IS NULL
    THROW 54061, N'EPOCH_FLYWAY_HISTORY_REQUIRED', 1;

DECLARE @epoch INT = (
    SELECT COUNT(*) FROM ctl.flyway_schema_history WHERE success = 1 AND type = N'SQL'
);
IF @epoch NOT IN (104, 105)
    OR (SELECT COUNT_BIG(*) FROM ctl.flyway_schema_history) <> @epoch + 1
    OR EXISTS (SELECT 1 FROM ctl.flyway_schema_history WHERE success = 0)
    OR (SELECT COUNT(DISTINCT TRY_CONVERT(INT, version)) FROM ctl.flyway_schema_history
        WHERE success = 1 AND type = N'SQL') <> @epoch
    OR (SELECT MIN(TRY_CONVERT(INT, version)) FROM ctl.flyway_schema_history
        WHERE success = 1 AND type = N'SQL') <> 1
    OR (SELECT MAX(TRY_CONVERT(INT, version)) FROM ctl.flyway_schema_history
        WHERE success = 1 AND type = N'SQL') <> @epoch
    THROW 54062, N'EPOCH_HISTORY_NOT_V104_OR_V105_CONTIGUOUS', 1;

DECLARE @failures TABLE (
    category NVARCHAR(40) COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_name NVARCHAR(300) COLLATE Latin1_General_100_BIN2 NOT NULL,
    detail NVARCHAR(200) NOT NULL
);
IF (SELECT COUNT_BIG(*) FROM sys.schemas WHERE name IN
    (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
    AND principal_id = DATABASE_PRINCIPAL_ID(N'v2_schema_owner')) <> 7
    INSERT @failures VALUES (N'SCHEMA', N'v2_schema_owner', N'Sete schemas V2 exigem ownership sem login.');
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'v2_schema_owner'
    AND type = N'S' AND authentication_type_desc = N'NONE')
    INSERT @failures VALUES (N'PRINCIPAL', N'v2_schema_owner', N'Owner V2 deve ser usuário sem login.');

DECLARE @objects TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY(schema_name, object_name, object_type)
);
DECLARE @types TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    type_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY(schema_name, type_name)
);
DECLARE @constraints TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    table_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    constraint_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    constraint_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY(schema_name, table_name, constraint_name)
);
DECLARE @unnamed TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    table_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    constraint_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    columns_text NVARCHAR(1000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reference_name NVARCHAR(260) COLLATE Latin1_General_100_BIN2 NOT NULL
);
DECLARE @indexes TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    table_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    index_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY(schema_name, table_name, index_name)
);

__INVENTORY_INSERTS__

INSERT @failures
SELECT N'OBJECT_MISSING', CONCAT(e.schema_name, N'.', e.object_name), N'Objeto versionado ausente ou tipo divergente.'
FROM @objects AS e WHERE NOT EXISTS (
    SELECT 1 FROM sys.objects AS a JOIN sys.schemas AS s ON s.schema_id = a.schema_id
    WHERE s.name COLLATE Latin1_General_100_BIN2 = e.schema_name
      AND a.name COLLATE Latin1_General_100_BIN2 = e.object_name
      AND a.type COLLATE Latin1_General_100_BIN2 = e.object_type AND a.is_ms_shipped = 0
);
INSERT @failures
SELECT N'OBJECT_EXTRA', CONCAT(s.name, N'.', a.name), N'Objeto fora do inventário versionado.'
FROM sys.objects AS a JOIN sys.schemas AS s ON s.schema_id = a.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND a.is_ms_shipped = 0 AND a.type IN (N'U', N'P', N'V', N'FN', N'IF', N'TF', N'TR')
  AND NOT (s.name = N'ctl' AND a.name = N'flyway_schema_history' AND a.type = N'U')
  AND NOT EXISTS (SELECT 1 FROM @objects AS e
      WHERE e.schema_name = s.name COLLATE Latin1_General_100_BIN2
        AND e.object_name = a.name COLLATE Latin1_General_100_BIN2
        AND e.object_type = a.type COLLATE Latin1_General_100_BIN2);

INSERT @failures
SELECT N'TYPE_MISSING', CONCAT(e.schema_name, N'.', e.type_name), N'TVP versionado ausente.'
FROM @types AS e WHERE NOT EXISTS (
    SELECT 1 FROM sys.table_types AS a JOIN sys.schemas AS s ON s.schema_id = a.schema_id
    WHERE s.name COLLATE Latin1_General_100_BIN2 = e.schema_name
      AND a.name COLLATE Latin1_General_100_BIN2 = e.type_name
);
INSERT @failures
SELECT N'TYPE_EXTRA', CONCAT(s.name, N'.', a.name), N'TVP fora do inventário versionado.'
FROM sys.table_types AS a JOIN sys.schemas AS s ON s.schema_id = a.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND NOT EXISTS (SELECT 1 FROM @types AS e
      WHERE e.schema_name = s.name COLLATE Latin1_General_100_BIN2
        AND e.type_name = a.name COLLATE Latin1_General_100_BIN2);

INSERT @failures
SELECT N'CONSTRAINT_MISSING', CONCAT(e.schema_name, N'.', e.table_name, N'.', e.constraint_name),
       N'Constraint versionada ausente ou tipo divergente.'
FROM @constraints AS e WHERE NOT EXISTS (
    SELECT 1 FROM sys.objects AS a JOIN sys.tables AS t ON t.object_id = a.parent_object_id
    JOIN sys.schemas AS s ON s.schema_id = t.schema_id
    WHERE s.name COLLATE Latin1_General_100_BIN2 = e.schema_name
      AND t.name COLLATE Latin1_General_100_BIN2 = e.table_name
      AND a.name COLLATE Latin1_General_100_BIN2 = e.constraint_name
      AND a.type COLLATE Latin1_General_100_BIN2 = e.constraint_type
);
INSERT @failures
SELECT N'CONSTRAINT_EXTRA', CONCAT(s.name, N'.', t.name, N'.', a.name),
       N'Constraint fora do inventário versionado.'
FROM sys.objects AS a JOIN sys.tables AS t ON t.object_id = a.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND a.type IN (N'C', N'D', N'F', N'PK', N'UQ')
  AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history')
  AND COALESCE(
      (SELECT is_system_named FROM sys.foreign_keys WHERE object_id = a.object_id),
      (SELECT is_system_named FROM sys.key_constraints WHERE object_id = a.object_id),
      (SELECT is_system_named FROM sys.check_constraints WHERE object_id = a.object_id),
      (SELECT is_system_named FROM sys.default_constraints WHERE object_id = a.object_id), 0) = 0
  AND NOT EXISTS (SELECT 1 FROM @constraints AS e
      WHERE e.schema_name = s.name COLLATE Latin1_General_100_BIN2
        AND e.table_name = t.name COLLATE Latin1_General_100_BIN2
        AND e.constraint_name = a.name COLLATE Latin1_General_100_BIN2
        AND e.constraint_type = a.type COLLATE Latin1_General_100_BIN2);

-- SQL Server assigns nonportable names to unnamed constraints. Their closed
-- inventory uses table, kind, ordered key columns and referenced table.
DECLARE @actual_unnamed TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    table_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    constraint_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    columns_text NVARCHAR(1000) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reference_name NVARCHAR(260) COLLATE Latin1_General_100_BIN2 NOT NULL
);
INSERT @actual_unnamed
SELECT s.name, t.name, N'F',
    COALESCE(STUFF((SELECT N',' + c.name FROM sys.foreign_key_columns AS fc
        JOIN sys.columns AS c ON c.object_id = fc.parent_object_id AND c.column_id = fc.parent_column_id
        WHERE fc.constraint_object_id = fk.object_id ORDER BY fc.constraint_column_id
        FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 1, N''), N''),
    CONCAT(OBJECT_SCHEMA_NAME(fk.referenced_object_id), N'.', OBJECT_NAME(fk.referenced_object_id))
FROM sys.foreign_keys AS fk JOIN sys.tables AS t ON t.object_id = fk.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND fk.is_system_named = 1 AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history');
INSERT @actual_unnamed
SELECT s.name, t.name, kc.type,
    COALESCE(STUFF((SELECT N',' + c.name FROM sys.index_columns AS ic
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
          AND ic.key_ordinal > 0 ORDER BY ic.key_ordinal
        FOR XML PATH(''), TYPE).value('.', 'NVARCHAR(MAX)'), 1, 1, N''), N''), N''
FROM sys.key_constraints AS kc JOIN sys.tables AS t ON t.object_id = kc.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND kc.is_system_named = 1 AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history');
INSERT @actual_unnamed
SELECT s.name, t.name, N'C', COALESCE(c.name, N''), N''
FROM sys.check_constraints AS ck JOIN sys.tables AS t ON t.object_id = ck.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
LEFT JOIN sys.columns AS c ON c.object_id = ck.parent_object_id AND c.column_id = ck.parent_column_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND ck.is_system_named = 1 AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history');
INSERT @actual_unnamed
SELECT s.name, t.name, N'D', COALESCE(c.name, N''), N''
FROM sys.default_constraints AS df JOIN sys.tables AS t ON t.object_id = df.parent_object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
LEFT JOIN sys.columns AS c ON c.object_id = df.parent_object_id AND c.column_id = df.parent_column_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND df.is_system_named = 1 AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history');

;WITH expected_counts AS (
    SELECT schema_name, table_name, constraint_type, columns_text, reference_name,
           COUNT_BIG(*) AS quantity FROM @unnamed
    GROUP BY schema_name, table_name, constraint_type, columns_text, reference_name
), actual_counts AS (
    SELECT schema_name, table_name, constraint_type, columns_text, reference_name,
           COUNT_BIG(*) AS quantity FROM @actual_unnamed
    GROUP BY schema_name, table_name, constraint_type, columns_text, reference_name
)
INSERT @failures
SELECT N'UNNAMED_CONSTRAINT',
       CONCAT(COALESCE(e.schema_name, a.schema_name), N'.',
              COALESCE(e.table_name, a.table_name), N'.',
              COALESCE(e.constraint_type, a.constraint_type), N'.',
              COALESCE(e.columns_text, a.columns_text)),
       N'Quantidade ou ligação de constraint sem nome diverge da migration.'
FROM expected_counts AS e FULL JOIN actual_counts AS a
  ON e.schema_name = a.schema_name AND e.table_name = a.table_name
 AND e.constraint_type = a.constraint_type AND e.columns_text = a.columns_text
 AND e.reference_name = a.reference_name
WHERE COALESCE(e.quantity, 0) <> COALESCE(a.quantity, 0);

INSERT @failures
SELECT N'INDEX_MISSING', CONCAT(e.schema_name, N'.', e.table_name, N'.', e.index_name),
       N'Índice versionado ausente.'
FROM @indexes AS e WHERE NOT EXISTS (
    SELECT 1 FROM sys.indexes AS a JOIN sys.tables AS t ON t.object_id = a.object_id
    JOIN sys.schemas AS s ON s.schema_id = t.schema_id
    WHERE s.name COLLATE Latin1_General_100_BIN2 = e.schema_name
      AND t.name COLLATE Latin1_General_100_BIN2 = e.table_name
      AND a.name COLLATE Latin1_General_100_BIN2 = e.index_name
      AND a.index_id > 0 AND a.is_primary_key = 0 AND a.is_unique_constraint = 0
);
INSERT @failures
SELECT N'INDEX_EXTRA', CONCAT(s.name, N'.', t.name, N'.', a.name),
       N'Índice fora do inventário versionado.'
FROM sys.indexes AS a JOIN sys.tables AS t ON t.object_id = a.object_id
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
WHERE s.name IN (N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon')
  AND a.index_id > 0 AND a.is_primary_key = 0 AND a.is_unique_constraint = 0
  AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history')
  AND NOT EXISTS (SELECT 1 FROM @indexes AS e
      WHERE e.schema_name = s.name COLLATE Latin1_General_100_BIN2
        AND e.table_name = t.name COLLATE Latin1_General_100_BIN2
        AND e.index_name = a.name COLLATE Latin1_General_100_BIN2);

-- Flyway is excluded only as ctl.flyway_schema_history. Every other ctl/ref
-- persisted NVARCHAR retains the original semantic BIN2 contract.
INSERT @failures
SELECT N'COLLATION', CONCAT(s.name, N'.', t.name, N'.', c.name),
       N'Coluna semântica persistida exige BIN2.'
FROM sys.tables AS t JOIN sys.schemas AS s ON s.schema_id = t.schema_id
JOIN sys.columns AS c ON c.object_id = t.object_id
WHERE s.name IN (N'ctl', N'ref') AND c.system_type_id = TYPE_ID(N'nvarchar')
  AND c.collation_name <> N'Latin1_General_100_BIN2'
  AND NOT (s.name = N'ctl' AND t.name = N'flyway_schema_history');

IF NOT EXISTS (SELECT 1 FROM sys.table_types AS t JOIN sys.schemas AS s ON s.schema_id = t.schema_id
    JOIN sys.columns AS c ON c.object_id = t.type_table_object_id
    WHERE s.name = N'ref' AND t.name = N'expansion_lab_label_batch'
      AND c.column_id = 3 AND c.name = N'label'
      AND c.system_type_id = TYPE_ID(N'nvarchar') AND c.max_length = 256
      AND c.is_nullable = 0 AND c.collation_name = N'Latin1_General_100_BIN2')
    INSERT @failures VALUES (N'COLLATION', N'ref.expansion_lab_label_batch.label', N'TVP semântico exige BIN2.');
DECLARE @label_type_table INT = (
    SELECT type_table_object_id FROM sys.table_types
    WHERE user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
);
IF (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = @label_type_table) <> 3
    OR (SELECT COUNT_BIG(*) FROM sys.columns WHERE object_id = @label_type_table AND (
        (column_id = 1 AND name = N'category' AND system_type_id = TYPE_ID(N'varchar')
            AND max_length = 16 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_BIN2')
        OR (column_id = 2 AND name = N'raw_value' AND system_type_id = TYPE_ID(N'nvarchar')
            AND max_length = 256 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_BIN2')
        OR (column_id = 3 AND name = N'label' AND system_type_id = TYPE_ID(N'nvarchar')
            AND max_length = 256 AND is_nullable = 0 AND collation_name = N'Latin1_General_100_BIN2')
    )) <> 3
    OR (SELECT COUNT_BIG(*) FROM sys.indexes WHERE object_id = @label_type_table AND index_id > 0) <> 1
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = @label_type_table
        AND is_primary_key = 1 AND is_unique = 1)
    OR (SELECT COUNT_BIG(*) FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        WHERE ic.object_id = @label_type_table AND i.is_primary_key = 1) <> 2
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @label_type_table AND i.is_primary_key = 1
          AND ic.key_ordinal = 1 AND c.name = N'category')
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.indexes AS i
        ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = @label_type_table AND i.is_primary_key = 1
          AND ic.key_ordinal = 2 AND c.name = N'raw_value')
    INSERT @failures VALUES (N'TVP_SHAPE', N'ref.expansion_lab_label_batch',
        N'Ordem, tipos, nulidade, collation ou PK do TVP divergente.');

DECLARE @register NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P'));
DECLARE @compact NVARCHAR(MAX) = REPLACE(REPLACE(REPLACE(REPLACE(@register,
    N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N'');
DECLARE @length INT = CHARINDEX(N'DATALENGTH(@source_instance)>256', @compact COLLATE Latin1_General_100_BIN2);
DECLARE @trim INT = CHARINDEX(N'SET@source_instance=LTRIM(RTRIM(@source_instance));', @compact COLLATE Latin1_General_100_BIN2);
DECLARE @lock INT = CHARINDEX(N'FROMctl.source_catalogWITH(UPDLOCK,HOLDLOCK)', @compact COLLATE Latin1_General_100_BIN2);
DECLARE @clock INT = CHARINDEX(N'DECLARE@nowDATETIME2(3)=SYSUTCDATETIME();', @compact COLLATE Latin1_General_100_BIN2);
DECLARE @insert INT = CHARINDEX(N'VALUES(@source_instance,@source_kind,1,@now)', @compact COLLATE Latin1_General_100_BIN2);
IF @register IS NULL OR @length = 0 OR @trim = 0 OR @lock = 0 OR @clock = 0 OR @insert = 0
    OR NOT (@length < @trim AND @trim < @lock AND @lock < @clock AND @clock < @insert)
    INSERT @failures VALUES (N'PROCEDURE_ORDER', N'ctl.usp_control_plane_register_source',
        N'Comprimento, normalização, lock, relógio e persistência fora de ordem.');
IF CHARINDEX(N'v.protocolCOLLATELatin1_General_100_BIN2=@source_kindCOLLATELatin1_General_100_BIN2',
    @compact COLLATE Latin1_General_100_BIN2) = 0
    INSERT @failures VALUES (N'PROCEDURE_BIN2', N'ctl.usp_control_plane_register_source',
        N'Protocolo de origem exige comparação BIN2 explícita.');

DECLARE @import NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'ref.usp_import_expansion_references', N'P'));
DECLARE @import_compact NVARCHAR(MAX) = REPLACE(REPLACE(REPLACE(REPLACE(@import,
    N' ', N''), NCHAR(9), N''), NCHAR(10), N''), NCHAR(13), N'');
IF @import IS NULL OR CHARINDEX(N'EXCEPTSELECTcategory,raw_value,labelFROM@labels',
        @import_compact COLLATE Latin1_General_100_BIN2) = 0
    OR CHARINDEX(N'EXCEPTSELECTcategory,raw_value,labelFROMref.expansion_lab_label',
        @import_compact COLLATE Latin1_General_100_BIN2) = 0
    OR (SELECT COUNT_BIG(*) FROM sys.parameters
        WHERE object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')) <> 10
    OR NOT EXISTS (SELECT 1 FROM sys.parameters WHERE object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')
        AND parameter_id = 6 AND name = N'@labels' AND user_type_id = TYPE_ID(N'ref.expansion_lab_label_batch')
        AND is_readonly = 1)
    INSERT @failures VALUES (N'PROCEDURE_TVP', N'ref.usp_import_expansion_references',
        N'Assinatura ou EXCEPT bidirecional divergente.');
DECLARE @parameter_mismatch INT = (
    SELECT COUNT(*) FROM (VALUES
        (1, N'@run_id', TYPE_ID(N'uniqueidentifier'), 16, 0),
        (2, N'@revision', TYPE_ID(N'int'), 4, 0),
        (3, N'@start', TYPE_ID(N'date'), 3, 0),
        (4, N'@end', TYPE_ID(N'date'), 3, 0),
        (5, N'@fingerprint', TYPE_ID(N'char'), 64, 0),
        (7, N'@branch_code', TYPE_ID(N'nvarchar'), 64, 0),
        (8, N'@branch_label', TYPE_ID(N'nvarchar'), 256, 0),
        (9, N'@payer_token', TYPE_ID(N'char'), 64, 0),
        (10, N'@now', TYPE_ID(N'datetime2'), 8, 7)
    ) AS expected(ordinal, parameter_name, system_type_id, max_length, scale)
    LEFT JOIN sys.parameters AS actual
      ON actual.object_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P')
     AND actual.parameter_id = expected.ordinal
    WHERE actual.name IS NULL
       OR actual.name COLLATE Latin1_General_100_BIN2 <> expected.parameter_name
       OR actual.system_type_id <> expected.system_type_id
       OR actual.max_length <> expected.max_length OR actual.scale <> expected.scale
);
IF @parameter_mismatch <> 0
    INSERT @failures VALUES (N'PROCEDURE_SIGNATURE', N'ref.usp_import_expansion_references',
        N'Ordem, tipo ou largura de parâmetro divergente de V042.');
IF EXISTS (SELECT 1 FROM sys.database_permissions AS p
    WHERE (p.class = 1 AND p.major_id = OBJECT_ID(N'ref.usp_import_expansion_references', N'P'))
       OR (p.class = 6 AND p.major_id = TYPE_ID(N'ref.expansion_lab_label_batch')))
    INSERT @failures VALUES (N'PRIVILEGE', N'ref.usp_import_expansion_references/ref.expansion_lab_label_batch',
        N'Permissão direta fora do contrato revisado.');

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
        AND name = N'CK_ctl_execution_audit_status' AND is_disabled = 0 AND is_not_trusted = 0)
    OR NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID(N'ref.expansion_lab_label', N'U')
        AND name = N'CK_exp_label' AND is_disabled = 0 AND is_not_trusted = 0)
    OR NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
        AND name = N'IX_ctl_execution_audit_status_started_at' AND is_disabled = 0)
    INSERT @failures VALUES (N'REBUILT_DEPENDENCY', N'ctl.execution_audit/ref.expansion_lab_label',
        N'Checks ou índice de auditoria ausentes/desabilitados.');
DECLARE @audit_index INT = (SELECT index_id FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
      AND name = N'IX_ctl_execution_audit_status_started_at'
      AND type = 2 AND is_unique = 0 AND has_filter = 0 AND is_disabled = 0);
IF @audit_index IS NULL
    OR (SELECT COUNT_BIG(*) FROM sys.index_columns WHERE object_id = OBJECT_ID(N'ctl.execution_audit', N'U')
        AND index_id = @audit_index) <> 2
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c
        ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND ic.index_id = @audit_index
          AND ic.key_ordinal = 1 AND ic.is_descending_key = 0 AND c.name = N'status')
    OR NOT EXISTS (SELECT 1 FROM sys.index_columns AS ic JOIN sys.columns AS c
        ON c.object_id = ic.object_id AND c.column_id = ic.column_id
        WHERE ic.object_id = OBJECT_ID(N'ctl.execution_audit', N'U') AND ic.index_id = @audit_index
          AND ic.key_ordinal = 2 AND ic.is_descending_key = 1 AND c.name = N'started_at')
    INSERT @failures VALUES (N'INDEX_SHAPE', N'ctl.IX_ctl_execution_audit_status_started_at',
        N'Índice reconstruído deve manter chaves, ordem e forma V025.');

IF @epoch = 104
    INSERT @failures VALUES (N'MIGRATION_PENDING', N'V105',
        N'A topologia V104 ainda exige V105 e validação física própria.');

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 54070, N'EPOCH_V104_V105_STRUCTURAL_DIVERGENCE', 1;
END;
PRINT N'EPOCH_V105_STRUCTURAL_PASS';

-- Field-scoped storage alignment for the literal Sinistro commentary accepted by the mapper.
-- Additive after V105: V038 remains immutable. No business DML, grants or Main adoption.
-- This file is a candidate until reviewed/applied by the authorized database owner.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 10000;

IF DB_NAME() <> N'ETL_SISTEMA_V2_SHADOW'
    THROW 54160, N'V106_EXACT_SHADOW_REQUIRED', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @table_id INT = OBJECT_ID(N'stg.expansion_lab_sin', N'U');
    DECLARE @column_id INT;
    DECLARE @collation SYSNAME;
    SELECT @column_id = column_id, @collation = collation_name
    FROM sys.columns
    WHERE object_id = @table_id AND name = N'insurance_claim_commentary';

    IF @table_id IS NULL OR @column_id IS NULL OR @collation IS NULL
        THROW 54161, N'V106_COMMENTARY_COLUMN_REQUIRED', 1;
    IF NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = @table_id AND column_id = @column_id
          AND system_type_id = TYPE_ID(N'nvarchar') AND user_type_id = TYPE_ID(N'nvarchar')
          AND max_length = 2048 AND is_nullable = 1 AND is_computed = 0
          AND is_identity = 0 AND is_sparse = 0 AND is_column_set = 0
          AND generated_always_type = 0 AND encryption_type IS NULL
    ) OR NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = @table_id AND name = N'insurance_claim_commentary_raw'
          AND system_type_id = TYPE_ID(N'nvarchar') AND user_type_id = TYPE_ID(N'nvarchar')
          AND max_length = 8000 AND is_nullable = 1 AND is_computed = 0
          AND collation_name = @collation
    ) OR NOT EXISTS (SELECT 1 FROM sys.fn_helpcollations() WHERE name = @collation)
        THROW 54162, N'V106_COMMENTARY_BASELINE_SHAPE_DRIFT', 1;

    -- Refuse unreviewed target dependencies; never drop/recreate any of them.
    IF EXISTS (SELECT 1 FROM sys.index_columns
               WHERE object_id = @table_id AND column_id = @column_id)
       OR EXISTS (SELECT 1 FROM sys.stats_columns
                  WHERE object_id = @table_id AND column_id = @column_id)
       OR EXISTS (SELECT 1 FROM sys.foreign_key_columns
                  WHERE (parent_object_id = @table_id AND parent_column_id = @column_id)
                     OR (referenced_object_id = @table_id AND referenced_column_id = @column_id))
       OR EXISTS (SELECT 1 FROM sys.sql_expression_dependencies
                  WHERE referenced_id = @table_id AND is_schema_bound_reference = 1
                    AND referenced_minor_id IN (0, @column_id))
        THROW 54163, N'V106_UNREVIEWED_COMMENTARY_DEPENDENCY', 1;

    -- Catalog metadata only: every other column, including raw/presence/wire, must remain identical.
    DECLARE @other_columns_before NVARCHAR(MAX) = (
        SELECT c.* FROM sys.columns AS c
        WHERE c.object_id = @table_id AND c.column_id <> @column_id
        ORDER BY c.column_id FOR JSON PATH, INCLUDE_NULL_VALUES
    );
    DECLARE @ddl NVARCHAR(MAX) = N'ALTER TABLE [stg].[expansion_lab_sin] '
        + N'ALTER COLUMN [insurance_claim_commentary] NVARCHAR(4000) COLLATE '
        + QUOTENAME(@collation) + N' NULL;';
    EXEC sys.sp_executesql @ddl;

    DECLARE @other_columns_after NVARCHAR(MAX) = (
        SELECT c.* FROM sys.columns AS c
        WHERE c.object_id = @table_id AND c.column_id <> @column_id
        ORDER BY c.column_id FOR JSON PATH, INCLUDE_NULL_VALUES
    );
    IF OBJECT_ID(N'stg.expansion_lab_sin', N'U') <> @table_id
       OR HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @other_columns_before))
          <> HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @other_columns_after))
       OR NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = @table_id AND column_id = @column_id
          AND name = N'insurance_claim_commentary'
          AND system_type_id = TYPE_ID(N'nvarchar') AND user_type_id = TYPE_ID(N'nvarchar')
          AND max_length = 8000 AND is_nullable = 1 AND is_computed = 0
          AND is_identity = 0 AND is_sparse = 0 AND is_column_set = 0
          AND generated_always_type = 0 AND encryption_type IS NULL AND collation_name = @collation
    )
        THROW 54164, N'V106_COMMENTARY_POSTCONDITION_FAILED', 1;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

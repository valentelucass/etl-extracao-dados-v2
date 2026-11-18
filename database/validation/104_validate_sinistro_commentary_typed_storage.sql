-- Read-only target storage validation after authorized V106 application.
-- Catalog metadata only: no DDL, DML, transactions, business-row reads or payload output.
-- This narrow check does not qualify the complete schema epoch, Main or operational adoption.
SET NOCOUNT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2_SHADOW'
    THROW 54170, N'SIN_COMMENTARY_EXACT_SHADOW_REQUIRED', 1;

DECLARE @table_id INT = OBJECT_ID(N'stg.expansion_lab_sin', N'U');
DECLARE @collation SYSNAME;
SELECT @collation = collation_name FROM sys.columns
WHERE object_id = @table_id AND name = N'insurance_claim_commentary';

IF @table_id IS NULL OR @collation IS NULL
    THROW 54171, N'SIN_COMMENTARY_TABLE_COLUMN_REQUIRED', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = @table_id AND name = N'insurance_claim_commentary'
      AND system_type_id = TYPE_ID(N'nvarchar') AND user_type_id = TYPE_ID(N'nvarchar')
      AND max_length = 8000 AND is_nullable = 1 AND is_computed = 0
      AND is_identity = 0 AND is_sparse = 0 AND is_column_set = 0
      AND generated_always_type = 0 AND encryption_type IS NULL
) OR NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = @table_id AND name = N'insurance_claim_commentary_raw'
      AND system_type_id = TYPE_ID(N'nvarchar') AND user_type_id = TYPE_ID(N'nvarchar')
      AND max_length = 8000 AND is_nullable = 1 AND is_computed = 0
      AND collation_name = @collation
) OR NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = @table_id AND name = N'insurance_claim_commentary_p'
      AND system_type_id = TYPE_ID(N'varchar') AND user_type_id = TYPE_ID(N'varchar')
      AND max_length = 6 AND is_nullable = 0
) OR NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = @table_id AND name = N'insurance_claim_commentary_w'
      AND system_type_id = TYPE_ID(N'varchar') AND user_type_id = TYPE_ID(N'varchar')
      AND max_length = 8 AND is_nullable = 0
)
    THROW 54172, N'SIN_COMMENTARY_STORAGE_SHAPE_MISMATCH', 1;

-- The mapper's other generic text fields still have the original NVARCHAR(1024) contract.
IF EXISTS (
    SELECT required.name FROM (VALUES
        (N'bo_number'), (N'brokers_case_number'), (N'customer_communication_contact_name'),
        (N'customer_communication_time'), (N'finished_commentary'), (N'icm_crn_psn_nickname'),
        (N'icm_dvr_iil_name'), (N'icm_fer_name'), (N'icm_fis_fit_pyr_nickname'),
        (N'icm_ttt_dealing_type'), (N'icm_ttt_ore_description'), (N'icm_ttt_solution_type'),
        (N'icm_vie_license_plate'), (N'informed_by'), (N'insurance_claim_location'),
        (N'internal_description'), (N'list_of_claimed_products'), (N'policy_number')
    ) AS required(name)
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.columns AS c
        WHERE c.object_id = @table_id AND c.name = required.name
          AND c.system_type_id = TYPE_ID(N'nvarchar') AND c.user_type_id = TYPE_ID(N'nvarchar')
          AND c.max_length = 2048 AND c.is_nullable = 1 AND c.is_computed = 0
    )
)
    THROW 54173, N'SIN_OTHER_TEXT_STORAGE_MUST_REMAIN_1024', 1;

SELECT N'SINISTRO_COMMENTARY_STORAGE_WIDTH_ONLY_VALIDATED' AS result,
    N'stg.expansion_lab_sin.insurance_claim_commentary' AS target_column,
    4000 AS typed_maximum_utf16_units, 8000 AS typed_maximum_bytes,
    4000 AS raw_maximum_utf16_units, 1024 AS other_generic_text_maximum_utf16_units,
    @collation AS preserved_column_collation,
    CAST(0 AS BIT) AS migration_history_verified,
    CAST(0 AS BIT) AS full_schema_epoch_verified,
    CAST(0 AS BIT) AS main_deployment_verified,
    CAST(0 AS BIT) AS operational_adoption_authorized;

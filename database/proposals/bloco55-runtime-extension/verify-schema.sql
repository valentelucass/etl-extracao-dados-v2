SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52850,N'EXACT_TARGET_REQUIRED',1;
IF OBJECT_ID(N'ctl.runtime_cotacao_reference') IS NULL OR OBJECT_ID(N'recon.runtime_vertical_output') IS NULL
 OR COL_LENGTH(N'core.manifesto_pick',N'freshness_at_utc') IS NULL
 OR COL_LENGTH(N'core.manifesto_mdfe',N'freshness_at_utc') IS NULL
 THROW 52850,N'V022_ADDITIONS_REQUIRED',1;
IF (SELECT COUNT(*) FROM sys.parameters WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_recovery'))<>9
 OR (SELECT COUNT(*) FROM sys.parameters WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_status'))<>9
 THROW 52850,N'OPTIONAL_TARIFF_PARAMETER_REQUIRED',1;
IF NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'ctl.trg_runtime_cotacao_reference_immutable') AND is_disabled=0)
 OR NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'recon.trg_runtime_vertical_output_immutable') AND is_disabled=0)
 THROW 52850,N'IMMUTABLE_EVIDENCE_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE major_id IN(OBJECT_ID(N'ctl.fn_runtime_tariff_valid'),OBJECT_ID(N'recon.usp_capture_runtime_vertical_output')))
 THROW 52850,N'INTERNAL_HELPER_GRANT_FORBIDDEN',1;
PRINT N'V022_SCHEMA_SHAPE_VERIFIED';

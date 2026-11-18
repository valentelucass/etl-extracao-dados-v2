-- Read-only postcondition of the container, not acceptance of a production schema.
SET NOCOUNT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2'
    THROW 55021, N'V2_DATABASE_VERIFY_EXACT_TARGET_REQUIRED', 1;
IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE database_id=DB_ID()
    AND name=N'ETL_SISTEMA_V2' AND state_desc=N'ONLINE' AND compatibility_level=160
    AND collation_name=N'Latin1_General_100_CI_AS_SC' AND is_read_only=0
    AND is_auto_close_on=0 AND is_auto_shrink_on=0 AND is_read_committed_snapshot_on=1
    AND is_trustworthy_on=0 AND is_db_chaining_on=0 AND recovery_model_desc=N'FULL'
    AND page_verify_option_desc=N'CHECKSUM')
    THROW 55022, N'V2_DATABASE_ATTRIBUTE_MISMATCH', 1;
IF EXISTS (SELECT 1 FROM sys.objects WHERE is_ms_shipped=0)
   OR EXISTS (SELECT 1 FROM sys.database_principals WHERE is_fixed_role=0
              AND name NOT IN(N'dbo',N'guest',N'INFORMATION_SCHEMA',N'sys',N'public'))
    THROW 55023, N'V2_DATABASE_NOT_EMPTY_CONTAINER', 1;
IF (SELECT COUNT(*) FROM sys.database_files)<>2
   OR EXISTS (SELECT 1 FROM sys.database_files WHERE is_percent_growth<>0 OR growth<>8192)
    THROW 55024, N'V2_DATABASE_FILE_POLICY_MISMATCH', 1;
SELECT DB_NAME() database_name,CONVERT(VARCHAR(32),SERVERPROPERTY('ProductVersion')) product_version,
 d.collation_name,d.compatibility_level,d.recovery_model_desc,d.is_read_committed_snapshot_on,
 d.page_verify_option_desc,
 (SELECT COUNT(*) FROM sys.objects WHERE is_ms_shipped=0) application_objects,
 (SELECT name,type_desc,size*8/1024 size_mb,growth*8/1024 growth_mb FROM sys.database_files
  ORDER BY file_id FOR JSON PATH) files,
 N'CONTAINER_ONLY_SCHEMA_ACCESS_AND_DATA_PENDING' qualification
FROM sys.databases d WHERE d.database_id=DB_ID() FOR JSON PATH;

-- Only the pending V019/V020. No grant, seed, renewal, reset or historical reinstall.
:On Error exit
:r "..\..\validation\053_validate_local_runtime_provisioning.sql"
GO
IF OBJECT_ID(N'ctl.usp_runtime_observation') IS NOT NULL OR OBJECT_ID(N'recon.usp_runtime_raise_alert') IS NOT NULL THROW 52513,N'PENDING_V019_ONLY',1;
BEGIN TRANSACTION;
GO
:r "..\..\migrations\V019__create_scoped_runtime_observability.sql"
GO
:r "..\..\migrations\V020__bind_existing_runtime_occurrences.sql"
GO
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52513,N'LOCAL_TARGET_REQUIRED',1;
IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_observation')) <> '53bc5865a9588fdd982eaa67c92d42fb817e26ad2f987f385e5818c60a906531'
 OR OBJECT_ID(N'ctl.usp_runtime_observation') IS NULL
 OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'recon.usp_runtime_raise_alert')) <> 'e3295b113a4bc2e62ee695ebb67b3cd59e12e659afaf88295f65ec260a034a4e'
 OR OBJECT_ID(N'recon.usp_runtime_raise_alert') IS NULL THROW 52513,N'EXACT_REVIEWED_SCOPED_MODULES_REQUIRED',1;
GO

IF OBJECT_ID(N'ctl.fn_runtime_consumed_scope') IS NULL OR OBJECT_ID(N'ctl.usp_control_plane_register_incremental_frontier') IS NULL THROW 52513,N'V020_EXISTING_OCCURRENCE_FENCE_REQUIRED',1;
IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.fn_runtime_consumed_scope'))<>'f173775ca477058ed583fbb3fbb68c656e0f4c1df0aa9c7ebfeb01ce5f0444c4' THROW 52513,N'V020_MODULE_BYTES_REQUIRED',1;
IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_control_plane_register_incremental_frontier'))<>'617ed12fc498783dc6619b1234e26b2c41a01aad91c0b4c593171014fa0b7b91' THROW 52513,N'V020_MODULE_BYTES_REQUIRED',1;
GO

IF OBJECT_ID(N'ctl.usp_runtime_temporal_plan') IS NULL OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_temporal_plan'))<>'2509ff6699c834a5720ef6f2f3b05ccecd2a44b9dd33c2556a0c70a8062ec825' THROW 52513,N'V020_TEMPORAL_WINDOW_BYTES_REQUIRED',1;
GO

COMMIT TRANSACTION;
PRINT N'V019_V020_SCHEMA_ONLY_COMMITTED_GRANTS_UNCHANGED';
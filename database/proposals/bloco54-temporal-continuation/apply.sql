-- Additive V021 only. Original V001..V020, rows, accounts, grants and mappings remain intact.
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS')
    THROW 52754,N'EXACT_LOCAL_WINDOWS_TARGET_REQUIRED',1;
IF @@TRANCOUNT<>0 OR OBJECT_ID(N'ctl.fn_runtime_consumed_temporal_scope') IS NOT NULL
    THROW 52754,N'V021_NOT_PENDING_RECONCILE_INSTALLED_STATE',1;
:r "..\bloco54-observability\verify-retained-replay.sql"
GO
BEGIN TRANSACTION;
GO
:r "..\..\migrations\V021__bind_durable_temporal_intent.sql"
GO
:r "verify.sql"
GO
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52754,N'EXACT_MIGRATION_TRANSACTION_REQUIRED',1;
COMMIT TRANSACTION;
PRINT N'V021_COMMITTED_SCHEMA_ONLY_NO_GRANTS';
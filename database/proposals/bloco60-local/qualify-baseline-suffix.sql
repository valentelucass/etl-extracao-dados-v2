:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 OR OBJECT_ID(N'ctl.source_protocol_binding') IS NOT NULL
 THROW 52970,N'B60_V023_TRANSACTION_REQUIRED',1;
BEGIN TRANSACTION;
GO
:r "..\..\migrations\V024__bind_source_protocols_and_users_runtime.sql"
:r "..\..\validation\056_validate_bloco60_runtime.sql"
:r "catalog.sql"
:r "preservation.sql"
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52970,N'B60_ROLLBACK_REQUIRED',1;
ROLLBACK TRANSACTION;
SELECT N'B60_BASELINE_SUFFIX_QUALIFIED_AND_ROLLED_BACK';
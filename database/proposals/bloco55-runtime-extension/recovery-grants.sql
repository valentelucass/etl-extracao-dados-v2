-- Explicit withdrawal only. Never executed automatically and never removes data.
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52850,N'EXACT_TARGET_REQUIRED',1;
DECLARE @service SYSNAME=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',
 @operator SYSNAME=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_view';
IF USER_ID(@service) IS NULL OR USER_ID(@operator) IS NULL THROW 52850,N'EXISTING_ACCOUNTS_REQUIRED',1;
BEGIN TRANSACTION;
DECLARE @commands NVARCHAR(MAX)=N'';
SELECT @commands=@commands+N'REVOKE EXECUTE ON OBJECT::'+procedure_name+N' FROM '+QUOTENAME(@service)+N';'
FROM (VALUES(N'stg.usp_stage_manifesto_observation'),(N'core.usp_prepare_manifesto_candidate_set'),
 (N'core.usp_apply_reconcile_publish_manifestos'),(N'stg.usp_stage_cotacao_record'),
 (N'core.usp_apply_reconcile_publish_cotacoes'),(N'stg.usp_stage_localizacao_carga_record'),
 (N'core.usp_apply_reconcile_publish_localizacao_cargas')) p(procedure_name);
EXEC sys.sp_executesql @commands;
UPDATE ctl.runtime_identity_scope SET revoked=1,scope_version=scope_version+1
WHERE original_sid IN(SUSER_SID(@service),SUSER_SID(@operator)) AND environment_name=N'LOCAL_SHADOW'
 AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' AND mode=N'BACKFILL'
 AND workload IN(N'manifestos',N'cotacoes',N'localizacao_cargas') AND revoked=0;
IF @@ROWCOUNT<>6 THROW 52850,N'EXACT_SIX_NEW_SCOPES_REQUIRED_RECONCILE_BEFORE_REPEAT',1;
COMMIT TRANSACTION;
PRINT N'B55_NEW_GRANTS_WITHDRAWN_NEW_SCOPES_REVOKED_DATA_PRESERVED';

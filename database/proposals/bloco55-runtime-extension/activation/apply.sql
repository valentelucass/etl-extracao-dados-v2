:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR OBJECT_ID(N'ctl.runtime_cotacao_reference') IS NULL THROW 52852,N'V022_EXACT_TARGET_REQUIRED',1;
:r "verify-before.sql"
GO
BEGIN TRANSACTION;
DECLARE @service SYSNAME=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',
 @policy CHAR(64)='c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d',@sql NVARCHAR(MAX)=N'';
SELECT @sql=@sql+N'GRANT EXECUTE ON OBJECT::'+p.name+N' TO '+QUOTENAME(@service)+N';' FROM (VALUES
 (N'stg.usp_stage_manifesto_observation'),
 (N'core.usp_prepare_manifesto_candidate_set'),
 (N'core.usp_apply_reconcile_publish_manifestos'),
 (N'stg.usp_stage_cotacao_record'),
 (N'core.usp_apply_reconcile_publish_cotacoes'),
 (N'stg.usp_stage_localizacao_carga_record'),
 (N'core.usp_apply_reconcile_publish_localizacao_cargas'))p(name);
EXEC sys.sp_executesql @sql;
INSERT ctl.runtime_identity_scope(original_sid,environment_name,source_instance,tenant_scope,workload,mode,scope_version,policy_fingerprint,revoked)
SELECT m.original_sid,N'LOCAL_SHADOW',N'LOCAL_V2',N'LOCAL_V2',w.workload,N'BACKFILL',1,@policy,0
FROM ctl.runtime_identity_mapping m CROSS JOIN (VALUES(N'manifestos'),(N'cotacoes'),(N'localizacao_cargas')) w(workload);
IF @@ROWCOUNT<>6 THROW 52852,N'SCOPE_DELTA_REQUIRED',1;
GO
:r "seed-quality.sql"
GO
:r "seed-tariff.sql"
GO
:r "verify-profile.sql"
GO
:r "verify-seeds.sql"
GO
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52852,N'EXACT_TRANSACTION_REQUIRED',1;
COMMIT TRANSACTION;
PRINT N'B55_EXACT_ACTIVATION_COMMITTED';
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' OR @@TRANCOUNT<>0
 OR SYSUTCDATETIME()<'2026-09-10T00:00:00' OR SYSUTCDATETIME()>='2026-09-16T00:00:00'
 THROW 52970,N'B60R_ACTIVATION_WINDOW_REQUIRED',1;
BEGIN TRANSACTION;
:r "profile-before.sql"
GRANT EXECUTE ON OBJECT::stg.usp_stage_usuario_record TO [RTR-SVW-002\etl_v2_exec];
GRANT EXECUTE ON OBJECT::core.usp_apply_reconcile_publish_usuarios TO [RTR-SVW-002\etl_v2_exec];
UPDATE ctl.runtime_identity_scope SET scope_version=13,revoked=0
 WHERE workload=N'usuarios' AND environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2'
 AND original_sid IN(SUSER_SID(N'RTR-SVW-002\etl_v2_exec'),SUSER_SID(N'RTR-SVW-002\etl_v2_view'))
 AND mode IN(N'BACKFILL',N'REPLAY') AND scope_version=12 AND revoked=1;
IF @@ROWCOUNT<>4 THROW 52970,N'B60R_EXACT_FOUR_EXISTING_SCOPES',1;
UPDATE ctl.runtime_identity_mapping SET replay=1,force_run=1,mapping_version=30
 WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec') AND mapping_version=29 AND replay=0 AND force_run=0;
IF @@ROWCOUNT<>1 THROW 52970,N'B60R_EXACT_SERVICE_VERSION',1;
:r "seed-quality.sql"
:r "verify-quality.sql"
GO
:r "profile-active.sql"
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52970,N'B60R_SINGLE_COMMIT_REQUIRED',1;
COMMIT TRANSACTION;
SELECT N'B60R_ACTIVATED_34_GRANTS_20_SCOPES_SERVICE_30';
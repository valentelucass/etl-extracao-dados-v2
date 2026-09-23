:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte'
 THROW 52970,N'B60_COMPENSATION_TARGET',1;
BEGIN TRANSACTION;
IF EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec') AND mapping_version NOT IN(25,26,27))
 THROW 52970,N'B60_COMPENSATION_MAPPING_DRIFT',1;
UPDATE ctl.runtime_identity_mapping SET replay=0,force_run=0,mapping_version=27
 WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec') AND mapping_version=26 AND replay=1 AND force_run=1;
UPDATE ctl.runtime_identity_scope SET revoked=1,scope_version=10 WHERE workload=N'usuarios'
 AND environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2'
 AND original_sid IN(SUSER_SID(N'RTR-SVW-002\etl_v2_exec'),SUSER_SID(N'RTR-SVW-002\etl_v2_view'))
 AND mode IN(N'BACKFILL',N'REPLAY') AND scope_version=9 AND revoked=0;
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED'
 WHERE policy_version IN(N'bloco60-usuarios-backfill-v5',N'bloco60-usuarios-replay-v5') AND policy_state=N'RATIFIED';
REVOKE EXECUTE ON OBJECT::stg.usp_stage_usuario_record FROM [RTR-SVW-002\etl_v2_exec];
REVOKE EXECUTE ON OBJECT::core.usp_apply_reconcile_publish_usuarios FROM [RTR-SVW-002\etl_v2_exec];
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52970,N'B60_COMPENSATION_SINGLE_COMMIT',1;
:r "profile-after.sql"
COMMIT TRANSACTION;
SELECT N'B60_SCOPES_REVOKED_RECEIPTS_DATA_AND_V024_PRESERVED';
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 OR OBJECT_ID(N'ctl.source_protocol_binding') IS NULL
 OR SYSUTCDATETIME()<'2026-09-09T00:00:00' OR SYSUTCDATETIME()>='2026-09-16T00:00:00'
 THROW 52970,N'B60_ACTIVATION_WINDOW_REQUIRED',1;
:r "profile-before.sql"
BEGIN TRANSACTION;
GRANT EXECUTE ON OBJECT::stg.usp_stage_usuario_record TO [RTR-SVW-002\etl_v2_exec];
GRANT EXECUTE ON OBJECT::core.usp_apply_reconcile_publish_usuarios TO [RTR-SVW-002\etl_v2_exec];
INSERT ctl.source_protocol_binding VALUES(N'LOCAL_V2',N'GRAPHQL',SYSUTCDATETIME());
INSERT ctl.runtime_identity_scope(original_sid,environment_name,source_instance,tenant_scope,workload,mode,scope_version,policy_fingerprint,revoked)
SELECT m.original_sid,N'LOCAL_SHADOW',N'LOCAL_V2',N'LOCAL_V2',N'usuarios',s.mode,1,
 'c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d',0
FROM ctl.runtime_identity_mapping m CROSS JOIN (VALUES(N'BACKFILL'),(N'REPLAY'))s(mode)
WHERE m.original_sid IN(SUSER_SID(N'RTR-SVW-002\etl_v2_exec'),SUSER_SID(N'RTR-SVW-002\etl_v2_view'));
IF @@ROWCOUNT<>4 THROW 52970,N'B60_EXACT_FOUR_SCOPES',1;
UPDATE ctl.runtime_identity_mapping SET replay=1,force_run=1,mapping_version=18
WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec') AND mapping_version=17 AND replay=0 AND force_run=0;
IF @@ROWCOUNT<>1 THROW 52970,N'B60_EXACT_SERVICE_VERSION',1;
:r "seed-quality.sql"
:r "verify-quality.sql"
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52970,N'B60_SINGLE_COMMIT_REQUIRED',1;
COMMIT TRANSACTION;
SELECT N'B60_ACTIVATION_COMMITTED_34_GRANTS_20_SCOPES_SERVICE_18';
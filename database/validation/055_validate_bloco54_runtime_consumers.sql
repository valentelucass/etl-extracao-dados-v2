:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52502,N'LOCAL_TARGET_REQUIRED',1;
IF OBJECT_ID(N'ctl.fn_runtime_consumed_scope',N'FN') IS NULL THROW 52502,N'DURABLE_CONSUMER_FUNCTION_REQUIRED',1;
DECLARE @expected TABLE(name SYSNAME PRIMARY KEY);
INSERT @expected VALUES(N'ctl.usp_runtime_status'),(N'ctl.usp_runtime_recovery'),
 (N'ctl.usp_control_plane_register_source'),(N'ctl.usp_control_plane_start_cycle'),(N'ctl.usp_control_plane_start_execution'),
 (N'ctl.usp_control_plane_heartbeat_lease'),(N'ctl.usp_control_plane_record_page'),(N'ctl.usp_control_plane_record_counts'),
 (N'ctl.usp_control_plane_transition_execution'),(N'ctl.usp_control_plane_register_incremental_frontier'),
 (N'stg.usp_stage_coleta_record'),(N'stg.usp_stage_frete_record'),(N'core.usp_prepare_staged_execution'),
 (N'core.usp_prepare_frete_candidate_set'),(N'core.usp_apply_reconcile_publish_coletas'),(N'core.usp_apply_reconcile_publish_fretes'),
 (N'recon.usp_evaluate_execution_data_quality'),(N'ctl.usp_runtime_temporal_plan'),(N'ctl.usp_runtime_temporal_gaps');
IF EXISTS(SELECT 1 FROM @expected WHERE OBJECT_DEFINITION(OBJECT_ID(name)) NOT LIKE N'%RUNTIME_DURABLE_CONSUMER_FENCE_REQUIRED%'
 OR OBJECT_DEFINITION(OBJECT_ID(name)) IS NULL) THROW 52502,N'ALL_19_CONSUMERS_REQUIRE_FENCE',1;
IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.fn_runtime_consumed_scope')) LIKE N'%SESSION_CONTEXT%'
 THROW 52502,N'CALLER_CONTEXT_CANNOT_AUTHORIZE',1;
IF CHARINDEX(N'DATALENGTH(k.name)<>DATALENGTH(j.[key])',OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_runtime_authorization')))=0
 THROW 52502,N'EXACT_SCOPE_KEYS_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE major_id=OBJECT_ID(N'ctl.fn_runtime_consumed_scope'))
 THROW 52502,N'CONSUMER_FUNCTION_CANNOT_BE_GRANTED',1;
PRINT N'B54_EXACT_19_DURABLE_CONSUMERS_PASS';

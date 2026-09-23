SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52513,N'LOCAL_TARGET_REQUIRED',1;
IF OBJECT_ID(N'ctl.fn_runtime_consumed_scope') IS NULL OR OBJECT_ID(N'ctl.usp_control_plane_register_incremental_frontier') IS NULL THROW 52513,N'V020_EXISTING_OCCURRENCE_FENCE_REQUIRED',1;
IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.fn_runtime_consumed_scope'))<>'f173775ca477058ed583fbb3fbb68c656e0f4c1df0aa9c7ebfeb01ce5f0444c4' THROW 52513,N'V020_MODULE_BYTES_REQUIRED',1;
IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_control_plane_register_incremental_frontier'))<>'617ed12fc498783dc6619b1234e26b2c41a01aad91c0b4c593171014fa0b7b91' THROW 52513,N'V020_MODULE_BYTES_REQUIRED',1;
GO

IF (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_observation')) <> '53bc5865a9588fdd982eaa67c92d42fb817e26ad2f987f385e5818c60a906531'
 OR OBJECT_ID(N'ctl.usp_runtime_observation') IS NULL
 OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'recon.usp_runtime_raise_alert')) <> 'e3295b113a4bc2e62ee695ebb67b3cd59e12e659afaf88295f65ec260a034a4e'
 OR OBJECT_ID(N'recon.usp_runtime_raise_alert') IS NULL THROW 52513,N'EXACT_REVIEWED_SCOPED_MODULES_REQUIRED',1;
GO
IF OBJECT_ID(N'ctl.usp_runtime_temporal_plan') IS NULL OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_temporal_plan'))<>'c7f98cfef5e0da091e45e3990237f4452f72adea8df32a548bdfe5d9124ab06c' THROW 52513,N'V021_TEMPORAL_WINDOW_BYTES_REQUIRED',1;
GO

-- Administrative read-only validation of the exact owner-authorized local account package.
:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52463,N'LOCAL_V2_TARGET_REQUIRED',1;
DECLARE @machine NVARCHAR(128)=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName')),
 @service SYSNAME,@operator SYSNAME,@policy CHAR(64)='c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d';
SET @service=@machine+N'\etl_v2_exec'; SET @operator=@machine+N'\etl_v2_view';
IF (SELECT COUNT_BIG(*) FROM sys.server_principals WHERE name IN(@service,@operator)
 AND type='U' AND is_disabled=0 AND default_database_name=N'ETL_SISTEMA_V2_SHADOW')<>2
 THROW 52463,N'EXACT_WINDOWS_LOGINS_REQUIRED',1;
IF ISNULL(IS_SRVROLEMEMBER(N'sysadmin',@service),1)<>0 OR ISNULL(IS_SRVROLEMEMBER(N'sysadmin',@operator),1)<>0
 THROW 52463,N'ADMINISTRATIVE_RUNTIME_LOGIN_FORBIDDEN',1;
IF (SELECT COUNT_BIG(*) FROM sys.database_principals WHERE name IN(@service,@operator)
 AND type='U' AND sid=SUSER_SID(name))<>2 THROW 52463,N'EXACT_WINDOWS_DATABASE_USERS_REQUIRED',1;
IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(USER_ID(@service),USER_ID(@operator)))
 THROW 52463,N'BROAD_RUNTIME_MEMBERSHIP_FORBIDDEN',1;
DECLARE @expected TABLE(principal_id INT,object_id INT,PRIMARY KEY(principal_id,object_id));
INSERT @expected SELECT USER_ID(@service),OBJECT_ID(p.name) FROM (VALUES
 (N'ctl.usp_runtime_authorization'),(N'ctl.usp_runtime_status'),(N'ctl.usp_runtime_recovery'),
 (N'ctl.usp_control_plane_register_source'),(N'ctl.usp_control_plane_start_cycle'),(N'ctl.usp_control_plane_start_execution'),
 (N'ctl.usp_control_plane_heartbeat_lease'),(N'ctl.usp_control_plane_record_page'),(N'ctl.usp_control_plane_record_counts'),
 (N'ctl.usp_control_plane_transition_execution'),(N'ctl.usp_control_plane_register_incremental_frontier'),
 (N'stg.usp_stage_coleta_record'),(N'stg.usp_stage_frete_record'),(N'core.usp_prepare_staged_execution'),
 (N'core.usp_prepare_frete_candidate_set'),(N'core.usp_apply_reconcile_publish_coletas'),(N'core.usp_apply_reconcile_publish_fretes'),
 (N'recon.usp_evaluate_execution_data_quality'),(N'ctl.usp_runtime_temporal_plan'),(N'ctl.usp_runtime_temporal_gaps'))p(name);
INSERT @expected VALUES(USER_ID(@operator),OBJECT_ID(N'ctl.usp_runtime_authorization')),
 (USER_ID(@operator),OBJECT_ID(N'ctl.usp_runtime_status'));
INSERT @expected VALUES (USER_ID(@service),OBJECT_ID(N'ctl.usp_runtime_observation')),
 (USER_ID(@service),OBJECT_ID(N'recon.usp_runtime_raise_alert')),
 (USER_ID(@operator),OBJECT_ID(N'ctl.usp_runtime_observation'));
IF EXISTS(SELECT principal_id,object_id FROM @expected EXCEPT SELECT grantee_principal_id,major_id
 FROM sys.database_permissions WHERE class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G')
 OR EXISTS(SELECT grantee_principal_id,major_id FROM sys.database_permissions
 WHERE grantee_principal_id IN(USER_ID(@service),USER_ID(@operator)) AND class=1
 EXCEPT SELECT principal_id,object_id FROM @expected)
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN(USER_ID(@service),USER_ID(@operator))
 AND NOT(class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G'
 OR class=0 AND permission_name='CONNECT' AND state='G'))
 THROW 52463,N'EXACT_25_PROCEDURE_GRANTS_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_authority_configuration)<>1 OR NOT EXISTS(
 SELECT 1 FROM ctl.runtime_authority_configuration WHERE singleton=1 AND enabled=1 AND capability_seconds=30
 AND server_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')) AND database_name=DB_NAME() AND policy_fingerprint=@policy)
 THROW 52463,N'LOCAL_AUTHORITY_CONFIGURATION_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_mapping)<>2 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_mapping
 WHERE original_sid IN(SUSER_SID(@service),SUSER_SID(@operator)) AND revoked=0 AND observer=1 AND replay=0 AND force_run=0
 AND valid_from_utc<=SYSUTCDATETIME() AND valid_until_utc>SYSUTCDATETIME()
 AND DATEDIFF_BIG(SECOND,valid_from_utc,valid_until_utc)<=2678400
 AND ((original_sid=SUSER_SID(@service) AND principal_kind='SERVICE' AND executor=1)
 OR(original_sid=SUSER_SID(@operator) AND principal_kind='OPERATOR' AND executor=0)))<>2
 THROW 52463,N'EXACT_RESTRICTED_MAPPINGS_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope)<>10
 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE mode IN(N'BACKFILL',N'INCREMENTAL'))<>8
 OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE original_sid NOT IN(SUSER_SID(@service),SUSER_SID(@operator))
 OR environment_name<>N'LOCAL_SHADOW' OR source_instance<>N'LOCAL_V2' OR tenant_scope<>N'LOCAL_V2'
 OR workload NOT IN(N'coletas',N'fretes') OR policy_fingerprint<>@policy OR scope_version<1
 OR NOT(mode IN(N'BACKFILL',N'INCREMENTAL') AND revoked=0
 OR mode=N'REPLAY' AND original_sid=SUSER_SID(@service) AND revoked=1 AND scope_version>=2))
 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE mode=N'REPLAY' AND original_sid=SUSER_SID(@service) AND revoked=1)<>2
 THROW 52515,N'EXACT_EIGHT_ORIGINAL_AND_TWO_REVOKED_REPLAY_SCOPES_REQUIRED',1;
IF EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE valid_until_utc<>CONVERT(DATETIME2(3),'2026-10-07T22:34:30.615'))
 THROW 52515,N'ORIGINAL_MAPPING_VALIDITY_REQUIRED',1;
PRINT N'B54_EXACT_25_GRANTS_8_ORIGINAL_2_REVOKED_REPLAY_PASS';
IF OBJECT_ID(N'ctl.fn_runtime_consumed_temporal_scope') IS NULL OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.fn_runtime_consumed_temporal_scope'))<>'e4d4a6cbfa781b60ad9cfc466536afbcd325ae2acb1ce2345552116442ee0695' THROW 52754,N'EXACT_V021_MODULE_REQUIRED',1;
GO

IF OBJECT_ID(N'ctl.usp_runtime_temporal_plan') IS NULL OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_temporal_plan'))<>'c7f98cfef5e0da091e45e3990237f4452f72adea8df32a548bdfe5d9124ab06c' THROW 52754,N'EXACT_V021_MODULE_REQUIRED',1;
GO

IF OBJECT_ID(N'ctl.usp_runtime_temporal_gaps') IS NULL OR (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',definition),2)) FROM sys.sql_modules WHERE object_id=OBJECT_ID(N'ctl.usp_runtime_temporal_gaps'))<>'2f112cf25fb02d0af955dbec7a5e2cb891c9ebb50a7f5b7090a22e973168ac79' THROW 52754,N'EXACT_V021_MODULE_REQUIRED',1;
GO
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE major_id=OBJECT_ID(N'ctl.fn_runtime_consumed_temporal_scope')) THROW 52754,N'NO_TEMPORAL_FUNCTION_GRANT_ALLOWED',1;
PRINT N'V021_TEMPORAL_PROFILE_EXACT_25_GRANTS_PASS';

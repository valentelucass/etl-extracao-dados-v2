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
INSERT @expected SELECT USER_ID(@service),OBJECT_ID(p.name) FROM (VALUES
 (N'stg.usp_stage_manifesto_observation'),
 (N'core.usp_prepare_manifesto_candidate_set'),
 (N'core.usp_apply_reconcile_publish_manifestos'),
 (N'stg.usp_stage_cotacao_record'),
 (N'core.usp_apply_reconcile_publish_cotacoes'),
 (N'stg.usp_stage_localizacao_carga_record'),
 (N'core.usp_apply_reconcile_publish_localizacao_cargas'))p(name);
IF EXISTS(SELECT principal_id,object_id FROM @expected EXCEPT SELECT grantee_principal_id,major_id
 FROM sys.database_permissions WHERE class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G')
 OR EXISTS(SELECT grantee_principal_id,major_id FROM sys.database_permissions
 WHERE grantee_principal_id IN(USER_ID(@service),USER_ID(@operator)) AND class=1
 EXCEPT SELECT principal_id,object_id FROM @expected)
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN(USER_ID(@service),USER_ID(@operator))
 AND NOT(class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G'
 OR class=0 AND permission_name='CONNECT' AND state='G'))
 THROW 52463,N'EXACT_32_PROCEDURE_GRANTS_REQUIRED',1;
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
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload<>N'usuarios')<>16
 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload<>N'usuarios' AND mode IN(N'BACKFILL',N'INCREMENTAL'))<>14
 OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE workload<>N'usuarios' AND (original_sid NOT IN(SUSER_SID(@service),SUSER_SID(@operator))
 OR environment_name<>N'LOCAL_SHADOW' OR source_instance<>N'LOCAL_V2' OR tenant_scope<>N'LOCAL_V2'
 OR workload NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas') OR policy_fingerprint<>@policy OR scope_version<1
 OR NOT(mode IN(N'BACKFILL',N'INCREMENTAL') AND revoked=0
 OR mode=N'REPLAY' AND original_sid=SUSER_SID(@service) AND revoked=1 AND scope_version>=2)))
 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload<>N'usuarios' AND mode=N'REPLAY' AND original_sid=SUSER_SID(@service) AND revoked=1)<>2
 THROW 52515,N'EXACT_EIGHT_ORIGINAL_AND_TWO_REVOKED_REPLAY_SCOPES_REQUIRED',1;
IF EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE valid_until_utc<>CONVERT(DATETIME2(3),'2026-10-07T22:34:30.615'))
 THROW 52515,N'ORIGINAL_MAPPING_VALIDITY_REQUIRED',1;

IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload IN(N'manifestos',N'cotacoes',N'localizacao_cargas') AND mode=N'BACKFILL' AND revoked=0 AND scope_version=1)<>6
 THROW 52852,N'EXACT_SIX_NEW_BACKFILL_SCOPES',1;
IF NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE principal_kind='SERVICE' AND mapping_version=21)
 OR NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE principal_kind='OPERATOR' AND mapping_version=1)
 OR NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE scope_id=1 AND scope_version=10)
 OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE scope_id BETWEEN 2 AND 8 AND scope_version<>1)
 THROW 52852,N'ORIGINAL_VERSIONS_MUST_REMAIN',1;

IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope)<>20
 OR (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload=N'usuarios' AND environment_name=N'LOCAL_SHADOW'
 AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' AND mode IN(N'BACKFILL',N'REPLAY')
 AND original_sid IN(SUSER_SID(@service),SUSER_SID(@operator)) AND scope_version=4 AND revoked=1
 AND policy_fingerprint=@policy)<>4 THROW 52970,N'B60_EXACT_USERS_SCOPES',1;
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v1',N'bloco60-usuarios-replay-v1') AND policy_state=N'REVOKED')<>2
 THROW 52970,N'B60_EXACT_POLICY_STATE',1;
IF (SELECT COUNT_BIG(*) FROM ctl.source_protocol_binding WHERE source_instance=N'LOCAL_V2' AND source_kind IN(N'GRAPHQL',N'DATA_EXPORT'))<>2
 OR NOT EXISTS(SELECT 1 FROM ctl.source_catalog WHERE source_instance=N'LOCAL_V2' AND source_kind=N'DATA_EXPORT' AND active=1)
 THROW 52970,N'B60_SHARED_LOGICAL_SOURCE',1;
SELECT N'B60S_PROFILE_BEFORE_PASS';
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v2',N'bloco60-usuarios-replay-v2') AND policy_state=N'REVOKED')<>2
 THROW 52970,N'B60R_NEW_POLICIES_REVOKED',1;
IF EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v3',N'bloco60-usuarios-replay-v3')) THROW 52970,N'B60S_POLICY_COLLISION',1;
IF EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE scope_fingerprint IN('88d26323f8226ae3fa1792ae264f3696713ba5e117939b24f0f9587566489506','9ab375081cab9e8f4515c10f37438f475020bb53a730bbd45693bf3cb473fdc8') AND effective_from_utc=CONVERT(DATETIME2(3),'2026-09-10T00:00:00.125')) THROW 52970,N'B60U_POLICY_EFFECTIVE_COLLISION',1;

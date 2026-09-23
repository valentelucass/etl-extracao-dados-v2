:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' THROW 52970,N'B60_RECOVERY_TARGET',1;
DECLARE @version BIGINT=(SELECT mapping_version FROM ctl.runtime_identity_mapping WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec')),
 @scopes BIGINT=(SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload=N'usuarios'),
 @active BIGINT=(SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload=N'usuarios' AND revoked=0),
 @policies BIGINT=(SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v5',N'bloco60-usuarios-replay-v5') AND policy_state=N'RATIFIED'),
 @grants BIGINT=(SELECT COUNT_BIG(*) FROM sys.database_permissions WHERE grantee_principal_id=USER_ID(N'RTR-SVW-002\etl_v2_exec') AND permission_name=N'EXECUTE' AND state=N'G'
 AND major_id IN(OBJECT_ID(N'stg.usp_stage_usuario_record'),OBJECT_ID(N'core.usp_apply_reconcile_publish_usuarios')));
SELECT CASE WHEN @version=25 AND @scopes=4 AND @active=0 AND @policies=0 AND @grants=0 THEN N'UNACTIVATED'
 WHEN @version=26 AND @scopes=4 AND @active=4 AND @policies=2 AND @grants=2 THEN N'ACTIVE'
 WHEN @version=27 AND @scopes=4 AND @active=0 AND @policies=0 AND @grants=0 THEN N'COMPENSATED'
 ELSE N'MIXED_STOP' END status,CASE WHEN OBJECT_ID(N'ctl.source_protocol_binding') IS NULL THEN 0 ELSE 1 END protocolSchema,
 @version serviceVersion,@scopes usersScopes,@active activeUsersScopes,@policies activePolicies,@grants usersGrants
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;

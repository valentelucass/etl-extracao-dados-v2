-- Read-only structural and privilege assertions; no principal or mapping seed.
-- Schema-install phase only. After authorized account provisioning, use validator 053.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52440,N'LOCAL_TARGET_REQUIRED',1;
DECLARE @objects TABLE(name SYSNAME PRIMARY KEY,kind CHAR(2));
INSERT @objects VALUES
 (N'runtime_authority_configuration','U'),(N'runtime_identity_mapping','U'),(N'runtime_identity_scope','U'),
 (N'runtime_authorization_decision','U'),(N'runtime_authorization_consumption','U'),
 (N'runtime_temporal_policy','U'),(N'runtime_temporal_window','U'),
 (N'usp_runtime_authorization','P'),(N'usp_runtime_status','P'),(N'usp_runtime_temporal_plan','P'),(N'usp_runtime_temporal_gaps','P'),
 (N'tr_runtime_authorization_decision_immutable','TR'),(N'tr_runtime_authorization_consumption_immutable','TR'),
 (N'tr_runtime_temporal_policy_immutable','TR'),(N'tr_runtime_temporal_window_immutable','TR');
IF EXISTS(SELECT 1 FROM @objects WHERE OBJECT_ID(N'ctl.'+name,kind) IS NULL)
 THROW 52440,N'AUTHORITY_TEMPORAL_OBJECT_REQUIRED',1;
IF EXISTS(SELECT 1 FROM @objects e JOIN sys.triggers t ON t.object_id=OBJECT_ID(N'ctl.'+e.name)
 WHERE e.kind='TR' AND (t.is_disabled=1 OR t.is_instead_of_trigger<>1))
 THROW 52440,N'APPEND_ONLY_TRIGGERS_REQUIRED',1;
IF EXISTS(SELECT 1 FROM @objects e JOIN sys.sql_modules m ON m.object_id=OBJECT_ID(N'ctl.'+e.name)
 WHERE e.kind='P' AND m.execute_as_principal_id IS NOT NULL)
 THROW 52440,N'CALLER_CONTEXT_MUST_BE_PRESERVED',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions p JOIN @objects e ON p.major_id=OBJECT_ID(N'ctl.'+e.name)
 WHERE p.class=1 AND p.state IN('G','W') AND p.grantee_principal_id<>DATABASE_PRINCIPAL_ID(N'dbo'))
 THROW 52440,N'OPERATIONAL_GRANT_NOT_AUTHORIZED_BY_SCHEMA_INSTALL',1;
IF EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping) OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope)
 OR EXISTS(SELECT 1 FROM ctl.runtime_authority_configuration)
 THROW 52440,N'IDENTITY_AUTHORITY_SEED_NOT_AUTHORIZED',1;
PRINT N'AUTHORITY_TEMPORAL_STRUCTURE_PASS_NO_OPERATIONAL_PROVISIONING';

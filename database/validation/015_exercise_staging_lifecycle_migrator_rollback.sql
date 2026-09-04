-- Prova sintética, limitada e rollback-only de que a role migrator aplica V005 e publica grants.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51630, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\migrations\V001__create_v2_schema_foundation.sql"
:r "..\migrations\V002__create_v2_database_roles.sql"
:r "..\migrations\V003__create_control_plane.sql"
:r "..\migrations\V004__create_staging_promotion_kernel.sql"

CREATE USER v2_migrator_lifecycle_probe WITHOUT LOGIN;
ALTER ROLE v2_migrator ADD MEMBER v2_migrator_lifecycle_probe;
EXECUTE AS USER = N'v2_migrator_lifecycle_probe';
GO

:r "..\migrations\V005__create_staging_lifecycle.sql"

IF HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'EXECUTE') <> 1
    THROW 51632, N'Migrator não consegue executar o publicador restrito.', 1;
IF HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'ALTER') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'CONTROL') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'TAKE OWNERSHIP') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo', N'SCHEMA', N'ALTER') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE') <> 0
    OR HAS_PERMS_BY_NAME(N'v2_schema_owner', N'USER', N'IMPERSONATE') <> 0
    OR HAS_PERMS_BY_NAME(N'ctl', N'SCHEMA', N'TAKE OWNERSHIP') <> 0
    THROW 51633, N'Migrator possui autoridade indevida sobre o publicador ou dbo.', 1;

REVERT;
ALTER ROLE v2_migrator DROP MEMBER v2_migrator_lifecycle_probe;
GO

:r "001_validate_schema_foundation.sql"
GO
:r "012_validate_staging_lifecycle.sql"
GO

EXECUTE AS USER = N'v2_schema_owner';
IF USER_NAME() <> N'v2_schema_owner'
    OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'CONTROL'), -1) <> 0
    OR COALESCE(HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE'), -1) <> 0
    OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'ALTER ANY ROLE'), -1) <> 0
    OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'ALTER ANY USER'), -1) <> 0
    OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'IMPERSONATE ANY USER'), 0) <> 0
    OR COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), N'DATABASE', N'TAKE OWNERSHIP'), -1) <> 0
BEGIN
    REVERT;
    THROW 51635, N'Owner restrito possui autoridade de database indevida.', 1;
END;
REVERT;
GO

ALTER ROLE v2_migrator ADD MEMBER v2_migrator_lifecycle_probe;
EXECUTE AS USER = N'v2_migrator_lifecycle_probe';
GO

DECLARE @forbidden_grant_error INT = NULL;
DECLARE @forbidden_grant_count_before BIGINT = NULL;
DECLARE @forbidden_grant_count_after BIGINT = NULL;
SET XACT_ABORT OFF;

REVERT;
SELECT @forbidden_grant_count_before = COUNT_BIG(*)
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name = N'v2_lifecycle_operator'
  AND permission_definition.class = 1
  AND permission_definition.major_id =
      OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P')
  AND permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN (N'G', N'W');
EXECUTE AS USER = N'v2_migrator_lifecycle_probe';

BEGIN TRY
    EXEC dbo.usp_publish_v2_procedure_grant
        N'ctl', N'usp_control_plane_register_source', N'v2_lifecycle_operator';
END TRY
BEGIN CATCH
    SET @forbidden_grant_error = ERROR_NUMBER();
END CATCH;

REVERT;
SELECT @forbidden_grant_count_after = COUNT_BIG(*)
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name = N'v2_lifecycle_operator'
  AND permission_definition.class = 1
  AND permission_definition.major_id =
      OBJECT_ID(N'ctl.usp_control_plane_register_source', N'P')
  AND permission_definition.permission_name = N'EXECUTE'
  AND permission_definition.state IN (N'G', N'W');

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF @forbidden_grant_error IS NULL
    OR @forbidden_grant_error <> 51222
    OR @forbidden_grant_count_before IS NULL
    OR @forbidden_grant_count_before <> 0
    OR @forbidden_grant_count_after IS NULL
    OR @forbidden_grant_count_after <> 0
BEGIN
    THROW 51634, N'Owner-context mutável alcançou dbo ou a fronteira dbo pôde ser alterada.', 1;
END;
SET XACT_ABORT ON;
PRINT N'V005 aplicada pela role migrator; dbo negado, owner-context mutável confinado e estado revertido.';

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51631, N'O rollback da prova de migrator não encerrou o escopo sintético.', 1;

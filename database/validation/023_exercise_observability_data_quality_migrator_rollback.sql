-- Prova rollback-only de que a role migrator aplica V006 sem autoridade dbo adicional.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51640, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\migrations\V001__create_v2_schema_foundation.sql"
:r "..\migrations\V002__create_v2_database_roles.sql"
:r "..\migrations\V003__create_control_plane.sql"
:r "..\migrations\V004__create_staging_promotion_kernel.sql"
:r "..\migrations\V005__create_staging_lifecycle.sql"

CREATE USER v2_migrator_observability_probe WITHOUT LOGIN;
ALTER ROLE v2_migrator ADD MEMBER v2_migrator_observability_probe;
EXECUTE AS USER = N'v2_migrator_observability_probe';
GO

:r "..\migrations\V006__create_observability_data_quality.sql"

IF HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'EXECUTE') <> 1
    THROW 51642, N'Migrator não consegue executar o publicador restrito.', 1;
IF HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'ALTER') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'CONTROL') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo.usp_publish_v2_procedure_grant', N'OBJECT', N'TAKE OWNERSHIP') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo', N'SCHEMA', N'ALTER') <> 0
    OR HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE') <> 0
    OR HAS_PERMS_BY_NAME(N'v2_schema_owner', N'USER', N'IMPERSONATE') <> 0
    THROW 51643, N'Migrator possui autoridade indevida sobre o publicador ou dbo.', 1;

REVERT;
ALTER ROLE v2_migrator DROP MEMBER v2_migrator_observability_probe;
GO

:r "021_validate_observability_data_quality.sql"
GO

IF EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name = N'v2_runtime'
      AND permission_definition.class = 1
      AND permission_definition.major_id IN (
          OBJECT_ID(N'recon.usp_evaluate_execution_data_quality', N'P'),
          OBJECT_ID(N'recon.usp_observe_execution_data_quality', N'P'),
          OBJECT_ID(N'recon.usp_record_execution_metric', N'P'),
          OBJECT_ID(N'recon.usp_raise_observability_alert', N'P'),
          OBJECT_ID(N'ctl.usp_observe_platform_health', N'P')
      )
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state IN (N'D', N'R')
)
    THROW 51644, N'Entrypoint V2-023 não foi publicado ao runtime mínimo.', 1;

IF (
    SELECT COUNT_BIG(*)
    FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.class = 1
      AND permission_definition.major_id IN (
          OBJECT_ID(N'recon.usp_evaluate_execution_data_quality', N'P'),
          OBJECT_ID(N'recon.usp_observe_execution_data_quality', N'P'),
          OBJECT_ID(N'recon.usp_record_execution_metric', N'P'),
          OBJECT_ID(N'recon.usp_raise_observability_alert', N'P'),
          OBJECT_ID(N'ctl.usp_observe_platform_health', N'P')
      )
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.state = N'G'
) <> 5
    THROW 51645, N'Os cinco grants mínimos de V2-023 não foram publicados.', 1;

ROLLBACK TRANSACTION;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51641, N'O rollback da prova de migrator não encerrou o escopo sintético.', 1;

PRINT N'V006 aplicada pela role migrator, grants mínimos publicados e estado revertido.';

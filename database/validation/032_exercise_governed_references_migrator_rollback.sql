-- Prova rollback-only de que v2_migrator aplica V008 sem ganhar leitura/DML em ref.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51990, N'A prova do migrator aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\migrations\V001__create_v2_schema_foundation.sql"
:r "..\migrations\V002__create_v2_database_roles.sql"
:r "..\migrations\V003__create_control_plane.sql"
:r "..\migrations\V004__create_staging_promotion_kernel.sql"
:r "..\migrations\V005__create_staging_lifecycle.sql"
:r "..\migrations\V006__create_observability_data_quality.sql"
:r "..\migrations\V007__create_usuarios_current_history.sql"

CREATE USER v2_migrator_references_probe WITHOUT LOGIN;
ALTER ROLE v2_migrator ADD MEMBER v2_migrator_references_probe;
EXECUTE AS USER = N'v2_migrator_references_probe';
GO

:r "..\migrations\V008__create_governed_references.sql"

IF HAS_PERMS_BY_NAME(N'ref', N'SCHEMA', N'ALTER') <> 1
   OR HAS_PERMS_BY_NAME(N'ref', N'SCHEMA', N'REFERENCES') <> 1
    THROW 51991, N'Migrator não possui a autoridade estrutural mínima de ref.', 1;
IF HAS_PERMS_BY_NAME(N'ref.reference_release', N'OBJECT', N'SELECT') <> 0
   OR HAS_PERMS_BY_NAME(N'ref.reference_release', N'OBJECT', N'INSERT') <> 0
   OR HAS_PERMS_BY_NAME(N'ref.reference_release', N'OBJECT', N'UPDATE') <> 0
   OR HAS_PERMS_BY_NAME(N'ref.reference_release', N'OBJECT', N'DELETE') <> 0
   OR HAS_PERMS_BY_NAME(N'dbo', N'SCHEMA', N'ALTER') <> 0
   OR HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE') <> 0
   OR HAS_PERMS_BY_NAME(N'v2_schema_owner', N'USER', N'IMPERSONATE') <> 0
    THROW 51992, N'Migrator ganhou DML/leitura ou autoridade indevida.', 1;

REVERT;
ALTER ROLE v2_migrator DROP MEMBER v2_migrator_references_probe;
GO

:r "030_validate_governed_references.sql"
GO

IF EXISTS (SELECT 1 FROM ref.reference_release)
    THROW 51993, N'Aplicar V008 como migrator criou conteúdo por default.', 1;

ROLLBACK TRANSACTION;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51994, N'O rollback da prova do migrator não encerrou o escopo.', 1;

PRINT N'V008 aplicada por v2_migrator sem leitura/DML em ref e revertida integralmente.';

-- Compila os access paths da dimensão current de Usuários sem persistir V009.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 52040, N'O SHOWPLAN dimensional aceita somente o banco local V2 de sombra.', 1;

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
:r "..\migrations\V008__create_governed_references.sql"
:r "..\migrations\V009__create_usuario_dimension_current_view.sql"
:r "035_validate_usuarios_dimension_current.sql"
GO

SET SHOWPLAN_XML ON;
GO

DECLARE @usuario_id BIGINT = 1;
SELECT dimension.usuario_id, dimension.environment_name, dimension.source_instance,
       dimension.tenant_scope, dimension.source_key_token,
       dimension.source_key_wire_type, dimension.name_presence, dimension.usuario_name,
       dimension.last_changed_at_utc, dimension.last_seen_at_utc
FROM core.v_usuario_dimension_current_v1 AS dimension
WHERE dimension.usuario_id = @usuario_id;
GO

DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_DIMENSION_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_DIMENSION_TENANT';
DECLARE @source_key_token NVARCHAR(256) = N'INTEGER:4101';
SELECT dimension.usuario_id, dimension.environment_name, dimension.source_instance,
       dimension.tenant_scope, dimension.source_key_token,
       dimension.source_key_wire_type, dimension.name_presence, dimension.usuario_name,
       dimension.last_changed_at_utc, dimension.last_seen_at_utc
FROM core.v_usuario_dimension_current_v1 AS dimension
WHERE dimension.environment_name = @environment_name
  AND dimension.source_instance = @source_instance
  AND dimension.tenant_scope = @tenant_scope
  AND dimension.[source_key_token] = @source_key_token;
GO

SELECT dimension.usuario_id, dimension.environment_name, dimension.source_instance,
       dimension.tenant_scope, dimension.source_key_token,
       dimension.source_key_wire_type, dimension.name_presence, dimension.usuario_name,
       dimension.last_changed_at_utc, dimension.last_seen_at_utc
FROM core.v_usuario_dimension_current_v1 AS dimension;
GO

SET SHOWPLAN_XML OFF;
GO

ROLLBACK TRANSACTION;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 52041, N'O rollback do SHOWPLAN dimensional não encerrou o escopo.', 1;
PRINT N'USUARIOS_DIMENSION_CURRENT_SHOWPLAN_ROLLED_BACK';

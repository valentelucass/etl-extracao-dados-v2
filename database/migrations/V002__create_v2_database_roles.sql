-- Papéis do banco V2. A associação de identities aos papéis é responsabilidade do DBA/owner
-- no boundary de deploy; esta migration não cria logins, usuários ou credenciais.

SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant') IS NOT NULL
    OR OBJECT_ID(N'dbo.v2_procedure_grant_allowlist') IS NOT NULL
    THROW 51226, N'Nome reservado da fronteira de grants já existe antes da fundação.', 1;

IF EXISTS (
    SELECT 1 FROM sys.database_principals
    WHERE name IN (
        N'v2_migrator', N'v2_runtime', N'v2_retention_governor',
        N'v2_lifecycle_reviewer', N'v2_lifecycle_operator', N'v2_archive_restorer',
        N'v2_schema_owner'
    )
)
    THROW 51225, N'Principal reservado V2 já existe antes da fundação.', 1;

CREATE ROLE v2_migrator AUTHORIZATION dbo;
CREATE ROLE v2_runtime AUTHORIZATION dbo;
CREATE ROLE v2_retention_governor AUTHORIZATION dbo;
CREATE ROLE v2_lifecycle_reviewer AUTHORIZATION dbo;
CREATE ROLE v2_lifecycle_operator AUTHORIZATION dbo;
CREATE ROLE v2_archive_restorer AUTHORIZATION dbo;

-- Todos os schemas mutáveis compartilham um owner sem login e sem autoridade de database. Assim,
-- ownership chaining continua funcional entre schemas V2, mas um módulo criado pelo migrator com
-- EXECUTE AS OWNER nunca adquire o contexto dbo.
CREATE USER v2_schema_owner WITHOUT LOGIN;

ALTER AUTHORIZATION ON SCHEMA::ctl TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::stg TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::core TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::ref TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::mart TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::pub TO v2_schema_owner;
ALTER AUTHORIZATION ON SCHEMA::recon TO v2_schema_owner;

DENY IMPERSONATE ON USER::dbo TO v2_schema_owner;
DENY IMPERSONATE ON USER::dbo TO public;
DENY IMPERSONATE ON USER::v2_schema_owner TO public;

-- Flyway mantém apenas seu histórico em ctl nesta fundação. O migrator recebe DDL explícito
-- e alteração somente nos schemas V2; ele não recebe db_owner nem permissão cross-database.
GRANT CREATE FUNCTION TO v2_migrator;
GRANT CREATE PROCEDURE TO v2_migrator;
GRANT CREATE TABLE TO v2_migrator;
GRANT CREATE TYPE TO v2_migrator;
GRANT CREATE VIEW TO v2_migrator;

GRANT ALTER, REFERENCES ON SCHEMA::ctl TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::stg TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::core TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::ref TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::mart TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::pub TO v2_migrator;
GRANT ALTER, REFERENCES ON SCHEMA::recon TO v2_migrator;
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ctl TO v2_migrator;
DENY ALTER ON SCHEMA::dbo TO v2_migrator;
DENY IMPERSONATE ON USER::dbo TO v2_migrator;
DENY IMPERSONATE ON USER::v2_schema_owner TO v2_migrator;
GO

CREATE TABLE dbo.v2_procedure_grant_allowlist (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    procedure_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    role_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_dbo_v2_procedure_grant_allowlist
        PRIMARY KEY CLUSTERED (schema_name, procedure_name, role_name)
);

INSERT INTO dbo.v2_procedure_grant_allowlist (schema_name, procedure_name, role_name)
VALUES
    (N'ctl', N'usp_control_plane_register_source', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_start_cycle', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_start_execution', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_heartbeat_lease', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_record_page', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_record_counts', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_transition_execution', N'v2_runtime'),
    (N'ctl', N'usp_control_plane_recover_stale_executions', N'v2_runtime'),
    (N'stg', N'usp_stage_record', N'v2_runtime'),
    (N'core', N'usp_prepare_staged_execution', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_execution', N'v2_runtime'),
    (N'stg', N'usp_stage_usuario_record', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_usuarios', N'v2_runtime'),
    (N'ctl', N'usp_approve_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_revoke_staging_retention_policy', N'v2_retention_governor'),
    (N'ctl', N'usp_place_staging_legal_hold', N'v2_retention_governor'),
    (N'ctl', N'usp_release_staging_legal_hold', N'v2_retention_governor'),
    (N'stg', N'usp_plan_staging_lifecycle', N'v2_lifecycle_reviewer'),
    (N'stg', N'usp_archive_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'stg', N'usp_purge_staging_lifecycle', N'v2_lifecycle_operator'),
    (N'recon', N'usp_restore_staging_archive', N'v2_archive_restorer'),
    (N'recon', N'usp_evaluate_execution_data_quality', N'v2_runtime'),
    (N'recon', N'usp_observe_execution_data_quality', N'v2_runtime'),
    (N'recon', N'usp_record_execution_metric', N'v2_runtime'),
    (N'recon', N'usp_raise_observability_alert', N'v2_runtime'),
    (N'ctl', N'usp_observe_platform_health', N'v2_runtime');

DENY SELECT, INSERT, UPDATE, DELETE, ALTER, TAKE OWNERSHIP
    ON OBJECT::dbo.v2_procedure_grant_allowlist TO public;
GO

-- SQL Server não permite repassar um GRANT OPTION herdado via role. Este publicador mínimo fica
-- em dbo, fora dos schemas alteráveis pelo migrator, roda como owner e aceita somente os triplets
-- exatos de procedure/role existentes em V003-V007. Novos entrypoints exigem revisão owner desta
-- allowlist; o migrator não recebe CONTROL, TAKE OWNERSHIP nem ALTER sobre o publicador.
GO

CREATE PROCEDURE dbo.usp_publish_v2_procedure_grant
    @schema_name SYSNAME,
    @procedure_name SYSNAME,
    @role_name SYSNAME
WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT OFF;

    IF @schema_name IS NULL OR @procedure_name IS NULL OR @role_name IS NULL
        THROW 51221, N'Publicação de entrypoint exige schema, procedure e role.', 1;

    IF NOT EXISTS (
        SELECT 1
        FROM dbo.v2_procedure_grant_allowlist AS allowed_grant
        WHERE allowed_grant.schema_name = @schema_name COLLATE Latin1_General_100_BIN2
          AND allowed_grant.procedure_name = @procedure_name COLLATE Latin1_General_100_BIN2
          AND allowed_grant.role_name = @role_name COLLATE Latin1_General_100_BIN2
    )
        THROW 51222, N'Publicação de entrypoint fora da allowlist V2.', 1;

    DECLARE @qualified_name NVARCHAR(517) =
        QUOTENAME(@schema_name) + N'.' + QUOTENAME(@procedure_name);
    IF OBJECT_ID(@qualified_name, N'P') IS NULL
        THROW 51223, N'Procedure V2 permitida não encontrada para publicação.', 1;
    IF EXISTS (
        SELECT 1
        FROM sys.sql_modules AS module_definition
        WHERE module_definition.object_id = OBJECT_ID(@qualified_name, N'P')
          AND module_definition.execute_as_principal_id IS NOT NULL
    )
        THROW 51224, N'Entrypoint com EXECUTE AS não pode ser publicado.', 1;

    DECLARE @grant_sql NVARCHAR(1000) = N'GRANT EXECUTE ON OBJECT::'
        + @qualified_name + N' TO ' + QUOTENAME(@role_name) + N';';
    EXEC sys.sp_executesql @grant_sql;
END;
GO

IF COALESCE(
       (SELECT principal_id FROM sys.objects
        WHERE object_id = OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')),
       (SELECT principal_id FROM sys.schemas WHERE name = N'dbo')
   ) <> DATABASE_PRINCIPAL_ID(N'dbo')
    THROW 51227, N'Publicador não pertence efetivamente a dbo.', 1;

DENY ALTER, TAKE OWNERSHIP
    ON OBJECT::dbo.usp_publish_v2_procedure_grant TO public;
GRANT EXECUTE ON OBJECT::dbo.usp_publish_v2_procedure_grant TO v2_migrator;
DENY ALTER, TAKE OWNERSHIP
    ON OBJECT::dbo.usp_publish_v2_procedure_grant TO v2_migrator;

-- O runtime começa sem acesso a objetos porque nenhuma responsabilidade de dados existe ainda.
-- As migrations donas futuras concederão somente DML/EXECUTE nos objetos necessários.
GRANT CONNECT TO v2_runtime;
DENY ALTER ANY ROLE TO v2_runtime;
DENY ALTER ANY SCHEMA TO v2_runtime;
DENY CREATE FUNCTION TO v2_runtime;
DENY CREATE PROCEDURE TO v2_runtime;
DENY CREATE RULE TO v2_runtime;
DENY CREATE SCHEMA TO v2_runtime;
DENY CREATE SYNONYM TO v2_runtime;
DENY CREATE TABLE TO v2_runtime;
DENY CREATE TYPE TO v2_runtime;
DENY CREATE VIEW TO v2_runtime;
DENY CREATE XML SCHEMA COLLECTION TO v2_runtime;

DENY ALTER ON SCHEMA::ctl TO v2_runtime;
DENY ALTER ON SCHEMA::stg TO v2_runtime;
DENY ALTER ON SCHEMA::core TO v2_runtime;
DENY ALTER ON SCHEMA::ref TO v2_runtime;
DENY ALTER ON SCHEMA::mart TO v2_runtime;
DENY ALTER ON SCHEMA::pub TO v2_runtime;
DENY ALTER ON SCHEMA::recon TO v2_runtime;

-- Papéis do lifecycle nascem sem membership e sem acesso direto a dados. V005 concede somente os
-- entrypoints correspondentes; ownership chaining mantém as tabelas fora do alcance do caller.
GRANT CONNECT TO v2_retention_governor;
GRANT CONNECT TO v2_lifecycle_reviewer;
GRANT CONNECT TO v2_lifecycle_operator;
GRANT CONNECT TO v2_archive_restorer;

DENY ALTER ANY ROLE TO v2_retention_governor;
DENY ALTER ANY ROLE TO v2_lifecycle_reviewer;
DENY ALTER ANY ROLE TO v2_lifecycle_operator;
DENY ALTER ANY ROLE TO v2_archive_restorer;
DENY ALTER ANY SCHEMA TO v2_retention_governor;
DENY ALTER ANY SCHEMA TO v2_lifecycle_reviewer;
DENY ALTER ANY SCHEMA TO v2_lifecycle_operator;
DENY ALTER ANY SCHEMA TO v2_archive_restorer;
DENY CREATE PROCEDURE TO v2_retention_governor;
DENY CREATE PROCEDURE TO v2_lifecycle_reviewer;
DENY CREATE PROCEDURE TO v2_lifecycle_operator;
DENY CREATE PROCEDURE TO v2_archive_restorer;
DENY CREATE TABLE TO v2_retention_governor;
DENY CREATE TABLE TO v2_lifecycle_reviewer;
DENY CREATE TABLE TO v2_lifecycle_operator;
DENY CREATE TABLE TO v2_archive_restorer;
DENY CREATE VIEW TO v2_retention_governor;
DENY CREATE VIEW TO v2_lifecycle_reviewer;
DENY CREATE VIEW TO v2_lifecycle_operator;
DENY CREATE VIEW TO v2_archive_restorer;

DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ctl TO v2_retention_governor;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::stg TO v2_retention_governor;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::core TO v2_retention_governor;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::recon TO v2_retention_governor;

DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ctl TO v2_lifecycle_reviewer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::stg TO v2_lifecycle_reviewer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::core TO v2_lifecycle_reviewer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::recon TO v2_lifecycle_reviewer;

DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ctl TO v2_lifecycle_operator;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::stg TO v2_lifecycle_operator;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::core TO v2_lifecycle_operator;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::recon TO v2_lifecycle_operator;

DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::ctl TO v2_archive_restorer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::stg TO v2_archive_restorer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::core TO v2_archive_restorer;
DENY ALTER, SELECT, INSERT, UPDATE, DELETE ON SCHEMA::recon TO v2_archive_restorer;

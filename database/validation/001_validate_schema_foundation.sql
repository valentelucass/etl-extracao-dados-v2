-- Validador somente leitura da fundação V2.
-- Fonte de expectativa: database/manifest/schema-foundation.json (fingerprint conferido pelo
-- verificador local). O histórico interno do Flyway não participa da comparação estrutural.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @expected_schemas TABLE (
    schema_name SYSNAME NOT NULL PRIMARY KEY
);

INSERT INTO @expected_schemas (schema_name)
VALUES
    (N'ctl'),
    (N'stg'),
    (N'core'),
    (N'ref'),
    (N'mart'),
    (N'pub'),
    (N'recon');

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA', expected.schema_name, N'Schema da fundação ausente.'
FROM @expected_schemas AS expected
WHERE SCHEMA_ID(expected.schema_name) IS NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA', N'shadow', N'Schema histórico proibido na fundação V2.'
WHERE SCHEMA_ID(N'shadow') IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRINCIPAL', N'v2_schema_owner',
       N'Owner interno sem login dos schemas V2 está ausente ou autenticável.'
WHERE NOT EXISTS (
    SELECT 1 FROM sys.database_principals
    WHERE name = N'v2_schema_owner'
      AND type = N'S'
      AND authentication_type_desc = N'NONE'
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA_OWNER', expected.schema_name,
       N'Schema mutável não pertence exclusivamente ao owner V2 sem login.'
FROM @expected_schemas AS expected
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.name = expected.schema_name
WHERE schema_definition.principal_id <> DATABASE_PRINCIPAL_ID(N'v2_schema_owner');

INSERT INTO @failures (category, object_name, detail)
SELECT N'SCHEMA_OWNER', schema_definition.name,
       N'Owner V2 sem login não pode possuir schema fora da allowlist exata.'
FROM sys.schemas AS schema_definition
WHERE schema_definition.principal_id = DATABASE_PRINCIPAL_ID(N'v2_schema_owner')
  AND NOT EXISTS (
      SELECT 1 FROM @expected_schemas AS expected
      WHERE expected.schema_name = schema_definition.name
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT_OWNER', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Objeto em schema V2 não pode sobrescrever o owner restrito do schema.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN @expected_schemas AS expected
    ON expected.schema_name = schema_definition.name
WHERE object_definition.is_ms_shipped = 0
  AND object_definition.principal_id IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRINCIPAL_OWNER', principal_definition.name,
       N'Owner V2 sem login não pode possuir outro principal/role.'
FROM sys.database_principals AS principal_definition
WHERE principal_definition.owning_principal_id =
      DATABASE_PRINCIPAL_ID(N'v2_schema_owner');

DECLARE @expected_roles TABLE (
    role_name SYSNAME NOT NULL PRIMARY KEY
);

INSERT INTO @expected_roles (role_name)
VALUES
    (N'v2_migrator'),
    (N'v2_runtime'),
    (N'v2_retention_governor'),
    (N'v2_lifecycle_reviewer'),
    (N'v2_lifecycle_operator'),
    (N'v2_archive_restorer');

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE', expected.role_name, N'Role da fundação ausente.'
FROM @expected_roles AS expected
WHERE NOT EXISTS (
    SELECT 1 FROM sys.database_principals AS role_definition
    WHERE role_definition.name = expected.role_name
      AND role_definition.type = N'R'
      AND role_definition.owning_principal_id = DATABASE_PRINCIPAL_ID(N'dbo')
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_OWNERSHIP', schema_definition.name,
       N'Role V2 não pode possuir schema e adquirir CONTROL implícito.'
FROM sys.schemas AS schema_definition
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = schema_definition.principal_id
INNER JOIN @expected_roles AS expected
    ON expected.role_name = owner_definition.name;

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_OWNERSHIP', principal_definition.name,
       N'Role V2 não pode possuir outro principal.'
FROM sys.database_principals AS principal_definition
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = principal_definition.owning_principal_id
INNER JOIN @expected_roles AS expected
    ON expected.role_name = owner_definition.name;

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_OWNERSHIP',
       CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Role V2 não pode possuir objeto e adquirir CONTROL implícito.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN sys.database_principals AS owner_definition
    ON owner_definition.principal_id = object_definition.principal_id
INNER JOIN @expected_roles AS expected
    ON expected.role_name = owner_definition.name
WHERE object_definition.is_ms_shipped = 0;

-- Esta asserção protege V001/V002 sem rejeitar objetos pertencentes a migrations posteriores.
-- O validator completo de V2-020 passa a ser a autoridade quando o control plane estiver presente.
DECLARE @control_plane_installed BIT =
    CASE WHEN OBJECT_ID(N'ctl.execution_partition', N'U') IS NULL THEN 0 ELSE 1 END;

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Objeto antecipado antes da migration dona.'
FROM sys.objects AS object_definition
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN @expected_schemas AS expected
    ON expected.schema_name = schema_definition.name
WHERE object_definition.is_ms_shipped = 0
  AND object_definition.type IN (N'U', N'P', N'V', N'FN', N'IF', N'TF', N'TR')
  AND @control_plane_installed = 0
  AND NOT (
      schema_definition.name = N'ctl'
      AND object_definition.name = N'flyway_schema_history'
      AND object_definition.type = N'U'
  );

DECLARE @expected_permissions TABLE (
    role_name SYSNAME NOT NULL,
    permission_state CHAR(1) NOT NULL,
    permission_class TINYINT NOT NULL,
    schema_name SYSNAME NOT NULL,
    permission_name NVARCHAR(128) NOT NULL,
    PRIMARY KEY (role_name, permission_state, permission_class, schema_name, permission_name)
);

INSERT INTO @expected_permissions (
    role_name,
    permission_state,
    permission_class,
    schema_name,
    permission_name
)
VALUES
    (N'v2_migrator', 'G', 0, N'', N'CREATE FUNCTION'),
    (N'v2_migrator', 'G', 0, N'', N'CREATE PROCEDURE'),
    (N'v2_migrator', 'G', 0, N'', N'CREATE TABLE'),
    (N'v2_migrator', 'G', 0, N'', N'CREATE TYPE'),
    (N'v2_migrator', 'G', 0, N'', N'CREATE VIEW'),
    (N'v2_migrator', 'G', 3, N'ctl', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'ctl', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'ctl', N'SELECT'),
    (N'v2_migrator', 'G', 3, N'ctl', N'INSERT'),
    (N'v2_migrator', 'G', 3, N'ctl', N'UPDATE'),
    (N'v2_migrator', 'G', 3, N'ctl', N'DELETE'),
    (N'v2_migrator', 'G', 3, N'stg', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'stg', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'core', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'core', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'ref', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'ref', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'mart', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'mart', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'pub', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'pub', N'REFERENCES'),
    (N'v2_migrator', 'G', 3, N'recon', N'ALTER'),
    (N'v2_migrator', 'G', 3, N'recon', N'REFERENCES'),
    (N'v2_migrator', 'D', 3, N'dbo', N'ALTER'),
    (N'v2_migrator', 'D', 4, N'dbo', N'IMPERSONATE'),
    (N'v2_migrator', 'D', 4, N'v2_schema_owner', N'IMPERSONATE'),
    (N'v2_migrator', 'G', 1, N'dbo.usp_publish_v2_procedure_grant', N'EXECUTE'),
    (N'v2_migrator', 'D', 1, N'dbo.usp_publish_v2_procedure_grant', N'ALTER'),
    (N'v2_migrator', 'D', 1, N'dbo.usp_publish_v2_procedure_grant', N'TAKE OWNERSHIP'),
    (N'v2_schema_owner', 'D', 4, N'dbo', N'IMPERSONATE'),
    (N'v2_schema_owner', 'G', 0, N'', N'CONNECT'),
    (N'public', 'D', 4, N'dbo', N'IMPERSONATE'),
    (N'public', 'D', 4, N'v2_schema_owner', N'IMPERSONATE'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'SELECT'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'INSERT'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'UPDATE'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'DELETE'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'ALTER'),
    (N'public', 'D', 1, N'dbo.v2_procedure_grant_allowlist', N'TAKE OWNERSHIP'),
    (N'public', 'D', 1, N'dbo.usp_publish_v2_procedure_grant', N'ALTER'),
    (N'public', 'D', 1, N'dbo.usp_publish_v2_procedure_grant', N'TAKE OWNERSHIP'),
    (N'v2_runtime', 'G', 0, N'', N'CONNECT'),
    (N'v2_runtime', 'D', 0, N'', N'ALTER ANY ROLE'),
    (N'v2_runtime', 'D', 0, N'', N'ALTER ANY SCHEMA'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE FUNCTION'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE PROCEDURE'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE RULE'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE SCHEMA'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE SYNONYM'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE TABLE'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE TYPE'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE VIEW'),
    (N'v2_runtime', 'D', 0, N'', N'CREATE XML SCHEMA COLLECTION'),
    (N'v2_runtime', 'D', 3, N'ctl', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'ctl', N'SELECT'),
    (N'v2_runtime', 'D', 3, N'ctl', N'INSERT'),
    (N'v2_runtime', 'D', 3, N'ctl', N'UPDATE'),
    (N'v2_runtime', 'D', 3, N'ctl', N'DELETE'),
    (N'v2_runtime', 'D', 3, N'stg', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'stg', N'SELECT'),
    (N'v2_runtime', 'D', 3, N'stg', N'INSERT'),
    (N'v2_runtime', 'D', 3, N'stg', N'UPDATE'),
    (N'v2_runtime', 'D', 3, N'stg', N'DELETE'),
    (N'v2_runtime', 'D', 3, N'core', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'core', N'SELECT'),
    (N'v2_runtime', 'D', 3, N'core', N'INSERT'),
    (N'v2_runtime', 'D', 3, N'core', N'UPDATE'),
    (N'v2_runtime', 'D', 3, N'core', N'DELETE'),
    (N'v2_runtime', 'D', 3, N'ref', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'mart', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'pub', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'recon', N'ALTER'),
    (N'v2_runtime', 'D', 3, N'recon', N'SELECT'),
    (N'v2_runtime', 'D', 3, N'recon', N'INSERT'),
    (N'v2_runtime', 'D', 3, N'recon', N'UPDATE'),
    (N'v2_runtime', 'D', 3, N'recon', N'DELETE');

DECLARE @lifecycle_roles TABLE (role_name SYSNAME NOT NULL PRIMARY KEY);
INSERT INTO @lifecycle_roles (role_name)
VALUES
    (N'v2_retention_governor'),
    (N'v2_lifecycle_reviewer'),
    (N'v2_lifecycle_operator'),
    (N'v2_archive_restorer');

INSERT INTO @expected_permissions (
    role_name, permission_state, permission_class, schema_name, permission_name
)
SELECT role_name, 'G', 0, N'', N'CONNECT'
FROM @lifecycle_roles;

INSERT INTO @expected_permissions (
    role_name, permission_state, permission_class, schema_name, permission_name
)
SELECT lifecycle_role.role_name, 'D', 0, N'', database_deny.permission_name
FROM @lifecycle_roles AS lifecycle_role
CROSS JOIN (VALUES
    (N'ALTER ANY ROLE'),
    (N'ALTER ANY SCHEMA'),
    (N'CREATE PROCEDURE'),
    (N'CREATE TABLE'),
    (N'CREATE VIEW')
) AS database_deny (permission_name);

INSERT INTO @expected_permissions (
    role_name, permission_state, permission_class, schema_name, permission_name
)
SELECT lifecycle_role.role_name, 'D', 3, schema_definition.schema_name,
       schema_deny.permission_name
FROM @lifecycle_roles AS lifecycle_role
CROSS JOIN (VALUES (N'ctl'), (N'stg'), (N'core'), (N'recon')) AS schema_definition (schema_name)
CROSS JOIN (VALUES
    (N'ALTER'), (N'SELECT'), (N'INSERT'), (N'UPDATE'), (N'DELETE')
) AS schema_deny (permission_name);

DECLARE @expected_grant_allowlist TABLE (
    schema_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    procedure_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    role_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (schema_name, procedure_name, role_name)
);
INSERT INTO @expected_grant_allowlist (schema_name, procedure_name, role_name)
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
    (N'stg', N'usp_stage_coleta_record', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_coletas', N'v2_runtime'),
    (N'stg', N'usp_stage_cotacao_record', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_cotacoes', N'v2_runtime'),
    (N'stg', N'usp_stage_manifesto_observation', N'v2_runtime'),
    (N'stg', N'usp_stage_manifesto_reduced_candidate', N'v2_runtime'),
    (N'core', N'usp_prepare_manifesto_candidate_set', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_manifestos', N'v2_runtime'),
    (N'stg', N'usp_stage_frete_record', N'v2_runtime'),
    (N'stg', N'usp_stage_frete_performance', N'v2_runtime'),
    (N'stg', N'usp_stage_frete_sidecar', N'v2_runtime'),
    (N'core', N'usp_prepare_frete_candidate_set', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_fretes', N'v2_runtime'),
    (N'stg', N'usp_stage_localizacao_carga_record', N'v2_runtime'),
    (N'core', N'usp_apply_reconcile_publish_localizacao_cargas', N'v2_runtime'),
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

INSERT INTO @failures (category, object_name, detail)
SELECT N'ALLOWLIST', N'dbo.v2_procedure_grant_allowlist',
       N'Allowlist imutável de grants ausente, com owner divergente ou conjunto inexato.'
WHERE OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U') IS NULL
   OR COALESCE(
          (SELECT principal_id FROM sys.objects
           WHERE object_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')),
          (SELECT principal_id FROM sys.schemas WHERE name = N'dbo')
      ) <> DATABASE_PRINCIPAL_ID(N'dbo')
   OR (SELECT COUNT_BIG(*) FROM dbo.v2_procedure_grant_allowlist) <> 41
   OR (SELECT COUNT_BIG(*) FROM sys.columns
       WHERE object_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')) <> 3
   OR EXISTS (
          SELECT 1
          FROM (VALUES
              (1, N'schema_name'), (2, N'procedure_name'), (3, N'role_name')
          ) AS expected_column (column_id, column_name)
          WHERE NOT EXISTS (
              SELECT 1 FROM sys.columns AS column_definition
              WHERE column_definition.object_id =
                    OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
                AND column_definition.column_id = expected_column.column_id
                AND column_definition.name = expected_column.column_name
                AND TYPE_NAME(column_definition.user_type_id) = N'sysname'
                AND column_definition.max_length = 256
                AND column_definition.is_nullable = 0
                AND column_definition.collation_name = N'Latin1_General_100_BIN2'
          )
      )
   OR NOT EXISTS (
          SELECT 1 FROM sys.indexes AS index_definition
          WHERE index_definition.object_id =
                OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
            AND index_definition.name = N'PK_dbo_v2_procedure_grant_allowlist'
            AND index_definition.is_primary_key = 1
            AND index_definition.is_unique = 1
            AND (SELECT COUNT_BIG(*) FROM sys.index_columns AS key_count
                 WHERE key_count.object_id = index_definition.object_id
                   AND key_count.index_id = index_definition.index_id
                   AND key_count.key_ordinal > 0) = 3
            AND NOT EXISTS (
                SELECT 1
                FROM (VALUES
                    (1, N'schema_name'), (2, N'procedure_name'), (3, N'role_name')
                ) AS expected_key (key_ordinal, column_name)
                WHERE NOT EXISTS (
                    SELECT 1 FROM sys.index_columns AS actual_key
                    INNER JOIN sys.columns AS actual_column
                        ON actual_column.object_id = actual_key.object_id
                       AND actual_column.column_id = actual_key.column_id
                    WHERE actual_key.object_id = index_definition.object_id
                      AND actual_key.index_id = index_definition.index_id
                      AND actual_key.key_ordinal = expected_key.key_ordinal
                      AND actual_column.name = expected_key.column_name
                )
            )
      )
   OR EXISTS (
          SELECT schema_name, procedure_name, role_name
          FROM @expected_grant_allowlist
          EXCEPT
          SELECT schema_name, procedure_name, role_name
          FROM dbo.v2_procedure_grant_allowlist
      )
   OR EXISTS (
          SELECT schema_name, procedure_name, role_name
          FROM dbo.v2_procedure_grant_allowlist
          EXCEPT
          SELECT schema_name, procedure_name, role_name
          FROM @expected_grant_allowlist
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROCEDURE', N'dbo.usp_publish_v2_procedure_grant',
       N'Publicador interno de grants do migrator ausente ou sem EXECUTE AS OWNER.'
WHERE OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P') IS NULL
   OR COALESCE(
          (SELECT principal_id FROM sys.objects
           WHERE object_id = OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')),
          (SELECT principal_id FROM sys.schemas WHERE name = N'dbo')
      ) <> DATABASE_PRINCIPAL_ID(N'dbo')
   OR NOT EXISTS (
          SELECT 1 FROM sys.sql_modules AS module_definition
          WHERE module_definition.object_id =
                    OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')
            AND module_definition.execute_as_principal_id = -2
      );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PROTOCOL', N'dbo.usp_publish_v2_procedure_grant',
       N'Publicador não possui allowlist exata ou permite publicar módulo EXECUTE AS.'
WHERE OBJECT_DEFINITION(OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')) IS NULL
   OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P'))
          NOT LIKE N'%FROM dbo.v2_procedure_grant_allowlist AS allowed_grant%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P'))
          NOT LIKE N'%execute_as_principal_id IS NOT NULL%';

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY',
       CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Módulo persistente em schema mutável não pode declarar EXECUTE AS.'
FROM sys.sql_modules AS module_definition
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = module_definition.object_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
INNER JOIN @expected_schemas AS expected
    ON expected.schema_name = schema_definition.name
WHERE module_definition.execute_as_principal_id IS NOT NULL;

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION',
       CONCAT(expected.role_name, N':', COALESCE(NULLIF(expected.schema_name, N''), N'DATABASE'),
              N':', expected.permission_name),
       N'Permissão mínima esperada ausente.'
FROM @expected_permissions AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    INNER JOIN sys.database_principals AS principal_definition
        ON principal_definition.principal_id = permission_definition.grantee_principal_id
    WHERE principal_definition.name COLLATE DATABASE_DEFAULT = expected.role_name
      AND permission_definition.state COLLATE DATABASE_DEFAULT = expected.permission_state
      AND permission_definition.class = expected.permission_class
      AND permission_definition.permission_name COLLATE DATABASE_DEFAULT = expected.permission_name
      AND (expected.permission_class <> 1 OR permission_definition.minor_id = 0)
      AND (
          (expected.permission_class = 0 AND permission_definition.major_id = 0)
          OR (
              expected.permission_class = 3
              AND permission_definition.major_id = SCHEMA_ID(expected.schema_name)
          )
          OR (
              expected.permission_class = 1
              AND permission_definition.major_id = OBJECT_ID(expected.schema_name)
          )
          OR (
              expected.permission_class = 4
              AND permission_definition.major_id = DATABASE_PRINCIPAL_ID(expected.schema_name)
          )
      )
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION',
       CONCAT(principal_definition.name, N':',
              CASE WHEN permission_definition.class = 0 THEN N'DATABASE'
                   WHEN permission_definition.class = 1 THEN
                       CONCAT(OBJECT_SCHEMA_NAME(permission_definition.major_id), N'.',
                              OBJECT_NAME(permission_definition.major_id))
                   WHEN permission_definition.class = 3 THEN
                       SCHEMA_NAME(permission_definition.major_id)
                   WHEN permission_definition.class = 4 THEN
                       USER_NAME(permission_definition.major_id)
                   ELSE N'UNKNOWN' END,
              N':', permission_definition.permission_name),
       N'Permissão direta fora do manifesto da fundação.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE (
        principal_definition.name COLLATE DATABASE_DEFAULT IN (
            N'v2_migrator', N'v2_schema_owner'
        )
        OR (
          EXISTS (
            SELECT 1 FROM @expected_roles AS expected_role
            WHERE expected_role.role_name =
                principal_definition.name COLLATE DATABASE_DEFAULT
          )
          AND permission_definition.class IN (0, 3, 4)
        )
      )
  AND NOT EXISTS (
      SELECT 1
      FROM @expected_permissions AS expected
      WHERE expected.role_name = principal_definition.name COLLATE DATABASE_DEFAULT
        AND expected.permission_state = permission_definition.state COLLATE DATABASE_DEFAULT
        AND expected.permission_class = permission_definition.class
        AND expected.permission_name = permission_definition.permission_name COLLATE DATABASE_DEFAULT
        AND (expected.permission_class <> 1 OR permission_definition.minor_id = 0)
        AND (
            (expected.permission_class = 0 AND permission_definition.major_id = 0)
            OR (
                expected.permission_class = 3
                AND permission_definition.major_id = SCHEMA_ID(expected.schema_name)
            )
            OR (
                expected.permission_class = 1
                AND permission_definition.major_id = OBJECT_ID(expected.schema_name)
            )
            OR (
                expected.permission_class = 4
                AND permission_definition.major_id = DATABASE_PRINCIPAL_ID(expected.schema_name)
            )
        )
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PUBLIC_PERMISSION', permission_definition.permission_name,
       N'Grant público permite que owner-context restrito escale no database.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name = N'public'
  AND permission_definition.state IN ('G', 'W')
  AND permission_definition.permission_name IN (
      N'CONTROL', N'ALTER ANY ROLE', N'ALTER ANY USER',
      N'IMPERSONATE ANY USER', N'TAKE OWNERSHIP', N'CREATE SCHEMA'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PUBLIC_PERMISSION', USER_NAME(permission_definition.major_id),
       N'Grant público permite impersonar dbo ou o owner restrito.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'public')
  AND permission_definition.state IN ('G', 'W')
  AND permission_definition.class = 4
  AND permission_definition.permission_name = N'IMPERSONATE'
  AND permission_definition.major_id IN (
      DATABASE_PRINCIPAL_ID(N'dbo'), DATABASE_PRINCIPAL_ID(N'v2_schema_owner')
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PUBLIC_PERMISSION', SCHEMA_NAME(permission_definition.major_id),
       N'Grant público permite controlar ou tomar ownership de schema V2.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'public')
  AND permission_definition.state IN ('G', 'W')
  AND permission_definition.class = 3
  AND permission_definition.permission_name IN (N'ALTER', N'CONTROL', N'TAKE OWNERSHIP')
  AND SCHEMA_NAME(permission_definition.major_id) IN (
      N'ctl', N'stg', N'core', N'ref', N'mart', N'pub', N'recon'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.v2_procedure_grant_allowlist',
       N'Allowlist imutável possui GRANT positivo explícito fora de dbo.'
FROM sys.database_permissions AS permission_definition
WHERE permission_definition.class = 1
  AND permission_definition.major_id = OBJECT_ID(N'dbo.v2_procedure_grant_allowlist', N'U')
  AND permission_definition.state IN ('G', 'W')
  AND permission_definition.grantee_principal_id <> DATABASE_PRINCIPAL_ID(N'dbo');

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE_BOUNDARY', N'dbo.usp_publish_v2_procedure_grant',
       N'Publicador possui GRANT positivo além do EXECUTE exato do migrator.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE permission_definition.class = 1
  AND permission_definition.major_id = OBJECT_ID(N'dbo.usp_publish_v2_procedure_grant', N'P')
  AND permission_definition.state IN ('G', 'W')
  AND principal_definition.name <> N'dbo'
  AND NOT (
      principal_definition.name = N'v2_migrator'
      AND permission_definition.permission_name = N'EXECUTE'
      AND permission_definition.minor_id = 0
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'ROLE_MEMBERSHIP', CONCAT(role_definition.name, N'->', member_definition.name),
       N'Roles/owner internos da fundação não podem possuir memberships semeadas.'
FROM sys.database_role_members AS membership
INNER JOIN sys.database_principals AS role_definition
    ON role_definition.principal_id = membership.role_principal_id
INNER JOIN sys.database_principals AS member_definition
    ON member_definition.principal_id = membership.member_principal_id
WHERE role_definition.name = N'v2_schema_owner'
   OR member_definition.name = N'v2_schema_owner'
   OR EXISTS (
       SELECT 1 FROM @expected_roles AS expected
       WHERE expected.role_name IN (role_definition.name, member_definition.name)
   );

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 51220, N'Fundação de schema V2 divergente do manifesto.', 1;
END;

PRINT N'Fundação de schema V2 validada com sucesso.';

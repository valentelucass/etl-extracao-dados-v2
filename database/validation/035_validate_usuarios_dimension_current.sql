-- Validador estrutural somente leitura da fatia Usuários de V2-035b.

SET NOCOUNT ON;

DECLARE @failures TABLE (
    category NVARCHAR(40) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(400) NOT NULL
);

DECLARE @view_id INT = OBJECT_ID(N'core.v_usuario_dimension_current_v1', N'V');

IF @view_id IS NULL
    INSERT INTO @failures VALUES
        (N'OBJECT', N'core.v_usuario_dimension_current_v1',
         N'A projeção dimensional current de Usuários está ausente.');

IF @view_id IS NOT NULL
   AND (
       CONVERT(INT, OBJECTPROPERTYEX(@view_id, N'IsSchemaBound')) <> 1
       OR NOT EXISTS (
           SELECT 1
           FROM sys.sql_modules AS module_definition
           WHERE module_definition.object_id = @view_id
             AND module_definition.uses_ansi_nulls = 1
             AND module_definition.uses_quoted_identifier = 1
       )
   )
    INSERT INTO @failures VALUES
        (N'MODULE', N'core.v_usuario_dimension_current_v1',
         N'A view deve ser schemabound com ANSI_NULLS e QUOTED_IDENTIFIER.');

DECLARE @expected_columns TABLE (
    column_id INT NOT NULL PRIMARY KEY,
    column_name SYSNAME NOT NULL,
    type_name SYSNAME NOT NULL,
    max_length SMALLINT NOT NULL,
    precision_value TINYINT NOT NULL,
    scale_value TINYINT NOT NULL,
    is_nullable BIT NOT NULL,
    collation_name SYSNAME NULL
);

INSERT INTO @expected_columns VALUES
    (1, N'usuario_id', N'bigint', 8, 19, 0, 0, NULL),
    (2, N'environment_name', N'nvarchar', 64, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (3, N'source_instance', N'nvarchar', 256, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (4, N'tenant_scope', N'nvarchar', 256, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (5, N'source_key_token', N'nvarchar', 512, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (6, N'source_key_wire_type', N'nvarchar', 32, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (7, N'name_presence', N'nvarchar', 16, 0, 0, 0, N'Latin1_General_100_BIN2'),
    (8, N'usuario_name', N'nvarchar', 510, 0, 0, 1, N'Latin1_General_100_BIN2'),
    (9, N'last_changed_at_utc', N'datetime2', 7, 23, 3, 0, NULL),
    (10, N'last_seen_at_utc', N'datetime2', 7, 23, 3, 0, NULL);

IF @view_id IS NOT NULL
   AND (
       EXISTS (
           SELECT column_id, column_name, type_name, max_length, precision_value,
                  scale_value, is_nullable, ISNULL(collation_name, N'')
           FROM @expected_columns
           EXCEPT
           SELECT column_definition.column_id, column_definition.name,
                  type_definition.name, column_definition.max_length,
                  column_definition.precision, column_definition.scale,
                  column_definition.is_nullable,
                  ISNULL(column_definition.collation_name, N'')
           FROM sys.columns AS column_definition
           INNER JOIN sys.types AS type_definition
               ON type_definition.user_type_id = column_definition.user_type_id
           WHERE column_definition.object_id = @view_id
       )
       OR EXISTS (
           SELECT column_definition.column_id, column_definition.name,
                  type_definition.name, column_definition.max_length,
                  column_definition.precision, column_definition.scale,
                  column_definition.is_nullable,
                  ISNULL(column_definition.collation_name, N'')
           FROM sys.columns AS column_definition
           INNER JOIN sys.types AS type_definition
               ON type_definition.user_type_id = column_definition.user_type_id
           WHERE column_definition.object_id = @view_id
           EXCEPT
           SELECT column_id, column_name, type_name, max_length, precision_value,
                  scale_value, is_nullable, ISNULL(collation_name, N'')
           FROM @expected_columns
       )
   )
    INSERT INTO @failures VALUES
        (N'COLUMN', N'core.v_usuario_dimension_current_v1',
         N'Ordem, nome, tipo, nullability ou collation das dez colunas diverge.');

IF @view_id IS NOT NULL
   AND (
       EXISTS (
           SELECT 1
           FROM sys.sql_expression_dependencies AS dependency
           WHERE dependency.referencing_id = @view_id
             AND dependency.referenced_id IS NOT NULL
             AND dependency.referenced_id <> OBJECT_ID(N'core.usuario', N'U')
       )
       OR NOT EXISTS (
           SELECT 1
           FROM sys.sql_expression_dependencies AS dependency
           WHERE dependency.referencing_id = @view_id
             AND dependency.referenced_id = OBJECT_ID(N'core.usuario', N'U')
       )
   )
    INSERT INTO @failures VALUES
        (N'DEPENDENCY', N'core.v_usuario_dimension_current_v1',
         N'A view deve depender somente de core.usuario.');

DECLARE @definition NVARCHAR(MAX) = OBJECT_DEFINITION(@view_id);
IF @view_id IS NOT NULL
   AND (
       @definition IS NULL
       OR @definition NOT LIKE N'%WITH SCHEMABINDING%'
       OR @definition NOT LIKE N'%FROM core.usuario AS usuario%'
       OR @definition NOT LIKE N'%usuario.active = CONVERT(BIT, 1)%'
       OR @definition NOT LIKE N'%usuario.source_key AS source_key_token%'
       OR @definition LIKE N'%usuario_history%'
       OR @definition LIKE N'% JOIN %'
       OR @definition LIKE N'%DISTINCT%'
       OR @definition LIKE N'%GROUP BY%'
       OR @definition LIKE N'%OVER (%'
       OR @definition LIKE N'%LTRIM%'
       OR @definition LIKE N'%RTRIM%'
       OR @definition LIKE N'%attribute_hash%'
       OR @definition LIKE N'%state_hash%'
       OR @definition LIKE N'%execution_id%'
   )
    INSERT INTO @failures VALUES
        (N'DEFINITION', N'core.v_usuario_dimension_current_v1',
         N'A projeção deve ser direta, ativa, sem join/history/dedupe/trim/metadado interno.');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(N'core.usuario', N'U')
      AND index_definition.name = N'PK_core_usuario'
      AND index_definition.is_primary_key = 1
      AND index_definition.is_unique = 1
)
OR NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS index_definition
    WHERE index_definition.object_id = OBJECT_ID(N'core.usuario', N'U')
      AND index_definition.name = N'UQ_core_usuario_source'
      AND index_definition.is_unique = 1
)
    INSERT INTO @failures VALUES
        (N'GRAIN', N'core.usuario',
         N'PK canônica ou identidade alternativa escopada está ausente.');

IF @view_id IS NOT NULL
   AND EXISTS (
       SELECT 1
       FROM sys.indexes AS index_definition
       WHERE index_definition.object_id = @view_id
         AND index_definition.index_id > 0
   )
    INSERT INTO @failures VALUES
        (N'INDEX', N'core.v_usuario_dimension_current_v1',
         N'Esta fatia não autoriza materialização ou índice adicional da view.');

IF OBJECT_ID(N'pub.vw_dim_usuarios', N'V') IS NOT NULL
    INSERT INTO @failures VALUES
        (N'CONSUMER_BOUNDARY', N'pub.vw_dim_usuarios',
         N'O contrato consumidor continua pendente em V2-037.');

IF @view_id IS NOT NULL
   AND EXISTS (
       SELECT 1
       FROM sys.database_permissions AS permission_definition
       WHERE permission_definition.class = 1
         AND permission_definition.major_id = @view_id
   )
    INSERT INTO @failures VALUES
        (N'PERMISSION', N'core.v_usuario_dimension_current_v1',
         N'V009 não autoriza permissão explícita na view interna.');

IF EXISTS (
    SELECT 1
    FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id IN (
              DATABASE_PRINCIPAL_ID(N'public'), DATABASE_PRINCIPAL_ID(N'v2_runtime')
          )
      AND permission_definition.permission_name = N'SELECT'
      AND permission_definition.state IN (N'G', N'W')
      AND (
          permission_definition.class = 0
          OR permission_definition.class = 3
             AND permission_definition.major_id = SCHEMA_ID(N'core')
          OR permission_definition.class = 1
             AND permission_definition.major_id IN (
                 OBJECT_ID(N'core.usuario', N'U'), @view_id
             )
      )
)
    INSERT INTO @failures VALUES
        (N'PERMISSION', N'public/v2_runtime',
         N'Leitura efetiva por grant direto/database/schema foi concedida indevidamente.');

IF NOT EXISTS (
    SELECT 1
    FROM sys.schemas AS schema_definition
    INNER JOIN sys.database_principals AS owner_definition
        ON owner_definition.principal_id = schema_definition.principal_id
    WHERE schema_definition.schema_id = SCHEMA_ID(N'core')
      AND owner_definition.name = N'v2_schema_owner'
)
    INSERT INTO @failures VALUES
        (N'OWNER', N'core', N'O owner isolado do schema core diverge.');

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail
    FROM @failures
    ORDER BY category, object_name;
    THROW 52010, N'Dimensão current de Usuários diverge do contrato de sombra.', 1;
END;

PRINT N'Dimensão current de Usuários V2 validada com sucesso.';

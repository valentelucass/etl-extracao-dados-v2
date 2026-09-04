-- Exercício sintético, set-based e rollback-only da fatia Usuários de V2-035b.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 52020, N'O exercício dimensional aceita somente o banco local V2 de sombra.', 1;

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

DECLARE @observed_at DATETIME2(3) = CONVERT(DATETIME2(3), N'2026-01-02T03:04:05.006');
DECLARE @changed_at DATETIME2(3) = DATEADD(MINUTE, -1, @observed_at);
DECLARE @first_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000901';
DECLARE @replay_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000902';
DECLARE @cycle_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000903';
DECLARE @partition_id BIGINT;

INSERT INTO ctl.source_catalog (source_instance, source_kind, active, registered_at_utc)
VALUES (N'SYNTHETIC_DIMENSION_SOURCE', N'GRAPHQL', 1, @changed_at);

INSERT INTO ctl.execution_cycle (cycle_id, plan_version, plan_fingerprint, planned_at_utc)
VALUES (@cycle_id, N'synthetic-dimension-plan-v1', REPLICATE('a', 64), @changed_at);

INSERT INTO ctl.execution_partition (
    environment_name, source_instance, tenant_scope, entity_name, execution_mode,
    partition_start_utc, partition_end_exclusive_utc, current_execution_id,
    current_state, next_attempt_number, created_at_utc
)
VALUES (
    N'LOCAL_SHADOW', N'SYNTHETIC_DIMENSION_SOURCE', N'SYNTHETIC_DIMENSION_TENANT',
    N'usuarios', N'BACKFILL', DATEADD(DAY, -1, @observed_at), @observed_at,
    NULL, NULL, 3, @changed_at
);
SET @partition_id = CONVERT(BIGINT, SCOPE_IDENTITY());

INSERT INTO ctl.execution_attempt (
    execution_id, partition_id, cycle_id, attempt_number, window_strategy,
    contract_version, contract_fingerprint, configuration_version,
    configuration_fingerprint, idempotency_key, replay_of_execution_id,
    current_state, next_transition_sequence, started_at_utc, terminal_at_utc
)
VALUES
    (@first_execution, @partition_id, @cycle_id, 1, N'GRAPHQL_RESTART_FROM_BEGINNING',
     N'graphql-users-v1', REPLICATE('b', 64), N'users-shadow-v1', REPLICATE('c', 64),
     N'synthetic-dimension-first', NULL, N'EXTRACTING', 3, @changed_at, NULL),
    (@replay_execution, @partition_id, @cycle_id, 2, N'GRAPHQL_RESTART_FROM_BEGINNING',
     N'graphql-users-v1', REPLICATE('b', 64), N'users-shadow-v1', REPLICATE('c', 64),
     N'synthetic-dimension-replay', @first_execution, N'EXTRACTING', 3,
     @observed_at, NULL);

DECLARE @synthetic_sources TABLE (
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    wire_type NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    name_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    usuario_name NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NULL,
    active BIT NOT NULL,
    hash_token CHAR(1) NOT NULL
);
INSERT INTO @synthetic_sources VALUES
    (N'INTEGER:4101', N'INTEGER', N'VALUE', N'  synthetic-current-value  ', 1, '1'),
    (N'STRING:4101', N'STRING', N'NULL', NULL, 1, '2'),
    (N'STRING:synthetic-absent', N'STRING', N'ABSENT', NULL, 1, '3'),
    (N'STRING:synthetic-inactive', N'STRING', N'VALUE', N'synthetic-inactive-value', 0, '4');

INSERT INTO core.entity_record_state (
    environment_name, source_instance, tenant_scope, entity_name, source_key,
    row_fingerprint_version, source_row_hash, presence_fingerprint_version,
    presence_fingerprint, source_freshness_at_utc, active,
    first_promoted_execution_id, last_promoted_execution_id,
    first_promoted_at_utc, last_promoted_at_utc
)
SELECT N'LOCAL_SHADOW', N'SYNTHETIC_DIMENSION_SOURCE', N'SYNTHETIC_DIMENSION_TENANT',
       N'usuarios', source.source_key, N'usuarios-attributes-v1',
       REPLICATE(source.hash_token, 64), N'usuarios-name-presence-v1',
       REPLICATE(source.hash_token, 64), NULL, source.active,
       @first_execution, @replay_execution, @changed_at, @observed_at
FROM @synthetic_sources AS source;

INSERT INTO core.usuario (
    record_state_id, environment_name, source_instance, tenant_scope, entity_name,
    source_key, source_key_wire_type, name_presence, usuario_name,
    attribute_fingerprint_version, attribute_hash, state_fingerprint_version,
    state_hash, active, first_seen_execution_id, last_seen_execution_id,
    last_changed_execution_id, first_seen_at_utc, last_seen_at_utc,
    last_changed_at_utc, observation_order_at_utc, observation_order_execution_id
)
SELECT state.record_state_id, state.environment_name, state.source_instance,
       state.tenant_scope, state.entity_name, state.source_key, source.wire_type,
       source.name_presence, source.usuario_name, N'usuarios-attributes-v1',
       REPLICATE(source.hash_token, 64), N'usuarios-state-v1',
       REPLICATE(source.hash_token, 64), source.active,
       @first_execution, @replay_execution, @replay_execution,
       @changed_at, @observed_at, @observed_at, @observed_at, @replay_execution
FROM core.entity_record_state AS state
INNER JOIN @synthetic_sources AS source
    ON source.source_key = state.source_key
WHERE state.environment_name = N'LOCAL_SHADOW'
  AND state.source_instance = N'SYNTHETIC_DIMENSION_SOURCE'
  AND state.tenant_scope = N'SYNTHETIC_DIMENSION_TENANT'
  AND state.entity_name = N'usuarios';

DECLARE @value_usuario_id BIGINT = (
    SELECT usuario_id
    FROM core.usuario
    WHERE source_key = N'INTEGER:4101'
);

INSERT INTO core.usuario_history (
    usuario_id, execution_id, change_kind, name_presence, usuario_name,
    attribute_fingerprint_version, attribute_hash, state_fingerprint_version,
    state_hash, active, observation_order_at_utc,
    observation_order_execution_id, changed_at_utc
)
VALUES
    (@value_usuario_id, @first_execution, N'INSERTED', N'VALUE', N'synthetic-before-value',
     N'usuarios-attributes-v1', REPLICATE('5', 64), N'usuarios-state-v1',
     REPLICATE('5', 64), 1, @changed_at, @first_execution, @changed_at),
    (@value_usuario_id, @replay_execution, N'UPDATED', N'VALUE',
     N'  synthetic-current-value  ', N'usuarios-attributes-v1', REPLICATE('1', 64),
     N'usuarios-state-v1', REPLICATE('1', 64), 1, @observed_at,
     @replay_execution, @observed_at);

IF (SELECT COUNT_BIG(*) FROM core.v_usuario_dimension_current_v1) <> 3
    THROW 52021, N'A dimensão não projetou exatamente os três current ativos.', 1;

IF EXISTS (
    SELECT 1
    FROM core.v_usuario_dimension_current_v1
    WHERE source_key_token = N'STRING:synthetic-inactive'
)
    THROW 52022, N'Current inativo vazou para a dimensão.', 1;

IF (SELECT COUNT_BIG(*)
    FROM core.v_usuario_dimension_current_v1
    WHERE source_key_token IN (N'INTEGER:4101', N'STRING:4101')) <> 2
    THROW 52023, N'Tokens INTEGER/STRING distintos foram colapsados.', 1;

IF NOT EXISTS (
    SELECT 1 FROM core.v_usuario_dimension_current_v1
    WHERE name_presence = N'VALUE' AND usuario_name = N'  synthetic-current-value  '
)
OR NOT EXISTS (
    SELECT 1 FROM core.v_usuario_dimension_current_v1
    WHERE name_presence = N'NULL' AND usuario_name IS NULL
)
OR NOT EXISTS (
    SELECT 1 FROM core.v_usuario_dimension_current_v1
    WHERE name_presence = N'ABSENT' AND usuario_name IS NULL
)
    THROW 52024, N'A presença tri-state ou a preservação sem trim divergiu.', 1;

IF (SELECT COUNT_BIG(*)
    FROM core.v_usuario_dimension_current_v1
    WHERE usuario_id = @value_usuario_id) <> 1
OR (SELECT COUNT_BIG(*)
    FROM core.usuario_history
    WHERE usuario_id = @value_usuario_id) <> 2
    THROW 52025, N'Histórico/replay multiplicou ou perdeu o grão current.', 1;

IF EXISTS (
    SELECT usuario_id
    FROM core.v_usuario_dimension_current_v1
    GROUP BY usuario_id
    HAVING COUNT_BIG(*) <> 1
)
    THROW 52026, N'O grão canônico não é uma linha por usuario_id.', 1;

IF EXISTS (
    SELECT 1
    FROM core.v_usuario_dimension_current_v1 AS dimension
    INNER JOIN core.usuario AS current_record
        ON current_record.usuario_id = dimension.usuario_id
    WHERE dimension.last_changed_at_utc <> current_record.last_changed_at_utc
       OR dimension.last_seen_at_utc <> current_record.last_seen_at_utc
)
    THROW 52027, N'Os timestamps técnicos current não foram projetados sem transformação.', 1;

CREATE USER v2_runtime_usuario_dimension_probe WITHOUT LOGIN;
ALTER ROLE v2_runtime ADD MEMBER v2_runtime_usuario_dimension_probe;
DECLARE @runtime_can_select INT;
EXECUTE AS USER = N'v2_runtime_usuario_dimension_probe';
SET @runtime_can_select = HAS_PERMS_BY_NAME(
    N'core.v_usuario_dimension_current_v1', N'OBJECT', N'SELECT'
);
REVERT;
IF @runtime_can_select <> 0
    THROW 52028, N'v2_runtime ganhou SELECT na dimensão interna.', 1;
ALTER ROLE v2_runtime DROP MEMBER v2_runtime_usuario_dimension_probe;
DROP USER v2_runtime_usuario_dimension_probe;

CREATE USER public_usuario_dimension_probe WITHOUT LOGIN;
DECLARE @public_can_select INT;
EXECUTE AS USER = N'public_usuario_dimension_probe';
SET @public_can_select = HAS_PERMS_BY_NAME(
    N'core.v_usuario_dimension_current_v1', N'OBJECT', N'SELECT'
);
REVERT;
IF @public_can_select <> 0
    THROW 52029, N'public ganhou SELECT na dimensão interna.', 1;
DROP USER public_usuario_dimension_probe;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 52030, N'O escopo rollback-only da dimensão foi consumido ou invalidado.', 1;
ROLLBACK TRANSACTION;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 52031, N'O rollback da dimensão não encerrou o escopo sintético.', 1;

PRINT N'Dimensão current de Usuários exercitada e revertida com sucesso.';

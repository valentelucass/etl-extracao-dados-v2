-- Exercício sintético, set-based e rollback-only da vertical V2-033.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51790, N'O exercício de Usuários aceita somente o banco local V2 de sombra.', 1;

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

IF OBJECT_ID(N'core.usuario', N'U') IS NULL
    OR OBJECT_ID(N'core.usuario_history', N'U') IS NULL
    OR OBJECT_ID(N'stg.usp_stage_usuario_record', N'P') IS NULL
    OR OBJECT_ID(N'core.usp_apply_reconcile_publish_usuarios', N'P') IS NULL
    THROW 51791, N'A reconstrução não criou a vertical de Usuários.', 1;

DECLARE @usuario_source_index_id INT = (
    SELECT index_id
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'core.usuario')
      AND name = N'UQ_core_usuario_source'
);
IF @usuario_source_index_id IS NULL
    OR (SELECT COUNT_BIG(*) FROM sys.index_columns
        WHERE object_id = OBJECT_ID(N'core.usuario')
          AND index_id = @usuario_source_index_id AND key_ordinal > 0) <> 5
    OR NOT EXISTS (
        SELECT 1
        FROM sys.index_columns AS indexed
        INNER JOIN sys.columns AS column_definition
            ON column_definition.object_id = indexed.object_id
           AND column_definition.column_id = indexed.column_id
        WHERE indexed.object_id = OBJECT_ID(N'core.usuario')
          AND indexed.index_id = @usuario_source_index_id
          AND indexed.key_ordinal = 1
          AND column_definition.name = N'environment_name'
    )
    THROW 51791, N'A identidade de Usuários não está isolada por environment.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_usuario_record'))
        NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 20%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_usuario_record')) NOT LIKE N'%(159)%'
    THROW 51791, N'Os limites de ordinal/C1 de Usuários não foram publicados.', 1;

IF OBJECT_ID(N'recon.trg_usuario_quarantine_immutable', N'TR') IS NULL
    OR OBJECT_ID(N'ctl.trg_usuario_promotion_result_immutable', N'TR') IS NULL
    OR OBJECT_ID(N'recon.trg_usuario_data_quality_requires_typed_pass', N'TR') IS NULL
    OR OBJECT_ID(N'ctl.trg_usuario_lifecycle_plan_budget', N'TR') IS NULL
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.trg_usuario_lifecycle_plan_budget')) LIKE N'%THROW 51718%'
    THROW 51791, N'Os gates imutáveis/fail-closed de Usuários não foram publicados.', 1;

IF CONVERT(INT, OBJECTPROPERTYEX(
        OBJECT_ID(N'ctl.trg_execution_publication_requires_data_quality'),
        N'ExecIsFirstInsertTrigger')) <> 1
    OR CONVERT(INT, OBJECTPROPERTYEX(
        OBJECT_ID(N'ctl.trg_usuario_publication_requires_apply_wrapper'),
        N'ExecIsLastInsertTrigger')) <> 1
    THROW 51791, N'A ordem determinística dos gates de publicação não foi aplicada.', 1;
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @cycle_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000701';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_USERS_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_USERS_TENANT';
DECLARE @entity_name NVARCHAR(128) = N'usuarios';
DECLARE @threshold_owner NVARCHAR(64) = N'quality-owner';
DECLARE @quarantine_owner NVARCHAR(64) = N'quarantine-owner';
DECLARE @retention_owner NVARCHAR(64) = N'retention-owner';
DECLARE @retention_version NVARCHAR(128) = N'synthetic-users-retention-v1';
DECLARE @policy_effective DATETIME2(3) = DATEADD(DAY, -1, @now);

EXEC ctl.usp_control_plane_register_source @source_instance, N'GRAPHQL', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle_id, N'synthetic-users-plan-v1', @plan_fingerprint, @now;

DECLARE @modes TABLE (
    environment_name NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,
    execution_mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL
);
INSERT INTO @modes (environment_name, execution_mode, policy_version) VALUES
    (N'LOCAL_SHADOW', N'BACKFILL', N'synthetic-users-dq-backfill-v1'),
    (N'LOCAL_SHADOW', N'REPLAY', N'synthetic-users-dq-replay-v1'),
    (N'OTHER_SHADOW', N'BACKFILL', N'synthetic-users-dq-other-backfill-v1');

DECLARE @policies TABLE (
    execution_mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_fingerprint CHAR(64) NOT NULL,
    policy_fingerprint CHAR(64) NULL
);
INSERT INTO @policies (execution_mode, policy_version, scope_fingerprint)
SELECT execution_mode, policy_version,
       LOWER(CONVERT(CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
           N'dq-scope-v1|',
           DATALENGTH(environment_name), N':', environment_name, N'|',
           DATALENGTH(@source_instance), N':', @source_instance, N'|',
           DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
           DATALENGTH(@entity_name), N':', @entity_name, N'|',
           DATALENGTH(execution_mode), N':', execution_mode
       ))), 2))
FROM @modes;

UPDATE policy
SET policy_fingerprint = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-policy-v1|',
        DATALENGTH(policy.policy_version), N':', policy.policy_version, N'|',
        policy.scope_fingerprint, N'|4|3600|',
        DATALENGTH(@threshold_owner), N':', @threshold_owner, N'|',
        DATALENGTH(@quarantine_owner), N':', @quarantine_owner, N'|',
        DATALENGTH(@retention_version), N':', @retention_version, N'|',
        DATALENGTH(@retention_owner), N':', @retention_owner, N'|',
        CONVERT(NVARCHAR(33), @policy_effective, 126), N'|',
        N'1:COUNT_EQUATION:0:0:', DATALENGTH(@threshold_owner), N':', @threshold_owner, N'|',
        N'2:PAGE_TERMINALITY:0:0:', DATALENGTH(@threshold_owner), N':', @threshold_owner, N'|',
        N'3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(@threshold_owner), N':',
            @threshold_owner, N'|',
        N'4:QUARANTINE_SLA:0:0:', DATALENGTH(@threshold_owner), N':', @threshold_owner
    ))), 2))
FROM @policies AS policy;

INSERT INTO ctl.data_quality_policy (
    policy_version, policy_fingerprint, scope_fingerprint, expected_checks,
    quarantine_sla_seconds, threshold_owner_role, quarantine_sla_owner_role,
    retention_policy_version, retention_owner_role, policy_state,
    effective_from_utc, recorded_at_utc
)
SELECT policy_version, policy_fingerprint, scope_fingerprint, 4, 3600,
       @threshold_owner, @quarantine_owner, @retention_version, @retention_owner,
       N'RATIFIED', @policy_effective, @now
FROM @policies;

INSERT INTO ctl.data_quality_check_policy (
    policy_version, policy_fingerprint, check_ordinal, check_code,
    maximum_failed_rows, maximum_failure_basis_points, threshold_owner_role
)
SELECT policy.policy_version, policy.policy_fingerprint,
       check_definition.check_ordinal, check_definition.check_code, 0, 0, @threshold_owner
FROM @policies AS policy
CROSS JOIN (VALUES
    (CONVERT(SMALLINT, 1), N'COUNT_EQUATION'),
    (CONVERT(SMALLINT, 2), N'PAGE_TERMINALITY'),
    (CONVERT(SMALLINT, 3), N'PROMOTION_RECONCILIATION'),
    (CONVERT(SMALLINT, 4), N'QUARANTINE_SLA')
) AS check_definition (check_ordinal, check_code);
GO

CREATE TABLE #usuario_result (
    execution_id UNIQUEIDENTIFIER, candidate_rows BIGINT, inserted_rows BIGINT,
    updated_rows BIGINT, reactivated_rows BIGINT, noop_rows BIGINT,
    stale_noop_rows BIGINT, reconciled_at_utc DATETIME2(3), published_at_utc DATETIME2(3),
    incremental_frontier_before_utc DATETIME2(3),
    incremental_frontier_after_utc DATETIME2(3)
);
GO

CREATE OR ALTER PROCEDURE #run_usuario_execution
    @execution_id UNIQUEIDENTIFIER,
    @partition_start_utc DATETIME2(3),
    @partition_end_exclusive_utc DATETIME2(3),
    @idempotency_key NVARCHAR(128),
    @execution_mode NVARCHAR(16) = N'BACKFILL',
    @replay_of_execution_id UNIQUEIDENTIFIER = NULL,
    @source_key_1 NVARCHAR(256),
    @wire_type_1 NVARCHAR(16),
    @name_presence_1 NVARCHAR(8),
    @usuario_name_1 NVARCHAR(255),
    @source_key_2 NVARCHAR(256) = NULL,
    @wire_type_2 NVARCHAR(16) = NULL,
    @name_presence_2 NVARCHAR(8) = NULL,
    @usuario_name_2 NVARCHAR(255) = NULL,
    @environment_name NVARCHAR(32) = N'LOCAL_SHADOW',
    @forced_started_at_utc DATETIME2(3) = NULL,
    @publish BIT = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @now DATETIME2(3) = COALESCE(@forced_started_at_utc, SYSUTCDATETIME());
    DECLARE @row_count BIGINT = CASE WHEN @source_key_2 IS NULL THEN 1 ELSE 2 END;
    DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
    DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
    DECLARE @policy_version NVARCHAR(128);
    DECLARE @policy_fingerprint CHAR(64);
    SELECT @policy_version = policy_version, @policy_fingerprint = policy_fingerprint
    FROM ctl.data_quality_policy
    WHERE scope_fingerprint = LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'dq-scope-v1|',
            DATALENGTH(@environment_name), N':', @environment_name, N'|',
            DATALENGTH(N'SYNTHETIC_USERS_SOURCE'), N':', N'SYNTHETIC_USERS_SOURCE', N'|',
            DATALENGTH(N'SYNTHETIC_USERS_TENANT'), N':', N'SYNTHETIC_USERS_TENANT', N'|',
            DATALENGTH(N'usuarios'), N':', N'usuarios', N'|',
            DATALENGTH(@execution_mode), N':', @execution_mode
        ))), 2));

    IF @policy_version IS NULL OR @policy_fingerprint IS NULL
        THROW 51792, N'A policy sintética de Data Quality não foi localizada.', 1;

    EXEC ctl.usp_control_plane_start_execution
        @execution_id = @execution_id,
        @cycle_id = '00000000-0000-0000-0000-000000000701',
        @environment_name = @environment_name,
        @source_instance = N'SYNTHETIC_USERS_SOURCE',
        @tenant_scope = N'SYNTHETIC_USERS_TENANT',
        @entity_name = N'usuarios',
        @execution_mode = @execution_mode,
        @partition_start_utc = @partition_start_utc,
        @partition_end_exclusive_utc = @partition_end_exclusive_utc,
        @window_strategy = N'GRAPHQL_RESTART_FROM_BEGINNING',
        @contract_version = N'graphql-users-v1',
        @contract_fingerprint = @contract_fingerprint,
        @configuration_version = N'users-shadow-v1',
        @configuration_fingerprint = @configuration_fingerprint,
        @idempotency_key = @idempotency_key,
        @replay_of_execution_id = @replay_of_execution_id,
        @lease_seconds = 3600,
        @started_at_utc = @now;

    -- O control plane valida o clock fornecido, mas persiste o clock do banco. O override existe
    -- somente nesta fixture para construir um empate determinístico e mantém ledger/lease coerentes.
    IF @forced_started_at_utc IS NOT NULL
    BEGIN
        UPDATE ctl.execution_attempt
        SET started_at_utc = @forced_started_at_utc
        WHERE execution_id = @execution_id;
        UPDATE ctl.execution_state_event
        SET transitioned_at_utc = @forced_started_at_utc
        WHERE execution_id = @execution_id AND transition_sequence IN (1, 2);
        UPDATE ctl.execution_lease
        SET acquired_at_utc = @forced_started_at_utc,
            heartbeat_at_utc = @forced_started_at_utc,
            expires_at_utc = DATEADD(SECOND, 3600, @forced_started_at_utc)
        WHERE execution_id = @execution_id;
    END;

    EXEC ctl.usp_control_plane_record_page
        @execution_id = @execution_id, @page_number = 1, @page_attempt = 1,
        @requested_page_size = 20, @physical_rows = @row_count,
        @distinct_root_keys = @row_count, @response_bytes = 256,
        @terminal_empty_page = 0, @read_at_utc = @now,
        @terminal_evidence_kind = N'GRAPHQL_PAGE_INFO';

    EXEC stg.usp_stage_usuario_record
        @execution_id, 1, 1, @source_key_1, @wire_type_1,
        @name_presence_1, @usuario_name_1, N'VALID', NULL, @now;
    IF @source_key_2 IS NOT NULL
        EXEC stg.usp_stage_usuario_record
            @execution_id, 1, 2, @source_key_2, @wire_type_2,
            @name_presence_2, @usuario_name_2, N'VALID', NULL, @now;

    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
    EXEC core.usp_prepare_staged_execution
        @execution_id, N'graphql-users-v1', @contract_fingerprint,
        N'users-shadow-v1', @configuration_fingerprint;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.usuario_promotion_result
        WHERE execution_id = @execution_id AND validation_state = N'PASSED'
          AND generic_candidate_rows = @row_count AND typed_candidate_rows = @row_count
          AND conflicting_root_keys = 0 AND generic_quarantine_rows = 0
    )
        THROW 51792, N'O candidate set tipado de Usuários não foi validado.', 1;

    DECLARE @dq TABLE (
        execution_id UNIQUEIDENTIFIER, policy_version NVARCHAR(128),
        policy_fingerprint CHAR(64), evaluation_fingerprint CHAR(64),
        expected_checks SMALLINT, completed_checks SMALLINT, passed_checks SMALLINT,
        failed_checks SMALLINT, evaluated_rows BIGINT, failed_rows BIGINT,
        evaluation_state NVARCHAR(16), evaluated_at_utc DATETIME2(3)
    );
    INSERT INTO @dq
    EXEC recon.usp_evaluate_execution_data_quality
        @execution_id, @policy_version, @policy_fingerprint;
    IF NOT EXISTS (
        SELECT 1 FROM @dq
        WHERE execution_id = @execution_id AND expected_checks = 4 AND completed_checks = 4
          AND passed_checks = 4 AND failed_checks = 0 AND failed_rows = 0
          AND evaluation_state = N'PASSED'
    )
        THROW 51793, N'A Data Quality de Usuários não passou.', 1;

    IF @publish = 1
    BEGIN
        EXEC core.usp_apply_reconcile_publish_usuarios
            @execution_id, N'graphql-users-v1', @contract_fingerprint,
            N'users-shadow-v1', @configuration_fingerprint;
        INSERT INTO #usuario_result
        SELECT result.execution_id, result.candidate_rows, result.inserted_rows,
               result.updated_rows, result.reactivated_rows, result.noop_rows,
               result.stale_noop_rows, result.reconciled_at_utc, result.published_at_utc,
               publication.incremental_frontier_before_utc,
               publication.incremental_frontier_after_utc
        FROM recon.usuario_reconciliation_result AS result
        INNER JOIN ctl.execution_publication_event AS publication
            ON publication.execution_id = result.execution_id
        WHERE result.execution_id = @execution_id;
    END;
END;
GO

DECLARE @initial_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000702';
DECLARE @same_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000703';
DECLARE @change_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000704';
DECLARE @reactivation_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000705';
DECLARE @absence_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000706';
DECLARE @absent_field_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000707';
DECLARE @null_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000708';
DECLARE @stale_replay_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000709';
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
EXEC #run_usuario_execution
    @initial_execution, '2026-01-01T00:00:00', '2026-01-01T01:00:00',
    N'users-initial', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'VALUE', N'Usuário sintético A',
    N'STRING:42', N'STRING', N'VALUE', N'Usuário sintético B';
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE inserted_rows = 2 AND candidate_rows = 2)
    OR (SELECT COUNT_BIG(*) FROM core.usuario) <> 2
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history WHERE change_kind = N'INSERTED') <> 2
    OR (SELECT COUNT_BIG(DISTINCT usuario_id) FROM core.usuario) <> 2
    THROW 51794, N'Insert inicial ou distinção INTEGER/STRING falhou.', 1;

DECLARE @integer_usuario_id BIGINT = (
    SELECT usuario_id FROM core.usuario WHERE source_key = N'INTEGER:42'
);
DECLARE @string_usuario_id BIGINT = (
    SELECT usuario_id FROM core.usuario WHERE source_key = N'STRING:42'
);
IF @integer_usuario_id = @string_usuario_id
    THROW 51795, N'Wire types distintos colidiram no canonical ID.', 1;

DELETE FROM #usuario_result;
EXEC core.usp_apply_reconcile_publish_usuarios
    @initial_execution, N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint;
INSERT INTO #usuario_result
SELECT result.execution_id, result.candidate_rows, result.inserted_rows,
       result.updated_rows, result.reactivated_rows, result.noop_rows,
       result.stale_noop_rows, result.reconciled_at_utc, result.published_at_utc,
       publication.incremental_frontier_before_utc,
       publication.incremental_frontier_after_utc
FROM recon.usuario_reconciliation_result AS result
INNER JOIN ctl.execution_publication_event AS publication
    ON publication.execution_id = result.execution_id
WHERE result.execution_id = @initial_execution;
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE inserted_rows = 2 AND candidate_rows = 2)
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history) <> 2
    THROW 51796, N'O retry pós-commit duplicou estado ou histórico.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @same_execution, '2026-01-01T01:00:00', '2026-01-01T02:00:00',
    N'users-noop', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'VALUE', N'Usuário sintético A';
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE noop_rows = 1 AND stale_noop_rows = 0)
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history) <> 2
    THROW 51797, N'O estado idêntico criou histórico ou não foi no-op.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @change_execution, '2026-01-01T02:00:00', '2026-01-01T03:00:00',
    N'users-change', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'VALUE', N'Usuário sintético A alterado';
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE updated_rows = 1)
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history
        WHERE usuario_id = @integer_usuario_id) <> 2
    THROW 51798, N'A mudança de hash não produziu um único histórico.', 1;

DECLARE @inactive_state_hash CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'usuarios-state-v1|',
        (SELECT attribute_hash FROM core.usuario WHERE usuario_id = @integer_usuario_id),
        N'|active=0'
    ))), 2));
DECLARE @integer_record_state_id BIGINT = (
    SELECT record_state_id FROM core.usuario WHERE usuario_id = @integer_usuario_id
);
UPDATE core.entity_record_state
SET active = 0
WHERE record_state_id = @integer_record_state_id;
UPDATE core.usuario SET active = 0, state_hash = @inactive_state_hash
WHERE usuario_id = @integer_usuario_id;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @reactivation_execution, '2026-01-01T03:00:00', '2026-01-01T04:00:00',
    N'users-reactivation', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'VALUE', N'Usuário sintético A alterado';
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE reactivated_rows = 1)
    OR NOT EXISTS (SELECT 1 FROM core.usuario
                   WHERE usuario_id = @integer_usuario_id AND active = 1)
    OR NOT EXISTS (SELECT 1 FROM core.entity_record_state
                   WHERE record_state_id = @integer_record_state_id AND active = 1)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_candidate_application
                   WHERE execution_id = @reactivation_execution
                     AND source_key = N'INTEGER:42'
                     AND application_disposition = N'REACTIVATED')
    OR NOT EXISTS (SELECT 1 FROM recon.usuario_candidate_application
                   WHERE execution_id = @reactivation_execution
                     AND source_key = N'INTEGER:42'
                     AND application_disposition = N'REACTIVATED')
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history
        WHERE usuario_id = @integer_usuario_id AND change_kind = N'REACTIVATED') <> 1
    THROW 51799, N'O reaparecimento não reativou o mesmo canonical ID.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @absence_execution, '2026-01-01T04:00:00', '2026-01-01T05:00:00',
    N'users-absence-disabled', N'BACKFILL', NULL,
    N'STRING:42', N'STRING', N'VALUE', N'Usuário sintético B';
IF NOT EXISTS (SELECT 1 FROM core.usuario
               WHERE usuario_id = @integer_usuario_id AND active = 1)
    OR EXISTS (SELECT 1 FROM core.usuario_history
               WHERE execution_id = @absence_execution AND usuario_id = @integer_usuario_id)
    THROW 51800, N'A ausência indevidamente desativou Usuários.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @absent_field_execution, '2026-01-01T05:00:00', '2026-01-01T06:00:00',
    N'users-partial-absent', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'ABSENT', NULL;
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE noop_rows = 1)
    OR NOT EXISTS (SELECT 1 FROM core.usuario
                   WHERE usuario_id = @integer_usuario_id AND name_presence = N'VALUE'
                     AND usuario_name = N'Usuário sintético A alterado')
    THROW 51801, N'Campo ausente apagou valor conhecido.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @null_execution, '2026-01-01T06:00:00', '2026-01-01T07:00:00',
    N'users-explicit-null', N'BACKFILL', NULL,
    N'INTEGER:42', N'INTEGER', N'NULL', NULL;
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE updated_rows = 1)
    OR NOT EXISTS (SELECT 1 FROM core.usuario
                   WHERE usuario_id = @integer_usuario_id AND name_presence = N'NULL'
                     AND usuario_name IS NULL)
    THROW 51802, N'Nulo explícito não foi distinguido de ausência.', 1;

DELETE FROM #usuario_result;
EXEC #run_usuario_execution
    @stale_replay_execution, '2026-01-01T00:00:00', '2026-01-01T01:00:00',
    N'users-stale-replay', N'REPLAY', @initial_execution,
    N'INTEGER:42', N'INTEGER', N'VALUE', N'Estado sintético obsoleto';
IF NOT EXISTS (SELECT 1 FROM #usuario_result WHERE noop_rows = 1 AND stale_noop_rows = 1)
    OR NOT EXISTS (SELECT 1 FROM core.usuario
                   WHERE usuario_id = @integer_usuario_id AND name_presence = N'NULL'
                     AND usuario_name IS NULL)
    OR EXISTS (SELECT 1 FROM core.usuario_history WHERE execution_id = @stale_replay_execution)
    THROW 51803, N'O replay obsoleto alterou current/history.', 1;

-- A mesma identidade de origem em outro environment produz current e identidade técnica próprios.
DECLARE @other_environment_execution UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000712';
EXEC #run_usuario_execution
    @execution_id = @other_environment_execution,
    @partition_start_utc = '2026-01-01T09:00:00',
    @partition_end_exclusive_utc = '2026-01-01T10:00:00',
    @idempotency_key = N'users-other-environment',
    @source_key_1 = N'INTEGER:42',
    @wire_type_1 = N'INTEGER',
    @name_presence_1 = N'VALUE',
    @usuario_name_1 = N'Usuário sintético isolado',
    @environment_name = N'OTHER_SHADOW';
DECLARE @other_usuario_id BIGINT = (
    SELECT usuario_id
    FROM core.usuario
    WHERE environment_name = N'OTHER_SHADOW' AND source_key = N'INTEGER:42'
);
IF @other_usuario_id IS NULL OR @other_usuario_id = @integer_usuario_id
    OR (SELECT COUNT_BIG(*) FROM core.usuario
        WHERE source_instance = N'SYNTHETIC_USERS_SOURCE'
          AND tenant_scope = N'SYNTHETIC_USERS_TENANT'
          AND entity_name = N'usuarios' AND source_key = N'INTEGER:42') <> 2
    OR NOT EXISTS (
        SELECT 1 FROM core.usuario
        WHERE usuario_id = @integer_usuario_id AND environment_name = N'LOCAL_SHADOW'
          AND name_presence = N'NULL' AND usuario_name IS NULL
    )
    THROW 51809, N'O environment não isolou a identidade/current de Usuários.', 1;

-- Empate de started_at é desempatado pelo execution_id de origem; uma origem intermediária
-- posterior ao vencedor é stale e não cria histórico.
DECLARE @tie_started_at_utc DATETIME2(3) = DATEADD(MINUTE, -1, SYSUTCDATETIME());
DECLARE @tie_ids TABLE (
    order_ordinal INT NOT NULL PRIMARY KEY,
    execution_id UNIQUEIDENTIFIER NOT NULL UNIQUE
);
INSERT INTO @tie_ids (order_ordinal, execution_id)
SELECT ROW_NUMBER() OVER (ORDER BY candidate.execution_id), candidate.execution_id
FROM (VALUES
    (CONVERT(UNIQUEIDENTIFIER, '00000000-0000-0000-0000-000000000713')),
    (CONVERT(UNIQUEIDENTIFIER, '00000000-0000-0000-0000-000000000714')),
    (CONVERT(UNIQUEIDENTIFIER, '00000000-0000-0000-0000-000000000715'))
) AS candidate(execution_id);
DECLARE @tie_low UNIQUEIDENTIFIER =
    (SELECT execution_id FROM @tie_ids WHERE order_ordinal = 1);
DECLARE @tie_middle UNIQUEIDENTIFIER =
    (SELECT execution_id FROM @tie_ids WHERE order_ordinal = 2);
DECLARE @tie_high UNIQUEIDENTIFIER =
    (SELECT execution_id FROM @tie_ids WHERE order_ordinal = 3);

EXEC #run_usuario_execution
    @execution_id = @tie_low,
    @partition_start_utc = '2026-01-01T10:00:00',
    @partition_end_exclusive_utc = '2026-01-01T11:00:00',
    @idempotency_key = N'users-tie-low',
    @source_key_1 = N'INTEGER:77', @wire_type_1 = N'INTEGER',
    @name_presence_1 = N'VALUE', @usuario_name_1 = N'Empate sintético A',
    @forced_started_at_utc = @tie_started_at_utc;
EXEC #run_usuario_execution
    @execution_id = @tie_high,
    @partition_start_utc = '2026-01-01T11:00:00',
    @partition_end_exclusive_utc = '2026-01-01T12:00:00',
    @idempotency_key = N'users-tie-high',
    @source_key_1 = N'INTEGER:77', @wire_type_1 = N'INTEGER',
    @name_presence_1 = N'VALUE', @usuario_name_1 = N'Empate sintético B',
    @forced_started_at_utc = @tie_started_at_utc;
EXEC #run_usuario_execution
    @execution_id = @tie_middle,
    @partition_start_utc = '2026-01-01T12:00:00',
    @partition_end_exclusive_utc = '2026-01-01T13:00:00',
    @idempotency_key = N'users-tie-middle-stale',
    @source_key_1 = N'INTEGER:77', @wire_type_1 = N'INTEGER',
    @name_presence_1 = N'VALUE', @usuario_name_1 = N'Empate sintético obsoleto',
    @forced_started_at_utc = @tie_started_at_utc;
IF NOT EXISTS (SELECT 1 FROM #usuario_result
               WHERE execution_id = @tie_high AND updated_rows = 1)
    OR NOT EXISTS (SELECT 1 FROM #usuario_result
                   WHERE execution_id = @tie_middle
                     AND noop_rows = 1 AND stale_noop_rows = 1)
    OR NOT EXISTS (
        SELECT 1 FROM core.usuario
        WHERE environment_name = N'LOCAL_SHADOW' AND source_key = N'INTEGER:77'
          AND usuario_name = N'Empate sintético B'
          AND observation_order_at_utc = @tie_started_at_utc
          AND observation_order_execution_id = @tie_high
    )
    OR (SELECT COUNT_BIG(*)
        FROM core.usuario_history AS history
        INNER JOIN core.usuario AS current_record ON current_record.usuario_id = history.usuario_id
        WHERE current_record.environment_name = N'LOCAL_SHADOW'
          AND current_record.source_key = N'INTEGER:77') <> 2
    OR EXISTS (SELECT 1 FROM core.usuario_history WHERE execution_id = @tie_middle)
    THROW 51810, N'O desempate total de Usuários não preservou o vencedor determinístico.', 1;

-- O wrapper comum+tipado é uma única unidade: um rollback para savepoint após a publicação
-- genérica remove também current/history/autorização tipados e permite reaplicação limpa.
DECLARE @atomic_execution UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000716';
EXEC #run_usuario_execution
    @execution_id = @atomic_execution,
    @partition_start_utc = '2026-01-01T13:00:00',
    @partition_end_exclusive_utc = '2026-01-01T14:00:00',
    @idempotency_key = N'users-atomic-rollback',
    @source_key_1 = N'INTEGER:88', @wire_type_1 = N'INTEGER',
    @name_presence_1 = N'VALUE', @usuario_name_1 = N'Atômico sintético',
    @publish = 0;
SAVE TRANSACTION usuario_atomic_publish;
EXEC core.usp_apply_reconcile_publish_usuarios
    @atomic_execution, N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint;
IF NOT EXISTS (SELECT 1 FROM ctl.execution_publication_event
               WHERE execution_id = @atomic_execution)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_candidate_application
                   WHERE execution_id = @atomic_execution)
    OR NOT EXISTS (SELECT 1 FROM recon.usuario_candidate_application
                   WHERE execution_id = @atomic_execution)
    OR NOT EXISTS (SELECT 1 FROM core.usuario_history
                   WHERE execution_id = @atomic_execution)
    THROW 51811, N'O wrapper não materializou a unidade genérica+tipada.', 1;
ROLLBACK TRANSACTION usuario_atomic_publish;
IF NOT EXISTS (SELECT 1 FROM ctl.execution_attempt
               WHERE execution_id = @atomic_execution AND current_state = N'PROMOTED')
    OR EXISTS (SELECT 1 FROM ctl.execution_publication_event
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM recon.execution_candidate_application
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM recon.execution_reconciliation_result
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM recon.usuario_apply_authorization
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM recon.usuario_candidate_application
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM recon.usuario_reconciliation_result
               WHERE execution_id = @atomic_execution)
    OR EXISTS (SELECT 1 FROM core.entity_record_state
               WHERE environment_name = N'LOCAL_SHADOW' AND source_key = N'INTEGER:88')
    OR EXISTS (SELECT 1 FROM core.usuario
               WHERE environment_name = N'LOCAL_SHADOW' AND source_key = N'INTEGER:88')
    OR EXISTS (SELECT 1 FROM core.usuario_history
               WHERE execution_id = @atomic_execution)
    THROW 51812, N'O rollback parcial deixou publicação ou estado aplicado.', 1;
EXEC core.usp_apply_reconcile_publish_usuarios
    @atomic_execution, N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint;
IF NOT EXISTS (SELECT 1 FROM ctl.execution_attempt
               WHERE execution_id = @atomic_execution AND current_state = N'PUBLISHED')
    OR NOT EXISTS (SELECT 1 FROM core.usuario
                   WHERE environment_name = N'LOCAL_SHADOW' AND source_key = N'INTEGER:88')
    THROW 51813, N'A reaplicação após rollback atômico não foi publicada.', 1;
GO

-- Conflito tipado: o envelope técnico é idêntico, mas nomes divergentes bloqueiam a vertical.
DECLARE @conflict_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000710';
DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @identity_row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @identity_presence_hash CHAR(64) = REPLICATE('e', 64);
EXEC ctl.usp_control_plane_start_execution
    @conflict_execution, '00000000-0000-0000-0000-000000000701',
    N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE', N'SYNTHETIC_USERS_TENANT', N'usuarios',
    N'BACKFILL', '2026-01-01T07:00:00', '2026-01-01T08:00:00',
    N'GRAPHQL_RESTART_FROM_BEGINNING', N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint, N'users-conflict', NULL, 3600, @now;
EXEC ctl.usp_control_plane_record_page
    @conflict_execution, 1, 1, 20, 2, 1, 256, 0, @now, N'GRAPHQL_PAGE_INFO';
EXEC stg.usp_stage_usuario_record
    @conflict_execution, 1, 1, N'INTEGER:99', N'INTEGER', N'VALUE',
    N'Conflito sintético A', N'VALID', NULL, @now;
EXEC stg.usp_stage_usuario_record
    @conflict_execution, 1, 2, N'INTEGER:99', N'INTEGER', N'VALUE',
    N'Conflito sintético B', N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @conflict_execution, N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint;
IF NOT EXISTS (
    SELECT 1 FROM ctl.usuario_promotion_result
    WHERE execution_id = @conflict_execution AND validation_state = N'BLOCKED'
      AND conflicting_root_keys = 1
) OR (SELECT COUNT_BIG(*) FROM recon.usuario_quarantine
      WHERE execution_id = @conflict_execution) <> 1
    THROW 51804, N'A divergência tipada não foi bloqueada e quarentenada.', 1;

-- Sem PASS tipado não existe avaliação comum persistível; ao envelhecer, o health falha fechado.
UPDATE ctl.execution_promotion_result
SET promoted_at_utc = DATEADD(MINUTE, -5, SYSUTCDATETIME())
WHERE execution_id = @conflict_execution;
DECLARE @health TABLE (
    health_status NVARCHAR(16), reason_code NVARCHAR(64),
    incomplete_data_quality_runs BIGINT, failed_data_quality_runs BIGINT,
    overdue_quarantine_rows BIGINT, stale_running_executions BIGINT,
    observed_at_utc DATETIME2(3)
);
INSERT INTO @health EXEC ctl.usp_observe_platform_health 60;
IF EXISTS (SELECT 1 FROM recon.execution_data_quality_evaluation
           WHERE execution_id = @conflict_execution)
    OR NOT EXISTS (SELECT 1 FROM @health
                   WHERE health_status = N'DOWN' AND reason_code = N'DQ_INCOMPLETE'
                     AND incomplete_data_quality_runs >= 1)
    THROW 51814, N'O health não falhou fechado para DQ tipada incompleta.', 1;

-- Bypass genérico sem sidecar também falha fechado na preparação vertical.
DECLARE @missing_sidecar_execution UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000711';
EXEC ctl.usp_control_plane_start_execution
    @missing_sidecar_execution, '00000000-0000-0000-0000-000000000701',
    N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE', N'SYNTHETIC_USERS_TENANT', N'usuarios',
    N'BACKFILL', '2026-01-01T08:00:00', '2026-01-01T09:00:00',
    N'GRAPHQL_RESTART_FROM_BEGINNING', N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint, N'users-missing-sidecar', NULL, 3600, @now;
EXEC ctl.usp_control_plane_record_page
    @missing_sidecar_execution, 1, 1, 20, 1, 1, 128, 0, @now, N'GRAPHQL_PAGE_INFO';
EXEC stg.usp_stage_record
    @missing_sidecar_execution, 1, 1, N'INTEGER:100',
    N'usuarios-identity-envelope-v1', @identity_row_hash,
    N'usuarios-identity-presence-v1', @identity_presence_hash,
    NULL, N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @missing_sidecar_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @missing_sidecar_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @missing_sidecar_execution, N'graphql-users-v1', @contract_fingerprint,
    N'users-shadow-v1', @configuration_fingerprint;
IF NOT EXISTS (
    SELECT 1 FROM ctl.usuario_promotion_result
    WHERE execution_id = @missing_sidecar_execution AND validation_state = N'BLOCKED'
      AND generic_candidate_rows = 1 AND typed_candidate_rows = 0
)
    THROW 51805, N'O candidate genérico sem sidecar não falhou fechado.', 1;

-- Lifecycle completo do staging de Usuários: orçamento tipado, plano/retry, archive/retry,
-- purge/retry e restore read-only/retry, preservando current/history.
DECLARE @lifecycle_initial UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000702';
DECLARE @lifecycle_small UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000703';
DECLARE @lifecycle_policy UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000720';
DECLARE @budget_plan UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000721';
DECLARE @lifecycle_plan UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000722';
DECLARE @lifecycle_restore UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000723';
DECLARE @typed_admission_plan UNIQUEIDENTIFIER =
    '00000000-0000-0000-0000-000000000724';
DECLARE @lifecycle_policy_version NVARCHAR(128) = N'synthetic-users-lifecycle-v1';
DECLARE @lifecycle_policy_fingerprint CHAR(64) = REPLICATE('4', 64);
DECLARE @data_owner_evidence CHAR(64) = REPLICATE('5', 64);
DECLARE @compliance_evidence CHAR(64) = REPLICATE('6', 64);
DECLARE @old_start DATETIME2(3) = DATEADD(DAY, -5, @now);
DECLARE @old_terminal_initial DATETIME2(3) = DATEADD(DAY, -3, @now);
DECLARE @old_terminal_small DATETIME2(3) = DATEADD(DAY, -2, @now);
DECLARE @current_rows_before_lifecycle BIGINT = (SELECT COUNT_BIG(*) FROM core.usuario);
DECLARE @history_rows_before_lifecycle BIGINT = (SELECT COUNT_BIG(*) FROM core.usuario_history);

UPDATE ctl.execution_attempt
SET started_at_utc = @old_start,
    terminal_at_utc = CASE WHEN execution_id = @lifecycle_initial
                           THEN @old_terminal_initial ELSE @old_terminal_small END
WHERE execution_id IN (@lifecycle_initial, @lifecycle_small);
UPDATE ctl.execution_state_event
SET transitioned_at_utc = CASE
        WHEN transition_sequence IN (1, 2) THEN @old_start
        WHEN execution_id = @lifecycle_initial THEN @old_terminal_initial
        ELSE @old_terminal_small
    END
WHERE execution_id IN (@lifecycle_initial, @lifecycle_small);
UPDATE stg.execution_record
SET staged_at_utc = CASE WHEN execution_id = @lifecycle_initial
                         THEN @old_terminal_initial ELSE @old_terminal_small END
WHERE execution_id IN (@lifecycle_initial, @lifecycle_small);

EXEC ctl.usp_approve_staging_retention_policy
    @lifecycle_policy, N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE',
    N'SYNTHETIC_USERS_TENANT', N'usuarios', N'PUBLISHED',
    @lifecycle_policy_version, @lifecycle_policy_fingerprint, 1,
    @data_owner_evidence, N'data-owner', @compliance_evidence, N'compliance';

DECLARE @plan_result TABLE (
    plan_id UNIQUEIDENTIFIER, policy_id UNIQUEIDENTIFIER, policy_version NVARCHAR(128),
    policy_fingerprint CHAR(64), planned_at_utc DATETIME2(3), cutoff_at_utc DATETIME2(3),
    scan_after_terminal_at_utc DATETIME2(3), scan_after_execution_id UNIQUEIDENTIFIER,
    next_scan_after_terminal_at_utc DATETIME2(3), next_scan_after_execution_id UNIQUEIDENTIFIER,
    examined_executions BIGINT, scan_truncated BIT, eligible_executions BIGINT,
    held_executions BIGINT, lease_blocked_executions BIGINT, oversized_executions BIGINT,
    deferred_executions BIGINT, selected_executions BIGINT, stage_rows BIGINT,
    candidate_rows BIGINT, evidence_rows BIGINT, archive_bytes BIGINT,
    planned_content_root_version NVARCHAR(64), planned_content_root CHAR(64), exact_retry BIT
);

-- Mede o custo integral pelo planner real e escolhe um cap que aceita o custo genérico do item
-- mais antigo, mas o torna oversized somente quando a extensão tipada é considerada. Com
-- maximum_executions=1, o item menor posterior precisa atravessar o filtro e ocupar a vaga.
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @budget_plan, @lifecycle_policy, @lifecycle_policy_version,
    @lifecycle_policy_fingerprint, N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE',
    N'SYNTHETIC_USERS_TENANT', N'usuarios', NULL, NULL,
    2, 10, 100, 100, 100, 1000, 5000, 1000000;
IF NOT EXISTS (SELECT 1 FROM @plan_result
               WHERE eligible_executions = 2 AND oversized_executions = 0
                 AND selected_executions = 2 AND deferred_executions = 0)
    THROW 51815, N'A medição integral do budget tipado divergiu.', 1;

DECLARE @initial_total_budget BIGINT = (
    SELECT archive_bytes FROM ctl.staging_lifecycle_plan_item
    WHERE plan_id = @budget_plan AND execution_id = @lifecycle_initial
);
DECLARE @small_total_budget BIGINT = (
    SELECT archive_bytes FROM ctl.staging_lifecycle_plan_item
    WHERE plan_id = @budget_plan AND execution_id = @lifecycle_small
);
DECLARE @initial_extension_budget BIGINT = (
    SELECT additional_archive_bytes
    FROM recon.ufn_staging_lifecycle_extension_archive_budget(@lifecycle_initial, 100)
);
DECLARE @small_extension_budget BIGINT = (
    SELECT additional_archive_bytes
    FROM recon.ufn_staging_lifecycle_extension_archive_budget(@lifecycle_small, 100)
);
IF @initial_total_budget IS NULL OR @small_total_budget IS NULL
    OR @initial_extension_budget IS NULL OR @small_extension_budget IS NULL
    OR @initial_extension_budget <= @small_extension_budget
    OR @initial_total_budget <= @small_total_budget
    THROW 51816, N'A fixture não distinguiu os custos integrais do lifecycle.', 1;

DECLARE @initial_generic_budget BIGINT = @initial_total_budget - @initial_extension_budget;
DECLARE @typed_budget_cap BIGINT = CASE
    WHEN @initial_generic_budget > @small_total_budget
        THEN @initial_generic_budget
    ELSE @small_total_budget
END;
IF @typed_budget_cap >= @initial_total_budget
    THROW 51816, N'A fixture não isolou o custo incremental tipado.', 1;

DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @typed_admission_plan, @lifecycle_policy, @lifecycle_policy_version,
    @lifecycle_policy_fingerprint, N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE',
    N'SYNTHETIC_USERS_TENANT', N'usuarios', NULL, NULL,
    1, 10, 100, 100, 100, 1000, 5000, @typed_budget_cap;
IF NOT EXISTS (SELECT 1 FROM @plan_result
               WHERE eligible_executions = 1 AND oversized_executions = 1
                 AND selected_executions = 1 AND deferred_executions = 0
                 AND stage_rows = 1 AND candidate_rows = 1
                 AND archive_bytes = @small_total_budget AND exact_retry = 0)
    OR EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
               WHERE plan_id = @typed_admission_plan
                 AND execution_id = @lifecycle_initial)
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @typed_admission_plan
                     AND execution_id = @lifecycle_small
                     AND archive_bytes = @small_total_budget)
    THROW 51816, N'O item tipado oversized bloqueou o item menor após o TOP.', 1;

DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy, @lifecycle_policy_version,
    @lifecycle_policy_fingerprint, N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE',
    N'SYNTHETIC_USERS_TENANT', N'usuarios', NULL, NULL,
    1, 10, 100, 100, 100, 1000, 5000, 1000000;
IF NOT EXISTS (SELECT 1 FROM @plan_result
               WHERE eligible_executions = 2 AND selected_executions = 1
                 AND deferred_executions = 1 AND stage_rows = 2
                 AND candidate_rows = 2 AND exact_retry = 0)
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @lifecycle_plan
                     AND execution_id = @lifecycle_initial)
    THROW 51817, N'O plano de lifecycle não selecionou a execução publicada esperada.', 1;
DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy, @lifecycle_policy_version,
    @lifecycle_policy_fingerprint, N'LOCAL_SHADOW', N'SYNTHETIC_USERS_SOURCE',
    N'SYNTHETIC_USERS_TENANT', N'usuarios', NULL, NULL,
    1, 10, 100, 100, 100, 1000, 5000, 1000000;
IF NOT EXISTS (SELECT 1 FROM @plan_result WHERE exact_retry = 1)
    THROW 51818, N'O retry exato do plano de Usuários não foi reconhecido.', 1;

DECLARE @archive_result TABLE (
    plan_id UNIQUEIDENTIFIER, archive_fingerprint_version NVARCHAR(128),
    archive_fingerprint CHAR(64), archived_executions BIGINT,
    archived_stage_rows BIGINT, archived_candidate_rows BIGINT, archived_bytes BIGINT,
    archived_quarantine_rows BIGINT, archived_application_rows BIGINT,
    archived_reconciliation_rows BIGINT, archived_state_event_rows BIGINT,
    archived_page_audit_rows BIGINT, archived_count_rows BIGINT,
    archived_publication_event_rows BIGINT, archived_promotion_result_rows BIGINT,
    content_root_version NVARCHAR(64), content_root CHAR(64),
    archived_at_utc DATETIME2(3), exact_retry BIT
);
INSERT INTO @archive_result
EXEC stg.usp_archive_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy_version, @lifecycle_policy_fingerprint;
IF NOT EXISTS (SELECT 1 FROM @archive_result
               WHERE archived_executions = 1 AND archived_stage_rows = 2
                 AND archived_candidate_rows = 2 AND exact_retry = 0)
    OR (SELECT COUNT_BIG(*) FROM recon.usuario_stage_archive
        WHERE plan_id = @lifecycle_plan AND execution_id = @lifecycle_initial) <> 2
    THROW 51819, N'O archive não preservou o sidecar tipado de Usuários.', 1;
DECLARE @archived_extension_budget BIGINT = (
    SELECT COALESCE(
               SUM(CONVERT(BIGINT, 86 + DATALENGTH(canonical.canonical_row))),
               CONVERT(BIGINT, 0)
           )
    FROM recon.usuario_stage_archive AS archived
    CROSS APPLY (SELECT (SELECT archived.plan_id, archived.stage_record_id,
                                archived.execution_id, archived.source_key_wire_type,
                                archived.name_presence, archived.attribute_fingerprint_version,
                                archived.attribute_hash, archived.presence_fingerprint_version,
                                archived.name_presence_hash, archived.discarded_name_bytes
                         FOR JSON PATH, WITHOUT_ARRAY_WRAPPER, INCLUDE_NULL_VALUES)
                        AS canonical_row) AS canonical
    WHERE archived.plan_id = @lifecycle_plan
      AND archived.execution_id = @lifecycle_initial
);
IF @archived_extension_budget <> @initial_extension_budget
    THROW 51819, N'O budget tipado divergiu da representação efetivamente arquivada.', 1;
DECLARE @archive_version NVARCHAR(128) =
    (SELECT TOP (1) archive_fingerprint_version FROM @archive_result);
DECLARE @archive_fingerprint CHAR(64) =
    (SELECT TOP (1) archive_fingerprint FROM @archive_result);
DELETE FROM @archive_result;
INSERT INTO @archive_result
EXEC stg.usp_archive_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy_version, @lifecycle_policy_fingerprint;
IF NOT EXISTS (SELECT 1 FROM @archive_result WHERE exact_retry = 1)
    THROW 51820, N'O retry exato do archive tipado não foi reconhecido.', 1;

DECLARE @purge_result TABLE (
    plan_id UNIQUEIDENTIFIER, purged_executions BIGINT, purged_stage_rows BIGINT,
    purged_candidate_rows BIGINT, purged_at_utc DATETIME2(3), exact_retry BIT
);
INSERT INTO @purge_result
EXEC stg.usp_purge_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy_version, @lifecycle_policy_fingerprint,
    @archive_version, @archive_fingerprint;
IF NOT EXISTS (SELECT 1 FROM @purge_result
               WHERE purged_executions = 1 AND purged_stage_rows = 2
                 AND purged_candidate_rows = 2 AND exact_retry = 0)
    OR EXISTS (SELECT 1 FROM stg.execution_record
               WHERE execution_id = @lifecycle_initial)
    OR EXISTS (SELECT 1 FROM stg.usuario_record
               WHERE execution_id = @lifecycle_initial)
    OR NOT EXISTS (SELECT 1 FROM recon.usuario_stage_disposal_evidence
                   WHERE plan_id = @lifecycle_plan
                     AND execution_id = @lifecycle_initial AND typed_rows = 2)
    OR (SELECT COUNT_BIG(*) FROM core.usuario) <> @current_rows_before_lifecycle
    OR (SELECT COUNT_BIG(*) FROM core.usuario_history) <> @history_rows_before_lifecycle
    THROW 51821, N'O purge tipado removeu evidência durável ou current/history.', 1;
DELETE FROM @purge_result;
INSERT INTO @purge_result
EXEC stg.usp_purge_staging_lifecycle
    @lifecycle_plan, @lifecycle_policy_version, @lifecycle_policy_fingerprint,
    @archive_version, @archive_fingerprint;
IF NOT EXISTS (SELECT 1 FROM @purge_result WHERE exact_retry = 1)
    THROW 51822, N'O retry exato do purge tipado não foi reconhecido.', 1;

DECLARE @restore_result TABLE (
    restore_id UNIQUEIDENTIFIER, plan_id UNIQUEIDENTIFIER, execution_id UNIQUEIDENTIFIER,
    restored_stage_rows BIGINT, restored_candidate_rows BIGINT,
    restored_at_utc DATETIME2(3), read_only BIT, exact_retry BIT
);
INSERT INTO @restore_result
EXEC recon.usp_restore_staging_archive
    @lifecycle_restore, @lifecycle_plan, @lifecycle_initial,
    @archive_version, @archive_fingerprint, 100, 100, N'RESTORE_TEST';
IF NOT EXISTS (SELECT 1 FROM @restore_result
               WHERE restored_stage_rows = 2 AND restored_candidate_rows = 2
                 AND read_only = 1 AND exact_retry = 0)
    OR (SELECT COUNT_BIG(*) FROM recon.usuario_stage_restore
        WHERE restore_id = @lifecycle_restore
          AND execution_id = @lifecycle_initial) <> 2
    OR EXISTS (SELECT 1 FROM stg.execution_record
               WHERE execution_id = @lifecycle_initial)
    OR EXISTS (SELECT 1 FROM stg.usuario_record
               WHERE execution_id = @lifecycle_initial)
    THROW 51823, N'O restore tipado não permaneceu read-only e íntegro.', 1;
DELETE FROM @restore_result;
INSERT INTO @restore_result
EXEC recon.usp_restore_staging_archive
    @lifecycle_restore, @lifecycle_plan, @lifecycle_initial,
    @archive_version, @archive_fingerprint, 100, 100, N'RESTORE_TEST';
IF NOT EXISTS (SELECT 1 FROM @restore_result WHERE read_only = 1 AND exact_retry = 1)
    THROW 51824, N'O retry exato do restore tipado não foi reconhecido.', 1;

IF EXISTS (
    SELECT environment_name, source_instance, tenant_scope, entity_name, source_key
    FROM core.usuario
    GROUP BY environment_name, source_instance, tenant_scope, entity_name, source_key
    HAVING COUNT_BIG(*) > 1
) OR EXISTS (
    SELECT execution_id, usuario_id FROM core.usuario_history
    GROUP BY execution_id, usuario_id HAVING COUNT_BIG(*) > 1
)
    THROW 51806, N'As invariantes de current/history foram violadas.', 1;

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51807, N'O exercício consumiu o escopo rollback-only.', 1;

-- Último negative gate: o erro deliberado pode tornar a transação uncommittable, por isso ele
-- encerra o exercício e comprova que um retry do mesmo ordinal nunca troca o conteúdo tipado.
DECLARE @divergent_retry_error INT = NULL;
DECLARE @divergent_retry_observed_at_utc DATETIME2(3) = SYSUTCDATETIME();
BEGIN TRY
    EXEC stg.usp_stage_usuario_record
        @lifecycle_small, 1, 1, N'INTEGER:42', N'INTEGER', N'VALUE',
        N'Conteúdo divergente recusado', N'VALID', NULL, @divergent_retry_observed_at_utc;
END TRY
BEGIN CATCH
    SET @divergent_retry_error = ERROR_NUMBER();
END CATCH;
IF @divergent_retry_error <> 51702
BEGIN
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW 51825, N'O retry divergente do stage tipado não falhou com o gate esperado.', 1;
END;

IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 51808, N'O rollback da vertical de Usuários não encerrou a transação.', 1;

PRINT N'Usuários/current/history validados e revertidos com sucesso.';

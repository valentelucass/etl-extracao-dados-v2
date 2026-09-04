-- Framework comum de observabilidade, integridade e Data Quality de V2-023.
-- Nenhuma policy produtiva é semeada: ativação exige evidência/owners fora desta migration.

SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

CREATE TABLE ctl.data_quality_policy (
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expected_checks SMALLINT NOT NULL,
    quarantine_sla_seconds BIGINT NOT NULL,
    threshold_owner_role NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    quarantine_sla_owner_role NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    retention_policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    retention_owner_role NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    effective_from_utc DATETIME2(3) NOT NULL,
    recorded_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_ctl_data_quality_policy
        PRIMARY KEY CLUSTERED (policy_version, policy_fingerprint),
    CONSTRAINT CK_ctl_data_quality_policy_fingerprint CHECK (
        policy_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND scope_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
    ),
    CONSTRAINT CK_ctl_data_quality_policy_values CHECK (
        expected_checks = 4
        AND quarantine_sla_seconds BETWEEN 1 AND 31536000
        AND policy_state IN (N'RATIFIED', N'REVOKED')
        AND effective_from_utc <= recorded_at_utc
        AND LEN(policy_version) BETWEEN 1 AND 128
        AND LEN(retention_policy_version) BETWEEN 1 AND 128
        AND LEFT(policy_version, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Za-z0-9]'
        AND policy_version COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^-A-Za-z0-9._]%'
        AND LEFT(retention_policy_version, 1) COLLATE Latin1_General_100_BIN2
            LIKE '[A-Za-z0-9]'
        AND retention_policy_version COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^-A-Za-z0-9._]%'
        AND DATALENGTH(policy_version) = DATALENGTH(LTRIM(RTRIM(policy_version)))
        AND DATALENGTH(retention_policy_version)
            = DATALENGTH(LTRIM(RTRIM(retention_policy_version)))
    ),
    CONSTRAINT CK_ctl_data_quality_policy_owner_roles CHECK (
        LEFT(threshold_owner_role, 1) COLLATE Latin1_General_100_BIN2 LIKE '[a-z]'
        AND threshold_owner_role COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^-a-z0-9_]%'
        AND LEN(threshold_owner_role) BETWEEN 2 AND 64
        AND DATALENGTH(threshold_owner_role)
            = DATALENGTH(LTRIM(RTRIM(threshold_owner_role)))
        AND LEFT(quarantine_sla_owner_role, 1) COLLATE Latin1_General_100_BIN2 LIKE '[a-z]'
        AND quarantine_sla_owner_role COLLATE Latin1_General_100_BIN2
            NOT LIKE '%[^-a-z0-9_]%'
        AND LEN(quarantine_sla_owner_role) BETWEEN 2 AND 64
        AND DATALENGTH(quarantine_sla_owner_role)
            = DATALENGTH(LTRIM(RTRIM(quarantine_sla_owner_role)))
        AND LEFT(retention_owner_role, 1) COLLATE Latin1_General_100_BIN2 LIKE '[a-z]'
        AND retention_owner_role COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^-a-z0-9_]%'
        AND LEN(retention_owner_role) BETWEEN 2 AND 64
        AND DATALENGTH(retention_owner_role)
            = DATALENGTH(LTRIM(RTRIM(retention_owner_role)))
    )
);
GO

CREATE UNIQUE INDEX UX_ctl_data_quality_policy_scope_effective
    ON ctl.data_quality_policy (scope_fingerprint, effective_from_utc);
GO

CREATE TABLE ctl.data_quality_check_policy (
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    check_ordinal SMALLINT NOT NULL,
    check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    maximum_failed_rows BIGINT NOT NULL,
    maximum_failure_basis_points INT NOT NULL,
    threshold_owner_role NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_ctl_data_quality_check_policy
        PRIMARY KEY CLUSTERED (policy_version, policy_fingerprint, check_ordinal),
    CONSTRAINT UQ_ctl_data_quality_check_policy_code
        UNIQUE (policy_version, policy_fingerprint, check_code),
    CONSTRAINT FK_ctl_data_quality_check_policy_policy FOREIGN KEY (
        policy_version, policy_fingerprint
    ) REFERENCES ctl.data_quality_policy (policy_version, policy_fingerprint),
    CONSTRAINT CK_ctl_data_quality_check_policy_values CHECK (
        check_ordinal BETWEEN 1 AND 4
        AND check_code IN (
            N'COUNT_EQUATION', N'PAGE_TERMINALITY',
            N'PROMOTION_RECONCILIATION', N'QUARANTINE_SLA'
        )
        AND DATALENGTH(check_code) = DATALENGTH(LTRIM(RTRIM(check_code)))
        AND maximum_failed_rows >= 0
        AND maximum_failure_basis_points BETWEEN 0 AND 10000
        AND (
            check_code = N'QUARANTINE_SLA'
            OR (maximum_failed_rows = 0 AND maximum_failure_basis_points = 0)
        )
    ),
    CONSTRAINT CK_ctl_data_quality_check_policy_owner CHECK (
        LEFT(threshold_owner_role, 1) COLLATE Latin1_General_100_BIN2 LIKE '[a-z]'
        AND threshold_owner_role COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^-a-z0-9_]%'
        AND LEN(threshold_owner_role) BETWEEN 2 AND 64
        AND DATALENGTH(threshold_owner_role)
            = DATALENGTH(LTRIM(RTRIM(threshold_owner_role)))
    )
);
GO

CREATE OR ALTER TRIGGER ctl.trg_data_quality_policy_immutable
ON ctl.data_quality_policy
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM deleted AS prior
        LEFT JOIN inserted AS current_row
            ON current_row.policy_version = prior.policy_version
           AND current_row.policy_fingerprint = prior.policy_fingerprint
        WHERE current_row.policy_version IS NULL
    )
        THROW 51617, N'Policy de Data Quality não pode ser removida ou trocar identidade.', 1;

    IF EXISTS (
        SELECT 1
        FROM deleted AS prior
        INNER JOIN inserted AS current_row
            ON current_row.policy_version = prior.policy_version
           AND current_row.policy_fingerprint = prior.policy_fingerprint
        WHERE prior.scope_fingerprint <> current_row.scope_fingerprint
           OR prior.expected_checks <> current_row.expected_checks
           OR prior.quarantine_sla_seconds <> current_row.quarantine_sla_seconds
           OR prior.threshold_owner_role <> current_row.threshold_owner_role
           OR prior.quarantine_sla_owner_role <> current_row.quarantine_sla_owner_role
           OR prior.retention_policy_version <> current_row.retention_policy_version
           OR prior.retention_owner_role <> current_row.retention_owner_role
           OR prior.effective_from_utc <> current_row.effective_from_utc
           OR prior.recorded_at_utc <> current_row.recorded_at_utc
           OR prior.policy_state <> N'RATIFIED'
           OR current_row.policy_state <> N'REVOKED'
    )
        THROW 51617, N'Policy de Data Quality é imutável; somente revogação unidirecional é aceita.', 1;
END;
GO

CREATE OR ALTER TRIGGER ctl.trg_data_quality_check_policy_immutable
ON ctl.data_quality_check_policy
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51618, N'Checks ratificados de Data Quality são imutáveis.', 1;
END;
GO

CREATE TABLE recon.execution_data_quality_evaluation (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    scope_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evaluation_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expected_checks SMALLINT NOT NULL,
    completed_checks SMALLINT NOT NULL,
    passed_checks SMALLINT NOT NULL,
    failed_checks SMALLINT NOT NULL,
    evaluated_rows BIGINT NOT NULL,
    failed_rows BIGINT NOT NULL,
    evaluation_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    candidate_rows BIGINT NOT NULL,
    promotion_recorded_at_utc DATETIME2(3) NOT NULL,
    evaluated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_execution_data_quality_evaluation
        PRIMARY KEY CLUSTERED (execution_id),
    CONSTRAINT FK_recon_execution_data_quality_evaluation_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT FK_recon_execution_data_quality_evaluation_policy FOREIGN KEY (
        policy_version, policy_fingerprint
    ) REFERENCES ctl.data_quality_policy (policy_version, policy_fingerprint),
    CONSTRAINT CK_recon_execution_data_quality_evaluation_fingerprint CHECK (
        evaluation_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
        AND scope_fingerprint COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
    ),
    CONSTRAINT CK_recon_execution_data_quality_evaluation_counts CHECK (
        expected_checks = 4
        AND completed_checks = expected_checks
        AND passed_checks + failed_checks = completed_checks
        AND evaluated_rows >= 0
        AND failed_rows BETWEEN 0 AND evaluated_rows
        AND candidate_rows >= 0
        AND (
            (evaluation_state = N'PASSED' AND passed_checks = expected_checks AND failed_checks = 0)
            OR (evaluation_state = N'FAILED' AND failed_checks > 0)
        )
        AND evaluated_at_utc >= promotion_recorded_at_utc
    )
);
GO

CREATE INDEX IX_recon_execution_data_quality_evaluation_state
    ON recon.execution_data_quality_evaluation (evaluation_state, evaluated_at_utc, execution_id);
GO

CREATE INDEX IX_ctl_execution_attempt_health_state_started
    ON ctl.execution_attempt (current_state, started_at_utc, execution_id)
    INCLUDE (partition_id);
GO

CREATE INDEX IX_ctl_execution_promotion_result_health_promoted
    ON ctl.execution_promotion_result (promoted_at_utc, execution_id);
GO

CREATE TABLE recon.execution_data_quality_check_result (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    check_ordinal SMALLINT NOT NULL,
    check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evaluated_rows BIGINT NOT NULL,
    failed_rows BIGINT NOT NULL,
    failure_basis_points INT NOT NULL,
    check_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    evaluated_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_execution_data_quality_check_result
        PRIMARY KEY CLUSTERED (execution_id, check_ordinal),
    CONSTRAINT UQ_recon_execution_data_quality_check_result_code
        UNIQUE (execution_id, check_code),
    CONSTRAINT FK_recon_execution_data_quality_check_result_evaluation
        FOREIGN KEY (execution_id)
        REFERENCES recon.execution_data_quality_evaluation (execution_id),
    CONSTRAINT CK_recon_execution_data_quality_check_result_values CHECK (
        check_ordinal BETWEEN 1 AND 4
        AND (
            (check_ordinal = 1 AND check_code = N'COUNT_EQUATION')
            OR (check_ordinal = 2 AND check_code = N'PAGE_TERMINALITY')
            OR (check_ordinal = 3 AND check_code = N'PROMOTION_RECONCILIATION')
            OR (check_ordinal = 4 AND check_code = N'QUARANTINE_SLA')
        )
        AND evaluated_rows >= 0
        AND failed_rows BETWEEN 0 AND evaluated_rows
        AND failure_basis_points BETWEEN 0 AND 10000
        AND check_state IN (N'PASSED', N'FAILED')
        AND reason_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(reason_code) BETWEEN 2 AND 64
    )
);
GO

CREATE TABLE recon.execution_metric_snapshot (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    metric_sequence INT NOT NULL,
    duration_milliseconds BIGINT NOT NULL,
    pages BIGINT NOT NULL,
    response_bytes BIGINT NOT NULL,
    physical_rows BIGINT NOT NULL,
    distinct_root_keys BIGINT NOT NULL,
    duplicate_rows BIGINT NOT NULL,
    valid_rows BIGINT NOT NULL,
    quarantined_root_keys BIGINT NOT NULL,
    unidentified_quarantine_rows BIGINT NOT NULL,
    quarantined_stage_rows BIGINT NOT NULL,
    candidate_rows BIGINT NOT NULL,
    inserted_rows BIGINT NOT NULL,
    updated_rows BIGINT NOT NULL,
    reactivated_rows BIGINT NOT NULL,
    noop_rows BIGINT NOT NULL,
    stale_noop_rows BIGINT NOT NULL,
    retry_attempts BIGINT NOT NULL,
    rate_limit_responses BIGINT NOT NULL,
    source_lag_milliseconds BIGINT NOT NULL,
    watermark_utc DATETIME2(3) NULL,
    captured_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_execution_metric_snapshot
        PRIMARY KEY CLUSTERED (execution_id, metric_sequence),
    CONSTRAINT FK_recon_execution_metric_snapshot_execution
        FOREIGN KEY (execution_id) REFERENCES ctl.execution_attempt (execution_id),
    CONSTRAINT CK_recon_execution_metric_snapshot_values CHECK (
        metric_sequence BETWEEN 1 AND 4096
        AND duration_milliseconds >= 0
        AND pages >= 0
        AND response_bytes >= 0
        AND physical_rows >= 0
        AND distinct_root_keys >= 0
        AND duplicate_rows >= 0
        AND valid_rows >= 0
        AND quarantined_root_keys >= 0
        AND unidentified_quarantine_rows >= 0
        AND quarantined_stage_rows >= 0
        AND candidate_rows >= 0
        AND inserted_rows >= 0
        AND updated_rows >= 0
        AND reactivated_rows >= 0
        AND noop_rows >= 0
        AND stale_noop_rows >= 0
        AND retry_attempts >= 0
        AND rate_limit_responses BETWEEN 0 AND retry_attempts
        AND source_lag_milliseconds >= 0
        AND physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows
        AND distinct_root_keys = valid_rows + quarantined_root_keys
        AND quarantined_stage_rows >= quarantined_root_keys + unidentified_quarantine_rows
        AND candidate_rows = valid_rows
        AND candidate_rows = inserted_rows + updated_rows + reactivated_rows + noop_rows
        AND stale_noop_rows <= noop_rows
        AND (watermark_utc IS NULL OR watermark_utc <= captured_at_utc)
    )
);
GO

CREATE TABLE recon.observability_alert (
    correlation_reference CHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alert_sequence INT NOT NULL,
    severity NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    alert_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    owner_role NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    occurrence_count BIGINT NOT NULL,
    occurred_at_utc DATETIME2(3) NOT NULL,
    CONSTRAINT PK_recon_observability_alert
        PRIMARY KEY CLUSTERED (correlation_reference, alert_sequence),
    CONSTRAINT CK_recon_observability_alert_reference CHECK (
        correlation_reference COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^0-9a-f]%'
    ),
    CONSTRAINT CK_recon_observability_alert_values CHECK (
        alert_sequence BETWEEN 1 AND 4096
        AND severity IN (N'INFO', N'WARNING', N'CRITICAL')
        AND DATALENGTH(severity) = DATALENGTH(LTRIM(RTRIM(severity)))
        AND LEFT(alert_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'
        AND alert_code COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Z0-9_]%'
        AND LEN(alert_code) BETWEEN 2 AND 64
        AND occurrence_count > 0
        AND LEFT(owner_role, 1) COLLATE Latin1_General_100_BIN2 LIKE '[a-z]'
        AND owner_role COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^-a-z0-9_]%'
        AND LEN(owner_role) BETWEEN 2 AND 64
    )
);
GO

CREATE OR ALTER PROCEDURE recon.usp_evaluate_execution_data_quality
    @execution_id UNIQUEIDENTIFIER,
    @policy_version NVARCHAR(MAX),
    @policy_fingerprint NVARCHAR(MAX)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL
        OR NULLIF(LTRIM(RTRIM(@policy_version)), N'') IS NULL
        OR DATALENGTH(@policy_version) <> DATALENGTH(LTRIM(RTRIM(@policy_version)))
        OR DATALENGTH(@policy_version) > 256
        OR LEFT(@policy_version, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Za-z0-9]'
        OR @policy_version COLLATE Latin1_General_100_BIN2 LIKE '%[^-A-Za-z0-9._]%'
        OR @policy_fingerprint IS NULL
        OR DATALENGTH(@policy_fingerprint) <> 128
        OR @policy_fingerprint COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
        THROW 51601, N'A avaliação exige execução e policy canônicas.', 1;

    BEGIN TRANSACTION;

    DECLARE @database_now_utc DATETIME2(3);
    DECLARE @partition_id BIGINT;
    DECLARE @execution_state NVARCHAR(32);
    DECLARE @environment_name NVARCHAR(32);
    DECLARE @source_instance NVARCHAR(128);
    DECLARE @tenant_scope NVARCHAR(128);
    DECLARE @entity_name NVARCHAR(128);
    DECLARE @execution_mode NVARCHAR(16);

    SELECT
        @partition_id = attempt.partition_id,
        @execution_state = attempt.current_state,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt
    INNER JOIN ctl.execution_partition AS partition
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @partition_id IS NULL
        THROW 51602, N'A execução de Data Quality não existe.', 1;

    DECLARE @lock_payload NVARCHAR(2000) = CONCAT(
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name
    );
    DECLARE @lock_resource NVARCHAR(255) = CONCAT(
        N'V2_APPLY_', CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @lock_payload), 2)
    );
    DECLARE @application_lock_result INT;
    EXEC @application_lock_result = sys.sp_getapplock
        @Resource = @lock_resource,
        @LockMode = 'Exclusive',
        @LockOwner = 'Transaction',
        @LockTimeout = 10000,
        @DbPrincipal = 'public';
    IF @application_lock_result < 0
        THROW 51603, N'Não foi possível serializar a avaliação de Data Quality.', 1;

    -- Releitura sob o mesmo lock do apply evita um permit separado do candidate set.
    SELECT
        @execution_state = attempt.current_state,
        @partition_id = partition.partition_id,
        @environment_name = partition.environment_name,
        @source_instance = partition.source_instance,
        @tenant_scope = partition.tenant_scope,
        @entity_name = partition.entity_name,
        @execution_mode = partition.execution_mode
    FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.execution_partition AS partition WITH (UPDLOCK, HOLDLOCK)
        ON partition.partition_id = attempt.partition_id
    WHERE attempt.execution_id = @execution_id;

    IF @execution_state NOT IN (N'PROMOTED', N'PUBLISHED')
        THROW 51604, N'A execução não está promovida para Data Quality.', 1;

    -- Vigência e SLA usam o instante posterior à espera do fence compartilhado com o apply.
    SET @database_now_utc = SYSUTCDATETIME();

    DECLARE @scope_material NVARCHAR(2000) = CONCAT(
        N'dq-scope-v1|',
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(@entity_name), N':', @entity_name, N'|',
        DATALENGTH(@execution_mode), N':', @execution_mode
    );
    DECLARE @scope_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @scope_material)), 2
    ));
    DECLARE @expected_checks SMALLINT;
    DECLARE @quarantine_sla_seconds BIGINT;

    DECLARE @candidate_rows BIGINT;
    DECLARE @promotion_physical_rows BIGINT;
    DECLARE @promotion_distinct_rows BIGINT;
    DECLARE @promotion_duplicate_rows BIGINT;
    DECLARE @promotion_quarantined_roots BIGINT;
    DECLARE @promotion_unidentified BIGINT;
    DECLARE @promotion_quarantined_stage BIGINT;
    DECLARE @promotion_recorded_at_utc DATETIME2(3);

    SELECT
        @candidate_rows = promotion.candidate_rows,
        @promotion_physical_rows = promotion.physical_rows,
        @promotion_distinct_rows = promotion.distinct_root_keys,
        @promotion_duplicate_rows = promotion.duplicate_rows,
        @promotion_quarantined_roots = promotion.quarantined_root_keys,
        @promotion_unidentified = promotion.unidentified_quarantine_rows,
        @promotion_quarantined_stage = promotion.quarantined_stage_rows,
        @promotion_recorded_at_utc = promotion.promoted_at_utc
    FROM ctl.execution_promotion_result AS promotion WITH (UPDLOCK, HOLDLOCK)
    WHERE promotion.execution_id = @execution_id;

    IF @candidate_rows IS NULL
        THROW 51606, N'A evidência do candidate set está ausente.', 1;

    IF EXISTS (
        SELECT 1
        FROM recon.execution_data_quality_evaluation AS evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE evaluation.execution_id = @execution_id
          AND (
              evaluation.policy_version <> @policy_version COLLATE Latin1_General_100_BIN2
              OR evaluation.policy_fingerprint
                    <> @policy_fingerprint COLLATE Latin1_General_100_BIN2
              OR evaluation.scope_fingerprint <> @scope_fingerprint
              OR evaluation.candidate_rows <> @candidate_rows
              OR evaluation.promotion_recorded_at_utc <> @promotion_recorded_at_utc
              OR evaluation.completed_checks <> evaluation.expected_checks
              OR evaluation.completed_checks <> (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
                  WHERE result.execution_id = @execution_id
              )
          )
    )
        THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

    DECLARE @has_existing_evaluation BIT = CASE WHEN EXISTS (
        SELECT 1 FROM recon.execution_data_quality_evaluation WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ) THEN 1 ELSE 0 END;

    IF @execution_state = N'PUBLISHED' AND @has_existing_evaluation = 0
        THROW 51616, N'A avaliação retroativa após publicação foi recusada.', 1;

    IF @execution_state = N'PUBLISHED'
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM recon.execution_data_quality_evaluation AS evaluation WITH (HOLDLOCK)
            INNER JOIN ctl.execution_publication_event AS publication WITH (HOLDLOCK)
                ON publication.execution_id = evaluation.execution_id
            WHERE evaluation.execution_id = @execution_id
              AND evaluation.evaluated_at_utc <= publication.published_at_utc
        )
            THROW 51607, N'O retry de Data Quality diverge da evidência imutável.', 1;

        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @policy_material NVARCHAR(4000);
    DECLARE @policy_effective_from_utc DATETIME2(3);
    SELECT
        @expected_checks = policy.expected_checks,
        @quarantine_sla_seconds = policy.quarantine_sla_seconds,
        @policy_effective_from_utc = policy.effective_from_utc,
        @policy_material = CONCAT(
            N'dq-policy-v1|',
            DATALENGTH(policy.policy_version), N':', policy.policy_version, N'|',
            policy.scope_fingerprint, N'|', policy.expected_checks, N'|',
            policy.quarantine_sla_seconds, N'|',
            DATALENGTH(policy.threshold_owner_role), N':', policy.threshold_owner_role, N'|',
            DATALENGTH(policy.quarantine_sla_owner_role), N':',
                policy.quarantine_sla_owner_role, N'|',
            DATALENGTH(policy.retention_policy_version), N':',
                policy.retention_policy_version, N'|',
            DATALENGTH(policy.retention_owner_role), N':', policy.retention_owner_role, N'|',
            CONVERT(NVARCHAR(33), policy.effective_from_utc, 126), N'|',
            check_1.check_ordinal, N':', check_1.check_code, N':',
                check_1.maximum_failed_rows, N':', check_1.maximum_failure_basis_points, N':',
                DATALENGTH(check_1.threshold_owner_role), N':', check_1.threshold_owner_role, N'|',
            check_2.check_ordinal, N':', check_2.check_code, N':',
                check_2.maximum_failed_rows, N':', check_2.maximum_failure_basis_points, N':',
                DATALENGTH(check_2.threshold_owner_role), N':', check_2.threshold_owner_role, N'|',
            check_3.check_ordinal, N':', check_3.check_code, N':',
                check_3.maximum_failed_rows, N':', check_3.maximum_failure_basis_points, N':',
                DATALENGTH(check_3.threshold_owner_role), N':', check_3.threshold_owner_role, N'|',
            check_4.check_ordinal, N':', check_4.check_code, N':',
                check_4.maximum_failed_rows, N':', check_4.maximum_failure_basis_points, N':',
                DATALENGTH(check_4.threshold_owner_role), N':', check_4.threshold_owner_role
        )
    FROM ctl.data_quality_policy AS policy WITH (UPDLOCK, HOLDLOCK)
    INNER JOIN ctl.data_quality_check_policy AS check_1 WITH (UPDLOCK, HOLDLOCK)
        ON check_1.policy_version = policy.policy_version
       AND check_1.policy_fingerprint = policy.policy_fingerprint
       AND check_1.check_ordinal = 1 AND check_1.check_code = N'COUNT_EQUATION'
    INNER JOIN ctl.data_quality_check_policy AS check_2 WITH (UPDLOCK, HOLDLOCK)
        ON check_2.policy_version = policy.policy_version
       AND check_2.policy_fingerprint = policy.policy_fingerprint
       AND check_2.check_ordinal = 2 AND check_2.check_code = N'PAGE_TERMINALITY'
    INNER JOIN ctl.data_quality_check_policy AS check_3 WITH (UPDLOCK, HOLDLOCK)
        ON check_3.policy_version = policy.policy_version
       AND check_3.policy_fingerprint = policy.policy_fingerprint
       AND check_3.check_ordinal = 3 AND check_3.check_code = N'PROMOTION_RECONCILIATION'
    INNER JOIN ctl.data_quality_check_policy AS check_4 WITH (UPDLOCK, HOLDLOCK)
        ON check_4.policy_version = policy.policy_version
       AND check_4.policy_fingerprint = policy.policy_fingerprint
       AND check_4.check_ordinal = 4 AND check_4.check_code = N'QUARANTINE_SLA'
    WHERE policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
      AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
      AND policy.scope_fingerprint = @scope_fingerprint
      AND policy.policy_state = N'RATIFIED'
      AND policy.effective_from_utc <= @database_now_utc
      AND NOT EXISTS (
          SELECT 1
          FROM ctl.data_quality_policy AS newer WITH (UPDLOCK, HOLDLOCK)
          WHERE newer.scope_fingerprint = policy.scope_fingerprint
            AND newer.effective_from_utc <= @database_now_utc
            AND newer.effective_from_utc > policy.effective_from_utc
      );

    DECLARE @calculated_policy_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @policy_material)), 2
    ));
    IF @expected_checks <> 4
        OR @quarantine_sla_seconds IS NULL
        OR @policy_material IS NULL
        OR @calculated_policy_fingerprint <> @policy_fingerprint
        THROW 51605, N'A policy de Data Quality não está íntegra, vigente e ratificada.', 1;

    IF @has_existing_evaluation = 1
    BEGIN
        COMMIT TRANSACTION;
        SELECT
            execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
            expected_checks, completed_checks, passed_checks, failed_checks,
            evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
        FROM recon.execution_data_quality_evaluation
        WHERE execution_id = @execution_id;
        RETURN;
    END;

    DECLARE @count_rows BIGINT;
    DECLARE @count_physical BIGINT;
    DECLARE @count_distinct BIGINT;
    DECLARE @count_duplicate BIGINT;
    DECLARE @count_valid BIGINT;
    DECLARE @count_quarantined BIGINT;
    DECLARE @count_unidentified BIGINT;
    SELECT
        @count_rows = COUNT_BIG(*),
        @count_physical = MAX(physical_rows),
        @count_distinct = MAX(distinct_root_keys),
        @count_duplicate = MAX(duplicate_rows),
        @count_valid = MAX(valid_rows),
        @count_quarantined = MAX(quarantined_root_keys),
        @count_unidentified = MAX(unidentified_quarantine_rows)
    FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)
    WHERE execution_id = @execution_id
      AND count_phase = N'STAGING_KERNEL';

    DECLARE @stage_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_candidate_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM stg.execution_candidate WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );
    DECLARE @actual_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*) FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    );

    DECLARE @page_rows BIGINT;
    DECLARE @minimum_page INT;
    DECLARE @maximum_page INT;
    DECLARE @terminal_pages BIGINT;
    DECLARE @terminal_page INT;
    DECLARE @page_physical_rows BIGINT;
    DECLARE @non_terminal_empty_pages BIGINT;
    DECLARE @invalid_terminal_pages BIGINT;
    DECLARE @oversized_root_pages BIGINT;
    ;WITH ranked_page AS (
        SELECT
            page_number, page_attempt, requested_page_size, physical_rows, distinct_root_keys,
            response_bytes, terminal_empty_page, terminal_evidence_kind,
            ROW_NUMBER() OVER (PARTITION BY page_number ORDER BY page_attempt DESC) AS authority_rank
        FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    ), authoritative_page AS (
        SELECT page_number, requested_page_size, physical_rows, distinct_root_keys,
               terminal_empty_page, terminal_evidence_kind
        FROM ranked_page WHERE authority_rank = 1
    )
    SELECT
        @page_rows = COUNT_BIG(*),
        @minimum_page = MIN(page_number),
        @maximum_page = MAX(page_number),
        @terminal_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN 1 ELSE 0 END
        ), 0),
        @terminal_page = MAX(
            CASE WHEN terminal_evidence_kind <> N'NONE' THEN page_number END
        ),
        @page_physical_rows = COALESCE(SUM(physical_rows), 0),
        @non_terminal_empty_pages = COALESCE(SUM(
            CASE WHEN terminal_evidence_kind = N'NONE' AND physical_rows = 0
                 THEN 1 ELSE 0 END
        ), 0),
        @invalid_terminal_pages = COALESCE(SUM(
            CASE
                WHEN terminal_evidence_kind = N'DATA_EXPORT_EMPTY_PAGE'
                     AND (terminal_empty_page <> 1
                          OR physical_rows <> 0 OR distinct_root_keys <> 0)
                    THEN 1
                WHEN terminal_evidence_kind = N'GRAPHQL_PAGE_INFO'
                     AND (terminal_empty_page <> 0 OR physical_rows = 0)
                    THEN 1
                WHEN terminal_evidence_kind NOT IN (
                    N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'
                ) THEN 1
                ELSE 0
            END
        ), 0),
        @oversized_root_pages = COALESCE(SUM(
            CASE WHEN distinct_root_keys > requested_page_size THEN 1 ELSE 0 END
        ), 0)
    FROM authoritative_page;

    DECLARE @divergent_page_attempts BIGINT = (
        SELECT COUNT_BIG(*)
        FROM (
            SELECT page_number
            FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)
            WHERE execution_id = @execution_id
            GROUP BY page_number
            HAVING MIN(requested_page_size) <> MAX(requested_page_size)
                OR MIN(physical_rows) <> MAX(physical_rows)
                OR MIN(distinct_root_keys) <> MAX(distinct_root_keys)
                OR MIN(response_bytes) <> MAX(response_bytes)
                OR MIN(CONVERT(TINYINT, terminal_empty_page))
                    <> MAX(CONVERT(TINYINT, terminal_empty_page))
                OR MIN(terminal_evidence_kind) <> MAX(terminal_evidence_kind)
        ) AS divergent
    );

    DECLARE @overdue_quarantine_rows BIGINT = (
        SELECT COUNT_BIG(*)
        FROM recon.quarantine_record WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
          AND quarantined_at_utc < DATEADD(
              SECOND,
              -CONVERT(INT, CASE WHEN @quarantine_sla_seconds > 2147483647
                  THEN 2147483647 ELSE @quarantine_sla_seconds END),
              @database_now_utc
          )
    );

    DECLARE @measurements TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @measurements (
        check_ordinal, check_code, evaluated_rows, failed_rows, reason_code
    ) VALUES
    (
        1, N'COUNT_EQUATION', 1,
        CASE WHEN @count_rows = 1
              AND @count_physical = @count_distinct + @count_duplicate + @count_unidentified
              AND @count_distinct = @count_valid + @count_quarantined
             THEN 0 ELSE 1 END,
        N'COUNT_EQUATION_MISMATCH'
    ),
    (
        2, N'PAGE_TERMINALITY', 1,
        CASE WHEN @page_rows > 0
              AND @minimum_page = 1
              AND @page_rows = @maximum_page
              AND @terminal_pages = 1
              AND @terminal_page = @maximum_page
              AND @non_terminal_empty_pages = 0
              AND @invalid_terminal_pages = 0
              AND @oversized_root_pages = 0
              AND @divergent_page_attempts = 0
              AND @page_physical_rows = @stage_rows
             THEN 0 ELSE 1 END,
        N'PAGE_TERMINALITY_MISMATCH'
    ),
    (
        3, N'PROMOTION_RECONCILIATION', 1,
        CASE WHEN @count_rows = 1
              AND @promotion_physical_rows = @count_physical
              AND @promotion_distinct_rows = @count_distinct
              AND @promotion_duplicate_rows = @count_duplicate
              AND @candidate_rows = @count_valid
              AND @promotion_quarantined_roots = @count_quarantined
              AND @promotion_unidentified = @count_unidentified
              AND @promotion_physical_rows = @stage_rows
              AND @promotion_distinct_rows
                    = @candidate_rows + @promotion_quarantined_roots
              AND @promotion_physical_rows = @promotion_distinct_rows
                    + @promotion_duplicate_rows + @promotion_unidentified
              AND @candidate_rows = @actual_candidate_rows
              AND @actual_quarantine_rows = @promotion_quarantined_stage
             THEN 0 ELSE 1 END,
        N'PROMOTION_RECONCILIATION_MISMATCH'
    ),
    (
        4, N'QUARANTINE_SLA', @actual_quarantine_rows, @overdue_quarantine_rows,
        N'QUARANTINE_SLA_EXCEEDED'
    );

    DECLARE @results TABLE (
        check_ordinal SMALLINT NOT NULL PRIMARY KEY,
        check_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
        evaluated_rows BIGINT NOT NULL,
        failed_rows BIGINT NOT NULL,
        failure_basis_points INT NOT NULL,
        check_state NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
        reason_code NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL
    );

    INSERT INTO @results (
        check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code
    )
    SELECT
        measurement.check_ordinal,
        measurement.check_code,
        measurement.evaluated_rows,
        measurement.failed_rows,
        CASE WHEN measurement.evaluated_rows = 0 THEN 0 ELSE CONVERT(INT, CEILING(
            CONVERT(DECIMAL(38, 10), measurement.failed_rows) * 10000
                / CONVERT(DECIMAL(38, 10), measurement.evaluated_rows)
        )) END,
        CASE WHEN measurement.failed_rows <= policy.maximum_failed_rows
              AND (
                  (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                  OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                        <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                            * policy.maximum_failure_basis_points
              )
             THEN N'PASSED' ELSE N'FAILED' END,
        CASE WHEN measurement.failed_rows = 0
                  OR (measurement.failed_rows <= policy.maximum_failed_rows
                      AND (
                          (measurement.evaluated_rows = 0 AND measurement.failed_rows = 0)
                          OR CONVERT(DECIMAL(38, 0), measurement.failed_rows) * 10000
                                <= CONVERT(DECIMAL(38, 0), measurement.evaluated_rows)
                                    * policy.maximum_failure_basis_points
                      ))
             THEN N'CHECK_WITHIN_THRESHOLD'
             ELSE measurement.reason_code END
    FROM @measurements AS measurement
    INNER JOIN ctl.data_quality_check_policy AS policy WITH (UPDLOCK, HOLDLOCK)
        ON policy.policy_version = @policy_version COLLATE Latin1_General_100_BIN2
       AND policy.policy_fingerprint = @policy_fingerprint COLLATE Latin1_General_100_BIN2
       AND policy.check_ordinal = measurement.check_ordinal
       AND policy.check_code = measurement.check_code;

    IF (SELECT COUNT_BIG(*) FROM @results) <> @expected_checks
        THROW 51608, N'A execução parcial dos checks foi recusada.', 1;

    DECLARE @completed_checks SMALLINT = CONVERT(SMALLINT, (SELECT COUNT_BIG(*) FROM @results));
    DECLARE @passed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'PASSED'
    ));
    DECLARE @failed_checks SMALLINT = CONVERT(SMALLINT, (
        SELECT COUNT_BIG(*) FROM @results WHERE check_state = N'FAILED'
    ));
    DECLARE @evaluated_rows BIGINT = (SELECT SUM(evaluated_rows) FROM @results);
    DECLARE @failed_rows BIGINT = (SELECT SUM(failed_rows) FROM @results);
    DECLARE @evaluation_state NVARCHAR(16) =
        CASE WHEN @failed_checks = 0 THEN N'PASSED' ELSE N'FAILED' END;

    DECLARE @evaluation_material NVARCHAR(2000) = CONCAT(
        N'dq-evaluation-v1|', @policy_version, N'|', @policy_fingerprint, N'|',
        @scope_fingerprint, N'|',
        CONVERT(NVARCHAR(36), @execution_id), N'|', @candidate_rows, N'|',
        CONVERT(NVARCHAR(33), @promotion_recorded_at_utc, 126), N'|',
        @completed_checks, N'|', @passed_checks, N'|', @failed_checks, N'|',
        @evaluated_rows, N'|', @failed_rows, N'|', @evaluation_state, N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 1), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 2), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 3), N'|',
        (SELECT CONCAT(check_code, N':', evaluated_rows, N':', failed_rows, N':', check_state)
         FROM @results WHERE check_ordinal = 4)
    );
    DECLARE @evaluation_fingerprint CHAR(64) = LOWER(CONVERT(
        CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @evaluation_material)), 2
    ));

    INSERT INTO recon.execution_data_quality_evaluation (
        execution_id, policy_version, policy_fingerprint, scope_fingerprint,
        evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, candidate_rows,
        promotion_recorded_at_utc, evaluated_at_utc
    ) VALUES (
        @execution_id, @policy_version, @policy_fingerprint, @scope_fingerprint,
        @evaluation_fingerprint,
        @expected_checks, @completed_checks, @passed_checks, @failed_checks,
        @evaluated_rows, @failed_rows, @evaluation_state, @candidate_rows,
        @promotion_recorded_at_utc, @database_now_utc
    );

    INSERT INTO recon.execution_data_quality_check_result (
        execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, evaluated_at_utc
    )
    SELECT
        @execution_id, check_ordinal, check_code, evaluated_rows, failed_rows,
        failure_basis_points, check_state, reason_code, @database_now_utc
    FROM @results;

    IF @@ROWCOUNT <> @expected_checks
        THROW 51609, N'A persistência parcial dos checks foi recusada.', 1;

    COMMIT TRANSACTION;

    SELECT
        execution_id, policy_version, policy_fingerprint, evaluation_fingerprint,
        expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluation_state, evaluated_at_utc
    FROM recon.execution_data_quality_evaluation
    WHERE execution_id = @execution_id;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_observe_execution_data_quality
    @execution_id UNIQUEIDENTIFIER,
    @maximum_sanitized_samples INT
AS
BEGIN
    SET NOCOUNT ON;
    IF @execution_id IS NULL OR @maximum_sanitized_samples IS NULL
       OR @maximum_sanitized_samples NOT BETWEEN 1 AND 32
        THROW 51610, N'A observação exige execução e limite TOP(N) entre 1 e 32.', 1;

    SELECT
        evaluation_state, expected_checks, completed_checks, passed_checks, failed_checks,
        evaluated_rows, failed_rows, evaluated_at_utc
    FROM recon.execution_data_quality_evaluation
    WHERE execution_id = @execution_id;

    SELECT TOP (@maximum_sanitized_samples)
        check_ordinal, check_code, check_state, reason_code,
        evaluated_rows, failed_rows, failure_basis_points, evaluated_at_utc
    FROM recon.execution_data_quality_check_result
    WHERE execution_id = @execution_id
    ORDER BY check_ordinal;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_record_execution_metric
    @execution_id UNIQUEIDENTIFIER,
    @metric_sequence INT,
    @duration_milliseconds BIGINT,
    @pages BIGINT,
    @response_bytes BIGINT,
    @physical_rows BIGINT,
    @distinct_root_keys BIGINT,
    @duplicate_rows BIGINT,
    @valid_rows BIGINT,
    @quarantined_root_keys BIGINT,
    @unidentified_quarantine_rows BIGINT,
    @quarantined_stage_rows BIGINT,
    @candidate_rows BIGINT,
    @inserted_rows BIGINT,
    @updated_rows BIGINT,
    @reactivated_rows BIGINT,
    @noop_rows BIGINT,
    @stale_noop_rows BIGINT,
    @retry_attempts BIGINT,
    @rate_limit_responses BIGINT,
    @source_lag_milliseconds BIGINT,
    @watermark_utc DATETIME2(3),
    @captured_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @execution_id IS NULL OR @metric_sequence IS NULL
       OR @duration_milliseconds IS NULL OR @pages IS NULL OR @response_bytes IS NULL
       OR @physical_rows IS NULL OR @distinct_root_keys IS NULL OR @duplicate_rows IS NULL
       OR @valid_rows IS NULL OR @quarantined_root_keys IS NULL
       OR @unidentified_quarantine_rows IS NULL OR @quarantined_stage_rows IS NULL
       OR @candidate_rows IS NULL OR @inserted_rows IS NULL OR @updated_rows IS NULL
       OR @reactivated_rows IS NULL OR @noop_rows IS NULL OR @stale_noop_rows IS NULL
       OR @retry_attempts IS NULL OR @rate_limit_responses IS NULL
       OR @source_lag_milliseconds IS NULL OR @captured_at_utc IS NULL
        THROW 51619, N'A métrica exige o shape fixo completo.', 1;

    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1 FROM ctl.execution_attempt WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id
    )
        THROW 51611, N'A execução da métrica não existe.', 1;

    IF EXISTS (
        SELECT 1 FROM recon.execution_metric_snapshot WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id AND metric_sequence = @metric_sequence
          AND (
              duration_milliseconds <> @duration_milliseconds OR pages <> @pages
              OR response_bytes <> @response_bytes OR physical_rows <> @physical_rows
              OR distinct_root_keys <> @distinct_root_keys OR duplicate_rows <> @duplicate_rows
              OR valid_rows <> @valid_rows OR quarantined_root_keys <> @quarantined_root_keys
              OR unidentified_quarantine_rows <> @unidentified_quarantine_rows
              OR quarantined_stage_rows <> @quarantined_stage_rows
              OR candidate_rows <> @candidate_rows
              OR inserted_rows <> @inserted_rows OR updated_rows <> @updated_rows
              OR reactivated_rows <> @reactivated_rows OR noop_rows <> @noop_rows
              OR stale_noop_rows <> @stale_noop_rows
              OR retry_attempts <> @retry_attempts
              OR rate_limit_responses <> @rate_limit_responses
              OR source_lag_milliseconds <> @source_lag_milliseconds
              OR watermark_utc <> @watermark_utc
              OR (watermark_utc IS NULL AND @watermark_utc IS NOT NULL)
              OR (watermark_utc IS NOT NULL AND @watermark_utc IS NULL)
              OR captured_at_utc <> @captured_at_utc
          )
    )
        THROW 51612, N'O retry da métrica diverge da evidência persistida.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM recon.execution_metric_snapshot WITH (UPDLOCK, HOLDLOCK)
        WHERE execution_id = @execution_id AND metric_sequence = @metric_sequence
    )
        INSERT INTO recon.execution_metric_snapshot (
            execution_id, metric_sequence, duration_milliseconds, pages, response_bytes,
            physical_rows, distinct_root_keys, duplicate_rows, valid_rows,
            quarantined_root_keys, unidentified_quarantine_rows, quarantined_stage_rows,
            candidate_rows, inserted_rows, updated_rows, reactivated_rows, noop_rows,
            stale_noop_rows, retry_attempts, rate_limit_responses, source_lag_milliseconds,
            watermark_utc, captured_at_utc
        ) VALUES (
            @execution_id, @metric_sequence, @duration_milliseconds, @pages, @response_bytes,
            @physical_rows, @distinct_root_keys, @duplicate_rows, @valid_rows,
            @quarantined_root_keys, @unidentified_quarantine_rows, @quarantined_stage_rows,
            @candidate_rows, @inserted_rows, @updated_rows, @reactivated_rows, @noop_rows,
            @stale_noop_rows, @retry_attempts, @rate_limit_responses, @source_lag_milliseconds,
            @watermark_utc, @captured_at_utc
        );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE recon.usp_raise_observability_alert
    @correlation_reference NVARCHAR(MAX),
    @alert_sequence INT,
    @severity NVARCHAR(MAX),
    @alert_code NVARCHAR(MAX),
    @owner_role NVARCHAR(MAX),
    @occurrence_count BIGINT,
    @occurred_at_utc DATETIME2(3)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF @correlation_reference IS NULL
       OR DATALENGTH(@correlation_reference) <> 128
       OR @correlation_reference COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
       OR @alert_sequence IS NULL OR @alert_sequence NOT BETWEEN 1 AND 4096
       OR @severity IS NULL
       OR DATALENGTH(@severity) <> DATALENGTH(LTRIM(RTRIM(@severity)))
       OR @severity COLLATE Latin1_General_100_BIN2 NOT IN (N'INFO', N'WARNING', N'CRITICAL')
       OR @alert_code IS NULL
       OR DATALENGTH(@alert_code) <> DATALENGTH(LTRIM(RTRIM(@alert_code)))
       OR LEN(@alert_code) NOT BETWEEN 2 AND 64
       OR LEFT(@alert_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'
       OR @alert_code COLLATE Latin1_General_100_BIN2 LIKE '%[^A-Z0-9_]%'
       OR @owner_role IS NULL
       OR DATALENGTH(@owner_role) <> DATALENGTH(LTRIM(RTRIM(@owner_role)))
       OR LEN(@owner_role) NOT BETWEEN 2 AND 64
       OR LEFT(@owner_role, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[a-z]'
       OR @owner_role COLLATE Latin1_General_100_BIN2 LIKE '%[^-a-z0-9_]%'
       OR @occurrence_count IS NULL OR @occurrence_count <= 0
       OR @occurred_at_utc IS NULL
        THROW 51621, N'O alerta exige o shape canônico completo.', 1;

    BEGIN TRANSACTION;

    IF EXISTS (
        SELECT 1 FROM recon.observability_alert WITH (UPDLOCK, HOLDLOCK)
        WHERE correlation_reference = @correlation_reference
          AND alert_sequence = @alert_sequence
          AND (
              severity <> @severity COLLATE Latin1_General_100_BIN2
              OR alert_code <> @alert_code COLLATE Latin1_General_100_BIN2
              OR owner_role <> @owner_role COLLATE Latin1_General_100_BIN2
              OR occurrence_count <> @occurrence_count
              OR occurred_at_utc <> @occurred_at_utc
          )
    )
        THROW 51613, N'O retry do alerta diverge da evidência persistida.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM recon.observability_alert WITH (UPDLOCK, HOLDLOCK)
        WHERE correlation_reference = @correlation_reference
          AND alert_sequence = @alert_sequence
    )
        INSERT INTO recon.observability_alert (
            correlation_reference, alert_sequence, severity, alert_code,
            owner_role, occurrence_count, occurred_at_utc
        ) VALUES (
            @correlation_reference, @alert_sequence, @severity, @alert_code,
            @owner_role, @occurrence_count, @occurred_at_utc
        );

    COMMIT TRANSACTION;
END;
GO

CREATE OR ALTER PROCEDURE ctl.usp_observe_platform_health
    @maximum_running_age_seconds BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    IF @maximum_running_age_seconds IS NULL
       OR @maximum_running_age_seconds NOT BETWEEN 1 AND 2592000
        THROW 51614, N'O health exige limite entre 1 segundo e 30 dias.', 1;

    DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @running_cutoff_utc DATETIME2(3) = DATEADD(
        SECOND, -CONVERT(INT, @maximum_running_age_seconds), @database_now_utc
    );
    DECLARE @incomplete_data_quality_runs BIGINT = (
        SELECT COUNT_BIG(*)
        FROM ctl.execution_attempt AS attempt
        INNER JOIN ctl.execution_promotion_result AS promotion
            ON promotion.execution_id = attempt.execution_id
        WHERE attempt.current_state = N'PROMOTED'
          AND promotion.promoted_at_utc < @running_cutoff_utc
          AND NOT EXISTS (
              SELECT 1 FROM recon.execution_data_quality_evaluation AS evaluation
              WHERE evaluation.execution_id = attempt.execution_id
          )
    );
    DECLARE @failed_data_quality_runs BIGINT = (
        SELECT COUNT_BIG(*)
        FROM recon.execution_data_quality_evaluation AS evaluation
        INNER JOIN ctl.execution_attempt AS attempt
            ON attempt.execution_id = evaluation.execution_id
        LEFT JOIN ctl.execution_partition AS partition
            ON partition.partition_id = attempt.partition_id
        LEFT JOIN ctl.execution_promotion_result AS promotion
            ON promotion.execution_id = evaluation.execution_id
        LEFT JOIN ctl.data_quality_policy AS policy
            ON policy.policy_version = evaluation.policy_version
           AND policy.policy_fingerprint = evaluation.policy_fingerprint
        WHERE attempt.current_state = N'PROMOTED'
          AND (
              evaluation.evaluation_state <> N'PASSED'
              OR evaluation.completed_checks <> evaluation.expected_checks
              OR evaluation.passed_checks <> evaluation.expected_checks
              OR evaluation.failed_checks <> 0
              OR evaluation.expected_checks <> policy.expected_checks
              OR evaluation.completed_checks <> (
                  SELECT COUNT_BIG(*)
                  FROM recon.execution_data_quality_check_result AS result
                  WHERE result.execution_id = evaluation.execution_id
              )
              OR EXISTS (
                  SELECT 1
                  FROM recon.execution_data_quality_check_result AS result
                  WHERE result.execution_id = evaluation.execution_id
                    AND result.check_state <> N'PASSED'
              )
              OR EXISTS (
                  SELECT 1
                  FROM recon.execution_data_quality_check_result AS result
                  WHERE result.execution_id = evaluation.execution_id
                    AND NOT EXISTS (
                        SELECT 1
                        FROM ctl.data_quality_check_policy AS expected_check
                        WHERE expected_check.policy_version = evaluation.policy_version
                          AND expected_check.policy_fingerprint = evaluation.policy_fingerprint
                          AND expected_check.check_ordinal = result.check_ordinal
                          AND expected_check.check_code = result.check_code
                    )
              )
              OR EXISTS (
                  SELECT 1
                  FROM ctl.data_quality_check_policy AS expected_check
                  WHERE expected_check.policy_version = evaluation.policy_version
                    AND expected_check.policy_fingerprint = evaluation.policy_fingerprint
                    AND NOT EXISTS (
                        SELECT 1
                        FROM recon.execution_data_quality_check_result AS result
                        WHERE result.execution_id = evaluation.execution_id
                          AND result.check_ordinal = expected_check.check_ordinal
                          AND result.check_code = expected_check.check_code
                    )
              )
              OR policy.policy_version IS NULL
              OR policy.policy_state <> N'RATIFIED'
              OR policy.effective_from_utc > @database_now_utc
              OR evaluation.scope_fingerprint <> policy.scope_fingerprint
              OR partition.partition_id IS NULL
              OR policy.scope_fingerprint <> LOWER(CONVERT(
                  CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                      N'dq-scope-v1|',
                      DATALENGTH(partition.environment_name), N':', partition.environment_name,
                      N'|', DATALENGTH(partition.source_instance), N':',
                      partition.source_instance, N'|', DATALENGTH(partition.tenant_scope), N':',
                      partition.tenant_scope, N'|', DATALENGTH(partition.entity_name), N':',
                      partition.entity_name, N'|', DATALENGTH(partition.execution_mode), N':',
                      partition.execution_mode
                  ))), 2
              ))
              OR EXISTS (
                  SELECT 1
                  FROM ctl.data_quality_policy AS newer
                  WHERE newer.scope_fingerprint = policy.scope_fingerprint
                    AND newer.effective_from_utc <= @database_now_utc
                    AND newer.effective_from_utc > policy.effective_from_utc
              )
              OR promotion.execution_id IS NULL
              OR promotion.candidate_rows <> evaluation.candidate_rows
              OR promotion.promoted_at_utc <> evaluation.promotion_recorded_at_utc
          )
    );
    DECLARE @overdue_quarantine_rows BIGINT = (
        SELECT COALESCE(SUM(breach.overdue_rows), CONVERT(BIGINT, 0))
        FROM (
            SELECT
                evaluation.execution_id,
                SUM(CASE WHEN quarantine.quarantined_at_utc < DATEADD(
                        SECOND, -CONVERT(INT, policy.quarantine_sla_seconds),
                        @database_now_utc
                    ) THEN CONVERT(BIGINT, 1) ELSE CONVERT(BIGINT, 0) END) AS overdue_rows
            FROM recon.execution_data_quality_evaluation AS evaluation
            INNER JOIN ctl.execution_attempt AS attempt
                ON attempt.execution_id = evaluation.execution_id
               AND (
                   attempt.current_state = N'PROMOTED'
                   OR (
                       attempt.current_state = N'PUBLISHED'
                       AND EXISTS (
                           SELECT 1
                           FROM ctl.partition_publication_pointer AS pointer
                           WHERE pointer.partition_id = attempt.partition_id
                             AND pointer.published_execution_id = attempt.execution_id
                       )
                   )
               )
            INNER JOIN ctl.data_quality_policy AS policy
                ON policy.policy_version = evaluation.policy_version
               AND policy.policy_fingerprint = evaluation.policy_fingerprint
            INNER JOIN ctl.data_quality_check_policy AS sla_check
                ON sla_check.policy_version = policy.policy_version
               AND sla_check.policy_fingerprint = policy.policy_fingerprint
               AND sla_check.check_code = N'QUARANTINE_SLA'
            LEFT JOIN recon.quarantine_record AS quarantine
                ON quarantine.execution_id = evaluation.execution_id
            GROUP BY
                evaluation.execution_id, policy.quarantine_sla_seconds,
                sla_check.maximum_failed_rows, sla_check.maximum_failure_basis_points
            HAVING SUM(CASE WHEN quarantine.quarantined_at_utc < DATEADD(
                        SECOND, -CONVERT(INT, policy.quarantine_sla_seconds),
                        @database_now_utc
                    ) THEN CONVERT(BIGINT, 1) ELSE CONVERT(BIGINT, 0) END)
                        > sla_check.maximum_failed_rows
                OR CONVERT(DECIMAL(38, 0), SUM(CASE
                       WHEN quarantine.quarantined_at_utc < DATEADD(
                           SECOND, -CONVERT(INT, policy.quarantine_sla_seconds),
                           @database_now_utc
                       ) THEN CONVERT(BIGINT, 1) ELSE CONVERT(BIGINT, 0) END)) * 10000
                    > CONVERT(DECIMAL(38, 0), COUNT_BIG(quarantine.quarantine_record_id))
                        * sla_check.maximum_failure_basis_points
        ) AS breach
    );
    DECLARE @stale_running_executions BIGINT = (
        SELECT COUNT_BIG(*)
        FROM ctl.execution_attempt
        WHERE current_state IN (N'PLANNED', N'EXTRACTING', N'EXTRACTED', N'STAGED', N'PROMOTED')
          AND started_at_utc < @running_cutoff_utc
    );
    DECLARE @health_status NVARCHAR(16) = CASE
        WHEN @incomplete_data_quality_runs > 0 OR @failed_data_quality_runs > 0
            OR @overdue_quarantine_rows > 0 THEN N'DOWN'
        WHEN @stale_running_executions > 0 THEN N'DEGRADED'
        ELSE N'UP' END;
    DECLARE @reason_code NVARCHAR(64) = CASE
        WHEN @incomplete_data_quality_runs > 0 THEN N'DQ_INCOMPLETE'
        WHEN @failed_data_quality_runs > 0 THEN N'DQ_FAILED'
        WHEN @overdue_quarantine_rows > 0 THEN N'QUARANTINE_SLA_EXCEEDED'
        WHEN @stale_running_executions > 0 THEN N'RUNNING_STALE'
        ELSE N'PLATFORM_READY' END;

    SELECT
        @health_status AS health_status,
        @reason_code AS reason_code,
        @incomplete_data_quality_runs AS incomplete_data_quality_runs,
        @failed_data_quality_runs AS failed_data_quality_runs,
        @overdue_quarantine_rows AS overdue_quarantine_rows,
        @stale_running_executions AS stale_running_executions,
        @database_now_utc AS observed_at_utc;
END;
GO

-- Último gate dentro da transação atômica: qualquer bypass desfaz core, pointer e watermark.
CREATE OR ALTER TRIGGER ctl.trg_execution_publication_requires_data_quality
ON ctl.execution_publication_event
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @publication_gate_now_utc DATETIME2(3) = SYSUTCDATETIME();

    IF EXISTS (
        SELECT 1
        FROM inserted AS publication
        LEFT JOIN recon.execution_data_quality_evaluation AS evaluation WITH (HOLDLOCK)
            ON evaluation.execution_id = publication.execution_id
        LEFT JOIN ctl.data_quality_policy AS policy WITH (HOLDLOCK)
            ON policy.policy_version = evaluation.policy_version
           AND policy.policy_fingerprint = evaluation.policy_fingerprint
        LEFT JOIN ctl.execution_promotion_result AS promotion WITH (HOLDLOCK)
            ON promotion.execution_id = publication.execution_id
        LEFT JOIN ctl.execution_attempt AS attempt WITH (HOLDLOCK)
            ON attempt.execution_id = publication.execution_id
        LEFT JOIN ctl.execution_partition AS partition WITH (HOLDLOCK)
            ON partition.partition_id = attempt.partition_id
        WHERE evaluation.execution_id IS NULL
           OR policy.policy_version IS NULL
           OR evaluation.evaluation_state <> N'PASSED'
           OR evaluation.completed_checks <> evaluation.expected_checks
           OR evaluation.passed_checks <> evaluation.expected_checks
           OR evaluation.failed_checks <> 0
           OR evaluation.expected_checks <> policy.expected_checks
           OR evaluation.completed_checks <> (
               SELECT COUNT_BIG(*)
               FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
               WHERE result.execution_id = publication.execution_id
           )
           OR EXISTS (
               SELECT 1
               FROM recon.execution_data_quality_check_result AS failed_result WITH (HOLDLOCK)
               WHERE failed_result.execution_id = publication.execution_id
                 AND failed_result.check_state <> N'PASSED'
           )
           OR EXISTS (
               SELECT 1
               FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
               WHERE result.execution_id = publication.execution_id
                 AND NOT EXISTS (
                     SELECT 1
                     FROM ctl.data_quality_check_policy AS expected_check WITH (HOLDLOCK)
                     WHERE expected_check.policy_version = evaluation.policy_version
                       AND expected_check.policy_fingerprint = evaluation.policy_fingerprint
                       AND expected_check.check_ordinal = result.check_ordinal
                       AND expected_check.check_code = result.check_code
                 )
           )
           OR EXISTS (
               SELECT 1
               FROM ctl.data_quality_check_policy AS expected_check WITH (HOLDLOCK)
               WHERE expected_check.policy_version = evaluation.policy_version
                 AND expected_check.policy_fingerprint = evaluation.policy_fingerprint
                 AND NOT EXISTS (
                     SELECT 1
                     FROM recon.execution_data_quality_check_result AS result WITH (HOLDLOCK)
                     WHERE result.execution_id = publication.execution_id
                       AND result.check_ordinal = expected_check.check_ordinal
                       AND result.check_code = expected_check.check_code
                 )
           )
           OR EXISTS (
               SELECT 1
                FROM ctl.data_quality_check_policy AS sla_check WITH (HOLDLOCK)
                CROSS APPLY (
                    SELECT
                        (
                            SELECT COUNT_BIG(*)
                            FROM recon.quarantine_record AS quarantine WITH (HOLDLOCK)
                            WHERE quarantine.execution_id = publication.execution_id
                        ) AS total_rows,
                        (
                            SELECT COUNT_BIG(*)
                            FROM recon.quarantine_record AS quarantine WITH (HOLDLOCK)
                            WHERE quarantine.execution_id = publication.execution_id
                              AND quarantine.quarantined_at_utc < DATEADD(
                                  SECOND, -CONVERT(INT, policy.quarantine_sla_seconds),
                                  @publication_gate_now_utc
                              )
                        ) AS overdue_rows
                ) AS quarantine_summary
               WHERE sla_check.policy_version = evaluation.policy_version
                 AND sla_check.policy_fingerprint = evaluation.policy_fingerprint
                 AND sla_check.check_code = N'QUARANTINE_SLA'
                 AND (
                     quarantine_summary.overdue_rows > sla_check.maximum_failed_rows
                     OR CONVERT(DECIMAL(38, 0), quarantine_summary.overdue_rows) * 10000
                        > CONVERT(DECIMAL(38, 0), quarantine_summary.total_rows)
                            * sla_check.maximum_failure_basis_points
                 )
           )
           OR policy.policy_state <> N'RATIFIED'
           OR policy.effective_from_utc > @publication_gate_now_utc
           OR evaluation.evaluated_at_utc > publication.published_at_utc
           OR publication.published_at_utc > @publication_gate_now_utc
           OR evaluation.scope_fingerprint <> policy.scope_fingerprint
           OR partition.partition_id IS NULL
           OR policy.scope_fingerprint <> LOWER(CONVERT(
               CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
                   N'dq-scope-v1|',
                   DATALENGTH(partition.environment_name), N':', partition.environment_name, N'|',
                   DATALENGTH(partition.source_instance), N':', partition.source_instance, N'|',
                   DATALENGTH(partition.tenant_scope), N':', partition.tenant_scope, N'|',
                   DATALENGTH(partition.entity_name), N':', partition.entity_name, N'|',
                   DATALENGTH(partition.execution_mode), N':', partition.execution_mode
               ))), 2
           ))
           OR EXISTS (
               SELECT 1
               FROM ctl.data_quality_policy AS newer WITH (HOLDLOCK)
               WHERE newer.scope_fingerprint = policy.scope_fingerprint
                 AND newer.effective_from_utc <= @publication_gate_now_utc
                 AND newer.effective_from_utc > policy.effective_from_utc
           )
           OR promotion.execution_id IS NULL
           OR promotion.candidate_rows <> evaluation.candidate_rows
           OR promotion.promoted_at_utc <> evaluation.promotion_recorded_at_utc
    )
        THROW 51615, N'Publicação recusada sem Data Quality completa, vigente e aprovada.', 1;
END;
GO

EXEC dbo.usp_publish_v2_procedure_grant
    N'recon', N'usp_evaluate_execution_data_quality', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'recon', N'usp_observe_execution_data_quality', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'recon', N'usp_record_execution_metric', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'recon', N'usp_raise_observability_alert', N'v2_runtime';
EXEC dbo.usp_publish_v2_procedure_grant
    N'ctl', N'usp_observe_platform_health', N'v2_runtime';

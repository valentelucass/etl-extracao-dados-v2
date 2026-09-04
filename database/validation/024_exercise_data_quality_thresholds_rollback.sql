-- Exercício rollback-only dos dois limites de threshold e da transição temporal do SLA.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51650, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @window_start DATETIME2(3) = DATEADD(HOUR, -1, @now);
DECLARE @policy_effective DATETIME2(3) = DATEADD(DAY, -1, @now);
DECLARE @cycle_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000641';
DECLARE @absolute_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000642';
DECLARE @percentage_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000643';
DECLARE @boundary_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000644';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('1', 64);
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('2', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('3', 64);
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_DQ_THRESHOLD_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_DQ_THRESHOLD_TENANT';
DECLARE @execution_mode NVARCHAR(16) = N'BACKFILL';
DECLARE @threshold_owner NVARCHAR(64) = N'quality-owner';
DECLARE @quarantine_owner NVARCHAR(64) = N'quarantine-owner';
DECLARE @retention_owner NVARCHAR(64) = N'retention-owner';
DECLARE @retention_version NVARCHAR(128) = N'synthetic-retention-v1';

DECLARE @policy_specs TABLE (
    execution_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    entity_name NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    policy_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    total_rows INT NOT NULL,
    maximum_failed_rows BIGINT NOT NULL,
    maximum_failure_basis_points INT NOT NULL,
    scope_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NULL,
    policy_fingerprint CHAR(64) COLLATE Latin1_General_100_BIN2 NULL
);
INSERT INTO @policy_specs (
    execution_id, entity_name, policy_version, total_rows,
    maximum_failed_rows, maximum_failure_basis_points
) VALUES
    (@absolute_execution, N'SYNTHETIC_DQ_ABSOLUTE', N'synthetic-dq-absolute-v1', 2, 1, 4000),
    (@percentage_execution, N'SYNTHETIC_DQ_PERCENTAGE', N'synthetic-dq-percentage-v1', 10, 0, 1000),
    (@boundary_execution, N'SYNTHETIC_DQ_BOUNDARY', N'synthetic-dq-boundary-v1', 10, 1, 1000);

UPDATE @policy_specs
SET scope_fingerprint = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-scope-v1|',
        DATALENGTH(@environment_name), N':', @environment_name, N'|',
        DATALENGTH(@source_instance), N':', @source_instance, N'|',
        DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
        DATALENGTH(entity_name), N':', entity_name, N'|',
        DATALENGTH(@execution_mode), N':', @execution_mode
    ))), 2
));

UPDATE @policy_specs
SET policy_fingerprint = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-policy-v1|',
        DATALENGTH(policy_version), N':', policy_version, N'|',
        scope_fingerprint, N'|4|3600|',
        DATALENGTH(@threshold_owner), N':', @threshold_owner, N'|',
        DATALENGTH(@quarantine_owner), N':', @quarantine_owner, N'|',
        DATALENGTH(@retention_version), N':', @retention_version, N'|',
        DATALENGTH(@retention_owner), N':', @retention_owner, N'|',
        CONVERT(NVARCHAR(33), @policy_effective, 126), N'|',
        N'1:COUNT_EQUATION:0:0:', DATALENGTH(@threshold_owner), N':',
            @threshold_owner, N'|',
        N'2:PAGE_TERMINALITY:0:0:', DATALENGTH(@threshold_owner), N':',
            @threshold_owner, N'|',
        N'3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(@threshold_owner), N':',
            @threshold_owner, N'|',
        N'4:QUARANTINE_SLA:', maximum_failed_rows, N':',
            maximum_failure_basis_points, N':', DATALENGTH(@threshold_owner), N':',
            @threshold_owner
    ))), 2
));

IF EXISTS (SELECT 1 FROM @policy_specs WHERE scope_fingerprint IS NULL OR policy_fingerprint IS NULL)
    THROW 51651, N'O fixture não calculou fingerprints canônicos.', 1;

INSERT INTO ctl.data_quality_policy (
    policy_version, policy_fingerprint, scope_fingerprint, expected_checks,
    quarantine_sla_seconds, threshold_owner_role, quarantine_sla_owner_role,
    retention_policy_version, retention_owner_role, policy_state,
    effective_from_utc, recorded_at_utc
)
SELECT
    policy_version, policy_fingerprint, scope_fingerprint, 4, 3600,
    @threshold_owner, @quarantine_owner, @retention_version, @retention_owner,
    N'RATIFIED', @policy_effective, @now
FROM @policy_specs;

INSERT INTO ctl.data_quality_check_policy (
    policy_version, policy_fingerprint, check_ordinal, check_code,
    maximum_failed_rows, maximum_failure_basis_points, threshold_owner_role
)
SELECT
    policy.policy_version, policy.policy_fingerprint,
    check_definition.check_ordinal, check_definition.check_code,
    0, 0, @threshold_owner
FROM @policy_specs AS policy
CROSS JOIN (VALUES
    (CONVERT(SMALLINT, 1), N'COUNT_EQUATION'),
    (CONVERT(SMALLINT, 2), N'PAGE_TERMINALITY'),
    (CONVERT(SMALLINT, 3), N'PROMOTION_RECONCILIATION')
) AS check_definition (check_ordinal, check_code)
UNION ALL
SELECT
    policy_version, policy_fingerprint, 4, N'QUARANTINE_SLA',
    maximum_failed_rows, maximum_failure_basis_points, @threshold_owner
FROM @policy_specs;

EXEC ctl.usp_control_plane_register_source @source_instance, N'SYNTHETIC', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle_id, N'synthetic-dq-threshold-plan-v1', @plan_fingerprint, @now;

DECLARE @absolute_entity NVARCHAR(128) = (
    SELECT entity_name FROM @policy_specs WHERE execution_id = @absolute_execution
);
DECLARE @percentage_entity NVARCHAR(128) = (
    SELECT entity_name FROM @policy_specs WHERE execution_id = @percentage_execution
);
DECLARE @boundary_entity NVARCHAR(128) = (
    SELECT entity_name FROM @policy_specs WHERE execution_id = @boundary_execution
);

EXEC ctl.usp_control_plane_start_execution
    @absolute_execution, @cycle_id, @environment_name, @source_instance, @tenant_scope,
    @absolute_entity, @execution_mode, @window_start, @now, N'SYNTHETIC_INTERVAL',
    N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint,
    N'synthetic-dq-threshold-absolute-001', NULL, 3600, @now;
EXEC ctl.usp_control_plane_start_execution
    @percentage_execution, @cycle_id, @environment_name, @source_instance, @tenant_scope,
    @percentage_entity, @execution_mode, @window_start, @now, N'SYNTHETIC_INTERVAL',
    N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint,
    N'synthetic-dq-threshold-percentage-001', NULL, 3600, @now;
EXEC ctl.usp_control_plane_start_execution
    @boundary_execution, @cycle_id, @environment_name, @source_instance, @tenant_scope,
    @boundary_entity, @execution_mode, @window_start, @now, N'SYNTHETIC_INTERVAL',
    N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint,
    N'synthetic-dq-threshold-boundary-001', NULL, 3600, @now;

EXEC ctl.usp_control_plane_record_page
    @absolute_execution, 1, 1, 100, 2, 0, 128, 0, @now;
EXEC ctl.usp_control_plane_record_page
    @absolute_execution, 2, 1, 100, 0, 0, 0, 1, @now;
EXEC ctl.usp_control_plane_record_page
    @percentage_execution, 1, 1, 100, 10, 0, 256, 0, @now;
EXEC ctl.usp_control_plane_record_page
    @percentage_execution, 2, 1, 100, 0, 0, 0, 1, @now;
EXEC ctl.usp_control_plane_record_page
    @boundary_execution, 1, 1, 100, 10, 0, 256, 0, @now;
EXEC ctl.usp_control_plane_record_page
    @boundary_execution, 2, 1, 100, 0, 0, 0, 1, @now;

;WITH ten_rows AS (
    SELECT ordinal FROM (VALUES (1),(2),(3),(4),(5),(6),(7),(8),(9),(10)) AS rows (ordinal)
)
INSERT INTO stg.execution_record (
    execution_id, input_batch_number, input_record_ordinal, source_key,
    row_fingerprint_version, source_row_hash, presence_fingerprint_version,
    presence_fingerprint, source_freshness_at_utc, validation_disposition,
    quarantine_reason_code, staged_at_utc
)
SELECT
    policy.execution_id, 1, rows.ordinal, NULL, NULL, NULL, NULL, NULL,
    @window_start, N'QUARANTINE', N'SYNTHETIC_INVALID', @now
FROM @policy_specs AS policy
INNER JOIN ten_rows AS rows ON rows.ordinal <= policy.total_rows;

EXEC ctl.usp_control_plane_transition_execution
    @absolute_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @absolute_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @absolute_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;
EXEC ctl.usp_control_plane_transition_execution
    @percentage_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @percentage_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @percentage_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;
EXEC ctl.usp_control_plane_transition_execution
    @boundary_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @boundary_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @boundary_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;

UPDATE quarantine
SET quarantined_at_utc = DATEADD(HOUR, -2, @now)
FROM recon.quarantine_record AS quarantine
INNER JOIN stg.execution_record AS staged
    ON staged.stage_record_id = quarantine.stage_record_id
WHERE staged.input_record_ordinal = 1
  AND quarantine.execution_id IN (
      @absolute_execution, @percentage_execution, @boundary_execution
  );

DECLARE @summary TABLE (
    execution_id UNIQUEIDENTIFIER,
    policy_version NVARCHAR(128),
    policy_fingerprint CHAR(64),
    evaluation_fingerprint CHAR(64),
    expected_checks SMALLINT,
    completed_checks SMALLINT,
    passed_checks SMALLINT,
    failed_checks SMALLINT,
    evaluated_rows BIGINT,
    failed_rows BIGINT,
    evaluation_state NVARCHAR(16),
    evaluated_at_utc DATETIME2(3)
);
DECLARE @absolute_policy_version NVARCHAR(128);
DECLARE @absolute_policy_fingerprint CHAR(64);
DECLARE @percentage_policy_version NVARCHAR(128);
DECLARE @percentage_policy_fingerprint CHAR(64);
DECLARE @boundary_policy_version NVARCHAR(128);
DECLARE @boundary_policy_fingerprint CHAR(64);
SELECT @absolute_policy_version = policy_version,
       @absolute_policy_fingerprint = policy_fingerprint
FROM @policy_specs WHERE execution_id = @absolute_execution;
SELECT @percentage_policy_version = policy_version,
       @percentage_policy_fingerprint = policy_fingerprint
FROM @policy_specs WHERE execution_id = @percentage_execution;
SELECT @boundary_policy_version = policy_version,
       @boundary_policy_fingerprint = policy_fingerprint
FROM @policy_specs WHERE execution_id = @boundary_execution;

INSERT INTO @summary EXEC recon.usp_evaluate_execution_data_quality
    @absolute_execution, @absolute_policy_version, @absolute_policy_fingerprint;
INSERT INTO @summary EXEC recon.usp_evaluate_execution_data_quality
    @percentage_execution, @percentage_policy_version, @percentage_policy_fingerprint;
INSERT INTO @summary EXEC recon.usp_evaluate_execution_data_quality
    @boundary_execution, @boundary_policy_version, @boundary_policy_fingerprint;

IF NOT EXISTS (
    SELECT 1 FROM recon.execution_data_quality_check_result
    WHERE execution_id = @absolute_execution AND check_code = N'QUARANTINE_SLA'
      AND evaluated_rows = 2 AND failed_rows = 1
      AND failure_basis_points = 5000 AND check_state = N'FAILED'
      AND reason_code = N'QUARANTINE_SLA_EXCEEDED'
)
    THROW 51652, N'O limite percentual não bloqueou quando o absoluto ainda passava.', 1;
IF NOT EXISTS (
    SELECT 1 FROM recon.execution_data_quality_check_result
    WHERE execution_id = @percentage_execution AND check_code = N'QUARANTINE_SLA'
      AND evaluated_rows = 10 AND failed_rows = 1
      AND failure_basis_points = 1000 AND check_state = N'FAILED'
      AND reason_code = N'QUARANTINE_SLA_EXCEEDED'
)
    THROW 51653, N'O limite absoluto não bloqueou na igualdade percentual.', 1;
IF NOT EXISTS (
    SELECT 1 FROM recon.execution_data_quality_check_result
    WHERE execution_id = @boundary_execution AND check_code = N'QUARANTINE_SLA'
      AND evaluated_rows = 10 AND failed_rows = 1
      AND failure_basis_points = 1000 AND check_state = N'PASSED'
      AND reason_code = N'CHECK_WITHIN_THRESHOLD'
) OR NOT EXISTS (
    SELECT 1 FROM @summary
    WHERE execution_id = @boundary_execution AND evaluation_state = N'PASSED'
      AND passed_checks = 4 AND failed_checks = 0
)
    THROW 51654, N'A igualdade simultânea dos thresholds não passou.', 1;

EXEC ctl.usp_control_plane_transition_execution
    @absolute_execution, N'PROMOTED', N'FAILED', N'DQ_THRESHOLD_FAILED', @now;
EXEC ctl.usp_control_plane_transition_execution
    @percentage_execution, N'PROMOTED', N'FAILED', N'DQ_THRESHOLD_FAILED', @now;

-- O snapshot persistido passou na igualdade; uma segunda linha cruza o SLA depois da avaliação.
UPDATE quarantine
SET quarantined_at_utc = DATEADD(HOUR, -2, @now)
FROM recon.quarantine_record AS quarantine
INNER JOIN stg.execution_record AS staged
    ON staged.stage_record_id = quarantine.stage_record_id
WHERE quarantine.execution_id = @boundary_execution
  AND staged.input_record_ordinal = 2;

DECLARE @health TABLE (
    health_status NVARCHAR(16), reason_code NVARCHAR(64),
    incomplete_data_quality_runs BIGINT, failed_data_quality_runs BIGINT,
    overdue_quarantine_rows BIGINT, stale_running_executions BIGINT,
    observed_at_utc DATETIME2(3)
);
INSERT INTO @health EXEC ctl.usp_observe_platform_health 3600;
IF NOT EXISTS (
    SELECT 1 FROM @health
    WHERE health_status = N'DOWN' AND reason_code = N'QUARANTINE_SLA_EXCEEDED'
      AND failed_data_quality_runs = 0 AND overdue_quarantine_rows = 2
)
    THROW 51655, N'O health não reavaliou o crossing temporal do SLA.', 1;

-- Última ação: a mesma mudança temporal deve invalidar a publicação no instante do trigger.
DECLARE @boundary_partition_id BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @boundary_execution
);
DECLARE @publication_gate_error INT = NULL;
DECLARE @publication_at DATETIME2(3) = SYSUTCDATETIME();
BEGIN TRY
    INSERT INTO ctl.execution_publication_event (
        execution_id, partition_id, previous_published_execution_id, published_at_utc,
        incremental_frontier_before_utc, incremental_frontier_after_utc,
        watermark_last_partition_id
    ) VALUES (
        @boundary_execution, @boundary_partition_id, NULL, @publication_at, NULL, NULL, NULL
    );
END TRY
BEGIN CATCH
    SET @publication_gate_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @publication_gate_error <> 51615
    THROW 51657, N'O trigger não reavaliou o crossing temporal do SLA.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51656, N'O rollback do exercício de thresholds não encerrou o escopo.', 1;

PRINT N'Threshold absoluto/percentual, igualdade e crossing temporal no health/trigger validados.';

-- Exercício sintético, set-based e rollback-only de V2-023.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51630, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;

CREATE TABLE #dq_transaction_sentinel (
    marker NVARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
);
INSERT INTO #dq_transaction_sentinel (marker) VALUES (N'OUTSIDE');

BEGIN TRANSACTION;
INSERT INTO #dq_transaction_sentinel (marker) VALUES (N'PENDING');

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "021_validate_observability_data_quality.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @window_start DATETIME2(3) = DATEADD(HOUR, -1, @now);
DECLARE @policy_effective DATETIME2(3) = DATEADD(DAY, -1, @now);
DECLARE @cycle_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000601';
DECLARE @pass_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000602';
DECLARE @fail_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000603';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @presence_fingerprint CHAR(64) = REPLICATE('e', 64);
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_DQ_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_DQ_TENANT';
DECLARE @execution_mode NVARCHAR(16) = N'BACKFILL';
DECLARE @pass_entity NVARCHAR(128) = N'SYNTHETIC_DQ_PASS';
DECLARE @fail_entity NVARCHAR(128) = N'SYNTHETIC_DQ_FAIL';
DECLARE @threshold_owner NVARCHAR(64) = N'quality-owner';
DECLARE @quarantine_owner NVARCHAR(64) = N'quarantine-owner';
DECLARE @retention_owner NVARCHAR(64) = N'retention-owner';
DECLARE @retention_version NVARCHAR(128) = N'synthetic-retention-v1';
DECLARE @pass_policy_version NVARCHAR(128) = N'synthetic-dq-pass-v1';
DECLARE @fail_policy_version NVARCHAR(128) = N'synthetic-dq-fail-v1';

DECLARE @pass_scope_material NVARCHAR(2000) = CONCAT(
    N'dq-scope-v1|',
    DATALENGTH(@environment_name), N':', @environment_name, N'|',
    DATALENGTH(@source_instance), N':', @source_instance, N'|',
    DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
    DATALENGTH(@pass_entity), N':', @pass_entity, N'|',
    DATALENGTH(@execution_mode), N':', @execution_mode
);
DECLARE @fail_scope_material NVARCHAR(2000) = CONCAT(
    N'dq-scope-v1|',
    DATALENGTH(@environment_name), N':', @environment_name, N'|',
    DATALENGTH(@source_instance), N':', @source_instance, N'|',
    DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
    DATALENGTH(@fail_entity), N':', @fail_entity, N'|',
    DATALENGTH(@execution_mode), N':', @execution_mode
);
DECLARE @pass_scope_fingerprint CHAR(64) = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @pass_scope_material)), 2
));
DECLARE @fail_scope_fingerprint CHAR(64) = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @fail_scope_material)), 2
));

DECLARE @pass_policy_material NVARCHAR(4000) = CONCAT(
    N'dq-policy-v1|',
    DATALENGTH(@pass_policy_version), N':', @pass_policy_version, N'|',
    @pass_scope_fingerprint, N'|4|3600|',
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
);
DECLARE @fail_policy_material NVARCHAR(4000) = CONCAT(
    N'dq-policy-v1|',
    DATALENGTH(@fail_policy_version), N':', @fail_policy_version, N'|',
    @fail_scope_fingerprint, N'|4|3600|',
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
);
DECLARE @pass_policy_fingerprint CHAR(64) = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @pass_policy_material)), 2
));
DECLARE @fail_policy_fingerprint CHAR(64) = LOWER(CONVERT(
    CHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), @fail_policy_material)), 2
));

INSERT INTO ctl.data_quality_policy (
    policy_version, policy_fingerprint, scope_fingerprint, expected_checks,
    quarantine_sla_seconds, threshold_owner_role, quarantine_sla_owner_role,
    retention_policy_version, retention_owner_role, policy_state,
    effective_from_utc, recorded_at_utc
) VALUES
    (
        @pass_policy_version, @pass_policy_fingerprint, @pass_scope_fingerprint, 4,
        3600, @threshold_owner, @quarantine_owner, @retention_version,
        @retention_owner, N'RATIFIED', @policy_effective, @now
    ),
    (
        @fail_policy_version, @fail_policy_fingerprint, @fail_scope_fingerprint, 4,
        3600, @threshold_owner, @quarantine_owner, @retention_version,
        @retention_owner, N'RATIFIED', @policy_effective, @now
    );

INSERT INTO ctl.data_quality_check_policy (
    policy_version, policy_fingerprint, check_ordinal, check_code,
    maximum_failed_rows, maximum_failure_basis_points, threshold_owner_role
)
SELECT policy.policy_version, policy.policy_fingerprint,
       check_definition.check_ordinal, check_definition.check_code,
       0, 0, @threshold_owner
FROM (VALUES
    (@pass_policy_version, @pass_policy_fingerprint),
    (@fail_policy_version, @fail_policy_fingerprint)
) AS policy (policy_version, policy_fingerprint)
CROSS JOIN (VALUES
    (CONVERT(SMALLINT, 1), N'COUNT_EQUATION'),
    (CONVERT(SMALLINT, 2), N'PAGE_TERMINALITY'),
    (CONVERT(SMALLINT, 3), N'PROMOTION_RECONCILIATION'),
    (CONVERT(SMALLINT, 4), N'QUARANTINE_SLA')
) AS check_definition (check_ordinal, check_code);

EXEC ctl.usp_control_plane_register_source @source_instance, N'SYNTHETIC', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle_id, N'synthetic-dq-plan-v1', @plan_fingerprint, @now;

EXEC ctl.usp_control_plane_start_execution
    @execution_id = @pass_execution,
    @cycle_id = @cycle_id,
    @environment_name = @environment_name,
    @source_instance = @source_instance,
    @tenant_scope = @tenant_scope,
    @entity_name = @pass_entity,
    @execution_mode = @execution_mode,
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @now,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-dq-pass-001',
    @lease_seconds = 3600,
    @started_at_utc = @now;
EXEC ctl.usp_control_plane_record_page
    @pass_execution, 1, 1, 100, 1, 1, 128, 0, @now;
EXEC ctl.usp_control_plane_record_page
    @pass_execution, 2, 1, 100, 0, 0, 0, 1, @now;
EXEC stg.usp_stage_record
    @pass_execution, 1, 1, N'synthetic-key-pass', N'row-v1', @row_hash,
    N'presence-v1', @presence_fingerprint, @window_start, N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @pass_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @pass_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @pass_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;

DECLARE @pass_summary TABLE (
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
INSERT INTO @pass_summary
EXEC recon.usp_evaluate_execution_data_quality
    @pass_execution, @pass_policy_version, @pass_policy_fingerprint;

IF NOT EXISTS (
    SELECT 1 FROM @pass_summary
    WHERE execution_id = @pass_execution
      AND expected_checks = 4 AND completed_checks = 4
      AND passed_checks = 4 AND failed_checks = 0
      AND failed_rows = 0 AND evaluation_state = N'PASSED'
) OR (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_check_result
      WHERE execution_id = @pass_execution) <> 4
    THROW 51631, N'O fixture positivo de Data Quality não passou integralmente.', 1;

DELETE FROM @pass_summary;
INSERT INTO @pass_summary
EXEC recon.usp_evaluate_execution_data_quality
    @pass_execution, @pass_policy_version, @pass_policy_fingerprint;
IF (SELECT COUNT_BIG(*) FROM @pass_summary) <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.execution_data_quality_evaluation
       WHERE execution_id = @pass_execution) <> 1
    THROW 51632, N'O retry exato de Data Quality não foi idempotente.', 1;

EXEC recon.usp_record_execution_metric
    @execution_id = @pass_execution,
    @metric_sequence = 1,
    @duration_milliseconds = 1000,
    @pages = 2,
    @response_bytes = 128,
    @physical_rows = 1,
    @distinct_root_keys = 1,
    @duplicate_rows = 0,
    @valid_rows = 1,
    @quarantined_root_keys = 0,
    @unidentified_quarantine_rows = 0,
    @quarantined_stage_rows = 0,
    @candidate_rows = 1,
    @inserted_rows = 1,
    @updated_rows = 0,
    @reactivated_rows = 0,
    @noop_rows = 0,
    @stale_noop_rows = 0,
    @retry_attempts = 1,
    @rate_limit_responses = 0,
    @source_lag_milliseconds = 0,
    @watermark_utc = NULL,
    @captured_at_utc = @now;
EXEC recon.usp_record_execution_metric
    @execution_id = @pass_execution,
    @metric_sequence = 1,
    @duration_milliseconds = 1000,
    @pages = 2,
    @response_bytes = 128,
    @physical_rows = 1,
    @distinct_root_keys = 1,
    @duplicate_rows = 0,
    @valid_rows = 1,
    @quarantined_root_keys = 0,
    @unidentified_quarantine_rows = 0,
    @quarantined_stage_rows = 0,
    @candidate_rows = 1,
    @inserted_rows = 1,
    @updated_rows = 0,
    @reactivated_rows = 0,
    @noop_rows = 0,
    @stale_noop_rows = 0,
    @retry_attempts = 1,
    @rate_limit_responses = 0,
    @source_lag_milliseconds = 0,
    @watermark_utc = NULL,
    @captured_at_utc = @now;
EXEC recon.usp_raise_observability_alert
    @pass_policy_fingerprint, 1, N'INFO', N'SYNTHETIC_ALERT', N'operations-owner', 1, @now;
EXEC recon.usp_raise_observability_alert
    @pass_policy_fingerprint, 1, N'INFO', N'SYNTHETIC_ALERT', N'operations-owner', 1, @now;
IF (SELECT COUNT_BIG(*) FROM recon.execution_metric_snapshot
    WHERE execution_id = @pass_execution) <> 1
   OR (SELECT COUNT_BIG(*) FROM recon.observability_alert
       WHERE correlation_reference = @pass_policy_fingerprint) <> 1
    THROW 51633, N'Métrica ou alerta não preservou retry exato.', 1;

EXEC core.usp_apply_reconcile_publish_execution
    @pass_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;
IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @pass_execution AND current_state = N'PUBLISHED'
)
    THROW 51634, N'O permit DQ aprovado não liberou a publicação atômica.', 1;

EXEC ctl.usp_control_plane_start_execution
    @execution_id = @fail_execution,
    @cycle_id = @cycle_id,
    @environment_name = @environment_name,
    @source_instance = @source_instance,
    @tenant_scope = @tenant_scope,
    @entity_name = @fail_entity,
    @execution_mode = @execution_mode,
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @now,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-dq-fail-001',
    @lease_seconds = 3600,
    @started_at_utc = @now;
EXEC ctl.usp_control_plane_record_page
    @fail_execution, 1, 1, 100, 1, 1, 128, 0, @now;
-- Página vazia não terminal é anômala mesmo que uma página terminal posterior exista.
EXEC ctl.usp_control_plane_record_page
    @fail_execution, 2, 1, 100, 0, 0, 0, 0, @now;
EXEC ctl.usp_control_plane_record_page
    @fail_execution, 3, 1, 100, 0, 0, 0, 1, @now;
EXEC stg.usp_stage_record
    @fail_execution, 1, 1, N'synthetic-key-fail', N'row-v1', @row_hash,
    N'presence-v1', @presence_fingerprint, @window_start, N'VALID', NULL, @now;
EXEC ctl.usp_control_plane_transition_execution
    @fail_execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution
    @fail_execution, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
EXEC core.usp_prepare_staged_execution
    @fail_execution, N'synthetic-contract-v1', @contract_fingerprint,
    N'synthetic-config-v1', @configuration_fingerprint;

DECLARE @fail_summary TABLE (
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
INSERT INTO @fail_summary
EXEC recon.usp_evaluate_execution_data_quality
    @fail_execution, @fail_policy_version, @fail_policy_fingerprint;
IF NOT EXISTS (
    SELECT 1 FROM @fail_summary
    WHERE execution_id = @fail_execution
      AND completed_checks = expected_checks
      AND failed_checks = 1 AND failed_rows = 1 AND evaluation_state = N'FAILED'
) OR NOT EXISTS (
    SELECT 1 FROM recon.execution_data_quality_check_result
    WHERE execution_id = @fail_execution
      AND check_code = N'PAGE_TERMINALITY' AND check_state = N'FAILED'
)
    THROW 51635, N'O check inválido não terminou FAILED de forma completa.', 1;

DECLARE @health TABLE (
    health_status NVARCHAR(16), reason_code NVARCHAR(64),
    incomplete_data_quality_runs BIGINT, failed_data_quality_runs BIGINT,
    overdue_quarantine_rows BIGINT, stale_running_executions BIGINT,
    observed_at_utc DATETIME2(3)
);
INSERT INTO @health EXEC ctl.usp_observe_platform_health 3600;
IF NOT EXISTS (
    SELECT 1 FROM @health
    WHERE health_status = N'DOWN' AND reason_code = N'DQ_FAILED'
      AND failed_data_quality_runs = 1
)
    THROW 51636, N'O health ocultou uma avaliação DQ falha.', 1;

-- Última ação: o trigger deve desfazer toda mutação do apply e a transação externa.
DECLARE @publication_gate_error INT = NULL;
BEGIN TRY
    EXEC core.usp_apply_reconcile_publish_execution
        @fail_execution, N'synthetic-contract-v1', @contract_fingerprint,
        N'synthetic-config-v1', @configuration_fingerprint;
END TRY
BEGIN CATCH
    SET @publication_gate_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;

IF @publication_gate_error <> 51615
    THROW 51637, N'A publicação sem PASS não foi bloqueada pelo gate SQL.', 1;
IF EXISTS (SELECT 1 FROM #dq_transaction_sentinel WHERE marker = N'PENDING')
    THROW 51638, N'O rollback não removeu o marcador interno da transação.', 1;
IF NOT EXISTS (SELECT 1 FROM #dq_transaction_sentinel WHERE marker = N'OUTSIDE')
    THROW 51639, N'O rollback ultrapassou o caller autorizado.', 1;

DROP TABLE #dq_transaction_sentinel;
PRINT N'Observabilidade e Data Quality exercitadas com fixtures sintéticas e rollback integral.';

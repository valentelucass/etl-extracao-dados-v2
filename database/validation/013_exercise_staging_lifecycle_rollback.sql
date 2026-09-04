-- Exercício sintético, limitado e rollback-only do lifecycle V2-045a.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51581, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "012_validate_staging_lifecycle.sql"
GO

-- Este exercício isola o lifecycle histórico. O hard gate DQ de publicação é exercitado
-- separadamente por 022 dentro de sua própria transação rollback-only.
DISABLE TRIGGER ctl.trg_execution_publication_requires_data_quality
    ON ctl.execution_publication_event;

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @old_start DATETIME2(3) = DATEADD(DAY, -3, @now);
DECLARE @old_terminal DATETIME2(3) = DATEADD(DAY, -2, @now);
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_LIFECYCLE_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_LIFECYCLE_TENANT';
DECLARE @entity NVARCHAR(128) = N'SYNTHETIC_LIFECYCLE_ENTITY';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000500';
DECLARE @eligible UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000501';
DECLARE @held UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000502';
DECLARE @too_recent UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000503';
DECLARE @non_terminal UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000504';
DECLARE @policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000510';
DECLARE @hold UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000511';
DECLARE @archive_hold UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000514';
DECLARE @plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000512';
DECLARE @restore UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000513';
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @cycle_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @policy_fingerprint CHAR(64) = REPLICATE('d', 64);
DECLARE @data_owner_evidence CHAR(64) = REPLICATE('e', 64);
DECLARE @compliance_evidence CHAR(64) = REPLICATE('f', 64);
DECLARE @hold_evidence CHAR(64) = REPLICATE('1', 64);
DECLARE @row_hash CHAR(64) = REPLICATE('2', 64);
DECLARE @presence_hash CHAR(64) = REPLICATE('3', 64);
DECLARE @eligible_start DATETIME2(3) = DATEADD(HOUR, -8, @now);
DECLARE @eligible_end DATETIME2(3) = DATEADD(HOUR, -7, @now);
DECLARE @held_start DATETIME2(3) = DATEADD(HOUR, -6, @now);
DECLARE @held_end DATETIME2(3) = DATEADD(HOUR, -5, @now);
DECLARE @recent_start DATETIME2(3) = DATEADD(HOUR, -4, @now);
DECLARE @recent_end DATETIME2(3) = DATEADD(HOUR, -3, @now);
DECLARE @running_start DATETIME2(3) = DATEADD(HOUR, -2, @now);
DECLARE @running_end DATETIME2(3) = DATEADD(HOUR, -1, @now);

CREATE USER v2_retention_governor_probe WITHOUT LOGIN;
CREATE USER v2_lifecycle_reviewer_probe WITHOUT LOGIN;
CREATE USER v2_lifecycle_operator_probe WITHOUT LOGIN;
CREATE USER v2_archive_restorer_probe WITHOUT LOGIN;
ALTER ROLE v2_retention_governor ADD MEMBER v2_retention_governor_probe;
ALTER ROLE v2_lifecycle_reviewer ADD MEMBER v2_lifecycle_reviewer_probe;
ALTER ROLE v2_lifecycle_operator ADD MEMBER v2_lifecycle_operator_probe;
ALTER ROLE v2_archive_restorer ADD MEMBER v2_archive_restorer_probe;

DECLARE @governor_execute INT;
DECLARE @reviewer_execute INT;
DECLARE @operator_archive_execute INT;
DECLARE @operator_purge_execute INT;
DECLARE @restorer_execute INT;
DECLARE @forbidden_select INT;
DECLARE @forbidden_cross_role_execute INT;

EXECUTE AS USER = N'v2_retention_governor_probe';
SELECT @governor_execute = HAS_PERMS_BY_NAME(
           N'ctl.usp_approve_staging_retention_policy', N'OBJECT', N'EXECUTE'),
       @forbidden_select = HAS_PERMS_BY_NAME(
           N'ctl.staging_retention_policy', N'OBJECT', N'SELECT'),
       @forbidden_cross_role_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_purge_staging_lifecycle', N'OBJECT', N'EXECUTE');
REVERT;
IF COALESCE(@governor_execute, 0) <> 1 OR COALESCE(@forbidden_select, 1) <> 0
    OR COALESCE(@forbidden_cross_role_execute, 1) <> 0
    THROW 51598, N'Boundary efetivo da role de governança divergiu.', 1;

EXECUTE AS USER = N'v2_lifecycle_reviewer_probe';
SELECT @reviewer_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_plan_staging_lifecycle', N'OBJECT', N'EXECUTE'),
       @forbidden_select = HAS_PERMS_BY_NAME(
           N'ctl.staging_lifecycle_plan', N'OBJECT', N'SELECT'),
       @forbidden_cross_role_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_archive_staging_lifecycle', N'OBJECT', N'EXECUTE');
REVERT;
IF COALESCE(@reviewer_execute, 0) <> 1 OR COALESCE(@forbidden_select, 1) <> 0
    OR COALESCE(@forbidden_cross_role_execute, 1) <> 0
    THROW 51598, N'Boundary efetivo da role de revisão divergiu.', 1;

EXECUTE AS USER = N'v2_lifecycle_operator_probe';
SELECT @operator_archive_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_archive_staging_lifecycle', N'OBJECT', N'EXECUTE'),
       @operator_purge_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_purge_staging_lifecycle', N'OBJECT', N'EXECUTE'),
       @forbidden_select = HAS_PERMS_BY_NAME(
           N'recon.staging_lifecycle_archive_manifest', N'OBJECT', N'SELECT'),
       @forbidden_cross_role_execute = HAS_PERMS_BY_NAME(
           N'recon.usp_restore_staging_archive', N'OBJECT', N'EXECUTE');
REVERT;
IF COALESCE(@operator_archive_execute, 0) <> 1
    OR COALESCE(@operator_purge_execute, 0) <> 1
    OR COALESCE(@forbidden_select, 1) <> 0
    OR COALESCE(@forbidden_cross_role_execute, 1) <> 0
    THROW 51598, N'Boundary efetivo da role de operação divergiu.', 1;

EXECUTE AS USER = N'v2_archive_restorer_probe';
SELECT @restorer_execute = HAS_PERMS_BY_NAME(
           N'recon.usp_restore_staging_archive', N'OBJECT', N'EXECUTE'),
       @forbidden_select = HAS_PERMS_BY_NAME(
           N'recon.staging_record_archive', N'OBJECT', N'SELECT'),
       @forbidden_cross_role_execute = HAS_PERMS_BY_NAME(
           N'stg.usp_plan_staging_lifecycle', N'OBJECT', N'EXECUTE');
REVERT;
IF COALESCE(@restorer_execute, 0) <> 1 OR COALESCE(@forbidden_select, 1) <> 0
    OR COALESCE(@forbidden_cross_role_execute, 1) <> 0
    THROW 51598, N'Boundary efetivo da role de restore divergiu.', 1;

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @old_start;
EXEC ctl.usp_control_plane_start_cycle @cycle, N'lifecycle-plan-v1',
    @cycle_fingerprint, @old_start;

EXEC ctl.usp_control_plane_start_execution
    @eligible, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @eligible_start, @eligible_end, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-lifecycle-eligible', NULL, 3600, @now;
EXEC ctl.usp_control_plane_start_execution
    @held, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @held_start, @held_end, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-lifecycle-held', NULL, 3600, @now;
EXEC ctl.usp_control_plane_start_execution
    @too_recent, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @recent_start, @recent_end, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-lifecycle-recent', NULL, 3600, @now;
EXEC ctl.usp_control_plane_start_execution
    @non_terminal, @cycle, N'LOCAL_SHADOW', @source, @tenant, @entity, N'BACKFILL',
    @running_start, @running_end, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-lifecycle-running', NULL, 3600, @now;

EXEC stg.usp_stage_record @eligible, 1, 1, N'eligible-key', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @old_terminal, N'VALID', NULL, @now;
EXEC stg.usp_stage_record @eligible, 1, 2, NULL, NULL, NULL, NULL, NULL,
    NULL, N'QUARANTINE', N'SOURCE_KEY_MISSING', @now;
EXEC stg.usp_stage_record @held, 1, 1, N'held-key', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @old_terminal, N'VALID', NULL, @now;
EXEC stg.usp_stage_record @too_recent, 1, 1, N'recent-key', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @now, N'VALID', NULL, @now;
EXEC stg.usp_stage_record @non_terminal, 1, 1, N'running-key', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @now, N'VALID', NULL, @now;

DECLARE @eligible_stage BIGINT = (
    SELECT stage_record_id FROM stg.execution_record
    WHERE execution_id = @eligible AND input_record_ordinal = 1
);
DECLARE @quarantine_stage BIGINT = (
    SELECT stage_record_id FROM stg.execution_record
    WHERE execution_id = @eligible AND input_record_ordinal = 2
);

INSERT INTO stg.execution_candidate (
    execution_id, source_key, winner_stage_record_id, row_fingerprint_version,
    source_row_hash, presence_fingerprint_version, presence_fingerprint,
    source_freshness_at_utc, prepared_at_utc
) VALUES (
    @eligible, N'eligible-key', @eligible_stage, N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @old_terminal, @old_terminal
);

INSERT INTO recon.quarantine_record (
    execution_id, stage_record_id, reason_code, source_key, row_fingerprint_version,
    source_row_hash, presence_fingerprint_version, presence_fingerprint, quarantined_at_utc
) VALUES (
    @eligible, @quarantine_stage, N'SOURCE_KEY_MISSING', NULL, NULL, NULL,
    NULL, NULL, @old_terminal
);

INSERT INTO core.entity_record_state (
    environment_name, source_instance, tenant_scope, entity_name, source_key,
    row_fingerprint_version, source_row_hash, presence_fingerprint_version,
    presence_fingerprint, source_freshness_at_utc, active,
    first_promoted_execution_id, last_promoted_execution_id,
    first_promoted_at_utc, last_promoted_at_utc
) VALUES (
    N'LOCAL_SHADOW', @source, @tenant, @entity, N'eligible-key',
    N'row-v1', @row_hash, N'presence-v1', @presence_hash, @old_terminal, 1,
    @eligible, @eligible, @old_terminal, @old_terminal
);
DECLARE @record_state_id BIGINT = SCOPE_IDENTITY();

INSERT INTO recon.execution_candidate_application (
    execution_id, source_key, record_state_id, application_disposition,
    result_row_fingerprint_version, result_source_row_hash,
    result_presence_fingerprint_version, result_presence_fingerprint,
    result_source_freshness_at_utc, applied_at_utc
) VALUES (
    @eligible, N'eligible-key', @record_state_id, N'INSERTED', N'row-v1', @row_hash,
    N'presence-v1', @presence_hash, @old_terminal, @old_terminal
);

INSERT INTO ctl.execution_page_audit (
    execution_id, page_number, page_attempt, requested_page_size, physical_rows,
    distinct_root_keys, response_bytes, terminal_empty_page, terminal_evidence_kind,
    read_at_utc
) VALUES (@eligible, 1, 1, 100, 2, 1, 256, 0, N'NONE', @old_terminal);

INSERT INTO ctl.execution_count (
    execution_id, count_phase, physical_rows, distinct_root_keys, duplicate_rows,
    valid_rows, quarantined_root_keys, unidentified_quarantine_rows, recorded_at_utc
) VALUES (@eligible, N'POST_STAGE', 2, 2, 0, 1, 1, 0, @old_terminal);

INSERT INTO ctl.execution_promotion_result (
    execution_id, physical_rows, distinct_root_keys, candidate_rows, duplicate_rows,
    quarantined_root_keys, unidentified_quarantine_rows, quarantined_stage_rows,
    promoted_at_utc
) VALUES (@eligible, 2, 2, 1, 0, 1, 0, 1, @old_terminal);

INSERT INTO recon.execution_reconciliation_result (
    execution_id, candidate_rows, inserted_rows, updated_rows, reactivated_rows,
    noop_rows, stale_noop_rows, reconciled_at_utc, published_at_utc
) VALUES (@eligible, 1, 1, 0, 0, 0, 0, @old_terminal, @old_terminal);

DECLARE @eligible_partition BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @eligible
);
INSERT INTO ctl.execution_publication_event (
    execution_id, partition_id, previous_published_execution_id, published_at_utc,
    incremental_frontier_before_utc, incremental_frontier_after_utc,
    watermark_last_partition_id
) VALUES (@eligible, @eligible_partition, NULL, @old_terminal, NULL, NULL, NULL);

DECLARE @old_t1 DATETIME2(3) = DATEADD(MILLISECOND, 1, @old_terminal);
DECLARE @old_t2 DATETIME2(3) = DATEADD(MILLISECOND, 2, @old_terminal);
DECLARE @old_t3 DATETIME2(3) = DATEADD(MILLISECOND, 3, @old_terminal);
DECLARE @old_t4 DATETIME2(3) = DATEADD(MILLISECOND, 4, @old_terminal);
DECLARE @old_t5 DATETIME2(3) = DATEADD(MILLISECOND, 5, @old_terminal);

EXEC ctl.usp_control_plane_transition_execution @eligible, N'EXTRACTING', N'EXTRACTED',
    N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @eligible, N'EXTRACTED', N'STAGED',
    N'STAGE_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @eligible, N'STAGED', N'FAILED',
    N'SYNTHETIC_FAILURE', @now;

EXEC ctl.usp_control_plane_transition_execution @held, N'EXTRACTING', N'EXTRACTED',
    N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @held, N'EXTRACTED', N'STAGED',
    N'STAGE_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @held, N'STAGED', N'FAILED',
    N'SYNTHETIC_FAILURE', @now;

UPDATE ctl.execution_attempt
SET started_at_utc = @old_start, terminal_at_utc = @old_t5
WHERE execution_id IN (@eligible, @held);
UPDATE ctl.execution_state_event
SET transitioned_at_utc = CASE
        WHEN transition_sequence IN (1, 2) THEN @old_start
        ELSE @old_t5
    END
WHERE execution_id IN (@eligible, @held);
UPDATE stg.execution_record
SET staged_at_utc = @old_terminal
WHERE execution_id IN (@eligible, @held);

EXEC ctl.usp_control_plane_transition_execution @too_recent, N'EXTRACTING', N'EXTRACTED',
    N'EXTRACTION_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @too_recent, N'EXTRACTED', N'STAGED',
    N'STAGE_OK', @now;
EXEC ctl.usp_control_plane_transition_execution @too_recent, N'STAGED', N'FAILED',
    N'SYNTHETIC_FAILURE', @now;

EXEC ctl.usp_place_staging_legal_hold
    @hold, @held, N'LEGAL_REVIEW', @hold_evidence, N'compliance';

EXEC ctl.usp_approve_staging_retention_policy
    @policy, N'LOCAL_SHADOW', @source, @tenant, @entity, N'NON_PUBLISHED_TERMINAL',
    N'synthetic-retention-v1', @policy_fingerprint, 1,
    @data_owner_evidence, N'data-owner', @compliance_evidence, N'compliance';

DECLARE @policy_retry TABLE (policy_id UNIQUEIDENTIFIER, exact_retry BIT);
BEGIN TRY
    EXECUTE AS USER = N'v2_retention_governor_probe';
    INSERT INTO @policy_retry
    EXEC ctl.usp_approve_staging_retention_policy
        @policy, N'LOCAL_SHADOW', @source, @tenant, @entity,
        N'NON_PUBLISHED_TERMINAL', N'synthetic-retention-v1',
        @policy_fingerprint, 1,
        @data_owner_evidence, N'data-owner', @compliance_evidence, N'compliance';
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME() = N'v2_retention_governor_probe' REVERT;
    THROW;
END CATCH;
IF (SELECT COUNT_BIG(*) FROM @policy_retry WHERE policy_id = @policy AND exact_retry = 1) <> 1
    THROW 51611, N'Role de governança não executou retry exato da policy.', 1;

DECLARE @stage_rows_before BIGINT = (SELECT COUNT_BIG(*) FROM stg.execution_record);
DECLARE @candidate_rows_before BIGINT = (SELECT COUNT_BIG(*) FROM stg.execution_candidate);
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
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @plan, @policy, N'synthetic-retention-v1', @policy_fingerprint,
    N'LOCAL_SHADOW', @source, @tenant, @entity,
    NULL, NULL,
    10, 10, 100, 100, 100, 100, 5000, 100000;

IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE examined_executions = 2 AND scan_truncated = 0
      AND eligible_executions = 1 AND held_executions = 1
      AND lease_blocked_executions = 0 AND oversized_executions = 0
      AND deferred_executions = 0 AND selected_executions = 1
      AND stage_rows = 2 AND candidate_rows = 1 AND evidence_rows > 0
      AND planned_content_root_version = N'archive-content-set-v1'
      AND LEN(planned_content_root) = 64 AND exact_retry = 0) <> 1
    THROW 51582, N'O dry-run não separou elegível, hold, recente e não terminal.', 1;
IF (SELECT COUNT_BIG(*) FROM stg.execution_record) <> @stage_rows_before
    OR (SELECT COUNT_BIG(*) FROM stg.execution_candidate) <> @candidate_rows_before
    THROW 51583, N'O dry-run alterou staging.', 1;
IF EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
           WHERE plan_id = @plan AND execution_id <> @eligible)
    THROW 51584, N'O plano selecionou execução fora do escopo elegível.', 1;

DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle
    @plan, @policy, N'synthetic-retention-v1', @policy_fingerprint,
    N'LOCAL_SHADOW', @source, @tenant, @entity,
    NULL, NULL,
    10, 10, 100, 100, 100, 100, 5000, 100000;
IF (SELECT COUNT_BIG(*) FROM @plan_result WHERE exact_retry = 1) <> 1
    THROW 51585, N'Retry exato do dry-run não foi reconhecido.', 1;

DELETE FROM @plan_result;
BEGIN TRY
    EXECUTE AS USER = N'v2_lifecycle_reviewer_probe';
    INSERT INTO @plan_result
    EXEC stg.usp_plan_staging_lifecycle
        @plan, @policy, N'synthetic-retention-v1', @policy_fingerprint,
        N'LOCAL_SHADOW', @source, @tenant, @entity,
        NULL, NULL,
        10, 10, 100, 100, 100, 100, 5000, 100000;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME() = N'v2_lifecycle_reviewer_probe' REVERT;
    THROW;
END CATCH;
IF (SELECT COUNT_BIG(*) FROM @plan_result WHERE exact_retry = 1) <> 1
    THROW 51612, N'Role de revisão não executou retry exato do plano.', 1;

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
    @plan, N'synthetic-retention-v1', @policy_fingerprint;
IF (SELECT COUNT_BIG(*) FROM @archive_result
    WHERE archived_executions = 1 AND archived_stage_rows = 2
      AND archived_candidate_rows = 1 AND archived_quarantine_rows = 1
      AND archived_application_rows = 1 AND archived_reconciliation_rows = 1
      AND archived_state_event_rows > 0 AND archived_page_audit_rows = 1
      AND archived_count_rows = 1 AND archived_publication_event_rows = 1
      AND archived_promotion_result_rows = 1
      AND content_root_version = N'archive-content-set-v1'
      AND LEN(content_root) = 64 AND exact_retry = 0) <> 1
    THROW 51586, N'O archive não preservou as dez fontes tipadas e seladas.', 1;

IF NOT EXISTS (SELECT 1 FROM recon.quarantine_record_archive WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_candidate_application_archive
                   WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_reconciliation_result_archive
                   WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_state_event_archive WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_page_audit_archive WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_count_archive WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_publication_event_archive WHERE plan_id = @plan)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_promotion_result_archive WHERE plan_id = @plan)
    THROW 51586, N'Uma fonte de evidência durável não foi arquivada.', 1;

DECLARE @archive_version NVARCHAR(128) =
    (SELECT TOP (1) archive_fingerprint_version FROM @archive_result);
DECLARE @archive_fingerprint CHAR(64) =
    (SELECT TOP (1) archive_fingerprint FROM @archive_result);

DELETE FROM @archive_result;
INSERT INTO @archive_result
EXEC stg.usp_archive_staging_lifecycle
    @plan, N'synthetic-retention-v1', @policy_fingerprint;
IF (SELECT COUNT_BIG(*) FROM @archive_result WHERE exact_retry = 1) <> 1
    THROW 51587, N'Retry exato do archive não revalidou o conteúdo.', 1;

DELETE FROM @archive_result;
BEGIN TRY
    EXECUTE AS USER = N'v2_lifecycle_operator_probe';
    INSERT INTO @archive_result
    EXEC stg.usp_archive_staging_lifecycle
        @plan, N'synthetic-retention-v1', @policy_fingerprint;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME() = N'v2_lifecycle_operator_probe' REVERT;
    THROW;
END CATCH;
IF (SELECT COUNT_BIG(*) FROM @archive_result WHERE exact_retry = 1) <> 1
    THROW 51613, N'Role de operação não executou retry exato do archive.', 1;

DECLARE @purge_result TABLE (
    plan_id UNIQUEIDENTIFIER, purged_executions BIGINT, purged_stage_rows BIGINT,
    purged_candidate_rows BIGINT, purged_at_utc DATETIME2(3), exact_retry BIT
);
INSERT INTO @purge_result
EXEC stg.usp_purge_staging_lifecycle
    @plan, N'synthetic-retention-v1', @policy_fingerprint,
    @archive_version, @archive_fingerprint;
IF (SELECT COUNT_BIG(*) FROM @purge_result
    WHERE purged_executions = 1 AND purged_stage_rows = 2
      AND purged_candidate_rows = 1 AND exact_retry = 0) <> 1
    THROW 51588, N'O purge não removeu exatamente o staging arquivado.', 1;
IF EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @eligible)
    OR EXISTS (SELECT 1 FROM stg.execution_candidate WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @held)
    OR NOT EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @too_recent)
    OR NOT EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @non_terminal)
    THROW 51589, N'O purge ultrapassou o conjunto terminal autorizado.', 1;
IF NOT EXISTS (SELECT 1 FROM recon.quarantine_record WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_candidate_application
                   WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM recon.execution_reconciliation_result
                   WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM ctl.execution_state_event WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM ctl.execution_page_audit WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM ctl.execution_count WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM ctl.execution_publication_event WHERE execution_id = @eligible)
    OR NOT EXISTS (SELECT 1 FROM ctl.execution_promotion_result WHERE execution_id = @eligible)
    OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE record_state_id = @record_state_id
          AND last_promoted_execution_id = @eligible
    )
    THROW 51616, N'O purge removeu evidência durável ou estado core.', 1;

DELETE FROM @purge_result;
INSERT INTO @purge_result
EXEC stg.usp_purge_staging_lifecycle
    @plan, N'synthetic-retention-v1', @policy_fingerprint,
    @archive_version, @archive_fingerprint;
IF (SELECT COUNT_BIG(*) FROM @purge_result WHERE exact_retry = 1) <> 1
    THROW 51590, N'Retry exato do purge não devolveu o receipt imutável.', 1;

DELETE FROM @purge_result;
BEGIN TRY
    EXECUTE AS USER = N'v2_lifecycle_operator_probe';
    INSERT INTO @purge_result
    EXEC stg.usp_purge_staging_lifecycle
        @plan, N'synthetic-retention-v1', @policy_fingerprint,
        @archive_version, @archive_fingerprint;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME() = N'v2_lifecycle_operator_probe' REVERT;
    THROW;
END CATCH;
IF (SELECT COUNT_BIG(*) FROM @purge_result WHERE exact_retry = 1) <> 1
    THROW 51614, N'Role de operação não executou retry exato do purge.', 1;

EXEC ctl.usp_place_staging_legal_hold
    @archive_hold, @eligible, N'ARCHIVE_LITIGATION', @hold_evidence, N'compliance';
IF NOT EXISTS (
    SELECT 1 FROM ctl.staging_legal_hold
    WHERE hold_id = @archive_hold AND execution_id = @eligible
      AND hold_scope = N'ARCHIVE_ONLY' AND released_at_utc IS NULL
)
    THROW 51590, N'Legal hold superveniente não protegeu o archive pós-purge.', 1;

DECLARE @restore_result TABLE (
    restore_id UNIQUEIDENTIFIER, plan_id UNIQUEIDENTIFIER, execution_id UNIQUEIDENTIFIER,
    restored_stage_rows BIGINT, restored_candidate_rows BIGINT,
    restored_at_utc DATETIME2(3), read_only BIT, exact_retry BIT
);
INSERT INTO @restore_result
EXEC recon.usp_restore_staging_archive
    @restore, @plan, @eligible, @archive_version, @archive_fingerprint,
    100, 100, N'RESTORE_TEST';
IF (SELECT COUNT_BIG(*) FROM @restore_result
    WHERE restored_stage_rows = 2 AND restored_candidate_rows = 1
      AND read_only = 1 AND exact_retry = 0) <> 1
    THROW 51591, N'O restore read-only não reproduziu o archive.', 1;
IF EXISTS (SELECT 1 FROM stg.execution_record WHERE execution_id = @eligible)
    OR EXISTS (SELECT 1 FROM stg.execution_candidate WHERE execution_id = @eligible)
    THROW 51592, N'O restore repopulou staging ativo.', 1;

DELETE FROM @restore_result;
INSERT INTO @restore_result
EXEC recon.usp_restore_staging_archive
    @restore, @plan, @eligible, @archive_version, @archive_fingerprint,
    100, 100, N'RESTORE_TEST';
IF (SELECT COUNT_BIG(*) FROM @restore_result WHERE read_only = 1 AND exact_retry = 1) <> 1
    THROW 51593, N'Retry exato do restore não revalidou o read-back.', 1;

DELETE FROM @restore_result;
BEGIN TRY
    EXECUTE AS USER = N'v2_archive_restorer_probe';
    INSERT INTO @restore_result
    EXEC recon.usp_restore_staging_archive
        @restore, @plan, @eligible, @archive_version, @archive_fingerprint,
        100, 100, N'RESTORE_TEST';
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME() = N'v2_archive_restorer_probe' REVERT;
    THROW;
END CATCH;
IF (SELECT COUNT_BIG(*) FROM @restore_result WHERE read_only = 1 AND exact_retry = 1) <> 1
    THROW 51615, N'Role de restore não executou retry exato do read-back.', 1;

EXEC ctl.usp_release_staging_legal_hold @hold, @hold_evidence, N'compliance';
EXEC ctl.usp_release_staging_legal_hold @archive_hold, @hold_evidence, N'compliance';
IF (SELECT COUNT_BIG(*) FROM ctl.staging_legal_hold_event
    WHERE hold_id = @hold AND event_action IN (N'PLACED', N'RELEASED')) <> 2
    THROW 51594, N'A trilha append-only do legal hold não foi preservada.', 1;

-- Fronteira determinística real: antes e exatamente no cutoff entram; +1 ms não entra.
DECLARE @boundary_source NVARCHAR(128) = N'SYNTHETIC_BOUNDARY_SOURCE';
DECLARE @boundary_tenant NVARCHAR(128) = N'SYNTHETIC_BOUNDARY_TENANT';
DECLARE @boundary_entity NVARCHAR(128) = N'SYNTHETIC_BOUNDARY_ENTITY';
DECLARE @boundary_policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000520';
DECLARE @boundary_plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000521';
DECLARE @before UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000522';
DECLARE @equal UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000523';
DECLARE @after UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000524';
DECLARE @boundary_clock DATETIME2(3) = DATEADD(HOUR, -6, @now);
DECLARE @boundary_cutoff DATETIME2(3) = DATEADD(DAY, -1, @boundary_clock);
DECLARE @boundary_policy_fingerprint CHAR(64) = REPLICATE('4', 64);

EXEC ctl.usp_control_plane_register_source @boundary_source, N'SYNTHETIC', @old_start;

DECLARE @boundary_execution TABLE (
    execution_id UNIQUEIDENTIFIER NOT NULL,
    terminal_at_utc DATETIME2(3) NOT NULL,
    partition_start_utc DATETIME2(3) NOT NULL,
    partition_end_utc DATETIME2(3) NOT NULL,
    idempotency_key NVARCHAR(128) NOT NULL
);
INSERT INTO @boundary_execution VALUES
    (@before, DATEADD(MILLISECOND, -1, @boundary_cutoff), DATEADD(HOUR, -3, @now),
        DATEADD(HOUR, -2, @now), N'synthetic-boundary-before'),
    (@equal, @boundary_cutoff, DATEADD(HOUR, -2, @now),
        DATEADD(HOUR, -1, @now), N'synthetic-boundary-equal'),
    (@after, DATEADD(MILLISECOND, 1, @boundary_cutoff), DATEADD(HOUR, -1, @now),
        @now, N'synthetic-boundary-after');

DECLARE @boundary_id UNIQUEIDENTIFIER;
DECLARE @boundary_terminal DATETIME2(3);
DECLARE @partition_start DATETIME2(3);
DECLARE @partition_end DATETIME2(3);
DECLARE @idempotency_key NVARCHAR(128);
DECLARE boundary_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT execution_id, terminal_at_utc, partition_start_utc,
           partition_end_utc, idempotency_key
    FROM @boundary_execution ORDER BY terminal_at_utc;
OPEN boundary_cursor;
FETCH NEXT FROM boundary_cursor INTO @boundary_id, @boundary_terminal,
    @partition_start, @partition_end, @idempotency_key;
WHILE @@FETCH_STATUS = 0
BEGIN
    INSERT INTO ctl.execution_partition (
        environment_name, source_instance, tenant_scope, entity_name, execution_mode,
        partition_start_utc, partition_end_exclusive_utc, current_execution_id,
        current_state, next_attempt_number, created_at_utc
    ) VALUES (
        N'LOCAL_SHADOW', @boundary_source, @boundary_tenant, @boundary_entity, N'BACKFILL',
        @partition_start, @partition_end, NULL, N'FAILED', 2, @old_start
    );
    DECLARE @boundary_partition BIGINT = SCOPE_IDENTITY();
    INSERT INTO ctl.execution_attempt (
        execution_id, partition_id, cycle_id, attempt_number, window_strategy,
        contract_version, contract_fingerprint, configuration_version,
        configuration_fingerprint, idempotency_key, replay_of_execution_id,
        current_state, next_transition_sequence, started_at_utc, terminal_at_utc
    ) VALUES (
        @boundary_id, @boundary_partition, @cycle, 1, N'SYNTHETIC_INTERVAL',
        N'contract-v1', @contract_fingerprint, N'config-v1',
        @configuration_fingerprint, @idempotency_key, NULL,
        N'FAILED', 4, @old_start, @boundary_terminal
    );
    UPDATE ctl.execution_partition
    SET current_execution_id = @boundary_id
    WHERE partition_id = @boundary_partition;
    INSERT INTO ctl.execution_state_event (
        execution_id, transition_sequence, previous_state, next_state,
        reason_code, transitioned_at_utc
    ) VALUES
        (@boundary_id, 1, NULL, N'PLANNED', N'EXECUTION_PLANNED', @old_start),
        (@boundary_id, 2, N'PLANNED', N'EXTRACTING', N'LEASE_ACQUIRED', @old_start),
        (@boundary_id, 3, N'EXTRACTING', N'FAILED', N'SYNTHETIC_FAILURE',
            @boundary_terminal);
    INSERT INTO stg.execution_record (
        execution_id, input_batch_number, input_record_ordinal, source_key,
        row_fingerprint_version, source_row_hash, presence_fingerprint_version,
        presence_fingerprint, source_freshness_at_utc, validation_disposition,
        quarantine_reason_code, staged_at_utc
    ) VALUES (@boundary_id, 1, 1, CONVERT(NVARCHAR(36), @boundary_id),
              N'row-v1', @row_hash, N'presence-v1', @presence_hash,
              @boundary_terminal, N'VALID', NULL, @boundary_terminal);

    FETCH NEXT FROM boundary_cursor INTO @boundary_id, @boundary_terminal,
        @partition_start, @partition_end, @idempotency_key;
END;
CLOSE boundary_cursor;
DEALLOCATE boundary_cursor;

EXEC ctl.usp_approve_staging_retention_policy
    @boundary_policy, N'LOCAL_SHADOW', @boundary_source, @boundary_tenant,
    @boundary_entity, N'NON_PUBLISHED_TERMINAL', N'boundary-retention-v1',
    @boundary_policy_fingerprint, 1, @data_owner_evidence, N'data-owner',
    @compliance_evidence, N'compliance';

DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @boundary_plan, @boundary_policy, N'boundary-retention-v1',
    @boundary_policy_fingerprint, N'LOCAL_SHADOW', @boundary_source,
    @boundary_tenant, @boundary_entity, NULL, NULL,
    10, 10, 10, 10, 20, 20, 1000,
    100000, @boundary_clock;
IF NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
               WHERE plan_id = @boundary_plan AND execution_id = @before)
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @boundary_plan AND execution_id = @equal)
    OR EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
               WHERE plan_id = @boundary_plan AND execution_id = @after)
    OR (SELECT COUNT_BIG(*) FROM ctl.staging_lifecycle_plan_item
        WHERE plan_id = @boundary_plan) <> 2
    THROW 51595, N'A procedure não preservou a fronteira fechada real do cutoff.', 1;

-- Um item acima do cap é classificado e não impede o item menor seguinte na mesma página.
INSERT INTO stg.execution_record (
    execution_id, input_batch_number, input_record_ordinal, source_key,
    row_fingerprint_version, source_row_hash, presence_fingerprint_version,
    presence_fingerprint, source_freshness_at_utc, validation_disposition,
    quarantine_reason_code, staged_at_utc
) VALUES (
    @before, 1, 2, N'synthetic-boundary-before-extra',
    N'row-v1', @row_hash, N'presence-v1', @presence_hash,
    @boundary_cutoff, N'VALID', NULL, @boundary_cutoff
);
DECLARE @oversized_plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000525';
DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @oversized_plan, @boundary_policy, N'boundary-retention-v1',
    @boundary_policy_fingerprint, N'LOCAL_SHADOW', @boundary_source,
    @boundary_tenant, @boundary_entity, NULL, NULL,
    1, 2, 10, 10, 20, 4, 1000, 100000, @boundary_clock;
IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE oversized_executions = 1 AND eligible_executions = 1
      AND selected_executions = 1) <> 1
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @oversized_plan AND execution_id = @equal)
    OR EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
               WHERE plan_id = @oversized_plan AND execution_id = @before)
    THROW 51599, N'Item acima de maximum_content_rows bloqueou item menor.', 1;

-- Cursor persistido permite continuar depois de uma página integralmente bloqueada por hold.
DECLARE @boundary_hold UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000526';
DECLARE @cursor_plan_one UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000527';
DECLARE @cursor_plan_two UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000528';
EXEC ctl.usp_place_staging_legal_hold
    @boundary_hold, @before, N'BOUNDARY_HOLD', @hold_evidence, N'compliance';
DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @cursor_plan_one, @boundary_policy, N'boundary-retention-v1',
    @boundary_policy_fingerprint, N'LOCAL_SHADOW', @boundary_source,
    @boundary_tenant, @boundary_entity, NULL, NULL,
    1, 1, 10, 10, 20, 20, 1000, 100000, @boundary_clock;
DECLARE @next_terminal DATETIME2(3) = (
    SELECT next_scan_after_terminal_at_utc FROM @plan_result
);
DECLARE @next_execution UNIQUEIDENTIFIER = (
    SELECT next_scan_after_execution_id FROM @plan_result
);
IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE examined_executions = 1 AND scan_truncated = 1
      AND held_executions = 1 AND selected_executions = 0
      AND next_scan_after_terminal_at_utc IS NOT NULL
      AND next_scan_after_execution_id = @before) <> 1
    THROW 51600, N'Plano truncado não emitiu cursor depois do legal hold.', 1;

DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @cursor_plan_two, @boundary_policy, N'boundary-retention-v1',
    @boundary_policy_fingerprint, N'LOCAL_SHADOW', @boundary_source,
    @boundary_tenant, @boundary_entity, @next_terminal, @next_execution,
    1, 1, 10, 10, 20, 20, 1000, 100000, @boundary_clock;
IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE scan_after_terminal_at_utc = @next_terminal
      AND scan_after_execution_id = @next_execution
      AND examined_executions = 1 AND scan_truncated = 0
      AND selected_executions = 1) <> 1
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @cursor_plan_two AND execution_id = @equal)
    THROW 51601, N'Continuação por cursor não avançou após o legal hold.', 1;
EXEC ctl.usp_release_staging_legal_hold @boundary_hold, @hold_evidence, N'compliance';

-- Lease ainda não liberada bloqueia somente sua execução; a seguinte continua elegível.
DECLARE @boundary_lease UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000529';
DECLARE @lease_plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000530';
DECLARE @before_partition BIGINT = (
    SELECT partition_id FROM ctl.execution_attempt WHERE execution_id = @before
);
INSERT INTO ctl.execution_lease (
    lease_id, partition_id, execution_id, acquired_at_utc,
    heartbeat_at_utc, expires_at_utc, released_at_utc
) VALUES (
    @boundary_lease, @before_partition, @before, @old_start,
    @old_start, DATEADD(HOUR, 1, @now), NULL
);
DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @lease_plan, @boundary_policy, N'boundary-retention-v1',
    @boundary_policy_fingerprint, N'LOCAL_SHADOW', @boundary_source,
    @boundary_tenant, @boundary_entity, NULL, NULL,
    1, 2, 10, 10, 20, 20, 1000, 100000, @boundary_clock;
IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE lease_blocked_executions = 1 AND selected_executions = 1) <> 1
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @lease_plan AND execution_id = @equal)
    THROW 51602, N'Lease ativa bloqueou execução não relacionada.', 1;
UPDATE ctl.execution_lease SET released_at_utc = @now WHERE lease_id = @boundary_lease;

-- A classe PUBLISHED possui política e seleção próprias, com TTL explicitamente selado.
DECLARE @published_source NVARCHAR(128) = N'SYNTHETIC_PUBLISHED_SOURCE';
DECLARE @published_tenant NVARCHAR(128) = N'SYNTHETIC_PUBLISHED_TENANT';
DECLARE @published_entity NVARCHAR(128) = N'SYNTHETIC_PUBLISHED_ENTITY';
DECLARE @published_execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000531';
DECLARE @published_policy UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000532';
DECLARE @published_plan UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000533';
DECLARE @published_policy_fingerprint CHAR(64) = REPLICATE('6', 64);
EXEC ctl.usp_control_plane_register_source @published_source, N'SYNTHETIC', @old_start;
INSERT INTO ctl.execution_partition (
    environment_name, source_instance, tenant_scope, entity_name, execution_mode,
    partition_start_utc, partition_end_exclusive_utc, current_execution_id,
    current_state, next_attempt_number, created_at_utc
) VALUES (
    N'LOCAL_SHADOW', @published_source, @published_tenant, @published_entity, N'BACKFILL',
    DATEADD(HOUR, -10, @now), DATEADD(HOUR, -9, @now), NULL,
    N'PUBLISHED', 2, @old_start
);
DECLARE @published_partition BIGINT = SCOPE_IDENTITY();
INSERT INTO ctl.execution_attempt (
    execution_id, partition_id, cycle_id, attempt_number, window_strategy,
    contract_version, contract_fingerprint, configuration_version,
    configuration_fingerprint, idempotency_key, replay_of_execution_id,
    current_state, next_transition_sequence, started_at_utc, terminal_at_utc
) VALUES (
    @published_execution, @published_partition, @cycle, 1, N'SYNTHETIC_INTERVAL',
    N'contract-v1', @contract_fingerprint, N'config-v1', @configuration_fingerprint,
    N'synthetic-published-lifecycle', NULL, N'PUBLISHED', 8, @old_start, @old_terminal
);
UPDATE ctl.execution_partition
SET current_execution_id = @published_execution
WHERE partition_id = @published_partition;
INSERT INTO ctl.execution_state_event (
    execution_id, transition_sequence, previous_state, next_state,
    reason_code, transitioned_at_utc
) VALUES
    (@published_execution, 1, NULL, N'PLANNED', N'EXECUTION_PLANNED', @old_start),
    (@published_execution, 2, N'PLANNED', N'EXTRACTING', N'LEASE_ACQUIRED', @old_start),
    (@published_execution, 3, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @old_start),
    (@published_execution, 4, N'EXTRACTED', N'STAGED', N'STAGE_OK', @old_start),
    (@published_execution, 5, N'STAGED', N'PROMOTED', N'CANDIDATE_SET_PREPARED', @old_start),
    (@published_execution, 6, N'PROMOTED', N'RECONCILED',
        N'CANDIDATE_SET_RECONCILED', @old_start),
    (@published_execution, 7, N'RECONCILED', N'PUBLISHED',
        N'RECONCILIATION_PUBLISHED', @old_terminal);
INSERT INTO stg.execution_record (
    execution_id, input_batch_number, input_record_ordinal, source_key,
    row_fingerprint_version, source_row_hash, presence_fingerprint_version,
    presence_fingerprint, source_freshness_at_utc, validation_disposition,
    quarantine_reason_code, staged_at_utc
) VALUES (
    @published_execution, 1, 1, N'synthetic-published-key',
    N'row-v1', @row_hash, N'presence-v1', @presence_hash,
    @old_terminal, N'VALID', NULL, @old_terminal
);
EXEC ctl.usp_approve_staging_retention_policy
    @published_policy, N'LOCAL_SHADOW', @published_source, @published_tenant,
    @published_entity, N'PUBLISHED', N'published-retention-v1',
    @published_policy_fingerprint, 1, @data_owner_evidence, N'data-owner',
    @compliance_evidence, N'compliance';
DELETE FROM @plan_result;
INSERT INTO @plan_result
EXEC stg.usp_plan_staging_lifecycle_at
    @published_plan, @published_policy, N'published-retention-v1',
    @published_policy_fingerprint, N'LOCAL_SHADOW', @published_source,
    @published_tenant, @published_entity, NULL, NULL,
    1, 1, 10, 10, 10, 10, 1000, 100000, @boundary_clock;
IF (SELECT COUNT_BIG(*) FROM @plan_result
    WHERE selected_executions = 1 AND oversized_executions = 0) <> 1
    OR NOT EXISTS (SELECT 1 FROM ctl.staging_lifecycle_plan_item
                   WHERE plan_id = @published_plan AND execution_id = @published_execution)
    THROW 51603, N'Classe PUBLISHED não foi planejada por sua política própria.', 1;

-- Corrupção posterior ao purge deve bloquear até mesmo um retry/read-back de restore.
UPDATE recon.staging_record_archive
SET source_row_hash = REPLICATE('5', 64)
WHERE plan_id = @plan AND execution_id = @eligible AND source_key = N'eligible-key';

DECLARE @corruption_error INT = NULL;
BEGIN TRY
    DELETE FROM @restore_result;
    INSERT INTO @restore_result
    EXEC recon.usp_restore_staging_archive
        @restore, @plan, @eligible, @archive_version, @archive_fingerprint,
        100, 100, N'RESTORE_TEST';
END TRY
BEGIN CATCH
    SET @corruption_error = ERROR_NUMBER();
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @corruption_error IS NULL OR @corruption_error <> 51539
    THROW 51596, N'Corrupção do archive não foi bloqueada pelo selo independente.', 1;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51597, N'O rollback do lifecycle não encerrou o escopo sintético.', 1;

PRINT N'Lifecycle de staging exercitado, corrupção bloqueada e estado revertido com sucesso.';

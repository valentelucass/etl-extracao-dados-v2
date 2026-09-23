-- Exercício sintético e rollback-only de V2-010/V2-015e. O result set tipado de
-- cada promoção é emitido para captura/assertiva pelo cliente SQLCMD; nenhum dado
-- ou DDL persiste.
-- A baseline abaixo materializa V010__create_coletas_shadow_vertical.sql.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51872, N'O exercício de Coletas aceita somente ETL_SISTEMA_V2_SHADOW.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "005_validate_progressive_data_gate.sql"
GO
:r "038_validate_coletas_shadow_vertical.sql"

IF OBJECT_ID(N'stg.usp_stage_coleta_record', N'P') IS NULL
   OR OBJECT_ID(N'core.usp_apply_reconcile_publish_coletas', N'P') IS NULL
   OR OBJECT_ID(N'core.coleta', N'U') IS NULL
   OR OBJECT_ID(N'ref.coleta_sequence_code_alias', N'U') IS NULL
   OR OBJECT_ID(N'recon.coleta_root_presence_observation', N'U') IS NULL
    THROW 51873, N'V010 não materializou a vertical de Coletas.', 1;
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COLETAS_6908';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('a', 64);
EXEC ctl.usp_control_plane_register_source @source, N'DATA_EXPORT', @now;
EXEC ctl.usp_control_plane_start_cycle
    '00000000-0000-0000-0000-000000001001', N'synthetic-coletas-plan-v1',
    @plan_fingerprint, @now;
GO

-- Política local ratificada somente dentro da transação rollback-only.
DECLARE @policy_effective DATETIME2(3) = DATEADD(DAY, -1, SYSUTCDATETIME());
DECLARE @policy_environment NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @policy_source NVARCHAR(128) = N'SYNTHETIC_COLETAS_6908';
DECLARE @policy_tenant NVARCHAR(128) = N'SYNTHETIC_COLETAS_TENANT';
DECLARE @policy_entity NVARCHAR(128) = N'coletas';
DECLARE @policy_mode NVARCHAR(16) = N'BACKFILL';
DECLARE @scope_fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-scope-v1|', DATALENGTH(@policy_environment), N':', @policy_environment,
        N'|', DATALENGTH(@policy_source), N':', @policy_source,
        N'|', DATALENGTH(@policy_tenant), N':', @policy_tenant,
        N'|', DATALENGTH(@policy_entity), N':', @policy_entity,
        N'|', DATALENGTH(@policy_mode), N':', @policy_mode))), 2));
DECLARE @policy_version NVARCHAR(128) = N'synthetic-coletas-dq-backfill-v1';
DECLARE @policy_fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-policy-v1|', DATALENGTH(@policy_version), N':', @policy_version,
        N'|', @scope_fingerprint, N'|4|3600|',
        DATALENGTH(N'quality-owner'), N':quality-owner|',
        DATALENGTH(N'quarantine-owner'), N':quarantine-owner|',
        DATALENGTH(N'synthetic-coletas-retention-v1'), N':synthetic-coletas-retention-v1|',
        DATALENGTH(N'retention-owner'), N':retention-owner|',
        CONVERT(NVARCHAR(33), @policy_effective, 126),
        N'|1:COUNT_EQUATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|2:PAGE_TERMINALITY:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|4:QUARANTINE_SLA:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner'))), 2));
INSERT INTO ctl.data_quality_policy VALUES (
    @policy_version, @policy_fingerprint, @scope_fingerprint, 4, 3600,
    N'quality-owner', N'quarantine-owner', N'synthetic-coletas-retention-v1',
    N'retention-owner', N'RATIFIED', @policy_effective, SYSUTCDATETIME());
INSERT INTO ctl.data_quality_check_policy VALUES
    (@policy_version, @policy_fingerprint, 1, N'COUNT_EQUATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 2, N'PAGE_TERMINALITY', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 3, N'PROMOTION_RECONCILIATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 4, N'QUARANTINE_SLA', 0, 0, N'quality-owner');
SET @policy_mode = N'REPLAY';
SET @scope_fingerprint = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-scope-v1|', DATALENGTH(@policy_environment), N':', @policy_environment,
        N'|', DATALENGTH(@policy_source), N':', @policy_source,
        N'|', DATALENGTH(@policy_tenant), N':', @policy_tenant,
        N'|', DATALENGTH(@policy_entity), N':', @policy_entity,
        N'|', DATALENGTH(@policy_mode), N':', @policy_mode))), 2));
SET @policy_version = N'synthetic-coletas-dq-replay-v1';
SET @policy_fingerprint = LOWER(CONVERT(CHAR(64), HASHBYTES(
    'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
        N'dq-policy-v1|', DATALENGTH(@policy_version), N':', @policy_version,
        N'|', @scope_fingerprint, N'|4|3600|',
        DATALENGTH(N'quality-owner'), N':quality-owner|',
        DATALENGTH(N'quarantine-owner'), N':quarantine-owner|',
        DATALENGTH(N'synthetic-coletas-retention-v1'), N':synthetic-coletas-retention-v1|',
        DATALENGTH(N'retention-owner'), N':retention-owner|',
        CONVERT(NVARCHAR(33), @policy_effective, 126),
        N'|1:COUNT_EQUATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|2:PAGE_TERMINALITY:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
        N'|4:QUARANTINE_SLA:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner'))), 2));
INSERT INTO ctl.data_quality_policy VALUES (
    @policy_version, @policy_fingerprint, @scope_fingerprint, 4, 3600,
    N'quality-owner', N'quarantine-owner', N'synthetic-coletas-retention-v1',
    N'retention-owner', N'RATIFIED', @policy_effective, SYSUTCDATETIME());
INSERT INTO ctl.data_quality_check_policy VALUES
    (@policy_version, @policy_fingerprint, 1, N'COUNT_EQUATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 2, N'PAGE_TERMINALITY', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 3, N'PROMOTION_RECONCILIATION', 0, 0, N'quality-owner'),
    (@policy_version, @policy_fingerprint, 4, N'QUARANTINE_SLA', 0, 0, N'quality-owner');
GO

CREATE OR ALTER PROCEDURE #run_coleta
    @execution_id UNIQUEIDENTIFIER,
    @start DATETIME2(3),
    @finish DATETIME2(3),
    @idempotency NVARCHAR(128),
    @source_key NVARCHAR(256),
    @payload NVARCHAR(MAX),
    @status_code NVARCHAR(32),
    @status_label NVARCHAR(64),
    @terminal BIT,
    @occurrence_action NVARCHAR(2048),
    @attempt_count SMALLINT,
    @freshness DATETIME2(3),
    @mode NVARCHAR(16) = N'BACKFILL',
    @replay_of UNIQUEIDENTIFIER = NULL,
    @sequence_code_json NVARCHAR(MAX) = N'{"value":6908}'
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COLETAS_6908';
    DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_COLETAS_TENANT';
    DECLARE @entity NVARCHAR(128) = N'coletas';
    DECLARE @contract CHAR(64) = REPLICATE('b', 64);
    DECLARE @configuration CHAR(64) = REPLICATE('c', 64);
    DECLARE @freshness_raw NVARCHAR(33) = CONVERT(NVARCHAR(33), @freshness, 126);
    DECLARE @scope CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'dq-scope-v1|', DATALENGTH(@environment), N':', @environment,
            N'|', DATALENGTH(@source), N':', @source,
            N'|', DATALENGTH(@tenant), N':', @tenant,
            N'|', DATALENGTH(@entity), N':', @entity,
            N'|', DATALENGTH(@mode), N':', @mode))), 2));
    DECLARE @policy_version NVARCHAR(128), @policy_fingerprint CHAR(64);
    SELECT @policy_version = policy_version, @policy_fingerprint = policy_fingerprint
    FROM ctl.data_quality_policy WHERE scope_fingerprint = @scope;

    EXEC ctl.usp_control_plane_start_execution
        @execution_id, '00000000-0000-0000-0000-000000001001',
        @environment, @source, @tenant, @entity, @mode, @start, @finish,
        N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6908-v02', @contract,
        N'coletas-shadow-v1', @configuration, @idempotency, @replay_of, 3600, @now;
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 1, 1, 100, 1, 1, 512, 0, @now, N'NONE';
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 2, 1, 100, 0, 0, 64, 1, @now, N'DATA_EXPORT_EMPTY_PAGE';
    EXEC stg.usp_stage_coleta_record
        @execution_id, 1, 1, @source_key, N'INTEGER', N'VALUE', @sequence_code_json,
        @payload, N'{"sequence_code":"VALUE","status":"VALUE"}', N'{}',
        @status_code, @status_code, @status_label, N'coletas-status-v1', @terminal,
        @occurrence_action, @attempt_count, @freshness_raw,
        @freshness, N'STATUS_UPDATED_AT', N'VALID', NULL, @now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
    EXEC core.usp_prepare_staged_execution
        @execution_id, N'dataexport-6908-v02', @contract,
        N'coletas-shadow-v1', @configuration;
    IF NOT EXISTS (
        SELECT 1 FROM ctl.coleta_promotion_result
        WHERE execution_id = @execution_id AND validation_state = N'PASSED'
          AND generic_candidate_rows = 1 AND typed_candidate_rows = 1
    )
        THROW 51874, N'Candidate set tipado de Coletas não passou.', 1;

    DECLARE @dq TABLE (
        execution_id UNIQUEIDENTIFIER, policy_version NVARCHAR(128),
        policy_fingerprint CHAR(64), evaluation_fingerprint CHAR(64),
        expected_checks SMALLINT, completed_checks SMALLINT, passed_checks SMALLINT,
        failed_checks SMALLINT, evaluated_rows BIGINT, failed_rows BIGINT,
        evaluation_state NVARCHAR(16), evaluated_at_utc DATETIME2(3));
    INSERT INTO @dq EXEC recon.usp_evaluate_execution_data_quality
        @execution_id, @policy_version, @policy_fingerprint;
    IF NOT EXISTS (SELECT 1 FROM @dq WHERE evaluation_state = N'PASSED' AND failed_rows = 0)
        THROW 51875, N'Data Quality de Coletas não passou.', 1;

    -- Este result set tipado é deliberadamente capturado pela saída do SQLCMD.
    EXEC core.usp_apply_reconcile_publish_coletas
        @execution_id, N'dataexport-6908-v02', @contract,
        N'coletas-shadow-v1', @configuration;
END;
GO

DECLARE @pending UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001002';
DECLARE @retro_terminal UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001003';
EXEC #run_coleta
    @pending, '2036-01-01T00:00:00', '2036-01-01T01:00:00', N'coletas-pending-t2',
    N'INTEGER:6908', N'{"id":6908,"status":"pending","version":"T2"}',
    N'pending', N'Pendente', 0, NULL, 0, '2036-01-20T10:00:00';
EXEC #run_coleta
    @retro_terminal, '2036-01-01T01:00:00', '2036-01-01T02:00:00',
    N'coletas-done-t1-retroactive', N'INTEGER:6908',
    N'{"id":6908,"status":"done","version":"T1"}', N'done', N'Coletada', 1,
    N'Coleta Realizada', 1, '2036-01-10T10:00:00';

IF NOT EXISTS (
    SELECT 1 FROM core.coleta
    WHERE source_key = N'INTEGER:6908' AND status_code = N'done'
      AND status_label = N'Coletada' AND terminal = 1 AND attempt_count = 1
      AND occurrence_action = N'Coleta Realizada'
      AND last_changed_execution_id = @retro_terminal
) OR NOT EXISTS (
    SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id = @retro_terminal AND source_key = N'INTEGER:6908'
      AND application_disposition = N'STALE_NO_OP'
)
    THROW 51876, N'COL-03: terminal retroativo não venceu o estado aberto.', 1;

DECLARE @terminal_open UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001004';
DECLARE @terminal_replay UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001005';
EXEC #run_coleta
    @terminal_open, '2036-01-01T02:00:00', '2036-01-01T03:00:00',
    N'coletas-terminal-never-regresses', N'INTEGER:6908',
    N'{"id":6908,"status":"pending","version":"T3"}',
    N'pending', N'Pendente', 0, NULL, 0, '2036-01-30T10:00:00';
IF NOT EXISTS (
    SELECT 1 FROM core.coleta
    WHERE source_key = N'INTEGER:6908' AND status_code = N'done'
      AND status_label = N'Coletada' AND terminal = 1 AND attempt_count = 1
      AND occurrence_action = N'Coleta Realizada'
      AND last_changed_execution_id = @retro_terminal
      AND last_seen_execution_id = @terminal_open
) OR NOT EXISTS (
    SELECT 1 FROM recon.execution_candidate_application
    WHERE execution_id = @terminal_open AND source_key = N'INTEGER:6908'
      AND application_disposition = N'UPDATED'
)
    THROW 51877, N'COL-03: terminal persistido regrediu para aberto.', 1;

EXEC #run_coleta
    @terminal_replay, '2036-01-01T01:00:00', '2036-01-01T02:00:00',
    N'coletas-replay-terminal', N'INTEGER:6908',
    N'{"id":6908,"status":"done","version":"T1"}',
    N'done', N'Coletada', 1, N'Coleta Realizada', 1, '2036-01-10T10:00:00',
    N'REPLAY', @retro_terminal;
IF (
    SELECT COUNT_BIG(*) FROM core.coleta WHERE source_key = N'INTEGER:6908'
) <> 1 OR NOT EXISTS (
    SELECT 1 FROM core.coleta
    WHERE source_key = N'INTEGER:6908' AND status_code = N'done'
      AND terminal = 1 AND last_changed_execution_id = @retro_terminal
)
    THROW 51878, N'O replay do terminal aceito não foi idempotente.', 1;

DECLARE @open_t2 UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001006';
DECLARE @open_t1 UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001007';
EXEC #run_coleta
    @open_t2, '2036-01-01T03:00:00', '2036-01-01T04:00:00',
    N'coletas-open-t2', N'INTEGER:6909',
    N'{"id":6909,"status":"pending","version":"T2"}',
    N'pending', N'Pendente', 0, NULL, 0, '2036-01-20T10:00:00',
    N'BACKFILL', NULL, N'{"value":6909}';
EXEC #run_coleta
    @open_t1, '2036-01-01T04:00:00', '2036-01-01T05:00:00',
    N'coletas-open-t1-retroactive', N'INTEGER:6909',
    N'{"id":6909,"status":"pending","version":"T1"}',
    N'pending', N'Pendente', 0, NULL, 0, '2036-01-10T10:00:00',
    N'BACKFILL', NULL, N'{"value":6909}';
IF NOT EXISTS (
    SELECT 1 FROM core.coleta
    WHERE source_key = N'INTEGER:6909' AND status_code = N'pending'
      AND terminal = 0 AND freshness_at_utc = '2036-01-20T10:00:00'
      AND last_changed_execution_id = @open_t2
      AND last_seen_execution_id = @open_t1
)
    THROW 51879, N'Estado aberto retroativo não permaneceu STALE_NO_OP.', 1;

-- Empate divergente é quarentenado antes da promoção.
DECLARE @conflict UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001008';
DECLARE @run_now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @contract CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration CHAR(64) = REPLICATE('c', 64);
EXEC ctl.usp_control_plane_start_execution
    @conflict, '00000000-0000-0000-0000-000000001001',
    N'LOCAL_SHADOW', N'SYNTHETIC_COLETAS_6908', N'SYNTHETIC_COLETAS_TENANT',
    N'coletas', N'BACKFILL', '2036-01-01T05:00:00', '2036-01-01T06:00:00',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6908-v02', @contract,
    N'coletas-shadow-v1', @configuration, N'coletas-equal-conflict', NULL, 3600, @run_now;
EXEC ctl.usp_control_plane_record_page
    @conflict, 1, 1, 100, 2, 1, 1024, 0, @run_now, N'NONE';
EXEC ctl.usp_control_plane_record_page
    @conflict, 2, 1, 100, 0, 0, 64, 1, @run_now, N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_coleta_record
    @conflict, 1, 1, N'INTEGER:6910', N'INTEGER', N'VALUE', N'{"value":6910}',
    N'{"id":6910,"variant":"A"}', N'{"sequence_code":"VALUE","status":"VALUE"}',
    N'{}', N'pending', N'pending', N'Pendente', N'coletas-status-v1', 0, NULL, 0,
    N'2036-01-20T10:00:00', '2036-01-20T10:00:00', N'STATUS_UPDATED_AT',
    N'VALID', NULL, @run_now;
EXEC stg.usp_stage_coleta_record
    @conflict, 2, 1, N'INTEGER:6910', N'INTEGER', N'VALUE', N'{"value":6910}',
    N'{"id":6910,"variant":"B"}', N'{"sequence_code":"VALUE","status":"VALUE"}',
    N'{}', N'pending', N'pending', N'Pendente', N'coletas-status-v1', 0, NULL, 0,
    N'2036-01-20T10:00:00', '2036-01-20T10:00:00', N'STATUS_UPDATED_AT',
    N'VALID', NULL, @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict, N'EXTRACTED', N'STAGED', N'STAGING_OK', @run_now;
EXEC core.usp_prepare_staged_execution
    @conflict, N'dataexport-6908-v02', @contract, N'coletas-shadow-v1', @configuration;
IF NOT EXISTS (
    SELECT 1 FROM ctl.coleta_promotion_result
    WHERE execution_id = @conflict AND validation_state = N'BLOCKED'
) OR (
    SELECT COUNT_BIG(*) FROM recon.quarantine_record
    WHERE execution_id = @conflict AND reason_code = N'EQUAL_FRESHNESS_CONFLICT'
) <> 2 OR EXISTS (
    SELECT 1 FROM stg.execution_candidate WHERE execution_id = @conflict
)
    THROW 51880, N'Empate divergente de Coletas não falhou fechado.', 1;

-- Sidecar tipado ausente: candidate set genérico existe, mas a promoção fica bloqueada.
DECLARE @partial UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000001009';
DECLARE @partial_row_hash CHAR(64) = REPLICATE('d', 64);
DECLARE @partial_presence_hash CHAR(64) = REPLICATE('e', 64);
EXEC ctl.usp_control_plane_start_execution
    @partial, '00000000-0000-0000-0000-000000001001',
    N'LOCAL_SHADOW', N'SYNTHETIC_COLETAS_6908', N'SYNTHETIC_COLETAS_TENANT',
    N'coletas', N'BACKFILL', '2036-01-01T06:00:00', '2036-01-01T07:00:00',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6908-v02', @contract,
    N'coletas-shadow-v1', @configuration, N'coletas-partial-sidecar', NULL, 3600, @run_now;
EXEC ctl.usp_control_plane_record_page
    @partial, 1, 1, 100, 1, 1, 512, 0, @run_now, N'NONE';
EXEC ctl.usp_control_plane_record_page
    @partial, 2, 1, 100, 0, 0, 64, 1, @run_now, N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_record
    @partial, 1, 1, N'INTEGER:6911', N'coletas-envelope-v1', @partial_row_hash,
    N'coletas-presence-v1', @partial_presence_hash, '2036-01-20T10:00:00',
    N'VALID', NULL, @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @partial, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @partial, N'EXTRACTED', N'STAGED', N'STAGING_OK', @run_now;
EXEC core.usp_prepare_staged_execution
    @partial, N'dataexport-6908-v02', @contract, N'coletas-shadow-v1', @configuration;
IF NOT EXISTS (
    SELECT 1 FROM ctl.coleta_promotion_result
    WHERE execution_id = @partial AND generic_candidate_rows = 1
      AND typed_candidate_rows = 0 AND validation_state = N'BLOCKED'
) OR EXISTS (
    SELECT 1 FROM core.coleta WHERE source_key = N'INTEGER:6911'
)
    THROW 51881, N'Tentativa parcial sem sidecar tipado não falhou fechado.', 1;

ROLLBACK TRANSACTION;
PRINT N'Coletas V2-010/V2-015e: COL-03, replay, stale, conflito e tentativa parcial validados; rollback integral concluído.';

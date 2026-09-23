-- P02R: exercício preparado, NÃO EXECUTADO. SQL sintético testa o protocolo; não fabrica prova Java.
-- Requer autorização física e preflight do alvo em master antes de uso.
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
    DECLARE @identity NVARCHAR(MAX)=ctl.fn_runtime_recovery_identity(@execution_id);
    -- Material exclusivamente sintético fornecido pelo exercício administrativo rollback-only.
    DECLARE @material NVARCHAR(MAX)=N'synthetic-completed-guard-evidence';
    EXEC ctl.usp_runtime_recovery @execution_id,N'SEAL',@identity,@material,
        @policy_version,@policy_fingerprint,N'',N'coletas';
    DECLARE @snapshot TABLE(reason NVARCHAR(40),execution_state NVARCHAR(32),lease_valid BIT,
        contract_verified BIT,candidate_rows BIGINT,quality NVARCHAR(16),revision CHAR(64),
        execution_id UNIQUEIDENTIFIER,inserted_rows BIGINT,updated_rows BIGINT,reactivated_rows BIGINT,
        noop_rows BIGINT,stale_noop_rows BIGINT,reconciled_at_utc DATETIME2(3),published_at_utc DATETIME2(3),
        incremental_frontier_before_utc DATETIME2(3),incremental_frontier_after_utc DATETIME2(3));
    INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution_id,N'READ',@identity,@material,
        @policy_version,@policy_fingerprint,N'',N'coletas';
    IF (SELECT COUNT(*) FROM @snapshot WHERE reason=N'ELIGIBLE' AND contract_verified=1 AND lease_valid=1)<>1
        THROW 52345,N'RUNTIME_RECOVERY_POSITIVE_READ_FAILED',1;
    DECLARE @revision CHAR(64)=(SELECT revision FROM @snapshot);
    -- Não encapsular RESUME em INSERT EXEC: wrappers existentes capturam seus próprios resultados.
    EXEC ctl.usp_runtime_recovery @execution_id,N'RESUME',@identity,@material,
        @policy_version,@policy_fingerprint,@revision,N'coletas';
    DELETE FROM @snapshot;
    DECLARE @applies_before BIGINT=(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@execution_id);
    INSERT @snapshot EXEC ctl.usp_runtime_recovery @execution_id,N'READ',@identity,@material,
        @policy_version,@policy_fingerprint,N'',N'coletas';
    IF (SELECT COUNT(*) FROM @snapshot WHERE reason=N'PUBLISHED' AND candidate_rows=1)<>1
        OR @applies_before<>(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@execution_id)
        THROW 52346,N'RUNTIME_RECOVERY_COMMIT_READBACK_FAILED',1;
END;
GO
EXEC #run_coleta '00000000-0000-0000-0000-000000005201',
    '2036-01-01T00:00:00','2036-01-01T01:00:00',N'p02r-synthetic-1',N'INTEGER:6908',
    N'{"id":6908,"status":"pending"}',N'pending',N'Pendente',0,NULL,0,'2036-01-01T00:00:00';
:r "048_validate_runtime_durable_recovery.sql"
GO
-- Adulteração é recusada pelo trigger; a exceção reverte inclusive o baseline temporário.
BEGIN TRY
    UPDATE ctl.runtime_contract_evidence SET evidence_hash=REPLICATE('0',64);
    THROW 52347,N'RUNTIME_RECOVERY_IMMUTABILITY_NOT_ENFORCED',1;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER()<>52300 THROW;
END CATCH;
IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;
PRINT N'P02R: exercício sintético revertido integralmente.';

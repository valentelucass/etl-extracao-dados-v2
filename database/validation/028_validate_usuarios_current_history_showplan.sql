-- Compila os dois entrypoints e os quatro access paths críticos de V2-033 sem executá-los:
-- IX_stg_usuario_record_execution_source, UQ_core_usuario_source,
-- IX_core_usuario_history_timeline e PK_recon_usuario_candidate_application.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51830, N'O SHOWPLAN de Usuários aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "026_validate_usuarios_current_history.sql"
GO

SET SHOWPLAN_XML ON;
GO

DECLARE @execution_id UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000733';
DECLARE @usuario_id BIGINT = 733;
DECLARE @source_key NVARCHAR(256) = N'INTEGER:733';
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_SHOWPLAN_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_SHOWPLAN_TENANT';
DECLARE @entity_name NVARCHAR(128) = N'usuarios';
DECLARE @fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @now DATETIME2(3) = '2026-09-01T12:00:00.000';

EXEC stg.usp_stage_usuario_record
    @execution_id = @execution_id,
    @input_batch_number = 1,
    @input_record_ordinal = 1,
    @source_key = @source_key,
    @source_key_wire_type = N'INTEGER',
    @name_presence = N'ABSENT',
    @usuario_name = NULL,
    @validation_disposition = N'VALID',
    @quarantine_reason_code = NULL,
    @observed_at_utc = @now;

EXEC core.usp_apply_reconcile_publish_usuarios
    @execution_id = @execution_id,
    @contract_version = N'usuarios-showplan-v1',
    @contract_fingerprint = @fingerprint,
    @configuration_version = N'usuarios-showplan-config-v1',
    @configuration_fingerprint = @fingerprint;

SELECT TOP (20) stage_record_id, attribute_hash, name_presence_hash
FROM stg.usuario_record WITH (
    INDEX(IX_stg_usuario_record_execution_source), FORCESEEK
)
WHERE execution_id = @execution_id
  AND source_key = @source_key
ORDER BY source_key, attribute_hash, name_presence_hash;

SELECT usuario_id, state_hash, observation_order_at_utc
FROM core.usuario WITH (INDEX(UQ_core_usuario_source), FORCESEEK)
WHERE environment_name = @environment_name
  AND source_instance = @source_instance
  AND tenant_scope = @tenant_scope
  AND entity_name = @entity_name
  AND source_key = @source_key;

SELECT TOP (100) usuario_history_id, execution_id, change_kind, state_hash
FROM core.usuario_history WITH (INDEX(IX_core_usuario_history_timeline), FORCESEEK)
WHERE usuario_id = @usuario_id
ORDER BY observation_order_at_utc DESC,
         observation_order_execution_id DESC,
         usuario_history_id DESC;

SELECT source_key, usuario_id, application_disposition
FROM recon.usuario_candidate_application WITH (
    INDEX(PK_recon_usuario_candidate_application), FORCESEEK
)
WHERE execution_id = @execution_id
  AND source_key = @source_key;
GO

SET SHOWPLAN_XML OFF;
GO

IF @@TRANCOUNT <> 1 OR XACT_STATE() <> 1
    THROW 51831, N'O SHOWPLAN de Usuários alterou o escopo rollback-only.', 1;

ROLLBACK TRANSACTION;

IF @@TRANCOUNT <> 0
    THROW 51832, N'O SHOWPLAN de Usuários não encerrou a transação.', 1;

PRINT N'USUARIOS_CURRENT_HISTORY_SHOWPLAN_ROLLED_BACK';

-- Exercício sintético, set-based, contido e rollback-only de V2-021.

:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
BEGIN
    THROW 51440, N'O exercício só aceita o banco local V2 de sombra autorizado.', 1;
END;

SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "003_validate_control_plane.sql"
GO
:r "007_validate_staging_promotion_kernel.sql"
GO

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @window_start DATETIME2(3) = DATEADD(HOUR, -1, @now);
DECLARE @window_end DATETIME2(3) = @now;
DECLARE @freshness_1 DATETIME2(3) = DATEADD(MILLISECOND, 1, @window_start);
DECLARE @freshness_2 DATETIME2(3) = DATEADD(MILLISECOND, 2, @window_start);
DECLARE @freshness_3 DATETIME2(3) = DATEADD(MILLISECOND, 3, @window_start);
DECLARE @stage_at DATETIME2(3) = '2000-01-01T00:00:00.000';
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_STAGE_SOURCE';
DECLARE @tenant NVARCHAR(128) = N'SYNTHETIC_STAGE_TENANT';
DECLARE @cycle UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000213';
DECLARE @execution UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000000211';
DECLARE @contract_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @configuration_fingerprint CHAR(64) = REPLICATE('b', 64);
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('c', 64);
DECLARE @hash_a CHAR(64) = REPLICATE('a', 64);
DECLARE @hash_b CHAR(64) = REPLICATE('b', 64);
DECLARE @hash_c CHAR(64) = REPLICATE('c', 64);
DECLARE @hash_d CHAR(64) = REPLICATE('d', 64);
DECLARE @hash_e CHAR(64) = REPLICATE('e', 64);
DECLARE @hash_f CHAR(64) = REPLICATE('f', 64);
DECLARE @presence_1 CHAR(64) = REPLICATE('1', 64);
DECLARE @presence_2 CHAR(64) = REPLICATE('2', 64);
DECLARE @presence_3 CHAR(64) = REPLICATE('3', 64);
DECLARE @presence_4 CHAR(64) = REPLICATE('4', 64);
DECLARE @presence_5 CHAR(64) = REPLICATE('5', 64);
DECLARE @presence_6 CHAR(64) = REPLICATE('6', 64);
DECLARE @max_source_key NVARCHAR(256) = REPLICATE(N'K', 256);
DECLARE @tabbed_source_key NVARCHAR(256) = NCHAR(9) + N'synthetic-tab' + NCHAR(9);
DECLARE @tabbed_row_version NVARCHAR(128) = NCHAR(9) + N'row-v1' + NCHAR(9);

EXEC ctl.usp_control_plane_register_source @source, N'SYNTHETIC', @now;
EXEC ctl.usp_control_plane_start_cycle
    @cycle, N'synthetic-plan-v1', @plan_fingerprint, @now;

EXEC ctl.usp_control_plane_start_execution
    @execution_id = @execution,
    @cycle_id = @cycle,
    @environment_name = N'LOCAL_SHADOW',
    @source_instance = @source,
    @tenant_scope = @tenant,
    @entity_name = N'SYNTHETIC_STAGE_ENTITY',
    @execution_mode = N'BACKFILL',
    @partition_start_utc = @window_start,
    @partition_end_exclusive_utc = @window_end,
    @window_strategy = N'SYNTHETIC_INTERVAL',
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint,
    @idempotency_key = N'synthetic-staging-containment-001',
    @lease_seconds = 3600,
    @started_at_utc = @now;

-- Estado sintético anterior: a promoção não pode sobrescrevê-lo antes de publicação reconciliada.
INSERT INTO core.entity_record_state (
    environment_name, source_instance, tenant_scope, entity_name, source_key,
    row_fingerprint_version, source_row_hash,
    presence_fingerprint_version, presence_fingerprint,
    source_freshness_at_utc, active,
    first_promoted_execution_id, last_promoted_execution_id,
    first_promoted_at_utc, last_promoted_at_utc
) VALUES (
    N'LOCAL_SHADOW', @source, @tenant, N'SYNTHETIC_STAGE_ENTITY', N'synthetic-key-a',
    N'row-v0', @hash_e, N'presence-v0', @presence_5,
    DATEADD(DAY, -1, @now), 1, @execution, @execution,
    DATEADD(DAY, -1, @now), DATEADD(DAY, -1, @now)
);

-- Uma duplicata resolvível, uma chave simples, um quarantine explícito e dois tipos de conflito.
DECLARE @kernel_before DATETIME2(3) = SYSUTCDATETIME();
EXEC stg.usp_stage_record @execution, 1, 1, N'synthetic-key-a', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @freshness_1, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 2, N'synthetic-key-b', N'row-v1', @hash_b,
    N'presence-v1', @presence_2, @freshness_1, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 3, N'synthetic-key-a', N'row-v2', @hash_c,
    N'presence-v2', @presence_3, @freshness_2, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 4, NULL, NULL, NULL, NULL, NULL,
    NULL, N'QUARANTINE', N'SOURCE_KEY_MISSING', @stage_at;
EXEC stg.usp_stage_record @execution, 1, 5, N'synthetic-key-equal', N'row-v1', @hash_d,
    N'presence-v1', @presence_4, @freshness_3, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 6, N'synthetic-key-equal', N'row-v2', @hash_e,
    N'presence-v2', @presence_5, @freshness_3, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 7, N'synthetic-key-unknown', N'row-v1', @hash_e,
    N'presence-v1', @presence_5, NULL, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 8, N'synthetic-key-unknown', N'row-v2', @hash_f,
    N'presence-v2', @presence_6, NULL, N'VALID', NULL, @stage_at;
-- Chaves opacas distinguem caixa/acento; espaços externos e labels são normalizados.
EXEC stg.usp_stage_record @execution, 1, 9, N'synthetic-KeyA   ', N'row-v1   ', @hash_a,
    N'presence-v1   ', @presence_1, @window_start, N'VALID   ', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 10, N'synthetic-keya', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 11, N'synthetic-acao', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 12, N'synthetic-ação', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 13, @max_source_key, N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 14, @tabbed_source_key, @tabbed_row_version, @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;

DECLARE @extracted_at DATETIME2(3) = DATEADD(MILLISECOND, 2, @now);
DECLARE @staged_at DATETIME2(3) = DATEADD(MILLISECOND, 3, @now);
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @extracted_at;
EXEC ctl.usp_control_plane_transition_execution
    @execution, N'EXTRACTED', N'STAGED', N'STAGE_OK', @staged_at;

-- Reenvio exato permanece no-op depois do fechamento do staging.
EXEC stg.usp_stage_record @execution, 1, 1, N'synthetic-key-a', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @freshness_1, N'VALID', NULL, @stage_at;
EXEC stg.usp_stage_record @execution, 1, 9, N'synthetic-KeyA', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @window_start, N'VALID', NULL, '1999-01-01T00:00:00.000';

EXEC core.usp_prepare_staged_execution
    @execution_id = @execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;

-- Simula confirmação perdida depois do commit: retries exatos devem reconhecer a evidência
-- persistida antes de consultar a lease, mesmo que ela já tenha expirado.
DECLARE @forced_expiry_clock DATETIME2(3) = SYSUTCDATETIME();
UPDATE ctl.execution_lease
SET acquired_at_utc = DATEADD(HOUR, -2, @forced_expiry_clock),
    heartbeat_at_utc = DATEADD(HOUR, -2, @forced_expiry_clock),
    expires_at_utc = DATEADD(HOUR, -1, @forced_expiry_clock)
WHERE execution_id = @execution;

EXEC stg.usp_stage_record @execution, 1, 1, N'synthetic-key-a', N'row-v1', @hash_a,
    N'presence-v1', @presence_1, @freshness_1, N'VALID', NULL,
    '1998-01-01T00:00:00.000';
EXEC core.usp_prepare_staged_execution
    @execution_id = @execution,
    @contract_version = N'synthetic-contract-v1',
    @contract_fingerprint = @contract_fingerprint,
    @configuration_version = N'synthetic-config-v1',
    @configuration_fingerprint = @configuration_fingerprint;
DECLARE @kernel_after DATETIME2(3) = SYSUTCDATETIME();

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_count
    WHERE execution_id = @execution
      AND count_phase = N'STAGING_KERNEL'
      AND physical_rows = 14
      AND distinct_root_keys = 10
      AND duplicate_rows = 3
      AND valid_rows = 8
      AND quarantined_root_keys = 2
      AND unidentified_quarantine_rows = 1
)
    THROW 51441, N'As equações canônicas do candidate set não foram auditadas.', 1;

IF (SELECT COUNT_BIG(*) FROM stg.execution_candidate WHERE execution_id = @execution) <> 8
    THROW 51442, N'O candidate set não preservou somente vencedores não ambíguos.', 1;

IF (SELECT COUNT_BIG(*) FROM stg.execution_candidate
    WHERE execution_id = @execution
      AND source_key IN (
          N'synthetic-KeyA', N'synthetic-keya', N'synthetic-acao', N'synthetic-ação'
      )) <> 4
    OR NOT EXISTS (
        SELECT 1 FROM stg.execution_candidate
        WHERE execution_id = @execution
          AND source_key = @max_source_key
          AND DATALENGTH(source_key) = 512
    )
    OR NOT EXISTS (
        SELECT 1 FROM stg.execution_candidate
        WHERE execution_id = @execution
          AND source_key = @tabbed_source_key
          AND row_fingerprint_version = @tabbed_row_version
    )
    THROW 51452, N'BIN2, trim canônico ou limite exato da chave de origem divergiu.', 1;

IF NOT EXISTS (
    SELECT 1 FROM stg.execution_candidate
    WHERE execution_id = @execution
      AND source_key = N'synthetic-key-a'
      AND row_fingerprint_version = N'row-v2'
      AND source_row_hash = @hash_c
      AND presence_fingerprint_version = N'presence-v2'
      AND presence_fingerprint = @presence_3
)
    THROW 51443, N'O vencedor mais fresco e suas versões não foram persistidos.', 1;

IF (SELECT COUNT_BIG(*) FROM recon.quarantine_record WHERE execution_id = @execution) <> 5
    OR (SELECT COUNT_BIG(DISTINCT source_key) FROM recon.quarantine_record
        WHERE execution_id = @execution) <> 2
    OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record
        WHERE execution_id = @execution AND source_key IS NULL
          AND reason_code = N'SOURCE_KEY_MISSING') <> 1
    OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record
        WHERE execution_id = @execution AND reason_code = N'EQUAL_FRESHNESS_CONFLICT') <> 2
    OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record
        WHERE execution_id = @execution AND reason_code = N'UNKNOWN_FRESHNESS_CONFLICT') <> 2
    THROW 51444, N'Conflitos de frescor não foram isolados em quarantine.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_promotion_result
    WHERE execution_id = @execution
      AND physical_rows = 14
      AND distinct_root_keys = 10
      AND candidate_rows = 8
      AND duplicate_rows = 3
      AND quarantined_root_keys = 2
      AND unidentified_quarantine_rows = 1
      AND quarantined_stage_rows = 5
)
    THROW 51445, N'O resultado da promoção não fecha as equações do candidate set.', 1;

IF (
    EXISTS (
    SELECT 1 FROM stg.execution_record
    WHERE execution_id = @execution
      AND (staged_at_utc < @kernel_before OR staged_at_utc > @kernel_after
           OR staged_at_utc = @stage_at)
    )
    OR EXISTS (
        SELECT 1 FROM stg.execution_candidate
        WHERE execution_id = @execution
          AND (prepared_at_utc < @kernel_before OR prepared_at_utc > @kernel_after)
    )
    OR EXISTS (
        SELECT 1 FROM recon.quarantine_record
        WHERE execution_id = @execution
          AND (quarantined_at_utc < @kernel_before OR quarantined_at_utc > @kernel_after)
    )
    OR EXISTS (
        SELECT 1 FROM ctl.execution_promotion_result
        WHERE execution_id = @execution
          AND (promoted_at_utc < @kernel_before OR promoted_at_utc > @kernel_after)
    )
    OR EXISTS (
        SELECT 1 FROM ctl.execution_count
        WHERE execution_id = @execution AND count_phase = N'STAGING_KERNEL'
          AND (recorded_at_utc < @kernel_before OR recorded_at_utc > @kernel_after)
    )
    OR EXISTS (
        SELECT 1 FROM ctl.execution_state_event
        WHERE execution_id = @execution AND reason_code = N'CANDIDATE_SET_PREPARED'
          AND (transitioned_at_utc < @kernel_before OR transitioned_at_utc > @kernel_after)
    )
)
    THROW 51451, N'O kernel confiou em timestamp técnico fornecido pelo caller.', 1;

IF (SELECT COUNT_BIG(*) FROM core.entity_record_state
    WHERE source_instance = @source AND tenant_scope = @tenant
      AND entity_name = N'SYNTHETIC_STAGE_ENTITY') <> 1
    OR NOT EXISTS (
        SELECT 1 FROM core.entity_record_state
        WHERE environment_name = N'LOCAL_SHADOW'
          AND source_instance = @source
          AND tenant_scope = @tenant
          AND entity_name = N'SYNTHETIC_STAGE_ENTITY'
          AND source_key = N'synthetic-key-a'
          AND row_fingerprint_version = N'row-v0'
          AND source_row_hash = @hash_e
          AND presence_fingerprint_version = N'presence-v0'
          AND presence_fingerprint = @presence_5
    )
    THROW 51446, N'A promoção alterou core antes da publicação reconciliada.', 1;

IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_attempt
    WHERE execution_id = @execution
      AND current_state = N'PROMOTED'
      AND next_transition_sequence = 6
)
    OR NOT EXISTS (
        SELECT 1 FROM ctl.execution_state_event
        WHERE execution_id = @execution
          AND previous_state = N'STAGED'
          AND next_state = N'PROMOTED'
          AND reason_code = N'CANDIDATE_SET_PREPARED'
    )
    THROW 51447, N'A transição evidence-bound ou sua sequência atômica não foi registrada.', 1;

IF EXISTS (SELECT 1 FROM ctl.partition_publication_pointer WHERE published_execution_id = @execution)
    THROW 51448, N'A promoção não pode criar publication pointer.', 1;

IF EXISTS (SELECT 1 FROM ctl.incremental_publication_watermark WHERE advanced_at_utc IS NOT NULL)
    THROW 51457, N'A promoção não pode avançar watermark incremental.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_publish_execution', N'P'))
       NOT LIKE N'%Publicação indisponível sem evidência de reconciliação e commit atômico%'
    THROW 51449, N'A publicação não permanece fail-closed.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
       LIKE N'%EXEC ctl.usp_control_plane_record_counts%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
       NOT LIKE N'%INSERT INTO ctl.execution_count%'
    THROW 51454, N'STAGING_KERNEL ainda usa a API pública de contagens.', 1;

IF (SELECT COUNT_BIG(*) FROM ctl.execution_count
    WHERE execution_id = @execution AND count_phase = N'STAGING_KERNEL') <> 1
    OR (SELECT COUNT_BIG(*) FROM stg.execution_record
        WHERE execution_id = @execution AND input_batch_number = 1
          AND input_record_ordinal = 1) <> 1
    OR (SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result
        WHERE execution_id = @execution) <> 1
    OR (SELECT COUNT_BIG(*) FROM stg.execution_candidate
        WHERE execution_id = @execution) <> 8
    OR (SELECT COUNT_BIG(*) FROM recon.quarantine_record
        WHERE execution_id = @execution) <> 5
    OR (SELECT COUNT_BIG(*) FROM ctl.execution_state_event
        WHERE execution_id = @execution AND reason_code = N'CANDIDATE_SET_PREPARED') <> 1
    THROW 51455, N'Retry com lease expirada duplicou evidência de staging ou promoção.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%@next_state COLLATE Latin1_General_100_BIN2%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_control_plane_transition_execution', N'P'))
       NOT LIKE N'%IN (N''PROMOTED'', N''RECONCILED'', N''PUBLISHED'')%'
    THROW 51450, N'O atalho manual PROMOTED para RECONCILED deveria falhar.', 1;

IF OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
       NOT LIKE N'%THROW 51403%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
       NOT LIKE N'%THROW 51404%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution', N'P'))
       NOT LIKE N'%THROW 51412%'
    OR OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
       NOT LIKE N'%@quarantine_reason_code COLLATE Latin1_General_100_BIN2%'
    THROW 51456, N'Divergência, novo staging fechado ou evidência inconsistente não falham fechados.', 1;

IF CHARINDEX(
       N'LEFT(@quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2',
       OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_record', N'P'))
   ) = 0
    OR EXISTS (
        SELECT 1
        FROM (VALUES
            (N'_X'),
            (N'1X'),
            (N'xX'),
            (NCHAR(9) + N'X'),
            (N'ßX')
        ) AS invalid_reason (reason_code)
        WHERE LEFT(invalid_reason.reason_code, 1) COLLATE Latin1_General_100_BIN2
                  LIKE N'[A-Z]'
          AND invalid_reason.reason_code COLLATE Latin1_General_100_BIN2
                  NOT LIKE N'%[^A-Z0-9_]%'
          AND LEN(invalid_reason.reason_code) BETWEEN 2 AND 64
    )
    THROW 51458, N'Motivo de quarantine inválido passou pela gramática canônica BIN2.', 1;

-- O parâmetro MAX deve rejeitar 257 caracteres antes que SQL Server possa truncar para a
-- mesma chave de 256 caracteres e tratar o comando como retry idempotente.
DECLARE @oversized_prefix_collision_rejected BIT = 0;
DECLARE @oversized_source_key NVARCHAR(MAX) = CONCAT(@max_source_key, N'X');
IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51461, N'O escopo rollback-only não estava íntegro antes da rejeição oversized final.', 1;
BEGIN TRY
    EXEC stg.usp_stage_record @execution, 1, 13, @oversized_source_key, N'row-v1', @hash_a,
        N'presence-v1', @presence_1, @window_start, N'VALID', NULL, @stage_at;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 51400
        SET @oversized_prefix_collision_rejected = 1;
    ELSE
        THROW;
END CATCH;

IF @oversized_prefix_collision_rejected = 0
    THROW 51453, N'Chave oversized com o mesmo prefixo foi truncada silenciosamente.', 1;

-- A rejeição oversized pode deixar o BEGIN interno aberto sob XACT_ABORT. O escopo externo provado
-- acima não pode desaparecer; ROLLBACK encerra todos os níveis restantes.
IF XACT_STATE() NOT IN (1, -1) OR @@TRANCOUNT < 1
    THROW 51459, N'O escopo rollback-only do candidate set foi consumido ou invalidado.', 1;

ROLLBACK TRANSACTION;

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51460, N'O rollback do candidate set não encerrou o escopo sintético.', 1;

PRINT N'Exercício rollback-only do candidate set contido concluído com sucesso.';

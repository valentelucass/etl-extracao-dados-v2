-- Exercício sintético, set-based e rollback-only de V2-027/V2-015e.
-- Não usa payload real e não persiste release, dados, objetos ou publicação.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51981, N'O exercício de Cotações aceita somente ETL_SISTEMA_V2_SHADOW.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "005_validate_progressive_data_gate.sql"
GO
:r "040_validate_cotacoes_shadow_vertical.sql"

IF OBJECT_ID(N'stg.usp_stage_cotacao_record', N'P') IS NULL
   OR OBJECT_ID(N'core.usp_apply_reconcile_publish_cotacoes', N'P') IS NULL
   OR OBJECT_ID(N'core.cotacao', N'U') IS NULL
   OR OBJECT_ID(N'recon.cotacao_root_presence_observation', N'U') IS NULL
    THROW 51982, N'V011 não materializou a vertical de Cotações.', 1;
GO

CREATE TABLE #tariff_release (
    release_code CHAR(1) COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY,
    reference_release_id BIGINT NOT NULL
);

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COTACOES_6906';
DECLARE @plan_fingerprint CHAR(64) = REPLICATE('a', 64);
DECLARE @release_a BIGINT, @release_b BIGINT;

INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'QUOTE_TARIFF', N'LOCAL_SHADOW_SYNTHETIC', N'synthetic-cotacoes-tariff-a', 1,
    N'DETERMINISTIC_LOCAL', N'SYNTHETIC_COTACOES_TARIFF_A', REPLICATE('1', 64), 1,
    '20360101', '20360201', N'SYNTHETIC_FIXTURE', N'REFERENCE_AUTHOR'
);
SET @release_a = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.tarifa_rota_uf (
    reference_release_id, family_code, origin_uf, destination_uf, coverage_state,
    minimum_amount, currency_code, unit_code, rounding_mode, valid_from,
    valid_to_exclusive, reason_code
) VALUES (
    @release_a, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
    CONVERT(DECIMAL(19,4), 12.3456), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
    '20360101', '20360201', N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint,
    imported_row_count, imported_byte_count, importer_role
) VALUES (
    @release_a, N'governed-references-v1', REPLICATE('1', 64), 1, 512,
    N'REFERENCE_IMPORTER'
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @release_a, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_COTACOES_TARIFF_A_APPROVAL', REPLICATE('a', 64)
);
INSERT INTO #tariff_release VALUES ('A', @release_a);

INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'QUOTE_TARIFF', N'LOCAL_SHADOW_SYNTHETIC', N'synthetic-cotacoes-tariff-b', 1,
    N'DETERMINISTIC_LOCAL', N'SYNTHETIC_COTACOES_TARIFF_B', REPLICATE('2', 64), 1,
    '20360201', '20370101', N'SYNTHETIC_FIXTURE', N'REFERENCE_AUTHOR'
);
SET @release_b = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.tarifa_rota_uf (
    reference_release_id, family_code, origin_uf, destination_uf, coverage_state,
    minimum_amount, currency_code, unit_code, rounding_mode, valid_from,
    valid_to_exclusive, reason_code
) VALUES (
    @release_b, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
    CONVERT(DECIMAL(19,4), 98.7654), 'USD', N'PER_WEIGHT', N'HALF_EVEN',
    '20360201', '20370101', N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint,
    imported_row_count, imported_byte_count, importer_role
) VALUES (
    @release_b, N'governed-references-v1', REPLICATE('2', 64), 1, 512,
    N'REFERENCE_IMPORTER'
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @release_b, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_COTACOES_TARIFF_B_APPROVAL', REPLICATE('b', 64)
);
INSERT INTO #tariff_release VALUES ('B', @release_b);

EXEC ctl.usp_control_plane_register_source @source, N'DATA_EXPORT', @now;
EXEC ctl.usp_control_plane_start_cycle
    '00000000-0000-0000-0000-000000002701', N'synthetic-cotacoes-plan-v1',
    @plan_fingerprint, @now;
GO

CREATE OR ALTER PROCEDURE #install_dq_policy
    @tenant NVARCHAR(128),
    @mode NVARCHAR(16),
    @suffix NVARCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @effective DATETIME2(3) = DATEADD(DAY, -1, SYSUTCDATETIME());
    DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COTACOES_6906';
    DECLARE @entity NVARCHAR(128) = N'cotacoes';
    DECLARE @scope CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'dq-scope-v1|', DATALENGTH(@environment), N':', @environment,
            N'|', DATALENGTH(@source), N':', @source,
            N'|', DATALENGTH(@tenant), N':', @tenant,
            N'|', DATALENGTH(@entity), N':', @entity,
            N'|', DATALENGTH(@mode), N':', @mode))), 2));
    DECLARE @version NVARCHAR(128) = CONCAT(N'synthetic-cotacoes-dq-', @suffix);
    DECLARE @fingerprint CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'dq-policy-v1|', DATALENGTH(@version), N':', @version, N'|', @scope,
            N'|4|3600|', DATALENGTH(N'quality-owner'), N':quality-owner|',
            DATALENGTH(N'quarantine-owner'), N':quarantine-owner|',
            DATALENGTH(N'synthetic-cotacoes-retention-v1'),
            N':synthetic-cotacoes-retention-v1|',
            DATALENGTH(N'retention-owner'), N':retention-owner|',
            CONVERT(NVARCHAR(33), @effective, 126),
            N'|1:COUNT_EQUATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
            N'|2:PAGE_TERMINALITY:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
            N'|3:PROMOTION_RECONCILIATION:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner',
            N'|4:QUARANTINE_SLA:0:0:', DATALENGTH(N'quality-owner'), N':quality-owner'))), 2));
    INSERT INTO ctl.data_quality_policy VALUES (
        @version, @fingerprint, @scope, 4, 3600, N'quality-owner',
        N'quarantine-owner', N'synthetic-cotacoes-retention-v1',
        N'retention-owner', N'RATIFIED', @effective, SYSUTCDATETIME()
    );
    INSERT INTO ctl.data_quality_check_policy VALUES
        (@version, @fingerprint, 1, N'COUNT_EQUATION', 0, 0, N'quality-owner'),
        (@version, @fingerprint, 2, N'PAGE_TERMINALITY', 0, 0, N'quality-owner'),
        (@version, @fingerprint, 3, N'PROMOTION_RECONCILIATION', 0, 0, N'quality-owner'),
        (@version, @fingerprint, 4, N'QUARANTINE_SLA', 0, 0, N'quality-owner');
END;
GO

EXEC #install_dq_policy N'SYNTHETIC_COTACOES_TENANT', N'BACKFILL', N'tenant-a-backfill-v1';
EXEC #install_dq_policy N'SYNTHETIC_COTACOES_TENANT', N'REPLAY', N'tenant-a-replay-v1';
EXEC #install_dq_policy N'SYNTHETIC_COTACOES_TENANT_B', N'BACKFILL', N'tenant-b-backfill-v1';
GO

CREATE OR ALTER PROCEDURE #run_cotacao
    @execution_id UNIQUEIDENTIFIER,
    @start DATETIME2(3),
    @finish DATETIME2(3),
    @idempotency NVARCHAR(128),
    @mode NVARCHAR(16),
    @replay_of UNIQUEIDENTIFIER,
    @tenant NVARCHAR(128),
    @source_key NVARCHAR(256),
    @payload NVARCHAR(MAX),
    @presence NVARCHAR(MAX),
    @nfse DATETIME2(3),
    @cte DATETIME2(3),
    @requested DATETIME2(3),
    @business_date DATE,
    @user NVARCHAR(MAX),
    @amount DECIMAL(19,4),
    @origin CHAR(2),
    @destination CHAR(2),
    @release BIGINT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
    DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COTACOES_6906';
    DECLARE @entity NVARCHAR(128) = N'cotacoes';
    DECLARE @contract CHAR(64) = REPLICATE('b', 64);
    DECLARE @configuration CHAR(64) = REPLICATE('c', 64);
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
        @execution_id, '00000000-0000-0000-0000-000000002701',
        @environment, @source, @tenant, @entity, @mode, @start, @finish,
        N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6906-v1', @contract,
        N'cotacoes-shadow-v1', @configuration, @idempotency, @replay_of, 3600, @now;
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 1, 1, 1000, 1, 1, 512, 0, @now, N'NONE';
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 2, 1, 1000, 0, 0, 64, 1, @now, N'DATA_EXPORT_EMPTY_PAGE';
    EXEC stg.usp_stage_cotacao_record
        @execution_id, 1, 1, @source_key, @payload, @presence, @user,
        @nfse, @cte, @requested, @business_date, @amount, NULL, @origin,
        @destination, N'VALID', NULL, @now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @now;
    EXEC ctl.usp_control_plane_transition_execution
        @execution_id, N'EXTRACTED', N'STAGED', N'STAGING_OK', @now;
    EXEC core.usp_prepare_staged_execution
        @execution_id, N'dataexport-6906-v1', @contract,
        N'cotacoes-shadow-v1', @configuration;
    IF NOT EXISTS (
        SELECT 1 FROM ctl.cotacao_promotion_result
        WHERE execution_id = @execution_id AND validation_state = N'PASSED'
          AND candidate_rows = 1 AND typed_candidate_rows = 1
    )
        THROW 51983, N'Candidate set de Cotações não passou.', 1;

    DECLARE @dq TABLE (
        execution_id UNIQUEIDENTIFIER, policy_version NVARCHAR(128),
        policy_fingerprint CHAR(64), evaluation_fingerprint CHAR(64),
        expected_checks SMALLINT, completed_checks SMALLINT, passed_checks SMALLINT,
        failed_checks SMALLINT, evaluated_rows BIGINT, failed_rows BIGINT,
        evaluation_state NVARCHAR(16), evaluated_at_utc DATETIME2(3)
    );
    INSERT INTO @dq EXEC recon.usp_evaluate_execution_data_quality
        @execution_id, @policy_version, @policy_fingerprint;
    IF NOT EXISTS (SELECT 1 FROM @dq WHERE evaluation_state = N'PASSED' AND failed_rows = 0)
        THROW 51984, N'Data Quality de Cotações não passou.', 1;

    -- O cliente SQLCMD captura este result set tipado; INSERT EXEC aninhado é proibido.
    EXEC core.usp_apply_reconcile_publish_cotacoes
        @execution_id, N'dataexport-6906-v1', @contract,
        N'cotacoes-shadow-v1', @configuration, @release;
END;
GO

DECLARE @presence_nfse NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"VALUE","qoe_qes_fit_fhe_cte_issued_at":"VALUE","qoe_qes_total":"VALUE","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"VALUE","qoe_qes_ony_sae_code":"VALUE","qoe_qes_diy_sae_code":"VALUE"}';
DECLARE @presence_value NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"VALUE","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"VALUE","qoe_qes_ony_sae_code":"VALUE","qoe_qes_diy_sae_code":"VALUE"}';
DECLARE @presence_absent NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"ABSENT","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"ABSENT","qoe_qes_ony_sae_code":"ABSENT","qoe_qes_diy_sae_code":"ABSENT"}';
DECLARE @presence_null NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"NULL","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"NULL","qoe_qes_ony_sae_code":"ABSENT","qoe_qes_diy_sae_code":"ABSENT"}';
DECLARE @presence_zero NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"VALUE","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"VALUE","qoe_qes_ony_sae_code":"ABSENT","qoe_qes_diy_sae_code":"ABSENT"}';
DECLARE @presence_uf_null NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"ABSENT","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"ABSENT","qoe_qes_ony_sae_code":"NULL","qoe_qes_diy_sae_code":"NULL"}';
DECLARE @tenant_a NVARCHAR(128) = N'SYNTHETIC_COTACOES_TENANT';
DECLARE @release_a BIGINT = (SELECT reference_release_id FROM #tariff_release WHERE release_code = 'A');
DECLARE @release_b BIGINT = (SELECT reference_release_id FROM #tariff_release WHERE release_code = 'B');
DECLARE @root_key NVARCHAR(256) = N'INTEGER:2147483648';
DECLARE @maximum_key NVARCHAR(256) = N'INTEGER:9223372036854775807';
DECLARE @seed UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002702';
DECLARE @maximum UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002703';
DECLARE @absent UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002704';
DECLARE @nulls UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002705';
DECLARE @zero UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002706';
DECLARE @replay UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002707';
DECLARE @stale UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002708';
DECLARE @retry_contract CHAR(64) = REPLICATE('b', 64);
DECLARE @retry_configuration CHAR(64) = REPLICATE('c', 64);

-- O boundary físico rejeita zero, negativos, overflow e formas não canônicas.
DECLARE @identity_cases TABLE (
    case_id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    expected_valid BIT NOT NULL
);
INSERT INTO @identity_cases (source_key, expected_valid) VALUES
    (N'INTEGER:1', 1),
    (N'INTEGER:2147483648', 1),
    (N'INTEGER:9223372036854775807', 1),
    (N'INTEGER:0', 0),
    (N'INTEGER:-1', 0),
    (N'INTEGER:9223372036854775808', 0),
    (N'INTEGER:+1', 0),
    (N'INTEGER:01', 0),
    (N'INTEGER: 1', 0),
    (N'INTEGER:1 ', 0),
    (N'INTEGER:1.0', 0),
    (N'INTEGER:' + NCHAR(0xFF11), 0);
DECLARE @invalid_source_key NVARCHAR(256);
DECLARE @invalid_case_id INT;
DECLARE @identity_rejected BIT;
DECLARE invalid_identity CURSOR LOCAL FAST_FORWARD FOR
    SELECT case_id, source_key FROM @identity_cases WHERE expected_valid = 0 ORDER BY case_id;
OPEN invalid_identity;
FETCH NEXT FROM invalid_identity INTO @invalid_case_id, @invalid_source_key;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @identity_rejected = 0;
    SET XACT_ABORT OFF;
    BEGIN TRY
        EXEC stg.usp_stage_cotacao_record
            '00000000-0000-0000-0000-000000002799', 1, @invalid_case_id,
            @invalid_source_key, N'{"identity":"synthetic-invalid"}', @presence_value,
            N'Identidade sintética', NULL, NULL, '2036-01-01T10:00:00', '2036-01-01',
            1.0000, NULL, 'SP', 'RJ', N'VALID', NULL, '2036-01-01T10:00:00';
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() = 51900
            SET @identity_rejected = 1;
        ELSE
            THROW;
    END CATCH;
    IF XACT_STATE() <> 1
        THROW 51985, N'Rejeição de identidade deixou a transação inconsistente.', 1;
    IF @identity_rejected = 0
        THROW 51985, N'Barreira SQL aceitou sequence_code BIGINT não canônico.', 1;
    FETCH NEXT FROM invalid_identity INTO @invalid_case_id, @invalid_source_key;
END;
CLOSE invalid_identity;
DEALLOCATE invalid_identity;
SET XACT_ABORT ON;
IF EXISTS (
    SELECT 1 FROM stg.cotacao_record
    WHERE execution_id = '00000000-0000-0000-0000-000000002799'
)
    THROW 51985, N'Identidade rejeitada alcançou o staging tipado.', 1;

EXEC #run_cotacao
    @seed, '2036-01-01T00:00:00', '2036-01-01T01:00:00', N'cotacoes-bigint-seed',
    N'BACKFILL', NULL, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"SEED"}', @presence_nfse,
    '2036-01-15T10:00:00', '2036-01-16T10:00:00', '2036-01-17T10:00:00',
    '2036-01-15', N'Usuário sintético', 10.2500, 'SP', 'RJ', @release_a;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_COTACOES_6906'
      AND tenant_scope = @tenant_a AND entity_name = N'cotacoes'
      AND source_key = @root_key
      AND freshness_origin = N'NFSE_ISSUED_AT'
      AND tariff_reference_release_id = @release_a
      AND tariff_minimum_amount = CONVERT(DECIMAL(19,4), 12.3456)
      AND tariff_currency_code = 'BRL' AND tariff_unit_code = N'PER_SHIPMENT'
      AND tariff_rounding_mode = N'HALF_UP' AND currency_code IS NULL
)
    THROW 51986, N'BIGINT acima de INT, COT-01 ou tarifa A não foram aplicados.', 1;

-- Retry da mesma execução não cria outra raiz.
EXEC core.usp_apply_reconcile_publish_cotacoes
    @seed, N'dataexport-6906-v1', @retry_contract,
    N'cotacoes-shadow-v1', @retry_configuration, @release_a;
IF (
    SELECT COUNT_BIG(*) FROM core.cotacao
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_COTACOES_6906'
      AND tenant_scope = @tenant_a AND entity_name = N'cotacoes'
      AND source_key = @root_key
) <> 1
    THROW 51987, N'Retry criou raiz BIGINT duplicada.', 1;

EXEC #run_cotacao
    @maximum, '2036-01-01T01:00:00', '2036-01-01T02:00:00', N'cotacoes-bigint-maximum',
    N'BACKFILL', NULL, @tenant_a, @maximum_key,
    N'{"sequence_code":9223372036854775807,"state":"MAXIMUM"}', @presence_value,
    NULL, NULL, '2036-01-16T10:00:00', '2036-01-16',
    N'Maximum', 1.0000, 'SP', 'RJ', @release_a;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE tenant_scope = @tenant_a AND source_key = @maximum_key
)
    THROW 51988, N'Long.MAX_VALUE não foi aceito fisicamente.', 1;

-- ABSENT preserva valores da mesma raiz, mas a tarifa vem da release B vigente.
EXEC #run_cotacao
    @absent, '2036-01-01T02:00:00', '2036-01-01T03:00:00', N'cotacoes-tri-state-absent',
    N'BACKFILL', NULL, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"ABSENT"}', @presence_absent,
    NULL, NULL, '2036-02-15T10:00:00', '2036-02-15',
    NULL, NULL, NULL, NULL, @release_b;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_COTACOES_6906'
      AND tenant_scope = @tenant_a AND entity_name = N'cotacoes'
      AND source_key = @root_key
      AND user_name_normalized = N'Usuário sintético'
      AND total_amount = CONVERT(DECIMAL(19,4), 10.2500)
      AND origin_uf = 'SP' AND destination_uf = 'RJ'
      AND tariff_reference_release_id = @release_b
      AND tariff_minimum_amount = CONVERT(DECIMAL(19,4), 98.7654)
      AND tariff_currency_code = 'USD' AND tariff_unit_code = N'PER_WEIGHT'
      AND tariff_rounding_mode = N'HALF_EVEN' AND currency_code IS NULL
      AND last_seen_execution_id = @absent
)
    THROW 51989, N'ABSENT, UFs efetivas ou atributos reference-only divergiram.', 1;

-- NULL limpa usuário/total; UFs ABSENT continuam resolvidas na mesma raiz.
EXEC #run_cotacao
    @nulls, '2036-01-01T03:00:00', '2036-01-01T04:00:00', N'cotacoes-tri-state-null',
    N'BACKFILL', NULL, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"NULL"}', @presence_null,
    NULL, NULL, '2036-03-01T10:00:00', '2036-03-01',
    NULL, NULL, NULL, NULL, @release_b;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE tenant_scope = @tenant_a AND source_key = @root_key
      AND user_name_normalized IS NULL AND total_amount IS NULL
      AND origin_uf = 'SP' AND destination_uf = 'RJ'
      AND tariff_reference_release_id = @release_b
)
    THROW 51990, N'NULL não limpou sem colapsar ABSENT das UFs.', 1;

-- VALUE zero é valor legítimo e substitui NULL.
EXEC #run_cotacao
    @zero, '2036-01-01T04:00:00', '2036-01-01T05:00:00', N'cotacoes-tri-state-zero',
    N'BACKFILL', NULL, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"ZERO"}', @presence_zero,
    NULL, NULL, '2036-04-01T10:00:00', '2036-04-01',
    N'Zero', 0.0000, NULL, NULL, @release_b;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE tenant_scope = @tenant_a AND source_key = @root_key
      AND user_name_normalized = N'Zero'
      AND total_amount = CONVERT(DECIMAL(19,4), 0.0000)
      AND origin_uf = 'SP' AND destination_uf = 'RJ'
      AND tariff_currency_code = 'USD' AND currency_code IS NULL
)
    THROW 51991, N'VALUE zero foi colapsado ou a moeda da fonte foi inventada.', 1;

-- Replay do ABSENT aceito e observação antiga permanecem idempotentes/stale.
EXEC #run_cotacao
    @replay, '2036-01-01T02:00:00', '2036-01-01T03:00:00', N'cotacoes-replay-absent',
    N'REPLAY', @absent, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"ABSENT"}', @presence_absent,
    NULL, NULL, '2036-02-15T10:00:00', '2036-02-15',
    NULL, NULL, NULL, NULL, @release_b;
EXEC #run_cotacao
    @stale, '2036-01-01T05:00:00', '2036-01-01T06:00:00', N'cotacoes-stale',
    N'BACKFILL', NULL, @tenant_a, @root_key,
    N'{"sequence_code":2147483648,"state":"OLDER"}', @presence_value,
    NULL, NULL, '2036-01-20T10:00:00', '2036-01-20',
    N'Older', 99.0000, 'SP', 'RJ', @release_a;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE tenant_scope = @tenant_a AND source_key = @root_key
      AND user_name_normalized = N'Zero'
      AND total_amount = CONVERT(DECIMAL(19,4), 0.0000)
      AND freshness_at_utc = '2036-04-01T10:00:00'
      AND tariff_reference_release_id = @release_b
) OR (
    SELECT COUNT_BIG(*) FROM core.cotacao
    WHERE tenant_scope = @tenant_a AND source_key = @root_key
) <> 1
    THROW 51992, N'Replay ABSENT ou stale regrediu a raiz corrente.', 1;

-- Duplicata entre páginas e empate divergente continuam fail-closed.
DECLARE @duplicate UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002709';
DECLARE @conflict UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002710';
DECLARE @run_now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @contract CHAR(64) = REPLICATE('b', 64);
DECLARE @configuration CHAR(64) = REPLICATE('c', 64);
EXEC ctl.usp_control_plane_start_execution
    @duplicate, '00000000-0000-0000-0000-000000002701', N'LOCAL_SHADOW',
    N'SYNTHETIC_COTACOES_6906', @tenant_a, N'cotacoes', N'BACKFILL',
    '2036-01-01T06:00:00', '2036-01-01T07:00:00',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6906-v1', @contract,
    N'cotacoes-shadow-v1', @configuration, N'cotacoes-duplicate-pages', NULL, 3600, @run_now;
EXEC ctl.usp_control_plane_record_page
    @duplicate, 1, 1, 1000, 2, 1, 1024, 0, @run_now, N'NONE';
EXEC ctl.usp_control_plane_record_page
    @duplicate, 2, 1, 1000, 0, 0, 64, 1, @run_now, N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_cotacao_record
    @duplicate, 1, 1, N'INTEGER:6908', N'{"sequence_code":6908}', @presence_value,
    N'U', NULL, NULL, '2036-01-20T10:00:00', '2036-01-20', 10.2500, NULL,
    'SP', 'RJ', N'VALID', NULL, @run_now;
EXEC stg.usp_stage_cotacao_record
    @duplicate, 2, 1, N'INTEGER:6908', N'{"sequence_code":6908}', @presence_value,
    N'U', NULL, NULL, '2036-01-20T10:00:00', '2036-01-20', 10.2500, NULL,
    'SP', 'RJ', N'VALID', NULL, @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @duplicate, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @duplicate, N'EXTRACTED', N'STAGED', N'STAGING_OK', @run_now;
EXEC core.usp_prepare_staged_execution
    @duplicate, N'dataexport-6906-v1', @contract, N'cotacoes-shadow-v1', @configuration;
IF NOT EXISTS (
    SELECT 1 FROM ctl.execution_promotion_result
    WHERE execution_id = @duplicate AND candidate_rows = 1 AND duplicate_rows = 1
)
    THROW 51993, N'Duplicata entre páginas não foi deduplicada.', 1;

EXEC ctl.usp_control_plane_start_execution
    @conflict, '00000000-0000-0000-0000-000000002701', N'LOCAL_SHADOW',
    N'SYNTHETIC_COTACOES_6906', @tenant_a, N'cotacoes', N'BACKFILL',
    '2036-01-01T07:00:00', '2036-01-01T08:00:00',
    N'DATA_EXPORT_RESTART_FROM_BEGINNING', N'dataexport-6906-v1', @contract,
    N'cotacoes-shadow-v1', @configuration, N'cotacoes-equal-conflict', NULL, 3600, @run_now;
EXEC ctl.usp_control_plane_record_page
    @conflict, 1, 1, 1000, 2, 1, 1024, 0, @run_now, N'NONE';
EXEC ctl.usp_control_plane_record_page
    @conflict, 2, 1, 1000, 0, 0, 64, 1, @run_now, N'DATA_EXPORT_EMPTY_PAGE';
EXEC stg.usp_stage_cotacao_record
    @conflict, 1, 1, N'INTEGER:6909', N'{"sequence_code":6909,"v":"A"}',
    @presence_value, N'U', NULL, NULL, '2036-01-20T10:00:00', '2036-01-20',
    10.2500, NULL, 'SP', 'RJ', N'VALID', NULL, @run_now;
EXEC stg.usp_stage_cotacao_record
    @conflict, 2, 1, N'INTEGER:6909', N'{"sequence_code":6909,"v":"B"}',
    @presence_value, N'U', NULL, NULL, '2036-01-20T10:00:00', '2036-01-20',
    10.2500, NULL, 'SP', 'RJ', N'VALID', NULL, @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict, N'EXTRACTING', N'EXTRACTED', N'EXTRACTION_OK', @run_now;
EXEC ctl.usp_control_plane_transition_execution
    @conflict, N'EXTRACTED', N'STAGED', N'STAGING_OK', @run_now;
EXEC core.usp_prepare_staged_execution
    @conflict, N'dataexport-6906-v1', @contract, N'cotacoes-shadow-v1', @configuration;
IF NOT EXISTS (
    SELECT 1 FROM ctl.cotacao_promotion_result
    WHERE execution_id = @conflict AND validation_state = N'BLOCKED'
      AND conflicting_root_keys = 1
) OR (
    SELECT COUNT_BIG(*) FROM recon.quarantine_record
    WHERE execution_id = @conflict AND reason_code = N'EQUAL_FRESHNESS_CONFLICT'
) <> 2 OR EXISTS (
    SELECT 1 FROM stg.execution_candidate WHERE execution_id = @conflict
)
    THROW 51994, N'Empate divergente não foi quarentenado.', 1;

-- A tuple completa não encontra current no tenant B, embora a mesma chave exista no tenant A.
IF EXISTS (
    SELECT 1 FROM core.cotacao current_record
    WHERE current_record.environment_name = N'LOCAL_SHADOW'
      AND current_record.source_instance = N'SYNTHETIC_COTACOES_6906'
      AND current_record.tenant_scope = N'SYNTHETIC_COTACOES_TENANT_B'
      AND current_record.entity_name = N'cotacoes' AND current_record.source_key = @root_key
)
    THROW 51995, N'A raiz corrente atravessou tenants antes da resolução efetiva.', 1;

-- UFs explicitamente NULL vencem o current e falham a tarifa antes do kernel comum.
DECLARE @null_route UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002711';
DECLARE @caught_tariff_failure BIT = 0;
DECLARE @partial_reconciliation BIT = 0;
BEGIN TRY
    EXEC #run_cotacao
        @null_route, '2036-01-01T08:00:00', '2036-01-01T09:00:00',
        N'cotacoes-explicit-null-route', N'BACKFILL', NULL,
        @tenant_a, @root_key,
        N'{"sequence_code":2147483648,"state":"EXPLICIT_NULL_ROUTE"}', @presence_uf_null,
        NULL, NULL, '2036-05-01T10:00:00', '2036-05-01',
        NULL, NULL, NULL, NULL, @release_b;
    THROW 51996, N'UF explicitamente NULL herdou rota corrente.', 1;
END TRY
BEGIN CATCH
    PRINT CONCAT(N'COTACOES_EXPECTED_FAILURE_XACT_STATE=', XACT_STATE(), N';TRANCOUNT=', @@TRANCOUNT);
    IF ERROR_NUMBER() = 51914
    BEGIN
        SET @caught_tariff_failure = 1;
        IF EXISTS (
            SELECT 1 FROM recon.execution_reconciliation_result
            WHERE execution_id = @null_route
        )
            SET @partial_reconciliation = 1;
    END
    ELSE
        THROW;
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 51997, N'O exercício de Cotações não encerrou o rollback integral.', 1;
IF @caught_tariff_failure = 0
    THROW 51998, N'A falha fechada para UFs NULL não foi observada.', 1;
IF @partial_reconciliation = 1
    THROW 51999, N'A falha tarifária deixou reconciliação parcial.', 1;

:r "support_cotacoes_v2_015e_cross_tenant.sql"

PRINT N'Cotações V2-027/V2-015e: BIGINT, tri-state, tarifa reference-only, isolamento físico de tenant, insert com UFs ABSENT, UFs NULL, replay, stale, dedupe e conflito validados; rollbacks integrais concluídos.';

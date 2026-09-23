-- Fase física complementar do V041. A mesma source key é semeada no tenant A;
-- um insert no tenant B com UFs ABSENT deve falhar, sem tomar rota/tarifa de A.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "005_validate_progressive_data_gate.sql"
GO
:r "040_validate_cotacoes_shadow_vertical.sql"

DECLARE @now DATETIME2(3) = SYSUTCDATETIME();
DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COTACOES_6906';
DECLARE @release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'QUOTE_TARIFF', N'LOCAL_SHADOW_SYNTHETIC', N'synthetic-cotacoes-tenant-probe', 1,
    N'DETERMINISTIC_LOCAL', N'SYNTHETIC_COTACOES_TENANT_PROBE', REPLICATE('3', 64), 1,
    '20360101', '20370101', N'SYNTHETIC_FIXTURE', N'REFERENCE_AUTHOR'
);
SET @release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.tarifa_rota_uf (
    reference_release_id, family_code, origin_uf, destination_uf, coverage_state,
    minimum_amount, currency_code, unit_code, rounding_mode, valid_from,
    valid_to_exclusive, reason_code
) VALUES (
    @release, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
    CONVERT(DECIMAL(19,4), 33.3333), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
    '20360101', '20370101', N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint,
    imported_row_count, imported_byte_count, importer_role
) VALUES (
    @release, N'governed-references-v1', REPLICATE('3', 64), 1, 512,
    N'REFERENCE_IMPORTER'
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_COTACOES_TENANT_PROBE_APPROVAL', REPLICATE('c', 64)
);

EXEC ctl.usp_control_plane_register_source @source, N'DATA_EXPORT', @now;
EXEC ctl.usp_control_plane_start_cycle
    '00000000-0000-0000-0000-000000002721', N'synthetic-cotacoes-tenant-plan-v1',
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', @now;
GO

CREATE OR ALTER PROCEDURE #install_tenant_probe_policy
    @tenant NVARCHAR(128),
    @suffix NVARCHAR(32)
AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @effective DATETIME2(3) = DATEADD(DAY, -1, SYSUTCDATETIME());
    DECLARE @environment NVARCHAR(32) = N'LOCAL_SHADOW';
    DECLARE @source NVARCHAR(128) = N'SYNTHETIC_COTACOES_6906';
    DECLARE @entity NVARCHAR(128) = N'cotacoes';
    DECLARE @mode NVARCHAR(16) = N'BACKFILL';
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

EXEC #install_tenant_probe_policy N'SYNTHETIC_COTACOES_TENANT', N'tenant-probe-a-v1';
EXEC #install_tenant_probe_policy N'SYNTHETIC_COTACOES_TENANT_B', N'tenant-probe-b-v1';
GO

CREATE OR ALTER PROCEDURE #run_tenant_probe
    @execution_id UNIQUEIDENTIFIER,
    @start DATETIME2(3),
    @finish DATETIME2(3),
    @idempotency NVARCHAR(128),
    @tenant NVARCHAR(128),
    @source_key NVARCHAR(256),
    @payload NVARCHAR(MAX),
    @presence NVARCHAR(MAX),
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
    DECLARE @contract CHAR(64) = REPLICATE('b', 64);
    DECLARE @configuration CHAR(64) = REPLICATE('c', 64);
    DECLARE @scope CHAR(64) = LOWER(CONVERT(CHAR(64), HASHBYTES(
        'SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(
            N'dq-scope-v1|', DATALENGTH(N'LOCAL_SHADOW'), N':LOCAL_SHADOW',
            N'|', DATALENGTH(N'SYNTHETIC_COTACOES_6906'), N':SYNTHETIC_COTACOES_6906',
            N'|', DATALENGTH(@tenant), N':', @tenant,
            N'|', DATALENGTH(N'cotacoes'), N':cotacoes',
            N'|', DATALENGTH(N'BACKFILL'), N':BACKFILL'))), 2));
    DECLARE @policy_version NVARCHAR(128), @policy_fingerprint CHAR(64);
    SELECT @policy_version = policy_version, @policy_fingerprint = policy_fingerprint
    FROM ctl.data_quality_policy WHERE scope_fingerprint = @scope;

    EXEC ctl.usp_control_plane_start_execution
        @execution_id, '00000000-0000-0000-0000-000000002721',
        N'LOCAL_SHADOW', N'SYNTHETIC_COTACOES_6906', @tenant, N'cotacoes',
        N'BACKFILL', @start, @finish, N'DATA_EXPORT_RESTART_FROM_BEGINNING',
        N'dataexport-6906-v1', @contract, N'cotacoes-shadow-v1', @configuration,
        @idempotency, NULL, 3600, @now;
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 1, 1, 1000, 1, 1, 512, 0, @now, N'NONE';
    EXEC ctl.usp_control_plane_record_page
        @execution_id, 2, 1, 1000, 0, 0, 64, 1, @now, N'DATA_EXPORT_EMPTY_PAGE';
    EXEC stg.usp_stage_cotacao_record
        @execution_id, 1, 1, @source_key, @payload, @presence, @user,
        NULL, NULL, @requested, @business_date, @amount, NULL, @origin,
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
        THROW 51961, N'Candidate set do probe de tenant não passou.', 1;

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
        THROW 51962, N'Data Quality do probe de tenant não passou.', 1;
    EXEC core.usp_apply_reconcile_publish_cotacoes
        @execution_id, N'dataexport-6906-v1', @contract,
        N'cotacoes-shadow-v1', @configuration, @release;
END;
GO

DECLARE @presence_value NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"VALUE","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"VALUE","qoe_qes_ony_sae_code":"VALUE","qoe_qes_diy_sae_code":"VALUE"}';
DECLARE @presence_absent NVARCHAR(MAX) =
    N'{"sequence_code":"VALUE","requested_at":"VALUE","qoe_qes_fit_nse_issued_at":"ABSENT","qoe_qes_fit_fhe_cte_issued_at":"ABSENT","qoe_qes_total":"ABSENT","qoe_crn_psn_nickname":"ABSENT","qoe_uer_name":"ABSENT","qoe_qes_ony_sae_code":"ABSENT","qoe_qes_diy_sae_code":"ABSENT"}';
DECLARE @release BIGINT = (
    SELECT reference_release_id FROM ref.reference_release
    WHERE release_version = N'synthetic-cotacoes-tenant-probe'
);
DECLARE @tenant_a NVARCHAR(128) = N'SYNTHETIC_COTACOES_TENANT';
DECLARE @tenant_b NVARCHAR(128) = N'SYNTHETIC_COTACOES_TENANT_B';
DECLARE @source_key NVARCHAR(256) = N'INTEGER:2147483648';
DECLARE @seed_a UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002722';
DECLARE @insert_b UNIQUEIDENTIFIER = '00000000-0000-0000-0000-000000002723';

EXEC #run_tenant_probe
    @seed_a, '2036-06-01T00:00:00', '2036-06-01T01:00:00',
    N'cotacoes-tenant-a-route-seed', @tenant_a, @source_key,
    N'{"sequence_code":2147483648,"tenant":"A"}', @presence_value,
    '2036-06-15T10:00:00', '2036-06-15', N'Tenant A', 7.0000, 'SP', 'RJ', @release;
IF NOT EXISTS (
    SELECT 1 FROM core.cotacao
    WHERE environment_name = N'LOCAL_SHADOW'
      AND source_instance = N'SYNTHETIC_COTACOES_6906'
      AND tenant_scope = @tenant_a AND entity_name = N'cotacoes'
      AND source_key = @source_key AND origin_uf = 'SP' AND destination_uf = 'RJ'
      AND tariff_reference_release_id = @release
)
    THROW 51963, N'O seed isolado do tenant A não foi materializado.', 1;

DECLARE @caught_cross_tenant BIT = 0;
DECLARE @cross_tenant_partial BIT = 0;
BEGIN TRY
    EXEC #run_tenant_probe
        @insert_b, '2036-06-01T01:00:00', '2036-06-01T02:00:00',
        N'cotacoes-tenant-b-absent-insert', @tenant_b, @source_key,
        N'{"sequence_code":2147483648,"tenant":"B","route":"ABSENT"}', @presence_absent,
        '2036-06-16T10:00:00', '2036-06-16', NULL, NULL, NULL, NULL, @release;
    THROW 51964, N'Insert ABSENT do tenant B tomou a rota do tenant A.', 1;
END TRY
BEGIN CATCH
    PRINT CONCAT(N'COTACOES_EXPECTED_CROSS_TENANT_FAILURE_XACT_STATE=', XACT_STATE(),
        N';TRANCOUNT=', @@TRANCOUNT);
    IF ERROR_NUMBER() = 51914
    BEGIN
        SET @caught_cross_tenant = 1;
        IF EXISTS (
            SELECT 1 FROM recon.execution_reconciliation_result
            WHERE execution_id = @insert_b
        ) OR EXISTS (
            SELECT 1 FROM core.cotacao
            WHERE environment_name = N'LOCAL_SHADOW'
              AND source_instance = N'SYNTHETIC_COTACOES_6906'
              AND tenant_scope = @tenant_b AND entity_name = N'cotacoes'
              AND source_key = @source_key
        )
            SET @cross_tenant_partial = 1;
    END
    ELSE
        THROW;
END CATCH;

IF XACT_STATE() <> 0
    ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 51965, N'O probe entre tenants não encerrou o rollback integral.', 1;
IF @caught_cross_tenant = 0
    THROW 51966, N'Insert com UFs ABSENT no tenant B não falhou fechado.', 1;
IF @cross_tenant_partial = 1
    THROW 51967, N'A falha entre tenants deixou reconciliação parcial.', 1;

PRINT N'Cotações V2-015e: insert com UFs ABSENT e isolamento físico entre tenants validados; rollback integral concluído.';

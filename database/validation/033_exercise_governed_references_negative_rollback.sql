-- Casos negativos isolados de V2-035a. Cada rejeição usa uma transação descartável própria,
-- pois THROW em trigger pode tornar o escopo atual irrecuperável. Não há valores de negócio.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51983, N'Os negativos de referências aceitam somente o banco local V2 de sombra.', 1;
GO

-- Vigências tarifárias da mesma rota/release não podem se sobrepor.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @tariff_overlap_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'QUOTE_TARIFF', N'LOCAL_NEGATIVE', N'tariff-overlap-v1', 1,
        N'AUTHORIZED_EXPORT', N'SYNTHETIC_NEGATIVE_TARIFF', REPLICATE('1', 64), 2,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_IMPORTER'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.tarifa_rota_uf VALUES
        (@release_id, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
         CONVERT(DECIMAL(19, 4), 10.0000), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
         '20360101', '20370101', N'SYNTHETIC_NEGATIVE'),
        (@release_id, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
         CONVERT(DECIMAL(19, 4), 20.0000), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
         '20360601', '20380101', N'SYNTHETIC_NEGATIVE');
END TRY
BEGIN CATCH
    DECLARE @tariff_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @tariff_error = 51940 SET @tariff_overlap_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @tariff_overlap_rejected = 0
    THROW 51984, N'Sobreposição tarifária não foi rejeitada.', 1;
GO

-- O banco rejeita uma faixa CEP inválida antes de qualquer promoção.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @invalid_cep_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'LOGISTICS_REGION', N'LOCAL_NEGATIVE', N'invalid-cep-v1', 1,
        N'AUTHORIZED_EXPORT', N'SYNTHETIC_NEGATIVE_CEP', REPLICATE('2', 64), 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_IMPORTER'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.regiao_logistica_cep VALUES (
        @release_id, N'LOGISTICS_REGION', '90000000', '10000000', N'REGION_A', 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE'
    );
END TRY
BEGIN CATCH
    DECLARE @cep_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @cep_error = 547 SET @invalid_cep_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @invalid_cep_rejected = 0
    THROW 51985, N'Faixa CEP inválida não foi rejeitada.', 1;
GO

-- Segregação de função: o papel autor não aprova a própria release.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @same_role_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'same-role-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_ROLE', REPLICATE('3', 64), 0,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_id, N'governed-references-v1', REPLICATE('3', 64), 0, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_AUTHOR',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('4', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @same_role_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @same_role_error = 51905 SET @same_role_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @same_role_rejected = 0
    THROW 51986, N'Autor conseguiu aprovar a própria release.', 1;
GO

-- Segregação de função: o papel que sela o conteúdo também não pode aprová-lo.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @same_importer_role_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'same-importer-role-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_IMPORTER_ROLE',
        REPLICATE('4', 64), 0, '20360101', '20380101',
        N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_id, N'governed-references-v1', REPLICATE('4', 64), 0, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_IMPORTER',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('5', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @same_importer_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @same_importer_error = 51905 SET @same_importer_role_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @same_importer_role_rejected = 0
    THROW 52048, N'Importador conseguiu aprovar o próprio recibo.', 1;
GO

-- O recibo sela somente cardinalidade física e fingerprint iguais ao envelope.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @row_count_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'wrong-row-count-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_ROWS', REPLICATE('5', 64), 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.reference_import_receipt (
        reference_release_id, contract_version, content_fingerprint,
        imported_row_count, imported_byte_count, importer_role
    ) VALUES (
        @release_id, N'governed-references-v1', REPLICATE('5', 64),
        1, 128, N'REFERENCE_IMPORTER'
    );
END TRY
BEGIN CATCH
    DECLARE @row_count_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @row_count_error = 51943 SET @row_count_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @row_count_rejected = 0
    THROW 51987, N'Cardinalidade física divergente não foi rejeitada.', 1;
GO

-- Conteúdo não pode ser acrescentado depois que uma release foi ratificada.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @late_content_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'late-content-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_LATE', REPLICATE('7', 64), 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.status_coleta VALUES (
        @release_id, N'PICK_STATUS', N'pending', N'legacy-pick-status-v1',
        N'pending', N'Pendente', 0,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.reference_import_receipt (
        reference_release_id, contract_version, content_fingerprint,
        imported_row_count, imported_byte_count, importer_role
    ) VALUES (
        @release_id, N'governed-references-v1', REPLICATE('7', 64),
        1, 128, N'REFERENCE_IMPORTER'
    );
    INSERT INTO ref.status_coleta VALUES (
        @release_id, N'PICK_STATUS', N'done', N'legacy-pick-status-v1',
        N'done', N'Coletada', 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE'
    );
END TRY
BEGIN CATCH
    DECLARE @late_content_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @late_content_error = 51913 SET @late_content_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @late_content_rejected = 0
    THROW 51988, N'Release ratificada aceitou conteúdo tardio.', 1;
GO

-- Uma release é append-only: correções exigem nova versão.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @release_update_rejected BIT = 0;
BEGIN TRY
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'immutable-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_IMMUTABLE', REPLICATE('9', 64), 1,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    UPDATE ref.reference_release
    SET reason_code = N'ILLEGAL_UPDATE'
    WHERE scope_code = N'LOCAL_NEGATIVE' AND release_version = N'immutable-v1';
END TRY
BEGIN CATCH
    DECLARE @release_update_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @release_update_error = 51900 SET @release_update_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @release_update_rejected = 0
    THROW 51989, N'Release governada aceitou UPDATE.', 1;
GO

-- Duas versões ratificadas do mesmo family/scope/activation não podem sobrepor vigência.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @ratified_overlap_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_a BIGINT;
    DECLARE @release_b BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES
        (N'PICK_STATUS', N'LOCAL_NEGATIVE', N'active-overlap-a', 1,
         N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_ACTIVE_A', REPLICATE('a', 64), 1,
         '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'),
        (N'PICK_STATUS', N'LOCAL_NEGATIVE', N'active-overlap-b', 1,
         N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_ACTIVE_B', REPLICATE('b', 64), 1,
         '20360601', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR');
    SELECT @release_a = reference_release_id FROM ref.reference_release
    WHERE scope_code = N'LOCAL_NEGATIVE' AND release_version = N'active-overlap-a';
    SELECT @release_b = reference_release_id FROM ref.reference_release
    WHERE scope_code = N'LOCAL_NEGATIVE' AND release_version = N'active-overlap-b';
    INSERT INTO ref.status_coleta VALUES
        (@release_a, N'PICK_STATUS', N'pending', N'legacy-pick-status-v1',
         N'pending', N'Pendente', 0,
         '20360101', '20370101', N'SYNTHETIC_NEGATIVE'),
        (@release_b, N'PICK_STATUS', N'pending', N'legacy-pick-status-v1',
         N'pending', N'Pendente', 0,
         '20360601', '20380101', N'SYNTHETIC_NEGATIVE');
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_a, N'governed-references-v1', REPLICATE('a', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_b, N'governed-references-v1', REPLICATE('b', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_a, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL_A', REPLICATE('c', 64)
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_b, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL_B', REPLICATE('d', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @ratified_overlap_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @ratified_overlap_error = 51907 SET @ratified_overlap_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @ratified_overlap_rejected = 0
    THROW 51990, N'Vigências ratificadas sobrepostas não foram rejeitadas.', 1;
GO

-- A mesma identidade com envelope divergente nunca é tratada como replay.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @divergent_replay_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    DECLARE @outcome NVARCHAR(16);
    EXEC ref.usp_register_reference_release
        @family_code = N'PICK_STATUS', @scope_code = N'LOCAL_NEGATIVE',
        @release_version = N'divergent-replay-v1', @schema_version = 1,
        @source_kind = N'DETERMINISTIC_LOCAL',
        @source_artifact_ref = N'SYNTHETIC_NEGATIVE_REPLAY',
        @source_fingerprint = '1111111111111111111111111111111111111111111111111111111111111111',
        @source_row_count = 0, @valid_from = '20360101', @valid_to_exclusive = '20370101',
        @reason_code = N'SYNTHETIC_NEGATIVE', @author_role = N'REFERENCE_AUTHOR',
        @reference_release_id = @release_id OUTPUT, @registration_outcome = @outcome OUTPUT;
    EXEC ref.usp_register_reference_release
        @family_code = N'PICK_STATUS', @scope_code = N'LOCAL_NEGATIVE',
        @release_version = N'divergent-replay-v1', @schema_version = 1,
        @source_kind = N'DETERMINISTIC_LOCAL',
        @source_artifact_ref = N'SYNTHETIC_NEGATIVE_REPLAY',
        @source_fingerprint = '2222222222222222222222222222222222222222222222222222222222222222',
        @source_row_count = 0, @valid_from = '20360101', @valid_to_exclusive = '20370101',
        @reason_code = N'SYNTHETIC_NEGATIVE', @author_role = N'REFERENCE_AUTHOR',
        @reference_release_id = @release_id OUTPUT, @registration_outcome = @outcome OUTPUT;
END TRY
BEGIN CATCH
    DECLARE @replay_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @replay_error = 52038 SET @divergent_replay_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @divergent_replay_rejected = 0
    THROW 52039, N'Replay divergente não foi rejeitado.', 1;
GO

-- Toda vigência filha deve estar contida na janela explícita da release.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @outside_release_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'outside-release-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_VALIDITY', REPLICATE('3', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.status_coleta VALUES (
        @release_id, N'PICK_STATUS', N'pending', N'legacy-pick-status-v1',
        N'pending', N'Pendente', 0, '20350101', '20370101', N'SYNTHETIC_NEGATIVE'
    );
END TRY
BEGIN CATCH
    DECLARE @outside_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @outside_error = 52011 SET @outside_release_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @outside_release_rejected = 0
    THROW 52040, N'Vigência filha fora da release não foi rejeitada.', 1;
GO

-- Calendário esparso não pode ser ratificado, mesmo quando o row_count do envelope confere.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @sparse_calendar_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'CALENDAR', N'LOCAL_NEGATIVE', N'sparse-calendar-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_CALENDAR', REPLICATE('4', 64), 2,
        '20360107', '20360110', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.calendario (
        reference_release_id, family_code, calendar_date, date_key, iso_weekday,
        is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
        billing_reference_date
    )
    SELECT @release_id, N'CALENDAR', calendar_date, date_key, iso_weekday,
           is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
           billing_reference_date
    FROM ref.ufn_calendar_seed_candidate_v1('20360107', '20360110')
    WHERE calendar_date <> CONVERT(DATE, '20360108', 112);
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_id, N'governed-references-v1', REPLICATE('4', 64), 2, 256,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('5', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @calendar_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @calendar_error = 51944 SET @sparse_calendar_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @sparse_calendar_rejected = 0
    THROW 52041, N'Calendário esparso foi ratificado.', 1;
GO

-- Dia não útil deve apontar exatamente para o último dia útil, não apenas para algum dia anterior.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @wrong_billing_reference_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'CALENDAR', N'LOCAL_NEGATIVE', N'wrong-billing-reference-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_BILLING', REPLICATE('5', 64), 7,
        '20360107', '20360114', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.calendario (
        reference_release_id, family_code, calendar_date, date_key, iso_weekday,
        is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
        billing_reference_date
    )
    SELECT @release_id, N'CALENDAR', calendar_date, date_key, iso_weekday,
           is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
           CASE WHEN calendar_date IN ('20360112', '20360113')
                THEN CONVERT(DATE, '20360110', 112) ELSE billing_reference_date END
    FROM ref.ufn_calendar_seed_candidate_v1('20360107', '20360114');
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_id, N'governed-references-v1', REPLICATE('5', 64), 7, 768,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('6', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @billing_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @billing_error = 51944 SET @wrong_billing_reference_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @wrong_billing_reference_rejected = 0
    THROW 52043, N'Referência útil não exata foi ratificada.', 1;
GO

-- Atribuição só ativa se sua release de filiais estiver ativa no mesmo escopo.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @branch_dependency_rejected BIT = 0;
BEGIN TRY
    DECLARE @branch_release BIGINT;
    DECLARE @attribution_release BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_OPERATIONS', N'LOCAL_NEGATIVE', N'branch-dependency-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_NEGATIVE_BRANCH', REPLICATE('6', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @branch_release = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.filial_operacional VALUES (
        @branch_release, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
        N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.reference_import_receipt VALUES (
        @branch_release, N'governed-references-v1', REPLICATE('6', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_ATTRIBUTION', N'LOCAL_NEGATIVE', N'attribution-dependency-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_NEGATIVE_ATTRIBUTION', REPLICATE('7', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @attribution_release = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.atribuicao_filial VALUES (
        @attribution_release, N'BRANCH_ATTRIBUTION', REPLICATE('a', 64),
        N'hmac-sha256-synthetic-v1', @branch_release, N'BRANCH_A',
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.reference_import_receipt VALUES (
        @attribution_release, N'governed-references-v1', REPLICATE('7', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @attribution_release, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('8', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @dependency_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @dependency_error = 51945 SET @branch_dependency_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @branch_dependency_rejected = 0
    THROW 52042, N'Atribuição ativou sem release de filiais no mesmo escopo.', 1;
GO

-- Somente schema_version 1 pertence ao contrato governed-references-v1.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @unsupported_schema_rejected BIT = 0;
BEGIN TRY
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'PICK_STATUS', N'LOCAL_NEGATIVE', N'unsupported-schema-v2', 2,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_SCHEMA', REPLICATE('1', 64), 0,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
END TRY
BEGIN CATCH
    DECLARE @schema_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @schema_error = 547 SET @unsupported_schema_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @unsupported_schema_rejected = 0
    THROW 52044, N'Schema de referência desconhecido foi aceito.', 1;
GO

-- A versão diferencia chaves canônicas; dentro da mesma versão o overlap continua proibido.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @same_normalization_overlap_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_OPERATIONS', N'LOCAL_NEGATIVE', N'alias-version-overlap-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_NEGATIVE_ALIAS_VERSION', REPLICATE('1', 64), 3,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.filial_operacional VALUES (
        @release_id, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
        N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.regiao_destino_alias VALUES
        (@release_id, N'BRANCH_OPERATIONS', N'DESTINATION', N'ALIAS_SYNTHETIC',
         N'owner-canonical-v1', N'BRANCH_A', '20360101', '20370101',
         N'SYNTHETIC_NEGATIVE'),
        (@release_id, N'BRANCH_OPERATIONS', N'DESTINATION', N'ALIAS_SYNTHETIC',
         N'owner-canonical-v1', N'BRANCH_A', '20360601', '20380101',
         N'SYNTHETIC_NEGATIVE');
END TRY
BEGIN CATCH
    DECLARE @same_normalization_overlap_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @same_normalization_overlap_error = 51919
        SET @same_normalization_overlap_rejected = 1;
    ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @same_normalization_overlap_rejected = 0
    THROW 52050, N'Alias na mesma normalization_version aceitou overlap.', 1;
GO

-- A versão diferencia tokens; dentro do mesmo esquema o overlap continua proibido.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @same_token_scheme_overlap_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_OPERATIONS', N'LOCAL_NEGATIVE', N'token-version-overlap-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_NEGATIVE_TOKEN_VERSION', REPLICATE('2', 64), 3,
        '20360101', '20380101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.filial_operacional VALUES (
        @release_id, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
        N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.filial_operacional_documento VALUES
        (@release_id, N'BRANCH_OPERATIONS', REPLICATE('a', 64),
         N'hmac-sha256-synthetic-v1', N'BRANCH_A', '20360101', '20370101',
         N'SYNTHETIC_NEGATIVE'),
        (@release_id, N'BRANCH_OPERATIONS', REPLICATE('a', 64),
         N'hmac-sha256-synthetic-v1', N'BRANCH_A', '20360601', '20380101',
         N'SYNTHETIC_NEGATIVE');
END TRY
BEGIN CATCH
    DECLARE @same_token_scheme_overlap_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @same_token_scheme_overlap_error = 51922
        SET @same_token_scheme_overlap_rejected = 1;
    ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @same_token_scheme_overlap_rejected = 0
    THROW 52051, N'Token no mesmo token_scheme_version aceitou overlap.', 1;
GO

-- Um lookback antigo/esparso não substitui o último dia útil imediatamente anterior.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @stale_lookback_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'CALENDAR', N'LOCAL_NEGATIVE', N'stale-lookback-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_LOOKBACK', REPLICATE('2', 64), 4,
        '20360105', '20360108', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.calendario (
        reference_release_id, family_code, calendar_date, date_key, iso_weekday,
        is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
        billing_reference_date
    )
    SELECT @release_id, N'CALENDAR', calendar_date, date_key, iso_weekday,
           is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
           CASE WHEN calendar_date IN ('20360105', '20360106')
                THEN CONVERT(DATE, '20360103', 112) ELSE billing_reference_date END
    FROM ref.ufn_calendar_seed_candidate_v1('20360103', '20360108')
    WHERE calendar_date <> CONVERT(DATE, '20360104', 112);
    INSERT INTO ref.reference_import_receipt VALUES (
        @release_id, N'governed-references-v1', REPLICATE('2', 64), 4, 512,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @release_id, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_NEGATIVE_APPROVAL', REPLICATE('3', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @lookback_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @lookback_error = 51944 SET @stale_lookback_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @stale_lookback_rejected = 0
    THROW 52045, N'Lookback esparso ou anterior ao último dia útil foi ratificado.', 1;
GO

-- Dia de semana só pode ser não útil quando uma política de feriado o justifica.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @weekday_without_holiday_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'CALENDAR', N'LOCAL_NEGATIVE', N'weekday-without-holiday-v1', 1,
        N'DETERMINISTIC_LOCAL', N'SYNTHETIC_NEGATIVE_WEEKDAY', REPLICATE('3', 64), 2,
        '20360104', '20360108', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.calendario VALUES (
        @release_id, N'CALENDAR', '20360104', 20360104, 5, 0,
        N'NONE', NULL, NULL, 1, '20360104'
    );
    INSERT INTO ref.calendario VALUES (
        @release_id, N'CALENDAR', '20360107', 20360107, 1, 0,
        N'NONE', NULL, NULL, 0, '20360104'
    );
END TRY
BEGIN CATCH
    DECLARE @weekday_without_holiday_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @weekday_without_holiday_error = 547
        SET @weekday_without_holiday_rejected = 1;
    ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @weekday_without_holiday_rejected = 0
    THROW 52049, N'Dia útil sem feriado foi classificado como não útil.', 1;
GO

-- O eixo de veículo comprovado é AGGREGATE/DRIVER; COMPANY pertence ao eixo motorista.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @fleet_axis_rejected BIT = 0;
BEGIN TRY
    DECLARE @release_id BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'OWNED_FLEET', N'LOCAL_NEGATIVE', N'invalid-fleet-axis-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_NEGATIVE_FLEET', REPLICATE('4', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @release_id = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.classificacao_frota_matriz (
        reference_release_id, family_code, classification_scope,
        vehicle_contract_class, driver_contract_class, classification_code,
        valid_from, valid_to_exclusive, reason_code
    ) VALUES (
        @release_id, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
        N'COMPANY', N'THIRD_PARTY', N'FLEET',
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE'
    );
END TRY
BEGIN CATCH
    DECLARE @fleet_axis_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @fleet_axis_error = 547 SET @fleet_axis_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @fleet_axis_rejected = 0
    THROW 52046, N'Matriz de frota aceitou classe do eixo errado.', 1;
GO

-- Uma filial não pode ser revogada enquanto sustentar atribuição ativa no mesmo escopo.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
GO
DECLARE @active_branch_revocation_rejected BIT = 0;
BEGIN TRY
    DECLARE @branch_release BIGINT;
    DECLARE @attribution_release BIGINT;
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_OPERATIONS', N'LOCAL_NEGATIVE', N'active-branch-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_ACTIVE_BRANCH', REPLICATE('5', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @branch_release = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.filial_operacional VALUES (
        @branch_release, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
        N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.reference_import_receipt VALUES (
        @branch_release, N'governed-references-v1', REPLICATE('5', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @branch_release, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_BRANCH_APPROVAL', REPLICATE('6', 64)
    );
    INSERT INTO ref.reference_release (
        family_code, scope_code, release_version, schema_version, source_kind,
        source_artifact_ref, source_fingerprint, source_row_count, valid_from,
        valid_to_exclusive, reason_code, author_role
    ) VALUES (
        N'BRANCH_ATTRIBUTION', N'LOCAL_NEGATIVE', N'active-attribution-v1', 1,
        N'OWNER_AUTHORED', N'SYNTHETIC_ACTIVE_ATTRIBUTION', REPLICATE('7', 64), 1,
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE', N'REFERENCE_AUTHOR'
    );
    SET @attribution_release = CONVERT(BIGINT, SCOPE_IDENTITY());
    INSERT INTO ref.atribuicao_filial VALUES (
        @attribution_release, N'BRANCH_ATTRIBUTION', REPLICATE('a', 64),
        N'hmac-sha256-synthetic-v1', @branch_release, N'BRANCH_A',
        '20360101', '20370101', N'SYNTHETIC_NEGATIVE'
    );
    INSERT INTO ref.reference_import_receipt VALUES (
        @attribution_release, N'governed-references-v1', REPLICATE('7', 64), 1, 128,
        N'REFERENCE_IMPORTER', SYSUTCDATETIME()
    );
    INSERT INTO ref.reference_release_ratification (
        reference_release_id, activation_scope, approver_role,
        approval_evidence_ref, approval_fingerprint
    ) VALUES (
        @attribution_release, N'SHADOW', N'REFERENCE_APPROVER',
        N'SYNTHETIC_ATTRIBUTION_APPROVAL', REPLICATE('8', 64)
    );
    INSERT INTO ref.reference_release_revocation (
        reference_release_id, activation_scope, revoker_role, reason_code,
        evidence_ref, evidence_fingerprint
    ) VALUES (
        @branch_release, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
        N'SYNTHETIC_BRANCH_REVOCATION', REPLICATE('9', 64)
    );
END TRY
BEGIN CATCH
    DECLARE @active_branch_error INT = ERROR_NUMBER();
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    IF @active_branch_error = 51948 SET @active_branch_revocation_rejected = 1; ELSE THROW;
END CATCH;
IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
IF @active_branch_revocation_rejected = 0
    THROW 52047, N'Filial ativa foi revogada antes de sua atribuição dependente.', 1;
GO

IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51991, N'Os negativos de referências deixaram transação aberta.', 1;

PRINT N'Negativos de referências governadas rejeitados e revertidos em escopos isolados.';

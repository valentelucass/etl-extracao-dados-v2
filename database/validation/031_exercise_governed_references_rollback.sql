-- Exercício sintético e rollback-only de V2-035a. Não contém documentos ou valores de negócio.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51960, N'O exercício de referências aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "030_validate_governed_references.sql"
GO

IF EXISTS (SELECT 1 FROM ref.reference_release)
   OR EXISTS (SELECT 1 FROM ref.reference_import_receipt)
   OR EXISTS (SELECT 1 FROM ref.reference_release_ratification)
   OR EXISTS (SELECT 1 FROM ref.reference_release_revocation)
   OR EXISTS (SELECT 1 FROM ref.calendario)
   OR EXISTS (SELECT 1 FROM ref.status_coleta)
   OR EXISTS (SELECT 1 FROM ref.frota_propria_documento)
   OR EXISTS (SELECT 1 FROM ref.classificacao_frota_alias)
   OR EXISTS (SELECT 1 FROM ref.classificacao_frota_matriz)
   OR EXISTS (SELECT 1 FROM ref.classificacao_frota_excecao_token)
   OR EXISTS (SELECT 1 FROM ref.regiao_logistica_cep)
   OR EXISTS (SELECT 1 FROM ref.tarifa_rota_uf)
    THROW 51961, N'V008 criou conteúdo ou ativação por default.', 1;

DECLARE @scope NVARCHAR(128) = N'LOCAL_SHADOW_SYNTHETIC';
DECLARE @valid_from DATE = CONVERT(DATE, '20360101', 112);
DECLARE @valid_to DATE = CONVERT(DATE, '20380101', 112);
DECLARE @calendar_start DATE = CONVERT(DATE, '20360105', 112);
DECLARE @calendar_end DATE = CONVERT(DATE, '20360303', 112);
DECLARE @token_a CHAR(64) = REPLICATE('a', 64);
DECLARE @token_b CHAR(64) = REPLICATE('b', 64);
DECLARE @token_c CHAR(64) = REPLICATE('c', 64);
DECLARE @token_d CHAR(64) = REPLICATE('d', 64);
DECLARE @token_f CHAR(64) = REPLICATE('f', 64);
DECLARE @calendar_rows BIGINT = (
    SELECT COUNT_BIG(*) FROM ref.ufn_calendar_seed_candidate_v1(@calendar_start, @calendar_end)
);

IF (SELECT COUNT_BIG(*) FROM ref.ufn_calendar_seed_candidate_v1(
        @calendar_start, @calendar_end
    ) WHERE calendar_date >= @calendar_start) <> DATEDIFF(DAY, @calendar_start, @calendar_end)
   OR (SELECT MIN(calendar_date) FROM ref.ufn_calendar_seed_candidate_v1(
       @calendar_start, @calendar_end)) <> CONVERT(DATE, '20360104', 112)
   OR (SELECT COUNT_BIG(*) FROM ref.ufn_calendar_seed_candidate_v1(
       '20300101', DATEADD(DAY, 3660, CONVERT(DATE, '20300101', 112)))
       WHERE calendar_date >= CONVERT(DATE, '20300101', 112)) <> 3660
   OR EXISTS (SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1(
       '20300101', DATEADD(DAY, 3661, CONVERT(DATE, '20300101', 112))))
    THROW 51962, N'O seed rolante violou intervalo ou limite explícito de 3.660 dias.', 1;
IF NOT EXISTS (
    SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1(@calendar_start, @calendar_end)
    WHERE calendar_date = CONVERT(DATE, '20360229', 112) AND date_key = 20360229
)
    THROW 51963, N'O seed rolante pós-2032 perdeu o dia bissexto.', 1;
IF NOT EXISTS (
    SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20360101', '20370101')
    WHERE holiday_code = N'GOOD_FRIDAY' AND holiday_kind = N'NATIONAL'
)
   OR EXISTS (
    SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20230101', '20240101')
    WHERE holiday_code = N'BLACK_CONSCIOUSNESS_DAY'
)
   OR NOT EXISTS (
    SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20240101', '20250101')
    WHERE holiday_code = N'BLACK_CONSCIOUSNESS_DAY'
)
    THROW 51963, N'Feriados móveis ou regra temporal nacional divergiram.', 1;
IF ref.ufn_normalize_pick_status_v1(N' DONE ') <> N'done'
   OR ref.ufn_normalize_pick_status_v1(N' IN-TRANSIT ') <> N'in_transit'
   OR ref.ufn_normalize_pick_status_v1(N'in-transit') <> N'in_transit'
   OR ref.ufn_normalize_pick_status_v1(N'in  transit') <> N'in__transit'
   OR ref.ufn_normalize_pick_status_v1(N'inválido') IS NOT NULL
    THROW 51964, N'Normalização explícita de status divergiu do comportamento legado ASCII.', 1;

DECLARE @calendar_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'CALENDAR', @scope, N'synthetic-calendar-v1', 1, N'DETERMINISTIC_LOCAL',
    N'SYNTHETIC_CALENDAR_V1', REPLICATE('1', 64), @calendar_rows,
    @calendar_start, @calendar_end, N'SYNTHETIC_FIXTURE', N'DATA_ENGINEER'
);
SET @calendar_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.calendario (
    reference_release_id, family_code, calendar_date, date_key, iso_weekday,
    is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
    billing_reference_date
)
SELECT @calendar_release, N'CALENDAR', calendar_date, date_key, iso_weekday,
       is_weekend, holiday_kind, holiday_code, holiday_label, is_business_day,
       billing_reference_date
FROM ref.ufn_calendar_seed_candidate_v1(@calendar_start, @calendar_end);
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint, imported_row_count,
    imported_byte_count, importer_role
) VALUES (
    @calendar_release, N'governed-references-v1', REPLICATE('1', 64),
    @calendar_rows, 4096, N'REFERENCE_IMPORTER'
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @calendar_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_CALENDAR_APPROVAL', REPLICATE('a', 64)
);
IF EXISTS (
    SELECT 1
    FROM ref.calendario AS calendar_row
    INNER JOIN ref.calendario AS billing_day
        ON billing_day.reference_release_id = calendar_row.reference_release_id
       AND billing_day.calendar_date = calendar_row.billing_reference_date
    WHERE calendar_row.reference_release_id = @calendar_release
      AND calendar_row.is_business_day = 0
      AND (billing_day.is_business_day <> 1 OR billing_day.calendar_date >= calendar_row.calendar_date)
)
    THROW 51965, N'A referência de faturamento não apontou para o último dia útil anterior.', 1;

-- O registro do envelope é realmente idempotente: criação seguida de replay retorna o mesmo id.
DECLARE @status_release BIGINT;
DECLARE @status_replay_release BIGINT;
DECLARE @registration_outcome NVARCHAR(16);
EXEC ref.usp_register_reference_release
    @family_code = N'PICK_STATUS', @scope_code = @scope,
    @release_version = N'synthetic-pick-status-v1', @schema_version = 1,
    @source_kind = N'DETERMINISTIC_LOCAL',
    @source_artifact_ref = N'SYNTHETIC_PICK_STATUS_V1',
    @source_fingerprint = '2222222222222222222222222222222222222222222222222222222222222222',
    @source_row_count = 9, @valid_from = @valid_from, @valid_to_exclusive = @valid_to,
    @reason_code = N'SYNTHETIC_FIXTURE', @author_role = N'DATA_ENGINEER',
    @reference_release_id = @status_release OUTPUT,
    @registration_outcome = @registration_outcome OUTPUT;
IF @registration_outcome <> N'CREATED' THROW 51966, N'Primeiro registro não criou a release.', 1;
EXEC ref.usp_register_reference_release
    @family_code = N'PICK_STATUS', @scope_code = @scope,
    @release_version = N'synthetic-pick-status-v1', @schema_version = 1,
    @source_kind = N'DETERMINISTIC_LOCAL',
    @source_artifact_ref = N'SYNTHETIC_PICK_STATUS_V1',
    @source_fingerprint = '2222222222222222222222222222222222222222222222222222222222222222',
    @source_row_count = 9, @valid_from = @valid_from, @valid_to_exclusive = @valid_to,
    @reason_code = N'SYNTHETIC_FIXTURE', @author_role = N'DATA_ENGINEER',
    @reference_release_id = @status_replay_release OUTPUT,
    @registration_outcome = @registration_outcome OUTPUT;
IF @registration_outcome <> N'REPLAY' OR @status_replay_release <> @status_release
    THROW 51967, N'Replay exato não retornou a mesma identidade sem duplicação.', 1;

INSERT INTO ref.status_coleta (
    reference_release_id, family_code, raw_status, normalization_version,
    canonical_status, display_label, is_terminal, valid_from, valid_to_exclusive, reason_code
)
SELECT @status_release, N'PICK_STATUS', raw_status, normalization_version,
       canonical_status, display_label, is_terminal, @valid_from, @valid_to,
       N'DETERMINISTIC_CANDIDATE'
FROM ref.v_status_coleta_seed_candidate_v1;
INSERT INTO ref.reference_import_receipt (
    reference_release_id, contract_version, content_fingerprint, imported_row_count,
    imported_byte_count, importer_role
) VALUES (
    @status_release, N'governed-references-v1', REPLICATE('2', 64), 9, 2048,
    N'REFERENCE_IMPORTER'
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @status_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_STATUS_SHADOW', REPLICATE('b', 64)
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @status_release, N'PRODUCTION', N'REFERENCE_APPROVER',
    N'SYNTHETIC_STATUS_PRODUCTION', REPLICATE('c', 64)
);
INSERT INTO ref.reference_release_revocation (
    reference_release_id, activation_scope, revoker_role, reason_code, evidence_ref,
    evidence_fingerprint
) VALUES (
    @status_release, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
    N'SYNTHETIC_STATUS_REVOCATION', REPLICATE('d', 64)
);
IF EXISTS (
    SELECT 1 FROM ref.reference_release_revocation
    WHERE reference_release_id = @status_release AND activation_scope = N'PRODUCTION'
)
   OR NOT EXISTS (
    SELECT 1 FROM ref.reference_release_ratification
    WHERE reference_release_id = @status_release AND activation_scope = N'PRODUCTION'
)
    THROW 51968, N'Revogação SHADOW afetou indevidamente a ativação PRODUCTION.', 1;

DECLARE @branch_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_OPERATIONS', @scope, N'synthetic-branches-v1', 1, N'OWNER_AUTHORED',
    N'SYNTHETIC_BRANCHES_V1', REPLICATE('3', 64), 5,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @branch_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.filial_operacional (
    reference_release_id, family_code, branch_code, branch_label, reason_code
) VALUES (
    @branch_release, N'BRANCH_OPERATIONS', N'BRANCH_A', N'Filial sintética A',
    N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.regiao_destino_alias (
    reference_release_id, family_code, alias_scope, normalized_alias,
    normalization_version, branch_code, valid_from, valid_to_exclusive, reason_code
) VALUES
    (@branch_release, N'BRANCH_OPERATIONS', N'DESTINATION', N'ALIAS_SYNTHETIC_A',
     N'uppercase-ascii-v1', N'BRANCH_A', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@branch_release, N'BRANCH_OPERATIONS', N'DESTINATION', N'ALIAS_SYNTHETIC_A',
     N'owner-canonical-v2', N'BRANCH_A', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE');
INSERT INTO ref.filial_operacional_documento (
    reference_release_id, family_code, document_token, token_scheme_version,
    branch_code, valid_from, valid_to_exclusive, reason_code
) VALUES
    (@branch_release, N'BRANCH_OPERATIONS', @token_a, N'hmac-sha256-synthetic-v1',
     N'BRANCH_A', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@branch_release, N'BRANCH_OPERATIONS', @token_a, N'hmac-sha256-synthetic-v2',
     N'BRANCH_A', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE');
IF (SELECT COUNT_BIG(*) FROM ref.regiao_destino_alias
    WHERE reference_release_id = @branch_release
      AND alias_scope = N'DESTINATION'
      AND normalized_alias = N'ALIAS_SYNTHETIC_A') <> 2
   OR (SELECT COUNT_BIG(*) FROM ref.filial_operacional_documento
       WHERE reference_release_id = @branch_release
          AND (document_token) = @token_a) <> 2
    THROW 51984, N'Versões distintas de chave canônica ou token não coexistiram.', 1;
INSERT INTO ref.reference_import_receipt VALUES (
    @branch_release, N'governed-references-v1', REPLICATE('3', 64), 5, 1536,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @branch_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_BRANCH_APPROVAL', REPLICATE('e', 64)
);

DECLARE @fleet_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'OWNED_FLEET', @scope, N'synthetic-fleet-v1', 1, N'AUTHORIZED_EXPORT',
    N'SYNTHETIC_FLEET_V1', REPLICATE('4', 64), 19,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @fleet_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.frota_propria_documento (
    reference_release_id, family_code, classification_scope,
    document_token, token_scheme_version,
    valid_from, valid_to_exclusive, reason_code
) VALUES (
    @fleet_release, N'OWNED_FLEET', N'DRIVER_OWNERSHIP',
    @token_b, N'hmac-sha256-synthetic-v1',
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.classificacao_frota_alias (
    reference_release_id, family_code, classification_scope, alias_scope, normalized_alias,
    normalization_version, contract_class, valid_from, valid_to_exclusive, reason_code
) VALUES
    (@fleet_release, N'OWNED_FLEET', N'DRIVER_OWNERSHIP',
     N'DRIVER_OWNERSHIP_CONTRACT', N'OWNERSHIP_AGGREGATE', N'synthetic-exact-bin2-v1',
     N'AGGREGATE', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'DRIVER_OWNERSHIP',
     N'DRIVER_OWNERSHIP_CONTRACT', N'OWNERSHIP_OTHER', N'synthetic-exact-bin2-v1',
     N'THIRD_PARTY', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'VEHICLE_CONTRACT', N'VEHICLE_AGGREGATE', N'synthetic-exact-bin2-v1',
     N'AGGREGATE', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'VEHICLE_CONTRACT', N'VEHICLE_DRIVER', N'synthetic-exact-bin2-v1',
     N'DRIVER', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER_CONTRACT', N'DRIVER_COMPANY', N'synthetic-exact-bin2-v1',
     N'COMPANY', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER_CONTRACT', N'DRIVER_AGGREGATE', N'synthetic-exact-bin2-v1',
     N'AGGREGATE', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER_CONTRACT', N'DRIVER_THIRD_PARTY', N'synthetic-exact-bin2-v1',
     N'THIRD_PARTY', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER_CONTRACT', N'DRIVER_UNSPECIFIED', N'synthetic-exact-bin2-v1',
     N'UNSPECIFIED', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE');
INSERT INTO ref.classificacao_frota_matriz (
    reference_release_id, family_code, classification_scope,
    vehicle_contract_class, driver_contract_class, classification_code,
    valid_from, valid_to_exclusive, reason_code
) VALUES
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'AGGREGATE', N'UNSPECIFIED', N'THIRD_PARTY', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'AGGREGATE', N'AGGREGATE', N'AGGREGATE', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'AGGREGATE', N'THIRD_PARTY', N'THIRD_PARTY', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'AGGREGATE', N'COMPANY', N'FLEET', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER', N'UNSPECIFIED', N'FLEET_PLUS_PX', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER', N'AGGREGATE', N'FLEET_PLUS_PX', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER', N'THIRD_PARTY', N'FLEET_PLUS_PX', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT',
     N'DRIVER', N'COMPANY', N'FLEET', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE');
INSERT INTO ref.classificacao_frota_excecao_token (
    reference_release_id, family_code, classification_scope, exception_scope,
    subject_token, token_scheme_version, required_vehicle_contract_class,
    classification_code, priority, valid_from, valid_to_exclusive, reason_code
) VALUES
    (@fleet_release, N'OWNED_FLEET', N'DRIVER_OWNERSHIP', N'OWNER_NAME_TOKEN',
     @token_f, N'hmac-sha256-synthetic-v1', NULL, N'FLEET', 1,
     @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'),
    (@fleet_release, N'OWNED_FLEET', N'VEHICLE_DRIVER_CONTRACT', N'OWNER_NAME_TOKEN',
     @token_f, N'hmac-sha256-synthetic-v1', N'AGGREGATE', N'FLEET_PLUS_PX', 1,
     @valid_from, @valid_to, N'SYNTHETIC_FIXTURE');
IF (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_matriz
    WHERE reference_release_id = @fleet_release) <> 8
   OR NOT EXISTS (
       SELECT 1 FROM ref.classificacao_frota_alias
       WHERE reference_release_id = @fleet_release
         AND classification_scope = N'VEHICLE_DRIVER_CONTRACT'
         AND alias_scope = N'VEHICLE_CONTRACT' AND contract_class = N'DRIVER'
   )
   OR (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_excecao_token
       WHERE reference_release_id = @fleet_release) <> 2
    THROW 51981, N'Políticas independentes e matriz sintética 2x4 divergiram.', 1;
INSERT INTO ref.reference_import_receipt VALUES (
    @fleet_release, N'governed-references-v1', REPLICATE('4', 64), 19, 8192,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @fleet_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_FLEET_APPROVAL', REPLICATE('f', 64)
);

DECLARE @attribution_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'BRANCH_ATTRIBUTION', @scope, N'synthetic-attribution-v1', 1,
    N'AUTHORIZED_EXPORT', N'SYNTHETIC_ATTRIBUTION_V1', REPLICATE('5', 64), 1,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @attribution_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.atribuicao_filial (
    reference_release_id, family_code, payer_document_token, token_scheme_version,
    branch_reference_release_id, branch_code, valid_from, valid_to_exclusive, reason_code
) VALUES (
    @attribution_release, N'BRANCH_ATTRIBUTION', @token_c,
    N'hmac-sha256-synthetic-v1', @branch_release, N'BRANCH_A', @valid_from, @valid_to,
    N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @attribution_release, N'governed-references-v1', REPLICATE('5', 64), 1, 512,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @attribution_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_ATTRIBUTION_APPROVAL', REPLICATE('1', 64)
);
INSERT INTO ref.reference_release_revocation (
    reference_release_id, activation_scope, revoker_role, reason_code,
    evidence_ref, evidence_fingerprint
) VALUES (
    @attribution_release, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
    N'SYNTHETIC_ATTRIBUTION_REVOCATION', REPLICATE('3', 64)
);
INSERT INTO ref.reference_release_revocation (
    reference_release_id, activation_scope, revoker_role, reason_code,
    evidence_ref, evidence_fingerprint
) VALUES (
    @branch_release, N'SHADOW', N'REFERENCE_APPROVER', N'SYNTHETIC_REVOKE',
    N'SYNTHETIC_BRANCH_REVOCATION', REPLICATE('4', 64)
);
IF NOT EXISTS (
    SELECT 1 FROM ref.reference_release_revocation
    WHERE reference_release_id = @branch_release AND activation_scope = N'SHADOW'
)
    THROW 51982, N'Revogação ordenada atribuição→filial não foi preservada.', 1;

DECLARE @cubage_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'CUBAGE_EXCLUSION', @scope, N'synthetic-cubage-v1', 1,
    N'AUTHORIZED_EXPORT', N'SYNTHETIC_CUBAGE_V1', REPLICATE('6', 64), 1,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @cubage_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.pagador_exclusao_cubagem (
    reference_release_id, family_code, payer_document_token, token_scheme_version,
    valid_from, valid_to_exclusive, reason_code
) VALUES (
    @cubage_release, N'CUBAGE_EXCLUSION', @token_d,
    N'hmac-sha256-synthetic-v1', @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @cubage_release, N'governed-references-v1', REPLICATE('6', 64), 1, 512,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);

DECLARE @region_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'LOGISTICS_REGION', @scope, N'synthetic-region-v1', 1,
    N'AUTHORIZED_EXPORT', N'SYNTHETIC_REGION_V1', REPLICATE('7', 64), 2,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @region_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.regiao_logistica_cep (
    reference_release_id, family_code, cep_start, cep_end, region_code,
    priority, valid_from, valid_to_exclusive, reason_code
) VALUES (
    @region_release, N'LOGISTICS_REGION', '00000000', '00000009',
    N'REGION_CEP', 1, @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.regiao_logistica_cidade_uf (
    reference_release_id, family_code, normalized_city, city_normalization_version,
    uf, region_code, priority, valid_from, valid_to_exclusive, reason_code
) VALUES (
    @region_release, N'LOGISTICS_REGION', N'CIDADE_SYNTHETIC',
    N'uppercase-ascii-v1', 'SP', N'REGION_CITY', 2,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE'
);
INSERT INTO ref.reference_import_receipt VALUES (
    @region_release, N'governed-references-v1', REPLICATE('7', 64), 2, 1024,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);

DECLARE @tariff_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'QUOTE_TARIFF', @scope, N'synthetic-tariff-v1', 1,
    N'AUTHORIZED_EXPORT', N'SYNTHETIC_TARIFF_V1', REPLICATE('8', 64), 3,
    @valid_from, @valid_to, N'SYNTHETIC_FIXTURE', N'REFERENCE_IMPORTER'
);
SET @tariff_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.tarifa_rota_uf (
    reference_release_id, family_code, origin_uf, destination_uf, coverage_state,
    minimum_amount, currency_code, unit_code, rounding_mode, valid_from,
    valid_to_exclusive, reason_code
) VALUES
    (@tariff_release, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
     CONVERT(DECIMAL(19, 4), 12.3400), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
     CONVERT(DATE, '20360101', 112), CONVERT(DATE, '20370101', 112),
     N'SYNTHETIC_FIXTURE'),
    (@tariff_release, N'QUOTE_TARIFF', 'SP', 'RJ', N'PRICED',
     CONVERT(DECIMAL(19, 4), 23.4500), 'BRL', N'PER_SHIPMENT', N'HALF_UP',
     CONVERT(DATE, '20370101', 112), CONVERT(DATE, '20380101', 112),
     N'SYNTHETIC_FIXTURE'),
    (@tariff_release, N'QUOTE_TARIFF', 'RJ', 'SP', N'UNAVAILABLE',
     NULL, 'BRL', N'PER_SHIPMENT', N'HALF_UP', @valid_from, @valid_to,
     N'SYNTHETIC_FIXTURE');
INSERT INTO ref.reference_import_receipt VALUES (
    @tariff_release, N'governed-references-v1', REPLICATE('8', 64), 3, 1536,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
IF EXISTS (
    SELECT 1 FROM ref.tarifa_rota_uf
    WHERE reference_release_id = @tariff_release
      AND origin_uf = 'AC' AND destination_uf = 'AL'
)
    THROW 51969, N'Combinação tarifária ausente recebeu fallback.', 1;

-- Baseline autorizada vazia é representável, selável e ratificável sem sentinela.
DECLARE @empty_release BIGINT;
INSERT INTO ref.reference_release (
    family_code, scope_code, release_version, schema_version, source_kind,
    source_artifact_ref, source_fingerprint, source_row_count, valid_from,
    valid_to_exclusive, reason_code, author_role
) VALUES (
    N'CUBAGE_EXCLUSION', N'LOCAL_SHADOW_SYNTHETIC_EMPTY', N'synthetic-empty-v1', 1,
    N'OWNER_AUTHORED', N'SYNTHETIC_EMPTY_V1', REPLICATE('9', 64), 0,
    @valid_from, @valid_to, N'SYNTHETIC_EMPTY_BASELINE', N'REFERENCE_IMPORTER'
);
SET @empty_release = CONVERT(BIGINT, SCOPE_IDENTITY());
INSERT INTO ref.reference_import_receipt VALUES (
    @empty_release, N'governed-references-v1', REPLICATE('9', 64), 0, 128,
    N'REFERENCE_IMPORTER', SYSUTCDATETIME()
);
INSERT INTO ref.reference_release_ratification (
    reference_release_id, activation_scope, approver_role,
    approval_evidence_ref, approval_fingerprint
) VALUES (
    @empty_release, N'SHADOW', N'REFERENCE_APPROVER',
    N'SYNTHETIC_EMPTY_APPROVAL', REPLICATE('2', 64)
);

-- As falhas de permissão seguintes são esperadas e não podem consumir o escopo externo.
SET XACT_ABORT OFF;
CREATE USER v2_reference_runtime_probe WITHOUT LOGIN;
ALTER ROLE v2_runtime ADD MEMBER v2_reference_runtime_probe;
DECLARE @runtime_select_rejected BIT = 0;
EXECUTE AS USER = N'v2_reference_runtime_probe';
BEGIN TRY
    SELECT TOP (1) reference_release_id FROM ref.reference_release;
END TRY
BEGIN CATCH
    IF ERROR_NUMBER() = 229 SET @runtime_select_rejected = 1; ELSE THROW;
END CATCH;
REVERT;
IF @runtime_select_rejected = 0
    THROW 51978, N'Runtime obteve SELECT direto em ref.', 1;
ALTER ROLE v2_runtime DROP MEMBER v2_reference_runtime_probe;

:r "030_validate_governed_references.sql"
GO

IF XACT_STATE() <> 1 OR @@TRANCOUNT <> 1
    THROW 51979, N'O escopo rollback-only das referências foi consumido ou invalidado.', 1;
ROLLBACK TRANSACTION;
IF XACT_STATE() <> 0 OR @@TRANCOUNT <> 0
    THROW 51980, N'O rollback das referências não encerrou o escopo sintético.', 1;

PRINT N'Referências governadas exercitadas com fixtures sintéticas e revertidas integralmente.';

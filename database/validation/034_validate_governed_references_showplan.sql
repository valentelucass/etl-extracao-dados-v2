-- Compila os access paths críticos de V2-035a sem executar consultas nem persistir V008.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 52000, N'O SHOWPLAN de referências aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
:r "..\baseline\001_schema_foundation_baseline.sql"
:r "030_validate_governed_references.sql"
GO

SET SHOWPLAN_XML ON;
GO

DECLARE @release_id BIGINT = 1;
DECLARE @as_of DATE = '20360601';
DECLARE @document_token CHAR(64) = REPLICATE('a', 64);

SELECT reference_release_id, release_version, source_fingerprint, source_row_count
FROM ref.reference_release WITH (
    INDEX(IX_ref_reference_release_scope_validity), FORCESEEK
)
WHERE family_code = N'PICK_STATUS'
  AND scope_code = N'LOCAL_SYNTHETIC_SHOWPLAN'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT TOP (1) calendar_date, billing_reference_date, holiday_kind, holiday_code
FROM ref.calendario WITH (INDEX(IX_ref_calendario_business_date), FORCESEEK)
WHERE reference_release_id = @release_id
  AND is_business_day = 1
  AND calendar_date <= @as_of
ORDER BY calendar_date DESC;

SELECT canonical_status, display_label, is_terminal
FROM ref.status_coleta WITH (INDEX(IX_ref_status_coleta_lookup), FORCESEEK)
WHERE reference_release_id = @release_id
  AND raw_status = N'pending'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT branch_code, normalization_version
FROM ref.regiao_destino_alias WITH (
    INDEX(IX_ref_regiao_destino_alias_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND alias_scope = N'DESTINATION'
  AND normalization_version = N'owner-canonical-v1'
  AND normalized_alias = N'SYNTHETIC_ALIAS'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT branch_code, token_scheme_version
FROM ref.filial_operacional_documento WITH (
    INDEX(IX_ref_filial_operacional_documento_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND token_scheme_version = N'hmac-sha256-synthetic-v1'
  AND document_token = @document_token
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT token_scheme_version
FROM ref.frota_propria_documento WITH (
    INDEX(IX_ref_frota_propria_documento_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND classification_scope = N'DRIVER_OWNERSHIP'
  AND token_scheme_version = N'hmac-sha256-synthetic-v1'
  AND document_token = @document_token
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT normalization_version, contract_class
FROM ref.classificacao_frota_alias WITH (
    INDEX(IX_ref_classificacao_frota_alias_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND classification_scope = N'VEHICLE_DRIVER_CONTRACT'
  AND alias_scope = N'VEHICLE_CONTRACT'
  AND normalization_version = N'owner-canonical-v1'
  AND normalized_alias = N'SYNTHETIC_CONTRACT'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT classification_code
FROM ref.classificacao_frota_matriz WITH (
    INDEX(IX_ref_classificacao_frota_matriz_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND classification_scope = N'VEHICLE_DRIVER_CONTRACT'
  AND vehicle_contract_class = N'AGGREGATE'
  AND driver_contract_class = N'THIRD_PARTY'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT token_scheme_version, required_vehicle_contract_class, classification_code, priority
FROM ref.classificacao_frota_excecao_token WITH (
    INDEX(IX_ref_classificacao_frota_excecao_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND classification_scope = N'VEHICLE_DRIVER_CONTRACT'
  AND exception_scope = N'OWNER_NAME_TOKEN'
  AND token_scheme_version = N'hmac-sha256-synthetic-v1'
  AND subject_token = @document_token
  AND required_vehicle_contract_class = N'AGGREGATE'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT branch_code, token_scheme_version
FROM ref.atribuicao_filial WITH (
    INDEX(IX_ref_atribuicao_filial_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND token_scheme_version = N'hmac-sha256-synthetic-v1'
  AND payer_document_token = @document_token
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT reference_release_id, branch_code
FROM ref.atribuicao_filial WITH (
    INDEX(IX_ref_atribuicao_filial_branch_dependency), FORCESEEK
)
WHERE branch_reference_release_id = @release_id;

SELECT token_scheme_version
FROM ref.pagador_exclusao_cubagem WITH (
    INDEX(IX_ref_pagador_exclusao_cubagem_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND token_scheme_version = N'hmac-sha256-synthetic-v1'
  AND payer_document_token = @document_token
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;

SELECT TOP (1) region_code, priority
FROM ref.regiao_logistica_cep WITH (
    INDEX(IX_ref_regiao_logistica_cep_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND cep_start <= '15000000'
  AND cep_end >= '15000000'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of
ORDER BY priority, cep_start DESC;

SELECT TOP (1) region_code, city_normalization_version, priority
FROM ref.regiao_logistica_cidade_uf WITH (
    INDEX(IX_ref_regiao_logistica_cidade_uf_lookup), FORCESEEK
)
WHERE reference_release_id = @release_id
  AND uf = 'SP'
  AND city_normalization_version = N'owner-canonical-v1'
  AND normalized_city = N'SYNTHETIC_CITY'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of
ORDER BY priority;

SELECT coverage_state, minimum_amount, currency_code, unit_code, rounding_mode
FROM ref.tarifa_rota_uf WITH (INDEX(IX_ref_tarifa_rota_uf_lookup), FORCESEEK)
WHERE reference_release_id = @release_id
  AND origin_uf = 'SP'
  AND destination_uf = 'RJ'
  AND valid_from <= @as_of
  AND valid_to_exclusive > @as_of;
GO

SET SHOWPLAN_XML OFF;
GO

IF @@TRANCOUNT <> 1 OR XACT_STATE() <> 1
    THROW 52001, N'O SHOWPLAN de referências alterou o escopo rollback-only.', 1;

ROLLBACK TRANSACTION;
IF @@TRANCOUNT <> 0
    THROW 52002, N'O SHOWPLAN de referências não encerrou a transação.', 1;

PRINT N'GOVERNED_REFERENCES_SHOWPLAN_ROLLED_BACK';

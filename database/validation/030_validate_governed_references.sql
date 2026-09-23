-- Validação estrutural, sanitizada e somente leitura da fundação offline V2-035a.
:setvar DatabaseName "ETL_SISTEMA_V2_SHADOW"
:On Error exit

IF DB_NAME() <> N'$(DatabaseName)'
    THROW 51950, N'O validator de referências aceita somente o banco local V2 de sombra.', 1;

SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @failures TABLE (
    category NVARCHAR(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_name NVARCHAR(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
    detail NVARCHAR(512) NOT NULL
);

DECLARE @expected_objects TABLE (
    object_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (object_name, object_type)
);
INSERT INTO @expected_objects VALUES
    (N'reference_release', N'U'),
    (N'reference_import_receipt', N'U'),
    (N'reference_release_ratification', N'U'),
    (N'reference_release_revocation', N'U'),
    (N'calendario', N'U'),
    (N'status_coleta', N'U'),
    (N'filial_operacional', N'U'),
    (N'regiao_destino_alias', N'U'),
    (N'filial_operacional_documento', N'U'),
    (N'frota_propria_documento', N'U'),
    (N'classificacao_frota_alias', N'U'),
    (N'classificacao_frota_matriz', N'U'),
    (N'classificacao_frota_excecao_token', N'U'),
    (N'atribuicao_filial', N'U'),
    (N'pagador_exclusao_cubagem', N'U'),
    (N'regiao_logistica_cep', N'U'),
    (N'regiao_logistica_cidade_uf', N'U'),
    (N'tarifa_rota_uf', N'U'),
    (N'v_status_coleta_seed_candidate_v1', N'V'),
    (N'usp_register_reference_release', N'P'),
    (N'ufn_normalize_pick_status_v1', N'FN'),
    (N'ufn_calendar_seed_candidate_v1', N'TF'),
    (N'trg_reference_release_immutable', N'TR'),
    (N'trg_reference_import_receipt_immutable', N'TR'),
    (N'trg_reference_import_receipt_guard', N'TR'),
    (N'trg_reference_ratification_immutable', N'TR'),
    (N'trg_reference_revocation_immutable', N'TR'),
    (N'trg_reference_revocation_guard', N'TR'),
    (N'trg_reference_ratification_guard', N'TR'),
    (N'trg_calendario_insert_guard', N'TR'),
    (N'trg_status_coleta_insert_guard', N'TR'),
    (N'trg_filial_operacional_insert_guard', N'TR'),
    (N'trg_regiao_destino_alias_insert_guard', N'TR'),
    (N'trg_filial_documento_insert_guard', N'TR'),
    (N'trg_frota_propria_insert_guard', N'TR'),
    (N'trg_classificacao_frota_alias_insert_guard', N'TR'),
    (N'trg_classificacao_frota_matriz_insert_guard', N'TR'),
    (N'trg_classificacao_frota_excecao_insert_guard', N'TR'),
    (N'trg_atribuicao_filial_insert_guard', N'TR'),
    (N'trg_exclusao_cubagem_insert_guard', N'TR'),
    (N'trg_regiao_logistica_cep_insert_guard', N'TR'),
    (N'trg_regiao_logistica_cidade_insert_guard', N'TR'),
    (N'trg_tarifa_rota_uf_insert_guard', N'TR');

-- Extensões downstream no schema ref permanecem sob validators próprios e só
-- entram aqui por nome/tipo exatos; não ampliam o contrato físico de V008.
DECLARE @authorized_downstream_objects TABLE (
    object_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    object_type CHAR(2) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (object_name, object_type)
);
INSERT INTO @authorized_downstream_objects VALUES
    (N'coleta_sequence_code_alias', N'U');

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', expected.object_name, N'Objeto obrigatório ausente ou com tipo divergente.'
FROM @expected_objects AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS actual
    WHERE actual.schema_id = SCHEMA_ID(N'ref')
      AND actual.name COLLATE Latin1_General_100_BIN2 = expected.object_name
      AND actual.type COLLATE Latin1_General_100_BIN2 = expected.object_type
      AND actual.is_ms_shipped = 0
);

INSERT INTO @failures (category, object_name, detail)
SELECT N'OBJECT', object_definition.name,
       N'Objeto ref fora do contrato de V008 e das extensões downstream allowlisted.'
FROM sys.objects AS object_definition
WHERE object_definition.schema_id = SCHEMA_ID(N'ref')
  AND object_definition.is_ms_shipped = 0
  AND object_definition.type IN (N'U', N'P', N'V', N'FN', N'IF', N'TF', N'TR')
  AND NOT EXISTS (
      SELECT 1 FROM @expected_objects AS expected
      WHERE expected.object_name = object_definition.name COLLATE Latin1_General_100_BIN2
        AND expected.object_type = object_definition.type COLLATE Latin1_General_100_BIN2
  )
  AND NOT EXISTS (
      SELECT 1 FROM @authorized_downstream_objects AS authorized
      WHERE authorized.object_name = object_definition.name COLLATE Latin1_General_100_BIN2
        AND authorized.object_type = object_definition.type COLLATE Latin1_General_100_BIN2
  );

DECLARE @expected_indexes TABLE (
    table_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    index_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    is_unique BIT NOT NULL,
    PRIMARY KEY (table_name, index_name)
);
INSERT INTO @expected_indexes VALUES
    (N'reference_release', N'UQ_ref_reference_release_identity', 1),
    (N'reference_release', N'IX_ref_reference_release_scope_validity', 0),
    (N'calendario', N'UQ_ref_calendario_date_key', 1),
    (N'calendario', N'IX_ref_calendario_business_date', 0),
    (N'status_coleta', N'IX_ref_status_coleta_lookup', 0),
    (N'regiao_destino_alias', N'IX_ref_regiao_destino_alias_lookup', 0),
    (N'filial_operacional_documento', N'IX_ref_filial_operacional_documento_lookup', 0),
    (N'frota_propria_documento', N'IX_ref_frota_propria_documento_lookup', 0),
    (N'classificacao_frota_alias', N'IX_ref_classificacao_frota_alias_lookup', 0),
    (N'classificacao_frota_matriz', N'IX_ref_classificacao_frota_matriz_lookup', 0),
    (N'classificacao_frota_excecao_token', N'IX_ref_classificacao_frota_excecao_lookup', 0),
    (N'atribuicao_filial', N'IX_ref_atribuicao_filial_lookup', 0),
    (N'atribuicao_filial', N'IX_ref_atribuicao_filial_branch_dependency', 0),
    (N'pagador_exclusao_cubagem', N'IX_ref_pagador_exclusao_cubagem_lookup', 0),
    (N'regiao_logistica_cep', N'IX_ref_regiao_logistica_cep_lookup', 0),
    (N'regiao_logistica_cidade_uf', N'IX_ref_regiao_logistica_cidade_uf_lookup', 0),
    (N'tarifa_rota_uf', N'IX_ref_tarifa_rota_uf_lookup', 0);

INSERT INTO @failures (category, object_name, detail)
SELECT N'INDEX', CONCAT(expected.table_name, N'.', expected.index_name),
       N'Índice de grão/lookup ausente ou com unicidade divergente.'
FROM @expected_indexes AS expected
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes AS actual
    WHERE actual.object_id = OBJECT_ID(CONCAT(N'ref.', expected.table_name), N'U')
      AND actual.name COLLATE Latin1_General_100_BIN2 = expected.index_name
      AND actual.is_unique = expected.is_unique
      AND actual.is_disabled = 0
);

DECLARE @required_constraints TABLE (
    constraint_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL PRIMARY KEY
);
INSERT INTO @required_constraints VALUES
    (N'UQ_ref_reference_release_identity'),
    (N'CK_ref_reference_release_family'),
    (N'CK_ref_reference_release_schema_version'),
    (N'CK_ref_reference_release_fingerprint'),
    (N'CK_ref_reference_release_validity'),
    (N'FK_ref_reference_import_receipt_release'),
    (N'CK_ref_reference_import_receipt_contract'),
    (N'FK_ref_reference_ratification_release'),
    (N'FK_ref_reference_revocation_ratification'),
    (N'FK_ref_calendario_billing_date'),
    (N'CK_ref_calendario_civil'),
    (N'CK_ref_calendario_business_reference'),
    (N'CK_ref_status_coleta_validity'),
    (N'FK_ref_regiao_destino_alias_branch'),
    (N'FK_ref_filial_operacional_documento_branch'),
    (N'FK_ref_atribuicao_filial_branch'),
    (N'CK_ref_classificacao_frota_matriz_contract'),
    (N'CK_ref_regiao_logistica_cep_range'),
    (N'CK_ref_regiao_logistica_cidade_uf_key'),
    (N'CK_ref_tarifa_rota_uf_value');

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', required.constraint_name, N'Constraint obrigatória ausente ou desabilitada.'
FROM @required_constraints AS required
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.objects AS constraint_definition
    WHERE constraint_definition.schema_id = SCHEMA_ID(N'ref')
      AND constraint_definition.name COLLATE Latin1_General_100_BIN2 = required.constraint_name
      AND constraint_definition.type IN (N'PK', N'UQ', N'F', N'C', N'D')
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'CONSTRAINT', CONCAT(OBJECT_NAME(parent_object_id), N'.', name),
       N'Constraint ref está desabilitada ou não confiável.'
FROM sys.foreign_keys
WHERE schema_id = SCHEMA_ID(N'ref')
  AND (is_disabled = 1 OR is_not_trusted = 1)
UNION ALL
SELECT N'CONSTRAINT', CONCAT(OBJECT_NAME(parent_object_id), N'.', name),
       N'CHECK ref está desabilitado ou não confiável.'
FROM sys.check_constraints
WHERE schema_id = SCHEMA_ID(N'ref')
  AND (is_disabled = 1 OR is_not_trusted = 1);

INSERT INTO @failures (category, object_name, detail)
SELECT N'COLLATION', CONCAT(table_definition.name, N'.', column_definition.name),
       N'Texto semântico de ref não usa BIN2.'
FROM sys.columns AS column_definition
INNER JOIN sys.tables AS table_definition
    ON table_definition.object_id = column_definition.object_id
WHERE table_definition.schema_id = SCHEMA_ID(N'ref')
  AND column_definition.collation_name IS NOT NULL
  AND column_definition.collation_name <> N'Latin1_General_100_BIN2';

IF COLUMNPROPERTY(OBJECT_ID(N'ref.reference_release'), N'reference_release_id', N'IsIdentity') <> 1
    INSERT INTO @failures VALUES (
        N'IDENTITY', N'reference_release.reference_release_id',
        N'A identidade técnica de release deve ser BIGINT IDENTITY.'
    );

IF (SELECT COUNT_BIG(*) FROM ref.v_status_coleta_seed_candidate_v1) <> 9
   OR (SELECT COUNT_BIG(*) FROM ref.v_status_coleta_seed_candidate_v1 WHERE is_terminal = 1) <> 4
   OR NOT EXISTS (
       SELECT 1 FROM ref.v_status_coleta_seed_candidate_v1
       WHERE raw_status = N'done' AND canonical_status = N'done'
         AND normalization_version = N'legacy-pick-status-v1'
         AND display_label = N'Coletada' AND is_terminal = 1
   )
   OR NOT EXISTS (
       SELECT 1 FROM ref.v_status_coleta_seed_candidate_v1
       WHERE raw_status = N'finished' AND canonical_status = N'finished'
         AND display_label = N'Finalizada' AND is_terminal = 1
   )
   OR NOT EXISTS (
       SELECT 1 FROM ref.v_status_coleta_seed_candidate_v1
       WHERE raw_status = N'cancelled' AND canonical_status = N'canceled'
         AND display_label = N'Cancelada' AND is_terminal = 1
   )
   OR EXISTS (
       SELECT 1 FROM ref.v_status_coleta_seed_candidate_v1
       WHERE raw_status IN (N'pending', N'treatment', N'manifested', N'in_transit', N'draft')
         AND is_terminal <> 0
   )
    INSERT INTO @failures VALUES (
        N'SEED', N'v_status_coleta_seed_candidate_v1',
        N'O candidato determinístico de status diverge do contrato bruto/canônico/label/terminal.'
    );

IF ref.ufn_normalize_pick_status_v1(N' DONE ') <> N'done'
   OR ref.ufn_normalize_pick_status_v1(N'in-transit') <> N'in_transit'
   OR ref.ufn_normalize_pick_status_v1(N'inválido') IS NOT NULL
    INSERT INTO @failures VALUES (
        N'NORMALIZATION', N'ufn_normalize_pick_status_v1',
        N'Trim/lowercase/hífen-espaço ou falha fechada divergiu do contrato legado ASCII.'
    );

IF (SELECT COUNT_BIG(*) FROM ref.ufn_calendar_seed_candidate_v1('20360220', '20360305')) <> 14
   OR NOT EXISTS (
       SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20360220', '20360305')
       WHERE calendar_date = CONVERT(DATE, '20360229', 112) AND date_key = 20360229
   )
   OR EXISTS (
       SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20360220', '20360305')
       WHERE is_business_day = 0 AND billing_reference_date >= calendar_date
   )
   OR EXISTS (
       SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20000101', '20110101')
   )
   OR (SELECT COUNT_BIG(*) FROM ref.ufn_calendar_seed_candidate_v1(
       '20300101', DATEADD(DAY, 3660, CONVERT(DATE, '20300101', 112)))
       WHERE calendar_date >= CONVERT(DATE, '20300101', 112)) <> 3660
   OR EXISTS (SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1(
       '20300101', DATEADD(DAY, 3661, CONVERT(DATE, '20300101', 112))))
   OR NOT EXISTS (
       SELECT 1 FROM ref.ufn_calendar_seed_candidate_v1('20360105', '20360112')
       WHERE calendar_date = CONVERT(DATE, '20360105', 112)
         AND is_business_day = 0
         AND billing_reference_date = CONVERT(DATE, '20360104', 112)
   )
   OR (SELECT MIN(calendar_date)
       FROM ref.ufn_calendar_seed_candidate_v1('20360101', '20360108'))
       <> CONVERT(DATE, '20351231', 112)
    INSERT INTO @failures VALUES (
        N'SEED', N'ufn_calendar_seed_candidate_v1',
        N'Calendário rolante/limitado não cobre data pós-2032, bissexto ou referência útil.'
    );

DECLARE @required_trigger_tokens TABLE (
    trigger_name SYSNAME COLLATE Latin1_General_100_BIN2 NOT NULL,
    required_token NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    PRIMARY KEY (trigger_name, required_token)
);
INSERT INTO @required_trigger_tokens VALUES
    (N'trg_reference_import_receipt_guard', N'@actual_rows'),
    (N'trg_reference_import_receipt_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_reference_ratification_guard', N'sys.sp_getapplock'),
    (N'trg_reference_ratification_guard', N'@LockOwner = ''Transaction'''),
    (N'trg_reference_ratification_guard', N'reference_import_receipt'),
    (N'trg_reference_ratification_guard', N'billing_reference_date'),
    (N'trg_reference_ratification_guard', N'branch_reference_release_id'),
    (N'trg_reference_ratification_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_reference_revocation_guard', N'IX_ref_atribuicao_filial_branch_dependency'),
    (N'trg_calendario_insert_guard', N'@locked_release_count'),
    (N'trg_filial_operacional_insert_guard', N'@locked_release_count'),
    (N'trg_status_coleta_insert_guard', N'@locked_release_count'),
    (N'trg_status_coleta_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_regiao_destino_alias_insert_guard', N'@locked_release_count'),
    (N'trg_regiao_destino_alias_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_filial_documento_insert_guard', N'@locked_release_count'),
    (N'trg_filial_documento_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_frota_propria_insert_guard', N'@locked_release_count'),
    (N'trg_frota_propria_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_classificacao_frota_alias_insert_guard', N'@locked_release_count'),
    (N'trg_classificacao_frota_alias_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_classificacao_frota_matriz_insert_guard', N'@locked_release_count'),
    (N'trg_classificacao_frota_matriz_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_classificacao_frota_excecao_insert_guard', N'@locked_release_count'),
    (N'trg_classificacao_frota_excecao_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_atribuicao_filial_insert_guard', N'@locked_release_count'),
    (N'trg_atribuicao_filial_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_exclusao_cubagem_insert_guard', N'@locked_release_count'),
    (N'trg_exclusao_cubagem_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_regiao_logistica_cep_insert_guard', N'@locked_release_count'),
    (N'trg_regiao_logistica_cep_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_regiao_logistica_cidade_insert_guard', N'@locked_release_count'),
    (N'trg_regiao_logistica_cidade_insert_guard', N'UPDLOCK, HOLDLOCK'),
    (N'trg_tarifa_rota_uf_insert_guard', N'@locked_release_count'),
    (N'trg_tarifa_rota_uf_insert_guard', N'UPDLOCK, HOLDLOCK');

INSERT INTO @failures (category, object_name, detail)
SELECT N'TRIGGER', required.trigger_name, N'Trigger não contém a guarda set-based/serializada.'
FROM @required_trigger_tokens AS required
WHERE OBJECT_DEFINITION(OBJECT_ID(CONCAT(N'ref.', required.trigger_name), N'TR'))
      NOT LIKE N'%' + required.required_token + N'%';

IF OBJECT_DEFINITION(OBJECT_ID(N'ref.usp_register_reference_release', N'P'))
      NOT LIKE N'%sys.sp_getapplock%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ref.usp_register_reference_release', N'P'))
      NOT LIKE N'%REPLAY%'
   OR OBJECT_DEFINITION(OBJECT_ID(N'ref.usp_register_reference_release', N'P'))
      NOT LIKE N'%UPDLOCK, HOLDLOCK%'
    INSERT INTO @failures VALUES (
        N'PROCEDURE', N'usp_register_reference_release',
        N'Registro de envelope não prova serialização, replay e conflito determinístico.'
    );

INSERT INTO @failures (category, object_name, detail)
SELECT N'TRIGGER', trigger_definition.name, N'Trigger ref está desabilitado.'
FROM sys.triggers AS trigger_definition
WHERE OBJECT_SCHEMA_NAME(trigger_definition.object_id) = N'ref'
  AND trigger_definition.is_disabled = 1;

INSERT INTO @failures (category, object_name, detail)
SELECT N'OVERLAP', N'status_coleta', N'Vigências sobrepostas persistidas.'
FROM ref.status_coleta AS left_rule
INNER JOIN ref.status_coleta AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.raw_status = left_rule.raw_status
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'regiao_destino_alias', N'Vigências sobrepostas persistidas.'
FROM ref.regiao_destino_alias AS left_rule
INNER JOIN ref.regiao_destino_alias AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.alias_scope = left_rule.alias_scope
   AND right_rule.normalization_version = left_rule.normalization_version
   AND right_rule.normalized_alias = left_rule.normalized_alias
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'filial_operacional_documento', N'Vigências sobrepostas persistidas.'
FROM ref.filial_operacional_documento AS left_rule
INNER JOIN ref.filial_operacional_documento AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND (right_rule.token_scheme_version) = left_rule.token_scheme_version
   AND (right_rule.document_token) = left_rule.document_token
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'frota_propria_documento', N'Vigências sobrepostas persistidas.'
FROM ref.frota_propria_documento AS left_rule
INNER JOIN ref.frota_propria_documento AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.classification_scope = left_rule.classification_scope
   AND (right_rule.token_scheme_version) = left_rule.token_scheme_version
   AND (right_rule.document_token) = left_rule.document_token
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'classificacao_frota_alias', N'Vigências sobrepostas persistidas.'
FROM ref.classificacao_frota_alias AS left_rule
INNER JOIN ref.classificacao_frota_alias AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.classification_scope = left_rule.classification_scope
   AND right_rule.alias_scope = left_rule.alias_scope
   AND right_rule.normalization_version = left_rule.normalization_version
   AND right_rule.normalized_alias = left_rule.normalized_alias
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'classificacao_frota_matriz', N'Vigências sobrepostas persistidas.'
FROM ref.classificacao_frota_matriz AS left_rule
INNER JOIN ref.classificacao_frota_matriz AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.classification_scope = left_rule.classification_scope
   AND right_rule.vehicle_contract_class = left_rule.vehicle_contract_class
   AND right_rule.driver_contract_class = left_rule.driver_contract_class
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'classificacao_frota_excecao_token', N'Vigências sobrepostas persistidas.'
FROM ref.classificacao_frota_excecao_token AS left_rule
INNER JOIN ref.classificacao_frota_excecao_token AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.classification_scope = left_rule.classification_scope
   AND right_rule.exception_scope = left_rule.exception_scope
   AND (right_rule.token_scheme_version) = left_rule.token_scheme_version
   AND (right_rule.subject_token) = left_rule.subject_token
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'atribuicao_filial', N'Vigências sobrepostas persistidas.'
FROM ref.atribuicao_filial AS left_rule
INNER JOIN ref.atribuicao_filial AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND (right_rule.token_scheme_version) = left_rule.token_scheme_version
   AND (right_rule.payer_document_token) = left_rule.payer_document_token
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'pagador_exclusao_cubagem', N'Vigências sobrepostas persistidas.'
FROM ref.pagador_exclusao_cubagem AS left_rule
INNER JOIN ref.pagador_exclusao_cubagem AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND (right_rule.token_scheme_version) = left_rule.token_scheme_version
   AND (right_rule.payer_document_token) = left_rule.payer_document_token
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'regiao_logistica_cep', N'Faixas CEP/vigências conflitantes persistidas.'
FROM ref.regiao_logistica_cep AS left_rule
INNER JOIN ref.regiao_logistica_cep AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND (
       right_rule.valid_from > left_rule.valid_from
       OR right_rule.valid_from = left_rule.valid_from
          AND right_rule.cep_start > left_rule.cep_start
       OR right_rule.valid_from = left_rule.valid_from
          AND right_rule.cep_start = left_rule.cep_start
          AND right_rule.cep_end > left_rule.cep_end
   )
   AND right_rule.cep_start <= left_rule.cep_end
   AND left_rule.cep_start <= right_rule.cep_end
   AND right_rule.valid_from < left_rule.valid_to_exclusive
   AND left_rule.valid_from < right_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'regiao_logistica_cidade_uf', N'Vigências sobrepostas persistidas.'
FROM ref.regiao_logistica_cidade_uf AS left_rule
INNER JOIN ref.regiao_logistica_cidade_uf AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.uf = left_rule.uf
   AND right_rule.city_normalization_version = left_rule.city_normalization_version
   AND right_rule.normalized_city = left_rule.normalized_city
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive
UNION ALL
SELECT N'OVERLAP', N'tarifa_rota_uf', N'Vigências tarifárias sobrepostas persistidas.'
FROM ref.tarifa_rota_uf AS left_rule
INNER JOIN ref.tarifa_rota_uf AS right_rule
    ON right_rule.reference_release_id = left_rule.reference_release_id
   AND right_rule.origin_uf = left_rule.origin_uf
   AND right_rule.destination_uf = left_rule.destination_uf
   AND right_rule.valid_from > left_rule.valid_from
   AND right_rule.valid_from < left_rule.valid_to_exclusive;

INSERT INTO @failures (category, object_name, detail)
SELECT N'RECEIPT', CONVERT(NVARCHAR(32), receipt.reference_release_id),
       N'Recibo diverge do envelope, conteúdo físico ou cronologia.'
FROM ref.reference_import_receipt AS receipt
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = receipt.reference_release_id
CROSS APPLY (
    SELECT CASE release_definition.family_code
        WHEN N'CALENDAR' THEN
            (SELECT COUNT_BIG(*) FROM ref.calendario
             WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'PICK_STATUS' THEN
            (SELECT COUNT_BIG(*) FROM ref.status_coleta
             WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'BRANCH_OPERATIONS' THEN
            (SELECT COUNT_BIG(*) FROM ref.filial_operacional
             WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_destino_alias
               WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.filial_operacional_documento
               WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'OWNED_FLEET' THEN
            (SELECT COUNT_BIG(*) FROM ref.frota_propria_documento
             WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_alias
               WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_matriz
               WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.classificacao_frota_excecao_token
               WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'BRANCH_ATTRIBUTION' THEN
            (SELECT COUNT_BIG(*) FROM ref.atribuicao_filial
             WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'CUBAGE_EXCLUSION' THEN
            (SELECT COUNT_BIG(*) FROM ref.pagador_exclusao_cubagem
             WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'LOGISTICS_REGION' THEN
            (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cep
             WHERE reference_release_id = receipt.reference_release_id)
            + (SELECT COUNT_BIG(*) FROM ref.regiao_logistica_cidade_uf
               WHERE reference_release_id = receipt.reference_release_id)
        WHEN N'QUOTE_TARIFF' THEN
            (SELECT COUNT_BIG(*) FROM ref.tarifa_rota_uf
             WHERE reference_release_id = receipt.reference_release_id)
        ELSE -1
    END AS actual_rows
) AS physical_content
WHERE receipt.content_fingerprint <> release_definition.source_fingerprint
   OR receipt.imported_row_count <> release_definition.source_row_count
   OR receipt.imported_row_count <> physical_content.actual_rows
   OR receipt.recorded_at_utc < release_definition.recorded_at_utc;

INSERT INTO @failures (category, object_name, detail)
SELECT N'GOVERNANCE', CONVERT(NVARCHAR(32), ratification.reference_release_id),
       N'Ratificação persistida pelo mesmo papel autor/importador.'
FROM ref.reference_release_ratification AS ratification
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = ratification.reference_release_id
INNER JOIN ref.reference_import_receipt AS receipt
    ON receipt.reference_release_id = ratification.reference_release_id
WHERE ratification.approver_role = release_definition.author_role
   OR ratification.approver_role = receipt.importer_role;

INSERT INTO @failures (category, object_name, detail)
SELECT N'GOVERNANCE', CONCAT(CONVERT(NVARCHAR(32), ratification.reference_release_id),
                             N':', ratification.activation_scope),
       N'Ratificação persistida sem recibo ou antes da importação.'
FROM ref.reference_release_ratification AS ratification
LEFT JOIN ref.reference_import_receipt AS receipt
    ON receipt.reference_release_id = ratification.reference_release_id
WHERE receipt.reference_release_id IS NULL
   OR ratification.ratified_at_utc < receipt.recorded_at_utc
UNION ALL
SELECT N'GOVERNANCE', CONCAT(CONVERT(NVARCHAR(32), revocation.reference_release_id),
                             N':', revocation.activation_scope),
       N'Revogação persistida antes da ratificação.'
FROM ref.reference_release_revocation AS revocation
INNER JOIN ref.reference_release_ratification AS ratification
    ON ratification.reference_release_id = revocation.reference_release_id
   AND ratification.activation_scope = revocation.activation_scope
WHERE revocation.revoked_at_utc < ratification.ratified_at_utc;

INSERT INTO @failures (category, object_name, detail)
SELECT N'VALIDITY', N'status_coleta', N'Vigência filha fora da release.'
FROM ref.status_coleta AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'regiao_destino_alias', N'Vigência filha fora da release.'
FROM ref.regiao_destino_alias AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'filial_operacional_documento', N'Vigência filha fora da release.'
FROM ref.filial_operacional_documento AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'frota_propria_documento', N'Vigência filha fora da release.'
FROM ref.frota_propria_documento AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'classificacao_frota_alias', N'Vigência filha fora da release.'
FROM ref.classificacao_frota_alias AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'classificacao_frota_matriz', N'Vigência filha fora da release.'
FROM ref.classificacao_frota_matriz AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'classificacao_frota_excecao_token', N'Vigência filha fora da release.'
FROM ref.classificacao_frota_excecao_token AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'atribuicao_filial', N'Vigência fora da release própria ou de filial.'
FROM ref.atribuicao_filial AS content
INNER JOIN ref.reference_release AS own_release
    ON own_release.reference_release_id = content.reference_release_id
INNER JOIN ref.reference_release AS branch_release
    ON branch_release.reference_release_id = content.branch_reference_release_id
WHERE content.valid_from < own_release.valid_from
   OR content.valid_to_exclusive > own_release.valid_to_exclusive
   OR content.valid_from < branch_release.valid_from
   OR content.valid_to_exclusive > branch_release.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'pagador_exclusao_cubagem', N'Vigência filha fora da release.'
FROM ref.pagador_exclusao_cubagem AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'regiao_logistica_cep', N'Vigência filha fora da release.'
FROM ref.regiao_logistica_cep AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'regiao_logistica_cidade_uf', N'Vigência filha fora da release.'
FROM ref.regiao_logistica_cidade_uf AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive
UNION ALL
SELECT N'VALIDITY', N'tarifa_rota_uf', N'Vigência filha fora da release.'
FROM ref.tarifa_rota_uf AS content
INNER JOIN ref.reference_release AS release_definition
    ON release_definition.reference_release_id = content.reference_release_id
WHERE content.valid_from < release_definition.valid_from
   OR content.valid_to_exclusive > release_definition.valid_to_exclusive;

INSERT INTO @failures (category, object_name, detail)
SELECT N'DEPENDENCY', CONVERT(NVARCHAR(32), attribution.reference_release_id),
       N'Atribuição ativa depende de release de filial ausente ou revogada no mesmo escopo.'
FROM ref.atribuicao_filial AS attribution
INNER JOIN ref.reference_release_ratification AS attribution_ratification
    ON attribution_ratification.reference_release_id = attribution.reference_release_id
LEFT JOIN ref.reference_release_revocation AS attribution_revocation
    ON attribution_revocation.reference_release_id = attribution.reference_release_id
   AND attribution_revocation.activation_scope = attribution_ratification.activation_scope
LEFT JOIN ref.reference_release_ratification AS branch_ratification
    ON branch_ratification.reference_release_id = attribution.branch_reference_release_id
   AND branch_ratification.activation_scope = attribution_ratification.activation_scope
LEFT JOIN ref.reference_release_revocation AS branch_revocation
    ON branch_revocation.reference_release_id = attribution.branch_reference_release_id
   AND branch_revocation.activation_scope = attribution_ratification.activation_scope
WHERE attribution_revocation.reference_release_id IS NULL
  AND (branch_ratification.reference_release_id IS NULL
       OR branch_revocation.reference_release_id IS NOT NULL);

INSERT INTO @failures (category, object_name, detail)
SELECT N'PERMISSION', CONCAT(principal_definition.name, N':', permission_definition.permission_name),
       N'Principal não autorizado possui acesso direto positivo em ref.'
FROM sys.database_permissions AS permission_definition
INNER JOIN sys.database_principals AS principal_definition
    ON principal_definition.principal_id = permission_definition.grantee_principal_id
WHERE principal_definition.name IN (N'public', N'v2_runtime', N'v2_migrator')
  AND permission_definition.state IN (N'G', N'W')
  AND permission_definition.permission_name IN (N'SELECT', N'INSERT', N'UPDATE', N'DELETE')
  AND (
      permission_definition.class = 3
      AND permission_definition.major_id = SCHEMA_ID(N'ref')
      OR permission_definition.class = 1
      AND OBJECT_SCHEMA_NAME(permission_definition.major_id) = N'ref'
  );

INSERT INTO @failures (category, object_name, detail)
SELECT N'PRIVILEGE', CONCAT(schema_definition.name, N'.', object_definition.name),
       N'Módulo mutável em ref não pode usar EXECUTE AS.'
FROM sys.sql_modules AS module_definition
INNER JOIN sys.objects AS object_definition
    ON object_definition.object_id = module_definition.object_id
INNER JOIN sys.schemas AS schema_definition
    ON schema_definition.schema_id = object_definition.schema_id
WHERE schema_definition.name = N'ref'
  AND module_definition.execute_as_principal_id IS NOT NULL;

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51951, N'Contrato estrutural de referências governadas divergiu.', 1;
END;

PRINT N'Referências governadas V2 validadas com sucesso.';

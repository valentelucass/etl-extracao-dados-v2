-- Validação somente leitura da vertical Cotações 6906 em sombra.
SET NOCOUNT ON;

DECLARE @failures TABLE (category NVARCHAR(64), object_name NVARCHAR(256), detail NVARCHAR(512));
DECLARE @required_objects TABLE (schema_name SYSNAME, object_name SYSNAME, object_type CHAR(2));
INSERT INTO @required_objects VALUES
    (N'stg', N'cotacao_record', N'U'),
    (N'ctl', N'cotacao_promotion_result', N'U'),
    (N'core', N'cotacao', N'U'),
    (N'recon', N'cotacao_root_presence_observation', N'U'),
    (N'stg', N'usp_stage_cotacao_record', N'P'),
    (N'core', N'usp_apply_reconcile_publish_cotacoes', N'P');

INSERT INTO @failures
SELECT N'OBJECT', CONCAT(required.schema_name, N'.', required.object_name), N'Objeto obrigatório ausente.'
FROM @required_objects AS required
WHERE OBJECT_ID(CONCAT(required.schema_name, N'.', required.object_name), required.object_type) IS NULL;

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'core.cotacao', N'U') AND name = N'cotacao_id'
      AND TYPE_NAME(user_type_id) = N'bigint' AND is_identity = 1
)
    INSERT INTO @failures VALUES (N'IDENTITY', N'core.cotacao',
        N'O identificador canônico deve permanecer BIGINT IDENTITY.');

IF EXISTS (
    SELECT expected.column_name
    FROM (VALUES
        (N'user_name_normalized'), (N'freshness_business_date'),
        (N'tariff_reference_release_id'), (N'tariff_effective_date'),
        (N'tariff_minimum_amount'), (N'tariff_currency_code'),
        (N'tariff_unit_code'), (N'tariff_rounding_mode')
    ) AS expected(column_name)
    WHERE NOT EXISTS (
        SELECT 1 FROM sys.columns
        WHERE object_id = OBJECT_ID(N'core.cotacao', N'U') AND name = expected.column_name
    )
)
    INSERT INTO @failures VALUES (N'COLUMN', N'core.cotacao',
        N'A materialização tipada de COT-01/COT-02 está incompleta.');

DECLARE @stage_definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_cotacao_record'));
DECLARE @apply_definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_cotacoes'));
DECLARE @kernel_definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_staged_execution'));
DECLARE @stage_identity_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'stg.cotacao_record')
      AND name = N'CK_stg_cotacao_identity');
DECLARE @core_identity_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'core.cotacao')
      AND name = N'CK_core_cotacao_identity');
DECLARE @stage_presence_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'stg.cotacao_record')
      AND name = N'CK_stg_cotacao_presence');
DECLARE @core_presence_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'core.cotacao')
      AND name = N'CK_core_cotacao_presence');
DECLARE @stage_currency_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'stg.cotacao_record')
      AND name = N'CK_stg_cotacao_source_currency');
DECLARE @core_currency_definition NVARCHAR(MAX) = (
    SELECT definition FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'core.cotacao')
      AND name = N'CK_core_cotacao_source_currency');
DECLARE @stage_identity_normalized NVARCHAR(MAX) = REPLACE(LOWER(@stage_identity_definition), N' ', N'');
DECLARE @core_identity_normalized NVARCHAR(MAX) = REPLACE(LOWER(@core_identity_definition), N' ', N'');
IF @stage_identity_definition IS NULL OR @core_identity_definition IS NULL
   OR CHARINDEX(N'as [bigint]', LOWER(@stage_identity_definition)) = 0
   OR CHARINDEX(N'as [bigint]', LOWER(@core_identity_definition)) = 0
   OR CHARINDEX(N'>convert([bigint],(0))', LOWER(@stage_identity_definition)) = 0
   OR CHARINDEX(N'>convert([bigint],(0))', LOWER(@core_identity_definition)) = 0
   OR CHARINDEX(N'datalength', LOWER(@stage_identity_definition)) = 0
   OR CHARINDEX(N'datalength', LOWER(@core_identity_definition)) = 0
   OR CHARINDEX(N'[source_key]=concat', @stage_identity_normalized) = 0
   OR CHARINDEX(N'[source_key]=concat', @core_identity_normalized) = 0
   OR CHARINDEX(N'as [int]', LOWER(@stage_identity_definition)) > 0
   OR CHARINDEX(N'as [int]', LOWER(@core_identity_definition)) > 0
   OR EXISTS (
       SELECT 1
       FROM (VALUES
           (OBJECT_ID(N'stg.cotacao_record'), N'source_key'),
           (OBJECT_ID(N'core.cotacao'), N'source_key')
       ) AS expected(object_id, column_name)
       LEFT JOIN sys.columns AS actual
         ON actual.object_id = expected.object_id AND actual.name = expected.column_name
       WHERE actual.column_id IS NULL
          OR actual.collation_name <> N'Latin1_General_100_BIN2'
   )
    INSERT INTO @failures VALUES (N'SOURCE_KEY', N'stg.cotacao_record/core.cotacao',
        N'sequence_code deve usar decimal canônico no intervalo BIGINT positivo, sem narrowing INT.');

IF @stage_definition IS NULL
   OR @stage_definition NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 1000%'
   OR @stage_definition NOT LIKE N'%INTEGER:%'
   OR @stage_definition NOT LIKE N'%TRY_CONVERT(BIGINT%'
   OR @stage_definition LIKE N'%TRY_CONVERT(INT%'
   OR @stage_definition NOT LIKE N'%DATALENGTH(@source_key)%'
   OR @stage_definition NOT LIKE N'%@source_key COLLATE Latin1_General_100_BIN2<>CONCAT(N''INTEGER:''%'
   OR @stage_definition NOT LIKE N'%CONCAT(N''INTEGER:'',CONVERT(NVARCHAR(20),TRY_CONVERT(BIGINT%COLLATE Latin1_General_100_BIN2%'
   OR @stage_definition NOT LIKE N'%JSON_VALUE(@field_presence_json,N''$.sequence_code'')%'
   OR @stage_definition NOT LIKE N'%JSON_VALUE(@field_presence_json,N''$.qoe_qes_diy_sae_code'')%'
   OR @stage_definition NOT LIKE N'%@currency_code IS NOT NULL%'
   OR @stage_definition NOT LIKE N'%DEFAULT%,N''GLOBAL'',N''SINGLETON''%'
   OR @stage_definition NOT LIKE N'%cotacoes-envelope-v1%'
   OR @stage_definition NOT LIKE N'%cotacoes-presence-v1%'
   OR @stage_definition NOT LIKE N'%nfse_issued_at_utc%'
   OR @stage_definition NOT LIKE N'%cte_issued_at_utc%'
   OR @stage_definition NOT LIKE N'%requested_at_utc%'
    INSERT INTO @failures VALUES (N'STAGE', N'stg.usp_stage_cotacao_record',
        N'O boundary não prova chave inteira, escopo, limite, fingerprints e frescor COT-01.');

DECLARE @required_presence_paths TABLE (
    json_path NVARCHAR(128) NOT NULL PRIMARY KEY,
    stage_variable SYSNAME NOT NULL
);
INSERT INTO @required_presence_paths VALUES
    (N'$.sequence_code', N'presence_sequence'),
    (N'$.requested_at', N'presence_requested'),
    (N'$.qoe_qes_fit_nse_issued_at', N'presence_nfse'),
    (N'$.qoe_qes_fit_fhe_cte_issued_at', N'presence_cte'),
    (N'$.qoe_qes_total', N'presence_total'),
    (N'$.qoe_crn_psn_nickname', N'presence_nickname'),
    (N'$.qoe_uer_name', N'presence_user'),
    (N'$.qoe_qes_ony_sae_code', N'presence_origin'),
    (N'$.qoe_qes_diy_sae_code', N'presence_destination');
DECLARE @stage_presence_normalized NVARCHAR(MAX) = REPLACE(LOWER(@stage_presence_definition), N' ', N'');
DECLARE @core_presence_normalized NVARCHAR(MAX) = REPLACE(LOWER(@core_presence_definition), N' ', N'');
DECLARE @stage_procedure_normalized NVARCHAR(MAX) = REPLACE(LOWER(@stage_definition), N' ', N'');
IF @stage_presence_definition IS NULL OR @core_presence_definition IS NULL
   OR @stage_presence_definition NOT LIKE N'%N''ABSENT''%'
   OR @stage_presence_definition NOT LIKE N'%N''NULL''%'
   OR @stage_presence_definition NOT LIKE N'%N''VALUE''%'
   OR @core_presence_definition NOT LIKE N'%N''ABSENT''%'
   OR @core_presence_definition NOT LIKE N'%N''NULL''%'
   OR @core_presence_definition NOT LIKE N'%N''VALUE''%'
   OR EXISTS (
       SELECT 1 FROM @required_presence_paths
       WHERE CHARINDEX(json_path, @stage_presence_definition) = 0
          OR CHARINDEX(json_path, @core_presence_definition) = 0
          OR CHARINDEX(json_path, @stage_definition) = 0
          OR CHARINDEX(CONCAT(
              N'coalesce(json_value([field_presence_json],n''', LOWER(json_path),
              N'''),n''invalid'')'
          ), @stage_presence_normalized) = 0
          OR CHARINDEX(CONCAT(
              N'coalesce(json_value([field_presence_json],n''', LOWER(json_path),
              N'''),n''invalid'')'
          ), @core_presence_normalized) = 0
          OR CHARINDEX(CONCAT(
              N'coalesce(@', LOWER(stage_variable), N',n''invalid'')'
          ), @stage_procedure_normalized) = 0
   )
    INSERT INTO @failures VALUES (N'PRESENCE', N'stg.cotacao_record/core.cotacao',
        N'Os nove tokens tri-state devem ser validados de modo fail-closed nas duas camadas.');

IF @stage_currency_definition IS NULL OR @core_currency_definition IS NULL
   OR CHARINDEX(N'[currency_code] IS NULL', @stage_currency_definition) = 0
   OR CHARINDEX(N'[currency_code] IS NULL', @core_currency_definition) = 0
    INSERT INTO @failures VALUES (N'CURRENCY', N'stg.cotacao_record/core.cotacao',
        N'A moeda da fonte deve permanecer NULL; moeda/unidade/arredondamento são reference-only.');

IF @kernel_definition IS NULL
   OR @kernel_definition NOT LIKE N'%EQUAL_FRESHNESS_CONFLICT%'
   OR @kernel_definition NOT LIKE N'%ROW_NUMBER()%'
    INSERT INTO @failures VALUES (N'DEDUPE', N'core.usp_prepare_staged_execution',
        N'O kernel não comprova quarentena estável para empate divergente.');

IF @apply_definition IS NULL
   OR @apply_definition NOT LIKE N'%BEGIN TRANSACTION%'
   OR @apply_definition NOT LIKE N'%EXEC core.usp_apply_reconcile_publish_execution%'
   OR @apply_definition NOT LIKE N'%reference_release_id explícito%'
   OR @apply_definition NOT LIKE N'%QUOTE_TARIFF%'
   OR @apply_definition NOT LIKE N'%activation_scope=N''SHADOW''%'
   OR @apply_definition NOT LIKE N'%reference_release_revocation%'
   OR @apply_definition NOT LIKE N'%@tariff_resolution%'
   OR @apply_definition NOT LIKE N'%@effective_candidates%'
   OR CHARINDEX(N'DECLARE @effective_candidates TABLE', @apply_definition) = 0
   OR CHARINDEX(N'DECLARE @effective_candidates TABLE', @apply_definition)
      >= CHARINDEX(N'DECLARE @tariff_resolution TABLE', @apply_definition)
   OR @apply_definition NOT LIKE N'%WHEN N''ABSENT'' THEN current_record.user_name_normalized%'
   OR @apply_definition NOT LIKE N'%WHEN N''ABSENT'' THEN current_record.total_amount%'
   OR @apply_definition NOT LIKE N'%WHEN N''ABSENT'' THEN current_record.origin_uf%'
   OR @apply_definition NOT LIKE N'%WHEN N''ABSENT'' THEN current_record.destination_uf%'
   OR @apply_definition NOT LIKE N'%WHEN N''NULL'' THEN NULL%'
   OR @apply_definition NOT LIKE N'%WHEN N''VALUE'' THEN typed.user_name_normalized%'
   OR @apply_definition NOT LIKE N'%WHEN N''VALUE'' THEN typed.total_amount%'
   OR @apply_definition NOT LIKE N'%WHEN N''VALUE'' THEN typed.origin_uf%'
   OR @apply_definition NOT LIKE N'%WHEN N''VALUE'' THEN typed.destination_uf%'
   OR @apply_definition NOT LIKE N'%effective.origin_uf%'
   OR @apply_definition NOT LIKE N'%effective.destination_uf%'
   OR @apply_definition NOT LIKE N'%current_record.environment_name=@environment_name%'
   OR @apply_definition NOT LIKE N'%current_record.source_instance=@source_instance%'
   OR @apply_definition NOT LIKE N'%current_record.tenant_scope=@tenant_scope%'
   OR @apply_definition NOT LIKE N'%matches.matching_rows=1%'
   OR @apply_definition NOT LIKE N'%coverage_state=N''PRICED''%'
   OR @apply_definition NOT LIKE N'%freshness_business_date>=valid_from%'
   OR @apply_definition NOT LIKE N'%freshness_business_date<valid_to_exclusive%'
   OR @apply_definition NOT LIKE N'%tariff.minimum_amount%'
   OR @apply_definition NOT LIKE N'%tariff.currency_code%'
   OR @apply_definition NOT LIKE N'%tariff.unit_code%'
   OR @apply_definition NOT LIKE N'%tariff.rounding_mode%'
   OR @apply_definition NOT LIKE N'%COT-02 falha fechada%'
   OR @apply_definition NOT LIKE N'%current_record.freshness_at_utc=typed.freshness_at_utc%'
   OR @apply_definition NOT LIKE N'%current_record.attribute_hash<>typed.attribute_hash%'
   OR @apply_definition NOT LIKE N'%BLOCKED_NO_COMPLETENESS_PROOF%'
   OR @apply_definition NOT LIKE N'%NOT_EVALUATED%'
   OR @apply_definition LIKE N'%MERGE %'
   OR @apply_definition LIKE N'%DELETE FROM core.cotacao%'
   OR @apply_definition LIKE N'%active=0%'
   OR @apply_definition LIKE N'%CREATE VIEW pub.%'
    INSERT INTO @failures VALUES (N'APPLY', N'core.usp_apply_reconcile_publish_cotacoes',
        N'A promoção não está fechada, set-based, escopada ou não preserva o tri-state antes da tarifa.');

IF EXISTS (
    SELECT 1 FROM sys.objects AS object_definition
    WHERE object_definition.schema_id = SCHEMA_ID(N'pub')
      AND object_definition.name LIKE N'%cotacao%'
)
    INSERT INTO @failures VALUES (N'PUBLICATION', N'pub',
        N'V2-027 não autoriza objeto publicado de Cotações.');

IF EXISTS (
    SELECT 1 FROM sys.database_permissions AS permission_definition
    WHERE permission_definition.grantee_principal_id = DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN (N'G', N'W') AND permission_definition.class = 1
      AND permission_definition.major_id IN (
          OBJECT_ID(N'stg.cotacao_record'), OBJECT_ID(N'core.cotacao'),
          OBJECT_ID(N'recon.cotacao_root_presence_observation')
      )
      AND permission_definition.permission_name IN (N'SELECT', N'INSERT', N'UPDATE', N'DELETE')
)
    INSERT INTO @failures VALUES (N'PERMISSION', N'v2_runtime',
        N'Runtime recebeu DML direto na vertical Cotações.');

IF EXISTS (SELECT 1 FROM @failures)
BEGIN
    SELECT category, object_name, detail FROM @failures ORDER BY category, object_name;
    THROW 51971, N'Contrato estrutural de Cotações divergiu.', 1;
END;

PRINT N'Cotações V2 em sombra validadas com sucesso.';

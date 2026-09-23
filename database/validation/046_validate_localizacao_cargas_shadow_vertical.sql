-- Validação somente leitura de Localização de Cargas 8656 em shadow base.
SET NOCOUNT ON;

DECLARE @failures TABLE(
    category NVARCHAR(64) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(512) NOT NULL
);
DECLARE @required TABLE(schema_name SYSNAME,object_name SYSNAME,object_type CHAR(2));
INSERT @required VALUES
    (N'stg',N'localizacao_carga_record',N'U'),
    (N'ctl',N'localizacao_carga_promotion_result',N'U'),
    (N'core',N'localizacao_cargas',N'U'),
    (N'recon',N'localizacao_carga_root_presence_observation',N'U'),
    (N'stg',N'usp_stage_localizacao_carga_record',N'P'),
    (N'core',N'usp_apply_reconcile_publish_localizacao_cargas',N'P'),
    (N'ctl',N'trg_localizacao_carga_prepare_candidate_set',N'TR');

INSERT @failures
SELECT N'OBJECT',CONCAT(required.schema_name,N'.',required.object_name),
       N'Objeto obrigatório ausente.'
FROM @required required
WHERE OBJECT_ID(CONCAT(required.schema_name,N'.',required.object_name),required.object_type) IS NULL;

IF NOT EXISTS(
    SELECT 1 FROM sys.columns
    WHERE object_id=OBJECT_ID(N'core.localizacao_cargas',N'U')
      AND name=N'localizacao_carga_id' AND TYPE_NAME(user_type_id)=N'bigint' AND is_identity=1
) INSERT @failures VALUES(N'IDENTITY',N'core.localizacao_cargas',
    N'A raiz exige surrogate BIGINT IDENTITY sem promover a source key.');

DECLARE @critical_columns TABLE(
    object_name NVARCHAR(128),column_name SYSNAME,type_name SYSNAME,max_length SMALLINT,
    precision_value TINYINT,scale_value TINYINT,nullable BIT,collation_name SYSNAME NULL
);
INSERT @critical_columns VALUES
    (N'stg.localizacao_carga_record',N'source_key',N'nvarchar',512,0,0,1,N'Latin1_General_100_BIN2'),
    (N'stg.localizacao_carga_record',N'invoices_volumes_typed',N'int',4,10,0,1,NULL),
    (N'stg.localizacao_carga_record',N'taxed_weight_typed',N'decimal',17,38,9,1,NULL),
    (N'stg.localizacao_carga_record',N'invoices_value_typed',N'decimal',17,38,9,1,NULL),
    (N'stg.localizacao_carga_record',N'total_typed',N'decimal',17,38,9,1,NULL),
    (N'stg.localizacao_carga_record',N'service_at_utc',N'datetime2',7,23,3,1,NULL),
    (N'stg.localizacao_carga_record',N'status_raw',N'nvarchar',-1,0,0,1,NULL),
    (N'stg.localizacao_carga_record',N'status_normalized',N'nvarchar',-1,0,0,1,N'Latin1_General_100_BIN2'),
    (N'stg.localizacao_carga_record',N'validation_disposition',N'nvarchar',32,0,0,0,N'Latin1_General_100_BIN2'),
    (N'stg.localizacao_carga_record',N'quarantine_reason_code',N'nvarchar',128,0,0,1,N'Latin1_General_100_BIN2'),
    (N'core.localizacao_cargas',N'source_key',N'nvarchar',512,0,0,0,N'Latin1_General_100_BIN2'),
    (N'core.localizacao_cargas',N'invoices_volumes_typed',N'int',4,10,0,1,NULL),
    (N'core.localizacao_cargas',N'taxed_weight_typed',N'decimal',17,38,9,1,NULL),
    (N'core.localizacao_cargas',N'invoices_value_typed',N'decimal',17,38,9,1,NULL),
    (N'core.localizacao_cargas',N'total_typed',N'decimal',17,38,9,1,NULL),
    (N'core.localizacao_cargas',N'service_at_utc',N'datetime2',7,23,3,0,NULL),
    (N'core.localizacao_cargas',N'status_raw',N'nvarchar',-1,0,0,1,NULL),
    (N'core.localizacao_cargas',N'status_normalized',N'nvarchar',-1,0,0,0,N'Latin1_General_100_BIN2');
IF EXISTS(
    SELECT 1 FROM @critical_columns expected
    LEFT JOIN sys.columns actual
      ON actual.object_id=OBJECT_ID(expected.object_name,N'U')
     AND actual.name=expected.column_name
    WHERE actual.column_id IS NULL OR TYPE_NAME(actual.user_type_id)<>expected.type_name
       OR actual.max_length<>expected.max_length OR actual.precision<>expected.precision_value
       OR actual.scale<>expected.scale_value OR actual.is_nullable<>expected.nullable
       OR (expected.collation_name IS NOT NULL AND actual.collation_name<>expected.collation_name)
) INSERT @failures VALUES(N'COLUMN',N'Localização typed columns',
    N'Os tipos físicos críticos INTEGER/DECIMAL(38,9)/DATETIME2(3)/BIN2 divergiram.');

DECLARE @stage NVARCHAR(MAX)=OBJECT_DEFINITION(
    OBJECT_ID(N'stg.usp_stage_localizacao_carga_record',N'P'));
DECLARE @apply NVARCHAR(MAX)=OBJECT_DEFINITION(
    OBJECT_ID(N'core.usp_apply_reconcile_publish_localizacao_cargas',N'P'));
DECLARE @trigger NVARCHAR(MAX)=OBJECT_DEFINITION(
    OBJECT_ID(N'ctl.trg_localizacao_carga_prepare_candidate_set',N'TR'));
DECLARE @stage_identity NVARCHAR(MAX)=(SELECT definition FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'stg.localizacao_carga_record',N'U')
      AND name=N'CK_stg_localizacao_carga_identity');
DECLARE @core_identity NVARCHAR(MAX)=(SELECT definition FROM sys.check_constraints
    WHERE parent_object_id=OBJECT_ID(N'core.localizacao_cargas',N'U')
      AND name=N'CK_core_localizacao_cargas_identity');

IF @stage_identity IS NULL OR @core_identity IS NULL
   OR @stage_identity NOT LIKE N'%INTEGER:%' OR @core_identity NOT LIKE N'%INTEGER:%'
   OR CHARINDEX(N'N''0''',@stage_identity)=0
   OR CHARINDEX(N'N''[1-9]%''',@stage_identity)=0
   OR CHARINDEX(N'N''-[1-9]%''',@stage_identity)=0
   OR CHARINDEX(N'N''%[^0-9]%''',@stage_identity)=0
   OR CHARINDEX(N'N''0''',@core_identity)=0
   OR CHARINDEX(N'N''[1-9]%''',@core_identity)=0
   OR CHARINDEX(N'N''-[1-9]%''',@core_identity)=0
   OR CHARINDEX(N'N''%[^0-9]%''',@core_identity)=0
   OR @stage_identity LIKE N'%TRY_CONVERT(BIGINT%'
   OR @core_identity LIKE N'%TRY_CONVERT(BIGINT%'
   OR @stage LIKE N'%N''/sequence_number''%'
   OR @stage LIKE N'%N''$.fields.sequence_number%'
    INSERT @failures VALUES(N'IDENTITY',N'stg/core.localizacao_cargas',
        N'/corporation_sequence_number deve ser INTEGER type-tagged sem range não provado; sequence_number é proibido.');

DECLARE @expected_parameters TABLE(parameter_id INT PRIMARY KEY,parameter_name SYSNAME);
INSERT @expected_parameters VALUES
    (1,N'@execution_id'),(2,N'@input_batch_number'),(3,N'@input_record_ordinal'),
    (4,N'@source_key'),(5,N'@payload_json'),(6,N'@field_presence_json'),
    (7,N'@service_at_raw'),(8,N'@service_at_utc'),(9,N'@service_at_presence'),
    (10,N'@service_at_parse_state'),(11,N'@invoices_volumes_raw'),
    (12,N'@invoices_volumes_typed'),(13,N'@invoices_volumes_presence'),
    (14,N'@invoices_volumes_parse_state'),(15,N'@taxed_weight_raw'),
    (16,N'@taxed_weight_typed'),(17,N'@invoices_value_raw'),
    (18,N'@invoices_value_typed'),(19,N'@total_raw'),(20,N'@total_typed'),
    (21,N'@status_raw'),(22,N'@status_normalized'),(23,N'@status_terminal'),
    (24,N'@status_branch_nickname_provenance'),(25,N'@validation_disposition'),
    (26,N'@quarantine_reason_code'),(27,N'@observed_at_utc');
IF (SELECT COUNT_BIG(*) FROM sys.parameters
    WHERE object_id=OBJECT_ID(N'stg.usp_stage_localizacao_carga_record',N'P'))<>27
   OR EXISTS(
       SELECT 1 FROM sys.parameters
       WHERE object_id=OBJECT_ID(N'stg.usp_stage_localizacao_carga_record',N'P')
         AND parameter_id IN(21,22)
         AND (TYPE_NAME(user_type_id)<>N'nvarchar' OR max_length<>-1))
   OR EXISTS(
       SELECT 1 FROM @expected_parameters expected
       LEFT JOIN sys.parameters actual
         ON actual.object_id=OBJECT_ID(N'stg.usp_stage_localizacao_carga_record',N'P')
        AND actual.parameter_id=expected.parameter_id AND actual.name=expected.parameter_name
       WHERE actual.parameter_id IS NULL
   ) INSERT @failures VALUES(N'API',N'stg.usp_stage_localizacao_carga_record',
       N'O entrypoint deve manter exatamente os 27 parâmetros na ordem do adapter JDBC.');

DECLARE @paths TABLE(json_path NVARCHAR(128) PRIMARY KEY);
INSERT @paths VALUES
    (N'corporation_sequence_number'),(N'type'),(N'service_at'),
    (N'invoices_volumes'),(N'taxed_weight'),(N'invoices_value'),(N'total'),
    (N'service_type'),(N'fit_crn_psn_nickname'),
    (N'fit_dpn_delivery_prediction_at'),(N'fit_dyn_name'),
    (N'fit_dyn_drt_nickname'),(N'fit_fsn_name'),(N'fit_fln_status'),
    (N'fit_fln_cln_nickname'),(N'fit_o_n_name'),(N'fit_o_n_drt_nickname');
IF @stage IS NULL OR @stage NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 100%'
   OR @stage NOT LIKE N'%COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>2%'
   OR @stage NOT LIKE N'%OPENJSON(@field_presence_json,N''$.fields''))<>17%'
   OR @stage NOT LIKE N'%$.fields.%'
   OR EXISTS(SELECT 1 FROM @paths WHERE CHARINDEX(json_path,@stage)=0)
   OR @stage NOT LIKE N'%COUNT_BIG(*)<>7%'
   OR @stage NOT LIKE N'%N''rawWireLexeme''%'
   OR @stage NOT LIKE N'%UNAVAILABLE_AFTER_JSON_TREE_NORMALIZATION%'
   OR @stage NOT LIKE N'%NOT_APPLICABLE_NON_NUMERIC_TOKEN%'
   OR @stage NOT LIKE N'%NOT_APPLICABLE_WITHOUT_VALUE%'
   OR @stage NOT LIKE N'%DATAEXPORT_8656%'
   OR @stage NOT LIKE N'%LOCALIZACAO_8656_17_PATHS_V1%'
   OR @stage NOT LIKE N'%FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION%'
   OR @stage NOT LIKE N'%PRESERVED_OR_STRICTLY_PARSED_BY_NAMED_FIELD%'
   OR @stage NOT LIKE N'%service_at_presence<>N''VALUE''%service_at_parse_state<>N''VALID''%'
   OR @stage NOT LIKE N'%INVOICES_VOLUMES_INVALID%ASCII%'
   OR @stage NOT LIKE N'%DECIMAL_INVALID%'
   OR @stage NOT LIKE N'%DECIMAL(38,9)%'
   OR @stage NOT LIKE N'%UNSOURCED_LEGACY%'
   OR @stage NOT LIKE N'%localizacao-cargas-status-v1%'
   OR @stage NOT LIKE N'%DEFAULT%GLOBAL%SINGLETON%'
   OR @stage NOT LIKE N'%localizacao-cargas-envelope-v2%'
   OR @stage NOT LIKE N'%localizacao-cargas-presence-v2%'
   OR @stage NOT LIKE N'%RAW_PAYLOAD_PRESENCE_REQUIRED%'
   OR @stage NOT LIKE N'%SOURCE_KEY_PAYLOAD_DIVERGENT_OR_UNAPPROVED_PATH%'
   OR @stage NOT LIKE N'%@service_at_envelope_raw_type%service_at_derived_utc%'
   OR @stage NOT LIKE N'%@invoices_volumes_envelope_raw_type%invoices_volumes_scalar_raw_type%'
   OR @stage NOT LIKE N'%@decimal_binding%envelope_raw_type%raw_wire_lexeme%'
   OR @stage NOT LIKE N'%@status_envelope_raw_type%status_envelope_raw_value%'
   OR @stage NOT LIKE N'%E. South America Standard Time%service_at_valid_offset_count%'
   OR @stage NOT LIKE N'%INSERT stg.localizacao_carga_record%validation_disposition%quarantine_reason_code%'
   OR @stage NOT LIKE N'%IF @validation_disposition=N''QUARANTINE''%COMMIT TRANSACTION%RETURN%'
    INSERT @failures VALUES(N'STAGE',N'stg.usp_stage_localizacao_carga_record',
        N'Lote, envelope fechado, quarentena auditável, tipos, frescor, status, escopo ou fingerprints divergiram.');

IF @trigger IS NULL OR @trigger NOT LIKE N'%COUNT(DISTINCT attribute_hash)>1%'
   OR @trigger NOT LIKE N'%quarantined_root_keys=0%'
   OR @trigger NOT LIKE N'%partition.entity_name=N''localizacao_cargas''%'
    INSERT @failures VALUES(N'DEDUPE',N'ctl.trg_localizacao_carga_prepare_candidate_set',
        N'Empate divergente ou cobertura tipada não bloqueia o candidate set.');

IF @apply IS NULL OR @apply NOT LIKE N'%BEGIN TRANSACTION%'
   OR @apply NOT LIKE N'%EXEC core.usp_apply_reconcile_publish_execution%'
   OR @apply NOT LIKE N'%EQUAL_FRESHNESS_CONFLICT%'
   OR @apply NOT LIKE N'%typed_record.freshness_at_utc>current_record.freshness_at_utc%'
   OR @apply NOT LIKE N'%application.application_disposition IN(N''NO_OP'',N''STALE_NO_OP'')%'
   OR @apply NOT LIKE N'%WHEN N''ABSENT''%current_record.invoices_volumes_typed%'
   OR @apply NOT LIKE N'%WHEN N''ABSENT''%current_record.status_raw%'
   OR @apply NOT LIKE N'%@effective_field_catalog%'
   OR @apply NOT LIKE N'%STRING_AGG%field_presence_json%payload_json%'
   OR @apply NOT LIKE N'%JSON_VALUE(typed_field.field_json,N''$.presence'')%Latin1_General_100_BIN2%N''ABSENT''%'
   OR @apply NOT LIKE N'%BLOCKED_NO_COMPLETENESS_PROOF%'
   OR @apply NOT LIKE N'%NOT_EVALUATED_PRUNE_DISABLED%'
   OR @apply NOT LIKE N'%UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source)%'
   OR @apply LIKE N'%JOIN core.frete%'
   OR @apply LIKE N'%REFERENCES core.frete%'
   OR @apply LIKE N'%TOP%1%'
   OR @apply LIKE N'%MERGE %'
   OR @apply LIKE N'%DELETE FROM core.localizacao_cargas%'
   OR @apply LIKE N'%active=0%'
    INSERT @failures VALUES(N'APPLY',N'core.usp_apply_reconcile_publish_localizacao_cargas',
        N'Promoção viola tri-state, frescor, ausência, escopo ou fronteira relacional.');

IF NOT EXISTS(SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'stg.localizacao_carga_record',N'U')
      AND name=N'IX_stg_localizacao_carga_execution_source_freshness')
   OR NOT EXISTS(SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'core.localizacao_cargas',N'U')
      AND name=N'UQ_core_localizacao_cargas_source' AND is_unique=1)
   OR NOT EXISTS(SELECT 1 FROM sys.indexes
    WHERE object_id=OBJECT_ID(N'core.localizacao_cargas',N'U')
      AND name=N'IX_core_localizacao_cargas_active_service' AND has_filter=1)
    INSERT @failures VALUES(N'INDEX',N'Localização',
        N'Os access paths mínimos de stage, chave escopada e partição estão ausentes.');

IF EXISTS(
    SELECT 1 FROM sys.foreign_keys
    WHERE referenced_object_id=OBJECT_ID(N'core.frete',N'U')
      AND parent_object_id IN(
          OBJECT_ID(N'stg.localizacao_carga_record',N'U'),
          OBJECT_ID(N'core.localizacao_cargas',N'U'),
          OBJECT_ID(N'recon.localizacao_carga_root_presence_observation',N'U'))
) INSERT @failures VALUES(N'RELATION',N'core.frete',
    N'Localização base não admite FK ou relação materializada com Fretes.');
IF EXISTS(
    SELECT 1 FROM sys.objects object_definition
    JOIN sys.schemas schema_definition ON schema_definition.schema_id=object_definition.schema_id
    WHERE schema_definition.name=N'pub' AND object_definition.name LIKE N'%localizacao%'
) INSERT @failures VALUES(N'PUBLICATION',N'pub',
    N'V2-028 não autoriza objeto publicável de Localização.');

IF EXISTS(
    SELECT 1 FROM sys.database_permissions permission_definition
    WHERE permission_definition.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN(N'G',N'W') AND permission_definition.class=1
      AND permission_definition.major_id IN(
          OBJECT_ID(N'stg.localizacao_carga_record'),OBJECT_ID(N'core.localizacao_cargas'),
          OBJECT_ID(N'ctl.localizacao_carga_promotion_result'),
          OBJECT_ID(N'recon.localizacao_carga_root_presence_observation'))
      AND permission_definition.permission_name IN(N'SELECT',N'INSERT',N'UPDATE',N'DELETE')
) INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',
    N'Runtime recebeu DML direto na vertical.');
IF (
    SELECT COUNT_BIG(*) FROM sys.database_permissions permission_definition
    WHERE permission_definition.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN(N'G',N'W') AND permission_definition.class=1
      AND permission_definition.permission_name=N'EXECUTE'
      AND permission_definition.major_id IN(
          OBJECT_ID(N'stg.usp_stage_localizacao_carga_record'),
          OBJECT_ID(N'core.usp_apply_reconcile_publish_localizacao_cargas'))
)<>2 INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',
    N'Os dois entrypoints fechados de Localização não estão concedidos.');
IF (SELECT COUNT_BIG(*) FROM dbo.v2_procedure_grant_allowlist)<>41
   OR NOT EXISTS(SELECT 1 FROM dbo.v2_procedure_grant_allowlist
       WHERE schema_name=N'stg' AND procedure_name=N'usp_stage_localizacao_carga_record'
         AND role_name=N'v2_runtime')
   OR NOT EXISTS(SELECT 1 FROM dbo.v2_procedure_grant_allowlist
       WHERE schema_name=N'core'
         AND procedure_name=N'usp_apply_reconcile_publish_localizacao_cargas'
         AND role_name=N'v2_runtime')
    INSERT @failures VALUES(N'ALLOWLIST',N'dbo.v2_procedure_grant_allowlist',
        N'A allowlist após V014 deve conter exatamente 41 triplets e dois entrypoints locais.');

IF EXISTS(SELECT 1 FROM @failures)
BEGIN
    SELECT category,object_name,detail FROM @failures ORDER BY category,object_name;
    THROW 52250,N'LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING: contrato estrutural divergiu.',1;
END;
PRINT N'Localização de Cargas V2-028 em sombra validada com sucesso.';

-- Validação somente leitura de Fretes 6389 em shadow base.
SET NOCOUNT ON;

DECLARE @failures TABLE(
    category NVARCHAR(64) NOT NULL,
    object_name NVARCHAR(256) NOT NULL,
    detail NVARCHAR(512) NOT NULL
);
DECLARE @required TABLE(schema_name SYSNAME,object_name SYSNAME,object_type CHAR(2));
INSERT @required VALUES
    (N'stg',N'frete_record',N'U'),
    (N'stg',N'frete_performance_observation',N'U'),
    (N'stg',N'frete_sidecar_observation',N'U'),
    (N'ctl',N'frete_promotion_result',N'U'),
    (N'core',N'frete',N'U'),
    (N'core',N'frete_performance',N'U'),
    (N'recon',N'frete_coleta_relation_candidate',N'U'),
    (N'recon',N'frete_terminal_transition_observation',N'U'),
    (N'recon',N'frete_root_presence_observation',N'U'),
    (N'stg',N'usp_stage_frete_record',N'P'),
    (N'stg',N'usp_stage_frete_performance',N'P'),
    (N'stg',N'usp_stage_frete_sidecar',N'P'),
    (N'core',N'usp_prepare_frete_candidate_set',N'P'),
    (N'core',N'usp_apply_reconcile_publish_fretes',N'P');

INSERT @failures
SELECT N'OBJECT',CONCAT(required.schema_name,N'.',required.object_name),
       N'Objeto obrigatório ausente.'
FROM @required required
WHERE OBJECT_ID(CONCAT(required.schema_name,N'.',required.object_name),required.object_type) IS NULL;

IF NOT EXISTS(
    SELECT 1 FROM sys.columns
    WHERE object_id=OBJECT_ID(N'core.frete',N'U') AND name=N'frete_id'
      AND system_type_id=127 AND is_identity=1
) INSERT @failures VALUES(N'IDENTITY',N'core.frete',
    N'A raiz exige surrogate BIGINT IDENTITY.');
IF EXISTS(
    SELECT 1 FROM sys.foreign_keys
    WHERE referenced_object_id=OBJECT_ID(N'core.coleta',N'U')
      AND parent_object_id IN(
          OBJECT_ID(N'core.frete',N'U'),OBJECT_ID(N'core.frete_performance',N'U'),
          OBJECT_ID(N'recon.frete_coleta_relation_candidate',N'U'))
) INSERT @failures VALUES(N'RELATION',N'core.coleta',
    N'V2-046b é a única dona do crosswalk; Fretes base não admite FK.');
IF EXISTS(
    SELECT 1 FROM sys.objects object_definition
    JOIN sys.schemas schema_definition ON schema_definition.schema_id=object_definition.schema_id
    WHERE schema_definition.name=N'pub' AND object_definition.name LIKE N'%frete%'
) INSERT @failures VALUES(N'PUBLICATION',N'pub',
    N'V2-011 não autoriza objeto publicável de Fretes.');

DECLARE @stage NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_frete_record'));
DECLARE @performance NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_frete_performance'));
DECLARE @sidecar NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_frete_sidecar'));
DECLARE @prepare NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_frete_candidate_set'));
DECLARE @apply NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_fretes'));
IF @stage IS NULL OR @stage NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 100%'
   OR @stage NOT LIKE N'%TRY_CONVERT(BIGINT,SUBSTRING(@source_key,9,256))%'
   OR @stage NOT LIKE N'%COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>18%'
   OR @stage NOT LIKE N'%$.updated_at%$.reference_number%$.fit_p_m_pck_sequence_code%'
   OR @stage NOT LIKE N'%CTE_CREATED_AT%CTE_ISSUED_AT%CRIADO_EM%SERVICO_EM%'
   OR @stage NOT LIKE N'%SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE%'
   OR @stage NOT LIKE N'%CONTRACTED_DATAEXPORT_6389_PATHS_ONLY%'
   OR @stage NOT LIKE N'%UNRESOLVED_RELATION_CANDIDATES_V2_046B%'
   OR @stage LIKE N'%@field_presence_json,N''$.accountingCreditId%'
   OR @stage LIKE N'%@field_presence_json,N''$.referenceNumber%'
   OR @stage LIKE N'%@field_presence_json,N''$.pickItemId%'
   OR @stage LIKE N'%core.coleta%'
    INSERT @failures VALUES(N'STAGE',N'stg.usp_stage_frete_record',
        N'Identidade, lote, tri-state, frescor ou isolamento relacional divergiram.');
IF @performance IS NULL
   OR @performance NOT LIKE N'%OFFICIAL_6389_THEN_FINISHED_AT%'
   OR @performance NOT LIKE N'%OFFICIAL_6389%FINISHED_AT_FALLBACK%'
   OR @performance NOT LIKE N'%INVALID%'
    INSERT @failures VALUES(N'PERFORMANCE',N'stg.usp_stage_frete_performance',
        N'Performance 6389 não fecha precedência/proveniência.');
IF @sidecar IS NULL OR @sidecar NOT LIKE N'%@edge_count NOT BETWEEN 0 AND 100%'
   OR @sidecar NOT LIKE N'%PRESERVE_ONLY_V2_046B_OWNS_CROSSWALK%'
   OR @sidecar LIKE N'%core.frete%'
    INSERT @failures VALUES(N'SIDECAR',N'stg.usp_stage_frete_sidecar',
        N'Sidecar não está limitado ou está preenchendo a raiz.');
IF @prepare IS NULL OR @prepare NOT LIKE N'%usp_prepare_staged_execution%'
   OR @prepare NOT LIKE N'%validation_state=N''PASSED''%'
    INSERT @failures VALUES(N'PREPARE',N'core.usp_prepare_frete_candidate_set',
        N'Candidate set tipado não está fail-closed.');
IF @apply IS NULL OR @apply NOT LIKE N'%EQUAL_FRESHNESS_CONFLICT%'
   OR @apply NOT LIKE N'%typed_record.freshness_at_utc>current_record.freshness_at_utc%'
   OR @apply NOT LIKE N'%application.application_disposition IN(N''NO_OP'',N''STALE_NO_OP'')%'
   OR @apply NOT LIKE N'%TERMINAL_REGRESSION_BLOCKED%'
   OR @apply NOT LIKE N'%BLOCKED_NO_COMPLETENESS_PROOF%'
   OR @apply NOT LIKE N'%NOT_EVALUATED_PRUNE_DISABLED%'
   OR @apply LIKE N'%DELETE FROM core.frete%'
   OR @apply LIKE N'%active=0%'
   OR @apply LIKE N'%MERGE %'
   OR @apply LIKE N'%JOIN core.coleta%'
    INSERT @failures VALUES(N'APPLY',N'core.usp_apply_reconcile_publish_fretes',
        N'Promoção viola frescor, terminalidade, ausência ou relação pendente.');
IF OBJECT_DEFINITION(OBJECT_ID(N'ctl.trg_frete_prepare_candidate_set')) NOT LIKE
        N'%COUNT(DISTINCT attribute_hash)>1%'
    INSERT @failures VALUES(N'DEDUPE',N'ctl.trg_frete_prepare_candidate_set',
        N'Empate divergente entre páginas não bloqueia o candidate set.');

IF EXISTS(
    SELECT 1 FROM sys.database_permissions permission_definition
    WHERE permission_definition.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN(N'G',N'W') AND permission_definition.class=1
      AND permission_definition.major_id IN(
          OBJECT_ID(N'stg.frete_record'),OBJECT_ID(N'stg.frete_performance_observation'),
          OBJECT_ID(N'stg.frete_sidecar_observation'),OBJECT_ID(N'core.frete'),
          OBJECT_ID(N'core.frete_performance'),
          OBJECT_ID(N'recon.frete_coleta_relation_candidate'))
      AND permission_definition.permission_name IN(N'SELECT',N'INSERT',N'UPDATE',N'DELETE')
) INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',
    N'Runtime recebeu DML direto na vertical.');
IF (
    SELECT COUNT_BIG(*) FROM sys.database_permissions permission_definition
    WHERE permission_definition.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime')
      AND permission_definition.state IN(N'G',N'W') AND permission_definition.class=1
      AND permission_definition.permission_name=N'EXECUTE'
      AND permission_definition.major_id IN(
          OBJECT_ID(N'stg.usp_stage_frete_record'),
          OBJECT_ID(N'stg.usp_stage_frete_performance'),
          OBJECT_ID(N'stg.usp_stage_frete_sidecar'),
          OBJECT_ID(N'core.usp_prepare_frete_candidate_set'),
          OBJECT_ID(N'core.usp_apply_reconcile_publish_fretes'))
)<>5 INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',
    N'Os cinco entrypoints fechados de Fretes não estão concedidos.');
IF (SELECT COUNT_BIG(*) FROM dbo.v2_procedure_grant_allowlist)<>41
    INSERT @failures VALUES(N'ALLOWLIST',N'dbo.v2_procedure_grant_allowlist',
        N'A allowlist esperada após V013 deve conter 39 triplets.');

IF EXISTS(SELECT 1 FROM @failures)
BEGIN
    SELECT category,object_name,detail FROM @failures ORDER BY category,object_name;
    THROW 52150,N'FRETES_SHADOW_VERTICAL_MISSING: contrato estrutural divergiu.',1;
END;
PRINT N'Fretes V2-011 em sombra validados com sucesso.';

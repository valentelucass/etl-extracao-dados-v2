-- Validação somente leitura da vertical Manifestos 6399 em shadow.
SET NOCOUNT ON;

DECLARE @failures TABLE(category NVARCHAR(64),object_name NVARCHAR(256),detail NVARCHAR(512));
DECLARE @required TABLE(schema_name SYSNAME,object_name SYSNAME,object_type CHAR(2));
INSERT @required VALUES
    (N'stg',N'manifesto_observation',N'U'),(N'stg',N'manifesto_reduced_candidate',N'U'),
    (N'stg',N'manifesto_pick_candidate',N'U'),(N'stg',N'manifesto_mdfe_candidate',N'U'),
    (N'ctl',N'manifesto_promotion_result',N'U'),(N'core',N'manifesto',N'U'),
    (N'core',N'manifesto_pick',N'U'),(N'core',N'manifesto_mdfe',N'U'),
    (N'recon',N'manifesto_coleta_relation_candidate',N'U'),(N'recon',N'manifesto_root_presence_observation',N'U'),
    (N'stg',N'usp_stage_manifesto_observation',N'P'),(N'stg',N'usp_stage_manifesto_reduced_candidate',N'P'),
    (N'core',N'usp_prepare_manifesto_candidate_set',N'P'),(N'core',N'usp_apply_reconcile_publish_manifestos',N'P');
INSERT @failures
SELECT N'OBJECT',CONCAT(required.schema_name,N'.',required.object_name),N'Objeto obrigatório ausente.'
FROM @required required WHERE OBJECT_ID(CONCAT(required.schema_name,N'.',required.object_name),required.object_type) IS NULL;

IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'core.manifesto',N'U') AND name=N'manifesto_id' AND is_identity=1)
    INSERT @failures VALUES(N'IDENTITY',N'core.manifesto',N'A raiz precisa de BIGINT IDENTITY canônico.');
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE referenced_object_id=OBJECT_ID(N'core.coleta',N'U') AND parent_object_id IN(OBJECT_ID(N'core.manifesto',N'U'),OBJECT_ID(N'core.manifesto_pick',N'U'),OBJECT_ID(N'core.manifesto_mdfe',N'U')))
    INSERT @failures VALUES(N'RELATION',N'core.coleta',N'V04 não pode materializar FK com Coletas.');
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_core_manifesto_pick_owner' AND parent_object_id=OBJECT_ID(N'core.manifesto_pick',N'U') AND referenced_object_id=OBJECT_ID(N'core.manifesto',N'U'))
    INSERT @failures VALUES(N'OWNERSHIP',N'core.manifesto_pick',N'Pick não está sob ownership da raiz.');
IF NOT EXISTS(SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_core_manifesto_mdfe_owner' AND parent_object_id=OBJECT_ID(N'core.manifesto_mdfe',N'U') AND referenced_object_id=OBJECT_ID(N'core.manifesto',N'U'))
    INSERT @failures VALUES(N'OWNERSHIP',N'core.manifesto_mdfe',N'MDF-e não está sob ownership da raiz.');
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'core.manifesto_mdfe',N'U') AND name=N'mdfe_status')
    INSERT @failures VALUES(N'MDFE_STATUS',N'core.manifesto_mdfe',N'mdfe_status é escalar da raiz, nunca filho.');

DECLARE @stage NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_manifesto_observation'));
DECLARE @candidate NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_stage_manifesto_reduced_candidate'));
DECLARE @prepare NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'core.usp_prepare_manifesto_candidate_set'));
DECLARE @apply NVARCHAR(MAX)=OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_manifestos'));
IF @stage IS NULL OR @stage NOT LIKE N'%@input_record_ordinal NOT BETWEEN 1 AND 100%'
    INSERT @failures VALUES(N'STAGE_LIMIT',N'stg.usp_stage_manifesto_observation',N'Staging não limita o lote físico local.');
IF @stage IS NULL OR @stage NOT LIKE N'%mft_cat_cot_number%'
   OR @stage NOT LIKE N'%mft_aoe_comments%' OR @stage NOT LIKE N'%DATALENGTH%maximum_utf16_units*2%'
    INSERT @failures VALUES(N'STAGE_TEXT',N'stg.usp_stage_manifesto_observation',N'Staging não valida os campos textuais críticos sem truncar.');
IF @stage IS NULL OR @stage NOT LIKE N'%UPPER(@source_instance) IN(N''DEFAULT'',N''GLOBAL'',N''SINGLETON'')%'
   OR @stage NOT LIKE N'%UPPER(@tenant_scope) IN(N''DEFAULT'',N''GLOBAL'',N''SINGLETON'')%'
    INSERT @failures VALUES(N'STAGE_SCOPE',N'stg.usp_stage_manifesto_observation',N'Staging não fecha source_instance e tenant_scope sentinela.');
IF @stage IS NULL OR @stage LIKE N'%core.coleta%'
    INSERT @failures VALUES(N'STAGE_RELATION',N'stg.usp_stage_manifesto_observation',N'Staging não pode materializar relação com Coletas.');
IF @candidate IS NULL OR @candidate NOT LIKE N'%manifestos-root-v1%' OR @candidate NOT LIKE N'%MAN-01%'
   OR @candidate NOT LIKE N'%MAN-02%' OR @candidate LIKE N'%MERGE %'
    INSERT @failures VALUES(N'REDUCER',N'stg.usp_stage_manifesto_reduced_candidate',N'Candidato reduzido não está versionado e fail-closed.');
-- V022 substitui a procedure V012: exige o reducer SQL e suas travas, não o literal antigo.
IF @prepare IS NULL OR @prepare NOT LIKE N'%usp_prepare_staged_execution%'
   OR NOT (@prepare LIKE N'%MAN-01/MAN-04%'
       OR (@prepare LIKE N'%CALLER_MANIFESTO_CANDIDATE_REJECTED%'
           AND @prepare LIKE N'%EQUAL_FRESHNESS_CONFLICT%'
           AND @prepare LIKE N'%METRIC_VALUE_CONFLICT%'
           AND @prepare LIKE N'%INSERT stg.manifesto_reduced_candidate%'))
    INSERT @failures VALUES(N'PREPARE',N'core.usp_prepare_manifesto_candidate_set',N'A preparação não vincula o reducer ao kernel.');
IF @apply IS NULL OR @apply NOT LIKE N'%EQUAL_FRESHNESS_CONFLICT%' OR @apply NOT LIKE N'%usp_apply_reconcile_publish_execution%'
   OR @apply NOT LIKE N'%BLOCKED_NO_COMPLETENESS_PROOF%' OR @apply LIKE N'%DELETE FROM core.manifesto%'
   OR @apply LIKE N'%active=0%' OR @apply LIKE N'%core.coleta%' OR @apply LIKE N'%CREATE VIEW pub.%'
    INSERT @failures VALUES(N'APPLY',N'core.usp_apply_reconcile_publish_manifestos',N'Promoção viola empate, ausência ou isolamento.');
IF EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=N'pub' AND o.name LIKE N'%manifesto%')
    INSERT @failures VALUES(N'PUBLICATION',N'pub',N'V04 não autoriza publicação.');
IF EXISTS(SELECT 1 FROM sys.database_permissions p WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime') AND p.state IN(N'G',N'W') AND p.class=1 AND p.major_id IN(OBJECT_ID(N'stg.manifesto_observation'),OBJECT_ID(N'core.manifesto'),OBJECT_ID(N'core.manifesto_pick'),OBJECT_ID(N'core.manifesto_mdfe')) AND p.permission_name IN(N'SELECT',N'INSERT',N'UPDATE',N'DELETE'))
    INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',N'Runtime recebeu DML direto na vertical.');
IF (SELECT COUNT_BIG(*) FROM sys.database_permissions p WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'v2_runtime') AND p.state IN(N'G',N'W') AND p.class=1 AND p.permission_name=N'EXECUTE' AND p.major_id IN(OBJECT_ID(N'stg.usp_stage_manifesto_observation'),OBJECT_ID(N'stg.usp_stage_manifesto_reduced_candidate'),OBJECT_ID(N'core.usp_prepare_manifesto_candidate_set'),OBJECT_ID(N'core.usp_apply_reconcile_publish_manifestos')) )<>4
    INSERT @failures VALUES(N'PERMISSION',N'v2_runtime',N'Os quatro entrypoints fechados de Manifestos não estão concedidos.');
IF EXISTS(SELECT 1 FROM @failures)
BEGIN SELECT category,object_name,detail FROM @failures ORDER BY category,object_name; THROW 52071,N'Contrato estrutural de Manifestos divergiu.',1; END;
PRINT N'Manifestos V2-026 em sombra validados com sucesso.';

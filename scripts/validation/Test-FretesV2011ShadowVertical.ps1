#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$relativePaths = [ordered]@{
    decision = 'docs\catalogos\fretes-v2-011\decisao-v01.json'
    sourceContract = 'docs\catalogos\contratos-primeira-onda\manifesto.json'
    identity = 'docs\catalogos\identidade-primeira-onda\manifesto.json'
    matrix = 'docs\catalogos\portabilidade\matriz-campos.csv'
    manifest = 'database\manifest\fretes-shadow-vertical.json'
    manifestHash = 'database\manifest\fretes-shadow-vertical.sha256'
    schemaManifest = 'database\manifest\schema-foundation.json'
    migration = 'database\migrations\V013__create_fretes_shadow_vertical.sql'
    validator = 'database\validation\044_validate_fretes_shadow_vertical.sql'
    exercise = 'database\validation\045_exercise_fretes_shadow_vertical_rollback.sql'
    baseline = 'database\baseline\001_schema_foundation_baseline.sql'
    progressive = 'database\validation\005_validate_progressive_data_gate.sql'
    runner = 'scripts\validation\Invoke-FretesV2011ShadowValidation.ps1'
    concurrency = 'scripts\validation\Test-FretesShadowConcurrency.ps1'
    mapper = 'src\main\java\br\com\esl\etl\v2\modulos\fretes\aplicacao\FreteDataExportRecordMapper.java'
    sidecarMapper = 'src\main\java\br\com\esl\etl\v2\modulos\fretes\aplicacao\FreteGraphQlSidecarMapper.java'
    stageRecord = 'src\main\java\br\com\esl\etl\v2\modulos\fretes\domain\FreteStageRecord.java'
    freshness = 'src\main\java\br\com\esl\etl\v2\modulos\fretes\domain\FreteFreshnessPolicy.java'
    stagingGateway = 'src\main\java\br\com\esl\etl\v2\plataforma\persistencia\fretes\JdbcSqlServerFreteStagingGateway.java'
    promotionGateway = 'src\main\java\br\com\esl\etl\v2\plataforma\persistencia\fretes\JdbcSqlServerFretePromotionGateway.java'
    runbook = 'docs\runbooks\v2-011-fretes-shadow-base.md'
}

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "FRETES_SHADOW_VERTICAL_MISSING: $Message" }
}

$paths = @{}
foreach ($entry in $relativePaths.GetEnumerator()) {
    $path = Join-Path $root $entry.Value
    Require-True (Test-Path -LiteralPath $path -PathType Leaf) "artefato obrigatório ausente: $($entry.Value)"
    $paths[$entry.Key] = $path
}

$decision = Get-Content -LiteralPath $paths.decision -Raw | ConvertFrom-Json
$sourceCatalog = Get-Content -LiteralPath $paths.sourceContract -Raw | ConvertFrom-Json
$identityCatalog = Get-Content -LiteralPath $paths.identity -Raw | ConvertFrom-Json
$manifest = Get-Content -LiteralPath $paths.manifest -Raw | ConvertFrom-Json
$schemaManifest = Get-Content -LiteralPath $paths.schemaManifest -Raw | ConvertFrom-Json
$matrixRows = @(Import-Csv -LiteralPath $paths.matrix | Where-Object {
        $_.template_or_document -ceq '6389' -and $_.row_kind -ceq 'DATA_EXPORT_DATA_FIELD'
    })
$migration = Get-Content -LiteralPath $paths.migration -Raw
$validator = Get-Content -LiteralPath $paths.validator -Raw
$exercise = Get-Content -LiteralPath $paths.exercise -Raw
$baseline = Get-Content -LiteralPath $paths.baseline -Raw
$progressive = Get-Content -LiteralPath $paths.progressive -Raw
$runner = Get-Content -LiteralPath $paths.runner -Raw
$concurrency = Get-Content -LiteralPath $paths.concurrency -Raw
$mapper = Get-Content -LiteralPath $paths.mapper -Raw
$sidecarMapper = Get-Content -LiteralPath $paths.sidecarMapper -Raw
$stageRecord = Get-Content -LiteralPath $paths.stageRecord -Raw
$freshness = Get-Content -LiteralPath $paths.freshness -Raw
$stagingGateway = Get-Content -LiteralPath $paths.stagingGateway -Raw
$promotionGateway = Get-Content -LiteralPath $paths.promotionGateway -Raw
$runbook = Get-Content -LiteralPath $paths.runbook -Raw

$expectedPaths = @(
    '/data/id',
    '/data/updated_at',
    '/data/reference_number',
    '/data/fit_p_m_pck_sequence_code',
    '/data/corporation_sequence_number',
    '/data/finished_at',
    '/data/fit_dpn_performance_finished_at'
)
$expectedMatrixIds = 1..7 | ForEach-Object { 'DE-DATA-6389-{0:D3}' -f $_ }
$expectedSidecarPaths = @(
    '/freight/edges/node/id',
    '/freight/edges/node/accountingCreditId',
    '/freight/edges/node/accountingCreditInstallmentId',
    '/freight/edges/node/referenceNumber',
    '/freight/edges/node/cte/key',
    '/freight/edges/node/total',
    '/freight/edges/node/corporationSequenceNumber',
    '/freight/edges/node/pickItemId',
    '/freight/pageInfo/hasNextPage',
    '/freight/pageInfo/endCursor'
)

$source6389 = @($sourceCatalog.contracts | Where-Object { $_.contractId -ceq 'dataexport-6389' })
$identity6389 = @($identityCatalog.entities | Where-Object { $_.contractId -ceq 'dataexport-6389' })
Require-True ($source6389.Count -eq 1 -and $identity6389.Count -eq 1) 'contrato ou identidade 6389 não é único.'
Require-True ($source6389[0].contractVersion -ceq '2026-08-31.v2-025a.1' -and
    $source6389[0].fingerprints.release -ceq '23aef4e4e03488d291d3990bdba813800e3ebb8c8be18e766a788daf9741ec15') `
    'o contrato 6389 versionado divergiu.'
Require-True ($identity6389[0].sourceKey.path -ceq '/id' -and
    (@($identity6389[0].sourceKey.wireTypes) -join '|') -ceq 'INTEGER' -and
    $identity6389[0].businessAlias.path -ceq '/corporation_sequence_number' -and
    $identity6389[0].identityFingerprint -ceq '4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723') `
    'FRE-01 não preserva /id INTEGER como única identidade e corporation_sequence_number como alias.'

Require-True ($decision.decisionStatus -ceq 'COMPLETE_LOCAL_DECISION_ONLY' -and
    $decision.execution.parentStatus -ceq 'OPEN_IMPLEMENTATION_PENDING' -and
    $decision.execution.baseShadowGate -ceq 'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW' -and
    $decision.execution.relationalParityGates -ceq 'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES' -and
    $decision.execution.crosswalkOwner -ceq 'V2-046b') `
    'a decisão histórica V2-011a ou sua divisão canônica foi reescrita.'
Require-True ($manifest.localState -ceq 'IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING' -and
    $manifest.roadmapTask -ceq 'V2-011' -and $manifest.route -ceq 'V09' -and
    $manifest.block -eq 45 -and $manifest.templateId -eq 6389) `
    'o manifesto não registra exatamente a implementação base V09/6389 em shadow.'
Require-True ($manifest.dependencyPolicy.baseShadow -ceq 'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW' -and
    $manifest.dependencyPolicy.relationalParity -ceq 'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES' -and
    $manifest.dependencyPolicy.crosswalkOwner -ceq 'V2-046b' -and
    $manifest.dependencyPolicy.v2_046a -ceq 'OPEN' -and $manifest.dependencyPolicy.v2_046b -ceq 'OPEN') `
    'V2-046a/b foi fechado ou recebeu responsabilidade indevida.'
Require-True ((@($manifest.contract.contractedDataPaths) -join '|') -ceq ($expectedPaths -join '|') -and
    (@($manifest.portabilityEnforcement.matrixIds) -join '|') -ceq ($expectedMatrixIds -join '|')) `
    'o manifesto deve limitar a evidência Data Export aos sete paths contratados.'
Require-True ($matrixRows.Count -eq 7 -and
    (@($matrixRows.matrix_id) -join '|') -ceq ($expectedMatrixIds -join '|') -and
    (@($matrixRows.source_path) -join '|') -ceq ($expectedPaths -join '|')) `
    'a fatia física 6389 de V2-017a diverge dos sete paths comprovados.'
Require-True ($manifest.portabilityEnforcement.scope -ceq 'V2-017a_APPLICABLE_6389_SLICE_ONLY_AGGREGATE_REMAINS_OPEN' -and
    $manifest.decisionEvidence.evidenceScope -ceq 'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE') `
    'a fatia V2-017a ou os campos sintéticos foram promovidos além da evidência.'
Require-True ((@($manifest.sidecar.approvedPaths) -join '|') -ceq ($expectedSidecarPaths -join '|') -and
    $manifest.sidecar.maximumEdgesPerBatch -eq 100 -and -not $manifest.sidecar.rootOrFreshnessAuthority) `
    'o sidecar deve manter exatamente dez paths, limite 100 e nenhuma autoridade de raiz/frescor.'
Require-True ($manifest.relationshipCandidate.state -ceq 'APPEND_ONLY_UNRESOLVED_V2_046B' -and
    -not $manifest.relationshipCandidate.foreignKeyToColeta -and
    -not $manifest.relationshipCandidate.joinOrLookupToColeta -and
    -not $manifest.relationshipCandidate.crosswalkOrBackfill) `
    'o candidato Coleta--Frete foi materializado indevidamente.'
Require-True ($manifest.incremental.partition -ceq 'freights.service_at' -and
    $manifest.incremental.absence -ceq 'NEVER_ALTERS_ACTIVE_STATE' -and
    $manifest.incremental.pruneOrSweep -ceq 'DISABLED') `
    'partição, ausência incremental ou prune divergiu.'
Require-True ($schemaManifest.grantPublisher.allowedTriplets -eq 41) 'o allowlist físico deve conter exatamente 41 triplets.'

$recordedHash = ((Get-Content -LiteralPath $paths.manifestHash -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$actualHash = (Get-FileHash -LiteralPath $paths.manifest -Algorithm SHA256).Hash.ToLowerInvariant()
Require-True ($recordedHash -ceq $actualHash) 'o fingerprint do manifesto de Fretes está desatualizado.'

foreach ($token in @('"id"', 'updated_at', 'reference_number', 'fit_p_m_pck_sequence_code',
        'corporation_sequence_number', 'cte_created_at', 'cte_issued_at', 'criado_em', 'servico_em',
        'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE', 'IGNORED_UNVERIFIED', 'OFFICIAL_6389',
        'FINISHED_AT_FALLBACK', 'AMBIGUOUS_OR_INVALID_PERFORMANCE_DATE', 'UNRESOLVED_NO_INFERENCE')) {
    Require-True ($mapper.Contains($token)) "o mapper não fecha o token $token."
}
Require-True (-not ($mapper -match '"accountingCreditId"|"accountingCreditInstallmentId"|"referenceNumber"|"pickItemId"|"pick_item_id"')) `
    'o mapper Data Export confundiu fields GraphQL com a raiz 6389.'
Require-True ($freshness.Contains('STALE_NO_OP') -and $freshness.Contains('REPLAY_NO_OP') -and
    $freshness.Contains('EQUAL_FRESHNESS_CONFLICT') -and -not $freshness.Contains('updated_at')) `
    'a única função de frescor não fecha antigo, replay, empate ou exclusão de updated_at.'
Require-True ($stageRecord.Contains('MAXIMUM_PAGE_SIZE = 100') -and
    $stageRecord.Contains('MAXIMUM_SIDECAR_EDGES = 100') -and
    $stageRecord.Contains('BigInteger.valueOf(Long.MAX_VALUE)')) `
    'limite 100 ou domínio da chave 6389 não é fail-closed.'
$actualSidecarPaths = @(
    [regex]::Matches($sidecarMapper, '"(?<path>/freight/(?:edges/node|pageInfo)/[^"\r\n]+)"') |
        ForEach-Object { $_.Groups['path'].Value } |
        Sort-Object -Unique
)
Require-True ((@($actualSidecarPaths) -join '|') -ceq (@($expectedSidecarPaths | Sort-Object) -join '|')) `
    'o mapper sidecar não preserva exatamente o conjunto dos dez paths aprovados.'
foreach ($path in $expectedSidecarPaths) {
    Require-True ($migration.Contains($path) -and $exercise.Contains($path)) `
        "a barreira SQL ou o exercício não ancora o path sidecar $path."
}
Require-True (-not ($sidecarMapper -match '(?i)cte_created_at|cte_issued_at|criado_em|servico_em|updated_at|freshness')) `
    'o sidecar tentou preencher raiz ou frescor.'

$requiredMigrationTokens = @(
    'stg.frete_record', 'stg.frete_performance_observation', 'stg.frete_sidecar_observation',
    'core.frete', 'core.frete_performance', 'recon.frete_coleta_relation_candidate',
    'recon.frete_terminal_transition_observation', 'recon.frete_root_presence_observation',
    'stg.usp_stage_frete_record', 'stg.usp_stage_frete_performance', 'stg.usp_stage_frete_sidecar',
    'core.usp_prepare_frete_candidate_set', 'core.usp_apply_reconcile_publish_fretes',
    'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW',
    'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES',
    'SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE', 'APPEND_ONLY_UNRESOLVED_V2_046B',
    'CONTRACTED_DATAEXPORT_6389_PATHS_ONLY',
    'COUNT_BIG(*) FROM OPENJSON(@field_presence_json))<>18',
    'freights.service_at', 'BLOCKED_NO_COMPLETENESS_PROOF', 'EQUAL_FRESHNESS_CONFLICT',
    'UPDLOCK,HOLDLOCK,INDEX(UQ_core_frete_source)'
)
foreach ($token in $requiredMigrationTokens) {
    Require-True ($migration.Contains($token)) "a migration V013 não contém $token."
}
Require-True ([regex]::Matches($migration, '(?im)^CREATE OR ALTER PROCEDURE (?:stg|core)\.usp_(?:stage|prepare|apply)[^\r\n]+$').Count -eq 5) `
    'V013 deve materializar exatamente cinco procedures da vertical.'
Require-True (-not ($migration -match '(?i)REFERENCES\s+core\.coleta|JOIN\s+core\.coleta|CREATE\s+VIEW\s+pub\.|DELETE\s+FROM\s+core\.frete|\bMERGE\s+|\bTOP\s*\(?(?:1|@)|\bactive\s*=\s*0')) `
    'V013 contém relação, lookup TOP 1, publicação, sweep, delete, MERGE ou desativação proibida.'
Require-True ($migration.Contains('IDENTITY(1,1)') -and $migration.Contains('CONSTRAINT UQ_core_frete_source UNIQUE(') -and
    $migration.Contains("N'DEFAULT',N'GLOBAL',N'SINGLETON'")) `
    'canonical_id ou escopo explícito de Fretes não é fechado fisicamente.'

Require-True ($stagingGateway.Contains('statement.addBatch()') -and
    $stagingGateway.Contains('statement.executeBatch()') -and $stagingGateway.Contains('connection.rollback()') -and
    $promotionGateway.Contains('SHADOW_UPSERT') -and $promotionGateway.Contains('prepareCall(sql)')) `
    'os gateways não preservam batch, transação, rollback ou shadow-only.'
Require-True (-not (($stagingGateway + $promotionGateway) -match '(?i)\b(?:INSERT|UPDATE|DELETE|MERGE)\s+(?:INTO|FROM|core\.|stg\.|recon\.)')) `
    'um gateway JDBC contém DML por registro.'

Require-True ($baseline.Contains('V013__create_fretes_shadow_vertical.sql')) 'o baseline não inclui V013.'
foreach ($token in @("N'frete_promotion_result'", "N'usp_apply_reconcile_publish_fretes'", '<> 41')) {
    Require-True ($progressive.Contains($token)) "o progressive gate não incorpora $token."
}
Require-True ($validator.Contains('FRETES_SHADOW_VERTICAL_MISSING') -and
    $validator.Contains('Fretes V2-011 em sombra validados com sucesso.') -and
    $validator.Contains("N'usp_stage_frete_sidecar'")) `
    'o validator SQL estrutural não fecha objetos e reason code estável.'
foreach ($token in @('BEGIN TRANSACTION;', ':r "044_validate_fretes_shadow_vertical.sql"',
        'Duplicata entre páginas', 'EQUAL_FRESHNESS_CONFLICT', 'TERMINAL_REGRESSION_BLOCKED',
        'Prune/sweep alterou active', 'CONTRACTED_DATAEXPORT_6389_PATHS_ONLY',
        '"reference_number":"VALUE"', 'ROLLBACK TRANSACTION;')) {
    Require-True ($exercise.Contains($token)) "o exercício rollback-only não contém $token."
}
Require-True ($runner.Contains('[switch]$ExecuteLocalShadow') -and
    $runner.Contains('-d master') -and $runner.Contains('-S localhost') -and
    $runner.Contains('ETL_SISTEMA_V2_SHADOW') -and $runner.Contains('045_exercise_fretes_shadow_vertical_rollback.sql')) `
    'o runner SQL não é opt-in e preso ao shadow local após consultar master.'
Require-True ($concurrency.Contains('Start-Process') -and $concurrency.Contains('-WindowStyle Hidden') -and
    $concurrency.Contains('FRETES_CONTENTION_CONFIRMED') -and
    $concurrency.Contains('FRETES_OTHER_ENVIRONMENT_ISOLATED') -and
    $concurrency.Contains('FRETES_LOCK_REACQUIRED_AFTER_ROLLBACK')) `
    'a prova concorrente não fecha contenção, isolamento e reacquisição após rollback.'
Require-True ($runbook.Contains('FRETES_SHADOW_VERTICAL_MISSING') -and
    $runbook.Contains('29 testes') -and $runbook.Contains('684 testes') -and
    $runbook.Contains('sem reduzir limites de cobertura') -and
    $runbook.Contains('SYNTHETIC_V09_NOT_SOURCE_CONTRACT_EVIDENCE')) `
    'o runbook não registra RED, GREEN focado e o limite da evidência sintética.'

Write-Output 'PASS: Fretes V2-011 base shadow, 7 paths 6389, sidecar limitado, relações pendentes, rollback e concorrência validados estaticamente.'

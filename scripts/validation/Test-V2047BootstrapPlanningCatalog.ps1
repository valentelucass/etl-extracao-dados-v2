#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) `
    + [System.IO.Path]::DirectorySeparatorChar
$catalogRoot = Join-Path $repositoryRoot 'docs\catalogos\bootstrap-v2-047'
$manifestPath = Join-Path $catalogRoot 'manifesto.json'
$manifestHashPath = Join-Path $catalogRoot 'manifesto.sha256'
$matrixPath = Join-Path $catalogRoot 'matriz-bootstrap-v01.csv'
$fixturePath = Join-Path $catalogRoot 'fixtures\casos-v01.synthetic.json'
$readmePath = Join-Path $catalogRoot 'README.md'
$runbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-047-fundacao-planejamento-bootstrap-sol.md'
$statesPath = Join-Path $repositoryRoot 'STATES.md'
$trailPath = Join-Path $repositoryRoot 'docs\runbooks\trilha-de-chats-gpt-5-6.md'

function Assert-True {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Message
    )
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param(
        [AllowNull()]$Actual,
        [AllowNull()]$Expected,
        [Parameter(Mandatory)][string]$Label
    )
    if ($null -eq $Actual -or $null -eq $Expected) {
        Assert-True ($null -eq $Actual -and $null -eq $Expected) "O valor $Label diverge."
        return
    }
    Assert-True ([string]$Actual -ceq [string]$Expected) "O valor $Label diverge."
}

function Assert-ExactSet {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        Assert-True ($item -is [string] -and $actualSet.Add([string]$item)) `
            "O conjunto $Label contém item inválido ou duplicado."
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($item in $Expected) { [void]$expectedSet.Add($item) }
    Assert-True $actualSet.SetEquals($expectedSet) "O conjunto $Label diverge."
}

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )
    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    Assert-True (-not $item.PSIsContainer -and -not $item.LinkType) `
        "O artefato '$($item.Name)' não é arquivo regular."
    Assert-True ($item.Length -le $MaximumBytes) "O artefato '$($item.Name)' excede o limite."
    $bytes = [IO.File]::ReadAllBytes($item.FullName)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF `
        -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    Assert-True (-not $hasBom) "O artefato '$($item.Name)' contém BOM UTF-8."
    $text = $utf8.GetString($bytes)
    Assert-True (-not $text.Contains([char]0xFFFD)) `
        "O artefato '$($item.Name)' contém substituição UTF-8."
    return $text
}

function Assert-NoDuplicateJsonElement {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Path
    )
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            Assert-True $names.Add($property.Name) "Propriedade JSON duplicada em $Path."
            Assert-NoDuplicateJsonElement -Element $property.Value -Path "$Path.$($property.Name)"
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJsonElement -Element $item -Path "$Path[$index]"
            $index++
        }
    }
}

function Read-StrictJson {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )
    $text = Read-StrictUtf8 -LiteralPath $LiteralPath -MaximumBytes $MaximumBytes
    $document = [System.Text.Json.JsonDocument]::Parse($text)
    try { Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$' }
    finally { $document.Dispose() }
    return [pscustomobject]@{ Text = $text; Value = $text | ConvertFrom-Json -Depth 100 }
}

function Resolve-RepositoryFile {
    param([Parameter(Mandatory)][string]$RelativePath)
    $resolved = [IO.Path]::GetFullPath((Join-Path $repositoryRoot `
        $RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)))
    Assert-True $resolved.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase) `
        "A referência '$RelativePath' escapou do repositório."
    $item = Get-Item -LiteralPath $resolved -ErrorAction Stop
    Assert-True (-not $item.PSIsContainer -and -not $item.LinkType) `
        "A referência '$RelativePath' não é arquivo regular."
    return $item.FullName
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$LiteralPath)
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $LiteralPath).Hash.ToLowerInvariant()
}

function Get-PlanEvaluation {
    param([Parameter(Mandatory)][hashtable]$Signals)
    $checks = @(
        @('executionMode', 'BOOTSTRAP', 'MODE_NOT_BOOTSTRAP'),
        @('sourceInstanceState', 'BOUND_EXPLICIT', 'SOURCE_INSTANCE_REQUIRED'),
        @('tenantScopeState', 'BOUND_EXPLICIT', 'TENANT_SCOPE_REQUIRED'),
        @('reservedScopeState', 'CLEAR', 'RESERVED_SCOPE_FORBIDDEN'),
        @('t0State', 'BOUND_CONSISTENT', 'T0_REQUIRED'),
        @('temporalOrder', 'TCUT_AFTER_T0', 'TCUT_NOT_AFTER_T0'),
        @('partitionShape', 'CONTIGUOUS_POSITIVE', 'PARTITION_SHAPE_INVALID'),
        @('deltaSemantics', '(T0,Tcut]', 'DELTA_SEMANTICS_MISMATCH'),
        @('watermarkEffect', 'NONE', 'INCREMENTAL_WATERMARK_FORBIDDEN'),
        @('contractFingerprintState', 'MATCH', 'CONTRACT_FINGERPRINT_MISMATCH'),
        @('identityFingerprintState', 'MATCH', 'IDENTITY_FINGERPRINT_MISMATCH'),
        @('canonicalIdPolicy', 'REGISTRY_PRESERVE', 'LEGACY_SURROGATE_AS_CANONICAL_FORBIDDEN'),
        @('rekeyPolicy', 'VERSIONED_ONLY', 'SILENT_REKEY_FORBIDDEN'),
        @('parentChildPolicy', 'ATOMIC_NO_ORPHAN', 'ORPHAN_CHILD_FORBIDDEN'),
        @('relationPolicy', 'DEFER_V2_046', 'RELATION_V2_046_ANTICIPATED'),
        @('absencePolicy', 'NO_DEACTIVATION_WITHOUT_PROOF', 'ABSENCE_DEACTIVATION_FORBIDDEN'),
        @('referencePolicy', 'EXPLICIT_RELEASE_SAME_SCOPE', 'IMPLICIT_REFERENCE_RELEASE_FORBIDDEN'),
        @('strategyPolicy', 'SINGLE_CANDIDATE_NO_AUTOMATIC_FALLBACK', 'AUTOMATIC_FALLBACK_FORBIDDEN'),
        @('executionState', 'BLOCKED', 'EXECUTION_READY_WITH_OPEN_GATES'),
        @('boundsState', 'BOUNDED', 'BOUNDS_REQUIRED'),
        @('evidenceState', 'SANITIZED_AGGREGATE_ONLY', 'SENSITIVE_EVIDENCE_FORBIDDEN'),
        @('rollbackPolicy', 'TRANSACTIONAL_NO_HARD_DELETE', 'DESTRUCTIVE_ROLLBACK_FORBIDDEN'),
        @('derivedFactPolicy', 'DEFER_UNTIL_INPUTS', 'DERIVED_FACT_BEFORE_INPUTS'),
        @('userTemporalPolicy', 'NO_INVENTED_DELTA', 'USER_TEMPORAL_DELTA_FORBIDDEN'),
        @('sourceBoundaryState', 'Q01_REQUIRED', 'SOURCE_BOUNDARY_UNPROVEN'),
        @('completionPolicy', 'ALL_BASE_AND_DELTA_RECONCILED', 'COMPLETION_WITH_GAP_FORBIDDEN'),
        @('lateDataPolicy', 'SEPARATE_REPLAY_NO_WATERMARK', 'LATE_DATA_POLICY_INVALID')
    )
    foreach ($check in $checks) {
        $name = [string]$check[0]
        if (-not $Signals.ContainsKey($name) -or [string]$Signals[$name] -cne [string]$check[1]) {
            return [pscustomobject]@{ disposition = 'REJECTED'; reason = [string]$check[2] }
        }
    }
    return [pscustomobject]@{
        disposition = 'PLANNING_ACCEPTED_EXECUTION_BLOCKED'
        reason = 'OK'
    }
}

$manifestDocument = Read-StrictJson -LiteralPath $manifestPath -MaximumBytes 524288
$fixtureDocument = Read-StrictJson -LiteralPath $fixturePath -MaximumBytes 524288
$matrixText = Read-StrictUtf8 -LiteralPath $matrixPath -MaximumBytes 524288
$readmeText = Read-StrictUtf8 -LiteralPath $readmePath -MaximumBytes 262144
$runbookText = Read-StrictUtf8 -LiteralPath $runbookPath -MaximumBytes 262144
$statesText = Read-StrictUtf8 -LiteralPath $statesPath -MaximumBytes 2097152
$trailText = Read-StrictUtf8 -LiteralPath $trailPath -MaximumBytes 2097152

$manifest = $manifestDocument.Value
Assert-ExactSet @($manifest.PSObject.Properties.Name) @(
    'schemaVersion', 'task', 'outcome', 'scope', 'closedVocabularies',
    'canonicalSources', 'coverage', 'planningPolicy', 'entities', 'artifacts',
    'scenarioSummary', 'futureGates', 'prohibitedScope'
) 'das propriedades do manifesto'
Assert-Equal $manifest.schemaVersion 'V2_047_BOOTSTRAP_PLANNING_FOUNDATION_V1' 'do schema'
Assert-Equal $manifest.task.roadmapTask 'V2-047' 'da tarefa'
Assert-Equal $manifest.task.route 'Q-BST-01' 'da rota'
Assert-Equal $manifest.task.block 41 'do bloco'
Assert-Equal $manifest.task.slice 'FUNDACAO_PLANEJAMENTO_OFFLINE' 'da fatia'
Assert-Equal $manifest.task.model 'SOL_ULTRA' 'do modelo'
Assert-Equal $manifest.outcome 'FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED' `
    'do resultado máximo'

Assert-Equal $manifest.scope.executionMode 'OFFLINE_CATALOG_VALIDATION_ONLY' 'do modo local'
foreach ($flag in @(
    'network', 'database', 'legacyDatabase', 'runtime', 'realPayload', 'credential',
    'production', 'realBootstrap', 'executionReady'
)) {
    Assert-True ($manifest.scope.$flag -eq $false) "O escopo '$flag' precisa permanecer falso."
}

Assert-Equal $manifest.coverage.sourceEntityRows 11 'das fontes-entidade'
Assert-Equal $manifest.coverage.entityHistoryRows 1 'dos históricos explícitos'
Assert-Equal $manifest.coverage.derivedFactRows 5 'dos fatos derivados'
Assert-Equal $manifest.coverage.totalMatrixRows 17 'das linhas da matriz'
Assert-Equal $manifest.coverage.planningEligibleEntities 6 'das entidades elegíveis'
Assert-Equal $manifest.coverage.planningBlockedOrConditionalEntities 5 `
    'das entidades bloqueadas/condicionais'

$expectedPlanningStates = @(
    'PLANNING_ELIGIBLE', 'PLANNING_BLOCKED_IDENTITY', 'CONDITIONAL_RASTER',
    'ENTITY_HISTORY_PLANNING', 'DERIVED_REBUILD_DEFERRED_V2_036'
)
$expectedExecutionStates = @(
    'BLOCKED_Q01_AND_SOURCE', 'BLOCKED_VERTICAL_Q01_AND_SOURCE',
    'BLOCKED_IDENTITY', 'BLOCKED_RASTER_DECISION', 'BLOCKED_INPUTS_AND_V2_036'
)
Assert-ExactSet $manifest.closedVocabularies.planningStates $expectedPlanningStates `
    'dos estados de planejamento'
Assert-ExactSet $manifest.closedVocabularies.executionStates $expectedExecutionStates `
    'dos estados de execução'
Assert-ExactSet $manifest.closedVocabularies.rowKinds @(
    'SOURCE_ENTITY', 'ENTITY_HISTORY', 'DERIVED_FACT'
) 'dos tipos de linha'

Assert-Equal $manifest.planningPolicy.internalInterval '[start,endExclusive)' 'do intervalo'
Assert-Equal $manifest.planningPolicy.t0 'CONSISTENT_SOURCE_SNAPSHOT_BOUNDARY_UTC' 'de T0'
Assert-Equal $manifest.planningPolicy.tcut 'FUTURE_REHEARSAL_OR_CUTOVER_BOUNDARY_UTC' 'de Tcut'
Assert-Equal $manifest.planningPolicy.delta '(T0,Tcut]' 'do delta'
Assert-Equal $manifest.planningPolicy.checkpointNamespace 'BOOTSTRAP' 'do namespace'
Assert-Equal $manifest.planningPolicy.incrementalWatermarkEffect 'NONE' 'do watermark'
Assert-Equal $manifest.planningPolicy.scopePolicy `
    'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_NO_RESERVED_SENTINELS' 'do escopo'
Assert-Equal $manifest.planningPolicy.identityPolicy `
    'TYPE_TAGGED_SOURCE_KEY_PRESERVE_REGISTRY_CANONICAL_ID' 'da identidade'
Assert-Equal $manifest.planningPolicy.reconciliationPolicy `
    'AGGREGATE_COUNTS_AND_DIGESTS_NO_ROW_VALUE_OR_KEY_EMISSION' 'da reconciliação'
Assert-Equal $manifest.planningPolicy.rollbackPolicy `
    'TRANSACTIONAL_PARTITION_REPLAY_NO_HARD_DELETE_NO_DOWN_MIGRATION' 'do rollback'

$sourceIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($source in $manifest.canonicalSources) {
    Assert-True $sourceIds.Add([string]$source.sourceId) 'Fonte canônica duplicada.'
    $sourcePath = Resolve-RepositoryFile -RelativePath $source.path
    Assert-Equal (Get-Sha256 -LiteralPath $sourcePath) $source.sha256 `
        "do SHA-256 de $($source.sourceId)"
}
Assert-ExactSet @($manifest.canonicalSources | ForEach-Object { $_.sourceId }) @(
    'ADR-0009', 'ADR-0015', 'CONTROL-PLANE', 'STAGING-PROMOTION-KERNEL',
    'CUTOVER-TOPOLOGY', 'CHARACTERIZATION-FOUNDATION', 'PORTABILITY-LEDGER',
    'SCHEMA-FOUNDATION', 'FIRST-WAVE-CONTRACTS', 'FIRST-WAVE-IDENTITY',
    'MANIFESTOS-CONTRACT', 'MANIFESTOS-IDENTITY', 'COTACOES-CONTRACT',
    'COTACOES-IDENTITY', 'LOCALIZACAO-CONTRACT', 'LOCALIZACAO-IDENTITY'
) 'das fontes canônicas'

$expectedEntities = @(
    'usuarios', 'coletas', 'fretes', 'manifestos', 'cotacoes', 'localizacao_cargas',
    'contas_a_pagar', 'faturas_por_cliente', 'inventario', 'sinistros', 'raster'
)
Assert-ExactSet @($manifest.entities | ForEach-Object { $_.entity }) $expectedEntities `
    'das entidades'
$eligibleEntities = @($manifest.entities | Where-Object { $_.planningState -ceq 'PLANNING_ELIGIBLE' })
Assert-ExactSet @($eligibleEntities | ForEach-Object { $_.entity }) @(
    'usuarios', 'coletas', 'fretes', 'manifestos', 'cotacoes', 'localizacao_cargas'
) 'das entidades elegíveis'
foreach ($entity in $manifest.entities) {
    Assert-True ($expectedPlanningStates -ccontains [string]$entity.planningState) `
        "Estado de planejamento inválido para $($entity.entity)."
    Assert-True ($expectedExecutionStates -ccontains [string]$entity.executionState) `
        "Estado de execução inválido para $($entity.entity)."
    Assert-True ([string]$entity.executionState -cne 'EXECUTION_READY') `
        "A entidade $($entity.entity) antecipou execução."
}

$firstContracts = (Read-StrictJson `
    -LiteralPath (Resolve-RepositoryFile 'docs/catalogos/contratos-primeira-onda/manifesto.json') `
    -MaximumBytes 1048576).Value
$firstIdentity = (Read-StrictJson `
    -LiteralPath (Resolve-RepositoryFile 'docs/catalogos/identidade-primeira-onda/manifesto.json') `
    -MaximumBytes 1048576).Value
$expectedBindings = @{}
foreach ($name in @('usuarios', 'coletas', 'fretes')) {
    $identity = @($firstIdentity.entities | Where-Object { $_.entity -ceq $name })
    Assert-Equal $identity.Count 1 "da identidade de $name"
    $contract = @($firstContracts.contracts | Where-Object {
        $_.contractId -ceq $identity[0].contractId
    })
    Assert-Equal $contract.Count 1 "do contrato de $name"
    $expectedBindings[$name] = @(
        [string]$contract[0].contractId,
        [string]$contract[0].fingerprints.release,
        [string]$identity[0].identityFingerprint
    )
}
$singleBindings = @(
    @('manifestos', 'docs/catalogos/contratos-esl-6399/manifesto.json',
        'docs/catalogos/identidade-manifestos/manifesto.json'),
    @('cotacoes', 'docs/catalogos/contratos-esl-6906/manifesto.json',
        'docs/catalogos/identidade-cotacoes/manifesto.json'),
    @('localizacao_cargas', 'docs/catalogos/contratos-esl-8656/manifesto.json',
        'docs/catalogos/identidade-localizacao-cargas/manifesto.json')
)
foreach ($binding in $singleBindings) {
    $contract = (Read-StrictJson -LiteralPath (Resolve-RepositoryFile $binding[1]) `
        -MaximumBytes 1048576).Value.contract
    $identity = (Read-StrictJson -LiteralPath (Resolve-RepositoryFile $binding[2]) `
        -MaximumBytes 1048576).Value.identity
    $expectedBindings[$binding[0]] = @(
        [string]$contract.contractId,
        [string]$contract.fingerprints.release,
        [string]$identity.fingerprints.identity
    )
}
foreach ($entity in $eligibleEntities) {
    $expected = $expectedBindings[[string]$entity.entity]
    Assert-Equal $entity.contractId $expected[0] "do contract id de $($entity.entity)"
    Assert-Equal $entity.contractFingerprint $expected[1] `
        "do contrato de $($entity.entity)"
    Assert-Equal $entity.identityFingerprint $expected[2] `
        "da identidade de $($entity.entity)"
}

$matrix = @($matrixText | ConvertFrom-Csv)
$expectedHeaders = @(
    'row_id', 'row_kind', 'entity', 'target', 'source_kind', 'contract_id',
    'contract_fingerprint', 'identity_fingerprint', 'planning_state', 'execution_state',
    'vertical_state', 'primary_strategy_candidate', 'fallback_strategy_candidate',
    'strategy_selection_state', 'execution_mode', 'partition_axis', 'source_timezone',
    'internal_interval', 'source_boundary_state', 't0_state', 'tcut_state',
    'delta_semantics', 'late_data_policy', 'identity_policy', 'parent_child_policy',
    'dependency_order', 'reference_release_state', 'checkpoint_namespace',
    'incremental_watermark_effect', 'reconciliation_policy', 'rollback_policy',
    'owner_role', 'due_gate', 'blockers', 'evidence'
)
Assert-ExactSet @($matrix[0].PSObject.Properties.Name) $expectedHeaders 'do cabeçalho da matriz'
Assert-Equal (@($matrix[0].PSObject.Properties.Name) -join '|') ($expectedHeaders -join '|') `
    'da ordem do cabeçalho da matriz'
Assert-Equal $matrix.Count 17 'da quantidade de linhas da matriz'
$expectedRowIds = @(
    'SRC-USR', 'SRC-COL', 'SRC-FRE', 'SRC-MAN', 'SRC-COT', 'SRC-LOC', 'SRC-CAP',
    'SRC-FAT', 'SRC-INV', 'SRC-SIN', 'SRC-RAS', 'HIS-USR', 'FACT-MAT01',
    'FACT-MAT02', 'FACT-MAT03', 'FACT-MAT04', 'FACT-MAT05'
)
Assert-ExactSet @($matrix.row_id) $expectedRowIds 'dos IDs da matriz'
Assert-Equal ($matrix.row_id -join '|') ($expectedRowIds -join '|') `
    'da ordem determinística das linhas da matriz'
foreach ($row in $matrix) {
    Assert-True ($manifest.closedVocabularies.rowKinds -ccontains [string]$row.row_kind) `
        "Tipo de linha inválido em $($row.row_id)."
    Assert-True ($expectedPlanningStates -ccontains [string]$row.planning_state) `
        "Estado de planejamento inválido em $($row.row_id)."
    Assert-True ($expectedExecutionStates -ccontains [string]$row.execution_state) `
        "Estado de execução inválido em $($row.row_id)."
    Assert-True ($manifest.closedVocabularies.strategyCandidates -ccontains `
            [string]$row.primary_strategy_candidate) `
        "Estratégia primária inválida em $($row.row_id)."
    Assert-True ($manifest.closedVocabularies.strategyCandidates -ccontains `
            [string]$row.fallback_strategy_candidate) `
        "Estratégia fallback inválida em $($row.row_id)."
    Assert-True ([string]$row.execution_state -cnotin @(
        'EXECUTION_READY', 'EXECUTED', 'PUBLISHED', 'COMPLETENESS_PROVEN'
    )) "A linha $($row.row_id) antecipou execução/evidência."
    $expectedInterval = if ([string]$row.row_id -ceq 'SRC-USR') {
        'NOT_APPLICABLE'
    } else {
        '[start,endExclusive)'
    }
    Assert-Equal $row.internal_interval $expectedInterval "do intervalo em $($row.row_id)"
    Assert-Equal $row.checkpoint_namespace 'BOOTSTRAP' `
        "do namespace em $($row.row_id)"
    Assert-Equal $row.incremental_watermark_effect 'NONE' "do watermark em $($row.row_id)"
    Assert-True ([string]$row.blockers -cne '') "A linha $($row.row_id) não possui gate futuro."
}
$eligibleMatrixRows = @($matrix | Where-Object {
        $_.row_kind -ceq 'SOURCE_ENTITY' -and $_.planning_state -ceq 'PLANNING_ELIGIBLE'
    })
Assert-ExactSet @($eligibleMatrixRows.entity) @(
    'usuarios', 'coletas', 'fretes', 'manifestos', 'cotacoes', 'localizacao_cargas'
) 'das entidades elegíveis na matriz'
foreach ($entity in $eligibleEntities) {
    $row = @($matrix | Where-Object { $_.entity -ceq $entity.entity -and $_.row_kind -ceq 'SOURCE_ENTITY' })
    Assert-Equal $row.Count 1 "da linha matricial de $($entity.entity)"
    Assert-Equal $row[0].contract_fingerprint $entity.contractFingerprint `
        "do fingerprint matricial de contrato de $($entity.entity)"
    Assert-Equal $row[0].identity_fingerprint $entity.identityFingerprint `
        "do fingerprint matricial de identidade de $($entity.entity)"
}

Assert-Equal $manifest.artifacts.matrix.path `
    'docs/catalogos/bootstrap-v2-047/matriz-bootstrap-v01.csv' 'do path da matriz'
Assert-Equal $manifest.artifacts.matrix.sha256 (Get-Sha256 -LiteralPath $matrixPath) `
    'do SHA-256 da matriz'
Assert-Equal $manifest.artifacts.fixture.path `
    'docs/catalogos/bootstrap-v2-047/fixtures/casos-v01.synthetic.json' 'do path da fixture'
Assert-Equal $manifest.artifacts.fixture.sha256 (Get-Sha256 -LiteralPath $fixturePath) `
    'do SHA-256 da fixture'

$fixture = $fixtureDocument.Value
Assert-True ($fixture.synthetic -eq $true) 'Os casos precisam ser explicitamente sintéticos.'
Assert-Equal $fixture.schemaVersion 'V2_047_BOOTSTRAP_PLANNING_CASES_V1' 'do schema de casos'
$forbiddenFixturePattern = '(?i)(https?://|jdbc:|bearer\s|password|credential|access[_-]?token)'
Assert-True (-not [regex]::IsMatch($fixtureDocument.Text, $forbiddenFixturePattern)) `
    'Os casos contêm coordenada externa ou marcador sensível.'
$baseSignals = @{}
foreach ($property in $fixture.baseline.PSObject.Properties) {
    $baseSignals[$property.Name] = [string]$property.Value
}
$actualReasons = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$positiveCount = 0
$negativeCount = 0
foreach ($scenario in $fixture.scenarios) {
    Assert-True $scenario.scenarioId.StartsWith('SYNTH_', [StringComparison]::Ordinal) `
        'Caso sem marcador sintético.'
    $signals = @{}
    foreach ($key in $baseSignals.Keys) { $signals[$key] = $baseSignals[$key] }
    foreach ($override in $scenario.overrides.PSObject.Properties) {
        $signals[$override.Name] = [string]$override.Value
    }
    $actual = Get-PlanEvaluation -Signals $signals
    Assert-Equal $actual.disposition $scenario.expectedDisposition `
        "da disposição de $($scenario.scenarioId)"
    Assert-Equal $actual.reason $scenario.expectedReason "do motivo de $($scenario.scenarioId)"
    if ($actual.reason -ceq 'OK') { $positiveCount++ }
    else { $negativeCount++; [void]$actualReasons.Add([string]$actual.reason) }
}
Assert-Equal $positiveCount $manifest.scenarioSummary.positive 'dos casos positivos'
Assert-Equal $negativeCount $manifest.scenarioSummary.negative 'dos casos negativos'
Assert-Equal $fixture.scenarios.Count $manifest.scenarioSummary.total 'do total de casos'
Assert-ExactSet @($actualReasons) $manifest.closedVocabularies.rejectionReasons `
    'dos motivos exercitados'

$hashText = Read-StrictUtf8 -LiteralPath $manifestHashPath -MaximumBytes 512
$expectedHashLine = "$(Get-Sha256 -LiteralPath $manifestPath)  manifesto.json`n"
Assert-Equal $hashText.Replace("`r`n", "`n") $expectedHashLine 'do arquivo de fingerprint'

$requiredTokens = @(
    'Q-BST-01', 'V2-047', 'FUNDACAO_PLANEJAMENTO_OFFLINE', 'T0', 'Tcut',
    '[start,endExclusive)', 'BOOTSTRAP', 'Q-*-01', 'Q-*-02',
    'FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED'
)
foreach ($token in $requiredTokens) {
    Assert-True $readmeText.Contains($token, [StringComparison]::Ordinal) `
        "README sem '$token'."
    Assert-True $runbookText.Contains($token, [StringComparison]::Ordinal) `
        "Runbook sem '$token'."
}
Assert-True ([regex]::IsMatch($statesText, `
        '(?m)^\s*- \[x\] \*\*V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE\b')) `
    'STATES.md não conclui exatamente a fundação de V2-047.'
Assert-True ([regex]::IsMatch($statesText, '(?m)^\s*- \[ \] \*\*V2-047\b')) `
    'STATES.md concluiu indevidamente V2-047 agregada.'
Assert-True ([regex]::IsMatch($trailText, `
        '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-BST-01 \| BLOCO=41 \| TAREFA=V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE \| .* \| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED \| EVIDENCIA=STATES\.md$')) `
    'A trilha não conclui exatamente Q-BST-01/Bloco 41.'

Write-Host (
    'PASS: V2-047/Q-BST-01 íntegro; 17 linhas, 6 entidades elegíveis, ' +
    "$($fixture.scenarios.Count) casos sintéticos e zero execução externa."
)

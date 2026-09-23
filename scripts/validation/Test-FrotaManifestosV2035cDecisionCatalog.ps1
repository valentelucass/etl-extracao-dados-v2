#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$legacyRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '..\etl-extracao-dados'))
$allowedRoots = @($repositoryRoot, $legacyRoot)
$catalogDirectory = Join-Path $repositoryRoot 'docs\catalogos\frota-manifestos-v2-035c'
$catalogPath = Join-Path $catalogDirectory 'decisao-v01.json'
$matrixPath = Join-Path $catalogDirectory 'matriz-evidencias-v01.csv'
$fixturePath = Join-Path $catalogDirectory 'fixtures\casos-v01.synthetic.json'
$reassessmentPath = Join-Path $catalogDirectory 'decisao-v02.json'
$reassessmentMatrixPath = Join-Path $catalogDirectory 'matriz-reavaliacao-v02.csv'
$reassessmentFixturePath = Join-Path $catalogDirectory 'fixtures\reavaliacao-v02.synthetic.json'
$readmePath = Join-Path $catalogDirectory 'README.md'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0025-frota-manifestos-identidade-dimensional-fail-closed.md'
$statePath = Join-Path $repositoryRoot 'STATES.md'
$terraRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\frota-manifestos-v2-035b-execucao-terra.md'

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) {
        throw "O artefato '$($item.Name)' excede o limite local de leitura."
    }
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "O artefato '$($item.Name)' contém BOM UTF-8."
    }
    return $utf8.GetString($bytes)
}

function Assert-NoDuplicateJsonElement {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Path
    )

    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw "O JSON contém propriedade duplicada ordinal em $Path."
            }
            Assert-NoDuplicateJsonElement -Element $property.Value -Path ($Path + '.' + $property.Name)
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJsonElement -Element $item -Path ($Path + '[' + $index + ']')
            $index++
        }
    }
}

function Read-StrictJson {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [Parameter(Mandatory)][string]$Label
    )

    $text = Read-StrictUtf8 -LiteralPath $LiteralPath -MaximumBytes $MaximumBytes
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($text)
        try {
            Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$'
        } finally {
            $document.Dispose()
        }
        $value = $text | ConvertFrom-Json -Depth 100
    } catch {
        throw "O JSON $Label é inválido ou contém propriedade duplicada: $($_.Exception.Message)"
    }
    return [pscustomobject]@{ Text = $text; Value = $value }
}

function Assert-RequiredProperty {
    param(
        [Parameter(Mandatory)][object]$Object,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Label
    )

    if ($Object.PSObject.Properties.Name -cnotcontains $Name) {
        throw "A propriedade '$Name' está ausente em $Label."
    }
}

function Assert-ExactValue {
    param(
        [AllowNull()][object]$Actual,
        [AllowNull()][object]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    if ([string]$Actual -cne [string]$Expected) {
        throw "O valor $Label diverge: esperado '$Expected', recebido '$Actual'."
    }
}

function Assert-ExactSet {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        if ($item -isnot [string] -or -not $actualSet.Add($item)) {
            throw "O conjunto $Label contém item não textual ou duplicado."
        }
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Expected)) {
        if ($item -isnot [string] -or -not $expectedSet.Add($item)) {
            throw "A baseline $Label contém item não textual ou duplicado."
        }
    }
    if ($actualSet.Count -ne $expectedSet.Count) {
        throw "O conjunto $Label diverge da decisão V2-035c."
    }
    foreach ($item in $expectedSet) {
        if (-not $actualSet.Contains($item)) {
            throw "O conjunto $Label diverge da decisão V2-035c."
        }
    }
}

function Assert-ExactSequence {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualItems = @($Actual | ForEach-Object { [string]$_ })
    $expectedItems = @($Expected | ForEach-Object { [string]$_ })
    if ($actualItems.Count -ne $expectedItems.Count) {
        throw "A sequência $Label diverge da decisão V2-035c."
    }
    for ($index = 0; $index -lt $expectedItems.Count; $index++) {
        if ($actualItems[$index] -cne $expectedItems[$index]) {
            throw "A sequência $Label diverge na posição $index."
        }
    }
}

function Assert-ExactRows {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][string[]]$Properties,
        [Parameter(Mandatory)][string]$Label
    )

    $actualKeys = foreach ($row in @($Actual)) {
        foreach ($property in $Properties) {
            Assert-RequiredProperty -Object $row -Name $property -Label $Label
        }
        ($Properties | ForEach-Object { [string]$row.$_ }) -join [char]0x1F
    }
    $expectedKeys = foreach ($row in @($Expected)) {
        ($Properties | ForEach-Object { [string]$row.$_ }) -join [char]0x1F
    }
    Assert-ExactSet -Actual @($actualKeys) -Expected @($expectedKeys) -Label $Label
}

function Resolve-LocalEvidencePath {
    param([Parameter(Mandatory)][string]$RelativePath)

    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        throw "A evidência '$RelativePath' não pode usar path absoluto."
    }
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativePath))
    $insideAllowedRoot = $false
    foreach ($root in $allowedRoots) {
        $prefix = $root.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        if ($resolved.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $insideAllowedRoot = $true
            break
        }
    }
    if (-not $insideAllowedRoot -or -not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "A evidência '$RelativePath' está ausente ou fora dos dois repositórios permitidos."
    }
    return $resolved
}

function Get-FileSha256 {
    param([Parameter(Mandatory)][string]$LiteralPath)

    return (Get-FileHash -Algorithm SHA256 -LiteralPath $LiteralPath).Hash.ToLowerInvariant()
}

$catalogJson = Read-StrictJson -LiteralPath $catalogPath -MaximumBytes 512KB -Label 'da decisão V2-035c'
$fixtureJson = Read-StrictJson -LiteralPath $fixturePath -MaximumBytes 256KB -Label 'das fixtures V2-035c'
$reassessmentJson = Read-StrictJson -LiteralPath $reassessmentPath -MaximumBytes 512KB -Label 'da reavaliação V02 de V2-035c'
$reassessmentFixtureJson = Read-StrictJson -LiteralPath $reassessmentFixturePath -MaximumBytes 256KB -Label 'das fixtures da reavaliação V02'
$catalog = $catalogJson.Value
$fixtures = $fixtureJson.Value
$reassessment = $reassessmentJson.Value
$reassessmentFixtures = $reassessmentFixtureJson.Value
$matrixText = Read-StrictUtf8 -LiteralPath $matrixPath -MaximumBytes 512KB
$reassessmentMatrixText = Read-StrictUtf8 -LiteralPath $reassessmentMatrixPath -MaximumBytes 512KB
$readmeText = Read-StrictUtf8 -LiteralPath $readmePath -MaximumBytes 256KB
$adrText = Read-StrictUtf8 -LiteralPath $adrPath -MaximumBytes 256KB
$stateText = Read-StrictUtf8 -LiteralPath $statePath -MaximumBytes 2MB

Assert-ExactValue $catalog.catalogVersion '2026-09-05.v2-035c.1' 'de catalogVersion'
Assert-ExactValue $catalog.decision 'FROTA_MANIFESTOS_DIMENSIONAL_FAIL_CLOSED' 'da decisão'
Assert-ExactValue $catalog.decisionStatus 'COMPLETE_LOCAL_DECISION_ONLY' 'de decisionStatus'
Assert-ExactValue $catalog.task 'V2-035c' 'da tarefa'
Assert-ExactValue $catalog.route 'D00' 'da rota'
if ([int]$catalog.block -ne 38) { throw 'O catálogo não pertence ao Bloco 38.' }
Assert-ExactValue $catalog.scope 'DECISION_ONLY_NO_IMPLEMENTATION_NO_SOURCE_IO_NO_PUBLICATION' 'do escopo'

Assert-ExactSet @($catalog.closedVocabularies.dimensionStatus) @('EXECUTION_READY', 'PARTIAL_DECISION_ONLY', 'BLOCKED') 'de estados dimensionais'
Assert-ExactSet @($catalog.closedVocabularies.presence) @('ABSENT', 'NULL', 'VALUE') 'de presença'
Assert-ExactSet @($catalog.closedVocabularies.dimension) @('VEICULOS', 'MOTORISTAS') 'de dimensões'
Assert-ExactSet @($catalog.closedVocabularies.observationRole) @('MAIN_VEHICLE', 'TRAILER_POSITION_1', 'TRAILER_POSITION_2', 'DRIVER') 'de papéis'
Assert-ExactSet @($catalog.closedVocabularies.evidenceRole) @('SUPPORT', 'COUNTEREVIDENCE', 'BOUNDARY') 'de papéis de evidência'
$allowedDispositions = @(
    'PRESERVE_OBSERVATION_ONLY', 'QUARANTINE_OBSERVATION', 'BLOCK_DIMENSIONAL_PROMOTION',
    'REPLAY_NO_OP', 'OLDER_OBSERVATION_NO_OP', 'NO_SWEEP_NO_STATE_CHANGE',
    'NO_RELATION_MATERIALIZATION', 'KEEP_ROLES_DISTINCT', 'KEEP_TRAILER_POSITIONS_DISTINCT'
)
Assert-ExactSet @($catalog.closedVocabularies.disposition) $allowedDispositions 'de disposições'

$contract = (Read-StrictJson -LiteralPath (Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6399\manifesto.json') -MaximumBytes 256KB -Label 'do contrato 6399').Value
$identity = (Read-StrictJson -LiteralPath (Join-Path $repositoryRoot 'docs\catalogos\identidade-manifestos\manifesto.json') -MaximumBytes 512KB -Label 'da identidade 6399').Value
$v03 = (Read-StrictJson -LiteralPath (Join-Path $repositoryRoot 'docs\catalogos\manifestos-v2-026\decisao-v03.json') -MaximumBytes 512KB -Label 'da decisão V03').Value
Assert-ExactValue $catalog.dependencyBinding.requiredTask 'V2-026' 'da dependência'
Assert-ExactValue $catalog.dependencyBinding.requiredTaskStatus 'IMPLEMENTADA_EM_SHADOW_ROLLBACK_ONLY' 'do estado da dependência'
Assert-ExactValue $catalog.dependencyBinding.sourceContract.catalogVersion $contract.contract.contractVersion 'da versão do contrato 6399'
Assert-ExactValue $catalog.dependencyBinding.sourceContract.releaseFingerprint $contract.contract.fingerprints.release 'do release fingerprint 6399'
Assert-ExactValue $catalog.dependencyBinding.sourceContract.classification $contract.contract.overallClassification 'da classificação do contrato 6399'
Assert-ExactValue $catalog.dependencyBinding.manifestIdentity.catalogVersion $identity.catalogVersion 'da versão da identidade P01'
Assert-ExactValue $catalog.dependencyBinding.manifestIdentity.decisionStatus $identity.decisionStatus 'do estado da identidade P01'
Assert-ExactValue $catalog.dependencyBinding.manifestIdentity.identityFingerprint $identity.identity.fingerprints.identity 'do fingerprint da identidade P01'
Assert-ExactValue $catalog.dependencyBinding.manifestDecision.catalogVersion $v03.catalogVersion 'da versão V03'
Assert-ExactValue $catalog.dependencyBinding.manifestDecision.decisionStatus $v03.decisionStatus 'do estado V03'
Assert-ExactValue $catalog.dependencyBinding.bindingLimit 'MANIFEST_IDENTITY_AND_FRESHNESS_DO_NOT_PROVE_VEHICLE_OR_DRIVER_MASTER_IDENTITY' 'do limite das dependências'

Assert-ExactSequence @($catalog.globalRules.freshness.manifestObservationOrder) @('/finished_at', '/closed_at', '/departured_at', '/created_at') 'do frescor de Manifesto'
Assert-ExactSet @($catalog.globalRules.freshness.forbiddenTieBreakers) @('ARRIVAL', 'PAGE', 'RECORD_ORDER', 'HASH', 'MAX') 'de desempates proibidos'
Assert-ExactValue $catalog.globalRules.relationship 'VEHICLE_AND_DRIVER_COOCCURRENCE_IN_A_MANIFEST_NEVER_CREATES_A_CANONICAL_RELATION_JOIN_OR_FOREIGN_KEY' 'da ausência de relação'
Assert-ExactValue $catalog.globalRules.absence 'NOT_OBSERVED_IN_PAGE_OR_WINDOW_IS_NO_STATE_CHANGE_AND_NEVER_DELETE_DEACTIVATE_OR_SWEEP' 'da ausência não destrutiva'
Assert-ExactValue $catalog.globalRules.promotion 'NO_DIMENSIONAL_CURRENT_HISTORY_OR_PUBLISHED_ROW_MAY_BE_MATERIALIZED_FROM_THIS_DECISION' 'da proibição de promoção'

$dimensions = @($catalog.dimensions)
if ($dimensions.Count -ne 2) { throw 'A decisão deve conter exatamente Veículos e Motoristas.' }
Assert-ExactSet @($dimensions | ForEach-Object { [string]$_.name }) @('VEICULOS', 'MOTORISTAS') 'dos resultados dimensionais'
foreach ($dimension in $dimensions) {
    foreach ($property in @('name', 'status', 'resultReason', 'keys', 'grain', 'branch', 'roles', 'normalizationAndValidation', 'lifecycle', 'quarantineReasons', 'unblockEvidence')) {
        Assert-RequiredProperty -Object $dimension -Name $property -Label "da dimensão $($dimension.name)"
    }
    if ([string]$dimension.status -cnotin @($catalog.closedVocabularies.dimensionStatus)) {
        throw "A dimensão $($dimension.name) usa estado fora do vocabulário fechado."
    }
    Assert-ExactValue $dimension.status 'BLOCKED' "do resultado $($dimension.name)"
    foreach ($keyName in @('sourceKey', 'canonicalKey', 'businessKey')) {
        Assert-RequiredProperty -Object $dimension.keys -Name $keyName -Label "das chaves $($dimension.name)"
        if (@($dimension.keys.$keyName.accepted).Count -ne 0) {
            throw "A dimensão $($dimension.name) aceitou indevidamente uma chave $keyName."
        }
    }
    Assert-ExactValue $dimension.keys.sourceKey.status 'ABSENT' "da source key $($dimension.name)"
    Assert-ExactValue $dimension.keys.canonicalKey.status 'BLOCKED' "da canonical key $($dimension.name)"
    Assert-ExactValue $dimension.keys.businessKey.status 'BLOCKED' "da business key $($dimension.name)"
    Assert-ExactValue $dimension.grain.status 'BLOCKED' "do grão $($dimension.name)"
    Assert-ExactValue $dimension.branch.sourcePath '/data/mft_crn_psn_nickname' "do path de filial $($dimension.name)"
    if (@($dimension.quarantineReasons).Count -lt 7 -or @($dimension.unblockEvidence).Count -lt 5) {
        throw "A dimensão $($dimension.name) não explicita quarentena e evidência de desbloqueio suficientes."
    }
}
$vehicles = @($dimensions | Where-Object { [string]$_.name -ceq 'VEICULOS' })[0]
$drivers = @($dimensions | Where-Object { [string]$_.name -ceq 'MOTORISTAS' })[0]
Assert-ExactRows @($vehicles.roles) @(
    [pscustomobject]@{ role='MAIN_VEHICLE'; platePath='/data/mft_vie_license_plate'; capacityPath='/data/mft_vie_weight_capacity' },
    [pscustomobject]@{ role='TRAILER_POSITION_1'; platePath='/data/mft_tl1_license_plate'; capacityPath='/data/mft_tl1_weight_capacity' },
    [pscustomobject]@{ role='TRAILER_POSITION_2'; platePath='/data/mft_tl2_license_plate'; capacityPath='/data/mft_tl2_weight_capacity' }
) @('role', 'platePath', 'capacityPath') 'dos papéis de Veículos'
Assert-ExactRows @($drivers.roles) @(
    [pscustomobject]@{ role='DRIVER'; namePath='/data/mft_mdr_iil_name'; contractTypePath='/data/mft_mdr_contract_type' }
) @('role', 'namePath', 'contractTypePath') 'do papel de Motoristas'
Assert-ExactValue $vehicles.normalizationAndValidation.plateNormalizer 'NOT_APPROVED' 'do normalizador de placa'
Assert-ExactValue $vehicles.normalizationAndValidation.platePattern 'NOT_APPROVED' 'da validação lexical de placa'
Assert-ExactValue $drivers.normalizationAndValidation.nameNormalizer 'NOT_APPROVED' 'do normalizador de nome'
Assert-ExactValue $drivers.normalizationAndValidation.legacyGenericSubstringExclusion 'COUNTEREVIDENCE_ONLY_NEVER_SILENTLY_DROP' 'da regra de nome genérico'
Assert-ExactValue $drivers.contractType.decision 'PRESERVE_AS_MANIFEST_CONTEXT_ONLY_NEVER_IDENTITY_OR_PROOF_OF_EMPLOYMENT_VALIDITY' 'do tipo de contrato do motorista'

$expectedFieldRows = @(
    [pscustomobject]@{ dimension='VEICULOS'; role='MAIN_VEHICLE'; sourcePath='/data/mft_vie_license_plate'; kind='TEXT_10'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='VEICULOS'; role='MAIN_VEHICLE'; sourcePath='/data/mft_vie_vee_name'; kind='TEXT_255'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='VEICULOS'; role='MAIN_VEHICLE'; sourcePath='/data/mft_vie_onr_name'; kind='TEXT_255'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='VEICULOS'; role='MAIN_VEHICLE'; sourcePath='/data/mft_vie_weight_capacity'; kind='DECIMAL'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='VEICULOS'; role='MAIN_VEHICLE'; sourcePath='/data/contract_type'; kind='TEXT_50'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='VEICULOS'; role='TRAILER_POSITION_1'; sourcePath='/data/mft_tl1_license_plate'; kind='TEXT_10'; decision='KEEP_ROLES_DISTINCT' },
    [pscustomobject]@{ dimension='VEICULOS'; role='TRAILER_POSITION_1'; sourcePath='/data/mft_tl1_weight_capacity'; kind='DECIMAL'; decision='KEEP_ROLES_DISTINCT' },
    [pscustomobject]@{ dimension='VEICULOS'; role='TRAILER_POSITION_2'; sourcePath='/data/mft_tl2_license_plate'; kind='TEXT_10'; decision='KEEP_TRAILER_POSITIONS_DISTINCT' },
    [pscustomobject]@{ dimension='VEICULOS'; role='TRAILER_POSITION_2'; sourcePath='/data/mft_tl2_weight_capacity'; kind='DECIMAL'; decision='KEEP_TRAILER_POSITIONS_DISTINCT' },
    [pscustomobject]@{ dimension='MOTORISTAS'; role='DRIVER'; sourcePath='/data/mft_mdr_iil_name'; kind='TEXT_255'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='MOTORISTAS'; role='DRIVER'; sourcePath='/data/mft_mdr_contract_type'; kind='TEXT_50'; decision='PRESERVE_OBSERVATION_ONLY' },
    [pscustomobject]@{ dimension='BOTH'; role='MANIFEST_CONTEXT'; sourcePath='/data/mft_crn_psn_nickname'; kind='TEXT_255'; decision='PRESERVE_OBSERVATION_ONLY' }
)
Assert-ExactRows @($catalog.fieldInventory) $expectedFieldRows @('dimension', 'role', 'sourcePath', 'kind', 'decision') 'do inventário de campos'

Assert-ExactValue $catalog.fixtureSet.path 'docs/catalogos/frota-manifestos-v2-035c/fixtures/casos-v01.synthetic.json' 'do path das fixtures'
if ($catalog.fixtureSet.syntheticOnly -isnot [bool] -or -not [bool]$catalog.fixtureSet.syntheticOnly -or
    $catalog.fixtureSet.containsRealData -isnot [bool] -or [bool]$catalog.fixtureSet.containsRealData) {
    throw 'O catálogo não declara fixtures exclusivamente sintéticas e sem dado real.'
}
$actualFixtureHash = Get-FileSha256 -LiteralPath $fixturePath
Assert-ExactValue $catalog.fixtureSet.sha256 $actualFixtureHash 'do SHA-256 das fixtures'
if ($fixtures.syntheticOnly -isnot [bool] -or -not [bool]$fixtures.syntheticOnly -or
    $fixtures.containsRealData -isnot [bool] -or [bool]$fixtures.containsRealData) {
    throw 'O envelope das fixtures não é estritamente sintético.'
}
Assert-ExactValue $fixtures.representation 'SYMBOLIC_CLASSES_AND_SYNTH_TOKENS_ONLY' 'da representação das fixtures'
$fixtureCases = @($fixtures.cases)
$expectedFixtureIds = @(
    'PRESENCE_TRI_STATE', 'INVALID_UNICODE', 'UTF16_OVERFLOW', 'VEHICLE_PLATE_NO_SOURCE_ID',
    'VEHICLE_NORMALIZATION_COLLISION', 'VEHICLE_LEXICAL_FORMAT_UNPROVEN', 'VEHICLE_PLATE_REASSIGNMENT',
    'VEHICLE_BRANCH_CONTEXT_ONLY', 'VEHICLE_MAIN_TRAILER_ROLE_COLLISION', 'VEHICLE_TRAILER_POSITION_SWAP',
    'VEHICLE_ATTRIBUTES_NOT_IDENTITY', 'VEHICLE_EQUAL_FRESHNESS_CONFLICT', 'VEHICLE_IDENTICAL_REPLAY',
    'VEHICLE_OLDER_OBSERVATION', 'VEHICLE_ABSENCE_NO_SWEEP', 'DRIVER_NAME_NO_SOURCE_ID', 'DRIVER_HOMONYM',
    'DRIVER_GENERIC_LABEL', 'DRIVER_UNICODE_NAME_COLLISION', 'DRIVER_CONTRACT_TYPE_NOT_IDENTITY',
    'DRIVER_BRANCH_CONTEXT_ONLY', 'DRIVER_RENAME_REKEY', 'DRIVER_EQUAL_FRESHNESS_CONFLICT',
    'DRIVER_ABSENCE_NO_SWEEP', 'COOCCURRENCE_NO_RELATION'
)
Assert-ExactSet @($fixtureCases | ForEach-Object { [string]$_.id }) $expectedFixtureIds 'de IDs das fixtures'
$fixtureById = @{}
foreach ($fixture in $fixtureCases) {
    foreach ($property in @('id', 'synthetic', 'dimension', 'inputClass', 'symbols', 'expected')) {
        Assert-RequiredProperty -Object $fixture -Name $property -Label "da fixture $($fixture.id)"
    }
    if ($fixture.synthetic -isnot [bool] -or -not [bool]$fixture.synthetic) {
        throw "A fixture $($fixture.id) não está marcada como sintética."
    }
    if ([string]$fixture.dimension -cnotin @('BOTH', 'VEICULOS', 'MOTORISTAS') -or
        [string]$fixture.inputClass -cnotmatch '^[A-Z0-9_]+$' -or
        [string]$fixture.expected -cnotin $allowedDispositions) {
        throw "A fixture $($fixture.id) usa dimensão, classe ou disposição inválida."
    }
    if (@($fixture.symbols).Count -eq 0) { throw "A fixture $($fixture.id) não possui símbolo sintético." }
    foreach ($symbol in @($fixture.symbols)) {
        if ([string]$symbol -cnotmatch '^SYNTH_[A-Z0-9_]+$') {
            throw "A fixture $($fixture.id) contém valor que não é token SYNTH_*."
        }
    }
    $fixtureById[[string]$fixture.id] = $fixture
}
if ($fixtureJson.Text -match '(?i)authorization\s*[:=]|bearer\s+|password\s*[:=]|https?://|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}|\b\d{11,14}\b|\b[A-Z]{3}[0-9][A-Z0-9][0-9]{2}\b') {
    throw 'As fixtures contêm marcador incompatível com casos simbólicos sem dado real.'
}

$expectedHeader = 'rule_id,dimension,topic,local_source,evidence_role,supports,does_not_support,counterexample_fixture,expected_disposition,test,decision_status'
$actualHeader = ($matrixText -split "`r?`n", 2)[0]
Assert-ExactValue $actualHeader $expectedHeader 'do header da matriz'
$matrix = @($matrixText | ConvertFrom-Csv)
if ($matrix.Count -ne 25) { throw 'A matriz V2-035c deve conter exatamente 25 regras.' }
Assert-ExactSet @($matrix | ForEach-Object { [string]$_.rule_id }) @(
    'FM-SHARED-001', 'FM-SHARED-002', 'FM-SHARED-003', 'FM-SHARED-004',
    'FM-VEH-001', 'FM-VEH-002', 'FM-VEH-003', 'FM-VEH-004', 'FM-VEH-005', 'FM-VEH-006',
    'FM-VEH-007', 'FM-VEH-008', 'FM-VEH-009', 'FM-VEH-010', 'FM-VEH-011', 'FM-VEH-012',
    'FM-DRV-001', 'FM-DRV-002', 'FM-DRV-003', 'FM-DRV-004', 'FM-DRV-005', 'FM-DRV-006',
    'FM-DRV-007', 'FM-DRV-008', 'FM-DRV-009'
) 'de regras da matriz'
$evidencePaths = @($catalog.evidence | ForEach-Object { [string]$_.path })
foreach ($row in $matrix) {
    foreach ($column in @('rule_id', 'dimension', 'topic', 'local_source', 'evidence_role', 'supports', 'does_not_support', 'counterexample_fixture', 'expected_disposition', 'test', 'decision_status')) {
        if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
            throw "A regra $($row.rule_id) possui coluna obrigatória vazia: $column."
        }
    }
    if ([string]$row.dimension -cnotin @('BOTH', 'VEICULOS', 'MOTORISTAS') -or
        [string]$row.evidence_role -cnotin @($catalog.closedVocabularies.evidenceRole) -or
        [string]$row.expected_disposition -cnotin $allowedDispositions -or
        [string]$row.decision_status -cne 'BLOCKED') {
        throw "A regra $($row.rule_id) usa vocabulário fora do catálogo."
    }
    if ([string]$row.local_source -cnotin $evidencePaths) {
        throw "A origem da regra $($row.rule_id) não está vinculada no catálogo."
    }
    if (-not $fixtureById.ContainsKey([string]$row.counterexample_fixture)) {
        throw "A regra $($row.rule_id) referencia fixture inexistente."
    }
    Assert-ExactValue $row.expected_disposition $fixtureById[[string]$row.counterexample_fixture].expected "da disposição da regra $($row.rule_id)"
}
Assert-ExactSet @($matrix | ForEach-Object { [string]$_.counterexample_fixture }) $expectedFixtureIds 'de cobertura das fixtures pela matriz'
Assert-ExactSet @($matrix | ForEach-Object { ([string]$_.dimension) + '|' + ([string]$_.topic) }) @(
    'BOTH|PRESENCE', 'BOTH|UNICODE', 'BOTH|TEXT_LIMITS', 'BOTH|RELATIONSHIP',
    'VEICULOS|SOURCE_KEY', 'VEICULOS|BUSINESS_KEY', 'VEICULOS|PLATE_VALIDATION', 'VEICULOS|REKEY',
    'VEICULOS|BRANCH', 'VEICULOS|MAIN_VS_TRAILER', 'VEICULOS|TRAILER_POSITIONS', 'VEICULOS|ATTRIBUTES',
    'VEICULOS|FRESHNESS_CONFLICT', 'VEICULOS|REPLAY', 'VEICULOS|OUT_OF_ORDER', 'VEICULOS|ABSENCE',
    'MOTORISTAS|SOURCE_KEY', 'MOTORISTAS|HOMONYMS', 'MOTORISTAS|GENERIC_NAMES', 'MOTORISTAS|NORMALIZATION',
    'MOTORISTAS|CONTRACT_TYPE', 'MOTORISTAS|BRANCH', 'MOTORISTAS|REKEY', 'MOTORISTAS|FRESHNESS_CONFLICT',
    'MOTORISTAS|ABSENCE'
) 'de tópicos por dimensão'

$expectedEvidencePaths = @(
    'docs/catalogos/contratos-esl-6399/manifesto.json',
    'docs/catalogos/identidade-manifestos/manifesto.json',
    'docs/catalogos/manifestos-v2-026/decisao-v03.json',
    'docs/adr/0024-manifestos-6399-identidade-presenca-frescor-reducers.md',
    'src/main/java/br/com/esl/etl/v2/modulos/manifestos/domain/ManifestoTextField.java',
    'src/main/java/br/com/esl/etl/v2/modulos/manifestos/aplicacao/ManifestoDataExportRecordMapper.java',
    'src/test/java/br/com/esl/etl/v2/modulos/manifestos/aplicacao/ManifestoDataExportRecordMapperTest.java',
    'database/migrations/V012__create_manifestos_shadow_vertical.sql',
    'docs/catalogos/portabilidade/matriz-campos.csv',
    '../etl-extracao-dados/database/views-dimensao/021_criar_view_dim_veiculos.sql',
    '../etl-extracao-dados/database/views-dimensao/022_criar_view_dim_motoristas.sql',
    '../etl-extracao-dados/database/tabelas/003_criar_tabela_manifestos.sql',
    '../etl-extracao-dados/docs/relatorio-bi-dashboards-logistica.md',
    '../etl-extracao-dados/docs/legado/pipelines-antigos/02-apis/dataexport/manifestos.md'
)
Assert-ExactSet $evidencePaths $expectedEvidencePaths 'de evidências locais'
Import-Module (Join-Path $PSScriptRoot 'OfflinePreparationSuccession.psm1')
$offlineSuccession = Get-OfflinePreparationSuccession -Root $repositoryRoot
foreach ($evidence in @($catalog.evidence)) {
    foreach ($property in @('path', 'sha256', 'role', 'supports', 'doesNotSupport')) {
        Assert-RequiredProperty -Object $evidence -Name $property -Label 'da evidência V2-035c'
    }
    if ([string]$evidence.role -cnotin @($catalog.closedVocabularies.evidenceRole)) {
        throw "A evidência $($evidence.path) possui papel fora do vocabulário."
    }
    $resolvedEvidence = Resolve-LocalEvidencePath -RelativePath ([string]$evidence.path)
    if ($null -ne $offlineSuccession -and $evidence.path -ceq $offlineSuccession.manifest.fleet.path) {
        # A decisão antiga aponta para seus bytes; a sucessão também verifica o mapper atual.
        $resolvedEvidence = Resolve-LocalEvidencePath -RelativePath $offlineSuccession.manifest.fleet.snapshot
    }
    Assert-ExactValue $evidence.sha256 (Get-FileSha256 -LiteralPath $resolvedEvidence) "do SHA-256 de $($evidence.path)"
}

$fieldText = Read-StrictUtf8 -LiteralPath (Join-Path $repositoryRoot 'src\main\java\br\com\esl\etl\v2\modulos\manifestos\domain\ManifestoTextField.java') -MaximumBytes 128KB
$mapperText = Read-StrictUtf8 -LiteralPath (Join-Path $repositoryRoot 'src\main\java\br\com\esl\etl\v2\modulos\manifestos\aplicacao\ManifestoDataExportRecordMapper.java') -MaximumBytes 512KB
$mapperTestText = Read-StrictUtf8 -LiteralPath (Join-Path $repositoryRoot 'src\test\java\br\com\esl\etl\v2\modulos\manifestos\aplicacao\ManifestoDataExportRecordMapperTest.java') -MaximumBytes 256KB
$migrationText = Read-StrictUtf8 -LiteralPath (Join-Path $repositoryRoot 'database\migrations\V012__create_manifestos_shadow_vertical.sql') -MaximumBytes 1MB
$legacyVehicleText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'database\views-dimensao\021_criar_view_dim_veiculos.sql') -MaximumBytes 128KB
$legacyDriverText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'database\views-dimensao\022_criar_view_dim_motoristas.sql') -MaximumBytes 128KB
foreach ($anchor in @(
    'VEHICLE_LICENSE_PLATE\("mft_vie_license_plate", 10\)',
    'VEHICLE_NAME\("mft_vie_vee_name", 255\)',
    'VEHICLE_OWNER_NAME\("mft_vie_onr_name", 255\)',
    'DRIVER_NAME\("mft_mdr_iil_name", 255\)',
    'CARRIER_NICKNAME\("mft_crn_psn_nickname", 255\)',
    'CONTRACT_TYPE\("contract_type", 50\)',
    'DRIVER_CONTRACT_TYPE\("mft_mdr_contract_type", 50\)',
    'TRAILER_ONE_LICENSE_PLATE\("mft_tl1_license_plate", 10\)',
    'TRAILER_TWO_LICENSE_PLATE\("mft_tl2_license_plate", 10\)'
)) {
    if ($fieldText -cnotmatch $anchor) { throw "O enum ManifestoTextField perdeu a âncora '$anchor'." }
}
if ($mapperText -cnotmatch 'value\.textValue\(\)\.length\(\) > field\.maximumUtf16Units\(\)' -or
    $mapperText -cnotmatch 'Character\.isHighSurrogate' -or
    $mapperText -cnotmatch 'Character\.isLowSurrogate' -or
    $mapperTestText -cnotmatch 'accepted\.put\("mft_vie_license_plate"' -or
    $migrationText -cmatch '(?i)CREATE\s+TABLE\s+core\.dim_(?:veiculo|motorista)') {
    throw 'A fronteira local de texto/Unicode ou a ausência de dimensão na V012 mudou.'
}
if ($legacyVehicleText -cnotmatch 'UPPER\(LTRIM\(RTRIM\(vehicle_plate\)\)\)' -or
    $legacyVehicleText -cnotmatch 'MAX\(UPPER\(LTRIM\(RTRIM\(vehicle_type\)\)\)\)' -or
    $legacyVehicleText -cnotmatch 'branch_nickname' -or
    $legacyDriverText -cnotmatch 'SELECT DISTINCT' -or
    $legacyDriverText -cnotmatch 'UPPER\(LTRIM\(RTRIM\(driver_name\)\)\)' -or
    $legacyDriverText -cnotmatch "driver_name NOT LIKE '%MOTORISTA%'") {
    throw 'A contraevidência das views dimensionais legadas mudou.'
}

$portability = @(Import-Csv -LiteralPath (Join-Path $repositoryRoot 'docs\catalogos\portabilidade\matriz-campos.csv') | Where-Object {
    [string]$_.entity -ceq 'manifestos' -and
    [string]$_.row_kind -ceq 'DATA_EXPORT_DATA_FIELD' -and
    [string]$_.source_path -cin @($expectedFieldRows | ForEach-Object { [string]$_.sourcePath })
})
if ($portability.Count -ne 12) { throw 'A matriz global não contém os 12 paths exatos de Frota no grão de Manifesto.' }
foreach ($row in $portability) {
    if ([string]$row.cardinality -cne 'ROOT_SCALAR_TRI_STATE_ONE_OBSERVATION_PER_PHYSICAL_RECORD') {
        throw "O path $($row.source_path) deixou de ser escalar da observação física de Manifesto."
    }
}

Assert-ExactValue $catalog.handoff.vehiclesExecutionReady $false 'de vehiclesExecutionReady'
Assert-ExactValue $catalog.handoff.driversExecutionReady $false 'de driversExecutionReady'
Assert-ExactValue $catalog.handoff.terraRunbookStatus 'MUST_NOT_EXIST' 'do handoff Terra'
Assert-ExactValue $catalog.handoff.nextOfficialBlock 'NONE' 'do próximo bloco'
Assert-ExactValue $catalog.execution.status 'NOT_STARTED' 'da execução dimensional'
Assert-ExactSet @($catalog.execution.allowedArtifacts) @('DOCUMENTATION', 'SYNTHETIC_FIXTURES', 'LOCAL_VALIDATOR') 'de artefatos permitidos'
Assert-ExactSet @($catalog.execution.forbiddenArtifacts) @('JAVA', 'MIGRATION', 'SCHEMA', 'TABLE', 'VIEW', 'PROCEDURE', 'GRANT', 'RUNTIME', 'RELATION', 'FACT', 'PUB_CONTRACT', 'TERRA_IMPLEMENTATION_PROMPT') 'de artefatos proibidos'
if (Test-Path -LiteralPath $terraRunbookPath) {
    throw 'O runbook Terra D03 existe apesar de VEICULOS=BLOCKED.'
}
if ($readmeText -cnotmatch '\| Veículos \| `BLOCKED` \|' -or
    $readmeText -cnotmatch '\| Motoristas \| `BLOCKED` \|' -or
    $readmeText -cnotmatch 'deve permanecer ausente' -or
    $adrText -cnotmatch 'PUB-07 deixa de autorizar' -or
    $adrText -cnotmatch 'D03 e D04 ficam bloqueadas' -or
    $stateText -cnotmatch '(?m)^\s*- \[x\] \*\*V2-035c\b' -or
    $stateText -cnotmatch '<code>VEICULOS=BLOCKED</code> e <code>MOTORISTAS=BLOCKED</code>' -or
    $stateText -cnotmatch 'Nenhum próximo bloco oficial está elegível') {
    throw 'README, ADR ou STATES.md não espelham integralmente o resultado fail-closed.'
}

Assert-ExactValue $reassessment.catalogVersion '2026-09-05.v2-035c.2' 'da versão da reavaliação V02'
Assert-ExactValue $reassessment.decision 'FROTA_MANIFESTOS_AUTHORIZED_REASSESSMENT_FAIL_CLOSED' 'da decisão V02'
Assert-ExactValue $reassessment.decisionStatus 'COMPLETE_LOCAL_REASSESSMENT_BLOCKED' 'do estado V02'
Assert-ExactValue $reassessment.task 'V2-035c' 'da tarefa V02'
Assert-ExactValue $reassessment.priorRoute 'D00' 'da rota histórica V02'
if ([int]$reassessment.priorBlock -ne 38) { throw 'A reavaliação V02 não preserva o Bloco 38 como histórico.' }
Assert-ExactValue $reassessment.scope 'REASSESSMENT_ONLY_CHAT_AND_WORKTREE_NO_IMPLEMENTATION_NO_SOURCE_IO_NO_PUBLICATION' 'do escopo V02'

Assert-ExactValue $reassessment.ownerAuthorization.source 'CURRENT_CHAT_OWNER_INSTRUCTION' 'da origem da autorização V02'
Assert-ExactValue $reassessment.ownerAuthorization.classification 'AUTHORIZATION_ONLY_NOT_DIMENSIONAL_TECHNICAL_EVIDENCE' 'da classificação da autorização'
Assert-ExactSet @($reassessment.ownerAuthorization.doesNotProve) @(
    'IMMUTABLE_PROVIDER_ID', 'SOURCE_BINDING', 'TENANT_BINDING', 'PLATE_OR_BRANCH_SEMANTICS',
    'DRIVER_IDENTITY_POLICY', 'ENTITY_LIFECYCLE', 'RELATIONSHIP'
) 'dos limites da autorização'

Assert-ExactValue $reassessment.evidenceBoundary.mode 'OFFLINE_READ_ONLY_EVIDENCE_AUDIT' 'do modo da reavaliação'
Assert-ExactValue $reassessment.evidenceBoundary.priorDecision.path 'docs/catalogos/frota-manifestos-v2-035c/decisao-v01.json' 'do path da decisão anterior'
Assert-ExactValue $reassessment.evidenceBoundary.priorDecision.sha256 (Get-FileSha256 -LiteralPath $catalogPath) 'do SHA-256 da decisão V01'
if ([int]$reassessment.evidenceBoundary.priorDecision.evidenceAnchorCount -ne @($catalog.evidence).Count -or
    [int]$reassessment.evidenceBoundary.priorDecision.evidenceAnchorCount -ne 14) {
    throw 'A reavaliação V02 não preserva exatamente os 14 anchors da V01.'
}
Assert-ExactValue $reassessment.evidenceBoundary.priorDecision.anchorPolicy 'REUSE_ONLY_AFTER_ALL_V01_SHA256_ANCHORS_MATCH' 'da política de anchors V02'
$baselineCutoff = [datetimeoffset]$reassessment.evidenceBoundary.worktreeInventory.baselineCutoff
Assert-ExactValue $baselineCutoff.ToString('yyyy-MM-ddTHH:mm:ss.fffffffzzz') '2026-09-05T13:35:21.2424688-03:00' 'do corte temporal pré-V02'
Assert-ExactSet @($reassessment.evidenceBoundary.worktreeInventory.excludedDirectories) @('.git', 'target', 'logs') 'das exclusões do inventário temporal'
if ([int]$reassessment.evidenceBoundary.worktreeInventory.filesAfterCutoffBeforeReassessment -ne 0) {
    throw 'A reavaliação V02 alega arquivo técnico novo antes de sua criação.'
}
Assert-ExactValue $reassessment.evidenceBoundary.worktreeInventory.classification 'NO_NEW_TECHNICAL_EVIDENCE' 'da classificação do inventário temporal'
Assert-ExactValue $reassessment.evidenceBoundary.chatAttachmentInventory.label 'pasted-text.txt' 'do anexo inventariado'
Assert-ExactValue $reassessment.evidenceBoundary.chatAttachmentInventory.sha256 '2d768f7f5474047ba3f4b36f74c59ca54bad2555d7efeab743fee6279db845c0' 'do hash sanitizado do anexo'
Assert-ExactValue $reassessment.evidenceBoundary.chatAttachmentInventory.classification 'GENERIC_OPERATIONAL_PROMPT_ONLY_NO_FLEET_CONTRACT' 'da classificação do anexo'
if ($reassessment.evidenceBoundary.chatAttachmentInventory.persistedCopy -isnot [bool] -or
    [bool]$reassessment.evidenceBoundary.chatAttachmentInventory.persistedCopy) {
    throw 'A reavaliação V02 não pode alegar cópia persistida do anexo.'
}
if (@($reassessment.evidenceBoundary.newQualifyingTechnicalEvidence).Count -ne 0 -or
    [int]$reassessment.evidenceBoundary.newQualifyingTechnicalEvidenceCount -ne 0) {
    throw 'A reavaliação V02 não pode alegar evidência técnica qualificadora inexistente.'
}
Assert-ExactValue $reassessment.evidenceBoundary.conclusion 'NO_EVIDENCE_PACKAGE_SATISFIES_ALL_REQUIREMENTS_FOR_EITHER_DIMENSION' 'da conclusão do inventário V02'
Assert-ExactSet @($reassessment.evidenceBoundary.prohibitedInputsNotUsed) @(
    'NETWORK', 'API', 'CURL', 'CREDENTIALS', 'DOT_ENV', 'REAL_PAYLOAD', 'DATABASE', 'SQLCMD',
    'DEPLOY', 'COMMIT', 'PUSH'
) 'dos inputs proibidos não usados'

Assert-ExactSet @($reassessment.closedVocabularies.dimension) @('VEICULOS', 'MOTORISTAS') 'das dimensões V02'
Assert-ExactSet @($reassessment.closedVocabularies.requirementStatus) @('MISSING', 'PROVEN_LIMITED') 'dos estados de requisito V02'
Assert-ExactSet @($reassessment.closedVocabularies.dimensionStatus) @('EXECUTION_READY', 'PARTIAL_DECISION_ONLY', 'BLOCKED') 'dos estados dimensionais V02'
Assert-ExactSet @($reassessment.closedVocabularies.fixtureGate) @('BLOCKED', 'PRESERVE_LIMITED_PROOF', 'ELIGIBLE_FOR_NEW_DECISION_SLICE') 'dos gates de fixture V02'
if ($reassessment.gatePolicy.allDimensionRequirementsRequired -isnot [bool] -or
    -not [bool]$reassessment.gatePolicy.allDimensionRequirementsRequired) {
    throw 'A reavaliação V02 deve exigir cumulativamente todos os requisitos dimensionais.'
}
Assert-ExactValue $reassessment.gatePolicy.anyMissingRequirement 'KEEP_DIMENSION_BLOCKED' 'da política de requisito ausente'
Assert-ExactValue $reassessment.gatePolicy.authorizationWithoutContractEvidence 'NO_STATUS_CHANGE' 'da política de autorização sem prova'
Assert-ExactValue $reassessment.gatePolicy.sufficientEvidenceAction 'CREATE_NEW_EXPLICIT_DECISION_SLICE_UNDER_V2_035' 'da ação com evidência suficiente'
Assert-ExactValue $reassessment.gatePolicy.insufficientEvidenceAction 'RECORD_EXACT_GAPS_WITHOUT_NEW_SLICE_ROUTE_BLOCK_OR_TERRA_PROMPT' 'da ação sem evidência suficiente'

$expectedReassessmentRequirements = @(
    [pscustomobject]@{ id='FM2-VEH-001'; dimension='VEICULOS'; requirement='IMMUTABLE_PROVIDER_ID_WITH_EXPLICIT_SOURCE_AND_TENANT'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D03' },
    [pscustomobject]@{ id='FM2-VEH-002'; dimension='VEICULOS'; requirement='PLATE_SEMANTICS_COLLISION_AND_REASSIGNMENT'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D03' },
    [pscustomobject]@{ id='FM2-VEH-003'; dimension='VEICULOS'; requirement='BRANCH_SEMANTICS_AND_CHANGE'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D03' },
    [pscustomobject]@{ id='FM2-VEH-004'; dimension='VEICULOS'; requirement='VALIDITY_CURRENT_HISTORY_ABSENCE_CONFLICT_AND_REKEY'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D03' },
    [pscustomobject]@{ id='FM2-VEH-005'; dimension='VEICULOS'; requirement='MAIN_VEHICLE_AND_EACH_TRAILER_REMAIN_DISTINCT'; status='PROVEN_LIMITED'; testAssertion='REQUIRE_PROVEN_LIMITED_AND_KEEP_ROLES_DISTINCT' },
    [pscustomobject]@{ id='FM2-DRV-001'; dimension='MOTORISTAS'; requirement='IMMUTABLE_PROVIDER_ID_WITH_EXPLICIT_SOURCE_AND_TENANT'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D04' },
    [pscustomobject]@{ id='FM2-DRV-002'; dimension='MOTORISTAS'; requirement='HOMONYM_GENERIC_RENAME_MERGE_AND_SPLIT_POLICY'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D04' },
    [pscustomobject]@{ id='FM2-DRV-003'; dimension='MOTORISTAS'; requirement='DRIVER_CONTRACT_SEMANTICS'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D04' },
    [pscustomobject]@{ id='FM2-DRV-004'; dimension='MOTORISTAS'; requirement='BRANCH_ASSIGNMENT_SEMANTICS_AND_CHANGE'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D04' },
    [pscustomobject]@{ id='FM2-DRV-005'; dimension='MOTORISTAS'; requirement='VALIDITY_CURRENT_HISTORY_ABSENCE_CONFLICT_AND_REKEY'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_BLOCK_D04' },
    [pscustomobject]@{ id='FM2-SHARED-001'; dimension='BOTH'; requirement='VEHICLE_DRIVER_RELATION_CONTRACT'; status='MISSING'; testAssertion='REQUIRE_MISSING_AND_PROHIBIT_RELATION' }
)
$reassessmentRequirements = @($reassessment.requirements)
Assert-ExactRows $reassessmentRequirements $expectedReassessmentRequirements @('id', 'dimension', 'requirement', 'status', 'testAssertion') 'dos requisitos V02'
$reassessmentRequirementById = @{}
foreach ($requirement in $reassessmentRequirements) {
    foreach ($property in @('id', 'dimension', 'requirement', 'status', 'origins', 'exactGap', 'limit', 'counterexample', 'fixtureIds', 'testAssertion')) {
        Assert-RequiredProperty -Object $requirement -Name $property -Label "do requisito $($requirement.id)"
    }
    foreach ($textProperty in @('exactGap', 'limit', 'counterexample')) {
        if ([string]::IsNullOrWhiteSpace([string]$requirement.$textProperty) -or [string]$requirement.$textProperty -cnotmatch '^[A-Z0-9_]+$') {
            throw "O requisito $($requirement.id) não registra $textProperty em vocabulário fechado."
        }
    }
    if (@($requirement.origins).Count -eq 0 -or @($requirement.fixtureIds).Count -eq 0) {
        throw "O requisito $($requirement.id) não possui origem ou fixture."
    }
    foreach ($origin in @($requirement.origins)) {
        if ([string]$origin -cne 'docs/catalogos/frota-manifestos-v2-035c/decisao-v01.json' -and
            [string]$origin -cnotin $evidencePaths) {
            throw "A origem $origin do requisito $($requirement.id) não pertence à evidência V01 fechada."
        }
        $null = Resolve-LocalEvidencePath -RelativePath ([string]$origin)
    }
    $reassessmentRequirementById[[string]$requirement.id] = $requirement
}

Assert-ExactValue $reassessment.fixtureSet.path 'docs/catalogos/frota-manifestos-v2-035c/fixtures/reavaliacao-v02.synthetic.json' 'do path das fixtures V02'
Assert-ExactValue $reassessment.fixtureSet.sha256 (Get-FileSha256 -LiteralPath $reassessmentFixturePath) 'do SHA-256 das fixtures V02'
if ([int]$reassessment.fixtureSet.caseCount -ne 13 -or
    $reassessment.fixtureSet.syntheticOnly -isnot [bool] -or -not [bool]$reassessment.fixtureSet.syntheticOnly -or
    $reassessment.fixtureSet.containsRealData -isnot [bool] -or [bool]$reassessment.fixtureSet.containsRealData) {
    throw 'O catálogo V02 não declara 13 fixtures estritamente sintéticas.'
}
if ($reassessmentFixtures.syntheticOnly -isnot [bool] -or -not [bool]$reassessmentFixtures.syntheticOnly -or
    $reassessmentFixtures.containsRealData -isnot [bool] -or [bool]$reassessmentFixtures.containsRealData) {
    throw 'O envelope de fixtures V02 não é estritamente sintético.'
}
Assert-ExactValue $reassessmentFixtures.representation 'SYMBOLIC_EVIDENCE_PACKAGES_AND_SYNTH_TOKENS_ONLY' 'da representação das fixtures V02'
Assert-ExactSet @($reassessmentFixtures.closedExpectedGates) @('BLOCKED', 'PRESERVE_LIMITED_PROOF', 'ELIGIBLE_FOR_NEW_DECISION_SLICE') 'dos resultados de fixtures V02'
$expectedReassessmentFixtureRows = @(
    [pscustomobject]@{ id='VEHICLE_PROVIDER_ID_SCOPE_MISSING'; dimension='VEICULOS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='VEHICLE_PLATE_REASSIGNMENT_MISSING'; dimension='VEICULOS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='VEHICLE_BRANCH_SEMANTICS_MISSING'; dimension='VEICULOS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='VEHICLE_LIFECYCLE_MISSING'; dimension='VEICULOS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='VEHICLE_ROLE_SEPARATION_PRESENT'; dimension='VEICULOS'; expectedGate='PRESERVE_LIMITED_PROOF' },
    [pscustomobject]@{ id='VEHICLE_HYPOTHETICAL_COMPLETE_PACKAGE'; dimension='VEICULOS'; expectedGate='ELIGIBLE_FOR_NEW_DECISION_SLICE' },
    [pscustomobject]@{ id='DRIVER_PROVIDER_ID_SCOPE_MISSING'; dimension='MOTORISTAS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='DRIVER_NAME_IDENTITY_POLICY_MISSING'; dimension='MOTORISTAS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='DRIVER_CONTRACT_SEMANTICS_MISSING'; dimension='MOTORISTAS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='DRIVER_BRANCH_SEMANTICS_MISSING'; dimension='MOTORISTAS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='DRIVER_LIFECYCLE_MISSING'; dimension='MOTORISTAS'; expectedGate='BLOCKED' },
    [pscustomobject]@{ id='DRIVER_HYPOTHETICAL_COMPLETE_PACKAGE'; dimension='MOTORISTAS'; expectedGate='ELIGIBLE_FOR_NEW_DECISION_SLICE' },
    [pscustomobject]@{ id='COOCCURRENCE_NO_RELATION'; dimension='BOTH'; expectedGate='BLOCKED' }
)
$reassessmentFixtureCases = @($reassessmentFixtures.cases)
Assert-ExactRows $reassessmentFixtureCases $expectedReassessmentFixtureRows @('id', 'dimension', 'expectedGate') 'das fixtures V02'
$reassessmentFixtureById = @{}
foreach ($fixture in $reassessmentFixtureCases) {
    foreach ($property in @('id', 'synthetic', 'dimension', 'symbols', 'missingRequirements', 'expectedGate')) {
        Assert-RequiredProperty -Object $fixture -Name $property -Label "da fixture V02 $($fixture.id)"
    }
    if ($fixture.synthetic -isnot [bool] -or -not [bool]$fixture.synthetic -or @($fixture.symbols).Count -eq 0) {
        throw "A fixture V02 $($fixture.id) não é estritamente sintética."
    }
    foreach ($symbol in @($fixture.symbols)) {
        if ([string]$symbol -cnotmatch '^SYNTH_[A-Z0-9_]+$') {
            throw "A fixture V02 $($fixture.id) contém símbolo fora de SYNTH_*."
        }
    }
    foreach ($missing in @($fixture.missingRequirements)) {
        if ([string]$missing -cnotmatch '^[A-Z0-9_]+$') {
            throw "A fixture V02 $($fixture.id) contém requisito ausente fora do vocabulário simbólico."
        }
    }
    $reassessmentFixtureById[[string]$fixture.id] = $fixture
}
foreach ($requirement in $reassessmentRequirements) {
    foreach ($fixtureId in @($requirement.fixtureIds)) {
        if (-not $reassessmentFixtureById.ContainsKey([string]$fixtureId)) {
            throw "O requisito $($requirement.id) referencia fixture V02 inexistente."
        }
    }
}
if ($reassessmentFixtureJson.Text -match '(?i)authorization\s*[:=]|bearer\s+|password\s*[:=]|https?://|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}') {
    throw 'As fixtures V02 contêm marcador incompatível com pacotes simbólicos sem dado real.'
}

Assert-ExactValue $reassessment.matrix.path 'docs/catalogos/frota-manifestos-v2-035c/matriz-reavaliacao-v02.csv' 'do path da matriz V02'
Assert-ExactValue $reassessment.matrix.sha256 (Get-FileSha256 -LiteralPath $reassessmentMatrixPath) 'do SHA-256 da matriz V02'
if ([int]$reassessment.matrix.rowCount -ne 11) { throw 'O catálogo V02 deve declarar 11 requisitos na matriz.' }
$expectedReassessmentHeader = 'requirement_id,dimension,requirement,status,origin,limit,counterexample,fixture_id,test_assertion,decision'
$actualReassessmentHeader = ($reassessmentMatrixText -split "`r?`n", 2)[0]
Assert-ExactValue $actualReassessmentHeader $expectedReassessmentHeader 'do header da matriz V02'
$reassessmentMatrix = @($reassessmentMatrixText | ConvertFrom-Csv)
if ($reassessmentMatrix.Count -ne 11) { throw 'A matriz V02 deve conter exatamente 11 requisitos.' }
Assert-ExactSet @($reassessmentMatrix | ForEach-Object { [string]$_.requirement_id }) @($expectedReassessmentRequirements | ForEach-Object { [string]$_.id }) 'dos IDs da matriz V02'
foreach ($row in $reassessmentMatrix) {
    foreach ($column in @('requirement_id', 'dimension', 'requirement', 'status', 'origin', 'limit', 'counterexample', 'fixture_id', 'test_assertion', 'decision')) {
        if ([string]::IsNullOrWhiteSpace([string]$row.$column)) {
            throw "A linha V02 $($row.requirement_id) possui coluna obrigatória vazia: $column."
        }
    }
    $requirement = $reassessmentRequirementById[[string]$row.requirement_id]
    if ($null -eq $requirement) { throw "A matriz V02 contém requisito desconhecido: $($row.requirement_id)." }
    Assert-ExactValue $row.dimension $requirement.dimension "da dimensão da matriz $($row.requirement_id)"
    Assert-ExactValue $row.requirement $requirement.requirement "do requisito da matriz $($row.requirement_id)"
    Assert-ExactValue $row.status $requirement.status "do estado da matriz $($row.requirement_id)"
    Assert-ExactValue $row.test_assertion $requirement.testAssertion "do teste da matriz $($row.requirement_id)"
    Assert-ExactValue $row.decision 'BLOCKED' "da decisão da matriz $($row.requirement_id)"
    if ([string]$row.origin -cnotin @($requirement.origins) -or
        [string]$row.fixture_id -cnotin @($requirement.fixtureIds) -or
        -not $reassessmentFixtureById.ContainsKey([string]$row.fixture_id)) {
        throw "A matriz V02 não vincula origem/fixture fechadas em $($row.requirement_id)."
    }
}

$reassessmentDimensions = @($reassessment.dimensions)
if ($reassessmentDimensions.Count -ne 2) { throw 'A reavaliação V02 deve conter exatamente duas dimensões.' }
$reassessmentVehicles = @($reassessmentDimensions | Where-Object { [string]$_.name -ceq 'VEICULOS' })
$reassessmentDrivers = @($reassessmentDimensions | Where-Object { [string]$_.name -ceq 'MOTORISTAS' })
if ($reassessmentVehicles.Count -ne 1 -or $reassessmentDrivers.Count -ne 1) {
    throw 'A reavaliação V02 não contém exatamente Veículos e Motoristas.'
}
Assert-ExactValue $reassessmentVehicles[0].status 'BLOCKED' 'do resultado V02 de Veículos'
Assert-ExactValue $reassessmentDrivers[0].status 'BLOCKED' 'do resultado V02 de Motoristas'
Assert-ExactSet @($reassessmentVehicles[0].satisfiedLimitedRequirements) @('FM2-VEH-005') 'da prova limitada de Veículos'
Assert-ExactSet @($reassessmentVehicles[0].missingRequirements) @('FM2-VEH-001', 'FM2-VEH-002', 'FM2-VEH-003', 'FM2-VEH-004') 'das lacunas de Veículos'
Assert-ExactSet @($reassessmentDrivers[0].satisfiedLimitedRequirements) @() 'das provas limitadas de Motoristas'
Assert-ExactSet @($reassessmentDrivers[0].missingRequirements) @('FM2-DRV-001', 'FM2-DRV-002', 'FM2-DRV-003', 'FM2-DRV-004', 'FM2-DRV-005') 'das lacunas de Motoristas'
if (@($reassessmentVehicles[0].unblockEvidence).Count -ne 5 -or @($reassessmentDrivers[0].unblockEvidence).Count -ne 5) {
    throw 'A reavaliação V02 não declara cinco evidências cumulativas de desbloqueio por dimensão.'
}

foreach ($booleanProperty in @('newDecisionSliceCreated', 'newFunctionalBlockAssigned')) {
    if ($reassessment.routeDecision.$booleanProperty -isnot [bool] -or [bool]$reassessment.routeDecision.$booleanProperty) {
        throw "A decisão de rota V02 não pode marcar $booleanProperty."
    }
}
if ([int]$reassessment.routeDecision.nowRouteCount -ne 0) { throw 'A reavaliação V02 não pode criar rota AGORA.' }
Assert-ExactValue $reassessment.routeDecision.d03 'CANDIDATE_BLOCKED_NOT_PROMOTED' 'do estado D03 V02'
Assert-ExactValue $reassessment.routeDecision.d03Hold 'V2-035c/REASSESSMENT_V02/VEICULOS=BLOCKED' 'do hold D03 V02'
Assert-ExactValue $reassessment.routeDecision.d04 'CANDIDATE_BLOCKED_NOT_PROMOTED' 'do estado D04 V02'
Assert-ExactValue $reassessment.routeDecision.d04Hold 'V2-035c/REASSESSMENT_V02/MOTORISTAS=BLOCKED' 'do hold D04 V02'
Assert-ExactValue $reassessment.routeDecision.v2035b 'OPEN' 'do pai V2-035b V02'
Assert-ExactValue $reassessment.routeDecision.v2035 'OPEN' 'do pai V2-035 V02'
Assert-ExactValue $reassessment.routeDecision.nextFunctionalBlock 'UNASSIGNED_39' 'do próximo bloco V02'
Assert-ExactValue $reassessment.routeDecision.terraRunbook 'MUST_NOT_EXIST' 'do runbook Terra V02'
Assert-ExactValue $reassessment.routeDecision.implementation 'NOT_STARTED' 'da implementação V02'
Assert-ExactValue $reassessment.routeDecision.migration 'NOT_CREATED' 'da migration V02'
Assert-ExactValue $reassessment.routeDecision.relationship 'NOT_CREATED' 'da relação V02'
Assert-ExactValue $reassessment.validationContract.validator 'scripts/validation/Test-FrotaManifestosV2035cDecisionCatalog.ps1' 'do validator V02'
Assert-ExactSet @($reassessment.validationContract.assertions) @(
    'STRICT_JSON_AND_NO_DUPLICATE_PROPERTIES', 'PRIOR_DECISION_AND_ALL_V01_EVIDENCE_HASHES_MATCH',
    'NO_NEW_QUALIFYING_TECHNICAL_EVIDENCE', 'EXACT_REQUIREMENT_STATUS_GAPS_LIMITS_COUNTEREXAMPLES_FIXTURES_AND_TESTS',
    'VEHICLES_AND_DRIVERS_REMAIN_BLOCKED', 'ONLY_MAIN_AND_TRAILER_ROLE_SEPARATION_IS_PROVEN_LIMITED',
    'SYNTHETIC_FIXTURES_AND_HASHES_MATCH', 'NO_NEW_SLICE_BLOCK_ROUTE_MIGRATION_RELATION_IMPLEMENTATION_OR_TERRA_PROMPT'
) 'das asserções V02'

if (Test-Path -LiteralPath $terraRunbookPath) {
    throw 'O runbook Terra D03 existe apesar da reavaliação V02 manter VEICULOS=BLOCKED.'
}
if ($readmeText -cnotmatch 'reavaliação V02' -or
    $readmeText -cnotmatch '\| Veículos \| ID imutável do fornecedor com source e tenant explícitos \| `MISSING` \|' -or
    $readmeText -cnotmatch '\| Veículos \| Principal separado de reboque 1 e reboque 2 \| `PROVEN_LIMITED` \|' -or
    $readmeText -cnotmatch '\| Motoristas \| Homônimos, genéricos, renomeação, merge e split \| `MISSING` \|' -or
    $adrText -cnotmatch '## Reavaliação autorizada V02' -or
    $adrText -cnotmatch 'ID imutável do fornecedor ligado a source e tenant' -or
    $stateText -cnotmatch '<code>COMPLETE_LOCAL_REASSESSMENT_BLOCKED</code>' -or
    $stateText -cnotmatch 'não se cria fatia nova sob V2-035, Bloco 39, migration, relação, implementação ou prompt Terra') {
    throw 'README, ADR ou STATES.md não espelham integralmente a reavaliação V02.'
}

Write-Output 'PASS: decisão V2-035c e reavaliação V02 validadas; VEICULOS=BLOCKED e MOTORISTAS=BLOCKED, 25 regras V01, 11 requisitos/13 fixtures V02, hashes/origens/limites/contraexemplos/testes coerentes; somente separação principal/reboques é PROVEN_LIMITED; zero nova fatia/rota/bloco/migration/relação, runbook Terra ausente e implementação NOT_STARTED.'

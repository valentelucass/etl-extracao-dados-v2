#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar
$identityManifestPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-cotacoes\manifesto.json'
$contractManifestPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6906\manifesto.json'
$maximumManifestBytes = 256KB
$maximumFixtureBytes = 64KB

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) {
        throw "O artefato '$($item.Name)' excede o limite de bytes."
    }
    return $utf8.GetString([System.IO.File]::ReadAllBytes($item.FullName))
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory)][object[]]$Actual,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualValues = @($Actual | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $expectedValues = @($Expected | Sort-Object -Unique)
    if ($actualValues.Count -ne $expectedValues.Count -or
        (Compare-Object -ReferenceObject $expectedValues -DifferenceObject $actualValues)) {
        throw "O conjunto $Label diverge da decisão de identidade 6906."
    }
}

function Assert-ExactSequence {
    param(
        [Parameter(Mandatory)][object[]]$Actual,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    if ((@($Actual | ForEach-Object { [string]$_ }) -join '|') -cne ($Expected -join '|')) {
        throw "A sequência $Label diverge da decisão de identidade 6906."
    }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)

    return [Convert]::ToHexString(
        [System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))
    ).ToLowerInvariant()
}

function Read-Json {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [Parameter(Mandatory)][string]$Label
    )

    $text = Read-StrictUtf8 -LiteralPath $Path -MaximumBytes $MaximumBytes
    try {
        return [pscustomobject]@{
            Text = $text
            Value = $text | ConvertFrom-Json -Depth 64
        }
    } catch {
        throw "O JSON $Label é inválido."
    }
}

$identityJson = Read-Json -Path $identityManifestPath -MaximumBytes $maximumManifestBytes -Label 'de identidade 6906'
$contractJson = Read-Json -Path $contractManifestPath -MaximumBytes $maximumManifestBytes -Label 'do contrato 6906'
$manifest = $identityJson.Value
$contract = $contractJson.Value.contract

$expectedEvidence = @(
    'HISTORICAL_SANITIZED_OBSERVATION',
    'LEGACY_STATIC_EVIDENCE',
    'SYNTHETIC_FIXTURES',
    'NO_TEMPORAL_OR_TENANT_PAYLOAD_PROOF',
    'NO_INDEPENDENT_COMPLETENESS_EVIDENCE'
)
$expectedReasons = @(
    'CARDINALITY_DIVERGENCE',
    'INVALID_SOURCE_KEY_TYPE',
    'INVALID_SOURCE_KEY_VALUE',
    'MISSING_SOURCE_KEY',
    'SCOPE_MISMATCH',
    'SOURCE_KEY_COLLISION',
    'SOURCE_KEY_TOO_LONG',
    'UNPROVEN_REKEY'
)
$expectedActions = @(
    'BLOCK_PROMOTION_PENDING_VERTICAL_RESOLUTION',
    'KEEP_CANONICAL',
    'NO_OP_REPLAY',
    'QUARANTINE',
    'REGISTER_NEW_CANONICAL',
    'VERSION_ALIASES_PRESERVE_CANONICAL'
)

if ([string]$manifest.catalogVersion -cne '2026-09-04.v2-009b.1' -or
    [string]$manifest.identityFingerprintVersion -cne 'quotes-identity-v1') {
    throw 'A versão do catálogo de identidade 6906 diverge da baseline.'
}
Assert-ExactSet -Actual @($manifest.evidenceVocabulary) -Expected $expectedEvidence -Label 'de evidências'
Assert-ExactSet -Actual @($manifest.identity.evidence) -Expected $expectedEvidence -Label 'da entidade'
Assert-ExactSet -Actual @($manifest.quarantineReasons) -Expected $expectedReasons -Label 'de motivos de quarentena'
Assert-ExactSet -Actual @($manifest.actions) -Expected $expectedActions -Label 'de ações'
Assert-ExactSequence -Actual @($manifest.registry.tuple) `
    -Expected @('source_instance', 'tenant_scope', 'entity', 'source_key') -Label 'da tuple de registry'

if ([string]$manifest.sourceFamily.sourceKind -cne 'ESL' -or
    [string]$manifest.sourceFamily.transport -cne 'DATA_EXPORT' -or
    [string]$manifest.registry.comparison -cne 'CASE_SENSITIVE_EXACT_BIN2' -or
    [string]$manifest.registry.canonicalIdStrategy -cne 'SQL_SURROGATE_BIGINT_IDENTITY' -or
    [bool]$manifest.registry.recordStateIdIsCanonicalId -or
    [string]$manifest.registry.physicalEnforcementGate -cne 'V2-009d') {
    throw 'O contrato de registry/canonical ID 6906 diverge da política aprovada.'
}

$expectedSchemas = @{ registry = 'core'; aliases = 'ref'; observations = 'stg'; conflicts = 'recon' }
foreach ($schema in $expectedSchemas.GetEnumerator()) {
    if ([string]$manifest.registry.physicalSchemas.($schema.Key) -cne $schema.Value) {
        throw 'Um objeto de identidade 6906 foi direcionado a schema não aprovado.'
    }
}

if ([string]$manifest.scope.policy -cne 'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED' -or
    [bool]$manifest.scope.globalUniquenessProven -or
    $null -ne $manifest.scope.tenantPayloadPath -or
    [string]$manifest.scope.tenantDerivation -cne
        'EXTERNAL_CONFIGURATION_REQUIRED_NO_BUSINESS_FIELD_DERIVATION') {
    throw 'O catálogo inventa tenant de payload, escopo implícito ou unicidade global.'
}
Assert-ExactSet -Actual @($manifest.scope.forbiddenTenantSentinels) `
    -Expected @('DEFAULT', 'GLOBAL', 'SINGLETON') -Label 'de sentinels de tenant proibidos'

if ([int]$manifest.canonicalization.maximumStorageCharacters -ne 256 -or
    [string]$manifest.canonicalization.integerEncoding -cne 'INTEGER:<CANONICAL_DECIMAL>' -or
    [bool]$manifest.canonicalization.integerAndStringEquivalent -or
    [string]$manifest.canonicalization.textNormalization -cne
        'NONE_FAIL_ON_BLANK_BOUNDARY_SPACE_CONTROL_OR_INVALID_UNICODE' -or
    [string]$manifest.canonicalization.processingBound -cne 'ONE_OBSERVATION_AT_A_TIME') {
    throw 'O codec da source key 6906 não preserva os limites aprovados.'
}
Assert-ExactSet -Actual @($manifest.canonicalization.acceptedWireTypes) -Expected @('INTEGER') `
    -Label 'de tipos do codec'

if ([string]$contract.contractId -cne 'dataexport-6906' -or
    [string]$contract.contractVersion -cne '2026-09-04.v2-025b.1' -or
    [string]$contract.fingerprints.release -cne
        '4bd641aa5ebe1c069776dc7009377d43c4c1d3e26727969667cc7adc01071166' -or
    [string]$contract.aspects.rootIdentity.classification -cne 'BUSINESS_DECISION_PENDING' -or
    [string]$contract.aspects.childIdentity.classification -cne 'ABSENT') {
    throw 'O catálogo 6906 não está ligado ao contrato offline que esta decisão encerra.'
}

$identity = $manifest.identity
if ([string]$identity.entity -cne 'cotacoes' -or
    [string]$identity.contractId -cne 'dataexport-6906' -or
    [string]$identity.contractVersion -cne '2026-09-04.v2-025b.1' -or
    [string]$identity.sourceContractReleaseFingerprint -cne [string]$contract.fingerprints.release -or
    [string]$identity.recordRoot -cne '/data/*' -or
    [string]$identity.sourceKey.path -cne '/sequence_code' -or
    [string]$identity.sourceKey.name -cne 'sequence_code' -or
    [string]$identity.sourceKey.role -cne 'SCOPED_SOURCE_KEY_NEVER_CANONICAL_ID' -or
    [string]$identity.sourceKey.decision -cne 'ACCEPTED_FOR_SCOPED_LOGICAL_ROOT_ONLY') {
    throw 'A source key ou a raiz de Cotações diverge da decisão V2-009b.'
}
Assert-ExactSet -Actual @($identity.sourceKey.wireTypes) -Expected @('INTEGER') -Label 'de tipos da source key'

if ($null -ne $identity.businessAlias.path -or $null -ne $identity.businessAlias.name -or
    [string]$identity.businessAlias.policy -cne 'ABSENT_NO_ALIAS_OR_CROSSWALK_PROVEN' -or
    [string]$identity.businessAlias.cardinality -cne 'NOT_APPLICABLE' -or
    [string]$identity.rootGrain.logical -cne 'ONE_LOGICAL_QUOTE_PER_SCOPED_SEQUENCE_CODE' -or
    [string]$identity.rootGrain.childOrExpansion -cne 'NO_CHILD_OR_EXPANSION_PROVEN') {
    throw 'O catálogo infere alias, crosswalk, filho ou grão físico não provado.'
}

if ([string]$identity.proof.uniqueness -cne 'LIMITED_LEGACY_PK_AND_ONE_SANITIZED_WINDOW_ONLY_NOT_GLOBAL' -or
    [string]$identity.proof.temporalStability -cne
        'UNPROVEN_NO_VERSIONED_PROVIDER_GUARANTEE_OR_REPEAT_OBSERVATION' -or
    [string]$identity.proof.tenantScope -cne 'UNPROVEN_IN_PAYLOAD_EXPLICIT_EXTERNAL_SCOPE_REQUIRED' -or
    [string]$identity.proof.cardinality -cne 'LIMITED_ONE_TO_ONE_OBSERVED_WINDOW_ONLY' -or
    [string]$identity.repeatAndConflictPolicy.sameScopedSourceKey -cne
        'SAME_LOGICAL_ROOT_NEVER_A_SECOND_CANONICAL_ROOT' -or
    [string]$identity.repeatAndConflictPolicy.divergentRepeatedRow -cne
        'BLOCK_PROMOTION_PENDING_V2_027_FRESHNESS_AND_REDUCER_RESOLUTION' -or
    [string]$identity.repeatAndConflictPolicy.alternateKeyOrRekey -cne
        'QUARANTINE_UNPROVEN_REKEY_UNTIL_VERSIONED_EVIDENCE' -or
    [string]$identity.repeatAndConflictPolicy.missingOrInvalidScopeOrKey -cne 'QUARANTINE') {
    throw 'O catálogo de Cotações amplia a evidência de unicidade, estabilidade, scope ou replay.'
}

if ([string]$identity.capabilities.shadowDomainImplementation -cne
        'IDENTITY_INPUT_ONLY_REQUIRES_V2_027_AND_V2_009D' -or
    [string]$identity.capabilities.sweepOrDeactivation -cne 'BLOCKED_NO_COMPLETENESS_PROOF' -or
    [string]$identity.capabilities.cutover -cne 'BLOCKED_NO_COMPLETENESS_PROOF') {
    throw 'O catálogo de identidade libera implementação, sweep ou cutover prematuramente.'
}
Assert-ExactSet -Actual @($identity.nextGates) -Expected @('V2-027', 'V2-009d', 'V2-025d') `
    -Label 'de próximos gates'

$fixturesByRole = @{}
$requiredRoles = @(
    'METADATA_SELECTED_SUBSET', 'PER_2_PAGE_1', 'PER_2_PAGE_2', 'PER_2_TERMINAL',
    'PER_3_PAGE_1', 'PER_3_TERMINAL'
)
Assert-ExactSet -Actual @($contract.fixtures.role) -Expected $requiredRoles -Label 'de fixtures do contrato 6906'
foreach ($fixture in @($contract.fixtures)) {
    $relativePath = ([string]$fixture.path).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $relativePath))
    if (-not $resolvedPath.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Uma fixture do contrato 6906 sai do repositório autorizado.'
    }
    $fixtureJson = Read-Json -Path $resolvedPath -MaximumBytes $maximumFixtureBytes -Label 'de fixture 6906'
    $actualHash = (Get-FileHash -LiteralPath $resolvedPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -cne [string]$fixture.sha256) {
        throw 'Uma fixture 6906 diverge do SHA-256 declarado no contrato.'
    }
    $fixturesByRole[[string]$fixture.role] = $fixtureJson.Value
}

$perThreeRows = @($fixturesByRole['PER_3_PAGE_1'].data)
$perThreeKeys = @($perThreeRows | ForEach-Object { $_.sequence_code } | Sort-Object -Unique)
if ($perThreeRows.Count -ne 3 -or $perThreeKeys.Count -ne 3 -or
    @($perThreeRows | Where-Object {
        $_.sequence_code -isnot [long] -or $_.PSObject.Properties.Name -contains 'id'
    }).Count -ne 0) {
    throw 'A fixture per=3 não preserva três candidatos inteiros, distintos e sem id.'
}

$perTwoRows = @($fixturesByRole['PER_2_PAGE_1'].data) + @($fixturesByRole['PER_2_PAGE_2'].data)
$perTwoKeys = @($perTwoRows | ForEach-Object { $_.sequence_code } | Sort-Object -Unique)
if ($perTwoRows.Count -ne 3 -or $perTwoKeys.Count -ne 3 -or
    @($fixturesByRole['PER_2_TERMINAL'].data).Count -ne 0 -or
    @($fixturesByRole['PER_3_TERMINAL'].data).Count -ne 0 -or
    (Compare-Object -ReferenceObject $perThreeKeys -DifferenceObject $perTwoKeys)) {
    throw 'As fixtures sintéticas não preservam a mesma cardinalidade observada e terminal local.'
}

if ([string]$identity.fingerprints.identity -cne
        (Get-Utf8Sha256 -Value ([string]$identity.fingerprintInputs.identity))) {
    throw 'O fingerprint de identidade 6906 não corresponde ao material UTF-8 declarado.'
}
$releaseMaterial = ([string]$identity.fingerprintInputs.release) + '|' +
    ([string]$identity.fingerprints.identity) + '|' +
    ([string]$identity.sourceContractReleaseFingerprint)
if ([string]$identity.fingerprints.release -cne (Get-Utf8Sha256 -Value $releaseMaterial)) {
    throw 'O fingerprint de release da identidade 6906 não corresponde aos componentes.'
}

if ($identityJson.Text -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|https?://|real[-_ ]?(?:id|cursor|document)') {
    throw 'O manifesto de identidade contém marcador incompatível com evidência sanitizada.'
}

Write-Output 'PASS: identidade 6906 validada com source key INTEGER escopada, três raízes sintéticas distintas e limites explícitos para tenant, estabilidade, expansão, sweep e cutover.'

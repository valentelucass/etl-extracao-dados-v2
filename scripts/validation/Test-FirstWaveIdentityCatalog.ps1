[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-primeira-onda\manifesto.json'
$maximumManifestBytes = 512KB

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
        throw "O conjunto $Label diverge da baseline fechada."
    }
}

function Assert-ExactSequence {
    param(
        [Parameter(Mandatory)][object[]]$Actual,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    if ((@($Actual | ForEach-Object { [string]$_ }) -join '|') -cne ($Expected -join '|')) {
        throw "A sequência $Label diverge da baseline fechada."
    }
}

$manifestItem = Get-Item -LiteralPath $manifestPath -ErrorAction Stop
if ($manifestItem.Length -gt $maximumManifestBytes) {
    throw 'O manifesto de identidade excede o limite de bytes.'
}
$manifestText = $utf8.GetString([System.IO.File]::ReadAllBytes($manifestItem.FullName))
try {
    $manifest = $manifestText | ConvertFrom-Json -Depth 64
} catch {
    throw 'O manifesto de identidade não é JSON válido.'
}

if ([string]$manifest.catalogVersion -cne '2026-09-01.v2-009a.2' -or
    [string]$manifest.identityFingerprintVersion -cne 'first-wave-identity-v1') {
    throw 'A versão do catálogo de identidade diverge da baseline.'
}
if ([string]$manifest.sourceFamily.sourceKind -cne 'ESL' -or
    [string]$manifest.sourceFamily.sourceInstanceMeaning -cne
        'STABLE_NON_SECRET_LOGICAL_ESL_ACCOUNT_SHARED_BY_TRANSPORTS') {
    throw 'A família lógica da source instance não está fechada.'
}
Assert-ExactSet -Actual @($manifest.sourceFamily.transports) `
    -Expected @('DATA_EXPORT', 'GRAPHQL') -Label 'de transportes'
Assert-ExactSequence -Actual @($manifest.registry.tuple) `
    -Expected @('source_instance', 'tenant_scope', 'entity', 'source_key') `
    -Label 'da tuple de registry'
if ([string]$manifest.registry.comparison -cne 'CASE_SENSITIVE_EXACT_BIN2' -or
    [string]$manifest.registry.canonicalIdStrategy -cne 'SQL_SURROGATE_BIGINT_IDENTITY' -or
    [bool]$manifest.registry.recordStateIdIsCanonicalId -or
    [string]$manifest.registry.physicalEnforcementGate -cne 'V2-009d') {
    throw 'O contrato de registry/canonical ID diverge da decisão V2-009a.'
}
$expectedSchemas = @{
    registry = 'core'
    aliases = 'ref'
    observations = 'stg'
    conflicts = 'recon'
}
foreach ($schema in $expectedSchemas.GetEnumerator()) {
    if ([string]$manifest.registry.physicalSchemas.($schema.Key) -cne $schema.Value) {
        throw 'Um objeto de identidade foi direcionado a schema não aprovado.'
    }
}
if ([string]$manifest.scope.policy -cne 'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED' -or
    [bool]$manifest.scope.globalUniquenessProven) {
    throw 'O catálogo inventa scope implícito ou unicidade global.'
}
Assert-ExactSet -Actual @($manifest.scope.forbiddenTenantSentinels) `
    -Expected @('DEFAULT', 'GLOBAL', 'SINGLETON') -Label 'de sentinels proibidos'
if ([int]$manifest.canonicalization.maximumStorageCharacters -ne 256 -or
    [string]$manifest.canonicalization.integerEncoding -cne 'INTEGER:<CANONICAL_DECIMAL>' -or
    [string]$manifest.canonicalization.stringEncoding -cne 'STRING:<EXACT_TEXT>' -or
    [bool]$manifest.canonicalization.integerAndStringEquivalent -or
    [string]$manifest.canonicalization.processingBound -cne 'ONE_OBSERVATION_AT_A_TIME') {
    throw 'O codec de source key não está fechado ou colapsa wire types.'
}

$expectedReasons = @(
    'AMBIGUOUS_BUSINESS_ALIAS',
    'CARDINALITY_DIVERGENCE',
    'INVALID_SOURCE_KEY_TYPE',
    'INVALID_SOURCE_KEY_VALUE',
    'MISSING_SOURCE_KEY',
    'SCOPE_MISMATCH',
    'SOURCE_KEY_COLLISION',
    'SOURCE_KEY_TOO_LONG',
    'UNPROVEN_ALIAS_CHANGE',
    'UNPROVEN_REKEY'
)
$expectedActions = @(
    'KEEP_CANONICAL',
    'NO_OP_REPLAY',
    'QUARANTINE',
    'REACTIVATE_EXACT_BINDING',
    'REGISTER_NEW_CANONICAL',
    'VERSION_ALIASES_PRESERVE_CANONICAL'
)
Assert-ExactSet -Actual @($manifest.quarantineReasons) -Expected $expectedReasons `
    -Label 'de motivos de quarentena'
Assert-ExactSet -Actual @($manifest.actions) -Expected $expectedActions -Label 'de ações'

$expected = @{
    coletas = @{
        contractId = 'dataexport-6908'
        sourceKind = 'DATA_EXPORT'
        documentReference = 'dataexport-coletas'
        sourceContractFingerprint = '052d84c37d9c55ae2b2b4891eb4771c68158ee7365402a1388cc68256c67cc59'
        identityFingerprint = '0ca2cd5ce2573d0bdec31d9bc141887f2d58bc79cd67d289981034a4865705e1'
        recordRoot = '/data'
        sourceKeyPath = '/id'
        sourceKeyName = 'id'
        wireTypes = @('INTEGER')
        aliasPath = '/sequence_code'
        aliasName = 'sequence_code'
        aliasPolicy = 'VERSIONED_NON_TECHNICAL'
        aliasCardinality = 'OBSERVED_ONE_TO_ONE_NOT_GLOBAL'
        rootCardinality = 'LOGICAL_ROOT_MAY_EXPAND_TO_REPEATED_PHYSICAL_ROWS'
        nextGates = @('V2-010', 'V2-025d', 'V2-009d')
    }
    fretes = @{
        contractId = 'dataexport-6389'
        sourceKind = 'DATA_EXPORT'
        documentReference = 'dataexport-fretes'
        sourceContractFingerprint = '23aef4e4e03488d291d3990bdba813800e3ebb8c8be18e766a788daf9741ec15'
        identityFingerprint = '4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723'
        recordRoot = '/data'
        sourceKeyPath = '/id'
        sourceKeyName = 'id'
        wireTypes = @('INTEGER')
        aliasPath = '/corporation_sequence_number'
        aliasName = 'corporation_sequence_number'
        aliasPolicy = 'VERSIONED_NON_TECHNICAL'
        aliasCardinality = 'ZERO_TO_MANY_LOOKUP_AMBIGUITY_BLOCKS_RESOLUTION'
        rootCardinality = 'LOGICAL_ROOT_MAY_EXPAND_TO_REPEATED_PHYSICAL_ROWS'
        nextGates = @('V2-011', 'V2-025d', 'V2-009d')
    }
    usuarios = @{
        contractId = 'graphql-individual'
        sourceKind = 'GRAPHQL'
        documentReference = 'graphql-users-snapshot'
        sourceContractFingerprint = 'a650e5e8313e6d45bff326dd8ab15920ba23da8e44e9237f538c4cbce76aca7c'
        identityFingerprint = '8e8ad89777277f3b8bf72b4b63f65d8fa813843380f44da99f0b035bbf788528'
        recordRoot = '/data/individual/edges/*'
        sourceKeyPath = '/node/id'
        sourceKeyName = 'user_id'
        wireTypes = @('STRING', 'INTEGER')
        aliasPath = $null
        aliasName = $null
        aliasPolicy = 'ABSENT'
        aliasCardinality = 'NOT_APPLICABLE'
        rootCardinality = 'ONE_NODE_PER_OBSERVED_EDGE'
        nextGates = @('V2-012a')
    }
}
Assert-ExactSet -Actual @($manifest.entities.entity) -Expected @($expected.Keys) `
    -Label 'de entidades'
foreach ($entity in @($manifest.entities)) {
    $entityName = [string]$entity.entity
    $contract = $expected[$entityName]
    foreach ($field in @(
        'contractId', 'sourceKind', 'documentReference', 'sourceContractFingerprint',
        'identityFingerprint', 'recordRoot', 'rootCardinality'
    )) {
        if ([string]$entity.$field -cne [string]$contract[$field]) {
            throw "A matriz $entityName diverge no campo $field."
        }
    }
    if ([string]$entity.contractVersion -cne '2026-08-31.v2-025a.1' -or
        [string]$entity.sourceKey.path -cne $contract.sourceKeyPath -or
        [string]$entity.sourceKey.name -cne $contract.sourceKeyName -or
        [string]$entity.sourceKey.role -cne 'SCOPED_SOURCE_KEY_NEVER_CANONICAL_ID' -or
        [string]$entity.businessAlias.policy -cne $contract.aliasPolicy -or
        [string]$entity.businessAlias.cardinality -cne $contract.aliasCardinality -or
        [string]$entity.evidenceScope -cne
            'CLOSED_HISTORICAL_WINDOWS_AND_SYNTHETIC_FIXTURES') {
        throw "A matriz $entityName diverge do contrato de identidade."
    }
    if ([string]$entity.businessAlias.path -cne [string]$contract.aliasPath -or
        [string]$entity.businessAlias.name -cne [string]$contract.aliasName) {
        throw "O alias da matriz $entityName diverge da baseline."
    }
    Assert-ExactSet -Actual @($entity.sourceKey.wireTypes) -Expected $contract.wireTypes `
        -Label "de wire types de $entityName"
    Assert-ExactSet -Actual @($entity.nextGates) -Expected $contract.nextGates `
        -Label "de próximos gates de $entityName"
    foreach ($hash in @($entity.sourceContractFingerprint, $entity.identityFingerprint)) {
        if ([string]$hash -cnotmatch '^[0-9a-f]{64}$') {
            throw "A matriz $entityName contém fingerprint inválido."
        }
    }
}

if ($manifestText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|https?://|real[-_ ]?(?:id|cursor|document)') {
    throw 'O manifesto contém marcador incompatível com evidência sanitizada.'
}

Write-Output 'PASS: 3 matrizes V2-009a, 10 motivos e 6 ações validados; zero scope global ou wire type colapsado.'

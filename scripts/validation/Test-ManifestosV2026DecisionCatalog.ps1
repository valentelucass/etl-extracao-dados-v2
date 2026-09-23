#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$legacyRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '..\etl-extracao-dados'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$legacyPrefix = $legacyRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$allowedPrefixes = @($repositoryPrefix, $legacyPrefix)

$catalogPath = Join-Path $repositoryRoot 'docs\catalogos\manifestos-v2-026\decisao-v03.json'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0024-manifestos-6399-identidade-presenca-frescor-reducers.md'
$identityPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-manifestos\manifesto.json'
$contractPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6399\manifesto.json'
$matrixPath = Join-Path $repositoryRoot 'docs\catalogos\portabilidade\matriz-campos.csv'

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

function Assert-NoDuplicateJsonProperties {
    param([Parameter(Mandatory)][string]$Text)

    $document = [System.Text.Json.JsonDocument]::Parse($Text)
    try {
        Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$'
    } finally {
        $document.Dispose()
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
        Assert-NoDuplicateJsonProperties -Text $text
        $value = $text | ConvertFrom-Json -Depth 100
    } catch {
        throw "O JSON $Label é inválido ou contém propriedade duplicada."
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

function Assert-ExactSet {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        if ($item -isnot [string]) { throw "O conjunto $Label contém item que não é string." }
        if (-not $actualSet.Add($item)) { throw "O conjunto $Label contém duplicata exata." }
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Expected)) {
        if ($item -isnot [string]) { throw "A baseline $Label contém item que não é string." }
        if (-not $expectedSet.Add($item)) { throw "A baseline $Label contém duplicata exata." }
    }
    if ($actualSet.Count -ne $expectedSet.Count) { throw "O conjunto $Label diverge da decisão V03." }
    foreach ($item in $expectedSet) {
        if (-not $actualSet.Contains($item)) { throw "O conjunto $Label diverge da decisão V03." }
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
    if ($actualItems.Count -ne $expectedItems.Count) { throw "A sequência $Label diverge da decisão V03." }
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
        foreach ($property in $Properties) { Assert-RequiredProperty -Object $row -Name $property -Label $Label }
        ($Properties | ForEach-Object { [string]$row.$_ }) -join [char]0x1F
    }
    $expectedKeys = foreach ($row in @($Expected)) {
        ($Properties | ForEach-Object { [string]$row.$_ }) -join [char]0x1F
    }
    Assert-ExactSet -Actual @($actualKeys) -Expected @($expectedKeys) -Label $Label
}

function Convert-JsonElementToCanonicalText {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Path
    )

    switch ($Element.ValueKind) {
        ([System.Text.Json.JsonValueKind]::Object) {
            $map = [System.Collections.Generic.Dictionary[string, System.Text.Json.JsonElement]]::new([System.StringComparer]::Ordinal)
            foreach ($property in $Element.EnumerateObject()) {
                if ($Path -ceq '$.identity' -and ($property.Name -ceq 'fingerprintInputs' -or $property.Name -ceq 'fingerprints')) {
                    continue
                }
                if (-not $map.TryAdd($property.Name, $property.Value)) {
                    throw "O JSON contém propriedade duplicada ordinal em $Path."
                }
            }
            [string[]]$names = @($map.Keys)
            [System.Array]::Sort($names, [System.StringComparer]::Ordinal)
            $parts = foreach ($name in $names) {
                $encodedName = [System.Text.Json.JsonEncodedText]::Encode($name).ToString()
                '"' + $encodedName + '":' + (Convert-JsonElementToCanonicalText -Element $map[$name] -Path ($Path + '.' + $name))
            }
            return '{' + ($parts -join ',') + '}'
        }
        ([System.Text.Json.JsonValueKind]::Array) {
            $parts = foreach ($item in $Element.EnumerateArray()) {
                Convert-JsonElementToCanonicalText -Element $item -Path ($Path + '[]')
            }
            return '[' + ($parts -join ',') + ']'
        }
        ([System.Text.Json.JsonValueKind]::String) {
            return '"' + [System.Text.Json.JsonEncodedText]::Encode($Element.GetString()).ToString() + '"'
        }
        ([System.Text.Json.JsonValueKind]::Number) { return $Element.GetRawText() }
        ([System.Text.Json.JsonValueKind]::True) { return 'true' }
        ([System.Text.Json.JsonValueKind]::False) { return 'false' }
        ([System.Text.Json.JsonValueKind]::Null) { return 'null' }
        default { throw "Tipo JSON não suportado no fingerprint em $Path." }
    }
}

function Get-SemanticManifestText {
    param([Parameter(Mandatory)][string]$JsonText)

    $document = [System.Text.Json.JsonDocument]::Parse($JsonText)
    try {
        return Convert-JsonElementToCanonicalText -Element $document.RootElement -Path '$'
    } finally {
        $document.Dispose()
    }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)

    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))).ToLowerInvariant()
}

function Resolve-EvidencePath {
    param([Parameter(Mandatory)][string]$RelativePath)

    $platformPath = $RelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolvedPath = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $platformPath))
    $insideAllowedRoot = $false
    foreach ($prefix in $allowedPrefixes) {
        if ($resolvedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            $insideAllowedRoot = $true
            break
        }
    }
    if (-not $insideAllowedRoot -or -not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "A evidência '$RelativePath' está ausente ou fora dos repositórios permitidos."
    }
    return $resolvedPath
}

$catalogJson = Read-StrictJson -LiteralPath $catalogPath -MaximumBytes 512KB -Label 'da decisão V03'
$identityJson = Read-StrictJson -LiteralPath $identityPath -MaximumBytes 512KB -Label 'de identidade P01'
$contractJson = Read-StrictJson -LiteralPath $contractPath -MaximumBytes 512KB -Label 'do contrato 6399'
$adrText = Read-StrictUtf8 -LiteralPath $adrPath -MaximumBytes 256KB
$catalog = $catalogJson.Value
$identityCatalog = $identityJson.Value
$identity = $identityCatalog.identity
$contract = $contractJson.Value.contract

if ([string]$catalog.catalogVersion -cne '2026-09-04.v2-026a-v03.1' -or
    [string]$catalog.decision -cne 'MANIFESTOS_6399_ROOT_CHILD_PRESENCE_FRESHNESS_REDUCERS' -or
    [string]$catalog.decisionStatus -cne 'COMPLETE_LOCAL_DECISION_ONLY' -or
    [string]$catalog.scope -cne 'DECISION_ONLY_NO_DDL_NO_RUNTIME_NO_SOURCE_IO_NO_PUBLICATION' -or
    [string]$catalog.task -cne 'V2-026a' -or [string]$catalog.route -cne 'V03') {
    throw 'A identidade, o escopo ou o resultado da decisão V03 divergem.'
}

if ([string]$catalog.sourceContract.id -cne 'dataexport-6399' -or
    [string]$catalog.sourceContract.version -cne '2026-09-04.v2-025b.2' -or
    [string]$catalog.sourceContract.releaseFingerprint -cne '30181093f00f47507d1910d9806a97697118ab671aeae0598d21a4a2726a7cb8') {
    throw 'A decisão V03 não está vinculada à revisão e release exatas do contrato 6399.'
}
if ([string]$contract.contractId -cne [string]$catalog.sourceContract.id -or
    [string]$contract.contractVersion -cne [string]$catalog.sourceContract.version -or
    [string]$contract.fingerprints.release -cne [string]$catalog.sourceContract.releaseFingerprint) {
    throw 'O contrato 6399 materializado diverge do binding congelado por V03.'
}
foreach ($fingerprintName in @('semantics', 'metadata', 'response')) {
    $calculated = Get-Utf8Sha256 -Value ([string]$contract.fingerprintInputs.$fingerprintName)
    if ([string]$contract.fingerprints.$fingerprintName -cne $calculated) {
        throw "O fingerprint '$fingerprintName' do contrato 6399 não recompõe."
    }
}
$contractReleaseMaterial = ([string]$contract.fingerprintInputs.release) + '|' +
    ([string]$contract.fingerprints.semantics) + '|' +
    ([string]$contract.fingerprints.metadata) + '|' +
    ([string]$contract.fingerprints.response)
if ((Get-Utf8Sha256 -Value $contractReleaseMaterial) -cne [string]$catalog.sourceContract.releaseFingerprint) {
    throw 'O release fingerprint congelado do contrato 6399 não recompõe.'
}

if ([string]$catalog.identityDecision.task -cne 'V2-009b/6399' -or
    [string]$catalog.identityDecision.requiredStatus -cne 'COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED' -or
    [string]$catalog.identityDecision.requiredCatalogVersion -cne '2026-09-04.v2-009b.2' -or
    [string]$catalog.identityDecision.reference -cne 'docs/catalogos/identidade-manifestos/manifesto.json' -or
    [string]$catalog.identityDecision.requiredIdentityFingerprint -cne 'ad2e3457a91961372dd29cb96bd6f2cb5fd6f67614d1255303787a8e2c4085c3' -or
    [string]$catalog.identityDecision.requiredReleaseFingerprint -cne '12e2b402a6ae1c7c484a7f4af33c443d0ee5ceb99201ec6e3e4811d2d7e2e4f3') {
    throw 'O binding lógico e criptográfico de P01 diverge da decisão V03.'
}
if ([string]$identityCatalog.catalogVersion -cne [string]$catalog.identityDecision.requiredCatalogVersion -or
    [string]$identityCatalog.decisionStatus -cne [string]$catalog.identityDecision.requiredStatus -or
    [string]$identity.fingerprints.identity -cne [string]$catalog.identityDecision.requiredIdentityFingerprint -or
    [string]$identity.fingerprints.release -cne [string]$catalog.identityDecision.requiredReleaseFingerprint) {
    throw 'O catálogo P01 materializado diverge do binding exigido por V03.'
}
$semanticIdentityText = Get-SemanticManifestText -JsonText $identityJson.Text
$calculatedIdentityFingerprint = Get-Utf8Sha256 -Value (([string]$identity.fingerprintInputs.identity) + '|' + $semanticIdentityText)
$calculatedIdentityRelease = Get-Utf8Sha256 -Value (([string]$identity.fingerprintInputs.release) + '|' + $calculatedIdentityFingerprint + '|' + [string]$identity.sourceContractReleaseFingerprint)
if ($calculatedIdentityFingerprint -cne [string]$catalog.identityDecision.requiredIdentityFingerprint -or
    $calculatedIdentityRelease -cne [string]$catalog.identityDecision.requiredReleaseFingerprint -or
    [string]$identity.sourceContractReleaseFingerprint -cne [string]$catalog.sourceContract.releaseFingerprint) {
    throw 'Os fingerprints P01 de identidade/release não recompõem ou não apontam ao contrato congelado.'
}

if ([string]$identity.sourceKey.path -cne '/sequence_code') { throw 'P01 não preserva a source key raiz esperada.' }
$identityChildren = @($identity.physicalGrain.children)
$identityPick = @($identityChildren | Where-Object { [string]$_.name -ceq 'pick_expansion' })
$identityMdfe = @($identityChildren | Where-Object { [string]$_.name -ceq 'mdfe_expansion' })
if ($identityPick.Count -ne 1 -or [string]$identityPick[0].path -cne '/mft_pfs_pck_sequence_code' -or
    $identityMdfe.Count -ne 1 -or [string]$identityMdfe[0].path -cne '/mft_mfs_key') {
    throw 'Os paths de filho P01 divergem de V03.'
}
$mdfePair = @()
if ($identityMdfe[0].PSObject.Properties.Name -ccontains 'physicalPair') {
    $mdfePair = @($identityMdfe[0].physicalPair)
} elseif ($identityMdfe[0].PSObject.Properties.Name -ccontains 'sameRecordPhysicalPair') {
    $mdfePair = @($identityMdfe[0].sameRecordPhysicalPair)
} elseif ($identityMdfe[0].PSObject.Properties.Name -ccontains 'atomicBundle') {
    $mdfePair = @($identityMdfe[0].atomicBundle)
}
Assert-ExactSet -Actual $mdfePair -Expected @('/mft_mfs_number', '/mft_mfs_key') -Label 'do par físico MDF-e em P01'
$identityMdfeText = $identityMdfe[0] | ConvertTo-Json -Depth 30 -Compress
if ($identityMdfeText -cmatch 'atomicBundle[^\r\n]*mdfe_status' -or
    [string]$identityMdfe[0].statusRole -cnotmatch '^ROOT_SCALAR_REPLICATED_BY_EXPANSION' -or
    [string]$identityMdfe[0].statusRole -cnotmatch 'NEVER_CREATES_OR_IDENTIFIES_CHILD' -or
    [string]$identityMdfe[0].asymmetry -cnotmatch 'STATUS_ALONE_IS_NOT_ASYMMETRY' -or
    [string]$identityMdfe[0].collision -cmatch '(?i)status') {
    throw 'P01 ainda trata mdfe_status como sinal, bundle ou conflito canônico do filho.'
}
if ($identityJson.Text -cnotmatch 'ROOT_SCALAR_REPLICATED_BY_EXPANSION' -or
    $identityJson.Text -cnotmatch 'NEVER_CREATES_OR_IDENTIFIES_CHILD') {
    throw 'P01 não registra mdfe_status como escalar raiz replicado.'
}

Assert-ExactSet -Actual @($catalog.execution.allowedArtifactsInV03) -Expected @('DOCUMENTATION', 'LOCAL_VALIDATOR') -Label 'de artefatos permitidos em V03'
Assert-ExactSet -Actual @($catalog.execution.forbiddenArtifactsInV03) -Expected @(
    'JAVA_PRODUCTION_CODE', 'MIGRATION', 'SCHEMA', 'TABLE', 'PROCEDURE', 'VIEW', 'FACT', 'GRANT', 'INTEGRATION', 'RUNTIME'
) -Label 'de artefatos proibidos em V03'
if ([string]$catalog.execution.status -cne 'NOT_STARTED' -or [string]$catalog.execution.owner -cne 'V04' -or
    [string]$catalog.execution.parentTask -cne 'V2-026' -or [string]$catalog.execution.parentTaskStatus -cne 'OPEN') {
    throw 'V03 antecipou a execução V04 ou fechou o pai V2-026.'
}

$rootGrain = $catalog.logicalGrains.root
$pickGrain = $catalog.logicalGrains.pick
$mdfeGrain = $catalog.logicalGrains.mdfe
Assert-ExactSequence -Actual @($rootGrain.naturalComponents) -Expected @(
    'source_instance', 'tenant_scope', 'entity=manifestos', 'INTEGER:sequence_code'
) -Label 'da chave natural raiz'
Assert-ExactSequence -Actual @($pickGrain.naturalComponents) -Expected @(
    'root_canonical_id', 'INTEGER:mft_pfs_pck_sequence_code'
) -Label 'da chave natural pick'
Assert-ExactSequence -Actual @($mdfeGrain.naturalComponents) -Expected @(
    'root_canonical_id', 'STRING:mft_mfs_key'
) -Label 'da chave natural MDF-e'
if ([string]$rootGrain.sourcePath -cne '/sequence_code' -or
    [string]$pickGrain.sourcePath -cne '/mft_pfs_pck_sequence_code' -or
    [string]$mdfeGrain.sourcePath -cne '/mft_mfs_key' -or
    [string]$pickGrain.cardinality -cne 'ROOT_ZERO_TO_MANY_NO_PROVIDER_MAXIMUM' -or
    [string]$mdfeGrain.cardinality -cne 'ROOT_ZERO_TO_MANY_NO_PROVIDER_MAXIMUM' -or
    [string]$mdfeGrain.number.sourcePath -cne '/mft_mfs_number' -or
    [string]$mdfeGrain.number.role -cne 'POSITIVE_INTEGER_CORRELATED_ATTRIBUTE_NEVER_IDENTITY' -or
    [string]$mdfeGrain.mdfeStatusContext.rootRole -cne 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_NEVER_CHILD_SIGNAL' -or
    [string]$mdfeGrain.mdfeStatusContext.childContext -cne 'RAW_CONTEXT_PROVENANCE_ONLY_WHEN_VALID_MDFE_KEY_EXISTS_NEVER_CANONICAL_CHILD_ATTRIBUTE_OR_LIFECYCLE') {
    throw 'A separação de raiz, pick, MDF-e, número ou mdfe_status diverge da decisão V03.'
}
Assert-ExactSequence -Actual @($mdfeGrain.number.physicalPair) -Expected @('/mft_mfs_number', '/mft_mfs_key') -Label 'do par físico MDF-e'

$aggregate = $catalog.evidenceAggregates
if ($aggregate.physicalRows -ne 228 -or $aggregate.logicalRoots -ne 100 -or
    $aggregate.pick.valueRows -ne 193 -or $aggregate.pick.nullRows -ne 35 -or
    $aggregate.pick.distinctScopedKeys -ne 193 -or $aggregate.pick.repeatedScopedKeys -ne 0 -or
    $aggregate.pick.collisions -ne 0 -or $aggregate.pick.rootsWithValue -ne 67 -or
    $aggregate.pick.rootsWithMultiple -ne 42 -or $aggregate.pick.observedMaximumPerRoot -ne 10 -or
    [string]$aggregate.pick.contractMaximum -cne 'UNBOUNDED_ZERO_TO_MANY') {
    throw 'Os agregados de raiz/pick divergem do corpus estático versionado.'
}
if ($aggregate.mdfe.numberValueRows -ne 80 -or $aggregate.mdfe.numberNullRows -ne 148 -or
    $aggregate.mdfe.keyValueRows -ne 80 -or $aggregate.mdfe.keyNullRows -ne 148 -or
    $aggregate.mdfe.numberKeyAsymmetries -ne 0 -or $aggregate.mdfe.distinctScopedKeys -ne 43 -or
    $aggregate.mdfe.repeatedScopedKeyGroups -ne 16 -or $aggregate.mdfe.repeatedRowExcess -ne 37 -or
    $aggregate.mdfe.observedMaximumRepeatedRowsPerScopedKey -ne 7 -or
    $aggregate.mdfe.rootsWithValue -ne 41 -or $aggregate.mdfe.rootsWithMultiple -ne 2 -or
    $aggregate.mdfe.observedMaximumDistinctKeysPerRoot -ne 2 -or
    $aggregate.mdfe.numberDivergencesPerScopedKey -ne 0 -or
    [string]$aggregate.mdfe.contractMaximum -cne 'UNBOUNDED_ZERO_TO_MANY') {
    throw 'Os agregados MDF-e divergem do corpus estático versionado.'
}
if ($aggregate.mdfeStatus.valueRows -ne 228 -or $aggregate.mdfeStatus.distinctValues -ne 2 -or
    $aggregate.mdfeStatus.valueRowsWithoutMdfeKey -ne 148 -or $aggregate.mdfeStatus.rootsWithDivergence -ne 0 -or
    $aggregate.mdfeStatus.scopedMdfeKeysWithDivergence -ne 0 -or
    $aggregate.mdfeStatus.rootsMixingRowsWithAndWithoutMdfeKey -ne 0 -or
    [string]$aggregate.mdfeStatus.conclusion -cne 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_NOT_MDFE_CHILD_SIGNAL') {
    throw 'Os agregados de mdfe_status não sustentam seu papel de escalar raiz.'
}
if ($aggregate.cooccurrence.pickAndMdfeKey -ne 59 -or $aggregate.cooccurrence.pickOnly -ne 134 -or
    $aggregate.cooccurrence.mdfeKeyOnly -ne 21 -or $aggregate.cooccurrence.neither -ne 14 -or
    $aggregate.cooccurrence.total -ne 228 -or
    ($aggregate.cooccurrence.pickAndMdfeKey + $aggregate.cooccurrence.pickOnly) -ne $aggregate.pick.valueRows -or
    ($aggregate.cooccurrence.pickAndMdfeKey + $aggregate.cooccurrence.mdfeKeyOnly) -ne $aggregate.mdfe.keyValueRows -or
    ($aggregate.cooccurrence.pickAndMdfeKey + $aggregate.cooccurrence.pickOnly + $aggregate.cooccurrence.mdfeKeyOnly + $aggregate.cooccurrence.neither) -ne $aggregate.physicalRows) {
    throw 'A tabela agregada de coocorrência não fecha aritmeticamente.'
}
$contractResponseMaterial = [string]$contract.fingerprintInputs.response
foreach ($token in @(
    '228_rows', '100_roots', '193_scoped_pick_keys', '43_scoped_mdfe_keys',
    '80_number_key_pairs', '148_status_without_mdfe_child', 'mdfe_status=root_scalar_replicated_by_expansion'
)) {
    if ($contractResponseMaterial -cnotmatch [regex]::Escape($token)) {
        throw "O contrato 6399 não ancora o agregado/correção '$token'."
    }
}

Assert-ExactSet -Actual @($catalog.attributePresence.vocabulary) -Expected @('ABSENT', 'NULL', 'VALUE') -Label 'de presença tri-state'
Assert-ExactSet -Actual @($catalog.attributePresence.leafRepresentation) -Expected @(
    'source_path', 'presence', 'raw_value', 'typed_value', 'parse_state', 'provenance'
) -Label 'de representação de folha'
Assert-ExactSet -Actual @($catalog.attributePresence.parseStates) -Expected @('NOT_APPLICABLE', 'VALID', 'INVALID') -Label 'de estado de parse'
if ([string]$catalog.attributePresence.invalidTypedValue -cne 'PRESERVE_RAW_AND_QUARANTINE_NEVER_COERCE_TO_NULL') {
    throw 'Valor tipado inválido pode ser confundido com NULL.'
}
$presenceExpected = @(
    [pscustomobject]@{ input='ABSENT'; result='ABSENT_CURRENT_COHORT'; targetAction='PROMOTE_ABSENT_PRESENCE_WITHOUT_OLDER_VALUE_BACKFILL' },
    [pscustomobject]@{ input='NULL'; result='EXPLICIT_NULL'; targetAction='CLEAR_NULLABLE_FIELD_ON_STRICTLY_NEWER_COHORT' },
    [pscustomobject]@{ input='VALUE'; result='TYPED_VALUE'; targetAction='APPLY_ON_STRICTLY_NEWER_COHORT' },
    [pscustomobject]@{ input='INVALID'; result='QUARANTINE'; targetAction='NO_PROMOTION' }
)
Assert-ExactRows -Actual @($catalog.attributePresence.promotionApplicationTruthTable) -Expected $presenceExpected -Properties @('input', 'result', 'targetAction') -Label 'da truth table de presença/promoção'

$freshnessOrder = @('/finished_at', '/closed_at', '/departured_at', '/created_at')
Assert-ExactSequence -Actual @($catalog.freshness.dedupeOrder) -Expected $freshnessOrder -Label 'de frescor no dedupe'
Assert-ExactSequence -Actual @($catalog.freshness.promotionOrder) -Expected $freshnessOrder -Label 'de frescor na promoção'
Assert-ExactSet -Actual @($catalog.freshness.equalFreshness.forbiddenTieBreakers) -Expected @('ARRIVAL', 'PAGE', 'RECORD_ORDER', 'HASH') -Label 'de desempates proibidos'
if ([string]$catalog.freshness.selection -cne 'FIRST_VALUE_AFTER_ALL_PRESENT_TEMPORALS_VALIDATE' -or
    [string]$catalog.freshness.presentInvalid -cne 'PRESERVE_RAW_AND_QUARANTINE_WHOLE_OBSERVATION_NO_FALLBACK' -or
    [string]$catalog.freshness.noValidValue -cne 'PRESERVE_OBSERVATION_AND_BLOCK_PROMOTION' -or
    [string]$catalog.freshness.olderObservation -cne 'PRESERVE_AUDIT_AND_NO_OP_PROMOTION' -or
    [string]$catalog.freshness.equalFreshness.canonicalIdentical -cne 'REPLAY_NO_OP' -or
    [string]$catalog.freshness.equalFreshness.residualDivergence -cne 'EQUAL_FRESHNESS_CONFLICT_QUARANTINE' -or
    [string]$catalog.freshness.hashRole -cne 'OPTIONAL_PRECOMPARE_ONLY_REQUIRES_EXACT_CANONICAL_COMPARISON') {
    throw 'Frescor, replay ou empate divergente não estão fail-closed.'
}

$rootTruthExpected = @(
    [pscustomobject]@{ inputs='ONLY_ABSENT'; result='ABSENT'; action='CURRENT_COHORT_ABSENT_NO_OLDER_VALUE_BACKFILL' },
    [pscustomobject]@{ inputs='NULL_AND_OPTIONAL_ABSENT_NO_VALUE'; result='NULL'; action='EXPLICIT_NULL' },
    [pscustomobject]@{ inputs='ONE_CANONICAL_VALUE_REPEATED_AND_OPTIONAL_ABSENT_NO_NULL'; result='VALUE'; action='APPLY_UNIQUE_VALUE' },
    [pscustomobject]@{ inputs='NULL_AND_VALUE'; result='CONFLICT'; action='QUARANTINE_FIELD_AND_BLOCK_ROOT_PROMOTION' },
    [pscustomobject]@{ inputs='TWO_OR_MORE_DISTINCT_CANONICAL_VALUES'; result='CONFLICT'; action='QUARANTINE_FIELD_AND_BLOCK_ROOT_PROMOTION' },
    [pscustomobject]@{ inputs='ANY_INVALID_TYPED_VALUE'; result='INVALID'; action='QUARANTINE_OBSERVATION' }
)
Assert-ExactRows -Actual @($catalog.reducers.rootDefault.truthTable) -Expected $rootTruthExpected -Properties @('inputs', 'result', 'action') -Label 'do reducer raiz comum'
Assert-ExactSet -Actual @($catalog.reducers.rootDefault.forbidden) -Expected @('GENERIC_SUM', 'GENERIC_MAX', 'KEEP_LAST', 'STALE_BACKFILL') -Label 'de reducers raiz proibidos'
Assert-ExactSequence -Actual @($catalog.reducers.manifestStatus.knownPrecedence) -Expected @('closed', 'in_transit', 'pending') -Label 'de precedência do status Manifesto'
if ([string]$catalog.reducers.manifestStatus.oneUnknownValueRepeated -cne 'PRESERVE_RAW_NO_INFERRED_TERMINALITY' -or
    [string]$catalog.reducers.manifestStatus.knownAndUnknownOrDistinctUnknowns -cne 'QUARANTINE_STATUS_CONFLICT' -or
    [string]$catalog.reducers.manifestStatus.nullAndValue -cne 'QUARANTINE_STATUS_CONFLICT' -or
    [string]$catalog.reducers.mdfeStatus.role -cne 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_NEVER_CHILD_SIGNAL' -or
    [string]$catalog.reducers.mdfeStatus.childContext -cne 'RAW_PROVENANCE_CONTEXT_ONLY_AFTER_CHILD_EXISTS_NEVER_CANONICAL_CHILD_ATTRIBUTE_LIFECYCLE_OR_CREATION') {
    throw 'O reducer de status ou o papel de mdfe_status diverge de V03.'
}

$metricExpected = @(
    [pscustomobject]@{ logicalName='km'; sourcePath='/km'; aliasOf='' },
    [pscustomobject]@{ logicalName='totalCost'; sourcePath='/total_cost'; aliasOf='' },
    [pscustomobject]@{ logicalName='manifestFreightsTotal'; sourcePath='/manifest_freights_total'; aliasOf='' },
    [pscustomobject]@{ logicalName='totalTaxedWeight'; sourcePath='/total_taxed_weight'; aliasOf='' },
    [pscustomobject]@{ logicalName='vehicleWeightCapacity'; sourcePath='/mft_vie_weight_capacity'; aliasOf='' },
    [pscustomobject]@{ logicalName='capacidadeKg'; sourcePath='/mft_vie_weight_capacity'; aliasOf='vehicleWeightCapacity' },
    [pscustomobject]@{ logicalName='manifestItemsCount'; sourcePath='/manifest_items_count'; aliasOf='' },
    [pscustomobject]@{ logicalName='finalizedManifestItemsCount'; sourcePath='/finalized_manifest_items_count'; aliasOf='' }
)
Assert-ExactRows -Actual @($catalog.reducers.man07Metrics.attributes) -Expected $metricExpected -Properties @('logicalName', 'sourcePath', 'aliasOf') -Label 'dos oito atributos MAN-07'
$metricTruthExpected = @(
    [pscustomobject]@{ inputs='ONLY_ABSENT'; result='ABSENT'; action='CURRENT_COHORT_ABSENT_NO_OLDER_VALUE_BACKFILL' },
    [pscustomobject]@{ inputs='NULL_AND_OPTIONAL_ABSENT_NO_VALUE'; result='NULL'; action='EXPLICIT_NULL' },
    [pscustomobject]@{ inputs='ONE_CANONICAL_VALUE_WITH_NULL_OR_ABSENT'; result='VALUE'; action='COMPLEMENTED_FROM_EXPANSION' },
    [pscustomobject]@{ inputs='ONE_CANONICAL_VALUE_REPEATED'; result='VALUE'; action='APPLY_UNIQUE_VALUE' },
    [pscustomobject]@{ inputs='TWO_OR_MORE_DISTINCT_CANONICAL_VALUES_INCLUDING_ZERO_VERSUS_NONZERO'; result='METRIC_VALUE_CONFLICT'; action='QUARANTINE_METRIC_AND_BLOCK_ROOT_PROMOTION' },
    [pscustomobject]@{ inputs='ANY_INVALID_TYPED_VALUE'; result='INVALID'; action='QUARANTINE_OBSERVATION' }
)
Assert-ExactRows -Actual @($catalog.reducers.man07Metrics.truthTable) -Expected $metricTruthExpected -Properties @('inputs', 'result', 'action') -Label 'do reducer MAN-07'
if ([string]$catalog.reducers.man07Metrics.zero -cne 'REAL_VALUE_NEVER_MISSING' -or
    [string]$catalog.reducers.man07Metrics.capacityAlias -cne 'ONE_REDUCED_SOURCE_SIGNAL_TWO_IDENTICAL_LOGICAL_PROJECTIONS_NEVER_DIVERGE' -or
    [string]$catalog.reducers.scope -cne 'ONLY_WITHIN_MAXIMUM_FRESHNESS_COHORT_NEVER_BACKFILL_FROM_OLDER_COHORT') {
    throw 'MAN-07 perdeu zero, alias único ou isolamento da coorte vencedora.'
}
Assert-ExactSet -Actual @($catalog.reducers.man07Metrics.forbidden) -Expected @('SUM', 'MAX', 'METRIC_SCORE', 'OLDER_VERSION_COMPLEMENT') -Label 'de reducers MAN-07 proibidos'

Assert-ExactSequence -Actual @($catalog.competence.preserve) -Expected @('source_path', 'raw_value', 'fallback_origin', 'instant_utc') -Label 'de proveniência da competência'
Assert-ExactSet -Actual @($catalog.competence.forbiddenSubstitutes) -Expected @('EXTRACTION_TIME', 'MAX', 'CLOSED_AT', 'FINISHED_AT') -Label 'de substitutos proibidos da competência'
if ([string]$catalog.competence.primaryPath -cne '/departured_at' -or [string]$catalog.competence.fallbackPath -cne '/created_at' -or
    [string]$catalog.competence.fallbackCondition -cne 'PRIMARY_ABSENT_OR_NULL_ONLY' -or
    [string]$catalog.competence.primaryPresentInvalid -cne 'QUARANTINE_NO_FALLBACK' -or
    [string]$catalog.competence.bothWithoutValue -cne 'UNKNOWN_BLOCK_DOWNSTREAM_CONSUMER' -or
    [string]$catalog.competence.civilDateBucketing -cne 'NOT_AUTHORIZED_WITHOUT_VERSIONED_TIME_ZONE_CONTRACT') {
    throw 'A competência não preserva saída, fallback e bloqueios decididos.'
}

Assert-ExactSet -Actual @($catalog.relationshipCandidates.forbidden) -Expected @('COLETA_LOOKUP', 'FOREIGN_KEY', 'TOP_ONE', 'ORPHAN_NULLING', 'INFERRED_JOIN') -Label 'de relações proibidas'
Assert-ExactSet -Actual @($catalog.absence.forbidden) -Expected @('SWEEP', 'DELETE', 'DEACTIVATE', 'SOFT_DELETE', 'PUBLICATION') -Label 'de efeitos de ausência proibidos'
if ([string]$catalog.relationshipCandidates.manifestoToColetaPath -cne '/mft_pfs_pck_sequence_code' -or
    [string]$catalog.relationshipCandidates.storage -cne 'APPEND_ONLY_VALUE_PRESENCE_PROVENANCE' -or
    [string]$catalog.relationshipCandidates.resolutionOwner -cne 'V2-046A' -or
    [string]$catalog.relationshipCandidates.materialization -cne 'FORBIDDEN_IN_V03_AND_V04_BASE_INGESTION' -or
    [string]$catalog.relationshipCandidates.orphan -cne 'PRESERVE_CANDIDATE_NEVER_NULL_OR_DROP' -or
    [string]$catalog.absence.rootNotObservedInPageOrWindow -cne 'NO_STATE_CHANGE' -or
    [string]$catalog.absence.childNotObservedInPageOrWindow -cne 'NO_STATE_CHANGE' -or
    [string]$catalog.absence.emptyPage -cne 'PAGINATION_STOP_SIGNAL_ONLY_NO_DOMAIN_ABSENCE' -or
    [string]$catalog.absence.activation -cne 'BLOCKED_UNTIL_V2_013_AND_INDEPENDENT_COMPLETE_SNAPSHOT_PROOF') {
    throw 'Relação candidata ou ausência antecipou materialização, sweep ou publicação.'
}

Assert-ExactSet -Actual @($catalog.forbiddenMechanics) -Expected @(
    'HASH_AS_IDENTITY', 'ORDER_AS_IDENTITY', 'MDFE_NUMBER_AS_IDENTITY', 'MDFE_STATUS_AS_CHILD_SIGNAL',
    'COOCCURRENCE_AS_RELATION', 'ARRIVAL_AS_FRESHNESS', 'EQUAL_FRESHNESS_KEEP_LAST', 'GENERIC_SUM',
    'GENERIC_MAX', 'METRIC_SCORE_TIE_BREAK', 'STALE_BACKFILL', 'INDEPENDENT_MDFE_FIELD_REDUCTION',
    'ROOT_OR_CHILD_SWEEP', 'DELETE_OR_DEACTIVATE', 'RELATION_LOOKUP_OR_FOREIGN_KEY',
    'PUBLICATION_OR_CUTOVER', 'DDL_OR_RUNTIME_IN_V03'
) -Label 'de mecânicas proibidas'

$fixtureExpected = @(
    [pscustomobject]@{ id='PRESENCE_ONLY_ABSENT'; expected='ABSENT_CURRENT_COHORT_NO_OLDER_BACKFILL' },
    [pscustomobject]@{ id='PRESENCE_EXPLICIT_NULL'; expected='NULL_EXPLICIT_CLEAR_ON_NEWER_COHORT' },
    [pscustomobject]@{ id='FRESHNESS_FALLBACK'; expected='DEPARTURED_AT_UTC' },
    [pscustomobject]@{ id='FRESHNESS_INVALID'; expected='QUARANTINE_NO_PROMOTION' },
    [pscustomobject]@{ id='OUT_OF_ORDER'; expected='PRESERVE_AUDIT_NO_OP' },
    [pscustomobject]@{ id='IDENTICAL_REPLAY'; expected='REPLAY_NO_OP' },
    [pscustomobject]@{ id='EQUAL_FRESHNESS_ROOT_CONFLICT'; expected='EQUAL_FRESHNESS_CONFLICT' },
    [pscustomobject]@{ id='STATUS_PRECEDENCE'; expected='CLOSED' },
    [pscustomobject]@{ id='STATUS_UNKNOWN_CONFLICT'; expected='QUARANTINE_STATUS_CONFLICT' },
    [pscustomobject]@{ id='METRIC_NULL_COMPLEMENT'; expected='COMPLEMENTED_FROM_EXPANSION' },
    [pscustomobject]@{ id='METRIC_ZERO_REPLAY'; expected='VALUE_ZERO' },
    [pscustomobject]@{ id='METRIC_ZERO_CONFLICT'; expected='METRIC_VALUE_CONFLICT' },
    [pscustomobject]@{ id='MDFE_PHYSICAL_PAIR'; expected='ONE_MDFE_CHILD_BY_KEY_NUMBER_ATTRIBUTE' },
    [pscustomobject]@{ id='MDFE_NUMBER_WITHOUT_KEY'; expected='QUARANTINE_NO_CHILD' },
    [pscustomobject]@{ id='MDFE_STATUS_WITHOUT_KEY'; expected='ROOT_STATUS_ONLY_NO_CHILD' },
    [pscustomobject]@{ id='DISTINCT_CHILDREN'; expected='PRESERVE_DISTINCT_CHILDREN_NO_ROOT_CONFLICT' },
    [pscustomobject]@{ id='RELATION_CANDIDATE'; expected='APPEND_CANDIDATE_NO_RELATION' },
    [pscustomobject]@{ id='EMPTY_PAGE'; expected='STOP_PAGINATION_NO_SWEEP' }
)
Assert-ExactRows -Actual @($catalog.decisionFixtures) -Expected $fixtureExpected -Properties @('id', 'expected') -Label 'das fixtures decisórias sintéticas'
foreach ($fixture in @($catalog.decisionFixtures)) {
    if ($fixture.synthetic -isnot [bool] -or -not [bool]$fixture.synthetic -or [string]::IsNullOrWhiteSpace([string]$fixture.inputClass)) {
        throw 'Uma fixture decisória não é explicitamente sintética ou não possui classe de entrada.'
    }
}

$evidenceFiles = @($catalog.evidenceBounds.files)
if ($evidenceFiles.Count -lt 12) { throw 'A decisão V03 não lista a cadeia estática mínima de evidência e contraevidência.' }
$evidencePaths = @()
foreach ($evidence in $evidenceFiles) {
    foreach ($property in @('path', 'role', 'readOnly')) { Assert-RequiredProperty -Object $evidence -Name $property -Label 'arquivo de evidência V03' }
    if ($evidence.readOnly -isnot [bool] -or -not [bool]$evidence.readOnly) { throw 'Uma evidência V03 não está marcada read-only.' }
    $evidencePaths += [string]$evidence.path
    [void](Resolve-EvidencePath -RelativePath ([string]$evidence.path))
}
Assert-ExactSet -Actual $evidencePaths -Expected $evidencePaths -Label 'de paths de evidência sem duplicatas'

$dtoText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'src\main\java\br\com\extrator\dominio\dataexport\manifestos\ManifestoDTO.java') -MaximumBytes 1MB
$mapperText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'src\main\java\br\com\extrator\integracao\mapeamento\dataexport\manifestos\ManifestoMapper.java') -MaximumBytes 1MB
$deduplicatorText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'src\main\java\br\com\extrator\integracao\dataexport\support\Deduplicator.java') -MaximumBytes 1MB
$repositoryText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'src\main\java\br\com\extrator\persistencia\repositorio\ManifestoRepository.java') -MaximumBytes 1MB
$procedureText = Read-StrictUtf8 -LiteralPath (Join-Path $legacyRoot 'database\procedures\005_criar_sp_carga_fato_gestao_vista_manifestos.sql') -MaximumBytes 2MB
foreach ($pattern in @(
    '@JsonProperty\("mft_pfs_pck_sequence_code"\)', '@JsonProperty\("mft_mfs_number"\)',
    '@JsonProperty\("mft_mfs_key"\)', '@JsonProperty\("mdfe_status"\)',
    'allProps\.put\("capacidade_kg", vehicleWeightCapacity\)'
)) {
    if ($dtoText -cnotmatch $pattern) { throw "O DTO legado perdeu a âncora estática '$pattern'." }
}
if ($mapperText -cnotmatch 'setCapacidadeKg\(converterParaBigDecimal\(dto\.getVehicleWeightCapacity\(\)' -or
    $deduplicatorText -cnotmatch 'BigDecimal\.ZERO\.compareTo\(valor\) != 0' -or
    $deduplicatorText -cnotmatch 'enriquecerMetricasManifesto\(base, complemento\)' -or
    $repositoryText -cnotmatch 'FROM dbo\.coletas' -or
    $repositoryText -cnotmatch 'COALESCE\(CAST\(target\.finished_at AS datetime2\)' -or
    $procedureText -cnotmatch 'MAX\(ml\.mdfe_number\)' -or
    $procedureText -cnotmatch 'MAX\(ml\.mdfe_key\)' -or
    $procedureText -cnotmatch 'STRING_AGG\(' -or
    $procedureText -cnotmatch 'WHEN NOT MATCHED BY SOURCE') {
    throw 'A contraevidência estática de presença, frescor, reducer, relação ou sweep mudou.'
}

$matrix = @(Import-Csv -LiteralPath $matrixPath | Where-Object { [string]$_.entity -ceq 'manifestos' })
if ($matrix.Count -ne 281) { throw 'A fatia Manifestos da matriz V2-017a não contém 281 linhas.' }
$infoRows = @($matrix | Where-Object { [string]$_.row_kind -ceq 'DATA_EXPORT_FIELD' })
if ($infoRows.Count -ne 91 -or @($infoRows | Where-Object { [string]$_.reducer_or_fallback -cne 'UNRESOLVED_BY_FIELD' }).Count -ne 0) {
    throw 'Os 91 slots /info não permanecem explicitamente não resolvidos.'
}
$dataSplits = @($matrix | Where-Object { [string]$_.row_kind -ceq 'DATA_EXPORT_DATA_FIELD' -and [string]$_.decision -ceq 'SPLIT' })
$v1Splits = @($matrix | Where-Object { [string]$_.row_kind -ceq 'V1_OPERATIONAL_COLUMN' -and [string]$_.decision -ceq 'SPLIT' })
Assert-ExactSet -Actual @($dataSplits | ForEach-Object { [string]$_.source_path }) -Expected @(
    '/data/mft_pfs_pck_sequence_code', '/data/mft_mfs_number', '/data/mft_mfs_key'
) -Label 'de paths DATA classificados como filho'
Assert-ExactSet -Actual @($v1Splits | ForEach-Object { [string]$_.source_path }) -Expected @(
    '/data/mft_pfs_pck_sequence_code', '/data/mft_mfs_number', '/data/mft_mfs_key'
) -Label 'de aliases V1 classificados como filho'
$allSplits = @($dataSplits + $v1Splits)
foreach ($row in $allSplits) {
    if ([string]$row.cardinality -cnotmatch 'ZERO_TO_MANY' -or
        [string]$row.v2_zone -ceq 'crosswalk' -or
        [string]$row.v2_target -cmatch '(?i)pending_relation|crosswalk') {
        throw "A linha $($row.matrix_id) ainda mistura filho 0..N com relação pendente."
    }
}
$mdfeStatusRows = @($matrix | Where-Object { [string]$_.source_path -ceq '/data/mdfe_status' })
if ($mdfeStatusRows.Count -ne 2) { throw 'A matriz não contém os dois mapeamentos esperados de mdfe_status.' }
foreach ($row in $mdfeStatusRows) {
    if ([string]$row.decision -cne 'PRESERVE' -or
        [string]$row.cardinality -cmatch '(?i)child|relation' -or
        [string]$row.v2_target -cmatch '(?i)child|relation|crosswalk' -or
        [string]$row.reducer_or_fallback -cne 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_TRI_STATE_UNIQUE_AT_WINNING_FRESHNESS_NOT_CHILD_SIGNAL_V03') {
        throw "A linha $($row.matrix_id) ainda trata mdfe_status como sinal ou atributo de filho."
    }
}
$pendingBusinessReducers = @($matrix | Where-Object {
    [string]$_.row_kind -cne 'DATA_EXPORT_FIELD' -and
    [string]$_.decision -cnotin @('DERIVE', 'RETIRE') -and
    [string]$_.reducer_or_fallback -ceq 'FIELD_SPECIFIC_PENDING_VERTICAL'
})
if ($pendingBusinessReducers.Count -ne 0) {
    throw "A matriz mantém $($pendingBusinessReducers.Count) reducers de negócio Manifestos pendentes após V03."
}
$decidedPaths = @(
    '/data/sequence_code', '/data/created_at', '/data/departured_at', '/data/closed_at', '/data/finished_at',
    '/data/status', '/data/mdfe_status', '/data/mft_pfs_pck_sequence_code', '/data/mft_mfs_number', '/data/mft_mfs_key',
    '/data/km', '/data/total_cost', '/data/manifest_freights_total', '/data/total_taxed_weight',
    '/data/mft_vie_weight_capacity', '/data/manifest_items_count', '/data/finalized_manifest_items_count'
)
$decidedRows = @($matrix | Where-Object { [string]$_.source_path -cin $decidedPaths })
foreach ($row in $decidedRows) {
    if ([string]$row.status -cmatch 'CANDIDATE|UNRESOLVED' -or [string]$row.due_gate -cmatch 'V2-041|V2-025d') {
        throw "A linha decidida $($row.matrix_id) ainda depende de contrato externo para a decisão local P01/V03."
    }
}

if ($adrText -cnotmatch 'Status: Aceito somente para a decisão V03' -or
    $adrText -cnotmatch 'execução permanece não iniciada em V04' -or
    $adrText -cnotmatch '228 linhas físicas para 100 raízes' -or
    $adrText -cnotmatch '148 linhas sem chave MDF-e' -or
    $adrText -cnotmatch 'escalar da raiz replicado pela expansão' -or
    $adrText -cnotmatch 'Estado de coorte anterior fica somente no histórico/audit' -or
    $adrText -cnotmatch 'V2-046a' -or $adrText -cnotmatch 'V2-013') {
    throw 'O ADR 0024 não documenta integralmente os limites materiais da decisão V03.'
}

$sanitizedDecisionText = $catalogJson.Text + "`n" + $adrText
if ($sanitizedDecisionText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|https?://|token_dataexport|real[-_ ]?(?:id|cursor|document)') {
    throw 'Os artefatos V03 contêm marcador incompatível com documentação decisória sanitizada.'
}

Write-Output 'PASS: decisão V03 de Manifestos 6399 validada como decision-only; P01/contrato/fingerprints, raiz/filhos, mdfe_status raiz, tri-state, frescor, reducers, competência, agregados, relações candidatas, ausência e matriz V2-017a estão coerentes; V04 permanece NOT_STARTED.'

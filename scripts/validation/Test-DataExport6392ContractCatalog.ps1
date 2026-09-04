[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6392\manifesto.json'
$maximumManifestBytes = 256KB
$maximumFixtureBytes = 64KB

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) {
        throw 'Um artefato do catálogo excede o limite de bytes.'
    }
    return $utf8.GetString([System.IO.File]::ReadAllBytes($item.FullName))
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory)][object[]]$Actual,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualStrings = @($Actual | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $expectedStrings = @($Expected | Sort-Object -Unique)
    if ($actualStrings.Count -ne $expectedStrings.Count -or
        (Compare-Object -ReferenceObject $expectedStrings -DifferenceObject $actualStrings)) {
        throw "O conjunto $Label diverge da baseline fechada."
    }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)

    return [Convert]::ToHexString(
        [System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))
    ).ToLowerInvariant()
}

$manifestText = Read-StrictUtf8 -LiteralPath $manifestPath -MaximumBytes $maximumManifestBytes
try {
    $manifest = $manifestText | ConvertFrom-Json -Depth 64
} catch {
    throw 'O manifesto do contrato 6392 não é JSON válido.'
}

$classifications = @('PROVEN', 'TRANSITIONAL', 'ABSENT', 'BUSINESS_DECISION_PENDING')
$evidenceLevels = @(
    'HISTORICAL_SANITIZED_OBSERVATION',
    'LEGACY_STATIC_EVIDENCE',
    'SYNTHETIC_FIXTURES',
    'NO_INDEPENDENT_COMPLETENESS_EVIDENCE'
)
$requiredAspects = @(
    'transport', 'metadata', 'responseRoot', 'filters', 'temporalTranslation', 'ordering', 'per',
    'rootIdentity', 'childIdentity', 'pagination', 'timezone', 'limitsErrors', 'completeness'
)
$requiredRoles = @(
    'METADATA_SELECTED_SUBSET', 'PER_1_PAGE_1', 'PER_1_PAGE_2', 'PER_1_TERMINAL',
    'PER_3_PAGE_1', 'PER_3_TERMINAL'
)

if ([string]$manifest.catalogVersion -cne '2026-09-04.v2-025b.1' -or
    [string]$manifest.contract.contractId -cne 'dataexport-6392' -or
    [string]$manifest.contract.contractVersion -cne '2026-09-04.v2-025b.1') {
    throw 'A identidade documental do contrato 6392 diverge da baseline.'
}
Assert-ExactSet -Actual @($manifest.classificationVocabulary) -Expected $classifications -Label 'de classificações'
Assert-ExactSet -Actual @($manifest.evidenceVocabulary) -Expected $evidenceLevels -Label 'de evidências'
Assert-ExactSet -Actual @($manifest.contract.evidence) -Expected $evidenceLevels -Label 'de proveniências do contrato'
Assert-ExactSet -Actual @($manifest.contract.nextGates) -Expected @('V2-009b', 'V2-032', 'V2-025d') -Label 'de próximos gates'
Assert-ExactSet -Actual @($manifest.contract.fixtures.role) -Expected $requiredRoles -Label 'de fixtures'

$aspectNames = @($manifest.contract.aspects.PSObject.Properties.Name)
Assert-ExactSet -Actual $aspectNames -Expected $requiredAspects -Label 'de aspectos'
foreach ($aspect in @($manifest.contract.aspects.PSObject.Properties)) {
    if ([string]$aspect.Value.classification -notin $classifications -or
        [string]::IsNullOrWhiteSpace([string]$aspect.Value.contract) -or
        [string]::IsNullOrWhiteSpace([string]$aspect.Value.scope)) {
        throw 'Um aspecto não respeita o vocabulário, contrato ou escopo obrigatório.'
    }
}

if ([string]$manifest.contract.overallClassification -cne 'TRANSITIONAL' -or
    [string]$manifest.contract.aspects.rootIdentity.classification -cne 'BUSINESS_DECISION_PENDING' -or
    [string]$manifest.contract.aspects.childIdentity.classification -cne 'BUSINESS_DECISION_PENDING' -or
    [string]$manifest.contract.aspects.temporalTranslation.classification -cne 'BUSINESS_DECISION_PENDING' -or
    [string]$manifest.contract.aspects.completeness.classification -cne 'ABSENT' -or
    [string]$manifest.contract.capabilities.shadowUpsert -cne
        'NOT_AUTHORIZED_BY_THIS_CONTRACT_ALONE_REQUIRES_V2_009B_V2_032_AND_ENTITY_GATES' -or
    [string]$manifest.contract.capabilities.sweepOrDeactivation -cne 'BLOCKED_NO_COMPLETENESS_PROOF' -or
    [string]$manifest.contract.capabilities.cutover -cne 'BLOCKED_NO_COMPLETENESS_PROOF') {
    throw 'O contrato não preserva os bloqueios de identidade, temporalidade, vertical ou completude.'
}

$rootPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$fixturesByRole = @{}
foreach ($fixture in @($manifest.contract.fixtures)) {
    $relative = ([string]$fixture.path).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $relative))
    if (-not $resolved.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Uma fixture sai do repositório autorizado.'
    }
    $fixtureText = Read-StrictUtf8 -LiteralPath $resolved -MaximumBytes $maximumFixtureBytes
    try {
        $fixtureJson = $fixtureText | ConvertFrom-Json -Depth 64
    } catch {
        throw 'Uma fixture do contrato 6392 não é JSON válido.'
    }
    $actualHash = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -cne [string]$fixture.sha256) {
        throw 'Uma fixture diverge do SHA-256 versionado.'
    }
    $fixturesByRole[[string]$fixture.role] = $fixtureJson
}

$info = $fixturesByRole['METADATA_SELECTED_SUBSET']
$infoFieldNames = @($info.fields | ForEach-Object { [string]$_.name } | Sort-Object)
$infoFilterNames = @($info.filters | ForEach-Object { [string]$_.name } | Sort-Object)
if ($info.observedSummary.fieldCount -ne 44 -or $info.observedSummary.filterCount -ne 6 -or
    -not [bool]$info.syntheticOnly -or
    (Compare-Object -ReferenceObject @(
        'icm_fis_fit_corporation_sequence_number', 'icm_fis_ioe_number', 'opening_at_date', 'sequence_code'
    ) -DifferenceObject $infoFieldNames) -or
    (Compare-Object -ReferenceObject @('opening_at_date') -DifferenceObject $infoFilterNames)) {
    throw 'A fixture de metadata não preserva o resumo sanitizado do template 6392.'
}

$perThreeRows = @($fixturesByRole['PER_3_PAGE_1'].data)
$perThreeRootCandidates = @($perThreeRows | ForEach-Object { [long]$_.sequence_code } | Sort-Object -Unique)
if ($perThreeRows.Count -ne 2 -or $perThreeRootCandidates.Count -ne 2 -or
    @($perThreeRows | Where-Object {
        $_.sequence_code -isnot [long] -or $_.PSObject.Properties.Name -contains 'id' -or
        $_.PSObject.Properties.Name -notcontains 'icm_fis_fit_corporation_sequence_number' -or
        $_.PSObject.Properties.Name -notcontains 'icm_fis_ioe_number'
    }).Count -ne 0) {
    throw 'A fixture per=3 não preserva a observação sanitizada de duas linhas, sequências e relações sem id.'
}

$perOneRows = @(
    $fixturesByRole['PER_1_PAGE_1'].data + $fixturesByRole['PER_1_PAGE_2'].data
)
$perOneRootCandidates = @($perOneRows | ForEach-Object { [long]$_.sequence_code } | Sort-Object -Unique)
if ($perOneRows.Count -ne 2 -or $perOneRootCandidates.Count -ne 2 -or
    @($fixturesByRole['PER_1_TERMINAL'].data).Count -ne 0 -or
    @($fixturesByRole['PER_3_TERMINAL'].data).Count -ne 0 -or
    (Compare-Object -ReferenceObject $perThreeRootCandidates -DifferenceObject $perOneRootCandidates)) {
    throw 'As fixtures de paginação sintética não preservam linhas, candidatos e terminal local.'
}

foreach ($name in @('semantics', 'metadata', 'response')) {
    if ([string]$manifest.contract.fingerprints.$name -cne
        (Get-Utf8Sha256 -Value ([string]$manifest.contract.fingerprintInputs.$name))) {
        throw 'Um fingerprint de contrato não corresponde ao material UTF-8 declarado.'
    }
}
$releaseMaterial = ([string]$manifest.contract.fingerprintInputs.release) + '|' +
    ([string]$manifest.contract.fingerprints.semantics) + '|' +
    ([string]$manifest.contract.fingerprints.metadata) + '|' +
    ([string]$manifest.contract.fingerprints.response)
if ([string]$manifest.contract.fingerprints.release -cne (Get-Utf8Sha256 -Value $releaseMaterial)) {
    throw 'O fingerprint de release não corresponde aos fingerprints componentes.'
}

if ($manifestText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|tenant[-_ ]?url|real[-_ ]?(?:id|cursor|document)') {
    throw 'O manifesto contém marcador incompatível com evidência sanitizada.'
}

Write-Output 'PASS: contrato 6392 com 13 aspectos, seis fixtures sintéticas e dois candidatos de raiz validado; financeiro, temporalidade, relações, filhos, identidade e completude permanecem bloqueados.'

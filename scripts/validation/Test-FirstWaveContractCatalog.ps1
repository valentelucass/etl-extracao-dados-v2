[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-primeira-onda\manifesto.json'
$maximumManifestBytes = 2MB
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

$manifestText = Read-StrictUtf8 -LiteralPath $manifestPath -MaximumBytes $maximumManifestBytes
try {
    $manifest = $manifestText | ConvertFrom-Json -Depth 64
} catch {
    throw 'O manifesto da primeira onda não é JSON válido.'
}

$classifications = @('PROVEN', 'TRANSITIONAL', 'ABSENT', 'BUSINESS_DECISION_PENDING')
$evidenceLevels = @(
    'LOCAL_CODE_AND_SYNTHETIC_FIXTURES',
    'HISTORICAL_SANITIZED_OBSERVATION',
    'LEGACY_STATIC_EVIDENCE',
    'NO_INDEPENDENT_COMPLETENESS_EVIDENCE'
)
$contractIds = @('dataexport-6908', 'dataexport-6389', 'graphql-individual')
$expectedNextGates = @{
    'dataexport-6908' = @('V2-010', 'V2-025d', 'V2-009d')
    'dataexport-6389' = @('V2-011', 'V2-025d', 'V2-009d')
    'graphql-individual' = @('V2-012a')
}
$requiredAspects = @(
    'transport',
    'metadata',
    'responseRoot',
    'filters',
    'temporalTranslation',
    'ordering',
    'per',
    'rootIdentity',
    'childIdentity',
    'pagination',
    'timezone',
    'limitsErrors',
    'completeness'
)

Assert-ExactSet -Actual @($manifest.classificationVocabulary) -Expected $classifications -Label 'de classificações'
Assert-ExactSet -Actual @($manifest.evidenceVocabulary) -Expected $evidenceLevels -Label 'de evidências'
Assert-ExactSet -Actual @($manifest.contracts.contractId) -Expected $contractIds -Label 'de contratos'
if ([string]$manifest.catalogVersion -cne '2026-09-01.v2-025a.3') {
    throw 'A versão documental do catálogo V2-025a diverge da baseline atualizada.'
}

$rootPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$fixtureCount = 0
$aspectCount = 0
foreach ($contract in @($manifest.contracts)) {
    $contractId = [string]$contract.contractId
    if ($contract.overallClassification -notin $classifications) {
        throw 'Um contrato usa classificação global fora do vocabulário.'
    }
    foreach ($evidence in @($contract.evidence)) {
        if ([string]$evidence -notin $evidenceLevels) {
            throw 'Um contrato mistura classificação e proveniência ou usa evidência desconhecida.'
        }
    }
    if ('NO_INDEPENDENT_COMPLETENESS_EVIDENCE' -notin @($contract.evidence)) {
        throw 'Um contrato omite a ausência comprovada de oráculo independente de completude.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$contract.ownerRole) -or
        [string]$contract.ownerRole -notmatch '^[A-Z][A-Z_]+$') {
        throw 'Um contrato não declara owner-papel válido.'
    }
    Assert-ExactSet -Actual @($contract.nextGates) -Expected $expectedNextGates[$contractId] -Label "de próximos gates de $contractId"

    $aspectNames = @($contract.aspects.PSObject.Properties.Name)
    foreach ($requiredAspect in $requiredAspects) {
        if ($requiredAspect -notin $aspectNames) {
            throw 'Um contrato não cobre todas as dimensões obrigatórias de V2-025a.'
        }
    }
    foreach ($aspect in @($contract.aspects.PSObject.Properties)) {
        $aspectCount++
        if ([string]$aspect.Value.classification -notin $classifications) {
            throw 'Um aspecto usa classificação fora do vocabulário fechado.'
        }
        if ([string]::IsNullOrWhiteSpace([string]$aspect.Value.contract) -or
            [string]::IsNullOrWhiteSpace([string]$aspect.Value.scope)) {
            throw 'Um aspecto não declara contrato e escopo.'
        }
    }

    if ([string]$contract.capabilities.shadowUpsert -ne 'NOT_BLOCKED_BY_COMPLETENESS_CONTRACT_AND_ENTITY_GATES_REQUIRED' -or
        [string]$contract.capabilities.sweepOrDeactivation -ne 'BLOCKED_NO_COMPLETENESS_PROOF' -or
        [string]$contract.capabilities.cutover -ne 'BLOCKED_NO_COMPLETENESS_PROOF') {
        throw 'O gate de completude não separa upsert, sweep e cutover corretamente.'
    }

    foreach ($fingerprint in @($contract.fingerprints.PSObject.Properties)) {
        if ([string]$fingerprint.Value -notmatch '^[0-9a-f]{64}$') {
            throw 'Um fingerprint do contrato não é SHA-256 canônico.'
        }
    }

    foreach ($fixture in @($contract.fixtures)) {
        $fixtureCount++
        $relative = ([string]$fixture.path).Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $relative))
        if (-not $resolved.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Uma fixture sai do repositório autorizado.'
        }
        $fixtureText = Read-StrictUtf8 -LiteralPath $resolved -MaximumBytes $maximumFixtureBytes
        try {
            $null = $fixtureText | ConvertFrom-Json -Depth 64
        } catch {
            throw 'Uma fixture da primeira onda não é JSON válido.'
        }
        $actualHash = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -cne [string]$fixture.sha256) {
            throw 'Uma fixture diverge do SHA-256 versionado.'
        }
    }
}

$usersContract = @($manifest.contracts | Where-Object contractId -CEQ 'graphql-individual')
if ($usersContract.Count -ne 1 -or
    [string]$usersContract[0].aspects.nullableNamePolicy.classification -cne 'PROVEN' -or
    [string]$usersContract[0].aspects.nullableNamePolicy.scope -cne
        'LOCAL_V2-033_CURRENT_HISTORY' -or
    [string]$usersContract[0].aspects.nullableNamePolicy.contract -cne
        'SHADOW_DOMAIN_PRESERVES_ABSENT_NULL_VALUE; ABSENT_DOES_NOT_OVERWRITE_KNOWN_VALUE; REMOTE_SCHEMA_NULLABILITY_AND_PUBLISHED_CONSUMER_POLICY_UNVERIFIED') {
    throw 'A política tri-state local de nome de Usuários diverge de V2-033.'
}

if ($manifestText -match '(?i)authorization\s*:\s*bearer|password\s*[=:]|tenant[-_ ]?url|real[-_ ]?(?:id|cursor|document)') {
    throw 'O manifesto contém marcador incompatível com evidência sanitizada.'
}

Write-Output "PASS: 3 contratos, $aspectCount aspectos e $fixtureCount fixtures sintéticas validados; zero classificação desconhecida."

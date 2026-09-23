[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6399\manifesto.json'
$fixturePrefix = 'docs/catalogos/contratos-esl-6399/fixtures/'
$maximumManifestBytes = 256KB
$maximumFixtureBytes = 64KB

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) {
        throw 'Um artefato do contrato 6399 excede o limite de bytes.'
    }
    return $utf8.GetString([System.IO.File]::ReadAllBytes($item.FullName))
}

function Assert-RequiredProperty {
    param(
        [Parameter(Mandatory)][object]$Object,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Label
    )

    if ($Object.PSObject.Properties.Name -cnotcontains $Name) {
        throw "O artefato $Label não declara a propriedade obrigatória '$Name'."
    }
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Actual,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualRaw = @($Actual | ForEach-Object { [string]$_ })
    $actualStrings = @($actualRaw | Sort-Object -Unique)
    $expectedStrings = @($Expected | Sort-Object -Unique)
    if ($actualRaw.Count -ne $actualStrings.Count -or
        $actualStrings.Count -ne $expectedStrings.Count -or
        (Compare-Object -ReferenceObject $expectedStrings -DifferenceObject $actualStrings)) {
        throw "O conjunto $Label diverge da baseline fechada do contrato 6399."
    }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)

    return [Convert]::ToHexString(
        [System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))
    ).ToLowerInvariant()
}

function Test-JsonInteger {
    param([AllowNull()][object]$Value)

    return $Value -is [sbyte] -or $Value -is [byte] -or
        $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64] -or $Value -is [uint64]
}

function Assert-DataEnvelope {
    param(
        [Parameter(Mandatory)][object]$Fixture,
        [Parameter(Mandatory)][string]$Label
    )

    Assert-RequiredProperty -Object $Fixture -Name 'data' -Label $Label
    if ($Fixture.data -is [string] -or $Fixture.data -isnot [System.Collections.IEnumerable]) {
        throw "A fixture $Label não contém o envelope sintético data como array."
    }
}

$manifestText = Read-StrictUtf8 -LiteralPath $manifestPath -MaximumBytes $maximumManifestBytes
if ($manifestText -match '[�]') {
    throw 'O manifesto do contrato 6399 contém caractere de substituição Unicode.'
}
try {
    $manifest = $manifestText | ConvertFrom-Json -Depth 64
} catch {
    throw 'O manifesto do contrato 6399 não é JSON válido.'
}

foreach ($property in @('catalogVersion', 'classificationVocabulary', 'evidenceVocabulary', 'contract')) {
    Assert-RequiredProperty -Object $manifest -Name $property -Label 'manifesto'
}
foreach ($property in @(
        'contractId', 'sourceKind', 'documentReference', 'contractVersion',
        'overallClassification', 'evidence', 'aspects', 'capabilities', 'ownerRole',
        'nextGates', 'fixtures', 'fingerprintInputs', 'fingerprints'
    )) {
    Assert-RequiredProperty -Object $manifest.contract -Name $property -Label 'contrato'
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
    'METADATA_SELECTED_SUBSET', 'PER_2_PAGE_1', 'PER_2_PAGE_2', 'PER_2_TERMINAL',
    'PER_3_PAGE_1', 'PER_3_TERMINAL'
)

if ([string]$manifest.catalogVersion -cne '2026-09-04.v2-025b.2' -or
    [string]$manifest.contract.contractId -cne 'dataexport-6399' -or
    [string]$manifest.contract.sourceKind -cne 'DATA_EXPORT' -or
    [string]$manifest.contract.documentReference -cne 'dataexport-manifestos' -or
    [string]$manifest.contract.contractVersion -cne '2026-09-04.v2-025b.2') {
    throw 'A identidade documental do contrato 6399 diverge da revisão corretiva.'
}
Assert-ExactSet -Actual @($manifest.classificationVocabulary) -Expected $classifications -Label 'de classificações'
Assert-ExactSet -Actual @($manifest.evidenceVocabulary) -Expected $evidenceLevels -Label 'de evidências'
Assert-ExactSet -Actual @($manifest.contract.evidence) -Expected $evidenceLevels -Label 'de proveniências'
Assert-ExactSet -Actual @($manifest.contract.nextGates) -Expected @('V2-026', 'V2-025d') -Label 'de próximos gates'
Assert-ExactSet -Actual @($manifest.contract.fixtures.role) -Expected $requiredRoles -Label 'de fixtures'

$aspectNames = @($manifest.contract.aspects.PSObject.Properties.Name)
Assert-ExactSet -Actual $aspectNames -Expected $requiredAspects -Label 'de aspectos'
foreach ($aspect in @($manifest.contract.aspects.PSObject.Properties)) {
    foreach ($property in @('classification', 'contract', 'scope')) {
        Assert-RequiredProperty -Object $aspect.Value -Name $property -Label 'aspecto do contrato'
    }
    if ([string]$aspect.Value.classification -cnotin $classifications -or
        [string]::IsNullOrWhiteSpace([string]$aspect.Value.contract) -or
        [string]::IsNullOrWhiteSpace([string]$aspect.Value.scope)) {
        throw 'Um aspecto não respeita o vocabulário, contrato ou escopo obrigatório.'
    }
}

$responseRootContract = [string]$manifest.contract.aspects.responseRoot.contract
if ([string]$manifest.contract.aspects.responseRoot.classification -cne 'TRANSITIONAL' -or
    $responseRootContract -cnotmatch 'data_ARRAY' -or
    $responseRootContract -cnotmatch 'HISTORICAL_TOP_LEVEL_ARRAY' -or
    $responseRootContract -cnotmatch 'NORMALIZE_TO_THE_SAME_RECORD' -or
    $responseRootContract -cnotmatch 'OTHER_OR_AMBIGUOUS_SHAPE_IS_QUARANTINED') {
    throw 'O contrato não congela os dois envelopes de raiz e a normalização fail-closed.'
}

$childContract = [string]$manifest.contract.aspects.childIdentity.contract
if ([string]$manifest.contract.aspects.rootIdentity.classification -cne 'TRANSITIONAL' -or
    [string]$manifest.contract.aspects.childIdentity.classification -cne 'TRANSITIONAL' -or
    $childContract -cnotmatch 'mft_pfs_pck_sequence_code' -or
    $childContract -cnotmatch 'mft_mfs_key' -or
    $childContract -cnotmatch 'mft_mfs_number' -or
    $childContract -cnotmatch 'mdfe_status' -or
    $childContract -cnotmatch 'mft_mfs_number_AND_mft_mfs_key.*(?:PAIR|CO_?PRESENCE)' -or
    $childContract -cnotmatch 'ROOT_SCALAR' -or
    $childContract -cnotmatch '(?:STATUS_ALONE_(?:DOES_NOT|NEVER)_CREATES?|mdfe_status.*NEVER_CREATES?_A_CHILD)') {
    throw 'A identidade de filhos não separa o par número/chave do mdfe_status escalar.'
}
if ($childContract -cmatch 'number\+key\+status|mft_mfs_number_AND_mdfe_status_ARE_ATOMIC') {
    throw 'O contrato ainda transforma mdfe_status isolado em sinal do filho MDF-e.'
}

if ([string]$manifest.contract.overallClassification -cne 'TRANSITIONAL' -or
    [string]$manifest.contract.aspects.completeness.classification -cne 'ABSENT' -or
    [string]$manifest.contract.capabilities.shadowUpsert -cne
        'NOT_AUTHORIZED_BY_THIS_CONTRACT_ALONE_REQUIRES_V2_026_EXECUTION_AND_ENTITY_GATES' -or
    [string]$manifest.contract.capabilities.sweepOrDeactivation -cne 'BLOCKED_NO_COMPLETENESS_PROOF' -or
    [string]$manifest.contract.capabilities.cutover -cne 'BLOCKED_NO_COMPLETENESS_PROOF') {
    throw 'O contrato não preserva os bloqueios de implementação, completude, sweep e cutover.'
}
Assert-RequiredProperty -Object $manifest.contract.capabilities -Name 'externalHolds' -Label 'capabilities'
Assert-RequiredProperty -Object $manifest.contract.capabilities.externalHolds -Name 'V2-025d' -Label 'externalHolds'
if ([string]$manifest.contract.capabilities.externalHolds.'V2-025d' -cne 'BLOCKED_BY_V2_041_EXTERNAL_HOLD') {
    throw 'V2-025d não permanece explicitamente bloqueada pelo hold externo V2-041.'
}

if ($manifestText -cmatch '(?<![A-Za-z0-9_])pick_sequence_code(?![A-Za-z0-9_])') {
    throw 'O source path sintético refutado pick_sequence_code reapareceu no contrato 6399.'
}

$fixturesByRole = @{}
$fixturePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($fixture in @($manifest.contract.fixtures)) {
    foreach ($property in @('role', 'path', 'sha256')) {
        Assert-RequiredProperty -Object $fixture -Name $property -Label 'fixture declarada'
    }
    $role = [string]$fixture.role
    $declaredPath = ([string]$fixture.path).Replace('\', '/')
    if (-not $declaredPath.StartsWith($fixturePrefix, [System.StringComparison]::Ordinal) -or
        -not $fixturePaths.Add($declaredPath) -or $fixturesByRole.ContainsKey($role)) {
        throw 'Uma fixture está fora do diretório permitido ou possui papel/path duplicado.'
    }
    $relativePath = $declaredPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $relativePath))
    if (-not $resolved.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Uma fixture sai do repositório autorizado.'
    }
    $fixtureText = Read-StrictUtf8 -LiteralPath $resolved -MaximumBytes $maximumFixtureBytes
    if ($fixtureText -match '[�]') {
        throw 'Uma fixture contém caractere de substituição Unicode.'
    }
    try {
        $fixtureJson = $fixtureText | ConvertFrom-Json -Depth 64
    } catch {
        throw 'Uma fixture do contrato 6399 não é JSON válido.'
    }
    $actualHash = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -cne [string]$fixture.sha256) {
        throw 'Uma fixture diverge do SHA-256 declarado.'
    }
    $fixturesByRole[$role] = $fixtureJson
}

$info = $fixturesByRole['METADATA_SELECTED_SUBSET']
foreach ($property in @('observedSummary', 'fields', 'filters', 'syntheticOnly')) {
    Assert-RequiredProperty -Object $info -Name $property -Label 'fixture de metadata'
}
if ([int]$info.observedSummary.fieldCount -ne 91 -or
    [int]$info.observedSummary.filterCount -ne 18 -or
    [string]$info.observedSummary.classification -cne 'HISTORICAL_SANITIZED_OBSERVATION' -or
    -not [bool]$info.syntheticOnly) {
    throw 'A fixture de metadata não preserva o resumo sanitizado 91/18.'
}
$expectedFieldNames = @(
    'sequence_code', 'mft_pfs_pck_sequence_code', 'mft_mfs_number', 'mft_mfs_key',
    'mdfe_status', 'status', 'created_at', 'departured_at', 'closed_at', 'finished_at'
)
Assert-ExactSet -Actual @($info.fields.name) -Expected $expectedFieldNames -Label 'de nomes selecionados de /info'
Assert-ExactSet -Actual @($info.filters.name) -Expected @('service_date') -Label 'de filtros selecionados de /info'

$dataRoles = @('PER_2_PAGE_1', 'PER_2_PAGE_2', 'PER_2_TERMINAL', 'PER_3_PAGE_1', 'PER_3_TERMINAL')
foreach ($role in $dataRoles) {
    Assert-DataEnvelope -Fixture $fixturesByRole[$role] -Label $role
}

$perThreeRows = @($fixturesByRole['PER_3_PAGE_1'].data)
$perTwoRows = @($fixturesByRole['PER_2_PAGE_1'].data) + @($fixturesByRole['PER_2_PAGE_2'].data)
if ($perThreeRows.Count -ne 4 -or
    @($perThreeRows | ForEach-Object { [string]$_.sequence_code } | Sort-Object -Unique).Count -ne 3) {
    throw 'A fixture per=3 não preserva quatro linhas físicas para três raízes.'
}
if ($perTwoRows.Count -ne 4 -or
    @($perTwoRows | ForEach-Object { [string]$_.sequence_code } | Sort-Object -Unique).Count -ne 3) {
    throw 'As fixtures per=2 não preservam quatro linhas físicas para três raízes.'
}
if (@($fixturesByRole['PER_2_TERMINAL'].data).Count -ne 0 -or
    @($fixturesByRole['PER_3_TERMINAL'].data).Count -ne 0) {
    throw 'As fixtures terminais sintéticas não estão vazias.'
}

$allDataRows = @($perThreeRows + $perTwoRows)
foreach ($row in $allDataRows) {
    foreach ($property in @(
            'sequence_code', 'mft_pfs_pck_sequence_code', 'mft_mfs_number',
            'mft_mfs_key', 'mdfe_status', 'status'
        )) {
        Assert-RequiredProperty -Object $row -Name $property -Label 'linha sintética 6399'
    }
    if ($row.PSObject.Properties.Name -ccontains 'id' -or
        $row.PSObject.Properties.Name -ccontains 'pick_sequence_code') {
        throw 'Uma linha sintética promove ID ou o source path de pick refutado.'
    }
    if (-not (Test-JsonInteger -Value $row.sequence_code) -or [decimal]$row.sequence_code -le 0) {
        throw 'Uma linha sintética não preserva sequence_code inteiro positivo.'
    }
    if ($null -ne $row.mft_pfs_pck_sequence_code -and
        (-not (Test-JsonInteger -Value $row.mft_pfs_pck_sequence_code) -or
            [decimal]$row.mft_pfs_pck_sequence_code -le 0)) {
        throw 'Uma linha sintética não preserva pick inteiro positivo ou nulo.'
    }
    $numberPresent = $null -ne $row.mft_mfs_number
    $keyPresent = $null -ne $row.mft_mfs_key
    if ($numberPresent -ne $keyPresent) {
        throw 'As fixtures sintéticas quebram a co-presença do par número/chave MDF-e.'
    }
    if ($numberPresent -and
        (-not (Test-JsonInteger -Value $row.mft_mfs_number) -or [decimal]$row.mft_mfs_number -le 0)) {
        throw 'Uma linha sintética não preserva o número MDF-e inteiro positivo.'
    }
    if ($keyPresent -and ([string]$row.mft_mfs_key -cnotmatch '^[0-9]{44}$')) {
        throw 'Uma linha sintética não preserva chave MDF-e artificial com 44 dígitos ASCII.'
    }
}

$repeatedRoots = @($perThreeRows | Group-Object sequence_code | Where-Object Count -gt 1)
if ($repeatedRoots.Count -ne 1 -or $repeatedRoots[0].Count -ne 2) {
    throw 'A fixture per=3 não contém uma única raiz expandida em duas linhas.'
}
$expandedRows = @($repeatedRoots[0].Group)
if (@($expandedRows | ForEach-Object { [string]$_.mft_pfs_pck_sequence_code } | Sort-Object -Unique).Count -ne 2 -or
    @($expandedRows | ForEach-Object { [string]$_.mft_mfs_key } | Sort-Object -Unique).Count -ne 1 -or
    @($expandedRows | ForEach-Object { [string]$_.mft_mfs_number } | Sort-Object -Unique).Count -ne 1 -or
    @($expandedRows | Where-Object { [string]$_.mft_mfs_key -cnotmatch '^[0-9]{44}$' }).Count -ne 0) {
    throw 'A expansão sintética não preserva picks distintos sobre o mesmo par número/chave MDF-e.'
}

$statusOnlyRows = @($perThreeRows | Where-Object {
        $null -eq $_.mft_mfs_number -and
        $null -eq $_.mft_mfs_key -and
        $null -ne $_.mdfe_status -and
        -not [string]::IsNullOrWhiteSpace([string]$_.mdfe_status)
    })
if ($statusOnlyRows.Count -lt 1) {
    throw 'A fixture per=3 não prova que mdfe_status isolado é escalar e não cria filho MDF-e.'
}

foreach ($name in @('semantics', 'metadata', 'response')) {
    Assert-RequiredProperty -Object $manifest.contract.fingerprintInputs -Name $name -Label 'fingerprintInputs'
    Assert-RequiredProperty -Object $manifest.contract.fingerprints -Name $name -Label 'fingerprints'
    if ([string]$manifest.contract.fingerprints.$name -cne
        (Get-Utf8Sha256 -Value ([string]$manifest.contract.fingerprintInputs.$name))) {
        throw 'Um fingerprint de contrato não corresponde ao material UTF-8 declarado.'
    }
}
Assert-RequiredProperty -Object $manifest.contract.fingerprintInputs -Name 'release' -Label 'fingerprintInputs'
Assert-RequiredProperty -Object $manifest.contract.fingerprints -Name 'release' -Label 'fingerprints'
$responseFingerprintInput = [string]$manifest.contract.fingerprintInputs.response
if ($responseFingerprintInput -cnotmatch 'recordRoots=/data/\*_local_policy\+/\*_versioned_historical_static' -or
    $responseFingerprintInput -cnotmatch 'mdfe(?:Existence|Physical)?Pair=number\+key' -or
    $responseFingerprintInput -cnotmatch 'mdfe(?:Status|_status)=root(?:Scalar|_scalar)' -or
    $responseFingerprintInput -cmatch 'number\+key\+status') {
    throw 'O fingerprint de resposta não materializa envelopes duais, par número/chave e status escalar.'
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

Write-Output 'PASS: contrato 6399 v2-025b.2 validado com 13 aspectos, envelopes duais, paths corrigidos, par MDF-e número/chave, mdfe_status escalar, seis fixtures sintéticas e bloqueios fail-closed.'

#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$legacyRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '..\etl-extracao-dados'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-manifestos\manifesto.json'
$readmePath = Join-Path $repositoryRoot 'docs\catalogos\identidade-manifestos\README.md'
$contractPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6399\manifesto.json'
$fixturePath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-6399\fixtures\6399-per-3-page-1.synthetic.json'
$corpusPath = Join-Path $legacyRoot 'docs\legado\pipelines-antigos\02-apis\dataexport\manifestos.md'
$paginatorPath = Join-Path $legacyRoot 'src\main\java\br\com\extrator\integracao\DataExportPaginator.java'
$dtoPath = Join-Path $legacyRoot 'src\main\java\br\com\extrator\dominio\dataexport\manifestos\ManifestoDTO.java'

function Assert-True {
    param([Parameter(Mandatory)][bool]$Condition, [Parameter(Mandatory)][string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-ExactSet {
    param([AllowEmptyCollection()][object[]]$Actual, [AllowEmptyCollection()][string[]]$Expected, [Parameter(Mandatory)][string]$Label)
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        Assert-True ($item -is [string]) "Item não textual em $Label."
        Assert-True ($actualSet.Add([string]$item)) "Duplicata em $Label."
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Expected)) { [void]$expectedSet.Add($item) }
    Assert-True ($actualSet.SetEquals($expectedSet)) "Conjunto divergente em $Label."
}

function Assert-NoDuplicateJsonElement {
    param([Parameter(Mandatory)][System.Text.Json.JsonElement]$Element, [Parameter(Mandatory)][string]$Path)
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            Assert-True ($names.Add($property.Name)) "Propriedade JSON duplicada em $Path."
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

function Read-StrictText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [switch]$AllowBom
    )
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    Assert-True ($item.Length -le $MaximumBytes) "Arquivo fora do limite de leitura: $($item.Name)."
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    Assert-True (-not $hasBom -or $AllowBom) "BOM UTF-8 não permitido: $($item.Name)."
    if ($hasBom) { return $utf8.GetString($bytes, 3, $bytes.Length - 3) }
    return $utf8.GetString($bytes)
}

function Read-StrictJson {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][long]$MaximumBytes)
    $text = Read-StrictText -Path $Path -MaximumBytes $MaximumBytes
    $document = [System.Text.Json.JsonDocument]::Parse($text)
    try { Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$' } finally { $document.Dispose() }
    return [pscustomobject]@{ Text = $text; Value = ($text | ConvertFrom-Json -Depth 100) }
}

function Convert-ToCanonicalJson {
    param([Parameter(Mandatory)][System.Text.Json.JsonElement]$Element, [Parameter(Mandatory)][string]$Path)
    switch ($Element.ValueKind) {
        ([System.Text.Json.JsonValueKind]::Object) {
            $map = [System.Collections.Generic.Dictionary[string, System.Text.Json.JsonElement]]::new([System.StringComparer]::Ordinal)
            foreach ($property in $Element.EnumerateObject()) {
                if ($Path -ceq '$.identity' -and ($property.Name -ceq 'fingerprintInputs' -or $property.Name -ceq 'fingerprints')) { continue }
                Assert-True ($map.TryAdd($property.Name, $property.Value)) "Propriedade JSON duplicada em $Path."
            }
            [string[]]$names = @($map.Keys)
            [Array]::Sort($names, [System.StringComparer]::Ordinal)
            $parts = foreach ($name in $names) {
                $encoded = [System.Text.Json.JsonEncodedText]::Encode($name).ToString()
                '"' + $encoded + '":' + (Convert-ToCanonicalJson -Element $map[$name] -Path ($Path + '.' + $name))
            }
            return '{' + ($parts -join ',') + '}'
        }
        ([System.Text.Json.JsonValueKind]::Array) {
            $parts = foreach ($item in $Element.EnumerateArray()) { Convert-ToCanonicalJson -Element $item -Path ($Path + '[]') }
            return '[' + ($parts -join ',') + ']'
        }
        ([System.Text.Json.JsonValueKind]::String) { return '"' + [System.Text.Json.JsonEncodedText]::Encode($Element.GetString()).ToString() + '"' }
        ([System.Text.Json.JsonValueKind]::Number) { return $Element.GetRawText() }
        ([System.Text.Json.JsonValueKind]::True) { return 'true' }
        ([System.Text.Json.JsonValueKind]::False) { return 'false' }
        ([System.Text.Json.JsonValueKind]::Null) { return 'null' }
        default { throw "Tipo JSON não suportado em $Path." }
    }
}

function Get-Utf8Sha256 {
    param([Parameter(Mandatory)][string]$Value)
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Value))).ToLowerInvariant()
}

function Assert-FileHash {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Expected)
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-True ($actual -ceq $Expected) "SHA-256 divergente para $([System.IO.Path]::GetFileName($Path))."
}

$manifestJson = Read-StrictJson -Path $manifestPath -MaximumBytes 256KB
$contractJson = Read-StrictJson -Path $contractPath -MaximumBytes 256KB
$fixtureJson = Read-StrictJson -Path $fixturePath -MaximumBytes 128KB
$readme = Read-StrictText -Path $readmePath -MaximumBytes 128KB
$manifest = $manifestJson.Value
$identity = $manifest.identity
$contract = $contractJson.Value.contract

Assert-True ([string]$manifest.catalogVersion -ceq '2026-09-04.v2-009b.2') 'Versão P01 divergente.'
Assert-True ([string]$manifest.identityFingerprintVersion -ceq 'manifestos-identity-v2') 'Versão do fingerprint P01 divergente.'
Assert-True ([string]$manifest.task -ceq 'V2-009b/6399' -and [string]$manifest.route -ceq 'P01') 'Tarefa ou rota P01 divergente.'
Assert-True ([string]$manifest.decisionStatus -ceq 'COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED') 'Resultado P01 divergente.'
Assert-True ($readme -cmatch 'COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED' -and $readme -cmatch 'V2-041' -and $readme -cmatch '148') 'README P01 não registra resultado, hold e contraevidência de status.'

Assert-ExactSet @($manifest.evidenceVocabulary) @(
    'VERSIONED_LEGACY_STATIC_CORPUS', 'LEGACY_STATIC_EVIDENCE', 'LOCAL_POLICY_AND_SYNTHETIC_FIXTURES',
    'NO_CURRENT_PROVIDER_FINGERPRINT', 'NO_TEMPORAL_OR_TENANT_PAYLOAD_PROOF', 'NO_INDEPENDENT_COMPLETENESS_EVIDENCE'
) 'vocabulário de evidência P01'
Assert-ExactSet @($manifest.quarantineReasons) @(
    'AMBIGUOUS_RECORD_ENVELOPE', 'CARDINALITY_DIVERGENCE', 'CHILD_NUMBER_SIGNAL_WITHOUT_VALID_KEY',
    'EQUAL_FRESHNESS_CONFLICT', 'INVALID_CHILD_KEY_TYPE', 'INVALID_CHILD_KEY_VALUE', 'INVALID_SOURCE_KEY_TYPE',
    'INVALID_SOURCE_KEY_VALUE', 'MDFE_NUMBER_KEY_PAIR_ASYMMETRY', 'MDFE_ATTRIBUTE_CONFLICT', 'MISSING_SOURCE_KEY',
    'SCOPE_MISMATCH', 'SOURCE_KEY_COLLISION', 'SOURCE_KEY_TOO_LONG', 'UNPROVEN_ALIAS_CHANGE', 'UNPROVEN_REKEY',
    'UNSUPPORTED_RECORD_ENVELOPE'
) 'motivos de quarentena P01'
Assert-ExactSet @($manifest.actions) @(
    'APPEND_RELATION_CANDIDATE_NO_MATERIALIZATION', 'KEEP_CANONICAL', 'NO_CHILD_FOR_ABSENT_OR_NULL_NUMBER_KEY_PAIR',
    'NO_OP_REPLAY', 'PRESERVE_QUARANTINED_OBSERVATION', 'QUARANTINE', 'REACTIVATE_EXACT_BINDING',
    'REGISTER_NEW_CANONICAL', 'REGISTER_OR_REUSE_CHILD_BY_NATURAL_KEY', 'VERSION_ALIASES_PRESERVE_CANONICAL'
) 'ações P01'

Assert-True ([string]$contract.contractVersion -ceq '2026-09-04.v2-025b.2') 'Versão do contrato 6399 divergente.'
Assert-True ([string]$contract.fingerprints.release -ceq '30181093f00f47507d1910d9806a97697118ab671aeae0598d21a4a2726a7cb8') 'Release do contrato 6399 divergente.'
Assert-True ([string]$identity.contractVersion -ceq [string]$contract.contractVersion -and [string]$identity.sourceContractReleaseFingerprint -ceq [string]$contract.fingerprints.release) 'P01 não está preso ao contrato 6399 exato.'
Assert-True ([string]$identity.recordRoot -ceq 'NORMALIZED_RECORD') 'Raiz normalizada ausente.'
Assert-ExactSet @($identity.recordRootVariants | ForEach-Object { [string]$_.path }) @('/data/*', '/*') 'envelopes de registro'
Assert-True ([string]$identity.recordRootPolicy -cmatch 'QUARANTINE_ANY_OTHER_OR_AMBIGUOUS_SHAPE') 'Envelope não falha fechado.'

Assert-ExactSet @($manifest.registry.tuple) @('source_instance', 'tenant_scope', 'entity', 'source_key') 'tuple escopada'
Assert-True ([string]$manifest.scope.policy -ceq 'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED' -and $null -eq $manifest.scope.tenantPayloadPath -and -not [bool]$manifest.scope.globalUniquenessProven) 'Scope P01 inventa tenant ou unicidade global.'
Assert-ExactSet @($manifest.scope.forbiddenTenantSentinels) @('DEFAULT', 'GLOBAL', 'SINGLETON') 'sentinels de tenant'
Assert-True ([string]$identity.sourceKey.path -ceq '/sequence_code' -and [string]$identity.sourceKey.valueConstraint -ceq 'POSITIVE_CANONICAL_BASE10_INTEGER') 'Source key da raiz divergente.'
Assert-ExactSet @($identity.sourceKey.wireTypes) @('INTEGER') 'wire types da raiz'
Assert-True ([string]$identity.canonicalKey.strategy -ceq 'SQL_SURROGATE_BIGINT_IDENTITY') 'Canonical ID não é surrogate.'
Assert-ExactSet @($identity.aliases.rejected) @('pick_sequence_code', 'mft_mfs_number', 'identificador_unico', 'chave_merge_hash', 'metadata_hash') 'aliases rejeitados'
Assert-True (@($identity.aliases.accepted).Count -eq 0) 'P01 aceitou alias sem prova.'

$children = @($identity.physicalGrain.children)
Assert-True ($children.Count -eq 2) 'P01 deve declarar exatamente dois tipos de filho.'
$pick = @($children | Where-Object { [string]$_.name -ceq 'pick_expansion' })
$mdfe = @($children | Where-Object { [string]$_.name -ceq 'mdfe_expansion' })
Assert-True ($pick.Count -eq 1 -and $mdfe.Count -eq 1) 'Filhos pick/MDF-e divergentes.'
$pick = $pick[0]
$mdfe = $mdfe[0]
Assert-True ([string]$pick.path -ceq '/mft_pfs_pck_sequence_code' -and [string]$pick.cardinality -cmatch 'ZERO_TO_MANY' -and [string]$pick.cardinality -cmatch 'ZERO_OR_ONE') 'Path ou cardinalidade de pick divergente.'
Assert-ExactSet @($pick.naturalComponents) @('root_canonical_id', 'INTEGER:mft_pfs_pck_sequence_code') 'componentes naturais de pick'
Assert-True ([string]$mdfe.path -ceq '/mft_mfs_key' -and [string]$mdfe.valueConstraint -ceq 'EXACTLY_44_ASCII_DECIMAL_DIGITS' -and [string]$mdfe.cardinality -cmatch 'ZERO_TO_MANY') 'Path, shape ou cardinalidade MDF-e divergente.'
Assert-ExactSet @($mdfe.naturalComponents) @('root_canonical_id', 'STRING:mft_mfs_key') 'componentes naturais MDF-e'
Assert-ExactSet @($mdfe.sameRecordPhysicalPair) @('/mft_mfs_number', '/mft_mfs_key') 'par físico MDF-e'
Assert-True ([string]$mdfe.numberRole -cmatch 'NEVER_IDENTITY' -and [string]$mdfe.statusRole -cmatch 'NEVER_CREATES_OR_IDENTIFIES_CHILD') 'Número/status foram promovidos indevidamente à identidade do filho.'
Assert-True ([string]$mdfe.absence -cmatch 'EVEN_WHEN_MDFE_STATUS_IS_VALUE' -and [string]$mdfe.asymmetry -cmatch 'STATUS_ALONE_IS_NOT_ASYMMETRY') 'Status isolado cria MDF-e ou quarentena indevida.'
Assert-True ([string]$mdfe.reduction -cmatch 'SAME_PHYSICAL_RECORD' -and [string]$mdfe.reduction -cmatch 'NEVER_INDEPENDENT_MAX') 'Par MDF-e permite Frankenstein.'
Assert-True ([string]$identity.physicalGrain.relationPolicy -cmatch 'NOT_INFERRED' -and [string]$pick.relation -cmatch 'V2_046A' -and [string]$pick.relation -cmatch 'NO_COLETA_RELATION_MATERIALIZED') 'Relação Manifesto→Coleta foi inferida.'

$rootScalars = @($identity.physicalGrain.rootReplicatedScalars)
Assert-True ($rootScalars.Count -eq 1 -and [string]$rootScalars[0].path -ceq '/mdfe_status') 'mdfe_status não foi fixado como escalar da raiz.'
Assert-True ([string]$rootScalars[0].observedPresence -cmatch '228_OF_228' -and [string]$rootScalars[0].observedPresence -cmatch '148_WITHOUT_MDFE_CHILD' -and [string]$rootScalars[0].decision -cmatch 'NEVER_USE_AS_CHILD_SIGNAL') 'Decisão de mdfe_status não incorpora a contraevidência local.'
Assert-True ([bool]$identity.outcome.closeSubcheckbox -and @($identity.outcome.blockers).Count -eq 0 -and @($identity.outcome.unblockEvidence).Count -eq 0) 'P01 não fecha exclusivamente seu subcheckbox.'
Assert-True ([string]$identity.capabilities.sweepOrDeactivation -ceq 'BLOCKED_NO_COMPLETENESS_PROOF' -and [string]$identity.capabilities.cutover -ceq 'BLOCKED_NO_COMPLETENESS_PROOF') 'P01 autorizou ausência/cutover.'
Assert-True ([string]$identity.capabilities.externalHolds.'V2-025d' -ceq 'BLOCKED_BY_V2_041_EXTERNAL_HOLD') 'Hold V2-041 não foi preservado.'
Assert-ExactSet @($identity.nextGates) @('V2-026', 'V2-009d', 'V2-025d') 'próximos gates P01'

Assert-FileHash -Path $corpusPath -Expected '0993449c8e3a87ebaffea5c5fde0c8a31b1d399d53218debdadad3e20dcf5ef9'
Assert-FileHash -Path $paginatorPath -Expected 'a44b3e18d18aa3933f98b1e63184524fd094909e4620852a07f44168c7bd6e59'
Assert-FileHash -Path $dtoPath -Expected '554d44fe98b85d9193609edecd2f958fc71d2883ea6ee93a58f126ebfd0623ba'
$paginatorText = Read-StrictText -Path $paginatorPath -MaximumBytes 512KB
Assert-True ($paginatorText -cmatch 'raizJson\.has\("data"\)\s*\?\s*raizJson\.get\("data"\)\s*:\s*raizJson' -and $paginatorText -cmatch 'dadosNode\.isArray\(\)') 'Parser legado não sustenta os dois envelopes de array.'
$dtoText = Read-StrictText -Path $dtoPath -MaximumBytes 512KB
foreach ($path in @('mft_pfs_pck_sequence_code', 'mft_mfs_number', 'mft_mfs_key', 'mdfe_status')) {
    Assert-True ($dtoText.Contains('@JsonProperty("' + $path + '")', [System.StringComparison]::Ordinal)) "DTO não contém o path $path."
}
Assert-True (-not $dtoText.Contains('@JsonProperty("pick_sequence_code")', [System.StringComparison]::Ordinal) -and -not $dtoText.Contains('@JsonAlias("pick_sequence_code")', [System.StringComparison]::Ordinal)) 'DTO aceita o path refutado pick_sequence_code.'

$corpusText = Read-StrictText -Path $corpusPath -MaximumBytes 4MB -AllowBom
$corpusMatch = [regex]::Match($corpusText, '(?ms)^\[\s*\{.*\}\s*\]\s*$')
Assert-True $corpusMatch.Success 'Array histórico 6399 não foi localizado de forma delimitada.'
$rows = @($corpusMatch.Value | ConvertFrom-Json -Depth 100)
Assert-True ($rows.Count -eq 228) 'Contagem física histórica 6399 divergente.'

$rootSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$pickSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$mdfeSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$globalMdfeSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$mdfeCounts = @{}
$rootPickCounts = @{}
$rootMdfeSets = @{}
$rootStatusSets = @{}
$rootHasMdfe = @{}
$rootHasNoMdfe = @{}
$pickObservations = 0
$mdfeObservations = 0
$absentMdfeObservations = 0
$asymmetricPairs = 0
foreach ($row in $rows) {
    foreach ($required in @('sequence_code', 'mft_pfs_pck_sequence_code', 'mft_mfs_number', 'mft_mfs_key', 'mdfe_status')) {
        Assert-True ($row.PSObject.Properties.Name -ccontains $required) "Corpus 6399 não contém o path obrigatório $required."
    }
    Assert-True ($row.PSObject.Properties.Name -cnotcontains 'pick_sequence_code') 'Corpus contém o path refutado pick_sequence_code.'
    Assert-True (($row.sequence_code -is [int] -or $row.sequence_code -is [long]) -and [long]$row.sequence_code -gt 0) 'Root source key histórica fora do shape integral positivo.'
    $root = 'I:' + [string]$row.sequence_code
    [void]$rootSet.Add($root)
    if (-not $rootPickCounts.ContainsKey($root)) { $rootPickCounts[$root] = 0 }
    if (-not $rootMdfeSets.ContainsKey($root)) { $rootMdfeSets[$root] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal) }
    if (-not $rootStatusSets.ContainsKey($root)) { $rootStatusSets[$root] = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal) }
    Assert-True ($null -ne $row.mdfe_status -and -not [string]::IsNullOrWhiteSpace([string]$row.mdfe_status)) 'mdfe_status histórico não é VALUE.'
    [void]$rootStatusSets[$root].Add([string]$row.mdfe_status)

    if ($null -ne $row.mft_pfs_pck_sequence_code) {
        Assert-True (($row.mft_pfs_pck_sequence_code -is [int] -or $row.mft_pfs_pck_sequence_code -is [long]) -and [long]$row.mft_pfs_pck_sequence_code -gt 0) 'Pick histórico fora do shape integral positivo.'
        $pickObservations++
        $rootPickCounts[$root]++
        [void]$pickSet.Add($root + '|I:' + [string]$row.mft_pfs_pck_sequence_code)
    }

    $hasNumber = $null -ne $row.mft_mfs_number
    $hasKey = $null -ne $row.mft_mfs_key
    if ($hasNumber -xor $hasKey) { $asymmetricPairs++ }
    if ($hasKey) {
        Assert-True ($hasNumber -and ($row.mft_mfs_number -is [int] -or $row.mft_mfs_number -is [long]) -and [long]$row.mft_mfs_number -gt 0) 'Número MDF-e histórico fora do par integral positivo.'
        Assert-True ([string]$row.mft_mfs_key -cmatch '^\d{44}$') 'Chave MDF-e histórica fora do contrato de 44 dígitos.'
        $mdfeObservations++
        $scopedMdfe = $root + '|S:' + [string]$row.mft_mfs_key
        [void]$mdfeSet.Add($scopedMdfe)
        [void]$globalMdfeSet.Add([string]$row.mft_mfs_key)
        [void]$rootMdfeSets[$root].Add([string]$row.mft_mfs_key)
        $rootHasMdfe[$root] = $true
        if (-not $mdfeCounts.ContainsKey($scopedMdfe)) { $mdfeCounts[$scopedMdfe] = 0 }
        $mdfeCounts[$scopedMdfe]++
    } else {
        $absentMdfeObservations++
        $rootHasNoMdfe[$root] = $true
    }
}

Assert-True ($rootSet.Count -eq 100) 'Contagem histórica de raízes divergente.'
Assert-True ($pickObservations -eq 193 -and $pickSet.Count -eq 193) 'Contagem/colisão histórica de picks divergente.'
Assert-True ($mdfeObservations -eq 80 -and $absentMdfeObservations -eq 148 -and $asymmetricPairs -eq 0) 'Par número/chave MDF-e histórico divergente.'
Assert-True ($mdfeSet.Count -eq 43 -and $globalMdfeSet.Count -eq 43) 'Contagem ou reutilização cross-root de chave MDF-e divergente.'
$repeatGroups = @($mdfeCounts.GetEnumerator() | Where-Object Value -gt 1)
$repeatExcess = [int](($repeatGroups | ForEach-Object { [int]$_.Value - 1 } | Measure-Object -Sum).Sum)
$maximumRepeat = [int](($mdfeCounts.Values | Measure-Object -Maximum).Maximum)
Assert-True ($repeatGroups.Count -eq 16 -and $repeatExcess -eq 37 -and $maximumRepeat -eq 7) 'Perfil de replay MDF-e histórico divergente.'
$maximumPicksPerRoot = [int](($rootPickCounts.Values | Measure-Object -Maximum).Maximum)
$maximumMdfePerRoot = [int](($rootMdfeSets.Values | ForEach-Object Count | Measure-Object -Maximum).Maximum)
Assert-True ($maximumPicksPerRoot -eq 10 -and $maximumMdfePerRoot -eq 2) 'Máximos observados 6399 divergentes; eles não são caps contratuais.'
Assert-True (@($rootStatusSets.Values | Where-Object Count -ne 1).Count -eq 0) 'mdfe_status diverge dentro de uma raiz histórica.'
Assert-True (@($rootSet | Where-Object { $rootHasMdfe.ContainsKey($_) -and $rootHasNoMdfe.ContainsKey($_) }).Count -eq 0) 'Uma raiz histórica mistura presença/ausência MDF-e.'

$fixtureRows = @($fixtureJson.Value.data)
Assert-True ($fixtureRows.Count -eq 4 -and @($fixtureRows | Group-Object sequence_code).Count -eq 3) 'Fixture sintética 6399 não conserva shape 4/3.'
Assert-True (@($fixtureRows | Where-Object { $_.PSObject.Properties.Name -ccontains 'pick_sequence_code' }).Count -eq 0) 'Fixture reinventa pick_sequence_code.'
Assert-True (@($fixtureRows | Where-Object { $null -eq $_.mft_mfs_number -and $null -eq $_.mft_mfs_key -and $null -ne $_.mdfe_status }).Count -ge 1) 'Fixture não prova que status isolado é raiz sem filho MDF-e.'
Assert-True (@($fixtureRows | Where-Object { $null -ne $_.mft_mfs_key -and [string]$_.mft_mfs_key -cnotmatch '^\d{44}$' }).Count -eq 0) 'Fixture MDF-e viola 44 dígitos.'

$document = [System.Text.Json.JsonDocument]::Parse($manifestJson.Text)
try { $canonical = Convert-ToCanonicalJson -Element $document.RootElement -Path '$' } finally { $document.Dispose() }
$identityFingerprint = Get-Utf8Sha256 -Value ([string]$identity.fingerprintInputs.identity + '|' + $canonical)
Assert-True ([string]$identity.fingerprints.identity -ceq $identityFingerprint) "Fingerprint P01 divergente (calculado: $identityFingerprint)."
Assert-True ([string]$identity.fingerprintInputs.release -ceq '2026-09-04.v2-009b.2|manifestos-identity-v2|dataexport-6399') 'Material de release P01 divergente.'
$releaseFingerprint = Get-Utf8Sha256 -Value ([string]$identity.fingerprintInputs.release + '|' + $identityFingerprint + '|' + [string]$identity.sourceContractReleaseFingerprint)
Assert-True ([string]$identity.fingerprints.release -ceq $releaseFingerprint) "Release P01 divergente (calculado: $releaseFingerprint)."

Write-Output 'PASS: V2-009b/6399 validada como COMPLETE_LOCAL_CONSERVATIVE_FAIL_CLOSED; raiz, filhos, paths, escopo, colisões, replay, cardinalidade 0..N, status raiz, corpus versionado e limites fail-closed estão coerentes.'

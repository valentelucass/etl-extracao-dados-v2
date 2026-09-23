[CmdletBinding()]
param(
    [switch]$ArtifactsOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestRelativePath = 'docs/catalogos/caracterizacao-v2-012/q-fnd-02/manifesto.json'
$manifestHashRelativePath = 'docs/catalogos/caracterizacao-v2-012/q-fnd-02/manifesto.sha256'
$expectedQfnd02JavaFiles = @(
    'Qfnd02ClosedSchemaTest.java', 'Qfnd02DeterminismTest.java', 'Qfnd02Evaluator.java',
    'Qfnd02FoundationTest.java', 'Qfnd02FretesRule.java', 'Qfnd02Loader.java',
    'Qfnd02LocalizacaoRule.java', 'Qfnd02MutationTest.java', 'Qfnd02Observation.java',
    'Qfnd02OfflineBoundaryTest.java', 'Qfnd02Profile.java', 'Qfnd02Q01ImmutabilityTest.java',
    'Qfnd02ReceiptWriter.java', 'Qfnd02Registry.java', 'Qfnd02Result.java', 'Qfnd02Vocabulary.java'
) | ForEach-Object { "src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/qfnd02/$_" }
$expectedQfnd02ResourceHashes = [ordered]@{
    'src/test/resources/contracts/v2-012/extensions/q-fnd-02/profiles/fretes-6389.profile.json' = 'e58300114443f94673545a12d8c8ece983725bfaa99be37c855df90b99451ea9'
    'src/test/resources/contracts/v2-012/extensions/q-fnd-02/profiles/localizacao-8656.profile.json' = 'd557f2bbfaea0e130aded1f991b7a6c96e9850162160b83832e4b3e32b10a37f'
    'src/test/resources/contracts/v2-012/extensions/q-fnd-02/fixtures/fretes-6389.synthetic.json' = 'a1ce0672197da66075e8bdce4ba659438f304aa41c5b3bd067f3d1c36a227647'
    'src/test/resources/contracts/v2-012/extensions/q-fnd-02/fixtures/localizacao-8656.synthetic.json' = '1162b138240086e0b1b9eecfcda7d6a80d1e65a82c7a28dea8427161d06402f3'
}
$expectedQfnd02ResourceFiles = @($expectedQfnd02ResourceHashes.Keys)
$expectedGovernanceArtifacts = [ordered]@{
    'ADR' = @('docs/adr/0029-extensao-aditiva-perfis-fretes-localizacao.md', 'b04fd7160d62d3b8150ff5dc59dc440346293bdc942ee937460db77a93b09424')
    'CATALOG_README' = @('docs/catalogos/caracterizacao-v2-012/q-fnd-02/README.md', 'ef8f8b7c2fd293e59cbb44de2f881f82d8069b162e8be75c08eeed1dc3181d74')
    'RUNBOOK' = @('docs/runbooks/v2-012-q-fnd-02-perfis-fretes-localizacao-sol.md', 'c7f9d8bc01cf32b270a648c4358985039891ea4226a04463baff28f38d672e71')
}

$q01Lock = [ordered]@{
    'docs/adr/0026-harness-caracterizacao-provider-neutral-fail-closed.md' = 'de4c3eaf3c4203b331d7ce316d6a5660d4c73af0128048dafc724b1fff65420a'
    'docs/catalogos/caracterizacao-v2-012/manifesto.json' = 'dc20b409ed726055bb6915fd6122af69704a975e570cd14867430c4a84496787'
    'docs/catalogos/caracterizacao-v2-012/README.md' = '3e6243848e80033878e7afea95c9ca407b1537633178a2358581714894fd7dc6'
    'docs/runbooks/v2-012-fundacao-caracterizacao-sol.md' = 'a7f15c7287547463a864e76bf952f7fc4260fb99f541bd41671cfa153c337bae'
    'scripts/validation/Test-V2012CharacterizationFoundation.ps1' = 'be22c7b12da4bb2a330bbc3ec9e0b74213059e52195db34476af78a2bd6b1743'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationAdapterRegistry.java' = 'f13c7b78144d2f47102e170443fbebb69d3efd3e703ebed8122171f686306040'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationClosedSchemaTest.java' = 'f4dd8e702d31e48237278752a7888f7c08bbb85ca4daaec10661b1a3624bb17c'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationDeterminismTest.java' = '5a37ef19e364f567c31f7bf3e19a38cf3a23bec2125b810423f0f5e1396c6c65'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationEvaluator.java' = '0c47113040a89aefb6c71ea0859c434eac9819188adc1fe9733a49f11339b026'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFingerprint.java' = '93a79c65f562f0ea0ea483e5a9bddd89f2080f1328c1e473292e156c46635474'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFixture.java' = 'd629491595b17e6d0ca33a5696f8b686371498400feac140d4908b289a0cac89'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFixtureLoader.java' = '0192cdeec0d99fc5be4430268d48ddf3bfd481a08bb55aac778692e417033783'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFoundationTest.java' = 'cbc3f5fb828c1665dabacfa543e6c6d28c1e7fe5464eb637a63eaa630725e1a1'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationObservation.java' = '05c0dc91b1270cfa8f3530f971d625fb0da1f76dc0a4e0b2b2b88fc18079545a'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationOfflineBoundaryTest.java' = '9057d5969433b5da8f94057221bdd3eee65acd4c6d35ebca1f8db447f6b87c68'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationOracleAdapter.java' = '469c7d2580823a02217813e52ea6987479146e4946c43ca96cd53d814cc126bb'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfile.java' = 'c4a5d709db558d7e650576442bd5877bd0adc5e85a8d6bcab5096d37feb3626b'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfileLoader.java' = '1ba8bd16bde5b29bb0edc21a28f6af231be188f9decf3f627265c796a9cd4a33'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfileRegistry.java' = '12f9712a39296f76150a4a669e06f9f1183b7f226731c94456de3285efa478a2'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReceipt.java' = 'f0bbd40d81dccc6f815eba845a68064afac79d47d229f72aa21a33f59b175945'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReceiptWriter.java' = '2a07bc489b8a8fa43ad0f363de49a5a271ffe554c4319a8b17c8ae46e0ee1ddd'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReportSanitizer.java' = '57398f287f6f53e2cfd748c0b352bd5c64cb1060a348c35612a48b5e04c8f95c'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationResult.java' = 'fba58990ff50db7f5eb3847bcb658aed2396e36587626575bf16df75633c6862'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationRule.java' = '59d154b343e3cb7e514c9a69a9b7ed4e7eda542f7b013ffcc091f1ed67fe5278'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationRuleRegistry.java' = 'acedf35b82b0678d0ad3c62199b14accbb2481f044d935276146467deab91bbf'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationSummary.java' = '0c8e87a47a5f1c3b65f7b035754794582145afe94456c334adfceba88d885dda'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationVocabulary.java' = 'a97a75a63a06aad6c583375615e3f16d247dbd8ca3ad29a66645fdaca1920080'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/ColetasCharacterizationRule.java' = 'a11d32e72dadbe8d77bbffc6b86acc9b0bd2edb2de6a41f8beea00188918fbad'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CotacoesCharacterizationRule.java' = '69e97b4dddb70f509b9d0eb0d4b3409392cb432c2cda572e7ae84c447c4113da'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/InMemoryDataExportCharacterizationAdapter.java' = '1b9783e423b8348d4ff036e5a357ac6bcdc197158b20e15cb29ba25e35071cbc'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/InMemoryGraphQlCharacterizationAdapter.java' = 'b4caf4fe77fe5ecf1ac9d4323502065a95fcd7a14d2397fc7f0de568c601d39d'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/ManifestosCharacterizationRule.java' = 'a63f08c313f611fa46460d88bedf153428a456183b604977e8c3f1efea973412'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/StrictUtf8JsonLoader.java' = 'b496a2531ae2d84a652f4e3bffc7d833f6a25b0b34594fec8badf42ae173f3b1'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/StrictUtf8JsonLoaderTest.java' = '2064c3de1491e1c306247dc65c490caf757d45d1fa72513c41a49f38763371ee'
    'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/UsuariosCharacterizationRule.java' = 'bb1449730353dfb0cc56f3adf943d03edfa151fb9b2532da133717b3b2f37526'
    'src/test/resources/contracts/v2-012/fixtures/coletas-6908.synthetic.json' = '3faae2d7ab68da5a6431012a1d6bd1eb539752e6d003d6e1997d31b6215355b4'
    'src/test/resources/contracts/v2-012/fixtures/cotacoes-6906.synthetic.json' = '49a5cf0f4f3be4ac748b4b84df11a44c4028309d93e81c00aa0cef68ead61449'
    'src/test/resources/contracts/v2-012/fixtures/manifestos-6399.synthetic.json' = '785ad1c6041105eb8e23941e9923fb57c4eaf58859515b14e66448e45dba0231'
    'src/test/resources/contracts/v2-012/fixtures/usuarios-individual.synthetic.json' = 'b0f917914e07589dbacbf5d12a7478b76feb3a930126f68fec233bc23eedbfc6'
    'src/test/resources/contracts/v2-012/profiles/coletas-6908.profile.json' = 'd0ffb7c54a935c3a62a9501791f6a8119c9f3ec8bc72d7171066778510e95a97'
    'src/test/resources/contracts/v2-012/profiles/cotacoes-6906.profile.json' = '0aff236196bae11046ecb4929096555fb9c86dd133deaaa84351d78a90027e3b'
    'src/test/resources/contracts/v2-012/profiles/manifestos-6399.profile.json' = '2774893397b0ff7b5b881d021f9f734a092040dbfa1dc47bd0f60a21eb8a5da9'
    'src/test/resources/contracts/v2-012/profiles/usuarios-individual.profile.json' = '171b965c011d1f482b35fdb9e4dff60a7798d90ee83b96d084183d9c542fae2e'
}

function Get-RepositoryPath([string]$relativePath) {
    return Join-Path $repositoryRoot $relativePath
}

function Get-Sha256([string]$literalPath) {
    return (Get-FileHash -LiteralPath $literalPath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-StrictUtf8([string]$literalPath, [int]$maximumBytes) {
    $bytes = [System.IO.File]::ReadAllBytes($literalPath)
    if ($bytes.Length -gt $maximumBytes) { throw 'Q_FND_02_SIZE_LIMIT' }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw 'Q_FND_02_UTF8_BOM'
    }
    $encoding = [System.Text.UTF8Encoding]::new($false, $true)
    $text = $encoding.GetString($bytes)
    if ($text.Contains([char]0) -or $text.Contains([char]0xFFFD)) { throw 'Q_FND_02_UTF8_INVALID' }
    return $text
}

function Read-StrictJson([string]$literalPath, [int]$maximumBytes) {
    $text = Read-StrictUtf8 $literalPath $maximumBytes
    return ConvertFrom-StrictJsonText $text
}

function Assert-NoDuplicateJsonProperties($element) {
    if ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) { throw 'Q_FND_02_JSON_DUPLICATE_KEY' }
            Assert-NoDuplicateJsonProperties $property.Value
        }
    } elseif ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $element.EnumerateArray()) { Assert-NoDuplicateJsonProperties $item }
    }
}

function ConvertFrom-StrictJsonText([string]$text) {
    $document = [System.Text.Json.JsonDocument]::Parse($text)
    try {
        Assert-NoDuplicateJsonProperties $document.RootElement
    } finally {
        $document.Dispose()
    }
    return $text | ConvertFrom-Json -Depth 100
}

function Assert-Equal($actual, $expected, [string]$reason) {
    if ($null -eq $actual -or $null -eq $expected) { throw $reason }
    if (($actual -is [System.Collections.IEnumerable] -and $actual -isnot [string]) -or
        ($expected -is [System.Collections.IEnumerable] -and $expected -isnot [string])) {
        throw $reason
    }
    if ($actual.GetType() -ne $expected.GetType() -or -not [object]::Equals($actual, $expected)) {
        throw $reason
    }
}

function Assert-Integer($actual, [long]$expected, [string]$reason) {
    if ($null -eq $actual -or
        ($actual -is [System.Collections.IEnumerable] -and $actual -isnot [string]) -or
        $actual -is [bool] -or
        $actual.GetType() -notin @([byte], [sbyte], [int16], [uint16], [int32], [uint32], [int64])) {
        throw $reason
    }
    if ([long]$actual -ne $expected) { throw $reason }
}

function Assert-ExactSet($actual, $expected, [string]$reason) {
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($value in @($actual)) { [void]$actualSet.Add([string]$value) }
    foreach ($value in @($expected)) { [void]$expectedSet.Add([string]$value) }
    if (-not $actualSet.SetEquals($expectedSet)) {
        throw $reason
    }
}

function Assert-UniqueExactSet($actual, $expected, [string]$reason) {
    $actualValues = @($actual | ForEach-Object { [string]$_ })
    $uniqueValues = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($value in $actualValues) {
        if (-not $uniqueValues.Add($value)) { throw $reason }
    }
    Assert-ExactSet $actualValues $expected $reason
}

function Assert-NoCaseCollisions($actual, [string]$reason) {
    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($value in @($actual | ForEach-Object { [string]$_ })) {
        if (-not $seen.Add($value)) { throw $reason }
    }
}

function Assert-ObjectSchema($object, $expectedProperties, [string]$reason) {
    if ($null -eq $object) { throw $reason }
    Assert-UniqueExactSet @($object.PSObject.Properties.Name) $expectedProperties $reason
}

function Get-Q01AggregateSha256 {
    $paths = @($q01Lock.Keys)
    [Array]::Sort($paths, [System.StringComparer]::OrdinalIgnoreCase)
    $builder = [System.Text.StringBuilder]::new()
    foreach ($relativePath in $paths) {
        [void]$builder.Append($q01Lock[$relativePath]).Append('  ').Append($relativePath).Append("`n")
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($builder.ToString())
    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

try {
    try {
        [void](ConvertFrom-StrictJsonText '{"duplicate":1,"duplicate":2}')
        throw 'Q_FND_02_JSON_SELF_TEST'
    } catch {
        if ($_.Exception.Message -cne 'Q_FND_02_JSON_DUPLICATE_KEY') { throw 'Q_FND_02_JSON_SELF_TEST' }
    }
    try {
        Assert-ObjectSchema ([pscustomobject]@{ expected = 1; rogue = 2 }) @('expected') 'Q_FND_02_SCHEMA_CLOSED'
        throw 'Q_FND_02_SCHEMA_SELF_TEST'
    } catch {
        if ($_.Exception.Message -cne 'Q_FND_02_SCHEMA_CLOSED') { throw 'Q_FND_02_SCHEMA_SELF_TEST' }
    }
    try {
        $arrayScalar = ConvertFrom-StrictJsonText '{"value":[]}'
        Assert-Equal $arrayScalar.value 'EXPECTED_SCALAR' 'Q_FND_02_SCALAR_TYPE'
        throw 'Q_FND_02_SCALAR_SELF_TEST'
    } catch {
        if ($_.Exception.Message -cne 'Q_FND_02_SCALAR_TYPE') { throw 'Q_FND_02_SCALAR_SELF_TEST' }
    }
    Assert-Equal $q01Lock.Count 43 'Q_FND_01_LOCK_COUNT'
    foreach ($entry in $q01Lock.GetEnumerator()) {
        $path = Get-RepositoryPath $entry.Key
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Q_FND_01_BYTE_DRIFT' }
        Assert-Equal (Get-Sha256 $path) $entry.Value 'Q_FND_01_BYTE_DRIFT'
    }

    $q01JavaRoot = Get-RepositoryPath 'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao'
    $actualAllJava = @(Get-ChildItem -LiteralPath $q01JavaRoot -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
    })
    $expectedAllJava = @($q01Lock.Keys | Where-Object { $_ -match '^src/test/java/.+\.java$' }) + $expectedQfnd02JavaFiles
    Assert-UniqueExactSet $actualAllJava $expectedAllJava 'Q_FND_01_SHADOW_OR_OVERWRITE'
    Assert-NoCaseCollisions $actualAllJava 'Q_FND_01_SHADOW_OR_OVERWRITE'
    $actualAllResources = @(Get-ChildItem -LiteralPath (Get-RepositoryPath 'src/test/resources/contracts/v2-012') -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
    })
    $expectedAllResources = @($q01Lock.Keys | Where-Object { $_ -match '^src/test/resources/contracts/v2-012/' }) + $expectedQfnd02ResourceFiles
    Assert-UniqueExactSet $actualAllResources $expectedAllResources 'Q_FND_01_SHADOW_OR_OVERWRITE'
    Assert-NoCaseCollisions $actualAllResources 'Q_FND_01_SHADOW_OR_OVERWRITE'

    $manifestPath = Get-RepositoryPath $manifestRelativePath
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        Write-Output 'Q_FND_02 status=FAIL reason=Q_FND_02_MANIFEST_MISSING'
        exit 1
    }

    $manifestHashPath = Get-RepositoryPath $manifestHashRelativePath
    if (-not (Test-Path -LiteralPath $manifestHashPath -PathType Leaf)) { throw 'Q_FND_02_MANIFEST_HASH_MISSING' }
    $manifest = Read-StrictJson $manifestPath 524288
    Assert-ObjectSchema $manifest @('schemaVersion','task','outcome','governanceArtifacts','q01Lock','foundation','canonicalBindings','profiles','mutations','openRoutes','prohibitedScope') 'Q_FND_02_SCHEMA_CLOSED'
    Assert-ObjectSchema $manifest.task @('route','block','taskId') 'Q_FND_02_SCHEMA_CLOSED'
    Assert-ObjectSchema $manifest.q01Lock @('fileCount','aggregateSha256','ordering','lineFormat','files') 'Q_FND_02_SCHEMA_CLOSED'
    Assert-ObjectSchema $manifest.foundation @('entityProfileCount','sourceChannelCount','profileStatus','gateStatus','providerEvidence','networkAccess','databaseAccess','runtimeExecution','providerExecution','outputRoot') 'Q_FND_02_SCHEMA_CLOSED'
    Assert-ObjectSchema $manifest.mutations @('cataloged','executedBy','allFailClosed') 'Q_FND_02_SCHEMA_CLOSED'
    foreach ($item in @($manifest.governanceArtifacts)) { Assert-ObjectSchema $item @('role','path','sha256') 'Q_FND_02_SCHEMA_CLOSED' }
    foreach ($item in @($manifest.q01Lock.files)) { Assert-ObjectSchema $item @('path','sha256') 'Q_FND_02_SCHEMA_CLOSED' }
    foreach ($item in @($manifest.canonicalBindings)) { Assert-ObjectSchema $item @('role','path','sha256') 'Q_FND_02_SCHEMA_CLOSED' }
    foreach ($item in @($manifest.profiles)) {
        Assert-ObjectSchema $item @('profileId','entity','channelCount','channelIds','profilePath','profileSha256','fixturePath','fixtureSha256','profileStatus','gateStatus') 'Q_FND_02_SCHEMA_CLOSED'
    }
    $declaredManifestHash = (Read-StrictUtf8 $manifestHashPath 256).Trim().ToLowerInvariant()
    Assert-Equal $declaredManifestHash (Get-Sha256 $manifestPath) 'Q_FND_02_MANIFEST_HASH_DRIFT'
    Assert-Equal $manifest.schemaVersion 'V2_012_Q_FND_02_V1' 'Q_FND_02_SCHEMA_VERSION'
    Assert-Equal $manifest.task.route 'Q-FND-02' 'Q_FND_02_ROUTE'
    Assert-Integer $manifest.task.block 48 'Q_FND_02_BLOCK'
    Assert-Equal $manifest.task.taskId 'V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE' 'Q_FND_02_TASK'
    Assert-Equal $manifest.outcome 'FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES' 'Q_FND_02_OUTCOME'
    $governanceArtifacts = @($manifest.governanceArtifacts)
    Assert-Equal $governanceArtifacts.Count 3 'Q_FND_02_GOVERNANCE_BINDING'
    Assert-UniqueExactSet @($governanceArtifacts.role) @($expectedGovernanceArtifacts.Keys) 'Q_FND_02_GOVERNANCE_BINDING'
    foreach ($artifact in $governanceArtifacts) {
        $expectedArtifact = $expectedGovernanceArtifacts[[string]$artifact.role]
        Assert-Equal $artifact.path $expectedArtifact[0] 'Q_FND_02_GOVERNANCE_BINDING'
        Assert-Equal $artifact.sha256 $expectedArtifact[1] 'Q_FND_02_GOVERNANCE_BINDING'
        Assert-Equal (Get-Sha256 (Get-RepositoryPath $artifact.path)) $expectedArtifact[1] 'Q_FND_02_GOVERNANCE_BINDING'
    }
    $manifestLockFiles = @($manifest.q01Lock.files)
    Assert-Integer $manifest.q01Lock.fileCount 43 'Q_FND_01_LOCK_COUNT'
    Assert-Equal $manifestLockFiles.Count 43 'Q_FND_01_LOCK_COUNT'
    Assert-UniqueExactSet @($manifestLockFiles.path) @($q01Lock.Keys) 'Q_FND_01_LOCK_PATHS'
    foreach ($locked in $manifestLockFiles) {
        Assert-Equal $locked.sha256 $q01Lock[[string]$locked.path] 'Q_FND_01_LOCK_HASH'
    }
    $expectedAggregate = 'ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a'
    Assert-Equal $manifest.q01Lock.aggregateSha256 $expectedAggregate 'Q_FND_01_AGGREGATE_DRIFT'
    Assert-Equal (Get-Q01AggregateSha256) $expectedAggregate 'Q_FND_01_AGGREGATE_DRIFT'
    Assert-Equal $manifest.q01Lock.ordering 'ORDINAL_IGNORE_CASE' 'Q_FND_01_AGGREGATE_DRIFT'
    Assert-Equal $manifest.q01Lock.lineFormat 'sha256__two_spaces__path__lf' 'Q_FND_01_AGGREGATE_DRIFT'
    foreach ($flag in @('networkAccess', 'databaseAccess', 'runtimeExecution', 'providerExecution')) {
        if ($manifest.foundation.$flag -isnot [bool] -or $manifest.foundation.$flag) { throw 'Q_FND_02_OFFLINE_BOUNDARY' }
    }
    Assert-Equal $manifest.foundation.profileStatus 'PREPARED_NOT_EXECUTED' 'Q_FND_02_PROFILE_EXECUTED'
    Assert-Equal $manifest.foundation.gateStatus 'ORACLE_REQUIRED' 'Q_FND_02_ORACLE_GATE'
    Assert-Equal $manifest.foundation.providerEvidence 'NOT_EXECUTED' 'Q_FND_02_PROVIDER_EVIDENCE'
    Assert-Equal $manifest.foundation.outputRoot 'target/v2-012-characterization/q-fnd-02' 'Q_FND_02_OUTPUT_ROOT'

    Assert-Integer $manifest.foundation.entityProfileCount 2 'Q_FND_02_PROFILE_COUNT'
    Assert-Integer $manifest.foundation.sourceChannelCount 3 'Q_FND_02_CHANNEL_COUNT'
    $actualJavaFiles = @(Get-ChildItem -LiteralPath (Get-RepositoryPath 'src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/qfnd02') -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
    })
    Assert-UniqueExactSet $actualJavaFiles $expectedQfnd02JavaFiles 'Q_FND_02_ROGUE_ARTIFACT'
    Assert-NoCaseCollisions $actualJavaFiles 'Q_FND_02_ROGUE_ARTIFACT'
    foreach ($mainJavaFile in @(Get-ChildItem -LiteralPath (Get-RepositoryPath 'src/main/java') -Recurse -File -Filter '*.java')) {
        $mainRelativePath = [System.IO.Path]::GetRelativePath($repositoryRoot, $mainJavaFile.FullName).Replace('\', '/')
        if ($mainRelativePath.Contains('qfnd02', [System.StringComparison]::OrdinalIgnoreCase) -or
            (Read-StrictUtf8 $mainJavaFile.FullName 1048576).Contains('qfnd02', [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Q_FND_02_RUNTIME_ARTIFACT'
        }
    }
    $actualResourceFiles = @(Get-ChildItem -LiteralPath (Get-RepositoryPath 'src/test/resources/contracts/v2-012/extensions/q-fnd-02') -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
    })
    Assert-UniqueExactSet $actualResourceFiles $expectedQfnd02ResourceFiles 'Q_FND_02_ROGUE_ARTIFACT'
    Assert-NoCaseCollisions $actualResourceFiles 'Q_FND_02_ROGUE_ARTIFACT'
    foreach ($resourceEntry in $expectedQfnd02ResourceHashes.GetEnumerator()) {
        Assert-Equal (Get-Sha256 (Get-RepositoryPath $resourceEntry.Key)) $resourceEntry.Value 'Q_FND_02_RESOURCE_HASH'
    }
    $catalogRoot = Get-RepositoryPath 'docs/catalogos/caracterizacao-v2-012'
    $catalogFiles = @(Get-ChildItem -LiteralPath $catalogRoot -Recurse -File | ForEach-Object {
        [System.IO.Path]::GetRelativePath($catalogRoot, $_.FullName).Replace('\', '/')
    })
    Assert-UniqueExactSet $catalogFiles @('README.md', 'manifesto.json', 'q-fnd-02/README.md', 'q-fnd-02/manifesto.json', 'q-fnd-02/manifesto.sha256') 'Q_FND_02_ROGUE_ARTIFACT'
    Assert-NoCaseCollisions $catalogFiles 'Q_FND_02_ROGUE_ARTIFACT'

    $expectedBindings = [ordered]@{
        'FRETES_CONTRACT' = @('docs/catalogos/contratos-primeira-onda/manifesto.json', 'd7910ff0e3d0f78bee57aaad5178561db8bcb0d0d253448531b606bcef45950c')
        'FRETES_IDENTITY' = @('docs/catalogos/identidade-primeira-onda/manifesto.json', '15348b0074af78a29867b33a6333f6d7d20e1b3ee31300162d26dc54c7c33c2b')
        'FRETES_DECISION' = @('docs/catalogos/fretes-v2-011/decisao-v01.json', 'b6a8ea2744956f1ef5a2407abd57609c8a353930952a5db49996eff5b3f0c318')
        'FRETES_SHADOW' = @('database/manifest/fretes-shadow-vertical.json', '303c5a6977c8fd52067b2954fee0a1d686f35fd1a78a330762050576596b700c')
        'LOCALIZACAO_CONTRACT' = @('docs/catalogos/contratos-esl-8656/manifesto.json', '6ed94fb98993d459ed1b9ced49da7cf93b010acd76a8e96e6fa498de62676494')
        'LOCALIZACAO_IDENTITY' = @('docs/catalogos/identidade-localizacao-cargas/manifesto.json', 'ade63c2c0483d94a668b194f7f8c76e63795aa188f02e93657e53ba88c8958de')
        'LOCALIZACAO_DECISION' = @('docs/catalogos/localizacao-cargas-v2-028/decisao-v01.json', 'b837d1d6722d48976c9021a418dceccc8a176fc0497aa6a1f2e71889810d1e13')
        'LOCALIZACAO_SHADOW' = @('database/manifest/localizacao-cargas-shadow-vertical.json', 'f8dad82685ef4c5b290922af98a20c0cf50de07a07e5e4d9ea5349fca81708e2')
    }
    $bindings = @($manifest.canonicalBindings)
    Assert-Equal $bindings.Count 8 'Q_FND_02_BINDING_COUNT'
    Assert-UniqueExactSet @($bindings.role) @($expectedBindings.Keys) 'Q_FND_02_BINDING_SET'
    foreach ($binding in $bindings) {
        $expectedBinding = $expectedBindings[[string]$binding.role]
        Assert-Equal $binding.path $expectedBinding[0] 'Q_FND_02_BINDING_PATH'
        Assert-Equal $binding.sha256 $expectedBinding[1] 'Q_FND_02_BINDING_HASH'
        Assert-Equal (Get-Sha256 (Get-RepositoryPath $binding.path)) $expectedBinding[1] 'Q_FND_02_BINDING_HASH'
    }

    $expectedProfiles = @{
        'V2_012_FRETES_6389' = @('FRETES', 2, 'fretes-6389.profile.json', 'fretes-6389.synthetic.json', @('FRETES_DATA_EXPORT_6389', 'FRETES_GRAPHQL_SIDECAR'))
        'V2_012_LOCALIZACAO_8656_DATA_EXPORT' = @('LOCALIZACAO_CARGAS', 1, 'localizacao-8656.profile.json', 'localizacao-8656.synthetic.json', @('LOCALIZACAO_DATA_EXPORT_8656'))
    }
    Assert-Equal @($manifest.profiles).Count 2 'Q_FND_02_PROFILE_COUNT'
    Assert-UniqueExactSet @($manifest.profiles.profileId) @($expectedProfiles.Keys) 'Q_FND_02_PROFILE_SET'
    $loadedProfiles = @{}
    $loadedFixtures = @{}
    foreach ($profileBinding in @($manifest.profiles)) {
        $expected = $expectedProfiles[[string]$profileBinding.profileId]
        if ($null -eq $expected) { throw 'Q_FND_02_UNKNOWN_PROFILE' }
        Assert-Equal $profileBinding.entity $expected[0] 'Q_FND_02_ENTITY'
        Assert-Integer $profileBinding.channelCount $expected[1] 'Q_FND_02_CHANNEL_COUNT'
        Assert-UniqueExactSet @($profileBinding.channelIds) @($expected[4]) 'Q_FND_02_CHANNEL_SET'
        foreach ($bindingKind in @('profile', 'fixture')) {
            $fileName = if ($bindingKind -eq 'profile') { $expected[2] } else { $expected[3] }
            $relativePath = "src/test/resources/contracts/v2-012/extensions/q-fnd-02/${bindingKind}s/$fileName"
            Assert-Equal $profileBinding."${bindingKind}Path" $relativePath 'Q_FND_02_RESOURCE_PATH'
            $resourcePath = Get-RepositoryPath $relativePath
            if (-not (Test-Path -LiteralPath $resourcePath -PathType Leaf)) { throw 'Q_FND_02_RESOURCE_MISSING' }
            Assert-Equal $profileBinding."${bindingKind}Sha256" (Get-Sha256 $resourcePath) 'Q_FND_02_RESOURCE_HASH'
        }
        Assert-Equal $profileBinding.profileStatus 'PREPARED_NOT_EXECUTED' 'Q_FND_02_PROFILE_EXECUTED'
        Assert-Equal $profileBinding.gateStatus 'ORACLE_REQUIRED' 'Q_FND_02_ORACLE_GATE'
        $loadedProfiles[$profileBinding.profileId] = Read-StrictJson (Get-RepositoryPath $profileBinding.profilePath) 524288
        $loadedFixtures[$profileBinding.profileId] = Read-StrictJson (Get-RepositoryPath $profileBinding.fixturePath) 524288
        $loadedProfile = $loadedProfiles[$profileBinding.profileId]
        $loadedFixture = $loadedFixtures[$profileBinding.profileId]
        Assert-ObjectSchema $loadedProfile @(
            'schemaVersion','profileId','entity','channels','explicitScopeRequired','relationshipEnabled',
            'sweepEnabled','publicationEnabled','bootstrapEnabled','parityEnabled','statusBranchNicknameState',
            'freshnessPolicy','knownTerminalStatuses','decisionPolicies','numericPolicies','absencePolicy',
            'limits','profileStatus','gateStatus','providerEvidence'
        ) 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
        Assert-ObjectSchema $loadedProfile.limits @(
            'maximumBytes','maximumRows','maximumPages','maximumPaths','maximumDepth','maximumPageSize',
            'maximumMicrobatch','maximumGraphQlEdges','terminalContinuationProbePages'
        ) 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
        Assert-Equal $loadedProfile.schemaVersion 'V2_012_Q_FND_02_PROFILE_V1' 'Q_FND_02_SCHEMA_VERSION'
        Assert-Equal $loadedProfile.profileId $profileBinding.profileId 'Q_FND_02_PROFILE_BINDING'
        Assert-UniqueExactSet @($loadedProfile.channels.channelId) @($expected[4]) 'Q_FND_02_CHANNEL_SET'
        foreach ($loadedChannel in @($loadedProfile.channels)) {
            Assert-ObjectSchema $loadedChannel @(
                'channelId','sourceProfile','contract','sourceKey','expectedPaths','fieldTypes','presenceByPath',
                'syntheticOnly','observationOnly','publicationBlocked','rootOrFreshnessAuthority',
                'updatedAtFreshness','shortPageIsTerminal','completenessProven','snapshotProven',
                'filterPolicy','temporalTranslation'
            ) 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
            Assert-ObjectSchema $loadedChannel.contract @('contractId','contractVersion','releaseFingerprint','identityFingerprint') 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
            Assert-ObjectSchema $loadedChannel.sourceKey @('path','wireType','typeTagged') 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
            Assert-ObjectSchema $loadedChannel.fieldTypes @($loadedChannel.expectedPaths) 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
            Assert-ObjectSchema $loadedChannel.presenceByPath @($loadedChannel.expectedPaths) 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
        }
        foreach ($numericPolicy in @($loadedProfile.numericPolicies.PSObject.Properties)) {
            Assert-ObjectSchema $numericPolicy.Value @('grammar','precision','scale','invalidPolicy') 'Q_FND_02_PROFILE_SCHEMA_CLOSED'
        }
        Assert-ObjectSchema $loadedFixture @(
            'schemaVersion','fixtureMarker','profileId','evidenceClassification','providerEvidence','channels','scenarios'
        ) 'Q_FND_02_FIXTURE_SCHEMA_CLOSED'
        Assert-Equal $loadedFixture.schemaVersion 'V2_012_Q_FND_02_FIXTURE_V1' 'Q_FND_02_SCHEMA_VERSION'
        Assert-Equal $loadedFixture.profileId $profileBinding.profileId 'Q_FND_02_FIXTURE_BINDING'
        Assert-Equal $loadedFixture.evidenceClassification 'SYNTHETIC_FIXTURE' 'Q_FND_02_FIXTURE_BINDING'
        Assert-Equal $loadedFixture.providerEvidence 'NOT_EXECUTED' 'Q_FND_02_PROVIDER_EVIDENCE'
        Assert-UniqueExactSet @($loadedFixture.channels.channelId) @($expected[4]) 'Q_FND_02_CHANNEL_SET'
        foreach ($observedChannel in @($loadedFixture.channels)) {
            Assert-ObjectSchema $observedChannel @(
                'channelId','observedPaths','observedTypes','presenceByPath','sourceKeyPath','sourceKeyTypeTagged',
                'shortPageIsTerminal','completenessProven','snapshotProven','relationshipEnabled',
                'publicationEnabled','sweepEnabled','rootOrFreshnessAuthority'
            ) 'Q_FND_02_FIXTURE_SCHEMA_CLOSED'
            Assert-ObjectSchema $observedChannel.observedTypes @($observedChannel.observedPaths) 'Q_FND_02_FIXTURE_SCHEMA_CLOSED'
            Assert-ObjectSchema $observedChannel.presenceByPath @($observedChannel.observedPaths) 'Q_FND_02_FIXTURE_SCHEMA_CLOSED'
        }
        foreach ($scenario in @($loadedFixture.scenarios)) {
            Assert-ObjectSchema $scenario @('scenarioId','mutation','expectedReason') 'Q_FND_02_FIXTURE_SCHEMA_CLOSED'
        }
    }

    $fretes = $loadedProfiles['V2_012_FRETES_6389']
    $localizacao = $loadedProfiles['V2_012_LOCALIZACAO_8656_DATA_EXPORT']
    $fretesPaths = @('/id', '/updated_at', '/reference_number', '/fit_p_m_pck_sequence_code', '/corporation_sequence_number', '/finished_at', '/fit_dpn_performance_finished_at')
    $sidecarPaths = @('/freight/edges/node/id', '/freight/edges/node/accountingCreditId', '/freight/edges/node/accountingCreditInstallmentId', '/freight/edges/node/referenceNumber', '/freight/edges/node/cte/key', '/freight/edges/node/total', '/freight/edges/node/corporationSequenceNumber', '/freight/edges/node/pickItemId', '/freight/pageInfo/hasNextPage', '/freight/pageInfo/endCursor')
    $localizacaoPaths = @('/corporation_sequence_number', '/type', '/service_at', '/invoices_volumes', '/taxed_weight', '/invoices_value', '/total', '/service_type', '/fit_crn_psn_nickname', '/fit_dpn_delivery_prediction_at', '/fit_dyn_name', '/fit_dyn_drt_nickname', '/fit_fsn_name', '/fit_fln_status', '/fit_fln_cln_nickname', '/fit_o_n_name', '/fit_o_n_drt_nickname')
    $fretesDe = @($fretes.channels | Where-Object channelId -eq 'FRETES_DATA_EXPORT_6389')[0]
    $sidecar = @($fretes.channels | Where-Object channelId -eq 'FRETES_GRAPHQL_SIDECAR')[0]
    $localizacaoDe = @($localizacao.channels)[0]
    Assert-UniqueExactSet @($fretesDe.expectedPaths) $fretesPaths 'Q_FND_02_FRETES_PATHS'
    Assert-UniqueExactSet @($sidecar.expectedPaths) $sidecarPaths 'Q_FND_02_SIDECAR_PATHS'
    Assert-UniqueExactSet @($localizacaoDe.expectedPaths) $localizacaoPaths 'Q_FND_02_LOCALIZACAO_PATHS'
    Assert-Equal $fretesDe.contract.contractId 'dataexport-6389' 'Q_FND_02_FRETES_CONTRACT'
    Assert-Equal $fretesDe.contract.contractVersion '2026-08-31.v2-025a.1' 'Q_FND_02_FRETES_CONTRACT'
    Assert-Equal $fretesDe.contract.releaseFingerprint '23aef4e4e03488d291d3990bdba813800e3ebb8c8be18e766a788daf9741ec15' 'Q_FND_02_FRETES_CONTRACT'
    Assert-Equal $fretesDe.contract.identityFingerprint '4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723' 'Q_FND_02_FRETES_IDENTITY'
    Assert-Equal $fretesDe.sourceKey.path '/id' 'Q_FND_02_FRETES_IDENTITY'
    Assert-Equal $fretesDe.sourceKey.wireType 'INTEGER' 'Q_FND_02_FRETES_IDENTITY'
    if (-not $fretesDe.sourceKey.typeTagged) { throw 'Q_FND_02_FRETES_IDENTITY' }
    Assert-Equal $sidecar.sourceProfile 'GRAPHQL_SIDECAR' 'Q_FND_02_SIDECAR_ISOLATION'
    Assert-Equal $sidecar.contract.contractId 'FREIGHTS_TRANSITIONAL_SIDECAR' 'Q_FND_02_SIDECAR_ISOLATION'
    foreach ($flag in @('syntheticOnly', 'observationOnly', 'publicationBlocked')) { Assert-Equal $sidecar.$flag $true 'Q_FND_02_SIDECAR_ISOLATION' }
    Assert-Equal $sidecar.rootOrFreshnessAuthority $false 'Q_FND_02_SIDECAR_AUTHORITY'
    Assert-Equal $localizacaoDe.contract.contractId 'dataexport-8656' 'Q_FND_02_LOCALIZACAO_CONTRACT'
    Assert-Equal $localizacaoDe.contract.contractVersion '2026-09-04.v2-025b.1' 'Q_FND_02_LOCALIZACAO_CONTRACT'
    Assert-Equal $localizacaoDe.contract.releaseFingerprint 'da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24' 'Q_FND_02_LOCALIZACAO_CONTRACT'
    Assert-Equal $localizacaoDe.contract.identityFingerprint '14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0' 'Q_FND_02_LOCALIZACAO_IDENTITY'
    Assert-Equal $localizacaoDe.sourceKey.path '/corporation_sequence_number' 'Q_FND_02_LOCALIZACAO_IDENTITY'
    if (@($localizacaoDe.expectedPaths) -contains '/sequence_number') { throw 'Q_FND_02_LOCALIZACAO_IDENTITY' }
    Assert-Equal $localizacao.statusBranchNicknameState 'ABSENT_UNSOURCED_LEGACY' 'Q_FND_02_LOC_07'
    Assert-UniqueExactSet @($fretes.decisionPolicies.PSObject.Properties.Name) @('FRE-01','FRE-02','FRE-03','FRE-04','FRE-05','FRE-06','FRE-07') 'Q_FND_02_FRE_RULES'
    Assert-UniqueExactSet @($localizacao.decisionPolicies.PSObject.Properties.Name) @('LOC-01','LOC-02','LOC-03','LOC-04','LOC-05','LOC-06','LOC-07') 'Q_FND_02_LOC_RULES'
    foreach ($profile in @($fretes, $localizacao)) {
        Assert-Equal $profile.profileStatus 'PREPARED_NOT_EXECUTED' 'Q_FND_02_PROFILE_EXECUTED'
        Assert-Equal $profile.gateStatus 'ORACLE_REQUIRED' 'Q_FND_02_ORACLE_GATE'
        Assert-Equal $profile.providerEvidence 'NOT_EXECUTED' 'Q_FND_02_PROVIDER_EVIDENCE'
        Assert-Equal $profile.absencePolicy 'INCREMENTAL_ABSENCE_NEVER_DEACTIVATES_SWEEP_DISABLED' 'Q_FND_02_ABSENCE_POLICY'
        Assert-Equal $profile.explicitScopeRequired $true 'Q_FND_02_SCOPE_REQUIRED'
        foreach ($unsafeFlag in @('relationshipEnabled','sweepEnabled','publicationEnabled','bootstrapEnabled','parityEnabled')) { Assert-Equal $profile.$unsafeFlag $false 'Q_FND_02_UNSAFE_CAPABILITY' }
        foreach ($channel in @($profile.channels)) {
            Assert-UniqueExactSet @($channel.fieldTypes.PSObject.Properties.Name) @($channel.expectedPaths) 'Q_FND_02_FIELD_TYPE_SCOPE'
            Assert-UniqueExactSet @($channel.presenceByPath.PSObject.Properties.Name) @($channel.expectedPaths) 'Q_FND_02_PRESENCE_SCOPE'
            foreach ($path in @($channel.expectedPaths)) { Assert-UniqueExactSet @($channel.presenceByPath.$path) @('ABSENT','NULL','VALUE') 'Q_FND_02_PRESENCE_STATES' }
            foreach ($flag in @('publicationBlocked')) { Assert-Equal $channel.$flag $true 'Q_FND_02_OFFLINE_BOUNDARY' }
            foreach ($flag in @('updatedAtFreshness','shortPageIsTerminal','completenessProven','snapshotProven')) { Assert-Equal $channel.$flag $false 'Q_FND_02_COMPLETENESS_CLAIM' }
        }
        foreach ($limit in @('maximumPageSize','maximumMicrobatch','maximumGraphQlEdges')) { Assert-Integer $profile.limits.$limit 100 'Q_FND_02_LIMITS' }
        Assert-Integer $profile.limits.terminalContinuationProbePages 1 'Q_FND_02_LIMITS'
    }
    foreach ($path in $fretesPaths | Where-Object { $_ -ne '/id' }) { Assert-Equal $fretesDe.fieldTypes.$path 'UNVERIFIED_ORACLE_REQUIRED' 'Q_FND_02_PROVIDER_TYPE' }
    foreach ($path in $localizacaoPaths | Where-Object { $_ -ne '/corporation_sequence_number' }) { Assert-Equal $localizacaoDe.fieldTypes.$path 'UNVERIFIED_ORACLE_REQUIRED' 'Q_FND_02_PROVIDER_TYPE' }
    Assert-Integer $localizacao.numericPolicies.'/invoices_volumes'.precision 10 'Q_FND_02_NUMERIC_POLICY'
    Assert-Integer $localizacao.numericPolicies.'/invoices_volumes'.scale 0 'Q_FND_02_NUMERIC_POLICY'
    foreach ($path in @('/taxed_weight','/invoices_value','/total')) {
        Assert-Integer $localizacao.numericPolicies.$path.precision 38 'Q_FND_02_NUMERIC_POLICY'
        Assert-Integer $localizacao.numericPolicies.$path.scale 9 'Q_FND_02_NUMERIC_POLICY'
    }

    $allScenarios = @($loadedFixtures.Values | ForEach-Object { @($_.scenarios) })
    Assert-Equal $allScenarios.Count 48 'Q_FND_02_MUTATION_COUNT'
    Assert-UniqueExactSet @($allScenarios.scenarioId) @($allScenarios.scenarioId | Sort-Object -Unique) 'Q_FND_02_MUTATION_DUPLICATE'
    $decisionMutations = @(
        1..7 | ForEach-Object { 'PROFILE_DECISION_FRE-{0:D2}' -f $_ }
        1..7 | ForEach-Object { 'PROFILE_DECISION_LOC-{0:D2}' -f $_ }
    )
    Assert-UniqueExactSet @($allScenarios.mutation | Where-Object { $_ -like 'PROFILE_DECISION_*' }) $decisionMutations 'Q_FND_02_MUTATION_COVERAGE'
    $mutationBindings = @($allScenarios | ForEach-Object {
        $entity = if ($_.scenarioId -like 'SYNTH_FRE_*') { 'FRETES' } elseif ($_.scenarioId -like 'SYNTH_LOC_*') { 'LOCALIZACAO' } else { throw 'Q_FND_02_MUTATION_COVERAGE' }
        "$entity|$($_.mutation)"
    })
    Assert-UniqueExactSet $mutationBindings @($mutationBindings) 'Q_FND_02_MUTATION_DUPLICATE'
    Assert-Integer $manifest.mutations.cataloged 48 'Q_FND_02_MUTATION_COUNT'
    Assert-Equal $manifest.mutations.executedBy 'Qfnd02MutationTest' 'Q_FND_02_MUTATION_EXECUTOR'
    if ($manifest.mutations.allFailClosed -isnot [bool] -or -not $manifest.mutations.allFailClosed) { throw 'Q_FND_02_MUTATION_EXECUTOR' }

    Assert-UniqueExactSet @($manifest.openRoutes) @('Q-FRE-01', 'Q-LOC-01') 'Q_FND_02_OPEN_ROUTES'
    Assert-UniqueExactSet @($manifest.prohibitedScope) @('BOOTSTRAP', 'CROSSWALK', 'CUTOVER', 'PARITY', 'PUBLICATION', 'RELATION', 'SWEEP') 'Q_FND_02_PROHIBITED_SCOPE'

    $requiredArtifacts = @(
        'docs/adr/0029-extensao-aditiva-perfis-fretes-localizacao.md',
        'docs/catalogos/caracterizacao-v2-012/q-fnd-02/README.md',
        'docs/runbooks/v2-012-q-fnd-02-perfis-fretes-localizacao-sol.md'
    )
    foreach ($relativePath in $requiredArtifacts) {
        $path = Get-RepositoryPath $relativePath
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Q_FND_02_DOCUMENT_MISSING' }
        $document = Read-StrictUtf8 $path 524288
        if ($document -match '(?im)^\s*(curl|sqlcmd)\b|Invoke-WebRequest|jdbc:') { throw 'Q_FND_02_OFFLINE_BOUNDARY' }
    }

    if (-not $ArtifactsOnly) {
        $states = Read-StrictUtf8 (Get-RepositoryPath 'STATES.md') 4194304
        $trail = Read-StrictUtf8 (Get-RepositoryPath 'docs/runbooks/trilha-de-chats-gpt-5-6.md') 2097152
        if ($states -notmatch '(?m)^\s*- \[x\] \*\*V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE') { throw 'Q_FND_02_STATE_OPEN' }
        if ($trail -notmatch '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-FND-02 \| BLOCO=48 \|') { throw 'Q_FND_02_TRAIL_OPEN' }
    }

    Write-Output 'Q_FND_02 status=PASS lock=43 entityProfiles=2 sourceChannels=3 providerEvidence=NOT_EXECUTED catalogedMutations=48 executionGate=Qfnd02MutationTest_SEPARATE'
} catch {
    $reason = [string]$_.Exception.Message
    if ([string]::IsNullOrWhiteSpace($reason) -or $reason -notmatch '^Q_FND_(?:01|02)_[A-Z0-9_]+$') { $reason = 'Q_FND_02_VALIDATION_FAILED' }
    Write-Output "Q_FND_02 status=FAIL reason=$reason line=$($_.InvocationInfo.ScriptLineNumber)"
    exit 1
}

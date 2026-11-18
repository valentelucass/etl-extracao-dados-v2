param(
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Metadata preparation only. This script never starts Java, Maven, Python, Source, UI or SQL.
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$destination = [IO.Path]::GetFullPath($OutputDirectory)
if (-not [IO.Path]::IsPathFullyQualified($OutputDirectory) -or (Test-Path -LiteralPath $destination)) {
    throw 'FRESH_ABSOLUTE_UNIT_DIRECTORY_REQUIRED'
}
$ownedJava = @(
    'src/main/java/br/com/esl/etl/v2/bootstrap/Main.java'
    'src/main/java/br/com/esl/etl/v2/bootstrap/IndependentExportCommand.java'
    'src/test/java/br/com/esl/etl/v2/bootstrap/IndependentExportCommandTest.java'
)
foreach ($directory in @(
    'src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao'
    'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao'
)) {
    $ownedJava += @(Get-ChildItem -LiteralPath (Join-Path $repo $directory) -Filter '*.java' -File |
        Sort-Object Name | ForEach-Object { $directory + '/' + $_.Name })
}
$controls = @(
    'pom.xml'
    'config/checkstyle.xml'
    'src/main/resources/contracts/dataexport/fretes-independent-export-columns-v1.json'
    'scripts/validation/Prepare-IndependentExportOfflineUnit.ps1'
)
$testClasses = @(
    'br.com.esl.etl.v2.bootstrap.IndependentExportCommandTest'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactBinary64Test'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExactExportTableTest'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportFileLifecycleTest'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportCycleTest'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportResultTest'
    'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportValidationTest'
)
$pins = @()
$testMethods = 0
foreach ($relative in @($ownedJava) + @($controls)) {
    $file = Get-Item -LiteralPath (Join-Path $repo $relative)
    if ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'UNIT_LINK_REFUSED' }
    $pins += [ordered]@{
        file = $relative
        path = $file.FullName
        bytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    if ($relative.StartsWith('src/test/') -and $relative.EndsWith('.java')) {
        $testMethods += [regex]::Matches([IO.File]::ReadAllText($file.FullName), '@Test\b').Count
    }
}
$spotlessSelection = '-DspotlessFiles=' + ($ownedJava -join ',')
$unit = [ordered]@{
    schema = 'INDEPENDENT_EXPORT_ROOT_OFFLINE_UNIT_V1'
    preparedAt = [DateTimeOffset]::UtcNow.ToString('o')
    status = 'PREPARED_NOT_EXECUTED_NOT_APPROVED'
    repository = $repo
    requiredExecutor = 'Root Original; existing owner and physical admission required'
    authorityCreated = $false
    Source = 0
    HTTP = 0
    SQL = 0
    credentials = 0
    CIM = 0
    serviceChanges = 0
    canonicalControlChanges = 0
    limits = [ordered]@{
        originalJobBytes = 1073741824
        originalProcessBytes = 1073741824
        formatDeadlineSeconds = 60
        offlineTestDeadlineSeconds = 300
        javaHeapMaximum = '512m'
        testForks = 0
        externalRequests = 0
        businessFiles = 0
        recaptures = 0
        automaticRepeatsOfFailedUnit = 0
    }
    syntheticJUnitMethodsDeclared = $testMethods
    inputPins = $pins
    requiredEnvironment = @{ MAVEN_OPTS = '-Xmx512m -Dfile.encoding=UTF-8' }
    formatProcess = [ordered]@{
        executable = 'mvn'
        arguments = @('-o', $spotlessSelection, 'spotless:apply')
        scope = 'Only the exact ownedJava selection; preserve other working changes'
    }
    testProcess = [ordered]@{
        executable = 'mvn'
        arguments = @('-o', '-DforkCount=0', ('-Dtest=' + ($testClasses -join ',')), $spotlessSelection, 'test')
        scope = 'Only seven named synthetic test classes; no integration tests or package goal'
    }
    requiredSequence = @(
        'Root checks current authority, all pins and absence of conflicting editor before any execution.'
        'Format once in its own bounded admitted Original; stop on failure. No tests are claimed here.'
        'Formatting changes bytes. Root generates a fresh unit in another fresh directory and pins the final code before test execution.'
        'Run the exact offline test process once in a separately admitted bounded Original. Enforce time, memory and closed process/stream receipts.'
        'Require declared tests executed, no skipped/failed tests, formatter/checkstyle/compiler success and final source/resource pins unchanged.'
        'Do not broaden tests, skip quality gates, access business inputs or renew any budget automatically after refusal.'
    )
    currentVerification = @{
        compilation = 'NOT_EXECUTED'
        formatting = 'NOT_EXECUTED'
        tests = 'NOT_EXECUTED'
        staticDiffWhitespace = 'Separate preparation check only; not compilation or qualification'
        historicalQualifierDifferential = 'NOT_EXECUTED_FOR_THIS_JAVA_PORT'
        mainSourceAdoption = $false
        dashboardRoundtrip = $false
    }
}
[IO.Directory]::CreateDirectory($destination) | Out-Null
$path = Join-Path $destination 'offline-unit.PREPARED.json'
[IO.File]::WriteAllText($path, (($unit | ConvertTo-Json -Depth 8) -replace "`r`n", "`n") + "`n", [Text.UTF8Encoding]::new($false))
[pscustomobject]@{
    path = $path
    bytes = (Get-Item -LiteralPath $path).Length
    sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    testsExecuted = 0
    sourceCalls = 0
} | ConvertTo-Json

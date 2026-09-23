#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$requiredJavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$javaExecutable = Join-Path $requiredJavaHome 'bin\java.exe'
$mavenWrapper = Join-Path $repositoryRoot 'mvnw.cmd'
$validator = Join-Path $repositoryRoot 'scripts\validation\Test-V2050MeasurementFoundation.ps1'

if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw 'V2_050_JDK17_MISSING'
}
if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw 'V2_050_MAVEN_WRAPPER_MISSING'
}

$env:JAVA_HOME = $requiredJavaHome
$env:Path = "$(Join-Path $requiredJavaHome 'bin');$env:Path"

$tests = @(
    'Measurement*Test',
    'ManagedPageGaugeTest',
    'ExecutionWidePageRetentionMutantTest',
    'DataExportPageStreamerMultiscaleMeasurementTest',
    'GraphQlPageStreamerMultiscaleMeasurementTest'
) -join ','

Push-Location $repositoryRoot
try {
    & $mavenWrapper --offline --batch-mode --no-transfer-progress `
        '-Dv2.measurement.receipt=true' "-Dtest=$tests" test
    if ($LASTEXITCODE -ne 0) { throw 'V2_050_FOCUSED_TESTS_FAILED' }

    & pwsh -NoProfile -File $validator -ArtifactsOnly
    if ($LASTEXITCODE -ne 0) { throw 'V2_050_ARTIFACT_VALIDATION_FAILED' }
} finally {
    Pop-Location
}

[Console]::Out.WriteLine(
    'V2_050_MEASUREMENT_RUNNER status=PASS mode=OFFLINE scales=16,256,4096 recordsPerPage=8 receipt=target/v2-050-measurement/measurement-foundation-receipt.json')

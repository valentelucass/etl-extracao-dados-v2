#Requires -Version 7.5
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory = Join-Path $root ('target/runtime-local/' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$checks = [Collections.Generic.List[object]]::new()
# Closed list: no physical runner, SQL execution, operational identity or canonical Maven build.
foreach ($entry in @(
    @{script='Test-RuntimeQualification.ps1';arguments=@()},
    @{script='Test-RuntimeQualificationGuards.ps1';arguments=@()},
    @{script='Test-Bloco60Assertions.ps1';arguments=@()},
    @{script='Test-Bloco60ConcurrentPair.ps1';arguments=@()},
    @{script='Test-Bloco60Controllers.ps1';arguments=@()},
    @{script='Test-Bloco60SqlContract.ps1';arguments=@('-SelfTest')}
)) {
    $log = Join-Path $directory ($entry.script + '.log')
    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot $entry.script) @($entry.arguments) *> $log
    $exitCode = $LASTEXITCODE
    $checks.Add(@{script=$entry.script;exit=$exitCode;log=$log;sha256=(Get-FileHash $log).Hash.ToLowerInvariant()})
}
$result = @{passed=(@($checks | Where-Object exit -NE 0).Count -eq 0);checks=@($checks);
    layer='OFFLINE_SYNTHETIC_AND_STATIC_SQL';sqlExecuted=$false;physicalObserverQualified=$false;
    syntheticLoopbackPermitted=$true;evidence=$directory}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'result.json') -Encoding utf8NoBOM
$result | ConvertTo-Json -Depth 6
if (-not $result.passed) { throw 'RUNTIME_LOCAL_CHECK_FAILED_SEE_LOGS' }

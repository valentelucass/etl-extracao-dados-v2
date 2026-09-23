#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepositoryRoot,
    [Parameter(Mandatory)][string]$Catalog,
    [Parameter(Mandatory)][string]$PmdDirectory,
    [Parameter(Mandatory)][string]$TestSourceRoot,
    [Parameter(Mandatory)][string[]]$TestReportDirectories,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'LocalStaticAnalysis.psm1')
Import-Module (Join-Path $PSScriptRoot 'LocalStaticDisposition.psm1')
$root=[IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([char]92,[char]47)
$target=Join-Path $root 'target'
$out=[IO.Path]::GetFullPath($OutputDirectory)
if (-not $out.StartsWith($target+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $out)) { throw 'DISPOSITION_NEW_OUTPUT_UNDER_TARGET_REQUIRED' }
Assert-StaticAnalysisPath $target
$ancestor=[IO.Path]::GetDirectoryName($out)
while (-not (Test-Path -LiteralPath $ancestor)) { $ancestor=[IO.Path]::GetDirectoryName($ancestor) }
Assert-StaticAnalysisPath $ancestor
[IO.Directory]::CreateDirectory($out) | Out-Null
$utf8=[Text.UTF8Encoding]::new($false,$true)
$request=[ordered]@{state='RESERVED';layer='LOCAL_DISPOSITION_REVIEW_ONLY';maximumAttempts=1;maximumSeconds=120;network=$false;sql=$false;recovery='Preserve outputs and raw findings; no retry, suppression or external effect.'}
[IO.File]::WriteAllText((Join-Path $out 'request.json'),($request | ConvertTo-Json),$utf8)
$timer=[Diagnostics.Stopwatch]::StartNew()
$code=1
try {
    $result=Get-LocalStaticDisposition -RepositoryRoot $root -Catalog $Catalog -PmdDirectory $PmdDirectory -TestSourceRoot $TestSourceRoot -TestReportDirectories $TestReportDirectories
    $result.elapsedSeconds=$timer.Elapsed.TotalSeconds
    if ($timer.Elapsed.TotalSeconds -gt 120) { throw 'DISPOSITION_DEADLINE_EXCEEDED' }
    $code=0
} catch {
    $reason=if ($_.Exception.Message -cmatch '^(DISPOSITION|STATIC)_[A-Z0-9_]+$') { $_.Exception.Message } else { 'DISPOSITION_EXECUTION_ERROR' }
    $result=[ordered]@{state='FAILED';reason=$reason;errorKind=$_.Exception.GetType().FullName;elapsedSeconds=$timer.Elapsed.TotalSeconds;nominalSecurityAcceptance=$false;releaseAcceptance=$false;network=$false;sql=$false}
}
[IO.File]::WriteAllText((Join-Path $out 'result.json'),($result | ConvertTo-Json -Depth 24),$utf8)
Write-Output $result.state
exit $code

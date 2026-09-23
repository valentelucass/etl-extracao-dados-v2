#Requires -Version 7.5
[CmdletBinding()]
param(
    [string]$EvidencePath = 'src/test/resources/runtime-qualification/concurrent.synthetic.json',
    [string]$ObserverSqlPath = 'database/validation/059_observe_bloco60_concurrent_waiters.sql'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Read-Bounded([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path, $root)
    if (-not $absolute.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'B61_VALIDATOR_LOCAL_PATH'
    }
    $file = Get-Item -LiteralPath $absolute
    if ($file.Length -gt 1MB) { throw 'B61_VALIDATOR_INPUT_BOUND' }
    return [IO.File]::ReadAllText($file.FullName, [Text.UTF8Encoding]::new($false, $true))
}
$dom = 'C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
if ($null -eq ('RuntimeObserverContract' -as [type])) {
    [void][Reflection.Assembly]::LoadFrom($dom)
    Add-Type -Path "$PSScriptRoot/RuntimeObserverContract.cs" -ReferencedAssemblies (
        @(Get-ChildItem (Join-Path $PSHOME 'ref') -Filter '*.dll' | ForEach-Object FullName) + @($dom))
}
$sql = (Read-Bounded $ObserverSqlPath).Replace(':On Error exit','').
    Replace('$(B60FirstPid)','101').Replace('$(B60SecondPid)','102').Replace('$(B60BarrierPid)','103')
[RuntimeObserverContract]::Check($sql)
Import-Module "$PSScriptRoot/RuntimeQualificationEvidence.psm1" -Force
$evidence = Read-Bounded $EvidencePath | ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
Assert-RuntimeQualificationEvidence $evidence
@{passed=$true;layer='OFFLINE_VALIDATOR_AND_OBSERVER_AST';sqlExecuted=$false;
    physicalObserverQualified=$false;sourceEvidence='SYNTHETIC_OFFLINE';samples=$evidence.samples.Count;
    adoptedObserver=$ObserverSqlPath;adoptedAdversarial='database/validation/060_exercise_bloco60_adversarial_rollback.sql';
    parserSha256=(Get-FileHash -LiteralPath $dom).Hash.ToLowerInvariant()} | ConvertTo-Json

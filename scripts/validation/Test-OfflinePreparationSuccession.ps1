#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $PSScriptRoot 'P06ReviewSuccession.psm1')
$result = Get-P06ReviewSuccession -Root $root -SelfTest:$SelfTest
if ($null -eq $result.successor) { throw 'OFFLINE_SUCCESSION_REQUIRED' }
$successor = $result.successor
foreach ($entry in $successor.map.Values) {
    if (-not $result.map.ContainsKey($entry.path) -or $result.map[$entry.path].after -cne $entry.after) { throw 'OFFLINE_SUCCESSION_COMPOSITION' }
}
[ordered]@{status='PASS';predecessor='P06';changedFiles=$successor.map.Count;baselineFiles=$successor.baselineFiles.Count;newFiles=$successor.newFiles.Count;successionGuards=$successor.guards;p06Guards=$result.guards;sourceCalls=0;databaseCalls=0;newAcceptances=0} | ConvertTo-Json

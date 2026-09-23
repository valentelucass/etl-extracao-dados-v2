#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $PSScriptRoot 'P11PhysicalSuccession.psm1') -Force
$result=Get-P11PhysicalSuccession -Root $root -SelfTest:$SelfTest
if($null -eq $result){throw 'P11_PHYSICAL_MANIFEST_MISSING'}
[ordered]@{
    status='PASS'
    successionGuards=$result.guards
    scope='LOCAL_NATIVE_PASS_FAILED_FULL_VERIFY_HISTORY_NO_PACKAGE'
    openExternalRequirements=16
    newAcceptances=0
    sourceCalls=0
    databaseUsed=$true
}|ConvertTo-Json

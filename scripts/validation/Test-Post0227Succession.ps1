#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Post0227Succession.psm1') -Force
$result=Get-Post0227Succession -Root ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))) -SelfTest:$SelfTest
if($null -eq $result){throw 'POST0227_MANIFEST_MISSING'}
[ordered]@{status='PASS';scope='LOCAL_REQUALIFICATION_P07_P08';guards=$result.guards;openExternalRequirements=16;newAcceptances=0}|ConvertTo-Json

#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'QualificacaoP0733Succession.psm1') -Force
$result=Get-QualificacaoP0733Succession -Root ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))) -SelfTest:$SelfTest
if($null -eq $result){throw 'QP0733_MANIFEST_MISSING'}
[ordered]@{status='PASS';scope='LOCAL_REQUALIFICATION_P07_P08_EXTERNAL_CRITERIA_REMAIN';guards=$result.guards;currentP07Qualified=$result.currentP07Qualified;currentP08Qualified=$result.currentP08Qualified;nominalApproval=$false;releaseApproved=$false;newAcceptances=0}|ConvertTo-Json

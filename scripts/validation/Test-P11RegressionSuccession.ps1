#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $PSScriptRoot 'P11RegressionSuccession.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
$matrixPath=Join-Path $root 'docs/catalogos/p11-regressao-local/matriz-atual.json'
$matrix=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($matrixPath)) 8388608
$null=Test-P11RegressionMatrix -Matrix $matrix
$guards=0
if($SelfTest){
    foreach($test in @(
        @('P11_MATRIX_SCOPE',{param($m)$m.networkCalls=0}),
        @('P11_MATRIX_SCOPE',{param($m)$m.openExternalRequirements=17}),
        @('P11_MATRIX_SCOPE',{param($m)$m.newAcceptances=1}),
        @('P11_MATRIX_ROUNDS',{param($m)$m.rounds[1].externalNetworkCalls=0}),
        @('P11_MATRIX_ROUNDS',{param($m)$m.rounds[1].externalNetworkCalls=7}),
        @('P11_MATRIX_ROUNDS',{param($m)$m.rounds[1].externalNetworkUsed=$false}),
        @('P11_MATRIX_ROUNDS',{param($m)$m.rounds[1].ledgerReceivedCalls=8}),
        @('P11_MATRIX_ROUNDS',{param($m)$m.rounds[2].externalNetworkCalls=1}),
        @('P11_MATRIX_REQUIREMENTS',{param($m)$m.stages[1].requirements[0].nominalOwner='invented'}),
        @('P11_MATRIX_REQUIREMENTS',{param($m)$m.stages[1].requirements[1].status='BLOQUEADO_POR_INPUT'})
    )){
        $copy=Read-QualificationJsonBytes ([Text.Encoding]::UTF8.GetBytes(($matrix|ConvertTo-Json -Depth 30))) 8388608
        & $test[1] $copy
        $reason='ACCEPTED';try{$null=Test-P11RegressionMatrix -Matrix $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $test[0]){throw ('P11_MATRIX_GUARD_'+$reason)};$guards++
    }
}
$result=Get-P11RegressionSuccession -Root $root -SelfTest:$SelfTest
[ordered]@{status='PASS';matrixGuards=$guards;successionGuards=$result.guards;openExternalRequirements=16;totalRequirements=17;newAcceptances=0;externalNetworkCalls=0;databaseCalls=0}|ConvertTo-Json

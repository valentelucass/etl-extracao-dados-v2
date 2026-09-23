#Requires -Version 7.5
param([switch]$SelfTest,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/cadeia-integral-por-contratos/'
Import-Module (Join-Path $PSScriptRoot 'IntegralChainSuccession.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
$succession=Get-IntegralChainSuccession -Root $root -SelfTest:$SelfTest
if($null -eq $succession){throw 'INTEGRAL_MANIFEST_REQUIRED'}
function Read([string]$path){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $root $path))) 5242880}
function Exact($a,$b,[string]$reason){
 if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}
}
function Same($a,$b){
 if($null -eq $b){if($null -ne $a){throw 'INTEGRAL_ORIGINAL_REWRITTEN'};return}
 if($b -is [Collections.IDictionary]){
  if($a -isnot [Collections.IDictionary]){throw 'INTEGRAL_ORIGINAL_REWRITTEN'}
  Exact @($a.Keys) @($b.Keys) 'INTEGRAL_ORIGINAL_REWRITTEN'
  foreach($key in $b.Keys){Same $a[$key] $b[$key]}
 }elseif($b -is [Array]){
  if($a -isnot [Array] -or $a.Count -ne $b.Count){throw 'INTEGRAL_ORIGINAL_REWRITTEN'}
  for($i=0;$i -lt $b.Count;$i++){Same $a[$i] $b[$i]}
 }elseif($null -eq $a -or $a.GetType() -ne $b.GetType() -or $a -cne $b){throw 'INTEGRAL_ORIGINAL_REWRITTEN'}
}
$baseline=Read 'docs/catalogos/macrobloco-integracao-funcional/matriz-45-unidades.json'
function Units($matrix){
 if($matrix.version -ne 1 -or $matrix.status -cne 'CADEIA_INTEGRAL_LOCAL_CONCLUIDA'){throw 'INTEGRAL_MATRIX_SCOPE'}
 Same $matrix.construction $baseline.construction
 Same $matrix.historicalAcceptances $baseline.historicalAcceptances
 Exact $matrix.units.id $baseline.units.id 'INTEGRAL_UNIT_UNIVERSE'
 for($i=0;$i -lt 45;$i++){
  $unit=$matrix.units[$i];$copy=[ordered]@{}
  foreach($key in $unit.Keys){if($key -cne 'integralReview'){$copy[$key]=$unit[$key]}}
  Same $copy $baseline.units[$i]
  $review=$unit.integralReview
  if($review.status -cne 'REVISAO_LOCAL_CONCLUIDA' -or $review.originalCriteriaPreserved -cne $true -or $review.countChanged -cne $false -or $review.nominalAcceptanceAdded -cne $false){throw 'INTEGRAL_REVIEW_SCOPE'}
  Exact $review.externalGates $unit.gates 'INTEGRAL_EXTERNAL_GATE_DRIFT'
  foreach($id in $review.proofIds){if($id -cnotmatch '^P(0[1-9]|1[0-9]|2[0-4])$'){throw 'INTEGRAL_PROOF_ID'}}
 }
}
$matrix=Read ($catalog+'matriz-45-unidades.json');Units $matrix
$fronts=Read ($catalog+'matriz-A-N.json')
if($fronts.version -ne 1 -or $fronts.status -cne 'CADEIA_INTEGRAL_LOCAL_CONCLUIDA' -or $fronts.construction -cne '39/45' -or $fronts.historicalAcceptances -cne '67/115' -or $fronts.newAcceptances -ne 0 -or $fronts.sourceCalls -ne 0){throw 'INTEGRAL_FRONT_SCOPE'}
Exact $fronts.fronts.id @('A','B','C','D','E','F','G','H','I','J','K','L','M','N') 'INTEGRAL_FRONT_UNIVERSE'
foreach($front in $fronts.fronts){
 if($front.status -cne 'CONCLUIDA_NO_ESCOPO_LOCAL' -or $front.externalAcceptance -cne $false -or $front.proofIds.Count -lt 1){throw 'INTEGRAL_FRONT_SCOPE'}
 foreach($id in $front.proofIds){if($id -cnotmatch '^P(0[1-9]|1[0-9]|2[0-4])$'){throw 'INTEGRAL_PROOF_ID'}}
}
if($matrix.predecessorMatrix.path -cne 'docs/catalogos/macrobloco-integracao-funcional/matriz-45-unidades.json' -or
 $matrix.predecessorMatrix.sha256 -cne (Get-FileHash -LiteralPath (Join-Path $root $matrix.predecessorMatrix.path)).Hash.ToLowerInvariant()){throw 'INTEGRAL_PREDECESSOR_MATRIX_PIN'}
$proofs=Read ($catalog+'provas.json')
if($proofs.version -ne 1 -or $proofs.sourceCalls -ne 0 -or $proofs.newAcceptances -ne 0){throw 'INTEGRAL_PROOFS_SCOPE'}
Exact $proofs.proofs.id @(1..24|ForEach-Object {'P{0:D2}' -f $_}) 'INTEGRAL_PROOF_UNIVERSE'
function Evidence($entry){
 if($entry.path -cnotmatch '^(target/macrobloco-cadeia-integral-20260914-01/|src/|docs/|scripts/|database/)[A-Za-z0-9_.$/-]+$' -or $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'INTEGRAL_PROOF_PATH'}
 if($IncludePrivateEvidence -or -not $entry.path.StartsWith('target/')){
  $physical=$entry.path
  if($null -ne $succession.successor -and $succession.successor.map.ContainsKey($entry.path)){
   $change=$succession.successor.map[$entry.path]
   if($change.before -ceq $entry.sha256){$physical=$change.snapshot}
  }
  Assert-QualificationPath (Join-Path $root $physical)
  if((Get-FileHash -LiteralPath (Join-Path $root $physical)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'INTEGRAL_PROOF_HASH'}
 }
}
if($proofs.common.rollbackConfirmed -cne $true -or $proofs.common.schemaVersion -ne 102 -or $proofs.common.evidence.Count -lt 1){throw 'INTEGRAL_PROOFS_SCOPE'}
foreach($entry in $proofs.common.evidence){Evidence $entry}
foreach($proof in $proofs.proofs){
 if($proof.status -cne 'PROVADO_LOCALMENTE' -or $proof.assertions.Count -lt 1 -or $proof.evidence.Count -lt 1){throw 'INTEGRAL_PROOF_INCOMPLETE'}
 foreach($entry in $proof.evidence){Evidence $entry}
}
$verification=Read ($catalog+'verificacao-local.json')
if($verification.passed -cne $true -or $verification.sourceCalls -ne 0 -or $verification.newAcceptances -ne 0 -or $verification.rollbackConfirmed -cne $true -or $verification.syntheticDomainCommit -cne $false -or $verification.schemaVersion -ne 102){throw 'INTEGRAL_VERIFICATION_SCOPE'}
if($verification.unit.failures -ne 0 -or $verification.unit.errors -ne 0 -or $verification.unit.skipped -ne 4 -or $verification.integration.failures -ne 0 -or $verification.integration.errors -ne 0 -or $verification.integration.skipped -ne 0){throw 'INTEGRAL_TEST_FAILURE'}
if($verification.testIdentityReconciliation.passed -cne $true){throw 'INTEGRAL_VERIFICATION_INCOMPLETE'}
Evidence $verification.testIdentityReconciliation
Evidence $verification.package
foreach($entry in $verification.evidence){Evidence $entry}
Exact $verification.jarCases.id @('set-a-complete','set-b-complete','missing-user','oracle-value','missing-loc-target') 'INTEGRAL_JAR_CASE_UNIVERSE'
foreach($case in $verification.jarCases){
 if($case.passed -cne $true -or $case.rollbackConfirmed -cne $true -or $case.layer -cne 'DISTRIBUTED_JAR_EXTRACTED_PROCESS' -or
  $case.jarSha256 -cne $verification.package.jarSha256 -or $case.inputSha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'INTEGRAL_JAR_NOT_EXECUTED'}
 if($case.id.EndsWith('-complete') -and ($case.comparisonsPassed -ne 19 -or $case.factGrainsProved -cne $true -or $case.previews -ne 33 -or $case.previewMatrixProved -cne $true)){throw 'INTEGRAL_JAR_INCOMPLETE'}
 foreach($entry in $case.evidence){Evidence $entry}
}
$guards=0
if($SelfTest){
 foreach($test in @(
  @('INTEGRAL_MATRIX_SCOPE',{param($m)$m.status='EM_EXECUCAO'}),
  @('INTEGRAL_ORIGINAL_REWRITTEN',{param($m)$m.units[0].countedAfter=$true}),
  @('INTEGRAL_UNIT_UNIVERSE',{param($m)$m.units=@($m.units|Select-Object -Skip 1)}),
  @('INTEGRAL_REVIEW_SCOPE',{param($m)$m.units[0].integralReview.nominalAcceptanceAdded=$true}),
  @('INTEGRAL_EXTERNAL_GATE_DRIFT',{param($m)$m.units[0].integralReview.externalGates=@()}),
  @('INTEGRAL_PROOF_ID',{param($m)$m.units[0].integralReview.proofIds=@('P25')})
 )){
  $copy=Read ($catalog+'matriz-45-unidades.json');& $test[1] $copy
  $reason='ACCEPTED';try{Units $copy}catch{$reason=$_.Exception.Message}
  if($reason -cne $test[0]){throw ('INTEGRAL_MATRIX_GUARD_'+$reason)};$guards++
 }
}
@{passed=$true;status=$succession.manifest.status;units=45;construction='39/45';historicalAcceptances='67/115';proofs=24;successionGuards=$succession.guards;matrixGuards=$guards;sourceCalls=0;newAcceptances=0;privateEvidence=[bool]$IncludePrivateEvidence}|ConvertTo-Json

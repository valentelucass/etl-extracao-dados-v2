#Requires -Version 7.0
param([switch]$IncludePrivateEvidence,[switch]$ManifestOnly)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$path=Join-Path $root 'database/manifest/runtime-bloco55-acceptances.json'
$m=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -DateKind String
if($m.version -ne 1 -or $m.block -ne 55 -or $m.scope -cne 'ADOPTED_LOCAL_WINDOWS_SQL_RUNTIME' -or ($m.accepted -join ',') -cne 'V2-042b,V2-042c,V2-022b,V2-042' -or -not $m.parent042Accepted -or $m.parent022Accepted -or $m.realSourceRequiredForTheseCriteria -or $m.productionGovernanceApproved -or $m.originalUntil -cne '2026-10-07T22:34:30.615Z'){throw 'B55_EXACT_CANONICAL_ACCEPTANCE_SCOPE_REQUIRED'}
$inputs=[ordered]@{provider='WINDOWS_INTEGRATED_EXISTING_ACCOUNTS';authorityAudiences='PINNED_LOCAL_SQL_DATABASE_ADMINISTERED_MODULES';principals='etl_v2_exec_SERVICE_etl_v2_view_OPERATOR';roleAuthority='OWNER_AUTHORIZED_LOCAL_ADMIN_ONLY';mappingRevocation='DURABLE_VERSIONED_MAPPING_SCOPE_NO_RENEWAL';sqlPrincipals='EXISTING_WINDOWS_LOGINS_EXACT_32_EXECUTES';durableSink='V016_SQL_APPEND_ONLY_DECISION_CONSUMPTION';pseudonymization='EXISTING_RANDOM_AUDIT_UUID_SID_ONLY_PROTECTED_SQL';approvalScope='OWNER_ADOPTED_SECTION_3_REUSE_B53_B54_LAB_MECHANISM'}
if(@($m.identityInputs.psobject.Properties).Count -ne $inputs.Count){throw 'B55_IDENTITY_INPUTS_INCOMPLETE'}
foreach($key in $inputs.Keys){if($m.identityInputs.$key -cne $inputs[$key]){throw 'B55_IDENTITY_INPUTS_INCOMPLETE'}}
if($m.proofFiles.Count -ne 12){throw 'B55_ACCEPTANCE_PROVENANCE_REQUIRED'}
foreach($entry in $m.proofFiles){
 if($entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or $entry.path.StartsWith('/')){throw 'B55_PROOF_PATH'}
 if($IncludePrivateEvidence -or -not $entry.path.StartsWith('target/')){
  if((Get-FileHash -LiteralPath (Join-Path $root $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'B55_ACCEPTANCE_PROOF_HASH_CHANGED'}
 }
}
if(-not $ManifestOnly){
 $state=Get-Content (Join-Path $root 'STATES.md') -Raw
 foreach($gate in $m.accepted){if([regex]::Matches($state,('(?m)^\s*- \[x\] \*\*'+[regex]::Escape($gate)+' —')).Count -ne 1){throw 'B55_ACCEPTANCE_STATE_NOT_SYNCHRONIZED'}}
 foreach($gate in @('V2-022','V2-041')){if($state -match ('(?m)^\s*- \[x\] \*\*'+[regex]::Escape($gate)+' —')){throw 'UNPROVEN_NOMINAL_GATE_CLOSED'}}
 if(-not $state.Contains('B55_CANONICAL_REVIEW_WINDOWS_SQL_LOCAL')){throw 'B55_CANONICAL_REVIEW_REQUIRED'}
}
if($IncludePrivateEvidence){
 & (Join-Path $PSScriptRoot 'Test-Bloco55SqlFenceEvidence.ps1') | Out-Null
 foreach($folder in @('B55_SMOKE_03','B55_MATRIX_01')){
  $results=@(Get-Content (Join-Path $root ('target/bloco55/runtime/'+$folder+'/results.json')) -Raw|ConvertFrom-Json)
  if(@($results|Where-Object {-not $_.passed}).Count){throw 'B55_OFFICIAL_JAR_ACCEPTANCE_NOT_PROVEN'}
 }
 $smoke=Get-Content (Join-Path $root 'target/bloco55/runtime/B55_SMOKE_03/results.json') -Raw|ConvertFrom-Json
 foreach($template in @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
  $positive=@($smoke|Where-Object id -CEQ ('B55_SMOKE_03_'+$template+'_RUN'))
  if($positive.Count -ne 1 -or $positive[0].observedExit -ne 0 -or $positive[0].observed.publications -ne 1 -or $positive[0].observed.consumptions -ne 1){throw 'B55_REAL_CAPABILITY_HANDLER_PUBLICATION_REQUIRED'}
 }
 $log=Get-Content (Join-Path $root 'target/bloco55/verify-03.log') -Raw
 foreach($marker in @('Tests run: 1138, Failures: 0, Errors: 0, Skipped: 4','All coverage checks have been met.','BUILD SUCCESS')){if(-not $log.Contains($marker)){throw 'B55_FINAL_VERIFY_REQUIRED'}}
}
'B55_CANONICAL_G06_G07_G08_PARENT042_PROVEN_LOCAL_WINDOWS_SQL_PARENT022_OPEN'

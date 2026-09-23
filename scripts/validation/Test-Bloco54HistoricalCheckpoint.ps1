#Requires -Version 7.0
param([switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifest=Get-Content (Join-Path $root 'database/manifest/runtime-bloco54-historical-sixth.json') -Raw|ConvertFrom-Json -DateKind String
if(-not $manifest.sixthCampaignAuthorized -or $manifest.seventhCampaignAuthorized -or $manifest.remainingUnits -ne 19 -or -not $manifest.ops02RecoveryVerified -or $manifest.scopeMaximumVersion -ne 10 -or $manifest.changedOriginalScopeId -ne 1 -or -not $manifest.temporalExecutionImplemented -or -not $manifest.requiredRejectedDriftAlertImplemented -or $manifest.preparedRevisionPhysicallyExecuted -or -not $manifest.preparedRevisionDiagnosed -or $manifest.preparedRevisionOfflineExportWindows -ne 3){throw 'SIXTH_CHECKPOINT_DRIFT'}
if($manifest.status -cne 'PARTIAL_NOT_ACCEPTED' -or $manifest.tests -ne 1065 -or $manifest.testFailures -ne 0 -or $manifest.testErrors -ne 0 -or $manifest.testSkips -ne 4 -or $manifest.newCanonicalAcceptances -ne 0 -or -not $manifest.mappingRevocationMatrixCompleted -or $manifest.replayForcePhysicallyVerified -or $manifest.driftAlertPhysicallyObserved -or $manifest.fullTemporalPhysicalMatrixCompleted -or $manifest.temporalDispatched -ne 0){throw 'UNPROVEN_B54_ACCEPTANCE'}
if(-not $manifest.additionalGrantsApplied -or -not $manifest.newRevisionPhysicallyExecuted -or -not $manifest.pendingV020PhysicallyQualified -or -not $manifest.mappingRecoveryVerified -or -not $manifest.supplementalCampaignAuthorized){throw 'PROVEN_B54_CHECKPOINT_LOST'}
if($manifest.originalGrants -ne 22 -or $manifest.currentGrants -ne 25 -or $manifest.proposedAdditionalGrants -ne 3 -or $manifest.originalMappings -ne 2 -or $manifest.originalScopes -ne 8 -or $manifest.mappingVersion -ne 15 -or $manifest.operatorMappingVersion -ne 1 -or $manifest.scopeVersion -ne 1 -or $manifest.originalUntil -cne '2026-10-07T22:34:30.615Z'){throw 'B54_NOMINAL_IDENTITY_DRIFT'}
if($manifest.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $manifest.installedThrough -cne 'V020' -or $manifest.pendingMigration -cne '' -or $manifest.databaseRows -ne 5328 -or $manifest.catalogSha256 -cne '6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a'){throw 'B54_SCHEMA_PHASE_DRIFT'}
if($manifest.reservations -ne 109 -or $manifest.campaignsOpened -ne 6 -or $manifest.campaignsClosed -ne 6 -or $manifest.maximumUnits -ne 128 -or $manifest.authorityHarnessCasesPassed -ne 39 -or $manifest.authorizationMatrix -cne 'PARTIAL_39_PASS_MAPPING_AND_CONSUME_RESTART_OPS02_RECOVERED' -or $manifest.temporalWindows -ne 6 -or $manifest.temporalDistinctOccurrences -ne 6 -or $manifest.newAttempts -ne 8 -or $manifest.newPublications -ne 4 -or $manifest.jarCasesExecuted -ne 28 -or $manifest.httpRequests -ne 22){throw 'B54_PHYSICAL_COUNTS_DRIFT'}
if((Get-FileHash (Join-Path $root 'database/migrations/V018__enforce_durable_runtime_consumers.sql')).Hash.ToLowerInvariant() -cne '0e418d19fe93ea3611f6d083ffa77e8e58e764ed0f5a180eb1d3a33e6f971f49'){throw 'APPLIED_V018_CHANGED'}
if((Get-FileHash (Join-Path $root 'database/migrations/V019__create_scoped_runtime_observability.sql')).Hash.ToLowerInvariant() -cne '42b8baae9c6681b3b5d1201d7062e33c7e34562eb5c643c7c269186128888d4c'){throw 'QUALIFIED_V019_CHANGED'}
$state=Get-Content (Join-Path $root 'STATES.md') -Raw
if(Test-Path (Join-Path $root 'database/manifest/runtime-bloco55-acceptances.json')){
 & (Join-Path $PSScriptRoot 'Test-Bloco55Acceptances.ps1') -IncludePrivateEvidence:$IncludePrivateEvidence | Out-Null
}else{
 foreach($gate in @('V2-042','V2-042b','V2-042c','V2-022','V2-022b')){
  if($state -match ('(?m)^\s*- \[x\] \*\*'+[regex]::Escape($gate)+' —')){throw 'UNPROVEN_CANONICAL_GATE_CLOSED'}
 }
}
$proposal=Join-Path $root 'database/proposals/bloco54-observability'
$package=Get-Content (Join-Path $proposal 'manifest.json') -Raw|ConvertFrom-Json -DateKind String
if(-not $package.applied -or -not $package.grantsAuthorized -or -not $package.supplementalCampaignAuthorized -or $package.grantDelta -ne 3 -or $package.expectedOriginalGrants -ne 22 -or $package.expectedAfterGrants -ne 25){throw 'GRANT_DELTA_DRIFT'}
foreach($file in $package.files){
 if($file.path -cnotmatch '^[A-Za-z0-9_.-]+$' -or $file.path.Contains('..')){throw 'PACKAGE_PATH_DRIFT'}
 if((Get-FileHash (Join-Path $proposal $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'REVIEW_PACKAGE_HASH_CHANGED'}
}
if($package.pendingV020Sha256 -cne '7ba03d5627016e722e077ca2c6a7eec926ea4779dccce8c6010d77927ed14df4' -or (Get-FileHash (Join-Path $root 'database/migrations/V020__bind_existing_runtime_occurrences.sql')).Hash.ToLowerInvariant() -cne $package.pendingV020Sha256){throw 'APPLIED_V020_CHANGED'}
$pending=Get-Content (Join-Path $root 'database/migrations/V020__bind_existing_runtime_occurrences.sql') -Raw
$verify=Get-Content (Join-Path $proposal 'verify.sql') -Raw
Import-Module (Join-Path $PSScriptRoot 'Bloco54SqlModules.psm1') -Force
$modules=@(Get-Bloco54SqlModuleHashes (Join-Path $root 'database/migrations/V020__bind_existing_runtime_occurrences.sql'))
if($modules.Count -ne 3 -or $pending -notmatch 'e.execution_id=d.execution_id' -or $pending -notmatch 'initial_contiguous_end_utc,126\) AS \[start\]' -or -not $pending.Contains('JSON_VALUE(@windows,''$[0].start'') AS [start]')){throw 'EXISTING_OCCURRENCE_FENCE_MISSING'}
foreach($module in $modules){if(-not $verify.Contains($module.sha256)){throw 'PENDING_MODULE_NOT_PINNED_IN_GRANT_PACKAGE'}}
$baseline=Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') -Raw
foreach($version in @('V019__create_scoped_runtime_observability.sql','V020__bind_existing_runtime_occurrences.sql')){if(-not $baseline.Contains($version)){throw 'PENDING_BASELINE_MISMATCH'}}
if($IncludePrivateEvidence){
 if(@($manifest.sixthProofFiles).Count -ne 11){throw 'EXACT_SIXTH_PROOF_SET_REQUIRED'}
 foreach($proof in $manifest.sixthProofFiles){
  if($proof.path -cnotmatch '^target/bloco54/resume-sixth/[A-Za-z0-9_.-]+$' -or (Get-FileHash (Join-Path $root $proof.path)).Hash.ToLowerInvariant() -cne $proof.sha256){throw 'SIXTH_PRIVATE_PROOF_CHANGED'}
 }
 $sixthMatrix=Get-Content (Join-Path $root 'target/bloco54/resume-sixth/authority-matrix-results.json') -Raw|ConvertFrom-Json
 $sixthCases=@($sixthMatrix|Where-Object {$_.PSObject.Properties.Name -contains 'layer'})
 if($sixthCases.Count -ne 8 -or @($sixthCases|Where-Object {-not $_.passed}).Count -ne 0 -or @($sixthCases|Where-Object {$_.id.StartsWith('SIXTH_AUTH08_') -and $_.consumptions -eq 0 -and $_.decisions -eq 1 -and $_.observedExit -eq 20}).Count -ne 6){throw 'SIXTH_AUTHORITY_CASES_NOT_PROVEN'}
 $sixthResult=Get-Content (Join-Path $root 'target/bloco54/resume-sixth/results.json') -Raw|ConvertFrom-Json
 if(@($sixthResult|Where-Object {$_.id -ceq 'SIXTH_MAPPING_RESTART' -and -not $_.passed}).Count -ne 1 -or @($sixthResult|Where-Object {$_.id -ceq 'SIXTH_FAILED' -and -not $_.passed}).Count -ne 1){throw 'SIXTH_FAILED_INITIAL_RECOVERY_MUST_REMAIN_RECORDED'}
 $sixthRecovery=Get-Content (Join-Path $root 'target/bloco54/resume-sixth/recovery-apply.log') -Raw
 if(-not $sixthRecovery.Contains('B54_OPS02_RECOVERY_COMMITTED_SERVICE_VERSION_15_ORIGINAL_RIGHTS_EXPIRY')){throw 'SIXTH_RECOVERY_NOT_PROVEN'}
 $sixthPreservation=Get-Content (Join-Path $root 'target/bloco54/resume-sixth/final-preservation.log') -Raw
 foreach($required in @('"priorAttempts":95,"priorPublications":75,"retainedExtracting":8,"b54Attempts":8,"b54Publications":4,"b54Pages":10,"b54Entries":12','"activeUserTransactions":0,"restrictedSessions":0','"maximumVersion":10,"revokedScopes":0','"principal_kind":"SERVICE","mapping_version":15')){if(-not $sixthPreservation.Contains($required)){throw 'SIXTH_PRESERVATION_NOT_PROVEN'}}
 $sixthBuild=Get-Content (Join-Path $root $manifest.finalVerifyLog) -Raw
 foreach($required in @('Tests run: 1065, Failures: 0, Errors: 0, Skipped: 4','All coverage checks have been met.','BUILD SUCCESS')){if(-not $sixthBuild.Contains($required)){throw 'V4_FULL_VERIFY_NOT_PROVEN'}}
 $events=@(Get-Content (Join-Path $root 'target/bloco54/ledger.jsonl') | ForEach-Object {$_|ConvertFrom-Json -DateKind String})
 $events=@($events|Where-Object {$_.type -ne 'EXTEND' -and $_.campaign -le 6})
$reserved=@($events|Where-Object type -eq RESERVE)
 $opened=@($events|Where-Object type -eq OPEN);$closed=@($events|Where-Object type -eq CLOSE)
 if($reserved.Count -ne 109 -or $opened.Count -ne 6 -or $closed.Count -ne 6 -or @($reserved.id|Select-Object -Unique).Count -ne 109 -or $opened[-1].sixthApproval -cne 'OWNER_APPROVED_B54_SIXTH_EXISTING_BALANCE'){throw 'B54_BUDGET_RECEIPT_DRIFT'}
 foreach($opening in $opened){
  $closing=@($closed|Where-Object campaign -eq $opening.campaign)
  if($closing.Count -ne 1 -or [DateTimeOffset]::Parse($closing[0].utc) -gt [DateTimeOffset]::Parse($opening.deadline)){throw 'B54_CAMPAIGN_DEADLINE_DRIFT'}
 }
 if((Get-FileHash (Join-Path $root 'target/bloco53/cumulative-reservations.txt')).Hash.ToLowerInvariant() -cne $manifest.b53LedgerSha256){throw 'B53_LEDGER_CHANGED'}
 $build=Get-Content (Join-Path $root 'target/bloco54/full-verify-final-v3.log') -Raw
 foreach($required in @('Tests run: 1056, Failures: 0, Errors: 0, Skipped: 4','All coverage checks have been met.','BUILD SUCCESS')){if(-not $build.Contains($required)){throw 'FINAL_VERIFY_NOT_PROVEN'}}
 $preserved=Get-Content (Join-Path $root 'target/bloco54/final-preservation.log') -Raw
 if(-not $preserved.Contains('95 75 8 4 2') -or -not $preserved.Contains('B53_AGGREGATE_PRESERVATION_PASS_V019_ABSENT')){throw 'B53_PRESERVATION_NOT_PROVEN'}
 $cases=Get-Content (Join-Path $root 'target/bloco54/restricted-controller-result.txt')
 if(@($cases|Where-Object {$_ -match ' expected=\d+ observed=\d+ http=\d+'}).Count -ne 11 -or -not ($cases -contains 'CAMPAIGN_CASES_COMPLETED') -or -not ($cases -contains 'HTTP_REQUESTS=11')){throw 'OFFICIAL_JAR_CASES_NOT_PROVEN'}
 Import-Module (Join-Path $PSScriptRoot 'Bloco54Artifact.psm1') -Force
 Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle') $manifest.physicalManifestSha256 | Out-Null
 Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle-v3') $manifest.finalPhysicalManifestSha256 | Out-Null
 Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle-v4') $manifest.preparedManifestSha256 | Out-Null
 if($manifest.preparedRevisionPhysicallyExecuted){throw 'UNPROVEN_V4_RUNTIME_EXECUTION'}
 if(@($manifest.proofFiles).Count -ne 10){throw 'EXACT_FINAL_PROOF_SET_REQUIRED'}
 foreach($proof in $manifest.proofFiles){
  if($proof.path -cnotmatch '^target/bloco54/(?:resume-approved-supplemental/)?[A-Za-z0-9_.-]+$' -or $proof.path.Contains('..') -or (Get-FileHash (Join-Path $root $proof.path)).Hash.ToLowerInvariant() -cne $proof.sha256){throw 'FINAL_PRIVATE_PROOF_CHANGED'}
 }
 $final=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/results.json') -Raw|ConvertFrom-Json
 $jar=@($final|Where-Object {$_.PSObject.Properties.Name -contains 'layer' -and $_.layer -ceq 'OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK'})
 if($jar.Count -ne 16 -or @($jar|Where-Object {-not $_.passed}).Count -ne 0 -or @($final|Where-Object {$_.id -ceq 'FINAL_MANUAL_STATUS' -and $_.passed}).Count -ne 1 -or @($final|Where-Object {$_.id -ceq 'FINAL_MANUAL_DIAGNOSE' -and $_.passed}).Count -ne 1){throw 'FINAL_JAR_AND_MANUAL_NOT_PROVEN'}
 $matrix=Get-Content (Join-Path $root 'target/bloco54/authority-matrix-results.json') -Raw|ConvertFrom-Json
 if(@($matrix|Where-Object passed -eq $true).Count -ne 31 -or @($matrix|Where-Object passed -eq $false).Count -ne 2){throw 'PARTIAL_AUTHORITY_MATRIX_EVIDENCE_CHANGED'}
 $finalPreservation=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/final-preservation-v3.log') -Raw
 foreach($required in @('"priorAttempts":95,"priorPublications":75,"retainedExtracting":8,"b54Attempts":8,"b54Publications":4,"b54Pages":10,"b54Entries":12','"activeUserTransactions":0,"restrictedSessions":0','B54_FINAL_AGGREGATE_PRESERVATION_TEMPORAL_QUIESCENCE_PASS')){if(-not $finalPreservation.Contains($required)){throw 'FINAL_PRESERVATION_NOT_PROVEN'}}
 $recovery=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/recovery-apply.log') -Raw
 if(-not $recovery.Contains('B54_AUTH08_RECOVERY_COMMITTED_SERVICE_VERSION_4_ORIGINAL_RIGHTS_EXPIRY')){throw 'MAPPING_RECOVERY_NOT_PROVEN'}
}
& (Join-Path $PSScriptRoot 'Test-Bloco54ComparisonPackage.ps1')
& (Join-Path $PSScriptRoot 'Test-Bloco54RecoveryUtc.ps1')
& (Join-Path $PSScriptRoot 'Test-Bloco54ResidualPackage.ps1') -IncludePrivateEvidence:$IncludePrivateEvidence
'B54_HISTORICAL_SIXTH_CHECKPOINT_PASS_CURRENT_STATE_SEPARATE'

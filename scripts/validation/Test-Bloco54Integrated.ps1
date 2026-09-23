#Requires -Version 7.0
param([switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Read-Json([string]$Path){Get-Content (Join-Path $root $Path) -Raw|ConvertFrom-Json -DateKind String}
function Assert-Hash($Entry){
 if($Entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Entry.path.Contains('..') -or $Entry.path.StartsWith('/')){throw 'B54_EVIDENCE_PATH_INVALID'}
 if((Get-FileHash (Join-Path $root $Entry.path)).Hash.ToLowerInvariant() -cne $Entry.sha256){throw ('B54_EVIDENCE_HASH_CHANGED: '+$Entry.path)}
}
$m=Read-Json 'database/manifest/runtime-bloco54.json'
if($m.version -ne 2 -or $m.status -cne 'LOCAL_A_G_COMPLETE_NOMINAL_GATES_PENDING' -or ($m.frontsCompleted -join ',') -cne 'A,B,C,D,E,F,G'){throw 'B54_LOCAL_COMPLETION_REQUIRED'}
foreach($pair in @(@('tests',1098),@('testFailures',0),@('testErrors',0),@('testSkips',4),@('databaseRows',6723),@('currentGrants',25),@('mappingVersion',17),@('operatorMappingVersion',1),@('originalScopes',8),@('retainedRevokedReplayScopes',2),@('scopeMaximumVersion',10),@('newAttempts',45),@('newPublications',30),@('b54AuditedPages',70),@('b54AuditedEntries',74),@('reservations',302),@('maximumUnits',320),@('remainingUnits',18),@('campaignsOpened',30),@('campaignsClosed',30),@('httpRequests',127),@('jarCasesExecuted',136),@('authorityHarnessCasesPassed',39),@('activeUserTransactions',0),@('restrictedSessions',0),@('newCanonicalAcceptances',0),@('refundedUnits',0))){
 if($m.($pair[0]) -ne $pair[1]){throw ('B54_FINAL_COUNT_DRIFT: '+$pair[0])}
}
if($m.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $m.installedThrough -cne 'V021' -or $m.pendingMigration -cne '' -or $m.catalogSha256 -cne '7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408' -or $m.originalUntil -cne '2026-10-07T22:34:30.615Z' -or $m.renewed -or $m.realSourceExecuted -or $m.productionExecuted){throw 'B54_TARGET_SCOPE_DRIFT'}
if($m.artifactManifestSha256 -cne '838d2316fa53f412131d25a3c0c9cb397805fb05cecc3693b68daa37507e95fa'){throw 'FINAL_V7_ARTIFACT_REQUIRED'}
Assert-Hash $m.historicalCheckpoint
$schema=Read-Json 'database/proposals/bloco54-temporal-continuation/manifest.json'
if($schema.status -cne 'APPLIED_VERIFIED_LOCAL_V021' -or -not $schema.applied -or $schema.grantDelta -ne 0 -or $schema.expectedGrants -ne 25 -or $schema.catalogSha256 -cne $m.catalogSha256){throw 'B54_V021_APPLICATION_RECEIPT_REQUIRED'}
Assert-Hash ([pscustomobject]@{path='database/migrations/'+$schema.migration;sha256=$schema.migrationSha256})
foreach($entry in $schema.files){Assert-Hash ([pscustomobject]@{path='database/proposals/bloco54-temporal-continuation/'+$entry.path;sha256=$entry.sha256})}
$baseline=Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') -Raw
if(-not $baseline.Contains($schema.migration)){throw 'V021_BASELINE_TAIL_REQUIRED'}
$state=Get-Content (Join-Path $root 'STATES.md') -Raw
if(-not $state.Contains($m.status)){throw 'B54_STATE_NOT_SYNCHRONIZED'}
if(Test-Path (Join-Path $root 'database/manifest/runtime-bloco55-acceptances.json')){
 & (Join-Path $PSScriptRoot 'Test-Bloco55Acceptances.ps1') -IncludePrivateEvidence:$IncludePrivateEvidence | Out-Null
 # B54 remains an exact historical snapshot; only the separately evidenced B55
 # criteria can change the current checkboxes. All original proof hashes remain.
}else{
 foreach($gate in @('V2-042','V2-042b','V2-042c','V2-022','V2-022b')){if($state -match ('(?m)^\s*- \[x\] \*\*'+[regex]::Escape($gate)+' —')){throw 'UNPROVEN_NOMINAL_GATE_CLOSED'}}
}
if($IncludePrivateEvidence){
 foreach($entry in $m.proofFiles){Assert-Hash $entry}
 foreach($entry in $m.additionalBatches){Assert-Hash $entry}
 Assert-Hash $schema.testedPreparation
 Assert-Hash $schema.applicationEvidence
 $cases=@{}
 foreach($path in $m.scenarioFiles){$cases[$path]=@(Read-Json $path)}
 $base='target/bloco54/completion-authorized-20260908/'
 function Require-Case([string]$Folder,[string]$Id){
  $path=$base+$Folder+'/results.json'
  if($Folder -ceq 'seventh'){$path='target/bloco54/residual-runtime/results.json'}
  $case=@($cases[$path]|Where-Object id -CEQ $Id)
  if($case.Count -ne 1 -or -not $case[0].passed){throw ('B54_REQUIRED_CASE_NOT_PROVEN: '+$Id)}
  return $case[0]
 }
 foreach($folder in @('V5_ARTIFACT_02','V5_RUNTIME_03','V7_RECOVERY_01','V7_LIMITS_01','V7_INCREMENTAL_01','V7_SINKS_06','V7_AUTH_SINK_01','V7_MANUAL_01','V021_SCHEMA_01')){
  $all=$cases[$base+$folder+'/results.json']
  if($all.Count -lt 3 -or @($all|Where-Object {-not $_.passed}).Count){throw ('B54_FINAL_GROUP_FAILED: '+$folder)}
 }
 $required=@{
  seventh=@('SEVENTH_REPLAY_COLETAS','SEVENTH_REPLAY_FRETES','SEVENTH_REPLAY_REPEAT','SEVENTH_REPLAY_ORIGIN_FRONTIER_UNCHANGED','SEVENTH_FORCE_AUTHORIZED','SEVENTH_FORCE_PARTIAL_REJECTED','SEVENTH_OPERATOR_FORCE_DENIED','REPLAY_RIGHTS_REMOVED_SCOPES_RETAINED','SEVENTH_DRIFT_ALERT_SQL')
  V5_AUTH_01=@('V5_AUTH_01_TLS','V5_AUTH_01_ENV_ROLE','V5_AUTH_01_PROPERTY_ROLE','V5_AUTH_01_CLI_ROLE','V5_AUTH_01_NAMESPACE','V5_AUTH_01_ACTION_MODE','V5_AUTH_01_OPERATOR_RUN','V5_AUTH_01_POLICY','V5_AUTH_01_MISSING_PIN','V5_AUTH_01_MISMATCH_PIN','V5_AUTH_01_DEPENDENCY_HASH')
  V5_ARTIFACT_02=@('V5_ARTIFACT_02_JAR_HASH','V5_ARTIFACT_02_ACL_READ_REMOVED','JAR_HASH_EXACT_RECOVERY','ACL_READ_REMOVED_EXACT_RECOVERY')
  V5_RUNTIME_01=@('HALT_CONSUMED_WITHOUT_ATTEMPT','V5_RUNTIME_01_RESTART_ORIGINAL','PUBLISHED_BEFORE_CALLER_COMPLETION','V5_RUNTIME_01_PUBLISHED_RESTART_NO_FETCH')
  V5_RUNTIME_03=@('CANCEL_DURABLE_TERMINAL','V5_RUNTIME_03_CANCELLED_RESTART_NO_FETCH','V5_RUNTIME_03_LEASE_EXPIRED','V5_RUNTIME_03_LEASE_NO_TAKEOVER')
  V6_FRETES_01=@('V6_FRETES_01_FRETE_2_CONTIGUOUS','V6_FRETES_01_FRETE_1_CONTIGUOUS','V6_FRETES_01_FRETE_3_CONTIGUOUS','V6_FRETES_01_REPEAT','V6_FRETES_01_OPERATOR_STATUS','V6_FRETES_01_FINAL_SQL_PERIOD')
  V6_CALENDAR_01=@('V6_CALENDAR_01_YEAR_SQL_PERIOD','V6_CALENDAR_01_SHORT_DAY_SQL_PERIOD','V6_CALENDAR_01_LONG_DAY_SQL_PERIOD','GAP_ZERO_SQL','OVERLAP_ZERO_SQL')
  V7_RECOVERY_01=@('V7_RECOVERY_01_FRETES_PLAN_RESTART','V7_RECOVERY_01_MONTH_PLAN_RESTART','V7_RECOVERY_01_MONTH_RUN_RESTART','V7_RECOVERY_01_MONTH_SQL_PERIOD','CHANGED_PLAN_DURABLE_REFUSAL','V7_RECOVERY_01_FINAL_OPERATOR_RUN_REFUSED')
  V7_LIMITS_01=@('V7_LIMITS_01_BLACKOUT_PREVIEW','V7_LIMITS_01_BACKLOG_PREVIEW','V7_LIMITS_01_RECONCILIATION_LIMIT_CONTIGUOUS','DEGRADED_LIMIT_STOPPED','STALE_DURABLE_LEASE_REFUSAL','V7_LIMITS_01_STALE_RESTART_NO_TAKEOVER')
  V7_INCREMENTAL_01=@('V7_INCREMENTAL_01_HTTP_LOOKBACK_1','V7_INCREMENTAL_01_HTTP_LOOKBACK_2','V7_INCREMENTAL_01_SQL_FRONTIER_1','V7_INCREMENTAL_01_SQL_FRONTIER_2','V7_INCREMENTAL_01_RESTART_NO_FETCH_NO_RESET')
  V7_SINKS_06=@('DQ_POLICY_REFUSED_WITH_VALID_PARTITION_CONTROL','PHYSICAL_REQUIRED_SINK_TIMEOUT_REFUSED','V7_SINKS_06_FAILED_SINK_RESTART_NO_FETCH')
  V7_AUTH_SINK_01=@('PHYSICAL_AUTHORIZATION_SINK_BEFORE_COMMIT')
  V7_MANUAL_01=@('MANUAL_DIAGNOSE','MANUAL_PLAN','V7_MANUAL_01_RUN','V7_MANUAL_01_STATUS_OPERATOR','V7_MANUAL_01_STATUS_SERVICE','MANUAL_CONTROLLER_COMPLETED')
  V021_SCHEMA_01=@('upgrade','baseline-tail','V021_APPLIED_VERIFIED')
 }
 foreach($folder in $required.Keys){foreach($id in $required[$folder]){Require-Case $folder $id|Out-Null}}
 $dq=Require-Case 'V7_SINKS_06' 'V7_SINKS_06_DQ_POLICY_ABSENT'
 $sink=Require-Case 'V7_SINKS_06' 'V7_SINKS_06_REQUIRED_SINK_TIMEOUT'
 $auth=Require-Case 'V7_AUTH_SINK_01' 'V7_AUTH_SINK_01_FAIL_BEFORE_DECISION_COMMIT'
 if($dq.http -ne 3 -or $dq.observed.attempts -ne 1 -or $dq.observed.publications -ne 0 -or $dq.observed.state -cne 'FAILED' -or $sink.http -ne 2 -or $sink.observed.state -cne 'FAILED' -or $sink.observed.publications -ne 0 -or $auth.http -ne 0 -or $auth.observedExit -ne 20 -or $auth.observed.decisions -ne 0 -or $auth.observed.consumptions -ne 0 -or $auth.observed.attempts -ne 0){throw 'DQ_OR_SINK_TEST_DID_NOT_REACH_INTENDED_BOUNDARY'}
 if(@($m.excludedProofs|Where-Object reason -eq 'EXPIRED_PARTITION_LEASE_PREVENTED_ATTEMPT_NOT_DQ').Count -ne 1){throw 'FALSE_DQ_EVIDENCE_MUST_REMAIN_EXCLUDED'}
 $http=42;$jar=39
 foreach($path in $m.scenarioFiles|Where-Object {$_ -like ($base+'*')}){
  foreach($total in @($cases[$path]|Where-Object id -eq HTTP_TOTAL)){$http+=$total.requests}
  $jar+=@($cases[$path]|Where-Object {$_.PSObject.Properties.Name -contains 'layer' -and $_.layer -in @('OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK','MANUAL_OFFICIAL_JAR_REAL_WINDOWS_SQL')}).Count
 }
 if($http -ne $m.httpRequests -or $jar -ne $m.jarCasesExecuted){throw 'B54_CUMULATIVE_JAR_HTTP_COUNTS_DRIFT'}
 $events=@(Get-Content (Join-Path $root 'target/bloco54/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -DateKind String})
 $res=@($events|Where-Object type -eq RESERVE);$opens=@($events|Where-Object type -eq OPEN);$closes=@($events|Where-Object type -eq CLOSE);$extensions=@($events|Where-Object type -eq EXTEND)
 if($res.Count -ne 302 -or $opens.Count -ne 30 -or $closes.Count -ne 30 -or @($res.id|Select-Object -Unique).Count -ne 302 -or $extensions.Count -ne 2){throw 'B54_LEDGER_COUNT_OR_UNIQUENESS_DRIFT'}
 $maximum=128
 foreach($extension in $extensions){
  if($extension.completionApproval -cne 'OWNER_APPROVED_B54_LOCAL_COMPLETION' -or $extension.previousMaximum -ne $maximum -or $extension.additionalUnits -ne 96 -or $extension.maximumUnits -ne ($maximum+96) -or @($m.additionalBatches|Where-Object sha256 -CEQ $extension.batchSha256).Count -ne 1){throw 'UNAPPROVED_BUDGET_EXTENSION'}
  $maximum=$extension.maximumUnits
 }
 for($i=0;$i -lt $res.Count;$i++){if($res[$i].ordinal -ne ($i+1) -or $res[$i].maximumEntries -ne 16 -or $res[$i].maximumPages -ne 4 -or $res[$i].maximumDerived -ne 512){throw 'B54_RESERVATION_LIMIT_DRIFT'}}
 foreach($open in $opens){
  $close=@($closes|Where-Object campaign -eq $open.campaign)
  if($close.Count -ne 1 -or [DateTimeOffset]::Parse($close[0].utc) -gt [DateTimeOffset]::Parse($open.deadline)){throw 'B54_OPEN_CAMPAIGN_OR_DEADLINE_DRIFT'}
  foreach($r in $res|Where-Object campaign -eq $open.campaign){if([DateTimeOffset]::Parse($r.utc) -lt [DateTimeOffset]::Parse($open.utc) -or [DateTimeOffset]::Parse($r.utc) -gt [DateTimeOffset]::Parse($close[0].utc)){throw 'RESERVATION_OUTSIDE_CAMPAIGN'}}
 }
 Assert-Hash ([pscustomobject]@{path='target/bloco53/cumulative-reservations.txt';sha256=$m.b53LedgerSha256})
 $build=Get-Content (Join-Path $root $m.finalVerifyLog) -Raw
 foreach($text in @('Tests run: 1098, Failures: 0, Errors: 0, Skipped: 4','All coverage checks have been met.','BUILD SUCCESS')){if(-not $build.Contains($text)){throw 'FINAL_JAVA_VERIFY_NOT_PROVEN'}}
 $pres=Get-Content (Join-Path $root ($base+'V7_MANUAL_01/preservation-after.log')) -Raw
 if(-not $pres.Contains('"priorAttempts":95,"priorPublications":75,"retainedExtracting":8,"b54Attempts":45,"b54Publications":30,"b54Pages":70,"b54Entries":74') -or $pres -notmatch '(?m)^0 0\s*$'){throw 'FINAL_SQL_PRESERVATION_NOT_PROVEN'}
 $catalog=Get-Content (Join-Path $root ($base+'V7_MANUAL_01/schema-after.log')) -Raw
 if(-not $catalog.Contains('SCHEMA_SHA256='+$m.catalogSha256) -or -not $catalog.Contains('DATA_ROWS=6723')){throw 'FINAL_CATALOG_NOT_PROVEN'}
 Import-Module (Join-Path $PSScriptRoot 'Bloco54Artifact.psm1') -Force
 Read-Bloco54Bundle (Join-Path $root $m.bundle) $m.artifactManifestSha256|Out-Null
 $diff=Read-Json 'target/bloco54/jar-entry-diff-reviewed-bundle-v7.json'
 if(@($diff|Where-Object {-not $_.administrative -and $_.sha256 -cne $_.originalSha256}).Count -or @($diff|Where-Object {$_.entry -match '(?i)fixture|harness|fakeverifier'}).Count){throw 'OFFICIAL_JAR_ENTRY_PROVENANCE_DRIFT'}
 $comparison=Get-Content (Join-Path $root 'target/bloco54/comparison.log') -Raw
 if(-not $comparison.Contains('CMP_SYNTHETIC_SIX_CASES_PASS_REAL_PARITY_NOT_TESTED')){throw 'SYNTHETIC_COMPARISON_NOT_PROVEN'}
}
& (Join-Path $PSScriptRoot 'Test-Bloco54HistoricalCheckpoint.ps1') -IncludePrivateEvidence:$IncludePrivateEvidence
'B54_LOCAL_A_G_HISTORICAL_PROOFS_PASS_CURRENT_ACCEPTANCES_CHECKED_SEPARATELY'

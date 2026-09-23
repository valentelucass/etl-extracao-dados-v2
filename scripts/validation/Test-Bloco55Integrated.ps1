#Requires -Version 7.0
param([switch]$IncludePrivateEvidence,[switch]$RequireComplete)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Read-Json([string]$Path){Get-Content -LiteralPath (Join-Path $root $Path) -Raw|ConvertFrom-Json -DateKind String}
function Hash-Proof($Entry){
 if($Entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Entry.path.Contains('..') -or $Entry.path.StartsWith('/')){throw 'B55_EVIDENCE_PATH'}
 if((Get-FileHash -LiteralPath (Join-Path $root $Entry.path)).Hash.ToLowerInvariant() -cne $Entry.sha256){throw ('B55_EVIDENCE_HASH_CHANGED_'+$Entry.path)}
}
$m=Read-Json 'database/manifest/runtime-bloco55.json'
if($m.version -ne 1 -or $m.block -ne 55 -or $m.status -cne 'LOCAL_A_J_COMPLETE' -or -not $m.integrationAccepted -or ($m.frontsCompleted -join ',') -cne 'A,B,C,D,E,F,G,H,I,J' -or $m.frontsPending.Count -ne 0){throw 'B55_FULL_ACCEPTANCE_NOT_PROVEN'}
if($m.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $m.installedThrough -cne 'V023' -or $m.catalogSha256 -cne 'ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae' -or $m.artifactManifestSha256 -cne 'a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13' -or $m.originalUntil -cne '2026-10-07T22:34:30.615Z' -or $m.renewed -or $m.realSourceExecuted -or $m.productionExecuted -or $m.scopeOrAccountsMissing){throw 'B55_EXACT_TARGET_RIGHTS_VALIDITY_REQUIRED'}
foreach($pair in @(@('grants',32),@('grantDelta',7),@('scopes',16),@('scopeDelta',6),@('mappings',2),@('serviceMappingVersion',17),@('operatorMappingVersion',1),@('originalScope1Version',10),@('otherOriginalScopesVersion',1),@('qualityHistorical',6),@('qualityActive',3),@('qualityRevoked',3),@('tariffReleases',1),@('tariffRows',2),@('maximumUnits',384),@('maximumHttp',2048),@('refundedUnits',0),@('tests',1138),@('testFailures',0),@('testErrors',0),@('testSkips',4),@('checkboxes',115),@('done',65),@('pending',50),@('openRoutes',193),@('b53Attempts',95),@('b53Publications',75),@('b53Extracting',8),@('b53Reservations',112),@('b54Attempts',45),@('b54Publications',30),@('b54Pages',70),@('b54Entries',74),@('b54Reservations',302),@('b54UnreusedUnits',18),@('comparisonCases',30),@('estimatedConsumers',9),@('temporalFalseNegativesReconciled',3),@('sqlFalseNegativesReconciled',4),@('restrictedSessions',0),@('observerTransactions',0))){if($m.($pair[0]) -ne $pair[1]){throw ('B55_EXACT_CHECKPOINT_DRIFT_'+$pair[0])}}
if(($m.validRuntimeGroups -join ',') -cne 'B55_SMOKE_03,B55_MATRIX_01,B55_MANUAL_STOP,B55_MANUAL_RESUME,B55_MANUAL_OPERATOR,B55_MANUAL_DEPENDENCY' -or ($m.excludedRuntimeGroups -join ',') -cne 'B55_SMOKE_01,B55_SMOKE_02' -or $m.pendingPhysical.Count -ne 0 -or ($m.completedPhysical -join ',') -cne 'B55_MANUAL_OPERATOR,B55_MANUAL_DEPENDENCY,B55_SQL_01' -or $m.blocker -cne ''){throw 'B55_FALSE_PASS_EXCLUSION_AND_PENDING_PROOFS_REQUIRED'}
Hash-Proof ([pscustomobject]@{path=$m.completedController;sha256=$m.completedControllerSha256})
if(($m.canonicalAcceptances -join ',') -cne 'V2-042b,V2-042c,V2-022b,V2-042'){throw 'B55_CANONICAL_ACCEPTANCE_DRIFT'}
if(@($m.proofFiles.path|Sort-Object -Unique).Count -ne $m.proofFiles.Count){throw 'B55_DUPLICATE_PROOF_PATH'}
foreach($required in @('target/bloco55/sql-fence-reconciliation.json','target/bloco55/runtime/B55_SQL_01/results.json','target/bloco55/runtime/B55_MANUAL_OPERATOR/results.json','target/bloco55/runtime/B55_MANUAL_OPERATOR/manual-summary.json','target/bloco55/runtime/B55_MANUAL_DEPENDENCY/results.json','target/bloco55/runtime/B55_MANUAL_DEPENDENCY/manual-summary.json','target/bloco55/snapshots/B55_READ_03/PRESERVATION.log','target/bloco55/snapshots/B55_READ_03/receipt.json','target/bloco55/snapshots/B55_SQL_01_DIAGNOSE/receipt.json','target/bloco55/batch-03.json')){
 if(@($m.proofFiles|Where-Object path -CEQ $required).Count -ne 1){throw 'B55_REQUIRED_COMPLETION_PROOF_ABSENT'}
}
foreach($entry in $m.proofFiles){if($IncludePrivateEvidence -or -not $entry.path.StartsWith('target/')){Hash-Proof $entry}}
& (Join-Path $PSScriptRoot 'Test-Bloco55Acceptances.ps1') -IncludePrivateEvidence:$IncludePrivateEvidence | Out-Null
$state=Get-Content (Join-Path $root 'STATES.md') -Raw
if([regex]::Matches($state,'(?m)^  - \[x\] \*\*V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS —').Count -ne 1){throw 'B55_LOCAL_SLICE_NOT_SYNCHRONIZED'}
if($IncludePrivateEvidence){
 $ledger=@(Get-Content (Join-Path $root 'target/bloco55/ledger.jsonl')|ForEach-Object{$_|ConvertFrom-Json -DateKind String})
 $res=@($ledger|Where-Object type -EQ RESERVE);$opens=@($ledger|Where-Object type -EQ OPEN);$closes=@($ledger|Where-Object type -EQ CLOSE);$batches=@($ledger|Where-Object type -EQ BATCH)
 if($res.Count -ne $m.reservations -or $opens.Count -ne $m.campaignsOpened -or $closes.Count -ne $opens.Count -or $batches.Count -ne $m.declaredBatches -or $batches.Count -gt 3 -or $res.Count -gt $batches[-1].maximumUnits -or $res.Count -gt 384 -or $opens.Count -gt 12){throw 'B55_BUDGET_RECONCILIATION'}
 for($i=0;$i -lt $res.Count;$i++){if($res[$i].ordinal -ne $i+1 -or $res[$i].maximumEntries -ne 16 -or $res[$i].maximumPages -ne 4 -or $res[$i].maximumDerived -ne 512){throw 'B55_UNIT_LIMIT_DRIFT'}}
 foreach($open in $opens){
  $close=@($closes|Where-Object campaign -EQ $open.campaign)
  if($close.Count -ne 1 -or [DateTimeOffset]::Parse($close[0].utc) -gt [DateTimeOffset]::Parse($open.deadline)){throw 'B55_CAMPAIGN_DEADLINE'}
  foreach($r in $res|Where-Object campaign -EQ $open.campaign){if([DateTimeOffset]::Parse($r.utc) -lt [DateTimeOffset]::Parse($open.utc) -or [DateTimeOffset]::Parse($r.utc) -gt [DateTimeOffset]::Parse($close[0].utc)){throw 'B55_RESERVATION_OUTSIDE_CAMPAIGN'}}
 }
 foreach($folder in $m.validRuntimeGroups){$results=@(Read-Json ('target/bloco55/runtime/'+$folder+'/results.json'));if(@($results|Where-Object {-not $_.passed}).Count){throw 'B55_REQUIRED_RUNTIME_GROUP_FAILED'}}
 & (Join-Path $PSScriptRoot 'Test-Bloco55TemporalEvidence.ps1') | Out-Null
 & (Join-Path $PSScriptRoot 'Test-Bloco55SqlFenceEvidence.ps1') | Out-Null
 foreach($receipt in @(Read-Json 'target/bloco55/temporal-reconciliation.json')+@(Read-Json 'target/bloco55/sql-fence-reconciliation.json')){foreach($entry in $receipt.proofFiles){Hash-Proof $entry}}
 $comparison=@(Read-Json 'target/bloco55/comparison/B55_COMPARE_02/results.json')
 if($comparison.Count -ne 5){throw 'FIVE_COMPARISON_TEMPLATES_REQUIRED'}
 foreach($vertical in $comparison){if($vertical.realParity -or $vertical.cases.Count -ne 6 -or @($vertical.cases|Where-Object {-not $_.passed}).Count -or ($vertical.cases.case_id -join ',') -cne 'EQUAL,FIELD_MISMATCH,DUPLICATE,ABSENT,WINDOW_MISMATCH,INCOMPLETE'){throw 'B55_COMPARISON_NOT_PROVEN'}}
 $measured=@()
 foreach($file in $m.measurementFiles){
  $rows=@(Read-Json $file)
  if($rows.Count -ne 3 -or ($rows.pages -join ',') -cne '16,256,4096'){throw 'B55_THREE_PIPELINE_SCALES_REQUIRED'}
  $measured+=$rows[0].template
  foreach($row in $rows){if($row.records -ne $row.pages*8 -or $row.batches -ne $row.pages -or $row.peakBatch -ne 1 -or $row.finalInFlight -ne 0 -or -not $row.releasedWeakBatchSample -or $row.heapPlateauProven -or $row.productionScaleProven -or $row.maximumHeapBytes -gt 536870912 -or $row.durationNanos -gt 120000000000){throw 'B55_MANAGED_PIPELINE_LIMIT_NOT_PROVEN'}}
 }
 if((@($measured|Sort-Object -Unique) -join ',') -cne 'COLETAS,COTACOES,FRETES,LOCALIZACAO_CARGAS,MANIFESTOS'){throw 'FIVE_TYPED_PIPELINE_RECEIPTS_REQUIRED'}
 $plans=@(Read-Json 'target/bloco55/plans/B55_PLANS_05/results.json')
 if($plans.Count -ne 9){throw 'NINE_ESTIMATED_CONSUMERS_REQUIRED'}
 foreach($consumer in $plans){
  if(-not $consumer.passed -or $consumer.executedPersistentDml -or $consumer.runtimeShowplanGrant -or $consumer.measuredPerformance -or $consumer.plans.Count -lt 1 -or $consumer.plans.Count -gt 512){throw 'ESTIMATED_SQL_INSPECTION_LIMIT'}
  foreach($plan in $consumer.plans){if($plan.bytes -gt 1048576 -or -not $plan.statementPlan){throw 'ESTIMATED_PLAN_BYTE_CAP'};Hash-Proof ([pscustomobject]@{path='target/bloco55/plans/B55_PLANS_05/'+$plan.file;sha256=$plan.sha256})}
 }
 $stop=Read-Json 'target/bloco55/runtime/B55_MANUAL_STOP/manual-summary.json';$resume=Read-Json 'target/bloco55/runtime/B55_MANUAL_RESUME/manual-summary.json'
 if($stop.processed -ne 3 -or -not $stop.interruptedByOwnerLimit -or $resume.processed -ne 20 -or $resume.automaticRetry -or -not $resume.sqlOwnsState -or $stop.manifestSha256 -cne $resume.manifestSha256){throw 'MANUAL_BOUNDED_RESTART_REQUIRED'}
 for($i=0;$i -lt 20;$i++){
  $row=$resume.summary[$i];$outcome=if($i -lt 3){'ALREADY_CONFIRMED'}else{'PUBLISHED'}
  if($row.outcome -cne $outcome -or $row.sqlPublications -ne 1){throw 'MANUAL_SQL_OUTCOME_NOT_PROVEN'}
  if($i -lt 3 -and ($row.http -ne 0 -or $row.invocationId -ceq $stop.summary[$i].invocationId -or $row.requestSha256 -cne $stop.summary[$i].requestSha256)){throw 'MANUAL_FROZEN_NEW_INVOCATION_NO_HTTP_REQUIRED'}
 }
 $negative=@(Read-Json $m.manualValidator);if($negative.Count -ne 8 -or @($negative|Where-Object {-not $_.passed}).Count){throw 'MANUAL_EIGHT_PREFLIGHT_MUTANTS_REQUIRED'}
 $operator=Read-Json 'target/bloco55/runtime/B55_MANUAL_OPERATOR/manual-summary.json'
 if($operator.operation -cne 'Status' -or $operator.processed -ne 5 -or $operator.manifestSha256 -cne $resume.manifestSha256 -or $operator.automaticRetry -or -not $operator.sqlOwnsState){throw 'MANUAL_OPERATOR_STATUS_REQUIRED'}
 for($i=0;$i -lt 5;$i++){
  $row=$operator.summary[$i]
  if($row.outcome -cne 'ALREADY_CONFIRMED' -or $row.http -ne 0 -or $row.sqlPublications -ne 1 -or $row.requestSha256 -cne $resume.summary[$i].requestSha256){throw 'MANUAL_OPERATOR_SQL_STATE_REQUIRED'}
 }
 $operatorCases=@(Read-Json 'target/bloco55/runtime/B55_MANUAL_OPERATOR/results.json'|Where-Object {$_.psobject.Properties.Name -contains 'account'})
 if($operatorCases.Count -ne 5 -or @($operatorCases|Where-Object {$_.account -cne 'etl_v2_view' -or $_.command -cne 'status'}).Count){throw 'REAL_OPERATOR_ACCOUNT_REQUIRED'}
 $dependency=Read-Json 'target/bloco55/runtime/B55_MANUAL_DEPENDENCY/manual-summary.json'
 if($dependency.processed -ne 5 -or $dependency.automaticRetry -or -not $dependency.sqlOwnsState -or ($dependency.summary.outcome -join ',') -cne 'FAILED,DEPENDENCY_BLOCKED,PUBLISHED,PUBLISHED,PUBLISHED' -or ($dependency.summary.http -join ',') -cne '3,0,4,4,4' -or ($dependency.summary.sqlPublications -join ',') -cne '0,0,1,1,1'){throw 'MANUAL_DEPENDENCY_ISOLATION_REQUIRED'}
 $diagnose=Read-Json 'target/bloco55/snapshots/B55_SQL_01_DIAGNOSE/receipt.json'
 if(-not $diagnose.passed -or $diagnose.grants -ne 32 -or $diagnose.scopes -ne 16){throw 'MANUAL_DIAGNOSE_REAL_SQL_REQUIRED'}
 $sql=Read-Json ($m.sqlSnapshot+'/receipt.json');if(-not $sql.passed -or $sql.b55.b55Publications -ne $m.newPublications -or $sql.b55.b55Attempts -ne $m.newAttempts){throw 'OWN_SQL_COUNTS_NOT_PROVEN'}
 $review=Read-Json ($m.review+'/receipt.json');if(-not $review.passed -or $review.initialPresent -ne 1230 -or $review.preservedAppliedMigrations -ne 21 -or $review.b54PrivateProofsPreserved -ne 35){throw 'B53_B54_PRESERVATION_NOT_PROVEN'}
 $inventory=Read-Json ($m.review+'/current-inventory.json')
 if($inventory.Count -ne $review.currentFiles -or $inventory.Count -gt 2000){throw 'FINAL_REVIEW_INVENTORY_BOUND'}
 # Preserve REVIEW_05 as the historical tree. The separately verified follow-up
 # admits only four exact document/validator revisions, never runtime code/DDL.
 $documentationDelta=$null
 if(Test-Path (Join-Path $root 'docs/continuidade/manifesto-pos-bloco55.json')){
  $documentationDelta=& (Join-Path $PSScriptRoot 'Test-ContinuidadeAgentes.ps1') -AsMap -IncludePrivateEvidence
 }
 foreach($entry in $inventory){
  if($null -ne $documentationDelta -and $documentationDelta.ContainsKey($entry.path)){
   $delta=$documentationDelta[$entry.path]
   if($delta.before -cne $entry.sha256){throw 'B55_DOCUMENT_CONTINUATION_BASELINE_MISMATCH'}
   Hash-Proof ([pscustomobject]@{path=$entry.path;sha256=$delta.after})
  }else{Hash-Proof $entry}
 }
 foreach($pair in @(@('target/bloco55/guards-b55-04.log','B55_COMPLETION_NINE_CONTRAPROOFS_PASS'),@('target/bloco55/guards-b54-02.log','B54_COMPLETION_GUARDS_6_CONTRAPROOFS_PASS'),@('target/bloco55/static-final-02.log','B55_STATIC_FINAL_ALL_REQUESTED_GATES_PASS'),@('target/bloco55/secret-scan-04.log','findings=0'))){
  if(-not (Get-Content -LiteralPath (Join-Path $root $pair[0]) -Raw).Contains($pair[1])){throw 'FINAL_OFFLINE_GATE_RECEIPT_REQUIRED'}
 }
 $quiescence=Read-Json 'target/bloco55/loopback-quiescence-final.json'
 if(-not $quiescence.passed -or $quiescence.listeners -ne 0 -or $quiescence.stoppedForeignProcesses){throw 'OWN_LOOPBACK_QUIESCENCE_REQUIRED'}
}
if($RequireComplete -and ($m.pendingPhysical.Count -ne 0 -or -not $m.integrationAccepted)){throw 'B55_REQUIRED_COMPLETION_PROOF_ABSENT'}
if($IncludePrivateEvidence){
 if($null -ne $documentationDelta){'B55_HISTORICAL_A_J_PROOFS_PASS_EXACT_DOCUMENTATION_CONTINUATION'}else{'B55_LOCAL_A_J_COMPLETE_ALL_REQUIRED_EVIDENCE_PASS'}
}else{'B55_LOCAL_A_J_MANIFEST_AND_CANONICAL_STATE_PASS_PRIVATE_PROOFS_NOT_REOPENED'}

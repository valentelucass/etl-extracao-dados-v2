#Requires -Version 7.0
param([switch]$WriteReceipt)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$base=Join-Path $root 'target/bloco55/runtime/B55_TIME_01'
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Read-Proof([string]$Name){
 $path=Join-Path $base $Name
 if((Get-Item -LiteralPath $path).Length -gt 64KB){throw 'TEMPORAL_PROOF_BOUND'}
 $utf8.GetString([IO.File]::ReadAllBytes($path))
}
$original=Read-Proof 'results.json'|ConvertFrom-Json -DateKind String
$receipts=@()
foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $prefix='B55_TIME_01_'+$template
 $persist=@($original|Where-Object id -CEQ ($prefix+'_PERSIST'))
 $durable=@($original|Where-Object id -CEQ ($prefix+'_TWO_WINDOWS_DURABLE'))
 if($persist.Count -ne 1 -or $persist[0].observedExit -ne 0 -or $persist[0].http -ne 0 -or $persist[0].passed -ne $false -or $durable.Count -ne 1 -or $durable[0].planned -ne 2 -or -not $durable[0].passed){throw 'EXACT_HISTORICAL_FALSE_NEGATIVE_REQUIRED'}
 if((Read-Proof ($prefix+'_PERSIST.log')) -cnotmatch '(?m)^TEMPORAL_PERSISTED windows=2 extracted=0 scheduler=0\s*$'){throw 'ACTUAL_TEMPORAL_PERSISTENCE_MARKER_REQUIRED'}
 $planned=(Read-Proof ($prefix+'_PLANNED.sql.log')).Trim()|ConvertFrom-Json
 if($planned.planned -ne 2){throw 'INDEPENDENT_SQL_PLANNED_WINDOWS_REQUIRED'}
 foreach($step in @('SECOND','GAP','REPEAT')){
  $case=@($original|Where-Object id -CEQ ($prefix+'_'+$step))
  $expectedHttp=if($step -ceq 'REPEAT'){0}else{4}
  if($case.Count -ne 1 -or -not $case[0].passed -or $case[0].observedExit -ne 0 -or $case[0].http -ne $expectedHttp -or $case[0].observed.publications -ne 1 -or $case[0].observed.consumptions -ne 1){throw 'TEMPORAL_PUBLICATION_RECOVERY_REQUIRED'}
  $frontier=if($step -ceq 'SECOND'){'2032-02-28T03:00:00Z'}else{'2032-03-01T03:00:00Z'}
  if(-not (Read-Proof ($prefix+'_'+$step+'.log')).Contains('TEMPORAL_EXECUTION_RECONCILIATION contiguous='+$frontier)){throw 'EXACT_CONTIGUOUS_FRONTIER_REQUIRED'}
 }
 $first=Read-Proof ($prefix+'_GAP.request.json')|ConvertFrom-Json -DateKind String
 $second=Read-Proof ($prefix+'_SECOND.request.json')|ConvertFrom-Json -DateKind String
 $repeat=Read-Proof ($prefix+'_REPEAT.request.json')|ConvertFrom-Json -DateKind String
 if($first.businessStart -cne '2032-02-28' -or $second.businessStart -cne '2032-02-29' -or $first.executionId -cne $repeat.executionId -or $first.invocationId -ceq $repeat.invocationId){throw 'LEAP_BOUNDARY_NEW_INVOCATION_REQUIRED'}
 foreach($kind in @('REQUEST','POLICY')){
  $negative=@($original|Where-Object id -CEQ ($prefix+'_'+$kind+'_CHANGED'))
  if($negative.Count -ne 1 -or -not $negative[0].passed -or $negative[0].observedExit -ne 2 -or $negative[0].http -ne 0 -or $negative[0].observed.decisions -ne 0){throw 'MATERIAL_CHANGE_PREFLIGHT_REQUIRED'}
 }
 $proofs=@('results.json',($prefix+'_PERSIST.log'),($prefix+'_PLANNED.sql.log'),($prefix+'_SECOND.log'),($prefix+'_GAP.log'),($prefix+'_REPEAT.log'))|ForEach-Object{[ordered]@{path='target/bloco55/runtime/B55_TIME_01/'+$_;sha256=(Get-FileHash -LiteralPath (Join-Path $base $_)).Hash.ToLowerInvariant()}}
 $receipts+=[ordered]@{template=$template;layer='INDEPENDENT_RECONCILIATION_OF_IMMUTABLE_JAR_AND_SQL_RECEIPTS';originalFalseNegativePreserved=$true;reason='WRONG_CONTROLLER_TEXT_LABEL';plannedWindows=2;publications=2;http=8;passed=$true;proofFiles=$proofs}
}
if($WriteReceipt){
 $destination=Join-Path $root 'target/bloco55/temporal-reconciliation.json'
 if(Test-Path $destination){throw 'EXISTING_TEMPORAL_RECONCILIATION_PRESERVE'}
 [IO.File]::WriteAllText($destination,($receipts|ConvertTo-Json -Depth 7),$utf8)
}
'B55_TEMPORAL_THREE_FALSE_NEGATIVES_RECONCILED_FROM_IMMUTABLE_PROOFS_PASS'

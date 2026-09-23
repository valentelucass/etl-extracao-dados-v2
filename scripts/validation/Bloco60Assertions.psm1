#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Test-Bloco60ScalarEqual($Left,$Right){
 if($null -eq $Left -or $null -eq $Right){return ($null -eq $Left -and $null -eq $Right)}
 return [string]$Left -ceq [string]$Right
}
function Assert-Bloco60Case($Case,$Observed,[int]$Exit,[int]$Http,[string]$Output,[bool]$Limited){
 if($Limited -or $Exit -notin $Case.expectedExit -or $Http -notin $Case.expectedHttp){throw ('B60_PROCESS_HTTP_OR_EXIT_'+$Case.id)}
 foreach($key in $Case.expected.Keys){
  if(-not $Observed.Contains($key) -or -not (Test-Bloco60ScalarEqual $Observed[$key] $Case.expected[$key])){throw ('B60_SQL_ASSERTION_'+$Case.id+'_'+$key)}
 }
 if($Observed.publications -eq 1 -and ($Observed.seals -ne 1 -or $Observed.quality -cne 'PASSED' -or $Observed.completedChecks -ne 4 -or $Observed.passedChecks -ne 4 -or $Observed.failedChecks -ne 0 -or $Observed.applicationRows -ne $Observed.candidateRows)){throw ('B60_PUBLICATION_GATES_'+$Case.id)}
 if($Case.fault -cne 'NONE' -and $Exit -ne 86 -and $Case.fault -cne 'CONSUME_ACK_LOST' -and -not $Output.Contains('fault_triggered=true')){throw ('B60_FAULT_NOT_TRIGGERED_'+$Case.id)}
 if($Case.fault -ceq 'CONSUME_ACK_LOST' -and $Observed.consumptions -ne 1){throw 'B60_CONSUMPTION_UNKNOWN_NOT_RECONCILED'}
 if($Http -gt 0 -and $Case.fault -notlike 'HALT_*'){
  $match=[regex]::Match($Output,'RUNTIME_HTTP_ATTEMPTS [^\r\n]*total=([0-9]+)')
  if(-not $match.Success -or [int]$match.Groups[1].Value -ne $Http){throw ('B60_HTTP_OBSERVER_RECONCILIATION_'+$Case.id)}
 }
 if($Observed.publications -eq 0 -and ($Observed.applicationRows -ne 0 -or $Observed.typedHistory -ne 0)){throw ('B60_NEGATIVE_HAS_TYPED_EFFECT_'+$Case.id)}
}
function Assert-Bloco60Unchanged($Before,$After){
 foreach($key in @('publications','pages','stageRows','candidateRows','applicationRows','typedHistory','typedReceiptHash','historyHash','currentHash')){
  if(-not $Before.Contains($key) -or -not $After.Contains($key) -or -not (Test-Bloco60ScalarEqual $Before[$key] $After[$key])){throw ('B60_REPEATED_DURABLE_EFFECT_'+$key)}
 }
}
function Assert-Bloco60PreservedRows([string]$Before,[string]$After){
 $prior=@{};$current=@{}
 foreach($pair in @(@($Before,$prior),@($After,$current))){
  foreach($line in $pair[0] -split "`r?`n"|Where-Object {$_.Trim().StartsWith('{')}){
   $row=$line|ConvertFrom-Json
   if(-not $pair[1].ContainsKey($row.table_name)){$pair[1][$row.table_name]=@{}}
   $counts=$pair[1][$row.table_name]
   foreach($hash in $row.hashes -split ','|Where-Object {$_}){if($hash -cnotmatch '^[a-f0-9]{64}$'){throw 'B60_ROW_HASH_FORMAT'};if(-not $counts.ContainsKey($hash)){$counts[$hash]=0};$counts[$hash]++}
  }
 }
 if($prior.Count -eq 0 -or $current.Count -ne $prior.Count){throw 'B60_ROW_TABLE_SET'}
 foreach($table in $prior.Keys){
  if(-not $current.ContainsKey($table)){throw 'B60_ROW_TABLE_MISSING'}
  foreach($hash in $prior[$table].Keys){if(-not $current[$table].ContainsKey($hash) -or $current[$table][$hash] -lt $prior[$table][$hash]){throw 'B60_HISTORICAL_ROW_CHANGED'}}
 }
}
Export-ModuleMember Assert-Bloco60Case,Assert-Bloco60Unchanged,Assert-Bloco60PreservedRows

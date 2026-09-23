#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Set-Location $root
Import-Module ./scripts/validation/Bloco60ConcurrentPair.psm1 -Force
function Exercise([string]$Mode){
 $script:events=[Collections.Generic.List[string]]::new()
 $args=@{
  StartBarrier={ $script:events.Add('START_BARRIER');@{ProcessId=101;Ready=$false} }
  ReadBarrierReady={param($b)$script:events.Add('SQL_READY');@{barrierPid=101;grantedApplicationLocks=$(if($Mode -eq 'NOT_READY'){0}else{1});sessions=1}}
  StartFirst={$script:events.Add('START_A');'A'}
  StartSecond={$script:events.Add('START_B');'B'}
  ObservePair={param($b,$a,$c)$script:events.Add('OBSERVE');if($Mode -eq 'OBSERVATION_FAIL'){throw 'OBSERVATION_FAILED'}}
  FinishBarrier={param($b)$script:events.Add('BARRIER_RECORDED')}
  FinishChild={param($c)$script:events.Add('RECORDED_'+$c);if($Mode -eq 'FIRST_RESULT_FAIL' -and $c -eq 'A'){throw 'FIRST_RESULT_FAILED'}}
  StopOwned={$script:events.Add('STOP_OWNED')}
 }
 $caught='NONE';try{Invoke-Bloco60ConcurrentPair @args}catch{$caught=$_.Exception.Message}
 @{events=($script:events -join ',');reason=$caught}
}
$ok=Exercise 'SUCCESS'
if($ok.reason -cne 'NONE' -or $ok.events -cne 'START_BARRIER,SQL_READY,START_A,START_B,OBSERVE,BARRIER_RECORDED,RECORDED_A,RECORDED_B'){throw 'PAIR_SQL_READINESS_SEQUENCE'}
$bad=Exercise 'NOT_READY';if($bad.reason -cne 'B60_AUTHORITATIVE_BARRIER_NOT_READY' -or $bad.events.Contains('START_A')){throw 'PAIR_UNPROVEN_READY_LAUNCH'}
$bad=Exercise 'OBSERVATION_FAIL';if($bad.reason -cne 'OBSERVATION_FAILED' -or -not $bad.events.EndsWith('STOP_OWNED,BARRIER_RECORDED,RECORDED_A,RECORDED_B')){throw 'PAIR_LOST_RESULTS_ON_OBSERVATION_FAILURE'}
$bad=Exercise 'FIRST_RESULT_FAIL';if($bad.reason -cne 'FIRST_RESULT_FAILED' -or -not $bad.events.EndsWith('RECORDED_A,RECORDED_B')){throw 'PAIR_SECOND_RESULT_LOST'}
[void][Reflection.Assembly]::LoadFrom('C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll')
$text=(Get-Content database/proposals/bloco60-concurrency-correction/barrier-ready.sql -Raw).Replace(':On Error exit','').Replace('$(B60BarrierPid)','101');$errors=$null;$reader=[IO.StringReader]::new($text)
try{$null=[Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser]::new($true).Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
if($errors.Count){throw 'PAIR_READINESS_SQL_PARSE'}
@{passed=$true;scenarios=4;readinessIndependentOfBufferedMarker=$true;refusedWithoutSqlReadiness=$true;bothResultsRetainedAfterObservationFailure=$true;secondResultRetainedAfterFirstFailure=$true;sqlParsed=$true;sqlExecuted=$false;jvms=0}|ConvertTo-Json

#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Set-Location $root
$folder=Join-Path $root 'database/proposals/bloco60-restante'
function Json([string]$Path){Get-Content $Path -Raw|ConvertFrom-Json -AsHashtable -Depth 30}
$original=Json 'database/proposals/bloco60-correcao/matrix.json';$matrix=Json (Join-Path $folder 'matrix.json');$carried=Json (Join-Path $folder 'carried-cases.json')
if($matrix.Count -ne 43 -or $carried.Count -ne 32 -or (@($matrix.id)+@($carried.id)|Sort-Object -Unique).Count -ne 75){throw 'REMAINING_CASE_SET'}
foreach($case in $matrix|Where-Object id -CNE 'USERS_CANCEL_RECHECK'){
 $old=@($original|Where-Object id -CEQ $case.id)
 if($old.Count -ne 1){throw 'REMAINING_EXACT_CASE'}
 foreach($key in $old[0].Keys){if(($case[$key]|ConvertTo-Json -Depth 12 -Compress) -cne ($old[0][$key]|ConvertTo-Json -Depth 12 -Compress)){throw ('REMAINING_ORACLE_CHANGED_'+$case.id+'_'+$key)}}
}
$replays=@($matrix|Where-Object command -CEQ 'replay');$oldWrongWindows=0
foreach($case in $replays){
 $r=Json (Join-Path $folder $case.request);$oldRequest=Json ('database/proposals/bloco60-correcao/'+$case.request)
 $origin=Json ('database/proposals/bloco60-correcao/requests/'+$case.id.Substring(7)+'.json')
 if($oldRequest.start -cne $origin.start){$oldWrongWindows++}
 if($r.replayOf -cne $origin.executionId -or $r.start -cne $origin.start -or $r.endExclusive -cne $origin.endExclusive){throw 'REPLAY_SQL_CANONICAL_WINDOW'}
}
if($oldWrongWindows -ne 2){throw 'REPLAY_DEFECT_NOT_REPRODUCED'}
$dom='C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
[void][Reflection.Assembly]::LoadFrom($dom)
function Expand([string]$Path){$text=[IO.File]::ReadAllText($Path);$text=[regex]::Replace($text,'(?m)^:r "([^"]+)"\s*$',{param($m) Expand (Join-Path $folder $m.Groups[1].Value)});[regex]::Replace($text,'(?m)^:On Error exit\s*$','')}
foreach($name in @('activate.sql','compensate.sql','collision-preflight.sql')){
 $errors=$null;$reader=[IO.StringReader]::new((Expand (Join-Path $folder $name)))
 try{$tree=[Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser]::new($true).Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
 if($errors.Count){throw ('REMAINING_SQL_PARSE_'+$name)}
 foreach($batch in $tree.Batches){$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase);foreach($s in $batch.Statements){$v=switch($s.GetType().Name){'DeclareVariableStatement'{@($s.Declarations|ForEach-Object {$_.VariableName.Value})};'DeclareTableVariableStatement'{@($s.Body.VariableName.Value)};default{@()}};foreach($n in $v){if(-not $names.Add($n)){throw 'REMAINING_SQL_DUPLICATE'}}}}
}
Import-Module ./scripts/validation/Bloco60RemainingBudget.psm1 -Force
$prior=@();foreach($path in @('target/bloco60-local/physical-corrective/cc4f84cb37f69724/ledger.jsonl','target/bloco60-local/physical-continuation/0560a96925160b1e/ledger.jsonl')){$prior+=@(Get-Content $path|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$now=[DateTimeOffset]'2026-09-10T04:05:00Z';$guards=0
function Check($event,$current=@(),$at=$now){Assert-Bloco60RemainingBudget -Event $event -Prior $prior -Current $current -Now $at}
function Reserved([string]$Kind,[int]$Count){@(1..$Count|ForEach-Object {@{type='RESERVE';kind=$Kind;utc=$now.ToString('o');emergency=$false}})}
function Refuse([scriptblock]$Body,[string]$Expected){$r='ACCEPTED';try{& $Body}catch{$r=$_.Exception.Message};if($r -cne $Expected){throw ('REMAINING_GUARD_'+$r)};$script:guards++}
Check @{type='OPEN'};Check @{type='RESERVE';kind='JVM'} (Reserved 'JVM' 46)
Refuse {Check @{type='RESERVE';kind='JVM'} (Reserved 'JVM' 47)} 'B60N_CUMULATIVE_JVM'
Check @{type='RESERVE';kind='SQL'} (Reserved 'READBACK' 180)
Refuse {Check @{type='RESERVE';kind='SQL'} (Reserved 'READBACK' 181)} 'B60N_CUMULATIVE_SQL'
Refuse {Check @{type='OPEN'} @() ([DateTimeOffset]'2026-09-10T04:40:11Z')} 'B60N_DEADLINE_NO_RENEWAL'
foreach($file in @('scripts/validation/Invoke-Bloco60Remaining.ps1','scripts/validation/Bloco60RemainingBudget.psm1')){$errors=$null;$tokens=$null;$null=[Management.Automation.Language.Parser]::ParseFile((Join-Path $root $file),[ref]$tokens,[ref]$errors);if($errors.Count){throw 'REMAINING_POWERSHELL_PARSE'}}
@{passed=$true;carried=32;remaining=42;cancellationRecheck=1;sqlBatchesParsed=3;replayWindowDefectsReproduced=2;replayWindowsCorrected=2;budgetGuards=$guards;priorSql=51;priorJvms=33;sqlExecuted=$false}|ConvertTo-Json

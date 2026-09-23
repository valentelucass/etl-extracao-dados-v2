#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Set-Location $root
function Json([string]$p){Get-Content $p -Raw|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String}
$folder='database/proposals/bloco60-seal-restante';$q=Json ($folder+'/quality-references.json')
$matrix=Json ($folder+'/matrix.json');$old=Json 'database/proposals/bloco60-audit-restante/matrix.json'
if($matrix.Count -ne 17 -or (@($matrix.id)-join ',') -cne (@($old|Select-Object -Skip 10).id-join ',')){throw 'EXACT_REMAINING_36'}
foreach($row in $q){$sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($row.material))).ToLowerInvariant();if($sha -cne $row.fingerprint -or -not $row.material.Contains('|2026-09-10T00:00:00.128|') -or -not (Get-Content ($folder+'/seed-quality.sql') -Raw).Contains($sha)){throw 'FINAL_DQ_CANONICAL'}}
$maps=@{};foreach($key in @('invocationId','executionId','cycleId','idempotencyKey')){$maps[$key]=@{}}
foreach($case in $matrix){
 $o=Json ('database/proposals/bloco60-audit-restante/'+$case.request);$r=Json ($folder+'/'+$case.request)
 foreach($key in $o.Keys){
  if($maps.ContainsKey($key)){if($o[$key] -ceq $r[$key]){throw 'FRESH_ID_REQUIRED'};if($maps[$key].ContainsKey($o[$key]) -and $maps[$key][$o[$key]] -cne $r[$key]){throw 'RESUME_ALIAS_CHANGED'};$maps[$key][$o[$key]]=$r[$key]}
  elseif($key -cnotin @('qualityVersion','qualityFingerprint') -and $o[$key] -cne $r[$key]){throw ('CONTRACT_CHANGED_'+$key)}
 }
 $oc=@($old|Where-Object id -CEQ $case.id)[0];foreach($key in $oc.Keys){if($key -notin @('invocationId','executionId','cycleId','idempotencyKey','qualityVersion','qualityFingerprint') -and ($oc[$key]|ConvertTo-Json -Compress -Depth 10) -cne ($case[$key]|ConvertTo-Json -Compress -Depth 10)){throw ('CASE_ORACLE_CHANGED_'+$key)}}
}
[void][Reflection.Assembly]::LoadFrom('C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll')
function Expand([string]$path){$body=[IO.File]::ReadAllText($path);$body=[regex]::Replace($body,'(?m)^:r "([^"]+)"\s*$',{param($m) Expand (Join-Path $folder $m.Groups[1].Value)});[regex]::Replace($body,'(?m)^:On Error exit\s*$','')}
foreach($name in @('activate.sql','compensate.sql','collision-preflight.sql')){
 $reader=[IO.StringReader]::new((Expand (Join-Path $folder $name)));$errors=$null
 try{$tree=[Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser]::new($true).Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
 if($errors.Count){throw ('FINAL_SQL_PARSE_'+$name)}
 foreach($batch in $tree.Batches){$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase);foreach($s in $batch.Statements){$vars=switch($s.GetType().Name){'DeclareVariableStatement'{@($s.Declarations|ForEach-Object {$_.VariableName.Value})};'DeclareTableVariableStatement'{@($s.Body.VariableName.Value)};default{@()}};foreach($v in $vars){if(-not $names.Add($v)){throw 'FINAL_SQL_DUPLICATE'}}}}
}
Import-Module ./scripts/validation/Bloco60SealFinalBudget.psm1 -Force
$events=@();foreach($p in @('target/bloco60-local/physical-corrective/cc4f84cb37f69724/ledger.jsonl','target/bloco60-local/physical-continuation/0560a96925160b1e/ledger.jsonl','target/bloco60-local/physical-remaining/60e96d5d9b27ecf6/ledger.jsonl','target/bloco60-local/physical-resumed/8117708c8e211c6a/ledger.jsonl','target/bloco60-local/physical-resumed-correction/a89c4dfa88c04833/ledger.jsonl','target/execucao-b60-retomada-20260910-0910/partition-diagnostic/ledger.jsonl','target/bloco60-local/physical-final/5683e0d9bc7c66dc/ledger.jsonl','target/bloco60-local/physical-audit-final/dbaaefbb0ddbafaa/ledger.jsonl')){$events+=@(Get-Content $p|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$at=[DateTimeOffset]'2026-09-10T12:45:00Z';$deadline=[DateTimeOffset]'2026-09-10T13:15:21.8816492Z';$checks=0
function Check($e,$current=@(),$now=$at){Assert-Bloco60SealFinalBudget -Event $e -Prior $events -Current $current -Now $now;$script:checks++}
function Entries([string]$kind,[int]$n,[bool]$emergency=$false){@(1..$n|ForEach-Object {@{type='RESERVE';kind=$kind;utc=$at.ToString('o');emergency=$emergency}})}
function Refuse([scriptblock]$body,[string]$expected){$r='ACCEPTED';try{& $body}catch{$r=$_.Exception.Message};if($r -cne $expected){throw ('FINAL_GUARD_'+$expected+'_GOT_'+$r)};$script:checks++}
Check @{type='OPEN'}
Check @{type='RESERVE';kind='JVM'} (Entries JVM 19)
Refuse {Check @{type='RESERVE';kind='JVM'} (Entries JVM 20)} 'B60U_CUMULATIVE_JVM'
Check @{type='RESERVE';kind='SQL'} (Entries READBACK 110)
Refuse {Check @{type='RESERVE';kind='SQL'} (Entries READBACK 111)} 'B60U_CUMULATIVE_SQL'
Refuse {Check @{type='OPEN'} @() $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Refuse {Check @{type='RESERVE';kind='JVM'} @() $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Check @{type='RESERVE';kind='COMPENSATE'} (Entries READBACK 7 $true) $deadline
Refuse {Check @{type='RESERVE';kind='READBACK'} (Entries READBACK 8 $true) $deadline} 'B60U_CUMULATIVE_ESCROW'
foreach($file in @('scripts/validation/Invoke-Bloco60SealFinalPhysical.ps1','scripts/validation/Bloco60SealFinalBudget.psm1')){$tokens=$null;$errors=$null;$null=[Management.Automation.Language.Parser]::ParseFile((Join-Path $root $file),[ref]$tokens,[ref]$errors);if($errors.Count){throw 'FINAL_POWERSHELL_PARSE'}}
Add-Type -Path ./scripts/validation/Bloco60SealFinalProcess.cs
@{passed=$true;freshRequests=17;oraclesAndAliasesPreserved=$true;canonicalFingerprints=2;sqlScriptsParsed=3;budgetChecks=$checks;priorSqlcmd=121;priorJvms=60;priorHttp=55;deadline=$deadline.ToString('o');sqlExecuted=$false}|ConvertTo-Json


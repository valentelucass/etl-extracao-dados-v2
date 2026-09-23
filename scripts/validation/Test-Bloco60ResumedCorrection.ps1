#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Set-Location $root
function Json([string]$p){Get-Content $p -Raw|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String}
$folder='database/proposals/bloco60-retomada-r2';$q=Json ($folder+'/quality-references.json')
$old=Json 'database/proposals/bloco60-restante/quality-references.json';$prior=Json 'database/proposals/bloco60-correcao/quality-references.json'
$oldCollisions=0
foreach($row in $q){
 $material=$row.material;$sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($material))).ToLowerInvariant()
 if($sha -cne $row.fingerprint -or -not $material.Contains('|2026-09-10T00:00:00.125|')){throw 'CORRECTED_POLICY_CANONICAL_HASH'}
 $o=@($old|Where-Object mode -CEQ $row.mode)[0];$p=@($prior|Where-Object mode -CEQ $row.mode)[0]
 if($o.scopeFingerprint -ceq $p.scopeFingerprint -and $o.material.Contains('|2026-09-10T00:00:00.124|') -and $p.material.Contains('|2026-09-10T00:00:00.124|')){$oldCollisions++}
 if($p.material.Contains('|2026-09-10T00:00:00.125|')){throw 'CORRECTED_POLICY_STILL_COLLIDES'}
 if(-not (Get-Content ($folder+'/seed-quality.sql') -Raw).Contains($sha)){throw 'SEED_HASH_MISMATCH'}
}
if($oldCollisions -ne 2){throw 'ORIGINAL_TWO_POLICY_COLLISIONS_NOT_REPRODUCED'}
$matrix=Json ($folder+'/matrix.json')
foreach($case in $matrix){
 $r=Json ($folder+'/'+$case.request);$o=Json ('database/proposals/bloco60-restante/'+$case.request)
 foreach($key in $o.Keys){if($key -cne 'qualityFingerprint' -and $r[$key] -cne $o[$key]){throw ('REQUEST_IDENTITY_OR_CONTRACT_CHANGED_'+$case.id)}}
 if($r.qualityVersion -like 'bloco60-usuarios-*-v3' -and @($q|Where-Object {$_.version -ceq $r.qualityVersion -and $_.fingerprint -ceq $r.qualityFingerprint}).Count -ne 1){throw 'REQUEST_DQ_REFERENCE'}
}
$dom='C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
[void][Reflection.Assembly]::LoadFrom($dom)
function Expand([string]$path){$body=[IO.File]::ReadAllText($path);$body=[regex]::Replace($body,'(?m)^:r "([^"]+)"\s*$',{param($m) Expand (Join-Path $folder $m.Groups[1].Value)});[regex]::Replace($body,'(?m)^:On Error exit\s*$','')}
foreach($name in @('activate.sql','compensate.sql','collision-preflight.sql')){
 $reader=[IO.StringReader]::new((Expand (Join-Path $folder $name)));$errors=$null
 try{$tree=[Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser]::new($true).Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
 if($errors.Count){throw ('CORRECTION_SQL_PARSE_'+$name)}
 foreach($batch in $tree.Batches){$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase);foreach($s in $batch.Statements){$vars=switch($s.GetType().Name){'DeclareVariableStatement'{@($s.Declarations|ForEach-Object {$_.VariableName.Value})};'DeclareTableVariableStatement'{@($s.Body.VariableName.Value)};default{@()}};foreach($v in $vars){if(-not $names.Add($v)){throw 'CORRECTION_SQL_DUPLICATE'}}}}
}
Import-Module ./scripts/validation/Bloco60ResumedCorrectionBudget.psm1 -Force
Import-Module ./scripts/validation/Bloco60ResumedCorrectionLedger.psm1 -Force
$events=@();foreach($dir in @('physical-corrective/cc4f84cb37f69724','physical-continuation/0560a96925160b1e','physical-remaining/60e96d5d9b27ecf6','physical-resumed/8117708c8e211c6a')){$events+=@(Get-Content ('target/bloco60-local/'+$dir+'/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$at=[DateTimeOffset]'2026-09-10T12:25:00Z';$deadline=[DateTimeOffset]'2026-09-10T13:15:21.8816492+00:00';$checks=0
function Check($e,$current=@(),$now=$at){Assert-Bloco60ResumedCorrectionBudget -Event $e -Prior $events -Current $current -Now $now;$script:checks++}
function Entries([string]$kind,[int]$n,[bool]$emergency=$false){@(1..$n|ForEach-Object {@{type='RESERVE';kind=$kind;utc=$at.ToString('o');emergency=$emergency}})}
function Refuse([scriptblock]$body,[string]$expected){$r='ACCEPTED';try{& $body}catch{$r=$_.Exception.Message};if($r -cne $expected){throw ('CORRECTION_GUARD_'+$expected+'_GOT_'+$r)};$script:checks++}
Check @{type='OPEN'}
Check @{type='RESERVE';kind='JVM'} (Entries JVM 46)
Refuse {Check @{type='RESERVE';kind='JVM'} (Entries JVM 47)} 'B60U_CUMULATIVE_JVM'
Check @{type='RESERVE';kind='SQL'} (Entries READBACK 169)
Refuse {Check @{type='RESERVE';kind='SQL'} (Entries READBACK 170)} 'B60U_CUMULATIVE_SQL'
Refuse {Check @{type='OPEN'} @() $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Refuse {Check @{type='RESERVE';kind='JVM'} @() $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Check @{type='RESERVE';kind='COMPENSATE'} (Entries READBACK 7 $true) $deadline
Refuse {Check @{type='RESERVE';kind='READBACK'} (Entries READBACK 8 $true) $deadline} 'B60U_CUMULATIVE_ESCROW'
$dir=Join-Path $root ('target/execucao-b60-retomada-20260910-0910/budget-test-'+[guid]::NewGuid().ToString('N'));$null=New-Item -ItemType Directory $dir
$line=Write-Bloco60ResumedCorrectionLedger -Path (Join-Path $dir 'ledger.jsonl') -Event @{type='OPEN';package='a'*64} -Now $at
if([DateTimeOffset]$line.deadline -ne $deadline){throw 'LEAF_DEADLINE_RENEWED'}
foreach($file in @('scripts/validation/Invoke-Bloco60ResumedCorrection.ps1','scripts/validation/Bloco60ResumedCorrectionBudget.psm1','scripts/validation/Bloco60ResumedCorrectionLedger.psm1')){$tokens=$null;$errors=$null;$null=[Management.Automation.Language.Parser]::ParseFile((Join-Path $root $file),[ref]$tokens,[ref]$errors);if($errors.Count){throw 'CORRECTION_POWERSHELL_PARSE'}}
@{passed=$true;duplicateEffectiveDatesReproduced=2;canonicalFingerprintsVerified=2;requestsPreserved=$matrix.Count;sqlBatchesParsed=3;budgetChecks=$checks;fixedLeafDeadline=$line.deadline;priorSqlcmd=62;priorJvms=33;sqlExecuted=$false}|ConvertTo-Json

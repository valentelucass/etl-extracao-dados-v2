#Requires -Version 7.0
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture=Join-Path $root ('target/bloco54/completion-guard-selftest-'+[guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$files=@(& rg --files --hidden -g '!.git/**' -g '!target/**' -g '!.codex-local/**' $root)
if($LASTEXITCODE -ne 0 -or $files.Count -gt 2000){throw 'BOUNDED_GUARD_FIXTURE_REQUIRED'}
foreach($file in $files){
 $relative=[IO.Path]::GetRelativePath($root,$file)
 if($relative.StartsWith('..') -or (Get-Item -LiteralPath $file).Length -gt 8MB){throw 'GUARD_FIXTURE_SCOPE_REQUIRED'}
 $destination=Join-Path $fixture $relative
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))|Out-Null
 [IO.File]::Copy($file,$destination)
}
$integrated=Join-Path $fixture 'scripts/validation/Test-Bloco54Integrated.ps1'
$trail=Join-Path $fixture 'scripts/validation/Test-Gpt56ChatTrail.ps1'
& $integrated|Out-Null
& $trail|Out-Null
$count=0
function Reject([string]$Relative,[scriptblock]$Change,[string]$Validator,[string]$Expected){
 $path=Join-Path $fixture $Relative
 $bytes=[IO.File]::ReadAllBytes($path)
 try{
  $changed=& $Change ($utf8.GetString($bytes))
  [IO.File]::WriteAllText($path,$changed,$utf8)
  $failure=$null
  try{& $Validator|Out-Null}catch{$failure=$_.Exception.Message}
  if($null -eq $failure -or $failure -notmatch $Expected){throw 'EXPECTED_GUARD_REFUSAL_NOT_OBSERVED'}
  $script:count++
 }finally{[IO.File]::WriteAllBytes($path,$bytes)}
}
Reject 'database/manifest/runtime-bloco54.json' {param($s) $s.Replace('"tests": 1098','"tests": 1097')} $integrated 'B54_FINAL_COUNT_DRIFT'
Reject 'database/manifest/runtime-bloco54.json' {param($s) $s.Replace('"newCanonicalAcceptances": 0','"newCanonicalAcceptances": 1')} $integrated 'B54_FINAL_COUNT_DRIFT'
Reject 'database/migrations/V021__bind_durable_temporal_intent.sql' {param($s) $s+"`n-- mutated isolated fixture"} $integrated 'B54_EVIDENCE_HASH_CHANGED'
Reject 'STATES.md' {param($s) $s.Replace('- [ ] **V2-022 —','- [x] **V2-022 —')} $integrated 'UNPROVEN_NOMINAL_GATE_CLOSED'
Reject 'docs/runbooks/trilha-de-chats-gpt-5-6.md' {param($s) $s.Replace('Bloco 55 — laboratório A–J; fonte real e produção com gates próprios','Bloco 55 — conclusão produtiva')} $trail 'B54_LOCAL_COMPLETION_PANEL_DRIFT'
Reject 'docs/runbooks/trilha-de-chats-gpt-5-6.md' {param($s) $s+"`n"+[regex]::Match($s,'(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=P02Q[^\r\n]+').Value+"`n"} $trail 'duplic|mesma|marcos|numera|hist.r|Bloco|rota'
& $integrated|Out-Null
& $trail|Out-Null
if($count -ne 6){throw 'SIX_GUARD_CONTRAPROOFS_REQUIRED'}
'B54_COMPLETION_GUARDS_6_CONTRAPROOFS_PASS_REAL_LEDGER_UNCHANGED'

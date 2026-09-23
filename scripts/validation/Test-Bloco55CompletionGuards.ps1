#Requires -Version 7.0
param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture=Join-Path $root ('target/bloco55/completion-guards/'+[guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$files=@(& rg --files --hidden -g '!.git/**' -g '!target/**' -g '!.codex-local/**' -g '!.env' $root)
if($LASTEXITCODE -ne 0 -or $files.Count -gt 2000){throw 'BOUNDED_B55_GUARD_FIXTURE_REQUIRED'}
foreach($file in $files){
 $relative=[IO.Path]::GetRelativePath($root,$file)
 if($relative.StartsWith('..') -or (Get-Item $file).Length -gt 8MB){throw 'GUARD_FIXTURE_SCOPE'}
 $destination=Join-Path $fixture $relative
 [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination));[IO.File]::Copy($file,$destination,$false)
}
$integrated=Join-Path $fixture 'scripts/validation/Test-Bloco55Integrated.ps1'
$acceptances=Join-Path $fixture 'scripts/validation/Test-Bloco55Acceptances.ps1'
$trail=Join-Path $fixture 'scripts/validation/Test-Gpt56ChatTrail.ps1'
& $integrated|Out-Null;& $trail|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$Id,[string]$Relative,[scriptblock]$Change,[string]$Validator,[string]$Expected){
 $path=Join-Path $fixture $Relative;$bytes=[IO.File]::ReadAllBytes($path)
 try{
  $original=$utf8.GetString($bytes);$changed=& $Change $original
  if($changed -ceq $original){throw ('MUTATION_MUST_CHANGE_FIXTURE_'+$Id)}
  [IO.File]::WriteAllText($path,$changed,$utf8)
  $reason='NOT_REJECTED';try{& $Validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $Expected){throw ('EXPECTED_B55_GUARD_REFUSAL_'+$Id)}
  $results.Add([ordered]@{id=$Id;layer='OFFLINE_ISOLATED_DOCUMENT_FIXTURE';reason=$reason;passed=$true})
 }finally{[IO.File]::WriteAllBytes($path,$bytes)}
}
Reject 'FALSE_A_J_COMPLETE' 'database/manifest/runtime-bloco55.json' {param($s)$s.Replace('"status": "LOCAL_A_J_COMPLETE"','"status": "PRODUCTION_COMPLETE"')} $integrated 'FULL_ACCEPTANCE_NOT_PROVEN'
Reject 'FALSE_LOCAL_ACCEPTANCE' 'STATES.md' {param($s)$s.Replace('- [x] **V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS —','- [ ] **V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS —')} $integrated 'LOCAL_SLICE_NOT_SYNCHRONIZED'
Reject 'FALSE_PARENT_ACCEPTANCE' 'STATES.md' {param($s)$s.Replace('- [ ] **V2-022 —','- [x] **V2-022 —')} $integrated 'UNPROVEN_NOMINAL_GATE_CLOSED'
Reject 'FALSE_FAILED_PROOF_ACCEPTANCE' 'database/manifest/runtime-bloco55.json' {param($s)$m=$s|ConvertFrom-Json -AsHashtable;$m.validRuntimeGroups[0]='B55_SMOKE_01';$m|ConvertTo-Json -Depth 8} $integrated 'FALSE_PASS_EXCLUSION'
Reject 'GRANT_DELTA_EXCESS' 'database/manifest/runtime-bloco55.json' {param($s)$s.Replace('"grantDelta": 7','"grantDelta": 8')} $integrated 'EXACT_CHECKPOINT_DRIFT_grantDelta'
Reject 'IDENTITY_INPUT_ABSENT' 'database/manifest/runtime-bloco55-acceptances.json' {param($s)$s.Replace('EXISTING_RANDOM_AUDIT_UUID_SID_ONLY_PROTECTED_SQL','UNKNOWN')} $acceptances 'IDENTITY_INPUTS_INCOMPLETE'
Reject 'DUPLICATE_B55' 'docs/runbooks/trilha-de-chats-gpt-5-6.md' {param($s)$s+"`n"+[regex]::Match($s,'(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=P02V[^\r\n]+').Value+"`n"} $trail 'SINGLE_COMPLETE_LOCAL_SLICE_REQUIRED'
Reject 'FALSE_PROGRESS_PANEL' 'docs/runbooks/trilha-de-chats-gpt-5-6.md' {param($s)$s.Replace('**115: 65 concluídos e 50 pendentes**','**115: 66 concluídos e 49 pendentes**')} $trail 'contagens do painel'
Reject 'REQUIRED_SQL_RECONCILIATION_ABSENT' 'database/manifest/runtime-bloco55.json' {param($s)$m=$s|ConvertFrom-Json -AsHashtable;$m.proofFiles=@($m.proofFiles|Where-Object path -CNE 'target/bloco55/sql-fence-reconciliation.json');$m|ConvertTo-Json -Depth 8} $integrated 'REQUIRED_COMPLETION_PROOF_ABSENT'
& $integrated -RequireComplete|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5),$utf8)
'B55_COMPLETION_NINE_CONTRAPROOFS_PASS_ORIGINAL_STATE_AND_LEDGERS_UNCHANGED'

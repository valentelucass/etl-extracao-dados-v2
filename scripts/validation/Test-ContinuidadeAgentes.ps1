#Requires -Version 7.0
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco56/manifesto.json')){
 # B56 verifies the exact current revisions and the preserved predecessor bytes.
 # Historical checks below continue to inspect the original documentary snapshot.
 $successor=& (Join-Path $PSScriptRoot 'Test-Bloco56Preparacao.ps1') -AsMap
}
function Historical-Path([string]$Path){
 if($null -ne $successor -and $successor.ContainsKey($Path)){return $successor[$Path].snapshot}
 return $Path
}
function Read-Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'CONTINUITY_PATH'}
 $file=Join-Path $root (Historical-Path $Path)
 if((Get-Item -LiteralPath $file).Length -gt 2MB){throw 'CONTINUITY_FILE_BOUND'}
 $utf8.GetString([IO.File]::ReadAllBytes($file))
}
function Assert-Hash([string]$Path,[string]$Hash){
 [void](Read-Local $Path)
 if($Hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Join-Path $root (Historical-Path $Path))).Hash.ToLowerInvariant() -cne $Hash){throw ('CONTINUITY_HASH_'+$Path)}
}
$m=Read-Local 'docs/continuidade/manifesto-pos-bloco55.json'|ConvertFrom-Json -DateKind String
if($m.version -ne 1 -or $m.status -cne 'DOCUMENTATION_ONLY_B56_PROPOSED' -or $m.authorizesPhysicalExecution -or $m.startsBloco56 -or $m.newReservations -ne 0 -or $m.newCanonicalAcceptances -ne 0){throw 'CONTINUITY_DOCUMENTATION_ONLY_REQUIRED'}
foreach($pair in @(@('total',115),@('done',65),@('pending',50),@('openRoutes',193),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'CONTINUITY_PROGRESS_DRIFT'}}
if($m.baseline.path -cne 'database/manifest/runtime-bloco55.json' -or $m.baseline.sha256 -cne 'bfccb40d72839ffe9115ab6e4d0760a4f0cecfee836efd14d6e216a3656479b7'){throw 'CONTINUITY_B55_BASELINE_CHANGED'}
Assert-Hash $m.baseline.path $m.baseline.sha256
$expected=[ordered]@{
 'AGENTS.md'='beee555f7c75cefe2dab591e8b5f0560ab9bf4739cfffd0687586b0e80c5dff9'
 'STATES.md'='2a47c85e26e94b742619ed1af94a9389971e0493c41648458abe4e8893a8d117'
 'docs/runbooks/trilha-de-chats-gpt-5-6.md'='7f051cc75e01eeb5c28572108a2dda3c555c22a21fea345ce06546f076ce2a82'
 'scripts/validation/Test-Bloco55Integrated.ps1'='4a8646053ab74fd99092acf40e49bff658eee4751320ed4c348ce17edf44f5ae'
}
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
if($m.changedExistingFiles.Count -ne $expected.Count){throw 'CONTINUITY_EXACT_DOCUMENT_DELTA_REQUIRED'}
foreach($entry in $m.changedExistingFiles){
 if(-not $expected.Contains($entry.path) -or $entry.before -cne $expected[$entry.path] -or $map.ContainsKey($entry.path)){throw 'CONTINUITY_EXACT_DOCUMENT_DELTA_REQUIRED'}
 Assert-Hash $entry.path $entry.after;$map.Add($entry.path,$entry)
}
$required=@('docs/runbooks/continuidade-agentes.md','docs/continuidade/RETOMADA.md','docs/continuidade/checkpoint-modelo.md','docs/continuidade/checkpoints/0001-pos-bloco55.md','docs/runbooks/prompt-bloco-56-identidades-e-verticais-condicionais.md','scripts/validation/Test-ContinuidadeAgentes.ps1','scripts/validation/Test-ContinuidadeAgentesGuards.ps1')
if($m.newFiles.Count -ne $required.Count -or @($m.newFiles.path|Sort-Object -Unique).Count -ne $required.Count){throw 'CONTINUITY_ARTIFACT_SET_REQUIRED'}
foreach($entry in $m.newFiles){if($entry.path -cnotin $required){throw 'CONTINUITY_ARTIFACT_SET_REQUIRED'};Assert-Hash $entry.path $entry.sha256}
$state=Read-Local 'STATES.md';$trail=Read-Local 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
$boxes=[regex]::Matches($state,'(?m)^\s*- \[(?<mark>[ xX])\]')
if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups['mark'].Value -match '[xX]'}).Count -ne 65 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 193 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'CONTINUITY_PROGRESS_DRIFT'}
foreach($text in @($state,$trail,(Read-Local 'docs/continuidade/RETOMADA.md'))){if(-not $text.Contains('CONTINUIDADE_DOCUMENTADA_B56_NAO_INICIADO')){throw 'CONTINUITY_CURRENT_POINTER_REQUIRED'}}
if(@((Read-Local 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'CONTINUITY_RESTART_INDEX_TOO_LONG'}
if(-not (Read-Local 'docs/runbooks/prompt-bloco-56-identidades-e-verticais-condicionais.md').Contains('PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO')){throw 'CONTINUITY_PROPOSAL_NOT_EXECUTION'}
foreach($task in @('V2-009b/8636','V2-009b/4924','V2-009b/10633','V2-009b/6392','V2-009b','V2-029','V2-030','V2-031','V2-032')){
 if([regex]::Matches($state,('(?m)^\s*- \[ \] \*\*'+[regex]::Escape($task)+' —')).Count -ne 1){throw 'CONTINUITY_IDENTITY_OR_VERTICAL_FALSE_ACCEPTANCE'}
}
if($IncludePrivateEvidence){
 foreach($pair in @(@('target/bloco55/completion-receipt.json','6732135353474346ba58c03855fd39746ecfb375a023422372574747512bfa82'),@('target/bloco55/ledger.jsonl','04d67fd1ba1138b94d0a034891e9d83003895f5379739062288364afceafb3b5'),@('target/bloco54/ledger.jsonl','64ad2f64d8dd899236926c5e9f9a50bd023b765ee232cb473b20934edcb75356'),@('target/bloco53/cumulative-reservations.txt','dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1'))){Assert-Hash $pair[0] $pair[1]}
}
if($null -ne $successor){
 foreach($path in @($map.Keys)){
  if($successor.ContainsKey($path)){
   if($map[$path].after -cne $successor[$path].before){throw 'CONTINUITY_B56_CHAIN_MISMATCH'}
   $map[$path].after=$successor[$path].after
  }
 }
 # Propagate only individually verified successor deltas, including the trail
 # validator. The successor enforces an exact path set and original bytes.
 foreach($path in $successor.Keys){if(-not $map.ContainsKey($path)){$map.Add($path,$successor[$path])}}
}
if($AsMap){return ,$map}
if($null -ne $successor){'CONTINUITY_HISTORICAL_DOCUMENTS_AND_B56_LOCAL_SUCCESSION_VALID_NO_NEW_ACCEPTANCE_OR_BUDGET'}
else{'CONTINUITY_DOCUMENTS_VALID_B56_NOT_STARTED_NO_NEW_ACCEPTANCE_OR_BUDGET'}

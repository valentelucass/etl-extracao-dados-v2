#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'RASC_PATH'}
 $file=Join-Path $root $Path;$node=Get-Item -LiteralPath $file
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'RASC_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$Path){$file=Local $Path;if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'RASC_FILE_BOUND'};return $utf8.GetString([IO.File]::ReadAllBytes($file))}
function Json([string]$Path){return (Read $Path|ConvertFrom-Json -Depth 50 -DateKind String)}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('RASC_HASH_'+$Path)}}
function Exact($Actual,$Expected,[string]$Reason){if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}}
function False($Value){if($Value -isnot [bool] -or $Value){throw 'RASC_FALSE_EFFECT_OR_ACCEPTANCE'}}
$prefix='docs/catalogos/raster-contrato-local/'
$m=Json ($prefix+'manifesto.json')
if($m.version -ne 1 -or $m.status -cne 'LOCAL_RASTER_DECISION_CONTRACT_COMPLETE_IDENTITY_BLOCKED'){throw 'RASC_STATE'}
if($m.predecessor.path -cne 'docs/catalogos/bloco56-continuacao/manifesto.json' -or $m.predecessor.sha256 -cne 'bd7f39d3aba7ac14e9e371886d6a35b51fa78a9372bb59aa76fcb14a0c3c4621'){throw 'RASC_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
Exact $m.acceptedTasks @('V2-034a','V2-025c') 'RASC_ONLY_ORIGINAL_TWO_ACCEPTANCES'
if($m.newCanonicalAcceptances -ne 2 -or $m.newIdentityAcceptances -ne 0 -or $m.newVerticalImplementations -ne 0){throw 'RASC_NO_VERTICAL_OR_IDENTITY_ACCEPTANCE'}
foreach($name in @('remoteCalls','sqlOperations','runtimeCampaigns','installations','unknownResults','budgetConsumed')){if($m.$name -ne 0){throw 'RASC_ZERO_EFFECTS'}}
foreach($name in @('runtimeEnabled','rotationProven','budgetTransferred','validityRenewed')){False $m.$name}
foreach($pair in @(@('done',67),@('total',115),@('pending',48),@('openRoutes',191),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'RASC_PROGRESS'}}
$previous=Json $m.predecessor.path
$prep=Json $previous.predecessor.path
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $prep.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $prep.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $prep.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($previous.predecessor.path,$previous.predecessor.sha256)
foreach($e in $previous.changedExistingFiles){if($baseline[$e.path] -cne $e.before){throw 'RASC_BASELINE_CHAIN'};$baseline[$e.path]=$e.after}
foreach($e in $previous.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1396){throw 'RASC_BASELINE_COUNT'}
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco56Continuacao.ps1','scripts/validation/Test-Bloco56ContinuacaoGuards.ps1','scripts/validation/Test-Bloco56Preparacao.ps1','scripts/validation/Test-ContinuidadeAgentes.ps1','scripts/validation/Test-Gpt56ChatTrail.ps1')
Exact $m.changedExistingFiles.path $allowed 'RASC_EXACT_EIGHT_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.before -cne $baseline[$e.path] -or $e.snapshot -cne ('docs/continuidade/historico/bloco56-continuacao/'+$e.path)){throw 'RASC_SNAPSHOT_CHAIN'}
 Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
}
foreach($path in $baseline.Keys){if(-not $map.ContainsKey($path)){Hash $path $baseline[$path]}}
$required=@($m.changedExistingFiles.snapshot)+@(
 ($prefix+'decisao.json'),($prefix+'contrato.json'),($prefix+'campos.json'),($prefix+'identidade-pendente.json'),($prefix+'fingerprints.json'),($prefix+'README.md'),
 ($prefix+'evidencias/legado.json'),($prefix+'fixtures/respostas.synthetic.json'),($prefix+'fixtures/limites.synthetic.json'),($prefix+'fixtures/reordenacao.synthetic.json'),
 'docs/adr/0039-raster-mantido-com-contrato-local-e-identidade-pendente.md',
 'docs/continuidade/checkpoints/0009-bloco56-raster-decisao-e-contrato-local.md','docs/continuidade/checkpoints/0010-bloco56-raster-validacao-e-retomada.md',
 'scripts/validation/Test-RasterContractCatalog.ps1','scripts/validation/Test-RasterContractGuards.ps1',
 'scripts/validation/Test-RasterLocalContinuation.ps1','scripts/validation/Test-RasterLocalContinuationGuards.ps1')
Exact $m.newFiles.path $required 'RASC_EXACT_NEW_FILES'
foreach($e in $m.newFiles){Hash $e.path $e.sha256}
$catalogResult=& (Join-Path $PSScriptRoot 'Test-RasterContractCatalog.ps1') -AsResult
if(-not $catalogResult.passed -or $catalogResult.identityAccepted -or $catalogResult.runtimeImplemented){throw 'RASC_CONTRACT_NOT_PROVEN'}
$state=Read 'STATES.md';$oldState=Read $map['STATES.md'].snapshot
$expectedState=$oldState.Replace('- [ ] **V2-034a —','- [x] **V2-034a —').Replace('- [ ] **V2-025c —','- [x] **V2-025c —')
Exact @([regex]::Matches($state,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) @([regex]::Matches($expectedState,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) 'RASC_ORIGINAL_CRITERIA_OR_OTHER_ACCEPTANCE_CHANGED'
foreach($id in @('V2-009c','V2-034b','V2-034','V2-025','V2-009b/10633','V2-009b/8636','V2-009b/4924','V2-009b/6392','V2-029','V2-030','V2-031','V2-032')){
 if([regex]::Matches($state,('(?m)^\s*- \[ \] \*\*'+[regex]::Escape($id)+' —')).Count -ne 1){throw 'RASC_BLOCKED_TASK_FALSE_ACCEPTANCE'}
}
$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$oldTrail=Read $map['docs/runbooks/trilha-de-chats-gpt-5-6.md'].snapshot
$oldChecked=@([regex]::Matches($oldTrail,'(?m)^- \[x\].+$')|ForEach-Object Value)
$newChecked=@([regex]::Matches($trail,'(?m)^- \[x\].+$')|ForEach-Object Value)
foreach($line in $oldChecked){if($line -cnotin $newChecked){throw 'RASC_HISTORICAL_TRAIL_CHANGED'}}
if($newChecked.Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'RASC_TRAIL_COUNT'}
foreach($pair in @(@('RAS-01','V2-034a'),@('RAS-02','V2-025c'))){
 if([regex]::Matches($trail,('(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA='+$pair[0]+' \| TAREFA='+$pair[1]+' \|.*EVIDENCIA=STATES\.md$')).Count -ne 1){throw 'RASC_TRAIL_ACCEPTANCE'}
}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md')){if(-not (Read $path).Contains('B56_RASTER_MANTIDO_CONTRATO_LOCAL_IDENTIDADE_PENDENTE')){throw 'RASC_CURRENT_POINTER'}}
if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'RASC_POINTER_BOUND'}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Json $m.initialInventory.path
 if($initial.Count -ne $baseline.Count){throw 'RASC_PRIVATE_INVENTORY'}
 foreach($e in $initial){if($baseline[$e.path] -cne $e.sha256){throw 'RASC_PRIVATE_REVISION'};Hash ('target/bloco56-raster/initial/'+$e.path) $e.sha256}
 foreach($e in $m.privateEvidence){Hash $e.path $e.sha256}
}
if($AsMap){return ,$map}
'RASTER_SUCCESSION_PASS_TWO_ORIGINAL_ACCEPTANCES_ZERO_SOURCE_SQL_RUNTIME'

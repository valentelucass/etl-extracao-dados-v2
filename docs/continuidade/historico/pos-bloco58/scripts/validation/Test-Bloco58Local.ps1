#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B58_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $path)
 $file=$node.FullName
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B58_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$path){$file=Local $path;if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'B58_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $hash){throw ('B58_HASH_'+$path)}}
function Exact($actual,$expected,[string]$reason){if(@($actual).Count -ne @($expected).Count -or (@($actual|Sort-Object -Unique)-join '|') -cne (@($expected|Sort-Object -Unique)-join '|')){throw $reason}}
$m=Read 'docs/catalogos/bloco58-local/manifesto.json'|ConvertFrom-Json -Depth 60 -DateKind String
if($m.version -ne 1 -or $m.block -ne 58 -or $m.status -cnotin @('EM_EXECUCAO','TESTADO_LOCAL_A_D')){throw 'B58_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/bloco57-complemento/manifesto.json' -or $m.predecessor.sha256 -cne '4d82e41a21e50dac1f18ce03cebf411b6dc5af401944804fcb4a6f13075c4988'){throw 'B58_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
$old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1488 -or $m.initialFiles -ne 1488){throw 'B58_BASELINE_COUNT'}
foreach($key in @('newAcceptances','sourceCalls','sqlOperations','runtimeCampaigns','budgetConsumed','unknownExternalEffects')){
 if(($m.$key -isnot [int] -and $m.$key -isnot [long]) -or $m.$key -ne 0){throw 'B58_LIMIT_OR_ACCEPTANCE'}
}
foreach($pair in @(@('total',115),@('done',67),@('pending',48),@('openRoutes',191),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'B58_PROGRESS'}}
foreach($pair in @(@('maximumBytes',65536),@('maximumRows',1000),@('maximumPages',100),@('maximumDepth',16),@('maximumPaths',256),@('maximumNodes',4096),@('maximumGraphQlNodes',20),@('maximumInFlightPages',1))){if($m.limits.($pair[0]) -ne $pair[1]){throw 'B58_CONSUMER_LIMIT'}}
$allowed=@('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'scripts/validation/Test-Bloco57Complemento.ps1',
 'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/MapperCharacterizationTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/LocalizacaoCharacterizationPathTest.java')
Exact $m.changedExistingFiles.path $allowed 'B58_EXACT_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.path -cnotin $allowed -or $e.snapshot -cne ('docs/continuidade/historico/bloco58/'+$e.path)){throw 'B58_UNAPPROVED_DELTA'}
 if($e.before -cne $baseline[$e.path]){throw 'B58_BASELINE_BEFORE'}
 Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
}
if(@($m.preservedFiles).Count+$map.Count -ne 1488){throw 'B58_PRESERVED_COUNT'}
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($e in $m.preservedFiles){
 if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B58_PRESERVED_BASELINE'}
 Hash $e.path $e.sha256
}
$new=@($m.changedExistingFiles.snapshot)+@(
 'docs/catalogos/bloco58-local/README.md',
 'scripts/validation/Test-Bloco58Local.ps1','scripts/validation/Test-Bloco58LocalGuards.ps1',
 'src/test/resources/contracts/bloco58/coletas.current-v1.synthetic.json',
 'src/test/resources/contracts/bloco58/usuarios.current-v1.synthetic.json',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/Bloco58DataExportAccess.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/Bloco58GraphQlAccess.java')
foreach($name in @('CharacterizationFixtures','LocalCharacterization','LocalCharacterizationBoundsTest',
 'ColetasCharacterization','ColetasCharacterizationTest','ColetasProjection','ColetasTemporalCharacterizationTest',
 'ColetasTraversalCharacterizationTest','UsuariosCharacterization','UsuariosCharacterizationTest',
 'UsuariosProjection','UsuariosTraversalCharacterizationTest')){
 $new+=('src/test/java/br/com/esl/etl/v2/contratos/bloco58/'+$name+'.java')
}
foreach($name in @('0021-bloco58-inicio-e-reproducao.md','0022-bloco58-coletas-local.md','0023-bloco58-usuarios-local.md',
 '0024-bloco58-regressao-local.md','0025-bloco58-sucessao-e-guards.md','0026-bloco58-fechamento-local.md')){
 $path='docs/continuidade/checkpoints/'+$name
 if($m.status -ceq 'TESTADO_LOCAL_A_D' -or (Test-Path -LiteralPath (Join-Path $root $path))){$new+=$path}
}
Exact $m.newFiles.path $new 'B58_EXACT_ADDITIONS'
foreach($e in $m.newFiles){
 if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B58_NEW_COLLISION'}
 Hash $e.path $e.sha256
}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$previous=Read $map[$path].snapshot
 $pattern='(?m)^\s*- \[[ xX]\].+$'
 if((@([regex]::Matches($current,$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous,$pattern)|ForEach-Object Value)-join [char]10)){throw 'B58_ORIGINAL_CHECKBOX_CHANGED'}
 if(-not $current.Contains('B58_') -or -not $current.Contains($previous)){throw 'B58_HISTORY_NOT_PRESERVED'}
}
$state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'B58_PROGRESS'}
if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'B58_RETOMADA_BOUND'}
foreach($entity in @('coletas','usuarios')){
 $fixture=Read ('src/test/resources/contracts/bloco58/'+$entity+'.current-v1.synthetic.json')|ConvertFrom-Json -Depth 40
 if($fixture.evidence -cne 'SYNTHETIC_LOCAL_ONLY' -or $fixture.cases.Count -ne $(if($entity -ceq 'coletas'){48}else{42})){throw 'B58_FIXTURE_SCOPE'}
 Hash $fixture.decision $fixture.decisionSha256
}
$plan=Read 'docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json'|ConvertFrom-Json -Depth 20
$pending=Read 'docs/catalogos/bloco57-complemento/cotacoes-input.pendente.json'|ConvertFrom-Json
if($plan.sourceExecutionEnabled -or $plan.sourceCalls -ne 0 -or $pending.sourceExecutionEnabled -or $pending.budgetAdopted){throw 'B58_SOURCE_ENABLED'}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
 if($initial.Count -ne 1488){throw 'B58_PRIVATE_COUNT'}
 $privateSeen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($e in $initial){
  if(-not $privateSeen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B58_PRIVATE_BASELINE'}
  Hash ('target/bloco58-local/initial/'+$e.path) $e.sha256
 }
 foreach($e in $m.evidence){Hash $e.path $e.sha256}
 if($m.status -ceq 'TESTADO_LOCAL_A_D'){
  $j=Read 'target/bloco58-local/java-result.json'|ConvertFrom-Json -Depth 15
  if(-not $j.passed -or $j.failures -ne 0 -or $j.errors -ne 0 -or $j.skipped -ne 5 -or $j.maximumHeapMiB -ne 512 -or $j.java -ne 17 -or -not $j.offline){throw 'B58_JAVA_PROOF'}
  foreach($e in $j.sourceBindings){Hash $e.path $e.sha256}
  foreach($e in $j.artifacts){Hash $e.path $e.sha256}
 }
}
if($AsMap){return ,$map}
'B58_LOCAL_SUCCESSION_PASS_NO_NEW_ACCEPTANCE_NO_SOURCE_OR_SQL'

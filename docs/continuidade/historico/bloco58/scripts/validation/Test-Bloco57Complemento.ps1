#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B57R_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $path)
 $file=$node.FullName
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B57R_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$path){$p=Local $path;if((Get-Item -LiteralPath $p).Length -gt 8MB){throw 'B57R_BOUND'};[Text.UTF8Encoding]::new($false,$true).GetString([IO.File]::ReadAllBytes($p))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $hash){throw ('B57R_HASH_'+$path)}}
function Exact($actual,$expected,[string]$reason){if(@($actual).Count -ne @($expected).Count -or (@($actual|Sort-Object -Unique)-join '|') -cne (@($expected|Sort-Object -Unique)-join '|')){throw $reason}}
$m=Read 'docs/catalogos/bloco57-complemento/manifesto.json'|ConvertFrom-Json -Depth 60 -DateKind String
if($m.version -ne 1 -or $m.block -ne 57 -or $m.status -cnotin @('EM_EXECUCAO','TESTADO_LOCAL_COMPLEMENTO_F1_F2_F3')){throw 'B57R_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/bloco57-local/manifesto.json' -or $m.predecessor.sha256 -cne '777b03f4fa0188b5162df85682c2896f43510c54d3c15f948bb6b10eba8d2c9e'){throw 'B57R_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
$old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1463 -or $m.initialFiles -ne 1463){throw 'B57R_BASELINE_COUNT'}
foreach($key in @('newAcceptances','sourceCalls','sqlOperations','runtimeCampaigns','budgetConsumed','unknownExternalEffects')){if($m.$key -ne 0){throw 'B57R_LIMIT_OR_ACCEPTANCE'}}
$allowed=@('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'scripts/validation/Test-Bloco57Local.ps1','scripts/validation/Test-Bloco57LocalGuards.ps1',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/MapperCharacterization.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/MapperProjection.java')
Exact $m.changedExistingFiles.path $allowed 'B57R_EXACT_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.path -cnotin $allowed -or $e.snapshot -cne ('docs/continuidade/historico/bloco57-complemento/'+$e.path)){throw 'B57R_UNAPPROVED_DELTA'}
 if($e.before -cne $baseline[$e.path]){throw 'B57R_BASELINE_BEFORE'}
 Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
}
if(@($m.preservedFiles).Count+$map.Count -ne 1463){throw 'B57R_PRESERVED_COUNT'}
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($e in $m.preservedFiles){
 if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B57R_PRESERVED_BASELINE'}
 Hash $e.path $e.sha256
}
$new=@($m.changedExistingFiles.snapshot)+@(
 'docs/continuidade/historico/bloco57-complemento/STATES-observado.md',
 'docs/continuidade/checkpoints/0018-bloco57-complemento-localizacao.md',
 'docs/catalogos/bloco57-complemento/README.md',
 'docs/catalogos/bloco57-complemento/cotacoes-input.pendente.json',
 'docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json',
 'scripts/probes/Invoke-CotacoesCharacterization.ps1',
 'scripts/validation/Test-Bloco57Complemento.ps1',
 'scripts/validation/Test-Bloco57ComplementoGuards.ps1',
 'src/test/java/br/com/esl/etl/v2/bootstrap/LocalizacaoCharacterizationPathTest.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesSourceInput.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesSourceRunner.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesCurlTransport.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesSourceCommandTest.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesSourceRunnerTest.java',
 'src/test/java/br/com/esl/etl/v2/contratos/mapping/CotacoesCurlTransportTest.java')
foreach($checkpoint in @('0019-bloco57-complemento-executor.md','0020-bloco57-complemento-fechamento.md')){
 if(Test-Path -LiteralPath (Join-Path $root ('docs/continuidade/checkpoints/'+$checkpoint))){$new+=('docs/continuidade/checkpoints/'+$checkpoint)}
}
Exact $m.newFiles.path $new 'B57R_EXACT_ADDITIONS'
foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B57R_NEW_COLLISION'};Hash $e.path $e.sha256}
if($m.observedDrift.path -cne 'STATES.md' -or $m.observedDrift.deliveredSha256 -cne '204800d11a72cb32965d78958235a4ae8ad9c00db95e4c2ca8291c072ea5203e' -or
 $m.observedDrift.observedSha256 -cne '3993be93d346c0ba1b845dd35660751e598eaf239f8b8f02b4d5cbbbdd3d94f6' -or
 $m.observedDrift.snapshot -cne 'docs/continuidade/historico/bloco57-complemento/STATES-observado.md'){throw 'B57R_OBSERVED_DRIFT_BINDING'}
Hash $m.observedDrift.snapshot $m.observedDrift.observedSha256
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$previous=Read $map[$path].snapshot
 $pattern='(?m)^\s*- \[[ xX]\].+$'
 if((@([regex]::Matches($current,$pattern)|ForEach-Object Value)-join "`n") -cne (@([regex]::Matches($previous,$pattern)|ForEach-Object Value)-join "`n")){throw 'B57R_ORIGINAL_CHECKBOX_CHANGED'}
 if(-not $current.Contains('B57_COMPLEMENTO_') -or -not $current.Contains($previous)){throw 'B57R_HISTORICAL_TEXT_NOT_PRESERVED'}
}
if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'B57R_RETOMADA_BOUND'}
$plan=Read 'docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json'|ConvertFrom-Json -Depth 20
if($plan.sourceExecutionEnabled -isnot [bool] -or $plan.sourceExecutionEnabled -or $plan.sourceCalls -ne 0 -or $plan.status -cne 'IMPLEMENTED_OFFLINE_SOURCE_BLOCKED_BY_INPUT'){throw 'B57R_SOURCE_PLAN_GATE'}
if($plan.sourceCommand -cne 'pwsh -NoProfile -File scripts/probes/Invoke-CotacoesCharacterization.ps1 -InputPath PRIVATE_INPUT_JSON -Execute'){throw 'B57R_SOURCE_COMMAND'}
foreach($pair in @(@('infoRequests',1),@('maximumDataPages',3),@('maximumRequests',4),@('pageStart',1),@('per',3),@('maximumPhysicalRowsPerPage',3),@('maximumRows',9),@('maximumBytesPerResponse',65536),@('maximumTotalBytes',262144),@('maximumElapsedSeconds',120),@('requestTimeoutSeconds',30),@('connectTimeoutSeconds',10),@('minimumIntervalSeconds',2),@('maximumDepth',16),@('maximumPaths',256),@('maximumNodes',4096),@('redirects',0),@('retries',0),@('fallbacks',0),@('concurrency',1))){
 if($plan.limits.($pair[0]) -ne $pair[1]){throw 'B57R_SOURCE_PLAN_LIMIT'}
}
$pending=Read 'docs/catalogos/bloco57-complemento/cotacoes-input.pendente.json'|ConvertFrom-Json
foreach($key in @('baseUri','logicalHost','sourceInstance','tenantScope','windowStart','windowEndExclusive','civilDateFrom','civilDateThrough','authorizationReference','temporalGuaranteeReference','representativenessReference','credentialAttestationReference','notBefore','notAfter','oracleFile','oracleSha256','executionId')){
 if($null -ne $pending.$key){throw 'B57R_PUBLIC_INPUT_MUST_REMAIN_PENDING'}
}
if($pending.sourceExecutionEnabled -isnot [bool] -or $pending.sourceExecutionEnabled -or $pending.budgetAdopted -isnot [bool] -or $pending.budgetAdopted){throw 'B57R_PUBLIC_INPUT_MUST_REMAIN_PENDING'}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
 if($initial.Count -ne 1463){throw 'B57R_PRIVATE_COUNT'}
 $privateSeen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($e in $initial){
  $expected=if($e.path -ceq 'STATES.md'){$m.observedDrift.observedSha256}else{$baseline[$e.path]}
  if(-not $privateSeen.Add($e.path) -or $e.sha256 -cne $expected){throw 'B57R_PRIVATE_BASELINE'}
  Hash ('target/bloco57-complemento/initial/'+$e.path) $e.sha256
 }
 foreach($e in $m.evidence){Hash $e.path $e.sha256}
}
if($AsMap){return ,$map}
'B57_COMPLEMENTO_SUCCESSION_PASS_NO_NEW_ACCEPTANCE_NO_SOURCE_CALL'

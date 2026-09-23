#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco60-local/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-Bloco60Local.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$path){if($null -ne $successor -and $successor.ContainsKey($path)){return $successor[$path].snapshot};return $path}
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B59_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B59_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$path){$file=Local (Historical $path);if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'B59_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $path))).Hash.ToLowerInvariant() -cne $hash){throw ('B59_HASH_'+$path)}}
function Exact($actual,$expected,[string]$reason){if(@($actual).Count -ne @($expected).Count -or (@($actual|Sort-Object -Unique)-join '|') -cne (@($expected|Sort-Object -Unique)-join '|')){throw $reason}}
$manifest='docs/catalogos/bloco59-local/manifesto.json'
$m=Read $manifest|ConvertFrom-Json -Depth 60 -DateKind String
if($m.version -ne 1 -or $m.block -ne 59 -or $m.status -cnotin @('EM_EXECUCAO','INTEGRACAO_LOCAL_USUARIOS_TESTADA')){throw 'B59_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/pos-bloco58/manifesto.json' -or $m.predecessor.sha256 -cne 'dbb53e1ac1a8b63b1e5e14786393923cf72e3e433b699e13013bddaf1d6ed480'){throw 'B59_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
$old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1532 -or $m.initialFiles -ne 1532){throw 'B59_BASELINE_COUNT'}
foreach($key in @('newAcceptances','sourceCalls','sqlOperations','runtimeCampaigns','budgetConsumed','unknownExternalEffects')){
 if(($m.$key -isnot [int] -and $m.$key -isnot [long]) -or $m.$key -ne 0){throw 'B59_LIMIT_OR_ACCEPTANCE'}
}
foreach($key in @('sqlApplied','budgetRenewed','credentialsRead','commitOrPush','canonicalTargetCleaned')){if($m.$key -isnot [bool] -or $m.$key){throw 'B59_FORBIDDEN_EFFECT'}}
foreach($pair in @(@('total',115),@('done',67),@('pending',48),@('openRoutes',191),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'B59_PROGRESS'}}
$allowed=@(
 'STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'scripts/validation/Test-PosBloco58Documentation.ps1',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeCompositionRoot.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalExecution.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java',
 'src/main/java/br/com/esl/etl/v2/modulos/usuarios/aplicacao/ExtrairUsuariosGraphQl.java',
 'src/main/java/br/com/esl/etl/v2/modulos/usuarios/aplicacao/UsuarioPromotionGateway.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/ControlPlaneGraphQlExtractionAudit.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/orquestracao/RuntimeExecutionSession.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/controle/JdbcSqlServerRuntimeRecovery.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/WindowsSqlRuntimeAuthorizationTest.java',
 'src/test/java/br/com/esl/etl/v2/contratos/Contract4924DataExportProbeTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/RuntimeRecoverySyntheticState.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/RuntimeSyntheticJdbc.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/ControlPlaneGraphQlExtractionAuditTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/ExtrairUsuariosGraphQlTest.java'
)
Exact $m.changedExistingFiles.path $allowed 'B59_EXACT_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.snapshot -cne ('docs/continuidade/historico/bloco59/'+$e.path) -or $e.before -cne $baseline[$e.path]){throw 'B59_BASELINE_BEFORE'}
 Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
}
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if(@($m.preservedFiles).Count+$map.Count -ne 1532){throw 'B59_PRESERVED_COUNT'}
foreach($e in $m.preservedFiles){
 if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B59_PRESERVED_BASELINE'}
 Hash $e.path $e.sha256
}
$new=@($m.changedExistingFiles.snapshot)+@(
 'database/preparation/bloco59/usuarios-runtime.sql',
 'docs/adr/0040-usuarios-graphql-no-runtime-operacional-local.md',
 'docs/catalogos/bloco59-local/README.md','docs/runbooks/v2-022-bloco59-usuarios-local.md',
 'scripts/validation/Test-Bloco59Local.ps1','scripts/validation/Test-Bloco59LocalGuards.ps1',
 'scripts/validation/Test-Bloco59SqlPreparation.ps1',
 'src/main/java/br/com/esl/etl/v2/bootstrap/LocalUsuariosRuntime.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersRequest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersOperationalRequestTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersIntegrationTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersSessionTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeUsersAuthorityFixture.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/RuntimeUsersJdbc.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlOperationalAuditTest.java',
 'docs/continuidade/checkpoints/0029-bloco59-adocao-e-inventario.md',
 'docs/continuidade/checkpoints/0030-bloco59-request-auditoria-cancelamento.md',
 'docs/continuidade/checkpoints/0031-bloco59-composicao-recuperacao-local.md',
 'docs/continuidade/checkpoints/0032-bloco59-verify-completo-preparacao-sql.md',
 'docs/continuidade/checkpoints/0033-bloco59-revisao-regressao-e-fechamento.md'
)
$finalCheckpoint='docs/continuidade/checkpoints/0034-bloco59-fechamento-local.md'
if($m.status -ceq 'INTEGRACAO_LOCAL_USUARIOS_TESTADA' -or (Test-Path -LiteralPath (Join-Path $root $finalCheckpoint))){$new+=$finalCheckpoint}
Exact $m.newFiles.path $new 'B59_EXACT_ADDITIONS'
foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B59_NEW_COLLISION'};Hash $e.path $e.sha256}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
 if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B59_CHECKBOX_CHANGED'}
 if(-not $current.Contains($previous) -or -not $current.Contains('B59_')){throw 'B59_HISTORY_NOT_PRESERVED'}
}
$state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'B59_PROGRESS'}
if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'B59_RETOMADA_BOUND'}
$request='src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java'
$pattern='(?s)        final var sorted =.*?(?=        final String entity = RuntimeVertical.entity)'
if([regex]::Match((Read $request),$pattern).Value -cne [regex]::Match((Read $map[$request].snapshot),$pattern).Value){throw 'B59_DATA_EXPORT_FINGERPRINT_CHANGED'}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
 if($initial.Count -ne 1532){throw 'B59_PRIVATE_COUNT'}
 $privateSeen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($e in $initial){
  if(-not $privateSeen.Add($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B59_PRIVATE_BASELINE'}
  Hash ('target/bloco59-local/initial/'+$e.path) $e.sha256
 }
 foreach($e in $m.evidence){Hash $e.path $e.sha256}
 Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
 $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 30
 if(-not $receipt.passed -or $receipt.artifacts.Count -ne 50 -or $receipt.phase -cne 'POST_B58_DOCUMENTATION'){throw 'B59_HISTORICAL_RECEIPT'}
 foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
 $j=Read 'target/bloco59-local/java-result.json'|ConvertFrom-Json -Depth 20
 if(-not $j.passed -or $j.tests -ne 1378 -or $j.failures -ne 0 -or $j.errors -ne 0 -or $j.skipped -ne 5 -or $j.maximumHeapMiB -ne 512 -or $j.java -ne 17 -or -not $j.offline){throw 'B59_JAVA_PROOF'}
 if($j.sourceBindings.Count -ne 808 -or $j.artifacts.Count -ne 211){throw 'B59_JAVA_BINDING_COUNT'}
 foreach($e in @($j.sourceBindings)+@($j.artifacts)){Hash $e.path $e.sha256}
 $jar=Read 'target/bloco59-local/jar-result.json'|ConvertFrom-Json
 if($jar.Count -ne 4 -or @($jar|Where-Object{-not $_.passed -or $_.sourceCalls -ne 0 -or $_.sqlOperations -ne 0}).Count){throw 'B59_JAR_PROOF'}
 $sql=Read 'target/bloco59-local/sql-preparation-result.json'|ConvertFrom-Json
 if(-not $sql.passed -or $sql.sqlExecuted -or $sql.sqlCompiled -or $sql.guards -ne 6){throw 'B59_SQL_LAYER'}
}
if($null -ne $successor){
 foreach($path in $successor.Keys){
  if($map.ContainsKey($path)){
   if($map[$path].after -cne $successor[$path].before){throw 'B59_B60_CHAIN'}
   $map[$path].after=$successor[$path].after
  }else{$map.Add($path,$successor[$path])}
 }
}
if($AsMap){return ,$map}
'B59_LOCAL_SUCCESSION_PASS_'+$m.status+'_NO_SOURCE_OR_PHYSICAL_SQL'

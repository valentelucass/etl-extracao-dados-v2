#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B60_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 return $file
}
function Read([string]$path){$file=Local $path;if((Get-Item $file).Length -gt 8MB){throw 'B60_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $hash){throw ('B60_HASH_'+$path)}}
function Exact($actual,$expected,[string]$reason){if(@($actual).Count -ne @($expected).Count -or (@($actual|Sort-Object -Unique)-join '|') -cne (@($expected|Sort-Object -Unique)-join '|')){throw $reason}}
# The following two lists are a closed, reviewed succession; no directory exceptions.
$allowed=@(
 'database/baseline/001_schema_foundation_baseline.sql',
 'docs/continuidade/RETOMADA.md',
 'docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'scripts/validation/Test-Bloco59Local.ps1',
 'scripts/validation/Test-Bloco59LocalGuards.ps1',
 'scripts/validation/Test-SchemaFoundationManifest.ps1',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeHttpAttempts.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeLaboratoryContract.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalExecution.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactVerifier.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredSqlConnection.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpExecutor.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpGatewayFactory.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/HttpGraphQlGateway.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersOperationalRequestTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactManifestTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpExecutorTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/HttpGraphQlGatewayTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/sombra/SchemaFoundationSqlContractTest.java',
 'STATES.md'
)
$allowedNew=@(
 'database/manifest/runtime-bloco60.json',
 'database/migrations/V024__bind_source_protocols_and_users_runtime.sql',
 'database/proposals/bloco60-local/activate.sql',
 'database/proposals/bloco60-local/adversarial.sql',
 'database/proposals/bloco60-local/all-quality-references.json',
 'database/proposals/bloco60-local/catalog.sql',
 'database/proposals/bloco60-local/collision-preflight.sql',
 'database/proposals/bloco60-local/compensate.sql',
 'database/proposals/bloco60-local/concurrent-barrier.sql',
 'database/proposals/bloco60-local/concurrent-observe.sql',
 'database/proposals/bloco60-local/install.sql',
 'database/proposals/bloco60-local/master-target.sql',
 'database/proposals/bloco60-local/matrix.json',
 'database/proposals/bloco60-local/package.json',
 'database/proposals/bloco60-local/preflight.sql',
 'database/proposals/bloco60-local/preservation.sql',
 'database/proposals/bloco60-local/preserved-row-hashes.sql',
 'database/proposals/bloco60-local/profile-active.sql',
 'database/proposals/bloco60-local/profile-after.sql',
 'database/proposals/bloco60-local/profile-before.sql',
 'database/proposals/bloco60-local/qualify-baseline-suffix.sql',
 'database/proposals/bloco60-local/qualify-upgrade.sql',
 'database/proposals/bloco60-local/quality-references.json',
 'database/proposals/bloco60-local/README.md',
 'database/proposals/bloco60-local/recovery-readback.sql',
 'database/proposals/bloco60-local/recovery-state.sql',
 'database/proposals/bloco60-local/requests/APPLY_ACK_LOST_RESUME.json',
 'database/proposals/bloco60-local/requests/APPLY_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/AUDIT_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/AUDIT_NULL.json',
 'database/proposals/bloco60-local/requests/AUTHORITY_EXPIRED.json',
 'database/proposals/bloco60-local/requests/AUTHORITY_INCONSISTENT.json',
 'database/proposals/bloco60-local/requests/AUTHORITY_SWAPPED.json',
 'database/proposals/bloco60-local/requests/COLETAS_KNOWN.json',
 'database/proposals/bloco60-local/requests/COLETAS_RUN.json',
 'database/proposals/bloco60-local/requests/COLETAS_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/COLETAS_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/requests/CONCURRENT_A.json',
 'database/proposals/bloco60-local/requests/CONCURRENT_B.json',
 'database/proposals/bloco60-local/requests/CONCURRENT_SEALED.json',
 'database/proposals/bloco60-local/requests/CONSUME_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/COTACOES_KNOWN.json',
 'database/proposals/bloco60-local/requests/COTACOES_RUN.json',
 'database/proposals/bloco60-local/requests/COTACOES_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/COTACOES_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/requests/DIAGNOSTIC_ADMINISTERED.json',
 'database/proposals/bloco60-local/requests/DIAGNOSTIC_STANDARD.json',
 'database/proposals/bloco60-local/requests/DQ_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/DQ_MISSING.json',
 'database/proposals/bloco60-local/requests/FORCE_RUN.json',
 'database/proposals/bloco60-local/requests/FRETES_KNOWN.json',
 'database/proposals/bloco60-local/requests/FRETES_RUN.json',
 'database/proposals/bloco60-local/requests/FRETES_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/FRETES_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/requests/HALT_AFTER_APPLY_RESUME.json',
 'database/proposals/bloco60-local/requests/HALT_AFTER_APPLY.json',
 'database/proposals/bloco60-local/requests/HALT_AFTER_PREPARE.json',
 'database/proposals/bloco60-local/requests/HALT_AFTER_SEAL_RESUME.json',
 'database/proposals/bloco60-local/requests/HALT_AFTER_SEAL.json',
 'database/proposals/bloco60-local/requests/HALT_BEFORE_APPLY.json',
 'database/proposals/bloco60-local/requests/HALT_BEFORE_PREPARE.json',
 'database/proposals/bloco60-local/requests/HALT_BEFORE_SEAL.json',
 'database/proposals/bloco60-local/requests/INVALID_OPERATION.json',
 'database/proposals/bloco60-local/requests/INVALID_PAGE.json',
 'database/proposals/bloco60-local/requests/INVALID_PROTOCOL.json',
 'database/proposals/bloco60-local/requests/LEASE_EXPIRED_REFUSED.json',
 'database/proposals/bloco60-local/requests/LEASE_EXPIRY_HALT.json',
 'database/proposals/bloco60-local/requests/LOCALIZACAO_CARGAS_KNOWN.json',
 'database/proposals/bloco60-local/requests/LOCALIZACAO_CARGAS_RUN.json',
 'database/proposals/bloco60-local/requests/LOCALIZACAO_CARGAS_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/LOCALIZACAO_CARGAS_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/requests/MANIFESTOS_KNOWN.json',
 'database/proposals/bloco60-local/requests/MANIFESTOS_RUN.json',
 'database/proposals/bloco60-local/requests/MANIFESTOS_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/MANIFESTOS_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/requests/MUTATE_CONFIGURATION.json',
 'database/proposals/bloco60-local/requests/MUTATE_CONTRACT.json',
 'database/proposals/bloco60-local/requests/MUTATE_ENTITY.json',
 'database/proposals/bloco60-local/requests/MUTATE_PROTOCOL.json',
 'database/proposals/bloco60-local/requests/MUTATE_TENANT.json',
 'database/proposals/bloco60-local/requests/OPERATOR_CANNOT_WRITE.json',
 'database/proposals/bloco60-local/requests/PREPARE_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/REPLAY_USERS_PARTIAL.json',
 'database/proposals/bloco60-local/requests/REPLAY_USUARIOS_RUN.json',
 'database/proposals/bloco60-local/requests/SEAL_ACK_LOST_RESUME.json',
 'database/proposals/bloco60-local/requests/SEAL_ACK_LOST.json',
 'database/proposals/bloco60-local/requests/STAGE_FAILURE.json',
 'database/proposals/bloco60-local/requests/STANDARD_AUTHORITY_ABSENT.json',
 'database/proposals/bloco60-local/requests/USERS_CANCEL.json',
 'database/proposals/bloco60-local/requests/USERS_CONFLICT.json',
 'database/proposals/bloco60-local/requests/USERS_INVALID_NODE.json',
 'database/proposals/bloco60-local/requests/USERS_NOOP.json',
 'database/proposals/bloco60-local/requests/USERS_NULL_TERMINAL.json',
 'database/proposals/bloco60-local/requests/USERS_ONE_PAGE.json',
 'database/proposals/bloco60-local/requests/USERS_PARTIAL.json',
 'database/proposals/bloco60-local/requests/USERS_UPDATE.json',
 'database/proposals/bloco60-local/requests/USUARIOS_KNOWN.json',
 'database/proposals/bloco60-local/requests/USUARIOS_RUN.json',
 'database/proposals/bloco60-local/requests/USUARIOS_STATUS_ETL_V2_EXEC.json',
 'database/proposals/bloco60-local/requests/USUARIOS_STATUS_ETL_V2_VIEW.json',
 'database/proposals/bloco60-local/seed-quality.sql',
 'database/proposals/bloco60-local/verify-quality.sql',
 'database/validation/056_validate_bloco60_runtime.sql',
 'database/validation/057_exercise_bloco60_adversarial_read.sql',
 'docs/adr/0041-origem-logica-multiprotocolo-e-usuarios-sql-local.md',
 'docs/catalogos/bloco60-local/README.md',
 'docs/continuidade/checkpoints/0035-bloco60-adocao-integridade-telemetria.md',
 'docs/continuidade/checkpoints/0036-bloco60-sql-autoridade-e-controladores.md',
 'docs/continuidade/checkpoints/0037-bloco60-verify-e-pacote-fisico.md',
 'docs/continuidade/checkpoints/0038-bloco60-fechamento-offline-aprovacao-pendente.md',
 'docs/continuidade/historico/bloco60/database/baseline/001_schema_foundation_baseline.sql',
 'docs/continuidade/historico/bloco60/docs/continuidade/RETOMADA.md',
 'docs/continuidade/historico/bloco60/docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'docs/continuidade/historico/bloco60/scripts/validation/Test-Bloco59Local.ps1',
 'docs/continuidade/historico/bloco60/scripts/validation/Test-Bloco59LocalGuards.ps1',
 'docs/continuidade/historico/bloco60/scripts/validation/Test-SchemaFoundationManifest.ps1',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeHttpAttempts.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeLaboratoryContract.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalExecution.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactVerifier.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredSqlConnection.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpExecutor.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpGatewayFactory.java',
 'docs/continuidade/historico/bloco60/src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/HttpGraphQlGateway.java',
 'docs/continuidade/historico/bloco60/src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersOperationalRequestTest.java',
 'docs/continuidade/historico/bloco60/src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactManifestTest.java',
 'docs/continuidade/historico/bloco60/src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpExecutorTest.java',
 'docs/continuidade/historico/bloco60/src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/HttpGraphQlGatewayTest.java',
 'docs/continuidade/historico/bloco60/src/test/java/br/com/esl/etl/v2/plataforma/persistencia/sombra/SchemaFoundationSqlContractTest.java',
 'docs/continuidade/historico/bloco60/STATES.md',
 'docs/runbooks/v2-022-bloco60-usuarios-sql-local.md',
 'scripts/validation/Bloco60AdminProcess.cs',
 'scripts/validation/Bloco60Assertions.psm1',
 'scripts/validation/Bloco60Budget.psm1',
 'scripts/validation/Bloco60Process.cs',
 'scripts/validation/Invoke-Bloco60Physical.ps1',
 'scripts/validation/New-Bloco60Package.ps1',
 'scripts/validation/New-Bloco60Requests.ps1',
 'scripts/validation/New-Bloco60ReviewedBundle.ps1',
 'scripts/validation/Test-Bloco60Assertions.ps1',
 'scripts/validation/Test-Bloco60Controllers.ps1',
 'scripts/validation/Test-Bloco60JarOffline.ps1',
 'scripts/validation/Test-Bloco60Local.ps1',
 'scripts/validation/Test-Bloco60LocalGuards.ps1',
 'scripts/validation/Test-Bloco60Package.ps1',
 'scripts/validation/Test-Bloco60SqlContract.ps1',
 'src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/LaboratorySqlBudget.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlHttpAttemptObserver.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersPhysicalProbe.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeUsersPhysicalProbeTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/LaboratorySqlBudgetTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeUsersPhysicalAuthority.java'
)
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'IMPLEMENTED_OFFLINE_PHYSICAL_APPROVAL_PENDING'){throw 'B60_STATUS'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco59-local/manifesto.json' -or $m.predecessor.sha256 -cne '7af4a41536b254974ee49fe20417a38ae3e711a673a0e7f298c1cf70240c6c7a'){throw 'B60_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
 foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
 foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1572 -or $m.initialFiles -ne 1572){throw 'B60_BASELINE_COUNT'}
 foreach($key in @('newAcceptances','realSourceCalls','physicalSqlOperations','runtimeCampaigns','physicalBudgetConsumed','unknownExternalEffects')){if(($m.$key -isnot [int] -and $m.$key -isnot [long]) -or $m.$key -ne 0){throw 'B60_LIMIT_OR_ACCEPTANCE'}}
 foreach($key in @('sqlApplied','sqlCompiled','approvalRecorded','budgetRenewed','credentialsRead','commitOrPush','canonicalTargetCleaned')){if($m.$key -isnot [bool] -or $m.$key){throw 'B60_FORBIDDEN_EFFECT'}}
 foreach($pair in @(@('total',115),@('done',67),@('pending',48),@('openRoutes',191),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'B60_PROGRESS'}}
 Exact $m.changedExistingFiles.path $allowed 'B60_EXACT_DELTAS'
 Exact $m.newFiles.path $allowedNew 'B60_EXACT_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){
  if(-not $baseline.ContainsKey($e.path) -or $e.snapshot -cne ('docs/continuidade/historico/bloco60/'+$e.path) -or $e.before -cne $baseline[$e.path]){throw 'B60_BASELINE_BEFORE'}
  Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
 }
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1572){throw 'B60_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){
  if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60_PRESERVED_BASELINE'}
  Hash $e.path $e.sha256
 }
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B60_CHECKBOX_CHANGED'}
  if(-not $current.Contains($previous) -or -not $current.Contains('B60_')){throw 'B60_HISTORY_NOT_PRESERVED'}
 }
 $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
 if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'B60_PROGRESS'}
 if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'B60_RETOMADA_BOUND'}
 if($m.physicalPackage.path -cne 'database/proposals/bloco60-local/package.json'){throw 'B60_PACKAGE_PATH'}
 Hash $m.physicalPackage.path $m.physicalPackage.sha256
 if($IncludePrivateEvidence){
  Hash $m.initialInventory.path $m.initialInventory.sha256
  $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
  if($initial.Count -ne 1572){throw 'B60_PRIVATE_COUNT'}
  foreach($e in $initial){if(-not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B60_PRIVATE_BASELINE'};Hash ('target/bloco60-local/initial/'+$e.path) $e.sha256}
  if($m.historicalReceipt.path -cne 'target/bloco59-local/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba'){throw 'B60_HISTORICAL_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 40
  if(-not $receipt.passed -or $receipt.artifacts.Count -ne 386){throw 'B60_HISTORICAL_RECEIPT_COUNT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $m.evidence){Hash $e.path $e.sha256}
  $j=Read 'target/bloco60-local/java-result.json'|ConvertFrom-Json -Depth 20
  if(-not $j.passed -or $j.tests -ne 1390 -or $j.failures -ne 0 -or $j.errors -ne 0 -or $j.skipped -ne 5 -or $j.maximumHeapMiB -ne 512 -or $j.java -ne 17 -or -not $j.offline -or $j.run -cne 'verify-03'){throw 'B60_JAVA_PROOF'}
  foreach($e in @($j.sourceBindings)+@($j.artifacts)){Hash $e.path $e.sha256}
  $package=Read $m.physicalPackage.path|ConvertFrom-Json -Depth 30
  foreach($e in $package.files){Hash $e.path $e.sha256}
 }
 return ,$map
}
$manifest=Read 'docs/catalogos/bloco60-local/manifesto.json'|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){
 foreach($tuple in @(
  @('B60_STATUS',{param($m)$m.status='QUALIFICACAO_FISICA_LOCAL_USUARIOS'}),
  @('B60_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
  @('B60_LIMIT_OR_ACCEPTANCE',{param($m)$m.newAcceptances=1}),
  @('B60_LIMIT_OR_ACCEPTANCE',{param($m)$m.physicalBudgetConsumed=1}),
  @('B60_LIMIT_OR_ACCEPTANCE',{param($m)$m.realSourceCalls=1}),
  @('B60_FORBIDDEN_EFFECT',{param($m)$m.sqlApplied=$true}),
  @('B60_FORBIDDEN_EFFECT',{param($m)$m.approvalRecorded=$true}),
  @('B60_FORBIDDEN_EFFECT',{param($m)$m.budgetRenewed=$true}),
  @('B60_PROGRESS',{param($m)$m.progress.done=68}),
  @('B60_EXACT_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/main/java'}),
  @('B60_EXACT_ADDITIONS',{param($m)$m.newFiles[0].path='docs/'}),
  @('B60_BASELINE_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
  @('B60_PRESERVED_BASELINE',{param($m)$m.preservedFiles[0].sha256='0'*64})
 )){
  $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
  & $tuple[1] $copy;$reason='NOT_REJECTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
  if($reason -cne $tuple[0]){throw ('B60_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;guards=$guards;changed=$result.Count;added=$manifest.newFiles.Count;private=[bool]$IncludePrivateEvidence;physicalSql=0}|ConvertTo-Json

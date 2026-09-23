#Requires -Version 7.5
param([switch]$SelfTest,[string]$ManifestPath='database/proposals/bloco60-local/package.json')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
function ReadLocal([string]$Path){$utf8.GetString([IO.File]::ReadAllBytes((Join-Path $root $Path)))}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'PREPARED_APPROVAL_REQUIRED' -or $m.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $m.serverName -cne 'RTR-SVW-002'){throw 'B60_PACKAGE_TARGET'}
 if($m.administrator -cne 'RTR-SVW-002\suporte' -or $m.executor -cne 'RTR-SVW-002\etl_v2_exec' -or $m.observer -cne 'RTR-SVW-002\etl_v2_view'){throw 'B60_PACKAGE_IDENTITIES'}
 if($m.validFrom -cne '2026-09-09T00:00:00Z' -or $m.validUntil -cne '2026-09-16T00:00:00Z'){throw 'B60_PACKAGE_OWN_WINDOW'}
 foreach($key in @('sqlExecuted','sourceRealExecuted','approvalRecorded','cleanEmptyDatabaseExecuted')){if($m[$key] -isnot [bool] -or $m[$key]){throw 'B60_PACKAGE_FALSE_PHYSICAL_CLAIM'}}
 if($m.sourceInstance -cne 'LOCAL_V2' -or $m.tenantScope -cne 'LOCAL_V2' -or $m.loopback -cne 'http://127.0.0.1:62160'){throw 'B60_PACKAGE_LOGICAL_SOURCE'}
 if($m.predecessorReceipt -cne '36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba' -or $m.expectedCatalog -cne 'ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae' -or $m.expectedRows -ne 9137){throw 'B60_PACKAGE_PRIOR_CHECKPOINT'}
 $limits=@{campaignMinutes=60;jvms=80;sqlcmd=240;ordinarySqlcmd=232;recoveryEscrow=8;concurrentJvms=2;concurrentSqlcmd=2;concurrentProcessesIncludingController=5;concurrentSqlSessions=10;jvmSeconds=60;sqlcmdSeconds=45;sqlCommandSeconds=30;heapMiBPerJvm=512;connectionsPerJvm=256;submissionsPerJvm=512;rpcInnerCommitCeilingPerJvm=4096;sourceRequests=400;pagesPerJvm=4;nodesPerJvm=80;graphQlPageSize=20;graphQlNodesOffered=800;dataExportRowsPerJvm=16;dataExportDerivedRowsPerJvm=512;responseBytesPerRequest=1048576;sourceResponseBytesOffered=16777216;sourceRequestBytes=8388608;javaOutputBytes=16384;sqlcmdOutputBytes=1048576;refunded=0;renewals=0}
 if($m.limits.Count -ne $limits.Count){throw 'B60_PACKAGE_LIMIT_SCHEMA'}
 foreach($key in $limits.Keys){if($m.limits[$key] -isnot [long] -and $m.limits[$key] -isnot [int] -or $m.limits[$key] -ne $limits[$key]){throw 'B60_PACKAGE_LIMIT'}}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
 if($m.files.Count -gt 500 -or $m.files.Count -lt 150){throw 'B60_PACKAGE_FILE_BOUND'}
 foreach($entry in $m.files){
  if($entry.path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $entry.path.Contains('..') -or $entry.path.StartsWith('/') -or -not $seen.Add($entry.path) -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'B60_PACKAGE_PATH'}
  $node=Get-Item -LiteralPath (Join-Path $root $entry.path)
  while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60_PACKAGE_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
  if((Get-FileHash (Join-Path $root $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'B60_PACKAGE_HASH'}
 }
 foreach($required in @('scripts/validation/Invoke-Bloco60Physical.ps1','scripts/validation/Bloco60Budget.psm1','scripts/validation/Bloco60Process.cs','scripts/validation/Bloco60AdminProcess.cs','scripts/validation/Bloco60Assertions.psm1','database/migrations/V024__bind_source_protocols_and_users_runtime.sql','database/proposals/bloco60-local/matrix.json','database/proposals/bloco60-local/recovery-state.sql','database/proposals/bloco60-local/compensate.sql','target/bloco60-local/java-result.json')){if(-not $seen.Contains($required)){throw 'B60_PACKAGE_MISSING_EXECUTION_INPUT'}}
 $matrix=ReadLocal 'database/proposals/bloco60-local/matrix.json'|ConvertFrom-Json -AsHashtable
 if($m.cases -ne 74 -or $matrix.Count -ne 74){throw 'B60_PACKAGE_CASE_COUNT'}
 $names=@($matrix.id)
 foreach($name in @('USUARIOS_RUN','COLETAS_RUN','FRETES_RUN','MANIFESTOS_RUN','COTACOES_RUN','LOCALIZACAO_CARGAS_RUN','USERS_ONE_PAGE','USERS_NOOP','USERS_UPDATE','USERS_CONFLICT','USERS_PARTIAL','USERS_CANCEL','CONSUME_ACK_LOST','PREPARE_ACK_LOST','SEAL_ACK_LOST','APPLY_ACK_LOST','HALT_BEFORE_PREPARE','HALT_AFTER_PREPARE','HALT_BEFORE_SEAL','HALT_AFTER_SEAL','HALT_BEFORE_APPLY','HALT_AFTER_APPLY','LEASE_EXPIRED_REFUSED','CONCURRENT_A','CONCURRENT_B','FORCE_RUN','AUTHORITY_SWAPPED','AUTHORITY_EXPIRED','AUTHORITY_INCONSISTENT')){if($name -cnotin $names){throw 'B60_PACKAGE_REQUIRED_CASE'}}
 $invocations=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($case in $matrix){
  if(-not $seen.Contains('database/proposals/bloco60-local/'+$case.request) -or $case.expectedExit.Count -lt 1 -or $case.expectedHttp.Count -lt 1){throw 'B60_PACKAGE_CASE_INPUT'}
  $request=ReadLocal ('database/proposals/bloco60-local/'+$case.request)|ConvertFrom-Json -AsHashtable
  if(-not $invocations.Add($request.invocationId)){throw 'B60_PACKAGE_REUSED_INVOCATION'}
  foreach($key in @('invocationId','executionId','cycleId')){if([guid]$request[$key] -eq [guid]::Empty){throw 'B60_PACKAGE_UUID'}}
  if($request.maximumPages -cne '4' -or $request.leaseSeconds -cnotin @('3','30')){throw 'B60_PACKAGE_REQUEST_CAP'}
  if($request.Contains('operation') -and $request.maximumNodes -cne '80'){throw 'B60_PACKAGE_NODES'}
 }
 $script=ReadLocal 'scripts/validation/Invoke-Bloco60Physical.ps1'
 if($script.IndexOf('B60_APPROVED_PACKAGE_HASH_REQUIRED') -gt $script.IndexOf('function SqlStart') -or $script -notmatch 'StopOwned' -or $script -notmatch 'RECONCILE' -or $script -match '(?i)Get-Process\s+(java|sqlservr)|Stop-Service|Start-Service'){throw 'B60_PACKAGE_CONTROLLER_BOUNDARY'}
 $migration=ReadLocal 'database/migrations/V024__bind_source_protocols_and_users_runtime.sql'
 $qualification=ReadLocal 'database/proposals/bloco60-local/qualify-upgrade.sql'
 if(-not $qualification.Contains($migration) -or $qualification -match '\bCOMMIT TRANSACTION;\s*SELECT N.B60_.*QUALIFIED' -or $qualification -notmatch 'B60_UPGRADE_QUALIFIED_AND_ROLLED_BACK'){throw 'B60_PACKAGE_UPGRADE_BINDING'}
 $initialBaseline=ReadLocal 'target/bloco60-local/initial/database/baseline/001_schema_foundation_baseline.sql'
 $baseline=ReadLocal 'database/baseline/001_schema_foundation_baseline.sql'
 if(-not $baseline.StartsWith($initialBaseline,[StringComparison]::Ordinal) -or ($baseline.Substring($initialBaseline.Length)).Trim() -cne ':r "..\migrations\V024__bind_source_protocols_and_users_runtime.sql"'){throw 'B60_PACKAGE_BASELINE_PREFIX'}
 foreach($path in @('install.sql','activate.sql','compensate.sql')){
  $sql=ReadLocal ('database/proposals/bloco60-local/'+$path)
  if($sql -match '(?i)\b(CREATE|DROP)\s+(DATABASE|LOGIN|USER)\b|\bTRUNCATE\b|\bDELETE\s+FROM\s+(ctl|stg|core|recon|ref)\.' -or $sql -notmatch 'ETL_SISTEMA_V2_SHADOW'){throw 'B60_PACKAGE_FORBIDDEN_SQL'}
 }
 $seed=ReadLocal 'database/proposals/bloco60-local/quality-references.json'|ConvertFrom-Json
 foreach($q in $seed){if([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($q.material))).ToLowerInvariant() -cne $q.fingerprint){throw 'B60_PACKAGE_QUALITY_HASH'}}
}
$manifest=ReadLocal $ManifestPath|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String
Check $manifest
$guards=0
if($SelfTest){
 foreach($mutation in @(
  {param($m)$m.target='localhost/ETL_SISTEMA'},
  {param($m)$m.executor='RTR-SVW-002\suporte'},
  {param($m)$m.validUntil='2026-10-07T22:34:30.615Z'},
  {param($m)$m.sqlExecuted=$true},
  {param($m)$m.approvalRecorded=$true},
  {param($m)$m.sourceInstance='USERS_ANOTHER_SOURCE'},
  {param($m)$m.limits.jvms=81},
  {param($m)$m.limits.refunded=1},
  {param($m)$m.expectedRows=0},
  {param($m)$m.files[0].sha256='0'*64},
  {param($m)$m.files[0].path='../foreign'},
  {param($m)$m.cases=73}
 )){
  $copy=($manifest|ConvertTo-Json -Depth 40)|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String
  & $mutation $copy;$refused=$false;try{Check $copy}catch{$refused=$true}
  if(-not $refused){throw 'B60_PACKAGE_COUNTERPROOF_ACCEPTED'};$guards++
 }
}
@{passed=$true;guards=$guards;cases=$manifest.cases;files=$manifest.files.Count;layer='STATIC_PHYSICAL_PACKAGE';sqlExecuted=$false;approval=$false}|ConvertTo-Json

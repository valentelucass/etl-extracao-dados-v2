#Requires -Version 7.5
param([ValidatePattern('^bundle-v[1-9][0-9]?$')][string]$Revision='bundle-v2')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$folder=Join-Path $root 'database/proposals/bloco60-local'
$output=Join-Path $folder 'package.json';if(Test-Path $output){throw 'B60_PACKAGE_EXISTS_PRESERVE'}
$paths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$variants=Get-Content (Join-Path $root ('target/bloco60-local/'+$Revision+'/variants.json')) -Raw|ConvertFrom-Json -AsHashtable
foreach($directory in @($folder,(Join-Path $root ('target/bloco60-local/'+$Revision)),(Join-Path $root 'database/migrations'),(Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'))){
 foreach($file in Get-ChildItem $directory -Recurse -File){[void]$paths.Add([IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/'))}
}
foreach($file in Get-ChildItem (Join-Path $root 'scripts/validation') -File|Where-Object Name -like '*Bloco60*'){[void]$paths.Add('scripts/validation/'+$file.Name)}
foreach($path in @('database/baseline/001_schema_foundation_baseline.sql','database/validation/056_validate_bloco60_runtime.sql','database/validation/057_exercise_bloco60_adversarial_read.sql','target/bloco60-local/verify-03/exit.json','target/bloco60-local/java-result.json')){[void]$paths.Add($path)}
$files=@($paths|Sort-Object|ForEach-Object {[ordered]@{path=$_;sha256=(Get-FileHash (Join-Path $root $_)).Hash.ToLowerInvariant()}})
$matrix=Get-Content (Join-Path $folder 'matrix.json') -Raw|ConvertFrom-Json
$manifest=[ordered]@{
 version=1;block=60;status='PREPARED_APPROVAL_REQUIRED';target='localhost/ETL_SISTEMA_V2_SHADOW';serverName='RTR-SVW-002';administrator='RTR-SVW-002\suporte';executor='RTR-SVW-002\etl_v2_exec';observer='RTR-SVW-002\etl_v2_view';
 validFrom='2026-09-09T00:00:00Z';validUntil='2026-09-16T00:00:00Z';authorityId='fd966e17-71c7-4071-9ccc-bedcaecffcd0';policy='c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d';
 predecessorReceipt='36a8b88b7efbcfd52859214cb91c647cea759335be46e684bcad7df5f210ecba';expectedCatalog='ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae';expectedRows=9137;sourceInstance='LOCAL_V2';tenantScope='LOCAL_V2';loopback='http://127.0.0.1:62160';
 limits=[ordered]@{campaignMinutes=60;jvms=80;sqlcmd=240;ordinarySqlcmd=232;recoveryEscrow=8;concurrentJvms=2;concurrentSqlcmd=2;concurrentProcessesIncludingController=5;concurrentSqlSessions=10;jvmSeconds=60;sqlcmdSeconds=45;sqlCommandSeconds=30;heapMiBPerJvm=512;connectionsPerJvm=256;submissionsPerJvm=512;rpcInnerCommitCeilingPerJvm=4096;sourceRequests=400;pagesPerJvm=4;nodesPerJvm=80;graphQlPageSize=20;graphQlNodesOffered=800;dataExportRowsPerJvm=16;dataExportDerivedRowsPerJvm=512;responseBytesPerRequest=1048576;sourceResponseBytesOffered=16777216;sourceRequestBytes=8388608;javaOutputBytes=16384;sqlcmdOutputBytes=1048576;refunded=0;renewals=0}
 minimumPermissionDelta=@('GRANT EXECUTE stg.usp_stage_usuario_record TO RTR-SVW-002\etl_v2_exec','GRANT EXECUTE core.usp_apply_reconcile_publish_usuarios TO RTR-SVW-002\etl_v2_exec','SERVICE mapping 17 -> 18: replay=1, force_run=1; same original mapping validity','4 USERS scopes: SERVICE/OPERATOR x BACKFILL/REPLAY, version=1','2 new strict Users DQ policies; no replacement of historical policies','LOCAL_V2/GRAPHQL protocol binding; preserve original DATA_EXPORT kind');
 compensation=@('SERVICE mapping 18 -> 19: replay=0, force_run=0','4 USERS scopes revoked, version=2','2 USERS DQ policies revoked','Revoke the exact 2 new procedure grants','Preserve V024, protocol metadata, synthetic rows, histories, decisions and receipts','Stop owned JVM/sqlcmd handles and loopback; no global cleanup');
 baselineMethod='EXACT_V023_PREFIX_PLUS_TWO_ISOLATED_ROLLBACK_SUFFIX_ROUTES';cleanEmptyDatabaseExecuted=$false;cases=$matrix.Count;variants=$variants;files=$files;sqlExecuted=$false;sourceRealExecuted=$false;approvalRecorded=$false
}
[IO.File]::WriteAllText($output,($manifest|ConvertTo-Json -Depth 15),[Text.UTF8Encoding]::new($false))
@{package='database/proposals/bloco60-local/package.json';sha256=(Get-FileHash $output).Hash.ToLowerInvariant();files=$files.Count;cases=$matrix.Count;sqlExecuted=$false}|ConvertTo-Json

#Requires -Version 7.5
# Offline preparation only. The closed, approved campaign is never reopened.
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$old=Join-Path $root 'database/proposals/bloco60-local'
$folder=Join-Path $root 'database/proposals/bloco60-correcao'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$approved='a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e'
if((Get-FileHash (Join-Path $old 'package.json')).Hash.ToLowerInvariant() -cne $approved){throw 'B60R_PREDECESSOR_HASH'}
$previous=Get-Content (Join-Path $old 'package.json') -Raw|ConvertFrom-Json -AsHashtable -Depth 30
foreach($e in $previous.files){if((Get-FileHash (Join-Path $root $e.path)).Hash.ToLowerInvariant() -cne $e.sha256){throw 'B60R_PREDECESSOR_DRIFT'}}
if(Test-Path $folder){throw 'B60R_PROPOSAL_EXISTS_PRESERVE'}
function ReadOld([string]$Name){[IO.File]::ReadAllText((Join-Path $old $Name),$utf8)}
function WriteNew([string]$Name,[string]$Body){$path=Join-Path $folder $Name;[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path));[IO.File]::WriteAllText($path,$Body,$utf8)}
function ReplaceOne([string]$Body,[string]$Before,[string]$After){if($Body.Split(@($Before),[StringSplitOptions]::None).Count -ne 2){throw 'B60R_EXACT_REPLACEMENT'};$Body.Replace($Before,$After)}
$replacements=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
$requests=@(Get-ChildItem (Join-Path $old 'requests') -File)
foreach($file in $requests){
 $r=Get-Content $file.FullName -Raw|ConvertFrom-Json -AsHashtable
 foreach($key in @('invocationId','executionId','cycleId','replayOf')){if($r[$key] -and -not $replacements.ContainsKey($r[$key])){$replacements.Add($r[$key],[guid]::NewGuid().ToString())}}
 if(-not $replacements.ContainsKey($r.idempotencyKey)){$replacements.Add($r.idempotencyKey,'B60_'+[guid]::NewGuid().ToString('N'))}
}
$quality=ReadOld 'quality-references.json'|ConvertFrom-Json -AsHashtable
foreach($q in $quality){
 $version=$q.version.Replace('-v1','-v2');$material=$q.material.Replace($q.version,$version).Replace('2026-09-09T00:00:00.124','2026-09-10T00:00:00.124')
 $fingerprint=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($material))).ToLowerInvariant()
 $replacements.Add($q.version,$version);$replacements.Add($q.fingerprint,$fingerprint)
}
function Rewrite([string]$Text){foreach($key in $replacements.Keys){$Text=$Text.Replace($key,$replacements[$key])};$Text.Replace('2026-09-09T00:00:00.124','2026-09-10T00:00:00.124')}
foreach($name in @('master-target.sql','catalog.sql','collision-preflight.sql','preserved-row-hashes.sql','seed-quality.sql','verify-quality.sql','quality-references.json','all-quality-references.json','matrix.json','concurrent-barrier.sql','concurrent-observe.sql','adversarial.sql','recovery-readback.sql')){WriteNew $name (Rewrite (ReadOld $name))}
foreach($file in $requests){WriteNew ('requests/'+$file.Name) (Rewrite ([IO.File]::ReadAllText($file.FullName,$utf8)))}
# Keep the exact 74 cases and their oracles, with fresh identities and synthetic Users keys.
$matrix=Get-Content (Join-Path $folder 'matrix.json') -Raw|ConvertFrom-Json -AsHashtable
foreach($case in $matrix){$case.group='B60_C_'+[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($case.group))).Substring(0,16);if($case.group -cnotmatch '^B60_[A-Z0-9_]{1,40}$'){throw 'B60R_SYNTHETIC_GROUP_BOUND'}}
WriteNew 'matrix.json' ($matrix|ConvertTo-Json -Depth 15)
$before=ReadOld 'profile-after.sql'
WriteNew 'profile-before.sql' ($before.Replace('B60_PROFILE_AFTER_PASS','B60R_PROFILE_BEFORE_PASS')+@'

IF EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v2',N'bloco60-usuarios-replay-v2'))
 THROW 52970,N'B60R_POLICY_COLLISION',1;
'@)
$after=$before.Replace('mapping_version=19','mapping_version=21').Replace('scope_version=2 AND revoked=1','scope_version=4 AND revoked=1').Replace('B60_PROFILE_AFTER_PASS','B60R_PROFILE_AFTER_PASS')
WriteNew 'profile-after.sql' ($after+@'

IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v2',N'bloco60-usuarios-replay-v2') AND policy_state=N'REVOKED')<>2
 THROW 52970,N'B60R_NEW_POLICIES_REVOKED',1;
'@)
$active=(Rewrite (ReadOld 'profile-active.sql')).Replace('mapping_version=18','mapping_version=20').Replace('scope_version=1 AND revoked=0','scope_version=3 AND revoked=0').Replace('B60_PROFILE_ACTIVE_PASS','B60R_PROFILE_ACTIVE_PASS')
# The old policies remain revoked throughout the corrective campaign.
WriteNew 'profile-active.sql' ($active+@'

IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco60-usuarios-backfill-v1',N'bloco60-usuarios-replay-v1') AND policy_state=N'REVOKED')<>2
 THROW 52970,N'B60R_HISTORICAL_POLICIES_PRESERVED',1;
'@)
$preflight=(ReadOld 'preflight.sql').Replace("OBJECT_ID(N'ctl.source_protocol_binding') IS NOT NULL","OBJECT_ID(N'ctl.source_protocol_binding') IS NULL")
WriteNew 'preflight.sql' $preflight
WriteNew 'activate.sql' @'
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' OR @@TRANCOUNT<>0
 OR SYSUTCDATETIME()<'2026-09-10T00:00:00' OR SYSUTCDATETIME()>='2026-09-16T00:00:00'
 THROW 52970,N'B60R_ACTIVATION_WINDOW_REQUIRED',1;
BEGIN TRANSACTION;
:r "profile-before.sql"
GRANT EXECUTE ON OBJECT::stg.usp_stage_usuario_record TO [RTR-SVW-002\etl_v2_exec];
GRANT EXECUTE ON OBJECT::core.usp_apply_reconcile_publish_usuarios TO [RTR-SVW-002\etl_v2_exec];
UPDATE ctl.runtime_identity_scope SET scope_version=3,revoked=0
 WHERE workload=N'usuarios' AND environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2'
 AND original_sid IN(SUSER_SID(N'RTR-SVW-002\etl_v2_exec'),SUSER_SID(N'RTR-SVW-002\etl_v2_view'))
 AND mode IN(N'BACKFILL',N'REPLAY') AND scope_version=2 AND revoked=1;
IF @@ROWCOUNT<>4 THROW 52970,N'B60R_EXACT_FOUR_EXISTING_SCOPES',1;
UPDATE ctl.runtime_identity_mapping SET replay=1,force_run=1,mapping_version=20
 WHERE original_sid=SUSER_SID(N'RTR-SVW-002\etl_v2_exec') AND mapping_version=19 AND replay=0 AND force_run=0;
IF @@ROWCOUNT<>1 THROW 52970,N'B60R_EXACT_SERVICE_VERSION',1;
:r "seed-quality.sql"
:r "verify-quality.sql"
:r "profile-active.sql"
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52970,N'B60R_SINGLE_COMMIT_REQUIRED',1;
COMMIT TRANSACTION;
SELECT N'B60R_ACTIVATED_34_GRANTS_20_SCOPES_SERVICE_20';
'@
$compensate=Rewrite (ReadOld 'compensate.sql')
$compensate=$compensate.Replace('NOT IN(17,18,19)','NOT IN(19,20,21)').Replace('mapping_version=19','mapping_version=21').Replace('mapping_version=18','mapping_version=20').Replace('scope_version=2 WHERE','scope_version=4 WHERE').Replace('scope_version=1 AND revoked=0','scope_version=3 AND revoked=0')
$compensate=$compensate.Replace('COMMIT TRANSACTION;',":r `"profile-after.sql`"`nCOMMIT TRANSACTION;")
WriteNew 'compensate.sql' $compensate
$state=Rewrite (ReadOld 'recovery-state.sql')
$state=$state.Replace('@version=17 AND @scopes=0','@version=19 AND @scopes=4 AND @active=0').Replace('@version=18','@version=20').Replace('@version=19 AND @scopes=4 AND @active=0 AND @policies=0 AND @grants=0 THEN N''COMPENSATED''','@version=21 AND @scopes=4 AND @active=0 AND @policies=0 AND @grants=0 THEN N''COMPENSATED''')
WriteNew 'recovery-state.sql' $state
$rows=ReadOld 'preserved-row-hashes.sql'
$rows=$rows.Replace("AND name NOT IN(N'source_protocol_binding',N'execution_source_protocol')",'')
$rows=ReplaceOne $rows "ELSE N'' END;" @'
WHEN @schema=N'ctl' AND @table=N'runtime_identity_scope'
 THEN N' WHERE NOT (workload=N''usuarios'' AND environment_name=N''LOCAL_SHADOW'' AND source_instance=N''LOCAL_V2'' AND tenant_scope=N''LOCAL_V2'' AND mode IN(N''BACKFILL'',N''REPLAY'') AND original_sid IN(SUSER_SID(N''RTR-SVW-002\etl_v2_exec''),SUSER_SID(N''RTR-SVW-002\etl_v2_view'')))'
 ELSE N'' END;
'@
WriteNew 'preserved-row-hashes.sql' ($rows.Replace('-- The one SERVICE mapping changed by the explicit activation/compensation has a separate oracle.','-- Only the SERVICE mapping and the exact four existing Users scopes have separate profile oracles.'))
# Create a separately named controller; no installation or rollback of V024 is allowed here.
$controller=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'Invoke-Bloco60Physical.ps1'),$utf8)
$controller=$controller.Replace('database/proposals/bloco60-local','database/proposals/bloco60-correcao').Replace('target/bloco60-local/physical/','target/bloco60-local/physical-corrective/').Replace("'2026-09-09T00:00:00Z'","'2026-09-10T00:00:00Z'")
$controller=$controller.Replace('function Local([string]$Path){',@'
if(-not $RecoverOnly -and ([DateTimeOffset]::UtcNow -lt [DateTimeOffset]$package.validFrom -or [DateTimeOffset]::UtcNow -ge [DateTimeOffset]$package.validUntil)){throw 'B60R_VALIDITY_REQUIRED'}
function Local([string]$Path){
'@)
$start=$controller.IndexOf("  if(`$before -notmatch 'SCHEMA_SHA256=")
$end=$controller.IndexOf('  # Activation could commit', $start)
if($start -lt 0 -or $end -le $start){throw 'B60R_CONTROLLER_SUFFIX'}
$controller=$controller.Substring(0,$start)+@'
  if($before -notmatch 'SCHEMA_SHA256=cfd68974c4fc7667cde1bf9376250d41d58fccf026f9aae5a03268062ecd8e34'){throw 'B60R_COMPENSATED_V024_CATALOG_DRIFT'}
  Sql 'COLLISION_PREFLIGHT' (Join-Path $proposal 'collision-preflight.sql')|Out-Null
  $priorRows=Sql 'HISTORICAL_ROWS_BEFORE' (Join-Path $proposal 'preserved-row-hashes.sql')
'@+"`n"+$controller.Substring($end)
$controller=$controller.Replace('ONE_SERVICE_MAPPING_SEPARATELY_VALIDATED','ONE_SERVICE_MAPPING_AND_EXACT_FOUR_USERS_SCOPES_SEPARATELY_VALIDATED')
$controller=$controller.Replace("  Compensate`r`n",@'
  Compensate
  $historical=Join-Path $evidence 'HISTORICAL_ROWS_BEFORE.sql.log'
  if(Test-Path -LiteralPath $historical){$recovered=Sql 'RECOVERY_HISTORICAL_ROWS' (Join-Path $proposal 'preserved-row-hashes.sql');Assert-Bloco60PreservedRows ([IO.File]::ReadAllText($historical)) $recovered}
'@+"`n")
# Verify the preserved multiset again after compensation, including failed campaigns.
$controller=$controller.Replace("`$active=`$false;`$passed=`$false","`$priorRows=`$null;`$active=`$false;`$passed=`$false")
$controller=$controller.Replace("if(`$active){try{Compensate}catch", "if(`$active){try{Compensate;`$finalRows=Sql 'RECOVERED_HISTORICAL_ROWS' (Join-Path `$proposal 'preserved-row-hashes.sql');Assert-Bloco60PreservedRows `$priorRows `$finalRows;Record @{id='RECOVERED_HISTORICAL_ROWS_PRESERVED';passed=`$true}}catch")
$controllerPath=Join-Path $PSScriptRoot 'Invoke-Bloco60CorrectivePhysical.ps1'
if(Test-Path $controllerPath){throw 'B60R_CONTROLLER_EXISTS'}
[IO.File]::WriteAllText($controllerPath,$controller,$utf8)
@{prepared=$true;cases=$matrix.Count;sqlExecuted=$false;approved=$false;mappingBefore=19;mappingActive=20;mappingAfter=21}|ConvertTo-Json

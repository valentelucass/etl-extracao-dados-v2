#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ApprovedPackageSha256,
 [switch]$RecoverOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$packagePath=Join-Path $root 'database/proposals/bloco60-seal-restante/package.json'
if((Get-FileHash -LiteralPath $packagePath).Hash.ToLowerInvariant() -cne $ApprovedPackageSha256){throw 'B60_APPROVED_PACKAGE_HASH_REQUIRED'}
$package=Get-Content -LiteralPath $packagePath -Raw|ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
if($package.block -ne 60 -or $package.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $package.validFrom -cne '2026-09-10T00:00:00Z' -or $package.validUntil -cne '2026-09-16T00:00:00Z'){throw 'B60_EXACT_PACKAGE_SCOPE'}
if(-not $RecoverOnly -and ([DateTimeOffset]::UtcNow -lt [DateTimeOffset]$package.validFrom -or [DateTimeOffset]::UtcNow -ge [DateTimeOffset]$package.validUntil)){throw 'B60R_VALIDITY_REQUIRED'}
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60_PACKAGE_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path)
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60_PACKAGE_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 Join-Path $root $Path
}
foreach($entry in $package.files){if((Get-FileHash -LiteralPath (Local $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw ('B60_PACKAGE_DRIFT_'+$entry.path)}}
if([Security.Principal.WindowsIdentity]::GetCurrent().Name -cne 'RTR-SVW-002\suporte' -or
 -not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'B60_REVIEWED_ADMIN_TOKEN_REQUIRED'}
$evidence=Join-Path $root ('target/bloco60-local/physical-seal-final/'+$ApprovedPackageSha256.Substring(0,16))
if(-not $RecoverOnly -and (Test-Path $evidence)){throw 'B60_PRIOR_RESULT_RECONCILE_BEFORE_REPEAT'}
if($RecoverOnly -and -not (Test-Path $evidence)){throw 'B60_EXISTING_CAMPAIGN_REQUIRED'}
[void][IO.Directory]::CreateDirectory($evidence)
$utf8=[Text.UTF8Encoding]::new($false,$true);$ledger=Join-Path $evidence 'ledger.jsonl'
$proposal=Join-Path $root 'database/proposals/bloco60-seal-restante'
$base='C:\ProgramData\EslEtlV2\app-bloco60'
$results=[Collections.Generic.List[object]]::new();$observedCases=@{};$credentials=@{};$server=$null;$barrier=$null
$jobs=[Collections.Generic.List[object]]::new()
Import-Module "$PSScriptRoot/Bloco60ResumedCorrectionLedger.psm1" -Force
Import-Module "$PSScriptRoot/Bloco60Assertions.psm1" -Force
Add-Type -Path "$PSScriptRoot/Bloco60SealFinalProcess.cs"
Add-Type -Path "$PSScriptRoot/Bloco60AdminProcess.cs"
Import-Module "$PSScriptRoot/Bloco60SealFinalBudget.psm1" -Force
$priorEvents=@()
foreach($path in @('target/bloco60-local/physical-corrective/cc4f84cb37f69724/ledger.jsonl','target/bloco60-local/physical-continuation/0560a96925160b1e/ledger.jsonl')){$priorEvents+=@(Get-Content (Join-Path $root $path)|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$extraEvents=@(Get-Content (Join-Path $root 'target/bloco60-local/physical-remaining/60e96d5d9b27ecf6/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})
$extraDebit=@($extraEvents|Where-Object type -CEQ 'RESERVE')
if($extraDebit.Count -ne 3 -or @($extraDebit|Where-Object kind -CNE 'READBACK').Count -or @($extraEvents|Where-Object type -CEQ 'CLOSE').Count -ne 1){throw 'B60S_EXACT_ADDITIONAL_READONLY_DEBIT'}
$resumedEvents=@(Get-Content (Join-Path $root 'target/bloco60-local/physical-resumed/8117708c8e211c6a/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})
$finalPrior=@();foreach($path in @('target/bloco60-local/physical-resumed-correction/a89c4dfa88c04833/ledger.jsonl','target/execucao-b60-retomada-20260910-0910/partition-diagnostic/ledger.jsonl')){$finalPrior+=@(Get-Content (Join-Path $root $path)|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$finalPrior+=@(Get-Content (Join-Path $root 'target/bloco60-local/physical-final/5683e0d9bc7c66dc/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})
$finalPrior+=@(Get-Content (Join-Path $root 'target/bloco60-local/physical-audit-final/dbaaefbb0ddbafaa/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})
function Ledger([hashtable]$Event){
 $current=@();if(Test-Path -LiteralPath $ledger){$current=@(Get-Content $ledger|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
 Assert-Bloco60SealFinalBudget -Event $Event -Prior (@($priorEvents)+@($extraEvents)+@($resumedEvents)+@($finalPrior)) -Current $current
 Write-Bloco60ResumedCorrectionLedger -Path $ledger -Event $Event|Out-Null
}
function Save([string]$Name,[string]$Text){$path=Join-Path $evidence $Name;if(Test-Path $path){throw 'B60_EVIDENCE_ALREADY_EXISTS'};[IO.File]::WriteAllText($path,$Text,$utf8);$path}
function Record($Value){$results.Add($Value);$name=if($RecoverOnly){'recovery-results.json'}else{'results.json'};[IO.File]::WriteAllText((Join-Path $evidence $name),($results|ConvertTo-Json -Depth 15),$utf8)}
function Observation([string]$Id,[string]$Outcome,[string]$File,[string]$Type='OBSERVE'){
 Ledger @{type=$Type;id=$Id;outcome=$Outcome;evidence=(Get-FileHash -LiteralPath $File).Hash.ToLowerInvariant()}
}
function SqlStart([string]$Id,[string]$File,[string]$Kind='READBACK',[string]$Database='ETL_SISTEMA_V2_SHADOW'){
 if($Database -cnotin @('master','ETL_SISTEMA_V2_SHADOW') -or $Database -ceq 'master' -and $Id -cnotin @('MASTER_TARGET','RECOVERY_MASTER')){throw 'B60_EXACT_SQL_TARGET'}
 Ledger @{type='RESERVE';id=$Id;kind=$Kind}
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command sqlcmd -CommandType Application).Source
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden';$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$proposal
 foreach($arg in @('-S','localhost','-d',$Database,'-E','-N','-f','65001','-l','10','-t','30','-b','-y','0','-w','65535','-i',$File)){$start.ArgumentList.Add($arg)}
 [Bloco60AdminJob]::new($start)
}
function SqlFinish([string]$Id,$Job,[bool]$Mutation){
 try {$result=$Job.Completion.GetAwaiter().GetResult();$file=Save ($Id+'.sql.log') $result.Output
  Observation $Id $(if($result.Code -eq 0 -and -not $result.Limited){'CONFIRMED'}elseif($Mutation){'UNKNOWN'}else{'REFUSED'}) $file
  Record @{id=$Id;layer='SQLCMD_REAL_WINDOWS';exit=$result.Code;limited=$result.Limited;log=[IO.Path]::GetRelativePath($root,$file);passed=($result.Code -eq 0 -and -not $result.Limited)}
  if($result.Code -ne 0 -or $result.Limited){throw ('B60_SQL_UNCONFIRMED_'+$Id)}
  return $result.Output
 } finally {$Job.Dispose()}
}
function Sql([string]$Id,[string]$File,[string]$Kind='READBACK',[string]$Database='ETL_SISTEMA_V2_SHADOW'){
 SqlFinish $Id (SqlStart $Id $File $Kind $Database) ($Kind -cin @('SQL','COMPENSATE'))
}
function Readback($Case){
 $request=Get-Content (Join-Path $proposal $Case.request) -Raw|ConvertFrom-Json
 $execution=([guid]$request.executionId).ToString();$invocation=([guid]$request.invocationId).ToString()
 $body=@"
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52970,N'B60_READBACK_TARGET',1;
DECLARE @e UNIQUEIDENTIFIER='$execution',@i UNIQUEIDENTIFIER='$invocation';
SELECT (SELECT COUNT_BIG(*) FROM ctl.execution_attempt WHERE execution_id=@e) attempts,
(SELECT current_state FROM ctl.execution_attempt WHERE execution_id=@e) state,
(SELECT LOWER(CONVERT(NVARCHAR(36),replay_of_execution_id)) FROM ctl.execution_attempt WHERE execution_id=@e) replayOf,
(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id=@i) decisions,
(SELECT decision FROM ctl.runtime_authorization_decision WHERE invocation_id=@i) decision,
(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id=@i) consumptions,
(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id=@e) publications,
(SELECT COUNT_BIG(*) FROM ctl.runtime_contract_evidence WHERE execution_id=@e) seals,
(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit WHERE execution_id=@e) pages,
(SELECT COALESCE(SUM(physical_rows),0) FROM ctl.execution_page_audit WHERE execution_id=@e) auditedRows,
(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=@e) stageRows,
COALESCE((SELECT candidate_rows FROM ctl.execution_promotion_result WHERE execution_id=@e),0) candidateRows,
(SELECT COUNT_BIG(*) FROM recon.execution_candidate_application WHERE execution_id=@e) applicationRows,
(SELECT evaluation_state FROM recon.execution_data_quality_evaluation WHERE execution_id=@e) quality,
COALESCE((SELECT completed_checks FROM recon.execution_data_quality_evaluation WHERE execution_id=@e),0) completedChecks,
COALESCE((SELECT passed_checks FROM recon.execution_data_quality_evaluation WHERE execution_id=@e),0) passedChecks,
COALESCE((SELECT failed_checks FROM recon.execution_data_quality_evaluation WHERE execution_id=@e),0) failedChecks,
COALESCE((SELECT inserted_rows FROM recon.usuario_reconciliation_result WHERE execution_id=@e),0) typedInserted,
COALESCE((SELECT updated_rows FROM recon.usuario_reconciliation_result WHERE execution_id=@e),0) typedUpdated,
COALESCE((SELECT noop_rows FROM recon.usuario_reconciliation_result WHERE execution_id=@e),0) typedNoop,
(SELECT COUNT_BIG(*) FROM core.usuario_history WHERE execution_id=@e) typedHistory,
LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',(SELECT * FROM recon.usuario_reconciliation_result WHERE execution_id=@e FOR JSON PATH,INCLUDE_NULL_VALUES)),2)) typedReceiptHash,
LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',(SELECT * FROM core.usuario_history WHERE execution_id=@e ORDER BY usuario_history_id FOR JSON PATH,INCLUDE_NULL_VALUES)),2)) historyHash,
LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',(SELECT u.* FROM core.usuario u JOIN recon.usuario_candidate_application a ON a.usuario_id=u.usuario_id WHERE a.execution_id=@e ORDER BY u.usuario_id FOR JSON PATH,INCLUDE_NULL_VALUES)),2)) currentHash
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES;
"@
 $sql=Save ($Case.id+'-readback.sql') $body
 $text=Sql ($Case.id+'_READBACK') $sql
 $json=($text -split "`r?`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''
 if(-not $json){throw 'B60_SQL_JSON_READBACK_REQUIRED'}
 return ($json|ConvertFrom-Json -AsHashtable)
}
function ProtectBase {
 if(-not (Test-Path $base)){
  [void][IO.Directory]::CreateDirectory($base)
  $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
  $admin=[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544');$acl.SetOwner($admin)
  foreach($sid in @('S-1-5-32-544','S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
  foreach($account in @('etl_v2_exec','etl_v2_view')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new((Get-LocalUser $account).SID,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow'))}
  Set-Acl -LiteralPath $base -AclObject $acl
  [IO.File]::WriteAllText((Join-Path $base 'ownership.json'),'B60_REVIEWED_LOCAL_PACKAGE',$utf8)
 }
 if((Get-Content (Join-Path $base 'ownership.json') -Raw) -cne 'B60_REVIEWED_LOCAL_PACKAGE'){throw 'B60_PROTECTED_OWNERSHIP_REQUIRED'}
 $allowed=@('S-1-5-32-544','S-1-5-18');$readers=@('etl_v2_exec','etl_v2_view')|ForEach-Object {(Get-LocalUser $_).SID.Value}
 foreach($item in @((Get-Item $base))+@(Get-ChildItem $base -Recurse -Force)){
  if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60_PROTECTED_REPARSE'}
  $acl=Get-Acl -LiteralPath $item.FullName
  if($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -notin $allowed){throw 'B60_PROTECTED_OWNER'}
  foreach($rule in $acl.Access){$sid=$rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
   $writeMask=[Security.AccessControl.FileSystemRights]::Write -bor [Security.AccessControl.FileSystemRights]::Delete -bor [Security.AccessControl.FileSystemRights]::ChangePermissions -bor [Security.AccessControl.FileSystemRights]::TakeOwnership
   if($rule.AccessControlType -ne 'Allow' -or $sid -notin ($allowed+$readers) -or $sid -notin $allowed -and ($rule.FileSystemRights -band $writeMask) -ne 0){throw 'B60_PROTECTED_ACL_EXCESS'}
  }
 }
}
function InstallBundles {
 Ledger @{type='RESERVE';id='INSTALL_ARTIFACTS';kind='INSTALL'}
 ProtectBase
 $installed=@{}
 foreach($variant in $package.variants){
  $destination=Join-Path $base $variant.manifest.Substring(0,16)
  # Existing immutable bundles are verified below; only a new work directory is added.
  [void][IO.Directory]::CreateDirectory($destination)
  $prefix=$variant.path+'/'
  foreach($entry in $package.files|Where-Object {$_.path.StartsWith($prefix,[StringComparison]::Ordinal)}){
   $relative=$entry.path.Substring($prefix.Length);$target=Join-Path $destination $relative
   [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target))
   if(-not (Test-Path -LiteralPath $target)){[IO.File]::Copy((Local $entry.path),$target,$false)}
   if((Get-FileHash $target).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'B60_INSTALLED_FILE_UNCONFIRMED'}
  }
  $work=Join-Path $destination ('work/'+$ApprovedPackageSha256.Substring(0,16));[void][IO.Directory]::CreateDirectory($work)
  foreach($entry in $package.files|Where-Object {$_.path.StartsWith('database/proposals/bloco60-seal-restante/requests/',[StringComparison]::Ordinal)}){
   [IO.File]::Copy((Local $entry.path),(Join-Path $work ([IO.Path]::GetFileName($entry.path))),$false)
  }
  $installed[$variant.variant]=@{directory=$destination;work=$work}
 }
 ProtectBase
 $file=Save 'installed.json' ($installed|ConvertTo-Json -Depth 5);Observation 'INSTALL_ARTIFACTS' 'CONFIRMED' $file
 return $installed
}
function StartJar($Case,[string]$Kind='JVM'){
 if($Case.waitBeforeSeconds -gt 0){if($Case.waitBeforeSeconds -gt 5){throw 'B60_WAIT_CAP'};Start-Sleep -Seconds $Case.waitBeforeSeconds}
 if($server.Failure){throw 'B60_SOURCE_ALREADY_FAILED'}
 Ledger @{type='RESERVE';id=$Case.id;kind=$Kind}
 $artifact=$installed[$(if($Case.artifact -ceq 'standard'){'administered'}else{$Case.artifact})]
 $request=Join-Path $artifact.work ([IO.Path]::GetFileName($Case.request))
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
 $start.UserName=$Case.account;$start.Domain='RTR-SVW-002';$start.Password=$credentials[$Case.account];$start.LoadUserProfile=$true
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden';$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$artifact.directory
 foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH','B60_PHYSICAL_PROBE')){[void]$start.Environment.Remove($key)}}
 $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token;$start.Environment['V2_GRAPHQL_TOKEN']=$server.Token
 foreach($arg in @('-Xmx512m',('-Djava.library.path='+(Join-Path $artifact.directory 'native')))){$start.ArgumentList.Add($arg)}
 $config=Join-Path $artifact.directory 'runtime.properties'
 if($Case.fault -cne 'NONE'){
  $start.Environment['B60_PHYSICAL_PROBE']='ADOPTED_B60_PHYSICAL_PACKAGE'
  foreach($arg in @('-cp',((Join-Path $artifact.directory 'etl-dataexport-v2.jar')+';'+(Join-Path $artifact.directory 'probe')+';'+(Join-Path $artifact.directory 'lib/*')),'br.com.esl.etl.v2.bootstrap.RuntimeUsersPhysicalEntry',$config,$request,$Case.command.Replace('-','_').ToUpperInvariant(),$Case.fault)){$start.ArgumentList.Add($arg)}
 }else{
  $jar=Join-Path $artifact.directory $(if($Case.artifact -ceq 'standard'){'standard/etl-dataexport-v2.jar'}else{'etl-dataexport-v2.jar'})
  foreach($arg in @('-jar',$jar,$Case.command,'--config',$config)){$start.ArgumentList.Add($arg)}
  if($Case.command -cne 'dry-run'){foreach($arg in @('--request',$request)){$start.ArgumentList.Add($arg)}}
 }
 if($Case.cancel){$start.RedirectStandardInput=$true;$start.ArgumentList.Add('--control-stdin')}
 $server.Scenario=$Case.scenario;$server.KeyGroup=$Case.group;$server.DataDelayMilliseconds=$Case.delayMilliseconds;$server.AllowOwnClientDisconnect=$Case.cancel
 $document=Get-Content (Join-Path $proposal $Case.request) -Raw|ConvertFrom-Json -AsHashtable
 if($document.Contains('businessStart')){$server.FixtureDate=$document.businessStart}
 $before=$server.Requests;$first=$server.FirstPages
 $task=if($Case.cancel){[Bloco60Child]::Run($start,$server,$server.DataRequests+1,$null)}else{[Bloco60Child]::Run($start)}
 $jobs.Add($task)
 return @{task=$task;beforeHttp=$before;beforeFirst=$first;case=$Case}
}
function FinishJar($Job){
 $case=$Job.case;$child=$Job.task.GetAwaiter().GetResult();$http=$server.Requests-$Job.beforeHttp
 $log=Save ($case.id+'.java.log') $child.Output
 # JVM exit is not a SQL oracle. Record uncertainty before the independent readback.
 Observation $case.id 'UNKNOWN' $log
 $observed=Readback $case
 $readback=Save ($case.id+'.readback.json') ($observed|ConvertTo-Json -Depth 8)
 Observation $case.id 'CONFIRMED' $readback 'RECONCILE'
 $ok=$false
 try {
  Assert-Bloco60Case $case $observed $child.Code $http $child.Output $child.Limited
  if($server.Failure){throw 'B60_SOURCE_FAILURE'}
  if($case.cancel -and -not $child.ControlSent){throw 'B60_CANCELLATION_NOT_SENT'}
  if($case.unchangedFrom){Assert-Bloco60Unchanged $observedCases[$case.unchangedFrom] $observed}
  if($http -gt 0 -and $server.FirstPages-$Job.beforeFirst -ne 1){throw 'B60_TRAVERSAL_MUST_START_AT_FIRST_PAGE'}
  $ok=$true
 } finally {
  $observedCases[$case.id]=$observed
  Record @{id=$case.id;layer=$case.layer;artifact=$case.artifact;account=$case.account;exit=$child.Code;expectedExit=$case.expectedExit;http=$http;expectedHttp=$case.expectedHttp;processId=$child.ProcessId;readback=$observed;passed=$ok}
 }
}
function Compensate {
 $prefix=if($RecoverOnly){'RECOVERY_'}else{''}
 $stateText=Sql ($prefix+'COMPENSATION_STATE') (Join-Path $proposal 'recovery-state.sql')
 $state=(($stateText -split "`r?`n"|Where-Object {$_.Trim().StartsWith('{')}) -join '')|ConvertFrom-Json
 if($state.status -ceq 'MIXED_STOP'){throw 'B60_COMPENSATION_STATE_DRIFT'}
 if($state.status -ceq 'UNACTIVATED'){
  Sql ($prefix+'PROFILE_UNACTIVATED') (Join-Path $proposal 'profile-before.sql')|Out-Null
  return
 }
 if($state.status -ceq 'COMPENSATED'){
  Sql ($prefix+'PROFILE_ALREADY_RESTORED') (Join-Path $proposal 'profile-after.sql')|Out-Null
  return
 }
 $prior=@(Get-Content $ledger|ForEach-Object {$_|ConvertFrom-Json}|Where-Object {$_.type -ceq 'RESERVE' -and $_.id -ceq 'RESTORE_B60'})
 if($prior.Count){throw 'B60_COMPENSATION_ALREADY_SUBMITTED_RECONCILE'}
 Sql 'RESTORE_B60' (Join-Path $proposal 'compensate.sql') 'COMPENSATE'|Out-Null
 Sql ($prefix+'PROFILE_RESTORED') (Join-Path $proposal 'profile-after.sql')|Out-Null
}
$priorRows=$null;$active=$false;$passed=$false
try {
 if($RecoverOnly){
  Sql 'RECOVERY_MASTER' (Join-Path $proposal 'master-target.sql') 'READBACK' 'master'|Out-Null
  Sql 'RECOVERY_STATE' (Join-Path $proposal 'recovery-state.sql')|Out-Null
  Compensate
  $historical=Join-Path $evidence 'HISTORICAL_ROWS_BEFORE.sql.log'
  if(Test-Path -LiteralPath $historical){$recovered=Sql 'RECOVERY_HISTORICAL_ROWS' (Join-Path $proposal 'preserved-row-hashes.sql');Assert-Bloco60PreservedRows ([IO.File]::ReadAllText($historical)) $recovered}
  $passed=$true
  Record @{id='RECOVERY_ONLY';passed=$true;qualification=$false}
 }else{
  Ledger @{type='OPEN';package=$ApprovedPackageSha256;authorizedWindow='USER_RESUME_20260910_1208';priorSqlcmd=121;priorJvms=60;priorHttp=55;automaticRenewals=0}
  Sql 'MASTER_TARGET' (Join-Path $proposal 'master-target.sql') 'READBACK' 'master'|Out-Null
  $before=Sql 'PREFLIGHT' (Join-Path $proposal 'preflight.sql')
  if($before -notmatch 'SCHEMA_SHA256=cfd68974c4fc7667cde1bf9376250d41d58fccf026f9aae5a03268062ecd8e34'){throw 'B60R_COMPENSATED_V024_CATALOG_DRIFT'}
  Sql 'COLLISION_PREFLIGHT' (Join-Path $root 'database/proposals/bloco60-seal-restante/collision-preflight.sql')|Out-Null
  $priorRows=Sql 'HISTORICAL_ROWS_BEFORE' (Join-Path $proposal 'preserved-row-hashes.sql')
  # Activation could commit even if the sqlcmd result is lost; compensation is then mandatory.
  $active=$true;Sql 'ACTIVATE_B60' (Join-Path $proposal 'activate.sql') 'SQL'|Out-Null
  Sql 'PROFILE_ACTIVE' (Join-Path $proposal 'profile-active.sql')|Out-Null
  $installed=InstallBundles
  foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
  $server=[Bloco60Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'),62160)
  $matrix=Get-Content (Join-Path $proposal 'matrix.json') -Raw|ConvertFrom-Json -AsHashtable
  foreach($case in $matrix){
   if($case.pair){continue}
   FinishJar (StartJar $case)
  }
  # The pair is performed immediately after its sealed seed at the end of the matrix.
  $pair=@($matrix|Where-Object pair -CEQ 'B60_RECOVERY_PAIR')
  if($pair.Count -ne 2){throw 'B60_EXACT_PAIR_REQUIRED'}
  $barrier=SqlStart 'CONCURRENT_BARRIER' (Join-Path $proposal 'concurrent-barrier.sql') 'ROLLBACK'
  if(-not $barrier.Ready.Wait([TimeSpan]::FromSeconds(10))){throw 'B60_BARRIER_NOT_READY'}
  $first=StartJar $pair[0] 'JVM_PAIR';$second=StartJar $pair[1] 'JVM_PAIR'
  $ownPids=@();$wait=0
  while($ownPids.Count -ne 2 -and $wait -lt 100){$ownPids=@([Bloco60Child]::ProcessIds);if($ownPids.Count -ne 2){Start-Sleep -Milliseconds 20};$wait++}
  if($ownPids.Count -ne 2){throw 'B60_TWO_OWN_PROCESS_IDS_REQUIRED'}
  $observe=':setvar B60FirstPid "'+([int]$ownPids[0])+'"'+"`n"+':setvar B60SecondPid "'+([int]$ownPids[1])+'"'+"`n"+':setvar B60BarrierPid "'+([int]$barrier.ProcessId)+'"'+"`n"+':r "concurrent-observe.sql"'
  Sql 'CONCURRENCY_OBSERVED' (Save 'concurrent-observed.sql' $observe)|Out-Null
  SqlFinish 'CONCURRENT_BARRIER' $barrier $false|Out-Null;$barrier=$null
  FinishJar $first;FinishJar $second
  if(@($results|Where-Object {$_.id -cin @('CONCURRENT_A','CONCURRENT_B') -and $_.exit -eq 0}).Count -lt 1){throw 'B60_CONCURRENT_NO_WINNER'}
  Sql 'ADVERSARIAL_RECOVERY' (Join-Path $proposal 'adversarial.sql')|Out-Null
  Sql 'CAMPAIGN_DURABLE_TOTALS' (Join-Path $proposal 'recovery-readback.sql')|Out-Null
  $afterRows=Sql 'HISTORICAL_ROWS_AFTER' (Join-Path $proposal 'preserved-row-hashes.sql')
  Assert-Bloco60PreservedRows $priorRows $afterRows
  Record @{id='HISTORICAL_ROWS_PRESERVED';passed=$true;exception='ONE_SERVICE_MAPPING_AND_EXACT_FOUR_USERS_SCOPES_SEPARATELY_VALIDATED'}
  $passed=$true
 }
}catch{
 $reason=$_.Exception.Message;if($reason -cnotmatch '^[A-Za-z0-9_-]{1,180}$'){$reason='B60_CONTROLLER_FAILURE_REDACTED'}
 Record @{id='CAMPAIGN_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;passed=$false}
}finally{
 [Bloco60Child]::StopOwned()
 foreach($job in $jobs){try{$null=$job.GetAwaiter().GetResult()}catch{$passed=$false;Record @{id='OWN_JVM_COMPLETION_UNKNOWN';passed=$false}}}
 if($null -ne $barrier){$barrier.Dispose()}
 if($null -ne $server){Record @{id='HTTP_TOTAL';requests=$server.Requests;nodes=$server.Nodes;requestBytes=$server.RequestBytes;responseBytes=$server.ResponseBytes;failure=$server.Failure;expectedDisconnects=$server.ExpectedDisconnects;passed=($server.Requests -le 345 -and -not $server.Failure)};$server.Dispose()}
 foreach($credential in $credentials.Values){$credential.Dispose()}
 if($active){try{Compensate;$finalRows=Sql 'RECOVERED_HISTORICAL_ROWS' (Join-Path $proposal 'preserved-row-hashes.sql');Assert-Bloco60PreservedRows $priorRows $finalRows;Record @{id='RECOVERED_HISTORICAL_ROWS_PRESERVED';passed=$true}}catch{$passed=$false;Record @{id='COMPENSATION_UNCONFIRMED';passed=$false}}}
 if(-not $RecoverOnly){Ledger @{type='CLOSE';passed=$passed;qualification=$(if($passed){'PHYSICAL_MATRIX_PASSED_REVIEW_REQUIRED'}else{'NOT_QUALIFIED'})}}
 $resultName=if($RecoverOnly){'recovery-result.json'}else{'controller-result.json'}
 Save $resultName (@{passed=$passed;package=$ApprovedPackageSha256;recoverOnly=[bool]$RecoverOnly;refunded=0}|ConvertTo-Json)|Out-Null
}
if(-not $passed){exit 1}

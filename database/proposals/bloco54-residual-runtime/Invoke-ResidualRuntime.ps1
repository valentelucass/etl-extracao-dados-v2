#Requires -Version 7.0
param([Parameter(Mandatory)][string]$ExpectedBundleSha256,[Parameter(Mandatory)][string]$ExpectedPreparationSha256,[Parameter(Mandatory)][string]$OwnerApproval)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$support=Join-Path $root 'scripts/validation'
$evidence=Join-Path $root 'target/bloco54/residual-runtime'
$package=Join-Path $root 'database/proposals/bloco54-observability'
$encoding=[Text.UTF8Encoding]::new($false,$true)
$results=[Collections.Generic.List[object]]::new()
$credentials=@{};$server=$null;$active=$false;$replayDirty=$false
$bundleHash=$ExpectedBundleSha256
function Record($Record){
    $results.Add($Record)
    [IO.File]::WriteAllText((Join-Path $evidence 'results.json'),($results|ConvertTo-Json -Depth 7),$encoding)
}
function Sql([string]$Name,[string]$Text){
    $file=Join-Path $evidence ($Name+'.sql')
    [IO.File]::WriteAllText($file,$Text,$encoding)
    Push-Location $package
    try {& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.sql.log'));$code=$LASTEXITCODE}
    finally {Pop-Location}
    if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
    return [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.sql.log')))
}
function State([string]$Name){
    $material=@'
SET NOCOUNT ON;
DECLARE @material NVARCHAR(MAX)=CONCAT(
 (SELECT principal_kind,mapping_version,valid_from_utc,valid_until_utc,revoked,observer,executor,replay,force_run,audit_reference FROM ctl.runtime_identity_mapping ORDER BY principal_kind FOR JSON PATH),N'|',
 (SELECT s.scope_id,m.principal_kind,s.environment_name,s.source_instance,s.tenant_scope,s.workload,s.mode,s.scope_version,s.policy_fingerprint,s.revoked FROM ctl.runtime_identity_scope s JOIN ctl.runtime_identity_mapping m ON m.original_sid=s.original_sid ORDER BY s.scope_id FOR JSON PATH));
SELECT @material; SELECT N'STATE_SHA256='+LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@material),2));
'@
    $out=Sql $Name $material
    $found=[regex]::Matches($out,'STATE_SHA256=([a-f0-9]{64})')
    if($found.Count -ne 1){throw 'EXACT_MAPPING_STATE_REQUIRED'}
    return $found[0].Groups[1].Value
}
function New-Request([string]$Template,[string]$Mode='BACKFILL'){
    $quality=@($review.Manifest.quality|Where-Object {$_.template -ceq $Template -and $_.mode -ceq $Mode})
    if($quality.Count -ne 1){throw 'EXACT_QUALITY_REQUIRED'}
    [ordered]@{invocationId=[guid]::NewGuid().ToString();executionId=[guid]::NewGuid().ToString();cycleId=[guid]::NewGuid().ToString();template=$Template;mode=$Mode;start='2024-01-01T03:00:00Z';endExclusive='2024-01-02T03:00:00Z';replayOf='';idempotencyKey=[guid]::NewGuid().ToString();businessStart='2024-01-01';businessEnd='2024-01-01';leaseSeconds='30';pageSize='2';maximumPages='4';maximumRows='16';maximumDistinctRoots='16';qualityVersion=$quality[0].version;qualityFingerprint=$quality[0].fingerprint;compatibilityVersion='bloco54-compatible-v1'}
}
function Save-Request([string]$Name,$Document){
    $text=$Document|ConvertTo-Json -Depth 6
    $file=Join-Path $work ($Name+'.json')
    [IO.File]::WriteAllText($file,$text,$encoding)
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.request.json')),$text,$encoding)
    return $file
}
function Jar([string]$Name,[string]$Account,[string]$Command,$Document,[int]$Expected,[int]$Http,[switch]$Temporal){
    $units=if($Document.Contains('temporalPolicy') -or $Document.Contains('dependencyRequest')){2}else{1}
    for($unit=1;$unit -le $units;$unit++){Add-Bloco54Reservation ($Name+'-'+$unit) 'OFFICIAL_V4_JAR_WINDOWS_SQL'|Out-Null}
    $file=Save-Request $Name $Document
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UserName=$Account;$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials[$Account]
    $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token
    foreach($arg in @(('-Djava.library.path='+(Join-Path $revision 'native')),'-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),$Command,'--config',$config,$(if($Temporal){'--temporal'}else{'--request'}),$file)){$start.ArgumentList.Add($arg)}
    $before=$server.Requests
    $result=[Bloco54Child]::Run($start).GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$result.Output,$encoding)
    $passed= -not $result.Limited -and $result.Code -eq $Expected -and $server.Requests-$before -eq $Http -and -not $server.Failure
    $observed=$null
    if(-not $Temporal){
        $inv=[guid]::Parse($Document.invocationId).ToString();$exec=[guid]::Parse($Document.executionId).ToString()
        $query="SET NOCOUNT ON; SELECT (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id='$inv') decisions,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id='$inv') consumptions,(SELECT COUNT_BIG(*) FROM ctl.execution_attempt WHERE execution_id='$exec') attempts,(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id='$exec') publications,(SELECT current_state FROM ctl.execution_attempt WHERE execution_id='$exec') state FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
        $readback=Sql ($Name+'-observe') $query
        $observed=($readback -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join '' | ConvertFrom-Json
        if($Expected -eq 0 -and $Command -ne 'status' -and $observed.publications -ne 1){$passed=$false}
        if($Expected -eq 0 -and ($observed.decisions -ne 1 -or $observed.consumptions -ne 1)){$passed=$false}
        if($Expected -eq 40 -and $observed.publications -ne 0){$passed=$false}
        if($Expected -eq 20 -and $observed.consumptions -ne 0){$passed=$false}
        if($Expected -eq 2 -and ($observed.decisions -ne 0 -or $observed.consumptions -ne 0 -or $observed.attempts -ne 0)){$passed=$false}
    }
    Record ([ordered]@{id=$Name;layer='OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK';account=$Account;expectedExit=$Expected;observedExit=$result.Code;http=$server.Requests-$before;limited=$result.Limited;observed=$observed;passed=$passed})
    if($result.Limited -or $server.Failure){throw 'OWNED_CHILD_OR_SOURCE_LIMIT'}
    return $passed
}
try {
    if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
    if($OwnerApproval -cne 'OWNER_APPROVED_B54_SEVENTH_EXISTING_BALANCE'){throw 'EXPLICIT_SEVENTH_OWNER_APPROVAL_REQUIRED'}
    if(Test-Path -LiteralPath $evidence){throw 'SEVENTH_EVIDENCE_ALREADY_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($evidence)
    $preparation=Join-Path $PSScriptRoot 'manifest.json'
    if((Get-FileHash $preparation).Hash.ToLowerInvariant() -cne $ExpectedPreparationSha256){throw 'PREPARATION_HASH_CHANGED'}
    foreach($entry in (Get-Content $preparation -Raw|ConvertFrom-Json).files){
        if($entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or $entry.path.StartsWith('/')){throw 'PREPARATION_PATH_REJECTED'}
        if((Get-FileHash (Join-Path $root $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'PREPARATION_FILE_CHANGED'}
    }
    Import-Module (Join-Path $support 'Bloco54Artifact.psm1') -Force
    $review=Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle-v4') $bundleHash
    $revision=Install-Bloco54Revision $review
    $work=Join-Path $revision 'seventh-continuation'
    if(Test-Path -LiteralPath $work){throw 'OWNED_CONTINUATION_ALREADY_EXISTS'}
    [void][IO.Directory]::CreateDirectory($work)
    foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
    Add-Type -Path (Join-Path $support 'Bloco54Process.cs')
    Import-Module (Join-Path $PSScriptRoot 'budget-reviewed.psm1') -Force
    $prior=Join-Path $root 'target/bloco54/resume-approved-supplemental'
    $receipt=Get-Content (Join-Path $prior 'manual-shared-configuration.json') -Raw|ConvertFrom-Json
    $port=[int]$receipt.port
    $config=Join-Path $work 'runtime.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText((Join-Path $revision 'runtime.properties')).Replace('http://127.0.0.1:1',('http://127.0.0.1:'+$port))),$encoding)
    if((Get-FileHash $config).Hash.ToLowerInvariant() -cne $receipt.configurationSha256){throw 'EXISTING_RUNTIME_CONFIGURATION_CHANGED'}
    $coletas=Get-Content (Join-Path $prior 'FINAL_RUN_COLETAS.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $fretes=Get-Content (Join-Path $prior 'FINAL_RUN_FRETES_DAG.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    # This preparation JVM only emits JSON. It uses neither SQL nor the loopback source.
    $blueprint=Save-Request 'SEVENTH_TIME_BLUEPRINT' (New-Request 'COLETAS')
    $preview=[Diagnostics.ProcessStartInfo]::new()
    $preview.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $preview.UseShellExecute=$false;$preview.CreateNoWindow=$true;$preview.WindowStyle='Hidden'
    $preview.RedirectStandardOutput=$true;$preview.RedirectStandardError=$true;$preview.WorkingDirectory=$revision
    foreach($key in @($preview.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$preview.Environment.Remove($key)}}
    foreach($argument in @('-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),'plan','--config',$config,'--temporal',(Join-Path $root 'config/laboratory/bloco54-temporal-coletas.json'),'--request',$blueprint)){$preview.ArgumentList.Add($argument)}
    $generated=[Bloco54Child]::Run($preview).GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence 'temporal-offline-export.log'),$generated.Output,$encoding)
    if($generated.Limited -or $generated.Code -ne 0){throw 'OFFLINE_TEMPORAL_EXPORT_FAILED'}
    $windows=@($generated.Output|ConvertFrom-Json -AsHashtable -DateKind String)
    if($windows.Count -ne 3){throw 'EXACT_THREE_GENERATED_WINDOWS_REQUIRED'}
    $catalog=(Get-Content (Join-Path $root 'target/bloco54/preflight-catalog.sql') -Raw).Replace('SELECT CONVERT(XML,@shape);','')
    $before=Sql 'schema-before' $catalog
    if(-not $before.Contains('SCHEMA_SHA256=6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a') -or -not $before.Contains('DATA_ROWS=5328')){throw 'SIXTH_RECOVERED_CHECKPOINT_DRIFT'}
    Sql 'profile-before' (Get-Content (Join-Path $package 'verify.sql') -Raw)|Out-Null
    Sql 'versions-before' "SET NOCOUNT ON; IF (SELECT mapping_version FROM ctl.runtime_identity_mapping WHERE principal_kind='SERVICE')<>15 OR (SELECT mapping_version FROM ctl.runtime_identity_mapping WHERE principal_kind='OPERATOR')<>1 OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE scope_version<>CASE WHEN scope_id=1 THEN 10 ELSE 1 END) THROW 52515,N'SIXTH_MAPPING_CHECKPOINT_REQUIRED',1;"|Out-Null
    Sql 'preservation-before' (Get-Content (Join-Path $prior 'final-preservation-v3.sql') -Raw)|Out-Null
    $campaign=Start-Bloco54SeventhCampaign $OwnerApproval
    $active=$true
    Add-Bloco54Reservation 'SEVENTH_SOURCE_START' 'LOOPBACK_CONTROLLER'|Out-Null
    $server=[Bloco54Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory'),$port)
        $mappingExpression=@'
CONCAT(
 (SELECT principal_kind,mapping_version,valid_from_utc,valid_until_utc,revoked,observer,executor,replay,force_run,audit_reference FROM ctl.runtime_identity_mapping ORDER BY principal_kind FOR JSON PATH),N'|',
 (SELECT s.scope_id,m.principal_kind,s.environment_name,s.source_instance,s.tenant_scope,s.workload,s.mode,s.scope_version,s.policy_fingerprint,s.revoked FROM ctl.runtime_identity_scope s JOIN ctl.runtime_identity_mapping m ON m.original_sid=s.original_sid ORDER BY s.scope_id FOR JSON PATH))
'@
        $replayBefore=State 'replay-before'
        $until='2026-10-07T22:34:30.615'
        $short=[DateTimeOffset]::Parse($campaign.deadline).AddSeconds(-20).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
        if([datetime]::ParseExact($short,"yyyy-MM-dd'T'HH:mm:ss.fff",[Globalization.CultureInfo]::InvariantCulture,([Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal)) -le [datetime]::UtcNow.AddMinutes(2)){throw 'REPLAY_CAMPAIGN_TIME_INSUFFICIENT'}
        $compensation=@'
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52515,N'LOCAL_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
DECLARE @actual NVARCHAR(MAX)={MATERIAL};
IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@actual),2))<>'{EXPECTED}' THROW 52515,N'REPLAY_COMPENSATION_STATE_CHANGED',1;
UPDATE ctl.runtime_identity_scope SET revoked=1,scope_version=scope_version+1 WHERE original_sid=(SELECT original_sid FROM ctl.runtime_identity_mapping WHERE principal_kind='SERVICE') AND environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' AND workload IN(N'coletas',N'fretes') AND mode=N'REPLAY' AND revoked=0;
IF @@ROWCOUNT<>2 THROW 52515,N'EXACT_TWO_REPLAY_SCOPES_REQUIRED',1;
UPDATE ctl.runtime_identity_mapping SET replay=0,force_run=0,valid_until_utc=CONVERT(DATETIME2(3),'{UNTIL}'),mapping_version=mapping_version+1 WHERE principal_kind='SERVICE' AND observer=1 AND executor=1 AND revoked=0 AND replay=1 AND force_run=1 AND valid_until_utc=CONVERT(DATETIME2(3),'{SHORT}');
IF @@ROWCOUNT<>1 THROW 52515,N'EXACT_TEMPORARY_SERVICE_MAPPING_REQUIRED',1;
COMMIT TRANSACTION;
'@
        $compensation=$compensation.Replace('{MATERIAL}',$mappingExpression).Replace('{UNTIL}',$until).Replace('{SHORT}',$short)
        [IO.File]::WriteAllText((Join-Path $evidence 'replay-compensation-template.sql'),$compensation,$encoding)
        Add-Bloco54Reservation 'SEVENTH_REPLAY01_TEMPORARY_MAPPING' 'EXACT_SERVICE_ROLES_TWO_SCOPES'|Out-Null
        Add-Bloco54Reservation 'SEVENTH_REPLAY02_COMPENSATION_RESERVED' 'EXACT_MAPPING_COMPENSATION'|Out-Null
        $grant=@'
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52515,N'LOCAL_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
DECLARE @actual NVARCHAR(MAX)={MATERIAL};
IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@actual),2))<>'{EXPECTED}' THROW 52515,N'REPLAY_PRECONDITION_CHANGED',1;
IF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE is_user_process=1 AND login_name IN(CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_view')) THROW 52515,N'RESTRICTED_ACCOUNTS_MUST_BE_IDLE',1;
UPDATE ctl.runtime_identity_mapping SET replay=1,force_run=1,valid_until_utc=CONVERT(DATETIME2(3),'{SHORT}'),mapping_version=mapping_version+1 WHERE principal_kind='SERVICE' AND observer=1 AND executor=1 AND revoked=0 AND replay=0 AND force_run=0 AND valid_until_utc=CONVERT(DATETIME2(3),'{UNTIL}');
IF @@ROWCOUNT<>1 THROW 52515,N'EXACT_ONE_TEMPORARY_MAPPING_REQUIRED',1;
INSERT ctl.runtime_identity_scope(original_sid,environment_name,source_instance,tenant_scope,workload,mode,scope_version,policy_fingerprint,revoked)
 SELECT original_sid,N'LOCAL_SHADOW',N'LOCAL_V2',N'LOCAL_V2',v.workload,N'REPLAY',1,'c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d',0 FROM ctl.runtime_identity_mapping CROSS JOIN(VALUES(N'coletas'),(N'fretes'))v(workload) WHERE principal_kind='SERVICE';
IF @@ROWCOUNT<>2 THROW 52515,N'EXACT_TWO_TEMPORARY_SCOPES_REQUIRED',1;
COMMIT TRANSACTION;
'@
        $grant=$grant.Replace('{MATERIAL}',$mappingExpression).Replace('{EXPECTED}',$replayBefore).Replace('{UNTIL}',$until).Replace('{SHORT}',$short)
        $replayDirty=$true
        Sql 'replay-apply' $grant|Out-Null
        $replayAfter=State 'replay-after'
        $compensation=$compensation.Replace('{EXPECTED}',$replayAfter)
        [IO.File]::WriteAllText((Join-Path $evidence 'replay-compensation-reviewed.sql'),$compensation,$encoding)
        try {
            foreach($entity in @('coletas','fretes')){
                Add-Bloco54Reservation ('SEVENTH_REPLAY_DQ_'+$entity) 'SYNTHETIC_REPLAY_QUALITY_POLICY'|Out-Null
                Sql ('replay-quality-'+$entity) (Get-Content (Join-Path $root ('target/bloco54/seed/'+$entity+'-REPLAY.sql')) -Raw)|Out-Null
            }
            $originIds=([guid]::Parse($coletas.executionId).ToString(),[guid]::Parse($fretes.executionId).ToString())
            $originQuery="SET NOCOUNT ON; DECLARE @material NVARCHAR(MAX)=CONCAT((SELECT * FROM ctl.execution_publication_event WHERE execution_id IN('$($originIds[0])','$($originIds[1])') ORDER BY execution_id FOR JSON PATH),N'|',(SELECT * FROM ctl.incremental_publication_watermark WHERE environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' ORDER BY entity_name FOR JSON PATH)); SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@material),2));"
            $originBefore=(Sql 'replay-original-frontier-before' $originQuery).Trim()
            $replayColetas=New-Request 'COLETAS' 'REPLAY';$replayColetas.replayOf=$coletas.executionId
            [void](Jar 'SEVENTH_REPLAY_COLETAS' 'etl_v2_exec' 'replay' $replayColetas 0 3)
            $replayFretes=New-Request 'FRETES' 'REPLAY';$replayFretes.replayOf=$fretes.executionId
            [void](Jar 'SEVENTH_REPLAY_FRETES' 'etl_v2_exec' 'replay' $replayFretes 0 3)
            $replayColetas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'SEVENTH_REPLAY_REPEAT' 'etl_v2_exec' 'replay' $replayColetas 0 0)
            $originAfter=(Sql 'replay-original-frontier-after' $originQuery).Trim()
            Record ([ordered]@{id='SEVENTH_REPLAY_ORIGIN_FRONTIER_UNCHANGED';passed=$originBefore -ceq $originAfter})
            $force=New-Request 'COLETAS';[void](Jar 'SEVENTH_FORCE_AUTHORIZED' 'etl_v2_exec' 'force-run' $force 0 3)
            $server.Scenario='PARTIAL';$forcePartial=New-Request 'COLETAS';[void](Jar 'SEVENTH_FORCE_PARTIAL_REJECTED' 'etl_v2_exec' 'force-run' $forcePartial 40 3)
            $force.invocationId=[guid]::NewGuid().ToString();[void](Jar 'SEVENTH_OPERATOR_FORCE_DENIED' 'etl_v2_view' 'force-run' $force 20 0)
            Record ([ordered]@{id='REPLAY_SOURCE_REQUESTS';http=$server.Requests;passed=$server.Requests -le 1000})
        } finally {
            if((State 'replay-pre-compensation') -cne $replayAfter){throw 'REPLAY_COMPENSATION_READBACK_CONFLICT'}
            Sql 'replay-compensate' $compensation|Out-Null
            $replayDirty=$false
        }
        Sql 'final-profile-retained-replay' (Get-Content (Join-Path $package 'verify-retained-replay.sql') -Raw)|Out-Null
        Record ([ordered]@{id='REPLAY_RIGHTS_REMOVED_SCOPES_RETAINED';mappingState=State 'replay-final-state';passed=$true})
    $server.Scenario='NORMAL'
    $server.Scenario='DRIFT'
    $drift=New-Request 'FRETES'
    [void](Jar 'SEVENTH_DRIFT_ALERT' 'etl_v2_exec' 'run' $drift 40 2)
    $driftExecution=[guid]::Parse($drift.executionId).ToString()
    $alerts=Sql 'drift-alert-independent' ("SET NOCOUNT ON; DECLARE @correlation CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256','$driftExecution'),2)); SELECT COUNT_BIG(*) alerts FROM recon.observability_alert WHERE correlation_reference=@correlation FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;")
    $alertCounts=($alerts -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
    Record ([ordered]@{id='SEVENTH_DRIFT_ALERT_SQL';alerts=$alertCounts.alerts;passed=$alertCounts.alerts -eq 1})
    $server.Scenario='NORMAL'
    foreach($ordinal in @(2,1,1)){
        $window=$windows[$ordinal-1]
        $window.invocationId=[guid]::NewGuid().ToString()
        $server.FixtureDate=$window.businessStart
        $name=if($ordinal -eq 2){'SEVENTH_TIME_SECOND_FIRST'}elseif(-not (Test-Path (Join-Path $evidence 'SEVENTH_TIME_FIRST.request.json'))){'SEVENTH_TIME_FIRST'}else{'SEVENTH_TIME_REPEAT'}
        $http=if($name -ceq 'SEVENTH_TIME_REPEAT'){0}else{3}
        [void](Jar $name 'etl_v2_exec' 'run' $window 0 $http)
        $expectedEnd=if($ordinal -eq 2){'2024-02-28T03:00:00Z'}else{'2024-03-01T03:00:00Z'}
        Record ([ordered]@{id=$name+'_CONTIGUOUS';expected=$expectedEnd;passed=([IO.File]::ReadAllText((Join-Path $evidence ($name+'.log')))).Contains('TEMPORAL_EXECUTION_RECONCILIATION contiguous='+$expectedEnd)})
        Sql ($name+'-temporal-readback') "SET NOCOUNT ON; SELECT w.partition_start_utc,w.partition_end_exclusive_utc,CASE WHEN p.execution_id IS NOT NULL THEN 'PUBLISHED' WHEN a.execution_id IS NULL THEN 'NOT_STARTED' ELSE a.current_state END state FROM ctl.runtime_temporal_window w LEFT JOIN ctl.execution_attempt a ON a.execution_id=w.execution_id LEFT JOIN ctl.execution_publication_event p ON p.execution_id=w.execution_id WHERE w.policy_version=N'bloco54-temporal-v1' AND w.namespace_fingerprint=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(NVARCHAR(200),N'LOCAL_SHADOW|LOCAL_V2|LOCAL_V2|coletas|BACKFILL')),2)) ORDER BY w.partition_start_utc FOR JSON PATH;"|Out-Null
    }
    $goodConfig=$config;$config=Join-Path $work 'invalid-tls.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText($goodConfig).Replace('trustServerCertificate=false','trustServerCertificate=true')),$encoding)
    try {[void](Jar 'SEVENTH_TLS_VALIDATION_REQUIRED' 'etl_v2_exec' 'run' (New-Request 'COLETAS') 2 0)}finally{$config=$goodConfig}
    Record ([ordered]@{id='SEVENTH_SOURCE_TOTAL';http=$server.Requests;passed=$server.Requests -le 978})
    $server.Dispose();$server=$null
    Sql 'schema-after' $catalog|Out-Null
    $preservation=Get-Content (Join-Path $prior 'final-preservation-v3.sql') -Raw
    $oldOwn=[regex]::Match($preservation,'INSERT @own VALUES [^;]+;').Value
    $owned=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($match in [regex]::Matches($oldOwn,"'([a-fA-F0-9-]{36})'")){[void]$owned.Add([guid]::Parse($match.Groups[1].Value).ToString())}
    foreach($file in Get-ChildItem -LiteralPath $evidence -Filter '*.request.json' -File){
        $request=Get-Content $file.FullName -Raw|ConvertFrom-Json -DateKind String
        [void]$owned.Add([guid]::Parse($request.executionId).ToString())
    }
    if($owned.Count -gt 32 -or $owned.Count -lt 9){throw 'OWNED_PRESERVATION_SET_LIMIT'}
    $values=(@($owned|Sort-Object|ForEach-Object {"('$_')"}) -join ',')
    $preservation=$preservation.Replace($oldOwn,('INSERT @own VALUES '+$values+';')).Replace(' OR @ownAttempts<>8 OR @ownPublications<>4','')
    $preservation=[regex]::Replace($preservation,"(?m)^IF EXISTS[^\r\n]+PERSIST_ONLY_MUST_NOT_EXTRACT[^\r\n]+\r?\n",'')
    Sql 'final-preservation' $preservation|Out-Null
    Sql 'final-profile-new-connection' (Get-Content (Join-Path $package 'verify-retained-replay.sql') -Raw)|Out-Null
    Record ([ordered]@{id='SEVENTH_COMPLETED';passed=$true})
} catch {
    $reason=[string]$_.Exception.Message
    if($reason -cnotmatch '^[A-Z0-9_a-z-]{1,160}$'){$reason='CONTINUATION_FAILURE_REDACTED'}
    Record ([ordered]@{id='SEVENTH_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;type=$_.Exception.GetType().Name;temporaryMappingUnconfirmed=$replayDirty;passed=$false})
} finally {
    if($null -ne $server){$server.Dispose()}
    foreach($credential in $credentials.Values){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign 'seventh-existing-balance-ended-see-independent-receipts'|Out-Null}
    [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

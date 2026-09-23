#Requires -Version 7.0
param([Parameter(Mandatory)][string]$ExpectedPackageSha256,[Parameter(Mandatory)][string]$ExpectedHarnessSha256,[string]$SupplementalApproval)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root $(if($SupplementalApproval){'target/bloco54/resume-approved-supplemental'}else{'target/bloco54/resume-approved'})
if($SupplementalApproval){
    if($SupplementalApproval -cne 'OWNER_APPROVED_B54_SINGLE_SUPPLEMENTAL'){throw 'EXPLICIT_SUPPLEMENTAL_OWNER_APPROVAL_REQUIRED'}
    if(Test-Path -LiteralPath $evidence){throw 'SUPPLEMENTAL_EVIDENCE_ALREADY_EXISTS'}
    [void][IO.Directory]::CreateDirectory($evidence)
}
$package=Join-Path $root 'database/proposals/bloco54-observability'
$encoding=[Text.UTF8Encoding]::new($false,$true)
$results=[Collections.Generic.List[object]]::new()
$credentials=@{};$server=$null;$active=$false;$replayDirty=$false
$bundleHash='5d2632ab3577ea803eb451ef59ea80c537023c88f7f1f0b7bca471ffc0f2b23f'
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
    $units=if($Temporal){[int]$Document.maximumBacklog}elseif($Document.Contains('dependencyRequest')){2}else{1}
    for($unit=1;$unit -le $units;$unit++){Add-Bloco54Reservation ($Name+'-'+$unit) 'OFFICIAL_FINAL_JAR_WINDOWS_SQL'|Out-Null}
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
        if($Expected -eq 20 -and $observed.consumptions -ne 0){$passed=$false}
    }
    Record ([ordered]@{id=$Name;layer='OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK';account=$Account;expectedExit=$Expected;observedExit=$result.Code;http=$server.Requests-$before;limited=$result.Limited;observed=$observed;passed=$passed})
    if($result.Limited -or $server.Failure){throw 'OWNED_CHILD_OR_SOURCE_LIMIT'}
    return $passed
}
try {
    if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
    $manifestPath=Join-Path $package 'manifest.json'
    if((Get-FileHash $manifestPath).Hash.ToLowerInvariant() -cne $ExpectedPackageSha256){throw 'APPROVED_PACKAGE_BYTES_CHANGED'}
    $manifest=Get-Content $manifestPath -Raw|ConvertFrom-Json -DateKind String
    if($manifest.grantDelta -ne 3 -or $manifest.expectedOriginalGrants -ne 22 -or $manifest.expectedAfterGrants -ne 25){throw 'EXACT_APPROVED_DELTA_REQUIRED'}
    foreach($file in $manifest.files){if((Get-FileHash (Join-Path $package $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'PACKAGE_FILE_CHANGED'}}
    if((Get-FileHash (Join-Path $root 'database/migrations/V020__bind_existing_runtime_occurrences.sql')).Hash.ToLowerInvariant() -cne $manifest.pendingV020Sha256){throw 'PENDING_V020_CHANGED'}
    Import-Module (Join-Path $PSScriptRoot 'Bloco54Artifact.psm1') -Force
    $review=Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle-v3') $bundleHash
    $revision=Install-Bloco54Revision $review
    $work=Join-Path $revision $(if($SupplementalApproval){'approved-continuation-supplemental'}else{'approved-continuation'})
    if(Test-Path -LiteralPath $work){throw 'OWNED_CONTINUATION_ALREADY_EXISTS'}
    [void][IO.Directory]::CreateDirectory($work)
    foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
    Add-Type -Path (Join-Path $PSScriptRoot 'Bloco54Process.cs')
    Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
    $campaign=if($SupplementalApproval){Start-Bloco54SupplementalCampaign $SupplementalApproval}else{Start-Bloco54Campaign 'owner-approved-final-schema-grants-jar-matrix-temporal-manual'}
    $active=$true
    $catalog=(Get-Content (Join-Path $root 'target/bloco54/preflight-catalog.sql') -Raw).Replace('SELECT CONVERT(XML,@shape);','')
    $before=Sql 'schema-before' $catalog
    if(-not $before.Contains('SCHEMA_SHA256=0eca062dbfc89a1f02223cfc33e2b04c85816d610b23806817b607479d389cd0') -or -not $before.Contains('DATA_ROWS=5133')){throw 'PREVIOUS_CHECKPOINT_DRIFT'}
    $install=Get-Content (Join-Path $package 'install-schema.sql') -Raw
    $qualification=$install.Replace('COMMIT TRANSACTION;',$catalog+"`nROLLBACK TRANSACTION;").Replace('V019_V020_SCHEMA_ONLY_COMMITTED_GRANTS_UNCHANGED','PENDING_SCHEMA_QUALIFIED_ROLLBACK_NO_GRANTS')
    $base=Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') -Raw
    $tail=@([regex]::Matches($base,'(?m)^:r "\.\.\\migrations\\(V0(?:19|20)__[A-Za-z_]+\.sql)"\r?$')|ForEach-Object {$_.Groups[1].Value})
    if(($tail -join ',') -cne 'V019__create_scoped_runtime_observability.sql,V020__bind_existing_runtime_occurrences.sql'){throw 'PENDING_BASELINE_TAIL_REQUIRED'}
    $hashes=@()
    foreach($name in @('upgrade','baseline')){
        Add-Bloco54Reservation ('SCHEMA08_'+$name) 'PENDING_DDL_ROLLBACK'|Out-Null
        $text=$qualification
        if($name -eq 'baseline'){
            $refs=($tail|ForEach-Object {':r "..\..\migrations\'+$_+'"'}) -join "`nGO`n"
            $text=[regex]::Replace($text,'(?ms)^:r "\.\.\\\.\.\\migrations\\V019.*?^:r "\.\.\\\.\.\\migrations\\V020[^\r\n]+',$refs)
        }
        $out=Sql ('qualify-'+$name) $text
        $matches=[regex]::Matches($out,'SCHEMA_SHA256=([a-f0-9]{64})')
        if($matches.Count -ne 1 -or -not $out.Contains('DATA_ROWS=5133')){throw 'QUALIFICATION_RECEIPT_INVALID'}
        $hashes+=$matches[0].Groups[1].Value
        $after=Sql ($name+'-rollback-readback') $catalog
        if($after.Trim() -cne $before.Trim()){throw 'QUALIFICATION_ROLLBACK_DRIFT'}
    }
    if($hashes[0] -cne $hashes[1]){throw 'PENDING_BASELINE_UPGRADE_DIFFER'}
    Record ([ordered]@{id='SCHEMA_V019_V020';layer='REAL_SQL_ROLLBACK';catalogSha256=$hashes[0];passed=$true})
    Add-Bloco54Reservation 'SCHEMA07_INSTALL' 'VERSIONED_SCHEMA_COMMIT'|Out-Null
    Sql 'install-pending' $install|Out-Null
    Add-Bloco54Reservation 'GRANT01_APPROVED_DELTA' 'EXACT_THREE_EXECUTE_GRANTS'|Out-Null
    $apply=(Get-Content (Join-Path $package 'apply.sql') -Raw).Replace('$(B54_APPROVAL)','OWNER_APPROVED_B54_OBSERVABILITY_DELTA')
    Sql 'apply-approved-grants' $apply|Out-Null
    Sql 'verify-new-connection' (Get-Content (Join-Path $package 'verify.sql') -Raw)|Out-Null
    Record ([ordered]@{id='EXACT_THREE_GRANTS_APPLIED';layer='REAL_SQL_NEW_CONNECTION';grants=25;passed=$true})
    Add-Bloco54Reservation 'SOURCE04_START' 'LOOPBACK_CONTROLLER'|Out-Null
    $server=[Bloco54Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory'))
    $config=Join-Path $work 'runtime.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText((Join-Path $revision 'runtime.properties')).Replace('http://127.0.0.1:1',('http://127.0.0.1:'+$server.Port))),$encoding)
    $coletas=New-Request 'COLETAS'
    [void](Jar 'FINAL_STATUS_SERVICE_ABSENT' 'etl_v2_exec' 'status' $coletas 10 0)
    $coletas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_STATUS_OPERATOR_ABSENT' 'etl_v2_view' 'status' $coletas 10 0)
    $coletas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_OPERATOR_RUN_DENY' 'etl_v2_view' 'run' $coletas 20 0)
    $coletas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_FORCE_ROLE_DENY' 'etl_v2_exec' 'force-run' $coletas 20 0)
    $coletas.invocationId=[guid]::NewGuid().ToString();$coletasPublished=Jar 'FINAL_RUN_COLETAS' 'etl_v2_exec' 'run' $coletas 0 3
    $coletas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_STATUS_COLETAS' 'etl_v2_view' 'status' $coletas 0 0)
    $coletas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_REPEAT_COLETAS' 'etl_v2_exec' 'run' $coletas 0 0)
    $fretes=New-Request 'FRETES';$fretes.dependencyRequest=$coletas|ConvertTo-Json -Compress
    $fretesPublished=Jar 'FINAL_RUN_FRETES_DAG' 'etl_v2_exec' 'run' $fretes 0 3
    $fretes.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_STATUS_FRETES' 'etl_v2_view' 'status' $fretes 0 0)
    $server.Scenario='PARTIAL';$partial=New-Request 'COLETAS';[void](Jar 'FINAL_PARTIAL' 'etl_v2_exec' 'run' $partial 40 3)
    $server.Scenario='NORMAL';$blocked=New-Request 'FRETES';$blocked.dependencyRequest=$partial|ConvertTo-Json -Compress
    [void](Jar 'FINAL_DAG_BLOCKED_NO_FETCH' 'etl_v2_exec' 'run' $blocked 40 0)
    $server.Scenario='DRIFT';$drift=New-Request 'FRETES';[void](Jar 'FINAL_DRIFT_FAILED' 'etl_v2_exec' 'run' $drift 40 2)
    $server.Scenario='NORMAL'
    foreach($entity in @('coletas','fretes')){
        $temporal=Get-Content (Join-Path $root ('config/laboratory/bloco54-temporal-'+$entity+'.json')) -Raw|ConvertFrom-Json -AsHashtable -DateKind String
        $temporal.invocation=[guid]::NewGuid().ToString()
        [void](Jar ('FINAL_TIME_'+$entity) 'etl_v2_exec' 'run' $temporal 0 0 -Temporal)
        $temporal.invocation=[guid]::NewGuid().ToString()
        [void](Jar ('FINAL_TIME_RESTART_'+$entity) 'etl_v2_exec' 'run' $temporal 0 0 -Temporal)
    }
    $port=$server.Port;$http=$server.Requests;$server.Dispose();$server=$null
    Record ([ordered]@{id='FINAL_SOURCE_STOPPED';http=$http;passed=$http -le 1013})
    $receipt=Join-Path $evidence 'manual-shared-configuration.json'
    [IO.File]::WriteAllText($receipt,([ordered]@{version=1;manifestSha256=$bundleHash;port=$port;configurationSha256=(Get-FileHash $config).Hash.ToLowerInvariant()}|ConvertTo-Json),$encoding)
    $coletas.invocationId=[guid]::NewGuid().ToString();$request=Save-Request 'FINAL_MANUAL_STATUS' $coletas
    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-Bloco54Manual.ps1') -Command status -Bundle $review.Path -ExpectedManifestSha256 $bundleHash -Request $request -ExpectedRequestSha256 (Get-FileHash $request).Hash.ToLowerInvariant() -CaseId FINAL_MANUAL_STATUS -Account etl_v2_view -ConfigurationReceipt $receipt -ExpectedReceiptSha256 (Get-FileHash $receipt).Hash.ToLowerInvariant() *> (Join-Path $evidence 'FINAL_MANUAL_STATUS.log')
    Record ([ordered]@{id='FINAL_MANUAL_STATUS';layer='FRESH_POWERSHELL_RESTRICTED_JAR';exit=$LASTEXITCODE;passed=$LASTEXITCODE -eq 0})
    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'Invoke-Bloco54Manual.ps1') -Command diagnose -Bundle $review.Path -ExpectedManifestSha256 $bundleHash -ObservabilityProfile *> (Join-Path $evidence 'FINAL_MANUAL_DIAGNOSE.log')
    Record ([ordered]@{id='FINAL_MANUAL_DIAGNOSE';layer='READ_ONLY';exit=$LASTEXITCODE;passed=$LASTEXITCODE -eq 0})
    # The SQL consumer deliberately accepts any still-valid matching consumption of this caller.
    # Expire our earlier RUN receipts before proving the absence of a consumed write scope.
    Start-Sleep -Seconds 31
    & (Join-Path $PSScriptRoot 'Invoke-Bloco54AuthorityMatrix.ps1') -ExpectedHarnessSha256 $ExpectedHarnessSha256 -UseActiveCampaign -ObservabilityProfile
    $matrix=Get-Content (Join-Path $root 'target/bloco54/authority-matrix-results.json') -Raw|ConvertFrom-Json
    $matrixPassed=@($matrix|Where-Object {$_.passed -eq $false}).Count -eq 0
    Record ([ordered]@{id='FINAL_AUTHORITY_MATRIX';cases=@($matrix).Count;passed=$matrixPassed})
    $restart=Join-Path $root 'target/bloco54/OPS02-restart-contract.json'
    if(Test-Path -LiteralPath $restart){
        & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'Restore-Bloco54HarnessMapping.ps1') -ExpectedContractSha256 (Get-FileHash $restart).Hash.ToLowerInvariant() -ObservabilityProfile *> (Join-Path $evidence 'FINAL_MAPPING_RESTART.log')
        Record ([ordered]@{id='FINAL_MAPPING_RESTART';exit=$LASTEXITCODE;passed=$LASTEXITCODE -eq 0})
    }
    Sql 'final-profile' (Get-Content (Join-Path $package 'verify.sql') -Raw)|Out-Null
    Record ([ordered]@{id='FINAL_PROFILE_25';mappingState=State 'final-mapping';passed=$true})
    if($matrixPassed -and $coletasPublished -and $fretesPublished){
        $mappingExpression=@'
CONCAT(
 (SELECT principal_kind,mapping_version,valid_from_utc,valid_until_utc,revoked,observer,executor,replay,force_run,audit_reference FROM ctl.runtime_identity_mapping ORDER BY principal_kind FOR JSON PATH),N'|',
 (SELECT s.scope_id,m.principal_kind,s.environment_name,s.source_instance,s.tenant_scope,s.workload,s.mode,s.scope_version,s.policy_fingerprint,s.revoked FROM ctl.runtime_identity_scope s JOIN ctl.runtime_identity_mapping m ON m.original_sid=s.original_sid ORDER BY s.scope_id FOR JSON PATH))
'@
        $replayBefore=State 'replay-before'
        $until='2026-10-07T22:34:30.615'
        $short=[DateTimeOffset]::Parse($campaign.deadline).AddSeconds(-20).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
        if([DateTimeOffset]::Parse($short) -le [DateTimeOffset]::UtcNow.AddMinutes(2)){throw 'REPLAY_CAMPAIGN_TIME_INSUFFICIENT'}
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
        Add-Bloco54Reservation 'REPLAY01_TEMPORARY_MAPPING' 'EXACT_SERVICE_ROLES_TWO_SCOPES'|Out-Null
        Add-Bloco54Reservation 'REPLAY02_COMPENSATION_RESERVED' 'EXACT_MAPPING_COMPENSATION'|Out-Null
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
                Add-Bloco54Reservation ('REPLAY_DQ_'+$entity) 'SYNTHETIC_REPLAY_QUALITY_POLICY'|Out-Null
                Sql ('replay-quality-'+$entity) (Get-Content (Join-Path $root ('target/bloco54/seed/'+$entity+'-REPLAY.sql')) -Raw)|Out-Null
            }
            Add-Bloco54Reservation 'REPLAY_SOURCE_START' 'LOOPBACK_CONTROLLER'|Out-Null
            $server=[Bloco54Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory'),$port)
            $replayColetas=New-Request 'COLETAS' 'REPLAY';$replayColetas.replayOf=$coletas.executionId
            [void](Jar 'FINAL_REPLAY_COLETAS' 'etl_v2_exec' 'replay' $replayColetas 0 3)
            $replayFretes=New-Request 'FRETES' 'REPLAY';$replayFretes.replayOf=$fretes.executionId
            [void](Jar 'FINAL_REPLAY_FRETES' 'etl_v2_exec' 'replay' $replayFretes 0 3)
            $replayColetas.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_REPLAY_REPEAT' 'etl_v2_exec' 'replay' $replayColetas 0 0)
            $force=New-Request 'COLETAS';[void](Jar 'FINAL_FORCE_AUTHORIZED' 'etl_v2_exec' 'force-run' $force 0 3)
            $server.Scenario='PARTIAL';$forcePartial=New-Request 'COLETAS';[void](Jar 'FINAL_FORCE_PARTIAL_REJECTED' 'etl_v2_exec' 'force-run' $forcePartial 40 3)
            $force.invocationId=[guid]::NewGuid().ToString();[void](Jar 'FINAL_OPERATOR_FORCE_DENIED' 'etl_v2_view' 'force-run' $force 20 0)
            Record ([ordered]@{id='REPLAY_SOURCE_REQUESTS';http=$server.Requests;passed=$server.Requests -le 1000})
            $server.Dispose();$server=$null
        } finally {
            if((State 'replay-pre-compensation') -cne $replayAfter){throw 'REPLAY_COMPENSATION_READBACK_CONFLICT'}
            Sql 'replay-compensate' $compensation|Out-Null
            $replayDirty=$false
        }
        Sql 'final-profile-retained-replay' (Get-Content (Join-Path $package 'verify-retained-replay.sql') -Raw)|Out-Null
        Record ([ordered]@{id='REPLAY_RIGHTS_REMOVED_SCOPES_RETAINED';mappingState=State 'replay-final-state';passed=$true})
    }
} catch {
    $reason=[string]$_.Exception.Message
    if($reason -cnotmatch '^[A-Z0-9_a-z-]{1,160}$'){$reason='CONTINUATION_FAILURE_REDACTED'}
    Record ([ordered]@{id='CONTINUATION_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;type=$_.Exception.GetType().Name;temporaryMappingUnconfirmed=$replayDirty;passed=$false})
} finally {
    if($null -ne $server){$server.Dispose()}
    foreach($credential in $credentials.Values){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign 'approved-continuation-ended-see-independent-receipts'|Out-Null}
    [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

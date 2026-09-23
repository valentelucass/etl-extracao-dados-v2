#Requires -Version 7.0
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$support=Join-Path $root 'scripts/validation'
$evidence=Join-Path $root 'target/bloco54/completion-authorized-20260908/temporal-corrected'
$package=Join-Path $root 'database/proposals/bloco54-observability'
$encoding=[Text.UTF8Encoding]::new($false,$true)
$results=[Collections.Generic.List[object]]::new()
$credentials=@{};$server=$null;$active=$false;$replayDirty=$false
$bundleHash='f2fe15f0ddaa3f8f92c07aef54e909df917eb8e3055b652b40af375f99e1a3bd'
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
    $units=1; if($Document.Contains('temporalPolicy')){$units++}; if($Document.Contains('dependencyRequest')){$units++}
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
    $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
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
function Export-Windows([string]$Name,$Policy,$Blueprint){
    $policyFile=Join-Path $work ($Name+'.policy.json')
    [IO.File]::WriteAllText($policyFile,($Policy|ConvertTo-Json -Depth 5),$encoding)
    [IO.File]::Copy($policyFile,(Join-Path $evidence ($Name+'.policy.json')),$false)
    $blueprintFile=Save-Request ($Name+'_BLUEPRINT') $Blueprint
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    foreach($arg in @('-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),'plan','--config',$config,'--temporal',$policyFile,'--request',$blueprintFile)){$start.ArgumentList.Add($arg)}
    $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'-export.log')),$result.Output,$encoding)
    if($result.Limited -or $result.Code -ne 0){throw 'OFFLINE_EXPORT_FAILED'}
    return @($result.Output|ConvertFrom-Json -AsHashtable -DateKind String)
}
try {
    if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
    if(Test-Path -LiteralPath $evidence){throw 'COMPLETION_EVIDENCE_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($evidence)
    Import-Module (Join-Path $support 'Bloco54Artifact.psm1') -Force
    Import-Module (Join-Path $support 'Bloco54Budget.psm1') -Force
    $review=Read-Bloco54Bundle (Join-Path $root 'target/bloco54/reviewed-bundle-v4') $bundleHash
    $revision=Install-Bloco54Revision $review
    $work=Join-Path $revision 'completion-temporal-corrected'
    if(Test-Path -LiteralPath $work){throw 'OWNED_WORK_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($work)
    foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
    Add-Type -Path (Join-Path $support 'Bloco54CompletionProcess.cs')
    $port=62128
    $config=Join-Path $work 'runtime.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText((Join-Path $revision 'runtime.properties')).Replace('http://127.0.0.1:1',('http://127.0.0.1:'+$port))),$encoding)
    $receipt=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/manual-shared-configuration.json') -Raw|ConvertFrom-Json
    if((Get-FileHash $config).Hash.ToLowerInvariant() -cne $receipt.configurationSha256){throw 'EXISTING_CONFIGURATION_CHANGED'}
    $catalog=Get-Content (Join-Path $root 'target/bloco54/residual-runtime/schema-after.sql') -Raw
    $before=Sql 'schema-before' $catalog
    if(-not $before.Contains('SCHEMA_SHA256=6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a') -or -not $before.Contains('DATA_ROWS=5548')){throw 'SEVENTH_CHECKPOINT_DRIFT'}
    Sql 'profile-before' (Get-Content (Join-Path $package 'verify-retained-replay.sql') -Raw)|Out-Null
    $policy=Get-Content (Join-Path $root 'config/laboratory/bloco54-temporal-coletas.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $policy.version='bloco54-temporal-leap-v2';$policy.firstUnpublished='2028-02-28';$policy.observedAt='2028-03-02T12:00:00Z'
    $windows=@(Export-Windows 'COMP_TIME_LEAP' $policy (New-Request 'COLETAS'))
    if($windows.Count -ne 3){throw 'EXACT_THREE_WINDOWS_REQUIRED'}
    Start-Bloco54CompletionCampaign '640224c11d049f3d3a977c76afe68dbbf2d7d56aec685c47e60e1319cbd48d8b' 'corrected-versioned-fixture-temporal-jar-six-windows-restart-out-of-order'|Out-Null
    $active=$true
    Add-Bloco54Reservation 'COMP_TIME_SOURCE' 'LOOPBACK_VERSIONED_FIXTURE'|Out-Null
    $server=[Bloco54CompletionLoopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-v2'),$port)
    foreach($ordinal in @(2,1,3,1)){
        $window=$windows[$ordinal-1];$window.invocationId=[guid]::NewGuid().ToString();$server.FixtureDate=$window.businessStart
        $name=if($ordinal -eq 1 -and (Test-Path (Join-Path $evidence 'COMP_TIME_1.request.json'))){'COMP_TIME_REPEAT'}else{'COMP_TIME_'+$ordinal}
        $http=if($name -ceq 'COMP_TIME_REPEAT'){0}else{3}
        [void](Jar $name 'etl_v2_exec' 'run' $window 0 $http)
        $expected=if($ordinal -eq 2){'2028-02-28T03:00:00Z'}elseif($ordinal -eq 1 -and $name -cne 'COMP_TIME_REPEAT'){'2028-03-01T03:00:00Z'}else{'2028-03-02T03:00:00Z'}
        Record ([ordered]@{id=$name+'_CONTIGUOUS';expected=$expected;passed=([IO.File]::ReadAllText((Join-Path $evidence ($name+'.log')))).Contains('TEMPORAL_EXECUTION_RECONCILIATION contiguous='+$expected)})
    }
    $window.invocationId=[guid]::NewGuid().ToString()
    [void](Jar 'COMP_COLETA_OPERATOR_STATUS' 'etl_v2_view' 'status' $window 0 0)
    Record ([ordered]@{id='HTTP_TOTAL';requests=$server.Requests;passed=$server.Requests -eq 9})
    $server.Dispose();$server=$null
    Sql 'schema-after' $catalog|Out-Null
    Sql 'profile-after' (Get-Content (Join-Path $package 'verify-retained-replay.sql') -Raw)|Out-Null
    Record ([ordered]@{id='TEMPORAL_CONTROLLER_COMPLETED';passed=$true})
}catch{
    $reason=[string]$_.Exception.Message
    if($reason -cnotmatch '^[A-Z0-9_a-z-]{1,160}$'){$reason='COMPLETION_FAILURE_REDACTED'}
    Record ([ordered]@{id='TEMPORAL_CONTROLLER_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;passed=$false})
}finally{
    if($null -ne $server){Record ([ordered]@{id='HTTP_TOTAL';requests=$server.Requests;passed=$server.Requests -le 982});$server.Dispose()}
    foreach($credential in $credentials.Values){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign 'corrected-temporal-controller-finished-see-receipts'|Out-Null}
    [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

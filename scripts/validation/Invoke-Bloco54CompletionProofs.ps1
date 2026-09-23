#Requires -Version 7.0
param([Parameter(Mandatory)][ValidateSet('Authority','ArtifactRetest','Runtime','RuntimeTail','TemporalFretes','TemporalCalendar','TemporalLimits','TemporalIncremental','TemporalRecovery','Sinks','SinksTail','AuthoritySink')][string]$Phase,
 [Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,30}$')][string]$ProofId,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedManifestSha256,
 [Parameter(Mandatory)][int]$ExpectedDatabaseRows,
 [ValidateSet('v5','v6','v7')][string]$RevisionLabel='v5',
 [ValidateSet('V020','V021')][string]$SchemaVersion='V020',
 [ValidatePattern('^[a-f0-9]{64}$')][string]$BatchSha256='640224c11d049f3d3a977c76afe68dbbf2d7d56aec685c47e60e1319cbd48d8b')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$support=Join-Path $root 'scripts/validation'
$evidence=Join-Path $root ('target/bloco54/completion-authorized-20260908/'+$ProofId)
$package=Join-Path $root $(if($SchemaVersion -ceq 'V021'){'database/proposals/bloco54-temporal-continuation'}else{'database/proposals/bloco54-observability'})
$profileFile=Join-Path $package $(if($SchemaVersion -ceq 'V021'){'verify.sql'}else{'verify-retained-replay.sql'})
$expectedCatalog=if($SchemaVersion -ceq 'V021'){'7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408'}else{'6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a'}
$encoding=[Text.UTF8Encoding]::new($false,$true)
$results=[Collections.Generic.List[object]]::new()
$credentials=@{};$server=$null;$active=$false;$replayDirty=$false
$bundleHash=$ExpectedManifestSha256
$extraEnvironment=@{};$extraJvm=@();$extraArguments=@();$cancelControl=$false;$killMarker=$null
$lastObservation=$null;$lastChild=$null
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
    if($Temporal){$units=[int]$Document.maximumBacklog}
    for($unit=1;$unit -le $units;$unit++){Add-Bloco54Reservation ($Name+'-'+$unit) ('OFFICIAL_'+$RevisionLabel.ToUpperInvariant()+'_JAR_WINDOWS_SQL')|Out-Null}
    if($Temporal){
        $totals="SET NOCOUNT ON; SELECT (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision) decisions,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption) consumptions FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
        $priorText=Sql ($Name+'-receipts-before') $totals
        $prior=($priorText -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
    }
    $file=Save-Request $Name $Document
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UserName=$Account;$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials[$Account]
    $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token
    foreach($entry in $extraEnvironment.GetEnumerator()){$start.Environment[$entry.Key]=[string]$entry.Value}
    foreach($argument in $extraJvm){$start.ArgumentList.Add($argument)}
    foreach($arg in @(('-Djava.library.path='+(Join-Path $revision 'native')),'-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),$Command,'--config',$config,$(if($Temporal){'--temporal'}else{'--request'}),$file)){$start.ArgumentList.Add($arg)}
    foreach($argument in $extraArguments){$start.ArgumentList.Add($argument)}
    if($cancelControl){$start.RedirectStandardInput=$true;$start.ArgumentList.Add('--control-stdin')}
    $before=$server.Requests
    $result=if($cancelControl){[Bloco54CompletionChild]::Run($start,$server,$server.DataRequests+1,$null).GetAwaiter().GetResult()}elseif($killMarker){[Bloco54CompletionChild]::Run($start,$null,0,$killMarker).GetAwaiter().GetResult()}else{[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()}
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$result.Output,$encoding)
    $exitMatched=if($Expected -eq -999){$result.KilledByController -and $result.Code -ne 0}else{$result.Code -eq $Expected}
    $passed= -not $result.Limited -and $exitMatched -and $server.Requests-$before -eq $Http -and -not $server.Failure
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
    if($Temporal){
        $afterText=Sql ($Name+'-receipts-after') $totals
        $after=($afterText -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
        $observed=[pscustomobject]@{decisions=$after.decisions-$prior.decisions;consumptions=$after.consumptions-$prior.consumptions}
        if($Expected -eq 0 -and ($observed.decisions -lt 1 -or $observed.decisions -ne $observed.consumptions -or $observed.decisions -gt $units)){$passed=$false}
        if($Expected -eq 2 -and ($observed.decisions -ne 0 -or $observed.consumptions -ne 0)){$passed=$false}
    }
    Record ([ordered]@{id=$Name;layer='OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK';artifactManifestSha256=$bundleHash;account=$Account;expectedExit=$Expected;observedExit=$result.Code;http=$server.Requests-$before;limited=$result.Limited;controllerKilled=$result.KilledByController;cancelSent=$result.ControlSent;observed=$observed;passed=$passed})
    if($result.Limited -or $server.Failure){throw 'OWNED_CHILD_OR_SOURCE_LIMIT'}
    $script:lastObservation=$observed;$script:lastChild=$result
    return $passed
}
function Export-Windows([string]$Name,$Policy,$Blueprint,[int]$Ordinal=0){
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
    if($Ordinal -gt 0){$start.ArgumentList.Add('--window');$start.ArgumentList.Add([string]$Ordinal)}
    $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'-export.log')),$result.Output,$encoding)
    if($result.Limited -or $result.Code -ne 0){throw 'OFFLINE_EXPORT_FAILED'}
    return @($result.Output|ConvertFrom-Json -AsHashtable -DateKind String)
}
function Policy([string]$Name,[string]$First,[string]$Observed,[int]$Maximum=1){
    $policy=Get-Content (Join-Path $root 'config/laboratory/bloco54-temporal-coletas.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $policy.version='bloco54-'+$Name.ToLowerInvariant()+'-v6'
    $policy.invocation=[guid]::NewGuid().ToString();$policy.firstUnpublished=$First;$policy.observedAt=$Observed
    $policy.maximumBacklog=[string]$Maximum;$policy.maximumReconciliation='64'
    return $policy
}
function Temporal-Observe([string]$Name,[array]$Requests,[int]$Published,[int]$Hours=0){
    if($Requests.Count -lt 1 -or $Requests.Count -gt 4){throw 'TEMPORAL_OBSERVATION_BOUND'}
    $values=@(foreach($r in $Requests){
        $id=([guid]$r.executionId).ToString()
        $start=([DateTimeOffset]::Parse($r.start)).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
        $end=([DateTimeOffset]::Parse($r.endExclusive)).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
        "('$id','$start','$end')"
    }) -join ','
    $query="SET NOCOUNT ON; DECLARE @expected TABLE(id UNIQUEIDENTIFIER,start_utc DATETIME2(3),end_utc DATETIME2(3)); INSERT @expected VALUES $values; SELECT (SELECT COUNT_BIG(*) FROM @expected e JOIN ctl.runtime_temporal_window w ON w.execution_id=e.id AND w.partition_start_utc=e.start_utc AND w.partition_end_exclusive_utc=e.end_utc) exactWindows,(SELECT COUNT_BIG(*) FROM @expected e JOIN ctl.execution_publication_event p ON p.execution_id=e.id) publications,(SELECT SUM(DATEDIFF(HOUR,w.partition_start_utc,w.partition_end_exclusive_utc)) FROM @expected e JOIN ctl.runtime_temporal_window w ON w.execution_id=e.id) hours,(SELECT COUNT(DISTINCT w.namespace_fingerprint) FROM @expected e JOIN ctl.runtime_temporal_window w ON w.execution_id=e.id) namespaces FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
    $out=Sql ($Name+'-period') $query
    $observation=($out -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
    Record ([ordered]@{id=$Name+'_SQL_PERIOD';layer='INDEPENDENT_SQL_AGGREGATE';observed=$observation;passed=$observation.exactWindows -eq $Requests.Count -and $observation.publications -eq $Published -and $observation.namespaces -eq 1 -and ($Hours -eq 0 -or $observation.hours -eq $Hours)})
}
function Contiguous([string]$Name,[string]$End,[bool]$Limited=$false){
    Record ([ordered]@{id=$Name+'_CONTIGUOUS';layer='OFFICIAL_JAR_RECONCILIATION';passed=$lastChild.Output.Contains('contiguous='+$End+' ') -and $lastChild.Output.Contains('limit='+$Limited.ToString().ToLowerInvariant())})
}
function Temporal-Fretes {
    $policy=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/temporal-corrected/COMP_TIME_LEAP.policy.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $policy.workload='fretes';$policy.dependsOn='coletas';$policy.invocation=[guid]::NewGuid().ToString()
    $windows=@{}
    foreach($ordinal in 1..3){
        $coleta=Get-Content (Join-Path $root ('target/bloco54/completion-authorized-20260908/temporal-corrected/COMP_TIME_'+$ordinal+'.request.json')) -Raw|ConvertFrom-Json -AsHashtable -DateKind String
        $blueprint=New-Request 'FRETES';$blueprint.dependencyRequest=$coleta|ConvertTo-Json -Depth 6 -Compress
        $export=@(Export-Windows ($ProofId+'_FRETE_EXPORT_'+$ordinal) $policy $blueprint $ordinal)
        if($export.Count -ne 1){throw 'SELECTED_WINDOW_REQUIRED'}
        $windows[$ordinal]=$export[0]
    }
    [void](Jar ($ProofId+'_PERSIST') 'etl_v2_exec' 'run' $policy 0 0 -Temporal)
    Temporal-Observe ($ProofId+'_PERSIST') @($windows[1],$windows[2],$windows[3]) 0 72
    foreach($ordinal in @(2,1,3)){
        $r=$windows[$ordinal];$server.FixtureDate=$r.businessStart
        [void](Jar ($ProofId+'_FRETE_'+$ordinal) 'etl_v2_exec' 'run' $r 0 3)
        $expectedEnd=switch($ordinal){2{'2028-02-28T03:00:00Z'}1{'2028-03-01T03:00:00Z'}3{'2028-03-02T03:00:00Z'}}
        Contiguous ($ProofId+'_FRETE_'+$ordinal) $expectedEnd
    }
    $policy.invocation=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_PERSIST_RESTART') 'etl_v2_exec' 'run' $policy 0 0 -Temporal)
    $r=$windows[2];$r.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_REPEAT') 'etl_v2_exec' 'run' $r 0 0)
    $r.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_OPERATOR_STATUS') 'etl_v2_view' 'status' $r 0 0)
    Temporal-Observe ($ProofId+'_FINAL') @($windows[1],$windows[2],$windows[3]) 3 72
}
function Calendar-Case([string]$Name,$Policy,[int]$Count,[int]$Hours,[switch]$PersistOnly){
    $windows=@(Export-Windows ($ProofId+'_'+$Name) $Policy (New-Request 'COLETAS' $Policy.mode))
    if($windows.Count -ne $Count){throw 'OFFLINE_WINDOW_COUNT_MISMATCH'}
    [void](Jar ($ProofId+'_'+$Name+'_PERSIST') 'etl_v2_exec' 'run' $Policy 0 0 -Temporal)
    if(-not $PersistOnly){
        for($i=0;$i -lt $windows.Count;$i++){
            $server.FixtureDate=$windows[$i].businessStart
            [void](Jar ($ProofId+'_'+$Name+'_RUN_'+($i+1)) 'etl_v2_exec' 'run' $windows[$i] 0 3)
        }
    }
    Temporal-Observe ($ProofId+'_'+$Name) $windows $(if($PersistOnly){0}else{$Count}) $Hours
    return ,$windows
}
function Preview-Flags([string]$Name,$Policy,[bool]$Backlog,[bool]$Blackout){
    $file=Save-Request ($Name+'_PREVIEW_POLICY') $Policy
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    foreach($arg in @('-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),'plan','--config',$config,'--temporal',$file)){$start.ArgumentList.Add($arg)}
    $beforeHttp=$server.Requests
    $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence ($Name+'-preview.log')),$result.Output,$encoding)
    Record ([ordered]@{id=$Name+'_PREVIEW';layer='OFFICIAL_JAR_OFFLINE';exit=$result.Code;http=$server.Requests-$beforeHttp;passed=$result.Code -eq 0 -and -not $result.Limited -and $server.Requests -eq $beforeHttp -and $result.Output.Contains('effects=0') -and $result.Output.Contains('backlog='+$Backlog.ToString().ToLowerInvariant()) -and $result.Output.Contains('blackout='+$Blackout.ToString().ToLowerInvariant())})
}
function Temporal-Calendar {
    $year=Policy 'year' '2024-12-31' '2025-01-02T12:00:00Z' 2
    [void](Calendar-Case 'YEAR' $year 2 48)
    $short=Policy 'short-day' '2018-11-03' '2018-11-04T12:00:00Z';$short.civilBoundary='01:00'
    [void](Calendar-Case 'SHORT_DAY' $short 1 23)
    $long=Policy 'long-day' '2019-02-16' '2019-02-17T12:00:00Z'
    [void](Calendar-Case 'LONG_DAY' $long 1 25)
    $month=Policy 'civil-month' '2024-02-01' '2024-03-01T12:00:00Z';$month.cadence='CIVIL_MONTH'
    [void](Calendar-Case 'PREVIOUS_MONTH' $month 1 696)
    foreach($case in @(@('GAP','2018-11-04','2018-11-05T12:00:00Z','00:00'),@('OVERLAP','2019-02-16','2019-02-17T12:00:00Z','23:30'))){
        $invalid=Policy $case[0] $case[1] $case[2];$invalid.civilBoundary=$case[3]
        [void](Jar ($ProofId+'_'+$case[0]) 'etl_v2_exec' 'run' $invalid 2 0 -Temporal)
        $version=$invalid.version
        $count=Sql ($ProofId+'_'+$case[0]+'-absence') "SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WHERE policy_version=N'$version') THROW 52754,N'INVALID_POLICY_PERSISTED',1; SELECT N'INVALID_POLICY_ZERO_SQL_EFFECT';"
        Record ([ordered]@{id=$case[0]+'_ZERO_SQL';passed=$count.Contains('INVALID_POLICY_ZERO_SQL_EFFECT')})
    }
}
function Temporal-Limits {
    $blackout=Policy 'blackout' '2024-04-01' '2024-04-05T12:00:00Z';$blackout.blackouts='2024-04-02/2024-04-03'
    Preview-Flags ($ProofId+'_BLACKOUT') $blackout $true $true
    [void](Calendar-Case 'BLACKOUT' $blackout 1 24)
    $backlog=Policy 'backlog' '2024-06-01' '2024-06-10T12:00:00Z' 2
    Preview-Flags ($ProofId+'_BACKLOG') $backlog $true $false
    [void](Calendar-Case 'BACKLOG' $backlog 2 48)
    $limited=Policy 'reconciliation-limit' '2024-08-01' '2024-08-03T12:00:00Z' 2
    $limited.maximumReconciliation='1';$limited.maximumDegraded='1'
    [void](Calendar-Case 'RECONCILIATION_LIMIT' $limited 2 48)
    Contiguous ($ProofId+'_RECONCILIATION_LIMIT') '2024-08-02T03:00:00Z' $true
    $degraded=Policy 'degradation-limit' '2024-09-01' '2024-09-02T12:00:00Z';$degraded.maximumDegraded='0'
    $windows=@(Export-Windows ($ProofId+'_DEGRADED') $degraded (New-Request 'COLETAS'))
    [void](Jar ($ProofId+'_DEGRADED_PERSIST') 'etl_v2_exec' 'run' $degraded 0 0 -Temporal)
    $server.Scenario='PARTIAL';$server.FixtureDate=$windows[0].businessStart
    try{[void](Jar ($ProofId+'_DEGRADED_RUN') 'etl_v2_exec' 'run' $windows[0] 30 3)}finally{$server.Scenario='NORMAL'}
    Record ([ordered]@{id='DEGRADED_LIMIT_STOPPED';configuredMaximum=0;observedFailed=1;observedExit=$lastChild.Code;passed=$lastObservation.state -ceq 'FAILED' -and $lastObservation.publications -eq 0 -and $lastChild.Code -eq 30})
    Temporal-Observe ($ProofId+'_DEGRADED') $windows 0 24
    $stale=Policy 'stale-lease' '2025-05-01' '2025-05-02T12:00:00Z'
    $windows=@(Export-Windows ($ProofId+'_STALE') $stale (New-Request 'COLETAS'))
    [void](Jar ($ProofId+'_STALE_PERSIST') 'etl_v2_exec' 'run' $stale 0 0 -Temporal)
    $windows[0].leaseSeconds='1';$server.DataDelayMilliseconds=2000;$server.FixtureDate=$windows[0].businessStart
    try{[void](Jar ($ProofId+'_STALE_RUN') 'etl_v2_exec' 'run' $windows[0] 40 2)}finally{$server.DataDelayMilliseconds=0}
    Contiguous ($ProofId+'_STALE_RUN') '2025-05-01T03:00:00Z'
    $windows[0].invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_STALE_RESTART_NO_TAKEOVER') 'etl_v2_exec' 'run' $windows[0] 40 0)
    Record ([ordered]@{id='STALE_DURABLE_LEASE_REFUSAL';passed=$lastObservation.state -ceq 'EXTRACTING' -and $lastObservation.publications -eq 0})
    Temporal-Observe ($ProofId+'_STALE') $windows 0 24
}
function Temporal-Recovery {
    $fretes=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V6_FRETES_01/V6_FRETES_01_PERSIST_RESTART.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $fretes.invocation=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_FRETES_PLAN_RESTART') 'etl_v2_exec' 'run' $fretes 0 0 -Temporal)
    $month=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V6_CALENDAR_01/V6_CALENDAR_01_PREVIOUS_MONTH.policy.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $month.invocation=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_MONTH_PLAN_RESTART') 'etl_v2_exec' 'run' $month 0 0 -Temporal)
    $request=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V6_CALENDAR_01/V6_CALENDAR_01_PREVIOUS_MONTH_RUN_1.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $request.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_MONTH_RUN_RESTART') 'etl_v2_exec' 'run' $request 0 0)
    Contiguous ($ProofId+'_MONTH_RUN_RESTART') '2024-03-01T03:00:00Z'
    Temporal-Observe ($ProofId+'_MONTH') @($request) 1 696
    $month.version='bloco54-unapproved-existing-plan-v7';$month.invocation=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_CHANGED_PERSISTED_POLICY') 'etl_v2_exec' 'run' $month 30 0 -Temporal)
    $out=Sql 'changed-policy-absence' "SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM ctl.runtime_temporal_policy WHERE policy_version=N'bloco54-unapproved-existing-plan-v7') THROW 52754,N'CHANGED_POLICY_PERSISTED',1; SELECT N'CHANGED_PLAN_REFUSED_ZERO_PLAN_MUTATION';"
    Record ([ordered]@{id='CHANGED_PLAN_DURABLE_REFUSAL';passed=$out.Contains('CHANGED_PLAN_REFUSED_ZERO_PLAN_MUTATION')})
    [void](Jar ($ProofId+'_FINAL_OPERATOR_RUN_REFUSED') 'etl_v2_view' 'run' (New-Request 'COLETAS') 20 0)
}
function Temporal-Incremental {
    $policy=Policy 'incremental-overlap' '2024-10-01' '2024-10-03T12:00:00Z' 2;$policy.mode='INCREMENTAL'
    $windows=@(Export-Windows ($ProofId+'_INCREMENTAL') $policy (New-Request 'COLETAS' 'INCREMENTAL'))
    [void](Jar ($ProofId+'_PERSIST') 'etl_v2_exec' 'run' $policy 0 0 -Temporal)
    foreach($i in 0..1){
        $server.FixtureDate=$windows[$i].businessStart
        $previous=[datetime]::ParseExact($server.FixtureDate,'yyyy-MM-dd',[cultureinfo]::InvariantCulture).AddDays(-1).ToString('yyyy-MM-dd')
        $server.ExpectedUpdatedAtFilter=$previous+' 23:00:00 - '+$server.FixtureDate+' 23:59:59'
        $beforeMatches=$server.MatchedUpdatedAtFilters
        [void](Jar ($ProofId+'_RUN_'+($i+1)) 'etl_v2_exec' 'run' $windows[$i] 0 3)
        Record ([ordered]@{id=$ProofId+'_HTTP_LOOKBACK_'+($i+1);layer='LOOPBACK_HTTP_FILTER_ASSERTION';matchedPages=$server.MatchedUpdatedAtFilters-$beforeMatches;passed=$server.MatchedUpdatedAtFilters-$beforeMatches -eq 2})
        $frontier=([DateTimeOffset]::Parse($windows[$i].endExclusive)).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
        $out=Sql ('incremental-frontier-'+$i) "SET NOCOUNT ON; IF (SELECT COUNT_BIG(*) FROM ctl.incremental_publication_watermark WHERE environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' AND entity_name=N'coletas' AND contiguous_partition_end_utc='$frontier')<>1 THROW 52754,N'CONTIGUOUS_INCREMENTAL_FRONTIER_MISMATCH',1; SELECT N'EXACT_CONTIGUOUS_FRONTIER_CONFIRMED';"
        Record ([ordered]@{id=$ProofId+'_SQL_FRONTIER_'+($i+1);passed=$out.Contains('EXACT_CONTIGUOUS_FRONTIER_CONFIRMED')})
    }
    $server.ExpectedUpdatedAtFilter=''
    $windows[0].invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_RESTART_NO_FETCH_NO_RESET') 'etl_v2_exec' 'run' $windows[0] 0 0)
    Temporal-Observe ($ProofId+'_FINAL') $windows 2 48
}
function Read-LiveLog([string]$Path){
    if(-not (Test-Path $Path)){return ''}
    $stream=[IO.File]::Open($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
    try{if($stream.Length -gt 16384){throw 'OWN_LOCK_OUTPUT_LIMIT'};$reader=[IO.StreamReader]::new($stream);try{return $reader.ReadToEnd()}finally{$reader.Dispose()}}finally{$stream.Dispose()}
}
function Sink-Cases([switch]$TailOnly) {
    if(-not $TailOnly){
    $quality=New-Request 'COLETAS';$quality.start='2026-06-01T03:00:00Z';$quality.endExclusive='2026-06-02T03:00:00Z';$quality.businessStart='2026-06-01';$quality.businessEnd='2026-06-01';$server.FixtureDate='2026-06-01'
    if(-not (Jar ($ProofId+'_DQ_VALID_CONTROL') 'etl_v2_exec' 'run' $quality 0 3)){throw 'DQ_POSITIVE_CONTROL_REQUIRED'}
    foreach($key in @('invocationId','executionId','cycleId','idempotencyKey')){$quality[$key]=[guid]::NewGuid().ToString()}
    $quality.qualityVersion='B54_UNAPPROVED_QUALITY';$quality.qualityFingerprint='0'*64
    [void](Jar ($ProofId+'_DQ_POLICY_ABSENT') 'etl_v2_exec' 'run' $quality 40 3)
    Record ([ordered]@{id='DQ_POLICY_REFUSED_WITH_VALID_PARTITION_CONTROL';passed=$lastObservation.publications -eq 0 -and $lastObservation.attempts -eq 1})
    }
    $request=New-Request 'COLETAS';$request.start='2026-06-02T03:00:00Z';$request.endExclusive='2026-06-03T03:00:00Z';$request.businessStart='2026-06-02';$request.businessEnd='2026-06-02';$server.FixtureDate='2026-06-02'
    $execution=([guid]$request.executionId).ToString()
    Add-Bloco54Reservation ($ProofId+'_OWN_SINK_LOCK') 'BOUNDED_OWN_SQL_ALERT_RANGE_LOCK'|Out-Null
    $lockFile=Join-Path $evidence 'own-alert-lock.sql'
    $lockOutput=Join-Path $evidence 'own-alert-lock.log'
    $lockError=Join-Path $evidence 'own-alert-lock-error.log'
    $lockBody=@"
SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 1000;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52754,N'EXACT_TARGET_REQUIRED',1;
DECLARE @correlation CHAR(64)=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARCHAR(36),'$execution')),2));
DECLARE @count BIGINT,@blocked BIT=0,@until DATETIME2(3)=DATEADD(SECOND,25,SYSUTCDATETIME());
BEGIN TRANSACTION;
SELECT @count=COUNT_BIG(*) FROM recon.observability_alert WITH(UPDLOCK,HOLDLOCK,INDEX(PK_recon_observability_alert)) WHERE correlation_reference=@correlation AND alert_sequence=1;
IF @count<>0 THROW 52754,N'OWN_FUTURE_ALERT_ONLY',1;
RAISERROR(N'OWN_ALERT_RANGE_LOCK_HELD',0,1) WITH NOWAIT;
WHILE SYSUTCDATETIME()<@until
BEGIN
 IF EXISTS(SELECT 1 FROM sys.dm_exec_requests r JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id WHERE r.blocking_session_id=@@SPID AND r.wait_type LIKE N'LCK[_]%' AND s.login_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec') SET @blocked=1;
 WAITFOR DELAY '00:00:01';
END;
ROLLBACK TRANSACTION;
IF @blocked<>1 THROW 52754,N'RESTRICTED_SINK_WAIT_NOT_OBSERVED',1;
PRINT N'OWN_ALERT_LOCK_ROLLED_BACK_RESTRICTED_WAIT_OBSERVED';
"@
    [IO.File]::WriteAllText($lockFile,$lockBody,$encoding)
    $sqlcmd=(Get-Command sqlcmd.exe -ErrorAction Stop).Source
    $child=Start-Process $sqlcmd -WindowStyle Hidden -WorkingDirectory $package -ArgumentList @('-S','localhost','-d','ETL_SISTEMA_V2_SHADOW','-E','-N','-l','10','-t','30','-b','-i',('"'+$lockFile+'"')) -RedirectStandardOutput $lockOutput -RedirectStandardError $lockError -PassThru
    try {
        $deadline=[DateTimeOffset]::UtcNow.AddSeconds(8);$held=$false;$poll=0
        while(-not $held -and -not $child.HasExited -and [DateTimeOffset]::UtcNow -lt $deadline){
            $poll++;$ownPid=[int]$child.Id
            $probe=Sql ('own-lock-observe-'+$poll) "SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM sys.dm_tran_locks l JOIN sys.dm_exec_sessions s ON s.session_id=l.request_session_id WHERE s.host_process_id=$ownPid AND s.open_transaction_count=1 AND l.resource_database_id=DB_ID() AND l.request_status=N'GRANT' AND l.request_mode LIKE N'Range%' AND l.resource_associated_entity_id IN(SELECT hobt_id FROM sys.partitions WHERE object_id=OBJECT_ID(N'recon.observability_alert'))) SELECT N'OWN_ALERT_RANGE_LOCK_CONFIRMED_IN_SQL';"
            $held=$probe.Contains('OWN_ALERT_RANGE_LOCK_CONFIRMED_IN_SQL')
            if(-not $held){Start-Sleep -Milliseconds 100}
        }
        if(-not $held){throw 'OWN_ALERT_LOCK_NOT_CONFIRMED'}
        $server.Scenario='DRIFT'
        try{[void](Jar ($ProofId+'_REQUIRED_SINK_TIMEOUT') 'etl_v2_exec' 'run' $request 40 2)}finally{$server.Scenario='NORMAL'}
        if(-not $child.WaitForExit(30000)){throw 'OWN_LOCK_CHILD_TIMEOUT'}
        if($child.ExitCode -ne 0 -or -not (Read-LiveLog $lockOutput).Contains('OWN_ALERT_LOCK_ROLLED_BACK_RESTRICTED_WAIT_OBSERVED')){throw 'SINK_LOCK_RECOVERY_UNCONFIRMED'}
        $out=Sql 'sink-alert-absence' "SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM recon.observability_alert WHERE correlation_reference=LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONVERT(VARCHAR(36),'$execution')),2))) THROW 52754,N'UNEXPECTED_ALERT_AFTER_TIMEOUT',1; SELECT N'FAILED_REQUIRED_SINK_ZERO_ALERTS';"
        Record ([ordered]@{id='PHYSICAL_REQUIRED_SINK_TIMEOUT_REFUSED';layer='OWN_SQL_RANGE_LOCK_REAL_JAR';passed=$lastObservation.state -ceq 'FAILED' -and $lastObservation.publications -eq 0 -and $out.Contains('FAILED_REQUIRED_SINK_ZERO_ALERTS')})
        $request.invocationId=[guid]::NewGuid().ToString()
        [void](Jar ($ProofId+'_FAILED_SINK_RESTART_NO_FETCH') 'etl_v2_exec' 'run' $request 40 0)
    }finally{if(-not $child.HasExited){$child.Kill();$child.WaitForExit(5000)|Out-Null};$child.Dispose()}
}
function Variant([string]$Name,[string]$ResourceMutation){
    $bundle=Join-Path $root ('target/bloco54/negative-'+$ProofId+'-'+$Name)
    if(Test-Path -LiteralPath $bundle){throw 'NEGATIVE_BUNDLE_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($bundle)
    foreach($entry in $review.Manifest.files){
        $destination=Join-Path $bundle $entry.path
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
        [IO.File]::Copy((Join-Path $review.Path $entry.path),$destination,$false)
    }
    $manifest=Get-Content (Join-Path $review.Path 'manifest.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $manifest.laboratoryNegativeCase=$ProofId+'_'+$Name
    if($ResourceMutation){
        Add-Type -AssemblyName System.IO.Compression
        $zip=[IO.Compression.ZipFile]::Open((Join-Path $bundle 'etl-dataexport-v2.jar'),[IO.Compression.ZipArchiveMode]::Update)
        try {
            $resource=if($ResourceMutation -ceq 'POLICY'){'runtime-authority.properties'}else{'runtime-sql-public.cer'}
            $entry=$zip.GetEntry($resource)
            $memory=[IO.MemoryStream]::new();$stream=$entry.Open()
            try{$stream.CopyTo($memory);$bytes=$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}
            $entry.Delete()
            if($ResourceMutation -cne 'MISSING_PIN'){
                if($ResourceMutation -ceq 'POLICY'){
                    $text=$encoding.GetString($bytes)
                    $text=[regex]::Replace($text,'(?m)^policyFingerprint=[a-f0-9]{64}\r?$','policyFingerprint='+('0'*64))
                    $bytes=$encoding.GetBytes($text)
                }elseif($ResourceMutation -ceq 'MISMATCH_PIN'){$bytes[$bytes.Length-1]=$bytes[$bytes.Length-1] -bxor 1}
                else{throw 'NEGATIVE_RESOURCE_ALLOWLIST'}
                $stream=$zip.CreateEntry($resource).Open()
                try{$stream.Write($bytes)}finally{$stream.Dispose()}
            }
        }finally{$zip.Dispose()}
        foreach($entry in $manifest.files){if($entry.path -ceq 'etl-dataexport-v2.jar'){$entry.sha256=(Get-FileHash (Join-Path $bundle $entry.path)).Hash.ToLowerInvariant()}}
    }
    [IO.File]::WriteAllText((Join-Path $bundle 'manifest.json'),($manifest|ConvertTo-Json -Depth 10),$encoding)
    $hash=(Get-FileHash (Join-Path $bundle 'manifest.json')).Hash.ToLowerInvariant()
    $negative=Read-Bloco54Bundle $bundle $hash
    $installed=Install-Bloco54Revision $negative
    Record ([ordered]@{id=$Name+'_ISOLATED_VARIANT';manifestSha256=$hash;mutation=$ResourceMutation;passed=$true})
    return $installed
}
function Authority-Sink {
    $request=New-Request 'COLETAS';$invocation=([guid]$request.invocationId).ToString()
    Add-Bloco54Reservation ($ProofId+'_OWN_DECISION_LOCK') 'BOUNDED_OWN_SQL_AUTHORIZATION_RANGE_LOCK'|Out-Null
    $file=Join-Path $evidence 'own-authorization-lock.sql';$output=Join-Path $evidence 'own-authorization-lock.log';$errorFile=Join-Path $evidence 'own-authorization-lock-error.log'
    $body=@"
SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 1000;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52754,N'EXACT_TARGET_REQUIRED',1;
DECLARE @count BIGINT,@blocked BIT=0,@until DATETIME2(3)=DATEADD(SECOND,25,SYSUTCDATETIME());
BEGIN TRANSACTION;
SELECT @count=COUNT_BIG(*) FROM ctl.runtime_authorization_decision WITH(UPDLOCK,HOLDLOCK,INDEX(PK_runtime_authorization_decision)) WHERE invocation_id='$invocation';
IF @count<>0 THROW 52754,N'OWN_FUTURE_DECISION_ONLY',1;
WHILE SYSUTCDATETIME()<@until
BEGIN
 IF EXISTS(SELECT 1 FROM sys.dm_exec_requests r JOIN sys.dm_exec_sessions s ON s.session_id=r.session_id WHERE r.blocking_session_id=@@SPID AND r.wait_type LIKE N'LCK[_]%' AND s.login_name=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec') SET @blocked=1;
 WAITFOR DELAY '00:00:01';
END;
ROLLBACK TRANSACTION;
IF @blocked<>1 THROW 52754,N'AUTHORIZATION_SINK_WAIT_NOT_OBSERVED',1;
PRINT N'OWN_AUTHORIZATION_LOCK_ROLLED_BACK_RESTRICTED_WAIT_OBSERVED';
"@
    [IO.File]::WriteAllText($file,$body,$encoding)
    $child=Start-Process (Get-Command sqlcmd.exe).Source -WindowStyle Hidden -WorkingDirectory $package -ArgumentList @('-S','localhost','-d','ETL_SISTEMA_V2_SHADOW','-E','-N','-l','10','-t','30','-b','-i',('"'+$file+'"')) -RedirectStandardOutput $output -RedirectStandardError $errorFile -PassThru
    try {
        $deadline=[DateTimeOffset]::UtcNow.AddSeconds(8);$held=$false;$poll=0
        while(-not $held -and -not $child.HasExited -and [DateTimeOffset]::UtcNow -lt $deadline){
            $poll++;$ownPid=[int]$child.Id
            $probe=Sql ('own-authorization-lock-observe-'+$poll) "SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM sys.dm_tran_locks l JOIN sys.dm_exec_sessions s ON s.session_id=l.request_session_id WHERE s.host_process_id=$ownPid AND s.open_transaction_count=1 AND l.resource_database_id=DB_ID() AND l.request_status=N'GRANT' AND l.request_mode LIKE N'Range%' AND l.resource_associated_entity_id IN(SELECT hobt_id FROM sys.partitions WHERE object_id=OBJECT_ID(N'ctl.runtime_authorization_decision'))) SELECT N'OWN_AUTHORIZATION_RANGE_LOCK_CONFIRMED';"
            $held=$probe.Contains('OWN_AUTHORIZATION_RANGE_LOCK_CONFIRMED');if(-not $held){Start-Sleep -Milliseconds 100}
        }
        if(-not $held){throw 'OWN_AUTHORIZATION_LOCK_NOT_CONFIRMED'}
        [void](Jar ($ProofId+'_FAIL_BEFORE_DECISION_COMMIT') 'etl_v2_exec' 'run' $request 20 0)
        if(-not $child.WaitForExit(30000) -or $child.ExitCode -ne 0 -or -not (Read-LiveLog $output).Contains('OWN_AUTHORIZATION_LOCK_ROLLED_BACK_RESTRICTED_WAIT_OBSERVED')){throw 'AUTHORIZATION_LOCK_RECOVERY_UNCONFIRMED'}
        Record ([ordered]@{id='PHYSICAL_AUTHORIZATION_SINK_BEFORE_COMMIT';layer='OWN_SQL_RANGE_LOCK_REAL_JAR';passed=$lastObservation.decisions -eq 0 -and $lastObservation.consumptions -eq 0 -and $lastObservation.attempts -eq 0 -and $lastObservation.publications -eq 0})
    }finally{if(-not $child.HasExited){$child.Kill();$child.WaitForExit(5000)|Out-Null};$child.Dispose()}
}
function Assert-Preservation {
    $text=Get-Content (Join-Path $root 'target/bloco54/residual-runtime/final-preservation.sql') -Raw
    $text=$text.Substring(0,$text.IndexOf('DECLARE @names TABLE'))
    $old=[regex]::Match($text,'INSERT @own VALUES [^;]+;').Value
    $owned=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($match in [regex]::Matches($old,"'([a-fA-F0-9-]{36})'")){[void]$owned.Add(([guid]$match.Groups[1].Value).ToString())}
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'target/bloco54/completion-authorized-20260908') -Recurse -Filter '*.request.json' -File){
        $request=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json -DateKind String
        if($request.PSObject.Properties.Name -contains 'executionId'){[void]$owned.Add(([guid]$request.executionId).ToString())}
    }
    if($owned.Count -gt 256){throw 'OWNED_OCCURRENCE_SET_LIMIT'}
    $values=(@($owned|Sort-Object|ForEach-Object {"('$_')"}) -join ',')
    $text=$text.Replace($old,('INSERT @own VALUES '+$values+';'))
    $text=$text.Replace("SELECT COUNT_BIG(*) FROM ctl.execution_attempt WHERE current_state=N'EXTRACTING'","SELECT COUNT_BIG(*) FROM ctl.execution_attempt a WHERE current_state=N'EXTRACTING' AND NOT EXISTS(SELECT 1 FROM @own o WHERE o.execution_id=a.execution_id)")
    $text+="`nIF EXISTS(SELECT 1 FROM sys.dm_exec_sessions WHERE is_user_process=1 AND login_name IN(CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_view')) THROW 52754,N'OWN_RUNTIME_SESSIONS_REMAIN',1; IF @@TRANCOUNT<>0 THROW 52754,N'OBSERVER_TRANSACTION_REMAINS',1; SELECT 0 restrictedSessions,@@TRANCOUNT observerTransactions;"
    Sql 'preservation-after' $text|Out-Null
}
function Authority-Cases([switch]$ArtifactOnly) {
    $goodRevision=$revision
    if(-not $ArtifactOnly){
    $goodConfig=$config
    $script:config=Join-Path $work 'insecure-workload.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText($goodConfig).Replace('trustServerCertificate=false','trustServerCertificate=true')),$encoding)
    try {[void](Jar ($ProofId+'_TLS') 'etl_v2_exec' 'run' (New-Request 'COLETAS') 2 0)}finally{$script:config=$goodConfig}
    $script:extraEnvironment=@{V2_RUNTIME_ROLE='SERVICE'}
    try {[void](Jar ($ProofId+'_ENV_ROLE') 'etl_v2_view' 'run' (New-Request 'COLETAS') 2 0)}finally{$script:extraEnvironment=@{}}
    $script:extraJvm=@('-Dv2.role=SERVICE')
    try {[void](Jar ($ProofId+'_PROPERTY_ROLE') 'etl_v2_view' 'run' (New-Request 'COLETAS') 2 0)}finally{$script:extraJvm=@()}
    $script:extraArguments=@('--role','SERVICE')
    try {[void](Jar ($ProofId+'_CLI_ROLE') 'etl_v2_view' 'run' (New-Request 'COLETAS') 2 0)}finally{$script:extraArguments=@()}
    $script:extraEnvironment=@{V2_DATAEXPORT_TENANT_SCOPE='UNAPPROVED'}
    try {[void](Jar ($ProofId+'_NAMESPACE') 'etl_v2_exec' 'run' (New-Request 'COLETAS') 2 0)}finally{$script:extraEnvironment=@{}}
    [void](Jar ($ProofId+'_ACTION_MODE') 'etl_v2_exec' 'replay' (New-Request 'COLETAS') 2 0)
    [void](Jar ($ProofId+'_OPERATOR_RUN') 'etl_v2_view' 'run' (New-Request 'COLETAS') 20 0)
    $goodRevision=$revision
    foreach($mutation in @('POLICY','MISSING_PIN','MISMATCH_PIN')){
        $script:revision=Variant $mutation $mutation
        try {[void](Jar ($ProofId+'_'+$mutation) 'etl_v2_exec' 'run' (New-Request 'COLETAS') 20 0)}finally{$script:revision=$goodRevision}
    }
    }
    $mutations=if($ArtifactOnly){@('JAR_HASH','ACL_READ_REMOVED')}else{@('JAR_HASH','DEPENDENCY_HASH','ACL_READ_REMOVED')}
    foreach($mutation in $mutations){
        $script:revision=Variant $mutation ''
        $relative=if($mutation -ceq 'JAR_HASH'){'etl-dataexport-v2.jar'}else{'lib/mssql-jdbc-12.8.1.jre11.jar'}
        $target=Join-Path $revision $relative
        $beforeHash=(Get-FileHash $target).Hash
        $acl=Get-Acl -LiteralPath $target
        $sddl=$acl.Sddl
        [IO.File]::WriteAllText((Join-Path $revision 'negative-original-acl.sddl'),$sddl,$encoding)
        [IO.File]::WriteAllText((Join-Path $evidence ($mutation+'-recovery.json')),(@{relativePath=$relative;sha256=$beforeHash.ToLowerInvariant();sddlSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($encoding.GetBytes($sddl))).ToLowerInvariant()}|ConvertTo-Json),$encoding)
        try {
            if($mutation -ceq 'ACL_READ_REMOVED'){
                $sid=(Get-LocalUser -Name etl_v2_exec).SID
                $deny=[Security.AccessControl.FileSystemAccessRule]::new($sid,[Security.AccessControl.FileSystemRights]::ReadData,[Security.AccessControl.AccessControlType]::Deny)
                $acl.AddAccessRule($deny)
                Set-Acl -LiteralPath $target -AclObject $acl
                $actual=Get-Acl -LiteralPath $target
                if($actual.Sddl -ceq $sddl -or @($actual.Access|Where-Object {$_.AccessControlType -eq 'Deny' -and $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -ceq $sid.Value}).Count -ne 1){throw 'EXACT_READ_DENIAL_NOT_APPLIED'}
            }elseif($mutation -ceq 'JAR_HASH'){
                $zip=[IO.Compression.ZipFile]::Open($target,[IO.Compression.ZipArchiveMode]::Update)
                try{$stream=$zip.CreateEntry('integrity-negative.txt').Open();try{$bytes=$encoding.GetBytes('bounded-administrative-integrity-negative');$stream.Write($bytes)}finally{$stream.Dispose()}}finally{$zip.Dispose()}
            }else{
                $stream=[IO.File]::Open($target,[IO.FileMode]::Append,[IO.FileAccess]::Write,[IO.FileShare]::None)
                try{$stream.WriteByte(0);$stream.Flush($true)}finally{$stream.Dispose()}
            }
            [void](Jar ($ProofId+'_'+$mutation) 'etl_v2_exec' 'run' (New-Request 'COLETAS') 20 0)
        }finally{
            if($mutation -ceq 'ACL_READ_REMOVED'){$restore=[Security.AccessControl.FileSecurity]::new();$restore.SetSecurityDescriptorSddlForm($sddl);Set-Acl -LiteralPath $target -AclObject $restore}
            else{[IO.File]::Copy((Join-Path $review.Path $relative),$target,$true)}
            if((Get-FileHash $target).Hash -cne $beforeHash -or (Get-Acl $target).Sddl -cne $sddl){throw 'NEGATIVE_ARTIFACT_RECOVERY_UNCONFIRMED'}
            $script:revision=$goodRevision
        }
        Record ([ordered]@{id=$mutation+'_EXACT_RECOVERY';passed=$true})
    }
}
function Runtime-Cases([switch]$TailOnly) {
    if(-not $TailOnly){
    $script:killMarker='RUNTIME_OBSERVATION reason=NOT_FOUND'
    $beforeDispatch=New-Request 'COLETAS'
    try {[void](Jar ($ProofId+'_HALT_BEFORE_DISPATCH') 'etl_v2_exec' 'run' $beforeDispatch -999 0)}finally{$script:killMarker=$null}
    Record ([ordered]@{id='HALT_CONSUMED_WITHOUT_ATTEMPT';passed=$lastObservation.consumptions -eq 1 -and $lastObservation.attempts -eq 0 -and $lastChild.KilledByController -and $lastChild.Code -ne 0})
    $beforeDispatch.invocationId=[guid]::NewGuid().ToString()
    $expectedHttp=if($lastObservation.attempts -eq 0){3}else{0}
    [void](Jar ($ProofId+'_RESTART_ORIGINAL') 'etl_v2_exec' 'run' $beforeDispatch 0 $expectedHttp)
    $afterPublish=New-Request 'COLETAS'
    $script:killMarker='RUNTIME_SQL_OBSERVATION state=PUBLISHED'
    try {[void](Jar ($ProofId+'_HALT_AFTER_PUBLICATION') 'etl_v2_exec' 'run' $afterPublish -999 3)}finally{$script:killMarker=$null}
    Record ([ordered]@{id='PUBLISHED_BEFORE_CALLER_COMPLETION';passed=$lastObservation.publications -eq 1 -and $lastChild.KilledByController -and $lastChild.Code -ne 0})
    $afterPublish.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_PUBLISHED_RESTART_NO_FETCH') 'etl_v2_exec' 'run' $afterPublish 0 0)
    }
    $cancel=New-Request 'FRETES'
    $server.DataDelayMilliseconds=2000;$server.AllowOwnClientDisconnect=$true;$script:cancelControl=$true
    try {[void](Jar ($ProofId+'_CANCEL_OWN_RUN') 'etl_v2_exec' 'run' $cancel 50 2)}finally{$server.DataDelayMilliseconds=0;$script:cancelControl=$false}
    Record ([ordered]@{id='CANCEL_DURABLE_TERMINAL';passed=$lastChild.ControlSent -and $lastObservation.state -ceq 'CANCELLED' -and $lastObservation.publications -eq 0})
    $cancel.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_CANCELLED_RESTART_NO_FETCH') 'etl_v2_exec' 'run' $cancel 50 0)
    $lease=New-Request 'COLETAS';$lease.leaseSeconds='1'
    $server.DataDelayMilliseconds=2000
    try {[void](Jar ($ProofId+'_LEASE_EXPIRED') 'etl_v2_exec' 'run' $lease 40 2)}finally{$server.DataDelayMilliseconds=0}
    $lease.invocationId=[guid]::NewGuid().ToString()
    [void](Jar ($ProofId+'_LEASE_NO_TAKEOVER') 'etl_v2_exec' 'run' $lease 40 0)
}
try {
    if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
    if(Test-Path -LiteralPath $evidence){throw 'PROOF_EVIDENCE_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($evidence)
    $testedFiles=@($PSCommandPath,(Join-Path $support 'Bloco54CompletionProcess.cs'),(Join-Path $support 'Bloco54Budget.psm1'),(Join-Path $support 'Bloco54Artifact.psm1'))
    $tested=@(foreach($testedFile in $testedFiles){$name=[IO.Path]::GetFileName($testedFile);[IO.File]::Copy($testedFile,(Join-Path $evidence ('tested-'+$name)),$false);[pscustomobject]@{name=$name;sha256=(Get-FileHash $testedFile).Hash.ToLowerInvariant()}})
    [IO.File]::WriteAllText((Join-Path $evidence 'tested-controller-files.json'),($tested|ConvertTo-Json),$encoding)
    Import-Module (Join-Path $support 'Bloco54Artifact.psm1') -Force
    Import-Module (Join-Path $support 'Bloco54Budget.psm1') -Force
    $review=Read-Bloco54Bundle (Join-Path $root ('target/bloco54/reviewed-bundle-'+$RevisionLabel)) $bundleHash
    $revision=Install-Bloco54Revision $review
    $work=Join-Path $revision $ProofId
    if(Test-Path -LiteralPath $work){throw 'OWNED_WORK_EXISTS_PRESERVE'}
    [void][IO.Directory]::CreateDirectory($work)
    foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
    Add-Type -Path (Join-Path $support 'Bloco54CompletionProcess.cs')
    $config=Join-Path $work 'runtime.properties'
    [IO.File]::WriteAllText($config,([IO.File]::ReadAllText((Join-Path $revision 'runtime.properties')).Replace('http://127.0.0.1:1','http://127.0.0.1:62128')),$encoding)
    $receipt=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/manual-shared-configuration.json') -Raw|ConvertFrom-Json
    if((Get-FileHash $config).Hash.ToLowerInvariant() -cne $receipt.configurationSha256){throw 'EXISTING_CONFIGURATION_CHANGED'}
    $catalog=Get-Content (Join-Path $root 'target/bloco54/residual-runtime/schema-after.sql') -Raw
    $before=Sql 'schema-before' $catalog
    if($before -cnotmatch ('(?m)^SCHEMA_SHA256='+$expectedCatalog+'\s*$') -or $before -cnotmatch ('(?m)^DATA_ROWS='+$ExpectedDatabaseRows+'\s*$')){throw 'EXACT_CHECKPOINT_DRIFT'}
    Sql 'profile-before' (Get-Content $profileFile -Raw)|Out-Null
    $stateBefore=State 'rights-before'
    Start-Bloco54CompletionCampaign $BatchSha256 ($Phase+'-'+$ProofId)|Out-Null
    $active=$true
    Add-Bloco54Reservation ($ProofId+'_SOURCE') 'LOOPBACK_VERSIONED_FIXTURE'|Out-Null
    $previousHttp=42 # Immutable first seven campaigns; later campaigns are recorded individually.
    $directories=@(Get-ChildItem (Join-Path $root 'target/bloco54/completion-authorized-20260908') -Directory)
    if($directories.Count -gt 128){throw 'BOUNDED_CAMPAIGN_EVIDENCE_LIMIT'}
    foreach($directory in $directories){
        $resultFile=Join-Path $directory.FullName 'results.json'
        if((Test-Path $resultFile) -and $directory.FullName -cne $evidence){
            if((Get-Item $resultFile).Length -gt 65536){throw 'BOUNDED_CAMPAIGN_RECEIPT_LIMIT'}
            $http=@(Get-Content $resultFile -Raw|ConvertFrom-Json|Where-Object {$_.id -ceq 'HTTP_TOTAL'})
            if($http.Count){$previousHttp+=[int]$http[-1].requests}
        }
    }
    if($previousHttp+200 -gt 1024){throw 'HTTP_CUMULATIVE_BUDGET_WOULD_BE_EXCEEDED'}
    Record ([ordered]@{id='HTTP_BUDGET_BEFORE';previousRequests=$previousHttp;maximumThisCampaign=200;maximumBlock=1024;passed=$true})
    $server=[Bloco54CompletionLoopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-v2'),62128)
    $smoke=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/FINAL_RUN_COLETAS.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
    $smoke.invocationId=[guid]::NewGuid().ToString()
    if(-not (Jar ($ProofId+'_POSITIVE_STATUS') 'etl_v2_exec' 'status' $smoke 0 0)){throw 'OFFICIAL_PREFLIGHT_FAILED'}
    switch($Phase){
        'Authority' {Authority-Cases}
        'ArtifactRetest' {Authority-Cases -ArtifactOnly}
        'Runtime' {Runtime-Cases}
        'RuntimeTail' {Runtime-Cases -TailOnly}
        'TemporalFretes' {Temporal-Fretes}
        'TemporalCalendar' {Temporal-Calendar}
        'TemporalLimits' {Temporal-Limits}
        'TemporalIncremental' {Temporal-Incremental}
        'TemporalRecovery' {Temporal-Recovery}
        'Sinks' {Sink-Cases}
        'SinksTail' {Sink-Cases -TailOnly}
        'AuthoritySink' {Authority-Sink}
    }
    Record ([ordered]@{id='HTTP_TOTAL';requests=$server.Requests;passed=$server.Requests -le 200})
    $server.Dispose();$server=$null
    Assert-Preservation
    Sql 'schema-after' $catalog|Out-Null
    Sql 'profile-after' (Get-Content $profileFile -Raw)|Out-Null
    if((State 'rights-after') -cne $stateBefore){throw 'RIGHTS_CHANGED'}
    Assert-Bloco54Protected 'C:\ProgramData\EslEtlV2\app-bloco54'
    Record ([ordered]@{id='PROOF_CONTROLLER_COMPLETED';passed=$true})
}catch{
    $cause=$_.Exception
    while($cause.InnerException){$cause=$cause.InnerException}
    $reason=[string]$cause.Message
    if($reason -cnotmatch '^[A-Z0-9_a-z-]{1,160}$'){$reason='PROOF_FAILURE_REDACTED'}
    Record ([ordered]@{id='PROOF_CONTROLLER_FAILED';reason=$reason;exceptionType=$cause.GetType().Name;line=$_.InvocationInfo.ScriptLineNumber;passed=$false})
}finally{
    if($null -ne $server){Record ([ordered]@{id='HTTP_TOTAL';requests=$server.Requests;passed=$server.Requests -le 200});$server.Dispose()}
    foreach($credential in $credentials.Values){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign ('completion-'+$ProofId+'-finished-see-receipts')|Out-Null}
    [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

param([Parameter(Mandatory)][string]$Bundle,[Parameter(Mandatory)][string]$ExpectedManifestSha256)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root 'target/bloco54'
$encoding=[Text.UTF8Encoding]::new($false,$true)
$summary=[Collections.Generic.List[string]]::new()
$credentials=@{}
$server=$null
$active=$false
try {
    $identity=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if(-not $identity.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'ADMINISTRATIVE_LAUNCHER_CONTEXT_REQUIRED'}
    $bundlePath=[IO.Path]::GetFullPath($Bundle)
    if($bundlePath -cne (Join-Path $root 'target/bloco54/reviewed-bundle')){throw 'REVIEWED_BUNDLE_PATH_REQUIRED'}
    $manifestPath=Join-Path $bundlePath 'manifest.json'
    if((Get-FileHash -LiteralPath $manifestPath).Hash.ToLowerInvariant() -cne $ExpectedManifestSha256){throw 'MANIFEST_HASH_MISMATCH'}
    $manifest=Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json -DateKind String
    if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($manifest.validUntil)){throw 'ORIGINAL_VALIDITY_EXPIRED'}
    foreach($file in $manifest.files){
        if($file.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $file.path.Contains('..')){throw 'MANIFEST_PATH_REJECTED'}
        if((Get-FileHash -LiteralPath (Join-Path $bundlePath $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'ARTIFACT_HASH_MISMATCH'}
    }
    $protected='C:\ProgramData\EslEtlV2\app-bloco54'
    $marker=Join-Path $protected 'ownership.json'
    if(Test-Path -LiteralPath $protected){
        if(-not (Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw).Trim() -cne 'BLOCO54_OWNER_AUTHORIZED_LOCAL_LAB_V1'){throw 'FOREIGN_PROTECTED_CONTENT_REFUSED'}
    } else {
        [void][IO.Directory]::CreateDirectory($protected)
        $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
        foreach($sid in @('S-1-5-32-544','S-1-5-18')){
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        }
        foreach($account in @('etl_v2_exec','etl_v2_view')){
            $user=Get-LocalUser -Name $account
            if(-not $user.Enabled -or $null -eq $user.AccountExpires -or $user.AccountExpires.ToUniversalTime() -le [datetime]::UtcNow){throw 'LOCAL_ACCOUNT_INVALID'}
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($user.SID,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow'))
        }
        Set-Acl -LiteralPath $protected -AclObject $acl
        [IO.File]::WriteAllText($marker,'BLOCO54_OWNER_AUTHORIZED_LOCAL_LAB_V1',$encoding)
    }
    $revision=Join-Path $protected $ExpectedManifestSha256.Substring(0,16)
    if(-not (Test-Path -LiteralPath $revision)){
        [void][IO.Directory]::CreateDirectory($revision)
        foreach($file in $manifest.files){
            $destination=Join-Path $revision $file.path
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
            [IO.File]::Copy((Join-Path $bundlePath $file.path),$destination,$false)
        }
        [IO.File]::Copy($manifestPath,(Join-Path $revision 'manifest.json'),$false)
    }
    foreach($file in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $revision $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'PROTECTED_COPY_HASH_MISMATCH'}}
    $badAcl=@((Get-Acl -LiteralPath $protected).Access | Where-Object {
        $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin @('S-1-5-32-544','S-1-5-18') -and
        ($_.FileSystemRights -band [Security.AccessControl.FileSystemRights]::Write) -ne 0
    })
    if($badAcl.Count){throw 'PROTECTED_ACL_WRITE_EXCESS'}
    foreach($name in @('etl_v2_exec','etl_v2_view')){
        $sealed=[IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi")
        $credentials[$name]=ConvertTo-SecureString -String $sealed
        $sealed=$null
    }
    Add-Type -Path (Join-Path $PSScriptRoot 'Bloco54Process.cs')
    Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
    $campaign=Start-Bloco54Campaign 'restricted-jar-loopback-and-authorization'
    $active=$true
    # New attempt after SCHEMA-03 rollback. Read back before DDL; never retry uncertain commits blindly.
    $state=& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -l 10 -t 20 -b -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN OBJECT_ID(N'ctl.fn_runtime_consumed_scope') IS NULL THEN 0 ELSE 1 END;"
    if($LASTEXITCODE -ne 0){throw 'SCHEMA_READBACK_FAILED'}
    if(($state -join '').Trim() -ceq '0'){
        Add-Bloco54Reservation 'SCHEMA-04' 'SQL_ADDITIVE_INSTALL' | Out-Null
        Push-Location (Join-Path $root 'database/validation')
        try {& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i (Join-Path $evidence 'install-v018.sql') *> (Join-Path $evidence 'install-v018-retry.log');if($LASTEXITCODE -ne 0){throw 'SCHEMA_INSTALL_FAILED'}} finally {Pop-Location}
    }
    & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -i (Join-Path $root 'database/validation/055_validate_bloco54_runtime_consumers.sql') *> (Join-Path $evidence 'postflight-v018.log')
    if($LASTEXITCODE -ne 0){throw 'SCHEMA_POSTFLIGHT_FAILED'}
    foreach($entity in @('coletas','fretes')){
        Add-Bloco54Reservation ('DQ-SEED-'+$entity) 'SQL_SYNTHETIC_POLICY' | Out-Null
        & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -i (Join-Path $evidence ('seed/'+$entity+'-BACKFILL.sql')) *> (Join-Path $evidence ('seed-'+$entity+'.log'))
        if($LASTEXITCODE -ne 0){throw 'DQ_SEED_UNCONFIRMED'}
    }
    $campaignDirectory=Join-Path $revision ('campaign-'+$campaign.campaign)
    [void][IO.Directory]::CreateDirectory($campaignDirectory)
    $server=[Bloco54Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory'))
    $config=Get-Content (Join-Path $revision 'runtime.properties') -Raw
    $config=$config.Replace('http://127.0.0.1:1',('http://127.0.0.1:'+$server.Port))
    $configPath=Join-Path $campaignDirectory 'runtime.properties'
    [IO.File]::WriteAllText($configPath,$config,$encoding)
    function Invoke-Restricted([string]$Id,[string]$Account,[string]$Command,[string]$Request,[int]$Expected) {
        Add-Bloco54Reservation $Id 'OFFICIAL_JAR_WINDOWS_SQL_HTTP' | Out-Null
        if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($campaign.deadline)){throw 'CAMPAIGN_DEADLINE'}
        $start=[Diagnostics.ProcessStartInfo]::new()
        $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
        $start.UserName=$Account;$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials[$Account]
        $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
        $start.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
        $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
        foreach($key in @($start.Environment.Keys)) {if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
        $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token
        foreach($argument in @(('-Djava.library.path='+(Join-Path $revision 'native')),'-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),$Command,'--config',$configPath,'--request',$Request)){$start.ArgumentList.Add($argument)}
        $before=$server.Requests
        $result=[Bloco54Child]::Run($start).GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $evidence ($Id+'.log')),$result.Output,$encoding)
        $summary.Add($Id+' account='+$Account+' expected='+$Expected+' observed='+$result.Code+' http='+($server.Requests-$before))
        if($result.Limited -or $result.Code -ne $Expected -or $server.Failure){throw ('CASE_FAILED_'+$Id)}
        if($Expected -eq 20 -and $server.Requests -ne $before){throw 'DENIAL_FETCHED_SOURCE'}
    }
    function New-Request([string]$Template,[string]$Mode='BACKFILL') {
        $quality=$manifest.quality | Where-Object { $_.template -eq $Template -and $_.mode -eq $Mode }
        if(@($quality).Count -ne 1){throw 'EXACT_QUALITY_REFERENCE_REQUIRED'}
        [ordered]@{invocationId=[guid]::NewGuid().ToString();executionId=[guid]::NewGuid().ToString();cycleId=[guid]::NewGuid().ToString();template=$Template;mode=$Mode;
            start='2024-01-01T03:00:00Z';endExclusive='2024-01-02T03:00:00Z';replayOf='';idempotencyKey=[guid]::NewGuid().ToString();businessStart='2024-01-01';businessEnd='2024-01-01';
            leaseSeconds='30';pageSize='2';maximumPages='4';maximumRows='16';maximumDistinctRoots='16';qualityVersion=$quality.version;qualityFingerprint=$quality.fingerprint;compatibilityVersion='bloco54-compatible-v1'}
    }
    function Save-Request([string]$Id,$Document){
        $file=Join-Path $campaignDirectory ($Id+'.json')
        [IO.File]::WriteAllText($file,($Document | ConvertTo-Json),$encoding)
        [IO.File]::WriteAllText((Join-Path $evidence ($Id+'.request.json')),($Document | ConvertTo-Json),$encoding)
        return $file
    }
    $coletas=New-Request 'COLETAS'
    Invoke-Restricted 'AUTH01_STATUS_SERVICE' 'etl_v2_exec' 'status' (Save-Request 'AUTH01_STATUS_SERVICE' $coletas) 10
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'AUTH02_STATUS_OPERATOR' 'etl_v2_view' 'status' (Save-Request 'AUTH02_STATUS_OPERATOR' $coletas) 10
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'AUTH03_RUN_OPERATOR' 'etl_v2_view' 'run' (Save-Request 'AUTH03_RUN_OPERATOR' $coletas) 20
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'AUTH03_FORCE_SERVICE' 'etl_v2_exec' 'force-run' (Save-Request 'AUTH03_FORCE_SERVICE' $coletas) 20
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'RUN01_COLETAS' 'etl_v2_exec' 'run' (Save-Request 'RUN01_COLETAS' $coletas) 0
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'RUN03_COLETAS_STATUS' 'etl_v2_view' 'status' (Save-Request 'RUN03_COLETAS_STATUS' $coletas) 0
    $coletas.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'RUN05_COLETAS_REPEAT' 'etl_v2_exec' 'run' (Save-Request 'RUN05_COLETAS_REPEAT' $coletas) 0
    $fretes=New-Request 'FRETES'
    Invoke-Restricted 'RUN02_FRETES' 'etl_v2_exec' 'run' (Save-Request 'RUN02_FRETES' $fretes) 0
    $fretes.invocationId=[guid]::NewGuid().ToString()
    Invoke-Restricted 'RUN04_FRETES_STATUS' 'etl_v2_view' 'status' (Save-Request 'RUN04_FRETES_STATUS' $fretes) 0
    $server.Scenario='PARTIAL'
    $partial=New-Request 'COLETAS'
    Invoke-Restricted 'RUN09_PARTIAL' 'etl_v2_exec' 'run' (Save-Request 'RUN09_PARTIAL' $partial) 40
    $server.Scenario='DRIFT'
    $drift=New-Request 'FRETES'
    Invoke-Restricted 'RUN15_DRIFT' 'etl_v2_exec' 'run' (Save-Request 'RUN15_DRIFT' $drift) 40
    $summary.Add('CAMPAIGN_CASES_COMPLETED')
} catch {
    $reason=[string]$_.Exception.Message
    if($reason -cnotmatch '^[A-Z0-9_-]{1,140}$'){$reason='CONTROLLER_FAILURE_REDACTED'}
    $summary.Add('FAILURE='+$reason+' line='+$_.InvocationInfo.ScriptLineNumber+' type='+$_.Exception.GetType().Name)
} finally {
    if($null -ne $server){$summary.Add('HTTP_REQUESTS='+$server.Requests);$server.Dispose()}
    foreach($credential in $credentials.Values){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign 'controller-finally-preserved-see-case-results' | Out-Null}
    [IO.File]::WriteAllLines((Join-Path $evidence 'restricted-controller-result.txt'),$summary,$encoding)
}

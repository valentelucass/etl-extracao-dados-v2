#Requires -Version 7.0
param(
 [Parameter(Mandatory)][ValidateSet('diagnose','install','plan','run','status','replay','force-run')][string]$Command,
 [Parameter(Mandatory)][string]$Bundle,
 [Parameter(Mandatory)][string]$ExpectedManifestSha256,
 [ValidateSet('etl_v2_exec','etl_v2_view')][string]$Account='etl_v2_exec',
 [string]$Request,[string]$ExpectedRequestSha256,[string]$CaseId,
 [string]$ConfigurationReceipt,[string]$ExpectedReceiptSha256,
 [switch]$Temporal,[switch]$StartCampaign,[switch]$ObservabilityProfile,[switch]$RetainedReplayScopes,
 [switch]$TemporalSchemaProfile,
 [ValidateSet('v1','v2')][string]$FixtureVersion='v2',
 [ValidatePattern('^[a-f0-9]{64}$')][string]$CompletionBatchSha256
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $PSScriptRoot 'Bloco54Artifact.psm1') -Force
$review=Read-Bloco54Bundle $Bundle $ExpectedManifestSha256
$encoding=[Text.UTF8Encoding]::new($false,$true)
if($Command -eq 'plan'){
    if(-not $Temporal){throw 'OFFLINE_TEMPORAL_PLAN_REQUIRED'}
    & 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe' -jar (Join-Path $review.Path 'etl-dataexport-v2.jar') plan --config (Join-Path $review.Path 'runtime.properties') --temporal $Request
    exit $LASTEXITCODE
}
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_ADMINISTRATIVE_LAUNCHER_REQUIRED'}
if($Command -eq 'install'){Install-Bloco54Revision $review | Out-Null; 'PROTECTED_REVISION_VERIFIED'; exit 0}
$protected='C:\ProgramData\EslEtlV2\app-bloco54'
Assert-Bloco54Protected $protected
$revision=Join-Path $protected $ExpectedManifestSha256.Substring(0,16)
foreach($entry in $review.Manifest.files){
    if((Get-FileHash -LiteralPath (Join-Path $revision $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'PROTECTED_ARTIFACT_CHANGED'}
}
foreach($name in @('etl_v2_exec','etl_v2_view')){
    $localAccount=Get-LocalUser -Name $name
    if(-not $localAccount.Enabled -or $null -eq $localAccount.AccountExpires -or $localAccount.AccountExpires.ToUniversalTime() -le [datetime]::UtcNow){throw 'LOCAL_ACCOUNT_EXPIRED_OR_DISABLED'}
}
if($Command -eq 'diagnose'){
    if($TemporalSchemaProfile -and (-not $RetainedReplayScopes -or -not $ObservabilityProfile)){throw 'TEMPORAL_SCHEMA_REQUIRES_COMPLETE_RETAINED_PROFILE'}
    if($RetainedReplayScopes -and -not $ObservabilityProfile){throw 'RETAINED_REPLAY_REQUIRES_OBSERVABILITY_PROFILE'}
    $validator=if($TemporalSchemaProfile){'database/proposals/bloco54-temporal-continuation/verify.sql'}elseif($RetainedReplayScopes){'database/proposals/bloco54-observability/verify-retained-replay.sql'}elseif($ObservabilityProfile){'database/proposals/bloco54-observability/verify.sql'}else{'database/validation/053_validate_local_runtime_provisioning.sql'}
    & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -l 10 -t 20 -b -i (Join-Path $root $validator)
    if($LASTEXITCODE -ne 0){throw 'EXACT_NOMINAL_GRANT_PROFILE_NOT_CONFIRMED'}
    & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -l 10 -t 20 -b -i (Join-Path $root 'database/validation/055_validate_bloco54_runtime_consumers.sql')
    if($LASTEXITCODE -ne 0){throw 'DURABLE_CONSUMERS_NOT_CONFIRMED'}
    'READ_ONLY_DIAGNOSIS_PASS_NO_RENEWAL'
    exit 0
}
if($Temporal -and $Command -ne 'run'){throw 'TEMPORAL_PROTECTED_RUN_ONLY'}
if($ExpectedRequestSha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-Item -LiteralPath $Request).Length -gt 16384 -or (Get-FileHash -LiteralPath $Request).Hash.ToLowerInvariant() -cne $ExpectedRequestSha256){throw 'FROZEN_REQUEST_REQUIRED'}
if($CaseId -cnotmatch '^[A-Za-z0-9_-]{1,80}$'){throw 'UNIQUE_CASE_REQUIRED'}
$exitCode=30;$server=$null;$password=$null;$opened=$false
$requestedPort=0
if($ConfigurationReceipt){
    if($ExpectedReceiptSha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-Item -LiteralPath $ConfigurationReceipt).Length -gt 16384 -or (Get-FileHash -LiteralPath $ConfigurationReceipt).Hash.ToLowerInvariant() -cne $ExpectedReceiptSha256){throw 'CONFIGURATION_RECEIPT_HASH_REQUIRED'}
    $prior=Get-Content -LiteralPath $ConfigurationReceipt -Raw | ConvertFrom-Json -DateKind String
    if($prior.manifestSha256 -cne $review.Hash -or $prior.port -lt 1 -or $prior.port -gt 65535){throw 'CONFIGURATION_RECEIPT_SCOPE_REJECTED'}
    $requestedPort=[int]$prior.port
}
Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
try{
    if($StartCampaign){
        if($CompletionBatchSha256){Start-Bloco54CompletionCampaign $CompletionBatchSha256 'manual-one-shot-completion'|Out-Null}
        else{Start-Bloco54Campaign 'manual-one-shot'|Out-Null}
        $opened=$true
    }
    $document=Get-Content -LiteralPath $Request -Raw | ConvertFrom-Json -DateKind String
    $units=1
    if($Temporal){$units=[int]$document.maximumBacklog;if($units -lt 1 -or $units -gt 4){throw 'TEMPORAL_RESERVATION_LIMIT'}}
    else{
        if($document.PSObject.Properties.Name -contains 'dependencyRequest'){$units++}
        if($document.PSObject.Properties.Name -contains 'temporalPolicy'){$units++}
    }
    # Reserve every possible window and the explicit predecessor STATUS before any child/socket.
    for($unit=1;$unit -le $units;$unit++){Add-Bloco54Reservation ($CaseId+'-'+$unit) 'MANUAL_OFFICIAL_JAR' | Out-Null}
    $work=Join-Path $revision ('work/'+$CaseId)
    if(Test-Path -LiteralPath $work){throw 'MANUAL_CASE_ALREADY_EXISTS'}
    [void][IO.Directory]::CreateDirectory($work)
    $frozen=Join-Path $work 'request.json'
    [IO.File]::Copy([IO.Path]::GetFullPath($Request),$frozen,$false)
    if((Get-FileHash -LiteralPath $frozen).Hash.ToLowerInvariant() -cne $ExpectedRequestSha256){throw 'REQUEST_COPY_UNCONFIRMED'}
    Add-Type -Path (Join-Path $PSScriptRoot 'Bloco54CompletionProcess.cs')
    $fixturePath=if($FixtureVersion -ceq 'v2'){'src/test/resources/runtime-laboratory-v2'}else{'src/test/resources/runtime-laboratory'}
    $server=[Bloco54CompletionLoopback]::new((Join-Path $root $fixturePath),$requestedPort)
    if($document.PSObject.Properties.Name -contains 'businessStart'){$server.FixtureDate=[string]$document.businessStart}
    $configuration=(Get-Content (Join-Path $revision 'runtime.properties') -Raw).Replace('http://127.0.0.1:1',('http://127.0.0.1:'+$server.Port))
    $config=Join-Path $work 'runtime.properties'
    [IO.File]::WriteAllText($config,$configuration,$encoding)
    $configHash=(Get-FileHash -LiteralPath $config).Hash.ToLowerInvariant()
    if($ConfigurationReceipt -and $prior.configurationSha256 -cne $configHash){throw 'FROZEN_CONFIGURATION_CHANGED'}
    $receipt=[ordered]@{version=1;manifestSha256=$review.Hash;port=$server.Port;configurationSha256=$configHash;requestSha256=$ExpectedRequestSha256}
    [IO.File]::WriteAllText((Join-Path $root ('target/bloco54/manual-'+$CaseId+'-configuration.json')),($receipt | ConvertTo-Json),$encoding)
    $password=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$Account.dpapi"))
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UserName=$Account;$start.Domain=$env:COMPUTERNAME;$start.Password=$password
    $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.WindowStyle=[Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token
    foreach($argument in @(('-Djava.library.path='+(Join-Path $revision 'native')),'-jar',(Join-Path $revision 'etl-dataexport-v2.jar'),$Command,'--config',$config,$(if($Temporal){'--temporal'}else{'--request'}),$frozen)){$start.ArgumentList.Add($argument)}
    $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
    $output=Join-Path $root ('target/bloco54/manual-'+$CaseId+'.log')
    [IO.File]::WriteAllText($output,$result.Output,$encoding)
    Write-Output $result.Output
    if($result.Limited -or $server.Failure){throw 'BOUNDED_CHILD_UNCONFIRMED'}
    $exitCode=$result.Code
    'MANUAL_ONE_SHOT exit='+$exitCode+' http='+$server.Requests
} finally {
    if($null -ne $server){$server.Dispose()}
    if($null -ne $password){$password.Dispose()}
    if($opened){Stop-Bloco54Campaign 'manual-finally-read-own-receipts-before-retry' | Out-Null}
}
exit $exitCode

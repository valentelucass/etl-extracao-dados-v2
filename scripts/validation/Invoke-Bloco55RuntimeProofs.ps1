#Requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedManifestSha256,
 [ValidatePattern('^reviewed-bundle-v[1-9][0-9]?$')][string]$Revision='reviewed-bundle-v2',
 [ValidateSet('Smoke','Matrix','Temporal','Manual','Sql')][string]$Phase='Smoke',
 [ValidatePattern('^2032-[0-9]{2}-[0-9]{2}$')][string]$ProofDate='2032-05-10',
 [string]$BatchManifest='', [string]$BatchSha256='',
 [ValidateSet('Run','Status')][string]$ManualOperation='Run',[ValidateRange(1,20)][int]$StopAfter=20,[switch]$ManualFault)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root ('target/bloco55/runtime/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
$results=[Collections.Generic.List[object]]::new();$credentials=@{};$server=$null
if(Test-Path $evidence){throw 'EXISTING_EVIDENCE_PRESERVE'}
[void][IO.Directory]::CreateDirectory($evidence)
function Record($Value){$results.Add($Value);[IO.File]::WriteAllText((Join-Path $evidence 'results.json'),($results|ConvertTo-Json -Depth 8),$encoding)}
function Sql([string]$Name,[string]$Body){
 $file=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($file,$Body,$encoding)
 & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.sql.log'))
 if($LASTEXITCODE -ne 0){throw ('SQL_FAILED_'+$Name)}
 [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.sql.log')))
}
function New-Request([string]$Template,[string]$Date){
 $q=@($review.Manifest.quality|Where-Object {$_.template -ceq $Template -and $_.mode -ceq 'BACKFILL'})
 if($q.Count -ne 1){throw 'EXACT_QUALITY_REQUIRED'}
 $end=[DateTime]::ParseExact($Date,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture).AddDays(1).ToString('yyyy-MM-dd')
 $request=[ordered]@{invocationId=[guid]::NewGuid().ToString();executionId=[guid]::NewGuid().ToString();cycleId=[guid]::NewGuid().ToString();template=$Template;mode='BACKFILL';start=$Date+'T03:00:00Z';endExclusive=$end+'T03:00:00Z';replayOf='';idempotencyKey=[guid]::NewGuid().ToString();businessStart=$Date;businessEnd=$Date;leaseSeconds='30';pageSize='2';maximumPages='4';maximumRows='16';maximumDistinctRoots='16';qualityVersion=$q[0].version;qualityFingerprint=$q[0].fingerprint;compatibilityVersion='bloco55-compatible-v1'}
 if($Template -ceq 'COTACOES'){$request.referenceReleaseId=[string]$activation.referenceReleaseId}
 $request
}
function Jar([string]$Name,[string]$Account,[string]$Command,$Document,[int]$Expected,[int]$Http,
 [string]$Scenario='NORMAL',[switch]$Cancel,[string]$KillMarker='',[string]$ConfigurationFile='',[switch]$ExistingPublicationRefusal){
 if($ExistingPublicationRefusal -and ($Expected -ne 40 -or $Http -ne 0 -or $Command -cne 'run')){throw 'EXISTING_PUBLICATION_REFUSAL_CONTRACT'}
 Add-Bloco55Reservation $Name 'OFFICIAL_JAR_WINDOWS_SQL_LOOPBACK'|Out-Null
 if($Document.Contains('temporalPolicy')){Add-Bloco55Reservation ($Name+'_PLAN') 'OFFICIAL_JAR_TEMPORAL_PERSISTENCE'|Out-Null}
 if($Document.Contains('dependencyRequest')){Add-Bloco55Reservation ($Name+'_DEP') 'OFFICIAL_JAR_DEPENDENCY_OBSERVATION'|Out-Null}
 $text=$Document|ConvertTo-Json -Depth 8
 if($encoding.GetByteCount($text) -gt 16384){throw 'REQUEST_BYTE_LIMIT'}
 $file=Join-Path $work ($Name+'.json');[IO.File]::WriteAllText($file,$text,$encoding)
 [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.request.json')),$text,$encoding)
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
 $start.UserName=$Account;$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials[$Account]
 $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
 $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$protectedRevision
 foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
 $start.Environment['V2_DATAEXPORT_TOKEN']=$server.Token
 $selectedConfig=if($ConfigurationFile){$ConfigurationFile}else{$config}
 if(-not ([IO.Path]::GetFullPath($selectedConfig)).StartsWith($work+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'OWNED_CONFIGURATION_REQUIRED'}
 foreach($arg in @('-Xmx512m',('-Djava.library.path='+(Join-Path $protectedRevision 'native')),'-jar',(Join-Path $protectedRevision 'etl-dataexport-v2.jar'),$Command,'--config',$selectedConfig,'--request',$file)){$start.ArgumentList.Add($arg)}
 if($Cancel){$start.RedirectStandardInput=$true;$start.ArgumentList.Add('--control-stdin')}
 $server.FixtureDate=$Document.businessStart
 $server.Scenario=$Scenario
 $before=$server.Requests
 $child=if($Cancel){[Bloco55Child]::Run($start,$server,$server.DataRequests+1,$null).GetAwaiter().GetResult()}elseif($KillMarker){[Bloco55Child]::Run($start,$null,0,$KillMarker).GetAwaiter().GetResult()}else{[Bloco55Child]::Run($start).GetAwaiter().GetResult()}
 [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$child.Output,$encoding)
 $exec=([guid]$Document.executionId).ToString();$inv=([guid]$Document.invocationId).ToString()
 $observation=Sql ($Name+'-observe') "SET NOCOUNT ON; DECLARE @execution UNIQUEIDENTIFIER='$exec',@invocation UNIQUEIDENTIFIER='$inv'; SELECT (SELECT decision FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation) decision,(SELECT reason FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation) reason,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id=@invocation) decisions,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id=@invocation) consumptions,(SELECT current_state FROM ctl.execution_attempt WHERE execution_id=@execution) state,(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id=@execution) publications,(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit WHERE execution_id=@execution) pages,(SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output WHERE execution_id=@execution) outputRows FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
 $observed=($observation -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
 $exitMatched=if($Expected -eq -999){$child.KilledByController -and $child.Code -ne 0}elseif($Expected -eq -998){$child.Code -in @(0,10,40,50)}else{$child.Code -eq $Expected}
 $httpMatched=if($Http -eq -1){$server.Requests-$before -in @(0,2,3,4)}else{$server.Requests-$before -eq $Http}
 $passed=$exitMatched -and $httpMatched -and -not $child.Limited -and -not $server.Failure
 if($Expected -eq -998 -and $child.Code -eq 0){$passed=$passed -and $observed.publications -eq 1 -and $observed.consumptions -eq 1}
 if($Expected -eq -998 -and $child.Code -ne 0){$passed=$passed -and $observed.publications -eq 0}
 if($Expected -eq 0){$passed=$passed -and $observed.publications -eq 1 -and $observed.consumptions -eq 1}
 if($Expected -eq 20){$passed=$passed -and $observed.decisions -eq 1 -and $observed.consumptions -eq 0 -and $observed.decision -ceq 'DENY'}
 if($Expected -in @(40,50)){
  $expectedPublications=if($ExistingPublicationRefusal){1}else{0}
  $passed=$passed -and $observed.consumptions -eq 1 -and $observed.publications -eq $expectedPublications
  if($ExistingPublicationRefusal){$passed=$passed -and $observed.state -ceq 'PUBLISHED' -and $child.Output.Contains('RUNTIME_OBSERVATION reason=INCONSISTENT')}
 }
 if($Expected -eq 50){$passed=$passed -and $observed.state -ceq 'CANCELLED' -and (-not $Cancel -or $child.ControlSent)}
 if($Expected -eq 30){$passed=$passed -and $observed.consumptions -eq 1}
 if($Expected -eq 2){$passed=$passed -and $observed.decisions -eq 0 -and $observed.consumptions -eq 0}
 Record ([ordered]@{id=$Name;layer='OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK';template=$Document.template;account=$Account;command=$Command;artifactManifest=$ExpectedManifestSha256;expectedExit=$Expected;observedExit=$child.Code;expectedHttp=$Http;http=$server.Requests-$before;killed=$child.KilledByController;cancelSent=$child.ControlSent;observed=$observed;passed=$passed})
 $script:lastObservation=$observed;$script:lastChild=$child;$script:lastHttp=$server.Requests-$before
 if($child.Limited -or $server.Failure){throw 'OWNED_PROCESS_OR_SOURCE_LIMIT'}
 if($child.Output.Contains('UNCONFIGURED')){throw 'OFFICIAL_PREFLIGHT_FAILED'}
 $passed
}
try{
 if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
 Import-Module (Join-Path $PSScriptRoot 'Bloco55Artifact.psm1') -Force
 Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
 $review=Read-Bloco55Bundle (Join-Path $root ('target/bloco55/'+$Revision)) $ExpectedManifestSha256
 if($Phase -ceq 'Manual'){
  Import-Module (Join-Path $root 'scripts/runtime/Bloco55ManualBatch.psm1') -Force
  $manualBatch=Read-Bloco55ManualBatch $BatchManifest $BatchSha256 $review
 }
 $activation=Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/applied.json') -Raw|ConvertFrom-Json
 Sql 'profile-before' (Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/verify-profile.sql') -Raw)|Out-Null
 Add-Bloco55Reservation ($ProofId+'_INSTALL') 'PROTECTED_REVIEWED_JAR_INSTALL_VERIFY'|Out-Null
 $base='C:\ProgramData\EslEtlV2\app-bloco55'
 if(-not (Test-Path $base)){
  [void][IO.Directory]::CreateDirectory($base)
  $acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
  $admin=[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544');$acl.SetOwner($admin)
  foreach($sid in @('S-1-5-32-544','S-1-5-18')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
  foreach($account in @('etl_v2_exec','etl_v2_view')){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new((Get-LocalUser $account).SID,'ReadAndExecute','ContainerInherit,ObjectInherit','None','Allow'))}
  Set-Acl -LiteralPath $base -AclObject $acl
  [IO.File]::WriteAllText((Join-Path $base 'ownership.json'),'BLOCO55_OWNER_AUTHORIZED_LOCAL_LAB_V1',$encoding)
 }
 $protectedRevision=Install-Bloco55Revision $review
 $work=Join-Path $protectedRevision ('work/'+$ProofId)
 if(Test-Path $work){throw 'PROTECTED_WORK_EXISTS_PRESERVE'}
 [void][IO.Directory]::CreateDirectory($work)
 foreach($name in @('etl_v2_exec','etl_v2_view')){$credentials[$name]=ConvertTo-SecureString -String ([IO.File]::ReadAllText("C:\ProgramData\EslEtlV2\secrets\$name.dpapi"))}
 Add-Type -Path (Join-Path $PSScriptRoot 'Bloco55Process.cs')
 Add-Bloco55Reservation ($ProofId+'_SOURCE') 'LOOPBACK_SYNTHETIC_BOUNDED_SOURCE'|Out-Null
 $server=[Bloco55Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'),62129)
 $config=Join-Path $work 'runtime.properties'
 [IO.File]::WriteAllText($config,[IO.File]::ReadAllText((Join-Path $protectedRevision 'runtime.properties')).Replace('http://127.0.0.1:1','http://127.0.0.1:62129'),$encoding)
 $requests=[Collections.Generic.List[object]]::new()
 if($Phase -ceq 'Smoke'){
 foreach($template in @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
  $r=New-Request $template $ProofDate;$requests.Add($r)
  $ok=Jar ($ProofId+'_'+$template+'_RUN') 'etl_v2_exec' 'run' $r 0 4
  if($ok){
   foreach($account in @('etl_v2_exec','etl_v2_view')){$r.invocationId=[guid]::NewGuid().ToString();Jar ($ProofId+'_'+$template+'_'+$account+'_STATUS') $account 'status' $r 0 0|Out-Null}
   $r.invocationId=[guid]::NewGuid().ToString();Jar ($ProofId+'_'+$template+'_KNOWN_RUN') 'etl_v2_exec' 'run' $r 0 0|Out-Null
  }
  if($template -cin @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
   $denied=New-Request $template '2032-04-11';Jar ($ProofId+'_'+$template+'_OPERATOR_RUN') 'etl_v2_view' 'run' $denied 20 0|Out-Null
   $invalid=New-Request $template '2032-04-12';$invalid.mode='INCREMENTAL';Jar ($ProofId+'_'+$template+'_MODE_DENIED') 'etl_v2_exec' 'run' $invalid 2 0|Out-Null
  }
 }
 }else{. (Join-Path $PSScriptRoot ('Bloco55'+$Phase+'Proofs.ps1'))}
 [IO.File]::WriteAllText((Join-Path $evidence 'occurrences.json'),($requests|ConvertTo-Json -Depth 8),$encoding)
 Sql 'profile-after' (Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/verify-profile.sql') -Raw)|Out-Null
 Assert-Bloco55Protected $base
 Record ([ordered]@{id='CONTROLLER_COMPLETED';passed=$true})
}catch{
 $reason=$_.Exception.Message;if($reason -cnotmatch '^[A-Za-z0-9_-]{1,160}$'){$reason='CONTROLLER_FAILURE_REDACTED'}
 Record ([ordered]@{id='CONTROLLER_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;passed=$false})
}finally{
 if($null -ne $server){Record ([ordered]@{id='HTTP_TOTAL';requests=$server.Requests;passed=$server.Requests -le 200});$server.Dispose()}
 foreach($credential in $credentials.Values){$credential.Dispose()}
 [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_INSPECT_RESULTS',$encoding)
}

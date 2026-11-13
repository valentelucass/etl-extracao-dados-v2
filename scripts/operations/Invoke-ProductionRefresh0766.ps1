[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$SettingsPath,
 [Parameter(Mandatory=$true)][string]$RuntimeManifestPath,
 [switch]$Continuous,
 [switch]$HistoryOnly,
 [switch]$Verify,
 [switch]$Worker,
 [string]$CycleId
)
$ErrorActionPreference='Stop'
$repo0766=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
. (Join-Path $PSScriptRoot 'RefreshSupervisor0766.ps1')
$storage0766='C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\'
$SettingsPath=[IO.Path]::GetFullPath($SettingsPath)
$RuntimeManifestPath=[IO.Path]::GetFullPath($RuntimeManifestPath)
if(-not $RuntimeManifestPath.StartsWith($storage0766,[StringComparison]::OrdinalIgnoreCase)){throw 'REFRESH_PRIVATE_MANIFEST_REQUIRED'}
$settings0766=Get-Content -LiteralPath $SettingsPath -Raw -Encoding utf8 | ConvertFrom-Json
$allowed0766=@('historyDays','historyStart','initialBatchDays','reconciliationBatchDays','changedDaysPerBatch','manifestScanDays','waitAfterCompletionMinutes','stateDirectory')
foreach($property0766 in $settings0766.PSObject.Properties.Name){if($property0766 -cnotin $allowed0766){throw 'REFRESH_UNSUPPORTED_SETTING'}}
$state0766=[IO.Path]::GetFullPath($settings0766.stateDirectory)
if(-not $state0766.StartsWith($storage0766,[StringComparison]::OrdinalIgnoreCase)){throw 'REFRESH_PRIVATE_STATE_REQUIRED'}
if($settings0766.waitAfterCompletionMinutes -ne 60){throw 'REFRESH_SIXTY_MINUTES_REQUIRED'}
if($HistoryOnly -and ($Continuous -or $settings0766.historyDays -ne 90 -or $settings0766.PSObject.Properties['historyStart'])){
 throw 'REFRESH_HISTORY_ONLY_REQUIRES_NINETY_DAYS'
}
if($Verify -and ($Worker -or $Continuous -or -not $HistoryOnly)){throw 'REFRESH_VERIFICATION_MODE_REQUIRED'}
$operatorStop0766=Join-Path $state0766 'STOP.operator.json'
$failureStop0766=Join-Path $state0766 'STOP.failure.json'
if(-not $Verify){
 $null=[IO.Directory]::CreateDirectory($state0766)
 if((Test-Path -LiteralPath $operatorStop0766) -or (Test-Path -LiteralPath $failureStop0766)){throw 'REFRESH_SUPERVISOR_STOP_PRESERVED'}
}
function Write-RefreshSupervisor0766($Value){
 $target0766=Join-Path $state0766 'supervisor.json'
 $temporary0766=$target0766+'.'+[Guid]::NewGuid().ToString('N')+'.partial'
 [IO.File]::WriteAllText($temporary0766,($Value | ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))
 Move-Item -LiteralPath $temporary0766 -Destination $target0766 -Force
}
if(-not $Worker -and -not $Verify){
 $supervisor0766=New-Object Threading.Mutex($false,'Global\ETL_V2_REFRESH0766_SUPERVISOR')
 $supervisorHeld0766=$false
 try{
  try{$supervisorHeld0766=$supervisor0766.WaitOne(0)}catch [Threading.AbandonedMutexException]{$supervisorHeld0766=$true}
  if(-not $supervisorHeld0766){throw 'REFRESH_SUPERVISOR_ALREADY_ACTIVE'}
  $supervisorPath0766=Join-Path $state0766 'supervisor.json'
  if($HistoryOnly -and (Test-Path -LiteralPath $supervisorPath0766)){
   $previous0766=Get-Content -LiteralPath $supervisorPath0766 -Raw -Encoding utf8 | ConvertFrom-Json
   if($previous0766.phase -ceq 'HISTORY_COMPLETED'){
    Write-Output 'Carga de 90 dias ja concluida.'
    return
   }
  }
  do{
   if((Test-Path -LiteralPath $operatorStop0766) -or (Test-Path -LiteralPath $failureStop0766)){
    if($HistoryOnly){throw 'Carga de 90 dias interrompida. O progresso foi salvo.'}
    break
   }
   $eligible0766=Get-RefreshEligible0766 $state0766
   if($HistoryOnly -and [DateTimeOffset]::UtcNow -lt $eligible0766){
    Write-Output ('Proximo lote a partir de '+$eligible0766.ToLocalTime().ToString('dd/MM/yyyy HH:mm:ss'))
   }
   while([DateTimeOffset]::UtcNow -lt $eligible0766){
    if((Test-Path -LiteralPath $operatorStop0766) -or (Test-Path -LiteralPath $failureStop0766)){
     if($HistoryOnly){throw 'Carga de 90 dias interrompida. O progresso foi salvo.'}
     return
    }
    $wait0766=($eligible0766-[DateTimeOffset]::UtcNow).TotalSeconds
    if($wait0766 -gt 0){Start-Sleep -Milliseconds ([int][Math]::Min(15000,[Math]::Ceiling($wait0766*1000)))}
   }
   $id0766=[Guid]::NewGuid().ToString('N')
   $arguments0766=@('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Worker','-SettingsPath',$SettingsPath,'-RuntimeManifestPath',$RuntimeManifestPath,'-CycleId',$id0766)
   if($HistoryOnly){
    $arguments0766+='-HistoryOnly'
    Write-Output ('Iniciando lote da carga de 90 dias em '+(Get-Date -Format 'dd/MM/yyyy HH:mm:ss'))
   }
   $outcome0766=& 'C:\Users\suporte\Documents\projetos\.runtime\memory-control\Executar-Batch.ps1' -Executable 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -ArgumentList $arguments0766 -WorkingDirectory $repo0766 -MaxMinutes 115 -ReturnReceipt -ReceiptPath (Join-Path $state0766 ($id0766+'.memory.json'))
   $finished0766=[DateTimeOffset]::UtcNow
   $reportPath0766=Join-Path $state0766 ('reports\'+$id0766+'.json')
   $report0766=$null
   $passed0766=$false
   if($outcome0766.job.ExitCode -eq 0 -and -not $outcome0766.job.TimedOut -and (Test-Path -LiteralPath $reportPath0766)){
    $report0766=Get-Content -LiteralPath $reportPath0766 -Raw -Encoding utf8 | ConvertFrom-Json
    $passed0766=$report0766.schema -ceq 'PRODUCTION_REFRESH0766' -and $report0766.passed -and
     (@($report0766.entities).Count -eq 11 -or (@($report0766.entities).Count -eq 12 -and $report0766.entities.entity -ccontains 'COLETAS_EXCLUIDAS')) -and
     @($report0766.entities.entity | Sort-Object -Unique).Count -eq @($report0766.entities).Count
    if($HistoryOnly){$passed0766=$passed0766 -and $report0766.historyOnly -eq $true -and @($report0766.entities).Count -eq 12}
   }
   if(-not $passed0766){
    [IO.File]::WriteAllText($failureStop0766,([ordered]@{schema='REFRESH_FAILURE0766';cycle=$id0766;at=$finished0766.ToString('o');reason='BATCH_NOT_CONFIRMED_NO_AUTOMATIC_RETRY'} | ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
    Write-RefreshSupervisor0766 ([ordered]@{phase='FAILED';cycle=$id0766;completedAt=$finished0766.ToString('o');nextEligibleAt=$null})
    if($HistoryOnly){Write-Output ('Lote interrompido. Consulte '+$reportPath0766)}
    throw 'REFRESH_BATCH_FAILED_STOP_PRESERVED'
   }
   $next0766=Get-RefreshNext0766 $finished0766
   $historyComplete0766=$HistoryOnly -and $report0766.initialComplete
   $phase0766=if($historyComplete0766){'HISTORY_COMPLETED'}elseif($Continuous -or $HistoryOnly){'WAITING'}else{'BATCH_COMPLETED'}
   Write-RefreshSupervisor0766 ([ordered]@{phase=$phase0766;cycle=$id0766;completedAt=$finished0766.ToString('o');nextEligibleAt=$next0766.ToString('o');initialComplete=$report0766.initialComplete})
   Write-Output ('REFRESH_BATCH_PASSED '+$id0766)
   if($historyComplete0766){
    Write-Output ('Carga de 90 dias concluida. Relatorio: '+$reportPath0766)
    break
   }
   if($HistoryOnly){
    $remainingDays0766=($report0766.entities | Where-Object {$null -ne $_.initialRemainingDays} | Measure-Object -Property initialRemainingDays -Maximum).Maximum
    foreach($pending0766 in @($report0766.entities | Where-Object {$_.status -ceq 'BATCH_DEFERRED' -and $_.reason})){
     Write-Output ($pending0766.entity+' ficou pendente para o proximo lote ('+$pending0766.reason+').')
    }
    if($null -ne $remainingDays0766){
     Write-Output ('Lote encerrado. Restam ate '+$remainingDays0766+' dias por entidade. Relatorio: '+$reportPath0766)
    }else{
     Write-Output ('Lote encerrado com datas pendentes. Relatorio: '+$reportPath0766)
    }
    continue
   }
   if(-not $Continuous){break}
   while([DateTimeOffset]::UtcNow -lt $next0766){
    if((Test-Path -LiteralPath $operatorStop0766) -or (Test-Path -LiteralPath $failureStop0766)){return}
    $remaining0766=($next0766-[DateTimeOffset]::UtcNow).TotalSeconds
    if($remaining0766 -gt 0){Start-Sleep -Milliseconds ([int][Math]::Min(15000,[Math]::Ceiling($remaining0766*1000)))}
   }
  }while($Continuous -or $HistoryOnly)
 }finally{if($supervisorHeld0766){$supervisor0766.ReleaseMutex()};$supervisor0766.Dispose()}
 return
}
if($PSVersionTable.PSEdition -ne 'Desktop' -or (-not $Verify -and $CycleId -notmatch '^[0-9a-f]{32}$')){throw 'REFRESH_PROTECTED_WINDOWS_WORKER_REQUIRED'}
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';' | Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
$manifest0766=Get-Content -LiteralPath $RuntimeManifestPath -Raw -Encoding utf8 | ConvertFrom-Json
if($manifest0766.schema -cne 'PRODUCTION_REFRESH_RUNTIME0766' -or @($manifest0766.files).Count -ne 9 -or @($manifest0766.scripts).Count -ne 3){throw 'REFRESH_RUNTIME_MANIFEST_SCOPE'}
$runtime0766=[IO.Path]::GetFullPath($manifest0766.directory)
if(-not $runtime0766.StartsWith($storage0766,[StringComparison]::OrdinalIgnoreCase) -or @(Get-ChildItem -LiteralPath $runtime0766 -File -Filter '*.jar').Count -ne 9){throw 'REFRESH_RUNTIME_DIRECTORY_SCOPE'}
foreach($pin0766 in $manifest0766.files){
 if($pin0766.name -notmatch '^[A-Za-z0-9_.-]+\.jar$' -or (Get-FileHash -LiteralPath (Join-Path $runtime0766 $pin0766.name)).Hash -cne $pin0766.sha256){throw 'REFRESH_RUNTIME_DRIFT'}
}
foreach($pin0766 in $manifest0766.scripts){if((Get-FileHash -LiteralPath (Join-Path $repo0766 $pin0766.path)).Hash -cne $pin0766.sha256){throw 'REFRESH_SCRIPT_DRIFT'}}
$jar0766=@($manifest0766.files | Where-Object name -CEQ 'etl-dataexport-v2-0766.jar')
if($jar0766.Count -ne 1){throw 'REFRESH_JAR_REQUIRED'}
if($Verify){
 Write-Output 'Arquivos da carga de 90 dias conferidos.'
 return
}
$sourceStop0766=Join-Path $storage0766 'quatro-etapas-20261024-01\independent-export-shared-dataexport\STOP429.json'
if(Test-Path -LiteralPath $sourceStop0766){throw 'REFRESH_SHARED_SOURCE_STOP'}
$sourceGate0766=New-Object Threading.Mutex($false,'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
$sourceHeld0766=$false;$credential0766=$null;$process0766=$null;$values0766=@{};$config0766=@{}
try{
 try{$sourceHeld0766=$sourceGate0766.WaitOne(0)}catch [Threading.AbandonedMutexException]{$sourceHeld0766=$true}
 if(-not $sourceHeld0766){throw 'REFRESH_OTHER_SOURCE_ACTIVE'}
 foreach($line0766 in [IO.File]::ReadAllLines((Join-Path $repo0766 '.env'))){
  if($line0766 -match '^\uFEFF?\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\s*=\s*(.*)$'){
   $key0766=$matches[1];$value0766=$matches[2].Trim()
   if($value0766.Length -ge 2 -and (($value0766[0] -eq '"' -and $value0766[$value0766.Length-1] -eq '"') -or ($value0766[0] -eq "'" -and $value0766[$value0766.Length-1] -eq "'"))){$value0766=$value0766.Substring(1,$value0766.Length-2)}
   if($value0766.Length){$values0766[$key0766]=$value0766}
  }
 }
 if(-not $values0766.ContainsKey('API_BASE_URL') -or -not $values0766.ContainsKey('API_DATAEXPORT_TOKEN')){throw 'REFRESH_SOURCE_CREDENTIAL_REQUIRED'}
 Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
 $profile0766=Get-ProjetosSqlProfile
 $privateSql0766=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Rodogarcia\SqlServer'
 $defaultCredential0766=Join-Path $privateSql0766 'etl-v2-runtime-prod.clixml'
 $credentialFile0766=$env:RODOGARCIA_ETL_V2_SQL_CREDENTIAL_FILE
 if(-not $credentialFile0766){$credentialFile0766=[Environment]::GetEnvironmentVariable('RODOGARCIA_ETL_V2_SQL_CREDENTIAL_FILE','User')}
 if(-not $credentialFile0766){$credentialFile0766=$defaultCredential0766}
 $credentialFile0766=[IO.Path]::GetFullPath($credentialFile0766)
 if(-not $credentialFile0766.Equals($defaultCredential0766,[StringComparison]::OrdinalIgnoreCase)){
  throw 'REFRESH_PRIVATE_CREDENTIAL_REQUIRED'
 }
 $credential0766=Import-Clixml -LiteralPath $credentialFile0766
 if($credential0766 -isnot [Management.Automation.PSCredential] -or $credential0766.UserName -cne 'etl_v2_runtime_prod'){
  throw 'REFRESH_RUNTIME_IDENTITY_REQUIRED'
 }
 foreach($property0766 in $settings0766.PSObject.Properties){if($property0766.Name -cne 'stateDirectory'){$config0766[$property0766.Name]=$property0766.Value}}
 if($HistoryOnly){$config0766.historyOnly=$true}
 $config0766.database='ETL_SISTEMA_V2';$config0766.runtimeSha256=$jar0766[0].sha256.ToLowerInvariant()
 $config0766.jdbcBaseUrl=$profile0766.jdbcBaseUrl;$config0766.sqlUser=$credential0766.UserName;$config0766.sqlPassword=$credential0766.GetNetworkCredential().Password
 $config0766.apiOrigin=$values0766['API_BASE_URL'].TrimEnd('/');$config0766.apiToken=$values0766['API_DATAEXPORT_TOKEN']
 $config0766.privateRoot=Join-Path $state0766 ('captures\'+$CycleId);$config0766.reportPath=Join-Path $state0766 ('reports\'+$CycleId+'.json')
 $config0766.checkpointPath=Join-Path $state0766 'checkpoint.json';$config0766.sourceStopPath=$sourceStop0766
 $process0766=New-Object Diagnostics.Process
 $process0766.StartInfo.FileName='C:\Users\suporte\AppData\Local\Programs\Eclipse Adoptium\jdk-17\bin\java.exe'
 $process0766.StartInfo.Arguments='-Xmx768m -Dfile.encoding=UTF-8 -cp "'+(Join-Path $runtime0766 '*')+'" br.com.esl.etl.v2.bootstrap.ProductionRefreshMain'
 $process0766.StartInfo.UseShellExecute=$false;$process0766.StartInfo.CreateNoWindow=$true
 $process0766.StartInfo.WorkingDirectory=$state0766
 $process0766.StartInfo.RedirectStandardInput=$true;$process0766.StartInfo.RedirectStandardOutput=$true;$process0766.StartInfo.RedirectStandardError=$true
 if(-not $process0766.Start()){throw 'REFRESH_JAVA_NOT_STARTED'}
 $outputTask0766=$process0766.StandardOutput.ReadToEndAsync();$errorTask0766=$process0766.StandardError.ReadToEndAsync()
 $writer0766=New-Object IO.StreamWriter($process0766.StandardInput.BaseStream,(New-Object Text.UTF8Encoding($false)))
 try{$writer0766.Write(($config0766 | ConvertTo-Json -Depth 5 -Compress))}finally{$writer0766.Dispose();$config0766.Clear();$values0766.Clear();$value0766=$null}
 if(-not $process0766.WaitForExit(6600000)){$process0766.Kill();$process0766.WaitForExit();throw 'REFRESH_JAVA_DEADLINE'}
 $withheldOutput0766=$outputTask0766.GetAwaiter().GetResult();$withheldError0766=$errorTask0766.GetAwaiter().GetResult()
 $withheldOutput0766=$null;$withheldError0766=$null
 $workerExit0766=$process0766.ExitCode
 Write-Output $(if($workerExit0766 -eq 0){'REFRESH_WORKER_PASSED'}else{'REFRESH_WORKER_FAILED_SEE_SANITIZED_REPORT'})
 exit $workerExit0766
}finally{
 $config0766.Clear();$values0766.Clear()
 if($credential0766 -is [Management.Automation.PSCredential]){$credential0766.Password.Dispose()}
 $credential0766=$null
 if($process0766){$process0766.Dispose()}
 if($sourceHeld0766){$sourceGate0766.ReleaseMutex()};$sourceGate0766.Dispose()
}

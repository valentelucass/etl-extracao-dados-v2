[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$CampaignPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Desktop'){throw 'ALL_RECEIVING_CAMPAIGN_WINDOWS_PS5_REQUIRED'}
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
. (Join-Path $PSScriptRoot 'AllReceivingCampaign0762.ps1')
$CampaignPath=[IO.Path]::GetFullPath($CampaignPath)
$campaign0762=Get-Content -LiteralPath $CampaignPath -Raw -Encoding UTF8|ConvertFrom-Json
if($campaign0762.schema -cne 'ETL_V2_ALL_RECEIVING_QUALIFICATION0762' -or $campaign0762.task_name -cne 'ETL-V2-All-Receiving-Qualification-0762' -or $campaign0762.maximum_cycles -ne 2 -or $campaign0762.delay_minutes -ne 60){throw 'ALL_RECEIVING_CAMPAIGN_SCOPE'}
$gate0762=New-Object Threading.Mutex($false,'Global\ETL_V2_ALL_RECEIVING_CAMPAIGN_0762');$held0762=$false
try{
 try{$held0762=$gate0762.WaitOne(0)}catch [Threading.AbandonedMutexException]{$held0762=$true}
 if(-not $held0762){throw 'ALL_RECEIVING_CAMPAIGN_OVERLAP'}
 if(Test-Path -LiteralPath $campaign0762.stop_path){throw 'ALL_RECEIVING_CAMPAIGN_STOP'}
 if(Test-Path -LiteralPath $campaign0762.state_path){$state0762=Get-Content -LiteralPath $campaign0762.state_path -Raw -Encoding UTF8|ConvertFrom-Json}else{$state0762=[pscustomobject]@{schema='ALL_RECEIVING_CAMPAIGN_STATE0762';cycles=@();next_at=$null;finished=$false}}
 if($state0762.schema -cne 'ALL_RECEIVING_CAMPAIGN_STATE0762' -or @($state0762.cycles).Count -ge 2){throw 'ALL_RECEIVING_CAMPAIGN_CLOSED'}
 $previous0762=@($state0762.cycles)
 foreach($cycle0762 in $previous0762){if($cycle0762.status -cne 'PASSED'){throw 'ALL_RECEIVING_PREVIOUS_CYCLE_UNCONFIRMED'}}
 if($previous0762.Count){
  $prior0762=Get-Content -LiteralPath $previous0762[-1].report_path -Raw -Encoding UTF8|ConvertFrom-Json
  if(-not(Test-AllReceivingComplete0762 $prior0762)){throw 'ALL_RECEIVING_PREVIOUS_REPORT_INCOMPLETE'}
  if([DateTimeOffset]::UtcNow -lt ([DateTimeOffset]::Parse($prior0762.completedAt).AddMinutes(60))){throw 'ALL_RECEIVING_DELAY_NOT_ELAPSED'}
 }
 $number0762=$previous0762.Count+1
 $reportPath0762=Join-Path $campaign0762.receipt_directory ('cycle'+$number0762+'.json')
 $private0762=Join-Path $campaign0762.private_directory ('cycle'+$number0762)
 if((Test-Path -LiteralPath $reportPath0762) -or (Test-Path -LiteralPath $private0762)){throw 'ALL_RECEIVING_CYCLE_ALREADY_RESERVED'}
 $entry0762=[pscustomobject]@{number=$number0762;started_at=[DateTimeOffset]::UtcNow.ToString('o');completed_at=$null;status='RUNNING';report_path=$reportPath0762;delay_after_previous_seconds=$null}
 $state0762.cycles=@($previous0762)+@($entry0762)
 Write-AllReceivingState0762 $campaign0762.state_path $state0762
 $passed0762=$false;$failure0762=$null
 try{
  & (Join-Path $PSScriptRoot 'Invoke-AllEntitiesReceiving0762.ps1') -Mode ROLLING -RecoveryAnchor $campaign0762.recovery_anchor -PrivateDirectory $private0762 -ReceiptPath $reportPath0762
  $childExit0762=$LASTEXITCODE
  if(-not(Test-Path -LiteralPath $reportPath0762)){throw 'ALL_RECEIVING_CYCLE_REPORT_MISSING'}
  $report0762=Get-Content -LiteralPath $reportPath0762 -Raw -Encoding UTF8|ConvertFrom-Json
  $passed0762=($childExit0762 -eq 0 -and (Test-AllReceivingComplete0762 $report0762))
  if($previous0762.Count){$entry0762.delay_after_previous_seconds=([DateTimeOffset]::Parse($report0762.startedAt)-[DateTimeOffset]::Parse($prior0762.completedAt)).TotalSeconds;if($entry0762.delay_after_previous_seconds -lt 3600){$passed0762=$false;$failure0762='ALL_RECEIVING_DELAY_PROOF_FAILED'}}
 }catch{$failure0762=if($_.Exception.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$_.Exception.Message}else{'ALL_RECEIVING_CYCLE_EXCEPTION'}}
 $entry0762.completed_at=[DateTimeOffset]::UtcNow.ToString('o');$entry0762.status=if($passed0762){'PASSED'}else{'FAILED'}
 if(-not $passed0762){
  [IO.File]::WriteAllText($campaign0762.stop_path,([ordered]@{schema='ALL_RECEIVING_CAMPAIGN_FAILURE0762';cycle=$number0762;failure=$failure0762;at=$entry0762.completed_at}|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)))
  Write-AllReceivingState0762 $campaign0762.state_path $state0762
  if(Get-ScheduledTask -TaskName $campaign0762.task_name -ErrorAction SilentlyContinue){$null=Disable-ScheduledTask -TaskName $campaign0762.task_name}
  throw 'ALL_RECEIVING_CYCLE_FAILED_NO_RETRY'
 }
 if($number0762 -eq 2){
  $state0762.finished=$true;$state0762.next_at=$null
  if(Get-ScheduledTask -TaskName $campaign0762.task_name -ErrorAction SilentlyContinue){$null=Disable-ScheduledTask -TaskName $campaign0762.task_name}
 }else{
  if(Get-ScheduledTask -TaskName $campaign0762.task_name -ErrorAction SilentlyContinue){throw 'ALL_RECEIVING_OWN_TASK_ALREADY_EXISTS'}
  $next0762=Get-AllReceivingNext0762 ([DateTimeOffset]::Parse($report0762.completedAt))
  $arguments0762='-NoLogo -NoProfile -NonInteractive -File "'+$PSCommandPath+'" -CampaignPath "'+$CampaignPath+'"'
  $action0762=New-ScheduledTaskAction -Execute 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -Argument $arguments0762 -WorkingDirectory ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')))
  $trigger0762=New-ScheduledTaskTrigger -Once -At $next0762.LocalDateTime
  $principal0762=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
  $settings0762=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 115) -StartWhenAvailable
  $null=Register-ScheduledTask -TaskName $campaign0762.task_name -Action $action0762 -Trigger $trigger0762 -Principal $principal0762 -Settings $settings0762 -Description 'Finite V2 qualification: eleven entities, SQL readback, second cycle 60 minutes after completion.'
  $state0762.next_at=$next0762.ToString('o')
 }
 Write-AllReceivingState0762 $campaign0762.state_path $state0762
 Write-Output ('ALL_RECEIVING_CYCLE_'+$number0762+'_PASSED')
}finally{if($held0762){$gate0762.ReleaseMutex()};$gate0762.Dispose()}

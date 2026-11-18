[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$CampaignPath)
$ErrorActionPreference='Stop'
$campaign=Get-Content -LiteralPath $CampaignPath -Raw -Encoding UTF8|ConvertFrom-Json
if($campaign.schema -cne 'ETL_V2_HOURLY_QUALIFICATION0759' -or $campaign.task_name -cne 'ETL-V2-Receiving-Qualification-0759' -or $campaign.maximum_cycles -ne 2 -or $campaign.delay_minutes -ne 60){throw 'RECEIVING_CAMPAIGN_SCOPE_REFUSED'}
if(Get-ScheduledTask -TaskName $campaign.task_name -ErrorAction SilentlyContinue){throw 'RECEIVING_TASK_ALREADY_EXISTS'}
if(-not(Test-Path -LiteralPath $campaign.qualification_report)){throw 'RECEIVING_REAL_QUALIFICATION_REQUIRED'}
$qualified=Get-Content -LiteralPath $campaign.qualification_report -Raw -Encoding UTF8|ConvertFrom-Json
if(-not $qualified.passed -or @($qualified.days | Where-Object {-not $_.stored -or -not $_.sqlEveryOccurrenceExact}).Count){throw 'RECEIVING_QUALIFICATION_NOT_PASSED'}
$script=Join-Path $PSScriptRoot 'Invoke-ReceivingQualificationCycle.ps1'
$arguments='-NoLogo -NoProfile -NonInteractive -File "'+$script+'" -CampaignPath "'+[IO.Path]::GetFullPath($CampaignPath)+'"'
$action=New-ScheduledTaskAction -Execute 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -Argument $arguments -WorkingDirectory ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')))
$trigger=New-ScheduledTaskTrigger -Once -At ([datetime]::Now.AddMinutes(1))
$principal=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
$settings=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 45) -StartWhenAvailable
$null=Register-ScheduledTask -TaskName $campaign.task_name -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Bounded two-cycle V2 receiving qualification; new DB only; V1/dashboard preserved.'
Get-ScheduledTask -TaskName $campaign.task_name | Select-Object TaskName,State

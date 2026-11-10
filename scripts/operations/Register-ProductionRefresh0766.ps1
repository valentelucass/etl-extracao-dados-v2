[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SettingsPath,[Parameter(Mandatory=$true)][string]$RuntimeManifestPath)
$ErrorActionPreference='Stop'
$SettingsPath=(Resolve-Path -LiteralPath $SettingsPath).Path
$RuntimeManifestPath=(Resolve-Path -LiteralPath $RuntimeManifestPath).Path
$name0766='ETL-V2-History-Refresh-0766'
if(Get-ScheduledTask -TaskName $name0766 -ErrorAction SilentlyContinue){throw 'REFRESH_OWN_TASK_ALREADY_EXISTS'}
$script0766=Join-Path $PSScriptRoot 'Invoke-ProductionRefresh0766.ps1'
$arguments0766='-NoLogo -NoProfile -NonInteractive -File "'+$script0766+'" -SettingsPath "'+$SettingsPath+'" -RuntimeManifestPath "'+$RuntimeManifestPath+'" -Continuous'
$action0766=New-ScheduledTaskAction -Execute 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -Argument $arguments0766 -WorkingDirectory ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')))
$trigger0766=New-ScheduledTaskTrigger -AtLogOn -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name)
$principal0766=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
$settings0766=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable
$null=Register-ScheduledTask -TaskName $name0766 -Action $action0766 -Trigger $trigger0766 -Principal $principal0766 -Settings $settings0766 -Description 'V2: initial history >=90 days, observed update filters, reconciliation and 60-minute wait after completion.'
Get-ScheduledTask -TaskName $name0766 | Select-Object TaskName,State

[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$CampaignPath)
$ErrorActionPreference='Stop'
$campaign=Get-Content -LiteralPath $CampaignPath -Raw -Encoding UTF8|ConvertFrom-Json
if($campaign.schema -cne 'ETL_V2_HOURLY_QUALIFICATION0759' -or $campaign.maximum_cycles -ne 2 -or $campaign.delay_minutes -ne 60 -or $campaign.task_name -cne 'ETL-V2-Receiving-Qualification-0759'){throw 'RECEIVING_CAMPAIGN_SCOPE_REFUSED'}
if(Test-Path -LiteralPath $campaign.stop_path){exit 1}
$reports=@(Get-ChildItem -LiteralPath $campaign.output_directory -Filter 'hourly-cycle*.json' -File | Where-Object Name -NotMatch '\.memory\.json$')
if($reports.Count -ge 2){exit 0}
foreach($f in $reports){$r=Get-Content -LiteralPath $f.FullName -Raw -Encoding UTF8|ConvertFrom-Json;if(-not $r.passed){exit 1}}
$sequence=$reports.Count+1
$path=Join-Path $campaign.output_directory ('hourly-cycle'+$sequence+'.json')
# CLI remains one-shot; Windows schedules the next occurrence after this one completes.
& 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -NoLogo -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'Invoke-ProductionReceiving.ps1') -Mode ROLLING -RecoveryAnchor $campaign.recovery_anchor -ReceiptPath $path
if($LASTEXITCODE -ne 0 -or -not(Test-Path -LiteralPath $path)){
 [IO.File]::WriteAllText($campaign.stop_path,'RECEIVING_FAILED_REQUIRES_REVIEW '+[DateTimeOffset]::Now.ToString('o'))
 exit 1
}
$report=Get-Content -LiteralPath $path -Raw -Encoding UTF8|ConvertFrom-Json
if(-not $report.passed){[IO.File]::WriteAllText($campaign.stop_path,'RECEIVING_REPORT_FAILED_REQUIRES_REVIEW');exit 1}
if($sequence -lt $campaign.maximum_cycles){
 $next=[datetime]::Now.AddMinutes(60)
 $trigger=New-ScheduledTaskTrigger -Once -At $next
 $null=Set-ScheduledTask -TaskName $campaign.task_name -Trigger $trigger
 [IO.File]::WriteAllText((Join-Path $campaign.output_directory ('next-after-cycle'+$sequence+'.json')),(@{next_local=$next.ToString('o');delay_from_completion_minutes=60;next_cycle=$sequence+1}|ConvertTo-Json))
}

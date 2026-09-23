#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module "$PSScriptRoot/Bloco60ResumedBudget.psm1" -Force
$prior=@();foreach($dir in @('physical-corrective/cc4f84cb37f69724','physical-continuation/0560a96925160b1e','physical-remaining/60e96d5d9b27ecf6')){$prior+=@(Get-Content (Join-Path $root ('target/bloco60-local/'+$dir+'/ledger.jsonl'))|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})}
$at=[DateTimeOffset]'2026-09-10T12:20:00Z';$deadline=$at.AddMinutes(60);$checks=0
$open=@{type='OPEN';utc=$at.ToString('o');deadline=$deadline.ToString('o')}
function Check($event,$current=@(),$now=$at){Assert-Bloco60ResumedBudget -Event $event -Prior $prior -Current $current -Now $now;$script:checks++}
function Entries([string]$kind,[int]$n,[bool]$emergency=$false){@($open)+@(1..$n|ForEach-Object {@{type='RESERVE';kind=$kind;utc=$at.ToString('o');emergency=$emergency}})}
function Refuse([scriptblock]$body,[string]$expected){$r='ACCEPTED';try{& $body}catch{$r=$_.Exception.Message};if($r -cne $expected){throw ('B60U_GUARD_'+$expected+'_GOT_'+$r)};$script:checks++}
Check @{type='OPEN'}
Refuse {Check @{type='OPEN'} @() ([DateTimeOffset]'2026-09-10T12:45:00Z')} 'B60U_SINGLE_USER_RESUMED_WINDOW'
Refuse {Check @{type='OPEN'} @($open)} 'B60U_SINGLE_USER_RESUMED_WINDOW'
Check @{type='RESERVE';kind='JVM'} (Entries JVM 42)
Check @{type='RESERVE';kind='JVM'} (Entries JVM 46)
Refuse {Check @{type='RESERVE';kind='JVM'} (Entries JVM 47)} 'B60U_CUMULATIVE_JVM'
Check @{type='RESERVE';kind='SQL'} (Entries READBACK 177)
Refuse {Check @{type='RESERVE';kind='SQL'} (Entries READBACK 178)} 'B60U_CUMULATIVE_SQL'
Refuse {Check @{type='RESERVE';kind='JVM'} @($open) $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Refuse {Check @{type='RESERVE';kind='SQL'} @($open) $deadline} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
Check @{type='RESERVE';kind='COMPENSATE'} (Entries READBACK 7 $true) $deadline
Refuse {Check @{type='RESERVE';kind='READBACK'} (Entries READBACK 8 $true) $deadline} 'B60U_CUMULATIVE_ESCROW'
Refuse {Check @{type='RESERVE';kind='JVM'} @($open,@{type='CLOSE'})} 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'
$wrong=$open.Clone();$wrong.deadline=$deadline.AddMinutes(1).ToString('o')
Refuse {Check @{type='RESERVE';kind='JVM'} @($wrong)} 'B60U_WINDOW_DRIFT'
@{passed=$true;checks=$checks;priorSqlcmd=54;priorJvms=33;plannedJvms=43;maximumPlannedCumulativeJvms=76;sqlExecuted=$false}|ConvertTo-Json

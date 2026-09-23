#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
Import-Module "$PSScriptRoot/Bloco60ContinuationBudget.psm1" -Force
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$prior=@(Get-Content (Join-Path $root 'target/bloco60-local/physical-corrective/cc4f84cb37f69724/ledger.jsonl')|ForEach-Object {$_|ConvertFrom-Json -AsHashtable -DateKind String})
$now=[DateTimeOffset]'2026-09-10T03:45:00Z';$checks=0
function Check([hashtable]$Event,[object[]]$Current=@(),[DateTimeOffset]$At=$now){Assert-Bloco60ContinuationBudget -Event $Event -Prior $prior -Current $Current -Now $At}
function Refuse([scriptblock]$Body,[string]$Expected){$reason='ACCEPTED';try{& $Body}catch{$reason=$_.Exception.Message};if($reason -cne $Expected){throw ('B60N_GUARD_'+$Expected+'_GOT_'+$reason)};$script:checks++}
function Reserved([string]$Kind,[int]$Count,[bool]$Emergency=$false){@(1..$Count|ForEach-Object {@{type='RESERVE';id='FAKE_'+$_;kind=$Kind;utc='2026-09-10T03:44:00Z';emergency=$Emergency}})}
Check @{type='OPEN'};$checks++
Check @{type='RESERVE';kind='READBACK'} (Reserved 'READBACK' 223);$checks++
Refuse {Check @{type='RESERVE';kind='SQL'} (Reserved 'READBACK' 224)} 'B60N_CUMULATIVE_SQL'
Check @{type='RESERVE';kind='JVM'} (Reserved 'JVM' 79);$checks++
Refuse {Check @{type='RESERVE';kind='JVM'} (Reserved 'JVM' 80)} 'B60N_CUMULATIVE_JVM'
$expired=[DateTimeOffset]'2026-09-10T04:40:11Z'
Refuse {Check @{type='OPEN'} @() $expired} 'B60N_DEADLINE_NO_RENEWAL'
Refuse {Check @{type='RESERVE';kind='SQL'} @() $expired} 'B60N_DEADLINE_NO_RENEWAL'
Check @{type='RESERVE';kind='COMPENSATE'} (Reserved 'READBACK' 231) $expired;$checks++
Refuse {Check @{type='RESERVE';kind='READBACK'} (Reserved 'READBACK' 232) $expired} 'B60N_CUMULATIVE_SQL'
Refuse {Check @{type='RESERVE';kind='READBACK'} (Reserved 'READBACK' 8 $true) $expired} 'B60N_CUMULATIVE_ESCROW'
@{passed=$true;checks=$checks;priorSqlCharged=8;priorJvms=0;deadlineUtc='2026-09-10T04:40:10.0501172Z';sqlExecuted=$false;renewals=0}|ConvertTo-Json

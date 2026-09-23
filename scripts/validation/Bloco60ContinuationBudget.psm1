#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# One cumulative envelope, beginning at the already charged corrective attempt.
# A leaf ledger is new; neither its OPEN nor a correction renews this deadline or balance.
function Assert-Bloco60ContinuationBudget {
 param([Parameter(Mandatory)][hashtable]$Event,[Parameter(Mandatory)][object[]]$Prior,
       [object[]]$Current=@(),[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
 $opens=@($Prior|Where-Object type -CEQ 'OPEN')
 if($opens.Count -ne 1 -or $opens[0].package -cne 'cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167' -or @($Prior|Where-Object type -CEQ 'CLOSE').Count -ne 1){throw 'B60N_EXACT_CLOSED_PREDECESSOR'}
 $deadline=[DateTimeOffset]$opens[0].deadline
 if($deadline -ne [DateTimeOffset]'2026-09-10T04:40:10.0501172Z'){throw 'B60N_ORIGINAL_DEADLINE'}
 $reserves=@(@($Prior)+@($Current)|Where-Object type -CEQ 'RESERVE')
 if(@($Prior|Where-Object type -CEQ 'RESERVE').Count -ne 8 -or @($Prior|Where-Object {$_.type -ceq 'RESERVE' -and $_.kind -cin @('JVM','JVM_PAIR')}).Count){throw 'B60N_PRIOR_DEBIT'}
 $stopped=$Now -ge $deadline -or $Now -ge [DateTimeOffset]'2026-09-16T00:00:00Z' -or @($Current|Where-Object type -CEQ 'CLOSE').Count -gt 0
 if($Event.type -ceq 'OPEN' -and $stopped){throw 'B60N_DEADLINE_NO_RENEWAL'}
 if($Event.type -cne 'RESERVE'){return}
 if($stopped -and $Event.kind -cnotin @('READBACK','COMPENSATE')){throw 'B60N_DEADLINE_NO_RENEWAL'}
 $emergency=@($reserves|Where-Object {$_.emergency -or [DateTimeOffset]$_.utc -ge $deadline}).Count
 if($stopped -and $emergency -ge 8){throw 'B60N_CUMULATIVE_ESCROW'}
 $sql=@($reserves|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count
 $jvms=@($reserves|Where-Object kind -cin @('JVM','JVM_PAIR')).Count
 if($Event.kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE') -and $sql -ge $(if($stopped){240}else{232})){throw 'B60N_CUMULATIVE_SQL'}
 if($Event.kind -cin @('JVM','JVM_PAIR') -and $jvms -ge 80){throw 'B60N_CUMULATIVE_JVM'}
 if($reserves.Count -ge 324){throw 'B60N_CUMULATIVE_RESERVATIONS'}
}
Export-ModuleMember Assert-Bloco60ContinuationBudget

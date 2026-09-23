#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
function Assert-Bloco60ResumedCorrectionBudget {
 param([Parameter(Mandatory)][hashtable]$Event,[Parameter(Mandatory)][object[]]$Prior,
       [object[]]$Current=@(),[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
 $opens=@($Prior|Where-Object type -CEQ 'OPEN')
 $packages=@('cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167','0560a96925160b1e17a01692a56abd91e1806fb45bc24ef01a6f485489c7c024','60e96d5d9b27ecf6307e202bd0dc55b287f9c70511d95f16a42dcad9f605f5db','8117708c8e211c6a8f81c34abc880acbad431102c5218c929bd0d10bafb8c0ea')
 if($opens.Count -ne 4 -or (@($opens.package)-join ',') -cne ($packages-join ',') -or @($Prior|Where-Object type -CEQ 'CLOSE').Count -ne 4){throw 'B60U_EXACT_FOUR_CLOSED_PREDECESSORS'}
 $old=@($Prior|Where-Object type -CEQ 'RESERVE')
 if($old.Count -ne 96 -or @($old|Where-Object kind -cin @('JVM','JVM_PAIR')).Count -ne 33 -or @($old|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count -ne 62){throw 'B60U_ALL_PRIOR_DEBITS_REQUIRED'}
 $deadline=[DateTimeOffset]$opens[3].deadline
 if($deadline -ne [DateTimeOffset]'2026-09-10T13:15:21.8816492+00:00'){throw 'B60U_WINDOW_DRIFT'}
 $stopped=$Now -ge $deadline -or $Now -ge [DateTimeOffset]'2026-09-16T00:00:00Z' -or @($Current|Where-Object type -CEQ 'CLOSE').Count -gt 0
 if($Event.type -ceq 'OPEN' -and ($stopped -or $Current.Count)){throw 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'}
 if($Event.type -cne 'RESERVE'){return}
 if($stopped -and $Event.kind -cnotin @('READBACK','COMPENSATE')){throw 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'}
 $reserves=@(@($Prior)+@($Current)|Where-Object type -CEQ 'RESERVE')
 $emergency=@($reserves|Where-Object {$_.emergency -or [DateTimeOffset]$_.utc -ge $deadline}).Count
 if($stopped -and $emergency -ge 8){throw 'B60U_CUMULATIVE_ESCROW'}
 $sql=@($reserves|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count
 $jvms=@($reserves|Where-Object kind -cin @('JVM','JVM_PAIR')).Count
 if($Event.kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE') -and $sql -ge $(if($stopped){240}else{232})){throw 'B60U_CUMULATIVE_SQL'}
 if($Event.kind -cin @('JVM','JVM_PAIR') -and $jvms -ge 80){throw 'B60U_CUMULATIVE_JVM'}
 if($reserves.Count -ge 324){throw 'B60U_CUMULATIVE_RESERVATIONS'}
}
Export-ModuleMember Assert-Bloco60ResumedCorrectionBudget

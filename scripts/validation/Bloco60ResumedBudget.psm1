#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
function Assert-Bloco60ResumedBudget {
 param([Parameter(Mandatory)][hashtable]$Event,[Parameter(Mandatory)][object[]]$Prior,
       [object[]]$Current=@(),[DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
 $opens=@($Prior|Where-Object type -CEQ 'OPEN')
 $packages=@('cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167','0560a96925160b1e17a01692a56abd91e1806fb45bc24ef01a6f485489c7c024','60e96d5d9b27ecf6307e202bd0dc55b287f9c70511d95f16a42dcad9f605f5db')
 if($opens.Count -ne 3 -or (@($opens.package)-join ',') -cne ($packages-join ',') -or @($Prior|Where-Object type -CEQ 'CLOSE').Count -ne 3){throw 'B60U_EXACT_CLOSED_PREDECESSORS'}
 $old=@($Prior|Where-Object type -CEQ 'RESERVE')
 if($old.Count -ne 88 -or @($old|Where-Object kind -cin @('JVM','JVM_PAIR')).Count -ne 33 -or @($old|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count -ne 54){throw 'B60U_ALL_PRIOR_DEBITS_REQUIRED'}
 $own=@($Current|Where-Object type -CEQ 'OPEN')
 if($Event.type -ceq 'OPEN'){
  if($Current.Count -or $Now -lt [DateTimeOffset]'2026-09-10T12:08:56Z' -or $Now -ge [DateTimeOffset]'2026-09-10T12:45:00Z'){throw 'B60U_SINGLE_USER_RESUMED_WINDOW'}
  return
 }
 if($own.Count -ne 1){throw 'B60U_CURRENT_OPEN_REQUIRED'}
 $start=[DateTimeOffset]$own[0].utc;$deadline=[DateTimeOffset]$own[0].deadline
 if($start -lt [DateTimeOffset]'2026-09-10T12:08:56Z' -or $start -ge [DateTimeOffset]'2026-09-10T12:45:00Z' -or $deadline -ne $start.AddMinutes(60)){throw 'B60U_WINDOW_DRIFT'}
 if($Event.type -cne 'RESERVE'){return}
 $reserves=@(@($Prior)+@($Current)|Where-Object type -CEQ 'RESERVE')
 $stopped=$Now -ge $deadline -or $Now -ge [DateTimeOffset]'2026-09-16T00:00:00Z' -or @($Current|Where-Object type -CEQ 'CLOSE').Count -gt 0
 if($stopped -and $Event.kind -cnotin @('READBACK','COMPENSATE')){throw 'B60U_DEADLINE_NO_AUTOMATIC_RENEWAL'}
 $emergency=@($reserves|Where-Object {$_.emergency -or [DateTimeOffset]$_.utc -ge $deadline}).Count
 if($stopped -and $emergency -ge 8){throw 'B60U_CUMULATIVE_ESCROW'}
 $sql=@($reserves|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count
 $jvms=@($reserves|Where-Object kind -cin @('JVM','JVM_PAIR')).Count
 if($Event.kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE') -and $sql -ge $(if($stopped){240}else{232})){throw 'B60U_CUMULATIVE_SQL'}
 if($Event.kind -cin @('JVM','JVM_PAIR') -and $jvms -ge 80){throw 'B60U_CUMULATIVE_JVM'}
 if($reserves.Count -ge 324){throw 'B60U_CUMULATIVE_RESERVATIONS'}
}
Export-ModuleMember Assert-Bloco60ResumedBudget

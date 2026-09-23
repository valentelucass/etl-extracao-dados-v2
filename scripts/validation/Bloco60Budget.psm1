#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Write-Bloco60Ledger {
 param([Parameter(Mandatory)][string]$Path,[Parameter(Mandatory)][hashtable]$Event,
       [DateTimeOffset]$Now=[DateTimeOffset]::UtcNow)
 $stream=[IO.File]::Open($Path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::Read)
 try {
  $reader=[IO.StreamReader]::new($stream,[Text.UTF8Encoding]::new($false,$true),$false,4096,$true)
  try {$raw=$reader.ReadToEnd()} finally {$reader.Dispose()}
  if($raw.Length -gt 2MB -or ($raw -and -not $raw.EndsWith("`n"))){throw 'B60_LEDGER_TRUNCATED_OR_LIMIT'}
  $events=@($raw -split "`n"|Where-Object {$_}|ForEach-Object {ConvertFrom-Json $_ -AsHashtable -DateKind String})
  $previous='0'*64;$ordinal=0
  foreach($entry in $events){
   if($entry.ordinal -ne ++$ordinal -or $entry.previous -cne $previous){throw 'B60_LEDGER_CHAIN'}
   $hash=$entry.hash;$entry.Remove('hash')
   $expected=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($entry|ConvertTo-Json -Depth 10 -Compress)))).ToLowerInvariant()
   if($expected -cne $hash){throw 'B60_LEDGER_HASH'}
   $previous=$hash
  }
  $opens=@($events|Where-Object type -CEQ 'OPEN');$reserves=@($events|Where-Object type -CEQ 'RESERVE')
  if($Event.type -ceq 'OPEN'){
   if($events.Count -or $Event.package -cnotmatch '^[a-f0-9]{64}$' -or $Now -lt [DateTimeOffset]'2026-09-09T00:00:00Z' -or $Now -ge [DateTimeOffset]'2026-09-16T00:00:00Z'){throw 'B60_OWN_VALIDITY_OR_SINGLE_OPEN'}
   $Event.deadline=$Now.AddMinutes(60).ToString('o')
  } else {
   if($opens.Count -ne 1){throw 'B60_OPEN_REQUIRED'}
   if($Event.type -ceq 'RESERVE'){
    $stopped=@($events|Where-Object type -CEQ 'CLOSE').Count -or $Now -ge [DateTimeOffset]$opens[0].deadline -or $Now -ge [DateTimeOffset]'2026-09-16T00:00:00Z'
    if($stopped -and $Event.kind -cnotin @('READBACK','COMPENSATE')){throw 'B60_CLOSED_OR_EXPIRED'}
    if($stopped -and @($reserves|Where-Object emergency -EQ $true).Count -ge 8){throw 'B60_RECOVERY_ESCROW_EXHAUSTED'}
    if($Event.id -cnotmatch '^[A-Z0-9_]{1,64}$' -or @($reserves|Where-Object id -CEQ $Event.id).Count){throw 'B60_RESERVATION_ID'}
    $pending=@($reserves|Where-Object { $id=$_.id; -not @($events|Where-Object { $_.type -cin @('OBSERVE','RECONCILE') -and $_.id -ceq $id -and $_.outcome -cne 'UNKNOWN' }).Count })
    # Only explicitly paired concurrency may have two unresolved JVM reservations.
    $pairAllowed=$Event.kind -ceq 'JVM_PAIR' -and $Event.id -cin @('CONCURRENT_A','CONCURRENT_B') -and $pending.Count -le 2 -and
      @($pending|Where-Object {$_.id -cnotin @('CONCURRENT_BARRIER','CONCURRENT_A','CONCURRENT_B')}).Count -eq 0
    if($pending.Count -and $Event.kind -cnotin @('READBACK','COMPENSATE') -and -not $pairAllowed){throw 'B60_RECONCILIATION_REQUIRED'}
    if($Event.kind -ceq 'COMPENSATE' -and ($Event.id -cne 'RESTORE_B60' -or @($reserves|Where-Object kind -CEQ 'COMPENSATE').Count)){throw 'B60_EXACT_COMPENSATION'}
    $cost=switch -CaseSensitive ($Event.kind){
     'JVM' {@{processes=1;sessions=256;commands=512;commits=4096;http=5;pages=4;nodes=80;bytes=5242880}}
     'JVM_PAIR' {@{processes=1;sessions=256;commands=512;commits=4096;http=5;pages=4;nodes=80;bytes=5242880}}
     'SQL' {@{processes=1;sessions=1;commands=1;commits=1;http=0;pages=0;nodes=0;bytes=0}}
     'READBACK' {@{processes=1;sessions=1;commands=1;commits=0;http=0;pages=0;nodes=0;bytes=0}}
     'COMPENSATE' {@{processes=1;sessions=1;commands=1;commits=1;http=0;pages=0;nodes=0;bytes=0}}
     'ROLLBACK' {@{processes=1;sessions=1;commands=1;commits=0;http=0;pages=0;nodes=0;bytes=0}}
     'INSTALL' {@{processes=0;sessions=0;commands=0;commits=0;http=0;pages=0;nodes=0;bytes=0}}
     default {throw 'B60_RESERVATION_KIND'}
    }
    $jvms=@($reserves|Where-Object kind -cin @('JVM','JVM_PAIR')).Count
    $sql=@($reserves|Where-Object kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE')).Count
    if(($Event.kind -cin @('JVM','JVM_PAIR') -and $jvms -ge 80) -or ($Event.kind -cin @('SQL','READBACK','ROLLBACK','COMPENSATE') -and $sql -ge $(if($stopped){240}else{232})) -or $reserves.Count -ge 324){throw 'B60_OWN_BUDGET_EXHAUSTED'}
    $Event.emergency=[bool]$stopped
    $Event.cost=$cost
   } elseif($Event.type -cin @('OBSERVE','RECONCILE')){
    if(@($reserves|Where-Object id -CEQ $Event.id).Count -ne 1 -or $Event.outcome -cnotin @('CONFIRMED','REFUSED','UNKNOWN') -or $Event.evidence -cnotmatch '^[a-f0-9]{64}$'){throw 'B60_OBSERVATION_SCHEMA'}
   } elseif($Event.type -ceq 'CLOSE') {
    if(@($events|Where-Object type -CEQ 'CLOSE').Count){throw 'B60_ALREADY_CLOSED'}
   } else {throw 'B60_EVENT_TYPE'}
  }
  $record=[ordered]@{ordinal=$ordinal+1;previous=$previous;utc=$Now.ToString('o')}
  foreach($key in @($Event.Keys|Sort-Object)){$record[$key]=$Event[$key]}
  $record.hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($record|ConvertTo-Json -Depth 10 -Compress)))).ToLowerInvariant()
  $bytes=[Text.Encoding]::UTF8.GetBytes(($record|ConvertTo-Json -Depth 10 -Compress)+"`n")
  [void]$stream.Seek(0,[IO.SeekOrigin]::End);$stream.Write($bytes);$stream.Flush($true)
  [pscustomobject]$record
 } finally {$stream.Dispose()}
}
Export-ModuleMember Write-Bloco60Ledger

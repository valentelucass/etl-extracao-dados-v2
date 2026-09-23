Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$script:root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:ledger=Join-Path $script:root 'target/bloco55/ledger.jsonl'
function Write-Bloco55Event([hashtable]$Event) {
    if((Get-FileHash -LiteralPath (Join-Path $script:root 'target/bloco54/ledger.jsonl')).Hash.ToLowerInvariant() -cne '64ad2f64d8dd899236926c5e9f9a50bd023b765ee232cb473b20934edcb75356'){throw 'B54_LEDGER_CHANGED'}
    if((Get-FileHash -LiteralPath (Join-Path $script:root 'target/bloco53/cumulative-reservations.txt')).Hash.ToLowerInvariant() -cne 'dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1'){throw 'B53_LEDGER_CHANGED'}
    $stream=[IO.File]::Open($script:ledger,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::Read)
    try {
        $reader=[IO.StreamReader]::new($stream,[Text.UTF8Encoding]::new($false,$true),$false,4096,$true)
        $content=$reader.ReadToEnd();$reader.Dispose()
        $events=@($content -split "`n" | Where-Object {$_} | ForEach-Object {$_|ConvertFrom-Json -DateKind String})
        $batches=@($events|Where-Object type -eq 'BATCH')
        $opened=@($events|Where-Object type -eq 'OPEN')
        $closed=@($events|Where-Object type -eq 'CLOSE')
        $reserved=@($events|Where-Object type -eq 'RESERVE')
        $now=[DateTimeOffset]::UtcNow
        if($Event.type -eq 'BATCH'){
            if($opened.Count -ne $closed.Count -or $batches.Count -ge 3 -or $Event.previousReservations -ne $reserved.Count -or $Event.manifestSha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'B55_BATCH_REFUSED'}
            $Event.ordinal=$batches.Count+1;$Event.units=128;$Event.maximumUnits=128*($batches.Count+1)
            $Event.authorization='OWNER_ADOPTED_B55_SECTION_3_A_J'
        }elseif($Event.type -eq 'OPEN'){
            if($batches.Count -eq 0 -or $opened.Count -ne $closed.Count -or $opened.Count -ge 12 -or $reserved.Count -ge $batches[-1].maximumUnits -or $now -ge [DateTimeOffset]::Parse('2026-10-07T22:34:30.615Z')){throw 'B55_CAMPAIGN_REFUSED'}
            $Event.campaign=$opened.Count+1;$Event.deadline=$now.AddMinutes(15).ToString('o')
            $Event.batch=$batches[-1].ordinal
        }elseif($Event.type -eq 'RESERVE'){
            if($opened.Count -ne $closed.Count+1 -or $now -ge [DateTimeOffset]::Parse($opened[-1].deadline) -or $reserved.Count -ge $batches[-1].maximumUnits -or @($reserved|Where-Object id -ceq $Event.id).Count){throw 'B55_BUDGET_OR_DEADLINE'}
            $Event.ordinal=$reserved.Count+1;$Event.campaign=$opened[-1].campaign
            $Event.maximumEntries=16;$Event.maximumPages=4;$Event.maximumDerived=512
        }elseif($Event.type -eq 'CLOSE'){
            if($opened.Count -ne $closed.Count+1){throw 'B55_ACTIVE_CAMPAIGN_REQUIRED'}
            $Event.campaign=$opened[-1].campaign
        }elseif($Event.type -eq 'NOTE'){
            if($Event.campaign -lt 1 -or $Event.campaign -gt $opened.Count -or $Event.reason -cnotmatch '^[A-Za-z0-9_-]{1,180}$'){throw 'B55_NOTE_SCHEMA'}
        }else{throw 'B55_EVENT_SCHEMA'}
        $Event.utc=$now.ToString('o')
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($Event|ConvertTo-Json -Compress)+"`n")
        [void]$stream.Seek(0,[IO.SeekOrigin]::End);$stream.Write($bytes);$stream.Flush($true)
        [pscustomobject]$Event
    }finally{$stream.Dispose()}
}
function Register-Bloco55Batch([string]$Manifest,[string]$ExpectedSha256){
    if((Get-FileHash -LiteralPath $Manifest).Hash.ToLowerInvariant() -cne $ExpectedSha256){throw 'B55_BATCH_HASH'}
    $batch=Get-Content -LiteralPath $Manifest -Raw|ConvertFrom-Json
    if($batch.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $batch.units -ne 128 -or $batch.maximumMinutes -ne 15 -or $batch.maximumHttp -ne 2048 -or $batch.maximumEntriesPerUnit -ne 16 -or $batch.maximumPagesPerUnit -ne 4 -or $batch.maximumDerivedPerUnit -ne 512){throw 'B55_BATCH_LIMITS'}
    Write-Bloco55Event @{type='BATCH';manifestSha256=$ExpectedSha256;previousReservations=[int]$batch.previousReservations}
}
function Start-Bloco55Campaign([string]$Purpose){Write-Bloco55Event @{type='OPEN';purpose=$Purpose}}
function Add-Bloco55Reservation([string]$Id,[string]$Layer){
    if($Id -cnotmatch '^[A-Za-z0-9_-]{1,100}$' -or $Layer -cnotmatch '^[A-Za-z0-9_-]{1,60}$'){throw 'B55_RESERVATION_SCHEMA'}
    Write-Bloco55Event @{type='RESERVE';id=$Id;layer=$Layer}
}
function Stop-Bloco55Campaign([string]$Reason){Write-Bloco55Event @{type='CLOSE';reason=$Reason}}
function Add-Bloco55Note([int]$Campaign,[string]$Reason){Write-Bloco55Event @{type='NOTE';campaign=$Campaign;reason=$Reason}}
Export-ModuleMember -Function Register-Bloco55Batch,Start-Bloco55Campaign,Add-Bloco55Reservation,Stop-Bloco55Campaign,Add-Bloco55Note

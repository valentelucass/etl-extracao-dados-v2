Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$script:root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:evidence=Join-Path $script:root 'target/bloco54'
$script:ledger=Join-Path $script:evidence 'ledger.jsonl'
function Write-Bloco54Event([hashtable]$Event) {
    $old=Join-Path $script:root 'target/bloco53/cumulative-reservations.txt'
    if((Get-FileHash -LiteralPath $old).Hash.ToLowerInvariant() -cne 'dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1'){throw 'B53_LEDGER_CHANGED'}
    $stream=[IO.File]::Open($script:ledger,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::Read)
    try {
        $reader=[IO.StreamReader]::new($stream,[Text.UTF8Encoding]::new($false,$true),$false,4096,$true)
        $content=$reader.ReadToEnd(); $reader.Dispose()
        $events=@($content -split "`n" | Where-Object {$_} | ForEach-Object {$_ | ConvertFrom-Json -DateKind String})
        $opened=@($events | Where-Object type -eq 'OPEN')
        $closed=@($events | Where-Object type -eq 'CLOSE')
        $reserved=@($events | Where-Object type -eq 'RESERVE')
        $extensions=@($events | Where-Object type -eq 'EXTEND')
        $unitCeiling=if($extensions.Count){[int]$extensions[-1].maximumUnits}else{128}
        if($Event.type -eq 'OPEN'){
            $completion=$Event.ContainsKey('completionApproval') -and $Event.completionApproval -ceq 'OWNER_APPROVED_B54_LOCAL_COMPLETION'
            $supplemental=$Event.ContainsKey('supplementalApproval') -and $Event.supplementalApproval -ceq 'OWNER_APPROVED_B54_SINGLE_SUPPLEMENTAL'
            $sixth=$Event.ContainsKey('sixthApproval') -and $Event.sixthApproval -ceq 'OWNER_APPROVED_B54_SIXTH_EXISTING_BALANCE'
            $maximum=if($completion){$opened.Count+1}elseif($sixth){6}elseif($supplemental){5}else{4}
            if($completion -and ($extensions.Count -eq 0 -or $Event.batchSha256 -cne $extensions[-1].batchSha256 -or $reserved.Count -ge $unitCeiling)){throw 'DECLARED_COMPLETION_BATCH_WITH_BALANCE_REQUIRED'}
            if($sixth -and ($opened.Count -ne 5 -or $reserved.Count -ne 99)){throw 'EXACT_SIXTH_START_CHECKPOINT_REQUIRED'}
            if($supplemental -and $opened.Count -ne 4){throw 'EXACTLY_ONE_SUPPLEMENTAL_CAMPAIGN'}
            if($opened.Count -ge $maximum -or $opened.Count -ne $closed.Count){throw 'CAMPAIGN_LIMIT_OR_UNCLOSED'}
            $Event.campaign=$opened.Count+1
            $Event.deadline=[DateTimeOffset]::UtcNow.AddMinutes(15).ToString('o')
        } elseif($Event.type -eq 'EXTEND') {
            if($opened.Count -ne $closed.Count -or $Event.completionApproval -cne 'OWNER_APPROVED_B54_LOCAL_COMPLETION' -or $Event.previousReservations -ne $reserved.Count -or $Event.additionalUnits -lt 1 -or $Event.additionalUnits -gt 128 -or $Event.previousMaximum -ne $unitCeiling -or $Event.batchSha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'EXPLICIT_FINITE_COMPLETION_BATCH_REQUIRED'}
            $Event.maximumUnits=$unitCeiling+$Event.additionalUnits
            $Event.campaign=$opened.Count
        } elseif($Event.type -eq 'NOTE') {
            $Event.campaign=$opened.Count
        } else {
            if($opened.Count -ne $closed.Count+1){throw 'ACTIVE_CAMPAIGN_REQUIRED'}
            $Event.campaign=$opened[-1].campaign
            if($Event.type -eq 'RESERVE'){
                if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($opened[-1].deadline)){throw 'CAMPAIGN_DEADLINE'}
                if($reserved.Count -ge $unitCeiling -or @($reserved | Where-Object id -eq $Event.id).Count){throw 'BUDGET_OR_DUPLICATE_RESERVATION'}
                $Event.ordinal=$reserved.Count+1
            }
        }
        $Event.utc=[DateTimeOffset]::UtcNow.ToString('o')
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($Event | ConvertTo-Json -Compress)+"`n")
        [void]$stream.Seek(0,[IO.SeekOrigin]::End); $stream.Write($bytes); $stream.Flush($true)
        [pscustomobject]$Event
    } finally {$stream.Dispose()}
}
function Start-Bloco54Campaign([string]$Purpose){Write-Bloco54Event @{type='OPEN';purpose=$Purpose}}
function Start-Bloco54SupplementalCampaign([string]$OwnerApproval){
    if($OwnerApproval -cne 'OWNER_APPROVED_B54_SINGLE_SUPPLEMENTAL'){throw 'EXPLICIT_SUPPLEMENTAL_OWNER_APPROVAL_REQUIRED'}
    Write-Bloco54Event @{type='OPEN';purpose='single-supplemental-continuation-same-128-unit-budget';supplementalApproval=$OwnerApproval}
}
function Start-Bloco54SixthCampaign([string]$OwnerApproval){
    if($OwnerApproval -cne 'OWNER_APPROVED_B54_SIXTH_EXISTING_BALANCE'){throw 'EXPLICIT_SIXTH_OWNER_APPROVAL_REQUIRED'}
    Write-Bloco54Event @{type='OPEN';purpose='remaining-cases-sixth-campaign-same-128-unit-budget';sixthApproval=$OwnerApproval}
}
function Add-Bloco54Reservation([string]$Id,[string]$Layer){
    if($Id -cnotmatch '^[A-Za-z0-9_-]{1,100}$' -or $Layer -cnotmatch '^[A-Za-z0-9_-]{1,60}$'){throw 'RESERVATION_SCHEMA'}
    Write-Bloco54Event @{type='RESERVE';id=$Id;layer=$Layer;maximumEntries=16;maximumPages=4;maximumDerived=512}
}
function Stop-Bloco54Campaign([string]$Reason){Write-Bloco54Event @{type='CLOSE';reason=$Reason}}
function Add-Bloco54Note([string]$Reason){Write-Bloco54Event @{type='NOTE';reason=$Reason}}
function Register-Bloco54CompletionBatch([string]$Manifest,[string]$ExpectedSha256,[string]$OwnerApproval){
    if($OwnerApproval -cne 'OWNER_APPROVED_B54_LOCAL_COMPLETION' -or $ExpectedSha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $Manifest).Hash.ToLowerInvariant() -cne $ExpectedSha256){throw 'OWNER_APPROVED_COMPLETION_BATCH_HASH_REQUIRED'}
    $batch=Get-Content -LiteralPath $Manifest -Raw|ConvertFrom-Json
    if($batch.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $batch.maximumMinutes -ne 15 -or $batch.maximumEntriesPerUnit -ne 16 -or $batch.maximumPagesPerUnit -ne 4 -or $batch.maximumDerivedPerUnit -ne 512){throw 'COMPLETION_BATCH_BOUNDARIES_CHANGED'}
    Write-Bloco54Event @{type='EXTEND';completionApproval=$OwnerApproval;previousReservations=[int]$batch.previousReservations;previousMaximum=[int]$batch.previousMaximum;additionalUnits=[int]$batch.additionalUnits;batchSha256=$ExpectedSha256}
}
function Start-Bloco54CompletionCampaign([string]$BatchSha256,[string]$Purpose){
    Write-Bloco54Event @{type='OPEN';completionApproval='OWNER_APPROVED_B54_LOCAL_COMPLETION';batchSha256=$BatchSha256;purpose=$Purpose}
}
Export-ModuleMember -Function Start-Bloco54Campaign,Start-Bloco54SupplementalCampaign,Start-Bloco54SixthCampaign,Register-Bloco54CompletionBatch,Start-Bloco54CompletionCampaign,Add-Bloco54Reservation,Stop-Bloco54Campaign,Add-Bloco54Note

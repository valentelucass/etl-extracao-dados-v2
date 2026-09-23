Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$script:root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
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
        if($Event.type -eq 'OPEN'){
            $supplemental=$Event.ContainsKey('supplementalApproval') -and $Event.supplementalApproval -ceq 'OWNER_APPROVED_B54_SINGLE_SUPPLEMENTAL'
            $sixth=$Event.ContainsKey('sixthApproval') -and $Event.sixthApproval -ceq 'OWNER_APPROVED_B54_SIXTH_EXISTING_BALANCE'
            $seventh=$Event.ContainsKey('seventhApproval') -and $Event.seventhApproval -ceq 'OWNER_APPROVED_B54_SEVENTH_EXISTING_BALANCE'
            $maximum=if($seventh){7}elseif($sixth){6}elseif($supplemental){5}else{4}
            if($seventh -and ($opened.Count -ne 6 -or $reserved.Count -ne 109)){throw 'EXACT_SEVENTH_START_CHECKPOINT_REQUIRED'}
            if($sixth -and ($opened.Count -ne 5 -or $reserved.Count -ne 99)){throw 'EXACT_SIXTH_START_CHECKPOINT_REQUIRED'}
            if($supplemental -and $opened.Count -ne 4){throw 'EXACTLY_ONE_SUPPLEMENTAL_CAMPAIGN'}
            if($opened.Count -ge $maximum -or $opened.Count -ne $closed.Count){throw 'CAMPAIGN_LIMIT_OR_UNCLOSED'}
            $Event.campaign=$opened.Count+1
            $Event.deadline=[DateTimeOffset]::UtcNow.AddMinutes(15).ToString('o')
        } elseif($Event.type -eq 'NOTE') {
            $Event.campaign=$opened.Count
        } else {
            if($opened.Count -ne $closed.Count+1){throw 'ACTIVE_CAMPAIGN_REQUIRED'}
            $Event.campaign=$opened[-1].campaign
            if($Event.type -eq 'RESERVE'){
                if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($opened[-1].deadline)){throw 'CAMPAIGN_DEADLINE'}
                if($reserved.Count -ge 128 -or @($reserved | Where-Object id -eq $Event.id).Count){throw 'BUDGET_OR_DUPLICATE_RESERVATION'}
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
function Start-Bloco54SeventhCampaign([string]$OwnerApproval){
    if($OwnerApproval -cne 'OWNER_APPROVED_B54_SEVENTH_EXISTING_BALANCE'){throw 'EXPLICIT_SEVENTH_OWNER_APPROVAL_REQUIRED'}
    Write-Bloco54Event @{type='OPEN';purpose='prepared-residual-runtime-same-128-unit-budget';seventhApproval=$OwnerApproval}
}
Export-ModuleMember -Function Start-Bloco54SeventhCampaign,Start-Bloco54Campaign,Start-Bloco54SupplementalCampaign,Start-Bloco54SixthCampaign,Add-Bloco54Reservation,Stop-Bloco54Campaign,Add-Bloco54Note

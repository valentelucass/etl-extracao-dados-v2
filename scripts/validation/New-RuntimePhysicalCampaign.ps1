param([ValidateRange(1,15)][int]$Minutes=15)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root 'target/bloco53'
$manifest=Join-Path $directory 'campaign.properties'
if (-not (Test-Path -LiteralPath $directory -PathType Container)) { [IO.Directory]::CreateDirectory($directory) | Out-Null }
$closed=@(Get-ChildItem -LiteralPath $directory -Filter 'campaign-??.closed.properties' -File)
if ($closed.Count -ge 3) { throw 'FOUR_FINITE_CAMPAIGNS_MAXIMUM_NO_BUDGET_RESET' }
$ledger=Join-Path $directory 'cumulative-reservations.txt'
if (Test-Path -LiteralPath $ledger) {
    $reservations=@(Get-Content -LiteralPath $ledger)
    foreach ($entry in $reservations) { [void][Guid]::ParseExact($entry,'D') }
    if ($reservations.Count -ge 128) { throw 'CUMULATIVE_BLOCK_BUDGET_EXHAUSTED' }
}
$encoding=[Text.UTF8Encoding]::new($false,$true)
if (Test-Path -LiteralPath $manifest) {
    $previous=[IO.File]::ReadAllText($manifest,$encoding)
    $deadline=[regex]::Match($previous,'(?m)^deadline=([^\r\n]+)$')
    if (-not $deadline.Success -or [DateTimeOffset]::Parse($deadline.Groups[1].Value) -gt [DateTimeOffset]::UtcNow) { throw 'PREVIOUS_CAMPAIGN_STILL_ACTIVE_OR_INVALID' }
    $archive=Join-Path $directory ('campaign-{0:D2}.closed.properties' -f ($closed.Count+1))
    if (Test-Path -LiteralPath $archive) { throw 'CAMPAIGN_ARCHIVE_COLLISION' }
    [IO.File]::WriteAllText($archive,$previous,$encoding)
}
$start=[DateTimeOffset]::UtcNow
$end=$start.AddMinutes($Minutes)
[IO.File]::WriteAllText($manifest,"campaign=$([Guid]::NewGuid())`ntarget=localhost/ETL_SISTEMA_V2_SHADOW`nstarted=$($start.ToString('o'))`ndeadline=$($end.ToString('o'))`n",$encoding)
Write-Output 'FINITE_CAMPAIGN_CREATED; CUMULATIVE_LEDGER_PRESERVED; NO_SQL_EXECUTED'

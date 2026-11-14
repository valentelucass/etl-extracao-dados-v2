[CmdletBinding()]
param(
    [switch]$Once,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 10
)

$ErrorActionPreference = 'Stop'
$storage = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'
$settings = Get-Content -LiteralPath (Join-Path $storage 'historico-90-dias-settings.json') -Raw -Encoding utf8 | ConvertFrom-Json
$state = $settings.stateDirectory
$catalogPath = Join-Path $PSScriptRoot '../../src/main/resources/contracts/dataexport/receiving-catalog-0766-v1.json'
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding utf8 | ConvertFrom-Json
$labels = @{}
foreach ($entry in $catalog.entities) {
    $labels[[int]$entry.reportId] = (Get-Culture).TextInfo.ToTitleCase($entry.entity.ToLowerInvariant().Replace('_', ' '))
}

function Read-StatusJson([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        try { Get-Content -LiteralPath $Path -Raw -Encoding utf8 | ConvertFrom-Json }
        catch [System.IO.IOException] { return $null }
        catch [System.Management.Automation.ItemNotFoundException] { return $null }
    }
}

function Read-LatestLedgerEvent([string]$Path) {
    try {
        $lines = @(Get-Content -LiteralPath $Path -Tail 6 -Encoding utf8)
    } catch [System.IO.IOException] {
        return $null
    } catch [System.Management.Automation.ItemNotFoundException] {
        return $null
    }
    # The worker can append events while the monitor is reading. Parse each line
    # independently and fall back to the last complete event if the tail is partial.
    for ($index = $lines.Count - 1; $index -ge 0; $index--) {
        try {
            $candidate = ConvertFrom-Json -InputObject $lines[$index]
        } catch {
            continue
        }
        $reportId = 0
        if ($candidate -is [pscustomobject] -and
            [int]::TryParse([string]$candidate.reportId, [ref]$reportId) -and
            $labels.ContainsKey($reportId) -and
            $candidate.state -cin @('RESERVED', 'OBSERVED', 'RETRY_WAIT')) {
            return $candidate
        }
    }
}

$lastMessage = $null
$lastPrinted = [DateTime]::MinValue
do {
    $supervisor = Read-StatusJson (Join-Path $state 'supervisor.json')
    $reportFile = Get-ChildItem -LiteralPath (Join-Path $state 'reports') -File -Filter '*.json' -ErrorAction SilentlyContinue |
        Where-Object BaseName -Match '^[0-9a-f]{32}$' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $report = if ($reportFile) { Read-StatusJson $reportFile.FullName }
    $currentSupervisor = if ($reportFile -and $supervisor.cycle -ceq $reportFile.BaseName) { $supervisor }
    $checkpoint = Read-StatusJson (Join-Path $state 'checkpoint.json')
    $done = 0
    foreach ($entry in $checkpoint.payload.entities.PSObject.Properties) {
        $entity = $entry.Value
        $done += ([DateTime]::ParseExact($entity.initialThrough, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture) -
                  [DateTime]::ParseExact($entity.initialNext, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)).Days
    }
    $total = [int]$settings.historyDays * @($catalog.entities).Count
    $progress = "$done/$total combinacoes de fonte e data gravadas no banco"
    $failed = @($report.entities | Where-Object { $_.status -ceq 'FAILED' }) | Select-Object -Last 1
    $finished = $false
    if ($currentSupervisor.phase -ceq 'HISTORY_COMPLETED') {
        $message = "Carga concluida. $progress."
        $finished = $true
    } elseif (Test-Path -LiteralPath (Join-Path $state 'STOP.failure.json')) {
        $detail = if ($failed) { "$($labels[[int]$failed.reportId]) - $($failed.reason)" } else { $report.reason }
        $message = "Carga interrompida. $detail. $progress."
        $finished = $true
    } elseif (Test-Path -LiteralPath (Join-Path $state 'STOP.operator.json')) {
        $message = "Parada solicitada pelo operador. $progress."
    } elseif ($report -and -not $report.completedAt) {
        $ledger = Join-Path $state ('captures\' + $reportFile.BaseName + '\ledger.jsonl')
        $event = Read-LatestLedgerEvent $ledger
        if ($event) {
            $label = $labels[[int]$event.reportId]
            $page = if ($event.page -eq 0) { 'metadados' } else { "$($event.day), pagina $($event.page)" }
            $request = switch ($event.state) {
                'OBSERVED' { "ESL respondeu HTTP $($event.http)" }
                'RETRY_WAIT' { "ESL falhou ($($event.reason)), nova tentativa em $($event.delaySeconds)s" }
                default { 'consultando ESL' }
            }
            $message = "$label ($page) - requisicao $($event.request), $request. $progress."
        } else {
            $message = "Lote iniciado, aguardando a primeira consulta. $progress."
        }
    } elseif ($currentSupervisor.nextEligibleAt) {
        $next = ([DateTimeOffset]$currentSupervisor.nextEligibleAt).ToLocalTime().ToString('dd/MM/yyyy HH:mm:ss')
        $message = "Lote encerrado. Proximo lote a partir de $next. $progress."
    } elseif ($currentSupervisor.phase -ceq 'FAILED') {
        $message = "Retomada liberada. Aguardando o proximo lote. $progress."
    } else {
        $message = "Aguardando o inicio da carga. $progress."
    }

    $now = Get-Date
    if ($message -cne $lastMessage -or ($now - $lastPrinted).TotalSeconds -ge 60) {
        Write-Output ('[{0:HH:mm:ss}] {1}' -f $now, $message)
        $lastMessage = $message
        $lastPrinted = $now
    }
    if ($Once -or $finished) { break }
    Start-Sleep -Seconds $IntervalSeconds
} while ($true)

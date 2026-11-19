[CmdletBinding(DefaultParameterSetName='Historico')]
param(
    [Parameter(Mandatory,ParameterSetName='Continuo')][switch]$Continuo,
    [Parameter(ParameterSetName='Continuo')][ValidateRange(0,1000)][int]$Lotes=0,
    [Parameter(Mandatory,ParameterSetName='Liberar')][switch]$SomenteLiberar,
    [Parameter(Mandatory,ParameterSetName='Publicar')][switch]$PublicarRecebido
)
$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'Start-ProductionHistory.ps1'
$storage = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'
$settings = Get-Content -LiteralPath (Join-Path $storage 'operational-settings.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$stateDir = [IO.Path]::GetFullPath($settings.stateDirectory)
if ($stateDir.TrimEnd('\','/') -ine [IO.Path]::GetFullPath((Join-Path $storage 'operational'))) {
    throw 'A pasta da rotina diaria diverge da configuracao instalada.'
}
$pausePath = Join-Path $stateDir 'STOP.operator.json'
$failurePath = Join-Path $stateDir 'STOP.failure.json'

function Write-RefreshRecord([string]$Path, $Value) {
    $temp = $Path + '.' + [guid]::NewGuid().ToString('N') + '.partial'
    try {
        [IO.File]::WriteAllText($temp, ($Value | ConvertTo-Json -Depth 8))
        if ([IO.File]::Exists($Path)) { [IO.File]::Replace($temp,$Path,[NullString]::Value) }
        else { [IO.File]::Move($temp,$Path) }
    } finally { if ([IO.File]::Exists($temp)) { [IO.File]::Delete($temp) } }
}

function Complete-PendingPublication {
    $failure = Get-Content -LiteralPath $failurePath -Raw -Encoding UTF8 | ConvertFrom-Json
    $id = $failure.cycle
    $lastPath = Join-Path $stateDir 'supervisor.json'
    $last = Get-Content -LiteralPath $lastPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($failure.schema -cne 'REFRESH_FAILURE0766' -or $id -cnotmatch '^[0-9a-f]{32}$' -or
        $failure.reason -cnotin @('RECEIVED_BATCH_PUBLICATION_PENDING','BATCH_NOT_CONFIRMED_NO_AUTOMATIC_RETRY') -or
        $last.cycle -cne $id -or $last.phase -cnotin @('FAILED','WAITING','HISTORY_COMPLETED')) {
        throw 'A falha registrada e o ultimo lote precisam corresponder para retomar a publicacao.'
    }
    if (Test-Path -LiteralPath $pausePath) {
        $pause = Get-Content -LiteralPath $pausePath -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($pause.schema -cne 'REFRESH_OPERATOR_STOP0766' -or $pause.cycle -cne $id) {
            throw 'A pausa pertence a outro lote.'
        }
    }
    $rawPath = Join-Path $stateDir "reports\$id.json"
    $raw = Get-Content -LiteralPath $rawPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $memory = Get-Content -LiteralPath (Join-Path $stateDir "$id.memory.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    $catalog = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../../src/main/resources/contracts/dataexport/receiving-catalog-0766-v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $expected = @($catalog.entities | ForEach-Object { $_.entity + ':' + $_.reportId } | Sort-Object)
    $actual = @($raw.entities | ForEach-Object { $_.entity + ':' + $_.reportId } | Sort-Object)
    if ($raw.schema -cne 'PRODUCTION_REFRESH0766' -or $raw.passed -isnot [bool] -or !$raw.passed -or
        $raw.historyOnly -isnot [bool] -or $raw.initialComplete -isnot [bool] -or !$raw.completedAt -or !$raw.startedAt -or
        [DateTimeOffset]$raw.completedAt -lt [DateTimeOffset]$raw.startedAt -or $expected.Count -ne 13 -or
        ($actual -join ',') -cne ($expected -join ',') -or @($raw.entities | Where-Object status -CEQ 'FAILED').Count -or
        $null -eq $memory.job.ExitCode -or $memory.job.ExitCode -ne 0 -or $memory.job.TimedOut -isnot [bool] -or $memory.job.TimedOut) {
        throw "O recebimento do lote nao foi confirmado. Consulte $rawPath antes de retomar."
    }
    if ($raw.historyOnly) {
        $requestPath = Join-Path $stateDir 'historico-solicitado.json'
        $request = Get-Content -LiteralPath $requestPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if (!$raw.initialComplete -or $request.id -cnotmatch '^[0-9a-f]{32}$' -or
            ($failure.historyRequestId -and $request.id -cne $failure.historyRequestId)) {
            throw 'A carga de 90 dias ainda precisa de confirmacao.'
        }
        $primary = Join-Path $stateDir 'checkpoint.json'
        $history = Join-Path $stateDir ('checkpoint-history-' + $request.id + '.json')
        $checkpoint = Get-Content -LiteralPath $history -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($checkpoint.schema -cne 'REFRESH_CHECKPOINT0766' -or $raw.sourceScope -cnotmatch '^[0-9a-f]{64}$' -or
            $checkpoint.payload.sourceScope -cne $raw.sourceScope -or $checkpoint.payload.lastCompletedAt -cne $raw.completedAt) {
            throw 'O ponto de retomada do historico pertence a outro recebimento.'
        }
    }
    $receiptPath = Join-Path $stateDir "reports\$id.publication.json"
    $alreadyPublished = Test-Path -LiteralPath $receiptPath -PathType Leaf
    if ($alreadyPublished) {
        $published = Get-Content -LiteralPath $receiptPath -Raw -Encoding UTF8 | ConvertFrom-Json
    } else {
        if ($last.phase -cne 'FAILED') { throw 'O recibo de publicacao do ultimo lote esta ausente.' }
        $published = & (Join-Path $PSScriptRoot 'Publish-ReceivedDashboard.ps1') -ReportPath $rawPath -CaptureDirectory (Join-Path $stateDir "captures\$id")
    }
    if ($published.ciclo -ine [guid]::ParseExact($id,'N').ToString() -or $null -eq $published.pendentes -or
        $published.pendentes -ne 0 -or $published.auditoria -lt 1 -or !$published.concluidaUtc -or
        [DateTimeOffset]$published.concluidaUtc -lt [DateTimeOffset]$raw.completedAt) {
        throw 'O banco ainda nao confirmou a publicacao desse lote.'
    }
    if (!$alreadyPublished) { Write-RefreshRecord $receiptPath $published }
    if ($raw.historyOnly) {
        if (!(Test-Path -LiteralPath $primary)) {
            $seed = $primary + '.' + [guid]::NewGuid().ToString('N') + '.partial'
            try { [IO.File]::Copy($history,$seed); [IO.File]::Move($seed,$primary) }
            finally { if ([IO.File]::Exists($seed)) { [IO.File]::Delete($seed) } }
        }
        $request.finished = $true
        Write-RefreshRecord $requestPath $request
    }
    $finish = [DateTimeOffset]$published.concluidaUtc
    $phase = if ($raw.historyOnly) { 'HISTORY_COMPLETED' } else { 'WAITING' }
    Write-RefreshRecord $lastPath ([ordered]@{phase=$phase;cycle=$id;completedAt=$finish.ToString('o');nextEligibleAt=$finish.AddMinutes(60).ToString('o');initialComplete=$raw.initialComplete})
    [IO.File]::Move($failurePath,(Join-Path $stateDir ('falha-publicacao-retomada-' + $id + '-' + [guid]::NewGuid().ToString('N') + '.json')))
    Write-Output "Dados recebidos e publicados. Lote $id."
}

& $launcher -Verify
if ((Test-Path -LiteralPath $failurePath) -or (Test-Path -LiteralPath $pausePath) -or $PublicarRecebido) {
    $mutexes = @()
    try {
        foreach ($name in 'CONTINUOUS','SUPERVISOR') {
            $mutex = [Threading.Mutex]::new($false,"Global\ETL_V2_REFRESH0766_$name")
            try { $held = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $held = $true }
            if (!$held) { $mutex.Dispose(); throw 'Uma carga ainda esta em andamento. Aguarde o encerramento para retomar.' }
            $mutexes += $mutex
        }
        if (Test-Path -LiteralPath $failurePath) {
            if ($SomenteLiberar) { throw 'O ultimo lote falhou. Confira o recebimento antes de liberar a rotina.' }
            Complete-PendingPublication
        }
        if ($PublicarRecebido) { Write-Output 'Conferencia de publicacao encerrada.'; return }
        if (Test-Path -LiteralPath $pausePath) {
            $pause = Get-Content -LiteralPath $pausePath -Raw -Encoding UTF8 | ConvertFrom-Json
            $last = Get-Content -LiteralPath (Join-Path $stateDir 'supervisor.json') -Raw -Encoding UTF8 | ConvertFrom-Json
            if ((Test-Path -LiteralPath $failurePath) -or $pause.schema -cne 'REFRESH_OPERATOR_STOP0766' -or
                $pause.cycle -cnotmatch '^[0-9a-f]{32}$' -or $pause.cycle -cne $last.cycle -or
                $last.phase -cnotin @('WAITING','BATCH_COMPLETED','HISTORY_COMPLETED')) {
                throw 'A pausa precisa ser conferida. O ultimo lote nao tem conclusao confirmada.'
            }
            $published = Get-Content -LiteralPath (Join-Path $stateDir "reports\$($pause.cycle).publication.json") -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($published.ciclo -ine [guid]::ParseExact($pause.cycle,'N').ToString() -or $null -eq $published.pendentes -or
                $published.pendentes -ne 0 -or $published.auditoria -lt 1) {
                throw 'O painel ainda nao confirmou a publicacao do lote anterior.'
            }
            $archive = Join-Path $stateDir ('pausa-retomada-'+$pause.cycle+'-'+[guid]::NewGuid().ToString('N')+'.json')
            [IO.File]::Move($pausePath,$archive)
        }
    } finally {
        foreach ($mutex in $mutexes) { $mutex.ReleaseMutex(); $mutex.Dispose() }
    }
}
if ($SomenteLiberar) { Write-Output 'Rotina liberada para o operador iniciar no PM2.'; return }
if ($Continuo) { & $launcher -Continuo -Lotes $Lotes } else { & $launcher }

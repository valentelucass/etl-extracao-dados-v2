[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$temporary = (Get-Item -LiteralPath ([IO.Path]::GetTempPath())).FullName.TrimEnd('\','/')
$root = Join-Path $temporary ('etl-publication-test-' + [guid]::NewGuid().ToString('N'))
$operations = Join-Path $root 'scripts/operations'
$catalogPath = 'src/main/resources/contracts/dataexport/receiving-catalog-0766-v1.json'
$storageBefore = $env:ETL_PUBLICATION_TEST_DIRECTORY

function Write-TestRecord([string]$Path, $Record) {
    [IO.File]::WriteAllText($Path, ($Record | ConvertTo-Json -Depth 6))
}

try {
    [void][IO.Directory]::CreateDirectory($operations)
    $catalogCopy = Join-Path $root $catalogPath
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($catalogCopy))
    [IO.File]::Copy((Join-Path $repo $catalogPath), $catalogCopy)
    $catalog = Get-Content -LiteralPath $catalogCopy -Raw -Encoding UTF8 | ConvertFrom-Json
    $source = [IO.File]::ReadAllText((Join-Path $repo 'scripts/operations/Resume-ProductionHistory.ps1'))
    $storageLine = [regex]::Match($source, '(?m)^\$storage = Join-Path \(\[Environment\]::GetFolderPath\(''MyDocuments''\)\) [^\r\n]+').Value
    $productionLock = 'Global\ETL_V2_REFRESH0766_'
    if (!$storageLine -or !$source.Contains($productionLock)) {
        throw 'Os caminhos de isolamento do teste precisam acompanhar o iniciador.'
    }
    $launcher = Join-Path $operations 'Resume-ProductionHistory.ps1'
    $isolated = $source.Replace($storageLine, '$storage = $env:ETL_PUBLICATION_TEST_DIRECTORY')
    [IO.File]::WriteAllText($launcher, $isolated.Replace($productionLock, ('Local\ETL_TEST_' + [guid]::NewGuid() + '_')))
    [IO.File]::WriteAllText((Join-Path $operations 'Start-ProductionHistory.ps1'), @'
param([switch]$Verify)
if (!$Verify) { throw 'Este teste nao inicia uma extracao.' }
'@)
    [IO.File]::WriteAllText((Join-Path $operations 'Publish-ReceivedDashboard.ps1'), @'
param($ReportPath, $CaptureDirectory)
$state = Join-Path $env:ETL_PUBLICATION_TEST_DIRECTORY 'operational'
$run = [IO.Path]::GetFileNameWithoutExtension($ReportPath)
if ([IO.Path]::GetFileName($CaptureDirectory) -cne $run) { throw 'Captura de outro lote.' }
Add-Content -LiteralPath "$state/chamadas.txt" -Value $run
Get-Content -LiteralPath "$state/publicador.json" -Raw | ConvertFrom-Json
'@)

    $entities = @($catalog.entities | ForEach-Object { @{entity=$_.entity; reportId=$_.reportId; status='BATCH_COMPLETED'} })
    foreach ($case in 'recebido','ja-publicado','recebimento-falho','recibo-de-outro-lote','fila-pendente') {
        $env:ETL_PUBLICATION_TEST_DIRECTORY = Join-Path $root $case
        $state = Join-Path $env:ETL_PUBLICATION_TEST_DIRECTORY 'operational'
        [void][IO.Directory]::CreateDirectory((Join-Path $state 'reports'))
        Write-TestRecord "$env:ETL_PUBLICATION_TEST_DIRECTORY/operational-settings.json" @{stateDirectory=$state}
        $id = [guid]::NewGuid()
        $cycle = $id.ToString('N')
        $finished = [DateTimeOffset]::UtcNow.AddHours(-1)
        $published = $finished.AddMinutes(4)
        $report = @{schema='PRODUCTION_REFRESH0766'; passed=($case -ne 'recebimento-falho'); historyOnly=$false;
            initialComplete=$true; startedAt=$finished.AddMinutes(-15).ToString('o'); completedAt=$finished.ToString('o'); entities=$entities}
        Write-TestRecord "$state/reports/$cycle.json" $report
        Write-TestRecord "$state/$cycle.memory.json" @{job=@{ExitCode=0; TimedOut=$false}}
        Write-TestRecord "$state/STOP.operator.json" @{schema='REFRESH_OPERATOR_STOP0766'; cycle=$cycle}
        Write-TestRecord "$state/STOP.failure.json" @{schema='REFRESH_FAILURE0766'; cycle=$cycle; reason='RECEIVED_BATCH_PUBLICATION_PENDING'}
        $phase = if ($case -eq 'ja-publicado') { 'WAITING' } else { 'FAILED' }
        Write-TestRecord "$state/supervisor.json" @{phase=$phase; cycle=$cycle}
        $receiptId = if ($case -eq 'recibo-de-outro-lote') { [guid]::NewGuid() } else { $id }
        $receipt = @{ciclo=$receiptId.ToString(); auditoria=1; pendentes=[int]($case -eq 'fila-pendente'); concluidaUtc=$published.ToString('o')}
        Write-TestRecord "$state/publicador.json" $receipt
        $saved = "$state/reports/$cycle.publication.json"
        if ($case -eq 'ja-publicado') { Write-TestRecord $saved $receipt }
        $accepted = $true
        try { & $launcher -PublicarRecebido | Out-Null } catch { $accepted = $false }
        if ($accepted -ne ($case -in 'recebido','ja-publicado') -or !(Test-Path -LiteralPath "$state/STOP.operator.json")) {
            throw "Resultado inesperado em $case."
        }
        if ((Test-Path -LiteralPath "$state/STOP.failure.json") -eq $accepted -or (Test-Path -LiteralPath $saved) -ne $accepted) {
            throw "Recibo ou registro da falha divergente em $case."
        }
        if ($accepted) {
            $supervisor = Get-Content -LiteralPath "$state/supervisor.json" -Raw | ConvertFrom-Json
            if ($supervisor.phase -cne 'WAITING' -or [DateTimeOffset]$supervisor.nextEligibleAt -ne $published.AddMinutes(60)) {
                throw 'Intervalo de 60 minutos contado do momento incorreto.'
            }
            & $launcher -PublicarRecebido | Out-Null
        }
        $calls = if (Test-Path -LiteralPath "$state/chamadas.txt") { @(Get-Content -LiteralPath "$state/chamadas.txt").Count } else { 0 }
        if ($calls -ne [int]($case -in 'recebido','recibo-de-outro-lote','fila-pendente')) {
            throw "Numero de publicacoes incorreto em $case."
        }
    }
    Write-Output 'Cinco casos de retomada passaram.'

    $monitorSource = [IO.File]::ReadAllText((Join-Path $repo 'scripts/operations/Watch-ProductionHistory.ps1'))
    if (!$monitorSource.Contains($storageLine)) { throw 'Confira o isolamento do monitor.' }
    $monitor = Join-Path $operations 'Watch-ProductionHistory.ps1'
    [IO.File]::WriteAllText($monitor, $monitorSource.Replace($storageLine, '$storage = $env:ETL_PUBLICATION_TEST_DIRECTORY'))
    $env:ETL_PUBLICATION_TEST_DIRECTORY = Join-Path $root 'acompanhamento'
    $state = Join-Path $env:ETL_PUBLICATION_TEST_DIRECTORY 'operational'
    [void][IO.Directory]::CreateDirectory((Join-Path $state 'reports'))
    Write-TestRecord "$env:ETL_PUBLICATION_TEST_DIRECTORY/historico-90-dias-settings.json" @{stateDirectory=$state; historyDays=90}
    Write-TestRecord "$state/checkpoint.json" @{payload=@{entities=@{}}}
    $id = [guid]::NewGuid()
    $cycle = $id.ToString('N')
    Write-TestRecord "$state/supervisor.json" @{cycle=[guid]::NewGuid().ToString('N'); phase='WAITING'}
    $raw = @{schema='PRODUCTION_REFRESH0766'; passed=$true; completedAt=[DateTimeOffset]::UtcNow.ToString('o'); historyOnly=$false; initialComplete=$true}
    Write-TestRecord "$state/reports/$cycle.json" $raw
    $saved = "$state/reports/$cycle.publication.json"
    $pendingMessage = 'Dados recebidos. Aguardando a confirmacao da publicacao no painel.'
    if ((& $monitor -Once) -notlike "*$pendingMessage*") { throw 'Monitor confundiu publicacao pendente com carga ainda nao iniciada.' }
    Write-TestRecord $saved @{ciclo=[guid]::NewGuid().ToString(); pendentes=0; auditoria=1}
    if ((& $monitor -Once) -notlike "*$pendingMessage*") { throw 'Monitor usou o recibo de outro lote.' }
    Write-TestRecord $saved @{ciclo=$id.ToString(); pendentes=0; auditoria=1}
    if ((& $monitor -Once) -notlike '*Dados recebidos e relatorios publicados.*') { throw 'Monitor nao reconheceu a publicacao atual.' }
    $raw.historyOnly=$true; $raw.initialComplete=$false
    Write-TestRecord "$state/reports/$cycle.json" $raw
    if ((& $monitor -Once) -like '*publica*') { throw 'Monitor anunciou publicacao em uma etapa intermediaria do historico.' }
    $raw.historyOnly=$false; $raw.initialComplete=$true
    Write-TestRecord "$state/reports/$cycle.json" $raw
    Write-TestRecord "$state/supervisor.json" @{cycle=$cycle; phase='WAITING'; nextEligibleAt=[DateTimeOffset]::Now.AddMinutes(60).ToString('o')}
    if ((& $monitor -Once) -notlike '*Proximo lote a partir*') { throw 'Monitor perdeu o horario da proxima busca.' }
    Write-Output 'Cinco casos de acompanhamento da publicacao passaram.'
} finally {
    $env:ETL_PUBLICATION_TEST_DIRECTORY = $storageBefore
    if (Test-Path -LiteralPath $root -PathType Container) {
        $fixture = Get-Item -LiteralPath $root -Force
        if ($fixture.Parent.FullName -ine $temporary -or $fixture.Name -cnotmatch '^etl-publication-test-[0-9a-f]{32}$' -or
            ($fixture.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Diretorio de teste inesperado.' }
        Remove-Item -LiteralPath $fixture.FullName -Recurse -Force
    }
}

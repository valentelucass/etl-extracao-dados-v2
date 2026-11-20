[CmdletBinding(DefaultParameterSetName='Historico')]
param(
    [Parameter(ParameterSetName='Verificar')][switch]$Verify,
    [Parameter(Mandatory,ParameterSetName='Continuo')][switch]$Continuo,
    [Parameter(ParameterSetName='Continuo')][ValidateRange(0,1000)][int]$Lotes=0
)

$ErrorActionPreference = 'Stop'
$storage = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'
$manifest = Join-Path $storage 'runtime-manifest-dashboard-fields-0818.json'
$runner = Join-Path $PSScriptRoot 'Invoke-ProductionRefresh0810.ps1'
$historySettings = Join-Path $storage 'historico-90-dias-settings.json'
& $runner -SettingsPath $historySettings -RuntimeManifestPath $manifest -HistoryOnly -Verify
if ($Verify) { return }
try {
    if ($Continuo) {
        & $runner -SettingsPath (Join-Path $storage 'operational-settings.json') -RuntimeManifestPath $manifest -Continuous -BatchLimit $Lotes
    } else {
        & $runner -SettingsPath $historySettings -RuntimeManifestPath $manifest -HistoryOnly
    }
} catch {
    if ($_.Exception.Message -cnotin @('REFRESH_BATCH_FAILED_STOP_PRESERVED', 'REFRESH_SUPERVISOR_STOP_PRESERVED')) { throw }
    $settings = Get-Content -LiteralPath $historySettings -Raw -Encoding UTF8 | ConvertFrom-Json
    $markerPath = Join-Path $settings.stateDirectory 'STOP.failure.json'
    if (!(Test-Path -LiteralPath $markerPath)) { throw }
    $marker = Get-Content -LiteralPath $markerPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($marker.cycle -cnotmatch '^[0-9a-f]{32}$') { throw }
    $reportPath = Join-Path $settings.stateDirectory ('reports\' + $marker.cycle + '.json')
    if (!(Test-Path -LiteralPath $reportPath)) { throw }
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.passed -and !(Test-Path -LiteralPath (Join-Path $settings.stateDirectory ('reports\'+$marker.cycle+'.publication.json')))) {
        throw "Os dados ja foram recebidos. A publicacao dos relatorios ficou pendente. Para retomar essa etapa, execute Resume-ProductionHistory.ps1 -PublicarRecebido. Detalhes em $reportPath"
    }
    $failure = $report.entities | Where-Object status -CEQ 'FAILED' | Select-Object -Last 1
    if ($failure.reason -ceq 'REFRESH_SQLTIMEOUTEXCEPTION') {
        throw "O banco demorou para responder em $($failure.entity). O lote parou. Detalhes em $reportPath"
    }
    throw "O lote parou. Detalhes em $reportPath"
}

[CmdletBinding()]
param([switch]$Verify)

$ErrorActionPreference = 'Stop'
$storage = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'
& (Join-Path $PSScriptRoot 'Invoke-ProductionRefresh0766.ps1') `
    -SettingsPath (Join-Path $storage 'historico-90-dias-settings.json') `
    -RuntimeManifestPath (Join-Path $storage 'runtime-manifest-dashboard-fields-0804.json') `
    -HistoryOnly -Verify:$Verify

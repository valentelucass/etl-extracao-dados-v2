@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "ETL_HISTORY_REPO=%~dp0"
set "ETL_HISTORY_VERIFY="
if /i "%~1"=="--verificar" set "ETL_HISTORY_VERIFY=1"
if not "%~1"=="" (
    if not defined ETL_HISTORY_VERIFY (
        echo Uso: CARGA_90_DIAS.bat [--verificar]
        exit /b 2
    )
)
echo Carga inicial do ETL_SISTEMA_V2 - ultimos 90 dias
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -Command "$ErrorActionPreference='Stop'; $root=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos\etl-extracao-dados-v2\atualizacao0766'; & (Join-Path $env:ETL_HISTORY_REPO 'scripts\operations\Invoke-ProductionRefresh0766.ps1') -SettingsPath (Join-Path $root 'historico-90-dias-settings.json') -RuntimeManifestPath (Join-Path $root 'runtime-manifest-dashboard-fields-0798.json') -HistoryOnly -Verify:($env:ETL_HISTORY_VERIFY -eq '1')"
exit /b %errorlevel%

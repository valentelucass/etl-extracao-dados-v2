@echo off
setlocal EnableExtensions DisableDelayedExpansion
if /i "%~1"=="--acompanhar" goto :acompanhar
if /i "%~1"=="--verificar" goto :verificar
if not "%~1"=="" (
    echo Uso: CARGA_90_DIAS.bat [--verificar ^| --acompanhar]
    exit /b 2
)
echo Carga inicial do ETL_SISTEMA_V2 - ultimos 90 dias
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -File "%~dp0scripts\operations\Start-ProductionHistory.ps1"
exit /b %errorlevel%

:verificar
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -File "%~dp0scripts\operations\Start-ProductionHistory.ps1" -Verify
exit /b %errorlevel%

:acompanhar
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -File "%~dp0scripts\operations\Watch-ProductionHistory.ps1"
exit /b %errorlevel%

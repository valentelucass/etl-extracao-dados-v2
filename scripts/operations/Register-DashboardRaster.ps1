[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$nome='Rodogarcia-ETL-Raster'
$exe=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$script=(Resolve-Path (Join-Path $PSScriptRoot 'Sync-DashboardRaster.ps1')).Path
$pwsh=(Get-Command pwsh.exe -CommandType Application).Source
$versao=& $pwsh -NoProfile -Command '$PSVersionTable.PSVersion.ToString()'
if([version]$versao -lt [version]'7.5'){throw 'A captura do Raster requer PowerShell 7.5 ou superior.'}
$argumentos='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "'+$script+'" -PowerShell7 "'+$pwsh+'" -Agendado'
$tempoMaximo=New-TimeSpan -Minutes 110
$opcoes=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit $tempoMaximo
$existente=Get-ScheduledTask -TaskName $nome -ErrorAction SilentlyContinue
if($existente){
    if($existente.Actions.Execute -ne $exe -or $existente.Actions.Arguments -ne $argumentos){throw 'A tarefa Raster existente tem uma configuracao diferente.'}
    if([Xml.XmlConvert]::ToTimeSpan($existente.Settings.ExecutionTimeLimit) -ne $tempoMaximo){
        $existente=Set-ScheduledTask -TaskName $nome -Settings $opcoes
    }
    return $existente | Select-Object TaskName,State
}
$acao=New-ScheduledTaskAction -Execute $exe -Argument $argumentos
$gatilho=New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval (New-TimeSpan -Hours 1)
$usuario=[Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal=New-ScheduledTaskPrincipal -UserId $usuario -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $nome -Action $acao -Trigger $gatilho -Principal $principal -Settings $opcoes -Description 'Atualiza viagens e paradas do Raster para os Horarios de Corte.' | Select-Object TaskName,State

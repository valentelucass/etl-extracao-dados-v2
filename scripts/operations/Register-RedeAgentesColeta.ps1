[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$nome = 'Rodogarcia-ETL-Agentes-Coleta'
$exe = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$script = (Resolve-Path (Join-Path $PSScriptRoot 'Sync-RedeAgentesColeta.ps1')).Path
$argsSync = '-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $script + '"'
$atual = Get-ScheduledTask -TaskName $nome -ErrorAction SilentlyContinue
if ($atual) {
    if ($atual.Actions.Execute -ne $exe -or $atual.Actions.Arguments -ne $argsSync) { throw 'Tarefa existente divergente.' }
    return $atual | Select-Object TaskName,State
}
$acao = New-ScheduledTaskAction -Execute $exe -Argument $argsSync
$gatilho = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(5) -RepetitionInterval (New-TimeSpan -Hours 1)
$usuario = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $usuario -LogonType Interactive -RunLevel Limited
$opts = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 45)
Register-ScheduledTask -TaskName $nome -Action $acao -Trigger $gatilho -Principal $principal -Settings $opts -Description 'Confere agentes de coleta da Rede Credenciada no relatório do ESL.' | Select-Object TaskName,State

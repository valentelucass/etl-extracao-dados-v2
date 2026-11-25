[CmdletBinding()] param()
$ErrorActionPreference='Stop'
$identidade=[Security.Principal.WindowsIdentity]::GetCurrent().Name
$nome='Rodogarcia-ETL-Retencao-Ocorrencias'
$worker=(Resolve-Path (Join-Path $PSScriptRoot 'Start-RetencaoOcorrencias.ps1')).Path
$exe=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$argumentos='-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy RemoteSigned -File "'+$worker+'"'
$actual=Get-ScheduledTask -TaskName $nome -ErrorAction SilentlyContinue
if ($actual -and ($actual.Actions.Execute -ne $exe -or $actual.Actions.Arguments -ne $argumentos)) {
    throw 'O nome da tarefa ja esta em uso.'
}
$acao=New-ScheduledTaskAction -Execute $exe -Argument $argumentos
$hora=New-ScheduledTaskTrigger -Once -At ([DateTime]::Today.AddMinutes(15)) -RepetitionInterval (New-TimeSpan -Hours 1)
$logon=New-ScheduledTaskTrigger -AtLogOn -User $identidade
$config=New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 1) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 5)
$usuario=New-ScheduledTaskPrincipal -UserId $identidade -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $nome -Action $acao -Trigger @($hora,$logon) -Settings $config -Principal $usuario -Description 'Retira dias vencidos de Ocorrencias conforme a regra no ETL_SISTEMA_V2.' -Force | Select-Object TaskName,State

[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$name = 'Rodogarcia-ETL-Parcelas-Recebiveis'
$script = (Resolve-Path (Join-Path $PSScriptRoot 'Sync-DashboardRecebiveis.ps1')).Path
$program = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$arguments = '-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $script + '"'
$task = Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
if ($task) {
    if ($task.Actions.Execute -ne $program -or $task.Actions.Arguments -ne $arguments) {
        throw 'Ja existe uma tarefa diferente com este nome.'
    }
    return $task | Select-Object TaskName,State
}
$action = New-ScheduledTaskAction -Execute $program -Argument $arguments
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Hours 1)
$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 45) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $name -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Atualiza e confere parcelas do ESL e a divisao dos valores entre os fretes.' | Select-Object TaskName,State

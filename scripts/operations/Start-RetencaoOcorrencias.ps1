[CmdletBinding()]
param([ValidateRange(1,32)][int]$MaxDias=12)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$executor=Join-Path $repo '../.runtime/memory-control/Executar-Batch.ps1'
$exe=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$worker=Join-Path $PSScriptRoot 'Invoke-RetencaoOcorrencias.ps1'
$arguments=@('-NoProfile','-NonInteractive','-ExecutionPolicy','RemoteSigned','-File',$worker,'-MaxDias',[string]$MaxDias)
& $executor -Executable $exe -ArgumentList $arguments -WorkingDirectory $repo -LimitMiB 512 -MaxMinutes 45
exit $LASTEXITCODE

[CmdletBinding()]
param([ValidateRange(1,32)][int]$MaxDias=12)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
Import-Module (Join-Path $PSScriptRoot 'RetentionWorker.psm1')
Initialize-RetentionArchive
$dir = Get-RetentionArchiveDirectory
$credential = Import-Clixml -LiteralPath (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$cn = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-Retencao-Ocorrencias'
try {
    $cn.Open()
    Invoke-RetentionQueue $cn $dir $MaxDias
} finally { $cn.Dispose(); $credential.Password.Dispose() }

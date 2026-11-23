[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cred=Get-ProjetosSqlAdminCredential
$con=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Instalar-Raster'
try {
    $con.Open()
    $tx=$con.BeginTransaction()
    $cmd=$con.CreateCommand()
    $cmd.Transaction=$tx
    $cmd.CommandTimeout=120
    $sql=[IO.File]::ReadAllText((Join-Path $repo 'database/production/publication/064_dashboard_atualizacao_raster.sql'))
    foreach($batch in ($sql -split '(?im)^GO\s*$')){
        if($batch.Trim()){$cmd.CommandText=$batch;$null=$cmd.ExecuteNonQuery()}
    }
    if($Apply){$tx.Commit();Write-Output 'Recebimento do Raster instalado.'}
    else {$tx.Rollback();Write-Output 'Instalacao do Raster ensaiada em transacao revertida.'}
} finally {$con.Dispose();$cred.Password.Dispose()}

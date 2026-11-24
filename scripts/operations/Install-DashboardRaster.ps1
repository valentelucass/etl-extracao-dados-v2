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
    $visao='070_dashboard_raster_vigente_entre_dias.sql'
    foreach($arquivo in @('064_dashboard_atualizacao_raster.sql','069_dashboard_raster_entre_dias.sql',$visao)){
        $sql=[IO.File]::ReadAllText((Join-Path $repo ('database/production/publication/'+$arquivo)))
        foreach($batch in ($sql -split '(?im)^GO\s*$')){
            if($batch.Trim()){$cmd.CommandText=$batch;$null=$cmd.ExecuteNonQuery()}
        }
    }
    $sha=[Security.Cryptography.SHA256]::Create()
    try {$hash=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql.Replace("`r`n","`n")))).Replace('-','')}
    finally {$sha.Dispose()}
    $cmd.CommandText=@'
IF EXISTS(SELECT 1 FROM ctl.production_installation_step WHERE step_id='070-dashboard-raster-vigente-entre-dias' AND source_sha256<>@hash)
    THROW 52223,'Versao da visao Raster divergente.',1;
IF NOT EXISTS(SELECT 1 FROM ctl.production_installation_step WHERE step_id='070-dashboard-raster-vigente-entre-dias')
    INSERT ctl.production_installation_step(step_id,source_name,source_sha256)
    VALUES('070-dashboard-raster-vigente-entre-dias','database/production/publication/070_dashboard_raster_vigente_entre_dias.sql',@hash);
'@
    $cmd.Parameters.Add('@hash',[Data.SqlDbType]::VarChar,64).Value=$hash
    $null=$cmd.ExecuteNonQuery()
    if($Apply){$tx.Commit();Write-Output 'Recebimento do Raster instalado.'}
    else {$tx.Rollback();Write-Output 'Instalacao do Raster ensaiada em transacao revertida.'}
} finally {$con.Dispose();$cred.Password.Dispose()}

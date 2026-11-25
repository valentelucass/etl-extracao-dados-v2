[CmdletBinding()]
param([switch]$Aplicar)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$pacotes = [ordered]@{
    '073-retencao-ocorrencias'='database/production/publication/073_retencao_ocorrencias.sql'
    '073b-retencao-revisao'='database/production/publication/073b_retencao_revisao.sql'
}
$sha = [Security.Cryptography.SHA256]::Create()
$cred = Get-ProjetosSqlAdminCredential
$cn = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Instalar-Retencao'
try {
    $cn.Open()
    $tx = $cn.BeginTransaction()
    $cmd = $cn.CreateCommand()
    $cmd.Transaction = $tx
    $cmd.CommandTimeout = 30
    $cmd.CommandText = @'
SET XACT_ABORT ON; SET LOCK_TIMEOUT 4500;
IF DB_NAME()<>'ETL_SISTEMA_V2' THROW 52300,'Base incorreta.',1;
DECLARE @lk int;
EXEC @lk=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=4500;
IF @lk<0 THROW 52302,'Publicacao ocupada.',1;

'@
    [void]$cmd.ExecuteNonQuery()
    $novos = 0
    foreach ($id in $pacotes.Keys) {
        $caminho = $pacotes[$id]
        $sql = [IO.File]::ReadAllText((Join-Path $repo $caminho)).Replace([Environment]::NewLine,[string][char]10)
        $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-','')
        $cmd.Parameters.Clear()
        $cmd.CommandText = 'SELECT source_sha256 FROM ctl.production_installation_step WITH(UPDLOCK,HOLDLOCK) WHERE step_id=@id'
        [void]$cmd.Parameters.AddWithValue('@id',$id)
        $anterior = $cmd.ExecuteScalar()
        if ($anterior -and $anterior -ne $hash) { throw 'Pacote difere do instalado.' }
        if ($anterior) { continue }
        $novos++
        foreach ($lote in ($sql -split '(?im)^GO\s*$')) {
            if ($lote.Trim()) { $cmd.CommandText = $lote; [void]$cmd.ExecuteNonQuery() }
        }
        $cmd.CommandText = "INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@id,@p,@h)"
        [void]$cmd.Parameters.AddWithValue('@p',$caminho)
        [void]$cmd.Parameters.AddWithValue('@h',$hash)
        [void]$cmd.ExecuteNonQuery()
    }
    $cmd.CommandText = 'EXEC ctl.usp_jujuba_retencao_catalogo'
    $catalogo = $cmd.ExecuteScalar() | ConvertFrom-Json
    if (-not $catalogo.available -or $catalogo.revision -cnotmatch '^[0-9A-F]{16}$' -or @($catalogo.entities | Where-Object supported).Count -ne 1) { throw 'Contrato inesperado.' }
    if ($Aplicar) { $tx.Commit() } else { $tx.Rollback() }
    [pscustomobject]@{aplicado=[bool]$Aplicar; pacotesNovos=$novos; entidades=$catalogo.entities.Count}
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($tx) { $tx.Dispose() }
    $cn.Dispose()
    $cred.Password.Dispose()
    $sha.Dispose()
}

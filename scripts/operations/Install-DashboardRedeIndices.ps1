[CmdletBinding()]
param([switch]$Aplicar)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$fonte = 'database/production/publication/065_dashboard_indices_rede.sql'
$sql = [IO.File]::ReadAllText((Join-Path $repo $fonte)).Replace([string][char]13,'')
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-','') }
finally { $sha.Dispose() }
$credencial = Get-ProjetosSqlAdminCredential
$conexao = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credencial -ApplicationName 'ETL-Indices-Rede'
try {
    $conexao.Open()
    $transacao = $conexao.BeginTransaction()
    $cmd = $conexao.CreateCommand()
    $cmd.Transaction = $transacao
    $cmd.CommandTimeout = 90
    $null = $cmd.Parameters.AddWithValue('@fonte',$fonte)
    $null = $cmd.Parameters.AddWithValue('@hash',$hash)
    $cmd.CommandText = @'
SET XACT_ABORT ON;
SET DEADLOCK_PRIORITY LOW;
SET LOCK_TIMEOUT 5000;
SET ANSI_NULLS, QUOTED_IDENTIFIER, ANSI_PADDING, ANSI_WARNINGS, CONCAT_NULL_YIELDS_NULL, ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes', @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
IF @lock<0 THROW 52200,'Publicacao em andamento.',1;
SELECT CASE WHEN source_name=@fonte THEN source_sha256 ELSE 'DIVERGENTE' END
FROM ctl.production_installation_step WHERE step_id='065-dashboard-indices-rede';
'@
    $registro = $cmd.ExecuteScalar()
    if ($registro -and $registro -cne $hash) { throw 'Assinatura do pacote de indices divergente.' }
    if (-not $registro) {
        $cmd.CommandText = $sql
        $null = $cmd.ExecuteNonQuery()
        $cmd.CommandText = "INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('065-dashboard-indices-rede',@fonte,@hash)"
        $null = $cmd.ExecuteNonQuery()
    }
    $cmd.CommandText = @'
SELECT COUNT(*) FROM sys.indexes
WHERE is_disabled=0 AND ((object_id=OBJECT_ID('core.dashboard_manifestos_leitura') AND name='IX_dashboard_rede_manifesto')
   OR (object_id=OBJECT_ID('core.dashboard_performance_ocorrencias') AND name='IX_dashboard_rede_retirada' AND has_filter=1));
'@
    if ($cmd.ExecuteScalar() -ne 2) { throw 'Indices de leitura ausentes.' }
    if ($Aplicar) { $transacao.Commit() } else { $transacao.Rollback() }
    [pscustomobject]@{aplicado=[bool]$Aplicar; jaInstalado=[bool]$registro; sha256=$hash}
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($transacao) { $transacao.Dispose() }
    $conexao.Dispose()
    $credencial.Password.Dispose()
}

[CmdletBinding()]
param([switch]$Aplicar)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$caminho = 'database/production/publication/059_dashboard_performance_ocorrencias.sql'
$conteudo = [IO.File]::ReadAllText((Join-Path $repo $caminho)).Replace("`r`n", "`n")
$sha = [Security.Cryptography.SHA256]::Create()
$hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($conteudo))).Replace('-', '')
$credencial = Get-ProjetosSqlAdminCredential
$conexao = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credencial -ApplicationName 'ETL-Instalar-Performance-Ocorrencias'
try {
    $conexao.Open()
    $transacao = $conexao.BeginTransaction()
    $cmd = $conexao.CreateCommand()
    $cmd.Transaction = $transacao
    $cmd.CommandTimeout = 600
    $cmd.CommandText = @'
SET XACT_ABORT ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52176, 'Base incorreta.', 1;
DECLARE @lock int;
EXEC @lock = sys.sp_getapplock @Resource = N'dashboard:fatos-fretes', @LockMode = 'Exclusive',
                              @LockOwner = 'Transaction', @LockTimeout = 5000;
IF @lock < 0 THROW 52177, 'Publicacao em andamento.', 1;
SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id='059-dashboard-performance-ocorrencias';
'@
    $registrado = $cmd.ExecuteScalar()
    if ($registrado -and $registrado -cne $hash) { throw 'Pacote de ocorrencias diferente da instalacao.' }
    if (-not $registrado) {
        foreach ($lote in ($conteudo -split '(?im)^GO\s*$')) {
            if ($lote.Trim()) {
                $cmd.CommandText = $lote
                $null = $cmd.ExecuteNonQuery()
            }
        }
        $cmd.CommandText = "INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('059-dashboard-performance-ocorrencias',@caminho,@hash)"
        $null = $cmd.Parameters.AddWithValue('@caminho', $caminho)
        $null = $cmd.Parameters.AddWithValue('@hash', $hash)
        $null = $cmd.ExecuteNonQuery()
        $cmd.Parameters.Clear()
    }

    $cmd.CommandText = @'
IF CHARINDEX(N'EXEC core.usp_dashboard_preparar_ocorrencias;',
             ISNULL(OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes')),N'')) = 0
    THROW 52178, 'Atualizacao automatica de ocorrencias ausente.', 1;
EXEC core.usp_dashboard_preparar_ocorrencias;
EXECUTE AS USER=N'usuario_etl';
SELECT COUNT_BIG(*) FROM pub.dashboard_performance_ocorrencias;
REVERT;
'@
    $linhas = $cmd.ExecuteScalar()
    $cmd.CommandText = @'
IF EXISTS (
    SELECT p.escopo,p.data_particao,r.row_count
    FROM ctl.dashboard_performance_ocorrencias_particao p
    JOIN ctl.production_ingestion_run r ON r.run_id=p.captura
    WHERE r.row_count>0
    EXCEPT
    SELECT escopo,data_particao,COUNT(*) FROM pub.dashboard_performance_ocorrencias GROUP BY escopo,data_particao
) THROW 52179, 'Divergencia na quantidade publicada por dia.', 1;
SELECT COUNT(*) FROM ctl.dashboard_performance_ocorrencias_particao;
'@
    $dias = $cmd.ExecuteScalar()
    if ($Aplicar) { $transacao.Commit() } else { $transacao.Rollback() }
    [pscustomobject]@{aplicado=[bool]$Aplicar; jaInstalado=[bool]$registrado; particoes=$dias; ocorrencias=$linhas}
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($transacao) { $transacao.Dispose() }
    $conexao.Dispose()
    $credencial.Password.Dispose()
    $sha.Dispose()
}

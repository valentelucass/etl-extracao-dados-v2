[CmdletBinding(DefaultParameterSetName='Rede')]
param([switch]$Aplicar,
      [Parameter(ParameterSetName='Fretes')][switch]$Fretes,
      [Parameter(ParameterSetName='Performance')][switch]$Performance)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$fonte = 'database/production/publication/065_dashboard_indices_rede.sql'
$passo = '065-dashboard-indices-rede'
if ($Fretes) {
    $fonte = 'database/production/publication/067_dashboard_indices_frete_operacao.sql'
    $passo = '067-dashboard-indices-frete-operacao'
} elseif ($Performance) {
    $fonte = 'database/production/publication/072_dashboard_comprovante_performance.sql'
    $passo = '072-dashboard-comprovante-performance'
}
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
    $null = $cmd.Parameters.AddWithValue('@passo',$passo)
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
FROM ctl.production_installation_step WHERE step_id=@passo;
'@
    $registro = $cmd.ExecuteScalar()
    if ($registro -and $registro -cne $hash) { throw 'Assinatura do pacote de indices divergente.' }
    if (-not $registro) {
        foreach ($lote in ($sql -split '(?im)^GO[ \t]*$')) {
            if ($lote.Trim()) {
                $cmd.CommandText = $lote
                $null = $cmd.ExecuteNonQuery()
            }
        }
        $cmd.CommandText = "INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@passo,@fonte,@hash)"
        $null = $cmd.ExecuteNonQuery()
    }
    $cmd.CommandText = @'
SELECT COUNT(*) FROM sys.indexes
WHERE is_disabled=0 AND ((object_id=OBJECT_ID('core.dashboard_manifestos_leitura') AND name='IX_dashboard_rede_manifesto')
   OR (object_id=OBJECT_ID('core.dashboard_performance_ocorrencias') AND name='IX_dashboard_rede_retirada' AND has_filter=1));
'@
    if ($Fretes) {
        $cmd.CommandText = "SELECT COUNT(*) FROM sys.indexes WHERE is_disabled=0 AND object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND name IN ('IX_detalhe_minuta_operacao','IX_detalhe_pagador_operacao')"
    }
    if ($Performance) {
        $cmd.CommandText = @'
SELECT COUNT(*) FROM sys.index_columns ic
JOIN sys.indexes i ON i.object_id=ic.object_id AND i.index_id=ic.index_id
JOIN sys.computed_columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
WHERE i.object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND i.name='IX_dashboard_entregas_previsao'
  AND i.is_disabled=0 AND ic.is_included_column=1 AND c.name='data_comprovante_entrega' AND c.is_persisted=1
  AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('pub.vw_fretes_powerbi_pronta')
    AND name=N'Comprovante de Entrega' AND system_type_id=TYPE_ID('datetimeoffset'));
'@
        if ($cmd.ExecuteScalar() -ne 1) { throw 'Leitura de comprovantes da Performance incompleta.' }
    } elseif ($cmd.ExecuteScalar() -ne 2) { throw 'Indices de leitura ausentes.' }
    if ($Fretes) {
        $cmd.Parameters.Clear()
        $cmd.CommandText = [IO.File]::ReadAllText((Join-Path $repo 'scripts/validation/dashboard-indices-frete-operacao.sql'))
        $null = $cmd.ExecuteNonQuery()
        $view = [IO.File]::ReadAllText((Join-Path $repo 'database/production/publication/068_dashboard_fretes_rede.sql')).Replace([string][char]13,'')
        $cmd.CommandText = "SELECT OBJECT_DEFINITION(OBJECT_ID('pub.dashboard_fretes_rede','V'));"
        $instalada = $cmd.ExecuteScalar()
        if (!$instalada -or $instalada -is [DBNull]) {
            $cmd.CommandText = $view
            $null = $cmd.ExecuteNonQuery()
        } elseif (([string]$instalada).Replace([string][char]13,'').Split(@(' AS'),2,[StringSplitOptions]::None)[-1].Trim() -cne $view.Split(@(' AS'),2,[StringSplitOptions]::None)[-1].Trim()) {
            throw 'A definicao de fretes da Rede difere da versao instalada.'
        }
        $cmd.CommandText = "EXECUTE AS USER=N'usuario_etl'; SELECT TOP(0) manifesto_entrega_consulta FROM pub.dashboard_fretes_rede; REVERT;"
        $null = $cmd.ExecuteNonQuery()
    }
    if ($Aplicar) { $transacao.Commit() } else { $transacao.Rollback() }
    [pscustomobject]@{aplicado=[bool]$Aplicar; jaInstalado=[bool]$registro; sha256=$hash}
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($transacao) { $transacao.Dispose() }
    $conexao.Dispose()
    $credencial.Password.Dispose()
}

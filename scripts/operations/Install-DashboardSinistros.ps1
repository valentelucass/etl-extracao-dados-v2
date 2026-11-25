[CmdletBinding()]
param([switch]$Aplicar)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1') -DisableNameChecking
$fonte = 'database/production/publication/074_dashboard_sinistros.sql'
$passo = '074-dashboard-sinistros'
$sql = [IO.File]::ReadAllText((Join-Path $repo $fonte)).Replace([string][char]13, '')
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-', '') }
finally { $sha.Dispose() }

$credencial = Get-ProjetosSqlAdminCredential
$conexao = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credencial -ApplicationName 'ETL-Base-Sinistros'
try {
    $conexao.Open()
    $tx = $conexao.BeginTransaction()
    $cmd = $conexao.CreateCommand()
    $cmd.Transaction = $tx
    $cmd.CommandTimeout = 90
    $null = $cmd.Parameters.AddWithValue('@passo', $passo)
    $null = $cmd.Parameters.AddWithValue('@fonte', $fonte)
    $null = $cmd.Parameters.AddWithValue('@hash', $hash)
    $cmd.CommandText = @'
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
SET ANSI_NULLS, QUOTED_IDENTIFIER ON;
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52301, 'Banco incorreto.', 1;
DECLARE @lock int;
EXEC @lock = sys.sp_getapplock @Resource=N'pub:dashboard_sinistros', @LockMode='Exclusive',
                              @LockOwner='Transaction', @LockTimeout=5000;
IF @lock < 0 THROW 52302, 'Outra instalacao da base de sinistros em andamento.', 1;
SELECT CASE WHEN source_name=@fonte THEN source_sha256 ELSE 'DIVERGENTE' END
FROM ctl.production_installation_step WHERE step_id=@passo;
'@
    $registrado = $cmd.ExecuteScalar()
    if ($registrado -and $registrado -ine $hash) { throw 'O arquivo difere da assinatura registrada para esta instalacao.' }
    if (-not $registrado) {
        $loteSql = $cmd.Parameters.Add('@ddl', [Data.SqlDbType]::NVarChar, -1)
        foreach ($lote in ($sql -split '(?im)^GO\s*$')) {
            if ($lote.Trim()) {
                $loteSql.Value = $lote
                $cmd.CommandText = 'EXEC sys.sp_executesql @ddl'
                $null = $cmd.ExecuteNonQuery()
            }
        }
        $cmd.Parameters.Remove($loteSql)
        $cmd.CommandText = 'INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@passo,@fonte,@hash)'
        $null = $cmd.ExecuteNonQuery()
    }
    $cmd.CommandText = [IO.File]::ReadAllText((Join-Path $repo 'scripts/validation/dashboard-sinistros-ocorrencias.sql'))
    $regressao = $cmd.ExecuteScalar() | ConvertFrom-Json
    if ($Aplicar) { $tx.Commit() } else { $tx.Rollback() }
    [pscustomobject]@{ aplicado=[bool]$Aplicar; jaInstalado=[bool]$registrado; regressao=$regressao }
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($tx) { $tx.Dispose() }
    $conexao.Dispose()
    $credencial.Password.Dispose()
}

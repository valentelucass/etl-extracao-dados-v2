[CmdletBinding()]
param([switch]$Aplicar,[switch]$PrepararArquivos)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$pacotes = @(
    @{ arquivo='061_dashboard_rede_credenciada_comprovantes.sql'; passo='061-dashboard-comprovantes';
       views=@('pub.rede_credenciada_arquivos','pub.rede_credenciada_cobertura') },
    @{ arquivo='062_customer_service_cobertura_ocorrencias.sql'; passo='062-customer-service-cobertura-ocorrencias';
       views=@('pub.customer_service_cobertura_ocorrencias') }
)

function Get-DefinicaoView([string]$texto) {
    $padrao = '(?is)^\s*(?:--[^\n]*\n\s*)*CREATE\s+(?:OR\s+ALTER\s+)?VIEW\s+(pub\.\w+)\s+AS\s+(SELECT\b.*?;)\s*$'
    $resultado = [regex]::Match($texto.Replace([string][char]13,''),$padrao)
    if (-not $resultado.Success) { throw 'Esperava uma unica definicao de view por lote.' }
    return @{ nome=$resultado.Groups[1].Value; corpo=$resultado.Groups[2].Value.Trim() }
}

function Get-HashFonte([string]$texto) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($texto))).Replace('-','') }
    finally { $sha.Dispose() }
}

$credencial = Get-ProjetosSqlAdminCredential
$conexao = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credencial -ApplicationName 'ETL-Instalar-Customer-Service'
$resumo = @()
try {
    $conexao.Open()
    $transacao = $conexao.BeginTransaction()
    $cmd = $conexao.CreateCommand()
    $cmd.Transaction = $transacao
    $cmd.CommandTimeout = 90
    $cmd.CommandText = @'
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52180,'Base incorreta.',1;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'ETL_V2_DASHBOARD_COMPATIBILITY_VIEWS',
     @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
IF @lock<0 THROW 52181,'Outra instalacao de consultas esta em andamento.',1;
'@
    $null = $cmd.ExecuteNonQuery()
    $caminhoPreparo = 'database/production/publication/066_dashboard_rede_arquivos_preparados.sql'
    $preparo = [IO.File]::ReadAllText((Join-Path $repo $caminhoPreparo)).Replace([string][char]13,'')
    $hashPreparo = Get-HashFonte $preparo
    $lotesPreparo = @($preparo -split '(?im)^GO[ \t]*$' | Where-Object { $_.Trim() })
    $viewPreparo = Get-DefinicaoView ($lotesPreparo | Where-Object { $_ -match 'CREATE OR ALTER VIEW pub\.rede_credenciada_arquivos AS' })
    $cmd.CommandText = "SELECT CASE WHEN source_name=@fonte THEN source_sha256 ELSE 'DIVERGENTE' END FROM ctl.production_installation_step WHERE step_id='066-dashboard-arquivos-preparados'"
    $null = $cmd.Parameters.AddWithValue('@fonte',$caminhoPreparo)
    $registroPreparo = $cmd.ExecuteScalar()
    if ($registroPreparo -and $registroPreparo -cne $hashPreparo) { throw 'Pacote de arquivamentos divergente.' }
    foreach ($pacote in $pacotes) {
        $caminho = 'database/production/publication/' + $pacote.arquivo
        $conteudo = [IO.File]::ReadAllText((Join-Path $repo $caminho)).Replace([string][char]13,'')
        $hash = Get-HashFonte $conteudo
        $lotes = @($conteudo -split '(?im)^GO[ \t]*$' | Where-Object { $_.Trim() })
        if ($lotes.Count -ne $pacote.views.Count) { throw "Numero de views divergente em $caminho." }
        $cmd.Parameters.Clear()
        $null = $cmd.Parameters.AddWithValue('@passo',$pacote.passo)
        $null = $cmd.Parameters.AddWithValue('@caminho',$caminho)
        $cmd.CommandText = 'SELECT CASE WHEN source_name=@caminho THEN source_sha256 ELSE N''DIVERGENTE'' END FROM ctl.production_installation_step WHERE step_id=@passo;'
        $registrado = $cmd.ExecuteScalar()
        if ($registrado -and $registrado -cne $hash) { throw "Pacote registrado com outra origem ou outro hash: $caminho." }
        $nome = $cmd.Parameters.Add('@view',[Data.SqlDbType]::NVarChar,256)
        for ($i=0; $i -lt $lotes.Count; $i++) {
            $fonte = Get-DefinicaoView $lotes[$i]
            if ($registroPreparo -and $fonte.nome -ceq $viewPreparo.nome) { $fonte = $viewPreparo }
            $nome.Value = $pacote.views[$i]
            if ($fonte.nome -cne $nome.Value) { throw "View inesperada em $caminho." }
            $cmd.CommandText = 'SELECT OBJECT_ID(@view);'
            $objeto = $cmd.ExecuteScalar()
            $existe = $null -ne $objeto -and $objeto -isnot [DBNull]
            if ($registrado -and -not $existe) { throw "View registrada mas ausente: $($nome.Value)." }
            if (-not $registrado -and $existe) { throw "View existente sem registro: $($nome.Value)." }
            if (-not $existe) {
                $ddl = $conexao.CreateCommand()
                try {
                    $ddl.Transaction = $transacao
                    $ddl.CommandText = $lotes[$i]
                    $null = $ddl.ExecuteNonQuery()
                } finally { $ddl.Dispose() }
            }
            $cmd.CommandText = "SELECT OBJECT_DEFINITION(OBJECT_ID(@view,N'V'));"
            $publicada = Get-DefinicaoView ([string]$cmd.ExecuteScalar())
            if ($fonte.nome -cne $publicada.nome -or $fonte.corpo -cne $publicada.corpo) {
                throw "Definicao da view diverge da fonte: $($nome.Value)."
            }
        }
        if (-not $registrado) {
            $null = $cmd.Parameters.AddWithValue('@hash',$hash)
            $cmd.CommandText = 'INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@passo,@caminho,@hash);'
            $null = $cmd.ExecuteNonQuery()
        }
        $resumo += [pscustomobject]@{passo=$pacote.passo; sha256=$hash; jaInstalado=[bool]$registrado}
    }
    $cmd.Parameters.Clear()
    if ($PrepararArquivos) {
        $cmd.CommandText = @'
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes', @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
IF @lock<0 THROW 52203,'Publicacao em andamento.',1;
'@
        $null = $cmd.ExecuteNonQuery()
        if (-not $registroPreparo) {
            foreach ($lote in $lotesPreparo) {
                $cmd.CommandText = $lote
                $null = $cmd.ExecuteNonQuery()
            }
            $cmd.CommandText = "INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('066-dashboard-arquivos-preparados',@fonte,@hash)"
            $null = $cmd.Parameters.AddWithValue('@fonte',$caminhoPreparo)
            $null = $cmd.Parameters.AddWithValue('@hash',$hashPreparo)
            $null = $cmd.ExecuteNonQuery()
        }
        $cmd.CommandText = [IO.File]::ReadAllText((Join-Path $repo 'scripts/validation/dashboard-rede-arquivamentos.sql'))
        $null = $cmd.ExecuteNonQuery()
        $resumo += [pscustomobject]@{passo='066-dashboard-arquivos-preparados'; sha256=$hashPreparo; jaInstalado=[bool]$registroPreparo}
    }
    $cmd.CommandText = @'
EXECUTE AS USER=N'usuario_etl';
BEGIN TRY
    SELECT (SELECT COUNT_BIG(*) FROM pub.rede_credenciada_arquivos) AS minutas_arquivadas,
           (SELECT COUNT_BIG(*) FROM pub.rede_credenciada_cobertura WHERE entidade='COMPROVANTES_ENTREGA') AS dias_comprovantes,
           (SELECT COUNT_BIG(*) FROM pub.customer_service_cobertura_ocorrencias) AS dias_ocorrencias;
    REVERT;
END TRY
BEGIN CATCH
    REVERT;
    THROW;
END CATCH;
'@
    $leitor = $cmd.ExecuteReader()
    try {
        if (-not $leitor.Read()) { throw 'Leitura pelo usuario do painel sem resultado.' }
        $contagens = [pscustomobject]@{
            minutasArquivadas=$leitor.GetInt64(0); diasComprovantes=$leitor.GetInt64(1); diasOcorrencias=$leitor.GetInt64(2)
        }
        $null = $leitor.NextResult()
    } finally { $leitor.Dispose() }
    if ($Aplicar) { $transacao.Commit() } else { $transacao.Rollback() }
    [pscustomobject]@{aplicado=[bool]$Aplicar; pacotes=$resumo; contagens=$contagens} | ConvertTo-Json -Depth 4
} finally {
    if ($cmd) { $cmd.Dispose() }
    if ($transacao) { $transacao.Dispose() }
    $conexao.Dispose()
    $credencial.Password.Dispose()
}

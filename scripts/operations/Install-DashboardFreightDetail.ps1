[CmdletBinding()]
param(
    [ValidateSet('Verificar','Aplicar','Reverter')][string]$Acao='Verificar',
    [ValidatePattern('^(ETL_SISTEMA_V2|JUJUBA_VALIDACAO_ETL_[0-9]{8}_[0-9]{2})$')][string]$Banco='ETL_SISTEMA_V2'
)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$arquivo=Join-Path $repo 'database/production/publication/058_dashboard_detalhe_incremental.sql'
$nome='core.usp_dashboard_preparar_leituras_operacionais'
$objetoFretes='core.dashboard_fretes_publicacao_preparada'
$objetoDetalhe='core.dashboard_detalhe_fretes_entrada'
$selecao='core.usp_dashboard_detalhe_calcular_selecao'
$integral='core.usp_dashboard_detalhe_calcular_integral'
$sha=[Security.Cryptography.SHA256]::Create()
function Hash-Texto([string]$texto) {
    [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::Unicode.GetBytes($texto.Replace("`r",'')))).Replace('-','')
}
function Executar([string]$texto,[hashtable]$valores=@{},[switch]$Escalar) {
    $cmd=$conexao.CreateCommand()
    try {
        $cmd.Transaction=$transacao; $cmd.CommandText=$texto; $cmd.CommandTimeout=35
        foreach($chave in $valores.Keys){$p=$cmd.Parameters.Add($chave,[Data.SqlDbType]::NVarChar,-1);$p.Value=$valores[$chave]}
        if($Escalar){return $cmd.ExecuteScalar()}
        [void]$cmd.ExecuteNonQuery()
    } finally {$cmd.Dispose()}
}
function Definicao([string]$objeto) {
    [string](Executar 'SELECT OBJECT_DEFINITION(OBJECT_ID(@objeto));' @{'@objeto'=$objeto} -Escalar)
}
function Substituir([string]$texto,[string]$antes,[string]$depois) {
    $i=$texto.IndexOf($antes,[StringComparison]::Ordinal)
    if($i -lt 0 -or $i -ne $texto.LastIndexOf($antes,[StringComparison]::Ordinal)){throw 'Trecho do contrato ausente ou repetido.'}
    $texto.Remove($i,$antes.Length).Insert($i,$depois)
}
function Recriar-Publicador([string]$texto) {
    $i=$texto.IndexOf($nome,[StringComparison]::Ordinal)
    if($i -lt 0 -or $texto.Substring(0,$i).Trim() -notmatch '^(CREATE(\s+OR\s+ALTER)?|ALTER)\s+PROCEDURE$'){throw 'Cabeçalho do publicador divergente.'}
    Executar ('ALTER PROCEDURE '+$texto.Substring($i))
}
$credencial=Get-ProjetosSqlAdminCredential
$conexao=New-ProjetosSqlConnection -Database $Banco -Credential $credencial -ApplicationName 'ETL-Instalar-Detalhe-Fretes'
$transacao=$null
try {
    $conexao.Open();$transacao=$conexao.BeginTransaction()
    Executar 'SET XACT_ABORT ON; SET DEADLOCK_PRIORITY LOW; SET LOCK_TIMEOUT 2500; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;'
    if($Banco -cne (Executar 'SELECT DB_NAME()' -Escalar)){throw 'Destino divergente.'}
    if($Banco -ne 'ETL_SISTEMA_V2' -and (Executar 'SELECT user_access FROM sys.databases WHERE database_id=DB_ID()' -Escalar) -ne 2){throw 'A cópia de teste deve ter acesso restrito.'}
    $lock=Executar "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=1500; SELECT @r;" -Escalar
    if($lock -lt 0){throw 'Publicação em andamento. Repita após o encerramento do lote.'}
    $pacote=[IO.File]::ReadAllText($arquivo)
    $assinatura=Hash-Texto $pacote
    $modulo=Definicao $nome
    $instalado=(Executar "SELECT IIF(OBJECT_ID(N'ctl.dashboard_detalhe_instalacao',N'U') IS NULL,0,1)" -Escalar) -eq 1
    if($instalado){
        $valido=Executar "SELECT COUNT(*) FROM ctl.dashboard_detalhe_instalacao WHERE revisao=58 AND pacote=CONVERT(binary(32),@pacote,2) AND assinatura_instalada=HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID(@nome)));" @{'@pacote'=$assinatura;'@nome'=$nome} -Escalar
        if($valido -ne 1){throw 'O pacote ou o publicador foi alterado após a instalação.'}
        if($Acao -eq 'Reverter'){
            $anterior=Executar 'SELECT modulo_anterior FROM ctl.dashboard_detalhe_instalacao WHERE revisao=58' -Escalar
            Recriar-Publicador $anterior
            # A versão anterior volta a ler todos os dados recebidos, inclusive os posteriores à instalação.
            Executar 'DROP PROCEDURE core.usp_dashboard_detalhe_incremental,core.usp_dashboard_detalhe_calcular_selecao,core.usp_dashboard_detalhe_calcular_integral; DROP FUNCTION core.fn_dashboard_detalhe_compativel; DROP TABLE ctl.dashboard_detalhe_frete_estado,ctl.dashboard_detalhe_instalacao;'
        } elseif((Executar 'SELECT core.fn_dashboard_detalhe_compativel()' -Escalar) -ne 1){throw 'O contrato do relatório mudou.'}
    } elseif($Acao -ne 'Reverter'){
        $fretes=(Definicao $objetoFretes).Replace("`r",'')
        $detalhe=(Definicao $objetoDetalhe).Replace("`r",'')
        $original=$modulo.Replace("`r",'')
        # A lógica das 123 colunas vem das views que o próprio relatório utiliza.
        if((Hash-Texto $fretes) -ne '7CC734EDDDF3E503FF793F58EBBFCF311A58D54267DB98FB1E2DB9C3D5CA13C4' -or
           (Hash-Texto $detalhe) -ne '1E24A2F5169AF46589CC348786F0024C58FB637F7D6C65C0776B5DBC3AD89C8E'){throw 'Versão das views diferente da validada.'}
        # ALTER e CREATE têm o mesmo corpo depois da reversão.
        $corpo=$original.Substring($original.IndexOf($nome,[StringComparison]::Ordinal))
        if((Hash-Texto $corpo) -ne '4667901C3DE32130B633BD84BA8132A1E01AA7993A7876A680DC7866B1C8403B'){throw 'Versão do publicador diferente da validada.'}
        $antigo=[regex]::Match($original,'(?m)^SELECT (?<colunas>[^\r\n]+) INTO #detalhe FROM core\.dashboard_detalhe_fretes_entrada OPTION\(MAXDOP 1,MAX_GRANT_PERCENT=2\);\nDELETE FROM core\.dashboard_detalhe_fretes_leitura;\nINSERT core\.dashboard_detalhe_fretes_leitura SELECT \* FROM #detalhe OPTION\(MAXDOP 1\);')
        if(!$antigo.Success){throw 'Gravação original do detalhe ausente.'}
        $colunas=$antigo.Groups['colunas'].Value
        if((Executar "SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND is_computed=0" -Escalar) -ne 123){throw 'Quantidade de colunas diferente da validada.'}
        if((Executar "SELECT COUNT(*) FROM sys.objects WHERE object_id IN(OBJECT_ID('ctl.dashboard_detalhe_frete_estado'),OBJECT_ID('core.usp_dashboard_detalhe_incremental'),OBJECT_ID('core.fn_dashboard_detalhe_compativel'),OBJECT_ID('core.usp_dashboard_detalhe_calcular_integral'),OBJECT_ID('core.usp_dashboard_detalhe_calcular_selecao'))" -Escalar) -ne 0){throw 'Objeto do instalador já existe fora da revisão 58.'}
        foreach($lote in ($pacote -split '(?im)^GO\s*$')){if($lote.Trim()){Executar $lote}}
        if((Executar "SELECT COUNT(*) FROM sys.computed_columns WHERE object_id=OBJECT_ID('core.dashboard_detalhe_fretes_leitura') AND name='manifesto_entrega_consulta'" -Escalar) -eq 1){
            $indices=[IO.File]::ReadAllText((Join-Path $repo 'database/production/publication/067_dashboard_indices_frete_operacao.sql'))
            Executar (($indices -split '(?im)^GO\s*$',2)[0])
        }
        if((Executar 'SELECT core.fn_dashboard_detalhe_compativel()' -Escalar) -ne 1){throw 'Dependências ou recursos de rastreamento diferentes do ensaio.'}
        $inicio=$fretes.IndexOf('WITH fretes AS (',[StringComparison]::Ordinal)
        $sql=$fretes.Substring($inicio).Trim().TrimEnd(';')
        $sql=Substituir $sql 'FROM core.dashboard_fretes_vigentes_preparada l' 'FROM #fonte_alterada l'
        $i=$sql.IndexOf('), invoices AS (',[StringComparison]::Ordinal);$j=$sql.IndexOf('), combined AS (',[StringComparison]::Ordinal)
        $sql=$sql.Remove($i,$j-$i).Replace('JOIN invoices ','JOIN #faturas ').Replace('JOIN locations ','JOIN #locais ')
        $marcador="`nSELECT f.*,COALESCE(f.branch_id,n.filial_id)"
        $sql=Substituir $sql $marcador (', publicacao AS ('+$marcador)
        $saida=$detalhe.Substring($detalhe.IndexOf("`nSELECT`n",[StringComparison]::Ordinal)+1).Trim().TrimEnd(';')
        $saida=Substituir $saida 'FROM core.dashboard_fretes_publicacao_preparada f' 'FROM publicacao f'
        $saida=Substituir $saida "(SELECT DISTINCT [Nº Minuta] AS minuta FROM pub.vw_inventario_powerbi WHERE [Comprovante Anexado]=N'Sim')" '#comprovantes'
        $sql=$sql+"`n), detalhe AS (`n"+$saida+"`n)`nINSERT #detalhe SELECT "+$colunas+' FROM detalhe OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2,RECOMPILE);'
        $referencias=@{'ref.dashboard_faturamento_atribuicao_filial'='#ref_atribuicao';'ref.dashboard_frete_cidade_destino'='#ref_cidade';'ref.dashboard_frete_filial_documento'='#ref_documento';'ref.dashboard_frete_filial_nome'='#ref_nome'}
        foreach($key in $referencias.Keys){$sql=Substituir $sql $key $referencias[$key]}
        Executar ("CREATE PROCEDURE $selecao AS BEGIN SET NOCOUNT ON; "+$sql+' END;')
        Executar ("CREATE PROCEDURE $integral @linhas int OUTPUT AS BEGIN SET NOCOUNT ON; INSERT #detalhe SELECT "+$colunas+" FROM $objetoDetalhe OPTION(MAXDOP 1,MAX_GRANT_PERCENT=2); SET @linhas=@@ROWCOUNT; END;")
        Recriar-Publicador (Substituir $original $antigo.Value 'EXEC core.usp_dashboard_detalhe_incremental;')
        Executar "INSERT ctl.dashboard_detalhe_instalacao(revisao,modulo_anterior,assinatura_instalada,pacote,instalada_utc) SELECT 58,@origem,HASHBYTES('SHA2_256',OBJECT_DEFINITION(OBJECT_ID(@nome))),CONVERT(binary(32),@pacote,2),SYSUTCDATETIME();" @{'@origem'=$modulo;'@pacote'=$assinatura;'@nome'=$nome}
    }
    if($Acao -eq 'Verificar'){$transacao.Rollback()}else{$transacao.Commit()}
    [pscustomobject]@{banco=$Banco;acao=$Acao;jaInstalado=$instalado;pacote=$assinatura;concluidoUtc=[DateTimeOffset]::UtcNow}
} finally {
    if($transacao){$transacao.Dispose()}
    $conexao.Dispose();$credencial.Password.Dispose();$sha.Dispose()
}

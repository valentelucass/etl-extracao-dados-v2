[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + $env:PSModulePath
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cfg = @{}
foreach ($line in [IO.File]::ReadLines((Join-Path $repo '.env'))) {
    if ($line -match '^\s*(API_BASE_URL|API_GRAPHQL_ENDPOINT|API_GRAPHQL_TOKEN)=(.*)$') {
        $cfg[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
$uri = [uri]::new([uri]$cfg.API_BASE_URL, $cfg.API_GRAPHQL_ENDPOINT)
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'rodogarcia.eslcloud.com.br' -or !$cfg.API_GRAPHQL_TOKEN) {
    throw 'Origem de coletas do ESL indisponivel.'
}
function Read-Relatorio($Query, $Variables) {
    $body = @{query=$Query;variables=$Variables} | ConvertTo-Json -Depth 6 -Compress
    $resp = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method Post -Headers @{Authorization='Bearer '+$cfg.API_GRAPHQL_TOKEN} -ContentType 'application/json' -Body $body -TimeoutSec 90 -MaximumRedirection 0
    $result = ConvertFrom-Json -InputObject $resp.Content
    if ($result.errors -or !$result.data) {throw 'O ESL retornou uma consulta incompleta para coletas.'}
    return $result.data
}
$query = @'
query ColetasRede($periodo: String!, $apos: String) {
  picksReportAnalytical(search:{picks:{requestDate:$periodo}},group:"status") {
    totals
    groupedCollection(first:20,after:$apos) {
      nodes { reports { id reportRequestDate reportAgent } }
      pageInfo { hasNextPage endCursor }
    }
  }
}
'@
$queryAgente = @'
query ExecutorColeta($id: ID!) {
  pick(params:{id:$id},first:1) { nodes { id agentId agent { person { document } } } }
}
'@
$cred = Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$con = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Rede-Agentes-Coleta'
try {
    $con.Open()
    $cmd = $con.CreateCommand()
    $cmd.CommandTimeout = 90
    $cmd.CommandText = "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='rede_agentes_coleta',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r"
    if ([int]$cmd.ExecuteScalar() -lt 0) { return }
    $cmd.CommandText = 'EXEC core.usp_rede_dias_agentes_coleta'
    $reader = $cmd.ExecuteReader()
    $dias = [Collections.Generic.List[datetime]]::new()
    try { while ($reader.Read()) { $dias.Add($reader.GetDateTime(0)) } } finally { $reader.Close() }
    for ($i=0; $i -lt $dias.Count; $i++) {
        $inicio = $dias[$i]
        while ($i+1 -lt $dias.Count -and $dias[$i+1] -eq $dias[$i].AddDays(1) -and $dias[$i+1] -le $inicio.AddDays(6)) { $i++ }
        $fim = $dias[$i]
        $range = $inicio.ToString('yyyy-MM-dd') + ' - ' + $fim.ToString('yyyy-MM-dd')
        $linhas = [Collections.Generic.List[object]]::new()
        $vars = @{periodo=$range;apos=$null}
        $cursores = @{}
        do {
            $rel = (Read-Relatorio $query $vars).picksReportAnalytical
            if ($null -eq $rel.totals.report_count -or $null -eq $rel.groupedCollection.pageInfo) { throw "Relatorio incompleto em $range." }
            foreach ($grupo in $rel.groupedCollection.nodes) {
                if ($null -eq $grupo.reports) { throw "Grupo de coletas incompleto em $range." }
                foreach ($coleta in $grupo.reports) {
                    $temAgente = ![string]::IsNullOrWhiteSpace([string]$coleta.reportAgent)
                    $doc = $null
                    if ($temAgente) {
                        $itens = @((Read-Relatorio $queryAgente @{id=[string]$coleta.id}).pick.nodes)
                        if ($itens.Count -ne 1 -or $itens[0].id -ne $coleta.id -or !$itens[0].agentId) {
                            throw "Executor divergente na coleta $($coleta.id)."
                        }
                        $doc = ([string]$itens[0].agent.person.document -replace '[.\s/\-]','').ToUpperInvariant()
                        if (!$doc) { $doc=$null }
                    }
                    $linhas.Add(@{id=$coleta.id;dia=$coleta.reportRequestDate;agente=$temAgente;documento=$doc})
                }
            }
            $page = $rel.groupedCollection.pageInfo
            $vars.apos = $page.endCursor
            if ($page.hasNextPage) {
                if (!$vars.apos -or $cursores.ContainsKey($vars.apos)) {throw 'Paginacao de coletas interrompida.'}
                $cursores[$vars.apos] = $true
            }
        } while ($page.hasNextPage)
        if ($linhas.Count -ne [int]$rel.totals.report_count) { throw "Quantidade divergente no ESL em $range." }
        $cmd.Parameters.Clear()
        $cmd.CommandText = 'EXEC core.usp_rede_publicar_agentes_coleta @inicio,@fim,@total,@linhas'
        $null = $cmd.Parameters.AddWithValue('@inicio',$inicio)
        $null = $cmd.Parameters.AddWithValue('@fim',$fim)
        $null = $cmd.Parameters.AddWithValue('@total',$linhas.Count)
        $json = $cmd.Parameters.Add('@linhas',[Data.SqlDbType]::NVarChar,-1)
        $json.Value = ConvertTo-Json -InputObject $linhas.ToArray() -Compress
        $null = $cmd.ExecuteNonQuery()
        Write-Output "$range : $($linhas.Count) coletas conferidas."
    }
} finally { $con.Dispose(); $cred.Password.Dispose() }

[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cfg = @{}
foreach ($line in [IO.File]::ReadLines((Join-Path $repo '.env'))) {
    if ($line -match '^\s*(API_BASE_URL|API_GRAPHQL_ENDPOINT|API_GRAPHQL_TOKEN)=(.*)$') {
        $cfg[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
$uri = [uri]::new([uri]$cfg.API_BASE_URL,$cfg.API_GRAPHQL_ENDPOINT)
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'rodogarcia.eslcloud.com.br' -or !$cfg.API_GRAPHQL_TOKEN) {
    throw 'Origem financeira do ESL indisponivel.'
}
$query = @'
query ParcelasFinanceiras($busca: AccountingCreditReportSearchInput!, $apos: String) {
  accountingCreditsReportAnalytical(search: $busca, group: "status") {
    totals
    groupedCollection(first: 10, after: $apos) {
      nodes { reports { id reportDocument reportCustomerCnpj reportIssueDate reportDueDate reportLiquidationDate reportValueToPay } }
      pageInfo { endCursor hasNextPage }
    }
  }
}
'@
$credential = Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-Parcelas-Recebiveis'
try {
    $connection.Open()
    $cmd = $connection.CreateCommand()
    $cmd.CommandTimeout = 90
    $cmd.CommandText = "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='dashboard_parcelas_recebiveis',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r"
    if ([int]$cmd.ExecuteScalar() -lt 0) { throw 'Uma coleta de parcelas ja esta em andamento.' }
    $cmd.CommandText = 'EXEC core.usp_dashboard_inicio_recebiveis'
    $primeira = $cmd.ExecuteScalar()
    if ($primeira -isnot [datetime]) { throw 'Periodo de emissao das faturas indisponivel.' }
    $ultimo = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'E. South America Standard Time').Date
    $cmd.CommandText = @'
CREATE TABLE #parcelas_esl(parcela_id bigint NOT NULL PRIMARY KEY, documento nvarchar(100) COLLATE DATABASE_DEFAULT,
    cliente_cnpj nvarchar(50) COLLATE DATABASE_DEFAULT,emissao date NOT NULL,vencimento date,liquidacao date,valor decimal(19,2) NOT NULL)
'@
    $null = $cmd.ExecuteNonQuery()
    $cmd.CommandText = @'
INSERT #parcelas_esl(parcela_id,documento,cliente_cnpj,emissao,vencimento,liquidacao,valor)
SELECT id, NULLIF(TRIM(documento),''), NULLIF(REPLACE(REPLACE(REPLACE(TRIM(cnpj),'.',''),'/',''),'-',''),''),
    emissao,vencimento,liquidacao,valor
FROM OPENJSON(@json) WITH(id bigint '$.id', documento nvarchar(100) '$.reportDocument',
    cnpj nvarchar(50) '$.reportCustomerCnpj', emissao date '$.reportIssueDate', vencimento date '$.reportDueDate',
    liquidacao date '$.reportLiquidationDate', valor decimal(19,2) '$.reportValueToPay')
'@
    $json = $cmd.Parameters.Add('@json',[Data.SqlDbType]::NVarChar,-1)
    $num = 0
    $total = [decimal]0
    for ($de = $primeira.Date; $de -le $ultimo; $de = $ate.AddDays(1)) {
        $ate = $de.AddDays(6)
        if ($ate -gt $ultimo) { $ate = $ultimo }
        $periodo = $de.ToString('yyyy-MM-dd') + ' - ' + $ate.ToString('yyyy-MM-dd')
        $vars = @{ busca = @{ accountingCredits = @{ issueDate = $periodo } }; apos = $null }
        $recebidas = 0
        $valor = [decimal]0
        $cursores = @{}
        do {
            $body = @{ query = $query; variables = $vars } | ConvertTo-Json -Depth 8 -Compress
            $resp = Invoke-WebRequest -UseBasicParsing -Uri $uri -Method Post -Headers @{ Authorization = 'Bearer ' + $cfg.API_GRAPHQL_TOKEN } -ContentType 'application/json' -Body $body -TimeoutSec 60 -MaximumRedirection 0
            $result = ConvertFrom-Json -InputObject $resp.Content
            $report = $result.data.accountingCreditsReportAnalytical
            if ($result.errors -or $null -eq $report.totals.report_count -or $null -eq $report.groupedCollection.pageInfo) {
                throw "Resposta incompleta do ESL para $periodo."
            }
            foreach ($grupo in $report.groupedCollection.nodes) {
                if ($null -eq $grupo.reports) { throw "Grupo sem parcelas em $periodo." }
                if ($grupo.reports.Count -gt 0) {
                    $json.Value = ConvertTo-Json -InputObject @($grupo.reports) -Depth 4 -Compress
                    $recebidas += $cmd.ExecuteNonQuery()
                    foreach ($row in $grupo.reports) { $valor += [decimal]$row.reportValueToPay }
                }
            }
            $page = $report.groupedCollection.pageInfo
            $vars.apos = $page.endCursor
            if ($page.hasNextPage) {
                if (!$vars.apos -or $cursores.ContainsKey($vars.apos)) { throw 'Paginacao do ESL interrompida.' }
                $cursores[$vars.apos] = $true
            }
        } while ($page.hasNextPage)
        if ($recebidas -ne [int]$report.totals.report_count -or $valor -ne [decimal]$report.totals.report_value_to_pay) {
            throw "Quantidade ou valor divergente do ESL para $periodo."
        }
        $num += $recebidas
        $total += $valor
    }
    $cmd.Parameters.Clear()
    $cmd.CommandText = 'EXEC core.usp_dashboard_publicar_recebiveis @inicio,@fim,@quantidade,@valor'
    $null = $cmd.Parameters.AddWithValue('@inicio',$primeira)
    $null = $cmd.Parameters.AddWithValue('@fim',$ultimo)
    $null = $cmd.Parameters.AddWithValue('@quantidade',$num)
    $null = $cmd.Parameters.AddWithValue('@valor',$total)
    $null = $cmd.ExecuteNonQuery()
    [pscustomobject]@{inicio=$primeira.ToString('yyyy-MM-dd');fim=$ultimo.ToString('yyyy-MM-dd');parcelas=$num;valor=$total}
} finally {
    $connection.Dispose()
    $credential.Password.Dispose()
}

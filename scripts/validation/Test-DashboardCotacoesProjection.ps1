[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ReceiptPath)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $ReceiptPath) { throw 'RECEIPT_ALREADY_EXISTS' }
$projects = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + (($env:PSModulePath -split ';' |
    Where-Object { $_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$' }) -join ';')
Import-Module (Join-Path $projects '.runtime/sql-server/SqlServerProjetos.psm1')

function Read-Sql([string]$Sql) {
    $cmd = $connection.CreateCommand()
    $cmd.CommandTimeout = 25
    $cmd.CommandText = $Sql
    try {
        $reader = $cmd.ExecuteReader()
        try {
            while ($reader.Read()) {
                $row = [ordered]@{}
                for ($i=0; $i -lt $reader.FieldCount; $i++) {
                    $value = $reader.GetValue($i)
                    $row[$reader.GetName($i)] = if ($value -is [DBNull]) { $null } else { $value }
                }
                [pscustomobject]$row
            }
        } finally { $reader.Dispose() }
    } finally { $cmd.Dispose() }
}

function Read-Shape([string]$Sql) {
    $cmd = $connection.CreateCommand()
    $cmd.CommandTimeout = 25
    $cmd.CommandText = $Sql
    try {
        $reader = $cmd.ExecuteReader()
        try {
            foreach ($column in $reader.GetSchemaTable().Rows) {
                $type = if ($column.DataTypeName -eq 'numeric') { 'decimal' } else { $column.DataTypeName }
                [pscustomobject]@{name=$column.ColumnName; type=$type; size=$column.ColumnSize;
                    precision=$column.NumericPrecision; scale=$column.NumericScale}
            }
        } finally { $reader.Dispose() }
    } finally { $cmd.Dispose() }
}

$receipt = [ordered]@{passed=$false; observedAt=[DateTimeOffset]::Now.ToString('o'); permanentSqlWrites=0; assertions=@()}
$credential = Get-ProjetosSqlAdminCredential
$connection = $null
try {
    $connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'DashboardCotacoesProjection0771'
    $connection.Open()
    $identity = @(Read-Sql "SELECT DB_NAME() db, CONVERT(varchar(30),SERVERPROPERTY('ProductVersion')) version")[0]
    if ($identity.db -cne 'ETL_SISTEMA_V2' -or $identity.version -ne '16.0.1000.6') { throw 'EXACT_TARGET_REQUIRED' }
    $receipt.engine = $identity.version
    $path = Join-Path $PSScriptRoot '../../database/production/publication/002_dashboard_cotacoes.sql'
    $text = [IO.File]::ReadAllText($path).Replace("`r`n","`n")
    $match = [regex]::Match($text, '(?is)CREATE OR ALTER VIEW pub\.vw_cotacoes_powerbi\s+AS\s+(WITH .+);\s*$')
    if (-not $match.Success) { throw 'EXACT_PUBLICATION_DEFINITION_REQUIRED' }
    $query = $match.Groups[1].Value
    $split = $query.IndexOf("`nSELECT`n    q.numero AS", [StringComparison]::Ordinal)
    if ($split -lt 0) { throw 'PUBLICATION_SELECT_BOUNDARY_REQUIRED' }
    $wrap = $query.Substring(0,$split).TrimEnd() + ", projection AS (" + $query.Substring($split) + ")`n"
    $receipt.sqlSha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash

    $expected = @(Read-Shape 'SELECT TOP(0) * FROM ETL_SISTEMA.dbo.vw_cotacoes_powerbi;')
    $actual = @(Read-Shape ($wrap + 'SELECT TOP(0) * FROM projection;'))
    if ($expected.Count -ne 54 -or $actual.Count -ne $expected.Count) { throw 'COLUMN_COUNT_MISMATCH' }
    $receipt.shapeDiff = @(for ($i=0; $i -lt $expected.Count; $i++) {
        $a=$expected[$i];$b=$actual[$i]
        if ($a.name -cne $b.name -or $a.type -cne $b.type -or $a.size -ne $b.size -or
            $a.precision -ne $b.precision -or $a.scale -ne $b.scale) {
            [ordered]@{expected=$a; actual=$b}
        }
    })
    if ($receipt.shapeDiff.Count) { throw 'COLUMN_TYPE_MISMATCH' }
    $receipt.assertions += '54 V1 column names, order, type families, widths and scales match'

    $totals = @'
SELECT COUNT_BIG(*) AS n, COUNT_BIG(DISTINCT [N° Cotação]) AS numbers,
       SUM(CONVERT(decimal(38,2),[Valor frete])) AS freight,
       COUNT_BIG([Data Cotação]) AS dated, COUNT_BIG([Metadata]) AS metadata_rows
FROM projection;
'@
    $receipt.latest = @(Read-Sql ($wrap + $totals))[0]
    $receipt.independent = @(Read-Sql @'
SELECT COUNT_BIG(*) AS n, COUNT_BIG(DISTINCT JSON_VALUE(source_envelope,'$.record.sequence_code')) AS numbers,
       SUM(CONVERT(decimal(38,2),CONVERT(decimal(18,2),JSON_VALUE(source_envelope,'$.record.qoe_qes_total')))) AS freight,
       COUNT_BIG(DISTINCT source_scope) AS scopes
FROM stg.production_export_latest WHERE entity_name='COTACOES';
'@)[0]
    if ($receipt.independent.scopes -ne 1 -or $receipt.latest.n -le 0 -or
        $receipt.latest.n -ne $receipt.independent.n -or $receipt.latest.numbers -ne $receipt.independent.numbers -or
        $receipt.latest.freight -ne $receipt.independent.freight -or $receipt.latest.dated -ne $receipt.latest.n -or
        $receipt.latest.metadata_rows -ne $receipt.latest.n) {
        throw 'LATEST_SOURCE_ROW_OR_AMOUNT_MISMATCH'
    }
    $payload = @(Read-Sql ($wrap + @'
, expected AS (
    SELECT HASHBYTES('SHA2_256', CONVERT(varbinary(max), JSON_QUERY(source_envelope,'$.record'))) fingerprint, COUNT_BIG(*) n
    FROM stg.production_export_latest WHERE entity_name='COTACOES'
    GROUP BY HASHBYTES('SHA2_256', CONVERT(varbinary(max), JSON_QUERY(source_envelope,'$.record')))
), actual AS (
    SELECT HASHBYTES('SHA2_256', CONVERT(varbinary(max), [Metadata])) fingerprint, COUNT_BIG(*) n
    FROM projection GROUP BY HASHBYTES('SHA2_256', CONVERT(varbinary(max), [Metadata]))
)
SELECT COUNT_BIG(*) AS differences FROM expected e FULL JOIN actual a ON e.fingerprint=a.fingerprint
WHERE e.n IS NULL OR a.n IS NULL OR e.n<>a.n;
'@))[0]
    if ($payload.differences -ne 0) { throw 'SOURCE_METADATA_MULTISET_MISMATCH' }
    $receipt.assertions += 'All latest rows, monetary total and literal metadata multiplicities match the independent source read'

    $limits = @(foreach ($convert in [regex]::Matches($query,'CONVERT\(nvarchar\((\d+)\), j\.(\w+)\)')) {
        $alias = [regex]::Escape($convert.Groups[2].Value)
        $mapping = [regex]::Match($query, "(?m)^\s*$alias nvarchar\(max\) '\$\.([a-z_]+)'")
        if (-not $mapping.Success) { throw 'TEXT_FIELD_SOURCE_MAPPING_MISSING' }
        "('" + $mapping.Groups[1].Value + "'," + (2 * [int]$convert.Groups[1].Value) + ")"
    })
    if ($limits.Count -eq 0) { throw 'V1_WIDTH_LIMITS_MISSING' }
    $wide = @(Read-Sql ("SELECT COUNT_BIG(*) n FROM stg.production_export_latest l" +
        " CROSS APPLY OPENJSON(l.source_envelope,'$.record') j JOIN (VALUES " + ($limits -join ',') +
        ") sizes(field_name,max_bytes) ON sizes.field_name=j.[key]" +
        " WHERE l.entity_name='COTACOES' AND DATALENGTH(j.value)>sizes.max_bytes;"))[0]
    if ($wide.n -ne 0) { throw 'V1_TEXT_WIDTH_WOULD_TRUNCATE' }
    $receipt.assertions += 'No source text would be truncated by the verified V1 column widths'

    $latestDefinition = @(Read-Sql "SELECT OBJECT_DEFINITION(OBJECT_ID(N'stg.production_export_latest', N'V')) definition")[0].definition
    $latestMatch = [regex]::Match($latestDefinition,'(?is)CREATE VIEW stg\.production_export_latest\s+AS\s+(SELECT .+?);?\s*$')
    if (-not $latestMatch.Success) { throw 'LATEST_VIEW_DEFINITION_REQUIRED' }
    $latestSql = $latestMatch.Groups[1].Value.Replace('ctl.production_ingestion_coverage','@coverage').
        Replace('ctl.production_export_template','@catalog').Replace('stg.production_export_occurrence','@archive')
    $fixture = @'
DECLARE @coverage table(source_scope char(64),entity_name varchar(40),business_date date,latest_run_id int,stored_utc datetime2);
DECLARE @catalog table(entity_name varchar(40),report_id int);
DECLARE @archive table(run_id int,occurrence_ordinal int,source_envelope nvarchar(max),envelope_sha256 binary(32));
INSERT @catalog VALUES('COTACOES',6906);
INSERT @coverage VALUES('one','COTACOES','2026-03-10',1,'2026-03-10T18:00:00');
INSERT @archive(run_id,occurrence_ordinal,source_envelope) VALUES
(1,1,N'{"record":{"sequence_code":8888,"requested_at":"2026-03-10T14:00:00-03:00","qoe_qes_total":"100.25","qoe_qes_ony_name":"Origem","qoe_qes_ony_sae_code":"PR","qoe_qes_diy_name":"Destino A","qoe_qes_diy_sae_code":"SP"}}'),
(1,2,N'{"record":{"sequence_code":8888,"requested_at":"2026-03-10T14:00:00-03:00","qoe_qes_total":"200.50","qoe_qes_ony_name":"Origem","qoe_qes_ony_sae_code":"PR","qoe_qes_diy_name":"Destino B","qoe_qes_diy_sae_code":"SP","qoe_qes_fit_nse_issued_at":"2026-03-10"}}'),
(2,1,N'{"record":{"sequence_code":8888,"requested_at":"2026-03-10T14:00:00-03:00","qoe_qes_total":"120.25","qoe_qes_ony_name":"Origem","qoe_qes_ony_sae_code":"PR","qoe_qes_diy_name":"Destino A","qoe_qes_diy_sae_code":"SP"}}');
INSERT @archive(run_id,occurrence_ordinal,source_envelope) SELECT 2,2,source_envelope FROM @archive WHERE run_id=1 AND occurrence_ordinal=2;
'@
    $fixtureQuery = "WITH latest_fixture AS (" + $latestSql + "),`n" + $wrap.Substring(5).
        Replace('stg.production_export_latest','latest_fixture').Replace('ctl.production_ingestion_coverage','@coverage')
    $detail = $fixtureQuery + @'
SELECT COUNT_BIG(*) n, COUNT(DISTINCT [Trecho]) routes, SUM([Valor frete]) freight,
       SUM(CASE WHEN [Status Conversão]='Convertida' AND DATEPART(TZOFFSET,[Nfse/Data de emissão])=-180 THEN 1 ELSE 0 END) converted,
       SUM(CASE WHEN [Min. Frete/KG]=0.74 THEN 1 ELSE 0 END) tariff, SUM([Volume]) volumes
FROM projection;
'@
    $first = @(Read-Sql ($fixture + "`n" + $detail))[0]
    $repeat = @(Read-Sql ($fixture + "`nUPDATE @coverage SET latest_run_id=2;`n" + $detail))[0]
    if ($first.n -ne 2 -or $first.routes -ne 2 -or $first.freight -ne [decimal]300.75 -or
        $repeat.n -ne 2 -or $repeat.routes -ne 2 -or $repeat.freight -ne [decimal]320.75 -or
        $repeat.converted -ne 1 -or $repeat.tariff -ne 2) { throw 'ROUTE_RECAPTURE_OR_V1_RULE_REGRESSION' }
    $empty = @(Read-Sql ($fixture + "`nUPDATE @coverage SET latest_run_id=3;`n" + $detail))[0]
    $multi = @(Read-Sql ($fixture + "`nINSERT @coverage VALUES('two','COTACOES','2026-03-10',2,'2026-03-10');`n" + $detail))[0]
    if ($empty.n -ne 0 -or $multi.n -ne 0) { throw 'EMPTY_REFRESH_OR_MULTI_SOURCE_MUST_NOT_REUSE_ROWS' }
    $receipt.replay = [ordered]@{before=$first; after=$repeat; confirmedEmpty=$empty.n; multipleSources=$multi.n}
    $receipt.assertions += 'Exact current-view query keeps both routes after replacement; old values, missing rows and multiple scopes are not added'

    $sameRoute = @'
UPDATE @archive
SET source_envelope=JSON_MODIFY(
    JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(JSON_MODIFY(source_envelope,
        '$.record.qoe_qes_diy_name','Destino A'),
        '$.record.qoe_qes_origin_postal_code','00000001'),
        '$.record.qoe_qes_destination_postal_code','00000002'),
        '$.record.qoe_qes_invoices_volumes',occurrence_ordinal),
        '$.record.qoe_qes_invoices_value',CASE occurrence_ordinal WHEN 1 THEN 900 ELSE 2000 END);
'@
    $sameFirst = @(Read-Sql ($fixture + "`n" + $sameRoute + "`n" + $detail))[0]
    $sameRepeat = @(Read-Sql ($fixture + "`n" + $sameRoute + "`nUPDATE @coverage SET latest_run_id=2;`n" + $detail))[0]
    if ($sameFirst.n -ne 2 -or $sameFirst.routes -ne 1 -or $sameFirst.volumes -ne 3 -or $sameFirst.freight -ne [decimal]300.75 -or
        $sameRepeat.n -ne 2 -or $sameRepeat.routes -ne 1 -or $sameRepeat.volumes -ne 3 -or $sameRepeat.freight -ne [decimal]320.75) {
        throw 'SAME_ROUTE_DISTINCT_CARGO_OR_RECAPTURE_REGRESSION'
    }
    $receipt.sameRouteReplay = [ordered]@{before=$sameFirst; after=$sameRepeat}
    $receipt.assertions += 'Different loads on the same route and postcodes remain two items after the latest batch replaces the prior one'
    $receipt.passed = $true
} catch {
    $e=$_.Exception;while($e.InnerException){$e=$e.InnerException}
    $receipt.failure = if ($e.Message -cmatch '^[A-Z0-9_]+$') { $e.Message } else { $e.GetType().Name + ':' + $e.Number }
    throw 'DASHBOARD_COTACOES_PROJECTION_FAILED_SEE_RECEIPT'
} finally {
    if ($connection) { $connection.Dispose() }
    $credential.Password.Dispose()
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptPath),($receipt | ConvertTo-Json -Depth 8),
        (New-Object Text.UTF8Encoding($false)))
}
Write-Output ('Cotacoes SQL checks=' + $receipt.assertions.Count + '; latest rows=' + $receipt.latest.n)

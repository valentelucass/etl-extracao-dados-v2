[CmdletBinding()]
param(
    [ValidateSet('Reference','Candidate')][string]$Mode = 'Candidate',
    [Parameter(Mandatory=$true)][string]$ReceiptPath
)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $ReceiptPath) { throw 'RECEIPT_ALREADY_EXISTS' }
$projects = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + (($env:PSModulePath -split ';' |
    Where-Object { $_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$' }) -join ';')
Import-Module (Join-Path $projects '.runtime/sql-server/SqlServerProjetos.psm1')

function Read-Rows($Connection, [string]$Sql) {
    $cmd = $Connection.CreateCommand()
    $cmd.CommandTimeout = 25
    $cmd.CommandText = $Sql
    try {
        $reader = $cmd.ExecuteReader()
        try {
            while ($reader.Read()) {
                $row = [ordered]@{}
                for ($i = 0; $i -lt $reader.FieldCount; $i++) {
                    $v = $reader.GetValue($i)
                    $row[$reader.GetName($i)] = if ($v -is [DBNull]) { $null }
                    elseif ($v -is [DateTime] -or $v -is [DateTimeOffset]) { $v.ToString('o') }
                    else { $v }
                }
                [pscustomobject]$row
            }
        } finally { $reader.Dispose() }
    } finally { $cmd.Dispose() }
}

$receipt = [ordered]@{mode=$Mode; observedAt=[DateTimeOffset]::Now.ToString('o'); passed=$false;
    permanentSqlWrites=0; sourceHttpCalls=0; assertions=@()}
$credential = Get-ProjetosSqlAdminCredential
$connection = $null
try {
    $connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'V2ReceivingOverviewQualification'
    $connection.Open()
    $identity = @(Read-Rows $connection "SELECT DB_NAME() AS database_name, CONVERT(varchar(30),SERVERPROPERTY('ProductVersion')) AS product_version")
    if ($identity[0].database_name -cne 'ETL_SISTEMA_V2' -or $identity[0].product_version -ne '16.0.1000.6') {
        throw 'EXACT_SQL_TARGET_REQUIRED'
    }
    $receipt.identity = $identity[0]
    $legacy = @(Read-Rows $connection 'SELECT TOP(0) timestamp_inicio,timestamp_fim,status_final,registros_extraidos,noop_count FROM ETL_SISTEMA.dbo.log_extracoes')
    $receipt.assertions += 'V1 execution audit fields resolve; V2 coverage does not substitute their meaning'
    $receipt.v1Routes = @(Read-Rows $connection @'
SELECT COUNT_BIG(*) AS n FROM DASHBOARDS.sys.synonyms
WHERE PARSENAME(base_object_name,3) = N'ETL_SISTEMA';
'@)[0].n
    $receipt.devDatabasePresent = @(Read-Rows $connection "SELECT CASE WHEN DB_ID(N'DASHBOARDS_DEV') IS NULL THEN 0 ELSE 1 END AS present")[0].present -eq 1
    $receipt.reference = @(Read-Rows $connection @'
WITH current_rows AS (
    SELECT entity_name, COUNT_BIG(*) AS n
    FROM stg.production_export_latest GROUP BY entity_name
), coverage_days AS (
    SELECT entity_name, COUNT_BIG(*) AS days, MIN(business_date) AS first_day,
           MAX(business_date) AS last_day, MAX(stored_utc) AS latest
    FROM ctl.production_ingestion_coverage GROUP BY entity_name
), historical AS (
    SELECT entity_name, SUM(CAST(row_count AS bigint)) AS n
    FROM ctl.production_ingestion_run WHERE run_state='STORED' GROUP BY entity_name
)
SELECT t.entity_name,t.report_id,t.expected_field_count AS fields,
       COALESCE(cur.n,0) AS current_rows,COALESCE(hist.n,0) AS historical_rows,
       COALESCE(c.days,0) AS days,c.first_day,c.last_day,c.latest
FROM ctl.production_export_template t
LEFT JOIN current_rows cur ON cur.entity_name=t.entity_name
LEFT JOIN historical hist ON hist.entity_name=t.entity_name
LEFT JOIN coverage_days c ON c.entity_name=t.entity_name
ORDER BY t.entity_name;
'@)
    $receipt.sourceScopes = @(Read-Rows $connection 'SELECT COUNT_BIG(DISTINCT source_scope) AS n FROM ctl.production_ingestion_coverage')[0].n
    if ($receipt.reference.Count -ne 11 -or $receipt.sourceScopes -ne 1 -or
        ($receipt.reference | Measure-Object current_rows -Sum).Sum -le 0 -or
        -not @($receipt.reference | Where-Object { $_.historical_rows -gt $_.current_rows }).Count) {
        throw 'REAL_REFERENCE_OR_DUPLICATION_REGRESSION_NOT_OBSERVED'
    }
    $receipt.assertions += 'Real latest readback differs from historical capture sum; test would catch duplicate totals'
    if ($Mode -eq 'Candidate') {
        $file = Join-Path $PSScriptRoot '../../database/production/publication/001_dashboard_receiving_overview.sql'
        $source = [IO.File]::ReadAllText($file)
        $definition = [regex]::Match($source, '(?is)^.*?CREATE OR ALTER VIEW pub\.v2_dashboard_receiving_overview\s+AS\s+(WITH .+);\s*$')
        if (-not $definition.Success) { throw 'EXACT_SINGLE_VIEW_DEFINITION_REQUIRED' }
        $query = $definition.Groups[1].Value
        $receipt.candidateSha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
        $receipt.candidate = @(Read-Rows $connection ($query + ' ORDER BY entity_name;'))
        if ($receipt.candidate.Count -ne $receipt.reference.Count) { throw 'CANDIDATE_ENTITY_COUNT_MISMATCH' }
        for ($i=0; $i -lt $receipt.reference.Count; $i++) {
            $ref = $receipt.reference[$i]
            $actual = $receipt.candidate[$i]
            $expectedStatus = if ($ref.days -gt 0) { 'AVAILABLE' } else { 'NOT_LOADED' }
            $latest = if ($actual.latest_capture_utc) { ([DateTimeOffset]::Parse($actual.latest_capture_utc)).UtcDateTime.ToString('o') } else { $null }
            if ($ref.entity_name -cne $actual.entity_name -or $ref.report_id -ne $actual.report_id -or
                $ref.fields -ne $actual.field_count -or $ref.current_rows -ne $actual.latest_rows -or
                $ref.days -ne $actual.covered_days -or $ref.first_day -ne $actual.first_business_date -or
                $ref.last_day -ne $actual.last_business_date -or
                ($ref.latest -and ($ref.latest + 'Z') -ne $latest) -or $actual.coverage_status -cne $expectedStatus) {
                throw 'CANDIDATE_DIFFERS_FROM_INDEPENDENT_REAL_REFERENCE'
            }
        }
        $receipt.assertions += 'Every real entity matches independent raw latest readback and confirmed dates'
        $fixture = $query.Replace('ctl.production_ingestion_coverage','@coverage')
        $fixture = $fixture.Replace('ctl.production_ingestion_run','@runs').Replace('ctl.production_export_template','@templates')
        $prelude = @'
DECLARE @coverage table(entity_name varchar(40),source_scope char(64),business_date date,latest_run_id int,stored_utc datetime2);
DECLARE @runs table(run_id int,entity_name varchar(40),source_scope char(64),period_start date,period_end date,run_state varchar(16),row_count int);
DECLARE @templates table(entity_name varchar(40),report_id int,expected_field_count int);
INSERT @templates VALUES ('A',1,2),('B',2,3);
INSERT @runs VALUES (1,'A','one','2026-11-02','2026-11-02','STORED',100),
                    (2,'A','one','2026-11-02','2026-11-02','STORED',100),
                    (3,'A','one','2026-11-04','2026-11-04','STORED',0),
                    (4,'A','one','2026-11-06','2026-11-06','FAILED',NULL),
                    (5,'A','two','2026-11-06','2026-11-06','STORED',50);
INSERT @coverage VALUES ('A','one','2026-11-02',2,'2026-11-02'), ('A','one','2026-11-04',3,'2026-11-04');
'@
        $healthy = @(Read-Rows $connection ($prelude + [Environment]::NewLine + $fixture + ' ORDER BY entity_name;'))
        if ($healthy[0].latest_rows -ne 100 -or $healthy[0].covered_days -ne 2 -or
            $healthy[0].coverage_status -cne 'AVAILABLE' -or $healthy[1].coverage_status -cne 'NOT_LOADED' -or
            $healthy[1].latest_rows -ne 0 -or $null -ne $healthy[1].first_business_date) {
            throw 'RECAPTURE_EMPTY_DAY_OR_GAP_REGRESSION'
        }
        $receipt.assertions += 'Two captures of 100 remain 100; empty confirmed day counts as a day; missing day is not claimed'
        $bad = @(Read-Rows $connection ($prelude + " INSERT @coverage VALUES ('A','one','2026-11-06',4,'2026-11-06'); " + $fixture + ' ORDER BY entity_name;'))
        if ($bad[0].coverage_status -cne 'INVALID_COVERAGE' -or $null -ne $bad[0].latest_rows) {
            throw 'INVALID_COVERAGE_MUST_NOT_LOOK_LIKE_ZERO'
        }
        $multi = @(Read-Rows $connection ($prelude + " INSERT @coverage VALUES ('A','two','2026-11-06',5,'2026-11-06'); " + $fixture + ' ORDER BY entity_name;'))
        if (@($multi | Where-Object { $_.coverage_status -cne 'MULTIPLE_SOURCES' -or $null -ne $_.latest_rows }).Count) {
            throw 'MULTIPLE_SCOPES_MUST_NOT_BE_ADDED'
        }
        $receipt.assertions += 'Failed coverage and multiple sources do not present false totals'
    }
    $receipt.passed = $true
} catch {
    $cause = $_.Exception
    while ($cause.InnerException) { $cause = $cause.InnerException }
    $receipt.failure = if ($cause.Message -cmatch '^[A-Z0-9_]+$') { $cause.Message }
        else { $cause.GetType().Name + ':' + $cause.Number }
    throw 'DASHBOARD_RECEIVING_QUALIFICATION_FAILED_SEE_RECEIPT'
} finally {
    if ($connection) { $connection.Dispose() }
    $credential.Password.Dispose()
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptPath), ($receipt | ConvertTo-Json -Depth 7),
        (New-Object Text.UTF8Encoding($false)))
}
Write-Output ('Mode=' + $Mode + '; checks=' + $receipt.assertions.Count + '; no persistent SQL writes')

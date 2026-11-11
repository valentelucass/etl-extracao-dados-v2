[CmdletBinding()]
param([switch]$Apply)

$ErrorActionPreference = 'Stop'
$projects = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + (($env:PSModulePath -split ';' |
    Where-Object { $_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$' }) -join ';')
Import-Module (Join-Path $projects '.runtime/sql-server/SqlServerProjetos.psm1')

$sqlRelative = 'database/production/publication/001_dashboard_receiving_overview.sql'
$sql = [IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../' + $sqlRelative))).Replace("`r`n","`n")
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-','') }
finally { $sha.Dispose() }
$viewPattern = '(?is)CREATE OR ALTER VIEW pub\.v2_dashboard_receiving_overview\s+AS\s+(WITH .+);\s*$'
$projection = [regex]::Match($sql, $viewPattern)
if (-not $projection.Success) { throw 'EXACT_PUBLICATION_SCRIPT_REQUIRED' }
$credential = Get-ProjetosSqlAdminCredential
$connection = $null
$transaction = $null

function Scalar([string]$Text, [bool]$WithHash=$false) {
    $cmd = $connection.CreateCommand()
    $cmd.CommandTimeout = 15
    $cmd.CommandText = $Text
    if ($transaction) { $cmd.Transaction = $transaction }
    if ($WithHash) {
        $param = $cmd.Parameters.Add('@hash',[Data.SqlDbType]::Char,64)
        $param.Value = $hash
    }
    try { $cmd.ExecuteScalar() } finally { $cmd.Dispose() }
}

try {
    $connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'V2ReceivingOverviewInstaller'
    $connection.Open()
    if ($Apply) {
        $transaction = $connection.BeginTransaction()
        $null = Scalar @'
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'ETL_V2_DASHBOARD_RECEIVING_OVERVIEW',@LockMode='Exclusive',
    @LockOwner='Transaction',@LockTimeout=5000;
IF @lock<0 THROW 51950, 'DASHBOARD_OVERVIEW_LOCK_REFUSED',1;
'@
    }
    $null = Scalar @'
IF DB_NAME() COLLATE Latin1_General_100_BIN2 <> N'ETL_SISTEMA_V2'
    OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion')) <> '16.0.1000.6'
    THROW 51951,'DASHBOARD_OVERVIEW_EXACT_TARGET_REQUIRED',1;
IF NOT EXISTS (SELECT 1 FROM ctl.production_installation_step WHERE step_id='0762-all-entities'
  AND source_sha256='B94D91FCC61A76768CF87228C2092F23C54160A73FA46135EF57EAD57CABFC07')
    THROW 51952,'DASHBOARD_OVERVIEW_RECEIVING_BASE_REQUIRED',1;
IF NOT EXISTS (SELECT 1 FROM sys.schemas p JOIN sys.schemas c ON p.principal_id=c.principal_id
    WHERE p.name='pub' AND c.name='ctl')
    THROW 51953,'DASHBOARD_OVERVIEW_OWNERSHIP_DRIFT',1;
'@
    $record = Scalar "SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id='0770-dashboard-receiving-overview'"
    $installed = Scalar "SELECT OBJECT_DEFINITION(OBJECT_ID(N'pub.v2_dashboard_receiving_overview',N'V'))"
    if ($record -or ($installed -and $installed -isnot [DBNull])) {
        $catalogPattern = $viewPattern.Replace('CREATE OR ALTER VIEW', 'CREATE\s+(?:OR\s+ALTER\s+)?VIEW')
        $installedMatch = [regex]::Match(([string]$installed).Replace("`r`n","`n"), $catalogPattern)
        if ($record -cne $hash -or -not $installedMatch.Success -or
            $projection.Groups[1].Value -cne $installedMatch.Groups[1].Value) {
            throw 'DASHBOARD_OVERVIEW_INSTALLATION_DRIFT'
        }
        if ($transaction) { $transaction.Commit() }
        Write-Output 'Dashboard receiving overview already installed and verified.'
        return
    }
    if (-not $Apply) {
        $rows = Scalar ($projection.Groups[1].Value.Replace('SELECT' + "`n" + '    t.entity_name,',
            'SELECT TOP(0)' + "`n" + '    t.entity_name,') + ';')
        Write-Output ('Validated target and query metadata; no persistent SQL writes. SHA-256: ' + $hash)
        return
    }
    $null = Scalar $sql
    $null = Scalar @'
IF (SELECT COUNT_BIG(*) FROM pub.v2_dashboard_receiving_overview) <>
    (SELECT COUNT_BIG(*) FROM ctl.production_export_template)
    THROW 51954,'DASHBOARD_OVERVIEW_CATALOG_MISMATCH',1;
IF EXISTS (SELECT 1 FROM pub.v2_dashboard_receiving_overview WHERE coverage_status IN ('MULTIPLE_SOURCES','INVALID_COVERAGE'))
    THROW 51955,'DASHBOARD_OVERVIEW_INCONSISTENT_COVERAGE',1;
INSERT ctl.production_installation_step(step_id,source_name,source_sha256)
VALUES('0770-dashboard-receiving-overview',N'database/production/publication/001_dashboard_receiving_overview.sql',@hash);
'@ $true
    $transaction.Commit()
    Write-Output ('Dashboard receiving overview installed. SHA-256: ' + $hash)
} catch {
    if ($transaction -and $transaction.Connection) { $transaction.Rollback() }
    $cause = $_.Exception
    while ($cause.InnerException) { $cause = $cause.InnerException }
    if ($cause.Message -cmatch '^[A-Z0-9_]+$') { throw $cause.Message }
    throw ('DASHBOARD_OVERVIEW_INSTALLER_FAILED: ' + $cause.GetType().Name + ':' + $cause.Number)
} finally {
    if ($transaction) { $transaction.Dispose() }
    if ($connection) { $connection.Dispose() }
    $credential.Password.Dispose()
}

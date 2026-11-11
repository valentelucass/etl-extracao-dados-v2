[CmdletBinding()]
param([switch]$Apply)

$ErrorActionPreference = 'Stop'
$projects = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + (($env:PSModulePath -split ';' |
    Where-Object { $_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$' }) -join ';')
Import-Module (Join-Path $projects '.runtime/sql-server/SqlServerProjetos.psm1')

$relative = 'database/production/publication/002_dashboard_cotacoes.sql'
$sql = [IO.File]::ReadAllText((Join-Path $PSScriptRoot ('../../' + $relative))).Replace("`r`n", "`n")
$viewPattern = '(?is)CREATE OR ALTER VIEW pub\.vw_cotacoes_powerbi\s+AS\s+(WITH .+);\s*$'
$definition = [regex]::Match($sql, $viewPattern)
if (-not $definition.Success) { throw 'EXACT_COTACOES_DEFINITION_REQUIRED' }
$sha = [Security.Cryptography.SHA256]::Create()
try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-', '') }
finally { $sha.Dispose() }

function Invoke-Scalar([string]$Text, [switch]$RecordHash) {
    $command = $connection.CreateCommand()
    $command.Transaction = $transaction
    $command.CommandTimeout = 20
    $command.CommandText = $Text
    if ($RecordHash) { $command.Parameters.Add('@hash', [Data.SqlDbType]::Char, 64).Value = $hash }
    try { $command.ExecuteScalar() } finally { $command.Dispose() }
}

$credential = Get-ProjetosSqlAdminCredential
$connection = $null
$transaction = $null
try {
    $connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'V2DashboardCotacoesInstaller'
    $connection.Open()
    $transaction = $connection.BeginTransaction()
    $null = Invoke-Scalar @'
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
IF DB_NAME() COLLATE Latin1_General_100_BIN2 <> N'ETL_SISTEMA_V2'
    OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion')) <> '16.0.1000.6'
    THROW 51960,'DASHBOARD_COTACOES_EXACT_TARGET_REQUIRED',1;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'ETL_V2_DASHBOARD_COTACOES',@LockMode='Exclusive',
    @LockOwner='Transaction',@LockTimeout=5000;
IF @lock<0 THROW 51961,'DASHBOARD_COTACOES_LOCK_REFUSED',1;
IF NOT EXISTS (SELECT 1 FROM ctl.production_installation_step WHERE step_id='0762-all-entities'
  AND source_sha256='B94D91FCC61A76768CF87228C2092F23C54160A73FA46135EF57EAD57CABFC07')
    THROW 51962,'DASHBOARD_COTACOES_RECEIVING_BASE_REQUIRED',1;
IF NOT EXISTS (SELECT 1 FROM ctl.production_export_template WHERE entity_name='COTACOES'
  AND report_id=6906 AND expected_field_count=37)
    THROW 51963,'DASHBOARD_COTACOES_SOURCE_CONTRACT_REQUIRED',1;
IF NOT EXISTS (SELECT 1 FROM sys.schemas p
    JOIN sys.schemas c ON c.principal_id=p.principal_id
    JOIN sys.schemas s ON s.principal_id=p.principal_id
    WHERE p.name='pub' AND c.name='ctl' AND s.name='stg')
    THROW 51964,'DASHBOARD_COTACOES_OWNERSHIP_DRIFT',1;
'@
    $record = Invoke-Scalar "SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id='0773-dashboard-cotacoes'"
    $objectId = Invoke-Scalar "SELECT OBJECT_ID(N'pub.vw_cotacoes_powerbi')"
    $exists = $null -ne $objectId -and $objectId -isnot [DBNull]
    if ($record -or $exists) {
        $installed = Invoke-Scalar "SELECT OBJECT_DEFINITION(OBJECT_ID(N'pub.vw_cotacoes_powerbi',N'V'))"
        $catalogPattern = $viewPattern.Replace('CREATE OR ALTER VIEW', 'CREATE\s+(?:OR\s+ALTER\s+)?VIEW')
        $installedMatch = [regex]::Match(([string]$installed).Replace("`r`n", "`n"), $catalogPattern)
        if ($record -cne $hash -or -not $installedMatch.Success -or
            $definition.Groups[1].Value -cne $installedMatch.Groups[1].Value) {
            throw 'DASHBOARD_COTACOES_INSTALLATION_DRIFT'
        }
    } else {
        $null = Invoke-Scalar $sql
    }

    $rows = Invoke-Scalar @'
DECLARE @rows bigint, @freight decimal(38,2), @expected bigint, @expectedFreight decimal(38,2), @scopes bigint;
SELECT @expected=COUNT_BIG(*), @scopes=COUNT_BIG(DISTINCT source_scope),
       @expectedFreight=SUM(CONVERT(decimal(18,2),JSON_VALUE(source_envelope,'$.record.qoe_qes_total')))
FROM stg.production_export_latest WHERE entity_name='COTACOES';
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    SELECT @rows=COUNT_BIG(*), @freight=SUM([Valor frete]) FROM pub.vw_cotacoes_powerbi;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
IF @scopes<>1 OR @expected=0 OR @rows<>@expected OR @freight IS NULL OR @freight<>@expectedFreight
    THROW 51965,'DASHBOARD_COTACOES_READBACK_MISMATCH',1;
SELECT @rows;
'@
    if ($Apply -and -not $exists) {
        $null = Invoke-Scalar @'
INSERT ctl.production_installation_step(step_id,source_name,source_sha256)
VALUES('0773-dashboard-cotacoes',N'database/production/publication/002_dashboard_cotacoes.sql',@hash);
'@ -RecordHash
        $transaction.Commit()
        Write-Output ('Dashboard quotes installed and readback verified. Rows: ' + $rows + '; SHA-256: ' + $hash)
    } else {
        $transaction.Rollback()
        $status = if ($exists) { 'Dashboard quotes already installed and verified.' } else { 'Dashboard quotes rehearsal rolled back.' }
        Write-Output ($status + ' Rows: ' + $rows + '; SHA-256: ' + $hash)
    }
} catch {
    if ($transaction -and $transaction.Connection) { $transaction.Rollback() }
    $cause = $_.Exception
    while ($cause.InnerException) { $cause = $cause.InnerException }
    if ($cause.Message -cmatch '^[A-Z0-9_]+$') { throw $cause.Message }
    throw ('DASHBOARD_COTACOES_INSTALLER_FAILED: ' + $cause.GetType().Name + ':' + $cause.Number)
} finally {
    if ($transaction) { $transaction.Dispose() }
    if ($connection) { $connection.Dispose() }
    $credential.Password.Dispose()
}

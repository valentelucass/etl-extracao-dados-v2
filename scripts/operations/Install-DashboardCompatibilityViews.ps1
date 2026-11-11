[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReceiptPath,
    [ValidateSet('References','Sinistros','Inventario','Localizacao')][string]$Group = 'References',
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$receiptFile = [IO.Path]::GetFullPath($ReceiptPath)
if (Test-Path -LiteralPath $receiptFile) { throw 'RECEIPT_ALREADY_EXISTS' }
if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($receiptFile)) -PathType Container)) {
    throw 'RECEIPT_DIRECTORY_REQUIRED'
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$env:PSModulePath = (Join-Path $PSHOME 'Modules') + ';' + (($env:PSModulePath -split ';' |
    Where-Object { $_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$' }) -join ';')
Import-Module (Join-Path $repository '../.runtime/sql-server/SqlServerProjetos.psm1')

$referenceViews = @(
    @{file='003_dashboard_dim_calendario.sql'; view='dim_calendario'; step='0774-dashboard-dim-calendario'; columns=19},
    @{file='004_dashboard_dim_filiais.sql'; view='vw_dim_filiais'; step='0774-dashboard-dim-filiais'; columns=2},
    @{file='005_dashboard_dim_motoristas.sql'; view='vw_dim_motoristas'; step='0774-dashboard-dim-motoristas'; columns=2},
    @{file='006_dashboard_dim_veiculos.sql'; view='vw_dim_veiculos'; step='0774-dashboard-dim-veiculos'; columns=4},
    @{file='007_dashboard_dim_planocontas.sql'; view='vw_dim_planocontas'; step='0774-dashboard-dim-planocontas'; columns=3}
)
$specs = switch ($Group) {
    'References' { $referenceViews }
    'Sinistros' { @{file='008_dashboard_sinistros.sql'; view='vw_sinistros_powerbi'; step='0774-dashboard-sinistros'; columns=34} }
    'Inventario' { @{file='009_dashboard_inventario.sql'; view='vw_inventario_powerbi'; step='0775-dashboard-inventario'; columns=34} }
    'Localizacao' { @{file='010_dashboard_localizacao_cargas.sql'; view='vw_localizacao_cargas_powerbi'; step='0775-dashboard-localizacao'; columns=29} }
}

function New-Command([string]$Text, [hashtable]$Parameters = @{}) {
    $command = $connection.CreateCommand()
    $command.Transaction = $transaction
    $command.CommandTimeout = 45
    $command.CommandText = $Text
    foreach ($key in $Parameters.Keys) {
        $command.Parameters.Add($key, [Data.SqlDbType]::NVarChar, 260).Value = $Parameters[$key]
    }
    $command
}

function Invoke-Scalar([string]$Text, [hashtable]$Parameters = @{}) {
    $command = New-Command $Text $Parameters
    try { $command.ExecuteScalar() } finally { $command.Dispose() }
}

function Read-Metadata([string]$Text) {
    $command = New-Command $Text
    try {
        $reader = $command.ExecuteReader()
        try {
            foreach ($field in $reader.GetSchemaTable().Rows) {
                [pscustomobject]@{
                    name=$field.ColumnName; type=$field.DataTypeName; size=$field.ColumnSize
                    precision=$field.NumericPrecision; scale=$field.NumericScale
                }
            }
        } finally { $reader.Dispose() }
    } finally { $command.Dispose() }
}

$receipt = [ordered]@{passed=$false; group=$Group; mode=if($Apply){'Apply'}else{'Rollback'}; views=@(); catalogWritesCommitted=$false}
$credential = Get-ProjetosSqlAdminCredential
$connection = $null
$transaction = $null
try {
    $connection = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'V2DashboardCompatibilityViews'
    $connection.Open()
    $transaction = $connection.BeginTransaction([Data.IsolationLevel]::Serializable)
    $null = Invoke-Scalar @'
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
IF DB_NAME() COLLATE Latin1_General_100_BIN2 <> N'ETL_SISTEMA_V2'
    OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion')) <> '16.0.1000.6'
    THROW 51966,'REFERENCE_VIEWS_EXACT_TARGET_REQUIRED',1;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'ETL_V2_DASHBOARD_COMPATIBILITY_VIEWS',@LockMode='Exclusive',
    @LockOwner='Transaction',@LockTimeout=5000;
IF @lock<0 THROW 51967,'REFERENCE_VIEWS_LOCK_REFUSED',1;
IF NOT EXISTS (
    SELECT 1 FROM sys.schemas p JOIN sys.schemas c ON c.principal_id=p.principal_id
    JOIN sys.schemas s ON s.principal_id=p.principal_id JOIN sys.schemas r ON r.principal_id=p.principal_id
    WHERE p.name='pub' AND c.name='ctl' AND s.name='stg' AND r.name='ref'
) THROW 51968,'REFERENCE_VIEWS_OWNERSHIP_DRIFT',1;
'@

    $added = 0
    foreach ($spec in $specs) {
        $relative = 'database/production/publication/' + $spec.file
        $sql = [IO.File]::ReadAllText((Join-Path $repository $relative)).Replace("`r`n", "`n")
        $pattern = '(?is)CREATE OR ALTER VIEW pub\.' + $spec.view + '\s+AS\s+(.+?);\s*$'
        $source = [regex]::Match($sql, $pattern)
        if (-not $source.Success) { throw 'REFERENCE_VIEW_SOURCE_FORMAT_CHANGED' }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-', '') }
        finally { $sha.Dispose() }
        $record = Invoke-Scalar 'SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id=@step' @{'@step'=$spec.step}
        $objectId = Invoke-Scalar 'SELECT OBJECT_ID(@object)' @{'@object'=('pub.'+$spec.view)}
        $exists = $null -ne $objectId -and $objectId -isnot [DBNull]
        if ($record -or $exists) {
            $definition = Invoke-Scalar "SELECT OBJECT_DEFINITION(OBJECT_ID(@object,N'V'))" @{'@object'=('pub.'+$spec.view)}
            $catalogPattern = $pattern.Replace('CREATE OR ALTER VIEW', 'CREATE\s+(?:OR\s+ALTER\s+)?VIEW')
            $installed = [regex]::Match(([string]$definition).Replace("`r`n", "`n"), $catalogPattern)
            if ($record -cne $hash -or -not $installed.Success -or $installed.Groups[1].Value -cne $source.Groups[1].Value) {
                throw 'REFERENCE_VIEW_INSTALLATION_DRIFT'
            }
        } else {
            $null = Invoke-Scalar $sql
            $null = Invoke-Scalar 'INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@step,@file,@hash)' @{
                '@step'=$spec.step; '@file'=$relative; '@hash'=$hash
            }
            $added++
        }
        $view = $spec.view
        $legacy = @(Read-Metadata "SELECT TOP(0) * FROM ETL_SISTEMA.dbo.[$view]")
        $published = @(Read-Metadata "SELECT TOP(0) * FROM pub.[$view]")
        if ($legacy.Count -ne $spec.columns -or $published.Count -ne $spec.columns -or
            (Compare-Object $legacy $published -Property name,type,size,precision,scale -CaseSensitive -SyncWindow 0)) {
            $receipt.shapeDifference = [ordered]@{view=$view; v1=$legacy; v2=$published}
            throw 'REFERENCE_VIEW_COLUMN_CONTRACT_MISMATCH'
        }
        $receipt.views += [pscustomobject]@{name=$view; columns=$published.Count; sha256=$hash; alreadyInstalled=$exists}
    }

    $validationScript = switch ($Group) {
        'References' { 'dashboard-reference-views.sql' }
        'Sinistros' { 'dashboard-sinistros.sql' }
        'Inventario' { 'dashboard-inventario.sql' }
        'Localizacao' { 'dashboard-localizacao.sql' }
    }
    $checksPath = Join-Path $PSScriptRoot ('../validation/' + $validationScript)
    $checks = [IO.File]::ReadAllText($checksPath) -split '(?m)^-- VALIDATION BATCH\s*$'
    for ($batchIndex=0; $batchIndex -lt $checks.Count; $batchIndex++) {
        $receipt.validationBatch = $batchIndex + 1
        $validation = Invoke-Scalar $checks[$batchIndex]
    }
    if (-not $validation -or $validation -is [DBNull]) { throw 'REFERENCE_VIEW_VALIDATION_MISSING' }
    $receipt.validation = ConvertFrom-Json -InputObject $validation
    if ($Apply -and $added -gt 0) {
        $transaction.Commit()
        $receipt.catalogWritesCommitted = $true
    } else {
        $transaction.Rollback()
    }
    $receipt.passed = $true
    $receipt | ConvertTo-Json -Depth 8
} catch {
    if ($transaction -and $transaction.Connection) { $transaction.Rollback() }
    $cause = $_.Exception
    while ($cause.InnerException -and $cause.GetType().Name -ne 'SqlException') { $cause = $cause.InnerException }
    $receipt.failure = [ordered]@{
        type=$cause.GetType().Name; number=$cause.Number
        code=if($cause.Message -cmatch '^[A-Z0-9_]+$'){$cause.Message}else{'REFERENCE_VIEW_VALIDATION_FAILED'}
    }
    throw ($receipt.failure.code + ':' + $receipt.failure.number)
} finally {
    if ($transaction) { $transaction.Dispose() }
    if ($connection) { $connection.Dispose() }
    $credential.Password.Dispose()
    [IO.File]::WriteAllText($receiptFile, ($receipt | ConvertTo-Json -Depth 9), [Text.UTF8Encoding]::new($false))
}

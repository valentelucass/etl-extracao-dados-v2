[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReceiptPath,
    [ValidateSet('References','Sinistros','Inventario','Localizacao','Faturas','ContasAPagar','Clientes','Usuarios','Raster','Coletores','Saude','Manifestos')][string]$Group = 'References',
    [string[]]$CaptureReceiptPath,
    [string]$CaptureDirectory,
    [string]$AuditReportPath,
    [string]$AuditCaptureDirectory,
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
    'Faturas' {
        @{file='011_dashboard_faturas_vigentes.sql'; view='dashboard_faturas_vigentes'; schema='core'; step='0776-dashboard-faturas-vigentes'}
        @{file='012_dashboard_faturas_por_cliente.sql'; view='vw_faturas_por_cliente_powerbi'; step='0776-dashboard-faturas-detalhe'; columns=42}
        @{file='013_dashboard_fato_faturas.sql'; view='fato_gestao_vista_faturas'; step='0776-dashboard-faturas-fato'; columns=54}
    }
    'ContasAPagar' { @{file='014_dashboard_contas_a_pagar.sql'; view='vw_contas_a_pagar_powerbi'; step='0776-dashboard-contas-a-pagar'; columns=31} }
    'Clientes' { @{file='015_dashboard_dim_clientes.sql'; view='vw_dim_clientes'; step='0776-dashboard-dim-clientes'; columns=1} }
    'Usuarios' { @{file='016_dashboard_dim_usuarios.sql'; view='vw_dim_usuarios'; step='0777-dashboard-dim-usuarios'; columns=3} }
    'Raster' {
        @{file='017_dashboard_raster_vigente.sql'; view='dashboard_raster_vigente'; schema='core'; step='0777-dashboard-raster-vigente'}
        @{file='018_dashboard_raster_viagens.sql'; view='raster_viagens'; step='0777-dashboard-raster-viagens'; columns=33}
        @{file='019_dashboard_raster_paradas.sql'; view='raster_viagem_paradas'; step='0777-dashboard-raster-paradas'; columns=25}
        @{file='020_dashboard_raster_transit_time.sql'; view='vw_raster_sm_transit_time'; step='0777-dashboard-raster-transit'; columns=37}
    }
    'Coletores' { @{file='021_dashboard_fato_coletores.sql'; view='fato_gestao_vista_coletores'; step='0777-dashboard-coletores'; columns=21} }
    'Saude' {
        @{file='022_dashboard_log_extracoes.sql'; view='log_extracoes'; step='0777-dashboard-log-extracoes'; columns=9}
        @{file='023_dashboard_bi_monitoramento.sql'; view='vw_bi_monitoramento'; step='0777-dashboard-monitoramento'; columns=9}
    }
    'Manifestos' {
        @{file='024_dashboard_manifestos_linhas.sql'; view='dashboard_manifestos_linhas'; schema='core'; step='0777-dashboard-manifestos-linhas'}
        @{file='025_dashboard_fato_manifestos.sql'; view='dashboard_fato_manifestos'; schema='core'; step='0777-dashboard-manifestos-fato'}
        @{file='026_dashboard_manifestos_powerbi.sql'; view='vw_manifestos_powerbi'; step='0777-dashboard-manifestos-detalhe'; columns=105}
        @{file='027_dashboard_manifestos_fato_dash.sql'; view='vw_fato_manifestos_dash'; step='0777-dashboard-manifestos-portal'; columns=106}
    }
}

$auxiliary = $Group -in @('Usuarios','Raster')
if (($CaptureReceiptPath -or $CaptureDirectory) -and (-not $auxiliary -or -not $CaptureReceiptPath -or -not $CaptureDirectory)) {
    throw 'AUXILIARY_CAPTURE_ARGUMENTS_INVALID'
}
if (($AuditReportPath -or $AuditCaptureDirectory) -and ($Group -ne 'Saude' -or -not $AuditReportPath -or -not $AuditCaptureDirectory)) {
    throw 'AUDIT_CAPTURE_ARGUMENTS_INVALID'
}

function New-Command([string]$Text, [hashtable]$Parameters = @{}) {
    $command = $connection.CreateCommand()
    $command.Transaction = $transaction
    $command.CommandTimeout = 45
    $command.CommandText = $Text
    foreach ($key in $Parameters.Keys) {
        $size = if ($key -eq '@records') { -1 } else { 260 }
        $command.Parameters.Add($key, [Data.SqlDbType]::NVarChar, $size).Value = $Parameters[$key]
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

function Install-ReceivingPackage([string]$RelativePath,[string]$Step,[string]$CountQuery,[int]$ExpectedObjects) {
    $sql = [IO.File]::ReadAllText((Join-Path $repository $RelativePath)).Replace("`r`n", "`n")
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-', '') }
    finally { $sha.Dispose() }
    $record = Invoke-Scalar 'SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id=@step' @{'@step'=$Step}
    $objectCount = Invoke-Scalar $CountQuery
    if ($record -or $objectCount) {
        if ($record -cne $hash -or $objectCount -ne $ExpectedObjects) { throw 'DASHBOARD_RECEIVING_DRIFT' }
        return 0
    }
    foreach ($batch in [regex]::Split($sql, '(?im)^GO[\t ]*$')) {
        if ($batch.Trim()) { $null = Invoke-Scalar $batch }
    }
    $null = Invoke-Scalar 'INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@step,@file,@hash)' @{
        '@step'=$Step; '@file'=$RelativePath; '@hash'=$hash
    }
    return 1
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
    JOIN sys.schemas domain_schema ON domain_schema.principal_id=p.principal_id
    WHERE p.name='pub' AND c.name='ctl' AND s.name='stg' AND r.name='ref' AND domain_schema.name='core'
) THROW 51968,'REFERENCE_VIEWS_OWNERSHIP_DRIFT',1;
'@

    $added = 0
    if ($auxiliary) {
        $relative = 'database/production/receiving/004_dashboard_auxiliary_receiving.sql'
        $countQuery = @'
SELECT COUNT(*) FROM sys.objects WHERE object_id IN (
    OBJECT_ID('ctl.dashboard_auxiliary_capture','U'),OBJECT_ID('stg.dashboard_auxiliary_record','U'),
    OBJECT_ID('ctl.usp_store_dashboard_auxiliary','P'),OBJECT_ID('stg.dashboard_auxiliary_latest','V'),
    OBJECT_ID('core.dashboard_raster_time','FN'))
'@
        $added += Install-ReceivingPackage $relative '0777-dashboard-auxiliary-receiving' $countQuery 5
        if ($CaptureReceiptPath) { $receipt.captures = @() }
        foreach ($path in $CaptureReceiptPath) {
            $capture = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $path).Path) | ConvertFrom-Json
            $expected = if ($Group -eq 'Usuarios') { 'Users' } else { 'Raster' }
            if (-not $capture.passed -or $capture.status -ne 200 -or $capture.source -cne $expected -or
                ($expected -eq 'Users' -and -not $capture.terminal) -or
                $capture.captureFile -cnotmatch ('^'+$expected.ToLowerInvariant()+'-[a-z0-9-]+\.json$') -or
                $capture.sha256 -cnotmatch '^[A-F0-9]{64}$' -or $capture.items -lt 1) {
                throw 'AUXILIARY_COMPLETE_SOURCE_RECEIPT_REQUIRED'
            }
            $directory = (Resolve-Path -LiteralPath $CaptureDirectory).Path
            if (-not (Test-Path -LiteralPath $directory -PathType Container)) { throw 'AUXILIARY_CAPTURE_DIRECTORY_REQUIRED' }
            $bytes = [IO.File]::ReadAllBytes((Join-Path $directory $capture.captureFile))
            if ($bytes.Length -gt 10485760) { throw 'AUXILIARY_CAPTURE_BYTES_EXCEEDED' }
            $sha = [Security.Cryptography.SHA256]::Create()
            try { $observedHash = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '') }
            finally { $sha.Dispose() }
            if ($observedHash -cne $capture.sha256) { throw 'AUXILIARY_CAPTURE_HASH_DIFFERS' }
            $observed = [DateTimeOffset]::Parse($capture.completedAt,[Globalization.CultureInfo]::InvariantCulture)
            $day = if ($expected -eq 'Users') { $observed.ToString('yyyy-MM-dd') } else { $capture.day }
            if ($day -cnotmatch '^\d{4}-\d{2}-\d{2}$') { throw 'AUXILIARY_CAPTURE_DATE_INVALID' }
            $captureId = Invoke-Scalar @'
DECLARE @observed_at datetimeoffset(3)=CONVERT(datetimeoffset(3),@observed,127);
EXEC ctl.usp_store_dashboard_auxiliary @source_name=@source,@business_date=@day,
     @observed_at=@observed_at,@row_count=@count,@records=@records;
'@ @{
                '@source'=$expected.ToUpperInvariant(); '@day'=$day; '@observed'=$observed.ToString('o')
                '@count'=[string]$capture.items; '@records'=[Text.UTF8Encoding]::new($false,$true).GetString($bytes)
            }
            if (-not $captureId) { throw 'AUXILIARY_CAPTURE_WRITE_UNCONFIRMED' }
            $receipt.captures += [ordered]@{id=$captureId; records=$capture.items; observedUtc=$observed.UtcDateTime.ToString('o'); sha256=$observedHash}
        }
    }
    if ($Group -eq 'Saude') {
        $relative = 'database/production/receiving/005_dashboard_refresh_audit.sql'
        $countQuery = @'
SELECT COUNT(*) FROM sys.objects WHERE object_id IN (
    OBJECT_ID('ctl.dashboard_refresh_cycle','U'),OBJECT_ID('ctl.dashboard_refresh_log','U'),
    OBJECT_ID('ctl.usp_store_dashboard_refresh_audit','P'))
'@
        $added += Install-ReceivingPackage $relative '0777-dashboard-refresh-audit' $countQuery 3
        if ($AuditReportPath) {
            $evidence = & (Join-Path $PSScriptRoot 'Read-DashboardRefreshAudit.ps1') -ReportPath $AuditReportPath -CaptureDirectory $AuditCaptureDirectory
            $cycleId = Invoke-Scalar @'
DECLARE @start datetimeoffset(3)=CONVERT(datetimeoffset(3),@started,127),
        @end datetimeoffset(3)=CONVERT(datetimeoffset(3),@completed,127),
        @sha binary(32)=CONVERT(binary(32),@digest,2);
EXEC ctl.usp_store_dashboard_refresh_audit @source_cycle=@cycle,@started_at=@start,@completed_at=@end,
     @passed=@passed,@failure_code=@failure,@report_hash=@sha,@operations=@records;
'@ @{
                '@cycle'=$evidence.cycle; '@started'=$evidence.started; '@completed'=$evidence.completed
                '@passed'=[string][int]$evidence.passed; '@failure'=if($evidence.failure){$evidence.failure}else{[DBNull]::Value}
                '@digest'=$evidence.sha256; '@records'=(ConvertTo-Json -InputObject @($evidence.operations) -Depth 4 -Compress)
            }
            if (-not $cycleId) { throw 'AUDIT_CAPTURE_WRITE_UNCONFIRMED' }
            $receipt.capture = [ordered]@{id=$cycleId; records=$evidence.operations.Count; sha256=$evidence.sha256}
        }
    }
    foreach ($spec in $specs) {
        $relative = 'database/production/publication/' + $spec.file
        $sql = [IO.File]::ReadAllText((Join-Path $repository $relative)).Replace("`r`n", "`n")
        $schema = if ($spec.schema) { $spec.schema } else { 'pub' }
        $objectName = $schema + '.' + $spec.view
        $pattern = '(?is)CREATE OR ALTER VIEW ' + [regex]::Escape($objectName) + '\s+AS\s+(.+?);\s*$'
        $source = [regex]::Match($sql, $pattern)
        if (-not $source.Success) { throw 'REFERENCE_VIEW_SOURCE_FORMAT_CHANGED' }
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-', '') }
        finally { $sha.Dispose() }
        $record = Invoke-Scalar 'SELECT source_sha256 FROM ctl.production_installation_step WHERE step_id=@step' @{'@step'=$spec.step}
        $objectId = Invoke-Scalar 'SELECT OBJECT_ID(@object)' @{'@object'=$objectName}
        $exists = $null -ne $objectId -and $objectId -isnot [DBNull]
        if ($record -or $exists) {
            $definition = Invoke-Scalar "SELECT OBJECT_DEFINITION(OBJECT_ID(@object,N'V'))" @{'@object'=$objectName}
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
        $published = @(Read-Metadata "SELECT TOP(0) * FROM [$schema].[$view]")
        if ($spec.columns) {
            $legacy = @(Read-Metadata "SELECT TOP(0) * FROM ETL_SISTEMA.dbo.[$view]")
            if ($legacy.Count -ne $spec.columns -or $published.Count -ne $spec.columns -or
                (Compare-Object $legacy $published -Property name,type,size,precision,scale -CaseSensitive -SyncWindow 0)) {
                $receipt.shapeDifference = [ordered]@{view=$view; v1=$legacy; v2=$published}
                throw 'REFERENCE_VIEW_COLUMN_CONTRACT_MISMATCH'
            }
        }
        $receipt.views += [pscustomobject]@{name=$view; columns=$published.Count; sha256=$hash; alreadyInstalled=$exists}
    }

    $validationScript = switch ($Group) {
        'References' { 'dashboard-reference-views.sql' }
        'Sinistros' { 'dashboard-sinistros.sql' }
        'Inventario' { 'dashboard-inventario.sql' }
        'Localizacao' { 'dashboard-localizacao.sql' }
        'Faturas' { 'dashboard-faturas.sql' }
        'ContasAPagar' { 'dashboard-contas-a-pagar.sql' }
        'Clientes' { 'dashboard-clientes.sql' }
        'Usuarios' { 'dashboard-usuarios.sql' }
        'Raster' { 'dashboard-raster.sql' }
        'Coletores' { 'dashboard-coletores.sql' }
        'Saude' { 'dashboard-saude.sql' }
        'Manifestos' { 'dashboard-manifestos.sql' }
    }
    $checksPath = Join-Path $PSScriptRoot ('../validation/' + $validationScript)
    $checks = [IO.File]::ReadAllText($checksPath) -split '(?m)^-- VALIDATION BATCH\s*$'
    for ($batchIndex=0; $batchIndex -lt $checks.Count; $batchIndex++) {
        $receipt.validationBatch = $batchIndex + 1
        $validation = Invoke-Scalar $checks[$batchIndex]
    }
    if (-not $validation -or $validation -is [DBNull]) { throw 'REFERENCE_VIEW_VALIDATION_MISSING' }
    $receipt.validation = ConvertFrom-Json -InputObject $validation
    if ($Apply -and ($added -gt 0 -or $receipt.capture -or $receipt.captures)) {
        $transaction.Commit()
        $receipt.catalogWritesCommitted = $added -gt 0
        if ($receipt.capture) { $receipt.capture.transactionCommitted = $true }
        if ($receipt.captures) { $receipt.capturesCommitted = $true }
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

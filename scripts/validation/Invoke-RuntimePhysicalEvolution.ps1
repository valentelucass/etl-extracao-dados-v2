param([switch]$Install)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence = Join-Path $root 'target/bloco53/schema'
$encoding = [Text.UTF8Encoding]::new($false, $true)
$installed = Get-Content (Join-Path $evidence 'installed-migrations.json') -Raw | ConvertFrom-Json
if (@($installed).Count -ne 15) { throw 'ORIGINAL_INSTALLATION_LEDGER_REQUIRED' }
foreach ($entry in $installed) {
    $path = Join-Path $root ('database/migrations/' + $entry.file)
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256) {
        throw 'APPLIED_MIGRATION_CHANGED'
    }
}
$runner = Get-Content (Join-Path $PSScriptRoot 'Invoke-RuntimePhysicalSchema.ps1') -Raw
$match = [regex]::Match($runner, '(?s)\$fingerprint = @''\r?\n(.*?)\r?\n''@')
if (-not $match.Success) { throw 'CATALOG_FINGERPRINT_CONTRACT_REQUIRED' }
$fingerprint = $match.Groups[1].Value
$preflight = @'
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR SERVERPROPERTY('IsClustered')<>0 OR SERVERPROPERTY('IsHadrEnabled')<>0
 THROW 52400,N'LOCAL_TARGET_REQUIRED',1;
IF ISNULL(CONVERT(NVARCHAR(40),CONNECTIONPROPERTY('auth_scheme')),N'') NOT IN(N'NTLM',N'KERBEROS')
 THROW 52400,N'WINDOWS_REQUIRED',1;
'@
$counts = @'
GO
DECLARE @counts NVARCHAR(MAX)=(SELECT STRING_AGG(CONVERT(NVARCHAR(MAX),N'SELECT COUNT_BIG(*) n FROM '+QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)),N' UNION ALL ') FROM sys.tables WHERE is_ms_shipped=0);
SET @counts=N'SELECT N''DATA_ROWS=''+CONVERT(NVARCHAR(32),SUM(n)) FROM ('+@counts+N') totals';
EXEC sys.sp_executesql @counts;
GO
'@
function Invoke-Checked([string]$name, [string]$body) {
    $path = Join-Path $evidence ($name + '.sql')
    [IO.File]::WriteAllText($path, $preflight + "`n" + $body, $encoding)
    Push-Location (Join-Path $root 'database/validation')
    try {
        $result = & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -C -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $path 2>&1
        $code = $LASTEXITCODE
        [IO.File]::WriteAllLines($path + '.log', [string[]]$result, $encoding)
        if ($code -ne 0) { throw "ADDITIVE_SQL_FAILED: $name" }
        return $result -join "`n"
    } finally { Pop-Location }
}
$master = & sqlcmd -S localhost -d master -E -C -l 10 -t 10 -b -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE';"
if ($LASTEXITCODE -ne 0 -or ($master -join '').Trim() -cne '1') { throw 'EXISTING_TARGET_PREFLIGHT_FAILED' }
$before = Invoke-Checked 'before-additive' ($fingerprint + "`n" + $counts)
if ($before -notmatch 'SCHEMA_SHA256=e2aca4133c541b6f1e0d9bca09b35d6a79257d4433a44653ed406d34a0e9bf97') {
    throw 'MODERN_V001_V015_CATALOG_DRIFT_NO_RESET_ALLOWED'
}
$dataBefore = [regex]::Match($before, 'DATA_ROWS=(\d+)').Groups[1].Value
$names = @('V016__create_windows_runtime_authority.sql', 'V017__create_runtime_temporal_plan.sql')
$pending = ($names | ForEach-Object { ':r "..\migrations\' + $_ + '"' }) -join "`n"
$baseline = Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql')
$baselinePending = @($baseline | Where-Object { $_ -match '^:r ' } | Select-Object -Skip 15)
if (($baselinePending -join "`n") -cne $pending) { throw 'PENDING_BASELINE_DIVERGES_FROM_MIGRATIONS' }
$checks = "`nGO`n:r `"005_validate_progressive_data_gate.sql`"`nGO`n:r `"051_validate_windows_authority_temporal.sql`"`nGO`n"
$hashes = @()
foreach ($kind in @('individual', 'baseline-pending')) {
    $body = if ($kind -eq 'individual') { $pending } else { $baselinePending -join "`n" }
    $result = Invoke-Checked ('qualify-additive-' + $kind) ("BEGIN TRANSACTION;`n" + $body + $checks + $fingerprint + "`nROLLBACK TRANSACTION;`n" + $counts)
    $hashes += [regex]::Match($result, 'SCHEMA_SHA256=([a-f0-9]{64})').Groups[1].Value
    if ([regex]::Match($result, 'DATA_ROWS=(\d+)').Groups[1].Value -cne $dataBefore) { throw 'ROLLBACK_ROW_COUNTS_CHANGED' }
}
if ($hashes[0] -notmatch '^[a-f0-9]{64}$' -or $hashes[0] -cne $hashes[1]) { throw 'ADDITIVE_CATALOG_EQUIVALENCE_FAILED' }
Write-Output "ADDITIVE_PATHS_EQUAL=$($hashes[0]); RETAINED_ROWS=$dataBefore"
if ($Install) {
    $body = "BEGIN TRANSACTION;`n" + $pending + $checks + $fingerprint + "`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52400,N'INSTALL_TRANSACTION_REQUIRED',1;`nCOMMIT TRANSACTION;`n" + $counts
    $result = Invoke-Checked 'install-additive' $body
    if (-not $result.Contains('SCHEMA_SHA256=' + $hashes[0]) -or [regex]::Match($result, 'DATA_ROWS=(\d+)').Groups[1].Value -cne $dataBefore) { throw 'POST_COMMIT_EVIDENCE_DIVERGED_NO_DESTRUCTIVE_RECOVERY' }
    $receipts = $names | ForEach-Object { [pscustomobject]@{file=$_;sha256=(Get-FileHash (Join-Path $root ('database/migrations/'+$_)) -Algorithm SHA256).Hash.ToLowerInvariant()} }
    [IO.File]::WriteAllText((Join-Path $evidence 'installed-additive-migrations.json'), ($receipts | ConvertTo-Json), $encoding)
    Write-Output 'V016_V017_INSTALLED_ATOMICALLY_NO_IDENTITY_NO_GRANT_NO_DATA_CLEANUP'
}

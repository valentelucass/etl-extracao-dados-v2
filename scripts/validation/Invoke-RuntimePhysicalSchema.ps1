param([switch]$Install)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$validation = Join-Path $root 'database/validation'
$evidence = Join-Path $root 'target/bloco53/schema'
[IO.Directory]::CreateDirectory($evidence) | Out-Null
$encoding = [Text.UTF8Encoding]::new($false, $true)
function Invoke-CheckedSql([string]$file) {
    Push-Location $validation
    try {
        $output = & sqlcmd -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -l 10 -t 30 -b -y 0 -w 65535 -i $file 2>&1
        $code = $LASTEXITCODE
        [IO.File]::WriteAllLines("$file.log", [string[]]$output, $encoding)
        if ($code -ne 0) { throw "SQL_SCHEMA_VALIDATION_FAILED: $file.log" }
        return ($output -join "`n")
    } finally { Pop-Location }
}
$preflight = @'
:On Error exit
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR SERVERPROPERTY('IsClustered')<>0 OR SERVERPROPERTY('IsHadrEnabled')<>0
 THROW 52400,N'LOCAL_TARGET_REQUIRED',1;
IF CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 52400,N'WINDOWS_REQUIRED',1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
:r "..\transition\001_reset_historical_shadow_for_schema_foundation.sql"
'@
$fingerprint = @'
GO
DECLARE @shape NVARCHAR(MAX)=CONCAT(
 (SELECT s.name schema_name,CASE WHEN EXISTS(SELECT 1 FROM sys.key_constraints k WHERE k.object_id=o.object_id AND k.is_system_named=1) THEN LEFT(o.name,LEN(o.name)-8)+N'SYSTEM' ELSE o.name END object_name,o.type,m.definition FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
  LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0 ORDER BY s.name,o.name FOR XML RAW,BINARY BASE64),
 (SELECT s.name schema_name,t.name table_name,c.name column_name,c.column_id,TYPE_NAME(c.user_type_id) type_name,c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,c.collation_name,d.definition,cc.definition computed_definition
  FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.columns c ON c.object_id=t.object_id
  LEFT JOIN sys.default_constraints d ON d.object_id=c.default_object_id LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id
  ORDER BY s.name,t.name,c.column_id FOR XML RAW),
 (SELECT s.name schema_name,t.name table_name,CASE WHEN EXISTS(SELECT 1 FROM sys.key_constraints k WHERE k.parent_object_id=i.object_id AND k.unique_index_id=i.index_id AND k.is_system_named=1) THEN LEFT(i.name,LEN(i.name)-8)+N'SYSTEM' ELSE i.name END index_name,i.type,i.is_unique,i.is_primary_key,i.filter_definition,c.name,ic.key_ordinal,ic.is_descending_key,ic.is_included_column
  FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id JOIN sys.indexes i ON i.object_id=t.object_id
  LEFT JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id LEFT JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
  ORDER BY s.name,t.name,i.name,ic.index_column_id FOR XML RAW),
 (SELECT SCHEMA_NAME(t.schema_id) schema_name,t.name table_name,f.name constraint_name,OBJECT_SCHEMA_NAME(f.referenced_object_id) ref_schema,OBJECT_NAME(f.referenced_object_id) ref_table,
  COL_NAME(fc.parent_object_id,fc.parent_column_id) parent_column,COL_NAME(fc.referenced_object_id,fc.referenced_column_id) ref_column,f.delete_referential_action,f.update_referential_action,f.is_disabled,f.is_not_trusted
  FROM sys.foreign_keys f JOIN sys.tables t ON t.object_id=f.parent_object_id JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id
  ORDER BY schema_name,t.name,f.name,fc.constraint_column_id FOR XML RAW),
 (SELECT OBJECT_SCHEMA_NAME(parent_object_id) schema_name,OBJECT_NAME(parent_object_id) table_name,name,definition,is_disabled,is_not_trusted FROM sys.check_constraints ORDER BY schema_name,table_name,name FOR XML RAW),
 (SELECT name,type,authentication_type FROM sys.database_principals WHERE principal_id>4 ORDER BY name FOR XML RAW),
 (SELECT USER_NAME(grantee_principal_id) grantee,class_desc,CASE class WHEN 1 THEN CONCAT(OBJECT_SCHEMA_NAME(major_id),'.',OBJECT_NAME(major_id)) WHEN 3 THEN SCHEMA_NAME(major_id) ELSE '' END target,state_desc,permission_name
  FROM sys.database_permissions ORDER BY grantee,class_desc,target,permission_name FOR XML RAW));
SELECT CONVERT(XML,@shape);
SELECT N'SCHEMA_SHA256='+LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@shape),2));
'@
$checks = @'
:r "001_validate_schema_foundation.sql"
GO
:r "005_validate_progressive_data_gate.sql"
GO
:r "038_validate_coletas_shadow_vertical.sql"
GO
:r "044_validate_fretes_shadow_vertical.sql"
GO
:r "048_validate_runtime_durable_recovery.sql"
GO
'@
$baseline = ':r "..\baseline\001_schema_foundation_baseline.sql"'
$migrations = (Get-ChildItem (Join-Path $root 'database/migrations/V*.sql') | Sort-Object Name | ForEach-Object { ':r "..\migrations\' + $_.Name + '"' }) -join "`n"
$hashes = @()
foreach ($path in @('baseline','individual')) {
    $body = if ($path -eq 'baseline') { $baseline } else { $migrations }
    $file = Join-Path $evidence "$path.sql"
    [IO.File]::WriteAllText($file, "$preflight`n$body`n$checks`n$fingerprint`nROLLBACK TRANSACTION;`nGO`n", $encoding)
    $result = Invoke-CheckedSql $file
    $match = [regex]::Match($result, 'SCHEMA_SHA256=([a-f0-9]{64})')
    if (-not $match.Success) { throw 'SCHEMA_FINGERPRINT_MISSING' }
    $hashes += $match.Groups[1].Value
}
if ($hashes[0] -cne $hashes[1]) { throw 'BASELINE_MIGRATIONS_DRIFT' }
Write-Output "BASELINE_MIGRATIONS_EQUAL=$($hashes[0])"
if ($Install) {
    $file = Join-Path $evidence 'installation.sql'
    [IO.File]::WriteAllText($file, "$preflight`n$migrations`n$checks`n$fingerprint`nIF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 52400,N'INSTALL_TRANSACTION_REQUIRED',1;`nCOMMIT TRANSACTION;`nGO`n", $encoding)
    $result = Invoke-CheckedSql $file
    if (-not $result.Contains("SCHEMA_SHA256=$($hashes[0])")) { throw 'POST_INSTALL_FINGERPRINT_MISMATCH' }
    Get-ChildItem (Join-Path $root 'database/migrations/V*.sql') | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{ file=$_.Name; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    } | ConvertTo-Json | ForEach-Object { [IO.File]::WriteAllText((Join-Path $evidence 'installed-migrations.json'), $_, $encoding) }
    Write-Output 'INSTALLED_ATOMICALLY_SYNTHETIC_DATA_RETAINED_NO_FLYWAY_HISTORY_CREATED'
}

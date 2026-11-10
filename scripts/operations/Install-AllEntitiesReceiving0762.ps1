param([Parameter(Mandatory=$true)][string]$ReceiptPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -ne 'Desktop'){throw 'ALL_RECEIVING_WINDOWS_PS5_REQUIRED'}
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
$repo0762=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$dir0762=Join-Path $repo0762 'database\production\receiving'
$manifest0762=Get-Content -LiteralPath (Join-Path $dir0762 'all-entities-manifest0762.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($manifest0762.schema -cne 'RECEIVING_ALL_ENTITIES0762' -or $manifest0762.target -cne 'ETL_SISTEMA_V2'){throw 'ALL_RECEIVING_PACKAGE_SCOPE'}
foreach($pin0762 in @(@{name='001_receiving.sql';hash=$manifest0762.base_sha256},@{name='002_source_envelope.sql';hash=$manifest0762.envelope_sha256},@{name='003_all_entities.sql';hash=$manifest0762.sql_sha256})){
 if((Get-FileHash -LiteralPath (Join-Path $dir0762 $pin0762.name)).Hash -cne $pin0762.hash){throw 'ALL_RECEIVING_SQL_PACKAGE_DRIFT'}
}
if(Test-Path -LiteralPath $ReceiptPath){throw 'ALL_RECEIVING_INSTALL_RECEIPT_EXISTS'}
$report0762=[ordered]@{schema='INSTALL_RECEIVING0762';at=[DateTimeOffset]::UtcNow.ToString('o');installed=$false;source_HTTP=0;target='ETL_SISTEMA_V2';sql_sha256=$manifest0762.sql_sha256;ddl_sent=$false;permissionsChanged=$false}
$connection0762=$null;$credential0762=$null;$tx0762=$null
function Get-ReceivingProtected0762($Connection){
 $cmd0762=$Connection.CreateCommand();$cmd0762.CommandTimeout=20
 $cmd0762.CommandText=@'
SELECT
 (SELECT COUNT(*) FROM ctl.production_ingestion_run) AS runs,
 (SELECT COUNT(*) FROM stg.production_export_occurrence) AS occurrences,
 (SELECT COUNT(*) FROM ctl.production_ingestion_coverage) AS coverage,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),(
  SELECT run_id,occurrence_ordinal,CONVERT(varchar(64),envelope_sha256,2) AS sha
  FROM stg.production_export_occurrence ORDER BY run_id,occurrence_ordinal FOR JSON PATH))),2) AS data_sha,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),(
  SELECT class,major_id,minor_id,grantee_principal_id,grantor_principal_id,type,state
  FROM sys.database_permissions ORDER BY class,major_id,minor_id,grantee_principal_id,type FOR JSON PATH))),2) AS grants_sha,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),(
  SELECT principal_id,name,type,default_schema_name,CONVERT(varchar(max),sid,2) AS sid
  FROM sys.database_principals ORDER BY principal_id FOR JSON PATH))),2) AS users_sha,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(nvarchar(max),(
  SELECT role_principal_id,member_principal_id FROM sys.database_role_members
  ORDER BY role_principal_id,member_principal_id FOR JSON PATH))),2) AS roles_sha;
'@
 try{$reader0762=$cmd0762.ExecuteReader();try{if(-not $reader0762.Read()){throw 'ALL_RECEIVING_PROTECTED_SNAPSHOT_MISSING'};$snapshot0762=[ordered]@{};for($i0762=0;$i0762 -lt $reader0762.FieldCount;$i0762++){$snapshot0762[$reader0762.GetName($i0762)]=$reader0762.GetValue($i0762)};return $snapshot0762}finally{$reader0762.Dispose()}}finally{$cmd0762.Dispose()}
}
try{
 Add-Type -Path 'C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
 $sql0762=[IO.File]::ReadAllText((Join-Path $dir0762 '003_all_entities.sql'))
 if($sql0762 -match '(?im)^\s*(GRANT|DENY|REVOKE|ALTER\s+LOGIN|CREATE\s+LOGIN|ALTER\s+USER|CREATE\s+USER)\b'){throw 'ALL_RECEIVING_PERMISSION_STATEMENT_REFUSED'}
 $parser0762=New-Object Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser($true);$reader0762=New-Object IO.StringReader($sql0762);$errors0762=$null
 try{$null=$parser0762.Parse($reader0762,[ref]$errors0762)}finally{$reader0762.Dispose()}
 if($errors0762.Count){$report0762.parseErrorCount=$errors0762.Count;throw 'ALL_RECEIVING_SQL_PARSE_FAILED'}
 $report0762.parsed=$true
 Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
 $credential0762=Get-ProjetosSqlAdminCredential
 $connection0762=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential0762 -ApplicationName 'ETL-V2-0762-All-Receiving-Migration'
 $connection0762.Open()
 $cmd0762=$connection0762.CreateCommand();$cmd0762.CommandTimeout=10
 $cmd0762.CommandText=@'
IF DB_NAME()<>'ETL_SISTEMA_V2' OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion'))<>'16.0.1000.6' THROW 51920,'ALL_RECEIVING_EXACT_TARGET_REQUIRED',1;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'ETL_V2_PRODUCTION_RECEIVING',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0;
IF @lock<0 THROW 51922,'ALL_RECEIVING_ACTIVE_WRITER_REFUSED',1;
IF (SELECT COUNT(*) FROM ctl.production_installation_step)<>12
 OR NOT EXISTS(SELECT 1 FROM ctl.production_installation_step WHERE step_id='0759-receiving' AND source_sha256=@base)
 OR NOT EXISTS(SELECT 1 FROM ctl.production_installation_step WHERE step_id='0759-source-envelope' AND source_sha256=@envelope)
 THROW 51921,'ALL_RECEIVING_BASE_JOURNAL_DRIFT',1;
IF EXISTS(SELECT 1 FROM ctl.production_ingestion_run WHERE run_state='RUNNING') THROW 51922,'ALL_RECEIVING_ACTIVE_RUN_REFUSED',1;
'@
 $null=$cmd0762.Parameters.AddWithValue('@base',$manifest0762.base_sha256);$null=$cmd0762.Parameters.AddWithValue('@envelope',$manifest0762.envelope_sha256)
 try{$null=$cmd0762.ExecuteNonQuery()}finally{$cmd0762.Dispose()}
 $before0762=Get-ReceivingProtected0762 $connection0762
 $tx0762=$connection0762.BeginTransaction()
 foreach($batch0762 in [regex]::Split($sql0762,'(?im)^\s*GO\s*\r?$')){
  if($batch0762.Trim()){$cmd0762=$connection0762.CreateCommand();$cmd0762.Transaction=$tx0762;$cmd0762.CommandTimeout=60;$cmd0762.CommandText=$batch0762;try{$report0762.ddl_sent=$true;$null=$cmd0762.ExecuteNonQuery()}finally{$cmd0762.Dispose()}}
 }
 $cmd0762=$connection0762.CreateCommand();$cmd0762.Transaction=$tx0762;$cmd0762.CommandTimeout=10
 $cmd0762.CommandText="IF (SELECT COUNT(*) FROM ctl.production_export_template)<>11 OR EXISTS(SELECT 1 FROM ctl.production_export_template t WHERE t.expected_field_count<>(SELECT COUNT(*) FROM ctl.production_export_field f WHERE f.entity_name=t.entity_name)) THROW 51926,'ALL_RECEIVING_CATALOG_COUNT_DRIFT',1; INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('0762-all-entities',N'production/receiving/003_all_entities.sql',@hash);"
 $null=$cmd0762.Parameters.AddWithValue('@hash',$manifest0762.sql_sha256);try{$null=$cmd0762.ExecuteNonQuery()}finally{$cmd0762.Dispose()}
 $tx0762.Commit();$tx0762.Dispose();$tx0762=$null
 $report0762.installed=$true
 $after0762=Get-ReceivingProtected0762 $connection0762
 $report0762.before=$before0762;$report0762.after=$after0762
 foreach($key0762 in $before0762.Keys){if($before0762[$key0762] -cne $after0762[$key0762]){throw 'ALL_RECEIVING_PROTECTED_STATE_DRIFT'}}
 $report0762.protectedStateExact=$true
}catch{
 if($tx0762){try{$tx0762.Rollback()}catch{};$tx0762.Dispose()}
 $error0762=$_.Exception;while($error0762.InnerException){$error0762=$error0762.InnerException}
 $report0762.failure_type=$error0762.GetType().Name
 $report0762.failure_code=if($error0762.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$error0762.Message}else{'DETAILS_WITHHELD'}
 if($error0762 -is [System.Data.SqlClient.SqlException]){$report0762.sqlErrorNumber=$error0762.Number}
}finally{
 if($connection0762){$connection0762.Dispose()};if($credential0762){$credential0762.Password.Dispose()}
 [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptPath),($report0762|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
}
if(-not $report0762.installed -or -not $report0762.protectedStateExact){exit 1}
Write-Output 'ALL_ENTITIES_RECEIVING_INSTALLED'

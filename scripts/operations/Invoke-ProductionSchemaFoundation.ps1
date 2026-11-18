[CmdletBinding()]
param([switch]$Install,[Parameter(Mandatory=$true)][string]$ReceiptPath)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -eq 'Desktop'){
 $env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifest=Get-Content -LiteralPath (Join-Path $root 'database\production\shared-foundation\manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($manifest.package -cne '0758-shared-foundation-v1' -or $manifest.target -cne 'ETL_SISTEMA_V2' -or $manifest.product_version -cne '16.0.1000.6'){throw 'SHARED_FOUNDATION_SCOPE_MISMATCH'}
$expected=@('V003__create_control_plane.sql','V004__create_staging_promotion_kernel.sql','V005__create_staging_lifecycle.sql','V006__create_observability_data_quality.sql','V007__create_usuarios_current_history.sql','V008__create_governed_references.sql','V009__create_usuario_dimension_current_view.sql')
if(@($manifest.sources).Count -ne 7){throw 'SHARED_FOUNDATION_SOURCE_COUNT'}
foreach($name in $expected){
 $entries=@($manifest.sources|Where-Object name -CEQ $name)
 if($entries.Count -ne 1){throw 'SHARED_FOUNDATION_SOURCE_IDENTITY'}
 $hash=[Security.Cryptography.SHA256]::Create();$stream=[IO.File]::OpenRead((Join-Path $root ('database\migrations\'+$name)))
 try{$actual=[BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','')}finally{$stream.Dispose();$hash.Dispose()}
 if($actual -cne $entries[0].sha256){throw 'SHARED_FOUNDATION_SOURCE_CHANGED'}
}
$ReceiptPath=[IO.Path]::GetFullPath($ReceiptPath)
if(Test-Path -LiteralPath $ReceiptPath){throw 'SHARED_FOUNDATION_RECEIPT_EXISTS'}
$receipt=[ordered]@{unit='0758_SHARED_FOUNDATION';target='ETL_SISTEMA_V2';at=[DateTimeOffset]::Now.ToString('o');install_requested=[bool]$Install;ddl_sent=$false;committed=$false;qualified=$false;source_files=$manifest.sources;schema_steps=@();flyway_history_created=$false;business_data_loaded=$false;complete_production_entities=$false;status='NOT_STARTED'}
$credential=$null;$runtime=$null;$connection=$null;$transaction=$null
function Invoke-FoundationSql([string]$Sql){
 $command=$connection.CreateCommand();$command.CommandTimeout=20;$command.CommandText=$Sql
 if($transaction){$command.Transaction=$transaction}
 try{$null=$command.ExecuteNonQuery()}finally{$command.Dispose()}
}
function Read-FoundationScalar([string]$Sql){
 $command=$connection.CreateCommand();$command.CommandTimeout=10;$command.CommandText=$Sql
 if($transaction){$command.Transaction=$transaction}
 try{return $command.ExecuteScalar()}finally{$command.Dispose()}
}
try{
 Add-Type -Path 'C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
 $parser=New-Object Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser($true)
 $contents=@{};$tables=@()
 foreach($name in $expected){
  $sql=[IO.File]::ReadAllText((Join-Path $root ('database\migrations\'+$name)))
  if($sql -match '(?im)\b(?:CREATE\s+LOGIN|ALTER\s+LOGIN|CREATE\s+USER|ALTER\s+DATABASE|USE\s+\[?|xp_cmdshell|sp_configure|OPENROWSET)\b' -or $sql -match '\bETL_SISTEMA(?:_V2(?:_SHADOW)?)?\b'){throw 'SHARED_FOUNDATION_FORBIDDEN_SCOPE'}
  $reader=New-Object IO.StringReader($sql);$errors=$null
  try{$null=$parser.Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
  if($errors.Count){throw 'SHARED_FOUNDATION_SQL160_PARSE_FAILED'}
  $contents[$name]=$sql
  foreach($match in [regex]::Matches($sql,'(?im)^\s*CREATE TABLE ((?:ctl|stg|core|ref|mart|pub|recon)\.[a-z0-9_]+)\s*\(')){$tables+=@($match.Groups[1].Value)}
 }
 $receipt.package_parse_passed=7;$receipt.expected_tables=$tables.Count
 if(-not $Install){$receipt.status='PACKAGE_VALIDATED_NO_SQL';$receipt.qualified=$true}
 else{
  Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
  $credential=Get-ProjetosSqlAdminCredential
  $connection=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-V2-0758-Shared-Foundation'
  $connection.Open()
  Invoke-FoundationSql @'
IF DB_NAME()<>N'ETL_SISTEMA_V2' OR CONVERT(VARCHAR(32),SERVERPROPERTY('ProductVersion'))<>'16.0.1000.6'
 THROW 51830,N'SHARED_FOUNDATION_EXACT_TARGET_REQUIRED',1;
IF OBJECT_ID(N'ctl.production_installation_step',N'U') IS NULL OR OBJECT_ID(N'pub.v2_installation_status',N'V') IS NULL
 THROW 51831,N'SHARED_FOUNDATION_APPROVED_SECURITY_REQUIRED',1;
IF (SELECT COUNT(*) FROM ctl.production_installation_step)<>3 OR OBJECT_ID(N'ctl.source_catalog') IS NOT NULL
 THROW 51832,N'SHARED_FOUNDATION_EXISTING_TARGET_REQUIRES_RECONCILIATION',1;
'@
  $transaction=$connection.BeginTransaction()
  foreach($name in $expected){
   $receipt.phase=$name
   foreach($batch in [regex]::Split($contents[$name],'(?im)^\s*GO\s*\r?$')){
    if($batch.Trim()){$receipt.ddl_sent=$true;Invoke-FoundationSql $batch}
   }
   if((Read-FoundationScalar 'SELECT @@TRANCOUNT') -ne 1){throw 'SHARED_FOUNDATION_TRANSACTION_BOUNDARY_CHANGED'}
   $entry=@($manifest.sources|Where-Object name -CEQ $name)[0]
   $command=$connection.CreateCommand();$command.Transaction=$transaction;$command.CommandTimeout=10
   $command.CommandText='INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@step,@name,@hash)'
   $null=$command.Parameters.AddWithValue('@step',('0758-'+$name.Substring(0,4)))
   $null=$command.Parameters.AddWithValue('@name',$name);$null=$command.Parameters.AddWithValue('@hash',$entry.sha256)
   try{$null=$command.ExecuteNonQuery()}finally{$command.Dispose()}
   $receipt.schema_steps+=@([ordered]@{name=$name;acknowledged_in_transaction=$true})
  }
  foreach($table in $tables){if((Read-FoundationScalar ("SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID(N'"+$table+"',N'U')")) -ne 1){throw 'SHARED_FOUNDATION_REQUIRED_TABLE_MISSING'}}
  Invoke-FoundationSql @'
IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1)
 OR EXISTS(SELECT 1 FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1)
 THROW 51833,N'SHARED_FOUNDATION_CONSTRAINT_NOT_TRUSTED',1;
IF (SELECT COUNT(*) FROM pub.v2_installation_status)<>10
 THROW 51834,N'SHARED_FOUNDATION_JOURNAL_MISMATCH',1;
'@
  $transaction.Commit();$transaction.Dispose();$transaction=$null;$receipt.committed=$true
  $receipt.actual_tables=[int](Read-FoundationScalar 'SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0')
  $receipt.empty_executions=[long](Read-FoundationScalar 'SELECT COUNT_BIG(*) FROM ctl.execution_attempt') -eq 0
  $connection.Dispose()
  $runtime=Import-Clixml -LiteralPath (Join-Path $env:LOCALAPPDATA 'Rodogarcia\SqlServer\etl-v2-runtime-prod.clixml')
  $connection=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $runtime -ApplicationName 'ETL-V2-0758-Runtime-Health-Read-Only'
  $connection.Open()
  $command=$connection.CreateCommand();$command.CommandTimeout=10;$command.CommandText='EXEC ctl.usp_observe_platform_health @maximum_running_age_seconds=3600'
  try{
   $reader=$command.ExecuteReader();$sets=0;$rows=0
   try{do{$sets++;while($reader.Read()){$rows++}}while($reader.NextResult())}finally{$reader.Dispose()}
   $receipt.runtime_health_entrypoint=[ordered]@{executed=$true;result_sets=$sets;rows=$rows}
  }finally{$command.Dispose()}
  $receipt.qualified=$true;$receipt.status='SHARED_FOUNDATION_INSTALLED_DOMAIN_PRODUCTION_CONTRACTS_PENDING'
 }
}catch{
 if($transaction){try{$transaction.Rollback()}catch{};$transaction.Dispose();$transaction=$null}
 $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
 $receipt.status=if($receipt.ddl_sent){'RECONCILE_EXACT_TARGET_NO_AUTOMATIC_RETRY'}else{'REFUSED_BEFORE_DDL'}
 $receipt.failure=[ordered]@{type=$cause.GetType().Name;number=$cause.Number;code=$(if($cause.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$cause.Message}else{'DETAILS_WITHHELD'})}
}finally{
 if($connection){$connection.Dispose()};if($credential){$credential.Password.Dispose()};if($runtime){$runtime.Password.Dispose()}
 $receipt.completed_at=[DateTimeOffset]::Now.ToString('o')
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($ReceiptPath))|Out-Null
 [IO.File]::WriteAllText($ReceiptPath,($receipt|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
}
Write-Output $receipt.status
if(-not $receipt.qualified){exit 1}

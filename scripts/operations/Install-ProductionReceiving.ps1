[CmdletBinding()]
param([switch]$Install,[Parameter(Mandatory=$true)][string]$ReceiptPath)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$package=Join-Path $repo 'database\production\receiving'
$manifest=Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
$sqlPath=Join-Path $package '001_receiving.sql'
if($manifest.target -cne 'ETL_SISTEMA_V2' -or $manifest.package -cne '0759-receiving-v1' -or (Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash -cne $manifest.sql_sha256){throw 'RECEIVING_PACKAGE_DRIFT'}
$ReceiptPath=[IO.Path]::GetFullPath($ReceiptPath)
if(Test-Path -LiteralPath $ReceiptPath){throw 'RECEIVING_RECEIPT_EXISTS'}
$r=[ordered]@{unit='0759';started_at=[DateTimeOffset]::Now.ToString('o');target='ETL_SISTEMA_V2';sql_sha256=$manifest.sql_sha256;installed=$false;parsed=$false;ddl_sent=$false;business_data_loaded=$false}
$connection=$null;$credential=$null;$transaction=$null
try{
 Add-Type -Path 'C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
 $sql=[IO.File]::ReadAllText($sqlPath);$parser=New-Object Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser($true)
 $reader=New-Object IO.StringReader($sql);$errors=$null
 try{$null=$parser.Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
 if($errors.Count){throw 'RECEIVING_SQL_PARSE_FAILED'}
 $r.parsed=$true
 if($Install){
  Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
  $credential=Get-ProjetosSqlAdminCredential
  $connection=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-V2-0759-Receiving-Install'
  $connection.Open()
  $cmd=$connection.CreateCommand();$cmd.CommandTimeout=10
  $cmd.CommandText="IF DB_NAME()<>N'ETL_SISTEMA_V2' OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion'))<>'16.0.1000.6' THROW 51910,'RECEIVING_EXACT_TARGET_REQUIRED',1; IF (SELECT COUNT(*) FROM ctl.production_installation_step)<>10 OR OBJECT_ID('ctl.production_ingestion_run') IS NOT NULL THROW 51911,'RECEIVING_EXISTING_STATE_REFUSED',1;"
  try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
  $transaction=$connection.BeginTransaction()
  foreach($batch in [regex]::Split($sql,'(?im)^\s*GO\s*\r?$')){
   if(-not $batch.Trim()){continue}
   $cmd=$connection.CreateCommand();$cmd.Transaction=$transaction;$cmd.CommandTimeout=20;$cmd.CommandText=$batch
   try{$r.ddl_sent=$true;$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
  }
  $cmd=$connection.CreateCommand();$cmd.Transaction=$transaction;$cmd.CommandTimeout=10
  $cmd.CommandText="INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('0759-receiving',N'production/receiving/001_receiving.sql',@hash); IF EXISTS(SELECT 1 FROM sys.check_constraints WHERE is_disabled=1 OR is_not_trusted=1) THROW 51912,'RECEIVING_UNTRUSTED_CONSTRAINT',1;"
  $null=$cmd.Parameters.AddWithValue('@hash',$manifest.sql_sha256)
  try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
  $transaction.Commit();$transaction.Dispose();$transaction=$null;$r.installed=$true
 }
}catch{
 if($transaction){try{$transaction.Rollback()}catch{};$transaction.Dispose()}
 $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
 $r.failure_type=$cause.GetType().Name
 $r.failure_code=if($cause.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$cause.Message}else{'DETAILS_WITHHELD'}
 if($cause.PSObject.Properties['Number']){$r.sql_number=$cause.Number}
}finally{
 if($connection){$connection.Dispose()};if($credential){$credential.Password.Dispose()}
 $r.completed_at=[DateTimeOffset]::Now.ToString('o')
 [IO.File]::WriteAllText($ReceiptPath,($r|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
}
if($r.Contains('failure_code')){exit 1}
Write-Output $(if($Install){'RECEIVING_INSTALLED'}else{'RECEIVING_PACKAGE_VALIDATED_NO_SQL'})

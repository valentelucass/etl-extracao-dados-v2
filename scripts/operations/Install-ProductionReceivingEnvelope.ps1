param([Parameter(Mandatory=$true)][string]$ReceiptPath)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'));$dir=Join-Path $repo 'database\production\receiving'
$manifest=Get-Content -LiteralPath (Join-Path $dir 'envelope-manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($manifest.package -cne '0759-receiving-envelope-v1' -or $manifest.target -cne 'ETL_SISTEMA_V2' -or (Get-FileHash -LiteralPath (Join-Path $dir '002_source_envelope.sql')).Hash -cne $manifest.sql_sha256 -or (Get-FileHash -LiteralPath (Join-Path $dir '001_receiving.sql')).Hash -cne $manifest.base_sha256){throw 'ENVELOPE_CORRECTION_PACKAGE_DRIFT'}
if(Test-Path -LiteralPath $ReceiptPath){throw 'ENVELOPE_CORRECTION_RECEIPT_EXISTS'}
$r=[ordered]@{at=[DateTimeOffset]::Now.ToString('o');installed=$false;source_HTTP=0;target='ETL_SISTEMA_V2';base_sha256=$manifest.base_sha256;sql_sha256=$manifest.sql_sha256;ddl_sent=$false}
$c=$null;$credential=$null;$tx=$null
try{
 Add-Type -Path 'C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
 $sql=[IO.File]::ReadAllText((Join-Path $dir '002_source_envelope.sql'))
 $parser=New-Object Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser($true);$reader=New-Object IO.StringReader($sql);$errors=$null
 try{$null=$parser.Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
 if($errors.Count){throw 'ENVELOPE_SQL_PARSE_FAILED'}
 $r.parsed=$true
 Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
 $credential=Get-ProjetosSqlAdminCredential;$c=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-V2-0759-Envelope-Correction';$c.Open()
 $cmd=$c.CreateCommand();$cmd.CommandTimeout=10
 $cmd.CommandText="IF DB_NAME()<>'ETL_SISTEMA_V2' OR CONVERT(varchar(32),SERVERPROPERTY('ProductVersion'))<>'16.0.1000.6' THROW 51920,'ENVELOPE_EXACT_TARGET_REQUIRED',1; IF (SELECT COUNT(*) FROM ctl.production_installation_step)<>11 OR NOT EXISTS(SELECT 1 FROM ctl.production_installation_step WHERE step_id='0759-receiving' AND source_sha256=@hash) THROW 51921,'ENVELOPE_BASE_JOURNAL_DRIFT',1; IF EXISTS(SELECT 1 FROM ctl.production_ingestion_run WHERE run_state='RUNNING') THROW 51922,'ENVELOPE_ACTIVE_RUN_REFUSED',1;"
 $null=$cmd.Parameters.AddWithValue('@hash',$manifest.base_sha256)
 try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
 $tx=$c.BeginTransaction()
 foreach($batch in [regex]::Split($sql,'(?im)^\s*GO\s*\r?$')){if($batch.Trim()){$cmd=$c.CreateCommand();$cmd.Transaction=$tx;$cmd.CommandTimeout=20;$cmd.CommandText=$batch;try{$r.ddl_sent=$true;$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}}}
 $cmd=$c.CreateCommand();$cmd.Transaction=$tx;$cmd.CommandTimeout=10;$cmd.CommandText="INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('0759-source-envelope',N'production/receiving/002_source_envelope.sql',@hash)"
 $null=$cmd.Parameters.AddWithValue('@hash',$manifest.sql_sha256);try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
 $tx.Commit();$tx.Dispose();$tx=$null;$r.installed=$true
}catch{
 if($tx){try{$tx.Rollback()}catch{};$tx.Dispose()}
 $e=$_.Exception;while($e.InnerException){$e=$e.InnerException};$r.failure_type=$e.GetType().Name;$r.failure_code=if($e.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$e.Message}else{'DETAILS_WITHHELD'}
}finally{
 if($c){$c.Dispose()};if($credential){$credential.Password.Dispose()};[IO.File]::WriteAllText($ReceiptPath,($r|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
}
if(-not $r.installed){exit 1}
Write-Output 'RECEIVING_ENVELOPE_CORRECTION_INSTALLED'

#Requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedPackageSha256,
 [Parameter(Mandatory)][int]$ExpectedDatabaseRows,
 [Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,30}$')][string]$ProofId)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$package=Join-Path $root 'database/proposals/bloco54-temporal-continuation'
$evidence=Join-Path $root ('target/bloco54/completion-authorized-20260908/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
$active=$false
$results=[Collections.Generic.List[object]]::new()
function Record($value){$results.Add($value);[IO.File]::WriteAllText((Join-Path $evidence 'results.json'),($results|ConvertTo-Json -Depth 6),$encoding)}
function Sql([string]$Name,[string]$Body,[string]$Database='ETL_SISTEMA_V2_SHADOW'){
 if($Database -cnotin @('master','ETL_SISTEMA_V2_SHADOW')){throw 'EXACT_SQL_TARGET_REQUIRED'}
 $file=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($file,$Body,$encoding)
 Push-Location $package
 try {& sqlcmd -S localhost -d $Database -E -N -f 65001 -l 10 -t 20 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.log'));$code=$LASTEXITCODE}finally{Pop-Location}
 if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
 return [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.log')))
}
try {
 if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
 if(Test-Path -LiteralPath $evidence){throw 'EVIDENCE_EXISTS_PRESERVE'}
 [void][IO.Directory]::CreateDirectory($evidence)
 $manifestFile=Join-Path $package 'manifest.json'
 if((Get-FileHash $manifestFile).Hash.ToLowerInvariant() -cne $ExpectedPackageSha256){throw 'EXACT_REVIEWED_PACKAGE_REQUIRED'}
 $manifest=Get-Content $manifestFile -Raw|ConvertFrom-Json
 if($manifest.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $manifest.grantDelta -ne 0 -or $manifest.expectedGrants -ne 25){throw 'EXACT_SCHEMA_ONLY_SCOPE_REQUIRED'}
 foreach($file in $manifest.files){if((Get-FileHash (Join-Path $package $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'PACKAGE_FILE_CHANGED'}}
 $migration=Join-Path $root ('database/migrations/'+$manifest.migration)
 if((Get-FileHash $migration).Hash.ToLowerInvariant() -cne $manifest.migrationSha256){throw 'MIGRATION_CHANGED'}
 foreach($file in @($PSCommandPath,$manifestFile,$migration,(Join-Path $package 'verify.sql'),(Join-Path $package 'apply.sql'))){[IO.File]::Copy($file,(Join-Path $evidence ('tested-'+[IO.Path]::GetFileName($file))),$false)}
 Sql 'master-target' "SET NOCOUNT ON; IF (SELECT COUNT_BIG(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE' AND is_read_only=0)<>1 THROW 52754,N'EXACT_TARGET_NOT_READY',1; SELECT N'EXACT_SHADOW_TARGET_ONLY';" 'master'|Out-Null
 $catalog=Get-Content (Join-Path $root 'target/bloco54/residual-runtime/schema-after.sql') -Raw
 $before=Sql 'schema-before' $catalog
 if($before -cnotmatch '(?m)^SCHEMA_SHA256=6a4d90971e7a111d695ace72cac98983fef8a1ef6b080153b0897a8dc27af45a\s*$' -or $before -cnotmatch ('(?m)^DATA_ROWS='+$ExpectedDatabaseRows+'\s*$')){throw 'EXACT_V020_CHECKPOINT_REQUIRED'}
 Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
 Start-Bloco54CompletionCampaign '21e720af862253dd98504b70d29d36ec7fba01d2b11cb324c5aa8e42142e3bf5' ('temporal-schema-'+$ProofId)|Out-Null
 $active=$true
 $install=Get-Content (Join-Path $package 'apply.sql') -Raw
 $baseline=Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql')
 if($baseline[-1] -cne ':r "..\migrations\V021__bind_durable_temporal_intent.sql"'){throw 'EXACT_PENDING_BASELINE_TAIL_REQUIRED'}
 $qualified=@()
 foreach($route in @('upgrade','baseline-tail')){
  Add-Bloco54Reservation ($ProofId+'_'+$route) 'PHYSICAL_V021_TRANSACTIONAL_QUALIFICATION'|Out-Null
  $body=$install.Replace('COMMIT TRANSACTION;',("GO`n"+$catalog+"`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52754,N'ROLLBACK_GUARD',1; ROLLBACK TRANSACTION;"))
  if($route -ceq 'baseline-tail'){$body=$body.Replace(':r "..\..\migrations\V021__bind_durable_temporal_intent.sql"',($baseline[-1].Replace('..\migrations','..\..\migrations')))}
  $body=$body.Replace('V021_COMMITTED_SCHEMA_ONLY_NO_GRANTS','V021_QUALIFIED_ROLLBACK_NO_DATA_OR_GRANTS')
  $out=Sql ($route+'-qualification') $body
  $hash=[regex]::Match($out,'SCHEMA_SHA256=([a-f0-9]{64})').Groups[1].Value
  if($hash.Length -ne 64 -or $out -cnotmatch ('(?m)^DATA_ROWS='+$ExpectedDatabaseRows+'\s*$')){throw 'QUALIFICATION_COUNTS_REQUIRED'}
  $qualified+=$hash
  if((Sql ($route+'-rollback-check') $catalog).Trim() -cne $before.Trim()){throw 'ROLLBACK_NOT_EXACTLY_RESTORED'}
  Record ([ordered]@{id=$route;catalogSha256=$hash;dataRows=$ExpectedDatabaseRows;rolledBack=$true;passed=$true})
 }
 if($qualified[0] -cne $qualified[1]){throw 'BASELINE_UPGRADE_CATALOG_DIFFERENT'}
 Add-Bloco54Reservation ($ProofId+'_APPLY') 'PHYSICAL_ADDITIVE_V021_SCHEMA_ONLY'|Out-Null
 Sql 'apply' $install|Out-Null
 Sql 'verify-new-connection' (Get-Content (Join-Path $package 'verify.sql') -Raw)|Out-Null
 $after=Sql 'schema-after' $catalog
 if($after -cnotmatch ('(?m)^SCHEMA_SHA256='+$qualified[0]+'\s*$') -or $after -cnotmatch ('(?m)^DATA_ROWS='+$ExpectedDatabaseRows+'\s*$')){throw 'COMMITTED_CATALOG_REQUIRES_RECONCILIATION'}
 Sql 'preservation-after' (Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V6_CALENDAR_01/preservation-after.sql') -Raw)|Out-Null
 Record ([ordered]@{id='V021_APPLIED_VERIFIED';catalogSha256=$qualified[0];dataRows=$ExpectedDatabaseRows;grantDelta=0;passed=$true})
}catch{
 $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
 $reason=[string]$cause.Message;if($reason -cnotmatch '^[A-Za-z0-9_-]{1,140}$'){$reason='SCHEMA_FAILURE_REDACTED'}
 Record ([ordered]@{id='SCHEMA_CONTROLLER_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;passed=$false})
}finally{
 if($active){Stop-Bloco54Campaign ('schema-'+$ProofId+'-finished-inspect-records-before-any-repeat')|Out-Null}
 [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

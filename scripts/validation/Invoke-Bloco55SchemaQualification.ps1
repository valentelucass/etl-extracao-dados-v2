param([Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedPackageSha256,
 [ValidateSet('upgrade','baseline-tail')][string]$Route='upgrade',[switch]$ExerciseManifesto,
 [ValidateSet('bloco55-runtime-extension','bloco55-output-projection')][string]$Package='bloco55-runtime-extension')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$proposal=Join-Path $root ('database/proposals/'+$Package)
$evidence=Join-Path $root ('target/bloco55/schema/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
if(Test-Path $evidence){throw 'EXISTING_EVIDENCE_PRESERVE'}
[void][IO.Directory]::CreateDirectory($evidence)
if((Get-FileHash (Join-Path $proposal 'manifest.json')).Hash.ToLowerInvariant() -cne $ExpectedPackageSha256){throw 'PACKAGE_HASH_MISMATCH'}
$manifest=Get-Content (Join-Path $proposal 'manifest.json') -Raw|ConvertFrom-Json
foreach($f in $manifest.files){if((Get-FileHash (Join-Path $proposal $f.path)).Hash.ToLowerInvariant() -cne $f.sha256){throw 'PACKAGE_FILE_CHANGED'}}
$migration=Join-Path $root ('database/migrations/'+$manifest.migration)
if((Get-FileHash $migration).Hash.ToLowerInvariant() -cne $manifest.migrationSha256){throw 'MIGRATION_CHANGED'}
foreach($f in @($migration,(Join-Path $proposal 'manifest.json'))){[IO.File]::Copy($f,(Join-Path $evidence ([IO.Path]::GetFileName($f))),$false)}
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
Add-Bloco55Reservation $ProofId 'SQL_SCHEMA_ROLLBACK_QUALIFICATION'|Out-Null
function Sql([string]$Name,[string]$Body,[string]$Database='ETL_SISTEMA_V2_SHADOW'){
 if($Database -cnotin @('master','ETL_SISTEMA_V2_SHADOW')){throw 'EXACT_DATABASE_REQUIRED'}
 $path=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($path,$Body,$encoding)
 Push-Location $proposal
 try{& sqlcmd -S localhost -d $Database -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $path *> (Join-Path $evidence ($Name+'.log'));$code=$LASTEXITCODE}finally{Pop-Location}
 if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
 [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.log')))
}
$catalog=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/schema-after.sql') -Raw
$before='';$passed=$false;$reason='';$qualified=''
try{
 Sql 'master-target' "SET NOCOUNT ON; IF (SELECT COUNT_BIG(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE' AND is_read_only=0)<>1 THROW 52850,N'EXACT_TARGET_REQUIRED',1; SELECT N'EXACT_TARGET_CONFIRMED';" 'master'|Out-Null
 $before=Sql 'before' $catalog
 if($before -notmatch ('SCHEMA_SHA256='+$manifest.previousCatalog) -or $before -notmatch ('DATA_ROWS='+$manifest.previousRows)){throw 'EXACT_CHECKPOINT_REQUIRED'}
 $body=Get-Content (Join-Path $proposal 'apply.sql') -Raw
 if($Route -ceq 'baseline-tail'){
  $tail=(Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql'))[-1]
  if($tail -cne (':r "..\migrations\'+$manifest.migration+'"')){throw 'BASELINE_TAIL_REQUIRED'}
  $body=$body.Replace((':r "..\..\migrations\'+$manifest.migration+'"'),$tail.Replace('..\migrations','..\..\migrations'))
 }
 $body=$body.Replace('COMMIT TRANSACTION;',("GO`n"+$catalog+"`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52850,N'ROLLBACK_REQUIRED',1; ROLLBACK TRANSACTION;"))
 $body=$body.Replace('V022_SCHEMA_COMMITTED','V022_QUALIFIED_ROLLBACK')
 $out=Sql 'qualify' $body
 $qualified=[regex]::Match($out,'SCHEMA_SHA256=([a-f0-9]{64})').Groups[1].Value
 if($qualified.Length -ne 64 -or $out -notmatch ('DATA_ROWS='+$manifest.previousRows)){throw 'QUALIFICATION_COUNTS_REQUIRED'}
 if($ExerciseManifesto){
  Add-Bloco55Reservation ($ProofId+'_MAN') 'SQL_TWO_PAGE_REDUCER_DQ_APPLY_ROLLBACK'|Out-Null
  $exercise=(Get-Content (Join-Path $proposal 'apply.sql') -Raw).Replace('COMMIT TRANSACTION;',":r `"exercise-manifesto.sql`"`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52850,N'ROLLBACK_REQUIRED',1; ROLLBACK TRANSACTION;")
  $exercise=$exercise.Replace('V022_SCHEMA_COMMITTED','B55_SQL_EXERCISE_ROLLED_BACK')
  $out=Sql 'exercise-manifesto' $exercise
  if(-not $out.Contains('B55_MANIFESTO_SQL_TWO_PAGES_ONE_ROOT_FOUR_CHILDREN_DQ_PUBLICATION_PASS')){throw 'MANIFESTO_EXERCISE_PROOF_REQUIRED'}
 }
 $passed=$true
}catch{$reason=$_.Exception.Message}
finally{
 $restored=Sql 'after-rollback' $catalog
 if($before -and $before.Trim() -cne $restored.Trim()){$passed=$false;$reason='ROLLBACK_PRESERVATION_FAILED'}
 $receipt=[ordered]@{id=$ProofId;route=$Route;packageSha256=$ExpectedPackageSha256;migrationSha256=$manifest.migrationSha256;
  qualifiedCatalog=$qualified;previousCatalog=$manifest.previousCatalog;rows=$manifest.previousRows;passed=$passed;reason=$reason;layer='SQL_REAL_ROLLBACK';grantsChanged=0}
 [IO.File]::WriteAllText((Join-Path $evidence 'receipt.json'),($receipt|ConvertTo-Json),$encoding)
 $receipt|ConvertTo-Json -Compress
}
if(-not $passed){exit 1}

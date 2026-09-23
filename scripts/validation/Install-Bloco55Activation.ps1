param([Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId,
 [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedPackageSha256)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root 'database/proposals/bloco55-runtime-extension/activation'
$evidence=Join-Path $root ('target/bloco55/activation/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
if(Test-Path $evidence){throw 'EXISTING_EVIDENCE_PRESERVE'}
if((Get-FileHash (Join-Path $directory 'manifest.json')).Hash.ToLowerInvariant() -cne $ExpectedPackageSha256){throw 'EXACT_ACTIVATION_PACKAGE_REQUIRED'}
$manifest=Get-Content (Join-Path $directory 'manifest.json') -Raw|ConvertFrom-Json
if($manifest.grantDelta -ne 7 -or $manifest.scopeDelta -ne 6 -or $manifest.dqPolicies -ne 3 -or $manifest.tariffRows -ne 2 -or $manifest.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW'){throw 'EXACT_ACTIVATION_DELTA_REQUIRED'}
foreach($f in $manifest.files){if((Get-FileHash (Join-Path $directory $f.path)).Hash.ToLowerInvariant() -cne $f.sha256){throw 'ACTIVATION_FILE_CHANGED'}}
[void][IO.Directory]::CreateDirectory($evidence)
foreach($f in $manifest.files){[IO.File]::Copy((Join-Path $directory $f.path),(Join-Path $evidence $f.path),$false)}
[IO.File]::Copy((Join-Path $directory 'manifest.json'),(Join-Path $evidence 'manifest.json'),$false)
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
function Sql([string]$Name,[string]$Body){
 $file=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($file,$Body,$encoding)
 Push-Location $directory
 try{& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.log'));$code=$LASTEXITCODE}finally{Pop-Location}
 if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
 [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.log')))
}
Add-Bloco55Reservation ($ProofId+'_Q1') 'SQL_EXACT_GRANTS_SCOPES_SEEDS_QUALIFICATION'|Out-Null
Add-Bloco55Reservation ($ProofId+'_Q2') 'SQL_SYNTHETIC_REFERENCE_DQ_QUALIFICATION'|Out-Null
$catalog=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/schema-after.sql') -Raw
$before=Sql 'before' $catalog
$schema=Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/applied.json') -Raw|ConvertFrom-Json
if($before -notmatch ('SCHEMA_SHA256='+$schema.catalogSha256) -or $before -notmatch 'DATA_ROWS=6723'){throw 'EXACT_APPLIED_SCHEMA_CHECKPOINT_REQUIRED'}
$apply=Get-Content (Join-Path $directory 'apply.sql') -Raw
$body=$apply.Replace('COMMIT TRANSACTION;',("GO`n"+$catalog+"`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52852,N'ROLLBACK_REQUIRED',1; ROLLBACK TRANSACTION;"))
$body=$body.Replace('B55_EXACT_ACTIVATION_COMMITTED','B55_EXACT_ACTIVATION_QUALIFIED_ROLLBACK')
$qualified=''
try{
 $out=Sql 'qualification' $body
 $qualified=[regex]::Match($out,'SCHEMA_SHA256=([a-f0-9]{64})').Groups[1].Value
 if($qualified.Length -ne 64 -or $out -notmatch 'DATA_ROWS=6749'){throw 'EXACT_26_SEED_ROWS_REQUIRED'}
}finally{if((Sql 'rollback-check' $catalog).Trim() -cne $before.Trim()){throw 'ACTIVATION_ROLLBACK_NOT_EXACT'}}
Add-Bloco55Reservation ($ProofId+'_A1') 'SQL_EXACT_SEVEN_GRANTS_SIX_SCOPES_APPLY'|Out-Null
Add-Bloco55Reservation ($ProofId+'_A2') 'SQL_EXACT_THREE_DQ_ONE_TARIFF_APPLY'|Out-Null
Sql 'apply-commit' $apply|Out-Null
Sql 'verify-profile-new-connection' (Get-Content (Join-Path $directory 'verify-profile.sql') -Raw)|Out-Null
$out=Sql 'verify-seeds-new-connection' (Get-Content (Join-Path $directory 'verify-seeds.sql') -Raw)
$release=[regex]::Match($out,'TARIFF_RELEASE_ID=(\d+)').Groups[1].Value
$after=Sql 'after' $catalog
if(-not $release -or $after -notmatch ('SCHEMA_SHA256='+$qualified) -or $after -notmatch 'DATA_ROWS=6749'){throw 'COMMITTED_ACTIVATION_REQUIRES_RECONCILIATION'}
$receipt=[ordered]@{id=$ProofId;passed=$true;packageSha256=$ExpectedPackageSha256;catalogSha256=$qualified;dataRows=6749;referenceReleaseId=$release;
 grantDelta=7;grants=32;scopeDelta=6;scopes=16;dqPolicies=3;tariffRows=2;validUntil=$manifest.validUntil;utc=[DateTimeOffset]::UtcNow.ToString('o')}
[IO.File]::WriteAllText((Join-Path $directory 'applied.json'),($receipt|ConvertTo-Json),$encoding)
[IO.File]::WriteAllText((Join-Path $evidence 'receipt.json'),($receipt|ConvertTo-Json),$encoding)
$receipt|ConvertTo-Json -Compress

param([Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId,
 [Parameter(Mandatory)][ValidateSet('c07fe2c271d9f0a94e57b752a4bacc6d5d4b99a9581cbf254652bec939e36abb')][string]$ExpectedPackageSha256)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root 'database/proposals/bloco55-dq-correction'
$evidence=Join-Path $root ('target/bloco55/dq-correction/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
if((Test-Path $evidence) -or (Test-Path (Join-Path $directory 'applied.json'))){throw 'EXISTING_EVIDENCE_PRESERVE'}
if((Get-FileHash (Join-Path $directory 'manifest.json')).Hash.ToLowerInvariant() -cne $ExpectedPackageSha256){throw 'EXACT_AUTHORIZED_DQ_PACKAGE_REQUIRED'}
$manifest=Get-Content (Join-Path $directory 'manifest.json') -Raw|ConvertFrom-Json
foreach($f in $manifest.files){if((Get-FileHash (Join-Path $directory $f.path)).Hash.ToLowerInvariant() -cne $f.sha256){throw 'DQ_PACKAGE_CHANGED'}}
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
$catalog=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/schema-after.sql') -Raw
Add-Bloco55Reservation ($ProofId+'_Q1') 'SQL_THREE_DQ_REVISIONS_QUALIFICATION'|Out-Null
$before=Sql 'before' $catalog
if($before -notmatch 'SCHEMA_SHA256=ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae'){throw 'CURRENT_V023_CATALOG_REQUIRED'}
$rows=[long][regex]::Match($before,'DATA_ROWS=(\d+)').Groups[1].Value
$apply=Get-Content (Join-Path $directory 'apply.sql') -Raw
$rollback="GO`n"+$catalog+"`nIF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52854,N'ROLLBACK_REQUIRED',1; ROLLBACK TRANSACTION;"
try{
 $q=Sql 'qualification' ($apply.Replace('COMMIT TRANSACTION;',$rollback).Replace('REVISIONS_COMMITTED','REVISIONS_QUALIFIED_ROLLBACK'))
 if($q -notmatch ('DATA_ROWS='+($rows+15)+'\b')){throw 'EXACT_FIFTEEN_ROWS_REQUIRED'}
}finally{if((Sql 'rollback-check' $catalog).Trim() -cne $before.Trim()){throw 'DQ_ROLLBACK_NOT_EXACT'}}
Add-Bloco55Reservation ($ProofId+'_Q2') 'SQL_DQ_REVOCATION_RECOVERY_QUALIFICATION'|Out-Null
$recovery=Get-Content (Join-Path $directory 'recovery.sql') -Raw
$updates=($recovery -split 'BEGIN TRANSACTION;',2)[1] -split 'COMMIT TRANSACTION;',2
$recoveryBody=$updates[0]+"`nIF (SELECT COUNT(*) FROM ctl.data_quality_policy WHERE policy_version LIKE N'bloco55-%-backfill-v[12]' AND policy_state=N'REVOKED')<>6 THROW 52854,N'EXACT_SIX_REVOKED_REQUIRED',1;`n"+$rollback
try{Sql 'recovery-qualification' ($apply.Replace('COMMIT TRANSACTION;',$recoveryBody).Replace('REVISIONS_COMMITTED','RECOVERY_QUALIFIED_ROLLBACK'))|Out-Null}
finally{if((Sql 'recovery-rollback-check' $catalog).Trim() -cne $before.Trim()){throw 'DQ_RECOVERY_ROLLBACK_NOT_EXACT'}}
Add-Bloco55Reservation ($ProofId+'_A1') 'SQL_AUTHORIZED_THREE_DQ_REVISIONS_APPLY'|Out-Null
Sql 'apply-commit' $apply|Out-Null
Sql 'verify-new-connection' (Get-Content (Join-Path $directory 'verify.sql') -Raw)|Out-Null
Sql 'verify-profile-new-connection' (Get-Content (Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/verify-profile.sql') -Raw)|Out-Null
$after=Sql 'after' $catalog
if($after -notmatch 'SCHEMA_SHA256=ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae' -or $after -notmatch ('DATA_ROWS='+($rows+15)+'\b')){throw 'COMMITTED_DQ_REQUIRES_RECONCILIATION'}
$receipt=[ordered]@{id=$ProofId;passed=$true;authorization='OWNER_EXPLICIT_THREE_DQ_REVISIONS_20260908';packageSha256=$ExpectedPackageSha256;dataRowsBefore=$rows;dataRowsAfter=$rows+15;activePolicies=3;revokedPolicies=3;historicalPolicies=6;grants=32;scopes=16;validUntil='2026-10-07T22:34:30.615Z';utc=[DateTimeOffset]::UtcNow.ToString('o')}
[IO.File]::WriteAllText((Join-Path $directory 'applied.json'),($receipt|ConvertTo-Json),$encoding)
[IO.File]::WriteAllText((Join-Path $evidence 'receipt.json'),($receipt|ConvertTo-Json),$encoding)
$receipt|ConvertTo-Json -Compress

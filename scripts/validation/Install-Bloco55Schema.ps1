param([Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId,
 [Parameter(Mandatory)][string]$UpgradeReceipt,[Parameter(Mandatory)][string]$BaselineReceipt,
 [ValidateSet('bloco55-runtime-extension','bloco55-output-projection')][string]$Package='bloco55-runtime-extension')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$proposal=Join-Path $root ('database/proposals/'+$Package)
$evidence=Join-Path $root ('target/bloco55/schema/'+$ProofId)
$encoding=[Text.UTF8Encoding]::new($false,$true)
if(Test-Path $evidence){throw 'EXISTING_EVIDENCE_PRESERVE'}
$up=Get-Content $UpgradeReceipt -Raw|ConvertFrom-Json
$tail=Get-Content $BaselineReceipt -Raw|ConvertFrom-Json
$hash=(Get-FileHash (Join-Path $proposal 'manifest.json')).Hash.ToLowerInvariant()
if(-not $up.passed -or -not $tail.passed -or $up.route -cne 'upgrade' -or $tail.route -cne 'baseline-tail' -or
 $up.packageSha256 -cne $hash -or $tail.packageSha256 -cne $hash -or $up.qualifiedCatalog -cne $tail.qualifiedCatalog){throw 'EXACT_QUALIFIED_PAIR_REQUIRED'}
$manifest=Get-Content (Join-Path $proposal 'manifest.json') -Raw|ConvertFrom-Json
foreach($f in $manifest.files){if((Get-FileHash (Join-Path $proposal $f.path)).Hash.ToLowerInvariant() -cne $f.sha256){throw 'PACKAGE_FILE_CHANGED'}}
if((Get-FileHash (Join-Path $root ('database/migrations/'+$manifest.migration))).Hash.ToLowerInvariant() -cne $manifest.migrationSha256){throw 'MIGRATION_CHANGED'}
[void][IO.Directory]::CreateDirectory($evidence)
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
Add-Bloco55Reservation $ProofId 'SQL_ADDITIVE_SCHEMA_APPLY_VERIFY'|Out-Null
function Sql([string]$Name,[string]$Body,[string]$Database='ETL_SISTEMA_V2_SHADOW'){
 if($Database -cnotin @('master','ETL_SISTEMA_V2_SHADOW')){throw 'EXACT_DATABASE_REQUIRED'}
 $file=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($file,$Body,$encoding)
 Push-Location $proposal
 try{& sqlcmd -S localhost -d $Database -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.log'));$code=$LASTEXITCODE}finally{Pop-Location}
 if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
 [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.log')))
}
Sql 'master-target' "SET NOCOUNT ON; IF (SELECT COUNT_BIG(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE' AND is_read_only=0)<>1 THROW 52850,N'EXACT_TARGET_REQUIRED',1; SELECT N'EXACT_TARGET_CONFIRMED';" 'master'|Out-Null
$catalog=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/schema-after.sql') -Raw
$before=Sql 'before' $catalog
if($before -notmatch ('SCHEMA_SHA256='+$manifest.previousCatalog) -or $before -notmatch ('DATA_ROWS='+$manifest.previousRows)){throw 'EXACT_CHECKPOINT_REQUIRED'}
Sql 'apply' (Get-Content (Join-Path $proposal 'apply.sql') -Raw)|Out-Null
Sql 'verify-new-connection' (Get-Content (Join-Path $proposal 'verify-schema.sql') -Raw)|Out-Null
$after=Sql 'after' $catalog
if($after -notmatch ('SCHEMA_SHA256='+$up.qualifiedCatalog) -or $after -notmatch ('DATA_ROWS='+$manifest.previousRows)){throw 'COMMITTED_STATE_REQUIRES_RECONCILIATION'}
$receipt=[ordered]@{id=$ProofId;packageSha256=$hash;catalogSha256=$up.qualifiedCatalog;migrationSha256=$manifest.migrationSha256;
 dataRows=$manifest.previousRows;grantDelta=0;scopeDelta=0;passed=$true;utc=[DateTimeOffset]::UtcNow.ToString('o')}
[IO.File]::WriteAllText((Join-Path $proposal 'applied.json'),($receipt|ConvertTo-Json),$encoding)
[IO.File]::Copy((Join-Path $proposal 'manifest.json'),(Join-Path $evidence 'manifest.json'),$false)
[IO.File]::WriteAllText((Join-Path $evidence 'receipt.json'),($receipt|ConvertTo-Json),$encoding)
$receipt|ConvertTo-Json -Compress

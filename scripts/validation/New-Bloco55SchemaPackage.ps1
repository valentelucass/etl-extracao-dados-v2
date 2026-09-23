$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$proposal=Join-Path $root 'database/proposals/bloco55-runtime-extension'
$encoding=[Text.UTF8Encoding]::new($false)
if(Test-Path (Join-Path $proposal 'applied.json')){throw 'APPLIED_PACKAGE_IMMUTABLE'}
$inventory=Get-Content (Join-Path $root 'target/bloco55/initial-inventory.json') -Raw|ConvertFrom-Json
$historical=@($inventory|Where-Object path -match '^database[/\\]migrations[/\\]V0(0[1-9]|1[0-9]|2[01])__')
if($historical.Count -ne 21){throw 'HISTORICAL_MIGRATIONS_INVENTORY_REQUIRED'}
foreach($file in $historical){if((Get-FileHash (Join-Path $root $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'HISTORICAL_MIGRATION_CHANGED'}}
$files=@(Get-ChildItem $proposal -File|Where-Object Name -ne 'manifest.json'|Sort-Object Name|ForEach-Object{
 [ordered]@{path=$_.Name;sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant();bytes=$_.Length}
})
$migration=Join-Path $root 'database/migrations/V022__extend_five_vertical_runtime.sql'
$manifest=[ordered]@{phase='SCHEMA_ONLY_REQUIRES_ROLLBACK_QUALIFICATION';database='localhost/ETL_SISTEMA_V2_SHADOW';
 previousCatalog='7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408';previousRows=6723;
 previousGrants=25;previousScopes=10;previousMappings=2;previousServiceVersion=17;previousOperatorVersion=1;
 validUntil='2026-10-07T22:34:30.615Z';grantDelta=0;scopeDelta=0;seedRows=0;
 migration='V022__extend_five_vertical_runtime.sql';migrationSha256=(Get-FileHash $migration).Hash.ToLowerInvariant();
 historicalMigrations=$historical;files=$files}
[IO.File]::WriteAllText((Join-Path $proposal 'manifest.json'),($manifest|ConvertTo-Json -Depth 8),$encoding)
(Get-FileHash (Join-Path $proposal 'manifest.json')).Hash.ToLowerInvariant()

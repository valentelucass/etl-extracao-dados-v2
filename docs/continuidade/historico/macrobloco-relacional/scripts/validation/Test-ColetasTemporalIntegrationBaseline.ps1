#Requires -Version 7.5
param([switch]$InstalledReceipts)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$run=Join-Path $root 'target/coletas-temporal-integration-20260910'
$migrations=@(Get-ChildItem -LiteralPath (Join-Path $root 'database/migrations') -File|Sort-Object Name)
$baseline=Get-Content -LiteralPath (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') -Raw
$includes=@([regex]::Matches($baseline,'(?m)^:r "\.\.\\migrations\\([^"\r\n]+)"\r?$')|ForEach-Object {$_.Groups[1].Value})
if(($migrations.Name -join '|') -cne ($includes -join '|') -or $migrations.Count -ne 28){throw 'COL_BASELINE_ORDER_OR_MEMBERSHIP'}
foreach($number in 1..28){if(-not $migrations[$number-1].Name.StartsWith(('V{0:D3}__' -f $number),[StringComparison]::Ordinal)){throw 'COL_MIGRATION_SEQUENCE'}}
if($InstalledReceipts){
    $initial=Get-Content -LiteralPath (Join-Path $run 'inventory-before.json') -Raw|ConvertFrom-Json
    foreach($file in $initial|Where-Object {$_.path -cmatch '^database/migrations/V0(0[1-9]|1[0-9]|2[0-4])__'}){
        if((Get-FileHash -LiteralPath (Join-Path $root $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'COL_APPLIED_PREDECESSOR_CHANGED'}
    }
    foreach($receipt in @('schema-install-01','schema-correction-install-01')){
        $frozen=Get-Content -LiteralPath (Join-Path $run ($receipt+'/migrations.json')) -Raw|ConvertFrom-Json
        foreach($file in $frozen){if((Get-FileHash -LiteralPath (Join-Path $root $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'COL_APPLIED_MIGRATION_CHANGED'}}
        $events=@(Get-Content -LiteralPath (Join-Path $run ($receipt+'/ledger.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
        if($events.Count -ne 2 -or $events[0].state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $events[1].state -cne 'CONFIRMED' -or
            $events[1].exit -ne 0 -or -not $events[1].installed){throw 'COL_INSTALL_RECEIPT_INVALID'}
    }
}
foreach($file in $migrations|Select-Object -Skip 24){
    $sql=Get-Content -LiteralPath $file.FullName -Raw
    if($sql -match '(?im)^\s*(GRANT|DENY|CREATE\s+(DATABASE|LOGIN|USER|ROLE)|DELETE\s+FROM|TRUNCATE\s+TABLE)\b'){throw 'COL_MIGRATION_SCOPE_EXPANSION'}
}
@{passed=$true;migrations=$migrations.Count;baselineIncludesExactlyOrderedMigrations=$true;
    installedReceiptsChecked=[bool]$InstalledReceipts;physicalFreshVsUpgradeEquivalenceProven=$false;
    reason='No authorized empty database or reset; static SQLCMD inclusion equivalence and additive installation only'}|ConvertTo-Json

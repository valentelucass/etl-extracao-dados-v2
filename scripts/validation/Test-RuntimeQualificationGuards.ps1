#Requires -Version 7.5
param([string]$PairModulePath = "$PSScriptRoot/Bloco60ConcurrentPair.psm1")
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory = Join-Path $root ('target/bloco61-guards/' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$fixture = [IO.File]::ReadAllText((Join-Path $root 'src/test/resources/runtime-qualification/concurrent.synthetic.json'))
$sql = [IO.File]::ReadAllText((Join-Path $root 'database/validation/059_observe_bloco60_concurrent_waiters.sql'))
$validator = Join-Path $PSScriptRoot 'Test-RuntimeQualification.ps1'
$checks = [Collections.Generic.List[object]]::new()
function Reject([string]$Name, [string]$Reason, [scriptblock]$Work) {
    $observed = 'ACCEPTED'
    try { & $Work | Out-Null } catch {
        $observed = $_.Exception.ToString()
        [IO.File]::WriteAllText((Join-Path $directory ($Name + '.failure.log')), $observed)
    }
    if (-not $observed.Contains($Reason)) { throw ('B61_GUARD_' + $Name + '_EXPECTED_' + $Reason) }
    $checks.Add(@{name=$Name;rejected=$true;reason=$Reason})
}
& $validator | Out-Null
foreach ($mutation in @(
    @('no_samples','B61_SAMPLE_BOUND',{param($e)$e.samples=@()}),
    @('no_pair','B61_CONCURRENT_EVIDENCE_MISSING',{param($e)$e.samples=$e.samples[0..26]}),
    @('separate_samples','B61_CONCURRENT_EVIDENCE_MISSING',{param($e)
        $last=$e.samples[27];$e.samples[26].sessions=@($last.sessions[0]);$e.samples[26].simultaneousOwnWaiters=1
        $last.sessions=@($last.sessions[1]);$last.simultaneousOwnWaiters=1}),
    @('reported_count','B61_CONCURRENCY_COUNT_DIVERGENCE',{param($e)$e.samples[27].simultaneousOwnWaiters=0}),
    @('null_lock','B61_CONCURRENCY_COUNT_DIVERGENCE',{param($e)$e.samples[27].sessions[0].matchingBarrierLock=$null}),
    @('other_process','B61_UNOWNED_SESSION',{param($e)$e.samples[27].sessions[0].processId=999}),
    @('same_session','B61_DISTINCT_SESSIONS',{param($e)$e.samples[27].sessions[1].sessionId=51}),
    @('different_request','B61_REQUEST_SCOPE_DIVERGENCE',{param($e)$e.requests[1].executionId='SYNTHETIC_OTHER'}),
    @('missing_result','B61_TWO_RESULTS_REQUIRED',{param($e)$e.results=@($e.results[0])}),
    @('wrong_exit','B60_PROCESS_HTTP_OR_EXIT',{param($e)$e.results[1].exit=40}),
    @('divergent_readback','B60_REPEATED_DURABLE_EFFECT_historyHash',{param($e)$e.results[1].readback.historyHash='e'*64}),
    @('null_receipt','B61_READBACK_HASH',{param($e)$e.results[0].readback.typedReceiptHash=$null;$e.results[1].readback.typedReceiptHash=''}),
    @('missing_receipt','B61_READBACK_HASH',{param($e)$e.results[1].readback.Remove('typedReceiptHash')}),
    @('both_receipts_null','B61_READBACK_HASH',{param($e)$e.results[0].readback.typedReceiptHash=$null;$e.results[1].readback.typedReceiptHash=$null}),
    @('time_bound','B61_SAMPLE_TIME_BOUND',{param($e)$e.samples[27].observedUtc='2036-01-01T00:00:08Z'}),
    @('local_time','B61_SAMPLE_UTC_REQUIRED',{param($e)$e.samples[27].observedUtc='2036-01-01T00:00:06.750'}),
    @('recovery_unknown','B61_RECOVERY_UNCONFIRMED',{param($e)$e.recovery.unknownEffects=1}),
    @('recovery_mixed','B61_RECOVERY_UNCONFIRMED',{param($e)$e.recovery.status='MIXED_STOP'}),
    @('recovery_profile','B61_RECOVERY_UNCONFIRMED',{param($e)$e.recovery.profileRestored=$false}),
    @('recovery_rows','B60_ROW_TABLE_SET',{param($e)$e.recovery.afterRows=''}),
    @('physical_claim','B61_EVIDENCE_LAYER',{param($e)$e.physicalExecuted=$true})
)) {
    $copy = $fixture | ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
    & $mutation[2] $copy
    $path = Join-Path $directory ($mutation[0] + '.json')
    $copy | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $path -Encoding utf8NoBOM
    Reject $mutation[0] $mutation[1] { & $validator -EvidencePath $path }
}
foreach ($mutation in @(
    @('early_exit','B61_OBSERVER_EARLY_EXIT_OR_BOUND',$sql.Replace('@tries<28','@tries<2')),
    @('late_exit','B61_OBSERVER_EARLY_EXIT_OR_BOUND',$sql.Replace('@tries<28','@tries<29')),
    @('one_waiter','B61_OBSERVER_EARLY_EXIT_OR_BOUND',$sql.Replace('@found<2','@found<1')),
    @('wait_budget','B61_OBSERVER_DELAY',$sql.Replace('00:00:00.250','00:00:01.000')),
    @('null_scalar_equality','B61_OBSERVER_NULL_EQUALITY',([regex]::Replace($sql,
        '(?s)ON EXISTS\(\s*SELECT b.resource_type,.*?w.resource_lock_partition\)',
        'ON b.resource_type=w.resource_type AND b.resource_subtype=w.resource_subtype AND b.resource_database_id=w.resource_database_id AND b.resource_description=w.resource_description AND b.resource_associated_entity_id=w.resource_associated_entity_id AND b.resource_lock_partition=w.resource_lock_partition'))),
    @('wrong_set_operator','B61_OBSERVER_NULL_SET_OPERATOR',$sql.Replace('INTERSECT','UNION'))
)) {
    $path = Join-Path $directory ($mutation[0] + '.sql')
    [IO.File]::WriteAllText($path, $mutation[2])
    Reject $mutation[0] $mutation[1] { & $validator -ObserverSqlPath $path }
}

# Exercise the shared assertion itself: NULL is not an empty string or an absent field.
Import-Module "$PSScriptRoot/Bloco60Assertions.psm1" -Force
$case=@{id='SYNTHETIC_NULL';expectedExit=@(0);expectedHttp=@(0);expected=@{nullable=$null};fault='NONE'}
$observed=@{nullable=$null;publications=0;seals=0;applicationRows=0;typedHistory=0}
Assert-Bloco60Case $case $observed 0 0 '' $false
$observed.nullable=''
Reject 'assert_null_empty' 'B60_SQL_ASSERTION_SYNTHETIC_NULL_nullable' { Assert-Bloco60Case $case $observed 0 0 '' $false }
$observed.Remove('nullable')
Reject 'assert_null_absent' 'B60_SQL_ASSERTION_SYNTHETIC_NULL_nullable' { Assert-Bloco60Case $case $observed 0 0 '' $false }

# Exercise the actual pair controller, including a failing cleanup callback.
Import-Module $PairModulePath -Force
foreach ($mode in @('OBSERVE','STOP','BARRIER','FIRST','SECOND','LAUNCH_SECOND')) {
    $script:events = [Collections.Generic.List[string]]::new()
    $arguments = @{
        StartBarrier={@{ProcessId=103}}
        ReadBarrierReady={param($barrier)@{barrierPid=103;grantedApplicationLocks=1;sessions=1}}
        StartFirst={'A'}
        StartSecond={if($mode -ceq 'LAUNCH_SECOND'){throw 'LAUNCH_SECOND_FAILED'};'B'}
        ObservePair={param($barrier,$first,$second)if($mode -cin @('OBSERVE','STOP')){throw 'OBSERVE_FAILED'}}
        StopOwned={$script:events.Add('STOP');if($mode -ceq 'STOP'){throw 'STOP_FAILED'}}
        FinishBarrier={param($barrier)$script:events.Add('BARRIER');if($mode -ceq 'BARRIER'){throw 'BARRIER_FAILED'}}
        FinishChild={param($child)$script:events.Add($child);if($mode -ceq 'FIRST' -and $child -ceq 'A'){throw 'FIRST_FAILED'};if($mode -ceq 'SECOND' -and $child -ceq 'B'){throw 'SECOND_FAILED'}}
    }
    Reject ('controller_' + $mode) ($mode + '_FAILED') { Invoke-Bloco60ConcurrentPair @arguments }
    $expected = if ($mode -ceq 'LAUNCH_SECOND') {'BARRIER,A'} else {'BARRIER,A,B'}
    if (-not (($script:events -join ',').EndsWith($expected, [StringComparison]::Ordinal))) {
        throw ('B61_RECOVERY_LOST_READBACK_' + $mode)
    }
}
$result = @{passed=$true;guards=$checks.Count;checks=@($checks);layer='SYNTHETIC_OFFLINE';
    sqlExecuted=$false;physicalObserverQualified=$false;evidence=$directory}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'result.json') -Encoding utf8NoBOM
$result | ConvertTo-Json -Depth 6

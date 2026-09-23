#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/Bloco60Assertions.psm1"

function Assert-RuntimeQualificationEvidence($Evidence) {
    if ($Evidence.version -ne 1 -or $Evidence.layer -cne 'SYNTHETIC_OFFLINE' -or
        $Evidence.physicalExecuted -ne $false) { throw 'B61_EVIDENCE_LAYER' }
    $requests = @($Evidence.requests)
    if ($requests.Count -ne 2 -or $requests[0].invocationId -ceq $requests[1].invocationId) {
        throw 'B61_DISTINCT_INVOCATIONS'
    }
    $keys = @('executionId','cycleId','mode','start','endExclusive','replayOf','idempotencyKey',
        'leaseSeconds','pageSize','maximumPages','qualityVersion','qualityFingerprint',
        'compatibilityVersion','protocol','operation','maximumNodes')
    foreach ($request in $requests) {
        if (($request.Keys | Sort-Object) -join '|' -cne
            ((@($keys) + 'invocationId' | Sort-Object) -join '|')) { throw 'B61_REQUEST_SHAPE' }
        foreach ($key in $keys) {
            if ($null -eq $request[$key] -or [string]$request[$key] -cne [string]$requests[0][$key]) {
                throw 'B61_REQUEST_SCOPE_DIVERGENCE'
            }
        }
    }
    $pids = @($Evidence.processes)
    if ($pids.Count -ne 2 -or @($pids | Sort-Object -Unique).Count -ne 2 -or
        @($pids | Where-Object { $_ -isnot [long] -and $_ -isnot [int] -or $_ -le 0 }).Count) {
        throw 'B61_DISTINCT_PROCESSES'
    }
    $samples = @($Evidence.samples)
    if ($samples.Count -lt 1 -or $samples.Count -gt 28) { throw 'B61_SAMPLE_BOUND' }
    $found = $false; $previous = $null; $firstInstant = $null; $ordinal = 0
    foreach ($sample in $samples) {
        $ordinal++
        if ($sample.sample -ne $ordinal) { throw 'B61_SAMPLE_SEQUENCE' }
        if ($sample.observedUtc -cnotmatch '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,7})?Z$') {
            throw 'B61_SAMPLE_UTC_REQUIRED'
        }
        $instant = [DateTimeOffset]::Parse($sample.observedUtc, [Globalization.CultureInfo]::InvariantCulture)
        if ($null -eq $firstInstant) { $firstInstant = $instant }
        if (($instant - $firstInstant).TotalMilliseconds -gt 7000) { throw 'B61_SAMPLE_TIME_BOUND' }
        if ($null -ne $previous -and $instant -lt $previous) { throw 'B61_SAMPLE_TIME_ORDER' }
        $previous = $instant
        $sessions = @($sample.sessions)
        if ($sessions.Count -gt 2 -or @($sessions | Where-Object { $_.processId -notin $pids }).Count) {
            throw 'B61_UNOWNED_SESSION'
        }
        $waiting = @($sessions | Where-Object {
            $_.matchingBarrierLock -ceq 1 -and $_.waitType -clike 'LCK_M_*'
        })
        $count = @($waiting | ForEach-Object processId | Sort-Object -Unique).Count
        if ($sample.simultaneousOwnWaiters -ne $count) { throw 'B61_CONCURRENCY_COUNT_DIVERGENCE' }
        if ($count -eq 2) {
            if (@($waiting.sessionId | Sort-Object -Unique).Count -ne 2) { throw 'B61_DISTINCT_SESSIONS' }
            $found = $true
        }
    }
    if (-not $found) { throw 'B61_CONCURRENT_EVIDENCE_MISSING' }
    $results = @($Evidence.results)
    if ($results.Count -ne 2) { throw 'B61_TWO_RESULTS_REQUIRED' }
    for ($i = 0; $i -lt 2; $i++) {
        $result = $results[$i]
        if ($result.processId -ne $pids[$i]) { throw 'B61_RESULT_PROCESS_DIVERGENCE' }
        foreach ($key in @('typedReceiptHash','historyHash','currentHash')) {
            if (-not $result.readback.Contains($key) -or $result.readback[$key] -isnot [string] -or
                $result.readback[$key] -cnotmatch '^[a-f0-9]{64}$') { throw 'B61_READBACK_HASH' }
        }
        $case = @{id='SYNTHETIC_PAIR'; expectedExit=@(0); expectedHttp=@(0);
            expected=@{publications=1;seals=1;applicationRows=2;typedHistory=2};fault='NONE'}
        Assert-Bloco60Case $case $result.readback $result.exit 0 '' $false
    }
    Assert-Bloco60Unchanged $results[0].readback $results[1].readback
    $recovery = $Evidence.recovery
    if ($recovery.status -cne 'COMPENSATED' -or $recovery.unknownEffects -ne 0 -or
        -not $recovery.profileRestored) { throw 'B61_RECOVERY_UNCONFIRMED' }
    Assert-Bloco60PreservedRows $recovery.beforeRows $recovery.afterRows
}
Export-ModuleMember Assert-RuntimeQualificationEvidence

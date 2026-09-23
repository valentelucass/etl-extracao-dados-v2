#Requires -Version 7.5
<#
.SYNOPSIS
Bounded, single-use B56 metadata investigation. Authorization is recorded separately.
No ETL, database, domain payload persistence, retries or credential changes.
#>
param([switch]$SelfTest, [switch]$FunctionsOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Read-EnvValue([string[]]$Lines, [string]$Name) {
    $value = $null
    foreach ($line in $Lines) {
        if ($line.TrimStart().StartsWith('#')) { continue }
        $split = $line.IndexOf('=')
        if ($split -lt 1 -or $line.Substring(0, $split).Trim() -cne $Name) { continue }
        $candidate = $line.Substring($split + 1).Trim()
        if ($candidate.Length -ge 2 -and (($candidate.StartsWith('"') -and $candidate.EndsWith('"')) -or ($candidate.StartsWith("'") -and $candidate.EndsWith("'")))) {
            $candidate = $candidate.Substring(1, $candidate.Length - 2).Trim()
        }
        if ($candidate.Length) { $value = $candidate }
    }
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '[\x00-\x1f\x7f]') { throw 'B56_ENV_INVALID' }
    return $value
}

function Get-SafeBase([string]$Value) {
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -cne 'https' -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'B56_BASE_INVALID' }
    return $uri
}

function Quote-CurlConfig([string]$Value) {
    if ($Value -match '[\x00-\x1f\x7f]') { throw 'B56_CONFIG_CONTROL' }
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

function Get-TechnicalName($Value) {
    if ($Value -is [string] -and $Value.Length -le 160 -and $Value -cmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$') { return $Value }
    return $null
}

function Get-DeclaredType($Value) {
    if ($Value -is [string] -and $Value.ToLowerInvariant() -cin @('integer','string','text','decimal','float','number','numeric','boolean','bool','date','datetime','timestamp','array','object','json','jsonb','bigint','time')) { return $Value.ToLowerInvariant() }
    return 'UNDECLARED_OR_UNRECOGNIZED'
}

function Get-MetadataProfile($Json) {
    if ($Json -isnot [Collections.IDictionary] -or $Json.Contains('errors') -or $Json.Contains('error')) { throw 'B56_INFO_ENVELOPE' }
    $doc = if ($Json.Contains('data')) { $Json.data } else { $Json }
    if ($doc -isnot [Collections.IDictionary] -or -not $doc.Contains('fields')) { throw 'B56_INFO_FIELDS' }
    $result = [ordered]@{}
    foreach ($collection in @('fields', 'filters')) {
        $profiles = [Collections.Generic.List[object]]::new()
        $omitted = 0
        $items = $doc[$collection]
        if ($null -eq $items -and $collection -eq 'filters') { $items = @() }
        if ($items -is [Collections.IDictionary]) {
            $entries = @(foreach ($key in $items.Keys) { [pscustomobject]@{name=$key; value=$items[$key]} })
        } elseif ($items -is [Collections.IList]) {
            $entries = @(foreach ($item in $items) { [pscustomobject]@{name=$null; value=$item} })
        } else { throw 'B56_INFO_COLLECTION' }
        if ($entries.Count -gt 4096) { throw 'B56_INFO_FIELD_BOUND' }
        foreach ($entry in $entries) {
            $name = $entry.name
            $type = 'UNDECLARED_OR_UNRECOGNIZED'
            if ($entry.value -is [Collections.IDictionary]) {
                foreach ($key in @('name','field','key','technical_name','technicalName')) {
                    if ($entry.value.Contains($key)) { $name = $entry.value[$key]; break }
                }
                foreach ($key in @('type','data_type','dataType')) {
                    if ($entry.value.Contains($key)) { $type = Get-DeclaredType $entry.value[$key]; break }
                }
            } elseif ($entry.value -is [string]) {
                if ($null -eq $name) { $name = $entry.value } else { $type = Get-DeclaredType $entry.value }
            }
            $safe = Get-TechnicalName $name
            if ($null -eq $safe) { $omitted++; continue }
            $profiles.Add([ordered]@{name=$safe;declaredType=$type})
        }
        $result[$collection] = @($profiles.ToArray())
        $result[$collection + 'Count'] = $entries.Count
        $result[$collection + 'NamesOmitted'] = $omitted
    }
    return $result
}

function Invoke-MemoryGet([Uri]$Uri, [string]$Token, [string]$SchemaQuery) {
    if ($SchemaQuery -and ($SchemaQuery -cnotmatch '^query B56Schema[A-Za-z]*\s*\{' -or $SchemaQuery -match '\b(mutation|subscription)\b' -or $SchemaQuery -notmatch '__schema|__type')) { throw 'B56_SCHEMA_QUERY_ONLY' }
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command curl.exe -CommandType Application).Source
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    # URL and authorization go through stdin, never process arguments or disk.
    $method = if ($SchemaQuery) { 'POST' } else { 'GET' }
    foreach ($arg in @('--disable','--config','-','--globoff','--silent','--request',$method,'--proto','=https','--max-redirs','0','--connect-timeout','10','--max-time','30','--max-filesize','10485760','--output','-','--write-out',"`nB56_HTTP_%{http_code}")) { $start.ArgumentList.Add($arg) }
    $config = 'url = ' + (Quote-CurlConfig $Uri.AbsoluteUri) + "`nheader = " + (Quote-CurlConfig ('Authorization: Bearer ' + $Token)) + "`nheader = " + (Quote-CurlConfig 'Accept: application/json') + "`n"
    if ($SchemaQuery) {
        $body = @{query=$SchemaQuery} | ConvertTo-Json -Compress
        $config += 'header = ' + (Quote-CurlConfig 'Content-Type: application/json') + "`ndata = " + (Quote-CurlConfig $body) + "`n"
    }
    $process = $null
    $memory = [IO.MemoryStream]::new()
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $process = [Diagnostics.Process]::Start($start)
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($config)
        $process.StandardInput.Close()
        $buffer = [byte[]]::new(8192)
        while ($true) {
            $read = $process.StandardOutput.BaseStream.ReadAsync($buffer, 0, $buffer.Length)
            while (-not $read.Wait(100)) { if ($watch.Elapsed.TotalSeconds -gt 35) { throw 'B56_WALL_TIMEOUT' } }
            $count = $read.GetAwaiter().GetResult()
            if ($count -eq 0) { break }
            if ($memory.Length + $count -gt 10MB + 128) { throw 'B56_RESPONSE_BOUND' }
            $memory.Write($buffer, 0, $count)
        }
        if (-not $process.WaitForExit(1000)) { throw 'B56_PROCESS_TIMEOUT' }
        $raw = $utf8.GetString($memory.ToArray())
        $match = [regex]::Match($raw, '\nB56_HTTP_(\d{3})$')
        $status = if ($match.Success) { [int]$match.Groups[1].Value } else { 0 }
        $summary = [ordered]@{httpStatus=$status;curlExit=$process.ExitCode;bytes=$memory.Length;elapsedMilliseconds=$watch.ElapsedMilliseconds;json=$null;failure=$null}
        if ($process.ExitCode -ne 0) { $summary.failure = 'TRANSPORT_FAILURE' }
        elseif ($status -lt 200 -or $status -gt 299) { $summary.failure = 'HTTP_NON_2XX' }
        else {
            try { $summary.json = $raw.Substring(0, $match.Index) | ConvertFrom-Json -AsHashtable -Depth 64 -DateKind String }
            catch { $summary.failure = 'INVALID_JSON' }
        }
        return $summary
    } finally {
        if ($null -ne $process) {
            if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
            $process.Dispose()
        }
        $memory.Dispose()
        $config = $null; $raw = $null; $Token = $null
    }
}

if ($FunctionsOnly) { return }
if ($SelfTest) {
    if ((Read-EnvValue @('K=first', 'K="last"', 'K=', '#K=no') 'K') -cne 'last') { throw 'TEST_ENV_LAST' }
    foreach ($bad in @('http://example.invalid','https://user@example.invalid','https://example.invalid/?token=bad','https://example.invalid/#fragment')) {
        $rejected=$false; try { [void](Get-SafeBase $bad) } catch { $rejected=$true }; if (-not $rejected) { throw 'TEST_BASE_REJECTION' }
    }
    $profile = Get-MetadataProfile ('{"fields":{"id":"integer","invoice_mapping":{"type":"object"},"unsafe label":"SECRET_SENTINEL"},"filters":[{"name":"accounting_debits.created_at","type":"date"}]}' | ConvertFrom-Json -AsHashtable)
    $serialized = $profile | ConvertTo-Json -Depth 10
    if ($profile.fieldsCount -ne 3 -or $profile.fieldsNamesOmitted -ne 1 -or $serialized.Contains('SECRET_SENTINEL') -or $profile.fields[0].declaredType -cne 'integer') { throw 'TEST_SANITIZATION' }
    $failed=$false; try { [void](Get-MetadataProfile (@{errors=@('SECRET_SENTINEL')})) } catch { $failed=$true }; if (-not $failed) { throw 'TEST_ERROR_ENVELOPE' }
    if ((Quote-CurlConfig 'a\b"c') -cne '"a\\b\"c"') { throw 'TEST_CONFIG_ESCAPE' }
    'B56_METADATA_OFFLINE_TESTS_PASS_NO_NETWORK'
    exit 0
}

$private = Join-Path $root 'target/bloco56-continuacao'
$ledger = Join-Path $private 'info-ledger.jsonl'
$request = Join-Path $private 'request-info.json'
if (Test-Path -LiteralPath $ledger) { throw 'B56_SINGLE_USE_ALREADY_RESERVED_NO_REPEAT' }
if (-not (Test-Path -LiteralPath $request)) { throw 'B56_REQUEST_MISSING' }
$plan = Get-Content -LiteralPath $request -Raw | ConvertFrom-Json
if ($plan.maximumCalls -ne 4 -or ($plan.templates -join ',') -cne '8636,4924,10633,6392' -or $plan.operation -cne 'GET /api/analytics/reports/{template}/info') { throw 'B56_REQUEST_SCOPE' }
$envLines = [IO.File]::ReadAllLines((Join-Path $root '../etl-extracao-dados/.env'))
$base = Get-SafeBase (Read-EnvValue $envLines 'API_BASE_URL')
$token = Read-EnvValue $envLines 'API_DATAEXPORT_TOKEN'
$requestHash = (Get-FileHash -LiteralPath $request).Hash.ToLowerInvariant()
$results = [Collections.Generic.List[object]]::new()
foreach ($template in @(8636,4924,10633,6392)) {
    $reservation = [ordered]@{phase=$plan.phase;template=$template;operation='INFO_GET';utc=[DateTimeOffset]::UtcNow.ToString('o');state='RESERVED_OUTCOME_UNKNOWN';requestSha256=$requestHash}
    [IO.File]::AppendAllText($ledger, ($reservation | ConvertTo-Json -Compress) + "`n", $utf8)
    $result = [ordered]@{template=$template;httpStatus=0;curlExit=$null;bytes=0;elapsedMilliseconds=0;failure=$null;metadata=$null}
    try {
        $builder = [UriBuilder]::new($base)
        $builder.Path = $builder.Path.TrimEnd('/') + '/api/analytics/reports/' + $template + '/info'
        $response = Invoke-MemoryGet $builder.Uri $token
        foreach ($key in @('httpStatus','curlExit','bytes','elapsedMilliseconds','failure')) { $result[$key] = $response[$key] }
        if ($null -eq $result.failure) { $result.metadata = Get-MetadataProfile $response.json }
    } catch { $result.failure = 'LOCAL_TRANSPORT_OR_SCHEMA_FAILURE' }
    $response = $null
    $results.Add($result)
    [IO.File]::WriteAllText((Join-Path $private ('info-' + $template + '.json')), ($result | ConvertTo-Json -Depth 15), $utf8)
    [IO.File]::AppendAllText($ledger, ([ordered]@{template=$template;state='OBSERVED';httpStatus=$result.httpStatus;failure=$result.failure;utc=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json -Compress) + "`n", $utf8)
    if ($null -ne $result.failure) { break }
    Start-Sleep -Seconds 2
}
$token=$null; $envLines=$null
$failed = @($results | Where-Object { $null -ne $_.failure }).Count -gt 0
[ordered]@{phase=$plan.phase;calls=$results.Count;stoppedOnFailure=$failed;results=@($results.ToArray())} | ConvertTo-Json -Depth 15
if ($failed) { exit 2 }

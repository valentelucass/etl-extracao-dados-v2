#requires -Version 7.5
<#
.SYNOPSIS
Runs one bounded read-only Data Export profile for the nine documented ESL templates.

.DESCRIPTION
Uses curl.exe only, keeps responses in memory and emits a sanitized summary.
It never writes URLs, headers, tokens, payloads, IDs or business values.
#>
[CmdletBinding(DefaultParameterSetName = 'Probe')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Probe')]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$ClosedDate,

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateSet(6908, 6389, 6399, 6906, 8656, 8636, 4924, 10633, 6392)]
    [int[]]$Templates = @(6908, 6389, 6399, 6906, 8656, 8636, 4924, 10633, 6392),

    [Parameter(Mandatory, ParameterSetName = 'SelfTest')]
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DataExportProbeGate.ps1')

$PageSize = 3
$FirstDataPage = 1
$MaximumDataPages = 1
$MaxResponseBytes = 10MB
$TimeoutSeconds = 30
$InterCallDelaySeconds = 3
$script:stage = 'INITIALIZATION'
$script:state = $null
$MaxCalls = $Templates.Count * 2

trap {
    if ($SelfTest) { [Console]::Error.WriteLine($_.Exception.Message) }
    [ordered]@{
        transport = 'GET_WITH_QUERY'
        calls_attempted = if ($null -ne $script:state) { $script:state.calls_attempted } else { 0 }
        processing_stage = $script:stage
        stopped = $true
        stop_reason = 'INTERNAL_PROCESSING_FAILURE'
    } | ConvertTo-Json -Compress
    exit 1
}

function Get-LegacyEnvPath {
    $root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
    $local = Join-Path $root '.env'
    if (Test-Path -LiteralPath $local -PathType Leaf) { return (Resolve-Path -LiteralPath $local).Path }
    $path = Join-Path (Split-Path -Parent $root) 'etl-extracao-dados\.env'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'LOCAL_ENV_MISSING' }
    return (Resolve-Path -LiteralPath $path).Path
}

function Get-LastNonEmptyEnvValue {
    param([string]$Path, [string]$Name)
    $value = $null
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match ('^\s*' + [regex]::Escape($Name) + '\s*=\s*(.*)$')) {
            $candidate = $Matches[1].Trim()
            if ($candidate.Length -ge 2 -and (($candidate.StartsWith('"') -and $candidate.EndsWith('"')) -or ($candidate.StartsWith("'") -and $candidate.EndsWith("'")))) {
                $candidate = $candidate.Substring(1, $candidate.Length - 2).Trim()
            }
            if (-not [string]::IsNullOrWhiteSpace($candidate)) { $value = $candidate }
        }
    }
    if ([string]::IsNullOrWhiteSpace($value) -or $value -match '[\x00-\x1f\x7f]') { throw 'LOCAL_ENV_VALUE_MISSING' }
    return $value
}

function ConvertTo-SafeBaseUri {
    param([string]$Value)
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -cne 'https' -or
        [string]::IsNullOrWhiteSpace($uri.Host) -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
        throw 'LOCAL_BASE_URI_INVALID'
    }
    return $uri
}

function ConvertTo-QueryString {
    param([System.Collections.IDictionary]$Values)
    return (($Values.Keys | ForEach-Object {
        [Uri]::EscapeDataString([string]$_) + '=' + [Uri]::EscapeDataString([string]$Values[$_])
    }) -join '&')
}

function New-ProbeUri {
    param([Uri]$BaseUri, [int]$TemplateId, [ValidateSet('INFO', 'DATA')][string]$Operation, [System.Collections.IDictionary]$Query)
    $suffix = if ($Operation -eq 'INFO') { '/api/analytics/reports/' + $TemplateId + '/info' } else { '/api/analytics/reports/' + $TemplateId + '/data?' + (ConvertTo-QueryString $Query) }
    return [Uri]::new($BaseUri, $suffix)
}

function Reserve-Call {
    if ($script:state.stopped) { return $false }
    if ($script:state.calls_attempted -ge $MaxCalls) { $script:state.stopped = $true; $script:state.stop_reason = 'CALL_BUDGET_REACHED'; return $false }
    if ($script:state.calls_attempted -gt 0) { Start-Sleep -Seconds $InterCallDelaySeconds }
    $script:state.calls_attempted++
    return $true
}

function Get-HttpFailureReason {
    param([int]$Status)
    if ($Status -eq 429) { return 'HTTP_429' }
    if ($Status -lt 200 -or $Status -gt 299) { return 'HTTP_NON_2XX' }
    return $null
}

function Quote-CurlConfig { param([string]$Value) return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"' }

function Invoke-CurlJsonInMemory {
    param([Uri]$Uri, [string]$Token)
    if (-not (Reserve-Call)) { return [pscustomobject]@{ http_status = $null; json = $null; failure = $script:state.stop_reason } }
    $marker = '__V2_NINE_HTTP_' + [guid]::NewGuid().ToString('N') + '__'
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'curl.exe'; $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    foreach ($argument in @('--disable','--config','-','--globoff','--silent','--show-error','--request','GET','--proto','=https','--max-redirs','0','--connect-timeout','10','--max-time',[string]$TimeoutSeconds,'--max-filesize',[string]$MaxResponseBytes,'--output','-','--write-out',($marker + '%{http_code}' + $marker))) { [void]$start.ArgumentList.Add($argument) }
    $config = 'url = ' + (Quote-CurlConfig $Uri.AbsoluteUri) + "`nheader = " + (Quote-CurlConfig ('Authorization: Bearer ' + $Token)) + "`nheader = " + (Quote-CurlConfig 'Accept: application/json') + "`n"
    $process = $null; $memory = [IO.MemoryStream]::new(); $transportFailure = $false; $tooLarge = $false
    try {
        $process = [Diagnostics.Process]::Start($start)
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($config); $process.StandardInput.Close()
        $buffer = [byte[]]::new(8192)
        while ($true) {
            $read = $process.StandardOutput.BaseStream.Read($buffer, 0, $buffer.Length)
            if ($read -le 0) { break }
            if ($memory.Length + $read -gt ($MaxResponseBytes + 512)) { $tooLarge = $true; $process.Kill($true); break }
            $memory.Write($buffer, 0, $read)
        }
        $process.WaitForExit(); $null = $stderr.GetAwaiter().GetResult()
        $raw = [Text.Encoding]::UTF8.GetString($memory.ToArray())
        $match = [regex]::Match($raw, [regex]::Escape($marker) + '(\d{3})' + [regex]::Escape($marker) + '$')
        if (-not $match.Success -or $process.ExitCode -ne 0) { $transportFailure = $true }
    } catch { $transportFailure = $true } finally {
        if ($null -ne $process) { if (-not $process.HasExited) { try { $process.Kill($true) } catch {} }; $process.Dispose() }
        $memory.Dispose(); $config = $null; $Token = $null
    }
    if ($tooLarge) { $script:state.stopped = $true; $script:state.stop_reason = 'RESPONSE_LIMIT_EXCEEDED'; return [pscustomobject]@{ http_status = $null; json = $null; failure = 'RESPONSE_LIMIT_EXCEEDED' } }
    if ($transportFailure) { $script:state.stopped = $true; $script:state.stop_reason = 'TRANSPORT_FAILURE'; return [pscustomobject]@{ http_status = $null; json = $null; failure = 'TRANSPORT_FAILURE' } }
    $status = [int]$match.Groups[1].Value; $script:state.last_http_status = $status
    $httpFailure = Get-HttpFailureReason $status
    if ($null -ne $httpFailure) { $script:state.stopped = $true; $script:state.stop_reason = $httpFailure; return [pscustomobject]@{ http_status = $status; json = $null; failure = $httpFailure } }
    $body = $raw.Substring(0, $match.Index); $json = $null
    try { $json = ConvertFrom-Json -InputObject $body -AsHashtable -Depth 64 -NoEnumerate -DateKind String } catch { $script:state.stopped = $true; $script:state.stop_reason = 'INVALID_JSON'; return [pscustomobject]@{ http_status = $status; json = $null; failure = 'INVALID_JSON' } }
    return [pscustomobject]@{ http_status = $status; json = $json; failure = $null }
}

function Get-JsonType {
    param($Value)
    if ($null -eq $Value) { return 'NULL' }
    if ($Value -is [System.Collections.IDictionary]) { return 'OBJECT' }
    if ($Value -is [System.Collections.IList] -and $Value -isnot [string]) { return 'ARRAY' }
    if ($Value -is [bool]) { return 'BOOLEAN' }
    if ($Value -is [string]) { return 'STRING' }
    if ($Value -is [ValueType]) { return 'NUMBER' }
    return 'OTHER'
}

function Test-IntegralJsonNumber {
    param($Value)
    return $Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or
        $Value -is [uint16] -or $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64] -or $Value -is [uint64]
}

function ConvertTo-SafeTechnicalName {
    param($Value)
    if ($Value -isnot [string]) { return $null }
    $name = $Value.Trim()
    if ($name.Length -eq 0 -or $name.Length -gt 128 -or $name -cnotmatch '^[A-Za-z_][A-Za-z0-9_.-]*$') { return $null }
    return $name
}

function Get-MetadataEntries {
    param($Value)
    $entries = [Collections.Generic.List[object]]::new()
    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in $Value.Keys) { $entries.Add([pscustomobject]@{ fallback = [string]$key; value = $Value[$key] }) }
    } elseif ($Value -is [System.Collections.IList] -and $Value -isnot [string]) {
        foreach ($item in $Value) { $entries.Add([pscustomobject]@{ fallback = $null; value = $item }) }
    } else { throw 'METADATA_COLLECTION_INVALID' }
    return ,$entries
}

function Get-MetadataEntryName {
    param($Entry)
    $value = $Entry.value
    if ($value -is [System.Collections.IDictionary]) {
        foreach ($key in @('name','field','key','technical_name','technicalName')) {
            if ($value.Contains($key) -and $value[$key] -is [string]) { return (ConvertTo-SafeTechnicalName $value[$key]) }
        }
    }
    return (ConvertTo-SafeTechnicalName $Entry.fallback)
}

function Get-MetadataEntryType {
    param($Entry)
    $value = $Entry.value
    $declared = $null
    if ($value -is [string] -and $null -ne $Entry.fallback) { $declared = $value }
    if ($value -is [System.Collections.IDictionary]) {
        foreach ($key in @('type','data_type','dataType')) {
            if ($value.Contains($key) -and $value[$key] -is [string]) { $declared = $value[$key]; break }
        }
    }
    if ($null -eq $declared) { return $null }
    $type = $declared.Trim().ToLowerInvariant()
    if ($type.Length -eq 0 -or $type.Length -gt 64 -or $type -cnotmatch '^[a-z][a-z0-9_.-]*$') { return $null }
    return $type
}

function Get-MetadataSummary {
    param($Json)
    if ($Json -is [System.Collections.IDictionary] -and ($Json.Contains('error') -or $Json.Contains('errors'))) { throw 'METADATA_ERROR_ENVELOPE' }
    $node = $Json
    if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) { $node = $Json['data'] }
    if ($node -isnot [System.Collections.IDictionary]) { throw 'METADATA_ENVELOPE_INVALID' }
    if (-not $node.Contains('fields')) { throw 'METADATA_FIELDS_MISSING' }
    $result = [ordered]@{ schema_fingerprint = $null; schema_fingerprint_eligible = $true }
    $fingerprintParts = [Collections.Generic.List[string]]::new()
    foreach ($kind in @('fields','filters')) {
        $value = $null
        if ($node.Contains($kind)) { $value = $node[$kind] }
        $result[$kind + '_shape'] = Get-JsonType $value
        if ($null -eq $value -and $kind -eq 'filters') { $value = @() }
        $entries = Get-MetadataEntries $value
        $result[$kind + '_count'] = $entries.Count
        $result[$kind + '_unnamed'] = 0
        $result[$kind + '_duplicate_names'] = 0
        $profiles = [Collections.Generic.List[object]]::new()
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($entry in $entries) {
            $name = Get-MetadataEntryName $entry
            if ($null -eq $name) { $result[$kind + '_unnamed']++; $result.schema_fingerprint_eligible = $false; continue }
            if (-not $seen.Add($name)) { $result[$kind + '_duplicate_names']++; $result.schema_fingerprint_eligible = $false; continue }
            $declaredType = Get-MetadataEntryType $entry
            $profiles.Add([ordered]@{ name = $name; declared_type = $declaredType })
            $fingerprintParts.Add($kind + ':' + $name + ':' + $(if ($null -eq $declaredType) { '?' } else { $declaredType }))
        }
        $result[$kind + '_profiles'] = @($profiles | Sort-Object -Property name)
    }
    if ($result.schema_fingerprint_eligible) {
        $canonical = ($fingerprintParts | Sort-Object -CaseSensitive) -join "`n"
        $digest = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($canonical))
        $result.schema_fingerprint = [Convert]::ToHexString($digest).ToLowerInvariant()
    }
    return $result
}

function Get-DataSummary {
    param(
        $Json,
        [string[]]$Candidates,
        [switch]$AllowPhysicalExpansion,
        [switch]$RequireScalarIdentity,
        [string]$ExpansionIdentityField
    )
    if ($Json -is [System.Collections.IDictionary] -and ($Json.Contains('error') -or $Json.Contains('errors'))) { throw 'DATA_ERROR_ENVELOPE' }
    $node = $Json
    if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) { $node = $Json['data'] }
    $data = [System.Collections.Generic.List[object]]::new()
    if ($node -is [System.Collections.IList] -and $node -isnot [string]) {
        foreach ($row in $node) { $data.Add($row) }
    } elseif ($node -is [System.Collections.IDictionary]) {
        if ($node.Count -gt 0) { $data.Add($node) }
    } else { throw ('DATA_ENVELOPE_INVALID_' + (Get-JsonType $node)) }
    $summary = [ordered]@{ response_shape = (Get-JsonType $node); physical_rows = $data.Count; page_is_empty = ($data.Count -eq 0); physical_limit_verified = ($data.Count -le $PageSize); entity_bound_verified = ($data.Count -le $PageSize); physical_expansion_accepted = $false; entity_count = $null; observed_field_names = @(); unsafe_field_name_count = 0; candidate_fields = [ordered]@{} }
    $fieldNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($row in $data) {
        if ($row -isnot [System.Collections.IDictionary]) { throw 'DATA_ROW_NOT_OBJECT' }
        foreach ($key in $row.Keys) {
            $safeName = ConvertTo-SafeTechnicalName ([string]$key)
            if ($null -eq $safeName) { $summary.unsafe_field_name_count++ } else { [void]$fieldNames.Add($safeName) }
        }
    }
    $summary.observed_field_names = @($fieldNames | Sort-Object -CaseSensitive)
    foreach ($candidate in $Candidates) {
        $present = 0; $nulls = 0; $scalars = 0; $integers = 0; $types = [ordered]@{}; $distinct = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($row in $data) {
            if ($row -isnot [System.Collections.IDictionary]) { throw 'DATA_ROW_NOT_OBJECT' }
            if (-not $row.Contains($candidate)) { continue }
            $present++; $value = $row[$candidate]; $type = Get-JsonType $value
            if (-not $types.Contains($type)) { $types[$type] = 0 }; $types[$type]++
            if ($null -eq $value) { $nulls++; continue }
            if (($type -eq 'STRING' -and -not [string]::IsNullOrWhiteSpace($value)) -or ($type -eq 'NUMBER' -and (Test-IntegralJsonNumber $value))) { $scalars++; [void]$distinct.Add($type + ':' + [string]$value) }
            if ($type -eq 'NUMBER' -and (Test-IntegralJsonNumber $value)) { $integers++ }
        }
        $summary.candidate_fields[$candidate] = [ordered]@{ present = $present; nulls = $nulls; scalar_values = $scalars; integer_values = $integers; distinct_scalar_values = $distinct.Count; types = $types }
    }
    if ($RequireScalarIdentity -and $data.Count -gt 0) {
        if (-not $summary.candidate_fields.Contains($ExpansionIdentityField)) { throw 'ENTITY_ID_UNVERIFIABLE' }
        $identity = $summary.candidate_fields[$ExpansionIdentityField]
        if ($identity.present -ne $data.Count -or $identity.nulls -ne 0 -or $identity.scalar_values -ne $data.Count) { throw 'ENTITY_ID_UNVERIFIABLE' }
    }
    if ($data.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($ExpansionIdentityField) -and $summary.candidate_fields.Contains($ExpansionIdentityField)) {
        $identity = $summary.candidate_fields[$ExpansionIdentityField]
        if ($identity.present -eq $data.Count -and $identity.nulls -eq 0 -and $identity.scalar_values -eq $data.Count) { $summary.entity_count = $identity.distinct_scalar_values }
    }
    if ($data.Count -gt $PageSize) {
        if (-not $AllowPhysicalExpansion -or [string]::IsNullOrWhiteSpace($ExpansionIdentityField) -or -not $summary.candidate_fields.Contains($ExpansionIdentityField)) { throw 'PHYSICAL_ROW_BOUND_EXCEEDED' }
        $identity = $summary.candidate_fields[$ExpansionIdentityField]
        $validExpansion = $identity.present -eq $data.Count -and $identity.nulls -eq 0 -and
            $identity.scalar_values -eq $data.Count -and
            $identity.distinct_scalar_values -le $PageSize
        if (-not $validExpansion) { throw 'ENTITY_BOUND_UNVERIFIABLE' }
        $summary.entity_bound_verified = $true
        $summary.physical_expansion_accepted = $true
        $summary.entity_count = $identity.distinct_scalar_values
    }
    return $summary
}

function Invoke-SelfTest {
    if ((Get-HttpFailureReason 429) -ne 'HTTP_429' -or (Get-HttpFailureReason 503) -ne 'HTTP_NON_2XX' -or $null -ne (Get-HttpFailureReason 200)) { throw 'SELFTEST_HTTP_FAILURE_CLASSIFICATION' }
    $metadata = Get-MetadataSummary (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"fields":{"id":{"type":"integer"},"created_at":{"type":"date"}},"filters":[{"name":"service_at","type":"date"}]}')
    if ($metadata.fields_count -ne 2 -or $metadata.filters_count -ne 1 -or -not $metadata.schema_fingerprint_eligible -or $metadata.fields_profiles.Count -ne 2 -or $metadata.schema_fingerprint.Length -ne 64) { throw 'SELFTEST_METADATA' }
    $metadataArray = Get-MetadataSummary (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":{"fields":[{"name":"id","type":"integer"}],"filters":[]}}')
    if ($metadataArray.fields_shape -ne 'ARRAY' -or $metadataArray.fields_profiles[0].name -ne 'id') { throw 'SELFTEST_METADATA_ARRAY' }
    $metadataUnsafe = Get-MetadataSummary (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"fields":{"unsafe / value":{"type":"integer"}},"filters":[]}')
    if ($metadataUnsafe.schema_fingerprint_eligible -or $metadataUnsafe.fields_unnamed -ne 1 -or $metadataUnsafe.fields_profiles.Count -ne 0) { throw 'SELFTEST_METADATA_UNSAFE' }
    $metadataDuplicate = Get-MetadataSummary (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"fields":[{"name":"id"},{"name":"id"}],"filters":[]}')
    if ($metadataDuplicate.schema_fingerprint_eligible -or $metadataDuplicate.fields_duplicate_names -ne 1) { throw 'SELFTEST_METADATA_DUPLICATE' }
    $summary = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '[{"sequence_code":1},{"sequence_code":2}]') -Candidates @('sequence_code')
    if ($summary.physical_rows -ne 2 -or $summary.candidate_fields.sequence_code.distinct_scalar_values -ne 2 -or $summary.observed_field_names.Count -ne 1) { throw 'SELFTEST_SUMMARY' }
    $one = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":{"id":1}}') -Candidates @('id')
    if ($one.physical_rows -ne 1 -or $one.candidate_fields.id.integer_values -ne 1) { throw 'SELFTEST_SINGLE_OBJECT' }
    $oneArray = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":[{"id":1}]}') -Candidates @('id')
    if ($oneArray.response_shape -ne 'ARRAY' -or $oneArray.physical_rows -ne 1) { throw 'SELFTEST_SINGLE_ARRAY' }
    $oneIdentity = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":[{"id":1}]}') -Candidates @('id') -RequireScalarIdentity -ExpansionIdentityField 'id'
    if ($oneIdentity.entity_count -ne 1) { throw 'SELFTEST_SINGLE_ENTITY_COUNT' }
    $empty = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":{}}') -Candidates @('id')
    if ($empty.physical_rows -ne 0) { throw 'SELFTEST_EMPTY_OBJECT' }
    $invalidShape = $false; try { [void](Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":17}') -Candidates @('id')) } catch { $invalidShape = $_.Exception.Message -eq 'DATA_ENVELOPE_INVALID_NUMBER' }
    if (-not $invalidShape) { throw 'SELFTEST_INVALID_SHAPE' }
    $textId = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":[{"id":"a"},{"id":"a"},{"id":"a"},{"id":"a"}]}') -Candidates @('id') -AllowPhysicalExpansion -RequireScalarIdentity -ExpansionIdentityField 'id'
    if (-not $textId.entity_bound_verified -or $textId.candidate_fields.id.distinct_scalar_values -ne 1) { throw 'SELFTEST_TEXT_ID_EXPANSION' }
    $missingIdRejected = $false; try { [void](Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":[{"id":1},{}]}') -Candidates @('id') -RequireScalarIdentity -ExpansionIdentityField 'id') } catch { $missingIdRejected = $_.Exception.Message -eq 'ENTITY_ID_UNVERIFIABLE' }
    if (-not $missingIdRejected) { throw 'SELFTEST_MISSING_ID' }
    $booleanIdRejected = $false; try { [void](Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '{"data":[{"id":true}]}') -Candidates @('id') -RequireScalarIdentity -ExpansionIdentityField 'id') } catch { $booleanIdRejected = $_.Exception.Message -eq 'ENTITY_ID_UNVERIFIABLE' }
    if (-not $booleanIdRejected) { throw 'SELFTEST_BOOLEAN_ID' }
    $expandedRows = @(foreach ($index in 1..101) { [ordered]@{ sequence_code = [int64](1 + (($index - 1) % 2)) } })
    $expanded = Get-DataSummary -Json $expandedRows -Candidates @('sequence_code') -AllowPhysicalExpansion -ExpansionIdentityField 'sequence_code'
    if (-not $expanded.physical_expansion_accepted -or -not $expanded.entity_bound_verified -or $expanded.entity_count -ne 2) { throw 'SELFTEST_EXPANSION_ACCEPTED' }
    $invalidExpansion = @(foreach ($index in 1..101) { [ordered]@{ sequence_code = [int64]$index } })
    $rejected = $false; try { [void](Get-DataSummary -Json $invalidExpansion -Candidates @('sequence_code') -AllowPhysicalExpansion -ExpansionIdentityField 'sequence_code') } catch { $rejected = $_.Exception.Message -eq 'ENTITY_BOUND_UNVERIFIABLE' }
    if (-not $rejected) { throw 'SELFTEST_EXPANSION_REJECTED' }
    $bad = $false; try { [void](ConvertTo-SafeBaseUri 'http://invalid.example') } catch { $bad = $true }; if (-not $bad) { throw 'SELFTEST_URI' }
    [ordered]@{ self_test = $true; network_calls = 0; passed = $true } | ConvertTo-Json -Compress
}

if ($SelfTest) { Invoke-SelfTest; exit 0 }

$date = [datetime]::MinValue
if (-not [datetime]::TryParseExact($ClosedDate, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$date) -or $date.Date -ge (Get-Date).Date) { throw 'CLOSED_DATE_REQUIRED' }
$gate = Enter-DataExportProbeGate
if ($null -eq $gate) { [ordered]@{ transport = 'GET_WITH_QUERY'; calls_attempted = 0; stopped = $true; stop_reason = 'LOCAL_CONCURRENT_PROBE' } | ConvertTo-Json -Compress; exit 1 }
try {
    $envPath = Get-LegacyEnvPath; $baseUri = ConvertTo-SafeBaseUri (Get-LastNonEmptyEnvValue $envPath 'API_BASE_URL'); $token = Get-LastNonEmptyEnvValue $envPath 'API_DATAEXPORT_TOKEN'
    $script:state = [ordered]@{ calls_attempted = 0; last_http_status = $null; stopped = $false; stop_reason = $null }
    $definitions = @(
        [pscustomobject]@{ template = 6908; order = 'sequence_code asc'; filters = @('picks.request_date'); root = 'id'; candidates = @('id','sequence_code') },
        [pscustomobject]@{ template = 6389; order = 'corporation_sequence_number asc'; filters = @('freights.service_at'); root = 'id'; candidates = @('id','corporation_sequence_number') },
        [pscustomobject]@{ template = 6399; order = 'sequence_code asc'; filters = @('manifests.service_date'); root = 'sequence_code'; candidates = @('sequence_code','mft_pfs_pck_sequence_code','mft_mfs_key') },
        [pscustomobject]@{ template = 6906; order = 'sequence_code asc'; filters = @('quotes.requested_at'); root = 'sequence_code'; candidates = @('sequence_code') },
        [pscustomobject]@{ template = 8656; order = 'sequence_number asc'; filters = @('freights.service_at'); root = 'corporation_sequence_number'; candidates = @('corporation_sequence_number','sequence_number') },
        [pscustomobject]@{ template = 8636; order = 'issue_date desc'; filters = @('accounting_debits.issue_date','accounting_debits.created_at'); root = $null; candidates = @('accounting_debit_id','ant_ils_sequence_code') },
        [pscustomobject]@{ template = 4924; order = 'unique_id asc'; filters = @('freights.service_at'); root = 'id'; candidates = @('id','unique_id') },
        [pscustomobject]@{ template = 10633; order = 'sequence_code asc'; filters = @('check_in_orders.started_at'); root = 'sequence_code'; candidates = @('sequence_code') },
        [pscustomobject]@{ template = 6392; order = 'sequence_code asc'; filters = @('insurance_claims.opening_at_date'); root = 'sequence_code'; candidates = @('sequence_code') }
    ) | Where-Object { $Templates -contains $_.template }
    $results = [Collections.Generic.List[object]]::new()
    foreach ($definition in $definitions) {
        if ($script:state.stopped) { break }
        $script:stage = 'INFO_' + $definition.template
        $info = Invoke-CurlJsonInMemory -Uri (New-ProbeUri $baseUri $definition.template 'INFO' @{}) -Token $token
        $entry = [ordered]@{ template = $definition.template; info = [ordered]@{ http_status = $info.http_status; summary = $null }; data = $null }
        if ($null -ne $info.failure) { $results.Add($entry); break }
        try { $entry.info.summary = Get-MetadataSummary $info.json } catch { $script:state.stopped = $true; $script:state.stop_reason = 'METADATA_CONTRACT_INVALID'; $results.Add($entry); break }
        $pages = [Collections.Generic.List[object]]::new(); $firstPageEmpty = $false
        for ($offset = 0; $offset -lt $MaximumDataPages -and -not $script:state.stopped; $offset++) {
            $pageNumber = $FirstDataPage + $offset
            $script:stage = 'DATA_' + $definition.template + '_PAGE_' + $pageNumber
            $query = [ordered]@{ page = [string]$pageNumber; per = [string]$PageSize; order_by = $definition.order }
            foreach ($filter in $definition.filters) {
                $range = if ($definition.template -in @(6908, 6389)) { $ClosedDate + ' 00:00:00 - ' + $ClosedDate + ' 23:59:59' } else { $ClosedDate + ' - ' + $ClosedDate }
                $query['search[' + $filter.Replace('.','][') + ']'] = $range
            }
            $data = Invoke-CurlJsonInMemory -Uri (New-ProbeUri $baseUri $definition.template 'DATA' $query) -Token $token
            $page = [ordered]@{ page = $pageNumber; http_status = $data.http_status; summary = $null }
            if ($null -ne $data.failure) { $pages.Add($page); break }
            try {
                $page.summary = Get-DataSummary -Json $data.json -Candidates $definition.candidates -AllowPhysicalExpansion:($null -ne $definition.root) -RequireScalarIdentity:($definition.template -in @(6908, 6389)) -ExpansionIdentityField $definition.root
            } catch { $script:state.stopped = $true; $script:state.stop_reason = $_.Exception.Message; $pages.Add($page); break }
            $pages.Add($page)
            if ($page.summary.physical_rows -eq 0) { $firstPageEmpty = $true; break }
        }
        $entry.data = [ordered]@{ first_page = $FirstDataPage; maximum_pages = $MaximumDataPages; pages = @($pages); first_page_empty = $firstPageEmpty; global_completeness_proven = $false }
        # A rodada de perfil solicita exatamente a primeira página; terminalidade não é inferida.
        $results.Add($entry)
        if ($script:state.stopped) { break }
    }
    $script:stage = 'SANITIZED_SUMMARY'
    [ordered]@{ transport = 'GET_WITH_QUERY'; closed_date = $ClosedDate; page_size = $PageSize; call_budget = $MaxCalls; calls_attempted = $script:state.calls_attempted; last_http_status = $script:state.last_http_status; processing_stage = $script:stage; results = @($results); stopped = $script:state.stopped; stop_reason = $script:state.stop_reason; contract_oracle_accepted = $false } | ConvertTo-Json -Depth 12
    exit $(if ($script:state.stopped) { 1 } else { 0 })
} finally { Exit-DataExportProbeGate $gate }

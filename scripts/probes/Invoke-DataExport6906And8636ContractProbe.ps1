#requires -Version 7.5
<#
.SYNOPSIS
Runs the explicitly authorized, bounded read-only 6906/8636 contract observation.

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
    [ValidateSet(6906, 8636)]
    [int[]]$Templates = @(6906, 8636),

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateRange(1, 10000)]
    [int]$FirstDataPage = 1,

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateRange(1, 6)]
    [int]$MaximumDataPages = 1,

    [Parameter(ParameterSetName = 'Probe')]
    [switch]$Allow6906PhysicalExpansion,

    [Parameter(Mandatory, ParameterSetName = 'SelfTest')]
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DataExportProbeGate.ps1')

$PageSize = 100
$MaxResponseBytes = 10MB
$TimeoutSeconds = 30
$InterCallDelaySeconds = 3
$script:stage = 'INITIALIZATION'
$MaxCalls = $Templates.Count * (1 + $MaximumDataPages)

trap {
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

function Quote-CurlConfig { param([string]$Value) return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"' }

function Invoke-CurlJsonInMemory {
    param([Uri]$Uri, [string]$Token)
    if (-not (Reserve-Call)) { return [pscustomobject]@{ http_status = $null; json = $null; failure = $script:state.stop_reason } }
    $marker = '__V2_6906_8636_HTTP_' + [guid]::NewGuid().ToString('N') + '__'
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
    $body = $raw.Substring(0, $match.Index); $json = $null
    try { $json = ConvertFrom-Json -InputObject $body -AsHashtable -Depth 64 -NoEnumerate -DateKind String } catch { $script:state.stopped = $true; $script:state.stop_reason = 'INVALID_JSON'; return [pscustomobject]@{ http_status = $status; json = $null; failure = 'INVALID_JSON' } }
    if ($status -lt 200 -or $status -gt 299) { $script:state.stopped = $true; $script:state.stop_reason = if ($status -eq 429) { 'HTTP_429' } else { 'HTTP_NON_2XX' }; return [pscustomobject]@{ http_status = $status; json = $null; failure = $script:state.stop_reason } }
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

function Get-MetadataSummary {
    param($Json)
    if ($Json -is [System.Collections.IDictionary] -and ($Json.Contains('error') -or $Json.Contains('errors'))) { throw 'METADATA_ERROR_ENVELOPE' }
    $node = if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) { $Json['data'] } else { $Json }
    if ($node -isnot [System.Collections.IDictionary]) { throw 'METADATA_ENVELOPE_INVALID' }
    $result = [ordered]@{}
    foreach ($kind in @('fields','filters')) {
        $value = if ($node.Contains($kind)) { $node[$kind] } else { $null }
        $result[$kind + '_shape'] = Get-JsonType $value
        $result[$kind + '_count'] = if ($value -is [System.Collections.IDictionary]) { $value.Count } elseif ($value -is [System.Collections.IList] -and $value -isnot [string]) { $value.Count } elseif ($null -eq $value) { 0 } else { -1 }
    }
    return $result
}

function Get-DataSummary {
    param(
        $Json,
        [string[]]$Candidates,
        [switch]$AllowPhysicalExpansion,
        [string]$ExpansionIdentityField
    )
    if ($Json -is [System.Collections.IDictionary] -and ($Json.Contains('error') -or $Json.Contains('errors'))) { throw 'DATA_ERROR_ENVELOPE' }
    $data = if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) { $Json['data'] } else { $Json }
    if ($data -isnot [System.Collections.IList] -or $data -is [string]) { throw 'DATA_ENVELOPE_INVALID' }
    $summary = [ordered]@{ physical_rows = $data.Count; physical_limit_verified = ($data.Count -le $PageSize); entity_bound_verified = ($data.Count -le $PageSize); physical_expansion_accepted = $false; candidate_fields = [ordered]@{} }
    foreach ($candidate in $Candidates) {
        $present = 0; $nulls = 0; $scalars = 0; $integers = 0; $types = [ordered]@{}; $distinct = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($row in $data) {
            if ($row -isnot [System.Collections.IDictionary]) { throw 'DATA_ROW_NOT_OBJECT' }
            if (-not $row.Contains($candidate)) { continue }
            $present++; $value = $row[$candidate]; $type = Get-JsonType $value
            if (-not $types.Contains($type)) { $types[$type] = 0 }; $types[$type]++
            if ($null -eq $value) { $nulls++; continue }
            if ($type -in @('STRING','NUMBER','BOOLEAN')) { $scalars++; [void]$distinct.Add($type + ':' + [string]$value) }
            if ($type -eq 'NUMBER' -and (Test-IntegralJsonNumber $value)) { $integers++ }
        }
        $summary.candidate_fields[$candidate] = [ordered]@{ present = $present; nulls = $nulls; scalar_values = $scalars; integer_values = $integers; distinct_scalar_values = $distinct.Count; types = $types }
    }
    if ($data.Count -gt $PageSize) {
        if (-not $AllowPhysicalExpansion -or [string]::IsNullOrWhiteSpace($ExpansionIdentityField) -or -not $summary.candidate_fields.Contains($ExpansionIdentityField)) { throw 'PHYSICAL_ROW_BOUND_EXCEEDED' }
        $identity = $summary.candidate_fields[$ExpansionIdentityField]
        $validExpansion = $identity.present -eq $data.Count -and $identity.nulls -eq 0 -and
            $identity.scalar_values -eq $data.Count -and $identity.integer_values -eq $data.Count -and
            $identity.types.Count -eq 1 -and $identity.types.Contains('NUMBER') -and
            $identity.distinct_scalar_values -le $PageSize
        if (-not $validExpansion) { throw 'ENTITY_BOUND_UNVERIFIABLE' }
        $summary.entity_bound_verified = $true
        $summary.physical_expansion_accepted = $true
        $summary.entity_count = $identity.distinct_scalar_values
    }
    return $summary
}

function Invoke-SelfTest {
    $summary = Get-DataSummary -Json (ConvertFrom-Json -AsHashtable -NoEnumerate -InputObject '[{"sequence_code":1},{"sequence_code":2}]') -Candidates @('sequence_code')
    if ($summary.physical_rows -ne 2 -or $summary.candidate_fields.sequence_code.distinct_scalar_values -ne 2) { throw 'SELFTEST_SUMMARY' }
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

if ($Allow6906PhysicalExpansion -and ($Templates.Count -ne 1 -or $Templates[0] -ne 6906)) { throw 'PHYSICAL_EXPANSION_LIMITED_TO_6906' }

$date = [datetime]::MinValue
if (-not [datetime]::TryParseExact($ClosedDate, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$date) -or $date.Date -ge (Get-Date).Date) { throw 'CLOSED_DATE_REQUIRED' }
$gate = Enter-DataExportProbeGate
if ($null -eq $gate) { [ordered]@{ transport = 'GET_WITH_QUERY'; calls_attempted = 0; stopped = $true; stop_reason = 'LOCAL_CONCURRENT_PROBE' } | ConvertTo-Json -Compress; exit 1 }
try {
    $envPath = Get-LegacyEnvPath; $baseUri = ConvertTo-SafeBaseUri (Get-LastNonEmptyEnvValue $envPath 'API_BASE_URL'); $token = Get-LastNonEmptyEnvValue $envPath 'API_DATAEXPORT_TOKEN'
    $script:state = [ordered]@{ calls_attempted = 0; last_http_status = $null; stopped = $false; stop_reason = $null }
    $definitions = @(
        [pscustomobject]@{ template = 6906; order = 'sequence_code asc'; filters = @('quotes.requested_at'); candidates = @('sequence_code','requested_at','qoe_qes_fit_nse_issued_at','qoe_qes_fit_fhe_cte_issued_at','qoe_qes_total','qoe_crn_psn_nickname','qoe_uer_name','qoe_qes_ony_sae_code','qoe_qes_diy_sae_code') },
        [pscustomobject]@{ template = 8636; order = 'issue_date desc'; filters = @('accounting_debits.issue_date','accounting_debits.created_at'); candidates = @('accounting_debit_id','ant_ils_sequence_code') }
    ) | Where-Object { $Templates -contains $_.template }
    $results = [Collections.Generic.List[object]]::new()
    foreach ($definition in $definitions) {
        if ($script:state.stopped) { break }
        $script:stage = 'INFO_' + $definition.template
        $info = Invoke-CurlJsonInMemory -Uri (New-ProbeUri $baseUri $definition.template 'INFO' @{}) -Token $token
        $entry = [ordered]@{ template = $definition.template; info = [ordered]@{ http_status = $info.http_status; summary = $null }; data = $null }
        if ($null -ne $info.failure) { $results.Add($entry); break }
        try { $entry.info.summary = Get-MetadataSummary $info.json } catch { $script:state.stopped = $true; $script:state.stop_reason = 'METADATA_CONTRACT_INVALID'; $results.Add($entry); break }
        $pages = [Collections.Generic.List[object]]::new(); $terminal = $false
        for ($offset = 0; $offset -lt $MaximumDataPages -and -not $script:state.stopped; $offset++) {
            $pageNumber = $FirstDataPage + $offset
            $script:stage = 'DATA_' + $definition.template + '_PAGE_' + $pageNumber
            $query = [ordered]@{ page = [string]$pageNumber; per = [string]$PageSize; order_by = $definition.order }
            foreach ($filter in $definition.filters) { $query['search[' + $filter.Replace('.','][') + ']'] = $ClosedDate + ' - ' + $ClosedDate }
            $data = Invoke-CurlJsonInMemory -Uri (New-ProbeUri $baseUri $definition.template 'DATA' $query) -Token $token
            $page = [ordered]@{ page = $pageNumber; http_status = $data.http_status; summary = $null }
            if ($null -ne $data.failure) { $pages.Add($page); break }
            try {
                $page.summary = Get-DataSummary -Json $data.json -Candidates $definition.candidates -AllowPhysicalExpansion:($Allow6906PhysicalExpansion -and $definition.template -eq 6906) -ExpansionIdentityField $(if ($definition.template -eq 6906) { 'sequence_code' } else { $null })
            } catch { $script:state.stopped = $true; $script:state.stop_reason = $_.Exception.Message; $pages.Add($page); break }
            $pages.Add($page)
            if ($page.summary.physical_rows -eq 0) { $terminal = $true; break }
        }
        $entry.data = [ordered]@{ first_page = $FirstDataPage; maximum_pages = $MaximumDataPages; pages = @($pages); terminal_observed = $terminal }
        if (-not $script:state.stopped -and -not $terminal -and $pages.Count -eq $MaximumDataPages) { $script:state.stopped = $true; $script:state.stop_reason = 'NON_TERMINAL_PAGE_CAP_REACHED' }
        $results.Add($entry)
        if ($script:state.stopped) { break }
    }
    $script:stage = 'SANITIZED_SUMMARY'
    [ordered]@{ transport = 'GET_WITH_QUERY'; closed_date = $ClosedDate; page_size = $PageSize; call_budget = $MaxCalls; calls_attempted = $script:state.calls_attempted; last_http_status = $script:state.last_http_status; processing_stage = $script:stage; results = @($results); stopped = $script:state.stopped; stop_reason = $script:state.stop_reason; contract_oracle_accepted = $false } | ConvertTo-Json -Depth 12
    exit $(if ($script:state.stopped) { 1 } else { 0 })
} finally { Exit-DataExportProbeGate $gate }

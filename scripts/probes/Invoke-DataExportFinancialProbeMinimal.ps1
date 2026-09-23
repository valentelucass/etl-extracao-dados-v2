#requires -Version 7.5
<#
.SYNOPSIS
Executa uma prova financeira sanitizada, estritamente read-only, entre 6908, 6389, 4924 e GraphQL.

.DESCRIPTION
Usa somente curl.exe, respostas em memória e queries GraphQL estáticas. A saída contém somente
contagens, flags e classificações; não grava nem imprime URL, token, payload, cursor, ID ou valor.
Cada execucao exige uma ordem que declare a janela fechada e o teto; a data do
exemplo nao constitui uma janela recomendada.

.EXAMPLE
pwsh -NoProfile -File .\scripts\probes\Invoke-DataExportFinancialProbeMinimal.ps1 -ClosedDate 2026-01-02

.EXAMPLE
pwsh -NoProfile -File .\scripts\probes\Invoke-DataExportFinancialProbeMinimal.ps1 -SelfTest
#>
[CmdletBinding(DefaultParameterSetName = 'Probe')]
param(
    [Parameter(Mandatory, ParameterSetName = 'Probe')]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$ClosedDate,

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateRange(1, 100)]
    [int]$PageSize = 100,

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateRange(1, 35)]
    [int]$MaxCalls = 10,

    [Parameter(ParameterSetName = 'Probe')]
    [ValidateRange(0, 10)]
    [int]$InterCallDelaySeconds = 2,

    [ValidateRange(2, 9)]
    [int]$MaximumPagesPerSource = 2,

    [Parameter(Mandatory, ParameterSetName = 'SelfTest')]
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DataExportProbeGate.ps1')

$MaxResponseBytes = 10MB
$CurlTimeoutSeconds = 30
$CurlConnectTimeoutSeconds = 10
$CaptureOverheadBytes = 512
$GraphQlFirst = 100
$processingStage = 'INITIALIZATION'

trap {
    $failureState = Get-Variable -Name state -ValueOnly -ErrorAction SilentlyContinue
    $callsAttempted = if ($failureState -is [System.Collections.IDictionary] -and $failureState.Contains('calls_attempted')) { $failureState['calls_attempted'] } else { $null }
    $failureStage = Get-Variable -Name processingStage -ValueOnly -ErrorAction SilentlyContinue
    [ordered]@{
        transport = [ordered]@{ data_export = 'GET_QUERY'; graphql = 'POST_QUERY' }
        calls_attempted = $callsAttempted
        processing_stage = $failureStage
        stopped = $true
        stop_reason = 'INTERNAL_PROCESSING_FAILURE'
    } | ConvertTo-Json -Compress
    exit 1
}

function ConvertTo-ClosedDate {
    param([string]$Value)

    $parsed = [datetime]::MinValue
    if (-not [datetime]::TryParseExact(
            $Value, 'yyyy-MM-dd', [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::None, [ref]$parsed)) {
        throw 'A data informada nao e valida.'
    }
    if ($parsed.Date -ge (Get-Date).Date) {
        throw 'A sonda exige uma data de negocio fechada.'
    }
    return $parsed.Date
}

function Get-LegacyEnvPath {
    try {
        $v2Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
        $local = Join-Path $v2Root '.env'
        if (Test-Path -LiteralPath $local -PathType Leaf) {
            return (Resolve-Path -LiteralPath $local).Path
        }
        $envPath = Join-Path (Split-Path -Parent $v2Root) 'etl-extracao-dados\.env'
        if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) { throw 'missing' }
        return (Resolve-Path -LiteralPath $envPath).Path
    } catch {
        throw 'O .env legado necessario para a sonda nao esta disponivel.'
    }
}

function Remove-OuterEnvQuotes {
    param([string]$Value)
    $trimmed = $Value.Trim()
    if ($trimmed.Length -ge 2 -and (($trimmed.StartsWith('"') -and $trimmed.EndsWith('"')) -or
                ($trimmed.StartsWith("'") -and $trimmed.EndsWith("'")))) {
        return $trimmed.Substring(1, $trimmed.Length - 2)
    }
    return $trimmed
}

function Get-LastNonEmptyEnvValue {
    param([string]$EnvPath, [string]$Name)
    $value = $null
    foreach ($line in Get-Content -LiteralPath $EnvPath) {
        if ($line -match ('^\s*' + [regex]::Escape($Name) + '\s*=\s*(.*)$')) {
            $candidate = Remove-OuterEnvQuotes -Value $Matches[1]
            if (-not [string]::IsNullOrWhiteSpace($candidate)) { $value = $candidate }
        }
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw ('A configuracao local obrigatoria nao esta disponivel: ' + $Name + '.')
    }
    return $value
}

function ConvertTo-SafeHttpsUri {
    param([string]$Value, [string]$Name)
    $uri = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($uri.Host) -or
        -not [string]::IsNullOrWhiteSpace($uri.UserInfo) -or
        -not [string]::IsNullOrWhiteSpace($uri.Query) -or
        -not [string]::IsNullOrWhiteSpace($uri.Fragment)) {
        throw ('A configuracao ' + $Name + ' nao e uma URL HTTPS segura.')
    }
    return $uri
}

function Resolve-GraphQlUri {
    param([System.Uri]$BaseUri, [string]$Endpoint)
    $candidate = $null
    if ([System.Uri]::TryCreate($Endpoint, [System.UriKind]::Absolute, [ref]$candidate)) {
        return ConvertTo-SafeHttpsUri -Value $candidate.AbsoluteUri -Name 'API_GRAPHQL_ENDPOINT'
    }
    $relative = $Endpoint.Trim()
    if ([string]::IsNullOrWhiteSpace($relative) -or -not $relative.StartsWith('/')) {
        throw 'A configuracao API_GRAPHQL_ENDPOINT deve ser um caminho absoluto ou uma URL HTTPS.'
    }
    return ConvertTo-SafeHttpsUri -Value (([System.Uri]::new($BaseUri, $relative)).AbsoluteUri) -Name 'API_GRAPHQL_ENDPOINT'
}

function ConvertTo-QueryString {
    param([System.Collections.IDictionary]$Parameters)
    return (($Parameters.Keys | ForEach-Object {
                ([System.Uri]::EscapeDataString([string]$_)) + '=' +
                    ([System.Uri]::EscapeDataString([string]$Parameters[$_]))
            }) -join '&')
}

function New-DataExportUri {
    param([System.Uri]$BaseUri, [int]$TemplateId, [System.Collections.IDictionary]$Query)
    return [System.Uri]::new(
        $BaseUri, ('/api/analytics/reports/{0}/data?{1}' -f $TemplateId, (ConvertTo-QueryString -Parameters $Query)))
}

function Test-ByteSequenceAt {
    param([byte[]]$Source, [byte[]]$Needle, [int]$Start)
    if ($Start -lt 0 -or ($Start + $Needle.Length) -gt $Source.Length) { return $false }
    for ($index = 0; $index -lt $Needle.Length; $index++) {
        if ($Source[$Start + $index] -ne $Needle[$index]) { return $false }
    }
    return $true
}

function Stop-Probe {
    param([System.Collections.IDictionary]$State, [string]$Reason)
    if (-not $State['stopped']) { $State['stopped'] = $true; $State['stop_reason'] = $Reason }
}

function Reserve-Call {
    param([System.Collections.IDictionary]$State, [int]$CallBudget, [int]$DelaySeconds)
    if ($State['stopped']) { return $false }
    if ($State['calls_attempted'] -ge $CallBudget) { Stop-Probe -State $State -Reason 'CALL_BUDGET_REACHED'; return $false }
    if ($State['calls_attempted'] -gt 0 -and $DelaySeconds -gt 0) { Start-Sleep -Seconds $DelaySeconds }
    $State['calls_attempted'] = [int]$State['calls_attempted'] + 1
    return $true
}

function Invoke-CurlJsonInMemory {
    param(
        [ValidateSet('GET', 'POST')][string]$Method,
        [System.Uri]$Uri,
        [string]$Token,
        [AllowNull()][string]$JsonBody,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    if (-not (Reserve-Call -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds)) {
        return [pscustomobject]@{ Executed = $false; HttpStatus = $null; JsonParsed = $false; Json = $null }
    }
    $marker = '__V2_FINANCIAL_STATUS_{0}__' -f ([guid]::NewGuid().ToString('N'))
    $markerBytes = [Text.Encoding]::ASCII.GetBytes($marker)
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'curl.exe'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $arguments = @(
        '--disable', '--globoff', '--silent', '--show-error', '--request', $Method,
        '--header', ('Authorization: Bearer {0}' -f $Token), '--header', 'Accept: application/json',
        '--connect-timeout', [string]$CurlConnectTimeoutSeconds, '--max-time', [string]$CurlTimeoutSeconds,
        '--max-redirs', '0', '--max-filesize', [string]$MaxResponseBytes, '--output', '-',
        '--write-out', ('{0}%{{http_code}}{0}' -f $marker)
    )
    if ($Method -eq 'POST') { $arguments += @('--header', 'Content-Type: application/json', '--data-binary', $JsonBody) }
    $arguments += $Uri.AbsoluteUri
    foreach ($argument in $arguments) { [void]$startInfo.ArgumentList.Add([string]$argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $captured = [IO.MemoryStream]::new()
    $allBytes = $null
    $responseLimitExceeded = $false
    $transportFailure = $false
    try {
        if (-not $process.Start()) { $transportFailure = $true } else {
            $stderrTask = $process.StandardError.ReadToEndAsync()
            $buffer = [byte[]]::new(8192)
            while ($true) {
                $read = $process.StandardOutput.BaseStream.Read($buffer, 0, $buffer.Length)
                if ($read -le 0) { break }
                if (($captured.Length + $read) -gt ($MaxResponseBytes + $CaptureOverheadBytes)) {
                    $responseLimitExceeded = $true
                    try { $process.Kill($true) } catch { $process.Kill() }
                    break
                }
                $captured.Write($buffer, 0, $read)
            }
            $process.WaitForExit()
            $null = $stderrTask.GetAwaiter().GetResult()
            $allBytes = $captured.ToArray()
        }
    } catch {
        $transportFailure = $true
        try { if (-not $process.HasExited) { $process.Kill($true) } } catch { }
    } finally {
        $captured.Dispose(); $process.Dispose(); $JsonBody = $null
    }
    if ($responseLimitExceeded) { Stop-Probe -State $State -Reason 'RESPONSE_LIMIT_EXCEEDED'; return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null } }
    if ($transportFailure -or $null -eq $allBytes) { Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'; return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null } }
    $endMarkerStart = $allBytes.Length - $markerBytes.Length
    $statusStart = $endMarkerStart - 3
    $startMarkerStart = $statusStart - $markerBytes.Length
    if (-not (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $endMarkerStart) -or
        -not (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $startMarkerStart)) {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'; return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null }
    }
    $statusText = [Text.Encoding]::ASCII.GetString($allBytes, $statusStart, 3)
    if ($statusText -notmatch '^\d{3}$' -or $startMarkerStart -gt $MaxResponseBytes) {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'; return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null }
    }
    $status = [int]$statusText
    $State['last_http_status'] = $status
    $json = $null; $jsonParsed = $false
    try {
        $bodyText = [Text.Encoding]::UTF8.GetString($allBytes, 0, $startMarkerStart)
        $json = ConvertFrom-Json -InputObject $bodyText -AsHashtable -Depth 100 -NoEnumerate -DateKind String
        $jsonParsed = $true
    } catch { $json = $null } finally { $bodyText = $null; $allBytes = $null }
    if ($status -lt 200 -or $status -gt 299) {
        Stop-Probe -State $State -Reason $(if ($status -eq 429) { 'HTTP_429' } else { 'HTTP_NON_2XX' })
    } elseif (-not $jsonParsed) { Stop-Probe -State $State -Reason 'INVALID_JSON' }
    return [pscustomobject]@{ Executed = $true; HttpStatus = $status; JsonParsed = $jsonParsed; Json = $json }
}

function Test-ErrorEnvelope {
    param($Value)
    return $Value -is [System.Collections.IDictionary] -and ($Value.Contains('error') -or $Value.Contains('errors'))
}

function Get-DataExportRecords {
    param($Json)
    $result = [pscustomobject]@{ Valid = $false; Records = [Collections.Generic.List[object]]::new() }
    if (Test-ErrorEnvelope -Value $Json) { return $result }
    # Do not use an if-expression here: PowerShell enumerates an empty JSON
    # array through the pipeline and would turn the valid terminal page []
    # into $null.
    $data = $Json
    if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) {
        $data = $Json['data']
    }
    if ($null -eq $data -or (Test-ErrorEnvelope -Value $data)) { return $result }
    if ($data -is [System.Collections.IList] -and -not ($data -is [string])) {
        foreach ($record in $data) { if (-not ($record -is [System.Collections.IDictionary])) { return $result }; $result.Records.Add($record) }
    } elseif ($data -is [System.Collections.IDictionary]) {
        if ($data.Count -gt 0) { $result.Records.Add($data) }
    } else { return $result }
    $result.Valid = $true
    return $result
}

function ConvertTo-Scalar {
    param($Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) { $value = $Value.Trim(); return $(if ([string]::IsNullOrWhiteSpace($value)) { $null } else { $value }) }
    if ($Value -is [sbyte] -or $Value -is [byte] -or $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [single] -or $Value -is [double] -or $Value -is [decimal]) {
        return [Convert]::ToString($Value, [Globalization.CultureInfo]::InvariantCulture)
    }
    return $null
}

function Get-MapScalar {
    param($Record, [string]$Field)
    if (-not ($Record -is [System.Collections.IDictionary]) -or -not $Record.Contains($Field)) { return $null }
    return ConvertTo-Scalar -Value $Record[$Field]
}

function Get-NestedMapScalar {
    param($Record, [string]$ObjectField, [string]$Field)
    if (-not ($Record -is [System.Collections.IDictionary]) -or -not $Record.Contains($ObjectField)) { return $null }
    return Get-MapScalar -Record $Record[$ObjectField] -Field $Field
}

function New-EntityIndex {
    param([string[]]$AttributeNames)
    $complete = @{}; $consistent = @{}
    $normalizedAttributeNames = [Collections.Generic.List[string]]::new()
    foreach ($name in @($AttributeNames)) {
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            $normalizedAttributeNames.Add($name)
            $complete[$name] = $true
            $consistent[$name] = $true
        }
    }
    return [pscustomobject]@{
        IdentityValid = $true
        PhysicalRecordCount = 0
        DuplicatePhysicalRowCount = 0
        InconsistentEntityCount = 0
        EntityById = [Collections.Generic.Dictionary[string, object]]::new([StringComparer]::Ordinal)
        IdByNaturalKey = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
        AttributeNames = $normalizedAttributeNames.ToArray()
        AttributeComplete = $complete
        AttributeConsistent = $consistent
    }
}

function Add-Entity {
    param($Index, $Id, $NaturalKey, [System.Collections.IDictionary]$Attributes)
    $script:processingStage = 'ENTITY_ADD_PHYSICAL_COUNT'
    $Index.PhysicalRecordCount = [int]$Index.PhysicalRecordCount + 1
    $script:processingStage = 'ENTITY_ADD_CANONICALIZE'
    $canonicalId = ConvertTo-Scalar -Value $Id
    $canonicalNaturalKey = ConvertTo-Scalar -Value $NaturalKey
    if ($null -eq $canonicalId -or $null -eq $canonicalNaturalKey) { $Index.IdentityValid = $false; return }
    $existing = $null
    $script:processingStage = 'ENTITY_ADD_LOOKUP_ID'
    if ($Index.EntityById.ContainsKey($canonicalId)) {
        $existing = $Index.EntityById[$canonicalId]
        if ($existing.NaturalKey -ne $canonicalNaturalKey) { $Index.IdentityValid = $false; $Index.InconsistentEntityCount++; return }
        $Index.DuplicatePhysicalRowCount++
    $script:processingStage = 'ENTITY_ADD_LOOKUP_NATURAL_KEY'
    } elseif ($Index.IdByNaturalKey.ContainsKey($canonicalNaturalKey)) {
        $Index.IdentityValid = $false; $Index.InconsistentEntityCount++; return
    } else {
        $script:processingStage = 'ENTITY_ADD_BUILD_ATTRIBUTES'
        $initial = @{}
        foreach ($field in $Index.AttributeNames) { $initial[$field] = $Attributes[$field] }
        $existing = [pscustomobject]@{ NaturalKey = $canonicalNaturalKey; Attributes = $initial }
        $script:processingStage = 'ENTITY_ADD_INSERT_ID'
        [void]$Index.EntityById.Add($canonicalId, $existing)
        $script:processingStage = 'ENTITY_ADD_INSERT_NATURAL_KEY'
        [void]$Index.IdByNaturalKey.Add($canonicalNaturalKey, $canonicalId)
    }
    $script:processingStage = 'ENTITY_ADD_ATTRIBUTES'
    foreach ($field in $Index.AttributeNames) {
        $value = $Attributes[$field]
        if ($null -eq $value) { $Index.AttributeComplete[$field] = $false; continue }
        $previous = $existing.Attributes[$field]
        if ($null -eq $previous) { $Index.AttributeComplete[$field] = $false }
        elseif ($previous -ne $value) { $Index.AttributeComplete[$field] = $false; $Index.AttributeConsistent[$field] = $false }
    }
}

function Get-EntityAttribute {
    param($Index, $Id, [string]$Name)
    $canonicalId = ConvertTo-Scalar -Value $Id
    if ($null -eq $canonicalId -or -not $Index.EntityById.ContainsKey($canonicalId)) { return $null }
    return $Index.EntityById[$canonicalId].Attributes[$Name]
}

function Test-StringSetsEqual {
    param($Left, $Right)
    if ($Left.Count -ne $Right.Count) { return $false }
    foreach ($value in $Left) { if (-not $Right.Contains($value)) { return $false } }
    return $true
}

function Get-IdentityEvidence {
    param($Left, $Right, [bool]$Completed)
    if (-not $Completed -or -not $Left.IdentityValid -or -not $Right.IdentityValid) {
        return [ordered]@{ completed = $false; entity_counts_equal = $false; natural_key_sets_equal = $false; canonical_ids_equal = $false }
    }
    $naturalKeysEqual = Test-StringSetsEqual -Left $Left.IdByNaturalKey.Keys -Right $Right.IdByNaturalKey.Keys
    $idsEqual = $naturalKeysEqual
    if ($naturalKeysEqual) {
        foreach ($key in $Left.IdByNaturalKey.Keys) {
            if ($Left.IdByNaturalKey[$key] -ne $Right.IdByNaturalKey[$key]) { $idsEqual = $false; break }
        }
    }
    return [ordered]@{
        completed = $true
        entity_counts_equal = ($Left.EntityById.Count -eq $Right.EntityById.Count)
        natural_key_sets_equal = $naturalKeysEqual
        canonical_ids_equal = $idsEqual
    }
}

function Invoke-DataExportTraversal {
    param($Definition, [datetime]$Date, [System.Uri]$BaseUri, [string]$Token,
        [System.Collections.IDictionary]$State, [int]$CallBudget, [int]$DelaySeconds)
    $index = New-EntityIndex -AttributeNames $Definition.Attributes
    $pages = [Collections.Generic.List[object]]::new(); $terminal = $false
    $range = '{0} - {0}' -f $Date.ToString('yyyy-MM-dd')
    for ($page = 1; $page -le $MaximumPagesPerSource -and -not $State['stopped']; $page++) {
        $query = [ordered]@{
            (('search[{0}]' -f $Definition.BusinessFilter.Replace('.', ']['))) = $range
            page = [string]$page; per = [string]$PageSize
        }
        # 4924 has conflicting historical order conventions.  Do not make an
        # unsupported global-order claim for the auxiliary CT-e comparison.
        if (-not [string]::IsNullOrWhiteSpace($Definition.OrderBy)) {
            $query['order_by'] = $Definition.OrderBy
        }
        $response = Invoke-CurlJsonInMemory -Method GET -Uri (New-DataExportUri -BaseUri $BaseUri -TemplateId $Definition.TemplateId -Query $query) `
            -Token $Token -JsonBody $null -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        if (-not $response.Executed) { break }
        if ($State['stopped'] -or -not $response.JsonParsed -or (Test-ErrorEnvelope -Value $response.Json)) {
            if (-not $State['stopped']) { Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_ENVELOPE' }; break
        }
        $records = Get-DataExportRecords -Json $response.Json
        if (-not $records.Valid) { Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_PAGE_SHAPE'; break }
        $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal); $idsVerifiable = $true
        foreach ($record in $records.Records) {
            $id = Get-MapScalar -Record $record -Field 'id'
            if ($null -eq $id) { $idsVerifiable = $false } else { [void]$ids.Add($id) }
            $attributes = @{}
            foreach ($field in $Definition.Attributes) { $attributes[$field] = Get-MapScalar -Record $record -Field $field }
            Add-Entity -Index $index -Id $id -NaturalKey (Get-MapScalar -Record $record -Field $Definition.NaturalKey) -Attributes $attributes
        }
        $withinLimit = $idsVerifiable -and $ids.Count -le $PageSize
        $pages.Add([ordered]@{ physical_record_count = $records.Records.Count; distinct_entity_count = $ids.Count; entity_ids_verifiable = $idsVerifiable; entity_count_within_requested_page_size = $withinLimit; page_is_empty = ($records.Records.Count -eq 0) })
        if (-not $idsVerifiable) { Stop-Probe -State $State -Reason 'ENTITY_COUNT_NOT_VERIFIABLE'; break }
        if (-not $withinLimit) { Stop-Probe -State $State -Reason 'ENTITY_COUNT_EXCEEDS_REQUESTED_SIZE'; break }
        if (-not $index.IdentityValid) { Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_ENTITY_IDENTITY'; break }
        if ($records.Records.Count -eq 0) { $terminal = $true; break }
    }
    if (-not $State['stopped'] -and -not $terminal) { Stop-Probe -State $State -Reason 'DATA_EXPORT_PAGE_LIMIT_REACHED' }
    return [pscustomobject]@{ TemplateId = $Definition.TemplateId; Index = $index; Pages = $pages; Terminal = $terminal; Completed = ($terminal -and $index.IdentityValid -and -not $State['stopped']) }
}

function New-GraphQlPayload {
    param([ValidateSet('PICK', 'FREIGHT')][string]$Kind, [datetime]$Date, [AllowNull()][string]$After)
    if ($Kind -eq 'PICK') {
        $query = 'query FinancialProbePicks($params: PickInput!, $after: String, $first: Int!) { pick(params: $params, after: $after, first: $first) { edges { node { id sequenceCode pickItems { id } } } pageInfo { hasNextPage endCursor } } }'
        $params = [ordered]@{ requestDate = $Date.ToString('yyyy-MM-dd') }
    } else {
        $query = 'query FinancialProbeFreights($params: FreightInput!, $after: String, $first: Int!) { freight(params: $params, after: $after, first: $first) { edges { node { id corporationSequenceNumber pickItemId total accountingCreditId accountingCreditInstallmentId cte { key } } } pageInfo { hasNextPage endCursor } } }'
        $params = [ordered]@{ serviceAt = ('{0} - {0}' -f $Date.ToString('yyyy-MM-dd')) }
    }
    return [ordered]@{ query = $query; variables = [ordered]@{ params = $params; after = $After; first = $GraphQlFirst } } | ConvertTo-Json -Compress -Depth 8
}

function Get-GraphQlEdges {
    param($Connection)
    $invalid = [pscustomobject]@{ Valid = $false; Edges = @() }
    if (-not ($Connection -is [System.Collections.IDictionary]) -or -not $Connection.Contains('edges')) { return $invalid }
    $edges = $Connection['edges']
    if ($edges -is [string] -or -not ($edges -is [System.Collections.IList])) { return $invalid }
    return [pscustomobject]@{ Valid = $true; Edges = $edges }
}

function Invoke-GraphQlTraversal {
    param([ValidateSet('PICK', 'FREIGHT')][string]$Kind, [datetime]$Date, [System.Uri]$Uri, [string]$Token,
        [System.Collections.IDictionary]$State, [int]$CallBudget, [int]$DelaySeconds)
    $attributes = if ($Kind -eq 'PICK') { @() } else { @('pick_item_id', 'total', 'cte_key', 'accounting_credit_id', 'accounting_credit_installment_id') }
    $index = New-EntityIndex -AttributeNames $attributes
    $pickItemToSequence = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    $pickItemMappingComplete = $true; $pickItemMappingConflictCount = 0; $pages = 0; $terminal = $false; $after = $null
    $edgeCount = 0; $invalidEdgeCount = 0; $processingFailureStage = $null
    $seenCursors = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $root = if ($Kind -eq 'PICK') { 'pick' } else { 'freight' }
    $naturalField = if ($Kind -eq 'PICK') { 'sequenceCode' } else { 'corporationSequenceNumber' }
    for ($page = 1; $page -le $MaximumPagesPerSource -and -not $State['stopped']; $page++) {
        $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_PAYLOAD' } else { 'GRAPHQL_FREIGHT_PAYLOAD' }
        $body = New-GraphQlPayload -Kind $Kind -Date $Date -After $after
        $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_REQUEST' } else { 'GRAPHQL_FREIGHT_REQUEST' }
        $response = Invoke-CurlJsonInMemory -Method POST -Uri $Uri -Token $Token -JsonBody $body -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        $body = $null
        $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_ENVELOPE' } else { 'GRAPHQL_FREIGHT_ENVELOPE' }
        if ($State['stopped'] -or -not $response.JsonParsed -or -not ($response.Json -is [System.Collections.IDictionary]) -or
            $response.Json.Contains('errors') -or -not $response.Json.Contains('data') -or -not ($response.Json['data'] -is [System.Collections.IDictionary]) -or
            -not $response.Json['data'].Contains($root)) { if (-not $State['stopped']) { Stop-Probe -State $State -Reason 'GRAPHQL_INVALID_ENVELOPE' }; break }
        $connection = $response.Json['data'][$root]
        $edgePage = Get-GraphQlEdges -Connection $connection
        if (-not $edgePage.Valid -or -not $connection.Contains('pageInfo') -or -not ($connection['pageInfo'] -is [System.Collections.IDictionary])) { Stop-Probe -State $State -Reason 'GRAPHQL_INVALID_PAGE_SHAPE'; break }
        $pages++
        $edgeCount += $edgePage.Edges.Count
        $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGES' } else { 'GRAPHQL_FREIGHT_EDGES' }
        try {
            foreach ($edge in $edgePage.Edges) {
                $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGE_SHAPE' } else { 'GRAPHQL_FREIGHT_EDGE_SHAPE' }
                if (-not ($edge -is [System.Collections.IDictionary]) -or -not $edge.Contains('node')) { $index.IdentityValid = $false; $invalidEdgeCount++; continue }
                $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGE_NODE' } else { 'GRAPHQL_FREIGHT_EDGE_NODE' }
                $node = $edge['node']; $nodeAttributes = @{}
                if (-not ($node -is [System.Collections.IDictionary]) -or -not $node.Contains('id') -or -not $node.Contains($naturalField)) {
                    $index.IdentityValid = $false
                    $invalidEdgeCount++
                    continue
                }
                if ($Kind -eq 'FREIGHT') {
                    $nodeAttributes['pick_item_id'] = Get-MapScalar -Record $node -Field 'pickItemId'
                    $nodeAttributes['total'] = Get-MapScalar -Record $node -Field 'total'
                    $nodeAttributes['cte_key'] = Get-NestedMapScalar -Record $node -ObjectField 'cte' -Field 'key'
                    $nodeAttributes['accounting_credit_id'] = Get-MapScalar -Record $node -Field 'accountingCreditId'
                    $nodeAttributes['accounting_credit_installment_id'] = Get-MapScalar -Record $node -Field 'accountingCreditInstallmentId'
                }
                $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGE_ID_VALUE' } else { 'GRAPHQL_FREIGHT_EDGE_ID_VALUE' }
                $nodeId = ConvertTo-Scalar -Value $node['id']
                $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGE_NATURAL_KEY_VALUE' } else { 'GRAPHQL_FREIGHT_EDGE_NATURAL_KEY_VALUE' }
                $nodeNaturalKey = ConvertTo-Scalar -Value $node[$naturalField]
                if ($null -eq $nodeId -or $null -eq $nodeNaturalKey) {
                    $index.IdentityValid = $false
                    $invalidEdgeCount++
                    continue
                }
                $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_EDGE_ADD_ENTITY' } else { 'GRAPHQL_FREIGHT_EDGE_ADD_ENTITY' }
                Add-Entity -Index $index -Id $nodeId -NaturalKey $nodeNaturalKey -Attributes $nodeAttributes
                if ($Kind -eq 'PICK') {
                    $script:processingStage = 'GRAPHQL_PICK_ITEMS'
                    if (-not ($node -is [System.Collections.IDictionary]) -or -not $node.Contains('pickItems') -or -not ($node['pickItems'] -is [System.Collections.IEnumerable])) { $pickItemMappingComplete = $false; continue }
                    $itemCount = 0
                    foreach ($item in @($node['pickItems'])) {
                        $itemId = Get-MapScalar -Record $item -Field 'id'; $sequence = Get-MapScalar -Record $node -Field 'sequenceCode'
                        if ($null -eq $itemId -or $null -eq $sequence) { $pickItemMappingComplete = $false; continue }
                        $itemCount++
                        if ($pickItemToSequence.ContainsKey($itemId) -and $pickItemToSequence[$itemId] -ne $sequence) { $pickItemMappingComplete = $false; $pickItemMappingConflictCount++ }
                        else { $pickItemToSequence[$itemId] = $sequence }
                    }
                    if ($itemCount -eq 0) { $pickItemMappingComplete = $false }
                }
            }
        } catch {
            $processingFailureStage = $script:processingStage
            Stop-Probe -State $State -Reason 'GRAPHQL_EDGE_PROCESSING_FAILURE'
            break
        }
        if (-not $index.IdentityValid) { Stop-Probe -State $State -Reason 'GRAPHQL_INVALID_ENTITY_IDENTITY'; break }
        $script:processingStage = if ($Kind -eq 'PICK') { 'GRAPHQL_PICK_PAGE_INFO' } else { 'GRAPHQL_FREIGHT_PAGE_INFO' }
        $pageInfo = $connection['pageInfo']
        if (-not ($pageInfo['hasNextPage'] -is [bool])) { Stop-Probe -State $State -Reason 'GRAPHQL_INVALID_PAGE_INFO'; break }
        if (-not $pageInfo['hasNextPage']) { $terminal = $true; break }
        $after = Get-MapScalar -Record $pageInfo -Field 'endCursor'
        if ($null -eq $after -or -not $seenCursors.Add($after)) { Stop-Probe -State $State -Reason 'GRAPHQL_INVALID_CURSOR'; break }
    }
    if (-not $State['stopped'] -and -not $terminal) { Stop-Probe -State $State -Reason 'GRAPHQL_PAGE_LIMIT_REACHED' }
    return [pscustomobject]@{ Index = $index; Pages = $pages; Terminal = $terminal; Completed = ($terminal -and $index.IdentityValid -and -not $State['stopped']); PickItemToSequence = $pickItemToSequence; PickItemMappingComplete = $pickItemMappingComplete; PickItemMappingConflictCount = $pickItemMappingConflictCount; EdgeCount = $edgeCount; InvalidEdgeCount = $invalidEdgeCount; ProcessingFailureStage = $processingFailureStage }
}

function Normalize-Cte {
    param([string]$Value)
    if ($null -eq $Value) { return $null }
    $normalized = $Value -replace '[^0-9]', ''
    return $(if ($normalized -match '^[0-9]{44}$') { $normalized } else { $null })
}

function Get-NormalizedCteEvidence {
    param($Traversal, [string]$Attribute)
    $set = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $complete = $Traversal.Completed -and $Traversal.Index.AttributeComplete[$Attribute] -and $Traversal.Index.EntityById.Count -gt 0
    if ($complete) {
        foreach ($id in $Traversal.Index.EntityById.Keys) {
            $normalized = Normalize-Cte -Value (Get-EntityAttribute -Index $Traversal.Index -Id $id -Name $Attribute)
            if ($null -eq $normalized) { $complete = $false; break }; [void]$set.Add($normalized)
        }
    }
    return [pscustomobject]@{ Complete = $complete; Values = $set }
}

function Get-FreteColetaEvidence {
    param($DataColetas, $DataFretes, $GraphQlColetas, $GraphQlFretes)
    $candidateComplete = $DataFretes.Completed -and $DataFretes.Index.AttributeComplete['fit_p_m_pck_sequence_code'] -and $DataFretes.Index.EntityById.Count -gt 0
    $candidateValues = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if ($candidateComplete) { foreach ($id in $DataFretes.Index.EntityById.Keys) { [void]$candidateValues.Add((Get-EntityAttribute -Index $DataFretes.Index -Id $id -Name 'fit_p_m_pck_sequence_code')) } }
    $candidateResolves = $candidateComplete -and $DataColetas.Completed -and $candidateValues.Count -gt 0
    if ($candidateResolves) { foreach ($value in $candidateValues) { if (-not $DataColetas.Index.IdByNaturalKey.ContainsKey($value)) { $candidateResolves = $false; break } } }
    $baselineComplete = $GraphQlFretes.Completed -and $GraphQlFretes.Index.AttributeComplete['pick_item_id'] -and $GraphQlColetas.Completed -and $GraphQlColetas.PickItemMappingComplete -and $GraphQlFretes.Index.EntityById.Count -gt 0
    $baselineResolves = $baselineComplete
    if ($baselineResolves) {
        foreach ($id in $GraphQlFretes.Index.EntityById.Keys) {
            $pickItemId = Get-EntityAttribute -Index $GraphQlFretes.Index -Id $id -Name 'pick_item_id'
            if ($null -eq $pickItemId -or -not $GraphQlColetas.PickItemToSequence.ContainsKey($pickItemId)) {
                $baselineResolves = $false
                break
            }
        }
    }
    $identity = Get-IdentityEvidence -Left $DataFretes.Index -Right $GraphQlFretes.Index -Completed ($DataFretes.Completed -and $GraphQlFretes.Completed)
    $candidateMatchesBaseline = $candidateResolves -and $baselineResolves -and $identity.canonical_ids_equal
    if ($candidateMatchesBaseline) {
        foreach ($id in $GraphQlFretes.Index.EntityById.Keys) {
            $pickItemId = Get-EntityAttribute -Index $GraphQlFretes.Index -Id $id -Name 'pick_item_id'
            if ($null -eq $pickItemId -or -not $GraphQlColetas.PickItemToSequence.ContainsKey($pickItemId) -or
                (Get-EntityAttribute -Index $DataFretes.Index -Id $id -Name 'fit_p_m_pck_sequence_code') -ne $GraphQlColetas.PickItemToSequence[$pickItemId]) {
                $candidateMatchesBaseline = $false
                break
            }
        }
    }
    return [ordered]@{ candidate_relation_count = $candidateValues.Count; candidate_keys_complete = $candidateComplete; candidate_keys_resolve_to_coletas = $candidateResolves; graphql_pick_item_baseline_complete = $baselineComplete; graphql_pick_item_baseline_resolves = $baselineResolves; candidate_matches_graphql_pick_baseline = $candidateMatchesBaseline; relation_confirmed = $false }
}

function ConvertTo-StrictDecimal {
    param([string]$Value)
    $parsed = [decimal]0
    $styles = [Globalization.NumberStyles]::AllowLeadingSign -bor [Globalization.NumberStyles]::AllowDecimalPoint
    if ($null -eq $Value -or -not [decimal]::TryParse($Value, $styles, [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) { return $null }
    return $parsed
}

function Get-RevenueEvidence {
    param($DataFretes, $GraphQlFretes, $GraphQlColetas, $Relation)
    $result = [ordered]@{ comparable = $false; revenue_group_count = 0; group_sets_equal = $false; group_values_equal = $false }
    if (-not $Relation.candidate_matches_graphql_pick_baseline -or -not $DataFretes.Index.AttributeComplete['total'] -or -not $GraphQlFretes.Index.AttributeComplete['total']) { return $result }
    $dataGroups = [Collections.Generic.Dictionary[string, decimal]]::new([StringComparer]::Ordinal)
    $graphGroups = [Collections.Generic.Dictionary[string, decimal]]::new([StringComparer]::Ordinal)
    foreach ($id in $GraphQlFretes.Index.EntityById.Keys) {
        if (-not $DataFretes.Index.EntityById.ContainsKey($id)) { return $result }
        $pickItemId = Get-EntityAttribute -Index $GraphQlFretes.Index -Id $id -Name 'pick_item_id'
        if ($null -eq $pickItemId -or -not $GraphQlColetas.PickItemToSequence.ContainsKey($pickItemId)) { return $result }
        $group = $GraphQlColetas.PickItemToSequence[$pickItemId]
        $dataAmount = ConvertTo-StrictDecimal -Value (Get-EntityAttribute -Index $DataFretes.Index -Id $id -Name 'total')
        $graphAmount = ConvertTo-StrictDecimal -Value (Get-EntityAttribute -Index $GraphQlFretes.Index -Id $id -Name 'total')
        if ($null -eq $group -or $null -eq $dataAmount -or $null -eq $graphAmount) { return $result }
        if ($dataGroups.ContainsKey($group)) { $dataGroups[$group] += $dataAmount } else { $dataGroups[$group] = $dataAmount }
        if ($graphGroups.ContainsKey($group)) { $graphGroups[$group] += $graphAmount } else { $graphGroups[$group] = $graphAmount }
    }
    $setsEqual = Test-StringSetsEqual -Left $dataGroups.Keys -Right $graphGroups.Keys
    $valuesEqual = $setsEqual
    if ($setsEqual) { foreach ($key in $dataGroups.Keys) { if ($dataGroups[$key] -ne $graphGroups[$key]) { $valuesEqual = $false; break } } }
    return [ordered]@{ comparable = $true; revenue_group_count = $dataGroups.Count; group_sets_equal = $setsEqual; group_values_equal = $valuesEqual }
}

function Get-CteInvoiceEvidence {
    param($DataFretes, $Invoices, $GraphQlFretes)
    $freteCtes = Get-NormalizedCteEvidence -Traversal $DataFretes -Attribute 'fit_fhe_cte_key'
    $invoiceCtes = Get-NormalizedCteEvidence -Traversal $Invoices -Attribute 'fit_fhe_cte_key'
    $graphCtes = Get-NormalizedCteEvidence -Traversal $GraphQlFretes -Attribute 'cte_key'
    $freteGraphEqual = $freteCtes.Complete -and $graphCtes.Complete -and (Test-StringSetsEqual -Left $freteCtes.Values -Right $graphCtes.Values)
    $invoiceCovers = $invoiceCtes.Complete -and $freteCtes.Complete
    if ($invoiceCovers) { foreach ($cte in $freteCtes.Values) { if (-not $invoiceCtes.Values.Contains($cte)) { $invoiceCovers = $false; break } } }
    return [ordered]@{ data_export_frete_ctes_complete = $freteCtes.Complete; invoice_ctes_complete = $invoiceCtes.Complete; graphql_frete_ctes_complete = $graphCtes.Complete; data_export_frete_ctes_match_graphql = $freteGraphEqual; invoice_ctes_cover_data_export_fretes = $invoiceCovers; relation_confirmed = ($freteGraphEqual -and $invoiceCovers) }
}

function Get-InvoiceIdCandidateEvidence {
    param($DataFretes, $Invoices)
    $complete = $DataFretes.Completed -and $Invoices.Completed
    $overlap = 0; $invoiceSubset = $complete
    if ($complete) { foreach ($id in $Invoices.Index.EntityById.Keys) { if ($DataFretes.Index.EntityById.ContainsKey($id)) { $overlap++ } else { $invoiceSubset = $false } } }
    return [ordered]@{ comparison_completed = $complete; invoice_entity_count = $Invoices.Index.EntityById.Count; overlapping_id_count = $overlap; every_invoice_id_is_a_frete_id = $invoiceSubset; id_equivalence_confirmed = $false }
}

function Get-AccountingClassification {
    param($DataFretes, $GraphQlFretes, [string]$Attribute)
    if (-not $DataFretes.Index.AttributeComplete[$Attribute]) { return 'ABSENT' }
    $identity = Get-IdentityEvidence -Left $DataFretes.Index -Right $GraphQlFretes.Index -Completed ($DataFretes.Completed -and $GraphQlFretes.Completed)
    if (-not $GraphQlFretes.Index.AttributeComplete[$Attribute] -or -not $identity.canonical_ids_equal) { return 'EXACT_SCALAR_OBSERVED_UNVERIFIED' }
    foreach ($id in $DataFretes.Index.EntityById.Keys) { if ((Get-EntityAttribute -Index $DataFretes.Index -Id $id -Name $Attribute) -ne (Get-EntityAttribute -Index $GraphQlFretes.Index -Id $id -Name $Attribute)) { return 'EXACT_SCALAR_OBSERVED_UNVERIFIED' } }
    return 'EXACT_SCALAR_MATCHED_UNAPPROVED'
}

function Get-TraversalSummary {
    param($Traversal, [bool]$OrderingClaimed = $true)
    return [ordered]@{ pages_executed = $Traversal.Pages.Count; terminal_observed = $Traversal.Terminal; completed = $Traversal.Completed; ordering_claimed = $OrderingClaimed; physical_record_count = $Traversal.Index.PhysicalRecordCount; entity_count = $Traversal.Index.EntityById.Count; identity_valid = $Traversal.Index.IdentityValid; duplicate_physical_row_count = $Traversal.Index.DuplicatePhysicalRowCount; inconsistent_entity_count = $Traversal.Index.InconsistentEntityCount }
}

function Invoke-LocalSelfTest {
    $cte = '35123456789012345678901234567890123456789012'
    if ($null -eq (Normalize-Cte -Value $cte) -or $null -ne (Normalize-Cte -Value 'invalid')) { throw 'Falha do normalizador sintético.' }
    $index = New-EntityIndex -AttributeNames @('total')
    Add-Entity -Index $index -Id 'synthetic-freight' -NaturalKey 'synthetic-sequence' -Attributes @{ total = '1.00' }
    Add-Entity -Index $index -Id 'synthetic-freight' -NaturalKey 'synthetic-sequence' -Attributes @{ total = '1.00' }
    if (-not $index.IdentityValid -or $index.EntityById.Count -ne 1 -or $index.DuplicatePhysicalRowCount -ne 1) { throw 'Falha do índice sintético.' }
    $nullIdentityIndex = New-EntityIndex -AttributeNames @()
    Add-Entity -Index $nullIdentityIndex -Id $null -NaturalKey $null -Attributes @{}
    if ($nullIdentityIndex.IdentityValid -or $nullIdentityIndex.EntityById.Count -ne 0) { throw 'Falha ao recusar identidade nula.' }
    $emptyAttributeIndex = New-EntityIndex -AttributeNames @()
    if ($emptyAttributeIndex.AttributeNames.Count -ne 0) { throw 'Falha ao normalizar atributos vazios.' }
    $terminal = Get-DataExportRecords -Json (ConvertFrom-Json -InputObject '[]' -AsHashtable -NoEnumerate -DateKind String)
    if (-not $terminal.Valid -or $terminal.Records.Count -ne 0) { throw 'Falha ao reconhecer a página terminal sintética.' }
    $missingPickItemIndex = New-EntityIndex -AttributeNames @('pick_item_id')
    Add-Entity -Index $missingPickItemIndex -Id 'synthetic-missing-item' -NaturalKey 'synthetic-missing-item-key' -Attributes @{ pick_item_id = $null }
    $missingPickItemIndex.AttributeComplete['pick_item_id'] = $true
    $emptyIndex = New-EntityIndex -AttributeNames @()
    Add-Entity -Index $emptyIndex -Id 'synthetic-pick' -NaturalKey 'synthetic-pick-key' -Attributes @{}
    $emptyPickItemMap = [Collections.Generic.Dictionary[string, string]]::new([StringComparer]::Ordinal)
    $missingPickItemEvidence = Get-FreteColetaEvidence `
        -DataColetas ([pscustomobject]@{ Completed = $true; Index = $emptyIndex }) `
        -DataFretes ([pscustomobject]@{ Completed = $false; Index = $emptyIndex }) `
        -GraphQlColetas ([pscustomobject]@{ Completed = $true; Index = $emptyIndex; PickItemToSequence = $emptyPickItemMap; PickItemMappingComplete = $true }) `
        -GraphQlFretes ([pscustomobject]@{ Completed = $true; Index = $missingPickItemIndex })
    if ($missingPickItemEvidence.graphql_pick_item_baseline_resolves) { throw 'Falha ao recusar item GraphQL ausente.' }
    $missingRevenueIndex = New-EntityIndex -AttributeNames @('pick_item_id', 'total')
    Add-Entity -Index $missingRevenueIndex -Id 'synthetic-revenue' -NaturalKey 'synthetic-revenue-key' -Attributes @{ pick_item_id = $null; total = '1.00' }
    $missingRevenueIndex.AttributeComplete['pick_item_id'] = $true
    $missingRevenueEvidence = Get-RevenueEvidence `
        -DataFretes ([pscustomobject]@{ Completed = $true; Index = $missingRevenueIndex }) `
        -GraphQlFretes ([pscustomobject]@{ Completed = $true; Index = $missingRevenueIndex }) `
        -GraphQlColetas ([pscustomobject]@{ PickItemToSequence = $emptyPickItemMap }) `
        -Relation ([ordered]@{ candidate_matches_graphql_pick_baseline = $true })
    if ($missingRevenueEvidence.comparable) { throw 'Falha ao recusar grupo de receita sem item.' }
    $script:processingStage = 'SELFTEST_GRAPHQL_EDGE_SHAPE'
    $fixturePick = ConvertFrom-Json -InputObject (Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '..\..\src\test\resources\contracts\graphql\picks-first.json')) -AsHashtable -NoEnumerate -DateKind String
    $fixtureEdges = Get-GraphQlEdges -Connection $fixturePick['data']['pick']
    $invalidEdges = Get-GraphQlEdges -Connection ([ordered]@{ edges = [ordered]@{} })
    if (-not $fixtureEdges.Valid -or $fixtureEdges.Edges.Count -ne 1 -or $invalidEdges.Valid) { throw 'Falha da forma sintética de edges GraphQL.' }
    $script:processingStage = 'SELFTEST_GRAPHQL_NODE_IDENTITY'
    $fixtureNode = $fixtureEdges.Edges[0]['node']
    $fixtureNodeIndex = New-EntityIndex -AttributeNames @()
    $fixtureNodeId = ConvertTo-Scalar -Value $fixtureNode['id']
    $fixtureNodeNaturalKey = ConvertTo-Scalar -Value $fixtureNode['sequenceCode']
    Add-Entity -Index $fixtureNodeIndex -Id $fixtureNodeId -NaturalKey $fixtureNodeNaturalKey -Attributes @{}
    $nonMapNodeIndex = New-EntityIndex -AttributeNames @()
    $nonMapNode = [object]::new()
    Add-Entity -Index $nonMapNodeIndex -Id (Get-MapScalar -Record $nonMapNode -Field 'id') -NaturalKey (Get-MapScalar -Record $nonMapNode -Field 'sequenceCode') -Attributes @{}
    if (-not $fixtureNodeIndex.IdentityValid -or $fixtureNodeIndex.EntityById.Count -ne 1 -or $nonMapNodeIndex.IdentityValid) { throw 'Falha na identidade sintética de nó GraphQL.' }
    $payload = New-GraphQlPayload -Kind FREIGHT -Date ([datetime]'2026-01-02') -After $null
    if (-not $payload.Contains('query') -or $payload.Contains('mutation')) { throw 'Falha da query estática sintética.' }
    [ordered]@{ self_test = $true; network_calls = 0; cte_normalizer_passed = $true; entity_index_passed = $true; null_identity_rejected = $true; empty_attribute_index_passed = $true; terminal_page_passed = $true; missing_pick_item_passed = $true; missing_revenue_group_passed = $true; graphql_edges_shape_passed = $true; graphql_node_identity_passed = $true; static_query_passed = $true; passed = $true } | ConvertTo-Json -Compress
}

if ($SelfTest) { Invoke-LocalSelfTest; exit 0 }

$probeGate = Enter-DataExportProbeGate
if ($null -eq $probeGate) {
    [ordered]@{ transport = [ordered]@{ data_export = 'GET_QUERY'; graphql = 'POST_QUERY' }; calls_attempted = 0; stopped = $true; stop_reason = 'LOCAL_CONCURRENT_PROBE' } | ConvertTo-Json -Compress
    exit 1
}
$probeExitCode = 0
try {
$date = ConvertTo-ClosedDate -Value $ClosedDate
$envPath = Get-LegacyEnvPath
$baseUri = ConvertTo-SafeHttpsUri -Value (Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_BASE_URL') -Name 'API_BASE_URL'
$dataExportToken = Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_DATAEXPORT_TOKEN'
$graphQlUri = Resolve-GraphQlUri -BaseUri $baseUri -Endpoint (Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_GRAPHQL_ENDPOINT')
$graphQlToken = Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_GRAPHQL_TOKEN'
$state = [ordered]@{ calls_attempted = 0; stopped = $false; stop_reason = $null; last_http_status = $null }
$coletaDefinition = [pscustomobject]@{ TemplateId = 6908; BusinessFilter = 'picks.request_date'; NaturalKey = 'sequence_code'; OrderBy = 'sequence_code asc'; Attributes = @() }
$freteDefinition = [pscustomobject]@{ TemplateId = 6389; BusinessFilter = 'freights.service_at'; NaturalKey = 'corporation_sequence_number'; OrderBy = 'corporation_sequence_number asc'; Attributes = @('fit_p_m_pck_sequence_code', 'total', 'fit_fhe_cte_key', 'accounting_credit_id', 'accounting_credit_installment_id') }
$invoiceDefinition = [pscustomobject]@{ TemplateId = 4924; BusinessFilter = 'freights.service_at'; NaturalKey = 'id'; OrderBy = $null; Attributes = @('fit_fhe_cte_key', 'fit_ant_value') }
$processingStage = 'DATA_EXPORT_COLETAS'
$dataColetas = Invoke-DataExportTraversal -Definition $coletaDefinition -Date $date -BaseUri $baseUri -Token $dataExportToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds
$processingStage = 'DATA_EXPORT_FRETES'
$dataFretes = if ($state['stopped']) { $null } else { Invoke-DataExportTraversal -Definition $freteDefinition -Date $date -BaseUri $baseUri -Token $dataExportToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds }
$processingStage = 'DATA_EXPORT_FATURAS'
$invoices = if ($state['stopped']) { $null } else { Invoke-DataExportTraversal -Definition $invoiceDefinition -Date $date -BaseUri $baseUri -Token $dataExportToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds }
$processingStage = 'GRAPHQL_COLETAS_AUDIT'
$graphQlColetas = if ($state['stopped']) { $null } else { Invoke-GraphQlTraversal -Kind PICK -Date $date -Uri $graphQlUri -Token $graphQlToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds }
$processingStage = 'GRAPHQL_FRETES_AUDIT'
$graphQlFretes = if ($state['stopped']) { $null } else { Invoke-GraphQlTraversal -Kind FREIGHT -Date $date -Uri $graphQlUri -Token $graphQlToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds }

$processingStage = 'IDENTITY_EVIDENCE'
$coletaIdentity = if ($null -eq $graphQlColetas) { [ordered]@{ completed = $false; entity_counts_equal = $false; natural_key_sets_equal = $false; canonical_ids_equal = $false } } else { Get-IdentityEvidence -Left $dataColetas.Index -Right $graphQlColetas.Index -Completed ($dataColetas.Completed -and $graphQlColetas.Completed) }
$freteIdentity = if ($null -eq $dataFretes -or $null -eq $graphQlFretes) { [ordered]@{ completed = $false; entity_counts_equal = $false; natural_key_sets_equal = $false; canonical_ids_equal = $false } } else { Get-IdentityEvidence -Left $dataFretes.Index -Right $graphQlFretes.Index -Completed ($dataFretes.Completed -and $graphQlFretes.Completed) }
$processingStage = 'RELATION_EVIDENCE'
$relation = if ($null -eq $dataFretes -or $null -eq $invoices -or $null -eq $graphQlColetas -or $null -eq $graphQlFretes) { [ordered]@{ candidate_relation_count = 0; candidate_keys_complete = $false; candidate_keys_resolve_to_coletas = $false; graphql_pick_item_baseline_complete = $false; graphql_pick_item_baseline_resolves = $false; candidate_matches_graphql_pick_baseline = $false; relation_confirmed = $false } } else { Get-FreteColetaEvidence -DataColetas $dataColetas -DataFretes $dataFretes -GraphQlColetas $graphQlColetas -GraphQlFretes $graphQlFretes }
$processingStage = 'REVENUE_EVIDENCE'
$revenue = if ($null -eq $dataFretes -or $null -eq $graphQlFretes -or $null -eq $graphQlColetas) { [ordered]@{ comparable = $false; revenue_group_count = 0; group_sets_equal = $false; group_values_equal = $false } } else { Get-RevenueEvidence -DataFretes $dataFretes -GraphQlFretes $graphQlFretes -GraphQlColetas $graphQlColetas -Relation $relation }
$processingStage = 'CTE_EVIDENCE'
$cte = if ($null -eq $dataFretes -or $null -eq $invoices -or $null -eq $graphQlFretes) { [ordered]@{ data_export_frete_ctes_complete = $false; invoice_ctes_complete = $false; graphql_frete_ctes_complete = $false; data_export_frete_ctes_match_graphql = $false; invoice_ctes_cover_data_export_fretes = $false; relation_confirmed = $false } } else { Get-CteInvoiceEvidence -DataFretes $dataFretes -Invoices $invoices -GraphQlFretes $graphQlFretes }
$processingStage = 'INVOICE_ID_EVIDENCE'
$invoiceIds = if ($null -eq $dataFretes -or $null -eq $invoices) { [ordered]@{ comparison_completed = $false; invoice_entity_count = 0; overlapping_id_count = 0; every_invoice_id_is_a_frete_id = $false; id_equivalence_confirmed = $false } } else { Get-InvoiceIdCandidateEvidence -DataFretes $dataFretes -Invoices $invoices }
$processingStage = 'ACCOUNTING_CLASSIFICATION'
$accountCredit = if ($null -eq $dataFretes -or $null -eq $graphQlFretes) { 'ABSENT' } else { Get-AccountingClassification -DataFretes $dataFretes -GraphQlFretes $graphQlFretes -Attribute 'accounting_credit_id' }
$accountInstallment = if ($null -eq $dataFretes -or $null -eq $graphQlFretes) { 'ABSENT' } else { Get-AccountingClassification -DataFretes $dataFretes -GraphQlFretes $graphQlFretes -Attribute 'accounting_credit_installment_id' }

$processingStage = 'SANITIZED_SUMMARY'
[ordered]@{
    transport = [ordered]@{ data_export = 'GET_QUERY'; graphql = 'POST_QUERY' }
    calls_attempted = $state['calls_attempted']; call_budget = $MaxCalls
    last_http_status = $state['last_http_status']; processing_stage = $processingStage
    data_export = [ordered]@{
        coletas_6908 = Get-TraversalSummary -Traversal $dataColetas
        fretes_6389 = if ($null -eq $dataFretes) { $null } else { Get-TraversalSummary -Traversal $dataFretes }
        faturas_4924 = if ($null -eq $invoices) { $null } else { Get-TraversalSummary -Traversal $invoices -OrderingClaimed $false }
    }
    graphql = [ordered]@{
        coletas = if ($null -eq $graphQlColetas) { $null } else { [ordered]@{ pages_executed = $graphQlColetas.Pages; terminal_observed = $graphQlColetas.Terminal; completed = $graphQlColetas.Completed; entity_count = $graphQlColetas.Index.EntityById.Count; identity_valid = $graphQlColetas.Index.IdentityValid; edge_count = $graphQlColetas.EdgeCount; invalid_edge_count = $graphQlColetas.InvalidEdgeCount; processing_failure_stage = $graphQlColetas.ProcessingFailureStage; pick_item_mapping_complete = $graphQlColetas.PickItemMappingComplete; pick_item_mapping_conflict_count = $graphQlColetas.PickItemMappingConflictCount } }
        fretes = if ($null -eq $graphQlFretes) { $null } else { [ordered]@{ pages_executed = $graphQlFretes.Pages; terminal_observed = $graphQlFretes.Terminal; completed = $graphQlFretes.Completed; entity_count = $graphQlFretes.Index.EntityById.Count; identity_valid = $graphQlFretes.Index.IdentityValid; edge_count = $graphQlFretes.EdgeCount; invalid_edge_count = $graphQlFretes.InvalidEdgeCount; processing_failure_stage = $graphQlFretes.ProcessingFailureStage } }
    }
    identity = [ordered]@{ coletas = $coletaIdentity; fretes = $freteIdentity }
    frete_coleta = $relation; frete_coleta_revenue = $revenue
    cte_invoice_4924 = $cte; invoice_id_candidate = $invoiceIds
    financial_fields = [ordered]@{ accounting_credit_id = $accountCredit; accounting_credit_installment_id = $accountInstallment; equivalence_confirmed = $false }
    stopped = $state['stopped']; stop_reason = $state['stop_reason']
} | ConvertTo-Json -Depth 12
$probeExitCode = if ($state['stopped']) { 1 } else { 0 }
} finally {
    Exit-DataExportProbeGate -Gate $probeGate
}
exit $probeExitCode

#requires -Version 7.5
<#
.SYNOPSIS
Compara em memoria a identidade Data Export e GraphQL de uma janela fechada.

.DESCRIPTION
Usa somente curl.exe, GET_WITH_QUERY para 6908/6389 e POST GraphQL com
documentos query estaticos. Executa duas travessias Data Export com tamanhos de
pagina positivos e distintos, cada uma ate a pagina vazia, e as compara ao mesmo
conjunto GraphQL terminal. Carrega a ultima definicao nao vazia do .env legado,
nao grava corpos, URL, token, cursor ou identificador e imprime somente resumo
sanitizado de contagens e flags de equivalencia.

.EXAMPLE
pwsh -NoProfile -File .\scripts\probes\Invoke-DataExportGraphQlIdentityProbe.ps1 `
  -TemplateId 6908 -WindowStart 2026-01-01 -WindowEnd 2026-01-01 `
  -PageSizeA 5 -PageSizeB 10 -MaxCalls 10
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(6908, 6389)]
    [int]$TemplateId,

    [Parameter(Mandatory)]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$WindowStart,

    [Parameter(Mandatory)]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$WindowEnd,

    [Parameter(Mandatory)]
    [ValidateRange(1, 100)]
    [int]$PageSizeA,

    [Parameter(Mandatory)]
    [ValidateRange(1, 100)]
    [int]$PageSizeB,

    [ValidateRange(1, 9)]
    [int]$MaxDataExportPages = 9,

    [ValidateRange(1, 9)]
    [int]$MaxGraphQlPages = 9,

    [ValidateRange(0, 10)]
    [int]$InterCallDelaySeconds = 2,

    [ValidateRange(1, 10)]
    [int]$MaxCalls = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PageSizeA -eq $PageSizeB) {
    throw 'Os dois tamanhos de pagina Data Export devem ser positivos e distintos.'
}

$MaxResponseBytes = 10MB
$CurlTimeoutSeconds = 30
$CurlConnectTimeoutSeconds = 10
$CaptureOverheadBytes = 512
$MaximumWindowDays = 7
$GraphQlFirst = 100

function ConvertTo-ClosedDate {
    param([string]$Value)

    $parsed = [datetime]::MinValue
    if (-not [datetime]::TryParseExact(
            $Value,
            'yyyy-MM-dd',
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::None,
            [ref]$parsed)) {
        throw 'A data informada nao e uma data fechada valida.'
    }
    return $parsed.Date
}

function Assert-ClosedWindow {
    param([datetime]$Start, [datetime]$End)

    if ($End -lt $Start) {
        throw 'O fim da janela nao pode ser anterior ao inicio.'
    }
    if (($End - $Start).TotalDays -gt $MaximumWindowDays) {
        throw 'A janela fechada excede o limite conservador permitido.'
    }
    if ($End -ge (Get-Date).Date) {
        throw 'Uma janela fechada deve terminar antes da data local atual.'
    }
}

function Get-LegacyEnvPath {
    try {
        $v2Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
        $workspaceRoot = Split-Path -Parent $v2Root
        $envPath = Join-Path $workspaceRoot 'etl-extracao-dados\.env'
        if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
            throw 'missing'
        }
        return (Resolve-Path -LiteralPath $envPath).Path
    } catch {
        throw 'O .env legado necessario para a sonda nao esta disponivel.'
    }
}

function Remove-OuterEnvQuotes {
    param([string]$Value)

    $trimmed = $Value.Trim()
    if ($trimmed.Length -ge 2 -and
        (($trimmed.StartsWith('"') -and $trimmed.EndsWith('"')) -or
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
            if (-not [string]::IsNullOrWhiteSpace($candidate)) {
                $value = $candidate
            }
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
    param(
        [System.Uri]$BaseUri,
        [int]$ReportTemplateId,
        [System.Collections.IDictionary]$Query
    )

    $path = '/api/analytics/reports/{0}/data' -f $ReportTemplateId
    return [System.Uri]::new($BaseUri, $path + '?' + (ConvertTo-QueryString -Parameters $Query))
}

function Test-ByteSequenceAt {
    param([byte[]]$Source, [byte[]]$Needle, [int]$Start)

    if ($Start -lt 0 -or ($Start + $Needle.Length) -gt $Source.Length) {
        return $false
    }
    for ($index = 0; $index -lt $Needle.Length; $index++) {
        if ($Source[$Start + $index] -ne $Needle[$index]) {
            return $false
        }
    }
    return $true
}

function Stop-Probe {
    param([System.Collections.IDictionary]$State, [string]$Reason)

    if (-not $State['stopped']) {
        $State['stopped'] = $true
        $State['stop_reason'] = $Reason
    }
}

function Reserve-Call {
    param(
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    if ($State['stopped']) {
        return $false
    }
    if ($State['calls_attempted'] -ge $CallBudget) {
        Stop-Probe -State $State -Reason 'CALL_BUDGET_REACHED'
        return $false
    }
    if ($State['calls_attempted'] -gt 0 -and $DelaySeconds -gt 0) {
        Start-Sleep -Seconds $DelaySeconds
    }
    $State['calls_attempted'] = [int]$State['calls_attempted'] + 1
    return $true
}

function Invoke-CurlJsonInMemory {
    param(
        [ValidateSet('GET', 'POST')]
        [string]$Method,
        [System.Uri]$Uri,
        [string]$Token,
        [AllowNull()]
        [string]$JsonBody,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    if (-not (Reserve-Call -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds)) {
        return [pscustomobject]@{ Executed = $false; HttpStatus = $null; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $false; TransportFailure = $false }
    }

    $marker = '__V2_CONTRACT_STATUS_{0}__' -f ([guid]::NewGuid().ToString('N'))
    $markerBytes = [System.Text.Encoding]::ASCII.GetBytes($marker)
    $writeOut = '{0}%{{http_code}}{0}' -f $marker
    $maximumCapturedBytes = $MaxResponseBytes + $CaptureOverheadBytes
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'curl.exe'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $arguments = @(
        '--disable', '--globoff', '--silent', '--show-error', '--request', $Method,
        '--header', ('Authorization: Bearer {0}' -f $Token),
        '--header', 'Accept: application/json',
        '--connect-timeout', [string]$CurlConnectTimeoutSeconds,
        '--max-time', [string]$CurlTimeoutSeconds,
        '--max-redirs', '0',
        '--max-filesize', [string]$MaxResponseBytes,
        '--output', '-', '--write-out', $writeOut
    )
    if ($Method -eq 'POST') {
        $arguments += @('--header', 'Content-Type: application/json', '--data-binary', $JsonBody)
    }
    $arguments += $Uri.AbsoluteUri
    foreach ($argument in $arguments) {
        [void]$startInfo.ArgumentList.Add([string]$argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    $captured = [System.IO.MemoryStream]::new()
    $stderrTask = $null
    $responseLimitExceeded = $false
    $transportFailure = $false
    $allBytes = $null
    try {
        if (-not $process.Start()) {
            $transportFailure = $true
        } else {
            $stderrTask = $process.StandardError.ReadToEndAsync()
            $buffer = [byte[]]::new(8192)
            while ($true) {
                $read = $process.StandardOutput.BaseStream.Read($buffer, 0, $buffer.Length)
                if ($read -le 0) {
                    break
                }
                if (($captured.Length + $read) -gt $maximumCapturedBytes) {
                    $responseLimitExceeded = $true
                    try { $process.Kill($true) } catch { $process.Kill() }
                    break
                }
                $captured.Write($buffer, 0, $read)
            }
            $process.WaitForExit()
            if ($null -ne $stderrTask) {
                $null = $stderrTask.GetAwaiter().GetResult()
            }
            $allBytes = $captured.ToArray()
        }
    } catch {
        $transportFailure = $true
        if (-not $process.HasExited) {
            try { $process.Kill($true) } catch { $process.Kill() }
        }
    } finally {
        $captured.Dispose()
        $process.Dispose()
        $JsonBody = $null
    }

    if ($responseLimitExceeded) {
        Stop-Probe -State $State -Reason 'RESPONSE_LIMIT_EXCEEDED'
        return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $true; TransportFailure = $false }
    }
    if ($transportFailure -or $null -eq $allBytes) {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'
        return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $false; TransportFailure = $true }
    }

    $endMarkerStart = $allBytes.Length - $markerBytes.Length
    $statusStart = $endMarkerStart - 3
    $startMarkerStart = $statusStart - $markerBytes.Length
    $validMarker = (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $endMarkerStart) -and
        (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $startMarkerStart)
    if (-not $validMarker) {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'
        return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $false; TransportFailure = $true }
    }
    $statusText = [System.Text.Encoding]::ASCII.GetString($allBytes, $statusStart, 3)
    if ($statusText -notmatch '^\d{3}$') {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'
        return [pscustomobject]@{ Executed = $true; HttpStatus = $null; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $false; TransportFailure = $true }
    }
    $status = [int]$statusText
    $bodyLength = $startMarkerStart
    if ($bodyLength -gt $MaxResponseBytes) {
        Stop-Probe -State $State -Reason 'RESPONSE_LIMIT_EXCEEDED'
        return [pscustomobject]@{ Executed = $true; HttpStatus = $status; JsonParsed = $false; Json = $null; ResponseLimitExceeded = $true; TransportFailure = $false }
    }
    $json = $null
    $jsonParsed = $false
    try {
        $bodyText = [System.Text.Encoding]::UTF8.GetString($allBytes, 0, $bodyLength)
        $json = ConvertFrom-Json -InputObject $bodyText -AsHashtable -Depth 100 -NoEnumerate -DateKind String
        $jsonParsed = $true
    } catch {
        $json = $null
    } finally {
        $bodyText = $null
        $allBytes = $null
    }
    if ($status -lt 200 -or $status -gt 299) {
        Stop-Probe -State $State -Reason $(if ($status -eq 429) { 'HTTP_429' } else { 'HTTP_NON_2XX' })
    } elseif (-not $jsonParsed) {
        Stop-Probe -State $State -Reason 'INVALID_JSON'
    }
    return [pscustomobject]@{ Executed = $true; HttpStatus = $status; JsonParsed = $jsonParsed; Json = $json; ResponseLimitExceeded = $false; TransportFailure = $false }
}

function Test-ErrorEnvelope {
    param($Value)
    return $Value -is [System.Collections.IDictionary] -and ($Value.Contains('error') -or $Value.Contains('errors'))
}

function Get-DataExportRecords {
    param($Json)

    $result = [pscustomobject]@{
        Valid = $false
        Records = [System.Collections.Generic.List[object]]::new()
    }
    if (Test-ErrorEnvelope -Value $Json) { return $result }
    $data = $Json
    if ($Json -is [System.Collections.IDictionary] -and $Json.Contains('data')) {
        $data = $Json['data']
    }
    if ($null -eq $data -or (Test-ErrorEnvelope -Value $data)) { return $result }
    if ($data -is [System.Collections.IList] -and -not ($data -is [string])) {
        foreach ($item in $data) {
            if (-not ($item -is [System.Collections.IDictionary])) { return $result }
            $result.Records.Add($item)
        }
    } elseif ($data -is [System.Collections.IDictionary]) {
        if ($data.Count -gt 0) { $result.Records.Add($data) }
    } else {
        return $result
    }
    $result.Valid = $true
    return $result
}

function Get-ComparableScalar {
    param($Value)
    if ($null -eq $Value) { return $null }
    if ($Value -is [string]) {
        $text = $Value.Trim()
        if ([string]::IsNullOrWhiteSpace($text)) {
            return $null
        }
        return $text
    }
    if ($Value -is [sbyte] -or $Value -is [byte] -or $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [single] -or $Value -is [double] -or $Value -is [decimal]) {
        return [System.Convert]::ToString($Value, [System.Globalization.CultureInfo]::InvariantCulture)
    }
    return $null
}

function Get-RecordComparableScalar {
    param($Record, [string]$FieldName)

    if (-not ($Record -is [System.Collections.IDictionary]) -or -not $Record.Contains($FieldName)) {
        return $null
    }
    return Get-ComparableScalar -Value $Record[$FieldName]
}

function New-EntityIndex {
    return [pscustomobject]@{
        Valid = $true
        PhysicalRecordCount = 0
        EntityById = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
        IdByNaturalKey = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
        DuplicatePhysicalRowCount = 0
        InconsistentEntityCount = 0
    }
}

function Add-EntityRecord {
    param($Index, $Record, [string]$IdField, [string]$NaturalKeyField)

    $Index.PhysicalRecordCount++
    $id = Get-RecordComparableScalar -Record $Record -FieldName $IdField
    $naturalKey = Get-RecordComparableScalar -Record $Record -FieldName $NaturalKeyField
    if ($null -eq $id -or $null -eq $naturalKey) {
        $Index.Valid = $false
        return
    }
    if ($Index.EntityById.ContainsKey($id)) {
        if ($Index.EntityById[$id] -ne $naturalKey) {
            $Index.Valid = $false
            $Index.InconsistentEntityCount++
            return
        }
        $Index.DuplicatePhysicalRowCount++
        return
    }
    if ($Index.IdByNaturalKey.ContainsKey($naturalKey)) {
        $Index.Valid = $false
        $Index.InconsistentEntityCount++
        return
    }
    $Index.EntityById[$id] = $naturalKey
    $Index.IdByNaturalKey[$naturalKey] = $id
}

function Test-IndexesEqual {
    param($DataExportIndex, $GraphQlIndex)

    $setsEqual = $DataExportIndex.IdByNaturalKey.Count -eq $GraphQlIndex.IdByNaturalKey.Count
    $idsEqual = $setsEqual
    if ($setsEqual) {
        foreach ($key in $DataExportIndex.IdByNaturalKey.Keys) {
            if (-not $GraphQlIndex.IdByNaturalKey.ContainsKey($key)) {
                $setsEqual = $false
                $idsEqual = $false
                break
            }
            if ($DataExportIndex.IdByNaturalKey[$key] -ne $GraphQlIndex.IdByNaturalKey[$key]) {
                $idsEqual = $false
            }
        }
    }
    return [pscustomobject]@{ NaturalKeySetsEqual = $setsEqual; CanonicalIdsEqual = $idsEqual }
}

function Invoke-DataExportEntityTraversal {
    param(
        [int]$ReportTemplateId,
        [string]$BusinessFilter,
        [string]$NaturalKeyField,
        [string]$OrderBy,
        [datetime]$Start,
        [datetime]$End,
        [int]$PageSize,
        [int]$MaximumPages,
        [System.Uri]$BaseUri,
        [string]$Token,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    $index = New-EntityIndex
    $pages = [System.Collections.Generic.List[object]]::new()
    $pagesExecuted = 0
    $terminalEmptyPageObserved = $false
    $allPagesEntityIdsVerifiable = $true
    $allPagesEntityCountsWithinPageSize = $true
    $range = '{0} - {1}' -f $Start.ToString('yyyy-MM-dd'), $End.ToString('yyyy-MM-dd')

    for ($page = 1; $page -le $MaximumPages -and -not $State['stopped']; $page++) {
        $query = [ordered]@{
            (('search[{0}]' -f $BusinessFilter.Replace('.', ']['))) = $range
            page = [string]$page
            per = [string]$PageSize
            order_by = $OrderBy
        }
        $response = Invoke-CurlJsonInMemory -Method GET -Uri (New-DataExportUri -BaseUri $BaseUri -ReportTemplateId $ReportTemplateId -Query $query) `
            -Token $Token -JsonBody $null -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        if (-not $response.Executed) {
            break
        }
        $pagesExecuted++
        if ($State['stopped'] -or -not $response.JsonParsed -or (Test-ErrorEnvelope -Value $response.Json)) {
            $allPagesEntityIdsVerifiable = $false
            $allPagesEntityCountsWithinPageSize = $false
            if (-not $State['stopped']) { Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_ENVELOPE' }
            break
        }

        $recordResult = Get-DataExportRecords -Json $response.Json
        if (-not $recordResult.Valid) {
            $allPagesEntityIdsVerifiable = $false
            $allPagesEntityCountsWithinPageSize = $false
            Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_PAGE_SHAPE'
            break
        }

        $pageEntityIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        $pageEntityIdsVerifiable = $true
        foreach ($record in $recordResult.Records) {
            $entityId = Get-RecordComparableScalar -Record $record -FieldName 'id'
            if ($null -eq $entityId) {
                $pageEntityIdsVerifiable = $false
            } else {
                [void]$pageEntityIds.Add($entityId)
            }
            Add-EntityRecord -Index $index -Record $record -IdField 'id' -NaturalKeyField $NaturalKeyField
        }

        $pageEntityCount = $pageEntityIds.Count
        $pageEntityCountWithinPageSize = $pageEntityIdsVerifiable -and $pageEntityCount -le $PageSize
        $allPagesEntityIdsVerifiable = $allPagesEntityIdsVerifiable -and $pageEntityIdsVerifiable
        $allPagesEntityCountsWithinPageSize = $allPagesEntityCountsWithinPageSize -and $pageEntityCountWithinPageSize
        $pages.Add([ordered]@{
            page = $page
            physical_record_count = $recordResult.Records.Count
            distinct_entity_count = $pageEntityCount
            entity_ids_verifiable = $pageEntityIdsVerifiable
            entity_count_within_requested_page_size = $pageEntityCountWithinPageSize
            page_is_empty = ($recordResult.Records.Count -eq 0)
        })

        if (-not $pageEntityIdsVerifiable) {
            Stop-Probe -State $State -Reason 'DATA_EXPORT_PAGE_ENTITY_ID_NOT_VERIFIABLE'
        } elseif (-not $pageEntityCountWithinPageSize) {
            Stop-Probe -State $State -Reason 'DATA_EXPORT_PAGE_ENTITY_COUNT_EXCEEDS_REQUESTED_SIZE'
        } elseif (-not $index.Valid) {
            Stop-Probe -State $State -Reason 'DATA_EXPORT_INVALID_ENTITY_IDENTITY'
        }
        if ($State['stopped']) {
            break
        }
        if ($recordResult.Records.Count -eq 0) {
            $terminalEmptyPageObserved = $true
            break
        }
    }

    if (-not $State['stopped'] -and -not $terminalEmptyPageObserved -and $pagesExecuted -ge $MaximumPages) {
        Stop-Probe -State $State -Reason 'DATA_EXPORT_PAGE_LIMIT_REACHED'
    }

    return [pscustomobject]@{
        PageSize = $PageSize
        Pages = $pages
        PagesExecuted = $pagesExecuted
        TerminalEmptyPageObserved = $terminalEmptyPageObserved
        AllPagesEntityIdsVerifiable = [bool]($pagesExecuted -gt 0 -and $allPagesEntityIdsVerifiable)
        AllPagesEntityCountsWithinPageSize = [bool]($pagesExecuted -gt 0 -and $allPagesEntityCountsWithinPageSize)
        Index = $index
    }
}

function Get-DataExportTraversalSanitizedSummary {
    param($Traversal, [int]$RequestedPageSize)

    if ($null -eq $Traversal) {
        return [ordered]@{
            requested_page_size = $RequestedPageSize
            executed = $false
            terminal_empty_page_observed = $false
            pages_executed = 0
            pages = @()
            physical_record_count = 0
            entity_count = 0
            all_pages_entity_ids_verifiable = $false
            all_pages_entity_counts_within_requested_page_size = $false
            identity_valid = $false
            duplicate_physical_row_count = 0
            inconsistent_entity_count = 0
        }
    }

    return [ordered]@{
        requested_page_size = $Traversal.PageSize
        executed = ($Traversal.PagesExecuted -gt 0)
        terminal_empty_page_observed = $Traversal.TerminalEmptyPageObserved
        pages_executed = $Traversal.PagesExecuted
        pages = @($Traversal.Pages)
        physical_record_count = $Traversal.Index.PhysicalRecordCount
        entity_count = $Traversal.Index.EntityById.Count
        all_pages_entity_ids_verifiable = $Traversal.AllPagesEntityIdsVerifiable
        all_pages_entity_counts_within_requested_page_size = $Traversal.AllPagesEntityCountsWithinPageSize
        identity_valid = $Traversal.Index.Valid
        duplicate_physical_row_count = $Traversal.Index.DuplicatePhysicalRowCount
        inconsistent_entity_count = $Traversal.Index.InconsistentEntityCount
    }
}

function Get-CompletedIndexComparison {
    param($LeftIndex, $RightIndex, [bool]$Completed)

    if ($null -eq $LeftIndex -or $null -eq $RightIndex) {
        return [ordered]@{
            comparison_completed = $false
            entity_counts_equal = $false
            natural_key_sets_equal = $false
            canonical_ids_equal = $false
        }
    }
    $comparison = Test-IndexesEqual -DataExportIndex $LeftIndex -GraphQlIndex $RightIndex
    return [ordered]@{
        comparison_completed = $Completed
        entity_counts_equal = [bool]($Completed -and $LeftIndex.EntityById.Count -eq $RightIndex.EntityById.Count)
        natural_key_sets_equal = [bool]($Completed -and $comparison.NaturalKeySetsEqual)
        canonical_ids_equal = [bool]($Completed -and $comparison.CanonicalIdsEqual)
    }
}

function New-GraphQlPayload {
    param(
        [int]$ReportTemplateId,
        [datetime]$Start,
        [datetime]$End,
        [AllowNull()][string]$After
    )

    if ($ReportTemplateId -eq 6908) {
        if ($Start -ne $End) { throw 'Coletas GraphQL exige uma unica data por consulta.' }
        $query = 'query ContractColetasIdentity($params: PickInput!, $after: String, $first: Int!) { pick(params: $params, after: $after, first: $first) { edges { node { id sequenceCode } } pageInfo { hasNextPage endCursor } } }'
        $params = [ordered]@{ requestDate = $Start.ToString('yyyy-MM-dd') }
    } else {
        $query = 'query ContractFretesIdentity($params: FreightInput!, $after: String, $first: Int!) { freight(params: $params, after: $after, first: $first) { edges { node { id corporationSequenceNumber } } pageInfo { hasNextPage endCursor } } }'
        $params = [ordered]@{ serviceAt = ('{0} - {1}' -f $Start.ToString('yyyy-MM-dd'), $End.ToString('yyyy-MM-dd')) }
    }
    return [ordered]@{ query = $query; variables = [ordered]@{ params = $params; after = $After; first = $GraphQlFirst } } | ConvertTo-Json -Compress -Depth 8
}

$startDate = ConvertTo-ClosedDate -Value $WindowStart
$endDate = ConvertTo-ClosedDate -Value $WindowEnd
Assert-ClosedWindow -Start $startDate -End $endDate
$definition = if ($TemplateId -eq 6908) {
    [pscustomobject]@{ BusinessFilter = 'picks.request_date'; NaturalKey = 'sequence_code'; OrderBy = 'sequence_code asc'; GraphQlRoot = 'pick'; GraphQlKey = 'sequenceCode' }
} else {
    [pscustomobject]@{ BusinessFilter = 'freights.service_at'; NaturalKey = 'corporation_sequence_number'; OrderBy = 'corporation_sequence_number asc'; GraphQlRoot = 'freight'; GraphQlKey = 'corporationSequenceNumber' }
}

$envPath = Get-LegacyEnvPath
$baseUri = ConvertTo-SafeHttpsUri -Value (Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_BASE_URL') -Name 'API_BASE_URL'
$dataExportToken = Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_DATAEXPORT_TOKEN'
$graphQlUri = Resolve-GraphQlUri -BaseUri $baseUri -Endpoint (Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_GRAPHQL_ENDPOINT')
$graphQlToken = Get-LastNonEmptyEnvValue -EnvPath $envPath -Name 'API_GRAPHQL_TOKEN'
$state = [ordered]@{ calls_attempted = 0; stopped = $false; stop_reason = $null }
$graphQlIndex = New-EntityIndex
$dataExportTraversalA = Invoke-DataExportEntityTraversal -ReportTemplateId $TemplateId `
    -BusinessFilter $definition.BusinessFilter -NaturalKeyField $definition.NaturalKey -OrderBy $definition.OrderBy `
    -Start $startDate -End $endDate -PageSize $PageSizeA -MaximumPages $MaxDataExportPages `
    -BaseUri $baseUri -Token $dataExportToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds
$dataExportTraversalB = $null
if (-not $state['stopped']) {
    $dataExportTraversalB = Invoke-DataExportEntityTraversal -ReportTemplateId $TemplateId `
        -BusinessFilter $definition.BusinessFilter -NaturalKeyField $definition.NaturalKey -OrderBy $definition.OrderBy `
        -Start $startDate -End $endDate -PageSize $PageSizeB -MaximumPages $MaxDataExportPages `
        -BaseUri $baseUri -Token $dataExportToken -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds
}

$graphQlPages = 0
$graphQlTerminal = $false
$after = $null
$seenCursors = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
for ($page = 1; $page -le $MaxGraphQlPages -and -not $state['stopped']; $page++) {
    $payload = New-GraphQlPayload -ReportTemplateId $TemplateId -Start $startDate -End $endDate -After $after
    $response = Invoke-CurlJsonInMemory -Method POST -Uri $graphQlUri -Token $graphQlToken -JsonBody $payload `
        -State $state -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds
    $payload = $null
    if ($state['stopped'] -or -not $response.JsonParsed -or -not ($response.Json -is [System.Collections.IDictionary]) -or
        $response.Json.Contains('errors') -or -not $response.Json.Contains('data') -or -not ($response.Json['data'] -is [System.Collections.IDictionary]) -or
        -not $response.Json['data'].Contains($definition.GraphQlRoot)) {
        if (-not $state['stopped']) { Stop-Probe -State $state -Reason 'GRAPHQL_INVALID_ENVELOPE' }
        break
    }
    $connection = $response.Json['data'][$definition.GraphQlRoot]
    if (-not ($connection -is [System.Collections.IDictionary]) -or -not $connection.Contains('edges') -or -not $connection.Contains('pageInfo') -or
        -not ($connection['edges'] -is [System.Collections.IEnumerable]) -or -not ($connection['pageInfo'] -is [System.Collections.IDictionary])) {
        Stop-Probe -State $state -Reason 'GRAPHQL_INVALID_PAGE_SHAPE'
        break
    }
    $graphQlPages++
    foreach ($edge in @($connection['edges'])) {
        if (-not ($edge -is [System.Collections.IDictionary]) -or -not $edge.Contains('node')) { $graphQlIndex.Valid = $false; continue }
        Add-EntityRecord -Index $graphQlIndex -Record $edge['node'] -IdField 'id' -NaturalKeyField $definition.GraphQlKey
    }
    if (-not $graphQlIndex.Valid) { Stop-Probe -State $state -Reason 'GRAPHQL_INVALID_ENTITY_IDENTITY'; break }
    $pageInfo = $connection['pageInfo']
    if (-not ($pageInfo['hasNextPage'] -is [bool])) { Stop-Probe -State $state -Reason 'GRAPHQL_INVALID_PAGE_INFO'; break }
    if (-not $pageInfo['hasNextPage']) { $graphQlTerminal = $true; break }
    $after = Get-ComparableScalar -Value $pageInfo['endCursor']
    if ($null -eq $after -or -not $seenCursors.Add($after)) { Stop-Probe -State $state -Reason 'GRAPHQL_INVALID_CURSOR'; break }
}
if (-not $state['stopped'] -and -not $graphQlTerminal) { Stop-Probe -State $state -Reason 'GRAPHQL_PAGE_LIMIT_REACHED' }

$dataExportAComplete = [bool](
    $null -ne $dataExportTraversalA -and $dataExportTraversalA.TerminalEmptyPageObserved -and
    $dataExportTraversalA.AllPagesEntityIdsVerifiable -and
    $dataExportTraversalA.AllPagesEntityCountsWithinPageSize -and $dataExportTraversalA.Index.Valid)
$dataExportBComplete = [bool](
    $null -ne $dataExportTraversalB -and $dataExportTraversalB.TerminalEmptyPageObserved -and
    $dataExportTraversalB.AllPagesEntityIdsVerifiable -and
    $dataExportTraversalB.AllPagesEntityCountsWithinPageSize -and $dataExportTraversalB.Index.Valid)
$graphQlComplete = [bool]($graphQlTerminal -and $graphQlIndex.Valid -and -not $state['stopped'])
$bothDataExportTraversalsComplete = [bool]($dataExportAComplete -and $dataExportBComplete -and -not $state['stopped'])
$fullWindowComparisonCompleted = [bool]($bothDataExportTraversalsComplete -and $graphQlComplete)
$dataExportAIndex = if ($null -eq $dataExportTraversalA) { $null } else { $dataExportTraversalA.Index }
$dataExportBIndex = if ($null -eq $dataExportTraversalB) { $null } else { $dataExportTraversalB.Index }
$firstDataExportToGraphQl = Get-CompletedIndexComparison -LeftIndex $dataExportAIndex -RightIndex $graphQlIndex `
    -Completed ([bool]($dataExportAComplete -and $graphQlComplete))
$secondDataExportToGraphQl = Get-CompletedIndexComparison -LeftIndex $dataExportBIndex -RightIndex $graphQlIndex `
    -Completed ([bool]($dataExportBComplete -and $graphQlComplete))
$dataExportPageSizesComparison = Get-CompletedIndexComparison -LeftIndex $dataExportAIndex -RightIndex $dataExportBIndex `
    -Completed $bothDataExportTraversalsComplete

[ordered]@{
    template = $TemplateId
    transport = [ordered]@{ data_export = 'GET_QUERY'; graphql = 'POST_QUERY' }
    calls_attempted = $state['calls_attempted']
    call_budget = $MaxCalls
    data_export = [ordered]@{
        page_size_a = Get-DataExportTraversalSanitizedSummary -Traversal $dataExportTraversalA -RequestedPageSize $PageSizeA
        page_size_b = Get-DataExportTraversalSanitizedSummary -Traversal $dataExportTraversalB -RequestedPageSize $PageSizeB
        page_size_sets = $dataExportPageSizesComparison
    }
    graphql = [ordered]@{
        terminal_page_observed = $graphQlTerminal
        pages_executed = $graphQlPages
        entity_count = $graphQlIndex.EntityById.Count
        identity_valid = $graphQlIndex.Valid
        duplicate_entity_count = $graphQlIndex.DuplicatePhysicalRowCount
        inconsistent_entity_count = $graphQlIndex.InconsistentEntityCount
    }
    comparison = [ordered]@{
        page_size_a_to_graphql = $firstDataExportToGraphQl
        page_size_b_to_graphql = $secondDataExportToGraphQl
        full_window_comparison_completed = $fullWindowComparisonCompleted
        full_window_identity_parity_confirmed = [bool](
            $fullWindowComparisonCompleted -and
            $firstDataExportToGraphQl.entity_counts_equal -and
            $firstDataExportToGraphQl.natural_key_sets_equal -and
            $firstDataExportToGraphQl.canonical_ids_equal -and
            $secondDataExportToGraphQl.entity_counts_equal -and
            $secondDataExportToGraphQl.natural_key_sets_equal -and
            $secondDataExportToGraphQl.canonical_ids_equal -and
            $dataExportPageSizesComparison.entity_counts_equal -and
            $dataExportPageSizesComparison.natural_key_sets_equal -and
            $dataExportPageSizesComparison.canonical_ids_equal)
    }
    stopped = $state['stopped']
    stop_reason = $state['stop_reason']
} | ConvertTo-Json -Depth 8
if ($state['stopped']) { exit 1 }

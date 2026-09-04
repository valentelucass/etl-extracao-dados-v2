#requires -Version 7.5
<#
.SYNOPSIS
Executa uma sonda Data Export estritamente read-only para os templates 6908 e 6389.

.DESCRIPTION
Usa somente curl.exe, GET com query string e a ultima declaracao nao vazia de
API_BASE_URL/API_DATAEXPORT_TOKEN no .env do projeto legado. A sonda nao grava
payload, cabecalho, URL, token ou dados de negocio: ela imprime ao final apenas
um resumo sanitizado de status, contagens, tipos, nulidade, formatos, nomes
tecnicos de campos e flags de paginacao. Nenhum valor de campo e emitido.

.EXAMPLE
pwsh -NoProfile -File .\scripts\probes\Invoke-DataExportContractProbe.ps1 `
  -WindowStart 2026-08-24 -WindowEnd 2026-08-24 `
  -PageSizeA 2 -PageSizeB 5 -MaxCalls 10

.EXAMPLE
pwsh -NoProfile -File .\scripts\probes\Invoke-DataExportContractProbe.ps1 `
  -WindowStart 2026-08-24 -WindowEnd 2026-08-24 `
  -PageSizeA 2 -PageSizeB 5 -TemplateIds 6908 -BoundedTraversal `
  -TraversalMaxPages 9 -MaxCalls 10
#>
[CmdletBinding()]
param(
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

    [ValidateNotNullOrEmpty()]
    [ValidateSet(6908, 6389)]
    [int[]]$TemplateIds = @(6908, 6389),

    [ValidateSet('DATE', 'STABLE_KEY')]
    [string]$OrderProfile = 'DATE',

    [switch]$BoundedTraversal,

    [ValidateRange(1, 9)]
    [int]$TraversalMaxPages = 4,

    [ValidateRange(0, 10)]
    [int]$InterCallDelaySeconds = 2,

    [ValidateRange(1, 10)]
    [int]$MaxCalls = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$MaxResponseBytes = 10MB
$CurlTimeoutSeconds = 30
$CurlConnectTimeoutSeconds = 10
$CaptureOverheadBytes = 512
$MaximumWindowDays = 7

function ConvertTo-ClosedDate {
    param([string]$Value)

    $parsed = [datetime]::MinValue
    $parsedSuccessfully = [datetime]::TryParseExact(
        $Value,
        'yyyy-MM-dd',
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::None,
        [ref]$parsed)

    if (-not $parsedSuccessfully) {
        throw 'A data informada nao e uma data fechada valida.'
    }

    return $parsed.Date
}

function Assert-ClosedWindow {
    param(
        [datetime]$Start,
        [datetime]$End
    )

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
    if ($trimmed.Length -ge 2) {
        $first = $trimmed[0]
        $last = $trimmed[$trimmed.Length - 1]
        if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
            return $trimmed.Substring(1, $trimmed.Length - 2).Trim()
        }
    }
    return $trimmed
}

function Get-LastNonEmptyEnvValue {
    param(
        [string]$EnvPath,
        [string]$Name
    )

    $lastNonEmpty = $null
    foreach ($sourceLine in [System.IO.File]::ReadLines($EnvPath)) {
        $line = $sourceLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }

        $separator = $line.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }

        $key = $line.Substring(0, $separator).Trim()
        if (-not [string]::Equals($key, $Name, [System.StringComparison]::Ordinal)) {
            continue
        }

        $candidate = Remove-OuterEnvQuotes -Value $line.Substring($separator + 1)
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $lastNonEmpty = $candidate
        }
    }

    if ([string]::IsNullOrWhiteSpace($lastNonEmpty)) {
        throw 'Uma configuracao local obrigatoria nao esta disponivel.'
    }

    return $lastNonEmpty
}

function ConvertTo-SafeBaseUri {
    param([string]$Value)

    $uri = $null
    $isAbsolute = [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri)
    if (-not $isAbsolute -or
        $uri.Scheme -ne 'https' -or
        [string]::IsNullOrWhiteSpace($uri.Host) -or
        -not [string]::IsNullOrWhiteSpace($uri.UserInfo) -or
        -not [string]::IsNullOrWhiteSpace($uri.Query) -or
        -not [string]::IsNullOrWhiteSpace($uri.Fragment)) {
        throw 'A configuracao local do endpoint nao atende aos requisitos de leitura segura.'
    }

    return $uri
}

function ConvertTo-QueryString {
    param([System.Collections.IDictionary]$Parameters)

    $pairs = foreach ($key in $Parameters.Keys) {
        $encodedKey = [System.Uri]::EscapeDataString([string]$key)
        $encodedValue = [System.Uri]::EscapeDataString([string]$Parameters[$key])
        '{0}={1}' -f $encodedKey, $encodedValue
    }
    return $pairs -join '&'
}

function New-DataExportUri {
    param(
        [System.Uri]$BaseUri,
        [int]$TemplateId,
        [ValidateSet('info', 'data')]
        [string]$Resource,
        [System.Collections.IDictionary]$QueryParameters
    )

    if ($TemplateId -notin @(6908, 6389)) {
        throw 'O template solicitado nao pertence a allowlist desta sonda.'
    }

    $builder = [System.UriBuilder]::new($BaseUri)
    $basePath = $builder.Path.TrimEnd('/')
    $builder.Path = '{0}/api/analytics/reports/{1}/{2}' -f $basePath, $TemplateId, $Resource
    $builder.Query = if ($null -eq $QueryParameters) { '' } else { ConvertTo-QueryString -Parameters $QueryParameters }
    return $builder.Uri
}

function Test-IsJsonObject {
    param($Value)
    return $Value -is [System.Collections.IDictionary]
}

function Test-IsJsonArray {
    param($Value)
    return $Value -is [System.Collections.IList] -and -not ($Value -is [string])
}

function Get-JsonType {
    param($Value)

    if ($null -eq $Value) {
        return 'null'
    }
    if ($Value -is [string]) {
        return 'string'
    }
    if ($Value -is [bool]) {
        return 'boolean'
    }
    if ($Value -is [sbyte] -or $Value -is [byte] -or
        $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [System.Numerics.BigInteger]) {
        return 'integer'
    }
    if ($Value -is [single] -or $Value -is [double] -or $Value -is [decimal]) {
        return 'number'
    }
    if (Test-IsJsonArray -Value $Value) {
        return 'array'
    }
    if (Test-IsJsonObject -Value $Value) {
        return 'object'
    }
    return 'other'
}

function Get-ObjectProperty {
    param(
        $Object,
        [string]$Name
    )

    if (Test-IsJsonObject -Value $Object) {
        foreach ($key in $Object.Keys) {
            if ([string]::Equals([string]$key, $Name, [System.StringComparison]::OrdinalIgnoreCase)) {
                return [pscustomobject]@{
                    Found = $true
                    Value = $Object[$key]
                }
            }
        }
    }

    return [pscustomobject]@{
        Found = $false
        Value = $null
    }
}

function Test-ErrorEnvelope {
    param($Value)

    if (-not (Test-IsJsonObject -Value $Value)) {
        return $false
    }

    return (Get-ObjectProperty -Object $Value -Name 'error').Found -or
        (Get-ObjectProperty -Object $Value -Name 'errors').Found
}

function New-ValueTypeCounts {
    return [ordered]@{
        string = 0
        integer = 0
        number = 0
        boolean = 0
        array = 0
        object = 0
        other = 0
    }
}

function New-TemporalFormatCounts {
    return [ordered]@{
        date = 0
        local_date_time = 0
        offset_date_time = 0
        blank = 0
        text = 0
    }
}

function Get-TemporalFormat {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return 'blank'
    }
    if ($Value -match '^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:?\d{2})$') {
        return 'offset_date_time'
    }
    if ($Value -match '^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$') {
        return 'local_date_time'
    }
    if ($Value -match '^\d{4}-\d{2}-\d{2}$') {
        return 'date'
    }
    return 'text'
}

function Add-Count {
    param(
        [System.Collections.IDictionary]$Counts,
        [string]$Name
    )

    if (-not $Counts.Contains($Name)) {
        $Counts[$Name] = 0
    }
    $Counts[$Name] = [int]$Counts[$Name] + 1
}

function ConvertTo-SanitizedTechnicalFieldName {
    param($Value)

    if (-not ($Value -is [string])) {
        return $null
    }

    $candidate = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($candidate) -or
        $candidate.Length -gt 128 -or
        $candidate -notmatch '^[A-Za-z_][A-Za-z0-9_.-]*$') {
        return $null
    }

    return $candidate
}

function New-FieldProfileMap {
    return [ordered]@{}
}

function Get-OrAddFieldProfile {
    param(
        [System.Collections.IDictionary]$Profiles,
        [string]$TechnicalName
    )

    if (-not $Profiles.Contains($TechnicalName)) {
        $Profiles[$TechnicalName] = [ordered]@{
            technical_name = $TechnicalName
            presence_count = 0
            null_count = 0
            json_type_counts = New-ValueTypeCounts
            temporal_format_counts = New-TemporalFormatCounts
        }
    }

    return $Profiles[$TechnicalName]
}

function Add-FieldProfileObservation {
    param(
        [System.Collections.IDictionary]$Profile,
        $Value
    )

    $Profile['presence_count'] = [int]$Profile['presence_count'] + 1
    if ($null -eq $Value) {
        $Profile['null_count'] = [int]$Profile['null_count'] + 1
        return
    }

    $type = Get-JsonType -Value $Value
    Add-Count -Counts $Profile['json_type_counts'] -Name $type
    if ($Value -is [string]) {
        Add-Count -Counts $Profile['temporal_format_counts'] -Name (Get-TemporalFormat -Value $Value)
    }
}

function Get-SanitizedTechnicalFieldNames {
    param([System.Collections.IDictionary]$Profiles)

    return @($Profiles.Keys | Sort-Object)
}

function Get-SanitizedFieldProfiles {
    param([System.Collections.IDictionary]$Profiles)

    $result = @()
    foreach ($technicalName in Get-SanitizedTechnicalFieldNames -Profiles $Profiles) {
        $result += ,$Profiles[$technicalName]
    }
    return $result
}

function Test-ByteSequenceAt {
    param(
        [byte[]]$Source,
        [byte[]]$Needle,
        [int]$Start
    )

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

function Invoke-CurlGetInMemory {
    param(
        [System.Uri]$Uri,
        [string]$Token
    )

    $marker = '__V2_DATAEXPORT_STATUS_{0}__' -f ([guid]::NewGuid().ToString('N'))
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
        '--disable',
        '--globoff',
        '--silent',
        '--show-error',
        '--request', 'GET',
        '--header', ('Authorization: Bearer {0}' -f $Token),
        '--header', 'Accept: application/json',
        '--connect-timeout', [string]$CurlConnectTimeoutSeconds,
        '--max-time', [string]$CurlTimeoutSeconds,
        '--max-redirs', '0',
        '--max-filesize', [string]$MaxResponseBytes,
        '--output', '-',
        '--write-out', $writeOut,
        $Uri.AbsoluteUri
    )
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
                    try {
                        $process.Kill($true)
                    } catch {
                        $process.Kill()
                    }
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
            try {
                $process.Kill($true)
            } catch {
                $process.Kill()
            }
        }
    } finally {
        $captured.Dispose()
        $process.Dispose()
    }

    if ($responseLimitExceeded) {
        return [pscustomobject]@{
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $true
            TransportFailure = $false
        }
    }

    if ($transportFailure -or $null -eq $allBytes) {
        return [pscustomobject]@{
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $false
            TransportFailure = $true
        }
    }

    $endMarkerStart = $allBytes.Length - $markerBytes.Length
    $statusStart = $endMarkerStart - 3
    $startMarkerStart = $statusStart - $markerBytes.Length
    $markerIsPresent = (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $endMarkerStart) -and
        (Test-ByteSequenceAt -Source $allBytes -Needle $markerBytes -Start $startMarkerStart)
    if (-not $markerIsPresent) {
        return [pscustomobject]@{
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $false
            TransportFailure = $true
        }
    }

    $statusText = [System.Text.Encoding]::ASCII.GetString($allBytes, $statusStart, 3)
    if ($statusText -notmatch '^\d{3}$') {
        return [pscustomobject]@{
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $false
            TransportFailure = $true
        }
    }

    $bodyLength = $startMarkerStart
    if ($bodyLength -gt $MaxResponseBytes) {
        return [pscustomobject]@{
            HttpStatus = [int]$statusText
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $true
            TransportFailure = $false
        }
    }

    $bodyText = $null
    $json = $null
    $jsonParsed = $false
    try {
        $bodyText = [System.Text.Encoding]::UTF8.GetString($allBytes, 0, $bodyLength)
        $json = ConvertFrom-Json -InputObject $bodyText -AsHashtable -Depth 100 -NoEnumerate -DateKind String
        $jsonParsed = $true
    } catch {
        $json = $null
        $jsonParsed = $false
    } finally {
        $bodyText = $null
        $allBytes = $null
    }

    return [pscustomobject]@{
        HttpStatus = [int]$statusText
        JsonParsed = $jsonParsed
        Json = $json
        ResponseLimitExceeded = $false
        TransportFailure = $false
    }
}

function Stop-Probe {
    param(
        [System.Collections.IDictionary]$State,
        [string]$Reason
    )

    if (-not $State['stopped']) {
        $State['stopped'] = $true
        $State['stop_reason'] = $Reason
    }
}

function Invoke-ReadOnlyGet {
    param(
        [System.Uri]$Uri,
        [string]$Token,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    if ($State['stopped']) {
        return [pscustomobject]@{
            Executed = $false
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $false
            TransportFailure = $false
        }
    }
    if ($State['calls_attempted'] -ge $CallBudget) {
        Stop-Probe -State $State -Reason 'CALL_BUDGET_REACHED'
        return [pscustomobject]@{
            Executed = $false
            HttpStatus = $null
            JsonParsed = $false
            Json = $null
            ResponseLimitExceeded = $false
            TransportFailure = $false
        }
    }

    if ($State['calls_attempted'] -gt 0 -and $DelaySeconds -gt 0) {
        Start-Sleep -Seconds $DelaySeconds
    }
    $State['calls_attempted'] = [int]$State['calls_attempted'] + 1
    $response = Invoke-CurlGetInMemory -Uri $Uri -Token $Token
    $response | Add-Member -NotePropertyName Executed -NotePropertyValue $true

    if ($response.ResponseLimitExceeded) {
        Stop-Probe -State $State -Reason 'RESPONSE_LIMIT_EXCEEDED'
    } elseif ($response.TransportFailure) {
        Stop-Probe -State $State -Reason 'TRANSPORT_FAILURE'
    } elseif ($null -eq $response.HttpStatus -or $response.HttpStatus -lt 200 -or $response.HttpStatus -gt 299) {
        $reason = if ($response.HttpStatus -eq 429) { 'HTTP_429' } else { 'HTTP_NON_2XX' }
        Stop-Probe -State $State -Reason $reason
    } elseif (-not $response.JsonParsed) {
        Stop-Probe -State $State -Reason 'INVALID_JSON'
    }

    return $response
}

function New-TransportSummary {
    param($Response)

    return [ordered]@{
        request_executed = [bool]$Response.Executed
        http_status = $Response.HttpStatus
        json = [bool]$Response.JsonParsed
        response_limit_exceeded = [bool]$Response.ResponseLimitExceeded
        transport_failure = [bool]$Response.TransportFailure
    }
}

function Get-MetadataTechnicalName {
    param($Value)

    $entry = Resolve-MetadataEntry -Value $Value
    $metadata = $entry.Value
    if ($metadata -is [string]) {
        if (-not [string]::IsNullOrWhiteSpace($entry.FallbackName)) {
            return $entry.FallbackName
        }
        return $metadata.Trim()
    }
    if (-not (Test-IsJsonObject -Value $metadata)) {
        return $null
    }
    foreach ($candidate in @('name', 'field', 'key', 'technical_name', 'technicalName')) {
        $property = Get-ObjectProperty -Object $metadata -Name $candidate
        if ($property.Found -and $property.Value -is [string] -and -not [string]::IsNullOrWhiteSpace($property.Value)) {
            return $property.Value.Trim()
        }
    }
    return $entry.FallbackName
}

function Get-MetadataDeclaredType {
    param($Value)

    $entry = Resolve-MetadataEntry -Value $Value
    $metadata = $entry.Value
    if ($metadata -is [string]) {
        return $metadata.Trim().ToLowerInvariant()
    }
    if (-not (Test-IsJsonObject -Value $metadata)) {
        return $null
    }
    foreach ($candidate in @('type', 'data_type', 'dataType')) {
        $property = Get-ObjectProperty -Object $metadata -Name $candidate
        if ($property.Found -and $property.Value -is [string] -and -not [string]::IsNullOrWhiteSpace($property.Value)) {
            return $property.Value.Trim().ToLowerInvariant()
        }
    }
    return $null
}

function Resolve-MetadataEntry {
    param($Value)

    if ($null -ne $Value -and
        $null -ne $Value.PSObject.Properties['__contract_fallback_name'] -and
        $null -ne $Value.PSObject.Properties['__contract_value']) {
        return [pscustomobject]@{
            FallbackName = $Value.__contract_fallback_name
            Value = $Value.__contract_value
        }
    }
    return [pscustomobject]@{
        FallbackName = $null
        Value = $Value
    }
}

function Add-MetadataCollectionItems {
    param(
        $Value,
        [System.Collections.Generic.List[object]]$Destination
    )

    if (Test-IsJsonArray -Value $Value) {
        foreach ($item in $Value) {
            $Destination.Add([pscustomobject]@{
                __contract_fallback_name = $null
                __contract_value = $item
            })
        }
        return $true
    }
    if (Test-IsJsonObject -Value $Value) {
        foreach ($key in $Value.Keys) {
            $Destination.Add([pscustomobject]@{
                __contract_fallback_name = [string]$key
                __contract_value = $Value[$key]
            })
        }
        return $true
    }
    return $false
}

function Get-InfoSummary {
    param($Json)

    $summary = [ordered]@{
        root_json_type = Get-JsonType -Value $Json
        json_error_envelope = Test-ErrorEnvelope -Value $Json
        metadata_shape_valid = $false
        field_count = $null
        technical_field_names = @()
        field_profiles = @()
        field_entries_without_safe_technical_name = 0
        filter_count = $null
        declared_type_counts = [ordered]@{}
        fields_without_declared_type = $null
        required_business_filter_declared = $false
        updated_at_filter_declared = $false
    }

    if ($summary['json_error_envelope'] -or -not (Test-IsJsonObject -Value $Json)) {
        return $summary
    }

    $dataProperty = Get-ObjectProperty -Object $Json -Name 'data'
    $document = if ($dataProperty.Found) { $dataProperty.Value } else { $Json }
    if (Test-ErrorEnvelope -Value $document -or -not (Test-IsJsonObject -Value $document)) {
        $summary['json_error_envelope'] = [bool]$summary['json_error_envelope'] -or (Test-ErrorEnvelope -Value $document)
        return $summary
    }

    $fieldsProperty = Get-ObjectProperty -Object $document -Name 'fields'
    if (-not $fieldsProperty.Found) {
        return $summary
    }

    $fields = [System.Collections.Generic.List[object]]::new()
    if (-not (Add-MetadataCollectionItems -Value $fieldsProperty.Value -Destination $fields)) {
        return $summary
    }

    $filters = [System.Collections.Generic.List[object]]::new()
    $filtersProperty = Get-ObjectProperty -Object $document -Name 'filters'
    if ($filtersProperty.Found -and -not (Add-MetadataCollectionItems -Value $filtersProperty.Value -Destination $filters)) {
        return $summary
    }

    $declaredTypeCounts = [ordered]@{}
    $withoutDeclaredType = 0
    $fieldProfiles = New-FieldProfileMap
    $fieldEntriesWithoutSafeTechnicalName = 0
    foreach ($field in $fields) {
        $metadataEntry = Resolve-MetadataEntry -Value $field
        $technicalName = ConvertTo-SanitizedTechnicalFieldName -Value (Get-MetadataTechnicalName -Value $field)
        if ([string]::IsNullOrWhiteSpace($technicalName)) {
            $fieldEntriesWithoutSafeTechnicalName++
        } else {
            $fieldProfile = Get-OrAddFieldProfile -Profiles $fieldProfiles -TechnicalName $technicalName
            Add-FieldProfileObservation -Profile $fieldProfile -Value $metadataEntry.Value
        }

        $declaredType = Get-MetadataDeclaredType -Value $field
        if ([string]::IsNullOrWhiteSpace($declaredType)) {
            $withoutDeclaredType++
        } else {
            Add-Count -Counts $declaredTypeCounts -Name $declaredType
        }
    }

    $summary['metadata_shape_valid'] = $true
    $summary['field_count'] = $fields.Count
    $summary['technical_field_names'] = @(Get-SanitizedTechnicalFieldNames -Profiles $fieldProfiles)
    $summary['field_profiles'] = @(Get-SanitizedFieldProfiles -Profiles $fieldProfiles)
    $summary['field_entries_without_safe_technical_name'] = $fieldEntriesWithoutSafeTechnicalName
    $summary['filter_count'] = $filters.Count
    $summary['declared_type_counts'] = $declaredTypeCounts
    $summary['fields_without_declared_type'] = $withoutDeclaredType
    return $summary
}

function Add-ExpectedFilterFlags {
    param(
        [System.Collections.IDictionary]$Summary,
        $Json,
        [string]$RequiredBusinessFilter
    )

    if (-not $Summary['metadata_shape_valid']) {
        return
    }

    $documentProperty = Get-ObjectProperty -Object $Json -Name 'data'
    $document = if ($documentProperty.Found) { $documentProperty.Value } else { $Json }
    $filtersProperty = Get-ObjectProperty -Object $document -Name 'filters'
    if (-not $filtersProperty.Found) {
        return
    }

    $filters = [System.Collections.Generic.List[object]]::new()
    if (-not (Add-MetadataCollectionItems -Value $filtersProperty.Value -Destination $filters)) {
        return
    }

    $businessFilterCandidates = @(
        $RequiredBusinessFilter,
        $RequiredBusinessFilter.Substring($RequiredBusinessFilter.LastIndexOf('.') + 1)
    )
    $updatedAtFilterCandidates = @('scopes.by_updated_at', 'by_updated_at')
    foreach ($filter in $filters) {
        $name = Get-MetadataTechnicalName -Value $filter
        if ($businessFilterCandidates -contains $name) {
            $Summary['required_business_filter_declared'] = $true
        }
        if ($updatedAtFilterCandidates -contains $name) {
            $Summary['updated_at_filter_declared'] = $true
        }
    }
}

function Get-DataSummary {
    param(
        $Json,
        [int]$RequestedPageSize
    )

    $summary = [ordered]@{
        root_json_type = Get-JsonType -Value $Json
        json_error_envelope = Test-ErrorEnvelope -Value $Json
        record_shape_valid = $false
        record_count = $null
        field_count = $null
        technical_field_names = @()
        field_profiles = @()
        field_names_without_safe_technical_name = 0
        unprofiled_field_occurrence_count = 0
        null_value_count = $null
        value_type_counts = New-ValueTypeCounts
        temporal_format_counts = New-TemporalFormatCounts
        updated_at_observed_count = 0
        updated_at_null_count = 0
        updated_at_value_type_counts = New-ValueTypeCounts
        updated_at_temporal_format_counts = New-TemporalFormatCounts
        page_is_empty = $false
        page_is_short = $false
        physical_record_count_exceeds_requested_size = $false
        page_exceeds_requested_size = $false
    }

    if ($summary['json_error_envelope']) {
        return $summary
    }

    $data = $Json
    if (Test-IsJsonObject -Value $Json) {
        $dataProperty = Get-ObjectProperty -Object $Json -Name 'data'
        if ($dataProperty.Found) {
            $data = $dataProperty.Value
        }
    }
    if ($null -eq $data -or (Test-ErrorEnvelope -Value $data)) {
        $summary['json_error_envelope'] = [bool]$summary['json_error_envelope'] -or (Test-ErrorEnvelope -Value $data)
        return $summary
    }

    $records = [System.Collections.Generic.List[object]]::new()
    if (Test-IsJsonArray -Value $data) {
        foreach ($item in $data) {
            $records.Add($item)
        }
    } elseif (Test-IsJsonObject -Value $data) {
        if ($data.Count -eq 0) {
            return $summary
        }
        $records.Add($data)
    } else {
        return $summary
    }

    $fieldNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $fieldNamesWithoutSafeTechnicalName = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $fieldProfiles = New-FieldProfileMap
    $unprofiledFieldOccurrenceCount = 0
    $valueTypeCounts = New-ValueTypeCounts
    $temporalFormatCounts = New-TemporalFormatCounts
    $nullValueCount = 0
    $updatedAtObservedCount = 0
    $updatedAtNullCount = 0
    $updatedAtValueTypeCounts = New-ValueTypeCounts
    $updatedAtTemporalFormatCounts = New-TemporalFormatCounts

    foreach ($record in $records) {
        if (-not (Test-IsJsonObject -Value $record)) {
            return $summary
        }
        foreach ($key in $record.Keys) {
            $fieldName = [string]$key
            [void]$fieldNames.Add($fieldName)
            $value = $record[$key]
            $technicalName = ConvertTo-SanitizedTechnicalFieldName -Value $fieldName
            if ([string]::IsNullOrWhiteSpace($technicalName)) {
                [void]$fieldNamesWithoutSafeTechnicalName.Add($fieldName)
                $unprofiledFieldOccurrenceCount++
            } else {
                $fieldProfile = Get-OrAddFieldProfile -Profiles $fieldProfiles -TechnicalName $technicalName
                Add-FieldProfileObservation -Profile $fieldProfile -Value $value
            }
            if ($null -eq $value) {
                $nullValueCount++
                continue
            }
            $type = Get-JsonType -Value $value
            Add-Count -Counts $valueTypeCounts -Name $type
            if ($value -is [string]) {
                Add-Count -Counts $temporalFormatCounts -Name (Get-TemporalFormat -Value $value)
            }
        }
        $updatedAt = Get-ObjectProperty -Object $record -Name 'updated_at'
        if ($updatedAt.Found) {
            $updatedAtObservedCount++
            if ($null -eq $updatedAt.Value) {
                $updatedAtNullCount++
            } else {
                $updatedAtType = Get-JsonType -Value $updatedAt.Value
                Add-Count -Counts $updatedAtValueTypeCounts -Name $updatedAtType
                if ($updatedAt.Value -is [string]) {
                    Add-Count -Counts $updatedAtTemporalFormatCounts -Name (Get-TemporalFormat -Value $updatedAt.Value)
                }
            }
        }
    }

    $summary['record_shape_valid'] = $true
    $summary['record_count'] = $records.Count
    $summary['field_count'] = $fieldNames.Count
    $summary['technical_field_names'] = @(Get-SanitizedTechnicalFieldNames -Profiles $fieldProfiles)
    $summary['field_profiles'] = @(Get-SanitizedFieldProfiles -Profiles $fieldProfiles)
    $summary['field_names_without_safe_technical_name'] = $fieldNamesWithoutSafeTechnicalName.Count
    $summary['unprofiled_field_occurrence_count'] = $unprofiledFieldOccurrenceCount
    $summary['null_value_count'] = $nullValueCount
    $summary['value_type_counts'] = $valueTypeCounts
    $summary['temporal_format_counts'] = $temporalFormatCounts
    $summary['updated_at_observed_count'] = $updatedAtObservedCount
    $summary['updated_at_null_count'] = $updatedAtNullCount
    $summary['updated_at_value_type_counts'] = $updatedAtValueTypeCounts
    $summary['updated_at_temporal_format_counts'] = $updatedAtTemporalFormatCounts
    $summary['page_is_empty'] = $records.Count -eq 0
    $summary['page_is_short'] = $records.Count -lt $RequestedPageSize
    $summary['physical_record_count_exceeds_requested_size'] = $records.Count -gt $RequestedPageSize
    return $summary
}

function Get-DataRecordsForComparison {
    param($Json)

    $result = [pscustomobject]@{
        Valid = $false
        Records = [System.Collections.Generic.List[object]]::new()
    }
    if (Test-ErrorEnvelope -Value $Json) {
        return $result
    }

    $data = $Json
    if (Test-IsJsonObject -Value $Json) {
        $dataProperty = Get-ObjectProperty -Object $Json -Name 'data'
        if ($dataProperty.Found) {
            $data = $dataProperty.Value
        }
    }
    if ($null -eq $data -or (Test-ErrorEnvelope -Value $data)) {
        return $result
    }

    if (Test-IsJsonArray -Value $data) {
        foreach ($item in $data) {
            if (-not (Test-IsJsonObject -Value $item)) {
                return $result
            }
            $result.Records.Add($item)
        }
    } elseif (Test-IsJsonObject -Value $data) {
        if ($data.Count -eq 0) {
            $result.Valid = $true
            return $result
        }
        $result.Records.Add($data)
    } else {
        return $result
    }

    $result.Valid = $true
    return $result
}

function ConvertTo-ComparableScalar {
    param($Value)

    if ($null -eq $Value) {
        return $null
    }
    if ($Value -is [string]) {
        return 'string:' + $Value
    }
    if ($Value -is [bool]) {
        return 'boolean:' + $Value.ToString().ToLowerInvariant()
    }
    if ($Value -is [sbyte] -or $Value -is [byte] -or
        $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [System.Numerics.BigInteger] -or
        $Value -is [single] -or $Value -is [double] -or $Value -is [decimal]) {
        return 'number:' + [System.Convert]::ToString($Value, [System.Globalization.CultureInfo]::InvariantCulture)
    }
    return $null
}

function Get-RecordKeyProfile {
    param(
        $Json,
        [string]$FieldName
    )

    $records = Get-DataRecordsForComparison -Json $Json
    $values = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $observedCount = 0
    $nullCount = 0
    $comparableCount = 0

    if ($records.Valid) {
        foreach ($record in $records.Records) {
            $property = Get-ObjectProperty -Object $record -Name $FieldName
            if (-not $property.Found) {
                continue
            }
            $observedCount++
            if ($null -eq $property.Value) {
                $nullCount++
                continue
            }
            $comparable = ConvertTo-ComparableScalar -Value $property.Value
            if ($null -ne $comparable) {
                $comparableCount++
                [void]$values.Add($comparable)
            }
        }
    }

    return [pscustomobject]@{
        Valid = [bool]$records.Valid
        ObservedCount = $observedCount
        NullCount = $nullCount
        ComparableCount = $comparableCount
        DistinctCount = $values.Count
        Values = $values
    }
}

function Add-RecordKeyProfileSummary {
    param(
        [System.Collections.IDictionary]$Summary,
        $Profile,
        [string]$Prefix
    )

    $null = $Summary[('{0}_comparison_valid' -f $Prefix)] = [bool]$Profile.Valid
    $null = $Summary[('{0}_observed_count' -f $Prefix)] = [int]$Profile.ObservedCount
    $null = $Summary[('{0}_null_count' -f $Prefix)] = [int]$Profile.NullCount
    $null = $Summary[('{0}_comparable_count' -f $Prefix)] = [int]$Profile.ComparableCount
    $null = $Summary[('{0}_distinct_count' -f $Prefix)] = [int]$Profile.DistinctCount
    $null = $Summary[('{0}_duplicate_count' -f $Prefix)] = [int]($Profile.ComparableCount - $Profile.DistinctCount)
}

function Get-EntityLimitStatus {
    param(
        $IdProfile,
        [int]$RecordCount,
        [int]$RequestedPageSize
    )

    $verifiable = $IdProfile.Valid -and
        $IdProfile.ObservedCount -eq $RecordCount -and
        $IdProfile.NullCount -eq 0 -and
        $IdProfile.ComparableCount -eq $RecordCount
    return [pscustomobject]@{
        Verifiable = [bool]$verifiable
        DistinctEntityCount = [int]$IdProfile.DistinctCount
        ExceedsRequestedSize = [bool](($verifiable) -and $IdProfile.DistinctCount -gt $RequestedPageSize)
    }
}

function Add-EntityLimitSummary {
    param(
        [System.Collections.IDictionary]$Summary,
        $Status
    )

    $null = $Summary['entity_limit_verifiable'] = [bool]$Status.Verifiable
    $null = $Summary['distinct_entity_count'] = [int]$Status.DistinctEntityCount
    $null = $Summary['entity_count_exceeds_requested_size'] = [bool]$Status.ExceedsRequestedSize
    $null = $Summary['page_exceeds_requested_size'] = [bool]$Status.ExceedsRequestedSize
}

function Add-PageOverlapSummary {
    param(
        [System.Collections.IDictionary]$Summary,
        $FirstProfile,
        $SecondProfile,
        [string]$Prefix
    )

    $comparisonValid = $FirstProfile.Valid -and $SecondProfile.Valid -and
        $FirstProfile.DistinctCount -gt 0 -and $SecondProfile.DistinctCount -gt 0
    $overlapCount = 0
    if ($comparisonValid) {
        foreach ($value in $FirstProfile.Values) {
            if ($SecondProfile.Values.Contains($value)) {
                $overlapCount++
            }
        }
    }

    $null = $Summary[('{0}_page_1_page_2_comparison_valid' -f $Prefix)] = [bool]$comparisonValid
    $null = $Summary[('{0}_page_1_page_2_overlap_count' -f $Prefix)] = $overlapCount
    $null = $Summary[('{0}_page_1_page_2_same_record_set' -f $Prefix)] = [bool](($comparisonValid) -and
        $FirstProfile.DistinctCount -eq $SecondProfile.DistinctCount -and
        $overlapCount -eq $FirstProfile.DistinctCount)
    $null = $Summary[('{0}_page_2_contains_only_new_records' -f $Prefix)] = [bool](($comparisonValid) -and $overlapCount -eq 0)
}

function New-TraversalKeyAccumulator {
    return [pscustomobject]@{
        Valid = $true
        RecordCount = 0
        ObservedCount = 0
        NullCount = 0
        ComparableCount = 0
        Values = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        CrossPageValues = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        PagesWithCrossPageOverlap = 0
    }
}

function Add-TraversalKeyProfile {
    param(
        $Accumulator,
        $Profile,
        [int]$RecordCount
    )

    if (-not $Profile.Valid) {
        $Accumulator.Valid = $false
        return
    }

    $Accumulator.RecordCount += $RecordCount
    $Accumulator.ObservedCount += [int]$Profile.ObservedCount
    $Accumulator.NullCount += [int]$Profile.NullCount
    $Accumulator.ComparableCount += [int]$Profile.ComparableCount

    $pageHasCrossPageOverlap = $false
    foreach ($value in $Profile.Values) {
        if ($Accumulator.Values.Contains($value)) {
            [void]$Accumulator.CrossPageValues.Add($value)
            $pageHasCrossPageOverlap = $true
        }
    }
    if ($pageHasCrossPageOverlap) {
        $Accumulator.PagesWithCrossPageOverlap++
    }
    foreach ($value in $Profile.Values) {
        [void]$Accumulator.Values.Add($value)
    }
}

function Add-TraversalKeySummary {
    param(
        [System.Collections.IDictionary]$Summary,
        $Accumulator,
        [string]$Prefix
    )

    $notComparableNonNullCount = $Accumulator.ObservedCount - $Accumulator.NullCount - $Accumulator.ComparableCount
    $duplicateCount = $Accumulator.ComparableCount - $Accumulator.Values.Count
    $null = $Summary[('{0}_comparison_valid' -f $Prefix)] = [bool]$Accumulator.Valid
    $null = $Summary[('{0}_record_count' -f $Prefix)] = [int]$Accumulator.RecordCount
    $null = $Summary[('{0}_missing_count' -f $Prefix)] = [int]($Accumulator.RecordCount - $Accumulator.ObservedCount)
    $null = $Summary[('{0}_observed_count' -f $Prefix)] = [int]$Accumulator.ObservedCount
    $null = $Summary[('{0}_null_count' -f $Prefix)] = [int]$Accumulator.NullCount
    $null = $Summary[('{0}_nonnull_count' -f $Prefix)] = [int]($Accumulator.ObservedCount - $Accumulator.NullCount)
    $null = $Summary[('{0}_comparable_count' -f $Prefix)] = [int]$Accumulator.ComparableCount
    $null = $Summary[('{0}_not_comparable_nonnull_count' -f $Prefix)] = [int]$notComparableNonNullCount
    $null = $Summary[('{0}_distinct_count' -f $Prefix)] = [int]$Accumulator.Values.Count
    $null = $Summary[('{0}_duplicate_count' -f $Prefix)] = [int]$duplicateCount
    $null = $Summary[('{0}_cross_page_overlap_distinct_count' -f $Prefix)] = [int]$Accumulator.CrossPageValues.Count
    $null = $Summary[('{0}_pages_with_cross_page_overlap_count' -f $Prefix)] = [int]$Accumulator.PagesWithCrossPageOverlap
    $null = $Summary[('{0}_any_cross_page_overlap' -f $Prefix)] = [bool]($Accumulator.CrossPageValues.Count -gt 0)
    $null = $Summary[('{0}_all_comparable_values_unique' -f $Prefix)] = [bool](
        $Accumulator.Valid -and $Accumulator.ComparableCount -eq $Accumulator.Values.Count)
}

function Invoke-BoundedTraversal {
    param(
        [int]$TemplateId,
        [string]$BusinessFilter,
        [string]$NaturalKey,
        [string]$OrderBy,
        [datetime]$Start,
        [datetime]$End,
        [int]$MaximumPages,
        [System.Uri]$BaseUri,
        [string]$Token,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds
    )

    $traversalPageSize = 100
    $summary = [ordered]@{
        enabled = $true
        per = $traversalPageSize
        maximum_pages = $MaximumPages
        pages = @()
        pages_executed = 0
        nonempty_page_count = 0
        terminal_empty_page_observed = $false
        empty_window_observed = $false
        maximum_pages_reached_without_terminal_empty = $false
        stopped_after_nonempty_page = $false
    }
    $idAccumulator = New-TraversalKeyAccumulator
    $naturalKeyAccumulator = New-TraversalKeyAccumulator
    $range = '{0} - {1}' -f $Start.ToString('yyyy-MM-dd'), $End.ToString('yyyy-MM-dd')

    for ($page = 1; $page -le $MaximumPages; $page++) {
        if ($State['stopped']) {
            break
        }

        $query = [ordered]@{
            (('search[{0}]' -f $BusinessFilter.Replace('.', ']['))) = $range
            page = [string]$page
            per = [string]$traversalPageSize
            order_by = $OrderBy
        }
        $uri = New-DataExportUri -BaseUri $BaseUri -TemplateId $TemplateId -Resource 'data' -QueryParameters $query
        $response =
            Invoke-ReadOnlyGet -Uri $uri -Token $Token -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        $pageSummary = New-DataRequestSummary -Response $response -Page $page -PageSize $traversalPageSize
        $idProfile = Get-RecordKeyProfile -Json $response.Json -FieldName 'id'
        $naturalKeyProfile = Get-RecordKeyProfile -Json $response.Json -FieldName $NaturalKey
        Add-RecordKeyProfileSummary -Summary $pageSummary -Profile $idProfile -Prefix 'id'
        Add-RecordKeyProfileSummary -Summary $pageSummary -Profile $naturalKeyProfile -Prefix 'natural_key'
        $entityLimit = if ($pageSummary['record_shape_valid']) {
            Get-EntityLimitStatus -IdProfile $idProfile -RecordCount ([int]$pageSummary['record_count']) -RequestedPageSize $traversalPageSize
        } else {
            [pscustomobject]@{ Verifiable = $false; DistinctEntityCount = 0; ExceedsRequestedSize = $false }
        }
        Add-EntityLimitSummary -Summary $pageSummary -Status $entityLimit
        $summary['pages'] += $pageSummary

        if ($response.Executed) {
            $summary['pages_executed'] = [int]$summary['pages_executed'] + 1
        }
        if ($pageSummary['record_shape_valid']) {
            $recordCount = [int]$pageSummary['record_count']
            Add-TraversalKeyProfile -Accumulator $idAccumulator -Profile $idProfile -RecordCount $recordCount
            Add-TraversalKeyProfile -Accumulator $naturalKeyAccumulator -Profile $naturalKeyProfile -RecordCount $recordCount
            if (-not $pageSummary['page_is_empty']) {
                $summary['nonempty_page_count'] = [int]$summary['nonempty_page_count'] + 1
            }
        }

        if ($response.JsonParsed -and $pageSummary['json_error_envelope']) {
            Stop-Probe -State $State -Reason 'JSON_ERROR_ENVELOPE'
        } elseif ($response.JsonParsed -and -not $pageSummary['record_shape_valid']) {
            Stop-Probe -State $State -Reason 'INVALID_PAGE_SHAPE'
        } elseif ($response.JsonParsed -and -not $entityLimit.Verifiable) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_NOT_VERIFIABLE'
        } elseif ($response.JsonParsed -and $entityLimit.ExceedsRequestedSize) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_EXCEEDS_REQUESTED_SIZE'
        }
        if ($State['stopped']) {
            $summary['stopped_after_nonempty_page'] = [bool]($summary['nonempty_page_count'] -gt 0)
            break
        }

        if ($pageSummary['page_is_empty']) {
            $summary['terminal_empty_page_observed'] = $true
            $summary['empty_window_observed'] = [bool]($summary['nonempty_page_count'] -eq 0)
            break
        }
    }

    if (-not $State['stopped'] -and -not $summary['terminal_empty_page_observed'] -and
        $summary['pages_executed'] -ge $MaximumPages) {
        $summary['maximum_pages_reached_without_terminal_empty'] = $true
        $summary['stopped_after_nonempty_page'] = [bool]($summary['nonempty_page_count'] -gt 0)
        Stop-Probe -State $State -Reason 'TRAVERSAL_PAGE_LIMIT_REACHED'
    }

    Add-TraversalKeySummary -Summary $summary -Accumulator $idAccumulator -Prefix 'id'
    Add-TraversalKeySummary -Summary $summary -Accumulator $naturalKeyAccumulator -Prefix 'natural_key'
    $summary['stopped'] = [bool]$State['stopped']
    $summary['stop_reason'] = $State['stop_reason']
    return $summary
}

function New-DataRequestSummary {
    param(
        $Response,
        [int]$Page,
        [int]$PageSize
    )

    $summary = New-TransportSummary -Response $Response
    $summary['page'] = $Page
    $summary['per'] = $PageSize
    if ($Response.JsonParsed) {
        $dataSummary = Get-DataSummary -Json $Response.Json -RequestedPageSize $PageSize
        foreach ($key in $dataSummary.Keys) {
            $summary[$key] = $dataSummary[$key]
        }
    } else {
        $summary['root_json_type'] = 'invalid'
        $summary['json_error_envelope'] = $false
        $summary['record_shape_valid'] = $false
        $summary['record_count'] = $null
        $summary['field_count'] = $null
        $summary['technical_field_names'] = @()
        $summary['field_profiles'] = @()
        $summary['field_names_without_safe_technical_name'] = 0
        $summary['unprofiled_field_occurrence_count'] = 0
        $summary['null_value_count'] = $null
        $summary['value_type_counts'] = New-ValueTypeCounts
        $summary['temporal_format_counts'] = New-TemporalFormatCounts
        $summary['updated_at_observed_count'] = 0
        $summary['updated_at_null_count'] = 0
        $summary['updated_at_value_type_counts'] = New-ValueTypeCounts
        $summary['updated_at_temporal_format_counts'] = New-TemporalFormatCounts
        $summary['page_is_empty'] = $false
        $summary['page_is_short'] = $false
        $summary['physical_record_count_exceeds_requested_size'] = $false
        $summary['page_exceeds_requested_size'] = $false
    }
    return $summary
}

function New-SkippedSecondPageSummary {
    param([int]$PageSize)

    return [ordered]@{
        request_executed = $false
        page = 2
        per = $PageSize
        page_skipped_after_empty_page_1 = $true
    }
}

function Get-OrderByForTemplate {
    param(
        [int]$TemplateId,
        [string]$Profile
    )

    if ($Profile -eq 'DATE') {
        switch ($TemplateId) {
            6908 { return 'request_date asc' }
            6389 { return 'corporation_sequence_number asc' }
            default { throw 'O template nao pertence a allowlist desta sonda.' }
        }
    }

    switch ($TemplateId) {
        6908 { return 'sequence_code asc' }
        6389 { return 'corporation_sequence_number asc' }
        default { throw 'O template nao pertence a allowlist desta sonda.' }
    }
}

function Invoke-TemplateProbe {
    param(
        [int]$TemplateId,
        [string]$BusinessFilter,
        [string]$NaturalKey,
        [string]$OrderBy,
        [string]$SelectedOrderProfile,
        [datetime]$Start,
        [datetime]$End,
        [int[]]$PageSizes,
        [System.Uri]$BaseUri,
        [string]$Token,
        [System.Collections.IDictionary]$State,
        [int]$CallBudget,
        [int]$DelaySeconds,
        [bool]$RunBoundedTraversal,
        [int]$TraversalMaximumPages
    )

    $templateSummary = [ordered]@{
        template = $TemplateId
        transport = 'GET_QUERY'
        order_profile = $SelectedOrderProfile
        info = $null
        pages = @()
    }

    $infoUri = New-DataExportUri -BaseUri $BaseUri -TemplateId $TemplateId -Resource 'info' -QueryParameters $null
    $infoResponse =
        Invoke-ReadOnlyGet -Uri $infoUri -Token $Token -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
    $infoSummary = New-TransportSummary -Response $infoResponse
    if ($infoResponse.JsonParsed) {
        $metadataSummary = Get-InfoSummary -Json $infoResponse.Json
        Add-ExpectedFilterFlags -Summary $metadataSummary -Json $infoResponse.Json -RequiredBusinessFilter $BusinessFilter
        foreach ($key in $metadataSummary.Keys) {
            $infoSummary[$key] = $metadataSummary[$key]
        }
        if (-not $metadataSummary['metadata_shape_valid']) {
            Stop-Probe -State $State -Reason 'INVALID_INFO_SHAPE'
        } elseif (-not $metadataSummary['required_business_filter_declared'] -or -not $metadataSummary['updated_at_filter_declared']) {
            Stop-Probe -State $State -Reason 'REQUIRED_FILTER_NOT_DECLARED'
        }
    } else {
        $infoSummary['root_json_type'] = 'invalid'
        $infoSummary['json_error_envelope'] = $false
        $infoSummary['metadata_shape_valid'] = $false
    }
    $templateSummary['info'] = $infoSummary

    if ($RunBoundedTraversal) {
        $templateSummary['bounded_traversal'] = Invoke-BoundedTraversal -TemplateId $TemplateId `
            -BusinessFilter $BusinessFilter -NaturalKey $NaturalKey -OrderBy $OrderBy -Start $Start -End $End `
            -MaximumPages $TraversalMaximumPages -BaseUri $BaseUri -Token $Token -State $State `
            -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        return $templateSummary
    }

    foreach ($pageSize in $PageSizes) {
        if ($State['stopped']) {
            break
        }

        $range = '{0} - {1}' -f $Start.ToString('yyyy-MM-dd'), $End.ToString('yyyy-MM-dd')
        $firstPageQuery = [ordered]@{
            (('search[{0}]' -f $BusinessFilter.Replace('.', ']['))) = $range
            page = '1'
            per = [string]$pageSize
            order_by = $OrderBy
        }
        $firstPageUri = New-DataExportUri -BaseUri $BaseUri -TemplateId $TemplateId -Resource 'data' -QueryParameters $firstPageQuery
        $firstResponse =
            Invoke-ReadOnlyGet -Uri $firstPageUri -Token $Token -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        $firstSummary = New-DataRequestSummary -Response $firstResponse -Page 1 -PageSize $pageSize
        $firstIdProfile = Get-RecordKeyProfile -Json $firstResponse.Json -FieldName 'id'
        $firstNaturalKeyProfile = Get-RecordKeyProfile -Json $firstResponse.Json -FieldName $NaturalKey
        Add-RecordKeyProfileSummary -Summary $firstSummary -Profile $firstIdProfile -Prefix 'id'
        Add-RecordKeyProfileSummary -Summary $firstSummary -Profile $firstNaturalKeyProfile -Prefix 'natural_key'
        $firstEntityLimit = if ($firstSummary['record_shape_valid']) {
            Get-EntityLimitStatus -IdProfile $firstIdProfile -RecordCount ([int]$firstSummary['record_count']) -RequestedPageSize $pageSize
        } else {
            [pscustomobject]@{ Verifiable = $false; DistinctEntityCount = 0; ExceedsRequestedSize = $false }
        }
        Add-EntityLimitSummary -Summary $firstSummary -Status $firstEntityLimit
        $templateSummary['pages'] += $firstSummary

        if ($firstResponse.JsonParsed -and $firstSummary['json_error_envelope']) {
            Stop-Probe -State $State -Reason 'JSON_ERROR_ENVELOPE'
        } elseif ($firstResponse.JsonParsed -and -not $firstSummary['record_shape_valid']) {
            Stop-Probe -State $State -Reason 'INVALID_PAGE_SHAPE'
        } elseif ($firstResponse.JsonParsed -and -not $firstEntityLimit.Verifiable) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_NOT_VERIFIABLE'
        } elseif ($firstResponse.JsonParsed -and $firstEntityLimit.ExceedsRequestedSize) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_EXCEEDS_REQUESTED_SIZE'
        }
        if ($State['stopped']) {
            break
        }

        if ($firstSummary['page_is_empty']) {
            $templateSummary['pages'] += New-SkippedSecondPageSummary -PageSize $pageSize
            continue
        }

        $secondPageQuery = [ordered]@{
            (('search[{0}]' -f $BusinessFilter.Replace('.', ']['))) = $range
            page = '2'
            per = [string]$pageSize
            order_by = $OrderBy
        }
        $secondPageUri = New-DataExportUri -BaseUri $BaseUri -TemplateId $TemplateId -Resource 'data' -QueryParameters $secondPageQuery
        $secondResponse =
            Invoke-ReadOnlyGet -Uri $secondPageUri -Token $Token -State $State -CallBudget $CallBudget -DelaySeconds $DelaySeconds
        $secondSummary = New-DataRequestSummary -Response $secondResponse -Page 2 -PageSize $pageSize
        $secondIdProfile = Get-RecordKeyProfile -Json $secondResponse.Json -FieldName 'id'
        $secondNaturalKeyProfile = Get-RecordKeyProfile -Json $secondResponse.Json -FieldName $NaturalKey
        Add-RecordKeyProfileSummary -Summary $secondSummary -Profile $secondIdProfile -Prefix 'id'
        Add-RecordKeyProfileSummary -Summary $secondSummary -Profile $secondNaturalKeyProfile -Prefix 'natural_key'
        $secondEntityLimit = if ($secondSummary['record_shape_valid']) {
            Get-EntityLimitStatus -IdProfile $secondIdProfile -RecordCount ([int]$secondSummary['record_count']) -RequestedPageSize $pageSize
        } else {
            [pscustomobject]@{ Verifiable = $false; DistinctEntityCount = 0; ExceedsRequestedSize = $false }
        }
        Add-EntityLimitSummary -Summary $secondSummary -Status $secondEntityLimit
        Add-PageOverlapSummary -Summary $secondSummary -FirstProfile $firstIdProfile -SecondProfile $secondIdProfile -Prefix 'id'
        Add-PageOverlapSummary -Summary $secondSummary -FirstProfile $firstNaturalKeyProfile -SecondProfile $secondNaturalKeyProfile -Prefix 'natural_key'
        $secondSummary['page_requested_after_nonempty_page_1'] = $true
        $templateSummary['pages'] += $secondSummary

        if ($secondResponse.JsonParsed -and $secondSummary['json_error_envelope']) {
            Stop-Probe -State $State -Reason 'JSON_ERROR_ENVELOPE'
        } elseif ($secondResponse.JsonParsed -and -not $secondSummary['record_shape_valid']) {
            Stop-Probe -State $State -Reason 'INVALID_PAGE_SHAPE'
        } elseif ($secondResponse.JsonParsed -and -not $secondEntityLimit.Verifiable) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_NOT_VERIFIABLE'
        } elseif ($secondResponse.JsonParsed -and $secondEntityLimit.ExceedsRequestedSize) {
            Stop-Probe -State $State -Reason 'ENTITY_COUNT_EXCEEDS_REQUESTED_SIZE'
        }
    }

    return $templateSummary
}

if (-not $BoundedTraversal -and $PageSizeA -eq $PageSizeB) {
    throw 'Os dois tamanhos de pagina devem ser distintos.'
}

$selectedTemplateIds = [System.Collections.Generic.HashSet[int]]::new()
foreach ($templateId in $TemplateIds) {
    [void]$selectedTemplateIds.Add($templateId)
}

$startDate = ConvertTo-ClosedDate -Value $WindowStart
$endDate = ConvertTo-ClosedDate -Value $WindowEnd
Assert-ClosedWindow -Start $startDate -End $endDate

$legacyEnvPath = Get-LegacyEnvPath
$baseUrl = Get-LastNonEmptyEnvValue -EnvPath $legacyEnvPath -Name 'API_BASE_URL'
$token = Get-LastNonEmptyEnvValue -EnvPath $legacyEnvPath -Name 'API_DATAEXPORT_TOKEN'
$baseUri = ConvertTo-SafeBaseUri -Value $baseUrl

$state = [ordered]@{
    calls_attempted = 0
    stopped = $false
    stop_reason = $null
}

$templateDefinitions = @(
    [pscustomobject]@{
        TemplateId = 6908
        BusinessFilter = 'picks.request_date'
        NaturalKey = 'sequence_code'
    },
    [pscustomobject]@{
        TemplateId = 6389
        BusinessFilter = 'freights.service_at'
        NaturalKey = 'corporation_sequence_number'
    }
)
$templates = @()
foreach ($templateDefinition in $templateDefinitions) {
    if ($state['stopped'] -or -not $selectedTemplateIds.Contains($templateDefinition.TemplateId)) {
        continue
    }
    $orderBy = Get-OrderByForTemplate -TemplateId $templateDefinition.TemplateId -Profile $OrderProfile
    $templates += Invoke-TemplateProbe -TemplateId $templateDefinition.TemplateId `
        -BusinessFilter $templateDefinition.BusinessFilter -NaturalKey $templateDefinition.NaturalKey `
        -OrderBy $orderBy -SelectedOrderProfile $OrderProfile -Start $startDate -End $endDate `
        -PageSizes @($PageSizeA, $PageSizeB) -BaseUri $baseUri -Token $token -State $state `
        -CallBudget $MaxCalls -DelaySeconds $InterCallDelaySeconds -RunBoundedTraversal ([bool]$BoundedTraversal) `
        -TraversalMaximumPages $TraversalMaxPages
}

$summary = [ordered]@{
    transport = 'GET_QUERY'
    order_profile = $OrderProfile
    template_ids = @($selectedTemplateIds | Sort-Object)
    bounded_traversal = [bool]$BoundedTraversal
    calls_attempted = $state['calls_attempted']
    call_budget = $MaxCalls
    inter_call_delay_seconds = $InterCallDelaySeconds
    stopped = $state['stopped']
    stop_reason = $state['stop_reason']
    templates = $templates
}

$summary | ConvertTo-Json -Depth 12
if ($state['stopped']) {
    exit 1
}

[CmdletBinding()]
param(
    [Parameter()]
    [string]$LegacyRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '..\etl-extracao-dados'),

    [Parameter()]
    [string]$OutputRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'docs\catalogos\portabilidade')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$v2Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$legacyRootResolved = (Resolve-Path -LiteralPath $LegacyRoot).Path
if ((Split-Path $legacyRootResolved -Leaf) -ne 'etl-extracao-dados') {
    throw "LegacyRoot deve apontar exatamente para o repositório etl-extracao-dados."
}

$outputRootFull = [System.IO.Path]::GetFullPath($OutputRoot)
if (-not $outputRootFull.StartsWith($v2Root + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot deve permanecer dentro de etl-extracao-dados-v2.'
}
[System.IO.Directory]::CreateDirectory($outputRootFull) | Out-Null

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Get-RelativeLegacyPath {
    param([Parameter(Mandatory)][string]$Path)
    return [System.IO.Path]::GetRelativePath($legacyRootResolved, $Path).Replace('\', '/')
}

function Write-DeterministicText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Content
    )
    $normalized = $Content.Replace("`r`n", "`n").Replace("`r", "`n")
    if (-not $normalized.EndsWith("`n", [System.StringComparison]::Ordinal)) {
        $normalized += "`n"
    }
    [System.IO.File]::WriteAllText($Path, $normalized, $utf8NoBom)
}

function Write-DeterministicCsv {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][object[]]$Rows
    )
    if ($Rows.Count -eq 0) {
        throw "Nenhuma linha gerada para $Path."
    }
    $csv = ($Rows | ConvertTo-Csv -NoTypeInformation) -join "`n"
    Write-DeterministicText -Path $Path -Content $csv
}

function Remove-SqlComments {
    param([Parameter(Mandatory)][string]$Text)
    $withoutBlocks = [regex]::Replace($Text, '(?s)/\*.*?\*/', ' ')
    return [regex]::Replace($withoutBlocks, '(?m)--.*$', ' ')
}

function Get-DelimitedBody {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][int]$OpenIndex
    )
    $depth = 0
    $inString = $false
    for ($i = $OpenIndex; $i -lt $Text.Length; $i++) {
        $character = $Text[$i]
        if ($character -eq "'") {
            if ($inString -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -eq "'") {
                $i++
                continue
            }
            $inString = -not $inString
            continue
        }
        if ($inString) { continue }
        if ($character -eq '(') { $depth++ }
        elseif ($character -eq ')') {
            $depth--
            if ($depth -eq 0) {
                return $Text.Substring($OpenIndex + 1, $i - $OpenIndex - 1)
            }
        }
    }
    throw 'Parênteses SQL não balanceados.'
}

function Split-TopLevelComma {
    param([Parameter(Mandatory)][string]$Text)
    $parts = [System.Collections.Generic.List[string]]::new()
    $start = 0
    $depth = 0
    $inString = $false
    $inBracket = $false
    for ($i = 0; $i -lt $Text.Length; $i++) {
        $character = $Text[$i]
        if ($inString) {
            if ($character -eq "'") {
                if ($i + 1 -lt $Text.Length -and $Text[$i + 1] -eq "'") { $i++; continue }
                $inString = $false
            }
            continue
        }
        if ($inBracket) {
            if ($character -eq ']') { $inBracket = $false }
            continue
        }
        if ($character -eq "'") { $inString = $true; continue }
        if ($character -eq '[') { $inBracket = $true; continue }
        if ($character -eq '(') { $depth++ }
        elseif ($character -eq ')') { $depth-- }
        elseif ($character -eq ',' -and $depth -eq 0) {
            $parts.Add($Text.Substring($start, $i - $start).Trim())
            $start = $i + 1
        }
    }
    if ($start -lt $Text.Length) { $parts.Add($Text.Substring($start).Trim()) }
    return $parts.ToArray()
}

function Get-CreateTableColumns {
    param([Parameter(Mandatory)][string]$Path)
    $text = Remove-SqlComments -Text ([System.IO.File]::ReadAllText($Path))
    $match = [regex]::Match($text, '(?is)\bCREATE\s+TABLE\s+(?<table>(?:\[?\w+\]?\.)?\[?\w+\]?)\s*\(')
    if (-not $match.Success) { throw "CREATE TABLE não encontrado em $(Get-RelativeLegacyPath $Path)." }
    $openIndex = $match.Index + $match.Length - 1
    $body = Get-DelimitedBody -Text $text -OpenIndex $openIndex
    $columns = [System.Collections.Generic.List[object]]::new()
    foreach ($segment in (Split-TopLevelComma -Text $body)) {
        $clean = $segment.Trim()
        if (-not $clean -or $clean -match '^(?i:CONSTRAINT|PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK)\b') { continue }
        $columnMatch = [regex]::Match($clean, '^(?:\[(?<bracket>[^\]]+)\]|(?<bare>[A-Za-z_][A-Za-z0-9_]*))\s+(?<definition>.+)$', [System.Text.RegularExpressions.RegexOptions]::Singleline)
        if (-not $columnMatch.Success) { continue }
        $name = if ($columnMatch.Groups['bracket'].Success) { $columnMatch.Groups['bracket'].Value } else { $columnMatch.Groups['bare'].Value }
        $definition = $columnMatch.Groups['definition'].Value.Trim()
        $typeMatch = [regex]::Match($definition, '^(?<type>\[[^\]]+\]|[A-Za-z0-9_]+)\s*(?<size>\([^\)]*\))?')
        $type = if ($typeMatch.Success) { ($typeMatch.Groups['type'].Value + $typeMatch.Groups['size'].Value).Replace('[', '').Replace(']', '').Replace(' ', '').ToUpperInvariant() } else { 'COMPUTED_OR_UNRESOLVED' }
        $nullable = if ($definition -match '(?i)\bNOT\s+NULL\b|\bPRIMARY\s+KEY\b') { 'NO' } else { 'YES' }
        $columns.Add([pscustomobject][ordered]@{
            Name = $name
            Type = $type
            Nullable = $nullable
            Definition = [regex]::Replace($definition, '\s+', ' ').Trim()
        })
    }
    return $columns.ToArray()
}

function Find-TopLevelKeyword {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Keyword,
        [int]$StartIndex = 0
    )
    $depth = 0
    $inString = $false
    $inBracket = $false
    for ($i = $StartIndex; $i -le $Text.Length - $Keyword.Length; $i++) {
        $character = $Text[$i]
        if ($inString) {
            if ($character -eq "'") {
                if ($i + 1 -lt $Text.Length -and $Text[$i + 1] -eq "'") { $i++; continue }
                $inString = $false
            }
            continue
        }
        if ($inBracket) {
            if ($character -eq ']') { $inBracket = $false }
            continue
        }
        if ($character -eq "'") { $inString = $true; continue }
        if ($character -eq '[') { $inBracket = $true; continue }
        if ($character -eq '(') { $depth++; continue }
        if ($character -eq ')') { $depth--; continue }
        if ($depth -ne 0) { continue }
        if ([string]::Compare($Text, $i, $Keyword, 0, $Keyword.Length, $true) -eq 0) {
            $beforeOk = $i -eq 0 -or -not [char]::IsLetterOrDigit($Text[$i - 1]) -and $Text[$i - 1] -ne '_'
            $afterIndex = $i + $Keyword.Length
            $afterOk = $afterIndex -ge $Text.Length -or -not [char]::IsLetterOrDigit($Text[$afterIndex]) -and $Text[$afterIndex] -ne '_'
            if ($beforeOk -and $afterOk) { return $i }
        }
    }
    return -1
}

function Get-ViewOutputs {
    param([Parameter(Mandatory)][string]$Path)
    $text = Remove-SqlComments -Text ([System.IO.File]::ReadAllText($Path))
    $viewMatch = [regex]::Match($text, '(?is)\bCREATE\s+(?:OR\s+ALTER\s+)?VIEW\s+(?<view>(?:\[?\w+\]?\.)?\[?\w+\]?)\s+AS\b')
    if (-not $viewMatch.Success) { throw "CREATE VIEW não encontrado em $(Get-RelativeLegacyPath $Path)." }
    $body = $text.Substring($viewMatch.Index + $viewMatch.Length)
    $selectIndex = Find-TopLevelKeyword -Text $body -Keyword 'SELECT'
    if ($selectIndex -lt 0) { throw "SELECT principal não encontrado em $(Get-RelativeLegacyPath $Path)." }
    $fromIndex = Find-TopLevelKeyword -Text $body -Keyword 'FROM' -StartIndex ($selectIndex + 6)
    if ($fromIndex -lt 0) { throw "FROM principal não encontrado em $(Get-RelativeLegacyPath $Path)." }
    $projection = $body.Substring($selectIndex + 6, $fromIndex - $selectIndex - 6).Trim()
    $projection = [regex]::Replace($projection, '^(?i:DISTINCT|ALL)\s+', '')
    $outputs = [System.Collections.Generic.List[object]]::new()
    $position = 0
    foreach ($expression in (Split-TopLevelComma -Text $projection)) {
        $position++
        $clean = [regex]::Replace($expression.Trim(), '\s+', ' ')
        $name = $null
        $aliasMatch = [regex]::Match($clean, '(?is)\s+AS\s+(?:\[(?<bracket>[^\]]+)\]|(?<bare>[A-Za-z_][A-Za-z0-9_]*))\s*$')
        if ($aliasMatch.Success) {
            $name = if ($aliasMatch.Groups['bracket'].Success) { $aliasMatch.Groups['bracket'].Value } else { $aliasMatch.Groups['bare'].Value }
        } else {
            $trailingBracket = [regex]::Match($clean, '\s+\[(?<name>[^\]]+)\]\s*$')
            if ($trailingBracket.Success) {
                $name = $trailingBracket.Groups['name'].Value
            } else {
                $direct = [regex]::Match($clean, '^(?:[A-Za-z_][A-Za-z0-9_]*\.)?\[?(?<name>[A-Za-z_][A-Za-z0-9_]*)\]?$')
                if ($direct.Success) { $name = $direct.Groups['name'].Value }
            }
        }
        $status = 'OUTPUT_NAME_PARSED'
        if ([string]::IsNullOrWhiteSpace($name)) {
            $name = '__expression_{0:D3}' -f $position
            $status = 'UNRESOLVED_OUTPUT_ALIAS'
        }
        $outputs.Add([pscustomobject][ordered]@{
            Name = $name
            Expression = $clean
            ParseStatus = $status
        })
    }
    return $outputs.ToArray()
}

function Get-GraphQlLeafPaths {
    param(
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][string]$QueryName
    )
    $tokens = [regex]::Matches($Query, '[A-Za-z_][A-Za-z0-9_]*|\$[A-Za-z_][A-Za-z0-9_]*|[{}():!,\[\]]|\d+') | ForEach-Object Value
    $firstBrace = [Array]::IndexOf($tokens, '{')
    if ($firstBrace -lt 0) { throw "Selection set ausente em $QueryName." }
    $script:index = $firstBrace
    $paths = [System.Collections.Generic.List[string]]::new()

    function Read-SelectionSet {
        param([string[]]$Prefix)
        if ($tokens[$script:index] -ne '{') { throw "Selection set inválido em $QueryName." }
        $script:index++
        while ($script:index -lt $tokens.Count -and $tokens[$script:index] -ne '}') {
            $token = $tokens[$script:index]
            if ($token -in @(',', '!')) { $script:index++; continue }
            if ($token -notmatch '^[A-Za-z_]') { $script:index++; continue }
            $field = $token
            $script:index++
            if ($script:index -lt $tokens.Count -and $tokens[$script:index] -eq ':') {
                $script:index++
                if ($script:index -lt $tokens.Count) { $field = $tokens[$script:index]; $script:index++ }
            }
            if ($script:index -lt $tokens.Count -and $tokens[$script:index] -eq '(') {
                $argumentDepth = 0
                do {
                    if ($tokens[$script:index] -eq '(') { $argumentDepth++ }
                    elseif ($tokens[$script:index] -eq ')') { $argumentDepth-- }
                    $script:index++
                } while ($script:index -lt $tokens.Count -and $argumentDepth -gt 0)
            }
            while ($script:index -lt $tokens.Count -and $tokens[$script:index] -eq '@') { $script:index++ }
            $nextPrefix = @($Prefix + $field)
            if ($script:index -lt $tokens.Count -and $tokens[$script:index] -eq '{') {
                Read-SelectionSet -Prefix $nextPrefix
            } else {
                $paths.Add(($nextPrefix -join '.'))
            }
        }
        if ($script:index -lt $tokens.Count -and $tokens[$script:index] -eq '}') { $script:index++ }
    }

    Read-SelectionSet -Prefix @()
    return $paths.ToArray()
}

function Get-ProtectionClass {
    param([Parameter(Mandatory)][string]$Name)
    $lower = $Name.ToLowerInvariant()
    if ($lower -match 'token|secret|password|authorization|credential') { return 'SECRET' }
    if ($lower -match 'cpf|cnpj|document|doc_|email|phone|telefone|celular|address|endereco|postal|cep|line1|line2|neighborhood|bairro') { return 'PII_DIRECT' }
    if ($lower -match 'nome|name|nickname|requester|motorista|driver|user') { return 'PII_PERSONAL' }
    if ($lower -match 'bank|banco|agency|agencia|account|conta|payment|paid|invoice|fatura|cte|nfse|mdfe|fiscal|tax|pis|cofins|difal|value|valor|total|subtotal|cost|price|tarifa|toll|gris') { return 'FINANCIAL_FISCAL' }
    if ($lower -match 'latitude|longitude|route|rota|city|cidade|state|uf|region|regiao|location|localizacao|placa|vehicle|veiculo|parada') { return 'OPERATIONAL_LOCATION' }
    if ($lower -match 'comment|reason|motivo|observ|description|descricao|xml|pdf') { return 'SENSITIVE_CONTENT' }
    if ($lower -match 'id|key|hash|sequence|codigo|code|cursor|run|execution|metadata') { return 'TECHNICAL_IDENTIFIER' }
    return 'INTERNAL_OPERATIONAL'
}

function Get-OwnerForEntity {
    param([Parameter(Mandatory)][string]$Entity)
    switch ($Entity) {
        'coletas' { 'OPERACAO_E_PLATAFORMA' }
        'fretes' { 'OPERACAO_FISCAL_E_PLATAFORMA' }
        'manifestos' { 'OPERACAO_E_BI' }
        'cotacoes' { 'COMERCIAL_E_OPERACAO' }
        'localizacao_cargas' { 'LOGISTICA_E_OPERACAO' }
        'contas_a_pagar' { 'CONTROLADORIA_E_FINANCEIRO' }
        'faturas_por_cliente' { 'CONTROLADORIA_E_FISCAL' }
        'inventario' { 'OPERACAO_E_LOGISTICA' }
        'sinistros' { 'OPERACAO_E_SINISTROS' }
        'usuarios' { 'SEGURANCA_E_OPERACAO' }
        'raster' { 'OPERACAO_RASTER_E_PLATAFORMA' }
        default { 'PLATAFORMA_DE_DADOS' }
    }
}

function Get-DataFieldDecision {
    param(
        [Parameter(Mandatory)][string]$Entity,
        [Parameter(Mandatory)][string]$Name
    )
    $lower = $Name.ToLowerInvariant()
    $isSourceIdentity =
        (($Entity -in @('coletas','fretes','faturas_por_cliente')) -and $lower -eq 'id') -or
        (($Entity -in @('manifestos','cotacoes','inventario','sinistros')) -and $lower -eq 'sequence_code') -or
        ($Entity -eq 'localizacao_cargas' -and $lower -eq 'corporation_sequence_number')
    if ($isSourceIdentity) { return 'PROMOTE' }
    if ($Entity -eq 'contas_a_pagar') { return 'SPLIT' }
    if ($Entity -eq 'manifestos') {
        if ($lower -in @('mft_pfs_pck_sequence_code','mft_mfs_number','mft_mfs_key')) {
            return 'SPLIT'
        }
        # mdfe_status is a root scalar replicated by the physical expansion. In the
        # bounded corpus it is VALUE on every row, including the 148 rows without an
        # MDF-e key/number, so it must neither signal nor identify an MDF-e child.
        if ($lower -eq 'mdfe_status') { return 'PRESERVE' }
        if ($lower -in @(
            'generate_mdfe', 'pick_manifest_items_count', 'reverse_pick_manifest_items_count',
            'calculated_pick_count', 'calculated_reverse_pick_count', 'pick_subtotal',
            'reverse_pick_subtotal'
        )) {
            return 'PRESERVE'
        }
    }
    if ($Entity -eq 'inventario' -and $lower -match 'invoice|freight|mapping|minuta') { return 'SPLIT' }
    if ($Entity -eq 'sinistros' -and $lower -match 'invoice|freight|minuta|occurrence') { return 'SPLIT' }
    if ($Entity -eq 'faturas_por_cliente' -and $lower -match 'freight|invoice|cte|nfse|order') { return 'SPLIT' }
    if ($lower -match 'pick_items|pick_item_id|fit_p_m_pck_sequence_code') { return 'SPLIT' }
    if ($lower -match '(^|_)(sequence_code|corporation_sequence_number|unique_id|reference_number)($|_)') { return 'ALIAS' }
    if ($lower -match '(^|_)(status|type|classification)($|_)|_at$|_date$|_hour$|updated|created|finished|issued|departured|closed') { return 'NORMALIZE' }
    return 'PRESERVE'
}

function Get-ManifestosReducer {
    param([Parameter(Mandatory)][string]$Name)

    switch ($Name.ToLowerInvariant()) {
        'sequence_code' { return 'SCOPED_ROOT_IDENTITY_P01' }
        'created_at' { return 'SHARED_FRESHNESS_FINISHED_CLOSED_DEPARTURED_CREATED_V03' }
        'departured_at' { return 'SHARED_FRESHNESS_FINISHED_CLOSED_DEPARTURED_CREATED_AND_COMPETENCE_PRIMARY_V03' }
        'closed_at' { return 'SHARED_FRESHNESS_FINISHED_CLOSED_DEPARTURED_CREATED_V03' }
        'finished_at' { return 'SHARED_FRESHNESS_FINISHED_CLOSED_DEPARTURED_CREATED_V03' }
        'status' { return 'KNOWN_STATUS_PRECEDENCE_CLOSED_IN_TRANSIT_PENDING_AT_WINNING_FRESHNESS_V03' }
        'mft_pfs_pck_sequence_code' { return 'ROOT_SCOPED_PICK_CHILD_KEY_P01_RELATION_PENDING_V2_046A' }
        'mft_mfs_key' { return 'ROOT_SCOPED_MDFE_CHILD_KEY_P01_EXACT_STRING44_V03' }
        'mft_mfs_number' { return 'MDFE_CHILD_ATTRIBUTE_PHYSICALLY_PAIRED_WITH_KEY_NOT_IDENTITY_V03' }
        'mdfe_status' { return 'ROOT_SCALAR_REPLICATED_BY_EXPANSION_TRI_STATE_UNIQUE_AT_WINNING_FRESHNESS_NOT_CHILD_SIGNAL_V03' }
        'km' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        'total_cost' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        'manifest_freights_total' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        'total_taxed_weight' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        'mft_vie_weight_capacity' { return 'ONE_SOURCE_SIGNAL_TWO_COMPATIBLE_PROJECTIONS_PRESERVE_ZERO_V03' }
        'vehicle_weight_capacity' { return 'ONE_SOURCE_SIGNAL_TWO_COMPATIBLE_PROJECTIONS_PRESERVE_ZERO_V03' }
        'capacidade_kg' { return 'DERIVE_FROM_REDUCED_MFT_VIE_WEIGHT_CAPACITY_NO_INDEPENDENT_REDUCER_V03' }
        'manifest_items_count' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        'finalized_manifest_items_count' { return 'UNIQUE_VALUE_COMPLEMENT_NULL_PRESERVE_ZERO_CONFLICT_QUARANTINE_V03' }
        default { return 'ROOT_SCALAR_TRI_STATE_UNIQUE_AT_WINNING_FRESHNESS_V03' }
    }
}

function Get-ProtectionPolicyId {
    param([Parameter(Mandatory)][string]$ProtectionClass)
    switch ($ProtectionClass) {
        'SECRET' { 'PROT-01' }
        'PII_DIRECT' { 'PROT-04/PROT-06' }
        'PII_PERSONAL' { 'PROT-05' }
        'FINANCIAL_FISCAL' { 'PROT-08' }
        'OPERATIONAL_LOCATION' { 'PROT-07' }
        'SENSITIVE_CONTENT' { 'PROT-09' }
        'TECHNICAL_IDENTIFIER' { 'PROT-03' }
        default { 'PROT-13' }
    }
}

function ConvertTo-SafeCatalogToken {
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return '' }
    $decomposed = $Value.ToLowerInvariant().Normalize([System.Text.NormalizationForm]::FormD)
    $withoutMarks = -join ($decomposed.ToCharArray() | Where-Object {
        [System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($_) -ne [System.Globalization.UnicodeCategory]::NonSpacingMark
    })
    return [regex]::Replace($withoutMarks, '[^a-z0-9_]+', '_').Trim('_')
}

function New-ArtifactRow {
    param(
        [string]$Type, [string]$Id, [string]$SourcePath, [string]$Responsibility,
        [string]$Decision, [string]$Destination, [string]$Owner, [string]$DueGate,
        [string]$Status, [string]$Evidence, [string]$PublicationBlocked, [string]$Notes
    )
    return [pscustomobject][ordered]@{
        artifact_type = $Type
        artifact_id = $Id
        source_path = $SourcePath
        responsibility = $Responsibility
        decision = $Decision
        v2_destination = $Destination
        owner_role = $Owner
        acceptor = 'TIME_NOMINAL_NAO_INFORMADO'
        due_gate = $DueGate
        status = $Status
        evidence = $Evidence
        # O baseline não possui aceitante nominal/time versionado; nenhuma linha pode publicar.
        publication_blocked = 'YES'
        notes = $Notes
    }
}

function New-FieldRow {
    param(
        [string]$RowId, [string]$RowKind, [string]$Entity, [string]$SourceContract,
        [string]$Template, [string]$SourceRoot, [string]$SourcePath, [string]$SourceType,
        [string]$Presence, [string]$Cardinality, [string]$Requested, [string]$DtoPresence,
        [string]$LegacyMapping, [string]$V2Zone, [string]$V2Target, [string]$Transformation,
        [string]$TimeZone, [string]$Reducer, [string]$Unit, [string]$Currency,
        [string]$PrecisionScale, [string]$Rounding, [string]$ProtectionClass,
        [string]$RetentionPolicy, [string]$Consumer, [string]$Decision, [string]$Owner,
        [string]$Status, [string]$DueGate, [string]$Evidence, [string]$Fixture,
        [string]$PublicationBlocked, [string]$DtoFieldName = '', [string]$DtoJavaType = ''
    )
    $contractVersion = switch -Regex ($SourceContract) {
        '^ESL_DATA_EXPORT' { 'EXTERNAL_CONTRACT_UNVERSIONED; fingerprint_due_V2-025d' }
        '^ESL_GRAPHQL' { 'LOCAL_QUERY_SNAPSHOT_2026-08-30; remote_schema_unverified' }
        '^RASTER' { 'EXTERNAL_CONTRACT_UNVERSIONED; decision_due_V2-034a' }
        '^V1_SQL' { 'LOCAL_LEGACY_SNAPSHOT_2026-08-30' }
        '^GRAPHQL_INDIVIDUAL' { 'LOCAL_QUERY_AND_V1_SNAPSHOT_2026-09-01; remote_schema_unverified' }
        default { 'LOCAL_STATIC_BASELINE_2026-08-30' }
    }
    $observationChannel = switch ($RowKind) {
        'DATA_EXPORT_FIELD' { 'INFO_FIELD_SLOT' }
        'DATA_EXPORT_DATA_FIELD' { 'DATA_FIELD_CANDIDATE' }
        'GRAPHQL_SELECTION' { 'GRAPHQL_SELECTION' }
        'V1_OPERATIONAL_COLUMN' { 'V1_PHYSICAL_COLUMN' }
        'V1_USER_COLUMN' { 'V1_USER_PHYSICAL_COLUMN' }
        'V1_RASTER_COLUMN' { 'V1_RASTER_PHYSICAL_COLUMN' }
        'V1_FACT_COLUMN' { 'MART_PHYSICAL_COLUMN' }
        'V1_VIEW_OUTPUT' { 'PUB_VIEW_OUTPUT' }
        default { $RowKind }
    }
    $dtoField = if (-not [string]::IsNullOrWhiteSpace($DtoFieldName)) { $DtoFieldName } elseif ($DtoPresence -eq 'YES' -and $SourcePath -match '^/data/(?<name>.+)$') { $Matches['name'] } else { 'NOT_PRESENT_OR_UNRESOLVED' }
    $dtoType = if (-not [string]::IsNullOrWhiteSpace($DtoJavaType)) { $DtoJavaType } else { 'NOT_PRESENT_OR_UNRESOLVED' }
    $semanticName = if ($SourcePath -match '^/(?:data|info)/(?<name>.+)$') {
        $Matches['name']
    } elseif ($RowKind -eq 'GRAPHQL_SELECTION') {
        if ($SourcePath -match '\.node\.(?<leaf>.+)$') { $Matches['leaf'] } else { $SourcePath }
    } elseif ($RowKind -eq 'V1_VIEW_OUTPUT' -and $LegacyMapping.StartsWith(('dbo.' + $Entity + '.'), [System.StringComparison]::OrdinalIgnoreCase)) {
        $LegacyMapping.Substring(('dbo.' + $Entity + '.').Length)
    } elseif ($LegacyMapping.Contains('.')) {
        $LegacyMapping.Substring($LegacyMapping.LastIndexOf('.') + 1)
    } else {
        $RowId
    }
    $lineageQualifier = switch ($RowKind) {
        'V1_VIEW_OUTPUT' { 'pub' }
        'V1_FACT_COLUMN' { 'mart' }
        'GRAPHQL_SELECTION' { 'graphql_' + (ConvertTo-SafeCatalogToken $Template) }
        'V1_USER_COLUMN' { 'core' }
        'V1_RASTER_COLUMN' { 'raster' }
        default { '' }
    }
    $lineageParts = @((ConvertTo-SafeCatalogToken $Entity))
    if (-not [string]::IsNullOrWhiteSpace($lineageQualifier)) { $lineageParts += $lineageQualifier }
    $safeSemanticName = ConvertTo-SafeCatalogToken $semanticName
    if ([string]::IsNullOrWhiteSpace($safeSemanticName)) { $safeSemanticName = ConvertTo-SafeCatalogToken $RowId }
    $lineageParts += $safeSemanticName
    if ($RowKind -eq 'V1_VIEW_OUTPUT') { $lineageParts += (ConvertTo-SafeCatalogToken $RowId) }
    $lineageGroup = $lineageParts -join '::'
    $protectionPolicy = Get-ProtectionPolicyId -ProtectionClass $ProtectionClass
    return [pscustomobject][ordered]@{
        matrix_id = $RowId
        row_kind = $RowKind
        entity = $Entity
        source_contract = $SourceContract
        contract_version_or_fingerprint = $contractVersion
        template_or_document = $Template
        source_root = $SourceRoot
        source_path = $SourcePath
        observation_channel = $observationChannel
        source_type = $SourceType
        presence = $Presence
        cardinality = $Cardinality
        requested = $Requested
        dto_field = $dtoField
        dto_type = $dtoType
        dto_presence = $DtoPresence
        legacy_mapping = $LegacyMapping
        lineage_group = $lineageGroup
        v2_zone = $V2Zone
        v2_target = $V2Target
        transformation = $Transformation
        time_zone = $TimeZone
        reducer_or_fallback = $Reducer
        unit = $Unit
        currency = $Currency
        precision_scale = $PrecisionScale
        rounding = $Rounding
        protection_class = $ProtectionClass
        protection_policy_id = $protectionPolicy
        retention_policy = $RetentionPolicy
        consumer = $Consumer
        impact = $Consumer
        decision = $Decision
        owner_role = $Owner
        acceptor = 'TIME_NOMINAL_NAO_INFORMADO'
        status = $Status
        due_gate = $DueGate
        dependencies = $DueGate
        evidence = $Evidence
        fixture = $Fixture
        expected_evidence = $Fixture
        # O baseline não possui aceitante nominal/time versionado; nenhuma linha pode publicar.
        publication_blocked = 'YES'
    }
}

$artifacts = [System.Collections.Generic.List[object]]::new()
$fields = [System.Collections.Generic.List[object]]::new()

# CLI: todas as flags registradas são extraídas do registry, sem executar o runtime.
$commandRegistryPath = Join-Path $legacyRootResolved 'src\main\java\br\com\extrator\comandos\cli\CommandRegistry.java'
$commandText = [System.IO.File]::ReadAllText($commandRegistryPath)
$commandFlags = [regex]::Matches($commandText, '"(?<flag>--[a-z0-9-]+)"') | ForEach-Object { $_.Groups['flag'].Value } | Sort-Object -Unique
$commandPreserve = @('--ajuda', '--help')
$commandSubstitute = @('--fluxo-completo', '--extracao-intervalo', '--fechamento-mensal', '--recovery', '--expurgo-orfaos', '--validar', '--auditoria', '--sincronizar-usuarios', '--materializar-fatos-bi')
$commandConsolidate = @('--verificar-timestamps', '--verificar-timezone', '--validar-manifestos', '--validar-dados', '--validar-api-banco-24h', '--validar-api-banco-24h-detalhado', '--validar-etl-extremo', '--validar-etl-resiliencia')
$commandConditional = @('--exportar-csv')
$commandHelpOmissions = @('--executar-step-isolado','--exportar-csv','--limpar-tabelas','--loop-daemon-run','--validar-dados','--validar-manifestos','--verificar-timestamps','--verificar-timezone')
foreach ($flag in $commandFlags) {
    if ($flag -in $commandPreserve) { $decision = 'PRESERVE'; $destination = 'cli help'; $gate = 'V2-018' }
    elseif ($flag -in $commandSubstitute) { $decision = 'SUBSTITUTE'; $destination = 'CLI one-shot/registry tipado'; $gate = 'V2-018/V2-022/V2-023' }
    elseif ($flag -in $commandConsolidate) { $decision = 'CONSOLIDATE'; $destination = 'gates de DQ/paridade'; $gate = 'V2-012/V2-038/V2-050' }
    elseif ($flag -in $commandConditional) { $decision = 'CONDITIONAL'; $destination = 'ferramenta administrativa protegida'; $gate = 'V2-037/V2-045' }
    else { $decision = 'RETIRE'; $destination = 'sem equivalente no CLI produtivo'; $gate = 'V2-018/V2-022' }
    $helpNote = if ($flag -in $commandHelpOmissions) { 'FLAG_REGISTRADA_MAS_OMITIDA_DO_HELP_LEGADO' } else { 'flag presente no registry/help ou alias' }
    $artifacts.Add((New-ArtifactRow -Type 'command_flag' -Id $flag -SourcePath (Get-RelativeLegacyPath $commandRegistryPath) -Responsibility 'entrada CLI legada' -Decision $decision -Destination $destination -Owner 'PLATAFORMA_E_OPERACOES' -DueGate $gate -Status 'CLASSIFIED' -Evidence 'registro estático no CommandRegistry; sem execução' -PublicationBlocked 'YES' -Notes ('37 flags/36 responsabilidades; ' + $helpNote + '; --ajuda/--help são aliases')))
}
$artifacts.Add((New-ArtifactRow -Type 'command_default' -Id 'NO_ARGUMENTS' -SourcePath (Get-RelativeLegacyPath $commandRegistryPath) -Responsibility 'default implícito executa fluxo completo' -Decision 'RETIRE' -Destination 'mostrar ajuda e sair sem efeito' -Owner 'PLATAFORMA_E_OPERACOES' -DueGate 'V2-018' -Status 'CLASSIFIED' -Evidence 'bootstrap/registry legado' -PublicationBlocked 'YES' -Notes 'fail-safe: ausência de argumentos não inicia carga'))

# Entidades e fontes.
$entityRows = @(
    @('coletas','GraphQL legado é a fonte implementada; Data Export 6908 é candidato sem constante/extrator local','SUBSTITUTE','core.coletas','V2-010'),
    @('fretes','GraphQL legado é a fonte principal; Data Export 6389 é somente enriquecimento/performance','SUBSTITUTE','core.fretes','V2-011'),
    @('manifestos','ESL Data Export 6399','SUBSTITUTE','core.manifestos + filhos','V2-026'),
    @('cotacoes','ESL Data Export 6906','SUBSTITUTE','core.cotacoes','V2-027'),
    @('localizacao_cargas','ESL Data Export 8656','SUBSTITUTE','core.localizacao_cargas','V2-028'),
    @('contas_a_pagar','ESL Data Export 8636','SUBSTITUTE','core.contas_a_pagar + parcelas','V2-029'),
    @('faturas_por_cliente','ESL Data Export 4924','SUBSTITUTE','core.faturas_por_cliente + crosswalks','V2-030'),
    @('inventario','ESL Data Export 10633','SUBSTITUTE','core.inventario + filhos','V2-031'),
    @('sinistros','ESL Data Export 6392','SUBSTITUTE','core.sinistros + relações','V2-032'),
    @('usuarios','GraphQL individual(enabled=true) transitório; Data Export exige template oficial; 9901 é só ID de auditoria','PRESERVE','core.usuario + core.usuario_history + core.v_usuario_dimension_current_v1','V2-024/V2-033/V2-035b'),
    @('raster','API Raster','CONDITIONAL','core.raster_viagens + paradas','V2-034a')
)
foreach ($entry in $entityRows) {
    $status = if ($entry[0] -eq 'raster') { 'CONDITIONAL_DISABLED' } elseif ($entry[0] -eq 'usuarios') { 'IMPLEMENTED_IN_SHADOW' } else { 'CLASSIFIED' }
    $artifacts.Add((New-ArtifactRow -Type 'entity_source' -Id $entry[0] -SourcePath 'STATES.md#catalogo-de-fontes' -Responsibility $entry[1] -Decision $entry[2] -Destination $entry[3] -Owner (Get-OwnerForEntity $entry[0]) -DueGate $entry[4] -Status $status -Evidence 'roadmap + código/SQL legado auditado offline' -PublicationBlocked 'YES' -Notes 'contrato final depende dos gates da vertical'))
}

# Scripts de tabela e responsabilidades.
$tableDecision = @{
    '001'=@('SUBSTITUTE','core.coletas','V2-009a/V2-010'); '002'=@('SUBSTITUTE','core.fretes','V2-009a/V2-011');
    '003'=@('SUBSTITUTE','core.manifestos + filhos','V2-009b/V2-026'); '004'=@('SUBSTITUTE','core.cotacoes','V2-027');
    '005'=@('SUBSTITUTE','core.localizacao_cargas','V2-028'); '006'=@('SUBSTITUTE','core.contas_a_pagar + filhos','V2-029');
    '007'=@('SUBSTITUTE','core.faturas_por_cliente + crosswalks','V2-030'); '008'=@('PRESERVE','ref.calendario','V2-035a');
    '009'=@('CONSOLIDATE','ctl.execution','V2-020'); '010'=@('CONSOLIDATE','ctl.page_audit','V2-020');
    '011'=@('SUBSTITUTE','core.usuario','V2-033/V2-035b'); '012'=@('CONSOLIDATE','ctl.execution','V2-020');
    '013'=@('RETIRE','nenhum; temporário sem PK','V2-019'); '014'=@('CONSOLIDATE','ctl.execution_event','V2-020');
    '015'=@('SUBSTITUTE','ctl.partition_ledger','V2-020'); '016'=@('CONSOLIDATE','core.usuario + core.usuario_history','V2-033');
    '017'=@('PRESERVE','core.usuario_history','V2-033'); '018'=@('RETIRE','flyway_schema_history + fingerprint','V2-019');
    '019'=@('SUBSTITUTE','stg/recon.quarantine','V2-021'); '020'=@('SUBSTITUTE','core.inventario + filhos','V2-031');
    '021'=@('SUBSTITUTE','core.sinistros + relações','V2-032'); '022'=@('CONSOLIDATE','ctl.execution/idempotency','V2-020');
    '023'=@('SUBSTITUTE','recon.quarantine','V2-021'); '024'=@('CONDITIONAL','core.raster_viagens','V2-034a/V2-034b');
    '025'=@('CONDITIONAL','core.raster_paradas','V2-034a/V2-034b'); '026'=@('PRESERVE','ref.regiao_destino_alias','V2-035a');
    '027'=@('PRESERVE','ref.frota_propria_documento','V2-035a'); '028'=@('PRESERVE','mart.fato_gestao_fretes','V2-036');
    '029'=@('PRESERVE','mart.fato_gestao_coletores','V2-036'); '030'=@('PRESERVE','mart.fato_fretes_faturamento','V2-036');
    '031'=@('PRESERVE','mart.fato_gestao_faturas','V2-036'); '032'=@('PRESERVE','mart.fato_gestao_manifestos','V2-036');
    '033'=@('PRESERVE','ref.atribuicao_filial','V2-035a'); '034'=@('PRESERVE','ref.regiao_logistica_cep + ref.regiao_logistica_cidade_uf','V2-035a');
    '035'=@('PRESERVE','ref.status_coleta','V2-035a')
}
$tableFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\tabelas') -File -Filter '*.sql' | Sort-Object Name
foreach ($file in $tableFiles) {
    $number = $file.BaseName.Substring(0, 3)
    $classification = $tableDecision[$number]
    $tableStatus = if ($number -in @('011','016','017')) {
        'IMPLEMENTED_IN_SHADOW'
    } elseif ($number -in @('008','035')) {
        'IMPLEMENTED_OFFLINE_CANDIDATE'
    } elseif ($number -in @('026','027','033','034')) {
        'OFFLINE_SCHEMA_READY_EXTERNAL_INPUT_REQUIRED'
    } else {
        'CLASSIFIED'
    }
    $artifacts.Add((New-ArtifactRow -Type 'table_script' -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility $file.BaseName.Substring(4) -Decision $classification[0] -Destination $classification[1] -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $classification[2] -Status $tableStatus -Evidence 'DDL legado; responsabilidade, não modelo a copiar' -PublicationBlocked 'YES' -Notes $(if ($number -eq '016') { 'ALTER; não cria a 35ª tabela final' } elseif ($number -in @('008','026','027','033','034','035')) { 'V008 modela o contrato tipado sem baseline/default produtivo' } else { '' })))
}

# Migrations, índices, constraints e seeds.
$migrationFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\migrations') -Recurse -File -Filter '*.sql' | Sort-Object FullName
foreach ($file in $migrationFiles) {
    $archived = $file.FullName -match '[\\/]historico_arquivado[\\/]'
    $decision = if ($archived) { 'RETIRE' } else { 'SUBSTITUTE' }
    $destination = if ($archived) { 'evidência histórica; não executar' } else { 'migration Flyway limpa da responsabilidade dona' }
    $artifacts.Add((New-ArtifactRow -Type 'migration' -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'evolução legada de schema/regra' -Decision $decision -Destination $destination -Owner 'PLATAFORMA_DE_DADOS' -DueGate 'V2-019/vertical-dona' -Status 'CLASSIFIED' -Evidence '58 arquivos; 47 arquivados + 11 ativos; 003 ausente' -PublicationBlocked 'YES' -Notes 'não copiar a cadeia 001-059'))
}

$indexFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\indices') -File -Filter '*.sql' | Sort-Object Name
foreach ($file in $indexFiles) {
    $count = ([regex]::Matches([System.IO.File]::ReadAllText($file.FullName), '(?i)\bCREATE\s+(?:UNIQUE\s+)?(?:NONCLUSTERED\s+|CLUSTERED\s+)?INDEX\b')).Count
    $indexNotes = if ($file.BaseName.StartsWith('001_')) { 'inclui subset Raster, que permanece condicional' } else { 'sem subset Raster; redesenhar por workload/plano' }
    $artifacts.Add((New-ArtifactRow -Type 'index_group' -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility "$count índices legados" -Decision 'SUBSTITUTE' -Destination 'índices derivados de workload/plano por migration' -Owner 'DBA_E_PLATAFORMA_DE_DADOS' -DueGate 'V2-009d/V2-050' -Status 'CLASSIFIED' -Evidence 'contagem estática de CREATE INDEX' -PublicationBlocked 'YES' -Notes $indexNotes))
}

# Índices UNIQUE embutidos em DDLs de tabela têm semântica de grão própria e
# não podem desaparecer dentro da classificação genérica do script de tabela.
foreach ($file in $tableFiles) {
    $number = $file.BaseName.Substring(0, 3)
    $text = Remove-SqlComments -Text ([System.IO.File]::ReadAllText($file.FullName))
    foreach ($match in [regex]::Matches($text, '(?is)\bCREATE\s+UNIQUE\s+(?:NONCLUSTERED\s+|CLUSTERED\s+)?INDEX\s+\[?(?<name>[A-Za-z0-9_]+)\]?')) {
        $decision = if ($number -eq '010') { 'CONSOLIDATE' } elseif ($number -match '^0(28|30|31)$') { 'CONDITIONAL' } else { 'PRESERVE' }
        $destination = if ($number -eq '010') { 'ctl.page_audit unique request/page identity' } elseif ($number -eq '033') { 'ref.atribuicao_filial unique active payer rule' } else { 'mart unique grain pending proof/ADR' }
        $gate = if ($number -eq '010') { 'V2-020/V2-009d' } elseif ($number -eq '033') { 'V2-035a/V2-009d' } else { 'V2-036/V2-009d' }
        $notes = if ($number -match '^0(28|30|31)$') { 'bloqueado: chave física diverge do loader/validator legado; provar grão/cardinalidade por ADR' } else { 'preservar a semântica de unicidade, não o DDL físico' }
        $artifacts.Add((New-ArtifactRow -Type 'embedded_unique_index' -Id ($file.BaseName + ':' + $match.Groups['name'].Value) -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'unicidade/grão embutido no DDL' -Decision $decision -Destination $destination -Owner 'DBA_E_PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $gate -Status $(if ($decision -eq 'CONDITIONAL') {'UNRESOLVED_GRAIN_WITH_GATE'} else {'CLASSIFIED'}) -Evidence 'CREATE UNIQUE INDEX embutido no script de tabela' -PublicationBlocked 'YES' -Notes $notes))
    }
}

foreach ($file in $tableFiles | Where-Object { $_.BaseName.Substring(0,3) -ne '016' }) {
    $text = Remove-SqlComments -Text ([System.IO.File]::ReadAllText($file.FullName))
    $named = [regex]::Matches($text, '(?is)\bCONSTRAINT\s+\[?(?<name>[A-Za-z0-9_]+)\]?\s+(?<kind>PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK|DEFAULT)')
    $constraintNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $namedKindOffsets = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($match in $named) {
        [void]$namedKindOffsets.Add($match.Groups['kind'].Index)
        if (-not $constraintNames.Add($match.Groups['name'].Value)) { continue }
        $kind = [regex]::Replace($match.Groups['kind'].Value.ToUpperInvariant(), '\s+', '_')
        $conditional = $file.BaseName.StartsWith('024_') -or $file.BaseName.StartsWith('025_')
        $retired = $file.BaseName -match '^0(13|18)_'
        $decision = if ($retired) { 'RETIRE' } elseif ($conditional) { 'CONDITIONAL' } else { 'SUBSTITUTE' }
        $gate = if ($retired) { 'V2-019' } elseif ($conditional) { 'V2-034a/V2-034b' } else { 'V2-009d/vertical-dona' }
        $constraintDestination = if ($retired) { 'nenhum equivalente físico; responsabilidade absorvida pelo control plane/Flyway V2' } else { 'constraint V2 com teste positivo/negativo e cardinalidade provada' }
        $constraintNotes = if ($retired) { 'retirar junto com a tabela legada; não preservar constraint física ou semântica obsoleta' } else { 'default/check comprovado pode ser preservado semanticamente; DDL é redesenhado' }
        $artifacts.Add((New-ArtifactRow -Type 'constraint' -Id ($file.BaseName + ':' + $match.Groups['name'].Value) -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility $kind -Decision $decision -Destination $constraintDestination -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $gate -Status 'CLASSIFIED' -Evidence 'constraint nomeada no DDL' -PublicationBlocked 'YES' -Notes $constraintNotes))
    }
    $columns = Get-CreateTableColumns -Path $file.FullName
    foreach ($column in $columns | Where-Object { $_.Definition -match '(?i)\bPRIMARY\s+KEY\b' }) {
        $id = $file.BaseName + ':INLINE_PK:' + $column.Name
        if (-not ($artifacts | Where-Object artifact_id -eq $id)) {
            $artifacts.Add((New-ArtifactRow -Type 'constraint' -Id $id -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'PRIMARY_KEY_INLINE' -Decision $(if ($file.BaseName -match '^02[45]_') { 'CONDITIONAL' } else { 'SUBSTITUTE' }) -Destination 'identity/PK V2 provada' -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate 'V2-009d/vertical-dona' -Status 'CLASSIFIED' -Evidence 'definição inline no DDL' -PublicationBlocked 'YES' -Notes 'chave física legada não é prova automática de identidade canônica'))
        }
    }
    foreach ($column in $columns) {
        $inlineKinds = [System.Collections.Generic.List[string]]::new()
        if ($column.Definition -match '(?i)\bDEFAULT\b' -and $column.Definition -notmatch '(?i)\bCONSTRAINT\s+[A-Za-z0-9_]+\s+DEFAULT\b') { $inlineKinds.Add('DEFAULT') }
        if ($column.Definition -match '(?i)\bUNIQUE\b' -and $column.Definition -notmatch '(?i)\bCONSTRAINT\s+[A-Za-z0-9_]+\s+UNIQUE\b') { $inlineKinds.Add('UNIQUE') }
        if ($column.Definition -match '(?i)\bCHECK\b' -and $column.Definition -notmatch '(?i)\bCONSTRAINT\s+[A-Za-z0-9_]+\s+CHECK\b') { $inlineKinds.Add('CHECK') }
        if ($column.Definition -match '(?i)\bREFERENCES\b' -and $column.Definition -notmatch '(?i)\bCONSTRAINT\s+[A-Za-z0-9_]+\s+FOREIGN\s+KEY\b') { $inlineKinds.Add('FOREIGN_KEY') }
        foreach ($kind in $inlineKinds) {
            $id = $file.BaseName + ':INLINE_' + $kind + ':' + $column.Name
            $conditional = $file.BaseName -match '^02[45]_'
            $retired = $file.BaseName -match '^0(13|18)_'
            $artifacts.Add((New-ArtifactRow -Type 'constraint' -Id $id -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility ($kind + '_INLINE') -Decision $(if ($retired) {'RETIRE'} elseif ($conditional) {'CONDITIONAL'} else {'SUBSTITUTE'}) -Destination $(if ($retired) {'nenhum equivalente físico; responsabilidade absorvida pelo control plane/Flyway V2'} else {'constraint V2 com semântica, cardinalidade e testes explícitos'}) -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $(if ($retired) {'V2-019'} elseif ($conditional) {'V2-034a/V2-034b'} else {'V2-009d/vertical-dona'}) -Status 'CLASSIFIED' -Evidence 'definição inline no CREATE TABLE' -PublicationBlocked 'YES' -Notes $(if ($retired) {'retirar junto com a tabela legada'} else {'constraint física legada é evidência, não default automático'})))
        }
    }
    $anonymousTableConstraints = [regex]::Matches($text, '(?im)^\s*(?<kind>PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK)\b')
    $anonymousOrdinal = 0
    foreach ($match in $anonymousTableConstraints) {
        # A palavra-chave de uma constraint nomeada pode começar na linha seguinte;
        # nesse caso a regex de linha não representa outra constraint anônima.
        if ($namedKindOffsets.Contains($match.Groups['kind'].Index)) { continue }
        $anonymousOrdinal++
        $kind = [regex]::Replace($match.Groups['kind'].Value.ToUpperInvariant(), '\s+', '_')
        $id = $file.BaseName + ':ANONYMOUS_' + $kind + ':' + ('{0:D3}' -f $anonymousOrdinal)
        $conditional = $file.BaseName -match '^02[45]_'
        $retired = $file.BaseName -match '^0(13|18)_'
        $artifacts.Add((New-ArtifactRow -Type 'constraint' -Id $id -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility ($kind + '_TABLE_LEVEL') -Decision $(if ($retired) {'RETIRE'} elseif ($conditional) {'CONDITIONAL'} else {'SUBSTITUTE'}) -Destination 'constraint V2 nomeada e testada' -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $(if ($retired) {'V2-019'} elseif ($conditional) {'V2-034a/V2-034b'} else {'V2-009d/vertical-dona'}) -Status 'CLASSIFIED' -Evidence 'constraint anônima no DDL' -PublicationBlocked 'YES' -Notes 'nome e físico serão substituídos; preservar somente semântica provada'))
    }
}
$alterTableFile = $tableFiles | Where-Object { $_.BaseName.Substring(0,3) -eq '016' } | Select-Object -First 1
if ($null -ne $alterTableFile) {
    $alterText = Remove-SqlComments -Text ([System.IO.File]::ReadAllText($alterTableFile.FullName))
    $alterConstraintNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($match in [regex]::Matches($alterText, '(?is)\bCONSTRAINT\s+\[?(?<name>[A-Za-z0-9_]+)\]?\s+(?<kind>PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK|DEFAULT)')) {
        if (-not $alterConstraintNames.Add($match.Groups['name'].Value)) { continue }
        $kind = [regex]::Replace($match.Groups['kind'].Value.ToUpperInvariant(), '\s+', '_')
        $artifacts.Add((New-ArtifactRow -Type 'constraint' -Id ($alterTableFile.BaseName + ':' + $match.Groups['name'].Value) -SourcePath (Get-RelativeLegacyPath $alterTableFile.FullName) -Responsibility $kind -Decision 'SUBSTITUTE' -Destination 'constraint de estado de Usuários na migration V2' -Owner 'SEGURANCA_E_PLATAFORMA_DE_DADOS' -DueGate 'V2-033/V2-009d' -Status 'CLASSIFIED' -Evidence 'constraint nomeada no ALTER legado' -PublicationBlocked 'YES' -Notes 'script 016 altera a responsabilidade 011/017'))
    }
}

$seedRows = @(
    @('calendar','database/tabelas/008_criar_tabela_dim_calendario.sql','calendário rolante','PRESERVE','ref.calendario + ref.ufn_calendar_seed_candidate_v1','V2-035a'),
    @('destination_region_alias','database/tabelas/026_criar_tabela_localizacao_cargas_regiao_destino_alias.sql','alias de região de destino','PRESERVE','ref.filial_operacional + ref.regiao_destino_alias','V2-035a'),
    @('branch_attribution','database/tabelas/033_criar_tabela_regras_atribuicao_filial.sql','pagador → filial vigente','PRESERVE','ref.atribuicao_filial','V2-035a'),
    @('pick_status_catalog','database/tabelas/035_criar_tabela_dim_status_coleta.sql','status bruto/canônico/label/terminal','PRESERVE','ref.status_coleta + ref.v_status_coleta_seed_candidate_v1','V2-035a'),
    @('owned_fleet_documents','database/tabelas/027_criar_tabela_manifestos_frota_propria_cnpjs.sql','documentos e política de classificação de frota','PRESERVE','ref.frota_propria_documento + ref.classificacao_frota_alias + ref.classificacao_frota_matriz + ref.classificacao_frota_excecao_token','V2-035a'),
    @('logistics_regions','database/tabelas/034_criar_tabela_dim_regiao_logistica_rules.sql','CEP ou cidade/UF vigente','PRESERVE','ref.regiao_logistica_cep + ref.regiao_logistica_cidade_uf','V2-035a'),
    @('tariffs_and_financial_rules','database/procedures/001_criar_sp_carga_fato_gestao_vista_fretes.sql; database/procedures/002_criar_sp_carga_fato_gestao_vista_coletores.sql; database/procedures/003_criar_sp_carga_fato_fretes_faturamento.sql; database/procedures/004_criar_sp_carga_fato_gestao_vista_faturas.sql; database/procedures/005_criar_sp_carga_fato_gestao_vista_manifestos.sql','tarifas, pagadores, documentos e classificações hardcoded','SUBSTITUTE','ref.filial_operacional_documento + ref.pagador_exclusao_cubagem + ref.tarifa_rota_uf + política tipada de classificação de frota','V2-035a'),
    @('mutable_external_reference_baseline','external-owner-input','conteúdo produtivo mutável não inferível do Git','CONDITIONAL','baseline autorizado e sanitizado','V2-035a/V2-045')
)
foreach ($entry in $seedRows) {
    $deterministicCandidate = $entry[0] -in @('calendar','pick_status_catalog')
    $externalContent = -not $deterministicCandidate
    $seedSource = $entry[1]
    $artifacts.Add((New-ArtifactRow -Type 'seed_responsibility' -Id $entry[0] -SourcePath $seedSource -Responsibility $entry[2] -Decision $entry[3] -Destination $entry[4] -Owner 'DATA_OWNER_E_PLATAFORMA_DE_DADOS' -DueGate $entry[5] -Status $(if ($externalContent) { 'OFFLINE_SCHEMA_READY_EXTERNAL_INPUT_REQUIRED' } else { 'IMPLEMENTED_OFFLINE_CANDIDATE' }) -Evidence 'SQL legado auditado sem copiar valores produtivos; V008 + manifesto governado' -PublicationBlocked 'YES' -Notes $(if ($externalContent) {'estrutura offline concluída; conteúdo produtivo exige export, fingerprint e ratificação autorizados'} else {'candidato determinístico inerte; produção exige ratificação explícita'})))
}

# Validações, procedures, views, auxiliares e launchers Windows.
$validationConsolidate = @('027','028','030','032','035','036','037','044','047','049')
$validationFact = @('038','039','040','041','045')
$validationRetire = @('031')
$validationFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\validacao') -File -Filter '*.sql' | Sort-Object Name
foreach ($file in $validationFiles) {
    $number = $file.BaseName.Substring(0,3)
    if ($file.Name -eq '042_validar_contrato_dashboard_performance.sql') { $decision = 'RETIRE'; $destination = 'consumer contract externo; somente asserção core comprovada'; $gate = 'V2-037' }
    elseif ($number -in $validationRetire) { $decision = 'RETIRE'; $destination = 'nenhum; operação destrutiva proibida'; $gate = 'V2-019' }
    elseif ($number -in $validationFact) { $decision = 'SUBSTITUTE'; $destination = 'gate de fato/mart'; $gate = 'V2-036/V2-038/V2-050' }
    elseif ($number -in $validationConsolidate) { $decision = 'CONSOLIDATE'; $destination = 'DQ/paridade/manifesto'; $gate = 'V2-012/V2-038/V2-050' }
    else { $decision = 'SUBSTITUTE'; $destination = 'manifesto/contract test/gate de schema'; $gate = 'V2-019/V2-037/V2-038' }
    $artifacts.Add((New-ArtifactRow -Type 'validation' -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'validação SQL legada' -Decision $decision -Destination $destination -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_DOMINIO' -DueGate $gate -Status 'CLASSIFIED' -Evidence '24 scripts; prefixo 042 duplicado' -PublicationBlocked 'YES' -Notes 'não executar no legado durante este bloco'))
}

$procedureFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\procedures') -File -Filter '*.sql' | Sort-Object Name
foreach ($file in $procedureFiles) {
    $artifacts.Add((New-ArtifactRow -Type 'procedure' -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'materialização de fato + sweep acoplado' -Decision 'SUBSTITUTE' -Destination 'procedure set-based V2 por fato; materialização e sweep separados' -Owner 'PLATAFORMA_DE_DADOS_E_OWNER_DO_FATO' -DueGate 'V2-036' -Status 'CLASSIFIED' -Evidence 'janela/mode implícitos e @MarcarAusentesComoExcluidos auditados' -PublicationBlocked 'YES' -Notes 'full/backfill enumera partições explícitas'))
}

$viewFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\views') -File -Filter '*.sql'
    Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'database\views-dimensao') -File -Filter '*.sql'
) | Sort-Object FullName
foreach ($file in $viewFiles) {
    $isWrapper = $file.BaseName -match '^02[34]_publicar_wrappers_'
    if ($isWrapper) { $type = 'view_wrapper'; $decision = 'RETIRE'; $destination = 'endpoint/alias externo, sem DDL cross-database'; $gate = 'V2-037/V2-048a' }
    elseif ($file.BaseName -match 'bi_monitoramento') { $type = 'view_etl'; $decision = 'CONSOLIDATE'; $destination = 'ctl/recon; não público por default'; $gate = 'V2-020/V2-037' }
    elseif ($file.BaseName -match 'raster') { $type = 'view_etl'; $decision = 'CONDITIONAL'; $destination = 'pub.vw_raster_sm_transit_time'; $gate = 'V2-034a/V2-034b/V2-037' }
    else { $type = 'view_etl'; $decision = 'PRESERVE'; $destination = 'pub contrato a ratificar por consumidor'; $gate = 'V2-037' }
    $artifacts.Add((New-ArtifactRow -Type $type -Id $file.BaseName -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'contrato SQL/view' -Decision $decision -Destination $destination -Owner 'BI_OU_CONSUMIDOR_E_PLATAFORMA_DE_DADOS' -DueGate $gate -Status $(if ($isWrapper) { 'CLASSIFIED' } else { 'CONSUMER_MANIFEST_PENDING' }) -Evidence 'DDL da view; dashboard não consultado' -PublicationBlocked 'YES' -Notes 'nome, grão, aliases e filtros são candidatos até aceite externo'))
}

$auxRows = @(
    @('database/reprocessamento/001_reprocessar_fato_manifestos_competencia_operacional.sql','SUBSTITUTE','comando governado com modo/janela explícitos','V2-023/V2-036'),
    @('database/seguranca/024_configurar_permissoes_usuario.sql','SUBSTITUTE','roles/grants Flyway + testes negativos','V2-019/V2-038'),
    @('database/security_sqlite/001_init_auth_schema.sqlite.sql','RETIRE','identidade de serviço/RBAC no boundary real','V2-042')
)
foreach ($entry in $auxRows) {
    $artifacts.Add((New-ArtifactRow -Type 'sql_auxiliary' -Id ([System.IO.Path]::GetFileNameWithoutExtension($entry[0])) -SourcePath $entry[0] -Responsibility 'SQL auxiliar legado' -Decision $entry[1] -Destination $entry[2] -Owner 'PLATAFORMA_DE_DADOS_E_SEGURANCA' -DueGate $entry[3] -Status 'CLASSIFIED' -Evidence 'arquivo auditado offline' -PublicationBlocked 'YES' -Notes ''))
}

$windowsDecision = @{
    '00-PRODUCAO_START.bat'=@('RETIRE','menu monolítico removido');
    '01-executar_extracao_completa.bat'=@('SUBSTITUTE','launcher fino opcional para one-shot');
    '02-testar_api_especifica.bat'=@('CONDITIONAL','ferramenta administrativa read-only');
    '03-validar_config.bat'=@('SUBSTITUTE','config validate/preflight tipado');
    '04-extracao_por_intervalo.bat'=@('SUBSTITUTE','run/backfill tipado');
    '05-loop_extracao_30min.bat'=@('RETIRE','scheduler externo + lease SQL');
    '06-relatorio-completo-validacao.bat'=@('CONSOLIDATE','gates/harness V2');
    '07-exportar_csv.bat'=@('CONDITIONAL','ferramenta administrativa protegida');
    '08-auditar_api.bat'=@('CONDITIONAL','probe de contrato separado');
    '09-gerenciar_usuarios.bat'=@('RETIRE','identidade/RBAC no boundary real');
    '10-expurgo-orfaos-noturno.ps1'=@('SUBSTITUTE','sweep one-shot agendado externamente');
    '11-retrofit.bat'=@('CONSOLIDATE','bootstrap/backfill/replay tipado');
    '15-fechamento-mensal.ps1'=@('SUBSTITUTE','fechamento one-shot com mês explícito');
    'limpar_logs.bat'=@('RETIRE','lifecycle/retention governados');
    'verificar_execucao_ativa.ps1'=@('SUBSTITUTE','status/lease/heartbeat no ctl')
}
$windowsEvidence = @{
    '00-PRODUCAO_START.bat'='menu monolítico de 1.011 linhas: autenticação SQLite, jobs e flags exclusivas do wrapper; múltiplos boundaries operacionais'
    '01-executar_extracao_completa.bat'='compila se necessário e executa o JAR com --fluxo-completo, interpretando exit code parcial'
    '02-testar_api_especifica.bat'='menu/argumentos escolhem GraphQL, Data Export ou Raster e entidade para comando de teste'
    '03-validar_config.bat'='executa auth-check e --validar; verifica conexão/pool/schema mínimo de tabelas+procedures e apenas anuncia APIs como OK'
    '04-extracao_por_intervalo.bat'='valida datas e chama --extracao-intervalo com API/entidade e modo rápido opcionais'
    '05-loop_extracao_30min.bat'='gerencia loop Java de 60 minutos em background com PID/state/log e start/status/stop'
    '06-relatorio-completo-validacao.bat'='chama --validar-etl-extremo com período fechado/stress e opções de idempotência/hidratação'
    '07-exportar_csv.bat'='menu chama --exportar-csv para todas as tabelas ou tabela escolhida e produz arquivo local'
    '08-auditar_api.bat'='chama --auditar-api e gera relatório CSV de campos das entidades'
    '09-gerenciar_usuarios.bat'='expõe bootstrap/create/reset/disable/info da autenticação SQLite local'
    '10-expurgo-orfaos-noturno.ps1'='chama --expurgo-orfaos batch 500 e, mesmo se falhar, executa as cinco procedures de fatos; java -version 2>&1 com ErrorActionPreference=Stop é incompatível com PowerShell 5.1'
    '11-retrofit.bat'='usa database/config.bat, auth/safety gate e --extracao-intervalo --retrofit aditivo sem prune'
    '15-fechamento-mensal.ps1'='executa --fechamento-mensal one-shot com log, event source e failure marker próprios'
    'limpar_logs.bat'='apaga logs após checagem de PID; /force só ignora o bloqueio do daemon e /full acrescenta temporários de target e last_run'
    'verificar_execucao_ativa.ps1'='inspeciona PID/state e command line de java/javaw para inferir execução ativa'
}
$windowsFiles = Get-ChildItem -LiteralPath (Join-Path $legacyRootResolved 'scripts\windows') -File | Sort-Object Name
foreach ($file in $windowsFiles) {
    $classification = $windowsDecision[$file.Name]
    $scriptEvidence = $windowsEvidence[$file.Name]
    if ([string]::IsNullOrWhiteSpace($scriptEvidence)) { throw "Evidência Windows ausente para $($file.Name)." }
    $artifacts.Add((New-ArtifactRow -Type 'windows_script' -Id $file.Name -SourcePath (Get-RelativeLegacyPath $file.FullName) -Responsibility 'launcher/job legado' -Decision $classification[0] -Destination $classification[1] -Owner 'OPERACOES_E_PLATAFORMA' -DueGate 'V2-018/V2-022/V2-023/V2-045' -Status 'CLASSIFIED' -Evidence $scriptEvidence -PublicationBlocked 'YES' -Notes 'scripts V2 serão launchers finos; nenhum daemon/PID'))
}
foreach ($alias in @('00-PRODUCAO_START.bat','limpar_logs.bat')) {
    $aliasPath = Join-Path $legacyRootResolved $alias
    if (Test-Path -LiteralPath $aliasPath) {
        $artifacts.Add((New-ArtifactRow -Type 'windows_alias' -Id ('root:' + $alias) -SourcePath (Get-RelativeLegacyPath $aliasPath) -Responsibility 'forwarder para scripts/windows' -Decision 'RETIRE' -Destination 'nenhum' -Owner 'OPERACOES_E_PLATAFORMA' -DueGate 'V2-022' -Status 'CLASSIFIED' -Evidence 'alias estático fora da contagem de 15 scripts primários' -PublicationBlocked 'YES' -Notes ''))
    }
}
$databaseRunner = Join-Path $legacyRootResolved 'database\executar_database.bat'
if (Test-Path -LiteralPath $databaseRunner) {
    $artifacts.Add((New-ArtifactRow -Type 'database_runner' -Id 'executar_database.bat' -SourcePath (Get-RelativeLegacyPath $databaseRunner) -Responsibility 'executor manual e incompleto de schema' -Decision 'SUBSTITUTE' -Destination 'Flyway + manifesto/fingerprint' -Owner 'DBA_E_PLATAFORMA_DE_DADOS' -DueGate 'V2-019' -Status 'CLASSIFIED' -Evidence 'omite tabela 035, índice 004, view 014 e validações 027/030/031/035/037/042_auditar/044/046/047/048/049; depende indiretamente das migrations 053/055/056' -PublicationBlocked 'YES' -Notes 'database/config.bat não foi lido'))
}

# Data Export: 442 slots /info preservam somente contagem+ordinal, pois os nomes atuais
# não foram versionados. Caminhos /data candidatos ficam em linhas separadas e nunca
# são promovidos a /info por correspondência de nome.
$dataExportSpecs = @(
    [pscustomobject]@{ Entity='coletas'; Template='6908'; Root='picks'; Count=31; Dto=$null; Known=@('id','sequence_code','updated_at','pck_mik_mft_crn_psn_nickname','pck_mik_mft_sequence_code','pck_mik_mft_vie_license_plate','pck_mik_mft_vie_vee_name') },
    [pscustomobject]@{ Entity='fretes'; Template='6389'; Root='freights'; Count=109; Dto='src\main\java\br\com\extrator\dominio\dataexport\fretes\FreteIndicadorDTO.java'; Known=@('id','updated_at','reference_number','fit_p_m_pck_sequence_code') },
    [pscustomobject]@{ Entity='manifestos'; Template='6399'; Root='manifests'; Count=91; Dto='src\main\java\br\com\extrator\dominio\dataexport\manifestos\ManifestoDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='cotacoes'; Template='6906'; Root='quotes'; Count=37; Dto='src\main\java\br\com\extrator\dominio\dataexport\cotacao\CotacaoDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='localizacao_cargas'; Template='8656'; Root='freights'; Count=24; Dto='src\main\java\br\com\extrator\dominio\dataexport\localizacaocarga\LocalizacaoCargaDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='contas_a_pagar'; Template='8636'; Root='accounting_debits'; Count=28; Dto='src\main\java\br\com\extrator\dominio\dataexport\contasapagar\ContasAPagarDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='faturas_por_cliente'; Template='4924'; Root='freights'; Count=52; Dto='src\main\java\br\com\extrator\dominio\dataexport\faturaporcliente\FaturaPorClienteDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='inventario'; Template='10633'; Root='check_in_orders'; Count=26; Dto='src\main\java\br\com\extrator\dominio\dataexport\inventario\InventarioDTO.java'; Known=@() },
    [pscustomobject]@{ Entity='sinistros'; Template='6392'; Root='insurance_claims'; Count=44; Dto='src\main\java\br\com\extrator\dominio\dataexport\sinistros\SinistroDTO.java'; Known=@() }
)
$knownSourceFields = @{}
$dtoFieldTypes = @{}
foreach ($spec in $dataExportSpecs) {
    $names = [System.Collections.Generic.List[string]]::new()
    $types = @{}
    foreach ($known in $spec.Known) { if (-not $names.Contains($known)) { $names.Add($known) } }
    if ($null -ne $spec.Dto) {
        $dtoPath = Join-Path $legacyRootResolved $spec.Dto
        $dtoText = [System.IO.File]::ReadAllText($dtoPath)
        foreach ($match in [regex]::Matches($dtoText, '(?s)@JsonProperty\("(?<name>[^"\r\n]+)"\)\s*(?:@[A-Za-z0-9_$.]+\([^\r\n]*\)\s*)*private\s+(?<type>[A-Za-z0-9_$.<>?,\[\]\s]+?)\s+[A-Za-z_][A-Za-z0-9_]*\s*;')) {
            $name = $match.Groups['name'].Value
            if (-not $names.Contains($name)) { $names.Add($name) }
            $types[$name] = [regex]::Replace($match.Groups['type'].Value.Trim(), '\s+', ' ')
        }
    }
    if ($names.Count -gt $spec.Count) { throw "DTO/campos conhecidos excedem /info para $($spec.Entity): $($names.Count) > $($spec.Count)." }
    $knownSourceFields[$spec.Entity] = @($names)
    $dtoFieldTypes[$spec.Entity] = $types
    for ($i = 0; $i -lt $spec.Count; $i++) {
        $slotName = '__unresolved_info_field_{0:D3}' -f ($i + 1)
        $fields.Add((New-FieldRow -RowId ('DE-INFO-{0}-{1:D3}' -f $spec.Template, ($i + 1)) -RowKind 'DATA_EXPORT_FIELD' -Entity $spec.Entity -SourceContract 'ESL_DATA_EXPORT_INFO' -Template $spec.Template -SourceRoot $spec.Root -SourcePath ('/info/' + $slotName) -SourceType 'UNVERIFIED_BY_INFO' -Presence 'OBSERVED_COUNT_SLOT_NAME_NOT_VERSIONED' -Cardinality 'ROOT_OR_EXPANDED_CHILD_TO_PROVE' -Requested 'YES' -DtoPresence 'UNKNOWN_NO_ORDINAL_CORRESPONDENCE' -LegacyMapping 'NO_IMPLICIT_MATCH_TO_DATA_OR_V1' -V2Zone 'stg' -V2Target ('stg.' + $spec.Entity + '.pending_contract_field_' + ('{0:D3}' -f ($i + 1))) -Transformation 'replace ordinal with sanitized /info fingerprint in V2-025d; no name inference' -TimeZone 'UNRESOLVED_BY_FIELD' -Reducer 'UNRESOLVED_BY_FIELD' -Unit 'UNRESOLVED_BY_FIELD' -Currency 'UNRESOLVED_BY_FIELD' -PrecisionScale 'UNRESOLVED_BY_FIELD' -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass 'INTERNAL_OPERATIONAL' -RetentionPolicy 'R-STG-CANDIDATE' -Consumer ('vertical ' + $spec.Entity) -Decision 'PRESERVE' -Owner (Get-OwnerForEntity $spec.Entity) -Status 'UNRESOLVED_SOURCE_PATH' -DueGate 'V2-041->V2-025d/vertical-contract' -Evidence ("contagem sanitizada /info=$($spec.Count); nomes técnicos atuais não foram versionados") -Fixture 'fingerprint /info sanitizado pendente' -PublicationBlocked 'YES'))
    }
    for ($i = 0; $i -lt $names.Count; $i++) {
        $fieldName = $names[$i]
        $fromDto = $null -ne $spec.Dto -and ([System.IO.File]::ReadAllText((Join-Path $legacyRootResolved $spec.Dto)) -match [regex]::Escape('@JsonProperty("' + $fieldName + '")'))
        $explicitSample = $spec.Known -contains $fieldName
        $isManifestos = $spec.Entity -eq 'manifestos'
        $status = if ($isManifestos) { 'LOCAL_STATIC_PATH_AND_REDUCER_DECIDED_P01_V03' } elseif ($explicitSample) { 'OBSERVED_DATA_SAMPLE_PATH' } else { 'LOCAL_DTO_DATA_PATH_CANDIDATE' }
        $evidence = if ($fromDto) { (Get-RelativeLegacyPath (Join-Path $legacyRootResolved $spec.Dto)) } else { 'STATES.md + contrato sanitizado local' }
        if ($isManifestos) {
            $evidence += '; docs/catalogos/identidade-manifestos/manifesto.json; docs/catalogos/manifestos-v2-026/decisao-v03.json'
        }
        $decision = Get-DataFieldDecision -Entity $spec.Entity -Name $fieldName
        $reducer = if ($isManifestos) { Get-ManifestosReducer -Name $fieldName } else { 'FIELD_SPECIFIC_PENDING_VERTICAL' }
        $safeTargetName = [regex]::Replace($fieldName.ToLowerInvariant(), '[^a-z0-9_]+', '_').Trim('_')
        $manifestChildKind = if (-not $isManifestos) { $null } elseif ($fieldName -eq 'mft_pfs_pck_sequence_code') { 'pick' } elseif ($fieldName -in @('mft_mfs_number','mft_mfs_key')) { 'mdfe' } else { $null }
        $cardinality = if ($null -ne $manifestChildKind) { 'ROOT_ZERO_TO_MANY_PHYSICAL_RECORD_ZERO_OR_ONE_FAIL_CLOSED' } elseif ($isManifestos) { 'ROOT_SCALAR_TRI_STATE_ONE_OBSERVATION_PER_PHYSICAL_RECORD' } elseif ($decision -eq 'SPLIT') { 'CHILD_OR_RELATION_TO_MODEL' } else { 'ROOT_SCALAR_OR_ALIAS_TO_PROVE' }
        $v2Target = if ($manifestChildKind -eq 'pick') { 'stg.manifestos.pick_child_observation.' + $safeTargetName } elseif ($manifestChildKind -eq 'mdfe') { 'stg.manifestos.mdfe_child_observation.' + $safeTargetName } elseif ($isManifestos) { 'stg.manifestos.root_observation.' + $safeTargetName } else { 'stg.' + $spec.Entity + '.' + $safeTargetName }
        $transformation = if ($fieldName -eq 'mft_pfs_pck_sequence_code' -and $isManifestos) { 'preserve typed root-scoped pick child observation and V2-046a candidate; never materialize relation' } elseif ($fieldName -eq 'mft_mfs_key' -and $isManifestos) { 'validate exact 44 ASCII digits and register root-scoped MDF-e child observation; never combine number into identity' } elseif ($fieldName -eq 'mft_mfs_number' -and $isManifestos) { 'preserve as MDF-e attribute paired with key in the same physical observation; never identity or independent MAX' } elseif ($fieldName -eq 'mdfe_status' -and $isManifestos) { 'preserve ABSENT/NULL/VALUE as root scalar replicated by expansion; never signal or identify an MDF-e child' } elseif ($decision -eq 'SPLIT') { 'split typed root/child with explicit natural components' } elseif ($decision -eq 'ALIAS') { 'preserve as explicit business key/alias; never source ID by inference' } elseif ($decision -eq 'NORMALIZE') { 'preserve raw + normalize typed value with versioned rule' } else { 'typed parse + explicit presence/provenance' }
        $dueGate = if ($isManifestos) { 'V2-026' } else { 'V2-041->V2-025d/vertical-contract' }
        $fields.Add((New-FieldRow -RowId ('DE-DATA-{0}-{1:D3}' -f $spec.Template, ($i + 1)) -RowKind 'DATA_EXPORT_DATA_FIELD' -Entity $spec.Entity -SourceContract 'ESL_DATA_EXPORT_DATA_CANDIDATE' -Template $spec.Template -SourceRoot $spec.Root -SourcePath ('/data/' + $fieldName) -SourceType 'UNVERIFIED_RUNTIME_TYPE' -Presence $(if ($explicitSample) {'OBSERVED_IN_SANITIZED_SAMPLE'} else {'DECLARED_BY_LOCAL_DTO_NOT_CURRENT_FINGERPRINT'}) -Cardinality $cardinality -Requested 'YES_IN_LEGACY_OR_PROBE' -DtoPresence $(if ($fromDto) {'YES'} else {'NO_OR_SIDECAR'}) -LegacyMapping ('candidate field ' + $fieldName + '; reconcile mapper/SQL separately') -V2Zone 'stg' -V2Target $v2Target -Transformation $transformation -TimeZone 'America/Sao_Paulo_WHEN_TEMPORAL' -Reducer $reducer -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'UNRESOLVED_IF_FINANCIAL' -PrecisionScale 'UNRESOLVED_IF_NUMERIC' -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $fieldName) -RetentionPolicy 'R-STG-CANDIDATE' -Consumer ('vertical ' + $spec.Entity) -Decision $decision -Owner (Get-OwnerForEntity $spec.Entity) -Status $status -DueGate $dueGate -Evidence $evidence -Fixture ('sanitized /data fixture for ' + $fieldName) -PublicationBlocked 'YES' -DtoFieldName $(if ($fromDto) {$fieldName} else {''}) -DtoJavaType $(if ($fromDto) {$dtoFieldTypes[$spec.Entity][$fieldName]} else {''})))
    }
}

# Nove tabelas operacionais V1.
$operationalTables = @{
    '001'='coletas'; '002'='fretes'; '003'='manifestos'; '004'='cotacoes'; '005'='localizacao_cargas';
    '006'='contas_a_pagar'; '007'='faturas_por_cliente'; '020'='inventario'; '021'='sinistros'
}
$entityVerticalGates = @{
    'coletas'='V2-010'; 'fretes'='V2-011'; 'manifestos'='V2-026'; 'cotacoes'='V2-027';
    'localizacao_cargas'='V2-028'; 'contas_a_pagar'='V2-029'; 'faturas_por_cliente'='V2-030';
    'inventario'='V2-031'; 'sinistros'='V2-032'
}
$manifestV1SourceOverrides = @{
    'mdfe_number' = 'mft_mfs_number'
    'mdfe_key' = 'mft_mfs_key'
    'mdfe_status' = 'mdfe_status'
    'pick_sequence_code' = 'mft_pfs_pck_sequence_code'
    'vehicle_weight_capacity' = 'mft_vie_weight_capacity'
    'capacidade_kg' = 'mft_vie_weight_capacity'
}
$manifestDecisionEvidence = 'docs/catalogos/identidade-manifestos/manifesto.json; docs/catalogos/manifestos-v2-026/decisao-v03.json'
$entityColumnPosition = @{}
foreach ($file in $tableFiles | Where-Object { $operationalTables.ContainsKey($_.BaseName.Substring(0,3)) }) {
    $entity = $operationalTables[$file.BaseName.Substring(0,3)]
    $entityColumnPosition[$entity] = 0
    foreach ($column in (Get-CreateTableColumns -Path $file.FullName)) {
        $entityColumnPosition[$entity]++
        $sourceFieldOverride = if ($entity -eq 'manifestos' -and $manifestV1SourceOverrides.ContainsKey($column.Name)) {
            [string]$manifestV1SourceOverrides[$column.Name]
        } else {
            $null
        }
        $known = ($knownSourceFields[$entity] -contains $column.Name) -or $null -ne $sourceFieldOverride
        $technical = $column.Name -match '(?i)^(metadata|data_extracao|excluido_na_origem|data_exclusao_origem|ausente_na_origem_desde|confirmacoes_ausencia_origem|ultima_reconciliacao_origem_em|reconciliacao_origem_run_id|motivo_exclusao_origem|hash_|criado_em|atualizado_em)'
        if ($column.Name -eq 'metadata') { $decision = 'RETIRE'; $zone = 'recon'; $target = 'recon.field_presence_and_provenance'; $status = 'RAW_METADATA_COLUMN_RETIRED'; $sourcePath = 'MULTIPLE_UNTYPED_FIELDS' }
        elseif ($entity -eq 'manifestos' -and $column.Name -eq 'id') {
            $decision = 'RETIRE'; $zone = 'recon'; $target = 'recon.manifestos_legacy_surrogate_evidence'
            $status = 'RETIRED_LEGACY_DATABASE_SURROGATE_NOT_SOURCE_IDENTITY'; $sourcePath = 'LEGACY_DATABASE_SURROGATE_NO_SOURCE_PATH'
        }
        elseif ($entity -eq 'manifestos' -and $column.Name -in @('identificador_unico','chave_merge_hash')) {
            $decision = 'RETIRE'; $zone = 'recon'; $target = 'recon.manifestos_legacy_identity_heuristic_evidence'
            $status = 'RETIRED_AS_IDENTITY_LEGACY_EVIDENCE_ONLY'; $sourcePath = 'LEGACY_DERIVED_IDENTIFIER_NO_SOURCE_PATH'
        }
        elseif ($technical) { $decision = 'DERIVE'; $zone = 'ctl'; $target = 'ctl/recon derived audit state'; $status = 'TECHNICAL_COLUMN_CLASSIFIED'; $sourcePath = 'DERIVED_BY_PIPELINE' }
        else {
            $decisionName = if ($null -ne $sourceFieldOverride) { $sourceFieldOverride } else { $column.Name }
            $decision = if ($entity -eq 'manifestos' -and $column.Name -eq 'capacidade_kg') { 'DERIVE' } else { Get-DataFieldDecision -Entity $entity -Name $decisionName }
            $manifestChildKind = if ($entity -ne 'manifestos') { $null } elseif ($decisionName -eq 'mft_pfs_pck_sequence_code') { 'pick' } elseif ($decisionName -in @('mft_mfs_number','mft_mfs_key')) { 'mdfe' } else { $null }
            if ($manifestChildKind -eq 'pick') { $zone = 'core'; $target = 'core.manifestos.pick_child_observation.' + $decisionName }
            elseif ($manifestChildKind -eq 'mdfe') { $zone = 'core'; $target = 'core.manifestos.mdfe_child_observation.' + $decisionName }
            elseif ($entity -eq 'manifestos' -and $known) { $zone = 'core'; $target = 'core.manifestos.root_observation.' + $column.Name }
            elseif ($decision -eq 'SPLIT') { $zone = 'crosswalk'; $target = ('crosswalk.' + $entity + '.pending_relation_' + $column.Name) }
            elseif ($decision -eq 'ALIAS') { $zone = 'crosswalk'; $target = ('crosswalk.' + $entity + '.alias_' + $column.Name) }
            else { $zone = 'core'; $target = ('core.' + $entity + '.' + $(if ($known) {$column.Name} else {'pending_' + $column.Name})) }
            $status = if ($entity -eq 'manifestos' -and $known) { 'LOCAL_STATIC_PATH_AND_REDUCER_DECIDED_P01_V03' } elseif ($known) { 'PROVISIONAL_NAME_MATCH_NOT_SEMANTIC_PROOF' } else { 'UNRESOLVED_BIDIRECTIONAL_MAPPING' }
            $sourcePath = if ($null -ne $sourceFieldOverride) { '/data/' + $sourceFieldOverride } elseif ($known) { '/data/' + $column.Name } else { 'UNRESOLVED_SOURCE_PATH' }
        }
        $dueGate = if ($column.Name -eq 'metadata') { 'V2-021/' + $entityVerticalGates[$entity] } elseif ($technical) { 'V2-020/' + $entityVerticalGates[$entity] } elseif ($entity -eq 'manifestos' -and ($known -or $decision -eq 'RETIRE')) { 'V2-026' } elseif ($known) { 'V2-025d/' + $entityVerticalGates[$entity] } else { 'V2-041->V2-025d/' + $entityVerticalGates[$entity] }
        $transformation = if ($column.Name -eq 'metadata') { 'replace raw metadata with typed presence and provenance; no source reducer' } elseif ($entity -eq 'manifestos' -and $column.Name -in @('id','identificador_unico','chave_merge_hash')) { 'retain only as legacy comparison evidence; never source root child alias or tie breaker identity' } elseif ($technical) { 'derive from immutable execution/publication ledger' } elseif ($entity -eq 'manifestos' -and $column.Name -eq 'pick_sequence_code') { 'preserve typed root-scoped pick child observation and V2-046a candidate; never materialize relation' } elseif ($entity -eq 'manifestos' -and $column.Name -eq 'mdfe_key') { 'validate exact 44 ASCII digits and register root-scoped MDF-e child observation; never combine number into identity' } elseif ($entity -eq 'manifestos' -and $column.Name -eq 'mdfe_number') { 'preserve as MDF-e attribute paired with key in the same physical observation; never identity or independent MAX' } elseif ($entity -eq 'manifestos' -and $column.Name -eq 'mdfe_status') { 'preserve ABSENT/NULL/VALUE as root scalar replicated by expansion; never signal or identify an MDF-e child' } elseif ($entity -eq 'manifestos' -and $column.Name -eq 'capacidade_kg') { 'derive compatible projection from the same reduced mft_vie_weight_capacity signal; never reduce independently' } elseif ($decision -eq 'SPLIT') { 'split root/child observation; relation remains pending V2-046a' } elseif ($decision -eq 'ALIAS') { 'store versioned business alias; never infer source identity' } elseif ($decision -eq 'NORMALIZE') { 'preserve raw and normalize with versioned rule' } else { 'typed mapping with explicit presence/provenance' }
        $reducerName = if ($entity -eq 'manifestos' -and $column.Name -eq 'capacidade_kg') { 'capacidade_kg' } elseif ($null -ne $sourceFieldOverride) { $sourceFieldOverride } else { $column.Name }
        $reducer = if ($entity -ne 'manifestos') { 'FIELD_SPECIFIC_PENDING_VERTICAL' } elseif ($column.Name -eq 'metadata') { 'RAW_METADATA_RETIRED_PRESENCE_PROVENANCE_ONLY_NO_V03_REDUCER' } elseif ($column.Name -eq 'id') { 'LEGACY_DATABASE_SURROGATE_RETIRED_NO_SOURCE_REDUCER' } elseif ($column.Name -in @('identificador_unico','chave_merge_hash')) { 'LEGACY_IDENTITY_HEURISTIC_RETIRED_NO_V03_REDUCER' } elseif ($technical) { 'TECHNICAL_LEDGER_DERIVED_NO_SOURCE_REDUCER' } else { Get-ManifestosReducer -Name $reducerName }
        $cardinality = if ($entity -eq 'manifestos' -and $decision -eq 'SPLIT') { 'ROOT_ZERO_TO_MANY_LEGACY_PHYSICAL_ROW_ZERO_OR_ONE_FAIL_CLOSED' } elseif ($entity -eq 'manifestos' -and ($technical -or $decision -eq 'RETIRE')) { 'NOT_APPLICABLE_TECHNICAL_OR_RETIRED' } elseif ($entity -eq 'manifestos') { 'ONE_ROOT_SCALAR_OBSERVATION_PER_LEGACY_ROW' } elseif ($decision -eq 'SPLIT') { 'ROOT_OR_CHILD_RELATION_TO_PROVE' } else { 'ONE_COLUMN_PER_LEGACY_ROW' }
        $rowEvidence = Get-RelativeLegacyPath $file.FullName
        if ($entity -eq 'manifestos' -and ($known -or $decision -eq 'RETIRE')) { $rowEvidence += '; ' + $manifestDecisionEvidence }
        $fields.Add((New-FieldRow -RowId ('V1-{0}-{1:D3}' -f $entity.ToUpperInvariant(), $entityColumnPosition[$entity]) -RowKind 'V1_OPERATIONAL_COLUMN' -Entity $entity -SourceContract 'V1_SQL_SERVER' -Template 'N/A' -SourceRoot ('dbo.' + $entity) -SourcePath $sourcePath -SourceType $column.Type -Presence $(if ($column.Nullable -eq 'NO') { 'REQUIRED_IN_V1_SQL' } else { 'NULLABLE_IN_V1_SQL' }) -Cardinality $cardinality -Requested 'N/A' -DtoPresence $(if ($known) { 'NAME_PRESENT_IN_DATA_CANDIDATE_NOT_INFO_PROOF' } else { 'TO_RECONCILE' }) -LegacyMapping ('dbo.' + $entity + '.' + $column.Name) -V2Zone $zone -V2Target $target -Transformation $transformation -TimeZone 'America/Sao_Paulo_WHEN_TEMPORAL' -Reducer $reducer -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'UNRESOLVED_IF_FINANCIAL' -PrecisionScale $column.Type -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $column.Name) -RetentionPolicy $(if ($zone -eq 'ctl' -or $zone -eq 'recon') { 'R-AUDIT-APPEND-ONLY' } else { 'R-DOMAIN-OWNER' }) -Consumer 'core + downstream contracts listed in STATES.md' -Decision $decision -Owner (Get-OwnerForEntity $entity) -Status $status -DueGate $dueGate -Evidence $rowEvidence -Fixture ('schema + mapping fixture for ' + $column.Name) -PublicationBlocked 'YES'))
    }
}

# Usuários: 9 colunas current (6 do CREATE + 3 do ALTER) e 9 de histórico.
# A matriz conserva a responsabilidade legada, mas aponta somente para o desenho V007 em sombra.
$userCurrentFile = Join-Path $legacyRootResolved 'database\tabelas\011_criar_tabela_dim_usuarios.sql'
$userAlterFile = Join-Path $legacyRootResolved 'database\tabelas\016_alter_tabela_dim_usuarios_estado.sql'
$userHistoryFile = Join-Path $legacyRootResolved 'database\tabelas\017_criar_tabela_dim_usuarios_historico.sql'
$userCurrent = [System.Collections.Generic.List[object]]::new()
foreach ($column in (Get-CreateTableColumns -Path $userCurrentFile)) { $userCurrent.Add($column) }
foreach ($manual in @(
    [pscustomobject]@{Name='ativo';Type='BIT';Nullable='NO';Definition='ADD ativo BIT NOT NULL'},
    [pscustomobject]@{Name='origem_atualizado_em';Type='DATETIME2';Nullable='YES';Definition='ADD origem_atualizado_em DATETIME2 NULL'},
    [pscustomobject]@{Name='ultima_extracao_em';Type='DATETIME2';Nullable='YES';Definition='ADD ultima_extracao_em DATETIME2 NULL'}
)) { if (-not ($userCurrent | Where-Object Name -eq $manual.Name)) { $userCurrent.Add($manual) } }
$userRows = @(
    $userCurrent | ForEach-Object { [pscustomobject]@{ Table='dim_usuarios'; Column=$_; Evidence=(Get-RelativeLegacyPath $userCurrentFile) + '; ' + (Get-RelativeLegacyPath $userAlterFile) } }
    (Get-CreateTableColumns -Path $userHistoryFile) | ForEach-Object { [pscustomobject]@{ Table='dim_usuarios_historico'; Column=$_; Evidence=(Get-RelativeLegacyPath $userHistoryFile) } }
)
$userMappings = @{
    'dim_usuarios.user_id' = [pscustomobject]@{
        SourcePath='individual.edges.node.id'; Requested='YES'; DtoPresence='YES'; DtoField='id'; DtoType='JsonNode';
        Zone='core'; Target='core.usuario.source_key + core.usuario.usuario_id'; Decision='PROMOTE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='type-tag INTEGER/STRING into scoped source_key; allocate BIGINT IDENTITY usuario_id';
        Reducer='case-sensitive scoped source identity; never name/order/hash as identity'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios.nome' = [pscustomobject]@{
        SourcePath='individual.edges.node.name'; Requested='YES'; DtoPresence='YES'; DtoField='name'; DtoType='JsonNode';
        Zone='core'; Target='core.usuario.usuario_name + core.usuario_history.usuario_name'; Decision='PROMOTE'; Protection='PII_PERSONAL';
        Transformation='preserve ABSENT/NULL/VALUE; current plus append-only history only on state change';
        Reducer='ABSENT preserves known value; NULL and VALUE remain distinct'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios.data_atualizacao' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_CURRENT'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.last_changed_at_utc'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='database technical change timestamp after set-based state transition';
        Reducer='never expose as source freshness or watermark'; TimeZone='UTC_TECHNICAL_ONLY'; DueGate='V2-033'
    }
    'dim_usuarios.excluido_na_origem' = [pscustomobject]@{
        SourcePath='NO_COMPLETENESS_PROOF'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.active'; Decision='RETIRE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='retire absence flag; V2-033 never writes active=0';
        Reducer='absence/cap/error/cancellation cannot deactivate'; TimeZone='N/A'; DueGate='V2-033/V2-012b/V2-013'
    }
    'dim_usuarios.data_exclusao_origem' = [pscustomobject]@{
        SourcePath='NO_COMPLETENESS_PROOF'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.active'; Decision='RETIRE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='no absence-derived deletion timestamp in the V2-033 shadow contract';
        Reducer='deactivation remains disabled until independent completeness and sweep gates'; TimeZone='N/A'; DueGate='V2-033/V2-012b/V2-013'
    }
    'dim_usuarios.hash_linha' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_CURRENT'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.attribute_hash + core.usuario.state_hash'; Decision='DERIVE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='versioned SQL Server fingerprints over name presence/value and active state';
        Reducer='same newer state is NO_OP; changed newer state appends history'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios.ativo' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_CURRENT'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.active'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='insert/reactivation sets active=1; this vertical has no active=0 transition';
        Reducer='reactivation preserves usuario_id; absence is a no-op'; TimeZone='N/A'; DueGate='V2-033/V2-012b/V2-013'
    }
    'dim_usuarios.origem_atualizado_em' = [pscustomobject]@{
        SourcePath='UNREQUESTED:individual.edges.node.updatedAt'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='ctl'; Target='ctl.execution_attempt.contract_fingerprint'; Decision='RETIRE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='fingerprinted contract records updatedAt as absent/unrequested; no invented source timestamp';
        Reducer='technical observation time never substitutes source freshness'; TimeZone='ABSENT_SOURCE_FIELD'; DueGate='V2-024/V2-033'
    }
    'dim_usuarios.ultima_extracao_em' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_CURRENT'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario.last_seen_at_utc'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='technical last-seen timestamp from the accepted execution';
        Reducer='newer identical observation advances last-seen without history'; TimeZone='UTC_TECHNICAL_ONLY'; DueGate='V2-033'
    }
    'dim_usuarios_historico.id' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.usuario_history_id'; Decision='DERIVE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='allocate append-only BIGINT IDENTITY history identifier';
        Reducer='one history row per execution and usuario_id only for a state change'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios_historico.execution_uuid' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.execution_id'; Decision='DERIVE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='bind immutable history to the applying V2 execution';
        Reducer='unique execution_id plus usuario_id makes retry idempotent'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios_historico.user_id' = [pscustomobject]@{
        SourcePath='individual.edges.node.id'; Requested='YES'; DtoPresence='YES'; DtoField='id'; DtoType='JsonNode';
        Zone='core'; Target='core.usuario_history.usuario_id'; Decision='PROMOTE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='resolve type-tagged scoped source_key to stable canonical usuario_id set-based';
        Reducer='history references canonical current; source id is never coerced to canonical id'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios_historico.nome' = [pscustomobject]@{
        SourcePath='individual.edges.node.name'; Requested='YES'; DtoPresence='YES'; DtoField='name'; DtoType='JsonNode';
        Zone='core'; Target='core.usuario_history.usuario_name'; Decision='PROMOTE'; Protection='PII_PERSONAL';
        Transformation='append accepted ABSENT/NULL/VALUE state only on INSERTED/UPDATED/REACTIVATED';
        Reducer='no history for NO_OP or STALE_NO_OP'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios_historico.ativo' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.active'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='append active=1 on insert/update/reactivation; no absence state emitted';
        Reducer='deactivation remains outside V2-033'; TimeZone='N/A'; DueGate='V2-033/V2-012b/V2-013'
    }
    'dim_usuarios_historico.origem_atualizado_em' = [pscustomobject]@{
        SourcePath='UNREQUESTED:individual.edges.node.updatedAt'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='ctl'; Target='ctl.execution_attempt.contract_fingerprint'; Decision='RETIRE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='contract evidence keeps source updatedAt absent instead of copying a technical timestamp';
        Reducer='no source freshness or temporal incremental is inferred'; TimeZone='ABSENT_SOURCE_FIELD'; DueGate='V2-024/V2-033'
    }
    'dim_usuarios_historico.hash_linha' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.attribute_hash + core.usuario_history.state_hash'; Decision='DERIVE'; Protection='TECHNICAL_IDENTIFIER';
        Transformation='copy versioned SQL fingerprints from the applied current state';
        Reducer='append only when the accepted state changes'; TimeZone='N/A'; DueGate='V2-033'
    }
    'dim_usuarios_historico.observado_em' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.observation_order_at_utc + core.usuario_history.changed_at_utc'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='persist total technical observation order and database change time';
        Reducer='order tuple includes origin execution id; never claims source freshness'; TimeZone='UTC_TECHNICAL_ONLY'; DueGate='V2-033'
    }
    'dim_usuarios_historico.tipo_alteracao' = [pscustomobject]@{
        SourcePath='DERIVED_BY_USER_HISTORY'; Requested='NO_OR_DERIVED'; DtoPresence='DERIVED_OR_UNREQUESTED'; DtoField=''; DtoType='';
        Zone='core'; Target='core.usuario_history.change_kind'; Decision='DERIVE'; Protection='INTERNAL_OPERATIONAL';
        Transformation='closed disposition INSERTED/UPDATED/REACTIVATED from set-based apply';
        Reducer='NO_OP and STALE_NO_OP never append history'; TimeZone='N/A'; DueGate='V2-033'
    }
}
$position = 0
foreach ($entry in $userRows) {
    $position++
    $column = $entry.Column
    $mappingKey = $entry.Table + '.' + $column.Name
    $mapping = $userMappings[$mappingKey]
    if ($null -eq $mapping) { throw "Mapeamento V2-033 ausente para $mappingKey." }
    $cardinality = if ($entry.Table -eq 'dim_usuarios') { 'ONE_CURRENT_PER_SCOPED_SOURCE_IDENTITY' } else { 'APPEND_ONLY_PER_ACCEPTED_STATE_CHANGE' }
    $fields.Add((New-FieldRow -RowId ('USR-SQL-{0:D3}' -f $position) -RowKind 'V1_USER_COLUMN' -Entity 'usuarios' -SourceContract 'GRAPHQL_INDIVIDUAL_AND_V1_SQL' -Template 'NO_DATA_EXPORT_TEMPLATE' -SourceRoot $entry.Table -SourcePath $mapping.SourcePath -SourceType $column.Type -Presence $(if ($column.Nullable -eq 'NO') {'REQUIRED_IN_V1_SQL'} else {'NULLABLE_IN_V1_SQL'}) -Cardinality $cardinality -Requested $mapping.Requested -DtoPresence $mapping.DtoPresence -LegacyMapping ('dbo.' + $entry.Table + '.' + $column.Name) -V2Zone $mapping.Zone -V2Target $mapping.Target -Transformation $mapping.Transformation -TimeZone $mapping.TimeZone -Reducer $mapping.Reducer -Unit 'N/A' -Currency 'N/A' -PrecisionScale $column.Type -Rounding 'N/A' -ProtectionClass $mapping.Protection -RetentionPolicy 'R-USER-HISTORY-OWNER' -Consumer 'current/history + core.v_usuario_dimension_current_v1 implemented in shadow; pub.vw_dim_usuarios pending V2-037' -Decision $mapping.Decision -Owner (Get-OwnerForEntity 'usuarios') -Status 'IMPLEMENTED_IN_SHADOW' -DueGate $mapping.DueGate -Evidence $entry.Evidence -Fixture ('bounded user current/history fixture ' + $column.Name) -PublicationBlocked 'YES' -DtoFieldName $mapping.DtoField -DtoJavaType $mapping.DtoType))
}

# Raster permanece no ledger mesmo desligado.
$rasterFiles = @(
    Join-Path $legacyRootResolved 'database\tabelas\024_criar_tabela_raster_viagens.sql'
    Join-Path $legacyRootResolved 'database\tabelas\025_criar_tabela_raster_viagem_paradas.sql'
)
$position = 0
foreach ($file in $rasterFiles) {
    $tableName = if ($file -match 'paradas') { 'raster_viagem_paradas' } else { 'raster_viagens' }
    foreach ($column in (Get-CreateTableColumns -Path $file)) {
        $position++
        $isMetadata = $column.Name -eq 'metadata'
        $fields.Add((New-FieldRow -RowId ('RAS-SQL-{0:D3}' -f $position) -RowKind 'V1_RASTER_COLUMN' -Entity 'raster' -SourceContract 'RASTER_API_CONDITIONAL' -Template 'N/A' -SourceRoot $tableName -SourcePath 'UNRESOLVED_RASTER_SOURCE_PATH' -SourceType $column.Type -Presence $(if ($column.Nullable -eq 'NO') {'REQUIRED_IN_V1_SQL'} else {'NULLABLE_IN_V1_SQL'}) -Cardinality $(if ($tableName -match 'paradas') {'CHILD_BY_REQUEST_AND_ORDER'} else {'PARENT_BY_REQUEST'}) -Requested 'NOT_AUTHORIZED_IN_THIS_BLOCK' -DtoPresence 'TO_RECONCILE' -LegacyMapping ('dbo.' + $tableName + '.' + $column.Name) -V2Zone $(if ($isMetadata) {'recon'} else {'core'}) -V2Target $(if ($isMetadata) {'recon.raster_field_presence'} else {'core.' + $tableName + '.' + $column.Name}) -Transformation $(if ($isMetadata) {'retire catch-all; retain only typed presence/provenance'} else {'conditional typed mapping; parent+children atomic'}) -TimeZone 'UNRESOLVED_IANA_CONTRACT' -Reducer 'no host timezone; cap is incompleteness' -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'N/A' -PrecisionScale $column.Type -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $column.Name) -RetentionPolicy $(if ($isMetadata) {'NO_RAW_PAYLOAD_DEFAULT'} else {'R-DOMAIN-OWNER'}) -Consumer 'conditional pub.vw_raster_sm_transit_time' -Decision $(if ($isMetadata) {'RETIRE'} else {'PRESERVE'}) -Owner (Get-OwnerForEntity 'raster') -Status $(if ($isMetadata) {'RAW_METADATA_COLUMN_RETIRED'} else {'CONDITIONAL_DISABLED_PENDING_V2-034a'}) -DueGate 'V2-034a/V2-034b' -Evidence (Get-RelativeLegacyPath $file) -Fixture ('conditional Raster fixture ' + $column.Name) -PublicationBlocked 'YES'))
    }
}

# Selection sets GraphQL operacionais/sidecars, parseados diretamente do código.
$graphQlPath = Join-Path $legacyRootResolved 'src\main\java\br\com\extrator\integracao\graphql\GraphQLQueries.java'
$graphQlText = [System.IO.File]::ReadAllText($graphQlPath)
$queryEntity = @{
    'QUERY_COLETAS'='coletas'; 'QUERY_FRETES'='fretes'; 'QUERY_NFSE'='faturas_por_cliente';
    'QUERY_USUARIOS_SISTEMA'='usuarios'; 'QUERY_RESOLVER_CONTA_BANCARIA'='contas_a_pagar'
}
$coletaMetadataOnly = @(
    'customer.id','corporation.person.cnpj','user.id','serviceStartHour','serviceEndHour','requester','comments','agentId',
    'cargoClassificationId','costCenterId','invoicesCubedWeight','lunchBreakEndHour','lunchBreakStartHour',
    'notificationEmail','notificationPhone','pickTypeId','pickupLocationId'
)
$graphPosition = 0
foreach ($queryName in $queryEntity.Keys | Sort-Object) {
    $queryMatch = [regex]::Match($graphQlText, '(?s)public\s+static\s+final\s+String\s+' + [regex]::Escape($queryName) + '\s*=\s*"""(?<query>.*?)""";')
    if (-not $queryMatch.Success) { throw "Query $queryName não encontrada em GraphQLQueries.java." }
    $entity = $queryEntity[$queryName]
    foreach ($path in (Get-GraphQlLeafPaths -Query $queryMatch.Groups['query'].Value -QueryName $queryName)) {
        $graphPosition++
        $relativeNode = if ($path -match '\.node\.(?<leaf>.+)$') { $Matches['leaf'] } else { $path }
        $metadataOnly = $queryName -eq 'QUERY_COLETAS' -and $relativeNode -in $coletaMetadataOnly
        $technicalCursor = $path -match 'pageInfo\.|\.cursor$'
        $safeGraphName = [regex]::Replace($relativeNode.ToLowerInvariant(), '[^a-z0-9_]+', '_').Trim('_')
        $nestedRelation = $relativeNode -match '(?i)(pickItems|freightInvoices|invoices|freights|occurrences)\.'
        $nestedAlias = $relativeNode -match '(?i)\.(id|sequenceCode|sequenceNumber|referenceNumber)$'
        $graphTransformation = $null
        $graphReducer = 'FIELD_SPECIFIC_PENDING_VERTICAL'
        if ($queryName -eq 'QUERY_USUARIOS_SISTEMA') {
            $legacy = 'dbo.dim_usuarios transitional source'
            $status = 'IMPLEMENTED_IN_SHADOW'
            $gate = 'V2-024/V2-033'
            if ($path -match '\.pageInfo\.hasNextPage$') {
                $decision = 'PRESERVE'; $zone = 'ctl'; $target = 'ctl.execution_page_audit.terminal_evidence_kind'
                $graphTransformation = 'classify false as GRAPHQL_PAGE_INFO terminal evidence only after a non-empty bounded page'
                $graphReducer = 'local traversal terminal; never completeness/snapshot/absence proof'
            } elseif ($path -match '\.pageInfo\.endCursor$') {
                $decision = 'PRESERVE'; $zone = 'ctl'; $target = 'ctl.execution_page_audit.page_number'
                $graphTransformation = 'consume opaque cursor only inside the live traversal; persist ordinal audit, never cursor value'
                $graphReducer = 'missing/repeated cursor with hasNextPage=true fails; retry restarts at page one'
            } elseif ($path -match '\.node\.id$') {
                $decision = 'PROMOTE'; $zone = 'stg'; $target = 'stg.usuario_record.source_key + core.usuario.source_key'
                $graphTransformation = 'validate scalar integer/string and preserve case-sensitive type-tagged source identity'
                $graphReducer = 'SQL resolves scoped current identity; invalid key quarantines without Java-wide sets'
            } elseif ($path -match '\.node\.name$') {
                $decision = 'PROMOTE'; $zone = 'stg'; $target = 'stg.usuario_record.usuario_name + core.usuario.usuario_name + core.usuario_history.usuario_name'
                $graphTransformation = 'preserve ABSENT/NULL/VALUE in a page-sized stage microbatch; SQL owns current/history'
                $graphReducer = 'ABSENT preserves known value; change appends history; no-op/stale does not'
            } else {
                throw "Folha inesperada no contrato GraphQL de Usuários: $path."
            }
        } elseif ($technicalCursor) {
            $legacy = 'pagination metadata'; $zone = 'ctl'; $target = 'ctl.source_cursor_evidence'; $status = 'TECHNICAL_SELECTION_CLASSIFIED'; $decision = 'PRESERVE'; $gate = 'V2-024/V2-025'
        } else {
            $decision = if ($nestedRelation) { 'SPLIT' } elseif ($nestedAlias) { 'ALIAS' } else { Get-DataFieldDecision -Entity $entity -Name $relativeNode.Replace('.','_') }
            if ($metadataOnly) { $legacy = 'metadata-only'; $status = 'GRAPHQL_METADATA_ONLY_TRANSITIONAL'; $gate = 'V2-041->V2-025d/V2-010' }
            else { $legacy = 'V1 mapper/sidecar to reconcile'; $status = 'TRANSITIONAL_GRAPHQL_SELECTION'; $gate = 'V2-024/' + $entityVerticalGates[$entity] }
            if ($decision -eq 'SPLIT') { $zone = 'crosswalk'; $target = 'crosswalk.' + $entity + '.pending_relation_' + $safeGraphName }
            elseif ($decision -eq 'ALIAS') { $zone = 'crosswalk'; $target = 'crosswalk.' + $entity + '.alias_' + $safeGraphName }
            else { $zone = 'stg'; $target = 'stg.' + $entity + '.sidecar_' + $safeGraphName }
        }
        if ([string]::IsNullOrWhiteSpace($graphTransformation)) {
            $graphTransformation = if ($decision -eq 'SPLIT') { 'stage separately; resolve relation/cardinality set-based; remove sidecar only after parity' } elseif ($decision -eq 'ALIAS') { 'preserve source alias and provenance; never infer canonical identity' } elseif ($decision -eq 'NORMALIZE') { 'preserve raw and normalize typed value; remove sidecar only after parity' } else { 'typed sidecar/presence; remove when Data Export equivalent is proved' }
        }
        $retentionPolicy = if ($zone -eq 'stg') { 'R-STG-CANDIDATE' } elseif ($zone -eq 'ctl') { 'R-CTL-APPEND-ONLY' } else { 'R-DOMAIN-OWNER' }
        $fields.Add((New-FieldRow -RowId ('GQL-{0:D4}' -f $graphPosition) -RowKind 'GRAPHQL_SELECTION' -Entity $entity -SourceContract 'ESL_GRAPHQL_READ_ONLY' -Template $queryName -SourceRoot ($path.Split('.')[0]) -SourcePath $path -SourceType 'GRAPHQL_TYPE_UNVERIFIED' -Presence 'REQUESTED_SELECTION' -Cardinality $(if ($decision -eq 'SPLIT') {'NESTED_RELATION_TO_PROVE'} else {'ROOT_OR_NESTED_SELECTION'}) -Requested 'YES' -DtoPresence 'TO_RECONCILE_WITH_GRAPHQL_DTO' -LegacyMapping $legacy -V2Zone $zone -V2Target $target -Transformation $graphTransformation -TimeZone 'America/Sao_Paulo_WHEN_TEMPORAL' -Reducer $graphReducer -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'UNRESOLVED_IF_FINANCIAL' -PrecisionScale 'UNRESOLVED_IF_NUMERIC' -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $relativeNode) -RetentionPolicy $retentionPolicy -Consumer ('transitional ' + $entity) -Decision $decision -Owner (Get-OwnerForEntity $entity) -Status $status -DueGate $gate -Evidence (Get-RelativeLegacyPath $graphQlPath) -Fixture ('synthetic GraphQL selection ' + $relativeNode) -PublicationBlocked 'YES'))
    }
}

# Cinco fatos: toda coluna física do DDL é um output mart a reconciliar.
$factFiles = $tableFiles | Where-Object { $_.BaseName -match '^0(28|29|30|31|32)_' }
$factPosition = 0
foreach ($file in $factFiles) {
    $factName = ([regex]::Match($file.BaseName, '^\d{3}_criar_tabela_(?<name>.+)$')).Groups['name'].Value
    foreach ($column in (Get-CreateTableColumns -Path $file.FullName)) {
        $factPosition++
        $grainConflict = ($file.BaseName -match '^0(28|30|31)_')
        $isMetadata = $column.Name -eq 'metadata'
        $fields.Add((New-FieldRow -RowId ('MART-{0:D4}' -f $factPosition) -RowKind 'V1_FACT_COLUMN' -Entity $factName -SourceContract 'V1_SQL_MATERIALIZED_FACT' -Template 'MAT-01..MAT-05' -SourceRoot ('dbo.' + $factName) -SourcePath 'SET_BASED_EXPRESSION_TO_RECONCILE' -SourceType $column.Type -Presence $(if ($column.Nullable -eq 'NO') {'REQUIRED_IN_V1_SQL'} else {'NULLABLE_IN_V1_SQL'}) -Cardinality $(if ($grainConflict) {'UNRESOLVED_LEGACY_GRAIN_CONFLICT'} else {'FACT_GRAIN_DOCUMENTED_IN_STATES'}) -Requested 'N/A' -DtoPresence 'N/A' -LegacyMapping ('dbo.' + $factName + '.' + $column.Name) -V2Zone $(if ($isMetadata) {'recon'} else {'mart'}) -V2Target $(if ($isMetadata) {'recon.fact_field_presence'} else {'mart.' + $factName + '.' + $column.Name}) -Transformation $(if ($isMetadata) {'retire catch-all; retain typed lineage/provenance'} else {'set-based fact expression with lineage and one canonical input per grain'}) -TimeZone 'America/Sao_Paulo_WHEN_TEMPORAL' -Reducer 'MAT rule + field-specific reducer' -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'UNRESOLVED_IF_FINANCIAL' -PrecisionScale $column.Type -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $column.Name) -RetentionPolicy $(if ($isMetadata) {'NO_RAW_PAYLOAD_DEFAULT'} else {'R-MART-CONTRACT'}) -Consumer 'pub/BI consumer manifest pending' -Decision $(if ($isMetadata) {'RETIRE'} else {'DERIVE'}) -Owner 'BI_CONTROLADORIA_OPERACAO_E_PLATAFORMA' -Status $(if ($isMetadata) {'RAW_METADATA_COLUMN_RETIRED'} elseif ($grainConflict) {'UNRESOLVED_FACT_GRAIN'} else {'MAPPED_FACT_OUTPUT'}) -DueGate 'V2-036/V2-037' -Evidence (Get-RelativeLegacyPath $file.FullName) -Fixture ('fact parity fixture ' + $column.Name) -PublicationBlocked 'YES'))
    }
}

# Outputs das 19 views ETL-owned. Wrappers cross-database não entram como output pertencente ao ETL.
$viewPosition = 0
$etlViewFiles = $viewFiles | Where-Object { $_.BaseName -notmatch '^02[34]_publicar_wrappers_' }
foreach ($file in $etlViewFiles) {
    $viewNameMatch = [regex]::Match([System.IO.File]::ReadAllText($file.FullName), '(?is)\bCREATE\s+(?:OR\s+ALTER\s+)?VIEW\s+(?:\[?dbo\]?\.)?\[?(?<name>[A-Za-z0-9_]+)\]?')
    $viewName = if ($viewNameMatch.Success) { $viewNameMatch.Groups['name'].Value } else { $file.BaseName }
    foreach ($output in (Get-ViewOutputs -Path $file.FullName)) {
        $viewPosition++
        $monitor = $viewName -eq 'vw_bi_monitoramento'
        $raster = $viewName -eq 'vw_raster_sm_transit_time'
        $isMetadata = $output.Name -eq 'Metadata'
        $status = if ($isMetadata) { 'RAW_METADATA_PUBLICATION_RETIRED_PENDING_CONSUMER_ACCEPTANCE' } elseif ($output.ParseStatus -like 'UNRESOLVED*') { $output.ParseStatus } elseif ($raster) { 'CONDITIONAL_VIEW_OUTPUT' } else { 'CONSUMER_CONTRACT_PENDING' }
        $fields.Add((New-FieldRow -RowId ('PUB-{0:D4}' -f $viewPosition) -RowKind 'V1_VIEW_OUTPUT' -Entity $viewName -SourceContract 'V1_SQL_VIEW' -Template 'PUB-01..PUB-08' -SourceRoot ('dbo.' + $viewName) -SourcePath $output.Expression -SourceType 'SQL_EXPRESSION_TYPE_TO_FINGERPRINT' -Presence 'VIEW_OUTPUT' -Cardinality 'VIEW_GRAIN_PENDING_CONSUMER_MANIFEST' -Requested 'N/A' -DtoPresence 'N/A' -LegacyMapping ('dbo.' + $viewName + '.' + $output.Name) -V2Zone $(if ($isMetadata) {'recon'} elseif ($monitor) {'ctl'} else {'pub'}) -V2Target $(if ($isMetadata) {'recon.published_field_presence'} elseif ($monitor) {'ctl.' + $viewName + '.' + $output.Name} else {'pub.' + $viewName + '.' + $output.Name}) -Transformation $(if ($isMetadata) {'retire raw metadata output; replace with typed columns and lineage'} else {'preserve alias/semantics until consumer-approved contract'}) -TimeZone 'America/Sao_Paulo_WHEN_TEMPORAL' -Reducer 'PUB rule + expression lineage' -Unit 'UNRESOLVED_IF_NUMERIC' -Currency 'UNRESOLVED_IF_FINANCIAL' -PrecisionScale 'TO_FINGERPRINT' -Rounding 'FAIL_ON_UNAPPROVED_ROUNDING' -ProtectionClass (Get-ProtectionClass $output.Name) -RetentionPolicy $(if ($isMetadata) {'NO_RAW_PAYLOAD_DEFAULT'} elseif ($monitor) {'R-CTL-APPEND-ONLY'} else {'R-PUB-CONTRACT'}) -Consumer 'TIME_NOMINAL_NAO_INFORMADO; dashboard não consultado' -Decision $(if ($isMetadata) {'RETIRE'} elseif ($monitor) {'NORMALIZE'} else {'PRESERVE'}) -Owner 'BI_OU_CONSUMIDOR_E_PLATAFORMA_DE_DADOS' -Status $status -DueGate $(if ($raster) {'V2-034a/V2-034b/V2-037'} else {'V2-037'}) -Evidence (Get-RelativeLegacyPath $file.FullName) -Fixture ('schema/output parity ' + $output.Name) -PublicationBlocked 'YES'))
    }
}

# 75 regras canônicas são extraídas do roadmap, que permanece a fonte textual governada.
$rules = [System.Collections.Generic.List[object]]::new()
$statesPath = Join-Path $v2Root 'STATES.md'
$statesLines = [System.IO.File]::ReadAllLines($statesPath)
$ruleCasesPath = Join-Path $PSScriptRoot 'PortabilityRuleCases.psd1'
$ruleCases = Import-PowerShellDataFile -LiteralPath $ruleCasesPath
$ruleSourceByPrefix = @{
    'COL'='STATES.md#coletas; GraphQLQueries.java; tabelas/001; views/012,015; procedure/005'
    'FRE'='STATES.md#fretes; GraphQLQueries.java; DTO 6389; tabelas/002; views/011; procedures/001,003'
    'MAN'='STATES.md#manifestos; docs/catalogos/manifestos-v2-026/decisao-v03.json; DTO/mapper 6399; tabelas/003; views/018,019; procedures/002,005'
    'COT'='STATES.md#cotacoes; DTO/mapper 6906; tabelas/004; views/016'
    'LOC'='STATES.md#localizacao; DTO/mapper 8656; tabelas/005; views/014'
    'CAP'='STATES.md#contas-a-pagar; DTO/mapper 8636; tabelas/006; views/013'
    'FAT'='STATES.md#faturas-por-cliente; DTO/mapper 4924; tabelas/007; views/011,017; procedures/003,004'
    'INV'='STATES.md#inventario; DTO/mapper 10633; tabelas/020; views/020'
    'SIN'='STATES.md#sinistros; DTO/mapper 6392; tabelas/021; views/021'
    'USR'='STATES.md#usuarios; GraphQLQueries.java; tabelas/011,016,017; view-dimensao/024'
    'RAS'='STATES.md#raster; tabelas/024,025; views/022'
    'MAT'='STATES.md#contratos-sql; tabelas/028..032; procedures/001..005'
    'PUB'='STATES.md#destino-das-19-views; views/011..025; views-dimensao/019..024'
}
$unresolvedRuleIds = @(
    'COL-07','COL-10','FRE-04','FRE-05','FRE-06','FRE-07','MAN-03','MAN-06',
    'LOC-01','LOC-05','LOC-07','CAP-01','CAP-04','FAT-01','FAT-02','FAT-04','FAT-07',
    'INV-01','INV-04','SIN-01','RAS-01','RAS-02','RAS-03','RAS-04',
    'RAS-05','RAS-06','MAT-01','MAT-02','MAT-03','MAT-04','MAT-05','PUB-01','PUB-02',
    'PUB-03','PUB-04','PUB-05','PUB-06','PUB-07','PUB-08'
)
foreach ($line in $statesLines) {
    $match = [regex]::Match($line, '^\s*- \*\*(?<id>[A-Z]{3}-\d{2})(?:\s+—\s+(?<title>[^*:]+))?:\*\*\s*(?<rule>.+)$')
    if (-not $match.Success) { continue }
    $id = $match.Groups['id'].Value
    $prefix = $id.Substring(0,3)
    $plain = [System.Net.WebUtility]::HtmlDecode([regex]::Replace($match.Groups['rule'].Value, '<[^>]+>', '')).Trim()
    $owner = switch ($prefix) {
        'COL' {'OPERACAO_E_PLATAFORMA'}; 'FRE' {'OPERACAO_FISCAL_E_PLATAFORMA'}; 'MAN' {'OPERACAO_E_BI'};
        'COT' {'COMERCIAL_E_OPERACAO'}; 'LOC' {'LOGISTICA_E_OPERACAO'}; 'CAP' {'CONTROLADORIA_E_FINANCEIRO'};
        'FAT' {'CONTROLADORIA_E_FISCAL'}; 'INV' {'OPERACAO_E_LOGISTICA'}; 'SIN' {'OPERACAO_E_SINISTROS'};
        'USR' {'SEGURANCA_E_OPERACAO'}; 'RAS' {'OPERACAO_RASTER_E_PLATAFORMA'};
        'MAT' {'BI_CONTROLADORIA_OPERACAO_E_PLATAFORMA'}; 'PUB' {'BI_OU_CONSUMIDOR_E_PLATAFORMA_DE_DADOS'}
    }
    $gate = switch ($prefix) {
        'COL' {'V2-010/V2-012'}; 'FRE' {'V2-011/V2-012'}; 'MAN' {'V2-026/V2-046a'}; 'COT' {'V2-027'};
        'LOC' {'V2-028'}; 'CAP' {'V2-029'}; 'FAT' {'V2-030'}; 'INV' {'V2-031'}; 'SIN' {'V2-032'};
        'USR' {'V2-024/V2-033'}; 'RAS' {'V2-034a/V2-034b'}; 'MAT' {'V2-036'}; 'PUB' {'V2-037'}
    }
    if ($id -eq 'USR-02') { $gate = 'V2-033/V2-012b/V2-013' }
    elseif ($id -eq 'USR-03') { $gate = 'V2-024/V2-033/V2-040' }
    if (-not $ruleCases.ContainsKey($id) -or @($ruleCases[$id]).Count -ne 2) {
        throw "Casos sintéticos positivo/negativo ausentes para $id em PortabilityRuleCases.psd1."
    }
    $isUnresolved = $id -in $unresolvedRuleIds
    $title = $match.Groups['title'].Value.Trim()
    if ([string]::IsNullOrWhiteSpace($title)) { $title = 'Regra ' + $id }
    $rules.Add([pscustomobject][ordered]@{
        rule_id = $id
        domain = $prefix
        title = $title
        rule = $plain
        source = $ruleSourceByPrefix[$prefix]
        positive_example = $ruleCases[$id][0]
        counterexample = $ruleCases[$id][1]
        affected_contract = $prefix + ' source/core/mart/pub conforme matriz-campos.csv'
        expected_test = ('PortabilityRule_{0}_positive_and_counterexample; implementation gate {1}' -f $id.Replace('-','_'), $gate)
        owner_role = $owner
        acceptor = if ($id -in @('MAN-01','MAN-02','MAN-04','MAN-07')) { 'OWNER_EXPLICIT_AUTHORIZATION_2026_09_04' } else { 'TIME_NOMINAL_NAO_INFORMADO' }
        decision = 'PRESERVE'
        status = if ($prefix -eq 'USR') { 'IMPLEMENTED_IN_SHADOW' } elseif ($id -in @('MAN-01','MAN-02','MAN-04','MAN-07')) { 'LOCAL_DECISION_FROZEN_V03_EXECUTION_PENDING' } elseif ($isUnresolved) { 'UNRESOLVED_WITH_GATE' } else { 'BASELINE_CLASSIFIED_PENDING_OWNER_ACCEPTANCE' }
        due_gate = $gate
        publication_blocked = 'YES'
        evidence = if ($id -in @('MAN-01','MAN-02','MAN-04','MAN-07')) { 'decisão V03 local fail-closed + corpus estático legado versionado; execução/publicação continuam nos gates próprios' } else { 'roadmap consolidado a partir de código/SQL legado; produção/owner é oráculo final' }
    })
}
if ($rules.Count -ne $ruleCases.Count) {
    throw "Catálogo de regras e casos sintéticos divergente: regras=$($rules.Count), casos=$($ruleCases.Count)."
}

$protection = @(
    [pscustomobject][ordered]@{protection_id='PROT-01';scope='segredos e credenciais';purpose='autenticar fontes/banco sem persistir valor';column_patterns='token|secret|password|authorization|credential';minimization='somente referência ao secret provider; nunca valor em DB/log/CSV/Git';at_rest_encryption='secret store aprovado';key_management='owner de Segurança; rotação e segregação por ambiente';in_transit='TLS e autenticação no canal autorizado';masking='redação total';allowed_profiles='secret-provider runtime estritamente necessário';denied_profiles='dev logs, mart, pub, recon, fixtures, argumentos CLI/system properties';retention_policy='não persistir';retention_status='RATIFIED';legal_hold='não aplicável ao valor; evidência sanitizada separada';owner_role='SEGURANCA_E_OPERACOES';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-018/V2-041/V2-044';status='RATIFIED_DIRECTION';evidence='AGENTS.md/STATES.md';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-02';scope='URL, headers, cursor e erro remoto';purpose='transporte e diagnóstico sanitizado';column_patterns='url|uri|header|cursor|error|message|stack';minimization='booleanos/categorias/contadores; sem URL tenant, header, cursor ou corpo';at_rest_encryption='TDE/backup conforme DBA quando persistido';key_management='DBA/Segurança';in_transit='TLS';masking='hash não reversível somente se finalidade aprovada; redação por default';allowed_profiles='ctl/recon limitado';denied_profiles='pub/mart/log textual irrestrito';retention_policy='R-CTL-APPEND-ONLY';retention_status='PENDING_V2-045';legal_hold='hold preserva evidência sanitizada';owner_role='SEGURANCA_E_PLATAFORMA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-018/V2-045';status='POLICY_PENDING_OWNER';evidence='ADRs 0004/0005';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-03';scope='identificadores técnicos e chaves de origem';purpose='identidade, dedupe, crosswalk e replay';column_patterns='id|key|sequence|codigo|hash';minimization='somente chaves necessárias e aliases versionados; sem IDs em logs';at_rest_encryption='TDE + backup criptografado';key_management='DBA/Segurança, sem chave na aplicação';in_transit='TLS';masking='tokenização/mascara em suporte e evidência';allowed_profiles='runtime core, recon restrito, owner da vertical';denied_profiles='exportação ad hoc e logs';retention_policy='R-DOMAIN-OWNER';retention_status='PENDING_V2-045';legal_hold='suspende purge por chave do caso';owner_role='DATA_OWNER_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-008/V2-045';status='POLICY_PENDING_OWNER';evidence='ADR 0008 + matriz de identidade';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-04';scope='documentos pessoais e empresariais';purpose='identificação fiscal/operacional e joins aprovados';column_patterns='cpf|cnpj|document|inscricao';minimization='normalizado somente quando necessário; preservar bruto apenas em staging controlado se aprovado';at_rest_encryption='TDE + backup criptografado; coluna adicional somente por threat model';key_management='DBA/Segurança';in_transit='TLS';masking='parcial por perfil; total em evidência/log';allowed_profiles='runtime mínimo, Fiscal/Controladoria/Operação conforme finalidade';denied_profiles='público genérico, logs, fixtures reais';retention_policy='R-DOMAIN-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='hold por caso/documento com auditoria';owner_role='DATA_OWNER_COMPLIANCE_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-045';status='POLICY_PENDING_OWNER';evidence='inventário de campos V1';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-05';scope='nomes e histórico de usuários/pessoas';purpose='atribuição operacional, dimensão e auditoria';column_patterns='name|nome|nickname|requester|driver|motorista|user';minimization='nome mínimo; sem duplicação em logs/evidências';at_rest_encryption='TDE + backup criptografado';key_management='DBA/Segurança';in_transit='TLS';masking='iniciais ou pseudônimo fora de perfis autorizados';allowed_profiles='Operação/Segurança/BI com finalidade';denied_profiles='logs, fixtures reais, acesso público';retention_policy='R-USER-HISTORY-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='histórico retido quando hold ativo';owner_role='SEGURANCA_OPERACOES_E_COMPLIANCE';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-033/V2-045';status='POLICY_PENDING_OWNER';evidence='tabelas 011/017 e selections GraphQL';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-06';scope='contato e endereço';purpose='execução e acompanhamento da operação logística';column_patterns='email|phone|telefone|address|endereco|line1|line2|postal|cep|bairro';minimization='campo estritamente necessário; metadata-only não promove por default';at_rest_encryption='TDE + backup criptografado';key_management='DBA/Segurança';in_transit='TLS';masking='parcial em UI/export; total em log/evidência';allowed_profiles='Operação autorizada';denied_profiles='BI amplo, logs, fixtures reais';retention_policy='R-DOMAIN-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='hold por caso com auditoria';owner_role='OPERACAO_COMPLIANCE_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-010/V2-045';status='POLICY_PENDING_OWNER';evidence='17 campos metadata-only de Coletas';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-07';scope='localização, rota, veículo e timeline';purpose='logística, ETA e desempenho';column_patterns='latitude|longitude|route|rota|city|cidade|uf|region|placa|vehicle|parada|arrival|depart';minimization='precisão e janela mínimas por consumidor';at_rest_encryption='TDE + backup criptografado';key_management='DBA/Segurança';in_transit='TLS';masking='generalização geográfica/temporal fora da Operação';allowed_profiles='Operação/Logística e BI aprovado';denied_profiles='acesso público, logs detalhados';retention_policy='R-DOMAIN-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='hold por viagem/caso';owner_role='LOGISTICA_COMPLIANCE_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-028/V2-034a/V2-045';status='POLICY_PENDING_OWNER';evidence='Localização/Raster DDL';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-08';scope='fiscal, bancário, pagamentos e valores financeiros';purpose='faturamento, conciliação e métricas financeiras';column_patterns='bank|account|payment|invoice|fatura|cte|nfse|mdfe|fiscal|tax|value|valor|total|subtotal|cost|price';minimization='campos aprovados por fato/contrato; moeda/unidade/escala obrigatórias';at_rest_encryption='TDE + backup criptografado; segregação de grants';key_management='DBA/Segurança';in_transit='TLS';masking='documentos/contas/valores conforme perfil e ambiente';allowed_profiles='Fiscal/Controladoria e runtime mínimo';denied_profiles='acesso genérico, logs e fixtures reais';retention_policy='R-FINANCIAL-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='hold fiscal/legal impede purge';owner_role='CONTROLADORIA_FISCAL_COMPLIANCE_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-029/V2-030/V2-036/V2-045';status='POLICY_PENDING_OWNER';evidence='DDL/procedures/views legadas';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-09';scope='comentários, motivos, descrições, XML e PDF';purpose='tratativa operacional/fiscal e evidência';column_patterns='comment|reason|motivo|observ|description|xml|pdf';minimization='não promover blob/texto livre sem finalidade; extrair somente atributos necessários';at_rest_encryption='TDE + storage cifrado se blob for aprovado';key_management='DBA/Segurança';in_transit='TLS';masking='redação de PII/segredo e limite de tamanho';allowed_profiles='owner do caso e auditor autorizado';denied_profiles='logs, pub genérico, fixtures reais';retention_policy='R-SENSITIVE-CONTENT-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='hold por documento/caso';owner_role='DATA_OWNER_COMPLIANCE_E_SEGURANCA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-021/V2-045';status='POLICY_PENDING_OWNER';evidence='DTOs/DDL legados';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-10';scope='payload/metadata bruto';purpose='nenhuma persistência por default; diagnóstico somente por exceção aprovada';column_patterns='metadata|payload|raw|body';minimization='staging tipado; presença/proveniência sem corpo integral';at_rest_encryption='se excepcionalmente aprovado: storage segregado cifrado';key_management='Segurança/DBA com rotação';in_transit='TLS';masking='não logar; sanitização antes de evidência';allowed_profiles='nenhum por default; break-glass auditado se aprovado';denied_profiles='runtime pub/mart/log/Git/fixtures';retention_policy='NO_RAW_PAYLOAD_DEFAULT';retention_status='RATIFIED';legal_hold='hold somente em evidência minimizada; payload exige decisão legal específica';owner_role='SEGURANCA_COMPLIANCE_E_DATA_OWNER';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-021/V2-045';status='RATIFIED_DIRECTION';evidence='ADR 0002/0003/0005';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-11';scope='staging bem-sucedido';purpose='promoção, replay curto e diagnóstico de DQ';column_patterns='stg.* accepted';minimization='campos tipados necessários por contrato; sem payload bruto';at_rest_encryption='TDE + grants runtime mínimos';key_management='DBA/Segurança';in_transit='TLS';masking='por classe do campo';allowed_profiles='runtime da vertical e suporte auditado';denied_profiles='pub/BI direto';retention_policy='CANDIDATE_7_DAYS';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='legal hold suspende lifecycle por execution_id';owner_role='DATA_OWNER_COMPLIANCE_E_DBA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-045';status='POLICY_PENDING_OWNER';evidence='direção 15 do roadmap';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-12';scope='staging falho';purpose='correção, replay e análise de DQ antes do archive';column_patterns='stg.* rejected';minimization='campo/erro tipado sanitizado; amostra limitada; sem corpo inteiro';at_rest_encryption='TDE + grants segregados';key_management='DBA/Segurança';in_transit='TLS';masking='por classe; mensagem sanitizada e limitada';allowed_profiles='runtime, DQ, suporte auditado';denied_profiles='pub/BI direto';retention_policy='CANDIDATE_30_DAYS';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='legal hold suspende lifecycle por execution_id';owner_role='DATA_OWNER_COMPLIANCE_E_DBA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-021/V2-045b';status='POLICY_PENDING_OWNER';evidence='direção 15 do roadmap; quarantine durável pertence a PROT-16';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-13';scope='core, ref e crosswalk';purpose='domínio canônico, referências e identidade';column_patterns='core.*|ref.*|crosswalk.*';minimization='somente contrato aprovado; aliases com vigência e proveniência';at_rest_encryption='TDE + backup criptografado + grants por schema';key_management='DBA/Segurança';in_transit='TLS';masking='views/perfis por classe';allowed_profiles='runtime e owners do domínio';denied_profiles='acesso anônimo/genérico';retention_policy='R-DOMAIN-OWNER';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='flag/ledger impede purge; aplicação não hard-delete';owner_role='DATA_OWNER_COMPLIANCE_E_DBA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-008/V2-019/V2-045';status='POLICY_PENDING_OWNER';evidence='ADRs 0006/0008';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-14';scope='mart e pub';purpose='fatos e contratos de consumo aprovados';column_patterns='mart.*|pub.*';minimization='somente colunas do manifesto consumidor; sem metadata/raw';at_rest_encryption='TDE + grants por contrato';key_management='DBA/Segurança';in_transit='TLS';masking='view/role por classe e ambiente';allowed_profiles='consumidores nominalmente aprovados';denied_profiles='acesso genérico; runtime sem necessidade';retention_policy='R-MART-PUB-CONTRACT';retention_status='PENDING_OWNER_CONSUMER';legal_hold='segue dado fonte/contrato e preserva evidência de versão';owner_role='BI_CONSUMIDOR_DATA_OWNER_E_DBA';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-036/V2-037/V2-045';status='POLICY_PENDING_OWNER';evidence='19 views/5 fatos inventariados';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-15';scope='ctl, logs e métricas';purpose='controle, observabilidade, auditoria e SLA';column_patterns='ctl.*|log|metric|execution|page|watermark';minimization='IDs técnicos tokenizados/omitidos; contadores/categorias; limites por evento/execução';at_rest_encryption='TDE + log storage cifrado';key_management='DBA/Segurança/Observabilidade';in_transit='TLS';masking='redaction central e testes';allowed_profiles='Operações/Plataforma/Segurança auditados';denied_profiles='consumidor BI genérico';retention_policy='R-CTL-LOG-LIFECYCLE';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='append-only/arquivo; hold por execution_id';owner_role='OPERACOES_SEGURANCA_COMPLIANCE';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-020/V2-043/V2-045';status='POLICY_PENDING_OWNER';evidence='roadmap observabilidade/proteção';publication_blocked='YES'},
    [pscustomobject][ordered]@{protection_id='PROT-16';scope='recon, evidência e arquivo/backup';purpose='paridade, investigação, recuperação e compliance';column_patterns='recon.*|evidence|archive|backup';minimization='totais, classificações e amostra limitada; nunca segredo/payload/ID real em Git';at_rest_encryption='TDE/storage e backups cifrados';key_management='DBA/Segurança com rotação e teste de restore';in_transit='TLS';masking='redação/tokenização; evidência versionável só sanitizada';allowed_profiles='DQ/Auditoria/Segurança/DBA autorizados';denied_profiles='pub e acesso genérico';retention_policy='APPEND_ONLY_ARCHIVE_OWNER_DEFINED';retention_status='PENDING_DATA_OWNER_COMPLIANCE';legal_hold='legal hold explícito prevalece sobre lifecycle';owner_role='COMPLIANCE_SEGURANCA_DBA_E_DATA_OWNER';acceptor='TIME_NOMINAL_NAO_INFORMADO';due_gate='V2-039/V2-045';status='POLICY_PENDING_OWNER';evidence='direção 15 e gates de paridade';publication_blocked='YES'}
)

# Ordenação e gravação.
$artifactsSorted = @($artifacts | Sort-Object artifact_type, artifact_id, source_path)
$fieldsSorted = @($fields | Sort-Object row_kind, matrix_id)
$rulesSorted = @($rules | Sort-Object rule_id)
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'inventario-artefatos.csv') -Rows $artifactsSorted
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'matriz-campos.csv') -Rows $fieldsSorted
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'regras-negocio.csv') -Rows $rulesSorted
Write-DeterministicCsv -Path (Join-Path $outputRootFull 'matriz-protecao-dados.csv') -Rows $protection

$fieldCounts = [ordered]@{}
foreach ($group in ($fieldsSorted | Group-Object row_kind | Sort-Object Name)) { $fieldCounts[$group.Name] = $group.Count }
$artifactCounts = [ordered]@{}
foreach ($group in ($artifactsSorted | Group-Object artifact_type | Sort-Object Name)) { $artifactCounts[$group.Name] = $group.Count }
$manifest = [ordered]@{
    catalog_version = '2026-09-04.v2-026a-manifestos-v03'
    generator = 'scripts/validation/Build-PortabilityCatalog.ps1'
    source_policy = 'offline allowlist; no dashboards, credentials, network, database or payload values'
    baselines = [ordered]@{
        data_export_info_slots = 442
        data_export_data_candidates = 270
        v1_operational_columns = 459
        v1_user_columns = 18
        v1_raster_columns = 58
        physical_columns_without_raster = 477
        physical_columns_with_raster = 535
        business_rules = 75
        etl_owned_views = 19
        materialized_facts = 5
        distinct_constraints = 170
        dedicated_index_declarations = 47
        embedded_unique_indexes = 6
    }
    field_counts = $fieldCounts
    artifact_counts = $artifactCounts
    unresolved_policy = 'owner_role + due_gate + publication_blocked=YES; zero UNCLASSIFIED; acceptor nominal/time remains external'
    vertical_slices = [ordered]@{
        usuarios = 'IMPLEMENTED_IN_SHADOW; current/history + core dimension view; pub compatibility and absence sweep blocked'
        governed_references = 'OFFLINE_FOUNDATION_COMPLETE; deterministic candidates inert; mutable baselines and activation external'
        manifestos_decision = 'LOCAL_DECISION_FROZEN_V03; P01 identity and MAN-01/MAN-02/MAN-04/MAN-07 only; V2-026 execution and publication pending'
    }
    external_inputs = @(
        'nomes técnicos dos slots /info não persistidos, a fechar somente após V2-041 na rodada autorizada V2-025d',
        'tipos/formatos/nullability/cardinalidade completos por contrato de fonte',
        'owners/aceitantes e manifesto dos consumidores das 19 views e cinco fatos',
        'retenção/legal hold aprovados por data owner/compliance em V2-045',
        'export, tokenização, fingerprint, owner e ratificação das referências produtivas mutáveis de V2-035a',
        'decisão manter/retirar Raster em V2-034a'
    )
}
$manifestJson = $manifest | ConvertTo-Json -Depth 8
Write-DeterministicText -Path (Join-Path $outputRootFull 'manifesto.json') -Content $manifestJson

Write-Host ("PASS: catálogo V2-017 gerado: {0} artefatos, {1} campos, {2} regras, {3} classes de proteção." -f $artifactsSorted.Count, $fieldsSorted.Count, $rulesSorted.Count, $protection.Count)

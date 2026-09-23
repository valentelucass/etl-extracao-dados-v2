#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryPrefix = $repositoryRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$catalogRoot = Join-Path $repositoryRoot 'docs\catalogos\caracterizacao-v2-012'
$manifestPath = Join-Path $catalogRoot 'manifesto.json'
$readmePath = Join-Path $catalogRoot 'README.md'
$runbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-012-fundacao-caracterizacao-sol.md'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0026-harness-caracterizacao-provider-neutral-fail-closed.md'
$profilesRoot = Join-Path $repositoryRoot 'src\test\resources\contracts\v2-012\profiles'
$fixturesRoot = Join-Path $repositoryRoot 'src\test\resources\contracts\v2-012\fixtures'
$implementationRoot = Join-Path $repositoryRoot 'src\test\java\br\com\esl\etl\v2\contratos\caracterizacao'
$statesPath = Join-Path $repositoryRoot 'STATES.md'
$trailPath = Join-Path $repositoryRoot 'docs\runbooks\trilha-de-chats-gpt-5-6.md'

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.PSIsContainer -or $item.Length -gt $MaximumBytes) {
        throw "O artefato '$($item.Name)' está ausente, não é arquivo ou excede o limite local."
    }
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "O artefato '$($item.Name)' contém BOM UTF-8."
    }
    $text = $utf8.GetString($bytes)
    $mojibakeMarkers = @(
        [string]::Concat([char]0x00C3, [char]0x00A7),
        [string]::Concat([char]0x00C3, [char]0x00A3),
        [string]::Concat([char]0x00C3, [char]0x00A1),
        [string]::Concat([char]0x00C3, [char]0x00A9),
        [string]::Concat([char]0x00C3, [char]0x00B3),
        [string]::Concat([char]0x00C2, [char]0x00A0),
        [string]::Concat([char]0x00E2, [char]0x20AC, [char]0x201C),
        [string]::Concat([char]0x00E2, [char]0x20AC, [char]0x201D),
        [string][char]0xFFFD
    )
    foreach ($marker in $mojibakeMarkers) {
        if ($text.Contains($marker, [System.StringComparison]::Ordinal)) {
            throw "O artefato '$($item.Name)' contém mojibake."
        }
    }
    return $text
}

function Assert-NoDuplicateJsonElement {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Path
    )

    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw "O JSON contém propriedade duplicada ordinal em $Path."
            }
            Assert-NoDuplicateJsonElement -Element $property.Value -Path ($Path + '.' + $property.Name)
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJsonElement -Element $item -Path ($Path + '[' + $index + ']')
            $index++
        }
    }
}

function Assert-NoSensitiveJsonElement {
    param(
        [Parameter(Mandatory)][System.Text.Json.JsonElement]$Element,
        [Parameter(Mandatory)][string]$Path
    )

    $forbiddenNames = @(
        'url', 'endpoint', 'token', 'header', 'headers', 'payload', 'rawcursor',
        'businessid', 'businessidhash', 'businesskey', 'customerbusinessid', 'realname',
        'documentvalue', 'licenseplate', 'branchvalue', 'rawvalue', 'secret',
        'recordcontent', 'recordcontenthash', 'connectionstring'
    )
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        foreach ($property in $Element.EnumerateObject()) {
            $normalized = $property.Name.ToLowerInvariant().Replace('_', '').Replace('-', '')
            if ($forbiddenNames -ccontains $normalized) {
                throw "O JSON contém o campo sensível '$($property.Name)' em $Path."
            }
            Assert-NoSensitiveJsonElement -Element $property.Value -Path ($Path + '.' + $property.Name)
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoSensitiveJsonElement -Element $item -Path ($Path + '[' + $index + ']')
            $index++
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::String) {
        $value = $Element.GetString()
        if ($value.Contains('://') -or $value.StartsWith('Bearer ', [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "O JSON contém valor sensível ou coordenada externa em $Path."
        }
    }
}

function Read-StrictJson {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [Parameter(Mandatory)][string]$Label
    )

    $text = Read-StrictUtf8 -LiteralPath $LiteralPath -MaximumBytes $MaximumBytes
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($text)
        try {
            Assert-NoDuplicateJsonElement -Element $document.RootElement -Path '$'
            Assert-NoSensitiveJsonElement -Element $document.RootElement -Path '$'
        } finally {
            $document.Dispose()
        }
        $value = $text | ConvertFrom-Json -Depth 100
    } catch {
        throw "O JSON $Label é inválido, inseguro ou contém propriedade duplicada: $($_.Exception.Message)"
    }
    return [pscustomobject]@{ Text = $text; Value = $value }
}

function Assert-RequiredProperty {
    param(
        [Parameter(Mandatory)][object]$Object,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Label
    )

    if ($Object.PSObject.Properties.Name -cnotcontains $Name) {
        throw "A propriedade '$Name' está ausente em $Label."
    }
}

function Assert-Equal {
    param(
        [AllowNull()]$Actual,
        [AllowNull()]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    if ($null -eq $Actual -or $null -eq $Expected) {
        if (-not ($null -eq $Actual -and $null -eq $Expected)) {
            throw "O valor $Label diverge da baseline."
        }
        return
    }
    if ([string]$Actual -cne [string]$Expected) {
        throw "O valor $Label diverge da baseline."
    }
}

function Assert-ExactSet {
    param(
        [AllowEmptyCollection()][object[]]$Actual,
        [AllowEmptyCollection()][object[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Actual)) {
        if ($item -isnot [string] -or -not $actualSet.Add([string]$item)) {
            throw "O conjunto $Label contém item inválido ou duplicado."
        }
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in @($Expected)) {
        if ($item -isnot [string] -or -not $expectedSet.Add([string]$item)) {
            throw "A baseline $Label contém item inválido ou duplicado."
        }
    }
    if (-not $actualSet.SetEquals($expectedSet)) {
        throw "O conjunto $Label diverge da baseline fechada."
    }
}

function Assert-Member {
    param(
        [AllowNull()]$Actual,
        [Parameter(Mandatory)][string[]]$Allowed,
        [Parameter(Mandatory)][string]$Label
    )

    if ($null -eq $Actual -or $Allowed -cnotcontains [string]$Actual) {
        throw "O vocabulário $Label contém valor desconhecido."
    }
}

function Resolve-RepositoryFile {
    param([Parameter(Mandatory)][string]$RelativePath)

    $platformPath = $RelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $platformPath))
    if (-not $resolved.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "A referência '$RelativePath' escapou do repositório."
    }
    $item = Get-Item -LiteralPath $resolved -ErrorAction Stop
    if ($item.PSIsContainer -or $item.LinkType) {
        throw "A referência '$RelativePath' não é arquivo regular local."
    }
    return $item.FullName
}

function Get-FileSha256 {
    param([Parameter(Mandatory)][string]$LiteralPath)

    return (Get-FileHash -Algorithm SHA256 -LiteralPath $LiteralPath).Hash.ToLowerInvariant()
}

function Assert-FileInventory {
    param(
        [Parameter(Mandatory)][string]$Directory,
        [Parameter(Mandatory)][string[]]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    $actual = @(Get-ChildItem -LiteralPath $Directory -File | ForEach-Object Name)
    Assert-ExactSet -Actual $actual -Expected $Expected -Label $Label
}

function Test-HasReason {
    param(
        [Parameter(Mandatory)][object]$Scenario,
        [Parameter(Mandatory)][string]$Reason
    )

    return @($Scenario.expectedReasons) -ccontains $Reason
}

$expectedVocabularies = [ordered]@{
    stages = @('V2_012A', 'V2_012B', 'V2_012C')
    entities = @('COLETAS', 'MANIFESTOS', 'COTACOES', 'USUARIOS')
    sourceKinds = @('DATA_EXPORT', 'GRAPHQL')
    oracleKinds = @('DATA_EXPORT_PROVIDER', 'GRAPHQL_PROVIDER')
    oracleAvailability = @('SYNTHETIC_IN_MEMORY_ONLY', 'MISSING')
    authorizationRequirements = @('EXPLICIT_OWNER_AUTHORIZATION')
    authorizationStates = @('SYNTHETIC_TEST_ONLY', 'MISSING')
    executionValueRequirements = @('REQUIRED_AT_EXECUTION')
    presence = @('ABSENT', 'NULL', 'VALUE')
    paginationKinds = @('NUMERIC_PAGE', 'RELAY_CURSOR')
    terminalConditions = @('EMPTY_PAGE_LOCAL_UNVERIFIED', 'PAGE_INFO_NO_NEXT_PAGE_LOCAL_UNVERIFIED')
    orderingRoles = @('PARITY_ONLY_NOT_IDENTITY_OR_CURSOR', 'ABSENT')
    temporalTranslations = @('BOUNDARIES_UNPROVEN', 'SOURCE_CIVIL_DATE_UNPROVEN', 'ABSENT_NO_TEMPORAL_FIELD')
    timezoneRequirements = @('EXPLICIT_REQUIRED', 'ORACLE_CONFIRMATION_REQUIRED', 'NOT_APPLICABLE')
    timestampStates = @('VALID', 'INVALID', 'NOT_APPLICABLE')
    timezoneStates = @('EXPLICIT_CONFIRMED', 'UNCONFIRMED', 'NOT_APPLICABLE')
    evidenceClassifications = @('SYNTHETIC_FIXTURE')
    profileStatuses = @('PREPARED_NOT_EXECUTED')
    observationStatuses = @('SYNTHETIC_STRUCTURE_ACCEPTED', 'FAIL_CLOSED')
    gateStatuses = @('ORACLE_REQUIRED')
    scenarioOutcomes = @('SYNTHETIC_STRUCTURE_ACCEPTED', 'FAIL_CLOSED')
    foundationOutcomes = @('FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES', 'FOUNDATION_FAIL_CLOSED')
    requiredChecks = @(
        'SYNTHETIC_ORACLE_AVAILABLE', 'SYNTHETIC_AUTHORIZATION_CONTEXT', 'EXPLICIT_SCOPES',
        'ROOT_MATCH', 'PATH_SET_MATCH', 'FIELD_TYPES_MATCH', 'FILTERS_MATCH',
        'RELATIONSHIPS_PRESERVED', 'ABSENCE_POLICY_PRESERVED', 'SOURCE_KEY_VALID',
        'PRESENCE_TRI_STATE_TRACKED', 'TERMINAL_CONDITION_MET', 'LIMITS_RESPECTED',
        'ORDERING_PARITY_VALID', 'TEMPORAL_EVIDENCE_VALID', 'STATUS_SEMANTICS_VALID',
        'CARDINALITY_MATCH', 'ENTITY_RULES_MATCH', 'SYNTHETIC_EVIDENCE_ONLY'
    )
    failClosedReasons = @(
        'PROFILE_BINDING_MISMATCH', 'ORACLE_MISSING', 'AUTHORIZATION_MISSING',
        'SOURCE_INSTANCE_MISSING', 'TENANT_SCOPE_MISSING', 'RESERVED_SCOPE_VALUE',
        'NON_SYNTHETIC_SCOPE_VALUE', 'ROOT_DRIFT', 'PATH_DRIFT', 'FIELD_SCOPE_MISMATCH',
        'ROOT_SCALAR_ROLE_DRIFT', 'FIELD_TYPE_MISMATCH', 'FILTER_CONTRACT_DRIFT',
        'RELATIONSHIP_INFERENCE_DRIFT', 'ABSENCE_POLICY_DRIFT', 'SOURCE_KEY_PATH_MISMATCH',
        'SOURCE_KEY_TAGGING_MISSING', 'SOURCE_KEY_MISSING', 'SOURCE_KEY_NULL',
        'SOURCE_KEY_TYPE_MISMATCH', 'SOURCE_KEY_VALUE_INVALID', 'SOURCE_KEY_COLLISION',
        'PRESENCE_MODEL_INCOMPLETE', 'PAGINATION_SEMANTICS_DRIFT', 'TERMINALITY_INCOMPLETE',
        'BYTE_LIMIT_EXCEEDED', 'ROW_LIMIT_EXCEEDED', 'PAGE_LIMIT_EXCEEDED',
        'PAGE_SIZE_EXCEEDED', 'DEPTH_LIMIT_EXCEEDED', 'PATH_LIMIT_EXCEEDED',
        'NODE_LIMIT_EXCEEDED', 'INVALID_TIMESTAMP', 'TEMPORAL_STATE_MISMATCH',
        'TIMEZONE_UNCONFIRMED', 'TIMEZONE_MISMATCH', 'TEMPORAL_PRECEDENCE_DRIFT',
        'STATUS_SEMANTICS_DRIFT', 'CARDINALITY_DRIFT', 'OBSERVATION_COUNT_MISMATCH',
        'LOGICAL_ENTITY_LIMIT_EXCEEDED', 'EXPANSION_SEMANTICS_DRIFT',
        'CHILD_IDENTITY_CONFLICT', 'CHILD_KEY_PATH_MISMATCH', 'CHILD_KEY_TAGGING_MISSING',
        'CHILD_ATTRIBUTE_ASYMMETRY', 'ORDER_PARITY_VIOLATION', 'GRAPHQL_PAGE_SIZE_EXCEEDED',
        'GRAPHQL_CURSOR_MISSING', 'GRAPHQL_CURSOR_REPEATED', 'GRAPHQL_CURSOR_CYCLE',
        'UNSUPPORTED_EVIDENCE_CLASSIFICATION'
    )
}

$expectedLimits = [ordered]@{
    maximumBytes = 65536
    maximumRows = 1000
    maximumPages = 100
    maximumDepth = 16
    maximumPaths = 256
    maximumNodes = 4096
}

$expectedProfiles = [ordered]@{
    COLETAS = [ordered]@{
        profileId = 'V2_012_COLETAS_6908'
        profileFile = 'coletas-6908.profile.json'
        profileSha256 = 'd0ffb7c54a935c3a62a9501791f6a8119c9f3ec8bc72d7171066778510e95a97'
        profileFingerprint = '50371854573065136e9dbe35c722c367a5afec3cecccb367fe6d41e882b33c46'
        fixtureFile = 'coletas-6908.synthetic.json'
        fixtureSha256 = '3faae2d7ab68da5a6431012a1d6bd1eb539752e6d003d6e1997d31b6215355b4'
        fixtureFingerprint = '1b2eecd3fa3b704dfbdcc9299059aa70e399e0a6f9c3ef15a5e5ea02ff8fdc15'
        sourceKind = 'DATA_EXPORT'
        oracleKind = 'DATA_EXPORT_PROVIDER'
        contractId = 'dataexport-6908'
        contractVersion = '2026-08-31.v2-025a.1'
        documentReference = 'dataexport-coletas'
        contractFingerprint = '052d84c37d9c55ae2b2b4891eb4771c68158ee7365402a1388cc68256c67cc59'
        identityFingerprint = '0ca2cd5ce2573d0bdec31d9bc141887f2d58bc79cd67d289981034a4865705e1'
        sourceKeyPath = '/id'
        sourceKeyWireTypes = @('INTEGER')
        paginationKind = 'NUMERIC_PAGE'
        maximumPageSize = 100
        temporalTranslation = 'BOUNDARIES_UNPROVEN'
        scenarioCount = 55
        acceptedScenarioCount = 3
        failClosedScenarioCount = 52
    }
    MANIFESTOS = [ordered]@{
        profileId = 'V2_012_MANIFESTOS_6399'
        profileFile = 'manifestos-6399.profile.json'
        profileSha256 = '2774893397b0ff7b5b881d021f9f734a092040dbfa1dc47bd0f60a21eb8a5da9'
        profileFingerprint = 'd99bf5dbf51b9a3764671aad3a7a76833214d1cbbfe8ee4af6a977734e7c7ba2'
        fixtureFile = 'manifestos-6399.synthetic.json'
        fixtureSha256 = '785ad1c6041105eb8e23941e9923fb57c4eaf58859515b14e66448e45dba0231'
        fixtureFingerprint = '3fe3d005923b9e2efbd9f6d3623f75d0ba4e4b49bd44ec5038eb90edbb565e09'
        sourceKind = 'DATA_EXPORT'
        oracleKind = 'DATA_EXPORT_PROVIDER'
        contractId = 'dataexport-6399'
        contractVersion = '2026-09-04.v2-025b.2'
        documentReference = 'dataexport-manifestos'
        contractFingerprint = '30181093f00f47507d1910d9806a97697118ab671aeae0598d21a4a2726a7cb8'
        identityFingerprint = 'ad2e3457a91961372dd29cb96bd6f2cb5fd6f67614d1255303787a8e2c4085c3'
        sourceKeyPath = '/sequence_code'
        sourceKeyWireTypes = @('INTEGER')
        paginationKind = 'NUMERIC_PAGE'
        maximumPageSize = 100
        temporalTranslation = 'SOURCE_CIVIL_DATE_UNPROVEN'
        scenarioCount = 41
        acceptedScenarioCount = 4
        failClosedScenarioCount = 37
    }
    COTACOES = [ordered]@{
        profileId = 'V2_012_COTACOES_6906'
        profileFile = 'cotacoes-6906.profile.json'
        profileSha256 = '0aff236196bae11046ecb4929096555fb9c86dd133deaaa84351d78a90027e3b'
        profileFingerprint = '57631b76ae16673a0a0bc795a95ca6bd2050a2665e77e5a6e20413cfc45b17e7'
        fixtureFile = 'cotacoes-6906.synthetic.json'
        fixtureSha256 = '49a5cf0f4f3be4ac748b4b84df11a44c4028309d93e81c00aa0cef68ead61449'
        fixtureFingerprint = 'fd6a189bde461b35d4a47cbdbe37c72d3d959edebec03d376e0ace96e33582eb'
        sourceKind = 'DATA_EXPORT'
        oracleKind = 'DATA_EXPORT_PROVIDER'
        contractId = 'dataexport-6906'
        contractVersion = '2026-09-04.v2-025b.1'
        documentReference = 'dataexport-cotacoes'
        contractFingerprint = '4bd641aa5ebe1c069776dc7009377d43c4c1d3e26727969667cc7adc01071166'
        identityFingerprint = '355db9b9998d0366378c763b10738c875772d98bc649bf7b3bcceabbfbe345cb'
        sourceKeyPath = '/sequence_code'
        sourceKeyWireTypes = @('INTEGER')
        paginationKind = 'NUMERIC_PAGE'
        maximumPageSize = 1000
        temporalTranslation = 'SOURCE_CIVIL_DATE_UNPROVEN'
        scenarioCount = 33
        acceptedScenarioCount = 3
        failClosedScenarioCount = 30
    }
    USUARIOS = [ordered]@{
        profileId = 'V2_012_USUARIOS_INDIVIDUAL'
        profileFile = 'usuarios-individual.profile.json'
        profileSha256 = '171b965c011d1f482b35fdb9e4dff60a7798d90ee83b96d084183d9c542fae2e'
        profileFingerprint = '44e33999e8ef260d050b6ceb75985ee236de343543ee478cc34facf57f3c58e4'
        fixtureFile = 'usuarios-individual.synthetic.json'
        fixtureSha256 = 'b0f917914e07589dbacbf5d12a7478b76feb3a930126f68fec233bc23eedbfc6'
        fixtureFingerprint = 'c72d02b44d7affb54a0d565c53540e2425d1b6af6b7e9a9b42608631cc58e5d8'
        sourceKind = 'GRAPHQL'
        oracleKind = 'GRAPHQL_PROVIDER'
        contractId = 'graphql-individual'
        contractVersion = '2026-08-31.v2-025a.1'
        documentReference = 'graphql-users-snapshot'
        contractFingerprint = 'a650e5e8313e6d45bff326dd8ab15920ba23da8e44e9237f538c4cbce76aca7c'
        identityFingerprint = '8e8ad89777277f3b8bf72b4b63f65d8fa813843380f44da99f0b035bbf788528'
        sourceKeyPath = '/node/id'
        sourceKeyWireTypes = @('INTEGER', 'STRING')
        paginationKind = 'RELAY_CURSOR'
        maximumPageSize = 20
        temporalTranslation = 'ABSENT_NO_TEMPORAL_FIELD'
        scenarioCount = 37
        acceptedScenarioCount = 3
        failClosedScenarioCount = 34
    }
}

$expectedSources = [ordered]@{
    'docs/adr/0004-paginacao-defensiva-dataexport.md' = '0aca9a4ea53c58c96cc661aff72ca77de17a32482b42887db916c2eeaa24cb06'
    'docs/adr/0005-harness-graphql-read-only-paridade.md' = '1c32b7fb4a4c1704a43dde9fccd178ed68498a5c08f8910e18e4c45f42461bc5'
    'docs/adr/0012-drift-de-contrato-antes-da-promocao.md' = '41c5af670de4b1b6894a89fd2b92fd7f552ece4d1c7adf61b0728a5e89d4a68c'
    'docs/adr/0016-adaptador-graphql-transitorio.md' = 'fc7d658f9db207fa781c00fc83f17ebb005ea96a5c12435f4159ba0c73156d0a'
    'docs/adr/0017-contratos-primeira-onda-e-completude.md' = 'ddef2eb8549a8c322648748bc89db9a40b7abab71027a94f988791196a250a5b'
    'docs/adr/0018-identidade-e-crosswalk-da-primeira-onda.md' = '8f948a04a87ad4173614243e3846b42dc65769c050b3363ebd67ece696488672'
    'docs/adr/0019-usuarios-current-history-em-sombra.md' = '8c14cebc95add8299ddbb9838836050f100d907be8801e5ea66b4bd3df72b9be'
    'docs/adr/0023-coletas-6908-dominio-presenca-frescor-status-e-schema.md' = '1841ada44ea415cb9ded94e6077ea0eb60f895cc30f8ef939d6d0039eaa800ea'
    'docs/adr/0024-manifestos-6399-identidade-presenca-frescor-reducers.md' = 'f83fef24452997a58ce9637355300281a3233fd360667c4f9bf9e7125ffe6ef1'
    'docs/adr/0025-frota-manifestos-identidade-dimensional-fail-closed.md' = 'c8a7ee99bc870bbd7fc6839fa40ad83b6b7c1724ab7cd2a23895e5645012eacf'
    'docs/catalogos/contratos-primeira-onda/manifesto.json' = 'd7910ff0e3d0f78bee57aaad5178561db8bcb0d0d253448531b606bcef45950c'
    'docs/catalogos/identidade-primeira-onda/manifesto.json' = '15348b0074af78a29867b33a6333f6d7d20e1b3ee31300162d26dc54c7c33c2b'
    'docs/catalogos/coletas-v2-010/decisao-v01.json' = '077c4e806c3174dd4909e94e99a72847ba6b76868fce492561f9bb307a85422c'
    'docs/catalogos/contratos-esl-6399/manifesto.json' = 'fc19e50cdfe0e91fcd192a17b65d6cc48f8f62dca13f755006d4e0fcfb8b86bd'
    'docs/catalogos/identidade-manifestos/manifesto.json' = '290ef5cbba142de8cf07c0a17948a8571ba7c9ac9673b0910786a960514e5a23'
    'docs/catalogos/manifestos-v2-026/decisao-v03.json' = '7721b88a934b2a58ff19bd7cee53f7bd57023a31528c77143915d72005cd19af'
    'docs/catalogos/contratos-esl-6906/manifesto.json' = 'ce93c3e051381dcfa570d858262817f7d8c9cd3248b505cbff60c45f61c4dff9'
    'docs/catalogos/identidade-cotacoes/manifesto.json' = '3c41036eff9ed4ce3357d3fcb4c5119a1a82222ce34493b4c428532413e7f811'
    'docs/catalogos/cotacoes-v2-027/decisao-v01.json' = '96a425c653e00889a1a4d6e97389e549468372ed252d66727191ca4aa0ea14ef'
    'docs/runbooks/revalidar-contrato-dataexport-coletas-fretes.md' = 'a47c0b9833f8c676995b4853195e4052551934a5673689cb55082c3b03f4113e'
    'docs/runbooks/usuarios-current-history-em-sombra.md' = '74a64aaa682803d3d5e22c6d80b33beb30a8f6f7608c1868fd30975788deee5b'
}

Assert-FileInventory -Directory $profilesRoot -Expected @(
    'coletas-6908.profile.json', 'manifestos-6399.profile.json',
    'cotacoes-6906.profile.json', 'usuarios-individual.profile.json'
) -Label 'de perfis V2-012'
Assert-FileInventory -Directory $fixturesRoot -Expected @(
    'coletas-6908.synthetic.json', 'manifestos-6399.synthetic.json',
    'cotacoes-6906.synthetic.json', 'usuarios-individual.synthetic.json'
) -Label 'de fixtures V2-012'

if (Test-Path -LiteralPath (Join-Path $repositoryRoot 'src\main\java\br\com\esl\etl\v2\contratos\caracterizacao')) {
    throw 'A fundação V2-012 escapou do escopo test-only.'
}
$adrInventory = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'docs\adr') -Filter '0026-*' -File)
if ($adrInventory.Count -ne 1 -or $adrInventory[0].FullName -cne $adrPath) {
    throw 'O ADR 0026 está ausente, duplicado ou foi sobrescrito por outro artefato.'
}

$manifestDocument = Read-StrictJson -LiteralPath $manifestPath -MaximumBytes 512KB -Label 'da fundação V2-012'
$manifest = $manifestDocument.Value
Assert-Equal $manifest.schemaVersion 'V2_012_CHARACTERIZATION_FOUNDATION_V1' 'schemaVersion do manifesto'
Assert-Equal $manifest.task.route 'Q-FND-01' 'route da fundação'
Assert-Equal $manifest.task.block 39 'bloco da fundação'
Assert-Equal $manifest.task.taskId 'V2-012/FUNDACAO_LOCAL' 'taskId da fundação'
Assert-Equal $manifest.task.slice 'FUNDACAO_OFFLINE_PROVIDER_NEUTRAL' 'fatia da fundação'
Assert-Equal $manifest.outcome 'FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES' 'resultado agregado'
Assert-Equal $manifest.foundation.executionMode 'OFFLINE_TEST_ONLY' 'modo de execução'
Assert-Equal $manifest.foundation.evidenceClassification 'SYNTHETIC_FIXTURE' 'classificação da evidência'
Assert-Equal $manifest.foundation.providerEvidence 'NOT_EXECUTED' 'execução do fornecedor'
Assert-Equal $manifest.foundation.outputRoot 'target/v2-012-characterization' 'raiz de receipts'
foreach ($flag in @('aggregateProviderPass', 'networkAccess', 'realPayloads', 'databaseAccess', 'runtimeExecution', 'externalConfiguration')) {
    Assert-RequiredProperty -Object $manifest.foundation -Name $flag -Label 'foundation'
    if ($manifest.foundation.$flag -isnot [bool] -or $manifest.foundation.$flag) {
        throw "O flag foundation.$flag deve permanecer boolean false."
    }
}

foreach ($name in $expectedVocabularies.Keys) {
    Assert-RequiredProperty -Object $manifest.closedVocabularies -Name $name -Label 'closedVocabularies'
    Assert-ExactSet -Actual @($manifest.closedVocabularies.PSObject.Properties[$name].Value) -Expected $expectedVocabularies[$name] -Label "closedVocabularies.$name"
}
foreach ($name in $expectedLimits.Keys) {
    Assert-RequiredProperty -Object $manifest.limits -Name $name -Label 'limits'
    Assert-Equal $manifest.limits.$name $expectedLimits[$name] "limits.$name"
}

$sourcePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($source in @($manifest.canonicalSources)) {
    if (-not $sourcePaths.Add([string]$source.path)) {
        throw 'O manifesto contém referência canônica duplicada.'
    }
    if (-not $expectedSources.Contains([string]$source.path)) {
        throw "O manifesto contém referência canônica desconhecida '$($source.path)'."
    }
    Assert-Equal $source.sha256 $expectedSources[[string]$source.path] "hash catalogado de $($source.path)"
    if ([string]$source.sha256 -cnotmatch '^[0-9a-f]{64}$') {
        throw "A referência '$($source.path)' contém SHA-256 inválido."
    }
    $resolvedSource = Resolve-RepositoryFile -RelativePath ([string]$source.path)
    Assert-Equal (Get-FileSha256 -LiteralPath $resolvedSource) $source.sha256 "hash real de $($source.path)"
}
Assert-ExactSet -Actual @($sourcePaths) -Expected @($expectedSources.Keys) -Label 'de referências canônicas'

$wireTypes = @('INTEGER', 'STRING', 'BOOLEAN', 'DECIMAL', 'OBJECT', 'ARRAY')
$sourceKeyDomains = @('CANONICAL_INTEGER', 'POSITIVE_INTEGER', 'CANONICAL_INTEGER_OR_NON_BLANK_STRING')
$childKinds = @('PICK', 'MDFE')
$childDomains = @('POSITIVE_INTEGER', 'FIXED_44_DIGIT_STRING')
$filterRoles = @('REQUIRED', 'COMPLEMENTARY')
$perSemantics = @('DISTINCT_SOURCE_KEYS_WITH_PHYSICAL_EXPANSION', 'UNPROVEN', 'MAXIMUM_FIRST_ARGUMENT')
$logicalGrains = @('DISTINCT_SCOPED_ID', 'DISTINCT_SCOPED_SEQUENCE_CODE', 'ONE_SCOPED_NODE_PER_EDGE')
$rootCardinalities = @('ARRAY_ELEMENTS', 'GRAPHQL_EDGE_NODES')
$expansionPolicies = @('PHYSICAL_ROWS_MAY_EXCEED_LOGICAL_ROOTS', 'PHYSICAL_ROWS_PRESERVE_DISTINCT_CHILDREN', 'NO_CHILD_OR_EXPANSION_PROVEN', 'ONE_NODE_PER_EDGE')
$childPolicies = @('PHYSICAL_EXPANSION_NEVER_CREATES_A_NEW_LOGICAL_ROOT', 'PICK_AND_MDFE_KEYS_ARE_CHILD_LOCAL_WITHOUT_RELATION_INFERENCE', 'NO_CHILD_IDENTITY_OBSERVED', 'NO_DATA_EXPORT_OR_INCREMENTAL_INFERENCE')
$absencePolicies = @('OBSERVATION_ONLY_NO_LIFECYCLE_INFERENCE', 'NO_DEACTIVATION_BY_ABSENCE')
$statusModes = @('CLOSED_CATALOG', 'KNOWN_PRECEDENCE_UNKNOWN_PRESERVED', 'NOT_APPLICABLE')

$manifestEntities = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$allScenarioIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$totalScenarios = 0
$totalAccepted = 0
foreach ($entry in @($manifest.profiles)) {
    $entity = [string]$entry.entity
    if (-not $manifestEntities.Add($entity) -or -not $expectedProfiles.Contains($entity)) {
        throw "O manifesto contém perfil duplicado ou desconhecido '$entity'."
    }
    $expected = $expectedProfiles[$entity]
    $expectedProfilePath = "src/test/resources/contracts/v2-012/profiles/$($expected.profileFile)"
    $expectedFixturePath = "src/test/resources/contracts/v2-012/fixtures/$($expected.fixtureFile)"
    Assert-Equal $entry.profileId $expected.profileId "$entity.profileId"
    Assert-Equal $entry.sourceKind $expected.sourceKind "$entity.sourceKind"
    Assert-Equal $entry.oracleKind $expected.oracleKind "$entity.oracleKind"
    Assert-Equal $entry.oracleExecutionStatus 'NOT_EXECUTED' "$entity.oracleExecutionStatus"
    Assert-Equal $entry.authorizationRequirement 'EXPLICIT_OWNER_AUTHORIZATION' "$entity.authorizationRequirement"
    Assert-Equal $entry.sourceInstanceRequirement 'REQUIRED_AT_EXECUTION' "$entity.sourceInstanceRequirement"
    Assert-Equal $entry.tenantScopeRequirement 'REQUIRED_AT_EXECUTION' "$entity.tenantScopeRequirement"
    Assert-Equal $entry.profileStatus 'PREPARED_NOT_EXECUTED' "$entity.profileStatus"
    Assert-Equal $entry.gateStatus 'ORACLE_REQUIRED' "$entity.gateStatus"
    Assert-Equal $entry.profilePath $expectedProfilePath "$entity.profilePath"
    Assert-Equal $entry.fixturePath $expectedFixturePath "$entity.fixturePath"
    Assert-Equal $entry.profileSha256 $expected.profileSha256 "$entity.profileSha256"
    Assert-Equal $entry.profileFingerprint $expected.profileFingerprint "$entity.profileFingerprint"
    Assert-Equal $entry.fixtureSha256 $expected.fixtureSha256 "$entity.fixtureSha256"
    Assert-Equal $entry.fixtureFingerprint $expected.fixtureFingerprint "$entity.fixtureFingerprint"
    Assert-Equal $entry.scenarioCount $expected.scenarioCount "$entity.scenarioCount"
    Assert-Equal $entry.acceptedScenarioCount $expected.acceptedScenarioCount "$entity.acceptedScenarioCount"
    Assert-Equal $entry.failClosedScenarioCount $expected.failClosedScenarioCount "$entity.failClosedScenarioCount"

    foreach ($contractProperty in @('contractId', 'contractVersion', 'documentReference', 'contractFingerprint', 'identityFingerprint')) {
        Assert-Equal $entry.contract.$contractProperty $expected[$contractProperty] "$entity.contract.$contractProperty"
    }
    Assert-Equal $entry.sourceKey.path $expected.sourceKeyPath "$entity.sourceKey.path"
    Assert-ExactSet -Actual @($entry.sourceKey.wireTypes) -Expected $expected.sourceKeyWireTypes -Label "$entity.sourceKey.wireTypes"
    if ($entry.sourceKey.typeTagged -isnot [bool] -or -not $entry.sourceKey.typeTagged) {
        throw "A source key de $entity deve permanecer type-tagged."
    }

    $profilePath = Resolve-RepositoryFile -RelativePath $expectedProfilePath
    $fixturePath = Resolve-RepositoryFile -RelativePath $expectedFixturePath
    Assert-Equal (Get-FileSha256 -LiteralPath $profilePath) $expected.profileSha256 "hash bruto do perfil $entity"
    Assert-Equal (Get-FileSha256 -LiteralPath $fixturePath) $expected.fixtureSha256 "hash bruto da fixture $entity"
    $profile = (Read-StrictJson -LiteralPath $profilePath -MaximumBytes 128KB -Label "do perfil $entity").Value
    $fixture = (Read-StrictJson -LiteralPath $fixturePath -MaximumBytes 256KB -Label "da fixture $entity").Value

    Assert-Equal $profile.schemaVersion 'V2_012_PROFILE_V1' "$entity.schemaVersion"
    Assert-Equal $profile.profileId $expected.profileId "$entity.profile.profileId"
    Assert-Equal $profile.entity $entity "$entity.profile.entity"
    Assert-Equal $profile.sourceKind $expected.sourceKind "$entity.profile.sourceKind"
    Assert-Equal $profile.oracleKind $expected.oracleKind "$entity.profile.oracleKind"
    Assert-Equal $profile.authorizationRequirement 'EXPLICIT_OWNER_AUTHORIZATION' "$entity.profile.authorizationRequirement"
    Assert-Equal $profile.profileStatus 'PREPARED_NOT_EXECUTED' "$entity.profile.profileStatus"
    Assert-Equal $profile.gateStatus 'ORACLE_REQUIRED' "$entity.profile.gateStatus"
    Assert-Equal $profile.fixtureResource "/contracts/v2-012/fixtures/$($expected.fixtureFile)" "$entity.fixtureResource"
    Assert-Equal $profile.fixtureFingerprint $expected.fixtureFingerprint "$entity.profile.fixtureFingerprint"
    Assert-ExactSet -Actual @($profile.stages) -Expected $expectedVocabularies.stages -Label "$entity.stages"
    Assert-ExactSet -Actual @($profile.requiredChecks) -Expected $expectedVocabularies.requiredChecks -Label "$entity.requiredChecks"
    Assert-ExactSet -Actual @($profile.scope.forbiddenValues) -Expected @('DEFAULT', 'GLOBAL', 'SINGLETON') -Label "$entity.scope.forbiddenValues"
    Assert-Equal $profile.scope.sourceInstance 'REQUIRED_AT_EXECUTION' "$entity.scope.sourceInstance"
    Assert-Equal $profile.scope.tenantScope 'REQUIRED_AT_EXECUTION' "$entity.scope.tenantScope"
    foreach ($limitName in $expectedLimits.Keys) {
        Assert-Equal $profile.limits.$limitName $expectedLimits[$limitName] "$entity.profile.limits.$limitName"
    }
    foreach ($contractProperty in @('contractId', 'contractVersion', 'documentReference', 'contractFingerprint', 'identityFingerprint')) {
        Assert-Equal $profile.contract.$contractProperty $expected[$contractProperty] "$entity.profile.contract.$contractProperty"
    }
    Assert-Equal $profile.sourceKey.path $expected.sourceKeyPath "$entity.profile.sourceKey.path"
    Assert-ExactSet -Actual @($profile.sourceKey.wireTypes) -Expected $expected.sourceKeyWireTypes -Label "$entity.profile.sourceKey.wireTypes"
    Assert-Member $profile.sourceKey.domain $sourceKeyDomains "$entity.sourceKey.domain"
    Assert-Equal $profile.pagination.kind $expected.paginationKind "$entity.pagination.kind"
    Assert-Equal $profile.pagination.maximumPageSize $expected.maximumPageSize "$entity.pagination.maximumPageSize"
    Assert-Member $profile.pagination.terminalCondition $expectedVocabularies.terminalConditions "$entity.pagination.terminalCondition"
    Assert-Member $profile.pagination.perSemantics $perSemantics "$entity.pagination.perSemantics"
    if ($profile.pagination.shortPageIsTerminal -or $profile.pagination.completenessProven -or $profile.pagination.snapshotProven) {
        throw "O perfil $entity inventa terminalidade curta, completude ou snapshot."
    }
    Assert-Member $profile.ordering.role $expectedVocabularies.orderingRoles "$entity.ordering.role"
    Assert-Equal $profile.temporal.translation $expected.temporalTranslation "$entity.temporal.translation"
    Assert-Member $profile.temporal.timezoneRequirement $expectedVocabularies.timezoneRequirements "$entity.temporal.timezoneRequirement"
    Assert-Member $profile.rootGrain.logicalGrain $logicalGrains "$entity.rootGrain.logicalGrain"
    Assert-Member $profile.rootGrain.cardinality $rootCardinalities "$entity.rootGrain.cardinality"
    Assert-Member $profile.rootGrain.expansionPolicy $expansionPolicies "$entity.rootGrain.expansionPolicy"
    Assert-Member $profile.rootGrain.childPolicy $childPolicies "$entity.rootGrain.childPolicy"
    Assert-Member $profile.absencePolicy $absencePolicies "$entity.absencePolicy"
    Assert-Member $profile.status.mode $statusModes "$entity.status.mode"
    foreach ($filter in @($profile.filters)) {
        Assert-Member $filter.role $filterRoles "$entity.filter.role"
    }
    foreach ($field in @($profile.fieldContracts)) {
        foreach ($wireType in @($field.wireTypes)) { Assert-Member $wireType $wireTypes "$entity.field.wireType" }
        foreach ($presence in @($field.presenceStates)) { Assert-Member $presence $expectedVocabularies.presence "$entity.field.presence" }
    }
    foreach ($child in @($profile.childIdentities)) {
        Assert-Member $child.kind $childKinds "$entity.child.kind"
        Assert-Member $child.domain $childDomains "$entity.child.domain"
        foreach ($wireType in @($child.wireTypes)) { Assert-Member $wireType $wireTypes "$entity.child.wireType" }
    }

    Assert-Equal $fixture.schemaVersion 'V2_012_FIXTURE_V1' "$entity.fixture.schemaVersion"
    if ([string]$fixture.fixtureMarker -cnotmatch '^SYNTH_[A-Z0-9_]{3,127}$') {
        throw "A fixture $entity não possui marcador sintético válido."
    }
    Assert-Equal $fixture.profileId $expected.profileId "$entity.fixture.profileId"
    Assert-Equal $fixture.baseObservation.entity $entity "$entity.fixture.entity"
    Assert-Equal $fixture.baseObservation.sourceKind $expected.sourceKind "$entity.fixture.sourceKind"
    Assert-Equal $fixture.baseObservation.oracleKind $expected.oracleKind "$entity.fixture.oracleKind"
    Assert-Equal $fixture.baseObservation.oracleAvailability 'SYNTHETIC_IN_MEMORY_ONLY' "$entity.fixture.oracleAvailability"
    Assert-Equal $fixture.baseObservation.authorizationState 'SYNTHETIC_TEST_ONLY' "$entity.fixture.authorizationState"
    Assert-Equal $fixture.baseObservation.sourceInstance 'SYNTH_SOURCE_INSTANCE' "$entity.fixture.sourceInstance"
    Assert-Equal $fixture.baseObservation.tenantScope 'SYNTH_TENANT_SCOPE' "$entity.fixture.tenantScope"
    Assert-Equal $fixture.baseObservation.evidenceClassification 'SYNTHETIC_FIXTURE' "$entity.fixture.evidenceClassification"
    Assert-Member $fixture.baseObservation.sourceKeyPresence $expectedVocabularies.presence "$entity.fixture.sourceKeyPresence"
    Assert-Member $fixture.baseObservation.rootCardinality $rootCardinalities "$entity.fixture.rootCardinality"
    Assert-Member $fixture.baseObservation.pagination.kind $expectedVocabularies.paginationKinds "$entity.fixture.pagination.kind"
    Assert-Member $fixture.baseObservation.pagination.terminalCondition $expectedVocabularies.terminalConditions "$entity.fixture.pagination.terminalCondition"
    Assert-Member $fixture.baseObservation.temporal.timestampState $expectedVocabularies.timestampStates "$entity.fixture.timestampState"
    Assert-Member $fixture.baseObservation.temporal.timezoneState $expectedVocabularies.timezoneStates "$entity.fixture.timezoneState"
    Assert-Member $fixture.baseObservation.temporal.observedTranslation $expectedVocabularies.temporalTranslations "$entity.fixture.observedTranslation"
    Assert-Member $fixture.baseObservation.status.mode $statusModes "$entity.fixture.status.mode"

    $scenarioIds = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $acceptedCount = 0
    foreach ($scenario in @($fixture.scenarios)) {
        if ([string]$scenario.scenarioId -cnotmatch '^SYNTH_[A-Z0-9_]{3,127}$' -or -not $scenarioIds.Add([string]$scenario.scenarioId)) {
            throw "A fixture $entity contém cenário sem marcador sintético ou duplicado."
        }
        [void]$allScenarioIds.Add([string]$scenario.scenarioId)
        Assert-Member $scenario.expectedOutcome $expectedVocabularies.scenarioOutcomes "$entity.$($scenario.scenarioId).expectedOutcome"
        foreach ($reason in @($scenario.expectedReasons)) {
            Assert-Member $reason $expectedVocabularies.failClosedReasons "$entity.$($scenario.scenarioId).reason"
        }
        if ($scenario.expectedOutcome -ceq 'SYNTHETIC_STRUCTURE_ACCEPTED') {
            if (@($scenario.expectedReasons).Count -ne 0) { throw "O cenário aceito $entity/$($scenario.scenarioId) contém motivo de falha." }
            $acceptedCount++
        } elseif (@($scenario.expectedReasons).Count -eq 0) {
            throw "O cenário fail-closed $entity/$($scenario.scenarioId) não contém motivo."
        }
        foreach ($scopeName in @('sourceInstance', 'tenantScope')) {
            $overridePropertyNames = @($scenario.override.PSObject.Properties | ForEach-Object Name)
            if ($overridePropertyNames -ccontains $scopeName) {
                $scopeValue = $scenario.override.$scopeName
                if ($null -eq $scopeValue -or [string]$scopeValue -ceq '') {
                    $requiredReason = if ($scopeName -ceq 'sourceInstance') { 'SOURCE_INSTANCE_MISSING' } else { 'TENANT_SCOPE_MISSING' }
                    if (-not (Test-HasReason -Scenario $scenario -Reason $requiredReason)) { throw "Escopo vazio sem contraprova explícita em $entity/$($scenario.scenarioId)." }
                } elseif (@('DEFAULT', 'GLOBAL', 'SINGLETON') -icontains [string]$scopeValue) {
                    if (-not (Test-HasReason -Scenario $scenario -Reason 'RESERVED_SCOPE_VALUE')) { throw "Sentinel reservado sem contraprova explícita em $entity/$($scenario.scenarioId)." }
                } elseif ([string]$scopeValue -ceq 'UNMARKED_SYNTHETIC_SCOPE') {
                    if (-not (Test-HasReason -Scenario $scenario -Reason 'NON_SYNTHETIC_SCOPE_VALUE')) { throw "Escopo não marcado sem contraprova explícita em $entity/$($scenario.scenarioId)." }
                } elseif (-not ([string]$scopeValue).StartsWith('SYNTH_', [System.StringComparison]::Ordinal)) {
                    throw "A fixture $entity contém source_instance/tenant_scope real ou inventado."
                }
            }
        }
    }
    Assert-Equal $scenarioIds.Count $expected.scenarioCount "$entity.fixture.scenarioCount real"
    Assert-Equal $acceptedCount $expected.acceptedScenarioCount "$entity.fixture.acceptedScenarioCount real"
    Assert-Equal ($scenarioIds.Count - $acceptedCount) $expected.failClosedScenarioCount "$entity.fixture.failClosedScenarioCount real"
    $totalScenarios += $scenarioIds.Count
    $totalAccepted += $acceptedCount
}
Assert-ExactSet -Actual @($manifestEntities) -Expected @($expectedProfiles.Keys) -Label 'de entidades dos perfis'
Assert-Equal $totalScenarios 166 'total de cenários sintéticos'
Assert-Equal $totalAccepted 13 'total de cenários aceitos'

$requiredCounterexamples = @(
    'SYNTH_ORACLE_MISSING', 'SYNTH_AUTHORIZATION_MISSING',
    'SYNTH_RESERVED_SOURCE_INSTANCE_DEFAULT', 'SYNTH_SOURCE_KEY_FIELD_WEAKEST_PRESENCE',
    'SYNTH_RELAY_FLAG_ON_NUMERIC_PAGE', 'SYNTH_SHORT_PAGE_FALSE_TERMINAL',
    'SYNTH_TEMPORAL_TRANSLATION_DRIFT', 'SYNTH_STATUS_COUNT_PRESENCE_MISMATCH',
    'SYNTH_CHILD_DISTINCT_ZERO_WITH_COUNT', 'SYNTH_MDFE_ATTRIBUTE_AS_IDENTITY',
    'SYNTH_ORDER_TIE_VIOLATION', 'SYNTH_TEMPORAL_VALID_WITHOUT_VALUES',
    'SYNTH_CURSOR_MISSING', 'SYNTH_CURSOR_REPEATED', 'SYNTH_CURSOR_CYCLE',
    'SYNTH_HAS_NEXT_AT_LIMIT', 'SYNTH_DEACTIVATION_BY_ABSENCE'
)
Assert-ExactSet -Actual @($manifest.counterexamples) -Expected $requiredCounterexamples -Label 'de contraexemplos catalogados'
foreach ($counterexample in $requiredCounterexamples) {
    if (-not $allScenarioIds.Contains($counterexample)) { throw "O contraexemplo '$counterexample' não existe nas fixtures." }
}

$expectedQ01 = [ordered]@{
    'Q-USR-01' = @('USUARIOS', 'CANDIDATO', 'GRAPHQL_PROVIDER')
    'Q-COL-01' = @('COLETAS', 'CANDIDATO', 'DATA_EXPORT_PROVIDER')
    'Q-MAN-01' = @('MANIFESTOS', 'EXTERNAL_HOLD', 'DATA_EXPORT_PROVIDER')
    'Q-COT-01' = @('COTACOES', 'CANDIDATO', 'DATA_EXPORT_PROVIDER')
}
$actualQ01 = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($route in @($manifest.entityQ01)) {
    if (-not $actualQ01.Add([string]$route.route) -or -not $expectedQ01.Contains([string]$route.route)) {
        throw 'O manifesto contém rota Q-*-01 duplicada ou desconhecida.'
    }
    $expectedRoute = $expectedQ01[[string]$route.route]
    Assert-Equal $route.entity $expectedRoute[0] "$($route.route).entity"
    Assert-Equal $route.trailStatus $expectedRoute[1] "$($route.route).trailStatus"
    Assert-Equal $route.completion 'OPEN' "$($route.route).completion"
    Assert-Equal $route.requiredOracle $expectedRoute[2] "$($route.route).requiredOracle"
}
Assert-ExactSet -Actual @($actualQ01) -Expected @($expectedQ01.Keys) -Label 'de rotas Q-*-01 da fundação'

Assert-ExactSet -Actual @($manifest.futureGates) -Expected @(
    'EXPLICIT_OWNER_AUTHORIZATION', 'EXPLICIT_SOURCE_INSTANCE', 'EXPLICIT_TENANT_SCOPE',
    'AUTHORIZED_ENTITY_ORACLE', 'SANITIZED_STRUCTURAL_EVIDENCE', 'INDEPENDENT_ENTITY_RESULT'
) -Label 'de gates futuros'
Assert-ExactSet -Actual @($manifest.prohibitedScope) -Expected @(
    'BOOTSTRAP', 'R01', 'R02', 'REAL_PARITY', 'SWEEP', 'PUBLICATION', 'CUTOVER', 'E2E', 'FLEET_IMPLEMENTATION'
) -Label 'de fases proibidas'

$statesText = Read-StrictUtf8 -LiteralPath $statesPath -MaximumBytes 2MB
$trailText = Read-StrictUtf8 -LiteralPath $trailPath -MaximumBytes 2MB
$entityQ01RoutePattern = 'Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-01'
if ([regex]::IsMatch($trailText, "(?m)^- \[x\].*\| ROTA=$entityQ01RoutePattern \|")) {
    throw 'Uma rota Q-*-01 de entidade foi indevidamente concluída.'
}
$openQ01Count = [regex]::Matches($trailText, "(?m)^- \[ \] STATUS=(?:CANDIDATO|EXTERNAL_HOLD) \| ROTA=$entityQ01RoutePattern \|").Count
Assert-Equal $openQ01Count 10 'quantidade de rotas Q-*-01 abertas'
foreach ($route in $expectedQ01.Keys) {
    $status = $expectedQ01[$route][1]
    if (-not $trailText.Contains("- [ ] STATUS=$status | ROTA=$route |")) {
        throw "A trilha não preserva $route em $status."
    }
}
foreach ($stateMarker in @('- [ ] **V2-012 —', '  - [ ] **V2-012a —', '  - [ ] **V2-012b —', '  - [ ] **V2-012c —')) {
    if (-not $statesText.Contains($stateMarker)) { throw "STATES.md não preserva o marcador aberto '$stateMarker'." }
}
if ([regex]::Matches($statesText, '(?m)^\s*- \[x\] \*\*V2-012/FUNDACAO_LOCAL\b').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-FND-01 \| BLOCO=39 \| TAREFA=V2-012/FUNDACAO_LOCAL .*\| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES \| EVIDENCIA=STATES\.md$').Count -ne 1) {
    throw 'O fechamento canônico de Q-FND-01/V2-012/FUNDACAO_LOCAL não está íntegro.'
}
if ([regex]::Matches($trailText, "(?m)^- \[ \] STATUS=AGORA \| ROTA=$entityQ01RoutePattern \|").Count -ne 0) {
    throw 'O fechamento da fundação não autoriza promover uma caracterização Q-*-01 sem oráculo.'
}

$requiredDocuments = @($readmePath, $runbookPath, $adrPath)
foreach ($documentPath in $requiredDocuments) {
    $documentText = Read-StrictUtf8 -LiteralPath $documentPath -MaximumBytes 512KB
    if ($documentText -match '(?im)^\s*(curl|sqlcmd)\b|Invoke-WebRequest|jdbc:') {
        throw "O documento '$([System.IO.Path]::GetFileName($documentPath))' incorporou comando externo ou de banco."
    }
}
$runbookText = Read-StrictUtf8 -LiteralPath $runbookPath -MaximumBytes 512KB
foreach ($requiredRunbookMarker in @('--offline', 'Test-V2012CharacterizationFoundation.ps1', 'PREPARED_NOT_EXECUTED', 'ORACLE_REQUIRED')) {
    if (-not $runbookText.Contains($requiredRunbookMarker)) { throw "O runbook não contém '$requiredRunbookMarker'." }
}

$boundaryTokens = @(
    'java.net', 'java.sql', 'System.getenv', 'System.getProperty', 'ContractRemoteExecution',
    'ContractTestConfiguration', 'DataExportTemplate', 'HttpClient', 'ProcessBuilder',
    'jdbc:', 'http://', 'https://'
)
foreach ($sourceFile in @(Get-ChildItem -LiteralPath $implementationRoot -File -Filter '*.java' | Where-Object Name -NotLike '*Test.java')) {
    $sourceText = Read-StrictUtf8 -LiteralPath $sourceFile.FullName -MaximumBytes 256KB
    foreach ($token in $boundaryTokens) {
        if ($sourceText.Contains($token)) { throw "A implementação '$($sourceFile.Name)' contém boundary proibida '$token'." }
    }
}

Write-Output 'V2-012 characterization foundation: PASS (4 profiles, 4 synthetic fixtures, 166 scenarios, 13 accepted structures, Q-*-01 open).'

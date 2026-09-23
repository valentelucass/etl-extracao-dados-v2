#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$catalogRoot = Join-Path $repositoryRoot 'docs\catalogos\fretes-v2-011'
$decisionPath = Join-Path $catalogRoot 'decisao-v01.json'
$fixturePath = Join-Path $catalogRoot 'fixtures\casos-v01.synthetic.json'
$contractPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-primeira-onda\manifesto.json'
$identityPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-primeira-onda\manifesto.json'
$graphqlCatalogPath = Join-Path $repositoryRoot 'docs\catalogos\graphql-transitorio.csv'

function Assert-Condition {
    param(
        [Parameter(Mandatory)][bool] $Condition,
        [Parameter(Mandatory)][string] $Reason
    )

    if (-not $Condition) {
        throw $Reason
    }
}

function Assert-ExactString {
    param(
        [Parameter()][AllowNull()][object] $Value,
        [Parameter(Mandatory)][string] $Expected,
        [Parameter(Mandatory)][string] $Reason
    )

    Assert-Condition -Condition ($Value -is [string] -and $Value -ceq $Expected) -Reason $Reason
}

function Assert-ExactProperties {
    param(
        [Parameter(Mandatory)][object] $Value,
        [Parameter(Mandatory)][string[]] $Expected,
        [Parameter(Mandatory)][string] $Reason
    )

    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    Assert-Condition -Condition ($actual.Count -eq $Expected.Count) -Reason $Reason
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($name in $actual) {
        Assert-Condition -Condition $actualSet.Add($name) -Reason $Reason
    }
    foreach ($name in $Expected) {
        Assert-Condition -Condition $actualSet.Contains($name) -Reason $Reason
    }
}

function Assert-ExactSet {
    param(
        [Parameter(Mandatory)][object[]] $Actual,
        [Parameter(Mandatory)][string[]] $Expected,
        [Parameter(Mandatory)][string] $Reason
    )

    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in $Actual) {
        Assert-Condition -Condition ($item -is [string] -and $actualSet.Add($item)) -Reason $Reason
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($item in $Expected) {
        Assert-Condition -Condition $expectedSet.Add($item) -Reason $Reason
    }
    Assert-Condition -Condition ($actualSet.Count -eq $expectedSet.Count) -Reason $Reason
    foreach ($item in $expectedSet) {
        Assert-Condition -Condition $actualSet.Contains($item) -Reason $Reason
    }
}

function Assert-JsonArray {
    param(
        [Parameter()][AllowNull()][object] $Value,
        [Parameter(Mandatory)][string] $Reason
    )

    Assert-Condition -Condition ($Value -is [System.Array]) -Reason $Reason
}

function Assert-NoDuplicateJsonProperties {
    param([Parameter(Mandatory)][System.Text.Json.JsonElement] $Element)

    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) {
                throw 'JSON_DUPLICATE_PROPERTY'
            }
            Assert-NoDuplicateJsonProperties -Element $property.Value
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJsonProperties -Element $item
        }
    }
}

function Read-StrictJson {
    param(
        [Parameter(Mandatory)][string] $LiteralPath,
        [Parameter(Mandatory)][long] $MaximumBytes,
        [Parameter(Mandatory)][string] $ReasonPrefix
    )

    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Leaf)) {
        throw "${ReasonPrefix}_MISSING"
    }
    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    $linkType = $item.PSObject.Properties['LinkType']
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
        ($null -ne $linkType -and $null -ne $linkType.Value)) {
        throw "${ReasonPrefix}_LINK_NOT_ALLOWED"
    }
    Assert-Condition -Condition ($item.FullName.StartsWith(
        $repositoryRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase
    )) -Reason "${ReasonPrefix}_OUTSIDE_REPOSITORY"
    Assert-Condition -Condition ($item.Length -le $MaximumBytes) -Reason "${ReasonPrefix}_TOO_LARGE"
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
    try {
        $text = $utf8.GetString($bytes)
    } catch [System.Text.DecoderFallbackException] {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
    if ($text.Contains([char] 0) -or $text.Contains([string][char] 0xFFFD, [System.StringComparison]::Ordinal)) {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
    $document = $null
    try {
        $options = [System.Text.Json.JsonDocumentOptions]::new()
        $options.AllowTrailingCommas = $false
        $options.CommentHandling = [System.Text.Json.JsonCommentHandling]::Disallow
        $document = [System.Text.Json.JsonDocument]::Parse($text, $options)
        Assert-NoDuplicateJsonProperties -Element $document.RootElement
        $value = $text | ConvertFrom-Json -Depth 100 -DateKind String
    } catch {
        if ($_.Exception.Message -ceq 'JSON_DUPLICATE_PROPERTY') {
            throw "${ReasonPrefix}_DUPLICATE_PROPERTY"
        }
        throw "${ReasonPrefix}_MALFORMED"
    } finally {
        if ($null -ne $document) {
            $document.Dispose()
        }
    }
    return [pscustomobject]@{ Value = $value; Bytes = $bytes; Text = $text }
}

function Get-Sha256Lower {
    param([Parameter(Mandatory)][byte[]] $Bytes)

    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Copy-JsonValue {
    param([Parameter(Mandatory)][object] $Value)

    return ($Value | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json -Depth 100 -DateKind String
}

$expectedRuleIds = @('FRE-01', 'FRE-02', 'FRE-03', 'FRE-04', 'FRE-05', 'FRE-06', 'FRE-07')
$expectedPresence = @('ABSENT', 'NULL', 'VALUE')
$expectedFreshness = @('cte_created_at', 'cte_issued_at', 'criado_em', 'servico_em')
$expectedTerminalStatuses = @('finished', 'done', 'canceled', 'cancelled')
$expectedSidecarFields = @(
    '/freight/edges/node/id',
    '/freight/edges/node/accountingCreditId',
    '/freight/edges/node/accountingCreditInstallmentId',
    '/freight/edges/node/referenceNumber',
    '/freight/edges/node/cte/key',
    '/freight/edges/node/total',
    '/freight/edges/node/corporationSequenceNumber',
    '/freight/edges/node/pickItemId',
    '/freight/pageInfo/hasNextPage',
    '/freight/pageInfo/endCursor'
)
$expectedLimitations = @(
    'NO_MAPPER',
    'NO_MIGRATION',
    'NO_SQL_OR_DATABASE',
    'NO_RUNTIME',
    'NO_RELATION_MATERIALIZATION',
    'NO_CHARACTERIZATION_OR_BOOTSTRAP',
    'NO_PUBLICATION_OR_CUTOVER',
    'NO_NETWORK_OR_SOURCE_IO',
    'NO_COMPLETENESS_PROOF',
    'NO_CURRENCY_OR_FINANCIAL_RULE_INFERENCE'
)
$expectedCases = [ordered]@{
    'FRE-01-POS' = @('FRE-01', 'ACCEPT_DECISION', 'SCOPED_IDENTITY_ACCEPTED')
    'FRE-01-NEG' = @('FRE-01', 'BLOCK', 'BUSINESS_ALIAS_IS_NOT_SOURCE_KEY')
    'FRE-02-POS' = @('FRE-02', 'ACCEPT_DECISION', 'FIRST_PRESENT_FRESHNESS_SELECTED')
    'FRE-02-NEG' = @('FRE-02', 'QUARANTINE', 'EQUAL_FRESHNESS_DIVERGENCE')
    'FRE-03-POS' = @('FRE-03', 'ACCEPT_DECISION', 'OMISSION_PRESERVES_KNOWN_VALUE')
    'FRE-03-NEG' = @('FRE-03', 'QUARANTINE', 'AMBIGUOUS_PERFORMANCE_DATE')
    'FRE-04-POS' = @('FRE-04', 'ACCEPT_DECISION', 'RELATION_CANDIDATE_PRESERVED_ONLY')
    'FRE-04-NEG' = @('FRE-04', 'BLOCK', 'RELATION_INFERENCE_FORBIDDEN')
    'FRE-05-POS' = @('FRE-05', 'NO_OP', 'INCREMENTAL_ABSENCE_NO_STATE_CHANGE')
    'FRE-05-NEG' = @('FRE-05', 'BLOCK', 'PRUNE_REQUIRES_SEPARATE_GATE')
    'FRE-06-POS' = @('FRE-06', 'ACCEPT_DECISION', 'FINANCIAL_RAW_TYPED_PRESENCE_ONLY')
    'FRE-06-NEG' = @('FRE-06', 'BLOCK', 'CURRENCY_OR_RULE_NOT_GOVERNED')
    'FRE-07-POS' = @('FRE-07', 'ACCEPT_DECISION', 'UNAMBIGUOUS_DATE_WITH_PROVEN_FORMAT')
    'FRE-07-NEG' = @('FRE-07', 'QUARANTINE', 'AMBIGUOUS_DAY_MONTH_DATE')
}

function Test-Decision {
    param([Parameter(Mandatory)][object] $Decision)

    Assert-ExactProperties -Value $Decision -Expected @(
        'catalogVersion', 'decision', 'decisionStatus', 'scope', 'task', 'route', 'block',
        'sourceContract', 'identityDecision', 'execution', 'rules', 'identity', 'presence',
        'freshness', 'performance', 'terminality', 'sidecars', 'financial', 'incremental',
        'absence', 'limitations', 'fixture'
    ) -Reason 'DECISION_TOP_LEVEL_SCHEMA_INVALID'
    Assert-ExactString $Decision.catalogVersion '2026-09-06.v2-011a.1' 'DECISION_VERSION_INVALID'
    Assert-ExactString $Decision.decision 'FRETES_6389_LOCAL_DOMAIN_DECISION' 'DECISION_ID_INVALID'
    Assert-ExactString $Decision.decisionStatus 'COMPLETE_LOCAL_DECISION_ONLY' 'DECISION_STATUS_INVALID'
    Assert-ExactString $Decision.scope 'DECISION_ONLY_NO_IMPLEMENTATION_NO_SOURCE_IO_NO_RELATION' 'DECISION_SCOPE_INVALID'
    Assert-ExactString $Decision.task 'V2-011a' 'DECISION_TASK_INVALID'
    Assert-ExactString $Decision.route 'V08' 'DECISION_ROUTE_INVALID'
    Assert-Condition -Condition (($Decision.block -is [int] -or $Decision.block -is [long]) -and $Decision.block -eq 44) -Reason 'DECISION_BLOCK_INVALID'

    Assert-ExactProperties $Decision.sourceContract @('id', 'version', 'releaseFingerprint', 'reference') 'SOURCE_CONTRACT_SCHEMA_INVALID'
    Assert-ExactString $Decision.sourceContract.id 'dataexport-6389' 'SOURCE_CONTRACT_ID_INVALID'
    Assert-ExactString $Decision.sourceContract.version '2026-08-31.v2-025a.1' 'SOURCE_CONTRACT_VERSION_INVALID'
    Assert-ExactString $Decision.sourceContract.releaseFingerprint '23aef4e4e03488d291d3990bdba813800e3ebb8c8be18e766a788daf9741ec15' 'SOURCE_CONTRACT_FINGERPRINT_INVALID'
    Assert-ExactString $Decision.sourceContract.reference 'docs/catalogos/contratos-primeira-onda/manifesto.json' 'SOURCE_CONTRACT_REFERENCE_INVALID'

    Assert-ExactProperties $Decision.identityDecision @('task', 'catalogVersion', 'identityFingerprint', 'reference') 'IDENTITY_BINDING_SCHEMA_INVALID'
    Assert-ExactString $Decision.identityDecision.task 'V2-009a' 'IDENTITY_TASK_INVALID'
    Assert-ExactString $Decision.identityDecision.catalogVersion '2026-09-01.v2-009a.2' 'IDENTITY_VERSION_INVALID'
    Assert-ExactString $Decision.identityDecision.identityFingerprint '4e35b90cd4a58e139210d9adf9ac050acba9a46e62585996b650c6252cf44723' 'IDENTITY_FINGERPRINT_INVALID'
    Assert-ExactString $Decision.identityDecision.reference 'docs/catalogos/identidade-primeira-onda/manifesto.json' 'IDENTITY_REFERENCE_INVALID'

    Assert-ExactProperties $Decision.execution @('parentTask', 'parentStatus', 'implementationOwner', 'baseShadowGate', 'relationalParityGates', 'crosswalkOwner', 'allowedArtifacts', 'forbiddenArtifacts') 'EXECUTION_SCHEMA_INVALID'
    Assert-ExactString $Decision.execution.parentTask 'V2-011' 'EXECUTION_PARENT_INVALID'
    Assert-ExactString $Decision.execution.parentStatus 'OPEN_IMPLEMENTATION_PENDING' 'EXECUTION_PARENT_STATUS_INVALID'
    Assert-ExactString $Decision.execution.implementationOwner 'V09' 'EXECUTION_OWNER_INVALID'
    Assert-ExactString $Decision.execution.baseShadowGate 'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW' 'BASE_SHADOW_GATE_INVALID'
    Assert-ExactString $Decision.execution.relationalParityGates 'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES' 'RELATIONAL_PARITY_GATES_INVALID'
    Assert-ExactString $Decision.execution.crosswalkOwner 'V2-046b' 'CROSSWALK_OWNER_INVALID'
    Assert-ExactSet @($Decision.execution.allowedArtifacts) @('DOCUMENTATION', 'LOCAL_VALIDATOR', 'SYNTHETIC_DECISION_CASES') 'ALLOWED_ARTIFACTS_INVALID'
    Assert-ExactSet @($Decision.execution.forbiddenArtifacts) @('JAVA_PRODUCTION_CODE', 'MAPPER', 'MIGRATION', 'SQL', 'RUNTIME', 'RELATION', 'CHARACTERIZATION', 'BOOTSTRAP', 'PUBLICATION', 'CUTOVER') 'FORBIDDEN_ARTIFACTS_INVALID'

    Assert-JsonArray $Decision.rules 'RULES_NOT_ARRAY'
    Assert-Condition -Condition (@($Decision.rules).Count -eq 7) -Reason 'RULE_COUNT_INVALID'
    Assert-ExactSet @($Decision.rules | ForEach-Object { $_.id }) $expectedRuleIds 'RULE_SET_INVALID'
    foreach ($rule in $Decision.rules) {
        Assert-ExactProperties $rule @('id', 'title', 'decision', 'evidence', 'implementationGate') 'RULE_SCHEMA_INVALID'
        Assert-Condition -Condition ($rule.title -is [string] -and $rule.title.Length -ge 3) -Reason 'RULE_TITLE_INVALID'
        Assert-Condition -Condition ($rule.decision -is [string] -and $rule.decision.Length -ge 10) -Reason 'RULE_DECISION_INVALID'
        Assert-Condition -Condition ($rule.evidence -is [string] -and $rule.evidence.Length -ge 10) -Reason 'RULE_EVIDENCE_INVALID'
        Assert-ExactString $rule.implementationGate 'V09' 'RULE_IMPLEMENTATION_GATE_INVALID'
    }

    Assert-ExactProperties $Decision.identity @('entity', 'tuple', 'sourceKey', 'businessAlias', 'canonicalId', 'expandedRows') 'IDENTITY_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.entity 'fretes' 'IDENTITY_ENTITY_INVALID'
    Assert-ExactSet @($Decision.identity.tuple) @('source_instance', 'tenant_scope', 'entity', 'source_key') 'IDENTITY_TUPLE_INVALID'
    Assert-ExactProperties $Decision.identity.sourceKey @('path', 'wireType', 'role') 'SOURCE_KEY_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.path '/id' 'SOURCE_KEY_PATH_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.wireType 'INTEGER' 'SOURCE_KEY_TYPE_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.role 'TYPE_TAGGED_SCOPED_SOURCE_KEY' 'SOURCE_KEY_ROLE_INVALID'
    Assert-ExactProperties $Decision.identity.businessAlias @('path', 'role', 'cardinality') 'BUSINESS_ALIAS_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.businessAlias.path '/corporation_sequence_number' 'BUSINESS_ALIAS_PATH_INVALID'
    Assert-ExactString $Decision.identity.businessAlias.role 'VERSIONED_NON_TECHNICAL_ALIAS_NEVER_IDENTITY' 'BUSINESS_ALIAS_ROLE_INVALID'
    Assert-ExactString $Decision.identity.businessAlias.cardinality 'ZERO_TO_MANY_LOOKUP_AMBIGUITY_BLOCKS' 'BUSINESS_ALIAS_CARDINALITY_INVALID'
    Assert-ExactString $Decision.identity.canonicalId 'SQL_SURROGATE_BIGINT_IDENTITY' 'CANONICAL_ID_INVALID'
    Assert-ExactString $Decision.identity.expandedRows 'DEDUPLICATE_ROOT_BY_SCOPED_ID_SET_BASED_NO_IMPLICIT_CHILD' 'EXPANDED_ROWS_INVALID'

    Assert-ExactProperties $Decision.presence @('vocabulary', 'absent', 'null', 'value', 'protectedGroups') 'PRESENCE_SCHEMA_INVALID'
    Assert-ExactSet @($Decision.presence.vocabulary) $expectedPresence 'PRESENCE_VOCABULARY_INVALID'
    Assert-ExactString $Decision.presence.absent 'PRESERVE_KNOWN_VALUE_AND_RECORD_ABSENT' 'PRESENCE_ABSENT_INVALID'
    Assert-ExactString $Decision.presence.null 'APPLY_EXPLICIT_NULL_ONLY_ON_NEWER_ACCEPTED_OBSERVATION' 'PRESENCE_NULL_INVALID'
    Assert-ExactString $Decision.presence.value 'APPLY_TYPED_VALUE_WITH_RAW_AND_PROVENANCE' 'PRESENCE_VALUE_INVALID'
    Assert-ExactSet @($Decision.presence.protectedGroups) @('CTE', 'FINALIZATIONS', 'PERFORMANCE', 'FINANCIAL', 'RELATION_CANDIDATES') 'PRESENCE_GROUPS_INVALID'

    Assert-ExactProperties $Decision.freshness @('dedupeOrder', 'promotionOrder', 'selection', 'presentInvalid', 'updatedAt', 'olderObservation', 'equalCanonical', 'equalDivergent', 'forbiddenTieBreakers') 'FRESHNESS_SCHEMA_INVALID'
    Assert-Condition -Condition ((@($Decision.freshness.dedupeOrder) -join '|') -ceq ($expectedFreshness -join '|')) -Reason 'FRESHNESS_DEDUPE_ORDER_INVALID'
    Assert-Condition -Condition ((@($Decision.freshness.promotionOrder) -join '|') -ceq ($expectedFreshness -join '|')) -Reason 'FRESHNESS_PROMOTION_ORDER_INVALID'
    Assert-ExactString $Decision.freshness.selection 'FIRST_PRESENT_TYPED_VALID_VALUE' 'FRESHNESS_SELECTION_INVALID'
    Assert-ExactString $Decision.freshness.presentInvalid 'PRESERVE_RAW_AND_QUARANTINE_NO_FALLBACK' 'FRESHNESS_INVALID_VALUE_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.updatedAt 'UNVERIFIED_NEVER_FRESHNESS_OR_WATERMARK' 'UPDATED_AT_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.olderObservation 'AUDIT_AND_NO_OP_NO_REGRESSION' 'OLDER_OBSERVATION_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.equalCanonical 'REPLAY_NO_OP' 'EQUAL_CANONICAL_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.equalDivergent 'QUARANTINE_STABLE_REASON' 'EQUAL_DIVERGENT_POLICY_INVALID'
    Assert-ExactSet @($Decision.freshness.forbiddenTieBreakers) @('ARRIVAL', 'PAGE', 'RECORD_ORDER', 'HASH') 'FRESHNESS_TIE_BREAKERS_INVALID'

    Assert-ExactProperties $Decision.performance @('officialPath', 'fallbackPath', 'precedence', 'provenance', 'ambiguousDate', 'unambiguousFormats', 'hostTimezone') 'PERFORMANCE_SCHEMA_INVALID'
    Assert-ExactString $Decision.performance.officialPath '/fit_dpn_performance_finished_at' 'PERFORMANCE_OFFICIAL_PATH_INVALID'
    Assert-ExactString $Decision.performance.fallbackPath '/finished_at' 'PERFORMANCE_FALLBACK_PATH_INVALID'
    Assert-ExactString $Decision.performance.precedence 'OFFICIAL_6389_THEN_FINISHED_AT' 'PERFORMANCE_PRECEDENCE_INVALID'
    Assert-ExactString $Decision.performance.provenance 'RAW_TYPED_PARSE_STATE_AND_SELECTED_ORIGIN_REQUIRED' 'PERFORMANCE_PROVENANCE_INVALID'
    Assert-ExactString $Decision.performance.ambiguousDate 'DAY_AND_MONTH_LE_12_QUARANTINE_NO_HEURISTIC' 'PERFORMANCE_AMBIGUOUS_DATE_INVALID'
    Assert-ExactSet @($Decision.performance.unambiguousFormats) @('MM/dd/yyyy_WHEN_DAY_GT_12', 'dd/MM/yyyy_WHEN_DAY_GT_12') 'PERFORMANCE_FORMATS_INVALID'
    Assert-ExactString $Decision.performance.hostTimezone 'FORBIDDEN_AMERICA_SAO_PAULO_EXPLICIT' 'PERFORMANCE_TIMEZONE_INVALID'

    Assert-ExactProperties $Decision.terminality @('terminalRawStatuses', 'mapping', 'persistedTerminal', 'retrogradeTerminal', 'unknownStatus', 'partialPayload') 'TERMINALITY_SCHEMA_INVALID'
    Assert-ExactSet @($Decision.terminality.terminalRawStatuses) $expectedTerminalStatuses 'TERMINAL_STATUS_SET_INVALID'
    Assert-ExactProperties $Decision.terminality.mapping @('finished', 'done', 'canceled', 'cancelled') 'TERMINAL_MAPPING_SCHEMA_INVALID'
    Assert-ExactString $Decision.terminality.mapping.finished 'finalizado' 'TERMINAL_MAPPING_INVALID'
    Assert-ExactString $Decision.terminality.mapping.done 'finalizado' 'TERMINAL_MAPPING_INVALID'
    Assert-ExactString $Decision.terminality.mapping.canceled 'cancelada' 'TERMINAL_MAPPING_INVALID'
    Assert-ExactString $Decision.terminality.mapping.cancelled 'cancelada' 'TERMINAL_MAPPING_INVALID'
    Assert-ExactString $Decision.terminality.persistedTerminal 'NEVER_REGRESSES_TO_OPEN' 'TERMINAL_REGRESSION_POLICY_INVALID'
    Assert-ExactString $Decision.terminality.retrogradeTerminal 'TERMINAL_WINS_OPEN_WITH_AUDIT' 'RETROGRADE_TERMINAL_POLICY_INVALID'
    Assert-ExactString $Decision.terminality.unknownStatus 'PRESERVE_RAW_NO_INFERRED_TERMINALITY' 'UNKNOWN_STATUS_POLICY_INVALID'
    Assert-ExactString $Decision.terminality.partialPayload 'PRESENCE_RULES_APPLY_NO_SILENT_ERASURE' 'PARTIAL_PAYLOAD_POLICY_INVALID'

    Assert-ExactProperties $Decision.sidecars @('source', 'catalogReference', 'fields', 'storage', 'memoryBound', 'failureIsolation', 'relationCandidate', 'joinPolicy', 'publication') 'SIDECAR_SCHEMA_INVALID'
    Assert-ExactString $Decision.sidecars.source 'GRAPHQL_TRANSITIONAL_ONLY' 'SIDECAR_SOURCE_INVALID'
    Assert-ExactString $Decision.sidecars.catalogReference 'docs/catalogos/graphql-transitorio.csv' 'SIDECAR_REFERENCE_INVALID'
    Assert-ExactSet @($Decision.sidecars.fields) $expectedSidecarFields 'SIDECAR_FIELD_SET_INVALID'
    Assert-ExactString $Decision.sidecars.storage 'INDEPENDENT_BOUNDED_STAGING_WITH_FIELD_PROVENANCE' 'SIDECAR_STORAGE_INVALID'
    Assert-ExactString $Decision.sidecars.memoryBound 'ONE_PAGE_PLUS_BOUNDED_BATCH_NEVER_EXECUTION_WIDE_MAP' 'SIDECAR_MEMORY_BOUND_INVALID'
    Assert-ExactString $Decision.sidecars.failureIsolation 'FIELD_OR_SIDECAR_BLOCKED_WITHOUT_OVERWRITING_ROOT' 'SIDECAR_FAILURE_POLICY_INVALID'
    Assert-ExactString $Decision.sidecars.relationCandidate '/freight/edges/node/pickItemId' 'SIDECAR_RELATION_CANDIDATE_INVALID'
    Assert-ExactString $Decision.sidecars.joinPolicy 'PRESERVE_ONLY_V2_046B_OWNS_SET_BASED_CROSSWALK' 'SIDECAR_JOIN_POLICY_INVALID'
    Assert-ExactString $Decision.sidecars.publication 'FORBIDDEN_UNTIL_PARITY_AND_CONSUMER_CONTRACT' 'SIDECAR_PUBLICATION_INVALID'

    Assert-ExactProperties $Decision.financial @('fields', 'storage', 'currency', 'unit', 'arithmetic', 'unknownOrDivergent', 'publication') 'FINANCIAL_SCHEMA_INVALID'
    Assert-ExactSet @($Decision.financial.fields) @('accountingCreditId', 'accountingCreditInstallmentId', 'referenceNumber', 'cte.key', 'total') 'FINANCIAL_FIELDS_INVALID'
    Assert-ExactString $Decision.financial.storage 'RAW_TYPED_PRESENCE_AND_PROVENANCE_ONLY' 'FINANCIAL_STORAGE_INVALID'
    Assert-ExactString $Decision.financial.currency 'UNRESOLVED_NO_INFERENCE' 'FINANCIAL_CURRENCY_INVALID'
    Assert-ExactString $Decision.financial.unit 'UNRESOLVED_NO_INFERENCE' 'FINANCIAL_UNIT_INVALID'
    Assert-ExactString $Decision.financial.arithmetic 'FORBIDDEN_WITHOUT_GOVERNED_CURRENCY_UNIT_PRECISION_ROUNDING' 'FINANCIAL_ARITHMETIC_INVALID'
    Assert-ExactString $Decision.financial.unknownOrDivergent 'QUARANTINE_OR_BLOCK_AFFECTED_OUTPUT' 'FINANCIAL_UNKNOWN_POLICY_INVALID'
    Assert-ExactString $Decision.financial.publication 'BLOCKED_PENDING_PARITY_AND_NOMINAL_OWNER' 'FINANCIAL_PUBLICATION_INVALID'

    Assert-ExactProperties $Decision.incremental @('partition', 'timezone', 'internalWindow', 'sourceBoundary', 'overlap', 'supplementaryLateData', 'watermark', 'replay') 'INCREMENTAL_SCHEMA_INVALID'
    Assert-ExactString $Decision.incremental.partition 'freights.service_at' 'INCREMENTAL_PARTITION_INVALID'
    Assert-ExactString $Decision.incremental.timezone 'America/Sao_Paulo' 'INCREMENTAL_TIMEZONE_INVALID'
    Assert-ExactString $Decision.incremental.internalWindow '[start,endExclusive)' 'INCREMENTAL_WINDOW_INVALID'
    Assert-ExactString $Decision.incremental.sourceBoundary 'INCLUSIVE_TRANSLATION_UNPROVEN_BLOCKS_OPERATIONAL_EXECUTION' 'INCREMENTAL_SOURCE_BOUNDARY_INVALID'
    Assert-ExactString $Decision.incremental.overlap 'VERSIONED_BOUNDED_REQUIRED' 'INCREMENTAL_OVERLAP_INVALID'
    Assert-ExactString $Decision.incremental.supplementaryLateData 'scopes.by_updated_at' 'INCREMENTAL_LATE_DATA_INVALID'
    Assert-ExactString $Decision.incremental.watermark 'ONLY_CONTIGUOUS_PUBLISHED_SERVICE_AT_PARTITIONS_NEVER_UPDATED_AT' 'INCREMENTAL_WATERMARK_INVALID'
    Assert-ExactString $Decision.incremental.replay 'SAME_SCOPED_ROOT_AND_CANONICAL_CONTENT_IS_NO_OP' 'INCREMENTAL_REPLAY_INVALID'

    Assert-ExactProperties $Decision.absence @('incremental', 'prune', 'enablementGate', 'requirements', 'failure') 'ABSENCE_SCHEMA_INVALID'
    Assert-ExactString $Decision.absence.incremental 'NEVER_PROVES_ABSENCE' 'ABSENCE_INCREMENTAL_INVALID'
    Assert-ExactString $Decision.absence.prune 'DISABLED' 'ABSENCE_PRUNE_INVALID'
    Assert-ExactString $Decision.absence.enablementGate 'V2-013_AFTER_Q_FRE_03_AND_COMPLETE_SNAPSHOT_PROOF' 'ABSENCE_GATE_INVALID'
    Assert-ExactSet @($Decision.absence.requirements) @('INDEPENDENT_COMPLETE_SNAPSHOT', 'TWO_ABSENCES', 'QUARANTINE', 'VOLUME_GUARDRAILS', 'HISTORY_GUARDRAILS', 'OWNER_ACCEPTANCE') 'ABSENCE_REQUIREMENTS_INVALID'
    Assert-ExactString $Decision.absence.failure 'NO_ACTIVE_STATE_CHANGE' 'ABSENCE_FAILURE_POLICY_INVALID'

    Assert-ExactSet @($Decision.limitations) $expectedLimitations 'LIMITATIONS_INVALID'
    Assert-ExactProperties $Decision.fixture @('path', 'sha256', 'caseCount') 'FIXTURE_BINDING_SCHEMA_INVALID'
    Assert-ExactString $Decision.fixture.path 'fixtures/casos-v01.synthetic.json' 'FIXTURE_PATH_INVALID'
    Assert-Condition -Condition ($Decision.fixture.sha256 -is [string] -and $Decision.fixture.sha256 -cmatch '^[0-9a-f]{64}$') -Reason 'FIXTURE_HASH_FORMAT_INVALID'
    Assert-Condition -Condition (($Decision.fixture.caseCount -is [int] -or $Decision.fixture.caseCount -is [long]) -and $Decision.fixture.caseCount -eq 14) -Reason 'FIXTURE_CASE_COUNT_INVALID'
}

function Test-Fixture {
    param([Parameter(Mandatory)][object] $Fixture)

    Assert-ExactProperties $Fixture @('schemaVersion', 'evidenceKind', 'cases') 'FIXTURE_TOP_LEVEL_SCHEMA_INVALID'
    Assert-ExactString $Fixture.schemaVersion 'v2-011a-fretes-decision-cases-v1' 'FIXTURE_VERSION_INVALID'
    Assert-ExactString $Fixture.evidenceKind 'SYNTHETIC_DECISION_CASES_ONLY' 'FIXTURE_EVIDENCE_KIND_INVALID'
    Assert-JsonArray $Fixture.cases 'FIXTURE_CASES_NOT_ARRAY'
    Assert-Condition -Condition (@($Fixture.cases).Count -eq $expectedCases.Count) -Reason 'FIXTURE_CASE_COUNT_INVALID'
    Assert-ExactSet @($Fixture.cases | ForEach-Object { $_.caseId }) @($expectedCases.Keys) 'FIXTURE_CASE_SET_INVALID'
    foreach ($case in $Fixture.cases) {
        Assert-ExactProperties $case @('caseId', 'rule', 'scenario', 'symbolicFacts', 'expected') 'FIXTURE_CASE_SCHEMA_INVALID'
        Assert-Condition -Condition ($case.caseId -is [string] -and $expectedCases.Contains($case.caseId)) -Reason 'FIXTURE_CASE_ID_INVALID'
        $contract = $expectedCases[$case.caseId]
        Assert-ExactString $case.rule $contract[0] 'FIXTURE_CASE_RULE_INVALID'
        Assert-Condition -Condition ($case.scenario -is [string] -and $case.scenario.Length -ge 8) -Reason 'FIXTURE_CASE_SCENARIO_INVALID'
        Assert-JsonArray $case.symbolicFacts 'FIXTURE_SYMBOLIC_FACTS_NOT_ARRAY'
        Assert-Condition -Condition (@($case.symbolicFacts).Count -ge 1) -Reason 'FIXTURE_SYMBOLIC_FACTS_EMPTY'
        foreach ($fact in $case.symbolicFacts) {
            Assert-Condition -Condition ($fact -is [string] -and $fact -cmatch '^[A-Z0-9_./=<>-]{3,100}$') -Reason 'FIXTURE_SYMBOLIC_FACT_INVALID'
        }
        Assert-ExactProperties $case.expected @('disposition', 'reason', 'roadmapEffect') 'FIXTURE_EXPECTED_SCHEMA_INVALID'
        Assert-ExactString $case.expected.disposition $contract[1] 'FIXTURE_DISPOSITION_INVALID'
        Assert-ExactString $case.expected.reason $contract[2] 'FIXTURE_REASON_INVALID'
        Assert-ExactString $case.expected.roadmapEffect 'DECISION_ONLY' 'FIXTURE_ROADMAP_EFFECT_INVALID'
    }
}

try {
    if (-not (Test-Path -LiteralPath $decisionPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
        throw 'FRETES_DECISION_CATALOG_MISSING'
    }
    $decisionDocument = Read-StrictJson $decisionPath 128KB 'DECISION_CATALOG'
    $fixtureDocument = Read-StrictJson $fixturePath 128KB 'DECISION_FIXTURE'
    $contractDocument = Read-StrictJson $contractPath 512KB 'SOURCE_CONTRACT'
    $identityDocument = Read-StrictJson $identityPath 256KB 'IDENTITY_CATALOG'
    Test-Decision $decisionDocument.Value
    Test-Fixture $fixtureDocument.Value
    Assert-ExactString $decisionDocument.Value.fixture.sha256 (Get-Sha256Lower $fixtureDocument.Bytes) 'FIXTURE_HASH_MISMATCH'

    $contract = @($contractDocument.Value.contracts | Where-Object { $_.contractId -ceq 'dataexport-6389' })
    Assert-Condition -Condition ($contract.Count -eq 1) -Reason 'SOURCE_CONTRACT_BINDING_MISSING'
    Assert-ExactString $contract[0].contractVersion $decisionDocument.Value.sourceContract.version 'SOURCE_CONTRACT_BINDING_VERSION_MISMATCH'
    Assert-ExactString $contract[0].fingerprints.release $decisionDocument.Value.sourceContract.releaseFingerprint 'SOURCE_CONTRACT_BINDING_FINGERPRINT_MISMATCH'
    $identity = @($identityDocument.Value.entities | Where-Object { $_.entity -ceq 'fretes' })
    Assert-Condition -Condition ($identity.Count -eq 1) -Reason 'IDENTITY_BINDING_MISSING'
    Assert-ExactString $identity[0].identityFingerprint $decisionDocument.Value.identityDecision.identityFingerprint 'IDENTITY_BINDING_FINGERPRINT_MISMATCH'

    $graphqlRows = @(Import-Csv -LiteralPath $graphqlCatalogPath | Where-Object { $_.operation -ceq 'FREIGHTS_TRANSITIONAL_SIDECAR' })
    Assert-Condition -Condition ($graphqlRows.Count -eq 10) -Reason 'SIDECAR_CATALOG_COUNT_INVALID'
    Assert-ExactSet @($graphqlRows | ForEach-Object { $_.path }) $expectedSidecarFields 'SIDECAR_CATALOG_BINDING_INVALID'

    $negativeMutations = @(
        [pscustomobject]@{ Reason = 'UPDATED_AT_POLICY_INVALID'; Mutate = { param($value) $value.freshness.updatedAt = 'USE_AS_WATERMARK' } },
        [pscustomobject]@{ Reason = 'FRESHNESS_DEDUPE_ORDER_INVALID'; Mutate = { param($value) $value.freshness.dedupeOrder = @('updated_at') } },
        [pscustomobject]@{ Reason = 'PERFORMANCE_AMBIGUOUS_DATE_INVALID'; Mutate = { param($value) $value.performance.ambiguousDate = 'GUESS_US_FIRST' } },
        [pscustomobject]@{ Reason = 'SIDECAR_MEMORY_BOUND_INVALID'; Mutate = { param($value) $value.sidecars.memoryBound = 'EXECUTION_WIDE_MAP' } },
        [pscustomobject]@{ Reason = 'BASE_SHADOW_GATE_INVALID'; Mutate = { param($value) $value.execution.baseShadowGate = 'V2_046A_REQUIRED_FOR_BASE_SHADOW' } },
        [pscustomobject]@{ Reason = 'SIDECAR_JOIN_POLICY_INVALID'; Mutate = { param($value) $value.sidecars.joinPolicy = 'MATERIALIZE_RELATION_NOW' } },
        [pscustomobject]@{ Reason = 'FINANCIAL_CURRENCY_INVALID'; Mutate = { param($value) $value.financial.currency = 'BRL' } },
        [pscustomobject]@{ Reason = 'ABSENCE_PRUNE_INVALID'; Mutate = { param($value) $value.absence.prune = 'ENABLED' } },
        [pscustomobject]@{ Reason = 'DECISION_TOP_LEVEL_SCHEMA_INVALID'; Mutate = { param($value) $value | Add-Member -NotePropertyName implementation -NotePropertyValue 'FORBIDDEN' } }
    )
    $blocked = 0
    foreach ($mutation in $negativeMutations) {
        $candidate = Copy-JsonValue $decisionDocument.Value
        & $mutation.Mutate $candidate
        try {
            Test-Decision $candidate
        } catch {
            Assert-Condition -Condition ($_.Exception.Message -ceq $mutation.Reason) -Reason 'NEGATIVE_REASON_MISMATCH'
            $blocked++
        }
    }
    Assert-Condition -Condition ($blocked -eq $negativeMutations.Count) -Reason 'NEGATIVE_CASE_ACCEPTED'
    Write-Output ("PASS: decisão local V2-011a/Fretes 6389 validada; rules={0} synthetic_cases={1} negative_mutations={2} result=COMPLETE_LOCAL_DECISION_ONLY implementation=OPEN base_shadow=UNBLOCKED relation=OPEN." -f $expectedRuleIds.Count, $expectedCases.Count, $blocked)
} catch {
    $reason = $_.Exception.Message
    if ($reason -isnot [string] -or $reason -cnotmatch '^[A-Z][A-Z0-9_]{2,100}$') {
        $reason = 'INTERNAL_ERROR_REDACTED'
    }
    Write-Output ("FRETES_V2_011A_DECISION status=FAIL reason={0}" -f $reason)
    exit 1
}

exit 0

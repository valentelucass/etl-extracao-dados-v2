#Requires -Version 7.0

# Gate offline e fail-closed da decisão local V2-028a.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$catalogRoot = Join-Path $repositoryRoot 'docs\catalogos\localizacao-cargas-v2-028'
$decisionPath = Join-Path $catalogRoot 'decisao-v01.json'
$fixturePath = Join-Path $catalogRoot 'fixtures\casos-v01.synthetic.json'
$readmePath = Join-Path $catalogRoot 'README.md'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0028-localizacao-cargas-8656-dominio-presenca-frescor-status-e-schema.md'
$runbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-028a-decisao-localizacao-cargas-sol.md'
$contractPath = Join-Path $repositoryRoot 'docs\catalogos\contratos-esl-8656\manifesto.json'
$identityPath = Join-Path $repositoryRoot 'docs\catalogos\identidade-localizacao-cargas\manifesto.json'
$portabilityRulesPath = Join-Path $repositoryRoot 'docs\catalogos\portabilidade\regras-negocio.csv'

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

function Read-StrictText {
    param(
        [Parameter(Mandatory)][string] $LiteralPath,
        [Parameter(Mandatory)][long] $MaximumBytes,
        [Parameter(Mandatory)][string] $ReasonPrefix
    )

    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Leaf)) {
        throw "${ReasonPrefix}_MISSING"
    }
    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    Assert-Condition -Condition ($item.Length -le $MaximumBytes) -Reason "${ReasonPrefix}_TOO_LARGE"
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
    try {
        return $utf8.GetString($bytes)
    } catch [System.Text.DecoderFallbackException] {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
}

function Get-Sha256Lower {
    param([Parameter(Mandatory)][byte[]] $Bytes)

    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Copy-JsonValue {
    param([Parameter(Mandatory)][object] $Value)

    return ($Value | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json -Depth 100 -DateKind String
}

$expectedRuleIds = @('LOC-01', 'LOC-02', 'LOC-03', 'LOC-04', 'LOC-05', 'LOC-06', 'LOC-07')
$expectedPresence = @('ABSENT', 'NULL', 'VALUE')
$expectedTerminalStatuses = @('finished', 'delivered', 'canceled', 'cancelled')
$expectedFreshnessTieBreakers = @('DATA_EXTRACAO', 'EXTRACTION_TIMESTAMP', 'HASH', 'PAGE', 'ARRIVAL', 'RECORD_ORDER')
$expectedLimitations = @(
    'NO_JAVA_PRODUCTION_CODE',
    'NO_MAPPER_OR_RUNTIME',
    'NO_MIGRATION_OR_SQL_OR_DATABASE',
    'NO_SOURCE_OR_NETWORK_IO',
    'NO_RELATION_FK_JOIN_CROSSWALK_OR_TOP_1',
    'NO_CHARACTERIZATION_OR_BOOTSTRAP',
    'NO_PUBLICATION_SWEEP_OR_CUTOVER',
    'NO_COMPLETENESS_PROOF',
    'NO_CURRENCY_UNIT_OR_ROUNDING_INFERENCE',
    'V2_046A_AND_V2_046B_REMAIN_OPEN'
)
$expectedLogicalSchema = @(
    'SCOPED_SOURCE_IDENTITY_AND_INTERNAL_SURROGATE',
    'RAW_TYPED_PARSE_PATH_PRESENCE_AND_PROVENANCE',
    'SERVICE_AT_FRESHNESS_REPLAY_AND_CONFLICT',
    'RAW_NORMALIZED_STATUS_AND_EXPLICIT_TERMINALITY',
    'STRICT_NUMERIC_PARSE_AND_QUARANTINE',
    'FREIGHT_ASSOCIATION_CANDIDATE_ONLY',
    'ABSENCE_EVIDENCE_WITH_SWEEP_DISABLED'
)
$expectedForbiddenFallbackPaths = @(
    '/fit_crn_psn_nickname',
    '/fit_dyn_drt_nickname',
    '/fit_fln_cln_nickname',
    '/fit_o_n_drt_nickname'
)
$expectedVolumeForbidden = @('FK', 'JOIN', 'FINAL_CROSSWALK', 'TOP_1', 'FUZZY_NAME_MATCH', 'PUBLICATION')
$expectedCases = [ordered]@{
    'LOC-01-SCOPED-KEY' = @('LOC-01', 'ACCEPT_DECISION', 'SCOPED_INTEGER_IDENTITY_ACCEPTED')
    'LOC-01-SEQUENCE-FALLBACK' = @('LOC-01', 'BLOCK', 'SEQUENCE_NUMBER_NOT_IDENTITY_OR_ALIAS')
    'LOC-01-SCOPE-SENTINEL' = @('LOC-01', 'QUARANTINE', 'EXPLICIT_SCOPE_REQUIRED')
    'LOC-02-ABSENT' = @('LOC-02', 'NO_OP', 'ABSENT_PRESERVES_KNOWN_VALUE')
    'LOC-02-NULL' = @('LOC-02', 'ACCEPT_DECISION', 'EXPLICIT_NULL_RECORDED')
    'LOC-02-INVALID' = @('LOC-02', 'QUARANTINE', 'INVALID_TYPED_VALUE_PRESERVED')
    'LOC-03-LOCAL-ZERO' = @('LOC-03', 'ACCEPT_DECISION', 'LOCAL_ZERO_IS_REAL_VALUE')
    'LOC-03-FREIGHT-FALLBACK' = @('LOC-03', 'ACCEPT_DECISION', 'FREIGHT_VOLUME_USED_WHEN_LOCAL_NULL')
    'LOC-03-BOTH-NULL' = @('LOC-03', 'ACCEPT_DECISION', 'ZERO_DEFAULT_ONLY_AFTER_BOTH_NULL')
    'LOC-03-AMBIGUOUS-FREIGHT' = @('LOC-03', 'BLOCK', 'AMBIGUOUS_FREIGHT_CANDIDATE')
    'LOC-04-VALID' = @('LOC-04', 'ACCEPT_DECISION', 'STRICT_NUMERIC_VALUE_ACCEPTED')
    'LOC-04-AMBIGUOUS-LOCALE' = @('LOC-04', 'QUARANTINE', 'AMBIGUOUS_NUMERIC_LOCALE')
    'LOC-04-OVERFLOW' = @('LOC-04', 'QUARANTINE', 'NUMERIC_RANGE_OR_SCALE_OVERFLOW')
    'LOC-05-NEWER' = @('LOC-05', 'ACCEPT_DECISION', 'NEWER_SERVICE_AT_PROMOTES')
    'LOC-05-OLDER' = @('LOC-05', 'NO_OP', 'OLDER_SERVICE_AT_NEVER_REGRESSES')
    'LOC-05-EQUAL-IDENTICAL' = @('LOC-05', 'NO_OP', 'EQUAL_IDENTICAL_IS_REPLAY')
    'LOC-05-EQUAL-DIVERGENT' = @('LOC-05', 'QUARANTINE', 'EQUAL_SERVICE_AT_DIVERGENCE')
    'LOC-05-TWO-NULL-DIVERGENT' = @('LOC-05', 'QUARANTINE', 'NULL_FRESHNESS_DIVERGENCE')
    'LOC-05-EXTRACTION-TIEBREAK' = @('LOC-05', 'BLOCK', 'UNPROVEN_FRESHNESS_TIEBREAKER')
    'LOC-06-BLANK' = @('LOC-06', 'ACCEPT_DECISION', 'BLANK_STATUS_BECOMES_SEM_STATUS')
    'LOC-06-KNOWN-TERMINAL' = @('LOC-06', 'ACCEPT_DECISION', 'KNOWN_TERMINAL_STATUS')
    'LOC-06-UNKNOWN' = @('LOC-06', 'ACCEPT_DECISION', 'UNKNOWN_STATUS_IS_NON_TERMINAL')
    'LOC-07-UNSOURCED' = @('LOC-07', 'NO_OP', 'UNSOURCED_LEGACY_REMAINS_ABSENT')
    'LOC-07-NICKNAME-FALLBACK' = @('LOC-07', 'BLOCK', 'SIMILAR_NICKNAME_FALLBACK_FORBIDDEN')
}

function Test-Decision {
    param([Parameter(Mandatory)][object] $Decision)

    Assert-ExactProperties $Decision @(
        'catalogVersion', 'decision', 'decisionStatus', 'scope', 'task', 'route', 'block',
        'sourceContract', 'identityDecision', 'execution', 'rules', 'identity', 'presence',
        'volume', 'numeric', 'freshness', 'status', 'unsourcedLegacy', 'historicalLimits',
        'logicalSchema', 'documentation', 'limitations', 'fixture'
    ) 'DECISION_TOP_LEVEL_SCHEMA_INVALID'
    Assert-ExactString $Decision.catalogVersion '2026-09-06.v2-028a.1' 'DECISION_VERSION_INVALID'
    Assert-ExactString $Decision.decision 'LOCALIZACAO_CARGAS_8656_LOCAL_DOMAIN_DECISION' 'DECISION_ID_INVALID'
    Assert-ExactString $Decision.decisionStatus 'COMPLETE_LOCAL_DECISION_ONLY' 'DECISION_STATUS_INVALID'
    Assert-ExactString $Decision.scope 'DECISION_ONLY_NO_IMPLEMENTATION_NO_SOURCE_IO_NO_RELATION' 'DECISION_SCOPE_INVALID'
    Assert-ExactString $Decision.task 'V2-028a' 'DECISION_TASK_INVALID'
    Assert-ExactString $Decision.route 'V10A' 'DECISION_ROUTE_INVALID'
    Assert-Condition (($Decision.block -is [int] -or $Decision.block -is [long]) -and $Decision.block -eq 46) 'DECISION_BLOCK_INVALID'

    Assert-ExactProperties $Decision.sourceContract @('id', 'version', 'releaseFingerprint', 'reference') 'SOURCE_CONTRACT_SCHEMA_INVALID'
    Assert-ExactString $Decision.sourceContract.id 'dataexport-8656' 'SOURCE_CONTRACT_ID_INVALID'
    Assert-ExactString $Decision.sourceContract.version '2026-09-04.v2-025b.1' 'SOURCE_CONTRACT_VERSION_INVALID'
    Assert-ExactString $Decision.sourceContract.releaseFingerprint 'da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24' 'SOURCE_CONTRACT_FINGERPRINT_INVALID'
    Assert-ExactString $Decision.sourceContract.reference 'docs/catalogos/contratos-esl-8656/manifesto.json' 'SOURCE_CONTRACT_REFERENCE_INVALID'

    Assert-ExactProperties $Decision.identityDecision @('task', 'catalogVersion', 'identityFingerprint', 'reference') 'IDENTITY_DECISION_SCHEMA_INVALID'
    Assert-ExactString $Decision.identityDecision.task 'V2-009b/8656' 'IDENTITY_DECISION_TASK_INVALID'
    Assert-ExactString $Decision.identityDecision.catalogVersion '2026-09-04.v2-009b.1' 'IDENTITY_DECISION_VERSION_INVALID'
    Assert-ExactString $Decision.identityDecision.identityFingerprint '14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0' 'IDENTITY_DECISION_FINGERPRINT_INVALID'
    Assert-ExactString $Decision.identityDecision.reference 'docs/catalogos/identidade-localizacao-cargas/manifesto.json' 'IDENTITY_DECISION_REFERENCE_INVALID'

    Assert-ExactProperties $Decision.execution @('parentTask', 'parentStatus', 'implementationOwner', 'allowedArtifacts', 'forbiddenArtifacts') 'EXECUTION_SCHEMA_INVALID'
    Assert-ExactString $Decision.execution.parentTask 'V2-028' 'EXECUTION_PARENT_INVALID'
    Assert-ExactString $Decision.execution.parentStatus 'OPEN_IMPLEMENTATION_PENDING' 'EXECUTION_PARENT_STATUS_INVALID'
    Assert-ExactString $Decision.execution.implementationOwner 'V10' 'EXECUTION_OWNER_INVALID'
    Assert-ExactSet @($Decision.execution.allowedArtifacts) @('DOCUMENTATION', 'LOCAL_VALIDATOR', 'SYNTHETIC_DECISION_CASES') 'ALLOWED_ARTIFACTS_INVALID'
    Assert-ExactSet @($Decision.execution.forbiddenArtifacts) @('JAVA_PRODUCTION_CODE', 'MAPPER', 'MIGRATION', 'SQL', 'DATABASE', 'SOURCE_IO', 'RELATION', 'CHARACTERIZATION', 'BOOTSTRAP', 'PUBLICATION', 'SWEEP', 'CUTOVER') 'FORBIDDEN_ARTIFACTS_INVALID'

    Assert-JsonArray $Decision.rules 'RULES_NOT_ARRAY'
    Assert-Condition (@($Decision.rules).Count -eq 7) 'RULE_COUNT_INVALID'
    Assert-ExactSet @($Decision.rules | ForEach-Object { $_.id }) $expectedRuleIds 'RULE_SET_INVALID'
    foreach ($rule in $Decision.rules) {
        Assert-ExactProperties $rule @('id', 'title', 'decision', 'evidence', 'implementationGate') 'RULE_SCHEMA_INVALID'
        Assert-Condition ($rule.title -is [string] -and $rule.title.Length -ge 3) 'RULE_TITLE_INVALID'
        Assert-Condition ($rule.decision -is [string] -and $rule.decision.Length -ge 20) 'RULE_DECISION_INVALID'
        Assert-Condition ($rule.evidence -is [string] -and $rule.evidence.Length -ge 10) 'RULE_EVIDENCE_INVALID'
        Assert-ExactString $rule.implementationGate 'V10' 'RULE_IMPLEMENTATION_GATE_INVALID'
    }

    Assert-ExactProperties $Decision.identity @('entity', 'tuple', 'sourceKey', 'forbiddenAlias', 'canonicalId', 'scope') 'IDENTITY_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.entity 'localizacao_cargas' 'IDENTITY_ENTITY_INVALID'
    Assert-Condition ((@($Decision.identity.tuple) -join '|') -ceq 'source_instance|tenant_scope|entity|source_key') 'IDENTITY_TUPLE_INVALID'
    Assert-ExactProperties $Decision.identity.sourceKey @('path', 'wireType', 'role') 'SOURCE_KEY_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.path '/corporation_sequence_number' 'SOURCE_KEY_PATH_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.wireType 'INTEGER' 'SOURCE_KEY_TYPE_INVALID'
    Assert-ExactString $Decision.identity.sourceKey.role 'TYPE_TAGGED_SCOPED_SOURCE_KEY' 'SOURCE_KEY_ROLE_INVALID'
    Assert-ExactProperties $Decision.identity.forbiddenAlias @('path', 'role') 'SOURCE_KEY_FALLBACK_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.forbiddenAlias.path '/sequence_number' 'SOURCE_KEY_FALLBACK_PATH_INVALID'
    Assert-ExactString $Decision.identity.forbiddenAlias.role 'NEVER_SOURCE_KEY_ALIAS_OR_FALLBACK' 'SOURCE_KEY_FALLBACK_POLICY_INVALID'
    Assert-ExactString $Decision.identity.canonicalId 'INTERNAL_SQL_SURROGATE_BIGINT_IDENTITY' 'CANONICAL_ID_INVALID'
    Assert-ExactProperties $Decision.identity.scope @('policy', 'forbiddenSentinels') 'IDENTITY_SCOPE_SCHEMA_INVALID'
    Assert-ExactString $Decision.identity.scope.policy 'EXPLICIT_SOURCE_INSTANCE_AND_TENANT_REQUIRED' 'IDENTITY_SCOPE_POLICY_INVALID'
    Assert-ExactSet @($Decision.identity.scope.forbiddenSentinels) @('DEFAULT', 'GLOBAL', 'SINGLETON') 'IDENTITY_SCOPE_SENTINELS_INVALID'

    Assert-ExactProperties $Decision.presence @('vocabulary', 'representation', 'absent', 'null', 'value', 'incrementalAbsence', 'sweep') 'PRESENCE_SCHEMA_INVALID'
    Assert-ExactSet @($Decision.presence.vocabulary) $expectedPresence 'PRESENCE_VOCABULARY_INVALID'
    Assert-ExactSet @($Decision.presence.representation) @('RAW', 'TYPED', 'PARSE_STATE', 'PATH', 'PRESENCE', 'PROVENANCE') 'PRESENCE_REPRESENTATION_INVALID'
    Assert-ExactString $Decision.presence.absent 'PRESERVE_KNOWN_VALUE_AND_RECORD_ABSENT' 'PRESENCE_ABSENT_INVALID'
    Assert-ExactString $Decision.presence.null 'APPLY_EXPLICIT_NULL_ONLY_ON_ACCEPTED_NEWER_OBSERVATION' 'PRESENCE_NULL_INVALID'
    Assert-ExactString $Decision.presence.value 'APPLY_TYPED_VALUE_WITH_RAW_PATH_PARSE_STATE_AND_PROVENANCE' 'PRESENCE_VALUE_INVALID'
    Assert-ExactString $Decision.presence.incrementalAbsence 'NO_ACTIVE_STATE_CHANGE' 'PRESENCE_INCREMENTAL_ABSENCE_INVALID'
    Assert-ExactString $Decision.presence.sweep 'DISABLED_NO_COMPLETENESS_PROOF' 'PRESENCE_SWEEP_INVALID'

    Assert-ExactProperties $Decision.volume @('downstreamProjection', 'zeroSemantics', 'localVolumesPath', 'freightCandidate', 'forbidden', 'publication') 'VOLUME_SCHEMA_INVALID'
    Assert-ExactString $Decision.volume.downstreamProjection 'COALESCE(volumes_localizacao, volumes_fretes, 0)' 'VOLUME_PROJECTION_INVALID'
    Assert-ExactString $Decision.volume.zeroSemantics 'ZERO_IS_REAL_LOCAL_VALUE_NEVER_FALLBACK' 'VOLUME_ZERO_POLICY_INVALID'
    Assert-ExactString $Decision.volume.localVolumesPath '/invoices_volumes' 'VOLUME_LOCAL_PATH_INVALID'
    Assert-ExactProperties $Decision.volume.freightCandidate @('localPath', 'freightAliasPath', 'matching', 'ambiguity', 'storage') 'VOLUME_FREIGHT_CANDIDATE_SCHEMA_INVALID'
    Assert-ExactString $Decision.volume.freightCandidate.localPath '/corporation_sequence_number' 'VOLUME_LOCAL_CANDIDATE_PATH_INVALID'
    Assert-ExactString $Decision.volume.freightCandidate.freightAliasPath '/corporation_sequence_number' 'VOLUME_FREIGHT_ALIAS_PATH_INVALID'
    Assert-ExactString $Decision.volume.freightCandidate.matching 'EXACT_SAME_SOURCE_INSTANCE_TENANT_AND_TYPED_ALIAS_CANDIDATE_ONLY' 'VOLUME_FREIGHT_MATCHING_INVALID'
    Assert-ExactString $Decision.volume.freightCandidate.ambiguity 'BLOCK_ZERO_OR_MANY_NEVER_PICK_FIRST' 'VOLUME_FREIGHT_AMBIGUITY_INVALID'
    Assert-ExactString $Decision.volume.freightCandidate.storage 'PRESENCE_AND_PROVENANCE_ONLY_NO_RELATION' 'VOLUME_FREIGHT_STORAGE_INVALID'
    Assert-ExactSet @($Decision.volume.forbidden) $expectedVolumeForbidden 'VOLUME_FORBIDDEN_INVALID'
    Assert-ExactString $Decision.volume.publication 'FORBIDDEN_PENDING_RELATIONAL_PARITY_AND_CONSUMER_GATES' 'VOLUME_PUBLICATION_INVALID'

    Assert-ExactProperties $Decision.numeric @('fields', 'invalid', 'overflow', 'scaleOverflow', 'ambiguousLocale', 'currency', 'unit', 'rounding') 'NUMERIC_SCHEMA_INVALID'
    Assert-JsonArray $Decision.numeric.fields 'NUMERIC_FIELDS_NOT_ARRAY'
    Assert-Condition (@($Decision.numeric.fields).Count -eq 4) 'NUMERIC_FIELD_COUNT_INVALID'
    $numericByPath = @{}
    foreach ($field in $Decision.numeric.fields) {
        Assert-ExactProperties $field @('path', 'targetType', 'grammar', 'precision', 'scale') 'NUMERIC_FIELD_SCHEMA_INVALID'
        Assert-Condition ($field.path -is [string] -and -not $numericByPath.ContainsKey($field.path)) 'NUMERIC_FIELD_PATH_INVALID'
        $numericByPath[$field.path] = $field
    }
    Assert-ExactSet @($numericByPath.Keys) @('/invoices_volumes', '/taxed_weight', '/invoices_value', '/total') 'NUMERIC_FIELD_SET_INVALID'
    $volumeField = $numericByPath['/invoices_volumes']
    Assert-ExactString $volumeField.targetType 'INTEGER_32_NON_NEGATIVE' 'NUMERIC_VOLUME_TYPE_INVALID'
    Assert-ExactString $volumeField.grammar 'ASCII_DIGITS_NO_SIGN_NO_GROUPING_NO_WHITESPACE' 'NUMERIC_VOLUME_GRAMMAR_INVALID'
    Assert-Condition ($volumeField.precision -eq 10 -and $volumeField.scale -eq 0) 'NUMERIC_VOLUME_SHAPE_INVALID'
    foreach ($path in @('/taxed_weight', '/invoices_value', '/total')) {
        $field = $numericByPath[$path]
        Assert-ExactString $field.targetType 'DECIMAL_38_9' 'NUMERIC_DECIMAL_TYPE_INVALID'
        Assert-ExactString $field.grammar 'ASCII_OPTIONAL_MINUS_DIGITS_OPTIONAL_DOT_FRACTION_NO_GROUPING_NO_WHITESPACE' 'NUMERIC_DECIMAL_GRAMMAR_INVALID'
        Assert-Condition ($field.precision -eq 38 -and $field.scale -eq 9) 'NUMERIC_DECIMAL_SHAPE_INVALID'
    }
    Assert-ExactString $Decision.numeric.invalid 'QUARANTINE_PRESERVE_RAW_NEVER_ZERO_OR_NULL' 'NUMERIC_INVALID_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.overflow 'QUARANTINE_PRESERVE_RAW' 'NUMERIC_OVERFLOW_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.scaleOverflow 'QUARANTINE_NO_ROUNDING' 'NUMERIC_SCALE_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.ambiguousLocale 'QUARANTINE_NO_HEURISTIC' 'NUMERIC_LOCALE_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.currency 'UNRESOLVED_NO_INFERENCE' 'NUMERIC_CURRENCY_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.unit 'UNRESOLVED_NO_INFERENCE' 'NUMERIC_UNIT_POLICY_INVALID'
    Assert-ExactString $Decision.numeric.rounding 'FORBIDDEN' 'NUMERIC_ROUNDING_POLICY_INVALID'

    Assert-ExactProperties $Decision.freshness @('path', 'selection', 'invalid', 'olderObservation', 'equalCanonical', 'equalDivergent', 'bothNullCanonical', 'bothNullDivergent', 'forbiddenTieBreakers') 'FRESHNESS_SCHEMA_INVALID'
    Assert-ExactString $Decision.freshness.path '/service_at' 'FRESHNESS_PATH_INVALID'
    Assert-ExactString $Decision.freshness.selection 'ONLY_PRESENT_TYPED_VALID_SERVICE_AT' 'FRESHNESS_SELECTION_INVALID'
    Assert-ExactString $Decision.freshness.invalid 'PRESERVE_RAW_AND_QUARANTINE_NO_PROMOTION' 'FRESHNESS_INVALID_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.olderObservation 'AUDIT_AND_NO_OP_NO_REGRESSION' 'FRESHNESS_OLDER_POLICY_INVALID'
    Assert-ExactString $Decision.freshness.equalCanonical 'REPLAY_NO_OP' 'FRESHNESS_EQUAL_CANONICAL_INVALID'
    Assert-ExactString $Decision.freshness.equalDivergent 'QUARANTINE_STABLE_REASON' 'FRESHNESS_EQUAL_DIVERGENT_INVALID'
    Assert-ExactString $Decision.freshness.bothNullCanonical 'REPLAY_NO_OP' 'FRESHNESS_NULL_CANONICAL_INVALID'
    Assert-ExactString $Decision.freshness.bothNullDivergent 'QUARANTINE_STABLE_REASON_NEVER_KEEP_LAST' 'FRESHNESS_NULL_DIVERGENT_INVALID'
    Assert-ExactSet @($Decision.freshness.forbiddenTieBreakers) $expectedFreshnessTieBreakers 'FRESHNESS_TIE_BREAKERS_INVALID'

    Assert-ExactProperties $Decision.status @('path', 'normalization', 'nullOrBlank', 'terminalStatuses', 'unknown', 'preservation', 'publication') 'STATUS_SCHEMA_INVALID'
    Assert-ExactString $Decision.status.path '/fit_fln_status' 'STATUS_PATH_INVALID'
    Assert-ExactString $Decision.status.normalization 'TRIM_THEN_LOWERCASE_INVARIANT' 'STATUS_NORMALIZATION_INVALID'
    Assert-ExactString $Decision.status.nullOrBlank 'sem_status' 'STATUS_BLANK_INVALID'
    Assert-ExactSet @($Decision.status.terminalStatuses) $expectedTerminalStatuses 'STATUS_TERMINAL_SET_INVALID'
    Assert-ExactString $Decision.status.unknown 'PRESERVE_RAW_AS_NON_TERMINAL_NO_INFERENCE' 'STATUS_UNKNOWN_POLICY_INVALID'
    Assert-ExactString $Decision.status.preservation 'RAW_NORMALIZED_AND_TERMINAL_FLAG_WITH_CATALOG_VERSION' 'STATUS_PRESERVATION_INVALID'
    Assert-ExactString $Decision.status.publication 'FORBIDDEN_IN_V2_028A' 'STATUS_PUBLICATION_INVALID'

    Assert-ExactProperties $Decision.unsourcedLegacy @('field', 'state', 'provenance', 'fallback', 'forbiddenFallbackPaths', 'enablementGate') 'UNSOURCED_SCHEMA_INVALID'
    Assert-ExactString $Decision.unsourcedLegacy.field 'status_branch_nickname' 'UNSOURCED_FIELD_INVALID'
    Assert-ExactString $Decision.unsourcedLegacy.state 'ABSENT' 'UNSOURCED_STATE_INVALID'
    Assert-ExactString $Decision.unsourcedLegacy.provenance 'UNSOURCED_LEGACY' 'UNSOURCED_PROVENANCE_INVALID'
    Assert-ExactString $Decision.unsourcedLegacy.fallback 'FORBIDDEN_NO_SIMILAR_FIELD_MATCH' 'UNSOURCED_FALLBACK_INVALID'
    Assert-ExactSet @($Decision.unsourcedLegacy.forbiddenFallbackPaths) $expectedForbiddenFallbackPaths 'UNSOURCED_FALLBACK_PATHS_INVALID'
    Assert-ExactString $Decision.unsourcedLegacy.enablementGate 'VERSIONED_SOURCE_PATH_OR_CONSUMER_APPROVED_RETIREMENT' 'UNSOURCED_ENABLEMENT_GATE_INVALID'

    Assert-ExactProperties $Decision.historicalLimits @('classification', 'per', 'timeoutSeconds', 'pageCap', 'rowCap', 'appliesAsV2Defaults') 'HISTORICAL_LIMITS_SCHEMA_INVALID'
    Assert-ExactString $Decision.historicalLimits.classification 'HISTORICAL_CANDIDATES_NOT_V2_DEFAULTS' 'HISTORICAL_LIMITS_CLASSIFICATION_INVALID'
    Assert-Condition ($Decision.historicalLimits.per -eq 10000 -and $Decision.historicalLimits.timeoutSeconds -eq 90 -and $Decision.historicalLimits.pageCap -eq 1200 -and $Decision.historicalLimits.rowCap -eq 150000) 'HISTORICAL_LIMITS_VALUES_INVALID'
    Assert-Condition ($Decision.historicalLimits.appliesAsV2Defaults -is [bool] -and -not $Decision.historicalLimits.appliesAsV2Defaults) 'HISTORICAL_LIMITS_DEFAULT_POLICY_INVALID'

    Assert-ExactSet @($Decision.logicalSchema) $expectedLogicalSchema 'LOGICAL_SCHEMA_INVALID'
    Assert-ExactProperties $Decision.documentation @('adr', 'runbook', 'readme') 'DOCUMENTATION_SCHEMA_INVALID'
    Assert-ExactString $Decision.documentation.adr 'docs/adr/0028-localizacao-cargas-8656-dominio-presenca-frescor-status-e-schema.md' 'DOCUMENTATION_ADR_INVALID'
    Assert-ExactString $Decision.documentation.runbook 'docs/runbooks/v2-028a-decisao-localizacao-cargas-sol.md' 'DOCUMENTATION_RUNBOOK_INVALID'
    Assert-ExactString $Decision.documentation.readme 'docs/catalogos/localizacao-cargas-v2-028/README.md' 'DOCUMENTATION_README_INVALID'
    Assert-ExactSet @($Decision.limitations) $expectedLimitations 'LIMITATIONS_INVALID'
    Assert-ExactProperties $Decision.fixture @('path', 'sha256', 'caseCount') 'FIXTURE_BINDING_SCHEMA_INVALID'
    Assert-ExactString $Decision.fixture.path 'fixtures/casos-v01.synthetic.json' 'FIXTURE_PATH_INVALID'
    Assert-Condition ($Decision.fixture.sha256 -is [string] -and $Decision.fixture.sha256 -cmatch '^[0-9a-f]{64}$') 'FIXTURE_HASH_FORMAT_INVALID'
    Assert-Condition (($Decision.fixture.caseCount -is [int] -or $Decision.fixture.caseCount -is [long]) -and $Decision.fixture.caseCount -eq $expectedCases.Count) 'FIXTURE_CASE_COUNT_INVALID'
}

function Test-Fixture {
    param([Parameter(Mandatory)][object] $Fixture)

    Assert-ExactProperties $Fixture @('schemaVersion', 'evidenceKind', 'cases') 'FIXTURE_TOP_LEVEL_SCHEMA_INVALID'
    Assert-ExactString $Fixture.schemaVersion 'v2-028a-localizacao-cargas-decision-cases-v1' 'FIXTURE_VERSION_INVALID'
    Assert-ExactString $Fixture.evidenceKind 'SYNTHETIC_DECISION_CASES_ONLY_NOT_SOURCE_CONTRACT_EVIDENCE' 'FIXTURE_EVIDENCE_KIND_INVALID'
    Assert-JsonArray $Fixture.cases 'FIXTURE_CASES_NOT_ARRAY'
    Assert-Condition (@($Fixture.cases).Count -eq $expectedCases.Count) 'FIXTURE_CASE_COUNT_INVALID'
    Assert-ExactSet @($Fixture.cases | ForEach-Object { $_.caseId }) @($expectedCases.Keys) 'FIXTURE_CASE_SET_INVALID'
    foreach ($case in $Fixture.cases) {
        Assert-ExactProperties $case @('caseId', 'rule', 'scenario', 'symbolicFacts', 'expected') 'FIXTURE_CASE_SCHEMA_INVALID'
        Assert-Condition ($case.caseId -is [string] -and $expectedCases.Contains($case.caseId)) 'FIXTURE_CASE_ID_INVALID'
        $contract = $expectedCases[$case.caseId]
        Assert-ExactString $case.rule $contract[0] 'FIXTURE_CASE_RULE_INVALID'
        Assert-Condition ($case.scenario -is [string] -and $case.scenario.Length -ge 8) 'FIXTURE_CASE_SCENARIO_INVALID'
        Assert-JsonArray $case.symbolicFacts 'FIXTURE_SYMBOLIC_FACTS_NOT_ARRAY'
        Assert-Condition (@($case.symbolicFacts).Count -ge 1) 'FIXTURE_SYMBOLIC_FACTS_EMPTY'
        foreach ($fact in $case.symbolicFacts) {
            Assert-Condition ($fact -is [string] -and $fact -cmatch '^[A-Z0-9_./=<>(), -]{3,140}$') 'FIXTURE_SYMBOLIC_FACT_INVALID'
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
        throw 'LOCALIZACAO_DECISION_CATALOG_MISSING'
    }
    $decisionDocument = Read-StrictJson $decisionPath 160KB 'DECISION_CATALOG'
    $fixtureDocument = Read-StrictJson $fixturePath 160KB 'DECISION_FIXTURE'
    $contractDocument = Read-StrictJson $contractPath 256KB 'SOURCE_CONTRACT'
    $identityDocument = Read-StrictJson $identityPath 256KB 'IDENTITY_CATALOG'
    Test-Decision $decisionDocument.Value
    Test-Fixture $fixtureDocument.Value
    Assert-ExactString $decisionDocument.Value.fixture.sha256 (Get-Sha256Lower $fixtureDocument.Bytes) 'FIXTURE_HASH_MISMATCH'

    $contract = $contractDocument.Value.contract
    Assert-ExactString $contract.contractId $decisionDocument.Value.sourceContract.id 'SOURCE_CONTRACT_BINDING_ID_MISMATCH'
    Assert-ExactString $contract.contractVersion $decisionDocument.Value.sourceContract.version 'SOURCE_CONTRACT_BINDING_VERSION_MISMATCH'
    Assert-ExactString $contract.fingerprints.release $decisionDocument.Value.sourceContract.releaseFingerprint 'SOURCE_CONTRACT_BINDING_FINGERPRINT_MISMATCH'
    Assert-ExactString $identityDocument.Value.catalogVersion $decisionDocument.Value.identityDecision.catalogVersion 'IDENTITY_BINDING_VERSION_MISMATCH'
    Assert-ExactString $identityDocument.Value.identity.entity 'localizacao_cargas' 'IDENTITY_BINDING_ENTITY_MISMATCH'
    Assert-ExactString $identityDocument.Value.identity.fingerprints.identity $decisionDocument.Value.identityDecision.identityFingerprint 'IDENTITY_BINDING_FINGERPRINT_MISMATCH'

    $portabilityRules = @(Import-Csv -LiteralPath $portabilityRulesPath | Where-Object { $_.rule_id -cmatch '^LOC-0[1-7]$' })
    Assert-Condition ($portabilityRules.Count -eq 7) 'PORTABILITY_RULE_COUNT_INVALID'
    Assert-ExactSet @($portabilityRules | ForEach-Object { $_.rule_id }) $expectedRuleIds 'PORTABILITY_RULE_SET_INVALID'
    foreach ($row in $portabilityRules) {
        Assert-ExactString $row.due_gate 'V2-028' 'PORTABILITY_GATE_INVALID'
    }

    $readme = Read-StrictText $readmePath 64KB 'CATALOG_README'
    $adr = Read-StrictText $adrPath 96KB 'ADR'
    $runbook = Read-StrictText $runbookPath 96KB 'RUNBOOK'
    foreach ($token in @('COMPLETE_LOCAL_DECISION_ONLY', 'LOC-01', 'LOC-07', 'V2-028')) {
        Assert-Condition $readme.Contains($token, [System.StringComparison]::Ordinal) 'CATALOG_README_ANCHOR_MISSING'
    }
    foreach ($token in @('V2-028a', 'COALESCE(volumes_localizacao, volumes_fretes, 0)', 'UNSOURCED_LEGACY', 'service_at')) {
        Assert-Condition $adr.Contains($token, [System.StringComparison]::Ordinal) 'ADR_ANCHOR_MISSING'
    }
    foreach ($token in @('LOCALIZACAO_DECISION_CATALOG_MISSING', 'Maven', 'SQL', 'V10')) {
        Assert-Condition $runbook.Contains($token, [System.StringComparison]::Ordinal) 'RUNBOOK_ANCHOR_MISSING'
    }

    $negativeMutations = @(
        [pscustomobject]@{ Reason = 'RULE_COUNT_INVALID'; Mutate = { param($value) $value.rules = @($value.rules | Where-Object { $_.id -cne 'LOC-07' }) } },
        [pscustomobject]@{ Reason = 'SOURCE_KEY_PATH_INVALID'; Mutate = { param($value) $value.identity.sourceKey.path = '/sequence_number' } },
        [pscustomobject]@{ Reason = 'SOURCE_KEY_FALLBACK_POLICY_INVALID'; Mutate = { param($value) $value.identity.forbiddenAlias.role = 'SOURCE_KEY_FALLBACK_ALLOWED' } },
        [pscustomobject]@{ Reason = 'UNSOURCED_FALLBACK_INVALID'; Mutate = { param($value) $value.unsourcedLegacy.fallback = 'USE_SIMILAR_NICKNAME' } },
        [pscustomobject]@{ Reason = 'FRESHNESS_NULL_DIVERGENT_INVALID'; Mutate = { param($value) $value.freshness.bothNullDivergent = 'KEEP_LAST' } },
        [pscustomobject]@{ Reason = 'FRESHNESS_PATH_INVALID'; Mutate = { param($value) $value.freshness.path = '/data_extracao' } },
        [pscustomobject]@{ Reason = 'NUMERIC_INVALID_POLICY_INVALID'; Mutate = { param($value) $value.numeric.invalid = 'COERCE_TO_ZERO_OR_NULL' } },
        [pscustomobject]@{ Reason = 'VOLUME_FREIGHT_AMBIGUITY_INVALID'; Mutate = { param($value) $value.volume.freightCandidate.ambiguity = 'PICK_FIRST' } },
        [pscustomobject]@{ Reason = 'PRESENCE_SWEEP_INVALID'; Mutate = { param($value) $value.presence.sweep = 'ENABLED' } },
        [pscustomobject]@{ Reason = 'VOLUME_PUBLICATION_INVALID'; Mutate = { param($value) $value.volume.publication = 'ENABLED' } },
        [pscustomobject]@{ Reason = 'VOLUME_FREIGHT_STORAGE_INVALID'; Mutate = { param($value) $value.volume.freightCandidate.storage = 'MATERIALIZED_RELATION' } },
        [pscustomobject]@{ Reason = 'SOURCE_CONTRACT_FINGERPRINT_INVALID'; Mutate = { param($value) $value.sourceContract.releaseFingerprint = ('0' * 64) } },
        [pscustomobject]@{ Reason = 'IDENTITY_DECISION_FINGERPRINT_INVALID'; Mutate = { param($value) $value.identityDecision.identityFingerprint = ('f' * 64) } }
    )
    $blocked = 0
    foreach ($mutation in $negativeMutations) {
        $candidate = Copy-JsonValue $decisionDocument.Value
        & $mutation.Mutate $candidate
        try {
            Test-Decision $candidate
        } catch {
            Assert-Condition ($_.Exception.Message -ceq $mutation.Reason) 'NEGATIVE_REASON_MISMATCH'
            $blocked++
        }
    }
    Assert-Condition ($blocked -eq $negativeMutations.Count) 'NEGATIVE_CASE_ACCEPTED'
    Write-Output ("PASS: decisão local V2-028a/Localização 8656 validada; rules={0} synthetic_cases={1} negative_mutations={2} result=COMPLETE_LOCAL_DECISION_ONLY implementation=OPEN relation=OPEN sweep=DISABLED publication=FORBIDDEN." -f $expectedRuleIds.Count, $expectedCases.Count, $blocked)
} catch {
    $reason = $_.Exception.Message
    if ($reason -isnot [string] -or $reason -cnotmatch '^[A-Z][A-Z0-9_]{2,100}$') {
        $reason = 'INTERNAL_ERROR_REDACTED'
    }
    Write-Output ("LOCALIZACAO_V2_028A_DECISION status=FAIL reason={0}" -f $reason)
    exit 1
}

exit 0

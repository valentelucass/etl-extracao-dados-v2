#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter()][switch] $ContractOnly,
    [Parameter()][switch] $ValidateEvidence
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($ContractOnly.IsPresent -eq $ValidateEvidence.IsPresent) {
    Write-Output 'V2_041_INTAKE status=FAIL reason=EXECUTION_MODE_INVALID'
    exit 1
}

$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$catalogRoot = Join-Path $repositoryRoot 'docs\catalogos\evidencia-rotacao-v2-041'
$manifestPath = Join-Path $catalogRoot 'manifesto.json'
$fixturePath = Join-Path $catalogRoot 'fixtures\atestado-completo.synthetic.json'
$evidencePath = Join-Path $repositoryRoot 'target\v2-041\sanitized-attestation.json'
$expectedCredentialClasses = @(
    'ESL_REST',
    'ESL_GRAPHQL',
    'ESL_DATA_EXPORT',
    'RASTER',
    'SQL_SERVER_LEGACY',
    'V2_SHADOW_TESTS'
)
$expectedApprovalRoles = @(
    'SECURITY',
    'LEGACY_OWNER',
    'LEGACY_OPERATIONS',
    'ESL_INTEGRATION_OWNER',
    'RASTER_OWNER',
    'DBA',
    'V2_ENVIRONMENT_OWNER'
)
$expectedScanProfiles = @(
    'OFFLINE_SECRET_SCAN|WORKTREE',
    'GITLEAKS|WORKTREE',
    'GITLEAKS|GIT_HISTORY'
)
$expectedLimitations = @(
    'NO_REMOTE_CHECK_BY_VALIDATOR',
    'NO_AUTHENTICITY_VERIFICATION',
    'NO_OPERATIONAL_TRUTH_ESTABLISHED',
    'NO_NOMINAL_OWNER_ACCEPTANCE_VERIFICATION',
    'NO_V2_041_STATE_CHANGE',
    'NO_DOWNSTREAM_AUTHORIZATION_INHERITANCE'
)

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
    $actualSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($name in $actual) {
        Assert-Condition -Condition $actualSet.Add($name) -Reason $Reason
    }
    $expectedSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($name in $Expected) {
        Assert-Condition -Condition $expectedSet.Add($name) -Reason $Reason
    }
    Assert-Condition -Condition ($actualSet.Count -eq $expectedSet.Count) -Reason $Reason
    foreach ($name in $expectedSet) {
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
        Assert-Condition -Condition ($item -is [string]) -Reason $Reason
        Assert-Condition -Condition $actualSet.Add($item) -Reason $Reason
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

    $repositoryRootItem = Get-Item -LiteralPath $repositoryRoot -ErrorAction Stop
    $repositoryRootLinkType = $repositoryRootItem.PSObject.Properties['LinkType']
    if (($repositoryRootItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
        ($null -ne $repositoryRootLinkType -and $null -ne $repositoryRootLinkType.Value)) {
        throw "${ReasonPrefix}_LINK_NOT_ALLOWED"
    }
    if (-not (Test-Path -LiteralPath $LiteralPath -PathType Leaf)) {
        throw "${ReasonPrefix}_MISSING"
    }
    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    $itemLinkType = $item.PSObject.Properties['LinkType']
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
        ($null -ne $itemLinkType -and $null -ne $itemLinkType.Value)) {
        throw "${ReasonPrefix}_LINK_NOT_ALLOWED"
    }
    $currentDirectory = $item.Directory
    while ($null -ne $currentDirectory -and
        -not $currentDirectory.FullName.Equals($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $directoryLinkType = $currentDirectory.PSObject.Properties['LinkType']
        if (($currentDirectory.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0 -or
            ($null -ne $directoryLinkType -and $null -ne $directoryLinkType.Value)) {
            throw "${ReasonPrefix}_LINK_NOT_ALLOWED"
        }
        $currentDirectory = $currentDirectory.Parent
    }
    if ($null -eq $currentDirectory) {
        throw "${ReasonPrefix}_OUTSIDE_REPOSITORY"
    }
    if ($item.Length -gt $MaximumBytes) {
        throw "${ReasonPrefix}_TOO_LARGE"
    }
    $bytes = [System.IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "${ReasonPrefix}_INVALID_UTF8"
    }
    try {
        $text = $strictUtf8.GetString($bytes)
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

    return [pscustomobject]@{ Value = $value; Bytes = $bytes }
}

function Get-Sha256Lower {
    param([Parameter(Mandatory)][byte[]] $Bytes)

    return [Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-RepositoryHeadSha {
    try {
        $gitOutput = @(& git -C $repositoryRoot rev-parse --verify 'HEAD^{commit}' 2>$null)
        $gitExitCode = $LASTEXITCODE
    } catch {
        throw 'ATTESTATION_REPOSITORY_HEAD_UNAVAILABLE'
    }
    if ($gitExitCode -ne 0 -or $gitOutput.Count -ne 1 -or
        $gitOutput[0] -isnot [string] -or $gitOutput[0] -cnotmatch '^[0-9a-f]{40}$') {
        throw 'ATTESTATION_REPOSITORY_HEAD_UNAVAILABLE'
    }
    return $gitOutput[0]
}

function ConvertTo-UtcTimestamp {
    param(
        [Parameter()][AllowNull()][object] $Value,
        [Parameter(Mandatory)][string] $Reason
    )

    if ($Value -isnot [string]) {
        throw $Reason
    }
    $parsed = [datetimeoffset]::MinValue
    $valid = [datetimeoffset]::TryParseExact(
        $Value,
        'yyyy-MM-ddTHH:mm:ssZ',
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::AssumeUniversal,
        [ref] $parsed
    )
    Assert-Condition -Condition $valid -Reason $Reason
    return $parsed.ToUniversalTime()
}

function Assert-CountValue {
    param(
        [Parameter()][AllowNull()][object] $Value,
        [Parameter(Mandatory)][string] $Reason
    )

    $isInteger = $Value -is [int] -or $Value -is [long]
    Assert-Condition -Condition $isInteger -Reason $Reason
    Assert-Condition -Condition ([long] $Value -ge 0 -and [long] $Value -le 100000) -Reason $Reason
}

function Copy-JsonValue {
    param([Parameter(Mandatory)][object] $Value)

    return ($Value | ConvertTo-Json -Depth 100 -Compress) | ConvertFrom-Json -Depth 100 -DateKind String
}

function Test-RotationAttestation {
    param(
        [Parameter(Mandatory)][object] $Attestation,
        [Parameter(Mandatory)][string] $ExpectedEvidenceKind,
        [Parameter()][AllowEmptyString()][string] $ExpectedRepositorySha = ''
    )

    Assert-ExactProperties -Value $Attestation -Expected @(
        'schemaVersion',
        'evidenceKind',
        'scope',
        'roadmapEffect',
        'closureState',
        'assertedOverallResult',
        'restrictedEvidenceReferenceRecorded',
        'attestedAtUtc',
        'continuity',
        'credentialClasses',
        'approvals',
        'scans',
        'declarations',
        'limitations'
    ) -Reason 'ATTESTATION_TOP_LEVEL_SCHEMA_INVALID'
    Assert-ExactString -Value $Attestation.schemaVersion -Expected 'v2-041-sanitized-attestation-v1' -Reason 'ATTESTATION_SCHEMA_VERSION_INVALID'
    Assert-ExactString -Value $Attestation.evidenceKind -Expected $ExpectedEvidenceKind -Reason 'ATTESTATION_EVIDENCE_KIND_INVALID'
    Assert-ExactString -Value $Attestation.scope -Expected 'V2-041' -Reason 'ATTESTATION_SCOPE_INVALID'
    Assert-ExactString -Value $Attestation.roadmapEffect -Expected 'NONE' -Reason 'ATTESTATION_ROADMAP_EFFECT_INVALID'
    Assert-ExactString -Value $Attestation.closureState -Expected 'V2_041_REMAINS_EXTERNAL_HOLD' -Reason 'ATTESTATION_CLOSURE_STATE_INVALID'
    Assert-ExactString -Value $Attestation.assertedOverallResult -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_RESULT_NOT_PASS'
    Assert-Condition -Condition ($Attestation.restrictedEvidenceReferenceRecorded -is [bool] -and $Attestation.restrictedEvidenceReferenceRecorded) -Reason 'ATTESTATION_RESTRICTED_REFERENCE_MISSING'
    $attestedAt = ConvertTo-UtcTimestamp -Value $Attestation.attestedAtUtc -Reason 'ATTESTATION_TIMESTAMP_INVALID'
    $freshnessFloor = [datetimeoffset]::MinValue
    if ($ExpectedEvidenceKind -ceq 'UNVERIFIED_EXTERNAL_ATTESTATION') {
        $now = [datetimeoffset]::UtcNow
        $freshnessFloor = $now.AddDays(-30)
        Assert-Condition -Condition ($attestedAt -le $now.AddMinutes(5)) -Reason 'ATTESTATION_TIMESTAMP_IN_FUTURE'
        Assert-Condition -Condition ($attestedAt -ge $freshnessFloor) -Reason 'ATTESTATION_TIMESTAMP_STALE'
    }

    Assert-JsonArray -Value $Attestation.limitations -Reason 'ATTESTATION_LIMITATIONS_NOT_ARRAY'
    Assert-ExactSet -Actual @($Attestation.limitations) -Expected $expectedLimitations -Reason 'ATTESTATION_LIMITATION_SET_INVALID'

    Assert-ExactProperties -Value $Attestation.declarations -Expected @(
        'containsSecretValues',
        'containsCredentialNames',
        'containsUrls',
        'containsTenantIdentifiers',
        'containsPayloads',
        'containsCursors',
        'containsBusinessIdentifiers'
    ) -Reason 'ATTESTATION_DECLARATIONS_SCHEMA_INVALID'
    foreach ($property in $Attestation.declarations.PSObject.Properties) {
        Assert-Condition -Condition ($property.Value -is [bool] -and -not $property.Value) -Reason 'ATTESTATION_FORBIDDEN_CONTENT_DECLARED'
    }

    Assert-ExactProperties -Value $Attestation.continuity -Expected @(
        'legacyWriterHealthy',
        'mandatoryJobsHealthy',
        'concurrentWriterAbsent',
        'rollbackPlanReady',
        'checkedAtUtc'
    ) -Reason 'ATTESTATION_CONTINUITY_SCHEMA_INVALID'
    foreach ($name in @('legacyWriterHealthy', 'mandatoryJobsHealthy', 'concurrentWriterAbsent', 'rollbackPlanReady')) {
        Assert-ExactString -Value $Attestation.continuity.$name -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_CONTINUITY_NOT_PASS'
    }
    $continuityAt = ConvertTo-UtcTimestamp -Value $Attestation.continuity.checkedAtUtc -Reason 'ATTESTATION_CONTINUITY_TIMESTAMP_INVALID'
    Assert-Condition -Condition ($continuityAt -ge $freshnessFloor) -Reason 'ATTESTATION_CONTINUITY_TIMESTAMP_STALE'
    Assert-Condition -Condition ($continuityAt -le $attestedAt) -Reason 'ATTESTATION_CONTINUITY_AFTER_ATTESTATION'

    Assert-JsonArray -Value $Attestation.credentialClasses -Reason 'ATTESTATION_CREDENTIAL_CLASSES_NOT_ARRAY'
    Assert-Condition -Condition (@($Attestation.credentialClasses).Count -eq $expectedCredentialClasses.Count) -Reason 'ATTESTATION_CREDENTIAL_CLASS_COUNT_INVALID'
    Assert-ExactSet -Actual @($Attestation.credentialClasses | ForEach-Object { $_.credentialClass }) -Expected $expectedCredentialClasses -Reason 'ATTESTATION_CREDENTIAL_CLASS_SET_INVALID'
    $ownerByClass = @{
        ESL_REST = 'LEGACY_OWNER'
        ESL_GRAPHQL = 'ESL_INTEGRATION_OWNER'
        ESL_DATA_EXPORT = 'ESL_INTEGRATION_OWNER'
        RASTER = 'RASTER_OWNER'
        SQL_SERVER_LEGACY = 'DBA'
        V2_SHADOW_TESTS = 'V2_ENVIRONMENT_OWNER'
    }
    foreach ($entry in $Attestation.credentialClasses) {
        Assert-ExactProperties -Value $entry -Expected @(
            'credentialClass',
            'ownerRole',
            'disposition',
            'liveConsumers',
            'assertedPreChangeHealth',
            'assertedReplacementState',
            'assertedReloadState',
            'assertedPostChangeHealth',
            'assertedPreviousCredentialInvalidation',
            'assertedRollbackState',
            'completedAtUtc'
        ) -Reason 'ATTESTATION_CREDENTIAL_CLASS_SCHEMA_INVALID'
        Assert-Condition -Condition ($entry.credentialClass -is [string]) -Reason 'ATTESTATION_CREDENTIAL_CLASS_SET_INVALID'
        $className = $entry.credentialClass
        Assert-ExactString -Value $entry.ownerRole -Expected $ownerByClass[$className] -Reason 'ATTESTATION_CREDENTIAL_OWNER_ROLE_INVALID'
        Assert-Condition -Condition ($entry.disposition -is [string]) -Reason 'ATTESTATION_CREDENTIAL_DISPOSITION_NOT_STRING'
        Assert-ExactProperties -Value $entry.liveConsumers -Expected @('jobs', 'services', 'operators', 'ci', 'workstations') -Reason 'ATTESTATION_CONSUMER_COUNTS_SCHEMA_INVALID'
        foreach ($count in $entry.liveConsumers.PSObject.Properties) {
            Assert-CountValue -Value $count.Value -Reason 'ATTESTATION_CONSUMER_COUNT_INVALID'
        }
        $liveConsumerTotal = [long] $entry.liveConsumers.jobs +
            [long] $entry.liveConsumers.services +
            [long] $entry.liveConsumers.operators +
            [long] $entry.liveConsumers.ci +
            [long] $entry.liveConsumers.workstations
        Assert-ExactString -Value $entry.assertedPreviousCredentialInvalidation -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_PREVIOUS_CREDENTIAL_NOT_INVALIDATED'
        Assert-ExactString -Value $entry.assertedRollbackState -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_ROLLBACK_NOT_READY'
        if ($className -ceq 'ESL_REST') {
            Assert-ExactString -Value $entry.disposition -Expected 'RETIRED' -Reason 'ATTESTATION_ESL_REST_NOT_RETIRED'
        } elseif ($className -ceq 'RASTER') {
            Assert-Condition -Condition (@('ROTATED', 'DISABLED') -ccontains $entry.disposition) -Reason 'ATTESTATION_RASTER_DISPOSITION_INVALID'
        } else {
            Assert-ExactString -Value $entry.disposition -Expected 'ROTATED' -Reason 'ATTESTATION_ACTIVE_CREDENTIAL_NOT_ROTATED'
        }
        Assert-ExactString -Value $entry.assertedPreChangeHealth -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_PRE_CHANGE_HEALTH_NOT_PASS'
        if ($entry.disposition -ceq 'ROTATED') {
            foreach ($name in @('assertedReplacementState', 'assertedReloadState', 'assertedPostChangeHealth')) {
                Assert-ExactString -Value $entry.$name -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_ROTATION_STEP_NOT_PASS'
            }
        } else {
            Assert-Condition -Condition ($liveConsumerTotal -eq 0) -Reason 'ATTESTATION_INACTIVE_CREDENTIAL_HAS_LIVE_CONSUMER'
            foreach ($name in @('assertedReplacementState', 'assertedReloadState', 'assertedPostChangeHealth')) {
                Assert-ExactString -Value $entry.$name -Expected 'NOT_APPLICABLE' -Reason 'ATTESTATION_INACTIVE_CREDENTIAL_STEP_INVALID'
            }
        }
        $completedAt = ConvertTo-UtcTimestamp -Value $entry.completedAtUtc -Reason 'ATTESTATION_CREDENTIAL_TIMESTAMP_INVALID'
        Assert-Condition -Condition ($completedAt -ge $freshnessFloor) -Reason 'ATTESTATION_CREDENTIAL_TIMESTAMP_STALE'
        Assert-Condition -Condition ($completedAt -le $continuityAt) -Reason 'ATTESTATION_CREDENTIAL_AFTER_CONTINUITY_CHECK'
        Assert-Condition -Condition ($completedAt -le $attestedAt) -Reason 'ATTESTATION_CREDENTIAL_AFTER_ATTESTATION'
    }

    Assert-JsonArray -Value $Attestation.approvals -Reason 'ATTESTATION_APPROVALS_NOT_ARRAY'
    Assert-Condition -Condition (@($Attestation.approvals).Count -eq $expectedApprovalRoles.Count) -Reason 'ATTESTATION_APPROVAL_COUNT_INVALID'
    Assert-ExactSet -Actual @($Attestation.approvals | ForEach-Object { $_.role }) -Expected $expectedApprovalRoles -Reason 'ATTESTATION_APPROVAL_ROLE_SET_INVALID'
    $approvalTimes = [System.Collections.Generic.List[datetimeoffset]]::new()
    foreach ($approval in $Attestation.approvals) {
        Assert-ExactProperties -Value $approval -Expected @('role', 'decision', 'nominalIdentityStoredRestricted', 'attestedAtUtc') -Reason 'ATTESTATION_APPROVAL_SCHEMA_INVALID'
        Assert-Condition -Condition ($approval.role -is [string]) -Reason 'ATTESTATION_APPROVAL_ROLE_SET_INVALID'
        Assert-ExactString -Value $approval.decision -Expected 'ASSERTED_APPROVED' -Reason 'ATTESTATION_APPROVAL_NOT_APPROVED'
        Assert-Condition -Condition ($approval.nominalIdentityStoredRestricted -is [bool] -and $approval.nominalIdentityStoredRestricted) -Reason 'ATTESTATION_NOMINAL_IDENTITY_NOT_RESTRICTED'
        $approvalAt = ConvertTo-UtcTimestamp -Value $approval.attestedAtUtc -Reason 'ATTESTATION_APPROVAL_TIMESTAMP_INVALID'
        Assert-Condition -Condition ($approvalAt -ge $freshnessFloor) -Reason 'ATTESTATION_APPROVAL_TIMESTAMP_STALE'
        Assert-Condition -Condition ($approvalAt -le $attestedAt) -Reason 'ATTESTATION_APPROVAL_AFTER_ATTESTATION'
        $approvalTimes.Add($approvalAt)
    }

    Assert-JsonArray -Value $Attestation.scans -Reason 'ATTESTATION_SCANS_NOT_ARRAY'
    Assert-Condition -Condition (@($Attestation.scans).Count -eq $expectedScanProfiles.Count) -Reason 'ATTESTATION_SCAN_COUNT_INVALID'
    $repositoryShas = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $actualScanProfiles = [System.Collections.Generic.List[string]]::new()
    $scanTimes = [System.Collections.Generic.List[datetimeoffset]]::new()
    foreach ($scan in $Attestation.scans) {
        Assert-ExactProperties -Value $scan -Expected @(
            'tool',
            'scope',
            'version',
            'result',
            'assertedScopeComplete',
            'candidateItems',
            'inspectedItems',
            'uninspectedItems',
            'oversizedUninspectedItems',
            'findings',
            'repositorySha',
            'executedAtUtc'
        ) -Reason 'ATTESTATION_SCAN_SCHEMA_INVALID'
        Assert-Condition -Condition ($scan.tool -is [string] -and $scan.scope -is [string]) -Reason 'ATTESTATION_SCAN_PROFILE_SET_INVALID'
        $actualScanProfiles.Add(('{0}|{1}' -f $scan.tool, $scan.scope))
        Assert-ExactString -Value $scan.result -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_SCAN_NOT_PASS'
        Assert-ExactString -Value $scan.assertedScopeComplete -Expected 'ASSERTED_PASS' -Reason 'ATTESTATION_SCAN_SCOPE_INCOMPLETE'
        foreach ($countName in @('candidateItems', 'inspectedItems', 'uninspectedItems', 'oversizedUninspectedItems', 'findings')) {
            Assert-CountValue -Value $scan.$countName -Reason 'ATTESTATION_SCAN_COUNT_INVALID'
        }
        Assert-Condition -Condition ([long] $scan.candidateItems -gt 0) -Reason 'ATTESTATION_SCAN_EMPTY_SCOPE'
        Assert-Condition -Condition ([long] $scan.inspectedItems -eq [long] $scan.candidateItems) -Reason 'ATTESTATION_SCAN_SCOPE_INCOMPLETE'
        Assert-Condition -Condition ([long] $scan.uninspectedItems -eq 0 -and [long] $scan.oversizedUninspectedItems -eq 0) -Reason 'ATTESTATION_SCAN_SCOPE_INCOMPLETE'
        Assert-Condition -Condition ([long] $scan.findings -eq 0) -Reason 'ATTESTATION_SCAN_FINDINGS_PRESENT'
        Assert-Condition -Condition ($scan.repositorySha -is [string] -and $scan.repositorySha -cmatch '^[0-9a-f]{40}$') -Reason 'ATTESTATION_REPOSITORY_SHA_INVALID'
        if (-not [string]::IsNullOrEmpty($ExpectedRepositorySha)) {
            Assert-ExactString -Value $scan.repositorySha -Expected $ExpectedRepositorySha -Reason 'ATTESTATION_REPOSITORY_SHA_NOT_CURRENT_HEAD'
        }
        [void] $repositoryShas.Add($scan.repositorySha)
        if ($scan.tool -ceq 'OFFLINE_SECRET_SCAN') {
            Assert-ExactString -Value $scan.version -Expected 'REPOSITORY_CURRENT' -Reason 'ATTESTATION_OFFLINE_SCANNER_VERSION_INVALID'
        } else {
            Assert-ExactString -Value $scan.version -Expected '8.29.1' -Reason 'ATTESTATION_GITLEAKS_VERSION_INVALID'
        }
        $scanAt = ConvertTo-UtcTimestamp -Value $scan.executedAtUtc -Reason 'ATTESTATION_SCAN_TIMESTAMP_INVALID'
        Assert-Condition -Condition ($scanAt -ge $freshnessFloor) -Reason 'ATTESTATION_SCAN_TIMESTAMP_STALE'
        Assert-Condition -Condition ($scanAt -ge $continuityAt) -Reason 'ATTESTATION_SCAN_BEFORE_CONTINUITY_CHECK'
        Assert-Condition -Condition ($scanAt -le $attestedAt) -Reason 'ATTESTATION_SCAN_AFTER_ATTESTATION'
        $scanTimes.Add($scanAt)
    }
    Assert-ExactSet -Actual @($actualScanProfiles) -Expected $expectedScanProfiles -Reason 'ATTESTATION_SCAN_PROFILE_SET_INVALID'
    Assert-Condition -Condition ($repositoryShas.Count -eq 1) -Reason 'ATTESTATION_SCAN_SHA_MISMATCH'
    $latestScanAt = ($scanTimes | Measure-Object -Maximum).Maximum
    foreach ($approvalAt in $approvalTimes) {
        Assert-Condition -Condition ($approvalAt -ge $latestScanAt) -Reason 'ATTESTATION_APPROVAL_BEFORE_SCANS_COMPLETE'
    }
}

function Test-CanonicalContract {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or -not (Test-Path -LiteralPath $fixturePath -PathType Leaf)) {
        throw 'V2_041_ATTESTATION_CATALOG_MISSING'
    }
    $manifestDocument = Read-StrictJson -LiteralPath $manifestPath -MaximumBytes 32KB -ReasonPrefix 'ATTESTATION_MANIFEST'
    $fixtureDocument = Read-StrictJson -LiteralPath $fixturePath -MaximumBytes 64KB -ReasonPrefix 'ATTESTATION_FIXTURE'
    $manifest = $manifestDocument.Value
    Assert-ExactProperties -Value $manifest -Expected @(
        'schemaVersion',
        'fixture',
        'fixtureSha256',
        'credentialClasses',
        'approvalRoles',
        'scanProfiles',
        'limitations',
        'negativeCaseCount'
    ) -Reason 'ATTESTATION_MANIFEST_SCHEMA_INVALID'
    Assert-ExactString -Value $manifest.schemaVersion -Expected 'v2-041-sanitized-attestation-v1' -Reason 'ATTESTATION_MANIFEST_VERSION_INVALID'
    Assert-ExactString -Value $manifest.fixture -Expected 'fixtures/atestado-completo.synthetic.json' -Reason 'ATTESTATION_MANIFEST_FIXTURE_INVALID'
    Assert-ExactString -Value $manifest.fixtureSha256 -Expected (Get-Sha256Lower -Bytes $fixtureDocument.Bytes) -Reason 'ATTESTATION_FIXTURE_HASH_MISMATCH'
    Assert-JsonArray -Value $manifest.credentialClasses -Reason 'ATTESTATION_MANIFEST_CLASSES_NOT_ARRAY'
    Assert-JsonArray -Value $manifest.approvalRoles -Reason 'ATTESTATION_MANIFEST_ROLES_NOT_ARRAY'
    Assert-JsonArray -Value $manifest.scanProfiles -Reason 'ATTESTATION_MANIFEST_SCANS_NOT_ARRAY'
    Assert-JsonArray -Value $manifest.limitations -Reason 'ATTESTATION_MANIFEST_LIMITATIONS_NOT_ARRAY'
    Assert-ExactSet -Actual @($manifest.credentialClasses) -Expected $expectedCredentialClasses -Reason 'ATTESTATION_MANIFEST_CLASS_SET_INVALID'
    Assert-ExactSet -Actual @($manifest.approvalRoles) -Expected $expectedApprovalRoles -Reason 'ATTESTATION_MANIFEST_ROLE_SET_INVALID'
    Assert-ExactSet -Actual @($manifest.scanProfiles) -Expected $expectedScanProfiles -Reason 'ATTESTATION_MANIFEST_SCAN_SET_INVALID'
    Assert-ExactSet -Actual @($manifest.limitations) -Expected $expectedLimitations -Reason 'ATTESTATION_MANIFEST_LIMITATION_SET_INVALID'

    Test-RotationAttestation -Attestation $fixtureDocument.Value -ExpectedEvidenceKind 'SYNTHETIC_CONTRACT_TEST_ONLY'

    $negativeCases = @(
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_RESULT_NOT_PASS'; Mutate = { param($value) $value.assertedOverallResult = 'ASSERTED_BLOCKED' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_FORBIDDEN_CONTENT_DECLARED'; Mutate = { param($value) $value.declarations.containsSecretValues = $true } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_CREDENTIAL_CLASS_COUNT_INVALID'; Mutate = { param($value) $value.credentialClasses = @($value.credentialClasses | Select-Object -Skip 1) } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_CREDENTIAL_CLASS_SET_INVALID'; Mutate = { param($value) $value.credentialClasses[1].credentialClass = 'ESL_REST' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_PREVIOUS_CREDENTIAL_NOT_INVALIDATED'; Mutate = { param($value) $value.credentialClasses[1].assertedPreviousCredentialInvalidation = 'ASSERTED_BLOCKED' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_SCAN_FINDINGS_PRESENT'; Mutate = { param($value) $value.scans[0].findings = 1 } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_SCAN_SHA_MISMATCH'; Mutate = { param($value) $value.scans[1].repositorySha = ('f' * 40) } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_NOMINAL_IDENTITY_NOT_RESTRICTED'; Mutate = { param($value) $value.approvals[0].nominalIdentityStoredRestricted = $false } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_TIMESTAMP_INVALID'; Mutate = { param($value) $value.attestedAtUtc = 'INVALID' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_EVIDENCE_KIND_INVALID'; Mutate = { param($value) $value.evidenceKind = 'UNVERIFIED_EXTERNAL_ATTESTATION' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_RASTER_DISPOSITION_INVALID'; Mutate = { param($value) $value.credentialClasses[3].disposition = 'disabled' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_INACTIVE_CREDENTIAL_HAS_LIVE_CONSUMER'; Mutate = { param($value) $value.credentialClasses[0].liveConsumers.jobs = 1 } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_CREDENTIAL_AFTER_CONTINUITY_CHECK'; Mutate = { param($value) $value.credentialClasses[0].completedAtUtc = '2000-01-01T00:26:00Z' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_TOP_LEVEL_SCHEMA_INVALID'; Mutate = { param($value) $value | Add-Member -NotePropertyName secret -NotePropertyValue 'FORBIDDEN' } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_SCHEMA_VERSION_INVALID'; Mutate = { param($value) $value.schemaVersion = @('v2-041-sanitized-attestation-v1') } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_SCAN_SCOPE_INCOMPLETE'; Mutate = { param($value) $value.scans[0].inspectedItems = 9 } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_APPROVAL_ROLE_SET_INVALID'; Mutate = { param($value) $value.approvals[0].role = @('SECURITY') } },
        [pscustomobject]@{ ExpectedReason = 'ATTESTATION_APPROVAL_ROLE_SET_INVALID'; Mutate = { param($value) $value.approvals[0].role = @('SECURITY', 'LEGACY_OWNER'); $value.approvals[1].role = @() } }
    )
    $expectedBlockedCases = $negativeCases.Count + 1
    Assert-CountValue -Value $manifest.negativeCaseCount -Reason 'ATTESTATION_NEGATIVE_CASE_COUNT_INVALID'
    Assert-Condition -Condition ([long] $manifest.negativeCaseCount -eq $expectedBlockedCases) -Reason 'ATTESTATION_NEGATIVE_CASE_COUNT_INVALID'
    $blocked = 0
    foreach ($negativeCase in $negativeCases) {
        $candidate = Copy-JsonValue -Value $fixtureDocument.Value
        & $negativeCase.Mutate $candidate
        try {
            Test-RotationAttestation -Attestation $candidate -ExpectedEvidenceKind 'SYNTHETIC_CONTRACT_TEST_ONLY'
        } catch {
            Assert-Condition -Condition ($_.Exception.Message -ceq $negativeCase.ExpectedReason) -Reason 'ATTESTATION_NEGATIVE_REASON_MISMATCH'
            $blocked++
        }
    }

    $externalCandidate = Copy-JsonValue -Value $fixtureDocument.Value
    $now = [datetimeoffset]::UtcNow
    $externalCandidate.evidenceKind = 'UNVERIFIED_EXTERNAL_ATTESTATION'
    $externalCandidate.attestedAtUtc = $now.ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
    $externalCandidate.continuity.checkedAtUtc = $now.AddMinutes(-5).ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
    foreach ($credentialClass in $externalCandidate.credentialClasses) {
        $credentialClass.completedAtUtc = $now.AddMinutes(-10).ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
    }
    for ($index = 0; $index -lt $externalCandidate.scans.Count; $index++) {
        $externalCandidate.scans[$index].executedAtUtc = $now.AddMinutes(-4 + $index).ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
    }
    foreach ($approval in $externalCandidate.approvals) {
        $approval.attestedAtUtc = $now.AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ssZ', [System.Globalization.CultureInfo]::InvariantCulture)
    }
    Test-RotationAttestation -Attestation $externalCandidate -ExpectedEvidenceKind 'UNVERIFIED_EXTERNAL_ATTESTATION' -ExpectedRepositorySha ('1' * 40)
    $staleExternalCandidate = Copy-JsonValue -Value $externalCandidate
    $staleExternalCandidate.continuity.checkedAtUtc = '2000-01-01T00:25:00Z'
    try {
        Test-RotationAttestation -Attestation $staleExternalCandidate -ExpectedEvidenceKind 'UNVERIFIED_EXTERNAL_ATTESTATION' -ExpectedRepositorySha ('1' * 40)
    } catch {
        Assert-Condition -Condition ($_.Exception.Message -ceq 'ATTESTATION_CONTINUITY_TIMESTAMP_STALE') -Reason 'ATTESTATION_NEGATIVE_REASON_MISMATCH'
        $blocked++
    }
    Assert-Condition -Condition ($blocked -eq $expectedBlockedCases) -Reason 'ATTESTATION_NEGATIVE_CASE_ACCEPTED'
    return $blocked
}

try {
    $blockedCases = Test-CanonicalContract
    if ($ValidateEvidence) {
        $evidenceDocument = Read-StrictJson -LiteralPath $evidencePath -MaximumBytes 32KB -ReasonPrefix 'ATTESTATION_EVIDENCE'
        $repositoryHeadSha = Get-RepositoryHeadSha
        Test-RotationAttestation -Attestation $evidenceDocument.Value -ExpectedEvidenceKind 'UNVERIFIED_EXTERNAL_ATTESTATION' -ExpectedRepositorySha $repositoryHeadSha
        Write-Output 'V2_041_EVIDENCE_INTAKE status=STRUCTURALLY_VALID_UNVERIFIED rotation=NOT_ESTABLISHED roadmap_effect=NONE gate=EXTERNAL_HOLD_PENDING_OWNER_REVIEW'
    } else {
        Write-Output ('V2_041_CONTRACT_GATE status=CONTRACT_VALID credential_classes={0} approvals={1} scans={2} blocked_negative_cases={3} evidence=NOT_EVALUATED gate=EXTERNAL_HOLD' -f
            $expectedCredentialClasses.Count,
            $expectedApprovalRoles.Count,
            $expectedScanProfiles.Count,
            $blockedCases)
    }
} catch {
    $reason = $_.Exception.Message
    if ($reason -isnot [string] -or $reason -cnotmatch '^[A-Z][A-Z0-9_]{2,100}$') {
        $reason = 'INTERNAL_ERROR_REDACTED'
    }
    Write-Output ('V2_041_INTAKE status=FAIL reason={0}' -f $reason)
    exit 1
}

exit 0

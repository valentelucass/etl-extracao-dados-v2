#Requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$ArtifactsOnly,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = Join-Path $repositoryRoot 'docs\catalogos\medicao-v2-050\manifesto.json'
$receiptRelativePath = 'target/v2-050-measurement/measurement-foundation-receipt.json'
$receiptPath = Join-Path $repositoryRoot $receiptRelativePath
$utf8Strict = [Text.UTF8Encoding]::new($false, $true)
$utf8NoBom = [Text.UTF8Encoding]::new($false)
$ordinal = [StringComparer]::Ordinal
$ordinalIgnoreCase = [StringComparer]::OrdinalIgnoreCase
$script:adversarialCases = 0

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    [Console]::Out.WriteLine(
        'V2_050_MEASUREMENT_FOUNDATION status=FAIL reason=V2_050_MEASUREMENT_FOUNDATION_MANIFEST_MISSING')
    exit 1
}

function Fail([string]$Reason) {
    throw [InvalidOperationException]::new($Reason)
}

function Assert-True([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { Fail $Reason }
}

function Assert-String($Actual, [string]$Expected, [string]$Reason) {
    if ($Actual -isnot [string] -or
        -not [string]::Equals($Actual, $Expected, [StringComparison]::Ordinal)) {
        Fail $Reason
    }
}

function Assert-Integer($Actual, [long]$Expected, [string]$Reason) {
    if (($Actual -isnot [int] -and $Actual -isnot [long]) -or [long]$Actual -ne $Expected) {
        Fail $Reason
    }
}

function Assert-NonNegativeInteger($Actual, [string]$Reason) {
    if (($Actual -isnot [int] -and $Actual -isnot [long]) -or [long]$Actual -lt 0L) {
        Fail $Reason
    }
}

function Assert-PositiveInteger($Actual, [string]$Reason) {
    if (($Actual -isnot [int] -and $Actual -isnot [long]) -or [long]$Actual -le 0L) {
        Fail $Reason
    }
}

function Assert-Boolean($Actual, [bool]$Expected, [string]$Reason) {
    if ($Actual -isnot [bool] -or $Actual -ne $Expected) { Fail $Reason }
}

function Assert-UniqueExactSet($Actual, [string[]]$Expected, [string]$Reason) {
    $actualItems = @($Actual)
    if ($actualItems.Count -ne $Expected.Count) { Fail $Reason }
    $seen = [Collections.Generic.HashSet[string]]::new($ordinal)
    $seenFolded = [Collections.Generic.HashSet[string]]::new($ordinalIgnoreCase)
    foreach ($item in $actualItems) {
        if ($item -isnot [string] -or -not $seen.Add($item) -or -not $seenFolded.Add($item)) {
            Fail $Reason
        }
    }
    foreach ($item in $Expected) {
        if (-not $seen.Contains($item)) { Fail $Reason }
    }
}

function Assert-OrderedExact($Actual, [string[]]$Expected, [string]$Reason) {
    $actualItems = @($Actual)
    Assert-UniqueExactSet $actualItems $Expected $Reason
    Assert-String ($actualItems -join "`u{001f}") ($Expected -join "`u{001f}") $Reason
}

function Assert-Properties($Value, [string[]]$Expected, [string]$Reason) {
    if ($null -eq $Value -or $Value -is [Array] -or $Value -is [string]) { Fail $Reason }
    Assert-UniqueExactSet @($Value.PSObject.Properties.Name) $Expected $Reason
}

function Assert-NoDuplicateJson([System.Text.Json.JsonElement]$Element, [string]$At) {
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new($ordinal)
        $folded = [Collections.Generic.HashSet[string]]::new($ordinalIgnoreCase)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name) -or -not $folded.Add($property.Name)) {
                Fail "V2_050_JSON_DUPLICATE_OR_CASE_COLLISION:$At"
            }
            Assert-NoDuplicateJson $property.Value "$At/$($property.Name)"
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        $index = 0
        foreach ($item in $Element.EnumerateArray()) {
            Assert-NoDuplicateJson $item "$At/$index"
            $index++
        }
    }
}

function Read-StrictText([string]$Path, [long]$MaximumBytes = 2097152) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Fail 'V2_050_ARTIFACT_MISSING' }
    $bytes = [IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -gt $MaximumBytes) { Fail 'V2_050_ARTIFACT_TOO_LARGE' }
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xef -and
        $bytes[1] -eq 0xbb -and $bytes[2] -eq 0xbf) {
        Fail 'V2_050_UTF8_BOM_FORBIDDEN'
    }
    try { $text = $utf8Strict.GetString($bytes) } catch { Fail 'V2_050_UTF8_INVALID' }
    if ($text.Contains([char]0) -or $text.Contains([char]0xfffd)) { Fail 'V2_050_UTF8_INVALID' }
    return $text
}

function ConvertFrom-StrictJsonText([string]$Text) {
    try {
        $document = [System.Text.Json.JsonDocument]::Parse($Text)
        try { Assert-NoDuplicateJson $document.RootElement '$' } finally { $document.Dispose() }
        return ($Text | ConvertFrom-Json -Depth 100)
    } catch [InvalidOperationException] { throw }
    catch { Fail 'V2_050_JSON_INVALID' }
}

function Read-StrictJson([string]$Path) {
    return ConvertFrom-StrictJsonText (Read-StrictText $Path)
}

function Assert-CanonicalText([string]$Text, [string]$Reason) {
    Assert-True (-not $Text.Contains("`r") -and
        $Text.EndsWith("`n", [StringComparison]::Ordinal)) $Reason
}

function Assert-RelativePath([string]$RelativePath) {
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or
        [IO.Path]::IsPathRooted($RelativePath) -or
        $RelativePath.Contains('\') -or
        $RelativePath -match '(^|/)\.\.(/|$)' -or
        $RelativePath -match '(^|/)\.(/|$)' -or
        $RelativePath -match '//') {
        Fail 'V2_050_PATH_INVALID'
    }
}

function Assert-NoReparsePath([string]$FullPath, [bool]$RequireLeaf = $true) {
    $full = [IO.Path]::GetFullPath($FullPath)
    $prefix = $repositoryRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        Fail 'V2_050_PATH_ESCAPE'
    }
    $relative = [IO.Path]::GetRelativePath($repositoryRoot, $full)
    $cursor = $repositoryRoot
    foreach ($part in ($relative -split '[\\/]')) {
        $cursor = Join-Path $cursor $part
        if (-not (Test-Path -LiteralPath $cursor)) { Fail 'V2_050_PATH_MISSING' }
        $item = Get-Item -LiteralPath $cursor -Force
        if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_050_REPARSE_POINT_FORBIDDEN'
        }
    }
    if ($RequireLeaf -and -not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Fail 'V2_050_PATH_NOT_FILE'
    }
}

function Get-LiveHash([string]$RelativePath) {
    Assert-RelativePath $RelativePath
    $full = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativePath))
    Assert-NoReparsePath $full
    return (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString(
            $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-RecursiveRelativeFiles([string]$RelativeRoot) {
    Assert-RelativePath $RelativeRoot
    $root = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativeRoot))
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        Fail 'V2_050_DIRECTORY_MISSING'
    }
    Assert-NoReparsePath $root $false
    $entries = @(Get-ChildItem -LiteralPath $root -Recurse -Force)
    foreach ($entry in $entries) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_050_REPARSE_POINT_FORBIDDEN'
        }
    }
    return @($entries | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
        [IO.Path]::GetRelativePath($root, $_.FullName).Replace('\', '/')
    })
}

function Assert-RecursiveAllowlist([string]$Root, [string[]]$Expected) {
    Assert-UniqueExactSet (Get-RecursiveRelativeFiles $Root) $Expected 'V2_050_CLOSED_WORLD_DRIFT'
}

function Assert-NamespaceAllowlist([string]$Root, [string]$NamePattern, [string[]]$Expected) {
    $rootPath = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $Root))
    if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) {
        Fail 'V2_050_DIRECTORY_MISSING'
    }
    $entries = @(Get-ChildItem -LiteralPath $rootPath -Recurse -Force)
    foreach ($entry in $entries) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_050_REPARSE_POINT_FORBIDDEN'
        }
    }
    $matches = @($entries | Where-Object {
        -not $_.PSIsContainer -and $_.Name -like $NamePattern
    })
    $relative = @($matches | ForEach-Object {
        [IO.Path]::GetRelativePath($rootPath, $_.FullName).Replace('\', '/')
    })
    Assert-UniqueExactSet $relative $Expected 'V2_050_CLOSED_WORLD_DRIFT'
}

function Get-TreeFingerprint([string]$RelativePath) {
    Assert-RelativePath $RelativePath
    $root = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $RelativePath))
    if (-not (Test-Path -LiteralPath $root)) { Fail 'V2_050_BASELINE_PATH_MISSING' }
    Assert-NoReparsePath $root (-not (Test-Path -LiteralPath $root -PathType Container))
    $entries = if (Test-Path -LiteralPath $root -PathType Leaf) {
        @((Get-Item -LiteralPath $root -Force))
    } else {
        @(Get-ChildItem -LiteralPath $root -Recurse -Force)
    }
    foreach ($entry in $entries) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail 'V2_050_REPARSE_POINT_FORBIDDEN'
        }
    }
    $files = @($entries | Where-Object { -not $_.PSIsContainer })
    $ordered = @($files | Sort-Object -Property FullName -CaseSensitive)
    $lines = @($ordered | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($repositoryRoot, $_.FullName).Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$relative`u{001f}$hash"
    })
    $material = if ($lines.Count -eq 0) { '' } else { ($lines -join "`n") + "`n" }
    return [pscustomobject]@{
        fileCount = $ordered.Count
        sha256 = Get-TextHash $material
    }
}

function Assert-ArtifactList($Items, [string[]]$ExpectedPaths, [string]$Reason) {
    $itemsArray = @($Items)
    Assert-OrderedExact @($itemsArray | ForEach-Object { $_.path }) $ExpectedPaths $Reason
    foreach ($item in $itemsArray) {
        Assert-Properties $item @('path', 'sha256') $Reason
        Assert-True ($item.sha256 -is [string] -and
            $item.sha256 -cmatch '^[0-9a-f]{64}$') $Reason
        Assert-String (Get-LiveHash $item.path) $item.sha256 $Reason
        $text = Read-StrictText (Join-Path $repositoryRoot $item.path)
        Assert-CanonicalText $text 'V2_050_ARTIFACT_TEXT_FORMAT'
    }
}

function Assert-FixtureObject($Fixture) {
    Assert-Properties $Fixture @('schemaVersion', 'fixtureKind', 'recordsPerPage', 'scenarios') `
        'V2_050_FIXTURE_SCHEMA'
    Assert-String $Fixture.schemaVersion '2026-09-07.v2-050.q-med-fnd-01.1' `
        'V2_050_FIXTURE_VALUE'
    Assert-String $Fixture.fixtureKind 'SYNTHETIC_FIXTURE' 'V2_050_FIXTURE_VALUE'
    Assert-Integer $Fixture.recordsPerPage 8 'V2_050_FIXTURE_VALUE'
    $expected = @(
        @('DATA_EXPORT_16', 'DataExportPageStreamer', 16L, 17L, 16L, 128L, 17L),
        @('DATA_EXPORT_256', 'DataExportPageStreamer', 256L, 257L, 256L, 2048L, 257L),
        @('DATA_EXPORT_4096', 'DataExportPageStreamer', 4096L, 4097L, 4096L, 32768L, 4097L),
        @('GRAPHQL_16', 'GraphQlPageStreamer', 16L, 16L, 16L, 128L, 16L),
        @('GRAPHQL_256', 'GraphQlPageStreamer', 256L, 256L, 256L, 2048L, 256L),
        @('GRAPHQL_4096', 'GraphQlPageStreamer', 4096L, 4096L, 4096L, 32768L, 4096L)
    )
    $scenarios = @($Fixture.scenarios)
    Assert-Integer $scenarios.Count 6 'V2_050_FIXTURE_SCENARIO_COUNT'
    for ($index = 0; $index -lt $expected.Count; $index++) {
        $scenario = $scenarios[$index]
        $row = $expected[$index]
        Assert-Properties $scenario @(
            'scenario', 'streamer', 'dataPages', 'expectedFetchedPages',
            'expectedConsumedPages', 'expectedRecords', 'expectedMaxInFlightPages',
            'expectedFinalInFlightPages', 'expectedAcquisitions', 'expectedReleases',
            'expectedMaxRetainedPages', 'expectedFinalRetainedPages') 'V2_050_FIXTURE_SCHEMA'
        Assert-String $scenario.scenario $row[0] 'V2_050_FIXTURE_VALUE'
        Assert-String $scenario.streamer $row[1] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.dataPages $row[2] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedFetchedPages $row[3] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedConsumedPages $row[4] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedRecords $row[5] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedMaxInFlightPages 1 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedFinalInFlightPages 0 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedAcquisitions $row[6] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedReleases $row[6] 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedMaxRetainedPages 0 'V2_050_FIXTURE_VALUE'
        Assert-Integer $scenario.expectedFinalRetainedPages 0 'V2_050_FIXTURE_VALUE'
    }
}

function Assert-ReceiptObject($Receipt) {
    Assert-Properties $Receipt @(
        'schemaVersion', 'fixtureKind', 'diagnosticsPolicy', 'mutantControl', 'runs') `
        'V2_050_RECEIPT_SCHEMA'
    Assert-String $Receipt.schemaVersion '2026-09-07.v2-050.measurement-receipt.1' `
        'V2_050_RECEIPT_VALUE'
    Assert-String $Receipt.fixtureKind 'SYNTHETIC_FIXTURE' 'V2_050_RECEIPT_VALUE'
    Assert-String $Receipt.diagnosticsPolicy 'JVM_HEAP_DIAGNOSTIC_ONLY' `
        'V2_050_RECEIPT_VALUE'
    Assert-String $Receipt.mutantControl 'LEAK_MUTANT_REJECTED' 'V2_050_RECEIPT_VALUE'
    $expected = @(
        @('DataExportPageStreamer', 16L, 17L, 16L, 128L, 17L),
        @('DataExportPageStreamer', 256L, 257L, 256L, 2048L, 257L),
        @('DataExportPageStreamer', 4096L, 4097L, 4096L, 32768L, 4097L),
        @('GraphQlPageStreamer', 16L, 16L, 16L, 128L, 16L),
        @('GraphQlPageStreamer', 256L, 256L, 256L, 2048L, 256L),
        @('GraphQlPageStreamer', 4096L, 4096L, 4096L, 32768L, 4096L)
    )
    $runs = @($Receipt.runs)
    Assert-Integer $runs.Count 6 'V2_050_RECEIPT_RUN_COUNT'
    for ($index = 0; $index -lt $expected.Count; $index++) {
        $run = $runs[$index]
        $row = $expected[$index]
        Assert-Properties $run @(
            'streamer', 'dataPages', 'recordsPerPage', 'evidence', 'diagnostics', 'assessment') `
            'V2_050_RECEIPT_RUN_SCHEMA'
        Assert-String $run.streamer $row[0] 'V2_050_RECEIPT_RUN_VALUE'
        Assert-Integer $run.dataPages $row[1] 'V2_050_RECEIPT_RUN_VALUE'
        Assert-Integer $run.recordsPerPage 8 'V2_050_RECEIPT_RUN_VALUE'
        Assert-Properties $run.evidence @(
            'fetchedPages', 'consumedPages', 'records', 'bytes', 'maxInFlightPages',
            'finalInFlightPages', 'acquisitions', 'releases', 'maxRetainedPages',
            'finalRetainedPages') 'V2_050_RECEIPT_EVIDENCE_SCHEMA'
        Assert-Integer $run.evidence.fetchedPages $row[2] 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.consumedPages $row[3] 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.records $row[4] 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-PositiveInteger $run.evidence.bytes 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.maxInFlightPages 1 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.finalInFlightPages 0 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.acquisitions $row[5] 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.releases $row[5] 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.maxRetainedPages 0 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Integer $run.evidence.finalRetainedPages 0 'V2_050_RECEIPT_EVIDENCE_VALUE'
        Assert-Properties $run.diagnostics @(
            'heapBeforeBytes', 'heapAfterBytes', 'heapPeakBytes', 'durationNanos') `
            'V2_050_RECEIPT_DIAGNOSTICS_SCHEMA'
        foreach ($metric in @('heapBeforeBytes', 'heapAfterBytes', 'heapPeakBytes', 'durationNanos')) {
            Assert-PositiveInteger $run.diagnostics.$metric 'V2_050_RECEIPT_DIAGNOSTICS_VALUE'
        }
        Assert-True ([long]$run.diagnostics.heapPeakBytes -ge [long]$run.diagnostics.heapBeforeBytes -and
            [long]$run.diagnostics.heapPeakBytes -ge [long]$run.diagnostics.heapAfterBytes) `
            'V2_050_RECEIPT_DIAGNOSTICS_VALUE'
        Assert-Properties $run.assessment @('disposition', 'reason') `
            'V2_050_RECEIPT_ASSESSMENT_SCHEMA'
        Assert-String $run.assessment.disposition 'PROVEN_SYNTHETICALLY' `
            'V2_050_RECEIPT_ASSESSMENT_VALUE'
        Assert-String $run.assessment.reason 'MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY' `
            'V2_050_RECEIPT_ASSESSMENT_VALUE'
    }
}

function Assert-NoForbiddenReceiptProperties([string]$Text) {
    $document = [System.Text.Json.JsonDocument]::Parse($Text)
    try {
        $stack = [Collections.Generic.Stack[System.Text.Json.JsonElement]]::new()
        $stack.Push($document.RootElement)
        while ($stack.Count -gt 0) {
            $element = $stack.Pop()
            if ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
                foreach ($property in $element.EnumerateObject()) {
                    if ($property.Name -cmatch '^(?i:id|cursor|url|payload|secret|credential|authorization)$') {
                        Fail 'V2_050_RECEIPT_FORBIDDEN_PROPERTY'
                    }
                    $stack.Push($property.Value)
                }
            } elseif ($element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
                foreach ($item in $element.EnumerateArray()) { $stack.Push($item) }
            }
        }
    } finally {
        $document.Dispose()
    }
    Assert-True ($Text -cnotmatch '(?i)https?://|\.env|bearer\s|password|token') `
        'V2_050_RECEIPT_FORBIDDEN_CONTENT'
}

function Assert-StateTargetShape([string]$StateText) {
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^- \[ \] \*\*V2-050 —')).Count 1 'V2_050_PARENT_MUST_REMAIN_OPEN'
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^- \[[xX]\] \*\*V2-050 —')).Count 0 'V2_050_PARENT_MUST_REMAIN_OPEN'
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^\s+- \[[xX]\] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL')).Count 1 `
        'V2_050_FOUNDATION_CLOSURE_STATE'
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^\s+- \[ \] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL')).Count 0 `
        'V2_050_FOUNDATION_CLOSURE_STATE'
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^- \[ \] \*\*V2-038 —')).Count 1 'V2_038_MUST_REMAIN_OPEN'
    Assert-Integer @([regex]::Matches(
        $StateText, '(?m)^- \[[xX]\] \*\*V2-038 —')).Count 0 'V2_038_MUST_REMAIN_OPEN'
}

function Assert-CheckboxSnapshot(
    [string]$StateText,
    [long]$ExpectedTotal,
    [long]$ExpectedDone,
    [string]$Reason) {
    $checkboxes = @([regex]::Matches($StateText, '(?m)^\s*- \[(?<mark>[ xX])\]'))
    $done = @($checkboxes | Where-Object {
        $_.Groups['mark'].Value -match '[xX]'
    }).Count
    Assert-Integer $checkboxes.Count $ExpectedTotal $Reason
    Assert-Integer $done $ExpectedDone $Reason
    Assert-Integer ($checkboxes.Count - $done) ($ExpectedTotal - $ExpectedDone) $Reason
}

function Assert-SidecarText([string]$Text, [string]$ExpectedHash) {
    Assert-String $Text "$ExpectedHash  manifesto.json`n" 'V2_050_MANIFEST_SIDECAR'
}

function Replace-First(
    [string]$Text,
    [string]$OldValue,
    [string]$NewValue) {
    $index = $Text.IndexOf($OldValue, [StringComparison]::Ordinal)
    if ($index -lt 0) { Fail 'V2_050_ADVERSARIAL_SOURCE_PATTERN_MISSING' }
    return $Text.Substring(0, $index) + $NewValue + $Text.Substring($index + $OldValue.Length)
}

function Assert-Rejected(
    [scriptblock]$Action,
    [string]$Case,
    [string]$ExpectedReason) {
    $rejected = $false
    $actualReason = ''
    try { & $Action } catch {
        $rejected = $true
        $actualReason = $_.Exception.Message
    }
    if (-not $rejected) { Fail "V2_050_ADVERSARIAL_ACCEPTED:$Case" }
    Assert-String $actualReason $ExpectedReason "V2_050_ADVERSARIAL_WRONG_REASON:$Case"
    $script:adversarialCases++
}

function Invoke-AdversarialSuite(
    [string]$ManifestText,
    [string]$FixtureText,
    [string]$ReceiptText,
    [string]$ManifestHash) {
    $temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("v2-050-validator-" + [guid]::NewGuid())
    $null = New-Item -ItemType Directory -Path $temporaryRoot
    try {
        $forged = Join-Path $temporaryRoot 'forged.json'

        [IO.File]::WriteAllText($forged,
            (Replace-First $ManifestText '"schemaVersion":' `
                '"schemaVersion":"duplicate","schemaVersion":'), $utf8NoBom)
        Assert-Rejected { Read-StrictJson $forged } 'MANIFEST_DUPLICATE' `
            'V2_050_JSON_DUPLICATE_OR_CASE_COLLISION:$'

        [IO.File]::WriteAllText($forged,
            (Replace-First $ManifestText '"schemaVersion"' '"SchemaVersion"'), $utf8NoBom)
        Assert-Rejected {
            $value = Read-StrictJson $forged
            Assert-Properties $value @(
                'schemaVersion','task','outcome','foundation','fixture','receipt','governanceArtifacts',
                'testArtifacts','scopeBaselines','openGates','prohibitedScope') 'FORGED_MANIFEST'
        } 'MANIFEST_CASING' 'FORGED_MANIFEST'

        [IO.File]::WriteAllText($forged,
            (Replace-First $ManifestText '"block": 50' '"block": "50"'), $utf8NoBom)
        Assert-Rejected {
            $value = Read-StrictJson $forged
            Assert-Integer $value.task.block 50 'FORGED_MANIFEST'
        } 'MANIFEST_TYPE' 'FORGED_MANIFEST'

        [IO.File]::WriteAllText($forged,
            (Replace-First $ManifestText '{' '{"rogue":true,'), $utf8NoBom)
        Assert-Rejected {
            $value = Read-StrictJson $forged
            Assert-Properties $value @(
                'schemaVersion','task','outcome','foundation','fixture','receipt','governanceArtifacts',
                'testArtifacts','scopeBaselines','openGates','prohibitedScope') 'FORGED_MANIFEST'
        } 'MANIFEST_EXTRA' 'FORGED_MANIFEST'

        Assert-Rejected { Assert-SidecarText ("0" * 64 + "  manifesto.json`n") $ManifestHash } `
            'SIDECAR_HASH' 'V2_050_MANIFEST_SIDECAR'
        Assert-Rejected { Assert-SidecarText "$ManifestHash manifesto.json`n" $ManifestHash } `
            'SIDECAR_FORMAT' 'V2_050_MANIFEST_SIDECAR'

        [IO.File]::WriteAllText($forged,
            (Replace-First $FixtureText '"fixtureKind"' '"FixtureKind"'), $utf8NoBom)
        Assert-Rejected { Assert-FixtureObject (Read-StrictJson $forged) } `
            'FIXTURE_CASING' 'V2_050_FIXTURE_SCHEMA'
        [IO.File]::WriteAllText($forged,
            (Replace-First $FixtureText '"recordsPerPage": 8' '"recordsPerPage": "8"'), $utf8NoBom)
        Assert-Rejected { Assert-FixtureObject (Read-StrictJson $forged) } `
            'FIXTURE_TYPE' 'V2_050_FIXTURE_VALUE'
        [IO.File]::WriteAllText($forged,
            (Replace-First $FixtureText '"dataPages": 16' '"dataPages": 15'), $utf8NoBom)
        Assert-Rejected { Assert-FixtureObject (Read-StrictJson $forged) } `
            'FIXTURE_SCALE' 'V2_050_FIXTURE_VALUE'
        [IO.File]::WriteAllText($forged,
            (Replace-First $FixtureText '{' '{"rogue":true,'), $utf8NoBom)
        Assert-Rejected { Assert-FixtureObject (Read-StrictJson $forged) } `
            'FIXTURE_EXTRA' 'V2_050_FIXTURE_SCHEMA'

        [IO.File]::WriteAllText($forged,
            (Replace-First $ReceiptText '"streamer"' '"Streamer"'), $utf8NoBom)
        Assert-Rejected { Assert-ReceiptObject (Read-StrictJson $forged) } `
            'RECEIPT_CASING' 'V2_050_RECEIPT_RUN_SCHEMA'
        [IO.File]::WriteAllText($forged,
            (Replace-First $ReceiptText '"recordsPerPage" : 8' '"recordsPerPage" : "8"'), $utf8NoBom)
        Assert-Rejected { Assert-ReceiptObject (Read-StrictJson $forged) } `
            'RECEIPT_TYPE' 'V2_050_RECEIPT_RUN_VALUE'
        [IO.File]::WriteAllText($forged,
            (Replace-First $ReceiptText '"maxRetainedPages" : 0' '"maxRetainedPages" : 1'), $utf8NoBom)
        Assert-Rejected { Assert-ReceiptObject (Read-StrictJson $forged) } `
            'RECEIPT_RETENTION' 'V2_050_RECEIPT_EVIDENCE_VALUE'
        [IO.File]::WriteAllText($forged,
            (Replace-First $ReceiptText '{' '{"payload":"forged",'), $utf8NoBom)
        Assert-Rejected { Assert-ReceiptObject (Read-StrictJson $forged) } `
            'RECEIPT_EXTRA' 'V2_050_RECEIPT_SCHEMA'

        $validState = @'
- [ ] **V2-050 — synthetic parent.**
  - [x] **V2-050/FUNDACAO_MEDICAO_LOCAL — synthetic child.**
- [ ] **V2-038 — synthetic qualification.**
'@ -replace "`r", ''
        Assert-StateTargetShape $validState
        Assert-CheckboxSnapshot $validState 3 1 'FORGED_STATE_COUNTS'
        Assert-Rejected {
            Assert-StateTargetShape ($validState.Replace('- [ ] **V2-050 —', '- [x] **V2-050 —'))
        } 'STATE_PARENT' 'V2_050_PARENT_MUST_REMAIN_OPEN'
        Assert-Rejected {
            Assert-StateTargetShape ($validState.Replace(
                '  - [x] **V2-050/FUNDACAO_MEDICAO_LOCAL',
                '  - [ ] **V2-050/FUNDACAO_MEDICAO_LOCAL'))
        } 'STATE_FOUNDATION' 'V2_050_FOUNDATION_CLOSURE_STATE'
        Assert-Rejected {
            Assert-StateTargetShape ($validState.Replace('- [ ] **V2-038 —', '- [x] **V2-038 —'))
        } 'STATE_V2_038' 'V2_038_MUST_REMAIN_OPEN'
        Assert-Rejected {
            Assert-CheckboxSnapshot ($validState + "`n- [ ] **ROGUE — forged.**`n") 3 1 `
                'FORGED_STATE_COUNTS'
        } 'STATE_COUNTS' 'FORGED_STATE_COUNTS'
    } finally {
        if (Test-Path -LiteralPath $temporaryRoot -PathType Container) {
            Get-ChildItem -LiteralPath $temporaryRoot -File -Force | ForEach-Object {
                Remove-Item -LiteralPath $_.FullName -Force
            }
            Remove-Item -LiteralPath $temporaryRoot -Force
        }
    }
}

try {
    $manifestText = Read-StrictText $manifestPath
    Assert-CanonicalText $manifestText 'V2_050_MANIFEST_TEXT_FORMAT'
    $manifest = ConvertFrom-StrictJsonText $manifestText
    Assert-Properties $manifest @(
        'schemaVersion', 'task', 'outcome', 'foundation', 'fixture', 'receipt',
        'governanceArtifacts', 'testArtifacts', 'scopeBaselines', 'openGates',
        'prohibitedScope') 'V2_050_MANIFEST_SCHEMA'
    Assert-String $manifest.schemaVersion '2026-09-07.v2-050.q-med-fnd-01.1' `
        'V2_050_MANIFEST_SCHEMA'
    Assert-Properties $manifest.task @('route', 'block', 'taskId') 'V2_050_MANIFEST_TASK'
    Assert-String $manifest.task.route 'Q-MED-FND-01' 'V2_050_MANIFEST_TASK'
    Assert-Integer $manifest.task.block 50 'V2_050_MANIFEST_TASK'
    Assert-String $manifest.task.taskId 'V2-050/FUNDACAO_MEDICAO_LOCAL' 'V2_050_MANIFEST_TASK'
    Assert-String $manifest.outcome 'FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED' `
        'V2_050_MANIFEST_OUTCOME'

    Assert-Properties $manifest.foundation @(
        'model', 'executionMode', 'fixtureKind', 'scales', 'recordsPerPage', 'streamers',
        'positiveDisposition', 'positiveReason', 'mutantControl', 'mutantReason',
        'diagnosticsPolicy', 'graphQlCycleDetectorBytes', 'entityScaleGatePassedCount',
        'networkAccess', 'databaseAccess', 'secretAccess', 'sqlPlanExecution', 'runtimeWiring') `
        'V2_050_FOUNDATION_SCHEMA'
    Assert-String $manifest.foundation.model 'GPT_5_6_SOL_ULTRA' 'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.executionMode 'LOCAL_OFFLINE_TEST_ONLY' `
        'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.fixtureKind 'SYNTHETIC_FIXTURE' 'V2_050_FOUNDATION_VALUE'
    $scales = @($manifest.foundation.scales)
    Assert-Integer $scales.Count 3 'V2_050_FOUNDATION_VALUE'
    Assert-Integer $scales[0] 16 'V2_050_FOUNDATION_VALUE'
    Assert-Integer $scales[1] 256 'V2_050_FOUNDATION_VALUE'
    Assert-Integer $scales[2] 4096 'V2_050_FOUNDATION_VALUE'
    Assert-Integer $manifest.foundation.recordsPerPage 8 'V2_050_FOUNDATION_VALUE'
    Assert-OrderedExact $manifest.foundation.streamers `
        @('DataExportPageStreamer', 'GraphQlPageStreamer') 'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.positiveDisposition 'PROVEN_SYNTHETICALLY' `
        'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.positiveReason `
        'MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY' 'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.mutantControl 'LEAK_MUTANT_REJECTED' `
        'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.mutantReason 'EXECUTION_WIDE_PAGE_RETENTION_DETECTED' `
        'V2_050_FOUNDATION_VALUE'
    Assert-String $manifest.foundation.diagnosticsPolicy 'JVM_HEAP_DIAGNOSTIC_ONLY' `
        'V2_050_FOUNDATION_VALUE'
    Assert-Integer $manifest.foundation.graphQlCycleDetectorBytes 1048576 `
        'V2_050_FOUNDATION_VALUE'
    Assert-Integer $manifest.foundation.entityScaleGatePassedCount 0 'V2_050_FOUNDATION_VALUE'
    foreach ($property in @(
        'networkAccess','databaseAccess','secretAccess','sqlPlanExecution','runtimeWiring')) {
        Assert-Boolean $manifest.foundation.$property $false 'V2_050_FOUNDATION_VALUE'
    }

    Assert-Properties $manifest.fixture @(
        'resourcePath','catalogPath','sha256','scenarioCount') 'V2_050_MANIFEST_FIXTURE'
    Assert-String $manifest.fixture.resourcePath `
        'src/test/resources/contracts/v2-050/measurement-foundation.synthetic.json' `
        'V2_050_MANIFEST_FIXTURE'
    Assert-String $manifest.fixture.catalogPath `
        'docs/catalogos/medicao-v2-050/fixtures/measurement-foundation.synthetic.json' `
        'V2_050_MANIFEST_FIXTURE'
    Assert-True ($manifest.fixture.sha256 -is [string] -and
        $manifest.fixture.sha256 -cmatch '^[0-9a-f]{64}$') 'V2_050_MANIFEST_FIXTURE'
    Assert-Integer $manifest.fixture.scenarioCount 6 'V2_050_MANIFEST_FIXTURE'
    $resourceFixtureText = Read-StrictText (Join-Path $repositoryRoot $manifest.fixture.resourcePath)
    $catalogFixtureText = Read-StrictText (Join-Path $repositoryRoot $manifest.fixture.catalogPath)
    Assert-CanonicalText $resourceFixtureText 'V2_050_FIXTURE_TEXT_FORMAT'
    Assert-String $catalogFixtureText $resourceFixtureText 'V2_050_FIXTURE_MIRROR_DRIFT'
    Assert-String (Get-LiveHash $manifest.fixture.resourcePath) $manifest.fixture.sha256 `
        'V2_050_FIXTURE_HASH'
    Assert-String (Get-LiveHash $manifest.fixture.catalogPath) $manifest.fixture.sha256 `
        'V2_050_FIXTURE_HASH'
    $fixture = ConvertFrom-StrictJsonText $resourceFixtureText
    Assert-FixtureObject $fixture

    Assert-Properties $manifest.receipt @(
        'directory','path','schemaVersion','encoding','lineEndings','atomicPublication',
        'sameObjectDeterministic','crossRunHashStable','runCount') 'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.directory 'target/v2-050-measurement' `
        'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.path $receiptRelativePath 'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.schemaVersion `
        '2026-09-07.v2-050.measurement-receipt.1' 'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.encoding 'UTF-8_NO_BOM_STRICT' 'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.lineEndings 'LF' 'V2_050_MANIFEST_RECEIPT'
    Assert-String $manifest.receipt.atomicPublication 'TEMP_FILE_ATOMIC_MOVE_REPLACE' `
        'V2_050_MANIFEST_RECEIPT'
    Assert-Boolean $manifest.receipt.sameObjectDeterministic $true 'V2_050_MANIFEST_RECEIPT'
    Assert-Boolean $manifest.receipt.crossRunHashStable $false 'V2_050_MANIFEST_RECEIPT'
    Assert-Integer $manifest.receipt.runCount 6 'V2_050_MANIFEST_RECEIPT'

    $expectedGovernancePaths = @(
        'docs/catalogos/medicao-v2-050/README.md',
        'docs/adr/0031-fundacao-medicao-local-multiescala.md',
        'docs/runbooks/v2-050-fundacao-medicao-local-sol.md',
        'scripts/validation/Invoke-V2050MeasurementFoundation.ps1',
        'scripts/validation/Test-V2050MeasurementFoundation.ps1'
    )
    $expectedTestPaths = @(
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/ExecutionWidePageRetentionMutant.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/ExecutionWidePageRetentionMutantTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/ManagedPageGauge.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/ManagedPageGaugeTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementAssessment.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementDiagnostics.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementDiagnosticsTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementEvaluator.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementEvaluatorTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementEvidence.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementFoundationReceiptTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementPlan.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementPlanTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementReceiptWriter.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementReceiptWriterTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementRun.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementRunTest.java',
        'src/test/java/br/com/esl/etl/v2/contratos/medicao/MeasurementStreamer.java',
        'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageStreamerMultiscaleMeasurementTest.java',
        'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlPageStreamerMultiscaleMeasurementTest.java'
    )
    Assert-ArtifactList $manifest.governanceArtifacts $expectedGovernancePaths `
        'V2_050_GOVERNANCE_DRIFT'
    Assert-ArtifactList $manifest.testArtifacts $expectedTestPaths 'V2_050_TEST_DRIFT'

    $expectedBaselines = @(
        @('src/main', 'TREE', 369L, 'a9864a473eb07080b303c095b13afda2690b8893fea40cc3ac51ac068f4ecb26'),
        @('database', 'TREE', 98L, '4d813607f302c5ef1a73d9dc7c3d35ae3903d457d63b2b3884ac49aeb263ea4c'),
        @('.github/workflows', 'TREE', 2L, '255e01fe6580534fa104fb5312376e0bb178dfb5401ebce6313cb92d8032e9b0'),
        @('pom.xml', 'FILE', 1L, 'b34dbf97d2ca1f572981b9d6067b274df3e92f47e5bbf53d83526ba7c2f2ed86'),
        @('src/main/java/br/com/esl/etl/v2/bootstrap/Main.java', 'FILE', 1L, 'f9ba1e6e223c0b5f0448eaa59f60e1eca2204c99063b48c2a256198e5e241ab5')
    )
    $baselines = @($manifest.scopeBaselines)
    Assert-Integer $baselines.Count $expectedBaselines.Count 'V2_050_BASELINE_COUNT'
    for ($index = 0; $index -lt $expectedBaselines.Count; $index++) {
        $baseline = $baselines[$index]
        $row = $expectedBaselines[$index]
        Assert-Properties $baseline @('path','kind','fileCount','sha256') `
            'V2_050_BASELINE_SCHEMA'
        Assert-String $baseline.path $row[0] 'V2_050_BASELINE_VALUE'
        Assert-String $baseline.kind $row[1] 'V2_050_BASELINE_VALUE'
        Assert-Integer $baseline.fileCount $row[2] 'V2_050_BASELINE_VALUE'
        Assert-String $baseline.sha256 $row[3] 'V2_050_BASELINE_VALUE'
        if ($baseline.kind -ceq 'FILE') {
            Assert-Integer $baseline.fileCount 1 'V2_050_PROHIBITED_SCOPE_DRIFT'
            Assert-String (Get-LiveHash $baseline.path) $baseline.sha256 `
                'V2_050_PROHIBITED_SCOPE_DRIFT'
        } else {
            $live = Get-TreeFingerprint $baseline.path
            Assert-Integer $live.fileCount $baseline.fileCount 'V2_050_PROHIBITED_SCOPE_DRIFT'
            Assert-String $live.sha256 $baseline.sha256 'V2_050_PROHIBITED_SCOPE_DRIFT'
        }
    }

    Assert-OrderedExact $manifest.openGates @(
        'V2-050', 'ENTITY_V2_050_GATE=OPEN', 'V2-038', 'ENTITY_ROUTES', 'OUTPUT_ROUTES') `
        'V2_050_OPEN_GATES'
    Assert-OrderedExact $manifest.prohibitedScope @(
        'NETWORK','SOURCE','API','ENV_SECRET','DATABASE','SQLCMD','FLYWAY','SQL_PLAN_REAL',
        'DDL','DML','MIGRATION','RUNTIME','MAIN','COMPOSITION_ROOT','POM','WORKFLOW','DEPLOY',
        'PUBLICATION','SWEEP','PRUNE','CUTOVER','PRODUCTIVE_HEAP_CLAIM','PRODUCTIVE_SLO_CLAIM',
        'PRODUCTIVE_SCALE_CLAIM') 'V2_050_PROHIBITED_SCOPE'

    Assert-RecursiveAllowlist 'docs/catalogos/medicao-v2-050' @(
        'README.md','fixtures/measurement-foundation.synthetic.json','manifesto.json','manifesto.sha256')
    Assert-RecursiveAllowlist 'src/test/resources/contracts/v2-050' @(
        'measurement-foundation.synthetic.json')
    Assert-RecursiveAllowlist 'src/test/java/br/com/esl/etl/v2/contratos/medicao' @(
        $expectedTestPaths | Where-Object { $_ -like '*/contratos/medicao/*' } |
            ForEach-Object { Split-Path -Leaf $_ })
    Assert-NamespaceAllowlist 'src/test/java' '*PageStreamerMultiscaleMeasurementTest.java' @(
        'br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageStreamerMultiscaleMeasurementTest.java',
        'br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlPageStreamerMultiscaleMeasurementTest.java')
    Assert-NamespaceAllowlist 'scripts/validation' '*V2050*' @(
        'Invoke-V2050MeasurementFoundation.ps1','Test-V2050MeasurementFoundation.ps1')
    Assert-NamespaceAllowlist 'docs/adr' '0031-*' @(
        '0031-fundacao-medicao-local-multiescala.md')
    Assert-NamespaceAllowlist 'docs/runbooks' 'v2-050-*' @(
        'v2-050-fundacao-medicao-local-sol.md')

    $manifestHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $sidecarPath = Join-Path $repositoryRoot 'docs/catalogos/medicao-v2-050/manifesto.sha256'
    $sidecarText = Read-StrictText $sidecarPath
    Assert-SidecarText $sidecarText $manifestHash

    Assert-NoReparsePath $receiptPath
    $receiptText = Read-StrictText $receiptPath 4194304
    Assert-CanonicalText $receiptText 'V2_050_RECEIPT_TEXT_FORMAT'
    Assert-NoForbiddenReceiptProperties $receiptText
    $receipt = ConvertFrom-StrictJsonText $receiptText
    Assert-ReceiptObject $receipt
    Assert-RecursiveAllowlist 'target/v2-050-measurement' @(
        'measurement-foundation-receipt.json')

    $allGovernedText = @($manifestText, $resourceFixtureText, $receiptText)
    $allGovernedText += @($manifest.governanceArtifacts | ForEach-Object {
        Read-StrictText (Join-Path $repositoryRoot $_.path)
    })
    $allGovernedText += @($manifest.testArtifacts | ForEach-Object {
        Read-StrictText (Join-Path $repositoryRoot $_.path)
    })
    $joined = $allGovernedText -join "`n"
    foreach ($literal in @(
        'SYNTHETIC_FIXTURE','DataExportPageStreamer','GraphQlPageStreamer',
        'MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY','LEAK_MUTANT_REJECTED',
        'JVM_HEAP_DIAGNOSTIC_ONLY','ENTITY_HEAP_PLATEAU_NOT_PROVEN',
        'ENTITY_SQL_PLAN_NOT_EXECUTED','ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN',
        'ENTITY_V2_050_GATE=OPEN','EXECUTION_WIDE_PAGE_RETENTION_DETECTED')) {
        Assert-True $joined.Contains($literal, [StringComparison]::Ordinal) `
            "V2_050_REQUIRED_LITERAL_MISSING:$literal"
    }

    $dataExportTest = Read-StrictText (Join-Path $repositoryRoot `
        'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageStreamerMultiscaleMeasurementTest.java')
    $graphQlTest = Read-StrictText (Join-Path $repositoryRoot `
        'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlPageStreamerMultiscaleMeasurementTest.java')
    foreach ($source in @($dataExportTest, $graphQlTest)) {
        Assert-True ($source -cmatch '@ValueSource\(ints = \{16, 256, 4096\}\)') `
            'V2_050_SCALE_TEST_MISSING'
        Assert-True ($source -cmatch 'RECORDS_PER_PAGE\s*=\s*8') `
            'V2_050_RECORD_COUNT_TEST_MISSING'
        Assert-True ($source.Contains('new ExecutionWidePageRetentionMutant',
            [StringComparison]::Ordinal)) 'V2_050_MUTANT_TEST_MISSING'
    }
    Assert-True ($dataExportTest.Contains('new DataExportPageStreamer(',
        [StringComparison]::Ordinal)) 'V2_050_REAL_DATA_EXPORT_STREAMER_MISSING'
    Assert-True ($graphQlTest.Contains('new GraphQlPageStreamer(',
        [StringComparison]::Ordinal)) 'V2_050_REAL_GRAPHQL_STREAMER_MISSING'
    Assert-True ($graphQlTest.Contains('ONE_MEBIBYTE', [StringComparison]::Ordinal) -and
        $graphQlTest.Contains('assertSame(initialWords, finalWords)', [StringComparison]::Ordinal)) `
        'V2_050_GRAPHQL_FIXED_BLOOM_PROOF_MISSING'
    Assert-True ($dataExportTest -cnotmatch 'List\s*<\s*(?:List|Map|Set)\s*<') `
        'V2_050_PREBUILT_EXECUTION_UNIVERSE'
    Assert-True ($graphQlTest -cnotmatch '(?:List|Map|Set)\s*<\s*(?:List|Map|Set)\s*<') `
        'V2_050_PREBUILT_EXECUTION_UNIVERSE'

    $mainSource = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'src/main/java') `
        -Recurse -File -Filter '*.java' | ForEach-Object { Read-StrictText $_.FullName 2097152 }
    Assert-True (($mainSource -join "`n") -cnotmatch
        'br\.com\.esl\.etl\.v2\.contratos\.medicao|MeasurementReceiptWriter|ManagedPageGauge') `
        'V2_050_RUNTIME_WIRING_FORBIDDEN'

    Invoke-AdversarialSuite $manifestText $resourceFixtureText $receiptText $manifestHash
    Assert-Integer $script:adversarialCases 18 'V2_050_ADVERSARIAL_COUNT'

    if (-not $ArtifactsOnly) {
        $states = Read-StrictText (Join-Path $repositoryRoot 'STATES.md') 4194304
        $trail = Read-StrictText `
            (Join-Path $repositoryRoot 'docs/runbooks/trilha-de-chats-gpt-5-6.md') 4194304
        Assert-StateTargetShape $states
        Assert-CheckboxSnapshot $states 107 53 'V2_050_CLOSURE_COUNTS'
        Assert-True $states.Contains(
            'FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED',
            [StringComparison]::Ordinal) 'V2_050_CLOSURE_STATE'
        Assert-True $states.Contains('ENTITY_HEAP_PLATEAU_NOT_PROVEN',
            [StringComparison]::Ordinal) 'V2_050_CLOSURE_STATE'
        Assert-True $states.Contains('ENTITY_SQL_PLAN_NOT_EXECUTED',
            [StringComparison]::Ordinal) 'V2_050_CLOSURE_STATE'
        Assert-True $states.Contains('ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN',
            [StringComparison]::Ordinal) 'V2_050_CLOSURE_STATE'
        Assert-True $states.Contains('ENTITY_V2_050_GATE=OPEN',
            [StringComparison]::Ordinal) 'V2_050_CLOSURE_STATE'

        $closedTrailPattern = '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-MED-FND-01 \| BLOCO=50 \| TAREFA=V2-050/FUNDACAO_MEDICAO_LOCAL \| FATIA=FUNDACAO_MEDICAO_LOCAL_MULTIESCALA_TEST_ONLY \| .* \| RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED \| EVIDENCIA=STATES\.md$'
        Assert-Integer @([regex]::Matches($trail, $closedTrailPattern)).Count 1 `
            'V2_050_CLOSURE_TRAIL'
        Assert-Integer @([regex]::Matches(
            $trail, '(?m)^- \[ \] STATUS=AGORA \|')).Count 0 'V2_050_CLOSURE_TRAIL'
        Assert-Integer @([regex]::Matches(
            $trail, '(?m)^- \[ \] STATUS=CANDIDATO \| ROTA=Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-05 \| TAREFA=V2-050 \|')).Count 10 `
            'V2_050_ENTITY_ROUTES_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches(
            $trail, '(?m)^- \[x\].*\| ROTA=Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-05 \|')).Count 0 `
            'V2_050_ENTITY_ROUTES_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches(
            $trail, '(?m)^- \[ \] STATUS=(?:CANDIDATO|CONDICIONAL) \| ROTA=O-(?:MAT0[1-5]|VW(?:0[1-9]|1[0-9]))-02 \| TAREFA=V2-050 \|')).Count 24 `
            'V2_050_OUTPUT_ROUTES_MUST_REMAIN_OPEN'
        Assert-Integer @([regex]::Matches(
            $trail, '(?m)^- \[x\].*\| ROTA=O-(?:MAT0[1-5]|VW(?:0[1-9]|1[0-9]))-02 \|')).Count 0 `
            'V2_050_OUTPUT_ROUTES_MUST_REMAIN_OPEN'
        Assert-True ($trail.Contains('nenhuma rota elegível para STATUS=AGORA',
            [StringComparison]::Ordinal) -or
            $trail.Contains('zero STATUS=AGORA', [StringComparison]::Ordinal)) `
            'V2_050_NO_ELIGIBLE_SUCCESSOR_MARKER'
    }

    $mode = if ($ArtifactsOnly) { 'ARTIFACTS_ONLY' } else { 'FULL' }
    if ($SelfTest) { $mode = "$mode+SELF_TEST" }
    [Console]::Out.WriteLine(
        "V2_050_MEASUREMENT_FOUNDATION status=PASS mode=$mode scenarios=6 scales=16,256,4096 streamers=2 recordsPerPage=8 adversarial=18 entityScaleGatesPassed=0")
    exit 0
} catch {
    $reason = $_.Exception.Message
    if ([string]::IsNullOrWhiteSpace($reason)) {
        $reason = 'V2_050_MEASUREMENT_FOUNDATION_UNKNOWN_FAILURE'
    }
    [Console]::Out.WriteLine("V2_050_MEASUREMENT_FOUNDATION status=FAIL reason=$reason")
    exit 1
}

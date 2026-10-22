#Requires -Version 7.5
[CmdletBinding()]
param(
    [string]$RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')),
    [string]$ManifestPath = 'docs/continuidade/current-continuity.json',
    [switch]$AsMap
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
$documentRoot = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([char]92, [char]47)
$strictUtf8 = [Text.UTF8Encoding]::new($false, $true)

function Read-CurrentFile([string]$RelativePath, [long]$Maximum = 2097152) {
    if ($RelativePath -cnotmatch '^[A-Za-z0-9_./-]+$' -or
        $RelativePath.StartsWith('/') -or
        @($RelativePath.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -ne 0) {
        throw 'CURRENT_CONTINUITY_PATH'
    }
    $file = [IO.FileInfo]::new((Join-Path $documentRoot $RelativePath))
    if (!$file.Exists -or $file.Length -lt 1 -or $file.Length -gt $Maximum) {
        throw 'CURRENT_CONTINUITY_FILE_BOUND'
    }
    $node = $file
    while ($node.FullName -cne $documentRoot) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'CURRENT_CONTINUITY_REPARSE'
        }
        $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
        if ($null -eq $node) { throw 'CURRENT_CONTINUITY_PATH' }
    }
    if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'CURRENT_CONTINUITY_REPARSE'
    }
    $stream = [IO.File]::Open($file.FullName, [IO.FileMode]::Open,
        [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        if ($stream.Length -lt 1 -or $stream.Length -gt $Maximum) {
            throw 'CURRENT_CONTINUITY_FILE_BOUND'
        }
        $bytes = [byte[]]::new([int]$stream.Length)
        $stream.ReadExactly($bytes)
        if ($stream.ReadByte() -ne -1) { throw 'CURRENT_CONTINUITY_FILE_BOUND' }
        [void]$strictUtf8.GetString($bytes)
        return ,$bytes
    } finally { $stream.Dispose() }
}

function Assert-FalseFlag($Object, [string]$Name) {
    if ($Object[$Name] -isnot [bool] -or $Object[$Name]) {
        throw 'CURRENT_CONTINUITY_NO_AUTHORITY'
    }
}

$manifest = Read-QualificationJsonBytes (Read-CurrentFile $ManifestPath 65536) 65536
Assert-QualificationFields $manifest @(
    'schema', 'request', 'purpose', 'authorizesPhysicalEffects',
    'grantsHistoricalAcceptance', 'changesCanonicalCheckboxes',
    'checkpoint', 'documents', 'historical'
)
if ($manifest.schema -cne 'V2_CURRENT_DOCUMENT_CONTINUITY_V1' -or
    ($manifest.request -cnotmatch '^HERMES_[a-f0-9]{12}$' -and
        $manifest.request -cne 'USER_20261016_CONTINUAR_V2') -or
    $manifest.purpose -cne 'CURRENT_DOCUMENTS_WITH_EXPLICIT_HISTORICAL_EVIDENCE_STATE') {
    throw 'CURRENT_CONTINUITY_SCOPE'
}
foreach ($name in @('authorizesPhysicalEffects', 'grantsHistoricalAcceptance', 'changesCanonicalCheckboxes')) {
    Assert-FalseFlag $manifest $name
}
if ($manifest.checkpoint -isnot [string] -or
    $manifest.checkpoint -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$') {
    throw 'CURRENT_CONTINUITY_CHECKPOINT'
}

$required = @(
    'AGENTS.md', 'STATES.md', 'TRILHA_CONCLUSAO_POR_MODELO.md',
    'docs/continuidade/RETOMADA.md', 'docs/runbooks/continuidade-agentes.md',
    'scripts/validation/Test-CurrentContinuity.ps1', $manifest.checkpoint
)
if ($manifest.documents -isnot [array] -or $manifest.documents.Count -ne $required.Count) {
    throw 'CURRENT_CONTINUITY_DOCUMENT_SET'
}
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $manifest.documents) {
    Assert-QualificationFields $entry @('path', 'bytes', 'sha256')
    if ($entry.path -cnotin $required -or !$seen.Add($entry.path)) {
        throw 'CURRENT_CONTINUITY_DOCUMENT_SET'
    }
    if (($entry.bytes -isnot [int] -and $entry.bytes -isnot [long]) -or
        $entry.bytes -lt 1 -or $entry.bytes -gt 2097152 -or
        $entry.sha256 -cnotmatch '^[a-f0-9]{64}$') {
        throw 'CURRENT_CONTINUITY_DOCUMENT_PIN'
    }
    $bytes = Read-CurrentFile $entry.path
    if ($bytes.Length -ne $entry.bytes -or
        (Get-QualificationByteHash $bytes) -cne $entry.sha256) {
        throw 'CURRENT_CONTINUITY_DOCUMENT_PIN'
    }
}

$historical = $manifest.historical
Assert-QualificationFields $historical @('manifest', 'closedReceipt', 'expectedReceiptState')
foreach ($item in @($historical.manifest, $historical.closedReceipt)) {
    Assert-QualificationFields $item @('path', 'sha256')
}
if ($historical.manifest.path -cne 'docs/continuidade/tres-etapas/handoff/manifesto.json' -or
    $historical.manifest.sha256 -cne 'b7efe60fb2997be5e5e4171e22f829c4235d73e334fee938f55a72aee85bb528' -or
    $historical.closedReceipt.path -cne 'target/tres-etapas-20260922-01/closed-receipt.json' -or
    $historical.closedReceipt.sha256 -cne 'd9ea24973b83430db76093b096fc449da29206216246452ae4e49ca163e2f465') {
    throw 'CURRENT_CONTINUITY_HISTORY_REFERENCE'
}
$oldBytes = Read-CurrentFile $historical.manifest.path 65536
if ((Get-QualificationByteHash $oldBytes) -cne $historical.manifest.sha256) {
    throw 'CURRENT_CONTINUITY_HISTORY_PIN'
}
$old = Read-QualificationJsonBytes $oldBytes 65536
if ($old.closedReceipt.path -cne $historical.closedReceipt.path -or
    $old.closedReceipt.sha256 -cne $historical.closedReceipt.sha256) {
    throw 'CURRENT_CONTINUITY_HISTORY_REFERENCE'
}
$receiptExists = Test-Path -LiteralPath (Join-Path $documentRoot $historical.closedReceipt.path)
if (!$receiptExists) {
    if ($historical.expectedReceiptState -cne 'MISSING_NOT_RECONSTRUCTED') {
        throw 'CURRENT_CONTINUITY_HISTORY_STATE'
    }
    $historyStatus = 'MISSING_NOT_RECONSTRUCTED'
} else {
    if ($historical.expectedReceiptState -cne 'ORIGINAL_RECEIPT_PRESENT') {
        throw 'CURRENT_CONTINUITY_HISTORY_STATE'
    }
    $receiptBytes = Read-CurrentFile $historical.closedReceipt.path 65536
    if ((Get-QualificationByteHash $receiptBytes) -cne $historical.closedReceipt.sha256) {
        throw 'CURRENT_CONTINUITY_HISTORY_PIN'
    }
    $historyStatus = 'ORIGINAL_RECEIPT_PRESENT_FULL_HISTORICAL_CHAIN_NOT_REVALIDATED'
}

# This validator proves current document integrity, never a historical execution
# or permission to run an external operation. The old validator remains strict.
$result = [ordered]@{
    status = 'CURRENT_DOCUMENTS_VERIFIED_HISTORY_STATE_EXPLICIT'
    currentDocumentsValid = $true
    documentsVerified = $required.Count
    historicalReceiptState = $historyStatus
    historicalClosureAccepted = $false
    fullHistoricalChainRevalidated = $false
    authorizesPhysicalEffects = $false
    changesCanonicalCheckboxes = $false
    request = $manifest.request
}
if ($AsMap) { return $result }
$result | ConvertTo-Json -Depth 4 -Compress

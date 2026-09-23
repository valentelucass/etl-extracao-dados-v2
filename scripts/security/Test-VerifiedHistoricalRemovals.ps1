param([Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'VerifiedHistoricalRemovals.psm1') -Force
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (-not $evidence.StartsWith((Join-Path $root 'target') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $evidence)) { throw 'REMOVAL_TEST_NEW_PRIVATE_DIRECTORY_REQUIRED' }
$null = New-Item -ItemType Directory -Path $evidence
$relative = 'docs/catalogos/alinhamento-entidades/manifesto.json'
$manifest = Join-Path $root $relative
$entries = @((Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json).changedExistingFiles | Where-Object { $null -eq $_.after })
foreach ($path in @($relative) + @($entries.snapshot)) {
    $destination = Join-Path $evidence $path
    $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
    [IO.File]::Copy((Join-Path $root $path), $destination)
}
$checks = [Collections.Generic.List[string]]::new()
function Reject([string]$Expected) {
    $reason = 'ACCEPTED'
    try { $null = Get-VerifiedHistoricalRemovals -Root $evidence } catch { $reason = $_.Exception.Message }
    if (-not $reason.Contains($Expected)) { throw ('REMOVAL_TEST_WRONG_REJECTION_' + $reason) }
    $checks.Add($Expected)
}
$valid = Get-VerifiedHistoricalRemovals -Root $evidence
if ($valid.Count -ne $entries.Count -or $valid.Contains('src/unexplained.java')) { throw 'REMOVAL_TEST_SET' }
$checks.Add('EXACT_DECLARED_SET')
$copy = Join-Path $evidence $relative
[IO.File]::AppendAllText($copy, ' ')
Reject 'REMOVAL_MANIFEST_HASH'
[IO.File]::Copy($manifest, $copy, $true)
$snapshot = Join-Path $evidence $entries[0].snapshot
[IO.File]::AppendAllText($snapshot, 'altered')
Reject 'REMOVAL_SNAPSHOT_HASH'
[IO.File]::Copy((Join-Path $root $entries[0].snapshot), $snapshot, $true)
# Move only this test's verified snapshot within the newly created fixture directory.
if (-not $snapshot.StartsWith($evidence + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'REMOVAL_TEST_PATH' }
[IO.File]::Move($snapshot, $snapshot + '.saved')
Reject 'Cannot find path'
[IO.File]::Move($snapshot + '.saved', $snapshot)
$empty = Join-Path $evidence 'empty'
$null = New-Item -ItemType Directory -Path $empty
if ((Get-VerifiedHistoricalRemovals -Root $empty).Count -ne 0) { throw 'REMOVAL_TEST_EMPTY' }
$checks.Add('NO_MANIFEST_NO_EXEMPTION')
$checks | ConvertTo-Json | Set-Content (Join-Path $evidence 'checks.json')
Write-Output ('VERIFIED_REMOVALS_TEST PASS checks=' + $checks.Count)

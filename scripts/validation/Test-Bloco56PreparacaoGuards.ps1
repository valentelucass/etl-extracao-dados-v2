#Requires -Version 7.0
param([switch]$AllowInProgress)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$successor = $null
if (Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco56-continuacao/manifesto.json')) {
    $successor = & (Join-Path $PSScriptRoot 'Test-Bloco56Continuacao.ps1') -AsMap
}
$manifest = 'docs/catalogos/bloco56/manifesto.json'
$m = Get-Content -LiteralPath (Join-Path $root $manifest) -Raw | ConvertFrom-Json -DateKind String
$t = Get-Content -LiteralPath (Join-Path $root 'docs/catalogos/bloco56/triagem.json') -Raw | ConvertFrom-Json -DateKind String
$fixture = Join-Path $root ('target/bloco56/guards/' + [guid]::NewGuid().ToString('N'))
$paths = @($m.changedExistingFiles.path) + @($m.newFiles.path) + @($m.preservedFiles.path) + @($m.predecessor.path, $manifest) + @($t.sources | Where-Object { -not $_.reviewedRevisionPath.StartsWith('../') } | ForEach-Object reviewedRevisionPath)
$paths = @($paths | Sort-Object -Unique)
if ($paths.Count -gt 1500) { throw 'B56_GUARD_COPY_BOUND' }
foreach ($path in $paths) {
    if ($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')) { throw 'B56_GUARD_PATH' }
    $destination = Join-Path $fixture $path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
    $revision = if ($null -ne $successor -and $successor.ContainsKey($path)) { $successor[$path].snapshot } else { $path }
    [IO.File]::Copy((Join-Path $root $revision), $destination, $false)
}
$validator = Join-Path $fixture 'scripts/validation/Test-Bloco56Preparacao.ps1'
& $validator -AllowInProgress:$AllowInProgress | Out-Null
$results = [Collections.Generic.List[object]]::new()
function Reject([string]$Id, [string]$Path, [scriptblock]$Mutation, [string]$Expected, [switch]$RefreshEntry) {
    $file = Join-Path $fixture $Path
    $manifestFile = Join-Path $fixture $manifest
    $bytes = [IO.File]::ReadAllBytes($file)
    $manifestBytes = [IO.File]::ReadAllBytes($manifestFile)
    try {
        $original = $utf8.GetString($bytes)
        $changed = & $Mutation $original
        if ($changed -ceq $original) { throw 'B56_MUTATION_MUST_CHANGE' }
        [IO.File]::WriteAllText($file, $changed, $utf8)
        if ($RefreshEntry) {
            # Keep file integrity valid to exercise the semantic guard independently.
            $mutantManifest = $utf8.GetString($manifestBytes) | ConvertFrom-Json -DateKind String
            $entry = @($mutantManifest.newFiles | Where-Object path -CEQ $Path)
            if ($entry.Count -ne 1) { throw 'B56_MUTANT_ENTRY' }
            $entry[0].sha256 = (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
            [IO.File]::WriteAllText($manifestFile, ($mutantManifest | ConvertTo-Json -Depth 35), $utf8)
        }
        $reason = 'NOT_REJECTED'
        try { & $validator -AllowInProgress:$AllowInProgress | Out-Null }
        catch { $reason = $_.Exception.Message }
        if ($reason -notmatch $Expected) { throw ('B56_GUARD_FAILED_' + $Id + '_' + $reason) }
        $results.Add([ordered]@{id=$Id;layer='OFFLINE_ISOLATED_DOCUMENT_FIXTURE';reason=$reason;passed=$true})
    } finally {
        [IO.File]::WriteAllBytes($file, $bytes)
        [IO.File]::WriteAllBytes($manifestFile, $manifestBytes)
    }
}
Reject 'PHYSICAL_AUTH_FROM_PREPARATION' $manifest {param($s) $s.Replace('"physicalExecutionAuthorized": false','"physicalExecutionAuthorized": true')} 'B56_NO_PHYSICAL_OR_FALSE_ACCEPTANCE'
Reject 'BUDGET_TRANSFER' $manifest {param($s) $s.Replace('"budgetTransferred": false','"budgetTransferred": true')} 'B56_NO_PHYSICAL_OR_FALSE_ACCEPTANCE'
Reject 'UNDECLARED_RESERVATION' $manifest {param($s) $s.Replace('"reservations": 0','"reservations": 1')} 'B56_ZERO_EFFECTS_REQUIRED'
Reject 'FALSE_PROGRESS' $manifest {param($s) $s.Replace('"done": 65','"done": 70')} 'B56_PROGRESS_DRIFT'
Reject 'RUNTIME_EXCEPTION' $manifest {param($s) $s.Replace('"path": "STATES.md"','"path": "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java"')} 'B56_EXACT_DOCUMENT_SUCCESSION'
Reject 'PREDECESSOR_REWRITTEN' $manifest {param($s) $s.Replace('05427a35461695468ce0012c00804d0511c52c0d1f7bbbb41cd56b2e89c7d11c',('0'*64))} 'B56_PREDECESSOR'
Reject 'SNAPSHOT_REWRITTEN' 'docs/continuidade/historico/pos-bloco55/STATES.md' {param($s) $s+"`nUNPROVEN"} 'B56_HASH_'
Reject 'FIXTURE_AS_ELIGIBILITY' 'docs/catalogos/bloco56/triagem.json' {param($s) $s.Replace('"implementationEligible": false','"implementationEligible": true')} 'B56_NO_PHYSICAL_OR_FALSE_ACCEPTANCE' -RefreshEntry
Reject 'OLD_CORPUS_AS_NEW' 'docs/catalogos/bloco56/triagem.json' {param($s) $s.Replace('UNCHANGED_PREVIOUSLY_ASSESSED','NEW_PROVIDER_PROOF')} 'B56_OLD_EVIDENCE_AS_NEW' -RefreshEntry
Reject 'PARSER_AS_AUTHENTICITY' 'docs/catalogos/bloco56/triagem.json' {param($s) $s.Replace('"authenticityOrBusinessAcceptanceByValidator": false','"authenticityOrBusinessAcceptanceByValidator": true')} 'B56_NO_PHYSICAL_OR_FALSE_ACCEPTANCE' -RefreshEntry
Reject 'ORIGINAL_CRITERION_REMOVED' 'docs/catalogos/bloco56/matriz-criterios.csv' {param($s) $s.Replace('FAT-07','FAT-99')} 'B56_CRITERION_COVERAGE' -RefreshEntry
Reject 'MIGRATION_REWRITTEN' 'database/migrations/V023__correct_scoped_runtime_output_projection.sql' {param($s) $s+"`n-- changed"} 'B56_HASH_'
& $validator -AllowInProgress:$AllowInProgress | Out-Null
if ($results.Count -ne 12) { throw 'B56_TWELVE_CONTRAPROOFS_REQUIRED' }
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'), ($results | ConvertTo-Json -Depth 5), $utf8)
'B56_TWELVE_CONTRAPROOFS_PASS_CANONICAL_FILES_UNCHANGED'

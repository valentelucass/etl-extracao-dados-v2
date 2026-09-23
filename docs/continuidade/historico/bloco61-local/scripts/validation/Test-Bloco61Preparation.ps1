#Requires -Version 7.5
param([switch]$AsMap, [switch]$IncludePrivateEvidence, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifestPath = 'docs/catalogos/bloco61-preparacao/manifesto.json'
$history = 'docs/continuidade/historico/bloco61-preparacao/'
$allowed = @('STATES.md', 'docs/runbooks/trilha-de-chats-gpt-5-6.md',
    'docs/continuidade/RETOMADA.md', 'scripts/validation/Test-Bloco60RenewedClosure.ps1')
$additions = @($allowed | ForEach-Object { $history + $_ }) + @(
    'scripts/validation/Test-Bloco61Preparation.ps1',
    'docs/catalogos/bloco61-preparacao/README.md',
    'docs/runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md',
    'docs/continuidade/checkpoints/0050-avaliacao-e-preparacao-bloco61.md')

function Local([string]$Path) {
    if ($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')) {
        throw 'B61P_PATH'
    }
    $node = Get-Item -LiteralPath (Join-Path $root $Path)
    $file = $node.FullName
    while ($node.FullName -cne $root) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'B61P_REPARSE' }
        $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
    }
    return $file
}

function Read([string]$Path) {
    return [IO.File]::ReadAllText((Local $Path), [Text.UTF8Encoding]::new($false, $true))
}

function Hash([string]$Path, [string]$Expected) {
    if ($Expected -cnotmatch '^[a-f0-9]{64}$' -or
        (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected) {
        throw ('B61P_HASH_' + $Path)
    }
}

function Exact($Actual, $Expected, [string]$Reason) {
    if (@($Actual).Count -ne @($Expected).Count -or
        ((@($Actual | Sort-Object -Unique)) -join '|') -cne
        ((@($Expected | Sort-Object -Unique)) -join '|')) { throw $Reason }
}

function Check($m) {
    if ($m.version -ne 1 -or $m.proposedBlock -ne 61 -or
        $m.status -cne 'ATENCOES_REGISTRADAS_B61_PROPOSTO_NAO_EXECUTADO' -or
        $m.startsBlock -or $m.physicalExecuted -or $m.authorizesPhysicalExecution -or
        $m.newAcceptances -ne 0 -or $m.newReservations -ne 0) { throw 'B61P_SCOPE' }
    if ($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or
        $m.progress.pending -ne 48 -or $m.progress.openRoutes -ne 191 -or
        $m.progress.now -ne 0) { throw 'B61P_PROGRESS' }
    if ($m.predecessor.path -cne 'docs/catalogos/bloco60-renovacao/manifesto.json' -or
        $m.predecessor.sha256 -cne '976b2c3fbb4324736d4f0f937b431c252ea62b7d38928268feee512ada6418db') {
        throw 'B61P_PREDECESSOR'
    }
    Hash $m.predecessor.path $m.predecessor.sha256
    $old = Read $m.predecessor.path | ConvertFrom-Json -Depth 60 -DateKind String
    $baseline = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($e in $old.preservedFiles) { $baseline.Add($e.path, $e.sha256) }
    foreach ($e in $old.changedExistingFiles) { $baseline.Add($e.path, $e.after) }
    foreach ($e in $old.newFiles) { $baseline.Add($e.path, $e.sha256) }
    $baseline.Add($m.predecessor.path, $m.predecessor.sha256)
    if ($baseline.Count -ne 2276 -or $m.initialFiles -ne 2276) { throw 'B61P_BASELINE' }
    Exact $m.changedExistingFiles.path $allowed 'B61P_DELTAS'
    Exact $m.newFiles.path $additions 'B61P_ADDITIONS'
    $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($e in $m.changedExistingFiles) {
        if (-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or
            $e.snapshot -cne ($history + $e.path)) { throw 'B61P_BEFORE' }
        Hash $e.snapshot $e.before
        Hash $e.path $e.after
        $map.Add($e.path, $e)
    }
    if ($m.preservedFiles.Count + $map.Count -ne 2276) { throw 'B61P_PRESERVED_COUNT' }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($e in $m.preservedFiles) {
        if ($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or
            -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]) {
            throw 'B61P_PRESERVED'
        }
        Hash $e.path $e.sha256
    }
    foreach ($e in $m.newFiles) {
        if ($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)) { throw 'B61P_NEW_COLLISION' }
        Hash $e.path $e.sha256
    }
    foreach ($path in @('STATES.md', 'docs/runbooks/trilha-de-chats-gpt-5-6.md')) {
        $current = Read $path
        $previous = Read $map[$path].snapshot
        if (-not $current.EndsWith($previous, [StringComparison]::Ordinal)) { throw 'B61P_HISTORY' }
        $pattern = '(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n", "`n"), $pattern) | ForEach-Object Value) `
            @([regex]::Matches($previous.Replace("`r`n", "`n"), $pattern) | ForEach-Object Value) 'B61P_CHECKBOX'
    }
    $state = Read 'STATES.md'
    $boxes = [regex]::Matches($state, '(?m)^\s*- \[([ xX])\]')
    $trail = Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
    if ($boxes.Count -ne 115 -or @($boxes | Where-Object { $_.Groups[1].Value -match '[xX]' }).Count -ne 67 -or
        [regex]::Matches($trail, '(?m)^- \[ \] STATUS=').Count -ne 191 -or
        $trail -match '(?m)^- \[ \] STATUS=AGORA \|') { throw 'B61P_CURRENT_PROGRESS' }
    foreach ($text in @($state, $trail, (Read 'docs/continuidade/RETOMADA.md'))) {
        if (-not $text.Contains($m.status)) { throw 'B61P_POINTER' }
    }
    if (@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120) { throw 'B61P_INDEX_LIMIT' }
    $prompt = Read 'docs/runbooks/prompt-bloco-61-consolidacao-local-e-paridade.md'
    if (-not $prompt.Contains('PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO')) { throw 'B61P_PROPOSAL' }
    if ($m.historicalReceipt.path -cne 'target/b60-conclusao-20260910/final/receipt.json' -or
        $m.historicalReceipt.sha256 -cne '37f35f77e160f4fdfa88c361089d2b99f9544153921c2a049588dde3bc88478e') {
        throw 'B61P_RECEIPT'
    }
    if ($IncludePrivateEvidence) {
        Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
        $receipt = Read $m.historicalReceipt.path | ConvertFrom-Json -Depth 60 -DateKind String
        if ($receipt.artifacts.Count -ne 4286) { throw 'B61P_RECEIPT_COUNT' }
        foreach ($e in $receipt.artifacts) {
            $path = if ($map.ContainsKey($e.path)) { $map[$e.path].snapshot } else { $e.path }
            Hash $path $e.sha256
        }
    }
    return ,$map
}

$manifest = Read $manifestPath | ConvertFrom-Json -Depth 60 -DateKind String
$result = Check $manifest
$guards = 0
if ($SelfTest) {
    foreach ($tuple in @(
        @('B61P_SCOPE', { param($m) $m.startsBlock = $true }),
        @('B61P_SCOPE', { param($m) $m.physicalExecuted = $true }),
        @('B61P_SCOPE', { param($m) $m.authorizesPhysicalExecution = $true }),
        @('B61P_SCOPE', { param($m) $m.newAcceptances = 1 }),
        @('B61P_SCOPE', { param($m) $m.newReservations = 1 }),
        @('B61P_PROGRESS', { param($m) $m.progress.now = 1 }),
        @('B61P_PREDECESSOR', { param($m) $m.predecessor.sha256 = '0' * 64 }),
        @('B61P_DELTAS', { param($m) $m.changedExistingFiles[0].path = 'src/unreviewed.java' }),
        @('B61P_ADDITIONS', { param($m) $m.newFiles[0].path = 'database/unreviewed.sql' }),
        @('B61P_BEFORE', { param($m) $m.changedExistingFiles[0].snapshot = '../outside.md' })
    )) {
        $copy = $manifest | ConvertTo-Json -Depth 60 | ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason = 'ACCEPTED'
        try { $null = Check $copy } catch { $reason = $_.Exception.Message }
        if ($reason -cne $tuple[0]) { throw ('B61P_GUARD_' + $tuple[0] + '_GOT_' + $reason) }
        $guards++
    }
}
if ($AsMap) { return ,$result }
@{ passed = $true; status = $manifest.status; private = [bool]$IncludePrivateEvidence;
    guards = $guards; newAcceptances = 0; startsBlock = $false; sqlExecuted = $false } | ConvertTo-Json

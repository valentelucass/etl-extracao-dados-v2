#Requires -Version 7.5
param([switch]$AsMap, [switch]$IncludePrivateEvidence, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifestPath = 'docs/catalogos/bloco61-local/manifesto.json'
$history = 'docs/continuidade/historico/bloco61-local/'
$allowed = @(
    'STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md',
    'scripts/validation/Test-Bloco61Preparation.ps1','scripts/validation/Bloco60Assertions.psm1',
    'scripts/validation/Test-Bloco60CorrectivePackage.ps1',
    'scripts/validation/Bloco60ConcurrentPair.psm1',
    'src/main/java/br/com/esl/etl/v2/modulos/manifestos/domain/ManifestoFieldValue.java',
    'src/main/java/br/com/esl/etl/v2/modulos/manifestos/domain/ManifestoRootReducer.java',
    'src/main/java/br/com/esl/etl/v2/modulos/manifestos/aplicacao/ManifestoDataExportRecordMapper.java',
    'src/test/java/br/com/esl/etl/v2/modulos/manifestos/domain/ManifestoRootReducerTest.java',
    'src/test/java/br/com/esl/etl/v2/arquitetura/ArchitectureRulesTest.java')
$additions = @($allowed | ForEach-Object { $history + $_ }) + @(
    'scripts/validation/Test-Bloco61Local.ps1','scripts/validation/Test-RuntimeLocal.ps1',
    'scripts/validation/Test-RuntimeQualification.ps1','scripts/validation/Test-RuntimeQualificationGuards.ps1',
    'scripts/validation/RuntimeQualificationEvidence.psm1','scripts/validation/RuntimeObserverContract.cs',
    'src/test/resources/runtime-qualification/concurrent.synthetic.json',
    'docs/adr/0042-manifestos-fronteira-json-e-coorte-limitada.md',
    'docs/runbooks/validacao-runtime-corrente.md','docs/runbooks/bloco61-matriz-paridade.md',
    'docs/continuidade/checkpoints/0051-bloco61-fronteira-manifestos.md',
    'docs/continuidade/checkpoints/0052-bloco61-validadores-e-inputs.md',
    'docs/continuidade/checkpoints/0053-bloco61-consolidacao-local-concluida.md',
    'docs/continuidade/checkpoints/0054-bloco61-fechamento-da-sucessao.md',
    'docs/catalogos/bloco61-local/RELATORIO.md')
function Local([string]$Path) {
    if ($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')) {
        throw 'B61L_PATH'
    }
    $node = Get-Item -LiteralPath (Join-Path $root $Path)
    $file = $node.FullName
    while ($node.FullName -cne $root) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'B61L_REPARSE' }
        $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
    }
    return $file
}
function Read([string]$Path) { [IO.File]::ReadAllText((Local $Path), [Text.UTF8Encoding]::new($false,$true)) }
function Hash([string]$Path, [string]$Expected) {
    if ($Expected -cnotmatch '^[a-f0-9]{64}$' -or
        (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected) {
        throw ('B61L_HASH_' + $Path)
    }
}
function Exact($Actual, $Expected, [string]$Reason) {
    if (@($Actual).Count -ne @($Expected).Count -or
        ((@($Actual | Sort-Object -Unique)) -join '|') -cne
        ((@($Expected | Sort-Object -Unique)) -join '|')) { throw $Reason }
}
function Check($m) {
    if ($m.version -ne 1 -or $m.block -ne 61 -or
        $m.status -cne 'LOCAL_CONSOLIDATION_COMPLETE_PARITY_INPUTS_PREPARED' -or
        -not $m.adoptedByUser -or $m.physicalExecuted -or $m.authorizesPhysicalExecution -or
        $m.newAcceptances -ne 0 -or $m.newReservations -ne 0 -or $m.parityExecuted) { throw 'B61L_SCOPE' }
    if ($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.pending -ne 48 -or
        $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0) { throw 'B61L_PROGRESS' }
    if ($m.predecessor.path -cne 'docs/catalogos/bloco61-preparacao/manifesto.json' -or
        $m.predecessor.sha256 -cne 'be2f1ff64c158ea683b8b9ebc9193cc815361ff59650da8fb184b75f50b9bf51') {
        throw 'B61L_PREDECESSOR'
    }
    Hash $m.predecessor.path $m.predecessor.sha256
    $old = Read $m.predecessor.path | ConvertFrom-Json -Depth 60 -DateKind String
    $baseline = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($e in $old.preservedFiles) { $baseline.Add($e.path,$e.sha256) }
    foreach ($e in $old.changedExistingFiles) { $baseline.Add($e.path,$e.after) }
    foreach ($e in $old.newFiles) { $baseline.Add($e.path,$e.sha256) }
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if ($baseline.Count -ne 2285 -or $m.initialFiles -ne 2285) { throw 'B61L_BASELINE' }
    Exact $m.changedExistingFiles.path $allowed 'B61L_DELTAS'
    Exact $m.newFiles.path $additions 'B61L_ADDITIONS'
    $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach ($e in $m.changedExistingFiles) {
        if (-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or
            $e.snapshot -cne ($history + $e.path)) { throw 'B61L_BEFORE' }
        Hash $e.snapshot $e.before
        Hash $e.path $e.after
        $map.Add($e.path,$e)
    }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if ($m.preservedFiles.Count + $map.Count -ne 2285) { throw 'B61L_PRESERVED_COUNT' }
    foreach ($e in $m.preservedFiles) {
        if ($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or
            -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]) {
            throw 'B61L_PRESERVED'
        }
        Hash $e.path $e.sha256
    }
    foreach ($e in $m.newFiles) {
        if ($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)) { throw 'B61L_NEW_COLLISION' }
        Hash $e.path $e.sha256
    }
    foreach ($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')) {
        $current = Read $path; $previous = Read $map[$path].snapshot
        if (-not $current.EndsWith($previous,[StringComparison]::Ordinal)) { throw 'B61L_HISTORY' }
        $pattern = '(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern) | ForEach-Object Value) `
            @([regex]::Matches($previous.Replace("`r`n","`n"),$pattern) | ForEach-Object Value) 'B61L_CHECKBOX'
    }
    $state = Read 'STATES.md'; $trail = Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
    $boxes = [regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if ($boxes.Count -ne 115 -or @($boxes | Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or
        [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or
        $trail -match '(?m)^- \[ \] STATUS=AGORA \|') { throw 'B61L_CURRENT_PROGRESS' }
    foreach ($text in @($state,$trail,(Read 'docs/continuidade/RETOMADA.md'))) {
        if (-not $text.Contains($m.status)) { throw 'B61L_POINTER' }
    }
    if (@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120) { throw 'B61L_INDEX_LIMIT' }
    if ($m.historicalReceipt.path -cne 'target/b60-conclusao-20260910/final/receipt.json' -or
        $m.historicalReceipt.sha256 -cne '37f35f77e160f4fdfa88c361089d2b99f9544153921c2a049588dde3bc88478e') {
        throw 'B61L_RECEIPT'
    }
    if ($IncludePrivateEvidence) {
        Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
        $receipt = Read $m.historicalReceipt.path | ConvertFrom-Json -Depth 60 -DateKind String
        if ($receipt.artifacts.Count -ne 4286) { throw 'B61L_RECEIPT_COUNT' }
        $priorMap = @{}
        foreach ($e in $old.changedExistingFiles) { $priorMap.Add($e.path,$e.snapshot) }
        foreach ($e in $receipt.artifacts) {
            $path = if ($priorMap.ContainsKey($e.path)) { $priorMap[$e.path] }
                elseif ($map.ContainsKey($e.path)) { $map[$e.path].snapshot } else { $e.path }
            Hash $path $e.sha256
        }
        foreach ($e in $m.evidence) { Hash $e.path $e.sha256 }
    }
    return ,$map
}
$manifest = Read $manifestPath | ConvertFrom-Json -Depth 60 -DateKind String
$result = Check $manifest
$guards = 0
if ($SelfTest) {
    foreach ($tuple in @(
        @('B61L_SCOPE',{param($m)$m.physicalExecuted=$true}),
        @('B61L_SCOPE',{param($m)$m.parityExecuted=$true}),
        @('B61L_SCOPE',{param($m)$m.newAcceptances=1}),
        @('B61L_PROGRESS',{param($m)$m.progress.now=1}),
        @('B61L_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('B61L_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('B61L_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'}),
        @('B61L_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B61L_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
        @(('B61L_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64}),
        @('B61L_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64})
    )) {
        $copy = $manifest | ConvertTo-Json -Depth 60 | ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED'
        try { $null=Check $copy } catch { $reason=$_.Exception.Message }
        if ($reason -cne $tuple[0]) { throw ('B61L_GUARD_'+$tuple[0]+'_GOT_'+$reason) }
        $guards++
    }
}
if ($AsMap) { return ,$result }
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;
    newAcceptances=0;sqlExecuted=$false;physicalObserverQualified=$false} | ConvertTo-Json

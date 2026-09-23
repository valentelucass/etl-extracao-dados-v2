#Requires -Version 7.0
param([switch]$AsMap, [switch]$IncludePrivateEvidence, [switch]$AllowInProgress)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$successor = $null
if (Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco56-continuacao/manifesto.json')) {
    $successor = & (Join-Path $PSScriptRoot 'Test-Bloco56Continuacao.ps1') -AsMap
}
function Historical-Revision([string]$Path) {
    if ($null -ne $successor -and $successor.ContainsKey($Path)) { return $successor[$Path].snapshot }
    return $Path
}
function Resolve-Local([string]$Path) {
    if ($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')) { throw 'B56_PATH' }
    $file = Join-Path $root $Path
    $node = Get-Item -LiteralPath $file
    while ($node.FullName -cne $root) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'B56_REPARSE' }
        $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
    }
    return $file
}
function Hash-File([string]$File) {
    $stream = [IO.File]::OpenRead($File)
    try { return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant() }
    finally { $stream.Dispose() }
}
function Read-Local([string]$Path) {
    $file = Resolve-Local (Historical-Revision $Path)
    if ((Get-Item -LiteralPath $file).Length -gt 8MB) { throw 'B56_FILE_BOUND' }
    return $utf8.GetString([IO.File]::ReadAllBytes($file))
}
function Assert-Hash([string]$Path, [string]$Hash) {
    if ($Hash -cnotmatch '^[a-f0-9]{64}$' -or (Hash-File (Resolve-Local (Historical-Revision $Path))) -cne $Hash) { throw ('B56_HASH_' + $Path) }
}
function Exact-Set($Actual, $Expected, [string]$Reason) {
    if (@($Actual).Count -ne @($Expected).Count -or (@($Actual | Sort-Object -Unique) -join '|') -cne (@($Expected | Sort-Object -Unique) -join '|')) { throw $Reason }
}
function Require-False($Value) {
    if ($Value -isnot [bool] -or $Value) { throw 'B56_NO_PHYSICAL_OR_FALSE_ACCEPTANCE' }
}
$m = Read-Local 'docs/catalogos/bloco56/manifesto.json' | ConvertFrom-Json -DateKind String
$complete = $m.status -ceq 'LOCAL_PREPARATION_COMPLETE_IDENTITIES_BLOCKED'
if ($m.version -ne 1 -or $m.block -ne 56 -or (-not $complete -and (-not $AllowInProgress -or $m.status -cne 'LOCAL_PREPARATION_VALIDATING'))) { throw 'B56_PREPARATION_STATE' }
foreach ($name in @('physicalExecutionAuthorized', 'realSourceExecuted', 'sqlExecuted', 'runtimeChanged', 'identityAccepted', 'budgetTransferred', 'validityRenewed')) { Require-False $m.$name }
foreach ($name in @('newCanonicalAcceptances', 'reservations', 'httpRequests', 'sqlOperations', 'installations', 'unknownPhysicalResults')) { if ($m.$name -isnot [long] -and $m.$name -isnot [int]) { throw 'B56_COUNTER_TYPE' }; if ($m.$name -ne 0) { throw 'B56_ZERO_EFFECTS_REQUIRED' } }
foreach ($name in @('physicalEffects', 'rightsDelta', 'eligibleVerticals')) { if ($m.$name -isnot [array] -or $m.$name.Count -ne 0) { throw 'B56_NO_ELIGIBLE_PHYSICAL_PACKAGE' } }
foreach ($pair in @(@('total',115),@('done',65),@('pending',50),@('openRoutes',193),@('now',0))) { if ($m.progress.($pair[0]) -ne $pair[1]) { throw 'B56_PROGRESS_DRIFT' } }
$historyPath = 'docs/continuidade/manifesto-pos-bloco55.json'
$historyHash = '05427a35461695468ce0012c00804d0511c52c0d1f7bbbb41cd56b2e89c7d11c'
if ($m.predecessor.path -cne $historyPath -or $m.predecessor.sha256 -cne $historyHash) { throw 'B56_PREDECESSOR' }
Assert-Hash $historyPath $historyHash
$history = Read-Local $historyPath | ConvertFrom-Json
$allowed = @('STATES.md', 'docs/runbooks/trilha-de-chats-gpt-5-6.md', 'docs/continuidade/RETOMADA.md', 'scripts/validation/Test-ContinuidadeAgentes.ps1', 'scripts/validation/Test-ContinuidadeAgentesGuards.ps1')
Exact-Set $m.changedExistingFiles.path $allowed 'B56_EXACT_DOCUMENT_SUCCESSION'
$map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach ($entry in $m.changedExistingFiles) {
    $previous = @($history.changedExistingFiles | Where-Object path -CEQ $entry.path)
    if ($previous.Count) { $expected = $previous[0].after }
    else { $expected = @($history.newFiles | Where-Object path -CEQ $entry.path)[0].sha256 }
    if ($entry.before -cne $expected -or $entry.snapshot -cne ('docs/continuidade/historico/pos-bloco55/' + $entry.path)) { throw 'B56_SNAPSHOT_SUCCESSION' }
    Assert-Hash $entry.snapshot $entry.before
    Assert-Hash $entry.path $entry.after
    $map.Add($entry.path, $entry)
}
$required = @('docs/catalogos/bloco56/README.md', 'docs/catalogos/bloco56/triagem.json', 'docs/catalogos/bloco56/matriz-criterios.csv', 'scripts/validation/Test-Bloco56Preparacao.ps1', 'scripts/validation/Test-Bloco56PreparacaoGuards.ps1', 'docs/continuidade/checkpoints/0002-bloco56-adocao-local.md', 'docs/continuidade/checkpoints/0003-bloco56-triagem-e-pacote.md') + @($m.changedExistingFiles.snapshot)
if ($complete) { $required += 'docs/continuidade/checkpoints/0004-bloco56-fechamento-local.md' }
Exact-Set $m.newFiles.path $required 'B56_EXACT_NEW_FILE_SET'
foreach ($entry in $m.newFiles) { Assert-Hash $entry.path $entry.sha256 }
foreach ($entry in $m.preservedFiles) { Assert-Hash $entry.path $entry.sha256 }
foreach ($requiredPath in @('database/manifest/runtime-bloco55.json','database/manifest/runtime-bloco55-acceptances.json','database/manifest/runtime-bloco54.json','docs/catalogos/portabilidade/matriz-campos.csv','docs/catalogos/portabilidade/manifesto.json')) {
    if (@($m.preservedFiles | Where-Object path -CEQ $requiredPath).Count -ne 1) { throw 'B56_PRESERVATION_SET' }
}
$t = Read-Local 'docs/catalogos/bloco56/triagem.json' | ConvertFrom-Json -DateKind String
if ($t.version -ne 1 -or $t.block -ne 56 -or $t.scope -cne 'CATALOG_REFERENCED_LOCAL_FILES_ONLY' -or $t.newProviderArtifactsReceived -ne 0) { throw 'B56_TRIAGE_SCOPE' }
Require-False $t.payloadCampaignRepeated
Require-False $t.authenticityOrBusinessAcceptanceByValidator
Exact-Set $t.fronts.route @('P08','P09','P10','P11') 'B56_FOUR_IDENTITY_FRONTS'
Exact-Set $t.fronts.front @('A','B','C','D') 'B56_IDENTITY_FRONT_SET'
Exact-Set $t.fronts.verticalFront @('E','F','G','H') 'B56_VERTICAL_FRONT_SET'
$originalState = Read-Local 'docs/continuidade/historico/pos-bloco55/STATES.md'
foreach ($front in $t.fronts) {
    $identity = Read-Local $front.identityManifest | ConvertFrom-Json
    if ($identity.route -cne $front.route -or $identity.task -cne $front.identityTask -or $identity.decisionStatus -cne $front.decisionStatus) { throw 'B56_IDENTITY_DRIFT' }
    Require-False $identity.identity.outcome.closeSubcheckbox
    Require-False $front.implementationEligible
    if ($front.identityState -cne 'BLOQUEADO_POR_INPUT' -or $front.verticalState -cne 'BLOQUEADO_POR_IDENTIDADE' -or $front.newPertinentEvidence.Count) { throw 'B56_UNSUPPORTED_ELIGIBILITY' }
    Exact-Set $front.blockers $identity.identity.outcome.blockers 'B56_BLOCKER_OMISSION'
    Exact-Set $front.unblockEvidence $identity.identity.outcome.unblockEvidence 'B56_UNBLOCK_EVIDENCE_OMISSION'
    $taskSection = [regex]::Match($originalState,'(?ms)^- \[ \] \*\*'+$front.verticalTask+' —.*?(?=^- \[|\z)').Value.Trim()
    if (-not $taskSection -or $taskSection -cne $front.verticalAcceptance) { throw 'B56_ORIGINAL_ACCEPTANCE_CHANGED' }
    foreach ($dep in $front.dependencies) {
        if ($dep.state -cne 'ACCEPTED_EXISTING_LOCAL_SCOPE' -or [regex]::Matches($originalState,'(?m)^\s*- \[x\] \*\*'+[regex]::Escape($dep.task)+' —').Count -ne 1) { throw 'B56_DEPENDENCY_FALSE_CLOSURE' }
        Require-False $dep.newAcceptance
    }
    foreach ($anchor in $front.anchors) {
        if ($anchor.expectedSha256 -cne $anchor.observedSha256 -or $anchor.novelty -cne 'UNCHANGED_PREVIOUSLY_ASSESSED' -or $anchor.contentReanalysis -cne 'NOT_REPEATED') { throw 'B56_OLD_EVIDENCE_AS_NEW' }
    }
    foreach ($path in @($front.identityManifest,$front.contractManifest)) {
        if (@($m.preservedFiles | Where-Object path -CEQ $path).Count -ne 1) { throw 'B56_IDENTITY_PRESERVATION_REQUIRED' }
    }
}
if ($t.sources.Count -ne 51 -or @($t.fronts.anchors).Count -ne 14) { throw 'B56_CORPUS_BOUND' }
foreach ($source in $t.sources) {
    Require-False $source.authenticityProven
    if ($source.reviewedRevisionPath.StartsWith('../etl-extracao-dados/')) {
        # External legacy bytes were checked by the existing four identity validators;
        # this document validator never resolves arbitrary parent paths.
        if (@($t.fronts.anchors | Where-Object path -CEQ $source.path).Count -ne 1) { throw 'B56_LEGACY_ALLOWLIST' }
    } else { Assert-Hash $source.reviewedRevisionPath $source.sha256 }
}
$matrix = @(Read-Local 'docs/catalogos/bloco56/matriz-criterios.csv' | ConvertFrom-Csv)
if ($matrix.Count -ne 26) { throw 'B56_CRITERION_COVERAGE' }
$rules = @('CAP-01','CAP-02','CAP-03','CAP-04','CAP-05','FAT-01','FAT-02','FAT-03','FAT-04','FAT-05','FAT-06','FAT-07','INV-01','INV-02','INV-03','INV-04','SIN-01','SIN-02')
Exact-Set @($matrix | Where-Object criterion -NotIn @('IDENTIDADE','ACEITE_IMPLEMENTACAO') | ForEach-Object criterion) $rules 'B56_CRITERION_COVERAGE'
foreach ($row in $matrix) {
    if ($row.state -cnotin @('BLOQUEADO_POR_INPUT','NAO_IMPLEMENTADO_DEPENDE_IDENTIDADE') -or -not $row.component -or -not $row.expectedProof -or -not $row.limit) { throw 'B56_PLANNING_IS_NOT_IMPLEMENTATION' }
    if ($row.criterion -cin $rules -and $row.originalCriterion -cne [regex]::Match($originalState,'(?m)^- \*\*'+$row.criterion+'[^\r\n]+').Value) { throw 'B56_ORIGINAL_RULE_CHANGED' }
}
$state = Read-Local 'STATES.md'; $trail = Read-Local 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
$oldTrail = Read-Local 'docs/continuidade/historico/pos-bloco55/docs/runbooks/trilha-de-chats-gpt-5-6.md'
foreach ($pair in @(@($state,$originalState),@($trail,$oldTrail))) {
    $currentBoxes = @([regex]::Matches($pair[0],'(?m)^\s*- \[[ xX]\].+$') | ForEach-Object Value)
    $originalBoxes = @([regex]::Matches($pair[1],'(?m)^\s*- \[[ xX]\].+$') | ForEach-Object Value)
    Exact-Set $currentBoxes $originalBoxes 'B56_CANONICAL_OR_ROUTE_REWRITE'
}
foreach ($text in @($state,$trail,(Read-Local 'docs/continuidade/RETOMADA.md'))) {
    $marker = if ($complete) { 'B56_PREPARACAO_LOCAL_CONCLUIDA_IDENTIDADES_BLOQUEADAS' } else { 'B56_PREPARACAO_LOCAL_EM_EXECUCAO' }
    if (-not $text.Contains($marker)) { throw 'B56_CURRENT_POINTER' }
}
if (@((Read-Local 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120) { throw 'B56_RESTART_INDEX_BOUND' }
if ($IncludePrivateEvidence) {
    Assert-Hash $m.initialInventory.path $m.initialInventory.sha256
    Assert-Hash $m.privateInventory.path $m.privateInventory.sha256
    $initial = Read-Local $m.initialInventory.path | ConvertFrom-Json
    if ($initial.Count -ne 1338) { throw 'B56_INITIAL_INVENTORY_COUNT' }
    foreach ($entry in $initial) {
        Assert-Hash ('target/bloco56/initial/' + $entry.path) $entry.sha256
        if (-not $map.ContainsKey($entry.path)) { Assert-Hash $entry.path $entry.sha256 }
    }
    $private = Read-Local $m.privateInventory.path | ConvertFrom-Json
    foreach ($entry in $private) {
        if ($entry.path -cnotmatch '^target/bloco5[345]/') { throw 'B56_PRIVATE_INVENTORY_SCOPE' }
        Assert-Hash $entry.path $entry.sha256
    }
    foreach ($entry in $m.testEvidence) { Assert-Hash $entry.path $entry.sha256 }
}
if ($null -ne $successor) {
    foreach ($path in @($map.Keys)) {
        if ($successor.ContainsKey($path)) {
            if ($map[$path].after -cne $successor[$path].before) { throw 'B56_CONTINUATION_CHAIN_MISMATCH' }
            $map[$path].after = $successor[$path].after
        }
    }
    # The scanner existed before B56 and remains pinned by the predecessor.
    # Carry its exact, verified delta to the B55 validator as well.
    $scannerPath = 'scripts/security/Invoke-OfflineSecretScan.ps1'
    if ($successor.ContainsKey($scannerPath)) { $map.Add($scannerPath, $successor[$scannerPath]) }
}
if ($AsMap) { return ,$map }
if ($null -ne $successor) { 'B56_PREPARATION_HISTORICAL_PROOFS_AND_READ_ONLY_SUCCESSOR_PASS' }
elseif ($complete) { 'B56_LOCAL_PREPARATION_PASS_FOUR_HOLDS_ZERO_NEW_ACCEPTANCES_OR_PHYSICAL_EFFECTS' }
else { 'B56_IN_PROGRESS_CHECKS_PASS_NO_COMPLETION_CLAIM' }

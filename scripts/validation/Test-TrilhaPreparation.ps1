#Requires -Version 7.0
[CmdletBinding()]
param([switch]$SelfTest)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Read-LocalText([string]$RelativePath) {
    # This validator consumes declarations, never commands or authorization.
    if ([string]::IsNullOrWhiteSpace($RelativePath) -or
        $RelativePath -notmatch '^[A-Za-z0-9_./-]+$' -or
        $RelativePath.StartsWith('/') -or
        @($RelativePath.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -gt 0) {
        throw 'PREP_REFERENCE_PATH'
    }
    $path = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'PREP_REFERENCE_MISSING' }
    $item = Get-Item -LiteralPath $path
    # Reject a link at any segment before reading a referenced file.
    $current = $item
    while ($null -ne $current -and $current.FullName -ne $root) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'PREP_REFERENCE_LINK'
        }
        $current = if ($current -is [IO.FileInfo]) { $current.Directory } else { $current.Parent }
    }
    if ($item.Length -gt 2MB) { throw 'PREP_REFERENCE_OVERSIZED' }
    return $utf8.GetString([IO.File]::ReadAllBytes($path))
}

function Assert-Fields($Value, [string[]]$Fields) {
    if ($Value -isnot [System.Collections.IDictionary] -or
        (($Value.Keys | Sort-Object) -join '|') -cne (($Fields | Sort-Object) -join '|')) {
        throw 'PREP_FIELDS'
    }
}

function Assert-Set($Actual, $Expected, [string]$Code) {
    if ((@($Actual | Sort-Object -Unique) -join '|') -cne
        (@($Expected | Sort-Object -Unique) -join '|')) { throw $Code }
}

function Assert-Preparation($Plan, [string]$StateText, [string]$TrailText) {
    Assert-Fields $Plan @('version', 'purpose', 'authority', 'trail', 'executionAuthorized',
        'newAcceptances', 'baseline', 'guide', 'inputs', 'stages')
    if ($Plan.version -ne 1 -or $Plan.purpose -cne 'PREPARACAO_OFFLINE_NAO_AUTORIZA_EXECUCAO' -or
        $Plan.executionAuthorized -isnot [bool] -or $Plan.executionAuthorized -or
        $Plan.newAcceptances -ne 0 -or $Plan.authority -cne 'STATES.md' -or
        $Plan.trail -cne 'TRILHA_CONCLUSAO_POR_MODELO.md') { throw 'PREP_NOT_AUTHORIZATION' }
    Assert-Fields $Plan.baseline @('construction', 'acceptances', 'checkpoint')
    if ($Plan.baseline.checkpoint -cnotmatch '^[0-9]{4}$') { throw 'PREP_BASELINE_CHECKPOINT' }
    $matrix = (Read-LocalText 'docs/catalogos/campanhas-integrais/matriz-45-unidades.json') | ConvertFrom-Json -AsHashtable
    if ($Plan.baseline.construction -cne $matrix.construction) { throw 'PREP_CONSTRUCTION_DRIFT' }
    $checkboxes = @([regex]::Matches($StateText, '(?m)^\s*- \[([ xX])\] \*\*V2-'))
    $done = @($checkboxes | Where-Object { $_.Groups[1].Value -ne ' ' }).Count
    if ($Plan.baseline.acceptances -cne "$done/$($checkboxes.Count)") { throw 'PREP_ACCEPTANCE_DRIFT' }
    $null = Read-LocalText $Plan.guide
    if ($Plan.inputs -isnot [array] -or $Plan.inputs.Count -ne 9) { throw 'PREP_INPUT_COUNT' }
    Assert-Set @($Plan.inputs.id) @('G01','G02','G03','G04','G05','G06','G07','G08','FEED') 'PREP_INPUT_SET'
    foreach ($inputRow in $Plan.inputs) {
        Assert-Fields $inputRow @('id','role','reference')
        if ([string]::IsNullOrWhiteSpace($inputRow.role)) { throw 'PREP_INPUT_ROLE' }
        $null = Read-LocalText $inputRow.reference
    }
    $expected = @(1..33 | ForEach-Object { 'P{0:D2}' -f $_ })
    if ($Plan.stages -isnot [array] -or $Plan.stages.Count -ne 33 -or
        ($Plan.stages.id -join '|') -cne ($expected -join '|')) { throw 'PREP_STAGE_SET_OR_ORDER' }
    $tableIds = @([regex]::Matches($TrailText, '(?m)^\| \*\*(P[0-9]{2}) —') |
        ForEach-Object { $_.Groups[1].Value })
    if (($tableIds -join '|') -cne ($expected -join '|')) { throw 'PREP_TRAIL_STAGE_DRIFT' }
    $covered = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($stage in $Plan.stages) {
        Assert-Fields $stage @('id','package','scope','dependsOn','conditional','inputs','units','reuse','prepare','exit')
        foreach ($field in @('package','scope','prepare','exit')) {
            if ($stage[$field] -isnot [string] -or [string]::IsNullOrWhiteSpace($stage[$field])) {
                throw 'PREP_STAGE_DESCRIPTION'
            }
        }
        foreach ($field in @('dependsOn','conditional','inputs','units','reuse')) {
            if ($stage[$field] -isnot [array]) { throw 'PREP_STAGE_ARRAY' }
        }
        $edges = @($stage.dependsOn)
        foreach ($edge in $stage.conditional) {
            Assert-Fields $edge @('id','when')
            if ($edge.when -isnot [string] -or [string]::IsNullOrWhiteSpace($edge.when)) {
                throw 'PREP_CONDITIONAL_REASON'
            }
            $edges += $edge.id
        }
        if (@($edges | Sort-Object -Unique).Count -ne $edges.Count) { throw 'PREP_DUPLICATE_EDGE' }
        foreach ($edgeId in $edges) {
            if ($edgeId -cnotin $expected) { throw 'PREP_DEPENDENCY_UNKNOWN' }
            # All current P edges point backwards; rejecting forward/self edges also rejects cycles.
            if ([int]$edgeId.Substring(1) -ge [int]$stage.id.Substring(1)) { throw 'PREP_DEPENDENCY_CYCLE_OR_FORWARD' }
        }
        foreach ($inputId in $stage.inputs) {
            if ($inputId -cnotin $Plan.inputs.id) { throw 'PREP_INPUT_UNKNOWN' }
        }
        if ($stage.units.Count -eq 0 -or $stage.reuse.Count -eq 0) { throw 'PREP_STAGE_EMPTY' }
        foreach ($unit in $stage.units) {
            if ($unit -cnotmatch '^V2-[0-9]{3}[a-z]?(?:/[0-9]+)?$') { throw 'PREP_UNIT_FORMAT' }
            $null = $covered.Add($unit)
        }
        foreach ($reference in $stage.reuse) { $null = Read-LocalText $reference }
    }
    $openIds = @([regex]::Matches($StateText, '(?m)^\s*- \[ \] \*\*(V2-[0-9]{3}[a-z]?(?:/[0-9]+)?) —') |
        ForEach-Object { $_.Groups[1].Value })
    if (@($openIds | Sort-Object -Unique).Count -ne $openIds.Count) { throw 'PREP_DUPLICATE_CANONICAL_ID' }
    Assert-Set @($covered) $openIds 'PREP_OPEN_ID_COVERAGE'
    # Guard the most consequential independent branches; this is not a physical admission engine.
    $byId = @{}
    foreach ($stage in $Plan.stages) { $byId[$stage.id] = $stage }
    if ('P09' -in $byId.P10.dependsOn -or 'P11' -in $byId.P13.dependsOn -or
        'P23' -in $byId.P24.dependsOn -or 'P04' -notin $byId.P05.dependsOn -or
        'P07' -notin $byId.P08.dependsOn -or 'P31' -notin $byId.P32.dependsOn -or
        'P32' -notin $byId.P33.dependsOn) { throw 'PREP_MATERIAL_DEPENDENCY' }
    return [ordered]@{ stages = 33; openIds = $openIds.Count; inputs = 9; executionAuthorized = $false }
}

$planText = Read-LocalText 'docs/catalogos/preparacao-trilha/plano.json'
$plan = $planText | ConvertFrom-Json -AsHashtable
$stateText = Read-LocalText 'STATES.md'
$trailText = Read-LocalText 'TRILHA_CONCLUSAO_POR_MODELO.md'
$summary = Assert-Preparation $plan $stateText $trailText

if ($SelfTest) {
    $cases = @(
        @{ name='missing stage'; code='PREP_STAGE_SET_OR_ORDER'; mutate={ param($p) $p.stages = @($p.stages | Where-Object id -NE 'P33') } },
        @{ name='duplicate stage'; code='PREP_STAGE_SET_OR_ORDER'; mutate={ param($p) $p.stages[32].id = 'P32' } },
        @{ name='cycle'; code='PREP_DEPENDENCY_CYCLE_OR_FORWARD'; mutate={ param($p) $p.stages[0].dependsOn = @('P04') } },
        @{ name='self edge'; code='PREP_DEPENDENCY_CYCLE_OR_FORWARD'; mutate={ param($p) $p.stages[3].dependsOn = @('P04') } },
        @{ name='unknown dependency'; code='PREP_DEPENDENCY_UNKNOWN'; mutate={ param($p) $p.stages[3].dependsOn = @('P99') } },
        @{ name='condition missing'; code='PREP_CONDITIONAL_REASON'; mutate={ param($p) $p.stages[2].conditional[0].when = '' } },
        @{ name='edge duplicated'; code='PREP_DUPLICATE_EDGE'; mutate={ param($p) $p.stages[3].dependsOn = @('P03','P03') } },
        @{ name='implicit grant'; code='PREP_NOT_AUTHORIZATION'; mutate={ param($p) $p.executionAuthorized = $true } },
        @{ name='string grant'; code='PREP_NOT_AUTHORIZATION'; mutate={ param($p) $p.executionAuthorized = 'false' } },
        @{ name='invented acceptance'; code='PREP_NOT_AUTHORIZATION'; mutate={ param($p) $p.newAcceptances = 1 } },
        @{ name='stale construction'; code='PREP_CONSTRUCTION_DRIFT'; mutate={ param($p) $p.baseline.construction = '40/45' } },
        @{ name='stale acceptance'; code='PREP_ACCEPTANCE_DRIFT'; mutate={ param($p) $p.baseline.acceptances = '68/115' } },
        @{ name='missing gate'; code='PREP_INPUT_COUNT'; mutate={ param($p) $p.inputs = @($p.inputs | Where-Object id -NE 'G07') } },
        @{ name='unknown gate'; code='PREP_INPUT_UNKNOWN'; mutate={ param($p) $p.stages[3].inputs = @('G99') } },
        @{ name='missing reference'; code='PREP_REFERENCE_MISSING'; mutate={ param($p) $p.stages[0].reuse = @('docs/catalogos/preparacao-trilha/nonexistent-proof.json') } },
        @{ name='path escape'; code='PREP_REFERENCE_PATH'; mutate={ param($p) $p.stages[0].reuse = @('../outside.json') } },
        @{ name='absolute path'; code='PREP_REFERENCE_PATH'; mutate={ param($p) $p.stages[0].reuse = @('C:/outside.json') } },
        @{ name='remote path'; code='PREP_REFERENCE_PATH'; mutate={ param($p) $p.stages[0].reuse = @('https://example.invalid/proof') } },
        @{ name='unknown command field'; code='PREP_FIELDS'; mutate={ param($p) $p.stages[0].command = 'not-executed' } },
        @{ name='unmapped unit'; code='PREP_OPEN_ID_COVERAGE'; mutate={ param($p) $p.stages[32].units = @('V2-999') } },
        @{ name='false gate on fact'; code='PREP_MATERIAL_DEPENDENCY'; mutate={ param($p) $p.stages[23].dependsOn += 'P23' } },
        @{ name='missing P04 predecessor'; code='PREP_MATERIAL_DEPENDENCY'; mutate={ param($p) $p.stages[4].dependsOn = @() } }
    )
    foreach ($case in $cases) {
        $copy = $planText | ConvertFrom-Json -AsHashtable
        & $case.mutate $copy
        $observed = ''
        try { $null = Assert-Preparation $copy $stateText $trailText }
        catch { $observed = $_.Exception.Message }
        if ($observed -cne $case.code) { throw "PREP_SELFTEST_FAILED: $($case.name); expected=$($case.code); observed=$observed" }
    }
    foreach ($pair in @(
        @{ state=$stateText.Replace('- [ ] **V2-040 —', '- [x] **V2-040 —'); trail=$trailText; code='PREP_ACCEPTANCE_DRIFT' },
        @{ state=$stateText; trail=$trailText.Replace('| **P33 —', '| **P34 —'); code='PREP_TRAIL_STAGE_DRIFT' }
    )) {
        $observed = ''
        try { $null = Assert-Preparation $plan $pair.state $pair.trail }
        catch { $observed = $_.Exception.Message }
        if ($observed -cne $pair.code) { throw 'PREP_SOURCE_DRIFT_SELFTEST_FAILED' }
    }
    Write-Output "PASS: self-test 1 positive + $($cases.Count + 2) negative cases; mutations in memory only."
}
Write-Output "PASS: preparation stages=$($summary.stages), openIds=$($summary.openIds), inputPackages=$($summary.inputs); executionAuthorized=false; no SQL/network/Maven."

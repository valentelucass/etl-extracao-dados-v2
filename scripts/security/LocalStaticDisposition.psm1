#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'LocalStaticAnalysis.psm1')

function Assert-DispositionKeys {
    param($Value, [string[]]$Expected)
    if ($Value -isnot [Collections.IDictionary] -or $Value.Count -ne $Expected.Count -or
        @($Value.Keys | Where-Object { $_ -cnotin $Expected }).Count) { throw 'DISPOSITION_JSON_SHAPE' }
}

function Assert-DispositionJsonProperties {
    param([System.Text.Json.JsonElement]$Element)
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($property in $Element.EnumerateObject()) {
            if (-not $names.Add($property.Name)) { throw 'DISPOSITION_JSON_DUPLICATE' }
            Assert-DispositionJsonProperties $property.Value
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $Element.EnumerateArray()) { Assert-DispositionJsonProperties $item }
    }
}

function Read-DispositionJson {
    param([string]$Path, [int]$MaximumBytes = 8388608)
    Assert-StaticAnalysisPath $Path
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-Item -LiteralPath $Path).Length -gt $MaximumBytes) { throw 'DISPOSITION_JSON_SIZE_OR_MISSING' }
    try {
        $text = [Text.UTF8Encoding]::new($false, $true).GetString([IO.File]::ReadAllBytes($Path))
        $options = [System.Text.Json.JsonDocumentOptions]::new()
        $options.MaxDepth = 24
        $document = [System.Text.Json.JsonDocument]::Parse($text, $options)
        try { Assert-DispositionJsonProperties $document.RootElement } finally { $document.Dispose() }
        return ,($text | ConvertFrom-Json -AsHashtable -Depth 24 -NoEnumerate)
    } catch {
        if ($_.Exception.Message -cmatch '^DISPOSITION_[A-Z0-9_]+$') { throw }
        throw 'DISPOSITION_JSON_INVALID'
    }
}

function Resolve-DispositionPath {
    param([string]$Root, [string]$Path, [switch]$Relative, [switch]$TargetOnly)
    if ([string]::IsNullOrWhiteSpace($Path)) { throw 'DISPOSITION_PATH' }
    if ($Relative -and ($Path -cnotmatch '^[A-Za-z0-9_$/.-]+$' -or
        [IO.Path]::IsPathRooted($Path) -or @($Path.Split('/') | Where-Object { $_ -cin @('', '.', '..') }).Count)) {
        throw 'DISPOSITION_RELATIVE_PATH'
    }
    $full = [IO.Path]::GetFullPath($(if ([IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $Root $Path }))
    $boundary = if ($TargetOnly) { Join-Path $Root 'target' } else { $Root }
    if (-not $full.StartsWith($boundary + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'DISPOSITION_PATH_SCOPE'
    }
    if (-not (Test-Path -LiteralPath $full)) { throw 'DISPOSITION_PATH_MISSING' }
    Assert-StaticAnalysisPath $full
    return $full
}

function Get-DispositionPin {
    param([string]$Root, [string]$Path)
    return [ordered]@{
        path = [IO.Path]::GetRelativePath($Root, $Path).Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

function Assert-DispositionHash {
    param([string]$Path, $Hash, [string]$Reason)
    if ($Hash -isnot [string] -or $Hash -cnotmatch '^[0-9a-f]{64}$' -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Hash) { throw $Reason }
}

function Assert-DispositionText {
    param($Value, [int]$Maximum, [string]$Reason)
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value) -or $Value.Length -gt $Maximum -or
        $Value -match '[\x00-\x08\x0B\x0C\x0E-\x1F]') { throw $Reason }
}

function Get-DispositionFindingKey {
    param($Finding)
    return ($Finding.path + '|' + $Finding.line + '|' + $Finding.rule + '|' + $Finding.method)
}

function Assert-DispositionDeadline {
    param([Diagnostics.Stopwatch]$Timer)
    if ($Timer.Elapsed.TotalSeconds -gt 120) { throw 'DISPOSITION_DEADLINE_EXCEEDED' }
}

function Assert-DispositionCounter {
    param($Value, [long]$Maximum = 200000)
    if ($Value -isnot [long] -or $Value -lt 0 -or $Value -gt $Maximum) { throw 'DISPOSITION_COUNTER_TYPE_OR_BOUND' }
}

function Read-DispositionTestReport {
    param([string]$Path)
    try { $xml = Read-StaticAnalysisXml $Path } catch { throw 'DISPOSITION_TEST_XML_INVALID' }
    $suite = $xml.DocumentElement
    if ($suite.LocalName -cne 'testsuite' -or $suite.NamespaceURI -cne '' -or
        $suite.GetAttribute('name') -cnotmatch '^[A-Za-z_$][A-Za-z0-9_$.]+$' -or
        @($suite.SelectNodes('*')).Where({ $_.LocalName -cnotin @('properties','testcase','system-out','system-err') }).Count -or
        @($xml.SelectNodes('//*')).Where({ $_.NamespaceURI -cne '' }).Count) { throw 'DISPOSITION_TEST_REPORT_SHAPE' }
    $counts = @{}
    foreach ($name in @('tests','failures','errors','skipped')) {
        $value = $suite.GetAttribute($name)
        if ($value -cnotmatch '^(0|[1-9][0-9]{0,5})$') { throw 'DISPOSITION_TEST_REPORT_COUNTS' }
        $counts[$name] = [int]$value
    }
    $cases = @($suite.SelectNodes('testcase'))
    $observed = @{ tests=$cases.Count; failures=0; errors=0; skipped=0 }
    $result = [Collections.Generic.List[object]]::new()
    foreach ($case in $cases) {
        $className = $case.GetAttribute('classname')
        $name = $case.GetAttribute('name')
        if ($className -cne $suite.GetAttribute('name') -or [string]::IsNullOrWhiteSpace($name) -or
            $name.Length -gt 2048 -or
            @($case.SelectNodes('*')).Where({ $_.LocalName -cnotin @('failure','error','skipped','system-out','system-err') }).Count) {
            throw 'DISPOSITION_TEST_REPORT_CASE'
        }
        $outcomes = @($case.SelectNodes('failure|error|skipped'))
        if ($outcomes.Count -gt 1) { throw 'DISPOSITION_TEST_REPORT_CASE' }
        $outcome = if ($outcomes.Count -eq 0) { 'PASS' } else { $outcomes[0].LocalName }
        if ($outcome -ceq 'failure') { $observed.failures++ }
        if ($outcome -ceq 'error') { $observed.errors++ }
        if ($outcome -ceq 'skipped') { $observed.skipped++ }
        # Dynamic factories can emit equal display names; retain every occurrence and outcome.
        $result.Add([ordered]@{ className=$className; name=$name; occurrence=$result.Count+1; outcome=$outcome; report=$Path })
    }
    foreach ($name in @('tests','failures','errors','skipped')) {
        if ($observed[$name] -ne $counts[$name]) { throw 'DISPOSITION_TEST_REPORT_COUNTS' }
    }
    if ($counts.failures -ne 0 -or $counts.errors -ne 0) { throw 'DISPOSITION_TEST_REPORT_FAILED' }
    return [ordered]@{ name=$suite.GetAttribute('name'); counts=$counts; cases=$result.ToArray() }
}

function Get-LocalStaticDisposition {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$RepositoryRoot,
        [Parameter(Mandatory)][string]$Catalog,
        [Parameter(Mandatory)][string]$PmdDirectory,
        [Parameter(Mandatory)][string]$TestSourceRoot,
        [Parameter(Mandatory)][string[]]$TestReportDirectories
    )
    $timer=[Diagnostics.Stopwatch]::StartNew()
    $root = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([char]92,[char]47)
    Assert-StaticAnalysisPath $root
    $catalogPath = Resolve-DispositionPath $root $Catalog
    $pmd = Resolve-DispositionPath $root $PmdDirectory -TargetOnly
    $testRoot = Resolve-DispositionPath $root $TestSourceRoot -TargetOnly
    $source = Resolve-DispositionPath $root 'src/main/java' -Relative
    $executedSource = Resolve-DispositionPath $testRoot 'src/main/java' -Relative
    if ($TestReportDirectories.Count -lt 1 -or $TestReportDirectories.Count -gt 4) { throw 'DISPOSITION_TEST_DIRECTORY_COUNT' }
    $pins = [Collections.Generic.List[object]]::new()
    $pins.Add((Get-DispositionPin $root $catalogPath))
    $catalogData = Read-DispositionJson $catalogPath
    Assert-DispositionKeys $catalogData @('schema','nominalSecurityAcceptance','releaseAcceptance','findings')
    if ($catalogData.schema -cne 'local-pmd-dispositions-v1' -or
        $catalogData.nominalSecurityAcceptance -isnot [bool] -or $catalogData.nominalSecurityAcceptance -or
        $catalogData.releaseAcceptance -isnot [bool] -or $catalogData.releaseAcceptance -or
        $catalogData.findings -isnot [array] -or $catalogData.findings.Count -lt 1 -or $catalogData.findings.Count -gt 10000) {
        throw 'DISPOSITION_CATALOG_CONTRACT'
    }
    $files = @{}
    foreach ($name in @('result.json','source-inventory.json','command.json','execution.json','ruleset.xml','benchmark.txt','build/pmd.xml')) {
        $files[$name] = Resolve-DispositionPath $root (Join-Path $pmd $name) -TargetOnly
        $pins.Add((Get-DispositionPin $root $files[$name]))
    }
    $raw = Read-DispositionJson $files['result.json']
    Assert-DispositionKeys $raw @('state','sourceFiles','parserCalls','ruleExecutions','findings','findingCount','processingErrors','nominalSecurityAcceptance','releaseAcceptance','offline','network','sql','ownedProcessesRemaining','elapsedSeconds')
    foreach ($counter in @('sourceFiles','parserCalls','findingCount','processingErrors','ownedProcessesRemaining')) {
        Assert-DispositionCounter $raw[$counter]
    }
    if ($raw.state -cne 'FINDINGS_OPEN' -or $raw.processingErrors -ne 0 -or $raw.ownedProcessesRemaining -ne 0 -or
        $raw.nominalSecurityAcceptance -isnot [bool] -or $raw.nominalSecurityAcceptance -or
        $raw.releaseAcceptance -isnot [bool] -or $raw.releaseAcceptance -or
        $raw.offline -isnot [bool] -or -not $raw.offline -or $raw.network -isnot [bool] -or $raw.network -or
        $raw.sql -isnot [bool] -or $raw.sql -or $raw.findings -isnot [array] -or
        $raw.findingCount -ne $raw.findings.Count -or $raw.findingCount -lt 1) { throw 'DISPOSITION_RAW_RESULT_CONTRACT' }
    $command = Read-DispositionJson $files['command.json']
    Assert-DispositionKeys $command @('arguments','sourceDirectory','rules','sourceFiles','rulesetSha256')
    Assert-DispositionCounter $command.sourceFiles 10000
    $execution = Read-DispositionJson $files['execution.json']
    Assert-DispositionKeys $execution @('exit','timedOut','elapsedSeconds','processExited')
    if ($execution.exit -ne 0 -or $execution.timedOut -isnot [bool] -or $execution.timedOut -or
        $execution.processExited -isnot [bool] -or -not $execution.processExited) { throw 'DISPOSITION_ANALYZER_NOT_COMPLETED' }
    $analyzedSource = Resolve-DispositionPath $root $command.sourceDirectory
    $inventory = Read-DispositionJson $files['source-inventory.json']
    if ($inventory -isnot [array] -or $inventory.Count -lt 1 -or $inventory.Count -gt 10000 -or
        $raw.sourceFiles -ne $inventory.Count -or $raw.parserCalls -ne $inventory.Count -or $command.sourceFiles -ne $inventory.Count) {
        throw 'DISPOSITION_INVENTORY_COUNT'
    }
    $inventoryByPath = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $inventory) {
        Assert-DispositionDeadline $timer
        Assert-DispositionKeys $entry @('path','sha256')
        if ($entry.path -isnot [string] -or -not $entry.path.EndsWith('.java',[StringComparison]::Ordinal) -or
            -not $inventoryByPath.TryAdd($entry.path, $entry.sha256)) { throw 'DISPOSITION_INVENTORY_DUPLICATE_OR_PATH' }
        $currentFile = Resolve-DispositionPath $source $entry.path -Relative
        $analyzedFile = Resolve-DispositionPath $analyzedSource $entry.path -Relative
        $executedFile = Resolve-DispositionPath $executedSource $entry.path -Relative
        Assert-DispositionHash $currentFile $entry.sha256 'DISPOSITION_SOURCE_DRIFT'
        Assert-DispositionHash $analyzedFile $entry.sha256 'DISPOSITION_ANALYZED_SOURCE_DRIFT'
        Assert-DispositionHash $executedFile $entry.sha256 'DISPOSITION_EXECUTED_MAIN_SOURCE_DRIFT'
        $pins.Add((Get-DispositionPin $root $currentFile))
        if ($analyzedFile -cne $currentFile) { $pins.Add((Get-DispositionPin $root $analyzedFile)) }
        $pins.Add((Get-DispositionPin $root $executedFile))
    }
    foreach ($directory in @($source,$analyzedSource,$executedSource)) {
        $actualSources = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Force -Filter '*.java')
        if ($actualSources.Count -ne $inventory.Count) { throw 'DISPOSITION_INVENTORY_SET' }
        foreach ($actual in $actualSources) {
            Assert-StaticAnalysisPath $actual.FullName
            if (-not $inventoryByPath.ContainsKey([IO.Path]::GetRelativePath($directory,$actual.FullName).Replace('\','/'))) {
                throw 'DISPOSITION_INVENTORY_SET'
            }
        }
    }
    $rulesPath = Resolve-DispositionPath $root 'scripts/security/pmd-security.xml' -Relative
    $pins.Add((Get-DispositionPin $root $rulesPath))
    $rules = @(Get-StaticAnalysisRules $rulesPath)
    $null = Get-StaticAnalysisRules $files['ruleset.xml']
    Assert-DispositionHash $rulesPath $command.rulesetSha256 'DISPOSITION_RULESET_DRIFT'
    Assert-DispositionHash $files['ruleset.xml'] $command.rulesetSha256 'DISPOSITION_RULESET_DRIFT'
    if ($command.rules -isnot [array] -or ($command.rules -join '|') -cne ($rules -join '|')) { throw 'DISPOSITION_RULESET_EXECUTION' }
    try {
        $pmdXml=Read-StaticAnalysisXml $files['build/pmd.xml']
        if (@($pmdXml.SelectNodes('//*')).Where({$_.NamespaceURI -cne 'http://pmd.sourceforge.net/report/2.0.0'}).Count) {
            throw 'DISPOSITION_PMD_XML_NAMESPACE'
        }
        $observed = Read-LocalStaticAnalysisResult -Report $files['build/pmd.xml'] -Benchmark $files['benchmark.txt'] -SourceDirectory $analyzedSource -ExpectedSourceCount $inventory.Count -RuleNames $rules
    } catch {
        if ($_.Exception.Message -cmatch '^(DISPOSITION|STATIC)_[A-Z0-9_]+$') { throw }
        throw 'DISPOSITION_PMD_XML_INVALID'
    }
    if ($observed.findingCount -ne $raw.findingCount -or $raw.ruleExecutions -isnot [array] -or $raw.ruleExecutions.Count -ne $rules.Count) {
        throw 'DISPOSITION_RAW_REPORT_MISMATCH'
    }
    $seenRules = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $raw.ruleExecutions) {
        Assert-DispositionKeys $entry @('rule','sourceCalls')
        Assert-DispositionCounter $entry.sourceCalls 10000
        if ($entry.rule -cnotin $rules -or -not $seenRules.Add($entry.rule) -or $entry.sourceCalls -ne $inventory.Count) {
            throw 'DISPOSITION_RULESET_EXECUTION'
        }
    }
    $rawKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($finding in $raw.findings) {
        Assert-DispositionKeys $finding @('path','line','rule','method')
        if (-not $inventoryByPath.ContainsKey($finding.path) -or $finding.rule -cnotin $rules -or
            $finding.line -isnot [long] -or $finding.line -lt 1 -or $finding.method -isnot [string] -or
            -not $rawKeys.Add((Get-DispositionFindingKey $finding))) { throw 'DISPOSITION_RAW_FINDING_CONTRACT' }
    }
    $observedKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($finding in $observed.findings) {
        if (-not $observedKeys.Add((Get-DispositionFindingKey $finding))) { throw 'DISPOSITION_RAW_REPORT_DUPLICATE' }
    }
    if (-not $rawKeys.SetEquals($observedKeys)) { throw 'DISPOSITION_RAW_REPORT_MISMATCH' }
    $reports = [Collections.Generic.List[object]]::new()
    $reportPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $reportClasses = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $reportBytes = 0L
    $reportCases = 0L
    foreach ($directory in $TestReportDirectories) {
        $resolved = Resolve-DispositionPath $root $directory -TargetOnly
        if (-not $resolved.Equals((Join-Path $testRoot 'target/surefire-reports'),[StringComparison]::OrdinalIgnoreCase) -and
            -not $resolved.Equals((Join-Path $testRoot 'target/failsafe-reports'),[StringComparison]::OrdinalIgnoreCase)) {
            throw 'DISPOSITION_TEST_REPORT_BUILD_SCOPE'
        }
        $xmlFiles = @(Get-ChildItem -LiteralPath $resolved -File -Filter 'TEST-*.xml')
        if ($xmlFiles.Count -lt 1 -or $xmlFiles.Count -gt 2000) { throw 'DISPOSITION_TEST_REPORT_SET' }
        foreach ($file in $xmlFiles) {
            Assert-DispositionDeadline $timer
            Assert-StaticAnalysisPath $file.FullName
            $reportBytes += $file.Length
            if ($file.Length -gt 8388608 -or $reportBytes -gt 134217728 -or
                -not $reportPaths.Add($file.FullName)) { throw 'DISPOSITION_TEST_REPORT_BOUND_OR_DUPLICATE' }
            $report = Read-DispositionTestReport $file.FullName
            $reportCases += $report.cases.Count
            if ($reportCases -gt 200000 -or -not $reportClasses.Add($report.name)) { throw 'DISPOSITION_TEST_REPORT_BOUND_OR_DUPLICATE' }
            $reports.Add($report)
            $pins.Add((Get-DispositionPin $root $file.FullName))
        }
    }
    $catalogKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $accepted = [Collections.Generic.List[object]]::new()
    $classifications=@('SANITIZACAO_DE_DADOS','TRADUCAO_TIPADA','FALSO_POSITIVO_OWNERSHIP_TRANSFERIDO',
        'FALSO_POSITIVO_HANDLE_EMPRESTADO','FALSO_POSITIVO_CAUSA_PRESERVADA',
        'FALSO_POSITIVO_CAUSA_PRESERVADA_COM_BUG_ADJACENTE_CORRIGIDO','DESEMPACOTAMENTO_E_SANITIZACAO')
    foreach ($finding in $catalogData.findings) {
        Assert-DispositionDeadline $timer
        Assert-DispositionKeys $finding @('id','path','line','rule','method','sourceSha256','classification','rationale','tests','proofLimit')
        if ($finding.id -isnot [string] -or $finding.id -cnotmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$' -or -not $ids.Add($finding.id) -or
            $finding.path -isnot [string] -or -not $finding.path.StartsWith('src/main/java/',[StringComparison]::Ordinal) -or
            $finding.line -isnot [long] -or $finding.line -lt 1 -or $finding.rule -cnotin $rules -or
            $finding.method -isnot [string] -or $finding.method -cnotmatch '^[A-Za-z_$][A-Za-z0-9_$]*$' -or
            $finding.classification -isnot [string] -or $finding.classification -cnotin $classifications) {
            throw 'DISPOSITION_FINDING_CONTRACT'
        }
        Assert-DispositionText $finding.rationale 8000 'DISPOSITION_RATIONALE'
        Assert-DispositionText $finding.proofLimit 8000 'DISPOSITION_PROOF_LIMIT'
        $relativeSource = $finding.path.Substring('src/main/java/'.Length)
        $sourcePath = Resolve-DispositionPath $root $finding.path -Relative
        if (-not $inventoryByPath.ContainsKey($relativeSource) -or $inventoryByPath[$relativeSource] -cne $finding.sourceSha256) {
            throw 'DISPOSITION_FINDING_SOURCE_HASH'
        }
        Assert-DispositionHash $sourcePath $finding.sourceSha256 'DISPOSITION_FINDING_SOURCE_HASH'
        $key = Get-DispositionFindingKey @{path=$relativeSource;line=$finding.line;rule=$finding.rule;method=$finding.method}
        if (-not $catalogKeys.Add($key)) { throw 'DISPOSITION_FINDING_DUPLICATE' }
        if (-not $rawKeys.Contains($key)) { throw 'DISPOSITION_FINDING_NOT_IN_RAW' }
        if ($finding.tests -isnot [array] -or $finding.tests.Count -lt 1 -or $finding.tests.Count -gt 30) { throw 'DISPOSITION_TEST_BINDING_COUNT' }
        $testKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $evidence = [Collections.Generic.List[object]]::new()
        foreach ($test in $finding.tests) {
            Assert-DispositionDeadline $timer
            Assert-DispositionKeys $test @('path','sha256','className','testCase')
            if ($test.className -isnot [string] -or $test.className -cnotmatch '^[A-Za-z_$][A-Za-z0-9_$.]+$' -or
                $test.testCase -isnot [string] -or $test.testCase -cnotmatch '^[A-Za-z_$][A-Za-z0-9_$]*$' -or
                -not $testKeys.Add($test.className+'|'+$test.testCase)) { throw 'DISPOSITION_TEST_BINDING_CONTRACT' }
            $classPath = 'src/test/java/' + (($test.className -split '\$')[0].Replace('.','/')) + '.java'
            if ($test.path -cne $classPath) { throw 'DISPOSITION_TEST_CLASS_PATH' }
            $currentTest = Resolve-DispositionPath $root $test.path -Relative
            $executedTest = Resolve-DispositionPath $testRoot $test.path -Relative
            Assert-DispositionHash $currentTest $test.sha256 'DISPOSITION_TEST_SOURCE_HASH'
            Assert-DispositionHash $executedTest $test.sha256 'DISPOSITION_EXECUTED_TEST_SOURCE_HASH'
            $pins.Add((Get-DispositionPin $root $currentTest))
            $pins.Add((Get-DispositionPin $root $executedTest))
            $matching = @($reports | Where-Object { $_.name -ceq $test.className } | ForEach-Object cases | Where-Object {
                $_.name -ceq $test.testCase -or $_.name.StartsWith($test.testCase+'(',[StringComparison]::Ordinal) -or
                $_.name.StartsWith($test.testCase+'[',[StringComparison]::Ordinal)
            })
            if ($matching.Count -eq 0) { throw 'DISPOSITION_TEST_EVIDENCE_MISSING' }
            if (@($matching | Where-Object { $_.outcome -cne 'PASS' }).Count) { throw 'DISPOSITION_TEST_NOT_PASSED' }
            $evidence.Add([ordered]@{className=$test.className;testCase=$test.testCase;invocations=$matching.Count;sourceSha256=$test.sha256;reports=@($matching.report | Sort-Object -Unique | ForEach-Object { [IO.Path]::GetRelativePath($root,$_).Replace('\','/') })})
        }
        $accepted.Add([ordered]@{id=$finding.id;path=$finding.path;line=$finding.line;rule=$finding.rule;method=$finding.method;sourceSha256=$finding.sourceSha256;classification=$finding.classification;tests=$evidence.ToArray();proofLimit=$finding.proofLimit})
    }
    if (-not $catalogKeys.SetEquals($rawKeys)) { throw 'DISPOSITION_FINDING_SET_INCOMPLETE' }
    $uniquePins = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($pin in $pins) {
        Assert-DispositionDeadline $timer
        if ($uniquePins.ContainsKey($pin.path) -and $uniquePins[$pin.path] -cne $pin.sha256) { throw 'DISPOSITION_INPUT_DRIFT' }
        $uniquePins[$pin.path]=$pin.sha256
        $path=Resolve-DispositionPath $root $pin.path -Relative
        Assert-DispositionHash $path $pin.sha256 'DISPOSITION_INPUT_DRIFT'
    }
    return [ordered]@{
        schema='local-pmd-disposition-result-v1';state='PASS_LOCAL_REVIEWED_FINDINGS';rawState='FINDINGS_OPEN';rawGateExit=1
        rawGateExitBasis='Existing runner returns exit1 for a nonempty FINDINGS_OPEN result; analyzer process exit0 was verified.'
        sourceFiles=$inventory.Count;rules=$rules.Count;rawFindings=$raw.findingCount;reviewedFindings=$accepted.Count
        reportFiles=$reportPaths.Count;reportCases=$reportCases;dispositions=$accepted.ToArray()
        evidencePins=@($uniquePins.Keys | Sort-Object -CaseSensitive | ForEach-Object { [ordered]@{path=$_;sha256=$uniquePins[$_]} })
        nominalSecurityAcceptance=$false;releaseAcceptance=$false;rawFindingsSuppressed=0;network=$false;sql=$false
        proofLimit='Exact catalog, code hashes and XML case results checked locally. This is not human approval, full SAST, source authenticity or a release gate acceptance.'
    }
}

Export-ModuleMember -Function Get-LocalStaticDisposition

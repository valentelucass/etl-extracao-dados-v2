#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-StaticAnalysisXml {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'STATIC_REPORT_MISSING' }
    if ((Get-Item -LiteralPath $Path).Length -gt 33554432) { throw 'STATIC_REPORT_SIZE' }
    $settings = [Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver = $null
    $settings.MaxCharactersInDocument = 33554432
    $reader = [Xml.XmlReader]::Create($Path, $settings)
    try {
        $document = [Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        return ,$document
    } finally { $reader.Dispose() }
}

function Assert-StaticAnalysisPath {
    param([Parameter(Mandatory)][string]$Path)
    $node = Get-Item -LiteralPath $Path -Force
    while ($null -ne $node) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'STATIC_REPARSE_POINT'
        }
        $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
    }
}

function Get-StaticAnalysisRules {
    param([Parameter(Mandatory)][string]$Ruleset)
    $document = Read-StaticAnalysisXml $Ruleset
    $expected = @(
        'category/java/security.xml/HardCodedCryptoKey',
        'category/java/security.xml/InsecureCryptoIv',
        'category/java/errorprone.xml/CloseResource',
        'category/java/bestpractices.xml/PreserveStackTrace',
        'category/java/errorprone.xml/AvoidCatchingThrowable',
        'category/java/errorprone.xml/DoNotThrowExceptionInFinally',
        'category/java/multithreading.xml/DontCallThreadRun',
        'category/java/multithreading.xml/DoubleCheckedLocking',
        'category/java/multithreading.xml/NonThreadSafeSingleton',
        'category/java/multithreading.xml/UnsynchronizedStaticFormatter'
    )
    $nodes = @($document.SelectNodes('/*[local-name()="ruleset"]/*[local-name()="rule"]'))
    $actual = @($nodes | ForEach-Object { $_.GetAttribute('ref') })
    if ($document.DocumentElement.LocalName -cne 'ruleset' -or
        $document.DocumentElement.NamespaceURI -cne 'http://pmd.sourceforge.net/ruleset/2.0.0' -or
        @($document.DocumentElement.Attributes | Where-Object { $_.Name -cnotin @('name','xmlns') }).Count -ne 0 -or
        @($document.SelectNodes('/*/*[local-name()!="description" and local-name()!="rule"]')).Count -ne 0 -or
        @($document.SelectNodes('/*/*[local-name()="description"]')).Count -ne 1 -or
        @($document.SelectNodes('/*/*[local-name()="description"]/*')).Count -ne 0 -or
        @($document.SelectNodes('//*')).Where({$_.NamespaceURI -cne 'http://pmd.sourceforge.net/ruleset/2.0.0'}).Count -ne 0 -or
        $actual.Count -ne 10 -or @($actual | Sort-Object -Unique).Count -ne 10 -or
        @($actual | Where-Object { $_ -cnotin $expected }).Count -ne 0 -or
        @($document.SelectNodes('//*[local-name()="exclude" or local-name()="exclude-pattern" or local-name()="property"]')).Count -ne 0 -or
        @($nodes | Where-Object { $_.ChildNodes.Count -ne 0 -or $_.Attributes.Count -ne 1 }).Count -ne 0) {
        throw 'STATIC_RULESET_CONTRACT'
    }
    return @($expected | ForEach-Object { ($_ -split '/')[-1] })
}

function Read-LocalStaticAnalysisResult {
    param(
        [Parameter(Mandatory)][string]$Report,
        [Parameter(Mandatory)][string]$Benchmark,
        [Parameter(Mandatory)][string]$SourceDirectory,
        [Parameter(Mandatory)][ValidateRange(1,10000)][int]$ExpectedSourceCount,
        [Parameter(Mandatory)][string[]]$RuleNames
    )
    $document = Read-StaticAnalysisXml $Report
    if ($document.DocumentElement.LocalName -cne 'pmd' -or
        $document.DocumentElement.NamespaceURI -cne 'http://pmd.sourceforge.net/report/2.0.0' -or
        $document.DocumentElement.GetAttribute('version') -cne '7.17.0') {
        throw 'STATIC_REPORT_VERSION'
    }
    if (@($document.SelectNodes('//*[local-name()="error" or local-name()="configerror"]')).Count) {
        throw 'STATIC_ANALYZER_ERRORS'
    }
    if (@($document.SelectNodes('//*[local-name()="suppressedviolation"]')).Count) {
        throw 'STATIC_SUPPRESSION_REFUSED'
    }
    if (@($document.SelectNodes('/*/*[local-name()!="file"]')).Count -ne 0 -or
        @($document.SelectNodes('/*/*[local-name()="file"]/*[local-name()!="violation"]')).Count -ne 0 -or
        @($document.SelectNodes('/*/*[local-name()="file"]/*/*')).Count -ne 0) {
        throw 'STATIC_REPORT_SHAPE'
    }
    if (-not (Test-Path -LiteralPath $Benchmark -PathType Leaf)) { throw 'STATIC_BENCHMARK_MISSING' }
    if ((Get-Item -LiteralPath $Benchmark).Length -gt 1048576) { throw 'STATIC_BENCHMARK_SIZE' }
    $text = [IO.File]::ReadAllText($Benchmark, [Text.UTF8Encoding]::new($false,$true))
    $parsed = [regex]::Match($text, '(?m)^Parser\s+\S+\s+\S+\s+(\d+)\s*$')
    if (-not $parsed.Success -or [int]$parsed.Groups[1].Value -ne $ExpectedSourceCount) {
        throw 'STATIC_SOURCE_COUNT_MISMATCH'
    }
    $calls = @()
    foreach ($rule in $RuleNames) {
        $found = [regex]::Matches($text, ('(?m)^' + [regex]::Escape($rule) + '\s+\S+\s+\S+\s+(\d+)(?:\s+\S+)?\s*$'))
        if ($found.Count -ne 1 -or [int]$found[0].Groups[1].Value -ne $ExpectedSourceCount) {
            throw 'STATIC_RULE_EXECUTION_MISMATCH'
        }
        $calls += [ordered]@{ rule=$rule; sourceCalls=$ExpectedSourceCount }
    }
    $source = [IO.Path]::GetFullPath($SourceDirectory).TrimEnd([char]92,[char]47)
    $findings = @()
    foreach ($file in $document.SelectNodes('//*[local-name()="file"]')) {
        $path = [IO.Path]::GetFullPath($file.GetAttribute('name'))
        if (-not $path.StartsWith($source + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'STATIC_REPORT_SOURCE_SCOPE'
        }
        foreach ($violation in $file.SelectNodes('*[local-name()="violation"]')) {
            $rule = $violation.GetAttribute('rule')
            if ($rule -cnotin $RuleNames) { throw 'STATIC_REPORT_RULE_UNKNOWN' }
            $findings += [ordered]@{
                path=[IO.Path]::GetRelativePath($source,$path).Replace('\','/')
                line=[int]$violation.GetAttribute('beginline')
                rule=$rule
                method=$violation.GetAttribute('method')
            }
        }
    }
    return [ordered]@{
        state=$(if ($findings.Count) { 'FINDINGS_OPEN' } else { 'PASS_LOCAL_STATIC_ANALYSIS' })
        sourceFiles=$ExpectedSourceCount; parserCalls=$ExpectedSourceCount
        ruleExecutions=$calls; findings=$findings; findingCount=$findings.Count
        processingErrors=0; nominalSecurityAcceptance=$false; releaseAcceptance=$false
    }
}

Export-ModuleMember -Function Read-StaticAnalysisXml,Assert-StaticAnalysisPath,Get-StaticAnalysisRules,Read-LocalStaticAnalysisResult

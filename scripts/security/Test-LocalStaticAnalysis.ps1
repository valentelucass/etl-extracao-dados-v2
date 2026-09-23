#Requires -Version 7.5
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'LocalStaticAnalysis.psm1') -Force
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out=[IO.Path]::GetFullPath($OutputDirectory)
$target=Join-Path $root 'target'
if (-not $out.StartsWith($target+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $out)) { throw 'STATIC_TEST_NEW_OUTPUT_REQUIRED' }
Assert-StaticAnalysisPath $target
$ancestor=[IO.Path]::GetDirectoryName($out)
while (-not (Test-Path -LiteralPath $ancestor)) { $ancestor=[IO.Path]::GetDirectoryName($ancestor) }
Assert-StaticAnalysisPath $ancestor
[IO.Directory]::CreateDirectory($out) | Out-Null
$utf8=[Text.UTF8Encoding]::new($false,$true)
$positive=Join-Path $out 'positive'
$negative=Join-Path $out 'negative'
[IO.Directory]::CreateDirectory($positive) | Out-Null
[IO.Directory]::CreateDirectory($negative) | Out-Null
$good=@'
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.ByteArrayInputStream;
class GoodSecurity {
    SecretKeySpec key(byte[] supplied) { return new SecretKeySpec(supplied, "AES"); }
    IvParameterSpec iv(byte[] supplied) { return new IvParameterSpec(supplied); }
    int read(byte[] supplied) throws Exception {
        try (var input = new ByteArrayInputStream(supplied)) { return input.read(); }
    }
}
'@
$bad=@'
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.nio.charset.StandardCharsets;
class BadSecurity {
    SecretKeySpec key() {
        return new SecretKeySpec("test-only-not-a-secret".getBytes(StandardCharsets.UTF_8), "AES");
    }
    IvParameterSpec iv() {
        return new IvParameterSpec("test-only-IV-0000".getBytes(StandardCharsets.UTF_8));
    }
    void cleanup() {
        try { throw new IllegalStateException("fixture-initial"); }
        finally { throw new IllegalArgumentException("fixture-cleanup"); }
    }
}
'@
[IO.File]::WriteAllText((Join-Path $positive 'GoodSecurity.java'),$good,$utf8)
[IO.File]::WriteAllText((Join-Path $negative 'BadSecurity.java'),$bad,$utf8)
$runner=Join-Path $PSScriptRoot 'Invoke-LocalStaticAnalysis.ps1'
$cases=[Collections.Generic.List[object]]::new()
& $runner -OutputDirectory (Join-Path $out 'positive-run') -SourceDirectory $positive
$positiveExit=$LASTEXITCODE
$positiveResult=Get-Content -LiteralPath (Join-Path $out 'positive-run/result.json') -Raw | ConvertFrom-Json
if ($positiveExit -ne 0 -or $positiveResult.state -cne 'PASS_LOCAL_STATIC_ANALYSIS' -or $positiveResult.sourceFiles -ne 1) {
    throw 'STATIC_POSITIVE_FIXTURE_FAILED'
}
$cases.Add([ordered]@{case='positive-resource-and-supplied-crypto';passed=$true;exit=$positiveExit})
& $runner -OutputDirectory (Join-Path $out 'negative-run') -SourceDirectory $negative
$negativeExit=$LASTEXITCODE
$negativeResult=Get-Content -LiteralPath (Join-Path $out 'negative-run/result.json') -Raw | ConvertFrom-Json
$expected=@('HardCodedCryptoKey','InsecureCryptoIv','DoNotThrowExceptionInFinally')
if ($negativeExit -ne 1 -or $negativeResult.state -cne 'FINDINGS_OPEN' -or
    @($expected | Where-Object { $_ -cnotin $negativeResult.findings.rule }).Count) {
    throw 'STATIC_NEGATIVE_FIXTURE_NOT_BLOCKED'
}
$cases.Add([ordered]@{case='hardcoded-key-iv-and-masking-finally';passed=$true;exit=$negativeExit;rules=@($negativeResult.findings.rule)})
$rules=Get-StaticAnalysisRules (Join-Path $PSScriptRoot 'pmd-security.xml')
$report=Join-Path $out 'positive-run/build/pmd.xml'
$benchmark=Join-Path $out 'positive-run/benchmark.txt'
function Reject([string]$Name,[string]$Reason,[scriptblock]$Action) {
    $rejected=$false
    try { $null=& $Action } catch {
        if ($_.Exception.Message -cne $Reason) { throw }
        $rejected=$true
    }
    if (-not $rejected) { throw ('STATIC_TEST_DID_NOT_REJECT_'+$Name) }
    $cases.Add([ordered]@{case=$Name;passed=$true;reason=$Reason})
}
Reject 'missing-report' 'STATIC_REPORT_MISSING' {
    Read-LocalStaticAnalysisResult -Report (Join-Path $out 'missing.xml') -Benchmark $benchmark -SourceDirectory $positive -ExpectedSourceCount 1 -RuleNames $rules
}
Reject 'source-count-mismatch' 'STATIC_SOURCE_COUNT_MISMATCH' {
    Read-LocalStaticAnalysisResult -Report $report -Benchmark $benchmark -SourceDirectory $positive -ExpectedSourceCount 2 -RuleNames $rules
}
$changedBenchmark=Join-Path $out 'missing-rule-benchmark.txt'
[IO.File]::WriteAllText($changedBenchmark,([IO.File]::ReadAllText($benchmark).Replace('HardCodedCryptoKey','MissingCryptoRule')),$utf8)
Reject 'rule-not-executed' 'STATIC_RULE_EXECUTION_MISMATCH' {
    Read-LocalStaticAnalysisResult -Report $report -Benchmark $changedBenchmark -SourceDirectory $positive -ExpectedSourceCount 1 -RuleNames $rules
}
foreach ($mutation in @(
    @{name='processing-error';xml='<error filename="fixture" msg="synthetic" />';reason='STATIC_ANALYZER_ERRORS'},
    @{name='suppressed-finding';xml='<suppressedviolation filename="fixture" suppressiontype="annotation" />';reason='STATIC_SUPPRESSION_REFUSED'},
    @{name='unknown-report-node';xml='<unknown-finding />';reason='STATIC_REPORT_SHAPE'}
)) {
    $path=Join-Path $out ($mutation.name+'.xml')
    [IO.File]::WriteAllText($path,([IO.File]::ReadAllText($report).Replace('</pmd>',$mutation.xml+'</pmd>')),$utf8)
    Reject $mutation.name $mutation.reason {
        Read-LocalStaticAnalysisResult -Report $path -Benchmark $benchmark -SourceDirectory $positive -ExpectedSourceCount 1 -RuleNames $rules
    }
}
$mutatedRules=Join-Path $out 'reduced-rules.xml'
[IO.File]::WriteAllText($mutatedRules,([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'pmd-security.xml')).Replace('<rule ref="category/java/security.xml/HardCodedCryptoKey" />','')),$utf8)
Reject 'reduced-ruleset' 'STATIC_RULESET_CONTRACT' { Get-StaticAnalysisRules $mutatedRules }
foreach ($mutation in @(
    @{name='include-filter';xml='<include-pattern>does-not-match-anything</include-pattern>'},
    @{name='unknown-ruleset-node';xml='<unknown-rule />'},
    @{name='duplicate-rule';xml='<rule ref="category/java/security.xml/HardCodedCryptoKey" />'}
)) {
    $path=Join-Path $out ($mutation.name+'.xml')
    [IO.File]::WriteAllText($path,([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'pmd-security.xml')).Replace('</ruleset>',$mutation.xml+'</ruleset>')),$utf8)
    Reject $mutation.name 'STATIC_RULESET_CONTRACT' { Get-StaticAnalysisRules $path }
}
$result=[ordered]@{state='PASS';mavenRuns=2;cases=$cases.ToArray();network=$false;sql=$false;nominalSecurityAcceptance=$false}
[IO.File]::WriteAllText((Join-Path $out 'result.json'),($result | ConvertTo-Json -Depth 10),$utf8)
Write-Output ('STATIC_ANALYSIS_SELF_TEST_PASS cases='+$cases.Count+' mavenRuns=2')

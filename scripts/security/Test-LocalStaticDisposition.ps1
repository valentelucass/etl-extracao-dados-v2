#Requires -Version 7.5
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'LocalStaticAnalysis.psm1')
Import-Module (Join-Path $PSScriptRoot 'LocalStaticDisposition.psm1')
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out=[IO.Path]::GetFullPath($OutputDirectory)
$target=Join-Path $repo 'target'
if (-not $out.StartsWith($target+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $out)) { throw 'DISPOSITION_TEST_NEW_OUTPUT_REQUIRED' }
Assert-StaticAnalysisPath $target
$ancestor=[IO.Path]::GetDirectoryName($out)
while (-not (Test-Path -LiteralPath $ancestor)) { $ancestor=[IO.Path]::GetDirectoryName($ancestor) }
Assert-StaticAnalysisPath $ancestor
[IO.Directory]::CreateDirectory($out) | Out-Null
$utf8=[Text.UTF8Encoding]::new($false,$true)
$cases=[Collections.Generic.List[object]]::new()
$timer=[Diagnostics.Stopwatch]::StartNew()
function Write-Text([string]$Path,[string]$Text) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path)) | Out-Null
    [IO.File]::WriteAllText($Path,$Text,$utf8)
}
function Write-Json([string]$Path,$Value) { Write-Text $Path (ConvertTo-Json -InputObject $Value -Depth 24) }
function Read-Json([string]$Path) { return ,([IO.File]::ReadAllText($Path,$utf8) | ConvertFrom-Json -AsHashtable -NoEnumerate) }
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Fixture([string]$Name) {
    $root=Join-Path $out $Name
    $source=Join-Path $root 'src/main/java'
    $pmd=Join-Path $root 'target/pmd'
    $build=Join-Path $root 'target/executed-build'
    $reports=Join-Path $build 'target/surefire-reports'
    $catalog=Join-Path $root 'catalog.json'
    $sourceFile=Join-Path $source 'example/Sample.java'
    $testRelative='src/test/java/example/SampleTest.java'
    Write-Text $sourceFile "package example;`nclass Sample { void copy() {} void capture() {} }`n"
    Write-Text (Join-Path $build 'src/main/java/example/Sample.java') ([IO.File]::ReadAllText($sourceFile,$utf8))
    Write-Text (Join-Path $root $testRelative) "package example;`nclass SampleTest { void verifies() {} }`n"
    Write-Text (Join-Path $build $testRelative) ([IO.File]::ReadAllText((Join-Path $root $testRelative),$utf8))
    $rulesText=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'pmd-security.xml'),$utf8)
    Write-Text (Join-Path $root 'scripts/security/pmd-security.xml') $rulesText
    Write-Text (Join-Path $pmd 'ruleset.xml') $rulesText
    $rules=@(Get-StaticAnalysisRules (Join-Path $pmd 'ruleset.xml'))
    $inventory=@(@{path='example/Sample.java';sha256=(Hash $sourceFile)})
    Write-Json (Join-Path $pmd 'source-inventory.json') $inventory
    $rawFindings=@(@{path='example/Sample.java';line=2;rule='CloseResource';method='copy'},@{path='example/Sample.java';line=3;rule='PreserveStackTrace';method='capture'})
    $raw=@{state='FINDINGS_OPEN';sourceFiles=1;parserCalls=1;ruleExecutions=@($rules | ForEach-Object {@{rule=$_;sourceCalls=1}});findings=$rawFindings;findingCount=2;processingErrors=0;nominalSecurityAcceptance=$false;releaseAcceptance=$false;offline=$true;network=$false;sql=$false;ownedProcessesRemaining=0;elapsedSeconds=1.0}
    Write-Json (Join-Path $pmd 'result.json') $raw
    Write-Json (Join-Path $pmd 'command.json') @{arguments=@('synthetic-self-test-no-analyzer-executed');sourceDirectory=$source;rules=$rules;sourceFiles=1;rulesetSha256=(Hash (Join-Path $pmd 'ruleset.xml'))}
    Write-Json (Join-Path $pmd 'execution.json') @{exit=0;timedOut=$false;elapsedSeconds=1.0;processExited=$true}
    $benchmark="Parser 0 0 1`n"+(@($rules | ForEach-Object { $_+' 0 0 1' }) -join "`n")+"`n"
    Write-Text (Join-Path $pmd 'benchmark.txt') $benchmark
    $escaped=[Security.SecurityElement]::Escape($sourceFile)
    Write-Text (Join-Path $pmd 'build/pmd.xml') ('<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0" version="7.17.0"><file name="'+$escaped+'"><violation beginline="2" rule="CloseResource" method="copy">Synthetic</violation><violation beginline="3" rule="PreserveStackTrace" method="capture">Synthetic</violation></file></pmd>')
    $xml='<testsuite name="example.SampleTest" tests="3" failures="0" errors="0" skipped="0"><testcase classname="example.SampleTest" name="verifies"/><testcase classname="example.SampleTest" name="verifies(int)[1]"/><testcase classname="example.SampleTest" name="verifies[2]"/></testsuite>'
    Write-Text (Join-Path $reports 'TEST-example.SampleTest.xml') $xml
    $findings=@($rawFindings | ForEach-Object {
        @{id=('PMD-'+$_.line);path=('src/main/java/'+$_.path);line=$_.line;rule=$_.rule;method=$_.method;sourceSha256=(Hash $sourceFile);classification='SANITIZACAO_DE_DADOS';rationale='Synthetic fixture exercises explicit technical disposition only.';tests=@(@{path=$testRelative;sha256=(Hash (Join-Path $root $testRelative));className='example.SampleTest';testCase='verifies'});proofLimit='Synthetic XML verifies this gate; no Java, PMD, SQL or real security acceptance was executed.'}
    })
    Write-Json $catalog @{schema='local-pmd-dispositions-v1';nominalSecurityAcceptance=$false;releaseAcceptance=$false;findings=$findings}
    return @{root=$root;source=$sourceFile;pmd=$pmd;build=$build;reports=$reports;catalog=$catalog;test=(Join-Path $root $testRelative);executedTest=(Join-Path $build $testRelative);xml=(Join-Path $reports 'TEST-example.SampleTest.xml')}
}
function Evaluate($Fixture) {
    if ($timer.Elapsed.TotalSeconds -gt 120) { throw 'DISPOSITION_TEST_DEADLINE' }
    Get-LocalStaticDisposition -RepositoryRoot $Fixture.root -Catalog $Fixture.catalog -PmdDirectory $Fixture.pmd -TestSourceRoot $Fixture.build -TestReportDirectories @($Fixture.reports)
}
function Reject([string]$Name,[string]$Reason,[scriptblock]$Mutate) {
    $fixture=Fixture $Name
    & $Mutate $fixture
    $rejected=$false
    try { $null=Evaluate $fixture } catch {
        if ($_.Exception.Message -cne $Reason) { throw ('DISPOSITION_TEST_UNEXPECTED_REASON_'+$Name+'_'+$_.Exception.Message) }
        $rejected=$true
    }
    if (-not $rejected) { throw ('DISPOSITION_TEST_NOT_REJECTED_'+$Name) }
    $cases.Add([ordered]@{name=$Name;passed=$true;reason=$Reason})
}
try {
    $positive=Fixture 'positive'
    $result=Evaluate $positive
    if ($result.state -cne 'PASS_LOCAL_REVIEWED_FINDINGS' -or $result.rawState -cne 'FINDINGS_OPEN' -or $result.rawGateExit -ne 1 -or
        $result.reviewedFindings -ne 2 -or $result.nominalSecurityAcceptance -or $result.releaseAcceptance -or
        @($result.dispositions | Where-Object { $_.tests[0].invocations -ne 3 }).Count) { throw 'DISPOSITION_POSITIVE_CONTRACT' }
    Write-Json (Join-Path $out 'positive-result.json') $result
    $cases.Add([ordered]@{name='exact-findings-and-all-three-method-invocations';passed=$true})
    $duplicates=Fixture 'dynamic-factory-multiplicity'
    Write-Text $duplicates.xml ([IO.File]::ReadAllText($duplicates.xml).Replace('name="verifies[2]"','name="verifies"'))
    $duplicateResult=Evaluate $duplicates
    if (@($duplicateResult.dispositions | Where-Object { $_.tests[0].invocations -ne 3 }).Count) { throw 'DISPOSITION_DUPLICATE_MULTIPLICITY_LOST' }
    $cases.Add([ordered]@{name='dynamic-factory-equal-names-preserve-three-occurrences';passed=$true})
    Reject 'duplicate-json-property' 'DISPOSITION_JSON_DUPLICATE' {param($f) $text=[IO.File]::ReadAllText($f.catalog);Write-Text $f.catalog ($text.Replace('"schema":','"schema":"duplicate","schema":'))}
    Reject 'unknown-catalog-property' 'DISPOSITION_JSON_SHAPE' {param($f) $c=Read-Json $f.catalog;$c.ignoreAll=$true;Write-Json $f.catalog $c}
    Reject 'nominal-approval-inferred' 'DISPOSITION_CATALOG_CONTRACT' {param($f) $c=Read-Json $f.catalog;$c.nominalSecurityAcceptance=$true;Write-Json $f.catalog $c}
    Reject 'release-approval-inferred' 'DISPOSITION_CATALOG_CONTRACT' {param($f) $c=Read-Json $f.catalog;$c.releaseAcceptance=$true;Write-Json $f.catalog $c}
    Reject 'duplicate-finding' 'DISPOSITION_FINDING_DUPLICATE' {param($f) $c=Read-Json $f.catalog;$c.findings[1]=$c.findings[0].Clone();$c.findings[1].id='different-id';Write-Json $f.catalog $c}
    Reject 'duplicate-finding-id' 'DISPOSITION_FINDING_CONTRACT' {param($f) $c=Read-Json $f.catalog;$c.findings[1].id=$c.findings[0].id;Write-Json $f.catalog $c}
    Reject 'missing-disposition' 'DISPOSITION_FINDING_SET_INCOMPLETE' {param($f) $c=Read-Json $f.catalog;$c.findings=@($c.findings[0]);Write-Json $f.catalog $c}
    Reject 'new-unmatched-finding' 'DISPOSITION_FINDING_NOT_IN_RAW' {param($f) $c=Read-Json $f.catalog;$c.findings[0].line=99;Write-Json $f.catalog $c}
    Reject 'wildcard-rule' 'DISPOSITION_FINDING_CONTRACT' {param($f) $c=Read-Json $f.catalog;$c.findings[0].rule='*';Write-Json $f.catalog $c}
    Reject 'wildcard-test' 'DISPOSITION_TEST_BINDING_CONTRACT' {param($f) $c=Read-Json $f.catalog;$c.findings[0].tests[0].testCase='*';Write-Json $f.catalog $c}
    Reject 'source-drift' 'DISPOSITION_SOURCE_DRIFT' {param($f) Write-Text $f.source 'changed source'}
    Reject 'executed-main-source-drift' 'DISPOSITION_EXECUTED_MAIN_SOURCE_DRIFT' {param($f) Write-Text (Join-Path $f.build 'src/main/java/example/Sample.java') 'changed executed main'}
    Reject 'executed-main-extra-source' 'DISPOSITION_INVENTORY_SET' {param($f) Write-Text (Join-Path $f.build 'src/main/java/example/Extra.java') 'class Extra {}'}
    Reject 'test-source-drift' 'DISPOSITION_TEST_SOURCE_HASH' {param($f) Write-Text $f.test 'changed test'}
    Reject 'executed-test-source-drift' 'DISPOSITION_EXECUTED_TEST_SOURCE_HASH' {param($f) Write-Text $f.executedTest 'changed executed test'}
    Reject 'catalog-source-hash' 'DISPOSITION_FINDING_SOURCE_HASH' {param($f) $c=Read-Json $f.catalog;$c.findings[0].sourceSha256=('0'*64);Write-Json $f.catalog $c}
    Reject 'inventory-duplicate' 'DISPOSITION_INVENTORY_DUPLICATE_OR_PATH' {param($f) $p=Join-Path $f.pmd 'source-inventory.json';$i=Read-Json $p;Write-Json $p @($i[0],$i[0]);$r=Read-Json (Join-Path $f.pmd 'result.json');$r.sourceFiles=2;$r.parserCalls=2;Write-Json (Join-Path $f.pmd 'result.json') $r;$c=Read-Json (Join-Path $f.pmd 'command.json');$c.sourceFiles=2;Write-Json (Join-Path $f.pmd 'command.json') $c}
    Reject 'inventory-new-source' 'DISPOSITION_INVENTORY_SET' {param($f) Write-Text (Join-Path $f.root 'src/main/java/example/Extra.java') 'class Extra {}'}
    Reject 'raw-count-mismatch' 'DISPOSITION_RAW_RESULT_CONTRACT' {param($f) $p=Join-Path $f.pmd 'result.json';$r=Read-Json $p;$r.findingCount=1;Write-Json $p $r}
    Reject 'raw-count-string' 'DISPOSITION_COUNTER_TYPE_OR_BOUND' {param($f) $p=Join-Path $f.pmd 'result.json';$r=Read-Json $p;$r.findingCount='2';Write-Json $p $r}
    Reject 'raw-report-mismatch' 'DISPOSITION_RAW_REPORT_MISMATCH' {param($f) $p=Join-Path $f.pmd 'result.json';$r=Read-Json $p;$r.findings[0].line=99;Write-Json $p $r}
    Reject 'analyzer-timeout' 'DISPOSITION_ANALYZER_NOT_COMPLETED' {param($f) $p=Join-Path $f.pmd 'execution.json';$e=Read-Json $p;$e.timedOut=$true;Write-Json $p $e}
    Reject 'pmd-processing-error' 'STATIC_ANALYZER_ERRORS' {param($f) $p=Join-Path $f.pmd 'build/pmd.xml';Write-Text $p ([IO.File]::ReadAllText($p).Replace('</pmd>','<error filename="synthetic"/></pmd>'))}
    Reject 'pmd-suppression' 'STATIC_SUPPRESSION_REFUSED' {param($f) $p=Join-Path $f.pmd 'build/pmd.xml';Write-Text $p ([IO.File]::ReadAllText($p).Replace('</pmd>','<suppressedviolation/></pmd>'))}
    Reject 'pmd-xml-dtd' 'DISPOSITION_PMD_XML_INVALID' {param($f) $p=Join-Path $f.pmd 'build/pmd.xml';Write-Text $p ('<!DOCTYPE pmd [<!ENTITY synthetic "blocked">]>'+[IO.File]::ReadAllText($p))}
    Reject 'ruleset-reduction' 'STATIC_RULESET_CONTRACT' {param($f) $p=Join-Path $f.pmd 'ruleset.xml';Write-Text $p ([IO.File]::ReadAllText($p).Replace('<rule ref="category/java/security.xml/HardCodedCryptoKey" />',''))}
    Reject 'rule-not-executed' 'STATIC_RULE_EXECUTION_MISMATCH' {param($f) $p=Join-Path $f.pmd 'benchmark.txt';Write-Text $p ([IO.File]::ReadAllText($p).Replace('CloseResource','NotExecuted'))}
    Reject 'report-counts-inconsistent' 'DISPOSITION_TEST_REPORT_COUNTS' {param($f) Write-Text $f.xml ([IO.File]::ReadAllText($f.xml).Replace('tests="3"','tests="4"'))}
    Reject 'method-prefix-is-not-match' 'DISPOSITION_TEST_EVIDENCE_MISSING' {param($f) Write-Text $f.xml ([IO.File]::ReadAllText($f.xml).Replace('verifies','verifiesOther'))}
    Reject 'one-invocation-skipped' 'DISPOSITION_TEST_NOT_PASSED' {param($f) $x=[IO.File]::ReadAllText($f.xml).Replace('skipped="0"','skipped="1"').Replace('name="verifies[2]"/>','name="verifies[2]"><skipped/></testcase>');Write-Text $f.xml $x}
    Reject 'one-invocation-failed' 'DISPOSITION_TEST_REPORT_FAILED' {param($f) $x=[IO.File]::ReadAllText($f.xml).Replace('failures="0"','failures="1"').Replace('name="verifies[2]"/>','name="verifies[2]"><failure/></testcase>');Write-Text $f.xml $x}
    Reject 'duplicate-invocation-skipped' 'DISPOSITION_TEST_NOT_PASSED' {param($f) $x=[IO.File]::ReadAllText($f.xml).Replace('skipped="0"','skipped="1"').Replace('name="verifies[2]"/>','name="verifies"><skipped/></testcase>');Write-Text $f.xml $x}
    Reject 'flaky-rerun-rejected' 'DISPOSITION_TEST_REPORT_CASE' {param($f) Write-Text $f.xml ([IO.File]::ReadAllText($f.xml).Replace('name="verifies[2]"/>','name="verifies[2]"><flakyFailure/></testcase>'))}
    Reject 'test-xml-dtd' 'DISPOSITION_TEST_XML_INVALID' {param($f) Write-Text $f.xml ('<!DOCTYPE testsuite [<!ENTITY synthetic "blocked">]>'+[IO.File]::ReadAllText($f.xml))}
    Reject 'test-class-path-mismatch' 'DISPOSITION_TEST_CLASS_PATH' {param($f) $c=Read-Json $f.catalog;$c.findings[0].tests[0].path='src/test/java/example/OtherTest.java';Write-Json $f.catalog $c}
    Reject 'test-report-outside-executed-build' 'DISPOSITION_TEST_REPORT_BUILD_SCOPE' {param($f) $other=Join-Path $f.root 'target/other-build/target/surefire-reports';Write-Text (Join-Path $other 'TEST-example.SampleTest.xml') ([IO.File]::ReadAllText($f.xml));$f.reports=$other}
    Reject 'test-report-nested-reuse' 'DISPOSITION_TEST_REPORT_BUILD_SCOPE' {param($f) $other=Join-Path $f.build 'target/old/surefire-reports';Write-Text (Join-Path $other 'TEST-example.SampleTest.xml') ([IO.File]::ReadAllText($f.xml));$f.reports=$other}
    Reject 'duplicate-report-directory' 'DISPOSITION_TEST_REPORT_BOUND_OR_DUPLICATE' {param($f) $f.reports=@($f.reports,$f.reports)}
    $summary=[ordered]@{state='PASS';layer='SYNTHETIC_GATE_SELF_TEST_ONLY';cases=$cases.ToArray();caseCount=$cases.Count;elapsedSeconds=$timer.Elapsed.TotalSeconds;javaExecuted=$false;pmdExecuted=$false;sql=$false;network=$false;nominalSecurityAcceptance=$false;releaseAcceptance=$false}
    Write-Json (Join-Path $out 'result.json') $summary
    Write-Output ('STATIC_DISPOSITION_SELF_TEST_PASS cases='+$cases.Count)
} catch {
    $message=$_.Exception.Message
    $safe=if($message -cmatch '^(DISPOSITION|STATIC)_[A-Za-z0-9_ -]+$'){$message}else{'DISPOSITION_SELF_TEST_EXECUTION_ERROR'}
    Write-Json (Join-Path $out 'result.json') @{state='FAILED';reason=$safe;errorKind=$_.Exception.GetType().FullName;sourceLine=$_.InvocationInfo.ScriptLineNumber;sourceFile=[IO.Path]::GetFileName($_.InvocationInfo.ScriptName);cases=$cases.ToArray();elapsedSeconds=$timer.Elapsed.TotalSeconds;javaExecuted=$false;pmdExecuted=$false;sql=$false;network=$false}
    throw $safe
}

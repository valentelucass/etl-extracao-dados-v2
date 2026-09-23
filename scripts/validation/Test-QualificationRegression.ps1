#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$BuildAttempt)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round='target/macrobloco-qualificacao-pacote-20260913-01/'
$oldRound='target/macrobloco-analitico-20260912-01/'
$utf8=[Text.UTF8Encoding]::new($false,$true)
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
function ReadJson([string]$path){
    Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $root $path))) 8388608
}
function Hash([string]$path){(Get-FileHash -LiteralPath (Join-Path $root $path)).Hash.ToLowerInvariant()}
function Xml([string]$path){
    $file=Join-Path $root $path
    if((Get-Item -LiteralPath $file).Length -gt 33554432){throw 'QUAL_REGRESSION_XML_SIZE'}
    $settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver=$null;$settings.MaxCharactersInDocument=33554432
    $reader=[Xml.XmlReader]::Create($file,$settings)
    try{$document=[Xml.XmlDocument]::new();$document.XmlResolver=$null;$document.Load($reader);return ,$document}
    finally{$reader.Dispose()}
}
$dynamicFactories=@{
    'br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02MutationTest/executesEveryCatalogedMutationAndChecksItsExactReason'=48
    'br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewMutationCatalogTest/executesOneHappyCaseAndFiftyTwoRealFailClosedMutations'=53
}
function Suite([string]$path,[switch]$Unit){
    $xml=Xml $path
    $cases=@($xml.SelectNodes('/testsuite/testcase'))
    if($cases.Count -eq 0 -or [int]$xml.testsuite.tests -ne $cases.Count){throw 'QUAL_REGRESSION_CASE_COUNT'}
    $keys=@($cases|ForEach-Object {$_.GetAttribute('classname')+'/'+$_.GetAttribute('name')})
    $frequencies=[Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
    foreach($key in $keys){if($frequencies.ContainsKey($key)){$frequencies[$key]++}else{$frequencies[$key]=1}}
    foreach($entry in $frequencies.GetEnumerator()){
        if($entry.Value -le 1){continue}
        # Surefire reports each DynamicTest with the factory method name. Only these two
        # unchanged historical factories may repeat; all integration identities stay exact.
        if(-not $Unit -or -not $dynamicFactories.ContainsKey($entry.Key) -or
            $entry.Value -ne $dynamicFactories[$entry.Key]){throw 'QUAL_REGRESSION_DUPLICATE_CASE'}
        $parts=$entry.Key.Split('/')
        $oldFactory=Xml ($oldRound+'verify-physical-analytic-02-surefire/TEST-'+$parts[0]+'.xml')
        $oldCount=@($oldFactory.SelectNodes('/testsuite/testcase')|Where-Object {
            $_.GetAttribute('classname') -ceq $parts[0] -and $_.GetAttribute('name') -ceq $parts[1]}).Count
        if($oldCount -ne $entry.Value){throw 'QUAL_REGRESSION_DYNAMIC_FACTORY_DRIFT'}
    }
    if($Unit){
        $occurrences=[Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
        $keys=@(foreach($key in $keys){
            if($frequencies[$key] -eq 1){$key;continue}
            if($occurrences.ContainsKey($key)){$occurrences[$key]++}else{$occurrences[$key]=1}
            $key+'#xml-occurrence-'+$occurrences[$key]
        })
    }
    if($xml.SelectNodes('/testsuite/testcase/failure|/testsuite/testcase/error').Count -ne 0){throw 'QUAL_REGRESSION_FAILED_CASE'}
    return [pscustomobject]@{path=$path;sha256=(Hash $path);cases=$keys;
        skipped=@($cases|Where-Object {$null -ne $_.SelectSingleNode('skipped')}|
            ForEach-Object {$_.GetAttribute('classname')+'/'+$_.GetAttribute('name')})}
}
function Exact($actual,$expected,[string]$reason){
    if(@($actual).Count -ne @($expected).Count -or
        (@($actual|Sort-Object)-join '|') -cne (@($expected|Sort-Object)-join '|')){throw $reason}
}
$result=ReadJson ($round+$BuildAttempt+'/result.json')
if($result.exit -ne 0 -or $result.phase -cne 'VerifyPhysical' -or $result.timedOut -or
    -not $result.rollbackConfirmed -or -not $result.logUtf8Integrity -or $result.logLimitExceeded){throw 'QUAL_REGRESSION_FULL_REQUIRED'}
Exact $result.arguments @('--offline','--batch-mode','--no-transfer-progress','spotless:apply','verify',
    '-Dv2.measurement.receipt=true','-Pshadow-local-integration','-Dshadow.local.integration.enabled=true') 'QUAL_REGRESSION_ARGUMENTS'
$old=ReadJson 'docs/catalogos/macrobloco-analitico/verification-summary.json'
$oldCases=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$oldSuites=[Collections.Generic.List[object]]::new()
foreach($class in $old.integration.qualifiedClasses.Keys){
    $affected=$oldRound+$old.integration.affectedAttempt+'-failsafe/TEST-'+$class+'.xml'
    $path=if(Test-Path -LiteralPath (Join-Path $root $affected)){$affected}else{
        $oldRound+$old.full.buildAttempt+'-failsafe/TEST-'+$class+'.xml'
    }
    $suite=Suite $path
    if($suite.skipped.Count -ne 0 -or $suite.cases.Count -ne $old.integration.qualifiedClasses[$class]){throw 'QUAL_REGRESSION_PREDECESSOR_CLASS'}
    foreach($case in $suite.cases){if(-not $oldCases.Add($case)){throw 'QUAL_REGRESSION_PREDECESSOR_DUPLICATE'}}
    $oldSuites.Add($suite)
}
if($oldSuites.Count -ne 61 -or $oldCases.Count -ne 378){throw 'QUAL_REGRESSION_PREDECESSOR_UNIVERSE'}
$reports=[Collections.Generic.List[object]]::new()
$units=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$integration=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$skipped=[Collections.Generic.List[string]]::new()
$newClasses=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($kind in @('surefire','failsafe')){
    $directory=$round+$BuildAttempt+'/build/target/'+$kind+'-reports/'
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $root $directory) -Filter 'TEST-*.xml' -File){
        $suite=Suite ($directory+$file.Name) -Unit:($kind -ceq 'surefire')
        $reports.Add($suite)
        if($kind -ceq 'failsafe'){
            if($suite.skipped.Count -ne 0){throw 'QUAL_REGRESSION_IT_SKIP'}
            foreach($case in $suite.cases){
                if(-not $integration.Add($case)){throw 'QUAL_REGRESSION_IT_DUPLICATE'}
                $null=$newClasses.Add($case.Split('/')[0])
            }
        }else{
            foreach($case in $suite.cases){if(-not $units.Add($case)){throw 'QUAL_REGRESSION_UNIT_DUPLICATE'}}
            foreach($case in $suite.skipped){$skipped.Add($case)}
        }
    }
}
if($units.Count -lt 1974 -or $integration.Count -le 378){throw 'QUAL_REGRESSION_MISSING_NEW_CASES'}
foreach($case in $oldCases){if(-not $integration.Contains($case)){throw ('QUAL_REGRESSION_OLD_CASE_MISSING_'+$case)}}
Exact $skipped $old.full.historicalSkips 'QUAL_REGRESSION_SKIP_DRIFT'
$mandatory=@(Get-ChildItem -LiteralPath (Join-Path $root 'src/test/java/br/com/esl/etl/v2/bootstrap') -Filter 'Qualification*IT.java' -File)
foreach($file in $mandatory){
    if(-not $newClasses.Contains('br.com.esl.etl.v2.bootstrap.'+$file.BaseName)){throw 'QUAL_REGRESSION_NEW_CLASS_MISSING'}
}
# Maven's actual verify must have enforced the unchanged per-package coverage gates.
$log=[IO.File]::ReadAllText((Join-Path $root ($round+$BuildAttempt+'/stdout.log')),$utf8)
if(-not $log.Contains('BUILD SUCCESS') -or -not $log.Contains('All coverage checks have been met.')){throw 'QUAL_REGRESSION_COVERAGE_CHECK_MISSING'}
[ordered]@{passed=$true;attempt=$BuildAttempt;unitTests=$units.Count;unitSkipped=$skipped.Count;
    historicalSkips=@($skipped);integrationTests=$integration.Count;integrationClasses=$newClasses.Count;
    integrationSkipped=0;previous378Exact=$true;previousClasses=61;mandatoryNewClasses=$mandatory.Count;
    coveragePassed=$true;rollbackConfirmed=$true;dynamicUnitFactories=$dynamicFactories;
    dynamicUnitIdentity='factory method plus XML occurrence; counts checked against predecessor, not deduplicated';
    reports=$reports.ToArray();previousReports=$oldSuites.ToArray()}|
    ConvertTo-Json -Depth 12

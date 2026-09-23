#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/macrobloco-relacional/'
$history='docs/continuidade/historico/macrobloco-relacional/'
$predecessor='docs/catalogos/coletas-temporal-integration/manifesto.json'
$predecessorHash='918f6c1e1671eefd71a7b3676cfcfb5b8b32615efe1f2c86925c36f47029682d'
$allowed=@('STATES.md','README.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
    'pom.xml','database/baseline/001_schema_foundation_baseline.sql',
    'scripts/validation/Test-SchemaFoundationManifest.ps1','scripts/validation/Test-ProgressiveDataGate.ps1',
    'scripts/validation/Test-ColetasTemporalIntegration.ps1','scripts/validation/Test-ColetasTemporalIntegrationBaseline.ps1',
    'src/test/java/br/com/esl/etl/v2/contratos/medicao/ManagedPageGauge.java',
    'src/test/java/br/com/esl/etl/v2/arquitetura/ArchitectureRulesTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/sombra/SchemaFoundationSqlContractTest.java')
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Local([string]$path){
    if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'REL_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'REL_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$path){[IO.File]::ReadAllText((Local $path),$utf8)}
function Hash([string]$path,[string]$expected){
    if($expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $expected){throw ('REL_HASH_'+$path)}
}
function Exact($a,$b,[string]$reason){
    if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}
}
function Baseline {
    Hash $predecessor $predecessorHash
    $old=Read $predecessor|ConvertFrom-Json -Depth 50 -DateKind String
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($predecessor,$predecessorHash)
    if($base.Count -ne 2494){throw 'REL_BASELINE_COUNT'}
    return ,$base
}
function Summary($s){
    if(-not $s.passed -or $s.full.tests -lt 1582 -or $s.full.failures -ne 0 -or $s.full.errors -ne 0 -or $s.full.skipped -ne 4 -or
        $s.integration.tests -lt 90 -or $s.integration.failures -ne 0 -or $s.integration.errors -ne 0 -or $s.integration.skipped -ne 0 -or
        -not $s.integration.realJdbc -or -not $s.integration.rollbackConfirmed -or -not $s.concurrency.actualClaimAndConsumption -or
        $s.jar.cases -lt 10 -or -not $s.jar.passed -or @($s.scale.completedScales|Sort-Object -Unique).Count -lt 3 -or
        $s.scale.heapPlateauProven -or $s.remoteCalls -ne 0 -or $s.operationalPromotionAuthorized -or
        $s.realQualificationAccepted -or $s.physicalFreshVsUpgradeEquivalenceProven){throw 'REL_VERIFICATION'}
}
function Check($m){
    if($m.version -ne 1 -or $m.status -cnotin @('RELATIONAL_LOCAL_CANDIDATE','RELATIONAL_LOCAL_COMPLETE') -or $m.initialFiles -ne 2494 -or
        $m.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $m.syntheticOnly -or $m.remoteCalls -ne 0 -or
        $m.newAcceptances -ne 0 -or $m.durableDomainRows -ne 0 -or $m.operationalPromotionAuthorized -or $m.realQualificationAccepted){throw 'REL_SCOPE'}
    if($m.predecessor.path -cne $predecessor -or $m.predecessor.sha256 -cne $predecessorHash){throw 'REL_PREDECESSOR'}
    Exact $m.changedExistingFiles.path $allowed 'REL_DELTAS'
    $base=Baseline
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if($base[$e.path] -cne $e.before -or $e.snapshot -cne ($history+$e.path)){throw 'REL_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
        if($e.path -cin @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $current=Read $e.path;$previous=Read $e.snapshot
            if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'REL_HISTORY'}
            $pattern='(?m)^\s*- \[[ xX]\].+$'
            Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'REL_CHECKBOX'
        }
    }
    Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'REL_PRESERVED_SET'
    foreach($e in $m.preservedFiles){if($base[$e.path] -cne $e.sha256){throw 'REL_PRESERVED'};Hash $e.path $e.sha256}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($e in $m.newFiles){if($base.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'REL_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'REL_SNAPSHOT_UNLISTED'}}
    Summary (Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 30)
    foreach($pointer in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        if(-not (Read $pointer).Contains($m.status)){throw 'REL_POINTER'}
    }
    Hash $m.checkpoint.path $m.checkpoint.sha256
    if(-not (Read 'docs/continuidade/RETOMADA.md').Contains($m.checkpoint.sha256)){throw 'REL_CHECKPOINT'}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $java=Read 'target/macrobloco-relacional-20260911-01/java-verification.json'|ConvertFrom-Json -Depth 20
        if(-not $java.passed -or $java.exit -ne 0 -or $java.sources.Count -lt 797){throw 'REL_JAVA'}
        foreach($e in $java.sources){Hash $e.path $e.sha256}
        foreach($install in @('schema-install-01','schema-evolution-install-01','schema-strengthen-install-01','schema-completeness-install-01','schema-key-install-01','schema-mdfe-install-01')){
            $events=@((Read ('target/macrobloco-relacional-20260911-01/'+$install+'/ledger.jsonl')) -split '\r?\n'|Where-Object {$_}|ForEach-Object {$_|ConvertFrom-Json})
            if($events.Count -ne 2 -or $events[0].state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $events[1].state -cne 'CONFIRMED' -or -not $events[1].installed){throw 'REL_INSTALL_LEDGER'}
            foreach($e in $events[0].migrations){Hash $e.path $e.sha256}
        }
    }
    return ,$map
}
$manifest=Read ($catalog+'manifesto.json')|ConvertFrom-Json -Depth 50 -DateKind String
$map=Check $manifest
if($AsMap){return ,$map}
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('REL_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
        @('REL_SCOPE',{param($m)$m.remoteCalls=1}),
        @('REL_SCOPE',{param($m)$m.target='OTHER'}),
        @('REL_SCOPE',{param($m)$m.durableDomainRows=1}),
        @('REL_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('REL_DELTAS',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
        @('REL_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 50|ConvertFrom-Json -Depth 50 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('REL_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
    }
    foreach($mutation in @({param($s)$s.integration.skipped=1},{param($s)$s.full.errors=1},{param($s)$s.scale.heapPlateauProven=$true},{param($s)$s.jar.passed=$false})){
        $copy=Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 30
        & $mutation $copy
        $reason='ACCEPTED';try{Summary $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne 'REL_VERIFICATION'){throw 'REL_SUMMARY_GUARD'};$guards++
    }
}
@{passed=$true;status=$manifest.status;guards=$guards;private=[bool]$IncludePrivateEvidence;remoteCalls=0;newAcceptances=0}|ConvertTo-Json

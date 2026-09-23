#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/coletas-temporal-integration/'
$history='docs/continuidade/historico/coletas-temporal-integration/'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$predecessor='docs/catalogos/coletas-temporal-sql/manifesto.json'
$predecessorHash='1e85c738a3e07adeaacbfa72052a4c0125f08526d5622c5726d37e932553f825'
$allowed=@(
    'STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
    'pom.xml','database/baseline/001_schema_foundation_baseline.sql',
    'scripts/validation/Test-SchemaFoundationManifest.ps1','scripts/validation/Test-ProgressiveDataGate.ps1',
    'scripts/validation/Test-ColetasTemporalSql.ps1','scripts/validation/Test-Bloco60SqlContract.ps1',
    'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcSqlServerColetaStagingGateway.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcSqlServerColetaTemporalGateway.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/sombra/SchemaFoundationSqlContractTest.java'
)
function Local([string]$path){
    if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'COLINT_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $path)
    $file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'COLINT_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$path){[IO.File]::ReadAllText((Local $path),$utf8)}
function Hash([string]$path,[string]$expected){
    if($expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $expected){throw ('COLINT_HASH_'+$path)}
}
function Exact($a,$b,[string]$reason){
    if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}
}
function Baseline{
    Hash $predecessor $predecessorHash
    $m=Read $predecessor|ConvertFrom-Json -Depth 40 -DateKind String
    # B63's manifest contains a transition. Its predecessor is the complete inventory.
    $link=Read $m.predecessor.path|ConvertFrom-Json -Depth 40 -DateKind String
    $all=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    $local=Read 'docs/catalogos/bloco63-temporal-local/manifesto.json'|ConvertFrom-Json -Depth 40 -DateKind String
    foreach($e in $local.preservedFiles){$all.Add($e.path,$e.sha256)}
    foreach($e in $local.changedExistingFiles){$all.Add($e.path,$e.after)}
    foreach($e in $local.newFiles){$all.Add($e.path,$e.sha256)}
    $all.Add('docs/catalogos/bloco63-temporal-local/manifesto.json','46dd40e8c80f74ca766da3c0ed62ff3e601d772ebc7e31248f3d8eea4b8a8636')
    foreach($transition in @(
        @('docs/continuidade/manifesto-decisao-documental.json','d98a9d8b988061e41b4d39705fac0417f0ea1a23cdbf3f6a33251184c31ed0a3'),
        @('docs/catalogos/coletas-temporal-proof/manifesto.json','44c330ff177a95963aa0d20b23dc9be7fef9756026eb675f378c40eca14fd79d'),
        @('docs/catalogos/coletas-temporal-link/manifesto.json','ab93c96c287a9f1c21168f0fc3907ca098384b15a3d43aaf5c9ab0934e4bfc63'),
        @($predecessor,$predecessorHash)
    )){
        Hash $transition[0] $transition[1]
        $next=Read $transition[0]|ConvertFrom-Json -Depth 40 -DateKind String
        foreach($e in $next.changedExistingFiles){
            if($all[$e.path] -cne $e.before){throw 'COLINT_BASELINE_CHAIN'}
            $all[$e.path]=$e.after
            if($transition[0] -ceq 'docs/continuidade/manifesto-decisao-documental.json'){$all.Add($e.snapshot,$e.before)}
        }
        foreach($e in $next.newFiles){$all.Add($e.path,$e.sha256)}
        $all.Add($transition[0],$transition[1])
    }
    if($all.Count -ne 2452){throw 'COLINT_BASELINE_COUNT'}
    return ,$all
}
function Summary($s){
    if(-not $s.passed -or $s.full.tests -ne 1563 -or $s.full.failures -ne 0 -or $s.full.errors -ne 0 -or $s.full.skipped -ne 4 -or
        $s.integration.tests -ne 39 -or $s.integration.failures -ne 0 -or $s.integration.errors -ne 0 -or $s.integration.skipped -ne 0 -or
        -not $s.integration.realJdbc -or -not $s.integration.syntheticOnly -or -not $s.integration.rollbackConfirmed -or
        -not $s.concurrency.actualStageQualificationConsumption -or $s.remoteCalls -ne 0 -or $s.realQualificationAccepted -or
        $s.operationalPromotionAuthorized -or $s.physicalFreshVsUpgradeEquivalenceProven){throw 'COLINT_VERIFICATION'}
}
function Check($m){
    if($m.version -ne 1 -or $m.status -cne 'COLETAS_TEMPORAL_INTEGRATION_LOCAL_COMPLETE' -or $m.initialFiles -ne 2452 -or
        $m.remoteCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.operationalPromotionAuthorized -or $m.realQualificationAccepted -or
        $m.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $m.syntheticOnly -or $m.durableDomainRows -ne 0){throw 'COLINT_SCOPE'}
    if($m.predecessor.path -cne $predecessor -or $m.predecessor.sha256 -cne $predecessorHash){throw 'COLINT_PREDECESSOR'}
    Exact $m.changedExistingFiles.path $allowed 'COLINT_DELTAS'
    $base=Baseline
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if($base[$e.path] -cne $e.before -or $e.snapshot -cne ($history+$e.path)){throw 'COLINT_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
        if($e.path -cin @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $current=Read $e.path;$old=Read $e.snapshot
            if(-not $current.EndsWith($old,[StringComparison]::Ordinal)){throw 'COLINT_HISTORY'}
            $pattern='(?m)^\s*- \[[ xX]\].+$'
            Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($old.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'COLINT_CHECKBOX'
        }
    }
    Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'COLINT_PRESERVED_SET'
    foreach($e in $m.preservedFiles){if($base[$e.path] -cne $e.sha256){throw 'COLINT_PRESERVED'};Hash $e.path $e.sha256}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($e in $m.newFiles){if($base.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'COLINT_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'COLINT_SNAPSHOT_UNLISTED'}}
    Summary (Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 20)
    foreach($p in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        if(-not (Read $p).Contains($m.status)){throw 'COLINT_POINTER'}
    }
    if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'COLINT_INDEX'}
    Hash $m.checkpoint.path $m.checkpoint.sha256
    if(-not (Read 'docs/continuidade/RETOMADA.md').Contains($m.checkpoint.sha256)){throw 'COLINT_CHECKPOINT'}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $v=Read 'target/coletas-temporal-integration-20260910/java-verification.json'|ConvertFrom-Json -Depth 20
        if(-not $v.passed -or $v.exit -ne 0 -or $v.sources.Count -ne 783){throw 'COLINT_JAVA'}
        foreach($e in $v.sources){Hash $e.path $e.sha256}
        foreach($install in @('schema-install-01','schema-correction-install-01')){
            $ledger=@((Read ('target/coletas-temporal-integration-20260910/'+$install+'/ledger.jsonl')) -split '\r?\n'|Where-Object {$_}|ForEach-Object {$_|ConvertFrom-Json})
            if($ledger.Count -ne 2 -or $ledger[0].state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $ledger[1].state -cne 'CONFIRMED' -or -not $ledger[1].installed){throw 'COLINT_INSTALL_LEDGER'}
            foreach($e in $ledger[0].migrations){Hash $e.path $e.sha256}
        }
    }
    return ,$map
}
$manifest=Read ($catalog+'manifesto.json')|ConvertFrom-Json -Depth 40 -DateKind String
$map=Check $manifest
if($AsMap){return ,$map}
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('COLINT_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
        @('COLINT_SCOPE',{param($m)$m.remoteCalls=1}),
        @('COLINT_SCOPE',{param($m)$m.target='OTHER'}),
        @('COLINT_SCOPE',{param($m)$m.durableDomainRows=1}),
        @('COLINT_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('COLINT_DELTAS',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
        @('COLINT_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 40|ConvertFrom-Json -Depth 40 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('COLINT_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
    }
    foreach($mutation in @({param($s)$s.integration.skipped=1},{param($s)$s.full.errors=1},{param($s)$s.physicalFreshVsUpgradeEquivalenceProven=$true})){
        $copy=Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 20
        & $mutation $copy
        $reason='ACCEPTED';try{Summary $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne 'COLINT_VERIFICATION'){throw 'COLINT_SUMMARY_GUARD'};$guards++
    }
}
# Historical validation uses explicit before snapshots; no historic receipt is rewritten.
$previous=& (Join-Path $PSScriptRoot 'Test-ColetasTemporalSql.ps1') -SelfTest:$SelfTest -IncludePrivateEvidence:$IncludePrivateEvidence|ConvertFrom-Json
if(-not $previous.passed){throw 'COLINT_HISTORICAL_FAILURE'}
@{passed=$true;status=$manifest.status;guards=$guards;historicalGuards=$previous.guards;private=[bool]$IncludePrivateEvidence;remoteCalls=0;newAcceptances=0}|ConvertTo-Json

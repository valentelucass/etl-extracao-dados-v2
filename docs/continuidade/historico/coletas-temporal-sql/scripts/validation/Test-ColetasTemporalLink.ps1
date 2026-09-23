#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$catalog='docs/catalogos/coletas-temporal-link/'
$history='docs/continuidade/historico/coletas-temporal-link/'
$allowed=@(
    'STATES.md',
    'docs/runbooks/trilha-de-chats-gpt-5-6.md',
    'docs/continuidade/RETOMADA.md',
    'scripts/validation/Test-ColetasTemporalProof.ps1',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlReadOperation.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlQueryParameters.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlTransitionalField.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlTestSupport.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlTransitionCatalogTest.java'
)
$concurrent=@(
    'src/test/java/br/com/esl/etl/v2/arquitetura/ArchitectureRulesTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeAuthorizationPolicyTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportStagingPipelineTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlFixtureContractTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep/FailClosedSweepPreviewKernelTest.java'
)
$allowed+=$concurrent
$concurrentDocuments=@('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'docs/catalogos/graphql-coletas-temporal.csv',
    'docs/adr/0045-coletas-referencia-temporal-local-com-proveniencia.md',
    'docs/continuidade/checkpoints/0064-coletas-ligacao-temporal-implementada.md',
    'docs/continuidade/checkpoints/0065-coletas-ligacao-temporal-verificada.md',
    'docs/continuidade/checkpoints/0066-coletas-ligacao-temporal-entrega.md',
    'docs/continuidade/checkpoints/0066-avisos-java-testes.md',
    'docs/continuidade/checkpoints/0067-coletas-continuidade-conciliada.md',
    'docs/catalogos/coletas-temporal-link/RELATORIO.md',
    'docs/catalogos/coletas-temporal-link/verification-summary.json',
    'scripts/validation/Test-ColetasTemporalLink.ps1',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/graphql/GraphQlColetasTemporalContractCatalog.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/domain/ColetaTemporalObservation.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/domain/ColetaTemporalIdentityBinding.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaGraphQlTemporalMapper.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/LigarReferenciaTemporalColeta.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ExtrairReferenciasTemporaisColetas.java',
    'src/test/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaTemporalLinkTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/ExtrairReferenciasTemporaisColetasTest.java'
)
function Local([string]$path){
    if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'COLLINK_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
    while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'COLLINK_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
    return $file
}
function Read([string]$path){[IO.File]::ReadAllText((Local $path),$utf8)}
function Hash([string]$path,[string]$expected){if($expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $expected){throw ('COLLINK_HASH_'+$path)}}
function Exact($a,$b,[string]$reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}}
function Baseline{
    $basePath='docs/catalogos/bloco63-temporal-local/manifesto.json'
    $baseHash='46dd40e8c80f74ca766da3c0ed62ff3e601d772ebc7e31248f3d8eea4b8a8636'
    Hash $basePath $baseHash
    $m=Read $basePath|ConvertFrom-Json -Depth 40 -DateKind String
    $all=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $m.preservedFiles){$all.Add($e.path,$e.sha256)}
    foreach($e in $m.changedExistingFiles){$all.Add($e.path,$e.after)}
    foreach($e in $m.newFiles){$all.Add($e.path,$e.sha256)}
    $all.Add($basePath,$baseHash)
    $decisionPath='docs/continuidade/manifesto-decisao-documental.json'
    $decisionHash='d98a9d8b988061e41b4d39705fac0417f0ea1a23cdbf3f6a33251184c31ed0a3'
    Hash $decisionPath $decisionHash
    $decision=Read $decisionPath|ConvertFrom-Json -Depth 20 -DateKind String
    foreach($e in $decision.changedExistingFiles){
        if($all[$e.path] -cne $e.before){throw 'COLLINK_BASELINE_CHAIN'}
        $all[$e.path]=$e.after;$all.Add($e.snapshot,$e.before)
    }
    foreach($e in $decision.newFiles){$all.Add($e.path,$e.sha256)}
    $all.Add($decisionPath,$decisionHash)
    $proofPath='docs/catalogos/coletas-temporal-proof/manifesto.json'
    $proofHash='44c330ff177a95963aa0d20b23dc9be7fef9756026eb675f378c40eca14fd79d'
    Hash $proofPath $proofHash
    $proof=Read $proofPath|ConvertFrom-Json -Depth 30 -DateKind String
    foreach($e in $proof.changedExistingFiles){
        if($all[$e.path] -cne $e.before){throw 'COLLINK_BASELINE_CHAIN'}
        $all[$e.path]=$e.after
    }
    foreach($e in $proof.newFiles){$all.Add($e.path,$e.sha256)}
    $all.Add($proofPath,$proofHash)
    if($all.Count -ne 2400){throw 'COLLINK_BASELINE'}
    return ,$all
}
function Check($m){
    if($m.version -ne 1 -or $m.status -cne 'COLETAS_TEMPORAL_LINK_LOCAL_VERIFIED' -or $m.remoteCalls -ne 0 -or $m.sqlExecuted -or $m.newAcceptances -ne 0 -or $m.operationalSidecarEnabled -or $m.sourceTemporalEquivalenceAccepted -or $m.newBudget){throw 'COLLINK_SCOPE'}
    if($m.predecessor.path -cne 'docs/catalogos/coletas-temporal-proof/manifesto.json' -or $m.predecessor.sha256 -cne '44c330ff177a95963aa0d20b23dc9be7fef9756026eb675f378c40eca14fd79d'){throw 'COLLINK_PREDECESSOR'}
    Exact $m.changedExistingFiles.path $allowed 'COLLINK_DELTAS'
    if($m.ownedExistingChanges -ne 9 -or $m.concurrentExistingChanges -ne 5){throw 'COLLINK_OWNERSHIP'}
    Exact $m.newFiles.path $additions 'COLLINK_ADDITIONS'
    $baseline=Baseline
    if($m.initialFiles -ne $baseline.Count){throw 'COLLINK_BASELINE'}
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        $origin=if($e.path -cin $concurrent){'CONCURRENT_CHANGE_NOT_AUTHORED_IN_THIS_DELIVERY'}elseif($e.path -cin $concurrentDocuments){'THIS_DELIVERY_AND_PRESERVED_CONCURRENT_DOCUMENTATION'}else{'THIS_DELIVERY'}
        if($e.origin -cne $origin){throw 'COLLINK_OWNERSHIP'}
        if(-not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.before -or $e.snapshot -cne ($history+$e.path)){throw 'COLLINK_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
        if($e.path.EndsWith('.md',[StringComparison]::Ordinal)){
            $current=Read $e.path;$old=Read $e.snapshot
            if($e.insertionOffset -lt 0 -or $e.insertionOffset -gt $old.Length -or $current.Length -le $old.Length -or $current.Remove($e.insertionOffset,$current.Length-$old.Length) -cne $old){throw 'COLLINK_HISTORY'}
            $pattern='(?m)^\s*- \[[ xX]\].+$'
            Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($old.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'COLLINK_CHECKBOX'
        }
    }
    foreach($path in $baseline.Keys){if(-not $map.ContainsKey($path)){Hash $path $baseline[$path]}}
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path)){throw 'COLLINK_NOT_NEW'};Hash $e.path $e.sha256}
    $summary=Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 20
    if($summary.fullPhase -cne 'verify-03' -or $summary.directedPhase -cne 'directed-02'){throw 'COLLINK_TEST_REVISION'}
    if(-not $summary.passed -or $summary.full.tests -ne 1530 -or $summary.full.failures -ne 0 -or $summary.full.errors -ne 0 -or $summary.full.skipped -ne 4 -or $summary.directed.tests -ne 272 -or $summary.newTests -ne 38 -or -not $summary.offline -or $summary.physicalSql -or $summary.remoteCalls -ne 0 -or $summary.operationalSidecarEnabled){throw 'COLLINK_VERIFICATION'}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md')){if(-not (Read $path).Contains($m.status)){throw 'COLLINK_POINTER'}}
    if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'COLLINK_INDEX'}
    if($IncludePrivateEvidence){
        $observed=Read 'target/coletas-temporal-link-20260910/concurrent-observed.json'|ConvertFrom-Json
        Exact $observed.path $concurrent 'COLLINK_CONCURRENT_SET'
        foreach($e in $observed){Hash $e.path $e.observed}
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $verification=Read 'target/coletas-temporal-link-20260910/java-verification.json'|ConvertFrom-Json -Depth 20
        if(-not $verification.passed -or $verification.exit -ne 0 -or $verification.sources.Count -ne 770){throw 'COLLINK_JAVA'}
        foreach($e in $verification.sources){Hash $e.path $e.sha256}
    }
    return ,$map
}
$manifest=Read ($catalog+'manifesto.json')|ConvertFrom-Json -Depth 40 -DateKind String
$map=Check $manifest;$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('COLLINK_SCOPE',{param($m)$m.remoteCalls=1}),
        @('COLLINK_SCOPE',{param($m)$m.operationalSidecarEnabled=$true}),
        @('COLLINK_SCOPE',{param($m)$m.newAcceptances=1}),
        @('COLLINK_SCOPE',{param($m)$m.sourceTemporalEquivalenceAccepted=$true}),
        @('COLLINK_OWNERSHIP',{param($m)$m.concurrentExistingChanges=4}),
        @('COLLINK_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('COLLINK_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('COLLINK_ADDITIONS',{param($m)$m.newFiles[0].path='unreviewed.java'}),
        @('COLLINK_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 40|ConvertFrom-Json -Depth 40 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('COLLINK_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
    }
}
if($AsMap){return ,$map}
@{passed=$true;status=$manifest.status;guards=$guards;private=[bool]$IncludePrivateEvidence;newAcceptances=0;remoteCalls=0}|ConvertTo-Json

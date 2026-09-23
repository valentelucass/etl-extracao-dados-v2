#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$catalog='docs/catalogos/coletas-temporal-sql/'
$history='docs/continuidade/historico/coletas-temporal-sql/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-ColetasTemporalLink.ps1')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaTemporalReferenceStore.java',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/PersistirReferenciasTemporaisColetas.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcSqlServerColetaTemporalGateway.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcSqlServerColetaTemporalGatewayTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/graphql/PersistirReferenciasTemporaisColetasTest.java',
    'database/proposals/coletas-temporal/001_coletas_temporal_reference.sql',
    'scripts/validation/Test-ColetasTemporalSqlPhysical.ps1',
    'scripts/validation/Test-ColetasTemporalSql.ps1',
    'docs/adr/0046-coletas-staging-temporal-sql-observacional.md',
    'docs/continuidade/checkpoints/0068-coletas-sql-observacional-qualificado.md',
    'docs/continuidade/checkpoints/0069-bloco63-fechamento-tecnico.md',
    'docs/catalogos/coletas-temporal-sql/RELATORIO.md',
    'docs/catalogos/coletas-temporal-sql/ACEITES.md',
    'docs/catalogos/coletas-temporal-sql/verification-summary.json'
)
function Local([string]$path){
    if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'COLSQL_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
    while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'COLSQL_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
    return $file
}
function Read([string]$path){[IO.File]::ReadAllText((Local $path),$utf8)}
function Hash([string]$path,[string]$expected){if($expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $expected){throw ('COLSQL_HASH_'+$path)}}
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
function CurrentBaseline{
    $all=Baseline
    $path='docs/catalogos/coletas-temporal-link/manifesto.json'
    $sha='ab93c96c287a9f1c21168f0fc3907ca098384b15a3d43aaf5c9ab0934e4bfc63'
    Hash $path $sha
    $previous=Read $path|ConvertFrom-Json -Depth 40 -DateKind String
    foreach($e in $previous.changedExistingFiles){if($all[$e.path] -cne $e.before){throw 'COLSQL_BASELINE_CHAIN'};$all[$e.path]=$e.after}
    foreach($e in $previous.newFiles){$all.Add($e.path,$e.sha256)}
    $all.Add($path,$sha)
    if($all.Count -ne 2433){throw 'COLSQL_BASELINE'}
    return ,$all
}
function VerifySummary($s){
    if(-not $s.passed -or $s.full.tests -ne 1556 -or $s.full.failures -ne 0 -or $s.full.errors -ne 0 -or $s.full.skipped -ne 4 -or $s.newJavaTests -ne 26 -or -not $s.offlineJava -or $s.remoteCalls -ne 0 -or -not $s.sql.passed -or $s.sql.cases.Count -ne 34 -or -not $s.sql.physicalSql -or -not $s.sql.syntheticOnly -or -not $s.sql.transactionalDdl -or $s.sql.durableDdl -or $s.sql.durableDomainRows -ne 0 -or $s.sql.remoteCalls -ne 0 -or $s.sql.sourceTemporalEquivalenceAccepted -or $s.sql.representativeAcceptance){throw 'COLSQL_VERIFICATION'}
    foreach($c in $s.sql.cases){if(-not $c.passed -or -not $c.rollbackConfirmed -or $c.observedError -ne $c.expectedError){throw 'COLSQL_SQL_CASE'}}
    Hash 'database/proposals/coletas-temporal/001_coletas_temporal_reference.sql' $s.sql.proposalSha256
    Hash 'scripts/validation/Test-ColetasTemporalSqlPhysical.ps1' $s.sql.scriptSha256
}
function Check($m){
    if($m.version -ne 1 -or $m.status -cne 'B63_TEMPORAL_TECHNICAL_SCOPE_COMPLETE' -or $m.remoteCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.operationalSidecarEnabled -or $m.sourceTemporalEquivalenceAccepted -or $m.newBudget -or $m.durableDdl){throw 'COLSQL_SCOPE'}
    if($m.predecessor.path -cne 'docs/catalogos/coletas-temporal-link/manifesto.json' -or $m.predecessor.sha256 -cne 'ab93c96c287a9f1c21168f0fc3907ca098384b15a3d43aaf5c9ab0934e4bfc63'){throw 'COLSQL_PREDECESSOR'}
    Exact $m.changedExistingFiles.path $allowed 'COLSQL_DELTAS'
    Exact $m.newFiles.path $additions 'COLSQL_ADDITIONS'
    $baseline=CurrentBaseline
    if($m.initialFiles -ne 2433){throw 'COLSQL_BASELINE'}
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if($baseline[$e.path] -cne $e.before -or $e.snapshot -cne ($history+$e.path)){throw 'COLSQL_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
        if($e.path -cin @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $current=Read $e.path;$old=Read $e.snapshot
            if($e.insertionOffset -lt 0 -or $e.insertionOffset -gt $old.Length -or $current.Length -le $old.Length -or $current.Remove($e.insertionOffset,$current.Length-$old.Length) -cne $old){throw 'COLSQL_HISTORY'}
            $pattern='(?m)^\s*- \[[ xX]\].+$'
            Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($old.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'COLSQL_CHECKBOX'
        }
    }
    foreach($path in $baseline.Keys){if(-not $map.ContainsKey($path)){Hash $path $baseline[$path]}}
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path)){throw 'COLSQL_NOT_NEW'};Hash $e.path $e.sha256}
    VerifySummary (Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 30)
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md')){if(-not (Read $path).Contains($m.status)){throw 'COLSQL_POINTER'}}
    if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'COLSQL_INDEX'}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $v=Read 'target/coletas-temporal-sql-20260910/java-verification.json'|ConvertFrom-Json -Depth 20
        if(-not $v.passed -or $v.exit -ne 0 -or $v.sources.Count -ne 775){throw 'COLSQL_JAVA'}
        foreach($e in $v.sources){Hash $e.path $e.sha256}
        $ledger=@((Read 'target/coletas-temporal-sql-20260910/physical-03/ledger.jsonl') -split '\r?\n'|Where-Object {$_}|ForEach-Object {$_|ConvertFrom-Json})
        if($ledger.Count -ne 68){throw 'COLSQL_LEDGER'}
        for($i=0;$i -lt 68;$i+=2){if($ledger[$i].state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $ledger[$i+1].state -cne 'OBSERVED' -or $ledger[$i].case -cne $ledger[$i+1].case -or -not $ledger[$i+1].result.passed -or -not $ledger[$i+1].result.rollbackConfirmed){throw 'COLSQL_LEDGER'}}
    }
    return ,$map
}
$manifest=Read ($catalog+'manifesto.json')|ConvertFrom-Json -Depth 40 -DateKind String
$map=Check $manifest;$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('COLSQL_SCOPE',{param($m)$m.remoteCalls=1}),
        @('COLSQL_SCOPE',{param($m)$m.operationalSidecarEnabled=$true}),
        @('COLSQL_SCOPE',{param($m)$m.newAcceptances=1}),
        @('COLSQL_SCOPE',{param($m)$m.sourceTemporalEquivalenceAccepted=$true}),
        @('COLSQL_SCOPE',{param($m)$m.durableDdl=$true}),
        @('COLSQL_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('COLSQL_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('COLSQL_ADDITIONS',{param($m)$m.newFiles[0].path='unreviewed.java'}),
        @('COLSQL_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 40|ConvertFrom-Json -Depth 40 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('COLSQL_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
    }
    foreach($mutation in @({param($s)$s.sql.durableDdl=$true},{param($s)$s.sql.cases[0].rollbackConfirmed=$false},{param($s)$s.full.errors=1})){
        $copy=Read ($catalog+'verification-summary.json')|ConvertFrom-Json -Depth 30
        & $mutation $copy
        $rejected=$false;try{VerifySummary $copy}catch{$rejected=$_.Exception.Message -cin @('COLSQL_VERIFICATION','COLSQL_SQL_CASE')}
        if(-not $rejected){throw 'COLSQL_SUMMARY_GUARD'};$guards++
    }
}
if($AsMap){return ,$map}
@{passed=$true;status=$manifest.status;guards=$guards;private=[bool]$IncludePrivateEvidence;newAcceptances=0;remoteCalls=0}|ConvertTo-Json

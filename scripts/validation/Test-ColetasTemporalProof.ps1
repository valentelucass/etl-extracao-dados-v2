#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$linkSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/coletas-temporal-link/manifesto.json')){
    $linkSuccessor=& (Join-Path $PSScriptRoot 'Test-ColetasTemporalLink.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$path){
    if($null -ne $linkSuccessor -and $linkSuccessor.ContainsKey($path)){return $linkSuccessor[$path].snapshot}
    return $path
}
$catalog='docs/catalogos/coletas-temporal-proof/'
$history='docs/continuidade/historico/coletas-temporal-proof/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco63TemporalLocal.ps1')
$added=@($allowed|ForEach-Object {$history+$_})+@('docs/catalogos/coletas-temporal-proof/RELATORIO.md','docs/catalogos/coletas-temporal-proof/source-summary.json','docs/catalogos/coletas-temporal-proof/sql-summary.json','docs/continuidade/checkpoints/0063-coletas-prova-temporal.md','scripts/validation/Test-ColetasTemporalProof.ps1')
function Local([string]$path){
    if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'COLPROOF_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
    while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'COLPROOF_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
    return $file
}
function Read([string]$path){[IO.File]::ReadAllText((Local (Historical $path)),$utf8)}
function Hash([string]$path,[string]$expected){if($expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $path))).Hash.ToLowerInvariant() -cne $expected){throw ('COLPROOF_HASH_'+$path)}}
function Exact($a,$b,[string]$reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}}
function Check($m){
    if($m.version -ne 1 -or $m.status -cne 'COLETAS_TEMPORAL_SOURCE_OBSERVED_SQL_CASES_VERIFIED' -or $m.remoteCalls -ne 5 -or -not $m.sqlSyntheticExecuted -or $m.newAcceptances -ne 0 -or $m.rotationProven -or $m.representativeParityAccepted -or $m.operationalSidecarEnabled){throw 'COLPROOF_SCOPE'}
    if($m.predecessor.path -cne 'docs/continuidade/manifesto-decisao-documental.json' -or $m.predecessor.sha256 -cne 'd98a9d8b988061e41b4d39705fac0417f0ea1a23cdbf3f6a33251184c31ed0a3'){throw 'COLPROOF_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $previous=Read $m.predecessor.path|ConvertFrom-Json -Depth 12
    Exact $m.changedExistingFiles.path $allowed 'COLPROOF_DELTAS'
    Exact $m.newFiles.path $added 'COLPROOF_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        $before=@($previous.changedExistingFiles|Where-Object path -CEQ $e.path)
        if($before.Count -ne 1 -or $e.before -cne $before[0].after -or $e.snapshot -cne ($history+$e.path)){throw 'COLPROOF_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
        if($e.path.EndsWith('.md',[StringComparison]::Ordinal)){
            $current=Read $e.path;$old=Read $e.snapshot
            if($e.insertionOffset -lt 0 -or $e.insertionOffset -gt $old.Length -or $current.Length -le $old.Length -or $current.Remove($e.insertionOffset,$current.Length-$old.Length) -cne $old){throw 'COLPROOF_HISTORY'}
            $pattern='(?m)^\s*- \[[ xX]\].+$'
            Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($old.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'COLPROOF_CHECKBOX'
        }
    }
    foreach($e in $m.newFiles){Hash $e.path $e.sha256}
    $source=Read ($catalog+'source-summary.json')|ConvertFrom-Json -Depth 30
    if($source.calls -ne 5 -or $source.maximumCalls -ne 5 -or $null -ne $source.stopReason -or $source.rawPayloadPersisted -or $source.rotationProven -or $source.representativeParityAccepted -or $source.sourceTemporalEquivalenceAccepted -or $source.snapshotProven){throw 'COLPROOF_SOURCE'}
    if($source.results.Count -ne 5 -or @($source.results|Where-Object {$_.httpStatus -ne 200 -or $_.curlExit -ne 0 -or $null -ne $_.failure}).Count -ne 0 -or $source.replays.Count -ne 2){throw 'COLPROOF_SOURCE_RESULT'}
    foreach($r in $source.replays){if(-not $r.accepted -or $r.quarantinedRows -ne 0 -or $r.preservationDifferences -ne 0 -or $r.physicalRows -ne 5 -or $r.comparison.matchedPhysicalRows -ne 4 -or $r.comparison.fieldDifferences.status_updated_at -ne 4 -or $r.snapshotProven -or $r.representativeParityAccepted -or $r.operationalBindingValidated){throw 'COLPROOF_REPLAY'}}
    $sql=Read ($catalog+'sql-summary.json')|ConvertFrom-Json -Depth 15
    if(-not $sql.passed -or -not $sql.physicalSql -or -not $sql.syntheticOnly -or $sql.ddlExecuted -or $sql.durableDomainRows -ne 0 -or -not $sql.precisionRoundingVerified -or $sql.cases.Count -ne 7){throw 'COLPROOF_SQL'}
    foreach($c in $sql.cases){if(-not $c.passed -or -not $c.rollbackConfirmed -or $c.observedSqlError -ne $c.expectedSqlError){throw 'COLPROOF_SQL_CASE'}}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md')){if(-not (Read $path).Contains($m.status)){throw 'COLPROOF_POINTER'}}
    if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'COLPROOF_INDEX'}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $ledger=@((Read 'target/coletas-temporal-proof-20260910/source-ledger.jsonl') -split '\r?\n'|Where-Object {$_}|ForEach-Object {$_|ConvertFrom-Json})
        if($ledger.Count -ne 10){throw 'COLPROOF_LEDGER_COUNT'}
        $scriptHash=(Get-FileHash -LiteralPath (Local 'target/coletas-temporal-proof-20260910/Source-Proof.executed.ps1')).Hash.ToLowerInvariant()
        for($i=0;$i -lt 10;$i+=2){if($ledger[$i].state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $ledger[$i].scriptSha256 -cne $scriptHash -or $ledger[$i+1].state -cne 'OBSERVED' -or $ledger[$i].operation -cne $ledger[$i+1].operation -or $ledger[$i+1].httpStatus -ne 200 -or $null -ne $ledger[$i+1].failure){throw 'COLPROOF_LEDGER_PAIR'}}
        $guards=Read 'target/coletas-temporal-proof-20260910/source-mode-guards.json'|ConvertFrom-Json
        if(-not $guards.passed -or -not $guards.ledgerUnchanged -or $guards.networkExecuted -or $guards.guards -ne 3){throw 'COLPROOF_MODE_GUARDS'}
    }
    return ,$map
}
$manifest=Read ($catalog+'manifesto.json')|ConvertFrom-Json -Depth 30 -DateKind String
$map=Check $manifest;$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('COLPROOF_SCOPE',{param($m)$m.rotationProven=$true}),
        @('COLPROOF_SCOPE',{param($m)$m.representativeParityAccepted=$true}),
        @('COLPROOF_SCOPE',{param($m)$m.remoteCalls=6}),
        @('COLPROOF_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('COLPROOF_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('COLPROOF_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('COLPROOF_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 30|ConvertFrom-Json -Depth 30 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('COLPROOF_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
    }
}
if($AsMap){
    if($null -ne $linkSuccessor){
        foreach($path in $linkSuccessor.Keys){
            $next=$linkSuccessor[$path]
            if($map.ContainsKey($path)){
                $old=$map[$path]
                if($old.after -cne $next.before){throw 'COLPROOF_SUCCESSOR_CHAIN'}
                $map[$path]=[pscustomobject]@{path=$path;before=$old.before;after=$next.after;snapshot=$old.snapshot}
            }else{$map.Add($path,$next)}
        }
    }
    return ,$map
}
@{passed=$true;status=$manifest.status;guards=$guards;private=[bool]$IncludePrivateEvidence;newAcceptances=0}|ConvertTo-Json

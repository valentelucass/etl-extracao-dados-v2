#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifestPath='docs/catalogos/bloco62-real-replay/manifesto.json'
$history='docs/continuidade/historico/bloco62-real-replay/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco62ColetasFix.ps1')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ColetasSourceReplay.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ColetasSourceReplayTest.java',
    'scripts/probes/Invoke-Bloco62ColetasReplay.ps1',
    'scripts/validation/Test-Bloco62RealReplay.ps1',
    'docs/catalogos/bloco62-real-replay/RELATORIO.md',
    'docs/catalogos/bloco62-real-replay/source-summary.json',
    'docs/continuidade/checkpoints/0057-bloco62-replay-real-coletas.md')
function Local([string]$Path){
    if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B62REPLAY_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B62REPLAY_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local $Path),$utf8)}
function Hash([string]$Path,[string]$Expected){
    if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('B62REPLAY_HASH_'+$Path)}
}
function Exact($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function Check($m){
    if($m.version -ne 1 -or $m.block -ne 62 -or $m.status -cne 'COLETAS_REAL_REPLAY_VERIFIED_PARITY_GAPS_RECORDED' -or -not $m.localPipelineVerified -or -not $m.realReplayVerified -or $m.remoteCalls -ne 5 -or $m.sqlExecuted -or $m.rawPayloadPersisted -or $m.rotationProven -or $m.v2012aAccepted -or $m.newAcceptances -ne 0){throw 'B62REPLAY_SCOPE'}
    if($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0){throw 'B62REPLAY_PROGRESS'}
    if($m.predecessor.path -cne 'docs/catalogos/bloco62-coletas-fix/manifesto.json' -or $m.predecessor.sha256 -cne '7310698489c7e0817af6cde25596eb84d7ad0d082fedf0b172494ff2dd90e151'){throw 'B62REPLAY_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if($baseline.Count -ne 2344 -or $m.initialFiles -ne 2344){throw 'B62REPLAY_BASELINE'}
    Exact $m.changedExistingFiles.path $allowed 'B62REPLAY_DELTAS'
    Exact $m.newFiles.path $additions 'B62REPLAY_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B62REPLAY_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if($m.preservedFiles.Count+$map.Count -ne 2344){throw 'B62REPLAY_PRESERVED_COUNT'}
    foreach($e in $m.preservedFiles){
        if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B62REPLAY_PRESERVED'}
        Hash $e.path $e.sha256
    }
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B62REPLAY_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        $current=Read $path;$before=Read $map[$path].snapshot
        if(-not $current.EndsWith($before,[StringComparison]::Ordinal)){throw 'B62REPLAY_HISTORY'}
        $pattern='(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($before.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B62REPLAY_CHECKBOX'
    }
    $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$resume=Read 'docs/continuidade/RETOMADA.md'
    $boxes=[regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'B62REPLAY_CURRENT_PROGRESS'}
    foreach($text in @($state,$trail,$resume)){if(-not $text.Contains($m.status)){throw 'B62REPLAY_POINTER'}}
    if(@($resume -split "`n").Count -gt 120){throw 'B62REPLAY_INDEX_LIMIT'}
    $source=Read 'docs/catalogos/bloco62-real-replay/source-summary.json'|ConvertFrom-Json -Depth 30
    if($source.calls -ne $m.remoteCalls -or $source.maximumCalls -ne 5 -or $source.rawPayloadPersisted -or $source.rotationProven -or $source.v2012aAccepted -or $source.sqlExecuted){throw 'B62REPLAY_SOURCE_SCOPE'}
    if($null -ne $source.stopReason -or $null -eq $source.replay -or -not $source.replay.accepted -or $source.replay.snapshotProven -or $source.replay.representativeParityAccepted -or $source.replay.operationalBindingValidated -or $source.replay.sqlExecuted -or $source.replay.comparison.globalSetEqualityProven){throw 'B62REPLAY_SOURCE_RESULT'}
    if($source.replay.physicalRows -lt 1 -or $source.replay.stagedRows -ne $source.replay.physicalRows -or $source.replay.quarantinedRows -ne 0 -or $source.replay.preservationDifferences -ne 0){throw 'B62REPLAY_PIPELINE'}
    if($source.results.Count -ne $source.calls){throw 'B62REPLAY_CALL_COUNT'}
    foreach($r in $source.results){if($r.httpStatus -ne 200 -or $r.curlExit -ne 0 -or $null -ne $r.failure){throw 'B62REPLAY_HTTP'}}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $private='target/b62-real-replay-20260910-171610/'
        $ledger=@([IO.File]::ReadAllLines((Local ($private+'ledger.jsonl')))|ForEach-Object {$_|ConvertFrom-Json})
        if($ledger.Count -ne 2*$source.calls){throw 'B62REPLAY_LEDGER_COUNT'}
        for($i=0;$i -lt $source.calls;$i++){
            $reservation=$ledger[2*$i];$observed=$ledger[2*$i+1]
            if($reservation.state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $observed.state -cne 'OBSERVED' -or $reservation.operation -cne $source.results[$i].operation -or $observed.operation -cne $reservation.operation -or $observed.httpStatus -ne $source.results[$i].httpStatus -or $null -ne $observed.failure){throw 'B62REPLAY_LEDGER_SEQUENCE'}
            Hash ($private+'request.json') $reservation.requestSha256
            Hash 'scripts/probes/Invoke-Bloco62ColetasReplay.ps1' $reservation.scriptSha256
            Hash 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ColetasSourceReplay.java' $reservation.javaSha256
            if($i -gt 0 -and ([DateTimeOffset]::Parse($reservation.utc)-[DateTimeOffset]::Parse($ledger[2*$i-1].utc)).TotalSeconds -lt 10){throw 'B62REPLAY_INTERVAL'}
        }
        $java=Read ($private+'java-verification.json')|ConvertFrom-Json
        if(-not $java.passed -or $java.exit -ne 0 -or $java.totals.tests -ne 1448 -or $java.totals.failures -ne 0 -or $java.totals.errors -ne 0 -or $java.physicalProfilesEnabled){throw 'B62REPLAY_JAVA'}
    }
    return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('B62REPLAY_SCOPE',{param($m)$m.realReplayVerified=$false}),
        @('B62REPLAY_SCOPE',{param($m)$m.v2012aAccepted=$true}),
        @('B62REPLAY_SCOPE',{param($m)$m.rotationProven=$true}),
        @('B62REPLAY_SCOPE',{param($m)$m.remoteCalls=6}),
        @('B62REPLAY_PROGRESS',{param($m)$m.progress.now=1}),
        @('B62REPLAY_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('B62REPLAY_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('B62REPLAY_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B62REPLAY_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B62REPLAY_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;remoteCalls=$manifest.remoteCalls;newAcceptances=0;realReplayVerified=$manifest.realReplayVerified;sqlExecuted=$false}|ConvertTo-Json

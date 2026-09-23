#Requires -Version 7.5
param([switch]$AsMap, [switch]$IncludePrivateEvidence, [switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifestPath='docs/catalogos/bloco62-investigacao/manifesto.json'
$history='docs/continuidade/historico/bloco62-investigacao/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco61Local.ps1')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'scripts/probes/Invoke-Bloco62SourceInvestigation.ps1',
    'scripts/validation/Test-Bloco62Investigation.ps1',
    'src/test/java/br/com/esl/etl/v2/contratos/bloco62/SourceObservationBoundaryTest.java',
    'docs/catalogos/bloco62-investigacao/RELATORIO.md',
    'docs/catalogos/bloco62-investigacao/source-summary.json',
    'docs/continuidade/checkpoints/0055-bloco62-investigacao-real-limitada.md')
function Local([string]$Path){
    if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B62_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B62_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local $Path),$utf8)}
function Hash([string]$Path,[string]$Expected){
    if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('B62_HASH_'+$Path)}
}
function Exact($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function Check($m){
    if($m.version -ne 1 -or $m.block -ne 62 -or $m.status -cne 'SOURCE_INVESTIGATION_COMPLETE_CHARACTERIZATION_PENDING' -or -not $m.adoptedByUser -or -not $m.sourceReadExecuted -or $m.remoteCalls -ne 4 -or $m.sqlExecuted -or $m.rawPayloadPersisted -or $m.rotationProven -or $m.v2012aAccepted -or $m.newAcceptances -ne 0){throw 'B62_SCOPE'}
    if($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0){throw 'B62_PROGRESS'}
    if($m.predecessor.path -cne 'docs/catalogos/bloco61-local/manifesto.json' -or $m.predecessor.sha256 -cne '3b9d80d19cbcefbe14273e79e0128764ce96f9970cc9bbf8d5f63e8ba3ca5c66'){throw 'B62_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if($baseline.Count -ne 2313 -or $m.initialFiles -ne 2313){throw 'B62_BASELINE'}
    Exact $m.changedExistingFiles.path $allowed 'B62_DELTAS'
    Exact $m.newFiles.path $additions 'B62_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B62_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if($m.preservedFiles.Count+$map.Count -ne 2313){throw 'B62_PRESERVED_COUNT'}
    foreach($e in $m.preservedFiles){
        if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B62_PRESERVED'}
        Hash $e.path $e.sha256
    }
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B62_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        $current=Read $path;$before=Read $map[$path].snapshot
        if(-not $current.EndsWith($before,[StringComparison]::Ordinal)){throw 'B62_HISTORY'}
        $pattern='(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($before.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B62_CHECKBOX'
    }
    $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$resume=Read 'docs/continuidade/RETOMADA.md'
    $boxes=[regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'B62_CURRENT_PROGRESS'}
    foreach($text in @($state,$trail,$resume)){if(-not $text.Contains($m.status)){throw 'B62_POINTER'}}
    if(@($resume -split "`n").Count -gt 120){throw 'B62_INDEX_LIMIT'}
    $source=Read 'docs/catalogos/bloco62-investigacao/source-summary.json'|ConvertFrom-Json -Depth 30
    if($source.calls -ne 4 -or $source.stoppedOnFailure -or $source.rawPayloadPersisted -or $source.rotationProven -or $source.v2012aAccepted -or $source.mapperExecuted -or $source.sqlExecuted){throw 'B62_SOURCE_SCOPE'}
    Exact $source.results.operation @('USERS_SCHEMA','USERS_SAMPLE','COLETAS_INFO','COLETAS_SAMPLE') 'B62_OPERATIONS'
    foreach($r in $source.results){if($r.httpStatus -ne 200 -or $r.curlExit -ne 0 -or $null -ne $r.failure){throw 'B62_SOURCE_FAILURE'}}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $ledger=@([IO.File]::ReadAllLines((Local 'target/b62-source-20260910-161039/ledger.jsonl'))|ForEach-Object {$_|ConvertFrom-Json})
        if($ledger.Count -ne 8){throw 'B62_LEDGER_COUNT'}
        for($i=0;$i -lt 4;$i++){
            $reservation=$ledger[2*$i];$observed=$ledger[2*$i+1]
            if($reservation.state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $observed.state -cne 'OBSERVED' -or $reservation.operation -cne $source.results[$i].operation -or $observed.operation -cne $reservation.operation -or $observed.httpStatus -ne 200 -or $null -ne $observed.failure){throw 'B62_LEDGER_SEQUENCE'}
            Hash 'target/b62-source-20260910-161039/request.json' $reservation.requestSha256
            Hash 'scripts/probes/Invoke-Bloco62SourceInvestigation.ps1' $reservation.scriptSha256
        }
    }
    return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('B62_SCOPE',{param($m)$m.v2012aAccepted=$true}),
        @('B62_SCOPE',{param($m)$m.rotationProven=$true}),
        @('B62_SCOPE',{param($m)$m.remoteCalls=5}),
        @('B62_PROGRESS',{param($m)$m.progress.now=1}),
        @('B62_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'}),
        @('B62_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B62_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B62_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;remoteCalls=4;newAcceptances=0;rotationProven=$false;v2012aAccepted=$false}|ConvertTo-Json

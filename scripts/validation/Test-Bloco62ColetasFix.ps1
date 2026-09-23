#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$replaySuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco62-real-replay/manifesto.json')){
    $replaySuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco62RealReplay.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){
    if($null -ne $replaySuccessor -and $replaySuccessor.ContainsKey($Path)){return $replaySuccessor[$Path].snapshot}
    return $Path
}
$manifestPath='docs/catalogos/bloco62-coletas-fix/manifesto.json'
$history='docs/continuidade/historico/bloco62-coletas-fix/'
$allowed=@(
    'STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md',
    'scripts/validation/Test-Bloco62Investigation.ps1',
    'scripts/validation/Test-Bloco61Local.ps1',
    'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeLaboratoryContract.java',
    'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalExecution.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportContractObservationConfiguration.java',
    'src/test/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalExecutionTest.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/RuntimeBootstrapTestFixture.java')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'scripts/validation/Test-Bloco62ColetasFix.ps1',
    'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportColetasContractCatalog.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportColetasContractTest.java',
    'src/test/resources/contracts/bloco62/6908-info.sanitized.json',
    'src/test/resources/contracts/bloco62/6908-page.synthetic.json',
    'docs/adr/0043-coletas-contrato-de-captura-e-forma-por-release.md',
    'docs/catalogos/bloco62-coletas-fix/RELATORIO.md',
    'docs/catalogos/bloco62-coletas-fix/diagnostic-summary.json',
    'docs/continuidade/checkpoints/0056-bloco62-correcao-local-coletas.md')
function Local([string]$Path){
    if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B62FIX_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B62FIX_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local (Historical $Path)),$utf8)}
function Hash([string]$Path,[string]$Expected){
    if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B62FIX_HASH_'+$Path)}
}
function Exact($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function Check($m){
    if($m.version -ne 1 -or $m.block -ne 62 -or $m.status -cne 'COLETAS_LOCAL_FIX_VERIFIED_REAL_REPLAY_PENDING' -or -not $m.localPipelineVerified -or $m.realReplayVerified -or $m.remoteCalls -ne 2 -or $m.sqlExecuted -or $m.rawPayloadPersisted -or $m.rotationProven -or $m.v2012aAccepted -or $m.newAcceptances -ne 0){throw 'B62FIX_SCOPE'}
    if($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0){throw 'B62FIX_PROGRESS'}
    if($m.predecessor.path -cne 'docs/catalogos/bloco62-investigacao/manifesto.json' -or $m.predecessor.sha256 -cne 'e318188381c7ed17a91840756152a265ba773b3f8108ef3539dd3ba7cde0305d'){throw 'B62FIX_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if($baseline.Count -ne 2324 -or $m.initialFiles -ne 2324){throw 'B62FIX_BASELINE'}
    Exact $m.changedExistingFiles.path $allowed 'B62FIX_DELTAS'
    Exact $m.newFiles.path $additions 'B62FIX_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B62FIX_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if($m.preservedFiles.Count+$map.Count -ne 2324){throw 'B62FIX_PRESERVED_COUNT'}
    foreach($e in $m.preservedFiles){
        if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B62FIX_PRESERVED'}
        Hash $e.path $e.sha256
    }
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B62FIX_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        $current=Read $path;$before=Read $map[$path].snapshot
        if(-not $current.EndsWith($before,[StringComparison]::Ordinal)){throw 'B62FIX_HISTORY'}
        $pattern='(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($before.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B62FIX_CHECKBOX'
    }
    $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$resume=Read 'docs/continuidade/RETOMADA.md'
    $boxes=[regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'B62FIX_CURRENT_PROGRESS'}
    foreach($text in @($state,$trail,$resume)){if(-not $text.Contains($m.status)){throw 'B62FIX_POINTER'}}
    if(@($resume -split "`n").Count -gt 120){throw 'B62FIX_INDEX_LIMIT'}
    $diagnostic=@(Read 'docs/catalogos/bloco62-coletas-fix/diagnostic-summary.json'|ConvertFrom-Json -Depth 30)
    if($diagnostic.Count -ne 2 -or $diagnostic[0].operation -cne 'info' -or $diagnostic[0].httpStatus -ne 200 -or $diagnostic[1].operation -cne 'data' -or $diagnostic[1].httpStatus -ne 429 -or $diagnostic[1].failure -cne 'HTTP_NON_2XX' -or $null -ne $diagnostic[1].profile){throw 'B62FIX_DIAGNOSTIC'}
    if($diagnostic[0].profile.fields.Count -ne 31 -or $diagnostic[0].profile.filters.Count -ne 6){throw 'B62FIX_METADATA'}
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $private='target/b62-coletas-fix-20260910-163750/'
        $ledger=@([IO.File]::ReadAllLines((Local ($private+'diagnostic-ledger.jsonl')))|ForEach-Object {$_|ConvertFrom-Json})
        if($ledger.Count -ne 4){throw 'B62FIX_LEDGER_COUNT'}
        for($i=0;$i -lt 2;$i++){
            $reservation=$ledger[2*$i];$observed=$ledger[2*$i+1]
            if($reservation.state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $observed.state -cne 'OBSERVED' -or $reservation.operation -cne $diagnostic[$i].operation -or $observed.operation -cne $reservation.operation -or $observed.httpStatus -ne $diagnostic[$i].httpStatus){throw 'B62FIX_LEDGER_SEQUENCE'}
            Hash ($private+'passo-inicial.md') $reservation.requestSha256
            Hash ($private+'diagnose.ps1') $reservation.scriptSha256
        }
        $java=Read ($private+'java-verification.json')|ConvertFrom-Json
        if(-not $java.passed -or $java.exit -ne 0 -or $java.totals.failures -ne 0 -or $java.totals.errors -ne 0 -or $java.physicalProfilesEnabled){throw 'B62FIX_JAVA'}
    }
    return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('B62FIX_SCOPE',{param($m)$m.realReplayVerified=$true}),
        @('B62FIX_SCOPE',{param($m)$m.rotationProven=$true}),
        @('B62FIX_SCOPE',{param($m)$m.remoteCalls=3}),
        @('B62FIX_PROGRESS',{param($m)$m.progress.now=1}),
        @('B62FIX_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('B62FIX_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('B62FIX_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B62FIX_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B62FIX_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }
}
if($AsMap){
    if($null -ne $replaySuccessor){
        foreach($entry in $replaySuccessor.GetEnumerator()){
            if($result.ContainsKey($entry.Key)){
                if($result[$entry.Key].after -cne $entry.Value.before){throw 'B62FIX_REPLAY_CHAIN'}
                $result[$entry.Key].after=$entry.Value.after
            }else{$result.Add($entry.Key,$entry.Value)}
        }
    }
    return ,$result
}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;remoteCalls=2;newAcceptances=0;realReplayVerified=$false;sqlExecuted=$false}|ConvertTo-Json

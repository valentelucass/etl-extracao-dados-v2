#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$temporalSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco63-temporal-local/manifesto.json')){
    $temporalSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco63TemporalLocal.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){
    if($null -ne $temporalSuccessor -and $temporalSuccessor.ContainsKey($Path)){return $temporalSuccessor[$Path].snapshot}
    return $Path
}
$manifestPath='docs/catalogos/bloco63-preparacao/manifesto.json'
$history='docs/continuidade/historico/bloco63-preparacao/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco62RealReplay.ps1')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'docs/runbooks/prompt-bloco-63-semantica-temporal-coletas.md',
    'docs/catalogos/bloco63-preparacao/README.md',
    'docs/continuidade/checkpoints/0058-preparacao-bloco63-coletas-temporal.md',
    'scripts/validation/Test-Bloco63Preparation.ps1')
function Local([string]$Path){
    if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B63PREP_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B63PREP_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local (Historical $Path)),$utf8)}
function Hash([string]$Path,[string]$Expected){
    if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B63PREP_HASH_'+$Path)}
}
function Exact($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function Check($m){
    if($m.version -ne 1 -or $m.block -ne 63 -or $m.status -cne 'B63_PROPOSTO_COLETAS_TEMPORAL_LOCAL_NAO_EXECUTADO' -or -not $m.prepared -or $m.adopted -or $m.executed -or $m.remoteCalls -ne 0 -or $m.sqlExecuted -or $m.newAcceptances -ne 0 -or $m.newBudget -or $m.v2012aAccepted -or $m.rotationProven){throw 'B63PREP_SCOPE'}
    if($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0){throw 'B63PREP_PROGRESS'}
    Exact $m.fronts @('A','B','C','D') 'B63PREP_FRONTS'
    if($m.predecessor.path -cne 'docs/catalogos/bloco62-real-replay/manifesto.json' -or $m.predecessor.sha256 -cne '871a433e74bbbaedbb6a788e993e37bd32ff40fc8bdbb735946b9415aba22f91'){throw 'B63PREP_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if($baseline.Count -ne 2356 -or $m.initialFiles -ne 2356){throw 'B63PREP_BASELINE'}
    Exact $m.changedExistingFiles.path $allowed 'B63PREP_DELTAS'
    Exact $m.newFiles.path $additions 'B63PREP_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B63PREP_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if($m.preservedFiles.Count+$map.Count -ne 2356){throw 'B63PREP_PRESERVED_COUNT'}
    foreach($e in $m.preservedFiles){
        if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B63PREP_PRESERVED'}
        Hash $e.path $e.sha256
    }
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B63PREP_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        $current=Read $path;$before=Read $map[$path].snapshot
        if(-not $current.EndsWith($before,[StringComparison]::Ordinal)){throw 'B63PREP_HISTORY'}
        $pattern='(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($before.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B63PREP_CHECKBOX'
    }
    $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$resume=Read 'docs/continuidade/RETOMADA.md'
    $boxes=[regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'B63PREP_CURRENT_PROGRESS'}
    foreach($text in @($state,$trail,$resume)){if(-not $text.Contains($m.status)){throw 'B63PREP_POINTER'}}
    if(@($resume -split "`n").Count -gt 120){throw 'B63PREP_INDEX_LIMIT'}
    $prompt=Read 'docs/runbooks/prompt-bloco-63-semantica-temporal-coletas.md'
    foreach($text in @('PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO','## A —','## B —','## C —','## D —','Não executar API','Não definir ou consumir novo orçamento')){if(-not $prompt.Contains($text)){throw 'B63PREP_PROMPT'}}
    if($IncludePrivateEvidence){foreach($e in $m.evidence){Hash $e.path $e.sha256}}
    return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest
$guards=0
if($SelfTest){
    foreach($tuple in @(
        @('B63PREP_SCOPE',{param($m)$m.adopted=$true}),
        @('B63PREP_SCOPE',{param($m)$m.executed=$true}),
        @('B63PREP_SCOPE',{param($m)$m.remoteCalls=1}),
        @('B63PREP_SCOPE',{param($m)$m.newBudget=$true}),
        @('B63PREP_SCOPE',{param($m)$m.v2012aAccepted=$true}),
        @('B63PREP_PROGRESS',{param($m)$m.progress.now=1}),
        @('B63PREP_FRONTS',{param($m)$m.fronts=@('A','B','D')}),
        @('B63PREP_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('B63PREP_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('B63PREP_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B63PREP_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B63PREP_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }
}
if($AsMap){
    if($null -ne $temporalSuccessor){
        foreach($entry in $temporalSuccessor.GetEnumerator()){
            if($result.ContainsKey($entry.Key)){
                if($result[$entry.Key].after -cne $entry.Value.before){throw 'B63PREP_TEMPORAL_CHAIN'}
                $result[$entry.Key].after=$entry.Value.after
            }else{$result.Add($entry.Key,$entry.Value)}
        }
    }
    return ,$result
}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;remoteCalls=0;newAcceptances=0;executed=$false}|ConvertTo-Json

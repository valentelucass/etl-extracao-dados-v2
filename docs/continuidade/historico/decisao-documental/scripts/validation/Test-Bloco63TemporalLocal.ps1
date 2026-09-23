#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifestPath='docs/catalogos/bloco63-temporal-local/manifesto.json'
$history='docs/continuidade/historico/bloco63-temporal-local/'
$allowed=@(
    'STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md',
    'scripts/validation/Test-Bloco63Preparation.ps1',
    'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java',
    'src/test/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapperTest.java',
    'src/test/java/br/com/esl/etl/v2/contratos/bloco58/ColetasCharacterization.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/JdbcSqlServerColetaGatewaysTest.java')
$additions=@($allowed|ForEach-Object {$history+$_})+@(
    'docs/adr/0044-coletas-temporal-local-sem-inferir-instante-de-status.md',
    'docs/catalogos/bloco63-temporal-local/MAPA-TEMPORAL.md',
    'docs/catalogos/bloco63-temporal-local/PROVA-REPRESENTATIVA.md',
    'docs/catalogos/bloco63-temporal-local/inputs-prova.json',
    'docs/catalogos/bloco63-temporal-local/RELATORIO.md',
    'docs/continuidade/checkpoints/0059-bloco63-mapa-temporal.md',
    'docs/continuidade/checkpoints/0060-bloco63-regressoes-e-inputs.md',
    'docs/continuidade/checkpoints/0061-bloco63-temporal-local-concluido.md',
    'src/test/java/br/com/esl/etl/v2/contratos/bloco58/ColetasCurrentTemporalTest.java',
    'scripts/validation/Test-Bloco63TemporalLocal.ps1')
function Local([string]$Path){
    if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B63LOCAL_PATH'}
    $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B63LOCAL_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local $Path),$utf8)}
function Hash([string]$Path,[string]$Expected){
    if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('B63LOCAL_HASH_'+$Path)}
}
function Exact($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function CheckInputs($inputs){
    if($inputs.authorizationGranted -or $null -ne $inputs.callBudget -or $inputs.representativeParityAccepted -or $inputs.snapshotProven -or $inputs.sqlExecuted -or $inputs.graphQl.operationalSidecarEnabled){throw 'B63LOCAL_INPUT_SCOPE'}
    $limits=$inputs.technicalLimitsNotAuthorization
    if($limits.maximumInputBytes -ne 65536 -or $limits.maximumRows -ne 1000 -or $limits.maximumPages -ne 100 -or $limits.maximumDepth -ne 16 -or $limits.maximumPaths -ne 256 -or $limits.maximumNodes -ne 4096 -or $limits.maximumPerDistinctIds -ne 100 -or $limits.maximumMicrobatch -ne 100){throw 'B63LOCAL_INPUT_LIMITS'}
    if($inputs.dataExport.fingerprint -cne 'b143864a5bbde9ccc3f36f488cbcf0d67de1034743987104b793736f00beefe9' -or $inputs.dataExport.responseForm -cne 'ROOT_ARRAY' -or $inputs.dataExport.missingStatusTimestamp -cne 'status_updated_at'){throw 'B63LOCAL_INPUT_CONTRACT'}
}
function Check($m){
    if($m.version -ne 1 -or $m.block -ne 63 -or $m.status -cne 'B63_TEMPORAL_LOCAL_COMPLETE_EXTERNAL_GAPS_OPEN' -or -not $m.prepared -or -not $m.adopted -or -not $m.executed -or $m.remoteCalls -ne 0 -or $m.sqlExecuted -or $m.newAcceptances -ne 0 -or $m.newBudget -or $m.v2012aAccepted -or $m.rotationProven){throw 'B63LOCAL_SCOPE'}
    if($m.progress.done -ne 67 -or $m.progress.total -ne 115 -or $m.progress.openRoutes -ne 191 -or $m.progress.now -ne 0){throw 'B63LOCAL_PROGRESS'}
    Exact $m.fronts @('A','B','C','D') 'B63LOCAL_FRONTS'
    if($m.predecessor.path -cne 'docs/catalogos/bloco63-preparacao/manifesto.json' -or $m.predecessor.sha256 -cne '0acd8a5cb6bb776c5b2b85f36d42ac77d9704058e2acbd495f4418be74f637b3'){throw 'B63LOCAL_PREDECESSOR'}
    Hash $m.predecessor.path $m.predecessor.sha256
    $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
    $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
    if($baseline.Count -ne 2365 -or $m.initialFiles -ne 2365){throw 'B63LOCAL_BASELINE'}
    Exact $m.changedExistingFiles.path $allowed 'B63LOCAL_DELTAS'
    Exact $m.newFiles.path $additions 'B63LOCAL_ADDITIONS'
    $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    foreach($e in $m.changedExistingFiles){
        if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B63LOCAL_BEFORE'}
        Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
    }
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if($m.preservedFiles.Count+$map.Count -ne 2365){throw 'B63LOCAL_PRESERVED_COUNT'}
    foreach($e in $m.preservedFiles){
        if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'B63LOCAL_PRESERVED'}
        Hash $e.path $e.sha256
    }
    foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B63LOCAL_NEW_COLLISION'};Hash $e.path $e.sha256}
    foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
        $current=Read $path;$before=Read $map[$path].snapshot
        if(-not $current.EndsWith($before,[StringComparison]::Ordinal)){throw 'B63LOCAL_HISTORY'}
        $pattern='(?m)^\s*- \[[ xX]\].+$'
        Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($before.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B63LOCAL_CHECKBOX'
    }
    $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md';$resume=Read 'docs/continuidade/RETOMADA.md'
    $boxes=[regex]::Matches($state,'(?m)^\s*- \[([ xX])\]')
    if($boxes.Count -ne 115 -or @($boxes|Where-Object {$_.Groups[1].Value -match '[xX]'}).Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA \|'){throw 'B63LOCAL_CURRENT_PROGRESS'}
    foreach($text in @($state,$trail,$resume)){if(-not $text.Contains($m.status)){throw 'B63LOCAL_POINTER'}}
    if(@($resume -split "`n").Count -gt 120){throw 'B63LOCAL_INDEX_LIMIT'}
    $prompt=Read 'docs/runbooks/prompt-bloco-63-semantica-temporal-coletas.md'
    foreach($text in @('PROPOSTO_NAO_ADOTADO_NAO_EXECUTADO','## A —','## B —','## C —','## D —','Não executar API','Não definir ou consumir novo orçamento')){if(-not $prompt.Contains($text)){throw 'B63LOCAL_PROMPT'}}
    CheckInputs (Read 'docs/catalogos/bloco63-temporal-local/inputs-prova.json'|ConvertFrom-Json -Depth 20)
    if($IncludePrivateEvidence){
        foreach($e in $m.evidence){Hash $e.path $e.sha256}
        $proof=Read 'target/b63-temporal-local-20260910/java-verification.json'|ConvertFrom-Json -Depth 20
        if(-not $proof.passed -or $proof.exit -ne 0 -or -not $proof.offline -or $proof.physicalProfilesEnabled -or $proof.totals.failures -ne 0 -or $proof.totals.errors -ne 0 -or $proof.totals.tests -ne $m.javaTests -or $proof.totals.skipped -ne $m.javaSkips){throw 'B63LOCAL_JAVA_EVIDENCE'}
        Exact $proof.sourceFiles.path (@($allowed|Where-Object {$_ -like '*.java'})+@('src/test/java/br/com/esl/etl/v2/contratos/bloco58/ColetasCurrentTemporalTest.java')) 'B63LOCAL_TESTED_SOURCES'
        foreach($source in $proof.sourceFiles){Hash $source.path $source.sha256}
    }
    return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest
$guards=0
if($SelfTest){
    $original=Read 'docs/catalogos/bloco63-temporal-local/inputs-prova.json'|ConvertFrom-Json -Depth 20
    foreach($tuple in @(
        @('B63LOCAL_INPUT_SCOPE',{param($i)$i.authorizationGranted=$true}),
        @('B63LOCAL_INPUT_SCOPE',{param($i)$i.callBudget=1}),
        @('B63LOCAL_INPUT_SCOPE',{param($i)$i.representativeParityAccepted=$true}),
        @('B63LOCAL_INPUT_SCOPE',{param($i)$i.graphQl.operationalSidecarEnabled=$true}),
        @('B63LOCAL_INPUT_LIMITS',{param($i)$i.technicalLimitsNotAuthorization.maximumInputBytes=65537}),
        @('B63LOCAL_INPUT_LIMITS',{param($i)$i.technicalLimitsNotAuthorization.maximumRows=1001}),
        @('B63LOCAL_INPUT_CONTRACT',{param($i)$i.dataExport.responseForm='ENVELOPE_DATA_ARRAY'})
    )){
        $copy=$original|ConvertTo-Json -Depth 20|ConvertFrom-Json -Depth 20
        & $tuple[1] $copy
        $reason='ACCEPTED';try{CheckInputs $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B63LOCAL_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }

    foreach($tuple in @(
        @('B63LOCAL_SCOPE',{param($m)$m.adopted=$false}),
        @('B63LOCAL_SCOPE',{param($m)$m.executed=$false}),
        @('B63LOCAL_SCOPE',{param($m)$m.remoteCalls=1}),
        @('B63LOCAL_SCOPE',{param($m)$m.newBudget=$true}),
        @('B63LOCAL_SCOPE',{param($m)$m.v2012aAccepted=$true}),
        @('B63LOCAL_PROGRESS',{param($m)$m.progress.now=1}),
        @('B63LOCAL_FRONTS',{param($m)$m.fronts=@('A','B','D')}),
        @('B63LOCAL_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('B63LOCAL_DELTAS',{param($m)$m.changedExistingFiles[0].path='database/unreviewed.sql'}),
        @('B63LOCAL_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside.md'}),
        @('B63LOCAL_ADDITIONS',{param($m)$m.newFiles[0].path='src/unreviewed.java'})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
        & $tuple[1] $copy
        $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $tuple[0]){throw ('B63LOCAL_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
        $guards++
    }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;remoteCalls=0;newAcceptances=0;executed=$true}|ConvertTo-Json

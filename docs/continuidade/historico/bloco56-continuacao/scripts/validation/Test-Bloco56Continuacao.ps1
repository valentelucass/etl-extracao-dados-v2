#Requires -Version 7.5
param([switch]$AsMap, [switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Local-File([string]$Path) {
    if($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B56C_PATH'}
    $file=Join-Path $root $Path
    $node=Get-Item -LiteralPath $file
    while($node.FullName -cne $root){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B56C_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
    return $file
}
function Read-Text([string]$Path){
    $file=Local-File $Path
    if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'B56C_FILE_BOUND'}
    return $utf8.GetString([IO.File]::ReadAllBytes($file))
}
function Read-Json([string]$Path){return (Read-Text $Path|ConvertFrom-Json -Depth 60 -DateKind String)}
function Check-Hash([string]$Path,[string]$Hash){
    if($Hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local-File $Path)).Hash.ToLowerInvariant() -cne $Hash){throw ('B56C_HASH_'+$Path)}
}
function Exact-Set($Actual,$Expected,[string]$Reason){
    if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}
}
function Require-False($Value){if($Value -isnot [bool] -or $Value){throw 'B56C_UNPROVEN_ACCEPTANCE_OR_EFFECT'}}

$prefix='docs/catalogos/bloco56-continuacao/'
$m=Read-Json ($prefix+'manifesto.json')
if($m.version -ne 1 -or $m.status -cne 'REMOTE_INVESTIGATION_COMPLETE_IDENTITIES_BLOCKED'){throw 'B56C_STATE'}
if($m.predecessor.path -cne 'docs/catalogos/bloco56/manifesto.json' -or $m.predecessor.sha256 -cne 'cf0e9dceacdc2a8b82fdeffad13e2d566c4bda4e6c27df72a7305f1d6dccdcfa'){throw 'B56C_PREDECESSOR'}
Check-Hash $m.predecessor.path $m.predecessor.sha256
foreach($field in @('newCanonicalAcceptances','newIdentityAcceptances','newVerticalImplementations','sqlOperations','runtimeCampaigns','installations','unknownResults')){if($m.$field -ne 0){throw 'B56C_NO_NEW_ACCEPTANCE_OR_RUNTIME'}}
foreach($field in @('rotationProven','credentialsChanged','budgetTransferred','validityRenewed')){Require-False $m.$field}
if($m.v2041 -cne 'UNRESOLVED' -or $m.sourceReadCalls -ne 11 -or $m.sourceDataRows -ne 8 -or $m.eligibleVerticals.Count -ne 0){throw 'B56C_EVIDENCE_SCOPE'}
foreach($pair in @(@('done',65),@('total',115),@('pending',50),@('openRoutes',193),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'B56C_PROGRESS'}}

$prior=Read-Json $m.predecessor.path
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $prior.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $prior.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $prior.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1352){throw 'B56C_BASELINE_COUNT'}
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco56Preparacao.ps1','scripts/validation/Test-Bloco56PreparacaoGuards.ps1','scripts/security/Invoke-OfflineSecretScan.ps1','scripts/validation/Test-ContinuidadeAgentes.ps1')
Exact-Set $m.changedExistingFiles.path $allowed 'B56C_EXACT_SEVEN_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
    if($e.before -cne $baseline[$e.path] -or $e.snapshot -cne ('docs/continuidade/historico/bloco56-preparacao/'+$e.path)){throw 'B56C_SUCCESSION'}
    Check-Hash $e.snapshot $e.before
    Check-Hash $e.path $e.after
    $map.Add($e.path,$e)
}
foreach($path in $baseline.Keys){if(-not $map.ContainsKey($path)){Check-Hash $path $baseline[$path]}}
$required=@($m.changedExistingFiles.snapshot)+@(
    'scripts/probes/Invoke-Bloco56IdentityMetadataProbe.ps1','scripts/probes/Invoke-Bloco56IdentityDataProbe.ps1','scripts/probes/Invoke-Bloco56SchemaProbe.ps1',
    'scripts/validation/Test-Bloco56Continuacao.ps1','scripts/validation/Test-Bloco56ContinuacaoGuards.ps1','scripts/validation/Test-Bloco56SecretExtensions.ps1','scripts/validation/Test-Bloco56ProbeGuards.ps1',
    'docs/continuidade/checkpoints/0005-bloco56-metadados-remotos.md','docs/continuidade/checkpoints/0006-bloco56-amostras-remotas.md','docs/continuidade/checkpoints/0007-bloco56-schema-e-limites.md','docs/continuidade/checkpoints/0008-bloco56-validacao-da-continuacao.md',
    ($prefix+'README.md'),($prefix+'schema-root.graphql'),($prefix+'schema-types.graphql'),($prefix+'schema-relations.graphql'))
foreach($id in @(8636,4924,10633,6392)){foreach($kind in @('info','data')){$required+=($prefix+'evidencias/'+$kind+'-'+$id+'.json')}}
foreach($stage in @('root','types','relations')){$required+=($prefix+'evidencias/schema-'+$stage+'.json')}
foreach($stage in @('info','data','schema-root','schema-types','schema-relations')){
    $required+=($prefix+'evidencias/request-'+$stage+'.json')
    $required+=($prefix+'evidencias/'+$stage+'-ledger.jsonl')
}
Exact-Set $m.newFiles.path $required 'B56C_EXACT_NEW_FILES'
foreach($e in $m.newFiles){Check-Hash $e.path $e.sha256}

$calls=0;$rows=0
foreach($stage in @('info','data','schema-root','schema-types','schema-relations')){
    $planPath=$prefix+'evidencias/request-'+$stage+'.json'
    $plan=Read-Json $planPath
    $planHash=(Get-FileHash -LiteralPath (Local-File $planPath)).Hash.ToLowerInvariant()
    $records=@((Read-Text ($prefix+'evidencias/'+$stage+'-ledger.jsonl')) -split '\r?\n'|Where-Object{$_}|ForEach-Object{$_|ConvertFrom-Json})
    $expectedCalls=if($stage -in @('info','data')){4}else{1}
    if($plan.maximumCalls -ne $expectedCalls -or $records.Count -ne 2*$expectedCalls){throw 'B56C_LEDGER_COUNT'}
    for($i=0;$i -lt $records.Count;$i+=2){
        $reserve=$records[$i];$observed=$records[$i+1]
        if($reserve.state -cne 'RESERVED_OUTCOME_UNKNOWN' -or $reserve.requestSha256 -cne $planHash -or $observed.state -cne 'OBSERVED' -or $observed.httpStatus -ne 200 -or $null -ne $observed.failure){throw 'B56C_LEDGER_OUTCOME'}
        if($stage -in @('info','data')){
            if($reserve.template -ne $observed.template -or $reserve.template -ne $plan.templates[$i/2]){throw 'B56C_LEDGER_TARGET'}
            $result=Read-Json ($prefix+'evidencias/'+$stage+'-'+$reserve.template+'.json')
        }else{
            if($reserve.stage -cne $stage.Substring(7) -or $observed.stage -cne $reserve.stage){throw 'B56C_LEDGER_TARGET'}
            Check-Hash $plan.document $reserve.documentSha256
            $doc=Read-Text $plan.document
            if($doc -cnotmatch '^query B56Schema' -or $doc -match '\b(mutation|subscription)\b' -or $doc -notmatch '__schema|__type'){throw 'B56C_INTROSPECTION_ONLY'}
            $result=Read-Json ($prefix+'evidencias/'+$stage+'.json')
        }
        if($result.httpStatus -ne 200 -or $result.curlExit -ne 0 -or $null -ne $result.failure -or $result.bytes -gt 10MB+128){throw 'B56C_HTTP_RESULT'}
        if($stage -eq 'data'){
            $rows+=$result.sample.physicalRows
            if($result.sample.physicalRows -ne 2 -or $result.sample.profiledRows -ne 2){throw 'B56C_SAMPLE_BOUND'}
            Require-False $result.sample.physicalLimitExceeded
            Require-False $result.sample.universalUniquenessProven
            Require-False $result.sample.stabilityProven
            foreach($candidate in $result.sample.candidateProfiles){Require-False $candidate.identityProven}
        }
        $calls++
    }
}
if($calls -ne 11 -or $rows -ne 8){throw 'B56C_EVIDENCE_COUNT'}
$dataPlan=Read-Json ($prefix+'evidencias/request-data.json')
if($dataPlan.page -ne 1 -or $dataPlan.per -ne 2 -or $dataPlan.windowStart -cne '2026-09-04' -or $dataPlan.windowEnd -cne '2026-09-04'){throw 'B56C_REQUEST_WINDOW'}
Exact-Set $dataPlan.filters.'8636' @('accounting_debits.issue_date','accounting_debits.created_at') 'B56C_CAP_BOTH_FILTERS'
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
    $current=Read-Text $path;$old=Read-Text $map[$path].snapshot
    Exact-Set @([regex]::Matches($current,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) @([regex]::Matches($old,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) 'B56C_ORIGINAL_ACCEPTANCE_OR_ROUTE_CHANGED'
}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md')){
    if(-not (Read-Text $path).Contains('B56_INVESTIGACAO_REMOTA_REGISTRADA_IDENTIDADES_BLOQUEADAS')){throw 'B56C_POINTER'}
}
if(@((Read-Text 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'B56C_POINTER_BOUND'}
if($IncludePrivateEvidence){
    Check-Hash $m.initialInventory.path $m.initialInventory.sha256
    $initial=Read-Json $m.initialInventory.path
    if($initial.Count -ne $baseline.Count){throw 'B56C_INITIAL_INVENTORY'}
    foreach($e in $initial){if($baseline[$e.path] -cne $e.sha256){throw 'B56C_INITIAL_REVISION'};Check-Hash ('target/bloco56-continuacao/initial/'+$e.path) $e.sha256}
    foreach($e in $m.privateEvidence){Check-Hash $e.path $e.sha256}
}
if($AsMap){return ,$map}
'B56_CONTINUATION_PASS_ELEVEN_READS_FOUR_HOLDS_ZERO_NEW_ACCEPTANCES'

#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco57-local/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-Bloco57Local.ps1') -AsMap
}
function Historical([string]$Path){if($null -ne $successor -and $successor.ContainsKey($Path)){return $successor[$Path].snapshot};return $Path}
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'ESLD_PATH'}
 $file=Join-Path $root $Path;$node=Get-Item -LiteralPath $file
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'ESLD_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$Path){$file=Local (Historical $Path);if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'ESLD_READ_BOUND'};return $utf8.GetString([IO.File]::ReadAllBytes($file))}
function Json([string]$Path){return (Read $Path|ConvertFrom-Json -Depth 60 -DateKind String)}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('ESLD_HASH_'+$Path)}}
function Exact($Actual,$Expected,[string]$Reason){if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}}
$prefix='docs/catalogos/esl-documentacao/'
$m=Json ($prefix+'manifesto.json')
if($m.version -ne 1 -or $m.status -cne 'PROVIDER_DOCUMENTATION_REGISTERED_NO_NEW_ACCEPTANCE'){throw 'ESLD_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/raster-contrato-local/manifesto.json' -or $m.predecessor.sha256 -cne 'fc1fc85f83d8f08dfae8b2b80726548093428962c03351b6644fea16b8004bd7'){throw 'ESLD_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
foreach($key in @('newCanonicalAcceptances','newIdentityAcceptances','newVerticalImplementations','sourceApiRequestsExecuted','sqlOperations','runtimeCampaigns','budgetConsumed','unknownEffects')){if($m.$key -ne 0){throw 'ESLD_NO_ACCEPTANCE_OR_OPERATION'}}
if($m.publicDocumentationRead -isnot [bool] -or -not $m.publicDocumentationRead){throw 'ESLD_DOCUMENTATION_READ_REQUIRED'}
$raster=Json $m.predecessor.path;$bc=Json $raster.predecessor.path;$prep=Json $bc.predecessor.path
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $prep.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $prep.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $prep.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($bc.predecessor.path,$bc.predecessor.sha256)
foreach($phase in @($bc,$raster)){
 foreach($e in $phase.changedExistingFiles){if($baseline[$e.path] -cne $e.before){throw 'ESLD_BASELINE_CHAIN'};$baseline[$e.path]=$e.after}
 foreach($e in $phase.newFiles){$baseline.Add($e.path,$e.sha256)}
 if($phase -eq $bc){$baseline.Add($raster.predecessor.path,$raster.predecessor.sha256)}else{$baseline.Add($m.predecessor.path,$m.predecessor.sha256)}
}
if($baseline.Count -ne 1422){throw 'ESLD_BASELINE_COUNT'}
$allowed=@('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','scripts/validation/Test-RasterLocalContinuation.ps1','scripts/validation/Test-RasterLocalContinuationGuards.ps1')
Exact $m.changedExistingFiles.path $allowed 'ESLD_EXACT_FIVE_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.before -cne $baseline[$e.path] -or $e.snapshot -cne ('docs/continuidade/historico/bloco56-raster/'+$e.path)){throw 'ESLD_SNAPSHOT_CHAIN'}
 Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
}
foreach($path in $baseline.Keys){if(-not $map.ContainsKey($path)){Hash $path $baseline[$path]}}
$required=@($m.changedExistingFiles.snapshot)+@(($prefix+'README.md'),($prefix+'fonte.json'),($prefix+'endpoints.csv'),($prefix+'graphql-operacoes.csv'),'docs/continuidade/checkpoints/0011-documentacao-esl-postman.md','scripts/validation/Test-EslDocumentation.ps1')
Exact $m.newFiles.path $required 'ESLD_EXACT_ADDITIONS'
foreach($e in $m.newFiles){Hash $e.path $e.sha256}
$url='https://documenter.getpostman.com/view/20571375/2s9YXk2fj5'
$source=Json ($prefix+'fonte.json')
if($source.title -cne 'TMS ESL CLOUD' -or $source.url -cne $url -or $source.indicatedBy -cne 'PROJECT_OWNER' -or $source.userProvidedAnchor -cne ($url+'#1843cd33-1848-438c-875b-6262107c5e94')){throw 'ESLD_OFFICIAL_SOURCE_BINDING'}
if($source.status -cne 'PROVIDER_DOCUMENTATION_REGISTERED_IDENTITIES_NOT_ACCEPTED'){throw 'ESLD_SOURCE_NOT_IDENTITY_PROOF'}
foreach($key in @('sourceApiRequestsExecuted','sqlOperations','newIdentityAcceptances','newCanonicalAcceptances')){if($source.$key -ne 0){throw 'ESLD_SOURCE_NO_OPERATION'}}
$requests=@((Read ($prefix+'endpoints.csv'))|ConvertFrom-Csv)
$operations=@((Read ($prefix+'graphql-operacoes.csv'))|ConvertFrom-Csv)
if($requests.Count -ne 191 -or $operations.Count -ne 180 -or $source.collection.requests -ne 191 -or $source.graphqlDocumentation.operationAnchors -ne 180){throw 'ESLD_INDEX_COUNT'}
foreach($pair in @(@('GET',39),@('POST',146),@('PATCH',1),@('DELETE',5))){if(@($requests|Where-Object method -CEQ $pair[0]).Count -ne $pair[1]){throw 'ESLD_METHOD_COUNT'}}
foreach($entry in $requests){if($entry.classification -cne 'DOCUMENTED_REQUEST_NOT_EXECUTED' -or -not $entry.documentation.StartsWith($url+'#',[StringComparison]::Ordinal) -or $entry.path -match '^https?://|/[0-9]+(?:/|$)'){throw 'ESLD_INDEX_SCOPE'}}
foreach($entry in $operations){if($entry.classification -cne 'DOCUMENTATION_ANCHOR_NOT_EXECUTED' -or $entry.documentation -cne ('https://demonstracao.eslcloud.com.br/graphql_docs#operation-'+$entry.anchor)){throw 'ESLD_GRAPHQL_INDEX_SCOPE'}}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$old=Read $map[$path].snapshot
 Exact @([regex]::Matches($current,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) @([regex]::Matches($old,'(?m)^\s*- \[[ xX]\].+$')|ForEach-Object Value) 'ESLD_ORIGINAL_ACCEPTANCES_CHANGED'
}
foreach($path in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){if(-not (Read $path).Contains('ESL_DOCUMENTACAO_POSTMAN_REGISTRADA')){throw 'ESLD_POINTER'}}
if(-not (Read 'STATES.md').Contains($source.userProvidedAnchor)){throw 'ESLD_STATES_URL'}
if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'ESLD_POINTER_BOUND'}
$state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'ESLD_PROGRESS_CHANGED'}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Json $m.initialInventory.path
 if($initial.Count -ne 1422){throw 'ESLD_PRIVATE_INVENTORY'}
 foreach($entry in $initial){if($baseline[$entry.path] -cne $entry.sha256){throw 'ESLD_PRIVATE_BASELINE'}}
 foreach($entry in $m.changedExistingFiles){Hash ('target/esl-documentacao-postman/initial/'+$entry.path) $entry.before}
 foreach($entry in $m.privateEvidence){Hash $entry.path $entry.sha256}
}
if($null -ne $successor){
 foreach($path in $successor.Keys){
  if($map.ContainsKey($path)){
   if($map[$path].after -cne $successor[$path].before){throw 'ESLD_B57_CHAIN'}
   $map[$path].after=$successor[$path].after
  }else{$map.Add($path,$successor[$path])}
 }
}
if($AsMap){return ,$map}
'ESL_DOCUMENTATION_PASS_191_REQUESTS_180_ANCHORS_NO_NEW_ACCEPTANCE'

#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B57_PATH'}
 $file=Get-Item -LiteralPath (Join-Path $root $path)
 $node=$file
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B57_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file.FullName
}
function Read([string]$path){$p=Local $path;if((Get-Item -LiteralPath $p).Length -gt 8MB){throw 'B57_BOUND'};[Text.UTF8Encoding]::new($false,$true).GetString([IO.File]::ReadAllBytes($p))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $path)).Hash.ToLowerInvariant() -cne $hash){throw ('B57_HASH_'+$path)}}
$m=Read 'docs/catalogos/bloco57-local/manifesto.json'|ConvertFrom-Json -Depth 60 -DateKind String
if($m.version -ne 1 -or $m.block -ne 57 -or $m.status -cnotin @('EM_EXECUCAO','TESTADO_LOCAL_A_D_DEPENDENCIAS_EXTERNAS')){throw 'B57_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/esl-documentacao/manifesto.json' -or $m.predecessor.sha256 -cne '92da7d773d0a067b77a5b1549b715d12fc3ec66c434fcf6dce0f08594f87e645'){throw 'B57_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
$esl=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$raster=Read $esl.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$continuation=Read $raster.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$prep=Read $continuation.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $prep.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $prep.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $prep.newFiles){$baseline.Add($e.path,$e.sha256)}
Hash $continuation.predecessor.path $continuation.predecessor.sha256
$baseline.Add($continuation.predecessor.path,$continuation.predecessor.sha256)
foreach($phase in @(
 @{manifest=$continuation;reference=$raster.predecessor},
 @{manifest=$raster;reference=$esl.predecessor},
 @{manifest=$esl;reference=$m.predecessor})){
 Hash $phase.reference.path $phase.reference.sha256
 foreach($e in $phase.manifest.changedExistingFiles){if($baseline[$e.path] -cne $e.before){throw 'B57_PREDECESSOR_CHAIN'};$baseline[$e.path]=$e.after}
 foreach($e in $phase.manifest.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($phase.reference.path,$phase.reference.sha256)
}
if($baseline.Count -ne 1434){throw 'B57_BASELINE_COUNT'}
foreach($key in @('newAcceptances','sourceCalls','sqlOperations','runtimeCampaigns','budgetConsumed','unknownExternalEffects')){if($m.$key -ne 0){throw 'B57_LIMIT_OR_ACCEPTANCE'}}
$allowed=@(
 'STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'scripts/validation/Test-EslDocumentation.ps1',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportStrictJsonParser.java',
 'src/main/java/br/com/esl/etl/v2/modulos/cotacoes/aplicacao/CotacaoDataExportRecordMapper.java',
 'src/main/java/br/com/esl/etl/v2/modulos/fretes/aplicacao/FreteDataExportRecordMapper.java')
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($entry in $m.changedExistingFiles){
 if($entry.path -cnotin $allowed -or $entry.snapshot -cne ('docs/continuidade/historico/bloco57/'+$entry.path)){throw 'B57_UNAPPROVED_DELTA'}
 if(-not $baseline.ContainsKey($entry.path) -or $baseline[$entry.path] -cne $entry.before){throw 'B57_BASELINE_BEFORE'}
 Hash $entry.path $entry.after;Hash $entry.snapshot $entry.before;$map.Add($entry.path,$entry)
}
if($m.initialFiles -ne 1434 -or @($m.preservedFiles).Count+$map.Count -ne 1434){throw 'B57_INITIAL_COUNT'}
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B57_DUPLICATE_PATH'};if(-not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B57_PRESERVED_BASELINE'};Hash $e.path $e.sha256}
foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B57_NEW_PATH_COLLISION'};Hash $e.path $e.sha256}
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$previous=Read $map[$path].snapshot
 $pattern='(?m)^\s*- \[[ xX]\].+$'
 if((@([regex]::Matches($current,$pattern)|ForEach-Object Value)-join "`n") -cne (@([regex]::Matches($previous,$pattern)|ForEach-Object Value)-join "`n")){throw 'B57_ORIGINAL_CHECKBOX_CHANGED'}
 if(-not $current.Contains('B57_CARACTERIZACAO_LOCAL')){throw 'B57_POINTER'}
}
if(@((Read 'docs/continuidade/RETOMADA.md') -split "`n").Count -gt 120){throw 'B57_RETOMADA_BOUND'}
foreach($e in $m.bindings){Hash $e.path $e.sha256}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 foreach($e in (Read $m.initialInventory.path|ConvertFrom-Json -Depth 10)){Hash ('target/bloco57-local/initial/'+$e.path) $e.sha256}
 foreach($e in $m.evidence){Hash $e.path $e.sha256}
}
if($AsMap){return ,$map}
'B57_LOCAL_SUCCESSION_PASS_NO_NEW_ACCEPTANCE_NO_EXTERNAL_EFFECT'

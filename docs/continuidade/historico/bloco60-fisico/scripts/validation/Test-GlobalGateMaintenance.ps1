#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifestPath='docs/catalogos/manutencao-gates-pos-b60/manifesto.json'
$historyPrefix='docs/continuidade/historico/gates-pos-b60/'
$allowed=@(
 'STATES.md',
 'docs/continuidade/RETOMADA.md',
 'docs/runbooks/trilha-de-chats-gpt-5-6.md',
 'docs/catalogos/bootstrap-v2-047/manifesto.json',
 'docs/catalogos/bootstrap-v2-047/manifesto.sha256',
 'docs/catalogos/cutover/manifesto.json',
 'docs/catalogos/cutover/manifesto.sha256',
 'scripts/validation/Build-CutoverTopologyCatalog.ps1',
 'scripts/validation/Test-CutoverTopologyCatalog.ps1',
 'scripts/validation/Test-ProgressiveDataGate.ps1',
 'scripts/validation/Test-Bloco60Local.ps1',
 'src/test/java/br/com/esl/etl/v2/arquitetura/CutoverTopologyContractTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/controle/ProgressiveDataGateSqlContractTest.java',
 'database/proposals/bloco60-local/package.json'
)
$allowedNew=@($allowed|ForEach-Object {$historyPrefix+$_})+@(
 'scripts/validation/Test-GlobalGateMaintenance.ps1',
 'docs/catalogos/manutencao-gates-pos-b60/README.md',
 'docs/continuidade/checkpoints/0039-correcao-gates-globais-adocao.md',
 'docs/continuidade/checkpoints/0040-correcao-gates-globais-fechamento.md'
)
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'GATES_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$result=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'GATES_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 return $result
}
function Read([string]$Path){$file=Local $Path;if((Get-Item $file).Length -gt 8MB){throw 'GATES_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('GATES_HASH_'+$Path)}}
function Exact($Actual,$Expected,[string]$Reason){if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}}
function Check($m){
 if($m.version -ne 1 -or $m.status -cnotin @('EM_EXECUCAO','LOCAL_GATES_RECONCILED') -or $m.functionalBlockCreated -ne $false -or $m.newAcceptances -ne 0 -or $m.physicalSql -ne 0 -or $m.realSourceCalls -ne 0){throw 'GATES_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-local/manifesto.json' -or $m.predecessor.sha256 -cne $predecessorHash){throw 'GATES_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
 foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
 foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1725 -or $m.initialFiles -ne 1725){throw 'GATES_BASELINE_COUNT'}
 Exact $m.changedExistingFiles.path $allowed 'GATES_EXACT_DELTAS'
 $expectedNew=if($m.status -ceq 'EM_EXECUCAO'){@($allowedNew|Where-Object {$_ -cne 'docs/continuidade/checkpoints/0040-correcao-gates-globais-fechamento.md'})}else{$allowedNew}
 Exact $m.newFiles.path $expectedNew 'GATES_EXACT_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){
  if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($historyPrefix+$e.path)){throw 'GATES_BEFORE'}
  Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
 }
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1725){throw 'GATES_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){
  if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'GATES_PRESERVED_BASELINE'}
  Hash $e.path $e.sha256
 }
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'GATES_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'GATES_HISTORY'}
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'GATES_CHECKBOX'}
 }
 # The prepared physical package changes only the hash of the revised local validator.
 # Its budgets, SQL, requests, principals and approval state remain byte-equivalent as objects.
 $packagePath='database/proposals/bloco60-local/package.json'
 $package=Read $packagePath|ConvertFrom-Json -Depth 60 -DateKind String
 $previousPackage=Read $map[$packagePath].snapshot|ConvertFrom-Json -Depth 60 -DateKind String
 $binding=@($package.files|Where-Object path -CEQ 'scripts/validation/Test-Bloco60Local.ps1')
 $previousBinding=@($previousPackage.files|Where-Object path -CEQ 'scripts/validation/Test-Bloco60Local.ps1')
 if($binding.Count -ne 1 -or $previousBinding.Count -ne 1 -or $binding[0].sha256 -cne $map['scripts/validation/Test-Bloco60Local.ps1'].after -or $previousBinding[0].sha256 -cne $map['scripts/validation/Test-Bloco60Local.ps1'].before){throw 'GATES_PACKAGE_BINDING'}
 $binding[0].sha256=$previousBinding[0].sha256
 if(($package|ConvertTo-Json -Depth 60 -Compress) -cne ($previousPackage|ConvertTo-Json -Depth 60 -Compress)){throw 'GATES_PACKAGE_SCOPE'}
 if($IncludePrivateEvidence){
  if($m.historicalReceipt.path -cne 'target/bloco60-local/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '0b6714ea1fb7a02f6c41896c1c40275e6cedbefd8e719e95f7ee20b65f1b0e53'){throw 'GATES_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if(-not $receipt.passed -or $receipt.artifacts.Count -ne 2094){throw 'GATES_RECEIPT_COUNT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  Hash $m.initialInventory.path $m.initialInventory.sha256
  $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
  if($initial.Count -ne 1725){throw 'GATES_INITIAL_COUNT'}
  foreach($e in $initial){if(-not $baseline.ContainsKey($e.path) -or $e.sha256 -cne $baseline[$e.path]){throw 'GATES_INITIAL_BASELINE'};Hash ('target/correcao-gates-pos-b60/initial/'+$e.path) $e.sha256}
 }
 return ,$map
}
# Literal predecessor anchor, filled from the reviewed B60 manifest, never accepted from input.
$predecessorHash='3740f1ff4aed22f3dcc90098706a23e471520239bd70dcc9f1547d376729f65d'
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){
 foreach($tuple in @(
  @('GATES_SCOPE',{param($m)$m.newAcceptances=1}),
  @('GATES_SCOPE',{param($m)$m.physicalSql=1}),
  @('GATES_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
  @('GATES_EXACT_DELTAS',{param($m)$m.changedExistingFiles[0].path='docs/'}),
  @('GATES_EXACT_ADDITIONS',{param($m)$m.newFiles[0].path='src/'}),
  @('GATES_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
  @('GATES_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='STATES.md'}),
  @('GATES_PRESERVED_BASELINE',{param($m)$m.preservedFiles[0].sha256='0'*64})
 )){
  $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
  & $tuple[1] $copy;$reason='NOT_REJECTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
  if($reason -cne $tuple[0]){throw ('GATES_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;changed=$result.Count;added=$manifest.newFiles.Count;guards=$guards;private=[bool]$IncludePrivateEvidence;physicalSql=0}|ConvertTo-Json

#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco59-local/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-Bloco59Local.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$path){if($null -ne $successor -and $successor.ContainsKey($path)){return $successor[$path].snapshot};return $path}
function Local([string]$path){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'POST58_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $path);$file=$node.FullName
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'POST58_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 return $file
}
function Read([string]$path){$file=Local (Historical $path);if((Get-Item -LiteralPath $file).Length -gt 8MB){throw 'POST58_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $path))).Hash.ToLowerInvariant() -cne $hash){throw ('POST58_HASH_'+$path)}}
function Exact($actual,$expected,[string]$reason){if(@($actual).Count -ne @($expected).Count -or (@($actual|Sort-Object -Unique)-join '|') -cne (@($expected|Sort-Object -Unique)-join '|')){throw $reason}}
$m=Read 'docs/catalogos/pos-bloco58/manifesto.json'|ConvertFrom-Json -Depth 60 -DateKind String
if($m.version -ne 1 -or $m.phase -cne 'POST_B58_DOCUMENTATION' -or $m.status -cnotin @('EM_EXECUCAO','DOCUMENTATION_CLOSED')){throw 'POST58_STATUS'}
if($m.predecessor.path -cne 'docs/catalogos/bloco58-local/manifesto.json' -or $m.predecessor.sha256 -cne '30cffcb2e2056ca43506101fad17cfb13dc8e4cf7c6b1fcd9c692c5f4b548b31'){throw 'POST58_PREDECESSOR'}
Hash $m.predecessor.path $m.predecessor.sha256
$old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
$baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
if($baseline.Count -ne 1521 -or $m.initialFiles -ne 1521){throw 'POST58_BASELINE_COUNT'}
foreach($key in @('newAcceptances','sourceCalls','sqlOperations','runtimeCampaigns','budgetConsumed','unknownExternalEffects')){
 if(($m.$key -isnot [int] -and $m.$key -isnot [long]) -or $m.$key -ne 0){throw 'POST58_EFFECT_OR_ACCEPTANCE'}
}
foreach($key in @('startsBloco59','javaReexecuted','budgetRenewed')){if($m.$key -isnot [bool] -or $m.$key){throw 'POST58_SCOPE'}}
foreach($pair in @(@('total',115),@('done',67),@('pending',48),@('openRoutes',191),@('now',0))){if($m.progress.($pair[0]) -ne $pair[1]){throw 'POST58_PROGRESS'}}
$allowed=@('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','scripts/validation/Test-Bloco58Local.ps1')
Exact $m.changedExistingFiles.path $allowed 'POST58_EXACT_DELTAS'
$map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($e in $m.changedExistingFiles){
 if($e.snapshot -cne ('docs/continuidade/historico/pos-bloco58/'+$e.path) -or $e.before -cne $baseline[$e.path]){throw 'POST58_BASELINE_BEFORE'}
 Hash $e.path $e.after;Hash $e.snapshot $e.before;$map.Add($e.path,$e)
}
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
if(@($m.preservedFiles).Count+$map.Count -ne 1521){throw 'POST58_PRESERVED_COUNT'}
foreach($e in $m.preservedFiles){
 if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'POST58_PRESERVED_BASELINE'}
 Hash $e.path $e.sha256
}
$observed='docs/continuidade/historico/pos-bloco58/STATES.observado.md'
if($m.observedStates.path -cne $observed -or $m.observedStates.sha256 -cne 'ee0b5515654740ad49f58116c1dfee37f54039e31dcefe798de8b8df3f68e728'){throw 'POST58_OBSERVED_BINDING'}
Hash $m.observedStates.path $m.observedStates.sha256
$new=@($m.changedExistingFiles.snapshot)+@($observed,'docs/catalogos/pos-bloco58/README.md','scripts/validation/Test-PosBloco58Documentation.ps1','scripts/validation/Test-PosBloco58DocumentationGuards.ps1')
foreach($name in @('0027-pos-bloco58-sucessao-documental.md','0028-pos-bloco58-fechamento-documental.md')){
 $path='docs/continuidade/checkpoints/'+$name
 if($m.status -ceq 'DOCUMENTATION_CLOSED' -or (Test-Path -LiteralPath (Join-Path $root $path))){$new+=$path}
}
Exact $m.newFiles.path $new 'POST58_EXACT_ADDITIONS'
foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'POST58_NEW_COLLISION'};Hash $e.path $e.sha256}
$state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
 $current=Read $path;$previous=Read $map[$path].snapshot
 $pattern='(?m)^\s*- \[[ xX]\].+$'
 if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'POST58_CHECKBOX_CHANGED'}
 if(-not $current.Contains('POST_B58_DOCS_')){throw 'POST58_STATE_MARKER'}
}
if(-not $state.Contains((Read $observed)) -or -not $trail.Contains((Read $map['docs/runbooks/trilha-de-chats-gpt-5-6.md'].snapshot))){throw 'POST58_OBSERVED_TEXT_NOT_PRESERVED'}
if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'POST58_PROGRESS'}
if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'POST58_RETOMADA_BOUND'}
foreach($id in @('3bfe6412-b458-4add-b2f1-5121b9c15203','4714de68-e48a-470a-8143-22b8abf72c19','8d66ad04-ff69-4577-ba5e-c576822c5d17','d6ea8648-ce24-4c27-94d5-8a64a81fa904','920c7fd5-a2f1-4a8a-a9b4-52d35e940917')){if(-not $state.Contains($id)){throw 'POST58_ATTACHMENT_MISSING'}}
if($IncludePrivateEvidence){
 Hash $m.initialInventory.path $m.initialInventory.sha256
 $initial=Read $m.initialInventory.path|ConvertFrom-Json -Depth 10
 if($initial.Count -ne 1521){throw 'POST58_PRIVATE_COUNT'}
 $privateSeen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($e in $initial){
  $expected=if($e.path -ceq 'STATES.md'){$m.observedStates.sha256}else{$baseline[$e.path]}
  if(-not $privateSeen.Add($e.path) -or $e.sha256 -cne $expected){throw 'POST58_PRIVATE_BASELINE'}
  Hash ('target/pos-bloco58-docs/initial/'+$e.path) $e.sha256
 }
 foreach($e in $m.evidence){Hash $e.path $e.sha256}
 if($m.historicalReceipt.path -cne 'target/bloco58-local/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '31f3f258e738e34899357148fe2f0d63d56d347d29c790b7bbef7a2be731f087'){throw 'POST58_RECEIPT_BINDING'}
 Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
 $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 30
 if(-not $receipt.passed -or $receipt.artifacts.Count -ne 104){throw 'POST58_HISTORICAL_RECEIPT'}
 foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
 if($m.javaProof.path -cne 'target/bloco58-local/java-result.json' -or $m.javaProof.sha256 -cne 'ecf578952946e54bbab7eb4b2186afc440abb1ee83b7fee636181adb2ebf5eb2'){throw 'POST58_JAVA_BINDING'}
 Hash $m.javaProof.path $m.javaProof.sha256
 $j=Read $m.javaProof.path|ConvertFrom-Json -Depth 20
 if(-not $j.passed -or $j.tests -ne 1303 -or $j.failures -ne 0 -or $j.errors -ne 0 -or $j.skipped -ne 5){throw 'POST58_JAVA_PROOF'}
 foreach($e in @($j.sourceBindings)+@($j.artifacts)){Hash $e.path $e.sha256}
}
if($null -ne $successor){
 foreach($path in $successor.Keys){
  if($map.ContainsKey($path)){
   if($map[$path].after -cne $successor[$path].before){throw 'POST58_B59_CHAIN'}
   $map[$path].after=$successor[$path].after
  }else{$map.Add($path,$successor[$path])}
 }
}
if($AsMap){return ,$map}
'POST58_DOCUMENTATION_SUCCESSION_PASS_NO_NEW_ACCEPTANCE_NO_JAVA_RERUN'

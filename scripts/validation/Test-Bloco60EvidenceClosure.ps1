#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifestPath='docs/catalogos/bloco60-provas/manifesto.json'
$renewedSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco60-renovacao/manifesto.json')){
 $renewedSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco60RenewedClosure.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){if($null -ne $renewedSuccessor -and $renewedSuccessor.ContainsKey($Path)){return $renewedSuccessor[$Path].snapshot};return $Path}
$history='docs/continuidade/historico/bloco60-provas/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco60ResumedClosure.ps1')
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60EC_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60EC_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}};$file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local (Historical $Path)),[Text.UTF8Encoding]::new($false,$true))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B60EC_HASH_'+$Path)}}
function Exact($a,$b,[string]$reason){if(@($a).Count -ne @($b).Count -or ((@($a|Sort-Object -Unique))-join '|') -cne ((@($b|Sort-Object -Unique))-join '|')){throw $reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'EVIDENCE_RECHECKED_FINAL_WINDOW_PENDING' -or $m.matrixPassed -or $m.newAcceptances -ne 0 -or $m.physicalExecuted -or $m.approvalRecorded -or $m.windowRenewed){throw 'B60EC_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-retomada/manifesto.json' -or $m.predecessor.sha256 -cne 'bbd5c653bc3f53ec1459bae84f380b2609a8f73bedccd97bf3f0f572aa462a11'){throw 'B60EC_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.observed.casesPassed -ne 72 -or $m.observed.casesPending -ne 2 -or $m.observed.sqlcmd -ne 160 -or $m.observed.jvms -ne 77 -or $m.observed.http -ne 71){throw 'B60EC_COUNTS'}
 if($m.package.path -cne 'target/b60-provas-finais-20260910/package/package.json' -or $m.package.sha256 -cne '26ba79e319a5c652b968ed617fd890b760c3649f42cfcdf268edfef4bb0cba57'){throw 'B60EC_PACKAGE'}
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)};foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)};foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)};$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 2256 -or $m.initialFiles -ne 2256){throw 'B60EC_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60EC_DELTAS'
 $added=@($allowed|ForEach-Object {$history+$_})+@('scripts/validation/Test-Bloco60EvidenceClosure.ps1','docs/catalogos/bloco60-provas/README.md','docs/continuidade/checkpoints/0047-bloco60-busca-de-provas-e-pacote-final.md')
 Exact $m.newFiles.path $added 'B60EC_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60EC_BEFORE'};Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if($m.preservedFiles.Count+$map.Count -ne 2256){throw 'B60EC_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60EC_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60EC_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60EC_HISTORY'}
  $pattern='(?m)^\s*- \[[ xX]\].+$'
  Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B60EC_CHECKBOX'
 }
 if($IncludePrivateEvidence){
  Hash $m.package.path $m.package.sha256
  $package=Read $m.package.path|ConvertFrom-Json -Depth 50 -DateKind String
  if($package.approvalRecorded -or $package.physicalExecuted -or $package.cases -ne 3 -or $package.files.Count -ne 83){throw 'B60EC_PACKAGE_SCOPE'}
  foreach($e in $package.files){Hash $e.path $e.sha256}
  if($m.historicalReceipt.path -cne 'target/execucao-b60-retomada-20260910-0910/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '3105901c37258ef71eee28d48085888aafe129ef1f255af663c6e8d6add1e8b8'){throw 'B60EC_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if($receipt.artifacts.Count -ne 3388){throw 'B60EC_RECEIPT_COUNT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $m.evidence){Hash $e.path $e.sha256}
  $proof=Read 'target/b60-provas-finais-20260910/evidence-audit.json'|ConvertFrom-Json -Depth 50
  if(-not $proof.passed -or $proof.matrixPassed -or $proof.casesPassed -ne 72 -or $proof.caseAssertions -ne 73 -or $proof.chains.Count -ne 11 -or $proof.sqlExecuted -or $proof.windowRenewed){throw 'B60EC_AUDIT'}
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60EC_SCOPE',{param($m)$m.matrixPassed=$true}),@('B60EC_SCOPE',{param($m)$m.approvalRecorded=$true}),@('B60EC_SCOPE',{param($m)$m.physicalExecuted=$true}),@('B60EC_SCOPE',{param($m)$m.windowRenewed=$true}),
 @('B60EC_COUNTS',{param($m)$m.observed.casesPassed=74}),@('B60EC_PACKAGE',{param($m)$m.package.sha256='0'*64}),@('B60EC_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'})
 )){$copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60EC_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
if($null -ne $renewedSuccessor){
 foreach($path in $renewedSuccessor.Keys){
  if($result.ContainsKey($path)){
   if($result[$path].after -cne $renewedSuccessor[$path].before){throw 'B60EC_RENEWED_CHAIN'}
   $result[$path].after=$renewedSuccessor[$path].after
  }else{$result.Add($path,$renewedSuccessor[$path])}
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;matrixPassed=$false;newAcceptances=0;approvalRecorded=$false;sqlExecuted=$false}|ConvertTo-Json

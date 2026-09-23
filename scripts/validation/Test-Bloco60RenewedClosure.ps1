#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifestPath='docs/catalogos/bloco60-renovacao/manifesto.json'
$preparationSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco61-preparacao/manifesto.json')){
 $preparationSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco61Preparation.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){if($null -ne $preparationSuccessor -and $preparationSuccessor.ContainsKey($Path)){return $preparationSuccessor[$Path].snapshot};return $Path}
$history='docs/continuidade/historico/bloco60-renovacao/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco60EvidenceClosure.ps1')
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60NC_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60NC_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}};$file
}
function Read([string]$Path){[IO.File]::ReadAllText((Local (Historical $Path)),[Text.UTF8Encoding]::new($false,$true))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B60NC_HASH_'+$Path)}}
function Exact($a,$b,[string]$reason){if(@($a).Count -ne @($b).Count -or ((@($a|Sort-Object -Unique))-join '|') -cne ((@($b|Sort-Object -Unique))-join '|')){throw $reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'PHYSICAL_LOCAL_USERS_QUALIFIED' -or -not $m.matrixPassed -or $m.newAcceptances -ne 0 -or -not $m.physicalExecuted -or -not $m.approvalRecorded -or -not $m.windowRenewed){throw 'B60NC_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-provas/manifesto.json' -or $m.predecessor.sha256 -cne '80f754a3b220e54e3f3968d68d438ebaa3f6efa4e928eec05a5b37842a0c122f'){throw 'B60NC_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.observed.casesPassed -ne 74 -or $m.observed.casesPending -ne 0 -or $m.observed.sqlcmd -ne 204 -or $m.observed.jvms -ne 83 -or $m.observed.http -ne 75){throw 'B60NC_COUNTS'}
 if($m.package.path -cne 'target/b60-conclusao-20260910/package/package.json' -or $m.package.sha256 -cne 'b776e40f6f1bd206cf215050901497cbac66d87fc4b06594b576684f8b83b61b'){throw 'B60NC_PACKAGE'}
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)};foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)};foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)};$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 2264 -or $m.initialFiles -ne 2264){throw 'B60NC_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60NC_DELTAS'
 $added=@($allowed|ForEach-Object {$history+$_})+@('scripts/validation/Test-Bloco60RenewedClosure.ps1','docs/catalogos/bloco60-renovacao/README.md','docs/continuidade/checkpoints/0048-bloco60-renovacao-compensada-e-correcao.md','docs/continuidade/checkpoints/0049-bloco60-qualificacao-fisica-local-concluida.md','database/validation/058_exercise_bloco60_adversarial_isolated.sql','database/validation/059_observe_bloco60_concurrent_waiters.sql','database/validation/060_exercise_bloco60_adversarial_rollback.sql')
 Exact $m.newFiles.path $added 'B60NC_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60NC_BEFORE'};Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if($m.preservedFiles.Count+$map.Count -ne 2264){throw 'B60NC_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60NC_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60NC_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60NC_HISTORY'}
  $pattern='(?m)^\s*- \[[ xX]\].+$'
  Exact @([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) @([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value) 'B60NC_CHECKBOX'
 }
 if($IncludePrivateEvidence){
  Hash $m.package.path $m.package.sha256
  $package=Read $m.package.path|ConvertFrom-Json -Depth 50 -DateKind String
  if($package.approvalRecorded -or $package.physicalExecuted -or $package.cases -ne 3 -or $package.files.Count -ne 89){throw 'B60NC_PACKAGE_SCOPE'}
  foreach($e in $package.files){Hash $e.path $e.sha256}
  if($m.historicalReceipt.path -cne 'target/b60-provas-finais-20260910/final/receipt.json' -or $m.historicalReceipt.sha256 -cne 'ad82571a0f9850d69a387bf6db8b9e4251a6c62bbd7f3f189eec08d50ca5eee9'){throw 'B60NC_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if($receipt.artifacts.Count -ne 3538){throw 'B60NC_RECEIPT_COUNT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $m.evidence){Hash $e.path $e.sha256}
  $proof=Read 'target/b60-conclusao-20260910/qualification-verification.json'|ConvertFrom-Json -Depth 50
  if(-not $proof.passed -or -not $proof.matrixPassed -or $proof.casesPassed -ne 74 -or $proof.concurrencyAssertions -ne 2 -or $proof.chains.Count -ne 16 -or $proof.recoveredServiceVersion -ne 35 -or $proof.unknownEffects -ne 0){throw 'B60NC_AUDIT'}
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60NC_SCOPE',{param($m)$m.matrixPassed=$false}),@('B60NC_SCOPE',{param($m)$m.approvalRecorded=$false}),@('B60NC_SCOPE',{param($m)$m.physicalExecuted=$false}),@('B60NC_SCOPE',{param($m)$m.windowRenewed=$false}),
 @('B60NC_COUNTS',{param($m)$m.observed.casesPassed=72}),@('B60NC_PACKAGE',{param($m)$m.package.sha256='0'*64}),@('B60NC_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'})
 )){$copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60NC_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
if($null -ne $preparationSuccessor){
 foreach($path in $preparationSuccessor.Keys){
  if($result.ContainsKey($path)){
   if($result[$path].after -cne $preparationSuccessor[$path].before){throw 'B60NC_PREPARATION_CHAIN'}
   $result[$path].after=$preparationSuccessor[$path].after
  }else{$result.Add($path,$preparationSuccessor[$path])}
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;matrixPassed=$true;newAcceptances=0;approvalRecorded=$true;sqlExecuted=$true}|ConvertTo-Json

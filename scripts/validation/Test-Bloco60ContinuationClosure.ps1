#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$resumedSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco60-retomada/manifesto.json')){
 $resumedSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco60ResumedClosure.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){if($null -ne $resumedSuccessor -and $resumedSuccessor.ContainsKey($Path)){return $resumedSuccessor[$Path].snapshot};return $Path}
$history='docs/continuidade/historico/bloco60-continuacao/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco60CorrectiveClosure.ps1')
$manifestPath='docs/catalogos/bloco60-continuacao/manifesto.json'
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60NC_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60NC_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 $file
}
function Read([string]$Path){$file=Local (Historical $Path);if((Get-Item $file).Length -gt 8MB){throw 'B60NC_BOUND'};[IO.File]::ReadAllText($file,[Text.UTF8Encoding]::new($false,$true))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B60NC_HASH_'+$Path)}}
function Exact($a,$b,[string]$Reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $Reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cnotin @('QUALIFICACAO_FISICA_LOCAL_USUARIOS','WINDOW_EXPIRED_MATRIX_INCOMPLETE') -or $m.approvalRecorded -ne $true -or $m.budgetRenewed -ne $false -or $m.newAcceptances -ne 0 -or $m.matrixPassed -ne ($m.status -ceq 'QUALIFICACAO_FISICA_LOCAL_USUARIOS')){throw 'B60NC_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-correcao/manifesto.json' -or $m.predecessor.sha256 -cne '7ebc73c71d8f3e930af0eed70c66006457c6e6de7cdd64c13cd7d8f1ccc9f30f'){throw 'B60NC_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.package.path -cne 'database/proposals/bloco60-restante-r2/package.json' -or $m.package.sha256 -cne '6172093677610a4f9415468f7c18f9531c6826a702c7e2a9a9c1cbf61021dca7'){throw 'B60NC_PACKAGE'}
 Hash $m.package.path $m.package.sha256
 if($m.observed.sqlcmd -lt 54 -or $m.observed.sqlcmd -gt 240 -or $m.observed.jvms -lt 33 -or $m.observed.jvms -gt 80 -or $m.observed.http -lt 34 -or $m.observed.http -gt 400 -or $m.observed.unresolved -ne 0 -or $m.observed.serviceVersion -notin @(21,23) -or $m.observed.deadlineUtc -cne '2026-09-10T04:40:10.0501172Z'){throw 'B60NC_OBSERVATIONS'}
 if(($m.matrixPassed -and ($m.observed.casesPassed -ne 74 -or $m.observed.serviceVersion -ne 23)) -or (-not $m.matrixPassed -and ($m.observed.casesPassed -lt 32 -or $m.observed.casesPassed -ge 74))){throw 'B60NC_OBSERVATIONS'}
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
 foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
 foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1865 -or $m.initialFiles -ne 1865){throw 'B60NC_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60NC_DELTAS'
 $package=Read $m.package.path|ConvertFrom-Json -Depth 30 -DateKind String
 $added=@($package.files|Where-Object {-not $_.path.StartsWith('target/') -and -not $baseline.ContainsKey($_.path)}|ForEach-Object path)
 $added+=@($allowed|ForEach-Object {$history+$_})+@($m.package.path,'scripts/validation/Test-Bloco60ContinuationClosure.ps1','docs/catalogos/bloco60-continuacao/README.md','docs/continuidade/checkpoints/0043-bloco60-retomada-e-correcao-sql.md','docs/continuidade/checkpoints/0044-bloco60-consolidacao-cumulativa.md')
 Exact $m.newFiles.path $added 'B60NC_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60NC_BEFORE'};Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1865){throw 'B60NC_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60NC_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60NC_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60NC_HISTORY'}
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B60NC_CHECKBOX'}
 }
 if($IncludePrivateEvidence){
  if($m.historicalReceipt.path -cne 'target/b60-correcao-20260910/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '340d9d5d7d1cdd773ec5fa48180d8a1eacb05e86e69e527f554473afc4e1d4d9'){throw 'B60NC_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if(-not $receipt.passed -or $receipt.artifacts.Count -ne 2421){throw 'B60NC_PRIOR_RECEIPT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $package.files){Hash $e.path $e.sha256}
  foreach($e in $m.evidence){if(-not ($e.path.StartsWith('target/execucao-b60-corretiva-20260910-0040/',[StringComparison]::Ordinal) -or $e.path.StartsWith('target/bloco60-local/physical-',[StringComparison]::Ordinal))){throw 'B60NC_EVIDENCE_PATH'};Hash $e.path $e.sha256}
  $proof=Read 'target/execucao-b60-corretiva-20260910-0040/first-attempt-verification.json'|ConvertFrom-Json
  if(-not $proof.passed -or -not $proof.historyMultisetPreserved -or $proof.administrativeCommits -ne 0 -or $proof.unresolved -ne 0 -or $proof.resolvedUnknown -ne 1){throw 'B60NC_RECOVERY_PROOF'}
  $state=Read 'target/bloco60-local/physical-corrective/cc4f84cb37f69724/COMPENSATION_STATE.sql.log'|ConvertFrom-Json
  if($state.status -cne 'UNACTIVATED' -or $state.serviceVersion -ne 19 -or $state.activeUsersScopes -ne 0 -or $state.activePolicies -ne 0 -or $state.usersGrants -ne 0){throw 'B60NC_SQL_STATE'}
  $proof=Read 'target/execucao-b60-corretiva-20260910-0040/physical-verification.json'|ConvertFrom-Json -Depth 40 -DateKind String
  if(-not $proof.passed -or $proof.qualification -cne $m.status -or $proof.casesPassed -ne $m.observed.casesPassed -or $proof.sqlcmd -ne $m.observed.sqlcmd -or $proof.jvms -ne $m.observed.jvms -or $proof.cumulativeHttp -ne $m.observed.http -or $proof.serviceVersionAfter -ne $m.observed.serviceVersion -or $proof.unresolved -ne 0 -or $proof.renewals -ne 0 -or -not $proof.historyMultisetPreserved){throw 'B60NC_PHYSICAL_PROOF'}
  if($m.matrixPassed -and ($proof.casesPassed -ne 74 -or $proof.casesPending -ne 0 -or -not $proof.cancellationRecheckPassed)){throw 'B60NC_MATRIX_INCOMPLETE'}
  if(-not $m.matrixPassed -and ($proof.casesPassed -ge 74 -or $proof.casesPending -le 0)){throw 'B60NC_INCOMPLETE_COUNT'}
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60NC_SCOPE',{param($m)$m.matrixPassed=-not $m.matrixPassed}),
 @('B60NC_SCOPE',{param($m)$m.approvalRecorded=$false}),
 @('B60NC_SCOPE',{param($m)$m.budgetRenewed=$true}),
 @('B60NC_SCOPE',{param($m)$m.newAcceptances=1}),
 @('B60NC_PACKAGE',{param($m)$m.package.sha256='0'*64}),
 @('B60NC_OBSERVATIONS',{param($m)$m.observed.sqlcmd=0}),
 @('B60NC_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'})
 )){$copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60NC_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
if($null -ne $resumedSuccessor){
 foreach($path in $resumedSuccessor.Keys){
  if($result.ContainsKey($path)){
   if($result[$path].after -cne $resumedSuccessor[$path].before){throw 'B60NC_RESUMED_CHAIN'}
   $result[$path].after=$resumedSuccessor[$path].after
  }else{$result.Add($path,$resumedSuccessor[$path])}
 }
}
if($AsMap){return ,$result}
 @{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;matrixPassed=$manifest.matrixPassed;newAcceptances=0;approvalRecorded=$true;sqlExecutedByValidator=$false}|ConvertTo-Json

#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$history='docs/continuidade/historico/bloco60-retomada/'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco60ContinuationClosure.ps1')
$manifestPath='docs/catalogos/bloco60-retomada/manifesto.json'
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60UC_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60UC_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}};$file
}
function Read([string]$Path){$file=Local $Path;if((Get-Item $file).Length -gt 8MB){throw 'B60UC_BOUND'};[IO.File]::ReadAllText($file,[Text.UTF8Encoding]::new($false,$true))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('B60UC_HASH_'+$Path)}}
function Exact($a,$b,[string]$Reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $Reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'WINDOW_EXPIRED_CONCURRENCY_UNPROVED' -or -not $m.approvalRecorded -or $m.budgetRenewed -or $m.newAcceptances -ne 0 -or $m.matrixPassed){throw 'B60UC_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-continuacao/manifesto.json' -or $m.predecessor.sha256 -cne '9c992ec5dd5fdd07802393bb7f3fb12af8c9474b74a79f63b536d35967e67758'){throw 'B60UC_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.package.path -cne 'database/proposals/bloco60-lease-restante/package.json' -or $m.package.sha256 -cne 'f08f6243c3904f8e6daa6a2ddf8f4fde24375789c18868ecd73d7ff450708942'){throw 'B60UC_PACKAGE'}
 Hash $m.package.path $m.package.sha256
 if($m.observed.sqlcmd -lt 101 -or $m.observed.sqlcmd -gt 240 -or $m.observed.jvms -lt 77 -or $m.observed.jvms -gt 80 -or $m.observed.http -lt 38 -or $m.observed.http -gt 400 -or $m.observed.unresolved -ne 0 -or $m.observed.serviceVersion -ne 31 -or $m.observed.casesPassed -ne 72 -or $m.observed.casesPending -ne 2 -or $m.observed.deadlineUtc -cne '2026-09-10T13:15:21.8816492+00:00' -or $m.observed.automaticWindowRenewals -ne 0){throw 'B60UC_OBSERVATIONS'}
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)};foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)};foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)};$baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1956 -or $m.initialFiles -ne 1956){throw 'B60UC_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60UC_DELTAS'
 $package=Read $m.package.path|ConvertFrom-Json -Depth 40 -DateKind String
 $added=@($package.files|Where-Object {-not $_.path.StartsWith('target/') -and -not $baseline.ContainsKey($_.path)}|ForEach-Object path)
 $added+=@($allowed|ForEach-Object {$history+$_})+@($m.package.path,'scripts/validation/Test-Bloco60ResumedClosure.ps1','docs/catalogos/bloco60-retomada/README.md','docs/continuidade/checkpoints/0046-bloco60-recuperacao-e-concorrencia-pendente.md','docs/continuidade/checkpoints/0045-bloco60-retomada-dos-casos-restantes.md')
 $added+=@('scripts/validation/Invoke-Bloco60PairReconcile.ps1','scripts/validation/Bloco60PairRecoveryLedger.psm1','database/proposals/bloco60-pair-reconcile/pair.sql','database/proposals/bloco60-pair-reconcile/package.json','scripts/validation/Bloco60ConcurrentPair.psm1','scripts/validation/Test-Bloco60ConcurrentPair.ps1','scripts/validation/Invoke-Bloco60ConcurrencyCorrection.ps1','database/proposals/bloco60-concurrency-correction/barrier-ready.sql','database/proposals/bloco60-concurrency-correction/README.md','database/proposals/bloco60-concurrency-correction/package.json')
 Exact $m.newFiles.path $added 'B60UC_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60UC_BEFORE'};Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1956){throw 'B60UC_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60UC_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60UC_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60UC_HISTORY'}
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B60UC_CHECKBOX'}
 }
 if($IncludePrivateEvidence){
  if($m.historicalReceipt.path -cne 'target/execucao-b60-corretiva-20260910-0040/final/receipt.json' -or $m.historicalReceipt.sha256 -cne 'db22307b37ecc241b09ec188c6e31a192b52037e694583d76a71e524815e4dd1'){throw 'B60UC_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if(-not $receipt.passed -or $receipt.artifacts.Count -ne 2704){throw 'B60UC_PRIOR_RECEIPT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $package.files){Hash $e.path $e.sha256}
  foreach($e in $m.evidence){if(-not ($e.path.StartsWith('target/execucao-b60-retomada-20260910-0910/',[StringComparison]::Ordinal) -or $e.path.StartsWith('target/bloco60-local/physical-',[StringComparison]::Ordinal))){throw 'B60UC_EVIDENCE_PATH'};Hash $e.path $e.sha256}
  $proof=Read 'target/execucao-b60-retomada-20260910-0910/physical-verification.json'|ConvertFrom-Json -Depth 50 -DateKind String
  if(-not $proof.passed -or $proof.qualification -cne $m.status -or $proof.casesPassed -ne 72 -or $proof.casesPending -ne 2 -or -not $proof.cancellationRecheckPassed -or $proof.sqlcmd -ne $m.observed.sqlcmd -or $proof.jvms -ne $m.observed.jvms -or $proof.cumulativeHttp -ne $m.observed.http -or $proof.serviceVersionAfter -ne 31 -or $proof.usersScopeVersionAfter -ne 14 -or $proof.unresolved -ne 0 -or $proof.budgetRenewals -ne 0 -or $proof.automaticWindowRenewals -ne 0 -or -not $proof.historyPreserved -or -not $proof.allOtherHistoricalRowsMultisetPreserved -or $proof.exactOwnMutablePartitionReconciled.retainedCancelledAttempts -ne 2){throw 'B60UC_PHYSICAL_PROOF'}
  if($proof.matrixPassed -or $proof.recoveryEscrowUsed -ne 2 -or $proof.totals.lastPair.publications -ne 1 -or $proof.totals.lastPair.consumptions -ne 2 -or $proof.totals.lastPair.barrierTransactions -ne 0){throw 'B60UC_PAIR_RECONCILIATION'}
  if($proof.chains.Count -ne 11 -or $proof.cases.Count -ne 73 -or @($proof.cases|Where-Object {-not $_.passed}).Count -or $proof.restrictedSessions -ne 0 -or $proof.aliveOwnProcesses -ne 0 -or $proof.listeners62160 -ne 0){throw 'B60UC_MATRIX_RECOVERY'}
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60UC_SCOPE',{param($m)$m.matrixPassed=$true}),@('B60UC_SCOPE',{param($m)$m.approvalRecorded=$false}),@('B60UC_SCOPE',{param($m)$m.budgetRenewed=$true}),@('B60UC_SCOPE',{param($m)$m.newAcceptances=1}),
 @('B60UC_PACKAGE',{param($m)$m.package.sha256='0'*64}),@('B60UC_OBSERVATIONS',{param($m)$m.observed.casesPassed=74}),@('B60UC_OBSERVATIONS',{param($m)$m.observed.jvms=81}),@('B60UC_OBSERVATIONS',{param($m)$m.observed.automaticWindowRenewals=1}),
 @('B60UC_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'})
 )){$copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60UC_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;matrixPassed=$false;newAcceptances=0;approvalRecorded=$true;sqlExecutedByValidator=$false}|ConvertTo-Json





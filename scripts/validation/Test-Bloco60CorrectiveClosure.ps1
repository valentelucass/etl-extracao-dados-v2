#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$continuationSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco60-continuacao/manifesto.json')){
 $continuationSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco60ContinuationClosure.ps1') -AsMap -IncludePrivateEvidence:$IncludePrivateEvidence
}
function Historical([string]$Path){if($null -ne $continuationSuccessor -and $continuationSuccessor.ContainsKey($Path)){return $continuationSuccessor[$Path].snapshot};return $Path}
$history='docs/continuidade/historico/bloco60-correcao/'
$manifestPath='docs/catalogos/bloco60-correcao/manifesto.json'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-Bloco60PhysicalClosure.ps1','src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactVerifier.java','src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactManifestTest.java')
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60RC_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60RC_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 $file
}
function Read([string]$Path){$file=Local (Historical $Path);if((Get-Item $file).Length -gt 8MB){throw 'B60RC_BOUND'};[IO.File]::ReadAllText($file,[Text.UTF8Encoding]::new($false,$true))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local (Historical $Path))).Hash.ToLowerInvariant() -cne $Expected){throw ('B60RC_HASH_'+$Path)}}
function Exact($a,$b,[string]$Reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $Reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'OFFLINE_CORRECTED_PHYSICAL_APPROVAL_PENDING' -or $m.newAcceptances -ne 0 -or $m.newPhysicalCampaignExecuted -ne $false -or $m.budgetRenewed -ne $false -or $m.approvalRecorded -ne $false){throw 'B60RC_SCOPE'}
 if($m.predecessor.path -cne 'docs/catalogos/bloco60-fisico/manifesto.json' -or $m.predecessor.sha256 -cne '4105c412c030b9c690b283f554f7f425967a226139b73b2117218829c835b918'){throw 'B60RC_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.package.path -cne 'database/proposals/bloco60-correcao/package.json' -or $m.package.sha256 -cne 'cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167'){throw 'B60RC_PACKAGE'}
 Hash $m.package.path $m.package.sha256
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
 foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
 foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1752 -or $m.initialFiles -ne 1752){throw 'B60RC_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60RC_DELTAS'
 $package=Read $m.package.path|ConvertFrom-Json -Depth 30 -DateKind String
 $added=@($package.files|Where-Object {-not $_.path.StartsWith('target/') -and -not $baseline.ContainsKey($_.path)}|ForEach-Object path)
 $added+=@($allowed|ForEach-Object {$history+$_})+@($m.package.path,'scripts/validation/Test-Bloco60CorrectiveClosure.ps1','docs/catalogos/bloco60-correcao/README.md','docs/continuidade/checkpoints/0042-bloco60-correcao-offline-pacote-corretivo.md')
 Exact $m.newFiles.path $added 'B60RC_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60RC_BEFORE'};Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1752){throw 'B60RC_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60RC_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60RC_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60RC_HISTORY'}
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B60RC_CHECKBOX'}
 }
 $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
 if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'B60RC_PROGRESS'}
 if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'B60RC_RETOMADA_BOUND'}
 if($IncludePrivateEvidence){
  if($m.historicalReceipt.path -cne 'target/execucao-b60-aprovada-20260909-2334/final/receipt.json' -or $m.historicalReceipt.sha256 -cne 'f2b35d102e8517b4fda6be67d2d84b8b1945077670470f77ef1d8f31307b605b'){throw 'B60RC_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if(-not $receipt.passed -or $receipt.campaignPassed -ne $false -or $receipt.artifacts.Count -ne 3600){throw 'B60RC_PRIOR_PHYSICAL'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $m.evidence){if(-not $e.path.StartsWith('target/b60-correcao-20260910/',[StringComparison]::Ordinal)){throw 'B60RC_EVIDENCE_PATH'};Hash $e.path $e.sha256}
  $build=Read 'target/b60-correcao-20260910/verify-01/exit.json'|ConvertFrom-Json
  if($build.exit -ne 0 -or -not $build.offline -or $build.physicalSql){throw 'B60RC_BUILD'}
  if((Read 'target/b60-correcao-20260910/semantic-red/result.log').Trim() -cne 'ADMINISTERED_MANIFEST_ENTRY_INVALID'){throw 'B60RC_REPRODUCTION'}
  $null=& (Join-Path $PSScriptRoot 'Test-Bloco60CorrectivePackage.ps1')
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60RC_SCOPE',{param($m)$m.newPhysicalCampaignExecuted=$true}),
 @('B60RC_SCOPE',{param($m)$m.newAcceptances=1}),
 @('B60RC_SCOPE',{param($m)$m.approvalRecorded=$true}),
 @('B60RC_PACKAGE',{param($m)$m.package.sha256='0'*64}),
 @('B60RC_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unreviewed.java'}),
 @('B60RC_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='STATES.md'})
 )){$copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60RC_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
if($null -ne $continuationSuccessor){
 foreach($path in $continuationSuccessor.Keys){
  if($result.ContainsKey($path)){
   if($result[$path].after -cne $continuationSuccessor[$path].before){throw 'B60RC_CONTINUATION_CHAIN'}
   $result[$path].after=$continuationSuccessor[$path].after
  }else{$result.Add($path,$continuationSuccessor[$path])}
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;guards=$guards;private=[bool]$IncludePrivateEvidence;newPhysicalCampaignExecuted=$false;newAcceptances=0}|ConvertTo-Json

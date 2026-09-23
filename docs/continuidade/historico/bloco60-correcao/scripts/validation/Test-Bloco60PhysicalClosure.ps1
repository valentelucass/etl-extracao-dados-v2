#Requires -Version 7.5
param([switch]$AsMap,[switch]$IncludePrivateEvidence,[switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifestPath='docs/catalogos/bloco60-fisico/manifesto.json'
$history='docs/continuidade/historico/bloco60-fisico/'
$physical='target/bloco60-local/physical/a3d28adeb17775bc/'
$evidence='target/execucao-b60-aprovada-20260909-2334/'
$packageHash='a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e'
$allowed=@('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md','docs/continuidade/RETOMADA.md','scripts/validation/Test-GlobalGateMaintenance.ps1')
$added=@($allowed|ForEach-Object {$history+$_})+@('docs/catalogos/bloco60-fisico/README.md','docs/continuidade/checkpoints/0041-bloco60-campanha-interrompida-compensada.md','scripts/validation/Test-Bloco60PhysicalClosure.ps1')
function Local([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'B60C_PATH'}
 $node=Get-Item -LiteralPath (Join-Path $root $Path);$file=$node.FullName
 while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B60C_REPARSE'};$node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}}
 return $file
}
function Read([string]$Path){$file=Local $Path;if((Get-Item $file).Length -gt 8MB){throw 'B60C_BOUND'};$utf8.GetString([IO.File]::ReadAllBytes($file))}
function Hash([string]$Path,[string]$Expected){if($Expected -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (Local $Path)).Hash.ToLowerInvariant() -cne $Expected){throw ('B60C_HASH_'+$Path)}}
function Exact($Actual,$Expected,[string]$Reason){if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}}
function Check($m){
 if($m.version -ne 1 -or $m.block -ne 60 -or $m.status -cne 'PHYSICAL_CAMPAIGN_STOPPED_COMPENSATED' -or $m.newAcceptances -ne 0 -or $m.campaignPassed -ne $false -or $m.sourceRealExecuted -ne $false -or $m.approvalRecorded -ne $true -or $m.budgetRenewed -ne $false){throw 'B60C_SCOPE'}
 if($m.package.path -cne 'database/proposals/bloco60-local/package.json' -or $m.package.sha256 -cne $packageHash){throw 'B60C_APPROVED_PACKAGE'}
 Hash $m.package.path $packageHash
 if($m.predecessor.path -cne 'docs/catalogos/manutencao-gates-pos-b60/manifesto.json' -or $m.predecessor.sha256 -cne 'dd5c5da628024155b1677a150a57859ee71d0e2ed0154a5669770aadda9c8990'){throw 'B60C_PREDECESSOR'}
 Hash $m.predecessor.path $m.predecessor.sha256
 if($m.observed.sqlcmd -ne 17 -or $m.observed.jvms -ne 1 -or $m.observed.reservations -ne 19 -or $m.observed.casesExecuted -ne 1 -or $m.observed.casesPassed -ne 0 -or $m.observed.casesNotExecuted -ne 73 -or $m.observed.sourceRequests -ne 0 -or $m.observed.unresolved -ne 0 -or $m.observed.compensationConfirmed -ne $true -or $m.observed.installedV024 -ne $true){throw 'B60C_OBSERVATIONS'}
 $old=Read $m.predecessor.path|ConvertFrom-Json -Depth 60 -DateKind String
 $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
 foreach($e in $old.preservedFiles){$baseline.Add($e.path,$e.sha256)}
 foreach($e in $old.changedExistingFiles){$baseline.Add($e.path,$e.after)}
 foreach($e in $old.newFiles){$baseline.Add($e.path,$e.sha256)}
 $baseline.Add($m.predecessor.path,$m.predecessor.sha256)
 if($baseline.Count -ne 1744 -or $m.initialFiles -ne 1744){throw 'B60C_BASELINE'}
 Exact $m.changedExistingFiles.path $allowed 'B60C_EXACT_DELTAS'
 Exact $m.newFiles.path $added 'B60C_EXACT_ADDITIONS'
 $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
 foreach($e in $m.changedExistingFiles){
  if(-not $baseline.ContainsKey($e.path) -or $e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'B60C_BEFORE'}
  Hash $e.snapshot $e.before;Hash $e.path $e.after;$map.Add($e.path,$e)
 }
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 if(@($m.preservedFiles).Count+$map.Count -ne 1744){throw 'B60C_PRESERVED_COUNT'}
 foreach($e in $m.preservedFiles){if($map.ContainsKey($e.path) -or -not $seen.Add($e.path) -or -not $baseline.ContainsKey($e.path) -or $baseline[$e.path] -cne $e.sha256){throw 'B60C_PRESERVED'};Hash $e.path $e.sha256}
 foreach($e in $m.newFiles){if($baseline.ContainsKey($e.path) -or -not $seen.Add($e.path)){throw 'B60C_NEW_COLLISION'};Hash $e.path $e.sha256}
 foreach($path in @('STATES.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
  $current=Read $path;$previous=Read $map[$path].snapshot;$pattern='(?m)^\s*- \[[ xX]\].+$'
  if(-not $current.EndsWith($previous,[StringComparison]::Ordinal)){throw 'B60C_HISTORY'}
  if((@([regex]::Matches($current.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10) -cne (@([regex]::Matches($previous.Replace("`r`n","`n"),$pattern)|ForEach-Object Value)-join [char]10)){throw 'B60C_CHECKBOX'}
 }
 $state=Read 'STATES.md';$trail=Read 'docs/runbooks/trilha-de-chats-gpt-5-6.md'
 if([regex]::Matches($state,'(?m)^\s*- \[[ xX]\]').Count -ne 115 -or [regex]::Matches($state,'(?m)^\s*- \[[xX]\]').Count -ne 67 -or [regex]::Matches($trail,'(?m)^- \[ \] STATUS=').Count -ne 191 -or $trail -match '(?m)^- \[ \] STATUS=AGORA'){throw 'B60C_PROGRESS'}
 if(@((Read 'docs/continuidade/RETOMADA.md') -split [char]10).Count -gt 120){throw 'B60C_RETOMADA_BOUND'}
 if($IncludePrivateEvidence){
  if($m.historicalReceipt.path -cne 'target/correcao-gates-pos-b60/final/receipt.json' -or $m.historicalReceipt.sha256 -cne '38dbd9eca8c798d3d96ce3eef6dd7360114f0cc02b9748b5c1e430fa7da3268b'){throw 'B60C_RECEIPT'}
  Hash $m.historicalReceipt.path $m.historicalReceipt.sha256
  $receipt=Read $m.historicalReceipt.path|ConvertFrom-Json -Depth 60 -DateKind String
  if(-not $receipt.passed -or $receipt.artifacts.Count -ne 2020){throw 'B60C_RECEIPT_COUNT'}
  foreach($e in $receipt.artifacts){$path=if($map.ContainsKey($e.path)){$map[$e.path].snapshot}else{$e.path};Hash $path $e.sha256}
  foreach($e in $m.evidence){if(-not ($e.path.StartsWith($physical,[StringComparison]::Ordinal) -or $e.path.StartsWith($evidence,[StringComparison]::Ordinal))){throw 'B60C_EVIDENCE_PATH'};Hash $e.path $e.sha256}
  foreach($path in @('ledger.jsonl','results.json','controller-result.json','PROFILE_RESTORED.sql.log','USUARIOS_RUN.readback.json')){if(@($m.evidence|Where-Object path -CEQ ($physical+$path)).Count -ne 1){throw 'B60C_REQUIRED_EVIDENCE'}}
  $events=@((Read ($physical+'ledger.jsonl')) -split "`n"|Where-Object {$_}|ForEach-Object {ConvertFrom-Json $_ -AsHashtable -DateKind String})
  $previous='0'*64;$ordinal=0
  foreach($event in $events){
   if($event.ordinal -ne ++$ordinal -or $event.previous -cne $previous){throw 'B60C_LEDGER_CHAIN'}
   $hash=$event.hash;$event.Remove('hash');$actual=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($event|ConvertTo-Json -Depth 10 -Compress)))).ToLowerInvariant()
   if($hash -cne $actual){throw 'B60C_LEDGER_HASH'};$previous=$hash
   if($event.type -cin @('OBSERVE','RECONCILE') -and @($m.evidence|Where-Object sha256 -CEQ $event.evidence).Count -lt 1){throw 'B60C_OBSERVATION_EVIDENCE'}
  }
  $reserves=@($events|Where-Object type -CEQ 'RESERVE')
  $pending=@($reserves|Where-Object {$id=$_.id;-not @($events|Where-Object {$_.type -cin @('OBSERVE','RECONCILE') -and $_.id -ceq $id -and $_.outcome -cne 'UNKNOWN'}).Count})
  if($events.Count -ne 41 -or $reserves.Count -ne 19 -or $pending.Count -ne 0 -or @($events|Where-Object type -CEQ 'OPEN').Count -ne 1 -or @($events|Where-Object type -CEQ 'CLOSE').Count -ne 1 -or $events[0].package -cne $packageHash -or $events[-1].passed -ne $false -or $events[-1].qualification -cne 'NOT_QUALIFIED'){throw 'B60C_LEDGER_RESULT'}
  $controller=Read ($physical+'controller-result.json')|ConvertFrom-Json
  if($controller.passed -ne $false -or $controller.package -cne $packageHash -or $controller.refunded -ne 0){throw 'B60C_CONTROLLER_RESULT'}
  $rows=@(Read ($physical+'results.json')|ConvertFrom-Json)
  foreach($id in @('MASTER_TARGET','PREFLIGHT','QUALIFY_UPGRADE','QUALIFY_BASELINE_SUFFIX','INSTALL_V024','PRESERVATION_AFTER_INSTALL','ACTIVATE_B60','PROFILE_ACTIVE','USUARIOS_RUN_READBACK','COMPENSATION_STATE','RESTORE_B60','PROFILE_RESTORED')){if(@($rows|Where-Object {$_.id -ceq $id -and $_.passed -eq $true -and $_.exit -eq 0}).Count -ne 1){throw 'B60C_SQL_RESULT'}}
  $case=@($rows|Where-Object id -CEQ 'USUARIOS_RUN');$http=@($rows|Where-Object id -CEQ 'HTTP_TOTAL')
  if($case.Count -ne 1 -or $case[0].exit -ne 20 -or $case[0].passed -ne $false -or $http.Count -ne 1 -or $http[0].requests -ne 0){throw 'B60C_RUNTIME_RESULT'}
  $readback=Read ($physical+'USUARIOS_RUN.readback.json')|ConvertFrom-Json
  foreach($key in @('attempts','decisions','consumptions','publications','seals','pages','stageRows','applicationRows','typedHistory')){if($readback.$key -ne 0){throw 'B60C_UNEXPECTED_RUNTIME_EFFECT'}}
  if((Read ($physical+'PROFILE_RESTORED.sql.log')).Trim() -cne 'B60_PROFILE_AFTER_PASS'){throw 'B60C_RECOVERY_PROFILE'}
 }
 return ,$map
}
$manifest=Read $manifestPath|ConvertFrom-Json -Depth 60 -DateKind String
$result=Check $manifest;$guards=0
if($SelfTest){
 foreach($tuple in @(
  @('B60C_SCOPE',{param($m)$m.campaignPassed=$true}),
  @('B60C_SCOPE',{param($m)$m.newAcceptances=1}),
  @('B60C_SCOPE',{param($m)$m.budgetRenewed=$true}),
  @('B60C_APPROVED_PACKAGE',{param($m)$m.package.sha256='0'*64}),
  @('B60C_OBSERVATIONS',{param($m)$m.observed.unresolved=1}),
  @('B60C_EXACT_DELTAS',{param($m)$m.changedExistingFiles[0].path='src/unapproved.java'}),
  @('B60C_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='STATES.md'})
 )){
  $copy=$manifest|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
  & $tuple[1] $copy;$reason='NOT_REJECTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
  if($reason -cne $tuple[0]){throw ('B60C_GUARD_'+$tuple[0]+'_GOT_'+$reason)};$guards++
 }
}
if($AsMap){return ,$result}
@{passed=$true;status=$manifest.status;private=[bool]$IncludePrivateEvidence;guards=$guards;campaignPassed=$false;compensationConfirmed=$true;newAcceptances=0;sqlExecutedByValidator=$false}|ConvertTo-Json

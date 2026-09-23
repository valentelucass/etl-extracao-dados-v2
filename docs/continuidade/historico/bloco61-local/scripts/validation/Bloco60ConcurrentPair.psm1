#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
# Readiness is a SQL observation of the owned barrier, independent of process output buffering.
function Invoke-Bloco60ConcurrentPair {
 param([Parameter(Mandatory)][scriptblock]$StartBarrier,
 [Parameter(Mandatory)][scriptblock]$ReadBarrierReady,
 [Parameter(Mandatory)][scriptblock]$StartFirst,
 [Parameter(Mandatory)][scriptblock]$StartSecond,
 [Parameter(Mandatory)][scriptblock]$ObservePair,
 [Parameter(Mandatory)][scriptblock]$FinishBarrier,
 [Parameter(Mandatory)][scriptblock]$FinishChild,
 [Parameter(Mandatory)][scriptblock]$StopOwned)
 $barrier=$null;$children=[Collections.Generic.List[object]]::new();$failure=$null
 try {
  $barrier=& $StartBarrier
  $ready=& $ReadBarrierReady $barrier
  if($null -eq $ready -or $ready.barrierPid -ne $barrier.ProcessId -or $ready.grantedApplicationLocks -ne 1 -or $ready.sessions -ne 1){throw 'B60_AUTHORITATIVE_BARRIER_NOT_READY'}
  $children.Add((& $StartFirst));$children.Add((& $StartSecond))
  & $ObservePair $barrier $children[0] $children[1]
 }catch{$failure=$_}
 finally {
  if($null -ne $failure){& $StopOwned}
  if($null -ne $barrier){try{& $FinishBarrier $barrier}catch{if($null -eq $failure){$failure=$_}}}
  # Always retain each launched child's exit, output and independent SQL readback.
  foreach($child in $children){try{& $FinishChild $child}catch{if($null -eq $failure){$failure=$_}}}
 }
 if($null -ne $failure){throw $failure}
}
Export-ModuleMember Invoke-Bloco60ConcurrentPair

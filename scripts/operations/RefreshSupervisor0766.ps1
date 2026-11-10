# Pure scheduling helpers. Dot-sourcing does not start a process or a timer.
function Get-RefreshNext0766([DateTimeOffset]$CompletedAt){return $CompletedAt.AddMinutes(60)}
function Get-RefreshEligible0766([string]$StateDirectory){
 $eligible0766=[DateTimeOffset]::MinValue
 $supervisorPath0766=Join-Path $StateDirectory 'supervisor.json'
 if(Test-Path -LiteralPath $supervisorPath0766){
  $supervisorRecord0766=Get-Content -LiteralPath $supervisorPath0766 -Raw -Encoding utf8 | ConvertFrom-Json
  if($supervisorRecord0766.nextEligibleAt){$eligible0766=[DateTimeOffset]$supervisorRecord0766.nextEligibleAt}
 }
 $checkpointPath0766=Join-Path $StateDirectory 'checkpoint.json'
 if(Test-Path -LiteralPath $checkpointPath0766){
  $checkpointRecord0766=Get-Content -LiteralPath $checkpointPath0766 -Raw -Encoding utf8 | ConvertFrom-Json
  if($checkpointRecord0766.payload.lastCompletedAt){
   $candidate0766=Get-RefreshNext0766 ([DateTimeOffset]$checkpointRecord0766.payload.lastCompletedAt)
   if($candidate0766 -gt $eligible0766){$eligible0766=$candidate0766}
  }
 }
 return $eligible0766
}

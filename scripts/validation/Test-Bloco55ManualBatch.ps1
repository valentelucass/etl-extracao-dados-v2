$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
Import-Module (Join-Path $PSScriptRoot 'Bloco55Artifact.psm1') -Force
Import-Module (Join-Path $root 'scripts/runtime/Bloco55ManualBatch.psm1') -Force
$review=Read-Bloco55Bundle (Join-Path $root 'target/bloco55/reviewed-bundle-v4') 'a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13'
$original=Join-Path $root 'target/bloco55/manual/positive/manifest.json';$originalHash=(Get-FileHash $original).Hash.ToLowerInvariant()
$valid=Read-Bloco55ManualBatch $original $originalHash $review
if($valid.Requests.Count -ne 20){throw 'TWENTY_VALID_REQUESTS_REQUIRED'}
$directory=Join-Path $root ('target/bloco55/manual/validator-'+[guid]::NewGuid().ToString())
[void][IO.Directory]::CreateDirectory($directory)
$results=[Collections.Generic.List[object]]::new()
$cases=[ordered]@{LAST_REQUEST_CHANGED='MANUAL_REQUEST_HASH';UNKNOWN_REQUEST_FIELD='MANUAL_CLOSED_SCHEMA';DUPLICATE_EXECUTION='MANUAL_UNIQUE_IDS';WRONG_REFERENCE='MANUAL_TARIFF_REFERENCE';WRONG_CONFIG='MANUAL_CONFIGURATION_HASH';DAG_CHANGED='MANUAL_FROZEN_PREDECESSOR_MISMATCH';PATH_ESCAPE='MANUAL_REQUEST_REFERENCE';DUPLICATE_JSON='MANUAL_DUPLICATE_JSON_KEY'}
foreach($case in $cases.Keys){
 $work=Join-Path $directory $case;[void][IO.Directory]::CreateDirectory((Join-Path $work 'requests'))
 $m=Get-Content $original -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 foreach($entry in $m.requests){[IO.File]::Copy((Join-Path ([IO.Path]::GetDirectoryName($original)) $entry.path),(Join-Path $work $entry.path),$false)}
 $index=switch($case){'WRONG_REFERENCE'{3}'DAG_CHANGED'{1}default{19}}
 $target=Join-Path $work $m.requests[$index].path;$r=Get-Content $target -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 switch($case){
  LAST_REQUEST_CHANGED {$r.qualityVersion='changed'}
  UNKNOWN_REQUEST_FIELD {$r.uncontracted='value'}
  DUPLICATE_EXECUTION {$r.executionId=$valid.Requests[0].Document.executionId}
  WRONG_REFERENCE {$r.referenceReleaseId='999'}
  WRONG_CONFIG {$m.configurationSha256='a'*64}
  DAG_CHANGED {$dep=$r.dependencyRequest|ConvertFrom-Json -AsHashtable;$dep.cycleId=[guid]::NewGuid().ToString();$r.dependencyRequest=$dep|ConvertTo-Json -Compress}
  PATH_ESCAPE {$m.requests[19].path='../outside.json'}
  DUPLICATE_JSON {}
 }
 [IO.File]::WriteAllText($target,($r|ConvertTo-Json -Depth 8),$utf8)
 if($case -notin @('LAST_REQUEST_CHANGED','PATH_ESCAPE')){$m.requests[$index].sha256=(Get-FileHash $target).Hash.ToLowerInvariant()}
 $file=Join-Path $work 'manifest.json';$text=$m|ConvertTo-Json -Depth 8
 if($case -ceq 'DUPLICATE_JSON'){$text=$text.Replace('"version": "bloco55-manual-v1"','"version": "bloco55-manual-v1", "version": "bloco55-manual-v1"')}
 [IO.File]::WriteAllText($file,$text,$utf8)
 $reason='NOT_REJECTED'
 try{Read-Bloco55ManualBatch $file (Get-FileHash $file).Hash.ToLowerInvariant() $review|Out-Null}catch{$reason=$_.Exception.Message}
 $results.Add([ordered]@{id=$case;layer='POWERSHELL_OFFLINE_NO_SQL_NO_HTTP';expected=$cases[$case];observed=$reason;passed=$reason -ceq $cases[$case]})
}
if((Get-FileHash $original).Hash.ToLowerInvariant() -cne $originalHash){throw 'FROZEN_ORIGINAL_CHANGED'}
[IO.File]::WriteAllText((Join-Path $directory 'results.json'),($results|ConvertTo-Json -Depth 5),$utf8)
$results|ForEach-Object{[pscustomobject]$_}|Select-Object id,passed|ConvertTo-Json -Compress
if(@($results|Where-Object {-not $_.passed}).Count){throw 'MANUAL_VALIDATOR_NOT_PROVEN'}

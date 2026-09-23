#Requires -Version 7.0
param()
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture=Join-Path $root ('target/continuidade-guards/'+[guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$m=Get-Content (Join-Path $root 'docs/continuidade/manifesto-pos-bloco55.json') -Raw|ConvertFrom-Json
$paths=@($m.changedExistingFiles.path)+@($m.newFiles.path)+@('docs/continuidade/manifesto-pos-bloco55.json',$m.baseline.path)
if($paths.Count -gt 20){throw 'CONTINUITY_FIXTURE_BOUND'}
foreach($path in $paths){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..')){throw 'CONTINUITY_FIXTURE_PATH'}
 $destination=Join-Path $fixture $path;[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
 [IO.File]::Copy((Join-Path $root $path),$destination,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-ContinuidadeAgentes.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$Id,[string]$Path,[scriptblock]$Mutate,[string]$Expected){
 $file=Join-Path $fixture $Path;$bytes=[IO.File]::ReadAllBytes($file)
 try{
  $original=$utf8.GetString($bytes);$changed=& $Mutate $original
  if($changed -ceq $original){throw 'CONTINUITY_MUTATION_MUST_CHANGE'}
  [IO.File]::WriteAllText($file,$changed,$utf8)
  $reason='NOT_REJECTED';try{& $validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $Expected){throw ('CONTINUITY_GUARD_FAILED_'+$Id+'_'+$reason)}
  $results.Add([ordered]@{id=$Id;layer='OFFLINE_ISOLATED_DOCUMENT_FIXTURE';reason=$reason;passed=$true})
 }finally{[IO.File]::WriteAllBytes($file,$bytes)}
}
$manifest='docs/continuidade/manifesto-pos-bloco55.json'
Reject 'PROPOSAL_AS_AUTHORIZATION' $manifest {param($s)$s.Replace('"authorizesPhysicalExecution": false','"authorizesPhysicalExecution": true')} 'DOCUMENTATION_ONLY_REQUIRED'
Reject 'UNDECLARED_UNITS' $manifest {param($s)$s.Replace('"newReservations": 0','"newReservations": 128')} 'DOCUMENTATION_ONLY_REQUIRED'
Reject 'FALSE_PERCENTAGE' $manifest {param($s)$s.Replace('"done": 65','"done": 70')} 'PROGRESS_DRIFT'
Reject 'RUNTIME_CODE_EXCEPTION' $manifest {param($s)$s.Replace('"path": "AGENTS.md"','"path": "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java"')} 'EXACT_DOCUMENT_DELTA_REQUIRED'
Reject 'REWRITTEN_B55_BASELINE' $manifest {param($s)$s.Replace('bfccb40d72839ffe9115ab6e4d0760a4f0cecfee836efd14d6e216a3656479b7',('0'*64))} 'B55_BASELINE_CHANGED'
Reject 'CHANGED_CHECKPOINT' 'docs/continuidade/checkpoints/0001-pos-bloco55.md' {param($s)$s+"`nALTERED_UNPROVEN_COMPLETION"} 'CONTINUITY_HASH_'
& $validator|Out-Null
if($results.Count -ne 6){throw 'CONTINUITY_SIX_CONTRAPROOFS_REQUIRED'}
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5),$utf8)
'CONTINUITY_SIX_CONTRAPROOFS_PASS_ORIGINAL_FILES_AND_LEDGERS_UNCHANGED'

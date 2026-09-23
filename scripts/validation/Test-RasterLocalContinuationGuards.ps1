#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/esl-documentacao/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-EslDocumentation.ps1') -AsMap
}
$manifest='docs/catalogos/raster-contrato-local/manifesto.json'
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json
$baseline=Get-Content -LiteralPath (Join-Path $root $m.initialInventory.path) -Raw|ConvertFrom-Json
$fixture=Join-Path $root ('target/bloco56-raster/succession-guards/'+[guid]::NewGuid().ToString('N'))
foreach($path in @(@($baseline.path)+@($m.newFiles.path)+@($manifest)|Sort-Object -Unique)){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..')){throw 'RASC_GUARD_PATH'}
 $dest=Join-Path $fixture $path
 [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
 $sourcePath=if($null -ne $successor -and $successor.ContainsKey($path)){$successor[$path].snapshot}else{$path}
 [IO.File]::Copy((Join-Path $root $sourcePath),$dest,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-RasterLocalContinuation.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$Id,[string]$Path,[scriptblock]$Mutate,[string]$Expected,[switch]$RefreshCurrentHash){
 $file=Join-Path $fixture $Path;$mf=Join-Path $fixture $manifest
 $original=[IO.File]::ReadAllBytes($file);$originalManifest=[IO.File]::ReadAllBytes($mf)
 try{
  $before=$utf8.GetString($original);$after=& $Mutate $before
  if($before -ceq $after){throw 'RASC_GUARD_NO_MUTATION'}
  [IO.File]::WriteAllText($file,$after,$utf8)
  if($RefreshCurrentHash){
   $mm=$utf8.GetString($originalManifest)|ConvertFrom-Json
   ($mm.changedExistingFiles|Where-Object path -CEQ $Path).after=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
   [IO.File]::WriteAllText($mf,($mm|ConvertTo-Json -Depth 20),$utf8)
  }
  $reason='NOT_REJECTED';try{& $validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $Expected){throw ('RASC_GUARD_FAILED_'+$Id+'_'+$reason)}
  $results.Add([ordered]@{id=$Id;passed=$true;reason=$reason;layer='OFFLINE_ISOLATED_SUCCESSION_FIXTURE'})
 }finally{[IO.File]::WriteAllBytes($file,$original);[IO.File]::WriteAllBytes($mf,$originalManifest)}
}
Reject 'THIRD_ACCEPTANCE' $manifest {param($s)$s.Replace('"newCanonicalAcceptances": 2','"newCanonicalAcceptances": 3')} 'RASC_NO_VERTICAL_OR_IDENTITY_ACCEPTANCE'
Reject 'FALSE_PROGRESS' $manifest {param($s)$s.Replace('"done": 67','"done": 68')} 'RASC_PROGRESS'
Reject 'BUDGET_FROM_SCOPE_DECISION' $manifest {param($s)$s.Replace('"budgetConsumed": 0','"budgetConsumed": 1')} 'RASC_ZERO_EFFECTS'
Reject 'RUNTIME_HASH_EXCEPTION' $manifest {param($s)$s.Replace('"path": "STATES.md"','"path": "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java"')} 'RASC_EXACT_EIGHT_DELTAS'
Reject 'FALSE_RASTER_IDENTITY' 'STATES.md' {param($s)$s.Replace('- [ ] **V2-009c —','- [x] **V2-009c —')} 'RASC_ORIGINAL_CRITERIA_OR_OTHER_ACCEPTANCE_CHANGED' -RefreshCurrentHash
Reject 'FALSE_ESL_VERTICAL' 'STATES.md' {param($s)$s.Replace('- [ ] **V2-029 —','- [x] **V2-029 —')} 'RASC_ORIGINAL_CRITERIA_OR_OTHER_ACCEPTANCE_CHANGED' -RefreshCurrentHash
Reject 'HISTORICAL_MANIFEST_REWRITTEN' 'docs/catalogos/bloco56-continuacao/manifesto.json' {param($s)$s+"`n "} 'RASC_HASH_'
Reject 'SNAPSHOT_REWRITTEN' 'docs/continuidade/historico/bloco56-continuacao/STATES.md' {param($s)$s+"`nALTERED"} 'RASC_HASH_'
& $validator|Out-Null
if($results.Count -ne 8){throw 'RASC_GUARD_COUNT'}
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 8),$utf8)
'RASTER_SUCCESSION_EIGHT_CONTRAPROOFS_PASS'

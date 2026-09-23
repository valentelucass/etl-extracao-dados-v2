#Requires -Version 7.5
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture=Join-Path $root ('target/bloco57-local/guards-'+[guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$manifest='docs/catalogos/bloco57-local/manifesto.json'
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json -Depth 60 -DateKind String
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco57-complemento/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-Bloco57Complemento.ps1') -AsMap
}
$paths=@($m.preservedFiles.path)+@($m.changedExistingFiles.path)+@($m.newFiles.path)+@($manifest)
if($paths.Count -gt 1600){throw 'B57_GUARD_COPY_BOUND'}
foreach($path in $paths){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B57_GUARD_PATH'}
 $destination=Join-Path $fixture $path
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))|Out-Null
 # These six cases exercise the frozen B57 photograph; current succession has its own guards.
 $source=if($null -ne $successor -and $successor.ContainsKey($path)){$successor[$path].snapshot}else{$path}
 [IO.File]::Copy((Join-Path $root $source),$destination,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-Bloco57Local.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$id,[scriptblock]$mutate,[string]$expected){
 $file=Join-Path $fixture $manifest;$original=[IO.File]::ReadAllBytes($file)
 try {
  $candidate=$utf8.GetString($original)|ConvertFrom-Json -Depth 60 -DateKind String
  & $mutate $candidate
  [IO.File]::WriteAllText($file,($candidate|ConvertTo-Json -Depth 60),$utf8)
  $reason='NOT_REJECTED'
  try {& $validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $expected){throw ('B57_GUARD_FAILED_'+$id+'_'+$reason)}
  $results.Add(@{id=$id;passed=$true;reason=$reason;layer='ISOLATED_STATIC_COPY'})
 } finally {[IO.File]::WriteAllBytes($file,$original)}
}
Reject 'SOURCE_EFFECT' {param($m)$m.sourceCalls=1} '^B57_LIMIT_OR_ACCEPTANCE$'
Reject 'FALSE_ACCEPTANCE' {param($m)$m.newAcceptances=1} '^B57_LIMIT_OR_ACCEPTANCE$'
Reject 'HISTORICAL_REBASE' {param($m)$m.changedExistingFiles[0].before='0'*64} '^B57_BASELINE_BEFORE$'
Reject 'DIRECTORY_EXCEPTION' {param($m)$m.changedExistingFiles[0].path='src/main/java';$m.changedExistingFiles[0].snapshot='docs/continuidade/historico/bloco57/src/main/java'} '^B57_UNAPPROVED_DELTA$'
Reject 'SOURCE_DRIFT_HIDDEN' {param($m)$m.preservedFiles[0].sha256='0'*64} '^B57_PRESERVED_BASELINE$'
Reject 'SNAPSHOT_DRIFT' {param($m)$m.changedExistingFiles[0].snapshot='docs/continuidade/historico/bloco57/OTHER.md'} '^B57_UNAPPROVED_DELTA$'
& $validator|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5)+"`n",$utf8)
'B57_SIX_ISOLATED_SUCCESSION_GUARDS_PASS'

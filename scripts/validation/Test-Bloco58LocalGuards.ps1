#Requires -Version 7.5
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifest='docs/catalogos/bloco58-local/manifesto.json'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json -Depth 60 -DateKind String
$fixture=Join-Path $root ('target/bloco58-local/guards-'+[guid]::NewGuid().ToString('N'))
$paths=@($m.preservedFiles.path)+@($m.changedExistingFiles.path)+@($m.newFiles.path)+@($manifest)
if($paths.Count -gt 1600){throw 'B58_GUARD_COPY_BOUND'}
foreach($path in $paths){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B58_GUARD_PATH'}
 $destination=Join-Path $fixture $path
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))|Out-Null
 [IO.File]::Copy((Join-Path $root $path),$destination,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-Bloco58Local.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$id,[scriptblock]$mutate,[string]$expected,[string]$changedPath=''){
 $file=Join-Path $fixture $manifest;$original=[IO.File]::ReadAllBytes($file)
 $changed=if($changedPath){[IO.File]::ReadAllBytes((Join-Path $fixture $changedPath))}else{$null}
 try {
  $candidate=$utf8.GetString($original)|ConvertFrom-Json -Depth 60 -DateKind String
  & $mutate $candidate
  [IO.File]::WriteAllText($file,($candidate|ConvertTo-Json -Depth 60),$utf8)
  $reason='NOT_REJECTED'
  try {& $validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $expected){throw ('B58_GUARD_FAILED_'+$id+'_'+$reason)}
  $results.Add(@{id=$id;passed=$true;reason=$reason;layer='ISOLATED_STATIC_COPY'})
 } finally {
  [IO.File]::WriteAllBytes($file,$original)
  if($changedPath){[IO.File]::WriteAllBytes((Join-Path $fixture $changedPath),$changed)}
 }
}
Reject 'SOURCE_EFFECT' {param($m)$m.sourceCalls=1} '^B58_LIMIT_OR_ACCEPTANCE$'
Reject 'FALSE_ACCEPTANCE' {param($m)$m.newAcceptances=1} '^B58_LIMIT_OR_ACCEPTANCE$'
Reject 'HISTORICAL_REBASE' {param($m)$m.changedExistingFiles[0].before='0'*64} '^B58_BASELINE_BEFORE$'
Reject 'DIRECTORY_EXCEPTION' {param($m)$m.changedExistingFiles[0].path='src/test/java'} '^B58_EXACT_DELTAS$'
Reject 'PRESERVED_DRIFT_HIDDEN' {param($m)$m.preservedFiles[0].sha256='0'*64} '^B58_PRESERVED_BASELINE$'
Reject 'LIMIT_EXPANDED' {param($m)$m.limits.maximumGraphQlNodes=21} '^B58_CONSUMER_LIMIT$'
Reject 'SOURCE_ENABLED_AND_REHASHED' {
 param($m)
 $path='docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json';$file=Join-Path $fixture $path
 $plan=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json -Depth 20
 $plan.sourceExecutionEnabled=$true
 [IO.File]::WriteAllText($file,($plan|ConvertTo-Json -Depth 20),$utf8)
 ($m.preservedFiles|Where-Object path -CEQ $path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} '^B58_PRESERVED_BASELINE$' 'docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json'
Reject 'PROFILE_REWRITTEN_AND_REHASHED' {
 param($m)
 $path='src/test/resources/contracts/v2-012/profiles/usuarios-individual.profile.json';$file=Join-Path $fixture $path
 [IO.File]::AppendAllText($file,[Environment]::NewLine,$utf8)
 ($m.preservedFiles|Where-Object path -CEQ $path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} '^B58_PRESERVED_BASELINE$' 'src/test/resources/contracts/v2-012/profiles/usuarios-individual.profile.json'
Reject 'CHECKBOX_CHANGED_AND_REHASHED' {
 param($m)
 $file=Join-Path $fixture 'STATES.md';$text=[IO.File]::ReadAllText($file)
 $pattern=[regex]::new('(?m)^([ \t]*- )\[ \]')
 [IO.File]::WriteAllText($file,$pattern.Replace($text,'$1[x]',1),$utf8)
 ($m.changedExistingFiles|Where-Object path -CEQ 'STATES.md').after=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} '^B58_ORIGINAL_CHECKBOX_CHANGED$' 'STATES.md'
& $validator|Out-Null
& (Join-Path $fixture 'scripts/validation/Test-Bloco57Complemento.ps1')|Out-Null
& (Join-Path $fixture 'scripts/validation/Test-Bloco57Local.ps1')|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5)+[Environment]::NewLine,$utf8)
'B58_NINE_SUCCESSION_GUARDS_PASS'

#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifest='docs/catalogos/pos-bloco58/manifesto.json'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json -Depth 60 -DateKind String
$fixture=Join-Path $root ('target/pos-bloco58-docs/guards-'+[guid]::NewGuid().ToString('N'))
$paths=@($m.preservedFiles.path)+@($m.changedExistingFiles.path)+@($m.newFiles.path)+@($manifest)
if($paths.Count -gt 1600){throw 'POST58_GUARD_BOUND'}
foreach($path in $paths){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'POST58_GUARD_PATH'}
 $dest=Join-Path $fixture $path
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))|Out-Null
 [IO.File]::Copy((Join-Path $root $path),$dest,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-PosBloco58Documentation.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$id,[scriptblock]$mutation,[string]$expected,[string]$changedPath=''){
 $file=Join-Path $fixture $manifest;$original=[IO.File]::ReadAllBytes($file)
 $changed=if($changedPath){[IO.File]::ReadAllBytes((Join-Path $fixture $changedPath))}else{$null}
 try{
  $candidate=$utf8.GetString($original)|ConvertFrom-Json -Depth 60 -DateKind String
  & $mutation $candidate
  [IO.File]::WriteAllText($file,($candidate|ConvertTo-Json -Depth 60),$utf8)
  $reason='NOT_REJECTED'
  try{& $validator|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -cne $expected){throw ('POST58_GUARD_FAILED_'+$id+'_'+$reason)}
  $results.Add(@{id=$id;passed=$true;reason=$reason;layer='ISOLATED_STATIC_COPY'})
 }finally{
  [IO.File]::WriteAllBytes($file,$original)
  if($changedPath){[IO.File]::WriteAllBytes((Join-Path $fixture $changedPath),$changed)}
 }
}
Reject 'FALSE_ACCEPTANCE' {param($m)$m.newAcceptances=1} 'POST58_EFFECT_OR_ACCEPTANCE'
Reject 'SOURCE_EFFECT' {param($m)$m.sourceCalls=1} 'POST58_EFFECT_OR_ACCEPTANCE'
Reject 'UNAUTHORIZED_NEW_BLOCK' {param($m)$m.startsBloco59=$true} 'POST58_SCOPE'
Reject 'FALSE_JAVA_RERUN' {param($m)$m.javaReexecuted=$true} 'POST58_SCOPE'
Reject 'HISTORICAL_REBASE' {param($m)$m.changedExistingFiles[0].before='0'*64} 'POST58_BASELINE_BEFORE'
Reject 'DIRECTORY_EXCEPTION' {param($m)$m.changedExistingFiles[0].path='src/main/java'} 'POST58_EXACT_DELTAS'
Reject 'CODE_REWRITTEN_AND_REHASHED' {
 param($m)
 $path='src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java'
 $file=Join-Path $fixture $path
 [IO.File]::AppendAllText($file,[Environment]::NewLine,$utf8)
 ($m.preservedFiles|Where-Object path -CEQ $path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'POST58_PRESERVED_BASELINE' 'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java'
Reject 'SOURCE_ENABLED_AND_REHASHED' {
 param($m)
 $path='docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json';$file=Join-Path $fixture $path
 $plan=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json -Depth 20
 $plan.sourceExecutionEnabled=$true
 [IO.File]::WriteAllText($file,($plan|ConvertTo-Json -Depth 20),$utf8)
 ($m.preservedFiles|Where-Object path -CEQ $path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'POST58_PRESERVED_BASELINE' 'docs/catalogos/bloco57-complemento/cotacoes-fonte-futura.json'
Reject 'CHECKBOX_CHANGED_AND_REHASHED' {
 param($m)
 $file=Join-Path $fixture 'STATES.md';$text=[IO.File]::ReadAllText($file)
 $pattern=[regex]::new('(?m)^([ \t]*- )\[ \]')
 [IO.File]::WriteAllText($file,$pattern.Replace($text,'$1[x]',1),$utf8)
 ($m.changedExistingFiles|Where-Object path -CEQ 'STATES.md').after=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'POST58_CHECKBOX_CHANGED' 'STATES.md'
& $validator|Out-Null
& (Join-Path $fixture 'scripts/validation/Test-Bloco58Local.ps1')|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5)+[Environment]::NewLine,$utf8)
'POST58_NINE_DOCUMENTATION_GUARDS_PASS'

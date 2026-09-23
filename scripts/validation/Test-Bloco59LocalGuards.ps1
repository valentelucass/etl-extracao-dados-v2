#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$manifest='docs/catalogos/bloco59-local/manifesto.json'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json -Depth 60 -DateKind String
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco60-local/manifesto.json')){$successor=& "$PSScriptRoot/Test-Bloco60Local.ps1" -AsMap}
$fixture=Join-Path $root ($(if($null -ne $successor){'target/bloco60-local/b59-guards-'}else{'target/bloco59-local/guards-'})+[guid]::NewGuid().ToString('N'))
$paths=@($m.preservedFiles.path)+@($m.changedExistingFiles.path)+@($m.newFiles.path)+@($manifest)
if($paths.Count -gt 1700){throw 'B59_GUARD_BOUND'}
foreach($path in $paths){
 if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'B59_GUARD_PATH'}
 $dest=Join-Path $fixture $path
 [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))|Out-Null
 $source=if($null -ne $successor -and $successor.ContainsKey($path)){$successor[$path].snapshot}else{$path}
 [IO.File]::Copy((Join-Path $root $source),$dest,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-Bloco59Local.ps1'
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
  if($reason -cne $expected){throw ('B59_GUARD_FAILED_'+$id+'_'+$reason)}
  $results.Add(@{id=$id;passed=$true;reason=$reason;layer='ISOLATED_STATIC_COPY'})
 }finally{
  [IO.File]::WriteAllBytes($file,$original)
  if($changedPath){[IO.File]::WriteAllBytes((Join-Path $fixture $changedPath),$changed)}
 }
}
Reject 'FALSE_PARENT_ACCEPTANCE' {param($m)$m.newAcceptances=1} 'B59_LIMIT_OR_ACCEPTANCE'
Reject 'SOURCE_EFFECT' {param($m)$m.sourceCalls=1} 'B59_LIMIT_OR_ACCEPTANCE'
Reject 'SQL_APPLIED_CLAIM' {param($m)$m.sqlApplied=$true} 'B59_FORBIDDEN_EFFECT'
Reject 'BUDGET_RENEWAL' {param($m)$m.budgetRenewed=$true} 'B59_FORBIDDEN_EFFECT'
Reject 'FALSE_PROGRESS' {param($m)$m.progress.done=68} 'B59_PROGRESS'
Reject 'HISTORICAL_REBASE' {param($m)$m.changedExistingFiles[0].before='0'*64} 'B59_BASELINE_BEFORE'
Reject 'DIRECTORY_EXCEPTION' {param($m)$m.changedExistingFiles[0].path='src/main/java'} 'B59_EXACT_DELTAS'
Reject 'UNRELATED_CODE_REWRITTEN_AND_REHASHED' {
 param($m)
 $path='src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java'
 $file=Join-Path $fixture $path
 [IO.File]::AppendAllText($file,[Environment]::NewLine,$utf8)
 ($m.preservedFiles|Where-Object path -CEQ $path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'B59_PRESERVED_BASELINE' 'src/main/java/br/com/esl/etl/v2/modulos/coletas/aplicacao/ColetaDataExportRecordMapper.java'
Reject 'CHECKBOX_CHANGED_AND_REHASHED' {
 param($m)
 $file=Join-Path $fixture 'STATES.md';$text=[IO.File]::ReadAllText($file)
 $pattern=[regex]::new('(?m)^([ \t]*- )\[ \]')
 [IO.File]::WriteAllText($file,$pattern.Replace($text,'$1[x]',1),$utf8)
 ($m.changedExistingFiles|Where-Object path -CEQ 'STATES.md').after=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'B59_CHECKBOX_CHANGED' 'STATES.md'
Reject 'DATA_EXPORT_FINGERPRINT_CHANGED_AND_REHASHED' {
 param($m)
 $path='src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java';$file=Join-Path $fixture $path
 [IO.File]::WriteAllText($file,[IO.File]::ReadAllText($file).Replace('sorted.remove("invocationId");','sorted.remove("executionId");'),$utf8)
 ($m.changedExistingFiles|Where-Object path -CEQ $path).after=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
} 'B59_DATA_EXPORT_FINGERPRINT_CHANGED' 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java'
& $validator|Out-Null
& (Join-Path $fixture 'scripts/validation/Test-PosBloco58Documentation.ps1')|Out-Null
& (Join-Path $fixture 'scripts/validation/Test-Bloco58Local.ps1')|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 5)+"`n",$utf8)
[pscustomobject]@{passed=$true;guards=$results.Count;layer='ISOLATED_STATIC_SUCCESSION';resultsPath=[IO.Path]::GetRelativePath($root,(Join-Path $fixture 'results.json')).Replace('\','/')}

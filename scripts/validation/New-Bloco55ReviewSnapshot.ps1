#Requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]{1,48}$')][string]$ReviewId)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root ('target/bloco55/reviews/'+$ReviewId)
if(Test-Path $directory){throw 'EXISTING_REVIEW_PRESERVE'}
[void][IO.Directory]::CreateDirectory($directory)
$utf8=[Text.UTF8Encoding]::new($false,$true)
$inventory=Get-Content (Join-Path $root 'target/bloco55/initial-inventory.json') -Raw|ConvertFrom-Json
$before=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
$changed=[Collections.Generic.List[object]]::new();$current=[Collections.Generic.List[object]]::new();$new=[Collections.Generic.List[object]]::new()
foreach($entry in $inventory){
 if($entry.path.Contains('..') -or [IO.Path]::IsPathRooted($entry.path)){throw 'INITIAL_INVENTORY_PATH'}
 $before.Add($entry.path,$entry)
 $original=Join-Path $root ('target/bloco55/initial/'+$entry.path)
 $present=Join-Path $root $entry.path
 if(-not (Test-Path -LiteralPath $present -PathType Leaf)){throw ('ORIGINAL_FILE_MISSING_'+$entry.path)}
 if((Get-FileHash -LiteralPath $original).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'INITIAL_SNAPSHOT_CHANGED'}
 $hash=(Get-FileHash -LiteralPath $present).Hash.ToLowerInvariant()
 if($hash -cne $entry.sha256){
  if($entry.path -cmatch '^database/migrations/V0(?:0[1-9]|1[0-9]|2[01])__'){throw 'APPLIED_V001_V021_CHANGED'}
  $changed.Add([ordered]@{path=$entry.path;before=$entry.sha256;after=$hash})
 }
}
# The generated manifest binds this review. Exclude its self-referential bytes
# from this inventory; the manifest is delivered separately beside the patch.
$files=@(& rg --files --hidden -g '!.git/**' -g '!target/**' -g '!.codex-local/**' -g '!.env' -g '!database/manifest/runtime-bloco55.json' $root)
if($LASTEXITCODE -ne 0 -or $files.Count -gt 2000){throw 'REVIEW_FILE_INVENTORY_BOUND'}
$texts=0;$scripts=0
foreach($path in $files){
 $relative=[IO.Path]::GetRelativePath($root,$path).Replace('\','/')
 if($relative.StartsWith('../')){throw 'REVIEW_FILE_ESCAPE'}
 $item=Get-Item -LiteralPath $path
 if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or $item.Length -gt 8MB){throw 'REVIEW_FILE_BOUND_OR_REPARSE'}
 $hash=(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()
 $entry=[ordered]@{path=$relative;sha256=$hash;bytes=$item.Length};$current.Add($entry)
 if(-not $before.ContainsKey($relative)){$new.Add($entry)}
 if($item.Extension -in @('.java','.md','.json','.xml','.ps1','.psm1','.sql','.txt','.properties','.csv','.sha256','.cs','.yml','.yaml') -or $item.Name -in @('.gitattributes','.gitignore','.editorconfig')){
  $bytes=[IO.File]::ReadAllBytes($path);[void]$utf8.GetString($bytes)
  if($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191){throw ('UTF8_BOM_'+$relative)}
  $texts++
 }
 if($item.Extension -in @('.ps1','.psm1')){
  $tokens=$null;$errors=$null;[void][Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$errors)
  if($errors.Count){throw ('POWERSHELL_SYNTAX_'+$relative)};$scripts++
 }
}
$b54=Get-Content (Join-Path $root 'database/manifest/runtime-bloco54.json') -Raw|ConvertFrom-Json
foreach($entry in $b54.proofFiles){if((Get-FileHash -LiteralPath (Join-Path $root $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'B54_PRIVATE_EVIDENCE_CHANGED'}}
foreach($pair in @(@('target/bloco53/cumulative-reservations.txt','dc45f84046fa1c2a6184dc181cc9bf1b39878c5c94ed4574e22d761f0cfc3df1'),@('target/bloco54/ledger.jsonl','64ad2f64d8dd899236926c5e9f9a50bd023b765ee232cb473b20934edcb75356'))){if((Get-FileHash -LiteralPath (Join-Path $root $pair[0])).Hash.ToLowerInvariant() -cne $pair[1]){throw 'B53_B54_LEDGER_CHANGED'}}
$empty=Join-Path $directory 'empty-before-new-file.txt';[IO.File]::WriteAllText($empty,'',$utf8)
$patch=[IO.StreamWriter]::new((Join-Path $directory 'bloco55-only.patch'),$false,$utf8)
try{
 foreach($entry in @($changed.ToArray())+@($new.ToArray())){
  $old=if($before.ContainsKey($entry.path)){Join-Path $root ('target/bloco55/initial/'+$entry.path)}else{$empty}
  $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command git).Source;$start.WorkingDirectory=$root;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
  foreach($arg in @('diff','--no-index','--no-ext-diff','--text','--',$old,(Join-Path $root $entry.path))){$start.ArgumentList.Add($arg)}
  $process=[Diagnostics.Process]::Start($start)
  try{$output=$process.StandardOutput.ReadToEnd();$errorOutput=$process.StandardError.ReadToEnd();$process.WaitForExit();if($process.ExitCode -notin @(0,1)){throw 'OWN_DIFF_FAILED'};$patch.Write($output)}finally{$process.Dispose()}
 }
}finally{$patch.Dispose()}
& git -C $root diff --check *> (Join-Path $directory 'git-diff-check.log')
if($LASTEXITCODE -ne 0){throw 'GIT_DIFF_CHECK_FAILED'}
foreach($pair in @(@('changed-initial.json',$changed.ToArray()),@('new-paths.json',$new.ToArray()),@('current-inventory.json',$current.ToArray()))){[IO.File]::WriteAllText((Join-Path $directory $pair[0]),(ConvertTo-Json -InputObject $pair[1] -Depth 5),$utf8)}
$receipt=[ordered]@{reviewId=$ReviewId;utc=[DateTimeOffset]::UtcNow.ToString('o');initialFiles=$inventory.Count;initialPresent=$inventory.Count;changedInitial=$changed.Count;newPaths=$new.Count;currentFiles=$current.Count;selfReferentialManifestExcluded='database/manifest/runtime-bloco55.json';strictUtf8Texts=$texts;powershellFilesParsed=$scripts;preservedAppliedMigrations=21;b54PrivateProofsPreserved=$b54.proofFiles.Count;b53LedgerPreserved=$true;b54LedgerPreserved=$true;diffCheck=$true;patchSha256=(Get-FileHash (Join-Path $directory 'bloco55-only.patch')).Hash.ToLowerInvariant();passed=$true}
[IO.File]::WriteAllText((Join-Path $directory 'receipt.json'),($receipt|ConvertTo-Json),$utf8)
[pscustomobject]$receipt

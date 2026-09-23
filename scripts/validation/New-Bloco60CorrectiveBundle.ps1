#Requires -Version 7.5
param([ValidatePattern('^bundle-v[1-9][0-9]?$')][string]$Revision='bundle-v2')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output=Join-Path $root ('target/b60-correcao-20260910/'+$Revision)
if(Test-Path $output){throw 'B60_BUNDLE_ALREADY_EXISTS'}
$build=Join-Path $root 'target/b60-correcao-20260910/verify-01/target'
$old=Join-Path $root 'target/bloco55/reviewed-bundle-v4'
$oldHash=(Get-FileHash (Join-Path $old 'manifest.json')).Hash.ToLowerInvariant()
if($oldHash -cne 'a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13'){throw 'B60_OLD_BUNDLE_HASH_REQUIRED'}
$oldManifest=Get-Content (Join-Path $old 'manifest.json') -Raw|ConvertFrom-Json
foreach($entry in $oldManifest.files){if((Get-FileHash (Join-Path $old $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'B60_OLD_BUNDLE_DRIFT'}}
$trust=Join-Path $root 'target/bloco53/provisioning-local-accounts/reviewed-bundle'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$base=Get-Content (Join-Path $old 'runtime.properties') -Raw
$config=@('runtime.environment=LOCAL_SHADOW','runtime.business-timezone=America/Sao_Paulo')
$config+=@($base -split "`r?`n"|Where-Object {$_ -cmatch '^dataexport\.'})|ForEach-Object {$_ -replace '^dataexport.base-url=.*','dataexport.base-url=http://127.0.0.1:62160'}
$config+=@('graphql.enabled=true','graphql.endpoint=http://127.0.0.1:62160/graphql','graphql.source-instance=LOCAL_V2','graphql.tenant-scope=LOCAL_V2','graphql.timeout-seconds=30','graphql.max-response-bytes=1048576')
$config+=@($config|Where-Object {$_ -cmatch '^dataexport\.(retry|resilience)\.'})|ForEach-Object {$_.Replace('dataexport.','graphql.')}
$config+=@($base -split "`r?`n"|Where-Object {$_ -cmatch '^shadow\.'})
$writers=@('S-1-5-32-544','S-1-5-18')|ForEach-Object {([Security.Principal.SecurityIdentifier]::new($_)).Translate([Security.Principal.NTAccount]).Value}
$resources=@{
 'runtime-authority.properties'=[IO.File]::ReadAllBytes((Join-Path $trust 'runtime-authority.properties'));
 'runtime-sql-public.cer'=[IO.File]::ReadAllBytes((Join-Path $trust 'sql-server-public.cer'));
 'runtime-laboratory.properties'=$utf8.GetBytes("bloco60-v1`nvalid-until=2026-09-16T00:00:00Z`n");
 'runtime-artifact-writers.json'=$utf8.GetBytes(($writers|ConvertTo-Json -Compress));
 'logback.xml'=[IO.File]::ReadAllBytes((Join-Path $root 'config/laboratory/logback-bloco54.xml'))
}
$official=Join-Path $build 'etl-dataexport-v2.jar'
$hashes=@{};$zip=[IO.Compression.ZipFile]::OpenRead($official)
try {foreach($entry in $zip.Entries){$stream=$entry.Open();try{$hashes[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}}}finally{$zip.Dispose()}
foreach($name in @('runtime-authority.properties','runtime-laboratory.properties')){if($hashes.ContainsKey($name)){throw 'B60_STANDARD_JAR_MUST_HAVE_NO_AUTHORITY'}}
$variants=[Collections.Generic.List[object]]::new()
foreach($variant in @('administered','swapped','expired','inconsistent')){
 $bundle=Join-Path $output $variant
 foreach($path in @('lib','native','standard/lib','probe')){[void][IO.Directory]::CreateDirectory((Join-Path $bundle $path))}
 [IO.File]::Copy($official,(Join-Path $bundle 'etl-dataexport-v2.jar'),$false)
 [IO.File]::Copy($official,(Join-Path $bundle 'standard/etl-dataexport-v2.jar'),$false)
 foreach($file in Get-ChildItem (Join-Path $build 'lib') -File){foreach($prefix in @('lib/','standard/lib/')){[IO.File]::Copy($file.FullName,(Join-Path $bundle ($prefix+$file.Name)),$false)}}
 foreach($entry in @($oldManifest.files|Where-Object path -like 'native/*')){[IO.File]::Copy((Join-Path $old $entry.path),(Join-Path $bundle $entry.path),$false)}
 $probeFiles=@(Get-ChildItem (Join-Path $build 'test-classes/br/com/esl/etl/v2/bootstrap') -File|Where-Object Name -cmatch '^RuntimeUsersPhysicalProbe(?:\$.*)?\.class$')
 $probeFiles+=Get-Item (Join-Path $build 'test-classes/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeUsersPhysicalAuthority.class')
 foreach($file in $probeFiles){$relative=[IO.Path]::GetRelativePath((Join-Path $build 'test-classes'),$file.FullName);$dest=Join-Path $bundle ('probe/'+$relative);[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest));[IO.File]::Copy($file.FullName,$dest,$false)}
 $jar=Join-Path $bundle 'etl-dataexport-v2.jar';$zip=[IO.Compression.ZipFile]::Open($jar,[IO.Compression.ZipArchiveMode]::Update)
 try {
  foreach($name in $resources.Keys){
   $bytes=$resources[$name]
   if($name -ceq 'runtime-authority.properties' -and $variant -ceq 'swapped'){$bytes=$utf8.GetBytes($utf8.GetString($bytes).Replace('fd966e17-71c7-4071-9ccc-bedcaecffcd0','60000000-0000-0000-0000-000000000060'))}
   $entry=$zip.GetEntry($name);if($null -ne $entry){if($name -cne 'logback.xml'){throw 'B60_ADMIN_RESOURCE_ALREADY_EXISTS'};$entry.Delete()}
   $entry=$zip.CreateEntry($name);$stream=$entry.Open();try{$stream.Write($bytes)}finally{$stream.Dispose()}
  }
 } finally {$zip.Dispose()}
 $entries=[Collections.Generic.List[object]]::new();$zip=[IO.Compression.ZipFile]::OpenRead($jar)
 try {
  if($zip.Entries.Count -ne $hashes.Count+$resources.Count-1){throw 'B60_JAR_ENTRY_DELTA'}
  foreach($entry in $zip.Entries){
   if($entry.FullName -cmatch '(PhysicalProbe|PhysicalAuthority|Test.class$|runtime-laboratory/)'){throw 'B60_TEST_CONTENT_IN_OFFICIAL_JAR'}
   $stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}
   if($hashes.ContainsKey($entry.FullName) -and $entry.FullName -cne 'logback.xml' -and $hashes[$entry.FullName] -cne $hash){throw 'B60_OFFICIAL_CLASS_CHANGED'}
   $entries.Add(@{entry=$entry.FullName;sha256=$hash;administrative=$resources.ContainsKey($entry.FullName)})
  }
 }finally{$zip.Dispose()}
 [IO.File]::WriteAllText((Join-Path $output ($variant+'-entries.json')),($entries|ConvertTo-Json -Depth 5),$utf8)
 [IO.File]::WriteAllText((Join-Path $bundle 'runtime.properties'),($config -join "`n")+"`n",$utf8)
 $files=@(Get-ChildItem $bundle -Recurse -File|Sort-Object FullName|ForEach-Object {@{path=[IO.Path]::GetRelativePath($bundle,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant()}})
 $manifest=[ordered]@{version=1;block=60;database='localhost/ETL_SISTEMA_V2_SHADOW';validFrom='2026-09-09T00:00:00Z';validUntil=$(if($variant -ceq 'expired'){'2026-09-08T00:00:00Z'}else{'2026-09-16T00:00:00Z'});files=$files}
 [IO.File]::WriteAllText((Join-Path $bundle 'manifest.json'),($manifest|ConvertTo-Json -Depth 6),$utf8)
 if($variant -ceq 'inconsistent'){[IO.File]::AppendAllText((Join-Path $bundle 'runtime.properties'),"# B60 controlled hash contradiction`n",$utf8)}
 $variants.Add(@{variant=$variant;path='target/b60-correcao-20260910/'+$Revision+'/'+$variant;manifest=(Get-FileHash (Join-Path $bundle 'manifest.json')).Hash.ToLowerInvariant();expectedRefusal=($variant -cne 'administered')})
}
[IO.File]::WriteAllText((Join-Path $output 'variants.json'),($variants|ConvertTo-Json -Depth 5),$utf8)
$variants|ConvertTo-Json -Depth 5

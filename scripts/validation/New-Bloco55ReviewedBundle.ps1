param([ValidatePattern('^reviewed-bundle-v[1-9][0-9]?$')][string]$Revision='reviewed-bundle-v1')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$bundle=Join-Path $root ('target/bloco55/'+$Revision)
if(Test-Path $bundle){throw 'BUNDLE_EXISTS_PRESERVE'}
$encoding=[Text.UTF8Encoding]::new($false,$true)
$build=Join-Path $root 'target/bloco55-build'
$old=Join-Path $root 'target/bloco53/provisioning-local-accounts/reviewed-bundle'
$until='2026-10-07T22:34:30.615Z'
if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($until)){throw 'ORIGINAL_VALIDITY_EXPIRED'}
[void][IO.Directory]::CreateDirectory($bundle)
foreach($directory in @('lib','native')){[void][IO.Directory]::CreateDirectory((Join-Path $bundle $directory))}
foreach($file in Get-ChildItem (Join-Path $build 'lib') -File){[IO.File]::Copy($file.FullName,(Join-Path $bundle ('lib/'+$file.Name)),$false)}
$cache=Join-Path $root 'target/bloco54/reviewed-bundle-v7'
$oldManifest=Get-Content (Join-Path $cache 'manifest.json') -Raw|ConvertFrom-Json
$dll=@($oldManifest.files|Where-Object path -like 'native/*')
if($dll.Count -ne 1){throw 'EXACT_CACHED_DLL_REQUIRED'}
$dllSource=Join-Path $cache $dll[0].path
if((Get-FileHash $dllSource).Hash.ToLowerInvariant() -cne $dll[0].sha256){throw 'CACHED_DLL_CHANGED'}
[IO.File]::Copy($dllSource,(Join-Path $bundle $dll[0].path),$false)
$jar=Join-Path $bundle 'etl-dataexport-v2.jar'
[IO.File]::Copy((Join-Path $build 'etl-dataexport-v2.jar'),$jar,$false)
$originalHashes=@{}
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try{foreach($entry in $zip.Entries){$stream=$entry.Open();try{$originalHashes[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}}}finally{$zip.Dispose()}
$writers=@('S-1-5-32-544','S-1-5-18')|ForEach-Object{([Security.Principal.SecurityIdentifier]::new($_)).Translate([Security.Principal.NTAccount]).Value}
$resources=@{
 'runtime-authority.properties'=[IO.File]::ReadAllBytes((Join-Path $old 'runtime-authority.properties'));
 'runtime-sql-public.cer'=[IO.File]::ReadAllBytes((Join-Path $old 'sql-server-public.cer'));
 'runtime-laboratory.properties'=$encoding.GetBytes("bloco55-v1`nvalid-until=$until`n");
 'logback.xml'=[IO.File]::ReadAllBytes((Join-Path $root 'config/laboratory/logback-bloco54.xml'));
 'runtime-artifact-writers.json'=$encoding.GetBytes(($writers|ConvertTo-Json -Compress))}
$zip=[IO.Compression.ZipFile]::Open($jar,[IO.Compression.ZipArchiveMode]::Update)
try{foreach($name in $resources.Keys){$entry=$zip.GetEntry($name);if($null -ne $entry){if($name -cne 'logback.xml'){throw 'ADMIN_RESOURCE_ALREADY_EXISTS'};$entry.Delete()};$entry=$zip.CreateEntry($name);$stream=$entry.Open();try{$stream.Write($resources[$name])}finally{$stream.Dispose()}}}finally{$zip.Dispose()}
$entries=[Collections.Generic.List[object]]::new()
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try{
 if($zip.Entries.Count -ne $originalHashes.Count+$resources.Count-1){throw 'UNEXPECTED_JAR_ENTRY_DELTA'}
 foreach($entry in $zip.Entries){
  if($entry.FullName -match '(?i)(PhysicalHarness|RuntimeBootstrapTest|runtime-laboratory/|synthetic.json|Bloco5[345]Process|PipelineTest)'){throw 'TEST_MATERIAL_IN_OFFICIAL_JAR'}
  $stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}
  if($originalHashes.ContainsKey($entry.FullName) -and $originalHashes[$entry.FullName] -cne $hash -and $entry.FullName -cne 'logback.xml'){throw 'ORIGINAL_JAR_ENTRY_CHANGED'}
  $entries.Add([ordered]@{entry=$entry.FullName;sha256=$hash;administrative=$resources.ContainsKey($entry.FullName)})
 }
}finally{$zip.Dispose()}
[IO.File]::WriteAllText((Join-Path $root ('target/bloco55/jar-entries-'+$Revision+'.json')),($entries|ConvertTo-Json -Depth 4),$encoding)
[IO.File]::Copy((Join-Path $cache 'runtime.properties'),(Join-Path $bundle 'runtime.properties'),$false)
if(-not (Test-Path (Join-Path $root 'database/proposals/bloco55-dq-correction/applied.json'))){throw 'QUALIFIED_DQ_CORRECTION_REQUIRED'}
$quality=@($oldManifest.quality)+@(Get-Content (Join-Path $root 'database/proposals/bloco55-dq-correction/quality-references.json') -Raw|ConvertFrom-Json)
if($quality.Count -ne 9){throw 'EXACT_FIVE_BACKFILL_FOUR_ORIGINAL_OTHER_POLICIES'}
$files=@(Get-ChildItem $bundle -Recurse -File|Sort-Object FullName|ForEach-Object{[ordered]@{path=[IO.Path]::GetRelativePath($bundle,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant()}})
$manifest=[ordered]@{version=1;block=55;database='localhost/ETL_SISTEMA_V2_SHADOW';validUntil=$until;quality=$quality;files=$files}
[IO.File]::WriteAllText((Join-Path $bundle 'manifest.json'),($manifest|ConvertTo-Json -Depth 8),$encoding)
'REVIEWED_BUNDLE_MANIFEST_SHA256='+(Get-FileHash (Join-Path $bundle 'manifest.json')).Hash.ToLowerInvariant()

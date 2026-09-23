param([ValidateSet('reviewed-bundle','reviewed-bundle-v2','reviewed-bundle-v3','reviewed-bundle-v4','reviewed-bundle-v5','reviewed-bundle-v6','reviewed-bundle-v7')][string]$RevisionLabel='reviewed-bundle-v3')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$bundle=Join-Path $root ('target/bloco54/'+$RevisionLabel)
if(Test-Path -LiteralPath $bundle){throw 'REVIEWED_BUNDLE_ALREADY_EXISTS_PRESERVE'}
$encoding=[Text.UTF8Encoding]::new($false,$true)
$build=Join-Path $root $(if($RevisionLabel -ceq 'reviewed-bundle-v7'){'target/bloco54-build-v7'}elseif($RevisionLabel -ceq 'reviewed-bundle-v6'){'target/bloco54-build-v6'}elseif($RevisionLabel -ceq 'reviewed-bundle-v5'){'target/bloco54-build-v5'}elseif($RevisionLabel -ceq 'reviewed-bundle-v4'){'target/bloco54-build-v4'}else{'target/bloco54-build'})
$old=Join-Path $root 'target/bloco53/provisioning-local-accounts/reviewed-bundle'
$inventory=Get-Content (Join-Path $root 'target/bloco54/identity-inventory.json') -Raw | ConvertFrom-Json -DateKind String
$until=($inventory | Sort-Object validUntil | Select-Object -First 1).validUntil
if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($until)){throw 'ORIGINAL_VALIDITY_EXPIRED'}
[void][IO.Directory]::CreateDirectory($bundle)
foreach($directory in @('lib','native')){
 [void][IO.Directory]::CreateDirectory((Join-Path $bundle $directory))
 foreach($file in Get-ChildItem -LiteralPath (Join-Path $build $directory) -File){[IO.File]::Copy($file.FullName,(Join-Path $bundle ($directory+'/'+$file.Name)),$false)}
}
$jar=Join-Path $bundle 'etl-dataexport-v2.jar'
[IO.File]::Copy((Join-Path $build 'etl-dataexport-v2.jar'),$jar,$false)
Add-Type -AssemblyName System.IO.Compression
$originalHashes=@{}
$original=[IO.Compression.ZipFile]::OpenRead($jar)
try {foreach($entry in $original.Entries){$stream=$entry.Open();try{$originalHashes[$entry.FullName]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}}}finally{$original.Dispose()}
$resources=@{
 'runtime-authority.properties'=[IO.File]::ReadAllBytes((Join-Path $old 'runtime-authority.properties'))
 'runtime-sql-public.cer'=[IO.File]::ReadAllBytes((Join-Path $old 'sql-server-public.cer'))
 'runtime-laboratory.properties'=$encoding.GetBytes("bloco54-v1`nvalid-until=$until`n")
 'logback.xml'=[IO.File]::ReadAllBytes((Join-Path $root 'config/laboratory/logback-bloco54.xml'))
}
if($RevisionLabel -in @('reviewed-bundle-v5','reviewed-bundle-v6','reviewed-bundle-v7')){
 $writers=@('S-1-5-32-544','S-1-5-18')|ForEach-Object {([Security.Principal.SecurityIdentifier]::new($_)).Translate([Security.Principal.NTAccount]).Value}
 $resources['runtime-artifact-writers.json']=$encoding.GetBytes(($writers|ConvertTo-Json -Compress))
}
$zip=[IO.Compression.ZipFile]::Open($jar,[IO.Compression.ZipArchiveMode]::Update)
try{foreach($name in $resources.Keys){if($null -ne $zip.GetEntry($name)){if($name -cne 'logback.xml'){throw 'ADMINISTRATIVE_RESOURCE_ALREADY_PRESENT'};$zip.GetEntry($name).Delete()};$entry=$zip.CreateEntry($name);$stream=$entry.Open();try{$stream.Write($resources[$name])}finally{$stream.Dispose()}}}finally{$zip.Dispose()}
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
$entries=[Collections.Generic.List[object]]::new()
try{
 if($zip.Entries.Count -ne $originalHashes.Count+$resources.Count-1){throw 'UNEXPECTED_JAR_ENTRY_DELTA'}
 foreach($entry in $zip.Entries){
  $stream=$entry.Open();try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}
  if($originalHashes.ContainsKey($entry.FullName) -and $originalHashes[$entry.FullName] -cne $hash -and $entry.FullName -cne 'logback.xml'){throw 'ORIGINAL_JAR_ENTRY_CHANGED'}
  if($entry.FullName -ceq 'logback.xml' -and $hash -cne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($resources['logback.xml'])).ToLowerInvariant()){throw 'LABORATORY_LOGGING_RESOURCE_CHANGED'}
  if($entry.FullName -match '(?i)(PhysicalHarness|RuntimeBootstrapTest|runtime-laboratory/|synthetic.json|Bloco54Process)'){throw 'TEST_MATERIAL_IN_OFFICIAL_JAR'}
  $entries.Add([pscustomobject]@{entry=$entry.FullName;sha256=$hash;originalSha256=$originalHashes[$entry.FullName];administrative=$resources.ContainsKey($entry.FullName)})
 }
}finally{$zip.Dispose()}
$entries | ConvertTo-Json | Set-Content (Join-Path $root ('target/bloco54/jar-entry-diff-'+$RevisionLabel+'.json')) -Encoding utf8NoBOM
$text=Get-Content (Join-Path $old 'runtime.properties') -Raw
$replacements=@{'dataexport.base-url'='http://127.0.0.1:1';'dataexport.max-response-bytes'='1048576';'dataexport.retry.max-attempts'='1';
 'dataexport.resilience.max-requests-per-cycle'='5';'dataexport.resilience.max-requests-per-workload'='5';
 'dataexport.resilience.step-timeout-seconds'='30';'dataexport.resilience.cycle-timeout-seconds'='60';'dataexport.resilience.max-repartitions'='0';
 'shadow.jdbc-url'='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=false'}
foreach($key in $replacements.Keys){$text=[regex]::Replace($text,('(?m)^'+[regex]::Escape($key)+'=.*$'),($key+'='+$replacements[$key]))}
[IO.File]::WriteAllText((Join-Path $bundle 'runtime.properties'),$text,$encoding)
$files=Get-ChildItem -LiteralPath $bundle -Recurse -File | ForEach-Object {[pscustomobject]@{path=[IO.Path]::GetRelativePath($bundle,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}}
$quality=@(Get-Content (Join-Path $root 'target/bloco54/quality-references.json') -Raw | ConvertFrom-Json)
if($RevisionLabel -ceq 'reviewed-bundle-v7'){
 $incremental=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/INCREMENTAL_POLICIES_01/quality-references.json') -Raw|ConvertFrom-Json
 if(@($incremental).Count -ne 2 -or @($incremental|Where-Object {$_.mode -cne 'INCREMENTAL' -or $_.fingerprint -cnotmatch '^[a-f0-9]{64}$'}).Count){throw 'EXACT_INCREMENTAL_QUALITY_PAIR_REQUIRED'}
 $quality+=@($incremental)
}
$manifest=[ordered]@{version=1;block=54;database='localhost/ETL_SISTEMA_V2_SHADOW';validUntil=$until;quality=$quality;files=@($files)}
[IO.File]::WriteAllText((Join-Path $bundle 'manifest.json'),($manifest|ConvertTo-Json -Depth 10),$encoding)
'REVIEWED_BUNDLE_MANIFEST_SHA256='+(Get-FileHash -LiteralPath (Join-Path $bundle 'manifest.json')).Hash.ToLowerInvariant()

#Requires -Version 7.5
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$FirstBuild,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$SecondBuild,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$FirstPackage,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$SecondPackage,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$utf8=[Text.UTF8Encoding]::new($false,$true)
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$output=Join-Path $round $Attempt
if(Test-Path -LiteralPath $output){throw 'QUAL_ARTIFACT_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($output)
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
function Read([string]$path){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($path)) 8388608}
function Hash([string]$path){(Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant()}
function Save([string]$name,$value){[IO.File]::WriteAllText((Join-Path $output $name),($value|ConvertTo-Json -Depth 20),$utf8)}
function Pin([string]$path){[ordered]@{path=[IO.Path]::GetRelativePath($root,$path).Replace('\','/');sha256=(Hash $path)}}
Save 'reservation.json' ([ordered]@{state='RESERVED';firstBuild=$FirstBuild;secondBuild=$SecondBuild;
    firstPackage=$FirstPackage;secondPackage=$SecondPackage;scope='readonly source, class/resource and actual ZIP bytes; one private source mutant';
    jdbc=$false;recovery='preserve outputs and failed mutant, never substitute old classes or normalized bytes'})
$firstRoot=Join-Path $round $FirstBuild;$secondRoot=Join-Path $round $SecondBuild
$first=Read (Join-Path $firstRoot 'result.json');$second=Read (Join-Path $secondRoot 'result.json')
if($first.phase -cne 'VerifyPhysical' -or $first.exit -ne 0 -or -not $first.rollbackConfirmed -or
    $first.timedOut -or -not $first.logUtf8Integrity -or $second.exit -ne 0 -or $second.timedOut -or
    -not $second.logUtf8Integrity -or $second.phase -cne 'Package'){throw 'QUAL_ARTIFACT_BUILDS'}
$a=Join-Path $round $FirstPackage;$b=Join-Path $round $SecondPackage
$packageA=Read (Join-Path $a 'result.json');$packageB=Read (Join-Path $b 'result.json')
if($packageA.candidate -or -not $packageB.candidate){throw 'QUAL_ARTIFACT_QUALIFIED_FIRST'}
foreach($key in @('revision','manifestSha256','archiveSha256','members','dependencies','sourceCount')){
    if($packageA[$key] -cne $packageB[$key]){throw 'QUAL_ARTIFACT_REPRODUCIBILITY'}
}
foreach($package in @($a,$b)){
    $null=Test-QualificationPackage -Directory (Join-Path $package 'payload') -ManifestSha256 $packageA.manifestSha256
    if((Hash (Join-Path $package 'qualification.zip')) -cne $packageA.archiveSha256){throw 'QUAL_ARTIFACT_ZIP_BYTES'}
}
$sources=Read (Join-Path $a 'qualified-source-inventory.json')
$other=Read (Join-Path $b 'qualified-source-inventory.json')
if(($sources|ConvertTo-Json -Depth 5 -Compress) -cne ($other|ConvertTo-Json -Depth 5 -Compress)){throw 'QUAL_ARTIFACT_SOURCE_SET'}
function CheckSource([string]$base,$entry){
    $path=Join-Path $base $entry.path
    if(-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Hash $path) -cne $entry.sha256){throw 'QUAL_ARTIFACT_SOURCE_DRIFT'}
}
foreach($entry in $sources){
    CheckSource $root $entry
    CheckSource (Join-Path $firstRoot 'build') $entry
    CheckSource (Join-Path $secondRoot 'build') $entry
}
$classes=[Collections.Generic.List[object]]::new()
$jarPath=Join-Path $a 'payload/etl-dataexport-v2.jar'
if((Hash $jarPath) -cne (Hash (Join-Path $firstRoot 'build/target/etl-dataexport-v2.jar')) -or
    (Hash $jarPath) -cne (Hash (Join-Path $secondRoot 'build/target/etl-dataexport-v2.jar'))){throw 'QUAL_ARTIFACT_JAR_BYTES'}
$archive=[IO.Compression.ZipFile]::OpenRead($jarPath)
try{
    $expected=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $classRoot=Join-Path $firstRoot 'build/target/classes'
    foreach($file in Get-ChildItem -LiteralPath $classRoot -File -Recurse){
        $name=[IO.Path]::GetRelativePath($classRoot,$file.FullName).Replace('\','/')
        $null=$expected.Add($name);$entry=$archive.GetEntry($name)
        if($null -eq $entry){throw 'QUAL_ARTIFACT_CLASS_MISSING'}
        $stream=$entry.Open()
        try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}finally{$stream.Dispose()}
        if($hash -cne (Hash $file.FullName) -or $hash -cne (Hash (Join-Path $secondRoot ('build/target/classes/'+$name)))){
            throw 'QUAL_ARTIFACT_CLASS_RESOURCE_DRIFT'
        }
        $classes.Add([ordered]@{path=$name;sha256=$hash;size=$entry.Length})
    }
    foreach($metadata in @('META-INF/MANIFEST.MF','META-INF/maven/br.com.esl.etl/etl-dataexport-v2/pom.xml','META-INF/maven/br.com.esl.etl/etl-dataexport-v2/pom.properties')){
        $null=$expected.Add($metadata)
    }
    $actual=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($entry in $archive.Entries){if(-not $entry.FullName.EndsWith('/')){if(-not $actual.Add($entry.FullName)){throw 'QUAL_ARTIFACT_DUPLICATE_CLASS'}}}
    if(-not $expected.SetEquals($actual)){throw 'QUAL_ARTIFACT_JAR_ENTRY_SET'}
}finally{$archive.Dispose()}
# An actual changed source copy must fail the same verifier; canonical sources stay untouched.
$selected=@($sources|Where-Object {$_.path.StartsWith('src/main/java/')})[0]
$mutantRoot=Join-Path $output 'source-after-build-mutant'
$mutant=Join-Path $mutantRoot $selected.path
$null=[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($mutant))
[IO.File]::Copy((Join-Path $root $selected.path),$mutant,$false)
[IO.File]::AppendAllText($mutant,"`n// Synthetic mutation after the build.`n",$utf8)
$refusal='ACCEPTED';try{CheckSource $mutantRoot $selected}catch{$refusal=$_.Exception.Message}
if($refusal -cne 'QUAL_ARTIFACT_SOURCE_DRIFT'){throw 'QUAL_ARTIFACT_SOURCE_MUTANT_ACCEPTED'}
Save 'source-after-build-mutant.json' ([ordered]@{passed=$true;expected=$selected.sha256;observed=(Hash $mutant);refusal=$refusal;path=$selected.path})
Save 'source-binding.json' ([ordered]@{passed=$true;sources=$sources.Count;entries=$classes.ToArray();java=17;
    qualifiedBuild=(Pin (Join-Path $firstRoot 'result.json'));sourceInventory=(Pin (Join-Path $a 'qualified-source-inventory.json'));
    packagedJar=(Pin $jarPath);sourceMutation=(Pin (Join-Path $output 'source-after-build-mutant.json'))})
Save 'reproducibility.json' ([ordered]@{passed=$true;byteIdentical=$true;normalization=$false;independentBuildDirectories=$true;
    firstBuild=(Pin (Join-Path $firstRoot 'result.json'));secondBuild=(Pin (Join-Path $secondRoot 'result.json'));
    firstArchive=(Pin (Join-Path $a 'qualification.zip'));secondArchive=(Pin (Join-Path $b 'qualification.zip'));
    revision=$packageA.revision;manifestSha256=$packageA.manifestSha256;dependencies=$packageA.dependencies;members=$packageA.members})
Save 'result.json' ([ordered]@{passed=$true;sourceCount=$sources.Count;classResourceEntries=$classes.Count;
    sourceBinding=(Pin (Join-Path $output 'source-binding.json'));reproducibility=(Pin (Join-Path $output 'reproducibility.json'))})
Get-Content -Raw -LiteralPath (Join-Path $output 'result.json')

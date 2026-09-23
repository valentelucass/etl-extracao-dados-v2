#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$BuildAttempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$OutputName,
    [switch]$Candidate,
    [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01',
    [string]$ArtifactInputs='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root ('target/'+$RoundName)
$attempt=Join-Path $round $BuildAttempt;$build=Join-Path $attempt 'build'
$output=Join-Path $round $OutputName
if(Test-Path -LiteralPath $output){throw 'QUAL_PACKAGE_OUTPUT_EXISTS'}
Assert-QualificationPath $build
$result=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $attempt 'result.json')))
if($result.exit -ne 0 -or $result.timedOut -or (-not $Candidate -and ($result.phase -cne 'VerifyPhysical' -or -not $result.rollbackConfirmed))){throw 'QUAL_PACKAGE_BUILD_NOT_QUALIFIED'}
$utf8=[Text.UTF8Encoding]::new($false)
$null=[IO.Directory]::CreateDirectory($output)
function Evidence([string]$name,$value){[IO.File]::WriteAllText((Join-Path $output $name),($value|ConvertTo-Json -Depth 20),$utf8)}
Evidence 'reservation.json' ([ordered]@{state='RESERVED';step='deterministic-package';build=$BuildAttempt;candidate=[bool]$Candidate;
    target=$output;limits='offline files only; no JDBC, installer or source endpoint';recovery='retain partial output; new attempt for retry'})
$payload=Join-Path $output 'payload';$null=[IO.Directory]::CreateDirectory($payload)
$members=[Collections.Generic.List[object]]::new()
function Add([string]$source,[string]$name,[string]$type,[string]$role){
    Assert-QualificationMemberName $name;Assert-QualificationPath $source
    $dest=Join-Path $payload $name
    $null=[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
    [IO.File]::Copy($source,$dest,$false)
    $members.Add([ordered]@{path=$name;size=(Get-Item -LiteralPath $dest).Length;
        sha256=(Get-FileHash -LiteralPath $dest).Hash.ToLowerInvariant();type=$type;role=$role})
}
function Json([string]$name,$value,[string]$role){
    $dest=Join-Path $payload $name;$null=[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
    [IO.File]::WriteAllText($dest,($value|ConvertTo-Json -Depth 24 -Compress)+"`n",$utf8)
    $members.Add([ordered]@{path=$name;size=(Get-Item -LiteralPath $dest).Length;
        sha256=(Get-FileHash -LiteralPath $dest).Hash.ToLowerInvariant();type='JSON';role=$role})
}
# Revision identifies actual source/test/schema/build inputs, never a dirty Git HEAD.
$sourceInventory=[Collections.Generic.List[object]]::new()
foreach($directory in @('src','database','config','.mvn')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $build $directory) -Recurse -File){
        $relative=[IO.Path]::GetRelativePath($build,$file.FullName).Replace('\','/')
        $hash=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        $sourceInventory.Add([ordered]@{path=$relative;sha256=$hash})
        if(-not $Candidate -and (Get-FileHash -LiteralPath (Join-Path $root $relative)).Hash.ToLowerInvariant() -cne $hash){throw 'QUAL_PACKAGE_SOURCE_DRIFT'}
    }
}
$packagingInputs=@('pom.xml','scripts/validation/New-QualificationPackage.ps1','scripts/validation/QualificationPackage.psm1','scripts/validation/Invoke-Qualification.ps1','docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.json','docs/catalogos/macrobloco-qualificacao-pacote/PACKAGE-README.md')
$packagingInputs+=@('docs/catalogos/campanhas-integrais/CONTRATO.md','docs/adr/0051-sequencias-integrais-declaradas-no-pacote.md')
$packagingInputs+=@(Get-ChildItem -LiteralPath (Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/third-party') -File|
    ForEach-Object {[IO.Path]::GetRelativePath($build,$_.FullName).Replace('\','/')})
foreach($relative in $packagingInputs){
    $file=Join-Path $build $relative;$hash=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
    $sourceInventory.Add([ordered]@{path=$relative;sha256=$hash})
    if(-not $Candidate -and (Get-FileHash -LiteralPath (Join-Path $root $relative)).Hash.ToLowerInvariant() -cne $hash){throw 'QUAL_PACKAGE_SOURCE_DRIFT'}
}
$inventory=@($sourceInventory|Sort-Object path -CaseSensitive)
$revision=Get-QualificationByteHash ($utf8.GetBytes(($inventory|ForEach-Object {$_.path+'|'+$_.sha256+"`n"}) -join ''))
Evidence 'qualified-source-inventory.json' $inventory
Add (Join-Path $build 'target/etl-dataexport-v2.jar') 'etl-dataexport-v2.jar' 'JAR' 'APPLICATION'
$lockPath=Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.json'
$lock=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($lockPath))
if($lock.version -cne 'qualification-dependencies-v1' -or $lock.dependencies.Count -ne 9){throw 'QUAL_DEPENDENCY_LOCK'}
$components=@(foreach($entry in $lock.dependencies){
    $folder=if($entry.type -ceq 'dll'){'native'}else{'lib'}
    $name=$folder+'/'+$entry.file;$source=Join-Path $build ('target/'+$name)
    if((Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant() -cne $entry.sha256 -or (Get-Item -LiteralPath $source).Length -ne $entry.size){throw 'QUAL_DEPENDENCY_DRIFT'}
    $role=if($folder -ceq 'native'){'NATIVE_AUTH'}else{'DEPENDENCY'}
    Add $source $name $entry.type.ToUpperInvariant() $role
    [ordered]@{type='library';'bom-ref'=$name;group=$entry.group;name=$entry.name;version=$entry.version;
        purl=('pkg:maven/'+$entry.group+'/'+$entry.name+'@'+$entry.version+'?type='+$entry.type);
        hashes=@([ordered]@{alg='SHA-256';content=$entry.sha256});licenses=@($entry.license);
        externalReferences=@([ordered]@{type='distribution';url=$entry.origin})}
})
if(@(Get-ChildItem -LiteralPath (Join-Path $build 'target/lib') -File).Count -ne 8){throw 'QUAL_DEPENDENCY_EXTRA'}
Add $lockPath 'dependencies.json' 'JSON' 'POLICY'
Add (Join-Path $build 'src/main/resources/qualification-laboratory/config.synthetic.json') 'config/config.synthetic.json' 'JSON' 'CONFIGURATION'
Add (Join-Path $build 'src/main/resources/analytic-laboratory/query-contracts.synthetic.json') 'contracts/query-contracts.synthetic.json' 'JSON' 'CONTRACT'
Add (Join-Path $build 'docs/catalogos/campanhas-integrais/CONTRATO.md') 'contracts/sequence-contract.md' 'TEXT' 'CONTRACT'
Add (Join-Path $build 'docs/adr/0051-sequencias-integrais-declaradas-no-pacote.md') 'contracts/sequence-adr.md' 'TEXT' 'CONTRACT'
Add (Join-Path $build 'src/main/resources/qualification-laboratory/outputs.synthetic.json') 'oracles/outputs.synthetic.json' 'JSON' 'ORACLE'
Add (Join-Path $build 'src/main/resources/qualification-laboratory/location-hashes.synthetic.json') 'oracles/location-hashes.synthetic.json' 'JSON' 'ORACLE'
Add (Join-Path $build 'src/main/resources/qualification-laboratory/location-representative-hashes.synthetic.json') 'oracles/location-representative-hashes.synthetic.json' 'JSON' 'ORACLE'
Add (Join-Path $build 'src/main/resources/qualification-laboratory/physical-columns.v098.json') 'contracts/physical-columns.v098.json' 'JSON' 'CONTRACT'
$fixtureEntries=@(foreach($folder in @('analytic-laboratory','expansion-laboratory')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $build ('src/main/resources/'+$folder)) -File|Sort-Object Name){
        $name='fixtures/'+$folder+'/'+$file.Name
        $type=if($file.Extension -ceq '.sql'){'SQL'}else{'JSON'}
        Add $file.FullName $name $type 'FIXTURE'
        [ordered]@{resource=$folder+'/'+$file.Name;member=$name;sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
    }
})
Json 'fixtures/fixture-index.json' ([ordered]@{version='qualification-fixtures-v1';origin='SYNTHETIC_INPUTS_NOT_QUERY_OUTPUTS';files=$fixtureEntries}) 'FIXTURE'
if($ArtifactInputs){
    Assert-QualificationPath $ArtifactInputs
    $artifactRoot=[IO.Path]::GetFullPath($ArtifactInputs)
    $indexFile=Join-Path $artifactRoot 'artifact-cases/index.json'
    # A declared sequence carries every pinned synthetic member.  Its two-case
    # index is larger than 64 KiB, while still far below the package-wide JSON
    # safety limit.  Keep a bounded, explicit limit rather than rejecting it.
    $index=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($indexFile)) 262144
    Assert-QualificationFields $index @('version','origin','cases')
    if($index.version -cnotin @('qualification-artifact-cases-v1','qualification-artifact-cases-v2') -or $index.origin -cne 'LOCAL_SYNTHETIC_ARTIFACT_V1' -or
        $index.cases.Count -lt 1 -or $index.cases.Count -gt 4){throw 'QUAL_ARTIFACT_INDEX'}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $null=$seen.Add('artifact-cases/index.json')
    foreach($case in $index.cases){
        if($index.version -ceq 'qualification-artifact-cases-v2'){
            Assert-QualificationFields $case @('id','kind','members')
            if($case.kind -cnotin @('SCENARIO','SEQUENCE')){throw 'QUAL_ARTIFACT_CASE_KIND'}
        }else{Assert-QualificationFields $case @('id','members')}
        if($case.id -cnotmatch '^[a-z][a-z0-9-]{0,39}$' -or $case.members.Count -lt 2 -or $case.members.Count -gt 340){throw 'QUAL_ARTIFACT_CASE'}
        foreach($member in $case.members){
            Assert-QualificationFields $member @('path','role')
            Assert-QualificationMemberName $member.path
            if(-not $member.path.StartsWith('artifact-cases/'+$case.id+'/',[StringComparison]::Ordinal) -or
                -not $member.path.EndsWith('.json',[StringComparison]::Ordinal) -or
                $member.role -cnotin @('FIXTURE','ORACLE') -or -not $seen.Add($member.path)){throw 'QUAL_ARTIFACT_MEMBER'}
            Add (Join-Path $artifactRoot $member.path) $member.path 'JSON' $member.role
        }
    }
    $actual=@(Get-ChildItem -LiteralPath (Join-Path $artifactRoot 'artifact-cases') -Recurse -File |
        ForEach-Object {[IO.Path]::GetRelativePath($artifactRoot,$_.FullName).Replace('\','/')})
    if(-not $seen.SetEquals([string[]]$actual)){throw 'QUAL_ARTIFACT_INPUT_SET'}
    Add $indexFile 'artifact-cases/index.json' 'JSON' 'FIXTURE'
}
$schemaEntries=@(foreach($file in Get-ChildItem -LiteralPath (Join-Path $build 'database/migrations') -File|Sort-Object Name){
    $name='schema/migrations/'+$file.Name;Add $file.FullName $name 'SQL' 'SCHEMA'
    [ordered]@{path=$name;sha256=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()}
})
if($schemaEntries.Count -ne 104){throw 'QUAL_SCHEMA_COUNT'}
Add (Join-Path $build 'database/baseline/001_schema_foundation_baseline.sql') 'schema/baseline/001_schema_foundation_baseline.sql' 'SQL' 'SCHEMA'
Json 'schema/schema-index.json' ([ordered]@{version='qualification-schema-v1';lastVersion=104;installationAutomatic=$false;migrations=$schemaEntries;baselineSha256=(Get-FileHash -LiteralPath (Join-Path $build 'database/baseline/001_schema_foundation_baseline.sql')).Hash.ToLowerInvariant()}) 'SCHEMA'
# All referenced hashes already exist; the campaign does not refer to its containing manifest.
function MemberHash([string]$name){$found=@($members|Where-Object path -CEQ $name);if($found.Count -ne 1){throw 'QUAL_EXAMPLE_MEMBER'};return $found[0].sha256}
Json 'config/campaign.synthetic.json' ([ordered]@{version='qualification-campaign-v1';id='package-smoke';pins=[ordered]@{
    revision=$revision;jar=(MemberHash 'etl-dataexport-v2.jar');schema=(MemberHash 'schema/schema-index.json');
    contracts=(MemberHash 'contracts/query-contracts.synthetic.json');fixture=(MemberHash 'fixtures/fixture-index.json');
    oracle=(MemberHash 'oracles/outputs.synthetic.json')};roots=2;pageSize=2;maximumSeconds=420;cases=@([ordered]@{
        id='smoke';wave='all';dependsOn=@();action='SCENARIO';mode='BOOTSTRAP';fault='NONE';barrier='NONE';
        outputs=@(1..19|ForEach-Object {'SQL-{0:00}' -f $_});tick='2036-04-02T12:00:00Z';
        start='2036-04-01';endExclusive='2036-04-02';zone='America/Sao_Paulo';lookbackSeconds=0;
        deadlineSeconds=86400;maximumCatchUp=1;blackouts=@();expected='PASS_LOCAL'})}) 'CONFIGURATION'
if($ArtifactInputs){
    foreach($artifactCase in $index.cases){
        $sequenceCase=$index.version -ceq 'qualification-artifact-cases-v2' -and $artifactCase.kind -ceq 'SEQUENCE'
        $entryName=if($sequenceCase){'sequence.json'}else{'input.json'}
        $inputFile=Join-Path $artifactRoot ('artifact-cases/'+$artifactCase.id+'/'+$entryName)
        $input=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($inputFile)) 65536
        $campaign=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $payload 'config/campaign.synthetic.json')))
        $campaign.id=$artifactCase.id;$campaign.roots=$input.roots;$campaign.pageSize=$input.pageSize
        $campaign.cases[0].id=$artifactCase.id;$campaign.cases[0].action=if($sequenceCase){'SEQUENCE'}else{'ARTIFACT'}
        if($sequenceCase){$campaign.maximumSeconds=3600}
        $campaign.cases[0].start=$input.windowStart;$campaign.cases[0].endExclusive=$input.windowEndExclusive
        $campaign.cases[0].tick=$input.windowEndExclusive+'T12:00:00Z'
        # Three declared civil partitions use the existing planner's three-window cap.
        # This is a logical synthetic deadline; wall-clock/heap/JDBC budgets are unchanged.
        $campaign.cases[0].maximumCatchUp=3;$campaign.cases[0].deadlineSeconds=259200
        Json ('config/campaign.'+$artifactCase.id+'.json') $campaign 'CONFIGURATION'
    }
}
Add (Join-Path $build 'scripts/validation/Invoke-Qualification.ps1') 'Invoke-Qualification.ps1' 'POWERSHELL' 'LAUNCHER'
Add (Join-Path $build 'scripts/validation/QualificationPackage.psm1') 'QualificationPackage.psm1' 'POWERSHELL' 'LAUNCHER'
Add (Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/PACKAGE-README.md') 'README.md' 'TEXT' 'DOCUMENTATION'
foreach($file in Get-ChildItem -LiteralPath (Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/third-party') -File|Sort-Object Name){
    $type=switch($file.Extension){'.json'{'JSON'};'.pom'{'XML'};default{'TEXT'}}
    Add $file.FullName ('licenses/'+$file.Name) $type 'LICENSE'
}
Json 'sbom.cdx.json' ([ordered]@{'$schema'='http://cyclonedx.org/schema/bom-1.6.schema.json';bomFormat='CycloneDX';specVersion='1.6';version=1;
    metadata=[ordered]@{component=[ordered]@{type='application';name='etl-dataexport-v2-local-laboratory';version='0.1.0-SNAPSHOT';hashes=@([ordered]@{alg='SHA-256';content=@($members|Where-Object role -CEQ 'APPLICATION')[0].sha256})}};
    components=@($components|Sort-Object 'bom-ref' -CaseSensitive)}) 'SBOM'
Json 'provenance.json' ([ordered]@{version='qualification-provenance-v1';revision=$revision;sourceKind='LOCAL_BYTE_SNAPSHOT';outputTimestamp='2026-09-13T00:00:00Z';java=17;maven='3.9.14';offline=$true;vulnerabilityFeed='NOT_EXECUTED';signed=$false;publishedCi=$false;operationalApproval=$false;dependencyLockSha256=(Get-FileHash -LiteralPath $lockPath).Hash.ToLowerInvariant()}) 'PROVENANCE'
$null=Test-QualificationSbom -Bom (Join-Path $payload 'sbom.cdx.json') -SchemaDirectory (Join-Path $payload 'licenses')
$manifest=[ordered]@{version='qualification-package-v1';revision=$revision;java=17;os='Windows';architecture='x64';schemaVersion=104;files=@($members|Sort-Object path -CaseSensitive)}
$manifestBytes=$utf8.GetBytes(($manifest|ConvertTo-Json -Depth 10 -Compress)+"`n")
$manifestHash=Get-QualificationByteHash $manifestBytes
[IO.File]::WriteAllBytes((Join-Path $payload 'package.json'),$manifestBytes)
[IO.File]::WriteAllText((Join-Path $payload 'package.sha256'),$manifestHash+"`n",$utf8)
$null=Test-QualificationPackage -Directory $payload -ManifestSha256 $manifestHash
# ZIP entry ordering, timestamps, compression and attributes are independent of source metadata.
$archive=Join-Path $output 'qualification.zip';$stream=[IO.File]::Open($archive,[IO.FileMode]::CreateNew)
$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$false)
try{
    foreach($name in (@(@($manifest.files.path)+@('package.json','package.sha256'))|Sort-Object -CaseSensitive)){
        $entry=$zip.CreateEntry($name,[IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime=[DateTimeOffset]::Parse('2026-09-13T00:00:00+00:00');$entry.ExternalAttributes=0
        $input=[IO.File]::OpenRead((Join-Path $payload $name));$destination=$entry.Open()
        try{$input.CopyTo($destination)}finally{$input.Dispose();$destination.Dispose()}
    }
}finally{$zip.Dispose();$stream.Dispose()}
Evidence 'result.json' ([ordered]@{state='PACKAGED_NOT_SMOKE_QUALIFIED';candidate=[bool]$Candidate;revision=$revision;manifestSha256=$manifestHash;
    archiveSha256=(Get-FileHash -LiteralPath $archive).Hash.ToLowerInvariant();members=$manifest.files.Count;dependencies=9;sourceCount=$inventory.Count})
Get-Content -LiteralPath (Join-Path $output 'result.json') -Raw

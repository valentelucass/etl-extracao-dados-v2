param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Code/control metadata only. No Java/Maven/Python/Node/Source/CIM/credentials or business input is opened.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$destination=[IO.Path]::GetFullPath($OutputDirectory)
if(-not[IO.Path]::IsPathFullyQualified($OutputDirectory)-or(Test-Path -LiteralPath $destination)){throw 'FRESH_ABSOLUTE_UNIT_DIRECTORY_REQUIRED'}
function Pin-Code([string]$relative){
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'UNIT_PATH_SCOPE'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'UNIT_LINK_REFUSED'}}
 $item=Get-Item -LiteralPath $path
 return [ordered]@{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
function Fresh-Control([string]$name,$value){
 $path=Join-Path $destination $name
 $bytes=[Text.UTF8Encoding]::new($false).GetBytes((($value|ConvertTo-Json -Depth 24)-replace([string][char]13+[char]10),[string][char]10)+[char]10)
 $stream=[IO.File]::Open($path,'CreateNew','Write','Read')
 try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
 return [ordered]@{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$owned=[Collections.Generic.List[string]]::new()
foreach($relative in @(
 'src/main/java/br/com/esl/etl/v2/bootstrap/Main.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/OperationalExportProfileCommand.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/GovernedExportDelivery.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/CapturedDashboardStageDelivery.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/OperationalProfileMainSource0CandidateTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/OperationalProfileMainPositiveSource0CandidateTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/HistoricalContentRouteIntegrationTest.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/HistoricalContentMainTest.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/IndependentExportCommand.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeIndependentExportCommand.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeIndependentExportExecution.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeCompositionRoot.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/orquestracao/IndependentExportPlanBinding.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportQueryEncoder.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/IndependentExportCommandTest.java'
)){$owned.Add($relative)}
foreach($part in @('src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao','src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao')){
 foreach($file in [IO.Directory]::EnumerateFiles((Join-Path $repo $part),'*.java')){$owned.Add($part+'/'+[IO.Path]::GetFileName($file))}
}
$ownedJava=[string[]]$owned.ToArray();[Array]::Sort($ownedJava,[StringComparer]::Ordinal)
$controlFiles=@(
 'pom.xml',
 'config/checkstyle.xml',
 'config/independent-export-operational.example.properties',
 'src/main/resources/contracts/dataexport/fretes-independent-export-columns-v1.json',
 'src/main/resources/contracts/dataexport/coletas-common31-v1.json',
 'src/main/resources/independent-export/OwnedCurlJob.cs',
 'src/main/resources/independent-export/RootNativeCensus.ps1',
 'src/main/resources/independent-export/RootPrivacyGuard.ps1',
 'src/main/resources/independent-export/RootWireWorker.ps1',
 'scripts/validation/Test-IndependentExportSelection.ps1',
 'scripts/validation/Prepare-IndependentExportFinalUnit.ps1',
 'docs/validacao/independent-export-permanent-v3.handoff.json'
)
$testClasses=@();$methods=0
foreach($relative in $ownedJava){
 if(-not$relative.StartsWith('src/test/')){continue}
 $source=[IO.File]::ReadAllText((Join-Path $repo $relative))
 if($source-match'@(ParameterizedTest|RepeatedTest|TestFactory)\b'){throw 'ORDINARY_JUNIT_ONLY'}
 $matches=[regex]::Matches($source,'(?m)^\s*@Test\s*(?:public\s+)?void\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)')
 $annotations=[regex]::Matches($source,'(?m)^\s*@Test\b').Count
 if($matches.Count-ne$annotations){throw 'EXPLICIT_ORDINARY_TEST_METHOD_LEDGER_REQUIRED'}
 if($matches.Count-eq0){continue}
 $className=$relative.Substring('src/test/java/'.Length).Replace('/','.').Substring(0,$relative.Length-'src/test/java/'.Length-'.java'.Length)
 $entries=@();$unique=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($match in $matches){
  $name=$match.Groups['name'].Value
  if(-not$unique.Add($name)){throw 'DUPLICATE_JUNIT_NAME'}
  $entries+=@([ordered]@{name=$name;junitNames=@($name)});$methods++
 }
 $testClasses+=@([ordered]@{className=$className;sourceFile=$relative;methods=$entries})
}
$inputs=@();foreach($relative in @($ownedJava)+@($controlFiles)){$inputs+=@(Pin-Code $relative)}
# Complete compiler universe is a separate control snapshot; inputPins keeps the exact V5 owned/control order.
$inventory=[Collections.Generic.List[string]]::new();$total=0L
foreach($part in @('src/main/java','src/test/java','src/main/resources','src/test/resources','config')){
 $base=Join-Path $repo $part
 if(-not[IO.Directory]::Exists($base)){continue}
 foreach($path in [IO.Directory]::EnumerateFiles($base,'*',[IO.SearchOption]::AllDirectories)){
  $extension=[IO.Path]::GetExtension($path).ToLowerInvariant()
  if($extension-in@('.jar','.xlsx','.xls','.zip','.parquet','.bin','.db','.jsonl','.raw')){throw 'ROOT_ONLY_INPUT_IN_COMPILATION_UNIVERSE'}
  $total+=([IO.FileInfo]::new($path)).Length
  if($total-gt67108864-or$inventory.Count-ge8192){throw 'COMPILE_INVENTORY_BOUND'}
  $inventory.Add($path)
 }
}
$sorted=[string[]]$inventory.ToArray();[Array]::Sort($sorted,[StringComparer]::Ordinal)
$fullPins=@();foreach($path in $sorted){
 $relative=$path.Substring($repo.Length+1).Replace('\','/')
 $pin=Pin-Code $relative
 $fullPins+=@([ordered]@{path=$pin.path;bytes=$pin.bytes;sha256=$pin.sha256})
}
[void][IO.Directory]::CreateDirectory($destination)
$snapshotPin=Fresh-Control 'compile-inventory.PREPARED.json' ([ordered]@{
 schema='ROOT_INDEPENDENT_EXPORT_COMPILATION_SNAPSHOT0721_V5';repository=$repo;pins=$fullPins;
 preparedOnly=$true;businessFiles=0;javaCompiled=$false;testsExecuted=0
})
$counts=[ordered]@{ownedJava=$ownedJava.Count;controls=$controlFiles.Count;inputs=$inputs.Count;classes=$testClasses.Count;methods=$methods;junitCases=$methods}
$selection='-DspotlessFiles='+($ownedJava-join',')
$classes=@($testClasses|ForEach-Object{$_.className})
$unit=[ordered]@{
 schema='INDEPENDENT_EXPORT_ROOT_OFFLINE_UNIT_V1';revision='PERMANENT_V3_FOR_ROOT_HARNESS_V5';preparedAt=[DateTimeOffset]::UtcNow.ToString('o');
 status='PREPARED_NOT_EXECUTED_NOT_APPROVED';repository=$repo;requiredExecutor='Root Original with existing physical admission';authorityCreated=$false;
 Source=0;HTTP=0;SQL=0;credentials=0;CIM=0;serviceChanges=0;canonicalControlChanges=0;
 ownedJava=$ownedJava;controlFiles=$controlFiles;testClasses=$testClasses;counts=$counts;
 syntheticJUnitMethodsDeclared=$methods;inputPins=$inputs;fullCompilationSnapshotPin=$snapshotPin;
 fullCompilationInventoryCount=$fullPins.Count;fullCompilationInventoryBytes=$total;
 limits=[ordered]@{originalJobBytes=1073741824;originalProcessBytes=1073741824;formatDeadlineSeconds=60;offlineTestDeadlineSeconds=300;
 javaHeapMaximum='512m';testForks=0;externalRequests=0;businessFiles=0;recaptures=0;automaticRepeatsOfFailedUnit=0};
 requiredEnvironment=@{MAVEN_OPTS='-Xmx512m -Dfile.encoding=UTF-8'};
 formatProcess=[ordered]@{executable='mvn';arguments=@('-o',$selection,'spotless:apply')};
 testProcess=[ordered]@{executable='mvn';arguments=@('-o','-DforkCount=0',('-Dtest='+($classes-join',')),$selection,'test')};
 requiredSequence=@(
 'Root reviews and admits FORMAT once using its existing V5 harness; no authority is supplied by this unit.',
 'Formatting changes bytes. Run this metadata preparer again in a fresh output directory and review the new unit before TEST.',
 'Root TEST uses JDK17/Maven3.9.6 offline, an isolated POM and absent fresh build directory. No prior class may satisfy compilation.',
 'Require exact ordinary executed case names, zero failures/errors/skips, unchanged complete compilation inventory and closed own process/stdin/pipes receipts.',
 'Run the original129 Python oracle and this real Java port in separately admitted Source0 Originals; the ordinary suite is not original129 qualification.',
 'Replay one independently approved real pair per Original with fullValueProjection and original precision axes. No automatic loop across pairs.',
 'Root Native childJob compilation/physical boundary and pure selection script require separate real receipts. Ordinary JUnit does not execute the Source worker.',
 'Do not run Source, UI, credentials, SQL, CIM, canonical/control updates, installations or production publication as part of FORMAT/TEST.'
 );
 differential=@{runner='br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.Original129Differential';adapter='br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.Original129JavaPort';
 expectedGuards=129;status='NOT_EXECUTED';expectedResultsPassedToAdapter=$false};
 currentVerification=@{compilation='NOT_EXECUTED';formatting='NOT_EXECUTED';tests='NOT_EXECUTED';realPairJavaReplay='NOT_EXECUTED';physicalRuntime='NOT_EXECUTED';
 mainSourceAdoption=$false;dashboardRoundtrip=$false}
}
$unitPin=Fresh-Control 'offline-unit.PREPARED.json' $unit
$manifest=[ordered]@{
 schema='ROOT_INDEPENDENT_EXPORT_FINAL_UNIT_MANIFEST0721_V5';approved=$false;dispatchable=$false;
 ownerRootUUID='01a17ee4-5f83-7051-9cec-ffc8e16642fe';repository=$repo;sourceUnit=$unitPin;
 ownedJava=$ownedJava;controlFiles=$controlFiles;testClasses=$testClasses;counts=$counts;
 note='Candidate exact signature for Root review. Root must issue its own approved manifest; this file grants no execution authority.'
}
$manifestPin=Fresh-Control 'root-manifest.CANDIDATE.json' $manifest
[ordered]@{unit=$unitPin;manifestCandidate=$manifestPin;compilationSnapshot=$snapshotPin;counts=$counts;testsExecuted=0;Source=0;HTTP=0;SQL=0;CIM=0}|ConvertTo-Json -Depth 8

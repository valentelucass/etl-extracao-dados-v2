param([Parameter(Mandatory)][string]$OutputDirectory,[string[]]$AdditionalControlFiles=@())
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Install this candidate as scripts/validation/Prepare-SinistrosOfflineUnit.ps1 before use.
# Metadata only: code/control bytes, no business data, JVM, classes/JARs or Source.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-InventarioOfflineUnit.ps1') -OutputDirectory $OutputDirectory -AdditionalControlFiles @('src/main/resources/contracts/dataexport/inventario-native26-v1.json'))|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
$controls=@('src/main/resources/contracts/dataexport/expansion-6392-observed-wire-20261025-v1.json','scripts/validation/Test-SinistrosWireScope.ps1','scripts/validation/Prepare-SinistrosOfflineUnit.ps1','src/main/resources/contracts/reconciliacao/sinistros-native44-candidate-DRAFT03.json','src/main/resources/contracts/reconciliacao/sinistros-native44-observed-20261107-v1.json','database/migrations/V106__widen_sinistro_commentary_typed_storage.sql','database/validation/104_validate_sinistro_commentary_typed_storage.sql')+@($AdditionalControlFiles)
foreach($relative in $controls){
 if($relative-isnot[string]-or$relative-cmatch'\\|(^|/)\.\.(/|$)'-or[IO.Path]::IsPathFullyQualified($relative)){throw 'SINISTROS_RELATIVE_CONTROL_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'SINISTROS_CONTROL_SCOPE'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'SINISTROS_CONTROL_LINK'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144-or$unit.controlFiles-ccontains$relative){throw 'SINISTROS_EXACT_CONTROL'}
 $unit.controlFiles+=@($relative);$unit.inputPins+=@(@{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()})
}
foreach($class in @('SinistrosIndependentRequestTest','SinistrosIntervalCaptureTest','SinistrosSourceCaptureMainTest','SinistrosTransportTest')){
 if(@($unit.testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$class)).Count-ne1){throw 'SINISTROS_FOUR_CURRENT_TEST_CLASSES_REQUIRED'}
}
# Sinistros successor: complete predecessor unit plus explicit existing module ownership.
$additionalOwned=@(
 'src/main/java/br/com/esl/etl/v2/modulos/sinistros/aplicacao/SinistroDataExportMapper.java',
 'src/main/java/br/com/esl/etl/v2/modulos/sinistros/aplicacao/SinistroDataExportDto.java',
 'src/main/java/br/com/esl/etl/v2/modulos/sinistros/aplicacao/ExtrairSinistroDataExport.java',
 'src/main/java/br/com/esl/etl/v2/modulos/sinistros/domain/SinistroObservation.java',
 'src/main/java/br/com/esl/etl/v2/modulos/sinistros/domain/SinistroRules.java'
)
function Pin-SinistrosOwned([string]$relative){
 if($relative-cnotmatch'^src/(main|test)/java/br/com/esl/etl/v2/[A-Za-z0-9_/]+\.java$'){throw 'SINISTROS_OWNED_JAVA_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'SINISTROS_OWNED_JAVA_ROOT'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'SINISTROS_OWNED_JAVA_LINK'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144){throw 'SINISTROS_OWNED_JAVA_BOUND'}
 return @{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$ownedSet=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($relative in $unit.ownedJava){if(-not$ownedSet.Add($relative)){throw 'SINISTROS_PREDECESSOR_DUPLICATE_JAVA'}}
foreach($relative in $additionalOwned){[void](Pin-SinistrosOwned $relative);[void]$ownedSet.Add($relative)}
$sortedOwned=[string[]]@($ownedSet);[Array]::Sort($sortedOwned,[StringComparer]::Ordinal)
$unit.ownedJava=$sortedOwned
$testClasses=@();$methodCount=0
foreach($relative in $sortedOwned){
 if(-not$relative.StartsWith('src/test/')){continue}
 $source=[IO.File]::ReadAllText((Join-Path $repo $relative))
 if($source-match'@(ParameterizedTest|RepeatedTest|TestFactory)\b'){throw 'SINISTROS_ORDINARY_JUNIT_ONLY'}
 $matches=[regex]::Matches($source,'(?m)^\s*@Test\s*(?:public\s+)?void\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)')
 if($matches.Count-ne[regex]::Matches($source,'(?m)^\s*@Test\b').Count){throw 'SINISTROS_EXACT_ORDINARY_METHOD_LEDGER'}
 if($matches.Count-eq0){continue}
 $className=$relative.Substring('src/test/java/'.Length,$relative.Length-'src/test/java/'.Length-'.java'.Length).Replace('/','.')
 $methods=@();$methodNames=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($match in $matches){$name=$match.Groups['name'].Value;if(-not$methodNames.Add($name)){throw 'SINISTROS_DUPLICATE_JUNIT_METHOD'};$methods+=@(@{name=$name;junitNames=@($name)});$methodCount++}
 $testClasses+=@(@{className=$className;sourceFile=$relative;methods=$methods})
}
$unit.testClasses=$testClasses;$unit.syntheticJUnitMethodsDeclared=$methodCount
$unit.counts.ownedJava=$sortedOwned.Count;$unit.counts.classes=$testClasses.Count;$unit.counts.methods=$methodCount;$unit.counts.junitCases=$methodCount
$selection='-DspotlessFiles='+($sortedOwned-join',')
$unit.formatProcess.arguments=@('-o',$selection,'spotless:apply')
$unit.testProcess.arguments=@('-o','-DforkCount=0',('-Dtest='+(($testClasses|ForEach-Object className)-join',')),$selection,'test')
# Rebuild all input pins in the required owned-Java then control order, without changing the complete compiler snapshot.
$unit.inputPins=@($sortedOwned|ForEach-Object{Pin-SinistrosOwned $_})+@($unit.inputPins|Where-Object{$unit.controlFiles-ccontains$_.file})
foreach($required in @('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosIndependentRequestTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosIntervalCaptureTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosSourceCaptureMainTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosTransportTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosRawMapperQualifierTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosMapperQualificationMainTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.QualifiedSinistrosModel44Test','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosContentAdapterTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.SinistrosDiagnosticMainTest')){
 if(@($unit.testClasses|Where-Object className -ceq $required).Count-ne1){throw 'SINISTROS_CONSUMER_COMPLETE_REVIEWED_TEST_SCOPE'}
}
$unit.counts.controls=$unit.controlFiles.Count;$unit.counts.inputs=$unit.inputPins.Count
$unit.revision='SINISTROS_6392_SOURCE_CONTENT44_AND_SHARED_REGRESSION_V1'
$unit.scopeNote='Exact current Sinistros Source/mapper/Native44 code and tests plus all predecessor tests; counts derived from code. Source0/HTTP0/SQL0, no entity acceptance or production authority.'
function Publish-SinistrosMetadata([string]$name,$value){
 $path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $stream=[IO.File]::Open($path,'CreateNew','Write','Read');try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
 return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish-SinistrosMetadata 'sinistros-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.ownedJava=$unit.ownedJava;$manifest.testClasses=$unit.testClasses;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts
$manifest.note='Candidate metadata only; exact current Sinistros44 and all predecessor regression inputs. Root must admit current build; no Source/production authority.'
$manifestPin=Publish-SinistrosMetadata 'sinistros-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0;testsExecuted=0}|ConvertTo-Json -Depth 8

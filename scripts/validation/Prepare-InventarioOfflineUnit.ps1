param([Parameter(Mandatory)][string]$OutputDirectory,[string[]]$AdditionalControlFiles=@())
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Install this candidate as scripts/validation/Prepare-InventarioOfflineUnit.ps1 before use.
# Metadata only: code/control bytes, no business data, JVM, classes/JARs or Source.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-CotacoesOfflineUnit.ps1') -OutputDirectory $OutputDirectory)|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
$controls=@('src/main/resources/contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json','scripts/validation/Test-InventarioWireScope.ps1','scripts/validation/Prepare-InventarioOfflineUnit.ps1')+@($AdditionalControlFiles)
foreach($relative in $controls){
 if($relative-isnot[string]-or$relative-cmatch'\\|(^|/)\.\.(/|$)'-or[IO.Path]::IsPathFullyQualified($relative)){throw 'INVENTARIO_RELATIVE_CONTROL_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'INVENTARIO_CONTROL_SCOPE'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'INVENTARIO_CONTROL_LINK'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144-or$unit.controlFiles-ccontains$relative){throw 'INVENTARIO_EXACT_CONTROL'}
 $unit.controlFiles+=@($relative);$unit.inputPins+=@(@{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()})
}
foreach($class in @('InventarioIndependentRequestTest','InventarioIntervalCaptureTest','InventarioSourceCaptureMainTest','InventarioTransportTest')){
 if(@($unit.testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$class)).Count-ne1){throw 'INVENTARIO_FOUR_CURRENT_TEST_CLASSES_REQUIRED'}
}
# Consumer successor only: explicit mapper05 ownership and narrowly scoped legacy projector regression.
$additionalOwned=@(
 'src/main/java/br/com/esl/etl/v2/bootstrap/DevLocalEngineObservations.java',
 'src/main/java/br/com/esl/etl/v2/modulos/inventario/aplicacao/InventarioDataExportMapper.java',
 'src/main/java/br/com/esl/etl/v2/modulos/inventario/aplicacao/InventarioInvoicesFieldParser.java',
 'src/main/java/br/com/esl/etl/v2/modulos/inventario/domain/InventarioInvoiceNumbers.java',
 'src/main/java/br/com/esl/etl/v2/modulos/inventario/domain/InventarioObservation.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionStrings.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/OrderedExpansionStrings.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionProjectedField.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionProjectionJson.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/expansao/JdbcExpansionStaging.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/InventarioInvoiceNumbersRoundTripTest.java',
 'src/test/java/br/com/esl/etl/v2/modulos/inventario/aplicacao/InventarioInvoicesFieldParserTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/InventarioInvoiceNumbersProjectionTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/persistencia/expansao/InventarioInvoiceNumbersJdbcEncodingTest.java',
 'src/main/java/br/com/esl/etl/v2/modulos/inventario/aplicacao/InventarioDataExportDto.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ExpansionFieldParser.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionValue.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionPageProjectorTest.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionCoreFixtures.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionSchemaFixtures.java'
)
function Pin-InventarioOwned([string]$relative){
 if($relative-cnotmatch'^src/(main|test)/java/br/com/esl/etl/v2/[A-Za-z0-9_/]+\.java$'){throw 'INVENTARIO_OWNED_JAVA_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'INVENTARIO_OWNED_JAVA_ROOT'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'INVENTARIO_OWNED_JAVA_LINK'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144){throw 'INVENTARIO_OWNED_JAVA_BOUND'}
 return @{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$ownedSet=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($relative in $unit.ownedJava){if(-not$ownedSet.Add($relative)){throw 'INVENTARIO_PREDECESSOR_DUPLICATE_JAVA'}}
foreach($relative in $additionalOwned){[void](Pin-InventarioOwned $relative);[void]$ownedSet.Add($relative)}
$sortedOwned=[string[]]@($ownedSet);[Array]::Sort($sortedOwned,[StringComparer]::Ordinal)
$unit.ownedJava=$sortedOwned
$testClasses=@();$methodCount=0
foreach($relative in $sortedOwned){
 if(-not$relative.StartsWith('src/test/')){continue}
 $source=[IO.File]::ReadAllText((Join-Path $repo $relative))
 if($source-match'@(ParameterizedTest|RepeatedTest|TestFactory)\b'){throw 'INVENTARIO_ORDINARY_JUNIT_ONLY'}
 $matches=[regex]::Matches($source,'(?m)^\s*@Test\s*(?:public\s+)?void\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)')
 if($matches.Count-ne[regex]::Matches($source,'(?m)^\s*@Test\b').Count){throw 'INVENTARIO_EXACT_ORDINARY_METHOD_LEDGER'}
 if($matches.Count-eq0){continue}
 $className=$relative.Substring('src/test/java/'.Length,$relative.Length-'src/test/java/'.Length-'.java'.Length).Replace('/','.')
 $methods=@();$methodNames=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($match in $matches){$name=$match.Groups['name'].Value;if(-not$methodNames.Add($name)){throw 'INVENTARIO_DUPLICATE_JUNIT_METHOD'};$methods+=@(@{name=$name;junitNames=@($name)});$methodCount++}
 $testClasses+=@(@{className=$className;sourceFile=$relative;methods=$methods})
}
$unit.testClasses=$testClasses;$unit.syntheticJUnitMethodsDeclared=$methodCount
$unit.counts.ownedJava=$sortedOwned.Count;$unit.counts.classes=$testClasses.Count;$unit.counts.methods=$methodCount;$unit.counts.junitCases=$methodCount
$selection='-DspotlessFiles='+($sortedOwned-join',')
$unit.formatProcess.arguments=@('-o',$selection,'spotless:apply')
$unit.testProcess.arguments=@('-o','-DforkCount=0',('-Dtest='+(($testClasses|ForEach-Object className)-join',')),$selection,'test')
# Rebuild all input pins in the required owned-Java then control order, without changing the complete compiler snapshot.
$unit.inputPins=@($sortedOwned|ForEach-Object{Pin-InventarioOwned $_})+@($unit.inputPins|Where-Object{$unit.controlFiles-ccontains$_.file})
foreach($required in @('br.com.esl.etl.v2.bootstrap.InventarioInvoiceNumbersRoundTripTest','br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioInvoicesFieldParserTest','br.com.esl.etl.v2.plataforma.fonte.dataexport.InventarioInvoiceNumbersProjectionTest','br.com.esl.etl.v2.plataforma.persistencia.expansao.InventarioInvoiceNumbersJdbcEncodingTest','br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionPageProjectorTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.InventarioContentAdapterTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.InventarioDiagnosticMainTest','br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.InventarioMapperQualificationMainTest')){
 if(@($unit.testClasses|Where-Object className -ceq $required).Count-ne1){throw 'INVENTARIO_CONSUMER_COMPLETE_REVIEWED_TEST_SCOPE'}
}
$unit.counts.controls=$unit.controlFiles.Count;$unit.counts.inputs=$unit.inputPins.Count
$unit.revision='INVENTARIO_10633_CONSUMER_MAPPER05_AND_SHARED_REGRESSION_V2'
$unit.scopeNote='Exact currently installed Inventário source/tests plus current shared reconciliation regression suite; all class/method counts derived from code. Earlier579 Cotações tests alone do not qualify Inventário. No Source/HTTP/SQL, entity acceptance or production authority.'
function Publish-InventarioMetadata([string]$name,$value){
 $path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $stream=[IO.File]::Open($path,'CreateNew','Write','Read');try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
 return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish-InventarioMetadata 'inventario-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.ownedJava=$unit.ownedJava;$manifest.testClasses=$unit.testClasses;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts
$manifest.note='Candidate Consumer metadata only; includes mapper05/shared ordered strings and narrow existing projector regression. Root must admit current build; no Source/production authority.'
$manifestPin=Publish-InventarioMetadata 'inventario-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0;testsExecuted=0}|ConvertTo-Json -Depth 8

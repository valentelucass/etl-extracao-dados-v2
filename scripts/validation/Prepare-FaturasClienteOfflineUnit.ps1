param([Parameter(Mandatory)][string]$OutputDirectory,[string[]]$AdditionalControlFiles=@(),[string[]]$AdditionalOwnedJava=@(),
 [string]$PredecessorTestBindingPath='C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\work\independent-export-faturas0753-build12-test-union\root-executed-binding.json',
 [string]$PredecessorTestBindingSha256='9A4C45364738B46F6C2318737BF771B4FF91FCA2150FB26503AA013D60B153C5')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$baselinePath='C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\independent-export-formatted-localizador0752-build150-compile\offline-unit.PREPARED.json'
if((Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash-cne'5E760E9265AD1DD424B01904585FE74F6C455C0954E95A2C8A7F29BC376A7ACB'){throw 'FAT_CURRENT_FULL150_UNIT_PIN'}
$baseline=Get-Content -LiteralPath $baselinePath -Raw|ConvertFrom-Json -AsHashtable
# DRAFT: Root must bind the actual closed Full12 predecessor, never a failed/partial cohort.
if($PredecessorTestBindingSha256-cnotmatch'^[A-Fa-f0-9]{64}$'){throw 'FAT_P2_CLOSED_FULL12_TEST_PIN_REQUIRED'}
if((Get-FileHash -LiteralPath $PredecessorTestBindingPath -Algorithm SHA256).Hash-cne$PredecessorTestBindingSha256.ToUpperInvariant()){throw 'FAT_P2_CLOSED_FULL12_TEST_PIN'}
$previousTest=Get-Content -LiteralPath $PredecessorTestBindingPath -Raw|ConvertFrom-Json -AsHashtable
if(-not($previousTest.success-and$previousTest.originalClosedKnown-and$previousTest.stateRestored-and$previousTest.syntheticTestsPassed-and$previousTest.mode-ceq'TEST')){throw 'FAT_P2_FULL_TEST_PREDECESSOR_REQUIRED'}
if((Get-Item -LiteralPath $previousTest.unit.path).Length-ne$previousTest.unit.bytes-or(Get-FileHash -LiteralPath $previousTest.unit.path -Algorithm SHA256).Hash-cne$previousTest.unit.sha256.ToUpperInvariant()){throw 'FAT_P2_CLOSED_FULL12_UNIT_PIN'}
$previousFat=Get-Content -LiteralPath $previousTest.unit.path -Raw|ConvertFrom-Json -AsHashtable
if($previousTest.syntheticTestCount-ne1109-or$previousFat.counts.methods-ne1109-or$previousFat.syntheticJUnitMethodsDeclared-ne1109){throw 'FAT_P2_EXACT_PREDECESSOR1109_LEDGER'}
$basePath=Join-Path $PSScriptRoot 'Prepare-LocalizadorOfflineUnit.ps1';$basePin=@($baseline.inputPins|Where-Object file -ceq 'scripts/validation/Prepare-LocalizadorOfflineUnit.ps1')
if($basePin.Count-ne1-or(Get-Item -LiteralPath $basePath).Length-ne$basePin[0].bytes-or(Get-FileHash -LiteralPath $basePath -Algorithm SHA256).Hash-cne$basePin[0].sha256.ToUpperInvariant()){throw 'FAT_EXACT_CURRENT_BASE_PREPARER'}
$moduleJava=@();foreach($branch in @('src/main/java/br/com/esl/etl/v2/modulos/faturasporcliente','src/test/java/br/com/esl/etl/v2/modulos/faturasporcliente')){
 $at=Join-Path $repo $branch;if(-not[IO.Directory]::Exists($at)){continue}
 foreach($path in [IO.Directory]::EnumerateFiles($at,'*.java',[IO.SearchOption]::AllDirectories)){
  for($check=$path;$check;$check=[IO.Path]::GetDirectoryName($check)){if(([IO.File]::GetAttributes($check)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'FAT_MODULE_REPARSE'}}
  $moduleJava+=@($path.Substring($repo.Length+1).Replace('\','/'))
 }
}
foreach($required in @('aplicacao/ExtrairFaturaClienteDataExport.java','aplicacao/FaturaClienteDataExportMapper.java','aplicacao/FaturaClienteDataExportDto.java','domain/FaturaClienteObservation.java','domain/FaturaClienteRules.java')){if($moduleJava-cnotcontains('src/main/java/br/com/esl/etl/v2/modulos/faturasporcliente/'+$required)){throw 'FAT_FIVE_REAL_MODULE_SOURCES_REQUIRED'}}
$controls=@($baseline.controlFiles)+@('src/main/resources/contracts/dataexport/expansion-4924-observed-wire-20261025-v1.json','scripts/validation/Prepare-FaturasClienteOfflineUnit.ps1','scripts/validation/Test-FaturasClienteWireScope.ps1','scripts/validation/Test-FaturasClienteNativeWireScope.ps1')+@($AdditionalControlFiles)
$fatRequiredSupport=@(
 'src/main/java/br/com/esl/etl/v2/bootstrap/DevLocalEngineObservations.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/FaturasClienteOperationalP2SelectionTest.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionCaptureTemplate.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionSchemaCatalog.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionPageProjection.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionSchemaGuard.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionCapturePlan.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionCaptureLimits.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionRawPage.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionSchemaPins.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportStrictJsonParser.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionProjectedRecord.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionPageProjector.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionAdmissionEmitter.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ObservedExpansionCaptureRequest.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionCaptured.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionObservation.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionBinding.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/expansao/ExpansionKey.java',
 'src/main/java/br/com/esl/etl/v2/bootstrap/DevLocalEngine.java',
 'src/test/java/br/com/esl/etl/v2/bootstrap/FaturasInvoiceNumbersRoundTripTest.java')
foreach($required in $fatRequiredSupport){if(-not[IO.File]::Exists((Join-Path $repo $required))){throw 'FAT_P2_DECODER_AND_ROUNDTRIP_REQUIRED'}}
$controls+=@('src/main/resources/contracts/dataexport/expansion-4924-observed-wire-20261108-p2.json',
 'database/pending/V107__faturas_field_scoped_array_positions.sql.template',
 'database/pending/105_validate_faturas_field_scoped_array_positions.sql.template')
$AdditionalOwnedJava=@($AdditionalOwnedJava)+$fatRequiredSupport
$metadata=(& $basePath -OutputDirectory $OutputDirectory -AdditionalControlFiles $controls -AdditionalOwnedJava (@($moduleJava)+@($AdditionalOwnedJava)))|ConvertFrom-Json -AsHashtable
$unit=Get-Content -LiteralPath $metadata.unit.path -Raw|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -LiteralPath $metadata.manifestCandidate.path -Raw|ConvertFrom-Json -AsHashtable
foreach($relative in $baseline.ownedJava){if($unit.ownedJava-cnotcontains$relative){throw 'FAT_NO_BASELINE_JAVA_DROPPED'}}
foreach($relative in $baseline.controlFiles){if($unit.controlFiles-cnotcontains$relative){throw 'FAT_NO_BASELINE_CONTROL_DROPPED'}}
$currentNames=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);foreach($class in $unit.testClasses){foreach($method in $class.methods){if(-not$currentNames.Add($class.className+'|'+$method.name)){throw 'FAT_DUPLICATE_JUNIT_SIGNATURE'}}}
$baselineCount=0;foreach($class in $baseline.testClasses){foreach($method in $class.methods){$baselineCount++;if(-not$currentNames.Contains($class.className+'|'+$method.name)){throw 'FAT_EVERY_FULL150_METHOD_RETAINED'}}}
if($baselineCount-ne1044-or$currentNames.Count-ne$unit.counts.methods){throw 'FAT_EXACT_ORDINARY_JUNIT_LEDGER'}
foreach($required in @('FaturasClienteIndependentRequestTest','FaturasClienteTransportTest','FaturasClienteIntervalCaptureTest','FaturasClienteSourceCaptureMainTest','FaturasClienteNativeUiDownloadMainTest','FaturasClienteRawMapperQualifierTest','FaturasClienteMapperQualificationMainTest','FaturasClienteContentAdapterTest','FaturasClienteDiagnosticMainTest')){if(@($unit.testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$required)).Count-ne1){throw 'FAT_CURRENT_FAMILY_TEST_OWNERSHIP'}}
foreach($relative in $previousFat.ownedJava){if($unit.ownedJava-cnotcontains$relative){throw 'FAT_P2_NO_FULL12_JAVA_DROPPED'}}
foreach($relative in $previousFat.controlFiles){if($unit.controlFiles-cnotcontains$relative){throw 'FAT_P2_NO_FULL12_CONTROL_DROPPED'}}
$previousMethods=0;foreach($class in $previousFat.testClasses){foreach($method in $class.methods){$previousMethods++;if(-not$currentNames.Contains($class.className+'|'+$method.name)){throw 'FAT_P2_EVERY_FULL12_METHOD_RETAINED'}}}
if($previousMethods-ne1109){throw 'FAT_P2_PREDECESSOR_METHOD_COUNT'}
foreach($required in @('FaturasArrayFieldParserTest','FaturasClienteP2WireContractTest')){if(@($unit.testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$required)).Count-ne1){throw 'FAT_P2_CAUSAL_TESTS_REQUIRED'}}
if(@($unit.testClasses|Where-Object className -ceq 'br.com.esl.etl.v2.bootstrap.FaturasInvoiceNumbersRoundTripTest').Count-ne1){throw 'FAT_P2_ROUNDTRIP_TEST_OWNERSHIP'}
foreach($required in @('src/main/java/br/com/esl/etl/v2/modulos/faturasporcliente/domain/FaturaInvoiceNumbers.java', 'src/main/java/br/com/esl/etl/v2/modulos/faturasporcliente/domain/FaturaInvoiceOrderNumbers.java', 'src/main/java/br/com/esl/etl/v2/modulos/faturasporcliente/aplicacao/FaturasArrayFieldParser.java', 'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao/FaturasArrayFieldParserTest.java', 'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao/FaturasClienteP2WireContractTest.java')) {
 if($unit.ownedJava-cnotcontains$required){throw 'FAT_P2_ALL_CAUSAL_TRANSITIVE_OWNERSHIP49_REQUIRED'}
}
if(@($unit.testClasses|Where-Object className -ceq 'br.com.esl.etl.v2.plataforma.fonte.dataexport.FaturasClienteOperationalP2SelectionTest').Count-ne1){throw 'FAT_P2_OPERATIONAL_GUARD_MAPPER_TEST_REQUIRED'}
foreach($required in @('src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao/FaturasClienteRecoveredDayQualification.java',
 'src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao/FaturasClienteRecoveredDayQualificationTest.java')){
 if($unit.ownedJava-cnotcontains$required){throw 'FAT_P2_RECOVERED_QUALIFICATION71_OWNERSHIP_REQUIRED'}
}
if(@($unit.testClasses|Where-Object className -ceq 'br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteRecoveredDayQualificationTest').Count-ne1){throw 'FAT_P2_RECOVERED_CAUSAL_TEST71_REQUIRED'}
$unit.revision='FATURAS_CLIENTE_4924_P2_WITH_CLOSED_FULL12_PREDECESSOR_V1';$unit.scopeNote='All1109 closed Full12 signatures and inputs plus Full150 predecessor retained; field-scoped FAT P2 decoder, array types, tests and SQL code controls owned. Counts derived. SQL execution and Source/Native policy adoption are not implied.'
function Publish-FatMetadata([string]$name,$value){$path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine);$stream=[IO.File]::Open($path,'CreateNew','Write','Read');try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()};return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}}
$unitPin=Publish-FatMetadata 'faturas-cliente-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.ownedJava=$unit.ownedJava;$manifest.testClasses=$unit.testClasses;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts;$manifest.note='Faturas Cliente4924 P2 plus all closed Full12 and Full150 inputs/methods; current complete build required. Source0 preparation is not live, SQL or presentation approval.'
$manifestPin=Publish-FatMetadata 'faturas-cliente-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0;testsExecuted=0}|ConvertTo-Json -Depth 8
param([Parameter(Mandatory)][string]$OutputDirectory,[string[]]$AdditionalControlFiles=@(),[string[]]$AdditionalOwnedJava=@())
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Install under scripts/validation only after Root review. Metadata preparation is not execution or acceptance.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$baselinePath='C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\independent-export-formatted-contaspagar0751-compile11\offline-unit.PREPARED.json'
if((Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash-cne'5A455A2E2BEAC93E5F0F395D86E1E86673719D20577E53CF1B99953F5F227979'){throw 'LOCALIZADOR_FROZEN_BASELINE_UNIT_PIN'}
$baseline=Get-Content -Raw -LiteralPath $baselinePath|ConvertFrom-Json -AsHashtable
$basePreparer=@($baseline.inputPins|Where-Object file -ceq 'scripts/validation/Prepare-ContasPagarOfflineUnit.ps1')
if($basePreparer.Count-ne1){throw 'LOCALIZADOR_EXACT_PREDECESSOR_PREPARER'}
$basePath=Join-Path $PSScriptRoot 'Prepare-ContasPagarOfflineUnit.ps1'
if((Get-Item -LiteralPath $basePath).Length-ne$basePreparer[0].bytes-or(Get-FileHash -LiteralPath $basePath -Algorithm SHA256).Hash-cne$basePreparer[0].sha256.ToUpperInvariant()){throw 'LOCALIZADOR_PREDECESSOR_PREPARER_PIN'}
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-ContasPagarOfflineUnit.ps1') -OutputDirectory $OutputDirectory -AdditionalControlFiles $baseline.controlFiles)|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
foreach($relative in $baseline.ownedJava){if($unit.ownedJava-cnotcontains$relative){throw 'LOCALIZADOR_NO_PREDECESSOR_JAVA_DROPPED'}}
foreach($relative in $baseline.controlFiles){if($unit.controlFiles-cnotcontains$relative){throw 'LOCALIZADOR_NO_PREDECESSOR_CONTROL_DROPPED'}}
function Pin-LocalizadorInput([string]$relative,[bool]$java){
 if($relative-isnot[string]-or$relative-cmatch'\\|(^|/)\.\.(/|$)'-or[IO.Path]::IsPathFullyQualified($relative)){throw 'LOCALIZADOR_RELATIVE_INPUT_SCOPE'}
 if($java){if($relative-cnotmatch'^src/(main|test)/java/br/com/esl/etl/v2/[A-Za-z0-9_/]+\.java$'){throw 'LOCALIZADOR_OWNED_JAVA_SCOPE'}}
 elseif($relative-cnotmatch'^(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_.-]+$'-or[IO.Path]::GetExtension($relative).ToLowerInvariant()-in@('.jar','.xlsx','.xls','.zip','.parquet','.bin','.db','.java')){throw 'LOCALIZADOR_CONTROL_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'LOCALIZADOR_INPUT_ROOT'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'LOCALIZADOR_INPUT_REPARSE'}}
 $item=Get-Item -LiteralPath $path;if($item.Length-le0-or$item.Length-gt262144){throw 'LOCALIZADOR_INPUT_BOUND'}
 return @{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$owned=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($relative in $unit.ownedJava){[void]$owned.Add($relative)}
$requiredLocalSupport=@(
 'src/main/java/br/com/esl/etl/v2/plataforma/SqlText.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/configuracao/DataExportClientSettings.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/configuracao/DataExportProperties.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/configuracao/SecretKey.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/configuracao/SecretProvider.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ApprovedGraphQlDocument.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractAlertSink.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractAllowance.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractCanonicalizer.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractChange.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractClassification.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractCompatibilityAlert.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractCompatibilityPolicy.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractDiff.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractDriftException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractExecutionBinding.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractExecutionContext.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractMetadata.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractObservationLimits.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractOpaquePath.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractPromotionPermit.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractResponse.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractResponsePathBoundary.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractResponseProfiler.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractRunGuard.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractSemanticsFingerprint.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractSourceKind.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractSourceSelection.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractText.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractTraversalTerminalEvidence.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractValidationResult.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/ContractValidator.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/SourceCompletenessStatus.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/SourceContractRelease.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/contrato/SourceDataEffect.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/controle/ControlPlaneStart.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/controle/ExecutionMode.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/controle/ExecutionPartitionKey.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/controle/ImmutableFingerprint.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/BusinessDateRange.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportBatchStaging.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportCircuitOpenException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportContractAdapter.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportExtractionAudit.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportExtractionLimits.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportExtractionResult.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportFailureCategory.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportFilterValue.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportFirstWaveContractCatalog.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportGateway.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportMetadataField.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportObservedEntityContractCatalog.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportObservedEntityPins.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageEntityLimitValidator.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageProgressGuard.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageRequest.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageResponse.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportPageStreamer.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportRawRecordSlices.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportReadPage.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportRecordInput.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportRequestProfile.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportResponseForm.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportResponseLimitExceededException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportRetryPolicy.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportStagingPipeline.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTemplate.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTemplateInfo.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTransport.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTransportFallbackPolicy.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTraversalVerification.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportUnavailableException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/SearchPath.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/SourceDateTimeRange.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/identidade/FirstWaveIdentityContract.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/identidade/IdentityQuarantineException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/identidade/ScopedSourceIdentity.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/observabilidade/CorrelationReference.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/observabilidade/StructuredCorrelationContext.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/staging/ShadowPromotionGateway.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/persistencia/staging/StagingPublicationResult.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualificacao/QualificationJson.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualificacao/CampaignJournal.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualidade/DataQualityEvaluationRequest.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualidade/DataQualityPolicyReference.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualidade/DataQualityPromotionPermit.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualidade/DataQualityRunSummary.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/qualidade/DataQualityState.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/CancellationToken.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/ResilienceBudgetExceededException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/ResilienceBudgetScope.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/ResilienceCancelledException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/ResilienceTimeoutException.java',
 'src/main/java/br/com/esl/etl/v2/plataforma/resiliencia/ResilienceTimeoutScope.java'
)
foreach($relative in @($requiredLocalSupport)+@($AdditionalOwnedJava)){
 [void](Pin-LocalizadorInput $relative $true);[void]$owned.Add($relative)
}
# Current exportacao Source/adapter/keyless/test Java are all discovered, never a count gate.
foreach($base in @('src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao','src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao','src/main/java/br/com/esl/etl/v2/modulos/localizacaocargas','src/test/java/br/com/esl/etl/v2/modulos/localizacaocargas')){
 $full=[IO.Path]::GetFullPath((Join-Path $repo $base));if(-not[IO.Directory]::Exists($full)){continue}
 $queue=[Collections.Generic.Queue[string]]::new();$queue.Enqueue($full)
 while($queue.Count){$at=$queue.Dequeue();if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'LOCALIZADOR_DISCOVERY_REPARSE'}
  foreach($entry in [IO.Directory]::EnumerateFileSystemEntries($at)){
   if(([IO.File]::GetAttributes($entry)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'LOCALIZADOR_DISCOVERY_REPARSE'}
   if([IO.Directory]::Exists($entry)){$queue.Enqueue($entry)}elseif([IO.Path]::GetExtension($entry)-ceq'.java'){
    $relative=$entry.Substring($repo.Length+1).Replace('\','/');[void](Pin-LocalizadorInput $relative $true);[void]$owned.Add($relative)
   }
  }
 }
}
$sorted=[string[]]@($owned);[Array]::Sort($sorted,[StringComparer]::Ordinal);$unit.ownedJava=$sorted
$controls=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$controlOrder=[Collections.Generic.List[string]]::new()
foreach($relative in @($unit.controlFiles)+@('src/main/resources/contracts/dataexport/localizacao-cargas-8656-observed-p1b-20261024.json','src/main/resources/contracts/dataexport/localizacao-cargas-8656-observed-p2-20261108.json','scripts/validation/Test-LocalizadorWireScope.ps1','scripts/validation/Test-LocalizadorNativeWireScope.ps1','scripts/validation/Prepare-LocalizadorOfflineUnit.ps1','scripts/validation/Exact-OwnedSpotlessSelection.ps1')+@($AdditionalControlFiles)){
 [void](Pin-LocalizadorInput $relative $false);if($controls.Add($relative)){$controlOrder.Add($relative)}
}
$unit.controlFiles=@($controlOrder)
$testClasses=@();$methodCount=0
foreach($relative in $sorted){
 if(-not$relative.StartsWith('src/test/')){continue};$source=[IO.File]::ReadAllText((Join-Path $repo $relative))
 if($source-match'@(ParameterizedTest|RepeatedTest|TestFactory)\b'){throw 'LOCALIZADOR_ORDINARY_JUNIT_ONLY'}
 $matches=[regex]::Matches($source,'(?m)^\s*@Test\s*(?:public\s+)?void\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)')
 if($matches.Count-ne[regex]::Matches($source,'(?m)^\s*@Test\b').Count){throw 'LOCALIZADOR_EXACT_ORDINARY_METHOD_LEDGER'}
 if($matches.Count-eq0){continue};$className=$relative.Substring('src/test/java/'.Length,$relative.Length-'src/test/java/'.Length-'.java'.Length).Replace('/','.')
 $methods=@();$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($match in $matches){$name=$match.Groups['name'].Value;if(-not$names.Add($name)){throw 'LOCALIZADOR_DUPLICATE_JUNIT_NAME'};$methods+=@(@{name=$name;junitNames=@($name)});$methodCount++}
 $testClasses+=@(@{className=$className;sourceFile=$relative;methods=$methods})
}
foreach($required in @('LocalizadorIndependentRequestTest','LocalizadorTransportTest','LocalizadorIntervalCaptureTest','LocalizadorSourceCaptureMainTest','LocalizadorNativeUiDownloadMainTest')){
 if(@($testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$required)).Count-ne1){throw 'LOCALIZADOR_CURRENT_SOURCE_TEST_SCOPE_REQUIRED'}
}
foreach($old in $baseline.testClasses){if(@($testClasses|Where-Object className -ceq $old.className).Count-ne1){throw 'LOCALIZADOR_NO_BASELINE_TEST_CLASS_DROPPED'}}
$unit.testClasses=$testClasses;$unit.syntheticJUnitMethodsDeclared=$methodCount
. (Join-Path $PSScriptRoot 'Exact-OwnedSpotlessSelection.ps1')
$unit.compactSpotlessSelection=Get-ExactOwnedSpotlessSelection $repo $sorted
$selection=$unit.compactSpotlessSelection.argument;$unit.formatProcess.arguments=@('-o',$selection,'spotless:apply')
$unit.testProcess.arguments=@('-o','-DforkCount=0',('-Dtest='+(($testClasses|ForEach-Object className)-join',')),$selection,'test')
$unit.inputPins=@($sorted|ForEach-Object{Pin-LocalizadorInput $_ $true})+@($unit.controlFiles|ForEach-Object{Pin-LocalizadorInput $_ $false})
$unit.counts=@{ownedJava=$sorted.Count;controls=$unit.controlFiles.Count;inputs=$unit.inputPins.Count;classes=$testClasses.Count;methods=$methodCount;junitCases=$methodCount}
$unit.revision='LOCALIZADOR_8656_SERVICE_AT_NATIVE26_WITH_FULL_CAP_PREDECESSOR_V1';$unit.scopeNote='Current Localizador source/adapter/test scope plus every closed CAP predecessor input/class. Counts derive from files and ordinary JUnit names. No Native layout, labels, identity, HTTP query or presentation policy is inferred.'
function Publish-LocalizadorMetadata([string]$name,$value){
 $path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $s=[IO.File]::Open($path,'CreateNew','Write','Read');try{$s.Write($bytes);$s.Flush($true)}finally{$s.Dispose()}
 return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish-LocalizadorMetadata 'localizador-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.ownedJava=$unit.ownedJava;$manifest.testClasses=$unit.testClasses;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts
$manifest.note='Candidate Localizador ownership and full CAP890 predecessor. Root must approve current unit/build. Source scope, Native layout/presentation and historical comparison remain pending.'
$manifestPin=Publish-LocalizadorMetadata 'localizador-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0;testsExecuted=0}|ConvertTo-Json -Depth 8

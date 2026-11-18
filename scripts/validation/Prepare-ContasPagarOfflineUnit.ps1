param([Parameter(Mandatory)][string]$OutputDirectory,[string[]]$AdditionalControlFiles=@(),[string[]]$AdditionalOwnedJava=@())
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Install under scripts/validation only after Root review. Metadata preparation is not execution or acceptance.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$baselinePath='C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\independent-export-formatted-sinistros0750-compile05\offline-unit.PREPARED.json'
if((Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash-cne'E97D843474C164A0C74E93301B4AFDAB01AFF6E6872DE1A0FDAB09D5F0C71749'){throw 'CONTAS_PAGAR_FROZEN_BASELINE_UNIT_PIN'}
$baseline=Get-Content -Raw -LiteralPath $baselinePath|ConvertFrom-Json -AsHashtable
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-SinistrosOfflineUnit.ps1') -OutputDirectory $OutputDirectory)|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
foreach($relative in $baseline.ownedJava){if($unit.ownedJava-cnotcontains$relative){throw 'CONTAS_PAGAR_NO_PREDECESSOR_JAVA_DROPPED'}}
foreach($relative in $baseline.controlFiles){if($unit.controlFiles-cnotcontains$relative){throw 'CONTAS_PAGAR_NO_PREDECESSOR_CONTROL_DROPPED'}}
function Pin-ContasPagarInput([string]$relative,[bool]$java){
 if($relative-isnot[string]-or$relative-cmatch'\\|(^|/)\.\.(/|$)'-or[IO.Path]::IsPathFullyQualified($relative)){throw 'CONTAS_PAGAR_RELATIVE_INPUT_SCOPE'}
 if($java){if($relative-cnotmatch'^src/(main|test)/java/br/com/esl/etl/v2/[A-Za-z0-9_/]+\.java$'){throw 'CONTAS_PAGAR_OWNED_JAVA_SCOPE'}}
 elseif($relative-cnotmatch'^(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_.-]+$'-or[IO.Path]::GetExtension($relative).ToLowerInvariant()-in@('.jar','.xlsx','.xls','.zip','.parquet','.bin','.db','.java')){throw 'CONTAS_PAGAR_CONTROL_SCOPE'}
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'CONTAS_PAGAR_INPUT_ROOT'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'CONTAS_PAGAR_INPUT_REPARSE'}}
 $item=Get-Item -LiteralPath $path;if($item.Length-le0-or$item.Length-gt262144){throw 'CONTAS_PAGAR_INPUT_BOUND'}
 return @{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$owned=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($relative in $unit.ownedJava){[void]$owned.Add($relative)}
foreach($relative in @(
 'src/main/java/br/com/esl/etl/v2/modulos/contasapagar/aplicacao/ContaPagarDataExportDto.java',
 'src/main/java/br/com/esl/etl/v2/modulos/contasapagar/aplicacao/ContaPagarDataExportMapper.java',
 'src/main/java/br/com/esl/etl/v2/modulos/contasapagar/aplicacao/ExtrairContaPagarDataExport.java',
 'src/main/java/br/com/esl/etl/v2/modulos/contasapagar/domain/ContaPagarObservation.java',
 'src/main/java/br/com/esl/etl/v2/modulos/contasapagar/domain/ContaPagarRules.java')+@($AdditionalOwnedJava)){
 [void](Pin-ContasPagarInput $relative $true);[void]$owned.Add($relative)
}
# Current exportacao Source/adapter/keyless/test Java are all discovered, never a count gate.
foreach($base in @('src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao','src/test/java/br/com/esl/etl/v2/plataforma/reconciliacao/exportacao','src/test/java/br/com/esl/etl/v2/modulos/contasapagar')){
 $full=[IO.Path]::GetFullPath((Join-Path $repo $base));if(-not[IO.Directory]::Exists($full)){continue}
 $queue=[Collections.Generic.Queue[string]]::new();$queue.Enqueue($full)
 while($queue.Count){$at=$queue.Dequeue();if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'CONTAS_PAGAR_DISCOVERY_REPARSE'}
  foreach($entry in [IO.Directory]::EnumerateFileSystemEntries($at)){
   if(([IO.File]::GetAttributes($entry)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'CONTAS_PAGAR_DISCOVERY_REPARSE'}
   if([IO.Directory]::Exists($entry)){$queue.Enqueue($entry)}elseif([IO.Path]::GetExtension($entry)-ceq'.java'){
    $relative=$entry.Substring($repo.Length+1).Replace('\','/');[void](Pin-ContasPagarInput $relative $true);[void]$owned.Add($relative)
   }
  }
 }
}
$sorted=[string[]]@($owned);[Array]::Sort($sorted,[StringComparer]::Ordinal);$unit.ownedJava=$sorted
$controls=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$controlOrder=[Collections.Generic.List[string]]::new()
foreach($relative in @($unit.controlFiles)+@('src/main/resources/contracts/dataexport/expansion-8636-observed-wire-20261025-v1.json','scripts/validation/Test-ContasPagarWireScope.ps1','scripts/validation/Prepare-ContasPagarOfflineUnit.ps1')+@($AdditionalControlFiles)){
 [void](Pin-ContasPagarInput $relative $false);if($controls.Add($relative)){$controlOrder.Add($relative)}
}
$unit.controlFiles=@($controlOrder)
$testClasses=@();$methodCount=0
foreach($relative in $sorted){
 if(-not$relative.StartsWith('src/test/')){continue};$source=[IO.File]::ReadAllText((Join-Path $repo $relative))
 if($source-match'@(ParameterizedTest|RepeatedTest|TestFactory)\b'){throw 'CONTAS_PAGAR_ORDINARY_JUNIT_ONLY'}
 $matches=[regex]::Matches($source,'(?m)^\s*@Test\s*(?:public\s+)?void\s+(?<name>[A-Za-z_$][A-Za-z0-9_$]*)\s*\(\s*\)')
 if($matches.Count-ne[regex]::Matches($source,'(?m)^\s*@Test\b').Count){throw 'CONTAS_PAGAR_EXACT_ORDINARY_METHOD_LEDGER'}
 if($matches.Count-eq0){continue};$className=$relative.Substring('src/test/java/'.Length,$relative.Length-'src/test/java/'.Length-'.java'.Length).Replace('/','.')
 $methods=@();$names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($match in $matches){$name=$match.Groups['name'].Value;if(-not$names.Add($name)){throw 'CONTAS_PAGAR_DUPLICATE_JUNIT_NAME'};$methods+=@(@{name=$name;junitNames=@($name)});$methodCount++}
 $testClasses+=@(@{className=$className;sourceFile=$relative;methods=$methods})
}
foreach($required in @('ContasPagarIndependentRequestTest','ContasPagarTransportTest','ContasPagarIntervalCaptureTest','ContasPagarSourceCaptureMainTest')){
 if(@($testClasses|Where-Object className -ceq ('br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.'+$required)).Count-ne1){throw 'CONTAS_PAGAR_CURRENT_SOURCE_TEST_SCOPE_REQUIRED'}
}
foreach($old in $baseline.testClasses){if(@($testClasses|Where-Object className -ceq $old.className).Count-ne1){throw 'CONTAS_PAGAR_NO_BASELINE_TEST_CLASS_DROPPED'}}
$unit.testClasses=$testClasses;$unit.syntheticJUnitMethodsDeclared=$methodCount
$selection='-DspotlessFiles='+($sorted-join',');$unit.formatProcess.arguments=@('-o',$selection,'spotless:apply')
$unit.testProcess.arguments=@('-o','-DforkCount=0',('-Dtest='+(($testClasses|ForEach-Object className)-join',')),$selection,'test')
$unit.inputPins=@($sorted|ForEach-Object{Pin-ContasPagarInput $_ $true})+@($unit.controlFiles|ForEach-Object{Pin-ContasPagarInput $_ $false})
$unit.counts=@{ownedJava=$sorted.Count;controls=$unit.controlFiles.Count;inputs=$unit.inputPins.Count;classes=$testClasses.Count;methods=$methodCount;junitCases=$methodCount}
$unit.revision='CONTAS_A_PAGAR_8636_ISSUE_DATE_SOURCE_KEYLESS28_AND_FULL_PREDECESSOR_V1';$unit.scopeNote='Current CAP Source/adapter/keyless/test scope plus the entire closed Sinistros predecessor. Counts derive from files and ordinary JUnit names. Metadata only; no Source, SQL, Native policy or acceptance.'
function Publish-ContasPagarMetadata([string]$name,$value){
 $path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $s=[IO.File]::Open($path,'CreateNew','Write','Read');try{$s.Write($bytes);$s.Flush($true)}finally{$s.Dispose()}
 return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish-ContasPagarMetadata 'contas-pagar-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.ownedJava=$unit.ownedJava;$manifest.testClasses=$unit.testClasses;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts
$manifest.note='Candidate current CAP28 and full prior ownership. Root must approve concrete unit/build; native policy, source admission and content acceptance remain separate.'
$manifestPin=Publish-ContasPagarMetadata 'contas-pagar-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0;testsExecuted=0}|ConvertTo-Json -Depth 8

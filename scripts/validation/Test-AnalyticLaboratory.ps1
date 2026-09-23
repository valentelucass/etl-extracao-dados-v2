#Requires -Version 7.5
param([switch]$Candidate,[switch]$SelfTest,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/macrobloco-analitico/'
Import-Module (Join-Path $PSScriptRoot 'AnalyticLaboratorySuccession.psm1') -Force
$successor=Get-AnalyticLaboratorySuccession -Root $root -SelfTest:$SelfTest
if($null -eq $successor){throw 'ANA_DELIVERY_MANIFEST_REQUIRED'}
function AnalyticView([string]$path){
 if($null -ne $successor.qualification -and $successor.qualification.map.ContainsKey($path)){
  return $successor.qualification.map[$path].snapshot
 }
 return $path
}
function Read([string]$path){Get-Content -Raw -LiteralPath (Join-Path $root (AnalyticView $path))|ConvertFrom-Json -Depth 60 -DateKind String}
function Hash([string]$path){(Get-FileHash -LiteralPath (Join-Path $root (AnalyticView $path))).Hash.ToLowerInvariant()}
function Exact($a,$b,[string]$reason){
 if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $reason}
}
$matrix=Read ($catalog+'matriz-colunas-final.json')
$typed=Read 'src/main/resources/analytic-laboratory/query-contracts.synthetic.json'
Exact $matrix.contracts.id $typed.contracts.id 'ANA_COLUMN_CONTRACT_SET'
if($matrix.contracts.Count -ne 19 -or $matrix.businessColumns -ne 673 -or $matrix.physicalColumns -ne 971 -or
 -not $matrix.identityIsSynthetic -or $matrix.externalCompatibilityAccepted){throw 'ANA_COLUMN_SCOPE'}
$count=0
foreach($contract in $matrix.contracts){
 $reader=@($typed.contracts|Where-Object id -CEQ $contract.id)[0]
 if($contract.columns.Count -ne $reader.columns.Count -or -not $contract.grain -or -not $contract.rule -or
  -not $contract.legacySourceSha256 -or $contract.definitionChain.Count -lt 1){throw 'ANA_COLUMN_LINEAGE'}
 foreach($column in $contract.columns){
  $expected=$reader.columns[$column.ordinal-1];$actual=$column.physicalMetadata
  if($column.name -cne $expected.name -or $actual.columnName -cne $expected.name -or
   $actual.sqlType -cne $expected.type -or $actual.precision -ne $expected.precision -or
   $actual.scale -ne $expected.scale -or $actual.nullable -ne $expected.nullable -or
   -not $column.localExpression -or -not $column.legacyExpression -or -not $column.consumer){throw 'ANA_COLUMN_METADATA'}
  $count++
 }
}
if($count -ne 673){throw 'ANA_COLUMN_MEMBERSHIP'}
$structure=Read ($catalog+'estrutura-local.json')
$laterFiles=if($null -ne $successor.qualification){@($successor.qualification.newFiles)}else{@()}
# The validated succession owns later migrations; this check describes the pinned analytic revision.
$migrations=@(Get-ChildItem -LiteralPath (Join-Path $root 'database/migrations') -File|
 Where-Object {('database/migrations/'+$_.Name) -cnotin $laterFiles}|Sort-Object Name)
$baseline=Get-Content -Raw -LiteralPath (Join-Path $root (AnalyticView 'database/baseline/001_schema_foundation_baseline.sql'))
$includes=@([regex]::Matches($baseline,'(?m)^:r "\.\.\\migrations\\([^"\r\n]+)"\r?$')|ForEach-Object {$_.Groups[1].Value})
Exact $migrations.Name $includes 'ANA_BASELINE_MEMBERSHIP'
Exact $structure.migrations.path @($migrations|ForEach-Object {'database/migrations/'+$_.Name}) 'ANA_STRUCTURE_MEMBERSHIP'
if($structure.lastVersion -ne 98 -or $structure.baselineSha256 -cne (Hash 'database/baseline/001_schema_foundation_baseline.sql')){throw 'ANA_STRUCTURE_BASELINE'}
foreach($entry in $structure.migrations){if((Hash $entry.path) -cne $entry.sha256){throw 'ANA_MIGRATION_DRIFT'}}
function VerifySummary($s){
 Exact $s.fronts @('A','B','C','D','E','F','G','H','I','J','K','L','M','N') 'ANA_SUMMARY_FRONTS'
 if(-not $s.passed -or -not $s.deliveryComplete -or $s.status -cne 'CONSTRUÇÃO_LOCAL_CONCLUÍDA' -or
  $s.full.failures -ne 0 -or $s.full.errors -ne 0 -or $s.full.skipped -ne 4 -or -not $s.full.coveragePassed -or
  $s.integration.failures -ne 0 -or $s.integration.errors -ne 0 -or $s.integration.skipped -ne 0 -or
  -not $s.integration.previous233Exact -or -not $s.integration.mandatoryClassesPresent -or
  -not $s.integration.rollbackConfirmed -or -not $s.java.sourceAndJarChecked -or
  -not $s.jar.passed -or $s.jar.cases -ne 40 -or -not $s.jar.rollbackConfirmed -or
  $s.scale.heapPlateauProven -or $s.scale.actualPlans -lt 1 -or
  $s.construction.before -ne 32 -or $s.construction.after -ne 37 -or $s.construction.denominator -ne 45 -or
  $s.remoteCalls -ne 0 -or $s.newAcceptances -ne 0 -or $s.operationalPromotionAuthorized -or
  $s.realQualificationAccepted){throw 'ANA_FINAL_VERIFICATION'}
 Exact $s.scale.completedScales @(4,16,32,16) 'ANA_FINAL_SCALES'
}
$guards=0
if(-not $Candidate){
 $summary=Read ($catalog+'verification-summary.json')
 VerifySummary $summary
 if($successor.status -cne $summary.status){throw 'ANA_FINAL_STATUS'}
 $quadro=Read ($catalog+'quadro-construcao.json')
 $old=Read 'docs/catalogos/macrobloco-expansao/quadro-construcao.json'
 Exact $quadro.units.id $old.units.id 'ANA_CONSTRUCTION_UNIVERSE'
 if($quadro.units.Count -ne 45 -or @($quadro.units|Where-Object {$_.before.locallyVerified}).Count -ne 32 -or
  @($quadro.units|Where-Object {$_.after.locallyVerified}).Count -ne 37){throw 'ANA_CONSTRUCTION_COUNT'}
 Exact @($quadro.units|Where-Object {-not $_.before.locallyVerified -and $_.after.locallyVerified}|ForEach-Object id) @('MAT-01','MAT-02','MAT-05','V2-034','V2-037') 'ANA_CONSTRUCTION_DELTA'
 if($SelfTest){
  foreach($mutation in @(
   {param($s)$s.integration.skipped=1}, {param($s)$s.full.coveragePassed=$false},
   {param($s)$s.integration.previous233Exact=$false}, {param($s)$s.java.sourceAndJarChecked=$false},
   {param($s)$s.jar.cases=39}, {param($s)$s.scale.heapPlateauProven=$true},
   {param($s)$s.operationalPromotionAuthorized=$true}, {param($s)$s.construction.after=38}
  )){
   $copy=$summary|ConvertTo-Json -Depth 60|ConvertFrom-Json -Depth 60 -DateKind String
   & $mutation $copy
   $reason='ACCEPTED';try{VerifySummary $copy}catch{$reason=$_.Exception.Message}
   if($reason -cne 'ANA_FINAL_VERIFICATION'){throw 'ANA_FINAL_GUARD'};$guards++
  }
 }
}
if($IncludePrivateEvidence){
 foreach($entry in $successor.manifest.evidence){
  if($entry.path -cnotmatch '^target/macrobloco-analitico-20260912-01/[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or
   (Hash $entry.path) -cne $entry.sha256){throw 'ANA_PRIVATE_EVIDENCE_HASH'}
 }
 if(-not $Candidate){
  $build=Read ('target/macrobloco-analitico-20260912-01/'+$summary.java.attempt+'-exit.json')
  $jar=Read ($summary.jar.directory+'/result.json')
  if($build.exit -ne 0 -or $build.phase -cne 'VerifyPhysical' -or -not $jar.passed -or -not $jar.rollbackConfirmed){throw 'ANA_PRIVATE_PHYSICAL'}
  $identity=Read ($summary.jar.directory+'/artifact-identity.json')
  foreach($entry in $identity.sources){if((Hash $entry.path) -cne $entry.sha256){throw 'ANA_DELIVERED_SOURCE_DRIFT'}}
 }
}
@{passed=$true;candidate=[bool]$Candidate;status=$successor.status;columns=$count;contracts=19;migrations=98;
 successionGuards=$successor.guards;summaryGuards=$guards;private=[bool]$IncludePrivateEvidence;
 remoteCalls=0;newAcceptances=0}|ConvertTo-Json

#Requires -Version 7.5
param([switch]$Candidate,[switch]$SelfTest,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/macrobloco-integracao-funcional/'
$old='docs/catalogos/macrobloco-fechamento-construcao/'
$round='target/macrobloco-integracao-funcional-20260914-01/'
Import-Module (Join-Path $PSScriptRoot 'FunctionalIntegrationSuccession.psm1') -Force
$succession=Get-FunctionalIntegrationSuccession -Root $root -SelfTest:$SelfTest
if($null -eq $succession){throw 'FUNCTIONAL_MANIFEST_REQUIRED'}
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Read([string]$p){
    $file=Join-Path $root $p
    if((Get-Item -LiteralPath $file).Length -ge 5242880){throw 'FUNCTIONAL_CANONICAL_SIZE'}
    Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($file)) 5242880
}
function Hash([string]$p){
    # Compare the historical package to its qualified bytes; the successor validates today's files.
    if($null -ne $succession.successor -and $succession.successor.map.ContainsKey($p)){
        $p=$succession.successor.map[$p].snapshot
    }
    (Get-FileHash -LiteralPath (Join-Path $root $p)).Hash.ToLowerInvariant()
}
function Exact($a,$b,[string]$reason){
    if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $reason}
}
function Same($actual,$expected){
    if($null -eq $expected){if($null -ne $actual){throw 'FUNCTIONAL_ORIGINAL_REWRITTEN'};return}
    if($expected -is [Collections.IDictionary]){
        if($actual -isnot [Collections.IDictionary]){throw 'FUNCTIONAL_ORIGINAL_REWRITTEN'}
        Exact @($actual.Keys) @($expected.Keys) 'FUNCTIONAL_ORIGINAL_REWRITTEN'
        foreach($key in $expected.Keys){Same $actual[$key] $expected[$key]}
    }elseif($expected -is [Array]){
        if($actual -isnot [Array] -or $actual.Count -ne $expected.Count){throw 'FUNCTIONAL_ORIGINAL_REWRITTEN'}
        for($i=0;$i -lt $expected.Count;$i++){Same $actual[$i] $expected[$i]}
    }elseif($null -eq $actual -or $actual.GetType() -ne $expected.GetType() -or $actual -cne $expected){throw 'FUNCTIONAL_ORIGINAL_REWRITTEN'}
}
function Original($actual,$expected){
    $copy=[ordered]@{};foreach($key in $actual.Keys){if($key -cne 'functionalReview'){$copy[$key]=$actual[$key]}}
    Same $copy $expected
}
function Pin($entry){
    Assert-QualificationFields $entry @('path','sha256')
    if($entry.path -cnotmatch '^target/macrobloco-integracao-funcional-20260914-01/[A-Za-z0-9_./-]+$' -or
        $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Hash $entry.path) -cne $entry.sha256){throw 'FUNCTIONAL_PRIVATE_PIN'}
}
$baseline=Read ($old+'matriz-45-unidades.json')
function Units($matrix){
    if($matrix.construction.before -ne 39 -or $matrix.construction.after -ne 39 -or $matrix.construction.total -ne 45 -or
        $matrix.historicalAcceptances.accepted -ne 67 -or $matrix.historicalAcceptances.total -ne 115){throw 'FUNCTIONAL_COUNTING'}
    Exact $matrix.units.id $baseline.units.id 'FUNCTIONAL_UNIVERSE'
    for($i=0;$i -lt 45;$i++){
        $unit=$matrix.units[$i];Original $unit $baseline.units[$i]
        $review=$unit.functionalReview
        if(-not $review.originalCriteriaPreserved -or $review.countChanged -or $review.nominalAcceptanceAdded -or
            $review.independentLocalWork -cne 'IMPLEMENTADO_OU_CAPACIDADE_EXISTENTE_REUTILIZADA'){throw 'FUNCTIONAL_REQUIREMENT_UNTRACED'}
        foreach($field in @('code','integrationConsumer','localProof','nominalDataOrDecision','configurationOrTarget','materialProof','authorizedAction')){
            if($null -eq $review[$field] -or [string]::IsNullOrWhiteSpace([string]$review[$field])){throw 'FUNCTIONAL_REQUIREMENT_UNTRACED'}
        }
        if($unit.countedAfter -isnot [bool] -or $unit.realAcceptance -isnot [bool] -or $unit.realAcceptance){throw 'FUNCTIONAL_COUNTING'}
    }
}
$matrix=Read ($catalog+'matriz-45-unidades.json');Units $matrix
function Universes($trace){
    foreach($spec in @(@('inputs',11),@('dimensions',6),@('facts',5),@('contracts',19),@('outputColumns',673),@('physicalMetadata',971),@('scopes',35))){
        if($trace.universes[$spec[0]] -ne $spec[1]){throw 'FUNCTIONAL_SCOPE_CONFLATION'}
    }
    if($trace.rows -ne 2437 -or $trace.conditionedExamined -ne 595 -or $trace.opaquePreserved -ne 442 -or
        -not $trace.originalFieldsPreserved -or -not $trace.originalClassificationPreserved){throw 'FUNCTIONAL_FIELD_UNIVERSE'}
}
$trace=Read ($catalog+'rastreabilidade-2437-campos.json');Universes $trace
if($trace.parts.Count -ne 3){throw 'FUNCTIONAL_FIELD_PARTS'}
$conditioned=0;$distribution=@{}
for($partIndex=0;$partIndex -lt 3;$partIndex++){
    $partName=('rastreabilidade-2437-campos-{0:D3}.json' -f ($partIndex+1));$pin=$trace.parts[$partIndex]
    if($pin.path -cne ($catalog+$partName) -or (Hash $pin.path) -cne $pin.sha256){throw 'FUNCTIONAL_FIELD_PART_PIN'}
    $part=Read $pin.path;$original=Read ($old+$partName)
    if($part.rows.Count -ne $original.rows.Count -or $part.rows.Count -ne $pin.rows){throw 'FUNCTIONAL_FIELD_PART_ROWS'}
    for($i=0;$i -lt $part.rows.Count;$i++){
        $row=$part.rows[$i];Original $row $original.rows[$i]
        if($row.audit.state -ceq 'VINCULO_INDIVIDUAL_CONDICIONADO'){
            $conditioned++
            if($row.functionalReview.decision -cnotin @('VINCULO_LOCAL_EXPLICITADO','VINCULO_NOMINAL_CONDICIONADO_DECOMPOSTO')){throw 'FUNCTIONAL_FIELD_NOT_REVIEWED'}
        }elseif($row.functionalReview.decision -cne 'CLASSIFICACAO_ANTERIOR_PRESERVADA'){throw 'FUNCTIONAL_RECLASSIFICATION'}
        if($row.functionalReview.nominalAcceptance -isnot [bool] -or $row.functionalReview.nominalAcceptance){throw 'FUNCTIONAL_RECLASSIFICATION'}
        $decision=$row.functionalReview.decision;if(-not $distribution.ContainsKey($decision)){$distribution[$decision]=0};$distribution[$decision]++
    }
}
if($conditioned -ne 595){throw 'FUNCTIONAL_CONDITIONED_COUNT'}
foreach($decision in $distribution.Keys){if($trace.distribution[$decision] -ne $distribution[$decision]){throw 'FUNCTIONAL_FIELD_DISTRIBUTION'}}
foreach($spec in @(@('rastreabilidade-401-artefatos.json',401),@('rastreabilidade-75-regras.json',75))){
    $document=Read ($catalog+$spec[0]);$original=Read ($old+$spec[0])
    if($document.rows.Count -ne $spec[1]){throw 'FUNCTIONAL_PORTABILITY_UNIVERSE'}
    for($i=0;$i -lt $spec[1];$i++){
        $row=$document.rows[$i];Original $row $original.rows[$i]
        if(-not $row.functionalReview.originalClassificationPreserved -or $row.functionalReview.nominalAcceptanceAdded -or
            @($row.functionalReview.exactExternalInputs).Count -lt 1){throw 'FUNCTIONAL_PORTABILITY_REVIEW'}
    }
}
$guards=0
if($SelfTest){
    foreach($spec in @(
        @('FUNCTIONAL_COUNTING',{param($m)$m.construction.after=40}),
        @('FUNCTIONAL_UNIVERSE',{param($m)$m.units[1].id=$m.units[0].id}),
        @('FUNCTIONAL_ORIGINAL_REWRITTEN',{param($m)$m.units[0].originalCriteria[0].text='RECLASSIFIED'}),
        @('FUNCTIONAL_REQUIREMENT_UNTRACED',{param($m)$m.units[0].functionalReview.nominalAcceptanceAdded=$true}),
        @('FUNCTIONAL_REQUIREMENT_UNTRACED',{param($m)$m.units[0].functionalReview.materialProof=''})
    )){
        $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($matrix|ConvertTo-Json -Depth 50 -Compress))) 5242880
        & $spec[1] $copy;$reason='ACCEPTED';try{Units $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $spec[0]){throw ('FUNCTIONAL_MATRIX_GUARD_'+$reason)};$guards++
    }
    $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($trace|ConvertTo-Json -Depth 20 -Compress))) 5242880
    $copy.universes.scopes=41;$reason='ACCEPTED';try{Universes $copy}catch{$reason=$_.Exception.Message}
    if($reason -cne 'FUNCTIONAL_SCOPE_CONFLATION'){throw 'FUNCTIONAL_SCOPE_GUARD'};$guards++
}
if(-not $Candidate){
    $summary=Read ($catalog+'verification-summary.json')
    if($summary.status -cne 'INTEGRACAO_FUNCIONAL_LOCAL_CONCLUIDA' -or $summary.status -cne $succession.status -or
        $summary.passed -isnot [bool] -or -not $summary.passed -or $summary.newAcceptances -ne 0 -or
        $summary.remoteCalls -ne 0 -or $summary.durableDomainRows -ne 0 -or $summary.realQualificationAccepted -or
        $summary.operationalPromotionAuthorized){throw 'FUNCTIONAL_SUMMARY'}
    Exact $summary.fronts @('A','B','C','D','E','F','G','H','I','J','K','L','M','N') 'FUNCTIONAL_FRONTS'
    $obligations=Read ($catalog+'obrigacoes-locais.json')
    Exact $obligations.obligations.id $summary.fronts 'FUNCTIONAL_OBLIGATIONS'
    foreach($item in $obligations.obligations){
        if($item.status -cne 'CONCLUIDO_LOCAL' -or -not $item.consumer -or $item.evidence.Count -lt 1){throw 'FUNCTIONAL_OBLIGATION_OPEN'}
    }
    if($IncludePrivateEvidence){
        foreach($proof in $summary.proofs.Values){Pin $proof}
        $regression=Read $summary.proofs.regression.path
        if(-not $regression.passed -or -not $regression.previous423Exact -or -not $regression.skipReasonsPreserved -or
            -not $regression.coveragePassed -or -not $regression.rollbackConfirmed -or $regression.unitTests -lt 2042 -or
            $regression.unitSkipped -ne 4 -or $regression.integrationTests -le 423 -or $regression.integrationSkipped -ne 0){throw 'FUNCTIONAL_REGRESSION'}
        foreach($report in $regression.reports){if((Hash $report.path) -cne $report.sha256){throw 'FUNCTIONAL_REPORT_DRIFT'}}
        $artifact=Read $summary.proofs.artifact.path;if(-not $artifact.passed){throw 'FUNCTIONAL_ARTIFACT'}
        foreach($name in @('fileConsumers','small','large','cancelCapture','cancelPrepare','cancelReceipt','inputGuards')){
            $proof=Read $summary.proofs[$name].path;if($proof.state -cne 'PASS_LOCAL' -or $proof.sourceWorkspace){throw ('FUNCTIONAL_NEW_PATH_'+$name)}
        }
        $guard=Read $summary.proofs.inputGuards.path
        if($guard.cases.Count -ne 4 -or @($guard.cases|Where-Object {$_.exit -ne 2 -or $_.reserved -ne 0 -or $_.intent -ne 0 -or $_.worker -ne 0 -or $_.jdbc -cne 'NOT_STARTED'}).Count -ne 0){throw 'FUNCTIONAL_BEFORE_EFFECT'}
        $resume=Read $summary.proofs.resume.path
        if(-not $resume.passed -or $resume.cases.Count -ne 4 -or $resume.sql -or $resume.workerRepeated -or $resume.sourceWorkspace){throw 'FUNCTIONAL_RESUME'}
        foreach($name in @($summary.package.first,$summary.package.second)){
            if((Hash ($round+$name+'/qualification.zip')) -cne $summary.package.archiveSha256){throw 'FUNCTIONAL_PACKAGE_DRIFT'}
        }
        foreach($entry in (Read ($round+$summary.package.first+'/qualified-source-inventory.json'))){
            if((Hash $entry.path) -cne $entry.sha256){throw 'FUNCTIONAL_CURRENT_SOURCE_DRIFT'}
        }
    }
}
if($IncludePrivateEvidence){foreach($entry in $succession.manifest.evidence){Pin $entry}}
[ordered]@{passed=$true;candidate=[bool]$Candidate;status=$succession.status;units=45;counted=39;
    fields=2437;artifacts=401;rules=75;conditionedExamined=595;scopes=35;originalRowsPreserved=$true;
    successionGuards=$succession.guards;matrixGuards=$guards;private=[bool]$IncludePrivateEvidence;
    operationalAcceptance=$false;humanReview=$false}|ConvertTo-Json

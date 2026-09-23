#Requires -Version 7.5
param([switch]$Candidate,[switch]$SelfTest,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/macrobloco-fechamento-construcao/'
$round='target/macrobloco-fechamento-construcao-20260913-01/'
Import-Module (Join-Path $PSScriptRoot 'ConstructionClosureSuccession.psm1') -Force
$succession=Get-ConstructionClosureSuccession -Root $root -SelfTest:$SelfTest
if($null -eq $succession){throw 'CLOSURE_MANIFEST_REQUIRED'}
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
$utf8=[Text.UTF8Encoding]::new($false,$true)
function PredecessorPath([string]$p){
    if($null -ne $succession.successor -and $succession.successor.map.ContainsKey($p)){return $succession.successor.map[$p].snapshot}
    return $p
}
function Read([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $root (PredecessorPath $p)))) 8388608}
function Hash([string]$p){(Get-FileHash -LiteralPath (Join-Path $root (PredecessorPath $p))).Hash.ToLowerInvariant()}
function Exact($a,$b,[string]$reason){
    if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $reason}
}
function Pin($entry){
    if($entry.path -cnotmatch '^target/macrobloco-fechamento-construcao-20260913-01/[A-Za-z0-9_./-]+$' -or
        $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Hash $entry.path) -cne $entry.sha256){throw 'CLOSURE_PRIVATE_PIN'}
}
$old=Read 'docs/catalogos/macrobloco-qualificacao-pacote/quadro-construcao.json'
function Units($matrix){
    if($matrix.construction.before -ne 39 -or $matrix.construction.after -ne 39 -or $matrix.construction.total -ne 45 -or
        $matrix.historicalAcceptances.accepted -ne 67 -or $matrix.historicalAcceptances.total -ne 115){throw 'CLOSURE_COUNTING'}
    Exact $matrix.units.id $old.units.id 'CLOSURE_UNIVERSE'
    foreach($unit in $matrix.units){
        $original=@($old.units|Where-Object id -CEQ $unit.id)[0]
        $counted=$original.after.implemented -and $original.after.integrated -and $original.after.locallyVerified
        if($unit.countedBefore -isnot [bool] -or $unit.countedAfter -isnot [bool] -or $unit.realAcceptance -isnot [bool] -or
            $unit.countedBefore -ne $counted -or $unit.countedAfter -ne $counted -or $unit.realAcceptance){throw 'CLOSURE_COUNTING'}
        if($unit.originalCriteria.Count -lt 1 -or $unit.consumerFiles.Count -lt 1 -or -not $unit.conclusion -or
            -not $unit.missingKind -or $unit.gates.Count -lt 1 -or -not $unit.nextAction){throw 'CLOSURE_REQUIREMENT_UNTRACED'}
        foreach($file in $unit.consumerFiles){if(-not (Test-Path -LiteralPath (Join-Path $root $file) -PathType Leaf)){throw 'CLOSURE_CONSUMER_MISSING'}}
    }
}
$matrix=Read ($catalog+'matriz-45-unidades.json');Units $matrix
$guards=0
if($SelfTest){
    foreach($spec in @(
        @('CLOSURE_COUNTING',{param($m)$m.construction.after=40}),
        @('CLOSURE_UNIVERSE',{param($m)$m.units[1].id=$m.units[0].id}),
        @('CLOSURE_COUNTING',{param($m)$m.units[0].realAcceptance=$true}),
        @('CLOSURE_COUNTING',{param($m)$m.units[0].countedAfter='false'}),
        @('CLOSURE_REQUIREMENT_UNTRACED',{param($m)$m.units[0].originalCriteria=@()})
    )){
        $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($matrix|ConvertTo-Json -Depth 40 -Compress))) 8388608
        & $spec[1] $copy
        $reason='ACCEPTED';try{Units $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $spec[0]){throw ('CLOSURE_MATRIX_GUARD_'+$reason)};$guards++
    }
}
foreach($spec in @(
    @{name='rastreabilidade-2437-campos.json';csv='matriz-campos.csv';key='matrix_id';count=2437},
    @{name='rastreabilidade-401-artefatos.json';csv='inventario-artefatos.csv';key='artifact_id';count=401},
    @{name='rastreabilidade-75-regras.json';csv='regras-negocio.csv';key='rule_id';count=75}
)){
    $document=Read ($catalog+$spec.name);$csv='docs/catalogos/portabilidade/'+$spec.csv
    if($spec.count -eq 2437){
        if($document.rowCount -ne 2437 -or $document.parts.Count -ne 3){throw 'CLOSURE_FIELD_PARTS'}
        $rows=[Collections.Generic.List[object]]::new()
        for($partIndex=0;$partIndex -lt 3;$partIndex++){
            $entry=$document.parts[$partIndex]
            $expectedPath=$catalog+('rastreabilidade-2437-campos-{0:D3}.json' -f ($partIndex+1))
            if($entry.path -cne $expectedPath -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
                (Hash $entry.path) -cne $entry.sha256){throw 'CLOSURE_FIELD_PART_PIN'}
            $part=Read $entry.path
            if($part.version -ne 1 -or $part.index -ne $partIndex+1 -or $part.rows.Count -ne $entry.rows -or
                $part.rows.Count -lt 1 -or $part.rows.Count -gt 813){throw 'CLOSURE_FIELD_PART_ROWS'}
            foreach($row in $part.rows){$rows.Add($row)}
        }
        $document['rows']=$rows.ToArray()
    }
    if($document.source.path -cne $csv -or $document.source.sha256 -cne (Hash $csv) -or $document.rows.Count -ne $spec.count){throw 'CLOSURE_PORTABILITY_SOURCE'}
    $original=@(Import-Csv -LiteralPath (Join-Path $root $csv))
    for($i=0;$i -lt $original.Count;$i++){
        $row=$document.rows[$i];$expected=$original[$i]
        Exact $row.original.Keys $expected.PSObject.Properties.Name 'CLOSURE_ORIGINAL_FIELDS'
        foreach($field in $expected.PSObject.Properties.Name){if($row.original[$field] -cne $expected.$field){throw 'CLOSURE_ORIGINAL_REWRITTEN'}}
        if(-not $row.audit.state -or -not $row.audit.missing -or $row.audit.realAcceptance -isnot [bool] -or $row.audit.realAcceptance){throw 'CLOSURE_PORTABILITY_VERDICT'}
    }
}
$trace=Read ($catalog+'rastreabilidade-resumo.json')
foreach($spec in @(@('entries',11),@('dimensions',6),@('facts',5),@('contracts',19),@('businessColumns',673),@('physicalMetadata',971),@('qualificationScopes',35))){
    if($trace[$spec[0]] -ne $spec[1]){throw 'CLOSURE_SCOPE_CONFLATION'}
}
foreach($pin in $trace.sourcePins){if((Hash $pin.path) -cne $pin.sha256){throw 'CLOSURE_TRACE_PIN'}}
if(-not $Candidate){
    $summary=Read ($catalog+'verification-summary.json')
    if($summary.status -cne 'AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS' -or $summary.status -cne $succession.status -or
        $summary.passed -isnot [bool] -or -not $summary.passed -or $summary.construction.before -ne 39 -or $summary.construction.after -ne 39 -or
        $summary.construction.total -ne 45 -or $summary.newAcceptances -ne 0 -or $summary.remoteCalls -ne 0 -or $summary.durableDomainRows -ne 0 -or
        $summary.realQualificationAccepted -or $summary.operationalPromotionAuthorized){throw 'CLOSURE_SUMMARY'}
    Exact $summary.fronts @('A','B','C','D','E','F','G','H','I','J','K','L','M','N') 'CLOSURE_FRONTS'
    if($IncludePrivateEvidence){
        foreach($entry in $summary.proofs.Values){Pin $entry}
        $regression=Read $summary.proofs.regression.path
        if(-not $regression.passed -or -not $regression.previous417Exact -or -not $regression.coveragePassed -or
            -not $regression.rollbackConfirmed -or $regression.unitTests -ne 1976 -or $regression.unitSkipped -ne 4 -or
            $regression.integrationTests -ne 423 -or $regression.integrationSkipped -ne 0){throw 'CLOSURE_REGRESSION'}
        foreach($report in $regression.reports){if((Hash $report.path) -cne $report.sha256){throw 'CLOSURE_REPORT_DRIFT'}}
        foreach($name in @('artifact','resumePackage')){
            $proof=Read $summary.proofs[$name].path
            if($proof.passed -isnot [bool] -or -not $proof.passed){throw ('CLOSURE_PROOF_'+$name)}
        }
        foreach($spec in @(@('extractedGuards',21),@('controlGuards',8))){
            $proof=Read $summary.proofs[$spec[0]].path
            if($proof.state -cne 'PASS_LOCAL' -or $proof.count -ne $spec[1] -or
                $proof.cases.Count -ne $spec[1] -or $proof.jdbc -cne 'NOT_STARTED' -or
                @($proof.cases|Where-Object {$_.passed -isnot [bool] -or -not $_.passed -or $_.exit -ne 2}).Count -ne 0){throw ('CLOSURE_PROOF_'+$spec[0])}
        }
        $sources=Read ($round+$summary.package.first+'/qualified-source-inventory.json')
        foreach($entry in $sources){if((Hash $entry.path) -cne $entry.sha256){throw 'CLOSURE_CURRENT_SOURCE_DRIFT'}}
        foreach($name in @($summary.package.first,$summary.package.second)){
            if((Hash ($round+$name+'/qualification.zip')) -cne $summary.package.archiveSha256){throw 'CLOSURE_PACKAGE_DRIFT'}
        }
    }
}
if($IncludePrivateEvidence){foreach($entry in $succession.manifest.evidence){Pin $entry}}
[ordered]@{passed=$true;candidate=[bool]$Candidate;status=$succession.status;units=45;counted=39;
    artifacts=401;fields=2437;rules=75;scope='local audit and correction; original gates preserved';
    successionGuards=$succession.guards;matrixGuards=$guards;private=[bool]$IncludePrivateEvidence}|ConvertTo-Json

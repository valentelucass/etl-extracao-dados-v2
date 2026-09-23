#Requires -Version 7.5
param([switch]$Candidate,[switch]$SelfTest,[switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog='docs/catalogos/macrobloco-qualificacao-pacote/'
$round='target/macrobloco-qualificacao-pacote-20260913-01/'
Import-Module (Join-Path $PSScriptRoot 'QualificationLaboratorySuccession.psm1') -Force
$successor=Get-QualificationLaboratorySuccession -Root $root -SelfTest:$SelfTest
if($null -eq $successor){throw 'QUAL_DELIVERY_MANIFEST_REQUIRED'}
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
function Read([string]$path){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $root $path))) 8388608}
function Hash([string]$path){(Get-FileHash -LiteralPath (Join-Path $root $path)).Hash.ToLowerInvariant()}
function Exact($a,$b,[string]$reason){
    if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $reason}
}
function Pin($entry){
    Assert-QualificationFields $entry @('path','sha256')
    if($entry.path -cnotmatch '^target/macrobloco-qualificacao-pacote-20260913-01/[A-Za-z0-9_./-]+$' -or
        $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
        (Hash $entry.path) -cne $entry.sha256){throw 'QUAL_DELIVERY_PRIVATE_PIN'}
}
$matrix=Read ($catalog+'matriz-colunas.json')
$oracle=Read 'src/main/resources/qualification-laboratory/outputs.synthetic.json'
$typed=Read 'src/main/resources/analytic-laboratory/query-contracts.synthetic.json'
if($matrix.businessColumns -ne 673 -or $matrix.physicalColumns -ne 971 -or $matrix.contracts -ne 19 -or
    $matrix.oracle.sha256 -cne (Hash $matrix.oracle.path) -or $matrix.physical.sha256 -cne (Hash $matrix.physical.path)){
    throw 'QUAL_DELIVERY_COLUMN_PINS'
}
Exact $matrix.outputs.id $typed.contracts.id 'QUAL_DELIVERY_CONTRACT_SET'
$count=0
foreach($output in $matrix.outputs){
    $contract=@($typed.contracts|Where-Object id -CEQ $output.id)[0]
    $rules=@($oracle.contracts|Where-Object id -CEQ $output.id)[0]
    if(-not $output.positiveCase -or $output.columns.Count -ne $contract.columns.Count){throw 'QUAL_DELIVERY_COLUMN_SCOPE'}
    foreach($column in $output.columns){
        $expected=$contract.columns[$column.ordinal-1]
        $rule=$rules.columns[$column.ordinal-1]
        foreach($field in @('ordinal','name','type','precision','scale','nullable')){
            if($column[$field] -cne $expected[$field]){throw 'QUAL_DELIVERY_COLUMN_METADATA'}
        }
        if($column.rule -cne $rule.rule -or $column.origin -cne $rule.origin -or $column.example -cne $rule.example -or
            -not $column.comparator -or -not $column.typedOracle -or $column.cases.Count -lt 1){throw 'QUAL_DELIVERY_COLUMN_ORACLE'}
        $count++
    }
}
if($count -ne 673){throw 'QUAL_DELIVERY_COLUMN_COUNT'}
$responsibility=Read ($catalog+'matriz-responsabilidades.json')
$expectedNodes=@(@('CAP','FAT','INV','SIN','LOC','FRE','MAN','COL','COT','USUARIO','RASTER')|
    ForEach-Object {'INPUT_'+$_})+@(1..19|ForEach-Object {'SQL-{0:00}' -f $_})+@('MAT01','MAT02','MAT03','MAT04','MAT05')
Exact $responsibility.nodes.id $expectedNodes 'QUAL_DELIVERY_RESPONSIBILITY_SET'
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($node in $responsibility.nodes){
    foreach($dependency in $node.dependencies){if(-not $seen.Contains($dependency)){throw 'QUAL_DELIVERY_DAG'}}
    if(-not $seen.Add($node.id) -or -not $node.case -or -not $node.oracle -or $node.consumers.Count -lt 1){throw 'QUAL_DELIVERY_CONSUMER'}
}
function VerifySummary($s){
    foreach($key in @('passed','deliveryComplete','operationalPromotionAuthorized','realQualificationAccepted','heapPlateauProven')){
        if($s[$key] -isnot [bool]){throw 'QUAL_DELIVERY_SUMMARY'}
    }
    foreach($value in @($s.full.previous378Exact,$s.full.coveragePassed,$s.full.rollbackConfirmed,
            $s.package.byteIdentical,$s.package.sourceAndJarChecked)){
        if($value -isnot [bool]){throw 'QUAL_DELIVERY_SUMMARY'}
    }
    Exact $s.fronts @('A','B','C','D','E','F','G','H','I','J','K','L','M','N') 'QUAL_DELIVERY_SUMMARY'
    if(-not $s.passed -or -not $s.deliveryComplete -or $s.status -cne 'CONSTRUÇÃO_LOCAL_CONCLUÍDA' -or
        $s.full.failures -ne 0 -or $s.full.errors -ne 0 -or $s.full.unitSkipped -ne 4 -or $s.full.integrationSkipped -ne 0 -or
        $s.full.unitTests -lt 1974 -or $s.full.integrationTests -le 378 -or -not $s.full.previous378Exact -or
        -not $s.full.coveragePassed -or -not $s.full.rollbackConfirmed -or -not $s.package.byteIdentical -or
        -not $s.package.sourceAndJarChecked -or $s.package.dependencies -ne 9 -or $s.package.members -lt 172 -or
        $s.construction.before -ne 37 -or $s.construction.after -ne 39 -or $s.construction.denominator -ne 45 -or
        $s.construction.historicalAcceptances -ne 67 -or $s.construction.acceptanceDenominator -ne 115 -or
        $s.remoteCalls -ne 0 -or $s.newAcceptances -ne 0 -or $s.durableDomainRows -ne 0 -or
        $s.operationalPromotionAuthorized -or $s.realQualificationAccepted -or $s.heapPlateauProven){throw 'QUAL_DELIVERY_SUMMARY'}
    Exact $s.scale.completedScales @(4,16,32,16) 'QUAL_DELIVERY_SUMMARY'
    Exact $s.proofs.Keys @('regression','reproducibility','scales','packageGuards','extractedGuards','controlGuards','sourceBinding','staticChecks','plans','smokes') 'QUAL_DELIVERY_SUMMARY'
    if($s.guards.package -lt 25 -or $s.guards.extracted -lt 20 -or $s.guards.control -lt 8){throw 'QUAL_DELIVERY_SUMMARY'}
}
$guards=0
if(-not $Candidate){
    $summary=Read ($catalog+'verification-summary.json')
    VerifySummary $summary
    if($summary.status -cne $successor.status -or $matrix.state -cne 'QUALIFIED_LOCAL' -or
        $responsibility.state -cne 'QUALIFIED_LOCAL'){throw 'QUAL_DELIVERY_STATUS'}
    $quadro=Read ($catalog+'quadro-construcao.json')
    $old=Read 'docs/catalogos/macrobloco-analitico/quadro-construcao.json'
    Exact $quadro.units.id $old.units.id 'QUAL_DELIVERY_45_UNIVERSE'
    foreach($unit in $quadro.units){
        $previous=@($old.units|Where-Object id -CEQ $unit.id)[0]
        foreach($field in @('implemented','integrated','locallyVerified','realAcceptance')){
            if($unit.before[$field] -cne $previous.after[$field]){throw 'QUAL_DELIVERY_CONSTRUCTION_BEFORE'}
        }
        if($unit.after.realAcceptance -cne $previous.after.realAcceptance){throw 'QUAL_DELIVERY_NEW_ACCEPTANCE'}
    }
    if($quadro.units.Count -ne 45 -or @($quadro.units|Where-Object {$_.before.locallyVerified}).Count -ne 37 -or
        @($quadro.units|Where-Object {$_.after.locallyVerified}).Count -ne 39){throw 'QUAL_DELIVERY_CONSTRUCTION_COUNT'}
    Exact @($quadro.units|Where-Object {-not $_.before.locallyVerified -and $_.after.locallyVerified}|ForEach-Object id) @('V2-038','V2-039') 'QUAL_DELIVERY_CONSTRUCTION_DELTA'
    if($SelfTest){
        foreach($mutation in @(
            {param($s)$s.full.integrationSkipped=1}, {param($s)$s.full.previous378Exact=$false},
            {param($s)$s.full.coveragePassed=$false}, {param($s)$s.package.byteIdentical=$false},
            {param($s)$s.package.sourceAndJarChecked=$false}, {param($s)$s.heapPlateauProven=$true},
            {param($s)$s.remoteCalls=1}, {param($s)$s.operationalPromotionAuthorized=$true},
            {param($s)$s.construction.after=40}, {param($s)$s.passed='true'},
            {param($s)$s.guards.control=7}, {param($s)$s.fronts=@('A')}
        )){
            $copy=Read-QualificationJsonBytes ([Text.Encoding]::UTF8.GetBytes(($summary|ConvertTo-Json -Depth 24))) 8388608
            & $mutation $copy
            $reason='ACCEPTED';try{VerifySummary $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne 'QUAL_DELIVERY_SUMMARY'){throw ('QUAL_DELIVERY_GUARD_'+$reason)};$guards++
        }
    }
}
if($IncludePrivateEvidence){
    foreach($entry in $successor.manifest.evidence){Pin $entry}
    if(-not $Candidate){
        foreach($key in $summary.proofs.Keys){Pin $summary.proofs[$key]}
        $regression=Read $summary.proofs.regression.path
        if(-not $regression.passed -or -not $regression.previous378Exact -or -not $regression.coveragePassed -or
            $regression.unitTests -ne $summary.full.unitTests -or $regression.integrationTests -ne $summary.full.integrationTests){throw 'QUAL_DELIVERY_REGRESSION'}
        foreach($report in @($regression.reports)+@($regression.previousReports)){
            if((Hash $report.path) -cne $report.sha256){throw 'QUAL_DELIVERY_XML_DRIFT'}
        }
        $package=Read ($round+$summary.package.first+'/result.json')
        $second=Read ($round+$summary.package.second+'/result.json')
        if($package.archiveSha256 -cne $second.archiveSha256 -or $package.manifestSha256 -cne $second.manifestSha256 -or
            $package.revision -cne $summary.package.revision -or $package.archiveSha256 -cne $summary.package.archiveSha256 -or
            (Hash ($round+$summary.package.first+'/qualification.zip')) -cne $package.archiveSha256 -or
            (Hash ($round+$summary.package.second+'/qualification.zip')) -cne $package.archiveSha256){throw 'QUAL_DELIVERY_REPRODUCIBILITY'}
        $sources=Read ($round+$summary.package.first+'/qualified-source-inventory.json')
        foreach($entry in $sources){if((Hash $entry.path) -cne $entry.sha256){throw 'QUAL_DELIVERY_SOURCE_DRIFT'}}
        $smokes=Read $summary.proofs.smokes.path
        foreach($entry in $smokes.attempts){
            $smoke=Read ($round+$entry.directory+'/result.json')
            if($smoke.state -cne 'PASS_LOCAL' -or $smoke.manifestSha256 -cne $package.manifestSha256 -or
                $smoke.archiveSha256 -cne $package.archiveSha256 -or $smoke.sourceWorkspace -or
                @($smoke.commands|Where-Object {$_.exit -ne 0 -or $_.sourceWorkspace}).Count -ne 0){throw 'QUAL_DELIVERY_SMOKE'}
            Exact $smoke.commands.command @('inspect','plan','run','status','resume','compare') 'QUAL_DELIVERY_SMOKE_COMMANDS'
        }
        foreach($proof in @('reproducibility','scales','packageGuards','extractedGuards','controlGuards','sourceBinding','staticChecks','plans','smokes')){
            $document=Read $summary.proofs[$proof].path
            if($document.passed -isnot [bool] -or -not $document.passed){throw ('QUAL_DELIVERY_PROOF_'+$proof)}
        }
    }
}
[ordered]@{passed=$true;candidate=[bool]$Candidate;status=$successor.status;columns=$count;contracts=19;
    responsibilities=$seen.Count;successionGuards=$successor.guards;summaryGuards=$guards;
    private=[bool]$IncludePrivateEvidence;remoteCalls=0;newAcceptances=0}|ConvertTo-Json

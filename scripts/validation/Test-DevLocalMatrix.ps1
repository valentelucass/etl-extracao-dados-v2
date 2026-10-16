[CmdletBinding()]
param([string]$Matrix = 'docs/catalogos/dev-local/matriz-p01-p33.json')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..')).TrimEnd('\', '/')
$prefix = $repository + [IO.Path]::DirectorySeparatorChar
$utf8 = [Text.UTF8Encoding]::new($false, $true)

function Resolve-DeliveryFile([string]$Relative) {
    if ([string]::IsNullOrWhiteSpace($Relative) -or $Relative.Contains('\') -or
        [IO.Path]::IsPathRooted($Relative) -or $Relative.Contains(':') -or
        @($Relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count -gt 0) {
        throw 'DEV_MATRIX_PATH'
    }
    $path = [IO.Path]::GetFullPath((Join-Path $repository $Relative))
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'DEV_MATRIX_ESCAPE' }
    $item = Get-Item -LiteralPath $path
    if ($item -isnot [IO.FileInfo] -or $item.Length -gt 32MB) { throw 'DEV_MATRIX_FILE' }
    $node = $item
    while ($null -ne $node -and $node.FullName -ne $repository) {
        if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'DEV_MATRIX_LINK' }
        $node = if ($node -is [IO.FileInfo]) { $node.Directory } else { $node.Parent }
    }
    return $path
}

function Read-DeliveryJson([string]$Relative) {
    $path = Resolve-DeliveryFile $Relative
    if ((Get-Item -LiteralPath $path).Length -gt 2MB) { throw 'DEV_MATRIX_JSON_BOUND' }
    return ($utf8.GetString([IO.File]::ReadAllBytes($path)) | ConvertFrom-Json -AsHashtable)
}

function Verify-Pin($Pin) {
    if ($Pin.path -isnot [string] -or $Pin.sha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'DEV_MATRIX_PIN' }
    $path = Resolve-DeliveryFile $Pin.path
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Pin.sha256) {
        throw 'DEV_MATRIX_BYTE_DRIFT'
    }
}

$plan = Read-DeliveryJson 'docs/catalogos/preparacao-trilha/plano.json'
$matrixDoc = Read-DeliveryJson $Matrix
if ($matrixDoc.schemaVersion -ne 1 -or $matrixDoc.request -cne 'HERMES_b77c5029cfbd' -or
    $matrixDoc.state -cne 'DEV_LOCAL_COMPLETED' -or
    $matrixDoc.scope -cne 'DEV_LOCAL_SYNTHETIC_NO_SQL_NO_EXTERNAL' -or
    $matrixDoc.historicalIntegralCounters.newIntegralAcceptances -ne 0) { throw 'DEV_MATRIX_SCOPE' }
Verify-Pin $matrixDoc.canonicalPlan
$expectedStageIds = @(1..33 | ForEach-Object { 'P{0:D2}' -f $_ })
if (($matrixDoc.stages.id -join '|') -cne ($expectedStageIds -join '|')) { throw 'DEV_MATRIX_STAGE_SET' }
$expectedUnits = @($plan.stages.units | Sort-Object -Unique)
if ($expectedUnits.Count -ne 48 -or
    ($matrixDoc.units.id -join '|') -cne ($expectedUnits -join '|')) { throw 'DEV_MATRIX_UNIT_SET' }
$proofs = @{}
foreach ($proof in $matrixDoc.proofs) {
    if ($proof.id -cnotmatch '^[A-Z0-9_]{1,80}$' -or $proofs.ContainsKey($proof.id) -or
        $proof.status -cne 'PASS' -or $proof.layer -notin @('OFFLINE', 'PACKAGED_CLI', 'FILE_READBACK')) {
        throw 'DEV_MATRIX_PROOF'
    }
    Verify-Pin $proof.artifact
    $proofs.Add($proof.id, $proof)
}
if ($proofs.Count -lt 5 -or @($matrixDoc.proofs | Where-Object layer -EQ 'PACKAGED_CLI').Count -eq 0) {
    throw 'DEV_MATRIX_ACTUAL_CLI_REQUIRED'
}
$validStatuses = @('VERIFICADO_LOCAL', 'EXCLUIDO_DEV_LOCAL')
$occurrences = 0
foreach ($stage in $matrixDoc.stages) {
    $canonical = @($plan.stages | Where-Object id -CEQ $stage.id)[0]
    if (($stage.canonicalUnits -join '|') -cne ($canonical.units -join '|') -or
        $stage.canonicalExit -cne $canonical.exit -or
        $stage.integralAcceptanceGranted -isnot [bool] -or $stage.integralAcceptanceGranted -or
        $stage.status -cnotin $validStatuses) { throw 'DEV_MATRIX_CANONICAL_DRIFT' }
    $occurrences += $stage.canonicalUnits.Count
    if ($stage.status -ceq 'VERIFICADO_LOCAL') {
        if ([string]::IsNullOrWhiteSpace($stage.localCriterion) -or $stage.evidence.Count -eq 0) {
            throw 'DEV_MATRIX_LOCAL_PROOF_REQUIRED'
        }
        if (@($matrixDoc.units | Where-Object { $_.id -cin $stage.canonicalUnits -and
                    $_.status -ceq 'VERIFICADO_LOCAL' }).Count -eq 0) {
            throw 'DEV_MATRIX_STAGE_LOCAL_UNIT_REQUIRED'
        }
        foreach ($proofId in $stage.evidence) {
            if (-not $proofs.ContainsKey($proofId)) { throw 'DEV_MATRIX_UNKNOWN_PROOF' }
        }
    } else {
        foreach ($field in @('exclusion', 'impact', 'futureTrack')) {
            if ([string]::IsNullOrWhiteSpace($stage[$field])) { throw 'DEV_MATRIX_EXCLUSION_REASON' }
        }
    }
}
foreach ($unit in $matrixDoc.units) {
    $stages = @($matrixDoc.stages | Where-Object { $unit.id -cin $_.canonicalUnits })
    if (($unit.stages -join '|') -cne ($stages.id -join '|') -or
        $unit.integralAcceptanceGranted -isnot [bool] -or $unit.integralAcceptanceGranted -or
        $unit.status -cnotin $validStatuses) { throw 'DEV_MATRIX_UNIT_LINK' }
    if ($unit.status -ceq 'VERIFICADO_LOCAL') {
        if ($unit.evidence.Count -eq 0 -or [string]::IsNullOrWhiteSpace($unit.localCriterion)) {
            throw 'DEV_MATRIX_UNIT_PROOF_REQUIRED'
        }
        foreach ($proofId in $unit.evidence) {
            if (-not $proofs.ContainsKey($proofId)) { throw 'DEV_MATRIX_UNKNOWN_PROOF' }
        }
    } else {
        foreach ($field in @('exclusion', 'impact', 'futureTrack')) {
            if ([string]::IsNullOrWhiteSpace($unit[$field])) { throw 'DEV_MATRIX_UNIT_EXCLUSION' }
        }
    }
}
foreach ($pin in $matrixDoc.deliveryPins) { Verify-Pin $pin }
if ($matrixDoc.deliveryPins.Count -lt 5 -or $matrixDoc.essentialDependencies.Count -ne 0) {
    throw 'DEV_MATRIX_DELIVERY_NOT_COMPLETE'
}
$verifiedStages = @($matrixDoc.stages | Where-Object status -CEQ 'VERIFICADO_LOCAL').Count
$excludedStages = 33 - $verifiedStages
$verifiedUnits = @($matrixDoc.units | Where-Object status -CEQ 'VERIFICADO_LOCAL').Count
$excludedUnits = 48 - $verifiedUnits
$counts = $matrixDoc.counts
if ($counts.stages -ne 33 -or $counts.uniqueUnits -ne 48 -or $counts.unitOccurrences -ne $occurrences -or
    $counts.stagesByStatus.VERIFICADO_LOCAL -ne $verifiedStages -or
    $counts.stagesByStatus.EXCLUIDO_DEV_LOCAL -ne $excludedStages -or
    $counts.unitsByStatus.VERIFICADO_LOCAL -ne $verifiedUnits -or
    $counts.unitsByStatus.EXCLUIDO_DEV_LOCAL -ne $excludedUnits) { throw 'DEV_MATRIX_COUNT_DRIFT' }
Write-Output "DEV_LOCAL_MATRIX PASS stages=33 verified=$verifiedStages excluded=$excludedStages uniqueUnits=48 verifiedUnits=$verifiedUnits excludedUnits=$excludedUnits occurrences=$occurrences essential=0"

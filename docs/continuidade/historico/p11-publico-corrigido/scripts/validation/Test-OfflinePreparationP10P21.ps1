#Requires -Version 7.5
[CmdletBinding()]
param([switch]$SelfTest, [switch]$IncludeEvidence)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$catalogPath = 'docs/catalogos/preparacao-offline-p10-p21/matriz.json'
$round = 'target/preparacao-offline-p10-p21-20260921-01'
function Read-Local([string]$relative) {
    if ($relative -notmatch '^(docs/|database/|src/|scripts/|\.github/|target/preparacao-offline-p10-p21-20260921-01/|pom\.xml$|CONTRIBUTING\.md$|SECURITY\.md$)' -or
        $relative -match '\.\.|\\|(?i)(\.env|\.(pem|key|pfx|p12|crt|cer)$)') { throw 'PREP_PATH' }
    $file = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'PREP_MISSING_ARTIFACT' }
    $item = Get-Item -LiteralPath $file
    if ($item.Length -gt 8MB -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'PREP_FILE_BOUNDARY' }
    return $utf8.GetString([IO.File]::ReadAllBytes($file)).TrimStart([char]0xFEFF)
}
function Exact($actual, $expected, [string]$code) {
    if (@($actual).Count -ne @($expected).Count -or
        (@($actual | Sort-Object) -join '|') -cne (@($expected | Sort-Object) -join '|')) { throw $code }
}
$plan = (Read-Local 'docs/catalogos/preparacao-trilha/plano.json') | ConvertFrom-Json -Depth 60
function Check($c, [switch]$Pins) {
    if ($c.version -ne 1 -or $c.scope -cne 'PREPARACAO_OFFLINE_P10_P11_P12_P14_P15_P21' -or
        $c.authority -cne 'STATES.md' -or $c.construction -cne '39/45' -or $c.acceptances -cne '67/115' -or
        $c.newAcceptances -ne 0 -or $c.externalExecutionAuthorized -cne $false -or
        $c.networkCalls -ne 0 -or $c.databaseCalls -ne 0 -or $c.secretReads -ne 0) { throw 'PREP_SCOPE' }
    Exact $c.stages.id @('P10','P11','P12','P14','P15','P21') 'PREP_STAGES'
    $requirements = @()
    foreach ($s in $c.stages) {
        $p = @($plan.stages | Where-Object id -CEQ $s.id)[0]
        Exact $s.gates $p.inputs 'PREP_GATES'
        Exact $s.units $p.units 'PREP_UNITS'
        Exact $s.dependsOn $p.dependsOn 'PREP_DEPENDENCIES'
        Exact @($s.conditional | ForEach-Object { $_.id + '|' + $_.when }) @($p.conditional | ForEach-Object { $_.id + '|' + $_.when }) 'PREP_CONDITIONAL'
        if ($s.localState -cne 'TESTADO_NA_CAMADA_OFFLINE' -or $s.externalState -cne 'BLOQUEADO_POR_INPUT' -or
            $s.externalExecutionAuthorized -cne $false -or $s.requirements.Count -lt 1 -or $s.artifacts.Count -lt 1) { throw 'PREP_STAGE_STATE' }
        foreach ($r in $s.requirements) {
            $expectedRole = @($plan.inputs | Where-Object id -CEQ $r.gate)
            if ($r.gate -cnotin $s.gates -or $expectedRole.Count -ne 1 -or
                $r.ownerRole -cne $expectedRole[0].role -or $null -ne $r.nominalOwner -or
                $r.evidenceReceived -cne $false -or $r.status -cne 'BLOQUEADO_POR_INPUT' -or
                [string]::IsNullOrWhiteSpace($r.requirement) -or [string]::IsNullOrWhiteSpace($r.source)) { throw 'PREP_REQUIREMENT' }
            if (-not (Test-Path -LiteralPath (Join-Path $root $r.source) -PathType Leaf)) { throw 'PREP_REQUIREMENT_SOURCE' }
            $requirements += $r.id
        }
    }
    Exact $requirements @('G02-PUBLICACAO','G02-CI','FEED-BASELINE','FEED-ACHADOS','G05-AMBIENTE','G05-RETENCAO','G05-RECUPERACAO','G03-10633','G03-8636','G03-4924','G03-6392','G03-RASTER','G04-SEMANTICA','G04-REFERENCIAS','G04-RATIFICACAO','G04-DIMENSOES','G04-FROTA') 'PREP_REQUIREMENT_SET'
    $sources = @{FILIAIS=@('FRE','MAN','CAP','FAT');CLIENTES=@('COL','FRE','FAT');VEICULOS=@('MAN','V2-035c');MOTORISTAS=@('MAN','V2-035c');PLANOCONTAS=@('CAP');USUARIOS=@('USER')}
    Exact $c.dimensions.id @($sources.Keys) 'PREP_DIMENSIONS'
    foreach ($d in $c.dimensions) {
        Exact $d.sources $sources[$d.id] 'PREP_DIMENSION_DAG'
        if ($d.publication -cne 'P25' -or $d.state -cne 'BLOQUEADO_POR_INPUT' -or $d.syntheticBindingsAreIdentityProof -cne $false) { throw 'PREP_DIMENSION_AUTHORITY' }
    }
    Exact $c.referenceFamilies @('CALENDAR','PICK_STATUS','BRANCH_OPERATIONS','OWNED_FLEET','BRANCH_ATTRIBUTION','CUBAGE_EXCLUSION','LOGISTICS_REGION','QUOTE_TARIFF') 'PREP_REFERENCE_FAMILIES'
    Exact $c.artifacts.path @(@($c.stages.artifacts) + 'docs/catalogos/preparacao-trilha/plano.json' | Sort-Object -Unique) 'PREP_ARTIFACT_SET'
    if (@($c.historicalDrift).Count -ne 1 -or
        $c.historicalDrift[0].path -cne 'src/main/java/br/com/esl/etl/v2/modulos/manifestos/aplicacao/ManifestoDataExportRecordMapper.java' -or
        $c.historicalDrift[0].historical -cne 'f99ad6764e679041b9616b72b958c3f2034e96b24e8a97d79bc215ba92611384' -or
        $c.historicalDrift[0].current -cne 'f3865ac8acc9d7edcd4eacbbad5aa57ae966b929a2059242095722b12efb04d6') { throw 'PREP_HISTORY' }
    if ($Pins) {
        foreach ($a in $c.artifacts) {
            $null = Read-Local $a.path
            if ($a.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
                (Get-FileHash -LiteralPath (Join-Path $root $a.path)).Hash.ToLowerInvariant() -cne $a.sha256) { throw 'PREP_PIN' }
        }
    }
}
$catalog = (Read-Local $catalogPath) | ConvertFrom-Json -Depth 60
Check $catalog -Pins
$guards = 0
if ($SelfTest) {
    $cases = @(
        @('PREP_SCOPE', {param($c) $c.externalExecutionAuthorized=$true}),
        @('PREP_SCOPE', {param($c) $c.acceptances='68/115'}),
        @('PREP_STAGES', {param($c) $c.stages=@($c.stages | Where-Object id -CNE 'P14')}),
        @('PREP_GATES', {param($c) $c.stages[0].gates=@('G01')}),
        @('PREP_DEPENDENCIES', {param($c) $c.stages[0].dependsOn=@('P09')}),
        @('PREP_CONDITIONAL', {param($c) $c.stages[4].conditional=@()}),
        @('PREP_REQUIREMENT', {param($c) $c.stages[0].requirements[0].nominalOwner='invented'}),
        @('PREP_REQUIREMENT', {param($c) $c.stages[0].requirements[0].evidenceReceived=$true}),
        @('PREP_REQUIREMENT', {param($c) $c.stages[0].requirements[0].ownerRole='another gate owner'}),
        @('PREP_DIMENSION_DAG', {param($c) $c.dimensions[2].sources=@('MAN')}),
        @('PREP_DIMENSION_AUTHORITY', {param($c) $c.dimensions[3].syntheticBindingsAreIdentityProof=$true}),
        @('PREP_HISTORY', {param($c) $c.historicalDrift=@()}),
        @('PREP_PIN', {param($c) $c.artifacts[0].sha256='0'*64})
    )
    foreach ($case in $cases) {
        $copy = $catalog | ConvertTo-Json -Depth 60 | ConvertFrom-Json -Depth 60
        & $case[1] $copy
        $observed = 'ACCEPTED'
        try { Check $copy -Pins } catch { $observed=$_.Exception.Message }
        if ($observed -cne $case[0]) { throw "PREP_GUARD_$guards" }
        $guards++
    }
}
if ($IncludeEvidence) {
    foreach ($id in @($catalog.stages.validators | Sort-Object -Unique)) {
        $receipt = (Read-Local "$round/$id.result.json") | ConvertFrom-Json
        if ($receipt.exitCode -ne 0) { throw 'PREP_LOCAL_VALIDATION_FAILED' }
    }
    $java = (Read-Local "$round/java.result.json") | ConvertFrom-Json
    if ($java.exitCode -ne 0) { throw 'PREP_JAVA_FAILED' }
    $boundaries = (Read-Local "$round/java-boundaries.result.json") | ConvertFrom-Json
    if ($boundaries.exitCode -ne 0) { throw 'PREP_JAVA_BOUNDARIES_FAILED' }
    $expectedClasses = @('ManifestoDataExportRecordMapperTest','ScopedSourceIdentityTest','IdentityConflictPolicyTest','FirstWaveIdentityCatalogTest','GovernedReferencesSqlContractTest','UsuariosDimensionCurrentSqlContractTest','StagingLifecycleSqlContractTest','IntegralArtifactBoundaryTest','ArchitectureRulesTest','ManifestoStageBatchTest')
    $observedClasses = @()
    $tests = 0
    foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $root "$round/build/target/surefire-reports") -Filter 'TEST-*.xml' -File)) {
        $xml = [xml] (Read-Local "$round/build/target/surefire-reports/$($file.Name)")
        if ([int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0 -or [int]$xml.testsuite.tests -lt 1) { throw 'PREP_JAVA_XML_FAILED' }
        $observedClasses += ($xml.testsuite.name -split '\.')[-1]
        $tests += [int]$xml.testsuite.tests
    }
    Exact $observedClasses $expectedClasses 'PREP_JAVA_CLASS_SET'
    if ($tests -ne 48) { throw 'PREP_JAVA_COUNT' }
    $historical = (Read-Local "$round/fleet-decision.result.json") | ConvertFrom-Json
    if ($historical.exitCode -eq 0) { throw 'PREP_HISTORICAL_FAILURE_NOT_PRESERVED' }
    $record = (Read-Local 'docs/catalogos/preparacao-offline-p10-p21/validacoes.json') | ConvertFrom-Json -Depth 30
    foreach ($a in $record.receipts) {
        $null = Read-Local $a.path
        if ((Get-FileHash -LiteralPath (Join-Path $root $a.path)).Hash.ToLowerInvariant() -cne $a.sha256) { throw 'PREP_RECEIPT_PIN' }
    }
}
[ordered]@{status='OFFLINE_PREPARATION_VALID';stages=6;requirements=17;dimensions=6;guards=$guards;privateEvidence=[bool]$IncludeEvidence;externalState='BLOQUEADO_POR_INPUT';newAcceptances=0} | ConvertTo-Json

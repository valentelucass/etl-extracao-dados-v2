[CmdletBinding()]
param(
    [switch]$RunSqlExercise
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\coletas-shadow-vertical.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\coletas-shadow-vertical.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V010__create_coletas_shadow_vertical.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\038_validate_coletas_shadow_vertical.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\039_exercise_coletas_shadow_vertical_rollback.sql'

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Test-OrdinalContains {
    param([string]$Text, [string]$Value)
    return $Text.IndexOf($Value, [StringComparison]::Ordinal) -ge 0
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Require-True ($manifest.manifestVersion -eq 1 -and $manifest.roadmapTask -ceq 'V2-010') `
    'O manifesto deve pertencer à execução V2-010.'
Require-True ($manifest.localState -ceq 'IMPLEMENTED_IN_SHADOW') `
    'Coletas deve permanecer explicitamente em shadow.'
Require-True ($manifest.sourceContract.contractId -ceq 'dataexport-6908' `
        -and $manifest.sourceContract.partition -ceq 'request_date' `
        -and $manifest.sourceContract.supplementaryLateDataWatermark -ceq 'FORBIDDEN') `
    'O contrato incremental de Coletas diverge da decisão V01.'
Require-True ($manifest.identity.sourceKey -ceq 'INTEGER_TYPE_TAGGED_ID' `
        -and $manifest.identity.businessAlias -ceq 'VERSIONED_SEQUENCE_CODE_NEVER_TECHNICAL_KEY' `
        -and $manifest.identity.canonicalKey -ceq 'BIGINT_IDENTITY') `
    'A identidade de Coletas diverge da decisão congelada.'
Require-True ((@(Compare-Object @('ABSENT','NULL','VALUE') @($manifest.attributes.presence) -CaseSensitive).Count -eq 0)) `
    'A presença tri-state de Coletas diverge.'
Require-True ($manifest.attributes.statusCatalog -ceq 'coletas-status-v1' `
        -and $manifest.attributes.terminalWinsOverOpenDespiteRetrogradeTimestamp -eq $true `
        -and $manifest.attributes.terminalNeverRegressesToOpen -eq $true `
        -and $manifest.attributes.terminalityDecisionOwner -ceq `
            'TYPED_WRAPPER_COL03_GENERIC_KERNEL_MAY_REPORT_STALE') `
    'O catálogo ou a terminalidade de Coletas diverge.'
Require-True ($manifest.relationships.inferredJoin -ceq 'FORBIDDEN' `
        -and $manifest.relationships.manifestoToColeta -ceq 'BLOCKED_UNTIL_V2_046A' `
        -and $manifest.relationships.coletaToFrete -ceq 'BLOCKED_UNTIL_V2_046B') `
    'A migration não pode antecipar relações canônicas.'
Require-True ($manifest.absence.sweep -ceq 'PROHIBITED_UNTIL_V2_013' `
        -and $manifest.absence.deactivation -ceq 'PROHIBITED_UNTIL_V2_013') `
    'Ausência não pode ativar sweep ou desativação em V02.'
Require-True ($manifest.runtime.publicationObjects -ceq 'PROHIBITED' `
        -and $manifest.runtime.cutover -ceq 'PROHIBITED' `
        -and $manifest.runtime.remoteCallsUsedAsEvidence -eq $false) `
    'O manifesto extrapola a autorização shadow-only.'

$migration = Get-Content -LiteralPath $migrationPath -Raw
foreach ($token in @(
        'CREATE TABLE stg.coleta_record', 'CREATE TABLE core.coleta',
        'CREATE TABLE ref.coleta_sequence_code_alias',
        'CREATE TABLE recon.coleta_root_presence_observation',
        'CREATE OR ALTER PROCEDURE stg.usp_stage_coleta_record',
        'CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_coletas',
        '@input_record_ordinal NOT BETWEEN 1 AND 100', 'coletas-status-v1',
        'BLOCKED_NO_COMPLETENESS_PROOF', 'NOT_EVALUATED',
        'Alteração de alias exige evidência aprovada.',
        'current_record.terminal = 0 AND typed.terminal = 1',
        'current_record.terminal = 1 AND typed.terminal = 0',
        'usp_publish_v2_procedure_grant N''stg'', N''usp_stage_coleta_record''',
        'N''core'', N''usp_apply_reconcile_publish_coletas'''
    )) {
    Require-True (Test-OrdinalContains $migration $token) "A migration não cobre $token."
}
$retroDisposition = $migration.IndexOf(
    "WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN N'UPDATED'",
    [StringComparison]::Ordinal)
$staleDisposition = $migration.IndexOf(
    "WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN N'STALE_NO_OP'",
    [StringComparison]::Ordinal)
$retroApply = $migration.IndexOf(
    'WHEN current_record.terminal = 0 AND typed.terminal = 1 THEN 1',
    [StringComparison]::Ordinal)
$staleApply = $migration.IndexOf(
    "WHEN generic_application.application_disposition = N'STALE_NO_OP' THEN 0",
    [StringComparison]::Ordinal)
Require-True ($retroDisposition -ge 0 -and $staleDisposition -ge 0 `
        -and $retroDisposition -lt $staleDisposition `
        -and $retroApply -ge 0 -and $staleApply -ge 0 -and $retroApply -lt $staleApply) `
    'Os dois CASE do plano tipado devem aplicar terminal retroativo antes de stale.'
foreach ($forbidden in @('CREATE VIEW pub.', 'CREATE TABLE pub.', 'DEACTIVATED', 'TRUNCATE TABLE', 'MERGE ')) {
    Require-True (-not (Test-OrdinalContains $migration $forbidden)) `
        "A migration contém o token proibido $forbidden."
}

$baseline = Get-Content -LiteralPath $baselinePath -Raw
Require-True (Test-OrdinalContains $baseline ':r "..\migrations\V010__create_coletas_shadow_vertical.sql"') `
    'O baseline não inclui V010.'
$validator = Get-Content -LiteralPath $validatorPath -Raw
Require-True (Test-OrdinalContains $validator 'Coletas V2 em sombra validadas com sucesso.') `
    'O validator estrutural de Coletas está ausente.'
$exercise = Get-Content -LiteralPath $exercisePath -Raw
Require-True ((Test-OrdinalContains $exercise 'V010__create_coletas_shadow_vertical.sql') `
        -and (Test-OrdinalContains $exercise 'COL-03: terminal retroativo') `
        -and (Test-OrdinalContains $exercise 'last_changed_execution_id = @retro_terminal') `
        -and (Test-OrdinalContains $exercise 'execution_id = @retro_terminal AND source_key = N''INTEGER:6908''') `
        -and (Test-OrdinalContains $exercise 'application_disposition = N''STALE_NO_OP''') `
        -and (Test-OrdinalContains $exercise 'coletas-terminal-never-regresses') `
        -and (Test-OrdinalContains $exercise 'coletas-open-t1-retroactive') `
        -and (Test-OrdinalContains $exercise 'coletas-replay-terminal') `
        -and (Test-OrdinalContains $exercise 'EQUAL_FRESHNESS_CONFLICT') `
        -and (Test-OrdinalContains $exercise 'coletas-partial-sidecar') `
        -and (Test-OrdinalContains $exercise 'ROLLBACK TRANSACTION')) `
    'O exercício de Coletas não é rollback-only.'

$actual = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$expected = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
Require-True ($actual -eq $expected) 'O fingerprint do manifesto de Coletas está desatualizado.'

if ($RunSqlExercise) {
    $targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
    $sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
    $targetVerification = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d master -b -h -1 -W `
            -Q "SET NOCOUNT ON; IF DB_ID(N'$targetDatabase') IS NULL THROW 51882, N'Alvo local de sombra ausente.', 1; SELECT name FROM sys.databases WHERE name = N'$targetDatabase';"
    )
    Require-True ($LASTEXITCODE -eq 0 `
            -and @($targetVerification | Where-Object { $_.Trim() -ceq $targetDatabase }).Count -eq 1) `
        'Não foi possível confirmar o alvo local autorizado para o exercício de Coletas.'

    Push-Location (Split-Path $exercisePath -Parent)
    try {
        $exerciseOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -b -r 1 `
                -h -1 -W -s '|' -w 65535 -i (Split-Path $exercisePath -Leaf) 2>&1 |
                ForEach-Object { [string]$_ }
        )
        $exerciseExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Require-True ($exerciseExitCode -eq 0) `
        'O exercício SQL rollback-only de Coletas falhou.'

    $typedRows = @(
        $exerciseOutput |
            ForEach-Object { (($_ -replace '\s*\|\s*', '|').Trim()) } |
            Where-Object { $_ -cmatch '^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\|' }
    )
    $expectedTypedPrefixes = @(
        '00000000-0000-0000-0000-000000001002|1|1|0|0|0|0|',
        '00000000-0000-0000-0000-000000001003|1|0|1|0|0|0|',
        '00000000-0000-0000-0000-000000001004|1|0|0|0|1|0|',
        '00000000-0000-0000-0000-000000001005|1|0|0|0|1|1|',
        '00000000-0000-0000-0000-000000001006|1|1|0|0|0|0|',
        '00000000-0000-0000-0000-000000001007|1|0|0|0|1|1|'
    )
    foreach ($prefix in $expectedTypedPrefixes) {
        Require-True (@($typedRows | Where-Object { $_.StartsWith($prefix, [StringComparison]::Ordinal) }).Count -eq 1) `
            "O result set tipado de Coletas não contém exatamente uma linha esperada: $prefix"
    }
    Require-True (@($exerciseOutput | Where-Object {
                $_ -ceq 'Coletas V2-010/V2-015e: COL-03, replay, stale, conflito e tentativa parcial validados; rollback integral concluído.'
            }).Count -eq 1) `
        'O exercício de Coletas não confirmou o rollback integral.'

    Write-Output 'Result sets tipados de Coletas capturados e validados no alvo local com rollback integral.'
}

Write-Output 'Manifesto e artefatos da vertical Coletas V2-010 validados com sucesso.'

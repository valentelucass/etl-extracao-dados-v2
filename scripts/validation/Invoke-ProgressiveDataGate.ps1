[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop

& (Join-Path $PSScriptRoot 'Test-ProgressiveDataGate.ps1')
& (Join-Path $PSScriptRoot 'Test-AtomicPublicationConcurrency.ps1')
& (Join-Path $PSScriptRoot 'Test-UsuariosCurrentHistoryConcurrency.ps1')
& (Join-Path $PSScriptRoot 'Test-StagingLifecycleConcurrency.ps1')
& (Join-Path $PSScriptRoot 'Test-StagingLifecycleShowplan.ps1')
& (Join-Path $PSScriptRoot 'Test-ObservabilityDataQualityShowplan.ps1')
& (Join-Path $PSScriptRoot 'Test-UsuariosCurrentHistoryShowplan.ps1')
& (Join-Path $PSScriptRoot 'Test-GovernedReferencesConcurrency.ps1')
& (Join-Path $PSScriptRoot 'Test-GovernedReferencesShowplan.ps1')
& (Join-Path $PSScriptRoot 'Test-UsuariosDimensionCurrentShowplan.ps1')

$targetVerification = @(
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d master -b -h -1 -W `
        -Q "SET NOCOUNT ON; IF DB_ID(N'$targetDatabase') IS NULL THROW 51342, N'Alvo local de sombra ausente.', 1; SELECT name FROM sys.databases WHERE name = N'$targetDatabase';"
)
if ($LASTEXITCODE -ne 0 -or @($targetVerification | Where-Object { $_.Trim() -eq $targetDatabase }).Count -ne 1) {
    throw 'Não foi possível confirmar o alvo local autorizado para o gate progressivo.'
}

Push-Location (Join-Path $repositoryRoot 'database\validation')
try {
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '006_exercise_progressive_data_gate_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício SQL rollback-only do gate progressivo falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '008_exercise_staging_promotion_kernel_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício SQL rollback-only do kernel de staging/promoção falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '010_exercise_atomic_publication_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício SQL rollback-only do protocolo atômico de publicação falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '011_exercise_contract_permit_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício SQL rollback-only do permit de contrato/configuração falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '013_exercise_staging_lifecycle_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício SQL rollback-only do lifecycle de staging falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '014_exercise_staging_policy_ratification_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo da ratificação de retenção falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '015_exercise_staging_lifecycle_migrator_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'A prova rollback-only da role migrator no lifecycle falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '016_exercise_staging_legal_hold_integrity_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo do ledger de legal hold falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '018_exercise_staging_fingerprint_ascii_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo da gramática ASCII de fingerprint falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '019_exercise_staging_legal_hold_retry_integrity_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo do retry de legal hold falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '020_exercise_staging_terminal_ledger_integrity_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo da prova terminal de staging falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '022_exercise_observability_data_quality_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício rollback-only de observabilidade e Data Quality falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '023_exercise_observability_data_quality_migrator_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'A prova rollback-only da role migrator para observabilidade e Data Quality falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '024_exercise_data_quality_thresholds_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício rollback-only dos thresholds de Data Quality falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '027_exercise_usuarios_current_history_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício rollback-only de Usuários/current/history falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '029_exercise_usuarios_late_sidecar_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício negativo do sidecar tardio de Usuários falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '031_exercise_governed_references_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício rollback-only de referências governadas falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '032_exercise_governed_references_migrator_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'A prova rollback-only da role migrator para referências falhou.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '033_exercise_governed_references_negative_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'Os casos negativos isolados de referências governadas falharam.'
    }
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -i '036_exercise_usuarios_dimension_current_rollback.sql' -b
    if ($LASTEXITCODE -ne 0) {
        throw 'O exercício rollback-only da dimensão current de Usuários falhou.'
    }
} finally {
    Pop-Location
}

Write-Output 'Gate progressivo, kernel, publicação, permit, lifecycle, Usuários current/dimensão, referências, concorrência, negativos isolados e SHOWPLAN executados no alvo local autorizado e revertidos integralmente.'

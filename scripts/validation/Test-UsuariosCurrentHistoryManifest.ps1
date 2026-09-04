[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\usuarios-current-history.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\usuarios-current-history.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V007__create_usuarios_current_history.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\026_validate_usuarios_current_history.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\027_exercise_usuarios_current_history_rollback.sql'
$showplanExercisePath = Join-Path $repositoryRoot 'database\validation\028_validate_usuarios_current_history_showplan.sql'
$lateSidecarExercisePath = Join-Path $repositoryRoot 'database\validation\029_exercise_usuarios_late_sidecar_rollback.sql'
$concurrencyGatePath = Join-Path $repositoryRoot 'scripts\validation\Test-UsuariosCurrentHistoryConcurrency.ps1'
$showplanGatePath = Join-Path $repositoryRoot 'scripts\validation\Test-UsuariosCurrentHistoryShowplan.ps1'

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Test-OrdinalContains {
    param([string]$Text, [string]$Value)
    return $Text.IndexOf($Value, [StringComparison]::Ordinal) -ge 0
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
Require-True ($manifest.manifestVersion -eq 3) 'A versão do manifesto de Usuários deve ser 3.'
Require-True ($manifest.roadmapTask -ceq 'V2-033') 'O manifesto deve pertencer a V2-033.'
Require-True ($manifest.localState -ceq 'IMPLEMENTED_IN_SHADOW') `
    'Usuários deve permanecer explicitamente em shadow.'
Require-True ($manifest.migration -ceq 'V007__create_usuarios_current_history.sql') `
    'A migration de Usuários diverge.'
Require-True ($manifest.sourceContract.operation -ceq 'USERS_SNAPSHOT') `
    'A operação GraphQL de Usuários diverge.'
Require-True ($manifest.sourceContract.root -ceq 'individual' `
        -and $manifest.sourceContract.filter -ceq 'enabled=true') `
    'A raiz/filtro GraphQL de Usuários diverge.'
Require-True (@(Compare-Object @('id', 'name') @($manifest.sourceContract.fields) `
            -CaseSensitive).Count -eq 0) `
    'Somente id/name são permitidos no contrato GraphQL de Usuários.'
Require-True ($manifest.sourceContract.maximumPageSize -eq 20 `
        -and $manifest.sourceContract.maximumInFlightPages -eq 1) `
    'Os limites de página/backpressure de Usuários divergem.'
Require-True ($manifest.sourceContract.paginationCursor -ceq 'INTRA_RUN_ONLY') `
    'O cursor GraphQL não pode ser watermark persistente.'
Require-True ($manifest.sourceContract.terminalEvidence -ceq `
        'GRAPHQL_PAGE_INFO_LOCAL_ONLY' `
        -and $manifest.sourceContract.datasetCompleteness -ceq 'NOT_PROVEN') `
    'pageInfo não pode ser promovido a prova de completude.'
Require-True ($manifest.sourceContract.updatedAt -ceq 'ABSENT_AND_NOT_INFERRED' `
        -and $manifest.sourceContract.legacyEndpoint9901 -ceq 'NOT_INFERRED') `
    'O contrato não pode inventar updatedAt nem endpoint 9901.'
Require-True (@(Compare-Object @('INTEGER', 'STRING') @($manifest.identity.wireTypes) `
            -CaseSensitive).Count -eq 0 `
        -and $manifest.identity.wireTypesRemainDistinct -eq $true `
        -and $manifest.identity.canonicalKey -ceq 'BIGINT_IDENTITY') `
    'A identidade type-tagged/canônica diverge.'
Require-True ($manifest.attributes.nameMaximumCharacters -eq 255 `
        -and @(Compare-Object @('ABSENT', 'NULL', 'VALUE') `
            @($manifest.attributes.namePresence) -CaseSensitive).Count -eq 0 `
        -and $manifest.attributes.absentPreservesKnownValue -eq $true) `
    'O contrato tri-state de name diverge.'
Require-True ($manifest.persistence.applicationMode -ceq 'SET_BASED' `
        -and $manifest.persistence.javaPerUserDml -ceq 'PROHIBITED' `
        -and $manifest.persistence.historyOnNoOp -eq $false) `
    'A promoção deve permanecer set-based e sem histórico em no-op.'
Require-True ($manifest.persistence.absenceDeactivation -ceq `
        'DISABLED_UNTIL_V2_012B_AND_V2_013') `
    'Ausência não pode desativar Usuários neste bloco.'
Require-True ($manifest.persistence.shadowDimensionView -ceq `
        'core.v_usuario_dimension_current_v1' `
        -and $manifest.persistence.shadowDimensionRoadmapTask -ceq `
            'V2-035B-USUARIOS' `
        -and $manifest.persistence.consumerCompatibilityView -ceq `
            'DEFERRED_TO_V2_037') `
    'Current/history deve separar a dimensão interna do contrato consumidor.'
Require-True ($manifest.concurrency.stageRetry -ceq 'UNIQUE_KEY_PLUS_UPDLOCK_HOLDLOCK' `
        -and $manifest.concurrency.applicationNamespace -ceq `
            'TWO_SESSION_EXCLUSIVE_TRANSACTION_APPLOCK' `
        -and $manifest.concurrency.twoSessionProbeKind -ceq `
            'DIRECT_APPLOCK_USING_FORMULA_EXTRACTED_FROM_V004' `
        -and $manifest.concurrency.entrypointConcurrencyEvidence -ceq `
            'COMPOSITIONAL_WITH_ROLLBACK_ENTRYPOINT_EXERCISE' `
        -and $manifest.concurrency.namespaceIncludesEnvironment -eq $true `
        -and $manifest.concurrency.lockReleasedByRollback -eq $true) `
    'O contrato de concorrência de Usuários diverge.'
Require-True ($manifest.quality.typedBlockedCanNeverPublish -eq $true `
        -and $manifest.quality.typedBlockedCanNeverReportReadyHealth -eq $true) `
    'O DQ tipado deve falhar fechado para publicação e health.'
Require-True ($manifest.lifecycle.rawNameArchive -ceq 'PROHIBITED' `
        -and $manifest.lifecycle.typedArchive -ceq 'MINIMIZED_AND_ATTESTED' `
        -and $manifest.lifecycle.planningBudget -ceq `
            'BOUNDED_SEEK_BEFORE_OVERSIZED_FILTER_TOP_AND_CUMULATIVE_ADMISSION' `
        -and $manifest.lifecycle.lateSidecar -ceq `
            'BLOCKED_BY_ATTEMPT_ROW_FENCE_AND_PREEXISTING_GENERIC_REJECTION' `
        -and $manifest.lifecycle.typedOversized -ceq `
            'CLASSIFIED_BEFORE_TOP_WITHOUT_STARVING_LATER_FITTING_EXECUTION' `
        -and $manifest.lifecycle.maximalBudgetBackfill -ceq 'NOT_CLAIMED' `
        -and $manifest.lifecycle.restore -ceq 'READ_ONLY' `
        -and $manifest.lifecycle.automaticPurge -eq $false) `
    'O lifecycle tipado/minimizado diverge.'
Require-True ($manifest.runtime.promotionPolicy -ceq 'SHADOW_UPSERT_ONLY' `
        -and $manifest.runtime.sweep -ceq 'PROHIBITED' `
        -and $manifest.runtime.cutover -ceq 'PROHIBITED' `
        -and $manifest.runtime.remoteCallsUsedAsEvidence -eq $false) `
    'O manifesto extrapola a autorização shadow-only.'
Require-True (@(Compare-Object @(
            'stg.usp_stage_usuario_record',
            'core.usp_apply_reconcile_publish_usuarios'
        ) @($manifest.runtime.grantedProcedures) -CaseSensitive).Count -eq 0) `
    'A allowlist de procedures de Usuários diverge.'
Require-True ($manifest.verification.lateSidecarExercise -ceq `
        'database/validation/029_exercise_usuarios_late_sidecar_rollback.sql') `
    'O manifesto não versiona o gate negativo do sidecar tardio.'

$migration = Get-Content -LiteralPath $migrationPath -Raw
foreach ($requiredToken in @(
        'CREATE TABLE stg.usuario_record',
        'CREATE TABLE core.usuario',
        'CREATE TABLE core.usuario_history',
        'CREATE TABLE recon.usuario_quarantine',
        'CREATE TABLE ctl.usuario_promotion_result',
        'CREATE OR ALTER PROCEDURE stg.usp_stage_usuario_record',
        'CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_usuarios',
        '@input_record_ordinal NOT BETWEEN 1 AND 20',
        'usuarios-identity-presence-v1',
        'observation_order_execution_id',
        'INSERT INTO core.usuario_history',
        'STALE_NO_OP',
        'REACTIVATED',
        'trg_usuario_publication_requires_apply_wrapper',
        'CREATE OR ALTER FUNCTION recon.ufn_staging_lifecycle_extension_archive_budget',
        'TOP (@maximum_extension_rows + 1)',
        'UQ_stg_usuario_record_execution_stage',
        'additional_archive_bytes',
        'THROW 51703',
        'trg_usuario_stage_archive_from_generic',
        'trg_execution_record_delete_usuario_stage',
        'usp_publish_v2_procedure_grant N''stg'', N''usp_stage_usuario_record''',
        'N''core'', N''usp_apply_reconcile_publish_usuarios'''
    )) {
    Require-True (Test-OrdinalContains $migration $requiredToken) `
        "A migration de Usuários não cobre $requiredToken."
}
Require-True (-not (Test-OrdinalContains $migration 'MERGE ')) `
    'A aplicação de Usuários não pode usar MERGE.'
Require-True (-not (Test-OrdinalContains $migration '[updatedAt]') `
        -and -not (Test-OrdinalContains $migration '@updatedAt') `
        -and -not (Test-OrdinalContains $migration 'updated_at')) `
    'A migration não pode materializar updatedAt.'
Require-True (-not (Test-OrdinalContains $migration "N'DEACTIVATED'")) `
    'V2-033 não autoriza desativação por ausência.'

$baseline = Get-Content -LiteralPath $baselinePath -Raw
Require-True (Test-OrdinalContains $baseline `
        ':r "..\migrations\V007__create_usuarios_current_history.sql"') `
    'O baseline não inclui V007.'

$validator = Get-Content -LiteralPath $validatorPath -Raw
Require-True (Test-OrdinalContains $validator `
        'Usuários/current/history V2 validados com sucesso.') `
    'O validator estrutural de Usuários está ausente.'
foreach ($validatorToken in @(
        'UQ_core_usuario_source', 'BIGINT IDENTITY',
        'trg_usuario_publication_requires_apply_wrapper',
        'ufn_staging_lifecycle_extension_archive_budget',
        'execution_data_quality_evaluation', 'DML direto positivo'
    )) {
    Require-True (Test-OrdinalContains $validator $validatorToken) `
        "O validator de Usuários não cobre $validatorToken."
}

$exercise = Get-Content -LiteralPath $exercisePath -Raw
foreach ($exerciseToken in @(
        'V007__create_usuarios_current_history.sql',
        'INTEGER', 'STRING', 'ABSENT', 'NULL',
        'REACTIVATED', 'stale_noop_rows',
        'conflicting_root_keys = 1',
        'O item tipado oversized bloqueou o item menor após o TOP.',
        '@archived_extension_budget <> @initial_extension_budget',
        'usp_apply_reconcile_publish_usuarios',
        'ROLLBACK TRANSACTION',
        'Usuários/current/history validados e revertidos com sucesso.'
    )) {
    Require-True (Test-OrdinalContains $exercise $exerciseToken) `
        "O exercício rollback-only de Usuários não cobre $exerciseToken."
}

$lateSidecarExercise = Get-Content -LiteralPath $lateSidecarExercisePath -Raw
foreach ($lateSidecarToken in @(
        'EXEC stg.usp_stage_record',
        "N'EXTRACTING', N'BLOCKED'",
        'EXEC stg.usp_stage_usuario_record',
        '@late_sidecar_error <> 51703',
        'ROLLBACK TRANSACTION',
        'Sidecar tipado tardio bloqueado e estado revertido com sucesso.'
    )) {
    Require-True (Test-OrdinalContains $lateSidecarExercise $lateSidecarToken) `
        "O gate de sidecar tardio não cobre $lateSidecarToken."
}

$concurrencyGate = Get-Content -LiteralPath $concurrencyGatePath -Raw
foreach ($concurrencyToken in @(
        'sys.sp_getapplock', "@LockOwner = 'Transaction'",
        'USUARIOS_CONTENTION_CONFIRMED', 'USUARIOS_OTHER_ENVIRONMENT_ISOLATED',
        'USUARIOS_LOCK_REACQUIRED_AFTER_ROLLBACK',
        'EXEC core.usp_apply_reconcile_publish_execution',
        'FROM stg.usuario_record WITH (UPDLOCK, HOLDLOCK)',
        'Remove-Item -LiteralPath'
    )) {
    Require-True (Test-OrdinalContains $concurrencyGate $concurrencyToken) `
        "O gate concorrente de Usuários não cobre $concurrencyToken."
}

$showplanExercise = Get-Content -LiteralPath $showplanExercisePath -Raw
foreach ($showplanExerciseToken in @(
        'SET SHOWPLAN_XML ON', 'stg.usp_stage_usuario_record',
        'core.usp_apply_reconcile_publish_usuarios',
        'IX_stg_usuario_record_execution_source', 'UQ_core_usuario_source',
        'IX_core_usuario_history_timeline', 'PK_recon_usuario_candidate_application',
        'ROLLBACK TRANSACTION', 'USUARIOS_CURRENT_HISTORY_SHOWPLAN_ROLLED_BACK'
    )) {
    Require-True (Test-OrdinalContains $showplanExercise $showplanExerciseToken) `
        "O exercício SHOWPLAN de Usuários não cobre $showplanExerciseToken."
}

$showplanGate = Get-Content -LiteralPath $showplanGatePath -Raw
foreach ($showplanGateToken in @(
        '028_validate_usuarios_current_history_showplan.sql',
        'Integrated Security=true', 'maximumPlanCharacters',
        'IX_stg_usuario_record_execution_source', 'UQ_core_usuario_source',
        'IX_core_usuario_history_timeline', 'PK_recon_usuario_candidate_application',
        'operationalWarnings=0'
    )) {
    Require-True (Test-OrdinalContains $showplanGate $showplanGateToken) `
        "O gate SHOWPLAN de Usuários não cobre $showplanGateToken."
}

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() `
        -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) `
    'O fingerprint do manifesto de Usuários está desatualizado.'

Write-Output 'Manifesto e artefatos de Usuários/current/history validados com sucesso.'

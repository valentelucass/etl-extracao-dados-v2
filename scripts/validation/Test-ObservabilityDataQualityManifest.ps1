[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\observability-data-quality.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\observability-data-quality.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V006__create_observability_data_quality.sql'
$usuariosMigrationPath = Join-Path $repositoryRoot 'database\migrations\V007__create_usuarios_current_history.sql'
$usuariosValidatorPath = Join-Path $repositoryRoot 'database\validation\026_validate_usuarios_current_history.sql'
$usuariosExercisePath = Join-Path $repositoryRoot 'database\validation\027_exercise_usuarios_current_history_rollback.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\021_validate_observability_data_quality.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\022_exercise_observability_data_quality_rollback.sql'
$migratorExercisePath = Join-Path $repositoryRoot 'database\validation\023_exercise_observability_data_quality_migrator_rollback.sql'
$thresholdExercisePath = Join-Path $repositoryRoot 'database\validation\024_exercise_data_quality_thresholds_rollback.sql'
$showplanPath = Join-Path $repositoryRoot 'database\validation\025_validate_observability_data_quality_showplan.sql'
$adrPath = Join-Path $repositoryRoot 'docs\adr\0014-observabilidade-e-data-quality-fail-closed.md'
$logbackPath = Join-Path $repositoryRoot 'src\main\resources\logback.xml'
$mainJavaPath = Join-Path $repositoryRoot 'src\main\java'

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Test-OrdinalContains {
    param([string]$Text, [string]$Value)
    return $Text.IndexOf($Value, [StringComparison]::Ordinal) -ge 0
}

function Require-ExactArray {
    param([object[]]$Actual, [object[]]$Expected, [string]$Label)
    Require-True ($Actual.Count -eq $Expected.Count) "$Label possui cardinalidade divergente."
    for ($index = 0; $index -lt $Expected.Count; $index++) {
        Require-True ([object]::Equals($Actual[$index], $Expected[$index])) `
            "$Label diverge na posição $index."
    }
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$migration = Get-Content -LiteralPath $migrationPath -Raw
$usuariosMigration = Get-Content -LiteralPath $usuariosMigrationPath -Raw
$usuariosValidator = Get-Content -LiteralPath $usuariosValidatorPath -Raw
$usuariosExercise = Get-Content -LiteralPath $usuariosExercisePath -Raw
$validator = Get-Content -LiteralPath $validatorPath -Raw
$exercise = Get-Content -LiteralPath $exercisePath -Raw
$migratorExercise = Get-Content -LiteralPath $migratorExercisePath -Raw
$thresholdExercise = Get-Content -LiteralPath $thresholdExercisePath -Raw
$showplan = Get-Content -LiteralPath $showplanPath -Raw
$adr = Get-Content -LiteralPath $adrPath -Raw
$logback = Get-Content -LiteralPath $logbackPath -Raw

Require-True ($manifest.manifestVersion -eq 2) 'A versão do manifesto V2-023 deve ser 2.'
Require-True ($manifest.roadmapTask -ceq 'V2-023' -and $manifest.localState -ceq 'COMPLETE_LOCAL') `
    'O manifesto deve representar V2-023 concluída localmente.'
Require-True ($manifest.defaultState.productivePoliciesSeeded -eq 0 `
        -and $manifest.defaultState.externalAlertDeliveryEnabled -eq $false `
        -and $manifest.defaultState.remoteHealthProbesEnabled -eq $false `
        -and $manifest.defaultState.automaticLogDeletionEnabled -eq $false) `
    'O default de observabilidade/DQ deve permanecer inerte e offline.'
Require-True ($manifest.externalActivationGate.roadmapTask -ceq 'V2-045b' `
        -and $manifest.externalActivationGate.status -ceq 'EXTERNAL_HOLD') `
    'A ativação produtiva deve permanecer no hold externo correto.'

$expectedChecks = @(
    'COUNT_EQUATION', 'PAGE_TERMINALITY',
    'PROMOTION_RECONCILIATION', 'QUARANTINE_SLA'
)
Require-ExactArray @($manifest.dataQuality.supportedCommonChecks) $expectedChecks 'Checks comuns'
Require-True ($manifest.dataQuality.expectedCommonChecks -eq 4 `
        -and $manifest.dataQuality.massProcessingOwner -ceq 'SQL_SERVER' `
        -and $manifest.dataQuality.javaResultShape -ceq 'FIXED_O_1_SUMMARY' `
        -and $manifest.dataQuality.thresholdRule -ceq 'ABSOLUTE_AND_BASIS_POINTS' `
        -and $manifest.dataQuality.partialExecution -ceq 'FAIL_CLOSED' `
        -and $manifest.dataQuality.policySeededByMigration -eq $false) `
    'O contrato fail-closed/pushdown do manifesto diverge.'
Require-True ($manifest.dataQuality.policyBinding.scopeFingerprint -ceq 'SHA256_ENV_SOURCE_TENANT_ENTITY_MODE' `
        -and $manifest.dataQuality.policyBinding.policyFingerprint -ceq 'SHA256_CANONICAL_POLICY_MATERIAL' `
        -and $manifest.dataQuality.policyBinding.effectiveSelection -ceq 'LATEST_EFFECTIVE_ROW_MUST_BE_RATIFIED' `
        -and $manifest.dataQuality.policyBinding.revokedLatestBehavior -ceq 'DENY_BY_DEFAULT' `
        -and $manifest.dataQuality.policyBinding.structuralThresholds -ceq 'ZERO' `
        -and $manifest.dataQuality.policyBinding.temporalQuarantineSla -ceq 'REVALIDATED_AT_EVALUATION_HEALTH_AND_PUBLICATION') `
    'O binding canônico/vigente da policy de Data Quality diverge.'
$expectedMetricFields = @(
    'execution_id', 'metric_sequence', 'duration_milliseconds', 'pages',
    'response_bytes', 'physical_rows', 'distinct_root_keys', 'duplicate_rows',
    'valid_rows', 'quarantined_root_keys', 'unidentified_quarantine_rows',
    'quarantined_stage_rows', 'candidate_rows', 'inserted_rows', 'updated_rows',
    'reactivated_rows', 'noop_rows', 'stale_noop_rows', 'retry_attempts',
    'rate_limit_responses', 'source_lag_milliseconds', 'watermark_utc',
    'captured_at_utc'
)
Require-ExactArray @($manifest.metricShape.fields) $expectedMetricFields 'Shape métrico'
$expectedMetricEquations = @(
    'physical_rows=distinct_root_keys+duplicate_rows+unidentified_quarantine_rows',
    'distinct_root_keys=valid_rows+quarantined_root_keys',
    'quarantined_stage_rows>=quarantined_root_keys+unidentified_quarantine_rows',
    'candidate_rows=valid_rows',
    'candidate_rows=inserted_rows+updated_rows+reactivated_rows+noop_rows',
    'stale_noop_rows<=noop_rows',
    'rate_limit_responses<=retry_attempts'
)
Require-ExactArray @($manifest.metricShape.equations) $expectedMetricEquations 'Equações métricas'
Require-True ($manifest.evidence.businessIdentifiers -eq $false `
        -and $manifest.evidence.payload -eq $false `
        -and $manifest.evidence.pii -eq $false `
        -and $manifest.evidence.rawSqlErrorMessage -eq $false `
        -and $manifest.evidence.maximumSanitizedSamples -eq 32) `
    'A política de evidência sanitizada diverge.'
Require-True ($manifest.limits.maximumMetricSnapshotsPerExecution -eq 4096 `
        -and $manifest.limits.maximumAlertsPerCorrelation -eq 4096 `
        -and $manifest.limits.maximumPrimaryLogEventsPerComponentProcess -eq 1000000 `
        -and $manifest.limits.maximumTotalLogEventsPerComponentProcess -eq 1000001 `
        -and $manifest.limits.maximumLogBytesPerComponentProcess -eq 268435456 `
        -and $manifest.limits.maximumHealthRunningAgeSeconds -eq 2592000) `
    'Os caps de observabilidade divergem.'
Require-True ($manifest.leastPrivilege.runtimeDirectCtlDml -eq $false `
        -and $manifest.leastPrivilege.runtimeDirectReconDml -eq $false `
        -and $manifest.leastPrivilege.runtimeEntrypoints -eq 5) `
    'A fronteira least-privilege de V2-023 diverge.'
Require-True ($manifest.durability.hardDeleteByApplication -eq $false `
        -and $manifest.durability.includedInV005Purge -eq $false) `
    'A evidência de V2-023 não pode entrar no hard delete de staging.'
$usuariosExtension = $manifest.verticalExtensions.usuarios
Require-True ($usuariosExtension.roadmapTask -ceq 'V2-033' `
        -and $usuariosExtension.migration -ceq 'V007__create_usuarios_current_history.sql' `
        -and $usuariosExtension.typedResult -ceq 'ctl.usuario_promotion_result' `
        -and $usuariosExtension.typedQuarantine -ceq 'recon.usuario_quarantine') `
    'A extensão DQ de Usuários diverge.'
Require-True ($usuariosExtension.publicationRequiresTypedPass -eq $true `
        -and $usuariosExtension.typedBlockedInvalidatesCommonPass -eq $true `
        -and $usuariosExtension.typedBlockedHealthStatus -ceq 'DOWN' `
        -and $usuariosExtension.conflictThreshold -eq 0 `
        -and $usuariosExtension.externalDeliveryEnabled -eq $false) `
    'O DQ tipado de Usuários deve falhar fechado e permanecer offline.'
Require-ExactArray @($usuariosExtension.ownerRoles) `
    @('data_quality_owner', 'operations') 'Owner-papéis do DQ de Usuários'
foreach ($usuariosToken in @(
        'usuario_promotion_result', 'usuario_quarantine',
        'execution_data_quality_evaluation', 'validation_state',
        'trg_usuario_publication_requires_apply_wrapper'
    )) {
    Require-True (Test-OrdinalContains ($usuariosMigration + "`n" + $usuariosValidator) `
            $usuariosToken) `
        "A extensão DQ de Usuários não cobre $usuariosToken."
}
foreach ($usuariosExerciseToken in @(
        'conflicting_root_keys = 1', 'BLOCKED',
        'usp_evaluate_execution_data_quality', 'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $usuariosExercise $usuariosExerciseToken) `
        "O exercício DQ de Usuários não cobre $usuariosExerciseToken."
}
Require-True ($manifest.localValidation.structuralValidator -ceq '021_validate_observability_data_quality.sql' `
        -and $manifest.localValidation.rollbackExercise -ceq '022_exercise_observability_data_quality_rollback.sql' `
        -and $manifest.localValidation.migratorExercise -ceq '023_exercise_observability_data_quality_migrator_rollback.sql' `
        -and $manifest.localValidation.thresholdExercise -ceq '024_exercise_data_quality_thresholds_rollback.sql' `
        -and $manifest.localValidation.showplanProbe -ceq '025_validate_observability_data_quality_showplan.sql' `
        -and $manifest.localValidation.externalCalls -eq $false) `
    'A matriz de validação local V2-023 diverge.'

foreach ($objectName in @(
        @($manifest.objects.ctlTables) + @($manifest.objects.reconTables) `
            + @($manifest.objects.publicProcedures) + @($manifest.objects.triggers) `
            + @($manifest.objects.indexes)
    )) {
    $leaf = ($objectName -split '\.')[-1]
    Require-True (Test-OrdinalContains $migration $leaf) "A migration não contém $objectName."
    Require-True (Test-OrdinalContains $validator $leaf) "O validator não cobre $objectName."
}

foreach ($token in @(
        'sys.sp_getapplock', 'V2_APPLY_', 'CONVERT(DECIMAL(38, 0), measurement.failed_rows)',
        'TOP (@maximum_sanitized_samples)', '@maximum_sanitized_samples NOT BETWEEN 1 AND 32',
        'A execução parcial dos checks foi recusada',
        'Publicação recusada sem Data Quality completa, vigente e aprovada',
        'trg_data_quality_policy_immutable',
        'trg_data_quality_check_policy_immutable',
        'scope_fingerprint',
        'A avaliação retroativa após publicação foi recusada',
        'usp_publish_v2_procedure_grant'
    )) {
    Require-True (Test-OrdinalContains $migration $token) "O contrato SQL não contém $token."
}
foreach ($token in @(
        'SET SHOWPLAN_XML ON',
        'recon.usp_evaluate_execution_data_quality',
        'recon.usp_observe_execution_data_quality',
        'recon.usp_record_execution_metric',
        'recon.usp_raise_observability_alert',
        'ctl.usp_observe_platform_health',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $showplan $token) `
        "O probe SHOWPLAN V2-023 não cobre $token."
}
Require-True (-not (Test-OrdinalContains $migration 'TRUNCATE TABLE')) `
    'V2-023 não pode usar TRUNCATE.'
Require-True (-not (Test-OrdinalContains $migration 'sp_executesql')) `
    'A engine DQ não pode aceitar SQL dinâmico.'
Require-True (-not (Test-OrdinalContains $migration 'INSERT INTO ctl.data_quality_policy (')) `
    'A migration não pode semear policy produtiva.'

foreach ($token in @(
        'evaluation_state = N''PASSED''', 'evaluation_state = N''FAILED''',
        'failed_checks = 1', 'health_status = N''DOWN''',
        '@publication_gate_error <> 51615', 'ROLLBACK TRANSACTION',
        'rollback integral'
    )) {
    Require-True (Test-OrdinalContains $exercise $token) "O exercício não cobre $token."
}
foreach ($token in @(
        'EXECUTE AS USER = N''v2_migrator_observability_probe''',
        'V006__create_observability_data_quality.sql',
        'Os cinco grants mínimos de V2-023 não foram publicados.',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $migratorExercise $token) `
        "A prova isolada de migrator não cobre $token."
}
foreach ($token in @(
        'failure_basis_points = 5000',
        'failure_basis_points = 1000',
        'O limite percentual não bloqueou quando o absoluto ainda passava.',
        'O limite absoluto não bloqueou na igualdade percentual.',
        'CHECK_WITHIN_THRESHOLD',
        'O health não reavaliou o crossing temporal do SLA.',
        'O trigger não reavaliou o crossing temporal do SLA.',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $thresholdExercise $token) `
        "O exercício de thresholds não cobre $token."
}

Require-True (Test-OrdinalContains $adr 'linha agregada O(1)') `
    'O ADR não registra a fronteira O(1).'
Require-True (Test-OrdinalContains $logback 'ch.qos.logback.classic.encoder.JsonEncoder' `
        -and (Test-OrdinalContains $logback '<withMDC>false</withMDC>') `
        -and (Test-OrdinalContains $logback '<withMessage>false</withMessage>') `
        -and (Test-OrdinalContains $logback '<withFormattedMessage>false</withFormattedMessage>') `
        -and (Test-OrdinalContains $logback '<withArguments>false</withArguments>') `
        -and (Test-OrdinalContains $logback '<withThrowable>false</withThrowable>') `
        -and (Test-OrdinalContains $logback '<maxHistory>0</maxHistory>') `
        -and (Test-OrdinalContains $logback '<root level="OFF">') `
        -and (Test-OrdinalContains $logback 'br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpExecutor') `
        -and (Test-OrdinalContains $logback 'br.com.esl.etl.v2.plataforma.fonte.dataexport.HttpDataExportGateway')) `
    'O Logback não preserva JSON sanitizado e lifecycle inerte.'
Require-True (-not (Test-OrdinalContains $logback 'execution_id')) `
    'O encoder não pode serializar execution_id real.'

$directLoggerFiles = @(
    Get-ChildItem -LiteralPath $mainJavaPath -Recurse -File -Filter '*.java' |
        Where-Object {
            $_.Name -ne 'Slf4jStructuredLogSink.java' -and
            (Get-Content -LiteralPath $_.FullName -Raw).Contains('LoggerFactory')
        }
)
Require-True ($directLoggerFiles.Count -eq 0) `
    'LoggerFactory produtivo deve ficar restrito ao adapter estruturado.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0]
Require-True ($fingerprint -ceq $recordedFingerprint.ToLowerInvariant()) `
    'O fingerprint do manifesto V2-023 está desatualizado.'

Write-Output 'Manifesto, logs e contrato estático de observabilidade/Data Quality validados.'

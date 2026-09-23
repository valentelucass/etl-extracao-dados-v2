[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$migrationsDirectory = Join-Path $repositoryRoot 'database\migrations'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$controlPlaneValidatorPath = Join-Path $repositoryRoot 'database\validation\003_validate_control_plane.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\005_validate_progressive_data_gate.sql'
$kernelValidatorPath = Join-Path $repositoryRoot 'database\validation\007_validate_staging_promotion_kernel.sql'
$publicationValidatorPath = Join-Path $repositoryRoot 'database\validation\009_validate_atomic_publication_protocol.sql'
$lifecycleValidatorPath = Join-Path $repositoryRoot 'database\validation\012_validate_staging_lifecycle.sql'
$observabilityValidatorPath = Join-Path $repositoryRoot 'database\validation\021_validate_observability_data_quality.sql'
$usuariosValidatorPath = Join-Path $repositoryRoot 'database\validation\026_validate_usuarios_current_history.sql'
$referencesValidatorPath = Join-Path $repositoryRoot 'database\validation\030_validate_governed_references.sql'
$usuariosDimensionValidatorPath = Join-Path $repositoryRoot `
    'database\validation\035_validate_usuarios_dimension_current.sql'
$manifestosValidatorPath = Join-Path $repositoryRoot `
    'database\validation\042_validate_manifestos_shadow_vertical.sql'
$fretesValidatorPath = Join-Path $repositoryRoot `
    'database\validation\044_validate_fretes_shadow_vertical.sql'
$localizacaoValidatorPath = Join-Path $repositoryRoot `
    'database\validation\046_validate_localizacao_cargas_shadow_vertical.sql'
$concurrencyProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-AtomicPublicationConcurrency.ps1'
$lifecycleConcurrencyProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-StagingLifecycleConcurrency.ps1'
$lifecycleShowplanProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-StagingLifecycleShowplan.ps1'
$observabilityShowplanProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-ObservabilityDataQualityShowplan.ps1'
$usuariosConcurrencyProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-UsuariosCurrentHistoryConcurrency.ps1'
$usuariosShowplanProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-UsuariosCurrentHistoryShowplan.ps1'
$referencesConcurrencyProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-GovernedReferencesConcurrency.ps1'
$referencesShowplanProbePath = Join-Path $repositoryRoot 'scripts\validation\Test-GovernedReferencesShowplan.ps1'
$usuariosDimensionShowplanProbePath = Join-Path $repositoryRoot `
    'scripts\validation\Test-UsuariosDimensionCurrentShowplan.ps1'
$exercisePath = Join-Path $repositoryRoot 'database\validation\006_exercise_progressive_data_gate_rollback.sql'
$runnerPath = Join-Path $repositoryRoot 'scripts\validation\Invoke-ProgressiveDataGate.ps1'

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

& (Join-Path $PSScriptRoot 'Test-SchemaFoundationManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-ControlPlaneManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-StagingPromotionKernelManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-StagingLifecycleManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-ObservabilityDataQualityManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-UsuariosCurrentHistoryManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-GovernedReferencesManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-UsuariosDimensionCurrentManifest.ps1')
& (Join-Path $PSScriptRoot 'Test-ManifestosV2026ShadowVertical.ps1')
& (Join-Path $PSScriptRoot 'Test-FretesV2011ShadowVertical.ps1')
& (Join-Path $PSScriptRoot 'Test-LocalizacaoCargasV2028ShadowVertical.ps1')

$expectedMigrations = @(
    'V001__create_v2_schema_foundation.sql',
    'V002__create_v2_database_roles.sql',
    'V003__create_control_plane.sql',
    'V004__create_staging_promotion_kernel.sql',
    'V005__create_staging_lifecycle.sql',
    'V006__create_observability_data_quality.sql',
    'V007__create_usuarios_current_history.sql',
    'V008__create_governed_references.sql',
    'V009__create_usuario_dimension_current_view.sql',
    'V010__create_coletas_shadow_vertical.sql',
    'V011__create_cotacoes_shadow_vertical.sql',
    'V012__create_manifestos_shadow_vertical.sql',
    'V013__create_fretes_shadow_vertical.sql',
    'V014__create_localizacao_cargas_shadow_vertical.sql',
    'V015__create_runtime_durable_recovery.sql',
    'V016__create_windows_runtime_authority.sql',
    'V017__create_runtime_temporal_plan.sql',
    'V018__enforce_durable_runtime_consumers.sql',
    'V019__create_scoped_runtime_observability.sql',
    'V020__bind_existing_runtime_occurrences.sql',
    'V021__bind_durable_temporal_intent.sql',
    'V022__extend_five_vertical_runtime.sql',
    'V023__correct_scoped_runtime_output_projection.sql',
    'V024__bind_source_protocols_and_users_runtime.sql',
    'V025__restore_coletas_extraction_audit.sql',
    'V026__create_coletas_temporal_reference.sql',
    'V027__create_coletas_exact_temporal_laboratory.sql',
    'V028__align_coletas_laboratory_presence_collation.sql',
    'V029__create_relational_laboratory.sql',
    'V030__capture_relational_laboratory.sql',
    'V031__resolve_relational_laboratory.sql',
    'V032__recompose_relational_laboratory.sql',
    'V033__align_relational_capture_components.sql',
    'V034__strengthen_relational_cohorts_and_binding_dates.sql',
    'V035__seal_relational_reconciliation_completeness.sql',
    'V036__guard_relational_key_padding.sql',
    'V037__preserve_relational_mdfe_number_contract.sql',
    'V038__create_expansion_laboratory_captures.sql',
    'V039__apply_expansion_laboratory.sql',
    'V040__capture_expansion_dependencies.sql',
    'V041__resolve_expansion_relations.sql',
    'V042__consume_expansion_references.sql',
    'V043__project_expansion_financial_inputs.sql',
    'V044__fence_expansion_captures_and_conflicts.sql',
    'V045__materialize_expansion_invoices.sql',
    'V046__capture_expansion_freight_terms.sql',
    'V047__materialize_expansion_revenue.sql',
    'V048__expansion_revenue_exact_comparison.sql',
    'V049__expansion_partition_recomposition.sql',
    'V050__expansion_active_amount_reducers.sql',
    'V051__expansion_sinistro_array_contract.sql',
    'V052__create_analytic_raster_laboratory.sql',
    'V053__apply_analytic_raster_laboratory.sql',
    'V054__project_analytic_raster_transit.sql',
    'V055__create_analytic_governed_references.sql',
    'V056__bind_analytic_sources_and_dimensions.sql',
    'V057__capture_analytic_freight_attributes.sql',
    'V058__materialize_analytic_freight_operational.sql',
    'V059__align_analytic_location_forecast_path.sql',
    'V060__project_analytic_freight_and_location.sql',
    'V061__bind_analytic_freight_capture_contract.sql',
    'V062__align_analytic_freight_capture_application.sql',
    'V063__bind_analytic_current_and_unloading_branches.sql',
    'V064__prepare_analytic_manifest_attributes.sql',
    'V065__bind_analytic_manifest_lifecycle.sql',
    'V066__align_analytic_manifest_preparation_collation.sql',
    'V067__materialize_analytic_collectors.sql',
    'V068__preserve_analytic_manifest_exact_freshness.sql',
    'V069__link_analytic_manifest_snapshot_cohort.sql',
    'V070__bind_analytic_manifest_freight_paths.sql',
    'V071__consume_analytic_owned_fleet_references.sql',
    'V072__materialize_analytic_manifests.sql',
    'V073__project_analytic_manifest_consumption.sql',
    'V074__compare_analytic_manifest_instants.sql',
    'V075__align_relational_collection_alias_wrapper.sql',
    'V076__scope_manifest_relations_to_current_components.sql',
    'V077__compare_relational_manifest_competence_instants.sql',
    'V078__resolve_manifest_vehicle_roles.sql',
    'V079__project_analytic_inventory_and_incidents.sql',
    'V080__project_analytic_financial_consumption.sql',
    'V081__project_analytic_internal_monitoring.sql',
    'V082__recognize_analytic_monitoring_quarantine_reasons.sql',
    'V083__capture_analytic_quote_attributes.sql',
    'V084__consume_analytic_quote_tariffs.sql',
    'V085__prepare_analytic_quote_snapshots.sql',
    'V086__project_analytic_quote_consumption.sql',
    'V087__bind_analytic_quote_runtime_reference.sql',
    'V088__bind_analytic_collection_supplements.sql',
    'V089__prepare_analytic_collection_snapshots.sql',
    'V090__bind_analytic_collection_regions.sql',
    'V091__project_analytic_collection_queries.sql',
    'V092__guard_synthetic_collection_absence.sql',
    'V093__prove_raster_windows_and_last_observation.sql',
    'V094__isolate_analytic_materialization_failures.sql',
    'V095__batch_analytic_manifest_composition_seals.sql',
    'V096__seal_analytic_scenario_publication.sql',
    'V097__monitor_analytic_raster_and_materializations.sql',
    'V098__bind_analytic_scenario_source_windows.sql',
    'V099__preserve_freight_terminal_omission_freshness.sql'
)
$actualMigrations = @(Get-ChildItem -LiteralPath $migrationsDirectory -File | ForEach-Object Name | Sort-Object)
Require-True (@(Compare-Object $expectedMigrations $actualMigrations -CaseSensitive).Count -eq 0) `
    'O gate progressivo exige exatamente V001-V098 no repositório; a instalação física tem validação própria.'

$baseline = Get-Content -LiteralPath $baselinePath -Raw
foreach ($migration in $expectedMigrations) {
    Require-True (Test-OrdinalContains $baseline (':r "..\migrations\' + $migration + '"')) `
        "O baseline não inclui explicitamente $migration."
}

$validator = (Get-Content -LiteralPath $controlPlaneValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $validatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $kernelValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $publicationValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $lifecycleValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $observabilityValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $usuariosValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $referencesValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $usuariosDimensionValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $manifestosValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $fretesValidatorPath -Raw) + "`n" + `
    (Get-Content -LiteralPath $localizacaoValidatorPath -Raw)
foreach ($requiredConstraint in @(
        'UQ_ctl_execution_partition_semantic_key',
        'CK_ctl_execution_count_equation',
        'UX_ctl_execution_lease_active_partition',
        'CK_ctl_execution_partition_next_attempt_number',
        'CK_ctl_execution_attempt_next_transition_sequence',
        'unidentified_quarantine_rows',
        'CK_ctl_execution_promotion_result_counts',
        'UQ_stg_execution_record_input',
        'PK_stg_execution_candidate',
        'CK_stg_execution_candidate_fingerprints',
        'UQ_recon_quarantine_record_stage',
        'PK_ctl_execution_publication_event',
        'PK_recon_execution_candidate_application',
        'CK_recon_execution_reconciliation_result_counts',
        'Latin1_General_100_BIN2',
        'max_length = -1',
        'DATALENGTH',
        'CK_ctl_execution_attempt_terminal_timestamp',
        'CK_ctl_execution_attempt_transition_sequence_lifecycle_bound',
        'CK_ctl_execution_state_event_lifecycle_bound',
        'PK_ctl_staging_lifecycle_plan',
        'PK_recon_staging_lifecycle_archive_manifest',
        'PK_recon_execution_data_quality_evaluation',
        'CK_recon_execution_data_quality_evaluation_counts',
        'PK_recon_execution_metric_snapshot',
        'PK_recon_observability_alert'
    )) {
    Require-True (Test-OrdinalContains $validator $requiredConstraint) `
        "O validator progressivo não cobre $requiredConstraint."
}
foreach ($requiredPermission in @(
        "N'v2_migrator'", "N'v2_runtime'", "N'CREATE TABLE'", "N'EXECUTE'",
        "N'SELECT'", "N'INSERT'", "N'UPDATE'", "N'DELETE'", 'ROLE_MEMBERSHIP',
        "N'usp_stage_record'", "N'usp_stage_usuario_record'",
        "N'usp_prepare_staged_execution'",
        "N'usp_apply_reconcile_publish_execution'",
        "N'usp_apply_reconcile_publish_usuarios'",
        "N'usp_control_plane_recover_stale_executions'",
        "N'usp_publish_v2_procedure_grant'",
        "N'v2_retention_governor'", "N'v2_lifecycle_reviewer'",
        "N'v2_lifecycle_operator'", "N'v2_archive_restorer'",
        "N'usp_plan_staging_lifecycle'", "N'usp_archive_staging_lifecycle'",
        "N'usp_purge_staging_lifecycle'", "N'usp_restore_staging_archive'",
        "N'usp_evaluate_execution_data_quality'", "N'usp_observe_execution_data_quality'",
        "N'usp_record_execution_metric'", "N'usp_raise_observability_alert'",
        "N'usp_observe_platform_health'"
    )) {
    Require-True (Test-OrdinalContains $validator $requiredPermission) `
        "O validator progressivo não cobre a política de privilégio $requiredPermission."
}
Require-True (Test-OrdinalContains $validator 'Gate progressivo de dados V2 validado com sucesso.') `
    'A confirmação do validator progressivo está ausente.'
Require-True (Test-OrdinalContains $validator 'Publicação indisponível sem evidência de reconciliação e commit atômico') `
    'O gate não prova a contenção fail-closed da publicação.'
Require-True (Test-OrdinalContains $validator 'INSERT INTO stg.execution_candidate') `
    'O gate não prova a persistência do candidate set.'
Require-True (Test-OrdinalContains $validator 'COUNT_NAMESPACE') `
    'O gate não reserva o namespace interno de contagens.'
Require-True (Test-OrdinalContains $validator 'IDEMPOTENCY') `
    'O gate não valida os fast-paths idempotentes.'
Require-True (Test-OrdinalContains $validator 'REASON_GRAMMAR') `
    'O gate não valida a gramática canônica de reason code.'
Require-True (Test-OrdinalContains $validator '@count_phase COLLATE Latin1_General_100_BIN2') `
    'O gate não prova a reserva BIN2 exata de STAGING_KERNEL.'
Require-True (Test-OrdinalContains $validator 'Lifecycle governado de staging V2 validado com sucesso.') `
    'A confirmação do validator de lifecycle está ausente.'
Require-True (Test-OrdinalContains $validator 'ufn_invalid_staging_legal_hold') `
    'O gate não valida a integridade fail-closed do ledger de legal hold.'
Require-True (Test-OrdinalContains $validator 'ufn_invalid_execution_state_ledger') `
    'O gate não valida a integridade fail-closed do ledger terminal.'
Require-True (Test-OrdinalContains $validator '@expected_fingerprint_parameters' `
        -and (Test-OrdinalContains $validator 'terminal_event.transition_sequence')) `
    'O gate não valida encoding de fingerprints e prova terminal imutável.'
Require-True (Test-OrdinalContains $validator 'Observabilidade e Data Quality V2 validadas com sucesso.') `
    'A confirmação do validator V2-023 está ausente.'
Require-True (Test-OrdinalContains $validator 'Usuários/current/history V2 validados com sucesso.') `
    'A confirmação do validator V2-033 está ausente.'
Require-True (Test-OrdinalContains $validator 'Referências governadas V2 validadas com sucesso.') `
    'A confirmação do validator V2-035a está ausente.'
Require-True (Test-OrdinalContains $validator `
        'Dimensão current de Usuários V2 validada com sucesso.') `
    'A confirmação do validator da fatia Usuários de V2-035b está ausente.'
Require-True (Test-OrdinalContains $validator 'Manifestos V2-026 em sombra validados com sucesso.') `
    'A confirmação do validator V2-026 está ausente.'
Require-True (Test-OrdinalContains $validator 'Fretes V2-011 em sombra validados com sucesso.') `
    'A confirmação do validator V2-011 está ausente.'
Require-True (Test-OrdinalContains $validator 'Localização de Cargas V2-028 em sombra validada com sucesso.') `
    'A confirmação do validator V2-028 está ausente.'
Require-True (Test-OrdinalContains $validator 'ref.ufn_calendar_seed_candidate_v1') `
    'O gate não valida o calendário rolante governado.'
Require-True (Test-OrdinalContains $validator 'IX_ref_regiao_logistica_cep_lookup') `
    'O gate não valida o lookup CEP tipado.'

$concurrencyProbe = Get-Content -LiteralPath $concurrencyProbePath -Raw
foreach ($requiredConcurrencyToken in @(
        "`$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'",
        'Start-Process', '-WindowStyle Hidden', 'sys.sp_getapplock',
        "@LockMode = 'Exclusive'", "@LockOwner = 'Transaction'",
        'CONTENTION_CONFIRMED', 'LOCK_REACQUIRED_AFTER_ROLLBACK',
        'ROLLBACK TRANSACTION', 'Remove-Item -LiteralPath'
    )) {
    Require-True (Test-OrdinalContains $concurrencyProbe $requiredConcurrencyToken) `
        "O probe concorrente não cobre $requiredConcurrencyToken."
}

$lifecycleShowplanProbe = Get-Content -LiteralPath $lifecycleShowplanProbePath -Raw
foreach ($requiredShowplanToken in @(
        '017_validate_staging_lifecycle_showplan.sql', 'SET SHOWPLAN_XML ON',
        'IX_ctl_execution_attempt_lifecycle', 'operationalWarnings=0',
        'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $lifecycleShowplanProbe $requiredShowplanToken) `
        "O gate SHOWPLAN de lifecycle não cobre $requiredShowplanToken."
}

$observabilityShowplanProbe = Get-Content -LiteralPath $observabilityShowplanProbePath -Raw
foreach ($requiredShowplanToken in @(
        '025_validate_observability_data_quality_showplan.sql', 'SET SHOWPLAN_XML ON',
        'IX_ctl_execution_attempt_health_state_started',
        'IX_recon_execution_data_quality_evaluation_state',
        'UX_ctl_data_quality_policy_scope_effective', 'operationalWarnings=0',
        'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $observabilityShowplanProbe $requiredShowplanToken) `
        "O gate SHOWPLAN V2-023 não cobre $requiredShowplanToken."
}

$usuariosConcurrencyProbe = Get-Content -LiteralPath $usuariosConcurrencyProbePath -Raw
foreach ($requiredConcurrencyToken in @(
        "`$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'",
        'Start-Process', '-WindowStyle Hidden', 'sys.sp_getapplock',
        "@LockMode = 'Exclusive'", "@LockOwner = 'Transaction'",
        'USUARIOS_CONTENTION_CONFIRMED', 'USUARIOS_OTHER_ENVIRONMENT_ISOLATED',
        'USUARIOS_LOCK_REACQUIRED_AFTER_ROLLBACK',
        'INDEX(UQ_core_usuario_source), FORCESEEK',
        'ROLLBACK TRANSACTION', 'Remove-Item -LiteralPath'
    )) {
    Require-True (Test-OrdinalContains $usuariosConcurrencyProbe $requiredConcurrencyToken) `
        "O gate concorrente de Usuários não cobre $requiredConcurrencyToken."
}

$usuariosShowplanProbe = Get-Content -LiteralPath $usuariosShowplanProbePath -Raw
foreach ($requiredShowplanToken in @(
        '028_validate_usuarios_current_history_showplan.sql', 'SET SHOWPLAN_XML ON',
        'IX_stg_usuario_record_execution_source', 'UQ_core_usuario_source',
        'IX_core_usuario_history_timeline', 'PK_recon_usuario_candidate_application',
        'operationalWarnings=0', 'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $usuariosShowplanProbe $requiredShowplanToken) `
        "O gate SHOWPLAN de Usuários não cobre $requiredShowplanToken."
}

$referencesConcurrencyProbe = Get-Content -LiteralPath $referencesConcurrencyProbePath -Raw
foreach ($requiredConcurrencyToken in @(
        "`$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'",
        'Start-Process', '-WindowStyle Hidden', 'sys.sp_getapplock',
        "@LockMode = 'Exclusive'", "@LockOwner = 'Transaction'",
        'REFERENCES_CONTENTION_CONFIRMED', 'REFERENCES_OTHER_SCOPE_ISOLATED',
        'REFERENCES_LOCK_REACQUIRED_AFTER_ROLLBACK',
        'ROLLBACK TRANSACTION', 'Remove-Item -LiteralPath'
    )) {
    Require-True (Test-OrdinalContains $referencesConcurrencyProbe $requiredConcurrencyToken) `
        "O gate concorrente de referências não cobre $requiredConcurrencyToken."
}

$referencesShowplanProbe = Get-Content -LiteralPath $referencesShowplanProbePath -Raw
foreach ($requiredShowplanToken in @(
        '034_validate_governed_references_showplan.sql', 'SET SHOWPLAN_XML ON',
        'IX_ref_reference_release_scope_validity', 'IX_ref_calendario_business_date',
        'IX_ref_status_coleta_lookup', 'IX_ref_classificacao_frota_alias_lookup',
        'IX_ref_classificacao_frota_matriz_lookup',
        'IX_ref_classificacao_frota_excecao_lookup', 'IX_ref_regiao_logistica_cep_lookup',
        'IX_ref_tarifa_rota_uf_lookup', 'operationalWarnings=0',
        '$conversionWarnings -ne 0',
        'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $referencesShowplanProbe $requiredShowplanToken) `
        "O gate SHOWPLAN de referências não cobre $requiredShowplanToken."
}

$usuariosDimensionShowplanProbe = Get-Content -LiteralPath `
    $usuariosDimensionShowplanProbePath -Raw
foreach ($requiredShowplanToken in @(
        '037_validate_usuarios_dimension_current_showplan.sql', 'SET SHOWPLAN_XML ON',
        'PK_core_usuario', 'UQ_core_usuario_source', 'conversionWarnings=0',
        'operationalWarnings=0', 'Integrated Security=true', 'maximumPlanCharacters'
    )) {
    Require-True (Test-OrdinalContains $usuariosDimensionShowplanProbe `
            $requiredShowplanToken) `
        "O gate SHOWPLAN da dimensão current de Usuários não cobre $requiredShowplanToken."
}

$runner = Get-Content -LiteralPath $runnerPath -Raw
foreach ($requiredRunnerArtifact in @(
        'Test-StagingLifecycleShowplan.ps1',
        'Test-ObservabilityDataQualityShowplan.ps1',
        'Test-UsuariosCurrentHistoryConcurrency.ps1',
        'Test-UsuariosCurrentHistoryShowplan.ps1',
        'Test-GovernedReferencesConcurrency.ps1',
        'Test-GovernedReferencesShowplan.ps1',
        'Test-UsuariosDimensionCurrentShowplan.ps1',
        '013_exercise_staging_lifecycle_rollback.sql',
        '014_exercise_staging_policy_ratification_rollback.sql',
        '015_exercise_staging_lifecycle_migrator_rollback.sql',
        '016_exercise_staging_legal_hold_integrity_rollback.sql',
        '018_exercise_staging_fingerprint_ascii_rollback.sql',
        '019_exercise_staging_legal_hold_retry_integrity_rollback.sql',
        '020_exercise_staging_terminal_ledger_integrity_rollback.sql',
        '022_exercise_observability_data_quality_rollback.sql',
        '023_exercise_observability_data_quality_migrator_rollback.sql',
        '024_exercise_data_quality_thresholds_rollback.sql',
        '027_exercise_usuarios_current_history_rollback.sql',
        '029_exercise_usuarios_late_sidecar_rollback.sql',
        '031_exercise_governed_references_rollback.sql',
        '032_exercise_governed_references_migrator_rollback.sql',
        '033_exercise_governed_references_negative_rollback.sql',
        '036_exercise_usuarios_dimension_current_rollback.sql',
        '043_exercise_manifestos_shadow_vertical_rollback.sql',
        '045_exercise_fretes_shadow_vertical_rollback.sql'
    )) {
    Require-True (Test-OrdinalContains $runner $requiredRunnerArtifact) `
        "O runner progressivo não executa $requiredRunnerArtifact."
}

foreach ($negativeExercise in @(
        '014_exercise_staging_policy_ratification_rollback.sql',
        '015_exercise_staging_lifecycle_migrator_rollback.sql',
        '016_exercise_staging_legal_hold_integrity_rollback.sql',
        '018_exercise_staging_fingerprint_ascii_rollback.sql',
        '019_exercise_staging_legal_hold_retry_integrity_rollback.sql',
        '020_exercise_staging_terminal_ledger_integrity_rollback.sql',
        '023_exercise_observability_data_quality_migrator_rollback.sql',
        '024_exercise_data_quality_thresholds_rollback.sql',
        '029_exercise_usuarios_late_sidecar_rollback.sql',
        '032_exercise_governed_references_migrator_rollback.sql',
        '033_exercise_governed_references_negative_rollback.sql'
    )) {
    $negativeExercisePath = Join-Path $repositoryRoot "database\validation\$negativeExercise"
    $negativeExerciseText = Get-Content -LiteralPath $negativeExercisePath -Raw
    Require-True (Test-OrdinalContains $negativeExerciseText 'ROLLBACK TRANSACTION') `
        "$negativeExercise não comprova rollback explícito."
}

$lifecycleConcurrencyProbe = Get-Content -LiteralPath $lifecycleConcurrencyProbePath -Raw
foreach ($requiredConcurrencyToken in @(
        "`$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'",
        'Start-Process', '-WindowStyle Hidden', 'V2_STAGING_LIFECYCLE',
        "@LockMode = 'Exclusive'", "@LockOwner = 'Transaction'",
        'LIFECYCLE_CONTENTION_CONFIRMED', 'LOCK_REACQUIRED_AFTER_ROLLBACK',
        'ROLLBACK TRANSACTION', 'Remove-Item -LiteralPath'
    )) {
    Require-True (Test-OrdinalContains $lifecycleConcurrencyProbe $requiredConcurrencyToken) `
        "O probe concorrente de lifecycle não cobre $requiredConcurrencyToken."
}

$exercise = Get-Content -LiteralPath $exercisePath -Raw
foreach ($requiredText in @(
        '004_exercise_control_plane_baseline_rollback.sql',
        'V001__create_v2_schema_foundation.sql',
        'V002__create_v2_database_roles.sql',
        'V003__create_control_plane.sql',
        'V004__create_staging_promotion_kernel.sql',
        'V005__create_staging_lifecycle.sql',
        'V006__create_observability_data_quality.sql',
        'V007__create_usuarios_current_history.sql',
        'V008__create_governed_references.sql',
        'V009__create_usuario_dimension_current_view.sql',
        'V010__create_coletas_shadow_vertical.sql',
        'V011__create_cotacoes_shadow_vertical.sql',
        'V012__create_manifestos_shadow_vertical.sql',
        'V013__create_fretes_shadow_vertical.sql',
        '005_validate_progressive_data_gate.sql',
        '007_validate_staging_promotion_kernel.sql',
        '009_validate_atomic_publication_protocol.sql',
        '012_validate_staging_lifecycle.sql',
        '021_validate_observability_data_quality.sql',
        '026_validate_usuarios_current_history.sql',
        '030_validate_governed_references.sql',
        '035_validate_usuarios_dimension_current.sql',
        '042_validate_manifestos_shadow_vertical.sql',
        '044_validate_fretes_shadow_vertical.sql',
        'ROLLBACK TRANSACTION'
    )) {
    Require-True (Test-OrdinalContains $exercise $requiredText) `
        "O exercício progressivo não cobre $requiredText."
}

& (Join-Path $PSScriptRoot 'Test-RuntimeDurableRecovery.ps1')
Write-Output 'Gate progressivo de dados: manifests, migrations, baseline e exercício rollback-only validados com sucesso.'

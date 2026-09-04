[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\staging-lifecycle.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\staging-lifecycle.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V005__create_staging_lifecycle.sql'
$usuariosMigrationPath = Join-Path $repositoryRoot 'database\migrations\V007__create_usuarios_current_history.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\012_validate_staging_lifecycle.sql'
$runnerPath = Join-Path $repositoryRoot 'scripts\validation\Invoke-ProgressiveDataGate.ps1'
$logLifecyclePath = Join-Path $repositoryRoot 'scripts\operations\Invoke-GovernedLogLifecycle.ps1'
$logLifecycleTestPath = Join-Path $repositoryRoot 'scripts\validation\Test-GovernedLogLifecycle.ps1'
$showplanPath = Join-Path $repositoryRoot 'scripts\validation\Test-StagingLifecycleShowplan.ps1'
$logbackPath = Join-Path $repositoryRoot 'src\main\resources\logback.xml'

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
$validator = Get-Content -LiteralPath $validatorPath -Raw
$runner = Get-Content -LiteralPath $runnerPath -Raw
$logLifecycle = Get-Content -LiteralPath $logLifecyclePath -Raw
$logLifecycleTest = Get-Content -LiteralPath $logLifecycleTestPath -Raw
$showplan = Get-Content -LiteralPath $showplanPath -Raw
$logback = Get-Content -LiteralPath $logbackPath -Raw

$expectedCtlTables = @(
    'staging_retention_policy', 'staging_retention_policy_event',
    'staging_legal_hold', 'staging_legal_hold_event', 'staging_lifecycle_plan',
    'staging_lifecycle_plan_item', 'staging_lifecycle_purge_event'
)
$expectedReconTables = @(
    'staging_lifecycle_archive_manifest', 'staging_record_archive',
    'staging_candidate_archive', 'quarantine_record_archive',
    'execution_candidate_application_archive', 'execution_reconciliation_result_archive',
    'execution_state_event_archive', 'execution_page_audit_archive',
    'execution_count_archive', 'execution_publication_event_archive',
    'execution_promotion_result_archive', 'staging_restore_session',
    'staging_restore_record', 'staging_restore_candidate'
)
$expectedPublicProcedures = @(
    'ctl.usp_approve_staging_retention_policy',
    'ctl.usp_revoke_staging_retention_policy',
    'ctl.usp_place_staging_legal_hold',
    'ctl.usp_release_staging_legal_hold',
    'stg.usp_plan_staging_lifecycle',
    'stg.usp_archive_staging_lifecycle',
    'stg.usp_purge_staging_lifecycle',
    'recon.usp_restore_staging_archive'
)
$expectedInternalProcedures = @(
    'stg.usp_plan_staging_lifecycle_at',
    'recon.usp_compute_live_staging_content_root_internal',
    'recon.usp_compute_archive_content_root_internal',
    'recon.usp_verify_staging_archive_internal',
    'recon.usp_verify_staging_restore_internal'
)
$expectedLiveSources = @(
    'stg.execution_record', 'stg.execution_candidate', 'recon.quarantine_record',
    'recon.execution_candidate_application', 'recon.execution_reconciliation_result',
    'ctl.execution_state_event', 'ctl.execution_page_audit', 'ctl.execution_count',
    'ctl.execution_publication_event', 'ctl.execution_promotion_result'
)
$expectedArchiveTables = @(
    'recon.staging_record_archive', 'recon.staging_candidate_archive',
    'recon.quarantine_record_archive', 'recon.execution_candidate_application_archive',
    'recon.execution_reconciliation_result_archive',
    'recon.execution_state_event_archive', 'recon.execution_page_audit_archive',
    'recon.execution_count_archive', 'recon.execution_publication_event_archive',
    'recon.execution_promotion_result_archive'
)
$expectedExercises = @(
    '013_exercise_staging_lifecycle_rollback.sql',
    '014_exercise_staging_policy_ratification_rollback.sql',
    '015_exercise_staging_lifecycle_migrator_rollback.sql',
    '016_exercise_staging_legal_hold_integrity_rollback.sql',
    '017_validate_staging_lifecycle_showplan.sql',
    '018_exercise_staging_fingerprint_ascii_rollback.sql',
    '019_exercise_staging_legal_hold_retry_integrity_rollback.sql',
    '020_exercise_staging_terminal_ledger_integrity_rollback.sql',
    '027_exercise_usuarios_current_history_rollback.sql',
    '029_exercise_usuarios_late_sidecar_rollback.sql'
)

Require-True ($manifest.manifestVersion -eq 3) 'A versão do manifesto de lifecycle deve ser 3.'
Require-True ($manifest.roadmapTask -ceq 'V2-045a') 'O manifesto deve pertencer a V2-045a.'
Require-True ($manifest.localState -ceq 'COMPLETE_LOCAL') `
    'A fundação local deve declarar somente COMPLETE_LOCAL.'
Require-True ($manifest.defaultState.retentionPoliciesSeeded -eq 0 `
        -and $manifest.defaultState.automaticPurge -eq $false `
        -and $manifest.defaultState.productiveTtlApproved -eq $false `
        -and $manifest.defaultState.logDeletionEnabled -eq $false) `
    'O default do lifecycle deve permanecer inerte.'
Require-True ($manifest.externalActivationGate.roadmapTask -ceq 'V2-045b' `
        -and $manifest.externalActivationGate.status -ceq 'EXTERNAL_HOLD') `
    'Os controles produtivos devem permanecer em V2-045b/EXTERNAL_HOLD.'
Require-True (@($manifest.externalActivationGate.ownerRoles).Count -eq 4) `
    'Os quatro owner-papéis externos devem permanecer explícitos.'
Require-True (@($manifest.externalActivationGate.physicalControlsNotProven).Count -eq 7) `
    'O manifesto não enumera todos os controles físicos ainda não comprovados.'

Require-ExactArray @($manifest.terminalStates.published) @('PUBLISHED') 'Estados published'
Require-ExactArray @($manifest.terminalStates.nonPublished) `
    @('BLOCKED', 'FAILED', 'CANCELLED', 'DEGRADED') 'Estados não publicados'
Require-ExactArray @($manifest.terminalStates.noLoad) `
    @('SKIPPED', 'NOT_APPLICABLE') 'Estados sem carga'
Require-True ($manifest.terminalStates.noLoadContract -ceq 'STAGING_MUST_BE_EMPTY') `
    'SKIPPED/NOT_APPLICABLE devem falhar se houver staging.'
Require-True ($manifest.eligibility.cursor -ceq 'terminal_at_utc_then_execution_id' `
        -and $manifest.eligibility.cursorPersistedInPlanReceipt -eq $true) `
    'Cursor bounded do planner diverge.'
Require-True ($manifest.eligibility.legalHoldLedger -ceq `
        'BASE_ROW_MUST_MATCH_PLACED_AND_RELEASED_EVENTS') `
    'O ledger fail-closed de legal hold está ausente.'
Require-True ($manifest.eligibility.policyRatification -ceq `
        'BASE_ROW_MATCHES_DISTINCT_DATA_OWNER_AND_COMPLIANCE_EVIDENCE_AND_ROLES_WITH_NO_REVOKED_EVENT' `
        -and $manifest.eligibility.fingerprintInputEncoding -ceq `
            'NVARCHAR_MAX_VALIDATED_AS_LOWERCASE_ASCII_HEX_BIN2' `
        -and $manifest.eligibility.terminalStateLedger -ceq `
            'BOUNDED_CONTIGUOUS_SEMANTIC_CHAIN_ORIGIN_CARDINALITY_CLOCK_AND_LAST_EVENT_MUST_MATCH_EXECUTION_ATTEMPT') `
    'Ratificação independente, encoding ou prova terminal divergem.'
Require-True ($manifest.eligibility.extensionArchiveBudget -ceq `
        'BOUNDED_CORRELATED_ITVF_BEFORE_OVERSIZED_FILTER_TOP_AND_CUMULATIVE_ADMISSION') `
    'O contrato de extensão do budget precisa anteceder toda admissão limitada.'

Require-True ($manifest.safetyLimits.kind -ceq `
        'LOGICAL_ADMISSION_BOUNDS_NOT_GLOBAL_PHYSICAL_IO_CAPS') `
    'Os caps não podem alegar limite físico global.'
foreach ($limit in @{
        maximumExecutionsCeiling = 1000
        maximumExaminedExecutionsCeiling = 5000
        maximumStageRowsCeiling = 1000000
        maximumCandidateRowsCeiling = 1000000
        maximumEvidenceRowsCeiling = 5000000
        maximumContentRowsCeiling = 100000
        maximumProbeRowsCeiling = 10000000
        maximumLegalHoldLedgerRowsPerExecution = 4096
        maximumTerminalStateEventsPerExecution = 7
        maximumArchiveBytesCeiling = 1073741824
    }.GetEnumerator()) {
    Require-True ($manifest.safetyLimits.($limit.Key) -eq $limit.Value) `
        "O limite $($limit.Key) diverge da migration."
}

Require-ExactArray @($manifest.objects.ctlTables) $expectedCtlTables 'Tabelas ctl'
Require-ExactArray @($manifest.objects.reconTables) $expectedReconTables 'Tabelas recon'
Require-ExactArray @($manifest.objects.publicProcedures) `
    $expectedPublicProcedures 'Procedures públicas'
Require-ExactArray @($manifest.objects.internalProcedures) `
    $expectedInternalProcedures 'Procedures internas'
Require-ExactArray @($manifest.objects.functions) `
    @('recon.ufn_archive_row_attestation',
        'recon.ufn_staging_lifecycle_extension_archive_budget',
        'ctl.ufn_invalid_staging_legal_hold',
        'ctl.ufn_invalid_execution_state_ledger') `
    'Funções do lifecycle'
Require-ExactArray @($manifest.objects.triggers) @(
    'recon.trg_quarantine_record_staging_lineage',
    'recon.trg_candidate_application_staging_lineage',
    'stg.trg_execution_record_lifecycle_delete_guard',
    'stg.trg_execution_candidate_lifecycle_delete_guard'
) 'Triggers do lifecycle'
Require-True (@($manifest.objects.indexes).Count -eq 17) `
    'O manifesto deve enumerar os 15 IX e 2 UX filtrados.'

Require-ExactArray @($manifest.archive.liveSources) $expectedLiveSources 'Fontes do archive'
Require-ExactArray @($manifest.archive.archiveTables) `
    $expectedArchiveTables 'Tabelas de archive'
Require-True ($manifest.archive.manifestFingerprintVersion -ceq `
        'staging-archive-manifest-v3' `
        -and $manifest.archive.contentRootVersion -ceq 'archive-content-set-v1' `
        -and $manifest.archive.rowAttestationVersion -ceq 'archive-row-json-v1' `
        -and $manifest.archive.cryptographicContentAttestation -eq $true) `
    'O selo criptográfico do archive diverge.'
Require-ExactArray @($manifest.purge.hardDeleteAllowlist) `
    @('stg.execution_candidate', 'stg.execution_record') 'Allowlist de purge'
Require-True ($manifest.purge.reappearedStagingOnRetry -ceq 'FAIL_CLOSED') `
    'Retry de purge precisa recusar staging reaparecido.'
Require-True ($manifest.restore.repopulatesActiveStaging -eq $false `
        -and $manifest.restore.publishesOrMutatesCore -eq $false `
        -and @($manifest.restore.contentReaderEntrypoints).Count -eq 0) `
    'Restore deve permanecer materialização interna read-only sem reader de conteúdo.'
$usuariosExtension = $manifest.verticalExtensions.usuarios
Require-True ($usuariosExtension.roadmapTask -ceq 'V2-033' `
        -and $usuariosExtension.migration -ceq 'V007__create_usuarios_current_history.sql') `
    'A extensão de lifecycle de Usuários deve pertencer a V2-033/V007.'
Require-True ($usuariosExtension.liveSource -ceq 'stg.usuario_record' `
        -and $usuariosExtension.archiveTable -ceq 'recon.usuario_stage_archive' `
        -and $usuariosExtension.restoreTable -ceq 'recon.usuario_stage_restore' `
        -and $usuariosExtension.disposalEvidence -ceq `
            'recon.usuario_stage_disposal_evidence') `
    'Os sidecars tipados de Usuários divergem.'
Require-True ($usuariosExtension.rawNameArchived -eq $false `
        -and $usuariosExtension.planningBudget -ceq `
            'TYPED_BYTES_INCLUDED_BY_BOUNDED_SEEK_BEFORE_OVERSIZED_FILTER_TOP_AND_CUMULATIVE_ADMISSION' `
        -and $usuariosExtension.contentRows -ceq `
            'ONE_TO_ONE_WITH_SELECTED_GENERIC_STAGE_ROWS_HAVING_TYPED_SIDECAR' `
        -and $usuariosExtension.integrity -ceq `
            'SEPARATE_TYPED_ATTESTATION_VERIFIED_BEFORE_PURGE') `
    'Minimização, orçamento ou integridade do sidecar de Usuários divergem.'
Require-ExactArray @($usuariosExtension.purgeOrder) `
    @('stg.usuario_record', 'stg.execution_record') 'Ordem de purge de Usuários'
Require-True ($usuariosExtension.lateSidecarExercise -ceq `
        '029_exercise_usuarios_late_sidecar_rollback.sql') `
    'A extensão de Usuários deve versionar o gate negativo do sidecar tardio.'
foreach ($usuariosToken in @(
        'CREATE TABLE recon.usuario_stage_archive',
        'CREATE TABLE recon.usuario_stage_restore',
        'CREATE TABLE recon.usuario_stage_disposal_evidence',
        'trg_usuario_lifecycle_plan_budget',
        'trg_usuario_stage_archive_from_generic',
        'trg_usuario_stage_restore_from_generic',
        'trg_execution_record_delete_usuario_stage',
        'archive_row_attestation',
        'discarded_name_bytes'
    )) {
    Require-True (Test-OrdinalContains $usuariosMigration $usuariosToken) `
        "A extensão lifecycle de Usuários não cobre $usuariosToken."
}
$typedArchiveStart = $usuariosMigration.IndexOf(
    'CREATE TABLE recon.usuario_stage_archive', [StringComparison]::Ordinal)
$typedArchiveEnd = $usuariosMigration.IndexOf(
    'CREATE INDEX IX_recon_usuario_stage_archive_execution',
    [StringComparison]::Ordinal)
Require-True ($typedArchiveStart -ge 0 -and $typedArchiveEnd -gt $typedArchiveStart) `
    'Não foi possível isolar o archive tipado de Usuários.'
$typedArchiveDefinition = $usuariosMigration.Substring(
    $typedArchiveStart, $typedArchiveEnd - $typedArchiveStart)
Require-True (-not (Test-OrdinalContains $typedArchiveDefinition 'usuario_name')) `
    'O nome bruto não pode ser duplicado no archive tipado.'

Require-True ($manifest.logLifecycle.mode -ceq 'LOCAL_MANUAL_OPT_IN' `
        -and $manifest.logLifecycle.ratification -ceq `
            'DISTINCT_DATA_OWNER_AND_COMPLIANCE_EVIDENCE' `
        -and $manifest.logLifecycle.inertPolicyHistory -ceq `
            'FAIL_CLOSED_BEFORE_DISABLED_OR_LEGAL_HOLD_RETURN' `
        -and $manifest.logLifecycle.defaultEnabled -eq $false `
        -and $manifest.logLifecycle.productiveColdStorageOrWorm -eq $false) `
    'Lifecycle de logs não pode alegar ativação/controle produtivo.'
Require-ExactArray @($manifest.logLifecycle.sourceFence) `
    @('NAMED_MUTEX', 'PERSISTENT_FILE_LOCK', 'ACTIVE_COMPLETE_MARKER') `
    'Fences de origem dos logs'
Require-True ($manifest.leastPrivilege.v2RuntimeLifecycleExecute -eq $false `
        -and $manifest.leastPrivilege.migrationGrantPublisher -ceq `
            'dbo.usp_publish_v2_procedure_grant') `
    'Boundary de publicação/execução diverge.'
Require-ExactArray @($manifest.localValidation.rollbackExercises) `
    $expectedExercises 'Exercícios rollback-only'
Require-True ($manifest.localValidation.showplan.operationalWarningsAllowed -eq 0 `
        -and $manifest.localValidation.showplan.scaleClaim -eq $false) `
    'SHOWPLAN local não pode aceitar warning operacional nem alegar escala.'

foreach ($objectName in @(
        $expectedCtlTables + $expectedReconTables + $expectedPublicProcedures `
            + $expectedInternalProcedures + @($manifest.objects.functions) `
            + @($manifest.objects.triggers) + @($manifest.objects.indexes)
    )) {
    $leafName = ($objectName -split '\.')[-1]
    Require-True (Test-OrdinalContains $migration $leafName) `
        "A migration não contém o objeto $objectName."
    Require-True (Test-OrdinalContains $validator $leafName) `
        "O validator não cobre o objeto $objectName."
}
foreach ($requiredMigrationToken in @(
        'staging-archive-manifest-v3', 'archive-content-set-v1',
        'archive-row-json-v1', 'ctl.ufn_invalid_staging_legal_hold(@execution_id)',
        'Retry de purge encontrou staging reaparecido',
        'maximum_examined_executions NOT BETWEEN @maximum_executions AND 5000',
        'maximum_content_rows NOT BETWEEN 1 AND 100000',
        "COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'",
        'data_owner_evidence_fingerprint COLLATE Latin1_General_100_BIN2 <>',
        'release_authority_evidence_fingerprint IS NOT NULL',
        'release_owner_role IS NOT NULL',
        'ctl.ufn_invalid_execution_state_ledger(examined.execution_id)',
        'CK_ctl_execution_state_event_lifecycle_bound',
        'IX_ctl_staging_legal_hold_execution',
        'IX_ctl_staging_legal_hold_event_execution',
        '@maximum_hold_ledger_rows_per_execution - 3',
        '@hold_ledger_event_probe_rows > @remaining_hold_ledger_probe_rows',
        'TOP (@remaining_hold_ledger_probe_rows + 1)',
        'usp_publish_v2_procedure_grant', 'DELETE stage_candidate',
        'DELETE stage_record', 'CAST(1 AS BIT) AS read_only'
    )) {
    Require-True (Test-OrdinalContains $migration $requiredMigrationToken) `
        "O contrato SQL não contém $requiredMigrationToken."
}
foreach ($requiredValidatorToken in @(
        '@expected_index_keys', '@expected_foreign_key_columns',
        'foreign_key.is_not_trusted = 0', 'foreign_key.delete_referential_action = 0',
        'actual_key.is_descending_key = 0', '@expected_fingerprint_parameters',
        '@maximum_extension_rows',
        "type_definition.name = N'nvarchar'", '@purge_delete_statement_count', 'ATTACK_SURFACE',
        'Lifecycle governado de staging V2 validado com sucesso.'
    )) {
    Require-True (Test-OrdinalContains $validator $requiredValidatorToken) `
        "O validator estrutural não contém $requiredValidatorToken."
}
Require-True (-not (Test-OrdinalContains $migration 'TRUNCATE TABLE')) `
    'Lifecycle não pode usar TRUNCATE.'
Require-True (-not (Test-OrdinalContains $migration 'ON DELETE CASCADE')) `
    'Lifecycle não pode usar cascade destrutivo.'
Require-True (-not (Test-OrdinalContains $migration 'retention_days DEFAULT')) `
    'Lifecycle não pode possuir TTL default.'

foreach ($exerciseName in $expectedExercises) {
    $exercisePath = Join-Path $repositoryRoot "database\validation\$exerciseName"
    Require-True (Test-Path -LiteralPath $exercisePath -PathType Leaf) `
        "O exercício $exerciseName está ausente."
    Require-True ((Test-OrdinalContains $runner $exerciseName) -or `
            ($exerciseName -ceq '017_validate_staging_lifecycle_showplan.sql')) `
        "O runner não encadeia $exerciseName."
}
foreach ($requiredLogToken in @(
        'maximumEntriesScanned', 'maximumFilesPerRun', 'maximumSingleFileBytes',
        'maximumSourceBytes', 'ACTIVE', 'COMPLETE', 'archiveRoot',
        'evaluationTimeUtcTicks', 'Copy-StreamExactly', 'Flush($true)',
        'sourceOperationLeafName', 'sourceLockLeafName',
        'dataOwnerEvidenceFingerprint -cne', 'Assert-InertLifecycleIntegrity'
    )) {
    Require-True (Test-OrdinalContains $logLifecycle $requiredLogToken) `
        "O lifecycle governado de logs não contém $requiredLogToken."
}
Require-True (Test-OrdinalContains $logLifecycleTest `
        'Lifecycle governado de logs validado com fixtures sintéticas.') `
    'O teste sintético de logs está ausente.'
Require-True (Test-OrdinalContains $logLifecycleTest `
        'Política de logs aceitou a mesma evidência para data owner e compliance.') `
    'O teste negativo de ratificação independente dos logs está ausente.'
Require-True (Test-OrdinalContains $logLifecycleTest `
        'Política disabled mascarou marker ACTIVE e tombstone pendente.') `
    'O teste negativo de histórico incompleto sob política inerte está ausente.'
foreach ($requiredShowplanToken in @(
        'SET SHOWPLAN_XML ON', 'maximumPlanCharacters',
        'IX_ctl_execution_attempt_lifecycle', 'operationalWarnings=0',
        'scaleClaim'
    )) {
    $showplanMaterial = $showplan + "`n" + `
        (Get-Content -LiteralPath (Join-Path $repositoryRoot `
            'database\validation\017_validate_staging_lifecycle_showplan.sql') -Raw) + `
        "`n" + (ConvertTo-Json $manifest.localValidation.showplan -Compress)
    Require-True (Test-OrdinalContains $showplanMaterial $requiredShowplanToken) `
        "O gate SHOWPLAN não contém $requiredShowplanToken."
}
Require-True (Test-OrdinalContains $logback '<maxHistory>0</maxHistory>') `
    'A exclusão automática de logs deve permanecer desabilitada até V2-045b.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0]
Require-True ($fingerprint -ceq $recordedFingerprint.ToLowerInvariant()) `
    'O fingerprint do manifesto de lifecycle está desatualizado.'

Write-Output 'Manifesto v2 e contrato estático completo do lifecycle de staging/logs validados.'

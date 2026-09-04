[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\control-plane.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\control-plane.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V003__create_control_plane.sql'
$promotionMigrationPath = Join-Path $repositoryRoot 'database\migrations\V004__create_staging_promotion_kernel.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\003_validate_control_plane.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\004_exercise_control_plane_baseline_rollback.sql'

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
$expectedKey = @(
    'environment_name', 'source_instance', 'tenant_scope', 'entity_name',
    'execution_mode', 'partition_start_utc', 'partition_end_exclusive_utc'
)
$expectedTables = @(
    'source_catalog', 'execution_cycle', 'execution_partition', 'execution_attempt',
    'execution_state_event', 'execution_lease', 'execution_page_audit', 'execution_count',
    'source_watermark_observation', 'partition_publication_pointer',
    'execution_publication_event', 'incremental_publication_watermark'
)
$expectedProcedures = @(
    'usp_control_plane_register_source', 'usp_control_plane_start_cycle',
    'usp_control_plane_start_execution', 'usp_control_plane_heartbeat_lease',
    'usp_control_plane_record_page', 'usp_control_plane_record_counts',
    'usp_control_plane_transition_execution', 'usp_control_plane_register_incremental_frontier',
    'usp_control_plane_publish_execution', 'usp_control_plane_recover_stale_executions'
)
$expectedRuntimeProcedures = @(
    'usp_control_plane_register_source', 'usp_control_plane_start_cycle',
    'usp_control_plane_start_execution', 'usp_control_plane_heartbeat_lease',
    'usp_control_plane_record_page', 'usp_control_plane_record_counts',
    'usp_control_plane_transition_execution', 'usp_control_plane_recover_stale_executions'
)
$expectedCrossSchemaRuntimeProcedures = @(
    'core.usp_apply_reconcile_publish_execution'
)
$ownerOnlyProcedures = @(
    'usp_control_plane_register_incremental_frontier',
    'usp_control_plane_publish_execution'
)

Require-True ($manifest.manifestVersion -eq 4) 'A versão do manifesto do control plane deve ser 4.'
Require-True ($manifest.roadmapTask -ceq 'V2-020b') 'O manifesto deve pertencer a V2-020b.'
Require-True ($manifest.schema -ceq 'ctl') 'O control plane deve permanecer em ctl.'
Require-True (@(Compare-Object $expectedKey @($manifest.semanticPartitionKey) -CaseSensitive).Count -eq 0) 'A chave semântica do manifesto diverge.'
Require-True (@(Compare-Object $expectedTables @($manifest.userTables) -CaseSensitive).Count -eq 0) 'As tabelas do control plane divergem.'
Require-True (@(Compare-Object $expectedProcedures @($manifest.procedures) -CaseSensitive).Count -eq 0) 'As procedures do control plane divergem.'
Require-True ($manifest.runtime.directTableDml -ceq 'DENIED') 'O runtime não pode ter DML direto.'
Require-True ($manifest.runtime.proceduresOnly -eq $true) 'O runtime deve usar apenas procedures.'
Require-True (@(Compare-Object $expectedRuntimeProcedures @($manifest.runtime.grantedProcedures) -CaseSensitive).Count -eq 0) 'Os grants do runtime divergem.'
Require-True (@(Compare-Object $expectedCrossSchemaRuntimeProcedures @($manifest.runtime.crossSchemaGrantedProcedures) -CaseSensitive).Count -eq 0) 'Os grants cross-schema do runtime divergem.'
Require-True (@(Compare-Object $ownerOnlyProcedures @($manifest.runtime.ownerOnlyProcedures) -CaseSensitive).Count -eq 0) 'As procedures owner-only divergem.'
Require-True ($manifest.publication.status -ceq 'ATOMIC_POSITIVE_PATH_AVAILABLE') 'A publicação positiva atômica deve estar disponível.'
Require-True ($manifest.publication.runtimeEntrypoint -ceq 'core.usp_apply_reconcile_publish_execution') 'O entry point positivo da publicação diverge.'
Require-True ($manifest.publication.runtimeExecute -eq $true) 'Runtime deve poder invocar o entry point positivo cross-schema.'
Require-True ($manifest.publication.positivePathAvailable -eq $true) 'O manifesto deve declarar a publicação positiva.'
Require-True ($manifest.publication.event -ceq 'execution_publication_event') 'O evento imutável de publicação diverge.'
Require-True ($manifest.publication.controlPlaneEntrypoint.procedure -ceq 'ctl.usp_control_plane_publish_execution') 'O stub ctl de publicação diverge.'
Require-True ($manifest.publication.controlPlaneEntrypoint.status -ceq 'CONTAINED_FAIL_CLOSED') 'O stub ctl de publicação deve permanecer fail-closed.'
Require-True ($manifest.publication.controlPlaneEntrypoint.runtimeExecute -eq $false) 'Runtime não pode executar o stub ctl de publicação.'
Require-True ($manifest.publication.controlPlaneEntrypoint.ownerOnly -eq $true) 'O stub ctl de publicação deve permanecer owner-only.'
Require-True ($manifest.recoveryProtocol.runtimeEntrypoint -ceq 'ctl.usp_control_plane_recover_stale_executions') 'O entry point de recovery diverge.'
Require-True ($manifest.recoveryProtocol.runtimeExecute -eq $true) 'Runtime deve poder executar o recovery stale.'
Require-True (@($manifest.recoveryProtocol.callerParameters).Count -eq 0) 'Recovery não pode aceitar parâmetros do caller.'
Require-True ($manifest.recoveryProtocol.callerTimestamp -eq $false) 'Recovery não pode aceitar timestamp do caller.'
Require-True ($manifest.recoveryProtocol.authoritativeDatabaseClock -eq $true) 'Recovery deve usar o relógio autoritativo do SQL Server.'
Require-True ($manifest.recoveryProtocol.databaseClockExpression -ceq 'SYSUTCDATETIME()') 'A expressão do relógio de recovery diverge.'
Require-True ($manifest.recoveryProtocol.resultCardinality -ceq 'single_row_single_column') 'Recovery deve retornar uma única linha e coluna.'
Require-True ($manifest.recoveryProtocol.resultCardinalityBound -ceq 'O(1)') 'A cardinalidade retornada pelo recovery deve permanecer O(1).'
Require-True ($manifest.recoveryProtocol.resultColumn -ceq 'recovered_executions') 'A coluna retornada pelo recovery diverge.'
Require-True ($manifest.recoveryProtocol.resultType -ceq 'BIGINT') 'O tipo da contagem de recovery diverge.'
$pointerForeignKey = $manifest.publication.executionOwnershipForeignKeys.pointer
$eventForeignKey = $manifest.publication.executionOwnershipForeignKeys.event
Require-True ($pointerForeignKey.constraint -ceq 'FK_ctl_partition_publication_pointer_partition_execution') 'A FK composta do pointer diverge.'
Require-True (@(Compare-Object @('partition_id', 'published_execution_id') @($pointerForeignKey.columns) -CaseSensitive).Count -eq 0) 'As colunas da FK composta do pointer divergem.'
Require-True ($pointerForeignKey.references -ceq 'ctl.execution_attempt') 'A FK composta do pointer deve referenciar execution_attempt.'
Require-True (@(Compare-Object @('partition_id', 'execution_id') @($pointerForeignKey.referencedColumns) -CaseSensitive).Count -eq 0) 'As colunas referenciadas pela FK do pointer divergem.'
Require-True ($eventForeignKey.constraint -ceq 'FK_ctl_execution_publication_event_partition_execution') 'A FK composta do evento diverge.'
Require-True (@(Compare-Object @('partition_id', 'execution_id') @($eventForeignKey.columns) -CaseSensitive).Count -eq 0) 'As colunas da FK composta do evento divergem.'
Require-True ($eventForeignKey.references -ceq 'ctl.execution_attempt') 'A FK composta do evento deve referenciar execution_attempt.'
Require-True (@(Compare-Object @('partition_id', 'execution_id') @($eventForeignKey.referencedColumns) -CaseSensitive).Count -eq 0) 'As colunas referenciadas pela FK do evento divergem.'
Require-True ($manifest.stateProtocol.genericTransitionCanPromote -eq $false) 'Transição genérica não pode promover.'
Require-True ($manifest.stateProtocol.genericTransitionCanReconcile -eq $false) 'Transição genérica não pode reconciliar.'
Require-True ($manifest.leaseProtocol.heartbeatCallerClockAuthoritative -eq $false) 'O relógio do caller não pode autorizar heartbeat.'
Require-True ($manifest.leaseProtocol.startCallerClockAuthoritative -eq $false) 'O relógio do caller não pode definir início ou lease.'
Require-True ($manifest.leaseProtocol.startCallerClockMaximumSkewSeconds -eq 300) 'O limite de skew do início diverge.'
Require-True ($manifest.leaseProtocol.databaseClockCapturedAfterLeaseLocks -eq $true) 'O relógio deve ser capturado depois dos locks da lease.'
Require-True ($manifest.semanticTextCollation.name -ceq 'Latin1_General_100_BIN2') 'A collation semântica deve ser BIN2 explícita.'
Require-True ($manifest.semanticTextCollation.scope -ceq 'all_persisted_nvarchar_columns') 'BIN2 deve cobrir todo NVARCHAR persistido.'
Require-True ($manifest.semanticTextCollation.outerSpacePolicy -ceq 'trim_only_ASCII_U+0020_after_length_validation_and_reject_noncanonical_storage') 'A canonicalização deve remover somente U+0020.'
Require-True ($manifest.semanticTextCollation.procedureVariableComparison -ceq 'explicit_Latin1_General_100_BIN2') 'Variáveis técnicas devem ser comparadas sob BIN2 explícito.'
Require-True ($manifest.textInputProtocol.silentPreBodyTruncation -ceq 'PROHIBITED') 'Truncamento textual pré-procedure deve ser proibido.'
Require-True ($manifest.textInputProtocol.reasonCodes -ceq 'trim_ASCII_U+0020_then_require_first_[A-Z]_and_remaining_[A-Z0-9_]_with_length_2_to_64; no_case_fold') 'A gramática canônica de reason code diverge.'
Require-True ($manifest.countGrain.quarantined_root_keys -ceq 'identified_quarantined_root_keys') 'Quarantined_root_keys deve permanecer em grão de raiz identificada.'
Require-True ($manifest.countGrain.unidentified_quarantine_rows -ceq 'physical_rows_without_source_key') 'Quarantine sem chave precisa de grão físico separado.'
Require-True ($manifest.replayProtocol.selfReference -ceq 'PROHIBITED') 'Replay não pode ser autorreferente.'
Require-True ($manifest.replayProtocol.replayChains -ceq 'PROHIBITED') 'Cadeia de replay não está autorizada.'
$expectedReplayFields = @(
    'environment_name', 'source_instance', 'tenant_scope', 'entity_name',
    'partition_start_utc', 'partition_end_exclusive_utc'
)
Require-True (@(Compare-Object $expectedReplayFields @($manifest.replayProtocol.comparedSemanticFields) -CaseSensitive).Count -eq 0) 'O namespace comparado do replay diverge.'
Require-True ($manifest.atomicCounters.maxPlusOneProhibited -eq $true) 'MAX+1 deve permanecer proibido.'
Require-True ($manifest.countPhaseProtocol.runtimeReservedExactPhase -ceq 'STAGING_KERNEL') 'A fase interna de contagens diverge.'
Require-True ($manifest.countPhaseProtocol.comparison -ceq 'Latin1_General_100_BIN2') 'A fase interna deve usar BIN2.'
Require-True ($manifest.countPhaseProtocol.caseVariantsAreDistinctExternalPhases -eq $true) 'A reserva de fase deve ser exata e case-sensitive.'
Require-True ($manifest.countPhaseProtocol.internalWriter -ceq 'core.usp_prepare_staged_execution_direct_insert_in_fenced_transaction') 'O writer interno de STAGING_KERNEL diverge.'
Require-True ($manifest.eventIdempotency.exactRetry -ceq 'returns_before_lease_fencing') 'Retry exato precisa anteceder o fencing da lease.'
Require-True ($manifest.eventIdempotency.divergentRetry -ceq 'REJECTED') 'Retry divergente precisa falhar fechado.'
Require-True ($manifest.eventIdempotency.heartbeat -ceq 'renewal_command_without_request_identity') 'Heartbeat não deve alegar identidade de request inexistente.'
Require-True ($manifest.pageAuditProtocol.parameterCount -eq 10) `
    'A auditoria de página deve possuir dez parâmetros.'
Require-True (@(Compare-Object @(
            'NONE', 'DATA_EXPORT_EMPTY_PAGE', 'GRAPHQL_PAGE_INFO'
        ) @($manifest.pageAuditProtocol.terminalEvidenceKinds) -CaseSensitive).Count -eq 0) `
    'Os tipos fechados de evidência terminal divergem.'
Require-True ($manifest.pageAuditProtocol.terminalEvidenceProvesDatasetCompleteness -eq $false `
        -and $manifest.pageAuditProtocol.retryComparesTerminalEvidenceKind -eq $true `
        -and $manifest.pageAuditProtocol.callerTimestampAuthoritative -eq $false) `
    'A evidência terminal não pode alegar completude nem ignorar retry/clock.'

$migration = Get-Content -LiteralPath $migrationPath -Raw
$normalizedMigration = [Text.RegularExpressions.Regex]::Replace($migration, '\s+', ' ')
$promotionMigration = Get-Content -LiteralPath $promotionMigrationPath -Raw
foreach ($table in $expectedTables) {
    Require-True (Test-OrdinalContains $migration "ctl.$table") "A migration não cobre ctl.$table."
}
foreach ($procedure in $expectedProcedures) {
    Require-True (Test-OrdinalContains $migration "ctl.$procedure") "A migration não cobre ctl.$procedure."
}
foreach ($procedure in $expectedRuntimeProcedures) {
    Require-True (Test-OrdinalContains $migration "N'ctl', N'$procedure', N'v2_runtime'") "A publicação de $procedure está ausente."
}
foreach ($procedure in $ownerOnlyProcedures) {
    Require-True (-not (Test-OrdinalContains $migration "N'ctl', N'$procedure', N'v2_runtime'")) "$procedure deve permanecer owner-only."
}
Require-True (Test-OrdinalContains $promotionMigration 'CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_execution') 'O entry point positivo cross-schema está ausente.'
Require-True (Test-OrdinalContains $promotionMigration "N'core', N'usp_apply_reconcile_publish_execution', N'v2_runtime'") 'A publicação do entry point positivo cross-schema está ausente.'
Require-True (Test-OrdinalContains $promotionMigration "SET current_state = N'PUBLISHED'") 'O entry point positivo não publica a execução.'
Require-True (Test-OrdinalContains $promotionMigration 'INSERT INTO ctl.execution_publication_event') 'O entry point positivo não registra o evento imutável de publicação.'
Require-True (Test-OrdinalContains $promotionMigration 'FROM ctl.partition_publication_pointer AS pointer WITH (UPDLOCK, HOLDLOCK)') 'O entry point positivo não serializa o pointer de publicação.'
Require-True (Test-OrdinalContains $promotionMigration 'FROM ctl.incremental_publication_watermark AS watermark WITH (UPDLOCK, HOLDLOCK)') 'O entry point positivo não serializa o watermark incremental.'
$singlelineRegexOptions = [System.Text.RegularExpressions.RegexOptions]::Singleline -bor [System.Text.RegularExpressions.RegexOptions]::CultureInvariant
Require-True ([System.Text.RegularExpressions.Regex]::IsMatch(
        $migration,
        'CREATE OR ALTER PROCEDURE ctl\.usp_control_plane_recover_stale_executions\s+AS\s+BEGIN',
        $singlelineRegexOptions
    )) 'Recovery deve possuir zero parâmetros do caller.'
Require-True (Test-OrdinalContains $migration 'DECLARE @database_now_utc DATETIME2(3) = SYSUTCDATETIME();') 'Recovery não captura o relógio autoritativo do SQL Server.'
Require-True (Test-OrdinalContains $migration 'DECLARE @recovered_executions BIGINT = (SELECT COUNT_BIG(*) FROM @stale);') 'Recovery não agrega a contagem antes de retornar.'
Require-True (Test-OrdinalContains $migration 'SELECT @recovered_executions AS recovered_executions;') 'Recovery não retorna a contagem O(1) contratada.'
Require-True ([System.Text.RegularExpressions.Regex]::IsMatch(
        $migration,
        'CONSTRAINT FK_ctl_partition_publication_pointer_partition_execution\s+FOREIGN KEY \(partition_id, published_execution_id\)\s+REFERENCES ctl\.execution_attempt \(partition_id, execution_id\)',
        $singlelineRegexOptions
    )) 'A migration não possui a FK composta entre pointer e execution_attempt.'
Require-True ([System.Text.RegularExpressions.Regex]::IsMatch(
        $migration,
        'CONSTRAINT FK_ctl_execution_publication_event_partition_execution\s+FOREIGN KEY \(partition_id, execution_id\)\s+REFERENCES ctl\.execution_attempt \(partition_id, execution_id\)',
        $singlelineRegexOptions
    )) 'A migration não possui a FK composta entre evento e execution_attempt.'
foreach ($column in $expectedKey) {
    Require-True (Test-OrdinalContains $migration $column) "A migration não contém a coluna $column da chave semântica."
}
Require-True (Test-OrdinalContains $migration 'UQ_ctl_execution_partition_semantic_key') 'A chave única semântica está ausente.'
Require-True (Test-OrdinalContains $migration 'UX_ctl_execution_lease_active_partition') 'A lease ativa única está ausente.'
Require-True (Test-OrdinalContains $migration 'STALE_LEASE_RECOVERED') 'A recuperação stale não possui evento sanitizado.'
Require-True (Test-OrdinalContains $migration 'DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::ctl TO v2_runtime') 'O deny de DML direto está ausente.'
Require-True (Test-OrdinalContains $migration 'plan_version NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL') 'A versão do plano não é persistida com BIN2.'
Require-True (Test-OrdinalContains $migration 'physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows') 'A primeira equação canônica está ausente.'
Require-True (Test-OrdinalContains $migration 'distinct_root_keys = valid_rows + quarantined_root_keys') 'A segunda equação canônica está ausente.'
Require-True (Test-OrdinalContains $migration 'origin_partition.environment_name = @environment_name') 'Replay não compara ambiente da origem.'
Require-True (Test-OrdinalContains $migration 'origin_partition.source_instance = @source_instance') 'Replay não compara fonte da origem.'
Require-True (Test-OrdinalContains $migration 'origin_partition.tenant_scope = @tenant_scope') 'Replay não compara tenant da origem.'
Require-True (Test-OrdinalContains $migration 'origin_partition.entity_name = @entity_name') 'Replay não compara entidade da origem.'
Require-True (Test-OrdinalContains $migration 'origin_partition.execution_mode IN (') 'Replay não restringe o modo da origem.'
Require-True (Test-OrdinalContains $migration '@replay_of_execution_id = @execution_id') 'Replay não bloqueia autorreferência.'
Require-True (Test-OrdinalContains $migration 'ABS(DATEDIFF_BIG(SECOND, @started_at_utc, @database_now_utc)) > 300') 'O início não valida skew do caller.'
Require-True (Test-OrdinalContains $migration '@next_state COLLATE Latin1_General_100_BIN2') 'Estados de input não usam BIN2 explícito.'
Require-True (Test-OrdinalContains $migration "IN (N'PROMOTED', N'RECONCILED', N'PUBLISHED')") 'Transições positivas não estão bloqueadas no entry point genérico.'
Require-True (Test-OrdinalContains $migration 'Publicação indisponível sem evidência de reconciliação e commit atômico') 'A publicação não está fail-closed.'
Require-True (Test-OrdinalContains $migration 'heartbeat_at_utc = @database_now_utc') 'Heartbeat não usa o relógio do banco.'
Require-True (Test-OrdinalContains $normalizedMigration `
        '@terminal_empty_page, @terminal_evidence_kind, @database_now_utc') `
    'Page audit ainda confia no relógio do caller ou omite o tipo terminal.'
Require-True (Test-OrdinalContains $migration `
        "N'NONE', N'DATA_EXPORT_EMPTY_PAGE', N'GRAPHQL_PAGE_INFO'") `
    'A auditoria de página não fecha os tipos de terminalidade.'
Require-True (Test-OrdinalContains $migration `
        '@existing_terminal_evidence_kind COLLATE Latin1_General_100_BIN2') `
    'Retry de página não compara a evidência terminal tipada.'
Require-True (Test-OrdinalContains $migration '@unidentified_quarantine_rows, @database_now_utc') 'Count audit ainda confia no relógio do caller.'
Require-True (Test-OrdinalContains $migration 'released_at_utc = @database_now_utc') 'Transição terminal ainda confia no relógio do caller.'
Require-True (Test-OrdinalContains $migration 'VALUES (@source_instance, @source_kind, 1, @database_now_utc)') 'Registro de fonte ainda confia no relógio do caller.'
Require-True (Test-OrdinalContains $migration 'VALUES (@cycle_id, @plan_version, LOWER(@plan_fingerprint), @database_now_utc)') 'Planejamento ainda confia no relógio do caller.'
Require-True (-not (Test-OrdinalContains $migration '@existing_planned_at_utc')) 'Retry de ciclo não pode depender do relógio do caller.'
foreach ($wideParameter in @(
        '@source_instance NVARCHAR(MAX)', '@source_kind NVARCHAR(MAX)',
        '@plan_version NVARCHAR(MAX)', '@plan_fingerprint NVARCHAR(MAX)',
        '@environment_name NVARCHAR(MAX)', '@tenant_scope NVARCHAR(MAX)',
        '@entity_name NVARCHAR(MAX)', '@execution_mode NVARCHAR(MAX)',
        '@window_strategy NVARCHAR(MAX)', '@contract_version NVARCHAR(MAX)',
        '@contract_fingerprint NVARCHAR(MAX)', '@configuration_version NVARCHAR(MAX)',
        '@configuration_fingerprint NVARCHAR(MAX)', '@idempotency_key NVARCHAR(MAX)',
        '@count_phase NVARCHAR(MAX)', '@expected_current_state NVARCHAR(MAX)',
        '@next_state NVARCHAR(MAX)', '@reason_code NVARCHAR(MAX)'
    )) {
    Require-True (Test-OrdinalContains $migration $wideParameter) "Parâmetro amplo ausente: $wideParameter."
}
Require-True (Test-OrdinalContains $migration 'IF DATALENGTH(@source_instance)') 'O catálogo não valida DATALENGTH.'
Require-True (Test-OrdinalContains $migration 'IF DATALENGTH(@plan_version)') 'O ciclo não valida DATALENGTH.'
Require-True (Test-OrdinalContains $migration 'DATALENGTH(@plan_fingerprint) > 128') `
    'Fingerprint Unicode do plano não possui limite pré-conversão.'
Require-True (Test-OrdinalContains $migration 'IF DATALENGTH(@environment_name)') 'O start não valida DATALENGTH.'
Require-True ($migration.IndexOf('IF DATALENGTH(@source_instance)', [StringComparison]::Ordinal) -lt $migration.IndexOf('SET @source_instance = LTRIM', [StringComparison]::Ordinal)) 'DATALENGTH deve preceder trim no catálogo.'
Require-True ($migration.IndexOf('IF DATALENGTH(@plan_version)', [StringComparison]::Ordinal) -lt $migration.IndexOf('SET @plan_version = LTRIM', [StringComparison]::Ordinal)) 'DATALENGTH deve preceder trim no ciclo.'
Require-True ($migration.IndexOf('IF DATALENGTH(@environment_name)', [StringComparison]::Ordinal) -lt $migration.IndexOf('SET @environment_name = LTRIM', [StringComparison]::Ordinal)) 'DATALENGTH deve preceder trim no start.'
Require-True (Test-OrdinalContains $migration 'OUTPUT deleted.next_attempt_number INTO @allocated_attempt') 'Attempt number não é alocado atomicamente.'
Require-True (Test-OrdinalContains $migration 'OUTPUT deleted.next_transition_sequence') 'Transition sequence não é alocada atomicamente.'
Require-True (-not (Test-OrdinalContains $migration 'MAX(attempt_number)')) 'MAX+1 de attempt_number é proibido.'
Require-True (-not (Test-OrdinalContains $migration 'MAX(transition_sequence)')) 'MAX+1 de transition_sequence é proibido.'
Require-True (Test-OrdinalContains $migration "@count_phase COLLATE Latin1_General_100_BIN2 = N'STAGING_KERNEL'") 'A API pública não reserva STAGING_KERNEL por BIN2 exato.'
Require-True (Test-OrdinalContains $migration 'FROM ctl.execution_page_audit WITH (UPDLOCK, HOLDLOCK)') 'Page retry não trava sua chave imutável.'
Require-True (Test-OrdinalContains $migration 'FROM ctl.execution_count WITH (UPDLOCK, HOLDLOCK)') 'Count retry não trava sua chave imutável.'
Require-True (Test-OrdinalContains $migration 'FROM ctl.execution_state_event WITH (UPDLOCK, HOLDLOCK)') 'Transition retry não trava seu evento imutável.'
Require-True (Test-OrdinalContains $migration 'THROW 51334') 'Page divergente não falha fechado.'
Require-True (Test-OrdinalContains $migration 'THROW 51335') 'Count divergente não falha fechado.'
Require-True (Test-OrdinalContains $migration "LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'") 'Constraint de evento aceita reason code iniciado por dígito ou underscore.'
Require-True (Test-OrdinalContains $migration "LEFT(@reason_code, 1) COLLATE Latin1_General_100_BIN2 NOT LIKE '[A-Z]'") 'Procedure de transição aceita reason code iniciado por dígito ou underscore.'
Require-True (-not (Test-OrdinalContains $migration 'payload_')) 'A migration não pode conter coluna de payload.'

$baseline = Get-Content -LiteralPath $baselinePath -Raw
Require-True (Test-OrdinalContains $baseline 'V003__create_control_plane.sql') 'O baseline não inclui V003.'
Require-True (Test-OrdinalContains $baseline 'V004__create_staging_promotion_kernel.sql') 'O baseline não inclui o entry point positivo de V004.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $validatorPath -Raw) 'Control plane V2 validado com sucesso.') 'O validator do control plane está ausente.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'ROLLBACK TRANSACTION') 'O exercício do control plane precisa fazer rollback.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'Replay de outro namespace semântico deveria falhar.') 'O exercício não prova replay incompatível.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'Retry tardio exato deve retornar pelo fast-path') 'O exercício não prova retry tardio idempotente.'
$exercise = Get-Content -LiteralPath $exercisePath -Raw
Require-True (Test-OrdinalContains $exercise 'Registro de fonte ou planejamento confiaram no relógio do caller.') 'O exercício não prova clock SQL para fonte/ciclo.'
Require-True (Test-OrdinalContains $exercise 'COLLATION_KeyA') 'O exercício não prova distinção de caixa/acento.'
Require-True (Test-OrdinalContains $exercise "N'SYNTHETIC_TENANT   '") 'O exercício não prova trim da chave semântica.'
Require-True (Test-OrdinalContains $exercise "NCHAR(9) + N'COLLATION_Tab' + NCHAR(9)") 'O exercício não prova preservação de TAB opaco.'
Require-True (Test-OrdinalContains $exercise "@count_phase COLLATE Latin1_General_100_BIN2 = N''STAGING_KERNEL''") 'O exercício não prova a reserva BIN2 exata da fase interna.'
Require-True (Test-OrdinalContains $exercise "(N'_X')") 'O exercício não rejeita reason code iniciado por underscore.'
Require-True (Test-OrdinalContains $exercise "(N'1X')") 'O exercício não rejeita reason code iniciado por dígito.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) 'O fingerprint do manifesto do control plane está desatualizado.'

Write-Output 'Manifesto e artefatos do control plane validados com sucesso.'

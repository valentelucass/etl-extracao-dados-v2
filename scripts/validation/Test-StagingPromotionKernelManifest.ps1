[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\staging-promotion-kernel.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\staging-promotion-kernel.sha256'
$migrationPath = Join-Path $repositoryRoot 'database\migrations\V004__create_staging_promotion_kernel.sql'
$lifecycleMigrationPath = Join-Path $repositoryRoot 'database\migrations\V005__create_staging_lifecycle.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\007_validate_staging_promotion_kernel.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\008_exercise_staging_promotion_kernel_rollback.sql'
$publicationValidatorPath = Join-Path $repositoryRoot 'database\validation\009_validate_atomic_publication_protocol.sql'
$publicationExercisePath = Join-Path $repositoryRoot 'database\validation\010_exercise_atomic_publication_rollback.sql'
$permitExercisePath = Join-Path $repositoryRoot 'database\validation\011_exercise_contract_permit_rollback.sql'

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
$expectedSchemas = @('ctl', 'stg', 'core', 'recon')
$expectedProcedures = @(
    'stg.usp_stage_record',
    'core.usp_prepare_staged_execution',
    'core.usp_apply_reconcile_publish_execution'
)

Require-True ($manifest.manifestVersion -eq 5) 'A versão do manifesto do kernel deve ser 5.'
Require-True ($manifest.roadmapTask -ceq 'V2-044') 'O hardening do manifesto deve pertencer a V2-044.'
Require-True ($manifest.lifecycleEvolutionTask -ceq 'V2-045a') `
    'A evolução de lineage do kernel deve pertencer a V2-045a.'
Require-True (@(Compare-Object $expectedSchemas @($manifest.schemas) -CaseSensitive).Count -eq 0) 'Os schemas do kernel divergem.'
Require-True (@(Compare-Object $expectedProcedures @($manifest.procedures) -CaseSensitive).Count -eq 0) 'As procedures do kernel divergem.'
Require-True ($manifest.runtime.directTableDml -ceq 'DENIED') 'O runtime não pode ter DML direto no kernel.'
Require-True ($manifest.runtime.proceduresOnly -eq $true) 'O runtime do kernel deve usar apenas procedures.'
Require-True ($manifest.runtime.maximumRecordsPerBatch -eq 10000) 'O teto absoluto de lote diverge.'
Require-True ($manifest.storage.rawPayload -ceq 'PROHIBITED') 'Payload bruto não pode entrar no staging.'
Require-True ($manifest.deduplication.strategy -ceq 'ROW_NUMBER') 'O dedupe set-based deve usar ROW_NUMBER.'
Require-True ($manifest.deduplication.equalFreshnessDifferentFingerprint -ceq 'QUARANTINE') 'Empate de frescor divergente deve ir para quarantine.'
Require-True ($manifest.deduplication.unknownFreshnessDifferentFingerprint -ceq 'QUARANTINE') 'Frescor desconhecido divergente deve ir para quarantine.'
Require-True ($manifest.quarantineCounting.quarantined_root_keys -like 'distinct identified source_key*') 'A contagem de raízes em quarantine diverge.'
Require-True ($manifest.quarantineCounting.unidentified_quarantine_rows -like 'physical quarantine rows without source_key*') 'Quarantine sem chave não está separado.'
Require-True ($manifest.clockAuthority.fencing -ceq 'database_clock_captured_after_lease_locks') 'O fencing deve capturar o relógio após os locks.'
Require-True ($manifest.semanticTextCollation.name -ceq 'Latin1_General_100_BIN2') 'A collation semântica deve ser BIN2 explícita.'
Require-True ($manifest.semanticTextCollation.scope -ceq 'all_persisted_nvarchar_columns_and_promotion_key_table_variables') 'BIN2 deve cobrir o storage e as table variables de dedupe.'
Require-True ($manifest.semanticTextCollation.outerSpacePolicy -ceq 'trim_only_ASCII_U+0020_after_length_validation_and_reject_noncanonical_storage') 'A canonicalização deve remover somente U+0020.'
Require-True ($manifest.semanticTextCollation.procedureVariableComparison -ceq 'explicit_Latin1_General_100_BIN2') 'Variáveis técnicas devem usar BIN2 explícito.'
Require-True ($manifest.textInputProtocol.silentPreBodyTruncation -ceq 'PROHIBITED') 'Truncamento textual pré-procedure deve ser proibido.'
Require-True ($manifest.textInputProtocol.reasonCodes -ceq 'trim_ASCII_U+0020_then_require_first_[A-Z]_and_remaining_[A-Z0-9_]_with_length_2_to_64; no_case_fold') 'A gramática canônica de reason code diverge.'
Require-True ($manifest.transactionProtocol.coreMutationBeforePublication -ceq 'SAME_TRANSACTION_ONLY') 'Aplicação ao core deve compartilhar a transação de publicação.'
Require-True ($manifest.transactionProtocol.atomicEntrypoint -ceq 'core.usp_apply_reconcile_publish_execution') 'O entrypoint atômico diverge.'
Require-True (@($manifest.transactionProtocol.application).Count -eq 6) 'O protocolo de aplicação está incompleto.'
Require-True (@($manifest.transactionProtocol.publication).Count -eq 6) 'O protocolo de publicação está incompleto.'
Require-True ($manifest.transactionProtocol.publicationRetry -like 'PUBLISHED exact retry*') 'Retry de publicação não está definido.'
Require-True ($manifest.transactionProtocol.newMutationFencing -like '*PROMOTED apply/publication require both a contract/configuration permit matching the locked execution and the same current-execution/lease fencing*') 'A aplicação positiva nova não está fenced pelo permit e pela lease.'
Require-True (@($manifest.transactionProtocol.application)[0] -like '*match_contract_and_configuration_permit_to_locked_execution*') 'O protocolo de aplicação não valida o permit sob lock.'
Require-True (@($manifest.contractPermit.parameterOrder).Count -eq 5) 'A assinatura do permit está incompleta.'
Require-True ($manifest.contractPermit.parameterOrder[0] -ceq 'execution_id') 'A ordem do permit diverge.'
Require-True ($manifest.contractPermit.parameterOrder[4] -ceq 'configuration_fingerprint') 'A ordem do permit diverge.'
Require-True ($manifest.contractPermit.comparison -ceq 'exact_Latin1_General_100_BIN2_under_execution_attempt_UPDLOCK_HOLDLOCK') 'A comparação do permit deve ser exata e sob lock.'
Require-True ($manifest.contractPermit.mismatchError -eq 51418) 'O erro sanitizado de mismatch do permit diverge.'
Require-True ($manifest.contractPermit.gatePosition -ceq 'before_PROMOTED_or_PUBLISHED_retry_and_before_any_persistent_mutation') 'O gate do permit está fora da posição segura.'
Require-True ($manifest.contractPermit.rollbackExercise -ceq 'database/validation/011_exercise_contract_permit_rollback.sql') 'O exercício rollback-only do permit diverge.'
Require-True ($manifest.contractPermit.cryptographicAttestation -eq $false) 'O manifesto não pode alegar atestação criptográfica inexistente.'
Require-True (@($manifest.transactionProtocol.publication) -contains 'lease_release_revalidates_active_database_clock_lease_and_uses_database_clock_timestamp') 'A publicação não revalida a lease no último write.'
Require-True ($manifest.transactionProtocol.stageBatch -like '*exact retry returns before lease fencing*') 'Retry exato de staging deve anteceder a lease.'
Require-True ($manifest.transactionProtocol.promotionRetry -like 'PROMOTED exact retry first requires*') 'Retry de promoção precisa validar permit e evidência persistida.'
Require-True ($manifest.transactionProtocol.internalCountPersistence -like 'direct INSERT*') 'STAGING_KERNEL deve ser persistido internamente.'
Require-True ($manifest.durableLineage.legacyBlockingForeignKeys -ceq `
        'REMOVED_BY_V005_AFTER_TYPED_ARCHIVE_CONTRACT') `
    'As FKs bloqueadoras devem ser substituídas pelo contrato de archive tipado.'

$migration = Get-Content -LiteralPath $migrationPath -Raw
foreach ($requiredText in @(
        'CREATE TABLE stg.execution_record',
        'CREATE TABLE stg.execution_candidate',
        'CREATE TABLE recon.quarantine_record',
        'CREATE TABLE core.entity_record_state',
        'CREATE TABLE ctl.execution_promotion_result',
        'CREATE TABLE recon.execution_candidate_application',
        'CREATE TABLE recon.execution_reconciliation_result',
        'CREATE OR ALTER PROCEDURE core.usp_prepare_staged_execution',
        'CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_execution',
        'ROW_NUMBER() OVER',
        'row_fingerprint_version',
        'presence_fingerprint_version',
        '@input_record_ordinal NOT BETWEEN 1 AND 10000',
        '@lease_expires_at_utc <= @database_now_utc',
        'EQUAL_FRESHNESS_CONFLICT',
        'UNKNOWN_FRESHNESS_CONFLICT',
        'unidentified_quarantine_rows',
        'quarantined_root_keys',
        'quarantined_stage_rows',
        'physical_rows = distinct_root_keys + duplicate_rows + unidentified_quarantine_rows',
        'distinct_root_keys = candidate_rows + quarantined_root_keys',
        '@duplicate_rows = @physical_rows - @distinct_rows - @unidentified_quarantine_rows',
        '@quarantine_reason_code, @database_now_utc',
        'winner.source_freshness_at_utc, @database_now_utc',
        'OUTPUT deleted.next_transition_sequence',
        'INSERT INTO ctl.execution_count',
        'CANDIDATE_SET_PREPARED',
        "N'CANDIDATE_SET_RECONCILED'",
        "N'RECONCILIATION_PUBLISHED'",
        'sys.sp_getapplock',
        "N'INSERTED', N'UPDATED', N'REACTIVATED', N'NO_OP', N'STALE_NO_OP'",
        'INSERT INTO core.entity_record_state',
        'UPDATE current_record',
        'INSERT INTO recon.execution_candidate_application',
        'INSERT INTO recon.execution_reconciliation_result',
        'INSERT INTO ctl.partition_publication_pointer',
        'UPDATE ctl.incremental_publication_watermark',
        'SET released_at_utc = @published_at_utc',
        'DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::stg TO v2_runtime',
        'DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::core TO v2_runtime',
        'DENY INSERT, UPDATE, DELETE, SELECT ON SCHEMA::recon TO v2_runtime'
    )) {
    Require-True (Test-OrdinalContains $migration $requiredText) "A migration não cobre $requiredText."
}
foreach ($procedure in $expectedProcedures) {
    $parts = $procedure.Split('.', 2)
    Require-True (Test-OrdinalContains $migration "N'$($parts[0])', N'$($parts[1])', N'v2_runtime'") "A publicação de $procedure está ausente."
}
Require-True (-not (Test-OrdinalContains $migration 'payload_json')) 'O kernel não pode persistir payload JSON.'
Require-True (-not (Test-OrdinalContains $migration 'MERGE ')) 'O kernel não pode promover com MERGE.'
Require-True (-not (Test-OrdinalContains $migration 'MAX(transition_sequence)')) 'A promoção não pode usar MAX+1.'
$prepareStart = $migration.IndexOf('CREATE OR ALTER PROCEDURE core.usp_prepare_staged_execution', [StringComparison]::Ordinal)
$applyStart = $migration.IndexOf('CREATE OR ALTER PROCEDURE core.usp_apply_reconcile_publish_execution', [StringComparison]::Ordinal)
Require-True ($prepareStart -ge 0 -and $applyStart -gt $prepareStart) 'Não foi possível isolar preparação e aplicação.'
$prepareBody = $migration.Substring($prepareStart, $applyStart - $prepareStart)
$applyBody = $migration.Substring($applyStart)
Require-True (-not (Test-OrdinalContains $prepareBody 'core.entity_record_state')) 'A preparação do candidate set não pode alterar core.'
Require-True (-not (Test-OrdinalContains $prepareBody 'partition_publication_pointer')) 'A preparação não pode publicar pointer.'
Require-True (-not (Test-OrdinalContains $prepareBody 'incremental_publication_watermark')) 'A preparação não pode avançar watermark.'
foreach ($procedureBody in @($prepareBody, $applyBody)) {
    foreach ($permitParameter in @(
            '@execution_id UNIQUEIDENTIFIER', '@contract_version NVARCHAR(MAX)',
            '@contract_fingerprint NVARCHAR(MAX)', '@configuration_version NVARCHAR(MAX)',
            '@configuration_fingerprint NVARCHAR(MAX)'
        )) {
        Require-True (Test-OrdinalContains $procedureBody $permitParameter) "Parâmetro do permit ausente: $permitParameter."
    }
    Require-True (Test-OrdinalContains $procedureBody 'THROW 51418') 'Mismatch do permit não falha fechado.'
    Require-True (Test-OrdinalContains $procedureBody 'IF @persisted_contract_version IS NULL') 'Permit persistido nulo não falha fechado.'
    Require-True (Test-OrdinalContains $procedureBody 'OR @persisted_configuration_fingerprint IS NULL') 'Fingerprint persistido nulo não falha fechado.'
    Require-True (Test-OrdinalContains $procedureBody '@persisted_contract_version COLLATE Latin1_General_100_BIN2') 'Versão do contrato não é comparada sob BIN2.'
    Require-True (Test-OrdinalContains $procedureBody '@persisted_configuration_fingerprint COLLATE Latin1_General_100_BIN2') 'Fingerprint de configuração não é comparado sob BIN2.'
    Require-True ($procedureBody.IndexOf('DATALENGTH(@contract_version)', [StringComparison]::Ordinal) -lt $procedureBody.IndexOf('SET @contract_version = LTRIM(RTRIM(@contract_version))', [StringComparison]::Ordinal)) 'DATALENGTH deve preceder trim no permit.'
}
Require-True ($prepareBody.IndexOf('THROW 51418', [StringComparison]::Ordinal) -lt $prepareBody.IndexOf("@execution_state COLLATE Latin1_General_100_BIN2 = N'PROMOTED'", [StringComparison]::Ordinal)) 'O gate prepare deve preceder o retry PROMOTED.'
Require-True ($applyBody.IndexOf('THROW 51418', [StringComparison]::Ordinal) -lt $applyBody.IndexOf("@execution_state COLLATE Latin1_General_100_BIN2 = N'PUBLISHED'", [StringComparison]::Ordinal)) 'O gate apply deve preceder o retry PUBLISHED.'
$prepareLockedRead = $prepareBody.IndexOf('FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)', [StringComparison]::Ordinal)
$prepareNullGate = $prepareBody.IndexOf('IF @persisted_contract_version IS NULL', [StringComparison]::Ordinal)
Require-True ($prepareLockedRead -ge 0 -and $prepareLockedRead -lt $prepareNullGate -and $prepareNullGate -lt $prepareBody.IndexOf('THROW 51418', [StringComparison]::Ordinal)) 'O gate prepare deve comparar o permit depois do row lock.'
$applyAppLock = $applyBody.IndexOf('EXEC @application_lock_result = sys.sp_getapplock', [StringComparison]::Ordinal)
$applyLockedRead = $applyBody.IndexOf('FROM ctl.execution_attempt AS attempt WITH (UPDLOCK, HOLDLOCK)', [StringComparison]::Ordinal)
$applyNullGate = $applyBody.IndexOf('IF @persisted_contract_version IS NULL', [StringComparison]::Ordinal)
Require-True ($applyAppLock -ge 0 -and $applyAppLock -lt $applyLockedRead -and $applyLockedRead -lt $applyNullGate -and $applyNullGate -lt $applyBody.IndexOf('THROW 51418', [StringComparison]::Ordinal)) 'O gate apply deve comparar o permit depois do applock e row lock.'
foreach ($wideParameter in @(
        '@source_key NVARCHAR(MAX)', '@row_fingerprint_version NVARCHAR(MAX)',
        '@source_row_hash NVARCHAR(MAX)', '@presence_fingerprint_version NVARCHAR(MAX)',
        '@presence_fingerprint NVARCHAR(MAX)', '@validation_disposition NVARCHAR(MAX)',
        '@quarantine_reason_code NVARCHAR(MAX)'
    )) {
    Require-True (Test-OrdinalContains $migration $wideParameter) "Parâmetro amplo ausente: $wideParameter."
}
Require-True (Test-OrdinalContains $migration 'DATALENGTH(@source_key) > 512') 'A chave não valida seu limite antes do corpo.'
Require-True (Test-OrdinalContains $migration 'DATALENGTH(@source_row_hash) > 128') `
    'Hash Unicode da linha não possui limite pré-conversão.'
Require-True ($migration.IndexOf('IF DATALENGTH(@source_key)', [StringComparison]::Ordinal) -lt $migration.IndexOf('SET @source_key = NULLIF(LTRIM', [StringComparison]::Ordinal)) 'DATALENGTH deve preceder trim no staging.'
Require-True (Test-OrdinalContains $migration 'COLLATE Latin1_General_100_BIN2') 'O kernel não declara BIN2.'
Require-True (-not (Test-OrdinalContains $migration 'EXEC ctl.usp_control_plane_record_counts')) 'Promoção não pode chamar a API pública de contagens reservada.'
Require-True (Test-OrdinalContains $migration 'THROW 51403') 'Retry divergente de staging não falha fechado.'
Require-True (Test-OrdinalContains $migration 'Execução promovida sem candidate set e auditoria coerentes.') 'Retry de promoção não valida evidência persistida.'
Require-True (Test-OrdinalContains $migration "LEFT(quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2") 'Constraint de staging aceita reason iniciado por dígito ou underscore.'
Require-True (Test-OrdinalContains $migration "LEFT(reason_code, 1) COLLATE Latin1_General_100_BIN2 LIKE '[A-Z]'") 'Ledger de quarantine aceita reason iniciado por dígito ou underscore.'
Require-True (Test-OrdinalContains $migration "LEFT(@quarantine_reason_code, 1) COLLATE Latin1_General_100_BIN2") 'Procedure de staging aceita reason iniciado por dígito ou underscore.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $baselinePath -Raw) 'V004__create_staging_promotion_kernel.sql') 'O baseline não inclui V004.'
$lifecycleMigration = Get-Content -LiteralPath $lifecycleMigrationPath -Raw
Require-True (Test-OrdinalContains $lifecycleMigration 'trg_quarantine_record_staging_lineage') `
    'A lineage de quarantine para staging/archive está ausente.'
Require-True (Test-OrdinalContains $lifecycleMigration 'trg_candidate_application_staging_lineage') `
    'A lineage de application para candidate/archive está ausente.'
Require-True (Test-OrdinalContains $lifecycleMigration `
        'DROP CONSTRAINT FK_recon_quarantine_record_stage') `
    'A FK bloqueadora de quarantine não foi substituída.'
Require-True (Test-OrdinalContains $lifecycleMigration `
        'DROP CONSTRAINT FK_recon_execution_candidate_application_candidate') `
    'A FK bloqueadora de application não foi substituída.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $validatorPath -Raw) 'Kernel de staging e promoção V2 validado com sucesso.') 'O validator do kernel está ausente.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'ROLLBACK TRANSACTION') 'O exercício do kernel precisa fazer rollback.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'unidentified_quarantine_rows = 1') 'O exercício não prova quarantine sem chave separado.'
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'SOURCE_KEY_MISSING') 'O exercício não preserva o quarantine sem chave.'
$exercise = Get-Content -LiteralPath $exercisePath -Raw
Require-True (Test-OrdinalContains $exercise "N'synthetic-KeyA'") 'O exercício não prova caixa distinta em source_key.'
Require-True (Test-OrdinalContains $exercise "N'synthetic-ação'") 'O exercício não prova acento distinto em source_key.'
Require-True (Test-OrdinalContains $exercise 'Chave oversized com o mesmo prefixo foi truncada silenciosamente.') 'O exercício não prova rejeição pré-truncamento.'
Require-True (Test-OrdinalContains $exercise 'physical_rows = 14') 'As contagens da fixture BIN2/TAB não foram recalculadas.'
Require-True (Test-OrdinalContains $exercise "NCHAR(9) + N'synthetic-tab' + NCHAR(9)") 'O exercício não preserva TAB em source_key opaca.'
Require-True (Test-OrdinalContains $exercise 'A promoção não pode avançar watermark incremental.') 'O exercício não prova watermark fail-closed.'
Require-True (Test-OrdinalContains $exercise 'Retry com lease expirada duplicou evidência de staging ou promoção.') 'O exercício não prova retry tardio completo.'
Require-True (Test-OrdinalContains $exercise "(N'_X')") 'O exercício não rejeita quarantine reason iniciado por underscore.'
Require-True (Test-OrdinalContains $exercise "(N'1X')") 'O exercício não rejeita quarantine reason iniciado por dígito.'
Require-True (Test-Path -LiteralPath $publicationValidatorPath -PathType Leaf) 'O validator do protocolo atômico está ausente.'
Require-True (Test-Path -LiteralPath $publicationExercisePath -PathType Leaf) 'O exercício rollback-only do protocolo atômico está ausente.'
Require-True (Test-Path -LiteralPath $permitExercisePath -PathType Leaf) 'O exercício rollback-only do permit está ausente.'
$publicationExercise = Get-Content -LiteralPath $publicationExercisePath -Raw
Require-True (Test-OrdinalContains $publicationExercise 'ROLLBACK TRANSACTION') 'O exercício atômico precisa fazer rollback.'
Require-True (Test-OrdinalContains $publicationExercise 'usp_apply_reconcile_publish_execution') 'O exercício não invoca o entrypoint atômico.'
Require-True (Test-OrdinalContains $publicationExercise 'STALE_NO_OP') 'O exercício não prova stale no-op.'
Require-True (Test-OrdinalContains $publicationExercise 'REACTIVATED') 'O exercício não prova reativação.'
$permitExercise = Get-Content -LiteralPath $permitExercisePath -Raw
Require-True (Test-OrdinalContains $permitExercise 'ERROR_NUMBER()') 'O exercício do permit não captura o erro SQL.'
Require-True (Test-OrdinalContains $permitExercise '@prepare_error IS NULL OR @prepare_error <> 51418') 'O exercício não prova mismatch antes da promoção sem bypass por NULL.'
Require-True (Test-OrdinalContains $permitExercise '@promoted_error IS NULL OR @promoted_error <> 51418') 'O exercício não prova mismatch antes da aplicação sem bypass por NULL.'
Require-True (Test-OrdinalContains $permitExercise '@apply_error IS NULL OR @apply_error <> 51418') 'O exercício não prova mismatch no retry publicado sem bypass por NULL.'
Require-True (([regex]::Matches($permitExercise, 'BEGIN TRANSACTION;')).Count -eq 3) 'O exercício do permit exige três cenários transacionais independentes.'
foreach ($permitState in @("N'STAGED'", "N'PROMOTED'", "N'PUBLISHED'")) {
    Require-True (Test-OrdinalContains $permitExercise $permitState) "O exercício não cobre o estado $permitState."
}
foreach ($sentinel in @('PENDING_PREPARE', 'PENDING_APPLY', 'PENDING_RETRY')) {
    Require-True (Test-OrdinalContains $permitExercise $sentinel) "O sentinel $sentinel está ausente."
}
Require-True (Test-OrdinalContains $permitExercise '@promoted_application_after <> @promoted_application_before') 'O cenário PROMOTED não prova ausência de aplicação.'
Require-True (Test-OrdinalContains $permitExercise '@apply_pointer_published_after <> @apply_pointer_published_before') 'O retry PUBLISHED não prova ausência de rewrite do pointer.'
Require-True (Test-OrdinalContains $permitExercise '@apply_lease_after <> @apply_lease_before') 'O retry PUBLISHED não prova ausência de rewrite da lease.'
Require-True (Test-OrdinalContains $permitExercise '_error_xact_state <> -1') 'O exercício não prova XACT_ABORT fail-closed.'
Require-True (Test-OrdinalContains $permitExercise 'ROLLBACK TRANSACTION') 'O exercício do permit precisa fazer rollback.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) 'O fingerprint do manifesto do kernel está desatualizado.'

Write-Output 'Manifesto e artefatos do kernel de staging/promoção validados com sucesso.'

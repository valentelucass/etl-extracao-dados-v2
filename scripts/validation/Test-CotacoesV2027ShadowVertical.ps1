#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$decisionPath = Join-Path $root 'docs\catalogos\cotacoes-v2-027\decisao-v01.json'
$manifestPath = Join-Path $root 'database\manifest\cotacoes-shadow-vertical.json'
$manifestFingerprintPath = Join-Path $root 'database\manifest\cotacoes-shadow-vertical.sha256'
$migrationPath = Join-Path $root 'database\migrations\V011__create_cotacoes_shadow_vertical.sql'
$validatorPath = Join-Path $root 'database\validation\040_validate_cotacoes_shadow_vertical.sql'
$progressiveValidatorPath = Join-Path $root 'database\validation\005_validate_progressive_data_gate.sql'
$exercisePath = Join-Path $root 'database\validation\041_exercise_cotacoes_shadow_vertical_rollback.sql'
$tenantExercisePath = Join-Path $root 'database\validation\support_cotacoes_v2_015e_cross_tenant.sql'
$concurrencyPath = Join-Path $root 'scripts\validation\Test-CotacoesShadowConcurrency.ps1'
$matrixPath = Join-Path $root 'docs\catalogos\portabilidade\matriz-campos.csv'

function Require-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-SqlSection {
    param([string]$Text, [string]$Start, [string]$End)
    $startIndex = $Text.IndexOf($Start, [StringComparison]::Ordinal)
    $endIndex = if ($startIndex -ge 0) {
        $Text.IndexOf($End, $startIndex + $Start.Length, [StringComparison]::Ordinal)
    } else { -1 }
    Require-True ($startIndex -ge 0 -and $endIndex -gt $startIndex) `
        "Seção SQL ausente ou fora de ordem: $Start -> $End."
    return $Text.Substring($startIndex, $endIndex - $startIndex)
}

function Normalize-Sql {
    param([string]$Text)
    return ([regex]::Replace($Text, '\s+', '')).ToLowerInvariant()
}

$decision = Get-Content -LiteralPath $decisionPath -Raw | ConvertFrom-Json
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$recordedManifestFingerprint = ((Get-Content -LiteralPath $manifestFingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$migration = Get-Content -LiteralPath $migrationPath -Raw
$validator = Get-Content -LiteralPath $validatorPath -Raw
$progressiveValidator = Get-Content -LiteralPath $progressiveValidatorPath -Raw
$exercise = Get-Content -LiteralPath $exercisePath -Raw
$tenantExercise = Get-Content -LiteralPath $tenantExercisePath -Raw
$concurrency = Get-Content -LiteralPath $concurrencyPath -Raw
$matrix = Get-Content -LiteralPath $matrixPath -Raw

Require-True ($decision.identity.sourceKey.path -ceq '/sequence_code') 'COT-01 deve usar somente /sequence_code.'
Require-True ($decision.identity.sourceKey.wireType -ceq 'INTEGER') 'COT-01 exige chave INTEGER type-tagged.'
Require-True ($decision.identity.sourceKey.domain -ceq 'POSITIVE_SIGNED_64_BIT' `
        -and [string]$decision.identity.sourceKey.minimum -ceq '1' `
        -and [string]$decision.identity.sourceKey.maximum -ceq '9223372036854775807' `
        -and $decision.identity.sourceKey.canonicalEncoding -ceq 'ASCII_DECIMAL_NO_SIGN_NO_LEADING_ZERO_NO_WHITESPACE') `
    'COT-01 deve limitar sequence_code ao BIGINT positivo canônico.'
Require-True (@($decision.identity.forbiddenTenantSentinels).Count -eq 3) 'As sentinelas de escopo devem ser explícitas.'
Require-True ($decision.attributes.originUf.path -ceq '/data/qoe_qes_ony_sae_code') 'A UF origem não está ancorada.'
Require-True ($decision.attributes.destinationUf.path -ceq '/data/qoe_qes_diy_sae_code') 'A UF destino não está ancorada.'
Require-True ($decision.attributes.originUf.matrixId -ceq 'DE-DATA-6906-007' -and $decision.attributes.destinationUf.matrixId -ceq 'DE-DATA-6906-009') 'A matriz V2-017a de UFs diverge.'
Require-True ($matrix.Contains('"DE-DATA-6906-007"') -and $matrix.Contains('"DE-DATA-6906-009"')) 'A evidência estática de UF está ausente.'
Require-True ($decision.tariff.reference -ceq 'ref.tarifa_rota_uf') 'COT-02 não pode usar outra referência tarifária.'
Require-True ($decision.presenceSemantics.absent -ceq 'PRESERVE_SAME_SCOPED_CURRENT_ROOT' `
        -and $decision.presenceSemantics.null -ceq 'APPLY_SQL_NULL' `
        -and $decision.presenceSemantics.value -ceq 'APPLY_TYPED_VALUE_INCLUDING_ZERO') `
    'A decisão não explicita ABSENT/NULL/VALUE sem colapso.'
$expectedPresenceFields = @(
    'sequence_code', 'requested_at', 'qoe_qes_fit_nse_issued_at',
    'qoe_qes_fit_fhe_cte_issued_at', 'qoe_qes_total', 'qoe_crn_psn_nickname',
    'qoe_uer_name', 'qoe_qes_ony_sae_code', 'qoe_qes_diy_sae_code'
)
Require-True ((@($decision.presenceSemantics.requiredFields) -join '|') -ceq `
        ($expectedPresenceFields -join '|')) `
    'A decisão deve enumerar exatamente os nove tokens de presença 6906.'
Require-True ((@($decision.tariff.effectiveRouteScope) -join '|') -ceq `
        'environment_name|source_instance|tenant_scope|entity_name|source_key' `
        -and $decision.tariff.sourceCurrency -ceq 'FORBIDDEN_REFERENCE_ONLY') `
    'A rota efetiva deve permanecer na mesma raiz escopada e a moeda deve vir da referência.'
Require-True ($manifest.localState -ceq 'IMPLEMENTADA_EM_SHADOW') 'O manifesto deve registrar somente a implementação comprovada em sombra.'
Require-True ($manifest.identity.sourceKeyDomain -ceq 'POSITIVE_SIGNED_64_BIT_1_TO_9223372036854775807' `
        -and $manifest.identity.sourceKeyEncoding -ceq `
            'ASCII_DECIMAL_NO_SIGN_NO_LEADING_ZERO_NO_WHITESPACE' `
        -and $manifest.attributes.absent -ceq 'PRESERVE_SAME_SCOPED_CURRENT_ROOT' `
        -and $manifest.attributes.null -ceq 'APPLY_SQL_NULL' `
        -and $manifest.attributes.value -ceq 'APPLY_TYPED_VALUE_INCLUDING_ZERO' `
        -and $manifest.tariff.sourceCurrency -ceq 'FORBIDDEN_REFERENCE_ONLY') `
    'O manifesto físico não fecha BIGINT, tri-state e moeda reference-only.'
Require-True ((@($manifest.attributes.presenceFields) -join '|') -ceq `
        ($expectedPresenceFields -join '|') `
        -and (@($manifest.tariff.effectiveRouteScope) -join '|') -ceq `
            'environment_name|source_instance|tenant_scope|entity_name|source_key') `
    'O manifesto físico diverge nos nove tokens ou no escopo da rota efetiva.'
Require-True ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $recordedManifestFingerprint) 'O fingerprint do manifesto de Cotações está desatualizado.'
$stageTable = Get-SqlSection $migration 'CREATE TABLE stg.cotacao_record' 'CREATE INDEX IX_stg_cotacao_execution_source'
$coreTable = Get-SqlSection $migration 'CREATE TABLE core.cotacao' 'CREATE TABLE recon.cotacao_root_presence_observation'
$stageProcedure = Get-SqlSection $migration 'CREATE OR ALTER PROCEDURE stg.usp_stage_cotacao_record' 'CREATE OR ALTER TRIGGER ctl.trg_cotacao_prepare_candidate_set'
$stageIdentity = Get-SqlSection $stageTable 'CONSTRAINT CK_stg_cotacao_identity' 'CONSTRAINT CK_stg_cotacao_json'
$coreIdentity = Get-SqlSection $coreTable 'CONSTRAINT CK_core_cotacao_identity' 'CONSTRAINT CK_core_cotacao_json'
$stagePresence = Get-SqlSection $stageTable 'CONSTRAINT CK_stg_cotacao_presence' 'CONSTRAINT CK_stg_cotacao_source_currency'
$corePresence = Get-SqlSection $coreTable 'CONSTRAINT CK_core_cotacao_presence' 'CONSTRAINT CK_core_cotacao_source_currency'
$normalizedStageProcedure = Normalize-Sql $stageProcedure
foreach ($identitySection in @($stageIdentity, $coreIdentity)) {
    $normalizedIdentity = Normalize-Sql $identitySection
    Require-True ($normalizedIdentity.Contains('try_convert(bigint') `
            -and -not $normalizedIdentity.Contains('try_convert(int') `
            -and $normalizedIdentity.Contains('>convert(bigint,0)') `
            -and $normalizedIdentity.Contains('datalength(source_key)') `
            -and $normalizedIdentity.Contains("source_key=concat(n'integer:'")) `
        'Cada constraint COT-01 deve impor BIGINT positivo e igualdade decimal canônica.'
}
Require-True ((Normalize-Sql $stageTable).Contains('source_keynvarchar(256)collatelatin1_general_100_bin2') `
        -and (Normalize-Sql $coreTable).Contains('source_keynvarchar(256)collatelatin1_general_100_bin2') `
        -and $normalizedStageProcedure.Contains("@source_keycollatelatin1_general_100_bin2<>concat(n'integer:'") `
        -and $normalizedStageProcedure.Contains('datalength(@source_key)') `
        -and $migration.Contains("N'DEFAULT',N'GLOBAL',N'SINGLETON'")) `
    'As três barreiras COT-01 não impõem comparação BIN2 e forma canônica.'
$presenceVariables = [ordered]@{
    '$.sequence_code' = 'presence_sequence'
    '$.requested_at' = 'presence_requested'
    '$.qoe_qes_fit_nse_issued_at' = 'presence_nfse'
    '$.qoe_qes_fit_fhe_cte_issued_at' = 'presence_cte'
    '$.qoe_qes_total' = 'presence_total'
    '$.qoe_crn_psn_nickname' = 'presence_nickname'
    '$.qoe_uer_name' = 'presence_user'
    '$.qoe_qes_ony_sae_code' = 'presence_origin'
    '$.qoe_qes_diy_sae_code' = 'presence_destination'
}
foreach ($presenceEntry in $presenceVariables.GetEnumerator()) {
    $constraintToken = "coalesce(json_value(field_presence_json,n'$($presenceEntry.Key)'),n'invalid')"
    $procedureToken = "coalesce(@$($presenceEntry.Value),n'invalid')"
    Require-True ((Normalize-Sql $stagePresence).Contains($constraintToken) `
            -and (Normalize-Sql $corePresence).Contains($constraintToken) `
            -and $normalizedStageProcedure.Contains($procedureToken)) `
        "Presença não fail-closed nas três barreiras para $($presenceEntry.Key)."
}
Require-True ($migration.Contains('EQUAL_FRESHNESS_CONFLICT') -or $validator.Contains('EQUAL_FRESHNESS_CONFLICT')) 'O empate divergente não tem reason code estável.'
Require-True ($migration.Contains('@tariff_resolution') -and $migration.Contains("r.family_code=N'QUOTE_TARIFF'")) 'A resolução tarifária COT-02 está incompleta.'
Require-True ($migration.Contains('@effective_candidates') `
        -and $migration.IndexOf('DECLARE @effective_candidates TABLE', [StringComparison]::Ordinal) `
          -lt $migration.IndexOf('DECLARE @tariff_resolution TABLE', [StringComparison]::Ordinal) `
        -and $migration.Contains("WHEN N'ABSENT' THEN current_record.user_name_normalized") `
        -and $migration.Contains('effective.origin_uf') `
        -and $migration.Contains('@currency_code IS NOT NULL')) `
    'Tri-state, UFs efetivas ou moeda reference-only não estão materializados antes da tarifa.'
Require-True ($migration.Contains("rat.activation_scope=N'SHADOW'") -and $migration.Contains('reference_release_revocation')) 'A ratificação/revogação da release não falha fechado.'
Require-True ($migration.Contains('matches.matching_rows=1') -and $migration.Contains("tariff.coverage_state=N'PRICED'")) 'Sobreposição ou cobertura tarifária não falham fechado.'
Require-True (-not $migration.Contains('MERGE ') -and -not $migration.Contains('CREATE VIEW pub.') -and -not $migration.Contains('active=0')) 'V011 contém publicação, MERGE ou desativação proibida.'
Require-True ($validator.Contains('Contrato estrutural de Cotações divergiu.') -and $exercise.Contains('ROLLBACK TRANSACTION')) 'Faltam validador estrutural ou exercício rollback-only.'
Require-True ($progressiveValidator.Contains("N'cotacao_promotion_result'") -and $progressiveValidator.Contains("N'usp_apply_reconcile_publish_cotacoes'") -and $progressiveValidator.Contains('<> 41')) 'O gate progressivo não preservou V011 após a extensão V014.'
Require-True ($exercise.Contains('005_validate_progressive_data_gate.sql')) 'O exercício não inclui o gate progressivo reconciliado.'
Require-True ($exercise.Contains("DB_NAME() <> N'`$(DatabaseName)'")) 'O exercício não está restrito ao alvo V2 local.'
Require-True ($exercise.Contains('9223372036854775807') `
        -and $exercise.Contains('9223372036854775808') `
        -and $exercise.Contains('DECLARE invalid_identity CURSOR LOCAL FAST_FORWARD') `
        -and $exercise.Contains('EXEC stg.usp_stage_cotacao_record') `
        -and $exercise.Contains('cotacoes-tri-state-absent') `
        -and $exercise.Contains(':r "support_cotacoes_v2_015e_cross_tenant.sql"') `
        -and $exercise.Contains('EQUAL_FRESHNESS_CONFLICT')) `
    'O exercício não cobre BIGINT, tri-state, isolamento de tenant e empate divergente.'
Require-True ($tenantExercise.Contains("N'cotacoes-tenant-b-absent-insert'") `
        -and $tenantExercise.Contains('@tenant_b, @source_key') `
        -and $tenantExercise.Contains('@presence_absent') `
        -and $tenantExercise.Contains('tenant_scope = @tenant_b') `
        -and $tenantExercise.Contains('ERROR_NUMBER() = 51914') `
        -and $tenantExercise.Contains('ROLLBACK TRANSACTION')) `
    'O exercício físico não prova insert ABSENT e isolamento pela tuple do tenant B.'
Require-True ($exercise.Contains('BLOCKED_NO_COMPLETENESS_PROOF') -or $migration.Contains('BLOCKED_NO_COMPLETENESS_PROOF')) 'A ausência não está bloqueada explicitamente.'
Require-True ($concurrency.Contains('Start-Process') -and $concurrency.Contains('-WindowStyle Hidden') -and $concurrency.Contains('COTACOES_CONTENTION_CONFIRMED')) 'A prova concorrente de Cotações está incompleta.'

Write-Output 'PASS: contrato V2-027 de Cotações, exercício rollback-only e prova concorrente validados localmente.'

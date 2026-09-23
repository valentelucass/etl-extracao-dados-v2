[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$decisionPath = Join-Path $root 'docs\catalogos\manifestos-v2-026\decisao-v03.json'
$identityPath = Join-Path $root 'docs\catalogos\identidade-manifestos\manifesto.json'
$migrationPath = Join-Path $root 'database\migrations\V012__create_manifestos_shadow_vertical.sql'
$manifestPath = Join-Path $root 'database\manifest\manifestos-shadow-vertical.json'
$manifestFingerprintPath = Join-Path $root 'database\manifest\manifestos-shadow-vertical.sha256'
$validatorPath = Join-Path $root 'database\validation\042_validate_manifestos_shadow_vertical.sql'
$exercisePath = Join-Path $root 'database\validation\043_exercise_manifestos_shadow_vertical_rollback.sql'
$concurrencyPath = Join-Path $root 'scripts\validation\Test-ManifestosShadowConcurrency.ps1'
$baselinePath = Join-Path $root 'database\baseline\001_schema_foundation_baseline.sql'
$progressivePath = Join-Path $root 'database\validation\005_validate_progressive_data_gate.sql'
$mapperPath = Join-Path $root 'src\main\java\br\com\esl\etl\v2\modulos\manifestos\aplicacao\ManifestoDataExportRecordMapper.java'
$reducerPath = Join-Path $root 'src\main\java\br\com\esl\etl\v2\modulos\manifestos\domain\ManifestoRootReducer.java'
$textFieldsPath = Join-Path $root 'src\main\java\br\com\esl\etl\v2\modulos\manifestos\domain\ManifestoTextField.java'

function Require-True {
    param([bool]$Condition,[string]$Message)
    if (-not $Condition) { throw $Message }
}

$decision = Get-Content -LiteralPath $decisionPath -Raw | ConvertFrom-Json
$identity = Get-Content -LiteralPath $identityPath -Raw | ConvertFrom-Json
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$migration = Get-Content -LiteralPath $migrationPath -Raw
$validator = Get-Content -LiteralPath $validatorPath -Raw
$exercise = Get-Content -LiteralPath $exercisePath -Raw
$concurrency = Get-Content -LiteralPath $concurrencyPath -Raw
$mapper = Get-Content -LiteralPath $mapperPath -Raw
$reducer = Get-Content -LiteralPath $reducerPath -Raw
$textFields = Get-Content -LiteralPath $textFieldsPath -Raw
$textFieldLines = Get-Content -LiteralPath $textFieldsPath

Require-True ($decision.execution.status -ceq 'NOT_STARTED' -and $decision.execution.owner -ceq 'V04') 'V03 deve permanecer decisão histórica, sem execução reaberta.'
Require-True ($identity.identity.sourceKey.path -ceq '/sequence_code') 'A raiz P01 de Manifestos divergiu.'
Require-True ($manifest.localState -ceq 'IMPLEMENTADA_EM_SHADOW' -and $manifest.templateId -eq 6399) 'O manifesto V04 não registra a vertical shadow 6399.'
Require-True (@($manifest.reducers).Count -eq 4 -and @($manifest.reducers) -ccontains 'MAN-01' -and @($manifest.reducers) -ccontains 'MAN-02' -and @($manifest.reducers) -ccontains 'MAN-04' -and @($manifest.reducers) -ccontains 'MAN-07') 'Os reducers congelados não estão todos registrados.'
Require-True ($manifest.relationshipCandidate.state -ceq 'APPEND_ONLY_UNRESOLVED_V2_046A' -and -not $manifest.relationshipCandidate.foreignKeyToColeta -and -not $manifest.relationshipCandidate.lookupToColeta) 'O candidato Manifesto--Coleta foi resolvido indevidamente.'
Require-True ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq ((Get-Content -LiteralPath $manifestFingerprintPath -Raw).Trim().ToLowerInvariant())) 'O fingerprint do manifesto de Manifestos está desatualizado.'
foreach ($required in @('stg.manifesto_observation','stg.manifesto_reduced_candidate','core.manifesto','core.manifesto_pick','core.manifesto_mdfe','recon.manifesto_coleta_relation_candidate','usp_stage_manifesto_observation','usp_stage_manifesto_reduced_candidate','usp_prepare_manifesto_candidate_set','usp_apply_reconcile_publish_manifestos','EQUAL_FRESHNESS_CONFLICT','BLOCKED_NO_COMPLETENESS_PROOF','DATALENGTH(text_value.[value])')) {
    Require-True ($migration.Contains($required)) "A migration V012 não contém $required."
}
foreach ($field in @('status','mdfe_status','mft_cat_cot_number','mft_aoe_comments','closing_comments','mft_s_n_svs_sge_sse_name')) {
    Require-True ($textFields.Contains('("' + $field + '"')) "O limite textual $field está ausente no enum."
}
Require-True (($textFieldLines | Select-String -SimpleMatch '("' | Measure-Object).Count -eq 27) 'A política Unicode deve conter exatamente 27 paths.'
Require-True ($mapper.Contains('parseOffsetInstant') -and $mapper.Contains('INVALID_FRESHNESS_TEMPORAL') -and $mapper.Contains('MISSING_VALID_FRESHNESS') -and $mapper.Contains('INVALID_COMPETENCE_TEMPORAL')) 'O mapper não fecha temporal/fallback.'
Require-True ($reducer.Contains('STATUS_PRECEDENCE') -and $reducer.Contains('MDFE_ATTRIBUTE_CONFLICT') -and $reducer.Contains('BigDecimal::compareTo') -and $reducer.Contains('EQUAL_FRESHNESS_CONFLICT')) 'Os reducers não provam MAN-01/02/04/07.'
Require-True (-not $migration.Contains('REFERENCES core.coleta') -and -not $migration.Contains('JOIN core.coleta') -and -not $migration.Contains('CREATE VIEW pub.') -and -not $migration.Contains('DELETE FROM core.manifesto') -and -not $migration.Contains('active=0') -and -not $migration.Contains('MERGE ')) 'V012 contém relação, publicação ou mecanismo destrutivo proibido.'
Require-True ((Get-Content -LiteralPath $baselinePath -Raw).Contains('V012__create_manifestos_shadow_vertical.sql')) 'O baseline não inclui V012.'
Require-True ((Get-Content -LiteralPath $progressivePath -Raw).Contains("N'manifesto_promotion_result'") -and (Get-Content -LiteralPath $progressivePath -Raw).Contains("N'usp_apply_reconcile_publish_manifestos'")) 'O gate progressivo não permite a vertical V04.'
Require-True ($validator.Contains('Manifestos V2-026 em sombra validados com sucesso.') -and $validator.Contains('FK_core_manifesto_pick_owner') -and $validator.Contains('FK_core_manifesto_mdfe_owner')) 'O validador SQL não prova ownership e estrutura V04.'
Require-True ($exercise.Contains('BEGIN TRANSACTION;') -and $exercise.Contains(':r "042_validate_manifestos_shadow_vertical.sql"') -and $exercise.Contains('CONVERT(NVARCHAR(MAX),N''x'')') -and $exercise.Contains('ROLLBACK TRANSACTION;')) 'O exercício V04 não prova staging e overflow em transação revertida.'
Require-True ($concurrency.Contains('MANIFESTOS_CONTENTION_CONFIRMED') -and $concurrency.Contains('MANIFESTOS_OTHER_ENVIRONMENT_ISOLATED') -and $concurrency.Contains('MANIFESTOS_LOCK_REACQUIRED_AFTER_ROLLBACK') -and $concurrency.Contains("-d master") -and $concurrency.Contains('UPDLOCK,HOLDLOCK,INDEX(UQ_core_manifesto_source)')) 'O probe concorrente V04 não fecha contenção, isolamento e rollback locais.'

Write-Output 'PASS: Manifestos V2-026 em shadow, reducers, ownership, Unicode, exercício rollback, concorrência e relação não resolvida validados estaticamente.'

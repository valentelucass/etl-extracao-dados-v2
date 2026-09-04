[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\usuarios-dimension-current.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\usuarios-dimension-current.sha256'
$currentManifestPath = Join-Path $repositoryRoot 'database\manifest\usuarios-current-history.json'
$currentFingerprintPath = Join-Path $repositoryRoot 'database\manifest\usuarios-current-history.sha256'
$migrationPath = Join-Path $repositoryRoot `
    'database\migrations\V009__create_usuario_dimension_current_view.sql'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot `
    'database\validation\035_validate_usuarios_dimension_current.sql'
$exercisePath = Join-Path $repositoryRoot `
    'database\validation\036_exercise_usuarios_dimension_current_rollback.sql'
$showplanExercisePath = Join-Path $repositoryRoot `
    'database\validation\037_validate_usuarios_dimension_current_showplan.sql'
$showplanGatePath = Join-Path $repositoryRoot `
    'scripts\validation\Test-UsuariosDimensionCurrentShowplan.ps1'

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
Require-True ($manifest.manifestVersion -eq 1) `
    'A versão do manifesto da dimensão current de Usuários deve ser 1.'
Require-True ($manifest.roadmapTask -ceq 'V2-035b-USUARIOS' `
        -and $manifest.localState -ceq 'IMPLEMENTED_IN_SHADOW') `
    'O manifesto deve fechar somente a fatia Usuários de V2-035b em sombra.'
Require-True ($manifest.migration -ceq `
        'V009__create_usuario_dimension_current_view.sql') `
    'A migration da dimensão current diverge.'
Require-True ($manifest.prerequisite.roadmapTask -ceq 'V2-033' `
        -and $manifest.prerequisite.manifest -ceq 'usuarios-current-history.json' `
        -and $manifest.prerequisite.current -ceq 'core.usuario' `
        -and $manifest.prerequisite.history -ceq 'core.usuario_history') `
    'A linhagem current/history da dimensão diverge.'

$currentFingerprint = (Get-FileHash -LiteralPath $currentManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedCurrentFingerprint = ((Get-Content -LiteralPath $currentFingerprintPath -Raw).Trim() `
        -split '\s+')[0].ToLowerInvariant()
Require-True ($currentFingerprint -eq $recordedCurrentFingerprint `
        -and $manifest.prerequisite.manifestFingerprint -ceq $currentFingerprint) `
    'O pré-requisito V2-033 não está ligado ao fingerprint current do repositório.'

Require-True ($manifest.object.schema -ceq 'core' `
        -and $manifest.object.name -ceq 'v_usuario_dimension_current_v1' `
        -and $manifest.object.type -ceq 'SCHEMABOUND_VIEW') `
    'O objeto dimensional interno diverge.'
$expectedColumns = @(
    'usuario_id', 'environment_name', 'source_instance', 'tenant_scope',
    'source_key_token', 'source_key_wire_type', 'name_presence', 'usuario_name',
    'last_changed_at_utc', 'last_seen_at_utc'
)
Require-True (@(Compare-Object $expectedColumns @($manifest.object.columns) `
            -CaseSensitive -SyncWindow 0).Count -eq 0) `
    'Nome ou ordem das colunas dimensionais diverge.'
Require-True (@(Compare-Object @('usuario_id') @($manifest.grain.primary) `
            -CaseSensitive).Count -eq 0 `
        -and @(Compare-Object @(
                'environment_name', 'source_instance', 'tenant_scope', 'source_key_token'
            ) @($manifest.grain.alternate) -CaseSensitive -SyncWindow 0).Count -eq 0 `
        -and $manifest.grain.fixedEntity -ceq 'usuarios' `
        -and $manifest.grain.maximumRowsPerCurrentUser -eq 1 `
        -and $manifest.grain.historyCanMultiplyGrain -eq $false) `
    'O grão current/identidade alternativa diverge.'
Require-True ($manifest.projection.activePredicate -ceq 'active = CONVERT(BIT, 1)' `
        -and $manifest.projection.applicationMode -ceq 'SET_BASED_VIEW' `
        -and $manifest.projection.historyJoin -ceq 'PROHIBITED' `
        -and $manifest.projection.distinctAggregationWindow -ceq 'PROHIBITED' `
        -and $manifest.projection.sourceKeyAlias -ceq `
            'SOURCE_KEY_TOKEN_NOT_LEGACY_USER_ID' `
        -and $manifest.projection.nameNormalization -ceq `
            'NONE_PRESERVE_VALIDATED_VALUE' `
        -and $manifest.projection.timestamps -ceq `
            'TECHNICAL_UTC_NOT_SOURCE_FRESHNESS') `
    'A projeção set-based/current-only diverge.'
Require-True (@(Compare-Object @('ABSENT', 'NULL', 'VALUE') `
            @($manifest.projection.namePresence) -CaseSensitive).Count -eq 0 `
        -and $manifest.projection.exposedHashes -eq $false `
        -and $manifest.projection.exposedExecutionIds -eq $false `
        -and $manifest.projection.exposedActiveConstant -eq $false) `
    'Presença/minimização da dimensão diverge.'
Require-True (@($manifest.security.newPrincipals).Count -eq 0 `
        -and @($manifest.security.newRoles).Count -eq 0 `
        -and @($manifest.security.selectGrants).Count -eq 0 `
        -and @($manifest.security.newIndexes).Count -eq 0 `
        -and $manifest.security.publicSelect -eq $false `
        -and $manifest.security.runtimeSelect -eq $false `
        -and $manifest.security.explicitPublicDeny -eq $false) `
    'V009 não pode criar principal, grant, deny ou índice.'
Require-True ($manifest.consumerBoundary.approvedConsumerContract -eq $false `
        -and $manifest.consumerBoundary.pubObject -ceq 'DEFERRED_TO_V2_037' `
        -and $manifest.consumerBoundary.legacyAlias -ceq 'pub.vw_dim_usuarios' `
        -and $manifest.consumerBoundary.legacyColumnAliases -ceq `
            'DEFERRED_TO_V2_037' `
        -and $manifest.consumerBoundary.legacyTrim -ceq 'DEFERRED_TO_V2_037' `
        -and $manifest.consumerBoundary.routingGrantsCutover -ceq `
            'PROHIBITED_IN_THIS_SLICE') `
    'O limite entre dimensão interna e V2-037 diverge.'

$migration = Get-Content -LiteralPath $migrationPath -Raw
foreach ($requiredToken in @(
        'CREATE VIEW core.v_usuario_dimension_current_v1', 'WITH SCHEMABINDING',
        'usuario.source_key AS source_key_token', 'FROM core.usuario AS usuario',
        'usuario.active = CONVERT(BIT, 1)'
    )) {
    Require-True (Test-OrdinalContains $migration $requiredToken) `
        "A migration dimensional não cobre $requiredToken."
}
foreach ($prohibitedToken in @(
        'CREATE INDEX', 'GRANT ', 'DENY ', 'CREATE ROLE', 'CREATE USER',
        'pub.vw_dim_usuarios', 'usuario_history', 'LTRIM', 'RTRIM',
        'DISTINCT', 'GROUP BY', 'MERGE '
    )) {
    Require-True (-not (Test-OrdinalContains $migration $prohibitedToken)) `
        "A migration dimensional contém o token proibido $prohibitedToken."
}

$baseline = Get-Content -LiteralPath $baselinePath -Raw
Require-True (Test-OrdinalContains $baseline `
        ':r "..\migrations\V009__create_usuario_dimension_current_view.sql"') `
    'O baseline não inclui V009.'

$validator = Get-Content -LiteralPath $validatorPath -Raw
foreach ($validatorToken in @(
        'Dimensão current de Usuários V2 validada com sucesso.',
        'SCHEMABINDING', 'source_key_token', 'UQ_core_usuario_source',
        'pub.vw_dim_usuarios', 'public/v2_runtime'
    )) {
    Require-True (Test-OrdinalContains $validator $validatorToken) `
        "O validator dimensional não cobre $validatorToken."
}

$exercise = Get-Content -LiteralPath $exercisePath -Raw
foreach ($exerciseToken in @(
        'V009__create_usuario_dimension_current_view.sql',
        'INTEGER:4101', 'STRING:4101', "N'ABSENT'", "N'NULL'", "N'VALUE'",
        'core.usuario_history', 'HAS_PERMS_BY_NAME', 'v2_runtime',
        'ROLLBACK TRANSACTION',
        'Dimensão current de Usuários exercitada e revertida com sucesso.'
    )) {
    Require-True (Test-OrdinalContains $exercise $exerciseToken) `
        "O exercício dimensional não cobre $exerciseToken."
}

$showplanExercise = Get-Content -LiteralPath $showplanExercisePath -Raw
foreach ($showplanToken in @(
        'SET SHOWPLAN_XML ON', 'core.v_usuario_dimension_current_v1',
        'dimension.usuario_id = @usuario_id',
        'dimension.[source_key_token] = @source_key_token',
        'ROLLBACK TRANSACTION', 'USUARIOS_DIMENSION_CURRENT_SHOWPLAN_ROLLED_BACK'
    )) {
    Require-True (Test-OrdinalContains $showplanExercise $showplanToken) `
        "O exercício SHOWPLAN dimensional não cobre $showplanToken."
}

$showplanGate = Get-Content -LiteralPath $showplanGatePath -Raw
foreach ($showplanGateToken in @(
        '037_validate_usuarios_dimension_current_showplan.sql',
        'Integrated Security=true', 'maximumPlanCharacters',
        'PK_core_usuario', 'UQ_core_usuario_source',
        'conversionWarnings=0', 'operationalWarnings=0'
    )) {
    Require-True (Test-OrdinalContains $showplanGate $showplanGateToken) `
        "O gate SHOWPLAN dimensional não cobre $showplanGateToken."
}

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() `
        -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) `
    'O fingerprint do manifesto da dimensão current está desatualizado.'

Write-Output 'Manifesto e artefatos da dimensão current de Usuários validados com sucesso.'

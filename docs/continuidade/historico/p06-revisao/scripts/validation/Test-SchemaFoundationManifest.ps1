[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database\manifest\schema-foundation.json'
$fingerprintPath = Join-Path $repositoryRoot 'database\manifest\schema-foundation.sha256'
$migrationsDirectory = Join-Path $repositoryRoot 'database\migrations'
$baselinePath = Join-Path $repositoryRoot 'database\baseline\001_schema_foundation_baseline.sql'
$validatorPath = Join-Path $repositoryRoot 'database\validation\001_validate_schema_foundation.sql'
$exercisePath = Join-Path $repositoryRoot 'database\validation\002_exercise_schema_foundation_baseline_rollback.sql'
$pomPath = Join-Path $repositoryRoot 'pom.xml'

function Require-True {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Test-OrdinalContains {
    param(
        [string]$Text,
        [string]$Value
    )

    return $Text.IndexOf($Value, [StringComparison]::Ordinal) -ge 0
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$expectedSchemas = @('ctl', 'stg', 'core', 'ref', 'mart', 'pub', 'recon')
$actualSchemas = @($manifest.schemas)
$runtimeGrants = @($manifest.roles.v2_runtime.databaseGrants)

Require-True ($manifest.manifestVersion -eq 5) 'A versão do manifesto da fundação deve ser 5.'
Require-True ($manifest.roadmapTask -eq 'V2-019') 'O manifesto deve pertencer a V2-019.'
Require-True ($actualSchemas.Count -eq $expectedSchemas.Count) 'A quantidade de schemas diverge do manifesto canônico.'
Require-True (@(Compare-Object $expectedSchemas $actualSchemas -CaseSensitive).Count -eq 0) 'Os schemas do manifesto divergem da fundação canônica.'
Require-True ($manifest.flywayHistory.schema -eq 'ctl') 'O histórico Flyway deve ficar em ctl.'
Require-True ($manifest.flywayHistory.table -eq 'flyway_schema_history') 'A tabela de histórico Flyway diverge.'
Require-True ($manifest.flywayHistory.includedInStructuralComparison -eq $false) 'O histórico Flyway não pode entrar na comparação estrutural.'
Require-True (@($manifest.forbiddenSchemas) -contains 'shadow') 'O schema histórico shadow deve ser proibido.'
Require-True ($manifest.schemaOwner.name -ceq 'v2_schema_owner' `
        -and $manifest.schemaOwner.authentication -ceq 'WITHOUT_LOGIN' `
        -and @($manifest.schemaOwner.databaseGrants).Count -eq 1 `
        -and @($manifest.schemaOwner.databaseGrants)[0] -ceq 'CONNECT' `
        -and $manifest.schemaOwner.dboImpersonation -ceq 'DENIED' `
        -and $manifest.schemaOwner.publicImpersonation -ceq `
            'DENIED_FOR_DBO_AND_SCHEMA_OWNER' `
        -and $manifest.schemaOwner.mutableModuleOwnerContext -ceq `
            'CONFINED_TO_V2_SCHEMAS') `
    'O owner restrito dos schemas mutáveis diverge.'
Require-True (@(Compare-Object $expectedSchemas @($manifest.schemaOwner.owns) `
            -CaseSensitive).Count -eq 0) `
    'O owner restrito não cobre exatamente os sete schemas V2.'
Require-True ($manifest.grantPublisher.procedure -ceq `
        'dbo.usp_publish_v2_procedure_grant' `
        -and $manifest.grantPublisher.allowlistTable -ceq `
            'dbo.v2_procedure_grant_allowlist' `
        -and $manifest.grantPublisher.allowedTriplets -eq 41 `
        -and $manifest.grantPublisher.effectiveOwner -ceq 'dbo' `
        -and $manifest.grantPublisher.rejectsExecuteAsTargets -eq $true `
        -and @(Compare-Object `
            @('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'ALTER', 'TAKE OWNERSHIP') `
            @($manifest.grantPublisher.publicTablePermissionsDenied) `
            -CaseSensitive).Count -eq 0 `
        -and @(Compare-Object @('ALTER', 'TAKE OWNERSHIP') `
            @($manifest.grantPublisher.publicProcedurePermissionsDenied) `
            -CaseSensitive).Count -eq 0) `
    'O publicador/allowlist imutável diverge.'
Require-True (@($manifest.roles.PSObject.Properties.Name) -contains 'v2_migrator') 'A role v2_migrator está ausente no manifesto.'
Require-True (@($manifest.roles.PSObject.Properties.Name) -contains 'v2_runtime') 'A role v2_runtime está ausente no manifesto.'
Require-True (@($manifest.roles.v2_migrator.objectGrants.'dbo.usp_publish_v2_procedure_grant') `
        -ccontains 'EXECUTE') 'O migrator não possui o publicador mínimo de entrypoints.'
Require-True (@($manifest.roles.v2_migrator.objectDenies.'dbo.usp_publish_v2_procedure_grant') `
        -ccontains 'ALTER' `
        -and @($manifest.roles.v2_migrator.objectDenies.'dbo.usp_publish_v2_procedure_grant') `
            -ccontains 'TAKE OWNERSHIP' `
        -and @($manifest.roles.v2_migrator.schemaDenies.dbo) -ccontains 'ALTER' `
        -and @($manifest.roles.v2_migrator.userDenies.dbo) -ccontains 'IMPERSONATE' `
        -and @($manifest.roles.v2_migrator.userDenies.v2_schema_owner) `
            -ccontains 'IMPERSONATE') `
    'Os DENY da fronteira dbo/owner restrito divergem.'
foreach ($lifecycleRole in @(
        'v2_retention_governor',
        'v2_lifecycle_reviewer',
        'v2_lifecycle_operator',
        'v2_archive_restorer'
    )) {
    Require-True (@($manifest.roles.PSObject.Properties.Name) -contains $lifecycleRole) `
        "A role $lifecycleRole está ausente no manifesto."
    Require-True (@($manifest.roles.$lifecycleRole.databaseGrants).Count -eq 1 `
            -and @($manifest.roles.$lifecycleRole.databaseGrants)[0] -ceq 'CONNECT') `
        "A role $lifecycleRole deve começar somente com CONNECT explícito."
}
Require-True ($runtimeGrants.Count -eq 1 -and $runtimeGrants[0] -ceq 'CONNECT') 'O runtime deve começar somente com CONNECT explícito.'
Require-True (@($manifest.foundationObjects.userTables).Count -eq 1 `
        -and @($manifest.foundationObjects.userTables)[0] -ceq `
            'dbo.v2_procedure_grant_allowlist') `
    'A fundação deve conter somente a allowlist de segurança em dbo.'
Require-True (@($manifest.foundationObjects.procedures).Count -eq 1 `
        -and @($manifest.foundationObjects.procedures)[0] -ceq `
            'dbo.usp_publish_v2_procedure_grant') `
    'A fundação deve conter somente o publicador interno de entrypoints.'

$migrationNames = @(Get-ChildItem -LiteralPath $migrationsDirectory -File | ForEach-Object Name | Sort-Object)
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
    'V099__preserve_freight_terminal_omission_freshness.sql',
    'V100__bind_explicit_integral_source_scope.sql',
    'V101__accept_bound_integral_freight_capture.sql',
    'V102__prove_declared_collection_preview.sql'
)
Require-True (@(Compare-Object $expectedMigrations $migrationNames -CaseSensitive).Count -eq 0) 'O conjunto de migrations ativas não é a história Flyway limpa esperada.'

$baseline = Get-Content -LiteralPath $baselinePath -Raw
foreach ($migrationName in $expectedMigrations) {
    Require-True (Test-OrdinalContains $baseline $migrationName) "O baseline não inclui $migrationName."
}

$validator = Get-Content -LiteralPath $validatorPath -Raw
foreach ($schema in $expectedSchemas) {
    Require-True (Test-OrdinalContains $validator "N'$schema'") "O validador não cobre o schema $schema."
}
foreach ($role in @(
        'v2_migrator', 'v2_runtime', 'v2_retention_governor',
        'v2_lifecycle_reviewer', 'v2_lifecycle_operator', 'v2_archive_restorer'
    )) {
    Require-True (Test-OrdinalContains $validator "N'$role'") "O validador não cobre a role $role."
}
Require-True (Test-OrdinalContains $validator "N'shadow'") 'O validador não rejeita o schema histórico shadow.'
Require-True (Test-OrdinalContains $validator 'Fundação de schema V2 validada com sucesso.') 'A confirmação do validador está ausente.'
Require-True (Test-OrdinalContains $validator "N'dbo.usp_publish_v2_procedure_grant'" `
        -and (Test-OrdinalContains $validator "N'v2_schema_owner'")) `
    'O validador não exige o publicador interno do migrator.'
foreach ($securityToken in @(
        'ROLE_OWNERSHIP', 'permission_definition.minor_id = 0',
        'Allowlist imutável possui GRANT positivo',
        'Publicador possui GRANT positivo', 'OBJECT_DEFINITION', 'IS NULL'
    )) {
    Require-True (Test-OrdinalContains $validator $securityToken) `
        "O validador não contém o fechamento de segurança $securityToken."
}
Require-True (Test-OrdinalContains (Get-Content -LiteralPath $exercisePath -Raw) 'ROLLBACK TRANSACTION') 'O exercício de baseline deve reverter a transação.'

$fingerprint = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$recordedFingerprint = ((Get-Content -LiteralPath $fingerprintPath -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
Require-True ($fingerprint -eq $recordedFingerprint) 'O fingerprint do manifesto está desatualizado.'

$pom = Get-Content -LiteralPath $pomPath -Raw
Require-True (Test-OrdinalContains $pom '<id>shadow-migrations-windows-auth</id>') 'O perfil Flyway com Windows Authentication está ausente.'
Require-True (Test-OrdinalContains $pom '<defaultSchema>ctl</defaultSchema>') 'O histórico Flyway deve usar ctl como schema padrão.'
Require-True (Test-OrdinalContains $pom '<table>flyway_schema_history</table>') 'A tabela de histórico Flyway não está configurada.'
Require-True (-not (Test-OrdinalContains $pom 'V2_SHADOW_MIGRATOR_PASSWORD')) 'O perfil Flyway não pode aceitar senha de migrator por ambiente nesta autorização local.'

Write-Output 'Manifesto e artefatos da fundação de schema validados com sucesso.'

param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$path=Join-Path $root 'database/manifest/runtime-windows-authority-temporal.json'
$manifest=Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
$hash=(Get-Content (Join-Path $root 'database/manifest/runtime-windows-authority-temporal.sha256') -Raw).Trim().Split(' ')[0]
if ((Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash) { throw 'WINDOWS_MANIFEST_CHECKSUM' }
if ($manifest.status -cne 'APPLIED_LOCAL_RESTRICTED_ACCOUNTS_VERIFIED' -or $manifest.principalMappings -ne 2 -or
    @($manifest.operationalGrants).Count -ne 22 -or $manifest.localScopeCount -ne 8 -or
    $manifest.localScope -cne 'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2' -or $manifest.sourceCalls -ne 0 -or
    $manifest.productionDatabaseAccess -ne 0 -or $manifest.localAccountValidityDays -ne 30 -or
    $manifest.positiveRestrictedPrincipal -cne 'NEW_WINDOWS_SESSIONS_SERVICE_OPERATOR_PASS' -or
    $manifest.officialJarPositive -cne 'STATUS_AUTHORIZED_TWO_ACCOUNTS_NOT_FOUND_EXIT_10') {
    throw 'LOCAL_PROVISIONING_EVIDENCE_CONTRACT'
}
$serviceProcedures=@('ctl.usp_runtime_authorization','ctl.usp_runtime_status','ctl.usp_runtime_recovery',
 'ctl.usp_control_plane_register_source','ctl.usp_control_plane_start_cycle','ctl.usp_control_plane_start_execution',
 'ctl.usp_control_plane_heartbeat_lease','ctl.usp_control_plane_record_page','ctl.usp_control_plane_record_counts',
 'ctl.usp_control_plane_transition_execution','ctl.usp_control_plane_register_incremental_frontier',
 'stg.usp_stage_coleta_record','stg.usp_stage_frete_record','core.usp_prepare_staged_execution',
 'core.usp_prepare_frete_candidate_set','core.usp_apply_reconcile_publish_coletas','core.usp_apply_reconcile_publish_fretes',
 'recon.usp_evaluate_execution_data_quality','ctl.usp_runtime_temporal_plan','ctl.usp_runtime_temporal_gaps')
$expectedGrants=@($serviceProcedures|ForEach-Object { 'SERVICE|EXECUTE|'+$_ })+
 @('OPERATOR|EXECUTE|ctl.usp_runtime_authorization','OPERATOR|EXECUTE|ctl.usp_runtime_status')
$actualGrants=@($manifest.operationalGrants|ForEach-Object { $_.principal+'|'+$_.permission+'|'+$_.object })
if (@(Compare-Object $expectedGrants $actualGrants -CaseSensitive).Count -ne 0 -or
    @($actualGrants|Sort-Object -Unique).Count -ne 22) { throw 'ONLY_EXACT_REVIEWED_PROCEDURE_GRANTS_ALLOWED' }
if (@($manifest.migrations).Count -ne 2 -or $manifest.scopeStringProperties -ne 22 -or $manifest.authorizationReceiptColumns -ne 11 -or $manifest.capabilityMaximumSeconds -ne 60 -or $manifest.temporalMaximumWindows -ne 64) { throw 'WINDOWS_TEMPORAL_LIMITS_CHANGED' }
$baseline=Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') -Raw
foreach ($entry in $manifest.migrations) {
    $sqlPath=Join-Path $root ('database/migrations/'+$entry.file)
    if ((Get-FileHash $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256 -or -not $baseline.Contains($entry.file)) { throw 'APPLIED_WINDOWS_TEMPORAL_MIGRATION_DRIFT' }
    $sql=Get-Content -LiteralPath $sqlPath -Raw
    if ($sql -match '(?im)^\s*(GRANT|DENY|ALTER ROLE|CREATE LOGIN|CREATE USER)\s') { throw 'STRUCTURAL_MIGRATION_PROVISIONS_OPERATIONAL_IDENTITY' }
}
foreach ($artifact in @('docs/adr/0035-authority-windows-sql-consumo-duravel-e-plano-temporal.md',
 'docs/runbooks/v2-042-provisionamento-windows-sql.md','config/runtime-windows-provisioning.example.json',
 'database/validation/050_exercise_windows_authority_temporal_rollback.sql','database/validation/051_validate_windows_authority_temporal.sql',
 'database/validation/052_exercise_runtime_recovery_adversarial_read.sql',
 'database/validation/053_validate_local_runtime_provisioning.sql',
 'scripts/validation/Install-LocalRuntimeAccounts.ps1','docs/runbooks/v2-042-contas-locais-windows.md')) {
    if (-not (Test-Path -LiteralPath (Join-Path $root $artifact) -PathType Leaf)) { throw 'WINDOWS_RUNTIME_ARTIFACT_MISSING' }
}
if (Test-Path -LiteralPath (Join-Path $root 'src/main/resources/runtime-authority.properties')) { throw 'LOCAL_AUTHORITY_MUST_STAY_IN_ADMINISTERED_DISTRIBUTION' }
$authority=Get-Content (Join-Path $root 'src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeAuthorityConfiguration.java') -Raw
if (-not $authority.Contains('trustServerCertificate=false') -or -not $authority.Contains('RuntimeAuthorizationPolicy.standard().fingerprint().sha256()')) { throw 'ADMINISTRATIVE_TRUST_PIN_REQUIRED' }
Write-Output 'WINDOWS_RUNTIME_PACKAGE_PASS schema_checksums=2 exact_local_grants=22 restricted_accounts=2 official_status=VERIFIED operational_source_gate=OPEN'

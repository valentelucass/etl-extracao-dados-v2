[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'database/manifest/runtime-durable-recovery.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$recorded = (Get-Content (Join-Path $repositoryRoot 'database/manifest/runtime-durable-recovery.sha256') -Raw).Trim().Split(' ')[0]
if ((Get-FileHash $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $recorded) { throw 'Manifest fingerprint mismatch.' }
if ($manifest.roadmapTask -cne 'V2-022/P02R' -or $manifest.maximumSummaryRows -ne 1 -or @($manifest.grants).Count -ne 0) { throw 'Recovery scope mismatch.' }
$sqlPath = Join-Path $repositoryRoot ('database/migrations/' + $manifest.migration)
$sql = Get-Content -LiteralPath $sqlPath -Raw
$recordedSql = (Get-Content (Join-Path $repositoryRoot 'database/manifest/runtime-durable-recovery-migration.sha256') -Raw).Trim().Split(' ')[0]
if ((Get-FileHash $sqlPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $recordedSql) { throw 'Migration fingerprint mismatch.' }
foreach ($token in @('UPDLOCK,HOLDLOCK', 'sys.sp_getapplock', 'V2_APPLY_', 'SYSUTCDATETIME()',
    'SHA2_256', 'audit_hash', 'trg_runtime_contract_evidence_immutable', '@expected_revision',
    'PARTIAL_EXTRACTION', 'DQ_OBSOLETE', 'TERMINAL', 'NOT_FOUND', 'INCONSISTENT',
    'core.usp_apply_reconcile_publish_coletas', 'core.usp_apply_reconcile_publish_fretes',
    'recon.usp_evaluate_execution_data_quality', 'ctl.fn_runtime_recovery_identity',
    'partition_end_exclusive_utc', 'replay_of_execution_id', 'c.plan_fingerprint',
    'ROLLBACK TRANSACTION', 'COMMIT TRANSACTION')) {
    if (-not $sql.Contains($token)) { throw "Recovery protocol missing: $token" }
}
if ($sql -match '(?im)^\s*(GRANT|DENY|ALTER ROLE)\s' -or $sql.Contains('EXEC ctl.usp_control_plane_recover_stale_executions')) {
    throw 'Recovery cannot grant capabilities or sweep leases.'
}
$baseline = Get-Content (Join-Path $repositoryRoot 'database/baseline/001_schema_foundation_baseline.sql') -Raw
if (-not $baseline.Contains($manifest.migration)) { throw 'Recovery absent from baseline.' }
$exercise = Get-Content (Join-Path $repositoryRoot 'database/validation/049_exercise_runtime_durable_recovery_rollback.sql') -Raw
foreach ($token in @('ROLLBACK TRANSACTION', "N'SEAL'", "N'READ'", "N'RESUME'", 'applies_before')) {
    if (-not $exercise.Contains($token)) { throw "Recovery exercise missing: $token" }
}
$java = Get-Content (Join-Path $repositoryRoot 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportRuntimeWorkload.java') -Raw
if ($java.IndexOf('guard.complete()') -gt $java.IndexOf('session.sealTraversal(') -or $java.IndexOf('session.sealTraversal(') -gt $java.IndexOf('session.staged(')) { throw 'Seal does not follow actual gates.' }
Write-Output 'P02R: contrato estático preservado; aplicação e provas físicas locais registradas no relatório do Bloco 53, sem aceite operacional.'

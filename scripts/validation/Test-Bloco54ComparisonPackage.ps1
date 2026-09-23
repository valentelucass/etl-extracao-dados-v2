param([switch]$ExecuteReal)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$contract=Get-Content (Join-Path $root 'docs/catalogos/comparacao-bloco54/contract.json') -Raw | ConvertFrom-Json
$missing=@('sourceInstance','tenantScope','approvedPeriod','identityOracle','financialOracle','currencyAndRounding','statusLabelOwnerApproval','evidenceDestinationApproval') | Where-Object {$null -eq $contract.$_}
if($ExecuteReal){
    if($missing.Count -gt 0){throw ('REAL_COMPARISON_PACKAGE_INCOMPLETE_COUNT_'+$missing.Count)}
    throw 'REAL_COMPARISON_NOT_AUTHORIZED_IN_BLOCO54'
}
if($contract.realExecutionAuthorized -or $contract.keyExportAllowed -or $contract.maximumSummaryRows -ne 64 -or $contract.maximumCivilDays -ne 1 -or $missing.Count -ne 8){throw 'COMPARISON_SCOPE_DRIFT'}
$fixture=Get-Content (Join-Path $root 'database/validation/054_exercise_synthetic_comparison.sql') -Raw
foreach($case in @('EQUAL','MONEY_STATUS','DUPLICATE','ABSENT','BOUNDARY','INCOMPLETE')){if(-not $fixture.Contains("'$case'")){throw 'SYNTHETIC_COMPARISON_CASE_MISSING'}}
$template=Get-Content (Join-Path $root 'docs/catalogos/comparacao-bloco54/export-legado.sql.template') -Raw
if($template -match '(?im)^\s*(INSERT|UPDATE|DELETE|TRUNCATE|MERGE|CREATE|ALTER|DROP|GRANT|REVOKE)\b' -or $template -notmatch 'MAXDOP 1'){throw 'READ_ONLY_EXPORT_CONTRACT_DRIFT'}
'COMPARISON_REVIEW_PACKAGE_PASS_REAL_EXPORT_BLOCKED'

#Requires -Version 7.5
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$path=Join-Path $root 'docs/catalogos/bloco57-local/cotacoes-fonte-futura.json'
$plan=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -Depth 30
if($plan.version -ne 1 -or $plan.status -cne 'PREPARED_BLOCKED_NOT_EXECUTED' -or $plan.template -ne 6906 -or $plan.method -cne 'GET_WITH_QUERY'){throw 'COT_FUTURE_SCOPE'}
foreach($field in @('logicalHost','sourceInstance','tenantScope','authorizationReference','windowStart','windowEndExclusive','sourceCivilDateRange','temporalGuarantee','representativenessEvidence','credentialAttestation','sourceCommand')){
 if($null -ne $plan.$field){throw 'COT_FUTURE_EXTERNAL_INPUT_NOT_ADOPTED'}
}
if($plan.budgetAdopted -isnot [bool] -or $plan.budgetAdopted -or $plan.sourceExecutionEnabled -isnot [bool] -or $plan.sourceExecutionEnabled){throw 'COT_FUTURE_NO_EXECUTION_AUTHORITY'}
$expected=@{pageStart=1;per=3;infoRequests=1;maximumDataPages=3;maximumRequests=4;maximumRows=9;maximumBytesPerResponse=65536;maximumTotalBytes=262144;maximumElapsedSeconds=120;timeoutSeconds=30;minimumIntervalSeconds=2;redirects=0;retries=0;transportFallbacks=0;concurrency=1;maximumDepth=16;maximumPaths=256;maximumNodes=4096}
foreach($key in $expected.Keys){if($plan.requestPlan.$key -ne $expected[$key]){throw 'COT_FUTURE_LIMIT'}}
if($plan.requestPlan.metadataPath -cne '/api/analytics/reports/6906/info' -or $plan.requestPlan.dataPath -cne '/api/analytics/reports/6906/data' -or $plan.requestPlan.filter -cne 'quotes.requested_at'){throw 'COT_FUTURE_REQUEST_PLAN'}
if($plan.sourceAdapterState -cne 'REQUIRES_SEPARATE_AUTHORIZED_BINDING_NO_EXISTING_6906_REMOTE_RUNNER' -or $plan.receipt.currentEvidence -cne 'SYNTHETIC_LOCAL_ONLY' -or -not $plan.receipt.noSourceReceiptExists){throw 'COT_FUTURE_EVIDENCE'}
'COT_FUTURE_PACKAGE_VALID_BLOCKED_NOT_EXECUTED'

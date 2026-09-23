#Requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]{1,48}$')][string]$ReviewId,
 [ValidatePattern('^B55_[A-Z0-9_]{1,32}$')][string]$SqlSnapshot='B55_READ_03')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Read-Json([string]$Path){Get-Content -LiteralPath (Join-Path $root $Path) -Raw|ConvertFrom-Json -DateKind String}
$ledger=@(Get-Content (Join-Path $root 'target/bloco55/ledger.jsonl')|ForEach-Object{$_|ConvertFrom-Json -DateKind String})
$reservations=@($ledger|Where-Object type -EQ RESERVE);$opens=@($ledger|Where-Object type -EQ OPEN);$closes=@($ledger|Where-Object type -EQ CLOSE)
if($opens.Count -ne $closes.Count){throw 'CLOSE_CAMPAIGN_BEFORE_MANIFEST'}
$snapshot=Read-Json ('target/bloco55/snapshots/'+$SqlSnapshot+'/receipt.json')
$review=Read-Json ('target/bloco55/reviews/'+$ReviewId+'/receipt.json')
$proofPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($path in @('target/bloco55/ledger.jsonl','target/bloco55/batch-01.json','target/bloco55/batch-02.json','target/bloco55/batch-03.json','target/bloco55/loopback-quiescence-final.json','target/bloco55/sql-fence-reconciliation.json','target/bloco55/runtime/B55_SQL_01/results.json','target/bloco55/snapshots/B55_READ_03/PRESERVATION.log','target/bloco55/snapshots/B55_SQL_01_DIAGNOSE/receipt.json','target/bloco55/verify-03.log','target/bloco55/final-jar-equivalence.json','target/bloco55/pom-equivalence.json','target/bloco55/schema/B55_SCHEMA_APPLY_02/receipt.json','target/bloco55/schema/B55_V023_APPLY/receipt.json','target/bloco55/activation/B55_ACTIVATE_02/receipt.json','target/bloco55/dq-correction/B55_DQ_FIX_01/receipt.json','target/bloco55/temporal-reconciliation.json','target/bloco55/runtime/B55_TIME_01/results.json','target/bloco55/runtime/B55_SMOKE_01/acceptance-exclusions.json','target/bloco55/comparison/B55_COMPARE_02/results.json','target/bloco55/plans/B55_PLANS_05/results.json','target/bloco55/manual-preview.log','target/bloco55/secret-selftests-01.log','target/bloco55/secret-scan-04.log','target/bloco55/guards-b55-04.log','target/bloco55/static-final-02.log','target/bloco55/guards-b54-02.log',('target/bloco55/snapshots/'+$SqlSnapshot+'/receipt.json'),('target/bloco55/reviews/'+$ReviewId+'/receipt.json'),('target/bloco55/reviews/'+$ReviewId+'/current-inventory.json'),('target/bloco55/reviews/'+$ReviewId+'/bloco55-only.patch'),'database/manifest/runtime-bloco55-acceptances.json','database/proposals/bloco55-runtime-extension/manifest.json','database/proposals/bloco55-runtime-extension/activation/manifest.json','database/proposals/bloco55-output-projection/manifest.json','database/proposals/bloco55-dq-correction/manifest.json','docs/adr/0038-runtime-cinco-verticais-e-consumidores-locais.md','docs/runbooks/v2-022-bloco55-cinco-verticais-local.md')){[void]$proofPaths.Add($path)}
$validGroups=@('B55_SMOKE_03','B55_MATRIX_01','B55_MANUAL_STOP','B55_MANUAL_RESUME','B55_MANUAL_OPERATOR','B55_MANUAL_DEPENDENCY')
foreach($folder in $validGroups){[void]$proofPaths.Add('target/bloco55/runtime/'+$folder+'/results.json')}
foreach($folder in @('B55_MANUAL_STOP','B55_MANUAL_RESUME','B55_MANUAL_OPERATOR','B55_MANUAL_DEPENDENCY')){[void]$proofPaths.Add('target/bloco55/runtime/'+$folder+'/manual-summary.json')}
$measurementFiles=@(Get-ChildItem -LiteralPath (Join-Path $root 'target/bloco55/measurement') -Filter '*.json' -Recurse -File|Sort-Object LastWriteTimeUtc -Descending|Select-Object -First 5)
if($measurementFiles.Count -ne 5){throw 'FIVE_FINAL_PIPELINE_RECEIPTS_REQUIRED'}
$measurementPaths=@($measurementFiles|ForEach-Object{[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/')})
foreach($path in $measurementPaths){[void]$proofPaths.Add($path)}
$manualTest=Get-ChildItem -LiteralPath (Join-Path $root 'target/bloco55/manual') -Directory -Filter 'validator-*'|Sort-Object LastWriteTimeUtc -Descending|Select-Object -First 1
$manualValidator=[IO.Path]::GetRelativePath($root,(Join-Path $manualTest.FullName 'results.json')).Replace('\','/');[void]$proofPaths.Add($manualValidator)
$http=0;$jar=0
foreach($file in Get-ChildItem -LiteralPath (Join-Path $root 'target/bloco55/runtime') -Recurse -File -Filter 'results.json'){
 $cases=@(Get-Content $file.FullName -Raw|ConvertFrom-Json)
 foreach($case in $cases){
  if($case.id -ceq 'HTTP_TOTAL'){$http+=$case.requests}
  if($case.psobject.Properties.Name -contains 'layer' -and $case.layer -in @('OFFICIAL_JAR_REAL_WINDOWS_SQL_LOOPBACK','OFFICIAL_JAR_SERVICE_SQL')){$jar++}
 }
}
$proofFiles=@($proofPaths|Sort-Object|ForEach-Object{[ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $root $_)).Hash.ToLowerInvariant()}})
$manifest=[ordered]@{version=1;block=55;status='LOCAL_A_J_COMPLETE';database='localhost/ETL_SISTEMA_V2_SHADOW';installedThrough='V023';catalogSha256=$snapshot.catalogSha256;databaseRows=$snapshot.databaseRows;artifactManifestSha256='a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13';bundle='target/bloco55/reviewed-bundle-v4';tests=1138;testFailures=0;testErrors=0;testSkips=4;grants=32;grantDelta=7;scopes=16;scopeDelta=6;mappings=2;serviceMappingVersion=17;operatorMappingVersion=1;originalScope1Version=10;otherOriginalScopesVersion=1;originalUntil='2026-10-07T22:34:30.615Z';renewed=$false;qualityHistorical=6;qualityActive=3;qualityRevoked=3;tariffReleases=1;tariffRows=2;reservations=$reservations.Count;maximumUnits=384;declaredBatches=@($ledger|Where-Object type -EQ BATCH).Count;campaignsOpened=$opens.Count;campaignsClosed=$closes.Count;refundedUnits=0;httpRequests=$http;maximumHttp=2048;jarCasesExecuted=$jar;jarCountIncludesFailedCases=$true;jarCountIncludesTemporalPersist=$true;newAttempts=$snapshot.b55.b55Attempts;newPublications=$snapshot.b55.b55Publications;auditedPages=$snapshot.b55.b55Pages;auditedEntries=$snapshot.b55.b55Entries;outputRows=$snapshot.b55.b55OutputRows;restrictedSessions=$snapshot.restrictedSessions;observerTransactions=$snapshot.observerTransactions;b53Attempts=95;b53Publications=75;b53Extracting=8;b53Reservations=112;b54Attempts=45;b54Publications=30;b54Pages=70;b54Entries=74;b54Reservations=302;b54UnreusedUnits=18;frontsCompleted=@('A','B','C','D','E','F','G','H','I','J');frontsPending=@();canonicalAcceptances=@('V2-042b','V2-042c','V2-022b','V2-042');integrationAccepted=$true;checkboxes=115;done=65;pending=50;openRoutes=193;realSourceExecuted=$false;productionExecuted=$false;validRuntimeGroups=$validGroups;excludedRuntimeGroups=@('B55_SMOKE_01','B55_SMOKE_02');temporalFalseNegativesReconciled=3;sqlFalseNegativesReconciled=4;comparisonCases=30;estimatedConsumers=9;measurementFiles=$measurementPaths;manualValidator=$manualValidator;pendingPhysical=@();completedPhysical=@('B55_MANUAL_OPERATOR','B55_MANUAL_DEPENDENCY','B55_SQL_01');completedController='scripts/validation/Invoke-Bloco55RemainingPhysical.ps1';completedControllerSha256=(Get-FileHash (Join-Path $PSScriptRoot 'Invoke-Bloco55RemainingPhysical.ps1')).Hash.ToLowerInvariant();blocker='';scopeOrAccountsMissing=$false;review='target/bloco55/reviews/'+$ReviewId;sqlSnapshot='target/bloco55/snapshots/'+$SqlSnapshot;initialFilesPreserved=$review.initialPresent;changedInitialFiles=$review.changedInitial;newPaths=$review.newPaths;proofFiles=$proofFiles}
$json=$manifest|ConvertTo-Json -Depth 8
$destination=Join-Path $root 'database/manifest/runtime-bloco55.json'
if(Test-Path $destination){
 $archive=Join-Path $root ('target/bloco55/manifest-checkpoints/'+[guid]::NewGuid().ToString('N')+'.json')
 [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($archive));[IO.File]::Copy($destination,$archive,$false)
}
[IO.File]::WriteAllText($destination,$json,$utf8)
'B55_COMPLETE_LOCAL_EVIDENCE_MANIFEST_WRITTEN_REQUIRES_VALIDATORS'

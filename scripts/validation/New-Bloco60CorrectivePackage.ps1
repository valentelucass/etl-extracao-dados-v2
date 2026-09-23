#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$folder=Join-Path $root 'database/proposals/bloco60-correcao';$output=Join-Path $folder 'package.json'
if(Test-Path $output){throw 'B60R_PACKAGE_EXISTS_PRESERVE'}
$previous=Get-Content (Join-Path $root 'database/proposals/bloco60-local/package.json') -Raw|ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
$package=$previous.Clone()
$package.status='PREPARED_CORRECTIVE_APPROVAL_REQUIRED';$package.validFrom='2026-09-10T00:00:00Z'
$package.predecessorPackage='a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e'
$package.predecessorReceipt='f2b35d102e8517b4fda6be67d2d84b8b1945077670470f77ef1d8f31307b605b'
$package.expectedCatalog='cfd68974c4fc7667cde1bf9376250d41d58fccf026f9aae5a03268062ecd8e34';$package.expectedRows=$null
$package.baselineMethod='EXISTING_V024_COMPENSATED_SERVICE_19_NO_DDL'
$package.versions=@{serviceBefore=19;serviceActive=20;serviceAfter=21;usersScopeBefore=2;usersScopeActive=3;usersScopeAfter=4}
$package.minimumPermissionDelta=@('Exactly the same two procedure GRANT EXECUTE permissions as the predecessor','SERVICE mapping 19 -> 20: replay=1, force_run=1; original validity unchanged; principal-wide flags include the existing five BACKFILL verticals','Exactly four existing Users scopes 2 -> 3, revoked=0; same identities/environment/source/tenant/modes/policy','Append Users DQ backfill-v2/replay-v2 and eight strict checks; old v1 policies remain revoked','No migration, database, account, role, membership, source-kind change or new protocol binding')
$package.compensation=@('SERVICE mapping 20 -> 21: replay=0, force_run=0','Exactly four Users scopes 3 -> 4, revoked=1','Revoke two new v2 DQ policies and the exact two temporary procedure grants','Preserve V024, original validity, protocol bindings, synthetic data, history, decisions, publications, seals and receipts','Stop owned handles and loopback; verify exact compensated profile and historical row-hash multiset','Only one SERVICE mapping and exactly four Users scope rows are excluded from multiset; their complete profile is checked separately')
$package.variants=Get-Content (Join-Path $root 'target/b60-correcao-20260910/bundle-v2/variants.json') -Raw|ConvertFrom-Json -AsHashtable
$paths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($directory in @($folder,(Join-Path $root 'target/b60-correcao-20260910/bundle-v2'),(Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'))){foreach($file in Get-ChildItem $directory -Recurse -File){[void]$paths.Add([IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/'))}}
foreach($file in Get-ChildItem $PSScriptRoot -File|Where-Object Name -like '*Bloco60Corrective*'){[void]$paths.Add('scripts/validation/'+$file.Name)}
foreach($path in @('scripts/validation/Bloco60Budget.psm1','scripts/validation/Bloco60Assertions.psm1','scripts/validation/Bloco60Process.cs','scripts/validation/Bloco60AdminProcess.cs','database/validation/057_exercise_bloco60_adversarial_read.sql','database/proposals/bloco60-local/package.json','target/execucao-b60-aprovada-20260909-2334/final/receipt.json','target/b60-correcao-20260910/verify-01/exit.json','target/b60-correcao-20260910/semantic-green-02/result.json','src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactVerifier.java','src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredArtifactManifestTest.java','src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredBundleOfflineProbe.java')){[void]$paths.Add($path)}
$package.files=@($paths|Sort-Object|ForEach-Object {@{path=$_;sha256=(Get-FileHash (Join-Path $root $_)).Hash.ToLowerInvariant()}})
$package|ConvertTo-Json -Depth 30|Set-Content $output -Encoding utf8NoBOM
@{sha256=(Get-FileHash $output).Hash.ToLowerInvariant();files=$package.files.Count;cases=$package.cases;sqlExecuted=$false;approvalRecorded=$false}|ConvertTo-Json

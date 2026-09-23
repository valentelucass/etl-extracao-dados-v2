#Requires -Version 7.0
param([Parameter(Mandatory)][ValidatePattern('^B55_[A-Z0-9_]{1,32}$')][string]$ProofId,[switch]$AssertSqlRefusals)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
$directory=Join-Path $root ('target/bloco55/snapshots/'+$ProofId)
if(Test-Path $directory){throw 'EXISTING_READONLY_SNAPSHOT_PRESERVE'}
[void][IO.Directory]::CreateDirectory($directory)
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Query([string]$Name,[string]$Text){
 Add-Bloco55Reservation ($ProofId+'_'+$Name) 'SQL_READONLY_OWN_AGGREGATES_AND_CATALOG'|Out-Null
 $path=Join-Path $directory ($Name+'.sql');[IO.File]::WriteAllText($path,$Text,$utf8)
 & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 30 -b -y 0 -w 65535 -i $path *> (Join-Path $directory ($Name+'.log'))
 if($LASTEXITCODE -ne 0){throw ('READONLY_SNAPSHOT_FAILED_'+$Name)}
 $output=[IO.File]::ReadAllText((Join-Path $directory ($Name+'.log')),$utf8)
 if($utf8.GetByteCount($output) -gt 16KB){throw 'READONLY_SUMMARY_BOUND'}
 $output
}
$catalog=Query 'CATALOG' ([IO.File]::ReadAllText((Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/schema-after.sql')))
if($catalog -cnotmatch '(?m)^SCHEMA_SHA256=ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae\s*$'){throw 'EXACT_V023_CATALOG_REQUIRED'}
$profile=Query 'PROFILE' ([IO.File]::ReadAllText((Join-Path $root 'database/proposals/bloco55-runtime-extension/activation/verify-profile.sql')))
if(-not $profile.Contains('B55_EXACT_32_GRANTS_16_SCOPES_ORIGINAL_VALIDITY_PASS')){throw 'EXACT_PROFILE_REQUIRED'}
$ids=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$requestFiles=@(Get-ChildItem -LiteralPath (Join-Path $root 'target/bloco55/runtime') -Filter '*.request.json' -File -Recurse)
if($requestFiles.Count -gt 512){throw 'OWN_REQUEST_INVENTORY_BOUND'}
foreach($file in $requestFiles){
 if($file.Length -gt 16KB){throw 'REQUEST_BYTE_BOUND'}
 $request=$utf8.GetString([IO.File]::ReadAllBytes($file.FullName))|ConvertFrom-Json -DateKind String
 [void]$ids.Add(([guid]$request.executionId).ToString())
}
if($ids.Count -eq 0 -or $ids.Count -gt 256){throw 'OWN_EXECUTION_COUNT_BOUND'}
$values=(@($ids|Sort-Object|ForEach-Object{"('$_')"}) -join ',')
$old=[IO.File]::ReadAllText((Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_MANUAL_01/preservation-after.sql'))
$insertion="DECLARE @b55 TABLE(execution_id UNIQUEIDENTIFIER PRIMARY KEY); INSERT @b55 VALUES $values;`n"
$old=$old.Replace('DECLARE @priorAttempts BIGINT=',($insertion+'IF EXISTS(SELECT 1 FROM @own o JOIN @b55 n ON n.execution_id=o.execution_id) THROW 52955,N''OWN_OCCURRENCES_MUST_BE_DISJOINT'',1;'+"`nDECLARE @priorAttempts BIGINT="))
$old=$old.Replace('WHERE NOT EXISTS(SELECT 1 FROM @own o WHERE o.execution_id=a.execution_id)','WHERE NOT EXISTS(SELECT 1 FROM @b55 n WHERE n.execution_id=a.execution_id) AND NOT EXISTS(SELECT 1 FROM @own o WHERE o.execution_id=a.execution_id)')
$old=$old.Replace('WHERE NOT EXISTS(SELECT 1 FROM @own o WHERE o.execution_id=p.execution_id)','WHERE NOT EXISTS(SELECT 1 FROM @b55 n WHERE n.execution_id=p.execution_id) AND NOT EXISTS(SELECT 1 FROM @own o WHERE o.execution_id=p.execution_id)')
$old=$old.Replace("WHERE current_state=N'EXTRACTING' AND NOT EXISTS", "WHERE current_state=N'EXTRACTING' AND NOT EXISTS(SELECT 1 FROM @b55 n WHERE n.execution_id=a.execution_id) AND NOT EXISTS")
$old+="`nIF @ownAttempts<>45 OR @ownPublications<>30 OR @pages<>70 OR @entries<>74 THROW 52955,N'B54_EXACT_PRESERVATION',1;"
$old+=@'

DECLARE @attempts BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_attempt a JOIN @b55 n ON n.execution_id=a.execution_id);
DECLARE @publications BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event a JOIN @b55 n ON n.execution_id=a.execution_id);
DECLARE @newPages BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit a JOIN @b55 n ON n.execution_id=a.execution_id);
DECLARE @newEntries BIGINT=(SELECT COALESCE(SUM(a.physical_rows),0) FROM ctl.execution_page_audit a JOIN @b55 n ON n.execution_id=a.execution_id);
DECLARE @outputRows BIGINT=(SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output a JOIN @b55 n ON n.execution_id=a.execution_id);
IF @newPages>1536 OR @newEntries>6144 OR @outputRows>196608 THROW 52955,N'B55_VOLUME_LIMIT',1;
SELECT @attempts b55Attempts,@publications b55Publications,@newPages b55Pages,@newEntries b55Entries,@outputRows b55OutputRows,@@TRANCOUNT observerTransactions FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
SELECT a.current_state,COUNT_BIG(*) occurrences FROM ctl.execution_attempt a JOIN @b55 n ON n.execution_id=a.execution_id GROUP BY a.current_state FOR JSON PATH;
SELECT COUNT_BIG(*) restrictedSessions FROM sys.dm_exec_sessions WHERE is_user_process=1 AND login_name IN(CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_view') FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
SELECT reference_release_id,family_code,scope_code FROM ref.reference_release WHERE reference_release_id IN(1,2,3) FOR JSON PATH;
'@
# PowerShell single-quoted strings preserve backslashes; T-SQL needs one separator.
$old=$old.Replace("N'\\etl_v2_", "N'\etl_v2_")
if($AssertSqlRefusals){
 $originalRows=@();$refusalRows=@()
 foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
  $request=Get-Content (Join-Path $root ('target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_'+$template+'_RUN.request.json')) -Raw|ConvertFrom-Json -DateKind String
  $originalRows+="('$(([guid]$request.executionId).ToString())','$(([guid]$request.invocationId).ToString())')"
 }
 foreach($suffix in @('MANIFESTOS_OCCURRENCE_MATERIAL_CHANGED','COTACOES_OCCURRENCE_MATERIAL_CHANGED','LOCALIZACAO_CARGAS_OCCURRENCE_MATERIAL_CHANGED','KNOWN_TEMPLATE_SWAPPED')){
  $request=Get-Content (Join-Path $root ('target/bloco55/runtime/B55_SQL_01/B55_SQL_01_'+$suffix+'.request.json')) -Raw|ConvertFrom-Json -DateKind String
  $refusalRows+="('$(([guid]$request.invocationId).ToString())')"
 }
 $old+="`nDECLARE @original TABLE(execution_id UNIQUEIDENTIFIER PRIMARY KEY,invocation_id UNIQUEIDENTIFIER); INSERT @original VALUES "+($originalRows -join ',')+";`nDECLARE @refused TABLE(invocation_id UNIQUEIDENTIFIER PRIMARY KEY); INSERT @refused VALUES "+($refusalRows -join ',')+';'
 $old+=@'

DECLARE @matched BIGINT=(SELECT COUNT_BIG(*) FROM @original o
 JOIN ctl.execution_attempt a ON a.execution_id=o.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 JOIN ctl.execution_cycle c ON c.cycle_id=a.cycle_id
 JOIN ctl.runtime_authorization_decision d ON d.invocation_id=o.invocation_id AND d.execution_id=o.execution_id
 WHERE d.decision='ALLOW' AND a.current_state=N'PUBLISHED'
 AND LOWER(CONVERT(NVARCHAR(36),a.cycle_id))=JSON_VALUE(d.scope_material,'$.cycle') COLLATE Latin1_General_100_BIN2
 AND c.plan_version=JSON_VALUE(d.scope_material,'$.planVersion') COLLATE Latin1_General_100_BIN2
 AND c.plan_fingerprint=JSON_VALUE(d.scope_material,'$.planHash') COLLATE Latin1_General_100_BIN2
 AND p.environment_name=JSON_VALUE(d.scope_material,'$.environment') COLLATE Latin1_General_100_BIN2
 AND p.source_instance=JSON_VALUE(d.scope_material,'$.source') COLLATE Latin1_General_100_BIN2
 AND p.tenant_scope=JSON_VALUE(d.scope_material,'$.tenant') COLLATE Latin1_General_100_BIN2
 AND p.entity_name=JSON_VALUE(d.scope_material,'$.entity') COLLATE Latin1_General_100_BIN2
 AND p.execution_mode=JSON_VALUE(d.scope_material,'$.mode') COLLATE Latin1_General_100_BIN2
 AND p.partition_start_utc=CONVERT(DATETIME2(3),JSON_VALUE(d.scope_material,'$.start'),127)
 AND p.partition_end_exclusive_utc=CONVERT(DATETIME2(3),JSON_VALUE(d.scope_material,'$.endExclusive'),127)
 AND a.window_strategy=JSON_VALUE(d.scope_material,'$.strategy') COLLATE Latin1_General_100_BIN2
 AND a.idempotency_key=JSON_VALUE(d.scope_material,'$.idempotency') COLLATE Latin1_General_100_BIN2
 AND a.contract_version=JSON_VALUE(d.scope_material,'$.contractVersion') COLLATE Latin1_General_100_BIN2
 AND a.contract_fingerprint=JSON_VALUE(d.scope_material,'$.contractHash') COLLATE Latin1_General_100_BIN2
 AND a.configuration_version=JSON_VALUE(d.scope_material,'$.configurationVersion') COLLATE Latin1_General_100_BIN2
 AND a.configuration_fingerprint=JSON_VALUE(d.scope_material,'$.configurationHash') COLLATE Latin1_General_100_BIN2);
DECLARE @originalPublications BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event p JOIN @original o ON o.execution_id=p.execution_id);
DECLARE @originalPages BIGINT=(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit p JOIN @original o ON o.execution_id=p.execution_id);
DECLARE @originalOutputs BIGINT=(SELECT COUNT_BIG(*) FROM recon.runtime_vertical_output p JOIN @original o ON o.execution_id=p.execution_id);
DECLARE @refusedConsumed BIGINT=(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption c JOIN @refused r ON r.invocation_id=c.invocation_id);
DECLARE @tariffBindings BIGINT=(SELECT COUNT_BIG(*) FROM ctl.runtime_cotacao_reference b JOIN @original o ON o.execution_id=b.execution_id WHERE b.reference_release_id=3);
IF @matched<>3 OR @originalPublications<>3 OR @originalPages<>9 OR @originalOutputs<>9 OR @refusedConsumed<>4 OR @tariffBindings<>1
 THROW 52955,N'ORIGINAL_BINDINGS_AFTER_FOUR_REFUSALS_NOT_PROVEN',1;
SELECT @matched originalBindings,@originalPublications originalPublications,@originalPages originalPages,@originalOutputs originalOutputs,@refusedConsumed refusedConsumed,@tariffBindings originalTariffBindings,0 materialMutations FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
'@
}
$preservation=Query 'PRESERVATION' $old
if(-not $preservation.Contains('"priorAttempts":95,"priorPublications":75,"retainedExtracting":8,"b54Attempts":45,"b54Publications":30,"b54Pages":70,"b54Entries":74')){throw 'B53_B54_EXACT_AGGREGATES_REQUIRED'}
$summaryLine=@($preservation -split "`n"|Where-Object {$_.StartsWith('{"b55Attempts"')})
if($summaryLine.Count -ne 1){throw 'OWN_AGGREGATE_RECEIPT_REQUIRED'}
$summary=$summaryLine[0]|ConvertFrom-Json
$receipt=[ordered]@{proofId=$ProofId;utc=[DateTimeOffset]::UtcNow.ToString('o');layer='READONLY_SQL_WINDOWS_OWN_AGGREGATES';catalogSha256='ceb70df963b995b5b8f43231a856a8c3c3a67af57458eca6948bbd989bf636ae';databaseRows=[int64]([regex]::Match($catalog,'DATA_ROWS=(\d+)').Groups[1].Value);grants=32;scopes=16;originalUntil='2026-10-07T22:34:30.615Z';b53Attempts=95;b53Publications=75;b53Extracting=8;b54Attempts=45;b54Publications=30;b54Pages=70;b54Entries=74;b55=$summary;restrictedSessions=0;observerTransactions=0;passed=$true}
[IO.File]::WriteAllText((Join-Path $directory 'receipt.json'),($receipt|ConvertTo-Json -Depth 5),$utf8)
[pscustomobject]$receipt

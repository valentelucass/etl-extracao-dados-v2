$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$directory=Join-Path $root 'database/proposals/bloco55-runtime-extension/activation'
if(Test-Path (Join-Path $directory 'applied.json')){throw 'ACTIVATED_PACKAGE_IMMUTABLE'}
[void][IO.Directory]::CreateDirectory($directory)
$encoding=[Text.UTF8Encoding]::new($false)
function Write-PackageFile([string]$Name,[string]$Body){[IO.File]::WriteAllText((Join-Path $directory $Name),$Body,$encoding)}
function Hash([string]$Text){[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($Text))).ToLowerInvariant()}
$old=Get-Content (Join-Path $root 'database/proposals/bloco54-temporal-continuation/verify.sql') -Raw
$start=$old.IndexOf('-- Administrative read-only validation')
$end=$old.IndexOf("PRINT N'B54_EXACT_25_GRANTS_8_ORIGINAL_2_REVOKED_REPLAY_PASS';",$start)
if($start -lt 0 -or $end -lt 0){throw 'OLD_PROFILE_ANCHOR_REQUIRED'}
$before=$old.Substring($start,$end-$start)+"PRINT N'B54_EXACT_PROFILE_PRESERVED';`n"
Write-PackageFile 'verify-before.sql' $before
$newProcedures=@('stg.usp_stage_manifesto_observation','core.usp_prepare_manifesto_candidate_set','core.usp_apply_reconcile_publish_manifestos',
 'stg.usp_stage_cotacao_record','core.usp_apply_reconcile_publish_cotacoes','stg.usp_stage_localizacao_carga_record','core.usp_apply_reconcile_publish_localizacao_cargas')
$grants=($newProcedures|ForEach-Object{" (N'$_')"}) -join ",`n"
$after=$before.Replace('IF EXISTS(SELECT principal_id,object_id FROM @expected EXCEPT',"INSERT @expected SELECT USER_ID(@service),OBJECT_ID(p.name) FROM (VALUES`n$grants)p(name);`nIF EXISTS(SELECT principal_id,object_id FROM @expected EXCEPT")
$after=$after.Replace('EXACT_25_PROCEDURE_GRANTS_REQUIRED','EXACT_32_PROCEDURE_GRANTS_REQUIRED').Replace('ctl.runtime_identity_scope)<>10','ctl.runtime_identity_scope)<>16')
$after=$after.Replace("mode IN(N'BACKFILL',N'INCREMENTAL'))<>8","mode IN(N'BACKFILL',N'INCREMENTAL'))<>14")
$after=$after.Replace("workload NOT IN(N'coletas',N'fretes')","workload NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas')")
$after+=@'
IF (SELECT COUNT_BIG(*) FROM ctl.runtime_identity_scope WHERE workload IN(N'manifestos',N'cotacoes',N'localizacao_cargas') AND mode=N'BACKFILL' AND revoked=0 AND scope_version=1)<>6
 THROW 52852,N'EXACT_SIX_NEW_BACKFILL_SCOPES',1;
IF NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE principal_kind='SERVICE' AND mapping_version=17)
 OR NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_mapping WHERE principal_kind='OPERATOR' AND mapping_version=1)
 OR NOT EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE scope_id=1 AND scope_version=10)
 OR EXISTS(SELECT 1 FROM ctl.runtime_identity_scope WHERE scope_id BETWEEN 2 AND 8 AND scope_version<>1)
 THROW 52852,N'ORIGINAL_VERSIONS_MUST_REMAIN',1;
PRINT N'B55_EXACT_32_GRANTS_16_SCOPES_ORIGINAL_VALIDITY_PASS';
'@
Write-PackageFile 'verify-profile.sql' $after.Replace("PRINT N'B54_EXACT_PROFILE_PRESERVED';",'')
$activate=@'
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR OBJECT_ID(N'ctl.runtime_cotacao_reference') IS NULL THROW 52852,N'V022_EXACT_TARGET_REQUIRED',1;
:r "verify-before.sql"
GO
BEGIN TRANSACTION;
DECLARE @service SYSNAME=CONVERT(NVARCHAR(128),SERVERPROPERTY('MachineName'))+N'\etl_v2_exec',
 @policy CHAR(64)='c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d',@sql NVARCHAR(MAX)=N'';
'@
$activate+="`nSELECT @sql=@sql+N'GRANT EXECUTE ON OBJECT::'+p.name+N' TO '+QUOTENAME(@service)+N';' FROM (VALUES`n$grants)p(name);`nEXEC sys.sp_executesql @sql;`n"
$activate+=@'
INSERT ctl.runtime_identity_scope(original_sid,environment_name,source_instance,tenant_scope,workload,mode,scope_version,policy_fingerprint,revoked)
SELECT m.original_sid,N'LOCAL_SHADOW',N'LOCAL_V2',N'LOCAL_V2',w.workload,N'BACKFILL',1,@policy,0
FROM ctl.runtime_identity_mapping m CROSS JOIN (VALUES(N'manifestos'),(N'cotacoes'),(N'localizacao_cargas')) w(workload);
IF @@ROWCOUNT<>6 THROW 52852,N'SCOPE_DELTA_REQUIRED',1;
GO
:r "seed-quality.sql"
GO
:r "seed-tariff.sql"
GO
:r "verify-profile.sql"
GO
:r "verify-seeds.sql"
GO
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52852,N'EXACT_TRANSACTION_REQUIRED',1;
COMMIT TRANSACTION;
PRINT N'B55_EXACT_ACTIVATION_COMMITTED';
'@
Write-PackageFile 'apply.sql' $activate
$references=[Collections.Generic.List[object]]::new();$seed=''
$owner='laboratory-owner';$retention='bloco55-preserve-v1';$effective='2026-09-08T00:00:00.123'
foreach($pair in @(@('MANIFESTOS','manifestos'),@('COTACOES','cotacoes'),@('LOCALIZACAO_CARGAS','localizacao_cargas'))){
 $version='bloco55-'+$pair[1]+'-backfill-v1'
 $scope=Hash ('dq-scope-v1|24:LOCAL_SHADOW|16:LOCAL_V2|16:LOCAL_V2|'+(2*$pair[1].Length)+':'+$pair[1]+'|16:BACKFILL')
 $material='dq-policy-v1|'+(2*$version.Length)+':'+$version+'|'+$scope+'|4|3600|30:laboratory-owner|30:laboratory-owner|36:bloco55-preserve-v1|30:laboratory-owner|'+$effective+'|1:COUNT_EQUATION:0:0:30:laboratory-owner|2:PAGE_TERMINALITY:0:0:30:laboratory-owner|3:PROMOTION_RECONCILIATION:0:0:30:laboratory-owner|4:QUARANTINE_SLA:0:0:30:laboratory-owner'
 $fingerprint=Hash $material
 $references.Add([ordered]@{template=$pair[0];mode='BACKFILL';version=$version;fingerprint=$fingerprint;scope=$scope;effectiveFrom=$effective})
 $seed+="`nINSERT ctl.data_quality_policy VALUES(N'$version','$fingerprint','$scope',4,3600,N'$owner',N'$owner',N'$retention',N'$owner',N'RATIFIED','$effective',SYSUTCDATETIME());`n"
 $seed+="INSERT ctl.data_quality_check_policy SELECT N'$version','$fingerprint',n,c,0,0,N'$owner' FROM (VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA'))p(n,c);`n"
}
Write-PackageFile 'quality-references.json' ($references|ConvertTo-Json -Depth 5)
Write-PackageFile 'seed-quality.sql' $seed
$tariff=[ordered]@{provenance='OWNER_ADOPTED_B55_SYNTHETIC_ONLY';scope='LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes';version='bloco55-tariff-v1';
 validFrom='2018-01-01';validToExclusive='2040-01-01';rows=@([ordered]@{origin='SP';destination='RJ';minimum='5.0000';currency='BRL';unit='PER_SHIPMENT';rounding='HALF_UP'},[ordered]@{origin='RJ';destination='SP';minimum='7.0000';currency='BRL';unit='PER_SHIPMENT';rounding='HALF_UP'})}
Write-PackageFile 'tariff.json' ($tariff|ConvertTo-Json -Depth 5)
$tariffHash=(Get-FileHash (Join-Path $directory 'tariff.json')).Hash.ToLowerInvariant()
$tariffBytes=(Get-Item (Join-Path $directory 'tariff.json')).Length
$tariffSql=@"
DECLARE @release BIGINT;
IF EXISTS(SELECT 1 FROM ref.reference_release WHERE scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes' AND release_version=N'bloco55-tariff-v1') THROW 52852,N'TARIFF_ALREADY_EXISTS_RECONCILE',1;
INSERT ref.reference_release(family_code,scope_code,release_version,schema_version,source_kind,source_artifact_ref,source_fingerprint,source_row_count,valid_from,valid_to_exclusive,reason_code,author_role)
VALUES(N'QUOTE_TARIFF',N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes',N'bloco55-tariff-v1',1,N'DETERMINISTIC_LOCAL',N'B55_SYNTHETIC_TARIFF','$tariffHash',2,'2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY',N'LOCAL_LABORATORY_OWNER');
SET @release=SCOPE_IDENTITY();
INSERT ref.tarifa_rota_uf VALUES(@release,N'QUOTE_TARIFF','SP','RJ',N'PRICED',5,'BRL',N'PER_SHIPMENT',N'HALF_UP','2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY'),
 (@release,N'QUOTE_TARIFF','RJ','SP',N'PRICED',7,'BRL',N'PER_SHIPMENT',N'HALF_UP','2018-01-01','2040-01-01',N'SYNTHETIC_LABORATORY_ONLY');
INSERT ref.reference_import_receipt(reference_release_id,contract_version,content_fingerprint,imported_row_count,imported_byte_count,importer_role)
VALUES(@release,N'governed-references-v1','$tariffHash',2,$tariffBytes,N'SYNTHETIC_LABORATORY_IMPORTER');
INSERT ref.reference_release_ratification(reference_release_id,activation_scope,approver_role,approval_evidence_ref,approval_fingerprint)
VALUES(@release,N'SHADOW',N'SYNTHETIC_LABORATORY_APPROVER',N'B55_OWNER_ADOPTED_SECTION_3','$tariffHash');
"@
Write-PackageFile 'seed-tariff.sql' $tariffSql
$verifySeed=@"
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_policy WHERE policy_version IN(N'bloco55-manifestos-backfill-v1',N'bloco55-cotacoes-backfill-v1',N'bloco55-localizacao_cargas-backfill-v1') AND expected_checks=4 AND policy_state=N'RATIFIED')<>3 THROW 52852,N'EXACT_THREE_DQ_POLICIES',1;
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy WHERE policy_version IN(N'bloco55-manifestos-backfill-v1',N'bloco55-cotacoes-backfill-v1',N'bloco55-localizacao_cargas-backfill-v1') AND maximum_failed_rows=0 AND maximum_failure_basis_points=0)<>12 THROW 52852,N'EXACT_TWELVE_STRICT_CHECKS',1;
DECLARE @release BIGINT=(SELECT reference_release_id FROM ref.reference_release WHERE scope_code=N'LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes' AND release_version=N'bloco55-tariff-v1' AND source_fingerprint='$tariffHash');
IF @release IS NULL OR (SELECT COUNT_BIG(*) FROM ref.tarifa_rota_uf WHERE reference_release_id=@release)<>2 OR ctl.fn_runtime_tariff_valid(NEWID(),@release)<>1 THROW 52852,N'EXACT_SCOPED_TARIFF_REQUIRED',1;
SELECT N'TARIFF_RELEASE_ID='+CONVERT(NVARCHAR(20),@release);
PRINT N'B55_EXACT_SYNTHETIC_SEEDS_PASS';
"@
Write-PackageFile 'verify-seeds.sql' $verifySeed
Write-PackageFile 'recovery.sql' ':r "..\recovery-grants.sql"'
$files=@(Get-ChildItem $directory -File|Where-Object Name -ne 'manifest.json'|Sort-Object Name|ForEach-Object{[ordered]@{path=$_.Name;sha256=(Get-FileHash $_.FullName).Hash.ToLowerInvariant()}})
Write-PackageFile 'manifest.json' ([ordered]@{database='localhost/ETL_SISTEMA_V2_SHADOW';previousGrants=25;grants=32;grantDelta=7;previousScopes=10;scopes=16;scopeDelta=6;dqPolicies=3;tariffReleases=1;tariffRows=2;validUntil='2026-10-07T22:34:30.615Z';files=$files}|ConvertTo-Json -Depth 5)
(Get-FileHash (Join-Path $directory 'manifest.json')).Hash.ToLowerInvariant()

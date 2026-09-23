# Generates only the uninstalled V022 draft from frozen historical SQL modules and reviewed additions.
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$proposal=Join-Path $root 'database/proposals/bloco55-runtime-extension'
$encoding=[Text.UTF8Encoding]::new($false)
function Module([int]$Version,[string]$Name){
 $file=@(Get-ChildItem (Join-Path $root 'database/migrations') -Filter ('V'+$Version.ToString('000')+'__*.sql'))
 if($file.Count -ne 1){throw 'EXACT_HISTORICAL_MIGRATION_REQUIRED'}
 $text=[IO.File]::ReadAllText($file[0].FullName).Replace("`r`n","`n")
 $pattern='(?ms)^CREATE(?: OR ALTER)? (?:PROCEDURE|FUNCTION) '+[regex]::Escape($Name)+'\b.*?^GO[ \t]*(?:\n|$)'
 $match=[regex]::Match($text,$pattern)
 if(-not $match.Success){throw ('SQL_MODULE_NOT_FOUND_'+$Name)}
 return $match.Value
}
function Replace-One([string]$Text,[string]$Old,[string]$New){
 if(([regex]::Matches($Text,[regex]::Escape($Old))).Count -ne 1){throw ('SQL_REPLACEMENT_NOT_UNIQUE_'+$Old.Substring(0,[Math]::Min(45,$Old.Length)))}
 return $Text.Replace($Old,$New)
}
function Fence([string]$Text,[string]$Entity,[bool]$Stage){
 $guard=@"
AS
BEGIN
    DECLARE @b55_actual NVARCHAR(MAX)=(SELECT LOWER(CONVERT(NVARCHAR(36),@execution_id)) AS [execution],
       N'$Entity' AS [entity],N'$Entity' AS [workload],N'BACKFILL' AS [mode] FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
    IF ctl.fn_runtime_consumed_scope(@b55_actual,1,NULL)<>1 THROW 52840,N'B55_VERTICAL_CONSUMER_FENCE',1;
"@
 if($Stage){$guard+=@'

    IF NOT EXISTS(SELECT 1 FROM ctl.execution_attempt a
      JOIN ctl.execution_partition p ON p.partition_id=a.partition_id AND p.current_execution_id=a.execution_id
      JOIN ctl.execution_lease l ON l.execution_id=a.execution_id AND l.partition_id=p.partition_id
      WHERE a.execution_id=@execution_id AND a.current_state=N'EXTRACTING' AND l.released_at_utc IS NULL
        AND l.expires_at_utc>SYSUTCDATETIME()) THROW 52841,N'B55_STAGE_LEASE_REQUIRED',1;
'@}
 return Replace-One $Text "AS`nBEGIN" $guard
}
function Capture([string]$Text){
 $position=$Text.LastIndexOf('COMMIT TRANSACTION;')
 if($position -lt 0){throw 'APPLY_COMMIT_NOT_FOUND'}
 return $Text.Insert($position,"EXEC recon.usp_capture_runtime_vertical_output @execution_id;`n    ")
}
$parts=[Collections.Generic.List[string]]::new()
$parts.Add([IO.File]::ReadAllText((Join-Path $proposal 'schema-additions.sql')))
$parts.Add([IO.File]::ReadAllText((Join-Path $proposal 'capture-outputs.sql')))
$parts.Add([IO.File]::ReadAllText((Join-Path $proposal 'manifesto-reducer.sql')))
foreach($entry in @(@(11,'stg.usp_stage_cotacao_record','cotacoes'),@(12,'stg.usp_stage_manifesto_observation','manifestos'),@(14,'stg.usp_stage_localizacao_carga_record','localizacao_cargas'))){
 $body=Fence (Module $entry[0] $entry[1]) $entry[2] $true
 $parts.Add($body)
}
foreach($entry in @(@(18,'core.usp_apply_reconcile_publish_coletas','coletas'),@(18,'core.usp_apply_reconcile_publish_fretes','fretes'),@(11,'core.usp_apply_reconcile_publish_cotacoes','cotacoes'),@(12,'core.usp_apply_reconcile_publish_manifestos','manifestos'),@(14,'core.usp_apply_reconcile_publish_localizacao_cargas','localizacao_cargas'))){
 $body=Module $entry[0] $entry[1]
 if($entry[0] -ne 18){$body=Fence $body $entry[2] $false}
 if($entry[2] -ceq 'cotacoes'){
   $body=Replace-One $body '  IF @reference_release_id IS NULL THROW' @'
  IF ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
    OR NOT EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference WHERE execution_id=@execution_id AND reference_release_id=@reference_release_id)
    THROW 52842,N'TARIFF_DURABLE_BINDING_REQUIRED',1;
  IF @reference_release_id IS NULL THROW
'@
 }
 if($entry[2] -ceq 'manifestos'){
   $start=$body.IndexOf('    INSERT core.manifesto_pick(')
   $end=$body.IndexOf('    INSERT recon.manifesto_root_presence_observation', $start)
   if($start -lt 0 -or $end -le $start){throw 'MANIFESTO_CHILD_REPLACEMENT_ANCHOR'}
   $body=$body.Substring(0,$start)+[IO.File]::ReadAllText((Join-Path $proposal 'manifesto-children.sql'))+"`n"+$body.Substring($end)
 }
 $parts.Add((Capture $body))
}
$quality=Module 18 'recon.usp_evaluate_execution_data_quality'
$quality=Replace-One $quality 'AND @page_physical_rows = @stage_rows' @'
AND (@page_physical_rows = @stage_rows AND @entity_name<>N'manifestos'
                   OR @entity_name=N'manifestos' AND EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result m
                       WHERE m.execution_id=@execution_id AND m.validation_state=N'PASSED'
                       AND m.physical_observation_rows=@page_physical_rows AND m.reduced_root_rows=@stage_rows
                       AND m.quarantined_observation_rows=0
                       AND m.physical_observation_rows=(SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution_id)))
'@
$parts.Add($quality)
$recovery=Module 18 'ctl.usp_runtime_recovery'
$recovery=Replace-One $recovery '    @entity NVARCHAR(MAX)' '    @entity NVARCHAR(MAX),@reference_release_id BIGINT=NULL'
$recovery=Replace-One $recovery "NOT IN(N'coletas',N'fretes')" "NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas')"
$recovery=Replace-One $recovery "    BEGIN TRY`n        BEGIN TRANSACTION;" @'
    IF @entity=N'cotacoes' AND ctl.fn_runtime_tariff_valid(@execution_id,@reference_release_id)<>1
       THROW 52843,N'TARIFF_SCOPE_OR_REFERENCE_REJECTED',1;
    IF @entity<>N'cotacoes' AND @reference_release_id IS NOT NULL THROW 52843,N'TARIFF_ENTITY_MISMATCH',1;
    BEGIN TRY
        BEGIN TRANSACTION;
'@
$recovery=Replace-One $recovery 'OR @rows<>(SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id)' @'
OR @rows<>CASE WHEN @entity=N'manifestos' THEN
                   (SELECT COUNT_BIG(*) FROM stg.manifesto_observation WITH(HOLDLOCK) WHERE execution_id=@execution_id)
                   ELSE (SELECT COUNT_BIG(*) FROM stg.execution_record WITH(HOLDLOCK) WHERE execution_id=@execution_id) END
'@
$recovery=Replace-One $recovery '                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);' @'
                    @policy_version,@policy_fingerprint,@rows,@pages,@audit_hash,@evidence_hash,@now);
            IF @entity=N'cotacoes' AND NOT EXISTS(SELECT 1 FROM ctl.runtime_cotacao_reference WITH(UPDLOCK,HOLDLOCK) WHERE execution_id=@execution_id)
              INSERT ctl.runtime_cotacao_reference(execution_id,reference_release_id,reference_fingerprint,bound_at_utc)
              SELECT @execution_id,@reference_release_id,source_fingerprint,@now FROM ref.reference_release WHERE reference_release_id=@reference_release_id;
'@
$recovery=Replace-One $recovery '            SET @typed_valid=1;' @'
            OR EXISTS(SELECT 1 FROM ctl.cotacao_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'cotacoes' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.localizacao_carga_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'localizacao_cargas' AND validation_state=N'PASSED' AND typed_candidate_rows=@candidates)
            OR EXISTS(SELECT 1 FROM ctl.manifesto_promotion_result WITH(HOLDLOCK) WHERE execution_id=@execution_id
              AND @entity=N'manifestos' AND validation_state=N'PASSED' AND reduced_root_rows=@candidates)
            SET @typed_valid=1;
'@
$recovery=Replace-One $recovery '                ELSE EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,' @'
                ELSE IF @entity=N'manifestos' EXEC core.usp_prepare_manifesto_candidate_set @execution_id,@contract_version,
                    @contract_fingerprint,@configuration_version,@configuration_fingerprint;
                ELSE EXEC core.usp_prepare_staged_execution @execution_id,@contract_version,
'@
$recovery=Replace-One $recovery '            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,' @'
            ELSE IF @entity=N'manifestos' EXEC core.usp_apply_reconcile_publish_manifestos @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE IF @entity=N'cotacoes' EXEC core.usp_apply_reconcile_publish_cotacoes @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint,@reference_release_id;
            ELSE IF @entity=N'localizacao_cargas' EXEC core.usp_apply_reconcile_publish_localizacao_cargas @execution_id,@contract_version,
                @contract_fingerprint,@configuration_version,@configuration_fingerprint;
            ELSE EXEC core.usp_apply_reconcile_publish_fretes @execution_id,@contract_version,
'@
$parts.Add($recovery)
$status=Module 18 'ctl.usp_runtime_status'
$status=Replace-One $status '@expected_revision NVARCHAR(MAX),@entity NVARCHAR(MAX)' '@expected_revision NVARCHAR(MAX),@entity NVARCHAR(MAX),@reference_release_id BIGINT=NULL'
$status=Replace-One $status '@policy_version,@policy_fingerprint,@expected_revision,@entity;' '@policy_version,@policy_fingerprint,@expected_revision,@entity,@reference_release_id;'
$parts.Add($status)
$authorization=Module 18 'ctl.usp_runtime_authorization'
$authorization=Replace-One $authorization '    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@scope_material))<>22' @'
    DECLARE @tariff_scope BIT=CASE WHEN JSON_VALUE(@scope_material,'$.version')=N'runtime-scope-tariff-v1' THEN 1 ELSE 0 END;
    IF @tariff_scope=1
    BEGIN
      INSERT @keys VALUES(N'referenceReleaseId',20);
      IF JSON_VALUE(@scope_material,'$.entity')<>N'cotacoes' OR JSON_VALUE(@scope_material,'$.workload')<>N'cotacoes'
         OR JSON_VALUE(@scope_material,'$.mode')<>N'BACKFILL'
         OR TRY_CONVERT(BIGINT,JSON_VALUE(@scope_material,'$.referenceReleaseId')) IS NULL
         OR TRY_CONVERT(BIGINT,JSON_VALUE(@scope_material,'$.referenceReleaseId'))<1
         THROW 52844,N'TARIFF_SCOPE_SCHEMA',1;
    END;
    IF (SELECT COUNT_BIG(*) FROM OPENJSON(@scope_material))<>22+CONVERT(INT,@tariff_scope)
'@
$authorization=Replace-One $authorization "OR JSON_VALUE(@scope_material,'$.version') COLLATE Latin1_General_100_BIN2<>N'runtime-scope-v1'" "OR JSON_VALUE(@scope_material,'$.version') COLLATE Latin1_General_100_BIN2<>CASE WHEN @tariff_scope=1 THEN N'runtime-scope-tariff-v1' ELSE N'runtime-scope-v1' END"
$parts.Add($authorization)
$file=Join-Path $root 'database/migrations/V022__extend_five_vertical_runtime.sql'
if(Test-Path (Join-Path $proposal 'applied.json')){throw 'APPLIED_MIGRATION_IMMUTABLE'}
[IO.File]::WriteAllText($file,($parts -join "`n").Replace("`r`n","`n"),$encoding)
'B55_SQL_DRAFT_GENERATED_NOT_APPLIED'

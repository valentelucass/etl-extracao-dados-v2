#Requires -Version 7.5
param([switch]$SelfTest,[string]$SqlPath='database/migrations/V024__bind_source_protocols_and_users_runtime.sql')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Read([string]$path){$utf8.GetString([IO.File]::ReadAllBytes((Join-Path $root $path))).Replace("`r`n","`n")}
function Procedure([string]$text,[string]$name){
 $m=[regex]::Matches($text,'(?ms)^CREATE OR ALTER PROCEDURE '+[regex]::Escape($name)+'\b.*?^GO\s*$')
 if($m.Count -ne 1){throw 'B60_SQL_PROCEDURE_CARDINALITY'}
 $m[0].Value.TrimEnd()
}
$v7=Read 'database/migrations/V007__create_usuarios_current_history.sql'
$v22=Read 'database/migrations/V022__extend_five_vertical_runtime.sql'
function Validate([string]$sql){
 if($sql.Length -gt 150000 -or $sql.Contains(':On Error')){throw 'B60_MIGRATION_BOUND'}
 foreach($required in @(
  'CREATE TABLE ctl.source_protocol_binding','CREATE TABLE ctl.execution_source_protocol',
  'FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id',
  'SELECT source_instance,source_kind,registered_at_utc FROM ctl.source_catalog;',
  "SELECT a.execution_id,p.source_instance,s.source_kind,N'LEGACY'",
  'CREATE TRIGGER ctl.trg_source_catalog_identity_immutable',
  'CREATE TRIGGER ctl.trg_execution_source_protocol_immutable',
  'CREATE TRIGGER ctl.trg_source_protocol_binding_immutable',
  'CREATE TRIGGER ctl.trg_execution_attempt_bind_protocol',
  'REFERENCES ctl.source_protocol_binding(source_instance,source_kind)',
  "CASE WHEN p.entity_name=N'usuarios' THEN N'GRAPHQL'",
  "(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas')",
  'SOURCE_PROTOCOL_NOT_REGISTERED','SOURCE_PROTOCOL_SCOPE_MISMATCH',
  'ctl.fn_runtime_consumed_scope(j.actual,1,NULL)=1',
  'CHECK(audited_rows>0 AND audited_pages>=1)',
  "(p.entity_name<>N'usuarios' AND e.audited_pages<2)",
  "q.expected_checks=4 AND q.completed_checks=4 AND q.passed_checks=4",
  'USERS_APPLY_DURABLE_SEAL_REQUIRED','USERS_STAGE_NULL_INPUT',
  'ctl.fn_runtime_consumed_scope(@b60_actual,1,NULL)',
  'JOIN ctl.execution_source_protocol s WITH(HOLDLOCK) ON s.execution_id=a.execution_id AND s.source_instance=@source_instance',
  'requested_page_size<>20 OR page_attempt<>1',
  "physical_rows NOT BETWEEN 1 AND 20 OR terminal_empty_page<>0",
  "@mode NOT IN(N'BACKFILL',N'REPLAY')", "a.window_strategy=N'FULL' AND s.source_kind=N'GRAPHQL'",
  "runtime-recovery-v1|14:GRAPHQL|44:graphql-users-snapshot|%",
  "@execution_state<>N'PROMOTED' OR @lease_valid=0 OR @audit_valid=0",
  "@typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0",
  'a.applied_at_utc>=u.published_at_utc AND a.applied_at_utc<=@now',
  'a.authorized_at_utc<=u.published_at_utc','c.applied_at_utc<>a.applied_at_utc',
  'h.changed_at_utc<>a.applied_at_utc',
  "AND (@entity<>N'usuarios' OR @contract_verified=1 AND @users_receipt_valid=1)",
  'LEFT JOIN recon.usuario_reconciliation_result users_receipt',
  "IF @reason<>N'ELIGIBLE' THROW 52306",'DATALENGTH(@expected_revision)<>128 THROW 52305'
 )){if(-not $sql.Contains($required)){throw ('B60_SQL_GATE_MISSING: '+$required)}}
 if($sql -match '(?im)^\s*(GRANT|REVOKE|TRUNCATE|DELETE\s+FROM|CREATE\s+(LOGIN|USER|DATABASE))\b' -or
    $sql.Contains('a.applied_at_utc<=u.published_at_utc') -or
    $sql -match '(?im)^\s*UPDATE\s+ctl\.source_catalog\s+SET\s+source_kind'){
  throw 'B60_SQL_FORBIDDEN_CHANGE'
 }
 foreach($name in @('stg.usp_stage_usuario_record','core.usp_apply_reconcile_publish_usuarios')){
  $current=Procedure $sql $name
  $unfenced=[regex]::Replace($current,'(?ms)^    -- B60_[A-Z_]+_BEGIN\n.*?^    -- B60_[A-Z_]+_END\n','')
  $unfenced=$unfenced.Replace("`n`n`n","`n`n")
  if($unfenced -cne (Procedure $v7 $name)){throw ('B60_V007_ALGORITHM_DRIFT: '+$name)}
 }
 $apply=Procedure $sql 'core.usp_apply_reconcile_publish_usuarios'
 if($apply.IndexOf('EXEC @b60_lock=sys.sp_getapplock') -gt $apply.IndexOf('AS attempt WITH (UPDLOCK, HOLDLOCK)')){throw 'B60_SQL_LOCK_ORDER'}
 $de=[regex]::Match($v22,"(?s)        IF @operation=N'SEAL'.*?(?=        DECLARE @contract_verified)").Value
 $actual=[regex]::Match($sql,"(?s)        IF @operation=N'SEAL' AND @entity<>N'usuarios'.*?(?=        DECLARE @contract_verified)").Value
 if(-not $de -or $actual.Replace(" AND @entity<>N'usuarios'",'') -cne $de){throw 'B60_DATA_EXPORT_SEAL_DRIFT'}
 foreach($name in @('coletas','fretes','manifestos','cotacoes','localizacao_cargas')){
  if(-not $sql.Contains('core.usp_apply_reconcile_publish_'+$name)){throw 'B60_DATA_EXPORT_REMOVED'}
 }
}
$sql=Read $SqlPath
Validate $sql
$guards=0
if($SelfTest){
 foreach($text in @(
  'CREATE TRIGGER ctl.trg_source_catalog_identity_immutable',
  'CREATE TRIGGER ctl.trg_execution_attempt_bind_protocol',
  'REFERENCES ctl.source_protocol_binding(source_instance,source_kind)',
  'SOURCE_PROTOCOL_NOT_REGISTERED','ctl.fn_runtime_consumed_scope(j.actual,1,NULL)=1',
  "(p.entity_name<>N'usuarios' AND e.audited_pages<2)",
  'USERS_APPLY_DURABLE_SEAL_REQUIRED','USERS_STAGE_NULL_INPUT',
  'ctl.fn_runtime_consumed_scope(@b60_actual,1,NULL)',
  'requested_page_size<>20 OR page_attempt<>1',
  "@typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0",
  'a.applied_at_utc>=u.published_at_utc AND a.applied_at_utc<=@now',
  'c.applied_at_utc<>a.applied_at_utc',
  'LEFT JOIN recon.usuario_reconciliation_result users_receipt'
 )){
  $refused=$false
  try{Validate $sql.Replace($text,'MUTATED_CONTRACT')}catch{$refused=$true}
  if(-not $refused){throw 'B60_SQL_MUTANT_ACCEPTED'}
  $guards++
 }
}
$baseline=Read 'database/baseline/001_schema_foundation_baseline.sql'
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/coletas-temporal-integration/manifesto.json')){
 $successor=& (Join-Path $PSScriptRoot 'Test-ColetasTemporalIntegration.ps1') -AsMap
 $baseline=Read $successor['database/baseline/001_schema_foundation_baseline.sql'].snapshot
}
if([regex]::Matches($baseline,'V024__bind_source_protocols_and_users_runtime.sql').Count -ne 1 -or
 -not $baseline.TrimEnd().EndsWith(':r "..\migrations\V024__bind_source_protocols_and_users_runtime.sql"')){
 throw 'B60_BASELINE_DELTA'
}
[pscustomobject]@{passed=$true;layer='STATIC_SQL_CONTRACT';sqlExecuted=$false;sqlCompiled=$false;guards=$guards;domainAlgorithmPreserved=$true;dataExportSealPreserved=$true}

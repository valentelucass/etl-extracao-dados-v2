#Requires -Version 7.5
param([switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function ReadText([string]$path){$utf8.GetString([IO.File]::ReadAllBytes((Join-Path $root $path))).Replace("`r`n","`n")}
$old=ReadText 'database/migrations/V022__extend_five_vertical_runtime.sql'
$draft=ReadText 'database/preparation/bloco59/usuarios-runtime.sql'
function Validate([string]$text){
 if($text.Length -gt 80000 -or -not $text.StartsWith('-- B59 PREPARACAO_NAO_EXECUTADA.')){throw 'B59_SQL_PREPARATION_BOUNDARY'}
 if([regex]::Matches($text,'CREATE OR ALTER PROCEDURE').Count -ne 1 -or
    $text -match '(?im)^\s*(GRANT|DENY|REVOKE|DROP|TRUNCATE|DELETE|CREATE TABLE|ALTER TABLE)\b') {throw 'B59_SQL_PREPARATION_SCOPE'}
 foreach($required in @(
  'CREATE OR ALTER PROCEDURE ctl.usp_runtime_recovery',
  'ctl.fn_runtime_consumed_scope(@b54_actual,CASE WHEN @operation=N''READ'' THEN 0 ELSE 1 END,NULL)',
  "NOT IN(N'coletas',N'fretes',N'manifestos',N'cotacoes',N'localizacao_cargas',N'usuarios')",
  "runtime-recovery-v1|14:GRAPHQL|44:graphql-users-snapshot|%",
  "@mode NOT IN(N'BACKFILL',N'REPLAY')", "a.window_strategy=N'FULL' AND s.source_kind=N'GRAPHQL'",
  "@terminal=1 AND @terminal_page=@last_page", 'requested_page_size<>20 OR page_attempt<>1',
  "terminal_evidence_kind NOT IN(N'NONE',N'GRAPHQL_PAGE_INFO')",
  "IF @operation=N'SEAL' AND @entity=N'usuarios'",
  "@execution_state<>N'PROMOTED' OR @lease_valid=0 OR @audit_valid=0",
  "@typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0",
  'e.passed_checks=4 AND e.failed_checks=0 AND e.failed_rows=0',
  'e.candidate_rows=@candidates AND e.promotion_recorded_at_utc=p.promoted_at_utc',
  'FROM stg.usuario_record WITH(HOLDLOCK)',
  "conflicting_root_keys=0 AND generic_quarantine_rows=0",
  "AND (@entity<>N'usuarios' OR @contract_verified=1 AND @users_receipt_valid=1)",
  "a.authorization_state=N'APPLIED'", 'a.applied_at_utc>=a.authorized_at_utc AND a.applied_at_utc<=u.published_at_utc',
  'u.history_rows=(SELECT COUNT_BIG(*) FROM core.usuario_history WITH(HOLDLOCK)',
  'h.state_hash<>c.result_state_hash OR h.attribute_hash<>c.result_attribute_hash',
  'h.observation_order_execution_id<>c.observation_order_execution_id',
  'LEFT JOIN recon.usuario_reconciliation_result users_receipt',
  "ELSE IF @entity=N'usuarios' EXEC core.usp_apply_reconcile_publish_usuarios",
  "IF @entity<>N'usuarios' OR @quality<>N'PASSED'",
  "IF @reason<>N'ELIGIBLE' THROW 52306", 'DATALENGTH(@expected_revision)<>128 THROW 52305'
 )){if(-not $text.Contains($required)){throw 'B59_SQL_REQUIRED_GATE_MISSING'}}
 foreach($column in @('inserted_rows','updated_rows','reactivated_rows','noop_rows','stale_noop_rows')){
  if(-not $text.Contains("COALESCE(users_receipt.$column,typed.$column,result.$column)")){throw 'B59_SQL_TYPED_RECEIPT_REQUIRED'}
 }
 # Preserve the exact DE seal block and all previous branch-specific promotion calls.
 $oldSeal=[regex]::Match($old,"(?s)        IF @operation=N'SEAL'.*?(?=        DECLARE @contract_verified)").Value
 $newSeal=[regex]::Match($text,"(?s)        IF @operation=N'SEAL' AND @entity<>N'usuarios'.*?(?=        DECLARE @contract_verified)").Value
 if(-not $oldSeal -or $newSeal.Replace(" AND @entity<>N'usuarios'",'') -cne $oldSeal){throw 'B59_SQL_DATA_EXPORT_SEAL_CHANGED'}
 foreach($entry in @('coletas','fretes','manifestos','cotacoes','localizacao_cargas')){
  if(-not $text.Contains('core.usp_apply_reconcile_publish_'+$entry)){throw 'B59_SQL_PREVIOUS_VERTICAL_REMOVED'}
 }
 if($text.Contains('a.applied_at_utc=u.published_at_utc')){throw 'B59_SQL_V007_CLOCK_SEMANTICS_CHANGED'}
}
Validate $draft
$guards=0
if($SelfTest){
 foreach($mutation in @(
  $draft.Replace('GRAPHQL_PAGE_INFO','NONE'),
  $draft.Replace("@execution_state<>N'PROMOTED' OR @lease_valid=0 OR @audit_valid=0", "@execution_state<>N'EXTRACTING' OR @lease_valid=0 OR @audit_valid=0"),
  $draft.Replace('recon.usuario_reconciliation_result users_receipt','recon.execution_reconciliation_result users_receipt'),
  $draft.Replace("@typed_valid=0 OR @quality<>N'PASSED' OR @dq_integrity=0",'@typed_valid=0'),
  $draft.Replace('fn_runtime_consumed_scope','fn_unbound_scope'),
  ($draft+"`nGRANT EXECUTE TO public;`n")
 )){
  $refused=$false
  try{Validate $mutation}catch{$refused=$true}
  if(-not $refused){throw 'B59_SQL_GUARD_ACCEPTED_MUTATION'}
  $guards++
 }
}
[pscustomobject]@{passed=$true;layer='STATIC_SQL_PREPARATION_ONLY';sqlExecuted=$false;sqlCompiled=$false;guards=$guards}

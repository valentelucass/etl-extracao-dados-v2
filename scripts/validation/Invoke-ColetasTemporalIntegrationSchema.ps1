#Requires -Version 7.5
param([switch]$Install,[switch]$Inspect,[switch]$Correction,[Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=[IO.Path]::GetFullPath((Join-Path $root $EvidenceDirectory))
$allowed=[IO.Path]::GetFullPath((Join-Path $root 'target/coletas-temporal-integration-20260910/'))
if(-not $evidence.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $evidence)){throw 'COL_SCHEMA_EVIDENCE_TARGET'}
$null=New-Item -ItemType Directory -Path $evidence
$migrations=@('V025__restore_coletas_extraction_audit.sql','V026__create_coletas_temporal_reference.sql','V027__create_coletas_exact_temporal_laboratory.sql')
if($Correction){$migrations=@('V028__align_coletas_laboratory_presence_collation.sql')}
$frozen=@(foreach($name in $migrations){
    $file=Join-Path $root ('database/migrations/'+$name)
    Copy-Item -LiteralPath $file -Destination (Join-Path $evidence $name)
    @{path=('database/migrations/'+$name);sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()}
})
$frozen|ConvertTo-Json|Set-Content (Join-Path $evidence 'migrations.json') -Encoding utf8
function Sql([string]$database,[string]$query,[string]$name){
    $output=& sqlcmd -S localhost -C -E -d $database -l 5 -t 30 -b -h -1 -W -Q $query 2>&1
    $code=$LASTEXITCODE
    $output|Set-Content (Join-Path $evidence ($name+'.log')) -Encoding utf8
    if($code -ne 0){throw ('COL_SCHEMA_'+$name)}
    return $output
}
$null=Sql 'master' "SET NOCOUNT ON; IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53190,N'EXACT_SHADOW_MISSING',1; SELECT N'EXACT_SHADOW_ONLINE';" 'master-preflight'
$before=@'
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53190,N'WRONG_TARGET',1;
SELECT N'EXACT_TARGET_WINDOWS_CONFIRMED';
SELECT COUNT_BIG(*) AS attempts FROM ctl.execution_attempt;
SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;
'@
$null=Sql 'ETL_SISTEMA_V2_SHADOW' $before 'before'
if($Inspect){Write-Output 'COL_SCHEMA_INSPECTED';exit 0}
$ledger=Join-Path $evidence 'ledger.jsonl'
@{state='RESERVED_OUTCOME_UNKNOWN';target='localhost/ETL_SISTEMA_V2_SHADOW';ddl=$(if($Install){'VERSIONED_ADDITIVE_INSTALL'}else{'MIGRATION_ROLLBACK_QUALIFICATION'});migrations=$frozen;testData='NONE';authorization='User 2026-09-10 and AGENTS local shadow'}|ConvertTo-Json -Depth 6 -Compress|Add-Content $ledger -Encoding utf8
$script=@'
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53190,N'WRONG_TARGET',1;
IF OBJECT_ID(N'ctl.execution_audit') IS NOT NULL OR OBJECT_ID(N'stg.coleta_temporal_run') IS NOT NULL OR OBJECT_ID(N'stg.coleta_exact_time') IS NOT NULL THROW 53190,N'RECONCILE_EXISTING_MIGRATION_BEFORE_RETRY',1;
IF OBJECT_ID(N'ctl.execution_source_protocol') IS NULL THROW 53190,N'V024_REQUIRED',1;
BEGIN TRANSACTION;
GO
'@
if($Correction){
    $script=$script.Replace("IF OBJECT_ID(N'ctl.execution_audit') IS NOT NULL OR OBJECT_ID(N'stg.coleta_temporal_run') IS NOT NULL OR OBJECT_ID(N'stg.coleta_exact_time') IS NOT NULL THROW 53190,N'RECONCILE_EXISTING_MIGRATION_BEFORE_RETRY',1;",
        "IF OBJECT_ID(N'core.coleta_temporal_laboratory') IS NULL OR (SELECT collation_name FROM sys.columns WHERE object_id=OBJECT_ID(N'core.coleta_temporal_laboratory') AND name=N'sequence_code_presence')=N'Latin1_General_100_BIN2' THROW 53190,N'RECONCILE_CORRECTION_BEFORE_RETRY',1;")
}
foreach($name in $migrations){$script+="`n:r `"$(Join-Path $evidence $name)`"`nGO`n"}
$script+=@'
IF OBJECT_ID(N'stg.usp_stage_coleta_exact_time') IS NULL OR OBJECT_ID(N'recon.usp_qualify_coleta_temporal_v2') IS NULL
    OR OBJECT_ID(N'core.usp_consume_coleta_temporal_laboratory') IS NULL THROW 53190,N'MIGRATION_INCOMPLETE',1;
IF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 53190,N'MIGRATION_TRANSACTION_INVALID',1;
'@
$script+=if($Install){"`nCOMMIT TRANSACTION;`nPRINT N'COL_SCHEMA_INSTALLED';`n"}else{"`nROLLBACK TRANSACTION;`nPRINT N'COL_SCHEMA_QUALIFIED_ROLLBACK';`n"}
$path=Join-Path $evidence 'executed.sql'
[IO.File]::WriteAllText($path,$script,[Text.UTF8Encoding]::new($false))
$output=& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 30 -b -f 65001 -i $path 2>&1
$code=$LASTEXITCODE
$output|Set-Content (Join-Path $evidence 'execution.log') -Encoding utf8
# sqlcmd disconnect rolls back an uncommitted DDL transaction; inspect authoritative objects before retry.
$null=Sql 'ETL_SISTEMA_V2_SHADOW' ($before+"`nSELECT COUNT_BIG(*) FROM sys.objects WHERE name IN(N'coleta_exact_time',N'coleta_temporal_run',N'coleta_temporal_laboratory');") 'after'
@{state=$(if($code -eq 0){'CONFIRMED'}else{'FAILED_RECONCILED'});exit=$code;installed=($Install -and $code -eq 0)}|ConvertTo-Json -Compress|Add-Content $ledger -Encoding utf8
if($code -ne 0){Get-Content (Join-Path $evidence 'execution.log') -Tail 12;throw 'COL_SCHEMA_EXECUTION_FAILED'}
Write-Output $output

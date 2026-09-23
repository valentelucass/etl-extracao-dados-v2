#Requires -Version 7.5
param([switch]$Install,[Parameter(Mandatory)][string]$Attempt,[ValidateRange(29,99)][int]$FirstVersion=29,[ValidateRange(29,99)][int]$LastVersion=31)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($Attempt -cnotmatch '^[a-z0-9-]{1,64}$'){throw 'REL_SCHEMA_ATTEMPT'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-relacional-20260911-01'
$evidence=Join-Path $round $Attempt
if(Test-Path -LiteralPath $evidence){throw 'REL_SCHEMA_ATTEMPT_EXISTS'}
New-Item -ItemType Directory -Path $evidence | Out-Null
if($LastVersion -lt $FirstVersion){throw 'REL_SCHEMA_VERSION_RANGE'}
$names=@(foreach($version in $FirstVersion..$LastVersion){
    $matching=@(Get-ChildItem -LiteralPath (Join-Path $root 'database/migrations') -Filter ('V{0:D3}__*.sql' -f $version))
    if($matching.Count -ne 1){throw 'REL_SCHEMA_VERSION_MISSING_OR_DUPLICATE'}
    $matching[0].Name
})
$files=@(foreach($name in $names){
    $file=Join-Path $root ('database/migrations/'+$name)
    Copy-Item -LiteralPath $file -Destination (Join-Path $evidence $name)
    @{path=('database/migrations/'+$name);sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()}
})
$files|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $evidence 'migrations.json') -Encoding utf8
function Sql([string]$database,[string]$query,[string]$name){
    $output=& sqlcmd -S localhost -C -E -d $database -l 5 -t 30 -b -h -1 -W -Q $query 2>&1
    $code=$LASTEXITCODE
    $output|Set-Content -LiteralPath (Join-Path $evidence ($name+'.log')) -Encoding utf8
    if($code -ne 0){throw ('REL_SCHEMA_'+$name)}
    return $output
}
$null=Sql 'master' "SET NOCOUNT ON; IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53290,N'EXACT_SHADOW_MISSING',1; SELECT N'EXACT_SHADOW_ONLINE';" 'master-preflight'
$inspect=@'
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53290,N'WRONG_TARGET',1;
SELECT N'EXACT_TARGET_WINDOWS_CONFIRMED';
SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;
'@
$null=Sql 'ETL_SISTEMA_V2_SHADOW' $inspect 'before'
$ledger=Join-Path $evidence 'ledger.jsonl'
@{state='RESERVED_OUTCOME_UNKNOWN';target='localhost/ETL_SISTEMA_V2_SHADOW';install=[bool]$Install;migrations=$files;budgetSeconds=300;queryTimeoutSeconds=30;testData='NONE';authorization='User adopted A-J 2026-09-11'}|ConvertTo-Json -Depth 5 -Compress|Add-Content -LiteralPath $ledger -Encoding utf8
$script=@'
:On Error exit
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53290,N'WRONG_TARGET',1;
IF OBJECT_ID(N'ctl.relational_lab_run') IS NOT NULL THROW 53290,N'RECONCILE_EXISTING_MIGRATION_BEFORE_RETRY',1;
IF OBJECT_ID(N'stg.coleta_exact_time') IS NULL THROW 53290,N'V027_REQUIRED',1;
BEGIN TRANSACTION;
GO
'@
foreach($name in $names){$script+="`n:r `"$(Join-Path $evidence $name)`"`nGO`n"}
if($FirstVersion -gt 29){
    $script=$script.Replace("IF OBJECT_ID(N'ctl.relational_lab_run') IS NOT NULL THROW 53290,N'RECONCILE_EXISTING_MIGRATION_BEFORE_RETRY',1;",
        "IF OBJECT_ID(N'ctl.relational_lab_run') IS NULL THROW 53290,N'REL_LAB_INITIAL_SCHEMA_REQUIRED',1;")
}
$script+=@'
IF OBJECT_ID(N'stg.usp_capture_relational_laboratory') IS NULL OR OBJECT_ID(N'core.usp_resolve_relational_laboratory') IS NULL
    OR OBJECT_ID(N'ctl.usp_claim_relational_lab_backlog') IS NULL THROW 53290,N'MIGRATION_INCOMPLETE',1;
IF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 53290,N'MIGRATION_TRANSACTION_INVALID',1;
'@
$script+=if($Install){"`nCOMMIT TRANSACTION;`nPRINT N'REL_SCHEMA_INSTALLED';`n"}else{"`nROLLBACK TRANSACTION;`nPRINT N'REL_SCHEMA_QUALIFIED_ROLLBACK';`n"}
$path=Join-Path $evidence 'executed.sql'
[IO.File]::WriteAllText($path,$script,[Text.UTF8Encoding]::new($false))
$output=& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 30 -b -f 65001 -i $path 2>&1
$code=$LASTEXITCODE
$output|Set-Content -LiteralPath (Join-Path $evidence 'execution.log') -Encoding utf8
$null=Sql 'ETL_SISTEMA_V2_SHADOW' ($inspect+"`nSELECT COUNT_BIG(*) FROM sys.objects WHERE name LIKE N'%relational_lab%';") 'after'
@{state=$(if($code -eq 0){'CONFIRMED'}else{'FAILED_RECONCILED'});exit=$code;installed=($Install -and $code -eq 0)}|ConvertTo-Json -Compress|Add-Content -LiteralPath $ledger -Encoding utf8
Get-Content -LiteralPath (Join-Path $evidence 'execution.log') -Tail 20
if($code -ne 0){throw 'REL_SCHEMA_EXECUTION_FAILED'}

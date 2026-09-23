#Requires -Version 7.5
param([switch]$Install,[Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
 [ValidateRange(52,199)][int]$FirstVersion=52,[ValidateRange(52,199)][int]$LastVersion=53,
 [switch]$BaselineTail,[string]$ValidationScript='',
 [ValidatePattern('^[a-z0-9-]{1,80}$')][string]$Round='macrobloco-analitico-20260912-01')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidenceRound=Join-Path $root ('target/'+$Round)
$evidence=Join-Path $evidenceRound $Attempt
if(Test-Path -LiteralPath $evidence){throw 'ANA_SCHEMA_ATTEMPT_EXISTS'}
if($LastVersion -lt $FirstVersion){throw 'ANA_SCHEMA_VERSION_RANGE'}
if($Install -and ($BaselineTail -or $ValidationScript)){throw 'ANA_SCHEMA_INSTALL_NO_EXERCISE'}
[void][IO.Directory]::CreateDirectory($evidence)
$files=@(foreach($version in $FirstVersion..$LastVersion){
 $matching=@(Get-ChildItem -LiteralPath (Join-Path $root 'database/migrations') -Filter ('V{0:D3}__*.sql' -f $version))
 if($matching.Count -ne 1){throw 'ANA_SCHEMA_VERSION_MISSING_OR_DUPLICATE'}
 Copy-Item -LiteralPath $matching[0].FullName -Destination (Join-Path $evidence $matching[0].Name)
 @{path=('database/migrations/'+$matching[0].Name);name=$matching[0].Name;sha256=(Get-FileHash -LiteralPath $matching[0].FullName).Hash.ToLowerInvariant()}
})
$files|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $evidence 'migrations.json') -Encoding utf8
if($BaselineTail){
 $tail=@(Get-Content (Join-Path $root 'database/baseline/001_schema_foundation_baseline.sql') | Where-Object {$_ -match '^:r '})[-$files.Count..-1]
 $expected=@($files|ForEach-Object {':r "..\migrations\'+$_.name+'"'})
 if(($tail -join "`n") -cne ($expected -join "`n")){throw 'ANA_SCHEMA_BASELINE_SUFFIX_MISMATCH'}
}
if($ValidationScript){
 $validation=[IO.Path]::GetFullPath((Join-Path $root $ValidationScript))
 if(-not $validation.StartsWith((Join-Path $root 'database/validation/'),[StringComparison]::OrdinalIgnoreCase)){throw 'ANA_SCHEMA_VALIDATION_PATH'}
 Copy-Item -LiteralPath $validation -Destination (Join-Path $evidence 'validation.sql')
}
function Sql([string]$database,[string]$query,[string]$name){
 $output=& sqlcmd -S localhost -C -E -d $database -l 5 -t 30 -b -h -1 -W -Q $query 2>&1
 $code=$LASTEXITCODE
 $output|Set-Content -LiteralPath (Join-Path $evidence ($name+'.log')) -Encoding utf8
 if($code -ne 0){throw ('ANA_SCHEMA_'+$name)}
 return $output
}
$null=Sql 'master' "SET NOCOUNT ON; IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53590,N'EXACT_SHADOW_MISSING',1; SELECT N'EXACT_SHADOW_ONLINE';" 'master-preflight'
$inspect=@'
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53590,N'WRONG_TARGET',1;
SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;
'@
$before=@(Sql 'ETL_SISTEMA_V2_SHADOW' $inspect 'before')
$catalog="SET NOCOUNT ON; SELECT SCHEMA_NAME(o.schema_id)+N'.'+o.name,CONVERT(VARCHAR(64),HASHBYTES('SHA2_256',m.definition),2) FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0 ORDER BY 1;"
$catalogBefore=@(Sql 'ETL_SISTEMA_V2_SHADOW' $catalog 'catalog-before')
$ledger=Join-Path $evidence 'ledger.jsonl'
@{state='RESERVED_OUTCOME_UNKNOWN';target='localhost/ETL_SISTEMA_V2_SHADOW';install=[bool]$Install;migrations=$files;budgetSeconds=300;queryTimeoutSeconds=30;domainDml=[bool]$ValidationScript;rollbackOnly=(-not $Install);baselineTail=[bool]$BaselineTail;authorization=$(if($Round -eq 'macrobloco-campanhas-integrais-20260915-01'){'P03 explicit local schema request 2026-09-19'}else{'Adopted analytical A-N request 2026-09-12'})}|ConvertTo-Json -Depth 5 -Compress|Add-Content -LiteralPath $ledger -Encoding utf8
$script=@'
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 53590,N'WRONG_TARGET',1;
IF OBJECT_ID(N'core.expansion_lab_root') IS NULL THROW 53590,N'EXPANSION_REQUIRED',1;
'@
if($FirstVersion -eq 52){$script+="`nIF OBJECT_ID(N'ctl.analytic_lab_run') IS NOT NULL THROW 53590,N'RECONCILE_EXISTING_MIGRATION_BEFORE_RETRY',1;`n"}
else{$script+="`nIF OBJECT_ID(N'ctl.analytic_lab_run') IS NULL THROW 53590,N'ANA_INITIAL_REQUIRED',1;`n"}
$script+="BEGIN TRANSACTION;`nGO`n"
foreach($file in $files){$script+=':r "'+(Join-Path $evidence $file.name)+'"'+"`nGO`n"}
$script+=$catalog+"`nGO`n"
if($ValidationScript){$script+=':r "'+(Join-Path $evidence 'validation.sql')+'"'+"`nGO`n"}
$script+=@'
IF OBJECT_ID(N'core.usp_apply_analytic_raster') IS NULL THROW 53590,N'MIGRATION_INCOMPLETE',1;
IF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 53590,N'MIGRATION_TRANSACTION_INVALID',1;
'@
$script+=if($Install){"`nCOMMIT TRANSACTION;`nPRINT N'ANA_SCHEMA_INSTALLED';`n"}else{"`nROLLBACK TRANSACTION;`nPRINT N'ANA_SCHEMA_QUALIFIED_ROLLBACK';`n"}
$path=Join-Path $evidence 'executed.sql'
[IO.File]::WriteAllText($path,$script,[Text.UTF8Encoding]::new($false))
$output=& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 30 -b -f 65001 -i $path 2>&1
$code=$LASTEXITCODE
$output|Set-Content -LiteralPath (Join-Path $evidence 'execution.log') -Encoding utf8
$after=@(Sql 'ETL_SISTEMA_V2_SHADOW' $inspect 'after')
$catalogAfter=@(Sql 'ETL_SISTEMA_V2_SHADOW' $catalog 'catalog-after')
$preserved=@($before|Where-Object {$_ -notin $after}).Count -eq 0
if(-not $Install -and ($catalogBefore -join "`n") -cne ($catalogAfter -join "`n")){$preserved=$false}
@{state=$(if($code -eq 0 -and $preserved){'CONFIRMED'}else{'FAILED_RECONCILED'});exit=$code;installed=($Install -and $code -eq 0);preexistingCountsPreserved=$preserved;tablesBefore=$before.Count;tablesAfter=$after.Count}|ConvertTo-Json -Compress|Add-Content -LiteralPath $ledger -Encoding utf8
Get-Content -LiteralPath (Join-Path $evidence 'execution.log') -Tail 18
if($code -ne 0 -or -not $preserved){throw 'ANA_SCHEMA_EXECUTION_FAILED'}

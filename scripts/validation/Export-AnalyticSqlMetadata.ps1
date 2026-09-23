#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-analitico-20260912-01'
$path=Join-Path $round ($Attempt+'-sql-metadata.json')
if(Test-Path -LiteralPath $path){throw 'ANA_METADATA_EXISTS'}
& sqlcmd -S localhost -C -E -d master -l 5 -t 10 -b -Q "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53590,N'EXACT_SHADOW_MISSING',1;" *> (Join-Path $round ($Attempt+'-metadata-master.log'))
if($LASTEXITCODE -ne 0){throw 'ANA_METADATA_MASTER'}
$query=@'
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53590,N'WRONG_TARGET',1;
SELECT v.name localName,c.column_id ordinal,c.name columnName,t.name sqlType,c.max_length bytes,c.precision,c.scale,c.is_nullable nullable,c.collation_name collation
FROM sys.views v JOIN sys.schemas s ON s.schema_id=v.schema_id JOIN sys.columns c ON c.object_id=v.object_id
JOIN sys.types t ON t.user_type_id=c.user_type_id
WHERE s.name=N'pub' AND v.name LIKE N'analytic_lab_sql_[01][0-9]'
AND TRY_CONVERT(INT,RIGHT(v.name,2)) BETWEEN 1 AND 19
ORDER BY v.name,c.column_id FOR JSON PATH,INCLUDE_NULL_VALUES;
'@
$raw=& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 20 -b -y 0 -Q $query 2>&1
$code=$LASTEXITCODE
$raw|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-metadata-query.log')) -Encoding utf8
@{exit=$code;kind='READ_ONLY_SQL_METADATA'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-metadata-exit.json')) -Encoding utf8
if($code -ne 0){throw 'ANA_METADATA_QUERY'}
$data=($raw -join '')|ConvertFrom-Json
if(@($data).Count -gt 4000){throw 'ANA_METADATA_BOUND'}
$data|ConvertTo-Json -Depth 4|Set-Content -LiteralPath $path -Encoding utf8
@{columns=@($data).Count;contracts=@($data.localName|Sort-Object -Unique).Count;path=$path}|ConvertTo-Json

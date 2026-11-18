[CmdletBinding()]
param(
    [switch]$Create,
    [Parameter(Mandatory=$true)][string]$ReceiptPath
)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -eq 'Desktop'){
    # A protected native launch may inherit PowerShell 7's module search path.
    # Select installed Windows PowerShell modules for this child process only.
    $legacyModules=Join-Path $PSHOME 'Modules'
    $env:PSModulePath=$legacyModules+';'+(($env:PSModulePath -split ';' | Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
}
function Get-BootstrapHash([string]$Path){
    $hash=[Security.Cryptography.SHA256]::Create();$stream=[IO.File]::OpenRead($Path)
    try{return [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-','')}
    finally{$stream.Dispose();$hash.Dispose()}
}
$package=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\database\production\bootstrap'))
$manifest=Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($manifest.target -cne 'ETL_SISTEMA_V2' -or $manifest.product_version -cne '16.0.1000.6'){
    throw 'PRODUCTION_BOOTSTRAP_MANIFEST_TARGET_MISMATCH'
}
$expected=@('001_create_database.sql','002_configure_database.sql','003_verify_database.sql')
if(@($manifest.files).Count -ne 3){throw 'PRODUCTION_BOOTSTRAP_MANIFEST_FILE_COUNT'}
foreach($name in $expected){
    $entry=@($manifest.files | Where-Object name -CEQ $name)
    if($entry.Count -ne 1 -or (Get-BootstrapHash (Join-Path $package $name)) -cne $entry[0].sha256){
        throw 'PRODUCTION_BOOTSTRAP_FILE_CHANGED'
    }
}
if(Test-Path -LiteralPath $ReceiptPath){throw 'PRODUCTION_BOOTSTRAP_RECEIPT_ALREADY_EXISTS'}
$intent=[IO.Path]::ChangeExtension([IO.Path]::GetFullPath($ReceiptPath),'.intent.json')
if(Test-Path -LiteralPath $intent){throw 'PRODUCTION_BOOTSTRAP_INTENT_ALREADY_EXISTS'}
Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
$receipt=[ordered]@{unit='0758_DATABASE_CONTAINER';started_at=[DateTimeOffset]::Now.ToString('o');target='ETL_SISTEMA_V2';create_requested=[bool]$Create;created=$false;qualified=$false;status='NOT_STARTED';ddl_sent=$false;steps=@();application_schema_installed=$false;application_access_qualified=$false;business_dml=0;principal_or_grant_changes=0;protected_catalog_preserved=$false}
$credential=$null;$connection=$null
function Read-Json([string]$Sql){
    $command=$connection.CreateCommand();$command.CommandTimeout=10;$command.CommandText=$Sql
    try{
        $reader=$command.ExecuteReader();$builder=New-Object Text.StringBuilder
        try{while($reader.Read()){[void]$builder.Append($reader.GetString(0));if($builder.Length -gt 1MB){throw 'BOOTSTRAP_METADATA_LIMIT'}}}finally{$reader.Dispose()}
        if($builder.Length){return ($builder.ToString() | ConvertFrom-Json)}
        return @()
    }finally{$command.Dispose()}
}
$snapshotSql=@'
SELECT
 (SELECT p.name,p.type_desc,p.is_disabled,p.create_date,p.modify_date FROM sys.server_principals p
  ORDER BY p.principal_id FOR JSON PATH) principals,
 (SELECT role_principal_id,member_principal_id FROM sys.server_role_members
  ORDER BY role_principal_id,member_principal_id FOR JSON PATH) roles,
 (SELECT class,major_id,grantee_principal_id,grantor_principal_id,type,state
  FROM sys.server_permissions ORDER BY class,major_id,grantee_principal_id,type FOR JSON PATH) permissions,
 (SELECT name,collation_name,compatibility_level,is_auto_close_on,is_auto_shrink_on,
  is_trustworthy_on,is_db_chaining_on,is_read_committed_snapshot_on,recovery_model_desc
  FROM sys.databases WHERE name IN(N'ETL_SISTEMA',N'ETL_SISTEMA_V2_SHADOW',N'DASHBOARDS',N'model')
  ORDER BY name FOR JSON PATH) protected_databases FOR JSON PATH;
'@
try{
    $receipt.phase='RESOLVE_CREDENTIAL'
    $credential=Get-ProjetosSqlAdminCredential
    $receipt.phase='OPEN_CONNECTION'
    $connection=New-ProjetosSqlConnection -Database master -Credential $credential -ApplicationName 'ETL-V2-0758-Database-Container'
    $connection.Open()
    $identity=@(Read-Json "SELECT CONVERT(VARCHAR(32),SERVERPROPERTY('ProductVersion')) product_version,CONVERT(NVARCHAR(128),SERVERPROPERTY('Edition')) edition,DB_NAME() database_name,(SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id=@@SPID) encrypted,DB_ID(N'ETL_SISTEMA_V2') target_id FOR JSON PATH,INCLUDE_NULL_VALUES")[0]
    if($identity.product_version -cne '16.0.1000.6' -or $identity.edition -notlike 'Standard Edition*' -or $identity.database_name -cne 'master' -or $identity.encrypted -cne 'TRUE'){throw 'BOOTSTRAP_EXISTING_ENGINE_OR_TLS_MISMATCH'}
    $receipt.identity=$identity
    $before=Read-Json $snapshotSql
    $beforeJson=ConvertTo-Json -InputObject $before -Depth 6 -Compress
    if($Create -and $null -ne $identity.target_id){throw 'BOOTSTRAP_EXISTING_DATABASE_REQUIRES_READ_ONLY_RECONCILIATION'}
    if(-not $Create -and $null -eq $identity.target_id){throw 'BOOTSTRAP_VALIDATE_TARGET_MISSING'}
    $receipt.status='PRECONDITIONS_CHECKED'
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($ReceiptPath))) | Out-Null
    [IO.File]::WriteAllText($intent,([ordered]@{at=[DateTimeOffset]::Now.ToString('o');target='ETL_SISTEMA_V2';create=[bool]$Create;files=$manifest.files;authorization='Current user explicitly requested creation/validation, real receiving, hourly recovery and dashboard validation; no principal/grant changes';recovery='No retry, DROP, file deletion or rollback of confirmed creation. Reconcile the exact target after uncertain result.'} | ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
    if($Create){
        foreach($name in @('001_create_database.sql','002_configure_database.sql')){
            $command=$connection.CreateCommand();$command.CommandTimeout=30
            $command.CommandText=[IO.File]::ReadAllText((Join-Path $package $name))
            $receipt.ddl_sent=$true
            try{[void]$command.ExecuteNonQuery();$receipt.steps+=@([ordered]@{file=$name;acknowledged=$true});if($name -eq '001_create_database.sql'){$receipt.created=$true}}
            finally{$command.Dispose()}
        }
    }
    $connection.ChangeDatabase('ETL_SISTEMA_V2')
    $receipt.validation=@(Read-Json ([IO.File]::ReadAllText((Join-Path $package '003_verify_database.sql'))))
    if($receipt.validation.Count -ne 1){throw 'BOOTSTRAP_POSTCONDITION_RECEIPT_MISSING'}
    $connection.ChangeDatabase('master')
    $after=Read-Json $snapshotSql
    $afterJson=ConvertTo-Json -InputObject $after -Depth 6 -Compress
    $receipt.protected_catalog_preserved=$beforeJson -ceq $afterJson
    if(-not $receipt.protected_catalog_preserved){throw 'BOOTSTRAP_PROTECTED_CATALOG_DRIFT'}
    $receipt.qualified=$true;$receipt.status='CONTAINER_VALIDATED_SCHEMA_ACCESS_DATA_PENDING'
}catch{
    $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
    $receipt.status=if($receipt.ddl_sent){'NOT_QUALIFIED_RECONCILE_EXACT_TARGET'}else{'REFUSED_BEFORE_DDL'}
    $receipt.failure=[ordered]@{type=$cause.GetType().Name;number=$cause.Number;command=$(if($cause.CommandName -match '^[A-Za-z][A-Za-z0-9_-]*$'){$cause.CommandName}else{$null});code=$(if($cause.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$cause.Message}else{'DETAILS_WITHHELD'})}
}finally{
    if($connection){$connection.Dispose()};if($credential){$credential.Password.Dispose()}
    $receipt.completed_at=[DateTimeOffset]::Now.ToString('o')
    [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptPath),($receipt | ConvertTo-Json -Depth 12),(New-Object Text.UTF8Encoding($false)))
}
Write-Output ($receipt.status)
if(-not $receipt.qualified){exit 1}

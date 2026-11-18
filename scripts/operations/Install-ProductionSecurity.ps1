[CmdletBinding()]
param(
    [switch]$Install,
    [Parameter(Mandatory=$true)][string]$ReceiptPath
)
$ErrorActionPreference='Stop'
if($PSVersionTable.PSEdition -eq 'Desktop'){
    $env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';' | Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
}
function Get-SecurityHash([string]$Path){
    $algorithm=[Security.Cryptography.SHA256]::Create();$stream=[IO.File]::OpenRead($Path)
    try{return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-','')}
    finally{$stream.Dispose();$algorithm.Dispose()}
}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifest=Get-Content -LiteralPath (Join-Path $root 'database\production\security\security-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($manifest.package -cne '0758-security-v1' -or $manifest.target -cne 'ETL_SISTEMA_V2' -or $manifest.new_login -cne 'etl_v2_runtime_prod' -or $manifest.existing_dashboard_login -cne 'usuario_etl'){throw 'SECURITY_MANIFEST_SCOPE_MISMATCH'}
$expected=@('V001__create_v2_schema_foundation.sql','V002__create_v2_database_roles.sql')
if(@($manifest.foundation_sources).Count -ne 2){throw 'SECURITY_MANIFEST_FILE_COUNT'}
foreach($name in $expected){
    $entry=@($manifest.foundation_sources | Where-Object name -CEQ $name)
    if($entry.Count -ne 1 -or (Get-SecurityHash (Join-Path $root ('database\migrations\'+$name))) -cne $entry[0].sha256){throw 'SECURITY_FOUNDATION_SOURCE_CHANGED'}
}
$ReceiptPath=[IO.Path]::GetFullPath($ReceiptPath)
if(Test-Path -LiteralPath $ReceiptPath){throw 'SECURITY_RECEIPT_ALREADY_EXISTS'}
$receipt=[ordered]@{unit='0758_SECURITY';started_at=[DateTimeOffset]::Now.ToString('o');target='ETL_SISTEMA_V2';install_requested=[bool]$Install;login_ddl_sent=$false;database_committed=$false;qualified=$false;status='NOT_STARTED';foundation_sources=$manifest.foundation_sources;old_catalog_preserved=$false;acl_changes=0;passwords_changed=0;flyway_history_created=$false}
$connection=$null;$admin=$null;$runtime=$null;$transaction=$null
function Invoke-SecuritySql([string]$Sql){
    $command=$connection.CreateCommand();$command.CommandTimeout=20;$command.CommandText=$Sql
    if($transaction){$command.Transaction=$transaction}
    try{[void]$command.ExecuteNonQuery()}finally{$command.Dispose()}
}
function Read-SecurityJson([string]$Sql){
    $command=$connection.CreateCommand();$command.CommandTimeout=10;$command.CommandText=$Sql
    if($transaction){$command.Transaction=$transaction}
    try{
        $reader=$command.ExecuteReader();$buffer=New-Object Text.StringBuilder
        try{while($reader.Read()){[void]$buffer.Append($reader.GetString(0));if($buffer.Length -gt 2MB){throw 'SECURITY_METADATA_LIMIT'}}}finally{$reader.Dispose()}
        if($buffer.Length){return ($buffer.ToString()|ConvertFrom-Json)}
        return @()
    }finally{$command.Dispose()}
}
$catalogSql=@'
SELECT
 (SELECT name,type_desc,is_disabled,create_date,modify_date FROM sys.server_principals
  WHERE name<>N'etl_v2_runtime_prod' ORDER BY principal_id FOR JSON PATH) principals,
 (SELECT role_principal_id,member_principal_id FROM sys.server_role_members
  WHERE member_principal_id<>ISNULL(SUSER_ID(N'etl_v2_runtime_prod'),-1)
  ORDER BY role_principal_id,member_principal_id FOR JSON PATH) roles,
 (SELECT class,major_id,grantee_principal_id,grantor_principal_id,type,state FROM sys.server_permissions
  WHERE grantee_principal_id<>ISNULL(SUSER_ID(N'etl_v2_runtime_prod'),-1)
  ORDER BY class,major_id,grantee_principal_id,type FOR JSON PATH) permissions,
 (SELECT name,owner_sid,collation_name,compatibility_level,is_auto_close_on,is_auto_shrink_on,
  is_trustworthy_on,is_db_chaining_on,is_read_committed_snapshot_on,recovery_model_desc
  FROM sys.databases WHERE name IN(N'ETL_SISTEMA',N'ETL_SISTEMA_V2_SHADOW',N'DASHBOARDS',N'model')
  ORDER BY name FOR JSON PATH) old_databases FOR JSON PATH;
'@
try{
    Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
    $admin=Get-ProjetosSqlAdminCredential
    $connection=New-ProjetosSqlConnection -Database master -Credential $admin -ApplicationName 'ETL-V2-0758-Approved-Security'
    $connection.Open()
    $receipt.phase='PREFLIGHT'
    Invoke-SecuritySql @'
IF DB_NAME()<>N'master' OR CONVERT(VARCHAR(32),SERVERPROPERTY('ProductVersion'))<>'16.0.1000.6'
    THROW 51800,N'SECURITY_ENGINE_OR_CONTEXT_MISMATCH',1;
IF DB_ID(N'ETL_SISTEMA_V2') IS NULL OR SUSER_ID(N'usuario_etl') IS NULL
    THROW 51801,N'SECURITY_REQUIRED_IDENTITY_MISSING',1;
'@
    $before=ConvertTo-Json -InputObject (Read-SecurityJson $catalogSql) -Depth 6 -Compress
    $connection.ChangeDatabase('ETL_SISTEMA_V2')
    if(-not $Install){
        $receipt.inventory=Read-SecurityJson "SELECT name,type_desc FROM sys.database_principals WHERE name IN(N'v2_schema_owner',N'v2_runtime',N'etl_v2_runtime_prod',N'usuario_etl') FOR JSON PATH"
        $receipt.status='READ_ONLY_INVENTORY';$receipt.qualified=$true
    }else{
        Invoke-SecuritySql @'
IF DB_NAME()<>N'ETL_SISTEMA_V2' OR SUSER_ID(N'etl_v2_runtime_prod') IS NOT NULL
    THROW 51802,N'SECURITY_EXISTING_LOGIN_REQUIRES_RECONCILIATION',1;
IF EXISTS(SELECT 1 FROM sys.objects WHERE is_ms_shipped=0)
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE name IN(N'ctl',N'stg',N'core',N'ref',N'mart',N'pub',N'recon'))
    THROW 51803,N'SECURITY_TARGET_MUST_BE_EMPTY_CONTAINER',1;
'@
        $privateDirectory=Join-Path $env:LOCALAPPDATA 'Rodogarcia\SqlServer'
        $credentialFile=Join-Path $privateDirectory 'etl-v2-runtime-prod.clixml'
        if(-not (Test-Path -LiteralPath $privateDirectory) -or (Test-Path -LiteralPath $credentialFile)){throw 'SECURITY_CREDENTIAL_DESTINATION_REQUIRES_RECONCILIATION'}
        $currentSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        $acl=Get-Acl -LiteralPath $privateDirectory
        foreach($rule in $acl.Access){
            $sid=$rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            if($rule.AccessControlType -ne 'Allow' -or $sid -notin @($currentSid,'S-1-5-18') -or ($rule.InheritanceFlags -band [Security.AccessControl.InheritanceFlags]::ObjectInherit) -eq 0){throw 'SECURITY_EXISTING_PRIVATE_ACL_NOT_QUALIFIED'}
        }
        if(@($acl.Access).Count -ne 2){throw 'SECURITY_EXISTING_PRIVATE_ACL_NOT_QUALIFIED'}
        $bytes=New-Object byte[] 48;$random=[Security.Cryptography.RandomNumberGenerator]::Create()
        try{$random.GetBytes($bytes)}finally{$random.Dispose()}
        $plain='Aa7!'+[Convert]::ToBase64String($bytes);[Array]::Clear($bytes,0,$bytes.Length)
        $secure=ConvertTo-SecureString -String $plain -AsPlainText -Force
        $runtime=New-Object Management.Automation.PSCredential('etl_v2_runtime_prod',$secure)
        $temporary=Join-Path $privateDirectory ('etl-v2-runtime-prod.'+[Guid]::NewGuid().ToString('N')+'.tmp')
        $runtime | Export-Clixml -LiteralPath $temporary -Encoding UTF8
        [IO.File]::Move($temporary,$credentialFile)
        $receipt.credential_persisted_dpapi_current_user=$true
        $connection.ChangeDatabase('master');$receipt.phase='CREATE_DEDICATED_LOGIN';$receipt.login_ddl_sent=$true
        try{Invoke-SecuritySql ("CREATE LOGIN [etl_v2_runtime_prod] WITH PASSWORD=N'"+$plain.Replace("'","''")+"',DEFAULT_DATABASE=[ETL_SISTEMA_V2],CHECK_POLICY=ON,CHECK_EXPIRATION=OFF;")}
        finally{$plain=$null}
        $connection.ChangeDatabase('ETL_SISTEMA_V2');$receipt.phase='FOUNDATION_TRANSACTION'
        $transaction=$connection.BeginTransaction()
        foreach($name in $expected){
            $sql=[IO.File]::ReadAllText((Join-Path $root ('database\migrations\'+$name)))
            foreach($batch in [regex]::Split($sql,'(?im)^\s*GO\s*\r?$')){if($batch.Trim()){Invoke-SecuritySql $batch}}
        }
        Invoke-SecuritySql @'
CREATE USER [etl_v2_runtime_prod] FOR LOGIN [etl_v2_runtime_prod];
GRANT CONNECT TO [etl_v2_runtime_prod];
ALTER ROLE [v2_runtime] ADD MEMBER [etl_v2_runtime_prod];
CREATE USER [usuario_etl] FOR LOGIN [usuario_etl];
GRANT CONNECT TO [usuario_etl];
GRANT SELECT ON SCHEMA::pub TO [usuario_etl];
CREATE TABLE ctl.production_installation_step(
 step_id VARCHAR(64) NOT NULL PRIMARY KEY,
 source_name NVARCHAR(260) NOT NULL,
 source_sha256 CHAR(64) NOT NULL,
 installed_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
 installed_by SYSNAME NOT NULL DEFAULT ORIGINAL_LOGIN()
);
'@
        foreach($entry in $manifest.foundation_sources){
            Invoke-SecuritySql ("INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES('0758-"+$entry.name.Substring(0,4)+"',N'"+$entry.name+"','"+$entry.sha256+"');")
        }
        Invoke-SecuritySql @'
IF (SELECT COUNT(*) FROM sys.schemas WHERE name IN(N'ctl',N'stg',N'core',N'ref',N'mart',N'pub',N'recon') AND USER_NAME(principal_id)=N'v2_schema_owner')<>7
    THROW 51804,N'SECURITY_SCHEMA_OWNER_MISMATCH',1;
IF (SELECT COUNT(*) FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'etl_v2_runtime_prod'))<>1
 OR NOT EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'etl_v2_runtime_prod') AND role_principal_id=USER_ID(N'v2_runtime'))
    THROW 51805,N'SECURITY_RUNTIME_MEMBERSHIP_MISMATCH',1;
IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'usuario_etl'))
    THROW 51806,N'SECURITY_DASHBOARD_UNEXPECTED_ROLE',1;
'@
        $transaction.Commit();$transaction.Dispose();$transaction=$null;$receipt.database_committed=$true
        $connection.ChangeDatabase('master')
        $after=ConvertTo-Json -InputObject (Read-SecurityJson $catalogSql) -Depth 6 -Compress
        $receipt.old_catalog_preserved=$before -ceq $after
        if(-not $receipt.old_catalog_preserved){throw 'SECURITY_PROTECTED_CATALOG_DRIFT'}
        Invoke-SecuritySql @'
IF EXISTS(SELECT 1 FROM sys.server_role_members WHERE member_principal_id=SUSER_ID(N'etl_v2_runtime_prod'))
    THROW 51807,N'SECURITY_RUNTIME_SERVER_ROLE_FOUND',1;
'@
        $connection.Dispose();$connection=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $runtime -ApplicationName 'ETL-V2-0758-Runtime-Identity-Qualification'
        $connection.Open();$receipt.phase='FRESH_RUNTIME_IDENTITY'
        $receipt.runtime_identity=@(Read-SecurityJson @'
SELECT DB_NAME() database_name,ORIGINAL_LOGIN() login_name,USER_NAME() database_user,
 IS_MEMBER(N'v2_runtime') runtime_member,IS_MEMBER(N'db_owner') db_owner,
 IS_SRVROLEMEMBER(N'sysadmin') sysadmin,IS_SRVROLEMEMBER(N'dbcreator') dbcreator,
 HAS_PERMS_BY_NAME(DB_NAME(),N'DATABASE',N'CREATE TABLE') create_table,
 HAS_PERMS_BY_NAME(N'core',N'SCHEMA',N'INSERT') core_insert,
 HAS_DBACCESS(N'ETL_SISTEMA') v1_access,HAS_DBACCESS(N'DASHBOARDS') dashboard_access
 FOR JSON PATH;
'@)[0]
        $id=$receipt.runtime_identity
        if($id.login_name -cne 'etl_v2_runtime_prod' -or $id.database_name -cne 'ETL_SISTEMA_V2' -or $id.runtime_member -ne 1 -or $id.db_owner -ne 0 -or $id.sysadmin -ne 0 -or $id.dbcreator -ne 0 -or $id.create_table -ne 0 -or $id.core_insert -ne 0 -or $id.v1_access -ne 0 -or $id.dashboard_access -ne 0){throw 'SECURITY_RUNTIME_EFFECTIVE_PRIVILEGE_MISMATCH'}
        $receipt.qualified=$true;$receipt.status='SECURITY_INSTALLED_RUNTIME_IDENTITY_QUALIFIED_DASHBOARD_CHECK_PENDING'
    }
}catch{
    if($transaction){try{$transaction.Rollback()}catch{};$transaction.Dispose();$transaction=$null}
    $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
    $receipt.status=if($receipt.login_ddl_sent){'RECONCILE_EXACT_TARGET_NO_AUTOMATIC_RETRY'}else{'REFUSED_BEFORE_LOGIN_DDL'}
    $receipt.failure=[ordered]@{type=$cause.GetType().Name;number=$cause.Number;code=$(if($cause.Message -cmatch '^[A-Z][A-Z0-9_]+$'){$cause.Message}else{'DETAILS_WITHHELD'})}
}finally{
    if($connection){$connection.Dispose()};if($admin){$admin.Password.Dispose()};if($runtime){$runtime.Password.Dispose()}
    $receipt.completed_at=[DateTimeOffset]::Now.ToString('o')
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($ReceiptPath))|Out-Null
    [IO.File]::WriteAllText($ReceiptPath,($receipt|ConvertTo-Json -Depth 10),(New-Object Text.UTF8Encoding($false)))
}
Write-Output $receipt.status
if(-not $receipt.qualified){exit 1}

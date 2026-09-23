param(
    [Parameter(Mandatory)][string]$Inputs,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = [IO.Path]::GetFullPath($OutputDirectory)
$prefix = [IO.Path]::GetFullPath((Join-Path $root 'target/bloco53')) + [IO.Path]::DirectorySeparatorChar
if (-not $output.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'REVIEW_OUTPUT_MUST_STAY_IN_BLOCK53_TARGET' }
$bytes = [IO.File]::ReadAllBytes([IO.Path]::GetFullPath($Inputs))
if ($bytes.Length -gt 16384) { throw 'PROVISIONING_INPUT_LIMIT' }
$text = [Text.UTF8Encoding]::new($false,$true).GetString($bytes)
$document = [System.Text.Json.JsonDocument]::Parse($text)
function Require-UniqueKeys($node) {
    if ($node.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($property in $node.EnumerateObject()) {
            if (-not $seen.Add($property.Name)) { throw 'DUPLICATE_PROVISIONING_PROPERTY' }
            Require-UniqueKeys $property.Value
        }
    } elseif ($node.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach ($item in $node.EnumerateArray()) { Require-UniqueKeys $item }
    }
}
try { Require-UniqueKeys $document.RootElement } finally { $document.Dispose() }
$values = $text | ConvertFrom-Json -AsHashtable -DateKind String
$required = @('authorityServer','authorityId','policyFingerprint','serviceLogin','operatorLogin','validFromUtc','validUntilUtc','scopes','reviewReference')
if (@(Compare-Object @($values.Keys) $required -CaseSensitive).Count -ne 0) { throw 'PROVISIONING_INPUT_SCHEMA' }
foreach ($key in $required | Where-Object { $_ -ne 'scopes' }) {
    if ($values[$key] -isnot [string] -or [string]::IsNullOrWhiteSpace($values[$key])) { throw 'REAL_PROVISIONING_INPUT_REQUIRED' }
}
if ($values.authorityServer -cnotmatch '^[A-Za-z0-9][A-Za-z0-9.-]{0,127}$' -or $values.policyFingerprint -cnotmatch '^[a-f0-9]{64}$') { throw 'AUTHORITY_PIN_INVALID' }
$contract = Get-Content (Join-Path $root 'database/manifest/runtime-windows-authority-temporal.json') -Raw | ConvertFrom-Json
if ($values.policyFingerprint -cne $contract.compiledPolicySha256) { throw 'COMPILED_POLICY_PIN_MISMATCH' }
$authority = [Guid]::ParseExact($values.authorityId,'D')
foreach ($key in @('serviceLogin','operatorLogin')) {
    if ($values[$key] -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,62}\\[A-Za-z0-9][A-Za-z0-9._$-]{0,62}$') { throw 'EXPLICIT_WINDOWS_LOGIN_REQUIRED' }
}
if ($values.serviceLogin -ieq $values.operatorLogin) { throw 'SEPARATE_SERVICE_OPERATOR_REQUIRED' }
if ($values.reviewReference -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$') { throw 'REVIEW_REFERENCE_INVALID' }
$from = [DateTimeOffset]::ParseExact($values.validFromUtc,'yyyy-MM-ddTHH:mm:ss.fffZ',[Globalization.CultureInfo]::InvariantCulture)
$until = [DateTimeOffset]::ParseExact($values.validUntilUtc,'yyyy-MM-ddTHH:mm:ss.fffZ',[Globalization.CultureInfo]::InvariantCulture)
if ($until -le $from -or ($until-$from).TotalDays -gt 31) { throw 'EXPLICIT_MAPPING_VALIDITY_LIMIT' }
if ($values.scopes -isnot [array] -or $values.scopes.Count -lt 1 -or $values.scopes.Count -gt 16) { throw 'EXPLICIT_BOUNDED_SCOPES_REQUIRED' }
foreach ($scope in $values.scopes) {
    if (@(Compare-Object @($scope.Keys) @('environment','source','tenant','workload','mode') -CaseSensitive).Count -ne 0) { throw 'PROVISIONING_SCOPE_SCHEMA' }
    foreach ($key in @('environment','source','tenant','workload')) {
        if ($scope[$key] -isnot [string] -or $scope[$key] -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$') { throw 'SCOPE_INVALID' }
    }
    if ($scope.environment.Length -gt 32 -or $scope.workload -cnotin @('coletas','fretes') -or $scope.mode -cnotin @('BOOTSTRAP','BACKFILL','INCREMENTAL','REPLAY')) { throw 'FIRST_WAVE_SCOPE_REQUIRED' }
}
function Literal([string]$value) { return "N'"+$value.Replace("'","''")+"'" }
function Identifier([string]$value) { return '['+$value.Replace(']',']]')+']' }
$procedures = @(
    'ctl.usp_runtime_authorization','ctl.usp_runtime_status','ctl.usp_runtime_recovery',
    'ctl.usp_control_plane_register_source','ctl.usp_control_plane_start_cycle','ctl.usp_control_plane_start_execution',
    'ctl.usp_control_plane_heartbeat_lease','ctl.usp_control_plane_record_page','ctl.usp_control_plane_record_counts',
    'ctl.usp_control_plane_transition_execution','ctl.usp_control_plane_register_incremental_frontier',
    'stg.usp_stage_coleta_record','stg.usp_stage_frete_record','core.usp_prepare_staged_execution',
    'core.usp_prepare_frete_candidate_set','core.usp_apply_reconcile_publish_coletas','core.usp_apply_reconcile_publish_fretes',
    'recon.usp_evaluate_execution_data_quality','ctl.usp_runtime_temporal_plan','ctl.usp_runtime_temporal_gaps'
)
$header = @"
-- REVIEW ONLY. Apply requires specific approval of this generated package.
-- Target: existing localhost/ETL_SISTEMA_V2_SHADOW. Review: $($values.reviewReference)
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR CONVERT(NVARCHAR(128),SERVERPROPERTY('ServerName')) COLLATE Latin1_General_100_BIN2<>$(Literal $values.authorityServer)
 THROW 52460,N'PROVISIONING_TARGET_MISMATCH',1;
IF CONNECTIONPROPERTY('auth_scheme') NOT IN(N'NTLM',N'KERBEROS') THROW 52460,N'WINDOWS_REQUIRED',1;
BEGIN TRANSACTION;
"@
$apply = [Text.StringBuilder]::new($header+"`n")
$recovery = [Text.StringBuilder]::new($header+"`n")
$verify = [Text.StringBuilder]::new("-- Execute with a NEW integrated connection under each actual account. No EXECUTE AS fixture.`nSET NOCOUNT ON;`n")
[void]$apply.AppendLine("IF EXISTS(SELECT 1 FROM ctl.runtime_authority_configuration) THROW 52460,N'AUTHORITY_ALREADY_CONFIGURED_REVIEW_EXISTING_STATE',1;")
[void]$apply.AppendLine("INSERT ctl.runtime_authority_configuration VALUES(1,'$authority',$(Literal $values.authorityServer),N'ETL_SISTEMA_V2_SHADOW',$(Literal $values.policyFingerprint),30,1);")
foreach ($kind in @('SERVICE','OPERATOR')) {
    $login = if ($kind -eq 'SERVICE') { $values.serviceLogin } else { $values.operatorLogin }
    $sqlName = Identifier $login
    $sqlLiteral = Literal $login
    $execute = if ($kind -eq 'SERVICE') { 1 } else { 0 }
    $grants = if ($kind -eq 'SERVICE') { $procedures } else { @('ctl.usp_runtime_authorization','ctl.usp_runtime_status') }
    [void]$apply.AppendLine("IF NOT EXISTS(SELECT 1 FROM sys.server_principals WHERE name=$sqlLiteral AND type='U' AND is_disabled=0) OR ISNULL(IS_SRVROLEMEMBER(N'sysadmin',$sqlLiteral),1)<>0 THROW 52460,N'EXISTING_RESTRICTED_WINDOWS_LOGIN_REQUIRED',1;")
    [void]$apply.AppendLine("IF DATABASE_PRINCIPAL_ID($sqlLiteral) IS NOT NULL THROW 52460,N'EXISTING_DATABASE_USER_REQUIRES_SEPARATE_DIFF_REVIEW',1;")
    [void]$apply.AppendLine("CREATE USER $sqlName FOR LOGIN $sqlName;")
    [void]$apply.AppendLine("INSERT ctl.runtime_identity_mapping VALUES(SUSER_SID($sqlLiteral),NEWID(),'$kind',1,'$($values.validFromUtc)','$($values.validUntilUtc)',0,1,$execute,0,0);")
    foreach ($scope in $values.scopes) {
        [void]$apply.AppendLine("INSERT ctl.runtime_identity_scope(original_sid,environment_name,source_instance,tenant_scope,workload,mode,scope_version,policy_fingerprint,revoked) VALUES(SUSER_SID($sqlLiteral),$(Literal $scope.environment),$(Literal $scope.source),$(Literal $scope.tenant),$(Literal $scope.workload),$(Literal $scope.mode),1,$(Literal $values.policyFingerprint),0);")
    }
    foreach ($procedure in $grants) {
        [void]$apply.AppendLine("GRANT EXECUTE ON OBJECT::$procedure TO $sqlName;")
        [void]$recovery.AppendLine("REVOKE EXECUTE ON OBJECT::$procedure FROM $sqlName;")
    }
    [void]$recovery.AppendLine("UPDATE ctl.runtime_identity_mapping SET revoked=1,mapping_version=mapping_version+1 WHERE original_sid=SUSER_SID($sqlLiteral);")
    [void]$recovery.AppendLine("UPDATE ctl.runtime_identity_scope SET revoked=1,scope_version=scope_version+1 WHERE original_sid=SUSER_SID($sqlLiteral);")
}
[void]$apply.AppendLine('IF XACT_STATE()<>1 OR @@TRANCOUNT<>1 THROW 52460,N''PROVISIONING_TRANSACTION_REQUIRED'',1; COMMIT TRANSACTION;')
[void]$recovery.AppendLine('UPDATE ctl.runtime_authority_configuration SET enabled=0 WHERE singleton=1; COMMIT TRANSACTION;')
[void]$verify.AppendLine(@'
SELECT CASE WHEN DB_NAME()=N'ETL_SISTEMA_V2_SHADOW' AND CONNECTIONPROPERTY('auth_scheme') IN(N'NTLM',N'KERBEROS')
 AND ORIGINAL_LOGIN()=SUSER_SNAME() AND SUSER_SID(ORIGINAL_LOGIN())=SUSER_SID()
 AND IS_SRVROLEMEMBER(N'sysadmin')=0 AND IS_MEMBER(N'db_owner')=0
 AND HAS_PERMS_BY_NAME(DB_NAME(),N'DATABASE',N'CONTROL')=0
 AND HAS_PERMS_BY_NAME(NULL,NULL,N'CONTROL SERVER')=0
 AND HAS_PERMS_BY_NAME(N'ctl.runtime_identity_mapping',N'OBJECT',N'SELECT')=0
 AND HAS_PERMS_BY_NAME(N'ctl.runtime_identity_mapping',N'OBJECT',N'INSERT')=0
 AND HAS_PERMS_BY_NAME(DB_NAME(),N'DATABASE',N'ALTER ANY ROLE')=0
 THEN N'PRINCIPAL_PREFLIGHT_PASS' ELSE N'PRINCIPAL_PREFLIGHT_DENIED' END;
-- Complete proof: official JAR status/run under each real account, then revocation/expiry/ack negatives.
-- This aggregate preflight does not replace those behavioral assertions.
'@)
if (Test-Path -LiteralPath $output) { throw 'REVIEW_DIRECTORY_MUST_BE_NEW' }
[IO.Directory]::CreateDirectory($output) | Out-Null
$encoding = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $output '01-apply-reviewed.sql'),$apply.ToString(),$encoding)
[IO.File]::WriteAllText((Join-Path $output '02-verify-new-session.sql'),$verify.ToString(),$encoding)
[IO.File]::WriteAllText((Join-Path $output '03-revoke-preserve-audit.sql'),$recovery.ToString(),$encoding)
[IO.File]::WriteAllText((Join-Path $output 'runtime-authority.properties'),"server=$($values.authorityServer)`ndatabase=ETL_SISTEMA_V2_SHADOW`nauthorityId=$authority`npolicyFingerprint=$($values.policyFingerprint)`n",$encoding)
Write-Output 'REVIEW_PACKAGE_WRITTEN; SQL_NOT_EXECUTED; SERVICE_EXECUTOR_OBSERVER; OPERATOR_OBSERVER_ONLY; REPLAY_FORCE_DISABLED'

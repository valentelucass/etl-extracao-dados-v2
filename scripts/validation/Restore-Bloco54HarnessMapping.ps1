#Requires -Version 7.0
param([Parameter(Mandatory)][string]$ExpectedContractSha256,[switch]$ObservabilityProfile,[switch]$SixthCampaign)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root $(if($SixthCampaign){'target/bloco54/resume-sixth'}else{'target/bloco54'})
$contractFile=Join-Path $evidence 'OPS02-restart-contract.json'
if($ExpectedContractSha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $contractFile).Hash.ToLowerInvariant() -cne $ExpectedContractSha256){throw 'RESTART_CONTRACT_HASH_REQUIRED'}
$contract=Get-Content -LiteralPath $contractFile -Raw | ConvertFrom-Json -DateKind String
$originalUntilUtc=[datetime]::ParseExact($contract.originalUntil,"yyyy-MM-dd'T'HH:mm:ss.fff",[Globalization.CultureInfo]::InvariantCulture,([Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal))
if($contract.state -cne 'PRESERVED_REVOKED' -or $contract.expected -cnotmatch '^[a-f0-9]{64}$' -or $originalUntilUtc -le [datetime]::UtcNow){throw 'RESTART_CONTRACT_ORIGINAL_VALIDITY_REQUIRED'}
if((Get-FileHash -LiteralPath (Join-Path $evidence 'OPS02-planned-recovery.sql')).Hash.ToLowerInvariant() -cne $contract.recoverySha256){throw 'RESTART_COMPENSATION_BYTES_CHANGED'}
$until=$originalUntilUtc.ToString('yyyy-MM-ddTHH:mm:ss.fff',[Globalization.CultureInfo]::InvariantCulture)
$state=@'
CONCAT((SELECT principal_kind,mapping_version,valid_from_utc,valid_until_utc,revoked,observer,executor,replay,force_run,audit_reference
 FROM ctl.runtime_identity_mapping ORDER BY principal_kind FOR JSON PATH),N'|',
 (SELECT s.scope_id,m.principal_kind,s.environment_name,s.source_instance,s.tenant_scope,s.workload,s.mode,s.scope_version,s.policy_fingerprint,s.revoked
 FROM ctl.runtime_identity_scope s JOIN ctl.runtime_identity_mapping m ON m.original_sid=s.original_sid ORDER BY s.scope_id FOR JSON PATH))
'@
$sql=@'
:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52514,N'LOCAL_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
DECLARE @state NVARCHAR(MAX)={STATE};
IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@state),2))<>'{EXPECTED}' THROW 52514,N'RESTART_STATE_CHANGED',1;
UPDATE ctl.runtime_identity_mapping SET revoked=0,mapping_version=mapping_version+1
 WHERE principal_kind='SERVICE' AND revoked=1 AND valid_until_utc=CONVERT(DATETIME2(3),'{UNTIL}')
 AND executor=1 AND observer=1 AND replay=0 AND force_run=0;
IF @@ROWCOUNT<>1 THROW 52514,N'RESTART_EXACT_ROW_REQUIRED',1;
GO
{VALIDATOR}
GO
COMMIT TRANSACTION;
PRINT N'OPS02_RESTART_COMPENSATION_CONFIRMED_VERSIONS_INCREASED';
'@
$validator=if($ObservabilityProfile){'database/proposals/bloco54-observability/verify.sql'}else{'database/validation/053_validate_local_runtime_provisioning.sql'}
$sql=$sql.Replace('{STATE}',$state).Replace('{EXPECTED}',$contract.expected).Replace('{UNTIL}',$until).Replace('{VALIDATOR}',(Get-Content (Join-Path $root $validator) -Raw))
$file=Join-Path $evidence 'OPS02-restart-apply-reviewed.sql'
[IO.File]::WriteAllText($file,$sql,[Text.UTF8Encoding]::new($false))
Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
Add-Bloco54Reservation $(if($SixthCampaign){'SIXTH_OPS02_RESTART_RECOVERY'}else{'OPS02_RESTART_RECOVERY'}) 'EXACT_MAPPING_COMPENSATION' | Out-Null
& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -i $file *> (Join-Path $evidence 'OPS02-restart-result.log')
if($LASTEXITCODE -ne 0){throw 'RESTART_UNCONFIRMED_READBACK_REQUIRED'}
'OPS02_RESTART_RECOVERED_NO_VALIDITY_EXTENSION'

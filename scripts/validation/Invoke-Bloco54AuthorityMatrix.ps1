param([Parameter(Mandatory)][string]$ExpectedHarnessSha256,[switch]$UseActiveCampaign,[switch]$ObservabilityProfile,[switch]$RemainingOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root $(if($RemainingOnly){'target/bloco54/resume-sixth'}else{'target/bloco54'})
$encoding=[Text.UTF8Encoding]::new($false,$true)
$revision='C:\ProgramData\EslEtlV2\app-bloco54\d382d4d39164cdb6'
$results=[Collections.Generic.List[object]]::new()
$credential=$null;$active=$false;$mappingDirty=$false
$stateExpression=@'
CONCAT((SELECT principal_kind,mapping_version,valid_from_utc,valid_until_utc,revoked,observer,executor,replay,force_run,audit_reference
 FROM ctl.runtime_identity_mapping ORDER BY principal_kind FOR JSON PATH),N'|',
 (SELECT s.scope_id,m.principal_kind,s.environment_name,s.source_instance,s.tenant_scope,s.workload,s.mode,s.scope_version,s.policy_fingerprint,s.revoked
 FROM ctl.runtime_identity_scope s JOIN ctl.runtime_identity_mapping m ON m.original_sid=s.original_sid ORDER BY s.scope_id FOR JSON PATH))
'@
function Invoke-LocalSql([string]$Name,[string]$Sql) {
    $file=Join-Path $evidence ($Name+'.sql')
    [IO.File]::WriteAllText($file,("SET NOCOUNT ON; SET XACT_ABORT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52505,N'LOCAL_TARGET_REQUIRED',1;`n"+$Sql),$encoding)
    $out=@(& sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -y 0 -w 65535 -i $file 2>&1)
    $code=$LASTEXITCODE
    [IO.File]::WriteAllLines((Join-Path $evidence ($Name+'.sql.log')),[string[]]$out,$encoding)
    if($code -ne 0){throw ('SQL_FAILED_'+$Name)}
    return ,@($out | ForEach-Object {[string]$_})
}
function Read-State([string]$Name) {
    $out=Invoke-LocalSql $Name ("DECLARE @material NVARCHAR(MAX)=$stateExpression; SELECT @material; SELECT LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@material),2));")
    $hash=@($out | Where-Object { $_.Trim() -cmatch '^[a-f0-9]{64}$' })
    if($hash.Count -ne 1){throw 'EXACT_STATE_HASH_REQUIRED'}
    return $hash[0].Trim()
}
function Start-Probe([string]$Id,[string]$Scenario,[guid]$Invocation,[string]$Field='unused',[string]$Barrier='unused') {
    Add-Bloco54Reservation $Id 'REAL_SQL_TEST_CLASSPATH' | Out-Null
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $start.UserName='etl_v2_exec';$start.Domain=$env:COMPUTERNAME;$start.Password=$credential
    $start.LoadUserProfile=$true;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
    $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$revision
    foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
    $classpath=(Join-Path $revision 'etl-dataexport-v2.jar')+';'+(Join-Path $revision 'lib/*')+';'+$harnessRoot
    foreach($argument in @(('-Djava.library.path='+(Join-Path $revision 'native')),'-cp',$classpath,'br.com.esl.etl.v2.plataforma.autorizacao.Bloco54AuthorityHarness',$Scenario,$Invocation.ToString(),$Field,$Barrier)){$start.ArgumentList.Add($argument)}
    return [pscustomobject]@{id=$Id;invocation=$Invocation.ToString();task=[Bloco54Child]::Run($start)}
}
function Complete-Probe($Probe,[int]$Expected,[int]$Decisions,[int]$Consumptions) {
    $result=$Probe.task.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $evidence ($Probe.id+'.log')),$result.Output,$encoding)
    $inv=[guid]::Parse($Probe.invocation).ToString()
    $sql="DECLARE @inv UNIQUEIDENTIFIER='$inv'; SELECT (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id=@inv) AS decisions,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id=@inv) AS consumptions FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
    $out=Invoke-LocalSql ($Probe.id+'-observe') $sql
    $counts=($out | Where-Object {$_.Trim().StartsWith('{')}) -join '' | ConvertFrom-Json
    $passed=($result.Code -eq $Expected -and -not $result.Limited -and $counts.decisions -eq $Decisions -and $counts.consumptions -eq $Consumptions)
    $results.Add([pscustomobject]@{id=$Probe.id;invocation=$inv;layer='REAL_WINDOWS_SQL_TEST_CLASSPATH';expectedExit=$Expected;observedExit=$result.Code;decisions=$counts.decisions;consumptions=$counts.consumptions;passed=$passed})
    if(-not $passed){throw ('CASE_FAILED_'+$Probe.id)}
}
try {
    if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'ADMINISTRATIVE_CONTEXT_REQUIRED'}
    $manifest=Get-Content (Join-Path $revision 'manifest.json') -Raw|ConvertFrom-Json -DateKind String
    foreach($file in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $revision $file.path)).Hash.ToLowerInvariant() -cne $file.sha256){throw 'PROTECTED_ARTIFACT_HASH_MISMATCH'}}
    $classRelative='br/com/esl/etl/v2/plataforma/autorizacao/Bloco54AuthorityHarness.class'
    $class=Join-Path $root ($(if($RemainingOnly){'target/bloco54-build-v4/test-classes/'}else{'target/bloco54-build/test-classes/'})+$classRelative)
    if((Get-FileHash -LiteralPath $class).Hash.ToLowerInvariant() -cne $ExpectedHarnessSha256){throw 'HARNESS_HASH_MISMATCH'}
    $harnessRoot=Join-Path 'C:\ProgramData\EslEtlV2\app-bloco54' ('test-harness-'+$ExpectedHarnessSha256.Substring(0,16))
    $destination=Join-Path $harnessRoot $classRelative
    if(-not (Test-Path -LiteralPath $destination)){
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination));[IO.File]::Copy($class,$destination,$false)
    }
    if((Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant() -cne $ExpectedHarnessSha256){throw 'PROTECTED_HARNESS_MISMATCH'}
    $credential=ConvertTo-SecureString -String ([IO.File]::ReadAllText('C:\ProgramData\EslEtlV2\secrets\etl_v2_exec.dpapi'))
    Add-Type -Path (Join-Path $PSScriptRoot 'Bloco54Process.cs')
    Import-Module (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Force
    if(-not $UseActiveCampaign){$campaign=Start-Bloco54Campaign 'physical-scope-matrix-concurrent-jvms-and-mapping-revocation';$active=$true}
    $originalHash=Read-State 'AUTH-mapping-original'
    $inventory=Get-Content (Join-Path $root 'target/bloco54/identity-inventory.json') -Raw|ConvertFrom-Json -DateKind String
    $until=([DateTimeOffset]::Parse(($inventory | Where-Object principal_kind -eq 'SERVICE').validUntil)).UtcDateTime.ToString('yyyy-MM-ddTHH:mm:ss.fff')
    if(-not $RemainingOnly){
    Complete-Probe (Start-Probe 'AUTH15_DIRECT_SQL' 'DIRECT_SQL' ([guid]::NewGuid())) 0 0 0
    if($ObservabilityProfile){Complete-Probe (Start-Probe 'AUTH15_EXISTING_OCCURRENCE' 'DIRECT_EXISTING' ([guid]::NewGuid()) '078e37f2-c69e-42e0-82f3-7d5f935bd5bb') 0 1 1}
    foreach($field in @('version','invocation','action','execution','environment','source','tenant','workload','entity','mode','start','endExclusive','strategy','idempotency','replayOf','cycle','planVersion','planHash','contractVersion','contractHash','configurationVersion','configurationHash')){
        Complete-Probe (Start-Probe ('AUTH_BIND_'+$field) 'FIELD' ([guid]::NewGuid()) $field) 0 1 0
    }
    Complete-Probe (Start-Probe 'AUTH11_RECEIPT' 'RECEIPT' ([guid]::NewGuid())) 0 1 0
    Complete-Probe (Start-Probe 'AUTH11_REUSE' 'REUSE' ([guid]::NewGuid())) 20 1 1
    Complete-Probe (Start-Probe 'AUTH12_LOST_AUTHORIZE_ACK' 'LOST_AUTHORIZE_ACK' ([guid]::NewGuid())) 20 1 0
    Complete-Probe (Start-Probe 'AUTH12_LOST_CONSUME_ACK' 'LOST_CONSUME_ACK' ([guid]::NewGuid())) 20 1 1
    $raceId=[guid]::NewGuid()
    $one=Start-Probe 'AUTH10_JVM_A' 'RACE' $raceId
    $two=Start-Probe 'AUTH10_JVM_B' 'RACE' $raceId
    $oneResult=$one.task.GetAwaiter().GetResult();$twoResult=$two.task.GetAwaiter().GetResult()
    if(((@($oneResult.Code,$twoResult.Code)|Sort-Object) -join ',') -cne '0,20'){throw 'TWO_JVM_SINGLE_CONSUMER_FAILED'}
    Complete-Probe $one $oneResult.Code 1 1
    Complete-Probe $two $twoResult.Code 1 1
    Complete-Probe (Start-Probe 'AUTH07_CAPABILITY_EXPIRED' 'EXPIRE' ([guid]::NewGuid())) 20 1 0
    }
    $scopeWhere="original_sid=(SELECT original_sid FROM ctl.runtime_identity_mapping WHERE principal_kind='SERVICE') AND environment_name=N'LOCAL_SHADOW' AND source_instance=N'LOCAL_V2' AND tenant_scope=N'LOCAL_V2' AND workload=N'coletas' AND mode=N'BACKFILL'"
    $mutations=[ordered]@{
        MAPPING_REVOKED="UPDATE ctl.runtime_identity_mapping SET revoked=1,mapping_version=mapping_version+1 WHERE principal_kind='SERVICE';"
        SCOPE_REVOKED="UPDATE ctl.runtime_identity_scope SET revoked=1,scope_version=scope_version+1 WHERE $scopeWhere;"
        MAPPING_VERSION="UPDATE ctl.runtime_identity_mapping SET mapping_version=mapping_version+1 WHERE principal_kind='SERVICE';"
        SCOPE_VERSION="UPDATE ctl.runtime_identity_scope SET scope_version=scope_version+1 WHERE $scopeWhere;"
        POLICY_CHANGED="UPDATE ctl.runtime_identity_scope SET policy_fingerprint=REPLICATE('b',64),scope_version=scope_version+1 WHERE $scopeWhere;"
        MAPPING_EXPIRED="UPDATE ctl.runtime_identity_mapping SET valid_until_utc=SYSUTCDATETIME(),mapping_version=mapping_version+1 WHERE principal_kind='SERVICE';"
    }
    foreach($name in $mutations.Keys){
        $id=$(if($RemainingOnly){'SIXTH_AUTH08_'}else{'AUTH08_'})+$name
        $before=Read-State ($id+'-before')
        $barrier=Join-Path $harnessRoot ($id+'.go')
        if(Test-Path -LiteralPath $barrier){throw 'EXISTING_TEST_BARRIER_REFUSED'}
        $probe=Start-Probe $id 'WAIT' ([guid]::NewGuid()) 'unused' $barrier
        $deadline=[datetime]::UtcNow.AddSeconds(10);$seen=$false
        do {
            $out=Invoke-LocalSql ($id+'-await') ("SELECT COUNT(*) FROM ctl.runtime_authorization_decision WHERE invocation_id='"+$probe.invocation+"' AND decision='ALLOW';")
            if(@($out|Where-Object {$_.Trim() -ceq '1'}).Count){$seen=$true;break}
            Start-Sleep -Milliseconds 100
        }while([datetime]::UtcNow -lt $deadline)
        if(-not $seen){throw 'DURABLE_AUTHORIZE_BARRIER_NOT_OBSERVED'}
        $restore="UPDATE ctl.runtime_identity_mapping SET revoked=0,observer=1,executor=1,replay=0,force_run=0,valid_until_utc=CONVERT(DATETIME2(3),'$until'),mapping_version=mapping_version+1 WHERE principal_kind='SERVICE'; UPDATE ctl.runtime_identity_scope SET revoked=0,policy_fingerprint='c76b345af620f21984d3b2d3cec10b0064e53f3b3440984c3f484c834977cc5d',scope_version=scope_version+1 WHERE $scopeWhere;"
        [IO.File]::WriteAllText((Join-Path $evidence ($id+'-planned-compensation.sql')),$restore,$encoding)
        $mutation="BEGIN TRANSACTION; DECLARE @before NVARCHAR(MAX)=$stateExpression; IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@before),2))<>'$before' THROW 52505,N'MAPPING_PRECONDITION_CHANGED',1; "+$mutations[$name]+" IF @@ROWCOUNT<>1 THROW 52505,N'EXACT_ONE_MAPPING_DELTA_REQUIRED',1; COMMIT;"
        $mappingDirty=$true
        Invoke-LocalSql ($id+'-apply') $mutation | Out-Null
        $after=Read-State ($id+'-after')
        [IO.File]::WriteAllText($barrier,'CONTINUE_TEST_ONLY',$encoding)
        Complete-Probe $probe 20 1 0
        # Always read actual state before compensation; preserve versions and original expiry.
        $current=Read-State ($id+'-pre-compensation')
        if($current -cne $after){throw 'CONCURRENT_MAPPING_CHANGE_REFUSED'}
        Invoke-LocalSql ($id+'-compensate') ("BEGIN TRANSACTION; DECLARE @current NVARCHAR(MAX)=$stateExpression; IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@current),2))<>'$current' THROW 52505,N'MAPPING_COMPENSATION_PRECONDITION',1; "+$restore+' COMMIT;') | Out-Null
        $mappingDirty=$false
        Read-State ($id+'-restored') | Out-Null
    }
    $validator=if($ObservabilityProfile){'database/proposals/bloco54-observability/verify.sql'}else{'database/validation/053_validate_local_runtime_provisioning.sql'}
    Invoke-LocalSql 'AUTH-final-provisioning' (Get-Content (Join-Path $root $validator) -Raw) | Out-Null
    if($RemainingOnly){
        $restartExecution=[guid]::NewGuid().ToString()
        Complete-Probe (Start-Probe 'SIXTH_AUTH13_CONSUME_STOP' 'CONSUME_STOP' ([guid]::NewGuid()) $restartExecution) 0 1 1
        if(-not ([IO.File]::ReadAllText((Join-Path $evidence 'SIXTH_AUTH13_CONSUME_STOP.log'))).Contains('REAL_CONSUME_COMMITTED_HALT_BEFORE_BUSINESS_DISPATCH')){throw 'CONSUME_HALT_NOT_CONFIRMED'}
        Complete-Probe (Start-Probe 'SIXTH_AUTH13_CONSUME_RESTART' 'CONSUME_RESTART' ([guid]::NewGuid()) $restartExecution) 0 1 1
        Invoke-LocalSql 'SIXTH_AUTH13-independent-check' ("IF (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE execution_id='$restartExecution')<>2 OR EXISTS(SELECT 1 FROM ctl.execution_attempt WHERE execution_id='$restartExecution') THROW 52505,N'CONSUME_RESTART_NOT_CONFIRMED',1; PRINT N'TWO_REAL_CONSUMPTIONS_FRESH_JVMS_NO_BUSINESS_DISPATCH';") | Out-Null
    }
    Add-Bloco54Reservation $(if($RemainingOnly){'SIXTH_OPS02_HARNESS_PARTIAL'}else{'OPS02_HARNESS_PARTIAL'}) 'SQL_REVOKED_PARTIAL_STATE' | Out-Null
    $partialBefore=Read-State 'OPS02-before'
    $recovery="UPDATE ctl.runtime_identity_mapping SET revoked=0,mapping_version=mapping_version+1 WHERE principal_kind='SERVICE' AND revoked=1 AND valid_until_utc=CONVERT(DATETIME2(3),'$until'); IF @@ROWCOUNT<>1 THROW 52505,N'EXACT_PARTIAL_MAPPING_RECOVERY_REQUIRED',1;"
    [IO.File]::WriteAllText((Join-Path $evidence 'OPS02-planned-recovery.sql'),$recovery,$encoding)
    Invoke-LocalSql 'OPS02-preserve-revoked' ("BEGIN TRANSACTION; DECLARE @before NVARCHAR(MAX)=$stateExpression; IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@before),2))<>'$partialBefore' THROW 52505,N'PARTIAL_STATE_PRECONDITION',1; UPDATE ctl.runtime_identity_mapping SET revoked=1,mapping_version=mapping_version+1 WHERE principal_kind='SERVICE'; COMMIT;") | Out-Null
    $partialAfter=Read-State 'OPS02-partial-readback'
    [IO.File]::WriteAllText((Join-Path $evidence 'OPS02-restart-contract.json'),([ordered]@{state='PRESERVED_REVOKED';before=$partialBefore;expected=$partialAfter;originalUntil=$until;recoverySha256=(Get-FileHash (Join-Path $evidence 'OPS02-planned-recovery.sql')).Hash.ToLowerInvariant()}|ConvertTo-Json),$encoding)
    $results.Add([pscustomobject]@{id='OPS02_PARTIAL_PRESERVED_REVOKED_RESTART_REQUIRED';passed=$true})
    $results.Add([pscustomobject]@{id='MATRIX_COMPLETED';passed=$true})
} catch {
    $reason=[string]$_.Exception.Message
    if($reason -cnotmatch '^[A-Z0-9_-]{1,160}$'){$reason='MATRIX_CONTROLLER_FAILURE_REDACTED'}
    $results.Add([pscustomobject]@{id='CONTROLLER_FAILURE';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;type=$_.Exception.GetType().Name;mappingDirty=$mappingDirty;passed=$false})
    if($mappingDirty){
        try {
            $failureState=Read-State 'AUTH-failure-readback'
            Invoke-LocalSql 'AUTH-failure-revoke-preserve' ("BEGIN TRANSACTION; DECLARE @actual NVARCHAR(MAX)=$stateExpression; IF LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',@actual),2))<>'$failureState' THROW 52505,N'FAILURE_READBACK_CONFLICT',1; UPDATE ctl.runtime_identity_mapping SET revoked=1,mapping_version=mapping_version+1 WHERE principal_kind='SERVICE' AND revoked=0; COMMIT;") | Out-Null
        } catch {$results.Add([pscustomobject]@{id='COMPENSATION_REQUIRES_READBACK';passed=$false})}
    }
} finally {
    if($null -ne $credential){$credential.Dispose()}
    if($active){Stop-Bloco54Campaign 'authority-matrix-finally-see-durable-results' | Out-Null}
    [IO.File]::WriteAllText((Join-Path $evidence 'authority-matrix-results.json'),($results|ConvertTo-Json -Depth 6),$encoding)
}

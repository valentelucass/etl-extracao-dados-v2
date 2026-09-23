#Requires -Version 7.5
param([switch]$Run,[Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if(-not $Run){throw 'COLSQL_EXPLICIT_RUN_REQUIRED'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$private=[IO.Path]::GetFullPath((Join-Path $root $EvidenceDirectory))
$allowed=[IO.Path]::GetFullPath((Join-Path $root 'target/coletas-temporal-sql-20260910/'))
if(-not $private.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or $private -eq $allowed.TrimEnd('\')){throw 'COLSQL_EVIDENCE_TARGET'}
if(Test-Path -LiteralPath $private){throw 'COLSQL_EVIDENCE_EXISTS'}
$null=New-Item -ItemType Directory -Path $private
$proposal=Join-Path $root 'database/proposals/coletas-temporal/001_coletas_temporal_reference.sql'
$scriptHash=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant()
$proposalHash=(Get-FileHash -LiteralPath $proposal).Hash.ToLowerInvariant()
Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $private 'executed.ps1')
Copy-Item -LiteralPath $proposal -Destination (Join-Path $private 'executed.sql')
$batches=@([regex]::Split([IO.File]::ReadAllText($proposal),'(?m)^GO\s*$')|Where-Object {-not [string]::IsNullOrWhiteSpace($_)})
$connection=$null;$transaction=$null
$source='SYNTHETIC_COLETAS_TEMPORAL_SQL'
$tenant='SYNTHETIC_COLETAS_TENANT'
$ledger=Join-Path $private 'ledger.jsonl'
function Ledger($entry){$entry|ConvertTo-Json -Depth 8 -Compress|Add-Content -LiteralPath $ledger -Encoding utf8}
function Command([string]$sql,[hashtable]$parameters=@{}){
    $cmd=$connection.CreateCommand();$cmd.CommandText=$sql;$cmd.CommandTimeout=20
    if($null -ne $transaction){$cmd.Transaction=$transaction}
    foreach($key in $parameters.Keys){$null=$cmd.Parameters.AddWithValue('@'+$key,$parameters[$key])}
    return $cmd
}
function Nq([string]$sql,[hashtable]$parameters=@{}){$cmd=Command $sql $parameters;try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}}
function Scalar([string]$sql,[hashtable]$parameters=@{}){$cmd=Command $sql $parameters;try{return $cmd.ExecuteScalar()}finally{$cmd.Dispose()}}
function Assert([bool]$condition,[string]$reason){if(-not $condition){throw $reason}}
function NewReference([int]$page=1,[int]$ordinal=1,[string]$status='pending',[int]$nano=123456789){
    $stamp='2036-01-20T10:00:00.'+$nano.ToString('D9')+'Z'
    return [ordered]@{executionId=$reference.ToString();source=$source;tenant=$tenant;queryDate='2036-01-20';
        contractVersion='2026-09-10.coletas-temporal.1';selectionVersion='graphql-document-v1';
        selectionSha256='422e85b03c534a7addc32c5711c4a9de1275d4245213030733bd95f444113c35';
        sourceKey='STRING:17';page=$page;ordinal=$ordinal;observedAt='2036-01-21T10:00:00.987654321Z';
        statusPresence='VALUE';statusRawJson=('"'+$status+'"');statusText=$status;statusCode=$status;
        statusUpdatedAtPresence='VALUE';statusUpdatedAtRawJson=('"'+$stamp+'"');statusUpdatedAtText=$stamp;
        requestDatePresence='VALUE';requestDateRawJson='"2036-01-20"';requestDateText='2036-01-20';
        epochSecond=[datetimeoffset]::Parse('2036-01-20T10:00:00Z').ToUnixTimeSeconds();nano=$nano}
}
function StageReference($observation){Nq 'EXEC stg.usp_stage_coleta_temporal @payload' @{payload=($observation|ConvertTo-Json -Compress -Depth 5)}}
function ConcurrentLock([bool]$blocked){
    $other=[Data.SqlClient.SqlConnection]::new('Server=localhost;Database=ETL_SISTEMA_V2_SHADOW;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5;Application Name=ColetasTemporalSqlConcurrentGuard;Pooling=false')
    try{
        $other.Open();$otherTransaction=$other.BeginTransaction();$cmd=$other.CreateCommand();$cmd.Transaction=$otherTransaction;$cmd.CommandTimeout=5
        $cmd.CommandText="DECLARE @resource NVARCHAR(255)=CONCAT(N'coleta-temporal:',LOWER(CONVERT(NVARCHAR(36),@ref))),@result INT; EXEC @result=sys.sp_getapplock @Resource=@resource,@LockMode=N'Exclusive',@LockOwner=N'Transaction',@LockTimeout=100; SELECT @result"
        $null=$cmd.Parameters.AddWithValue('@ref',$reference)
        $result=$cmd.ExecuteScalar()
        Assert $(if($blocked){$result -eq -1}else{$result -ge 0}) 'CONCURRENT_TRANSACTION_GUARD'
        $cmd.Dispose();$otherTransaction.Rollback();$otherTransaction.Dispose()
    }finally{$other.Dispose()}
}
function CompleteReference([int]$pages=1,[long]$nodes=1){Nq 'EXEC stg.usp_complete_coleta_temporal @ref,@pages,@nodes' @{ref=$reference;pages=$pages;nodes=$nodes}}
function Bind([string]$key='INTEGER:17',[string]$refKey='STRING:17',[string]$scope=$tenant){
    Nq 'EXEC stg.usp_bind_coleta_temporal @de,@ref,@source,@tenant,@key,@refKey,@date,@version,@sha' @{
        de=$data;ref=$reference;source=$source;tenant=$scope;key=$key;refKey=$refKey;date='2036-01-20';version='synthetic-crosswalk-v1';sha=('a'*64)}
}
function StageData([int]$ordinal=1,[string]$status='pending',[string]$origin='REQUEST_DATE',[switch]$Quarantine){
    $terminal=$status -in @('done','finished','canceled','cancelled')
    $label=switch($status){'done'{'Coletada'};'finished'{'Finalizada'};'canceled'{'Cancelada'};default{'Pendente'}}
    $fresh=if($origin -ceq 'REQUEST_DATE'){'2036-01-20T03:00:00.000'}else{'2036-01-20T10:00:00.123'}
    $raw=if($origin -ceq 'REQUEST_DATE'){[DBNull]::Value}else{'2036-01-20T10:00:00.123456789Z'}
    $payload=@{id=17;status=$status;request_date='2036-01-20';synthetic_variant=$ordinal}|ConvertTo-Json -Compress
    Nq @'
DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@time DATETIME2(3)=CONVERT(DATETIME2(3),@fresh,126);
EXEC stg.usp_stage_coleta_record @de,1,@ordinal,N'INTEGER:17',N'INTEGER',N'VALUE',N'{"value":17}',
    @payload,N'{"sequence_code":"VALUE","status":"VALUE","status_updated_at":"ABSENT"}',N'{}',
    @status,@status,@label,N'coletas-status-v1',@terminal,N'Synthetic',@attempts,@raw,@time,@origin,@disposition,@reason,@now;
'@ @{de=$data;ordinal=$ordinal;fresh=$fresh;payload=$payload;status=$status;label=$label;terminal=$terminal;
        attempts=[int]$terminal;raw=$raw;origin=$origin;disposition=$(if($Quarantine){'QUARANTINE'}else{'VALID'});
        reason=$(if($Quarantine){'SYNTHETIC_QUARANTINE'}else{[DBNull]::Value})}
}
function ReadyData{
    Nq @'
DECLARE @now DATETIME2(3)=SYSUTCDATETIME();
EXEC ctl.usp_control_plane_transition_execution @de,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@now;
EXEC ctl.usp_control_plane_transition_execution @de,N'EXTRACTED',N'STAGED',N'STAGING_OK',@now;
'@ @{de=$data}
}
function Qualify{
    $cmd=Command 'EXEC recon.usp_qualify_coleta_temporal @de,@ref' @{de=$data;ref=$reference}
    try{$reader=$cmd.ExecuteReader();try{
        Assert ($reader.Read()) 'SUMMARY_MISSING'
        $result=@{considered=$reader.GetInt64(0);candidates=$reader.GetInt64(1);blocked=$reader.GetInt64(2)}
        Assert (-not $reader.Read()) 'SUMMARY_DUPLICATED'
        return $result
    }finally{$reader.Dispose()}}finally{$cmd.Dispose()}
}
$cases=@(
    'MATCH','IDENTICAL_REFERENCE_REPEAT','CONFLICTING_REFERENCE_TIME','CONFLICTING_REFERENCE_STATUS',
    'SUBMILLISECOND_CONFLICT','RETRY_IDENTICAL','RETRY_DIVERGENT','LATE_STAGE','CAPTURE_INCOMPLETE',
    'ORDINAL_GAP','CONTRACT_DRIFT','SELECTION_DRIFT','RUN_SCOPE_DRIFT','TENANT_BINDING',
    'AMBIGUOUS_BINDING','BINDING_ABSENT','REFERENCE_ABSENT','WINDOW_MISMATCH','STATUS_MISMATCH',
    'NULL_TIME','ABSENT_TIME','INVALID_TIME','NATIVE_MATCH','NATIVE_LEXEME_UNPROVEN',
    'DATA_EXPANSION','DATA_ROOT_CONFLICT','DATA_NOT_STAGED','CANCELED_MATCH','FINISHED_MATCH',
    'DONE_VS_FINISHED','QUARANTINE','UNKNOWN_STATUS','UTC_EDGE','CONCURRENT_REFERENCE_GUARD'
)
$errors=@{RETRY_DIVERGENT=53002;LATE_STAGE=53003;CAPTURE_INCOMPLETE=53004;ORDINAL_GAP=53004;
    CONTRACT_DRIFT=53000;SELECTION_DRIFT=53000;RUN_SCOPE_DRIFT=53001;TENANT_BINDING=53006;
    AMBIGUOUS_BINDING=53007;DATA_NOT_STAGED=53006}
$expected=@{CONFLICTING_REFERENCE_TIME='REFERENCE_CONFLICT';CONFLICTING_REFERENCE_STATUS='REFERENCE_CONFLICT';
    SUBMILLISECOND_CONFLICT='REFERENCE_CONFLICT';BINDING_ABSENT='BINDING_ABSENT';REFERENCE_ABSENT='REFERENCE_ABSENT';
    WINDOW_MISMATCH='WINDOW_MISMATCH';STATUS_MISMATCH='STATUS_MISMATCH';NULL_TIME='REFERENCE_INVALID';
    ABSENT_TIME='REFERENCE_INVALID';INVALID_TIME='REFERENCE_INVALID';NATIVE_MATCH='DATA_EXPORT_TIME_RETAINED';
    NATIVE_LEXEME_UNPROVEN='NATIVE_PRECISION_UNVERIFIED';DATA_ROOT_CONFLICT='DATA_EXPORT_ROOT_CONFLICT';
    DONE_VS_FINISHED='STATUS_MISMATCH';UNKNOWN_STATUS='STATUS_MISMATCH'}
$results=[Collections.Generic.List[object]]::new()
try{
    $connection=[Data.SqlClient.SqlConnection]::new('Server=localhost;Database=master;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5;Application Name=ColetasTemporalSqlQualification;Pooling=false')
    $connection.Open()
    Assert ((Scalar "SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE'") -eq 1) 'EXACT_TARGET_UNAVAILABLE'
    $connection.Dispose()
    $connection=[Data.SqlClient.SqlConnection]::new('Server=localhost;Database=ETL_SISTEMA_V2_SHADOW;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5;Application Name=ColetasTemporalSqlQualification;Pooling=false')
    $connection.Open()
    Assert ((Scalar 'SELECT DB_NAME()') -ceq 'ETL_SISTEMA_V2_SHADOW') 'WRONG_TARGET'
    Nq 'SET NOCOUNT ON; SET XACT_ABORT ON; SET LOCK_TIMEOUT 1500;'
    $objects="SELECT COUNT(*) FROM sys.objects WHERE name IN(N'coleta_temporal_run',N'coleta_temporal_observation',N'coleta_temporal_binding',N'usp_lock_coleta_temporal',N'usp_stage_coleta_temporal',N'usp_complete_coleta_temporal',N'usp_bind_coleta_temporal',N'vw_coleta_temporal_candidate',N'usp_qualify_coleta_temporal')"
    $residual="SELECT COUNT_BIG(*) FROM ctl.source_catalog WHERE source_instance=@source"
    Assert ((Scalar $objects) -eq 0 -and (Scalar $residual @{source=$source}) -eq 0) 'PREEXISTING_SCOPE'
    foreach($case in $cases){
        $expectedError=if($errors.ContainsKey($case)){$errors[$case]}else{0}
        $row=[ordered]@{case=$case;expectedError=$expectedError;observedError=0;passed=$false;rollbackConfirmed=$false;failure=$null}
        Ledger @{case=$case;state='RESERVED_OUTCOME_UNKNOWN';scriptSha256=$scriptHash;proposalSha256=$proposalHash;target='LOCAL_V2_SHADOW_ONLY';ddl='TRANSACTIONAL_ROLLBACK_ONLY'}
        $transaction=$connection.BeginTransaction()
        $data=[guid]::NewGuid();$reference=[guid]::NewGuid()
        try{
            foreach($batch in $batches){Nq $batch}
            Nq @'
DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@sha CHAR(64)=REPLICATE('a',64),@idem NVARCHAR(128)=CONVERT(NVARCHAR(36),@de);
EXEC ctl.usp_control_plane_register_source @source,N'DATA_EXPORT',@now;
EXEC ctl.usp_control_plane_start_cycle @cycle,N'synthetic-temporal-sql-v1',@sha,@now;
EXEC ctl.usp_control_plane_start_execution @de,@cycle,N'LOCAL_SHADOW',@source,@tenant,N'coletas',N'BACKFILL',
    '2036-01-20','2036-01-21',N'DATA_EXPORT_RESTART_FROM_BEGINNING',N'dataexport-6908-v02',@sha,
    N'coletas-shadow-v1',@sha,@idem,NULL,3600,@now;
'@ @{source=$source;tenant=$tenant;cycle=[guid]::NewGuid();de=$data}
            $status=switch($case){'CANCELED_MATCH'{'canceled'};'FINISHED_MATCH'{'finished'};'DONE_VS_FINISHED'{'done'};default{'pending'}}
            $origin=if($case.StartsWith('NATIVE_')){'STATUS_UPDATED_AT'}else{'REQUEST_DATE'}
            StageData -status $status -origin $origin
            if($case -ceq 'DATA_EXPANSION'){StageData -ordinal 2}
            if($case -ceq 'DATA_ROOT_CONFLICT'){StageData -ordinal 2 -status 'done'}
            if($case -ceq 'QUARANTINE'){StageData -ordinal 2 -Quarantine}
            if($case -cne 'DATA_NOT_STAGED'){ReadyData}
            $observation=NewReference -status $status
            switch($case){
                'CONTRACT_DRIFT'{$observation.contractVersion='unapproved-v1'}
                'SELECTION_DRIFT'{$observation.selectionSha256='b'*64}
                'ORDINAL_GAP'{$observation.ordinal=2}
                'WINDOW_MISMATCH'{$observation.requestDateText='2036-01-19';$observation.requestDateRawJson='"2036-01-19"'}
                'STATUS_MISMATCH'{$observation.statusCode='done';$observation.statusText='done';$observation.statusRawJson='"done"'}
                'DONE_VS_FINISHED'{$observation.statusCode='finished';$observation.statusText='finished';$observation.statusRawJson='"finished"'}
                'NULL_TIME'{$observation.statusUpdatedAtPresence='NULL';$observation.statusUpdatedAtRawJson=$null;$observation.statusUpdatedAtText=$null;$observation.epochSecond=$null;$observation.nano=$null}
                'ABSENT_TIME'{$observation.statusUpdatedAtPresence='ABSENT';$observation.statusUpdatedAtRawJson=$null;$observation.statusUpdatedAtText=$null;$observation.epochSecond=$null;$observation.nano=$null}
                'INVALID_TIME'{$observation.statusUpdatedAtRawJson='"invalid"';$observation.statusUpdatedAtText='invalid';$observation.epochSecond=$null;$observation.nano=$null}
                'NATIVE_LEXEME_UNPROVEN'{$observation.statusUpdatedAtText='2036-01-20T07:00:00.123456789-03:00';$observation.statusUpdatedAtRawJson='"2036-01-20T07:00:00.123456789-03:00"'}
                'UNKNOWN_STATUS'{$observation.statusCode=$null;$observation.statusText='future_status';$observation.statusRawJson='"future_status"'}
                'UTC_EDGE'{$observation.statusUpdatedAtText='2036-01-19T23:59:59.123456789-03:00';$observation.statusUpdatedAtRawJson='"2036-01-19T23:59:59.123456789-03:00"';$observation.epochSecond=[datetimeoffset]::Parse('2036-01-20T02:59:59Z').ToUnixTimeSeconds()}
            }
            StageReference $observation
            $pages=1;$nodes=1
            switch($case){
                'RETRY_IDENTICAL'{StageReference $observation}
                'RETRY_DIVERGENT'{$observation.nano=123456788;$observation.statusUpdatedAtText='2036-01-20T10:00:00.123456788Z';$observation.statusUpdatedAtRawJson='"2036-01-20T10:00:00.123456788Z"';StageReference $observation}
                'RUN_SCOPE_DRIFT'{$observation.tenant='OTHER_SYNTHETIC_TENANT';$observation.page=2;StageReference $observation}
                'IDENTICAL_REFERENCE_REPEAT'{$observation.page=2;StageReference $observation;$pages=2;$nodes=2}
                'CONFLICTING_REFERENCE_TIME'{$observation.page=2;$observation.epochSecond++;$observation.statusUpdatedAtText='2036-01-20T10:00:01.123456789Z';$observation.statusUpdatedAtRawJson='"2036-01-20T10:00:01.123456789Z"';StageReference $observation;$pages=2;$nodes=2}
                'CONFLICTING_REFERENCE_STATUS'{$observation.page=2;$observation.statusCode='done';$observation.statusText='done';$observation.statusRawJson='"done"';StageReference $observation;$pages=2;$nodes=2}
                'SUBMILLISECOND_CONFLICT'{$observation.page=2;$observation.nano=123456788;$observation.statusUpdatedAtText='2036-01-20T10:00:00.123456788Z';$observation.statusUpdatedAtRawJson='"2036-01-20T10:00:00.123456788Z"';StageReference $observation;$pages=2;$nodes=2}
                'CONCURRENT_REFERENCE_GUARD'{ConcurrentLock $true}
            }
            if($case -cne 'CAPTURE_INCOMPLETE'){CompleteReference $pages $nodes}
            if($case -ceq 'LATE_STAGE'){$observation.page=2;StageReference $observation}
            if($case -ceq 'TENANT_BINDING'){Bind -scope 'OTHER_SYNTHETIC_TENANT'}
            if($case -ceq 'REFERENCE_ABSENT'){Bind -refKey 'STRING:absent'}
            elseif($case -cne 'BINDING_ABSENT'){Bind}
            if($case -ceq 'AMBIGUOUS_BINDING'){Bind -key 'INTEGER:18'}
            $summary=Qualify
            $repeat=Qualify
            Assert ($summary.considered -eq $repeat.considered -and $summary.candidates -eq $repeat.candidates -and $summary.blocked -eq $repeat.blocked) 'QUALIFICATION_NOT_IDEMPOTENT'
            Assert ($expectedError -eq 0) 'EXPECTED_REFUSAL_NOT_OBSERVED'
            $outcome=if($expected.ContainsKey($case)){$expected[$case]}else{'COMPLEMENTED'}
            $typedCount=if($case -in @('DATA_EXPANSION','DATA_ROOT_CONFLICT')){2}else{1}
            $considered=if($case -in @('DATA_EXPANSION','DATA_ROOT_CONFLICT','QUARANTINE')){2}else{1}
            $candidates=if($outcome -in @('COMPLEMENTED','DATA_EXPORT_TIME_RETAINED')){$typedCount}else{0}
            Assert ($summary.considered -eq $considered -and $summary.candidates -eq $candidates -and $summary.blocked -eq ($considered-$candidates)) 'COUNT_EQUATION'
            $matching=Scalar 'SELECT COUNT_BIG(*) FROM recon.vw_coleta_temporal_candidate WHERE data_export_execution_id=@de AND reference_execution_id=@ref AND outcome=@outcome AND promotion_authorized=0' @{de=$data;ref=$reference;outcome=$outcome}
            Assert ($matching -eq $typedCount) 'OUTCOME_MISMATCH'
            if($candidates -gt 0){Assert ((Scalar 'SELECT COUNT_BIG(*) FROM recon.vw_coleta_temporal_candidate WHERE data_export_execution_id=@de AND reference_execution_id=@ref AND candidate_nano=123456789 AND candidate_epoch_second=@seconds AND millisecond_exact=0' @{de=$data;ref=$reference;seconds=$observation.epochSecond}) -eq $candidates) 'INSTANT_PRECISION_LOST'}
            Assert ((Scalar 'SELECT COUNT_BIG(*) FROM core.coleta WHERE source_instance=@source' @{source=$source}) -eq 0) 'UNAUTHORIZED_PROMOTION'
            Assert ((Scalar 'SELECT COUNT_BIG(*) FROM stg.coleta_record WHERE execution_id=@de AND freshness_origin=@origin AND JSON_VALUE(field_presence_json,''$.status_updated_at'')=N''ABSENT''' @{de=$data;origin=$origin}) -eq $typedCount) 'DATA_EXPORT_MUTATED'
            $row.Add('summary',$summary);$row.Add('outcome',$outcome);$row.passed=$true
        }catch [Data.SqlClient.SqlException]{
            $row.observedError=$_.Exception.Number;$row.passed=$row.observedError -eq $expectedError
            if(-not $row.passed){$row.failure='SQL_UNEXPECTED';$row.Add('line',$_.Exception.LineNumber);$row.Add('procedure',$_.Exception.Procedure)}
        }catch{$row.failure=$_.Exception.Message}
        finally{
            if($null -ne $transaction.Connection){$transaction.Rollback()}
            $transaction.Dispose();$transaction=$null
            $row.rollbackConfirmed=(Scalar 'SELECT @@TRANCOUNT') -eq 0 -and (Scalar $objects) -eq 0 -and (Scalar $residual @{source=$source}) -eq 0
            if($case -ceq 'CONCURRENT_REFERENCE_GUARD'){ConcurrentLock $false}
            if(-not $row.rollbackConfirmed){$row.passed=$false;$row.failure='ROLLBACK_UNKNOWN'}
        }
        $results.Add($row);Ledger @{case=$case;state='OBSERVED';result=$row}
        $row|ConvertTo-Json -Depth 6 -Compress
        if(-not $row.passed){break}
    }
}finally{
    if($null -ne $transaction -and $null -ne $transaction.Connection){$transaction.Rollback();$transaction.Dispose()}
    if($null -ne $connection){$connection.Dispose()}
    $summary=[ordered]@{passed=($results.Count -eq $cases.Count -and @($results|Where-Object {-not $_.passed}).Count -eq 0);
        physicalSql=$true;syntheticOnly=$true;transactionalDdl=$true;durableDdl=$false;durableDomainRows=0;
        remoteCalls=0;sourceTemporalEquivalenceAccepted=$false;representativeAcceptance=$false;
        proposalSha256=$proposalHash;scriptSha256=$scriptHash;cases=@($results.ToArray())}
    $summary|ConvertTo-Json -Depth 10|Set-Content -LiteralPath (Join-Path $private 'summary.json') -Encoding utf8
}
if(-not $summary.passed){exit 1}

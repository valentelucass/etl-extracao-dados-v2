# Physical fault matrix. Each process is owned and every retry is explicit with a fresh invocation.
foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $name=$ProofId+'_'+$template
 $r=New-Request $template '2032-06-01'
 Jar ($name+'_PARTIAL') 'etl_v2_exec' 'run' $r 40 3 -Scenario PARTIAL|Out-Null
 Record ([ordered]@{id=$name+'_PARTIAL_NO_PROMOTION';layer='SQL_OWN_OCCURRENCE';passed=$lastObservation.pages -eq 1 -and $lastObservation.publications -eq 0 -and $lastObservation.state -ceq 'FAILED'})
 $r=New-Request $template '2032-06-02';$server.DataDelayMilliseconds=2000;$server.AllowOwnClientDisconnect=$true
 try{Jar ($name+'_CANCEL') 'etl_v2_exec' 'run' $r 50 2 -Cancel|Out-Null}finally{$server.DataDelayMilliseconds=0}
 $r.invocationId=[guid]::NewGuid().ToString();Jar ($name+'_CANCEL_RESUME') 'etl_v2_exec' 'run' $r 50 0|Out-Null
 $r=New-Request $template '2032-06-03'
 Jar ($name+'_KILL_BEFORE') 'etl_v2_exec' 'run' $r -999 0 -KillMarker 'RUNTIME_OBSERVATION reason=NOT_FOUND'|Out-Null
 Record ([ordered]@{id=$name+'_CONSUMED_BEFORE_DISPATCH';layer='SQL_OWN_OCCURRENCE';passed=$lastChild.KilledByController -and $lastObservation.consumptions -eq 1 -and $lastObservation.publications -eq 0 -and $lastObservation.pages -eq 0})
 $r.invocationId=[guid]::NewGuid().ToString();Jar ($name+'_RESTART_BEFORE') 'etl_v2_exec' 'run' $r 0 4|Out-Null
 $r=New-Request $template '2032-06-04'
 Jar ($name+'_KILL_AFTER') 'etl_v2_exec' 'run' $r -999 4 -KillMarker 'RUNTIME_SQL_OBSERVATION state=PUBLISHED'|Out-Null
 Record ([ordered]@{id=$name+'_PUBLISHED_BEFORE_CALLER_FINISHED';layer='SQL_OWN_OCCURRENCE';passed=$lastChild.KilledByController -and $lastObservation.publications -eq 1})
 $r.invocationId=[guid]::NewGuid().ToString();Jar ($name+'_RESTART_AFTER') 'etl_v2_exec' 'run' $r 0 0|Out-Null
 $r=New-Request $template '2032-06-05';$r.leaseSeconds='1';$server.DataDelayMilliseconds=2000
 try{Jar ($name+'_LEASE_EXPIRED') 'etl_v2_exec' 'run' $r 40 2|Out-Null}finally{$server.DataDelayMilliseconds=0}
 $r.invocationId=[guid]::NewGuid().ToString();Jar ($name+'_NO_TAKEOVER') 'etl_v2_exec' 'run' $r 40 0|Out-Null
 $r=New-Request $template '2032-06-06';$r.receiptId=[guid]::NewGuid().ToString()
 Jar ($name+'_CALLER_RECEIPT') 'etl_v2_exec' 'run' $r 2 0|Out-Null
}
$r=New-Request 'COTACOES' '2032-06-07';[void]$r.Remove('referenceReleaseId');Jar ($ProofId+'_REF_ABSENT') 'etl_v2_exec' 'run' $r 2 0|Out-Null
$r=New-Request 'COTACOES' '2032-06-08';$r.referenceReleaseId='9223372036854775806';Jar ($ProofId+'_REF_UNKNOWN') 'etl_v2_exec' 'run' $r 30 0|Out-Null
$r=Get-Content (Join-Path $root 'target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_COTACOES_RUN.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
$r.invocationId=[guid]::NewGuid().ToString();$r.referenceReleaseId='9223372036854775806';Jar ($ProofId+'_REF_KNOWN_CHANGED') 'etl_v2_exec' 'run' $r 30 0|Out-Null
$r=New-Request 'MANIFESTOS' '2032-06-09';Jar ($ProofId+'_ROOT_NULL_CONFLICT') 'etl_v2_exec' 'run' $r 40 4 -Scenario ROOT_CONFLICT|Out-Null

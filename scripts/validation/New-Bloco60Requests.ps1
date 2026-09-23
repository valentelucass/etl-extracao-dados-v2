#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$folder=Join-Path $root 'database/proposals/bloco60-local'
$out=Join-Path $folder 'requests'
if(Test-Path $out){throw 'B60_REQUESTS_ALREADY_FROZEN'}
[void][IO.Directory]::CreateDirectory($out)
$utf8=[Text.UTF8Encoding]::new($false,$true)
$quality=@(Get-Content (Join-Path $folder 'quality-references.json') -Raw|ConvertFrom-Json -AsHashtable)
$quality+=@(Get-Content (Join-Path $root 'target/bloco55/reviewed-bundle-v4/manifest.json') -Raw|ConvertFrom-Json -AsHashtable).quality
$cases=[Collections.Generic.List[object]]::new();$requests=@{};$day=0
function NewDocument([string]$Template='USUARIOS',[string]$Mode='BACKFILL',[string]$Replay=''){
 $script:day++
 $date=([DateTime]'2033-06-01').AddDays($script:day)
 $q=@($quality|Where-Object { $_.template -ceq $Template -and $_.mode -ceq $Mode })
 if($q.Count -ne 1){throw 'B60_EXACT_QUALITY_REFERENCE'}
 $r=[ordered]@{invocationId=[guid]::NewGuid().ToString();executionId=[guid]::NewGuid().ToString();cycleId=[guid]::NewGuid().ToString();mode=$Mode;start=$date.ToString('yyyy-MM-dd')+'T03:00:00Z';endExclusive=$date.AddDays(1).ToString('yyyy-MM-dd')+'T03:00:00Z';replayOf=$Replay;idempotencyKey='B60_'+[guid]::NewGuid().ToString('N');leaseSeconds='30';pageSize='20';maximumPages='4';qualityVersion=$q[0].version;qualityFingerprint=$q[0].fingerprint;compatibilityVersion='bloco60-compatible-v1'}
 if($Template -ceq 'USUARIOS'){$r.protocol='GRAPHQL';$r.operation='USERS_SNAPSHOT';$r.maximumNodes='80'}
 else{$r.template=$Template;$r.pageSize='2';$r.maximumRows='16';$r.maximumDistinctRoots='16';$r.businessStart=$date.ToString('yyyy-MM-dd');$r.businessEnd=$date.ToString('yyyy-MM-dd');if($Template -ceq 'COTACOES'){$r.referenceReleaseId='3'}}
 return $r
}
function CopyDocument($r){$copy=($r|ConvertTo-Json -Depth 8)|ConvertFrom-Json -AsHashtable;$copy.invocationId=[guid]::NewGuid().ToString();$copy}
function AddCase([string]$Id,$Document,[hashtable]$Options=@{}){
 $record=[ordered]@{id=$Id;request='requests/'+$Id+'.json';layer='OFFICIAL_JAR_WINDOWS_SQL';artifact='administered';account='etl_v2_exec';command='run';fault='NONE';scenario='NORMAL';group='B60_'+$Id;delayMilliseconds=0;cancel=$false;waitBeforeSeconds=0;pair='';expectedExit=@(0);expectedHttp=@(2);expected=@{publications=1;seals=1;consumptions=1};unchangedFrom=''}
 foreach($key in $Options.Keys){if(-not $record.Contains($key)){throw ('B60_CASE_UNKNOWN_KEY_'+$key)};$record[$key]=$Options[$key]}
 if($record.fault -cne 'NONE'){$record.layer='REAL_WINDOWS_SQL_CLIENT_FAULT_PROBE'}
 [IO.File]::WriteAllText((Join-Path $folder $record.request),($Document|ConvertTo-Json -Depth 8),$utf8)
 $requests[$Id]=$Document;$cases.Add($record)
}
foreach($template in @('USUARIOS','COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $r=NewDocument $template;$http=if($template -ceq 'USUARIOS'){2}else{4};$name=$template+'_RUN'
 AddCase $name $r @{expectedHttp=@($http);group='B60_BASE'}
 foreach($account in @('etl_v2_exec','etl_v2_view')){AddCase ($template+'_STATUS_'+$account.ToUpperInvariant()) (CopyDocument $r) @{command='status';account=$account;expectedHttp=@(0);expected=@{publications=1;seals=1;consumptions=1};unchangedFrom=$name}}
 AddCase ($template+'_KNOWN') (CopyDocument $r) @{expectedHttp=@(0);unchangedFrom=$name}
}
AddCase 'USERS_ONE_PAGE' (NewDocument) @{scenario='ONE_PAGE';expectedHttp=@(1);expected=@{publications=1;seals=1;consumptions=1;pages=1;auditedRows=1}}
AddCase 'USERS_NOOP' (NewDocument) @{group='B60_BASE';expected=@{publications=1;seals=1;consumptions=1;typedNoop=2;typedUpdated=0;typedInserted=0}}
AddCase 'USERS_UPDATE' (NewDocument) @{group='B60_BASE';scenario='UPDATE';expected=@{publications=1;seals=1;consumptions=1;typedUpdated=2;typedInserted=0;typedNoop=0}}
foreach($case in @('CONFLICT','PARTIAL','NULL_TERMINAL','INVALID_NODE')){
 AddCase ('USERS_'+$case) (NewDocument) @{scenario=$case;expectedExit=@(40);expectedHttp=$(if($case -cin @('NULL_TERMINAL','INVALID_NODE')){@(1)}else{@(2)});expected=@{publications=0;seals=0;consumptions=1}}
}
AddCase 'USERS_CANCEL' (NewDocument) @{cancel=$true;delayMilliseconds=1500;expectedExit=@(50);expectedHttp=@(1);expected=@{publications=0;seals=0;consumptions=1;state='CANCELLED'}}
foreach($fault in @('MUTATE_TENANT','MUTATE_ENTITY','MUTATE_CONTRACT','MUTATE_CONFIGURATION','MUTATE_PROTOCOL')){
 AddCase $fault (NewDocument) @{fault=$fault;expectedExit=@(40);expectedHttp=@(0);expected=@{attempts=0;publications=0;seals=0;consumptions=1}}
}
AddCase 'OPERATOR_CANNOT_WRITE' (NewDocument) @{account='etl_v2_view';expectedExit=@(20);expectedHttp=@(0);expected=@{attempts=0;publications=0;seals=0;consumptions=0;decisions=1;decision='DENY'}}
AddCase 'CONSUME_ACK_LOST' (NewDocument) @{fault='CONSUME_ACK_LOST';expectedExit=@(20);expectedHttp=@(0);expected=@{attempts=0;publications=0;seals=0;consumptions=1}}
foreach($entry in @(@('INVALID_PAGE','pageSize','21'),@('INVALID_PROTOCOL','protocol','DATA_EXPORT'),@('INVALID_OPERATION','operation','UNKNOWN'))){
 $r=NewDocument;$r[$entry[1]]=$entry[2]
 AddCase $entry[0] $r @{expectedExit=@(2);expectedHttp=@(0);expected=@{attempts=0;decisions=0;publications=0;consumptions=0}}
}
AddCase 'STANDARD_AUTHORITY_ABSENT' (NewDocument) @{artifact='standard';expectedExit=@(20);expectedHttp=@(0);expected=@{attempts=0;decisions=0;publications=0;consumptions=0}}
foreach($artifact in @('standard','administered')){AddCase ('DIAGNOSTIC_'+$artifact.ToUpperInvariant()) (NewDocument) @{artifact=$artifact;command='dry-run';expectedExit=@(0);expectedHttp=@(0);expected=@{attempts=0;decisions=0;publications=0;consumptions=0}}}
foreach($fault in @('STAGE_FAILURE','AUDIT_NULL','AUDIT_ACK_LOST','DQ_MISSING','DQ_ACK_LOST','PREPARE_ACK_LOST')){
 $http=if($fault -cin @('STAGE_FAILURE','AUDIT_NULL','AUDIT_ACK_LOST')){@(1)}else{@(2)}
 AddCase $fault (NewDocument) @{fault=$fault;expectedExit=@(40);expectedHttp=$http;expected=@{publications=0;seals=0;consumptions=1}}
}
foreach($point in @('PREPARE','SEAL','APPLY')){
 foreach($when in @('BEFORE','AFTER')){
  $id='HALT_'+$when+'_'+$point;$r=NewDocument
  $seal=if($point -ceq 'APPLY' -or $point -ceq 'SEAL' -and $when -ceq 'AFTER'){1}else{0}
  $publication=if($point -ceq 'APPLY' -and $when -ceq 'AFTER'){1}else{0}
  AddCase $id $r @{fault=$id;expectedExit=@(86);expectedHttp=@(2);expected=@{publications=$publication;seals=$seal;consumptions=1}}
  if($seal -eq 1){AddCase ($id+'_RESUME') (CopyDocument $r) @{expectedHttp=@(0);unchangedFrom=$(if($publication){$id}else{''})}}
 }
}
foreach($point in @('SEAL','APPLY')){
 $id=$point+'_ACK_LOST';$r=NewDocument
 AddCase $id $r @{fault=$id;expectedExit=@(40);expectedHttp=@(2);expected=@{publications=$(if($point -ceq 'APPLY'){1}else{0});seals=1;consumptions=1}}
 AddCase ($id+'_RESUME') (CopyDocument $r) @{expectedHttp=@(0);unchangedFrom=$(if($point -ceq 'APPLY'){$id}else{''})}
}
$r=NewDocument;$r.leaseSeconds='3'
AddCase 'LEASE_EXPIRY_HALT' $r @{fault='HALT_AFTER_SEAL';expectedExit=@(86);expectedHttp=@(2);expected=@{publications=0;seals=1;consumptions=1}}
AddCase 'LEASE_EXPIRED_REFUSED' (CopyDocument $r) @{waitBeforeSeconds=5;expectedExit=@(30);expectedHttp=@(0);expected=@{publications=0;seals=1;consumptions=1};unchangedFrom='LEASE_EXPIRY_HALT'}
foreach($origin in @('USUARIOS_RUN','USERS_PARTIAL')){
 $r=NewDocument 'USUARIOS' 'REPLAY' $requests[$origin].executionId
 AddCase ('REPLAY_'+$origin) $r @{command='replay';group='B60_REPLAY';expected=@{publications=1;seals=1;consumptions=1;replayOf=$requests[$origin].executionId}}
}
AddCase 'FORCE_RUN' (NewDocument) @{command='force-run'}
$r=NewDocument
AddCase 'CONCURRENT_SEALED' $r @{fault='HALT_AFTER_SEAL';expectedExit=@(86);expectedHttp=@(2);expected=@{publications=0;seals=1;consumptions=1}}
AddCase 'CONCURRENT_A' (CopyDocument $r) @{pair='B60_RECOVERY_PAIR';expectedHttp=@(0)}
AddCase 'CONCURRENT_B' (CopyDocument $r) @{pair='B60_RECOVERY_PAIR';expectedHttp=@(0)}
if($cases.Count -gt 80){throw 'B60_FROZEN_JVM_CAP'}
[IO.File]::WriteAllText((Join-Path $folder 'matrix.json'),($cases|ConvertTo-Json -Depth 12),$utf8)
[IO.File]::WriteAllText((Join-Path $folder 'all-quality-references.json'),($quality|ConvertTo-Json -Depth 8),$utf8)
[ordered]@{cases=$cases.Count;sqlExecuted=$false;requestsFrozen=$true}|ConvertTo-Json

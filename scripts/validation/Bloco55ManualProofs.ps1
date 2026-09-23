# Every occurrence remains owned by SQL. This file stores receipts, never a workflow state store.
$summary=[Collections.Generic.List[object]]::new();$done=0
foreach($entry in $manualBatch.Requests){
 if($done -ge $StopAfter){break}
 $r=$entry.Document|ConvertTo-Json -Depth 8|ConvertFrom-Json -AsHashtable -DateKind String
 $r.invocationId=[guid]::NewGuid().ToString()
 $account=if($ManualOperation -ceq 'Status'){'etl_v2_view'}else{'etl_v2_exec'}
 $command=if($ManualOperation -ceq 'Status'){'status'}else{'run'}
 $name=$ProofId+'_'+$entry.Ordinal.ToString('00')+'_'+$r.template
 $scenario=if($ManualFault -and $entry.Ordinal -eq 1 -and $r.template -ceq 'COLETAS'){'PARTIAL'}else{'NORMAL'}
 Jar $name $account $command $r -998 -1 -Scenario $scenario|Out-Null
 $outcome=if($lastChild.Output.Contains('RUNTIME_DEPENDENCY reason=DEPENDENCY_NOT_PUBLISHED source_composed=0')){'DEPENDENCY_BLOCKED'}else{switch($lastChild.Code){
  0 {if($lastHttp -eq 0){'ALREADY_CONFIRMED'}else{'PUBLISHED'}}
  10 {'UNCERTAIN'}
  40 {'FAILED'}
  50 {'CANCELLED'}
  default {'UNCERTAIN'}
 }}
 $summary.Add([ordered]@{ordinal=$entry.Ordinal;template=$r.template;window=$r.businessStart;outcome=$outcome;requestSha256=$entry.Sha256;invocationId=$r.invocationId;exit=$lastChild.Code;http=$lastHttp;sqlPublications=$lastObservation.publications})
 $done++;$requests.Add($r)
 if($lastChild.Code -in @(2,20,30) -or $lastChild.Limited){throw 'MANUAL_PREFLIGHT_INTEGRITY_OR_UNCERTAINTY_STOP'}
}
$receipt=[ordered]@{manifestSha256=$manualBatch.Sha256;operation=$ManualOperation;planned=$manualBatch.Requests.Count;processed=$done;interruptedByOwnerLimit=$done -lt $manualBatch.Requests.Count;automaticRetry=$false;sqlOwnsState=$true;summary=$summary}
$text=$receipt|ConvertTo-Json -Depth 6
if($encoding.GetByteCount($text) -gt 16384 -or $summary.Count -gt 64){throw 'MANUAL_SUMMARY_CAP'}
[IO.File]::WriteAllText((Join-Path $evidence 'manual-summary.json'),$text,$encoding)
if((Get-FileHash $manualBatch.Path).Hash.ToLowerInvariant() -cne $manualBatch.Sha256){throw 'MANUAL_MANIFEST_CHANGED_DURING_RUN'}
Record ([ordered]@{id='MANUAL_FROZEN_MANIFEST_PRESERVED';passed=$true})

#Requires -Version 7.0
param([switch]$WriteReceipt)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$proofs=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
function Read-Proof([string]$Path){
 $file=Join-Path $root $Path
 if((Get-Item -LiteralPath $file).Length -gt 64KB){throw 'SQL_FENCE_PROOF_BOUND'}
 [void]$proofs.Add($Path);$utf8.GetString([IO.File]::ReadAllBytes($file))
}
$base='target/bloco55/runtime/B55_SQL_01/'
$cases=@((Read-Proof ($base+'results.json'))|ConvertFrom-Json -DateKind String)
$suffixes=@('MANIFESTOS_OCCURRENCE_MATERIAL_CHANGED','COTACOES_OCCURRENCE_MATERIAL_CHANGED','LOCALIZACAO_CARGAS_OCCURRENCE_MATERIAL_CHANGED','KNOWN_TEMPLATE_SWAPPED')
if($cases.Count -ne 19 -or @($cases|Where-Object {-not $_.passed}).Count -ne 4){throw 'EXACT_SQL_FENCE_CASE_MATRIX_REQUIRED'}
foreach($suffix in $suffixes){
 $id='B55_SQL_01_'+$suffix;$case=@($cases|Where-Object id -CEQ $id)
 if($case.Count -ne 1 -or $case[0].passed -or $case[0].expectedExit -ne 30 -or $case[0].observedExit -ne 40 -or $case[0].http -ne 0 -or $case[0].account -cne 'etl_v2_exec' -or $case[0].observed.consumptions -ne 1 -or $case[0].observed.publications -ne 1 -or $case[0].observed.state -cne 'PUBLISHED' -or $case[0].observed.pages -ne 3){throw 'IMMUTABLE_FALSE_EXIT_EXPECTATION_REQUIRED'}
 $log=Read-Proof ($base+$id+'.log')
 foreach($marker in @('RUNTIME_OBSERVATION reason=INCONSISTENT','RUNTIME_HTTP_ATTEMPTS metadata=0 data=0 total=0','Runtime: SOURCE_DQ')){if(-not $log.Contains($marker)){throw 'DURABLE_RECOVERY_REFUSAL_MARKER_REQUIRED'}}
 $request=(Read-Proof ($base+$id+'.request.json'))|ConvertFrom-Json -DateKind String
 $originalTemplate=if($suffix -ceq 'KNOWN_TEMPLATE_SWAPPED'){'MANIFESTOS'}else{$suffix.Replace('_OCCURRENCE_MATERIAL_CHANGED','')}
 $original=(Read-Proof ('target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_'+$originalTemplate+'_RUN.request.json'))|ConvertFrom-Json -DateKind String
 if($request.executionId -cne $original.executionId -or $request.invocationId -ceq $original.invocationId -or $case[0].observed.outputRows -ne $(if($originalTemplate -ceq 'MANIFESTOS'){5}else{2})){throw 'SAME_ORIGINAL_OCCURRENCE_NEW_INVOCATION_REQUIRED'}
 if($suffix -ceq 'KNOWN_TEMPLATE_SWAPPED'){
  if($request.template -ceq $original.template){throw 'TEMPLATE_MUTATION_REQUIRED'}
 }elseif($request.cycleId -ceq $original.cycleId){throw 'OCCURRENCE_MATERIAL_MUTATION_REQUIRED'}
}
foreach($case in $cases|Where-Object {$_.id -notin @($suffixes|ForEach-Object{'B55_SQL_01_'+$_})}){if(-not $case.passed){throw 'UNRECONCILED_SQL_CASE_FAILED'}}
foreach($suffix in @('MANIFESTOS_OTHER_OCCURRENCE','COTACOES_OTHER_OCCURRENCE','LOCALIZACAO_CARGAS_OTHER_OCCURRENCE','CALLER_REDUCED_CANDIDATE','CALLER_DIRECT_STAGE_DML')){
 $expected=if($suffix.StartsWith('CALLER_')){229}else{52840}
 $proof=(Read-Proof ($base+'B55_SQL_01_'+$suffix+'.log')).Trim()|ConvertFrom-Json
 if($proof.expected_error -ne $expected -or $proof.residual_transactions -ne 0){throw 'REAL_RESTRICTED_SQL_DENIAL_REQUIRED'}
}
$revocation=(Read-Proof ($base+'B55_SQL_01_REFERENCE_REVOCATION.sql.log')).Trim()|ConvertFrom-Json
if($revocation.code -cne 'REVOKED_REFERENCE_REFUSED_ROLLBACK_PRESERVED' -or $revocation.residual_transactions -ne 0){throw 'REFERENCE_REVOCATION_ROLLBACK_REQUIRED'}
$preservation=Read-Proof 'target/bloco55/snapshots/B55_READ_03/PRESERVATION.log'
$bindingLines=@($preservation -split "`n"|Where-Object {$_.StartsWith('{"originalBindings"')})
if($bindingLines.Count -ne 1){throw 'INDEPENDENT_SQL_BINDING_RECEIPT_REQUIRED'}
$bindings=$bindingLines[0]|ConvertFrom-Json
foreach($pair in @(@('originalBindings',3),@('originalPublications',3),@('originalPages',9),@('originalOutputs',9),@('refusedConsumed',4),@('originalTariffBindings',1),@('materialMutations',0))){if($bindings.($pair[0]) -ne $pair[1]){throw 'ORIGINAL_BINDINGS_MUTATED_AFTER_REFUSAL'}}
$snapshot=(Read-Proof 'target/bloco55/snapshots/B55_READ_03/receipt.json')|ConvertFrom-Json
if(-not $snapshot.passed -or $snapshot.observerTransactions -ne 0 -or $snapshot.restrictedSessions -ne 0){throw 'FINAL_SQL_SNAPSHOT_REQUIRED'}
if($WriteReceipt){
 $destination=Join-Path $root 'target/bloco55/sql-fence-reconciliation.json'
 if(Test-Path $destination){throw 'EXISTING_SQL_RECONCILIATION_PRESERVE'}
 $files=@($proofs|Sort-Object|ForEach-Object{[ordered]@{path=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $root $_)).Hash.ToLowerInvariant()}})
 $receipt=[ordered]@{layer='IMMUTABLE_JAR_LOGS_AND_INDEPENDENT_SQL_ORIGINAL_BINDINGS';reason='WRONG_CONTROLLER_EXIT_EXPECTATION_30_ACTUAL_SOURCE_DQ_40';originalFalseNegativesPreserved=4;otherRecordsPassed=15;http=0;bindings=$bindings;passed=$true;proofFiles=$files}
 [IO.File]::WriteAllText($destination,($receipt|ConvertTo-Json -Depth 7),$utf8)
}
'B55_SQL_FOUR_FALSE_NEGATIVES_RECONCILED_DIRECT_DENIALS_AND_ORIGINAL_BINDINGS_PASS'

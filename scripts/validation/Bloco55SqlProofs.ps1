& (Join-Path $root 'scripts/runtime/Invoke-Bloco55ManualBatch.ps1') -Manifest (Join-Path $root 'target/bloco55/manual/positive/manifest.json') -ManifestSha256 '19e0e7f307c4bea191382cdcb6beccd47f3d6452c336a8726a780d66cbd6e293' -RunId ($ProofId+'_DIAGNOSE') -Operation Diagnose | Out-Null
Record ([ordered]@{id=$ProofId+'_DIAGNOSE';layer='MANUAL_LAUNCHER_READONLY_SQL_AND_PROTECTED_ARTIFACT';passed=$true})
function Restricted-Sql([string]$Name,[string]$Body,[int]$ExpectedError){
 Add-Bloco55Reservation $Name 'SQL_REAL_WINDOWS_SERVICE_FENCE_ROLLBACK'|Out-Null
 $sql="SET NOCOUNT ON;SET XACT_ABORT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR IS_SRVROLEMEMBER(N'sysadmin')<>0 OR IS_MEMBER(N'db_owner')<>0 OR RIGHT(ORIGINAL_LOGIN(),11)<>N'etl_v2_exec' THROW 52901,N'REAL_RESTRICTED_WINDOWS_REQUIRED',1;`nBEGIN TRY BEGIN TRANSACTION;`n"+$Body+"`nROLLBACK; THROW 52902,N'EXPECTED_CONSUMER_REFUSAL',1; END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK; IF ERROR_NUMBER()<>$ExpectedError THROW; SELECT ERROR_NUMBER() expected_error,@@TRANCOUNT residual_transactions FOR JSON PATH,WITHOUT_ARRAY_WRAPPER; END CATCH;"
 $file=Join-Path $work ($Name+'.sql');[IO.File]::WriteAllText($file,$sql,$encoding);[IO.File]::Copy($file,(Join-Path $evidence ($Name+'.sql')),$false)
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command sqlcmd.exe).Source
 $start.UserName='etl_v2_exec';$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials.etl_v2_exec;$start.LoadUserProfile=$true
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden';$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$protectedRevision
 foreach($arg in @('-S','localhost','-d','ETL_SISTEMA_V2_SHADOW','-E','-N','-l','10','-t','30','-b','-y','0','-i',$file)){$start.ArgumentList.Add($arg)}
 $child=[Bloco55Child]::Run($start).GetAwaiter().GetResult();[IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$child.Output,$encoding)
 $passed=$child.Code -eq 0 -and -not $child.Limited -and $child.Output.Contains('"expected_error":'+$ExpectedError) -and $child.Output.Contains('"residual_transactions":0')
 Record ([ordered]@{id=$Name;layer='SQLCMD_REAL_WINDOWS_SERVICE';account='etl_v2_exec';expectedError=$ExpectedError;exit=$child.Code;passed=$passed})
 if($child.Limited){throw 'RESTRICTED_SQL_CHILD_LIMIT'}
}
foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $name=$ProofId+'_'+$template
 $r=Get-Content (Join-Path $root ('target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_'+$template+'_RUN.request.json')) -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 $r.invocationId=[guid]::NewGuid().ToString();Jar ($name+'_OWN_CONSUMED') 'etl_v2_exec' 'run' $r 0 0|Out-Null
 $foreign=[guid]::NewGuid().ToString()
 $body="DECLARE @other UNIQUEIDENTIFIER='$foreign'; EXEC sys.sp_set_session_context @key=N'execution_id',@value=@other; EXEC core.usp_apply_reconcile_publish_"+$template.ToLowerInvariant()+" @other,NULL,NULL,NULL,NULL"+$(if($template -ceq 'COTACOES'){',3'})+';'
 Restricted-Sql ($name+'_OTHER_OCCURRENCE') $body 52840
 $changed=New-Request $template '2032-09-01';$changed.executionId=$r.executionId;$changed.cycleId=[guid]::NewGuid().ToString()
 Jar ($name+'_OCCURRENCE_MATERIAL_CHANGED') 'etl_v2_exec' 'run' $changed 40 0 -ExistingPublicationRefusal|Out-Null
}
Restricted-Sql ($ProofId+'_CALLER_REDUCED_CANDIDATE') 'EXEC stg.usp_stage_manifesto_reduced_candidate;' 229
Restricted-Sql ($ProofId+'_CALLER_DIRECT_STAGE_DML') "INSERT stg.execution_record(execution_id) VALUES(NEWID());" 229
$r=New-Request 'MANIFESTOS' '2032-09-02';$r.entity='cotacoes';Jar ($ProofId+'_ENTITY_CHANGED') 'etl_v2_exec' 'run' $r 2 0|Out-Null
$alternate=Join-Path $work 'other-namespace.properties'
$original=[IO.File]::ReadAllText($config);$altered=$original.Replace('dataexport.tenant-scope=LOCAL_V2','dataexport.tenant-scope=OTHER_SYNTHETIC_SCOPE')
if($altered -ceq $original){throw 'EXACT_NAMESPACE_CONFIGURATION_KEY_REQUIRED'}
[IO.File]::WriteAllText($alternate,$altered,$encoding)
$r=New-Request 'LOCALIZACAO_CARGAS' '2032-09-03';Jar ($ProofId+'_NAMESPACE_CHANGED') 'etl_v2_exec' 'run' $r 2 0 -ConfigurationFile $alternate|Out-Null
$r=New-Request 'COTACOES' '2032-09-03';Jar ($ProofId+'_REFERENCE_WRONG_NAMESPACE') 'etl_v2_exec' 'run' $r 2 0 -ConfigurationFile $alternate|Out-Null
$r=New-Request 'LOCALIZACAO_CARGAS' '2032-05-10'
$known=Get-Content (Join-Path $root 'target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_MANIFESTOS_RUN.request.json') -Raw|ConvertFrom-Json -DateKind String
$r.executionId=$known.executionId;$r.cycleId=$known.cycleId
Jar ($ProofId+'_KNOWN_TEMPLATE_SWAPPED') 'etl_v2_exec' 'run' $r 40 0 -ExistingPublicationRefusal|Out-Null
Add-Bloco55Reservation ($ProofId+'_REFERENCE_REVOCATION') 'SQL_OWN_REFERENCE_REVOKED_ROLLBACK'|Out-Null
$referenceSql=@'
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 THROW 52903,N'EXACT_TARGET',1;
DECLARE @execution UNIQUEIDENTIFIER=NEWID();
IF ctl.fn_runtime_tariff_valid(@execution,3)<>1 THROW 52903,N'VALID_ORIGINAL_REFERENCE_REQUIRED',1;
BEGIN TRANSACTION;
INSERT ref.reference_release_revocation(reference_release_id,activation_scope,revoker_role,reason_code,evidence_ref,evidence_fingerprint)
VALUES(3,N'SHADOW',N'LABORATORY_OWNER',N'SYNTHETIC_REVOCATION_TEST',N'B55-TRANSIENT-REVOCATION',REPLICATE('b',64));
IF ctl.fn_runtime_tariff_valid(@execution,3)<>0 THROW 52903,N'REVOKED_REFERENCE_MUST_BE_REFUSED',1;
ROLLBACK;
IF ctl.fn_runtime_tariff_valid(@execution,3)<>1 OR EXISTS(SELECT 1 FROM ref.reference_release_revocation WHERE reference_release_id=3 AND activation_scope=N'SHADOW') THROW 52903,N'EXACT_ROLLBACK_REQUIRED',1;
SELECT N'REVOKED_REFERENCE_REFUSED_ROLLBACK_PRESERVED' code,@@TRANCOUNT residual_transactions FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
'@
$out=Sql ($ProofId+'_REFERENCE_REVOCATION') $referenceSql
Record ([ordered]@{id=$ProofId+'_REFERENCE_REVOCATION';layer='SQL_REAL_FUNCTION_ADMIN_TRANSACTION_ROLLBACK';passed=$out.Contains('REVOKED_REFERENCE_REFUSED_ROLLBACK_PRESERVED') -and $out.Contains('"residual_transactions":0')})

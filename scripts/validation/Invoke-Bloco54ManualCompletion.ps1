#Requires -Version 7.0
param([Parameter(Mandatory)][int]$ExpectedDatabaseRows,
 [Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,30}$')][string]$ProofId)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root ('target/bloco54/completion-authorized-20260908/'+$ProofId)
$bundle=Join-Path $root 'target/bloco54/reviewed-bundle-v7'
$hash='838d2316fa53f412131d25a3c0c9cb397805fb05cecc3693b68daa37507e95fa'
$results=[Collections.Generic.List[object]]::new();$active=$false
$encoding=[Text.UTF8Encoding]::new($false,$true)
function Record($value){$results.Add($value);[IO.File]::WriteAllText((Join-Path $evidence 'results.json'),($results|ConvertTo-Json -Depth 6),$encoding)}
function Sql([string]$Name,[string]$Body){
 $file=Join-Path $evidence ($Name+'.sql');[IO.File]::WriteAllText($file,$Body,$encoding)
 & sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -f 65001 -l 10 -t 20 -b -y 0 -w 65535 -i $file *> (Join-Path $evidence ($Name+'.log'))
 if($LASTEXITCODE -ne 0){throw ('MANUAL_SQL_FAILED_'+$Name)}
 return [IO.File]::ReadAllText((Join-Path $evidence ($Name+'.log')))
}
function Child([string]$Name,[string[]]$Arguments){
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName=Join-Path $PSHOME 'pwsh.exe';$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
 $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$root
 foreach($arg in @('-NoProfile','-File',(Join-Path $root 'scripts/validation/Invoke-Bloco54Manual.ps1'),'-Bundle',$bundle,'-ExpectedManifestSha256',$hash)+$Arguments){$start.ArgumentList.Add($arg)}
 $result=[Bloco54CompletionChild]::Run($start).GetAwaiter().GetResult()
 [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$result.Output,$encoding)
 if($result.Limited){throw 'MANUAL_CHILD_LIMIT'}
 return $result
}
try {
 if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
 if(Test-Path $evidence){throw 'EVIDENCE_EXISTS_PRESERVE'}
 [void][IO.Directory]::CreateDirectory($evidence)
 foreach($file in @($PSCommandPath,(Join-Path $root 'scripts/validation/Invoke-Bloco54Manual.ps1'),(Join-Path $root 'scripts/validation/Bloco54CompletionProcess.cs'))){[IO.File]::Copy($file,(Join-Path $evidence ('tested-'+[IO.Path]::GetFileName($file))),$false)}
 Add-Type -Path (Join-Path $root 'scripts/validation/Bloco54CompletionProcess.cs')
 $catalog=Get-Content (Join-Path $root 'target/bloco54/residual-runtime/schema-after.sql') -Raw
 $before=Sql 'schema-before' $catalog
 if($before -cnotmatch ('(?m)^DATA_ROWS='+$ExpectedDatabaseRows+'\s*$') -or -not $before.Contains('SCHEMA_SHA256=7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408')){throw 'EXACT_MANUAL_CHECKPOINT_REQUIRED'}
 $diagnosis=Child 'DIAGNOSE' @('-Command','diagnose','-ObservabilityProfile','-RetainedReplayScopes','-TemporalSchemaProfile')
 Record ([ordered]@{id='MANUAL_DIAGNOSE';layer='ADMINISTRATIVE_READ_ONLY';exit=$diagnosis.Code;passed=$diagnosis.Code -eq 0 -and $diagnosis.Output.Contains('READ_ONLY_DIAGNOSIS_PASS_NO_RENEWAL')})
 if($diagnosis.Code -ne 0){throw 'MANUAL_DIAGNOSIS_FAILED'}
 $preview=Child 'PLAN' @('-Command','plan','-Temporal','-Request',(Join-Path $root 'config/laboratory/bloco54-temporal-coletas.json'))
 Record ([ordered]@{id='MANUAL_PLAN';layer='OFFICIAL_JAR_OFFLINE';exit=$preview.Code;passed=$preview.Code -eq 0 -and $preview.Output.Contains('TEMPORAL_PREVIEW effects=0 windows=3')})
 Import-Module (Join-Path $root 'scripts/validation/Bloco54Budget.psm1') -Force
 Start-Bloco54CompletionCampaign '21e720af862253dd98504b70d29d36ec7fba01d2b11cb324c5aa8e42142e3bf5' ('manual-final-'+$ProofId)|Out-Null
 $active=$true
 $request=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/FINAL_RUN_COLETAS.request.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 foreach($key in @('invocationId','executionId','cycleId','idempotencyKey')){$request[$key]=[guid]::NewGuid().ToString()}
 $request.start='2026-06-03T03:00:00Z';$request.endExclusive='2026-06-04T03:00:00Z';$request.businessStart='2026-06-03';$request.businessEnd='2026-06-03'
 $receipt=Get-Content (Join-Path $root 'target/bloco54/resume-approved-supplemental/manual-shared-configuration.json') -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 $receipt.manifestSha256=$hash
 $receiptFile=Join-Path $evidence 'configuration-receipt.json';[IO.File]::WriteAllText($receiptFile,($receipt|ConvertTo-Json),$encoding)
 $receiptHash=(Get-FileHash $receiptFile).Hash.ToLowerInvariant()
 $httpTotal=0
 foreach($case in @(@('RUN','run','etl_v2_exec',3),@('STATUS_OPERATOR','status','etl_v2_view',0),@('STATUS_SERVICE','status','etl_v2_exec',0))){
  $request.invocationId=[guid]::NewGuid().ToString()
  $name=$ProofId+'_'+$case[0]
  $file=Join-Path $evidence ($name+'.request.json');[IO.File]::WriteAllText($file,($request|ConvertTo-Json -Depth 6),$encoding)
  $requestHash=(Get-FileHash $file).Hash.ToLowerInvariant()
  $child=Child $name @('-Command',$case[1],'-Account',$case[2],'-Request',$file,'-ExpectedRequestSha256',$requestHash,'-CaseId',$name,'-ConfigurationReceipt',$receiptFile,'-ExpectedReceiptSha256',$receiptHash,'-FixtureVersion','v2')
  $invocation=([guid]$request.invocationId).ToString();$execution=([guid]$request.executionId).ToString()
  $readback=Sql ($name+'-observe') "SET NOCOUNT ON; SELECT (SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_decision WHERE invocation_id='$invocation') decisions,(SELECT COUNT_BIG(*) FROM ctl.runtime_authorization_consumption WHERE invocation_id='$invocation') consumptions,(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event WHERE execution_id='$execution') publications FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
  $observed=$readback|ConvertFrom-Json
  $http=[regex]::Match($child.Output,'MANUAL_ONE_SHOT exit=0 http=(\d+)')
  $passed=$child.Code -eq 0 -and $http.Success -and [int]$http.Groups[1].Value -eq $case[3] -and $observed.decisions -eq 1 -and $observed.consumptions -eq 1 -and $observed.publications -eq 1
  if($http.Success){$httpTotal+=[int]$http.Groups[1].Value}
  Record ([ordered]@{id=$name;layer='MANUAL_OFFICIAL_JAR_REAL_WINDOWS_SQL';account=$case[2];exit=$child.Code;http=$(if($http.Success){[int]$http.Groups[1].Value}else{-1});observed=$observed;passed=$passed})
  if(-not $passed){throw 'MANUAL_RECEIPT_NOT_CONFIRMED'}
 }
 Record ([ordered]@{id='HTTP_TOTAL';requests=$httpTotal;passed=$httpTotal -eq 3})
 $preservation=Get-Content (Join-Path $root 'target/bloco54/completion-authorized-20260908/V7_SINKS_06/preservation-after.sql') -Raw
 $old=[regex]::Match($preservation,'INSERT @own VALUES [^;]+;').Value
 $preservation=$preservation.Replace($old,($old.TrimEnd(';')+",('$execution');"))
 Sql 'preservation-after' $preservation|Out-Null
 Sql 'schema-after' $catalog|Out-Null
 Sql 'profile-after' (Get-Content (Join-Path $root 'database/proposals/bloco54-temporal-continuation/verify.sql') -Raw)|Out-Null
 Record ([ordered]@{id='MANUAL_CONTROLLER_COMPLETED';passed=$true})
}catch{
 $cause=$_.Exception;while($cause.InnerException){$cause=$cause.InnerException}
 $reason=[string]$cause.Message;if($reason -cnotmatch '^[A-Za-z0-9_-]{1,140}$'){$reason='MANUAL_FAILURE_REDACTED'}
 Record ([ordered]@{id='MANUAL_CONTROLLER_FAILED';reason=$reason;line=$_.InvocationInfo.ScriptLineNumber;passed=$false})
}finally{
 if($active){Stop-Bloco54Campaign ('manual-'+$ProofId+'-finished-see-independent-receipts')|Out-Null}
 [IO.File]::WriteAllText((Join-Path $evidence 'controller-complete.txt'),'FINISHED_SEE_RESULTS',$encoding)
}

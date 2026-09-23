param([ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId='B55_PLANS_01')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
$directory=Join-Path $root ('target/bloco55/plans/'+$ProofId)
if(Test-Path $directory){throw 'EXISTING_EVIDENCE_PRESERVE'}
[void][IO.Directory]::CreateDirectory($directory)
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
$receipts=[Collections.Generic.List[object]]::new()
$migration=Get-Content (Join-Path $root 'database/migrations/V022__extend_five_vertical_runtime.sql') -Raw
$begin=$migration.IndexOf(' -- Independent cohorts are selected in SQL')
$finish=$migration.IndexOf(' -- Only this SQL consumer creates generic reduced rows;')
if($begin -lt 0 -or $finish -le $begin){throw 'EXACT_TEMPORARY_REDUCER_SEGMENT_REQUIRED'}
$temporarySql=$migration.Substring($begin,$finish-$begin)+"`nSELECT *,DENSE_RANK() OVER(PARTITION BY source_key,mdfe_key ORDER BY freshness_at_utc DESC) AS child_rank INTO #mdfe FROM #observations WHERE mdfe_key IS NOT NULL;"
$manifesto=Get-Content (Join-Path $root 'target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_MANIFESTOS_RUN.request.json') -Raw|ConvertFrom-Json
foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $request=Get-Content (Join-Path $root ('target/bloco55/runtime/B55_SMOKE_03/B55_SMOKE_03_'+$template+'_RUN.request.json')) -Raw|ConvertFrom-Json
 foreach($consumer in @('CANDIDATE','APPLY','RECOVERY')){
  $name=$template+'_'+$consumer
  Add-Bloco55Reservation ($ProofId+'_'+$name) 'SQL_ESTIMATED_OWN_OCCURRENCE_NO_DML'|Out-Null
  $connection=[Data.SqlClient.SqlConnection]::new('Server=localhost;Initial Catalog=ETL_SISTEMA_V2_SHADOW;Integrated Security=SSPI;Encrypt=True;TrustServerCertificate=False;Connect Timeout=10;Pooling=False;Application Name=B55BoundedEstimatedPlans')
  $files=[Collections.Generic.List[object]]::new();$indexes=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $transaction=$null
  try{
   $connection.Open();$transaction=$connection.BeginTransaction();$query=$connection.CreateCommand();$query.Transaction=$transaction;$query.CommandTimeout=30
   $query.CommandText='SELECT contract_version,contract_fingerprint,configuration_version,configuration_fingerprint FROM ctl.execution_attempt WHERE execution_id=@execution'
   [void]$query.Parameters.Add('@execution',[Data.SqlDbType]::UniqueIdentifier);$query.Parameters['@execution'].Value=[guid]$request.executionId
   $reader=$query.ExecuteReader();try{if(-not $reader.Read()){throw 'OWN_PUBLISHED_EXECUTION_REQUIRED'};$values=@($reader.GetString(0),$reader.GetString(1),$reader.GetString(2),$reader.GetString(3));if($reader.Read()){throw 'UNIQUE_EXECUTION_REQUIRED'}}finally{$reader.Dispose()}
   $query.Parameters.Clear();$query.CommandText="DECLARE @execution_id UNIQUEIDENTIFIER='"+([guid]$manifesto.executionId).ToString()+"'; "+$temporarySql
   [void]$query.ExecuteNonQuery()
   $query.Parameters.Clear();$query.CommandText='SET SHOWPLAN_XML ON';[void]$query.ExecuteNonQuery()
   $procedure=if($consumer -ceq 'CANDIDATE'){if($template -ceq 'MANIFESTOS'){'core.usp_prepare_manifesto_candidate_set'}else{'core.usp_prepare_staged_execution'}}elseif($consumer -ceq 'APPLY'){'core.usp_apply_reconcile_publish_'+$template.ToLowerInvariant()}else{'ctl.usp_runtime_recovery'}
   $query.CommandText=if($consumer -ceq 'RECOVERY'){'EXEC ctl.usp_runtime_recovery @execution,N''RESUME'',N''{}'',N''{}'',@qualityVersion,@qualityHash,N''NONE'',@entity,@reference'}else{'EXEC '+$procedure+' @execution,@cv,@ch,@fv,@fh'+$(if($template -ceq 'COTACOES' -and $consumer -ceq 'APPLY'){',@reference'})}
   [void]$query.Parameters.Add('@execution',[Data.SqlDbType]::UniqueIdentifier);$query.Parameters['@execution'].Value=[guid]$request.executionId
   for($i=0;$i -lt 4;$i++){[void]$query.Parameters.Add(@('@cv','@ch','@fv','@fh')[$i],[Data.SqlDbType]::NVarChar,128);$query.Parameters[$i+1].Value=$values[$i]}
   [void]$query.Parameters.Add('@qualityVersion',[Data.SqlDbType]::NVarChar,128);$query.Parameters['@qualityVersion'].Value=$request.qualityVersion
   [void]$query.Parameters.Add('@qualityHash',[Data.SqlDbType]::NVarChar,64);$query.Parameters['@qualityHash'].Value=$request.qualityFingerprint
   [void]$query.Parameters.Add('@entity',[Data.SqlDbType]::NVarChar,32);$query.Parameters['@entity'].Value=$template.ToLowerInvariant()
   [void]$query.Parameters.Add('@reference',[Data.SqlDbType]::BigInt);$query.Parameters['@reference'].Value=if($template -ceq 'COTACOES'){3L}else{[DBNull]::Value}
   # SqlClient RPC execution does not return the requested SHOWPLAN result here.
   # Compile one SQL batch with typed local variables. Names/types are closed above;
   # GUIDs are parsed, and SQL-owned strings use SQL literal escaping, never shell text.
   $declarations=[Text.StringBuilder]::new()
   foreach($parameter in $query.Parameters){
    $type=switch($parameter.SqlDbType){'UniqueIdentifier' {'UNIQUEIDENTIFIER'} 'BigInt' {'BIGINT'} 'NVarChar' {'NVARCHAR('+[int]$parameter.Size+')'} default {throw 'CLOSED_PLAN_PARAMETER_TYPE'}}
    $literal=if($parameter.Value -is [DBNull]){'NULL'}elseif($parameter.SqlDbType -eq [Data.SqlDbType]::BigInt){([long]$parameter.Value).ToString([Globalization.CultureInfo]::InvariantCulture)}else{"N'"+([string]$parameter.Value).Replace("'","''")+"'"}
    [void]$declarations.Append('DECLARE '+$parameter.ParameterName+' '+$type+'='+$literal+'; ')
   }
   $query.CommandText=$declarations.ToString()+$query.CommandText;$query.Parameters.Clear()
   $reader=$query.ExecuteReader();$count=0;$resultSets=0
   try{do{while($reader.Read()){
    $resultSets++;if($resultSets -gt 32){throw 'ESTIMATED_RESULT_SET_LIMIT'}
    $xml=$reader.GetString(0);if($utf8.GetByteCount($xml) -gt 16777216){throw 'ESTIMATED_BATCH_MEMORY_LIMIT'}
    $document=[xml]$xml
    # SQL returns a batch of statement plans. Keep each complete statement plan
    # as its own native ShowPlanXML artifact, each bounded to 1 MiB.
    $statements=@($document.SelectNodes("//*[local-name()='StmtSimple'][*[local-name()='QueryPlan'] or *[local-name()='UDF'] or *[local-name()='StoredProc']]"))
    foreach($statement in $statements){
     if($statement.SelectNodes(".//*[local-name()='StmtSimple']").Count -gt 0){continue}
     $count++;if($count -gt 512){throw 'ESTIMATED_STATEMENT_COUNT_LIMIT'}
     $standalone=$document.DocumentElement.CloneNode($false);$batchSequence=$document.CreateElement('BatchSequence',$standalone.NamespaceURI);$batch=$document.CreateElement('Batch',$standalone.NamespaceURI);$list=$document.CreateElement('Statements',$standalone.NamespaceURI)
     [void]$list.AppendChild($statement.CloneNode($true));[void]$batch.AppendChild($list);[void]$batchSequence.AppendChild($batch);[void]$standalone.AppendChild($batchSequence)
     $bytes=$utf8.GetBytes($standalone.OuterXml);if($bytes.Length -gt 1048576){throw 'ESTIMATED_STATEMENT_BYTE_LIMIT'}
     $file=Join-Path $directory ($name+'_'+$count+'.sqlplan');[IO.File]::WriteAllBytes($file,$bytes)
     $files.Add([ordered]@{file=[IO.Path]::GetFileName($file);bytes=$bytes.Length;statementPlan=$true;sha256=(Get-FileHash $file).Hash.ToLowerInvariant()})
     foreach($match in [regex]::Matches($standalone.OuterXml,'Index="([^"<>]{1,160})"')){[void]$indexes.Add($match.Groups[1].Value)}
    }
   }}while($reader.NextResult())}finally{$reader.Dispose()}
   $query.Parameters.Clear();$query.CommandText='SET SHOWPLAN_XML OFF';[void]$query.ExecuteNonQuery();$query.Dispose()
   if($count -eq 0){throw 'ESTIMATED_PLAN_MISSING'}
   $receipts.Add([ordered]@{template=$template;consumer=$consumer;procedure=$procedure;layer='SQL_ESTIMATED_ADMIN_OWN_EXECUTION_PARAMETERS';executedPersistentDml=$false;temporaryReducerShape='TWO_OWN_OBSERVATIONS_TRANSACTION_ROLLBACK';runtimeShowplanGrant=$false;measuredPerformance=$false;plans=$files;indexes=@($indexes|Sort-Object);passed=$true})
  }finally{if($null -ne $transaction){$transaction.Rollback();$transaction.Dispose()};$connection.Dispose()}
 }
}
[IO.File]::WriteAllText((Join-Path $directory 'results.json'),($receipts|ConvertTo-Json -Depth 8),$utf8)
'ESTIMATED_CONSUMERS_PROVEN='+$receipts.Count

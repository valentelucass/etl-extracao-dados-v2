param([ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$ProofId='B55_COMPARE_01',
 [ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$RuntimeProof='B55_SMOKE_03',[ValidateSet('Synthetic')][string]$Mode='Synthetic')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
$directory=Join-Path $root ('target/bloco55/comparison/'+$ProofId)
if(Test-Path $directory){throw 'EXISTING_EVIDENCE_PRESERVE'}
[void][IO.Directory]::CreateDirectory($directory)
$contract=Join-Path $root 'docs/catalogos/comparacao-bloco55/contract.json'
$contractHash=(Get-FileHash $contract).Hash.ToLowerInvariant()
$sql=Get-Content (Join-Path $root 'database/validation/bloco55-runtime-comparison.sql') -Raw
Import-Module (Join-Path $PSScriptRoot 'Bloco55Budget.psm1') -Force
$receipts=[Collections.Generic.List[object]]::new()
foreach($template in @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $file=Join-Path $root ('target/bloco55/runtime/'+$RuntimeProof+'/'+$RuntimeProof+'_'+$template+'_RUN.request.json')
 $request=Get-Content $file -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 Add-Bloco55Reservation ($ProofId+'_'+$template) 'SQL_INDEPENDENT_ORACLE_TEMP_ROLLBACK'|Out-Null
 $connection=[Data.SqlClient.SqlConnection]::new('Server=localhost;Initial Catalog=ETL_SISTEMA_V2_SHADOW;Integrated Security=SSPI;Encrypt=True;TrustServerCertificate=False;Connect Timeout=10;Pooling=False;Application Name=B55BoundedComparison')
 $transaction=$null
 try{
  $connection.Open();$transaction=$connection.BeginTransaction();$query=$connection.CreateCommand();$query.Transaction=$transaction;$query.CommandTimeout=30;$query.CommandText=$sql
  [void]$query.Parameters.Add('@template',[Data.SqlDbType]::NVarChar,32);$query.Parameters['@template'].Value=$template
  [void]$query.Parameters.Add('@execution',[Data.SqlDbType]::UniqueIdentifier);$query.Parameters['@execution'].Value=[guid]$request.executionId
  [void]$query.Parameters.Add('@date',[Data.SqlDbType]::Date);$query.Parameters['@date'].Value=[DateTime]::ParseExact($request.businessStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
  foreach($pair in @(@('@start','start'),@('@end','endExclusive'))){[void]$query.Parameters.Add($pair[0],[Data.SqlDbType]::DateTime2);$query.Parameters[$pair[0]].Value=[DateTimeOffset]::Parse($request[$pair[1]]).UtcDateTime}
  [void]$query.Parameters.Add('@reference',[Data.SqlDbType]::BigInt);$query.Parameters['@reference'].Value=if($request.Contains('referenceReleaseId')){[long]$request.referenceReleaseId}else{[DBNull]::Value}
  $reader=$query.ExecuteReader();$output=[Text.StringBuilder]::new()
  try{while($reader.Read()){[void]$output.Append($reader.GetString(0));if($utf8.GetByteCount($output.ToString()) -gt 16384){throw 'COMPARISON_OUTPUT_LIMIT'}}}finally{$reader.Dispose();$query.Dispose()}
  $rows=@($output.ToString()|ConvertFrom-Json)
  if($rows.Count -ne 6){throw 'EXACT_SIX_SUMMARIES_REQUIRED'}
  $receipt=[ordered]@{template=$template;layer='SQL_SERVER_OWN_PUBLISHED_OUTPUT_TEMP_ORACLE_ROLLBACK';contractVersion='bloco55-runtime-output-v1';contractSha256=$contractHash;requestSha256=(Get-FileHash $file).Hash.ToLowerInvariant();provenance='INDEPENDENT_SYNTHETIC_EXPECTATION';maximumSeconds=30;maximumRoots=64;maximumChildren=128;realParity=$false;cases=$rows;passed=@($rows|Where-Object {-not $_.passed}).Count -eq 0}
  $receipts.Add($receipt);[IO.File]::WriteAllText((Join-Path $directory ($template+'.json')),($receipt|ConvertTo-Json -Depth 8),$utf8)
 }finally{if($null -ne $transaction){$transaction.Rollback();$transaction.Dispose()};$connection.Dispose()}
}
[IO.File]::WriteAllText((Join-Path $directory 'results.json'),($receipts|ConvertTo-Json -Depth 8),$utf8)
$receipts|Select-Object template,passed|ConvertTo-Json -Compress
if(@($receipts|Where-Object {-not $_.passed}).Count){throw 'COMPARISON_NOT_PROVEN'}

#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$source=Join-Path $root 'docs/catalogos/raster-contrato-local'
$fixture=Join-Path $root ('target/bloco56-raster/contract-guards/'+[guid]::NewGuid().ToString('N'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$fingerprint=Get-Content (Join-Path $source 'fingerprints.json') -Raw|ConvertFrom-Json
foreach($path in @($fingerprint.files.path)+@('fingerprints.json')){
 $dest=Join-Path $fixture $path
 [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
 [IO.File]::Copy((Join-Path $source $path),$dest,$false)
}
$validator=Join-Path $PSScriptRoot 'Test-RasterContractCatalog.ps1'
& $validator -CatalogRoot $fixture|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$Id,[string]$Path,[scriptblock]$Mutate,[string]$Expected){
 $file=Join-Path $fixture $Path;$fp=Join-Path $fixture 'fingerprints.json'
 $original=[IO.File]::ReadAllBytes($file);$originalFp=[IO.File]::ReadAllBytes($fp)
 try{
  $value=$utf8.GetString($original)|ConvertFrom-Json -AsHashtable -Depth 40
  & $Mutate $value
  [IO.File]::WriteAllText($file,($value|ConvertTo-Json -Depth 40),$utf8)
  $f=$utf8.GetString($originalFp)|ConvertFrom-Json
  ($f.files|Where-Object path -CEQ $Path).sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
  [IO.File]::WriteAllText($fp,($f|ConvertTo-Json -Depth 10),$utf8)
  $reason='NOT_REJECTED';try{& $validator -CatalogRoot $fixture|Out-Null}catch{$reason=$_.Exception.Message}
  if($reason -notmatch $Expected){throw ('RAS_GUARD_FAILED_'+$Id+'_'+$reason)}
  $results.Add([ordered]@{id=$Id;passed=$true;reason=$reason;layer='ISOLATED_OFFLINE_MUTANT_WITH_REFRESHED_DATA_HASH'})
 }finally{[IO.File]::WriteAllBytes($file,$original);[IO.File]::WriteAllBytes($fp,$originalFp)}
}
Reject 'LOCAL_DECISION_AS_NETWORK_AUTH' 'decisao.json' {param($v)$v.remoteExecutionAuthorized=$true} 'RAS_FALSE_AUTHORITY'
Reject 'INVENTED_PRODUCTION_OWNER' 'decisao.json' {param($v)$v.nominalProductionOwner='SYNTHETIC_INVENTED_OWNER'} 'RAS_CONSUMER_SCOPE'
Reject 'CREDENTIAL_AUTO_ENABLE' 'contrato.json' {param($v)$v.policy.automaticEnableFromCredentialPresence=$true} 'RAS_FALSE_AUTHORITY'
Reject 'POSITION_AS_IDENTITY' 'contrato.json' {param($v)$v.policy.positionalChildIdentityAllowed=$true} 'RAS_FALSE_AUTHORITY'
Reject 'SHORT_AS_COMPLETE' 'contrato.json' {param($v)$v.policy.shortBatchProvesCompleteness=$true} 'RAS_FALSE_AUTHORITY'
Reject 'EMPTY_AS_COMPLETE' 'contrato.json' {param($v)$v.policy.emptyBatchProvesCompleteness=$true} 'RAS_FALSE_AUTHORITY'
Reject 'INVENTED_PROVIDER_COMPLETENESS' 'contrato.json' {param($v)$v.aspects.completeness.classification='PROVEN'} 'RAS_PROVIDER_GUARANTEE'
Reject 'INVENTED_CURSOR' 'contrato.json' {param($v)$v.request.cursorParameter='SYNTHETIC_CURSOR'} 'RAS_PAGINATION_INVENTED'
Reject 'IDENTITY_FROM_DTO' 'identidade-pendente.json' {param($v)$v.identityAccepted=$true} 'RAS_FALSE_AUTHORITY'
Reject 'SHADOW_WITHOUT_IDENTITY' 'contrato.json' {param($v)$v.capabilities.shadowUpsert='ALLOWED'} 'RAS_SHADOW_GATE'
Reject 'MISSING_ROOT_AS_SUCCESS' 'fixtures/respostas.synthetic.json' {param($v)$v.cases[3].expected='TERMINAL_UNVERIFIED';$v.cases[3].expectedRows=0} 'RAS_RESPONSE_CASE_MISSING_ROOT'
Reject 'CAP_AS_COMPLETE' 'fixtures/limites.synthetic.json' {param($v)$v.cases[3].expected='TERMINAL_UNVERIFIED'} 'RAS_LIMIT_CASE_CAP_MINIMUM'
& $validator -CatalogRoot $fixture|Out-Null
if($results.Count -ne 12){throw 'RAS_GUARD_COUNT'}
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 8),$utf8)
'RASTER_CONTRACT_TWELVE_CONTRAPROOFS_PASS'

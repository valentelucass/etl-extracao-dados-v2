#Requires -Version 7.5
[CmdletBinding()]
param([string]$CatalogRoot,[switch]$IncludeLegacyEvidence,[switch]$AsResult)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if(-not $CatalogRoot){$CatalogRoot=Join-Path $root 'docs/catalogos/raster-contrato-local'}
$CatalogRoot=[IO.Path]::GetFullPath($CatalogRoot)
if(-not $CatalogRoot.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'RAS_CATALOG_SCOPE'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
function File([string]$Path){
 if($Path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $Path.Contains('..') -or $Path.StartsWith('/')){throw 'RAS_PATH'}
 $file=Join-Path $CatalogRoot $Path
 $node=Get-Item -LiteralPath $file
 while($node.FullName -cne $root){
  if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'RAS_REPARSE'}
  $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
 }
 if((Get-Item -LiteralPath $file).Length -gt 2MB){throw 'RAS_FILE_BOUND'}
 return $file
}
function Json([string]$Path){return $utf8.GetString([IO.File]::ReadAllBytes((File $Path)))|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String}
function Exact($Actual,$Expected,[string]$Reason){if(@($Actual).Count -ne @($Expected).Count -or (@($Actual|Sort-Object -Unique)-join '|') -cne (@($Expected|Sort-Object -Unique)-join '|')){throw $Reason}}
function False($Value){if($Value -isnot [bool] -or $Value){throw 'RAS_FALSE_AUTHORITY_OR_ACCEPTANCE'}}
function Limit-Outcome([int]$Rows,[int]$Days){
 if($Rows -lt 0 -or $Days -lt 1){return 'INVALID_BOUND'}
 if($Rows -ge 500){if($Days -eq 1){return 'BLOCKED_MINIMUM_WINDOW_CAP'};return 'REPARTITION_REQUIRED_CONTRACT_BOUND'}
 return 'TERMINAL_UNVERIFIED'
}
# Catalog-only interpretation, never a runtime parser or source traversal.
function Response-Outcome($Response){
 if($Response -isnot [Collections.IDictionary]){return @{outcome='SHAPE_BLOCKED';rows=$null}}
 foreach($key in $Response.Keys){if($key -imatch '^(error|erro)$' -and $null -ne $Response[$key]){return @{outcome='SOURCE_ERROR';rows=$null}}}
 $resultKeys=@($Response.Keys|Where-Object{$_ -ieq 'result'})
 if($resultKeys.Count -ne 1 -or $null -eq $Response[$resultKeys[0]]){return @{outcome='SHAPE_BLOCKED';rows=$null}}
 $result=$Response[$resultKeys[0]]
 $wrappers=if($result -is [Collections.IDictionary]){,@($result)}elseif($result -is [array]){,@($result)}else{,@()}
 if($wrappers.Count -eq 0){return @{outcome='SHAPE_BLOCKED';rows=$null}}
 $rows=0
 foreach($wrapper in $wrappers){
  if($wrapper -isnot [Collections.IDictionary]){return @{outcome='SHAPE_BLOCKED';rows=$null}}
  $keys=@($wrapper.Keys|Where-Object{$_ -ieq 'Viagens'})
  if($keys.Count -ne 1 -or $null -eq $wrapper[$keys[0]]){return @{outcome='SHAPE_BLOCKED';rows=$null}}
  $trips=$wrapper[$keys[0]]
  if($trips -is [Collections.IDictionary]){$rows++}
  elseif($trips -is [array]){
   foreach($trip in $trips){if($trip -isnot [Collections.IDictionary]){return @{outcome='SHAPE_BLOCKED';rows=$null}};$rows++}
  }else{return @{outcome='SHAPE_BLOCKED';rows=$null}}
 }
 return @{outcome=(Limit-Outcome $rows 1);rows=$rows}
}
$fingerprint=Json 'fingerprints.json'
$required=@('decisao.json','contrato.json','campos.json','identidade-pendente.json','evidencias/legado.json','fixtures/respostas.synthetic.json','fixtures/limites.synthetic.json','fixtures/reordenacao.synthetic.json')
Exact @($fingerprint.files|ForEach-Object{$_['path']}) $required 'RAS_EXACT_DATA_FILES'
foreach($e in $fingerprint.files){if($e.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath (File $e.path)).Hash.ToLowerInvariant() -cne $e.sha256){throw 'RAS_HASH'}}
$decision=Json 'decisao.json';$c=Json 'contrato.json';$identity=Json 'identidade-pendente.json';$fields=Json 'campos.json';$legacy=Json 'evidencias/legado.json'
if($decision.task -cne 'V2-034a' -or $decision.decision -cne 'MANTER' -or $decision.authority -cne 'USER_DELEGATION_CURRENT_CONVERSATION' -or $decision.ownerRole -cne 'OWNER_DO_PROJETO' -or $decision.instruction -cne 'vc que decide, preciso continuar'){throw 'RAS_DECISION_AUTHORITY'}
foreach($name in @('enabledByDefault','remoteExecutionAuthorized','sqlExecutionAuthorized','productionChangeAuthorized','runtimeBudgetAdopted')){False $decision[$name]}
if($decision.consumers.Count -ne 3 -or $null -ne $decision.nominalProductionOwner){throw 'RAS_CONSUMER_SCOPE'}
foreach($consumer in $decision.consumers){False $consumer.currentProductionUsageProven;if([string]::IsNullOrWhiteSpace($consumer.impact)){throw 'RAS_CONSUMER_IMPACT'}}
Exact $decision.rules @('RAS-01','RAS-02','RAS-03','RAS-04','RAS-05','PUB-08') 'RAS_ORIGINAL_RULES'
Exact $decision.specificLocalAuthorization @('FORMALIZAR_V2_025C_OFFLINE','INVESTIGAR_V2_009C_COM_ARTEFATOS_LOCAIS','IMPLEMENTAR_TESTAR_LOCAL_SOMENTE_COM_DEPENDENCIAS_COMPROVADAS') 'RAS_LOCAL_AUTHORIZATION'
if($c.task -cne 'V2-025c' -or $c.overallClassification -cne 'TRANSITIONAL' -or $c.nextGate -cne 'V2-009c'){throw 'RAS_CONTRACT_SCOPE'}
Exact $c.classificationVocabulary @('PROVEN','TRANSITIONAL','ABSENT','BUSINESS_DECISION_PENDING') 'RAS_CLASSIFICATION_VOCABULARY'
Exact @($c.aspects.Keys) @('endpoint','transport','authentication','metadata','responseRoot','filters','temporalTranslation','ordering','per','rootIdentity','childIdentity','pagination','timezone','limitsErrors','freshnessSnapshot','sentinelDuration','atomicity','completeness') 'RAS_ASPECTS'
foreach($a in $c.aspects.Values){if($a.classification -cnotin $c.classificationVocabulary -or [string]::IsNullOrWhiteSpace($a.contract) -or [string]::IsNullOrWhiteSpace($a.evidence)){throw 'RAS_ASPECT_EVIDENCE'}}
foreach($name in @('metadata','ordering','per','completeness')){if($c.aspects[$name].classification -cne 'ABSENT'){throw 'RAS_PROVIDER_GUARANTEE_INVENTED'}}
foreach($name in @('temporalTranslation','timezone','freshnessSnapshot')){if($c.aspects[$name].classification -cne 'BUSINESS_DECISION_PENDING'){throw 'RAS_TEMPORAL_GUARANTEE_INVENTED'}}
foreach($name in @('enabledByDefault','automaticEnableFromCredentialPresence','productionDefault','hostTimezoneAllowed','implicitCurrentDateAllowed','positionalChildIdentityAllowed','shortBatchProvesCompleteness','emptyBatchProvesCompleteness')){False $c.policy[$name]}
if($c.policy.legacyAlertCap -ne 500 -or $c.policy.maximumDurationMinutes -ne 43200 -or $c.policy.remoteCalls -ne 0 -or $c.policy.sqlOperations -ne 0){throw 'RAS_LIMITS'}
if($c.request.method -cne 'POST' -or $c.request.relativePath -cne '/datasnap/rest/TWebService/%22getEventoFimViagem%22' -or $c.request.dateFormat -cne 'yyyy-MM-dd' -or $c.request.contentType -cne 'application/json'){throw 'RAS_WIRE_CONTRACT'}
Exact $c.request.bodyKeys @('Ambiente','Login','Senha','TipoRetorno','DataInicial','DataFinal','StatusViagem') 'RAS_BODY_FIELDS'
foreach($name in @('orderingParameter','pageParameter','perParameter','cursorParameter')){if($null -ne $c.request[$name]){throw 'RAS_PAGINATION_INVENTED'}}
False $c.capabilities.identityAccepted;False $c.capabilities.runtimeEnabled
if($c.capabilities.shadowUpsert -cne 'BLOCKED_V2_009C_AND_V2_034B'){throw 'RAS_SHADOW_GATE'}
foreach($name in @('sweep','deactivation','cutover')){if($c.capabilities[$name] -cne 'BLOCKED_NO_COMPLETENESS_PROOF'){throw 'RAS_COMPLETENESS_GATE'}}
foreach($name in @('identityAccepted','currentProviderGuarantee','sampleUniquenessIsProof','legacyPrimaryKeyIsProof','positionFallbackAccepted')){False $identity[$name]}
if($identity.task -cne 'V2-009c' -or $null -ne $identity.canonicalBinding -or $identity.rootMissing.Count -ne 4 -or $identity.childMissing.Count -ne 4 -or $identity.nextImplementation -cne 'V2-034b_BLOCKED' -or $identity.remoteCalls -ne 0){throw 'RAS_IDENTITY_HOLD'}
if($fields.provenance -cne 'LEGACY_DTO_DECLARATIONS_NOT_PROVIDER_SCHEMA' -or $fields.fields.Count -ne 51){throw 'RAS_FIELD_CATALOG'}
foreach($f in $fields.fields){if($f.wireTypeClassification -cne 'TRANSITIONAL' -or -not $f.coercionIsNotWireTypeProof -or $f.aliases.Count -lt 1){throw 'RAS_WIRE_TYPE_INFERENCE'}}
if($legacy.files.Count -ne 14 -or $legacy.remoteCalls -ne 0 -or $legacy.credentialsRead -ne 0 -or $legacy.rawResponsesRead -ne 0){throw 'RAS_LEGACY_EVIDENCE_SCOPE'}
foreach($e in $legacy.files){False $e.currentProviderGuarantee;if($e.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'RAS_LEGACY_HASH'}}
if($IncludeLegacyEvidence){
 $legacyRoot=[IO.Path]::GetFullPath((Join-Path $root '../etl-extracao-dados'))
 foreach($e in $legacy.files){
  if($e.path -cnotmatch '^(src/main/java/br/com/extrator/|database/(tabelas|views)/|docs/modelagem/)[A-Za-z0-9_./-]+$' -or $e.path.Contains('..')){throw 'RAS_LEGACY_PATH'}
  $file=Join-Path $legacyRoot $e.path
  if((Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -cne $e.sha256){throw 'RAS_LEGACY_DRIFT'}
 }
}
$responses=Json 'fixtures/respostas.synthetic.json';$limits=Json 'fixtures/limites.synthetic.json';$reorder=Json 'fixtures/reordenacao.synthetic.json'
foreach($fixture in @($responses,$limits)){if($fixture.provenance -cne 'SYNTHETIC_ONLY_NOT_PROVIDER_EVIDENCE'){throw 'RAS_FIXTURE_PROVENANCE'}}
if($responses.cases.Count -ne 8 -or $limits.cases.Count -ne 7){throw 'RAS_CASE_COUNT'}
foreach($case in $responses.cases){$actual=Response-Outcome $case.response;if($actual.outcome -cne $case.expected -or $actual.rows -ne $case.expectedRows){throw ('RAS_RESPONSE_CASE_'+$case.id)}}
foreach($case in $limits.cases){if((Limit-Outcome $case.rows $case.days) -cne $case.expected){throw ('RAS_LIMIT_CASE_'+$case.id)}}
if($reorder.provenance -cne 'SYNTHETIC_COUNTEREXAMPLE_NOT_OBSERVED_COLLISION' -or $reorder.original.Count -ne 2 -or $reorder.reordered.Count -ne 2){throw 'RAS_REORDER_FIXTURE'}
False $reorder.identityProven
if($reorder.original[0].CodigoCliente -ceq $reorder.reordered[0].CodigoCliente -or $reorder.original[0].CodigoCliente -cne $reorder.reordered[1].CodigoCliente){throw 'RAS_POSITION_COUNTEREXAMPLE'}
$result=[ordered]@{passed=$true;layer='OFFLINE_CATALOG_POLICY_ONLY';aspects=18;legacyFields=51;responseCases=8;limitCases=7;identityCounterexamples=1;remoteCalls=0;sqlOperations=0;runtimeImplemented=$false;identityAccepted=$false}
if($AsResult){return $result}
'RASTER_CONTRACT_PASS_18_ASPECTS_51_FIELDS_16_OFFLINE_CASES_IDENTITY_BLOCKED'

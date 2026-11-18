param([Parameter(Mandatory)][string]$OutputDirectory,[string]$WorkerPath,[string]$LegacyFixturePath)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$worker=if($WorkerPath){[IO.Path]::GetFullPath($WorkerPath)}else{Join-Path $repo 'src\main\resources\independent-export\RootWireWorker.ps1'}
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($worker,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'FATURAS_CLIENTE_WIRE_PARSE'}
foreach($name in @('Need-Wire','Read-Pin','Read','Fresh-Metadata','Assert-FaturasClienteWireRequest')){
 $fn=@($ast.FindAll({param($n)$n-is[Management.Automation.Language.FunctionDefinitionAst]-and$n.Name-ceq$name},$false))
 if($fn.Count-ne1){throw 'FATURAS_CLIENTE_EXACT_WIRE_FUNCTION'}
 . ([scriptblock]::Create($fn[0].Extent.Text))
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'FATURAS_CLIENTE_FRESH_FIXTURE_DIRECTORY'}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$now=[DateTimeOffset]::UtcNow;$actor='00000000-0000-4000-8000-000000000001';$origin='https://capture.invalid'
$base=@{schema='ROOT_FATURAS_CLIENTE_FINITE_SOURCE_GRANT_V1';approved=$true;entity='FATURAS_POR_CLIENTE';reportId=4924;profile='FATURAS_CLIENTE_4924_SERVICE_AT_NATIVE52_V1';filterScopePolicy='SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1';updatedAtFilter='';executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100;createdUtc=$now.AddSeconds(-1).ToString('o');expiresUtc=$now.AddSeconds(60).ToString('o');attemptId='00000000-0000-4000-8000-000000000002';periodStart='2026-10-12';periodEnd='2026-10-18';zone='America/Sao_Paulo'}
$filterNames=@('freights[service_at]','scopes[by_updated_at]','scopes[from_controller]','freights[payer_id]','scopes[from_accounting_credit_status]','fit_accounting_credit[due_date]','fit_accounting_credit[customer_id]','fit_accounting_credit[paid]','fit_accounting_credit[issue_date]','fit_freight_cte[corporation_sequence_number]','freights[corporation_sequence_number]','scopes[from_seller]','freights[status]','freights[type]','freights[emission_type]','freights[courtesy]','scopes[substituted]','scopes[with_delivery_receipt]')
$requestBase=@{schema='FATURAS_CLIENTE_INDEPENDENT_REQUEST_V1';profile=$base.profile;entity=$base.entity;reportId=4924;attemptId=$base.attemptId;periodStart=$base.periodStart;periodEnd=$base.periodEnd;zone=$base.zone;filters=@{}}
foreach($key in $filterNames){$requestBase.filters[$key]=''}
$requestBase.filters['freights[service_at]']=$base.periodStart+' - '+$base.periodEnd
$data=$origin+'/api/analytics/reports/4924/data?search%5Bfreights%5D%5Bservice_at%5D=2026-10-12%20-%202026-10-18&page=1&per=100'
$cases=@(@{name='info-no-query';url=($origin+'/api/analytics/reports/4924/info');ok=$true})
foreach($page in 1..16){$cases+=@(@{name=('page-'+$page);url=$data.Replace('page=1&',('page='+$page+'&'));ok=$true})}
$cases+=@(
 @{name='single-day';url=$data.Replace('2026-10-18','2026-10-12');ok=$true;changes=@{periodEnd='2026-10-12'};requestFields=@{periodEnd='2026-10-12'};requestFilters=@{'freights[service_at]'='2026-10-12 - 2026-10-12'}},
 @{name='month-boundary';url=$data.Replace('2026-10-12','2026-10-26').Replace('2026-10-18','2026-11-01');ok=$true;changes=@{periodStart='2026-10-26';periodEnd='2026-11-01'};requestFields=@{periodStart='2026-10-26';periodEnd='2026-11-01'};requestFilters=@{'freights[service_at]'='2026-10-26 - 2026-11-01'}},
 @{name='info-query';url=($origin+'/api/analytics/reports/4924/info?x=1')},
 @{name='page17';url=$data.Replace('page=1&','page=17&')},
 @{name='page0';url=$data.Replace('page=1&','page=0&')},
 @{name='page01';url=$data.Replace('page=1&','page=01&')},
 @{name='per50';url=$data.Replace('per=100','per=50')},
 @{name='wrong-period';url=$data.Replace('2026-10-12','2026-10-11')},
 @{name='wrong-origin';url=$data.Replace('capture.invalid','other.invalid')},
 @{name='wrong-report';url=$data.Replace('/4924/','/8636/')},
 @{name='wrong-method';url=$data;method='POST'},
 @{name='body-forbidden';url=$data;body='{}'},
 @{name='wrong-authentication';url=$data;authentication='NONE'},
 @{name='order-by-forbidden';url=($data+'&order_by=service_at%20desc')},
 @{name='empty-order-forbidden';url=($data+'&order_by=')},
 @{name='updated-nonempty-uri';url=($data+'&search%5Bscopes%5D%5Bby_updated_at%5D=2026-10-12%20-%202026-10-18')},
 @{name='updated-empty-uri';url=($data+'&search%5Bscopes%5D%5Bby_updated_at%5D=')},
 @{name='extra-empty-filter';url=($data+'&search%5Bfreights%5D%5Bstatus%5D=')},
 @{name='duplicate-service';url=($data+'&search%5Bfreights%5D%5Bservice_at%5D=2026-10-12%20-%202026-10-18')},
 @{name='swapped-query-order';url=$data.Replace('&page=1&per=100','&per=100&page=1')},
 @{name='query-fragment';url=($data+'#forbidden')},
 @{name='expired';url=$data;changes=@{expiresUtc=$now.AddSeconds(-1).ToString('o')}},
 @{name='unbounded-grant';url=$data;changes=@{expiresUtc=$now.AddSeconds(301).ToString('o')}},
 @{name='string-approval';url=$data;changes=@{approved='true'}},
 @{name='source-drift';url=$data;changes=@{sourceConfigurationSha256=('c'*64)}},
 @{name='code-drift';url=$data;changes=@{codeUnitSha256=('c'*64)}},
 @{name='actor-drift';url=$data;changes=@{executingRootUUID='00000000-0000-4000-8000-000000000003'}},
 @{name='missing-grant';url=$data;missing=$true},
 @{name='grant-pin-drift';url=$data;mutate=$true},
 @{name='eight-days';url=$data;changes=@{periodEnd='2026-10-19'}},
 @{name='inverted';url=$data;changes=@{periodStart='2026-10-19'}},
 @{name='invalid-civil';url=$data;changes=@{periodStart='2026-02-30'}},
 @{name='wrong-zone';url=$data;changes=@{zone='UTC'}},
 @{name='updated-grant-missing';url=$data;removeGrant='updatedAtFilter'},
 @{name='updated-grant-filled';url=$data;changes=@{updatedAtFilter='2026-10-12 - 2026-10-18'}},
 @{name='updated-grant-null';url=$data;changes=@{updatedAtFilter=$null}},
 @{name='updated-grant-space';url=$data;changes=@{updatedAtFilter=' '}},
 @{name='policy-missing';url=$data;removeGrant='filterScopePolicy'},
 @{name='policy-wrong';url=$data;changes=@{filterScopePolicy='ISSUE_DATE_WITH_EXPLICIT_EMPTY_CREATED_AT_V1'}},
 @{name='old-profile';url=$data;changes=@{profile='FATURAS_CLIENTE_4924_OBSERVED26_COMPARISON_V1'}},
 @{name='cap-replay';url=$data;changes=@{schema='ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_GRANT_V1';entity='CONTAS_A_PAGAR';reportId=8636}},
 @{name='request-pin-missing';url=$data;removeGrant='requestPin'},
 @{name='request-top-extra';url=$data;requestFields=@{updatedAtFilter=''}},
 @{name='request-report-string';url=$data;requestFields=@{reportId='4924'}},
 @{name='request-attempt-mismatch';url=$data;requestFields=@{attemptId='00000000-0000-4000-8000-000000000019'}},
 @{name='request-service-mismatch';url=$data;requestFilters=@{'freights[service_at]'='2026-10-13 - 2026-10-18'}}
)
foreach($field in $filterNames|Where-Object{$_-cne'freights[service_at]'}){
 $changes=@{};$changes[$field]='filled';$cases+=@(@{name=('filled-aux-'+$cases.Count);url=$data;requestFilters=$changes})
 $cases+=@(@{name=('missing-aux-'+$cases.Count);url=$data;removeFilter=$field})
}
foreach($cap in @{reportId=4924;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
 $wrong=@{};$wrong[$cap.Key]=[string]$cap.Value;$cases+=@(@{name=('string-cap-'+$cap.Key);url=$data;changes=$wrong})
}
foreach($key in @('pageIndex','effectiveHTTPMaximum','pageScopePolicy')){$changes=@{};$changes[$key]=1;$cases+=@(@{name=('raw-scope-'+$key);url=$data;changes=$changes})}
$cases+=@(@{name='source212';url=$data;changes=@{SourceSeconds=212}},@{name='http18';url=$data;changes=@{HTTPMaximum=18}},@{name='boolean-per';url=$data;changes=@{per=$true}},@{name='sql1';url=$data;changes=@{SQL=1}})
$localPassed=0
foreach($case in $cases){
 $grant=$base.Clone();if($case.Contains('changes')){foreach($key in $case.changes.Keys){$grant[$key]=$case.changes[$key]}}
 $req=$requestBase.Clone();$req.filters=$requestBase.filters.Clone()
 if($case.Contains('requestFields')){foreach($key in $case.requestFields.Keys){$req[$key]=$case.requestFields[$key]}}
 if($case.Contains('requestFilters')){foreach($key in $case.requestFilters.Keys){$req.filters[$key]=$case.requestFilters[$key]}}
 if($case.Contains('removeFilter')){$req.filters.Remove($case.removeFilter)}
 $p=Join-Path $OutputDirectory ('request-'+$case.name+'.json');$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($req|ConvertTo-Json -Depth 15 -Compress));[IO.File]::WriteAllBytes($p,$bytes)
 $grant.requestPin=@{path=$p;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()}
 if($case.Contains('removeGrant')){$grant.Remove($case.removeGrant)}
 $p=Join-Path $OutputDirectory ('grant-'+$case.name+'.json');$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($grant|ConvertTo-Json -Depth 15 -Compress));[IO.File]::WriteAllBytes($p,$bytes)
 $ctx=@{executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);apiOrigin=$origin;faturasClienteGrantPin=@{path=$p;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()}}
 if($case.Contains('missing')){$ctx.Remove('faturasClienteGrantPin')};if($case.Contains('mutate')){[IO.File]::AppendAllText($p,' ')}
 $msg=@{method=$(if($case.Contains('method')){$case.method}else{'GET'});authentication=$(if($case.Contains('authentication')){$case.authentication}else{'DATA_EXPORT'});deadline=$now.AddSeconds(30).ToString('o')}
 if($case.Contains('body')){$msg.jsonBody=$case.body}
 $accepted=$false;try{$null=Assert-FaturasClienteWireRequest $ctx $msg ([Uri]$case.url) $now;$accepted=$true}catch{}
 if($accepted-ne($case.Contains('ok')-and$case.ok)){throw ('FATURAS_CLIENTE_WIRE_SCOPE_FIXTURE_'+$case.name)}
 $localPassed++
}
# Execute the predecessor's complete cases unchanged; its code must match the CAP full-unit pin.
$baselinePath='C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade\outputs\independent-export-formatted-localizador0752-build150-compile\offline-unit.PREPARED.json'
if((Get-Item -LiteralPath $baselinePath).Length-ne728475-or(Get-FileHash -LiteralPath $baselinePath -Algorithm SHA256).Hash-cne'5E760E9265AD1DD424B01904585FE74F6C455C0954E95A2C8A7F29BC376A7ACB'){throw 'FATURAS_CLIENTE_FROZEN_PREDECESSOR_UNIT_PIN'}
$baseline=Get-Content -LiteralPath $baselinePath -Raw|ConvertFrom-Json -AsHashtable
$old=@($baseline.inputPins|Where-Object file -ceq 'scripts/validation/Test-LocalizadorWireScope.ps1')
if($old.Count-ne1){throw 'FATURAS_CLIENTE_EXACT_LEGACY_FIXTURE_REQUIRED'}
$legacy=if($LegacyFixturePath){[IO.Path]::GetFullPath($LegacyFixturePath)}else{$old[0].path}
$legacyPin=@{path=$legacy;bytes=$old[0].bytes;sha256=$old[0].sha256.ToLowerInvariant()};$null=Read-Pin $legacyPin 262144
$legacyResult=(& $legacy -OutputDirectory (Join-Path $OutputDirectory 'legacy-cap-full') -WorkerPath $worker)|ConvertFrom-Json -AsHashtable
$null=Read-Pin $legacyPin 262144
if($legacyResult.schema-cne'LOCALIZADOR_WIRE_SCOPE_SYNTHETIC_FIXTURES_V1'-or-not$legacyResult.syntheticOnly-or$legacyResult.passed-le0-or$legacyResult.Source-ne0-or$legacyResult.HTTP-ne0-or$legacyResult.SQL-ne0){throw 'FATURAS_CLIENTE_ALL_LEGACY_FIXTURES_REQUIRED'}
[ordered]@{schema='FATURAS_CLIENTE_WIRE_SCOPE_SYNTHETIC_FIXTURES_V1';passed=($localPassed+$legacyResult.passed);faturasClientePassed=$localPassed;predecessorPassed=$legacyResult.passed;predecessorFixturePin=$legacyPin;allPredecessorCasesExecuted=$true;Source=0;HTTP=0;SQL=0;CIM=0;credentials=0;syntheticOnly=$true;filterScopePolicy='SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1';updatedAtFilter='';orderByOmitted=$true;rootIdentityAssumed=$false}|ConvertTo-Json -Depth 6

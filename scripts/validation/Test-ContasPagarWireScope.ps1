param([Parameter(Mandatory)][string]$OutputDirectory,[string]$WorkerPath)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$worker=if($WorkerPath){[IO.Path]::GetFullPath($WorkerPath)}else{Join-Path $repo 'src\main\resources\independent-export\RootWireWorker.ps1'}
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($worker,[ref]$tokens,[ref]$errors)
if(@($errors).Count){throw 'CONTAS_PAGAR_WIRE_SOURCE_PARSE'}
foreach($name in @('Need-Wire','Read-Pin','Read','Fresh-Metadata','Assert-ContasPagarWireRequest','Assert-ManifestosWireRequest','Assert-CotacoesWireRequest','Assert-InventarioWireRequest','Assert-SinistrosWireRequest','Get-CurlTimeArgument','Publish-WireResponseMarker')){
 $found=@($ast.FindAll({param($node)$node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq$name},$false))
 if($found.Count-ne1){throw 'CONTAS_PAGAR_WIRE_EXACT_FUNCTION'}
 . ([scriptblock]::Create($found[0].Extent.Text))
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'CONTAS_PAGAR_WIRE_FIXTURES_FRESH_DIRECTORY'}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$now=[DateTimeOffset]::UtcNow
$actor='00000000-0000-4000-8000-000000000001'
$base=@{schema='ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_GRANT_V1';approved=$true;entity='CONTAS_A_PAGAR';reportId=8636;profile='CONTAS_A_PAGAR_8636_ISSUE_DATE_NATIVE28_V1';executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100;createdUtc=$now.AddSeconds(-1).ToString('o');expiresUtc=$now.AddSeconds(60).ToString('o');attemptId='00000000-0000-4000-8000-000000000002';periodStart='2026-10-12';periodEnd='2026-10-18';zone='America/Sao_Paulo';filterScopePolicy='ISSUE_DATE_WITH_EXPLICIT_EMPTY_CREATED_AT_V1';createdAtFilter=''}
$origin='https://capture.invalid'
$requestBase=@{schema='CONTAS_A_PAGAR_INDEPENDENT_REQUEST_V1';profile=$base.profile;entity=$base.entity;reportId=8636;attemptId=$base.attemptId;periodStart=$base.periodStart;periodEnd=$base.periodEnd;zone=$base.zone;filters=@{}}
foreach($name in @('accounting_debits[accounting_planning_management_id]','accounting_debits[corporation_id]','accounting_debits[created_at]','accounting_debits[document]','accounting_debits[issue_date]','accounting_debits[paid]','accounting_debits[receiver_id]','accounting_debits[type]','scopes[from_competence_period_string]')){$requestBase.filters[$name]=''}
$requestBase.filters['accounting_debits[issue_date]']=$base.periodStart+' - '+$base.periodEnd
$data=$origin+'/api/analytics/reports/8636/data?search%5Baccounting_debits%5D%5Bissue_date%5D=2026-10-12%20-%202026-10-18&page=1&per=100&order_by=issue_date%20desc'
$cases=@(
 @{name='info';url=($origin+'/api/analytics/reports/8636/info');ok=$true},
 @{name='page1';url=$data;ok=$true},
 @{name='page16';url=$data.Replace('page=1&','page=16&');ok=$true},
 @{name='extra-info-filter';url=($origin+'/api/analytics/reports/8636/info?x=1')},
 @{name='page17';url=$data.Replace('page=1&','page=17&')},
 @{name='wrong-per';url=$data.Replace('per=100','per=101')},
 @{name='wrong-period';url=$data.Replace('2026-10-12','2026-10-11')},
 @{name='extra-filter';url=($data+'&search%5Baccounting_debits%5D%5Bpaid%5D=true')},
 @{name='wrong-origin';url=$data.Replace('capture.invalid','elsewhere.invalid')},
 @{name='wrong-template';url=$data.Replace('/8636/','/6389/')},
 @{name='post';url=$data;method='POST'},
 @{name='body';url=$data;body='{}'},
 @{name='expired';url=$data;changes=@{expiresUtc=$now.AddSeconds(-1).ToString('o')}},
 @{name='unbounded-window';url=$data;changes=@{expiresUtc=$now.AddSeconds(301).ToString('o')}},
 @{name='string-approval';url=$data;changes=@{approved='true'}},
 @{name='budget18';url=$data;changes=@{HTTPMaximum=18}},
 @{name='source-drift';url=$data;changes=@{sourceConfigurationSha256=('c'*64)}},
 @{name='code-drift';url=$data;changes=@{codeUnitSha256=('c'*64)}},
 @{name='actor-drift';url=$data;changes=@{executingRootUUID='00000000-0000-4000-8000-000000000003'}},
 @{name='missing-grant';url=$data;missing=$true},
 @{name='pin-drift';url=$data;mutate=$true}
)
foreach($cap in @{reportId=8636;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
 $wrong=@{};$wrong[$cap.Key]=[string]$cap.Value
 $cases+=@(@{name=('string-cap-'+$cap.Key);url=$data;changes=$wrong})
}
$cases+=@(@{name='eight-days';url=$data;changes=@{periodEnd='2026-10-19'}})
$created='search%5Baccounting_debits%5D%5Bcreated_at%5D=2026-10-12%20-%202026-10-18'
$cases+=@(
 @{name='created-uri-nonempty-refused';url=($data+'&'+$created)},
 @{name='created-uri-empty-refused';url=($data+'&search%5Baccounting_debits%5D%5Bcreated_at%5D=')},
 @{name='created-grant-filled-refused';url=$data;changes=@{createdAtFilter='2026-10-12 - 2026-10-18'}},
 @{name='created-grant-space-refused';url=$data;changes=@{createdAtFilter=' '}},
 @{name='created-grant-null-refused';url=$data;changes=@{createdAtFilter=$null}},
 @{name='created-grant-missing-refused';url=$data;removeGrantField='createdAtFilter'},
 @{name='policy-missing-refused';url=$data;removeGrantField='filterScopePolicy'},
 @{name='policy-other-refused';url=$data;changes=@{filterScopePolicy='BOTH_DATE_FILTERS'}},
 @{name='policy-boolean-refused';url=$data;changes=@{filterScopePolicy=$true}},
 @{name='old-both-filter-profile-refused';url=$data;changes=@{profile='CONTAS_A_PAGAR_8636_COMPARISON_V1'}},
 @{name='empty-extra-filter';url=($data+'&search%5Baccounting_debits%5D%5Bdocument%5D=')},
 @{name='omit-order-by';url=$data.Replace('&order_by=issue_date%20desc','')},
 @{name='wrong-order-by';url=$data.Replace('order_by=issue_date%20desc','order_by=created_at%20desc')},
 @{name='order-by-asc';url=$data.Replace('order_by=issue_date%20desc','order_by=issue_date%20asc')},
 @{name='order-by-before-pages';url=$data.Replace('&page=1&per=100&order_by=issue_date%20desc','&order_by=issue_date%20desc&page=1&per=100')},
 @{name='legacy-grant-replayed-as-cap';url=$data;changes=@{schema='ROOT_SINISTROS_FINITE_SOURCE_GRANT_V1';entity='SINISTROS';reportId=6392;profile='SINISTROS_6392_NATIVE44_V1'}},
 @{name='boolean-per';url=$data;changes=@{per=$true}},
 @{name='sql-one';url=$data;changes=@{SQL=1}},
 @{name='inverted-period';url=$data;changes=@{periodStart='2026-10-19'}},
 @{name='invalid-civil';url=$data;changes=@{periodStart='2026-02-30'}},
 @{name='query-fragment';url=($data+'#not-allowed')}
)
$cases+=@(
 @{name='request-missing-created-key';url=$data;removeRequestFilter='accounting_debits[created_at]'},
 @{name='request-created-filled';url=$data;requestFilterChanges=@{'accounting_debits[created_at]'='2026-10-12 - 2026-10-18'}},
 @{name='request-created-null';url=$data;requestFilterChanges=@{'accounting_debits[created_at]'=$null}},
 @{name='request-created-space';url=$data;requestFilterChanges=@{'accounting_debits[created_at]'=' '}},
 @{name='request-other-filter-filled';url=$data;requestFilterChanges=@{'accounting_debits[paid]'='true'}},
 @{name='request-pin-missing';url=$data;removeGrantField='requestPin'},
 @{name='request-old-profile';url=$data;requestFieldChanges=@{profile='CONTAS_A_PAGAR_8636_COMPARISON_V1'}},
 @{name='request-attempt-mismatch';url=$data;requestFieldChanges=@{attemptId='00000000-0000-4000-8000-000000000019'}},
 @{name='request-issue-mismatch';url=$data;requestFilterChanges=@{'accounting_debits[issue_date]'='2026-10-13 - 2026-10-18'}}
)
$passed=0
foreach($field in @('pageIndex','effectiveHTTPMaximum','pageScopePolicy')){
 $changes=@{};$changes[$field]=if($field-ceq'pageScopePolicy'){'EXACT_INFO_THEN_ONE_RAW_PAGE_V1'}else{1}
 $cases+=@(@{name=('no-raw-scope-'+$field);url=$data;changes=$changes})
}
foreach($case in $cases){
 $grant=$base.Clone();if($case.Contains('changes')){foreach($key in $case.changes.Keys){$grant[$key]=$case.changes[$key]}}
 $req=$requestBase.Clone();$req.filters=$requestBase.filters.Clone()
 if($case.Contains('requestFilterChanges')){foreach($key in $case.requestFilterChanges.Keys){$req.filters[$key]=$case.requestFilterChanges[$key]}}
 if($case.Contains('requestFieldChanges')){foreach($key in $case.requestFieldChanges.Keys){$req[$key]=$case.requestFieldChanges[$key]}}
 if($case.Contains('removeRequestFilter')){[void]$req.filters.Remove($case.removeRequestFilter)}
 $requestPath=Join-Path $OutputDirectory ('request-'+$case.name+'.json');$requestBytes=[Text.UTF8Encoding]::new($false).GetBytes(($req|ConvertTo-Json -Depth 12 -Compress));[IO.File]::WriteAllBytes($requestPath,$requestBytes)
 $grant.requestPin=@{path=$requestPath;bytes=$requestBytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($requestBytes)).ToLowerInvariant()}
 if($case.Contains('removeGrantField')){$grant.Remove($case.removeGrantField)}
 $path=Join-Path $OutputDirectory ($case.name+'.json');$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($grant|ConvertTo-Json -Depth 12 -Compress));[IO.File]::WriteAllBytes($path,$bytes)
 $context=@{executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);apiOrigin=$origin;contasPagarGrantPin=@{path=$path;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()}}
 if($case.Contains('missing')){$context.Remove('contasPagarGrantPin')};if($case.Contains('mutate')){[IO.File]::AppendAllText($path,' ')}
 $message=@{method=$(if($case.Contains('method')){$case.method}else{'GET'});authentication='DATA_EXPORT';deadline=$now.AddSeconds(30).ToString('o')}
 if($case.Contains('body')){$message.jsonBody=$case.body}
 $accepted=$false;try{$null=Assert-ContasPagarWireRequest $context $message ([Uri]$case.url) $now;$accepted=$true}catch{}
 $expected=$case.Contains('ok')-and$case.ok
 if($accepted-ne$expected){throw ('CONTAS_PAGAR_WIRE_SCOPE_FIXTURE_'+$case.name)}
 $passed++
}
# Compatibility executes only the unchanged old admission functions over synthetic grants.
foreach($old in @(
 @('Manifestos','manifestos','MANIFESTOS',6399,'MANIFESTOS_6399_NATIVE91_V1','search%5Bmanifests%5D%5Bservice_date%5D'),
 @('Cotacoes','cotacoes','COTACOES',6906,'COTACOES_6906_NATIVE37_V1','search%5Bquotes%5D%5Brequested_at%5D'),
 @('Inventario','inventario','INVENTARIO',10633,'INVENTARIO_10633_NATIVE26_V1','search%5Bcheck_in_orders%5D%5Bstarted_at%5D'),
 @('Sinistros','sinistros','SINISTROS',6392,'SINISTROS_6392_NATIVE44_V1','search%5Binsurance_claims%5D%5Bopening_at_date%5D'))){
 $g=$base.Clone();$g.schema='ROOT_'+$old[2]+'_FINITE_SOURCE_GRANT_V1';$g.entity=$old[2];$g.reportId=$old[3];$g.profile=$old[4]
 $p=Join-Path $OutputDirectory ('legacy-'+$old[1]+'.json');$b=[Text.UTF8Encoding]::new($false).GetBytes(($g|ConvertTo-Json -Depth 12 -Compress));[IO.File]::WriteAllBytes($p,$b)
 $ctx=@{executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);apiOrigin=$origin};$ctx[$old[1]+'GrantPin']=@{path=$p;bytes=$b.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b)).ToLowerInvariant()}
 $msg=@{method='GET';authentication='DATA_EXPORT';deadline=$now.AddSeconds(30).ToString('o')};$fn='Assert-'+$old[0]+'WireRequest'
 $url=$origin+'/api/analytics/reports/'+$old[3]+'/data?'+$old[5]+'=2026-10-12%20-%202026-10-18&page=1&per=100'
 $accepted=$false;try{$null=& $fn $ctx $msg ([Uri]$url) $now;$accepted=$true}catch{}
 if(-not$accepted){throw ('CONTAS_PAGAR_LEGACY_COMPAT_POSITIVE_'+$old[1])};$passed++
 $accepted=$false;try{$null=& $fn $ctx $msg ([Uri]($url+'&order_by=issue_date%20desc')) $now;$accepted=$true}catch{}
 if($accepted){throw ('CONTAS_PAGAR_LEGACY_COMPAT_NEGATIVE_'+$old[1])};$passed++
}
# Exercise the ACTUAL unchanged reservation block, not an imitation or the full physical worker.
$workerText=[IO.File]::ReadAllText($worker)
$first=$workerText.IndexOf('   $grantDirectory=Join-Path $c.sharedSourceDirectory',[StringComparison]::Ordinal)
$last=$workerText.IndexOf("   [void](Fresh-Metadata (Join-Path `$grantDirectory (`$admitted.operation+'.reserved.json'))",[StringComparison]::Ordinal)
if($first-lt0-or$last-le$first){throw 'CONTAS_PAGAR_EXACT_RESERVATION_BLOCK_REQUIRED'}
$end=$workerText.IndexOf([char]10,$last);if($end-lt$last){throw 'CONTAS_PAGAR_COMPLETE_RESERVATION_LINE_REQUIRED'}
$reservation=[scriptblock]::Create($workerText.Substring($first,$end-$first))
$syntheticShared=Join-Path $OutputDirectory 'synthetic-wire-ledger';[void][IO.Directory]::CreateDirectory($syntheticShared)
function Assert-PrivatePath0712($path,$policy,$unused){if(-not$path.StartsWith($syntheticShared+[IO.Path]::DirectorySeparatorChar,[StringComparison]::Ordinal)){throw 'CONTAS_PAGAR_FIXTURE_PRIVATE_SCOPE'};return $true}
function Reserve-Synthetic([string]$attempt,[int]$page){
 $c=@{sharedSourceDirectory=$syntheticShared};$privacyPolicy=@{};$finiteEntity='CONTAS_A_PAGAR';$finiteGrantPin=@{path='synthetic-grant';bytes=1;sha256=('a'*64)}
 $admitted=@{grant=@{attemptId=$attempt};operation=$(if($page-eq0){'info'}else{'page-'+$page});page=$page;rawPageScope=$false}
 $request=@{operationId=[guid]::NewGuid().ToString()};. $reservation
}
function Check-Reserve([string]$name,[scriptblock]$action,[bool]$expected){$ok=$false;try{$null=& $action;$ok=$true}catch{};if($ok-ne$expected){throw ('CONTAS_PAGAR_REPLAY_FIXTURE_'+$name)};$script:passed++}
$a='00000000-0000-4000-8000-000000000011';$ledger=Join-Path $syntheticShared ('contas_a_pagar-'+$a)
Check-Reserve 'data-before-info' {Reserve-Synthetic $a 1} $false
Check-Reserve 'first-info' {Reserve-Synthetic $a 0} $true
Check-Reserve 'replay-info' {Reserve-Synthetic $a 0} $false
Check-Reserve 'page-before-info-complete' {Reserve-Synthetic $a 1} $false
$null=Fresh-Metadata (Join-Path $ledger 'info.complete.json') @{status=200;complete=$true}
Check-Reserve 'page-one-first' {Reserve-Synthetic $a 1} $true
Check-Reserve 'page-one-replay' {Reserve-Synthetic $a 1} $false
Check-Reserve 'page-two-before-one-complete' {Reserve-Synthetic $a 2} $false
$null=Fresh-Metadata (Join-Path $ledger 'page-1.complete.json') @{status=200;complete=$true}
Check-Reserve 'page-two-after-one' {Reserve-Synthetic $a 2} $true
# A different synthetic grant has a deliberately exhausted original INFO deadline.
$z='00000000-0000-4000-8000-000000000012';$expired=Join-Path $syntheticShared ('contas_a_pagar-'+$z);[void][IO.Directory]::CreateDirectory($expired)
$null=Fresh-Metadata (Join-Path $expired 'info.reserved.json') @{sourceDeadlineUtc=[DateTimeOffset]::UtcNow.AddSeconds(-1).ToString('o')}
$null=Fresh-Metadata (Join-Path $expired 'info.complete.json') @{status=200;complete=$true}
Check-Reserve 'source210-exhausted' {Reserve-Synthetic $z 1} $false
foreach($case in @(@{remaining=21.1234567;millis=30000;expected='21'},@{remaining=210;millis=30000;expected='30'},@{remaining=2.9;millis=1500;expected='1'},@{remaining=0.999;millis=30000},@{remaining=[double]::NaN;millis=30000},@{remaining=30;millis=30001})){
 $accepted=$false;$value=$null;try{$value=Get-CurlTimeArgument $case.remaining $case.millis;$accepted=$true}catch{}
 $expected=$case.Contains('expected')
 if($accepted-ne$expected-or$expected-and$value-cne$case.expected){throw 'CONTAS_PAGAR_CURL_WHOLE_SECONDS_FIXTURE'}
 $passed++
}
$current=Join-Path $OutputDirectory 'synthetic-last-response.json'
foreach($sequence in 1..3){
 $temporary=Join-Path $OutputDirectory ('synthetic-response-'+$sequence+'.pending.json')
 [IO.File]::WriteAllText($temporary,('{"sequence":'+$sequence+'}'),[Text.UTF8Encoding]::new($false))
 Publish-WireResponseMarker $temporary $current
 if([IO.File]::Exists($temporary)-or(Get-Content -Raw -LiteralPath $current|ConvertFrom-Json).sequence-ne$sequence){throw 'CONTAS_PAGAR_SEQUENTIAL_RESPONSE_MARKER_PUBLICATION'}
}
$passed++
[ordered]@{schema='CONTAS_A_PAGAR_WIRE_SCOPE_SYNTHETIC_FIXTURES_V1';passed=$passed;Source=0;HTTP=0;SQL=0;CIM=0;credentials=0;syntheticOnly=$true;legacyAdmissionCompatibilityExercised=$true;actualReservationReplayBlockExercised=$true;rootIdentityAssumed=$false;filterScopePolicy='ISSUE_DATE_WITH_EXPLICIT_EMPTY_CREATED_AT_V1';createdAtFilter='';priorBothDateProfileRefused=$true}|ConvertTo-Json -Depth 4

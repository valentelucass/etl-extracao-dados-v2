param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$worker=Join-Path $repo 'src\main\resources\independent-export\RootWireWorker.ps1'
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($worker,[ref]$tokens,[ref]$errors)
if(@($errors).Count){throw 'COTACOES_WIRE_SOURCE_PARSE'}
foreach($name in @('Need-Wire','Read-Pin','Assert-CotacoesWireRequest','Get-CurlTimeArgument','Publish-WireResponseMarker')){
 $found=@($ast.FindAll({param($node)$node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq$name},$false))
 if($found.Count-ne1){throw 'COTACOES_WIRE_EXACT_FUNCTION'}
 . ([scriptblock]::Create($found[0].Extent.Text))
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'COTACOES_WIRE_FIXTURES_FRESH_DIRECTORY'}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$now=[DateTimeOffset]::UtcNow
$actor='00000000-0000-4000-8000-000000000001'
$base=@{schema='ROOT_COTACOES_FINITE_SOURCE_GRANT_V1';approved=$true;entity='COTACOES';reportId=6906;profile='COTACOES_6906_NATIVE37_V1';executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100;createdUtc=$now.AddSeconds(-1).ToString('o');expiresUtc=$now.AddSeconds(60).ToString('o');attemptId='00000000-0000-4000-8000-000000000002';periodStart='2026-10-12';periodEnd='2026-10-18';zone='America/Sao_Paulo'}
$origin='https://capture.invalid'
$data=$origin+'/api/analytics/reports/6906/data?search%5Bquotes%5D%5Brequested_at%5D=2026-10-12%20-%202026-10-18&page=1&per=100'
$cases=@(
 @{name='info';url=($origin+'/api/analytics/reports/6906/info');ok=$true},
 @{name='page1';url=$data;ok=$true},
 @{name='page16';url=$data.Replace('page=1&','page=16&');ok=$true},
 @{name='extra-info-filter';url=($origin+'/api/analytics/reports/6906/info?x=1')},
 @{name='page17';url=$data.Replace('page=1&','page=17&')},
 @{name='wrong-per';url=$data.Replace('per=100','per=101')},
 @{name='wrong-period';url=$data.Replace('2026-10-12','2026-10-11')},
 @{name='extra-filter';url=($data+'&search%5Bquotes%5D%5Bstatus%5D=closed')},
 @{name='wrong-origin';url=$data.Replace('capture.invalid','elsewhere.invalid')},
 @{name='wrong-template';url=$data.Replace('/6906/','/6389/')},
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
$passed=0
$rawUrl=$origin+'/api/analytics/reports/6906/data?search%5Bquotes%5D%5Brequested_at%5D=2026-10-12%20-%202026-10-12&page=4&per=100'
$rawChanges=@{pageIndex=4;effectiveHTTPMaximum=2;pageScopePolicy='EXACT_INFO_THEN_ONE_RAW_PAGE_V1';periodEnd='2026-10-12'}
foreach($rawCase in @(
 @{name='raw-info';url=($origin+'/api/analytics/reports/6906/info');ok=$true},
 @{name='raw-page4';url=$rawUrl;ok=$true},
 @{name='raw-wrong-page';url=$rawUrl.Replace('page=4','page=3')},
 @{name='raw-string-page';url=$rawUrl;change=@{pageIndex='4'}},
 @{name='raw-string-budget';url=$rawUrl;change=@{effectiveHTTPMaximum='2'}},
 @{name='raw-budget3';url=$rawUrl;change=@{effectiveHTTPMaximum=3}},
 @{name='raw-unknown-policy';url=$rawUrl;change=@{pageScopePolicy='not-qualified'}},
 @{name='raw-multiple-days';url=$data;change=@{periodEnd='2026-10-18'}},
 @{name='raw-missing-page';url=$rawUrl;remove='pageIndex'},
 @{name='raw-missing-budget';url=$rawUrl;remove='effectiveHTTPMaximum'},
 @{name='raw-missing-policy';url=$rawUrl;remove='pageScopePolicy'}
)){
 $changes=$rawChanges.Clone();if($rawCase.Contains('change')){foreach($key in $rawCase.change.Keys){$changes[$key]=$rawCase.change[$key]}}
 $case=@{name=$rawCase.name;url=$rawCase.url;changes=$changes};if($rawCase.Contains('ok')){$case.ok=$rawCase.ok};if($rawCase.Contains('remove')){$case.removeGrantField=$rawCase.remove}
 $cases+=@($case)
}
foreach($case in $cases){
 $grant=$base.Clone();if($case.Contains('changes')){foreach($key in $case.changes.Keys){$grant[$key]=$case.changes[$key]}}
 if($case.Contains('removeGrantField')){$grant.Remove($case.removeGrantField)}
 $path=Join-Path $OutputDirectory ($case.name+'.json');$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($grant|ConvertTo-Json -Depth 12 -Compress));[IO.File]::WriteAllBytes($path,$bytes)
 $context=@{executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);apiOrigin=$origin;cotacoesGrantPin=@{path=$path;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()}}
 if($case.Contains('missing')){$context.Remove('cotacoesGrantPin')};if($case.Contains('mutate')){[IO.File]::AppendAllText($path,' ')}
 $message=@{method=$(if($case.Contains('method')){$case.method}else{'GET'});authentication='DATA_EXPORT';deadline=$now.AddSeconds(30).ToString('o')}
 if($case.Contains('body')){$message.jsonBody=$case.body}
 $accepted=$false;try{$null=Assert-CotacoesWireRequest $context $message ([Uri]$case.url) $now;$accepted=$true}catch{}
 $expected=$case.Contains('ok')-and$case.ok
 if($accepted-ne$expected){throw ('COTACOES_WIRE_SCOPE_FIXTURE_'+$case.name)}
 $passed++
}
foreach($case in @(@{remaining=21.1234567;millis=30000;expected='21'},@{remaining=210;millis=30000;expected='30'},@{remaining=2.9;millis=1500;expected='1'},@{remaining=0.999;millis=30000},@{remaining=[double]::NaN;millis=30000},@{remaining=30;millis=30001})){
 $accepted=$false;$value=$null;try{$value=Get-CurlTimeArgument $case.remaining $case.millis;$accepted=$true}catch{}
 $expected=$case.Contains('expected')
 if($accepted-ne$expected-or$expected-and$value-cne$case.expected){throw 'COTACOES_CURL_WHOLE_SECONDS_FIXTURE'}
 $passed++
}
$current=Join-Path $OutputDirectory 'synthetic-last-response.json'
foreach($sequence in 1..3){
 $temporary=Join-Path $OutputDirectory ('synthetic-response-'+$sequence+'.pending.json')
 [IO.File]::WriteAllText($temporary,('{"sequence":'+$sequence+'}'),[Text.UTF8Encoding]::new($false))
 Publish-WireResponseMarker $temporary $current
 if([IO.File]::Exists($temporary)-or(Get-Content -Raw -LiteralPath $current|ConvertFrom-Json).sequence-ne$sequence){throw 'COTACOES_SEQUENTIAL_RESPONSE_MARKER_PUBLICATION'}
}
$passed++
[ordered]@{schema='COTACOES_WIRE_SCOPE_SYNTHETIC_FIXTURES_V1';passed=$passed;Source=0;HTTP=0;SQL=0;CIM=0;credentials=0;syntheticOnly=$true}|ConvertTo-Json -Depth 4

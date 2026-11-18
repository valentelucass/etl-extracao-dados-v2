param([Parameter(Mandatory)][string]$OutputDirectory,[Parameter(Mandatory)][string]$WorkerPath)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($WorkerPath,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'FATURAS_CLIENTE_NATIVE_WIRE_PARSE'}
foreach($name in @('Need-Wire','Read-Pin','Assert-FaturasClienteNativeWireRequest')){
 $fn=@($ast.FindAll({param($n)$n-is[Management.Automation.Language.FunctionDefinitionAst]-and$n.Name-ceq$name},$false))
 if($fn.Count-ne1){throw 'FATURAS_CLIENTE_NATIVE_WIRE_FUNCTION'};. ([scriptblock]::Create($fn[0].Extent.Text))
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'FATURAS_CLIENTE_NATIVE_WIRE_FRESH_FIXTURES'}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$now=[DateTimeOffset]::UtcNow;$actor='00000000-0000-4000-8000-000000000001'
$target='https://eslcloud.s3.sa-east-1.amazonaws.com/active_storage/rodogarcia/analytics_report_files/authored-faturasCliente.xlsx?authored=not-a-real-signature'
$link=@{schema='ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_V1';entity='FATURAS_POR_CLIENTE';profile='FATURAS_CLIENTE_4924_SERVICE_AT_NATIVE52_V1';reportId=4924;fileId=1773250;periodStart='2026-10-12';periodEnd='2026-10-18';sourceUiHref='/analytics/report_files/1773250';targetUri=$target}
function Put-NativeFixture([string]$name,$value){$path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 20 -Compress));[IO.File]::WriteAllBytes($path,$bytes);return @{path=$path;bytes=$bytes.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()}}
$linkPin=Put-NativeFixture 'private-authored-link.json' $link
$base=@{schema='ROOT_FATURAS_CLIENTE_UI_LINK_ONLY_NATIVE_GRANT_V1';approved=$true;entity='FATURAS_POR_CLIENTE';profile=$link.profile;reportId=4924;fileId=1773250;periodStart=$link.periodStart;periodEnd=$link.periodEnd;zone='America/Sao_Paulo';executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);operation='download_from_observed_ui_link';downloadAuthentication='NONE';filterScopePolicy='SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1';updatedAtFilter='';uiObservedLinkPin=$linkPin;attemptId='00000000-0000-4000-8000-000000000002';createdUtc=$now.AddSeconds(-1).ToString('o');expiresUtc=$now.AddSeconds(60).ToString('o');SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=1;INFOHTTPMaximum=0;DATAHTTPMaximum=0;DOWNLOADHTTPMaximum=1;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;maximumDownloadBytes=8388608;requestSeconds=30;retryMaximum=0;redirectMaximum=0}
$cases=@(
 @{name='exact-one-download';url=$target;ok=$true},
 @{name='metadata-api';url='https://rodogarcia.eslcloud.com.br/api/analytics/report_files/1773250'},
 @{name='info-api';url='https://rodogarcia.eslcloud.com.br/api/analytics/reports/4924/info'},
 @{name='post';url=$target;message=@{method='POST'}},
 @{name='bearer';url=$target;message=@{authentication='DATA_EXPORT'}},
 @{name='body';url=$target;message=@{jsonBody='{}'}},
 @{name='http';url=$target.Replace('https:','http:')},
 @{name='wrong-host';url=$target.Replace('eslcloud.s3.sa-east-1.amazonaws.com','other.invalid')},
 @{name='wrong-uri';url=($target+'&extra=1')},
 @{name='fragment';url=($target+'#forbidden')},
 @{name='wrong-actor';url=$target;changes=@{executingRootUUID='00000000-0000-4000-8000-000000000003'}},
 @{name='wrong-code';url=$target;changes=@{codeUnitSha256=('c'*64)}},
 @{name='wrong-fingerprint';url=$target;changes=@{sourceConfigurationSha256=('c'*64)}},
 @{name='cap-grant';url=$target;changes=@{schema='ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_GRANT_V1'}},
 @{name='wrong-profile';url=$target;changes=@{profile='FATURAS_CLIENTE_4924_OBSERVED26_COMPARISON_V1'}},
 @{name='updated-filled';url=$target;changes=@{updatedAtFilter='filled'}},
 @{name='source-expired210';url=$target;changes=@{createdUtc=$now.AddSeconds(-211).ToString('o');expiresUtc=$now.AddSeconds(30).ToString('o')}},
 @{name='created-two-seconds-before-start-deadline210';url=$target;ok=$true;changes=@{createdUtc=$now.AddSeconds(-2).ToString('o');expiresUtc=$now.AddSeconds(298).ToString('o')};message=@{deadline=$now.AddSeconds(210).ToString('o')}},
 @{name='deadline211-from-current-time';url=$target;changes=@{createdUtc=$now.AddSeconds(-2).ToString('o');expiresUtc=$now.AddSeconds(298).ToString('o')};message=@{deadline=$now.AddSeconds(211).ToString('o')}},
 @{name='http2';url=$target;changes=@{HTTPMaximum=2}},
 @{name='metadata1';url=$target;changes=@{INFOHTTPMaximum=1}},
 @{name='long-request';url=$target;message=@{timeoutMillis=30001}},
 @{name='oversize';url=$target;message=@{maximumBodyBytes=8388609}},
 @{name='link-pin-drift';url=$target;badLinkPin=$true},
 @{name='missing-grant';url=$target;missing=$true}
)
foreach($cap in @{reportId=4924;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=1;INFOHTTPMaximum=0;DATAHTTPMaximum=0;DOWNLOADHTTPMaximum=1;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;maximumDownloadBytes=8388608;requestSeconds=30;retryMaximum=0;redirectMaximum=0}.GetEnumerator()){$wrong=@{};$wrong[$cap.Key]=[string]$cap.Value;$cases+=@(@{name=('string-cap-'+$cap.Key);url=$target;changes=$wrong})}
$passed=0
foreach($case in $cases){
 $grant=$base.Clone();if($case.Contains('changes')){foreach($key in $case.changes.Keys){$grant[$key]=$case.changes[$key]}}
 if($case.Contains('badLinkPin')){$grant.uiObservedLinkPin=$linkPin.Clone();$grant.uiObservedLinkPin.sha256=('c'*64)}
 $grantPin=Put-NativeFixture ($case.name+'.json') $grant
 $context=@{executingRootUUID=$actor;sourceConfigurationSha256=('a'*64);codeUnitSha256=('b'*64);faturasClienteNativeGrantPin=$grantPin}
 if($case.Contains('missing')){$context.Remove('faturasClienteNativeGrantPin')}
 $message=@{method='GET';authentication='NONE';url=$case.url;deadline=$now.AddSeconds(30).ToString('o');timeoutMillis=30000;maximumBodyBytes=8388608}
 if($case.Contains('message')){foreach($key in $case.message.Keys){$message[$key]=$case.message[$key]}}
 $ok=$false;try{$null=Assert-FaturasClienteNativeWireRequest $context $message ([Uri]$case.url) $now;$ok=$true}catch{}
 if($ok-ne($case.Contains('ok')-and$case.ok)){throw ('FATURAS_CLIENTE_NATIVE_WIRE_CASE_'+$case.name)};$passed++
}
[ordered]@{schema='FATURAS_CLIENTE_NATIVE_UI_LINK_ONLY_WIRE_FIXTURES_V1';passed=$passed;cases=$cases.Count;Source=0;HTTP=0;SQL=0;CIM=0;syntheticOnly=$true;businessValuesPublished=$false;networkExecuted=$false}|ConvertTo-Json -Depth 5

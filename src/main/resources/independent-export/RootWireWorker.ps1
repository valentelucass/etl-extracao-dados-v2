param([Parameter(Mandatory)][string]$ContextPath,[Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ContextSha256,
 [Parameter(Mandatory)][string]$MainProofPath,[Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$MainProofSha256)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
# Actual physical boundary. No canonical/slot/ACL/service/identity mutation is performed.
$leases=[Collections.Generic.List[object]]::new();$child=$null;$childJob=$null;$guard=$null;$job=$null
$started=$false;$closed=$false;$stdinClosed=$false;$stdoutEof=$false;$stderrEof=$false
$status=$null;$bodyPin=$null;$failure='BEFORE_PHYSICAL_GATES';$curlConfig=$null;$token=$null;$out=$null
$curlPid=$null;$birth=$null;$errorBytes=0L;$request=$null;$submittedAt=$null;$mainProcess=$null;$partialPin=$null
$manifestos=$false;$cotacoes=$false;$inventario=$false;$sinistros=$false;$contasPagar=$false;$localizador=$false;$localizadorNative=$false;$faturasCliente=$false;$faturasClienteNative=$false;$grantDirectory=$null;$admitted=$null;$finiteGrantPin=$null;$finiteEntity=$null
$safeFailureCode=$null;$curlExitCode=$null;$privateStderrPin=$null;$errorOut=[IO.MemoryStream]::new()
function Need-Wire([bool]$ok,[string]$code){if(-not$ok){throw $code}}
function Publish-WireResponseMarker([string]$temporary,[string]$current){
 # PowerShell converts $null to an empty string for this .NET string
 # parameter. NullString preserves a real null backup path on replacement.
 if([IO.File]::Exists($current)){[IO.File]::Replace($temporary,$current,[NullString]::Value,$false)}else{[IO.File]::Move($temporary,$current)}
}
function Get-CurlTimeArgument([double]$remainingSeconds,[long]$timeoutMillis){
 Need-Wire ([double]::IsFinite($remainingSeconds)-and$remainingSeconds-gt0-and$timeoutMillis-gt0-and$timeoutMillis-le30000) 'WIRE_CURL_TIME_INPUT'
 # This installed Windows curl rejects fractional seconds. Round down, so its
 # own deadline can never exceed either remaining budget or requested timeout.
 $wholeSeconds=[math]::Floor([math]::Min($remainingSeconds,$timeoutMillis/1000.0))
 Need-Wire ($wholeSeconds-ge1-and$wholeSeconds-le30) 'WIRE_CURL_WHOLE_SECOND_BUDGET'
 return $wholeSeconds.ToString('0',[Globalization.CultureInfo]::InvariantCulture)
}
function Read-Pin($p,[int]$maximum){
 Need-Wire ($p.path-is[string]-and[IO.Path]::IsPathFullyQualified($p.path)-and$p.bytes-gt0-and$p.bytes-le$maximum-and$p.sha256-cmatch'^[a-f0-9]{64}$') 'WIRE_PIN'
 for($at=$p.path;$at;$at=[IO.Path]::GetDirectoryName($at)){Need-Wire (([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-eq0) 'WIRE_NO_REPARSE'}
 $b=[IO.File]::ReadAllBytes($p.path);Need-Wire ($b.Length-eq$p.bytes-and[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b)).ToLowerInvariant()-ceq$p.sha256) 'WIRE_PIN_DRIFT';return ,$b
}
function Read([string]$path){
 $item=Get-Item -LiteralPath $path
 Need-Wire ($item.Length-gt0-and$item.Length-le262144) 'WIRE_CONTROL_JSON_BOUND'
 return Get-Content -Raw -LiteralPath $path|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String
}
function Assert-ManifestosWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('manifestosGrantPin')) 'WIRE_MANIFESTOS_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.manifestosGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_MANIFESTOS_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'MANIFESTOS'-and$grant.reportId-eq6399-and$grant.profile-ceq'MANIFESTOS_6399_NATIVE91_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_MANIFESTOS_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.SQL-eq0-and$grant.OriginalMaximum-eq1-and$grant.SourceMaximum-eq1-and$grant.HTTPMaximum-eq17-and$grant.INFOHTTPMaximum-eq1-and$grant.DATAHTTPMaximum-eq16-and$grant.SourceSeconds-eq210-and$grant.OriginalSeconds-eq300-and$grant.OriginalBytes-eq1073741824-and$grant.retryMaximum-eq0-and$grant.redirectMaximum-eq0-and$grant.per-eq100) 'WIRE_MANIFESTOS_FINITE_GRANT_BINDING'
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_MANIFESTOS_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_MANIFESTOS_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt32-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_MANIFESTOS_GET_ONLY_DECLARED_PERIOD'
 $rawPageScope=$grant.Contains('pageIndex')-or$grant.Contains('effectiveHTTPMaximum')-or$grant.Contains('pageScopePolicy')
 if($rawPageScope){
  Need-Wire ($grant.Contains('pageIndex')-and$grant.Contains('effectiveHTTPMaximum')-and$grant.Contains('pageScopePolicy')) 'WIRE_MANIFESTOS_RAW_PAGE_EXPLICIT_SCOPE'
  Need-Wire (($grant.pageIndex-is[int]-or$grant.pageIndex-is[long])-and$grant.pageIndex-ge1-and$grant.pageIndex-le16-and($grant.effectiveHTTPMaximum-is[int]-or$grant.effectiveHTTPMaximum-is[long])-and$grant.effectiveHTTPMaximum-eq2-and$grant.pageScopePolicy-ceq'EXACT_INFO_THEN_ONE_RAW_PAGE_V1'-and$grant.periodStart-ceq$grant.periodEnd) 'WIRE_MANIFESTOS_RAW_PAGE_FINITE_SCOPE'
 }
 $base=$context.apiOrigin+'/api/analytics/reports/6399/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$rawPageScope}}
 $filter=[Uri]::EscapeDataString('search[manifests][service_date]')+'='+[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 for($page=1;$page-le16;$page++){
  if(((-not$rawPageScope)-or$page-eq$grant.pageIndex)-and$target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$rawPageScope}}
 }
 throw 'WIRE_MANIFESTOS_EXACT_QUERY_NOT_GRANTED'
}
function Assert-CotacoesWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('cotacoesGrantPin')) 'WIRE_COTACOES_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.cotacoesGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_COTACOES_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'COTACOES'-and$grant.reportId-eq6906-and$grant.profile-ceq'COTACOES_6906_NATIVE37_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_COTACOES_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.SQL-eq0-and$grant.OriginalMaximum-eq1-and$grant.SourceMaximum-eq1-and$grant.HTTPMaximum-eq17-and$grant.INFOHTTPMaximum-eq1-and$grant.DATAHTTPMaximum-eq16-and$grant.SourceSeconds-eq210-and$grant.OriginalSeconds-eq300-and$grant.OriginalBytes-eq1073741824-and$grant.retryMaximum-eq0-and$grant.redirectMaximum-eq0-and$grant.per-eq100) 'WIRE_COTACOES_FINITE_GRANT_BINDING'
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_COTACOES_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_COTACOES_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt32-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_COTACOES_GET_ONLY_DECLARED_PERIOD'
 $rawPageScope=$grant.Contains('pageIndex')-or$grant.Contains('effectiveHTTPMaximum')-or$grant.Contains('pageScopePolicy')
 if($rawPageScope){
  Need-Wire ($grant.Contains('pageIndex')-and$grant.Contains('effectiveHTTPMaximum')-and$grant.Contains('pageScopePolicy')) 'WIRE_COTACOES_RAW_PAGE_EXPLICIT_SCOPE'
  Need-Wire (($grant.pageIndex-is[int]-or$grant.pageIndex-is[long])-and$grant.pageIndex-ge1-and$grant.pageIndex-le16-and($grant.effectiveHTTPMaximum-is[int]-or$grant.effectiveHTTPMaximum-is[long])-and$grant.effectiveHTTPMaximum-eq2-and$grant.pageScopePolicy-ceq'EXACT_INFO_THEN_ONE_RAW_PAGE_V1'-and$grant.periodStart-ceq$grant.periodEnd) 'WIRE_COTACOES_RAW_PAGE_FINITE_SCOPE'
 }
 $base=$context.apiOrigin+'/api/analytics/reports/6906/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$rawPageScope}}
 $filter=[Uri]::EscapeDataString('search[quotes][requested_at]')+'='+[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 for($page=1;$page-le16;$page++){
  if(((-not$rawPageScope)-or$page-eq$grant.pageIndex)-and$target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$rawPageScope}}
 }
 throw 'WIRE_COTACOES_EXACT_QUERY_NOT_GRANTED'
}
function Assert-InventarioWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('inventarioGrantPin')) 'WIRE_INVENTARIO_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.inventarioGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_INVENTARIO_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'INVENTARIO'-and$grant.reportId-eq10633-and$grant.profile-ceq'INVENTARIO_10633_NATIVE26_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_INVENTARIO_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.SQL-eq0-and$grant.OriginalMaximum-eq1-and$grant.SourceMaximum-eq1-and$grant.HTTPMaximum-eq17-and$grant.INFOHTTPMaximum-eq1-and$grant.DATAHTTPMaximum-eq16-and$grant.SourceSeconds-eq210-and$grant.OriginalSeconds-eq300-and$grant.OriginalBytes-eq1073741824-and$grant.retryMaximum-eq0-and$grant.redirectMaximum-eq0-and$grant.per-eq100) 'WIRE_INVENTARIO_FINITE_GRANT_BINDING'
 foreach($cap in @{reportId=10633;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_INVENTARIO_TYPED_FIXED_LIMIT'
 }
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_INVENTARIO_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_INVENTARIO_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_INVENTARIO_GET_ONLY_DECLARED_PERIOD'
 $rawPageScope=$grant.Contains('pageIndex')-or$grant.Contains('effectiveHTTPMaximum')-or$grant.Contains('pageScopePolicy')
 if($rawPageScope){
  Need-Wire ($grant.Contains('pageIndex')-and$grant.Contains('effectiveHTTPMaximum')-and$grant.Contains('pageScopePolicy')) 'WIRE_INVENTARIO_RAW_PAGE_EXPLICIT_SCOPE'
  Need-Wire (($grant.pageIndex-is[int]-or$grant.pageIndex-is[long])-and$grant.pageIndex-ge1-and$grant.pageIndex-le16-and($grant.effectiveHTTPMaximum-is[int]-or$grant.effectiveHTTPMaximum-is[long])-and$grant.effectiveHTTPMaximum-eq2-and$grant.pageScopePolicy-ceq'EXACT_INFO_THEN_ONE_RAW_PAGE_V1'-and$grant.periodStart-ceq$grant.periodEnd) 'WIRE_INVENTARIO_RAW_PAGE_FINITE_SCOPE'
 }
 $base=$context.apiOrigin+'/api/analytics/reports/10633/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$rawPageScope}}
 $filter=[Uri]::EscapeDataString('search[check_in_orders][started_at]')+'='+[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 for($page=1;$page-le16;$page++){
  if(((-not$rawPageScope)-or$page-eq$grant.pageIndex)-and$target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$rawPageScope}}
 }
 throw 'WIRE_INVENTARIO_EXACT_QUERY_NOT_GRANTED'
}
function Assert-SinistrosWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('sinistrosGrantPin')) 'WIRE_SINISTROS_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.sinistrosGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_SINISTROS_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'SINISTROS'-and$grant.reportId-eq6392-and$grant.profile-ceq'SINISTROS_6392_NATIVE44_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_SINISTROS_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.SQL-eq0-and$grant.OriginalMaximum-eq1-and$grant.SourceMaximum-eq1-and$grant.HTTPMaximum-eq17-and$grant.INFOHTTPMaximum-eq1-and$grant.DATAHTTPMaximum-eq16-and$grant.SourceSeconds-eq210-and$grant.OriginalSeconds-eq300-and$grant.OriginalBytes-eq1073741824-and$grant.retryMaximum-eq0-and$grant.redirectMaximum-eq0-and$grant.per-eq100) 'WIRE_SINISTROS_FINITE_GRANT_BINDING'
 foreach($cap in @{reportId=6392;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_SINISTROS_TYPED_FIXED_LIMIT'
 }
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_SINISTROS_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_SINISTROS_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_SINISTROS_GET_ONLY_DECLARED_PERIOD'
 Need-Wire (-not$grant.Contains('pageIndex')-and-not$grant.Contains('effectiveHTTPMaximum')-and-not$grant.Contains('pageScopePolicy')) 'WIRE_SINISTROS_NO_RAW_PAGE_SCOPE'
 $rawPageScope=$false
 $base=$context.apiOrigin+'/api/analytics/reports/6392/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$rawPageScope}}
 $filter=[Uri]::EscapeDataString('search[insurance_claims][opening_at_date]')+'='+[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 for($page=1;$page-le16;$page++){
  if(((-not$rawPageScope)-or$page-eq$grant.pageIndex)-and$target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$rawPageScope}}
 }
 throw 'WIRE_SINISTROS_EXACT_QUERY_NOT_GRANTED'
}
function Assert-ContasPagarWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('contasPagarGrantPin')) 'WIRE_CONTAS_PAGAR_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.contasPagarGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'CONTAS_A_PAGAR'-and$grant.reportId-eq8636-and$grant.profile-ceq'CONTAS_A_PAGAR_8636_ISSUE_DATE_NATIVE28_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_CONTAS_PAGAR_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256) 'WIRE_CONTAS_PAGAR_SOURCE_CODE_BINDING'
 Need-Wire ($grant.Contains('filterScopePolicy')-and$grant.filterScopePolicy-is[string]-and$grant.filterScopePolicy-ceq'ISSUE_DATE_WITH_EXPLICIT_EMPTY_CREATED_AT_V1'-and$grant.Contains('createdAtFilter')-and$grant.createdAtFilter-is[string]-and$grant.createdAtFilter-ceq'') 'WIRE_CONTAS_PAGAR_EXPLICIT_ISSUE_ONLY_POLICY'
 Need-Wire ($grant.Contains('requestPin')) 'WIRE_CONTAS_PAGAR_REQUEST_PIN_REQUIRED'
 $declared=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $grant.requestPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($declared.schema-ceq'CONTAS_A_PAGAR_INDEPENDENT_REQUEST_V1'-and$declared.profile-ceq$grant.profile-and$declared.entity-ceq$grant.entity-and$declared.reportId-eq8636-and$declared.attemptId-ceq$grant.attemptId-and$declared.periodStart-ceq$grant.periodStart-and$declared.periodEnd-ceq$grant.periodEnd-and$declared.zone-ceq$grant.zone-and$declared.filters-is[Collections.IDictionary]-and$declared.filters.Count-eq9) 'WIRE_CONTAS_PAGAR_REQUEST_SCOPE_BOUND_TO_GRANT'
 foreach($name in @('accounting_debits[accounting_planning_management_id]','accounting_debits[corporation_id]','accounting_debits[created_at]','accounting_debits[document]','accounting_debits[issue_date]','accounting_debits[paid]','accounting_debits[receiver_id]','accounting_debits[type]','scopes[from_competence_period_string]')){
  Need-Wire (@($declared.filters.Keys)-ccontains$name-and$declared.filters[$name]-is[string]-and$declared.filters[$name]-ceq$(if($name-ceq'accounting_debits[issue_date]'){$grant.periodStart+' - '+$grant.periodEnd}else{''})) 'WIRE_CONTAS_PAGAR_NINE_EXPLICIT_FILTERS_ISSUE_ONLY'
 }
 foreach($cap in @{reportId=8636;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_CONTAS_PAGAR_TYPED_FIXED_LIMIT'
 }
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_CONTAS_PAGAR_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_CONTAS_PAGAR_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_CONTAS_PAGAR_GET_ONLY_DECLARED_PERIOD'
 Need-Wire (-not$grant.Contains('pageIndex')-and-not$grant.Contains('effectiveHTTPMaximum')-and-not$grant.Contains('pageScopePolicy')) 'WIRE_CONTAS_PAGAR_NO_RAW_PAGE_SCOPE'
 $base=$context.apiOrigin+'/api/analytics/reports/8636/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$false}}
 # Explicitly empty created_at belongs to the request/grant; its query parameter must be omitted. No root count is inferred.
 $period=[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 $filters=[Uri]::EscapeDataString('search[accounting_debits][issue_date]')+'='+$period
 $order='&order_by='+[Uri]::EscapeDataString('issue_date desc')
 for($page=1;$page-le16;$page++){
  if($target.AbsoluteUri-ceq($base+'data?'+$filters+'&page='+$page+'&per=100'+$order)){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$false}}
 }
 throw 'WIRE_CONTAS_PAGAR_EXACT_QUERY_NOT_GRANTED'
}
function Assert-LocalizadorWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('localizadorGrantPin')) 'WIRE_LOCALIZADOR_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.localizadorGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_LOCALIZADOR_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'LOCALIZADOR'-and$grant.profile-ceq'LOCALIZADOR_8656_SERVICE_AT_NATIVE26_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_LOCALIZADOR_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256) 'WIRE_LOCALIZADOR_SOURCE_CODE_BINDING'
 Need-Wire ($grant.Contains('filterScopePolicy')-and$grant.filterScopePolicy-is[string]-and$grant.filterScopePolicy-ceq'SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1'-and$grant.Contains('updatedAtFilter')-and$grant.updatedAtFilter-is[string]-and$grant.updatedAtFilter-ceq'') 'WIRE_LOCALIZADOR_EXPLICIT_SERVICE_ONLY_POLICY'
 Need-Wire ($grant.Contains('requestPin')) 'WIRE_LOCALIZADOR_REQUEST_PIN_REQUIRED'
 $declared=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $grant.requestPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($declared.Count-eq9-and$declared.schema-ceq'LOCALIZADOR_INDEPENDENT_REQUEST_V1'-and$declared.profile-ceq$grant.profile-and$declared.entity-ceq$grant.entity-and($declared.reportId-is[int]-or$declared.reportId-is[long])-and$declared.reportId-eq8656-and$declared.attemptId-ceq$grant.attemptId-and$declared.periodStart-ceq$grant.periodStart-and$declared.periodEnd-ceq$grant.periodEnd-and$declared.zone-ceq$grant.zone-and$declared.filters-is[Collections.IDictionary]-and$declared.filters.Count-eq8) 'WIRE_LOCALIZADOR_REQUEST_SCOPE_BOUND_TO_GRANT'
 foreach($name in @('freights[service_at]','fit_freight_location[current_location_id]','freights[payer_id]','freights[type]','freights[status]','fit_fis_invoice[number]','fit_fis_invoice[key]','scopes[by_updated_at]')){
  Need-Wire (@($declared.filters.Keys)-ccontains$name-and$declared.filters[$name]-is[string]-and$declared.filters[$name]-ceq$(if($name-ceq'freights[service_at]'){$grant.periodStart+' - '+$grant.periodEnd}else{''})) 'WIRE_LOCALIZADOR_EIGHT_EXPLICIT_SERVICE_ONLY_FILTERS'
 }
 foreach($cap in @{reportId=8656;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_LOCALIZADOR_TYPED_FIXED_LIMIT'
 }
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_LOCALIZADOR_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_LOCALIZADOR_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_LOCALIZADOR_GET_ONLY_DECLARED_PERIOD'
 Need-Wire (-not$grant.Contains('pageIndex')-and-not$grant.Contains('effectiveHTTPMaximum')-and-not$grant.Contains('pageScopePolicy')) 'WIRE_LOCALIZADOR_NO_RAW_PAGE_SCOPE'
 $base=$context.apiOrigin+'/api/analytics/reports/8656/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$false}}
 $period=[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 $filter=[Uri]::EscapeDataString('search[freights][service_at]')+'='+$period
 for($page=1;$page-le16;$page++){
  if($target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$false}}
 }
 throw 'WIRE_LOCALIZADOR_EXACT_SERVICE_QUERY_WITHOUT_ORDER_REQUIRED'
}
function Assert-LocalizadorNativeWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('localizadorNativeGrantPin')) 'WIRE_LOCALIZADOR_NATIVE_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.localizadorNativeGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_LOCALIZADOR_UI_LINK_ONLY_NATIVE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'LOCALIZADOR'-and$grant.profile-ceq'LOCALIZADOR_8656_SERVICE_AT_NATIVE26_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_LOCALIZADOR_NATIVE_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.operation-ceq'download_from_observed_ui_link'-and$grant.downloadAuthentication-ceq'NONE') 'WIRE_LOCALIZADOR_NATIVE_SOURCE_CODE_OPERATION_BOUND'
 Need-Wire ($grant.filterScopePolicy-ceq'SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1'-and$grant.updatedAtFilter-is[string]-and$grant.updatedAtFilter-ceq''-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_LOCALIZADOR_NATIVE_SERVICE_ONLY_POLICY'
 foreach($cap in @{reportId=8656;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=1;INFOHTTPMaximum=0;DATAHTTPMaximum=0;DOWNLOADHTTPMaximum=1;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;maximumDownloadBytes=8388608;requestSeconds=30;retryMaximum=0;redirectMaximum=0}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_LOCALIZADOR_NATIVE_TYPED_SINGLE_DOWNLOAD_CAPS'
 }
 Need-Wire (($grant.fileId-is[int]-or$grant.fileId-is[long])-and$grant.fileId-gt0-and$grant.fileId-le999999999999999999-and$grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$') 'WIRE_LOCALIZADOR_NATIVE_FILE_PERIOD_IDENTITY'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7) 'WIRE_LOCALIZADOR_NATIVE_FINITE_PERIOD'
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and$created-lt$end-and($end-$created).TotalSeconds-le300-and($now-$created).TotalSeconds-lt210-and[DateTimeOffset]::Parse($message.deadline)-le$end-and[DateTimeOffset]::Parse($message.deadline)-le$now.AddSeconds(210)) 'WIRE_LOCALIZADOR_NATIVE_FRESH210_WINDOW'
 Need-Wire ($message.method-ceq'GET'-and$message.authentication-ceq'NONE'-and-not$message.Contains('jsonBody')-and$target.Scheme-ceq'https'-and$target.Port-eq443-and$target.UserInfo.Length-eq0-and$target.Fragment.Length-eq0-and-not$target.IsLoopback-and$target.IdnHost-ceq'eslcloud.s3.sa-east-1.amazonaws.com') 'WIRE_LOCALIZADOR_NATIVE_HTTPS_S3_NO_BEARER'
 Need-Wire (($message.timeoutMillis-is[int]-or$message.timeoutMillis-is[long])-and$message.timeoutMillis-gt0-and$message.timeoutMillis-le30000-and($message.maximumBodyBytes-is[int]-or$message.maximumBodyBytes-is[long])-and$message.maximumBodyBytes-gt0-and$message.maximumBodyBytes-le8388608) 'WIRE_LOCALIZADOR_NATIVE_REQUEST_BOUND'
 $link=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $grant.uiObservedLinkPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($link.schema-ceq'ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_V1'-and$link.profile-ceq$grant.profile-and$link.entity-ceq$grant.entity-and$link.reportId-eq8656-and$link.fileId-eq$grant.fileId-and$link.periodStart-ceq$grant.periodStart-and$link.periodEnd-ceq$grant.periodEnd-and$link.sourceUiHref-ceq('/analytics/report_files/'+$grant.fileId)) 'WIRE_LOCALIZADOR_NATIVE_LINK_SCOPE'
 Need-Wire ($link.targetUri-is[string]-and$message.url-is[string]-and$message.url-ceq$link.targetUri-and$target.OriginalString-ceq$link.targetUri-and$target.AbsoluteUri-ceq$link.targetUri-and$target.AbsolutePath.StartsWith('/active_storage/rodogarcia/analytics_report_files/',[StringComparison]::Ordinal)-and$target.Query.Length-gt0) 'WIRE_LOCALIZADOR_NATIVE_EXACT_OBSERVED_PRIVATE_URI'
 return @{grant=$grant;operation='download';page=0;rawPageScope=$false}
}
function Assert-FaturasClienteWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('faturasClienteGrantPin')) 'WIRE_FATURAS_CLIENTE_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.faturasClienteGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_FATURAS_CLIENTE_FINITE_SOURCE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'FATURAS_POR_CLIENTE'-and$grant.profile-ceq'FATURAS_CLIENTE_4924_SERVICE_AT_NATIVE52_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_FATURAS_CLIENTE_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256) 'WIRE_FATURAS_CLIENTE_SOURCE_CODE_BINDING'
 Need-Wire ($grant.Contains('filterScopePolicy')-and$grant.filterScopePolicy-is[string]-and$grant.filterScopePolicy-ceq'SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1'-and$grant.Contains('updatedAtFilter')-and$grant.updatedAtFilter-is[string]-and$grant.updatedAtFilter-ceq'') 'WIRE_FATURAS_CLIENTE_EXPLICIT_SERVICE_ONLY_POLICY'
 Need-Wire ($grant.Contains('requestPin')) 'WIRE_FATURAS_CLIENTE_REQUEST_PIN_REQUIRED'
 $declared=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $grant.requestPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($declared.Count-eq9-and$declared.schema-ceq'FATURAS_CLIENTE_INDEPENDENT_REQUEST_V1'-and$declared.profile-ceq$grant.profile-and$declared.entity-ceq$grant.entity-and($declared.reportId-is[int]-or$declared.reportId-is[long])-and$declared.reportId-eq4924-and$declared.attemptId-ceq$grant.attemptId-and$declared.periodStart-ceq$grant.periodStart-and$declared.periodEnd-ceq$grant.periodEnd-and$declared.zone-ceq$grant.zone-and$declared.filters-is[Collections.IDictionary]-and$declared.filters.Count-eq18) 'WIRE_FATURAS_CLIENTE_REQUEST_SCOPE_BOUND_TO_GRANT'
 foreach($name in @('freights[service_at]','scopes[by_updated_at]','scopes[from_controller]','freights[payer_id]','scopes[from_accounting_credit_status]','fit_accounting_credit[due_date]','fit_accounting_credit[customer_id]','fit_accounting_credit[paid]','fit_accounting_credit[issue_date]','fit_freight_cte[corporation_sequence_number]','freights[corporation_sequence_number]','scopes[from_seller]','freights[status]','freights[type]','freights[emission_type]','freights[courtesy]','scopes[substituted]','scopes[with_delivery_receipt]')){
  Need-Wire (@($declared.filters.Keys)-ccontains$name-and$declared.filters[$name]-is[string]-and$declared.filters[$name]-ceq$(if($name-ceq'freights[service_at]'){$grant.periodStart+' - '+$grant.periodEnd}else{''})) 'WIRE_FATURAS_CLIENTE_EIGHT_EXPLICIT_SERVICE_ONLY_FILTERS'
 }
 foreach($cap in @{reportId=4924;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=17;INFOHTTPMaximum=1;DATAHTTPMaximum=16;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;retryMaximum=0;redirectMaximum=0;per=100}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_FATURAS_CLIENTE_TYPED_FIXED_LIMIT'
 }
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and($end-$created).TotalSeconds-le300-and$created-lt$end-and[DateTimeOffset]::Parse($message.deadline)-le$end) 'WIRE_FATURAS_CLIENTE_FRESH_GRANT_WINDOW'
 Need-Wire ($grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_FATURAS_CLIENTE_PERIOD_DECLARATION'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7-and$message.method-ceq'GET'-and$message.authentication-ceq'DATA_EXPORT'-and-not$message.Contains('jsonBody')-and$target.GetLeftPart([UriPartial]::Authority)-ceq$context.apiOrigin-and$target.Fragment.Length-eq0-and$target.UserInfo.Length-eq0) 'WIRE_FATURAS_CLIENTE_GET_ONLY_DECLARED_PERIOD'
 Need-Wire (-not$grant.Contains('pageIndex')-and-not$grant.Contains('effectiveHTTPMaximum')-and-not$grant.Contains('pageScopePolicy')) 'WIRE_FATURAS_CLIENTE_NO_RAW_PAGE_SCOPE'
 $base=$context.apiOrigin+'/api/analytics/reports/4924/'
 if($target.AbsoluteUri-ceq($base+'info')){return @{grant=$grant;operation='info';page=0;rawPageScope=$false}}
 $period=[Uri]::EscapeDataString($grant.periodStart+' - '+$grant.periodEnd)
 $filter=[Uri]::EscapeDataString('search[freights][service_at]')+'='+$period
 for($page=1;$page-le16;$page++){
  if($target.AbsoluteUri-ceq($base+'data?'+$filter+'&page='+$page+'&per=100')){return @{grant=$grant;operation=('page-'+$page);page=$page;rawPageScope=$false}}
 }
 throw 'WIRE_FATURAS_CLIENTE_EXACT_SERVICE_QUERY_WITHOUT_ORDER_REQUIRED'
}
function Assert-FaturasClienteNativeWireRequest($context,$message,[Uri]$target,[DateTimeOffset]$now){
 Need-Wire ($context.Contains('faturasClienteNativeGrantPin')) 'WIRE_FATURAS_CLIENTE_NATIVE_EXPLICIT_GRANT_REQUIRED'
 $grant=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $context.faturasClienteNativeGrantPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($grant.schema-ceq'ROOT_FATURAS_CLIENTE_UI_LINK_ONLY_NATIVE_GRANT_V1'-and$grant.approved-is[bool]-and$grant.approved-and$grant.entity-ceq'FATURAS_POR_CLIENTE'-and$grant.profile-ceq'FATURAS_CLIENTE_4924_SERVICE_AT_NATIVE52_V1'-and$grant.executingRootUUID-cmatch'^[a-f0-9-]{36}$'-and$grant.executingRootUUID-ceq$context.executingRootUUID) 'WIRE_FATURAS_CLIENTE_NATIVE_GRANT_SCOPE'
 Need-Wire ($grant.sourceConfigurationSha256-ceq$context.sourceConfigurationSha256-and$grant.codeUnitSha256-ceq$context.codeUnitSha256-and$grant.operation-ceq'download_from_observed_ui_link'-and$grant.downloadAuthentication-ceq'NONE') 'WIRE_FATURAS_CLIENTE_NATIVE_SOURCE_CODE_OPERATION_BOUND'
 Need-Wire ($grant.filterScopePolicy-ceq'SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1'-and$grant.updatedAtFilter-is[string]-and$grant.updatedAtFilter-ceq''-and$grant.zone-ceq'America/Sao_Paulo') 'WIRE_FATURAS_CLIENTE_NATIVE_SERVICE_ONLY_POLICY'
 foreach($cap in @{reportId=4924;SQL=0;OriginalMaximum=1;SourceMaximum=1;HTTPMaximum=1;INFOHTTPMaximum=0;DATAHTTPMaximum=0;DOWNLOADHTTPMaximum=1;SourceSeconds=210;OriginalSeconds=300;OriginalBytes=1073741824;maximumDownloadBytes=8388608;requestSeconds=30;retryMaximum=0;redirectMaximum=0}.GetEnumerator()){
  Need-Wire ($grant.Contains($cap.Key)-and($grant[$cap.Key]-is[int]-or$grant[$cap.Key]-is[long])-and$grant[$cap.Key]-eq$cap.Value) 'WIRE_FATURAS_CLIENTE_NATIVE_TYPED_SINGLE_DOWNLOAD_CAPS'
 }
 Need-Wire (($grant.fileId-is[int]-or$grant.fileId-is[long])-and$grant.fileId-gt0-and$grant.fileId-le999999999999999999-and$grant.attemptId-cmatch'^[a-f0-9-]{36}$'-and$grant.periodStart-cmatch'^\d{4}-\d{2}-\d{2}$'-and$grant.periodEnd-cmatch'^\d{4}-\d{2}-\d{2}$') 'WIRE_FATURAS_CLIENTE_NATIVE_FILE_PERIOD_IDENTITY'
 $start=[DateTime]::ParseExact($grant.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture);$finish=[DateTime]::ParseExact($grant.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 Need-Wire ($finish-ge$start-and($finish-$start).TotalDays-lt7) 'WIRE_FATURAS_CLIENTE_NATIVE_FINITE_PERIOD'
 $created=[DateTimeOffset]::Parse($grant.createdUtc);$end=[DateTimeOffset]::Parse($grant.expiresUtc)
 Need-Wire ($created-le$now-and$now-lt$end-and$created-lt$end-and($end-$created).TotalSeconds-le300-and($now-$created).TotalSeconds-lt210-and[DateTimeOffset]::Parse($message.deadline)-le$end-and[DateTimeOffset]::Parse($message.deadline)-le$now.AddSeconds(210)) 'WIRE_FATURAS_CLIENTE_NATIVE_FRESH210_WINDOW'
 Need-Wire ($message.method-ceq'GET'-and$message.authentication-ceq'NONE'-and-not$message.Contains('jsonBody')-and$target.Scheme-ceq'https'-and$target.Port-eq443-and$target.UserInfo.Length-eq0-and$target.Fragment.Length-eq0-and-not$target.IsLoopback-and$target.IdnHost-ceq'eslcloud.s3.sa-east-1.amazonaws.com') 'WIRE_FATURAS_CLIENTE_NATIVE_HTTPS_S3_NO_BEARER'
 Need-Wire (($message.timeoutMillis-is[int]-or$message.timeoutMillis-is[long])-and$message.timeoutMillis-gt0-and$message.timeoutMillis-le30000-and($message.maximumBodyBytes-is[int]-or$message.maximumBodyBytes-is[long])-and$message.maximumBodyBytes-gt0-and$message.maximumBodyBytes-le8388608) 'WIRE_FATURAS_CLIENTE_NATIVE_REQUEST_BOUND'
 $link=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $grant.uiObservedLinkPin 262144))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 Need-Wire ($link.schema-ceq'ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_V1'-and$link.profile-ceq$grant.profile-and$link.entity-ceq$grant.entity-and$link.reportId-eq4924-and$link.fileId-eq$grant.fileId-and$link.periodStart-ceq$grant.periodStart-and$link.periodEnd-ceq$grant.periodEnd-and$link.sourceUiHref-ceq('/analytics/report_files/'+$grant.fileId)) 'WIRE_FATURAS_CLIENTE_NATIVE_LINK_SCOPE'
 Need-Wire ($link.targetUri-is[string]-and$message.url-is[string]-and$message.url-ceq$link.targetUri-and$target.OriginalString-ceq$link.targetUri-and$target.AbsoluteUri-ceq$link.targetUri-and$target.AbsolutePath.StartsWith('/active_storage/rodogarcia/analytics_report_files/',[StringComparison]::Ordinal)-and$target.Query.Length-gt0) 'WIRE_FATURAS_CLIENTE_NATIVE_EXACT_OBSERVED_PRIVATE_URI'
 return @{grant=$grant;operation='download';page=0;rawPageScope=$false}
}
function Hold-Pin($p){
 [void](Read-Pin $p 8388608)
 $stream=[IO.File]::Open($p.path,'Open','Read','Read')
 try{
  Need-Wire ($stream.Length-eq$p.bytes-and(Get-FileHash -InputStream $stream -Algorithm SHA256).Hash.ToLowerInvariant()-ceq$p.sha256) 'WIRE_HELD_PIN_DRIFT'
  $stream.Position=0;$leases.Add(@{pin=$p;stream=$stream});$stream=$null
 }finally{if($stream){$stream.Dispose()}}
}
function Fresh-Metadata([string]$path,$value){
 $b=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 20 -Compress))
 Need-Wire ($b.Length-le131072) 'WIRE_METADATA_BOUND';$s=[IO.File]::Open($path,'CreateNew','Write','Read')
 try{$s.Write($b);$s.Flush($true)}finally{$s.Dispose()}
 return @{path=$path;bytes=$b.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b)).ToLowerInvariant()}
}
# Same Native guard uses this existing metadata writer name.
function S2Fresh([string]$path,$value,[int]$maximum){Fresh-Metadata $path $value}
function Set-Stop([int]$code){
 if($code-eq429){
  $latch=Join-Path $c.sharedSourceDirectory 'STOP429.json'
  if(-not[IO.File]::Exists($latch)){[void](Fresh-Metadata $latch @{schema='ESL_SHARED_STOP429_V1';sourceConfigurationSha256=$c.sourceConfigurationSha256;observedUtc=[DateTimeOffset]::UtcNow.ToString('o');operationId=$request.operationId;status=429})}
 }
}
function Inspect-Headers([byte[]]$bytes,[int]$length){
 $at=0
 while($at-lt$length){
  $end=-1
  for($i=$at;$i+3-lt[math]::Min($length,65537);$i++){if($bytes[$i]-eq13-and$bytes[$i+1]-eq10-and$bytes[$i+2]-eq13-and$bytes[$i+3]-eq10){$end=$i+4;break}}
  if($end-lt0){Need-Wire ($length-le65536) 'WIRE_STREAM_HEADERS_65536';return $null}
  Need-Wire ($end-le65536) 'WIRE_STREAM_HEADERS_65536'
  $header=[Text.Encoding]::Latin1.GetString($bytes,$at,$end-$at);$line=($header-split'\r\n')[0]
  Need-Wire ($line-cmatch'^HTTP/(?:1\.[01]|2|3) ([1-5][0-9]{2})(?: |$)') 'WIRE_HTTP_STATUS_LINE';$code=[int]$Matches[1]
  if($code-ge100-and$code-lt200){$at=$end;continue};Set-Stop $code;return @{status=$code;bodyStart=$end}
 }
 return $null
}
try{
 Need-Wire ([IO.Path]::IsPathFullyQualified($ContextPath)-and[IO.File]::Exists($ContextPath)) 'WIRE_CONTEXT_REQUIRED'
 $b=[IO.File]::ReadAllBytes($ContextPath)
 Need-Wire ($b.Length-le262144-and[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b)).ToLowerInvariant()-ceq$ContextSha256) 'WIRE_CONTEXT_PIN'
 $c=[Text.UTF8Encoding]::new($false,$true).GetString($b)|ConvertFrom-Json -AsHashtable -Depth 40 -DateKind String
 Need-Wire ($c.schema-ceq'ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1') 'WIRE_CONTEXT_SCHEMA'
 foreach($name in @('physicalDll','childJobDll','nativeGuard','privacyGuard','privacyPolicy','selection','provider','curl')){Hold-Pin $c[$name]}
 [void][Reflection.Assembly]::LoadFrom($c.physicalDll.path);[void][Reflection.Assembly]::LoadFrom($c.childJobDll.path)
 $job=[ColetasWeeklyS2.OwnJob]::ObserveCurrentOriginal()
 Need-Wire ($job.Pid-eq$PID-and$job.JobLimitBytes-eq1073741824-and$job.ProcessLimitBytes-eq1073741824-and($job.Flags-band8960)-eq8960) 'WIRE_PROTECTED_ROOT_ORIGINAL'
 . $c.nativeGuard.path;. $c.privacyGuard.path
 Need-Wire ($c.ownedMainImage-cin@('java.exe','javaw.exe')) 'WIRE_EXPLICIT_MAIN_IMAGE_NAME'
 $mainProofPin=@{path=$MainProofPath;bytes=([IO.FileInfo]::new($MainProofPath)).Length;sha256=$MainProofSha256}
 Hold-Pin $mainProofPin
 $mainClaim=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $mainProofPin 8192))|ConvertFrom-Json -AsHashtable -Depth 10 -DateKind String
 Need-Wire ($mainClaim.schema-ceq'INDEPENDENT_OWNED_MAIN_SELF_PROOF_V1'-and$mainClaim.pid-gt0-and$mainClaim.parentPid-gt0-and$mainClaim.imageName-ceq$c.ownedMainImage) 'WIRE_MAIN_CLAIM'
 $workerProcess=[Diagnostics.Process]::GetCurrentProcess()
 try{Need-Wire ([EslIndependentExport.OwnedCurlJob]::ParentProcessId($workerProcess)-eq$mainClaim.pid) 'WIRE_ACTUAL_WORKER_PARENT'}finally{$workerProcess.Dispose()}
 $mainProcess=[Diagnostics.Process]::GetProcessById([int]$mainClaim.pid)
 $mainBirth=[DateTimeOffset]::new($mainProcess.StartTime.ToUniversalTime())
 Need-Wire (-not$mainProcess.HasExited-and[EslIndependentExport.OwnedCurlJob]::ParentProcessId($mainProcess)-eq$mainClaim.parentPid-and$mainBirth.ToUnixTimeMilliseconds()-eq$mainClaim.startEpochMillis-and[EslIndependentExport.OwnedCurlJob]::CurrentOriginalContainsProcess($mainProcess.Id)) 'WIRE_MAIN_KERNEL_BIRTH_PARENT_ORIGINAL'
 Need-Wire ($mainClaim.codeUnitSha256-ceq$c.codeUnitSha256) 'WIRE_MAIN_CODE_UNIT_BINDING'
 $cimBirth=$mainBirth.AddTicks(-($mainBirth.Ticks%10))
 $ownedMain=@{pid=[long]$mainClaim.pid;parentPid=[long]$mainClaim.parentPid;name=$c.ownedMainImage;creationUtc=$cimBirth.ToUniversalTime().ToString('o');kernelBirthUtc=$mainBirth.ToUniversalTime().ToString('o');birthPrecision='CIM_MICROSECOND';workerPid=[long]$PID;selfProofPin=$mainProofPin}
 $privacyPolicy=Read-PrivacyPolicy0712 $c.privacyPolicy
 [void](Assert-PrivatePath0712 $c.privateRoot $privacyPolicy $true);[void](Assert-PrivatePath0712 $c.sharedSourceDirectory $privacyPolicy $false)
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP429.json'))) 'WIRE_SHARED_STOP429'
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP_UNKNOWN.json'))) 'WIRE_SHARED_UNKNOWN_EFFECT'
 $guard=[IO.File]::Open((Join-Path $c.sharedSourceDirectory 'wire.lock'),'OpenOrCreate','ReadWrite','None')
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP429.json'))) 'WIRE_SHARED_STOP429_AFTER_LOCK'
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP_UNKNOWN.json'))) 'WIRE_SHARED_UNKNOWN_AFTER_LOCK'
 $lastResponse=Join-Path $c.sharedSourceDirectory 'last-response.json'
 if([IO.File]::Exists($lastResponse)){
  # Three fresh monotonic seconds after obtaining the shared channel also covers restart.
  $gap=[Diagnostics.Stopwatch]::StartNew()
  while($gap.ElapsedMilliseconds-lt3000){[Threading.Thread]::Sleep(10)}
 }
 [Console]::Out.WriteLine('{"schema":"ROOT_WIRE_READY_V1","privateInputMayFollow":true}');[Console]::Out.Flush()
 $inputStream=[Console]::OpenStandardInput();$message=[IO.MemoryStream]::new();$buffer=[byte[]]::new(4096)
 try{
  $inputWatch=[Diagnostics.Stopwatch]::StartNew()
  while($true){
   $read=$inputStream.ReadAsync($buffer,0,$buffer.Length)
   Need-Wire ($read.Wait([math]::Max(1,5000-$inputWatch.ElapsedMilliseconds))) 'WIRE_PRIVATE_STDIN_DEADLINE'
   $n=$read.GetAwaiter().GetResult();if($n-eq0){break}
   Need-Wire ($message.Length+$n-le1048576-and$inputWatch.ElapsedMilliseconds-lt5000) 'WIRE_STDIN_BOUND'
   $message.Write($buffer,0,$n)
  }
  $request=[Text.UTF8Encoding]::new($false,$true).GetString($message.ToArray())|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 }finally{$inputStream.Dispose();$message.Dispose()}
 Need-Wire ($request.schema-ceq'INDEPENDENT_SINGLE_WIRE_REQUEST_V1'-and$request.maximumBodyBytes-gt0-and$request.maximumBodyBytes-le8388608-and$request.timeoutMillis-gt0-and$request.timeoutMillis-le30000) 'WIRE_REQUEST_BOUND'
 Need-Wire ($request.sourceConfigurationSha256-ceq$c.sourceConfigurationSha256-and[DateTimeOffset]::UtcNow-lt[DateTimeOffset]::Parse($request.deadline)) 'WIRE_CONFIGURATION_AND_DEADLINE'
 Need-Wire ($request.operationId-cmatch'^[a-f0-9-]{36}$'-and$request.method-cin@('GET','POST')) 'WIRE_OPERATION'
 $c.run=Join-Path $c.privateRoot $request.operationId
 Need-Wire (-not(Test-Path -LiteralPath $c.run)) 'WIRE_FRESH_OPERATION_DIRECTORY'
 [void][IO.Directory]::CreateDirectory($c.run);[void](Assert-PrivatePath0712 $c.run $privacyPolicy $false)
 $selected=[Text.UTF8Encoding]::new($false,$true).GetString((Read-Pin $c.selection 131072))|ConvertFrom-Json -AsHashtable -Depth 20 -DateKind String
 # Validate the Root external selection intact before adding the independently proven Main.
 WValidateSelection $selected
 $ownedTuple=WTuple $ownedMain;$selectedRows=[Collections.Generic.List[object]]::new();$already=$false
 foreach($row in $selected.selectedIdentities){if($row.pid-eq$ownedMain.pid){Need-Wire ((WTuple $row)-ceq$ownedTuple) 'WIRE_MAIN_EXTERNAL_SELECTION_CONFLICT';$already=$true};$selectedRows.Add($row)}
 if(-not$already){$selectedRows.Add($ownedMain)}
 $selected.selectedIdentities=@($selectedRows);$selected.selectedIdentityCount=$selectedRows.Count;$selected.ownedMainProof=$ownedMain
 $operationRun=$c.run;$c.run=Join-Path $operationRun 'native-before-provider'
 [void][IO.Directory]::CreateDirectory($c.run)
 $native=WNativeGate $selected;$c.run=$operationRun
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP429.json'))) 'WIRE_SHARED_STOP429_BEFORE_PROVIDER'
 $url=[Uri]::new($request.url,[UriKind]::Absolute)
 Need-Wire ($url.Scheme-ceq'https'-and$url.UserInfo.Length-eq0-and$url.Fragment.Length-eq0-and$url.Port-eq443-and-not$url.IsLoopback) 'WIRE_HTTPS_ORIGIN'
 if($c.Contains('faturasClienteNativeGrantPin')){Need-Wire ($request.authentication-ceq'NONE'-and$request.method-ceq'GET') 'WIRE_FATURAS_CLIENTE_NATIVE_NO_BEARER_OR_API_OPERATION'}
 if($c.Contains('localizadorNativeGrantPin')){Need-Wire ($request.authentication-ceq'NONE'-and$request.method-ceq'GET') 'WIRE_LOCALIZADOR_NATIVE_NO_BEARER_OR_API_OPERATION'}
 if($request.authentication-ceq'DATA_EXPORT'){
  $manifestos=($url.AbsolutePath-cmatch'^/api/analytics/reports/6399/(data|info)$')
  $cotacoes=($url.AbsolutePath-cmatch'^/api/analytics/reports/6906/(data|info)$')
  $inventario=($url.AbsolutePath-cmatch'^/api/analytics/reports/10633/(data|info)$')
  $sinistros=($url.AbsolutePath-cmatch'^/api/analytics/reports/6392/(data|info)$')
  $contasPagar=($url.AbsolutePath-cmatch'^/api/analytics/reports/8636/(data|info)$')
  $localizador=($url.AbsolutePath-cmatch'^/api/analytics/reports/8656/(data|info)$')
  $faturasCliente=($url.AbsolutePath-cmatch'^/api/analytics/reports/4924/(data|info)$')
  Need-Wire ($url.GetLeftPart([UriPartial]::Authority)-ceq$c.apiOrigin-and(
   ($request.method-ceq'POST'-and$url.AbsolutePath-ceq'/api/analytics/reports/6389/export')-or
   ($request.method-ceq'GET'-and$url.AbsolutePath-cmatch'^/api/analytics/report_files/[1-9][0-9]{0,17}$')-or
   ($request.method-ceq'GET'-and$url.AbsolutePath-cmatch'^/api/analytics/reports/6908/(data|info)$')-or
   ($request.method-ceq'GET'-and($manifestos-or$cotacoes-or$inventario-or$sinistros-or$contasPagar-or$localizador-or$faturasCliente)))) 'WIRE_CLOSED_API_ROUTE'
  if($manifestos-or$cotacoes-or$inventario-or$sinistros-or$contasPagar-or$localizador-or$faturasCliente){
   if($faturasCliente){
    Need-Wire ($c.Contains('faturasClienteGrantPin')) 'WIRE_FATURAS_CLIENTE_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.faturasClienteGrantPin;$finiteEntity='FATURAS_POR_CLIENTE'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-FaturasClienteWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }elseif($localizador){
    Need-Wire ($c.Contains('localizadorGrantPin')) 'WIRE_LOCALIZADOR_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.localizadorGrantPin;$finiteEntity='LOCALIZADOR'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-LocalizadorWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }elseif($contasPagar){
    Need-Wire ($c.Contains('contasPagarGrantPin')) 'WIRE_CONTAS_PAGAR_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.contasPagarGrantPin;$finiteEntity='CONTAS_A_PAGAR'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-ContasPagarWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }elseif($sinistros){
    Need-Wire ($c.Contains('sinistrosGrantPin')) 'WIRE_SINISTROS_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.sinistrosGrantPin;$finiteEntity='SINISTROS'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-SinistrosWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }elseif($inventario){
    Need-Wire ($c.Contains('inventarioGrantPin')) 'WIRE_INVENTARIO_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.inventarioGrantPin;$finiteEntity='INVENTARIO'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-InventarioWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }elseif($cotacoes){
    Need-Wire ($c.Contains('cotacoesGrantPin')) 'WIRE_COTACOES_EXPLICIT_GRANT_REQUIRED'
    $finiteGrantPin=$c.cotacoesGrantPin;$finiteEntity='COTACOES'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-CotacoesWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }else{
    $finiteGrantPin=$c.manifestosGrantPin;$finiteEntity='MANIFESTOS'
    Hold-Pin $finiteGrantPin
    $admitted=Assert-ManifestosWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   }
   $grantDirectory=Join-Path $c.sharedSourceDirectory ($finiteEntity.ToLowerInvariant()+'-'+$admitted.grant.attemptId)
   if($admitted.page-eq0){Need-Wire (-not(Test-Path -LiteralPath $grantDirectory)) 'WIRE_MANIFESTOS_GRANT_ALREADY_STARTED';[void][IO.Directory]::CreateDirectory($grantDirectory)}
   Need-Wire ([IO.Directory]::Exists($grantDirectory)) 'WIRE_MANIFESTOS_INFO_MUST_PRECEDE_DATA'
   [void](Assert-PrivatePath0712 $grantDirectory $privacyPolicy $false)
   if($admitted.page-gt0){
    $first=Read (Join-Path $grantDirectory 'info.reserved.json')
    Need-Wire ([DateTimeOffset]::UtcNow-lt[DateTimeOffset]::Parse($first.sourceDeadlineUtc)) 'WIRE_MANIFESTOS_SOURCE210_EXHAUSTED'
    # A raw-page grant authorizes exactly INFO+N, with no interval-completeness claim.
    # Its final day assembler separately requires contiguous disjoint pages and native EOF coverage.
    $previous=if($admitted.page-eq1-or$admitted.rawPageScope){'info'}else{'page-'+($admitted.page-1)}
    $priorComplete=Read (Join-Path $grantDirectory ($previous+'.complete.json'))
    Need-Wire ($priorComplete.status-eq200-and$priorComplete.complete-eq$true) 'WIRE_MANIFESTOS_PREVIOUS_COMPLETE200_REQUIRED'
   }
   [void](Fresh-Metadata (Join-Path $grantDirectory ($admitted.operation+'.reserved.json')) @{schema=('ROOT_'+$finiteEntity+'_FINITE_WIRE_RESERVATION_V1');attemptId=$admitted.grant.attemptId;grantPin=$finiteGrantPin;operation=$admitted.operation;wireOperationId=$request.operationId;reservedUtc=[DateTimeOffset]::UtcNow.ToString('o');sourceDeadlineUtc=[DateTimeOffset]::UtcNow.AddSeconds(210).ToString('o');retryMaximum=0;SQL=0})
  }
  $tokens=$null;$errors=$null;$ast=[Management.Automation.Language.Parser]::ParseFile($c.provider.path,[ref]$tokens,[ref]$errors)
  $fn=@($ast.FindAll({param($node)$node-is[Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-ceq'Get-LastNonEmptyEnvValue'},$false))
  Need-Wire ($errors.Count-eq0-and$fn.Count-eq1) 'WIRE_EXISTING_PROVIDER_AST';. ([scriptblock]::Create($fn[0].Extent.Text))
  WNoLinks $c.credentialEnvironmentPath
  $origin=Get-LastNonEmptyEnvValue $c.credentialEnvironmentPath 'API_BASE_URL'
  Need-Wire ($origin.TrimEnd('/')-ceq$c.apiOrigin) 'WIRE_EXISTING_PROVIDER_ORIGIN'
  $token=Get-LastNonEmptyEnvValue $c.credentialEnvironmentPath 'API_DATAEXPORT_TOKEN'
  Need-Wire ($token.Length-gt0-and$token.Length-le4096-and$token-cnotmatch'[\x00-\x20\x7f]') 'WIRE_PROVIDER_TOKEN_FORMAT'
 }else{Need-Wire ($request.authentication-ceq'NONE'-and$request.method-ceq'GET'-and$c.downloadHosts-ccontains$url.IdnHost) 'WIRE_NO_BEARER_DOWNLOAD_HOST'
  if($c.Contains('faturasClienteNativeGrantPin')){
   $faturasClienteNative=$true;$finiteEntity='FATURAS_CLIENTE_NATIVE';$finiteGrantPin=$c.faturasClienteNativeGrantPin
   Hold-Pin $finiteGrantPin
   $admitted=Assert-FaturasClienteNativeWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   Hold-Pin $admitted.grant.uiObservedLinkPin
   $grantDirectory=Join-Path $c.sharedSourceDirectory ('faturasCliente_native-'+$admitted.grant.attemptId)
   Need-Wire (-not(Test-Path -LiteralPath $grantDirectory)) 'WIRE_FATURAS_CLIENTE_NATIVE_GRANT_ALREADY_STARTED'
   [void][IO.Directory]::CreateDirectory($grantDirectory);[void](Assert-PrivatePath0712 $grantDirectory $privacyPolicy $false)
   [void](Fresh-Metadata (Join-Path $grantDirectory 'download.reserved.json') @{schema='ROOT_FATURAS_CLIENTE_NATIVE_FINITE_WIRE_RESERVATION_V1';attemptId=$admitted.grant.attemptId;grantPin=$finiteGrantPin;operation='download';wireOperationId=$request.operationId;reservedUtc=[DateTimeOffset]::UtcNow.ToString('o');retryMaximum=0;SQL=0})
  }
  if($c.Contains('localizadorNativeGrantPin')){
   $localizadorNative=$true;$finiteEntity='LOCALIZADOR_NATIVE';$finiteGrantPin=$c.localizadorNativeGrantPin
   Hold-Pin $finiteGrantPin
   $admitted=Assert-LocalizadorNativeWireRequest $c $request $url ([DateTimeOffset]::UtcNow)
   Hold-Pin $admitted.grant.uiObservedLinkPin
   $grantDirectory=Join-Path $c.sharedSourceDirectory ('localizador_native-'+$admitted.grant.attemptId)
   Need-Wire (-not(Test-Path -LiteralPath $grantDirectory)) 'WIRE_LOCALIZADOR_NATIVE_GRANT_ALREADY_STARTED'
   [void][IO.Directory]::CreateDirectory($grantDirectory);[void](Assert-PrivatePath0712 $grantDirectory $privacyPolicy $false)
   [void](Fresh-Metadata (Join-Path $grantDirectory 'download.reserved.json') @{schema='ROOT_LOCALIZADOR_NATIVE_FINITE_WIRE_RESERVATION_V1';attemptId=$admitted.grant.attemptId;grantPin=$finiteGrantPin;operation='download';wireOperationId=$request.operationId;reservedUtc=[DateTimeOffset]::UtcNow.ToString('o');retryMaximum=0;SQL=0})
  }
 }
 $nl=[string][char]10
 $curlConfig='url = '+(WQuote $request.url)+$nl+'proxy = '+(WQuote '')+$nl+'noproxy = '+(WQuote '*')+$nl
 if($token){$curlConfig+='header = '+(WQuote ('Authorization: Bearer '+$token))+$nl}
 if($request.Contains('jsonBody')-and$null-ne$request.jsonBody){
  Need-Wire ($request.authentication-ceq'DATA_EXPORT'-and$request.jsonBody-is[string]-and$request.jsonBody.Length-le262144) 'WIRE_JSON_REQUEST_BODY'
  $curlConfig+='header = '+(WQuote 'Content-Type: application/json')+$nl+'data-binary = '+(WQuote $request.jsonBody)+$nl
 }
 $marker='__ESL_'+[guid]::NewGuid().ToString('N')+'__'
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$c.curl.path;$start.WorkingDirectory=$c.run
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardInput=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 $seconds=([decimal]$request.timeoutMillis/1000).ToString([Globalization.CultureInfo]::InvariantCulture)
 foreach($arg in @('--disable','--config','-','--globoff','--silent','--show-error','--include','--request',$request.method,'--proto','=https','--max-redirs','0','--retry','0','--connect-timeout','10','--max-time',$seconds,'--max-filesize',[string]$request.maximumBodyBytes,'--output','-','--write-out',($marker+'%{http_code}'+$marker))){[void]$start.ArgumentList.Add($arg)}
 $start.Environment.Clear();$start.Environment['SystemRoot']='C:\Windows';$start.Environment['WINDIR']='C:\Windows'
 # Re-run the exact native census immediately before every actual wire attempt.
 $c.run=Join-Path $operationRun 'native-before-send';[void][IO.Directory]::CreateDirectory($c.run)
 $native=WNativeGate $selected;$c.run=$operationRun
 Need-Wire (-not[IO.File]::Exists((Join-Path $c.sharedSourceDirectory 'STOP429.json'))) 'WIRE_SHARED_STOP429_BEFORE_SEND'
 $remainingSeconds=([DateTimeOffset]::Parse($request.deadline)-[DateTimeOffset]::UtcNow).TotalSeconds-2
 Need-Wire ($remainingSeconds-gt0) 'WIRE_REQUEST_DEADLINE_BEFORE_START'
 $timeIndex=$start.ArgumentList.IndexOf('--max-time')+1
 $start.ArgumentList[$timeIndex]=Get-CurlTimeArgument $remainingSeconds $request.timeoutMillis
 $child=[EslIndependentExport.DetachedOwnedCurl]::StartSuspended($start);$started=$true;$curlPid=$child.Id;$birth=$child.StartTime.ToUniversalTime().ToString('o')
 $childJob=[EslIndependentExport.OwnedCurlJob]::AttachBeforeInput($child.Process)
 $child.ResumeAfterAttachment($childJob)
 $out=[IO.MemoryStream]::new();$outBuffer=[byte[]]::new(8192);$errBuffer=[byte[]]::new(4096)
 $outTask=$child.StandardOutput.BaseStream.ReadAsync($outBuffer,0,$outBuffer.Length);$errTask=$child.StandardError.BaseStream.ReadAsync($errBuffer,0,$errBuffer.Length)
 Need-Wire (([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($native.observedUtc)).TotalMilliseconds-lt3000-and[DateTimeOffset]::UtcNow-lt[DateTimeOffset]::Parse($request.deadline)) 'WIRE_NATIVE_AGE_BEFORE_INPUT'
 $write=$child.StandardInput.WriteAsync($curlConfig);Need-Wire ($write.Wait(2000)) 'WIRE_STDIN_WRITE_TIMEOUT'
 [void]$write.GetAwaiter().GetResult();$child.StandardInput.Close();$stdinClosed=$true;$submittedAt=[DateTimeOffset]::UtcNow.ToString('o');$curlConfig=$null;$token=$null
 $watch=[Diagnostics.Stopwatch]::StartNew();$headers=$null;$errorBytes=0L
 while($true){
  Need-Wire ($watch.ElapsedMilliseconds-lt$request.timeoutMillis+5000-and[DateTimeOffset]::UtcNow-lt[DateTimeOffset]::Parse($request.deadline)) 'WIRE_FINITE_DEADLINE'
  if(-not$stdoutEof-and$outTask.IsCompleted){
   $n=$outTask.GetAwaiter().GetResult()
   if($n-eq0){$stdoutEof=$true}else{
    Need-Wire ($out.Length+$n-le$request.maximumBodyBytes+65536+512) 'WIRE_STREAM_RESPONSE_BOUND'
    $out.Write($outBuffer,0,$n)
    if($null-eq$headers){$headers=Inspect-Headers $out.GetBuffer() ([int]$out.Length)}
    elseif($out.Length-$headers.bodyStart-512-gt$request.maximumBodyBytes){throw 'WIRE_STREAM_BODY_BOUND'}
    $outTask=$child.StandardOutput.BaseStream.ReadAsync($outBuffer,0,$outBuffer.Length)
   }
  }
  if(-not$stderrEof-and$errTask.IsCompleted){$n=$errTask.GetAwaiter().GetResult();if($n-eq0){$stderrEof=$true}else{$errorBytes+=$n;Need-Wire ($errorBytes-le65536) 'WIRE_STDERR_BOUND';$errorOut.Write($errBuffer,0,$n);$errTask=$child.StandardError.BaseStream.ReadAsync($errBuffer,0,$errBuffer.Length)}}
  if($child.HasExited-and$stdoutEof-and$stderrEof){$closed=$true;break};[Threading.Thread]::Sleep(10)
 }
 $curlExitCode=$child.ExitCode
 Need-Wire ($curlExitCode-eq0-and$null-ne$headers) 'WIRE_CURL_EXIT_OR_HEADERS'
 $bytes=$out.ToArray();$tail=[Text.Encoding]::ASCII.GetString($bytes,[math]::Max(0,$bytes.Length-256),[math]::Min(256,$bytes.Length))
 Need-Wire ($tail-cmatch([regex]::Escape($marker)+'(?<status>[1-5][0-9]{2})'+[regex]::Escape($marker)+'$')) 'WIRE_TRAILER'
 $status=[int]$Matches.status;Need-Wire ($status-eq$headers.status) 'WIRE_STATUS_BINDING'
 $end=$bytes.Length-(2*$marker.Length+3);$bodyLength=$end-$headers.bodyStart
 Need-Wire ($bodyLength-ge0-and$bodyLength-le$request.maximumBodyBytes) 'WIRE_COMPLETE_BODY_BOUND'
 [void](Assert-PrivatePath0712 $c.run $privacyPolicy $false)
 $body=[byte[]]::new($bodyLength);[Array]::Copy($bytes,$headers.bodyStart,$body,0,$bodyLength)
 $bodyPath=Join-Path $c.run 'response.body';$s=[IO.File]::Open($bodyPath,'CreateNew','Write','Read')
 try{$s.Write($body);$s.Flush($true)}finally{$s.Dispose()}
 $bodyPin=@{path=$bodyPath;bytes=$body.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($body)).ToLowerInvariant()};$failure=$null
 WPostcheck
 if($manifestos-or$cotacoes-or$inventario-or$sinistros-or$contasPagar-or$localizador-or$localizadorNative-or$faturasCliente-or$faturasClienteNative){[void](Fresh-Metadata (Join-Path $grantDirectory ($admitted.operation+'.complete.json')) @{schema=('ROOT_'+$finiteEntity+'_FINITE_WIRE_COMPLETE_V1');attemptId=$admitted.grant.attemptId;operation=$admitted.operation;wireOperationId=$request.operationId;status=$status;complete=$true;bodyPin=$bodyPin;completedUtc=[DateTimeOffset]::UtcNow.ToString('o')})}
 $lastTemporary=Join-Path $c.sharedSourceDirectory ('response-'+$request.operationId+'.pending.json')
 [void](Fresh-Metadata $lastTemporary @{completedAt=[DateTimeOffset]::UtcNow.ToString('o');operationId=$request.operationId})
 Publish-WireResponseMarker $lastTemporary $lastResponse
}catch{$safeFailureCode=if($_.Exception.Message-cmatch'^[A-Z][A-Z0-9_]{1,159}$'){$_.Exception.Message}else{'ROOT_PRIVATE_REFUSAL_DETAIL_WITHHELD'};$failure='ROOT_PHYSICAL_WIRE_REFUSED_OR_UNKNOWN'}
finally{
 $token=$null;$curlConfig=$null
 if($mainProcess){$mainProcess.Dispose()}
 if($started-and-not$closed){try{if($childJob){$childJob.Stop()}elseif(-not$child.HasExited){$child.Kill($true)};$closed=$child.WaitForExit(5000)}catch{$closed=$false}}
 $jobFacts=$null
 if($childJob){try{$jobFacts=$childJob.Observe()}catch{$failure='ROOT_CHILD_JOB_RECEIPT_UNKNOWN'};$childJob.Dispose()}
 if($child){try{$child.StandardInput.Close()}catch{};try{$child.StandardOutput.Dispose();$child.StandardError.Dispose()}catch{};$child.Dispose()}
 if($started-and$null-ne$failure){
  try{
   if($errorOut.Length-gt0){
    [void](Assert-PrivatePath0712 $operationRun $privacyPolicy $false)
    $privateError=$errorOut.ToArray();$privateErrorPath=Join-Path $operationRun 'transfer.stderr.private'
    $s=[IO.File]::Open($privateErrorPath,'CreateNew','Write','Read');try{$s.Write($privateError);$s.Flush($true)}finally{$s.Dispose()}
    $privateStderrPin=@{path=$privateErrorPath;bytes=$privateError.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($privateError)).ToLowerInvariant()}
   }
   if($out-and$out.Length-gt0){
    [void](Assert-PrivatePath0712 $operationRun $privacyPolicy $false)
    $partial=$out.ToArray();$partialPath=Join-Path $operationRun 'response.partial.raw'
    $s=[IO.File]::Open($partialPath,'CreateNew','Write','Read');try{$s.Write($partial);$s.Flush($true)}finally{$s.Dispose()}
    $partialPin=@{path=$partialPath;bytes=$partial.Length;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($partial)).ToLowerInvariant()}
   }
   $unknown=Join-Path $c.sharedSourceDirectory 'STOP_UNKNOWN.json'
   if(-not[IO.File]::Exists($unknown)){[void](Fresh-Metadata $unknown @{schema='ESL_SHARED_UNKNOWN_WIRE_EFFECT_V1';operationId=$request.operationId;observedUtc=[DateTimeOffset]::UtcNow.ToString('o');rootReconciliationRequired=$true})}
  }catch{$failure='ROOT_PARTIAL_RETENTION_OR_UNKNOWN_LATCH_FAILED'}
 }
 if($guard){$guard.Dispose()};if($out){$out.Dispose()};$errorOut.Dispose();foreach($lease in $leases){$lease.stream.Dispose()}
 $receipt=@{schema='ROOT_INDEPENDENT_SINGLE_WIRE_RESULT_V1';operationId=if($request){$request.operationId}else{$null};OriginalPid=if($job){$job.Pid}else{$null};status=$status;bodyPin=$bodyPin;partialResponsePin=$partialPin;started=$started;configSubmittedAt=$submittedAt;ownedCurlPid=$curlPid;kernelBirthUtc=$birth;closedKnown=$closed;stdinClosedKnown=$stdinClosed;stdoutEof=$stdoutEof;stderrEof=$stderrEof;stderrBytes=$errorBytes;childJobFacts=$jobFacts;failure=$failure;completedAt=[DateTimeOffset]::UtcNow.ToString('o');credentialValuesSerialized=$false;URLSerialized=$false;sourceAuthorityGranted=$false}
 $receipt.safeFailureCode=$safeFailureCode;$receipt.curlExitCode=$curlExitCode;$receipt.privateStderrPin=$privateStderrPin
 [Console]::Out.WriteLine(($receipt|ConvertTo-Json -Compress -Depth 8));[Console]::Out.Flush()
}
if($null-eq$failure-and$closed-and$stdinClosed-and$stdoutEof-and$stderrEof){exit 0}else{exit 1}

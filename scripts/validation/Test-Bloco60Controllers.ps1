#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$evidence=Join-Path $root ('target/bloco60-local/controllers-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($evidence)
Import-Module "$PSScriptRoot/Bloco60Budget.psm1" -Force
$ledger=Join-Path $evidence 'synthetic-ledger.jsonl';$now=[DateTimeOffset]'2026-09-10T12:00:00Z';$checks=0
function Event($event){Write-Bloco60Ledger -Path $ledger -Event $event -Now $now|Out-Null}
function Reject([scriptblock]$Work,[string]$Reason){$caught='NONE';try{& $Work}catch{$caught=$_.Exception.Message};if($caught -cne $Reason){throw ('GUARD_EXPECTED_'+$Reason+'_OBSERVED_'+$caught)};$script:checks++}
Reject {Event @{type='RESERVE';id='FIRST';kind='SQL'}} 'B60_OPEN_REQUIRED'
Event @{type='OPEN';package='a'*64}
Reject {Event @{type='OPEN';package='a'*64}} 'B60_OWN_VALIDITY_OR_SINGLE_OPEN'
Event @{type='RESERVE';id='FIRST';kind='JVM'}
Event @{type='OBSERVE';id='FIRST';outcome='UNKNOWN';evidence='b'*64}
Reject {Event @{type='RESERVE';id='SECOND';kind='SQL'}} 'B60_RECONCILIATION_REQUIRED'
Event @{type='RECONCILE';id='FIRST';outcome='CONFIRMED';evidence='c'*64}
Reject {Event @{type='RESERVE';id='FIRST';kind='JVM'}} 'B60_RESERVATION_ID'
for($i=2;$i -le 80;$i++){Event @{type='RESERVE';id=('JVM_'+$i);kind='JVM'};Event @{type='OBSERVE';id=('JVM_'+$i);outcome='REFUSED';evidence='d'*64}}
Reject {Event @{type='RESERVE';id='EXCESS';kind='JVM'}} 'B60_OWN_BUDGET_EXHAUSTED'
$now=$now.AddMinutes(61)
Reject {Event @{type='RESERVE';id='EXPIRED';kind='SQL'}} 'B60_CLOSED_OR_EXPIRED'
Event @{type='CLOSE'}
$bytes=[IO.File]::ReadAllBytes($ledger);$bytes[40]=$bytes[40] -bxor 1;[IO.File]::WriteAllBytes($ledger,$bytes)
Reject {Event @{type='CLOSE'}} 'B60_LEDGER_CHAIN'
Add-Type -Path "$PSScriptRoot/Bloco60Process.cs"
Add-Type -Path "$PSScriptRoot/Bloco60AdminProcess.cs"
$childScript=Join-Path $evidence 'owned-child.ps1'
[IO.File]::WriteAllText($childScript,"Write-Output 'B60_BARRIER_READY'`nStart-Sleep -Seconds 3`nexit 7`n")
function ChildStart {
 $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command pwsh -CommandType Application).Source
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden';$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
 foreach($arg in @('-NoProfile','-File',$childScript)){$start.ArgumentList.Add($arg)}
 return $start
}
$one=[Bloco60AdminJob]::new((ChildStart));$two=[Bloco60AdminJob]::new((ChildStart))
try {
 $refused=$false;try{$unexpected=[Bloco60AdminJob]::new((ChildStart));$unexpected.Dispose()}catch{if($_.Exception.ToString().Contains('B60_TWO_ADMIN_PROCESS_LIMIT')){$refused=$true}else{throw}}
 if(-not $refused){throw 'B60_ADMIN_PROCESS_LIMIT_NOT_ENFORCED'};$checks++
 if(-not $one.Ready.Wait([TimeSpan]::FromSeconds(10)) -or $one.ProcessId -eq $two.ProcessId){throw 'B60_ADMIN_BARRIER_OR_ID'}
 foreach($job in @($one,$two)){$out=$job.Completion.GetAwaiter().GetResult();if($out.Code -ne 7 -or $out.Limited -or -not $out.Output.Contains('B60_BARRIER_READY')){throw 'B60_ADMIN_RESULT'}}
 $checks++
}finally{$one.Dispose();$two.Dispose()}
$server=[Bloco60Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'),0)
$client=[Net.Http.HttpClient]::new();$client.Timeout=[TimeSpan]::FromSeconds(5)
try {
 $client.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$server.Token)
 foreach($after in @($null,'B60_SECOND_PAGE')){
  $body=@{operationName='V2UsersSnapshot';query='query V2UsersSnapshot($params: IndividualInput!, $after: String, $first: Int!) { individual(params: $params, after: $after, first: $first) { edges { node { id name } } pageInfo { hasNextPage endCursor } } }';variables=@{params=@{enabled=$true};after=$after;first=20}}|ConvertTo-Json -Depth 8
  $content=[Net.Http.StringContent]::new($body,[Text.Encoding]::UTF8,'application/json')
  $response=$client.PostAsync(('http://127.0.0.1:'+$server.Port+'/graphql'),$content).GetAwaiter().GetResult()
  try {$parsed=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json;if($response.StatusCode -ne 200 -or $parsed.data.individual.pageInfo.hasNextPage -ne ($null -eq $after)){throw 'LOOPBACK_GRAPHQL_SEQUENCE'}}finally{$response.Dispose();$content.Dispose()}
 }
 if($server.Requests -ne 2 -or $server.GraphQlRequests -ne 2 -or $server.FirstPages -ne 1 -or $server.Nodes -ne 2 -or $server.Failure){throw 'LOOPBACK_OBSERVED_COUNTS'}
 $checks++
 foreach($template in @('6908','6389','6399','6906','8656')){
  foreach($suffix in @('info','data?page=1','data?page=2','data?page=3')){
   $response=$client.GetAsync(('http://127.0.0.1:'+$server.Port+'/api/analytics/reports/'+$template+'/'+$suffix)).GetAwaiter().GetResult()
   try{if($response.StatusCode -ne 200){throw 'LOOPBACK_DATAEXPORT_RESPONSE'}}finally{$response.Dispose()}
  }
 }
 if($server.Requests -ne 22 -or $server.FirstPages -ne 6 -or $server.Failure){throw 'SIX_VERTICAL_LOOPBACK_COUNTS'}
 $checks++
} finally {$client.Dispose();$server.Dispose()}
$result=@{passed=$true;checks=$checks;syntheticHttp=22;sqlExecuted=$false;windowsIdentityExecuted=$false;path=$evidence}
[IO.File]::WriteAllText((Join-Path $evidence 'result.json'),($result|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json

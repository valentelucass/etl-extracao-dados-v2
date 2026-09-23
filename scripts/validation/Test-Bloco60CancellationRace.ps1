#Requires -Version 7.5
param([switch]$Original,[switch]$Unexpected)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Add-Type -Path (Join-Path $PSScriptRoot $(if($Original){'Bloco60Process.cs'}else{'Bloco60RemainingProcess.cs'}))
$server=[Bloco60Loopback]::new((Join-Path $root 'src/test/resources/runtime-laboratory-bloco55'),0)
$socket=[Net.Sockets.TcpClient]::new();$client=[Net.Http.HttpClient]::new()
try{
 $server.AllowOwnClientDisconnect=-not $Unexpected;$server.DataDelayMilliseconds=1000;$server.KeyGroup='B60_CANCEL_RACE'
 $body='{"operationName":"V2UsersSnapshot","query":"query V2UsersSnapshot($params: IndividualInput!, $after: String, $first: Int!) { individual(params: $params, after: $after, first: $first) { edges { node { id name } } pageInfo { hasNextPage endCursor } } }","variables":{"params":{"enabled":true},"after":null,"first":20}}'
 $bytes=[Text.Encoding]::UTF8.GetBytes($body)
 $header=[Text.Encoding]::ASCII.GetBytes("POST /graphql HTTP/1.1`r`nHost: localhost`r`nAuthorization: Bearer "+$server.Token+"`r`nContent-Type: application/json`r`nContent-Length: "+$bytes.Length+"`r`nConnection: close`r`n`r`n")
 $socket.Connect('127.0.0.1',$server.Port);$stream=$socket.GetStream();$stream.Write($header);$stream.Write($bytes);$stream.Flush()
 $limit=[DateTime]::UtcNow.AddSeconds(3)
 while($server.DataRequests -ne 1 -and [DateTime]::UtcNow -lt $limit){Start-Sleep -Milliseconds 10}
 if($server.DataRequests -ne 1){throw 'RACE_REQUEST_NOT_RECEIVED'}
 # Mimic the next case changing shared configuration before the delayed write fails.
 $server.AllowOwnClientDisconnect=$false;$server.DataDelayMilliseconds=0;$server.KeyGroup='B60_NEXT_CASE'
 $socket.Client.LingerState=[Net.Sockets.LingerOption]::new($true,0);$socket.Dispose()
 Start-Sleep -Milliseconds 1400
 if($Original -or $Unexpected){if(-not $server.Failure -or $server.ExpectedDisconnects -ne 0){throw 'UNEXPECTED_DISCONNECT_NOT_REFUSED'}}
 else{
  if($server.Failure -or $server.ExpectedDisconnects -ne 1){throw 'EXPECTED_DISCONNECT_LEAKED_TO_NEXT_CASE'}
  $client.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$server.Token)
  $content=[Net.Http.StringContent]::new($body,[Text.Encoding]::UTF8,'application/json')
  $response=$client.PostAsync(('http://127.0.0.1:'+$server.Port+'/graphql'),$content).GetAwaiter().GetResult()
  try{$json=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json;if($response.StatusCode -ne 200 -or $json.data.individual.edges[0].node.id -cne 'B60_NEXT_CASE_1'){throw 'NEXT_CASE_CONTAMINATED'}}finally{$response.Dispose();$content.Dispose()}
 }
 @{passed=$true;original=[bool]$Original;unexpected=[bool]$Unexpected;originalFailureReproduced=[bool]$Original;expectedDisconnects=$server.ExpectedDisconnects;sourceFailure=[bool]$server.Failure;requests=$server.Requests;sqlExecuted=$false}|ConvertTo-Json
}finally{$socket.Dispose();$client.Dispose();$server.Dispose()}

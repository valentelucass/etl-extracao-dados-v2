#Requires -Version 7.5
param([string]$RoundPath,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$testOnly=$SelfTest
. (Join-Path $PSScriptRoot 'Invoke-Bloco56IdentityMetadataProbe.ps1') -FunctionsOnly

# Transport helpers below retain raw UTF8 only in memory; generated from the reviewed B62 probe.
function ValidateJsonElement([System.Text.Json.JsonElement]$Element, [ref]$Nodes) {
    $Nodes.Value++
    if ($Nodes.Value -gt 4096) { throw 'B62_JSON_NODE_LIMIT' }
    if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($property in $Element.EnumerateObject()) {
            if(-not $seen.Add($property.Name)){throw 'B62_JSON_DUPLICATE_PROPERTY'}
            ValidateJsonElement $property.Value $Nodes
        }
    } elseif ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
        foreach($item in $Element.EnumerateArray()){ValidateJsonElement $item $Nodes}
    }
}

function StrictJson([string]$Text) {
    $document=$null
    try {
        $options=[System.Text.Json.JsonDocumentOptions]::new(); $options.MaxDepth=16
        $document=[System.Text.Json.JsonDocument]::Parse($Text,$options)
        $nodes=0; ValidateJsonElement $document.RootElement ([ref]$nodes)
        return ,($Text | ConvertFrom-Json -AsHashtable -Depth 16 -DateKind String -NoEnumerate)
    } finally {if($null -ne $document){$document.Dispose()}}
}

function Fetch([Uri]$Uri, [string]$Token, [string]$Body, [int]$MaximumBytes) {
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName=(Get-Command curl.exe -CommandType Application).Source
    $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    $method=if($Body){'POST'}else{'GET'}
    foreach($arg in @('--disable','--config','-','--globoff','--silent','--request',$method,'--proto','=https','--max-redirs','0','--connect-timeout','10','--max-time','30','--max-filesize',[string]$MaximumBytes,'--output','-','--write-out',"`nB62_HTTP_%{http_code}")){$start.ArgumentList.Add($arg)}
    $config='url = '+(Quote-CurlConfig $Uri.AbsoluteUri)+"`nheader = "+(Quote-CurlConfig ('Authorization: Bearer '+$Token))+"`nheader = "+(Quote-CurlConfig 'Accept: application/json')+"`n"
    if($Body){$config+='header = '+(Quote-CurlConfig 'Content-Type: application/json')+"`ndata = "+(Quote-CurlConfig $Body)+"`n"}
    $process=$null; $memory=[IO.MemoryStream]::new(); $watch=[Diagnostics.Stopwatch]::StartNew()
    try {
        $process=[Diagnostics.Process]::Start($start)
        $stderr=$process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($config); $process.StandardInput.Close()
        $buffer=[byte[]]::new(8192)
        while($true){
            $read=$process.StandardOutput.BaseStream.ReadAsync($buffer,0,$buffer.Length)
            while(-not $read.Wait(100)){if($watch.Elapsed.TotalSeconds -gt 35){throw 'B62_WALL_TIMEOUT'}}
            $count=$read.GetAwaiter().GetResult(); if($count -eq 0){break}
            if($memory.Length+$count -gt $MaximumBytes+32){throw 'B62_RESPONSE_BOUND'}
            $memory.Write($buffer,0,$count)
        }
        if(-not $process.WaitForExit(1000)){throw 'B62_PROCESS_TIMEOUT'}
        $raw=$utf8.GetString($memory.ToArray()); $match=[regex]::Match($raw,'\nB62_HTTP_(\d{3})$')
        $status=if($match.Success){[int]$match.Groups[1].Value}else{0}
        $result=[ordered]@{httpStatus=$status;curlExit=$process.ExitCode;responseBytes=$memory.Length;elapsedMilliseconds=$watch.ElapsedMilliseconds;failure=$null;json=$null;body=$null}
        if($process.ExitCode -ne 0){$result.failure='TRANSPORT_FAILURE'}
        elseif($status -lt 200 -or $status -gt 299){$result.failure='HTTP_NON_2XX'}
        else {
            try {
                $bodyText=$raw.Substring(0,$match.Index)
                $result.responseBytes=$utf8.GetByteCount($bodyText)
                if($result.responseBytes -gt $MaximumBytes){throw 'B62_RESPONSE_BOUND'}
                $result.json=StrictJson $bodyText; $result.body=$bodyText
            } catch {$result.failure='INVALID_OR_OVERSIZED_JSON'}
        }
        return $result
    } finally {
        if($null -ne $process){if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
        $memory.Dispose();$config=$null;$raw=$null;$Token=$null;$bodyText=$null
    }
}

function DataProfile($Json){
    if($Json -isnot [Collections.IList] -or $Json.Count -gt 1000){throw 'B62_DATA_SHAPE'}
    $ids=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($row in $Json){
        if($row -isnot [Collections.IDictionary] -or -not $row.Contains('id') -or ($row.id -isnot [int] -and $row.id -isnot [long] -and $row.id -isnot [bigint])){throw 'B62_DATA_ID'}
        [void]$ids.Add([string]$row.id)
        if($ids.Count -gt 2){throw 'B62_DATA_PER'}
    }
    return [ordered]@{physicalRows=$Json.Count;distinctRoots=$ids.Count;terminal=($Json.Count -eq 0)}
}
function GraphProfile($Json){
    if($Json -isnot [Collections.IDictionary] -or $Json.Contains('errors') -or $Json.Contains('error') -or $Json.data -isnot [Collections.IDictionary]){throw 'B62_GRAPH_ENVELOPE'}
    $connection=$Json.data.pick
    if($connection -isnot [Collections.IDictionary] -or $connection.edges -isnot [Collections.IList] -or $connection.edges.Count -gt 100){throw 'B62_GRAPH_EDGES'}
    $ids=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($edge in $connection.edges){
        if($edge -isnot [Collections.IDictionary] -or $edge.node -isnot [Collections.IDictionary]){throw 'B62_GRAPH_NODE'}
        $node=$edge.node
        foreach($field in @('id','sequenceCode','status','requestDate','serviceDate','finishDate','cancellationReason','statusUpdatedAt')){if(-not $node.Contains($field)){throw 'B62_GRAPH_SELECTION'}}
        if(($node.id -isnot [string] -and $node.id -isnot [int] -and $node.id -isnot [long]) -or [string]::IsNullOrWhiteSpace([string]$node.id) -or -not $ids.Add([string]$node.id)){throw 'B62_GRAPH_ID'}
    }
    $info=$connection.pageInfo
    if($info -isnot [Collections.IDictionary] -or $info.hasNextPage -isnot [bool] -or -not $info.Contains('endCursor') -or ($null -ne $info.endCursor -and $info.endCursor -isnot [string])){throw 'B62_GRAPH_PAGEINFO'}
    if($info.hasNextPage -and ($connection.edges.Count -eq 0 -or [string]::IsNullOrWhiteSpace($info.endCursor))){throw 'B62_GRAPH_CURSOR'}
    return [ordered]@{rows=$connection.edges.Count;hasNextPage=$info.hasNextPage}
}
function WriteInt($Stream,[int]$Value){
    $bytes=[BitConverter]::GetBytes($Value)
    if([BitConverter]::IsLittleEndian){[Array]::Reverse($bytes)}
    $Stream.Write($bytes,0,4)
}
function WriteFrame($Stream,[string]$Value){
    $bytes=$utf8.GetBytes($Value)
    if($bytes.Length -lt 1 -or $bytes.Length -gt 65536){throw 'B62_FRAME_BOUND'}
    WriteInt $Stream $bytes.Length
    $Stream.Write($bytes,0,$bytes.Length)
}
function Replay([string]$Build,[string]$Metadata,$Pages,$GraphPages){
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe'
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    foreach($arg in @('-Xmx128m','-cp',((Join-Path $Build 'target/test-classes')+';'+(Join-Path $Build 'target/classes')+';'+(Join-Path $Build 'target/lib/*')),'br.com.esl.etl.v2.plataforma.fonte.dataexport.ColetasSourceReplay')){$start.ArgumentList.Add($arg)}
    $process=$null
    try{
        $process=[Diagnostics.Process]::Start($start)
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $stream=$process.StandardInput.BaseStream
        WriteInt $stream 6201;WriteFrame $stream $Metadata;WriteInt $stream $Pages.Count
        foreach($page in $Pages){WriteFrame $stream $page}
        WriteInt $stream $GraphPages.Count
        foreach($page in $GraphPages){WriteFrame $stream $page}
        $stream.Flush();$process.StandardInput.Close()
        if(-not $process.WaitForExit(30000)){throw 'B62_REPLAY_TIMEOUT'}
        $output=$stdout.GetAwaiter().GetResult()
        if($output.Length -gt 16384){throw 'B62_REPLAY_OUTPUT_BOUND'}
        $lines=@($output.Trim() -split '\r?\n')
        $result=StrictJson $lines[-1]
        if($process.ExitCode -ne 0){return [ordered]@{accepted=$false;reason='REPLAY_INPUT_OR_CONTRACT_REJECTED'}}
        # An explicit vocabulary protects persisted evidence even if the Java harness regresses.
        $expected=@('accepted','layer','contractVersion','contractFingerprint','dataPages','physicalRows','distinctRoots','repeatedRootsAcrossPages','stagedRows','quarantinedRows','preservationDifferences','unknownStatusRows','unavailableFreshnessRows','dataTerminalObserved','captureLimitReached','graphPages','graphRows','graphTerminalObserved','comparison','snapshotProven','representativeParityAccepted','operationalBindingValidated','sqlExecuted')
        if((($result.Keys|Sort-Object)-join ',') -cne (($expected|Sort-Object)-join ',')){throw 'B62_REPLAY_OUTPUT_KEYS'}
        if($result.layer -cne 'SOURCE_BODY_REPLAY_IN_MEMORY_STAGING' -or $result.contractVersion -cne '2026-09-10.b62-coletas-scalar-capture.1' -or $result.contractFingerprint -cnotmatch '^[a-f0-9]{64}$'){throw 'B62_REPLAY_OUTPUT_RELEASE'}
        foreach($key in $expected|Where-Object {$_ -notin @('layer','contractVersion','contractFingerprint','comparison')}){if($result[$key] -isnot [bool] -and $result[$key] -isnot [int] -and $result[$key] -isnot [long]){throw 'B62_REPLAY_OUTPUT_TYPE'}}
        if((($result.comparison.Keys|Sort-Object)-join ',') -cne 'canonicalIdDifferences,fieldDifferences,globalSetEqualityProven,matchedPhysicalRows,rowsOutsideGraphSample'){throw 'B62_REPLAY_COMPARISON_KEYS'}
        if((($result.comparison.fieldDifferences.Keys|Sort-Object)-join ',') -cne 'cancellation_reason,finish_date,request_date,service_date,status,status_updated_at'){throw 'B62_REPLAY_FIELD_KEYS'}
        foreach($key in @('canonicalIdDifferences','matchedPhysicalRows','rowsOutsideGraphSample')){if($result.comparison[$key] -isnot [int] -and $result.comparison[$key] -isnot [long]){throw 'B62_REPLAY_COMPARISON_TYPE'}}
        foreach($value in $result.comparison.fieldDifferences.Values){if($value -isnot [int] -and $value -isnot [long]){throw 'B62_REPLAY_FIELD_TYPE'}}
        if($result.snapshotProven -or $result.representativeParityAccepted -or $result.operationalBindingValidated -or $result.sqlExecuted -or $result.comparison.globalSetEqualityProven){throw 'B62_REPLAY_FALSE_ACCEPTANCE'}
        return $result
    }finally{if($null -ne $process){if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}}
}

if($testOnly){
    $guards=0
    $profile=DataProfile (StrictJson '[{"id":1},{"id":1},{"id":2}]')
    if($profile.physicalRows -ne 3 -or $profile.distinctRoots -ne 2 -or $profile.terminal){throw 'B62_SELFTEST_EXPANSION'}
    foreach($case in @(
        @('B62_DATA_ID',{DataProfile (StrictJson '[{"id":"1"}]')}),
        @('B62_DATA_PER',{DataProfile (StrictJson '[{"id":1},{"id":2},{"id":3}]')}),
        @('B62_DATA_SHAPE',{DataProfile (StrictJson '{"data":[]}')}),
        @('B62_JSON_DUPLICATE_PROPERTY',{StrictJson '{"id":1,"id":2}'}),
        @('B62_GRAPH_ENVELOPE',{GraphProfile (StrictJson '{"errors":["PRIVATE_SENTINEL"]}')}),
        @('B62_GRAPH_CURSOR',{GraphProfile (StrictJson '{"data":{"pick":{"edges":[],"pageInfo":{"hasNextPage":true,"endCursor":null}}}}')})
    )){
        $reason='ACCEPTED';try{$null=& $case[1]}catch{$reason=$_.Exception.Message}
        if($reason -cne $case[0]){throw ('B62_SELFTEST_'+$case[0])};$guards++
    }
    $memory=[IO.MemoryStream]::new()
    try{WriteInt $memory 6201;if([Convert]::ToHexString($memory.ToArray()) -cne '00001839'){throw 'B62_FRAME_ENDIAN'}}finally{$memory.Dispose()}
    @{passed=$true;guards=$guards;networkExecuted=$false}|ConvertTo-Json
    exit 0
}

if($RoundPath -cnotmatch '^target/b62-real-replay-[0-9]{8}-[0-9]{6}$'){throw 'B62_ROUND_PATH'}
$private=Join-Path $root $RoundPath
$node=Get-Item -LiteralPath $private
while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B62_REPARSE'};$node=$node.Parent}
$requestPath=Join-Path $private 'request.json'
$plan=Get-Content -LiteralPath $requestPath -Raw|ConvertFrom-Json
if($plan.block -ne 62 -or $plan.maximumCalls -ne 5 -or $plan.maximumDurationSeconds -ne 240 -or $plan.timeoutSeconds -ne 30 -or $plan.minimumIntervalSeconds -ne 10 -or $plan.maximumResponseBytes -ne 65536 -or $plan.maximumDataPages -ne 2 -or $plan.maximumGraphQlPages -ne 2 -or $plan.coletasPer -ne 2 -or $plan.graphQlFirst -ne 100 -or $plan.maximumRows -ne 1000 -or $plan.windowStart -cne '2026-09-09' -or $plan.windowEnd -cne '2026-09-09' -or $plan.orderBy -cne 'sequence_code asc' -or $plan.retry -or $plan.redirect -or $plan.rawPayloadPersistence -or $plan.sql -or $plan.production -or ($plan.operations -join ',') -cne 'COLETAS_INFO,COLETAS_DATA_PAGE_1,COLETAS_DATA_PAGE_2_IF_NONEMPTY,COLETAS_GRAPHQL_PAGE_1,COLETAS_GRAPHQL_PAGE_2_IF_HAS_NEXT'){throw 'B62_REQUEST_SCOPE'}
$ledger=Join-Path $private 'ledger.jsonl'
if(Test-Path -LiteralPath $ledger){throw 'B62_SINGLE_USE_NO_REPEAT'}
$build=Join-Path $private 'build'
$javaSource='src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/ColetasSourceReplay.java'
if((Get-FileHash (Join-Path $root $javaSource)).Hash -cne (Get-FileHash (Join-Path $build $javaSource)).Hash -or -not (Test-Path (Join-Path $build 'target/lib/jackson-databind-2.17.2.jar'))){throw 'B62_BUILD_BINDING'}
$requestHash=(Get-FileHash -LiteralPath $requestPath).Hash.ToLowerInvariant()
$scriptHash=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant()
$javaHash=(Get-FileHash -LiteralPath (Join-Path $root $javaSource)).Hash.ToLowerInvariant()
# Validate the stdin Java boundary using authored data before opening the new network budget.
$smoke=Replay $build ([IO.File]::ReadAllText((Join-Path $build 'src/test/resources/contracts/bloco62/6908-info.sanitized.json'),$utf8)) @('[]') @()
if(-not $smoke.accepted -or -not $smoke.dataTerminalObserved){throw 'B62_REPLAY_PREFLIGHT'}
[IO.File]::WriteAllText((Join-Path $private 'replay-preflight.json'),($smoke|ConvertTo-Json -Depth 12),$utf8)
$envLines=[IO.File]::ReadAllLines((Join-Path $root '../etl-extracao-dados/.env'))
$base=Get-SafeBase (Read-EnvValue $envLines 'API_BASE_URL')
$endpoint=Read-EnvValue $envLines 'API_GRAPHQL_ENDPOINT'
$graph=if($endpoint.StartsWith('/')){[Uri]::new($base,$endpoint)}else{Get-SafeBase $endpoint}
if($graph.Scheme -cne 'https' -or $graph.Host -cne $base.Host -or $graph.Port -ne $base.Port -or $graph.AbsolutePath -cne '/graphql' -or $graph.Query -or $graph.Fragment -or $graph.UserInfo){throw 'B62_GRAPH_ENDPOINT'}
$graphToken=Read-EnvValue $envLines 'API_GRAPHQL_TOKEN';$exportToken=Read-EnvValue $envLines 'API_DATAEXPORT_TOKEN';$envLines=$null
$query='query B62ColetasReference($params: PickInput!, $after: String, $first: Int!) { pick(params: $params, after: $after, first: $first) { edges { node { id sequenceCode status requestDate serviceDate finishDate cancellationReason statusUpdatedAt } } pageInfo { hasNextPage endCursor } } }'
$clock=[Diagnostics.Stopwatch]::StartNew();$results=[Collections.Generic.List[object]]::new()
$pages=[Collections.Generic.List[string]]::new();$graphPages=[Collections.Generic.List[string]]::new()
$metadata=$null;$cursor=$null;$dataTerminal=$false;$graphTerminal=$false;$totalRows=0;$stopReason=$null
try{
    foreach($operation in $plan.operations){
        if($operation -ceq 'COLETAS_DATA_PAGE_2_IF_NONEMPTY' -and $dataTerminal){continue}
        if($operation -ceq 'COLETAS_GRAPHQL_PAGE_2_IF_HAS_NEXT' -and $graphTerminal){continue}
        if($results.Count -gt 0){Start-Sleep -Seconds 10}
        if($clock.Elapsed.TotalSeconds -gt 200){$stopReason='CAMPAIGN_TIME_STOP';break}
        $uri=$graph;$token=$graphToken;$body=$null
        if($operation.StartsWith('COLETAS_GRAPHQL')){$body=@{query=$query;variables=@{params=@{requestDate='2026-09-09'};after=$cursor;first=100}}|ConvertTo-Json -Depth 8 -Compress}
        else{
            $builder=[UriBuilder]::new($base);$suffix=if($operation -ceq 'COLETAS_INFO'){'info'}else{'data'}
            $builder.Path=$builder.Path.TrimEnd('/')+'/api/analytics/reports/6908/'+$suffix
            if($suffix -ceq 'data'){
                $params=[ordered]@{'search[picks][request_date]'='2026-09-09 - 2026-09-09';page=[string]($pages.Count+1);per='2';order_by='sequence_code asc'}
                $builder.Query=(@(foreach($key in $params.Keys){[Uri]::EscapeDataString($key)+'='+[Uri]::EscapeDataString($params[$key])})-join '&')
            }
            $uri=$builder.Uri;$token=$exportToken
        }
        [IO.File]::AppendAllText($ledger,(@{state='RESERVED_OUTCOME_UNKNOWN';operation=$operation;utc=[DateTimeOffset]::UtcNow.ToString('o');requestSha256=$requestHash;scriptSha256=$scriptHash;javaSha256=$javaHash}|ConvertTo-Json -Compress)+"`n",$utf8)
        $result=[ordered]@{operation=$operation;httpStatus=0;curlExit=$null;responseBytes=0;elapsedMilliseconds=0;failure=$null;profile=$null}
        try{
            $response=Fetch $uri $token $body 65536
            foreach($key in @('httpStatus','curlExit','responseBytes','elapsedMilliseconds','failure')){$result[$key]=$response[$key]}
            if($null -eq $result.failure){
                if($operation -ceq 'COLETAS_INFO'){
                    $result.profile=Get-MetadataProfile $response.json;$metadata=$response.body
                    # Empty authored traversal validates metadata only; it is not a source data page.
                    $check=Replay $build $metadata @('[]') @()
                    if(-not $check.accepted){throw 'B62_METADATA_CONTRACT'}
                    $result.profile.metadataGatePassed=$true
                }
                elseif($operation.StartsWith('COLETAS_DATA')){
                    $result.profile=DataProfile $response.json;$totalRows+=$result.profile.physicalRows
                    if($totalRows -gt 1000){throw 'B62_TOTAL_ROWS'}
                    $dataTerminal=$result.profile.terminal;$pages.Add($response.body)
                }else{
                    $result.profile=GraphProfile $response.json
                    $next=$response.json.data.pick.pageInfo.endCursor
                    if($result.profile.hasNextPage -and $null -ne $cursor -and $next -ceq $cursor){throw 'B62_REPEATED_CURSOR'}
                    $graphTerminal=-not $result.profile.hasNextPage;$cursor=$next;$graphPages.Add($response.body)
                }
            }
        }catch{$reason=$_.Exception.Message;$result.failure=if($reason -cmatch '^B62_[A-Z0-9_]{1,70}$'){$reason}else{'LOCAL_TRANSPORT_OR_SCHEMA_FAILURE'}}
        $response=$null;$body=$null;$token=$null;$results.Add($result)
        [IO.File]::WriteAllText((Join-Path $private ($operation+'.json')),($result|ConvertTo-Json -Depth 18),$utf8)
        [IO.File]::AppendAllText($ledger,(@{state='OBSERVED';operation=$operation;utc=[DateTimeOffset]::UtcNow.ToString('o');httpStatus=$result.httpStatus;curlExit=$result.curlExit;failure=$result.failure}|ConvertTo-Json -Compress)+"`n",$utf8)
        @{operation=$operation;httpStatus=$result.httpStatus;failure=$result.failure;bytes=$result.responseBytes}|ConvertTo-Json -Compress
        if($null -ne $result.failure){$stopReason=$result.failure;break}
    }
    $replay=$null
    if($null -ne $metadata -and $pages.Count -gt 0){
        try{$replay=Replay $build $metadata $pages $graphPages}catch{$replay=[ordered]@{accepted=$false;reason='LOCAL_REPLAY_BOUNDARY_FAILURE'}}
    }
    $summary=[ordered]@{block=62;phase='BOUNDED_REAL_REPLAY_AND_GRAPHQL_COMPARISON';calls=$results.Count;maximumCalls=5;stopReason=$stopReason;elapsedMilliseconds=$clock.ElapsedMilliseconds;rawPayloadPersisted=$false;rotationProven=$false;v2012aAccepted=$false;sqlExecuted=$false;results=@($results.ToArray());replay=$replay}
    [IO.File]::WriteAllText((Join-Path $private 'source-summary.json'),($summary|ConvertTo-Json -Depth 24),$utf8)
    $summary|ConvertTo-Json -Depth 24
}finally{$metadata=$null;$pages.Clear();$graphPages.Clear();$graphToken=$null;$exportToken=$null;$cursor=$null;$next=$null;$uri=$null;$base=$null;$graph=$null}
if($null -ne $stopReason -or $null -eq $replay -or -not $replay.accepted){exit 2}

#Requires -Version 7.5
param([string]$RoundPath, [switch]$SelfTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$runSelfTest = $SelfTest
. (Join-Path $PSScriptRoot 'Invoke-Bloco56IdentityMetadataProbe.ps1') -FunctionsOnly

function WireType($Value) {
    if ($null -eq $Value) { return 'NULL' }
    if ($Value -is [string]) { return 'STRING' }
    if ($Value -is [bool]) { return 'BOOLEAN' }
    if ($Value -is [Collections.IDictionary]) { return 'OBJECT' }
    if ($Value -is [Collections.IList]) { return 'ARRAY' }
    if ($Value -is [int] -or $Value -is [long] -or $Value -is [bigint]) { return 'INTEGER' }
    if ($Value -is [double] -or $Value -is [decimal]) { return 'NUMBER' }
    throw 'B62_WIRE_TYPE'
}

function FieldProfile($Rows, [string]$Field) {
    $types = [ordered]@{}
    $absent = 0
    foreach ($row in $Rows) {
        if (-not $row.Contains($Field)) { $absent++; continue }
        $type = WireType $row[$Field]
        if (-not $types.Contains($type)) { $types[$type] = 0 }
        $types[$type]++
    }
    return [ordered]@{field=$Field;absent=$absent;types=$types}
}

function IdentityProfile($Rows, [int]$MaximumRoots) {
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($row in $Rows) {
        if ($row -isnot [Collections.IDictionary] -or -not $row.Contains('id')) { throw 'B62_ID_UNVERIFIABLE' }
        $type = WireType $row.id
        if ($type -cnotin @('INTEGER','STRING') -or ($type -ceq 'STRING' -and [string]::IsNullOrWhiteSpace($row.id))) { throw 'B62_ID_UNVERIFIABLE' }
        [void]$seen.Add($type + ':' + [string]$row.id)
        if ($seen.Count -gt $MaximumRoots) { throw 'B62_ROOT_LIMIT' }
    }
    return [ordered]@{physicalRows=$Rows.Count;distinctTypedRoots=$seen.Count;repeatedPhysicalRows=$Rows.Count-$seen.Count;globalIdentityProven=$false}
}

function UsersProfile($Json) {
    if ($Json -isnot [Collections.IDictionary] -or $Json.Contains('errors') -or $Json.Contains('error') -or $Json.data -isnot [Collections.IDictionary]) { throw 'B62_USERS_ENVELOPE' }
    $connection = $Json.data.individual
    if ($connection -isnot [Collections.IDictionary] -or $connection.edges -isnot [Collections.IList] -or $connection.edges.Count -gt 5) { throw 'B62_USERS_EDGES' }
    $nodes = @(foreach ($edge in $connection.edges) {
        if ($edge -isnot [Collections.IDictionary] -or $edge.node -isnot [Collections.IDictionary]) { throw 'B62_USERS_NODE' }
        $edge.node
    })
    $identity = IdentityProfile $nodes 5
    if ($identity.repeatedPhysicalRows -gt 0) { throw 'B62_USERS_DUPLICATE_ID' }
    $page = $connection.pageInfo
    if ($page -isnot [Collections.IDictionary] -or $page.hasNextPage -isnot [bool] -or -not $page.Contains('endCursor')) { throw 'B62_USERS_PAGEINFO' }
    if ($null -ne $page.endCursor -and $page.endCursor -isnot [string]) { throw 'B62_USERS_CURSOR_TYPE' }
    if ($page.hasNextPage -and ([string]::IsNullOrWhiteSpace($page.endCursor) -or $nodes.Count -eq 0)) { throw 'B62_USERS_CURSOR_REQUIRED' }
    return [ordered]@{identity=$identity;fields=@((FieldProfile $nodes 'id'),(FieldProfile $nodes 'name'));hasNextPage=$page.hasNextPage;endCursorType=(WireType $page.endCursor);snapshotProven=$false;sourceTemporalFieldSelected=$false;mapperExecuted=$false}
}

function ColetasProfile($Json) {
    if ($Json -is [Collections.IList]) { $rows=$Json; $form='ROOT_ARRAY' }
    elseif ($Json -is [Collections.IDictionary] -and -not $Json.Contains('error') -and -not $Json.Contains('errors') -and $Json.data -is [Collections.IList]) { $rows=$Json.data; $form='ENVELOPE_DATA_ARRAY' }
    else { throw 'B62_COLETAS_ENVELOPE' }
    if ($rows.Count -gt 1000) { throw 'B62_ROW_LIMIT' }
    $identity = IdentityProfile $rows 2
    foreach ($row in $rows) { if ((WireType $row.id) -cne 'INTEGER') { throw 'B62_COLETAS_ID_TYPE' } }
    $fields = @('id','sequence_code','status','request_date','service_date','finish_date','status_updated_at','updated_at','pck_mik_mft_sequence_code')
    return [ordered]@{envelope=$form;identity=$identity;fields=@(foreach($field in $fields){FieldProfile $rows $field});representativeWindowProven=$false;temporalBoundariesProven=$false;mapperExecuted=$false;relationshipsProven=$false}
}

function GraphType($Type, [int]$Depth=0) {
    if ($null -eq $Type) { return $null }
    if ($Depth -gt 4 -or $Type -isnot [Collections.IDictionary] -or $Type.kind -cnotin @('NON_NULL','LIST','SCALAR','OBJECT','INPUT_OBJECT','ENUM','INTERFACE','UNION')) { throw 'B62_SCHEMA_TYPE' }
    if ($null -ne $Type.name -and ($Type.name -isnot [string] -or $Type.name -cnotmatch '^[A-Za-z_][A-Za-z0-9_]{0,127}$')) { throw 'B62_SCHEMA_NAME' }
    $value=[ordered]@{kind=$Type.kind;name=$Type.name}
    if ($Type.Contains('ofType')) { $value.ofType=GraphType $Type.ofType ($Depth+1) }
    return $value
}

function UsersSchema($Json) {
    if ($Json -isnot [Collections.IDictionary] -or $Json.Contains('errors') -or $Json.Contains('error') -or $Json.data -isnot [Collections.IDictionary]) { throw 'B62_SCHEMA_ENVELOPE' }
    $selected=@($Json.data.__schema.queryType.fields | Where-Object name -CEQ 'individual')
    if ($selected.Count -ne 1 -or $null -eq $Json.data.__type) { throw 'B62_USERS_SCHEMA_MISSING' }
    $arguments=@(foreach($arg in $selected[0].args){if($arg.name -cin @('params','first','after')){[ordered]@{name=$arg.name;type=(GraphType $arg.type)}}})
    $inputs=@(foreach($field in $Json.data.__type.inputFields){if($field.name -cin @('enabled','updatedAt')){[ordered]@{name=$field.name;type=(GraphType $field.type)}}})
    return [ordered]@{query='individual';returnType=(GraphType $selected[0].type);arguments=$arguments;selectedInputFields=$inputs;unselectedFieldsOmitted=$true;temporalSemanticsProven=$false}
}

function ValidateJsonElement([System.Text.Json.JsonElement]$Element, [ref]$Nodes) {
    $Nodes.Value++
    if ($Nodes.Value -gt 32768) { throw 'B62_JSON_NODE_LIMIT' }
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
        $options=[System.Text.Json.JsonDocumentOptions]::new(); $options.MaxDepth=32
        $document=[System.Text.Json.JsonDocument]::Parse($Text,$options)
        $nodes=0; ValidateJsonElement $document.RootElement ([ref]$nodes)
        return ,($Text | ConvertFrom-Json -AsHashtable -Depth 32 -DateKind String -NoEnumerate)
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
        $result=[ordered]@{httpStatus=$status;curlExit=$process.ExitCode;responseBytes=$memory.Length;elapsedMilliseconds=$watch.ElapsedMilliseconds;failure=$null;json=$null}
        if($process.ExitCode -ne 0){$result.failure='TRANSPORT_FAILURE'}
        elseif($status -lt 200 -or $status -gt 299){$result.failure='HTTP_NON_2XX'}
        else {
            try {
                $bodyText=$raw.Substring(0,$match.Index)
                $result.responseBytes=$utf8.GetByteCount($bodyText)
                if($result.responseBytes -gt $MaximumBytes){throw 'B62_RESPONSE_BOUND'}
                $result.json=StrictJson $bodyText
            } catch {$result.failure='INVALID_OR_OVERSIZED_JSON'}
        }
        return $result
    } finally {
        if($null -ne $process){if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
        $memory.Dispose();$config=$null;$raw=$null;$Token=$null;$bodyText=$null
    }
}

if($runSelfTest){
    $guards=0
    $value=StrictJson '{"data":{"individual":{"edges":[{"node":{"id":"01","name":"PRIVATE_SENTINEL"}},{"node":{"id":1}}],"pageInfo":{"hasNextPage":true,"endCursor":"PRIVATE_CURSOR"}}}}'
    $result=UsersProfile $value
    if($result.identity.distinctTypedRoots -ne 2 -or $result.fields[1].absent -ne 1 -or ($result|ConvertTo-Json -Depth 12) -match 'PRIVATE_SENTINEL|PRIVATE_CURSOR|STRING:01'){throw 'B62_TEST_USERS_SANITIZATION'}
    $value=StrictJson '{"data":[{"id":1},{"id":1},{"id":2}]}'
    $result=ColetasProfile $value
    if($result.identity.physicalRows -ne 3 -or $result.identity.distinctTypedRoots -ne 2){throw 'B62_TEST_EXPANSION'}
    foreach($case in @(
        @('B62_ROOT_LIMIT',{ColetasProfile (StrictJson '[{"id":1},{"id":2},{"id":3}]')}),
        @('B62_ID_UNVERIFIABLE',{ColetasProfile (StrictJson '[{"id":null}]')}),
        @('B62_COLETAS_ID_TYPE',{ColetasProfile (StrictJson '[{"id":"1"}]')}),
        @('B62_JSON_DUPLICATE_PROPERTY',{StrictJson '{"id":1,"id":2}'}),
        @('B62_USERS_ENVELOPE',{UsersProfile (StrictJson '{"errors":["PRIVATE_SENTINEL"]}')}),
        @('B62_USERS_CURSOR_REQUIRED',{UsersProfile (StrictJson '{"data":{"individual":{"edges":[],"pageInfo":{"hasNextPage":true,"endCursor":null}}}}')}),
        @('B62_USERS_DUPLICATE_ID',{UsersProfile (StrictJson '{"data":{"individual":{"edges":[{"node":{"id":1}},{"node":{"id":1}}],"pageInfo":{"hasNextPage":false,"endCursor":null}}}}')}),
        @('B62_SCHEMA_NAME',{GraphType (@{kind='SCALAR';name='https://private.invalid'})})
    )){
        $reason='ACCEPTED';try{$null=& $case[1]}catch{$reason=$_.Exception.Message}
        if($reason -cne $case[0]){throw ('B62_TEST_EXPECTED_'+$case[0]+'_GOT_'+$reason)}
        $guards++
    }
    [ordered]@{passed=$true;guards=$guards;positiveCases=2;networkExecuted=$false}|ConvertTo-Json
    exit 0
}

if($RoundPath -cnotmatch '^target/b62-source-[0-9]{8}-[0-9]{6}$'){throw 'B62_ROUND_PATH'}
$private=Join-Path $root $RoundPath
$node=Get-Item -LiteralPath $private
while($node.FullName -cne $root){if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'B62_REPARSE'};$node=$node.Parent}
$requestPath=Join-Path $private 'request.json'
$plan=Get-Content -LiteralPath $requestPath -Raw|ConvertFrom-Json -Depth 10
if($plan.block -ne 62 -or $plan.maximumCalls -ne 4 -or $plan.maximumDurationSeconds -ne 180 -or $plan.timeoutSeconds -ne 30 -or $plan.usersFirst -ne 5 -or $plan.coletasPer -ne 2 -or $plan.page -ne 1 -or $plan.windowStart -cne '2026-09-09' -or $plan.windowEnd -cne '2026-09-09' -or $plan.metadataMaximumBytes -ne 1048576 -or $plan.dataMaximumBytes -ne 65536 -or ($plan.operations -join ',') -cne 'USERS_SCHEMA,USERS_SAMPLE,COLETAS_INFO,COLETAS_SAMPLE' -or $plan.retry -or $plan.redirect -or $plan.rawPayloadPersistence){throw 'B62_REQUEST_SCOPE'}
$ledger=Join-Path $private 'ledger.jsonl'
if(Test-Path -LiteralPath $ledger){throw 'B62_SINGLE_USE_NO_REPEAT'}
$envLines=[IO.File]::ReadAllLines((Join-Path $root '../etl-extracao-dados/.env'))
$base=Get-SafeBase (Read-EnvValue $envLines 'API_BASE_URL')
$endpoint=Read-EnvValue $envLines 'API_GRAPHQL_ENDPOINT'
$graph=if($endpoint.StartsWith('/')){[Uri]::new($base,$endpoint)}else{Get-SafeBase $endpoint}
if($graph.Scheme -cne 'https' -or $graph.Host -cne $base.Host -or $graph.Port -ne $base.Port -or $graph.AbsolutePath -cne '/graphql' -or $graph.Query -or $graph.Fragment -or $graph.UserInfo){throw 'B62_GRAPHQL_ENDPOINT'}
$graphToken=Read-EnvValue $envLines 'API_GRAPHQL_TOKEN'
$exportToken=Read-EnvValue $envLines 'API_DATAEXPORT_TOKEN'
$schemaQuery='query B62UsersSchema { __schema { queryType { fields { name type { kind name ofType { kind name ofType { kind name } } } args { name type { kind name ofType { kind name ofType { kind name } } } } } } } __type(name: "IndividualInput") { name inputFields { name type { kind name ofType { kind name ofType { kind name } } } } } }'
$usersQuery='query V2UsersSnapshot($params: IndividualInput!, $after: String, $first: Int!) { individual(params: $params, after: $after, first: $first) { edges { node { id name } } pageInfo { endCursor hasNextPage } } }'
$requestHash=(Get-FileHash -LiteralPath $requestPath).Hash.ToLowerInvariant()
$scriptHash=(Get-FileHash -LiteralPath $PSCommandPath).Hash.ToLowerInvariant()
$clock=[Diagnostics.Stopwatch]::StartNew();$results=[Collections.Generic.List[object]]::new()
foreach($operation in $plan.operations){
    if($clock.Elapsed.TotalSeconds -gt 140){throw 'B62_CAMPAIGN_TIME_STOP'}
    $uri=$graph;$token=$graphToken;$body=$null;$maximumBytes=1048576
    switch($operation){
        'USERS_SCHEMA' {$body=@{query=$schemaQuery}|ConvertTo-Json -Compress}
        'USERS_SAMPLE' {$body=@{query=$usersQuery;variables=@{params=@{enabled=$true};after=$null;first=5}}|ConvertTo-Json -Depth 5 -Compress;$maximumBytes=65536}
        'COLETAS_INFO' {$builder=[UriBuilder]::new($base);$builder.Path=$builder.Path.TrimEnd('/')+'/api/analytics/reports/6908/info';$uri=$builder.Uri;$token=$exportToken}
        'COLETAS_SAMPLE' {
            $builder=[UriBuilder]::new($base);$builder.Path=$builder.Path.TrimEnd('/')+'/api/analytics/reports/6908/data'
            $query=[ordered]@{'search[picks][request_date]'='2026-09-09 - 2026-09-09';page='1';per='2';order_by='id asc'}
            $builder.Query=(@(foreach($key in $query.Keys){[Uri]::EscapeDataString($key)+'='+[Uri]::EscapeDataString($query[$key])})-join '&')
            $uri=$builder.Uri;$token=$exportToken;$maximumBytes=65536
        }
    }
    [IO.File]::AppendAllText($ledger,(@{operation=$operation;state='RESERVED_OUTCOME_UNKNOWN';utc=[DateTimeOffset]::UtcNow.ToString('o');requestSha256=$requestHash;scriptSha256=$scriptHash}|ConvertTo-Json -Compress)+"`n",$utf8)
    $result=[ordered]@{operation=$operation;httpStatus=0;curlExit=$null;responseBytes=0;elapsedMilliseconds=0;failure=$null;profile=$null}
    try {
        $response=Fetch $uri $token $body $maximumBytes
        foreach($key in @('httpStatus','curlExit','responseBytes','elapsedMilliseconds','failure')){$result[$key]=$response[$key]}
        if($null -eq $result.failure){
            $result.profile=switch($operation){'USERS_SCHEMA'{UsersSchema $response.json};'USERS_SAMPLE'{UsersProfile $response.json};'COLETAS_INFO'{Get-MetadataProfile $response.json};'COLETAS_SAMPLE'{ColetasProfile $response.json}}
        }
    } catch {
        $reason=$_.Exception.Message
        $result.failure=if($reason -cmatch '^B62_[A-Z0-9_]{1,70}$'){$reason}else{'LOCAL_TRANSPORT_OR_SCHEMA_FAILURE'}
    }
    $response=$null;$body=$null;$token=$null
    $results.Add($result)
    [IO.File]::WriteAllText((Join-Path $private ($operation+'.json')),($result|ConvertTo-Json -Depth 20),$utf8)
    [IO.File]::AppendAllText($ledger,(@{operation=$operation;state='OBSERVED';httpStatus=$result.httpStatus;curlExit=$result.curlExit;failure=$result.failure;utc=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json -Compress)+"`n",$utf8)
    [ordered]@{operation=$operation;httpStatus=$result.httpStatus;curlExit=$result.curlExit;failure=$result.failure;bytes=$result.responseBytes}|ConvertTo-Json -Compress
    if($null -ne $result.failure){break}
    if($results.Count -lt 4){Start-Sleep -Seconds 2}
}
$envLines=$null;$graphToken=$null;$exportToken=$null
$failed=@($results|Where-Object {$null -ne $_.failure}).Count -gt 0
$summary=[ordered]@{block=62;phase='SOURCE_INVESTIGATION';calls=$results.Count;maximumCalls=4;stoppedOnFailure=$failed;elapsedMilliseconds=$clock.ElapsedMilliseconds;rawPayloadPersisted=$false;rotationProven=$false;v2012aAccepted=$false;mapperExecuted=$false;sqlExecuted=$false;results=@($results.ToArray())}
[IO.File]::WriteAllText((Join-Path $private 'source-summary.json'),($summary|ConvertTo-Json -Depth 24),$utf8)
if($failed){exit 2}

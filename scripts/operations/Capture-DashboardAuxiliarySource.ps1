[CmdletBinding()]
param(
    [ValidateSet('Users','Raster')][Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Receipt,
    [Parameter(Mandatory)][string]$CaptureDirectory,
    [ValidateRange(1,5000)][int]$MaxPages=2000,
    [string]$RasterDate
)
$ErrorActionPreference='Stop'
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$private=[IO.Path]::GetFullPath($CaptureDirectory)
if($private.Equals($project,[StringComparison]::OrdinalIgnoreCase) -or
   $private.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw 'AUXILIARY_CAPTURE_REQUIRES_PRIVATE_DIRECTORY_OUTSIDE_REPOSITORY'
}
if($PSVersionTable.PSVersion -lt [version]'7.5'){throw 'POWERSHELL_75_REQUIRED'}
$receiptPath=[IO.Path]::GetFullPath($Receipt)
if(Test-Path -LiteralPath $receiptPath){throw 'RECEIPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($private)
$stop=Join-Path $private ('STOP429-'+$Source+'.json')
if(Test-Path -LiteralPath $stop){throw 'SOURCE_RATE_LIMIT_STOP'}
$config=@{}
foreach($line in [IO.File]::ReadLines((Join-Path $project '.env'))){
    if($line -match '^\s*(API_GRAPHQL_ENDPOINT|API_GRAPHQL_TOKEN|API_BASE_URL|RASTER_BASE_URL|RASTER_AMBIENTE|RASTER_LOGIN|RASTER_SENHA|RASTER_STATUS_VIAGEM)\s*=\s*(.*?)\s*$'){
        $config[$Matches[1]]=$Matches[2].Trim('"').Trim("'")
    }
}
function Quote-Curl([string]$Text) {
    if($Text -match '[\x00-\x1f\x7f]'){throw 'CURL_CONFIG_VALUE_INVALID'}
    '"'+$Text.Replace('\','\\').Replace('"','\"')+'"'
}
$result=[ordered]@{source=$Source;passed=$false;requests=0;status=$null;terminal=$false;items=0;startedAt=[DateTimeOffset]::UtcNow.ToString('o')}
$temporary=Join-Path $private ([Guid]::NewGuid().ToString('N')+'.part')
function Invoke-Source([uri]$Uri,$Body,[string]$Authorization,[int]$Limit) {
    if($Uri.Scheme -cne 'https' -or $Uri.UserInfo -or $Uri.Query -or $Uri.Fragment -or
       (-not $Uri.IsDefaultPort -and ($Source -ne 'Raster' -or $Uri.Port -ne 8443))){
        throw 'HTTPS_ENDPOINT_REQUIRED'
    }
    $args=@(
        'url = '+(Quote-Curl $Uri.AbsoluteUri)
        'header = "Content-Type: application/json"'
        'header = "Accept: application/json"'
        'data-binary = '+(Quote-Curl ($Body | ConvertTo-Json -Depth 8 -Compress))
        'output = '+(Quote-Curl $temporary)
    )
    if($Authorization){$args+=('header = '+(Quote-Curl ('Authorization: Bearer '+$Authorization)))}
    $result.requests++
    $status=($args -join "`n") | & curl.exe --disable --config - --silent --proto '=https' --tlsv1.2 --connect-timeout 10 --max-time 30 --max-filesize $Limit --write-out '%{http_code}'
    if($LASTEXITCODE -ne 0 -or $status -notmatch '^\d{3}$'){
        $result.curlExit=[int]$LASTEXITCODE
        throw 'CURL_TRANSPORT_FAILED'
    }
    $result.status=[int]$status
    if($result.status -eq 429){
        [IO.File]::WriteAllText($stop,([ordered]@{source=$Source;at=[DateTimeOffset]::UtcNow.ToString('o');status=429}|ConvertTo-Json))
    }
    if($result.status -ne 200){throw ('HTTP_'+$result.status)}
    $raw=[IO.File]::ReadAllText($temporary)
    if(-not $raw){throw 'EMPTY_RESPONSE'}
    $raw | ConvertFrom-Json -AsHashtable -Depth 64 -NoEnumerate -DateKind String
}
$mutex=[Threading.Mutex]::new($false,('Global\ETL_V2_DASHBOARD_'+$Source.ToUpperInvariant()+'_OBSERVATION'))
$held=$false
try {
    try{$held=$mutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$held=$true}
    if(-not $held){throw 'SOURCE_OBSERVATION_ALREADY_RUNNING'}
    if($Source -eq 'Users'){
        $uri=$null
        if(-not [uri]::TryCreate($config.API_GRAPHQL_ENDPOINT,[UriKind]::Absolute,[ref]$uri)){
            if(-not $config.API_GRAPHQL_ENDPOINT.StartsWith('/') -or $config.API_GRAPHQL_ENDPOINT.StartsWith('//')){
                throw 'ESL_GRAPHQL_ROUTE_REQUIRED'
            }
            $uri=[uri]::new([uri]$config.API_BASE_URL,$config.API_GRAPHQL_ENDPOINT)
        }
        if($uri.Host -cne ([uri]$config.API_BASE_URL).Host -or -not $config.API_GRAPHQL_TOKEN){throw 'ESL_GRAPHQL_CONFIG_REQUIRED'}
        $query='query V2UsersSnapshot($params: IndividualInput!, $after: String, $first: Int!) { individual(params: $params, after: $after, first: $first) { edges { node { id name } } pageInfo { endCursor hasNextPage } } }'
        $rows=[Collections.Generic.Dictionary[long,string]]::new()
        $cursor=$null
        $cursors=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $result.nullNames=0
        $result.emptyNames=0
        for($page=1;$page -le $MaxPages;$page++){
            $body=@{operationName='V2UsersSnapshot';query=$query;variables=@{params=@{enabled=$true};after=$cursor;first=20}}
            $data=Invoke-Source $uri $body $config.API_GRAPHQL_TOKEN 2097152
            if($data.errors -or -not ($data.data.individual.edges -is [System.Collections.IList])){
                throw 'USERS_GRAPHQL_INVALID_RESPONSE'
            }
            $connection=$data.data.individual
            if($connection.edges.Count -gt 20 -or -not ($connection.pageInfo.hasNextPage -is [bool])){
                throw 'USERS_PAGE_CONTRACT_INVALID'
            }
            foreach($edge in $connection.edges){
                [long]$id=0
                if(-not [long]::TryParse([string]$edge.node.id,[ref]$id) -or $id -le 0 -or $rows.ContainsKey($id)){
                    throw 'USERS_IDENTITY_INVALID_OR_REPEATED'
                }
                $name=$edge.node.name
                if($null -eq $name){$result.nullNames++}
                elseif($name -isnot [string] -or $name.Length -gt 255){throw 'USERS_NAME_CONTRACT_INVALID'}
                elseif(-not $name.Trim()){$result.emptyNames++}
                $rows.Add($id,$name)
                $result.items++
            }
            if(-not $connection.pageInfo.hasNextPage){$result.terminal=$true;break}
            $cursor=$connection.pageInfo.endCursor
            if($connection.edges.Count -eq 0 -or -not ($cursor -is [string]) -or -not $cursor -or -not $cursors.Add($cursor)){
                throw 'USERS_CURSOR_INVALID_OR_REPEATED'
            }
            if($page -lt $MaxPages){Start-Sleep -Seconds 3}
        }
        if($MaxPages -gt 1 -and (-not $result.terminal -or $rows.Count -eq 0)){throw 'USERS_TERMINAL_NOT_REACHED'}
        if($result.terminal -and $rows.Count -gt 0){
            $target=Join-Path $private ('users-'+[Guid]::NewGuid().ToString('N')+'.json')
            $sourceRows=@(foreach($item in $rows.GetEnumerator()){[ordered]@{id=$item.Key;name=$item.Value}})
            [IO.File]::WriteAllText($target,(ConvertTo-Json -InputObject $sourceRows -Compress -Depth 4))
            $result.captureFile=[IO.Path]::GetFileName($target)
            $result.sha256=(Get-FileHash -LiteralPath $target).Hash
        }
    } else {
        if(-not $RasterDate){
            $zone=[TimeZoneInfo]::FindSystemTimeZoneById('E. South America Standard Time')
            $RasterDate=[TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow,$zone).AddDays(-1).ToString('yyyy-MM-dd')
        }
        $base=[uri]$config.RASTER_BASE_URL
        if(-not $base.AbsolutePath.EndsWith('/datasnap/rest/TWebService',[StringComparison]::OrdinalIgnoreCase) -and
           -not $base.AbsolutePath.EndsWith('/datasnap/rest/TWebService/',[StringComparison]::OrdinalIgnoreCase)){
            throw 'RASTER_ROUTE_REQUIRED'
        }
        if(-not $config.RASTER_LOGIN -or -not $config.RASTER_SENHA -or -not $config.RASTER_AMBIENTE -or -not $config.RASTER_STATUS_VIAGEM){
            throw 'RASTER_CONFIG_REQUIRED'
        }
        [void][DateOnly]::ParseExact($RasterDate,'yyyy-MM-dd')
        $uri=[uri]($base.AbsoluteUri.TrimEnd('/')+'/%22getEventoFimViagem%22')
        $body=@{Ambiente=$config.RASTER_AMBIENTE;Login=$config.RASTER_LOGIN;Senha=$config.RASTER_SENHA;TipoRetorno='JSON';
               DataInicial=$RasterDate;DataFinal=$RasterDate;StatusViagem=$config.RASTER_STATUS_VIAGEM}
        $data=Invoke-Source $uri $body '' 10485760
        function Read-Trips($Node){
            if($Node -is [System.Collections.IDictionary]){
                if($Node.Contains('CodSolicitacao')){$Node;return}
                if($Node.Contains('result')){Read-Trips $Node.result;return}
                if($Node.Contains('Viagens')){
                    if(-not $Node.Contains('CodErro') -or [string]$Node.CodErro -ne '0'){throw 'RASTER_SOURCE_ERROR'}
                    Read-Trips $Node.Viagens
                    return
                }
                throw 'RASTER_RESPONSE_SHAPE_INVALID'
            }
            if($Node -is [System.Collections.IList]){foreach($item in $Node){Read-Trips $item};return}
            throw 'RASTER_RESPONSE_SHAPE_INVALID'
        }
        $trips=@(Read-Trips $data)
        $result.items=$trips.Count
        $result.day=$RasterDate
        if($trips.Count -eq 0 -or $trips.Count -ge 500){throw 'RASTER_DAY_COVERAGE_UNPROVEN'}
        $target=Join-Path $private ('raster-'+$RasterDate+'-'+[Guid]::NewGuid().ToString('N')+'.json')
        [IO.File]::WriteAllText($target,(ConvertTo-Json -InputObject $trips -Compress -Depth 50))
        $result.captureFile=[IO.Path]::GetFileName($target)
        $result.sha256=(Get-FileHash -LiteralPath $target).Hash
    }
    $result.passed=$true
} catch {
    $message=$_.Exception.Message
    $result.failure=if($message -cmatch '^[A-Z0-9_]+$'){$message}else{'OBSERVATION_FAILED'}
    throw $result.failure
} finally {
    if($held){$mutex.ReleaseMutex()}
    $mutex.Dispose()
    if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}
    $config.Clear()
    $result.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
    [IO.File]::WriteAllText($receiptPath,($result | ConvertTo-Json -Depth 5))
}

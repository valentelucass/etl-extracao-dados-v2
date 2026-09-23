#Requires -Version 7.5
param([ValidateSet('root','types','relations')][string]$Stage='root', [switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$schemaSelfTest=$SelfTest
. (Join-Path $PSScriptRoot 'Invoke-Bloco56IdentityMetadataProbe.ps1') -FunctionsOnly

function Protect-Schema($Value, [int]$Depth=0) {
    if ($Depth -gt 20) { throw 'B56_SCHEMA_DEPTH' }
    if ($null -eq $Value) { return $null }
    if ($Value -is [Collections.IList]) {
        if ($Value.Count -gt 2048) { throw 'B56_SCHEMA_COLLECTION_BOUND' }
        return ,@(foreach ($v in $Value) { Protect-Schema $v ($Depth+1) })
    }
    if ($Value -isnot [Collections.IDictionary]) { throw 'B56_SCHEMA_OBJECT' }
    $out=[ordered]@{}
    foreach ($key in $Value.Keys) {
        if ($key -cin @('name','kind')) {
            if ($null -eq $Value[$key]) { $out[$key]=$null }
            elseif ($Value[$key] -is [string] -and $Value[$key] -cmatch '^[A-Za-z_][A-Za-z0-9_]{0,159}$') { $out[$key]=$Value[$key] }
            else { throw 'B56_SCHEMA_NAME' }
        } elseif ($key -ceq 'description') {
            # Descriptions are supplier schema documentation. Retain no URL,
            # contact detail, long digit sequence or multiline application text.
            $d=$Value[$key]
            if ($null -eq $d) { $out[$key]=$null }
            elseif ($d -is [string] -and $d.Length -le 1000 -and $d -notmatch 'https?://|@|\d{5}|[\x00-\x08\x0b\x0c\x0e-\x1f]|Bearer|password|token') { $out[$key]=$d }
            else { $out[$key]='OMITTED_BY_SANITIZER' }
        } elseif ($key -cin @('data','__schema','queryType','types','fields','type','ofType','args','inputFields','enumValues','__type','debit','credit','inventory','claim','freight','debitInput','inventoryInput','claimInput','invoice','installment','relationA','relationB','relationC')) {
            $out[$key]=Protect-Schema $Value[$key] ($Depth+1)
        } else { throw 'B56_SCHEMA_UNEXPECTED_KEY' }
    }
    return $out
}

if ($schemaSelfTest) {
    $p=Protect-Schema ('{"data":{"__type":{"name":"Example","fields":[{"name":"id","description":"Identifier","type":{"kind":"SCALAR","name":"ID"}}]}}}'|ConvertFrom-Json -AsHashtable)
    if ($p.data.__type.fields[0].type.name -cne 'ID') { throw 'TEST_SCHEMA_TYPE' }
    $p=Protect-Schema (@{description='https://secret.invalid/SECRET_SENTINEL'})
    if ($p.description -cne 'OMITTED_BY_SANITIZER') { throw 'TEST_SCHEMA_SANITIZATION' }
    $rejected=$false;try {[void](Protect-Schema (@{payload='SECRET_SENTINEL'}))}catch{$rejected=$true};if(-not $rejected){throw 'TEST_SCHEMA_FIELDS'}
    'B56_SCHEMA_OFFLINE_TESTS_PASS_NO_NETWORK'
    exit 0
}
$private=Join-Path $root 'target/bloco56-continuacao'
$ledger=Join-Path $private ('schema-'+$Stage+'-ledger.jsonl')
$request=Join-Path $private ('request-schema-'+$Stage+'.json')
if(Test-Path -LiteralPath $ledger){throw 'B56_SCHEMA_SINGLE_USE_NO_REPEAT'}
$plan=Get-Content -LiteralPath $request -Raw|ConvertFrom-Json
if($plan.maximumCalls -ne 1 -or $plan.document -cne ('docs/catalogos/bloco56-continuacao/schema-'+$Stage+'.graphql')){throw 'B56_SCHEMA_REQUEST'}
$document=[IO.File]::ReadAllText((Join-Path $root $plan.document))
$envLines=[IO.File]::ReadAllLines((Join-Path $root '../etl-extracao-dados/.env'))
$base=Get-SafeBase (Read-EnvValue $envLines 'API_BASE_URL')
$endpoint=Read-EnvValue $envLines 'API_GRAPHQL_ENDPOINT'
$uri=if($endpoint.StartsWith('/')){[Uri]::new($base,$endpoint)}else{Get-SafeBase $endpoint}
if($uri.Scheme -cne 'https' -or $uri.Host -cne $base.Host -or $uri.Port -ne $base.Port -or $uri.AbsolutePath -cne '/graphql' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo){throw 'B56_SCHEMA_ENDPOINT'}
$token=Read-EnvValue $envLines 'API_GRAPHQL_TOKEN'
[IO.File]::AppendAllText($ledger,([ordered]@{stage=$Stage;state='RESERVED_OUTCOME_UNKNOWN';requestSha256=(Get-FileHash -LiteralPath $request).Hash.ToLowerInvariant();documentSha256=(Get-FileHash -LiteralPath (Join-Path $root $plan.document)).Hash.ToLowerInvariant();utc=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json -Compress)+"`n",$utf8)
$result=[ordered]@{stage=$Stage;httpStatus=0;curlExit=$null;bytes=0;elapsedMilliseconds=0;failure=$null;schema=$null}
try{
    $response=Invoke-MemoryGet $uri $token $document
    foreach($key in @('httpStatus','curlExit','bytes','elapsedMilliseconds','failure')){$result[$key]=$response[$key]}
    if($null -eq $result.failure){
        if($response.json -isnot [Collections.IDictionary] -or $response.json.Contains('errors') -or -not $response.json.Contains('data')){$result.failure='GRAPHQL_ERROR_OR_ENVELOPE'}
        else{$result.schema=Protect-Schema $response.json}
    }
}catch{$result.failure='LOCAL_TRANSPORT_OR_SCHEMA_FAILURE'}
$response=$null;$token=$null;$envLines=$null
[IO.File]::WriteAllText((Join-Path $private ('schema-'+$Stage+'.json')),($result|ConvertTo-Json -Depth 30),$utf8)
[IO.File]::AppendAllText($ledger,([ordered]@{stage=$Stage;state='OBSERVED';httpStatus=$result.httpStatus;failure=$result.failure;utc=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json -Compress)+"`n",$utf8)
[ordered]@{stage=$Stage;httpStatus=$result.httpStatus;failure=$result.failure;bytes=$result.bytes}|ConvertTo-Json -Compress
if($null -ne $result.failure){exit 2}

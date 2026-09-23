#Requires -Version 7.5
param([switch]$SelfTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$dataSelfTest = $SelfTest
. (Join-Path $PSScriptRoot 'Invoke-Bloco56IdentityMetadataProbe.ps1') -FunctionsOnly

function Get-WireType($Value) {
    if ($null -eq $Value) { return 'NULL' }
    if ($Value -is [string]) { return 'STRING' }
    if ($Value -is [bool]) { return 'BOOLEAN' }
    if ($Value -is [Collections.IDictionary]) { return 'OBJECT' }
    if ($Value -is [Collections.IList]) { return 'ARRAY' }
    if ($Value -is [long] -or $Value -is [int] -or $Value -is [bigint]) { return 'INTEGER' }
    if ($Value -is [double] -or $Value -is [decimal]) { return 'NUMBER' }
    throw 'B56_WIRE_TYPE'
}

function Add-Shape($Profiles, [string]$Path, $Value, [int]$Depth=0) {
    if ($Depth -gt 4 -or $Profiles.Count -gt 1024) { throw 'B56_SHAPE_BOUND' }
    if (-not $Profiles.Contains($Path)) { $Profiles[$Path]=[ordered]@{path=$Path;observations=0;types=[ordered]@{};emptyArrays=0;arrayItems=0;unprofiledObjectKeys=0} }
    $p=$Profiles[$Path]; $p.observations++
    $type=Get-WireType $Value
    if (-not $p.types.Contains($type)) { $p.types[$type]=0 }
    $p.types[$type]++
    if ($type -eq 'ARRAY') {
        if ($Value.Count -gt 256) { throw 'B56_CHILD_BOUND' }
        if ($Value.Count -eq 0) { $p.emptyArrays++ }
        $p.arrayItems += $Value.Count
        foreach ($child in $Value) { Add-Shape $Profiles ($Path+'/*') $child ($Depth+1) }
    } elseif ($type -eq 'OBJECT') {
        # No arbitrary object key can become retained business data.
        foreach ($key in $Value.Keys) {
            if ($key -cin @('id','number','series','serie','key','type','invoice_id','invoice_order_id','invoice_number','invoice_series','invoice_key','freight_id','accounting_credit_id','sequence_code','order_number','document','value','weight','volumes')) { Add-Shape $Profiles ($Path+'/'+$key) $Value[$key] ($Depth+1) }
            else { $p.unprofiledObjectKeys++ }
        }
    }
}

function Get-DataProfile($Json, [string[]]$Fields) {
    $rows = $null
    if ($Json -is [Collections.IList]) { $rows=$Json }
    elseif ($Json -is [Collections.IDictionary] -and -not $Json.Contains('errors') -and -not $Json.Contains('error') -and $Json['data'] -is [Collections.IList]) { $rows=$Json.data }
    else { throw 'B56_DATA_ENVELOPE' }
    $profiles=[ordered]@{}
    $sample=@($rows | Select-Object -First 2)
    foreach ($row in $sample) {
        if ($row -isnot [Collections.IDictionary]) { throw 'B56_ROW_OBJECT_REQUIRED' }
        foreach ($field in $Fields) { if ($row.Contains($field)) { Add-Shape $profiles ('/'+$field) $row[$field] } }
    }
    $keys=[Collections.Generic.List[object]]::new()
    foreach ($field in $Fields | Where-Object { $_ -match '(^id$|_id$|sequence|icm_fis_ioe_number)' }) {
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $missing=0;$nulls=0;$nonScalar=0;$values=0
        foreach ($row in $sample) {
            if (-not $row.Contains($field)) { $missing++; continue }
            $v=$row[$field];$type=Get-WireType $v
            if ($type -eq 'NULL') { $nulls++; continue }
            if ($type -notin @('STRING','INTEGER')) { $nonScalar++; continue }
            [void]$seen.Add($type+':'+[string]$v);$values++
        }
        $keys.Add([ordered]@{path='/'+$field;missing=$missing;nulls=$nulls;nonScalar=$nonScalar;sampledValues=$values;sampledDistinct=$seen.Count;sampledDuplicateValues=$values-$seen.Count;identityProven=$false})
    }
    return [ordered]@{physicalRows=$rows.Count;profiledRows=$sample.Count;physicalLimitExceeded=($rows.Count -gt 2);empty=($rows.Count -eq 0);profiles=@($profiles.Values);candidateProfiles=@($keys.ToArray());universalUniquenessProven=$false;stabilityProven=$false}
}

if ($dataSelfTest) {
    $fixture='{"data":[{"id":9,"invoices_mapping":[{"id":11,"number":"SECRET_SENTINEL","sensitive_dynamic_key":"SECRET_SENTINEL"}]},{"id":"9","invoices_mapping":null}]}'|ConvertFrom-Json -AsHashtable
    $p=Get-DataProfile $fixture @('id','invoices_mapping','accounting_debit_id')
    if ($p.physicalRows -ne 2 -or $p.candidateProfiles[0].sampledDistinct -ne 2 -or $p.candidateProfiles[1].missing -ne 2 -or ($p|ConvertTo-Json -Depth 15).Contains('SECRET_SENTINEL')) { throw 'TEST_DATA_TYPES_OR_SANITIZATION' }
    $expanded=Get-DataProfile ('[{"id":1},{"id":1},{"id":2}]'|ConvertFrom-Json -AsHashtable -NoEnumerate) @('id')
    if (-not $expanded.physicalLimitExceeded -or $expanded.profiledRows -ne 2 -or $expanded.candidateProfiles[0].sampledDuplicateValues -ne 1) { throw 'TEST_DATA_EXPANSION_BOUND' }
    $empty=Get-DataProfile ('{"data":[]}'|ConvertFrom-Json -AsHashtable) @('id')
    if (-not $empty.empty) { throw 'TEST_EMPTY' }
    $rejected=$false; try { [void](Get-DataProfile (@{errors=@('SECRET_SENTINEL')}) @('id')) } catch { $rejected=$true }; if (-not $rejected) { throw 'TEST_DATA_ERROR_ENVELOPE' }
    'B56_DATA_OFFLINE_TESTS_PASS_NO_NETWORK'
    exit 0
}

$private=Join-Path $root 'target/bloco56-continuacao'
$ledger=Join-Path $private 'data-ledger.jsonl'
$request=Join-Path $private 'request-data.json'
if (Test-Path -LiteralPath $ledger) { throw 'B56_DATA_SINGLE_USE_NO_REPEAT' }
$plan=Get-Content -LiteralPath $request -Raw|ConvertFrom-Json
if ($plan.maximumCalls -ne 4 -or $plan.per -ne 2 -or $plan.page -ne 1 -or $plan.windowStart -cne '2026-09-04' -or $plan.windowEnd -cne '2026-09-04' -or ($plan.templates -join ',') -cne '10633,6392,4924,8636') { throw 'B56_DATA_REQUEST_SCOPE' }
$definitions=@(
    @{id=10633;filters=@('check_in_orders.started_at');order='sequence_code asc'},
    @{id=6392;filters=@('insurance_claims.opening_at_date');order='sequence_code asc'},
    @{id=4924;filters=@('freights.service_at');order='id asc'},
    @{id=8636;filters=@('accounting_debits.issue_date','accounting_debits.created_at');order='issue_date desc'}
)
foreach ($definition in $definitions) {
    $info=Get-Content -LiteralPath (Join-Path $private ('info-'+$definition.id+'.json')) -Raw|ConvertFrom-Json
    if ($info.httpStatus -ne 200 -or $null -ne $info.failure) { throw 'B56_DATA_METADATA_PREREQUISITE' }
    $definition.fields=@($info.metadata.fields.name)+@('id','accounting_debit_id','accounting_credit_id','check_in_order_id','insurance_claim_id','freight_id','installment_id')|Sort-Object -Unique
    if (($plan.filters.([string]$definition.id) -join ',') -cne ($definition.filters -join ',')) { throw 'B56_DATA_FILTERS' }
}
$envLines=[IO.File]::ReadAllLines((Join-Path $root '../etl-extracao-dados/.env'))
$base=Get-SafeBase (Read-EnvValue $envLines 'API_BASE_URL')
$token=Read-EnvValue $envLines 'API_DATAEXPORT_TOKEN'
$requestHash=(Get-FileHash -LiteralPath $request).Hash.ToLowerInvariant()
$failed=$false;$calls=0
foreach ($definition in $definitions) {
    $id=$definition.id
    [IO.File]::AppendAllText($ledger,([ordered]@{template=$id;operation='DATA_GET';state='RESERVED_OUTCOME_UNKNOWN';requestSha256=$requestHash;utc=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json -Compress)+"`n",$utf8)
    $calls++
    $result=[ordered]@{template=$id;httpStatus=0;curlExit=$null;bytes=0;elapsedMilliseconds=0;failure=$null;sample=$null}
    try {
        $builder=[UriBuilder]::new($base);$builder.Path=$builder.Path.TrimEnd('/')+'/api/analytics/reports/'+$id+'/data'
        $query=[ordered]@{page='1';per='2';order_by=$definition.order}
        foreach ($filter in $definition.filters) { $query['search['+$filter.Replace('.','][')+']']='2026-09-04 - 2026-09-04' }
        $builder.Query=(@(foreach ($key in $query.Keys) { [Uri]::EscapeDataString($key)+'='+[Uri]::EscapeDataString($query[$key]) }) -join '&')
        $response=Invoke-MemoryGet $builder.Uri $token
        foreach ($key in @('httpStatus','curlExit','bytes','elapsedMilliseconds','failure')) { $result[$key]=$response[$key] }
        if ($null -eq $result.failure) {
            $result.sample=Get-DataProfile $response.json $definition.fields
            if ($result.sample.physicalLimitExceeded) { $result.failure='PHYSICAL_ROW_BOUND_EXCEEDED_STOPPED' }
        }
    } catch { $result.failure='LOCAL_TRANSPORT_OR_SCHEMA_FAILURE' }
    $response=$null
    [IO.File]::WriteAllText((Join-Path $private ('data-'+$id+'.json')),($result|ConvertTo-Json -Depth 15),$utf8)
    [IO.File]::AppendAllText($ledger,([ordered]@{template=$id;state='OBSERVED';httpStatus=$result.httpStatus;failure=$result.failure;utc=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json -Compress)+"`n",$utf8)
    [ordered]@{template=$id;httpStatus=$result.httpStatus;failure=$result.failure;physicalRows=if($null -eq $result.sample){$null}else{$result.sample.physicalRows}}|ConvertTo-Json -Compress
    if ($null -ne $result.failure) { $failed=$true;break }
    Start-Sleep -Seconds 2
}
$token=$null;$envLines=$null
[ordered]@{phase=$plan.phase;calls=$calls;stoppedOnFailure=$failed}|ConvertTo-Json -Compress
if ($failed) { exit 2 }

[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReportPath,
    [Parameter(Mandatory=$true)][string]$CaptureDirectory
)
$ErrorActionPreference='Stop'
$file=Get-Item -LiteralPath $ReportPath
$directory=Get-Item -LiteralPath $CaptureDirectory
if ($file.PSIsContainer -or -not $directory.PSIsContainer -or
    $file.BaseName -cnotmatch '^[0-9a-f]{32}$' -or $file.Length -gt 4194304) {
    throw 'REFRESH_AUDIT_REPORT_PATH_INVALID'
}
$bytes=[IO.File]::ReadAllBytes($file.FullName)
$report=[Text.UTF8Encoding]::new($false,$true).GetString($bytes) | ConvertFrom-Json
$entities=@($report.entities.entity | Sort-Object -Unique)
$coreEntities=@($entities | Where-Object {$_ -cnotin @('COLETAS_EXCLUIDAS','COMPROVANTES_ENTREGA')})
if ($report.schema -cne 'PRODUCTION_REFRESH0766' -or $report.passed -isnot [bool] -or
    -not $report.startedAt -or -not $report.completedAt -or
    ($report.passed -and ($entities.Count -ne @($report.entities).Count -or
        $coreEntities.Count -ne 11 -or
        ($entities -ccontains 'COMPROVANTES_ENTREGA' -and $entities -cnotcontains 'COLETAS_EXCLUIDAS')))) {
    throw 'REFRESH_AUDIT_REPORT_CONTRACT_INVALID'
}
$started=[DateTimeOffset]::Parse($report.startedAt,[Globalization.CultureInfo]::InvariantCulture)
$completed=[DateTimeOffset]::Parse($report.completedAt,[Globalization.CultureInfo]::InvariantCulture)
$failure=if (-not $report.passed) {
    if ($report.reason) { [string]$report.reason }
    else { [string](@($report.entities | Where-Object {$_.status -eq 'FAILED'} | Select-Object -First 1).reason) }
}
if (-not $report.passed -and $failure -cnotmatch '^[A-Z0-9_]{1,96}$') { throw 'REFRESH_AUDIT_FAILURE_CODE_REQUIRED' }

$status=@(Get-ChildItem -LiteralPath $directory.FullName -Filter '*.status' -File)
if ($status.Count -ne $report.http) { throw 'REFRESH_AUDIT_REQUEST_COUNT_DIFFERS' }

# A retry keeps the same report, day and page and receives the next request number.
# Keep the final attempt so a successful retry and a terminal failure are still checked below.
$ledger = Join-Path $directory.FullName 'ledger.jsonl'
if (Test-Path -LiteralPath $ledger -PathType Leaf) {
    $filesByName = @{}
    foreach ($entry in $status) { $filesByName[$entry.Name] = $entry }
    $superseded = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $observed = $null
    foreach ($line in [IO.File]::ReadLines($ledger)) {
        if ($line -clike '*"state":"OBSERVED"*') {
            $observed = ConvertFrom-Json -InputObject $line
            continue
        }
        if ($line -cnotlike '*"state":"RETRY_WAIT"*') { continue }
        $retry = ConvertFrom-Json -InputObject $line
        # Metadata and update discovery are not daily snapshots in the audit.
        if ($retry.day -ceq 'info' -or $retry.day -cmatch '^update-\d+-\d{4}-\d{2}-\d{2}$') { continue }
        if ($retry.state -cne 'RETRY_WAIT' -or $retry.request -notmatch '^\d{1,4}$' -or
            $retry.reportId -notmatch '^\d{4,5}$' -or $retry.day -cnotmatch '^\d{4}-\d{2}-\d{2}$' -or
            $retry.page -notmatch '^\d{1,3}$' -or $retry.http -notmatch '^\d{1,3}$') {
            throw 'REFRESH_AUDIT_RETRY_RECORD_INVALID'
        }
        $attempt = '{0:0000}-{1}-{2}-{3:000}.status' -f [int]$retry.request, $retry.reportId, $retry.day, [int]$retry.page
        $next = '{0:0000}-{1}-{2}-{3:000}.status' -f (1 + [int]$retry.request), $retry.reportId, $retry.day, [int]$retry.page
        if ($filesByName.ContainsKey($attempt) -and $filesByName.ContainsKey($next)) {
            $http = [IO.File]::ReadAllText($filesByName[$attempt].FullName).Trim()
            $statusCode = if ($http -cmatch '^\d{3}$') { [int]$http } else { 0 }
            if ($observed.request -ne $retry.request -or $observed.reportId -ne $retry.reportId -or
                $observed.day -cne $retry.day -or $observed.page -ne $retry.page -or
                $observed.http -ne $retry.http -or $statusCode -ne $retry.http -or
                ($statusCode -eq 200 -and -not $observed.curlExit)) {
                throw 'REFRESH_AUDIT_RETRY_HTTP_DIFFERS'
            }
            $null = $superseded.Add($attempt)
        }
    }
    $status = @($status | Where-Object { -not $superseded.Contains($_.Name) })
}
$operations=@($report.operations | Where-Object {$_.runId -and -not $_.failureStateUnconfirmed})
$seen=@{}
$details=@(foreach ($operation in $operations) {
    $entity=@($report.entities | Where-Object {$_.entity -ceq $operation.entity})
    if ($entity.Count -ne 1 -or $entity[0].reportId -notmatch '^\d{4,5}$' -or $operation.day -cnotmatch '^\d{4}-\d{2}-\d{2}$' -or
        $operation.runId -cnotmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$') {
        throw 'REFRESH_AUDIT_OPERATION_INVALID'
    }
    $key=$entity[0].reportId.ToString()+'-'+$operation.day
    $prefix='^\d{4}-'+[regex]::Escape($key)+'-(?<page>\d{3})\.status$'
    $remaining=@($status | Where-Object {$_.Name -cmatch $prefix} | Sort-Object Name | Select-Object -Skip ([int]$seen[$key]))
    $pages=@(for ($index=0;$index -lt $remaining.Count;$index++) {
        if ($index -gt 0 -and [regex]::Match($remaining[$index].Name,$prefix).Groups['page'].Value -ceq '001') { break }
        $remaining[$index]
    })
    $seen[$key]=[int]$seen[$key]+$pages.Count
    $rowCount=0
    $dataPages=0
    $terminal=$false
    for ($i=0;$i -lt $pages.Count;$i++) {
        $page=$pages[$i]
        if ([int][regex]::Match($page.Name,$prefix).Groups['page'].Value -ne ($i+1) -or $terminal) {
            throw 'REFRESH_AUDIT_PAGE_SEQUENCE_DIFFERS'
        }
        $http=[IO.File]::ReadAllText($page.FullName).Trim()
        if ($http -cne '200') {
            if ($operation.stored -or $i -ne ($pages.Count-1)) { throw 'REFRESH_AUDIT_HTTP_FAILED' }
            break
        }
        $body=[IO.Path]::ChangeExtension($page.FullName,'.json')
        if (-not (Test-Path -LiteralPath $body -PathType Leaf) -or (Get-Item -LiteralPath $body).Length -gt 10485760) {
            throw 'REFRESH_AUDIT_PAGE_REQUIRED'
        }
        $wire=ConvertFrom-Json -InputObject ('{"payload":'+[IO.File]::ReadAllText($body)+'}')
        $rows=$wire.payload
        if ($rows -isnot [array]) {
            if ($rows.errors) { throw 'REFRESH_AUDIT_PAGE_SHAPE_INVALID' }
            $rows=$rows.data
        }
        if ($rows -isnot [array]) { throw 'REFRESH_AUDIT_PAGE_SHAPE_INVALID' }
        $terminal=$rows.Count -eq 0
        if (-not $terminal) {
            $rowCount+=$rows.Count
            $dataPages++
        }
    }
    if ($operation.stored -and (-not $terminal -or -not $operation.sqlReadbackExact -or $rowCount -ne $operation.rows)) {
        throw 'REFRESH_AUDIT_PAGE_TOTAL_DIFFERS'
    }
    if (-not $operation.stored -and -not $operation.reason) { throw 'REFRESH_AUDIT_INCOMPLETE_OPERATION' }
    [ordered]@{runId=$operation.runId;entity=$operation.entity;pages=$dataPages;rows=if($operation.stored){$rowCount}else{$null}}
})
$sha=[Security.Cryptography.SHA256]::Create()
try { $digest=[BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','') }
finally { $sha.Dispose() }
[pscustomobject]@{
    cycle=[Guid]::ParseExact($file.BaseName,'N').ToString()
    started=$started.ToString('o');completed=$completed.ToString('o');passed=$report.passed;failure=$failure
    sha256=$digest;operations=$details
}

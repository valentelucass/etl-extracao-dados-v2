#requires -Version 7.5
<#
.SYNOPSIS
Qualifies only INFO metadata for the two reports requested in unit 0760.
.DESCRIPTION
Curl uses stdin for credentials. No business data is requested or written.
Every source action is reserved before dispatch; any failure stops the unit.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ReceiptPath,
    [switch]$Worker
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$ReceiptPath = [IO.Path]::GetFullPath($ReceiptPath)
if (Test-Path -LiteralPath $ReceiptPath) { throw 'NEW_ENTITIES_RECEIPT_EXISTS' }
if (-not $Worker) {
    $receipt = & (Join-Path (Split-Path $repo) '.runtime\memory-control\Executar-Batch.ps1') `
        -Executable (Join-Path $PSHOME 'pwsh.exe') `
        -ArgumentList @('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Worker','-ReceiptPath',$ReceiptPath) `
        -WorkingDirectory $repo -MaxMinutes 3 -ReturnReceipt -ReceiptPath ($ReceiptPath + '.memory.json')
    exit $receipt.job.ExitCode
}
. (Join-Path $PSScriptRoot 'DataExportProbeGate.ps1')
$shared = 'C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\independent-export-shared-dataexport'
$stopPath = Join-Path $shared 'STOP429.json'
$ledger = $ReceiptPath + '.ledger.jsonl'
$result = [ordered]@{schema='NEW_ENTITIES_INFO0760';startedUtc=[DateTime]::UtcNow.ToString('o');success=$false;calls=0;maximumCalls=2;retry=0;redirect=0;businessDataRequested=$false;SQL=0;reports=@();reason=$null}
$gate = $null
function Quote-Config([string]$value) {
    if ($value -match '[\x00-\x1f\x7f]') { throw 'NEW_ENTITIES_CONFIG_CONTROL' }
    return '"' + $value.Replace('\','\\').Replace('"','\"') + '"'
}
function Write-Ledger($entry) {
    $entry | ConvertTo-Json -Depth 8 -Compress | Add-Content -LiteralPath $ledger -Encoding utf8
}
function Summarize-Entries($node) {
    $entries = @()
    if ($node -is [Collections.IDictionary]) {
        $entries = @(foreach ($key in $node.Keys) { @{key=[string]$key;value=$node[$key]} })
    } elseif ($node -is [Collections.IList]) {
        $entries = @(foreach ($item in $node) { @{key=$null;value=$item} })
    } else { throw 'NEW_ENTITIES_METADATA_COLLECTION' }
    return @(foreach ($entry in $entries) {
        $item = $entry.value
        $safe = [ordered]@{}
        if ($null -ne $entry.key) { $safe['key'] = $entry.key }
        if ($item -is [Collections.IDictionary]) {
            $safe['metadataKeys'] = @($item.Keys | Sort-Object)
            foreach ($key in @('name','field','key','label','title','type','data_type','format','required','scope','group','namespace','search_key','filter_name')) {
                if ($item.Contains($key) -and ($null -eq $item[$key] -or $item[$key] -is [string] -or $item[$key] -is [bool])) {
                    $safe[$key] = $item[$key]
                }
            }
        } elseif ($item -is [string]) { $safe['type'] = $item }
        if ($safe.Count -eq 0) { throw 'NEW_ENTITIES_UNNAMED_METADATA' }
        $safe
    })
}
try {
    if (-not (Test-Path -LiteralPath $shared -PathType Container)) { throw 'NEW_ENTITIES_SHARED_MISSING' }
    if (Test-Path -LiteralPath $stopPath) { throw 'NEW_ENTITIES_SHARED_STOP429' }
    $gate = Enter-DataExportProbeGate
    if ($null -eq $gate) { throw 'NEW_ENTITIES_SOURCE_BUSY' }
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines((Join-Path $repo '.env'))) {
        if ($line -match '^\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\s*=\s*(.*)$') {
            $candidate = $Matches[2].Trim().Trim('"').Trim("'")
            if ($candidate) { $values[$Matches[1]] = $candidate }
        }
    }
    if (-not $values.ContainsKey('API_BASE_URL') -or -not $values.ContainsKey('API_DATAEXPORT_TOKEN')) { throw 'NEW_ENTITIES_LOCAL_CREDENTIAL_MISSING' }
    $origin = [Uri]$values.API_BASE_URL
    if ($origin.Scheme -cne 'https' -or $origin.Host -cne 'rodogarcia.eslcloud.com.br' -or $origin.UserInfo -or $origin.Query -or $origin.Fragment) { throw 'NEW_ENTITIES_ORIGIN_REFUSED' }
    foreach ($reportId in @(11067,12072)) {
        if (Test-Path -LiteralPath $stopPath) { throw 'NEW_ENTITIES_SHARED_STOP429' }
        if ($result.calls -gt 0) { Start-Sleep -Seconds 3 }
        $requestId = [guid]::NewGuid().ToString()
        Write-Ledger ([ordered]@{requestId=$requestId;state='RESERVED';reportId=$reportId;operation='INFO';utc=[DateTime]::UtcNow.ToString('o');maximumSeconds=30;maximumBytes=10485760})
        $result.calls++
        $start = [Diagnostics.ProcessStartInfo]::new('curl.exe')
        $start.UseShellExecute=$false; $start.CreateNoWindow=$true
        $start.RedirectStandardInput=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
        $marker='__0760_HTTP_'+[guid]::NewGuid().ToString('N')+'__'
        foreach ($arg in @('--disable','--config','-','--globoff','--silent','--show-error','--request','GET','--proto','=https','--max-redirs','0','--connect-timeout','10','--max-time','30','--max-filesize','10485760','--output','-','--write-out',($marker+'%{http_code}'+$marker))) { $start.ArgumentList.Add($arg) }
        $url = [Uri]::new($origin,('/api/analytics/reports/'+$reportId+'/info'))
        $config='url = '+(Quote-Config $url.AbsoluteUri)+"`nheader = "+(Quote-Config ('Authorization: Bearer '+$values.API_DATAEXPORT_TOKEN))+"`nheader = "+(Quote-Config 'Accept: application/json')+"`n"
        $process=$null; $memory=[IO.MemoryStream]::new()
        try {
            $process=[Diagnostics.Process]::Start($start)
            $stderr=$process.StandardError.ReadToEndAsync()
            $process.StandardInput.Write($config); $process.StandardInput.Close(); $config=$null
            $buffer=[byte[]]::new(8192)
            while (($read=$process.StandardOutput.BaseStream.Read($buffer,0,$buffer.Length)) -gt 0) {
                if ($memory.Length+$read -gt 10486272) { $process.Kill($true); throw 'NEW_ENTITIES_RESPONSE_BOUND' }
                $memory.Write($buffer,0,$read)
            }
            $process.WaitForExit(); $null=$stderr.GetAwaiter().GetResult()
            $body=[Text.Encoding]::UTF8.GetString($memory.ToArray())
            $match=[regex]::Match($body,[regex]::Escape($marker)+'(\d{3})'+[regex]::Escape($marker)+'$')
            if (-not $match.Success) { throw 'NEW_ENTITIES_HTTP_RESULT_UNKNOWN' }
            $status=[int]$match.Groups[1].Value
            Write-Ledger ([ordered]@{requestId=$requestId;state='OBSERVED';reportId=$reportId;httpStatus=$status;curlExit=$process.ExitCode;utc=[DateTime]::UtcNow.ToString('o')})
            if ($status -eq 429) {
                if (-not (Test-Path -LiteralPath $stopPath)) { [IO.File]::WriteAllText($stopPath,(@{schema='SOURCE_STOP429';reportId=$reportId;requestId=$requestId;utc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Compress)) }
                throw 'NEW_ENTITIES_HTTP429'
            }
            if ($process.ExitCode -ne 0 -or $status -ne 200) { throw 'NEW_ENTITIES_HTTP_FAILURE' }
            $json=ConvertFrom-Json -InputObject $body.Substring(0,$match.Index) -AsHashtable -NoEnumerate -Depth 64 -DateKind String
            if ($json -isnot [Collections.IDictionary] -or $json.Contains('errors') -or $json.Contains('error')) { throw 'NEW_ENTITIES_INFO_ENVELOPE' }
            $node=$json
            if ($json.Contains('data')) { $node=$json.data }
            if ($node -isnot [Collections.IDictionary] -or -not $node.Contains('fields') -or -not $node.Contains('filters')) { throw 'NEW_ENTITIES_INFO_CONTRACT' }
            $result.reports+=@([ordered]@{reportId=$reportId;httpStatus=$status;topLevelKeys=@($node.Keys | Sort-Object);defaultOrder=$node.default_order;requiredDateFilters=$node.required_date_filters;fields=@(Summarize-Entries $node.fields);filters=@(Summarize-Entries $node.filters)})
        } finally {
            if ($null -ne $process) { if (-not $process.HasExited) { $process.Kill($true) }; $process.Dispose() }
            $memory.Dispose(); $body=$null; $json=$null; $node=$null; $config=$null
        }
    }
    $result.success=$true
} catch {
    $reason=$_.Exception.Message
    $result.reason=if ($reason -cmatch '^NEW_ENTITIES_[A-Z0-9_]+$') { $reason } else { 'NEW_ENTITIES_INTERNAL_FAILURE' }
} finally {
    $values=$null
    Exit-DataExportProbeGate $gate
    $result['finishedUtc']=[DateTime]::UtcNow.ToString('o')
    $result | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $ReceiptPath -Encoding utf8
}
if (-not $result.success) { Write-Output $result.reason; exit 1 }
Write-Output 'NEW_ENTITIES_INFO_COMPLETE'

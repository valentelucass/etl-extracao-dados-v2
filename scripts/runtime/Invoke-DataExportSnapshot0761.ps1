[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$RuntimeDirectory,
    [Parameter(Mandatory=$true)][string]$PrivateDirectory,
    [Parameter(Mandatory=$true)][string]$ReceiptPath,
    [Parameter(Mandatory=$true)][string]$PeriodStart,
    [Parameter(Mandatory=$true)][string]$PeriodEnd
)
$ErrorActionPreference='Stop'
$repo0761=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$storage0761='C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\'
$runtime0761=[IO.Path]::GetFullPath($RuntimeDirectory)
$private0761=[IO.Path]::GetFullPath($PrivateDirectory)
if(-not $runtime0761.StartsWith($storage0761,[StringComparison]::OrdinalIgnoreCase) -or
   -not $private0761.StartsWith($storage0761,[StringComparison]::OrdinalIgnoreCase)) { throw 'SNAPSHOT_PRIVATE_ROOT' }
if((Test-Path -LiteralPath $private0761) -or (Test-Path -LiteralPath $ReceiptPath)) { throw 'SNAPSHOT_FRESH_ATTEMPT_REQUIRED' }
$start0761=[DateTime]::ParseExact($PeriodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
$end0761=[DateTime]::ParseExact($PeriodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
if($end0761 -lt $start0761 -or ($end0761-$start0761).TotalDays -gt 7) { throw 'SNAPSHOT_EIGHT_DAYS_MAXIMUM' }
$manifest0761=Get-Content -LiteralPath (Join-Path $runtime0761 'manifest.json') -Raw | ConvertFrom-Json
if($manifest0761.schema -ne 'DATAEXPORT_SNAPSHOT_RUNTIME0761' -or @($manifest0761.files).Count -lt 2) { throw 'SNAPSHOT_RUNTIME_MANIFEST' }
foreach($pin0761 in $manifest0761.files) {
    if($pin0761.name -notmatch '^[A-Za-z0-9_.-]+\.jar$') { throw 'SNAPSHOT_RUNTIME_NAME' }
    $path0761=Join-Path $runtime0761 $pin0761.name
    if((Get-FileHash -LiteralPath $path0761 -Algorithm SHA256).Hash -ne $pin0761.sha256) { throw 'SNAPSHOT_RUNTIME_DRIFT' }
}
$stop0761=Join-Path $storage0761 'quatro-etapas-20261024-01\independent-export-shared-dataexport\STOP429.json'
if(Test-Path -LiteralPath $stop0761) { throw 'SNAPSHOT_SHARED_STOP429' }
$mutex0761=New-Object Threading.Mutex($false,'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
$held0761=$false
try {
    try { $held0761=$mutex0761.WaitOne(0) } catch [Threading.AbandonedMutexException] { $held0761=$true }
    if(-not $held0761) { throw 'SNAPSHOT_SOURCE_BUSY' }
    $java0761='C:\Users\suporte\AppData\Local\Programs\Eclipse Adoptium\jdk-17\bin\java.exe'
    $arguments0761=@('-Xmx768m','-Dfile.encoding=UTF-8','-cp',(Join-Path $runtime0761 '*'),
        'br.com.esl.etl.v2.bootstrap.DataExportSnapshotMain',(Join-Path $repo0761 '.env'),
        $private0761,[IO.Path]::GetFullPath($ReceiptPath),$PeriodStart,$PeriodEnd)
    $executor0761=Join-Path (Split-Path $repo0761 -Parent) '.runtime\memory-control\Executar-Batch.ps1'
    $memory0761=$ReceiptPath+'.memory.json'
    & $executor0761 -Executable $java0761 -ArgumentList $arguments0761 -WorkingDirectory $repo0761 -MaxMinutes 65 -ReceiptPath $memory0761 -ReturnReceipt | Out-Null
    $result0761=Get-Content -LiteralPath $memory0761 -Raw | ConvertFrom-Json
    if($result0761.job.ExitCode -ne 0) { throw ('SNAPSHOT_PROCESS_EXIT_'+$result0761.job.ExitCode) }
} finally {
    if($held0761) { $mutex0761.ReleaseMutex() }
    $mutex0761.Dispose()
}

#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$PackageAttempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$SmokeAttempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$utf8=[Text.UTF8Encoding]::new($false,$true)
[Console]::OutputEncoding=$utf8
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$evidence=Join-Path $round $Attempt
if(Test-Path -LiteralPath $evidence){throw 'QUAL_CONTROL_GUARD_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($evidence)
function Json([string]$path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 30),$utf8)}
function Read([string]$path){return Get-Content -Raw -LiteralPath $path|ConvertFrom-Json -AsHashtable}
$prior=Read (Join-Path $round ($SmokeAttempt+'/result.json'))
if($prior.state -cne 'PASS_LOCAL' -or $prior.package -cne $PackageAttempt){throw 'QUAL_CONTROL_GUARD_SOURCE_UNQUALIFIED'}
Json (Join-Path $evidence 'reservation.json') ([ordered]@{state='RESERVED';package=$PackageAttempt;smoke=$SmokeAttempt;
    maximumCases=8;caseSeconds=90;maximumLogBytes=1048576;sql='status only; no JDBC';
    target=$evidence;recovery='mutate only fresh copied control, preserve source evidence and all failures'})
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$package=Join-Path $round $PackageAttempt;$receipt=Read (Join-Path $package 'result.json')
$pwsh=(Get-Command pwsh.exe -CommandType Application|Select-Object -First 1).Source
$results=[Collections.Generic.List[object]]::new()
foreach($kind in @('receipt-exit','reused-pid','reconciliation-exit','duplicate-output','campaign-result','partial-log','partial-journal','extra-control')){
    $caseRoot=Join-Path $evidence $kind;$null=[IO.Directory]::CreateDirectory($caseRoot)
    $payload=Join-Path $caseRoot 'pacote extraído'
    $null=Expand-QualificationPackage -Archive (Join-Path $package 'qualification.zip') -ArchiveSha256 $receipt.archiveSha256 -Destination $payload -AllowedRoot $round
    $control=Join-Path $caseRoot 'control'
    Copy-Item -LiteralPath (Join-Path $round ($SmokeAttempt+'/control')) -Destination $control -Recurse
    $casePath=Join-Path $control 'case-smoke'
    switch($kind){
        'receipt-exit' {$file=Join-Path $casePath 'receipt.json';$json=Read $file;$json.exit=2;Json $file $json}
        'reused-pid' {$file=Join-Path $casePath 'process.json';$json=Read $file;$json.pid=$PID;$json.start='1970-01-01T00:00:00Z';Json $file $json}
        'reconciliation-exit' {$file=Join-Path $casePath 'reconciliation.json';$json=Read $file;$json.exit=2;Json $file $json}
        'duplicate-output' {$file=Join-Path $casePath 'receipt.json';$json=Read $file;$json.report.outputs[18].contract='SQL-01';Json $file $json}
        'campaign-result' {$file=Join-Path $control 'campaign-result.json';$json=Read $file;$json.cases[0].state='FAILED';Json $file $json}
        'partial-log' {[IO.File]::WriteAllText((Join-Path $casePath 'stdout.log'),'partial child log',$utf8)}
        'partial-journal' {[IO.File]::WriteAllText((Join-Path $control 'journal/0002.json'),'{',$utf8)}
        'extra-control' {Json (Join-Path $casePath 'foreign.json') @{synthetic=$true}}
    }
    $info=[Diagnostics.ProcessStartInfo]::new($pwsh);$info.WorkingDirectory=$payload
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in @('-NoProfile','-File',(Join-Path $payload 'Invoke-Qualification.ps1'),'-Command','status',
        '-ManifestSha256',$receipt.manifestSha256,'-Control',$control)){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($info)
    try{
        Json (Join-Path $caseRoot 'process.json') ([ordered]@{state='STARTED';pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');command='status';caseSeconds=90})
        $outPath=Join-Path $caseRoot 'stdout.log';$errPath=Join-Path $caseRoot 'stderr.log'
        $outFile=[IO.File]::Open($outPath,[IO.FileMode]::CreateNew);$errFile=[IO.File]::Open($errPath,[IO.FileMode]::CreateNew)
        $outTask=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$errTask=$process.StandardError.BaseStream.CopyToAsync($errFile)
        $timer=[Diagnostics.Stopwatch]::StartNew();$limit=$false
        while(-not $process.WaitForExit(100)){
            if($timer.Elapsed.TotalSeconds -gt 90 -or $outFile.Length -gt 1048576 -or $errFile.Length -gt 1048576){$limit=$true;$process.Kill($true);$process.WaitForExit();break}
        }
        $code=if($limit){124}else{$process.ExitCode}
        try{$null=$outTask.GetAwaiter().GetResult();$null=$errTask.GetAwaiter().GetResult()}finally{$outFile.Dispose();$errFile.Dispose()}
        Json (Join-Path $caseRoot 'exit.json') ([ordered]@{exit=$code;limit=$limit})
        $err=[IO.File]::ReadAllText($errPath,$utf8)
        $out=[IO.File]::ReadAllText($outPath,$utf8)
        $refused=$false;$observed='ACCEPTED'
        if($code -eq 2){$failure=Read-QualificationJsonBytes $utf8.GetBytes($err.Trim());$refused=$failure.state -ceq 'REFUSED';$observed=$failure.code}
        $results.Add([ordered]@{case=$kind;exit=$code;refused=$refused;code=$observed;passed=$refused})
        Json (Join-Path $evidence 'result.json') ([ordered]@{state='IN_PROGRESS';cases=$results.ToArray()})
    }finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
}
$passed=@($results|Where-Object {-not $_.passed}).Count -eq 0
Json (Join-Path $evidence 'result.json') ([ordered]@{state=if($passed){'PASS_LOCAL'}else{'FAILED'};package=$PackageAttempt;cases=$results.ToArray();count=$results.Count;jdbc='NOT_STARTED'})
if(-not $passed){throw 'QUAL_CONTROL_GUARD_ACCEPTED_MUTATION'}
Get-Content -Raw -LiteralPath (Join-Path $evidence 'result.json')

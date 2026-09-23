#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output=Join-Path $root ('target/macrobloco-analitico-20260912-01/'+$Attempt)
if(Test-Path -LiteralPath $output){throw 'ANA_SCAN_GUARD_COLLISION'}
[void][IO.Directory]::CreateDirectory($output)
$scanner=Join-Path $root 'scripts/security/Invoke-OfflineSecretScan.ps1'
$java='src/main/java/br/com/esl/etl/v2/plataforma/fonte/raster/RasterLoopbackTransport.java'
$sql='database/migrations/V071__consume_analytic_owned_fleet_references.sql'
$json='src/main/resources/analytic-laboratory/fleet-references.synthetic.json'
$original=[ordered]@{}
foreach($path in @($java,$sql,$json)){$original[$path]=[IO.File]::ReadAllText((Join-Path $root $path))}
@{state='RESERVED_OUTCOME_UNKNOWN';secondsPerCase=30;campaignSeconds=180;cases=7;syntheticOnly=$true}|
    ConvertTo-Json|Set-Content (Join-Path $output 'reservation.json') -Encoding utf8
$timer=[Diagnostics.Stopwatch]::StartNew()
$results=[Collections.Generic.List[object]]::new()
foreach($case in @('exact-fixtures','changed-password','different-path','changed-document-token','changed-sql-identifier','clean-cjs','secret-cjs')){
    if($timer.Elapsed.TotalSeconds -ge 180){throw 'ANA_SCAN_GUARD_BUDGET'}
    $fixture=Join-Path $output $case
    [void][IO.Directory]::CreateDirectory($fixture)
    & git -C $fixture init --quiet
    if($LASTEXITCODE -ne 0){throw 'ANA_SCAN_GUARD_GIT'}
    foreach($path in $original.Keys){
        $text=$original[$path]
        if($case -eq 'changed-password' -and $path -eq $java){$text=$text.Replace('synthetic-raster-password','synthetic-other-password')}
        if($case -eq 'changed-sql-identifier' -and $path -eq $sql){$text=$text.Replace('=normalized.','=unrecognized.')}
        if($case -eq 'changed-document-token' -and $path -eq $json){
            $document=$text|ConvertFrom-Json
            $document.documents[0].documentToken='1'*64
            $text=$document|ConvertTo-Json -Depth 20
        }
        $destination=Join-Path $fixture $path
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $destination))
        [IO.File]::WriteAllText($destination,$text,[Text.UTF8Encoding]::new($false))
    }
    if($case -eq 'different-path'){
        [IO.File]::WriteAllText((Join-Path $fixture 'Other.java'),$original[$java],[Text.UTF8Encoding]::new($false))
    }
    $cjsSentinel='Bearer '+('SYNTHETIC'+'-CJS-123456789')
    if($case -in @('clean-cjs','secret-cjs')){
        $content=if($case -eq 'clean-cjs'){'const fixture = 1;'}else{$cjsSentinel}
        [IO.File]::WriteAllText((Join-Path $fixture 'sample.cjs'),$content,[Text.UTF8Encoding]::new($false))
    }
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=(Get-Command pwsh).Source
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in @('-NoProfile','-File',$scanner,'-Source',$fixture)){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        [void]$process.Start()
        @{case=$case;pid=$process.Id;state='RUNNING'}|ConvertTo-Json -Compress|Add-Content (Join-Path $output 'processes.jsonl') -Encoding utf8
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $finished=$process.WaitForExit(30000)
        if(-not $finished){$process.Kill($true);$process.WaitForExit()}
        $text=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $output ($case+'.log')),$text,[Text.UTF8Encoding]::new($false))
        $expected=if($case -in @('exact-fixtures','clean-cjs')){0}else{1}
        $passed=$finished -and $process.ExitCode -eq $expected -and
            -not $text.Contains('synthetic-other-password') -and -not $text.Contains(('1'*64)) -and -not $text.Contains($cjsSentinel)
        $result=@{case=$case;expected=$expected;exit=$process.ExitCode;passed=$passed;timedOut=(-not $finished)}
        $results.Add($result)
        $result|ConvertTo-Json -Compress|Add-Content (Join-Path $output 'observations.jsonl') -Encoding utf8
    }finally{$process.Dispose()}
}
$passed=@($results|Where-Object {-not $_.passed}).Count -eq 0
@{passed=$passed;cases=@($results);genericExemptionsAdded=$false}|ConvertTo-Json -Depth 5|
    Set-Content (Join-Path $output 'result.json') -Encoding utf8
if(-not $passed){throw 'ANA_SCAN_GUARDS_FAILED'}
'ANA_SCAN_GUARDS_PASS_SEVEN_CASES'

#Requires -Version 7.5
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [ValidateSet('Compile','Directed','Physical','PackagePhysical','VerifyPhysical','Package','PackageDirected')][string]$Phase='Compile',
    [ValidatePattern('^[A-Za-z0-9,*]*$')][string]$Tests='',
    [ValidateRange(60,3600)][int]$BudgetSeconds=900,
    [string]$Snapshot=''
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($Phase -ceq 'PackageDirected' -and -not $Tests){throw 'QUAL_DIRECTED_PACKAGE_TESTS_REQUIRED'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$attemptRoot=Join-Path $round $Attempt
if(Test-Path -LiteralPath $attemptRoot){throw 'QUAL_BUILD_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($attemptRoot)
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Save([string]$name,$value){[IO.File]::WriteAllText((Join-Path $attemptRoot $name),($value|ConvertTo-Json -Depth 12),$utf8)}
Save 'reservation.json' ([ordered]@{state='RESERVED_OUTCOME_UNKNOWN';phase=$Phase;attempt=$Attempt;
    target='localhost/ETL_SISTEMA_V2_SHADOW';budgetSeconds=$BudgetSeconds;java=17;heapMiB=512;
    precondition='adopted A-N local scope; canonical before snapshots retained';ddl=$false;
    domainCommit=$false;recovery='observe owned process and SQL aggregates before retry'})
$build=Join-Path $attemptRoot 'build'
$sourceRoot=$root
if($Snapshot){
    $sourceRoot=[IO.Path]::GetFullPath($Snapshot)
    if(-not $sourceRoot.StartsWith($round+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
        throw 'QUAL_SNAPSHOT_OUTSIDE_ROUND'
    }
}
Push-Location -LiteralPath $sourceRoot
try{$files=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*')
    if($LASTEXITCODE -ne 0){throw 'QUAL_BUILD_ENUMERATION'}
}finally{Pop-Location}
$inventory=@(foreach($relative in $files|Sort-Object){
    $source=Join-Path $sourceRoot $relative
    $dest=Join-Path $build $relative
    $null=[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
    [IO.File]::Copy($source,$dest,$false)
    [ordered]@{path=$relative.Replace('\','/');sha256=(Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant()}
})
Save 'inputs.json' $inventory
$physical=$Phase -in @('Physical','PackagePhysical','VerifyPhysical')
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53900,N'QUAL_WRONG_TARGET',1; SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;"
function Sql([string]$database,[string]$statement,[string]$name){
    $info=[Diagnostics.ProcessStartInfo]::new('sqlcmd.exe');$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
    foreach($a in @('-S','localhost','-C','-E','-d',$database,'-l','5','-t','10','-b','-f','65001','-h','-1','-W','-Q',$statement)){$info.ArgumentList.Add($a)}
    $p=[Diagnostics.Process]::Start($info)
    try{$o=$p.StandardOutput.ReadToEndAsync();$e=$p.StandardError.ReadToEndAsync()
        if(-not $p.WaitForExit(15000)){$p.Kill($true);$p.WaitForExit();throw 'QUAL_SQL_TIMEOUT'}
        [IO.File]::WriteAllText((Join-Path $attemptRoot $name),$o.GetAwaiter().GetResult()+$e.GetAwaiter().GetResult(),$utf8)
        if($p.ExitCode -ne 0){throw 'QUAL_SQL_PREFLIGHT'}
    }finally{$p.Dispose()}
}
if($physical){
    Sql 'master' "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53900,N'QUAL_SHADOW_MISSING',1;" 'master.log'
    Sql 'ETL_SISTEMA_V2_SHADOW' $query 'before.log'
}
$arguments=@('--offline','--batch-mode','--no-transfer-progress','spotless:apply')
switch($Phase){
    'Compile' {$arguments+='test-compile'}
    'Directed' {$arguments+='test';if($Tests){$arguments+=('-Dtest='+$Tests)}}
    'Package' {$arguments+=@('package','-DskipTests','-Pshadow-local-integration','-Dshadow.local.integration.enabled=true')}
    'PackageDirected' {$arguments+=@('package',('-Dtest='+$Tests),'-Pshadow-local-integration','-Dshadow.local.integration.enabled=true')}
    'Physical' {$arguments+=@('process-test-classes','failsafe:integration-test','failsafe:verify');if($Tests){$arguments+=('-Dit.test='+$Tests)}}
    'PackagePhysical' {$arguments+=@('package','failsafe:integration-test','failsafe:verify','-Dtest=Qualification*Test');if($Tests){$arguments+=('-Dit.test='+$Tests)}}
    'VerifyPhysical' {$arguments+=@('verify','-Dv2.measurement.receipt=true')}
}
if($physical){$arguments+=@('-Pshadow-local-integration','-Dshadow.local.integration.enabled=true')}
$info=[Diagnostics.ProcessStartInfo]::new('C:\Program Files (x86)\apache-maven-3.9.14\bin\mvn.cmd')
$info.WorkingDirectory=$build;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
$info.Environment['JAVA_HOME']='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$info.Environment['JAVA_TOOL_OPTIONS']='-Xmx512m -Dfile.encoding=UTF-8'
if($physical){$info.Environment['V2_SHADOW_JDBC_URL']='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=45000'}
foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
$process=[Diagnostics.Process]::Start($info)
$logLimitExceeded=$false;$logIntegrity=$true
try{
    Save 'process.json' @{pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');arguments=$arguments;workingDirectory=$build}
    $outPath=Join-Path $attemptRoot 'stdout.log';$errPath=Join-Path $attemptRoot 'stderr.log'
    $outFile=[IO.FileStream]::new($outPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    $errFile=[IO.FileStream]::new($errPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try{
        $stdout=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$stderr=$process.StandardError.BaseStream.CopyToAsync($errFile)
        $watch=[Diagnostics.Stopwatch]::StartNew();$timedOut=$false
        while(-not $process.WaitForExit(500)){
            $logLimitExceeded=(Get-Item -LiteralPath $outPath).Length -gt 16777216 -or (Get-Item -LiteralPath $errPath).Length -gt 16777216
            $timedOut=$watch.Elapsed.TotalSeconds -ge $BudgetSeconds
            if($timedOut -or $logLimitExceeded){$process.Kill($true);$process.WaitForExit();break}
        }
        $code=if($timedOut){124}elseif($logLimitExceeded){125}else{$process.ExitCode}
        $null=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
    }finally{$outFile.Dispose();$errFile.Dispose()}
    foreach($path in @($outPath,$errPath)){
        if((Get-Item -LiteralPath $path).Length -gt 16777216){$logLimitExceeded=$true;$code=125;continue}
        try{$decoded=[IO.File]::ReadAllText($path,$utf8);if($decoded.Contains([char]0xfffd)){$logIntegrity=$false}}
        catch [Text.DecoderFallbackException]{$logIntegrity=$false}
    }
    if(-not $logIntegrity){$code=126}
}finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
# Copy formatting back only for files changed by THIS request and still matching the input snapshot.
$before=Get-Content -Raw -LiteralPath (Join-Path $round 'inventory-before.json')|ConvertFrom-Json
$original=@{};foreach($entry in $before.files){$original[$entry.path]=$entry.sha256}
if(-not $Snapshot){foreach($entry in $inventory){
    if($entry.path.EndsWith('.java') -and (-not $original.ContainsKey($entry.path) -or $original[$entry.path] -cne $entry.sha256)){
        $current=Join-Path $root $entry.path
        if((Get-FileHash -LiteralPath $current).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'QUAL_CONCURRENT_SOURCE_CHANGE'}
        [IO.File]::Copy((Join-Path $build $entry.path),$current,$true)
    }
}}
$rollback=$null
if($physical){
    Sql 'ETL_SISTEMA_V2_SHADOW' $query 'after.log'
    $rollback=[IO.File]::ReadAllText((Join-Path $attemptRoot 'before.log'),$utf8) -ceq [IO.File]::ReadAllText((Join-Path $attemptRoot 'after.log'),$utf8)
}
Save 'result.json' @{state='OBSERVED';phase=$Phase;exit=$code;timedOut=$timedOut;rollbackConfirmed=$rollback;arguments=$arguments;
    rawLogMaximumBytes=16777216;logLimitExceeded=$logLimitExceeded;logUtf8Integrity=$logIntegrity}
Get-Content -LiteralPath (Join-Path $attemptRoot 'stdout.log') -Tail 24
Get-Content -LiteralPath (Join-Path $attemptRoot 'stderr.log') -Tail 5
if($physical -and -not $rollback){throw 'QUAL_AGGREGATE_DRIFT'}
exit $code

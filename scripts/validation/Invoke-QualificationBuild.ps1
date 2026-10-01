#Requires -Version 7.5
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [ValidateSet('Compile','Directed','Physical','PackagePhysical','VerifyPhysical','Package','PackageDirected','PackageShadow')][string]$Phase='Compile',
    [ValidatePattern('^[A-Za-z0-9,*]*$')][string]$Tests='',
    # Full local integration has 115 classes; the previous 3,600-second cap
    # stopped a clean run after 91 completed classes. Keep a finite ceiling
    # while allowing a single full qualification window when explicitly reserved.
    [ValidateRange(60,7200)][int]$BudgetSeconds=900,
    [string]$Snapshot='',
    [string]$JavaHome='',
    [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($Phase -ceq 'PackageDirected' -and -not $Tests){throw 'QUAL_DIRECTED_PACKAGE_TESTS_REQUIRED'}
$jdkHome=if($JavaHome){[IO.Path]::GetFullPath($JavaHome)}elseif($env:JAVA_HOME){[IO.Path]::GetFullPath($env:JAVA_HOME)}else{throw 'QUAL_JDK17_REQUIRED'}
$java=Join-Path $jdkHome 'bin/java.exe'
if(-not (Test-Path -LiteralPath $java -PathType Leaf)){throw 'QUAL_JDK17_REQUIRED'}
$javaVersion=(& $java -version 2>&1 | Out-String)
if($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "17[\.]'){throw 'QUAL_JDK17_REQUIRED'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root ('target/'+$RoundName)
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
$shadowPackage=$Phase -ceq 'PackageShadow'
if($physical -and [string]::IsNullOrWhiteSpace($env:V2_SHADOW_JDBC_URL)){
    throw 'QUAL_SHADOW_URL_REQUIRED'
}
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
# The lifecycle runs spotless:check; keep the copied source bytes immutable.
$arguments=@('--offline','--batch-mode','--no-transfer-progress')
switch($Phase){
    'Compile' {$arguments+='test-compile'}
    'Directed' {$arguments+='test';if($Tests){$arguments+=('-Dtest='+$Tests)}}
    # Offline package phases must use the global 12.8.2 pair pinned by the P08 lock.
    'Package' {$arguments+=@('package','-DskipTests')}
    'PackageDirected' {$arguments+=@('package',('-Dtest='+$Tests))}
    'PackageShadow' {$arguments+=@('package','-DskipTests')}
    'Physical' {$arguments+=@('process-test-classes','failsafe:integration-test','failsafe:verify');if($Tests){$arguments+=('-Dit.test='+$Tests)}}
    'PackagePhysical' {$arguments+=@('package','failsafe:integration-test','failsafe:verify','-Dtest=Qualification*Test');if($Tests){$arguments+=('-Dit.test='+$Tests)}}
    'VerifyPhysical' {$arguments+=@('verify','-Dv2.measurement.receipt=true')}
}
if($physical -or $shadowPackage){$arguments+=@('-Pshadow-local-integration','-Dshadow.local.integration.enabled=true')}
$mavenWrapper=Join-Path $build 'mvnw.cmd'
if(-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)){throw 'QUAL_MAVEN_WRAPPER_REQUIRED'}
$info=[Diagnostics.ProcessStartInfo]::new($mavenWrapper)
$info.WorkingDirectory=$build;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
$info.Environment['JAVA_HOME']=$jdkHome
$info.Environment['JAVA_TOOL_OPTIONS']='-Xmx512m -Dfile.encoding=UTF-8'
if($shadowPackage){$null=$info.Environment.Remove('V2_SHADOW_JDBC_URL')}
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
if($code -eq 0 -and $Phase -in @('Package','PackageDirected')){
    # The lock's native member is packaged as bytes only; it is never loaded by these phases.
    $lock=Get-Content -Raw -LiteralPath (Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.json')|ConvertFrom-Json
    $auth=@($lock.dependencies|Where-Object {$_.name -ceq 'mssql-jdbc_auth' -and $_.type -ceq 'dll'})
    if($auth.Count -ne 1 -or $auth[0].version -cne '12.8.2.x64' -or $auth[0].file -cne 'mssql-jdbc_auth-12.8.2.x64.dll'){
        throw 'QUAL_PACKAGE_NATIVE_LOCK'
    }
    $native=Join-Path $build 'target/native'
    $null=[IO.Directory]::CreateDirectory($native)
    if(@(Get-ChildItem -LiteralPath $native -File).Count -ne 0){throw 'QUAL_PACKAGE_NATIVE_UNEXPECTED'}
    $source=Join-Path $env:USERPROFILE '.m2/repository/com/microsoft/sqlserver/mssql-jdbc_auth/12.8.2.x64/mssql-jdbc_auth-12.8.2.x64.dll'
    if(-not (Test-Path -LiteralPath $source -PathType Leaf)){throw 'QUAL_PACKAGE_NATIVE_CACHE_MISSING'}
    if((Get-Item -LiteralPath $source).Length -ne $auth[0].size -or
       (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant() -cne $auth[0].sha256){
        throw 'QUAL_PACKAGE_NATIVE_CACHE_DRIFT'
    }
    [IO.File]::Copy($source,(Join-Path $native $auth[0].file),$false)
    Save 'native-staging.json' @{state='PASSIVE_BYTES_ONLY';phase=$Phase;file=$auth[0].file;sha256=$auth[0].sha256;source='local Maven cache';loaded=$false}
}
if($code -eq 0 -and $shadowPackage){
    $lock=Get-Content -Raw -LiteralPath (Join-Path $build 'docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.shadow-local-12.8.2.json')|ConvertFrom-Json
    $jdbc=@($lock.dependencies|Where-Object {$_.name -ceq 'mssql-jdbc' -and $_.type -ceq 'jar'})
    $auth=@($lock.dependencies|Where-Object {$_.name -ceq 'mssql-jdbc_auth' -and $_.type -ceq 'dll'})
    if($lock.dependencies.Count -ne 9 -or $jdbc.Count -ne 1 -or $auth.Count -ne 1 -or
       $jdbc[0].version -cne '12.8.2.jre11' -or $auth[0].version -cne '12.8.2.x64'){
        throw 'QUAL_SHADOW_PACKAGE_LOCK_PAIR'
    }
    $actualLib=@(Get-ChildItem -LiteralPath (Join-Path $build 'target/lib') -File)
    $actualNative=@(Get-ChildItem -LiteralPath (Join-Path $build 'target/native') -File)
    if($actualLib.Count -ne 8 -or $actualNative.Count -ne 1){throw 'QUAL_SHADOW_PACKAGE_DEPENDENCY_SET'}
    foreach($entry in $lock.dependencies){
        $folder=if($entry.type -ceq 'dll'){'native'}else{'lib'}
        $file=Join-Path $build ('target/'+$folder+'/'+$entry.file)
        if(-not (Test-Path -LiteralPath $file -PathType Leaf) -or
           (Get-Item -LiteralPath $file).Length -ne $entry.size -or
           (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256){
            throw 'QUAL_SHADOW_PACKAGE_DEPENDENCY_DRIFT'
        }
    }
    Save 'native-staging.json' @{state='PASSIVE_BYTES_ONLY';phase=$Phase;file=$auth[0].file;
        sha256=$auth[0].sha256;source='Maven Central cache via opt-in profile';loaded=$false}
}
$inputIntegrity=$null
if($code -eq 0){
    $inputIntegrity=$true
    foreach($entry in $inventory){
        $copied=Join-Path $build $entry.path
        if(-not (Test-Path -LiteralPath $copied -PathType Leaf) -or
            (Get-FileHash -LiteralPath $copied -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256){
            $inputIntegrity=$false;$code=127;break
        }
    }
}
$rollback=$null
if($physical){
    Sql 'ETL_SISTEMA_V2_SHADOW' $query 'after.log'
    $rollback=[IO.File]::ReadAllText((Join-Path $attemptRoot 'before.log'),$utf8) -ceq [IO.File]::ReadAllText((Join-Path $attemptRoot 'after.log'),$utf8)
}
Save 'result.json' @{state='OBSERVED';phase=$Phase;exit=$code;timedOut=$timedOut;rollbackConfirmed=$rollback;arguments=$arguments;
    packageVariant=if($shadowPackage){'SHADOW_LOCAL_12_8_2'}else{'NORMAL_12_8_2'};
    rawLogMaximumBytes=16777216;logLimitExceeded=$logLimitExceeded;logUtf8Integrity=$logIntegrity;
    inputIntegrity=$inputIntegrity}
Get-Content -LiteralPath (Join-Path $attemptRoot 'stdout.log') -Tail 24
Get-Content -LiteralPath (Join-Path $attemptRoot 'stderr.log') -Tail 5
if($physical -and -not $rollback){throw 'QUAL_AGGREGATE_DRIFT'}
exit $code

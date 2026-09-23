#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-relacional-20260911-01'
$build=Join-Path $round 'build/target'
$output=Join-Path $round $Attempt
if(Test-Path -LiteralPath $output){throw 'REL_JAR_ATTEMPT_EXISTS'}
$jar=@(Get-ChildItem -LiteralPath $build -Filter '*.jar' -File | Where-Object Name -NotMatch '^original-')
if($jar.Count -ne 1){throw 'REL_JAR_PACKAGE_REQUIRED'}
[void][IO.Directory]::CreateDirectory($output)
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53290,N'WRONG_TARGET',1; SELECT COUNT_BIG(*) FROM ctl.execution_attempt; SELECT COUNT_BIG(*) FROM ctl.execution_audit; SELECT COUNT_BIG(*) FROM ctl.relational_lab_run; SELECT COUNT_BIG(*) FROM core.relational_lab_root; SELECT COUNT_BIG(*) FROM core.relational_lab_link; SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog;"
& sqlcmd -S localhost -C -E -d master -l 5 -t 10 -b -Q "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53290,N'EXACT_SHADOW_MISSING',1;" *> (Join-Path $output 'master.log')
if($LASTEXITCODE -ne 0){throw 'REL_JAR_PREFLIGHT'}
& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $output 'before.log')
if($LASTEXITCODE -ne 0){throw 'REL_JAR_BEFORE'}
$url='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=30000'
$cases=@(
    @{name='scenario';args=@('scenario','--synthetic-relational-lab','--roots=2','--page-size=3');expected=0;optin=$true;url=$url},
    @{name='hydrate';args=@('hydrate','--synthetic-relational-lab','--roots=2');expected=0;optin=$true;url=$url},
    @{name='replay';args=@('replay','--synthetic-relational-lab','--roots=2','--days=2');expected=0;optin=$true;url=$url},
    @{name='status-degraded';args=@('status','--synthetic-relational-lab');expected=10;optin=$true;url=$url},
    @{name='optin-absent';args=@('scenario','--synthetic-relational-lab');expected=20;optin=$false;url=$url},
    @{name='foreign-host';args=@('scenario','--synthetic-relational-lab');expected=20;optin=$true;url='jdbc:sqlserver://unapproved.invalid;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true'},
    @{name='production-database';args=@('scenario','--synthetic-relational-lab');expected=20;optin=$true;url='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA;integratedSecurity=true'},
    @{name='real-path';args=@('scenario','--synthetic-relational-lab','--path=C:/real');expected=20;optin=$true;url=$url},
    @{name='graphql';args=@('scenario','--synthetic-relational-lab','--source=GRAPHQL');expected=20;optin=$true;url=$url},
    @{name='budget';args=@('scenario','--synthetic-relational-lab','--roots=999');expected=20;optin=$true;url=$url}
)
@{state='RESERVED_OUTCOME_UNKNOWN';target='localhost/ETL_SISTEMA_V2_SHADOW';cases=$cases.Count;secondsPerCase=30;heapMiB=512;rollbackOnly=$true;jarSha256=(Get-FileHash $jar[0].FullName).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $output 'reservation.json') -Encoding utf8
$results=[Collections.Generic.List[object]]::new()
foreach($case in $cases){
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $info.WorkingDirectory=$root
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($arg in @('-Xmx512m',('-Djava.library.path='+(Join-Path $build 'native')))){$info.ArgumentList.Add($arg)}
    if($case.optin){$info.ArgumentList.Add('-Dshadow.local.integration.enabled=true');$info.ArgumentList.Add('-Dshadow.local.integration.profile.active=true')}
    foreach($arg in @('-cp',($jar[0].FullName+';'+(Join-Path $build 'lib/*')),'br.com.esl.etl.v2.bootstrap.RelationalLaboratoryMain')+$case.args){$info.ArgumentList.Add($arg)}
    $info.Environment['V2_SHADOW_JDBC_URL']=$case.url
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try {
        [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $completed=$process.WaitForExit(30000)
        if(-not $completed){$process.Kill($true);$process.WaitForExit()}
        $outText=$stdout.GetAwaiter().GetResult();$errText=$stderr.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $output ($case.name+'.out.log')),$outText,[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $output ($case.name+'.err.log')),$errText,[Text.UTF8Encoding]::new($false))
        $results.Add(@{case=$case.name;expected=$case.expected;exit=$process.ExitCode;timedOut=(-not $completed);passed=($completed -and $process.ExitCode -eq $case.expected)})
    } finally {$process.Dispose()}
}
& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $output 'after.log')
if($LASTEXITCODE -ne 0){throw 'REL_JAR_AFTER'}
$restored=[IO.File]::ReadAllText((Join-Path $output 'before.log')) -ceq [IO.File]::ReadAllText((Join-Path $output 'after.log'))
$passed=$restored -and @($results | Where-Object {-not $_.passed}).Count -eq 0
@{passed=$passed;rollbackConfirmed=$restored;cases=@($results);layer='PACKAGED_JAR_PROCESS_REAL_JDBC';remoteCalls=0;publication=$false}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $output 'result.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $output 'result.json')
if(-not $passed){throw 'REL_JAR_VALIDATION_FAILED'}

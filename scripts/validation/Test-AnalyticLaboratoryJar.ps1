#Requires -Version 7.5
param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$BuildAttempt,
    [ValidatePattern('^[a-z0-9-]{1,64}$')][string]$TestAttempt,
    [string[]]$CaseNames=@()
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-analitico-20260912-01'
$snapshot=Join-Path $round ('build-'+$BuildAttempt)
$build=Join-Path $snapshot 'target'
$output=Join-Path $round $Attempt
if(Test-Path -LiteralPath $output){throw 'ANA_JAR_ATTEMPT_EXISTS'}
$jar=@(Get-ChildItem -LiteralPath $build -Filter '*.jar' -File | Where-Object Name -NotMatch '^original-')
if($jar.Count -ne 1){throw 'ANA_JAR_PACKAGE_REQUIRED'}
[void][IO.Directory]::CreateDirectory($output)
$verifiedBuild=Get-Content -Raw -LiteralPath (Join-Path $round ($BuildAttempt+'-exit.json'))|ConvertFrom-Json
if($verifiedBuild.exit -ne 0 -or $verifiedBuild.phase -cne 'VerifyPhysical'){throw 'ANA_JAR_FULL_VERIFY_REQUIRED'}
$testSnapshot=$snapshot
$changedTests=[Collections.Generic.List[object]]::new()
if($TestAttempt){
    # After a green full verify, only test changes may use an independently qualified snapshot.
    # Every changed/new IT must have run physically; removed tests and production drift are refused.
    $testSnapshot=Join-Path $round ('build-'+$TestAttempt)
    $verifiedTests=Get-Content -Raw -LiteralPath (Join-Path $round ($TestAttempt+'-exit.json'))|ConvertFrom-Json
    if($verifiedTests.exit -ne 0 -or $verifiedTests.phase -cne 'Physical'){throw 'ANA_JAR_AFFECTED_PHYSICAL_REQUIRED'}
    foreach($previous in Get-ChildItem -LiteralPath (Join-Path $snapshot 'src/test/java') -File -Recurse){
        $relative=[IO.Path]::GetRelativePath($snapshot,$previous.FullName)
        if(-not (Test-Path -LiteralPath (Join-Path $testSnapshot $relative))){throw 'ANA_JAR_TEST_REMOVAL'}
    }
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $testSnapshot 'src/test/java') -File -Recurse){
        $relative=[IO.Path]::GetRelativePath($testSnapshot,$file.FullName).Replace([char]92,[char]47)
        $before=Join-Path $snapshot $relative
        $hash=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        if((Test-Path -LiteralPath $before) -and (Get-FileHash -LiteralPath $before).Hash.ToLowerInvariant() -ceq $hash){continue}
        if(-not $relative.EndsWith('IT.java')){throw 'ANA_JAR_TEST_CHANGE_WITHOUT_PHYSICAL_CLASS'}
        $class=$relative.Substring('src/test/java/'.Length).Replace('/','.').Replace('.java','')
        $report=Join-Path $testSnapshot ('target/failsafe-reports/TEST-'+$class+'.xml')
        $xml=[xml](Get-Content -Raw -LiteralPath $report)
        if([int]$xml.testsuite.tests -le 0 -or [int]$xml.testsuite.failures -ne 0 -or
            [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0){throw 'ANA_JAR_CHANGED_TEST_NOT_PASSED'}
        $changedTests.Add(@{path=$relative;sha256=$hash;class=$class;tests=[int]$xml.testsuite.tests;
            reportSha256=(Get-FileHash -LiteralPath $report).Hash.ToLowerInvariant()})
    }
    foreach($relativeRoot in @('src/main/java','src/main/resources')){
        foreach($file in Get-ChildItem -LiteralPath (Join-Path $testSnapshot $relativeRoot) -File -Recurse){
            $relative=[IO.Path]::GetRelativePath($testSnapshot,$file.FullName)
            if((Get-FileHash -LiteralPath $file.FullName).Hash -cne
                (Get-FileHash -LiteralPath (Join-Path $snapshot $relative)).Hash){throw 'ANA_JAR_PRODUCTION_CHANGED_AFTER_VERIFY'}
        }
    }
}
# Compare the actual tested source snapshot and every packaged class/resource before Java starts.
$sourceIdentity=[Collections.Generic.List[object]]::new()
foreach($relativeRoot in @('src/main/java','src/main/resources','src/test/java')){
    $qualifiedSnapshot=if($relativeRoot -ceq 'src/test/java'){$testSnapshot}else{$snapshot}
    $sourceRoot=Join-Path $qualifiedSnapshot $relativeRoot
    $snapshotFiles=@(Get-ChildItem -LiteralPath $sourceRoot -File -Recurse)
    $currentFiles=@(Get-ChildItem -LiteralPath (Join-Path $root $relativeRoot) -File -Recurse)
    if($snapshotFiles.Count -ne $currentFiles.Count){throw 'ANA_JAR_SOURCE_INVENTORY_DRIFT'}
    foreach($file in $snapshotFiles){
        $relative=[IO.Path]::GetRelativePath($qualifiedSnapshot,$file.FullName)
        $current=Join-Path $root $relative
        if(-not (Test-Path -LiteralPath $current -PathType Leaf)){throw 'ANA_JAR_SOURCE_MISSING'}
        $digest=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        if((Get-FileHash -LiteralPath $current).Hash.ToLowerInvariant() -cne $digest){throw 'ANA_JAR_SOURCE_REVISION_DRIFT'}
        $sourceIdentity.Add(@{path=$relative.Replace([char]92,[char]47);sha256=$digest})
    }
}
$entries=[Collections.Generic.List[object]]::new()
$archive=[IO.Compression.ZipFile]::OpenRead($jar[0].FullName)
try{
    $classRoot=Join-Path $build 'classes'
    $classFiles=@(Get-ChildItem -LiteralPath $classRoot -File -Recurse)
    foreach($file in $classFiles){
        $entryName=[IO.Path]::GetRelativePath($classRoot,$file.FullName).Replace([char]92,[char]47)
        $entry=$archive.GetEntry($entryName)
        if($null -eq $entry){throw 'ANA_JAR_PACKAGED_ENTRY_MISSING'}
        $stream=$entry.Open()
        try{$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}
        finally{$stream.Dispose()}
        if($hash -cne (Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()){throw 'ANA_JAR_CLASS_RESOURCE_DRIFT'}
        $entries.Add(@{path=$entryName;sha256=$hash;bytes=$entry.Length})
    }
    if(@($archive.Entries|Where-Object {$_.FullName.EndsWith('.class')}).Count -ne
        @($classFiles|Where-Object {$_.Name.EndsWith('.class')}).Count){throw 'ANA_JAR_CLASS_INVENTORY_DRIFT'}
}finally{$archive.Dispose()}
@{state='SOURCE_AND_PACKAGED_CLASSES_MATCH';buildAttempt=$BuildAttempt;testAttempt=$TestAttempt;changedTests=@($changedTests);
    jarSha256=(Get-FileHash -LiteralPath $jar[0].FullName).Hash.ToLowerInvariant();
    sources=@($sourceIdentity);packagedEntries=@($entries)}|ConvertTo-Json -Depth 5|
    Set-Content -LiteralPath (Join-Path $output 'artifact-identity.json') -Encoding utf8
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53390,N'WRONG_TARGET',1; SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;"
& sqlcmd -S localhost -C -E -d master -l 5 -t 10 -b -Q "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53390,N'EXACT_SHADOW_MISSING',1;" *> (Join-Path $output 'master.log')
if($LASTEXITCODE -ne 0){throw 'ANA_JAR_PREFLIGHT'}
& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $output 'before.log')
if($LASTEXITCODE -ne 0){throw 'ANA_JAR_BEFORE'}
$url='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=30000'
$cases=[Collections.Generic.List[object]]::new()
foreach($command in @('scenario','recompose','replay','status')){
    $cases.Add(@{name=$command;args=@($command,'--synthetic-analytic-lab');expected=0;optin=$true;url=$url;main=$false})
}
foreach($number in 1..19){
    $contract='SQL-{0:D2}' -f $number
    $cases.Add(@{name=('query-{0:D2}' -f $number);args=@('query','--synthetic-analytic-lab',('--contract='+$contract));expected=0;optin=$true;url=$url;main=$false})
}
foreach($fault in @('RASTER_INCOMPLETE','MANIFEST_FLEET_MISSING','FINANCIAL_REFERENCE_MISSING','COLLECTION_SNAPSHOT_INVALID')){
    $cases.Add(@{name=('degraded-'+$fault.ToLowerInvariant());args=@('status','--synthetic-analytic-lab',('--fault='+$fault));expected=10;optin=$true;url=$url;main=$false})
}
foreach($refusal in @(
    @{name='path';option='--path=C:/synthetic-refused'},
    @{name='source';option='--source=GRAPHQL'},
    @{name='url';option='--url=https://synthetic.invalid'},
    @{name='permit';option='--permit=synthetic-refused'},
    @{name='mode';option='--mode=SWEEP'},
    @{name='roots';option='--roots=481'},
    @{name='page';option='--page-size=17'},
    @{name='limit';option='--limit=4097'},
    @{name='contract';option='--contract=SQL-20'}
)){
    $cases.Add(@{name=('refused-'+$refusal.name);args=@('scenario','--synthetic-analytic-lab',$refusal.option);expected=20;optin=$true;url=$url;main=$false})
}
$cases.Add(@{name='refused-no-optin';args=@('scenario','--synthetic-analytic-lab');expected=20;optin=$false;url=$url;main=$false})
$cases.Add(@{name='refused-foreign-host';args=@('scenario','--synthetic-analytic-lab');expected=20;optin=$true;url='jdbc:sqlserver://unapproved.invalid;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true';main=$false})
$cases.Add(@{name='refused-production';args=@('scenario','--synthetic-analytic-lab');expected=20;optin=$true;url='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA;integratedSecurity=true';main=$false})
$cases.Add(@{name='main-default-no-io';args=@();expected=0;optin=$false;url='synthetic-invalid-no-connection';main=$true})
if($CaseNames.Count -gt 0){
    if(@($CaseNames|Where-Object {$_ -notin $cases.name}).Count -gt 0){throw 'ANA_JAR_CASE_NOT_CLOSED'}
    $selected=@($cases|Where-Object {$_.name -in $CaseNames})
}else{$selected=@($cases)}
@{state='RESERVED_OUTCOME_UNKNOWN';target='localhost/ETL_SISTEMA_V2_SHADOW';cases=$selected.Count;secondsPerCase=300;totalSeconds=1200;heapMiB=512;rollbackOnly=$true;jarSha256=(Get-FileHash -LiteralPath $jar[0].FullName).Hash.ToLowerInvariant()}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $output 'reservation.json') -Encoding utf8
$results=[Collections.Generic.List[object]]::new()
$elapsed=[Diagnostics.Stopwatch]::StartNew()
foreach($case in $selected){
    if($elapsed.Elapsed.TotalSeconds -ge 1200){throw 'ANA_JAR_CAMPAIGN_BUDGET'}
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
    $info.WorkingDirectory=$root;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($arg in @('-Xmx512m',('-Djava.library.path='+(Join-Path $build 'native')))){$info.ArgumentList.Add($arg)}
    if($case.optin){$info.ArgumentList.Add('-Dshadow.local.integration.enabled=true');$info.ArgumentList.Add('-Dshadow.local.integration.profile.active=true')}
    $entry=if($case.main){'br.com.esl.etl.v2.bootstrap.Main'}else{'br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryMain'}
    foreach($arg in @('-cp',($jar[0].FullName+';'+(Join-Path $build 'lib/*')),$entry)+$case.args){$info.ArgumentList.Add($arg)}
    $info.Environment['V2_SHADOW_JDBC_URL']=$case.url
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    $caseTimer=[Diagnostics.Stopwatch]::StartNew()
    try{
        [void]$process.Start()
        @{pid=$process.Id;case=$case.name;state='RUNNING'}|ConvertTo-Json -Compress|Add-Content -LiteralPath (Join-Path $output 'processes.jsonl') -Encoding utf8
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $completed=$process.WaitForExit(300000)
        if(-not $completed){$process.Kill($true);$process.WaitForExit()}
        $outText=$stdout.GetAwaiter().GetResult();$errText=$stderr.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $output ($case.name+'.out.log')),$outText,[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $output ($case.name+'.err.log')),$errText,[Text.UTF8Encoding]::new($false))
        $expectedOutput=if($case.main){-not $outText.Contains('ANA_LAB_')}elseif($case.expected -eq 20){$outText.Contains('ANA_LAB_CONFIG_REJECTED')}else{$outText.Contains('ANA_LAB_ROLLBACK_ONLY')}
        if($case.name -like 'query-*'){$expectedOutput=$expectedOutput -and ($outText -match 'ANA_LAB_QUERY contract=SQL-\d{2} rows=[1-9][0-9]* columns=[1-9]')}
        if($case.expected -eq 10){$expectedOutput=$expectedOutput -and $outText.Contains('state=DEGRADED') -and $outText.Contains('contract=SQL-06 rows=4')}
        $observed=@{case=$case.name;expected=$case.expected;exit=$process.ExitCode;timedOut=(-not $completed);elapsedMs=$caseTimer.ElapsedMilliseconds;passed=($completed -and $process.ExitCode -eq $case.expected -and $expectedOutput)}
        $results.Add($observed)
        $observed|ConvertTo-Json -Compress|Add-Content -LiteralPath (Join-Path $output 'observations.jsonl') -Encoding utf8
        Write-Output ('ANA_JAR_CASE {0} exit={1} passed={2}' -f $case.name,$process.ExitCode,$observed.passed)
    }finally{$process.Dispose()}
}
& sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $output 'after.log')
if($LASTEXITCODE -ne 0){throw 'ANA_JAR_AFTER'}
$restored=[IO.File]::ReadAllText((Join-Path $output 'before.log')) -ceq [IO.File]::ReadAllText((Join-Path $output 'after.log'))
$passed=$restored -and @($results|Where-Object {-not $_.passed}).Count -eq 0
@{passed=$passed;rollbackConfirmed=$restored;cases=@($results);layer='PACKAGED_JAR_PROCESS_REAL_JDBC';remoteCalls=0;productionPublication=$false}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $output 'result.json') -Encoding utf8
if(-not $passed){throw 'ANA_JAR_VALIDATION_FAILED'}

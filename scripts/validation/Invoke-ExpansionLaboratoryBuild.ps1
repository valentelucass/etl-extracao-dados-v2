#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [ValidateSet('Compile','Directed','Physical','Verify','VerifyPhysical')][string]$Phase='Compile',
    [string]$Tests='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-expansao-20260912-01'
$build=Join-Path $round 'build'
$log=Join-Path $round ('logs/'+$Attempt+'.log')
if(Test-Path -LiteralPath $log){throw 'EXP_LOG_EXISTS'}
$initial=Get-Content -Raw -LiteralPath (Join-Path $round 'inventory-before.json')|ConvertFrom-Json
$before=@{};foreach($entry in $initial){$before[$entry.path]=$entry.sha256}
$own=[Collections.Generic.List[string]]::new()
$inputHashes=@{}
Push-Location -LiteralPath $root
try {
    $files=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*')
    foreach($relative in $files){
        $inputFile=Join-Path $root $relative
        $dest=Join-Path $build $relative
        $canonical=$relative.Replace([char]92,[char]47)
        if($relative.EndsWith('.java') -and (-not $before.ContainsKey($canonical) -or
            (Get-FileHash -LiteralPath $inputFile).Hash.ToLowerInvariant() -cne $before[$canonical])){
            $own.Add($relative);$inputHashes[$relative]=(Get-FileHash -LiteralPath $inputFile).Hash
        }
        if((Test-Path -LiteralPath $dest) -and (Get-Item -LiteralPath $dest).LastWriteTimeUtc -eq (Get-Item -LiteralPath $inputFile).LastWriteTimeUtc){continue}
        [void][IO.Directory]::CreateDirectory((Split-Path -Parent $dest))
        Copy-Item -LiteralPath $inputFile -Destination $dest
    }
}finally{Pop-Location}
$physical=$Phase -in @('Physical','VerifyPhysical')
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53390,N'WRONG_TARGET',1; SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;"
if($physical){
    & sqlcmd -S localhost -C -E -d master -l 5 -t 10 -b -Q "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53390,N'EXACT_SHADOW_MISSING',1;" *> (Join-Path $round ($Attempt+'-master.log'))
    if($LASTEXITCODE -ne 0){throw 'EXP_MASTER_PREFLIGHT'}
    & sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $round ($Attempt+'-before.log'))
    if($LASTEXITCODE -ne 0){throw 'EXP_SHADOW_PREFLIGHT'}
}
@{state='RESERVED_OUTCOME_UNKNOWN';phase=$Phase;attempt=$Attempt;budgetSeconds=900;java=17;heapMiB=512;ddl=$false;syntheticRollbackOnly=$physical}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-action.json')) -Encoding utf8
$previousJava=$env:JAVA_HOME;$previousOptions=$env:JAVA_TOOL_OPTIONS;$previousUrl=$env:V2_SHADOW_JDBC_URL
try{
    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot';$env:JAVA_TOOL_OPTIONS='-Xmx512m'
    $arguments=@('--offline','--batch-mode','--no-transfer-progress','spotless:apply')
    switch($Phase){
        'Compile' {$arguments+='test-compile'}
        'Directed' {$arguments+='test';if($Tests){$arguments+=('-Dtest='+$Tests)}}
        'Verify' {$arguments+=@('verify','-Dv2.measurement.receipt=true')}
        {$_ -in @('Physical','VerifyPhysical')} {
            $env:V2_SHADOW_JDBC_URL='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=30000'
            if($Phase -eq 'VerifyPhysical'){$arguments+=@('verify','-Dv2.measurement.receipt=true')}
            else{$arguments+=@('process-test-classes','failsafe:integration-test','failsafe:verify')}
            $arguments+=@('-Pshadow-local-integration','-Dshadow.local.integration.enabled=true')
            if($Tests){$arguments+=('-Dit.test='+$Tests)}
        }
    }
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName='C:\Program Files (x86)\apache-maven-3.9.14\bin\mvn.cmd'
    $info.WorkingDirectory=$build;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try{
        [void]$process.Start();$process.Id|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-process.txt'))
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $finished=$process.WaitForExit(900000)
        if(-not $finished){$process.Kill($true);$process.WaitForExit();$code=124}else{$code=$process.ExitCode}
        [IO.File]::WriteAllText($log,$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult(),[Text.UTF8Encoding]::new($false))
    }finally{$process.Dispose()}
    foreach($relative in $own){
        if((Get-FileHash -LiteralPath (Join-Path $root $relative)).Hash -ceq $inputHashes[$relative]){
            Copy-Item -LiteralPath (Join-Path $build $relative) -Destination (Join-Path $root $relative)
        }
    }
    foreach($kind in @('surefire','failsafe')){
        $reports=Join-Path $build ('target/'+$kind+'-reports')
        if(Test-Path -LiteralPath $reports){Copy-Item -LiteralPath $reports -Destination (Join-Path $round ($Attempt+'-'+$kind)) -Recurse}
    }
    if($physical){
        & sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $round ($Attempt+'-after.log'))
        if($LASTEXITCODE -ne 0){throw 'EXP_AFTER_QUERY'}
        if((Get-Content -Raw (Join-Path $round ($Attempt+'-before.log'))) -cne (Get-Content -Raw (Join-Path $round ($Attempt+'-after.log')))){throw 'EXP_AGGREGATE_DRIFT'}
    }
    @{state='OBSERVED';attempt=$Attempt;phase=$Phase;exit=$code;arguments=$arguments}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-exit.json')) -Encoding utf8
    Get-Content -LiteralPath $log -Tail 24
    exit $code
}finally{$env:JAVA_HOME=$previousJava;$env:JAVA_TOOL_OPTIONS=$previousOptions;$env:V2_SHADOW_JDBC_URL=$previousUrl}

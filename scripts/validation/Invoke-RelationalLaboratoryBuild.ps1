#Requires -Version 7.5
param([Parameter(Mandatory)][string]$Attempt,[ValidateSet('Compile','Directed','Physical','Verify','VerifyPhysical')][string]$Phase='Compile',[string]$Tests='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
if($Attempt -cnotmatch '^[a-z0-9-]{1,64}$'){throw 'REL_BUILD_ATTEMPT'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-relacional-20260911-01'
$build=Join-Path $round 'build'
$log=Join-Path $round ('logs/'+$Attempt+'.log')
if(Test-Path -LiteralPath $log){throw 'REL_BUILD_LOG_EXISTS'}
Push-Location -LiteralPath $root
try {
    $files=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*')
    foreach($relative in $files){
        $inputFile=Join-Path $root $relative
        $dest=Join-Path $build $relative
        if((Test-Path -LiteralPath $dest) -and (Get-Item -LiteralPath $dest).LastWriteTimeUtc -eq (Get-Item -LiteralPath $inputFile).LastWriteTimeUtc){continue}
        New-Item -ItemType Directory -Path (Split-Path -Parent $dest) -Force|Out-Null
        Copy-Item -LiteralPath $inputFile -Destination $dest
    }
} finally {Pop-Location}
$physical=$Phase -in @('Physical','VerifyPhysical')
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53290,N'WRONG_TARGET',1; SELECT COUNT_BIG(*) FROM ctl.execution_attempt; SELECT COUNT_BIG(*) FROM ctl.relational_lab_run; SELECT COUNT_BIG(*) FROM core.relational_lab_root; SELECT COUNT_BIG(*) FROM ctl.relational_lab_backlog;"
if($physical){
    & sqlcmd -S localhost -C -E -d master -l 5 -t 10 -b -Q "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53290,N'EXACT_SHADOW_MISSING',1;" *> (Join-Path $round ($Attempt+'-master.log'))
    if($LASTEXITCODE -ne 0){throw 'REL_BUILD_MASTER_PREFLIGHT'}
    & sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $round ($Attempt+'-before.log'))
    if($LASTEXITCODE -ne 0){throw 'REL_BUILD_SHADOW_PREFLIGHT'}
}
@{state='RESERVED_OUTCOME_UNKNOWN';phase=$Phase;attempt=$Attempt;target=$(if($physical){'localhost/ETL_SISTEMA_V2_SHADOW'}else{'OFFLINE'});budgetSeconds=900;java=17;heapMiB=512;ddl=$false;syntheticRollbackOnly=$physical}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-action.json')) -Encoding utf8
$previousJava=$env:JAVA_HOME
$previousOptions=$env:JAVA_TOOL_OPTIONS
$previousUrl=$env:V2_SHADOW_JDBC_URL
try {
    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
    $env:JAVA_TOOL_OPTIONS='-Xmx512m'
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
    Push-Location -LiteralPath $build
    try {& 'C:\Program Files (x86)\apache-maven-3.9.14\bin\mvn.cmd' @arguments *> $log;$code=$LASTEXITCODE}
    finally {Pop-Location}
    # Only this round's new Java files receive formatter output; previous sources remain untouched.
    $own=@(Get-ChildItem -LiteralPath (Join-Path $build 'src') -Recurse -File -Filter '*Relational*.java')
    foreach($file in $own){$relative=[IO.Path]::GetRelativePath($build,$file.FullName);Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $root $relative)}
    foreach($relative in @('src/test/java/br/com/esl/etl/v2/contratos/medicao/ManagedPageGauge.java','src/test/java/br/com/esl/etl/v2/plataforma/persistencia/sombra/SchemaFoundationSqlContractTest.java','src/test/java/br/com/esl/etl/v2/arquitetura/ArchitectureRulesTest.java')){
        Copy-Item -LiteralPath (Join-Path $build $relative) -Destination (Join-Path $root $relative)
    }
    foreach($kind in @('surefire','failsafe')){
        $reports=Join-Path $build ('target/'+$kind+'-reports')
        if(Test-Path -LiteralPath $reports){Copy-Item -LiteralPath $reports -Destination (Join-Path $round ($Attempt+'-'+$kind)) -Recurse}
    }
    if($physical){
        & sqlcmd -S localhost -C -E -d ETL_SISTEMA_V2_SHADOW -l 5 -t 10 -b -h -1 -W -Q $query *> (Join-Path $round ($Attempt+'-after.log'))
        if($LASTEXITCODE -ne 0){throw 'REL_BUILD_AFTER_QUERY'}
    }
    @{state='OBSERVED';attempt=$Attempt;phase=$Phase;exit=$code;arguments=$arguments}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $round ($Attempt+'-exit.json')) -Encoding utf8
    Get-Content -LiteralPath $log -Tail 30
    exit $code
} finally {
    $env:JAVA_HOME=$previousJava;$env:JAVA_TOOL_OPTIONS=$previousOptions;$env:V2_SHADOW_JDBC_URL=$previousUrl
}

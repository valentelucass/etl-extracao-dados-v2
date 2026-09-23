#Requires -Version 7.0
param([ValidateSet('Focused','Full')][string]$Mode='Full')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$temporary=Join-Path $root '.bloco54-v6.pom.xml'
$canonical=[IO.File]::ReadAllText((Join-Path $root 'pom.xml'))
$replacement='<build><directory>${project.basedir}/target/bloco54-build-v6</directory>'
$isolated=$canonical.Replace('<build>',$replacement)
if($isolated -ceq $canonical -or $isolated.Replace($replacement,'<build>') -cne $canonical){throw 'POM_ISOLATION_NOT_EQUIVALENT'}
if(Test-Path -LiteralPath $temporary){throw 'EXISTING_TEMPORARY_POM_PRESERVED'}
[IO.File]::WriteAllText($temporary,$isolated,[Text.UTF8Encoding]::new($false))
$expected=(Get-FileHash -LiteralPath $temporary).Hash
$previousJavaPath=$env:JAVA_HOME
$log=Join-Path $root ('target/bloco54/completion-authorized-20260908/verify-'+$Mode+'-'+[datetime]::UtcNow.ToString('yyyyMMddTHHmmss')+'.log')
try {
    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
    Push-Location $root
    try {
        if($Mode -ceq 'Focused'){
            & ./mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco54-v6.pom.xml spotless:apply test '-Dtest=RuntimeTemporalCoordinatorTest,AdministeredArtifactManifestTest,RuntimeCancellationControlTest,AdministeredArtifactVerifierTest,RuntimeCompositionRootTest,RuntimeTemporalBindingTest,RuntimeRequiredDriftAlertTest,RuntimeOperationalExecutionTest,RuntimeLaboratoryContractTest,MainTest' *> $log
        }else{& ./mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco54-v6.pom.xml spotless:apply verify *> $log}
        $code=$LASTEXITCODE
    }finally{Pop-Location}
    if($code -ne 0){throw ('V6_VERIFY_FAILED_'+$Mode)}
    'B54_V6_VERIFY_PASS mode='+$Mode+' log='+$log
}finally{
    $env:JAVA_HOME=$previousJavaPath
    if([IO.Path]::GetFullPath($temporary) -cne (Join-Path $root '.bloco54-v6.pom.xml') -or (Get-FileHash -LiteralPath $temporary).Hash -cne $expected){throw 'TEMPORARY_POM_CHANGED_PRESERVED'}
    Remove-Item -LiteralPath $temporary
}

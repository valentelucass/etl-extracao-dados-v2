#Requires -Version 7.0
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$temporary=Join-Path $root '.bloco54.pom.xml'
$canonical=[IO.File]::ReadAllText((Join-Path $root 'pom.xml'))
$replacement='<build><directory>${project.basedir}/target/bloco54-build</directory>'
$isolated=$canonical.Replace('<build>',$replacement)
if($isolated -ceq $canonical -or $isolated.Replace($replacement,'<build>') -cne $canonical){throw 'POM_ISOLATION_NOT_EQUIVALENT'}
if(Test-Path -LiteralPath $temporary){throw 'EXISTING_TEMPORARY_POM_PRESERVED'}
$encoding=[Text.UTF8Encoding]::new($false,$true)
[IO.File]::WriteAllText($temporary,$isolated,$encoding)
$expected=(Get-FileHash -LiteralPath $temporary).Hash
$previousJavaPath=$env:JAVA_HOME
$log=Join-Path $root ('target/bloco54/verify-helper-'+[datetime]::UtcNow.ToString('yyyyMMddTHHmmss')+'.log')
try {
    $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
    Push-Location $root
    try {& .\mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco54.pom.xml verify *> $log;$code=$LASTEXITCODE}
    finally {Pop-Location}
    if($code -ne 0){throw 'OFFLINE_VERIFY_FAILED_SEE_ISOLATED_LOG'}
    'B54_JAVA17_OFFLINE_VERIFY_PASS'
} finally {
    $env:JAVA_HOME=$previousJavaPath
    $absolute=[IO.Path]::GetFullPath($temporary)
    if($absolute -cne (Join-Path $root '.bloco54.pom.xml')){throw 'TEMPORARY_POM_TARGET_REJECTED'}
    if((Get-FileHash -LiteralPath $absolute).Hash -cne $expected){throw 'TEMPORARY_POM_CHANGED_PRESERVED'}
    Remove-Item -LiteralPath $absolute
}

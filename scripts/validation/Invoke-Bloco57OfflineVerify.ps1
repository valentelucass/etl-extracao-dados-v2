#Requires -Version 7.5
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$temporary=Join-Path $root '.bloco57-verify.pom.xml'
if(Test-Path -LiteralPath $temporary){throw 'B57_EXISTING_TEMPORARY_POM_PRESERVED'}
$canonical=[IO.File]::ReadAllText((Join-Path $root 'pom.xml'))
$replacement='<build><directory>${project.basedir}/target/bloco57-local/build-final</directory>'
$isolated=$canonical.Replace('<build>',$replacement)
if($isolated -ceq $canonical -or $isolated.Replace($replacement,'<build>') -cne $canonical){throw 'B57_POM_NOT_EQUIVALENT'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
$evidence=Join-Path $root ('target/bloco57-local/verify-'+[guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($evidence)|Out-Null
[IO.File]::WriteAllText((Join-Path $evidence 'isolated.pom.xml'),$isolated,$utf8)
[IO.File]::WriteAllText($temporary,$isolated,$utf8)
$expected=(Get-FileHash -LiteralPath $temporary).Hash
$previousJava=$env:JAVA_HOME
$previousJavaOptions=$env:JAVA_TOOL_OPTIONS
$code=-1
try {
 $env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
 $env:JAVA_TOOL_OPTIONS='-Xmx512m'
 Push-Location -LiteralPath $root
 try {& .\mvnw.cmd --offline --batch-mode --no-transfer-progress -f .bloco57-verify.pom.xml verify *> (Join-Path $evidence 'verify.log');$code=$LASTEXITCODE}
 finally {Pop-Location}
 [IO.File]::WriteAllText((Join-Path $evidence 'exit.json'),(@{exit=$code;expectedExit=0;java=17;maximumHeapMiB=512;offline=$true;output='target/bloco57-local/build-final';noClean=$true}|ConvertTo-Json)+"`n",$utf8)
 if($code -ne 0){throw ('B57_OFFLINE_VERIFY_FAILED_'+$evidence)}
 'B57_OFFLINE_VERIFY_PASS_'+$evidence
} finally {
 $env:JAVA_HOME=$previousJava
 $env:JAVA_TOOL_OPTIONS=$previousJavaOptions
 if([IO.Path]::GetFullPath($temporary) -cne (Join-Path $root '.bloco57-verify.pom.xml')){throw 'B57_TEMPORARY_TARGET_REJECTED'}
 if((Get-FileHash -LiteralPath $temporary).Hash -cne $expected){throw 'B57_TEMPORARY_POM_CHANGED_PRESERVED'}
 Remove-Item -LiteralPath $temporary
}

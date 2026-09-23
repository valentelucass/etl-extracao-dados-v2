#Requires -Version 7.5
<#
.SYNOPSIS
Preflight offline por padrão; -Execute seleciona a aquisição futura 6906 explicitamente.
Não carrega .env. A credencial provisionada usa B57_COT_BEARER_TOKEN somente no processo.
#>
param(
 [Parameter(Mandatory)][string]$InputPath,
 [switch]$Execute,
 [string]$JavaHome='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$inputFile=[IO.Path]::GetFullPath($InputPath)
if($inputFile -match '[\x00-\x1f\x7f]' -or -not (Test-Path -LiteralPath $inputFile -PathType Leaf)){throw 'COT_SOURCE_INPUT_MISSING'}
$inputNode=Get-Item -LiteralPath $inputFile
while($null -ne $inputNode){
 if(($inputNode.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'COT_SOURCE_INPUT_REPARSE'}
 $inputNode=if($inputNode -is [IO.DirectoryInfo]){$inputNode.Parent}else{$inputNode.Directory}
}
if((Get-Item -LiteralPath $inputFile).Length -gt 65536){throw 'COT_SOURCE_INPUT_BOUND'}
if(-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe') -PathType Leaf)){throw 'COT_SOURCE_JAVA17_MISSING'}
$mode=if($Execute){'execute'}else{'preflight'}
$id=[guid]::NewGuid().ToString('N')
$relative='target/bloco57-source-command/command-'+$id
$evidence=Join-Path $root $relative
$temporary=Join-Path $root ('.bloco57-source-'+$id+'.pom.xml')
if(Test-Path -LiteralPath $temporary){throw 'COT_SOURCE_TEMPORARY_EXISTS'}
$canonical=[IO.File]::ReadAllText((Join-Path $root 'pom.xml'))
$replacement='<build><directory>${project.basedir}/'+$relative+'/build</directory>'
$isolated=$canonical.Replace('<build>',$replacement)
if($isolated -ceq $canonical -or $isolated.Replace($replacement,'<build>') -cne $canonical){throw 'COT_SOURCE_POM_NOT_EQUIVALENT'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
[IO.Directory]::CreateDirectory($evidence)|Out-Null
[IO.File]::WriteAllText((Join-Path $evidence 'isolated.pom.xml'),$isolated,$utf8)
[IO.File]::WriteAllText($temporary,$isolated,$utf8)
$expected=(Get-FileHash -LiteralPath $temporary).Hash
$previousJava=$env:JAVA_HOME
$previousOptions=$env:JAVA_TOOL_OPTIONS
try {
 $env:JAVA_HOME=$JavaHome
 $env:JAVA_TOOL_OPTIONS='-Xmx512m'
 Push-Location -LiteralPath $root
 try {
  & .\mvnw.cmd --offline --batch-mode --no-transfer-progress -f $temporary '-Dtest=CotacoesSourceCommandTest' ('-Dbloco57.cotacoes.command='+$mode) ('-Dbloco57.cotacoes.input='+$inputFile) test *> (Join-Path $evidence 'command.log')
  $code=$LASTEXITCODE
 } finally {Pop-Location}
 [IO.File]::WriteAllText((Join-Path $evidence 'exit.json'),(@{exit=$code;mode=$mode;java=17;heapMiB=512;offlineBuild=$true;sourceSelected=[bool]$Execute;noClean=$true}|ConvertTo-Json),$utf8)
 if($code -ne 0){throw 'COT_SOURCE_COMMAND_REJECTED_SEE_SANITIZED_LOG_AND_EXISTING_RESERVATIONS'}
 if($Execute){'COT_SOURCE_BOUNDED_SAMPLE_MATCH_NOT_CANONICAL_ACCEPTANCE'}else{'COT_SOURCE_PREFLIGHT_PASS_NO_NETWORK_NO_RESERVATION'}
} finally {
 $env:JAVA_HOME=$previousJava
 $env:JAVA_TOOL_OPTIONS=$previousOptions
 if([IO.Path]::GetFullPath($temporary) -cne (Join-Path $root ('.bloco57-source-'+$id+'.pom.xml'))){throw 'COT_SOURCE_TEMPORARY_TARGET'}
 if((Get-FileHash -LiteralPath $temporary).Hash -cne $expected){throw 'COT_SOURCE_TEMPORARY_CHANGED_PRESERVED'}
 Remove-Item -LiteralPath $temporary
}

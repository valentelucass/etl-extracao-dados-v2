#Requires -Version 7.5
param(
 [switch]$ValidateOnly,
 [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01',
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$BuildAttempt,
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,48}$')][string]$OutputName
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root ('target/'+$RoundName)
Import-Module (Join-Path $root 'scripts/validation/QualificationPackage.psm1') -Force
Assert-QualificationPath $round
$attempt=Join-Path $round $BuildAttempt
if($ValidateOnly){return [pscustomobject]@{round=$round;build=(Join-Path $attempt 'build');physical=$false}}
$build=Join-Path $attempt 'build'
$result=Get-Content -Raw -LiteralPath (Join-Path $attempt 'result.json')|ConvertFrom-Json
if($result.exit -ne 0 -or $result.timedOut){throw 'EXAMPLES_BUILD_NOT_PASSED'}
$output=Join-Path $round $OutputName
$proof=Join-Path $round ($OutputName+'-author')
if((Test-Path -LiteralPath $output) -or (Test-Path -LiteralPath $proof)){throw 'EXAMPLES_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($proof)
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Save([string]$name,$value){[IO.File]::WriteAllText((Join-Path $proof $name),($value|ConvertTo-Json -Depth 16),$utf8)}
$jar=Join-Path $build 'target/etl-dataexport-v2.jar'
$jarHash=(Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant()
Save 'reservation.json' @{state='RESERVED';build=$BuildAttempt;output=$OutputName;jarSha256=$jarHash;
 sourceCalls=0;sql=$false;heapMiB=512;initialHeapMiB=64;budgetSeconds=600;purpose='author independent synthetic examples before SQL; runtime classes loaded from actual JAR'}
$info=[Diagnostics.ProcessStartInfo]::new('C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe')
$info.WorkingDirectory=$build;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){$null=$info.Environment.Remove($name)}
$classpath=(Join-Path $build 'target/test-classes')+';'+$jar+';'+(Join-Path $build 'target/lib/*')
foreach($arg in @('-Xms64m','-Xmx512m','-Dfile.encoding=UTF-8','-cp',$classpath,'br.com.esl.etl.v2.bootstrap.IntegralCampaignPackageFixtures',$output)){$info.ArgumentList.Add($arg)}
$process=[Diagnostics.Process]::Start($info);$watch=[Diagnostics.Stopwatch]::StartNew()
try{
 Save 'process.json' @{pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');classpath=$classpath;sql=$false}
 $outFile=[IO.File]::Create((Join-Path $proof 'stdout.log'));$errFile=[IO.File]::Create((Join-Path $proof 'stderr.log'))
 try{
  $out=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$err=$process.StandardError.BaseStream.CopyToAsync($errFile)
  while(-not $process.WaitForExit(500)){
   if($watch.Elapsed.TotalSeconds -gt 600 -or $outFile.Length -gt 1048576 -or $errFile.Length -gt 1048576){$process.Kill($true);$process.WaitForExit();throw 'EXAMPLES_PROCESS_BOUND'}
  }
  $null=$out.GetAwaiter().GetResult();$null=$err.GetAwaiter().GetResult()
 }finally{$outFile.Dispose();$errFile.Dispose()}
 $exitCode=$process.ExitCode
}finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
Save 'process-result.json' @{exit=$exitCode;elapsedMilliseconds=$watch.ElapsedMilliseconds}
if($exitCode -ne 0){throw 'EXAMPLES_AUTHOR_FAILURE'}
$index=Get-Content -Raw -LiteralPath (Join-Path $output 'index.json')|ConvertFrom-Json
if($index.runtimeSha256 -cne $jarHash -or ($index.cases.id -join ',') -cne 'sequence-a,sequence-b'){throw 'EXAMPLES_BINDING'}
Save 'result.json' @{passed=$true;jarSha256=$jarHash;indexSha256=(Get-FileHash -LiteralPath (Join-Path $output 'index.json')).Hash.ToLowerInvariant();cases=@($index.cases|ForEach-Object {@{id=$_.id;roots=$_.roots;files=$_.members.Count}})}
Get-Content -LiteralPath (Join-Path $proof 'result.json')

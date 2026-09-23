#Requires -Version 7.5
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$SourceDirectory = '',
    [string]$JavaHome = '',
    [string]$MavenHome = '',
    [ValidateRange(1,180)][int]$MaximumSeconds = 180
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'LocalStaticAnalysis.psm1') -Force
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8 = [Text.UTF8Encoding]::new($false,$true)
$target = Join-Path $root 'target'
$out = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $out.StartsWith($target + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $out)) { throw 'STATIC_NEW_OUTPUT_UNDER_TARGET_REQUIRED' }
Assert-StaticAnalysisPath $target
$ancestor = [IO.Path]::GetDirectoryName($out)
while (-not (Test-Path -LiteralPath $ancestor)) { $ancestor=[IO.Path]::GetDirectoryName($ancestor) }
Assert-StaticAnalysisPath $ancestor
[IO.Directory]::CreateDirectory($out) | Out-Null
function Save-Json([string]$Name,$Value) {
    [IO.File]::WriteAllText((Join-Path $out $Name),($Value | ConvertTo-Json -Depth 20),$utf8)
}
$process = $null
$exitCode = 1
$timer = [Diagnostics.Stopwatch]::StartNew()
Save-Json 'request.json' ([ordered]@{
    state='RESERVED'; mode='OFFLINE_LOCAL_STATIC_ANALYSIS'; maximumAttempts=1
    maximumSeconds=$MaximumSeconds; network=$false; sql=$false
    recovery='Preserve attempt; terminate only owned Java tree on timeout; no automatic retry/download.'
})
try {
    $defaultSource=[IO.Path]::GetFullPath((Join-Path $root 'src/main/java'))
    if (-not $SourceDirectory) { $SourceDirectory=$defaultSource }
    $source=[IO.Path]::GetFullPath($SourceDirectory).TrimEnd([char]92,[char]47)
    if (-not $source.Equals($defaultSource,[StringComparison]::OrdinalIgnoreCase) -and
        -not $source.StartsWith($target + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'STATIC_SOURCE_SCOPE'
    }
    Assert-StaticAnalysisPath $source
    $inventory=@(Get-ChildItem -LiteralPath $source -Recurse -File -Filter '*.java' | ForEach-Object {
        Assert-StaticAnalysisPath $_.FullName
        [ordered]@{path=[IO.Path]::GetRelativePath($source,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant()}
    })
    if ($inventory.Count -lt 1 -or $inventory.Count -gt 10000) { throw 'STATIC_SOURCE_COUNT' }
    Save-Json 'source-inventory.json' $inventory
    $ruleset=Join-Path $PSScriptRoot 'pmd-security.xml'
    $rules=Get-StaticAnalysisRules $ruleset
    [IO.File]::Copy($ruleset,(Join-Path $out 'ruleset.xml'),$false)
    $cache=Join-Path $env:USERPROFILE '.m2/repository'
    $pins=@{
        'net/sourceforge/pmd/pmd-core/7.17.0/pmd-core-7.17.0.jar'='73089de9d6bfe41e2c4e5780f0465c5e647a048c486f942070fbccceec666a52'
        'net/sourceforge/pmd/pmd-java/7.17.0/pmd-java-7.17.0.jar'='1fa1c9651562ed3e575c84c6c91c19f05165e6aa06712a89761c787d743707eb'
        'org/apache/maven/plugins/maven-pmd-plugin/3.28.0/maven-pmd-plugin-3.28.0.jar'='d1bb9064f2e5645a1470a2ade3444ef0504bd28b7c3a4c96e17ca2c4eeb4d892'
    }
    foreach ($entry in $pins.GetEnumerator()) {
        $artifact=Join-Path $cache $entry.Key
        if (-not (Test-Path -LiteralPath $artifact -PathType Leaf) -or
            (Get-FileHash -LiteralPath $artifact).Hash.ToLowerInvariant() -cne $entry.Value) { throw 'STATIC_TOOL_CACHE_MISSING_OR_DRIFT' }
    }
    if (-not $JavaHome) { $JavaHome=Split-Path (Split-Path (Get-Command java.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source -Parent) -Parent }
    if (-not $MavenHome) { $MavenHome=Split-Path (Split-Path (Get-Command mvn.cmd -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source -Parent) -Parent }
    if ([IO.File]::ReadAllText((Join-Path $JavaHome 'release')) -cnotmatch '(?m)^JAVA_VERSION="17(?:[.][^"]+)?"') { throw 'STATIC_JAVA17_REQUIRED' }
    if (-not (Test-Path -LiteralPath (Join-Path $MavenHome 'lib/maven-core-3.9.14.jar'))) { throw 'STATIC_MAVEN_VERSION' }
    $settings=Join-Path $out 'settings-empty.xml'
    [IO.File]::WriteAllText($settings,'<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"><offline>true</offline></settings>',$utf8)
    function Xml([string]$Value) { [Security.SecurityElement]::Escape($Value) }
    $sourceXml=Xml $source; $buildXml=Xml (Join-Path $out 'build'); $reportsXml=Xml (Join-Path $out 'reports')
    $benchmarkXml=Xml (Join-Path $out 'benchmark.txt'); $rulesXml=Xml (Join-Path $out 'ruleset.xml')
    $pom=Join-Path $out 'pom.xml'
    $pomText=@"
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion><groupId>local.static.analysis</groupId><artifactId>etl-security</artifactId><version>1</version>
  <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding><project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding><maven.compiler.release>17</maven.compiler.release></properties>
  <dependencies>
    <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.18.11</version></dependency>
    <dependency><groupId>com.fasterxml.jackson.datatype</groupId><artifactId>jackson-datatype-jsr310</artifactId><version>2.18.11</version></dependency>
    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.16</version></dependency>
    <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.16</version><scope>runtime</scope></dependency>
    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>12.8.2.jre11</version></dependency>
  </dependencies>
  <build><sourceDirectory>$sourceXml</sourceDirectory><directory>$buildXml</directory><outputDirectory>$buildXml/classes</outputDirectory><testOutputDirectory>$buildXml/test-classes</testOutputDirectory><plugins><plugin>
    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-pmd-plugin</artifactId><version>3.28.0</version>
    <configuration><targetJdk>17</targetJdk><includeTests>false</includeTests><linkXRef>false</linkXRef><format>xml</format><analysisCache>false</analysisCache><skipEmptyReport>false</skipEmptyReport><renderSuppressedViolations>true</renderSuppressedViolations><benchmark>true</benchmark><benchmarkOutputFilename>$benchmarkXml</benchmarkOutputFilename><outputDirectory>$reportsXml</outputDirectory><targetDirectory>$buildXml</targetDirectory><rulesetsTargetDirectory>$buildXml/rulesets</rulesetsTargetDirectory><rulesets><ruleset>$rulesXml</ruleset></rulesets></configuration>
  </plugin></plugins></build><reporting><outputDirectory>$reportsXml</outputDirectory></reporting>
</project>
"@
    [IO.File]::WriteAllText($pom,$pomText,$utf8)
    $info=[Diagnostics.ProcessStartInfo]::new((Join-Path $JavaHome 'bin/java.exe'))
    $info.WorkingDirectory=$out; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$utf8; $info.StandardErrorEncoding=$utf8
    foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH','MAVEN_OPTS','MAVEN_ARGS','V2_SHADOW_JDBC_URL')) { $null=$info.Environment.Remove($name) }
    $arguments=@('-Xmx512m','-Dfile.encoding=UTF-8',('-Dclassworlds.conf='+(Join-Path $MavenHome 'bin/m2.conf')),('-Dmaven.home='+$MavenHome),('-Dmaven.multiModuleProjectDirectory='+$out),('-Dmaven.repo.local='+$cache),'-Djansi.force=false','-classpath',(Join-Path $MavenHome 'boot/plexus-classworlds-2.9.0.jar'),'org.codehaus.plexus.classworlds.launcher.Launcher','--offline','--batch-mode','--no-transfer-progress','--settings',$settings,'--global-settings',$settings,'-f',$pom,'org.apache.maven.plugins:maven-pmd-plugin:3.28.0:pmd')
    foreach ($argument in $arguments) { $info.ArgumentList.Add($argument) }
    Save-Json 'command.json' ([ordered]@{arguments=$arguments;sourceDirectory=$source;rules=$rules;sourceFiles=$inventory.Count;rulesetSha256=(Get-FileHash -LiteralPath $ruleset).Hash.ToLowerInvariant()})
    $process=[Diagnostics.Process]::Start($info)
    Save-Json 'process.json' ([ordered]@{id=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('o');owned=$true})
    $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
    $timedOut=-not $process.WaitForExit($MaximumSeconds * 1000)
    if ($timedOut) { $process.Kill($true); $process.WaitForExit() }
    $output=$stdout.GetAwaiter().GetResult(); $errorOutput=$stderr.GetAwaiter().GetResult()
    [IO.File]::WriteAllText((Join-Path $out 'stdout.log'),$output,$utf8)
    [IO.File]::WriteAllText((Join-Path $out 'stderr.log'),$errorOutput,$utf8)
    Save-Json 'execution.json' ([ordered]@{exit=$process.ExitCode;timedOut=$timedOut;elapsedSeconds=$timer.Elapsed.TotalSeconds;processExited=$process.HasExited})
    if ($timedOut) { throw 'STATIC_TIMEOUT' }
    if ($process.ExitCode -ne 0) { throw 'STATIC_MAVEN_FAILED' }
    if ($output.Length + $errorOutput.Length -gt 2097152) { throw 'STATIC_LOG_LIMIT' }
    foreach ($entry in $inventory) {
        if ((Get-FileHash -LiteralPath (Join-Path $source $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256) { throw 'STATIC_SOURCE_DRIFT' }
    }
    if (@(Get-ChildItem -LiteralPath $source -Recurse -File -Filter '*.java').Count -ne $inventory.Count) { throw 'STATIC_SOURCE_DRIFT' }
    $result=Read-LocalStaticAnalysisResult -Report (Join-Path $out 'build/pmd.xml') -Benchmark (Join-Path $out 'benchmark.txt') -SourceDirectory $source -ExpectedSourceCount $inventory.Count -RuleNames $rules
    $result.offline=$true; $result.network=$false; $result.sql=$false; $result.ownedProcessesRemaining=0
    $result.elapsedSeconds=$timer.Elapsed.TotalSeconds
    Save-Json 'result.json' $result
    $exitCode=if ($result.findingCount) { 1 } else { 0 }
    Write-Output ($result.state + ' sources=' + $result.sourceFiles + ' findings=' + $result.findingCount)
} catch {
    $reason=if ($_.Exception.Message -cmatch '^STATIC_[A-Z0-9_]+$') { $_.Exception.Message } else { 'STATIC_EXECUTION_ERROR' }
    Save-Json 'result.json' ([ordered]@{state='FAILED';reason=$reason;errorKind=$_.Exception.GetType().FullName;elapsedSeconds=$timer.Elapsed.TotalSeconds;nominalSecurityAcceptance=$false;releaseAcceptance=$false;offline=$true;network=$false;sql=$false})
    Write-Output $reason
} finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
    }
}
exit $exitCode

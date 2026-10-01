#Requires -Version 7.5
param([Parameter(Mandatory)][ValidateSet('inspect','plan','run','status','resume','compare','config-validate','dry-run')][string]$Command,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256,
    [string]$Campaign='', [string]$Control='', [string]$Configuration='')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$utf8=[Text.UTF8Encoding]::new($false,$true)
[Console]::OutputEncoding=$utf8
$OutputEncoding=$utf8
# This entry lives at the root of an extracted, verified payload. It never searches a workspace.
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$null=Test-QualificationPackage -Directory $PSScriptRoot -ManifestSha256 $ManifestSha256
$java=(Get-Command java.exe -CommandType Application -ErrorAction Stop|Select-Object -First 1).Source
$jar=Join-Path $PSScriptRoot 'etl-dataexport-v2.jar'
$offline=$Command -in @('config-validate','dry-run')
if($offline){
    if($Campaign -or $Control -or $Configuration){throw 'QUAL_OFFLINE_SMOKE_INPUTS'}
    $safeConfig=Join-Path $PSScriptRoot 'config/application.example.properties'
    $arguments=@('-Xmx512m','-Dfile.encoding=UTF-8','-cp',$jar,'br.com.esl.etl.v2.bootstrap.Main')
    if($Command -ceq 'config-validate'){$arguments+=@('config','validate')}
    else{$arguments+='dry-run'}
    $arguments+=@('--config',$safeConfig)
}else{
    $lock=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'dependencies.json'))) 65536
    $jdbc=@($lock.dependencies|Where-Object {$_.group -ceq 'com.microsoft.sqlserver' -and $_.name -ceq 'mssql-jdbc' -and $_.type -ceq 'jar'})
    $auth=@($lock.dependencies|Where-Object {$_.group -ceq 'com.microsoft.sqlserver' -and $_.name -ceq 'mssql-jdbc_auth' -and $_.type -ceq 'dll'})
    if($jdbc.Count -ne 1 -or $auth.Count -ne 1 -or
       $jdbc[0].version -cne '12.8.2.jre11' -or $auth[0].version -cne '12.8.2.x64' -or
       $jdbc[0].file -cne 'mssql-jdbc-12.8.2.jre11.jar' -or
       $auth[0].file -cne 'mssql-jdbc_auth-12.8.2.x64.dll'){
        throw 'QUAL_PHYSICAL_AUTH_PAIR_REQUIRED'
    }
    # The supervisor itself performs the master preflight before spawning a worker.
    $classpath=$jar+[IO.Path]::PathSeparator+(Join-Path $PSScriptRoot 'lib/*')
    $arguments=@('-Xmx512m','-Dfile.encoding=UTF-8','-Dshadow.local.integration.profile.active=true',
        '-Dshadow.local.integration.enabled=true',('-Djava.library.path='+(Join-Path $PSScriptRoot 'native')),
        '-cp',$classpath,'br.com.esl.etl.v2.bootstrap.QualificationLaboratoryMain',$Command,
        ('--manifest-sha='+$ManifestSha256))
    if($Campaign){$arguments+=('--campaign='+[IO.Path]::GetFullPath($Campaign))}
    if($Control){$arguments+=('--control='+[IO.Path]::GetFullPath($Control))}
    if($Configuration){$arguments+=('--configuration='+[IO.Path]::GetFullPath($Configuration))}
}
$info=[Diagnostics.ProcessStartInfo]::new($java);$info.WorkingDirectory=$PSScriptRoot
$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
# Inherited VM/classpath options are not package inputs and must not inject another agent or jar.
foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){$null=$info.Environment.Remove($name)}
if($offline){
    foreach($name in @($info.Environment.Keys|Where-Object { $_.StartsWith('V2_',[StringComparison]::OrdinalIgnoreCase) })){
        $null=$info.Environment.Remove($name)
    }
}
foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
$process=[Diagnostics.Process]::Start($info)
try{
    $outBuffer=[char[]]::new(4096);$errBuffer=[char[]]::new(4096)
    $outTask=$process.StandardOutput.ReadAsync($outBuffer,0,$outBuffer.Length)
    $errTask=$process.StandardError.ReadAsync($errBuffer,0,$errBuffer.Length)
    $outDone=$false;$errDone=$false;$characters=0L;$timer=[Diagnostics.Stopwatch]::StartNew();$timedOut=$false
    while(-not ($process.HasExited -and $outDone -and $errDone)){
        foreach($stream in @('out','err')){
            $pending=if($stream -eq 'out'){$outTask}else{$errTask}
            $done=if($stream -eq 'out'){$outDone}else{$errDone}
            if(-not $done -and $pending.IsCompleted){
                $count=$pending.GetAwaiter().GetResult()
                if($count -eq 0){if($stream -eq 'out'){$outDone=$true}else{$errDone=$true};continue}
                $characters+=$count
                if($characters -gt 1048576){throw 'QUAL_LOG_LIMIT'}
                $buffer=if($stream -eq 'out'){$outBuffer}else{$errBuffer}
                $chunk=[string]::new($buffer,0,$count)
                if($chunk.Contains([char]0xfffd)){throw 'QUAL_LOG_ENCODING'}
                if($stream -eq 'out'){
                    [Console]::Out.Write($chunk);$outTask=$process.StandardOutput.ReadAsync($outBuffer,0,$outBuffer.Length)
                }else{
                    [Console]::Error.Write($chunk);$errTask=$process.StandardError.ReadAsync($errBuffer,0,$errBuffer.Length)
                }
            }
        }
        if($timer.Elapsed.TotalSeconds -ge 3660){$timedOut=$true;break}
        $null=$process.WaitForExit(20)
        if($process.HasExited -and -not ($outDone -and $errDone)){Start-Sleep -Milliseconds 10}
    }
    if($timedOut){$process.Kill($true);$process.WaitForExit();$exitCode=124}else{$exitCode=$process.ExitCode}
}finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
exit $exitCode

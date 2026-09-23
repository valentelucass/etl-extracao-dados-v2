#Requires -Version 7.5
param([Parameter(Mandatory)][ValidateSet('inspect','plan','run','status','resume','compare')][string]$Command,
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
$arguments=@('-Xmx512m','-Dfile.encoding=UTF-8','-Dshadow.local.integration.profile.active=true',
    '-Dshadow.local.integration.enabled=true',('-Djava.library.path='+(Join-Path $PSScriptRoot 'native')),
    '-cp',$jar,'br.com.esl.etl.v2.bootstrap.QualificationLaboratoryMain',$Command,
    ('--manifest-sha='+$ManifestSha256))
if($Campaign){$arguments+=('--campaign='+[IO.Path]::GetFullPath($Campaign))}
if($Control){$arguments+=('--control='+[IO.Path]::GetFullPath($Control))}
if($Configuration){$arguments+=('--configuration='+[IO.Path]::GetFullPath($Configuration))}
$info=[Diagnostics.ProcessStartInfo]::new($java);$info.WorkingDirectory=$PSScriptRoot
$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
# Inherited VM/classpath options are not package inputs and must not inject another agent or jar.
foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){$null=$info.Environment.Remove($name)}
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

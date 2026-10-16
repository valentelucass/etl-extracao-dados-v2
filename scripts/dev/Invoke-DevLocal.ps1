param(
    [Parameter(Mandatory)][ValidateSet('init','run','status','replay','cancel','error','stop','scenario')][string]$Command,
    [Parameter(Mandatory)][string]$Workspace,
    [switch]$Synthetic,
    [string]$Package = (Join-Path $PSScriptRoot '../../target/dev-local-package-b77/etl-dataexport-v2.jar'),
    [string]$Java = 'java.exe',
    [string]$Config,
    [string]$Execution,
    [ValidateSet('BOOTSTRAP','INCREMENTAL','RECOMPOSE')][string]$Mode,
    [string]$Previous,
    [ValidateSet('ABSENCE_REACTIVATION','PARTIAL_REFUSED','QUARANTINE_REFUSED','OWNER_REFUSED')][string]$Case,
    [ValidateRange(64,512)][int]$HeapMiB = 128
)
$ErrorActionPreference = 'Stop'
if (!$Synthetic) { throw 'DEV_LOCAL_SYNTHETIC_REQUIRED' }
$artifact = Get-Item -LiteralPath $Package
if ($artifact.PSIsContainer -or $artifact.Extension -cne '.jar') { throw 'DEV_LOCAL_PACKAGE_REQUIRED' }
$javaCommand = Get-Command $Java -CommandType Application
$libraryDirectory = Join-Path $artifact.DirectoryName 'lib'
$classpath = $artifact.FullName
if (Test-Path -LiteralPath $libraryDirectory -PathType Container) {
    $classpath += [IO.Path]::PathSeparator + (Join-Path $libraryDirectory '*')
}
$nativeArguments = @('-Dfile.encoding=UTF-8', "-Xmx${HeapMiB}m", '-cp', $classpath,
    'br.com.esl.etl.v2.bootstrap.DevLocalMain', $Command, '--synthetic', '--workspace', $Workspace)
if ($Config) { $nativeArguments += @('--config', $Config) }
if ($Execution) { $nativeArguments += @('--execution', $Execution) }
if ($Mode) { $nativeArguments += @('--mode', $Mode) }
if ($Previous) { $nativeArguments += @('--previous', $Previous) }
if ($Case) { $nativeArguments += @('--case', $Case) }
& $javaCommand.Source @nativeArguments
exit $LASTEXITCODE

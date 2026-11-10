[CmdletBinding()]
param(
 [ValidateSet('RANGE','ROLLING')][string]$Mode='ROLLING',
 [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$PeriodStart,
 [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$PeriodEnd,
 [Parameter(Mandatory=$true)][ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$RecoveryAnchor,
 [Parameter(Mandatory=$true)][string]$PrivateDirectory,
 [Parameter(Mandatory=$true)][string]$ReceiptPath,
 [switch]$Worker
)
$ErrorActionPreference='Stop'
$repo0762=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$ReceiptPath=[IO.Path]::GetFullPath($ReceiptPath);$PrivateDirectory=[IO.Path]::GetFullPath($PrivateDirectory)
if((Test-Path -LiteralPath $ReceiptPath) -or (Test-Path -LiteralPath $PrivateDirectory)){throw 'ALL_RECEIVING_FRESH_ATTEMPT_REQUIRED'}
$storage0762='C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\'
if(-not $PrivateDirectory.StartsWith($storage0762,[StringComparison]::OrdinalIgnoreCase)){throw 'ALL_RECEIVING_PRIVATE_SCOPE'}
if($Mode -eq 'RANGE' -and (-not $PeriodStart -or -not $PeriodEnd)){throw 'ALL_RECEIVING_EXPLICIT_RANGE_REQUIRED'}
if(-not $Worker){
 $arguments0762=@('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Worker','-Mode',$Mode,'-RecoveryAnchor',$RecoveryAnchor,'-PrivateDirectory',$PrivateDirectory,'-ReceiptPath',$ReceiptPath)
 if($Mode -eq 'RANGE'){$arguments0762+=@('-PeriodStart',$PeriodStart,'-PeriodEnd',$PeriodEnd)}
 $memory0762=& 'C:\Users\suporte\Documents\projetos\.runtime\memory-control\Executar-Batch.ps1' -Executable 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -ArgumentList $arguments0762 -WorkingDirectory $repo0762 -MaxMinutes 115 -ReturnReceipt -ReceiptPath ($ReceiptPath+'.memory.json')
 exit $memory0762.job.ExitCode
}
if($PSVersionTable.PSEdition -ne 'Desktop'){throw 'ALL_RECEIVING_WINDOWS_PS5_REQUIRED'}
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
$manifest0762=Get-Content -LiteralPath (Join-Path $repo0762 'database\production\receiving\runtime-manifest0762.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($manifest0762.schema -cne 'ALL_RECEIVING_RUNTIME0762' -or @($manifest0762.files).Count -ne 9){throw 'ALL_RECEIVING_RUNTIME_SCOPE'}
$runtime0762=[IO.Path]::GetFullPath($manifest0762.directory)
if(-not $runtime0762.StartsWith($storage0762,[StringComparison]::OrdinalIgnoreCase)){throw 'ALL_RECEIVING_RUNTIME_PRIVATE_SCOPE'}
if(@(Get-ChildItem -LiteralPath $runtime0762 -File -Filter '*.jar').Count -ne 9){throw 'ALL_RECEIVING_UNLISTED_RUNTIME_FILE'}
foreach($pin0762 in $manifest0762.files){
 if($pin0762.name -notmatch '^[A-Za-z0-9_.-]+\.jar$' -or (Get-FileHash -LiteralPath (Join-Path $runtime0762 $pin0762.name)).Hash -cne $pin0762.sha256){throw 'ALL_RECEIVING_RUNTIME_DRIFT'}
}
foreach($pin0762 in $manifest0762.scripts){
 if((Get-FileHash -LiteralPath (Join-Path $repo0762 $pin0762.path)).Hash -cne $pin0762.sha256){throw 'ALL_RECEIVING_SCRIPT_DRIFT'}
}
$shared0762=Join-Path $storage0762 'quatro-etapas-20261024-01\independent-export-shared-dataexport'
if(-not(Test-Path -LiteralPath $shared0762 -PathType Container) -or (Test-Path -LiteralPath (Join-Path $shared0762 'STOP429.json'))){throw 'ALL_RECEIVING_SHARED_SOURCE_STOP'}
$gate0762=New-Object Threading.Mutex($false,'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
$held0762=$false;$credential0762=$null;$process0762=$null;$values0762=@{};$exit0762=1
try{
 try{$held0762=$gate0762.WaitOne(0)}catch [Threading.AbandonedMutexException]{$held0762=$true}
 if(-not $held0762){throw 'ALL_RECEIVING_OTHER_SOURCE_ACTIVE'}
 foreach($line0762 in [IO.File]::ReadAllLines((Join-Path $repo0762 '.env'))){
  if($line0762 -match '^\uFEFF?\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\s*=\s*(.*)$'){
   $key0762=$matches[1];$value0762=$matches[2].Trim()
   if($value0762.Length -ge 2 -and (($value0762[0] -eq '"' -and $value0762[$value0762.Length-1] -eq '"') -or ($value0762[0] -eq "'" -and $value0762[$value0762.Length-1] -eq "'"))){$value0762=$value0762.Substring(1,$value0762.Length-2)}
   if($value0762.Length){$values0762[$key0762]=$value0762}
  }
 }
 if(-not $values0762.ContainsKey('API_BASE_URL') -or -not $values0762.ContainsKey('API_DATAEXPORT_TOKEN')){throw 'ALL_RECEIVING_SOURCE_CREDENTIAL_REQUIRED'}
 $profile0762=Get-ProjetosSqlProfile
 $credential0762=Import-Clixml -LiteralPath (Join-Path $env:LOCALAPPDATA 'Rodogarcia\SqlServer\etl-v2-runtime-prod.clixml')
 $config0762=[ordered]@{database='ETL_SISTEMA_V2';runtimeSha256=($manifest0762.files|Where-Object {$_.name -ceq 'etl-dataexport-v2-0762.jar'}).sha256.ToLowerInvariant();jdbcBaseUrl=$profile0762.jdbcBaseUrl;sqlUser=$credential0762.UserName;sqlPassword=$credential0762.GetNetworkCredential().Password;apiOrigin=$values0762['API_BASE_URL'].TrimEnd('/');apiToken=$values0762['API_DATAEXPORT_TOKEN'];privateRoot=$PrivateDirectory;stopPath=(Join-Path $shared0762 'STOP429.json');reportPath=$ReceiptPath;mode=$Mode;periodStart=$PeriodStart;periodEnd=$PeriodEnd;recoveryAnchor=$RecoveryAnchor}
 $process0762=New-Object Diagnostics.Process
 $process0762.StartInfo.FileName='C:\Users\suporte\AppData\Local\Programs\Eclipse Adoptium\jdk-17\bin\java.exe'
 $process0762.StartInfo.Arguments='-Xmx768m -Dfile.encoding=UTF-8 -cp "'+(Join-Path $runtime0762 '*')+'" br.com.esl.etl.v2.bootstrap.ProductionAllReceivingMain'
 $process0762.StartInfo.UseShellExecute=$false;$process0762.StartInfo.CreateNoWindow=$true
 $process0762.StartInfo.RedirectStandardInput=$true;$process0762.StartInfo.RedirectStandardOutput=$true;$process0762.StartInfo.RedirectStandardError=$true
 if(-not $process0762.Start()){throw 'ALL_RECEIVING_JAVA_NOT_STARTED'}
 $outTask0762=$process0762.StandardOutput.ReadToEndAsync();$errTask0762=$process0762.StandardError.ReadToEndAsync()
 $writer0762=New-Object IO.StreamWriter($process0762.StandardInput.BaseStream,(New-Object Text.UTF8Encoding($false)))
 try{$writer0762.Write(($config0762|ConvertTo-Json -Compress -Depth 4))}finally{$writer0762.Dispose();$config0762.Clear();$values0762.Clear();$value0762=$null}
 if(-not $process0762.WaitForExit(6600000)){throw 'ALL_RECEIVING_JAVA_DEADLINE'}
 $withheldOut0762=$outTask0762.GetAwaiter().GetResult();$withheldError0762=$errTask0762.GetAwaiter().GetResult()
 if(-not(Test-Path -LiteralPath $ReceiptPath)){throw 'ALL_RECEIVING_REPORT_MISSING'}
 $exit0762=$process0762.ExitCode
 Write-Output $(if($exit0762 -eq 0){'ALL_RECEIVING_PASSED'}else{'ALL_RECEIVING_FAILED_SEE_REPORT'})
}finally{
 $withheldOut0762=$null;$withheldError0762=$null;$value0762=$null;$values0762.Clear()
 if($process0762){if(-not $process0762.HasExited){$process0762.Kill();$null=$process0762.WaitForExit(5000)};$process0762.Dispose()}
 if($credential0762){$credential0762.Password.Dispose()}
 if($held0762){$gate0762.ReleaseMutex()};$gate0762.Dispose()
}
exit $exit0762

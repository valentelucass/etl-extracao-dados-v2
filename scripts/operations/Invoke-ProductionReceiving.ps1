[CmdletBinding()]
param(
 [ValidateSet('RANGE','ROLLING')][string]$Mode='RANGE',
 [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$PeriodStart,
 [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$PeriodEnd,
 [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string]$RecoveryAnchor='2026-10-30',
 [Parameter(Mandatory=$true)][string]$ReceiptPath,
 [switch]$Worker
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$ReceiptPath=[IO.Path]::GetFullPath($ReceiptPath)
if(Test-Path -LiteralPath $ReceiptPath){throw 'RECEIVING_REPORT_ALREADY_EXISTS'}
if($Mode -eq 'RANGE' -and (-not $PeriodStart -or -not $PeriodEnd)){throw 'RECEIVING_EXPLICIT_RANGE_REQUIRED'}
if(-not $Worker){
 $argsList=@('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-Worker','-Mode',$Mode,'-RecoveryAnchor',$RecoveryAnchor,'-ReceiptPath',$ReceiptPath)
 if($Mode -eq 'RANGE'){$argsList+=@('-PeriodStart',$PeriodStart,'-PeriodEnd',$PeriodEnd)}
 $receipt=& 'C:\Users\suporte\Documents\projetos\.runtime\memory-control\Executar-Batch.ps1' -Executable 'C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe' -ArgumentList $argsList -WorkingDirectory $repo -MaxMinutes 115 -ReturnReceipt -ReceiptPath ($ReceiptPath+'.memory.json')
 if($receipt.job.ExitCode -ne 0){exit $receipt.job.ExitCode}
 exit 0
}
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+(($env:PSModulePath -split ';'|Where-Object {$_ -notmatch '[\\/]PowerShell[\\/]7[\\/]Modules$'}) -join ';')
Import-Module 'C:\Users\suporte\Documents\projetos\.runtime\sql-server\SqlServerProjetos.psm1'
$manifest=Get-Content -LiteralPath (Join-Path $repo 'database\production\receiving\runtime-manifest-rev2.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($manifest.schema -cne 'PRODUCTION_RECEIVING_RUNTIME0759' -or @($manifest.pins).Count -ne 16 -or $manifest.classpath -cne ((@($manifest.pins)[0..8]|ForEach-Object path)-join';')){throw 'RECEIVING_RUNTIME_MANIFEST_SCOPE_REFUSED'}
foreach($pin in $manifest.pins){if((Get-FileHash -LiteralPath $pin.path -Algorithm SHA256).Hash -cne $pin.sha256){throw 'RECEIVING_QUALIFIED_RUNTIME_CHANGED'}}
$privateRoot='C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\production-receiving0759'
$shared='C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\independent-export-shared-dataexport'
if(-not(Test-Path -LiteralPath $shared -PathType Container)){throw 'RECEIVING_EXISTING_SHARED_SOURCE_REQUIRED'}
if(Test-Path -LiteralPath (Join-Path $shared 'STOP429.json')){throw 'RECEIVING_EXISTING_SHARED_SOURCE_STOP429'}
if(-not(Test-Path -LiteralPath $privateRoot -PathType Container)){throw 'RECEIVING_PRIVATE_ROOT_NOT_PREPARED'}
$gate=New-Object Threading.Mutex($false,'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
$acquired=$false;$credential=$null;$process=$null;$plain=$null;$values=@{}
try{
 try{$acquired=$gate.WaitOne(0)}catch [Threading.AbandonedMutexException]{$acquired=$true}
 if(-not $acquired){throw 'RECEIVING_OTHER_SOURCE_UNIT_ACTIVE'}
 foreach($line in [IO.File]::ReadAllLines((Join-Path $repo '.env'))){
  if($line -match '^\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\s*=\s*(.*)$'){
   $key=$matches[1];$value=$matches[2].Trim()
   if($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[$value.Length-1] -eq '"') -or ($value[0] -eq "'" -and $value[$value.Length-1] -eq "'"))){$value=$value.Substring(1,$value.Length-2)}
   if($value.Length){$values[$key]=$value}
  }
 }
 if(-not $values.ContainsKey('API_BASE_URL') -or -not $values.ContainsKey('API_DATAEXPORT_TOKEN')){throw 'RECEIVING_V2_SOURCE_CREDENTIAL_REQUIRED'}
 $profile=Get-ProjetosSqlProfile
 $credential=Import-Clixml -LiteralPath (Join-Path $env:LOCALAPPDATA 'Rodogarcia\SqlServer\etl-v2-runtime-prod.clixml')
 $config=[ordered]@{database='ETL_SISTEMA_V2';jdbcBaseUrl=$profile.jdbcBaseUrl;sqlUser=$credential.UserName;sqlPassword=$credential.GetNetworkCredential().Password;apiOrigin=$values['API_BASE_URL'].TrimEnd('/');apiToken=$values['API_DATAEXPORT_TOKEN'];curlPath='C:\Windows\System32\curl.exe';privateRoot=$privateRoot;stopPath=(Join-Path $shared 'STOP429.json');reportPath=$ReceiptPath;mode=$Mode;periodStart=$PeriodStart;periodEnd=$PeriodEnd;recoveryAnchor=$RecoveryAnchor;codeSha256=$manifest.code_sha256.ToLowerInvariant()}
 $process=New-Object Diagnostics.Process
 $process.StartInfo.FileName='C:\Users\suporte\AppData\Local\Programs\Eclipse Adoptium\jdk-17\bin\java.exe'
 $process.StartInfo.Arguments='-Xmx512m -Dfile.encoding=UTF-8 -cp "'+$manifest.classpath+'" br.com.esl.etl.v2.plataforma.producao.ProductionReceivingMain'
 $process.StartInfo.UseShellExecute=$false;$process.StartInfo.CreateNoWindow=$true
 $process.StartInfo.RedirectStandardInput=$true;$process.StartInfo.RedirectStandardOutput=$true;$process.StartInfo.RedirectStandardError=$true
 if(-not $process.Start()){throw 'RECEIVING_JAVA_NOT_STARTED'}
 $outTask=$process.StandardOutput.ReadToEndAsync();$errTask=$process.StandardError.ReadToEndAsync()
 $writer=New-Object IO.StreamWriter($process.StandardInput.BaseStream,(New-Object Text.UTF8Encoding($false)))
 try{$writer.Write(($config|ConvertTo-Json -Compress -Depth 4))}finally{$writer.Dispose();$config.Clear();$values.Clear();$value=$null}
 if(-not $process.WaitForExit(6600000)){throw 'RECEIVING_JAVA_DEADLINE'}
 $safe=$outTask.GetAwaiter().GetResult();$privateError=$errTask.GetAwaiter().GetResult()
 if(-not(Test-Path -LiteralPath $ReceiptPath)){throw 'RECEIVING_STRUCTURED_REPORT_MISSING'}
 $exitCode=$process.ExitCode
 Write-Output $(if($exitCode -eq 0){'RECEIVING_PASSED'}else{'RECEIVING_FAILED_SEE_REPORT'})
}finally{
 $plain=$null;$privateError=$null;$safe=$null;$value=$null;$values.Clear()
 if($process){if(-not $process.HasExited){$process.Kill();$null=$process.WaitForExit(5000)};$process.Dispose()}
 if($credential){$credential.Password.Dispose()}
 if($acquired){$gate.ReleaseMutex()};$gate.Dispose()
}
exit $exitCode

#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ApprovedPackageSha256)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));Set-Location $root
$package='database/proposals/bloco60-pair-reconcile/package.json'
if((Get-FileHash $package).Hash.ToLowerInvariant() -cne $ApprovedPackageSha256){throw 'DIAG_PACKAGE_HASH'}
$p=Get-Content $package -Raw|ConvertFrom-Json;foreach($e in $p.files){if((Get-FileHash $e.path).Hash.ToLowerInvariant() -cne $e.sha256){throw 'DIAG_FILE_HASH'}}
if([Security.Principal.WindowsIdentity]::GetCurrent().Name -cne 'RTR-SVW-002\suporte' -or -not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'DIAG_WINDOWS_AUTHORITY'}
if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]'2026-09-16T00:00:00Z'){throw 'DIAG_VALIDITY_EXPIRED'}
$folder=Join-Path $root 'target/execucao-b60-retomada-20260910-0910/pair-diagnostic';if(Test-Path $folder){throw 'DIAG_RECONCILE_BEFORE_REPEAT'};$null=New-Item -ItemType Directory $folder
Import-Module "$PSScriptRoot/Bloco60PairRecoveryLedger.psm1" -Force
Add-Type -Path "$PSScriptRoot/Bloco60AdminProcess.cs"
$ledger=Join-Path $folder 'ledger.jsonl';$passed=$false
$null=Write-Bloco60PairRecoveryLedger -Path $ledger -Event @{type='OPEN';package=$ApprovedPackageSha256;priorSqlcmd=158;priorJvms=77;operation='EXACT_TWO_RECOVERY_ESCROW_READBACKS'}
try{
 foreach($tuple in @(@('MASTER_TARGET','master','database/proposals/bloco60-retomada-r2/master-target.sql'),@('PAIR_RECONCILIATION','ETL_SISTEMA_V2_SHADOW','database/proposals/bloco60-pair-reconcile/pair.sql'))){
  $null=Write-Bloco60PairRecoveryLedger -Path $ledger -Event @{type='RESERVE';id=$tuple[0];kind='READBACK'}
  $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=(Get-Command sqlcmd -CommandType Application).Source;$start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=Join-Path $root 'database/proposals/bloco60-pair-reconcile'
  foreach($arg in @('-S','localhost','-d',$tuple[1],'-E','-N','-f','65001','-l','10','-t','30','-b','-y','0','-w','65535','-i',(Join-Path $root $tuple[2]))){$start.ArgumentList.Add($arg)}
  $job=[Bloco60AdminJob]::new($start)
  try{$result=$job.Completion.GetAwaiter().GetResult();$file=Join-Path $folder ($tuple[0]+'.sql.log');[IO.File]::WriteAllText($file,$result.Output,[Text.UTF8Encoding]::new($false));$ok=$result.Code -eq 0 -and -not $result.Limited;$null=Write-Bloco60PairRecoveryLedger -Path $ledger -Event @{type='OBSERVE';id=$tuple[0];outcome=$(if($ok){'CONFIRMED'}else{'REFUSED'});evidence=(Get-FileHash $file).Hash.ToLowerInvariant()};if(-not $ok){throw ('DIAG_REFUSED_'+$tuple[0])}}finally{$job.Dispose()}
 }
 $passed=$true
}finally{
 $null=Write-Bloco60PairRecoveryLedger -Path $ledger -Event @{type='CLOSE';passed=$passed}
 @{passed=$passed;sqlcmd=2;jvms=0;mutations=0;priorSqlcmd=158;priorJvms=77;pid=$PID}|ConvertTo-Json|Set-Content (Join-Path $folder 'result.json') -Encoding utf8NoBOM
}
if(-not $passed){exit 1}


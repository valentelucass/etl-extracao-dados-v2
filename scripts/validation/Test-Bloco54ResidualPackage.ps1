#Requires -Version 7.0
param([switch]$IncludePrivateEvidence)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$package=Join-Path $root 'database/proposals/bloco54-residual-runtime'
$manifest=Get-Content (Join-Path $package 'manifest.json') -Raw|ConvertFrom-Json
$execution=Get-Content (Join-Path $package 'execution.json') -Raw|ConvertFrom-Json
if($execution.status -cne 'SEVENTH_AUTHORIZED_EXECUTED_PARTIAL_RESOLVED_BY_COMPLETION' -or $execution.campaign -ne 7 -or $execution.reservationsAfter -ne 128 -or -not $execution.rightsCompensated){throw 'RESIDUAL_EXECUTION_RECEIPT_REQUIRED'}
if((Get-FileHash (Join-Path $package 'manifest.json')).Hash.ToLowerInvariant() -cne $execution.preparedManifest.sha256){throw 'HISTORICAL_PREPARATION_CHANGED'}
if($manifest.status -cne 'PREPARED_NOT_AUTHORIZED' -or $manifest.seventhCampaignAuthorized -or $manifest.previousReservations -ne 109 -or $manifest.additionalReservations -ne 19 -or $manifest.maximumReservations -ne 128 -or $manifest.maximumMinutes -ne 15 -or $manifest.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW'){throw 'RESIDUAL_APPROVAL_OR_BUDGET_DRIFT'}
foreach($entry in $manifest.files){
 if($entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or $entry.path.StartsWith('/')){throw 'RESIDUAL_FILE_SCOPE'}
 if(-not $IncludePrivateEvidence -and $entry.path.StartsWith('target/')){continue}
 if($entry.path -ceq 'scripts/validation/Invoke-Bloco54Manual.ps1'){
  if($entry.sha256 -cne $execution.historicalManual.sha256){throw 'HISTORICAL_MANUAL_RECEIPT_DRIFT'}
  if($IncludePrivateEvidence -and (Get-FileHash (Join-Path $root $execution.historicalManual.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'HISTORICAL_MANUAL_BYTES_CHANGED'}
  continue
 }
 if((Get-FileHash (Join-Path $root $entry.path)).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'RESIDUAL_FILE_CHANGED'}
}
foreach($name in @('Invoke-ResidualRuntime.ps1','budget-reviewed.psm1')){
 $tokens=$null;$errors=$null
 [void][Management.Automation.Language.Parser]::ParseFile((Join-Path $package $name),[ref]$tokens,[ref]$errors)
 if($errors.Count){throw 'RESIDUAL_SCRIPT_PARSE_FAILED'}
}
$controller=Get-Content (Join-Path $package 'Invoke-ResidualRuntime.ps1') -Raw
foreach($required in @('OWNER_APPROVED_B54_SEVENTH_EXISTING_BALANCE','DATA_ROWS=5328','mapping_version','SIXTH_MAPPING_CHECKPOINT_REQUIRED','final-preservation','REPLAY_COMPENSATION_READBACK_CONFLICT','REPLAY_PRECONDITION_CHANGED','SEVENTH_DRIFT_ALERT_SQL','SEVENTH_TLS_VALIDATION_REQUIRED','SEVENTH_TIME_SECOND_FIRST','AssumeUniversal')){if(-not $controller.Contains($required)){throw 'RESIDUAL_REQUIRED_GUARD_MISSING'}}
$active=Get-Content (Join-Path $PSScriptRoot 'Bloco54Budget.psm1') -Raw
if($active.Contains('Start-Bloco54SeventhCampaign')){throw 'UNAUTHORIZED_SEVENTH_INSTALLED_IN_ACTIVE_HELPER'}
if($IncludePrivateEvidence){
 $proof=Get-Content (Join-Path $root 'target/bloco54/resume-sixth/residual-budget-selftest.log') -Raw
 if(-not $proof.Contains('NO_UNAUTHORIZED_SEVENTH_NO_129_NO_EIGHTH_REAL_LEDGER_PRESERVED')){throw 'RESIDUAL_BUDGET_COPY_NOT_PROVEN'}
}
'RESIDUAL_HISTORICAL_PREPARATION_AND_SEVENTH_EXECUTION_RECEIPT_PASS'

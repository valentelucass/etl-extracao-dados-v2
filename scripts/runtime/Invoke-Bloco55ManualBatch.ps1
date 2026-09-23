#Requires -Version 7.0
param([Parameter(Mandatory)][string]$Manifest,[Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256,
 [Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_]{1,40}$')][string]$RunId,
 [ValidateSet('Preview','Diagnose','Run','Status')][string]$Operation='Preview',[ValidateRange(1,20)][int]$StopAfter=20,
 [ValidatePattern('^reviewed-bundle-v[1-9][0-9]?$')][string]$Revision='reviewed-bundle-v4',
 [ValidatePattern('^[a-f0-9]{64}$')][string]$ArtifactSha256='a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $root 'scripts/validation/Bloco55Artifact.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Bloco55ManualBatch.psm1') -Force
$review=Read-Bloco55Bundle (Join-Path $root ('target/bloco55/'+$Revision)) $ArtifactSha256
$batch=Read-Bloco55ManualBatch $Manifest $ManifestSha256 $review
if($Operation -ceq 'Preview'){'MANUAL_BATCH_PREFLIGHT_PASS requests='+$batch.Requests.Count+' effects=0';return}
if($Operation -ceq 'Diagnose'){
 Assert-Bloco55Protected 'C:\ProgramData\EslEtlV2\app-bloco55'
 & (Join-Path $root 'scripts/validation/Invoke-Bloco55ReadOnlySnapshot.ps1') -ProofId $RunId
 'MANUAL_DIAGNOSE_PROTECTED_ARTIFACT_EXACT_PROFILE_PRESERVATION_PASS'
 return
}
$controller=Join-Path $root 'scripts/validation/Invoke-Bloco55RuntimeProofs.ps1'
& $controller -ProofId $RunId -Revision $Revision -ExpectedManifestSha256 $ArtifactSha256 -Phase Manual -BatchManifest $batch.Path -BatchSha256 $batch.Sha256 -ManualOperation $Operation -StopAfter $StopAfter
if(-not (Test-Path (Join-Path $root ('target/bloco55/runtime/'+$RunId+'/manual-summary.json')))){throw 'MANUAL_EXECUTION_UNCERTAIN_INSPECT_SQL'}
Get-Content (Join-Path $root ('target/bloco55/runtime/'+$RunId+'/manual-summary.json')) -Raw

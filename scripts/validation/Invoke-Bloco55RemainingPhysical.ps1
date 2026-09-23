# One normal UAC elevation for the already-authorized remaining finite cases.
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if(-not ([Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'NORMAL_UAC_REQUIRED'}
$hash='a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13'
$launcher=Join-Path $root 'scripts/runtime/Invoke-Bloco55ManualBatch.ps1'
$controller=Join-Path $PSScriptRoot 'Invoke-Bloco55RuntimeProofs.ps1'
& $launcher -Manifest (Join-Path $root 'target/bloco55/manual/positive/manifest.json') -ManifestSha256 '19e0e7f307c4bea191382cdcb6beccd47f3d6452c336a8726a780d66cbd6e293' -RunId B55_MANUAL_OPERATOR -Operation Status -StopAfter 5
& $controller -ProofId B55_MANUAL_DEPENDENCY -Revision reviewed-bundle-v4 -ExpectedManifestSha256 $hash -Phase Manual -BatchManifest (Join-Path $root 'target/bloco55/manual/dependency-negative/manifest.json') -BatchSha256 'f1d60eb4d3f8fbd92af89858dd8b64734ec0c85f6b9398395ed1d282d9ae19e1' -ManualFault
& $controller -ProofId B55_SQL_01 -Revision reviewed-bundle-v4 -ExpectedManifestSha256 $hash -Phase Sql
'REMAINING_FINITE_PHYSICAL_CONTROLLER_FINISHED_INSPECT_RECEIPTS'

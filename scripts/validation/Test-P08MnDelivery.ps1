#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
function ReadJson([string]$path){Get-Content -LiteralPath (Join-Path $root $path) -Raw|ConvertFrom-Json -AsHashtable}
function Hash([string]$path){(Get-FileHash -LiteralPath (Join-Path $root $path)).Hash.ToLowerInvariant()}
function Fail([string]$code){throw $code}
$manifestPath='docs/catalogos/p08-mn/manifesto.json'
$manifest=ReadJson $manifestPath
if((Get-Content -LiteralPath (Join-Path $root 'docs/catalogos/p08-mn/manifesto.sha256') -Raw).Trim() -cne (Hash $manifestPath)){Fail 'P08N_MANIFEST_SEAL'}
if($manifest.status -cne 'P08_M_N_ACEITO_NO_ESCOPO_LOCAL' -or $manifest.package.members -ne 724 -or
    $manifest.package.archiveSha256 -notmatch '^[a-f0-9]{64}$' -or $manifest.package.manifestSha256 -notmatch '^[a-f0-9]{64}$'){Fail 'P08N_SCOPE'}
foreach($name in @('syntheticRollbackOnly','ddl','domainCommit','production','cutover','realParity','humanReview','parentV2Acceptance')){
    $expected=$name -eq 'syntheticRollbackOnly'
    if($manifest.scope[$name] -ne $expected){Fail 'P08N_SCOPE'}
}
if($manifest.scope.remoteCalls -ne 0 -or $manifest.counters.construction -cne '39/45' -or $manifest.counters.historicalAcceptances -cne '67/115'){Fail 'P08N_COUNTERS'}
foreach($entry in $manifest.overlay){if((Hash $entry.path) -cne $entry.sha256){Fail 'P08N_OVERLAY_DRIFT'}}
$round='target/macrobloco-qualificacao-pacote-20260913-01/'
foreach($package in @('p08-mn-package-current-primary-23','p08-mn-package-current-reproduction-23')){
    $result=ReadJson ($round+$package+'/result.json')
    if($result.members -ne 724 -or $result.archiveSha256 -cne $manifest.package.archiveSha256 -or $result.manifestSha256 -cne $manifest.package.manifestSha256 -or
        (Hash ($round+$package+'/qualification.zip')) -cne $manifest.package.archiveSha256){Fail 'P08N_PACKAGE_DRIFT'}
}
$seal=ReadJson $manifest.package.readbackSeal
if((Hash $manifest.package.readbackSeal) -cne $manifest.package.readbackSealSha256 -or -not $seal.package.byteIdentical -or
    $seal.package.archiveSha256 -cne $manifest.package.archiveSha256 -or $seal.package.manifestSha256 -cne $manifest.package.manifestSha256 -or -not $seal.m.rollbackConfirmed){Fail 'P08N_SEAL_DRIFT'}
$variants=@('current-sequence-a','current-sequence-b','current-value','current-precision','key','multiplicity','old-reference','missing-user','pin-drift','command')
foreach($variant in $variants){$r=ReadJson ($round+'p08-mn-'+$variant+'-23/result.json');if(-not $r.passed -or -not $r.rollbackConfirmed -or $r.timedOut -or $r.logLimitExceeded -or $r.ddl -or $r.domainCommit -or $r.sourceCalls -ne 0){Fail 'P08N_M_RECEIPT'}}
$scanner=Get-Content -LiteralPath (Join-Path $root ($round+'p08-mn-n-scanner-23/stdout.log')) -Raw
if(-not $scanner.Contains('OFFLINE_SECRET_SCAN status=PASS') -or -not $scanner.Contains('findings=0')){Fail 'P08N_SCANNER'}
@{passed=$true;status=$manifest.status;members=724;archiveSha256=$manifest.package.archiveSha256;receipts=$variants.Count;sourceCalls=0;newAcceptances=0}|ConvertTo-Json -Compress

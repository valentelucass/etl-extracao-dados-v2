#Requires -Version 7.5
param(
    [Parameter(Mandatory)][ValidateSet('RELATIONAL_LOCAL_CANDIDATE','RELATIONAL_LOCAL_COMPLETE')][string]$Status,
    [Parameter(Mandatory)][ValidatePattern('^docs/continuidade/checkpoints/[A-Za-z0-9_-]+\.md$')][string]$Checkpoint
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-relacional-20260911-01'
$catalog='docs/catalogos/macrobloco-relacional/'
$history='docs/continuidade/historico/macrobloco-relacional/'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$initial=Get-Content -LiteralPath (Join-Path $round 'inventory-before.json') -Raw|ConvertFrom-Json -Depth 30
$base=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach($entry in $initial){$base.Add($entry.path,$entry)}
function Files {
    Push-Location -LiteralPath $root
    try {
        $names=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*')
        if($LASTEXITCODE -ne 0){throw 'DELIVERY_INVENTORY_FAILED'}
        return @($names|ForEach-Object {$_ -replace '\\','/'}|Sort-Object)
    } finally {Pop-Location}
}
function Hash([string]$relative){(Get-FileHash -LiteralPath (Join-Path $root $relative)).Hash.ToLowerInvariant()}
$changed=[Collections.Generic.List[object]]::new()
$preserved=[Collections.Generic.List[object]]::new()
foreach($entry in $initial){
    $current=Hash $entry.path
    if($current -ceq $entry.sha256){$preserved.Add(@{path=$entry.path;sha256=$current});continue}
    $snapshot=$history+$entry.path
    $source=Join-Path $round ('before/'+$entry.path)
    if((Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'DELIVERY_BEFORE_DRIFT'}
    $destination=Join-Path $root $snapshot
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $destination))
    if(Test-Path -LiteralPath $destination){
        if((Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'DELIVERY_SNAPSHOT_DRIFT'}
    }else{Copy-Item -LiteralPath $source -Destination $destination}
    $changed.Add(@{path=$entry.path;before=$entry.sha256;after=$current;snapshot=$snapshot})
}
$new=[Collections.Generic.List[object]]::new()
foreach($relative in Files){
    if($base.ContainsKey($relative) -or $relative -cin @(($catalog+'manifesto.json'),($catalog+'manifesto.sha256'))){continue}
    $new.Add(@{path=$relative;sha256=(Hash $relative)})
}
$evidence=[Collections.Generic.List[object]]::new()
foreach($relative in @('java-verification.json','packaged-class-verification.json','scale-verification.json',
    'schema-validation-final.json','jar-01/result.json','security-offline-01.json','security-selftest-01.json',
    'static-01.json','security-delta-01.json','review-candidate-01.json',
    'logs/continuity-candidate-relational-01.log','logs/continuity-candidate-predecessor-01.log',
    'logs/runtime-candidate-01.log')){
    $path='target/macrobloco-relacional-20260911-01/'+$relative
    $evidence.Add(@{path=$path;sha256=(Hash $path)})
}
foreach($installation in @('schema-install-01','schema-evolution-install-01','schema-strengthen-install-01',
    'schema-completeness-install-01','schema-key-install-01','schema-mdfe-install-01')){
    foreach($name in @('ledger.jsonl','migrations.json')){
        $path='target/macrobloco-relacional-20260911-01/'+$installation+'/'+$name
        $evidence.Add(@{path=$path;sha256=(Hash $path)})
    }
}
$manifest=@{version=1;status=$Status;initialFiles=$initial.Count;target='localhost/ETL_SISTEMA_V2_SHADOW';
    syntheticOnly=$true;remoteCalls=0;newAcceptances=0;durableDomainRows=0;operationalPromotionAuthorized=$false;realQualificationAccepted=$false;
    predecessor=@{path='docs/catalogos/coletas-temporal-integration/manifesto.json';sha256='918f6c1e1671eefd71a7b3676cfcfb5b8b32615efe1f2c86925c36f47029682d'};
    changedExistingFiles=@($changed);preservedFiles=@($preserved);newFiles=@($new);evidence=@($evidence);
    checkpoint=@{path=$Checkpoint;sha256=(Hash $Checkpoint)}}
$manifest|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $root ($catalog+'manifesto.json')) -Encoding utf8
(Hash ($catalog+'manifesto.json'))|Set-Content -LiteralPath (Join-Path $root ($catalog+'manifesto.sha256')) -Encoding ascii
$after=@(foreach($relative in Files){@{path=$relative;sha256=(Hash $relative);bytes=(Get-Item -LiteralPath (Join-Path $root $relative)).Length}})
$after|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $round 'inventory-after.json') -Encoding utf8
$empty=Join-Path $round 'empty-for-diff.txt'
if(-not (Test-Path -LiteralPath $empty)){[IO.File]::WriteAllText($empty,'',$utf8)}
$patch=[Text.StringBuilder]::new();$codePatch=[Text.StringBuilder]::new()
$deltas=@($changed.path)+@($after|Where-Object {-not $base.ContainsKey($_.path)}|ForEach-Object path)
foreach($relative in $deltas|Sort-Object){
    $isNew=-not $base.ContainsKey($relative)
    $before=if($isNew){$empty}else{Join-Path $round ('before/'+$relative)}
    $current=Join-Path $root $relative
    $info=[Diagnostics.ProcessStartInfo]::new();$info.FileName='git';$info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in @('diff','--no-index','--no-ext-diff','--text','--',$before,$current)){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::new();$process.StartInfo=$info
    try {
        [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        $process.WaitForExit();$text=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
        if($process.ExitCode -gt 1){throw 'DELIVERY_DIFF_FAILED'}
    } finally {$process.Dispose()}
    $hunk=$text.IndexOf('@@ ')
    if($hunk -lt 0){throw ('DELIVERY_DIFF_NO_HUNK_'+$relative)}
    $header="diff --git a/$relative b/$relative`n"
    if($isNew){$header+="new file mode 100644`n--- /dev/null`n"}else{$header+="--- a/$relative`n"}
    $entry=$header+"+++ b/$relative`n"+$text.Substring($hunk).Replace("`r`n","`n")
    [void]$patch.Append($entry)
    if($relative -notlike 'docs/continuidade/historico/*' -and $relative -cnotin @(($catalog+'manifesto.json'),($catalog+'manifesto.sha256'))){[void]$codePatch.Append($entry)}
}
[IO.File]::WriteAllText((Join-Path $round 'diff.patch'),$patch.ToString(),$utf8)
[IO.File]::WriteAllText((Join-Path $round 'diff-review.patch'),$codePatch.ToString(),$utf8)
@{status=$Status;initialFiles=$initial.Count;changed=$changed.Count;new=$new.Count;finalFiles=$after.Count;
    diffSha256=(Get-FileHash (Join-Path $round 'diff.patch')).Hash.ToLowerInvariant();
    reviewDiffSha256=(Get-FileHash (Join-Path $round 'diff-review.patch')).Hash.ToLowerInvariant();
    manifestSha256=(Hash ($catalog+'manifesto.json'))}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $round 'delivery-summary.json') -Encoding utf8
Get-Content -LiteralPath (Join-Path $round 'delivery-summary.json')

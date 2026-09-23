Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Read-Bloco55Bundle([string]$Bundle,[string]$ExpectedManifestSha256) {
    $path=[IO.Path]::GetFullPath($Bundle)
    $workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    if(-not $path.StartsWith((Join-Path $workspace 'target/bloco55')+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'BUNDLE_OUTSIDE_B55'}
    if($ExpectedManifestSha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'MANIFEST_HASH_REQUIRED'}
    $file=Join-Path $path 'manifest.json'
    if((Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -cne $ExpectedManifestSha256){throw 'MANIFEST_HASH_MISMATCH'}
    if((Get-Item -LiteralPath $file).Length -gt 65536){throw 'MANIFEST_LIMIT'}
    $manifest=Get-Content -LiteralPath $file -Raw | ConvertFrom-Json -DateKind String
    if($manifest.block -ne 55 -or $manifest.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW'){throw 'BUNDLE_TARGET_REJECTED'}
    if([DateTimeOffset]::UtcNow -ge [DateTimeOffset]::Parse($manifest.validUntil)){throw 'ORIGINAL_VALIDITY_EXPIRED'}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    if(@($manifest.files).Count -gt 128){throw 'BUNDLE_FILE_LIMIT'}
    foreach($entry in $manifest.files){
        if($entry.path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or $entry.path.StartsWith('/') -or -not $seen.Add($entry.path) -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'BUNDLE_ENTRY_REJECTED'}
        $item=Get-Item -LiteralPath (Join-Path $path $entry.path)
        if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'BUNDLE_REPARSE_REFUSED'}
        if((Get-FileHash -LiteralPath $item.FullName).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'BUNDLE_BYTES_CHANGED'}
    }
    foreach($required in @('etl-dataexport-v2.jar','runtime.properties')){if(-not $seen.Contains($required)){throw 'BUNDLE_ENTRY_MISSING'}}
    [pscustomobject]@{Path=$path;Manifest=$manifest;Hash=$ExpectedManifestSha256}
}
function Assert-Bloco55Protected([string]$Directory) {
    $path=[IO.Path]::GetFullPath($Directory)
    if($path -cne 'C:\ProgramData\EslEtlV2\app-bloco55'){throw 'PROTECTED_TARGET_REJECTED'}
    $allowed=@('S-1-5-32-544','S-1-5-18')
    $readers=@('etl_v2_exec','etl_v2_view') | ForEach-Object {(Get-LocalUser -Name $_).SID.Value}
    foreach($item in @((Get-Item -LiteralPath $path)) + @(Get-ChildItem -LiteralPath $path -Recurse -Force)){
        if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'PROTECTED_REPARSE_REFUSED'}
        $acl=Get-Acl -LiteralPath $item.FullName
        $owner=$acl.GetOwner([Security.Principal.SecurityIdentifier]).Value
        if($owner -notin $allowed){throw 'PROTECTED_OWNER_INVALID'}
        foreach($rule in $acl.Access){
            $sid=$rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
            if($rule.AccessControlType -ne 'Allow'){throw 'PROTECTED_ACL_UNEXPECTED_DENY'}
            if($sid -notin ($allowed+$readers)){throw 'PROTECTED_ACL_FOREIGN_PRINCIPAL'}
            $writeMask=[Security.AccessControl.FileSystemRights]::Write -bor [Security.AccessControl.FileSystemRights]::Delete -bor [Security.AccessControl.FileSystemRights]::ChangePermissions -bor [Security.AccessControl.FileSystemRights]::TakeOwnership
            if($sid -notin $allowed -and ($rule.FileSystemRights -band $writeMask) -ne 0){throw 'PROTECTED_ACL_WRITE_EXCESS'}
        }
    }
}
function Install-Bloco55Revision($Review) {
    $base='C:\ProgramData\EslEtlV2\app-bloco55'
    $marker=Join-Path $base 'ownership.json'
    if(-not (Test-Path -LiteralPath $marker) -or (Get-Content -LiteralPath $marker -Raw).Trim() -cne 'BLOCO55_OWNER_AUTHORIZED_LOCAL_LAB_V1'){throw 'PROTECTED_OWNERSHIP_REQUIRED'}
    Assert-Bloco55Protected $base
    $revision=Join-Path $base $Review.Hash.Substring(0,16)
    [void][IO.Directory]::CreateDirectory($revision)
    # Resume only missing files. A differing existing file is never overwritten.
    foreach($entry in $Review.Manifest.files){
        $destination=Join-Path $revision $entry.path
        if(Test-Path -LiteralPath $destination){
            if((Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'PROTECTED_PARTIAL_CONFLICT'}
        }
    }
    foreach($entry in $Review.Manifest.files){
        $destination=Join-Path $revision $entry.path
        if(-not (Test-Path -LiteralPath $destination)){
            [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
            [IO.File]::Copy((Join-Path $Review.Path $entry.path),$destination,$false)
        }
        if((Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'PROTECTED_COPY_UNCONFIRMED'}
    }
    $manifest=Join-Path $revision 'manifest.json'
    if(-not (Test-Path -LiteralPath $manifest)){[IO.File]::Copy((Join-Path $Review.Path 'manifest.json'),$manifest,$false)}
    if((Get-FileHash -LiteralPath $manifest).Hash.ToLowerInvariant() -cne $Review.Hash){throw 'PROTECTED_MANIFEST_CHANGED'}
    Assert-Bloco55Protected $base
    $revision
}
Export-ModuleMember -Function Read-Bloco55Bundle,Assert-Bloco55Protected,Install-Bloco55Revision

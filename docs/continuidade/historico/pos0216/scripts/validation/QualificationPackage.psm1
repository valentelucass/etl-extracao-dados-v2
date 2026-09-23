#Requires -Version 7.5
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$script:Utf8=[Text.UTF8Encoding]::new($false,$true)
function Read-QualificationJsonBytes([byte[]]$Bytes,[int]$Maximum=1048576){
    if($Bytes.Length -lt 1 -or $Bytes.Length -gt $Maximum){throw 'QUAL_JSON_SIZE'}
    $text=$script:Utf8.GetString($Bytes)
    if($text.Contains([char]0xfffd) -or $text[0] -eq [char]0xfeff){throw 'QUAL_JSON_UTF8'}
    $options=[System.Text.Json.JsonDocumentOptions]::new();$options.MaxDepth=24
    $document=[System.Text.Json.JsonDocument]::Parse($text,$options)
    function Unique($node){
        if($node.ValueKind -eq [System.Text.Json.JsonValueKind]::Object){
            $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach($property in $node.EnumerateObject()){
                if(-not $seen.Add($property.Name)){throw 'QUAL_JSON_DUPLICATE'}
                Unique $property.Value
            }
        }elseif($node.ValueKind -eq [System.Text.Json.JsonValueKind]::Array){
            foreach($item in $node.EnumerateArray()){Unique $item}
        }
    }
    try{Unique $document.RootElement}finally{$document.Dispose()}
    return ($text|ConvertFrom-Json -AsHashtable -Depth 24 -DateKind String)
}
function Assert-QualificationFields($Object,[string[]]$Names){
    if($Object -isnot [Collections.IDictionary] -or
        @($Object.Keys).Count -ne $Names.Count -or
        @($Object.Keys|Where-Object {$_ -cnotin $Names}).Count -ne 0){throw 'QUAL_JSON_MEMBERS'}
}
function Assert-QualificationPath([string]$Path){
    $node=Get-Item -LiteralPath $Path -Force
    while($null -ne $node){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'QUAL_PACKAGE_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
    }
}
function Assert-QualificationMemberName([string]$Name,[switch]$Envelope){
    if($Name -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$' -or
        (-not $Envelope -and $Name -cin @('package.json','package.sha256'))){throw 'QUAL_PACKAGE_PATH'}
    foreach($part in $Name.Split('/')){
        if(-not $part -or $part -in @('.','..') -or $part.EndsWith('.') -or
            $part -match '^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\..*)?$'){throw 'QUAL_PACKAGE_PATH'}
    }
}
function Get-QualificationByteHash([byte[]]$Bytes){
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}
function Read-QualificationManifest([byte[]]$Bytes,[string]$Sha256){
    if($Sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-QualificationByteHash $Bytes) -cne $Sha256){throw 'QUAL_PACKAGE_PIN'}
    $manifest=Read-QualificationJsonBytes $Bytes
    Assert-QualificationFields $manifest @('version','revision','java','os','architecture','schemaVersion','files')
    if($manifest.version -cne 'qualification-package-v1' -or $manifest.revision -cnotmatch '^[a-f0-9]{64}$' -or
        $manifest.java -isnot [long] -and $manifest.java -isnot [int] -or $manifest.java -ne 17 -or
        $manifest.os -cne 'Windows' -or $manifest.architecture -cne 'x64' -or
        ($manifest.schemaVersion -isnot [long] -and $manifest.schemaVersion -isnot [int]) -or
        $manifest.schemaVersion -cnotin @(98,99,102,104) -or
        $manifest.files -isnot [array] -or $manifest.files.Count -lt 12 -or $manifest.files.Count -gt 512){throw 'QUAL_PACKAGE_COMPATIBILITY'}
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $total=[long]0
    foreach($entry in $manifest.files){
        Assert-QualificationFields $entry @('path','size','sha256','type','role')
        Assert-QualificationMemberName $entry.path
        if(-not $seen.Add($entry.path) -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
            ($entry.size -isnot [long] -and $entry.size -isnot [int]) -or $entry.size -lt 1 -or $entry.size -gt 33554432 -or
            $entry.type -cnotin @('JAR','DLL','JSON','SQL','TEXT','POWERSHELL','XML') -or
            $entry.role -cnotin @('APPLICATION','DEPENDENCY','NATIVE_AUTH','CONFIGURATION','CONTRACT','SCHEMA','FIXTURE','ORACLE','LAUNCHER','DOCUMENTATION','SBOM','PROVENANCE','LICENSE','POLICY')){
            throw 'QUAL_PACKAGE_MEMBER_CONTRACT'
        }
        $total+=$entry.size
    }
    if($total -gt 100663296){throw 'QUAL_PACKAGE_TOTAL_SIZE'}
    return $manifest
}
function Test-QualificationPackage {
    [CmdletBinding()]param([Parameter(Mandatory)][string]$Directory,[Parameter(Mandatory)][string]$ManifestSha256)
    $root=[IO.Path]::GetFullPath($Directory)
    Assert-QualificationPath $root
    $manifestPath=Join-Path $root 'package.json'
    if((Get-Item -LiteralPath $manifestPath).Length -gt 1048576){throw 'QUAL_JSON_SIZE'}
    $manifest=Read-QualificationManifest ([IO.File]::ReadAllBytes($manifestPath)) $ManifestSha256
    $actual=@(Get-ChildItem -LiteralPath $root -Recurse -Force)
    if($actual.Count -gt 1024){throw 'QUAL_PACKAGE_MEMBER_LIMIT'}
    foreach($item in $actual){Assert-QualificationPath $item.FullName}
    $files=@($actual|Where-Object {-not $_.PSIsContainer})
    $names=@($files|ForEach-Object {[IO.Path]::GetRelativePath($root,$_.FullName).Replace('\','/')})
    $expected=@($manifest.files.path)+@('package.json','package.sha256')
    if($names.Count -ne $expected.Count -or @($names|Where-Object {$_ -cnotin $expected}).Count -ne 0){throw 'QUAL_PACKAGE_CONTENT_SET'}
    foreach($entry in $manifest.files){
        $file=Join-Path $root $entry.path
        if((Get-Item -LiteralPath $file).Length -ne $entry.size -or
            (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'QUAL_PACKAGE_MEMBER_HASH'}
    }
    $sidecar=Join-Path $root 'package.sha256'
    if((Get-Item -LiteralPath $sidecar).Length -ne 65 -or [IO.File]::ReadAllText($sidecar,$script:Utf8) -cne ($ManifestSha256+"`n")){throw 'QUAL_PACKAGE_SIDECAR'}
    return $manifest
}
function Expand-QualificationPackage {
    [CmdletBinding()]param([Parameter(Mandatory)][string]$Archive,[Parameter(Mandatory)][string]$ArchiveSha256,
        [Parameter(Mandatory)][string]$Destination,[Parameter(Mandatory)][string]$AllowedRoot)
    $zipPath=[IO.Path]::GetFullPath($Archive);$dest=[IO.Path]::GetFullPath($Destination)
    $allowed=[IO.Path]::GetFullPath($AllowedRoot).TrimEnd([char]92,[char]47)
    Assert-QualificationPath $zipPath;Assert-QualificationPath $allowed
    if(-not $dest.StartsWith($allowed+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        (Test-Path -LiteralPath $dest)){throw 'QUAL_EXTRACT_DESTINATION'}
    $ancestor=[IO.Path]::GetDirectoryName($dest)
    while(-not (Test-Path -LiteralPath $ancestor)){$ancestor=[IO.Path]::GetDirectoryName($ancestor)}
    Assert-QualificationPath $ancestor
    if((Get-Item -LiteralPath $zipPath).Length -gt 67108864 -or $ArchiveSha256 -cnotmatch '^[a-f0-9]{64}$' -or
        (Get-FileHash -LiteralPath $zipPath).Hash.ToLowerInvariant() -cne $ArchiveSha256){throw 'QUAL_ARCHIVE_PIN_OR_SIZE'}
    $stream=[IO.File]::Open($zipPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try{
        # Verify the same locked byte stream that will be extracted; close the hash/open race.
        $sha=[Security.Cryptography.SHA256]::Create()
        try{$hash=[Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant()}finally{$sha.Dispose()}
        if($hash -cne $ArchiveSha256){throw 'QUAL_ARCHIVE_PIN_OR_SIZE'}
        $stream.Position=0
        $zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Read,$true)
        try{
            if($zip.Entries.Count -lt 14 -or $zip.Entries.Count -gt 514){throw 'QUAL_ARCHIVE_MEMBER_LIMIT'}
            $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
            $total=[long]0
            foreach($entry in $zip.Entries){
                Assert-QualificationMemberName $entry.FullName -Envelope
                $unixKind=($entry.ExternalAttributes -shr 16) -band 0xf000
                if(-not $seen.Add($entry.FullName) -or $unixKind -notin @(0,0x8000) -or
                    ($entry.ExternalAttributes -band 0x410) -ne 0){throw 'QUAL_ARCHIVE_LINK_OR_DUPLICATE'}
                if($entry.Length -lt 1 -or $entry.Length -gt 33554432 -or $entry.CompressedLength -lt 1 -or
                    $entry.Length -gt 200*$entry.CompressedLength){throw 'QUAL_ARCHIVE_EXPANSION_LIMIT'}
                $total+=$entry.Length
            }
            if($total -gt 101711872){throw 'QUAL_ARCHIVE_EXPANSION_LIMIT'}
            function Bytes($entry,[int]$maximum){
                if($null -eq $entry -or $entry.Length -gt $maximum){throw 'QUAL_ARCHIVE_MANIFEST_MISSING_OR_SIZE'}
                $input=$entry.Open();$memory=[IO.MemoryStream]::new()
                try{
                    $buffer=[byte[]]::new(16384);$count=0
                    while(($count=$input.Read($buffer,0,$buffer.Length)) -gt 0){
                        if($memory.Length+$count -gt $maximum){throw 'QUAL_ARCHIVE_EXPANSION_LIMIT'}
                        $memory.Write($buffer,0,$count)
                    }
                    if($memory.Length -ne $entry.Length){throw 'QUAL_ARCHIVE_LENGTH'}
                    return ,$memory.ToArray()
                }finally{$input.Dispose();$memory.Dispose()}
            }
            $manifestBytes=Bytes ($zip.GetEntry('package.json')) 1048576
            $manifestHash=Get-QualificationByteHash $manifestBytes
            $manifest=Read-QualificationManifest $manifestBytes $manifestHash
            $sidecar=Bytes ($zip.GetEntry('package.sha256')) 65
            if($script:Utf8.GetString($sidecar) -cne ($manifestHash+"`n")){throw 'QUAL_PACKAGE_SIDECAR'}
            $expected=@($manifest.files.path)+@('package.json','package.sha256')
            if($expected.Count -ne $zip.Entries.Count -or @($zip.Entries|Where-Object {$_.FullName -cnotin $expected}).Count -ne 0){throw 'QUAL_PACKAGE_CONTENT_SET'}
            # Hash all declared payload members before creating the extraction directory.
            foreach($member in $manifest.files){
                $entry=$zip.GetEntry($member.path)
                if($null -eq $entry -or $entry.Length -ne $member.size){throw 'QUAL_PACKAGE_MEMBER_HASH'}
                $bytes=Bytes $entry $member.size
                if((Get-QualificationByteHash $bytes) -cne $member.sha256){throw 'QUAL_PACKAGE_MEMBER_HASH'}
            }
            $null=[IO.Directory]::CreateDirectory($dest)
            foreach($entry in $zip.Entries){
                $file=Join-Path $dest $entry.FullName
                $null=[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($file))
                $bytes=Bytes $entry ([int]$entry.Length)
                $output=[IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
                try{$output.Write($bytes);$output.Flush($true)}finally{$output.Dispose()}
            }
            $null=Test-QualificationPackage -Directory $dest -ManifestSha256 $manifestHash
            return [pscustomobject]@{directory=$dest;manifestSha256=$manifestHash;archiveSha256=$ArchiveSha256;members=$expected.Count;passed=$true}
        }finally{$zip.Dispose()}
    }finally{$stream.Dispose()}
}
function Test-QualificationSbom {
    [CmdletBinding()]param([Parameter(Mandatory)][string]$Bom,[Parameter(Mandatory)][string]$SchemaDirectory)
    # Resolve only the two exact official schema dependencies locally. No schema fetch is possible.
    $schemaFiles=@('bom-1.6.schema.json','spdx.schema.json','jsf-0.82.schema.json')
    $expected=@('3e92dddbc30cf7f6a02b80f0942b1a4cfd4fb1c26f1dfc4310afa9d613cafb93','baa9d3bd1ed57b6751b0887edead6b5063ff53ff7429cf85d476c6c94af0166e','8bae002c25e723db7ee1f26afde680ae1a2b1a8f6b4b4b0fd65dc3becb090aae')
    $schemas=@(for($i=0;$i -lt 3;$i++){
        $path=Join-Path $SchemaDirectory $schemaFiles[$i];Assert-QualificationPath $path
        if((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -cne $expected[$i]){throw 'QUAL_SBOM_SCHEMA_PIN'}
        Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($path))
    })
    function Resolve($node,[string]$prefix){
        if($node -is [Collections.IDictionary]){
            $null=$node.Remove('$id')
            if($node.Contains('$ref')){
                $reference=$node['$ref']
                if($reference.StartsWith('#/')){$node['$ref']='#/'+$prefix+$reference.Substring(2)}
                elseif($reference -ceq 'spdx.schema.json'){$node['$ref']='#/definitions/qualificationExternalSpdx'}
                elseif($reference -ceq 'jsf-0.82.schema.json'){$node['$ref']='#/definitions/qualificationExternalJsf'}
                elseif($reference -ceq 'jsf-0.82.schema.json#/definitions/signature'){$node['$ref']='#/definitions/qualificationExternalJsf/definitions/signature'}
                else{throw 'QUAL_SBOM_SCHEMA_EXTERNAL_REFERENCE'}
            }
            foreach($key in @($node.Keys)){Resolve $node[$key] $prefix}
        }elseif($node -is [array]){foreach($item in $node){Resolve $item $prefix}}
    }
    Resolve $schemas[0] ''
    Resolve $schemas[1] 'definitions/qualificationExternalSpdx/'
    Resolve $schemas[2] 'definitions/qualificationExternalJsf/'
    $schemas[0].definitions['qualificationExternalSpdx']=$schemas[1]
    $schemas[0].definitions['qualificationExternalJsf']=$schemas[2]
    $document=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($Bom))
    $schema=$schemas[0]|ConvertTo-Json -Depth 100 -Compress
    if(-not (Test-Json -Json ($document|ConvertTo-Json -Depth 24 -Compress) -Schema $schema -ErrorAction Stop)){
        throw 'QUAL_SBOM_SCHEMA_INVALID'
    }
    return $true
}
Export-ModuleMember -Function Read-QualificationJsonBytes,Assert-QualificationFields,Assert-QualificationPath,Assert-QualificationMemberName,Get-QualificationByteHash,Read-QualificationManifest,Test-QualificationPackage,Expand-QualificationPackage,Test-QualificationSbom

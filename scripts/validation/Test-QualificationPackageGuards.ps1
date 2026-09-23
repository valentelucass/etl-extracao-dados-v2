#Requires -Version 7.5
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$attempt=Join-Path $round ('package-guards-'+[Guid]::NewGuid().ToString('N'))
$null=[IO.Directory]::CreateDirectory($attempt)
$utf8=[Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText((Join-Path $attempt 'reservation.json'),(@{state='RESERVED';step='offline-package-adversaries';target=$attempt;limits='synthetic files; no subprocess JDBC or network; at most 40 archives';recovery='retain all artifacts'}|ConvertTo-Json),$utf8)
$payload=[ordered]@{}
1..12|ForEach-Object {$payload['content/file-'+$_+'.txt']=$utf8.GetBytes('synthetic-'+$_)}
$members=@(foreach($name in $payload.Keys){[ordered]@{path=$name;size=$payload[$name].Length;sha256=Get-QualificationByteHash $payload[$name];type='TEXT';role='DOCUMENTATION'}})
$manifest=[ordered]@{version='qualification-package-v1';revision=('a'*64);java=17;os='Windows';architecture='x64';schemaVersion=98;files=$members}
$manifestBytes=$utf8.GetBytes(($manifest|ConvertTo-Json -Depth 8))
$manifestHash=Get-QualificationByteHash $manifestBytes
$payload['package.json']=$manifestBytes;$payload['package.sha256']=$utf8.GetBytes($manifestHash+"`n")
function Archive([string]$name,[string]$extra='',[int]$attributes=0,[string]$drop='', [switch]$Tamper,[switch]$Duplicate,[switch]$Bomb){
    $file=Join-Path $attempt ($name+'.zip')
    $stream=[IO.File]::Open($file,[IO.FileMode]::CreateNew)
    $zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$false)
    try{
        foreach($entryName in $payload.Keys){
            if($entryName -ceq $drop){continue}
            $entry=$zip.CreateEntry($entryName)
            $out=$entry.Open()
            try{
                if($Tamper -and $entryName -ceq 'content/file-1.txt'){$out.Write($utf8.GetBytes('bad-data'))}
                else{$out.Write($payload[$entryName])}
            }finally{$out.Dispose()}
        }
        if($extra){
            $entry=$zip.CreateEntry($extra);$entry.ExternalAttributes=$attributes;$out=$entry.Open()
            try{if($Bomb){$out.Write([byte[]]::new(1048576))}else{$out.Write($utf8.GetBytes('synthetic'))}}finally{$out.Dispose()}
        }
        if($Duplicate){$entry=$zip.CreateEntry('content/file-1.txt');$out=$entry.Open();try{$out.Write($utf8.GetBytes('duplicate'))}finally{$out.Dispose()}}
    }finally{$zip.Dispose();$stream.Dispose()}
    return $file
}
$results=[Collections.Generic.List[object]]::new()
function Check([string]$name,[scriptblock]$action,[string]$reason){
    $observed='ACCEPTED'
    try{& $action|Out-Null}catch{$observed=$_.Exception.Message}
    $passed=$observed -ceq $reason
    $results.Add([ordered]@{case=$name;expected=$reason;observed=$observed;passed=$passed})
    if(-not $passed){throw ('QUAL_GUARD_FAILED_'+$name+'_GOT_'+$observed)}
}
function Expand([string]$file,[string]$name){
    Expand-QualificationPackage -Archive $file -ArchiveSha256 (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -Destination (Join-Path $attempt $name) -AllowedRoot $attempt
}
try{
    $valid=Archive 'valid'
    Check 'valid-envelope-extraction' {Expand $valid 'valid extraction ação'} 'ACCEPTED'
    Check 'reused-destination' {Expand $valid 'valid extraction ação'} 'QUAL_EXTRACT_DESTINATION'
    Check 'wrong-archive-pin' {Expand-QualificationPackage -Archive $valid -ArchiveSha256 ('0'*64) -Destination (Join-Path $attempt 'pin') -AllowedRoot $attempt} 'QUAL_ARCHIVE_PIN_OR_SIZE'
    foreach($name in @('../escape.txt','/absolute.txt','C:/absolute.txt','//server/share.txt','file.txt:stream','CON.txt','LPT1.txt','path./file.txt','content//gap.txt','content/./file.txt','a\b.txt','á.txt')){
        $case='path-'+$results.Count;$file=Archive $case $name
        Check $case {Expand $file ($case+'-out')} 'QUAL_PACKAGE_PATH'
    }
    $duplicate=Archive 'duplicate' -Duplicate
    Check 'duplicate' {Expand $duplicate 'duplicate-out'} 'QUAL_ARCHIVE_LINK_OR_DUPLICATE'
    $case=Archive 'case-collision' 'CONTENT/FILE-1.TXT'
    Check 'case-collision' {Expand $case 'case-out'} 'QUAL_ARCHIVE_LINK_OR_DUPLICATE'
    $link=Archive 'symlink' 'link.txt' -1610612736
    Check 'symlink' {Expand $link 'link-out'} 'QUAL_ARCHIVE_LINK_OR_DUPLICATE'
    $reparse=Archive 'reparse' 'link.txt' 0x400
    Check 'reparse' {Expand $reparse 'reparse-out'} 'QUAL_ARCHIVE_LINK_OR_DUPLICATE'
    $extra=Archive 'extra' 'extra.txt'
    Check 'extra' {Expand $extra 'extra-out'} 'QUAL_PACKAGE_CONTENT_SET'
    $missing=Archive 'missing' -drop 'content/file-1.txt'
    Check 'missing' {Expand $missing 'missing-out'} 'QUAL_ARCHIVE_MEMBER_LIMIT'
    $tamper=Archive 'tampered' -Tamper
    Check 'tampered' {Expand $tamper 'tamper-out'} 'QUAL_PACKAGE_MEMBER_HASH'
    $bomb=Archive 'bomb' 'bomb.txt' -Bomb
    Check 'compression-bomb' {Expand $bomb 'bomb-out'} 'QUAL_ARCHIVE_EXPANSION_LIMIT'
    Check 'duplicate-json' {Read-QualificationJsonBytes ($utf8.GetBytes('{"version":1,"version":2}'))} 'QUAL_JSON_DUPLICATE'
    $extracted=Join-Path $attempt 'valid extraction ação'
    [IO.File]::WriteAllText((Join-Path $extracted 'extra.txt'),'synthetic',$utf8)
    Check 'directory-extra' {Test-QualificationPackage -Directory $extracted -ManifestSha256 $manifestHash} 'QUAL_PACKAGE_CONTENT_SET'
    $state='PASS'
}catch{$state='FAILED';throw}finally{
    [IO.File]::WriteAllText((Join-Path $attempt 'result.json'),(@{state=$state;tests=$results;scope='package envelope integrity and extraction; not real payload or JDBC qualification'}|ConvertTo-Json -Depth 8),$utf8)
    @{state=$state;cases=$results.Count;attempt=$attempt}|ConvertTo-Json
}

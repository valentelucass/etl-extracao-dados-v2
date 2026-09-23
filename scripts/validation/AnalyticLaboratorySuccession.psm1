#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-AnalyticLaboratorySuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/macrobloco-analitico/'
    $history='docs/continuidade/historico/macrobloco-analitico/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    $qualification=$null
    $qualificationModule=Join-Path $PSScriptRoot 'QualificationLaboratorySuccession.psm1'
    if(Test-Path -LiteralPath $qualificationModule){
        Import-Module $qualificationModule -Force
        $qualification=Get-QualificationLaboratorySuccession -Root $Root -SelfTest:$SelfTest
    }
    $guidance='docs/continuidade/orientacao-entrada-unica/manifesto.json'
    $guidanceHash='a63cdfe48d17078ef5369d086a88cbf461df9e9624eaf1073b216b5626d58340'
    $expansion='docs/catalogos/macrobloco-expansao/manifesto.json'
    $expansionHash='c3318c87bf46bd63f43ec12e31b09b2ab4bee3e0a5a0b50fe319d68a22edc057'
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path){
        # A qualified successor preserves the exact analytic bytes; no path class is exempted.
        if($null -ne $qualification -and $qualification.map.ContainsKey($path)){
            $path=$qualification.map[$path].snapshot
        }
        if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'ANA_SUCCESSION_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $path)
        $file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'ANA_SUCCESSION_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'ANA_SUCCESSION_ESCAPE'}
        }
        return $file
    }
    function Digest([string]$path){(Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant()}
    function ReadPhysical([string]$path){[IO.File]::ReadAllText((Physical $path),$utf8)}
    function CheckHash([string]$path,[string]$hash){
        if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash){throw ('ANA_SUCCESSION_HASH_'+$path)}
    }
    function Exact($first,$second,[string]$reason){
        if(@($first).Count -ne @($second).Count -or
            (@($first|Sort-Object -Unique)-join '|') -cne (@($second|Sort-Object -Unique)-join '|')){throw $reason}
    }
    CheckHash $guidance $guidanceHash
    CheckHash $expansion $expansionHash
    $old=ReadPhysical $expansion|ConvertFrom-Json -Depth 50 -DateKind String
    $instruction=ReadPhysical $guidance|ConvertFrom-Json -Depth 50 -DateKind String
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($entry in $old.preservedFiles){$base.Add($entry.path,$entry.sha256)}
    foreach($entry in $old.changedExistingFiles){$base.Add($entry.path,$entry.after)}
    foreach($entry in $old.newFiles){$base.Add($entry.path,$entry.sha256)}
    $base.Add($expansion,$expansionHash)
    if((ReadPhysical 'docs/catalogos/macrobloco-expansao/manifesto.sha256').Trim() -cne $expansionHash){throw 'ANA_EXPANSION_SEAL'}
    $base.Add('docs/catalogos/macrobloco-expansao/manifesto.sha256',(Digest 'docs/catalogos/macrobloco-expansao/manifesto.sha256'))
    foreach($entry in $instruction.changedExistingFiles){
        if($base[$entry.path] -cne $entry.before){throw 'ANA_GUIDANCE_BEFORE'}
        $base[$entry.path]=$entry.after
    }
    foreach($entry in $instruction.newFiles){$base.Add($entry.path,$entry.sha256)}
    $base.Add($guidance,$guidanceHash)
    if((ReadPhysical 'docs/continuidade/orientacao-entrada-unica/manifesto.sha256').Trim() -cne $guidanceHash){throw 'ANA_GUIDANCE_SEAL'}
    $base.Add('docs/continuidade/orientacao-entrada-unica/manifesto.sha256',(Digest 'docs/continuidade/orientacao-entrada-unica/manifesto.sha256'))
    if($base.Count -ne 2704){throw 'ANA_SUCCESSION_INITIAL_INVENTORY'}
    $allowed=ReadPhysical ($catalog+'sucessao-arquivos-existentes.json')|ConvertFrom-Json -Depth 20
    function CheckManifest($manifest){
        if($manifest.version -ne 1 -or $manifest.initialFiles -ne 2704 -or
            $manifest.status -cnotin @('ANALYTIC_LOCAL_CANDIDATE','CONSTRUÇÃO_LOCAL_CONCLUÍDA') -or
            $manifest.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $manifest.syntheticOnly -or
            $manifest.remoteCalls -ne 0 -or $manifest.newAcceptances -ne 0 -or $manifest.durableDomainRows -ne 0 -or
            $manifest.operationalPromotionAuthorized -or $manifest.realQualificationAccepted){throw 'ANA_SUCCESSION_SCOPE'}
        if($manifest.predecessor.path -cne $guidance -or $manifest.predecessor.sha256 -cne $guidanceHash -or
            $manifest.requestSha256 -cne '990e481060eefd1c1ee01c168d46ba85c7d384edd5d61ce746ef9974d8d01a33'){throw 'ANA_SUCCESSION_PREDECESSOR'}
        Exact $manifest.changedExistingFiles.path $allowed.paths 'ANA_SUCCESSION_CHANGED_SET'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.changedExistingFiles){
            if(-not $base.ContainsKey($entry.path) -or $entry.before -cne $base[$entry.path] -or
                $entry.snapshot -cne ($history+$entry.path)){throw 'ANA_SUCCESSION_BEFORE'}
            CheckHash $entry.snapshot $entry.before
            CheckHash $entry.path $entry.after
            $map.Add($entry.path,$entry)
        }
        Exact $manifest.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'ANA_SUCCESSION_PRESERVED_SET'
        foreach($entry in $manifest.preservedFiles){
            if($entry.sha256 -cne $base[$entry.path]){throw 'ANA_SUCCESSION_PRESERVED'}
            CheckHash $entry.path $entry.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.newFiles){
            if($base.ContainsKey($entry.path) -or -not $seen.Add($entry.path)){throw 'ANA_SUCCESSION_NEW_COLLISION'}
            CheckHash $entry.path $entry.sha256
        }
        foreach($entry in $manifest.changedExistingFiles){
            if(-not $seen.Contains($entry.snapshot)){throw 'ANA_SUCCESSION_SNAPSHOT_UNLISTED'}
        }
        Push-Location -LiteralPath $Root
        try{
            $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|
                ForEach-Object {$_ -replace '\\','/'})
            if($LASTEXITCODE -ne 0){throw 'ANA_SUCCESSION_INVENTORY_FAILED'}
        }finally{Pop-Location}
        if($null -ne $qualification){
            # Compare the pinned historical universe after the successor validates current bytes.
            $actual=$qualification.baselineFiles
        }
        Exact $actual @(@($base.Keys)+@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'ANA_SUCCESSION_UNLISTED_DRIFT'
        CheckHash $manifest.checkpoint.path $manifest.checkpoint.sha256
        foreach($pointer in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $text=ReadPhysical $pointer
            if(-not $text.Contains($manifest.status) -or -not $text.Contains($manifest.checkpoint.sha256)){throw 'ANA_SUCCESSION_POINTER'}
        }
        return ,$map
    }
    CheckHash $manifestPath ((ReadPhysical ($catalog+'manifesto.sha256')).Trim())
    $manifest=ReadPhysical $manifestPath|ConvertFrom-Json -Depth 50 -DateKind String
    $map=CheckManifest $manifest
    $guards=0
    if($SelfTest){
        foreach($tuple in @(
            @('ANA_SUCCESSION_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
            @('ANA_SUCCESSION_SCOPE',{param($m)$m.remoteCalls=1}),
            @('ANA_SUCCESSION_SCOPE',{param($m)$m.target='OTHER'}),
            @('ANA_SUCCESSION_SCOPE',{param($m)$m.newAcceptances=1}),
            @('ANA_SUCCESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('ANA_SUCCESSION_CHANGED_SET',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
            @('ANA_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
            @('ANA_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('ANA_SUCCESSION_PRESERVED_SET',{param($m)$m.preservedFiles=$m.preservedFiles|Select-Object -Skip 1})
        )){
            $copy=$manifest|ConvertTo-Json -Depth 50|ConvertFrom-Json -Depth 50 -DateKind String
            & $tuple[1] $copy
            $reason='ACCEPTED'
            try{$null=CheckManifest $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $tuple[0]){throw ('ANA_SUCCESSION_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
            $guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $qualification){
        foreach($path in $qualification.map.Keys){
            $next=$qualification.map[$path]
            if($map.ContainsKey($path)){
                $previous=$map[$path]
                if($previous.after -cne $next.before){throw 'ANA_QUALIFICATION_CHAIN'}
                $map[$path]=[pscustomobject]@{path=$path;before=$previous.before;after=$next.after;snapshot=$previous.snapshot}
            }else{$map.Add($path,$next)}
        }
        $newFiles+=@($qualification.newFiles)
    }
    return [pscustomobject]@{map=$map;newFiles=$newFiles;qualification=$qualification;
        guards=$guards;status=$manifest.status;manifest=$manifest}
}

Export-ModuleMember -Function Get-AnalyticLaboratorySuccession

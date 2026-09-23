#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-QualificationLaboratorySuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/macrobloco-qualificacao-pacote/'
    $history='docs/continuidade/historico/macrobloco-qualificacao-pacote/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    $closure=$null
    $closureModule=Join-Path $PSScriptRoot 'ConstructionClosureSuccession.psm1'
    if(Test-Path -LiteralPath $closureModule){
        Import-Module $closureModule -Force
        $closure=Get-ConstructionClosureSuccession -Root $Root -SelfTest:$SelfTest
    }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $predecessor='docs/catalogos/macrobloco-analitico/manifesto.json'
    $predecessorHash='0389d441f3390f61206bc6ea3403cdb5001b2c32a1502bc98d884806e7a04d3f'
    $requestHash='33fa79950ac7c27ae9fb9efd07241d31101d8dd350a3f0e58c8876adaafecd59'
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path){
        if($null -ne $closure -and $closure.map.ContainsKey($path)){
            $path=$closure.map[$path].snapshot
        }
        if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'QUAL_SUCCESSION_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $path)
        $file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'QUAL_SUCCESSION_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'QUAL_SUCCESSION_ESCAPE'}
        }
        return $file
    }
    function Digest([string]$path){(Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant()}
    function ReadPhysical([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'QUAL_SUCCESSION_SIZE'}
        return [IO.File]::ReadAllText($file,$utf8)
    }
    function ReadJson([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'QUAL_SUCCESSION_SIZE'}
        return Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($file)) 8388608
    }
    function CheckHash([string]$path,[string]$hash){
        if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash){throw ('QUAL_SUCCESSION_HASH_'+$path)}
    }
    function Exact($first,$second,[string]$reason){
        if(@($first).Count -ne @($second).Count -or
            (@($first|Sort-Object -Unique)-join '|') -cne (@($second|Sort-Object -Unique)-join '|')){throw $reason}
    }
    CheckHash $predecessor $predecessorHash
    $old=ReadJson $predecessor
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($entry in $old.preservedFiles){$base.Add($entry.path,$entry.sha256)}
    foreach($entry in $old.changedExistingFiles){$base.Add($entry.path,$entry.after)}
    foreach($entry in $old.newFiles){$base.Add($entry.path,$entry.sha256)}
    $base.Add($predecessor,$predecessorHash)
    $oldSeal='docs/catalogos/macrobloco-analitico/manifesto.sha256'
    if((ReadPhysical $oldSeal).Trim() -cne $predecessorHash){throw 'QUAL_SUCCESSION_PREDECESSOR_SEAL'}
    $base.Add($oldSeal,(Digest $oldSeal))
    if($base.Count -ne 2988){throw 'QUAL_SUCCESSION_INITIAL_INVENTORY'}
    $allowed=ReadJson ($catalog+'sucessao-arquivos-existentes.json')
    Assert-QualificationFields $allowed @('version','predecessorSha256','paths')
    if($allowed.version -ne 1 -or $allowed.predecessorSha256 -cne $predecessorHash){throw 'QUAL_SUCCESSION_ALLOWED_ORIGIN'}

    function CheckManifest($manifest){
        Assert-QualificationFields $manifest @('version','status','initialFiles','target','syntheticOnly',
            'remoteCalls','newAcceptances','durableDomainRows','operationalPromotionAuthorized',
            'realQualificationAccepted','requestSha256','predecessor','checkpoint',
            'changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach($field in @('version','initialFiles','remoteCalls','newAcceptances','durableDomainRows')){
            if($manifest[$field] -isnot [long] -and $manifest[$field] -isnot [int]){throw 'QUAL_SUCCESSION_SCOPE'}
        }
        foreach($field in @('syntheticOnly','operationalPromotionAuthorized','realQualificationAccepted')){
            if($manifest[$field] -isnot [bool]){throw 'QUAL_SUCCESSION_SCOPE'}
        }
        if($manifest.version -ne 1 -or $manifest.initialFiles -ne 2988 -or
            $manifest.status -cnotin @('QUALIFICATION_LOCAL_CANDIDATE','CONSTRUÇÃO_LOCAL_CONCLUÍDA') -or
            $manifest.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $manifest.syntheticOnly -or
            $manifest.remoteCalls -ne 0 -or $manifest.newAcceptances -ne 0 -or $manifest.durableDomainRows -ne 0 -or
            $manifest.operationalPromotionAuthorized -or $manifest.realQualificationAccepted){throw 'QUAL_SUCCESSION_SCOPE'}
        Assert-QualificationFields $manifest.predecessor @('path','sha256')
        if($manifest.predecessor.path -cne $predecessor -or $manifest.predecessor.sha256 -cne $predecessorHash -or
            $manifest.requestSha256 -cne $requestHash){throw 'QUAL_SUCCESSION_PREDECESSOR'}
        Exact $manifest.changedExistingFiles.path $allowed.paths 'QUAL_SUCCESSION_CHANGED_SET'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.changedExistingFiles){
            Assert-QualificationFields $entry @('path','before','after','snapshot')
            if(-not $base.ContainsKey($entry.path) -or $entry.before -cne $base[$entry.path] -or
                $entry.snapshot -cne ($history+$entry.path)){throw 'QUAL_SUCCESSION_BEFORE'}
            CheckHash $entry.snapshot $entry.before
            CheckHash $entry.path $entry.after
            $map.Add($entry.path,$entry)
        }
        Exact $manifest.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'QUAL_SUCCESSION_PRESERVED_SET'
        foreach($entry in $manifest.preservedFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.sha256 -cne $base[$entry.path]){throw 'QUAL_SUCCESSION_PRESERVED'}
            CheckHash $entry.path $entry.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.newFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($base.ContainsKey($entry.path) -or -not $seen.Add($entry.path) -or
                $entry.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'QUAL_SUCCESSION_NEW_COLLISION'}
            CheckHash $entry.path $entry.sha256
        }
        foreach($entry in $manifest.changedExistingFiles){
            if(-not $seen.Contains($entry.snapshot)){throw 'QUAL_SUCCESSION_SNAPSHOT_UNLISTED'}
        }
        Push-Location -LiteralPath $Root
        try{
            $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|
                ForEach-Object {$_ -replace '\\','/'})
            if($LASTEXITCODE -ne 0){throw 'QUAL_SUCCESSION_INVENTORY_FAILED'}
        }finally{Pop-Location}
        if($null -ne $closure){
            $added=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
            foreach($path in $closure.newFiles){$null=$added.Add($path)}
            $actual=@($actual|Where-Object {-not $added.Contains($_)})
        }
        Exact $actual @(@($base.Keys)+@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'QUAL_SUCCESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $manifest.checkpoint @('path','sha256')
        if($manifest.checkpoint.path -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$'){
            throw 'QUAL_SUCCESSION_CHECKPOINT_PATH'
        }
        CheckHash $manifest.checkpoint.path $manifest.checkpoint.sha256
        foreach($pointer in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $text=ReadPhysical $pointer
            if(-not $text.Contains($manifest.status) -or -not $text.Contains($manifest.checkpoint.sha256)){
                throw 'QUAL_SUCCESSION_POINTER'
            }
        }
        foreach($entry in $manifest.evidence){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.path -cnotmatch '^target/macrobloco-qualificacao-pacote-20260913-01/[A-Za-z0-9_./-]+$' -or
                $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'QUAL_SUCCESSION_EVIDENCE_PATH'}
        }
        return ,$map
    }
    CheckHash $manifestPath ((ReadPhysical ($catalog+'manifesto.sha256')).Trim())
    $manifest=ReadJson $manifestPath
    $map=CheckManifest $manifest
    $guards=0
    if($SelfTest){
        foreach($tuple in @(
            @('QUAL_SUCCESSION_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
            @('QUAL_SUCCESSION_SCOPE',{param($m)$m.syntheticOnly='true'}),
            @('QUAL_SUCCESSION_SCOPE',{param($m)$m.remoteCalls=1}),
            @('QUAL_SUCCESSION_SCOPE',{param($m)$m.newAcceptances=1}),
            @('QUAL_SUCCESSION_SCOPE',{param($m)$m.target='OTHER'}),
            @('QUAL_SUCCESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('QUAL_SUCCESSION_PREDECESSOR',{param($m)$m.requestSha256='0'*64}),
            @('QUAL_SUCCESSION_CHANGED_SET',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
            @('QUAL_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
            @('QUAL_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('QUAL_SUCCESSION_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
            @('QUAL_JSON_MEMBERS',{param($m)$m.extra='UNDECLARED'})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 24 -Compress))) 8388608
            & $tuple[1] $copy
            $reason='ACCEPTED';try{$null=CheckManifest $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $tuple[0]){throw ('QUAL_SUCCESSION_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
            $guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $closure){
        foreach($path in $closure.map.Keys){
            $next=$closure.map[$path]
            if($map.ContainsKey($path)){
                $previous=$map[$path]
                if($previous.after -cne $next.before){throw 'QUAL_CLOSURE_CHAIN'}
                $map[$path]=[pscustomobject]@{path=$path;before=$previous.before;after=$next.after;snapshot=$previous.snapshot}
            }else{$map.Add($path,$next)}
        }
        $newFiles+=@($closure.newFiles)
    }
    return [pscustomobject]@{map=$map;newFiles=$newFiles;closure=$closure;
        guards=$guards;status=$manifest.status;manifest=$manifest}
}

Export-ModuleMember -Function Get-QualificationLaboratorySuccession

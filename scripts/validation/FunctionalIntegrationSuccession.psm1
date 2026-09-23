#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-FunctionalIntegrationSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/macrobloco-integracao-funcional/'
    $history='docs/continuidade/historico/macrobloco-integracao-funcional/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $predecessor='docs/catalogos/macrobloco-fechamento-construcao/manifesto.json'
    $predecessorHash='143e07b35f0df031b3a7d0ed99edbc2a7ecedfa48331faddf04dd40c1200fe5d'
    $requestHash='1639fb98c90b98d0d3fe23b5b1d066f2fb10a7d4f06350d1cb4967425e2eff6e'
    Import-Module (Join-Path $PSScriptRoot 'EntityAlignmentSuccession.psm1')
    $entities=Get-EntityAlignmentSuccession -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path){
        if($null -ne $entities -and $entities.map.ContainsKey($path)){$path=$entities.map[$path].snapshot}
        if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'FUNCTIONAL_SUCCESSION_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $path)
        $file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'FUNCTIONAL_SUCCESSION_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'FUNCTIONAL_SUCCESSION_ESCAPE'}
        }
        return $file
    }
    function Digest([string]$path){(Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant()}
    function ReadPhysical([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'FUNCTIONAL_SUCCESSION_SIZE'}
        return [IO.File]::ReadAllText($file,$utf8)
    }
    function ReadJson([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'FUNCTIONAL_SUCCESSION_SIZE'}
        return Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($file)) 8388608
    }
    function CheckHash([string]$path,[string]$hash){
        if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash){throw ('FUNCTIONAL_SUCCESSION_HASH_'+$path)}
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
    $oldSeal='docs/catalogos/macrobloco-fechamento-construcao/manifesto.sha256'
    if((ReadPhysical $oldSeal).Trim() -cne $predecessorHash){throw 'FUNCTIONAL_SUCCESSION_PREDECESSOR_SEAL'}
    $base.Add($oldSeal,(Digest $oldSeal))
    if($base.Count -ne 3190){throw 'FUNCTIONAL_SUCCESSION_INITIAL_INVENTORY'}
    $allowed=ReadJson ($catalog+'sucessao-arquivos-existentes.json')
    Assert-QualificationFields $allowed @('version','predecessorSha256','paths')
    if($allowed.version -ne 1 -or $allowed.predecessorSha256 -cne $predecessorHash){throw 'FUNCTIONAL_SUCCESSION_ALLOWED_ORIGIN'}

    function CheckManifest($manifest){
        Assert-QualificationFields $manifest @('version','status','initialFiles','target','syntheticOnly',
            'remoteCalls','newAcceptances','durableDomainRows','operationalPromotionAuthorized',
            'realQualificationAccepted','requestSha256','predecessor','checkpoint',
            'changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach($field in @('version','initialFiles','remoteCalls','newAcceptances','durableDomainRows')){
            if($manifest[$field] -isnot [long] -and $manifest[$field] -isnot [int]){throw 'FUNCTIONAL_SUCCESSION_SCOPE'}
        }
        foreach($field in @('syntheticOnly','operationalPromotionAuthorized','realQualificationAccepted')){
            if($manifest[$field] -isnot [bool]){throw 'FUNCTIONAL_SUCCESSION_SCOPE'}
        }
        if($manifest.version -ne 1 -or $manifest.initialFiles -ne 3190 -or
            $manifest.status -cnotin @('FUNCTIONAL_INTEGRATION_CANDIDATE','INTEGRACAO_FUNCIONAL_LOCAL_CONCLUIDA') -or
            $manifest.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $manifest.syntheticOnly -or
            $manifest.remoteCalls -ne 0 -or $manifest.newAcceptances -ne 0 -or $manifest.durableDomainRows -ne 0 -or
            $manifest.operationalPromotionAuthorized -or $manifest.realQualificationAccepted){throw 'FUNCTIONAL_SUCCESSION_SCOPE'}
        Assert-QualificationFields $manifest.predecessor @('path','sha256')
        if($manifest.predecessor.path -cne $predecessor -or $manifest.predecessor.sha256 -cne $predecessorHash -or
            $manifest.requestSha256 -cne $requestHash){throw 'FUNCTIONAL_SUCCESSION_PREDECESSOR'}
        Exact $manifest.changedExistingFiles.path $allowed.paths 'FUNCTIONAL_SUCCESSION_CHANGED_SET'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.changedExistingFiles){
            Assert-QualificationFields $entry @('path','before','after','snapshot')
            if(-not $base.ContainsKey($entry.path) -or $entry.before -cne $base[$entry.path] -or
                $entry.snapshot -cne ($history+$entry.path)){throw 'FUNCTIONAL_SUCCESSION_BEFORE'}
            if($entry.path.StartsWith('docs/continuidade/historico/',[StringComparison]::Ordinal) -or
                $entry.path -cmatch '/manifesto\.(json|sha256)$' -or
                $entry.path -cmatch '^docs/continuidade/checkpoints/0(?:0[0-9]{2}|1[0-3][0-9]|14[0-4])-'){
                throw 'FUNCTIONAL_SUCCESSION_IMMUTABLE_PREDECESSOR'
            }
            CheckHash $entry.snapshot $entry.before
            CheckHash $entry.path $entry.after
            $map.Add($entry.path,$entry)
        }
        Exact $manifest.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'FUNCTIONAL_SUCCESSION_PRESERVED_SET'
        foreach($entry in $manifest.preservedFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.sha256 -cne $base[$entry.path]){throw 'FUNCTIONAL_SUCCESSION_PRESERVED'}
            CheckHash $entry.path $entry.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.newFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($base.ContainsKey($entry.path) -or -not $seen.Add($entry.path) -or
                $entry.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'FUNCTIONAL_SUCCESSION_NEW_COLLISION'}
            CheckHash $entry.path $entry.sha256
        }
        foreach($entry in $manifest.changedExistingFiles){
            if(-not $seen.Contains($entry.snapshot)){throw 'FUNCTIONAL_SUCCESSION_SNAPSHOT_UNLISTED'}
        }
        Push-Location -LiteralPath $Root
        try{
            $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|
                ForEach-Object {$_ -replace '\\','/'})
            if($LASTEXITCODE -ne 0){throw 'FUNCTIONAL_SUCCESSION_INVENTORY_FAILED'}
        }finally{Pop-Location}
        if($null -ne $entities){$actual=$entities.baselineFiles}
        Exact $actual @(@($base.Keys)+@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'FUNCTIONAL_SUCCESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $manifest.checkpoint @('path','sha256')
        if($manifest.checkpoint.path -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$'){
            throw 'FUNCTIONAL_SUCCESSION_CHECKPOINT_PATH'
        }
        CheckHash $manifest.checkpoint.path $manifest.checkpoint.sha256
        foreach($pointer in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $text=ReadPhysical $pointer
            if(-not $text.Contains($manifest.status) -or -not $text.Contains($manifest.checkpoint.sha256)){
                throw 'FUNCTIONAL_SUCCESSION_POINTER'
            }
        }
        foreach($entry in $manifest.evidence){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.path -cnotmatch '^target/macrobloco-integracao-funcional-20260914-01/[A-Za-z0-9_./-]+$' -or
                $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'FUNCTIONAL_SUCCESSION_EVIDENCE_PATH'}
        }
        return ,$map
    }
    CheckHash $manifestPath ((ReadPhysical ($catalog+'manifesto.sha256')).Trim())
    $manifest=ReadJson $manifestPath
    $map=CheckManifest $manifest
    $guards=0
    if($SelfTest){
        foreach($tuple in @(
            @('FUNCTIONAL_SUCCESSION_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
            @('FUNCTIONAL_SUCCESSION_SCOPE',{param($m)$m.syntheticOnly='true'}),
            @('FUNCTIONAL_SUCCESSION_SCOPE',{param($m)$m.remoteCalls=1}),
            @('FUNCTIONAL_SUCCESSION_SCOPE',{param($m)$m.newAcceptances=1}),
            @('FUNCTIONAL_SUCCESSION_SCOPE',{param($m)$m.target='OTHER'}),
            @('FUNCTIONAL_SUCCESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('FUNCTIONAL_SUCCESSION_PREDECESSOR',{param($m)$m.requestSha256='0'*64}),
            @('FUNCTIONAL_SUCCESSION_CHANGED_SET',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
            @('FUNCTIONAL_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
            @('FUNCTIONAL_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('FUNCTIONAL_SUCCESSION_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
            @('QUAL_JSON_MEMBERS',{param($m)$m.extra='UNDECLARED'})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 24 -Compress))) 8388608
            & $tuple[1] $copy
            $reason='ACCEPTED';try{$null=CheckManifest $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $tuple[0]){throw ('FUNCTIONAL_SUCCESSION_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
            $guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $entities){
        foreach($entry in $entities.map.Values){
            if($map.ContainsKey($entry.path)){
                $previous=$map[$entry.path]
                if($previous.after -cne $entry.before){throw 'FUNCTIONAL_ENTITY_CHAIN_BREAK'}
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            }else{$map.Add($entry.path,$entry)}
        }
        $newFiles=@(@($newFiles)+@($entities.newFiles)|Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;newFiles=$newFiles;successor=$entities;
        guards=$guards;status=$manifest.status;manifest=$manifest;baselineFiles=@($base.Keys)}
}

Export-ModuleMember -Function Get-FunctionalIntegrationSuccession

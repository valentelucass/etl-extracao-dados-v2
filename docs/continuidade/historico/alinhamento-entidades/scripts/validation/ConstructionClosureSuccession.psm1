#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-ConstructionClosureSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/macrobloco-fechamento-construcao/'
    $history='docs/continuidade/historico/macrobloco-fechamento-construcao/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $predecessor='docs/catalogos/macrobloco-qualificacao-pacote/manifesto.json'
    $predecessorHash='9eb1784292288fad57d5b636b36b8569ffaf3657fc154a37391b378733f652ce'
    $requestHash='c12924463dfb0439b933e4a12288d36b772207c1972294f894d8c4ba773e23e1'
    Import-Module (Join-Path $PSScriptRoot 'FunctionalIntegrationSuccession.psm1')
    $functional=Get-FunctionalIntegrationSuccession -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path){
        if($null -ne $functional -and $functional.map.ContainsKey($path)){$path=$functional.map[$path].snapshot}
        if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'CLOSURE_SUCCESSION_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $path)
        $file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'CLOSURE_SUCCESSION_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'CLOSURE_SUCCESSION_ESCAPE'}
        }
        return $file
    }
    function Digest([string]$path){(Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant()}
    function ReadPhysical([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'CLOSURE_SUCCESSION_SIZE'}
        return [IO.File]::ReadAllText($file,$utf8)
    }
    function ReadJson([string]$path){
        $file=Physical $path
        if((Get-Item -LiteralPath $file).Length -gt 8388608){throw 'CLOSURE_SUCCESSION_SIZE'}
        return Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($file)) 8388608
    }
    function CheckHash([string]$path,[string]$hash){
        if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash){throw ('CLOSURE_SUCCESSION_HASH_'+$path)}
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
    $oldSeal='docs/catalogos/macrobloco-qualificacao-pacote/manifesto.sha256'
    if((ReadPhysical $oldSeal).Trim() -cne $predecessorHash){throw 'CLOSURE_SUCCESSION_PREDECESSOR_SEAL'}
    $base.Add($oldSeal,(Digest $oldSeal))
    if($base.Count -ne 3158){throw 'CLOSURE_SUCCESSION_INITIAL_INVENTORY'}
    $allowed=ReadJson ($catalog+'sucessao-arquivos-existentes.json')
    Assert-QualificationFields $allowed @('version','predecessorSha256','paths')
    if($allowed.version -ne 1 -or $allowed.predecessorSha256 -cne $predecessorHash){throw 'CLOSURE_SUCCESSION_ALLOWED_ORIGIN'}

    function CheckManifest($manifest){
        Assert-QualificationFields $manifest @('version','status','initialFiles','target','syntheticOnly',
            'remoteCalls','newAcceptances','durableDomainRows','operationalPromotionAuthorized',
            'realQualificationAccepted','requestSha256','predecessor','checkpoint',
            'changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach($field in @('version','initialFiles','remoteCalls','newAcceptances','durableDomainRows')){
            if($manifest[$field] -isnot [long] -and $manifest[$field] -isnot [int]){throw 'CLOSURE_SUCCESSION_SCOPE'}
        }
        foreach($field in @('syntheticOnly','operationalPromotionAuthorized','realQualificationAccepted')){
            if($manifest[$field] -isnot [bool]){throw 'CLOSURE_SUCCESSION_SCOPE'}
        }
        if($manifest.version -ne 1 -or $manifest.initialFiles -ne 3158 -or
            $manifest.status -cnotin @('CLOSURE_LOCAL_CANDIDATE','AUDITORIA_E_CORRECOES_LOCAIS_CONCLUIDAS') -or
            $manifest.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or -not $manifest.syntheticOnly -or
            $manifest.remoteCalls -ne 0 -or $manifest.newAcceptances -ne 0 -or $manifest.durableDomainRows -ne 0 -or
            $manifest.operationalPromotionAuthorized -or $manifest.realQualificationAccepted){throw 'CLOSURE_SUCCESSION_SCOPE'}
        Assert-QualificationFields $manifest.predecessor @('path','sha256')
        if($manifest.predecessor.path -cne $predecessor -or $manifest.predecessor.sha256 -cne $predecessorHash -or
            $manifest.requestSha256 -cne $requestHash){throw 'CLOSURE_SUCCESSION_PREDECESSOR'}
        Exact $manifest.changedExistingFiles.path $allowed.paths 'CLOSURE_SUCCESSION_CHANGED_SET'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.changedExistingFiles){
            Assert-QualificationFields $entry @('path','before','after','snapshot')
            if(-not $base.ContainsKey($entry.path) -or $entry.before -cne $base[$entry.path] -or
                $entry.snapshot -cne ($history+$entry.path)){throw 'CLOSURE_SUCCESSION_BEFORE'}
            CheckHash $entry.snapshot $entry.before
            CheckHash $entry.path $entry.after
            $map.Add($entry.path,$entry)
        }
        Exact $manifest.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'CLOSURE_SUCCESSION_PRESERVED_SET'
        foreach($entry in $manifest.preservedFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.sha256 -cne $base[$entry.path]){throw 'CLOSURE_SUCCESSION_PRESERVED'}
            CheckHash $entry.path $entry.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($entry in $manifest.newFiles){
            Assert-QualificationFields $entry @('path','sha256')
            if($base.ContainsKey($entry.path) -or -not $seen.Add($entry.path) -or
                $entry.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'CLOSURE_SUCCESSION_NEW_COLLISION'}
            CheckHash $entry.path $entry.sha256
        }
        foreach($entry in $manifest.changedExistingFiles){
            if(-not $seen.Contains($entry.snapshot)){throw 'CLOSURE_SUCCESSION_SNAPSHOT_UNLISTED'}
        }
        Push-Location -LiteralPath $Root
        try{
            $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|
                ForEach-Object {$_ -replace '\\','/'})
            if($LASTEXITCODE -ne 0){throw 'CLOSURE_SUCCESSION_INVENTORY_FAILED'}
        }finally{Pop-Location}
        # The successor already verified every current byte and the exact expanded inventory.
        # This check still proves the predecessor's exact3190-file inventory, using its snapshots.
        if($null -ne $functional){$actual=$functional.baselineFiles}
        Exact $actual @(@($base.Keys)+@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'CLOSURE_SUCCESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $manifest.checkpoint @('path','sha256')
        if($manifest.checkpoint.path -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$'){
            throw 'CLOSURE_SUCCESSION_CHECKPOINT_PATH'
        }
        CheckHash $manifest.checkpoint.path $manifest.checkpoint.sha256
        foreach($pointer in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $text=ReadPhysical $pointer
            if(-not $text.Contains($manifest.status) -or -not $text.Contains($manifest.checkpoint.sha256)){
                throw 'CLOSURE_SUCCESSION_POINTER'
            }
        }
        foreach($entry in $manifest.evidence){
            Assert-QualificationFields $entry @('path','sha256')
            if($entry.path -cnotmatch '^target/macrobloco-fechamento-construcao-20260913-01/[A-Za-z0-9_./-]+$' -or
                $entry.path.Contains('..') -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'CLOSURE_SUCCESSION_EVIDENCE_PATH'}
        }
        return ,$map
    }
    CheckHash $manifestPath ((ReadPhysical ($catalog+'manifesto.sha256')).Trim())
    $manifest=ReadJson $manifestPath
    $map=CheckManifest $manifest
    $guards=0
    if($SelfTest){
        foreach($tuple in @(
            @('CLOSURE_SUCCESSION_SCOPE',{param($m)$m.operationalPromotionAuthorized=$true}),
            @('CLOSURE_SUCCESSION_SCOPE',{param($m)$m.syntheticOnly='true'}),
            @('CLOSURE_SUCCESSION_SCOPE',{param($m)$m.remoteCalls=1}),
            @('CLOSURE_SUCCESSION_SCOPE',{param($m)$m.newAcceptances=1}),
            @('CLOSURE_SUCCESSION_SCOPE',{param($m)$m.target='OTHER'}),
            @('CLOSURE_SUCCESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('CLOSURE_SUCCESSION_PREDECESSOR',{param($m)$m.requestSha256='0'*64}),
            @('CLOSURE_SUCCESSION_CHANGED_SET',{param($m)$m.changedExistingFiles[0].path='unreviewed.sql'}),
            @('CLOSURE_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
            @('CLOSURE_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('CLOSURE_SUCCESSION_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
            @('QUAL_JSON_MEMBERS',{param($m)$m.extra='UNDECLARED'})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 24 -Compress))) 8388608
            & $tuple[1] $copy
            $reason='ACCEPTED';try{$null=CheckManifest $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $tuple[0]){throw ('CLOSURE_SUCCESSION_GUARD_'+$tuple[0]+'_GOT_'+$reason)}
            $guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $functional){
        foreach($entry in $functional.map.Values){
            if($map.ContainsKey($entry.path)){
                $previous=$map[$entry.path]
                if($previous.after -cne $entry.before){throw 'CLOSURE_FUNCTIONAL_CHAIN_BREAK'}
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            }else{$map.Add($entry.path,$entry)}
        }
        $newFiles=@(@($newFiles)+@($functional.newFiles)|Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;newFiles=$newFiles;successor=$functional;
        guards=$guards;status=$manifest.status;manifest=$manifest}
}

Export-ModuleMember -Function Get-ConstructionClosureSuccession

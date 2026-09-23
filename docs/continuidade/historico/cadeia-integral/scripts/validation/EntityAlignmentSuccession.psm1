#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-EntityAlignmentSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/alinhamento-entidades/'
    $history='docs/continuidade/historico/alinhamento-entidades/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $predecessor='docs/catalogos/macrobloco-integracao-funcional/manifesto.json'
    $predecessorHash='ffc6701328e046abdaa35533a603a78103b8914917a0c50b85c1c6e0e1965e4d'
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path){
        if($path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/')){throw 'ENTITY_SUCCESSION_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $path)
        $file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'ENTITY_SUCCESSION_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'ENTITY_SUCCESSION_ESCAPE'}
        }
        return $file
    }
    function Digest([string]$path){(Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant()}
    function ReadJson([string]$path){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608}
    function CheckHash([string]$path,[string]$hash){if($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash){throw ('ENTITY_SUCCESSION_HASH_'+$path)}}
    function Exact($a,$b,[string]$reason){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -Unique)-join '|') -cne (@($b|Sort-Object -Unique)-join '|')){throw $reason}}
    CheckHash $predecessor $predecessorHash
    $old=ReadJson $predecessor
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($predecessor,$predecessorHash)
    $oldSeal='docs/catalogos/macrobloco-integracao-funcional/manifesto.sha256'
    if([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $predecessorHash){throw 'ENTITY_SUCCESSION_PREDECESSOR_SEAL'}
    $base.Add($oldSeal,(Digest $oldSeal))
    if($base.Count -ne 3308){throw 'ENTITY_SUCCESSION_BASE'}
    function CheckManifest($m){
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','target','sourceCalls','newAcceptances','durableDomainRows','construction','historicalAcceptances','checkpoint','changedExistingFiles','preservedFiles','newFiles')
        foreach($field in @('version','initialFiles','sourceCalls','newAcceptances','durableDomainRows')){
            if($m[$field] -isnot [long] -and $m[$field] -isnot [int]){throw 'ENTITY_SUCCESSION_SCOPE'}
        }
        if($m.version -ne 1 -or $m.initialFiles -ne 3308 -or $m.status -cne 'CONTRATOS_ENTIDADES_CORRECOES_LOCAIS_CONCLUIDAS' -or $m.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $m.sourceCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.durableDomainRows -ne 0 -or $m.construction -cne '39/45' -or $m.historicalAcceptances -cne '67/115'){throw 'ENTITY_SUCCESSION_SCOPE'}
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if($m.predecessor.path -cne $predecessor -or $m.predecessor.sha256 -cne $predecessorHash){throw 'ENTITY_SUCCESSION_PREDECESSOR'}
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedExistingFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if(-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history+$e.path)){throw 'ENTITY_SUCCESSION_BEFORE'}
            if($e.path -cmatch '^docs/continuidade/(historico|checkpoints)/' -or $e.path -cmatch '/manifesto\.(json|sha256)$' -or $e.path -cmatch '^database/migrations/'){throw 'ENTITY_SUCCESSION_IMMUTABLE_PREDECESSOR'}
            CheckHash $e.snapshot $e.before
            if($null -eq $e.after){
                if($e.path -cnotmatch '^src/(main|test)/java/br/com/esl/etl/v2/modulos/(coletas|fretes|localizacaocargas|manifestos)/aplicacao/[A-Za-z]+\.java$' -or (Test-Path -LiteralPath (Join-Path $Root $e.path))){throw 'ENTITY_SUCCESSION_REMOVAL'}
            }else{CheckHash $e.path $e.after}
            $map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'ENTITY_SUCCESSION_PRESERVED_SET'
        foreach($e in $m.preservedFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($e.sha256 -cne $base[$e.path]){throw 'ENTITY_SUCCESSION_PRESERVED'}
            CheckHash $e.path $e.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($e in $m.newFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'ENTITY_SUCCESSION_NEW_COLLISION'}
            CheckHash $e.path $e.sha256
        }
        foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'ENTITY_SUCCESSION_SNAPSHOT_UNLISTED'}}
        $deleted=@($m.changedExistingFiles|Where-Object {$null -eq $_.after}|ForEach-Object {$_.path})
        Push-Location -LiteralPath $Root
        try{$actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|ForEach-Object {$_ -replace '\\','/'});if($LASTEXITCODE -ne 0){throw 'ENTITY_SUCCESSION_ENUMERATION'}}finally{Pop-Location}
        Exact $actual @(@($base.Keys|Where-Object {$_ -cnotin $deleted})+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'ENTITY_SUCCESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $m.checkpoint @('path','sha256')
        if($m.checkpoint.path -cne 'docs/continuidade/checkpoints/0154-contratos-entidades-e-correcoes-basicas.md'){throw 'ENTITY_SUCCESSION_CHECKPOINT'}
        CheckHash $m.checkpoint.path $m.checkpoint.sha256
        foreach($p in @('STATES.md','docs/continuidade/RETOMADA.md','docs/runbooks/trilha-de-chats-gpt-5-6.md')){
            $text=[IO.File]::ReadAllText((Physical $p),$utf8)
            if(-not $text.Contains($m.status) -or -not $text.Contains($m.checkpoint.sha256)){throw 'ENTITY_SUCCESSION_POINTER'}
        }
        return ,$map
    }
    CheckHash $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest=ReadJson $manifestPath
    $map=CheckManifest $manifest
    $guards=0
    if($SelfTest){
        foreach($test in @(
            @('ENTITY_SUCCESSION_SCOPE',{param($m)$m.sourceCalls=1}),
            @('ENTITY_SUCCESSION_SCOPE',{param($m)$m.sourceCalls=$false}),
            @('ENTITY_SUCCESSION_SCOPE',{param($m)$m.construction='40/45'}),
            @('ENTITY_SUCCESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('ENTITY_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].before='0'*64}),
            @('ENTITY_SUCCESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('ENTITY_SUCCESSION_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason='ACCEPTED';try{$null=CheckManifest $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('ENTITY_SUCCESSION_GUARD_'+$reason)};$guards++
        }
    }
    return [pscustomobject]@{map=$map;newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'));deletedFiles=@($manifest.changedExistingFiles|Where-Object {$null -eq $_.after}|ForEach-Object {$_.path});baselineFiles=@($base.Keys);manifest=$manifest;guards=$guards}
}
Export-ModuleMember -Function Get-EntityAlignmentSuccession

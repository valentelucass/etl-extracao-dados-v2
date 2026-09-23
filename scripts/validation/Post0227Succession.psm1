#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-StaticAnalysisSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/continuidade/avanco-seguranca/'
    $history='docs/continuidade/historico/avanco-seguranca/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    Import-Module (Join-Path $PSScriptRoot 'QualificacaoP0733Succession.psm1')
    $successor=Get-QualificacaoP0733Succession -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    # A newer boundary protects current bytes before this historical revision can resolve snapshots.
    function RawPhysical([string]$p){
        if($null -ne $successor -and $successor.map.ContainsKey($p)){$p=$successor.map[$p].snapshot}
        if($p -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $p.Contains('..') -or $p.StartsWith('/') -or
            $p -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$'){throw 'STATIC_ADVANCE_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $p);$file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'STATIC_ADVANCE_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'STATIC_ADVANCE_ESCAPE'}
        }
        return $file
    }
    function RawHash([string]$p){(Get-FileHash -LiteralPath (RawPhysical $p)).Hash.ToLowerInvariant()}
    function RawJson([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((RawPhysical $p))) 8388608}
    function RawPin([string]$p,[string]$sha){if($sha -cnotmatch '^[a-f0-9]{64}$' -or (RawHash $p) -cne $sha){throw ('STATIC_ADVANCE_HASH_'+$p)}}
    function Exact($a,$b,[string]$code){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $code}}
    $oldPath='docs/continuidade/entrega-p09-p33/manifesto.json'
    $oldHash='45e8524f8a7d2d67601e844917176ed1542c9452a6ce6a6d8af891707fcdb265'
    RawPin $oldPath $oldHash
    $oldSeal=$oldPath.Replace('.json','.sha256')
    if([IO.File]::ReadAllText((RawPhysical $oldSeal),$utf8).Trim() -cne $oldHash){throw 'STATIC_ADVANCE_OLD_SEAL'}
    $old=RawJson $oldPath
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($oldPath,$oldHash);$base.Add($oldSeal,(RawHash $oldSeal))
    Push-Location -LiteralPath $Root
    try{
        $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object {$_ -replace '\\','/'})
        if($LASTEXITCODE -ne 0){throw 'STATIC_ADVANCE_ENUMERATION'}
    }finally{Pop-Location}
    function AssertStaticEvidence($result){
        if($result.status -cne 'LOCAL_STATIC_ANALYSIS_ADVANCE' -or $result.sourceCalls -ne 0 -or
            $result.databaseUsed -isnot [bool] -or $result.databaseUsed -or $result.newAcceptances -ne 0 -or
            $result.construction -cne '39/45' -or $result.historicalAcceptances -cne '67/115' -or
            $result.runtimeChanged -isnot [bool] -or -not $result.runtimeChanged -or
            $result.currentP07Qualified -isnot [bool] -or $result.currentP07Qualified -or
            $result.currentP08Qualified -isnot [bool] -or $result.currentP08Qualified){throw 'STATIC_ADVANCE_EVIDENCE_SCOPE'}
    }
    function CheckCurrent($m,$observed=$actual){
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseUsed','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach($field in @('version','initialFiles','sourceCalls','newAcceptances')){
            if($m[$field] -isnot [long] -and $m[$field] -isnot [int]){throw 'STATIC_ADVANCE_SCOPE'}
        }
        if($m.version -ne 1 -or $m.initialFiles -ne 3811 -or $base.Count -ne 3811 -or
            $m.status -cne 'LOCAL_STATIC_ANALYSIS_ADVANCE' -or $m.sourceCalls -ne 0 -or
            $m.databaseUsed -isnot [bool] -or $m.databaseUsed -or $m.newAcceptances -ne 0 -or
            $m.construction -cne '39/45' -or $m.historicalAcceptances -cne '67/115'){throw 'STATIC_ADVANCE_SCOPE'}
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash){throw 'STATIC_ADVANCE_PREDECESSOR'}
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','src/main/java/br/com/esl/etl/v2/bootstrap/QualificationConcurrency.java','src/main/java/br/com/esl/etl/v2/bootstrap/LocalArtifactScenario.java','src/main/java/br/com/esl/etl/v2/bootstrap/QualificationTemporalMatrix.java','src/main/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/ColetaTemporalLaboratorySession.java','src/test/java/br/com/esl/etl/v2/plataforma/persistencia/coletas/ColetaTemporalLaboratorySessionCloseTest.java','src/main/java/br/com/esl/etl/v2/plataforma/persistencia/controle/BoundedRuntimeDataSource.java','src/test/java/br/com/esl/etl/v2/plataforma/persistencia/controle/RuntimeJdbcBoundariesTest.java','src/main/java/br/com/esl/etl/v2/plataforma/persistencia/analitico/JdbcRasterBatch.java','src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportHttpExecutor.java','src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportHttpExecutorTest.java','scripts/validation/Post0227Succession.psm1') 'STATIC_ADVANCE_DELTA_SET'
        $revisionFiles=if($null -ne $successor){@($observed|Where-Object {$_ -cnotin $successor.newFiles})}else{$observed}
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'STATIC_ADVANCE_UNLISTED_DRIFT'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedExistingFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if(-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or
                $e.snapshot -cne ($history+$e.path)){throw 'STATIC_ADVANCE_BEFORE'}
            RawPin $e.snapshot $e.before
            if($null -eq $e.after){throw 'STATIC_ADVANCE_REMOVAL'}
            RawPin $e.path $e.after;$map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'STATIC_ADVANCE_PRESERVED_SET'
        foreach($e in $m.preservedFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($e.sha256 -cne $base[$e.path]){throw 'STATIC_ADVANCE_PRESERVED'}
            RawPin $e.path $e.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $checkpoints=@('docs/continuidade/checkpoints/0236-avanco-seguranca-local.md','scripts/security/Invoke-LocalStaticAnalysis.ps1','scripts/security/Test-LocalStaticAnalysis.ps1','scripts/security/LocalStaticAnalysis.psm1','scripts/security/pmd-security.xml','scripts/security/README-static-analysis.md','src/test/java/br/com/esl/etl/v2/bootstrap/QualificationConcurrencyTest.java','src/main/java/br/com/esl/etl/v2/bootstrap/SavepointScope.java','src/test/java/br/com/esl/etl/v2/bootstrap/SavepointScopeTest.java','src/test/java/br/com/esl/etl/v2/plataforma/persistencia/analitico/JdbcRasterBatchTest.java')
        foreach($e in $m.newFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'STATIC_ADVANCE_NEW_COLLISION'}
            if(-not $e.path.StartsWith($catalog,[StringComparison]::Ordinal) -and
                $e.path -cnotin $m.changedExistingFiles.snapshot -and $e.path -cnotin $checkpoints){throw 'STATIC_ADVANCE_NEW_SCOPE'}
            RawPin $e.path $e.sha256
        }
        foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'STATIC_ADVANCE_SNAPSHOT_UNLISTED'}}
        Assert-QualificationFields $m.evidence @('path','sha256')
        if($m.evidence.path -cne ($catalog+'resultado.json')){throw 'STATIC_ADVANCE_EVIDENCE_PATH'}
        RawPin $m.evidence.path $m.evidence.sha256
        $result=RawJson $m.evidence.path
        AssertStaticEvidence $result
        foreach($e in $result.receipts){Assert-QualificationFields $e @('path','sha256');RawPin $e.path $e.sha256}
        return ,$map
    }
    RawPin $manifestPath ([IO.File]::ReadAllText((RawPhysical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest=RawJson $manifestPath;$map=CheckCurrent $manifest;$guards=0
    if($SelfTest){
        foreach($test in @(
            @('STATIC_ADVANCE_SCOPE',{param($m)$m.sourceCalls=1}),
            @('STATIC_ADVANCE_SCOPE',{param($m)$m.sourceCalls='0'}),
            @('STATIC_ADVANCE_SCOPE',{param($m)$m.databaseUsed=$true}),
            @('STATIC_ADVANCE_SCOPE',{param($m)$m.newAcceptances=1}),
            @('STATIC_ADVANCE_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('STATIC_ADVANCE_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('STATIC_ADVANCE_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='src/main/resources/logback.xml'}),
            @('STATIC_ADVANCE_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('STATIC_ADVANCE_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('STATIC_ADVANCE_PRESERVED',{param($m)($m.preservedFiles|Where-Object path -ceq 'src/main/resources/logback.xml').sha256='0'*64}),
            @('STATIC_ADVANCE_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
            @(('STATIC_ADVANCE_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy;$reason='ACCEPTED';try{$null=CheckCurrent $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('STATIC_ADVANCE_GUARD_'+$reason)};$guards++
        }
        $reason='ACCEPTED';try{$null=CheckCurrent $manifest @($actual|Where-Object {$_ -cne 'src/main/resources/logback.xml'})}catch{$reason=$_.Exception.Message}
        if($reason -cne 'STATIC_ADVANCE_UNLISTED_DRIFT'){throw ('STATIC_ADVANCE_GUARD_'+$reason)};$guards++
        foreach($path in @('src/main/resources/undeclared-runtime.json',($catalog+'../outside.json'))){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
            $copy.newFiles+=@{path=$path;sha256='0'*64}
            $reason='ACCEPTED';try{$null=CheckCurrent $copy @(@($actual)+@($path))}catch{$reason=$_.Exception.Message}
            $expected=if($path.StartsWith($catalog,[StringComparison]::Ordinal)){'STATIC_ADVANCE_PATH'}else{'STATIC_ADVANCE_NEW_SCOPE'}
            if($reason -cne $expected){throw ('STATIC_ADVANCE_GUARD_'+$reason)};$guards++
        }
    }
    if($SelfTest){
        foreach($field in @('runtimeChanged','currentP07Qualified','currentP08Qualified')){
            $copy=RawJson $manifest.evidence.path
            $copy[$field]=($field -cne 'runtimeChanged')
            $reason='ACCEPTED';try{AssertStaticEvidence $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne 'STATIC_ADVANCE_EVIDENCE_SCOPE'){throw ('STATIC_ADVANCE_GUARD_'+$reason)};$guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $successor){
        foreach($entry in $successor.map.Values){
            if($map.ContainsKey($entry.path)){
                $previous=$map[$entry.path]
                if($previous.after -cne $entry.before){throw 'QP0733_CHAIN_BREAK'}
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            }else{$map.Add($entry.path,$entry)}
        }
        $newFiles=@(@($newFiles)+@($successor.newFiles)|Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;runtimeChanged=$true;currentP07Qualified=($null -ne $successor -and $successor.currentP07Qualified);currentP08Qualified=($null -ne $successor -and $successor.currentP08Qualified);successor=$successor}
}

function Get-P09P33DocumentarySuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/continuidade/entrega-p09-p33/'
    $history='docs/continuidade/historico/entrega-p09-p33/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $successor=Get-StaticAnalysisSuccession -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    # A later validated revision supplies exact snapshots; without it these remain current bytes.
    function RawPhysical([string]$p){
        if($null -ne $successor -and $successor.map.ContainsKey($p)){$p=$successor.map[$p].snapshot}
        if($p -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $p.Contains('..') -or $p.StartsWith('/') -or
            $p -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$'){throw 'P09_P33_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $p);$file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'P09_P33_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'P09_P33_ESCAPE'}
        }
        return $file
    }
    function RawHash([string]$p){(Get-FileHash -LiteralPath (RawPhysical $p)).Hash.ToLowerInvariant()}
    function RawJson([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((RawPhysical $p))) 8388608}
    function RawPin([string]$p,[string]$sha){if($sha -cnotmatch '^[a-f0-9]{64}$' -or (RawHash $p) -cne $sha){throw ('P09_P33_HASH_'+$p)}}
    function Exact($a,$b,[string]$code){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $code}}
    $oldPath='docs/catalogos/requalificacao-pos0227/manifesto.json'
    $oldHash='60ce9eea1154282b947aa7a6d51d8b51b44ac58fcafe3d2405e160e0f2fe9231'
    RawPin $oldPath $oldHash
    $oldSeal=$oldPath.Replace('.json','.sha256')
    if([IO.File]::ReadAllText((RawPhysical $oldSeal),$utf8).Trim() -cne $oldHash){throw 'P09_P33_OLD_SEAL'}
    $old=RawJson $oldPath
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($oldPath,$oldHash);$base.Add($oldSeal,(RawHash $oldSeal))
    Push-Location -LiteralPath $Root
    try{
        $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object {$_ -replace '\\','/'})
        if($LASTEXITCODE -ne 0){throw 'P09_P33_ENUMERATION'}
    }finally{Pop-Location}
    function CheckCurrent($m,$observed=$actual){
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseUsed','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach($field in @('version','initialFiles','sourceCalls','newAcceptances')){
            if($m[$field] -isnot [long] -and $m[$field] -isnot [int]){throw 'P09_P33_SCOPE'}
        }
        if($m.version -ne 1 -or $m.initialFiles -ne 3798 -or $base.Count -ne 3798 -or
            $m.status -cne 'DOCUMENTARY_SUCCESSION_P09_P33' -or $m.sourceCalls -ne 0 -or
            $m.databaseUsed -isnot [bool] -or $m.databaseUsed -or $m.newAcceptances -ne 0 -or
            $m.construction -cne '39/45' -or $m.historicalAcceptances -cne '67/115'){throw 'P09_P33_SCOPE'}
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash){throw 'P09_P33_PREDECESSOR'}
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','docs/catalogos/macrobloco-qualificacao-pacote/PACKAGE-README.md','scripts/validation/Post0227Succession.psm1') 'P09_P33_DELTA_SET'
        $revisionFiles=if($null -ne $successor){@($observed|Where-Object {$_ -cnotin $successor.newFiles})}else{$observed}
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'P09_P33_UNLISTED_DRIFT'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedExistingFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if(-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or
                $e.snapshot -cne ($history+$e.path)){throw 'P09_P33_BEFORE'}
            RawPin $e.snapshot $e.before
            if($null -eq $e.after){throw 'P09_P33_REMOVAL'}
            RawPin $e.path $e.after;$map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'P09_P33_PRESERVED_SET'
        foreach($e in $m.preservedFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($e.sha256 -cne $base[$e.path]){throw 'P09_P33_PRESERVED'}
            RawPin $e.path $e.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $checkpoints=@('docs/continuidade/checkpoints/0234-p09-p33-reconciliacao-e-frentes-conferidas.md','docs/continuidade/checkpoints/0235-p09-p33-entrega-local-conferida.md')
        foreach($e in $m.newFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'P09_P33_NEW_COLLISION'}
            if(-not $e.path.StartsWith($catalog,[StringComparison]::Ordinal) -and
                $e.path -cnotin $m.changedExistingFiles.snapshot -and $e.path -cnotin $checkpoints){throw 'P09_P33_NEW_SCOPE'}
            RawPin $e.path $e.sha256
        }
        foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'P09_P33_SNAPSHOT_UNLISTED'}}
        Assert-QualificationFields $m.evidence @('path','sha256')
        if($m.evidence.path -cne ($catalog+'resultado.json')){throw 'P09_P33_EVIDENCE_PATH'}
        RawPin $m.evidence.path $m.evidence.sha256
        $result=RawJson $m.evidence.path
        if($result.status -cne $m.status -or $result.sourceCalls -ne 0 -or
            $result.databaseUsed -isnot [bool] -or $result.databaseUsed -or $result.newAcceptances -ne 0 -or
            $result.construction -cne '39/45' -or $result.historicalAcceptances -cne '67/115'){throw 'P09_P33_EVIDENCE_SCOPE'}
        foreach($e in $result.receipts){Assert-QualificationFields $e @('path','sha256');RawPin $e.path $e.sha256}
        return ,$map
    }
    RawPin $manifestPath ([IO.File]::ReadAllText((RawPhysical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest=RawJson $manifestPath;$map=CheckCurrent $manifest;$guards=0
    if($SelfTest){
        foreach($test in @(
            @('P09_P33_SCOPE',{param($m)$m.sourceCalls=1}),
            @('P09_P33_SCOPE',{param($m)$m.sourceCalls='0'}),
            @('P09_P33_SCOPE',{param($m)$m.databaseUsed=$true}),
            @('P09_P33_SCOPE',{param($m)$m.newAcceptances=1}),
            @('P09_P33_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('P09_P33_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('P09_P33_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='src/main/resources/logback.xml'}),
            @('P09_P33_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('P09_P33_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('P09_P33_PRESERVED',{param($m)($m.preservedFiles|Where-Object path -ceq 'src/main/resources/logback.xml').sha256='0'*64}),
            @('P09_P33_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
            @(('P09_P33_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy;$reason='ACCEPTED';try{$null=CheckCurrent $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('P09_P33_GUARD_'+$reason)};$guards++
        }
        $reason='ACCEPTED';try{$null=CheckCurrent $manifest @($actual|Where-Object {$_ -cne 'src/main/resources/logback.xml'})}catch{$reason=$_.Exception.Message}
        if($reason -cne 'P09_P33_UNLISTED_DRIFT'){throw ('P09_P33_GUARD_'+$reason)};$guards++
        foreach($path in @('src/main/resources/undeclared-runtime.json',($catalog+'../outside.json'))){
            $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
            $copy.newFiles+=@{path=$path;sha256='0'*64}
            $reason='ACCEPTED';try{$null=CheckCurrent $copy @(@($actual)+@($path))}catch{$reason=$_.Exception.Message}
            $expected=if($path.StartsWith($catalog,[StringComparison]::Ordinal)){'P09_P33_PATH'}else{'P09_P33_NEW_SCOPE'}
            if($reason -cne $expected){throw ('P09_P33_GUARD_'+$reason)};$guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $successor){
        foreach($entry in $successor.map.Values){
            if($map.ContainsKey($entry.path)){
                $previous=$map[$entry.path]
                if($previous.after -cne $entry.before){throw 'STATIC_ADVANCE_CHAIN_BREAK'}
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            }else{$map.Add($entry.path,$entry)}
        }
        $newFiles=@(@($newFiles)+@($successor.newFiles)|Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;successor=$successor}
}

function Get-Post0227Succession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/requalificacao-pos0227/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $successor=Get-P09P33DocumentarySuccession -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$p){
        if($null -ne $successor -and $successor.map.ContainsKey($p)){$p=$successor.map[$p].snapshot}
        if($p -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $p.Contains('..') -or $p.StartsWith('/') -or
            $p -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$'){throw 'POST0227_PATH'}
        $node=Get-Item -LiteralPath (Join-Path $Root $p);$file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'POST0227_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'POST0227_ESCAPE'}
        }
        return $file
    }
    function Hash([string]$p){(Get-FileHash -LiteralPath (Physical $p)).Hash.ToLowerInvariant()}
    function Json([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $p))) 8388608}
    function Pin([string]$p,[string]$sha){if($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $p) -cne $sha){throw ('POST0227_HASH_'+$p)}}
    function Exact($a,$b,[string]$code){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object)-join '|') -cne (@($b|Sort-Object)-join '|')){throw $code}}
    $oldPath='docs/catalogos/p11-fisico/manifesto.json'
    $oldHash='20b9262adcdb8192e5231e57ccae943ffbf44a2a2472df1ea54b3081ac45cbe4'
    Pin $oldPath $oldHash
    $old=Json $oldPath
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($oldPath,$oldHash);$base.Add($oldPath.Replace('.json','.sha256'),(Hash $oldPath.Replace('.json','.sha256')))
    Push-Location -LiteralPath $Root
    try{
        $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object {$_ -replace '\\','/'})
        if($LASTEXITCODE -ne 0){throw 'POST0227_ENUMERATION'}
    }finally{Pop-Location}
    function Check($m){
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseUsed','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        if($m.version -ne 1 -or $m.initialFiles -ne 3779 -or $base.Count -ne 3779 -or
            $m.status -cne 'LOCAL_REQUALIFICATION_P07_P08_PASS' -or $m.sourceCalls -ne 0 -or
            $m.databaseUsed -cne $true -or $m.newAcceptances -ne 0 -or
            $m.construction -cne '39/45' -or $m.historicalAcceptances -cne '67/115'){throw 'POST0227_SCOPE'}
        if($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash){throw 'POST0227_PREDECESSOR'}
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/P11PhysicalSuccession.psm1') 'POST0227_DELTA_SET'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedExistingFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if(-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or
                $e.snapshot -cne ('docs/continuidade/historico/requalificacao-pos0227/'+$e.path)){throw 'POST0227_BEFORE'}
            Pin $e.snapshot $e.before
            if($null -eq $e.after){throw 'POST0227_REMOVAL'}
            Pin $e.path $e.after;$map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'POST0227_PRESERVED_SET'
        foreach($e in $m.preservedFiles){if($e.sha256 -cne $base[$e.path]){throw 'POST0227_PRESERVED'};Pin $e.path $e.sha256}
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($e in $m.newFiles){
            if($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'POST0227_NEW_COLLISION'}
            Pin $e.path $e.sha256
        }
        foreach($e in $m.changedExistingFiles){if(-not $seen.Contains($e.snapshot)){throw 'POST0227_SNAPSHOT_UNLISTED'}}
        $revisionFiles=if($null -ne $successor){@($actual|Where-Object {$_ -cnotin $successor.newFiles})}else{$actual}
        if($null -ne $successor){Exact $revisionFiles $successor.baselineFiles 'POST0227_SUCCESSOR_BASELINE'}
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'POST0227_UNLISTED_DRIFT'
        if($m.evidence.path -cne ($catalog+'resultado.json')){throw 'POST0227_EVIDENCE_PATH'}
        Pin $m.evidence.path $m.evidence.sha256
        $result=Json $m.evidence.path
        if($result.status -cne $m.status -or -not $result.fullPhysicalVerify -or -not $result.packageExecuted -or
            $result.sourceCalls -ne 0 -or $result.externalNetworkCalls -ne 0 -or $null -ne $result.databaseCalls -or
            $result.ddl -or $result.domainCommit -or $result.productionUsed -or $result.newAcceptances -ne 0){throw 'POST0227_EVIDENCE_SCOPE'}
        foreach($e in $result.receipts){Pin $e.path $e.sha256}
        $round='target/requalificacao-pos0227-20260922-01/'
        $macro='target/macrobloco-qualificacao-pacote-20260913-01/'
        $verify=Json ($macro+$result.buildAttempt+'/result.json')
        if($verify.exit -ne 0 -or $verify.phase -cne 'VerifyPhysical' -or $verify.timedOut -or
            -not $verify.rollbackConfirmed -or -not $verify.logUtf8Integrity -or $verify.logLimitExceeded){throw 'POST0227_VERIFY'}
        $regression=Json ($round+'p07-regression.json')
        if(-not $regression.passed -or $regression.unitTests -ne 2162 -or $regression.unitSkipped -ne 4 -or
            $regression.integrationTests -ne 492 -or $regression.integrationClasses -ne 105 -or
            $regression.integrationSkipped -ne 0 -or -not $regression.coveragePassed){throw 'POST0227_REGRESSION'}
        foreach($e in $regression.reports){Pin $e.path $e.sha256}
        $exact=Json ($round+'p07-exact-predecessor.json')
        if(-not $exact.passed -or $exact.comparisons.Count -ne 2 -or @($exact.comparisons|Where-Object {-not $_.exactCaseMultiplicity}).Count){throw 'POST0227_CASE_IDENTITIES'}
        $reproduction=Json ($round+'reproduction.json')
        if(-not $reproduction.passed -or -not $reproduction.byteIdentical -or $reproduction.comparedBytes -lt 1){throw 'POST0227_REPRODUCTION'}
        foreach($kind in @('primary','reproduction')){
            $p=$macro+'pos0227-package-'+$kind+'-01/'
            Pin ($p+'qualification.zip') $reproduction.archiveSha256
            $package=Json ($p+'result.json')
            if($package.members -ne $reproduction.members -or $package.manifestSha256 -cne $reproduction.manifestSha256 -or $package.dependencies -ne 9){throw 'POST0227_PACKAGE'}
        }
        $smoke=Json ($macro+'pos0227-smoke-02/result.json')
        Exact $smoke.commands.command @('inspect','plan','run','status','resume','compare') 'POST0227_SMOKE_COMMANDS'
        if($smoke.state -cne 'PASS_LOCAL' -or $smoke.sourceWorkspace -or @($smoke.commands|Where-Object {$_.exit -ne 0 -or $_.sourceWorkspace}).Count){throw 'POST0227_SMOKE'}
        foreach($kind in @('control','extracted')){
            $guards=Json ($macro+'pos0227-'+$kind+'-guards-01/result.json')
            $count=if($kind -ceq 'control'){8}else{21}
            if($guards.state -cne 'PASS_LOCAL' -or $guards.count -ne $count -or $guards.jdbc -cne 'NOT_STARTED' -or @($guards.cases|Where-Object {-not $_.passed}).Count){throw 'POST0227_GUARDS'}
            if($kind -ceq 'extracted' -and ($guards.childrenCreated -ne 0 -or @($guards.cases|Where-Object {$_.controlCreated}).Count)){throw 'POST0227_CREATED_CHILD'}
        }
        $envelope=Json ($round+'package-guards-01/stdout.log')
        if($envelope.state -cne 'PASS' -or $envelope.cases -ne 25){throw 'POST0227_ENVELOPE'}
        foreach($name in @('sequence-a','sequence-b','value','precision','key','multiplicity','old-reference','missing-user','pin-drift','command')){
            $proof=Json ($macro+'pos0227-'+$name+'-01/result.json')
            if(-not $proof.passed -or -not $proof.rollbackConfirmed -or $proof.timedOut -or $proof.logLimitExceeded -or $proof.layer -cne 'DISTRIBUTED_JAR_EXTRACTED_SEQUENCE'){throw 'POST0227_SEQUENCE'}
            if($name -cin @('sequence-a','sequence-b') -and ($proof.stages -ne 7 -or $proof.comparisons -ne 133 -or $proof.previews -ne 231)){throw 'POST0227_SEQUENCE_TOTALS'}
        }
        foreach($name in @('master','audit','tables')){if((Hash ($round+$name+'-before.log')) -cne (Hash ($round+$name+'-after-final.log'))){throw 'POST0227_AGGREGATES'}}
        $matrix=Json ($catalog+'matriz-atual.json')
        if($matrix.openExternalRequirements -ne 16 -or $matrix.newAcceptances -ne 0 -or $matrix.rounds.Count -ne 5 -or
            $null -ne $matrix.rounds[1].externalNetworkCalls -or $matrix.rounds[1].ledgerReceivedCalls -ne 7 -or
            $matrix.rounds[3].externalNetworkCalls -ne 4 -or $null -ne $matrix.rounds[3].databaseCalls -or
            $matrix.rounds[4].externalNetworkCalls -ne 0 -or $null -ne $matrix.rounds[4].databaseCalls){throw 'POST0227_MATRIX'}
        $requirements=@($matrix.stages|ForEach-Object {$_.requirements})
        if(@($requirements|Where-Object status -ceq 'BLOQUEADO_POR_INPUT').Count -ne 16 -or @($requirements|Where-Object {$null -ne $_.nominalOwner}).Count){throw 'POST0227_EXTERNAL_ACCEPTANCE'}
        return ,$map
    }
    Pin $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest=Json $manifestPath;$map=Check $manifest;$guards=0
    if($SelfTest){foreach($test in @(
        @('POST0227_SCOPE',{param($m)$m.sourceCalls=1}),
        @('POST0227_SCOPE',{param($m)$m.databaseUsed=$false}),
        @('POST0227_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
        @('POST0227_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
        @('POST0227_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='pom.xml'}),
        @('POST0227_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
        @('POST0227_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
        @('POST0227_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64}),
        @('POST0227_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)}),
        @(('POST0227_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
    )){
        $copy=Read-QualificationJsonBytes ($utf8.GetBytes(($manifest|ConvertTo-Json -Depth 12 -Compress))) 8388608
        & $test[1] $copy;$reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
        if($reason -cne $test[0]){throw ('POST0227_GUARD_'+$reason)};$guards++
    }}
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $successor){
        foreach($entry in $successor.map.Values){
            if($map.ContainsKey($entry.path)){
                $previous=$map[$entry.path]
                if($previous.after -cne $entry.before){throw 'P09_P33_CHAIN_BREAK'}
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            }else{$map.Add($entry.path,$entry)}
        }
        $newFiles=@(@($newFiles)+@($successor.newFiles)|Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;successor=$successor}
}
Export-ModuleMember -Function Get-Post0227Succession

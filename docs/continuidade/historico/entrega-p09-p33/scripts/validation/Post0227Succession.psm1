#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-Post0227Succession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/catalogos/requalificacao-pos0227/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$p){
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
        Exact $actual @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'POST0227_UNLISTED_DRIFT'
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
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'));manifest=$manifest;guards=$guards}
}
Export-ModuleMember -Function Get-Post0227Succession

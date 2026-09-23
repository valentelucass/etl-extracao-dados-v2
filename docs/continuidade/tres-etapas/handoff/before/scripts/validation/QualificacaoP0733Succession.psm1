#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-ThreeStagePlanningOverlay {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $folder='docs/continuidade/tres-etapas/'
    $manifestPath=$folder+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    function File([string]$p){
        if($p -cnotmatch '^[\p{L}\p{N}_./-]+$' -or $p.Contains('..') -or $p.StartsWith('/')){throw 'THREE_PATH'}
        $node=[IO.FileInfo]::new((Join-Path $Root $p));$full=$node.FullName
        if(-not $node.Exists){throw 'THREE_PATH'}
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'THREE_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'THREE_PATH'}
        };return $full
    }
    function Hash([string]$p){
        $stream=[IO.File]::OpenRead((File $p))
        try{return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant()}
        finally{$stream.Dispose()}
    }
    function Json([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((File $p))) 8388608}
    function Pin($p){if($p.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Hash $p.path) -cne $p.sha256){throw 'THREE_PIN'}}
    function Exact($a,$b){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -CaseSensitive)-join '|') -cne (@($b|Sort-Object -CaseSensitive)-join '|')){throw 'THREE_SET'}}
    $previousPath='docs/continuidade/qualificacao-p07-p33/manifesto.json'
    $previousHash='1fd01ece9d633cf6fb9765ad776d7fe8049b8c714258a9d118d268f108e1c88b'
    Pin @{path=$previousPath;sha256=$previousHash}
    $previous=Json $previousPath
    $baseline=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in @($previous.preservedFiles)+@($previous.newFiles)){$baseline.Add($e.path,$e.sha256)}
    foreach($e in $previous.changedExistingFiles){$baseline.Add($e.path,$e.after)}
    $baseline.Add($previousPath,$previousHash);$baseline.Add($previousPath.Replace('.json','.sha256'),(Hash $previousPath.Replace('.json','.sha256')))
    $allowed=@('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/QualificacaoP0733Succession.psm1')
    $checkpoint='docs/continuidade/checkpoints/0244-finalizacao-em-tres-etapas.md'
    $added=@(($folder+'GUIA_E_PROMPTS.md'),$checkpoint)+@($allowed|ForEach-Object {$folder+'before/'+$_})
    Push-Location -LiteralPath $Root
    try{$actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|ForEach-Object {$_ -replace '\\','/'});if($LASTEXITCODE -ne 0){throw 'THREE_ENUMERATION'}}finally{Pop-Location}
    function Check($m){
        Assert-QualificationFields $m @('version','status','predecessor','closedReceipt','changedFiles','newFiles','checkpoint','newAcceptances','sourceCalls','databaseCalls')
        if($m.version -cne 1 -or $m.status -cne 'THREE_STAGES_ADOPTED_EXECUTION_NOT_STARTED' -or $m.newAcceptances -cne 0 -or $m.sourceCalls -cne 0 -or $m.databaseCalls -cne 0){throw 'THREE_SCOPE'}
        if($m.predecessor.path -cne $previousPath -or $m.predecessor.sha256 -cne $previousHash){throw 'THREE_PREDECESSOR'}
        Pin $m.predecessor
        if($m.closedReceipt.path -cne 'target/qualificacao-p07-p33-20260922-01/physical/closed-receipt.json'){throw 'THREE_RECEIPT'}
        Pin $m.closedReceipt;$closed=Json $m.closedReceipt.path
        if($closed.state -cne 'CLOSED' -or $closed.currentP07Qualified -cne $true -or $closed.currentP08Qualified -cne $true){throw 'THREE_RECEIPT'}
        Exact $m.changedFiles.path $allowed;Exact $m.newFiles.path $added
        Exact $actual @(@($baseline.Keys)+@($added)+@($manifestPath,$manifestPath.Replace('.json','.sha256')))
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if($e.before -cne $baseline[$e.path] -or $e.snapshot -cne ($folder+'before/'+$e.path) -or $e.before -ceq $e.after){throw 'THREE_BEFORE'}
            Pin @{path=$e.snapshot;sha256=$e.before};Pin @{path=$e.path;sha256=$e.after};$map.Add($e.path,$e)
        }
        foreach($p in $baseline.Keys){if(-not $map.ContainsKey($p)){Pin @{path=$p;sha256=$baseline[$p]}}}
        foreach($e in $m.newFiles){Assert-QualificationFields $e @('path','sha256');Pin $e}
        if($m.checkpoint.path -cne $checkpoint){throw 'THREE_CHECKPOINT'};Pin $m.checkpoint
        return ,$map
    }
    $seal=[IO.File]::ReadAllText((File $manifestPath.Replace('.json','.sha256'))).Trim()
    Pin @{path=$manifestPath;sha256=$seal};$manifest=Json $manifestPath;$map=Check $manifest;$guards=0
    if($SelfTest){
        foreach($test in @(
            @('THREE_SCOPE',{param($m)$m.newAcceptances=1}),
            @('THREE_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('THREE_SET',{param($m)$m.newFiles=@($m.newFiles|Select-Object -Skip 1)}),
            @('THREE_SET',{param($m)$m.changedFiles[0].path='src/main/resources/logback.xml'}),
            @('THREE_BEFORE',{param($m)$m.changedFiles[0].snapshot='STATES.md'}),
            @('THREE_PIN',{param($m)$m.changedFiles[0].after='0'*64})
        )){
            $copy=Read-QualificationJsonBytes ([Text.Encoding]::UTF8.GetBytes(($manifest|ConvertTo-Json -Depth 30 -Compress))) 8388608
            & $test[1] $copy;$reason='NO_REFUSAL'
            try{$null=Check $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('THREE_GUARD_'+$reason)};$guards++
        }
    }
    return [pscustomobject]@{map=$map;newFiles=@($added)+@($manifestPath,$manifestPath.Replace('.json','.sha256'));guards=$guards}
}

function Get-QualificacaoP0733Succession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root,[switch]$SelfTest)
    $ErrorActionPreference='Stop'
    $Root=[IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog='docs/continuidade/qualificacao-p07-p33/'
    $history='docs/continuidade/historico/qualificacao-p07-p33/'
    $manifestPath=$catalog+'manifesto.json'
    if(-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))){return $null}
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $planning=Get-ThreeStagePlanningOverlay -Root $Root -SelfTest:$SelfTest
    $utf8=[Text.UTF8Encoding]::new($false,$true)
    # The newest boundary always verifies current bytes before exposing historical snapshots.
    function Physical([string]$p){
        if($null -ne $planning -and $planning.map.ContainsKey($p)){$p=$planning.map[$p].snapshot}
        if($p -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $p.Contains('..') -or $p.StartsWith('/') -or
            $p -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$'){throw 'QP0733_PATH'}
        $full=Join-Path $Root $p
        if(-not (Test-Path -LiteralPath $full -PathType Leaf)){throw ('QP0733_MISSING_'+$p)}
        $node=Get-Item -LiteralPath $full;$file=$node.FullName
        while($node.FullName -cne $Root){
            if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'QP0733_REPARSE'}
            $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
            if($null -eq $node){throw 'QP0733_ESCAPE'}
        }
        return $file
    }
    function Hash([string]$p){(Get-FileHash -LiteralPath (Physical $p)).Hash.ToLowerInvariant()}
    function Json([string]$p){Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $p))) 8388608}
    function Pin([string]$p,[string]$sha){if($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $p) -cne $sha){throw ('QP0733_HASH_'+$p)}}
    function Exact($a,$b,[string]$code){if(@($a).Count -ne @($b).Count -or (@($a|Sort-Object -CaseSensitive)-join '|') -cne (@($b|Sort-Object -CaseSensitive)-join '|')){throw $code}}
    function IsInteger($n){return $n -is [long] -or $n -is [int]}
    function IsBoolean($n,[bool]$expected){return $n -is [bool] -and $n -ceq $expected}
    $oldPath='docs/continuidade/avanco-seguranca/manifesto.json'
    $oldHash='18aa869780d516e32b7338f92fde5c31fd10e13a5bb59fd0d2a1852ca2beab0c'
    Pin $oldPath $oldHash
    $oldSeal=$oldPath.Replace('.json','.sha256')
    if([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $oldHash){throw 'QP0733_OLD_SEAL'}
    $old=Json $oldPath
    $base=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $old.preservedFiles){$base.Add($e.path,$e.sha256)}
    foreach($e in $old.changedExistingFiles){$base.Add($e.path,$e.after)}
    foreach($e in $old.newFiles){$base.Add($e.path,$e.sha256)}
    $base.Add($oldPath,$oldHash);$base.Add($oldSeal,(Hash $oldSeal))
    Push-Location -LiteralPath $Root
    try{
        $actual=@(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*'|ForEach-Object {$_ -replace '\\','/'})
        if($LASTEXITCODE -ne 0){throw 'QP0733_ENUMERATION'}
    }finally{Pop-Location}
    if($null -ne $planning){$actual=@($actual|Where-Object {$_ -cnotin $planning.newFiles})}
    function CheckScope($m){
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseUsed','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence','checkpoint')
        foreach($field in @('version','initialFiles','sourceCalls','newAcceptances')){if(-not (IsInteger $m[$field])){throw 'QP0733_SCOPE'}}
        if($m.version -ne 1 -or $m.initialFiles -ne $base.Count -or
            $m.status -cne 'LOCAL_REQUALIFICATION_P07_P08_PASS' -or $m.sourceCalls -ne 0 -or
            -not (IsBoolean $m.databaseUsed $true) -or $m.newAcceptances -ne 0 -or
            $m.construction -cne $old.construction -or $m.historicalAcceptances -cne $old.historicalAcceptances){throw 'QP0733_SCOPE'}
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash){throw 'QP0733_PREDECESSOR'}
    }
    function CheckCurrent($m,$observed=$actual){
        CheckScope $m
        Assert-QualificationFields $m.checkpoint @('path','sha256')
        if($m.checkpoint.path -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$' -or
            $base.ContainsKey($m.checkpoint.path)){throw 'QP0733_CHECKPOINT'}
        Pin $m.checkpoint.path $m.checkpoint.sha256
        Exact $observed @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'QP0733_UNLISTED_DRIFT'
        $map=[Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach($e in $m.changedExistingFiles){
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if(-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or
                $e.snapshot -cne ($history+$e.path)){throw 'QP0733_BEFORE'}
            if($null -eq $e.after){throw 'QP0733_REMOVAL'}
            if($e.before -ceq $e.after){throw 'QP0733_UNCHANGED_DELTA'}
            if($e.path -cnotin @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/Post0227Succession.psm1') -and
                $e.path -cnotmatch '^src/(main|test)/java/.+\.java$' -and -not $e.path.StartsWith('scripts/security/',[StringComparison]::Ordinal)){throw 'QP0733_DELTA_SCOPE'}
            Pin $e.snapshot $e.before;Pin $e.path $e.after;$map.Add($e.path,$e)
        }
        foreach($required in @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/Post0227Succession.psm1')){
            if(-not $map.ContainsKey($required)){throw 'QP0733_MANDATORY_DELTA'}
        }
        Exact $m.preservedFiles.path @($base.Keys|Where-Object {-not $map.ContainsKey($_)}) 'QP0733_PRESERVED_SET'
        foreach($e in $m.preservedFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($e.sha256 -cne $base[$e.path]){throw 'QP0733_PRESERVED'}
            Pin $e.path $e.sha256
        }
        $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($e in $m.newFiles){
            Assert-QualificationFields $e @('path','sha256')
            if($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))){throw 'QP0733_NEW_COLLISION'}
            if(-not $e.path.StartsWith($catalog,[StringComparison]::Ordinal) -and
                $e.path -cnotin $m.changedExistingFiles.snapshot -and $e.path -cnotmatch '^docs/continuidade/checkpoints/[0-9]{4}-[a-z0-9-]+\.md$' -and
                $e.path -cnotin @('scripts/validation/QualificacaoP0733Succession.psm1','scripts/validation/Test-QualificacaoP0733Succession.ps1','docs/adr/0054-disposicao-tecnica-especifica-de-alertas-pmd.md') -and
                -not $e.path.StartsWith('scripts/security/',[StringComparison]::Ordinal) -and
                $e.path -cnotmatch '^src/(main|test)/java/.+\.java$'){throw 'QP0733_NEW_SCOPE'}
            Pin $e.path $e.sha256
        }
        foreach($p in @($m.changedExistingFiles.snapshot)+@($m.checkpoint.path,'scripts/validation/QualificacaoP0733Succession.psm1','scripts/validation/Test-QualificacaoP0733Succession.ps1')){
            if(-not $seen.Contains($p)){throw 'QP0733_REQUIRED_NEW_FILE'}
        }
        Assert-QualificationFields $m.evidence @('path','sha256')
        if($m.evidence.path -cne ($catalog+'resultado.json') -or -not $seen.Contains($m.evidence.path)){throw 'QP0733_EVIDENCE_PATH'}
        Pin $m.evidence.path $m.evidence.sha256
        return ,$map
    }
    function AssertEvidenceScope($result){
        foreach($field in @('sourceCalls','externalNetworkCalls','newAcceptances')){if(-not (IsInteger $result[$field]) -or $result[$field] -ne 0){throw 'QP0733_EVIDENCE_SCOPE'}}
        foreach($field in @('ddl','domainCommit','productionUsed','nominalApproval','releaseApproved','deploymentUsed','cutover')){
            if(-not (IsBoolean $result[$field] $false)){throw 'QP0733_EVIDENCE_SCOPE'}
        }
        foreach($field in @('fullPhysicalVerify','packageExecuted','currentP07Qualified','currentP08Qualified')){
            if(-not (IsBoolean $result[$field] $true)){throw 'QP0733_EVIDENCE_SCOPE'}
        }
        if($result.status -cne 'LOCAL_REQUALIFICATION_P07_P08_PASS' -or $null -ne $result.databaseCalls -or
            $result.construction -cne $old.construction -or $result.historicalAcceptances -cne $old.historicalAcceptances){throw 'QP0733_EVIDENCE_SCOPE'}
    }
    function AssertP07($verify,$regression,$exact){
        if($verify.state -cne 'OBSERVED' -or $verify.rawLogMaximumBytes -ne 16777216 -or
            $verify.exit -ne 0 -or $verify.phase -cne 'VerifyPhysical' -or -not (IsBoolean $verify.timedOut $false) -or
            -not (IsBoolean $verify.rollbackConfirmed $true) -or -not (IsBoolean $verify.logUtf8Integrity $true) -or
            -not (IsBoolean $verify.logLimitExceeded $false)){throw 'QP0733_VERIFY'}
        Exact $verify.arguments @('--offline','--batch-mode','--no-transfer-progress','spotless:apply','verify','-Dv2.measurement.receipt=true','-Pshadow-local-integration','-Dshadow.local.integration.enabled=true') 'QP0733_VERIFY_ARGUMENTS'
        if(-not (IsBoolean $regression.passed $true) -or -not (IsBoolean $regression.coveragePassed $true) -or
            -not (IsBoolean $regression.rollbackConfirmed $true) -or $regression.unitTests -le 0 -or
            $regression.integrationTests -le 0 -or $regression.integrationSkipped -ne 0){throw 'QP0733_REGRESSION'}
        if(-not (IsBoolean $exact.passed $true) -or -not (IsBoolean $exact.coveragePassed $true) -or
            -not (IsBoolean $exact.rollbackConfirmed $true) -or $exact.unitCases -ne $regression.unitTests -or
            $exact.integrationCases -ne $regression.integrationTests -or $exact.sourceFilesChecked -le 0){throw 'QP0733_IDENTITIES'}
        foreach($id in @('P07_0233_UNIT','P07_0233_INTEGRATION','UNIT_0236')){
            $comparison=@($exact.comparisons|Where-Object reference -CEQ $id)
            if($comparison.Count -ne 1 -or -not (IsBoolean $comparison[0].allPriorIdentityMultiplicitiesPreserved $true) -or
                $comparison[0].currentCases -lt $comparison[0].priorCases -or $comparison[0].priorCases -le 0){throw 'QP0733_IDENTITIES'}
        }
    }
    function AssertP07Logs([string]$stdout,[string]$stderr,[string]$before,[string]$after){
        if($stdout.Contains([char]0xfffd) -or $stderr.Contains([char]0xfffd)){throw 'QP0733_VERIFY_LOG_UTF8'}
        if(-not $stdout.Contains('BUILD SUCCESS')){throw 'QP0733_VERIFY_BUILD_SUCCESS'}
        if(-not $stdout.Contains('All coverage checks have been met.')){throw 'QP0733_VERIFY_COVERAGE_LOG'}
        if([string]::IsNullOrWhiteSpace($before) -or $before -cne $after){throw 'QP0733_VERIFY_ROLLBACK_LOG'}
    }
    function AssertReproduction($r){
        if(-not (IsBoolean $r.passed $true) -or -not (IsBoolean $r.byteIdentical $true) -or $r.comparedBytes -le 0 -or
            $r.members -le 0 -or $r.dependencies -ne 9){throw 'QP0733_REPRODUCTION'}
    }
    function AssertStaticGate($gate,$raw,$catalogData,$inventory){
        if($gate.schema -cne 'local-pmd-disposition-result-v1' -or $gate.state -cne 'PASS_LOCAL_REVIEWED_FINDINGS' -or
            $gate.rawState -cne 'FINDINGS_OPEN' -or $gate.rawGateExit -ne 1 -or $gate.rawFindingsSuppressed -ne 0 -or
            -not (IsBoolean $gate.nominalSecurityAcceptance $false) -or -not (IsBoolean $gate.releaseAcceptance $false) -or
            -not (IsBoolean $gate.network $false) -or -not (IsBoolean $gate.sql $false)){throw 'QP0733_STATIC_GATE'}
        if($raw.state -cne 'FINDINGS_OPEN' -or $gate.rawFindings -ne $raw.findingCount -or
            $gate.reviewedFindings -ne $raw.findingCount -or $gate.dispositions.Count -ne $raw.findingCount -or
            $raw.findings.Count -ne $raw.findingCount -or $catalogData.findings.Count -ne $raw.findingCount -or
            $gate.sourceFiles -ne @($inventory).Count -or $raw.sourceFiles -ne @($inventory).Count){throw 'QP0733_STATIC_COUNTS'}
        if($catalogData.schema -cne 'local-pmd-dispositions-v1' -or
            -not (IsBoolean $catalogData.nominalSecurityAcceptance $false) -or
            -not (IsBoolean $catalogData.releaseAcceptance $false)){throw 'QP0733_STATIC_CATALOG'}
    }
    function AssertStaticProvenance($recorded,$observed){
        Exact @($recorded.evidencePins|ForEach-Object {$_.path+'|'+$_.sha256}) @($observed.evidencePins|ForEach-Object {$_.path+'|'+$_.sha256}) 'QP0733_STATIC_PROVENANCE'
    }
    function ReportCases([string]$relative){
        $file=Physical $relative
        if((Get-Item -LiteralPath $file).Length -gt 33554432){throw 'QP0733_XML_SIZE'}
        $settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit
        $settings.XmlResolver=$null;$settings.MaxCharactersInDocument=33554432
        $reader=[Xml.XmlReader]::Create($file,$settings);$xml=[Xml.XmlDocument]::new();$xml.XmlResolver=$null
        try{$xml.Load($reader)}finally{$reader.Dispose()}
        $cases=@($xml.SelectNodes('/testsuite/testcase'))
        if($cases.Count -eq 0 -or [int]$xml.testsuite.tests -ne $cases.Count -or
            $xml.SelectNodes('/testsuite/testcase/failure|/testsuite/testcase/error').Count -ne 0){throw 'QP0733_XML_RESULT'}
        if(@($cases|Where-Object {[string]::IsNullOrWhiteSpace($_.GetAttribute('classname')) -or [string]::IsNullOrWhiteSpace($_.GetAttribute('name'))}).Count){throw 'QP0733_XML_IDENTITY'}
        return @{keys=@($cases|ForEach-Object {$_.GetAttribute('classname')+'/'+$_.GetAttribute('name')});
            skipped=@($cases|Where-Object {$null -ne $_.SelectSingleNode('skipped')}|ForEach-Object {$_.GetAttribute('classname')+'/'+$_.GetAttribute('name')})}
    }
    function Frequency($keys){
        $frequencies=[Collections.Generic.Dictionary[string,int]]::new([StringComparer]::Ordinal)
        foreach($key in $keys){if($frequencies.ContainsKey($key)){$frequencies[$key]++}else{$frequencies[$key]=1}}
        return ,$frequencies
    }
    function AssertSmoke($smoke){
        Exact $smoke.commands.command @('inspect','plan','run','status','resume','compare') 'QP0733_SMOKE_COMMANDS'
        if($smoke.state -cne 'PASS_LOCAL' -or -not (IsBoolean $smoke.sourceWorkspace $false) -or
            @($smoke.commands|Where-Object {$_.exit -ne 0 -or -not (IsBoolean $_.sourceWorkspace $false)}).Count){throw 'QP0733_SMOKE'}
    }
    function AssertSequence($proof,[string]$variant){
        if(-not (IsBoolean $proof.passed $true) -or -not (IsBoolean $proof.rollbackConfirmed $true) -or
            -not (IsBoolean $proof.timedOut $false) -or -not (IsBoolean $proof.logLimitExceeded $false) -or
            -not (IsBoolean $proof.ddl $false) -or -not (IsBoolean $proof.domainCommit $false) -or
            $proof.sourceCalls -ne 0 -or $proof.layer -cne 'DISTRIBUTED_JAR_EXTRACTED_SEQUENCE' -or
            $proof.variant -cne $variant -or $proof.exit -ne $proof.expectedExit){throw 'QP0733_SEQUENCE'}
        if($variant -ceq 'COMPLETE' -and ($proof.stages -ne 7 -or $proof.comparisons -ne 133 -or $proof.previews -ne 231 -or $proof.exit -ne 0)){throw 'QP0733_SEQUENCE_TOTALS'}
        if($variant -cne 'COMPLETE' -and $proof.expectedExit -eq 0){throw 'QP0733_SEQUENCE_RECUSAL'}
    }
    $seal=[IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim()
    Pin $manifestPath $seal
    $manifest=Json $manifestPath;$map=CheckCurrent $manifest
    $result=Json $manifest.evidence.path;AssertEvidenceScope $result
    if($result.checkpoint.path -cne $manifest.checkpoint.path -or $result.checkpoint.sha256 -cne $manifest.checkpoint.sha256){throw 'QP0733_CHECKPOINT'}
    $pins=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($e in $result.receipts){Assert-QualificationFields $e @('path','sha256');Pin $e.path $e.sha256;$pins.Add($e.path,$e.sha256)}
    function Receipt([string]$p){if(-not $pins.ContainsKey($p)){throw ('QP0733_RECEIPT_UNLISTED_'+$p)};return Json $p}
    function ReceiptPin([string]$p,[string]$sha){if(-not $pins.ContainsKey($p) -or $pins[$p] -cne $sha){throw ('QP0733_RECEIPT_UNLISTED_'+$p)};Pin $p $sha}
    function ReceiptText([string]$p){
        if(-not $pins.ContainsKey($p)){throw ('QP0733_RECEIPT_UNLISTED_'+$p)}
        $file=Physical $p
        if((Get-Item -LiteralPath $file).Length -gt 16777216){throw 'QP0733_VERIFY_LOG_LIMIT'}
        return $utf8.GetString([IO.File]::ReadAllBytes($file))
    }
    $static=$result.staticAnalysis
    foreach($field in @('engineResult','catalog')){
        Assert-QualificationFields $static[$field] @('path','sha256')
        ReceiptPin $static[$field].path $static[$field].sha256
    }
    if($static.catalog.path -cne ($catalog+'disposicoes-pmd.json') -or
        $static.engineResult.path -cnotmatch '^target/qualificacao-p07-p33-20260922-01/[A-Za-z0-9_/-]+/result\.json$'){throw 'QP0733_STATIC_SCOPE'}
    foreach($relative in @($static.pmdDirectory,$static.testSourceRoot)+@($static.testReportDirectories)){
        if($relative -cnotmatch '^target/[A-Za-z0-9_./-]+$' -or $relative.Contains('..')){throw 'QP0733_STATIC_SCOPE'}
    }
    $staticGate=Receipt $static.engineResult.path
    $staticRaw=Receipt ($static.pmdDirectory+'/result.json')
    $staticInventory=Receipt ($static.pmdDirectory+'/source-inventory.json')
    $staticCatalog=Receipt $static.catalog.path
    AssertStaticGate $staticGate $staticRaw $staticCatalog $staticInventory
    if(-not (IsInteger $static.rawFindingCount) -or $static.rawFindingCount -ne $staticRaw.findingCount){throw 'QP0733_STATIC_COUNTS'}
    foreach($e in $staticGate.evidencePins){Pin $e.path $e.sha256}
    Import-Module (Join-Path $PSScriptRoot '../security/LocalStaticDisposition.psm1')
    $staticTimer=[Diagnostics.Stopwatch]::StartNew()
    $staticObserved=Get-LocalStaticDisposition -RepositoryRoot $Root -Catalog $static.catalog.path -PmdDirectory $static.pmdDirectory -TestSourceRoot $static.testSourceRoot -TestReportDirectories @($static.testReportDirectories)
    if($staticTimer.Elapsed.TotalSeconds -gt 120){throw 'QP0733_STATIC_DEADLINE'}
    AssertStaticGate $staticObserved $staticRaw $staticCatalog $staticInventory
    foreach($field in @('schema','state','rawState','rawGateExit','sourceFiles','rules','rawFindings','reviewedFindings','reportFiles','reportCases','rawFindingsSuppressed')){
        if($staticGate[$field] -cne $staticObserved[$field]){throw 'QP0733_STATIC_REPLAY'}
    }
    AssertStaticProvenance $staticGate $staticObserved
    Exact $staticGate.dispositions.id $staticObserved.dispositions.id 'QP0733_STATIC_DISPOSITIONS'
    foreach($disposition in $staticObserved.dispositions){
        $record=@($staticGate.dispositions|Where-Object id -CEQ $disposition.id)[0]
        foreach($field in @('path','line','rule','method','sourceSha256','classification','proofLimit')){
            if($record[$field] -cne $disposition[$field]){throw 'QP0733_STATIC_DISPOSITIONS'}
        }
        Exact @($record.tests|ForEach-Object {$_.className+'|'+$_.testCase+'|'+$_.invocations+'|'+$_.sourceSha256+'|'+(@($_.reports|Sort-Object)-join ',')}) @($disposition.tests|ForEach-Object {$_.className+'|'+$_.testCase+'|'+$_.invocations+'|'+$_.sourceSha256+'|'+(@($_.reports|Sort-Object)-join ',')}) 'QP0733_STATIC_TEST_PROVENANCE'
    }
    $physical=$result.physical
    $round='target/qualificacao-p07-p33-20260922-01/physical/'
    $macro='target/macrobloco-qualificacao-pacote-20260913-01/'
    if($physical.round -cne $round){throw 'QP0733_ROUND'}
    foreach($field in @('buildAttempt','packagePrimary','packageReproduction','smokeAttempt','controlAttempt','extractedAttempt')){
        if($physical[$field] -cnotmatch '^pos0236-[a-z0-9-]{1,56}$'){throw 'QP0733_ATTEMPT'}
    }
    $authority=Receipt ($round+'authority.json')
    if($authority.scope.host -cne 'localhost' -or $authority.scope.database -cne 'ETL_SISTEMA_V2_SHADOW' -or
        -not (IsBoolean $authority.scope.syntheticOnly $true) -or -not (IsBoolean $authority.scope.rollbackOnly $true) -or
        $authority.limits.externalNetworkRequests -ne 0 -or $authority.limits.totalReservedSeconds -gt 43200 -or
        $authority.limits.fullVerifySeconds -gt 7200 -or $authority.limits.sequenceSeconds -gt 1800){throw 'QP0733_AUTHORITY'}
    foreach($field in @('ddl','domainCommit','production','externalBusinessSources','publication','deploy','cutover','thirdPartyProcessOrConfigurationChanges')){
        if(-not (IsBoolean $authority.scope[$field] $false)){throw 'QP0733_AUTHORITY'}
    }
    $verify=Receipt ($macro+$physical.buildAttempt+'/result.json')
    $regression=Receipt ($round+'p07-regression.json')
    $exact=Receipt ($round+'p07-exact-predecessor.json')
    AssertP07 $verify $regression $exact
    $verifyStdout=ReceiptText ($macro+$physical.buildAttempt+'/stdout.log')
    $verifyStderr=ReceiptText ($macro+$physical.buildAttempt+'/stderr.log')
    $verifyBefore=ReceiptText ($macro+$physical.buildAttempt+'/before.log')
    $verifyAfter=ReceiptText ($macro+$physical.buildAttempt+'/after.log')
    AssertP07Logs $verifyStdout $verifyStderr $verifyBefore $verifyAfter
    if($regression.attempt -cne $physical.buildAttempt){throw 'QP0733_REGRESSION_ATTEMPT'}
    $snapshot=Receipt ($round+'source-snapshot.json')
    $buildInputs=Receipt ($macro+$physical.buildAttempt+'/inputs.json')
    $runtimePattern='^(src/|database/|config/|\.mvn/|pom\.xml$)'
    $snapshotRuntime=@($snapshot.files|Where-Object {$_.path -cmatch $runtimePattern})
    $buildRuntime=@($buildInputs|Where-Object {$_.path -cmatch $runtimePattern})
    Exact $snapshotRuntime.path @($actual|Where-Object {$_ -cmatch $runtimePattern}) 'QP0733_SOURCE_SET'
    Exact @($snapshotRuntime|ForEach-Object {$_.path+'|'+$_.sha256}) @($buildRuntime|ForEach-Object {$_.path+'|'+$_.sha256}) 'QP0733_BUILD_INPUTS'
    if($exact.sourceFilesChecked -ne $snapshotRuntime.Count){throw 'QP0733_SOURCE_COUNT'}
    ReceiptPin ($round+'regression-references.json') $snapshot.regressionReferencesSha256
    $references=Receipt ($round+'regression-references.json')
    if($references.buildAttempt -cne $physical.buildAttempt){throw 'QP0733_REFERENCE_ATTEMPT'}
    $currentCases=@{};$currentSkipped=@{}
    foreach($kind in @('surefire','failsafe')){
        $prefix=$macro+$physical.buildAttempt+'/build/target/'+$kind+'-reports/'
        $reports=@($regression.reports|Where-Object {$_.path.StartsWith($prefix,[StringComparison]::Ordinal)})
        $actualReports=@(Get-ChildItem -LiteralPath (Join-Path $Root $prefix) -Filter 'TEST-*.xml' -File|ForEach-Object {$prefix+$_.Name})
        Exact $reports.path $actualReports 'QP0733_REPORT_SET'
        $keys=[Collections.Generic.List[string]]::new();$skips=[Collections.Generic.List[string]]::new()
        foreach($e in $reports){
            Pin $e.path $e.sha256;$cases=ReportCases $e.path
            foreach($key in $cases.keys){$keys.Add($key)}
            foreach($key in $cases.skipped){$skips.Add($key)}
        }
        $currentCases[$kind]=Frequency $keys;$currentSkipped[$kind]=@($skips)
        if($kind -ceq 'surefire'){
            if($keys.Count -ne $regression.unitTests -or $skips.Count -ne $regression.unitSkipped){throw 'QP0733_XML_TOTALS'}
            Exact @($skips) $regression.historicalSkips 'QP0733_SKIP_DRIFT'
        }elseif($keys.Count -ne $regression.integrationTests -or $skips.Count -ne 0 -or $reports.Count -ne $regression.integrationClasses){throw 'QP0733_XML_TOTALS'}
    }
    if(@($regression.reports|Where-Object {-not $_.path.StartsWith($macro+$physical.buildAttempt+'/build/target/surefire-reports/',[StringComparison]::Ordinal) -and -not $_.path.StartsWith($macro+$physical.buildAttempt+'/build/target/failsafe-reports/',[StringComparison]::Ordinal)}).Count){throw 'QP0733_REPORT_SCOPE'}
    $referenceList=@($references.references)
    if($null -ne $references.finalUnitReference){$referenceList+=@($references.finalUnitReference)}
    $historicalReferences=@{
        P07_0233_UNIT=$macro+'pos0227-p07-verify-01/build/target/surefire-reports'
        P07_0233_INTEGRATION=$macro+'pos0227-p07-verify-01/build/target/failsafe-reports'
        UNIT_0236='target/avanco-seguranca-20260922-01/unit-check-04/build/target/surefire-reports'
    }
    foreach($id in $historicalReferences.Keys){
        $reference=@($referenceList|Where-Object id -CEQ $id)
        if($reference.Count -ne 1 -or $reference[0].path -cne $historicalReferences[$id]){throw 'QP0733_HISTORICAL_REFERENCE'}
    }
    foreach($reference in $referenceList){
        if($reference.kind -cnotin @('surefire','failsafe') -or $reference.path -cnotmatch '^target/[A-Za-z0-9_./-]+$' -or $reference.path.Contains('..')){throw 'QP0733_REFERENCE_SCOPE'}
        $referenceFiles=@(Get-ChildItem -LiteralPath (Join-Path $Root $reference.path) -Filter 'TEST-*.xml' -File|ForEach-Object {$_.Name})
        Exact $reference.files.file $referenceFiles 'QP0733_REFERENCE_FILE_SET'
        $keys=[Collections.Generic.List[string]]::new();$skips=[Collections.Generic.List[string]]::new()
        foreach($e in $reference.files){
            if($e.file -cnotmatch '^TEST-[A-Za-z0-9_.$-]+\.xml$'){throw 'QP0733_REFERENCE_SCOPE'}
            $p=$reference.path+'/'+$e.file;Pin $p $e.sha256;$cases=ReportCases $p
            foreach($key in $cases.keys){$keys.Add($key)}
            foreach($key in $cases.skipped){$skips.Add($key)}
        }
        $frequencies=Frequency $keys
        foreach($entry in $frequencies.GetEnumerator()){
            if(-not $currentCases[$reference.kind].ContainsKey($entry.Key) -or $currentCases[$reference.kind][$entry.Key] -lt $entry.Value){throw 'QP0733_CASE_MISSING'}
        }
        Exact @($skips) $currentSkipped[$reference.kind] 'QP0733_SKIP_DRIFT'
        $comparison=@($exact.comparisons|Where-Object reference -CEQ $reference.id)
        if($comparison.Count -ne 1 -or $comparison[0].priorCases -ne $keys.Count -or
            $comparison[0].currentCases -ne ($currentCases[$reference.kind].Values|Measure-Object -Sum).Sum){throw 'QP0733_REFERENCE_TOTALS'}
    }
    Exact $referenceList.id $exact.comparisons.reference 'QP0733_REFERENCE_SET'
    foreach($key in $references.requiredNewCases){
        if(-not $currentCases.surefire.ContainsKey($key) -and -not $currentCases.failsafe.ContainsKey($key)){throw 'QP0733_REQUIRED_CASE_MISSING'}
        if($currentSkipped.surefire -ccontains $key -or $currentSkipped.failsafe -ccontains $key){throw 'QP0733_REQUIRED_CASE_SKIPPED'}
    }
    foreach($e in $snapshotRuntime){Pin $e.path $e.sha256;Pin ($macro+$physical.buildAttempt+'/build/'+$e.path) $e.sha256}
    $reproduction=Receipt ($round+'reproduction.json');AssertReproduction $reproduction
    $packageRevision=$null
    foreach($attempt in @($physical.packagePrimary,$physical.packageReproduction)){
        $folder=$macro+$attempt+'/'
        $package=Receipt ($folder+'result.json')
        if($package.members -ne $reproduction.members -or $package.dependencies -ne $reproduction.dependencies -or
            $package.revision -cne $reproduction.revision -or $package.manifestSha256 -cne $reproduction.manifestSha256 -or
            $package.archiveSha256 -cne $reproduction.archiveSha256 -or -not (IsBoolean $package.candidate $false)){throw 'QP0733_PACKAGE'}
        ReceiptPin ($folder+'qualification.zip') $reproduction.archiveSha256
        if((Get-Item -LiteralPath (Physical ($folder+'qualification.zip'))).Length -ne $reproduction.comparedBytes){throw 'QP0733_PACKAGE_SIZE'}
        $null=Test-QualificationPackage -Directory (Join-Path $Root ($folder+'payload')) -ManifestSha256 $reproduction.manifestSha256
        $null=Test-QualificationSbom -Bom (Physical ($folder+'payload/sbom.cdx.json')) -SchemaDirectory (Join-Path $Root ($folder+'payload/licenses'))
        $sourceInventory=Receipt ($folder+'qualified-source-inventory.json')
        foreach($e in $sourceInventory){Pin $e.path $e.sha256}
        Exact @($sourceInventory|Where-Object {$_.path -cmatch $runtimePattern}|ForEach-Object {$_.path+'|'+$_.sha256}) @($snapshotRuntime|ForEach-Object {$_.path+'|'+$_.sha256}) 'QP0733_PACKAGE_SOURCE_SET'
        # The producer hashes this exact recorded sequence; sorting the parsed dictionaries changes it.
        $revisionBytes=$utf8.GetBytes((@($sourceInventory|ForEach-Object {$_.path+'|'+$_.sha256+"`n"})) -join '')
        if((Get-QualificationByteHash $revisionBytes) -cne $package.revision){throw 'QP0733_PACKAGE_REVISION'}
        if((Hash ($folder+'payload/etl-dataexport-v2.jar')) -cne (Hash ($macro+$physical.buildAttempt+'/build/target/etl-dataexport-v2.jar'))){throw 'QP0733_PACKAGE_BUILD_JAR'}
        $packageRevision=$package.revision
    }
    $smoke=Receipt ($macro+$physical.smokeAttempt+'/result.json');AssertSmoke $smoke
    if($smoke.package -cne $physical.packagePrimary -or $smoke.archiveSha256 -cne $reproduction.archiveSha256 -or
        $smoke.manifestSha256 -cne $reproduction.manifestSha256){throw 'QP0733_SMOKE_PACKAGE'}
    foreach($kind in @('control','extracted')){
        $attempt=if($kind -ceq 'control'){$physical.controlAttempt}else{$physical.extractedAttempt}
        $guards=Receipt ($macro+$attempt+'/result.json')
        $count=if($kind -ceq 'control'){8}else{21}
        if($guards.state -cne 'PASS_LOCAL' -or $guards.count -ne $count -or $guards.jdbc -cne 'NOT_STARTED' -or
            $guards.cases.Count -ne $count -or @($guards.cases|Where-Object {-not (IsBoolean $_.passed $true)}).Count){throw 'QP0733_GUARDS'}
        if($kind -ceq 'extracted' -and ($guards.childrenCreated -ne 0 -or @($guards.cases|Where-Object {$_.controlCreated}).Count)){throw 'QP0733_CREATED_CHILD'}
    }
    $envelope=Receipt ($round+'package-guards-01/stdout.log')
    if($envelope.state -cne 'PASS' -or $envelope.cases -ne 25){throw 'QP0733_ENVELOPE'}
    $variants=[ordered]@{'sequence-a'='COMPLETE';'sequence-b'='COMPLETE';value='VALUE';precision='PRECISION';key='KEY';multiplicity='MULTIPLICITY';'old-reference'='OLD_REFERENCE';'missing-user'='MISSING_USER';'pin-drift'='PIN_DRIFT';command='COMMAND'}
    Exact $physical.sequences.Keys @($variants.Keys) 'QP0733_SEQUENCE_SET'
    $sequenceEvidence=@{}
    foreach($name in $variants.Keys){
        $attempt=$physical.sequences[$name]
        if($attempt -cnotmatch '^pos0236-[a-z0-9-]{1,56}$'){throw 'QP0733_ATTEMPT'}
        $proof=Receipt ($macro+$attempt+'/result.json');AssertSequence $proof $variants[$name]
        $expectedCase=if($name -ceq 'sequence-b'){'sequence-b'}else{'sequence-a'}
        if($proof.case -cne $expectedCase){throw 'QP0733_SEQUENCE_CASE'}
        $sequenceEvidence[$name]=$proof
    }
    foreach($phase in @('p07','final')){
        foreach($name in @('master','audit','tables')){
            $before=$round+$name+'-before.log';$after=$round+$name+'-after-'+$phase+'.log'
            if(-not $pins.ContainsKey($before) -or -not $pins.ContainsKey($after) -or (Hash $before) -cne (Hash $after)){throw 'QP0733_AGGREGATES'}
        }
    }
    $pipeline=Receipt ($round+'p08-result.json')
    if($pipeline.state -cne 'OBSERVED_PASS'){throw 'QP0733_PIPELINE'}
    $guardsCount=0
    if($SelfTest){
        function Clone($value){Read-QualificationJsonBytes ($utf8.GetBytes(($value|ConvertTo-Json -Depth 50 -Compress))) 8388608}
        function Refusal([scriptblock]$body,[string]$expected){
            $reason='ACCEPTED';try{& $body}catch{$reason=$_.Exception.Message}
            if($reason -cne $expected){throw ('QP0733_GUARD_EXPECTED_'+$expected+'_OBSERVED_'+$reason)}
        }
        foreach($test in @(
            @('QP0733_SCOPE',{param($m)$m.sourceCalls=1}),
            @('QP0733_SCOPE',{param($m)$m.sourceCalls='0'}),
            @('QP0733_SCOPE',{param($m)$m.databaseUsed=$false}),
            @('QP0733_SCOPE',{param($m)$m.newAcceptances=1}),
            @('QP0733_SCOPE',{param($m)$m.initialFiles++}),
            @('QP0733_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('QP0733_CHECKPOINT',{param($m)$m.checkpoint.path='STATES.md'}),
            @(('QP0733_HASH_'+$manifest.checkpoint.path),{param($m)$m.checkpoint.sha256='0'*64}),
            @('QP0733_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('QP0733_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('QP0733_UNCHANGED_DELTA',{param($m)$m.changedExistingFiles[0].after=$m.changedExistingFiles[0].before}),
            @(('QP0733_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64}),
            @('QP0733_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles|Select-Object -Skip 1)})
        )){
            $copy=Clone $manifest;& $test[1] $copy
            Refusal {$null=CheckCurrent $copy} $test[0];$guardsCount++
        }
        Refusal {$null=CheckCurrent $manifest @($actual|Where-Object {$_ -cne 'src/main/resources/logback.xml'})} 'QP0733_UNLISTED_DRIFT';$guardsCount++
        Refusal {$null=CheckCurrent $manifest @(@($actual)+@('src/main/resources/unlisted.json'))} 'QP0733_UNLISTED_DRIFT';$guardsCount++
        foreach($entry in @(@{path='src/main/resources/undeclared.json';reason='QP0733_NEW_SCOPE'},@{path=$catalog+'../outside.json';reason='QP0733_PATH'})){
            $copy=Clone $manifest;$copy.newFiles+=@{path=$entry.path;sha256='0'*64}
            Refusal {$null=CheckCurrent $copy @(@($actual)+@($entry.path))} $entry.reason;$guardsCount++
        }
        Refusal {Pin 'src/main/resources/logback.xml' ('0'*64)} 'QP0733_HASH_src/main/resources/logback.xml';$guardsCount++
        Refusal {$null=Physical 'docs/continuidade/qualificacao-p07-p33/__missing_proof__.json'} 'QP0733_MISSING_docs/continuidade/qualificacao-p07-p33/__missing_proof__.json';$guardsCount++
        foreach($field in @('nominalApproval','releaseApproved','deploymentUsed','cutover','fullPhysicalVerify','packageExecuted','currentP07Qualified','currentP08Qualified')){
            $copy=Clone $result;$copy[$field]= -not $copy[$field]
            Refusal {AssertEvidenceScope $copy} 'QP0733_EVIDENCE_SCOPE';$guardsCount++
        }
        $copy=Clone $verify;$copy.exit=1;Refusal {AssertP07 $copy $regression $exact} 'QP0733_VERIFY';$guardsCount++
        Refusal {AssertP07Logs 'All coverage checks have been met.' '' $verifyBefore $verifyAfter} 'QP0733_VERIFY_BUILD_SUCCESS';$guardsCount++
        Refusal {AssertP07Logs 'BUILD SUCCESS' '' $verifyBefore $verifyAfter} 'QP0733_VERIFY_COVERAGE_LOG';$guardsCount++
        Refusal {AssertP07Logs $verifyStdout ([string][char]0xfffd) $verifyBefore $verifyAfter} 'QP0733_VERIFY_LOG_UTF8';$guardsCount++
        Refusal {AssertP07Logs $verifyStdout $verifyStderr $verifyBefore ($verifyAfter+'DRIFT')} 'QP0733_VERIFY_ROLLBACK_LOG';$guardsCount++
        Refusal {AssertP07Logs $verifyStdout $verifyStderr '' ''} 'QP0733_VERIFY_ROLLBACK_LOG';$guardsCount++
        $copy=Clone $regression;$copy.coveragePassed=$false;Refusal {AssertP07 $verify $copy $exact} 'QP0733_REGRESSION';$guardsCount++
        $copy=Clone $exact;$copy.comparisons[0].allPriorIdentityMultiplicitiesPreserved=$false;Refusal {AssertP07 $verify $regression $copy} 'QP0733_IDENTITIES';$guardsCount++
        $copy=Clone $reproduction;$copy.byteIdentical=$false;Refusal {AssertReproduction $copy} 'QP0733_REPRODUCTION';$guardsCount++
        $copy=Clone $smoke;$copy.sourceWorkspace=$true;Refusal {AssertSmoke $copy} 'QP0733_SMOKE';$guardsCount++
        $copy=Clone $sequenceEvidence['sequence-a'];$copy.rollbackConfirmed=$false;Refusal {AssertSequence $copy 'COMPLETE'} 'QP0733_SEQUENCE';$guardsCount++
        $copy=Clone $sequenceEvidence.value;$copy.expectedExit=0;$copy.exit=0;Refusal {AssertSequence $copy 'VALUE'} 'QP0733_SEQUENCE_RECUSAL';$guardsCount++
        $copy=Clone $staticGate;$copy.state='PASS';Refusal {AssertStaticGate $copy $staticRaw $staticCatalog $staticInventory} 'QP0733_STATIC_GATE';$guardsCount++
        $copy=Clone $staticGate;$copy.rawFindingsSuppressed=1;Refusal {AssertStaticGate $copy $staticRaw $staticCatalog $staticInventory} 'QP0733_STATIC_GATE';$guardsCount++
        $copy=Clone $staticGate;$copy.rawFindings++;Refusal {AssertStaticGate $copy $staticRaw $staticCatalog $staticInventory} 'QP0733_STATIC_COUNTS';$guardsCount++
        $copy=Clone $staticGate;$copy.nominalSecurityAcceptance=$true;Refusal {AssertStaticGate $copy $staticRaw $staticCatalog $staticInventory} 'QP0733_STATIC_GATE';$guardsCount++
        $copy=Clone $staticGate;$copy.releaseAcceptance=$true;Refusal {AssertStaticGate $copy $staticRaw $staticCatalog $staticInventory} 'QP0733_STATIC_GATE';$guardsCount++
        $copy=Clone $staticGate;$copy.evidencePins=@($copy.evidencePins|Select-Object -Skip 1);Refusal {AssertStaticProvenance $copy $staticObserved} 'QP0733_STATIC_PROVENANCE';$guardsCount++
        foreach($missingPin in @(($macro+$physical.buildAttempt+'/result.json'),($static.pmdDirectory+'/source-inventory.json'),($macro+$physical.packagePrimary+'/qualification.zip'))){
            $savedPin=$pins[$missingPin];$null=$pins.Remove($missingPin)
            try{Refusal {$null=Receipt $missingPin} ('QP0733_RECEIPT_UNLISTED_'+$missingPin);$guardsCount++}finally{$pins.Add($missingPin,$savedPin)}
        }
        foreach($missingPin in @('stdout.log','stderr.log','before.log','after.log'|ForEach-Object {$macro+$physical.buildAttempt+'/'+$_})){
            $savedPin=$pins[$missingPin];$null=$pins.Remove($missingPin)
            try{Refusal {$null=ReceiptText $missingPin} ('QP0733_RECEIPT_UNLISTED_'+$missingPin);$guardsCount++}finally{$pins.Add($missingPin,$savedPin)}
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if($null -ne $planning){
        foreach($e in $planning.map.Values){if($map.ContainsKey($e.path)){$map[$e.path].after=$e.after}}
        $newFiles+=@($planning.newFiles);$guardsCount+=$planning.guards
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guardsCount;runtimeChanged=$true;currentP07Qualified=$true;currentP08Qualified=$true;nominalApproval=$false;releaseApproved=$false;packageRevision=$packageRevision}
}

Export-ModuleMember -Function Get-QualificacaoP0733Succession,Get-ThreeStagePlanningOverlay

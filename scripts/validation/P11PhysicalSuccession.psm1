#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-P11PhysicalSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog = 'docs/catalogos/p11-fisico/'
    $history = 'docs/continuidade/historico/p11-fisico/'
    $manifestPath = $catalog + 'manifesto.json'
    if (-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))) { return $null }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $oldPath = 'docs/catalogos/p11-regressao-local/manifesto.json'
    $oldHash = '718b87b9e7dd405e5181e719f5feec7b5613bb560b862bea374c78e603c3dbc8'
    Import-Module (Join-Path $PSScriptRoot 'Post0227Succession.psm1')
    $successor = Get-Post0227Succession -Root $Root -SelfTest:$SelfTest
    $utf8 = [Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path) {
        if ($null -ne $successor -and $successor.map.ContainsKey($path)) { $path = $successor.map[$path].snapshot }
        if ($path -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/') -or
            $path -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$') { throw 'P11_PHYSICAL_PATH' }
        $node = Get-Item -LiteralPath (Join-Path $Root $path)
        $file = $node.FullName
        while ($node.FullName -cne $Root) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'P11_PHYSICAL_REPARSE' }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
            if ($null -eq $node) { throw 'P11_PHYSICAL_ESCAPE' }
        }
        return $file
    }
    function Hash([string]$path) { (Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant() }
    function Json([string]$path) { Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608 }
    function Pin([string]$path,[string]$sha) {
        if ($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $path) -cne $sha) { throw ('P11_PHYSICAL_HASH_' + $path) }
    }
    function Exact($a,$b,[string]$errorCode) {
        if (@($a).Count -ne @($b).Count -or (@($a | Sort-Object) -join '|') -cne (@($b | Sort-Object) -join '|')) { throw $errorCode }
    }
    Pin $oldPath $oldHash
    $old = Json $oldPath
    $base = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($e in $old.preservedFiles) { $base.Add($e.path,$e.sha256) }
    foreach ($e in $old.changedExistingFiles) { $base.Add($e.path,$e.after) }
    foreach ($e in $old.newFiles) { $base.Add($e.path,$e.sha256) }
    $base.Add($oldPath,$oldHash)
    $oldSeal = $oldPath.Replace('.json','.sha256')
    if ([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $oldHash) { throw 'P11_PHYSICAL_OLD_SEAL' }
    $base.Add($oldSeal,(Hash $oldSeal))
    Push-Location -LiteralPath $Root
    try {
        $actual = @(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object { $_ -replace '\\','/' })
        if ($LASTEXITCODE -ne 0) { throw 'P11_PHYSICAL_ENUMERATION' }
    } finally { Pop-Location }
    function Check($m) {
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseUsed','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach ($field in @('version','initialFiles','sourceCalls','newAcceptances')) {
            if ($m[$field] -isnot [long] -and $m[$field] -isnot [int]) { throw 'P11_PHYSICAL_SCOPE' }
        }
        if ($m.version -ne 1 -or $m.initialFiles -ne 3754 -or $base.Count -ne 3754 -or
            $m.status -cne 'P11_NATIVE_PASS_FULL_VERIFY_FAILED' -or $m.sourceCalls -ne 0 -or
            $m.databaseUsed -cne $true -or $m.newAcceptances -ne 0 -or $m.construction -cne '39/45' -or
            $m.historicalAcceptances -cne '67/115') { throw 'P11_PHYSICAL_SCOPE' }
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if ($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash) { throw 'P11_PHYSICAL_PREDECESSOR' }
        $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/P11RegressionSuccession.psm1','docs/catalogos/macrobloco-qualificacao-pacote/dependency-lock.json') 'P11_PHYSICAL_DELTA_SET'
        foreach ($e in $m.changedExistingFiles) {
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if (-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history+$e.path)) { throw 'P11_PHYSICAL_BEFORE' }
            Pin $e.snapshot $e.before
            if ($null -eq $e.after) { throw 'P11_PHYSICAL_REMOVAL' }
            Pin $e.path $e.after
            $map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys | Where-Object { -not $map.ContainsKey($_) }) 'P11_PHYSICAL_PRESERVED_SET'
        foreach ($e in $m.preservedFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($e.sha256 -cne $base[$e.path]) { throw 'P11_PHYSICAL_PRESERVED' }
            Pin $e.path $e.sha256
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.newFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))) { throw 'P11_PHYSICAL_NEW_COLLISION' }
            Pin $e.path $e.sha256
        }
        foreach ($e in $m.changedExistingFiles) { if (-not $seen.Contains($e.snapshot)) { throw 'P11_PHYSICAL_SNAPSHOT_UNLISTED' } }
        $revisionFiles = if ($null -ne $successor) { $successor.baselineFiles } else { $actual }
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'P11_PHYSICAL_UNLISTED_DRIFT'
        Assert-QualificationFields $m.evidence @('path','sha256')
        if ($m.evidence.path -cne ($catalog+'resultado.json')) { throw 'P11_PHYSICAL_EVIDENCE_PATH' }
        Pin $m.evidence.path $m.evidence.sha256
        $evidence = Json $m.evidence.path
        if ($evidence.status -cne 'NATIVE_PASS_FULL_VERIFY_FAILED' -or
            $evidence.sourceCalls -ne 0 -or $evidence.databaseUsed -cne $true -or
            $evidence.nativeAuthenticationTested -cne $true -or $evidence.fullPhysicalVerify -cne $false -or
            $evidence.newAcceptances -ne 0 -or $evidence.packageExecuted -cne $false -or
            $evidence.productionUsed -cne $false -or $evidence.domainCommit -cne $false -or
            $evidence.ddl -cne $false) { throw 'P11_PHYSICAL_EVIDENCE_SCOPE' }
        Pin 'pom.xml' $evidence.sourcePomSha256
        foreach ($entry in $evidence.receipts) { Pin $entry.path $entry.sha256 }
        $round='target/p11-fisico-20260921-01/'
        $macro='target/macrobloco-qualificacao-pacote-20260913-01/'
        $native=Json ($round+'native-summary.json')
        if ($native.status -cne 'NATIVE_JDBC_12_8_2_LOCAL_PASS' -or $native.nativeVersion -cne '12.8.2.x64' -or
            $native.tests -ne 1 -or $native.failures -ne 0 -or $native.errors -ne 0 -or $native.skipped -ne 0 -or
            -not $native.rollbackConfirmed -or -not $native.aggregateReadbackEqual) { throw 'P11_PHYSICAL_NATIVE' }
        foreach ($entry in $native.receipts) { Pin $entry.path $entry.sha256 }
        $diagnostic=Json ($round+'diagnostic-summary.json')
        if ($diagnostic.status -cne 'ORIGINAL_SCALE_CLASS_PASS_UNCHANGED' -or
            $diagnostic.tests -ne 4 -or $diagnostic.errors -ne 0 -or $diagnostic.failures -ne 0 -or
            $diagnostic.codeChanged -or $diagnostic.timeLimitsChanged -or $diagnostic.assertionsChanged -or
            -not $diagnostic.rollbackConfirmed -or -not $diagnostic.baselineEqual) { throw 'P11_PHYSICAL_SCALE_DIAGNOSTIC' }
        foreach ($entry in $diagnostic.receipts) { Pin $entry.path $entry.sha256 }
        $failed=Json ($macro+'p11-p07-verify-01/result.json')
        if ($failed.exit -ne 1 -or -not $failed.rollbackConfirmed -or $failed.timedOut) { throw 'P11_PHYSICAL_FAILED_HISTORY' }
        if ($evidence.failedRuns.Count -ne 2) { throw 'P11_PHYSICAL_FAILED_RUNS' }
        foreach ($run in $evidence.failedRuns) {
            $build=Json ($macro+$run.attempt+'/result.json')
            if ($build.exit -ne 1 -or $build.phase -cne 'VerifyPhysical' -or
                -not $build.rollbackConfirmed -or -not $build.logUtf8Integrity) { throw 'P11_PHYSICAL_FAILURE_RECEIPT' }
            foreach ($group in @('unit','integration')) {
                $stats=@{tests=0;errors=0;failures=0;skipped=0;classes=0}
                foreach ($report in $run[$group].reports) {
                    Pin $report.path $report.sha256
                    [xml]$xml=[IO.File]::ReadAllText((Physical $report.path),$utf8)
                    foreach ($field in @('tests','errors','failures','skipped')) { $stats[$field]+=[int]$xml.testsuite.GetAttribute($field) }
                    $stats.classes++
                }
                foreach ($field in $stats.Keys) { if ($stats[$field] -ne $run[$group][$field]) { throw 'P11_PHYSICAL_REPORT_TOTAL' } }
            }
            if ($run.unit.tests -ne 2162 -or $run.unit.skipped -ne 4 -or $run.unit.errors -ne 0 -or
                $run.unit.failures -ne 0 -or $run.integration.errors -lt 1) { throw 'P11_PHYSICAL_FAILED_REPORTS' }
        }
        Exact $evidence.failedRuns.attempt @('p11-p07-verify-01','p11-p07-verify-02') 'P11_PHYSICAL_FAILED_IDENTITIES'
        $stopped=Json ($round+'pipeline-result-02.json')
        if ($stopped.state -cne 'STOPPED' -or $stopped.reason -cne 'P07_NOT_PASS') { throw 'P11_PHYSICAL_DEPENDENCY_STOP' }
        foreach ($name in @('p11-package-primary-01','p11-package-reproduction-01','p11-smoke-01','p11-sequence-a-01','p11-sequence-b-01')) {
            if (Test-Path -LiteralPath (Join-Path $Root ($macro+$name))) { throw 'P11_PHYSICAL_PACKAGE_UNEXPECTED' }
        }
        $gates=Json ($macro+'p11-manifest-gates-diagnostic-01/result.json')
        if ($gates.exit -ne $evidence.manifestDiagnostic.exit -or $gates.rollbackConfirmed -cne $true) { throw 'P11_PHYSICAL_MANIFEST_DIAGNOSTIC' }
        foreach ($name in @('master','audit','tables')) {
            if ((Hash ($round+$name+'-before.log')) -cne (Hash ($round+$name+'-after-final.log'))) { throw 'P11_PHYSICAL_FINAL_AGGREGATES' }
        }
        $search=Json ($catalog+'investigacao-inputs.json')
        if ($search.openExternalRequirements -ne 16 -or $search.newAcceptances -ne 0 -or $search.remoteCount -ne 0) { throw 'P11_PHYSICAL_SEARCH' }
        foreach ($requirement in $search.requirements) {
            if ($null -ne $requirement.nominalOwner -or $requirement.newNominalAcceptance) { throw 'P11_PHYSICAL_FALSE_ACCEPTANCE' }
            foreach ($entry in $requirement.evidence) { Pin $entry.path $entry.sha256 }
        }
        $matrix=Json ($catalog+'matriz-atual.json')
        if ($matrix.version -ne 3 -or $matrix.rounds.Count -ne 4 -or $matrix.openExternalRequirements -ne 16 -or
            $matrix.newAcceptances -ne 0 -or $matrix.rounds[3].externalNetworkCalls -ne 4 -or
            $matrix.rounds[3].databaseUsed -cne $true -or $null -ne $matrix.rounds[3].databaseCalls -or
            $matrix.rounds[1].countStatus -cne 'TOTAL_NAO_INSTRUMENTADO' -or
            $null -ne $matrix.rounds[1].externalNetworkCalls -or $matrix.rounds[1].ledgerReceivedCalls -ne 7) { throw 'P11_PHYSICAL_MATRIX' }
        $requirements=@($matrix.stages | ForEach-Object {$_.requirements})
        if ($requirements.Count -ne 17 -or @($requirements | Where-Object status -ceq 'BLOQUEADO_POR_INPUT').Count -ne 16 -or
            @($requirements | Where-Object {$null -ne $_.nominalOwner}).Count -ne 0) { throw 'P11_PHYSICAL_MATRIX_ACCEPTANCE' }
        if ($matrix.technicalWork[0].status -cne 'QUALIFICADO_NO_ESCOPO_LOCAL' -or
            $matrix.technicalWork[0].countsReadThisRound -cne $true -or
            $matrix.technicalWork[1].status -cne 'NAO_QUALIFICADO_FALHA_DE_EXECUCAO' -or
            $null -ne $matrix.technicalWork[0].remainingRequirement -or
            $null -eq $matrix.technicalWork[1].remainingRequirement) { throw 'P11_PHYSICAL_MATRIX_TECHNICAL' }

        foreach ($p in @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md')) {
            if (-not [IO.File]::ReadAllText((Physical $p),$utf8).Contains($m.status)) { throw 'P11_PHYSICAL_POINTER' }
        }
        return ,$map
    }
    Pin $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest = Json $manifestPath
    $map = Check $manifest
    $guards = 0
    if ($SelfTest) {
        foreach ($test in @(
            @('P11_PHYSICAL_SCOPE',{param($m)$m.sourceCalls=1}),
            @('P11_PHYSICAL_SCOPE',{param($m)$m.databaseUsed=$false}),
            @('P11_PHYSICAL_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('P11_PHYSICAL_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('P11_PHYSICAL_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='pom.xml'}),
            @('P11_PHYSICAL_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('P11_PHYSICAL_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('P11_PHYSICAL_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64}),
            @('P11_PHYSICAL_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles | Select-Object -Skip 1)}),
            @(('P11_PHYSICAL_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )) {
            $copy = Read-QualificationJsonBytes ($utf8.GetBytes(($manifest | ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('P11_PHYSICAL_GUARD_'+$reason)};$guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if ($null -ne $successor) {
        foreach ($entry in $successor.map.Values) {
            if ($map.ContainsKey($entry.path)) {
                $previous=$map[$entry.path]
                if ($previous.after -cne $entry.before) { throw 'POST0227_CHAIN_BREAK' }
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            } else { $map.Add($entry.path,$entry) }
        }
        $newFiles=@(@($newFiles)+@($successor.newFiles) | Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;successor=$successor}
}
Export-ModuleMember -Function Get-P11PhysicalSuccession

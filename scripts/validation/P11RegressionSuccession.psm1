#Requires -Version 7.5
Set-StrictMode -Version Latest


function Test-P11RegressionMatrix {
    param([Parameter(Mandatory)]$Matrix)
    if ($Matrix.version -ne 2 -or $Matrix.scope -cne 'P10_P11_P12_P14_P15_P21_POS0220' -or
        $Matrix.Contains('networkCalls') -or $Matrix.openExternalRequirements -ne 16 -or
        $Matrix.newAcceptances -ne 0 -or $Matrix.construction -cne '39/45' -or $Matrix.acceptances -cne '67/115') { throw 'P11_MATRIX_SCOPE' }
    $rounds = @($Matrix.rounds)
    if ($rounds.Count -ne 3 -or $rounds[0].id -cne 'PREPARACAO_OFFLINE' -or
        $rounds[0].externalNetworkCalls -ne 0 -or $rounds[1].id -cne 'P11_PUBLICO_0220' -or
        $rounds[1].externalNetworkUsed -cne $true -or $null -ne $rounds[1].externalNetworkCalls -or
        $rounds[1].countStatus -cne 'TOTAL_NAO_INSTRUMENTADO' -or $rounds[1].ledgerReceivedCalls -ne 7 -or
        $rounds[2].id -cne 'P11_REGRESSAO_0221' -or $rounds[2].externalNetworkCalls -ne 0) { throw 'P11_MATRIX_ROUNDS' }
    $requirements = @($Matrix.stages | ForEach-Object { $_.requirements })
    if ($requirements.Count -ne 17 -or @($requirements | Where-Object status -ceq 'BLOQUEADO_POR_INPUT').Count -ne 16 -or
        @($requirements | Where-Object { $null -ne $_.nominalOwner }).Count -ne 0 -or
        @($requirements | Where-Object { $_.id -ceq 'FEED-ACHADOS' -and $_.status -ceq 'CORRIGIDO_E_TESTADO_NA_CAMADA_JAVA_NVD' }).Count -ne 1) { throw 'P11_MATRIX_REQUIREMENTS' }
    return $true
}

function Get-P11RegressionSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog = 'docs/catalogos/p11-regressao-local/'
    $history = 'docs/continuidade/historico/p11-regressao-local/'
    $manifestPath = $catalog + 'manifesto.json'
    if (-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))) { return $null }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $oldPath = 'docs/catalogos/p11-publico-corrigido/manifesto.json'
    $oldHash = '574a148c5c9fca87dcd73dc5badd6c5454ca0ed9f3bce519b7cb537cf4342214'
    Import-Module (Join-Path $PSScriptRoot 'P11PhysicalSuccession.psm1')
    $physical = Get-P11PhysicalSuccession -Root $Root -SelfTest:$SelfTest
    $utf8 = [Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path) {
        if ($null -ne $physical -and $physical.map.ContainsKey($path)) { $path = $physical.map[$path].snapshot }
        if ($path -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/') -or
            $path -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$') { throw 'P11_REGRESSION_PATH' }
        $node = Get-Item -LiteralPath (Join-Path $Root $path)
        $file = $node.FullName
        while ($node.FullName -cne $Root) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'P11_REGRESSION_REPARSE' }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
            if ($null -eq $node) { throw 'P11_REGRESSION_ESCAPE' }
        }
        return $file
    }
    function Hash([string]$path) { (Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant() }
    function Json([string]$path) { Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608 }
    function Pin([string]$path,[string]$sha) {
        if ($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $path) -cne $sha) { throw ('P11_REGRESSION_HASH_' + $path) }
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
    if ([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $oldHash) { throw 'P11_REGRESSION_OLD_SEAL' }
    $base.Add($oldSeal,(Hash $oldSeal))
    Push-Location -LiteralPath $Root
    try {
        $actual = @(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object { $_ -replace '\\','/' })
        if ($LASTEXITCODE -ne 0) { throw 'P11_REGRESSION_ENUMERATION' }
    } finally { Pop-Location }
    function Check($m) {
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseCalls','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach ($field in @('version','initialFiles','sourceCalls','databaseCalls','newAcceptances')) {
            if ($m[$field] -isnot [long] -and $m[$field] -isnot [int]) { throw 'P11_REGRESSION_SCOPE' }
        }
        if ($m.version -ne 1 -or $m.initialFiles -ne 3739 -or $base.Count -ne 3739 -or
            $m.status -cne 'P11_LOCAL_REGRESSION_RECORDED' -or $m.sourceCalls -ne 0 -or
            $m.databaseCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.construction -cne '39/45' -or
            $m.historicalAcceptances -cne '67/115') { throw 'P11_REGRESSION_SCOPE' }
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if ($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash) { throw 'P11_REGRESSION_PREDECESSOR' }
        $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','scripts/validation/P11PublicAuditSuccession.psm1') 'P11_REGRESSION_DELTA_SET'
        foreach ($e in $m.changedExistingFiles) {
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if (-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history+$e.path)) { throw 'P11_REGRESSION_BEFORE' }
            Pin $e.snapshot $e.before
            if ($null -eq $e.after) { throw 'P11_REGRESSION_REMOVAL' }
            Pin $e.path $e.after
            $map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys | Where-Object { -not $map.ContainsKey($_) }) 'P11_REGRESSION_PRESERVED_SET'
        foreach ($e in $m.preservedFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($e.sha256 -cne $base[$e.path]) { throw 'P11_REGRESSION_PRESERVED' }
            Pin $e.path $e.sha256
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.newFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))) { throw 'P11_REGRESSION_NEW_COLLISION' }
            Pin $e.path $e.sha256
        }
        foreach ($e in $m.changedExistingFiles) { if (-not $seen.Contains($e.snapshot)) { throw 'P11_REGRESSION_SNAPSHOT_UNLISTED' } }
        $revisionFiles = if ($null -ne $physical) { $physical.baselineFiles } else { $actual }
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'P11_REGRESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $m.evidence @('path','sha256')
        if ($m.evidence.path -cne ($catalog+'resultado.json')) { throw 'P11_REGRESSION_EVIDENCE_PATH' }
        Pin $m.evidence.path $m.evidence.sha256
        $evidence = Json $m.evidence.path
        if ($evidence.status -cne 'LOCAL_REGRESSION_PASS_PHYSICAL_PENDING' -or
            $evidence.externalNetworkCalls -ne 0 -or $evidence.databaseCalls -ne 0 -or
            $evidence.nativeAuthenticationTested -cne $false -or $evidence.fullPhysicalVerify -cne $false -or
            $evidence.newAcceptances -ne 0 -or $evidence.tests.failures -ne 0 -or
            $evidence.tests.errors -ne 0 -or $evidence.tests.total -lt 2162 -or
            $evidence.packageExecuted -cne $false) { throw 'P11_REGRESSION_EVIDENCE_SCOPE' }
        Pin 'pom.xml' $evidence.sourcePomSha256
        $matrix = Json ($catalog+'matriz-atual.json')
        $null = Test-P11RegressionMatrix -Matrix $matrix
        foreach ($entry in $evidence.receipts) { Pin $entry.path $entry.sha256 }
        foreach ($p in @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md')) {
            if (-not [IO.File]::ReadAllText((Physical $p),$utf8).Contains($m.status)) { throw 'P11_REGRESSION_POINTER' }
        }
        return ,$map
    }
    Pin $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest = Json $manifestPath
    $map = Check $manifest
    $guards = 0
    if ($SelfTest) {
        foreach ($test in @(
            @('P11_REGRESSION_SCOPE',{param($m)$m.sourceCalls=1}),
            @('P11_REGRESSION_SCOPE',{param($m)$m.databaseCalls=$false}),
            @('P11_REGRESSION_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('P11_REGRESSION_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('P11_REGRESSION_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='pom.xml'}),
            @('P11_REGRESSION_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('P11_REGRESSION_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('P11_REGRESSION_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64}),
            @('P11_REGRESSION_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles | Select-Object -Skip 1)}),
            @(('P11_REGRESSION_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )) {
            $copy = Read-QualificationJsonBytes ($utf8.GetBytes(($manifest | ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('P11_REGRESSION_GUARD_'+$reason)};$guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if ($null -ne $physical) {
        foreach ($entry in $physical.map.Values) {
            if ($map.ContainsKey($entry.path)) {
                $previous=$map[$entry.path]
                if ($previous.after -cne $entry.before) { throw 'P11_PHYSICAL_CHAIN_BREAK' }
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            } else { $map.Add($entry.path,$entry) }
        }
        $newFiles=@(@($newFiles)+@($physical.newFiles) | Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;successor=$physical}
}
Export-ModuleMember -Function Get-P11RegressionSuccession,Test-P11RegressionMatrix

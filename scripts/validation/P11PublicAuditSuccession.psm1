#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-P11PublicAuditSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog = 'docs/catalogos/p11-publico-corrigido/'
    $history = 'docs/continuidade/historico/p11-publico-corrigido/'
    $manifestPath = $catalog + 'manifesto.json'
    if (-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))) { return $null }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $oldPath = 'docs/catalogos/p11-cache-offline/manifesto.json'
    $oldHash = '33b11f1909642d689ed28266a32b9bbc276c4362726d56b291ede4a219c4cb6e'
    Import-Module (Join-Path $PSScriptRoot 'P11RegressionSuccession.psm1')
    $regression = Get-P11RegressionSuccession -Root $Root -SelfTest:$SelfTest
    $utf8 = [Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path) {
        if ($null -ne $regression -and $regression.map.ContainsKey($path)) { $path = $regression.map[$path].snapshot }
        if ($path -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/') -or
            $path -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$') { throw 'P11_PUBLIC_PATH' }
        $node = Get-Item -LiteralPath (Join-Path $Root $path)
        $file = $node.FullName
        while ($node.FullName -cne $Root) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'P11_PUBLIC_REPARSE' }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
            if ($null -eq $node) { throw 'P11_PUBLIC_ESCAPE' }
        }
        return $file
    }
    function Hash([string]$path) { (Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant() }
    function Json([string]$path) { Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608 }
    function Pin([string]$path,[string]$sha) {
        if ($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $path) -cne $sha) { throw ('P11_PUBLIC_HASH_' + $path) }
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
    if ([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $oldHash) { throw 'P11_PUBLIC_OLD_SEAL' }
    $base.Add($oldSeal,(Hash $oldSeal))
    Push-Location -LiteralPath $Root
    try {
        $actual = @(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object { $_ -replace '\\','/' })
        if ($LASTEXITCODE -ne 0) { throw 'P11_PUBLIC_ENUMERATION' }
    } finally { Pop-Location }
    function Check($m) {
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseCalls','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach ($field in @('version','initialFiles','sourceCalls','databaseCalls','newAcceptances')) {
            if ($m[$field] -isnot [long] -and $m[$field] -isnot [int]) { throw 'P11_PUBLIC_SCOPE' }
        }
        if ($m.version -ne 1 -or $m.initialFiles -ne 3722 -or $base.Count -ne 3722 -or
            $m.status -cne 'P11_PUBLIC_FEED_PATCHED' -or $m.sourceCalls -ne 0 -or
            $m.databaseCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.construction -cne '39/45' -or
            $m.historicalAcceptances -cne '67/115') { throw 'P11_PUBLIC_SCOPE' }
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if ($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash) { throw 'P11_PUBLIC_PREDECESSOR' }
        $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','docs/catalogos/campanhas-integrais/ENTRADAS-E-EFEITOS-EXTERNOS.md','scripts/validation/P11CachedAuditSuccession.psm1','scripts/validation/Test-OfflinePreparationP10P21.ps1','scripts/validation/Test-DependencyVulnerabilityPolicy.ps1','pom.xml') 'P11_PUBLIC_DELTA_SET'
        foreach ($e in $m.changedExistingFiles) {
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if (-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history+$e.path)) { throw 'P11_PUBLIC_BEFORE' }
            Pin $e.snapshot $e.before
            if ($null -eq $e.after) { throw 'P11_PUBLIC_REMOVAL' }
            Pin $e.path $e.after
            $map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys | Where-Object { -not $map.ContainsKey($_) }) 'P11_PUBLIC_PRESERVED_SET'
        foreach ($e in $m.preservedFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($e.sha256 -cne $base[$e.path]) { throw 'P11_PUBLIC_PRESERVED' }
            Pin $e.path $e.sha256
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.newFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))) { throw 'P11_PUBLIC_NEW_COLLISION' }
            Pin $e.path $e.sha256
        }
        foreach ($e in $m.changedExistingFiles) { if (-not $seen.Contains($e.snapshot)) { throw 'P11_PUBLIC_SNAPSHOT_UNLISTED' } }
        $revisionFiles = if ($null -ne $regression) { $regression.baselineFiles } else { $actual }
        Exact $revisionFiles @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'P11_PUBLIC_UNLISTED_DRIFT'
        Assert-QualificationFields $m.evidence @('path','sha256')
        if ($m.evidence.path -cne ($catalog+'resultado.json')) { throw 'P11_PUBLIC_EVIDENCE_PATH' }
        Pin $m.evidence.path $m.evidence.sha256
        $evidence = Json $m.evidence.path
        if ($evidence.status -cne 'PATCHED_SCAN_PASS' -or $evidence.dependencies -ne 13 -or
            $evidence.findings -ne 0 -or $evidence.scannerErrors -ne 0 -or $evidence.exitCode -ne 0 -or
            $evidence.feedUpdated -cne $true -or $evidence.newAcceptances -ne 0 -or
            $evidence.baselineHumanAccepted -cne $false -or $evidence.tests.failures -ne 0 -or
            $evidence.tests.errors -ne 0 -or $evidence.tests.total -lt 122) { throw 'P11_PUBLIC_EVIDENCE_SCOPE' }
        Pin 'pom.xml' $evidence.sourcePomSha256
        $report = Json $evidence.reportPath
        if ($report.dependencies.Count -ne 13 -or
            ($report.scanInfo.Contains('analysisExceptions') -and @($report.scanInfo.analysisExceptions).Count -ne 0)) { throw 'P11_PUBLIC_REPORT' }
        foreach ($dependency in $report.dependencies) {
            if ($dependency.Contains('vulnerabilities') -and @($dependency.vulnerabilities).Count -ne 0) { throw 'P11_PUBLIC_FINDINGS' }
        }
        [xml]$pom = [IO.File]::ReadAllText((Physical 'pom.xml'),$utf8)
        if ($pom.project.properties.'jackson.version' -cne '2.18.11' -or
            $pom.project.properties.'mssql.jdbc.version' -cne '12.8.2.jre11' -or
            $pom.project.properties.'mssql.jdbc.auth.x64.version' -cne '12.8.2.x64') { throw 'P11_PUBLIC_PINS' }
        foreach ($entry in $evidence.receipts) { Pin $entry.path $entry.sha256 }
        foreach ($p in @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md')) {
            if (-not [IO.File]::ReadAllText((Physical $p),$utf8).Contains($m.status)) { throw 'P11_PUBLIC_POINTER' }
        }
        return ,$map
    }
    Pin $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest = Json $manifestPath
    $map = Check $manifest
    $guards = 0
    if ($SelfTest) {
        foreach ($test in @(
            @('P11_PUBLIC_SCOPE',{param($m)$m.sourceCalls=1}),
            @('P11_PUBLIC_SCOPE',{param($m)$m.databaseCalls=$false}),
            @('P11_PUBLIC_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('P11_PUBLIC_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('P11_PUBLIC_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='pom.xml'}),
            @('P11_PUBLIC_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('P11_PUBLIC_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('P11_PUBLIC_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64}),
            @('P11_PUBLIC_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles | Select-Object -Skip 1)}),
            @(('P11_PUBLIC_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )) {
            $copy = Read-QualificationJsonBytes ($utf8.GetBytes(($manifest | ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('P11_PUBLIC_GUARD_'+$reason)};$guards++
        }
    }
    $newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))
    if ($null -ne $regression) {
        foreach ($entry in $regression.map.Values) {
            if ($map.ContainsKey($entry.path)) {
                $previous=$map[$entry.path]
                if ($previous.after -cne $entry.before) { throw 'P11_REGRESSION_CHAIN_BREAK' }
                $map[$entry.path]=@{path=$entry.path;before=$previous.before;after=$entry.after;snapshot=$previous.snapshot}
            } else { $map.Add($entry.path,$entry) }
        }
        $newFiles=@(@($newFiles)+@($regression.newFiles) | Sort-Object -Unique)
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=$newFiles;manifest=$manifest;guards=$guards;successor=$regression}
}
Export-ModuleMember -Function Get-P11PublicAuditSuccession

#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-P11CachedAuditSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92,[char]47)
    $catalog = 'docs/catalogos/p11-cache-offline/'
    $history = 'docs/continuidade/historico/p11-cache-offline/'
    $manifestPath = $catalog + 'manifesto.json'
    if (-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))) { return $null }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $oldPath = 'docs/catalogos/continuidade-pos0216/manifesto.json'
    $oldHash = 'feccbc554f7c539e47c23acdcfd59a901b203132b3dffc58f5c12f2a2b24e6f3'
    $utf8 = [Text.UTF8Encoding]::new($false,$true)
    function Physical([string]$path) {
        if ($path -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/') -or
            $path -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$') { throw 'P11_CACHE_PATH' }
        $node = Get-Item -LiteralPath (Join-Path $Root $path)
        $file = $node.FullName
        while ($node.FullName -cne $Root) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'P11_CACHE_REPARSE' }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
            if ($null -eq $node) { throw 'P11_CACHE_ESCAPE' }
        }
        return $file
    }
    function Hash([string]$path) { (Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant() }
    function Json([string]$path) { Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608 }
    function Pin([string]$path,[string]$sha) {
        if ($sha -cnotmatch '^[a-f0-9]{64}$' -or (Hash $path) -cne $sha) { throw ('P11_CACHE_HASH_' + $path) }
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
    if ([IO.File]::ReadAllText((Physical $oldSeal),$utf8).Trim() -cne $oldHash) { throw 'P11_CACHE_OLD_SEAL' }
    $base.Add($oldSeal,(Hash $oldSeal))
    Push-Location -LiteralPath $Root
    try {
        $actual = @(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object { $_ -replace '\\','/' })
        if ($LASTEXITCODE -ne 0) { throw 'P11_CACHE_ENUMERATION' }
    } finally { Pop-Location }
    function Check($m) {
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseCalls','newAcceptances','construction','historicalAcceptances','changedExistingFiles','preservedFiles','newFiles','evidence')
        foreach ($field in @('version','initialFiles','sourceCalls','databaseCalls','newAcceptances')) {
            if ($m[$field] -isnot [long] -and $m[$field] -isnot [int]) { throw 'P11_CACHE_SCOPE' }
        }
        if ($m.version -ne 1 -or $m.initialFiles -ne 3709 -or $base.Count -ne 3709 -or
            $m.status -cne 'P11_CACHED_SCAN_COMPLETED_FINDINGS_OPEN' -or $m.sourceCalls -ne 0 -or
            $m.databaseCalls -ne 0 -or $m.newAcceptances -ne 0 -or $m.construction -cne '39/45' -or
            $m.historicalAcceptances -cne '67/115') { throw 'P11_CACHE_SCOPE' }
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if ($m.predecessor.path -cne $oldPath -or $m.predecessor.sha256 -cne $oldHash) { throw 'P11_CACHE_PREDECESSOR' }
        $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        Exact $m.changedExistingFiles.path @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md','docs/catalogos/campanhas-integrais/ENTRADAS-E-EFEITOS-EXTERNOS.md','scripts/validation/OfflinePreparationSuccession.psm1') 'P11_CACHE_DELTA_SET'
        foreach ($e in $m.changedExistingFiles) {
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if (-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history+$e.path)) { throw 'P11_CACHE_BEFORE' }
            Pin $e.snapshot $e.before
            if ($null -eq $e.after) { throw 'P11_CACHE_REMOVAL' }
            Pin $e.path $e.after
            $map.Add($e.path,$e)
        }
        Exact $m.preservedFiles.path @($base.Keys | Where-Object { -not $map.ContainsKey($_) }) 'P11_CACHE_PRESERVED_SET'
        foreach ($e in $m.preservedFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($e.sha256 -cne $base[$e.path]) { throw 'P11_CACHE_PRESERVED' }
            Pin $e.path $e.sha256
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.newFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath,($catalog+'manifesto.sha256'))) { throw 'P11_CACHE_NEW_COLLISION' }
            Pin $e.path $e.sha256
        }
        foreach ($e in $m.changedExistingFiles) { if (-not $seen.Contains($e.snapshot)) { throw 'P11_CACHE_SNAPSHOT_UNLISTED' } }
        Exact $actual @(@($base.Keys)+@($m.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'))) 'P11_CACHE_UNLISTED_DRIFT'
        Assert-QualificationFields $m.evidence @('path','sha256')
        if ($m.evidence.path -cne ($catalog+'resultado.json')) { throw 'P11_CACHE_EVIDENCE_PATH' }
        Pin $m.evidence.path $m.evidence.sha256
        $evidence = Json $m.evidence.path
        if ($evidence.status -cne 'COMPLETED_WITH_FINDINGS' -or $evidence.dependencies -ne 13 -or
            $evidence.findings.Count -ne 4 -or $evidence.scannerErrors -ne 0 -or $evidence.exitCode -ne 1 -or
            $evidence.freshFeedAccepted -cne $false -or $evidence.newAcceptances -ne 0 -or
            $evidence.networkGuardActive -cne $true -or $evidence.originalCachePreserved -cne $true) { throw 'P11_CACHE_EVIDENCE_SCOPE' }
        Exact $evidence.findings.cve @('CVE-2026-54512','CVE-2026-54514','CVE-2026-54515','CVE-2025-59250') 'P11_CACHE_FINDINGS'
        foreach ($entry in $evidence.receipts) { Pin $entry.path $entry.sha256 }
        foreach ($p in @('STATES.md','TRILHA_CONCLUSAO_POR_MODELO.md','docs/continuidade/RETOMADA.md')) {
            if (-not [IO.File]::ReadAllText((Physical $p),$utf8).Contains($m.status)) { throw 'P11_CACHE_POINTER' }
        }
        return ,$map
    }
    Pin $manifestPath ([IO.File]::ReadAllText((Physical ($catalog+'manifesto.sha256')),$utf8).Trim())
    $manifest = Json $manifestPath
    $map = Check $manifest
    $guards = 0
    if ($SelfTest) {
        foreach ($test in @(
            @('P11_CACHE_SCOPE',{param($m)$m.sourceCalls=1}),
            @('P11_CACHE_SCOPE',{param($m)$m.databaseCalls=$false}),
            @('P11_CACHE_SCOPE',{param($m)$m.historicalAcceptances='68/115'}),
            @('P11_CACHE_PREDECESSOR',{param($m)$m.predecessor.sha256='0'*64}),
            @('P11_CACHE_DELTA_SET',{param($m)$m.changedExistingFiles[0].path='pom.xml'}),
            @('P11_CACHE_BEFORE',{param($m)$m.changedExistingFiles[0].snapshot='../outside'}),
            @('P11_CACHE_REMOVAL',{param($m)$m.changedExistingFiles[0].after=$null}),
            @('P11_CACHE_PRESERVED',{param($m)$m.preservedFiles[0].sha256='0'*64}),
            @('P11_CACHE_PRESERVED_SET',{param($m)$m.preservedFiles=@($m.preservedFiles | Select-Object -Skip 1)}),
            @(('P11_CACHE_HASH_'+$manifest.changedExistingFiles[0].path),{param($m)$m.changedExistingFiles[0].after='0'*64})
        )) {
            $copy = Read-QualificationJsonBytes ($utf8.GetBytes(($manifest | ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason='ACCEPTED';try{$null=Check $copy}catch{$reason=$_.Exception.Message}
            if($reason -cne $test[0]){throw ('P11_CACHE_GUARD_'+$reason)};$guards++
        }
    }
    return [pscustomobject]@{map=$map;baselineFiles=@($base.Keys);newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256'));manifest=$manifest;guards=$guards}
}
Export-ModuleMember -Function Get-P11CachedAuditSuccession

#Requires -Version 7.5
Set-StrictMode -Version Latest

function Get-OfflinePreparationSuccession {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Root, [switch]$SelfTest)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92, [char]47)
    $catalog = 'docs/catalogos/continuidade-pos0216/'
    $history = 'docs/continuidade/historico/pos0216/'
    $manifestPath = $catalog + 'manifesto.json'
    if (-not (Test-Path -LiteralPath (Join-Path $Root $manifestPath))) { return $null }
    Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1')
    $predecessor = 'docs/catalogos/p06-revisao/manifesto.json'
    $predecessorHash = 'a0c23aa471941dbdf249f04755377d2728d1f7d159ce3d6ab177142d2c0af7f2'
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    function Physical([string]$path) {
        if ($path -cnotmatch '^[\p{L}\p{N}_.$/-]+$' -or $path.Contains('..') -or $path.StartsWith('/') -or
            $path -match '(?i)(^|/)\.env|\.(pem|key|pfx|p12|crt|cer)$') { throw 'OFFLINE_SUCCESSION_PATH' }
        $node = Get-Item -LiteralPath (Join-Path $Root $path)
        $file = $node.FullName
        while ($node.FullName -cne $Root) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'OFFLINE_SUCCESSION_REPARSE' }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
            if ($null -eq $node) { throw 'OFFLINE_SUCCESSION_ESCAPE' }
        }
        return $file
    }
    function Digest([string]$path) { (Get-FileHash -LiteralPath (Physical $path)).Hash.ToLowerInvariant() }
    function ReadJson([string]$path) { Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Physical $path))) 8388608 }
    function CheckHash([string]$path, [string]$hash) {
        if ($hash -cnotmatch '^[a-f0-9]{64}$' -or (Digest $path) -cne $hash) { throw ('OFFLINE_SUCCESSION_HASH_' + $path) }
    }
    function Exact($a, $b, [string]$reason) {
        if (@($a).Count -ne @($b).Count -or (@($a | Sort-Object) -join '|') -cne (@($b | Sort-Object) -join '|')) { throw $reason }
    }
    CheckHash $predecessor $predecessorHash
    $old = ReadJson $predecessor
    $base = [Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach ($e in $old.preservedFiles) { $base.Add($e.path, $e.sha256) }
    foreach ($e in $old.changedExistingFiles) { if ($null -ne $e.after) { $base.Add($e.path, $e.after) } }
    foreach ($e in $old.newFiles) { $base.Add($e.path, $e.sha256) }
    $base.Add($predecessor, $predecessorHash)
    $oldSeal = 'docs/catalogos/p06-revisao/manifesto.sha256'
    if ([IO.File]::ReadAllText((Physical $oldSeal), $utf8).Trim() -cne $predecessorHash) { throw 'OFFLINE_SUCCESSION_PREDECESSOR_SEAL' }
    $base.Add($oldSeal, (Digest $oldSeal))
    Push-Location -LiteralPath $Root
    try {
        $actual = @(rg --files --hidden -g '!.git' -g '!target' -g '!logs' -g '!.env*' -g '!**/*secret*' -g '!**/*credential*' | ForEach-Object { $_ -replace '\\','/' })
        if ($LASTEXITCODE -ne 0) { throw 'OFFLINE_SUCCESSION_ENUMERATION' }
    } finally { Pop-Location }
    function CheckManifest($m) {
        Assert-QualificationFields $m @('version','status','initialFiles','predecessor','sourceCalls','databaseCalls','newAcceptances','construction','historicalAcceptances','checkpoint','changedExistingFiles','preservedFiles','newFiles','fleet')
        foreach ($field in @('version','initialFiles','sourceCalls','databaseCalls','newAcceptances')) {
            if ($m[$field] -isnot [long] -and $m[$field] -isnot [int]) { throw 'OFFLINE_SUCCESSION_SCOPE' }
        }
        if ($m.version -ne 1 -or $m.initialFiles -ne $base.Count -or $m.status -cne 'CORRECAO_LOCAL_POS0216' -or
            $m.sourceCalls -ne 0 -or $m.databaseCalls -ne 0 -or $m.newAcceptances -ne 0 -or
            $m.construction -cne '39/45' -or $m.historicalAcceptances -cne '67/115') { throw 'OFFLINE_SUCCESSION_SCOPE' }
        Assert-QualificationFields $m.predecessor @('path','sha256')
        if ($m.predecessor.path -cne $predecessor -or $m.predecessor.sha256 -cne $predecessorHash) { throw 'OFFLINE_SUCCESSION_PREDECESSOR' }
        $map = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.changedExistingFiles) {
            Assert-QualificationFields $e @('path','before','after','snapshot')
            if (-not $base.ContainsKey($e.path) -or $e.before -cne $base[$e.path] -or $e.snapshot -cne ($history + $e.path)) { throw 'OFFLINE_SUCCESSION_BEFORE' }
            if ($e.path -cmatch '^docs/continuidade/(historico|checkpoints)/|/manifesto\.(json|sha256)$|^database/migrations/') { throw 'OFFLINE_SUCCESSION_IMMUTABLE_PREDECESSOR' }
            CheckHash $e.snapshot $e.before
            if ($null -eq $e.after) { throw 'OFFLINE_SUCCESSION_REMOVAL' }
            CheckHash $e.path $e.after
            $map.Add($e.path, $e)
        }
        Exact $m.preservedFiles.path @($base.Keys | Where-Object { -not $map.ContainsKey($_) }) 'OFFLINE_SUCCESSION_PRESERVED_SET'
        foreach ($e in $m.preservedFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($e.sha256 -cne $base[$e.path]) { throw 'OFFLINE_SUCCESSION_PRESERVED' }
            CheckHash $e.path $e.sha256
        }
        $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($e in $m.newFiles) {
            Assert-QualificationFields $e @('path','sha256')
            if ($base.ContainsKey($e.path) -or -not $seen.Add($e.path) -or $e.path -cin @($manifestPath, ($catalog + 'manifesto.sha256'))) { throw 'OFFLINE_SUCCESSION_NEW_COLLISION' }
            CheckHash $e.path $e.sha256
        }
        foreach ($e in $m.changedExistingFiles) { if (-not $seen.Contains($e.snapshot)) { throw 'OFFLINE_SUCCESSION_SNAPSHOT_UNLISTED' } }
        Exact $actual @(@($base.Keys) + @($m.newFiles.path) + @($manifestPath, ($catalog + 'manifesto.sha256'))) 'OFFLINE_SUCCESSION_UNLISTED_DRIFT'
        Assert-QualificationFields $m.fleet @('path','before','after','snapshot','decision','decisionSha256')
        $f = $m.fleet
        if ($f.path -cne 'src/main/java/br/com/esl/etl/v2/modulos/manifestos/aplicacao/ManifestoDataExportRecordMapper.java' -or
            $f.before -cne 'f99ad6764e679041b9616b72b958c3f2034e96b24e8a97d79bc215ba92611384' -or
            $f.after -cne 'f3865ac8acc9d7edcd4eacbbad5aa57ae966b929a2059242095722b12efb04d6' -or
            $f.snapshot -cne ($history + 'frota/' + $f.path) -or
            $f.decision -cne 'docs/catalogos/frota-manifestos-v2-035c/decisao-v01.json') { throw 'OFFLINE_SUCCESSION_FLEET' }
        CheckHash $f.decision $f.decisionSha256
        $decision = ReadJson $f.decision
        $anchor = @($decision.evidence | Where-Object path -CEQ $f.path)
        if ($anchor.Count -ne 1 -or $anchor[0].sha256 -cne $f.before -or -not $seen.Contains($f.snapshot)) { throw 'OFFLINE_SUCCESSION_FLEET_ANCHOR' }
        CheckHash $f.snapshot $f.before
        CheckHash $f.path $f.after
        Assert-QualificationFields $m.checkpoint @('path','sha256')
        if ($m.checkpoint.path -cne 'docs/continuidade/checkpoints/0217-correcao-local-succession-frota.md') { throw 'OFFLINE_SUCCESSION_CHECKPOINT' }
        CheckHash $m.checkpoint.path $m.checkpoint.sha256
        foreach ($p in @('STATES.md','docs/continuidade/RETOMADA.md','TRILHA_CONCLUSAO_POR_MODELO.md')) {
            if (-not [IO.File]::ReadAllText((Physical $p), $utf8).Contains($m.status)) { throw 'OFFLINE_SUCCESSION_POINTER' }
        }
        if (-not [IO.File]::ReadAllText((Physical 'docs/continuidade/RETOMADA.md'), $utf8).Contains($m.checkpoint.sha256)) { throw 'OFFLINE_SUCCESSION_CHECKPOINT_POINTER' }
        return ,$map
    }
    CheckHash $manifestPath ([IO.File]::ReadAllText((Physical ($catalog + 'manifesto.sha256')), $utf8).Trim())
    $manifest = ReadJson $manifestPath
    $map = CheckManifest $manifest
    $guards = 0
    if ($SelfTest) {
        foreach ($test in @(
            @('OFFLINE_SUCCESSION_SCOPE', { param($m) $m.sourceCalls = 1 }),
            @('OFFLINE_SUCCESSION_SCOPE', { param($m) $m.databaseCalls = $false }),
            @('OFFLINE_SUCCESSION_SCOPE', { param($m) $m.historicalAcceptances = '68/115' }),
            @('OFFLINE_SUCCESSION_PREDECESSOR', { param($m) $m.predecessor.sha256 = '0'*64 }),
            @('OFFLINE_SUCCESSION_BEFORE', { param($m) $m.changedExistingFiles[0].before = '0'*64 }),
            @('OFFLINE_SUCCESSION_BEFORE', { param($m) $m.changedExistingFiles[0].snapshot = '../outside' }),
            @(('OFFLINE_SUCCESSION_HASH_' + $manifest.changedExistingFiles[0].path), { param($m) $m.changedExistingFiles[0].after = '0'*64 }),
            @('OFFLINE_SUCCESSION_REMOVAL', { param($m) $m.changedExistingFiles[0].after = $null }),
            @('OFFLINE_SUCCESSION_PRESERVED', { param($m) $m.preservedFiles[0].sha256 = '0'*64 }),
            @('OFFLINE_SUCCESSION_PRESERVED_SET', { param($m) $m.preservedFiles = @($m.preservedFiles | Select-Object -Skip 1) }),
            @('OFFLINE_SUCCESSION_FLEET', { param($m) $m.fleet.before = $m.fleet.after }),
            @('OFFLINE_SUCCESSION_PATH', { param($m) $m.newFiles[0].path = '../outside' }),
            @('OFFLINE_SUCCESSION_PATH', { param($m) $m.newFiles[0].path = 'C:/outside' }),
            @('OFFLINE_SUCCESSION_UNLISTED_DRIFT', { param($m) $m.newFiles = @($m.newFiles | Where-Object path -CNE 'scripts/validation/OfflinePreparationSuccession.psm1') })
        )) {
            $copy = Read-QualificationJsonBytes ($utf8.GetBytes(($manifest | ConvertTo-Json -Depth 12 -Compress))) 8388608
            & $test[1] $copy
            $reason = 'ACCEPTED'; try { $null = CheckManifest $copy } catch { $reason = $_.Exception.Message }
            if ($reason -cne $test[0]) { throw ('OFFLINE_SUCCESSION_GUARD_' + $reason) }; $guards++
        }
    }
    return [pscustomobject]@{map=$map; newFiles=@($manifest.newFiles.path)+@($manifestPath,($catalog+'manifesto.sha256')); baselineFiles=@($base.Keys); manifest=$manifest; guards=$guards}
}
Export-ModuleMember -Function Get-OfflinePreparationSuccession

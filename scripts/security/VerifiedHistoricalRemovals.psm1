Set-StrictMode -Version Latest

function Get-VerifiedHistoricalRemovals {
    param([Parameter(Mandatory)][string]$Root)
    $ErrorActionPreference = 'Stop'
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([char]92, [char]47)
    $relative = 'docs/catalogos/alinhamento-entidades/manifesto.json'
    $manifest = Join-Path $Root $relative
    $result = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if (-not (Test-Path -LiteralPath $manifest)) { return ,$result }
    function CheckedHash([string]$Path) {
        $item = Get-Item -LiteralPath $Path -Force
        if ($item.PSIsContainer) { throw 'REMOVAL_FILE_REQUIRED' }
        $node = $item
        while ($null -ne $node) {
            if (($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'REMOVAL_LINK' }
            if ($node.FullName -ceq $Root) { return (Get-FileHash -LiteralPath $item.FullName).Hash.ToLowerInvariant() }
            $node = if ($node -is [IO.DirectoryInfo]) { $node.Parent } else { $node.Directory }
        }
        throw 'REMOVAL_ESCAPE'
    }
    # Immutable predecessor, not a caller-provided allowlist or a newly generated seal.
    if ((CheckedHash $manifest) -cne 'f23b401f5829216d8f929b021dfff6632620ca55eec1f8718961eec1ab7b518d') {
        throw 'REMOVAL_MANIFEST_HASH'
    }
    $bytes = [IO.File]::ReadAllBytes($manifest)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = [BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
    if ($digest -cne
        'f23b401f5829216d8f929b021dfff6632620ca55eec1f8718961eec1ab7b518d') { throw 'REMOVAL_MANIFEST_HASH' }
    $document = [Text.UTF8Encoding]::new($false, $true).GetString($bytes) | ConvertFrom-Json
    foreach ($entry in $document.changedExistingFiles | Where-Object { $null -eq $_.after }) {
        if ($entry.snapshot -cne ('docs/continuidade/historico/alinhamento-entidades/' + $entry.path) -or
            (CheckedHash (Join-Path $Root $entry.snapshot)) -cne $entry.before) { throw 'REMOVAL_SNAPSHOT_HASH' }
        [void]$result.Add($entry.path)
    }
    return ,$result
}

Export-ModuleMember -Function Get-VerifiedHistoricalRemovals

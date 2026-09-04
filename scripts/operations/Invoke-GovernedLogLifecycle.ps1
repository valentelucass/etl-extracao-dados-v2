#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$LogDirectory,

    [Parameter(Mandatory = $true)]
    [string]$ArchiveDirectory,

    [Parameter(Mandatory = $true)]
    [string]$PolicyPath,

    [Parameter(Mandatory = $true)]
    [DateTimeOffset]$EvaluationTimeUtc,

    [switch]$Execute
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$policyMaximumBytes = 65536
$receiptMaximumBytes = 1048576
$rolledLogPattern = '^etl-dataexport-v2\.[0-9]{4}-[0-9]{2}-[0-9]{2}\.log$'
$sourceLockLeafName = '.etl-v2-log-source.lock'
$sourceOperationLeafName = '.etl-v2-log-lifecycle.operation.json'
$runningOnWindows = [IO.Path]::DirectorySeparatorChar -eq '\'
$pathComparison = if ($runningOnWindows) {
    [StringComparison]::OrdinalIgnoreCase
} else {
    [StringComparison]::Ordinal
}

function Assert-Condition {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

function Test-HexFingerprint {
    param([object]$Value)

    return $Value -is [string] -and $Value -cmatch '^[0-9a-f]{64}$'
}

function Test-Integer {
    param([object]$Value)

    return $Value -is [int] -or $Value -is [long]
}

function Get-Sha256Text {
    param([string]$Text)

    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
        return ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace(
            '-',
            ''
        ).ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Assert-NotReparsePoint {
    param([IO.FileSystemInfo]$Item, [string]$Message)

    Assert-Condition (($Item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) `
        $Message
}

function Assert-NoReparseAncestors {
    param([string]$Path, [string]$Message)

    $current = if (Test-Path -LiteralPath $Path -PathType Container) {
        [IO.DirectoryInfo]::new($Path)
    } else {
        [IO.FileInfo]::new($Path).Directory
    }
    while ($null -ne $current) {
        Assert-NotReparsePoint $current $Message
        $current = $current.Parent
    }
}

function Resolve-SafeDirectory {
    param([string]$Path, [string]$Label)

    Assert-Condition (Test-Path -LiteralPath $Path -PathType Container) `
        "$Label deve existir e ser diretório."
    $resolved = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).ProviderPath
    $full = [IO.Path]::GetFullPath($resolved)
    if ($runningOnWindows) {
        Assert-Condition (-not $full.StartsWith('\\', [StringComparison]::Ordinal)) `
            "$Label não pode usar caminho UNC/compartilhado."
    }
    $fileSystemRoot = [IO.Path]::GetPathRoot($full)
    Assert-Condition (-not $full.Equals($fileSystemRoot, $pathComparison)) `
        "$Label não pode ser uma raiz de filesystem."
    Assert-NoReparseAncestors $full `
        "$Label e seus ancestrais não podem conter link/reparse point."
    return $full.TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
}

function Assert-DirectChildPath {
    param([string]$Root, [string]$Name)

    Assert-Condition ([IO.Path]::GetFileName($Name) -ceq $Name) `
        'Nome de arquivo não é um leaf name válido.'
    $candidatePath = [IO.Path]::GetFullPath((Join-Path $Root $Name))
    Assert-Condition ($candidatePath.StartsWith(
            $Root + [IO.Path]::DirectorySeparatorChar,
            $pathComparison
        )) 'Arquivo escapou do diretório autorizado.'
    return $candidatePath
}

function Test-SourceControlLeafName {
    param([string]$Name)

    return $Name -ceq $sourceLockLeafName `
        -or $Name -ceq $sourceOperationLeafName `
        -or $Name -ceq ($sourceOperationLeafName + '.pending')
}

function Assert-ExactProperties {
    param([object]$Value, [string[]]$Expected, [string]$Label)

    Assert-Condition ($null -ne $Value) "$Label ausente."
    $actual = @($Value.PSObject.Properties.Name)
    Assert-Condition ($actual.Count -eq $Expected.Count) `
        "$Label contém propriedades ausentes ou inesperadas."
    foreach ($propertyName in $Expected) {
        Assert-Condition ($actual -ccontains $propertyName) `
            "$Label contém propriedades ausentes ou inesperadas."
    }
}

function Read-BoundedBytes {
    param([string]$Path, [long]$MaximumBytes, [string]$Label)

    Assert-Condition (Test-Path -LiteralPath $Path -PathType Leaf) "$Label ausente."
    $item = Get-Item -LiteralPath $Path
    Assert-NotReparsePoint $item "$Label não pode ser link/reparse point."
    $stream = [IO.FileStream]::new(
        $item.FullName,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )
    try {
        Assert-Condition ($stream.Length -le $MaximumBytes) `
            "$Label excede o limite de bytes."
        $bytes = [byte[]]::new([int]$stream.Length)
        $offset = 0
        while ($offset -lt $bytes.Length) {
            $read = $stream.Read($bytes, $offset, $bytes.Length - $offset)
            Assert-Condition ($read -gt 0) "$Label mudou durante a leitura."
            $offset += $read
        }
        Assert-Condition ($stream.Length -eq $bytes.LongLength) `
            "$Label mudou durante a leitura."
        return $bytes
    } finally {
        $stream.Dispose()
    }
}

function Get-BoundedFileSha256 {
    param(
        [string]$Path,
        [long]$MaximumBytes,
        [string]$Label,
        [long]$ExpectedBytes = -1
    )

    Assert-Condition (Test-Path -LiteralPath $Path -PathType Leaf) "$Label ausente."
    $item = Get-Item -LiteralPath $Path
    Assert-NotReparsePoint $item "$Label não pode ser link/reparse point."
    $stream = [IO.FileStream]::new(
        $item.FullName,
        [IO.FileMode]::Open,
        [IO.FileAccess]::Read,
        [IO.FileShare]::Read
    )
    $hash = [Security.Cryptography.IncrementalHash]::CreateHash(
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    try {
        Assert-Condition ($stream.Length -le $MaximumBytes `
                -and ($ExpectedBytes -lt 0 -or $stream.Length -eq $ExpectedBytes)) `
            "$Label excede ou diverge do tamanho autorizado."
        $buffer = [byte[]]::new(65536)
        $totalBytes = [long]0
        while (($read = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            Assert-Condition ($read -le ($MaximumBytes - $totalBytes)) `
                "$Label excede o limite durante o hash."
            $hash.AppendData($buffer, 0, $read)
            $totalBytes += $read
        }
        Assert-Condition ($totalBytes -eq $stream.Length `
                -and ($ExpectedBytes -lt 0 -or $totalBytes -eq $ExpectedBytes)) `
            "$Label mudou durante o hash."
        return ([BitConverter]::ToString($hash.GetHashAndReset())).Replace(
            '-',
            ''
        ).ToLowerInvariant()
    } finally {
        $hash.Dispose()
        $stream.Dispose()
    }
}

function Copy-StreamExactly {
    param(
        [IO.Stream]$Source,
        [IO.Stream]$Destination,
        [long]$ExpectedBytes,
        [string]$Label
    )

    $buffer = [byte[]]::new(65536)
    $remaining = $ExpectedBytes
    while ($remaining -gt 0) {
        $requested = [int][Math]::Min([long]$buffer.Length, $remaining)
        $read = $Source.Read($buffer, 0, $requested)
        Assert-Condition ($read -gt 0) "$Label terminou antes do tamanho esperado."
        $Destination.Write($buffer, 0, $read)
        $remaining -= $read
    }
    Assert-Condition ($Source.ReadByte() -eq -1) `
        "$Label excedeu o tamanho esperado durante a cópia."
}

function Read-BoundedJson {
    param([string]$Path, [long]$MaximumBytes, [string]$Label)

    $bytes = Read-BoundedBytes $Path $MaximumBytes $Label
    try {
        $algorithm = [Security.Cryptography.SHA256]::Create()
        try {
            $sha256 = ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace(
                '-',
                ''
            ).ToLowerInvariant()
        } finally {
            $algorithm.Dispose()
        }
        $strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
        $value = $strictUtf8.GetString($bytes) | ConvertFrom-Json
        return [pscustomobject]@{
            value = $value
            sha256 = $sha256
        }
    } catch {
        throw "$Label não contém JSON válido."
    }
}

function Write-NewUtf8FileAtomic {
    param([string]$Path, [string]$Content, [long]$MaximumBytes)

    $bytes = [Text.Encoding]::UTF8.GetBytes($Content)
    Assert-Condition ($bytes.LongLength -le $MaximumBytes) `
        'Recibo excede o limite de bytes.'
    Assert-Condition (-not (Test-Path -LiteralPath $Path)) `
        'Recibo final já existe; criação divergente recusada.'
    $pendingPath = $Path + '.pending'
    if (Test-Path -LiteralPath $pendingPath) {
        $pendingItem = Get-Item -LiteralPath $pendingPath
        Assert-NotReparsePoint $pendingItem `
            'Recibo pendente não pode ser link/reparse point.'
        Assert-Condition ($pendingItem.Length -le $MaximumBytes) `
            'Recibo pendente excede o limite de bytes.'
        $pendingBytes = Read-BoundedBytes $pendingPath $MaximumBytes `
            'Recibo pendente'
        if (-not [Collections.StructuralComparisons]::StructuralEqualityComparer.Equals(
                $pendingBytes,
                $bytes
            )) {
            throw 'Recibo pendente diverge do conteúdo esperado; recuperação automática recusada.'
        }
    }
    if (-not (Test-Path -LiteralPath $pendingPath)) {
        $stream = [IO.FileStream]::new(
            $pendingPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        } finally {
            $stream.Dispose()
        }
    }
    [IO.File]::Move($pendingPath, $Path)
}

function Write-Utf8FileAtomicReplace {
    param([string]$Path, [string]$Content, [long]$MaximumBytes)

    $bytes = [Text.Encoding]::UTF8.GetBytes($Content)
    Assert-Condition ($bytes.LongLength -le $MaximumBytes) `
        'Marker de operação excede o limite de bytes.'
    $pendingPath = $Path + '.pending'
    if (Test-Path -LiteralPath $pendingPath) {
        $pendingBytes = Read-BoundedBytes $pendingPath $MaximumBytes `
            'Marker de operação pendente'
        Assert-Condition (
            [Collections.StructuralComparisons]::StructuralEqualityComparer.Equals(
                $pendingBytes,
                $bytes
            )
        ) 'Marker de operação pendente pertence a outra execução.'
    } else {
        $stream = [IO.FileStream]::new(
            $pendingPath,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None
        )
        try {
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush($true)
        } finally {
            $stream.Dispose()
        }
    }
    if (Test-Path -LiteralPath $Path) {
        Assert-NotReparsePoint (Get-Item -LiteralPath $Path) `
            'Marker de operação não pode ser link/reparse point.'
    }
    [IO.File]::Move($pendingPath, $Path, $true)
}

function Assert-PolicyFileUnchanged {
    param([string]$Path, [string]$ExpectedHash)

    Assert-Condition (Test-Path -LiteralPath $Path -PathType Leaf) `
        'Política desapareceu durante a operação.'
    $item = Get-Item -LiteralPath $Path
    Assert-NotReparsePoint $item `
        'Política mudou para link/reparse point durante a operação.'
    Assert-Condition ($item.Length -le $policyMaximumBytes) `
        'Política excedeu o limite durante a operação.'
    $actualHash = Get-BoundedFileSha256 $Path $policyMaximumBytes 'Política'
    Assert-Condition ($actualHash -ceq $ExpectedHash) `
        'Política ou legal hold mudou durante a operação.'
}

function Assert-ArchiveEntry {
    param([object]$Entry, [string]$ArchiveRoot)

    $archivePath = Assert-DirectChildPath $ArchiveRoot $Entry.name
    Assert-Condition (Test-Path -LiteralPath $archivePath -PathType Leaf) `
        'Archive verificado desapareceu.'
    $archiveItem = Get-Item -LiteralPath $archivePath
    Assert-NotReparsePoint $archiveItem `
        'Archive verificado não pode ser link/reparse point.'
    Assert-Condition ($archiveItem.Length -eq [long]$Entry.bytes) `
        'Tamanho do archive divergiu do permit receipt.'
    $archiveHash = Get-BoundedFileSha256 $archivePath ([long]$Entry.bytes) `
        'Archive verificado' ([long]$Entry.bytes)
    Assert-Condition ($archiveHash -ceq $Entry.sha256) `
        'Archive divergiu do permit receipt.'
}

function Assert-PermitReceipt {
    param(
        [object]$Permit,
        [object]$Policy,
        [string]$ReceiptId,
        [string]$ScopeFingerprint,
        [long]$EvaluationTimeUtcTicks,
        [long]$CutoffUtcTicks,
        [string]$ArchiveRoot
    )

    Assert-ExactProperties $Permit @(
        'manifestVersion', 'receiptId', 'scopeFingerprint', 'policyVersion',
        'policyFingerprint', 'evaluationTimeUtcTicks', 'cutoffUtcTicks',
        'examinedFiles', 'files'
    ) 'Permit receipt'
    Assert-Condition ((Test-Integer $Permit.manifestVersion) `
            -and $Permit.manifestVersion -eq 1) `
        'Versão do permit receipt incompatível.'
    Assert-Condition ($Permit.receiptId -ceq $ReceiptId) 'Permit receipt divergente.'
    Assert-Condition ($Permit.scopeFingerprint -ceq $ScopeFingerprint) `
        'Permit receipt pertence a outro escopo de diretórios.'
    Assert-Condition ($Permit.policyVersion -ceq $Policy.policyVersion) `
        'Permit receipt pertence a outra versão de política.'
    Assert-Condition ($Permit.policyFingerprint -ceq $Policy.policyFingerprint) `
        'Permit receipt pertence a outra política.'
    Assert-Condition ((Test-Integer $Permit.evaluationTimeUtcTicks) `
            -and $Permit.evaluationTimeUtcTicks -eq $EvaluationTimeUtcTicks) `
        'Permit receipt pertence a outro instante de avaliação.'
    Assert-Condition ((Test-Integer $Permit.cutoffUtcTicks) `
            -and $Permit.cutoffUtcTicks -eq $CutoffUtcTicks) `
        'Permit receipt contém cutoff divergente.'
    Assert-Condition (@($Permit.files).Count -le [int]$Policy.maximumFilesPerRun) `
        'Permit receipt excede maximumFilesPerRun.'
    Assert-Condition ((Test-Integer $Permit.examinedFiles) `
            -and $Permit.examinedFiles -ge 0 `
            -and $Permit.examinedFiles -le [int]$Policy.maximumEntriesScanned) `
        'Permit receipt contém examinedFiles inválido.'

    $seenNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $permittedBytes = [long]0
    foreach ($entry in @($Permit.files)) {
        Assert-ExactProperties $entry @('name', 'bytes', 'sha256', 'lastWriteTimeUtcTicks') `
            'Entrada do permit receipt'
        Assert-Condition ($entry.name -is [string] `
                -and $entry.name -cmatch $rolledLogPattern) `
            'Permit receipt contém nome fora da allowlist.'
        Assert-Condition ($seenNames.Add($entry.name)) `
            'Permit receipt contém nome duplicado.'
        Assert-Condition ((Test-Integer $entry.bytes) -and $entry.bytes -ge 0 `
                -and $entry.bytes -le [long]$Policy.maximumSingleFileBytes) `
            'Permit receipt contém tamanho inválido.'
        Assert-Condition (Test-HexFingerprint $entry.sha256) `
            'Permit receipt contém hash inválido.'
        Assert-Condition ((Test-Integer $entry.lastWriteTimeUtcTicks) `
                -and $entry.lastWriteTimeUtcTicks -gt 0 `
                -and [long]$entry.lastWriteTimeUtcTicks -le $CutoffUtcTicks) `
            'lastWriteTimeUtcTicks do permit receipt é inválido.'
        Assert-Condition ([long]$entry.bytes -le `
                ([long]$Policy.maximumSourceBytes - $permittedBytes)) `
            'Permit receipt excede maximumSourceBytes.'
        $permittedBytes += [long]$entry.bytes
        Assert-ArchiveEntry $entry $ArchiveRoot
    }

    return [pscustomobject]@{
        files = @($Permit.files).Count
        bytes = $permittedBytes
    }
}

function Get-EligibleLogs {
    param([string]$Root, [object]$Policy, [DateTime]$CutoffUtc)

    $eligible = [Collections.Generic.List[IO.FileInfo]]::new()
    $examined = 0
    $eligibleBytes = [long]0
    foreach ($candidatePath in [IO.Directory]::EnumerateFileSystemEntries(
            $Root,
            '*',
            [IO.SearchOption]::TopDirectoryOnly
        )) {
        if (Test-SourceControlLeafName ([IO.Path]::GetFileName($candidatePath))) {
            $sourceControlItem = Get-Item -LiteralPath $candidatePath
            Assert-Condition (-not $sourceControlItem.PSIsContainer) `
                'Controle da origem deve ser arquivo regular.'
            Assert-NotReparsePoint $sourceControlItem `
                'Controle da origem não pode ser link/reparse point.'
            continue
        }
        $examined++
        Assert-Condition ($examined -le [int]$Policy.maximumEntriesScanned) `
            'Diretório excede maximumEntriesScanned; nenhum arquivo foi alterado.'
        $candidate = Get-Item -LiteralPath $candidatePath
        Assert-NotReparsePoint $candidate `
            'Entrada link/reparse point não é aceita no lifecycle de logs.'
        if ($candidate.PSIsContainer) {
            continue
        }
        if ($candidate.Name -cnotmatch $rolledLogPattern `
                -or $candidate.LastWriteTimeUtc -gt $CutoffUtc) {
            continue
        }
        Assert-Condition ($eligible.Count -lt [int]$Policy.maximumFilesPerRun) `
            'Quantidade de logs elegíveis excede maximumFilesPerRun; nenhum arquivo foi alterado.'
        Assert-Condition ($candidate.Length -le [long]$Policy.maximumSingleFileBytes) `
            'Um log elegível excede maximumSingleFileBytes; nenhum arquivo foi alterado.'
        Assert-Condition ([long]$candidate.Length -le `
                ([long]$Policy.maximumSourceBytes - $eligibleBytes)) `
            'Bytes elegíveis excedem maximumSourceBytes; nenhum arquivo foi alterado.'
        $eligibleBytes += [long]$candidate.Length
        $eligible.Add($candidate)
    }
    $ordered = @($eligible | Sort-Object LastWriteTimeUtc, Name)
    return [pscustomobject]@{
        files = $ordered
        bytes = $eligibleBytes
        examined = $examined
    }
}

function Read-OperationMarker {
    param([string]$MarkerPath)

    $marker = (Read-BoundedJson $MarkerPath $receiptMaximumBytes `
            'Operation marker').value
    Assert-ExactProperties $marker @(
        'manifestVersion', 'state', 'receiptId', 'scopeFingerprint',
        'policyFingerprint', 'archiveRoot', 'evaluationTimeUtcTicks',
        'completionSha256'
    ) 'Operation marker'
    Assert-Condition ((Test-Integer $marker.manifestVersion) `
            -and $marker.manifestVersion -eq 1 `
            -and $marker.state -is [string] `
            -and @('ACTIVE', 'COMPLETE') -ccontains $marker.state `
            -and (Test-HexFingerprint $marker.receiptId) `
            -and (Test-HexFingerprint $marker.scopeFingerprint) `
            -and (Test-HexFingerprint $marker.policyFingerprint) `
            -and $marker.archiveRoot -is [string] `
            -and $marker.archiveRoot.Length -ge 1 `
            -and $marker.archiveRoot.Length -le 32767 `
            -and [IO.Path]::IsPathFullyQualified($marker.archiveRoot) `
            -and (Test-Integer $marker.evaluationTimeUtcTicks) `
            -and $marker.evaluationTimeUtcTicks -gt 0 `
            -and (($marker.state -ceq 'ACTIVE' -and $null -eq $marker.completionSha256) `
                -or ($marker.state -ceq 'COMPLETE' `
                    -and (Test-HexFingerprint $marker.completionSha256)))) `
        'Operation marker contém identidade inválida.'
    return $marker
}

function Test-OperationMarkerMatches {
    param(
        [object]$Marker,
        [string]$ReceiptId,
        [string]$ScopeFingerprint,
        [string]$PolicyFingerprint,
        [string]$ArchiveRoot,
        [long]$EvaluationTimeUtcTicks
    )

    return $Marker.receiptId -ceq $ReceiptId `
        -and $Marker.scopeFingerprint -ceq $ScopeFingerprint `
        -and $Marker.policyFingerprint -ceq $PolicyFingerprint `
        -and $Marker.archiveRoot.Equals($ArchiveRoot, $pathComparison) `
        -and $Marker.evaluationTimeUtcTicks -eq $EvaluationTimeUtcTicks
}

function Write-OperationMarker {
    param(
        [string]$MarkerPath,
        [string]$ReceiptId,
        [string]$ScopeFingerprint,
        [string]$PolicyFingerprint,
        [string]$ArchiveRoot,
        [long]$EvaluationTimeUtcTicks,
        [string]$State,
        [AllowNull()]
        [string]$CompletionSha256
    )

    Assert-Condition (@('ACTIVE', 'COMPLETE') -ccontains $State `
            -and (($State -ceq 'ACTIVE' `
                    -and [string]::IsNullOrEmpty($CompletionSha256)) `
                -or ($State -ceq 'COMPLETE' `
                    -and (Test-HexFingerprint $CompletionSha256)))) `
        'Transição do operation marker é inválida.'
    $markerDocument = [ordered]@{
        manifestVersion = 1
        state = $State
        receiptId = $ReceiptId
        scopeFingerprint = $ScopeFingerprint
        policyFingerprint = $PolicyFingerprint
        archiveRoot = $ArchiveRoot
        evaluationTimeUtcTicks = $EvaluationTimeUtcTicks
        completionSha256 = if ($State -ceq 'ACTIVE') { $null } else { $CompletionSha256 }
    }
    Write-Utf8FileAtomicReplace $MarkerPath `
        ($markerDocument | ConvertTo-Json -Compress) $receiptMaximumBytes
    $marker = Read-OperationMarker $MarkerPath
    $identityMatches = Test-OperationMarkerMatches $marker $ReceiptId `
        $ScopeFingerprint $PolicyFingerprint $ArchiveRoot $EvaluationTimeUtcTicks
    Assert-Condition ($identityMatches `
            -and $marker.state -ceq $State `
            -and (([string]::IsNullOrEmpty($CompletionSha256) `
                    -and $null -eq $marker.completionSha256) `
                -or $marker.completionSha256 -ceq $CompletionSha256)) `
        'Operation marker não preservou a identidade esperada.'
}

function Assert-CompletedOperationMarker {
    param([object]$Marker, [string]$LogRoot, [string]$ScopeLogMaterial)

    Assert-Condition ($Marker.state -ceq 'COMPLETE') `
        'Operation marker anterior ainda está ativo.'
    $previousArchiveRoot = Resolve-SafeDirectory $Marker.archiveRoot `
        'Archive do operation marker anterior'
    Assert-Condition (-not $LogRoot.Equals($previousArchiveRoot, $pathComparison) `
            -and -not $previousArchiveRoot.StartsWith(
                $LogRoot + [IO.Path]::DirectorySeparatorChar,
                $pathComparison
            ) `
            -and -not $LogRoot.StartsWith(
                $previousArchiveRoot + [IO.Path]::DirectorySeparatorChar,
                $pathComparison
            )) 'Archive anterior possui relação de paths inválida.'
    $previousArchiveMaterial = if ($runningOnWindows) {
        $previousArchiveRoot.ToUpperInvariant()
    } else {
        $previousArchiveRoot
    }
    $previousScopeCanonical = 'log-lifecycle-scope-v2|{0}:{1}|{2}:{3}' -f @(
        [Text.Encoding]::UTF8.GetByteCount($ScopeLogMaterial),
        $ScopeLogMaterial,
        [Text.Encoding]::UTF8.GetByteCount($previousArchiveMaterial),
        $previousArchiveMaterial
    )
    Assert-Condition ((Get-Sha256Text $previousScopeCanonical) -ceq `
            $Marker.scopeFingerprint) `
        'Archive locator anterior diverge do scope fingerprint.'
    $previousScopeRoot = Assert-DirectChildPath $previousArchiveRoot `
        ("scope-$($Marker.scopeFingerprint)")
    Assert-Condition (Test-Path -LiteralPath $previousScopeRoot -PathType Container) `
        'Namespace do archive anterior desapareceu.'
    Assert-NotReparsePoint (Get-Item -LiteralPath $previousScopeRoot) `
        'Namespace do archive anterior não pode ser link/reparse point.'
    $previousPermitPath = Assert-DirectChildPath $previousScopeRoot `
        "log-lifecycle-$($Marker.receiptId).permit.json"
    $previousCompletionPath = Assert-DirectChildPath $previousScopeRoot `
        "log-lifecycle-$($Marker.receiptId).complete.json"
    $permitEnvelope = Read-BoundedJson $previousPermitPath $receiptMaximumBytes `
        'Permit receipt anterior'
    $completionEnvelope = Read-BoundedJson $previousCompletionPath `
        $receiptMaximumBytes 'Completion receipt anterior'
    $completion = $completionEnvelope.value
    Assert-ExactProperties $completion @(
        'manifestVersion', 'receiptId', 'scopeFingerprint', 'policyFingerprint',
        'permitSha256', 'examinedFiles', 'archivedFiles', 'archivedBytes'
    ) 'Completion receipt anterior'
    Assert-Condition ((Test-Integer $completion.manifestVersion) `
            -and $completion.manifestVersion -eq 1 `
            -and $completion.receiptId -ceq $Marker.receiptId `
            -and $completion.scopeFingerprint -ceq $Marker.scopeFingerprint `
            -and $completion.policyFingerprint -ceq $Marker.policyFingerprint `
            -and $completionEnvelope.sha256 -ceq $Marker.completionSha256 `
            -and $completion.permitSha256 -ceq $permitEnvelope.sha256 `
            -and (Test-Integer $completion.examinedFiles) `
            -and $completion.examinedFiles -ge 0 `
            -and $completion.examinedFiles -le 100000 `
            -and (Test-Integer $completion.archivedFiles) `
            -and $completion.archivedFiles -ge 0 `
            -and $completion.archivedFiles -le 1000 `
            -and (Test-Integer $completion.archivedBytes) `
            -and $completion.archivedBytes -ge 0 `
            -and $completion.archivedBytes -le 1073741824) `
        'Completion receipt anterior diverge do operation marker.'
    Assert-Condition ((Get-BoundedFileSha256 $previousPermitPath `
                $receiptMaximumBytes 'Permit receipt anterior') -ceq `
            $permitEnvelope.sha256) `
        'Permit receipt anterior mudou durante a leitura.'

    $permit = $permitEnvelope.value
    Assert-ExactProperties $permit @(
        'manifestVersion', 'receiptId', 'scopeFingerprint', 'policyVersion',
        'policyFingerprint', 'evaluationTimeUtcTicks', 'cutoffUtcTicks',
        'examinedFiles', 'files'
    ) 'Permit receipt anterior'
    Assert-Condition ((Test-Integer $permit.manifestVersion) `
            -and $permit.manifestVersion -eq 1 `
            -and $permit.receiptId -ceq $Marker.receiptId `
            -and $permit.scopeFingerprint -ceq $Marker.scopeFingerprint `
            -and $permit.policyFingerprint -ceq $Marker.policyFingerprint `
            -and $permit.policyVersion -is [string] `
            -and $permit.policyVersion -cmatch '^[A-Za-z0-9._-]{1,128}$' `
            -and (Test-Integer $permit.evaluationTimeUtcTicks) `
            -and $permit.evaluationTimeUtcTicks -eq $Marker.evaluationTimeUtcTicks `
            -and (Test-Integer $permit.cutoffUtcTicks) `
            -and $permit.cutoffUtcTicks -gt 0 `
            -and (Test-Integer $permit.examinedFiles) `
            -and $permit.examinedFiles -eq $completion.examinedFiles `
            -and @($permit.files).Count -eq $completion.archivedFiles) `
        'Permit receipt anterior diverge do operation marker.'

    $seenNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $archivedBytes = [long]0
    for ($entryIndex = 0; $entryIndex -lt @($permit.files).Count; $entryIndex++) {
        $entry = @($permit.files)[$entryIndex]
        Assert-ExactProperties $entry @(
            'name', 'bytes', 'sha256', 'lastWriteTimeUtcTicks'
        ) 'Entrada do permit anterior'
        Assert-Condition ($entry.name -is [string] `
                -and $entry.name -cmatch $rolledLogPattern `
                -and $seenNames.Add($entry.name) `
                -and (Test-Integer $entry.bytes) `
                -and $entry.bytes -ge 0 `
                -and $entry.bytes -le 1073741824 `
                -and (Test-HexFingerprint $entry.sha256) `
                -and (Test-Integer $entry.lastWriteTimeUtcTicks) `
                -and $entry.lastWriteTimeUtcTicks -gt 0 `
                -and [long]$entry.bytes -le (1073741824 - $archivedBytes)) `
            'Entrada do permit anterior está fora dos limites globais.'
        $archivedBytes += [long]$entry.bytes
        Assert-ArchiveEntry $entry $previousScopeRoot
        $previousSource = Assert-DirectChildPath $LogRoot $entry.name
        $previousTombstone = Assert-DirectChildPath $LogRoot `
            ('.etl-v2-log-lifecycle-{0}-{1}.pending-delete' -f `
                $Marker.receiptId, $entryIndex)
        Assert-Condition (-not (Test-Path -LiteralPath $previousSource) `
                -and -not (Test-Path -LiteralPath $previousTombstone)) `
            'Operação anterior ainda possui source ou tombstone.'
    }
    Assert-Condition ($archivedBytes -eq [long]$completion.archivedBytes) `
        'Bytes do permit anterior divergem do completion receipt.'
}

function Assert-NoOrphanTombstone {
    param([string]$LogRoot, [int]$MaximumEntries)

    $examined = 0
    foreach ($entryPath in [IO.Directory]::EnumerateFileSystemEntries(
            $LogRoot,
            '*',
            [IO.SearchOption]::TopDirectoryOnly
        )) {
        if (Test-SourceControlLeafName ([IO.Path]::GetFileName($entryPath))) {
            $sourceControlItem = Get-Item -LiteralPath $entryPath
            Assert-Condition (-not $sourceControlItem.PSIsContainer) `
                'Controle da origem deve ser arquivo regular.'
            Assert-NotReparsePoint $sourceControlItem `
                'Controle da origem não pode ser link/reparse point.'
            continue
        }
        $examined++
        Assert-Condition ($examined -le $MaximumEntries) `
            'Diretório de logs excede o teto de inspeção para retomada.'
        $entry = Get-Item -LiteralPath $entryPath
        if ($entry.Name -cmatch `
                '^\.etl-v2-log-lifecycle-[0-9a-f]{64}-[0-9]+\.pending-delete$') {
            Assert-NotReparsePoint $entry `
                'Tombstone não pode ser link/reparse point.'
            throw 'Tombstone sem permit receipt íntegro exige recuperação manual.'
        }
    }
}

function Assert-InertLifecycleIntegrity {
    param(
        [string]$LogRoot,
        [string]$ArchiveScopeRoot,
        [string]$ScopeLogMaterial,
        [string]$SourceFingerprint,
        [string]$ResolvedPolicyPath,
        [string]$PolicyFileHash,
        [int]$MaximumEntries
    )

    Assert-Condition ($MaximumEntries -ge 1 -and $MaximumEntries -le 100000) `
        'Teto de inspeção da política inerte é inválido.'
    $sourceMutex = [Threading.Mutex]::new(
        $false,
        $(if ($runningOnWindows) { 'Global\' } else { '' }) +
            'etl-v2-log-source-' + $SourceFingerprint
    )
    $sourceMutexHeld = $false
    $sourceFileLock = $null
    $policyGuard = $null
    try {
        try {
            $sourceMutexHeld = $sourceMutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $sourceMutexHeld = $true
        }
        Assert-Condition $sourceMutexHeld `
            'Política inerte não pode mascarar outra execução da mesma origem.'

        $sourceLockPath = Assert-DirectChildPath $LogRoot $sourceLockLeafName
        if (Test-Path -LiteralPath $sourceLockPath) {
            $sourceLockItem = Get-Item -LiteralPath $sourceLockPath
            Assert-Condition (-not $sourceLockItem.PSIsContainer) `
                'Source lock deve ser arquivo regular.'
            Assert-NotReparsePoint $sourceLockItem `
                'Source lock não pode ser link/reparse point.'
            try {
                $sourceFileLock = [IO.FileStream]::new(
                    $sourceLockPath,
                    [IO.FileMode]::Open,
                    [IO.FileAccess]::ReadWrite,
                    [IO.FileShare]::None
                )
            } catch {
                throw 'Política inerte não pode mascarar o file lock da origem.'
            }
        }

        $policyGuard = [IO.FileStream]::new(
            $ResolvedPolicyPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        Assert-PolicyFileUnchanged $ResolvedPolicyPath $PolicyFileHash

        $operationMarkerPath = Assert-DirectChildPath $LogRoot $sourceOperationLeafName
        Assert-Condition (-not (Test-Path -LiteralPath ($operationMarkerPath + '.pending'))) `
            'Política inerte não pode mascarar transição pendente do operation marker.'
        if (Test-Path -LiteralPath $operationMarkerPath -PathType Leaf) {
            $operationMarker = Read-OperationMarker $operationMarkerPath
            Assert-CompletedOperationMarker $operationMarker $LogRoot $ScopeLogMaterial
        } elseif (Test-Path -LiteralPath $ArchiveScopeRoot) {
            Assert-Condition (Test-Path -LiteralPath $ArchiveScopeRoot -PathType Container) `
                'Namespace de archive inerte deve ser diretório.'
            $scopeItem = Get-Item -LiteralPath $ArchiveScopeRoot
            Assert-NotReparsePoint $scopeItem `
                'Namespace de archive inerte não pode ser link/reparse point.'
            $archiveEntries = 0
            foreach ($archiveEntryPath in [IO.Directory]::EnumerateFileSystemEntries(
                    $ArchiveScopeRoot,
                    '*',
                    [IO.SearchOption]::TopDirectoryOnly
                )) {
                $archiveEntries++
                Assert-Condition ($archiveEntries -le $MaximumEntries) `
                    'Namespace de archive inerte excede o teto de inspeção.'
                $archiveEntry = Get-Item -LiteralPath $archiveEntryPath
                Assert-NotReparsePoint $archiveEntry `
                    'Artefato de archive inerte não pode ser link/reparse point.'
                Assert-Condition (-not $archiveEntry.PSIsContainer `
                        -and $archiveEntry.Name -ceq '.etl-v2-log-lifecycle.lock') `
                    'Política inerte não pode mascarar archive ou receipt sem operation marker.'
            }
        }

        Assert-NoOrphanTombstone $LogRoot $MaximumEntries
        Assert-PolicyFileUnchanged $ResolvedPolicyPath $PolicyFileHash
    } finally {
        if ($null -ne $policyGuard) {
            $policyGuard.Dispose()
        }
        if ($null -ne $sourceFileLock) {
            $sourceFileLock.Dispose()
        }
        if ($sourceMutexHeld) {
            $sourceMutex.ReleaseMutex()
        }
        $sourceMutex.Dispose()
    }
}

Assert-Condition ($EvaluationTimeUtc.Offset -eq [TimeSpan]::Zero) `
    'EvaluationTimeUtc deve declarar offset UTC zero.'
if ($Execute) {
    Assert-Condition ($EvaluationTimeUtc -le [DateTimeOffset]::UtcNow) `
        'EvaluationTimeUtc futuro não é permitido em execução com efeitos.'
}

$logRoot = Resolve-SafeDirectory $LogDirectory 'Diretório de logs ativos'
$archiveRoot = Resolve-SafeDirectory $ArchiveDirectory 'Diretório de archive'
Assert-Condition (-not $logRoot.Equals($archiveRoot, $pathComparison)) `
    'Diretórios de log ativo e archive devem ser distintos.'
Assert-Condition (-not $archiveRoot.StartsWith(
        $logRoot + [IO.Path]::DirectorySeparatorChar,
        $pathComparison
    )) 'Archive não pode ficar dentro do diretório de logs ativos.'
Assert-Condition (-not $logRoot.StartsWith(
        $archiveRoot + [IO.Path]::DirectorySeparatorChar,
        $pathComparison
    )) 'Diretório de logs ativos não pode ficar dentro do archive.'
$scopeLogMaterial = if ($runningOnWindows) { $logRoot.ToUpperInvariant() } else { $logRoot }
$scopeArchiveMaterial = if ($runningOnWindows) {
    $archiveRoot.ToUpperInvariant()
} else {
    $archiveRoot
}
$scopeCanonical = 'log-lifecycle-scope-v2|{0}:{1}|{2}:{3}' -f @(
    [Text.Encoding]::UTF8.GetByteCount($scopeLogMaterial),
    $scopeLogMaterial,
    [Text.Encoding]::UTF8.GetByteCount($scopeArchiveMaterial),
    $scopeArchiveMaterial
)
$scopeFingerprint = Get-Sha256Text $scopeCanonical
$sourceCanonical = 'log-lifecycle-source-v1|{0}:{1}' -f @(
    [Text.Encoding]::UTF8.GetByteCount($scopeLogMaterial),
    $scopeLogMaterial
)
$sourceFingerprint = Get-Sha256Text $sourceCanonical
$archiveScopeRoot = Assert-DirectChildPath $archiveRoot ("scope-$scopeFingerprint")

Assert-Condition (Test-Path -LiteralPath $PolicyPath -PathType Leaf) `
    'PolicyPath deve apontar para arquivo existente.'
$resolvedPolicyPath = [IO.Path]::GetFullPath(
    (Resolve-Path -LiteralPath $PolicyPath -ErrorAction Stop).ProviderPath
)
Assert-Condition (-not $resolvedPolicyPath.StartsWith(
        $logRoot + [IO.Path]::DirectorySeparatorChar,
        $pathComparison
    ) -and -not $resolvedPolicyPath.StartsWith(
        $archiveRoot + [IO.Path]::DirectorySeparatorChar,
        $pathComparison
    )) 'Política deve ficar fora dos diretórios de logs e archive.'
Assert-NoReparseAncestors $resolvedPolicyPath `
    'Política e seus ancestrais não podem conter link/reparse point.'
$policyItem = Get-Item -LiteralPath $resolvedPolicyPath
Assert-NotReparsePoint $policyItem 'Política não pode ser link/reparse point.'
Assert-Condition ($policyItem.Length -le $policyMaximumBytes) `
    'Política excede o limite de bytes.'
$policyFileHashBefore = Get-BoundedFileSha256 $resolvedPolicyPath `
    $policyMaximumBytes 'Política'
$policyEnvelope = Read-BoundedJson $resolvedPolicyPath $policyMaximumBytes 'Política'
$policy = $policyEnvelope.value
$policyFileHash = Get-BoundedFileSha256 $resolvedPolicyPath `
    $policyMaximumBytes 'Política'
Assert-Condition ($policyFileHashBefore -ceq $policyEnvelope.sha256 `
        -and $policyEnvelope.sha256 -ceq $policyFileHash) `
    'Política mudou durante a leitura.'
Assert-Condition ((Test-Integer $policy.manifestVersion) `
        -and $policy.manifestVersion -eq 1) 'Versão de política de logs incompatível.'
Assert-Condition ($policy.enabled -is [bool]) 'enabled deve ser booleano.'

if (-not $policy.enabled) {
    Assert-ExactProperties $policy @('manifestVersion', 'enabled') 'Política desabilitada'
    Assert-InertLifecycleIntegrity $logRoot $archiveScopeRoot $scopeLogMaterial `
        $sourceFingerprint $resolvedPolicyPath $policyFileHash 100000
    [pscustomobject]@{
        state = 'DISABLED'
        examinedFiles = 0
        candidateFiles = 0
        candidateBytes = 0
        archivedFiles = 0
        exactRetry = $false
    }
    return
}

Assert-ExactProperties $policy @(
    'manifestVersion', 'enabled', 'policyVersion', 'retentionDays',
    'maximumEntriesScanned', 'maximumFilesPerRun', 'maximumSingleFileBytes',
    'maximumSourceBytes', 'scopeFingerprint', 'policyFingerprint',
    'dataOwnerEvidenceFingerprint',
    'complianceEvidenceFingerprint', 'legalHoldActive'
) 'Política habilitada'
Assert-Condition ($policy.policyVersion -is [string] `
        -and $policy.policyVersion -cmatch '^[A-Za-z0-9._-]{1,128}$') `
    'policyVersion inválida.'
Assert-Condition ((Test-Integer $policy.retentionDays) `
        -and $policy.retentionDays -ge 1 -and $policy.retentionDays -le 36500) `
    'retentionDays está fora do contrato.'
Assert-Condition ((Test-Integer $policy.maximumEntriesScanned) `
        -and $policy.maximumEntriesScanned -ge 1 `
        -and $policy.maximumEntriesScanned -le 100000) `
    'maximumEntriesScanned está fora do contrato.'
Assert-Condition ((Test-Integer $policy.maximumFilesPerRun) `
        -and $policy.maximumFilesPerRun -ge 1 `
        -and $policy.maximumFilesPerRun -le 1000) `
    'maximumFilesPerRun está fora do contrato.'
Assert-Condition ((Test-Integer $policy.maximumSingleFileBytes) `
        -and $policy.maximumSingleFileBytes -ge 1 `
        -and $policy.maximumSingleFileBytes -le 1073741824) `
    'maximumSingleFileBytes está fora do contrato.'
Assert-Condition ((Test-Integer $policy.maximumSourceBytes) `
        -and $policy.maximumSourceBytes -ge 1 `
        -and $policy.maximumSourceBytes -le 1073741824) `
    'maximumSourceBytes está fora do contrato.'
Assert-Condition (Test-HexFingerprint $policy.policyFingerprint) `
    'policyFingerprint inválido.'
Assert-Condition ((Test-HexFingerprint $policy.scopeFingerprint) `
        -and $policy.scopeFingerprint -ceq $scopeFingerprint) `
    'scopeFingerprint da política não corresponde aos diretórios autorizados.'
Assert-Condition (Test-HexFingerprint $policy.dataOwnerEvidenceFingerprint) `
    'Evidência do data owner inválida.'
Assert-Condition (Test-HexFingerprint $policy.complianceEvidenceFingerprint) `
    'Evidência de compliance inválida.'
Assert-Condition ($policy.dataOwnerEvidenceFingerprint -cne `
        $policy.complianceEvidenceFingerprint) `
    'Evidências de data owner e compliance precisam ser distintas.'
Assert-Condition ($policy.legalHoldActive -is [bool]) `
    'legalHoldActive deve ser booleano.'

$policyCanonicalTemplate = 'log-lifecycle-policy-v1|enabled=true|policyVersion={0}' +
    '|retentionDays={1}|maximumEntriesScanned={2}|maximumFilesPerRun={3}' +
    '|maximumSingleFileBytes={4}|maximumSourceBytes={5}|scopeFingerprint={6}' +
    '|legalHoldActive={7}|dataOwnerEvidence={8}|complianceEvidence={9}'
$policyCanonical = $policyCanonicalTemplate -f @(
    $policy.policyVersion,
    [int]$policy.retentionDays,
    [int]$policy.maximumEntriesScanned,
    [int]$policy.maximumFilesPerRun,
    [long]$policy.maximumSingleFileBytes,
    [long]$policy.maximumSourceBytes,
    $policy.scopeFingerprint,
    $policy.legalHoldActive.ToString().ToLowerInvariant(),
    $policy.dataOwnerEvidenceFingerprint,
    $policy.complianceEvidenceFingerprint
)
Assert-Condition ((Get-Sha256Text $policyCanonical) -ceq $policy.policyFingerprint) `
    'policyFingerprint não sela o conteúdo governado da política.'

if ($policy.legalHoldActive) {
    Assert-InertLifecycleIntegrity $logRoot $archiveScopeRoot $scopeLogMaterial `
        $sourceFingerprint $resolvedPolicyPath $policyFileHash `
        ([int]$policy.maximumEntriesScanned)
    [pscustomobject]@{
        state = 'LEGAL_HOLD'
        examinedFiles = 0
        candidateFiles = 0
        candidateBytes = 0
        archivedFiles = 0
        exactRetry = $false
    }
    return
}

$cutoffUtc = $EvaluationTimeUtc.UtcDateTime.AddDays(-[int]$policy.retentionDays)
$evaluationTimeUtcTicks = $EvaluationTimeUtc.UtcDateTime.Ticks
$cutoffUtcTicks = $cutoffUtc.Ticks
$receiptKey = '{0}|{1}|{2}' -f @(
    $policy.policyFingerprint,
    $evaluationTimeUtcTicks,
    $scopeFingerprint
)
$receiptId = Get-Sha256Text $receiptKey
$permitPath = Assert-DirectChildPath $archiveScopeRoot `
    "log-lifecycle-$receiptId.permit.json"
$completionPath = Assert-DirectChildPath $archiveScopeRoot `
    "log-lifecycle-$receiptId.complete.json"
$operationMarkerPath = Assert-DirectChildPath $logRoot $sourceOperationLeafName

if (-not $Execute) {
    $previewMutex = [Threading.Mutex]::new(
        $false,
        $(if ($runningOnWindows) { 'Global\' } else { '' }) +
            'etl-v2-log-source-' + $sourceFingerprint
    )
    $previewMutexHeld = $false
    $previewSourceLock = $null
    try {
        try {
            $previewMutexHeld = $previewMutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $previewMutexHeld = $true
        }
        Assert-Condition $previewMutexHeld `
            'Dry-run bloqueado por outra execução da mesma origem.'
        $previewSourceLockPath = Assert-DirectChildPath $logRoot $sourceLockLeafName
        if (Test-Path -LiteralPath $previewSourceLockPath -PathType Leaf) {
            Assert-NotReparsePoint (Get-Item -LiteralPath $previewSourceLockPath) `
                'Source lock não pode ser link/reparse point.'
            try {
                $previewSourceLock = [IO.FileStream]::new(
                    $previewSourceLockPath,
                    [IO.FileMode]::Open,
                    [IO.FileAccess]::ReadWrite,
                    [IO.FileShare]::None
                )
            } catch {
                throw 'Dry-run bloqueado pelo file lock da origem.'
            }
        }
        Assert-Condition (-not (Test-Path -LiteralPath `
                    ($operationMarkerPath + '.pending'))) `
            'Dry-run bloqueado por transição pendente do operation marker.'
        if (Test-Path -LiteralPath $operationMarkerPath -PathType Leaf) {
            $previewMarker = Read-OperationMarker $operationMarkerPath
            Assert-Condition ($previewMarker.state -ceq 'COMPLETE') `
                'Dry-run bloqueado por operação incompleta da mesma origem.'
            Assert-CompletedOperationMarker $previewMarker $logRoot $scopeLogMaterial
            if (Test-OperationMarkerMatches $previewMarker $receiptId `
                    $scopeFingerprint $policy.policyFingerprint $archiveRoot `
                    $evaluationTimeUtcTicks) {
                $previewCompletionPath = Assert-DirectChildPath $archiveScopeRoot `
                    "log-lifecycle-$receiptId.complete.json"
                $previewCompletion = (Read-BoundedJson $previewCompletionPath `
                        $receiptMaximumBytes 'Completion receipt').value
                [pscustomobject]@{
                    state = 'COMPLETE'
                    examinedFiles = [int]$previewCompletion.examinedFiles
                    candidateFiles = [int]$previewCompletion.archivedFiles
                    candidateBytes = [long]$previewCompletion.archivedBytes
                    archivedFiles = [int]$previewCompletion.archivedFiles
                    exactRetry = $true
                }
                return
            }
        }
        $previewScan = Get-EligibleLogs $logRoot $policy $cutoffUtc
        [pscustomobject]@{
            state = 'DRY_RUN'
            examinedFiles = $previewScan.examined
            candidateFiles = @($previewScan.files).Count
            candidateBytes = $previewScan.bytes
            archivedFiles = 0
            exactRetry = $false
        }
        return
    } finally {
        if ($null -ne $previewSourceLock) {
            $previewSourceLock.Dispose()
        }
        if ($previewMutexHeld) {
            $previewMutex.ReleaseMutex()
        }
        $previewMutex.Dispose()
    }
}

$operationMutex = $null
$mutexHeld = $false
$sourceMutex = $null
$sourceMutexHeld = $false
$sourceFileLock = $null
$policyGuard = $null
$permitGuard = $null
$operationFileLock = $null
$activeOperationEstablished = $false
$currentOperationMarker = $null
try {
    if ($Execute) {
        $sourceMutex = [Threading.Mutex]::new(
            $false,
            $(if ($runningOnWindows) { 'Global\' } else { '' }) +
                'etl-v2-log-source-' + $sourceFingerprint
        )
        try {
            $sourceMutexHeld = $sourceMutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $sourceMutexHeld = $true
        }
        Assert-Condition $sourceMutexHeld `
            'Outra execução do lifecycle de logs já possui o lock desta origem.'
        $sourceLockPath = Assert-DirectChildPath $logRoot $sourceLockLeafName
        if (Test-Path -LiteralPath $sourceLockPath) {
            $sourceLockItem = Get-Item -LiteralPath $sourceLockPath
            Assert-Condition (-not $sourceLockItem.PSIsContainer) `
                'Source lock deve ser arquivo regular.'
            Assert-NotReparsePoint $sourceLockItem `
                'Source lock não pode ser link/reparse point.'
        }
        try {
            $sourceFileLock = [IO.FileStream]::new(
                $sourceLockPath,
                [IO.FileMode]::OpenOrCreate,
                [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::None
            )
        } catch {
            throw 'Outra sessão já possui o file lock da origem de logs.'
        }
        Assert-NotReparsePoint (Get-Item -LiteralPath $sourceLockPath) `
            'Source lock não pode ser link/reparse point.'
        $operationMutex = [Threading.Mutex]::new(
            $false,
            $(if ($runningOnWindows) { 'Global\' } else { '' }) +
                'etl-v2-log-lifecycle-' + $scopeFingerprint
        )
        try {
            $mutexHeld = $operationMutex.WaitOne(0)
        } catch [Threading.AbandonedMutexException] {
            $mutexHeld = $true
        }
        Assert-Condition $mutexHeld `
            'Outra execução do lifecycle de logs já possui o lock deste escopo.'
        if (Test-Path -LiteralPath $archiveScopeRoot -PathType Container) {
            Assert-NotReparsePoint (Get-Item -LiteralPath $archiveScopeRoot) `
                'Namespace de archive não pode ser link/reparse point.'
            $operationLockPath = Assert-DirectChildPath $archiveScopeRoot `
                '.etl-v2-log-lifecycle.lock'
            if (Test-Path -LiteralPath $operationLockPath) {
                Assert-NotReparsePoint (Get-Item -LiteralPath $operationLockPath) `
                    'File lock não pode ser link/reparse point.'
            }
            try {
                $operationFileLock = [IO.FileStream]::new(
                    $operationLockPath,
                    [IO.FileMode]::OpenOrCreate,
                    [IO.FileAccess]::ReadWrite,
                    [IO.FileShare]::None
                )
            } catch {
                throw 'Outra sessão já possui o file lock deste lifecycle de logs.'
            }
            Assert-NotReparsePoint (Get-Item -LiteralPath $operationLockPath) `
                'File lock não pode ser link/reparse point.'
        }
        $policyGuard = [IO.FileStream]::new(
            $resolvedPolicyPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
        $existingMarker = $null
        if (Test-Path -LiteralPath $operationMarkerPath -PathType Leaf) {
            $existingMarker = Read-OperationMarker $operationMarkerPath
        }
        $pendingMarkerPath = $operationMarkerPath + '.pending'
        if (Test-Path -LiteralPath $pendingMarkerPath -PathType Leaf) {
            $pendingMarker = Read-OperationMarker $pendingMarkerPath
            Assert-Condition (Test-OperationMarkerMatches $pendingMarker `
                    $receiptId $scopeFingerprint $policy.policyFingerprint `
                    $archiveRoot $evaluationTimeUtcTicks) `
                'Há transição incompleta; retome policy e EvaluationTimeUtc originais.'
            if ($null -ne $existingMarker) {
                $existingMatchesCurrent = Test-OperationMarkerMatches $existingMarker `
                    $receiptId $scopeFingerprint $policy.policyFingerprint `
                    $archiveRoot $evaluationTimeUtcTicks
                Assert-Condition ($existingMatchesCurrent `
                        -or $existingMarker.state -ceq 'COMPLETE') `
                    'Outra operação incompleta precisa ser retomada antes desta transição.'
                Assert-Condition (-not ($existingMatchesCurrent `
                        -and $existingMarker.state -ceq 'COMPLETE' `
                        -and $pendingMarker.state -ceq 'ACTIVE')) `
                    'Transição COMPLETE para ACTIVE é proibida para a mesma operação.'
                if (-not $existingMatchesCurrent) {
                    Assert-CompletedOperationMarker $existingMarker $logRoot `
                        $scopeLogMaterial
                    Assert-Condition ($evaluationTimeUtcTicks -gt `
                            [long]$existingMarker.evaluationTimeUtcTicks) `
                        'Transição pendente não pode retroceder o instante da origem.'
                }
            }
            if ($pendingMarker.state -ceq 'COMPLETE') {
                $pendingCompletion = Read-BoundedJson $completionPath `
                    $receiptMaximumBytes 'Completion receipt da transição pendente'
                Assert-Condition ($pendingCompletion.sha256 -ceq `
                        $pendingMarker.completionSha256) `
                    'Transição COMPLETE pendente diverge do completion receipt.'
            }
            [IO.File]::Move($pendingMarkerPath, $operationMarkerPath, $true)
            $existingMarker = Read-OperationMarker $operationMarkerPath
        }
        if ($null -ne $existingMarker) {
            $markerMatchesCurrent = Test-OperationMarkerMatches $existingMarker `
                $receiptId $scopeFingerprint $policy.policyFingerprint `
                $archiveRoot $evaluationTimeUtcTicks
            if ($markerMatchesCurrent) {
                $currentOperationMarker = $existingMarker
                $activeOperationEstablished = $existingMarker.state -ceq 'ACTIVE'
                if ($existingMarker.state -ceq 'COMPLETE') {
                    Assert-Condition ((Test-Path -LiteralPath $permitPath -PathType Leaf) `
                            -and (Test-Path -LiteralPath $completionPath -PathType Leaf)) `
                        'Operation marker COMPLETE perdeu seus recibos; recuperação manual exigida.'
                }
            } else {
                Assert-Condition ($existingMarker.state -ceq 'COMPLETE') `
                    'Outra operação incompleta deve ser retomada com seu escopo original.'
                Assert-CompletedOperationMarker $existingMarker $logRoot `
                    $scopeLogMaterial
                if ($evaluationTimeUtcTicks -le `
                        [long]$existingMarker.evaluationTimeUtcTicks) {
                    Assert-Condition ((Test-Path -LiteralPath $permitPath -PathType Leaf) `
                            -and (Test-Path -LiteralPath $completionPath -PathType Leaf)) `
                        'Operação histórica sem recibos íntegros não pode renascer.'
                }
            }
        }
        if ($activeOperationEstablished `
                -and -not (Test-Path -LiteralPath $permitPath -PathType Leaf) `
                -and -not (Test-Path -LiteralPath $completionPath -PathType Leaf)) {
            Assert-NoOrphanTombstone $logRoot `
                ([int]$policy.maximumEntriesScanned)
        }
    }

    if ($Execute -and (Test-Path -LiteralPath $completionPath -PathType Leaf)) {
        $completionEnvelope = Read-BoundedJson $completionPath $receiptMaximumBytes `
            'Completion receipt'
        $completion = $completionEnvelope.value
        Assert-ExactProperties $completion @(
            'manifestVersion', 'receiptId', 'scopeFingerprint', 'policyFingerprint',
            'permitSha256', 'examinedFiles', 'archivedFiles', 'archivedBytes'
        ) 'Completion receipt'
        Assert-Condition ((Test-Integer $completion.manifestVersion) `
                -and $completion.manifestVersion -eq 1) `
            'Versão do completion receipt incompatível.'
        Assert-Condition ($completion.receiptId -ceq $receiptId `
                -and $completion.scopeFingerprint -ceq $scopeFingerprint `
                -and $completion.policyFingerprint -ceq $policy.policyFingerprint) `
            'Completion receipt pertence a outra operação.'
        Assert-Condition (Test-HexFingerprint $completion.permitSha256) `
            'Completion receipt contém hash de permit inválido.'
        Assert-Condition (Test-Path -LiteralPath $permitPath -PathType Leaf) `
            'Completion receipt perdeu seu permit receipt.'
        $permitGuard = [IO.FileStream]::new(
            $permitPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $completedPermitEnvelope = Read-BoundedJson $permitPath $receiptMaximumBytes `
            'Permit receipt'
        $permitHash = $completedPermitEnvelope.sha256
        Assert-Condition ($completion.permitSha256 -ceq $permitHash `
                -and (Get-BoundedFileSha256 $permitPath $receiptMaximumBytes `
                    'Permit receipt') -ceq $permitHash) `
            'Completion receipt não corresponde ao permit receipt.'
        $completedPermit = $completedPermitEnvelope.value
        $completedFacts = Assert-PermitReceipt `
            $completedPermit $policy $receiptId $scopeFingerprint `
            $evaluationTimeUtcTicks $cutoffUtcTicks $archiveScopeRoot
        Assert-Condition ((Test-Integer $completion.archivedFiles) `
                -and (Test-Integer $completion.archivedBytes) `
                -and (Test-Integer $completion.examinedFiles) `
                -and $completion.examinedFiles -eq $completedPermit.examinedFiles `
                -and $completion.archivedFiles -eq $completedFacts.files `
                -and $completion.archivedBytes -eq $completedFacts.bytes) `
            'Completion receipt contém totais divergentes.'
        for ($entryIndex = 0; $entryIndex -lt @($completedPermit.files).Count; $entryIndex++) {
            $entry = @($completedPermit.files)[$entryIndex]
            $completedSource = Assert-DirectChildPath $logRoot $entry.name
            $tombstoneName = '.etl-v2-log-lifecycle-{0}-{1}.pending-delete' -f `
                $receiptId, $entryIndex
            $completedTombstone = Assert-DirectChildPath $logRoot $tombstoneName
            Assert-Condition (-not (Test-Path -LiteralPath $completedSource) `
                    -and -not (Test-Path -LiteralPath $completedTombstone)) `
                'Source ou tombstone reapareceu após completion; exact retry recusado.'
        }
        Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
        if ($null -ne $currentOperationMarker) {
            if ($currentOperationMarker.state -ceq 'COMPLETE') {
                Assert-Condition ($currentOperationMarker.completionSha256 -ceq `
                        $completionEnvelope.sha256) `
                    'Operation marker diverge do completion receipt.'
            } else {
                Write-OperationMarker $operationMarkerPath $receiptId `
                    $scopeFingerprint $policy.policyFingerprint `
                    $archiveRoot $evaluationTimeUtcTicks 'COMPLETE' `
                    $completionEnvelope.sha256
            }
        }
        [pscustomobject]@{
            state = 'COMPLETE'
            examinedFiles = [int]$completion.examinedFiles
            candidateFiles = [int]$completion.archivedFiles
            candidateBytes = [long]$completion.archivedBytes
            archivedFiles = [int]$completion.archivedFiles
            exactRetry = $true
        }
        return
    }

    $permit = $null
    $permitHash = $null
    if ($Execute -and (Test-Path -LiteralPath $permitPath -PathType Leaf)) {
        Assert-Condition $activeOperationEstablished `
            'Permit incompleto não corresponde ao operation marker atual.'
        $permitGuard = [IO.FileStream]::new(
            $permitPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $permitEnvelope = Read-BoundedJson $permitPath $receiptMaximumBytes 'Permit receipt'
        $permit = $permitEnvelope.value
        $null = Assert-PermitReceipt `
            $permit $policy $receiptId $scopeFingerprint `
            $evaluationTimeUtcTicks $cutoffUtcTicks $archiveScopeRoot
        $permitHash = $permitEnvelope.sha256
        Assert-Condition ((Get-BoundedFileSha256 $permitPath $receiptMaximumBytes `
                    'Permit receipt') -ceq $permitHash) `
            'Permit receipt mudou durante a leitura.'
    }

    if ($null -eq $permit) {
        $scan = Get-EligibleLogs $logRoot $policy $cutoffUtc
        $candidateFiles = @($scan.files)
        if (-not $Execute) {
            [pscustomobject]@{
                state = 'DRY_RUN'
                examinedFiles = $scan.examined
                candidateFiles = $candidateFiles.Count
                candidateBytes = $scan.bytes
                archivedFiles = 0
                exactRetry = $false
            }
            return
        }

        if ($null -eq $operationFileLock) {
            if (-not (Test-Path -LiteralPath $archiveScopeRoot)) {
                $null = [IO.Directory]::CreateDirectory($archiveScopeRoot)
            }
            Assert-Condition (Test-Path -LiteralPath $archiveScopeRoot -PathType Container) `
                'Namespace de archive do escopo é inválido.'
            Assert-NotReparsePoint (Get-Item -LiteralPath $archiveScopeRoot) `
                'Namespace de archive não pode ser link/reparse point.'
            $operationLockPath = Assert-DirectChildPath $archiveScopeRoot `
                '.etl-v2-log-lifecycle.lock'
            if (Test-Path -LiteralPath $operationLockPath) {
                Assert-NotReparsePoint (Get-Item -LiteralPath $operationLockPath) `
                    'File lock não pode ser link/reparse point.'
            }
            try {
                $operationFileLock = [IO.FileStream]::new(
                    $operationLockPath,
                    [IO.FileMode]::OpenOrCreate,
                    [IO.FileAccess]::ReadWrite,
                    [IO.FileShare]::None
                )
            } catch {
                throw 'Outra sessão já possui o file lock deste lifecycle de logs.'
            }
            Assert-NotReparsePoint (Get-Item -LiteralPath $operationLockPath) `
                'File lock não pode ser link/reparse point.'
            Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
        }
        if (-not $activeOperationEstablished) {
            Assert-NoOrphanTombstone $logRoot `
                ([int]$policy.maximumEntriesScanned)
            Write-OperationMarker $operationMarkerPath $receiptId $scopeFingerprint `
                $policy.policyFingerprint $archiveRoot $evaluationTimeUtcTicks `
                'ACTIVE' $null
            $activeOperationEstablished = $true
            $currentOperationMarker = Read-OperationMarker $operationMarkerPath
        }

        $entries = @()
        $archivedSourceBytes = [long]0
        for ($candidateIndex = 0; $candidateIndex -lt $candidateFiles.Count; $candidateIndex++) {
            Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
            $candidate = Get-Item -LiteralPath $candidateFiles[$candidateIndex].FullName
            Assert-NotReparsePoint $candidate `
                'Log elegível mudou para link/reparse point durante a preparação.'
            Assert-Condition ($candidate.Name -cmatch $rolledLogPattern `
                    -and $candidate.LastWriteTimeUtc -le $cutoffUtc) `
                'Log deixou de ser elegível durante a preparação.'
            Assert-Condition ($candidate.Length -le [long]$policy.maximumSingleFileBytes `
                    -and [long]$candidate.Length -le `
                    ([long]$policy.maximumSourceBytes - $archivedSourceBytes)) `
                'Tamanho mudou ou excedeu o limite durante a preparação.'

            $archivePath = Assert-DirectChildPath $archiveScopeRoot $candidate.Name
            $pendingArchiveName = '.etl-v2-log-lifecycle-{0}-{1}.archive-pending' -f `
                $receiptId, $candidateIndex
            $pendingArchivePath = Assert-DirectChildPath $archiveScopeRoot $pendingArchiveName
            $candidateLength = [long]$candidate.Length
            $sourceHash = Get-BoundedFileSha256 $candidate.FullName `
                ([long]$policy.maximumSingleFileBytes) 'Log elegível' $candidateLength
            if (-not (Test-Path -LiteralPath $archivePath -PathType Leaf)) {
                if (Test-Path -LiteralPath $pendingArchivePath) {
                    $pendingArchive = Get-Item -LiteralPath $pendingArchivePath
                    Assert-NotReparsePoint $pendingArchive `
                        'Archive pendente não pode ser link/reparse point.'
                    if ($pendingArchive.Length -ne $candidateLength `
                            -or $pendingArchive.Length -gt `
                                [long]$policy.maximumSingleFileBytes) {
                        Remove-Item -LiteralPath $pendingArchivePath
                    } else {
                        $pendingHash = Get-BoundedFileSha256 $pendingArchivePath `
                            ([long]$policy.maximumSingleFileBytes) `
                            'Archive pendente' $candidateLength
                        if ($pendingHash -cne $sourceHash) {
                            Remove-Item -LiteralPath $pendingArchivePath
                        }
                    }
                }
                if (-not (Test-Path -LiteralPath $pendingArchivePath)) {
                    $sourceStream = [IO.FileStream]::new(
                        $candidate.FullName,
                        [IO.FileMode]::Open,
                        [IO.FileAccess]::Read,
                        [IO.FileShare]::Read
                    )
                    $archiveStream = $null
                    try {
                        Assert-Condition ($sourceStream.Length -eq $candidate.Length) `
                            'Log mudou antes da cópia de archive.'
                        $archiveStream = [IO.FileStream]::new(
                            $pendingArchivePath,
                            [IO.FileMode]::CreateNew,
                            [IO.FileAccess]::Write,
                            [IO.FileShare]::None
                        )
                        Copy-StreamExactly $sourceStream $archiveStream `
                            $candidateLength 'Log elegível'
                        $archiveStream.Flush($true)
                    } finally {
                        if ($null -ne $archiveStream) {
                            $archiveStream.Dispose()
                        }
                        $sourceStream.Dispose()
                    }
                }
                $pendingItem = Get-Item -LiteralPath $pendingArchivePath
                Assert-NotReparsePoint $pendingItem `
                    'Archive pendente não pode ser link/reparse point.'
                Assert-Condition ($pendingItem.Length -eq $candidateLength `
                        -and $pendingItem.Length -le `
                            [long]$policy.maximumSingleFileBytes) `
                    'Archive pendente excede ou diverge do tamanho autorizado.'
                $pendingHash = Get-BoundedFileSha256 $pendingArchivePath `
                    ([long]$policy.maximumSingleFileBytes) 'Archive pendente' `
                    $candidateLength
                $sourceHash = Get-BoundedFileSha256 $candidate.FullName `
                    ([long]$policy.maximumSingleFileBytes) 'Log elegível' `
                    $candidateLength
                Assert-Condition ($pendingItem.Length -eq $candidateLength `
                        -and $pendingHash -ceq $sourceHash) `
                    'Cópia pendente não reproduziu o log ativo; purge recusado.'
                [IO.File]::Move($pendingArchivePath, $archivePath)
            }

            $sourceItem = Get-Item -LiteralPath $candidate.FullName
            $archiveItem = Get-Item -LiteralPath $archivePath
            Assert-NotReparsePoint $sourceItem `
                'Log mudou para link/reparse point durante a preparação.'
            Assert-NotReparsePoint $archiveItem `
                'Archive mudou para link/reparse point durante a preparação.'
            $sourceHash = Get-BoundedFileSha256 $sourceItem.FullName `
                ([long]$policy.maximumSingleFileBytes) 'Log elegível' `
                ([long]$sourceItem.Length)
            $archiveHash = Get-BoundedFileSha256 $archiveItem.FullName `
                ([long]$policy.maximumSingleFileBytes) 'Archive' `
                ([long]$sourceItem.Length)
            Assert-Condition ($sourceItem.LastWriteTimeUtc -le $cutoffUtc `
                    -and $sourceItem.Length -eq $archiveItem.Length `
                    -and $sourceItem.Length -le [long]$policy.maximumSingleFileBytes `
                    -and $sourceHash -ceq $archiveHash) `
                'Source/archive divergiu antes da emissão do permit.'
            Assert-Condition ([long]$sourceItem.Length -le `
                    ([long]$policy.maximumSourceBytes - $archivedSourceBytes)) `
                'Bytes preparados excedem maximumSourceBytes; purge recusado.'
            $archivedSourceBytes += [long]$sourceItem.Length
            $entries += [ordered]@{
                name = $sourceItem.Name
                bytes = [long]$sourceItem.Length
                sha256 = $sourceHash
                lastWriteTimeUtcTicks = $sourceItem.LastWriteTimeUtc.Ticks
            }
        }

        Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
        $permitDocument = [ordered]@{
            manifestVersion = 1
            receiptId = $receiptId
            scopeFingerprint = $scopeFingerprint
            policyVersion = $policy.policyVersion
            policyFingerprint = $policy.policyFingerprint
            evaluationTimeUtcTicks = $evaluationTimeUtcTicks
            cutoffUtcTicks = $cutoffUtcTicks
            examinedFiles = $scan.examined
            files = $entries
        }
        Write-NewUtf8FileAtomic $permitPath `
            ($permitDocument | ConvertTo-Json -Depth 5 -Compress) $receiptMaximumBytes
        $permitGuard = [IO.FileStream]::new(
            $permitPath,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        $permitEnvelope = Read-BoundedJson $permitPath $receiptMaximumBytes 'Permit receipt'
        $permit = $permitEnvelope.value
        $null = Assert-PermitReceipt `
            $permit $policy $receiptId $scopeFingerprint `
            $evaluationTimeUtcTicks $cutoffUtcTicks $archiveScopeRoot
        $permitHash = $permitEnvelope.sha256
        Assert-Condition ((Get-BoundedFileSha256 $permitPath $receiptMaximumBytes `
                    'Permit receipt') -ceq $permitHash) `
            'Permit receipt mudou durante a leitura.'
    }

    $archivedBytes = [long]0
    for ($entryIndex = 0; $entryIndex -lt @($permit.files).Count; $entryIndex++) {
        $entry = @($permit.files)[$entryIndex]
        $sourcePath = Assert-DirectChildPath $logRoot $entry.name
        $tombstoneName = '.etl-v2-log-lifecycle-{0}-{1}.pending-delete' -f `
            $receiptId, $entryIndex
        $tombstonePath = Assert-DirectChildPath $logRoot $tombstoneName
        Assert-Condition (-not ((Test-Path -LiteralPath $sourcePath) `
                -and (Test-Path -LiteralPath $tombstonePath))) `
            'Source e tombstone coexistem; purge recusado.'

        $archivePathForGuard = Assert-DirectChildPath $archiveScopeRoot $entry.name
        $archiveGuard = [IO.FileStream]::new(
            $archivePathForGuard,
            [IO.FileMode]::Open,
            [IO.FileAccess]::Read,
            [IO.FileShare]::Read
        )
        try {
            if (Test-Path -LiteralPath $sourcePath -PathType Leaf) {
                $sourceItem = Get-Item -LiteralPath $sourcePath
                Assert-NotReparsePoint $sourceItem `
                    'Source do permit mudou para link/reparse point.'
                Assert-Condition ($sourceItem.Length -eq [long]$entry.bytes `
                        -and $sourceItem.LastWriteTimeUtc.Ticks -eq `
                        [long]$entry.lastWriteTimeUtcTicks) `
                    'Source do permit mudou de tamanho ou data.'
                $sourceHash = Get-BoundedFileSha256 $sourcePath `
                    ([long]$entry.bytes) 'Source do permit' ([long]$entry.bytes)
                Assert-Condition ($sourceHash -ceq $entry.sha256) `
                    'Source do permit mudou de conteúdo.'
                Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
                Assert-Condition ((Get-BoundedFileSha256 $permitPath `
                            $receiptMaximumBytes 'Permit receipt') -ceq $permitHash) `
                    'Permit receipt mudou durante a operação.'
                Assert-ArchiveEntry $entry $archiveScopeRoot
                [IO.File]::Move($sourcePath, $tombstonePath)
            }

            if (Test-Path -LiteralPath $tombstonePath -PathType Leaf) {
                try {
                $tombstone = Get-Item -LiteralPath $tombstonePath
                Assert-NotReparsePoint $tombstone `
                    'Tombstone não pode ser link/reparse point.'
                Assert-Condition ($tombstone.Length -eq [long]$entry.bytes `
                        -and $tombstone.LastWriteTimeUtc.Ticks -eq `
                        [long]$entry.lastWriteTimeUtcTicks) `
                    'Tombstone divergiu do permit receipt.'
                $tombstoneHash = Get-BoundedFileSha256 $tombstonePath `
                    ([long]$entry.bytes) 'Tombstone' ([long]$entry.bytes)
                Assert-Condition ($tombstoneHash -ceq $entry.sha256) `
                    'Tombstone divergiu do conteúdo arquivado.'
                Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
                Assert-Condition ((Get-BoundedFileSha256 $permitPath $receiptMaximumBytes `
                            'Permit receipt') -ceq $permitHash) `
                    'Permit receipt mudou durante a operação.'
                Assert-ArchiveEntry $entry $archiveScopeRoot
                } catch {
                    if (-not (Test-Path -LiteralPath $sourcePath) `
                            -and (Test-Path -LiteralPath $tombstonePath -PathType Leaf)) {
                        [IO.File]::Move($tombstonePath, $sourcePath)
                    }
                    throw
                }
                Remove-Item -LiteralPath $tombstonePath
            }
        } finally {
            $archiveGuard.Dispose()
        }
        Assert-Condition ([long]$entry.bytes -le `
                ([long]$policy.maximumSourceBytes - $archivedBytes)) `
            'Bytes removidos excederiam maximumSourceBytes.'
        $archivedBytes += [long]$entry.bytes
    }

    for ($entryIndex = 0; $entryIndex -lt @($permit.files).Count; $entryIndex++) {
        $entry = @($permit.files)[$entryIndex]
        $sourcePath = Assert-DirectChildPath $logRoot $entry.name
        $tombstoneName = '.etl-v2-log-lifecycle-{0}-{1}.pending-delete' -f `
            $receiptId, $entryIndex
        $tombstonePath = Assert-DirectChildPath $logRoot $tombstoneName
        Assert-Condition (-not (Test-Path -LiteralPath $sourcePath) `
                -and -not (Test-Path -LiteralPath $tombstonePath)) `
            'Source ou tombstone reapareceu antes do completion.'
    }
    Assert-PolicyFileUnchanged $resolvedPolicyPath $policyFileHash
    Assert-Condition ((Get-BoundedFileSha256 $permitPath $receiptMaximumBytes `
                'Permit receipt') -ceq $permitHash) `
        'Permit receipt mudou antes do completion.'
    $completionDocument = [ordered]@{
        manifestVersion = 1
        receiptId = $receiptId
        scopeFingerprint = $scopeFingerprint
        policyFingerprint = $policy.policyFingerprint
        permitSha256 = $permitHash
        examinedFiles = [int]$permit.examinedFiles
        archivedFiles = @($permit.files).Count
        archivedBytes = $archivedBytes
    }
    $completionJson = $completionDocument | ConvertTo-Json -Compress
    Write-NewUtf8FileAtomic $completionPath $completionJson $receiptMaximumBytes
    $writtenCompletion = Read-BoundedJson $completionPath $receiptMaximumBytes `
        'Completion receipt'
    Assert-ExactProperties $writtenCompletion.value @(
        'manifestVersion', 'receiptId', 'scopeFingerprint', 'policyFingerprint',
        'permitSha256', 'examinedFiles', 'archivedFiles', 'archivedBytes'
    ) 'Completion receipt'
    Assert-Condition ($writtenCompletion.sha256 -ceq (Get-Sha256Text $completionJson) `
            -and $writtenCompletion.value.receiptId -ceq $receiptId `
            -and $writtenCompletion.value.scopeFingerprint -ceq $scopeFingerprint `
            -and $writtenCompletion.value.policyFingerprint -ceq `
                $policy.policyFingerprint `
            -and $writtenCompletion.value.permitSha256 -ceq $permitHash `
            -and $writtenCompletion.value.examinedFiles -eq $permit.examinedFiles `
            -and $writtenCompletion.value.archivedFiles -eq @($permit.files).Count `
            -and $writtenCompletion.value.archivedBytes -eq $archivedBytes) `
        'Completion receipt não foi persistido integralmente.'
    Write-OperationMarker $operationMarkerPath $receiptId $scopeFingerprint `
        $policy.policyFingerprint $archiveRoot $evaluationTimeUtcTicks 'COMPLETE' `
        $writtenCompletion.sha256

    [pscustomobject]@{
        state = 'COMPLETE'
        examinedFiles = [int]$permit.examinedFiles
        candidateFiles = @($permit.files).Count
        candidateBytes = $archivedBytes
        archivedFiles = @($permit.files).Count
        exactRetry = $false
    }
} finally {
    if ($null -ne $permitGuard) {
        $permitGuard.Dispose()
    }
    if ($null -ne $policyGuard) {
        $policyGuard.Dispose()
    }
    if ($null -ne $operationFileLock) {
        $operationFileLock.Dispose()
    }
    if ($mutexHeld) {
        $operationMutex.ReleaseMutex()
    }
    if ($null -ne $operationMutex) {
        $operationMutex.Dispose()
    }
    if ($sourceMutexHeld) {
        if ($null -ne $sourceFileLock) {
            $sourceFileLock.Dispose()
            $sourceFileLock = $null
        }
        $sourceMutex.ReleaseMutex()
    }
    if ($null -ne $sourceMutex) {
        $sourceMutex.Dispose()
    }
}

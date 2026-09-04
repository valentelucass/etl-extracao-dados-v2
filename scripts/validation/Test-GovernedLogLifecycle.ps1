#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$lifecycleScript = Join-Path $repositoryRoot `
    'scripts\operations\Invoke-GovernedLogLifecycle.ps1'
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$testDirectory = [IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ('etl-v2-log-lifecycle-' + [Guid]::NewGuid().ToString('N')))
)

function Assert-Condition {
    param([bool]$Condition, [string]$Message)

    if (-not $Condition) {
        throw $Message
    }
}

function Get-Sha256Text {
    param([string]$Text)

    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
        return ([BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

function Write-Utf8File {
    param([string]$Path, [string]$Content)

    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Write-EnabledPolicy {
    param(
        [string]$Path,
        [string]$LogDirectory,
        [string]$ArchiveDirectory,
        [bool]$LegalHoldActive,
        [int]$MaximumEntriesScanned = 100,
        [int]$MaximumFilesPerRun = 10,
        [long]$MaximumSingleFileBytes = 1048576,
        [long]$MaximumSourceBytes = 1048576,
        [string]$PolicyVersion = 'synthetic-v1',
        [string]$DataOwnerEvidence = ('a' * 64),
        [string]$ComplianceEvidence = ('b' * 64)
    )

    $logRoot = [IO.Path]::GetFullPath((Resolve-Path $LogDirectory).ProviderPath).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $archiveRoot = [IO.Path]::GetFullPath((Resolve-Path $ArchiveDirectory).ProviderPath).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $scopeLogMaterial = $logRoot
    $scopeArchiveMaterial = $archiveRoot
    if ([IO.Path]::DirectorySeparatorChar -eq '\') {
        $scopeLogMaterial = $scopeLogMaterial.ToUpperInvariant()
        $scopeArchiveMaterial = $scopeArchiveMaterial.ToUpperInvariant()
    }
    $scopeCanonical = 'log-lifecycle-scope-v2|{0}:{1}|{2}:{3}' -f @(
        [Text.Encoding]::UTF8.GetByteCount($scopeLogMaterial),
        $scopeLogMaterial,
        [Text.Encoding]::UTF8.GetByteCount($scopeArchiveMaterial),
        $scopeArchiveMaterial
    )
    $scopeFingerprint = Get-Sha256Text $scopeCanonical
    $canonicalTemplate = 'log-lifecycle-policy-v1|enabled=true|policyVersion={0}' +
        '|retentionDays={1}|maximumEntriesScanned={2}|maximumFilesPerRun={3}' +
        '|maximumSingleFileBytes={4}|maximumSourceBytes={5}|scopeFingerprint={6}' +
        '|legalHoldActive={7}|dataOwnerEvidence={8}|complianceEvidence={9}'
    $canonical = $canonicalTemplate -f @(
        $PolicyVersion,
        3,
        $MaximumEntriesScanned,
        $MaximumFilesPerRun,
        $MaximumSingleFileBytes,
        $MaximumSourceBytes,
        $scopeFingerprint,
        $LegalHoldActive.ToString().ToLowerInvariant(),
        $dataOwnerEvidence,
        $complianceEvidence
    )
    $policy = [ordered]@{
        manifestVersion = 1
        enabled = $true
        policyVersion = $PolicyVersion
        retentionDays = 3
        maximumEntriesScanned = $MaximumEntriesScanned
        maximumFilesPerRun = $MaximumFilesPerRun
        maximumSingleFileBytes = $MaximumSingleFileBytes
        maximumSourceBytes = $MaximumSourceBytes
        scopeFingerprint = $scopeFingerprint
        policyFingerprint = Get-Sha256Text $canonical
        dataOwnerEvidenceFingerprint = $dataOwnerEvidence
        complianceEvidenceFingerprint = $complianceEvidence
        legalHoldActive = $LegalHoldActive
    }
    Write-Utf8File $Path ($policy | ConvertTo-Json -Depth 4)
}

function New-TestArea {
    param([string]$Name)

    $area = Join-Path $testDirectory $Name
    $logs = Join-Path $area 'logs'
    $archive = Join-Path $area 'archive'
    $null = New-Item -ItemType Directory -Path $logs -Force
    $null = New-Item -ItemType Directory -Path $archive -Force
    return [pscustomobject]@{
        root = $area
        logs = $logs
        archive = $archive
        policy = Join-Path $area 'policy.json'
    }
}

function Write-LogFixture {
    param([string]$Path, [string]$Content, [DateTime]$LastWriteTimeUtc)

    Write-Utf8File $Path $Content
    (Get-Item -LiteralPath $Path).LastWriteTimeUtc = $LastWriteTimeUtc
}

function Invoke-Lifecycle {
    param(
        [object]$Area,
        [switch]$Execute,
        [DateTimeOffset]$EvaluationTimeUtc = [DateTimeOffset]::Parse(
            '2026-01-10T00:00:00Z'
        )
    )

    $arguments = @{
        LogDirectory = $Area.logs
        ArchiveDirectory = $Area.archive
        PolicyPath = $Area.policy
        EvaluationTimeUtc = $EvaluationTimeUtc
    }
    if ($Execute) {
        $arguments.Execute = $true
    }
    return & $lifecycleScript @arguments
}

function Assert-FreshInertAreaUnchanged {
    param(
        [object]$Area,
        [string]$ExpectedLogPath,
        [string]$Context
    )

    foreach ($controlLeafName in @(
            '.etl-v2-log-source.lock',
            '.etl-v2-log-lifecycle.operation.json',
            '.etl-v2-log-lifecycle.operation.json.pending'
        )) {
        Assert-Condition (-not (Test-Path -LiteralPath `
                    (Join-Path $Area.logs $controlLeafName))) `
            "$Context criou controle no diretório de origem."
    }
    Assert-Condition (@(Get-ChildItem -LiteralPath $Area.logs -Recurse -Force `
                -Filter '.etl-v2-log-lifecycle-*.pending-delete').Count -eq 0) `
        "$Context criou tombstone no diretório de origem."
    $sourceEntries = @(Get-ChildItem -LiteralPath $Area.logs -Recurse -Force)
    Assert-Condition ($sourceEntries.Count -eq 1 `
            -and $sourceEntries[0].FullName -ceq $ExpectedLogPath) `
        "$Context criou artefato inesperado no diretório de origem."
    Assert-Condition (@(Get-ChildItem -LiteralPath $Area.archive `
                -Recurse -Force).Count -eq 0) `
        "$Context criou namespace, lock, archive ou receipt."
}

function Get-SourceMutexName {
    param([string]$LogDirectory)

    $logRoot = [IO.Path]::GetFullPath((Resolve-Path $LogDirectory).ProviderPath).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    if ([IO.Path]::DirectorySeparatorChar -eq '\') {
        $logRoot = $logRoot.ToUpperInvariant()
    }
    $canonical = 'log-lifecycle-source-v1|{0}:{1}' -f @(
        [Text.Encoding]::UTF8.GetByteCount($logRoot),
        $logRoot
    )
    return $(if ([IO.Path]::DirectorySeparatorChar -eq '\') { 'Global\' } else { '' }) +
        'etl-v2-log-source-' + (Get-Sha256Text $canonical)
}

function Assert-Throws {
    param([scriptblock]$Action, [string]$Message)

    $thrown = $false
    try {
        & $Action
    } catch {
        $thrown = $true
    }
    Assert-Condition $thrown $Message
}

try {
    $null = New-Item -ItemType Directory -Path $testDirectory

    $nonIndependent = New-TestArea 'non-independent-evidence'
    Write-EnabledPolicy $nonIndependent.policy $nonIndependent.logs `
        $nonIndependent.archive $false -PolicyVersion 'synthetic-non-independent-v1' `
        -DataOwnerEvidence ('a' * 64) -ComplianceEvidence ('a' * 64)
    Assert-Throws { Invoke-Lifecycle $nonIndependent } `
        'Política de logs aceitou a mesma evidência para data owner e compliance.'
    Assert-Condition (@(Get-ChildItem -LiteralPath $nonIndependent.archive `
                -Recurse -Force).Count -eq 0) `
        'Ratificação não independente criou artefato de archive.'

    $inertHistory = New-TestArea 'inert-policy-history'
    Write-EnabledPolicy $inertHistory.policy $inertHistory.logs $inertHistory.archive $false `
        -PolicyVersion 'synthetic-inert-history-v1'
    $inertHistoryLog = Join-Path $inertHistory.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $inertHistoryLog 'inert-history' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $inertHistory -Execute
    $inertMarkerPath = Join-Path $inertHistory.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $inertMarker = Get-Content -LiteralPath $inertMarkerPath -Raw | ConvertFrom-Json
    $inertPermit = @(Get-ChildItem -LiteralPath $inertHistory.archive -Recurse `
            -Filter 'log-lifecycle-*.permit.json' -File)[0]
    $inertCompletion = @(Get-ChildItem -LiteralPath $inertHistory.archive -Recurse `
            -Filter 'log-lifecycle-*.complete.json' -File)[0]
    Remove-Item -LiteralPath $inertCompletion.FullName
    Write-Utf8File $inertHistory.policy '{"manifestVersion":1,"enabled":false}'
    Assert-Throws { Invoke-Lifecycle $inertHistory -Execute } `
        'Política disabled mascarou marker COMPLETE sem completion receipt.'

    $inertMarker.state = 'ACTIVE'
    $inertMarker.completionSha256 = $null
    Write-Utf8File $inertMarkerPath ($inertMarker | ConvertTo-Json -Compress)
    $inertTombstone = Join-Path $inertHistory.logs `
        ('.etl-v2-log-lifecycle-{0}-0.pending-delete' -f $inertMarker.receiptId)
    Write-Utf8File $inertTombstone 'inert-history'
    Write-EnabledPolicy $inertHistory.policy $inertHistory.logs $inertHistory.archive $true `
        -PolicyVersion 'synthetic-inert-hold-v1'
    Assert-Throws { Invoke-Lifecycle $inertHistory -Execute } `
        'Política em legal hold mascarou marker ACTIVE e tombstone pendente.'
    Write-Utf8File $inertHistory.policy '{"manifestVersion":1,"enabled":false}'
    Assert-Throws { Invoke-Lifecycle $inertHistory -Execute } `
        'Política disabled mascarou marker ACTIVE e tombstone pendente.'
    Assert-Condition ((Test-Path -LiteralPath $inertPermit.FullName -PathType Leaf) `
            -and (Test-Path -LiteralPath $inertTombstone -PathType Leaf)) `
        'Validação de política inerte alterou evidência incompleta.'

    $normal = New-TestArea 'normal'
    Write-EnabledPolicy $normal.policy $normal.logs $normal.archive $false
    $activeLog = Join-Path $normal.logs 'etl-dataexport-v2.log'
    $oldLog = Join-Path $normal.logs 'etl-dataexport-v2.2026-01-01.log'
    $recentLog = Join-Path $normal.logs 'etl-dataexport-v2.2026-01-09.log'
    $otherLog = Join-Path $normal.logs 'unmanaged.2026-01-01.log'
    Write-LogFixture $activeLog 'active' ([DateTime]'2026-01-01T00:00:00Z')
    Write-LogFixture $oldLog 'old' ([DateTime]'2026-01-01T00:00:00Z')
    Write-LogFixture $recentLog 'recent' ([DateTime]'2026-01-09T00:00:00Z')
    Write-LogFixture $otherLog 'other' ([DateTime]'2026-01-01T00:00:00Z')

    $dryRun = Invoke-Lifecycle $normal
    Assert-Condition ($dryRun.state -ceq 'DRY_RUN' -and $dryRun.candidateFiles -eq 1) `
        'Dry-run não selecionou exatamente o log rolado elegível.'
    Assert-Condition (Test-Path -LiteralPath $oldLog -PathType Leaf) `
        'Dry-run removeu um log.'
    Assert-Condition (@(Get-ChildItem -LiteralPath $normal.archive -File).Count -eq 0) `
        'Dry-run criou archive ou recibo.'

    $executed = Invoke-Lifecycle $normal -Execute
    Assert-Condition ($executed.state -ceq 'COMPLETE' `
            -and $executed.archivedFiles -eq 1 -and -not $executed.exactRetry) `
        'Execução governada não concluiu o archive e purge esperados.'
    Assert-Condition (-not (Test-Path -LiteralPath $oldLog)) `
        'Log elegível permaneceu após completion.'
    $archivedLog = @(Get-ChildItem -LiteralPath $normal.archive -Recurse `
            -Filter 'etl-dataexport-v2.2026-01-01.log' -File)
    Assert-Condition ($archivedLog.Count -eq 1) `
        'Archive do log elegível não foi preservado.'
    foreach ($preserved in @($activeLog, $recentLog, $otherLog)) {
        Assert-Condition (Test-Path -LiteralPath $preserved -PathType Leaf) `
            'Arquivo fora da allowlist/cutoff foi alterado.'
    }

    $completionReceipt = @(Get-ChildItem -LiteralPath $normal.archive `
            -Recurse -Filter 'log-lifecycle-*.complete.json' -File)
    Assert-Condition ($completionReceipt.Count -eq 1) `
        'Completion receipt não foi materializado de forma única.'
    Remove-Item -LiteralPath $completionReceipt[0].FullName
    $normalMarkerPath = Join-Path $normal.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $normalMarker = Get-Content -LiteralPath $normalMarkerPath -Raw | ConvertFrom-Json
    $normalMarker.state = 'ACTIVE'
    $normalMarker.completionSha256 = $null
    Write-Utf8File $normalMarkerPath ($normalMarker | ConvertTo-Json -Compress)
    $resumed = Invoke-Lifecycle $normal -Execute
    Assert-Condition ($resumed.state -ceq 'COMPLETE' -and -not $resumed.exactRetry) `
        'Retomada após interrupção não completou a partir do permit receipt.'
    $retried = Invoke-Lifecycle $normal -Execute
    Assert-Condition ($retried.state -ceq 'COMPLETE' -and $retried.exactRetry) `
        'Exact retry não releu o completion receipt.'

    $normalScopeDirectory = $completionReceipt[0].DirectoryName
    foreach ($historyIndex in 1..125) {
        Write-Utf8File (Join-Path $normalScopeDirectory `
                ('.synthetic-completed-history-{0:D3}.json' -f $historyIndex)) '{}'
    }
    foreach ($directoryIndex in 1..101) {
        $null = New-Item -ItemType Directory -Path (Join-Path $normal.logs `
                ('unmanaged-{0:D3}' -f $directoryIndex))
    }
    $boundedRetry = Invoke-Lifecycle $normal -Execute
    Assert-Condition ($boundedRetry.state -ceq 'COMPLETE' `
            -and $boundedRetry.exactRetry) `
        'Exact retry ficou dependente de varrer histórico ou diretório ambiente.'

    Write-Utf8File $archivedLog[0].FullName 'tampered'
    Assert-Throws { Invoke-Lifecycle $normal -Execute } `
        'Exact retry aceitou archive corrompido.'

    $held = New-TestArea 'held'
    Write-EnabledPolicy $held.policy $held.logs $held.archive $true `
        -PolicyVersion 'synthetic-held-v1'
    $heldLog = Join-Path $held.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $heldLog 'held' ([DateTime]'2026-01-01T00:00:00Z')
    $holdResult = Invoke-Lifecycle $held -Execute
    Assert-Condition ($holdResult.state -ceq 'LEGAL_HOLD' `
            -and (Test-Path -LiteralPath $heldLog -PathType Leaf)) `
        'Legal hold não bloqueou efeitos sobre logs.'
    Assert-FreshInertAreaUnchanged $held $heldLog 'Legal hold em origem nova'

    $capped = New-TestArea 'capped'
    Write-EnabledPolicy $capped.policy $capped.logs $capped.archive $false `
        -MaximumFilesPerRun 1 `
        -PolicyVersion 'synthetic-capped-v1'
    foreach ($day in @('01', '02')) {
        Write-LogFixture (Join-Path $capped.logs `
                "etl-dataexport-v2.2026-01-$day.log") 'capped' `
            ([DateTime]"2026-01-${day}T00:00:00Z")
    }
    Assert-Throws { Invoke-Lifecycle $capped -Execute } `
        'O teto de arquivos não falhou fechado.'
    Assert-Condition (@(Get-ChildItem -LiteralPath $capped.logs `
                -Filter 'etl-dataexport-v2.*.log' -File).Count -eq 2 `
            -and @(Get-ChildItem -LiteralPath $capped.archive -Recurse -Force).Count -eq 0) `
        'Falha de limite alterou arquivos.'

    $oversizedPending = New-TestArea 'oversized-pending'
    Write-EnabledPolicy $oversizedPending.policy $oversizedPending.logs `
        $oversizedPending.archive $false -MaximumSingleFileBytes 8 `
        -MaximumSourceBytes 8 -PolicyVersion 'synthetic-pending-v1'
    $oversizedPendingLog = Join-Path $oversizedPending.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $oversizedPendingLog 'ok' ([DateTime]'2026-01-01T00:00:00Z')
    $oversizedPolicy = Get-Content -LiteralPath $oversizedPending.policy -Raw |
        ConvertFrom-Json
    $oversizedEvaluation = [DateTimeOffset]::Parse('2026-01-10T00:00:00Z')
    $oversizedReceiptId = Get-Sha256Text ('{0}|{1}|{2}' -f @(
        $oversizedPolicy.policyFingerprint,
        $oversizedEvaluation.UtcDateTime.Ticks,
        $oversizedPolicy.scopeFingerprint
    ))
    $oversizedScopeRoot = Join-Path $oversizedPending.archive `
        ("scope-$($oversizedPolicy.scopeFingerprint)")
    $null = New-Item -ItemType Directory -Path $oversizedScopeRoot
    $oversizedMarker = [ordered]@{
        manifestVersion = 1
        state = 'ACTIVE'
        receiptId = $oversizedReceiptId
        scopeFingerprint = $oversizedPolicy.scopeFingerprint
        policyFingerprint = $oversizedPolicy.policyFingerprint
        archiveRoot = [IO.Path]::GetFullPath($oversizedPending.archive)
        evaluationTimeUtcTicks = $oversizedEvaluation.UtcDateTime.Ticks
        completionSha256 = $null
    }
    Write-Utf8File (Join-Path $oversizedPending.logs `
            '.etl-v2-log-lifecycle.operation.json') `
        ($oversizedMarker | ConvertTo-Json -Compress)
    $oversizedPendingPath = Join-Path $oversizedScopeRoot `
        ('.etl-v2-log-lifecycle-{0}-0.archive-pending' -f $oversizedReceiptId)
    Write-Utf8File $oversizedPendingPath ('x' * 128)
    $oversizedPendingResult = Invoke-Lifecycle $oversizedPending -Execute
    Assert-Condition ($oversizedPendingResult.state -ceq 'COMPLETE' `
            -and $oversizedPendingResult.archivedFiles -eq 1 `
            -and -not (Test-Path -LiteralPath $oversizedPendingPath)) `
        'Archive pendente oversized não foi descartado antes da retomada limitada.'

    $scanCapped = New-TestArea 'scan-capped'
    Write-EnabledPolicy $scanCapped.policy $scanCapped.logs $scanCapped.archive $false `
        -MaximumEntriesScanned 1 -PolicyVersion 'synthetic-scan-capped-v1'
    $null = New-Item -ItemType Directory -Path (Join-Path $scanCapped.logs 'first')
    $null = New-Item -ItemType Directory -Path (Join-Path $scanCapped.logs 'second')
    Assert-Throws { Invoke-Lifecycle $scanCapped } `
        'O teto de entradas do diretório não falhou fechado.'

    $forged = New-TestArea 'forged-receipt'
    Write-EnabledPolicy $forged.policy $forged.logs $forged.archive $false `
        -PolicyVersion 'synthetic-forged-v1'
    $forgedLog = Join-Path $forged.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $forgedLog 'forged' ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $forged -Execute
    $forgedCompletion = @(Get-ChildItem -LiteralPath $forged.archive -Recurse `
            -Filter 'log-lifecycle-*.complete.json' -File)
    $forgedPermit = @(Get-ChildItem -LiteralPath $forged.archive -Recurse `
            -Filter 'log-lifecycle-*.permit.json' -File)
    Assert-Condition ($forgedCompletion.Count -eq 1 -and $forgedPermit.Count -eq 1) `
        'Fixture de recibo forjado não produziu recibos únicos.'
    Remove-Item -LiteralPath $forgedCompletion[0].FullName
    $forgedMarkerPath = Join-Path $forged.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $forgedMarker = Get-Content -LiteralPath $forgedMarkerPath -Raw | ConvertFrom-Json
    $forgedMarker.state = 'ACTIVE'
    $forgedMarker.completionSha256 = $null
    Write-Utf8File $forgedMarkerPath ($forgedMarker | ConvertTo-Json -Compress)
    $permitDocument = Get-Content -LiteralPath $forgedPermit[0].FullName -Raw |
        ConvertFrom-Json
    $permitDocument.files[0].lastWriteTimeUtcTicks = `
        ([DateTime]'2026-01-09T00:00:00Z').Ticks
    Write-Utf8File $forgedPermit[0].FullName `
        ($permitDocument | ConvertTo-Json -Depth 5 -Compress)
    Write-LogFixture $forgedLog 'forged' ([DateTime]'2026-01-09T00:00:00Z')
    Assert-Throws { Invoke-Lifecycle $forged -Execute } `
        'Permit adulterado autorizou source posterior ao cutoff.'
    Assert-Condition (Test-Path -LiteralPath $forgedLog -PathType Leaf) `
        'Source recente foi removido por permit adulterado.'

    $foreignActive = New-TestArea 'foreign-active-operation'
    Write-EnabledPolicy $foreignActive.policy $foreignActive.logs `
        $foreignActive.archive $false -PolicyVersion 'synthetic-active-v1'
    $foreignActiveLog = Join-Path $foreignActive.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $foreignActiveLog 'active-operation' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $foreignActive -Execute
    $foreignPermitPath = @(Get-ChildItem -LiteralPath $foreignActive.archive -Recurse `
            -Filter 'log-lifecycle-*.permit.json' -File)[0].FullName
    $foreignCompletionPath = @(Get-ChildItem -LiteralPath $foreignActive.archive `
            -Recurse -Filter 'log-lifecycle-*.complete.json' -File)[0].FullName
    $foreignPermit = Get-Content -LiteralPath $foreignPermitPath -Raw | ConvertFrom-Json
    Remove-Item -LiteralPath $foreignCompletionPath
    $foreignMarkerPath = Join-Path $foreignActive.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $foreignMarker = [ordered]@{
        manifestVersion = 1
        state = 'ACTIVE'
        receiptId = $foreignPermit.receiptId
        scopeFingerprint = $foreignPermit.scopeFingerprint
        policyFingerprint = $foreignPermit.policyFingerprint
        archiveRoot = [IO.Path]::GetFullPath($foreignActive.archive)
        evaluationTimeUtcTicks = $foreignPermit.evaluationTimeUtcTicks
        completionSha256 = $null
    }
    Write-Utf8File $foreignMarkerPath ($foreignMarker | ConvertTo-Json -Compress)
    Assert-Throws {
        Invoke-Lifecycle $foreignActive -Execute `
            -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-11T00:00:00Z'))
    } 'Operação incompleta aceitou policy/instante divergente.'
    $foreignResumed = Invoke-Lifecycle $foreignActive -Execute
    Assert-Condition ($foreignResumed.state -ceq 'COMPLETE' `
            -and -not $foreignResumed.exactRetry `
            -and (Test-Path -LiteralPath $foreignMarkerPath -PathType Leaf)) `
        'Operação incompleta não retomou com a identidade original.'

    $orphan = New-TestArea 'orphan-tombstone'
    Write-EnabledPolicy $orphan.policy $orphan.logs $orphan.archive $false `
        -PolicyVersion 'synthetic-orphan-v1'
    $orphanLog = Join-Path $orphan.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $orphanLog 'orphan' ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $orphan -Execute
    $orphanPermitPath = @(Get-ChildItem -LiteralPath $orphan.archive -Recurse `
            -Filter 'log-lifecycle-*.permit.json' -File)[0].FullName
    $orphanCompletionPath = @(Get-ChildItem -LiteralPath $orphan.archive -Recurse `
            -Filter 'log-lifecycle-*.complete.json' -File)[0].FullName
    $orphanPermit = Get-Content -LiteralPath $orphanPermitPath -Raw | ConvertFrom-Json
    $orphanMarkerPath = Join-Path $orphan.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $orphanMarker = [ordered]@{
        manifestVersion = 1
        state = 'ACTIVE'
        receiptId = $orphanPermit.receiptId
        scopeFingerprint = $orphanPermit.scopeFingerprint
        policyFingerprint = $orphanPermit.policyFingerprint
        archiveRoot = [IO.Path]::GetFullPath($orphan.archive)
        evaluationTimeUtcTicks = $orphanPermit.evaluationTimeUtcTicks
        completionSha256 = $null
    }
    Remove-Item -LiteralPath $orphanCompletionPath
    Remove-Item -LiteralPath $orphanPermitPath
    Write-Utf8File $orphanMarkerPath ($orphanMarker | ConvertTo-Json -Compress)
    $orphanTombstone = Join-Path $orphan.logs `
        ('.etl-v2-log-lifecycle-{0}-0.pending-delete' -f $orphanPermit.receiptId)
    Write-Utf8File $orphanTombstone 'orphan'
    Assert-Throws { Invoke-Lifecycle $orphan -Execute } `
        'Tombstone sem permit íntegro produziu novo completion.'
    Assert-Condition (Test-Path -LiteralPath $orphanTombstone -PathType Leaf) `
        'Tombstone sem permit foi removido em vez de falhar fechado.'

    $sequential = New-TestArea 'sequential-operations'
    Write-EnabledPolicy $sequential.policy $sequential.logs $sequential.archive $false `
        -PolicyVersion 'synthetic-sequential-v1'
    Write-LogFixture (Join-Path $sequential.logs `
            'etl-dataexport-v2.2026-01-01.log') 'first' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $sequential -Execute
    Write-LogFixture (Join-Path $sequential.logs `
            'etl-dataexport-v2.2026-01-02.log') 'second' `
        ([DateTime]'2026-01-02T00:00:00Z')
    $secondCycle = Invoke-Lifecycle $sequential -Execute `
        -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-11T00:00:00Z'))
    Assert-Condition ($secondCycle.state -ceq 'COMPLETE' `
            -and $secondCycle.archivedFiles -eq 1 `
            -and -not $secondCycle.exactRetry) `
        'Operation marker não avançou após uma operação anterior íntegra.'

    $historicalPermit = New-TestArea 'historical-incomplete-permit'
    Write-EnabledPolicy $historicalPermit.policy $historicalPermit.logs `
        $historicalPermit.archive $false -PolicyVersion 'synthetic-history-v1'
    $historicalFirstLog = Join-Path $historicalPermit.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $historicalFirstLog 'historical-first' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $historicalPermit -Execute
    $historicalFirstPermit = @(Get-ChildItem -LiteralPath $historicalPermit.archive `
            -Recurse -Filter 'log-lifecycle-*.permit.json' -File)[0]
    $historicalFirstDocument = Get-Content -LiteralPath $historicalFirstPermit.FullName `
        -Raw | ConvertFrom-Json
    $historicalFirstCompletion = Join-Path $historicalFirstPermit.DirectoryName `
        ("log-lifecycle-$($historicalFirstDocument.receiptId).complete.json")
    $historicalSecondLog = Join-Path $historicalPermit.logs `
        'etl-dataexport-v2.2026-01-02.log'
    Write-LogFixture $historicalSecondLog 'historical-second' `
        ([DateTime]'2026-01-02T00:00:00Z')
    $null = Invoke-Lifecycle $historicalPermit -Execute `
        -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-11T00:00:00Z'))
    Remove-Item -LiteralPath $historicalFirstCompletion
    Write-LogFixture $historicalFirstLog 'historical-first' `
        ([DateTime]'2026-01-01T00:00:00Z')
    Assert-Throws { Invoke-Lifecycle $historicalPermit -Execute } `
        'Permit histórico incompleto contornou o operation marker mais novo.'
    Assert-Condition (Test-Path -LiteralPath $historicalFirstLog -PathType Leaf) `
        'Permit histórico incompleto removeu source sob erro.'
    Remove-Item -LiteralPath $historicalFirstPermit.FullName
    Assert-Throws { Invoke-Lifecycle $historicalPermit -Execute } `
        'Operação histórica sem ambos os recibos renasceu após marker mais novo.'
    Assert-Condition (Test-Path -LiteralPath $historicalFirstLog -PathType Leaf) `
        'Operação histórica sem recibos removeu source restaurado.'

    $lostReceipts = New-TestArea 'complete-marker-lost-receipts'
    Write-EnabledPolicy $lostReceipts.policy $lostReceipts.logs `
        $lostReceipts.archive $false -PolicyVersion 'synthetic-lost-v1'
    $lostReceiptsLog = Join-Path $lostReceipts.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $lostReceiptsLog 'lost-receipts' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $lostReceipts -Execute
    $lostPermit = @(Get-ChildItem -LiteralPath $lostReceipts.archive -Recurse `
            -Filter 'log-lifecycle-*.permit.json' -File)[0]
    $lostCompletion = @(Get-ChildItem -LiteralPath $lostReceipts.archive -Recurse `
            -Filter 'log-lifecycle-*.complete.json' -File)[0]
    Remove-Item -LiteralPath $lostPermit.FullName
    Remove-Item -LiteralPath $lostCompletion.FullName
    Write-LogFixture $lostReceiptsLog 'lost-receipts' `
        ([DateTime]'2026-01-01T00:00:00Z')
    Assert-Throws { Invoke-Lifecycle $lostReceipts -Execute } `
        'Marker COMPLETE sem recibos foi rebaixado para ACTIVE.'
    Assert-Throws {
        Invoke-Lifecycle $lostReceipts -Execute `
            -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-11T00:00:00Z'))
    } 'Nova identidade contornou marker COMPLETE com recibos ausentes.'
    $lostMarkerPath = Join-Path $lostReceipts.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $lostMarker = Get-Content -LiteralPath $lostMarkerPath -Raw | ConvertFrom-Json
    $staleActiveMarker = Get-Content -LiteralPath $lostMarkerPath -Raw | ConvertFrom-Json
    $staleActiveMarker.state = 'ACTIVE'
    $staleActiveMarker.completionSha256 = $null
    Write-Utf8File ($lostMarkerPath + '.pending') `
        ($staleActiveMarker | ConvertTo-Json -Compress)
    Assert-Throws { Invoke-Lifecycle $lostReceipts -Execute } `
        'Pending ACTIVE rebaixou marker COMPLETE da mesma identidade.'
    Assert-Condition ($lostMarker.state -ceq 'COMPLETE' `
            -and (Test-Path -LiteralPath $lostReceiptsLog -PathType Leaf)) `
        'Perda de recibos alterou marker COMPLETE ou source restaurado.'

    $scopeMismatch = New-TestArea 'scope-mismatch'
    $differentScope = New-TestArea 'different-scope'
    Write-EnabledPolicy $scopeMismatch.policy $differentScope.logs `
        $differentScope.archive $false -PolicyVersion 'synthetic-scope-v1'
    $scopeLog = Join-Path $scopeMismatch.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $scopeLog 'scope' ([DateTime]'2026-01-01T00:00:00Z')
    Assert-Throws { Invoke-Lifecycle $scopeMismatch -Execute } `
        'Política aprovada para outro escopo foi reutilizada.'
    Assert-Condition (Test-Path -LiteralPath $scopeLog -PathType Leaf) `
        'Falha de escopo alterou source.'

    if ([IO.Path]::DirectorySeparatorChar -eq '\') {
        $caseVariant = New-TestArea 'case-variant'
        Write-EnabledPolicy $caseVariant.policy $caseVariant.logs `
            $caseVariant.archive $false -PolicyVersion 'synthetic-case-v1'
        $caseLog = Join-Path $caseVariant.logs 'etl-dataexport-v2.2026-01-01.log'
        Write-LogFixture $caseLog 'case' ([DateTime]'2026-01-01T00:00:00Z')
        $caseResult = & $lifecycleScript `
            -LogDirectory $caseVariant.logs.ToUpperInvariant() `
            -ArchiveDirectory $caseVariant.archive.ToUpperInvariant() `
            -PolicyPath $caseVariant.policy `
            -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-10T00:00:00Z')) `
            -Execute
        Assert-Condition ($caseResult.state -ceq 'COMPLETE') `
            'Casing alternativo criou identidade divergente no Windows.'
    }

    $futureClock = New-TestArea 'future-clock'
    Write-EnabledPolicy $futureClock.policy $futureClock.logs $futureClock.archive $false `
        -PolicyVersion 'synthetic-future-v1'
    $futureLog = Join-Path $futureClock.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $futureLog 'future' ([DateTime]'2026-01-01T00:00:00Z')
    Assert-Throws {
        Invoke-Lifecycle $futureClock -Execute `
            -EvaluationTimeUtc ([DateTimeOffset]::UtcNow.AddMinutes(5))
    } 'Relógio futuro foi aceito em execução com efeitos.'
    Assert-Condition (Test-Path -LiteralPath $futureLog -PathType Leaf) `
        'Falha de relógio futuro alterou source.'

    $invalidType = New-TestArea 'invalid-type'
    Write-Utf8File $invalidType.policy '{"manifestVersion":"1","enabled":"true"}'
    Assert-Throws { Invoke-Lifecycle $invalidType -Execute } `
        'Tipos JSON coercíveis foram aceitos pela política.'

    $invalidMarkerState = New-TestArea 'invalid-marker-state'
    Write-EnabledPolicy $invalidMarkerState.policy $invalidMarkerState.logs `
        $invalidMarkerState.archive $false -PolicyVersion 'synthetic-marker-case-v1'
    $invalidStatePolicy = Get-Content -LiteralPath $invalidMarkerState.policy -Raw |
        ConvertFrom-Json
    $invalidStateMarker = [ordered]@{
        manifestVersion = 1
        state = 'complete'
        receiptId = 'a' * 64
        scopeFingerprint = $invalidStatePolicy.scopeFingerprint
        policyFingerprint = $invalidStatePolicy.policyFingerprint
        archiveRoot = [IO.Path]::GetFullPath($invalidMarkerState.archive)
        evaluationTimeUtcTicks = ([DateTime]'2026-01-10T00:00:00Z').Ticks
        completionSha256 = 'b' * 64
    }
    Write-Utf8File (Join-Path $invalidMarkerState.logs `
            '.etl-v2-log-lifecycle.operation.json') `
        ($invalidStateMarker | ConvertTo-Json -Compress)
    Assert-Throws { Invoke-Lifecycle $invalidMarkerState } `
        'Casing inválido do estado do operation marker foi aceito.'

    $fileSystemRoot = [IO.Path]::GetPathRoot($normal.logs)
    Assert-Throws {
        & $lifecycleScript -LogDirectory $fileSystemRoot `
            -ArchiveDirectory $invalidType.archive -PolicyPath $invalidType.policy `
            -EvaluationTimeUtc ([DateTimeOffset]::Parse('2026-01-10T00:00:00Z'))
    } 'Raiz de filesystem foi aceita como diretório de logs.'

    $lockFile = @(Get-ChildItem -LiteralPath $normal.archive -Recurse `
            -Filter '.etl-v2-log-lifecycle.lock' -File)
    Assert-Condition ($lockFile.Count -eq 1) 'File lock do escopo não foi materializado.'
    $heldLock = [IO.FileStream]::new(
        $lockFile[0].FullName,
        [IO.FileMode]::Open,
        [IO.FileAccess]::ReadWrite,
        [IO.FileShare]::None
    )
    try {
        Assert-Throws { Invoke-Lifecycle $normal -Execute } `
            'Execução concorrente ignorou o file lock do escopo.'
    } finally {
        $heldLock.Dispose()
    }

    $sharedSourceA = New-TestArea 'shared-source-a'
    $sharedSourceB = New-TestArea 'shared-source-b'
    $sharedSourceB = [pscustomobject]@{
        root = $sharedSourceB.root
        logs = $sharedSourceA.logs
        archive = $sharedSourceB.archive
        policy = $sharedSourceB.policy
    }
    Write-EnabledPolicy $sharedSourceA.policy $sharedSourceA.logs `
        $sharedSourceA.archive $false -PolicyVersion 'synthetic-source-a-v1'
    Write-EnabledPolicy $sharedSourceB.policy $sharedSourceB.logs `
        $sharedSourceB.archive $false -PolicyVersion 'synthetic-source-b-v1'
    $sourceMutexName = Get-SourceMutexName $sharedSourceA.logs
    $sourceLockJob = Start-Job -ScriptBlock {
        param([string]$MutexName)

        $mutex = [Threading.Mutex]::new($false, $MutexName)
        try {
            $null = $mutex.WaitOne()
            Write-Output 'READY'
            Start-Sleep -Seconds 30
        } finally {
            try { $mutex.ReleaseMutex() } catch {}
            $mutex.Dispose()
        }
    } -ArgumentList $sourceMutexName
    try {
        $sourceLockReady = $false
        $sourceLockDeadline = [DateTime]::UtcNow.AddSeconds(8)
        while (-not $sourceLockReady -and [DateTime]::UtcNow -lt $sourceLockDeadline) {
            $sourceLockReady = @(
                Receive-Job -Job $sourceLockJob -Keep -ErrorAction SilentlyContinue
            ) -ccontains 'READY'
            if (-not $sourceLockReady) {
                Start-Sleep -Milliseconds 50
            }
        }
        Assert-Condition $sourceLockReady 'Holder do source lock não ficou pronto.'
        Assert-Throws { Invoke-Lifecycle $sharedSourceB -Execute } `
            'Archives distintos contornaram o lock da mesma origem de logs.'
    } finally {
        Stop-Job -Job $sourceLockJob -ErrorAction SilentlyContinue
        Remove-Job -Job $sourceLockJob -Force -ErrorAction SilentlyContinue
    }

    $sharedCrashLog = Join-Path $sharedSourceA.logs `
        'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $sharedCrashLog 'shared-crash' `
        ([DateTime]'2026-01-01T00:00:00Z')
    $null = Invoke-Lifecycle $sharedSourceA -Execute
    $sharedPermitPath = @(Get-ChildItem -LiteralPath $sharedSourceA.archive `
            -Recurse -Filter 'log-lifecycle-*.permit.json' -File)[0].FullName
    $sharedCompletionPath = @(Get-ChildItem -LiteralPath $sharedSourceA.archive `
            -Recurse -Filter 'log-lifecycle-*.complete.json' -File)[0].FullName
    $sharedPermit = Get-Content -LiteralPath $sharedPermitPath -Raw | ConvertFrom-Json
    Remove-Item -LiteralPath $sharedCompletionPath
    $sharedMarkerPath = Join-Path $sharedSourceA.logs `
        '.etl-v2-log-lifecycle.operation.json'
    $sharedActiveMarker = [ordered]@{
        manifestVersion = 1
        state = 'ACTIVE'
        receiptId = $sharedPermit.receiptId
        scopeFingerprint = $sharedPermit.scopeFingerprint
        policyFingerprint = $sharedPermit.policyFingerprint
        archiveRoot = [IO.Path]::GetFullPath($sharedSourceA.archive)
        evaluationTimeUtcTicks = $sharedPermit.evaluationTimeUtcTicks
        completionSha256 = $null
    }
    Write-Utf8File $sharedMarkerPath ($sharedActiveMarker | ConvertTo-Json -Compress)
    Write-LogFixture $sharedCrashLog 'shared-crash' `
        ([DateTime]'2026-01-01T00:00:00Z')
    Assert-Throws { Invoke-Lifecycle $sharedSourceB } `
        'Dry-run ignorou operação incompleta da mesma origem.'
    Assert-Condition (@(Get-ChildItem -LiteralPath $sharedSourceB.archive `
                -Recurse -Force).Count -eq 0) `
        'Dry-run bloqueado criou artefato no archive alternativo.'
    Assert-Throws { Invoke-Lifecycle $sharedSourceB -Execute } `
        'Archive alternativo ignorou operação incompleta da mesma origem.'
    Assert-Condition (Test-Path -LiteralPath $sharedCrashLog -PathType Leaf) `
        'Archive alternativo alterou source de operação incompleta.'
    $sharedResumed = Invoke-Lifecycle $sharedSourceA -Execute
    Assert-Condition ($sharedResumed.state -ceq 'COMPLETE' `
            -and -not $sharedResumed.exactRetry `
            -and -not (Test-Path -LiteralPath $sharedCrashLog)) `
        'Operação original não retomou após fence entre archives.'

    $disabled = New-TestArea 'disabled'
    Write-Utf8File $disabled.policy '{"manifestVersion":1,"enabled":false}'
    $disabledLog = Join-Path $disabled.logs 'etl-dataexport-v2.2026-01-01.log'
    Write-LogFixture $disabledLog 'disabled' ([DateTime]'2026-01-01T00:00:00Z')
    $disabledResult = Invoke-Lifecycle $disabled -Execute
    Assert-Condition ($disabledResult.state -ceq 'DISABLED' `
            -and (Test-Path -LiteralPath $disabledLog -PathType Leaf)) `
        'Política desabilitada produziu efeito.'
    Assert-FreshInertAreaUnchanged $disabled $disabledLog `
        'Política disabled em origem nova'
} finally {
    if (Test-Path -LiteralPath $testDirectory) {
        $resolvedTestDirectory = [IO.Path]::GetFullPath((Resolve-Path $testDirectory).Path)
        $expectedPrefix = [IO.Path]::GetFullPath(
            (Join-Path $temporaryRoot 'etl-v2-log-lifecycle-')
        )
        if (-not $resolvedTestDirectory.StartsWith(
                $expectedPrefix,
                [StringComparison]::OrdinalIgnoreCase
            )) {
            throw 'Diretório temporário inesperado; cleanup recusado.'
        }
        Remove-Item -LiteralPath $resolvedTestDirectory -Recurse -Force
    }
}

Write-Output 'Lifecycle governado de logs validado com fixtures sintéticas.'

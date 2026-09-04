[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDirectory = [IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ('etl-v2-lifecycle-lock-' + [Guid]::NewGuid().ToString('N')))
)
$holderProcess = $null

function Read-ProbeOutput {
    param([string[]]$Paths)

    return ($Paths | ForEach-Object {
        if (Test-Path -LiteralPath $_ -PathType Leaf) {
            Get-Content -LiteralPath $_ -Raw
        }
    }) -join "`n"
}

$lockSql = @'
DECLARE @lock_result INT;
EXEC @lock_result = sys.sp_getapplock
    @Resource = N'V2_STAGING_LIFECYCLE',
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
'@
$holderLockSql = $lockSql.Replace('@LockTimeout = 0', '@LockTimeout = 5000')

$holderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51598, N'O probe concorrente aceita somente o alvo local autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$holderLockSql
IF @lock_result < 0 THROW 51598, N'Holder não adquiriu o lock de lifecycle.', 1;
RAISERROR(N'LIFECYCLE_LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:10';
ROLLBACK TRANSACTION;
PRINT N'LIFECYCLE_LOCK_RELEASED';
"@

$contenderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51598, N'O probe concorrente aceita somente o alvo local autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockSql
IF @lock_result >= 0
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'LIFECYCLE_LOCK_ACQUIRED_EARLY';
END
ELSE
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'LIFECYCLE_CONTENTION_CONFIRMED';
END;
"@

$reacquireSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51598, N'O probe concorrente aceita somente o alvo local autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockSql
IF @lock_result < 0 THROW 51598, N'O rollback não liberou o lock de lifecycle.', 1;
ROLLBACK TRANSACTION;
PRINT N'LOCK_REACQUIRED_AFTER_ROLLBACK';
"@

try {
    $null = New-Item -ItemType Directory -Path $probeDirectory
    $holderPath = Join-Path $probeDirectory 'holder.sql'
    $contenderPath = Join-Path $probeDirectory 'contender.sql'
    $reacquirePath = Join-Path $probeDirectory 'reacquire.sql'
    $holderOutputPath = Join-Path $probeDirectory 'holder.out'
    $holderErrorPath = Join-Path $probeDirectory 'holder.err'

    [IO.File]::WriteAllText($holderPath, $holderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($contenderPath, $contenderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($reacquirePath, $reacquireSql, [Text.UTF8Encoding]::new($false))

    $holderProcess = Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru `
        -ArgumentList @(
            '-S', 'localhost', '-C', '-E', '-f', '65001', '-d', $targetDatabase,
            '-i', $holderPath, '-b', '-h', '-1', '-W'
        ) `
        -RedirectStandardOutput $holderOutputPath -RedirectStandardError $holderErrorPath

    $contentionConfirmed = $false
    $readyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(8)
    $contenderOutput = @()
    while (-not $contentionConfirmed -and [DateTimeOffset]::UtcNow -lt $readyDeadline) {
        if ($holderProcess.HasExited) {
            $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
            throw "A sessão holder terminou antes da contenção. $holderOutput"
        }
        $contenderOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
                -i $contenderPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0) {
            throw "O contender falhou. $($contenderOutput -join ' ')"
        }
        $contentionConfirmed = ($contenderOutput -join "`n").IndexOf(
            'LIFECYCLE_CONTENTION_CONFIRMED',
            [StringComparison]::Ordinal
        ) -ge 0
        if (-not $contentionConfirmed) {
            Start-Sleep -Milliseconds 100
        }
    }
    if (-not $contentionConfirmed) {
        throw "A segunda sessão não confirmou contenção do lifecycle. $($contenderOutput -join ' ')"
    }

    if (-not $holderProcess.WaitForExit(15000)) {
        throw 'A sessão holder do lifecycle não terminou no prazo.'
    }
    $holderProcess.WaitForExit()
    $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
    $holderExitCode = $holderProcess.ExitCode
    if (($null -ne $holderExitCode -and $holderExitCode -ne 0) `
            -or $holderOutput.IndexOf('LIFECYCLE_LOCK_RELEASED', [StringComparison]::Ordinal) -lt 0) {
        throw "A sessão holder do lifecycle falhou (exit=$holderExitCode). $holderOutput"
    }

    $reacquireOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $reacquirePath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($reacquireOutput -join "`n").IndexOf(
                'LOCK_REACQUIRED_AFTER_ROLLBACK', [StringComparison]::Ordinal) -lt 0) {
        throw "O lock de lifecycle não foi readquirido. $($reacquireOutput -join ' ')"
    }
} finally {
    if ($null -ne $holderProcess -and -not $holderProcess.HasExited) {
        $holderProcess.Kill()
        $holderProcess.WaitForExit()
    }
    if (Test-Path -LiteralPath $probeDirectory) {
        $resolvedProbeDirectory = [IO.Path]::GetFullPath((Resolve-Path $probeDirectory).Path)
        $expectedPrefix = [IO.Path]::GetFullPath((Join-Path $temporaryRoot 'etl-v2-lifecycle-lock-'))
        if (-not $resolvedProbeDirectory.StartsWith(
                $expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Diretório temporário inesperado; cleanup recusado: $resolvedProbeDirectory"
        }
        Remove-Item -LiteralPath $resolvedProbeDirectory -Recurse -Force
    }
}

Write-Output 'Concorrência do lock de lifecycle comprovada e revertida integralmente.'

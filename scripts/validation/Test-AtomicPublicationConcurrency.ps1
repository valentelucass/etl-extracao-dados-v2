[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDirectory = [IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ('etl-v2-atomic-lock-' + [Guid]::NewGuid().ToString('N')))
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

$lockDeclaration = @'
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_CONCURRENCY_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_CONCURRENCY_TENANT';
DECLARE @entity_name NVARCHAR(128) = N'SYNTHETIC_CONCURRENCY_ENTITY';
DECLARE @lock_payload NVARCHAR(2000) = CONCAT(
    DATALENGTH(@environment_name), N':', @environment_name, N'|',
    DATALENGTH(@source_instance), N':', @source_instance, N'|',
    DATALENGTH(@tenant_scope), N':', @tenant_scope, N'|',
    DATALENGTH(@entity_name), N':', @entity_name
);
DECLARE @lock_resource NVARCHAR(255) = CONCAT(
    N'V2_APPLY_', CONVERT(NVARCHAR(64), HASHBYTES('SHA2_256', @lock_payload), 2)
);
DECLARE @lock_result INT;
'@

$holderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51462, N'O probe concorrente só aceita o banco local V2 autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51463, N'A sessão holder não adquiriu o lock transacional.', 1;
RAISERROR(N'LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:15';
ROLLBACK TRANSACTION;
PRINT N'LOCK_RELEASED';
"@

$contenderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51464, N'O probe concorrente só aceita o banco local V2 autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result >= 0
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'LOCK_ACQUIRED_BEFORE_HOLDER';
END
ELSE
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'CONTENTION_CONFIRMED';
END;
"@

$reacquireSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51466, N'O probe concorrente só aceita o banco local V2 autorizado.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51467, N'O rollback da sessão holder não liberou o lock.', 1;
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

    Start-Sleep -Milliseconds 1000
    $contentionConfirmed = $false
    $contenderTranscript = @()
    for ($attempt = 0; $attempt -lt 20 -and -not $contentionConfirmed; $attempt++) {
        if ($holderProcess.HasExited) {
            break
        }
        $contenderOutput = @(
            & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
                -i $contenderPath -b -h -1 -W
        )
        if ($LASTEXITCODE -ne 0) {
            throw "A sessão contender falhou. $($contenderOutput -join ' ')"
        }
        $contenderText = $contenderOutput -join "`n"
        $contenderTranscript += $contenderText
        $contentionConfirmed = $contenderText.IndexOf(
            'CONTENTION_CONFIRMED', [StringComparison]::Ordinal
        ) -ge 0
        if (-not $contentionConfirmed) {
            Start-Sleep -Milliseconds 100
        }
    }
    if (-not $contentionConfirmed) {
        $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
        throw "A segunda sessão não confirmou contenção exclusiva. $holderOutput $($contenderTranscript -join ' ')"
    }

    if (-not $holderProcess.WaitForExit(20000)) {
        throw 'A sessão holder não terminou no prazo do probe concorrente.'
    }
    $holderProcess.WaitForExit()
    $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
    $holderExitCode = $holderProcess.ExitCode
    if (($null -ne $holderExitCode -and $holderExitCode -ne 0) `
            -or $holderOutput.IndexOf('LOCK_RELEASED', [StringComparison]::Ordinal) -lt 0) {
        throw "A sessão holder falhou antes do rollback (exit=$holderExitCode). $holderOutput"
    }

    $reacquireOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $reacquirePath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($reacquireOutput -join "`n").IndexOf(
                'LOCK_REACQUIRED_AFTER_ROLLBACK', [StringComparison]::Ordinal) -lt 0) {
        throw "O lock não foi readquirido depois do rollback. $($reacquireOutput -join ' ')"
    }
} finally {
    if ($null -ne $holderProcess -and -not $holderProcess.HasExited) {
        $holderProcess.Kill()
        $holderProcess.WaitForExit()
    }
    if (Test-Path -LiteralPath $probeDirectory) {
        $resolvedProbeDirectory = [IO.Path]::GetFullPath((Resolve-Path $probeDirectory).Path)
        $expectedPrefix = [IO.Path]::GetFullPath(
            (Join-Path $temporaryRoot 'etl-v2-atomic-lock-')
        )
        if (-not $resolvedProbeDirectory.StartsWith(
                $expectedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Diretório temporário inesperado; cleanup recusado: $resolvedProbeDirectory"
        }
        Remove-Item -LiteralPath $resolvedProbeDirectory -Recurse -Force
    }
}

Write-Output 'Concorrência do lock atômico comprovada em sessões distintas e revertida integralmente.'

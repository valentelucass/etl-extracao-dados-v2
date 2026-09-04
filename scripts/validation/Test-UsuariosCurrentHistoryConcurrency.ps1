[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$kernelPath = Join-Path $repositoryRoot `
    'database\migrations\V004__create_staging_promotion_kernel.sql'
$usuariosPath = Join-Path $repositoryRoot `
    'database\migrations\V007__create_usuarios_current_history.sql'
$kernel = Get-Content -LiteralPath $kernelPath -Raw
$usuarios = Get-Content -LiteralPath $usuariosPath -Raw

$commonApplyMatch = [Text.RegularExpressions.Regex]::Match(
    $kernel,
    '(?ms)^CREATE OR ALTER PROCEDURE core\.usp_apply_reconcile_publish_execution\b.*?^GO\s*$'
)
if (-not $commonApplyMatch.Success) {
    throw 'O entrypoint comum de aplicação não pôde ser isolado para o probe concorrente.'
}
$commonApplyDefinition = $commonApplyMatch.Value
$formulaStartMarker = 'DECLARE @lock_payload NVARCHAR(2000) = CONCAT('
$formulaEndMarker = 'DECLARE @application_lock_result INT;'
$formulaStart = $commonApplyDefinition.IndexOf($formulaStartMarker, [StringComparison]::Ordinal)
$formulaEnd = $commonApplyDefinition.IndexOf($formulaEndMarker, [StringComparison]::Ordinal)
if ($formulaStart -lt 0 -or $formulaEnd -le $formulaStart) {
    throw 'A fórmula canônica do namespace de aplicação não pôde ser isolada.'
}
$canonicalLockFormula = $commonApplyDefinition.Substring(
    $formulaStart,
    $formulaEnd - $formulaStart
).Trim()

foreach ($requiredFormulaToken in @(
        'DATALENGTH(@environment_name)',
        'DATALENGTH(@source_instance)',
        'DATALENGTH(@tenant_scope)',
        'DATALENGTH(@entity_name)',
        "N'V2_APPLY_'"
    )) {
    if ($canonicalLockFormula.IndexOf(
            $requiredFormulaToken,
            [StringComparison]::Ordinal
        ) -lt 0) {
        throw "A fórmula canônica do lock não contém $requiredFormulaToken."
    }
}
foreach ($requiredKernelToken in @(
        'sys.sp_getapplock',
        "@LockOwner = 'Transaction'"
    )) {
    if ($commonApplyDefinition.IndexOf(
            $requiredKernelToken,
            [StringComparison]::Ordinal
        ) -lt 0) {
        throw "O lock comum não contém $requiredKernelToken."
    }
}
foreach ($requiredUsuariosToken in @(
        'BEGIN TRANSACTION;',
        'EXEC core.usp_apply_reconcile_publish_execution',
        'FROM stg.usuario_record WITH (UPDLOCK, HOLDLOCK)',
        'INDEX(UQ_core_usuario_source), FORCESEEK',
        'UQ_core_usuario_source UNIQUE ('
    )) {
    if ($usuarios.IndexOf($requiredUsuariosToken, [StringComparison]::Ordinal) -lt 0) {
        throw "A vertical de Usuários não contém a guarda concorrente $requiredUsuariosToken."
    }
}

$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDirectory = [IO.Path]::GetFullPath(
    (Join-Path $temporaryRoot ('etl-v2-usuarios-lock-' + [Guid]::NewGuid().ToString('N')))
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

$lockInputs = @'
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_USERS_CONCURRENCY_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_USERS_CONCURRENCY_TENANT';
DECLARE @entity_name NVARCHAR(128) = N'usuarios';
'@
$lockDeclaration = $lockInputs + "`r`n" + $canonicalLockFormula `
    + "`r`nDECLARE @lock_result INT;"
$otherEnvironmentLockDeclaration = $lockDeclaration.Replace(
    "N'LOCAL_SHADOW'",
    "N'OTHER_SHADOW'"
)

$holderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51840, N'O probe concorrente de Usuários só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$lockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 5000,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51841, N'A sessão holder não adquiriu o namespace de Usuários.', 1;
RAISERROR(N'USUARIOS_LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:12';
ROLLBACK TRANSACTION;
PRINT N'USUARIOS_LOCK_RELEASED';
"@

$contenderSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51842, N'O probe concorrente de Usuários só aceita o banco local V2.', 1;
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
IF @lock_result = -1
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'USUARIOS_CONTENTION_CONFIRMED';
END
ELSE IF @lock_result >= 0
BEGIN
    ROLLBACK TRANSACTION;
    PRINT N'USUARIOS_LOCK_ACQUIRED_BEFORE_HOLDER';
END
ELSE
BEGIN
    ROLLBACK TRANSACTION;
    THROW 51847, N'O contender retornou estado de lock inesperado.', 1;
END;
"@

$otherEnvironmentSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51843, N'O probe concorrente de Usuários só aceita o banco local V2.', 1;
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
$otherEnvironmentLockDeclaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 0,
    @DbPrincipal = 'public';
IF @lock_result < 0
    THROW 51844, N'Outro environment sofreu contenção indevida.', 1;
ROLLBACK TRANSACTION;
PRINT N'USUARIOS_OTHER_ENVIRONMENT_ISOLATED';
"@

$reacquireSql = @"
:On Error exit
IF DB_NAME() <> N'$targetDatabase'
    THROW 51845, N'O probe concorrente de Usuários só aceita o banco local V2.', 1;
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
    THROW 51846, N'O rollback não liberou o namespace de Usuários.', 1;
ROLLBACK TRANSACTION;
PRINT N'USUARIOS_LOCK_REACQUIRED_AFTER_ROLLBACK';
"@

try {
    $null = New-Item -ItemType Directory -Path $probeDirectory
    $holderPath = Join-Path $probeDirectory 'holder.sql'
    $contenderPath = Join-Path $probeDirectory 'contender.sql'
    $otherEnvironmentPath = Join-Path $probeDirectory 'other-environment.sql'
    $reacquirePath = Join-Path $probeDirectory 'reacquire.sql'
    $holderOutputPath = Join-Path $probeDirectory 'holder.out'
    $holderErrorPath = Join-Path $probeDirectory 'holder.err'

    [IO.File]::WriteAllText($holderPath, $holderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($contenderPath, $contenderSql, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText(
        $otherEnvironmentPath,
        $otherEnvironmentSql,
        [Text.UTF8Encoding]::new($false)
    )
    [IO.File]::WriteAllText($reacquirePath, $reacquireSql, [Text.UTF8Encoding]::new($false))

    $holderProcess = Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru `
        -ArgumentList @(
            '-S', 'localhost', '-C', '-E', '-f', '65001', '-d', $targetDatabase,
            '-i', $holderPath, '-b', '-h', '-1', '-W'
        ) `
        -RedirectStandardOutput $holderOutputPath -RedirectStandardError $holderErrorPath

    # A própria tentativa não bloqueante é o handshake entre as sessões:
    # enquanto o holder ainda inicia, ela adquire e reverte; quando falha,
    # comprova de forma observável que o mesmo namespace já está retido.
    $readyDeadline = [DateTimeOffset]::UtcNow.AddSeconds(8)
    $contentionConfirmed = $false
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
            throw "O contender de Usuários falhou. $($contenderOutput -join ' ')"
        }
        $contentionConfirmed = ($contenderOutput -join "`n").IndexOf(
            'USUARIOS_CONTENTION_CONFIRMED',
            [StringComparison]::Ordinal
        ) -ge 0
        if (-not $contentionConfirmed) {
            Start-Sleep -Milliseconds 100
        }
    }
    if (-not $contentionConfirmed) {
        throw "A segunda sessão não confirmou contenção de Usuários. $($contenderOutput -join ' ')"
    }

    $otherEnvironmentOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $otherEnvironmentPath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($otherEnvironmentOutput -join "`n").IndexOf(
                'USUARIOS_OTHER_ENVIRONMENT_ISOLATED',
                [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O namespace de outro environment não ficou isolado. $($otherEnvironmentOutput -join ' ')"
    }

    if (-not $holderProcess.WaitForExit(18000)) {
        throw 'A sessão holder de Usuários não terminou no prazo.'
    }
    $holderProcess.WaitForExit()
    $holderOutput = Read-ProbeOutput @($holderOutputPath, $holderErrorPath)
    $holderExitCode = $holderProcess.ExitCode
    if (($null -ne $holderExitCode -and $holderExitCode -ne 0) `
            -or $holderOutput.IndexOf(
                'USUARIOS_LOCK_RELEASED',
                [StringComparison]::Ordinal
            ) -lt 0) {
        throw "A sessão holder de Usuários falhou (exit=$holderExitCode). $holderOutput"
    }

    $reacquireOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i $reacquirePath -b -h -1 -W
    )
    if ($LASTEXITCODE -ne 0 `
            -or ($reacquireOutput -join "`n").IndexOf(
                'USUARIOS_LOCK_REACQUIRED_AFTER_ROLLBACK',
                [StringComparison]::Ordinal
            ) -lt 0) {
        throw "O lock de Usuários não foi readquirido. $($reacquireOutput -join ' ')"
    }
} finally {
    if ($null -ne $holderProcess -and -not $holderProcess.HasExited) {
        $holderProcess.Kill()
        $holderProcess.WaitForExit()
    }
    if (Test-Path -LiteralPath $probeDirectory) {
        $resolvedProbeDirectory = [IO.Path]::GetFullPath((Resolve-Path $probeDirectory).Path)
        $expectedPrefix = [IO.Path]::GetFullPath((Join-Path $temporaryRoot 'etl-v2-usuarios-lock-'))
        if (-not $resolvedProbeDirectory.StartsWith(
                $expectedPrefix,
                [StringComparison]::OrdinalIgnoreCase
            )) {
            throw "Diretório temporário inesperado; cleanup recusado: $resolvedProbeDirectory"
        }
        Remove-Item -LiteralPath $resolvedProbeDirectory -Recurse -Force
    }
}

Write-Output 'Concorrência do namespace de Usuários comprovada em duas sessões, isolada por environment e revertida.'

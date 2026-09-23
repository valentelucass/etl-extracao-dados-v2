#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$kernel = Get-Content -LiteralPath (Join-Path $root 'database\migrations\V004__create_staging_promotion_kernel.sql') -Raw
$cotacoes = Get-Content -LiteralPath (Join-Path $root 'database\migrations\V011__create_cotacoes_shadow_vertical.sql') -Raw
$applyMatch = [Text.RegularExpressions.Regex]::Match(
    $kernel,
    '(?ms)^CREATE OR ALTER PROCEDURE core\.usp_apply_reconcile_publish_execution\b.*?^GO\s*$'
)
if (-not $applyMatch.Success) { throw 'O entrypoint comum de aplicação não pôde ser isolado.' }
$apply = $applyMatch.Value
$formulaStart = $apply.IndexOf('DECLARE @lock_payload NVARCHAR(2000) = CONCAT(', [StringComparison]::Ordinal)
$formulaEnd = $apply.IndexOf('DECLARE @application_lock_result INT;', [StringComparison]::Ordinal)
if ($formulaStart -lt 0 -or $formulaEnd -le $formulaStart) {
    throw 'A fórmula canônica do namespace de aplicação não pôde ser isolada.'
}
$formula = $apply.Substring($formulaStart, $formulaEnd - $formulaStart).Trim()
foreach ($token in @(
        'DATALENGTH(@environment_name)', 'DATALENGTH(@source_instance)',
        'DATALENGTH(@tenant_scope)', 'DATALENGTH(@entity_name)', "N'V2_APPLY_'",
        'sys.sp_getapplock', "@LockOwner = 'Transaction'")) {
    if ($apply.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "O protocolo comum não contém $token."
    }
}
foreach ($token in @(
        'BEGIN TRANSACTION;', 'EXEC core.usp_apply_reconcile_publish_execution',
        'WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_cotacao_source))',
        'CONSTRAINT UQ_core_cotacao_source UNIQUE(', 'current_record.freshness_at_utc=typed.freshness_at_utc')) {
    if ($cotacoes.IndexOf($token, [StringComparison]::Ordinal) -lt 0) {
        throw "A vertical Cotações não contém a guarda concorrente $token."
    }
}

$target = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDirectory = Join-Path $temporaryRoot ('etl-v2-cotacoes-lock-' + [Guid]::NewGuid().ToString('N'))
$holder = $null

function Invoke-Probe {
    param([string]$Path)
    $output = @(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $target -i $Path -b -h -1 -W)
    if ($LASTEXITCODE -ne 0) { throw "Probe SQLCMD falhou: $($output -join ' ')" }
    return ($output -join "`n")
}

$inputs = @'
DECLARE @environment_name NVARCHAR(32) = N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128) = N'SYNTHETIC_COTACOES_CONCURRENCY_SOURCE';
DECLARE @tenant_scope NVARCHAR(128) = N'SYNTHETIC_COTACOES_CONCURRENCY_TENANT';
DECLARE @entity_name NVARCHAR(128) = N'cotacoes';
'@
$declaration = $inputs + "`r`n" + $formula + "`r`nDECLARE @lock_result INT;"
$otherEnvironment = $declaration.Replace("N'LOCAL_SHADOW'", "N'OTHER_SHADOW'")
function New-LockSql {
    param([string]$Declaration, [int]$Timeout, [string]$Success, [string]$Failure)
    return @"
:On Error exit
IF DB_NAME() <> N'$target' THROW 51980, N'Probe concorrente de Cotações fora do alvo local V2.', 1;
SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
$Declaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = $Timeout,
    @DbPrincipal = 'public';
IF @lock_result < 0 BEGIN ROLLBACK TRANSACTION; PRINT N'$Failure'; END
ELSE BEGIN ROLLBACK TRANSACTION; PRINT N'$Success'; END;
"@
}
$holderSql = @"
:On Error exit
IF DB_NAME() <> N'$target' THROW 51980, N'Probe concorrente de Cotações fora do alvo local V2.', 1;
SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
$declaration
EXEC @lock_result = sys.sp_getapplock
    @Resource = @lock_resource,
    @LockMode = 'Exclusive',
    @LockOwner = 'Transaction',
    @LockTimeout = 5000,
    @DbPrincipal = 'public';
IF @lock_result < 0 THROW 51981, N'Holder não adquiriu o namespace de Cotações.', 1;
RAISERROR(N'COTACOES_LOCK_HELD', 10, 1) WITH NOWAIT;
WAITFOR DELAY '00:00:06';
ROLLBACK TRANSACTION; PRINT N'COTACOES_LOCK_RELEASED';
"@
$contenderSql = New-LockSql $declaration 0 'COTACOES_LOCK_ACQUIRED_BEFORE_HOLDER' 'COTACOES_CONTENTION_CONFIRMED'
$isolatedSql = New-LockSql $otherEnvironment 0 'COTACOES_OTHER_ENVIRONMENT_ISOLATED' 'COTACOES_OTHER_ENVIRONMENT_BLOCKED'
$reacquireSql = New-LockSql $declaration 0 'COTACOES_LOCK_REACQUIRED_AFTER_ROLLBACK' 'COTACOES_LOCK_NOT_RELEASED'

try {
    $null = New-Item -ItemType Directory -Path $probeDirectory
    $paths = @{}
    foreach ($entry in @(@('holder', $holderSql), @('contender', $contenderSql), @('isolated', $isolatedSql), @('reacquire', $reacquireSql))) {
        $path = Join-Path $probeDirectory ($entry[0] + '.sql')
        [IO.File]::WriteAllText($path, $entry[1], [Text.UTF8Encoding]::new($false))
        $paths[$entry[0]] = $path
    }
    $holderOut = Join-Path $probeDirectory 'holder.out'
    $holderErr = Join-Path $probeDirectory 'holder.err'
    $holder = Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru -ArgumentList @(
        '-S','localhost','-C','-E','-f','65001','-d',$target,'-i',$paths.holder,'-b','-h','-1','-W'
    ) -RedirectStandardOutput $holderOut -RedirectStandardError $holderErr

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(8)
    $contended = $false
    while (-not $contended -and [DateTimeOffset]::UtcNow -lt $deadline) {
        if ($holder.HasExited) { throw 'A sessão holder terminou antes da contenção.' }
        $output = Invoke-Probe $paths.contender
        $contended = $output.Contains('COTACOES_CONTENTION_CONFIRMED')
        if (-not $contended) { Start-Sleep -Milliseconds 100 }
    }
    if (-not $contended) { throw 'A segunda sessão não confirmou contenção de Cotações.' }
    if (-not (Invoke-Probe $paths.isolated).Contains('COTACOES_OTHER_ENVIRONMENT_ISOLATED')) {
        throw 'O namespace de outro environment sofreu contenção indevida.'
    }
    if (-not $holder.WaitForExit(10000)) { throw 'A sessão holder não terminou no prazo.' }
    $holderText = ((Get-Content -LiteralPath $holderOut -Raw), (Get-Content -LiteralPath $holderErr -Raw)) -join "`n"
    if ($holder.ExitCode -ne 0 -or -not $holderText.Contains('COTACOES_LOCK_RELEASED')) {
        throw "A sessão holder falhou: $holderText"
    }
    if (-not (Invoke-Probe $paths.reacquire).Contains('COTACOES_LOCK_REACQUIRED_AFTER_ROLLBACK')) {
        throw 'O rollback não liberou o namespace de Cotações.'
    }
} finally {
    if ($null -ne $holder -and -not $holder.HasExited) { $holder.Kill(); $holder.WaitForExit() }
    if (Test-Path -LiteralPath $probeDirectory) { Remove-Item -LiteralPath $probeDirectory -Recurse -Force }
}

Write-Output 'Concorrência de Cotações comprovada em duas sessões, isolada por environment e revertida.'

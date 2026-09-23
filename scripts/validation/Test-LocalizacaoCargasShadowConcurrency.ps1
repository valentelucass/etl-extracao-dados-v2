#Requires -Version 7.0
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$kernel = Get-Content -LiteralPath (Join-Path $root 'database\migrations\V004__create_staging_promotion_kernel.sql') -Raw
$localizacao = Get-Content -LiteralPath (Join-Path $root 'database\migrations\V014__create_localizacao_cargas_shadow_vertical.sql') -Raw
$apply = [regex]::Match($kernel,'(?ms)^CREATE OR ALTER PROCEDURE core\.usp_apply_reconcile_publish_execution\b.*?^GO\s*$')
if (-not $apply.Success) { throw 'Entrypoint comum de aplicação não pôde ser isolado.' }
$start = $apply.Value.IndexOf('DECLARE @lock_payload NVARCHAR(2000) = CONCAT(',[StringComparison]::Ordinal)
$end = $apply.Value.IndexOf('DECLARE @application_lock_result INT;',[StringComparison]::Ordinal)
if ($start -lt 0 -or $end -le $start) { throw 'Fórmula canônica do applock não encontrada.' }
$formula = $apply.Value.Substring($start,$end-$start).Trim()
foreach ($token in @('BEGIN TRANSACTION;','EXEC core.usp_apply_reconcile_publish_execution',
 'WITH(UPDLOCK,HOLDLOCK,INDEX(UQ_core_localizacao_cargas_source))',
 'CONSTRAINT UQ_core_localizacao_cargas_source UNIQUE(','EQUAL_FRESHNESS_CONFLICT')) {
    if (-not $localizacao.Contains($token)) { throw "Guarda concorrente ausente: $token" }
}
$target = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$verified = @(& $sqlcmd.Source -S localhost -C -E -f 65001 -d master -b -h -1 -W `
    -Q "SET NOCOUNT ON; SELECT DB_NAME(),CASE WHEN DB_ID(N'$target') IS NULL THEN N'MISSING' ELSE N'$target' END;")
if ($LASTEXITCODE -ne 0 -or ($verified -join ' ') -cnotmatch "master\s+$target") {
    throw 'Probe concorrente não confirmou o alvo local literal.'
}
$tmpRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$probeDir = Join-Path $tmpRoot ('etl-v2-localizacao-lock-' + [Guid]::NewGuid().ToString('N'))
$holder = $null
function Invoke-Probe([string]$path) {
    $output = @(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $target -i $path -b -h -1 -W)
    if ($LASTEXITCODE -ne 0) { throw "Probe SQLCMD falhou: $($output -join ' ')" }
    $output -join "`n"
}
$inputs = @'
DECLARE @environment_name NVARCHAR(32)=N'LOCAL_SHADOW';
DECLARE @source_instance NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_CONCURRENCY_SOURCE';
DECLARE @tenant_scope NVARCHAR(128)=N'SYNTHETIC_LOCALIZACAO_CONCURRENCY_TENANT';
DECLARE @entity_name NVARCHAR(128)=N'localizacao_cargas';
'@
$declaration = $inputs + "`r`n" + $formula + "`r`nDECLARE @lock_result INT;"
$other = $declaration.Replace("N'LOCAL_SHADOW'","N'OTHER_SHADOW'")
$otherTenant = $declaration.Replace(
    "N'SYNTHETIC_LOCALIZACAO_CONCURRENCY_TENANT'",
    "N'SYNTHETIC_LOCALIZACAO_CONCURRENCY_OTHER_TENANT'")
function New-LockSql([string]$decl,[int]$timeout,[string]$success,[string]$failure) {
@"
:On Error exit
IF DB_NAME()<>N'$target' THROW 52290,N'Probe Localização fora do alvo local.',1;
SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
$decl
EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode='Exclusive',
 @LockOwner='Transaction',@LockTimeout=$timeout,@DbPrincipal='public';
IF @lock_result<0 BEGIN ROLLBACK TRANSACTION; PRINT N'$failure'; END
ELSE BEGIN ROLLBACK TRANSACTION; PRINT N'$success'; END;
"@
}
$holderSql = @"
:On Error exit
IF DB_NAME()<>N'$target' THROW 52291,N'Holder Localização fora do alvo.',1;
SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;
$declaration
EXEC @lock_result=sys.sp_getapplock @Resource=@lock_resource,@LockMode='Exclusive',
 @LockOwner='Transaction',@LockTimeout=5000,@DbPrincipal='public';
IF @lock_result<0 THROW 52292,N'Holder não adquiriu lock.',1;
RAISERROR(N'LOCALIZACAO_CARGAS_LOCK_HELD',10,1) WITH NOWAIT;
WAITFOR DELAY '00:00:06'; ROLLBACK TRANSACTION;
PRINT N'LOCALIZACAO_CARGAS_LOCK_RELEASED';
"@
$sql = @{
 holder=$holderSql
 contender=(New-LockSql $declaration 0 'LOCALIZACAO_CARGAS_LOCK_ACQUIRED_EARLY' 'LOCALIZACAO_CARGAS_CONTENTION_CONFIRMED')
 isolated=(New-LockSql $other 0 'LOCALIZACAO_CARGAS_OTHER_ENVIRONMENT_ISOLATED' 'LOCALIZACAO_CARGAS_OTHER_ENVIRONMENT_BLOCKED')
 tenantIsolated=(New-LockSql $otherTenant 0 'LOCALIZACAO_CARGAS_OTHER_TENANT_ISOLATED' 'LOCALIZACAO_CARGAS_OTHER_TENANT_BLOCKED')
 reacquire=(New-LockSql $declaration 0 'LOCALIZACAO_CARGAS_LOCK_REACQUIRED_AFTER_ROLLBACK' 'LOCALIZACAO_CARGAS_LOCK_NOT_RELEASED')
}
try {
    $null = New-Item -ItemType Directory -Path $probeDir
    $paths = @{}
    foreach ($name in $sql.Keys) {
        $path = Join-Path $probeDir "$name.sql"
        [IO.File]::WriteAllText($path,$sql[$name],[Text.UTF8Encoding]::new($false))
        $paths[$name]=$path
    }
    $holderOut=Join-Path $probeDir 'holder.out'; $holderErr=Join-Path $probeDir 'holder.err'
    $holder=Start-Process -FilePath $sqlcmd.Source -WindowStyle Hidden -PassThru -ArgumentList @(
      '-S','localhost','-C','-E','-f','65001','-d',$target,'-i',$paths.holder,'-b','-h','-1','-W') `
      -RedirectStandardOutput $holderOut -RedirectStandardError $holderErr
    $deadline=[DateTimeOffset]::UtcNow.AddSeconds(8); $contended=$false
    while (-not $contended -and [DateTimeOffset]::UtcNow -lt $deadline) {
        if ($holder.HasExited) { throw 'Holder encerrou antes da contenção.' }
        $contended=(Invoke-Probe $paths.contender).Contains('LOCALIZACAO_CARGAS_CONTENTION_CONFIRMED')
        if (-not $contended) { Start-Sleep -Milliseconds 100 }
    }
    if (-not $contended) { throw 'Contenção não confirmada.' }
    if (-not (Invoke-Probe $paths.isolated).Contains('LOCALIZACAO_CARGAS_OTHER_ENVIRONMENT_ISOLATED')) {
        throw 'Isolamento por environment falhou.'
    }
    if (-not (Invoke-Probe $paths.tenantIsolated).Contains('LOCALIZACAO_CARGAS_OTHER_TENANT_ISOLATED')) {
        throw 'Isolamento por tenant falhou.'
    }
    if (-not $holder.WaitForExit(10000)) { throw 'Holder não terminou no prazo.' }
    $holderText=((Get-Content $holderOut -Raw),(Get-Content $holderErr -Raw))-join "`n"
    if ($holder.ExitCode -ne 0 -or -not $holderText.Contains('LOCALIZACAO_CARGAS_LOCK_RELEASED')) {
        throw "Holder falhou: $holderText"
    }
    if (-not (Invoke-Probe $paths.reacquire).Contains('LOCALIZACAO_CARGAS_LOCK_REACQUIRED_AFTER_ROLLBACK')) {
        throw 'Rollback não liberou o applock.'
    }
} finally {
    if ($null -ne $holder -and -not $holder.HasExited) { $holder.Kill(); $holder.WaitForExit() }
    if (Test-Path -LiteralPath $probeDir) {
        $resolved=[IO.Path]::GetFullPath((Resolve-Path $probeDir).Path)
        $prefix=[IO.Path]::GetFullPath((Join-Path $tmpRoot 'etl-v2-localizacao-lock-'))
        if (-not $resolved.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) {
            throw "Cleanup recusado: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
Write-Output 'Concorrência de Localização comprovada, isolada por environment/tenant e revertida.'

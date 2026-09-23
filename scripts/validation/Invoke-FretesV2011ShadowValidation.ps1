#Requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$ExecuteLocalShadow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
& (Join-Path $PSScriptRoot 'Test-FretesV2011ShadowVertical.ps1')

if (-not $ExecuteLocalShadow) {
    throw 'FRETES_SHADOW_VERTICAL_MISSING: prova SQL exige opt-in -ExecuteLocalShadow.'
}

$targetDatabase = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$targetVerification = @(
    & $sqlcmd.Source -S localhost -C -E -f 65001 -d master -b -h -1 -W `
        -Q "SET NOCOUNT ON; SELECT DB_NAME(), CASE WHEN DB_ID(N'$targetDatabase') IS NOT NULL THEN N'$targetDatabase' ELSE N'MISSING' END;"
)
if ($LASTEXITCODE -ne 0 -or ($targetVerification -join ' ') -cnotmatch "master\s+$targetDatabase") {
    throw 'O runner de Fretes não confirmou literalmente localhost/ETL_SISTEMA_V2_SHADOW via master.'
}

$stateQuery = @"
SET NOCOUNT ON;
SELECT CONCAT(
  COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions
            WHERE object_id=OBJECT_ID(N'core.frete',N'U') AND index_id IN(0,1)),0),
  N'|',
  COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions
            WHERE object_id=OBJECT_ID(N'stg.frete_record',N'U') AND index_id IN(0,1)),0),
  N'|',
  COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions
            WHERE object_id=OBJECT_ID(N'recon.frete_coleta_relation_candidate',N'U')
              AND index_id IN(0,1)),0)
);
"@
$before = (@(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -b -h -1 -W `
            -Q $stateQuery) -join '').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível obter o estado anterior da vertical.' }

$validationDirectory = Join-Path $root 'database\validation'
Push-Location $validationDirectory
try {
    $exerciseOutput = @(
        & $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase `
            -i '045_exercise_fretes_shadow_vertical_rollback.sql' -b
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Exercício rollback-only de Fretes falhou: $($exerciseOutput -join ' ')"
    }
} finally {
    Pop-Location
}

$after = (@(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $targetDatabase -b -h -1 -W `
            -Q $stateQuery) -join '').Trim()
if ($LASTEXITCODE -ne 0 -or $after -cne $before) {
    throw "Rollback de Fretes alterou o estado persistido: antes=$before; depois=$after"
}

& (Join-Path $PSScriptRoot 'Test-FretesShadowConcurrency.ps1')
Write-Output "PASS: Fretes V2-011 exercitada no shadow local e revertida; estado=$after."

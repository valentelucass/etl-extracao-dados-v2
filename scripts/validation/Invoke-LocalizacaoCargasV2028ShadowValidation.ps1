#Requires -Version 7.0
[CmdletBinding()]
param([switch]$ExecuteLocalShadow)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
& (Join-Path $PSScriptRoot 'Test-LocalizacaoCargasV2028ShadowVertical.ps1')
if (-not $ExecuteLocalShadow) {
    throw 'LOCALIZACAO_CARGAS_SHADOW_VERTICAL_MISSING: use -ExecuteLocalShadow para a prova SQL.'
}
$target = 'ETL_SISTEMA_V2_SHADOW'
$sqlcmd = Get-Command sqlcmd.exe -ErrorAction Stop
$verify = @(& $sqlcmd.Source -S localhost -C -E -f 65001 -d master -b -h -1 -W `
    -Q "SET NOCOUNT ON; SELECT DB_NAME(),CASE WHEN DB_ID(N'$target') IS NULL THEN N'MISSING' ELSE N'$target' END;")
if ($LASTEXITCODE -ne 0 -or ($verify -join ' ') -cnotmatch "master\s+$target") {
    throw 'Alvo literal localhost/ETL_SISTEMA_V2_SHADOW não confirmado via master.'
}
$state = @"
SET NOCOUNT ON;
SELECT CONCAT(
 COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions WHERE object_id=OBJECT_ID(N'core.localizacao_cargas',N'U') AND index_id IN(0,1)),0),N'|',
 COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions WHERE object_id=OBJECT_ID(N'stg.localizacao_carga_record',N'U') AND index_id IN(0,1)),0),N'|',
 COALESCE((SELECT SUM(CONVERT(BIGINT,[rows])) FROM sys.partitions WHERE object_id=OBJECT_ID(N'recon.localizacao_carga_root_presence_observation',N'U') AND index_id IN(0,1)),0));
"@
$before = (@(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $target -b -h -1 -W -Q $state) -join '').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Estado anterior não pôde ser lido.' }
Push-Location (Join-Path $root 'database\validation')
try {
    $out = @(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $target `
        -i '047_exercise_localizacao_cargas_shadow_vertical_rollback.sql' -b)
    if ($LASTEXITCODE -ne 0) { throw "SQL047 falhou: $($out -join ' ')" }
} finally { Pop-Location }
$after = (@(& $sqlcmd.Source -S localhost -C -E -f 65001 -d $target -b -h -1 -W -Q $state) -join '').Trim()
if ($LASTEXITCODE -ne 0 -or $after -cne $before) {
    throw "Rollback alterou o estado: antes=$before; depois=$after"
}
& (Join-Path $PSScriptRoot 'Test-LocalizacaoCargasShadowConcurrency.ps1')
Write-Output "PASS: Localização V2-028 exercitada e revertida; estado=$after."

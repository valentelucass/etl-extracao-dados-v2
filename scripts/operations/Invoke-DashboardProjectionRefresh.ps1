# Consome a cobertura confirmada em uma conexão própria, fora da transação de recebimento.
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][pscredential]$Credential,
    [switch]$Continuous,
    [ValidateRange(1,3600)][int]$PollSeconds = 60,
    [ValidateRange(1,120)][int]$CommandTimeout = 30
)
$ErrorActionPreference = 'Stop'
if ($Database -cne 'ETL_SISTEMA_V2' -and $Database -cnotmatch '^DASHBOARDS_DEV_AUDIT_[0-9]{8}$') {
    throw 'DASHBOARD_PROJECTION_DATABASE_REQUIRED'
}
Import-Module (Join-Path $PSScriptRoot '../../../.runtime/sql-server/SqlServerProjetos.psm1')
. (Join-Path $PSScriptRoot 'DashboardProjectionBatch.ps1')
do {
    $connection = New-ProjetosSqlConnection -Database $Database -Credential $Credential -ApplicationName 'ETL-Dashboard-Projection'
    try {
        $connection.Open()
        if ($connection.Database -cne $Database) { throw 'DASHBOARD_PROJECTION_DATABASE_MISMATCH' }
        $result = Invoke-DashboardProjectionSweep $connection $CommandTimeout
        Write-Output $result
        if (($result | Where-Object { $_.Failed -gt 0 }) -and -not $Continuous) { throw 'DASHBOARD_PROJECTION_PARTITIONS_PENDING' }
    } catch {
        $cause = $_.Exception
        while ($cause -and -not ($cause.PSObject.Properties['Number'])) { $cause = $cause.InnerException }
        if (-not $Continuous -or -not $cause -or $cause.Number -notin @(-2,53,64,233,1205,1222,10053,10054,10060)) { throw }
        Write-Warning ('Fila temporariamente indisponivel; nova consulta no proximo ciclo. SQL '+$cause.Number)
    } finally {
        $connection.Dispose()
    }
    if ($Continuous) { Start-Sleep -Seconds $PollSeconds }
} while ($Continuous)

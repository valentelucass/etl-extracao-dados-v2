[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ReportPath,
    [Parameter(Mandatory)][string]$CaptureDirectory
)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
Import-Module (Join-Path $PSScriptRoot '../../../.runtime/sql-server/SqlServerProjetos.psm1')
$evidence=& (Join-Path $PSScriptRoot 'Read-DashboardRefreshAudit.ps1') -ReportPath $ReportPath -CaptureDirectory $CaptureDirectory
if (!$evidence.passed) {throw 'O recebimento do lote ainda nao foi confirmado.'}
$credential=Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
try {
    $database='ETL_SISTEMA_V2'
    $partitions=@(& (Join-Path $PSScriptRoot 'Invoke-DashboardProjectionRefresh.ps1') -Database $database -Credential $credential -CommandTimeout 120)
    if ($partitions.Count -ne 4 -or @($partitions | Where-Object { $_.Failed -ne 0 }).Count) {
        throw 'Publicacao de particoes incompleta.'
    }
    $connection=New-ProjetosSqlConnection -Database $database -Credential $credential -ApplicationName 'ETL-Publicacao-Painel'
    try {
        $connection.Open()
        if ($connection.Database -cne $database) {throw 'Destino de publicacao incorreto.'}
        $command=$connection.CreateCommand()
        # O preparo troca varios relatorios em uma transacao. As leituras de controle usam seu proprio prazo.
        $command.CommandTimeout=[int]([TimeSpan]::FromHours(2)).TotalSeconds
        $command.CommandText='EXEC core.usp_dashboard_publicar_fatos_fretes'
        $command.CommandText+=' @audit=@receipt'
        $parameter=$command.Parameters.Add('@receipt',[Data.SqlDbType]::NVarChar,-1)
        $parameter.Value=ConvertTo-Json -InputObject $evidence -Depth 5 -Compress
        $auditId=$command.ExecuteScalar()
        if ([long]$auditId -lt 1) {throw 'Auditoria do recebimento sem confirmacao.'}
        $command.Parameters.Clear()
        $command.CommandTimeout=30
        $command.CommandText='SELECT COUNT_BIG(*) FROM pub.vw_dashboard_particoes_pendentes'
        if ([long]$command.ExecuteScalar() -ne 0) {throw 'A fila recebeu novos dados durante a publicacao.'}
        [pscustomobject]@{concluidaUtc=[DateTimeOffset]::UtcNow.ToString('o');particoes=$partitions;pendentes=0;ciclo=$evidence.cycle;auditoria=[long]$auditId}
    } finally {$connection.Dispose()}
} finally {$credential.Password.Dispose()}

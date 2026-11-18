# Cada chamada visita uma vez cada chave pendente. Uma falha não impede os outros dias.
function Invoke-DashboardProjectionSweep($Connection, [int]$CommandTimeout) {
    foreach ($projection in 'COLETAS','FRETES_VIGENTES','FRETES_VINCULOS','COTACOES') {
        Invoke-DashboardProjectionPageSequence $Connection $CommandTimeout $projection
    }
}

function Invoke-DashboardProjectionPageSequence($Connection, [int]$CommandTimeout, $Projection) {
    $list = $Connection.CreateCommand()
    $apply = $Connection.CreateCommand()
    try {
        $list.CommandTimeout = $CommandTimeout
        $list.CommandText = @'
SELECT TOP (32) business_date, entity_name, source_scope
FROM pub.vw_dashboard_particoes_pendentes
WHERE projection_name=@projection AND (
    @day IS NULL OR business_date>@day OR
    (business_date=@day AND (entity_name>@entity OR (entity_name=@entity AND source_scope>@scope)))
)
ORDER BY business_date, entity_name, source_scope
OPTION (MAXDOP 1);
'@
        $apply.CommandTimeout = $CommandTimeout
        $apply.CommandType = [Data.CommandType]::StoredProcedure
        $list.Parameters.Add('@projection', [Data.SqlDbType]::NVarChar, 128).Value = $Projection
        $apply.CommandText = switch ($Projection) {
            'COLETAS' { 'core.usp_dashboard_preparar_coletas' }
            'FRETES_VIGENTES' { 'core.usp_dashboard_preparar_fretes_vigentes' }
            'FRETES_VINCULOS' { 'core.usp_dashboard_preparar_fretes_vinculos' }
            'COTACOES' { 'core.usp_dashboard_preparar_cotacoes' }
            default { throw 'DASHBOARD_PROJECTION_UNKNOWN' }
        }
        $afterDay = $list.Parameters.Add('@day', [Data.SqlDbType]::Date)
        $afterEntity = $list.Parameters.Add('@entity', [Data.SqlDbType]::VarChar, 40)
        $afterScope = $list.Parameters.Add('@scope', [Data.SqlDbType]::Char, 64)
        $day = $apply.Parameters.Add('@business_date', [Data.SqlDbType]::Date)
        $entity = $apply.Parameters.Add('@entity_name', [Data.SqlDbType]::VarChar, 40)
        $scope = $apply.Parameters.Add('@source_scope', [Data.SqlDbType]::Char, 64)
        $afterDay.Value = $afterEntity.Value = $afterScope.Value = [DBNull]::Value
        $completed = 0
        $failed = 0
        do {
            $page = New-Object Collections.ArrayList
            $reader = $list.ExecuteReader()
            try {
                while ($reader.Read()) {
                    [void]$page.Add(@($reader.GetDateTime(0), $reader.GetString(1), $reader.GetString(2)))
                }
            } finally { $reader.Dispose() }
            foreach ($key in $page) {
                $day.Value = $afterDay.Value = $key[0]
                $entity.Value = $afterEntity.Value = $key[1]
                $scope.Value = $afterScope.Value = $key[2]
                try {
                    [void]$apply.ExecuteNonQuery()
                    $completed++
                } catch {
                    if ($Connection.State -ne [Data.ConnectionState]::Open) { throw }
                    # Um timeout pode interromper o batch antes do CATCH do SQL Server.
                    $cleanup = $Connection.CreateCommand()
                    try {
                        $cleanup.CommandText = 'IF @@TRANCOUNT>0 ROLLBACK;'
                        $cleanup.CommandTimeout = $CommandTimeout
                        [void]$cleanup.ExecuteNonQuery()
                    } finally { $cleanup.Dispose() }
                    $failed++
                    Write-Warning ('Preparo pendente: {0} {1:yyyy-MM-dd}; SQL {2}' -f $key[1],$key[0],$_.Exception.GetBaseException().Number)
                }
            }
        } while ($page.Count -eq 32)
        [pscustomobject]@{ Projection=$Projection; Completed=$completed; Failed=$failed; CheckedUtc=[DateTime]::UtcNow }
    } finally { $apply.Dispose(); $list.Dispose() }
}

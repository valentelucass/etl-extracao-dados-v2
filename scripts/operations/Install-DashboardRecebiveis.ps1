[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$credential=Get-ProjetosSqlAdminCredential
$connection=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credential -ApplicationName 'ETL-Instalar-Recebiveis'
try {
    $connection.Open()
    $transaction=$connection.BeginTransaction()
    $command=$connection.CreateCommand()
    $command.Transaction=$transaction
    foreach($relative in 'receiving/015_dashboard_parcelas_recebiveis.sql','publication/055_dashboard_financeiro_recebiveis.sql'){
        $contents=Get-Content (Join-Path $repo ('database/production/'+$relative)) -Raw -Encoding UTF8
        foreach($batch in ($contents -split '(?im)^GO\s*$')){
            if($batch.Trim()){
                $command.CommandText=$batch
                $null=$command.ExecuteNonQuery()
            }
        }
    }
    $transaction.Commit()
} finally {
    $connection.Dispose()
    $credential.Password.Dispose()
}

[CmdletBinding()]
param([switch]$ComBanco)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$script=Join-Path $repo 'scripts/operations/Read-RasterOverlappingWindows.ps1'
$intervalos=[Collections.Generic.List[object]]::new()
$de=[datetime]::new(2026,10,28); $ate=[datetime]::new(2026,11,5)
$fonte={
    param($a,$b,$doc)
    if($doc -ne '00000000000001'){throw 'O filtro de filial mudou.'}
    if(($b-$a).TotalDays -ge 5){return 'LIMITE'}
    $intervalos.Add(@($a,$b))
    return 'OK'
}
& $script $de $ate '00000000000001' $fonte
for($dia=$de;$dia -lt $ate;$dia=$dia.AddDays(1)){
    if(!@($intervalos | Where-Object {$_[0] -le $dia -and $_[1] -ge $dia.AddDays(1)}).Count){throw 'Divisao perdeu uma virada de dia.'}
}
$recusou=$false
try { & $script $de $de.AddDays(1) '00000000000001' {return 'LIMITE'} } catch { $recusou=$_.Exception.Message -like '*limite*' }
if(!$recusou){throw 'Uma resposta truncada foi aceita.'}
Write-Output 'OK: sobreposicao, virada de mes e recusa de janela truncada.'
if(!$ComBanco){return}
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cred=Get-ProjetosSqlAdminCredential
$con=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'Teste-Raster-Entre-Dias'
try {
    $con.Open(); $tx=$con.BeginTransaction()
    try {
        $cmd=$con.CreateCommand(); $cmd.Transaction=$tx; $cmd.CommandTimeout=90
        foreach($arq in @('database/production/publication/069_dashboard_raster_entre_dias.sql','database/production/publication/070_dashboard_raster_vigente_entre_dias.sql','scripts/validation/dashboard-raster-entre-dias.sql')){
            foreach($batch in ([IO.File]::ReadAllText((Join-Path $repo $arq)) -split '(?im)^GO\s*$')){
                if($batch.Trim()){$cmd.CommandText=$batch;$null=$cmd.ExecuteNonQuery()}
            }
        }
    } finally {if($tx.Connection){$tx.Rollback()}}
    Write-Output 'OK: inicio e fim, duplicidade, reprocessamento, ordem das atualizacoes, viagens e paradas no SQL. Transacao revertida.'
} finally {$con.Dispose();$cred.Password.Dispose()}

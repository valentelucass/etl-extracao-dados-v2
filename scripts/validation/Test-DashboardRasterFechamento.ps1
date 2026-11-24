[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../operations/DashboardRasterFechamento.ps1')
$pasta=Join-Path ([IO.Path]::GetTempPath()) ('etl-teste-raster-fechamento-'+[guid]::NewGuid().ToString('N'))
$null=[IO.Directory]::CreateDirectory($pasta)
$arquivo=Join-Path $pasta 'fechamento-mensal-v1.json'
function Testar-Plano([string]$data,[string]$primeiro,[string]$ultimo,[string]$ate){
    $plano=Get-RasterFechamentoPendente $pasta ([datetime]$data)
    if(!$primeiro){
        if($null -ne $plano){throw "Um mes ja conferido foi agendado novamente em $data."}
        return
    }
    if($null -eq $plano -or $plano.Inicio.ToString('yyyy-MM-dd') -ne $primeiro -or
       $plano.Fim.ToString('yyyy-MM-dd') -ne $ultimo -or $plano.ConferirAte.ToString('yyyy-MM-dd') -ne $ate){
        throw "Periodo incorreto para $data."
    }
}
try {
    $chamadas=[Collections.Generic.List[string]]::new()
    $executar={param([string[]]$Opcoes,[int]$Minutos) $chamadas.Add(('{0} {1}' -f $Minutos,($Opcoes -join ' ')).Trim())}
    Invoke-RasterCicloAgendado -Pasta $pasta -Hoje ([datetime]'2026-11-24') -ExecutarLote $executar
    if(($chamadas -join '|') -ne '26|45 -FechamentoMensal'){throw 'A tarefa normal nao agendou o mes anterior depois do periodo corrente.'}
    $chamadas.Clear()
    $opcoes=@('-Inicio','2026-10-10','-Fim','2026-10-12','-SomenteEntreDias')
    Invoke-RasterCicloAgendado -Pasta $pasta -Hoje ([datetime]'2026-11-24') -ExecutarLote $executar -Opcoes $opcoes
    if(($chamadas -join '|') -ne ('26 '+($opcoes -join ' '))){throw 'O agendamento perdeu o periodo ou repetiu uma consulta especifica.'}
    Testar-Plano '2026-11-24' '2026-10-01' '2026-10-31' '2026-11-07'
    Testar-Plano '2027-01-10' '2026-12-01' '2026-12-31' '2027-01-07'
    Testar-Plano '2028-03-20' '2028-02-01' '2028-02-29' '2028-03-07'
    Testar-Plano '2027-03-01' '2027-02-01' '2027-02-28' '2027-03-01'
    Testar-Plano '2026-12-12' '2026-11-01' '2026-11-30' '2026-12-07'

    # A interrupcao durante a leitura ou publicacao deixa o mesmo mes pendente,
    # inclusive se o relogio virar para outro mes durante a indisponibilidade.
    Set-RasterFechamento $pasta ([datetime]'2026-10-01')
    Testar-Plano '2026-12-03' '2026-10-01' '2026-10-31' '2026-11-07'
    Set-RasterFechamento $pasta ([datetime]'2026-10-01') '2026-11-01'
    Testar-Plano '2026-11-02'
    Testar-Plano '2026-11-06'
    Testar-Plano '2026-11-07' '2026-10-01' '2026-10-31' '2026-11-07'
    Set-RasterFechamento $pasta ([datetime]'2026-10-01') '2026-11-07'
    Testar-Plano '2026-11-24'
    $chamadas.Clear()
    Invoke-RasterCicloAgendado -Pasta $pasta -Hoje ([datetime]'2026-11-24') -ExecutarLote $executar
    if(($chamadas -join '|') -ne '26'){throw 'A tarefa releu um fechamento completo ou deixou de atualizar o dia.'}
    Testar-Plano '2027-01-03' '2026-11-01' '2026-11-30' '2026-12-07'
    Set-RasterFechamento $pasta ([datetime]'2026-11-01') '2026-12-07'
    Testar-Plano '2027-01-03' '2026-12-01' '2026-12-31' '2027-01-03'

    [IO.File]::WriteAllText($arquivo,'{"mes":"2026-10","conferidoAte":"2026-10-19"}')
    $rejeitado=$false
    try {Get-RasterFechamentoPendente $pasta ([datetime]'2026-11-07') | Out-Null} catch {$rejeitado=$true}
    if(!$rejeitado){throw 'Um recibo parcial foi considerado completo.'}
    Write-Output 'OK: mes anterior completo, meses de 28/29/30/31 dias, virada do ano, retomada apos falha, semana de virada e reexecucao.'
} finally {
    if([IO.File]::Exists($arquivo)){[IO.File]::Delete($arquivo)}
    [IO.Directory]::Delete($pasta)
}

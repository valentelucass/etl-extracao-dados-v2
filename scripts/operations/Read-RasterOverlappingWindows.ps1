[CmdletBinding()]
param(
    [Parameter(Mandatory)][datetime]$Inicio,
    [Parameter(Mandatory)][datetime]$Fim,
    [Parameter(Mandatory)][string]$Cnpj,
    [Parameter(Mandatory)][scriptblock]$Consultar
)
# A mesma SM pode aparecer nos dois intervalos ou nas duas filiais.
# A persistencia usa a identidade do Raster para publicar a viagem uma unica vez.
$resultado=& $Consultar $Inicio $Fim $Cnpj
if($resultado -eq 'OK'){return}
if($resultado -ne 'LIMITE'){throw 'Resposta inesperada na consulta de viagens entre dias.'}

$dias=[int]($Fim.Date-$Inicio.Date).TotalDays
if($dias -lt 2){throw ('O Raster atingiu o limite inclusive na janela de duas datas da filial '+$Cnpj+'.')}
# Com datas inclusivas, intervalos adjacentes perderiam todas as viagens da virada.
$passo=[math]::Max(1,[math]::Floor(($dias+1)/3))
& $PSCommandPath -Inicio $Inicio -Fim $Fim.AddDays(-$passo) -Cnpj $Cnpj -Consultar $Consultar
& $PSCommandPath -Inicio $Inicio.AddDays($passo) -Fim $Fim -Cnpj $Cnpj -Consultar $Consultar

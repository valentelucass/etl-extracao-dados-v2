# Chamadas de escrita ocorrem sob o mesmo bloqueio SQL usado para publicar o Raster.
function Get-RasterFechamentoPendente {
    param([string]$Pasta, [datetime]$Hoje)
    $mesAtual=[datetime]::new($Hoje.Year,$Hoje.Month,1)
    $inicio=$mesAtual.AddMonths(-1)
    $arquivo=Join-Path $Pasta 'fechamento-mensal-v1.json'
    if([IO.File]::Exists($arquivo)){
        $estado=[IO.File]::ReadAllText($arquivo) | ConvertFrom-Json
        $inicio=[datetime]::ParseExact($estado.mes+'-01','yyyy-MM-dd',[cultureinfo]::InvariantCulture)
        if($estado.conferidoAte){
            $confirmado=[datetime]::ParseExact($estado.conferidoAte,'yyyy-MM-dd',[cultureinfo]::InvariantCulture)
            $ultimoDia=$inicio.AddMonths(1).AddDays(-1)
            $limite=$ultimoDia.AddDays(7)
            if($confirmado -lt $ultimoDia -or $confirmado -gt $limite){throw 'Recibo de fechamento Raster com intervalo invalido.'}
            if($confirmado -eq $limite){$inicio=$inicio.AddMonths(1)}
            elseif($Hoje.Date -lt $limite){return}
        }
    }
    if($inicio -ge $mesAtual){return}
    $fim=$inicio.AddMonths(1).AddDays(-1)
    $ate=$fim.AddDays(7)
    if($ate -gt $Hoje.Date){$ate=$Hoje.Date}
    [pscustomobject]@{Inicio=$inicio;Fim=$fim;ConferirAte=$ate}
}

function Set-RasterFechamento {
    param([string]$Pasta, [datetime]$Inicio, [string]$ConferidoAte)
    $arquivo=Join-Path $Pasta 'fechamento-mensal-v1.json'
    $novo=Join-Path $Pasta ('fechamento-'+[guid]::NewGuid().ToString('N')+'.tmp')
    $estado=[ordered]@{mes=$Inicio.ToString('yyyy-MM');conferidoAte=$ConferidoAte}
    try {
        [IO.File]::WriteAllText($novo,($estado | ConvertTo-Json -Compress))
        if([IO.File]::Exists($arquivo)){
            [IO.File]::Replace($novo,$arquivo,[System.Management.Automation.Language.NullString]::Value)
        }else{[IO.File]::Move($novo,$arquivo)}
    } finally {if([IO.File]::Exists($novo)){[IO.File]::Delete($novo)}}
}

function Invoke-RasterCicloAgendado {
    param([string]$Pasta, [datetime]$Hoje, [scriptblock]$ExecutarLote, [string[]]$Opcoes, [switch]$FechamentoMensal)
    if(!$FechamentoMensal){& $ExecutarLote -Opcoes $Opcoes -Minutos 26}
    if(!$Opcoes.Count -and (Get-RasterFechamentoPendente $Pasta $Hoje)){
        & $ExecutarLote -Opcoes @('-FechamentoMensal') -Minutos 45
    }
}

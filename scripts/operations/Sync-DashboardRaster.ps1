[CmdletBinding()]
param(
    [string]$PowerShell7='pwsh.exe',
    [datetime]$Inicio,
    [datetime]$Fim,
    [switch]$Agendado,
    [switch]$SomenteEntreDias,
    [switch]$FechamentoMensal
)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$pwsh=(Get-Command $PowerShell7 -CommandType Application -ErrorAction Stop).Source
. (Join-Path $PSScriptRoot 'DashboardRasterFechamento.ps1')
$private=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2/raster-atualizacao'
$null=[IO.Directory]::CreateDirectory($private)
$personalizado=$PSBoundParameters.ContainsKey('Inicio') -or $PSBoundParameters.ContainsKey('Fim')
if($FechamentoMensal -and ($personalizado -or $SomenteEntreDias)){throw 'O fechamento mensal determina o seu proprio periodo.'}
if($SomenteEntreDias -and !$personalizado){throw 'Informe o periodo para reconferir as viagens entre dias.'}
if($personalizado -and (!$PSBoundParameters.ContainsKey('Inicio') -or !$PSBoundParameters.ContainsKey('Fim') -or
    $Fim.Date -lt $Inicio.Date -or ($Fim.Date-$Inicio.Date).TotalDays -gt 31)){throw 'Informe um periodo de ate 32 dias.'}
$hoje=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow,'E. South America Standard Time').Date
if($Agendado){
    $scriptAtual=$PSCommandPath
    $limite=(Get-Date).AddMinutes(75)
    $executarLote={
        param([string[]]$Opcoes,[int]$Minutos)
        $jobs=Join-Path $repo '../.runtime/memory-control/Executar-Batch.ps1'
        $argsJob=@('-NoLogo','-NoProfile','-NonInteractive','-File',$scriptAtual,'-PowerShell7',$pwsh)+$Opcoes
        while($true){
            try {
                $execucao=& $jobs -Executable powershell.exe -ArgumentList $argsJob -WorkingDirectory $repo -LimitMiB 680 -MaxMinutes $Minutos -ReturnReceipt
                if($null -eq $execucao.job.ExitCode -or $execucao.job.ExitCode -ne 0){throw ('Falha na atualizacao Raster, codigo '+$execucao.job.ExitCode)}
                return
            } catch {
                if($_.Exception.Message -notmatch '^BATCH_(CONCURRENCY_LIMIT|MEMORY_ADMISSION)' -or (Get-Date) -ge $limite){throw}
                Start-Sleep -Seconds 30
            }
        }
    }
    $opcoes=@()
    if($personalizado){$opcoes=@('-Inicio',$Inicio.ToString('yyyy-MM-dd'),'-Fim',$Fim.ToString('yyyy-MM-dd'))}
    if($SomenteEntreDias){$opcoes+='-SomenteEntreDias'}
    Invoke-RasterCicloAgendado -Pasta $private -Hoje $hoje -ExecutarLote $executarLote -Opcoes $opcoes -FechamentoMensal:$FechamentoMensal
    return
}
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cred=Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$con=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Atualizar-Raster'
try {
    $con.Open()
    $cmd=$con.CreateCommand()
    $cmd.CommandTimeout=120
    $cmd.CommandText="DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='dashboard_raster_atualizacao',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r"
    if([int]$cmd.ExecuteScalar() -lt 0){
        Write-Output 'Atualizacao do Raster em andamento. A tarefa agendada volta a consultar no proximo ciclo.'
        return
    }
    $fechamento=$null
    if($FechamentoMensal -or ($personalizado -and !$SomenteEntreDias -and $Inicio.Day -eq 1 -and $Fim.Date -eq $Inicio.Date.AddMonths(1).AddDays(-1))){
        $plano=Get-RasterFechamentoPendente -Pasta $private -Hoje $hoje
        if($FechamentoMensal){
            if(!$plano){return}
            $Inicio=$plano.Inicio
            $Fim=$plano.Fim
            $personalizado=$true
        }
        if($plano -and $Inicio.Date -eq $plano.Inicio -and $Fim.Date -eq $plano.Fim){
            $fechamento=$plano
            Set-RasterFechamento -Pasta $private -Inicio $Inicio
        }
    }
    $dias=[Collections.Generic.List[datetime]]::new()
    if($personalizado){
        if(!$SomenteEntreDias){
            for($dia=$Inicio.Date;$dia -le $Fim.Date;$dia=$dia.AddDays(1)){$dias.Add($dia)}
        }
    } else {
        $cmd.CommandText='EXEC core.usp_dashboard_dias_raster'
        $leitor=$cmd.ExecuteReader()
        try {while($leitor.Read()){$dias.Add($leitor.GetDateTime(0))}} finally {$leitor.Dispose()}
    }
    $falhas=[Collections.Generic.List[string]]::new()
    foreach($dia in $dias){
        $data=$dia.ToString('yyyy-MM-dd')
        $recibo=Join-Path $private ('sync-'+[guid]::NewGuid().ToString('N')+'.json')
        $arquivo=$null
        try {
            if(Test-Path -LiteralPath (Join-Path $private 'STOP429-Raster.json')){throw 'SOURCE_RATE_LIMIT_STOP'}
            & $pwsh -NoLogo -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'Capture-DashboardAuxiliarySource.ps1') -Source Raster -RasterDate $data -Receipt $recibo -CaptureDirectory $private
            if($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $recibo)){throw 'RASTER_DIA_NAO_RECEBIDO'}
            $r=Get-Content -LiteralPath $recibo -Raw -Encoding UTF8 | ConvertFrom-Json
            if(!$r.passed -or $r.source -ne 'Raster' -or $r.status -ne 200 -or $r.from -or $r.day -ne $data -or
                $r.captureFile -cnotmatch ('^raster-'+$data+'-[a-f0-9]{32}\.json$')){throw 'RASTER_RECIBO_INVALIDO'}
            $arquivo=Join-Path $private $r.captureFile
            if((Get-FileHash -LiteralPath $arquivo).Hash -ne $r.sha256){throw 'RASTER_CONTEUDO_DIVERGENTE'}
            $cmd.Parameters.Clear()
            $cmd.CommandText='EXEC core.usp_dashboard_receber_dia_raster @dia,@observado,@total,@registros'
            $cmd.Parameters.Add('@dia',[Data.SqlDbType]::Date).Value=$dia
            $cmd.Parameters.Add('@observado',[Data.SqlDbType]::DateTimeOffset).Value=[DateTimeOffset]::Parse($r.completedAt)
            $cmd.Parameters.Add('@total',[Data.SqlDbType]::Int).Value=[int]$r.items
            $cmd.Parameters.Add('@registros',[Data.SqlDbType]::NVarChar,-1).Value=[IO.File]::ReadAllText($arquivo)
            $null=$cmd.ExecuteNonQuery()
            Write-Output "$data - $($r.items) viagens conferidas."
        } catch {
            if(Test-Path -LiteralPath (Join-Path $private 'STOP429-Raster.json')){throw 'Raster limitou as consultas. Consulte o recibo STOP429.'}
            $falhas.Add($data)
            Write-Warning ("Falha em "+$data+": "+$_.Exception.Message)
        } finally {
            if($arquivo -and (Test-Path -LiteralPath $arquivo -PathType Leaf)){Remove-Item -LiteralPath $arquivo}
            if(Test-Path -LiteralPath $recibo -PathType Leaf){Remove-Item -LiteralPath $recibo}
        }
    }
    # A API restringe tanto o inicio como o fim real. A leitura por filial permite
    # abranger o mes com seus limites sem estourar o teto de viagens da origem.
    $primeiro=if($personalizado){$Inicio.Date}else{[datetime]::new($hoje.Year,$hoje.Month,1)}
    $de=$primeiro.AddDays(-7)
    $ate=if($personalizado -and $Fim.Date.AddDays(7) -lt $hoje){$Fim.Date.AddDays(7)}else{$hoje}
    $marcador=Join-Path $private 'ultima-conferencia-mensal.txt'
    $dataHoje=$hoje.ToString('yyyy-MM-dd')
    $janelaCurta=!$personalizado -and [IO.File]::Exists($marcador) -and [IO.File]::ReadAllText($marcador) -ceq $dataHoje
    if($janelaCurta){$de=$hoje.AddDays(-3)}
    $cmd.Parameters.Clear()
    $cmd.CommandText='EXEC core.usp_dashboard_filiais_raster'
    $filiais=[Collections.Generic.List[string]]::new()
    $leitor=$cmd.ExecuteReader()
    try {while($leitor.Read()){$filiais.Add($leitor.GetString(0))}} finally {$leitor.Dispose()}
    if(!$filiais.Count){throw 'Nao foram encontrados os documentos das filiais Raster.'}
    $lerJanela={
        param([datetime]$inicioJanela,[datetime]$fimJanela,[string]$cnpjFilial)
        $inicioIso=$inicioJanela.ToString('yyyy-MM-dd')
        $fimIso=$fimJanela.ToString('yyyy-MM-dd')
        $recibo=Join-Path $private ('janela-'+[guid]::NewGuid().ToString('N')+'.json')
        $arquivo=$null
        try {
            if(Test-Path -LiteralPath (Join-Path $private 'STOP429-Raster.json')){throw 'SOURCE_RATE_LIMIT_STOP'}
            $argsFiltro=@()
            if($cnpjFilial){$argsFiltro=@('-RasterCnpj',$cnpjFilial)}
            & $pwsh -NoLogo -NoProfile -NonInteractive -File (Join-Path $repo 'scripts/operations/Capture-DashboardAuxiliarySource.ps1') -Source Raster -RasterFrom $inicioIso -RasterDate $fimIso @argsFiltro -Receipt $recibo -CaptureDirectory $private | Out-Null
            $codigo=$LASTEXITCODE
            $r=Get-Content -LiteralPath $recibo -Raw -Encoding UTF8 | ConvertFrom-Json
            if($r.failure -eq 'RASTER_RESPONSE_TOO_LARGE' -or ($r.failure -eq 'RASTER_DAY_COVERAGE_UNPROVEN' -and $r.items -ge 500)){return 'LIMITE'}
            if($codigo -ne 0 -or !$r.passed -or $r.status -ne 200 -or $r.source -ne 'Raster' -or
                $r.from -ne $inicioIso -or $r.day -ne $fimIso -or [string]$r.branch -ne $cnpjFilial -or
                $r.captureFile -cnotmatch ('^raster-intervalo-'+$fimIso+'-[a-f0-9]{32}\.json$')){throw 'RASTER_JANELA_NAO_CONFIRMADA'}
            $arquivo=Join-Path $private $r.captureFile
            if((Get-FileHash -LiteralPath $arquivo).Hash -ne $r.sha256){throw 'RASTER_CONTEUDO_DIVERGENTE'}
            $cmd.Parameters.Clear()
            $cmd.CommandText='EXEC core.usp_dashboard_receber_raster_entre_dias @inicio,@fim,@observado,@total,@registros'
            $cmd.Parameters.Add('@inicio',[Data.SqlDbType]::Date).Value=$inicioJanela
            $cmd.Parameters.Add('@fim',[Data.SqlDbType]::Date).Value=$fimJanela
            $cmd.Parameters.Add('@observado',[Data.SqlDbType]::DateTimeOffset).Value=[DateTimeOffset]::Parse($r.completedAt)
            $cmd.Parameters.Add('@total',[Data.SqlDbType]::Int).Value=[int]$r.items
            $cmd.Parameters.Add('@registros',[Data.SqlDbType]::NVarChar,-1).Value=[IO.File]::ReadAllText($arquivo)
            $alteradas=$cmd.ExecuteScalar()
            $origem=if($cnpjFilial){'Filial '+$cnpjFilial}else{'Todas as filiais'}
            Write-Host ("{0}: {1} a {2}, {3} viagens entre dias inseridas ou corrigidas." -f $origem,$inicioIso,$fimIso,$alteradas)
            return 'OK'
        } finally {
            if($arquivo -and (Test-Path -LiteralPath $arquivo -PathType Leaf)){Remove-Item -LiteralPath $arquivo}
            if(Test-Path -LiteralPath $recibo -PathType Leaf){Remove-Item -LiteralPath $recibo}
        }
    }
    # A primeira passagem do dia cobre o mes. Nas seguintes, uma chamada curta
    # cobre a madrugada de todas as filiais quando cabe no limite da origem.
    if(!$janelaCurta -or (& $lerJanela $de $ate '') -eq 'LIMITE'){
        foreach($documento in $filiais){
            try { & (Join-Path $PSScriptRoot 'Read-RasterOverlappingWindows.ps1') -Inicio $de -Fim $ate -Cnpj $documento -Consultar $lerJanela }
            catch {
                if(Test-Path -LiteralPath (Join-Path $private 'STOP429-Raster.json')){throw 'Raster limitou as consultas. Consulte o recibo STOP429.'}
                $falhas.Add($documento)
                Write-Warning ('Falha na conferencia da filial '+$documento+': '+$_.Exception.Message)
            }
        }
    }
    $cmd.Parameters.Clear()
    $cmd.CommandText='EXEC core.usp_dashboard_atualizar_leitura_raster'
    $null=$cmd.ExecuteNonQuery()
    if($falhas.Count){throw ('Datas ou filiais Raster pendentes: '+($falhas -join ', '))}
    if($fechamento){
        $diasEsperados=($fechamento.Fim-$fechamento.Inicio).Days+1
        if($dias.Count -ne $diasEsperados -or $ate -ne $fechamento.ConferirAte){throw 'Cobertura mensal Raster incompleta.'}
        Set-RasterFechamento -Pasta $private -Inicio $Inicio -ConferidoAte $ate.ToString('yyyy-MM-dd')
        Write-Output ('Fechamento Raster de {0:yyyy-MM} publicado, com {1} dias e viagens entre dias conferidas ate {2:yyyy-MM-dd}.' -f $Inicio,$dias.Count,$ate)
    }
    if(!$janelaCurta -and $ate -eq $hoje -and $de -le [datetime]::new($hoje.Year,$hoje.Month,1).AddDays(-7)){
        [IO.File]::WriteAllText($marcador,$dataHoje)
    }
} finally {$con.Dispose();$cred.Password.Dispose()}

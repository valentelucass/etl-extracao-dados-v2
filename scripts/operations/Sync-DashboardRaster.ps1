[CmdletBinding()]
param(
    [string]$PowerShell7='pwsh.exe',
    [datetime]$Inicio,
    [datetime]$Fim,
    [switch]$Agendado
)
$ErrorActionPreference='Stop'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$pwsh=(Get-Command $PowerShell7 -CommandType Application -ErrorAction Stop).Source
if($Agendado){
    $limite=(Get-Date).AddMinutes(75)
    while($true){
        try {
            $argsJob=@('-NoLogo','-NoProfile','-NonInteractive','-File',$PSCommandPath,'-PowerShell7',$pwsh)
            $jobs=Join-Path $repo '../.runtime/memory-control/Executar-Batch.ps1'
            $execucao=& $jobs -Executable powershell.exe -ArgumentList $argsJob -WorkingDirectory $repo -LimitMiB 680 -MaxMinutes 26 -ReturnReceipt
            if($execucao.job.ExitCode -ne 0){throw ('Falha na atualizacao Raster, codigo '+$execucao.job.ExitCode)}
            return
        } catch {
            if($_.Exception.Message -notmatch '^BATCH_(CONCURRENCY_LIMIT|MEMORY_ADMISSION)' -or (Get-Date) -ge $limite){throw}
            Start-Sleep -Seconds 30
        }
    }
}
$private=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2/raster-atualizacao'
$null=[IO.Directory]::CreateDirectory($private)
$personalizado=$PSBoundParameters.ContainsKey('Inicio') -or $PSBoundParameters.ContainsKey('Fim')
if($personalizado -and (!$PSBoundParameters.ContainsKey('Inicio') -or !$PSBoundParameters.ContainsKey('Fim') -or
    $Fim.Date -lt $Inicio.Date -or ($Fim.Date-$Inicio.Date).TotalDays -gt 31)){throw 'Informe um periodo de ate 32 dias.'}
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$cred=Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$con=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Atualizar-Raster'
try {
    $con.Open()
    $cmd=$con.CreateCommand()
    $cmd.CommandTimeout=120
    $cmd.CommandText="DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource='dashboard_raster_atualizacao',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r"
    if([int]$cmd.ExecuteScalar() -lt 0){Write-Output 'Atualizacao do Raster em andamento.'; return}
    $dias=[Collections.Generic.List[datetime]]::new()
    if($personalizado){
        for($dia=$Inicio.Date;$dia -le $Fim.Date;$dia=$dia.AddDays(1)){$dias.Add($dia)}
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
            if(!$r.passed -or $r.source -ne 'Raster' -or $r.status -ne 200 -or $r.day -ne $data -or
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
    $cmd.Parameters.Clear()
    $cmd.CommandText='EXEC core.usp_dashboard_atualizar_leitura_raster'
    $null=$cmd.ExecuteNonQuery()
    if($falhas.Count){throw ('Dias Raster pendentes: '+($falhas -join ', '))}
} finally {$con.Dispose();$cred.Password.Dispose()}

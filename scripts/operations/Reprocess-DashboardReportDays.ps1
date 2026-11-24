[CmdletBinding(DefaultParameterSetName='Ocorrencias')]
param(
    [Parameter(ParameterSetName='Ocorrencias')][ValidateRange(1,1000)][int]$MaximoDias=97,
    [Parameter(Mandatory,ParameterSetName='Fretes')][ValidateCount(1,1000)]
    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')][string[]]$DatasFrete
)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$env:PSModulePath=(Join-Path $PSHOME 'Modules')+';'+$env:PSModulePath
$fretes=$PSCmdlet.ParameterSetName -eq 'Fretes'
$areaRelatorio='ocorrencias-performance-059'
$campoData='reprocessOccurrenceDate'
$schemaRecibo='OCORRENCIA_RECAPTURA_FILIAL'
$nomeMutex='Global\ETL_V2_PERFORMANCE_FILIAIS'
if($fretes){
    $areaRelatorio='fretes-performance-comprovantes'
    $campoData='reprocessFreightDate'
    $schemaRecibo='FRETE_RECAPTURA_COMPROVANTE'
    $nomeMutex='Global\ETL_V2_PERFORMANCE_COMPROVANTES'
    $datas=@($DatasFrete | Select-Object -Unique)
    $MaximoDias=$datas.Count
}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$armazenamento=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2'
$rotina=Join-Path $armazenamento 'atualizacao0766'
$estado=Join-Path $rotina 'operational'
$area=Join-Path $rotina $areaRelatorio
$manifesto=Get-Content -LiteralPath (Join-Path $rotina 'runtime-manifest-dashboard-fields-0818.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$jdk=Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium/jdk-17/bin'
$stop=Join-Path $armazenamento 'quatro-etapas-20261024-01/independent-export-shared-dataexport/STOP429.json'

Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
$runtimeCred=Import-Clixml (Join-Path $env:LOCALAPPDATA 'Rodogarcia/SqlServer/etl-v2-runtime-prod.clixml')
$profileSql=Get-ProjetosSqlProfile
$credConsulta=$runtimeCred
if(!$fretes){$adm=Get-ProjetosSqlAdminCredential; $credConsulta=$adm}
$consulta=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $credConsulta -ApplicationName ('ETL-Reprocessar-'+$areaRelatorio)
$vez=[Threading.Mutex]::new($false,$nomeMutex)
$tomouVez=$false
$fonte=[Threading.Mutex]::new($false,'Global\ETL_V2_DATAEXPORT_READONLY_PROBE')
$tomouFonte=$false

function Atualizar-Leitura($comando, [switch]$Aguardar) {
    $comando.CommandText=@'
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',
     @LockOwner='Transaction',@LockTimeout=5000;
IF @lock>=0 EXEC core.usp_dashboard_preparar_ocorrencias;
COMMIT;
SELECT @lock;
'@
    do {
        $resultado=[int]$comando.ExecuteScalar()
        if($resultado -ge 0 -or !$Aguardar){return}
        if($resultado -ne -1){throw "Falha ao obter a publicacao de ocorrencias ($resultado)."}
        Start-Sleep -Seconds 10
    }while($true)
}

try {
    try{$tomouVez=$vez.WaitOne(0)}catch [Threading.AbandonedMutexException]{$tomouVez=$true}
    if(-not $tomouVez){throw "Uma recaptura de $areaRelatorio ja esta ativa."}
    $classes=Join-Path $area 'classes'
    $null=New-Item -ItemType Directory -Force $classes
    foreach($pin in $manifesto.files){
        if((Get-FileHash -Algorithm SHA256 (Join-Path $manifesto.directory $pin.name)).Hash -ine $pin.sha256){throw 'Runtime diferente da versao instalada.'}
    }
    $jars=Join-Path $manifesto.directory '*'
    & (Join-Path $jdk 'javac.exe') -encoding UTF-8 -cp $jars -d $classes (Join-Path $repo 'src/main/java/br/com/esl/etl/v2/plataforma/producao/ProductionRefreshCycle.java')
    if($LASTEXITCODE){throw 'Falha ao compilar a rotina de recaptura.'}
    $valores=@{}
    foreach($linha in [IO.File]::ReadAllLines((Join-Path $repo '.env'))){
        if($linha -match '^\s*(API_BASE_URL|API_DATAEXPORT_TOKEN)\s*=\s*(.*)$'){$valores[$matches[1]]=$matches[2].Trim().Trim('"').Trim("'")}
    }
    if(!$valores.API_BASE_URL -or !$valores.API_DATAEXPORT_TOKEN){throw 'Credencial do ESL indisponivel.'}
    $jar=@($manifesto.files | Where-Object name -eq 'etl-dataexport-v2-0766.jar')
    if($jar.Count -ne 1){throw 'Runtime indefinido.'}
    $consulta.Open()
    $cmd=$consulta.CreateCommand()
    $cmd.CommandTimeout=120
    if($fretes){
        $checkpoint=Get-Content -Raw -LiteralPath (Join-Path $estado 'checkpoint.json') | ConvertFrom-Json
        $scope=[string]$checkpoint.payload.sourceScope
        if($scope -cnotmatch '^[a-f0-9]{64}$'){throw 'Escopo do recebimento nao identificado.'}
        $cmd.CommandText="EXEC ctl.usp_production_ingestion_plan @scope,@dia,@dia,'FRETES'"
        $cmd.Parameters.Add('@scope',[Data.SqlDbType]::Char,64).Value=$scope
        $dataConsulta=$cmd.Parameters.Add('@dia',[Data.SqlDbType]::Date)
    }else{Atualizar-Leitura $cmd}
    $sqlDia=@'
SELECT TOP (1) CONVERT(char(10), c.business_date, 23) AS dia,r.row_count AS linhas
FROM ctl.production_ingestion_coverage c
JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id
CROSS APPLY (SELECT TOP(1) o.source_envelope FROM stg.production_export_occurrence o
             WHERE o.run_id=c.latest_run_id ORDER BY o.occurrence_ordinal) AS amostra
WHERE c.entity_name='OCORRENCIAS' AND JSON_PATH_EXISTS(amostra.source_envelope,'$.record.ice_fit_fln_cln_nickname')=1
  AND JSON_PATH_EXISTS(amostra.source_envelope,'$.record.ice_uer_crn_psn_nickname')=0
ORDER BY c.business_date DESC FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
'@
    $quantidade=0
    while($quantidade -lt $MaximoDias){
        if((Test-Path $stop) -or (Test-Path (Join-Path $estado 'STOP.failure.json')) -or (Test-Path (Join-Path $estado 'STOP.operator.json'))){throw 'A integracao tem uma parada registrada.'}
        $reserva=600
        if($fretes){
            $dia=[pscustomobject]@{dia=$datas[$quantidade]}
            $dataConsulta.Value=[DateTime]::ParseExact($dia.dia,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
            $reader=$cmd.ExecuteReader()
            try{
                if(!$reader.Read() -or !$reader.GetBoolean($reader.GetOrdinal('stored'))){
                    throw "Sem captura anterior de Fretes em $($dia.dia)."
                }
            }finally{$reader.Dispose()}
        }else{
            $cmd.CommandText=$sqlDia
            $proximo=$cmd.ExecuteScalar()
            if(!$proximo -or $proximo -is [DBNull]){break}
            $dia=$proximo | ConvertFrom-Json
            $reserva=[Math]::Max(600,[Math]::Ceiling($dia.linhas/100.0)*6 + 120)
        }
        $supervisor=Get-Content (Join-Path $estado 'supervisor.json') -Raw | ConvertFrom-Json
        if($supervisor.phase -ne 'WAITING' -or [DateTimeOffset]::Parse($supervisor.nextEligibleAt) -le [DateTimeOffset]::UtcNow.AddSeconds($reserva)){
            Start-Sleep -Seconds 30
            continue
        }
        try{$tomouFonte=$fonte.WaitOne(0)}catch [Threading.AbandonedMutexException]{$tomouFonte=$true}
        if(-not $tomouFonte){Start-Sleep -Seconds 15; continue}
        try{
            $id=[Guid]::NewGuid().ToString('N')
            $config=@{database='ETL_SISTEMA_V2';runtimeSha256=$jar[0].sha256.ToLowerInvariant();historyDays=90;
                jdbcBaseUrl=$profileSql.jdbcBaseUrl;sqlUser=$runtimeCred.UserName;sqlPassword=$runtimeCred.GetNetworkCredential().Password;
                apiOrigin=$valores.API_BASE_URL.TrimEnd('/');apiToken=$valores.API_DATAEXPORT_TOKEN;
                privateRoot=(Join-Path $area $id);reportPath=(Join-Path $area ($id+'.json'));
                checkpointPath=(Join-Path $estado ('checkpoint-recuperacao-'+$id+'.json'));sourceStopPath=$stop}
            $config[$campoData]=$dia.dia
            $p=[Diagnostics.Process]::new()
            try{
                $p.StartInfo.FileName=Join-Path $jdk 'java.exe'
                $p.StartInfo.Arguments='-Xmx768m -Dfile.encoding=UTF-8 -cp "'+$classes+';'+$jars+'" br.com.esl.etl.v2.bootstrap.ProductionRefreshMain'
                $p.StartInfo.UseShellExecute=$false
                $p.StartInfo.CreateNoWindow=$true
                $p.StartInfo.RedirectStandardInput=$true
                $p.StartInfo.RedirectStandardOutput=$true
                $p.StartInfo.RedirectStandardError=$true
                $null=$p.Start()
                $out=$p.StandardOutput.ReadToEndAsync()
                $err=$p.StandardError.ReadToEndAsync()
                $escritor=[IO.StreamWriter]::new($p.StandardInput.BaseStream,[Text.UTF8Encoding]::new($false))
                try{$escritor.Write(($config|ConvertTo-Json -Compress))}finally{$escritor.Dispose();$config.Clear()}
                $p.WaitForExit()
                $null=$out.GetAwaiter().GetResult()
                $erro=$err.GetAwaiter().GetResult()
                if($p.ExitCode){
                    $codigo=[regex]::Match($erro,'(?<=Exception: )[A-Z_0-9]{3,100}\b').Value
                    throw "Falha na recaptura de $($dia.dia): $codigo. Recibo $id."
                }
            }finally{$p.Dispose()}
            $recibo=Get-Content (Join-Path $area ($id+'.json')) -Raw -Encoding UTF8 | ConvertFrom-Json
            $operacao=@($recibo.operations)
            if($recibo.schema -cne $schemaRecibo -or !$recibo.passed -or $operacao.Count -ne 1 -or !$operacao[0].snapshotTerminal -or !$operacao[0].sqlReadbackExact -or $operacao[0].day -ne $dia.dia){
                throw 'Recaptura sem confirmacao de leitura do SQL.'
            }
            $quantidade++
            [pscustomobject]@{dia=$dia.dia;linhas=$operacao[0].rows;captura=$operacao[0].runId;releitura=$true} | ConvertTo-Json -Compress
        }finally{$fonte.ReleaseMutex();$tomouFonte=$false}
        if(!$fretes){Atualizar-Leitura $cmd}
    }
    if($fretes){
        $particoes=@(& (Join-Path $PSScriptRoot 'Invoke-DashboardProjectionRefresh.ps1') -Database 'ETL_SISTEMA_V2' -Credential $runtimeCred -CommandTimeout 120)
        if($particoes.Count -ne 4 -or @($particoes | Where-Object {$_.Failed -gt 0}).Count){throw 'Projecao de fretes pendente.'}
        $cmd.Parameters.Clear()
        $cmd.CommandText='EXEC core.usp_dashboard_publicar_fatos_fretes'
        $cmd.CommandTimeout=7200
        $null=$cmd.ExecuteNonQuery()
        Write-Output "Fretes publicados para $quantidade data(s) de servico."
    }else{Atualizar-Leitura $cmd -Aguardar}
}finally{
    if($tomouFonte){$fonte.ReleaseMutex()}
    if($tomouVez){$vez.ReleaseMutex()}
    $fonte.Dispose();$vez.Dispose();$consulta.Dispose()
    $runtimeCred.Password.Dispose()
    if($adm){$adm.Password.Dispose()}
    if($valores){$valores.Clear()}
}

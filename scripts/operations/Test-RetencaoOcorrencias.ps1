# Exercita os objetos instalados com um dia sintético que nunca é confirmado.
[CmdletBinding()] param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
Import-Module (Join-Path $PSScriptRoot 'RetentionWorker.psm1')
Initialize-RetentionArchive
$cred=Get-ProjetosSqlAdminCredential
$cn=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Prova-Retencao-Sintetica'
$directory=Join-Path ([IO.Path]::GetTempPath()) ('jujuba-retencao-prova-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($directory)
$day=[datetime]::new(1900,1,2)
$fixture=@'
SET NOCOUNT ON;
IF EXISTS(SELECT 1 FROM ctl.production_ingestion_run WHERE period_start='19000102') OR EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia WHERE dia='19000102')
 THROW 52390,'Data sintetica ocupada.',1;
DECLARE @catalogo TABLE(payload nvarchar(max)); INSERT @catalogo EXEC ctl.usp_jujuba_retencao_catalogo;
DECLARE @rev varchar(16)=(SELECT JSON_VALUE(payload,'$.revision') FROM @catalogo);
EXEC ctl.usp_jujuba_retencao_configurar 'OCORRENCIAS','own',90,@rev;
DECLARE @r uniqueidentifier=NEWID(), @ant uniqueidentifier=NEWID(), @outra uniqueidentifier=NEWID(),
 @d date='19000102', @s char(64)=REPLICATE('f',64),@h char(64)=REPLICATE('1',64),@j nvarchar(max)=N'{"origem":"prova","texto":"teste / aspas \" & ação","v":null}';
SET @j=JSON_MODIFY(@j,'$.detalhe',REPLICATE(CONVERT(nvarchar(max),NCHAR(28450)),5201)+N'  ');
INSERT ctl.production_ingestion_run(run_id,source_scope,entity_name,period_start,period_end,completed_utc,run_state,request_sha256,capture_sha256,row_count)
 VALUES(@r,@s,'OCORRENCIAS',@d,@d,SYSUTCDATETIME(),'STORED',@h,@h,2),(@ant,@s,'OCORRENCIAS',@d,@d,SYSUTCDATETIME(),'STORED',@h,@h,1),
 (@outra,@s,'COLETAS',@d,@d,SYSUTCDATETIME(),'STORED',@h,@h,1);
INSERT stg.production_export_occurrence VALUES(@r,1,@d,@j,HASHBYTES('SHA2_256',@j)),(@r,2,@d,@j,HASHBYTES('SHA2_256',@j)),
 (@ant,1,@d,@j,HASHBYTES('SHA2_256',@j)),(@outra,1,@d,@j,HASHBYTES('SHA2_256',@j));
INSERT ctl.production_ingestion_coverage VALUES(@s,'OCORRENCIAS',@d,@r,SYSUTCDATETIME()),(@s,'COLETAS',@d,@outra,SYSUTCDATETIME());
INSERT ctl.dashboard_performance_ocorrencias_particao VALUES(@s,@d,@r);
INSERT core.dashboard_performance_ocorrencias(escopo,data_particao,ordem,minuta,nota_fiscal,cte,codigo,descricao,ocorrido_em,filial,status_entrega)
 VALUES(@s,@d,1,8,N'0001',N'  ',2,N'prova',CAST('1900-01-02T01:02:03.456' AS datetime2(3)),NCHAR(28450),N'ok'),(@s,@d,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
-- Referência independente, obtida dos valores ainda nas tabelas de negócio.
SELECT SUM(n) AS bytes INTO #volumeEsperado FROM (
 SELECT DATALENGTH(run_id)+DATALENGTH(occurrence_ordinal)+DATALENGTH(business_date)+DATALENGTH(source_envelope)+DATALENGTH(envelope_sha256) AS n
 FROM stg.production_export_occurrence WHERE run_id IN (@r,@ant)
 UNION ALL SELECT DATALENGTH(escopo)+DATALENGTH(data_particao)+DATALENGTH(ordem)+ISNULL(DATALENGTH(minuta),0)+ISNULL(DATALENGTH(nota_fiscal),0)
  +ISNULL(DATALENGTH(cte),0)+ISNULL(DATALENGTH(codigo),0)+ISNULL(DATALENGTH(descricao),0)+ISNULL(DATALENGTH(ocorrido_em),0)
  +ISNULL(DATALENGTH(filial),0)+ISNULL(DATALENGTH(status_entrega),0) FROM core.dashboard_performance_ocorrencias WHERE escopo=@s AND data_particao=@d
 UNION ALL SELECT DATALENGTH(source_scope)+DATALENGTH(entity_name)+DATALENGTH(business_date)+DATALENGTH(latest_run_id)+DATALENGTH(stored_utc)
 FROM ctl.production_ingestion_coverage WHERE latest_run_id=@r
 UNION ALL SELECT DATALENGTH(escopo)+DATALENGTH(data_particao)+DATALENGTH(captura) FROM ctl.dashboard_performance_ocorrencias_particao WHERE captura=@r
) medicao;
IF ctl.ufn_jujuba_retencao_corte('20261125',90)<>'20260828' OR ctl.ufn_jujuba_retencao_corte('20261126',90)<>'20260829'
 OR ctl.ufn_jujuba_retencao_corte('20240301',2)<>'20240229' OR ctl.ufn_jujuba_retencao_corte(@d,0) IS NOT NULL
 THROW 52390,'Janela incorreta.',1;
INSERT ctl.jujuba_retencao_hold VALUES('OCORRENCIAS',@d,N'prova');
IF EXISTS(SELECT 1 FROM ctl.jujuba_retencao_elegiveis WHERE dia=@d) THROW 52390,'Protecao ignorada.',1;
DELETE ctl.jujuba_retencao_hold WHERE dia=@d AND entidade='OCORRENCIAS';
'@
try {
    $cn.Open()
    foreach ($negative in @($true,$false)) {
        $tx=$cn.BeginTransaction()
        $cmd=$cn.CreateCommand()
        $cmd.Transaction=$tx
        try {
            $cmd.CommandText=$fixture
            [void]$cmd.ExecuteNonQuery()
            if ($negative) {
                $cmd.CommandText="DECLARE @id uniqueidentifier=NEWID(),@h char(64)=REPLICATE('0',64); EXEC ctl.usp_jujuba_retencao_retirar 'OCORRENCIAS','19000102',@id,@h;"
                try { [void]$cmd.ExecuteNonQuery(); throw 'Uma assinatura incorreta foi aceita.' }
                catch { if ($_.Exception.GetBaseException().Number -ne 52307) { throw } }
            } else {
                $result=Invoke-RetentionDay $cn $day $directory $tx
                $file=Get-ChildItem -LiteralPath $directory -Filter '*.jsonl.gz' -File
                $medida=Measure-RetentionArchive $cn $file.FullName $tx
                $cmd.CommandText='SELECT bytes FROM #volumeEsperado'
                $esperado=$cmd.ExecuteScalar()
                if ($medida.assinatura -cne $result.assinatura -or $medida.bytes -ne $esperado -or $medida.origem -ne 3 -or
                    $medida.publicacao -ne 2 -or $medida.cobertura -ne 1 -or $medida.marcador -ne 1) {throw 'Medicao historica divergente.'}
                $stream=[IO.File]::OpenRead($file.FullName)
                $zip=[IO.Compression.GZipStream]::new($stream,[IO.Compression.CompressionMode]::Decompress)
                $reader=[IO.StreamReader]::new($zip,[Text.Encoding]::UTF8)
                try { $rows=@(while ($null -ne ($line=$reader.ReadLine())) { $line | ConvertFrom-Json }) }
                finally { $reader.Dispose(); $zip.Dispose(); $stream.Dispose() }
                $raw=@($rows | Where-Object tipo -eq 'origem')
                $public=@($rows | Where-Object tipo -eq 'publicacao')
                if ($rows.Count -ne 9 -or $raw.Count -ne 3 -or $public.Count -ne 2 -or
                    @($rows | Where-Object tipo -eq 'captura').Count -ne 2 -or
                    @($rows | Where-Object tipo -eq 'cobertura').Count -ne 1 -or
                    @($rows | Where-Object tipo -eq 'marcador').Count -ne 1) { throw 'Camada ausente no arquivo.' }
                $cmd.CommandText='CREATE TABLE #restauro(r uniqueidentifier,o int,original nvarchar(max),h char(64), PRIMARY KEY(r,o))'
                [void]$cmd.ExecuteNonQuery()
                foreach ($item in $raw) {
                    if ($item.source_envelope -isnot [string]) { throw 'O envelope perdeu a representacao original.' }
                    $cmd.CommandText='INSERT #restauro VALUES(@r,@o,@j,@h)'
                    [void]$cmd.Parameters.AddWithValue('@r',[guid]$item.run_id)
                    [void]$cmd.Parameters.AddWithValue('@o',[int]$item.occurrence_ordinal)
                    [void]$cmd.Parameters.AddWithValue('@j',$item.source_envelope)
                    [void]$cmd.Parameters.AddWithValue('@h',$item.envelope_sha256)
                    [void]$cmd.ExecuteNonQuery()
                    $cmd.Parameters.Clear()
                }
                $cmd.CommandText=@'
IF (SELECT COUNT(*) FROM #restauro)<>3 OR EXISTS(SELECT 1 FROM #restauro WHERE h<>CONVERT(char(64),HASHBYTES('SHA2_256',original),2))
 THROW 52390,'Recuperacao de envelopes divergente.',1;
DECLARE @bytes bigint=(SELECT bytes FROM #volumeEsperado);
DECLARE @p TABLE(payload nvarchar(max)); INSERT @p EXEC ctl.usp_jujuba_retencao_previa 'OCORRENCIAS',90;
IF NOT EXISTS(SELECT 1 FROM @p CROSS APPLY OPENJSON(payload,'$.days') WITH(dia date '$.day',bytes bigint '$.removedBytes',retirado bit '$.removed') a
 WHERE a.dia='19000102' AND a.retirado=1 AND a.bytes=@bytes) THROW 52390,'Volume da previa divergente.',1;
IF NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia WHERE entidade='OCORRENCIAS' AND dia='19000102' AND origem=3 AND vigentes=2 AND publicadas=2 AND bytes_apagados=@bytes)
 OR EXISTS(SELECT 1 FROM ctl.production_ingestion_run r JOIN stg.production_export_occurrence o ON r.run_id=o.run_id WHERE r.entity_name='OCORRENCIAS' AND r.period_start='19000102')
 OR EXISTS(SELECT 1 FROM core.dashboard_performance_ocorrencias WHERE data_particao='19000102')
 OR EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WHERE entity_name='OCORRENCIAS' AND business_date='19000102')
 OR EXISTS(SELECT 1 FROM ctl.dashboard_performance_ocorrencias_particao WHERE data_particao='19000102')
 OR NOT EXISTS(SELECT 1 FROM ctl.production_ingestion_run r JOIN stg.production_export_occurrence o ON o.run_id=r.run_id
    JOIN ctl.production_ingestion_coverage c ON c.latest_run_id=r.run_id WHERE r.entity_name='COLETAS' AND r.period_start='19000102')
 THROW 52390,'Escopo ou auditoria divergente.',1;
EXEC ctl.usp_jujuba_retencao_retirar 'OCORRENCIAS','19000102',@arquivo,@assinatura;
'@
                [void]$cmd.Parameters.AddWithValue('@arquivo',$result.arquivo)
                [void]$cmd.Parameters.AddWithValue('@assinatura',$result.assinatura)
                [void]$cmd.ExecuteNonQuery()
            }
        } finally { $cmd.Dispose(); if ($tx.Connection) { $tx.Rollback() }; $tx.Dispose() }
    }
    $cmd=$cn.CreateCommand()
    try {
        $cmd.CommandText="SELECT (SELECT COUNT(*) FROM ctl.production_ingestion_run WHERE period_start='19000102')+(SELECT COUNT(*) FROM ctl.jujuba_retencao_dia WHERE dia='19000102')"
        if ($cmd.ExecuteScalar() -ne 0) { throw 'O ensaio deixou dados na base.' }
    } finally { $cmd.Dispose() }
    'OK janela, protecao, assinatura, restauro, isolamento, idempotencia, rollback e bytes conferidos nas tabelas, no arquivo e na previa.'
} finally {
    $cn.Dispose(); $cred.Password.Dispose()
    foreach ($file in [IO.Directory]::EnumerateFiles($directory)) { [IO.File]::Delete($file) }
    [IO.Directory]::Delete($directory,$false)
}

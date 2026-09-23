-- ANA-03 / PUB-08: explicit, versioned projection; neither aliases nor grants are published.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE FUNCTION core.ufn_analytic_raster_time(@second BIGINT,@nano INT,@offset INT)
RETURNS DATETIMEOFFSET(7)
AS BEGIN
 IF @second IS NULL RETURN NULL;
 DECLARE @utc DATETIME2(7)=DATEADD(NANOSECOND,@nano-(@nano%100),
 DATEADD(SECOND,CONVERT(INT,@second%86400),DATEADD(DAY,CONVERT(INT,@second/86400),CONVERT(DATETIME2(7),'19700101'))));
 RETURN SWITCHOFFSET(TODATETIMEOFFSET(@utc,'+00:00'),@offset/60);
END;
GO
CREATE VIEW pub.analytic_lab_sql_13 AS
WITH base AS (
 SELECT v.run_id,v.trip_key,p.stop_key,v.observation_id trip_observation,p.observation_id stop_observation,
 v.cod_solicitacao,v.placa_veiculo,v.status_viagem,v.cnpj_cliente_orig,v.cnpj_cliente_dest,
 v.cod_ibge_cidade_orig,v.cod_ibge_cidade_dest,v.tempo_total_viagem,
 v.dentro_prazo dentro_prazo_raster,v.percentual_atraso percentual_atraso_raster,
 v.route_cod_rota cod_rota,v.route_descricao rota_descricao,v.link_timeline,
 p.ordem,p.tipo,p.cod_ibge_cidade parada_cidade,p.cnpj_cliente parada_cnpj,
 core.ufn_analytic_raster_time(v.data_hora_prev_ini,v.data_hora_prev_ini_nano,v.data_hora_prev_ini_offset) data_hora_prev_ini,
 core.ufn_analytic_raster_time(v.data_hora_prev_fim,v.data_hora_prev_fim_nano,v.data_hora_prev_fim_offset) data_hora_prev_fim,
 core.ufn_analytic_raster_time(p.data_hora_prev_chegada,p.data_hora_prev_chegada_nano,p.data_hora_prev_chegada_offset) data_hora_prev_chegada,
 core.ufn_analytic_raster_time(v.data_hora_real_ini,v.data_hora_real_ini_nano,v.data_hora_real_ini_offset) data_hora_real_ini,
 core.ufn_analytic_raster_time(v.data_hora_real_fim,v.data_hora_real_fim_nano,v.data_hora_real_fim_offset) data_hora_real_fim,
 core.ufn_analytic_raster_time(p.data_hora_real_chegada,p.data_hora_real_chegada_nano,p.data_hora_real_chegada_offset) data_hora_real_chegada,
 core.ufn_analytic_raster_time(p.data_hora_real_saida,p.data_hora_real_saida_nano,p.data_hora_real_saida_offset) data_hora_real_saida,
 v.extracted_at trip_extracted,p.extracted_at stop_extracted
 FROM core.analytic_raster_trip v
 LEFT JOIN core.analytic_raster_stop p ON p.run_id=v.run_id AND p.trip_key=v.trip_key AND p.active=1
 WHERE v.active=1
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_trip q JOIN ctl.analytic_raster_capture c ON c.capture_id=q.capture_id
 WHERE c.run_id=v.run_id AND q.trip_key=v.trip_key AND q.revision>=v.revision AND q.disposition='QUARANTINE')
 AND NOT EXISTS(SELECT 1 FROM stg.analytic_raster_stop q JOIN ctl.analytic_raster_capture c ON c.capture_id=q.capture_id
 WHERE c.run_id=p.run_id AND q.trip_key=p.trip_key AND q.stop_key=p.stop_key AND q.revision>=p.revision AND q.disposition='QUARANTINE')
),normalized AS (
 SELECT *,NULLIF(LTRIM(RTRIM(REPLACE(REPLACE(rota_descricao,N'/BRASIL',N''),N' ATE ',N'|'))),N'') route_text
 FROM base
),route_parts AS (
 SELECT *,CASE WHEN CHARINDEX(N'|',route_text)>0 AND CHARINDEX(N'|',route_text,CHARINDEX(N'|',route_text)+1)=0
 THEN NULLIF(LTRIM(RTRIM(LEFT(route_text,CHARINDEX(N'|',route_text)-1))),N'') END route_origin,
 CASE WHEN CHARINDEX(N'|',route_text)>0 AND CHARINDEX(N'|',route_text,CHARINDEX(N'|',route_text)+1)=0
 THEN NULLIF(LTRIM(RTRIM(SUBSTRING(route_text,CHARINDEX(N'|',route_text)+1,1024))),N'') END route_destination
 FROM normalized
),resolved AS (
 SELECT *,COALESCE(route_origin,NULLIF(cnpj_cliente_orig,N''),cod_ibge_cidade_orig) origem_sm,
 COALESCE(parada_cidade,route_destination,NULLIF(parada_cnpj,N''),NULLIF(cnpj_cliente_dest,N''),cod_ibge_cidade_dest) destino_sm,
 CASE WHEN route_origin IS NOT NULL THEN 'ROUTE_V1' WHEN NULLIF(cnpj_cliente_orig,N'') IS NOT NULL THEN 'TRIP_CNPJ' ELSE 'TRIP_CITY' END origin_provenance,
 CASE WHEN parada_cidade IS NOT NULL THEN 'STOP_CITY' WHEN route_destination IS NOT NULL THEN 'ROUTE_V1'
 WHEN NULLIF(parada_cnpj,N'') IS NOT NULL THEN 'STOP_CNPJ' WHEN NULLIF(cnpj_cliente_dest,N'') IS NOT NULL THEN 'TRIP_CNPJ' ELSE 'TRIP_CITY' END destination_provenance,
 DATEDIFF(MINUTE,data_hora_prev_ini,COALESCE(data_hora_prev_chegada,data_hora_prev_fim)) fallback_minutes,
 CASE WHEN stop_extracted>trip_extracted THEN stop_extracted ELSE trip_extracted END data_extracao_raster,
 CASE WHEN stop_extracted>trip_extracted THEN 'STOP' ELSE 'TRIP' END extraction_provenance
 FROM route_parts
),duration AS (
 SELECT *,CASE WHEN tempo_total_viagem BETWEEN 0 AND 43200 THEN tempo_total_viagem
 WHEN fallback_minutes BETWEEN 0 AND 43200 THEN fallback_minutes END transit_minutes,
 CASE WHEN tempo_total_viagem BETWEEN 0 AND 43200 THEN 'DIRECT'
 WHEN fallback_minutes BETWEEN 0 AND 43200 THEN 'PREDICTED_DIFFERENCE' ELSE 'INVALID_OR_ABSENT' END duration_provenance,
 CONVERT(BIT,CASE WHEN tempo_total_viagem IS NOT NULL AND tempo_total_viagem NOT BETWEEN 0 AND 43200 THEN 1 ELSE 0 END) invalid_direct_duration
 FROM resolved
),presentation AS (
 SELECT *,CONCAT(origem_sm,N' x ',destino_sm) origem_destino,
 CASE WHEN CHARINDEX(N'/',origem_sm)>0 THEN LTRIM(RTRIM(LEFT(origem_sm,CHARINDEX(N'/',origem_sm)-1)))
 WHEN CHARINDEX(N'-',origem_sm)>0 THEN LTRIM(RTRIM(LEFT(origem_sm,CHARINDEX(N'-',origem_sm)-1))) ELSE origem_sm END origem_nome,
 CASE WHEN CHARINDEX(N'/',destino_sm)>0 THEN LTRIM(RTRIM(LEFT(destino_sm,CHARINDEX(N'/',destino_sm)-1)))
 WHEN CHARINDEX(N'-',destino_sm)>0 THEN LTRIM(RTRIM(LEFT(destino_sm,CHARINDEX(N'-',destino_sm)-1))) ELSE destino_sm END destino_nome,
 CASE WHEN ordem IS NOT NULL THEN CONCAT(ordem,N'º') END ordem_parada_label,
 CONVERT(CHAR(5),CAST(data_hora_prev_ini AS TIME),108) horario_corte_texto,
 CONVERT(CHAR(5),CAST(COALESCE(data_hora_prev_chegada,data_hora_prev_fim) AS TIME),108) previsao_chegada_destino,
 CASE WHEN transit_minutes IS NOT NULL THEN CONCAT(
 CASE WHEN transit_minutes/60<100 THEN RIGHT('00'+CONVERT(VARCHAR(2),transit_minutes/60),2) ELSE CONVERT(VARCHAR(10),transit_minutes/60) END,
 ':',RIGHT('00'+CONVERT(VARCHAR(2),transit_minutes%60),2)) END transit_time_texto
 FROM duration
)
SELECT cod_solicitacao,placa_veiculo,status_viagem,
 origem_sm [ORIGEM - SM],destino_sm [DESTINO - SM],origem_destino [Origem x Destino],origem_nome [ORIGEM],
 ordem_parada_label [ORDEM],destino_nome [DESTINO],horario_corte_texto [HORÁRIO CORTE],
 previsao_chegada_destino [PREV. CHEGADA (destino)],transit_time_texto [TRANSIT TIME],
 origem_sm,destino_sm,origem_destino,origem_nome,ordem_parada_label,destino_nome,horario_corte_texto,
 previsao_chegada_destino,transit_time_texto,ordem ordem_parada,tipo tipo_parada,
 data_hora_prev_ini data_hora_prev_ini_raster,data_hora_prev_fim data_hora_prev_fim_raster,
 data_hora_prev_chegada data_hora_prev_chegada_parada,data_hora_real_ini,data_hora_real_fim,
 data_hora_real_chegada,data_hora_real_saida,dentro_prazo_raster,percentual_atraso_raster,
 cod_rota,rota_descricao,link_timeline,data_extracao_raster [Data de extracao],data_extracao_raster,
 run_id,trip_key,stop_key,trip_observation,stop_observation,transit_minutes,duration_provenance,
 origin_provenance,destination_provenance,extraction_provenance,invalid_direct_duration,
 cnpj_cliente_orig,COALESCE(NULLIF(parada_cnpj,N''),NULLIF(cnpj_cliente_dest,N'')) destination_cnpj,
 CONVERT(VARCHAR(32),'RASTER_ROUTE_V1') route_policy
FROM presentation;
GO

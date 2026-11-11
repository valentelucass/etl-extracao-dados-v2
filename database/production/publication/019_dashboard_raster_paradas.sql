CREATE OR ALTER VIEW pub.raster_viagem_paradas
AS
WITH raw_stops AS (
    SELECT r.source_key AS cod_solicitacao,r.observed_utc,CONVERT(int,p.[key])+1 AS ordinal,
           p.value AS metadata,COALESCE(TRY_CONVERT(int,JSON_VALUE(p.value,'$.Ordem')),CONVERT(int,p.[key])+1) AS ordem
    FROM core.dashboard_raster_vigente r
    CROSS APPLY OPENJSON(r.record_json,'$.ColetasEntregas') p
    WHERE p.[type]=5
), selected AS (
    -- The V1 MERGE overwrites a repeated (trip, order) with the last stop read.
    SELECT *,ROW_NUMBER() OVER(PARTITION BY cod_solicitacao,ordem ORDER BY ordinal DESC) AS row_rank
    FROM raw_stops
)
SELECT s.cod_solicitacao,s.ordem,
       CONVERT(nvarchar(5),TRIM(j.tipo)) AS tipo,
       j.cod_ibge_cidade,
       CONVERT(nvarchar(20),TRIM(j.cnpj_cliente)) AS cnpj_cliente,
       CONVERT(nvarchar(50),TRIM(j.codigo_cliente)) AS codigo_cliente,
       core.dashboard_raster_time(j.data_hora_prev_chegada) AS data_hora_prev_chegada,
       core.dashboard_raster_time(j.data_hora_prev_saida) AS data_hora_prev_saida,
       core.dashboard_raster_time(j.data_hora_real_chegada) AS data_hora_real_chegada,
       core.dashboard_raster_time(j.data_hora_real_saida) AS data_hora_real_saida,
       j.latitude,j.longitude,
       CONVERT(nvarchar(5),TRIM(j.dentro_prazo_raster)) AS dentro_prazo_raster,
       CONVERT(nvarchar(80),TRIM(j.diferenca_tempo_raster)) AS diferenca_tempo_raster,
       j.km_percorrido_entrega,j.km_restante_entrega,
       CONVERT(nvarchar(5),TRIM(j.chegou_na_entrega)) AS chegou_na_entrega,
       core.dashboard_raster_time(j.data_hora_ultima_posicao) AS data_hora_ultima_posicao,
       j.latitude_ultima_posicao,j.longitude_ultima_posicao,
       CONVERT(nvarchar(500),TRIM(j.referencia_ultima_posicao)) AS referencia_ultima_posicao,
       s.metadata,s.observed_utc AS data_extracao,
       CONVERT(bit,0) AS excluido_na_origem,CONVERT(datetime2(0),NULL) AS data_exclusao_origem
FROM selected s
CROSS APPLY OPENJSON(s.metadata) WITH(
    tipo nvarchar(max) '$.Tipo',
    cod_ibge_cidade bigint '$.CodIBGECidade',
    cnpj_cliente nvarchar(max) '$.CNPJCliente',
    codigo_cliente nvarchar(max) '$.CodigoCliente',
    data_hora_prev_chegada nvarchar(100) '$.DataHoraPrevChegada',
    data_hora_prev_saida nvarchar(100) '$.DataHoraPrevSaida',
    data_hora_real_chegada nvarchar(100) '$.DataHoraRealChegada',
    data_hora_real_saida nvarchar(100) '$.DataHoraRealSaida',
    latitude decimal(18,8) '$.Latitude',
    longitude decimal(18,8) '$.Longitude',
    dentro_prazo_raster nvarchar(max) '$.DentroPrazo',
    diferenca_tempo_raster nvarchar(max) '$.DiferencaTempo',
    km_percorrido_entrega decimal(18,3) '$.KmPercorridoEntrega',
    km_restante_entrega decimal(18,3) '$.KmRestanteEntrega',
    chegou_na_entrega nvarchar(max) '$.ChegouNaEntrega',
    data_hora_ultima_posicao nvarchar(100) '$.DataHoraUltimaPosicao',
    latitude_ultima_posicao decimal(18,8) '$.LatitudeUltimaPosicao',
    longitude_ultima_posicao decimal(18,8) '$.LongitudeUltimaPosicao',
    referencia_ultima_posicao nvarchar(max) '$.ReferenciaUltimaPosicao'
) j
WHERE s.row_rank=1;

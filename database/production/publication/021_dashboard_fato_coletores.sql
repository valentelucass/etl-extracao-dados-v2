-- The contribution types and branch aliases follow the V1 collectors procedure.
CREATE OR ALTER VIEW pub.fato_gestao_vista_coletores
AS
WITH source_scope AS (
    SELECT MAX(stored_utc) AS snapshot_utc
    FROM ctl.production_ingestion_coverage
    WHERE entity_name IN ('FRETES','MANIFESTOS','INVENTARIO')
    HAVING COUNT(DISTINCT entity_name)=3 AND COUNT(DISTINCT source_scope)=1
), filiais_operacionais AS (
    SELECT b.code,CONVERT(nvarchar(255),CONCAT(b.code,N' - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA')) AS nome
    FROM (VALUES(N'AGU'),(N'CAS'),(N'CPQ'),(N'CWB'),(N'NHB'),(N'REC'),(N'RJR'),(N'SPO')) b(code)
), filiais_alias AS (
    SELECT a.alias_key,fo.nome
    FROM filiais_operacionais fo
    CROSS APPLY (VALUES(fo.code),(fo.nome),(CONCAT(N'TR RODOGARCIA | ',fo.code)),
        (CONCAT(N'RODOGARCIA FILIAL ',fo.code))) a(alias_key)
), fretes_filial AS (
    SELECT j.minuta,j.filial,
           CONVERT(datetime2(7),TODATETIMEOFFSET(l.stored_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS updated_at,
           ROW_NUMBER() OVER(PARTITION BY j.minuta ORDER BY l.stored_utc DESC,j.id DESC,l.business_date DESC,l.occurrence_ordinal DESC) AS row_rank
    FROM stg.production_export_latest l
    CROSS JOIN source_scope
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH(
        minuta bigint '$.corporation_sequence_number',
        filial nvarchar(255) '$.fit_crn_psn_nickname',
        id bigint '$.id'
    ) j
    WHERE l.entity_name='FRETES' AND j.minuta IS NOT NULL
), manifestos_fonte AS (
    SELECT j.manifesto,CONVERT(date,j.created_at) AS data_referencia,
           REPLACE(REPLACE(REPLACE(COALESCE(j.destinos,j.destino),N'[',N''),N']',N''),N'"',N'') AS destinos,
           COALESCE(a.nome,NULLIF(TRIM(j.filial),N''),N'Filial nao informada') AS filial,
           CONVERT(datetime2(7),TODATETIMEOFFSET(l.stored_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS updated_at,
           ROW_NUMBER() OVER(PARTITION BY j.manifesto ORDER BY l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal DESC) AS row_rank
    FROM stg.production_export_latest l
    CROSS JOIN source_scope
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH(
        manifesto bigint '$.sequence_code',
        created_at datetimeoffset(7) '$.created_at',
        classificacao nvarchar(255) '$.mft_man_name',
        filial nvarchar(255) '$.mft_crn_psn_nickname',
        destinos nvarchar(max) '$.mft_mte_unloading_recipient_names' AS JSON,
        destino nvarchar(max) '$.mft_mte_unloading_recipient_names'
    ) j
    LEFT JOIN filiais_alias a ON a.alias_key COLLATE Latin1_General_CI_AI=TRIM(j.filial) COLLATE Latin1_General_CI_AI
    WHERE l.entity_name='MANIFESTOS' AND j.manifesto IS NOT NULL AND j.created_at IS NOT NULL
      AND (j.classificacao IS NULL OR (
          TRIM(j.classificacao) COLLATE Latin1_General_CI_AI NOT LIKE N'carga fechada%'
      AND TRIM(j.classificacao) COLLATE Latin1_General_CI_AI NOT LIKE N'acerto de motorista%'
      AND TRIM(j.classificacao) COLLATE Latin1_General_CI_AI NOT LIKE N'frete retorno%'
      AND TRIM(j.classificacao) COLLATE Latin1_General_CI_AI NOT LIKE N'viagem vazia%'))
), descarregamentos AS (
    SELECT m.manifesto,m.data_referencia,m.updated_at,COALESCE(a.nome,n.parte) AS filial,
           ROW_NUMBER() OVER(PARTITION BY m.manifesto ORDER BY COALESCE(a.nome,n.parte) COLLATE Latin1_General_CI_AI) AS row_rank
    FROM manifestos_fonte m
    CROSS APPLY STRING_SPLIT(REPLACE(REPLACE(REPLACE(m.destinos,CHAR(13),N';'),CHAR(10),N';'),N',',N';'),N';') s
    CROSS APPLY (VALUES(NULLIF(TRIM(CONVERT(nvarchar(255),s.value)),N''))) n(parte)
    LEFT JOIN filiais_alias a ON a.alias_key COLLATE Latin1_General_CI_AI=n.parte COLLATE Latin1_General_CI_AI
    WHERE m.row_rank=1 AND n.parte COLLATE Latin1_General_CI_AI<>N'null'
), ordens AS (
    SELECT i.[N° Ordem] AS ordem,CONVERT(date,i.[Data/Hora início]) AS data_referencia,
           COALESCE(a.nome,b.nome,N'Filial nao informada') AS filial,
           CONVERT(bigint,CASE WHEN i.[Data/Hora fim] IS NULL THEN 1 ELSE 0 END) AS incompleta,
           GREATEST(i.[Data de extracao],f.updated_at) AS updated_at,
           ROW_NUMBER() OVER(PARTITION BY i.[N° Ordem] ORDER BY GREATEST(i.[Data de extracao],f.updated_at) DESC,
               i.[Identificador Único] DESC) AS row_rank
    FROM pub.vw_inventario_powerbi i
    CROSS JOIN source_scope
    LEFT JOIN fretes_filial f ON f.minuta=i.[Nº Minuta] AND f.row_rank=1
    CROSS APPLY(VALUES(COALESCE(NULLIF(TRIM(i.[Filial da Ordem de Conferência]),N''),NULLIF(TRIM(f.filial),N'')))) b(nome)
    LEFT JOIN filiais_alias a ON a.alias_key COLLATE Latin1_General_CI_AI=b.nome COLLATE Latin1_General_CI_AI
    WHERE i.[N° Ordem] IS NOT NULL AND i.[Data/Hora início] IS NOT NULL
      AND TRIM(i.[Tipo]) COLLATE Latin1_General_CI_AI IN(N'Carregamento',N'Descarregamento',N'Picking',N'Recebimento',N'Retorno')
), contribuicoes AS (
    SELECT data_referencia,filial,updated_at,CONVERT(bigint,0) AS bipados,
           CONVERT(bigint,1) AS emitidos,CONVERT(bigint,0) AS descarregamentos,CONVERT(bigint,0) AS incompletos
    FROM manifestos_fonte WHERE row_rank=1
    UNION ALL
    SELECT data_referencia,filial,updated_at,0,0,1,0 FROM descarregamentos WHERE row_rank=1
    UNION ALL
    SELECT data_referencia,filial,updated_at,1,0,0,incompleta FROM ordens WHERE row_rank=1
), agregado AS (
    SELECT data_referencia,CONVERT(nvarchar(255),filial) AS filial,
           LOWER(CONVERT(nvarchar(255),filial)) COLLATE Latin1_General_CI_AI AS filial_key,
           SUM(bipados) AS manifestos_bipados,SUM(emitidos) AS manifestos_emitidos,
           SUM(descarregamentos) AS manifestos_descarregamento,SUM(incompletos) AS manifestos_incompletos,
           SUM(emitidos)+SUM(descarregamentos) AS total_manifestos,MAX(updated_at) AS updated_at
    FROM contribuicoes GROUP BY data_referencia,filial
), metricas AS (
    SELECT a.*,YEAR(a.data_referencia)*100+MONTH(a.data_referencia) AS data_referencia_yyyymm,
           CONVERT(nvarchar(50),N'Geral') AS classificacao,
           CONVERT(decimal(9,4),COALESCE(CONVERT(decimal(19,4),a.manifestos_bipados)*100/
               NULLIF(CONVERT(decimal(19,4),a.total_manifestos),0),0)) AS pct_utilizacao,
           CONVERT(bit,CASE WHEN a.manifestos_bipados>0 THEN 1 ELSE 0 END) AS tem_ordens_conferencia,
           CONVERT(bit,CASE WHEN a.total_manifestos>0 THEN 1 ELSE 0 END) AS tem_manifestos_bipaveis,
           CONVERT(bit,CASE WHEN f.code IS NOT NULL THEN 1 ELSE 0 END) AS is_filial_operacional,
           CONVERT(datetime2(0),s.snapshot_utc) AS snapshot_em
    FROM agregado a CROSS JOIN source_scope s LEFT JOIN filiais_operacionais f ON f.nome=a.filial
)
SELECT m.data_referencia,m.data_referencia_yyyymm,m.filial,m.filial_key,m.classificacao,
       m.manifestos_bipados,m.manifestos_emitidos,m.manifestos_descarregamento,m.total_manifestos,m.manifestos_incompletos,
       m.pct_utilizacao,m.tem_ordens_conferencia,m.tem_manifestos_bipaveis,m.is_filial_operacional,
       CONVERT(bit,1) AS is_linha_valida_indicador,CONVERT(bit,0) AS excluido_na_origem,
       CONVERT(datetime2(0),NULL) AS data_exclusao_origem,m.snapshot_em AS ultima_reconciliacao_origem_em,
       CONVERT(datetime2(0),m.updated_at) AS updated_at,m.snapshot_em,
       CONVERT(binary(32),HASHBYTES('SHA2_256',CONCAT_WS(N'|',
           CONVERT(nvarchar(30),m.data_referencia,126),m.data_referencia_yyyymm,m.filial,m.filial_key,m.classificacao,
           m.manifestos_bipados,m.manifestos_emitidos,m.manifestos_descarregamento,m.total_manifestos,m.manifestos_incompletos,
           CONVERT(nvarchar(30),m.pct_utilizacao),m.tem_ordens_conferencia,m.tem_manifestos_bipaveis,m.is_filial_operacional,
           1,0,COALESCE(CONVERT(nvarchar(30),m.updated_at,126),N'__NULL__')))) AS hash_linha
FROM metricas m;

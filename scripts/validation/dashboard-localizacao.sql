SET NOCOUNT ON;

SELECT TOP(0) * INTO #published FROM pub.vw_localizacao_cargas_powerbi;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #published SELECT * FROM pub.vw_localizacao_cargas_powerbi;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

IF EXISTS(SELECT 1 FROM ETL_SISTEMA.dbo.localizacao_cargas WHERE status_branch_nickname IS NOT NULL)
   OR EXISTS(SELECT 1 FROM #published WHERE [Filial Atual] IS NOT NULL)
    THROW 51981,'DASHBOARD_LOCALIZACAO_BRANCH_SOURCE_CHANGED',1;

SELECT JSON_QUERY(l.source_envelope,'$.record') AS record_json,
       CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.corporation_sequence_number')) AS freight_number
INTO #source
FROM stg.production_export_latest l WHERE l.entity_name='LOCALIZACAO_CARGAS';
CREATE UNIQUE CLUSTERED INDEX ix_tracking_source ON #source(freight_number);
DECLARE @source_rows bigint=(SELECT COUNT_BIG(*) FROM #source);
IF @source_rows=0 OR @source_rows<>(SELECT COUNT_BIG(*) FROM #published)
   OR @source_rows<>(SELECT COUNT_BIG(DISTINCT [N° Minuta]) FROM #published)
   OR EXISTS (
       SELECT 1 FROM #source s FULL JOIN #published p ON s.freight_number=p.[N° Minuta]
       WHERE p.[N° Minuta] IS NULL OR s.freight_number IS NULL
          OR HASHBYTES('SHA2_256',CONVERT(varbinary(max),s.record_json))
             <>HASHBYTES('SHA2_256',CONVERT(varbinary(max),p.[Metadata]))
   )
    THROW 51982,'DASHBOARD_LOCALIZACAO_ROWS_DIFFER',1;

IF EXISTS (
    SELECT 1 FROM #published p
    LEFT JOIN ETL_SISTEMA.dbo.localizacao_cargas_regiao_destino_alias a
      ON a.nome_responsavel=p.[Filial Destino] COLLATE Latin1_General_CI_AS AND a.ativo=1
    WHERE p.[Sigla Responsável Região Destino] COLLATE Latin1_General_CI_AS<>COALESCE(a.sigla,N'SEM_MAP')
       OR NOT EXISTS (
            SELECT p.[Data do frete],p.[Volumes],p.[Peso Taxado Decimal],p.[Valor NF Decimal],p.[Valor Frete],
                   p.[Classificação],p.[Localização Atual]
            INTERSECT SELECT
                CONVERT(datetimeoffset,JSON_VALUE(p.[Metadata],'$.service_at')),
                CONVERT(int,JSON_VALUE(p.[Metadata],'$.invoices_volumes')),
                TRY_CONVERT(decimal(18,3),NULLIF(LTRIM(RTRIM(REPLACE(JSON_VALUE(p.[Metadata],'$.taxed_weight'),',',''))),'')),
                TRY_CONVERT(decimal(18,2),NULLIF(LTRIM(RTRIM(REPLACE(JSON_VALUE(p.[Metadata],'$.invoices_value'),',',''))),'')),
                CONVERT(decimal(18,2),JSON_VALUE(p.[Metadata],'$.total')),
                JSON_VALUE(p.[Metadata],'$.fit_fsn_name'),
                JSON_VALUE(p.[Metadata],'$.fit_fln_cln_nickname')
       )
) THROW 51983,'DASHBOARD_LOCALIZACAO_MEASURES_OR_ALIASES_DIFFER',1;

SELECT @source_rows AS source_rows, COUNT_BIG(*) AS published_rows,
       SUM([Valor Frete]) AS freight_value, SUM([Valor NF Decimal]) AS invoice_value,
       SUM(CONVERT(bigint,[Volumes])) AS volumes, SUM(CONVERT(bigint,[Status Terminal])) AS terminal_count,
       SUM(CONVERT(bigint,[Cancelado Flag])) AS cancelled_count
FROM #published FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER;

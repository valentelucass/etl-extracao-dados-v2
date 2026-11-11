SET NOCOUNT ON;
SELECT TOP(0) * INTO #details FROM pub.vw_faturas_por_cliente_powerbi;
SELECT TOP(0) * INTO #facts FROM pub.fato_gestao_vista_faturas;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #details SELECT * FROM pub.vw_faturas_por_cliente_powerbi;
    INSERT #facts SELECT * FROM pub.fato_gestao_vista_faturas;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_faturas_detail ON #details([ID Único]);
CREATE UNIQUE CLUSTERED INDEX ix_faturas_fact ON #facts(unique_id);

-- VALIDATION BATCH
SELECT JSON_QUERY(l.source_envelope,'$.record') AS record_json,
       HASHBYTES('SHA2_256',CONVERT(varbinary(max),JSON_QUERY(l.source_envelope,'$.record'))) AS fingerprint
INTO #receipts
FROM stg.production_export_latest l WHERE l.entity_name='FATURAS_POR_CLIENTE';
CREATE INDEX ix_faturas_receipt ON #receipts(fingerprint);
IF NOT EXISTS(SELECT 1 FROM #receipts) OR NOT EXISTS(SELECT 1 FROM #details)
   OR EXISTS (
       SELECT 1 FROM #details d
       WHERE NOT EXISTS (SELECT 1 FROM #receipts r
            WHERE r.fingerprint=HASHBYTES('SHA2_256',CONVERT(varbinary(max),d.[Metadata])))
   )
   OR EXISTS(SELECT 1 FROM #details WHERE [ID Único] NOT LIKE N'FPC-HASH-%' OR LEN([ID Único])<>73)
    THROW 51985,'DASHBOARD_FATURAS_SOURCE_LINK_MISMATCH',1;

DECLARE @bounds TABLE(field_name nvarchar(100) PRIMARY KEY,max_bytes int);
INSERT @bounds VALUES
    (N'fit_crn_psn_nickname',510),(N'fit_diy_sae_name',100),(N'fit_fhe_cte_key',200),
    (N'type',220),(N'fit_fsn_name',200),(N'fit_pyr_name',510),(N'fit_pyr_document',100),
    (N'fit_rpt_name',510),(N'fit_rpt_document',100),(N'fit_sdr_name',510),(N'fit_sdr_document',100),
    (N'fit_sps_slr_psn_name',510),(N'fit_ant_document',100);
IF EXISTS (
    SELECT 1 FROM #receipts r CROSS APPLY OPENJSON(r.record_json) j
    JOIN @bounds b ON j.[key] COLLATE DATABASE_DEFAULT=b.field_name
    WHERE DATALENGTH(j.value)>b.max_bytes
) THROW 51986,'DASHBOARD_FATURAS_LEGACY_WIDTH_EXCEEDED',1;

IF EXISTS (
    SELECT 1 FROM #details d
    CROSS APPLY(VALUES(
       JSON_VALUE(d.[Metadata],'$.total'),
       JSON_VALUE(d.[Metadata],'$.fit_ant_value'),
       JSON_VALUE(d.[Metadata],'$.third_party_ctes_value')
    )) amounts(freight,invoice,third_party)
    WHERE NOT EXISTS (
      SELECT d.[Frete/Valor dos CT-es], d.[Fatura/Valor Total], d.[Terceiros/Valor CT-es],
             d.[Status do Processo], d.[Fatura/Emissão], d.[Parcelas/Vencimento], d.[Fatura/Baixa]
      INTERSECT SELECT
        TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(amounts.freight,',','')),'')),
        TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(amounts.invoice,',','')),'')),
        TRY_CONVERT(decimal(18,2),amounts.third_party),
        CASE WHEN JSON_VALUE(d.[Metadata],'$.fit_ant_document') IS NULL THEN 'Aguardando Faturamento' ELSE 'Faturado' END,
        CONVERT(nvarchar(10),TRY_CONVERT(date,JSON_VALUE(d.[Metadata],'$.fit_ant_issue_date')),23),
        CONVERT(nvarchar(10),TRY_CONVERT(date,JSON_VALUE(d.[Metadata],'$.fit_ant_ils_due_date')),23),
        CONVERT(nvarchar(10),TRY_CONVERT(date,JSON_VALUE(d.[Metadata],'$.fit_ant_ils_atn_transaction_date')),23)
    )
) THROW 51987,'DASHBOARD_FATURAS_SOURCE_VALUES_DIFFER',1;

-- VALIDATION BATCH
IF EXISTS (
   SELECT 1 FROM #details d FULL JOIN #facts f ON f.unique_id=d.[ID Único]
   WHERE f.unique_id IS NULL OR d.[ID Único] IS NULL OR f.excluido_na_origem<>0 OR f.snapshot_em IS NULL
      OR NOT EXISTS (
        SELECT f.valor_frete, f.valor_fatura, f.valor_fit_ant, f.third_party_ctes_value, f.valor_operacional,
               f.numero_cte, f.numero_nfse, f.status_processo, f.status_pagamento, f.data_referencia_mensal_yyyymm
        INTERSECT SELECT
          CONVERT(decimal(19,4),d.[Frete/Valor dos CT-es]),CONVERT(decimal(19,4),d.[Fatura/Valor Total]),
          CONVERT(decimal(19,4),d.[Fatura/Valor]),CONVERT(decimal(19,4),d.[Terceiros/Valor CT-es]),
          CONVERT(decimal(19,4),COALESCE(d.[Fatura/Valor],d.[Fatura/Valor Total],d.[Frete/Valor dos CT-es],0)),
          d.[CT-e/Número],d.[NFS-e/Número],
          CASE WHEN f.documento_fatura IS NULL THEN N'Aguardando Faturamento' ELSE N'Faturado' END,
          CASE WHEN f.documento_fatura IS NULL THEN N'sem_fatura'
               WHEN TRY_CONVERT(date,d.[Fatura/Baixa]) IS NOT NULL THEN N'baixado'
               WHEN TRY_CONVERT(date,d.[Parcelas/Vencimento]) IS NULL THEN N'sem_vencimento'
               WHEN TRY_CONVERT(date,d.[Parcelas/Vencimento])<CONVERT(date,f.snapshot_em) THEN N'vencido' ELSE N'a_vencer' END,
          YEAR(COALESCE(TRY_CONVERT(date,d.[Fatura/Emissão]),CONVERT(date,d.[CT-e/Data de emissão])))*100
           +MONTH(COALESCE(TRY_CONVERT(date,d.[Fatura/Emissão]),CONVERT(date,d.[CT-e/Data de emissão])))
      )
) THROW 51988,'DASHBOARD_FATURAS_FACT_AND_DETAIL_DIFFER',1;

SELECT (SELECT COUNT_BIG(*) FROM #receipts) AS captured_occurrences, COUNT_BIG(*) AS documents,
       SUM(f.valor_frete) AS freight_total,SUM(f.valor_fatura) AS invoice_total,
       SUM(f.valor_operacional) AS operational_total,
       COUNT_BIG(CASE WHEN f.status_processo=N'Faturado' THEN 1 END) AS billed_rows,
       COUNT_BIG(CASE WHEN f.numero_cte IS NOT NULL THEN 1 END) AS cte_rows,
       COUNT_BIG(CASE WHEN f.numero_nfse IS NOT NULL THEN 1 END) AS nfse_rows,
       COUNT_BIG(v.unique_id) AS keys_also_present_in_v1
FROM #facts f
LEFT JOIN ETL_SISTEMA.dbo.faturas_por_cliente v ON v.unique_id=f.unique_id COLLATE Latin1_General_CI_AS
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

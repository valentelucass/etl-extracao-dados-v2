SET NOCOUNT ON;
SELECT TOP(0) * INTO #payables FROM pub.vw_contas_a_pagar_powerbi;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #payables SELECT * FROM pub.vw_contas_a_pagar_powerbi;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_payables_key ON #payables([Lançamento a Pagar/N°]);

-- VALIDATION BATCH
SELECT JSON_QUERY(l.source_envelope,'$.record') AS raw_record,
       TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.ant_ils_sequence_code')) AS sequence_code,
       l.stored_utc,l.business_date,l.occurrence_ordinal
INTO #captured FROM stg.production_export_latest l WHERE l.entity_name='CONTAS_A_PAGAR';
IF NOT EXISTS (SELECT 1 FROM #captured) OR EXISTS(SELECT 1 FROM #captured WHERE sequence_code IS NULL)
   OR (SELECT COUNT_BIG(DISTINCT sequence_code) FROM #captured)<>(SELECT COUNT_BIG(*) FROM #payables)
    THROW 51989,'DASHBOARD_PAYABLE_KEYS_DIFFER',1;
CREATE INDEX ix_captured_payables ON #captured(sequence_code);

DECLARE @bounds TABLE(field_name nvarchar(100) PRIMARY KEY,max_bytes int);
INSERT @bounds VALUES
    (N'document',200),(N'type',200),(N'ant_rir_name',510),(N'ant_crn_psn_nickname',510),
    (N'ant_ils_pas_ant_classification',200),(N'ant_ils_pas_ant_name',510),(N'ant_ces_acr_name',510),
    (N'ant_aln_name',510),(N'ant_uer_name',510);
IF EXISTS (
    SELECT 1 FROM #captured c CROSS APPLY OPENJSON(c.raw_record) j
    JOIN @bounds b ON j.[key] COLLATE DATABASE_DEFAULT=b.field_name
    WHERE DATALENGTH(j.value)>b.max_bytes
      OR (b.field_name=N'document' AND CONVERT(nvarchar(max),CONVERT(varchar(100),j.value)) COLLATE Latin1_General_100_BIN2<>j.value)
) THROW 51990,'DASHBOARD_PAYABLE_LEGACY_WIDTH_EXCEEDED',1;

IF EXISTS (
    SELECT 1 FROM #payables p
    OUTER APPLY (
        SELECT TOP(1) c.raw_record FROM #captured c
        CROSS APPLY (VALUES(
            TRY_CONVERT(datetime2(3),TRY_CONVERT(datetimeoffset(7),JSON_VALUE(c.raw_record,'$.created_at'))),
            TRY_CONVERT(date,JSON_VALUE(c.raw_record,'$.ant_ils_atn_liquidation_date')),
            TRY_CONVERT(date,JSON_VALUE(c.raw_record,'$.ant_ils_atn_transaction_date'))
        )) dates(created_at,liquidated_at,transacted_at)
        WHERE c.sequence_code=p.[Lançamento a Pagar/N°]
        ORDER BY GREATEST(dates.created_at,DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),dates.liquidated_at))),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),dates.transacted_at)))) DESC,
            c.stored_utc DESC,c.business_date DESC,c.occurrence_ordinal DESC
    ) expected
    WHERE expected.raw_record IS NULL OR HASHBYTES('SHA2_256',expected.raw_record)<>HASHBYTES('SHA2_256',p.[Metadata])
) THROW 51991,'DASHBOARD_PAYABLE_FRESHNESS_DIFFERS',1;

-- VALIDATION BATCH
IF EXISTS (
    SELECT 1 FROM #payables p
    WHERE NOT EXISTS (
        SELECT p.[Emissão],p.[Baixa/Data liquidação],p.[Data transação],p.[Status Pagamento],p.[Conciliado],
               p.[Valor],p.[Juros],p.[Desconto],p.[Valor a pagar],p.[Valor pago],p.[Conta Contábil/Valor],p.[Centro de custo/Valor]
        INTERSECT SELECT
           TRY_CONVERT(date,JSON_VALUE(p.[Metadata],'$.issue_date')),
           TRY_CONVERT(date,JSON_VALUE(p.[Metadata],'$.ant_ils_atn_liquidation_date')),
           TRY_CONVERT(date,JSON_VALUE(p.[Metadata],'$.ant_ils_atn_transaction_date')),
           CASE WHEN JSON_VALUE(p.[Metadata],'$.paid') IN (N'true',N'1') THEN N'PAGO' ELSE N'ABERTO' END,
           CASE WHEN JSON_VALUE(p.[Metadata],'$.ant_ils_atn_reconciled') IN (N'true',N'1') THEN 'Conciliado'
                WHEN JSON_VALUE(p.[Metadata],'$.ant_ils_atn_reconciled') IN (N'false',N'0') THEN 'Não conciliado' END,
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.value'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.interest_value'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.discount_value'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.value_to_pay'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.paid_value'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.ant_ils_pas_value'),'R$',''),',','')),'')),
           TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(JSON_VALUE(p.[Metadata],'$.ant_ces_value'),'R$',''),',','')),''))
    )
) THROW 51992,'DASHBOARD_PAYABLE_SOURCE_VALUES_DIFFER',1;

SELECT (SELECT COUNT_BIG(*) FROM #captured) AS captured_occurrences, COUNT_BIG(*) AS payables,
       SUM(p.[Valor]) AS original_total,SUM(p.[Valor a pagar]) AS payable_total,SUM(p.[Valor pago]) AS paid_total,
       SUM(p.[Conta Contábil/Valor]) AS accounting_total,SUM(p.[Centro de custo/Valor]) AS cost_center_total,
       COUNT_BIG(CASE WHEN p.[Status Pagamento]=N'PAGO' THEN 1 END) AS paid_count,
       COUNT_BIG(CASE WHEN p.[Conciliado]='Conciliado' THEN 1 END) AS reconciled_count,
       COUNT_BIG(old.sequence_code) AS keys_also_present_in_v1
FROM #payables p
LEFT JOIN ETL_SISTEMA.dbo.contas_a_pagar old ON old.sequence_code=p.[Lançamento a Pagar/N°] AND COALESCE(old.excluido_na_origem,0)=0
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

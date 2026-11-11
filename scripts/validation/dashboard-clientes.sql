SET NOCOUNT ON;
SELECT TOP(0) * INTO #clients FROM pub.vw_dim_clientes;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #clients SELECT * FROM pub.vw_dim_clientes;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_dashboard_clientes ON #clients(Nome);

-- VALIDATION BATCH
SELECT l.entity_name,j.source_id,l.stored_utc,l.business_date,l.occurrence_ordinal,l.source_envelope
INTO #client_source
FROM stg.production_export_latest l
CROSS APPLY (VALUES(TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')))) j(source_id)
WHERE l.entity_name IN ('COLETAS','FRETES');
IF EXISTS (SELECT 1 FROM #client_source WHERE source_id IS NULL)
   OR (SELECT COUNT(DISTINCT entity_name) FROM #client_source)<>2
    THROW 51993,'DASHBOARD_CLIENT_INPUT_REQUIRED',1;
CREATE INDEX ix_client_source ON #client_source(entity_name,source_id,stored_utc,business_date,occurrence_ordinal);

WITH identifiers AS (
    SELECT DISTINCT entity_name,source_id FROM #client_source
)
SELECT i.entity_name,p.value AS observed_name
INTO #expected_clients
FROM identifiers i
CROSS APPLY (
    SELECT TOP(1) s.source_envelope FROM #client_source s
    WHERE s.entity_name=i.entity_name AND s.source_id=i.source_id
    ORDER BY s.stored_utc DESC,s.business_date DESC,s.occurrence_ordinal DESC
) current_row
CROSS APPLY OPENJSON(current_row.source_envelope,'$.record') p
WHERE (i.entity_name='COLETAS' AND p.[key]=N'pck_cor_nickname')
   OR (i.entity_name='FRETES' AND p.[key] IN (N'fit_pyr_name',N'fit_rpt_nickname',N'fit_sdr_nickname'));
INSERT #expected_clients
SELECT 'FATURAS_POR_CLIENTE',p.value
FROM pub.vw_faturas_por_cliente_powerbi f CROSS APPLY OPENJSON(f.Metadata) p
WHERE p.[key] IN (N'fit_pyr_name',N'fit_rpt_name',N'fit_sdr_name');

IF (SELECT COUNT(DISTINCT entity_name) FROM #expected_clients WHERE TRIM(observed_name)<>N'')<>3
   OR EXISTS(SELECT 1 FROM #expected_clients WHERE DATALENGTH(TRIM(observed_name))>510)
    THROW 51994,'DASHBOARD_CLIENT_NAMES_OR_WIDTH_INVALID',1;
SELECT DISTINCT CONVERT(nvarchar(255),UPPER(TRIM(observed_name COLLATE Latin1_General_CI_AS))) AS Nome
INTO #client_names FROM #expected_clients WHERE TRIM(observed_name)<>N'';
IF EXISTS(SELECT Nome FROM #client_names EXCEPT SELECT Nome FROM #clients)
   OR EXISTS(SELECT Nome FROM #clients EXCEPT SELECT Nome FROM #client_names)
    THROW 51995,'DASHBOARD_CLIENT_NAMES_DIFFER',1;

SELECT COUNT_BIG(*) AS distinct_names,
       (SELECT COUNT_BIG(*) FROM #expected_clients WHERE entity_name='COLETAS' AND TRIM(observed_name)<>N'') AS collection_roles,
       (SELECT COUNT_BIG(*) FROM #expected_clients WHERE entity_name='FRETES' AND TRIM(observed_name)<>N'') AS freight_roles,
       (SELECT COUNT_BIG(*) FROM #expected_clients WHERE entity_name='FATURAS_POR_CLIENTE' AND TRIM(observed_name)<>N'') AS invoice_roles,
       COUNT_BIG(old.Nome) AS names_also_present_in_v1
FROM #clients c LEFT JOIN ETL_SISTEMA.dbo.vw_dim_clientes old ON old.Nome=c.Nome
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

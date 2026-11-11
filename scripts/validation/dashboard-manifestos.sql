SET NOCOUNT ON;
IF EXISTS(SELECT 1 FROM ref.frota_propria_documento)
   OR EXISTS(SELECT 1 FROM ETL_SISTEMA.dbo.manifestos_frota_propria_cnpjs WHERE ativo=1)
    THROW 52025,'DASHBOARD_MANIFESTO_FLEET_RULES_REQUIRE_BINDING',1;
SELECT TOP(0) * INTO #details FROM pub.vw_manifestos_powerbi;
SELECT TOP(0) * INTO #facts FROM pub.vw_fato_manifestos_dash;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #details SELECT * FROM pub.vw_manifestos_powerbi;
    INSERT #facts SELECT * FROM pub.vw_fato_manifestos_dash;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_manifest_detail ON #details([Número]);
CREATE UNIQUE CLUSTERED INDEX ix_manifest_fact ON #facts([Número]);

-- VALIDATION BATCH
SELECT TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.sequence_code')) AS manifest_number,
       JSON_QUERY(l.source_envelope,'$.record') AS record_json
INTO #captured FROM stg.production_export_latest l WHERE l.entity_name='MANIFESTOS';
CREATE INDEX ix_manifest_capture ON #captured(manifest_number);
IF NOT EXISTS(SELECT 1 FROM #captured) OR NOT EXISTS(SELECT 1 FROM #details)
   OR EXISTS(SELECT 1 FROM #captured WHERE manifest_number IS NULL)
   OR (SELECT COUNT_BIG(*) FROM #details)<>(SELECT COUNT_BIG(*) FROM #facts)
   OR EXISTS(SELECT [Número] FROM #details EXCEPT SELECT [Número] FROM #facts)
   OR EXISTS(SELECT 1 FROM #details d WHERE NOT EXISTS(
       SELECT 1 FROM #captured c WHERE c.manifest_number=d.[Número]
         AND HASHBYTES('SHA2_256',c.record_json)=HASHBYTES('SHA2_256',d.[Metadata])))
    THROW 52026,'DASHBOARD_MANIFESTO_GRAIN_OR_SOURCE_DIFFERS',1;

-- VALIDATION BATCH
SELECT TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')) AS id,
       TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.fit_p_m_pck_sequence_code')) AS collection_number,
       TRY_CONVERT(decimal(18,2),JSON_VALUE(l.source_envelope,'$.record.total')) AS amount,
       l.stored_utc,l.business_date,l.occurrence_ordinal
INTO #freights FROM stg.production_export_latest l WHERE l.entity_name='FRETES';
CREATE INDEX ix_manifest_freights ON #freights(id,stored_utc,business_date,occurrence_ordinal);
SELECT DISTINCT TRY_CONVERT(bigint,JSON_VALUE(source_envelope,'$.record.sequence_code')) AS collection_number
INTO #collections FROM stg.production_export_latest WHERE entity_name='COLETAS';
WITH ids AS (SELECT DISTINCT id FROM #freights WHERE id IS NOT NULL)
SELECT s.collection_number,SUM(COALESCE(s.amount,0)) AS amount
INTO #collection_amounts
FROM ids
CROSS APPLY(
    SELECT TOP(1) collection_number,amount FROM #freights f WHERE f.id=ids.id
    ORDER BY f.stored_utc DESC,f.business_date DESC,f.occurrence_ordinal DESC
) s
JOIN #collections c ON c.collection_number=s.collection_number
GROUP BY s.collection_number;

-- VALIDATION BATCH
SELECT sequence_code,COUNT_BIG(*) AS components,MAX(manifest_freights_total) AS freight_total,
       MAX(COALESCE(departured_at,created_at)) AS reference_time,
       SUM(COALESCE(c.amount,0)) AS collection_total
INTO #expectations
FROM core.dashboard_manifestos_linhas m LEFT JOIN #collection_amounts c ON c.collection_number=m.pick_sequence_code
WHERE vehicle_plate IS NULL OR vehicle_plate<>N'ACM0000'
GROUP BY sequence_code;
IF (SELECT COUNT_BIG(*) FROM #expectations)<>(SELECT COUNT_BIG(*) FROM #facts)
   OR EXISTS(
       SELECT 1 FROM #facts f JOIN #expectations e ON e.sequence_code=f.[Número]
       WHERE NOT EXISTS(SELECT f.[Fretes/Total],f.[Data criação],f.[Coletas/Total]
                        INTERSECT SELECT e.freight_total,e.reference_time,e.collection_total)
          OR f.[Receita Total Transportada]<>COALESCE(f.[Fretes/Total],0)+COALESCE(f.[Coletas/Total],0)
   ) THROW 52027,'DASHBOARD_MANIFESTO_FINANCIAL_OR_DATE_DIFFERS',1;

SELECT (SELECT COUNT_BIG(*) FROM #captured) AS captured_occurrences,
       (SELECT SUM(components) FROM #expectations) AS selected_components,
       COUNT_BIG(*) AS manifests,SUM(f.[Fretes/Total]) AS freight_total,SUM(f.[Coletas/Total]) AS collections_total,
       SUM(f.[Receita Total Transportada]) AS revenue_total,
       COUNT_BIG(v.sequence_code) AS numbers_also_present_in_v1,
       COUNT_BIG(CASE WHEN v.sequence_code IS NOT NULL AND f.[Fretes/Total] IS NOT DISTINCT FROM v.fretes_total
           AND f.[Coletas/Total] IS NOT DISTINCT FROM v.coletas_total THEN 1 END) AS equal_legacy_financial_pairs,
       COUNT_BIG(CASE WHEN v.sequence_code IS NOT NULL AND f.[Fretes/Total] IS DISTINCT FROM v.fretes_total THEN 1 END) AS legacy_freight_differences,
       COUNT_BIG(CASE WHEN v.sequence_code IS NOT NULL AND f.[Coletas/Total] IS DISTINCT FROM v.coletas_total THEN 1 END) AS legacy_collection_differences
FROM #facts f
LEFT JOIN ETL_SISTEMA.dbo.fato_gestao_vista_manifestos v ON v.sequence_code=f.[Número] AND v.excluido_na_origem=0
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

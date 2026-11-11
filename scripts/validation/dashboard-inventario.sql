SET NOCOUNT ON;

SELECT TOP(0) * INTO #published FROM pub.vw_inventario_powerbi;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #published SELECT * FROM pub.vw_inventario_powerbi;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;

-- VALIDATION BATCH
SELECT JSON_QUERY(l.source_envelope,'$.record') AS record_json, l.stored_utc, l.business_date, l.occurrence_ordinal
INTO #source FROM stg.production_export_latest l WHERE l.entity_name='INVENTARIO';
IF NOT EXISTS(SELECT 1 FROM #source) OR EXISTS (
    SELECT 1 FROM #source
    WHERE TRY_CONVERT(bigint,JSON_VALUE(record_json,'$.sequence_code')) IS NULL
      OR (JSON_VALUE(record_json,'$.cnr_c_s_fit_invoices_mapping') IS NOT NULL)
      OR (JSON_QUERY(record_json,'$.cnr_c_s_fit_invoices_mapping') IS NOT NULL
          AND ISJSON(JSON_QUERY(record_json,'$.cnr_c_s_fit_invoices_mapping'),ARRAY)<>1)
) THROW 51978,'DASHBOARD_INVENTARIO_INVALID_KEY_INPUT',1;

SELECT LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',
       CONVERT(varchar(max),CONCAT_WS('|',
           CONVERT(bigint,JSON_VALUE(s.record_json,'$.sequence_code')),
           COALESCE(CONVERT(varchar(20),CONVERT(bigint,JSON_VALUE(s.record_json,'$.cnr_c_s_fit_corporation_sequence_number'))),'null'),
           COALESCE(JSON_QUERY(s.record_json,'$.cnr_c_s_fit_invoices_mapping'),N''),
           COALESCE(JSON_VALUE(s.record_json,'$.started_at'),N'')) COLLATE Latin1_General_100_BIN2_UTF8)),2))
       COLLATE DATABASE_DEFAULT AS item_key,
       COALESCE(
         CONVERT(datetime2,CONVERT(datetimeoffset,JSON_VALUE(s.record_json,'$.cnr_c_s_fit_dpn_performance_finished_at'))),
         CONVERT(datetime2,CONVERT(datetimeoffset,JSON_VALUE(s.record_json,'$.finished_at'))),
         CONVERT(datetime2,CONVERT(datetimeoffset,JSON_VALUE(s.record_json,'$.started_at')))
       ) AS freshness,
       s.record_json,s.stored_utc,s.business_date,s.occurrence_ordinal
INTO #keys FROM #source s;
CREATE INDEX ix_inventory_key ON #keys(item_key, freshness, stored_utc, business_date, occurrence_ordinal);

-- VALIDATION BATCH
-- An anti-join reconstructs the V1 winner independently of the view's window.
SELECT k.item_key,k.record_json
INTO #expected
FROM #keys k
WHERE NOT EXISTS (
    SELECT 1 FROM #keys newer WHERE newer.item_key=k.item_key AND (
         newer.freshness>k.freshness
      OR newer.freshness IS NOT NULL AND k.freshness IS NULL
      OR (newer.freshness=k.freshness OR (newer.freshness IS NULL AND k.freshness IS NULL)) AND (
            newer.stored_utc>k.stored_utc
         OR newer.stored_utc=k.stored_utc AND newer.business_date>k.business_date
         OR newer.stored_utc=k.stored_utc AND newer.business_date=k.business_date
            AND newer.occurrence_ordinal>k.occurrence_ordinal
      )
    )
);
CREATE UNIQUE CLUSTERED INDEX ix_expected_item ON #expected(item_key);
CREATE UNIQUE CLUSTERED INDEX ix_published_inventory ON #published([Identificador Único]);

-- VALIDATION BATCH
DECLARE @published_rows bigint=(SELECT COUNT_BIG(*) FROM #published),
        @physical_rows bigint=(SELECT COUNT_BIG(*) FROM #source);
IF @published_rows<>(SELECT COUNT_BIG(*) FROM #expected)
   OR @published_rows<>(SELECT COUNT_BIG(DISTINCT [Identificador Único]) FROM #published)
   OR EXISTS (
      SELECT 1 FROM #published p FULL JOIN #expected e ON p.[Identificador Único]=e.item_key
      WHERE e.item_key IS NULL OR p.[Identificador Único] IS NULL
         OR HASHBYTES('SHA2_256',CONVERT(varbinary(max),p.[Metadata]))
             <>HASHBYTES('SHA2_256',CONVERT(varbinary(max),e.record_json))
   )
    THROW 51979,'DASHBOARD_INVENTARIO_WINNERS_DIFFER',1;

IF EXISTS (
    SELECT 1 FROM #published p
    WHERE NOT EXISTS (
      SELECT p.[Nº Minuta],p.[N° Ordem],p.[Valor de NF],p.[Volumes],p.[Volumes Lidos],p.[Peso Real],p.[Peso Taxado],p.[M³]
      INTERSECT SELECT
        CONVERT(bigint,JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_corporation_sequence_number')),
        CONVERT(bigint,JSON_VALUE(p.[Metadata],'$.sequence_code')),
        CONVERT(decimal(18,2),JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_invoices_value')),
        CONVERT(int,JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_invoices_volumes')),
        CONVERT(int,JSON_VALUE(p.[Metadata],'$.cnr_c_s_read_volumes')),
        CONVERT(decimal(18,3),JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_real_weight')),
        CONVERT(decimal(18,3),JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_taxed_weight')),
        CONVERT(decimal(18,3),JSON_VALUE(p.[Metadata],'$.cnr_c_s_fit_total_cubic_volume'))
    )
) THROW 51980,'DASHBOARD_INVENTARIO_AMOUNTS_OR_COUNTS_DIFFER',1;

IF EXISTS (
    SELECT 1 FROM #keys k JOIN #published p ON k.item_key=p.[Identificador Único]
    WHERE JSON_VALUE(k.record_json,'$.cnr_c_s_fit_fte_lce_ore_description') COLLATE Latin1_General_100_CI_AI_SC
          LIKE N'%comprovante%entrega%anexado%' AND p.[Comprovante Anexado]<>N'Sim'
) THROW 51984,'DASHBOARD_INVENTARIO_RECEIPT_FLAG_LOST',1;

SELECT @physical_rows AS physical_rows,@published_rows AS published_rows,
       @physical_rows-@published_rows AS repeated_occurrences,
       SUM([Valor de NF]) AS invoice_value,SUM(CONVERT(bigint,[Volumes])) AS volumes,
       SUM(CONVERT(bigint,[Volumes Lidos])) AS scanned_volumes,
       COUNT_BIG(CASE WHEN [Comprovante Anexado]=N'Sim' THEN 1 END) AS keys_with_receipt
FROM #published FOR JSON PATH,INCLUDE_NULL_VALUES,WITHOUT_ARRAY_WRAPPER;

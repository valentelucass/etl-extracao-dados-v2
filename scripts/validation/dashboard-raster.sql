SET NOCOUNT ON;
SELECT TOP(0) * INTO #trips FROM pub.raster_viagens;
SELECT TOP(0) * INTO #stops FROM pub.raster_viagem_paradas;
SELECT TOP(0) * INTO #transit FROM pub.vw_raster_sm_transit_time;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #trips SELECT * FROM pub.raster_viagens;
    INSERT #stops SELECT * FROM pub.raster_viagem_paradas;
    INSERT #transit SELECT * FROM pub.vw_raster_sm_transit_time;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_raster_trip ON #trips(cod_solicitacao);
CREATE UNIQUE CLUSTERED INDEX ix_raster_stop ON #stops(cod_solicitacao,ordem);
CREATE UNIQUE CLUSTERED INDEX ix_raster_transit ON #transit(cod_solicitacao,ordem_parada);

-- VALIDATION BATCH
SELECT r.source_key AS trip_key,
       COALESCE(TRY_CONVERT(int,JSON_VALUE(p.value,'$.Ordem')),CONVERT(int,p.[key])+1) AS stop_order,
       CONVERT(int,p.[key])+1 AS ordinal,p.[type],p.value AS record_json
INTO #observed_stops
FROM core.dashboard_raster_vigente r
CROSS APPLY OPENJSON(r.record_json,'$.ColetasEntregas') p;
CREATE INDEX ix_raster_source_stop ON #observed_stops(trip_key,stop_order,ordinal);
IF NOT EXISTS(SELECT 1 FROM #trips) OR NOT EXISTS(SELECT 1 FROM #stops)
   OR EXISTS(SELECT 1 FROM #observed_stops WHERE [type]<>5)
   OR (SELECT COUNT_BIG(*) FROM #trips)<>(SELECT COUNT_BIG(*) FROM core.dashboard_raster_vigente)
   OR (SELECT COUNT_BIG(*) FROM #stops)<>(SELECT COUNT_BIG(*) FROM (SELECT DISTINCT trip_key,stop_order FROM #observed_stops) d)
   OR (SELECT COUNT_BIG(*) FROM #transit)<>((SELECT COUNT_BIG(*) FROM #stops)+
       (SELECT COUNT_BIG(*) FROM #trips t WHERE NOT EXISTS(SELECT 1 FROM #stops s WHERE s.cod_solicitacao=t.cod_solicitacao)))
   OR EXISTS(
       SELECT 1 FROM #trips t LEFT JOIN core.dashboard_raster_vigente r ON r.source_key=t.cod_solicitacao
       WHERE r.source_key IS NULL OR HASHBYTES('SHA2_256',t.metadata)<>HASHBYTES('SHA2_256',r.record_json)
          OR t.excluido_na_origem<>0 OR t.data_extracao<>r.observed_utc
   )
   OR EXISTS(
       SELECT 1 FROM #stops s
       OUTER APPLY(SELECT TOP(1) r.record_json FROM #observed_stops r
           WHERE r.trip_key=s.cod_solicitacao AND r.stop_order=s.ordem ORDER BY r.ordinal DESC) p
       WHERE p.record_json IS NULL OR HASHBYTES('SHA2_256',s.metadata)<>HASHBYTES('SHA2_256',p.record_json)
          OR s.excluido_na_origem<>0
   ) THROW 52017,'DASHBOARD_RASTER_GRAIN_OR_PROVENANCE_DIFFERS',1;

-- VALIDATION BATCH
IF EXISTS(
    SELECT 1 FROM #trips t CROSS APPLY OPENJSON(t.metadata) j
    WHERE j.[type]<>0 AND j.[key] IN (N'DataHoraPrevIni',N'DataHoraPrevFim',N'DataHoraRealIni',N'DataHoraRealFim',N'DataHoraIdentificouFimViagem')
      AND TRIM(j.value)<>N'' AND LEFT(TRIM(j.value),10)<>N'1900-01-01'
      AND core.dashboard_raster_time(j.value) IS NULL
) OR EXISTS (
    SELECT 1 FROM #stops s CROSS APPLY OPENJSON(s.metadata) j
    WHERE j.[type]<>0 AND j.[key] IN (N'DataHoraPrevChegada',N'DataHoraPrevSaida',N'DataHoraRealChegada',N'DataHoraRealSaida',N'DataHoraUltimaPosicao')
      AND TRIM(j.value)<>N'' AND LEFT(TRIM(j.value),10)<>N'1900-01-01'
      AND core.dashboard_raster_time(j.value) IS NULL
) THROW 52018,'DASHBOARD_RASTER_TIMESTAMP_NOT_INTERPRETED',1;

SELECT (SELECT COUNT_BIG(*) FROM #trips) AS trips,
       (SELECT COUNT_BIG(*) FROM #observed_stops) AS observed_stops,
       (SELECT COUNT_BIG(*) FROM #stops) AS published_stops,
       (SELECT COUNT_BIG(*) FROM #transit) AS transit_rows,
       (SELECT COUNT(DISTINCT business_date) FROM stg.dashboard_auxiliary_latest WHERE source_name='RASTER') AS covered_days,
       (SELECT COUNT_BIG(*) FROM #trips v JOIN ETL_SISTEMA.dbo.raster_viagens o ON o.cod_solicitacao=v.cod_solicitacao) AS legacy_trip_overlap,
       (SELECT COUNT_BIG(*) FROM #stops p JOIN ETL_SISTEMA.dbo.raster_viagem_paradas o
           ON o.cod_solicitacao=p.cod_solicitacao AND o.ordem=p.ordem) AS legacy_stop_overlap
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

CREATE OR ALTER VIEW core.dashboard_raster_vigente AS
WITH observacoes AS (
    SELECT source_key,record_json,observed_utc,business_date,capture_id
    FROM stg.dashboard_auxiliary_latest WHERE source_name='RASTER'
    UNION ALL
    SELECT source_key,record_json,observed_utc,NULL,NULL FROM core.dashboard_raster_entre_dias
), ordenadas AS (
    SELECT *,ROW_NUMBER() OVER(PARTITION BY source_key ORDER BY observed_utc DESC,business_date DESC,capture_id DESC) AS ordem
    FROM observacoes
)
SELECT source_key,record_json,observed_utc FROM ordenadas WHERE ordem=1;

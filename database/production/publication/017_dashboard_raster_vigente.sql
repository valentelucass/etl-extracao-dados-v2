-- The trip and stop views consume the same revision of each Raster trip.
CREATE OR ALTER VIEW core.dashboard_raster_vigente
AS
WITH ranked AS (
    SELECT *,ROW_NUMBER() OVER(PARTITION BY source_key ORDER BY observed_utc DESC,business_date DESC,capture_id DESC) AS row_rank
    FROM stg.dashboard_auxiliary_latest WHERE source_name='RASTER'
)
SELECT source_key,record_json,observed_utc FROM ranked WHERE row_rank=1;

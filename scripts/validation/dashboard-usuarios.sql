SET NOCOUNT ON;
SELECT TOP(0) * INTO #dashboard_users FROM pub.vw_dim_usuarios;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #dashboard_users SELECT * FROM pub.vw_dim_usuarios;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_dashboard_user_id ON #dashboard_users([User ID]);

-- VALIDATION BATCH
IF NOT EXISTS(SELECT 1 FROM #dashboard_users)
   OR (SELECT COUNT_BIG(*) FROM #dashboard_users)<>(SELECT COUNT_BIG(*) FROM stg.dashboard_auxiliary_latest WHERE source_name='USERS')
   OR EXISTS (
       SELECT 1
       FROM stg.dashboard_auxiliary_latest r
       LEFT JOIN #dashboard_users p ON p.[User ID]=r.source_key
       WHERE r.source_name='USERS' AND (p.[User ID] IS NULL OR NOT EXISTS (
           SELECT p.[Nome],p.[Data Atualizacao]
           INTERSECT SELECT TRIM(JSON_VALUE(r.record_json,'$.name')),
              CONVERT(datetime,TODATETIMEOFFSET(r.observed_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time')
       ))
   ) THROW 52016,'DASHBOARD_USER_SOURCE_VALUES_DIFFER',1;

SELECT COUNT_BIG(*) AS users,
       COUNT_BIG(CASE WHEN n.[Nome] IS NULL THEN 1 END) AS users_without_name,
       COUNT_BIG(o.[User ID]) AS users_also_present_in_v1,
       COUNT_BIG(CASE WHEN o.[User ID] IS NOT NULL AND
           n.[Nome] COLLATE Latin1_General_100_BIN2=o.[Nome] COLLATE Latin1_General_100_BIN2 THEN 1 END) AS identical_legacy_names
FROM #dashboard_users n
LEFT JOIN ETL_SISTEMA.dbo.vw_dim_usuarios o ON o.[User ID]=n.[User ID]
FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

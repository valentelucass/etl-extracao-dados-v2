CREATE OR ALTER VIEW pub.vw_dim_usuarios
AS
SELECT r.source_key AS [User ID],
       CONVERT(nvarchar(255),TRIM(j.name)) AS [Nome],
       CONVERT(datetime,TODATETIMEOFFSET(r.observed_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS [Data Atualizacao]
FROM stg.dashboard_auxiliary_latest r
CROSS APPLY OPENJSON(r.record_json) WITH(name nvarchar(max) '$.name') j
WHERE r.source_name='USERS';

CREATE OR ALTER VIEW pub.vw_dim_usuarios
AS
SELECT r.source_key AS [User ID],
       CONVERT(nvarchar(255),TRIM(j.name)) AS [Nome],
       CONVERT(datetime,CASE WHEN j.provenance='LEGACY_DIM_USUARIOS' THEN j.source_updated_at
            ELSE CONVERT(datetime2(3),TODATETIMEOFFSET(r.observed_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') END) AS [Data Atualizacao]
FROM stg.dashboard_auxiliary_latest r
CROSS APPLY OPENJSON(r.record_json) WITH(
    name nvarchar(max) '$.name',
    source_updated_at datetime2(3) '$.source_updated_at',
    provenance varchar(30) '$.provenance'
) j
WHERE r.source_name='USERS';

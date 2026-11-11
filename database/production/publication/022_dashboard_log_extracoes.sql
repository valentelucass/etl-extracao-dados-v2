CREATE OR ALTER VIEW pub.log_extracoes
AS
SELECT l.log_id AS id,CONVERT(nvarchar(50),LOWER(r.entity_name)) AS entidade,
       CONVERT(datetime2(7),TODATETIMEOFFSET(r.started_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS timestamp_inicio,
       CONVERT(datetime2(7),TODATETIMEOFFSET(r.completed_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS timestamp_fim,
       CONVERT(nvarchar(20),CASE r.run_state WHEN 'STORED' THEN N'COMPLETO' ELSE N'FALHA' END) AS status_final,
       COALESCE(r.row_count,0)-l.unchanged_rows AS registros_extraidos,l.pages AS paginas_processadas,
       l.unchanged_rows AS noop_count,CONVERT(nvarchar(max),r.failure_code) AS mensagem
FROM ctl.dashboard_refresh_log l JOIN ctl.production_ingestion_run r ON r.run_id=l.run_id;

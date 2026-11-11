CREATE OR ALTER VIEW pub.vw_bi_monitoramento
AS
SELECT c.cycle_id AS [Id],d.started AS [Inicio],d.completed AS [Fim],
       DATEDIFF(second,c.started_utc,c.completed_utc) AS [Duracao (s)],CONVERT(date,d.started) AS [Data],
       CONVERT(varchar(20),CASE WHEN c.passed=1 THEN 'SUCCESS' ELSE 'FAILED' END) AS [Status],
       COALESCE(totals.rows_count,0) AS [Total Registros],
       CONVERT(varchar(50),CASE WHEN c.passed=0 THEN 'EXTRACAO' END) AS [Categoria Erro],
       CONVERT(varchar(500),c.failure_code) AS [Mensagem Erro]
FROM ctl.dashboard_refresh_cycle c
CROSS APPLY(VALUES(
    CONVERT(datetime2(7),TODATETIMEOFFSET(c.started_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time'),
    CONVERT(datetime2(7),TODATETIMEOFFSET(c.completed_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time')
)) d(started,completed)
CROSS APPLY (
    SELECT SUM(r.row_count) AS rows_count FROM ctl.dashboard_refresh_log l
    JOIN ctl.production_ingestion_run r ON r.run_id=l.run_id WHERE l.cycle_id=c.cycle_id
) totals;

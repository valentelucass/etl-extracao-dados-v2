SET NOCOUNT ON;
SELECT TOP(0) * INTO #log FROM pub.log_extracoes;
SELECT TOP(0) * INTO #cycles FROM pub.vw_bi_monitoramento;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #log SELECT * FROM pub.log_extracoes;
    INSERT #cycles SELECT * FROM pub.vw_bi_monitoramento;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
CREATE UNIQUE CLUSTERED INDEX ix_dashboard_log ON #log(id);
CREATE UNIQUE CLUSTERED INDEX ix_dashboard_cycle ON #cycles([Id]);

-- VALIDATION BATCH
IF NOT EXISTS(SELECT 1 FROM #cycles)
   OR (SELECT COUNT_BIG(*) FROM #cycles)<>(SELECT COUNT_BIG(*) FROM ctl.dashboard_refresh_cycle)
   OR (SELECT COUNT_BIG(*) FROM #log)<>(SELECT COUNT_BIG(*) FROM ctl.dashboard_refresh_log)
   OR EXISTS(
        SELECT 1 FROM #log p JOIN ctl.dashboard_refresh_log l ON l.log_id=p.id
        JOIN ctl.production_ingestion_run r ON r.run_id=l.run_id
        WHERE p.registros_extraidos<0 OR p.noop_count<0 OR p.timestamp_inicio>p.timestamp_fim
           OR p.registros_extraidos+p.noop_count<>COALESCE(r.row_count,0)
           OR p.paginas_processadas<>l.pages OR p.entidade<>LOWER(r.entity_name)
           OR p.status_final<>CASE r.run_state WHEN 'STORED' THEN N'COMPLETO' ELSE N'FALHA' END
   )
   OR EXISTS(
       SELECT 1 FROM #cycles p JOIN ctl.dashboard_refresh_cycle c ON c.cycle_id=p.[Id]
       WHERE p.[Data]<>CONVERT(date,p.[Inicio]) OR p.[Duracao (s)]<0 OR p.[Inicio]>p.[Fim]
          OR (c.passed=1 AND (p.[Status]<>'SUCCESS' OR p.[Categoria Erro] IS NOT NULL))
          OR (c.passed=0 AND (p.[Status]<>'FAILED' OR p.[Mensagem Erro]<>c.failure_code))
   ) THROW 52024,'DASHBOARD_AUDIT_TOTALS_OR_STATE_DIFFER',1;

SELECT (SELECT COUNT_BIG(*) FROM #cycles) AS cycles,
       COUNT_BIG(*) AS run_logs,COUNT_BIG(DISTINCT entidade) AS entities,
       SUM(CONVERT(bigint,registros_extraidos)) AS changed_rows,SUM(CONVERT(bigint,noop_count)) AS identical_rows,
       SUM(CONVERT(bigint,paginas_processadas)) AS nonempty_pages,
       (SELECT SUM(CONVERT(bigint,[Total Registros])) FROM #cycles) AS cycle_rows,
       (SELECT COUNT_BIG(*) FROM #cycles WHERE [Status]='FAILED') AS failed_cycles
FROM #log FOR JSON PATH, INCLUDE_NULL_VALUES, WITHOUT_ARRAY_WRAPPER;

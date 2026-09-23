-- SQL10: internal capture audit. Never project payload, free-form error text or credentials.
SET ANSI_NULLS ON;SET QUOTED_IDENTIFIER ON;
GO
CREATE VIEW core.analytic_lab_monitor_event AS
 SELECT g.run_id,c.execution_id event_id,CONVERT(VARCHAR(24),c.vertical) COLLATE Latin1_General_100_BIN2 entity,
 c.created_at started_at,c.sealed_at finished_at,CONVERT(NVARCHAR(32),
 CASE WHEN q.quarantine>0 THEN N'DEGRADED' ELSE c.state END) COLLATE Latin1_General_100_BIN2 state,
 c.physical_rows observed,q.quarantine,CONVERT(VARCHAR(24),'EXPANSION_CAPTURE') provenance
 FROM ctl.analytic_lab_source_group g JOIN ctl.expansion_lab_capture c ON c.run_id=g.expansion_run
 OUTER APPLY(SELECT COUNT_BIG(*) quarantine FROM stg.expansion_lab_observation o
 WHERE o.execution_id=c.execution_id AND o.disposition='QUARANTINE') q
 UNION ALL
 SELECT g.run_id,c.execution_id,CONVERT(VARCHAR(24),c.entity_name),e.started_at_utc,e.terminal_at_utc,
 e.current_state,c.physical_rows,p.quarantined_stage_rows,'RELATIONAL_CAPTURE'
 FROM ctl.analytic_lab_source_group g JOIN ctl.relational_lab_capture c ON c.run_id=g.relational_run
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 LEFT JOIN ctl.execution_promotion_result p ON p.execution_id=e.execution_id
 UNION ALL
 SELECT g.run_id,c.execution_id,CONVERT(VARCHAR(24),c.entity),e.started_at_utc,e.terminal_at_utc,
 CASE WHEN c.quarantine>0 THEN N'DEGRADED' ELSE e.current_state END,c.observed,c.quarantine,'DEPENDENCY_CAPTURE'
 FROM ctl.analytic_lab_source_group g JOIN ctl.expansion_lab_dependency_capture c ON c.run_id=g.expansion_run
 JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 WHERE NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture x WHERE x.execution_id=c.execution_id AND x.run_id=g.relational_run)
 UNION ALL
 SELECT source.run_id,e.execution_id,CONVERT(VARCHAR(24),source.entity),e.started_at_utc,e.terminal_at_utc,
 e.current_state,p.physical_rows,p.quarantined_stage_rows,'ATTACHED_RUNTIME'
 FROM ctl.analytic_lab_execution_source source JOIN ctl.execution_attempt e ON e.execution_id=source.execution_id
 LEFT JOIN ctl.execution_promotion_result p ON p.execution_id=e.execution_id;
GO
CREATE VIEW pub.analytic_lab_sql_10 AS SELECT
 CONVERT(NVARCHAR(36),event_id) AS [Id],started_at AS [Inicio],finished_at AS [Fim],
 CONVERT(DECIMAL(28,8),DATEDIFF_BIG(MICROSECOND,started_at,finished_at)/CONVERT(DECIMAL(28,8),1000000)) AS [Duracao (s)],
 CONVERT(DATE,started_at) AS [Data],state AS [Status],observed AS [Total Registros],
 CONVERT(NVARCHAR(32),CASE WHEN quarantine>0 THEN N'DATA_QUALITY'
 WHEN state IN(N'FAILED',N'CANCELLED',N'BLOCKED',N'DEGRADED',N'INCOMPLETE') THEN N'EXECUTION_STATE' END) AS [Categoria Erro],
 CONVERT(NVARCHAR(64),CASE WHEN quarantine>0 THEN N'SYNTHETIC_SOURCE_QUARANTINE'
 WHEN state=N'FAILED' THEN N'EXECUTION_FAILED' WHEN state=N'CANCELLED' THEN N'EXECUTION_CANCELLED'
 WHEN state=N'BLOCKED' THEN N'EXECUTION_BLOCKED' WHEN state=N'DEGRADED' THEN N'EXECUTION_DEGRADED'
 WHEN state=N'INCOMPLETE' THEN N'CAPTURE_INCOMPLETE' END) AS [Mensagem Erro],
 run_id,event_id,entity,provenance,quarantine,CONVERT(DATE,started_at) business_date
 FROM core.analytic_lab_monitor_event;
GO

CREATE OR ALTER VIEW pub.customer_service_cobertura_ocorrencias AS
SELECT c.business_date AS dia
FROM ctl.production_ingestion_coverage c
LEFT JOIN ctl.dashboard_performance_ocorrencias_particao p
    ON p.escopo = c.source_scope AND p.data_particao = c.business_date
WHERE c.entity_name = 'OCORRENCIAS'
GROUP BY c.business_date
HAVING MIN(CASE WHEN p.captura = c.latest_run_id THEN 1 ELSE 0 END) = 1;
GO

-- Os horários pertencem ao mesmo registro imutável do relatório 12072 que a ocorrência publicada.
CREATE VIEW pub.dashboard_ocorrencias_tratativas AS
SELECT p.escopo, p.data_particao, r.occurrence_ordinal AS ordem,
       j.prazo, j.solucao
FROM ctl.dashboard_performance_ocorrencias_particao p
JOIN stg.production_export_occurrence r ON r.run_id=p.captura
CROSS APPLY OPENJSON(r.source_envelope, '$.record') WITH (
    prazo datetimeoffset(3) '$.ice_ttt_solution_deadline',
    solucao datetimeoffset(3) '$.ice_ttt_solution_at'
) j;
GO
CREATE VIEW pub.dashboard_cobertura_sinistros AS
SELECT entity_name AS entidade, business_date AS dia
FROM ctl.production_ingestion_coverage
WHERE entity_name IN ('SINISTROS','FRETES')
GROUP BY entity_name, business_date;
GO
GRANT SELECT ON OBJECT::pub.dashboard_ocorrencias_tratativas TO usuario_etl;
GRANT SELECT ON OBJECT::pub.dashboard_cobertura_sinistros TO usuario_etl;

-- Only the most recent accepted capture of an ESL partition is authoritative.
-- Report 11361 is partitioned by the creation date of the archive.
CREATE OR ALTER VIEW pub.rede_credenciada_arquivos AS
SELECT dados.minuta, MIN(c.business_date) AS primeiro_arquivamento
FROM ctl.production_ingestion_coverage AS c
JOIN stg.production_export_occurrence AS o ON o.run_id = c.latest_run_id
CROSS APPLY OPENJSON(o.source_envelope, '$.record') WITH (
    minuta bigint '$.fag_fhs_fit_corporation_sequence_number',
    arquivo nvarchar(100) '$.sequence_code'
) AS dados
WHERE c.entity_name = 'COMPROVANTES_ENTREGA'
  AND dados.minuta IS NOT NULL AND NULLIF(TRIM(dados.arquivo), '') IS NOT NULL
GROUP BY dados.minuta;
GO
CREATE OR ALTER VIEW pub.rede_credenciada_cobertura AS
SELECT DISTINCT entity_name AS entidade, business_date AS data_arquivo
FROM ctl.production_ingestion_coverage
WHERE entity_name IN ('COMPROVANTES_ENTREGA', 'CONSOLIDACAO_DETALHADA');
GO

-- Compare os registros do relatório 11067 com a projeção usando a última captura aceita de cada partição.
SELECT c.business_date AS dia, o.occurrence_ordinal AS ordem,
       CONVERT(bigint, JSON_VALUE(o.source_envelope,'$.record.sequence_code')) AS consolidacao,
       CONVERT(bigint, JSON_VALUE(o.source_envelope,'$.record.ctd_fgs_corporation_sequence_number')) AS minuta,
       CONVERT(decimal(19,2), JSON_VALUE(o.source_envelope,'$.record.charged_value')) AS cobrado,
       CONVERT(decimal(19,2), JSON_VALUE(o.source_envelope,'$.record.subtotal')) AS calculado,
       CONVERT(decimal(19,2), JSON_VALUE(o.source_envelope,'$.record.ctd_fgs_total')) AS frete
INTO #origem
FROM ctl.production_ingestion_coverage AS c
JOIN stg.production_export_occurrence AS o ON o.run_id = c.latest_run_id
WHERE c.entity_name = 'CONSOLIDACAO_DETALHADA';

EXECUTE AS USER=N'usuario_etl';
BEGIN TRY
    SELECT data_emissao AS dia, ordem, consolidacao, minuta, cobrado, calculado, frete_rodogarcia AS frete
    INTO #leitura FROM pub.dashboard_rede_credenciada_consolidacoes;
    REVERT;
END TRY
BEGIN CATCH
    REVERT;
    THROW;
END CATCH;

IF (SELECT COUNT_BIG(*) FROM #origem) <> (SELECT COUNT_BIG(*) FROM #leitura)
    OR EXISTS (SELECT * FROM #origem EXCEPT SELECT * FROM #leitura)
    OR EXISTS (SELECT * FROM #leitura EXCEPT SELECT * FROM #origem)
    THROW 52200, 'DASHBOARD_REDE_CONSOLIDACAO_DIVERGENTE', 1;

SELECT COUNT_BIG(*) AS linhas, COUNT_BIG(DISTINCT consolidacao) AS consolidacoes,
       MIN(dia) AS inicio, MAX(dia) AS fim
FROM #leitura FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;

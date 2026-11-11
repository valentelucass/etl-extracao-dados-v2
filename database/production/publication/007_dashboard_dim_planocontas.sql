-- Use accounting allocations for the filter. Payment totals use a separate grain.
CREATE OR ALTER VIEW pub.vw_dim_planocontas
AS
SELECT CONVERT(nvarchar(255), UPPER(TRIM(j.account_name COLLATE Latin1_General_CI_AS))) AS Descricao,
       ISNULL(CONVERT(nvarchar(100), MAX(j.classification COLLATE Latin1_General_CI_AS)),
              N'OUTROS / NÃO CLASSIFICADO') AS Classificacao,
       CONVERT(time(0), '00:00:00') AS [Hora (Solicitacao)]
FROM stg.production_export_latest AS l
CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
    account_name nvarchar(max) '$.ant_ils_pas_ant_name',
    classification nvarchar(max) '$.ant_ils_pas_ant_classification'
) AS j
WHERE l.entity_name = 'CONTAS_A_PAGAR' AND TRIM(j.account_name) <> N''
  AND (SELECT COUNT_BIG(DISTINCT source_scope)
       FROM ctl.production_ingestion_coverage WHERE entity_name = 'CONTAS_A_PAGAR') = 1
GROUP BY UPPER(TRIM(j.account_name COLLATE Latin1_General_CI_AS));

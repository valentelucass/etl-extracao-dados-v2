-- Filter dimension from the same four business sources used by the V1 view.
CREATE OR ALTER VIEW pub.vw_dim_filiais
AS
WITH branches AS (
    SELECT CASE l.entity_name
               WHEN 'FRETES' THEN j.freight_branch
               WHEN 'MANIFESTOS' THEN j.manifest_branch
               WHEN 'CONTAS_A_PAGAR' THEN j.payable_branch
               WHEN 'FATURAS_POR_CLIENTE' THEN j.freight_branch
           END COLLATE Latin1_General_CI_AS AS branch_name
    FROM stg.production_export_latest AS l
    CROSS APPLY OPENJSON(l.source_envelope, '$.record') WITH (
        freight_branch nvarchar(max) '$.fit_crn_psn_nickname',
        manifest_branch nvarchar(max) '$.mft_crn_psn_nickname',
        payable_branch nvarchar(max) '$.ant_crn_psn_nickname'
    ) AS j
    WHERE l.entity_name IN ('FRETES', 'MANIFESTOS', 'CONTAS_A_PAGAR', 'FATURAS_POR_CLIENTE')
      AND (SELECT COUNT_BIG(DISTINCT source_scope)
           FROM ctl.production_ingestion_coverage
           WHERE entity_name IN ('FRETES', 'MANIFESTOS', 'CONTAS_A_PAGAR', 'FATURAS_POR_CLIENTE')) = 1
)
SELECT DISTINCT CONVERT(nvarchar(255), TRIM(branch_name)) AS NomeFilial,
       CONVERT(time(0), '00:00:00') AS [Hora (Solicitacao)]
FROM branches
WHERE TRIM(branch_name) <> N'';

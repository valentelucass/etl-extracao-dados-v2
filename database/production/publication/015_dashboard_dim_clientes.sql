-- Keep the three input entities and normalized-name grain of the V1 dimension.
CREATE OR ALTER VIEW pub.vw_dim_clientes
AS
WITH source_scope AS (
    SELECT 1 AS ready FROM ctl.production_ingestion_coverage
    WHERE entity_name IN ('COLETAS','FRETES','FATURAS_POR_CLIENTE')
    HAVING COUNT(DISTINCT source_scope)=1 AND COUNT(DISTINCT entity_name)=3
), active_rows AS (
    SELECT l.entity_name,j.*,ROW_NUMBER() OVER(PARTITION BY l.entity_name,j.source_id ORDER BY
        l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal DESC) AS row_rank
    FROM stg.production_export_latest l
    CROSS JOIN source_scope
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        source_id bigint '$.id',
        pick_customer nvarchar(max) '$.pck_cor_nickname',
        freight_payer nvarchar(max) '$.fit_pyr_name',
        freight_sender nvarchar(max) '$.fit_sdr_nickname',
        freight_recipient nvarchar(max) '$.fit_rpt_nickname'
    ) j
    WHERE l.entity_name IN ('COLETAS','FRETES') AND j.source_id IS NOT NULL
), parties AS (
    SELECT CASE WHEN a.entity_name='COLETAS' THEN a.pick_customer ELSE p.client_name END AS client_name
    FROM active_rows a
    CROSS APPLY (VALUES(1,a.freight_payer),(2,a.freight_sender),(3,a.freight_recipient)) p(role_number,client_name)
    WHERE a.row_rank=1 AND (a.entity_name='FRETES' OR p.role_number=1)
    UNION ALL
    SELECT p.client_name
    FROM core.dashboard_faturas_vigentes f
    CROSS JOIN source_scope
    CROSS APPLY (VALUES(f.pagador_nome),(f.remetente_nome),(f.destinatario_nome)) p(client_name)
)
SELECT DISTINCT CONVERT(nvarchar(255),UPPER(TRIM(client_name COLLATE Latin1_General_CI_AS))) AS [Nome]
FROM parties WHERE TRIM(client_name)<>N'';

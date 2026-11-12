CREATE OR ALTER VIEW core.dashboard_fretes_linhas
AS
WITH fretes AS (
    SELECT j.*,l.source_scope,l.business_date,l.stored_utc,l.occurrence_ordinal
    FROM stg.production_export_latest l
    CROSS APPLY (SELECT
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')) AS id,
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.corporation_sequence_number')) AS corporation_sequence_number,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.service_at')) AS servico_em,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.updated_at')) AS updated_at,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.status')) AS status,
        TRY_CONVERT(nvarchar(100),JSON_VALUE(l.source_envelope,'$.record.type')) AS tipo_frete,
        TRY_CONVERT(bit,JSON_VALUE(l.source_envelope,'$.record.courtesy')) AS cortesia,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.modal')) AS modal,
        TRY_CONVERT(nvarchar(255),JSON_VALUE(l.source_envelope,'$.record.fit_crn_psn_nickname')) AS filial_nome,
        TRY_CONVERT(nvarchar(255),JSON_VALUE(l.source_envelope,'$.record.fit_pyr_name')) AS pagador_nome,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.fit_pyr_document')) AS pagador_documento,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.fit_sdr_document')) AS remetente_documento,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.fit_rir_document')) AS destinatario_documento,
        TRY_CONVERT(nvarchar(255),JSON_VALUE(l.source_envelope,'$.record.fit_dos_cty_name')) AS destino_cidade,
        TRY_CONVERT(nvarchar(10),JSON_VALUE(l.source_envelope,'$.record.fit_diy_sae_code')) AS destino_uf,
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_cte_id')) AS cte_id,
        TRY_CONVERT(nvarchar(90),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_key')) AS chave_cte,
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_number')) AS numero_cte,
        TRY_CONVERT(int,JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_series')) AS serie_cte,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_created_at')) AS cte_created_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_issued_at')) AS cte_issued_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_cancellation_at')) AS cte_canceled_at,
        TRY_CONVERT(nvarchar(100),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_status')) AS cte_status,
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.nfse_number')) AS nfse_number,
        TRY_CONVERT(nvarchar(50),JSON_VALUE(l.source_envelope,'$.record.nfse_series')) AS nfse_series,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_nse_issued_at')) AS nfse_issued_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_dpn_delivery_prediction_at')) AS data_previsao_entrega,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_dpn_performance_finished_at')) AS fit_dpn_performance_finished_at,
        TRY_CONVERT(decimal(18,2),JSON_VALUE(l.source_envelope,'$.record.total')) AS valor_total,
        TRY_CONVERT(int,JSON_VALUE(l.source_envelope,'$.record.invoices_volumes')) AS invoices_total_volumes,
        TRY_CONVERT(decimal(18,3),JSON_VALUE(l.source_envelope,'$.record.taxed_weight')) AS taxed_weight,
        TRY_CONVERT(decimal(18,3),JSON_VALUE(l.source_envelope,'$.record.real_weight')) AS real_weight,
        TRY_CONVERT(decimal(18,6),JSON_VALUE(l.source_envelope,'$.record.cubages_cubed_weight')) AS cubages_cubed_weight,
        TRY_CONVERT(decimal(18,6),JSON_VALUE(l.source_envelope,'$.record.total_cubic_volume')) AS total_cubic_volume
    ) j
    WHERE l.entity_name='FRETES' AND j.id IS NOT NULL
      AND (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name='FRETES')=1
), latest_freight AS (
    SELECT *,ROW_NUMBER() OVER (PARTITION BY source_scope,id ORDER BY
        stored_utc DESC,
        COALESCE(cte_issued_at,cte_created_at,nfse_issued_at) DESC,
        CASE WHEN cte_canceled_at IS NULL AND cte_status IN (N'authorized',N'autorizado') THEN 0 ELSE 1 END,
        updated_at DESC,cte_id DESC,business_date DESC,occurrence_ordinal) AS freight_rank
    FROM fretes
), faturas AS (
    SELECT j.*,l.source_scope,
           ROW_NUMBER() OVER (PARTITION BY l.source_scope,j.id ORDER BY
               l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal) AS invoice_rank
    FROM stg.production_export_latest l
    CROSS APPLY (SELECT
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')) AS id,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.finished_at')) AS finished_at
    ) j
    WHERE l.entity_name='FATURAS_POR_CLIENTE' AND j.id IS NOT NULL
)
SELECT f.*,b.finished_at,
       NULLIF(LOWER(TRIM(f.filial_nome)),N'') AS filial_nome_key,
       CONVERT(datetime2(0),f.stored_utc) AS data_extracao
FROM latest_freight f
LEFT JOIN faturas b ON b.source_scope=f.source_scope AND b.id=f.id AND b.invoice_rank=1
WHERE f.freight_rank=1;

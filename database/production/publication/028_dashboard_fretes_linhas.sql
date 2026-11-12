CREATE OR ALTER VIEW core.dashboard_fretes_linhas
AS
WITH fretes AS (
    SELECT j.*,l.source_scope,l.business_date,l.stored_utc,l.occurrence_ordinal
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        id bigint '$.id',
        corporation_sequence_number bigint '$.corporation_sequence_number',
        servico_em datetimeoffset(7) '$.service_at',
        updated_at datetimeoffset(7) '$.updated_at',
        status nvarchar(50) '$.status',
        tipo_frete nvarchar(100) '$.type',
        cortesia bit '$.courtesy',
        modal nvarchar(50) '$.modal',
        filial_nome nvarchar(255) '$.fit_crn_psn_nickname',
        pagador_nome nvarchar(255) '$.fit_pyr_name',
        pagador_documento nvarchar(50) '$.fit_pyr_document',
        remetente_documento nvarchar(50) '$.fit_sdr_document',
        destinatario_documento nvarchar(50) '$.fit_rir_document',
        destino_cidade nvarchar(255) '$.fit_dos_cty_name',
        destino_uf nvarchar(10) '$.fit_diy_sae_code',
        cte_id bigint '$.fit_fhe_cte_cte_id',
        chave_cte nvarchar(90) '$.fit_fhe_cte_key',
        numero_cte bigint '$.fit_fhe_cte_number',
        serie_cte int '$.fit_fhe_cte_series',
        cte_created_at datetimeoffset(7) '$.fit_fhe_cte_created_at',
        cte_issued_at datetimeoffset(7) '$.fit_fhe_cte_issued_at',
        cte_canceled_at datetimeoffset(7) '$.fit_fhe_cte_cancellation_at',
        cte_status nvarchar(100) '$.fit_fhe_cte_status',
        nfse_number bigint '$.nfse_number',
        nfse_series nvarchar(50) '$.nfse_series',
        nfse_issued_at datetimeoffset(7) '$.fit_nse_issued_at',
        data_previsao_entrega datetimeoffset(7) '$.fit_dpn_delivery_prediction_at',
        fit_dpn_performance_finished_at datetimeoffset(7) '$.fit_dpn_performance_finished_at',
        valor_total decimal(18,2) '$.total',
        invoices_total_volumes int '$.invoices_volumes',
        taxed_weight decimal(18,3) '$.taxed_weight',
        real_weight decimal(18,3) '$.real_weight',
        cubages_cubed_weight decimal(18,6) '$.cubages_cubed_weight',
        total_cubic_volume decimal(18,6) '$.total_cubic_volume'
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
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        id bigint '$.id',
        finished_at datetimeoffset(7) '$.finished_at'
    ) j
    WHERE l.entity_name='FATURAS_POR_CLIENTE' AND j.id IS NOT NULL
)
SELECT f.*,b.finished_at,
       NULLIF(LOWER(TRIM(f.filial_nome)),N'') AS filial_nome_key,
       CONVERT(datetime2(0),f.stored_utc) AS data_extracao
FROM latest_freight f
LEFT JOIN faturas b ON b.source_scope=f.source_scope AND b.id=f.id AND b.invoice_rank=1
WHERE f.freight_rank=1;

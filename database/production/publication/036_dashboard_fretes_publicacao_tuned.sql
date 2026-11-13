CREATE OR ALTER VIEW core.dashboard_fretes_publicacao
AS
-- O recebimento valida um unico objeto record por envelope. O limite explicita essa cardinalidade.
WITH fretes AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           JSON_QUERY(l.source_envelope,'$.record') AS metadata,
           ROW_NUMBER() OVER (PARTITION BY l.source_scope,k.id ORDER BY
              l.stored_utc DESC,COALESCE(k.cte_issued_at,k.cte_created_at,k.nfse_issued_at) DESC,
              k.updated_at DESC,k.cte_id DESC,l.business_date DESC,l.occurrence_ordinal) AS rn
    FROM stg.production_export_latest l
    CROSS APPLY (SELECT TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')) AS id,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_issued_at')) AS cte_issued_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_created_at')) AS cte_created_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.fit_nse_issued_at')) AS nfse_issued_at,
        TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.updated_at')) AS updated_at,
        TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_cte_id')) AS cte_id) k
    CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(l.source_envelope,'$.record') WITH (
        id bigint, corporation_sequence_number bigint, updated_at datetimeoffset(7), service_at datetimeoffset(7),
        status nvarchar(50), type nvarchar(100), modal nvarchar(50), courtesy bit, service_type nvarchar(50),
        reference_number nvarchar(100), insurance_enabled bit, payment_accountable_type nvarchar(50),
        insurance_accountable_type nvarchar(50), km decimal(18,2),
        branch_id bigint '$.fit_crn_id', branch_name nvarchar(255) '$.fit_crn_psn_nickname',
        payer_name nvarchar(255) '$.fit_pyr_name', payer_document nvarchar(50) '$.fit_pyr_document',
        payer_id bigint '$.fit_pyr_id', sender_id bigint '$.fit_sdr_id', sender_document nvarchar(50) '$.fit_sdr_document',
        sender_nickname nvarchar(255) '$.fit_sdr_nickname', receiver_id bigint '$.fit_rir_id',
        receiver_document nvarchar(50) '$.fit_rir_document', receiver_nickname nvarchar(255) '$.fit_rir_nickname',
        origin_city nvarchar(255) '$.fit_oas_cty_name', origin_state nvarchar(10) '$.fit_ony_sae_code',
        destination_city nvarchar(255) '$.fit_dos_cty_name', destination_state nvarchar(10) '$.fit_diy_sae_code',
        classification nvarchar(255) '$.fit_fsn_name', price_table nvarchar(255) '$.fit_cre_name',
        user_name nvarchar(255) '$.fit_uer_name',
        cte_id bigint '$.fit_fhe_cte_cte_id', cte_key nvarchar(100) '$.fit_fhe_cte_key',
        cte_number nvarchar(50) '$.fit_fhe_cte_number', cte_series nvarchar(50) '$.fit_fhe_cte_series',
        cte_emission_type nvarchar(50) '$.fit_fhe_cte_emission_type',
        cte_created_at datetimeoffset(7) '$.fit_fhe_cte_created_at', cte_issued_at datetimeoffset(7) '$.fit_fhe_cte_issued_at',
        cte_cancellation_at datetimeoffset(7) '$.fit_fhe_cte_cancellation_at', cte_status nvarchar(255) '$.fit_fhe_cte_status',
        nfse_number nvarchar(50), nfse_series nvarchar(50), nfse_status nvarchar(50) '$.fit_nse_status',
        nfse_issued_at datetimeoffset(7) '$.fit_nse_issued_at',
        nfse_cancellation_reason nvarchar(255) '$.fit_nse_cancelation_reason',
        predicted_at datetimeoffset(7) '$.fit_dpn_delivery_prediction_at',
        performance_finished_at datetimeoffset(7) '$.fit_dpn_performance_finished_at',
        invoices_mapping nvarchar(max) AS JSON, invoices_value decimal(18,2), invoices_volumes int,
        taxed_weight decimal(18,3), real_weight decimal(18,3), cubages_cubed_weight decimal(18,6), total_cubic_volume decimal(18,6),
        total decimal(18,2), freight_weight_subtotal decimal(18,2), collect_subtotal decimal(18,2),
        delivery_subtotal decimal(18,2), sec_cat_subtotal decimal(18,2), redispatch_subtotal decimal(18,2),
        itr_subtotal decimal(18,2), dispatch_subtotal decimal(18,2), gris_subtotal decimal(18,2),
        ad_valorem_subtotal decimal(18,2), toll_subtotal decimal(18,2), other_fees decimal(18,2),
        suframa_subtotal decimal(18,2), emergency_subtotal decimal(18,2), tde_subtotal decimal(18,2),
        additionals_subtotal decimal(18,2), adm_fee_subtotal decimal(18,2), trt_subtotal decimal(18,2),
        fiscal_cst nvarchar(10) '$.fit_f_l_cst_type', fiscal_cfop nvarchar(10) '$.fit_f_l_cfop_code',
        fiscal_icms decimal(18,2) '$.fit_f_l_tax_value', fiscal_pis decimal(18,2) '$.fit_f_l_pis_value',
        fiscal_cofins decimal(18,2) '$.fit_f_l_cofins_value', fiscal_base decimal(18,2) '$.fit_f_l_calculation_basis',
        fiscal_icms_rate decimal(18,6) '$.fit_f_l_tax_rate', fiscal_pis_rate decimal(18,6) '$.fit_f_l_pis_rate',
        fiscal_cofins_rate decimal(18,6) '$.fit_f_l_cofins_rate', fiscal_has_difal bit '$.fit_f_l_has_difal',
        fiscal_difal_origin decimal(18,2) '$.fit_f_l_difal_tax_value_origin',
        fiscal_difal_destination decimal(18,2) '$.fit_f_l_difal_tax_value_destination'
    )) j
    WHERE l.entity_name='FRETES' AND k.id IS NOT NULL
      AND (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name='FRETES')=1
), invoices AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           ROW_NUMBER() OVER (PARTITION BY l.source_scope,TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.id')) ORDER BY
              l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal) AS rn,
           ROW_NUMBER() OVER (PARTITION BY l.source_scope,CONVERT(nvarchar(100),JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_key')) ORDER BY
              CASE WHEN NULLIF(TRIM(JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_status')),N'') IS NOT NULL THEN 0
                   WHEN NULLIF(TRIM(JSON_VALUE(l.source_envelope,'$.record.fit_fhe_cte_status_result')),N'') IS NOT NULL THEN 1 ELSE 2 END,
              l.stored_utc DESC,l.business_date DESC,l.occurrence_ordinal) AS cte_rn
    FROM stg.production_export_latest l
    CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(l.source_envelope,'$.record') WITH (
        id bigint, created_at datetimeoffset(7), finished_at datetimeoffset(7),
        invoices_weight decimal(18,3), payment_type nvarchar(50),
        sender_name nvarchar(255) '$.fit_sdr_name', receiver_name nvarchar(255) '$.fit_rpt_name',
        receiver_document nvarchar(50) '$.fit_rpt_document',
        cte_key nvarchar(100) '$.fit_fhe_cte_key', cte_status nvarchar(255) '$.fit_fhe_cte_status',
        cte_result nvarchar(4000) '$.fit_fhe_cte_status_result'
    )) j
    WHERE l.entity_name='FATURAS_POR_CLIENTE' AND j.id IS NOT NULL
), locations AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           ROW_NUMBER() OVER (PARTITION BY l.source_scope,TRY_CONVERT(bigint,JSON_VALUE(l.source_envelope,'$.record.corporation_sequence_number')) ORDER BY
                l.stored_utc DESC,TRY_CONVERT(datetimeoffset(7),JSON_VALUE(l.source_envelope,'$.record.service_at')) DESC,
                l.business_date DESC,l.occurrence_ordinal) AS rn
    FROM stg.production_export_latest l
    CROSS APPLY (SELECT TOP (1) * FROM OPENJSON(l.source_envelope,'$.record') WITH (
        minute bigint '$.corporation_sequence_number',service_at datetimeoffset(7),invoices_volumes int,
        predicted_at datetimeoffset(7) '$.fit_dpn_delivery_prediction_at',
        destination_region nvarchar(255) '$.fit_dyn_name',destination_branch nvarchar(255) '$.fit_dyn_drt_nickname'
    )) j
    WHERE l.entity_name='LOCALIZACAO_CARGAS' AND j.minute IS NOT NULL
), combined AS (
    SELECT f.*,i.created_at,i.finished_at,i.invoices_weight,i.payment_type,
           COALESCE(NULLIF(TRIM(i.sender_name),N''),f.sender_nickname) AS sender_name,
           CASE WHEN f.receiver_document=i.receiver_document THEN COALESCE(NULLIF(TRIM(i.receiver_name),N''),f.receiver_nickname)
                ELSE f.receiver_nickname END AS receiver_name,
           NULLIF(TRIM(c.cte_result),N'') AS cte_result,
           COALESCE(NULLIF(TRIM(c.cte_status),N''),NULLIF(TRIM(f.cte_status),N'')) AS cte_real_status,
           c.stored_utc AS cte_stored_utc,loc.stored_utc AS location_stored_utc,
           COALESCE(loc.invoices_volumes,f.invoices_volumes,0) AS volumes,
           COALESCE(CONVERT(date,f.predicted_at),CONVERT(date,loc.predicted_at)) AS delivery_forecast,
           COALESCE(f.performance_finished_at,i.finished_at) AS performance_completion,
           NULLIF(TRIM(loc.destination_branch),N'') AS destination_branch,
           COALESCE(NULLIF(TRIM(loc.destination_region),N''),f.destination_state,N'SEM_REGIAO') AS destination_region,
           COALESCE(f.cte_issued_at,f.service_at) AS billing_date,
           CONVERT(decimal(18,2),COALESCE(f.freight_weight_subtotal,0)+COALESCE(f.collect_subtotal,0)
            +COALESCE(f.delivery_subtotal,0)+COALESCE(f.sec_cat_subtotal,0)+COALESCE(f.redispatch_subtotal,0)
            +COALESCE(f.itr_subtotal,0)+COALESCE(f.dispatch_subtotal,0)+COALESCE(f.gris_subtotal,0)
            +COALESCE(f.ad_valorem_subtotal,0)+COALESCE(f.toll_subtotal,0)+COALESCE(f.other_fees,0)
            +COALESCE(f.suframa_subtotal,0)+COALESCE(f.emergency_subtotal,0)+COALESCE(f.tde_subtotal,0)
            +COALESCE(f.additionals_subtotal,0)+COALESCE(f.adm_fee_subtotal,0)+COALESCE(f.trt_subtotal,0)) AS subtotal,
           CONVERT(bit,CASE WHEN f.cte_id IS NOT NULL OR NULLIF(TRIM(f.cte_key),N'') IS NOT NULL
                           OR f.cte_number IS NOT NULL OR f.cte_series IS NOT NULL THEN 1 ELSE 0 END) AS has_cte,
           CONVERT(bit,CASE WHEN f.classification COLLATE Latin1_General_CI_AI LIKE N'%bloqueio%'
                 AND (f.classification COLLATE Latin1_General_CI_AI LIKE N'%anulacao%'
                      OR f.classification COLLATE Latin1_General_CI_AI LIKE N'%isolamento%') THEN 1 ELSE 0 END) AS blocked,
           UPPER(NULLIF(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(f.payer_document),
                  N'.',N''),N'-',N''),N'/',N''),N' ',N''),NCHAR(9),N''),N'')) COLLATE Latin1_General_CI_AS AS payer_key
    FROM fretes f
    LEFT HASH JOIN invoices i ON i.source_scope=f.source_scope AND i.id=f.id AND i.rn=1
    LEFT HASH JOIN invoices c ON c.source_scope=f.source_scope AND c.cte_key=f.cte_key AND c.cte_rn=1
    LEFT HASH JOIN locations loc ON loc.source_scope=f.source_scope AND loc.minute=f.corporation_sequence_number AND loc.rn=1
    WHERE f.rn=1
), evidence AS (
    SELECT combined.*,
           CASE
               WHEN cte_cancellation_at IS NOT NULL OR cte_status COLLATE Latin1_General_CI_AI LIKE N'%cancelad%'
                 OR cte_status COLLATE Latin1_General_CI_AI LIKE N'%cancell%' THEN N'fretes.CT-e'
               WHEN cte_real_status COLLATE Latin1_General_CI_AI LIKE N'%cancelad%'
                 OR cte_real_status COLLATE Latin1_General_CI_AI LIKE N'%cancell%' THEN N'faturas_por_cliente.status_cte'
               WHEN cte_result COLLATE Latin1_General_CI_AI LIKE N'%cancelad%'
                 OR cte_result COLLATE Latin1_General_CI_AI LIKE N'%cancell%' THEN N'faturas_por_cliente.status_cte_result'
               WHEN has_cte=1 AND status COLLATE Latin1_General_CI_AI IN (N'cancelado',N'cancelada',N'canceled',N'cancelled')
                   THEN N'fretes.status'
           END AS cancellation_evidence
    FROM combined
), replaced AS (
    SELECT evidence.*,
        MAX(CASE WHEN has_cte=1 AND cancellation_evidence IS NULL THEN COALESCE(cte_issued_at,cte_created_at) END)
            OVER(PARTITION BY source_scope,branch_id,corporation_sequence_number) AS current_cte_date
    FROM evidence
)
SELECT f.*,COALESCE(f.branch_id,n.filial_id) AS resolved_branch_id,b.documento AS branch_document,
       COALESCE(r.filial_destino_nome,NULLIF(TRIM(f.branch_name),N'')) AS billing_branch,
       COALESCE(r.filial_destino_key,LOWER(NULLIF(TRIM(f.branch_name),N''))) AS billing_branch_key,
       DATEDIFF(DAY,f.delivery_forecast,CONVERT(date,f.performance_completion)) AS performance_days,
       CONVERT(bit,CASE WHEN COALESCE(f.courtesy,0)=1 OR f.blocked=1 THEN 0 ELSE 1 END) AS origin_eligible,
       CONVERT(bit,CASE WHEN COALESCE(f.courtesy,0)=1 OR f.blocked=1 OR f.cancellation_evidence IS NOT NULL
             OR f.billing_date IS NULL THEN 0 ELSE 1 END) AS billing_eligible
FROM replaced f
LEFT JOIN ref.dashboard_frete_filial_nome n ON f.branch_id IS NULL
    AND n.filial_nome_key=LOWER(TRIM(f.branch_name)) COLLATE Latin1_General_CI_AS
LEFT JOIN ref.dashboard_frete_filial_documento b ON b.filial_id=COALESCE(f.branch_id,n.filial_id)
LEFT JOIN ref.dashboard_faturamento_atribuicao_filial r ON r.pagador_documento_key=f.payer_key
WHERE f.cancellation_evidence IS NULL OR f.current_cte_date IS NULL
   OR f.corporation_sequence_number IS NULL OR f.branch_id IS NULL
   OR COALESCE(f.cte_issued_at,f.cte_created_at)>=f.current_cte_date;

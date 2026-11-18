-- Duas fontes que podem mudar o resultado de um frete já recebido.
INSERT ctl.dashboard_projection_dependency (projection_name,entity_name)
VALUES (N'FRETES_VINCULOS','FATURAS_POR_CLIENTE'),(N'FRETES_VINCULOS','LOCALIZACAO_CARGAS');
CREATE TABLE core.dashboard_freight_support (
    run_id uniqueidentifier NOT NULL, occurrence_ordinal int NOT NULL,
    entity_name varchar(40) NOT NULL, source_scope char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    business_date date NOT NULL, stored_utc datetime2(3) NOT NULL,
    id bigint NULL,rank_id bigint NULL,cte_key nvarchar(100) NULL,rank_cte_key nvarchar(100) NULL,cte_priority tinyint NULL,
    created_at datetimeoffset(7) NULL,finished_at datetimeoffset(7) NULL,invoices_weight decimal(18,3) NULL,
    payment_type nvarchar(50) NULL,sender_name nvarchar(255) NULL,receiver_name nvarchar(255) NULL,
    receiver_document nvarchar(50) NULL,cte_status nvarchar(255) NULL,cte_result nvarchar(4000) NULL,
    minute bigint NULL,rank_minute bigint NULL,service_at datetimeoffset(7) NULL,rank_service_at datetimeoffset(7) NULL,
    invoices_volumes int NULL,predicted_at datetimeoffset(7) NULL,
    destination_region nvarchar(255) NULL,destination_branch nvarchar(255) NULL,
    CONSTRAINT PK_dashboard_freight_support PRIMARY KEY(run_id,occurrence_ordinal)
);
CREATE INDEX IX_dashboard_support_invoice_id ON core.dashboard_freight_support
    (source_scope,rank_id,stored_utc DESC,business_date DESC,occurrence_ordinal)
    WHERE entity_name='FATURAS_POR_CLIENTE' AND id IS NOT NULL WITH(MAXDOP=1);
CREATE INDEX IX_dashboard_support_invoice_cte ON core.dashboard_freight_support
    (source_scope,rank_cte_key,cte_priority,stored_utc DESC,business_date DESC,occurrence_ordinal)
    WHERE entity_name='FATURAS_POR_CLIENTE' AND id IS NOT NULL WITH(MAXDOP=1);
CREATE INDEX IX_dashboard_support_location ON core.dashboard_freight_support
    (source_scope,rank_minute,stored_utc DESC,rank_service_at DESC,business_date DESC,occurrence_ordinal)
    WHERE entity_name='LOCALIZACAO_CARGAS' AND minute IS NOT NULL WITH(MAXDOP=1);
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_fretes_vinculos
    @entity_name varchar(40), @source_scope char(64), @business_date date
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET LOCK_TIMEOUT 3000;
    IF @@TRANCOUNT<>0 THROW 52070,'FRETES_VINCULOS_OUTER_TRANSACTION_REFUSED',1;
    IF @entity_name IS NULL OR @entity_name NOT IN ('FATURAS_POR_CLIENTE','LOCALIZACAO_CARGAS') OR @source_scope IS NULL OR @business_date IS NULL
        THROW 52071,'FRETES_VINCULOS_PARTITION_REQUIRED',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock int, @run uniqueidentifier, @old uniqueidentifier, @expected int, @capture datetime2(7);
        EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fretes:vinculos', @LockMode='Exclusive',
            @LockOwner='Transaction', @LockTimeout=0;
        IF @lock<0 THROW 52072,'FRETES_VINCULOS_PUBLICATION_BUSY',1;
        IF (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name=@entity_name)<>1
            THROW 52073,'FRETES_VINCULOS_SINGLE_SOURCE_REQUIRED',1;
        SELECT @run=c.latest_run_id,@capture=c.stored_utc,@expected=r.row_count
        FROM ctl.production_ingestion_coverage c JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id
            AND r.run_state='STORED' AND r.source_scope=c.source_scope AND r.entity_name=c.entity_name
            AND r.period_start=c.business_date AND r.period_end=c.business_date
        WHERE c.source_scope=@source_scope AND c.business_date=@business_date AND c.entity_name=@entity_name;
        IF @run IS NULL OR @expected IS NULL OR @expected NOT BETWEEN 0 AND 100000
            THROW 52074,'FRETES_VINCULOS_COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
        SELECT @old=applied_run_id FROM ctl.dashboard_projection_partition
        WHERE projection_name=N'FRETES_VINCULOS' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @old=@run BEGIN COMMIT; RETURN; END;
        IF EXISTS (SELECT 1 FROM stg.production_export_occurrence WHERE run_id=@run
                   AND ISNULL(ISJSON(JSON_QUERY(source_envelope,'$.record'),OBJECT),0)<>1)
            THROW 52075,'FRETES_VINCULOS_RECORD_REQUIRED',1;

        DECLARE @inserted int;
        IF @entity_name='FATURAS_POR_CLIENTE'
        BEGIN
            INSERT core.dashboard_freight_support(run_id,occurrence_ordinal,entity_name,source_scope,business_date,stored_utc,id,cte_key,created_at,finished_at,invoices_weight,payment_type,sender_name,receiver_name,receiver_document,cte_status,cte_result,rank_id,rank_cte_key,cte_priority)
            SELECT @run,o.occurrence_ordinal,@entity_name,@source_scope,@business_date,@capture,j.id,j.cte_key,j.created_at,j.finished_at,j.invoices_weight,j.payment_type,j.sender_name,j.receiver_name,j.receiver_document,j.cte_status,j.cte_result,TRY_CONVERT(bigint,JSON_VALUE(o.source_envelope,'$.record.id')),CONVERT(nvarchar(100),JSON_VALUE(o.source_envelope,'$.record.fit_fhe_cte_key')),CASE WHEN NULLIF(TRIM(JSON_VALUE(o.source_envelope,'$.record.fit_fhe_cte_status')),N'') IS NOT NULL THEN 0 WHEN NULLIF(TRIM(JSON_VALUE(o.source_envelope,'$.record.fit_fhe_cte_status_result')),N'') IS NOT NULL THEN 1 ELSE 2 END
            FROM stg.production_export_occurrence o
            CROSS APPLY (SELECT TOP(1) * FROM OPENJSON(o.source_envelope,'$.record') WITH (
        id bigint, created_at datetimeoffset(7), finished_at datetimeoffset(7),
        invoices_weight decimal(18,3), payment_type nvarchar(50),
        sender_name nvarchar(255) '$.fit_sdr_name', receiver_name nvarchar(255) '$.fit_rpt_name',
        receiver_document nvarchar(50) '$.fit_rpt_document',
        cte_key nvarchar(100) '$.fit_fhe_cte_key', cte_status nvarchar(255) '$.fit_fhe_cte_status',
        cte_result nvarchar(4000) '$.fit_fhe_cte_status_result'
    )) j
            WHERE o.run_id=@run OPTION(MAXDOP 1,MAX_GRANT_PERCENT=0.2);
            SET @inserted=@@ROWCOUNT;
        END
        ELSE
        BEGIN
            INSERT core.dashboard_freight_support(run_id,occurrence_ordinal,entity_name,source_scope,business_date,stored_utc,minute,service_at,invoices_volumes,predicted_at,destination_region,destination_branch,rank_minute,rank_service_at)
            SELECT @run,o.occurrence_ordinal,@entity_name,@source_scope,@business_date,@capture,j.minute,j.service_at,j.invoices_volumes,j.predicted_at,j.destination_region,j.destination_branch,TRY_CONVERT(bigint,JSON_VALUE(o.source_envelope,'$.record.corporation_sequence_number')),TRY_CONVERT(datetimeoffset(7),JSON_VALUE(o.source_envelope,'$.record.service_at'))
            FROM stg.production_export_occurrence o
            CROSS APPLY (SELECT TOP(1) * FROM OPENJSON(o.source_envelope,'$.record') WITH (
        minute bigint '$.corporation_sequence_number',service_at datetimeoffset(7),invoices_volumes int,
        predicted_at datetimeoffset(7) '$.fit_dpn_delivery_prediction_at',
        destination_region nvarchar(255) '$.fit_dyn_name',destination_branch nvarchar(255) '$.fit_dyn_drt_nickname'
    )) j
            WHERE o.run_id=@run OPTION(MAXDOP 1,MAX_GRANT_PERCENT=0.2);
            SET @inserted=@@ROWCOUNT;
        END;
        IF @inserted<>@expected THROW 52076,'FRETES_VINCULOS_CARDINALITY_MISMATCH',1;
        DELETE FROM core.dashboard_freight_support WHERE run_id=@old;
        IF NOT EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
            WHERE entity_name=@entity_name AND source_scope=@source_scope AND business_date=@business_date AND latest_run_id=@run)
            OR EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(HOLDLOCK)
            WHERE entity_name=@entity_name AND source_scope<>@source_scope)
            THROW 52077,'FRETES_VINCULOS_CAPTURE_SUPERSEDED',1;
        UPDATE ctl.dashboard_projection_partition SET applied_run_id=@run,capture_utc=@capture,published_utc=SYSUTCDATETIME()
        WHERE projection_name=N'FRETES_VINCULOS' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @@ROWCOUNT=0 INSERT ctl.dashboard_projection_partition
            (projection_name,entity_name,source_scope,business_date,applied_run_id,capture_utc,published_utc)
            VALUES (N'FRETES_VINCULOS',@entity_name,@source_scope,@business_date,@run,@capture,SYSUTCDATETIME());
        COMMIT;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
CREATE OR ALTER VIEW core.dashboard_fretes_publicacao_preparada
AS
-- O recebimento valida um unico objeto record por envelope. O limite explicita essa cardinalidade.
WITH fretes AS (
    SELECT j.*,l.source_scope,l.stored_utc,
           JSON_QUERY(l.source_envelope,'$.record') AS metadata,
           CONVERT(bigint,1) AS rn
    FROM core.dashboard_fretes_vigentes_preparada l
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
), invoices AS (
    SELECT s.*,
           ROW_NUMBER() OVER(PARTITION BY source_scope,rank_id ORDER BY stored_utc DESC,business_date DESC,occurrence_ordinal) AS rn,
           ROW_NUMBER() OVER(PARTITION BY source_scope,rank_cte_key ORDER BY cte_priority,stored_utc DESC,business_date DESC,occurrence_ordinal) AS cte_rn
    FROM core.dashboard_freight_support s WHERE entity_name='FATURAS_POR_CLIENTE' AND id IS NOT NULL
), locations AS (
    SELECT s.*,ROW_NUMBER() OVER(PARTITION BY source_scope,rank_minute ORDER BY
        stored_utc DESC,rank_service_at DESC,business_date DESC,occurrence_ordinal) AS rn
    FROM core.dashboard_freight_support s WHERE entity_name='LOCALIZACAO_CARGAS' AND minute IS NOT NULL
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
    LEFT JOIN invoices i ON i.source_scope=f.source_scope AND i.id=f.id AND i.rn=1
    LEFT JOIN invoices c ON c.source_scope=f.source_scope AND c.cte_key=f.cte_key AND c.cte_rn=1
    LEFT JOIN locations loc ON loc.source_scope=f.source_scope AND loc.minute=f.corporation_sequence_number AND loc.rn=1
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

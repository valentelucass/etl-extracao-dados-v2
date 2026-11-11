-- Identity and freshness follow InventarioMapper and InventarioRepository in the V1.
CREATE OR ALTER VIEW pub.vw_inventario_powerbi
AS
WITH scope_guard AS (
    SELECT COUNT_BIG(DISTINCT source_scope) AS scopes
    FROM ctl.production_ingestion_coverage
    WHERE entity_name IN ('INVENTARIO','FRETES')
), inventory AS (
    SELECT j.*,
           l.occurrence_ordinal, l.stored_utc, l.business_date,
           JSON_QUERY(l.source_envelope,'$.record') AS record_json,
           CONVERT(datetimeoffset(7),j.started) AS started_at,
           CONVERT(nvarchar(64),LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',
                CONVERT(varchar(max),CONCAT(j.order_number,'|',
                    COALESCE(CONVERT(varchar(20),j.freight_number),'null'),'|',
                    COALESCE(j.invoice_list,N''),'|',j.started) COLLATE Latin1_General_100_BIN2_UTF8)),2))) AS item_key
    FROM stg.production_export_latest l
    CROSS JOIN scope_guard g
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        order_number bigint '$.sequence_code',
        freight_number bigint '$.cnr_c_s_fit_corporation_sequence_number',
        payer nvarchar(max) '$.cnr_c_s_fit_pyr_nickname',
        sender nvarchar(max) '$.cnr_c_s_fit_sdr_nickname',
        origin_city nvarchar(max) '$.cnr_c_s_fit_sdr_ads_cty_name',
        receiver nvarchar(max) '$.cnr_c_s_fit_rpt_nickname',
        destination_city nvarchar(max) '$.cnr_c_s_fit_rpt_ads_cty_name',
        delivery_region nvarchar(max) '$.cnr_c_s_fit_dyn_name',
        delivery_branch nvarchar(max) '$.cnr_c_s_fit_dyn_drt_nickname',
        order_branch nvarchar(max) '$.cnr_crn_psn_nickname',
        order_type nvarchar(max) '$.type',
        started nvarchar(100) '$.started_at',
        finished_at datetimeoffset(7) '$.finished_at',
        order_status nvarchar(max) '$.status',
        checker nvarchar(max) '$.cnr_cis_eoe_psn_name',
        invoice_list nvarchar(max) '$.cnr_c_s_fit_invoices_mapping' AS JSON,
        invoice_value decimal(18,2) '$.cnr_c_s_fit_invoices_value',
        real_weight decimal(18,3) '$.cnr_c_s_fit_real_weight',
        cubic_volume decimal(18,3) '$.cnr_c_s_fit_total_cubic_volume',
        taxed_weight decimal(18,3) '$.cnr_c_s_fit_taxed_weight',
        volume_count int '$.cnr_c_s_fit_invoices_volumes',
        read_volumes int '$.cnr_c_s_read_volumes',
        delivery_forecast datetimeoffset(7) '$.cnr_c_s_fit_dpn_delivery_prediction_at',
        performance_finished_at datetimeoffset(7) '$.cnr_c_s_fit_dpn_performance_finished_at',
        last_occurrence_at datetimeoffset(7) '$.cnr_c_s_fit_fte_lce_occurrence_at',
        last_occurrence_description nvarchar(max) '$.cnr_c_s_fit_fte_lce_ore_description'
    ) j
    WHERE l.entity_name='INVENTARIO' AND g.scopes=1 AND j.order_number IS NOT NULL
), selected AS (
    SELECT *,
           ROW_NUMBER() OVER(PARTITION BY item_key ORDER BY
               COALESCE(CONVERT(datetime2,performance_finished_at),CONVERT(datetime2,finished_at),
                        CONVERT(datetime2,started_at)) DESC,
               stored_utc DESC,business_date DESC,occurrence_ordinal DESC) AS rank_in_key
    FROM inventory
), receipt_history AS (
    -- The V1 keeps a positive receipt flag on subsequent updates to the same key.
    SELECT DISTINCT CONVERT(nvarchar(64),LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',
        CONVERT(varchar(max),CONCAT(j.order_number,'|',
            COALESCE(CONVERT(varchar(20),j.freight_number),'null'),'|',
            COALESCE(j.invoice_list,N''),'|',j.started) COLLATE Latin1_General_100_BIN2_UTF8)),2))) AS item_key
    FROM ctl.production_ingestion_run r
    JOIN stg.production_export_occurrence o ON o.run_id=r.run_id
    CROSS APPLY OPENJSON(o.source_envelope,'$.record') WITH (
        order_number bigint '$.sequence_code',
        freight_number bigint '$.cnr_c_s_fit_corporation_sequence_number',
        invoice_list nvarchar(max) '$.cnr_c_s_fit_invoices_mapping' AS JSON,
        started nvarchar(100) '$.started_at',
        receipt_description nvarchar(max) '$.cnr_c_s_fit_fte_lce_ore_description'
    ) j
    WHERE r.entity_name='INVENTARIO' AND r.run_state='STORED' AND j.order_number IS NOT NULL
      AND j.receipt_description COLLATE Latin1_General_100_CI_AI_SC LIKE N'%comprovante%entrega%anexado%'
      AND EXISTS (SELECT 1 FROM ctl.production_ingestion_coverage c
          WHERE c.entity_name='INVENTARIO' AND c.source_scope=r.source_scope)
), freight_branches AS (
    SELECT j.freight_number, MAX(j.branch_name) AS branch_name
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        freight_number bigint '$.corporation_sequence_number',
        branch_name nvarchar(max) '$.fit_crn_psn_nickname'
    ) j
    WHERE l.entity_name='FRETES' AND j.freight_number IS NOT NULL
    GROUP BY j.freight_number
    HAVING COUNT_BIG(DISTINCT j.branch_name)=1
)
SELECT CONVERT(time(0),i.started_at) AS [Hora (Início)],
       i.item_key AS [Identificador Único],
       i.freight_number AS [Nº Minuta],
       CONVERT(nvarchar(255),i.payer) AS [Pagador],
       CONVERT(nvarchar(255),i.sender) AS [Remetente],
       CONVERT(nvarchar(255),i.origin_city) AS [Origem],
       CONVERT(nvarchar(255),i.receiver) AS [Destinatário],
       CONVERT(nvarchar(255),i.destination_city) AS [Destino],
       CONVERT(nvarchar(255),i.delivery_region) AS [Região Entrega],
       CONVERT(nvarchar(255),i.delivery_branch) AS [Filial Entregadora],
       i.order_number AS [N° Ordem],
       CONVERT(nvarchar(255),i.order_branch) AS [Filial],
       CONVERT(nvarchar(255),i.order_branch) AS [Filial da Ordem de Conferência],
       CONVERT(nvarchar(255),COALESCE(f.branch_name,i.order_branch)) AS [Filial Emissora do Frete],
       CONVERT(nvarchar(100),CASE i.order_type
           WHEN 'CheckIn::Order::Loading' THEN N'Carregamento'
           WHEN 'CheckIn::Order::Unloading' THEN N'Descarregamento'
           WHEN 'CheckIn::Order::Picking' THEN N'Picking'
           WHEN 'CheckIn::Order::Receipt' THEN N'Recebimento'
           WHEN 'CheckIn::Order::Return' THEN N'Retorno'
           ELSE i.order_type END) AS [Tipo],
       i.started_at AS [Data/Hora início],
       i.finished_at AS [Data/Hora fim],
       CONVERT(nvarchar(100),CASE i.order_status WHEN 'pending' THEN N'pendente'
           WHEN 'finished' THEN N'finalizado' ELSE i.order_status END) AS [Status],
       CONVERT(nvarchar(255),i.checker) AS [Conferente],
       REPLACE(REPLACE(REPLACE(i.invoice_list,N'[',N''),N']',N''),N'"',N'') AS [Notas Fiscais],
       i.invoice_value AS [Valor de NF],
       i.real_weight AS [Peso Real],
       i.cubic_volume AS [M³],
       i.taxed_weight AS [Peso Taxado],
       i.volume_count AS [Volumes],
       i.read_volumes AS [Volumes Lidos],
       i.delivery_forecast AS [Previsão de Entrega],
       i.performance_finished_at AS [Finalização da Performance],
       i.performance_finished_at AS [Data de Finalização],
       i.last_occurrence_at AS [Data Última Ocorrência],
       CONVERT(nvarchar(500),i.last_occurrence_description) AS [Ocorrência],
       CASE WHEN receipt.item_key IS NOT NULL THEN N'Sim' ELSE N'Não' END AS [Comprovante Anexado],
       i.record_json AS [Metadata],
       CONVERT(datetime2(7),TODATETIMEOFFSET(i.stored_utc,'+00:00')
           AT TIME ZONE 'E. South America Standard Time') AS [Data de extracao]
FROM selected i
LEFT JOIN freight_branches f ON f.freight_number=i.freight_number
LEFT JOIN receipt_history receipt ON receipt.item_key=i.item_key
WHERE i.rank_in_key=1;

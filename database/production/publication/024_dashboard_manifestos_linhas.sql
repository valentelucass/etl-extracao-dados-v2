-- Typed V2 source paths follow the 6399 DTO and the V1 Manifestos mapper.
CREATE OR ALTER VIEW core.dashboard_manifestos_linhas
AS
WITH raw_rows AS (
    SELECT j.*,l.stored_utc,l.business_date,l.occurrence_ordinal,JSON_QUERY(l.source_envelope,'$.record') AS metadata
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        sequence_code bigint '$.sequence_code',
        status nvarchar(50) '$.status',
        created_at datetimeoffset(7) '$.created_at',
        departured_at datetimeoffset(7) '$.departured_at',
        closed_at datetimeoffset(7) '$.closed_at',
        finished_at datetimeoffset(7) '$.finished_at',
        mdfe_number int '$.mft_mfs_number',
        mdfe_key nvarchar(100) '$.mft_mfs_key',
        mdfe_status nvarchar(50) '$.mdfe_status',
        distribution_pole nvarchar(255) '$.mft_ape_name',
        classification nvarchar(255) '$.mft_man_name',
        vehicle_plate nvarchar(10) '$.mft_vie_license_plate',
        vehicle_type nvarchar(255) '$.mft_vie_vee_name',
        vehicle_owner nvarchar(255) '$.mft_vie_onr_name',
        driver_name nvarchar(255) '$.mft_mdr_iil_name',
        branch_nickname nvarchar(255) '$.mft_crn_psn_nickname',
        vehicle_departure_km int '$.vehicle_departure_km',
        closing_km int '$.closing_km',
        traveled_km int '$.traveled_km',
        invoices_count int '$.invoices_count',
        invoices_volumes int '$.invoices_volumes',
        invoices_weight decimal(18,3) '$.invoices_weight',
        total_taxed_weight decimal(18,3) '$.total_taxed_weight',
        total_cubic_volume decimal(18,6) '$.total_cubic_volume',
        invoices_value decimal(18,2) '$.invoices_value',
        manifest_freights_total decimal(18,2) '$.manifest_freights_total',
        pick_sequence_code bigint '$.mft_pfs_pck_sequence_code',
        contract_number nvarchar(50) '$.mft_cat_cot_number',
        contract_type nvarchar(50) '$.contract_type',
        driver_contract_type nvarchar(50) '$.mft_mdr_contract_type',
        calculation_type nvarchar(50) '$.calculation_type',
        cargo_type nvarchar(255) '$.cargo_type',
        daily_subtotal decimal(18,2) '$.daily_subtotal',
        total_cost decimal(18,2) '$.total_cost',
        freight_subtotal decimal(18,2) '$.freight_subtotal',
        fuel_subtotal decimal(18,2) '$.fuel_subtotal',
        toll_subtotal decimal(18,2) '$.toll_subtotal',
        driver_services_total decimal(18,2) '$.driver_services_total',
        operational_expenses_total decimal(18,2) '$.operational_expenses_total',
        inss_value decimal(18,2) '$.mft_a_t_inss_value',
        sest_senat_value decimal(18,2) '$.mft_a_t_sest_senat_value',
        ir_value decimal(18,2) '$.mft_a_t_ir_value',
        paying_total decimal(18,2) '$.paying_total',
        manual_km bit '$.manual_km',
        generate_mdfe bit '$.generate_mdfe',
        monitoring_request bit '$.monitoring_request',
        uniq_destinations_count int '$.uniq_destinations_count',
        mobile_read_at datetimeoffset(7) '$.mobile_read_at',
        km decimal(18,2) '$.km',
        delivery_manifest_items_count int '$.delivery_manifest_items_count',
        transfer_manifest_items_count int '$.transfer_manifest_items_count',
        pick_manifest_items_count int '$.pick_manifest_items_count',
        dispatch_draft_manifest_items_count int '$.dispatch_draft_manifest_items_count',
        consolidation_manifest_items_count int '$.consolidation_manifest_items_count',
        reverse_pick_manifest_items_count int '$.reverse_pick_manifest_items_count',
        manifest_items_count int '$.manifest_items_count',
        finalized_manifest_items_count int '$.finalized_manifest_items_count',
        calculated_pick_count int '$.calculated_pick_count',
        calculated_delivery_count int '$.calculated_delivery_count',
        calculated_dispatch_count int '$.calculated_dispatch_count',
        calculated_consolidation_count int '$.calculated_consolidation_count',
        calculated_reverse_pick_count int '$.calculated_reverse_pick_count',
        pick_subtotal decimal(18,2) '$.pick_subtotal',
        delivery_subtotal decimal(18,2) '$.delivery_subtotal',
        dispatch_subtotal decimal(18,2) '$.dispatch_subtotal',
        consolidation_subtotal decimal(18,2) '$.consolidation_subtotal',
        reverse_pick_subtotal decimal(18,2) '$.reverse_pick_subtotal',
        advance_subtotal decimal(18,2) '$.advance_subtotal',
        fleet_costs_subtotal decimal(18,2) '$.fleet_costs_subtotal',
        additionals_subtotal decimal(18,2) '$.additionals_subtotal',
        discounts_subtotal decimal(18,2) '$.discounts_subtotal',
        discount_value decimal(18,2) '$.discount_value',
        adjustment_comments nvarchar(max) '$.mft_aoe_comments',
        contract_status nvarchar(50) '$.mft_cat_cot_status',
        iks_id nvarchar(100) '$.mft_iks_id',
        programacao_sequence_code nvarchar(50) '$.mft_s_n_sequence_code',
        programacao_starting_at datetimeoffset(7) '$.mft_s_n_starting_at',
        programacao_ending_at datetimeoffset(7) '$.mft_s_n_ending_at',
        trailer1_license_plate nvarchar(10) '$.mft_tl1_license_plate',
        trailer1_weight_capacity decimal(18,2) '$.mft_tl1_weight_capacity',
        trailer2_license_plate nvarchar(10) '$.mft_tl2_license_plate',
        trailer2_weight_capacity decimal(18,2) '$.mft_tl2_weight_capacity',
        vehicle_weight_capacity decimal(18,2) '$.mft_vie_weight_capacity',
        vehicle_cubic_weight decimal(18,2) '$.mft_vie_cubic_weight',
        capacidade_kg decimal(18,2) '$.mft_vie_weight_capacity',
        unloading_recipient_names nvarchar(max) '$.mft_mte_unloading_recipient_names' AS JSON,
        delivery_region_names nvarchar(max) '$.mft_mte_delivery_region_names' AS JSON,
        programacao_cliente nvarchar(255) '$.mft_s_n_svs_sge_pyr_nickname',
        programacao_tipo_servico nvarchar(255) '$.mft_s_n_svs_sge_sse_name',
        creation_user_name nvarchar(255) '$.mft_uer_name',
        adjustment_user_name nvarchar(255) '$.mft_aoe_rer_name'
    ) j
    WHERE l.entity_name='MANIFESTOS' AND j.sequence_code IS NOT NULL
      AND (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage
           WHERE entity_name IN ('MANIFESTOS','FRETES','COLETAS'))=1
), keyed AS (
    SELECT r.*,CONVERT(nvarchar(100),CASE
        WHEN r.mdfe_number IS NOT NULL THEN CONCAT(COALESCE(r.pick_sequence_code,r.sequence_code),N'_MDFE_',r.mdfe_number)
        WHEN r.pick_sequence_code IS NOT NULL THEN CONVERT(nvarchar(100),r.pick_sequence_code)
        ELSE LOWER(CONVERT(char(64),HASHBYTES('SHA2_256',CONVERT(varchar(max),
            CONCAT(N'{',stable.fields,N'}') COLLATE Latin1_General_100_BIN2_UTF8)),2)) END) AS identificador_unico
    FROM raw_rows r
    OUTER APPLY (
        SELECT STRING_AGG(CONCAT(N'"',STRING_ESCAPE(j.[key],'json'),N'":',CASE j.[type]
            WHEN 0 THEN N'null' WHEN 1 THEN CONCAT(N'"',STRING_ESCAPE(j.value,'json'),N'"') ELSE j.value END),N',')
            WITHIN GROUP(ORDER BY j.[key] COLLATE Latin1_General_100_BIN2) AS fields
        FROM OPENJSON(r.metadata) j
        WHERE r.mdfe_number IS NULL AND r.pick_sequence_code IS NULL
          AND j.[key] NOT IN (N'mobile_read_at',N'departured_at',N'closed_at',N'finished_at',
              N'vehicle_departure_km',N'closing_km',N'traveled_km',N'finalized_manifest_items_count',
              N'mft_mfs_number',N'mft_mfs_key',N'mdfe_status',N'mft_aoe_comments',N'mft_aoe_rer_name')
    ) stable
), ranked AS (
    SELECT *,ROW_NUMBER() OVER(PARTITION BY sequence_code,
        COALESCE(CONVERT(nvarchar(100),pick_sequence_code),identificador_unico),COALESCE(mdfe_number,-1)
        ORDER BY COALESCE(CONVERT(datetime2(7),finished_at),CONVERT(datetime2(7),closed_at),
            CONVERT(datetime2(7),departured_at),CONVERT(datetime2(7),created_at)) DESC,
            stored_utc DESC,business_date DESC,occurrence_ordinal DESC) AS row_rank
    FROM keyed
)
SELECT r.*,
       CONVERT(nvarchar(max),JSON_VALUE(r.metadata,'$.operational_comments')) AS obs_operacional,
       CONVERT(nvarchar(max),JSON_VALUE(r.metadata,'$.closing_comments')) AS obs_financeira,
       CONVERT(datetime2(7),TODATETIMEOFFSET(r.stored_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS data_extracao,
       CONVERT(bit,0) AS excluido_na_origem
FROM ranked r WHERE r.row_rank=1;

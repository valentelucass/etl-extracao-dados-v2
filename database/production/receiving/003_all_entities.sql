-- 0762: versioned receiving evolution. Existing 001/002, identities and grants are immutable.
IF DB_NAME()<>N'ETL_SISTEMA_V2' THROW 51900,'PRODUCTION_DATABASE_REQUIRED',1;
GO
CREATE TABLE ctl.production_export_template (
 entity_name varchar(40) NOT NULL PRIMARY KEY,
 report_id int NOT NULL UNIQUE,
 row_date_field varchar(128) COLLATE Latin1_General_100_BIN2 NULL,
 expected_field_count int NOT NULL CHECK(expected_field_count BETWEEN 1 AND 512),
 CHECK(row_date_field IS NOT NULL OR entity_name='MANIFESTOS')
);
CREATE TABLE ctl.production_export_field (
 entity_name varchar(40) NOT NULL REFERENCES ctl.production_export_template(entity_name),
 field_name varchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
 PRIMARY KEY(entity_name,field_name)
);
INSERT ctl.production_export_template VALUES('CONSOLIDACAO_DETALHADA',11067,'issue_date',39);
INSERT ctl.production_export_field VALUES
('CONSOLIDACAO_DETALHADA','ctd_fgs_crn_psn_nickname'),
('CONSOLIDACAO_DETALHADA','sequence_code'),
('CONSOLIDACAO_DETALHADA','issue_date'),
('CONSOLIDACAO_DETALHADA','ctd_rhr_cpy_nickname'),
('CONSOLIDACAO_DETALHADA','ctd_rhr_cpy_document'),
('CONSOLIDACAO_DETALHADA','document_number'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_fhe_cte_number'),
('CONSOLIDACAO_DETALHADA','charged_value'),
('CONSOLIDACAO_DETALHADA','subtotal'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_subtotal'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_total'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_taxed_weight'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_fte_has_delivery_receipt'),
('CONSOLIDACAO_DETALHADA','bill_status'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_service_at'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_corporation_sequence_number'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_invoices_volumes'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_invoices_mapping'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_dos_cty_name'),
('CONSOLIDACAO_DETALHADA','ctd_sdr_document'),
('CONSOLIDACAO_DETALHADA','ctd_sdr_nickname'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_oas_cty_name'),
('CONSOLIDACAO_DETALHADA','ctd_rpt_document'),
('CONSOLIDACAO_DETALHADA','ctd_rpt_nickname'),
('CONSOLIDACAO_DETALHADA','ctd_rpt_charge_tde'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_dpn_delivery_prediction_at'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_finished_at'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_dpn_finished_within_deadline'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_fte_lce_ore_description'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_fte_foe_ore_description'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_fte_foe_occurrence_at'),
('CONSOLIDACAO_DETALHADA','ctd_fgs_d_t_created_at'),
('CONSOLIDACAO_DETALHADA','invoices_volumes'),
('CONSOLIDACAO_DETALHADA','real_weight'),
('CONSOLIDACAO_DETALHADA','taxed_weight'),
('CONSOLIDACAO_DETALHADA','invoices_value'),
('CONSOLIDACAO_DETALHADA','ctd_diy_cin_tda_value'),
('CONSOLIDACAO_DETALHADA','ctd_ant_document'),
('CONSOLIDACAO_DETALHADA','charged_value_details');
INSERT ctl.production_export_template VALUES('OCORRENCIAS',12072,'occurrence_at',20);
INSERT ctl.production_export_field VALUES
('OCORRENCIAS','ice_f_e_fit_corporation_sequence_number'),
('OCORRENCIAS','ice_f_e_ioe_number'),
('OCORRENCIAS','ice_fit_fhe_cte_number'),
('OCORRENCIAS','ice_ore_code'),
('OCORRENCIAS','ice_ore_description'),
('OCORRENCIAS','occurrence_at'),
('OCORRENCIAS','on_mobile'),
('OCORRENCIAS','ice_uer_name'),
('OCORRENCIAS','ice_fit_service_at'),
('OCORRENCIAS','ice_fit_dpn_delivery_prediction_at'),
('OCORRENCIAS','occurrence_schedule_at'),
('OCORRENCIAS','ice_fit_dpn_performance_finished_at'),
('OCORRENCIAS','delivery_status'),
('OCORRENCIAS','ice_fit_fln_cln_nickname'),
('OCORRENCIAS','ice_fit_dpn_manual_schedule'),
('OCORRENCIAS','ice_ttt_solution_deadline'),
('OCORRENCIAS','ice_ttt_solution_at'),
('OCORRENCIAS','ice_ttt_solution_type'),
('OCORRENCIAS','ice_ttt_soe_description'),
('OCORRENCIAS','ice_ttt_solution_comments');
INSERT ctl.production_export_template VALUES('COLETAS',6908,'request_date',31);
INSERT ctl.production_export_field VALUES
('COLETAS','id'),
('COLETAS','pck_crn_psn_nickname'),
('COLETAS','sequence_code'),
('COLETAS','created_at'),
('COLETAS','request_date'),
('COLETAS','service_date'),
('COLETAS','finish_date'),
('COLETAS','pck_cor_nickname'),
('COLETAS','invoices_volumes'),
('COLETAS','invoices_weight'),
('COLETAS','taxed_weight'),
('COLETAS','invoices_value'),
('COLETAS','status'),
('COLETAS','pck_pds_cty_name'),
('COLETAS','pck_pds_cty_sae_code'),
('COLETAS','pck_pds_postal_code'),
('COLETAS','pck_pds_neighborhood'),
('COLETAS','pck_prn_name'),
('COLETAS','pck_prn_drt_nickname'),
('COLETAS','updated_at'),
('COLETAS','pck_loe_ore_description'),
('COLETAS','pck_pus_ore_description'),
('COLETAS','pck_pus_ore_trigger'),
('COLETAS','pck_uer_name'),
('COLETAS','pck_ctr_name'),
('COLETAS','cancellation_reason'),
('COLETAS','pck_mik_attempt_number'),
('COLETAS','pck_mik_mft_crn_psn_nickname'),
('COLETAS','pck_mik_mft_sequence_code'),
('COLETAS','pck_mik_mft_vie_license_plate'),
('COLETAS','pck_mik_mft_vie_vee_name');
INSERT ctl.production_export_template VALUES('FRETES',6389,'service_at',114);
INSERT ctl.production_export_field VALUES
('FRETES','id'),
('FRETES','sequence_code'),
('FRETES','fit_crn_psn_nickname'),
('FRETES','fit_sdr_nickname'),
('FRETES','fit_sdr_document'),
('FRETES','fit_rpt_nickname'),
('FRETES','fit_rpt_document'),
('FRETES','fit_spr_nickname'),
('FRETES','fit_spr_document'),
('FRETES','fit_rir_nickname'),
('FRETES','fit_rir_document'),
('FRETES','fit_pyr_document'),
('FRETES','fit_pyr_name'),
('FRETES','fit_pyr_nickname'),
('FRETES','fit_pyr_mds_cty_name'),
('FRETES','fit_pyr_mds_cty_sae_code'),
('FRETES','fit_oas_postal_code'),
('FRETES','fit_oas_cty_name'),
('FRETES','fit_ony_sae_code'),
('FRETES','fit_o_n_name'),
('FRETES','fit_o_n_drt_nickname'),
('FRETES','fit_dos_postal_code'),
('FRETES','fit_dos_cty_name'),
('FRETES','fit_diy_sae_code'),
('FRETES','fit_dyn_name'),
('FRETES','fit_dyn_drt_nickname'),
('FRETES','corporation_sequence_number'),
('FRETES','reference_number'),
('FRETES','emission_type'),
('FRETES','service_type'),
('FRETES','type'),
('FRETES','fit_o_t_corporation_sequence_number'),
('FRETES','courtesy'),
('FRETES','payment_accountable_type'),
('FRETES','status'),
('FRETES','service_at'),
('FRETES','fit_fhe_cte_status'),
('FRETES','fit_fhe_cte_number'),
('FRETES','fit_fhe_cte_series'),
('FRETES','fit_fhe_cte_issued_at'),
('FRETES','fit_fhe_cte_cancellation_at'),
('FRETES','fit_fhe_cte_cancellation_reason'),
('FRETES','fit_fhe_cte_key'),
('FRETES','fit_nse_status'),
('FRETES','fit_nse_number'),
('FRETES','nfse_series'),
('FRETES','nfse_number'),
('FRETES','fit_nse_issued_at'),
('FRETES','fit_nse_nee_code'),
('FRETES','fit_nse_cancelation_reason'),
('FRETES','fit_dpn_delivery_prediction_at'),
('FRETES','fit_dpn_performance_finished_at'),
('FRETES','fit_dpn_manual_schedule'),
('FRETES','invoices_mapping'),
('FRETES','invoices_value'),
('FRETES','invoices_volumes'),
('FRETES','real_weight'),
('FRETES','cubages_cubed_weight'),
('FRETES','total_cubic_volume'),
('FRETES','taxed_weight'),
('FRETES','km'),
('FRETES','freight_weight_subtotal'),
('FRETES','collect_subtotal'),
('FRETES','delivery_subtotal'),
('FRETES','sec_cat_subtotal'),
('FRETES','redispatch_subtotal'),
('FRETES','itr_subtotal'),
('FRETES','dispatch_subtotal'),
('FRETES','gris_subtotal'),
('FRETES','ad_valorem_subtotal'),
('FRETES','toll_subtotal'),
('FRETES','other_fees'),
('FRETES','suframa_subtotal'),
('FRETES','emergency_subtotal'),
('FRETES','tde_subtotal'),
('FRETES','fit_ony_cin_tda_value'),
('FRETES','fit_diy_cin_tda_value'),
('FRETES','additionals_subtotal'),
('FRETES','fit_fis_see_name'),
('FRETES','fit_fis_subtotal'),
('FRETES','adm_fee_subtotal'),
('FRETES','trt_subtotal'),
('FRETES','total'),
('FRETES','calculation_type'),
('FRETES','fit_cre_name'),
('FRETES','fit_sps_slr_psn_name'),
('FRETES','fit_sps_slr_psn_nickname'),
('FRETES','insurance_accountable_type'),
('FRETES','fit_psn_name'),
('FRETES','fit_fsn_name'),
('FRETES','fit_uer_name'),
('FRETES','fit_qrd_qoe_sequence_code'),
('FRETES','fit_p_m_pck_sequence_code'),
('FRETES','fit_p_m_pck_finish_date'),
('FRETES','fit_p_m_pck_mik_mft_sequence_code'),
('FRETES','fit_fte_f_r_mft_sequence_code'),
('FRETES','fit_fte_f_r_occurrence_at'),
('FRETES','fit_mer_mft_sequence_code'),
('FRETES','fit_mer_mft_service_date'),
('FRETES','fit_fte_l_y_mft_sequence_code'),
('FRETES','fit_fte_l_y_mft_service_date'),
('FRETES','fit_ftt_tax_total'),
('FRETES','fit_ftt_agent_costs'),
('FRETES','fit_ftt_commission_costs'),
('FRETES','fit_ftt_redispatch_costs'),
('FRETES','fit_ftt_manifest_costs'),
('FRETES','fit_ftt_consolidation_costs'),
('FRETES','fit_ftt_insurance_costs'),
('FRETES','fit_ftt_operational_expenses'),
('FRETES','fit_ftt_total_cost'),
('FRETES','fit_d_t_created_at'),
('FRETES','comments'),
('FRETES','updated_at'),
('FRETES','fit_ftt_manifest_pick_costs');
INSERT ctl.production_export_template VALUES('MANIFESTOS',6399,NULL,91);
INSERT ctl.production_export_field VALUES
('MANIFESTOS','mft_crn_psn_nickname'),
('MANIFESTOS','created_at'),
('MANIFESTOS','sequence_code'),
('MANIFESTOS','mft_man_name'),
('MANIFESTOS','mft_s_n_svs_sge_pyr_nickname'),
('MANIFESTOS','mft_s_n_svs_sge_sse_name'),
('MANIFESTOS','mft_s_n_sequence_code'),
('MANIFESTOS','mft_s_n_starting_at'),
('MANIFESTOS','mobile_read_at'),
('MANIFESTOS','departured_at'),
('MANIFESTOS','mft_s_n_ending_at'),
('MANIFESTOS','closed_at'),
('MANIFESTOS','finished_at'),
('MANIFESTOS','km'),
('MANIFESTOS','vehicle_departure_km'),
('MANIFESTOS','closing_km'),
('MANIFESTOS','traveled_km'),
('MANIFESTOS','manual_km'),
('MANIFESTOS','status'),
('MANIFESTOS','mft_cat_cot_number'),
('MANIFESTOS','mft_cat_cot_status'),
('MANIFESTOS','generate_mdfe'),
('MANIFESTOS','mdfe_status'),
('MANIFESTOS','mft_mfs_number'),
('MANIFESTOS','mft_mfs_key'),
('MANIFESTOS','monitoring_request'),
('MANIFESTOS','mft_iks_id'),
('MANIFESTOS','invoices_volumes'),
('MANIFESTOS','invoices_count'),
('MANIFESTOS','invoices_value'),
('MANIFESTOS','invoices_weight'),
('MANIFESTOS','total_cubic_volume'),
('MANIFESTOS','total_taxed_weight'),
('MANIFESTOS','manifest_freights_total'),
('MANIFESTOS','mft_pfs_pck_sequence_code'),
('MANIFESTOS','delivery_manifest_items_count'),
('MANIFESTOS','transfer_manifest_items_count'),
('MANIFESTOS','pick_manifest_items_count'),
('MANIFESTOS','dispatch_draft_manifest_items_count'),
('MANIFESTOS','consolidation_manifest_items_count'),
('MANIFESTOS','reverse_pick_manifest_items_count'),
('MANIFESTOS','manifest_items_count'),
('MANIFESTOS','finalized_manifest_items_count'),
('MANIFESTOS','uniq_destinations_count'),
('MANIFESTOS','contract_type'),
('MANIFESTOS','mft_mdr_contract_type'),
('MANIFESTOS','calculation_type'),
('MANIFESTOS','cargo_type'),
('MANIFESTOS','calculated_pick_count'),
('MANIFESTOS','calculated_delivery_count'),
('MANIFESTOS','calculated_dispatch_count'),
('MANIFESTOS','calculated_consolidation_count'),
('MANIFESTOS','calculated_reverse_pick_count'),
('MANIFESTOS','freight_subtotal'),
('MANIFESTOS','fuel_subtotal'),
('MANIFESTOS','toll_subtotal'),
('MANIFESTOS','daily_subtotal'),
('MANIFESTOS','pick_subtotal'),
('MANIFESTOS','delivery_subtotal'),
('MANIFESTOS','consolidation_subtotal'),
('MANIFESTOS','dispatch_subtotal'),
('MANIFESTOS','reverse_pick_subtotal'),
('MANIFESTOS','advance_subtotal'),
('MANIFESTOS','fleet_costs_subtotal'),
('MANIFESTOS','additionals_subtotal'),
('MANIFESTOS','discounts_subtotal'),
('MANIFESTOS','discount_value'),
('MANIFESTOS','operational_expenses_total'),
('MANIFESTOS','total_cost'),
('MANIFESTOS','paying_total'),
('MANIFESTOS','driver_services_total'),
('MANIFESTOS','mft_a_t_inss_value'),
('MANIFESTOS','mft_a_t_sest_senat_value'),
('MANIFESTOS','mft_a_t_ir_value'),
('MANIFESTOS','mft_mte_unloading_recipient_names'),
('MANIFESTOS','mft_mte_delivery_region_names'),
('MANIFESTOS','mft_vie_onr_name'),
('MANIFESTOS','mft_mdr_iil_name'),
('MANIFESTOS','mft_vie_license_plate'),
('MANIFESTOS','mft_vie_vee_name'),
('MANIFESTOS','mft_vie_weight_capacity'),
('MANIFESTOS','mft_vie_cubic_weight'),
('MANIFESTOS','mft_tl1_license_plate'),
('MANIFESTOS','mft_tl1_weight_capacity'),
('MANIFESTOS','mft_tl2_license_plate'),
('MANIFESTOS','mft_tl2_weight_capacity'),
('MANIFESTOS','mft_aoe_rer_name'),
('MANIFESTOS','mft_aoe_comments'),
('MANIFESTOS','mft_ape_name'),
('MANIFESTOS','mft_uer_name'),
('MANIFESTOS','mft_fis_fit_mey_mobile_receipt');
INSERT ctl.production_export_template VALUES('COTACOES',6906,'requested_at',37);
INSERT ctl.production_export_field VALUES
('COTACOES','qoe_crn_psn_name'),
('COTACOES','requested_at'),
('COTACOES','sequence_code'),
('COTACOES','qoe_qes_fon_name'),
('COTACOES','qoe_cor_document'),
('COTACOES','qoe_cor_name'),
('COTACOES','qoe_qes_ony_name'),
('COTACOES','qoe_qes_ony_sae_code'),
('COTACOES','qoe_qes_diy_name'),
('COTACOES','qoe_qes_diy_sae_code'),
('COTACOES','qoe_qes_cre_name'),
('COTACOES','qoe_qes_invoices_volumes'),
('COTACOES','qoe_qes_taxed_weight'),
('COTACOES','qoe_qes_invoices_value'),
('COTACOES','qoe_qes_total'),
('COTACOES','qoe_qes_status'),
('COTACOES','qoe_qes_fit_fhe_cte_issued_at'),
('COTACOES','qoe_qes_fit_nse_issued_at'),
('COTACOES','qoe_uer_name'),
('COTACOES','qoe_crn_psn_nickname'),
('COTACOES','qoe_qes_sdr_document'),
('COTACOES','qoe_qes_sdr_nickname'),
('COTACOES','qoe_qes_rpt_document'),
('COTACOES','qoe_qes_rpt_nickname'),
('COTACOES','qoe_cor_nickname'),
('COTACOES','qoe_qes_origin_postal_code'),
('COTACOES','qoe_qes_destination_postal_code'),
('COTACOES','qoe_qes_real_weight'),
('COTACOES','qoe_qes_disapprove_comments'),
('COTACOES','qoe_qes_freight_comments'),
('COTACOES','qoe_qes_fit_fdt_subtotal'),
('COTACOES','requester_name'),
('COTACOES','qoe_qes_itr_subtotal'),
('COTACOES','qoe_qes_tde_subtotal'),
('COTACOES','qoe_qes_collect_subtotal'),
('COTACOES','qoe_qes_delivery_subtotal'),
('COTACOES','qoe_qes_other_fees');
INSERT ctl.production_export_template VALUES('LOCALIZACAO_CARGAS',8656,'service_at',26);
INSERT ctl.production_export_field VALUES
('LOCALIZACAO_CARGAS','fit_crn_psn_nickname'),
('LOCALIZACAO_CARGAS','corporation_sequence_number'),
('LOCALIZACAO_CARGAS','invoices_mapping'),
('LOCALIZACAO_CARGAS','type'),
('LOCALIZACAO_CARGAS','fit_fsn_name'),
('LOCALIZACAO_CARGAS','service_at'),
('LOCALIZACAO_CARGAS','fit_dpn_delivery_prediction_at'),
('LOCALIZACAO_CARGAS','finished_at'),
('LOCALIZACAO_CARGAS','fit_fln_cln_nickname'),
('LOCALIZACAO_CARGAS','fit_fln_status'),
('LOCALIZACAO_CARGAS','fit_o_n_name'),
('LOCALIZACAO_CARGAS','fit_o_n_drt_nickname'),
('LOCALIZACAO_CARGAS','fit_dyn_name'),
('LOCALIZACAO_CARGAS','fit_dyn_drt_nickname'),
('LOCALIZACAO_CARGAS','fit_pyr_nickname'),
('LOCALIZACAO_CARGAS','fit_rpt_name'),
('LOCALIZACAO_CARGAS','invoices_volumes'),
('LOCALIZACAO_CARGAS','taxed_weight'),
('LOCALIZACAO_CARGAS','invoices_value'),
('LOCALIZACAO_CARGAS','total'),
('LOCALIZACAO_CARGAS','service_type'),
('LOCALIZACAO_CARGAS','fit_fte_lce_ore_description'),
('LOCALIZACAO_CARGAS','fit_fte_lce_occurrence_at'),
('LOCALIZACAO_CARGAS','fit_d_t_created_at'),
('LOCALIZACAO_CARGAS','fit_dos_cty_sae_code'),
('LOCALIZACAO_CARGAS','fit_dos_cty_name');
INSERT ctl.production_export_template VALUES('CONTAS_A_PAGAR',8636,'issue_date',28);
INSERT ctl.production_export_field VALUES
('CONTAS_A_PAGAR','ant_ils_sequence_code'),
('CONTAS_A_PAGAR','document'),
('CONTAS_A_PAGAR','ant_ils_pas_ant_classification'),
('CONTAS_A_PAGAR','ant_ils_pas_ant_name'),
('CONTAS_A_PAGAR','ant_ils_pas_value'),
('CONTAS_A_PAGAR','ant_ces_acr_name'),
('CONTAS_A_PAGAR','ant_ces_value'),
('CONTAS_A_PAGAR','ant_rir_name'),
('CONTAS_A_PAGAR','issue_date'),
('CONTAS_A_PAGAR','competence_month'),
('CONTAS_A_PAGAR','competence_year'),
('CONTAS_A_PAGAR','ant_crn_psn_nickname'),
('CONTAS_A_PAGAR','ant_uer_name'),
('CONTAS_A_PAGAR','created_at'),
('CONTAS_A_PAGAR','ant_ils_atn_liquidation_date'),
('CONTAS_A_PAGAR','ant_ils_atn_transaction_date'),
('CONTAS_A_PAGAR','ant_ils_atn_reconciled'),
('CONTAS_A_PAGAR','comments'),
('CONTAS_A_PAGAR','ant_ils_expense_description'),
('CONTAS_A_PAGAR','ant_ils_comments'),
('CONTAS_A_PAGAR','ant_aln_name'),
('CONTAS_A_PAGAR','type'),
('CONTAS_A_PAGAR','value'),
('CONTAS_A_PAGAR','interest_value'),
('CONTAS_A_PAGAR','discount_value'),
('CONTAS_A_PAGAR','value_to_pay'),
('CONTAS_A_PAGAR','paid'),
('CONTAS_A_PAGAR','paid_value');
INSERT ctl.production_export_template VALUES('FATURAS_POR_CLIENTE',4924,'service_at',52);
INSERT ctl.production_export_field VALUES
('FATURAS_POR_CLIENTE','id'),
('FATURAS_POR_CLIENTE','fit_crn_psn_nickname'),
('FATURAS_POR_CLIENTE','fit_pyr_name'),
('FATURAS_POR_CLIENTE','fit_pyr_document'),
('FATURAS_POR_CLIENTE','fit_pyr_cor_billing_cycle'),
('FATURAS_POR_CLIENTE','fit_pyr_cor_billing_due_in_days'),
('FATURAS_POR_CLIENTE','corporation_sequence_number'),
('FATURAS_POR_CLIENTE','fit_nse_number'),
('FATURAS_POR_CLIENTE','nfse_number'),
('FATURAS_POR_CLIENTE','fit_fhe_cte_number'),
('FATURAS_POR_CLIENTE','fit_fhe_cte_issued_at'),
('FATURAS_POR_CLIENTE','fit_ant_document'),
('FATURAS_POR_CLIENTE','fit_ant_issue_date'),
('FATURAS_POR_CLIENTE','fit_ant_ils_due_date'),
('FATURAS_POR_CLIENTE','fit_ant_ils_original_due_date'),
('FATURAS_POR_CLIENTE','fit_ant_value'),
('FATURAS_POR_CLIENTE','fit_ant_interest_value'),
('FATURAS_POR_CLIENTE','fit_ant_discount_value'),
('FATURAS_POR_CLIENTE','fit_ant_tat_bnk_name'),
('FATURAS_POR_CLIENTE','fit_ant_tat_agency_number'),
('FATURAS_POR_CLIENTE','fit_ant_tat_account_number'),
('FATURAS_POR_CLIENTE','fit_ant_ils_atn_transaction_date'),
('FATURAS_POR_CLIENTE','fit_sdr_document'),
('FATURAS_POR_CLIENTE','fit_sdr_name'),
('FATURAS_POR_CLIENTE','fit_rpt_document'),
('FATURAS_POR_CLIENTE','fit_rpt_name'),
('FATURAS_POR_CLIENTE','fit_fhe_cte_status_result'),
('FATURAS_POR_CLIENTE','fit_fhe_cte_key'),
('FATURAS_POR_CLIENTE','fit_fhe_cte_status'),
('FATURAS_POR_CLIENTE','type'),
('FATURAS_POR_CLIENTE','emission_type'),
('FATURAS_POR_CLIENTE','service_type'),
('FATURAS_POR_CLIENTE','courtesy'),
('FATURAS_POR_CLIENTE','payment_type'),
('FATURAS_POR_CLIENTE','fit_diy_sae_name'),
('FATURAS_POR_CLIENTE','total'),
('FATURAS_POR_CLIENTE','invoices_mapping'),
('FATURAS_POR_CLIENTE','reference_number'),
('FATURAS_POR_CLIENTE','fit_fte_invoices_order_number'),
('FATURAS_POR_CLIENTE','fit_fsn_name'),
('FATURAS_POR_CLIENTE','fit_sps_slr_psn_name'),
('FATURAS_POR_CLIENTE','fit_ant_tat_custom_instruction'),
('FATURAS_POR_CLIENTE','fit_ant_tat_bro_description'),
('FATURAS_POR_CLIENTE','status'),
('FATURAS_POR_CLIENTE','comments'),
('FATURAS_POR_CLIENTE','service_at'),
('FATURAS_POR_CLIENTE','finished_at'),
('FATURAS_POR_CLIENTE','fit_fte_foe_ore_description'),
('FATURAS_POR_CLIENTE','fit_fte_has_delivery_receipt'),
('FATURAS_POR_CLIENTE','fit_d_t_created_at'),
('FATURAS_POR_CLIENTE','fit_dyn_drt_nickname'),
('FATURAS_POR_CLIENTE','fit_ant_ant_name');
INSERT ctl.production_export_template VALUES('INVENTARIO',10633,'started_at',26);
INSERT ctl.production_export_field VALUES
('INVENTARIO','cnr_c_s_fit_corporation_sequence_number'),
('INVENTARIO','cnr_c_s_fit_pyr_nickname'),
('INVENTARIO','cnr_c_s_fit_sdr_nickname'),
('INVENTARIO','cnr_c_s_fit_sdr_ads_cty_name'),
('INVENTARIO','cnr_c_s_fit_rpt_nickname'),
('INVENTARIO','cnr_c_s_fit_rpt_ads_cty_name'),
('INVENTARIO','cnr_c_s_fit_dyn_name'),
('INVENTARIO','cnr_c_s_fit_dyn_drt_nickname'),
('INVENTARIO','sequence_code'),
('INVENTARIO','cnr_crn_psn_nickname'),
('INVENTARIO','type'),
('INVENTARIO','started_at'),
('INVENTARIO','finished_at'),
('INVENTARIO','status'),
('INVENTARIO','cnr_cis_eoe_psn_name'),
('INVENTARIO','cnr_c_s_fit_invoices_mapping'),
('INVENTARIO','cnr_c_s_fit_invoices_value'),
('INVENTARIO','cnr_c_s_fit_real_weight'),
('INVENTARIO','cnr_c_s_fit_total_cubic_volume'),
('INVENTARIO','cnr_c_s_fit_taxed_weight'),
('INVENTARIO','cnr_c_s_fit_invoices_volumes'),
('INVENTARIO','cnr_c_s_read_volumes'),
('INVENTARIO','cnr_c_s_fit_dpn_delivery_prediction_at'),
('INVENTARIO','cnr_c_s_fit_dpn_performance_finished_at'),
('INVENTARIO','cnr_c_s_fit_fte_lce_occurrence_at'),
('INVENTARIO','cnr_c_s_fit_fte_lce_ore_description');
INSERT ctl.production_export_template VALUES('SINISTROS',6392,'opening_at_date',44);
INSERT ctl.production_export_field VALUES
('SINISTROS','sequence_code'),
('SINISTROS','opening_at_date'),
('SINISTROS','occurrence_at_date'),
('SINISTROS','occurrence_at_time'),
('SINISTROS','expected_solution_date'),
('SINISTROS','insurance_claim_location'),
('SINISTROS','informed_by'),
('SINISTROS','rctrc'),
('SINISTROS','rctac'),
('SINISTROS','rcfdc'),
('SINISTROS','insurance_claim_commentary'),
('SINISTROS','list_of_claimed_products'),
('SINISTROS','customer_communication_contact_name'),
('SINISTROS','customer_communication_date'),
('SINISTROS','customer_communication_time'),
('SINISTROS','brokers_case_number'),
('SINISTROS','bo_number'),
('SINISTROS','policy_number'),
('SINISTROS','finished_at_date'),
('SINISTROS','finished_at_time'),
('SINISTROS','finished_commentary'),
('SINISTROS','internal_description'),
('SINISTROS','invoices_count'),
('SINISTROS','icm_fis_fit_corporation_sequence_number'),
('SINISTROS','icm_fis_ioe_number'),
('SINISTROS','invoices_volumes'),
('SINISTROS','invoices_weight'),
('SINISTROS','invoices_value'),
('SINISTROS','icm_fis_fit_pyr_nickname'),
('SINISTROS','customer_debits_subtotal'),
('SINISTROS','customer_credit_entries_subtotal'),
('SINISTROS','responsible_credits_subtotal'),
('SINISTROS','responsible_debit_entries_subtotal'),
('SINISTROS','insurer_credits_subtotal'),
('SINISTROS','insurance_claim_total'),
('SINISTROS','icm_crn_psn_nickname'),
('SINISTROS','icm_dvr_iil_name'),
('SINISTROS','icm_fer_name'),
('SINISTROS','icm_vie_license_plate'),
('SINISTROS','icm_ttt_ore_description'),
('SINISTROS','icm_ttt_ore_code'),
('SINISTROS','icm_ttt_treatment_at'),
('SINISTROS','icm_ttt_dealing_type'),
('SINISTROS','icm_ttt_solution_type');
GO
-- Replace only the original single-entity CHECK with the finite catalog FK.
DECLARE @check sysname;
SELECT @check=cc.name FROM sys.check_constraints cc
WHERE cc.parent_object_id=OBJECT_ID(N'ctl.production_ingestion_run')
AND cc.parent_column_id=COLUMNPROPERTY(OBJECT_ID(N'ctl.production_ingestion_run'),'entity_name','ColumnId')
AND REPLACE(REPLACE(REPLACE(cc.definition,'(',''),')',''),' ','')=N'[entity_name]=''FATURAS_POR_CLIENTE''';
IF @check IS NULL THROW 51923,'RECEIVING_ENTITY_CHECK_DRIFT',1;
DECLARE @ddl nvarchar(max)=N'ALTER TABLE ctl.production_ingestion_run DROP CONSTRAINT '+QUOTENAME(@check);
EXEC sys.sp_executesql @ddl;
ALTER TABLE ctl.production_ingestion_run WITH CHECK ADD CONSTRAINT FK_production_run_template
 FOREIGN KEY(entity_name) REFERENCES ctl.production_export_template(entity_name);
ALTER TABLE ctl.production_ingestion_coverage WITH CHECK ADD CONSTRAINT FK_production_coverage_template
 FOREIGN KEY(entity_name) REFERENCES ctl.production_export_template(entity_name);
GO
ALTER PROCEDURE ctl.usp_production_ingestion_start
 @run_id uniqueidentifier,@source_scope char(64),@business_date date,@request_sha256 char(64),
 @entity_name varchar(40)='FATURAS_POR_CLIENTE'
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @run_id IS NULL OR @business_date IS NULL OR @source_scope IS NULL OR @request_sha256 IS NULL
 OR LEN(@source_scope)<>64 OR LEN(@request_sha256)<>64
 OR @source_scope COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 OR @request_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51902,'EXPLICIT_SCOPE_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name COLLATE Latin1_General_100_BIN2=@entity_name COLLATE Latin1_General_100_BIN2)
 THROW 51924,'CLOSED_RECEIVING_ENTITY_REQUIRED',1;
 INSERT ctl.production_ingestion_run(run_id,source_scope,entity_name,period_start,period_end,run_state,request_sha256)
 VALUES(@run_id,@source_scope,@entity_name,@business_date,@business_date,'RUNNING',@request_sha256);
END;
GO
ALTER PROCEDURE stg.usp_production_ingestion_store
 @run_id uniqueidentifier,@rows nvarchar(max),@expected_count int,@capture_sha256 char(64)
AS
BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 IF @@TRANCOUNT<>0 THROW 51901,'OUTER_TRANSACTION_REFUSED',1;
 IF @expected_count IS NULL OR @expected_count<0 OR @expected_count>100000
 OR @rows IS NULL OR DATALENGTH(@rows)>134217728 OR ISJSON(@rows)<>1
 OR LEFT(LTRIM(@rows),1)<>'[' OR @capture_sha256 IS NULL OR LEN(@capture_sha256)<>64
 OR @capture_sha256 COLLATE Latin1_General_100_BIN2 LIKE '%[^0-9a-f]%'
 THROW 51903,'COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
 BEGIN TRY
  BEGIN TRANSACTION;
  DECLARE @day date,@scope char(64),@state varchar(16),@entity varchar(40),@report int,@date_field varchar(128),@fields int;
  SELECT @day=r.period_start,@scope=r.source_scope,@state=r.run_state,@entity=r.entity_name,
   @report=t.report_id,@date_field=t.row_date_field,@fields=t.expected_field_count
  FROM ctl.production_ingestion_run r WITH(UPDLOCK,HOLDLOCK)
  JOIN ctl.production_export_template t ON t.entity_name=r.entity_name WHERE r.run_id=@run_id;
  IF @state IS NULL OR @state<>'RUNNING' THROW 51904,'RUNNING_RUN_REQUIRED',1;
  IF (SELECT COUNT(*) FROM OPENJSON(@rows))<>@expected_count THROW 51905,'SOURCE_COUNT_MISMATCH',1;
  -- Validate envelope before invoking JSON_VALUE/OPENJSON on nested strings.
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) WHERE [type]<>1 OR ISJSON([value])<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
   WHERE ISNULL(JSON_VALUE(r.[value],'$.entity'),N'') COLLATE Latin1_General_100_BIN2<>@entity COLLATE Latin1_General_100_BIN2
   OR ISNULL(TRY_CONVERT(int,JSON_VALUE(r.[value],'$.templateId')),-1)<>@report
   OR NOT EXISTS(SELECT 1 FROM OPENJSON(r.[value]) e WHERE e.[key]='record' AND e.[type]=5)
   OR (SELECT COUNT(*) FROM OPENJSON(r.[value]) e WHERE e.[key]='record')<>1)
   THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  -- Exact field set and multiplicity, including explicit nulls. No payload column is discarded.
  IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
   WHERE (SELECT COUNT(*) FROM OPENJSON(r.[value],'$.record'))<>@fields
   OR (SELECT COUNT(DISTINCT j.[key] COLLATE Latin1_General_100_BIN2) FROM OPENJSON(r.[value],'$.record') j)<>@fields
   OR EXISTS(SELECT 1 FROM OPENJSON(r.[value],'$.record') j
    WHERE NOT EXISTS(SELECT 1 FROM ctl.production_export_field f WHERE f.entity_name=@entity AND f.field_name=j.[key] COLLATE Latin1_General_100_BIN2)))
   THROW 51925,'SOURCE_FIELD_CONTRACT_MISMATCH',1;
  IF @date_field IS NOT NULL
  BEGIN
   DECLARE @date_path nvarchar(256)=N'$.record.'+@date_field;
   -- Fractional seconds are removed for the civil-date check only, avoiding datetimeoffset
   -- rounding at midnight. Original fractions remain untouched in source_envelope.
   IF EXISTS(SELECT 1 FROM OPENJSON(@rows) r
    CROSS APPLY(SELECT JSON_VALUE(r.[value],@date_path) AS value) d
    CROSS APPLY(SELECT CASE
     WHEN RIGHT(d.value,1)='Z' THEN LEFT(d.value,19)+'+00:00'
     WHEN d.value COLLATE Latin1_General_100_BIN2 LIKE '%[+-][0-9][0-9]:[0-9][0-9]' THEN LEFT(d.value,19)+RIGHT(d.value,6)
     ELSE NULL END AS offset_value) o
    CROSS APPLY(SELECT CASE WHEN o.offset_value IS NOT NULL
     THEN CONVERT(date,TRY_CONVERT(datetimeoffset(0),o.offset_value) AT TIME ZONE 'E. South America Standard Time')
     ELSE TRY_CONVERT(date,LEFT(d.value,10)) END AS civil_date) c
    WHERE c.civil_date IS NULL OR c.civil_date<>@day)
    THROW 51906,'SOURCE_DATE_OR_ENVELOPE_MISMATCH',1;
  END;
  INSERT stg.production_export_occurrence(run_id,occurrence_ordinal,business_date,source_envelope,envelope_sha256)
  SELECT @run_id,CONVERT(int,[key])+1,@day,[value],HASHBYTES('SHA2_256',CONVERT(varbinary(max),[value]))
  FROM OPENJSON(@rows);
  UPDATE ctl.production_ingestion_run SET run_state='STORED',completed_utc=SYSUTCDATETIME(),
   row_count=@expected_count,capture_sha256=@capture_sha256 WHERE run_id=@run_id;
  UPDATE ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
   SET latest_run_id=@run_id,stored_utc=SYSUTCDATETIME()
   WHERE source_scope=@scope AND entity_name=@entity AND business_date=@day;
  IF @@ROWCOUNT=0 INSERT ctl.production_ingestion_coverage VALUES(@scope,@entity,@day,@run_id,SYSUTCDATETIME());
  COMMIT;
 END TRY BEGIN CATCH
  IF XACT_STATE()<>0 ROLLBACK;
  THROW;
 END CATCH;
END;
GO
ALTER PROCEDURE ctl.usp_production_ingestion_plan
 @source_scope char(64),@anchor date,@until date,@entity_name varchar(40)='FATURAS_POR_CLIENTE'
AS
BEGIN
 SET NOCOUNT ON;
 IF @anchor IS NULL OR @until IS NULL OR @until<@anchor OR DATEDIFF(day,@anchor,@until)>3660
 THROW 51908,'BOUNDED_RECOVERY_RANGE_REQUIRED',1;
 IF NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name COLLATE Latin1_General_100_BIN2=@entity_name COLLATE Latin1_General_100_BIN2)
 THROW 51924,'CLOSED_RECEIVING_ENTITY_REQUIRED',1;
 WITH days AS(SELECT @anchor AS business_date UNION ALL SELECT DATEADD(day,1,business_date) FROM days WHERE business_date<@until)
 SELECT d.business_date,CONVERT(bit,CASE WHEN c.latest_run_id IS NULL THEN 0 ELSE 1 END) AS stored
 FROM days d LEFT JOIN ctl.production_ingestion_coverage c ON c.source_scope=@source_scope
 AND c.entity_name=@entity_name AND c.business_date=d.business_date
 ORDER BY d.business_date OPTION(MAXRECURSION 3661);
END;
GO
CREATE VIEW stg.production_export_latest AS
 SELECT c.source_scope,c.entity_name,t.report_id,c.business_date,c.latest_run_id AS run_id,
  o.occurrence_ordinal,o.source_envelope,o.envelope_sha256,c.stored_utc
 FROM ctl.production_ingestion_coverage c
 JOIN ctl.production_export_template t ON t.entity_name=c.entity_name
 JOIN stg.production_export_occurrence o ON o.run_id=c.latest_run_id;
GO

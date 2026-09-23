package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarRules;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioRules;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroRules;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/** Two real JDBC batches per bounded page; no per-record database operation or key lookup. */
public final class JdbcExpansionStaging {
    private final DataSource dataSource;
    private final UUID run;

    public JdbcExpansionStaging(final DataSource dataSource, final UUID run) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource);
        this.run = java.util.Objects.requireNonNull(run);
    }

    private static final String HEADER =
            "INSERT INTO stg.expansion_lab_observation (execution_id,run_id,vertical,"
                    + "occurrence,root_type,root_key,part_type,part_key,component_type,component_key,"
                    + "revision,binding_evidence,currency,unit,additive_allocation,active,reactivation,"
                    + "fresh_second,fresh_nano,fresh_inclusive,valid,proof_attached,root_amount,"
                    + "part_amount,allocation_amount,business_date,comparison_bytes) VALUES (?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String CAP_FIELDS =
            "INSERT INTO stg.expansion_lab_cap ([execution_id],[occurrence],[ant_aln_name_p],"
                    + "[ant_aln_name_w],[ant_aln_name_raw],[ant_aln_name],[ant_ces_acr_name_p],"
                    + "[ant_ces_acr_name_w],[ant_ces_acr_name_raw],[ant_ces_acr_name],[ant_ces_value_p],"
                    + "[ant_ces_value_w],[ant_ces_value_raw],[ant_ces_value],[ant_crn_psn_nickname_p],"
                    + "[ant_crn_psn_nickname_w],[ant_crn_psn_nickname_raw],[ant_crn_psn_nickname],"
                    + "[ant_ils_atn_liquidation_date_p],[ant_ils_atn_liquidation_date_w],"
                    + "[ant_ils_atn_liquidation_date_raw],[ant_ils_atn_liquidation_date],"
                    + "[ant_ils_atn_reconciled_p],[ant_ils_atn_reconciled_w],"
                    + "[ant_ils_atn_reconciled_raw],[ant_ils_atn_reconciled],"
                    + "[ant_ils_atn_transaction_date_p],[ant_ils_atn_transaction_date_w],"
                    + "[ant_ils_atn_transaction_date_raw],[ant_ils_atn_transaction_date],"
                    + "[ant_ils_comments_p],[ant_ils_comments_w],[ant_ils_comments_raw],"
                    + "[ant_ils_comments],[ant_ils_expense_description_p],"
                    + "[ant_ils_expense_description_w],[ant_ils_expense_description_raw],"
                    + "[ant_ils_expense_description],[ant_ils_pas_ant_classification_p],"
                    + "[ant_ils_pas_ant_classification_w],[ant_ils_pas_ant_classification_raw],"
                    + "[ant_ils_pas_ant_classification],[ant_ils_pas_ant_name_p],"
                    + "[ant_ils_pas_ant_name_w],[ant_ils_pas_ant_name_raw],[ant_ils_pas_ant_name],"
                    + "[ant_ils_pas_value_p],[ant_ils_pas_value_w],[ant_ils_pas_value_raw],"
                    + "[ant_ils_pas_value],[ant_ils_sequence_code_p],[ant_ils_sequence_code_w],"
                    + "[ant_ils_sequence_code_raw],[ant_ils_sequence_code],[ant_rir_name_p],"
                    + "[ant_rir_name_w],[ant_rir_name_raw],[ant_rir_name],[ant_uer_name_p],"
                    + "[ant_uer_name_w],[ant_uer_name_raw],[ant_uer_name],[comments_p],[comments_w],"
                    + "[comments_raw],[comments],[competence_month_p],[competence_month_w],"
                    + "[competence_month_raw],[competence_month],[competence_year_p],"
                    + "[competence_year_w],[competence_year_raw],[competence_year],[created_at_p],"
                    + "[created_at_w],[created_at_raw],[created_at],[created_at_nano],"
                    + "[discount_value_p],[discount_value_w],[discount_value_raw],[discount_value],"
                    + "[document_p],[document_w],[document_raw],[document],[interest_value_p],"
                    + "[interest_value_w],[interest_value_raw],[interest_value],[issue_date_p],"
                    + "[issue_date_w],[issue_date_raw],[issue_date],[paid_p],[paid_w],[paid_raw],[paid],"
                    + "[paid_value_p],[paid_value_w],[paid_value_raw],[paid_value],[type_p],[type_w],"
                    + "[type_raw],[type],[value_p],[value_w],[value_raw],[value],[value_to_pay_p],"
                    + "[value_to_pay_w],[value_to_pay_raw],[value_to_pay]) VALUES (?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String FAT_FIELDS =
            "INSERT INTO stg.expansion_lab_fat ([execution_id],[occurrence],[comments_p],"
                    + "[comments_w],[comments_raw],[comments],[corporation_sequence_number_p],"
                    + "[corporation_sequence_number_w],[corporation_sequence_number_raw],"
                    + "[corporation_sequence_number],[courtesy_p],[courtesy_w],[courtesy_raw],"
                    + "[courtesy],[emission_type_p],[emission_type_w],[emission_type_raw],"
                    + "[emission_type],[finished_at_p],[finished_at_w],[finished_at_raw],[finished_at],"
                    + "[finished_at_nano],[fit_ant_ant_name_p],[fit_ant_ant_name_w],"
                    + "[fit_ant_ant_name_raw],[fit_ant_ant_name],[fit_ant_discount_value_p],"
                    + "[fit_ant_discount_value_w],[fit_ant_discount_value_raw],[fit_ant_discount_value],"
                    + "[fit_ant_document_p],[fit_ant_document_w],[fit_ant_document_raw],"
                    + "[fit_ant_document],[fit_ant_ils_atn_transaction_date_p],"
                    + "[fit_ant_ils_atn_transaction_date_w],[fit_ant_ils_atn_transaction_date_raw],"
                    + "[fit_ant_ils_atn_transaction_date],[fit_ant_ils_due_date_p],"
                    + "[fit_ant_ils_due_date_w],[fit_ant_ils_due_date_raw],[fit_ant_ils_due_date],"
                    + "[fit_ant_ils_original_due_date_p],[fit_ant_ils_original_due_date_w],"
                    + "[fit_ant_ils_original_due_date_raw],[fit_ant_ils_original_due_date],"
                    + "[fit_ant_interest_value_p],[fit_ant_interest_value_w],"
                    + "[fit_ant_interest_value_raw],[fit_ant_interest_value],[fit_ant_issue_date_p],"
                    + "[fit_ant_issue_date_w],[fit_ant_issue_date_raw],[fit_ant_issue_date],"
                    + "[fit_ant_tat_account_number_p],[fit_ant_tat_account_number_w],"
                    + "[fit_ant_tat_account_number_raw],[fit_ant_tat_account_number],"
                    + "[fit_ant_tat_agency_number_p],[fit_ant_tat_agency_number_w],"
                    + "[fit_ant_tat_agency_number_raw],[fit_ant_tat_agency_number],"
                    + "[fit_ant_tat_bnk_name_p],[fit_ant_tat_bnk_name_w],[fit_ant_tat_bnk_name_raw],"
                    + "[fit_ant_tat_bnk_name],[fit_ant_tat_bro_description_p],"
                    + "[fit_ant_tat_bro_description_w],[fit_ant_tat_bro_description_raw],"
                    + "[fit_ant_tat_bro_description],[fit_ant_tat_custom_instruction_p],"
                    + "[fit_ant_tat_custom_instruction_w],[fit_ant_tat_custom_instruction_raw],"
                    + "[fit_ant_tat_custom_instruction],[fit_ant_value_p],[fit_ant_value_w],"
                    + "[fit_ant_value_raw],[fit_ant_value],[fit_crn_psn_nickname_p],"
                    + "[fit_crn_psn_nickname_w],[fit_crn_psn_nickname_raw],[fit_crn_psn_nickname],"
                    + "[fit_d_t_created_at_p],[fit_d_t_created_at_w],[fit_d_t_created_at_raw],"
                    + "[fit_d_t_created_at],[fit_d_t_created_at_nano],[fit_diy_sae_name_p],"
                    + "[fit_diy_sae_name_w],[fit_diy_sae_name_raw],[fit_diy_sae_name],"
                    + "[fit_dyn_drt_nickname_p],[fit_dyn_drt_nickname_w],[fit_dyn_drt_nickname_raw],"
                    + "[fit_dyn_drt_nickname],[fit_fhe_cte_issued_at_p],[fit_fhe_cte_issued_at_w],"
                    + "[fit_fhe_cte_issued_at_raw],[fit_fhe_cte_issued_at],[fit_fhe_cte_issued_at_nano],"
                    + "[fit_fhe_cte_key_p],[fit_fhe_cte_key_w],[fit_fhe_cte_key_raw],[fit_fhe_cte_key],"
                    + "[fit_fhe_cte_number_p],[fit_fhe_cte_number_w],[fit_fhe_cte_number_raw],"
                    + "[fit_fhe_cte_number],[fit_fhe_cte_status_p],[fit_fhe_cte_status_w],"
                    + "[fit_fhe_cte_status_raw],[fit_fhe_cte_status],[fit_fhe_cte_status_result_p],"
                    + "[fit_fhe_cte_status_result_w],[fit_fhe_cte_status_result_raw],"
                    + "[fit_fhe_cte_status_result],[fit_fsn_name_p],[fit_fsn_name_w],[fit_fsn_name_raw],"
                    + "[fit_fsn_name],[fit_fte_foe_ore_description_p],[fit_fte_foe_ore_description_w],"
                    + "[fit_fte_foe_ore_description_raw],[fit_fte_foe_ore_description],"
                    + "[fit_fte_has_delivery_receipt_p],[fit_fte_has_delivery_receipt_w],"
                    + "[fit_fte_has_delivery_receipt_raw],[fit_fte_has_delivery_receipt],"
                    + "[fit_fte_invoices_order_number_p],[fit_fte_invoices_order_number_w],"
                    + "[fit_fte_invoices_order_number_raw],[fit_fte_invoices_order_number],"
                    + "[fit_nse_number_p],[fit_nse_number_w],[fit_nse_number_raw],[fit_nse_number],"
                    + "[fit_pyr_cor_billing_cycle_p],[fit_pyr_cor_billing_cycle_w],"
                    + "[fit_pyr_cor_billing_cycle_raw],[fit_pyr_cor_billing_cycle],"
                    + "[fit_pyr_cor_billing_due_in_days_p],[fit_pyr_cor_billing_due_in_days_w],"
                    + "[fit_pyr_cor_billing_due_in_days_raw],[fit_pyr_cor_billing_due_in_days],"
                    + "[fit_pyr_document_p],[fit_pyr_document_w],[fit_pyr_document_raw],"
                    + "[fit_pyr_document],[fit_pyr_name_p],[fit_pyr_name_w],[fit_pyr_name_raw],"
                    + "[fit_pyr_name],[fit_rpt_document_p],[fit_rpt_document_w],[fit_rpt_document_raw],"
                    + "[fit_rpt_document],[fit_rpt_name_p],[fit_rpt_name_w],[fit_rpt_name_raw],"
                    + "[fit_rpt_name],[fit_sdr_document_p],[fit_sdr_document_w],[fit_sdr_document_raw],"
                    + "[fit_sdr_document],[fit_sdr_name_p],[fit_sdr_name_w],[fit_sdr_name_raw],"
                    + "[fit_sdr_name],[fit_sps_slr_psn_name_p],[fit_sps_slr_psn_name_w],"
                    + "[fit_sps_slr_psn_name_raw],[fit_sps_slr_psn_name],[id_p],[id_w],[id_raw],[id],"
                    + "[invoices_mapping_p],[invoices_mapping_w],[invoices_mapping_raw],"
                    + "[invoices_mapping],[nfse_number_p],[nfse_number_w],[nfse_number_raw],"
                    + "[nfse_number],[payment_type_p],[payment_type_w],[payment_type_raw],"
                    + "[payment_type],[reference_number_p],[reference_number_w],[reference_number_raw],"
                    + "[reference_number],[service_at_p],[service_at_w],[service_at_raw],[service_at],"
                    + "[service_at_nano],[service_type_p],[service_type_w],[service_type_raw],"
                    + "[service_type],[status_p],[status_w],[status_raw],[status],"
                    + "[third_party_ctes_value_p],[third_party_ctes_value_w],"
                    + "[third_party_ctes_value_raw],[third_party_ctes_value],[total_p],[total_w],"
                    + "[total_raw],[total],[type_p],[type_w],[type_raw],[type]) VALUES (?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?)";
    private static final String INV_FIELDS =
            "INSERT INTO stg.expansion_lab_inv ([execution_id],[occurrence],"
                    + "[cnr_c_s_fit_corporation_sequence_number_p],"
                    + "[cnr_c_s_fit_corporation_sequence_number_w],"
                    + "[cnr_c_s_fit_corporation_sequence_number_raw],"
                    + "[cnr_c_s_fit_corporation_sequence_number],"
                    + "[cnr_c_s_fit_dpn_delivery_prediction_at_p],"
                    + "[cnr_c_s_fit_dpn_delivery_prediction_at_w],"
                    + "[cnr_c_s_fit_dpn_delivery_prediction_at_raw],"
                    + "[cnr_c_s_fit_dpn_delivery_prediction_at],"
                    + "[cnr_c_s_fit_dpn_delivery_prediction_at_nano],"
                    + "[cnr_c_s_fit_dpn_performance_finished_at_p],"
                    + "[cnr_c_s_fit_dpn_performance_finished_at_w],"
                    + "[cnr_c_s_fit_dpn_performance_finished_at_raw],"
                    + "[cnr_c_s_fit_dpn_performance_finished_at],"
                    + "[cnr_c_s_fit_dpn_performance_finished_at_nano],[cnr_c_s_fit_dyn_drt_nickname_p],"
                    + "[cnr_c_s_fit_dyn_drt_nickname_w],[cnr_c_s_fit_dyn_drt_nickname_raw],"
                    + "[cnr_c_s_fit_dyn_drt_nickname],[cnr_c_s_fit_dyn_name_p],[cnr_c_s_fit_dyn_name_w],"
                    + "[cnr_c_s_fit_dyn_name_raw],[cnr_c_s_fit_dyn_name],"
                    + "[cnr_c_s_fit_fte_lce_occurrence_at_p],[cnr_c_s_fit_fte_lce_occurrence_at_w],"
                    + "[cnr_c_s_fit_fte_lce_occurrence_at_raw],[cnr_c_s_fit_fte_lce_occurrence_at],"
                    + "[cnr_c_s_fit_fte_lce_occurrence_at_nano],[cnr_c_s_fit_fte_lce_ore_description_p],"
                    + "[cnr_c_s_fit_fte_lce_ore_description_w],"
                    + "[cnr_c_s_fit_fte_lce_ore_description_raw],[cnr_c_s_fit_fte_lce_ore_description],"
                    + "[cnr_c_s_fit_invoices_mapping_p],[cnr_c_s_fit_invoices_mapping_w],"
                    + "[cnr_c_s_fit_invoices_mapping_raw],[cnr_c_s_fit_invoices_mapping],"
                    + "[cnr_c_s_fit_invoices_value_p],[cnr_c_s_fit_invoices_value_w],"
                    + "[cnr_c_s_fit_invoices_value_raw],[cnr_c_s_fit_invoices_value],"
                    + "[cnr_c_s_fit_invoices_volumes_p],[cnr_c_s_fit_invoices_volumes_w],"
                    + "[cnr_c_s_fit_invoices_volumes_raw],[cnr_c_s_fit_invoices_volumes],"
                    + "[cnr_c_s_fit_pyr_nickname_p],[cnr_c_s_fit_pyr_nickname_w],"
                    + "[cnr_c_s_fit_pyr_nickname_raw],[cnr_c_s_fit_pyr_nickname],"
                    + "[cnr_c_s_fit_real_weight_p],[cnr_c_s_fit_real_weight_w],"
                    + "[cnr_c_s_fit_real_weight_raw],[cnr_c_s_fit_real_weight],"
                    + "[cnr_c_s_fit_rpt_ads_cty_name_p],[cnr_c_s_fit_rpt_ads_cty_name_w],"
                    + "[cnr_c_s_fit_rpt_ads_cty_name_raw],[cnr_c_s_fit_rpt_ads_cty_name],"
                    + "[cnr_c_s_fit_rpt_nickname_p],[cnr_c_s_fit_rpt_nickname_w],"
                    + "[cnr_c_s_fit_rpt_nickname_raw],[cnr_c_s_fit_rpt_nickname],"
                    + "[cnr_c_s_fit_sdr_ads_cty_name_p],[cnr_c_s_fit_sdr_ads_cty_name_w],"
                    + "[cnr_c_s_fit_sdr_ads_cty_name_raw],[cnr_c_s_fit_sdr_ads_cty_name],"
                    + "[cnr_c_s_fit_sdr_nickname_p],[cnr_c_s_fit_sdr_nickname_w],"
                    + "[cnr_c_s_fit_sdr_nickname_raw],[cnr_c_s_fit_sdr_nickname],"
                    + "[cnr_c_s_fit_taxed_weight_p],[cnr_c_s_fit_taxed_weight_w],"
                    + "[cnr_c_s_fit_taxed_weight_raw],[cnr_c_s_fit_taxed_weight],"
                    + "[cnr_c_s_fit_total_cubic_volume_p],[cnr_c_s_fit_total_cubic_volume_w],"
                    + "[cnr_c_s_fit_total_cubic_volume_raw],[cnr_c_s_fit_total_cubic_volume],"
                    + "[cnr_c_s_read_volumes_p],[cnr_c_s_read_volumes_w],[cnr_c_s_read_volumes_raw],"
                    + "[cnr_c_s_read_volumes],[cnr_cis_eoe_psn_name_p],[cnr_cis_eoe_psn_name_w],"
                    + "[cnr_cis_eoe_psn_name_raw],[cnr_cis_eoe_psn_name],[cnr_crn_psn_nickname_p],"
                    + "[cnr_crn_psn_nickname_w],[cnr_crn_psn_nickname_raw],[cnr_crn_psn_nickname],"
                    + "[finished_at_p],[finished_at_w],[finished_at_raw],[finished_at],"
                    + "[finished_at_nano],[sequence_code_p],[sequence_code_w],[sequence_code_raw],"
                    + "[sequence_code],[started_at_p],[started_at_w],[started_at_raw],[started_at],"
                    + "[started_at_nano],[status_p],[status_w],[status_raw],[status],[type_p],[type_w],"
                    + "[type_raw],[type]) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?)";
    private static final String SIN_FIELDS =
            "INSERT INTO stg.expansion_lab_sin ([execution_id],[occurrence],[bo_number_p],"
                    + "[bo_number_w],[bo_number_raw],[bo_number],[brokers_case_number_p],"
                    + "[brokers_case_number_w],[brokers_case_number_raw],[brokers_case_number],"
                    + "[customer_communication_contact_name_p],[customer_communication_contact_name_w],"
                    + "[customer_communication_contact_name_raw],[customer_communication_contact_name],"
                    + "[customer_communication_date_p],[customer_communication_date_w],"
                    + "[customer_communication_date_raw],[customer_communication_date],"
                    + "[customer_communication_time_p],[customer_communication_time_w],"
                    + "[customer_communication_time_raw],[customer_communication_time],"
                    + "[customer_credit_entries_subtotal_p],[customer_credit_entries_subtotal_w],"
                    + "[customer_credit_entries_subtotal_raw],[customer_credit_entries_subtotal],"
                    + "[customer_debits_subtotal_p],[customer_debits_subtotal_w],"
                    + "[customer_debits_subtotal_raw],[customer_debits_subtotal],"
                    + "[expected_solution_date_p],[expected_solution_date_w],"
                    + "[expected_solution_date_raw],[expected_solution_date],[finished_at_date_p],"
                    + "[finished_at_date_w],[finished_at_date_raw],[finished_at_date],"
                    + "[finished_at_time_p],[finished_at_time_w],[finished_at_time_raw],"
                    + "[finished_at_time],[finished_commentary_p],[finished_commentary_w],"
                    + "[finished_commentary_raw],[finished_commentary],[icm_crn_psn_nickname_p],"
                    + "[icm_crn_psn_nickname_w],[icm_crn_psn_nickname_raw],[icm_crn_psn_nickname],"
                    + "[icm_dvr_iil_name_p],[icm_dvr_iil_name_w],[icm_dvr_iil_name_raw],"
                    + "[icm_dvr_iil_name],[icm_fer_name_p],[icm_fer_name_w],[icm_fer_name_raw],"
                    + "[icm_fer_name],[icm_fis_fit_corporation_sequence_number_p],"
                    + "[icm_fis_fit_corporation_sequence_number_w],"
                    + "[icm_fis_fit_corporation_sequence_number_raw],"
                    + "[icm_fis_fit_corporation_sequence_number],[icm_fis_fit_pyr_nickname_p],"
                    + "[icm_fis_fit_pyr_nickname_w],[icm_fis_fit_pyr_nickname_raw],"
                    + "[icm_fis_fit_pyr_nickname],[icm_fis_ioe_number_p],[icm_fis_ioe_number_w],"
                    + "[icm_fis_ioe_number_raw],[icm_fis_ioe_number],[icm_ttt_dealing_type_p],"
                    + "[icm_ttt_dealing_type_w],[icm_ttt_dealing_type_raw],[icm_ttt_dealing_type],"
                    + "[icm_ttt_ore_code_p],[icm_ttt_ore_code_w],[icm_ttt_ore_code_raw],"
                    + "[icm_ttt_ore_code],[icm_ttt_ore_description_p],[icm_ttt_ore_description_w],"
                    + "[icm_ttt_ore_description_raw],[icm_ttt_ore_description],"
                    + "[icm_ttt_solution_type_p],[icm_ttt_solution_type_w],[icm_ttt_solution_type_raw],"
                    + "[icm_ttt_solution_type],[icm_ttt_treatment_at_p],[icm_ttt_treatment_at_w],"
                    + "[icm_ttt_treatment_at_raw],[icm_ttt_treatment_at],[icm_ttt_treatment_at_nano],"
                    + "[icm_vie_license_plate_p],[icm_vie_license_plate_w],[icm_vie_license_plate_raw],"
                    + "[icm_vie_license_plate],[informed_by_p],[informed_by_w],[informed_by_raw],"
                    + "[informed_by],[insurance_claim_commentary_p],[insurance_claim_commentary_w],"
                    + "[insurance_claim_commentary_raw],[insurance_claim_commentary],"
                    + "[insurance_claim_location_p],[insurance_claim_location_w],"
                    + "[insurance_claim_location_raw],[insurance_claim_location],"
                    + "[insurance_claim_total_p],[insurance_claim_total_w],[insurance_claim_total_raw],"
                    + "[insurance_claim_total],[insurer_credits_subtotal_p],"
                    + "[insurer_credits_subtotal_w],[insurer_credits_subtotal_raw],"
                    + "[insurer_credits_subtotal],[internal_description_p],[internal_description_w],"
                    + "[internal_description_raw],[internal_description],[invoices_count_p],"
                    + "[invoices_count_w],[invoices_count_raw],[invoices_count],[invoices_value_p],"
                    + "[invoices_value_w],[invoices_value_raw],[invoices_value],[invoices_volumes_p],"
                    + "[invoices_volumes_w],[invoices_volumes_raw],[invoices_volumes],"
                    + "[invoices_weight_p],[invoices_weight_w],[invoices_weight_raw],[invoices_weight],"
                    + "[list_of_claimed_products_p],[list_of_claimed_products_w],"
                    + "[list_of_claimed_products_raw],[list_of_claimed_products],[occurrence_at_date_p],"
                    + "[occurrence_at_date_w],[occurrence_at_date_raw],[occurrence_at_date],"
                    + "[occurrence_at_time_p],[occurrence_at_time_w],[occurrence_at_time_raw],"
                    + "[occurrence_at_time],[opening_at_date_p],[opening_at_date_w],"
                    + "[opening_at_date_raw],[opening_at_date],[policy_number_p],[policy_number_w],"
                    + "[policy_number_raw],[policy_number],[rcfdc_p],[rcfdc_w],[rcfdc_raw],[rcfdc],"
                    + "[rctac_p],[rctac_w],[rctac_raw],[rctac],[rctrc_p],[rctrc_w],[rctrc_raw],[rctrc],"
                    + "[responsible_credits_subtotal_p],[responsible_credits_subtotal_w],"
                    + "[responsible_credits_subtotal_raw],[responsible_credits_subtotal],"
                    + "[responsible_debit_entries_subtotal_p],[responsible_debit_entries_subtotal_w],"
                    + "[responsible_debit_entries_subtotal_raw],[responsible_debit_entries_subtotal],"
                    + "[sequence_code_p],[sequence_code_w],[sequence_code_raw],"
                    + "[sequence_code]) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                    + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public void stage(
            final UUID execution,
            final int batchNumber,
            final List<? extends ExpansionCaptured<?>> batch,
            final Instant observedAt,
            final CancellationToken cancellation) {
        if (batchNumber < 1 || batch.isEmpty() || batch.size() > 100 || observedAt == null) {
            throw new IllegalArgumentException("EXP_BATCH_BOUND");
        }
        final String vertical = batch.get(0).observation().vertical();
        final String sql =
                switch (vertical) {
                    case "CAP" -> CAP_FIELDS;
                    case "FAT" -> FAT_FIELDS;
                    case "INV" -> INV_FIELDS;
                    case "SIN" -> SIN_FIELDS;
                    default -> throw new IllegalArgumentException("EXP_VERTICAL");
                };
        try (var connection = dataSource.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var header = connection.prepareStatement(HEADER);
                    var detail = connection.prepareStatement(sql);
                    var arrays =
                            connection.prepareStatement(
                                    "INSERT INTO stg.expansion_lab_array_item(execution_id,occurrence,field_name,"
                                            + "physical_position,text_value) VALUES(?,?,?,?,?)")) {
                header.setQueryTimeout(20);
                detail.setQueryTimeout(20);
                arrays.setQueryTimeout(20);
                for (final var captured : batch) {
                    cancellation.throwIfCancellationRequested();
                    if (!vertical.equals(captured.observation().vertical())) {
                        throw new IllegalArgumentException("EXP_BATCH_VERTICAL");
                    }
                    final var bytes = new StringBuilder();
                    detail.setString(1, execution.toString());
                    detail.setLong(2, captured.occurrence());
                    bindDetails(detail, arrays, execution, captured, bytes);
                    bindHeader(
                            header,
                            execution,
                            captured,
                            bytes.toString().getBytes(StandardCharsets.UTF_8));
                    header.addBatch();
                    detail.addBatch();
                }
                cancellation.throwIfCancellationRequested();
                header.executeBatch();
                detail.executeBatch();
                arrays.executeBatch();
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (final SQLException failure) {
            throw new IllegalStateException("EXP_STAGE_SQL", failure);
        }
    }

    private void bindHeader(
            final PreparedStatement statement,
            final UUID execution,
            final ExpansionCaptured<?> captured,
            final byte[] bytes)
            throws SQLException {
        final var row = captured.observation();
        final var binding = captured.binding();
        statement.setString(1, execution.toString());
        statement.setString(2, run.toString());
        statement.setString(3, row.vertical());
        statement.setLong(4, captured.occurrence());
        if (binding == null) {
            for (int i = 5; i <= 17; i++) {
                statement.setObject(i, null);
            }
        } else {
            statement.setString(5, binding.root().kind().name());
            statement.setNString(6, binding.root().value());
            statement.setString(7, binding.part().kind().name());
            statement.setNString(8, binding.part().value());
            statement.setString(9, binding.component().kind().name());
            statement.setNString(10, binding.component().value());
            statement.setInt(11, binding.revision());
            statement.setString(12, binding.evidence());
            statement.setString(13, binding.currency());
            statement.setString(14, binding.unit());
            statement.setBoolean(15, binding.additiveAllocation());
            statement.setBoolean(16, binding.active());
            statement.setBoolean(17, binding.reactivation());
        }
        final ExpansionFreshness fresh;
        final boolean proof;
        final java.math.BigDecimal rootAmount, partAmount, allocation;
        final java.time.LocalDate date;
        if (row instanceof ContaPagarObservation r) {
            fresh = ContaPagarRules.freshness(r);
            proof = false;
            rootAmount = r.value().value();
            partAmount = r.valueToPay().value();
            allocation = r.antCesValue().value();
            date = r.issueDate().value();
        } else if (row instanceof FaturaClienteObservation r) {
            fresh = FaturaClienteRules.freshness(r);
            proof = false;
            rootAmount = r.fitAntValue().value();
            partAmount = null;
            allocation = null;
            date = r.fitAntIssueDate().value();
        } else if (row instanceof InventarioObservation r) {
            fresh = InventarioRules.freshness(r);
            proof = InventarioRules.proofAttached(r.cnrCSFitFteLceOreDescription().value());
            rootAmount = r.cnrCSFitInvoicesValue().value();
            partAmount = null;
            allocation = null;
            date =
                    r.startedAt().value() == null
                            ? null
                            : r.startedAt().value().atZone(ExpansionFreshness.ZONE).toLocalDate();
        } else if (row instanceof SinistroObservation r) {
            fresh = SinistroRules.freshness(r);
            proof = false;
            rootAmount = r.insuranceClaimTotal().value();
            partAmount = null;
            allocation = null;
            date = r.openingAtDate().value();
        } else {
            throw new IllegalArgumentException("EXP_OBSERVATION_TYPE");
        }
        statement.setObject(18, fresh == null ? null : fresh.second());
        statement.setObject(19, fresh == null ? null : fresh.nano());
        statement.setObject(20, fresh == null ? null : (fresh.exclusive() ? 0 : 1));
        statement.setBoolean(21, row.valid());
        statement.setBoolean(22, proof);
        statement.setBigDecimal(23, rootAmount);
        statement.setBigDecimal(24, partAmount);
        statement.setBigDecimal(25, allocation);
        statement.setObject(26, date);
        statement.setBytes(27, bytes);
    }

    private static void bindDetails(
            final PreparedStatement statement,
            final PreparedStatement arrays,
            final UUID execution,
            final ExpansionCaptured<?> captured,
            final StringBuilder bytes)
            throws SQLException {
        final var row = captured.observation();
        int index = 3;
        if (row instanceof ContaPagarObservation value) {
            index = bind(statement, index, value.antAlnName(), "TEXT", bytes);
            index = bind(statement, index, value.antCesAcrName(), "TEXT", bytes);
            index = bind(statement, index, value.antCesValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.antCrnPsnNickname(), "TEXT", bytes);
            index = bind(statement, index, value.antIlsAtnLiquidationDate(), "DATE", bytes);
            index = bind(statement, index, value.antIlsAtnReconciled(), "BOOLEAN", bytes);
            index = bind(statement, index, value.antIlsAtnTransactionDate(), "DATE", bytes);
            index = bind(statement, index, value.antIlsComments(), "TEXT", bytes);
            index = bind(statement, index, value.antIlsExpenseDescription(), "TEXT", bytes);
            index = bind(statement, index, value.antIlsPasAntClassification(), "TEXT", bytes);
            index = bind(statement, index, value.antIlsPasAntName(), "TEXT", bytes);
            index = bind(statement, index, value.antIlsPasValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.antIlsSequenceCode(), "INTEGER", bytes);
            index = bind(statement, index, value.antRirName(), "TEXT", bytes);
            index = bind(statement, index, value.antUerName(), "TEXT", bytes);
            index = bind(statement, index, value.comments(), "TEXT", bytes);
            index = bind(statement, index, value.competenceMonth(), "INTEGER", bytes);
            index = bind(statement, index, value.competenceYear(), "INTEGER", bytes);
            index = bind(statement, index, value.createdAt(), "INSTANT", bytes);
            index = bind(statement, index, value.discountValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.document(), "TEXT", bytes);
            index = bind(statement, index, value.interestValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.issueDate(), "DATE", bytes);
            index = bind(statement, index, value.paid(), "BOOLEAN", bytes);
            index = bind(statement, index, value.paidValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.type(), "TEXT", bytes);
            index = bind(statement, index, value.value(), "DECIMAL", bytes);
            index = bind(statement, index, value.valueToPay(), "DECIMAL", bytes);
        } else if (row instanceof FaturaClienteObservation value) {
            index = bind(statement, index, value.comments(), "TEXT", bytes);
            index = bind(statement, index, value.corporationSequenceNumber(), "INTEGER", bytes);
            index = bind(statement, index, value.courtesy(), "BOOLEAN", bytes);
            index = bind(statement, index, value.emissionType(), "TEXT", bytes);
            index = bind(statement, index, value.finishedAt(), "INSTANT", bytes);
            index = bind(statement, index, value.fitAntAntName(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntDiscountValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.fitAntDocument(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntIlsAtnTransactionDate(), "DATE", bytes);
            index = bind(statement, index, value.fitAntIlsDueDate(), "DATE", bytes);
            index = bind(statement, index, value.fitAntIlsOriginalDueDate(), "DATE", bytes);
            index = bind(statement, index, value.fitAntInterestValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.fitAntIssueDate(), "DATE", bytes);
            index = bind(statement, index, value.fitAntTatAccountNumber(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntTatAgencyNumber(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntTatBnkName(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntTatBroDescription(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntTatCustomInstruction(), "TEXT", bytes);
            index = bind(statement, index, value.fitAntValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.fitCrnPsnNickname(), "TEXT", bytes);
            index = bind(statement, index, value.fitDTCreatedAt(), "INSTANT", bytes);
            index = bind(statement, index, value.fitDiySaeName(), "TEXT", bytes);
            index = bind(statement, index, value.fitDynDrtNickname(), "TEXT", bytes);
            index = bind(statement, index, value.fitFheCteIssuedAt(), "INSTANT", bytes);
            index = bind(statement, index, value.fitFheCteKey(), "TEXT", bytes);
            index = bind(statement, index, value.fitFheCteNumber(), "INTEGER", bytes);
            index = bind(statement, index, value.fitFheCteStatus(), "TEXT", bytes);
            index = bind(statement, index, value.fitFheCteStatusResult(), "TEXT", bytes);
            index = bind(statement, index, value.fitFsnName(), "TEXT", bytes);
            index = bind(statement, index, value.fitFteFoeOreDescription(), "TEXT", bytes);
            index = bind(statement, index, value.fitFteHasDeliveryReceipt(), "BOOLEAN", bytes);
            index = bind(statement, index, value.fitFteInvoicesOrderNumber(), "STRINGS", bytes);
            array(
                    arrays,
                    execution,
                    captured.occurrence(),
                    "fit_fte_invoices_order_number",
                    value.fitFteInvoicesOrderNumber());
            index = bind(statement, index, value.fitNseNumber(), "INTEGER", bytes);
            index = bind(statement, index, value.fitPyrCorBillingCycle(), "TEXT", bytes);
            index = bind(statement, index, value.fitPyrCorBillingDueInDays(), "INTEGER", bytes);
            index = bind(statement, index, value.fitPyrDocument(), "TEXT", bytes);
            index = bind(statement, index, value.fitPyrName(), "TEXT", bytes);
            index = bind(statement, index, value.fitRptDocument(), "TEXT", bytes);
            index = bind(statement, index, value.fitRptName(), "TEXT", bytes);
            index = bind(statement, index, value.fitSdrDocument(), "TEXT", bytes);
            index = bind(statement, index, value.fitSdrName(), "TEXT", bytes);
            index = bind(statement, index, value.fitSpsSlrPsnName(), "TEXT", bytes);
            index = bind(statement, index, value.id(), "INTEGER", bytes);
            index = bind(statement, index, value.invoicesMapping(), "STRINGS", bytes);
            array(
                    arrays,
                    execution,
                    captured.occurrence(),
                    "invoices_mapping",
                    value.invoicesMapping());
            index = bind(statement, index, value.nfseNumber(), "IDENTIFIER", bytes);
            index = bind(statement, index, value.paymentType(), "TEXT", bytes);
            index = bind(statement, index, value.referenceNumber(), "TEXT", bytes);
            index = bind(statement, index, value.serviceAt(), "INSTANT", bytes);
            index = bind(statement, index, value.serviceType(), "TEXT", bytes);
            index = bind(statement, index, value.status(), "TEXT", bytes);
            index = bind(statement, index, value.thirdPartyCtesValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.total(), "DECIMAL", bytes);
            index = bind(statement, index, value.type(), "TEXT", bytes);
        } else if (row instanceof InventarioObservation value) {
            index =
                    bind(
                            statement,
                            index,
                            value.cnrCSFitCorporationSequenceNumber(),
                            "INTEGER",
                            bytes);
            index =
                    bind(
                            statement,
                            index,
                            value.cnrCSFitDpnDeliveryPredictionAt(),
                            "INSTANT",
                            bytes);
            index =
                    bind(
                            statement,
                            index,
                            value.cnrCSFitDpnPerformanceFinishedAt(),
                            "INSTANT",
                            bytes);
            index = bind(statement, index, value.cnrCSFitDynDrtNickname(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitDynName(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitFteLceOccurrenceAt(), "INSTANT", bytes);
            index = bind(statement, index, value.cnrCSFitFteLceOreDescription(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitInvoicesMapping(), "STRINGS", bytes);
            array(
                    arrays,
                    execution,
                    captured.occurrence(),
                    "cnr_c_s_fit_invoices_mapping",
                    value.cnrCSFitInvoicesMapping());
            index = bind(statement, index, value.cnrCSFitInvoicesValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.cnrCSFitInvoicesVolumes(), "INTEGER", bytes);
            index = bind(statement, index, value.cnrCSFitPyrNickname(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitRealWeight(), "DECIMAL", bytes);
            index = bind(statement, index, value.cnrCSFitRptAdsCtyName(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitRptNickname(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitSdrAdsCtyName(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitSdrNickname(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCSFitTaxedWeight(), "DECIMAL", bytes);
            index = bind(statement, index, value.cnrCSFitTotalCubicVolume(), "DECIMAL", bytes);
            index = bind(statement, index, value.cnrCSReadVolumes(), "INTEGER", bytes);
            index = bind(statement, index, value.cnrCisEoePsnName(), "TEXT", bytes);
            index = bind(statement, index, value.cnrCrnPsnNickname(), "TEXT", bytes);
            index = bind(statement, index, value.finishedAt(), "INSTANT", bytes);
            index = bind(statement, index, value.sequenceCode(), "INTEGER", bytes);
            index = bind(statement, index, value.startedAt(), "INSTANT", bytes);
            index = bind(statement, index, value.status(), "TEXT", bytes);
            index = bind(statement, index, value.type(), "TEXT", bytes);
        } else if (row instanceof SinistroObservation value) {
            index = bind(statement, index, value.boNumber(), "TEXT", bytes);
            index = bind(statement, index, value.brokersCaseNumber(), "TEXT", bytes);
            index = bind(statement, index, value.customerCommunicationContactName(), "TEXT", bytes);
            index = bind(statement, index, value.customerCommunicationDate(), "DATE", bytes);
            index = bind(statement, index, value.customerCommunicationTime(), "TEXT", bytes);
            index = bind(statement, index, value.customerCreditEntriesSubtotal(), "DECIMAL", bytes);
            index = bind(statement, index, value.customerDebitsSubtotal(), "DECIMAL", bytes);
            index = bind(statement, index, value.expectedSolutionDate(), "DATE", bytes);
            index = bind(statement, index, value.finishedAtDate(), "DATE", bytes);
            index = bind(statement, index, value.finishedAtTime(), "TIME", bytes);
            index = bind(statement, index, value.finishedCommentary(), "TEXT", bytes);
            index = bind(statement, index, value.icmCrnPsnNickname(), "TEXT", bytes);
            index = bind(statement, index, value.icmDvrIilName(), "TEXT", bytes);
            index = bind(statement, index, value.icmFerName(), "TEXT", bytes);
            index =
                    bind(
                            statement,
                            index,
                            value.icmFisFitCorporationSequenceNumber(),
                            "INTEGER",
                            bytes);
            index = bind(statement, index, value.icmFisFitPyrNickname(), "TEXT", bytes);
            index = bind(statement, index, value.icmFisIoeNumber(), "IDENTIFIER", bytes);
            index = bind(statement, index, value.icmTttDealingType(), "TEXT", bytes);
            index = bind(statement, index, value.icmTttOreCode(), "INTEGER", bytes);
            index = bind(statement, index, value.icmTttOreDescription(), "TEXT", bytes);
            index = bind(statement, index, value.icmTttSolutionType(), "TEXT", bytes);
            index = bind(statement, index, value.icmTttTreatmentAt(), "INSTANT", bytes);
            index = bind(statement, index, value.icmVieLicensePlate(), "TEXT", bytes);
            index = bind(statement, index, value.informedBy(), "TEXT", bytes);
            index = bind(statement, index, value.insuranceClaimCommentary(), "TEXT", bytes);
            index = bind(statement, index, value.insuranceClaimLocation(), "TEXT", bytes);
            index = bind(statement, index, value.insuranceClaimTotal(), "DECIMAL", bytes);
            index = bind(statement, index, value.insurerCreditsSubtotal(), "DECIMAL", bytes);
            index = bind(statement, index, value.internalDescription(), "TEXT", bytes);
            index = bind(statement, index, value.invoicesCount(), "INTEGER", bytes);
            index = bind(statement, index, value.invoicesValue(), "DECIMAL", bytes);
            index = bind(statement, index, value.invoicesVolumes(), "INTEGER", bytes);
            index = bind(statement, index, value.invoicesWeight(), "DECIMAL", bytes);
            index = bind(statement, index, value.listOfClaimedProducts(), "TEXT", bytes);
            index = bind(statement, index, value.occurrenceAtDate(), "DATE", bytes);
            index = bind(statement, index, value.occurrenceAtTime(), "TIME", bytes);
            index = bind(statement, index, value.openingAtDate(), "DATE", bytes);
            index = bind(statement, index, value.policyNumber(), "TEXT", bytes);
            index = bind(statement, index, value.rcfdc(), "STRINGS", bytes);
            array(arrays, execution, captured.occurrence(), "rcfdc", value.rcfdc());
            index = bind(statement, index, value.rctac(), "STRINGS", bytes);
            array(arrays, execution, captured.occurrence(), "rctac", value.rctac());
            index = bind(statement, index, value.rctrc(), "STRINGS", bytes);
            array(arrays, execution, captured.occurrence(), "rctrc", value.rctrc());
            index = bind(statement, index, value.responsibleCreditsSubtotal(), "DECIMAL", bytes);
            index =
                    bind(
                            statement,
                            index,
                            value.responsibleDebitEntriesSubtotal(),
                            "DECIMAL",
                            bytes);
            index = bind(statement, index, value.sequenceCode(), "INTEGER", bytes);
        } else {
            throw new IllegalArgumentException("EXP_OBSERVATION_TYPE");
        }
    }

    private static int bind(
            final PreparedStatement statement,
            int index,
            final ExpansionValue<?> field,
            final String kind,
            final StringBuilder bytes)
            throws SQLException {
        statement.setString(index++, field.presence().name());
        statement.setString(index++, field.wire().name());
        statement.setNString(index++, field.raw());
        bytes.append(field.presence()).append(':').append(field.wire()).append(':');
        if (field.raw() == null) {
            bytes.append("-1:");
        } else {
            bytes.append(field.raw().length()).append(':').append(field.raw());
        }
        final Object value = field.value();
        if (kind.equals("INSTANT")) {
            final Instant instant = (Instant) value;
            statement.setObject(index++, instant == null ? null : instant.getEpochSecond());
            statement.setObject(index++, instant == null ? null : instant.getNano());
        } else if (kind.equals("TIME")) {
            statement.setObject(
                    index++, value == null ? null : ((java.time.LocalTime) value).toNanoOfDay());
        } else if (kind.equals("STRINGS")) {
            statement.setObject(
                    index++, value == null ? null : ((ExpansionStrings) value).items().size());
        } else {
            statement.setObject(index++, value);
        }
        return index;
    }

    private static void array(
            final PreparedStatement statement,
            final UUID execution,
            final long occurrence,
            final String field,
            final ExpansionValue<ExpansionStrings> value)
            throws SQLException {
        if (value.value() == null) {
            return;
        }
        int position = 0;
        for (final String text : value.value().items()) {
            statement.setString(1, execution.toString());
            statement.setLong(2, occurrence);
            statement.setString(3, field);
            statement.setInt(4, position++);
            statement.setNString(5, text);
            statement.addBatch();
        }
    }
}

package br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;

public final class FaturaClienteDataExportMapper {
    public FaturaClienteObservation map(final int occurrence, final JsonNode envelope) {
        if (occurrence < 1 || occurrence > 100) {
            throw new IllegalArgumentException("EXP_OCCURRENCE_BOUND");
        }
        final var dto = new FaturaClienteDataExportDto(envelope.get("data"));
        return new FaturaClienteObservation(
                ExpansionFieldParser.text(dto.data(), "comments"),
                ExpansionFieldParser.integer(dto.data(), "corporation_sequence_number"),
                ExpansionFieldParser.bool(dto.data(), "courtesy"),
                ExpansionFieldParser.text(dto.data(), "emission_type"),
                ExpansionFieldParser.instant(dto.data(), "finished_at"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_ant_name"),
                ExpansionFieldParser.decimal(dto.data(), "fit_ant_discount_value"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_document"),
                ExpansionFieldParser.date(dto.data(), "fit_ant_ils_atn_transaction_date"),
                ExpansionFieldParser.date(dto.data(), "fit_ant_ils_due_date"),
                ExpansionFieldParser.date(dto.data(), "fit_ant_ils_original_due_date"),
                ExpansionFieldParser.decimal(dto.data(), "fit_ant_interest_value"),
                ExpansionFieldParser.date(dto.data(), "fit_ant_issue_date"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_tat_account_number"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_tat_agency_number"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_tat_bnk_name"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_tat_bro_description"),
                ExpansionFieldParser.text(dto.data(), "fit_ant_tat_custom_instruction"),
                ExpansionFieldParser.decimal(dto.data(), "fit_ant_value"),
                ExpansionFieldParser.text(dto.data(), "fit_crn_psn_nickname"),
                ExpansionFieldParser.instant(dto.data(), "fit_d_t_created_at"),
                ExpansionFieldParser.text(dto.data(), "fit_diy_sae_name"),
                ExpansionFieldParser.text(dto.data(), "fit_dyn_drt_nickname"),
                ExpansionFieldParser.instant(dto.data(), "fit_fhe_cte_issued_at"),
                ExpansionFieldParser.text(dto.data(), "fit_fhe_cte_key"),
                ExpansionFieldParser.integer(dto.data(), "fit_fhe_cte_number"),
                ExpansionFieldParser.text(dto.data(), "fit_fhe_cte_status"),
                ExpansionFieldParser.text(dto.data(), "fit_fhe_cte_status_result"),
                ExpansionFieldParser.text(dto.data(), "fit_fsn_name"),
                ExpansionFieldParser.text(dto.data(), "fit_fte_foe_ore_description"),
                ExpansionFieldParser.bool(dto.data(), "fit_fte_has_delivery_receipt"),
                ExpansionFieldParser.strings(dto.data(), "fit_fte_invoices_order_number"),
                ExpansionFieldParser.integer(dto.data(), "fit_nse_number"),
                ExpansionFieldParser.text(dto.data(), "fit_pyr_cor_billing_cycle"),
                ExpansionFieldParser.integer(dto.data(), "fit_pyr_cor_billing_due_in_days"),
                ExpansionFieldParser.text(dto.data(), "fit_pyr_document"),
                ExpansionFieldParser.text(dto.data(), "fit_pyr_name"),
                ExpansionFieldParser.text(dto.data(), "fit_rpt_document"),
                ExpansionFieldParser.text(dto.data(), "fit_rpt_name"),
                ExpansionFieldParser.text(dto.data(), "fit_sdr_document"),
                ExpansionFieldParser.text(dto.data(), "fit_sdr_name"),
                ExpansionFieldParser.text(dto.data(), "fit_sps_slr_psn_name"),
                ExpansionFieldParser.integer(dto.data(), "id"),
                ExpansionFieldParser.strings(dto.data(), "invoices_mapping"),
                ExpansionFieldParser.identifier(dto.data(), "nfse_number"),
                ExpansionFieldParser.text(dto.data(), "payment_type"),
                ExpansionFieldParser.text(dto.data(), "reference_number"),
                ExpansionFieldParser.instant(dto.data(), "service_at"),
                ExpansionFieldParser.text(dto.data(), "service_type"),
                ExpansionFieldParser.text(dto.data(), "status"),
                ExpansionFieldParser.decimal(dto.data(), "third_party_ctes_value"),
                ExpansionFieldParser.decimal(dto.data(), "total"),
                ExpansionFieldParser.text(dto.data(), "type"));
    }
}

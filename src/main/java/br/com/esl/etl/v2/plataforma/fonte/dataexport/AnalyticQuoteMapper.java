package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticQuoteAttributes;
import com.fasterxml.jackson.databind.JsonNode;

/** Strict supplementary parsing before the existing quotation pipeline can publish. */
public final class AnalyticQuoteMapper {
    public AnalyticQuoteAttributes map(final JsonNode row) {
        if (row == null || !row.isObject()) {
            throw new IllegalArgumentException("ANA_QUOTE_OBJECT_REQUIRED");
        }
        return new AnalyticQuoteAttributes(
                ExpansionFieldParser.instant(row, "requested_at"),
                ExpansionFieldParser.integer(row, "sequence_code"),
                ExpansionFieldParser.text(row, "qoe_qes_fon_name"),
                ExpansionFieldParser.text(row, "qoe_cor_document"),
                ExpansionFieldParser.text(row, "qoe_cor_name"),
                ExpansionFieldParser.text(row, "qoe_qes_ony_name"),
                ExpansionFieldParser.text(row, "qoe_qes_ony_sae_code"),
                ExpansionFieldParser.text(row, "qoe_qes_diy_name"),
                ExpansionFieldParser.text(row, "qoe_qes_diy_sae_code"),
                ExpansionFieldParser.text(row, "qoe_qes_cre_name"),
                ExpansionFieldParser.integer(row, "qoe_qes_invoices_volumes"),
                ExpansionFieldParser.decimal(row, "qoe_qes_taxed_weight"),
                ExpansionFieldParser.decimal(row, "qoe_qes_invoices_value"),
                ExpansionFieldParser.decimal(row, "qoe_qes_total"),
                ExpansionFieldParser.instant(row, "qoe_qes_fit_fhe_cte_issued_at"),
                ExpansionFieldParser.instant(row, "qoe_qes_fit_nse_issued_at"),
                ExpansionFieldParser.text(row, "qoe_uer_name"),
                ExpansionFieldParser.text(row, "qoe_crn_psn_nickname"),
                ExpansionFieldParser.text(row, "qoe_qes_sdr_document"),
                ExpansionFieldParser.text(row, "qoe_qes_sdr_nickname"),
                ExpansionFieldParser.text(row, "qoe_qes_rpt_document"),
                ExpansionFieldParser.text(row, "qoe_qes_rpt_nickname"),
                ExpansionFieldParser.text(row, "qoe_qes_origin_postal_code"),
                ExpansionFieldParser.text(row, "qoe_qes_destination_postal_code"),
                ExpansionFieldParser.decimal(row, "qoe_qes_real_weight"),
                ExpansionFieldParser.text(row, "qoe_qes_disapprove_comments"),
                ExpansionFieldParser.text(row, "qoe_qes_freight_comments"),
                ExpansionFieldParser.decimal(row, "qoe_qes_fit_fdt_subtotal"),
                ExpansionFieldParser.text(row, "requester_name"),
                ExpansionFieldParser.decimal(row, "qoe_qes_itr_subtotal"),
                ExpansionFieldParser.decimal(row, "qoe_qes_tde_subtotal"),
                ExpansionFieldParser.decimal(row, "qoe_qes_collect_subtotal"),
                ExpansionFieldParser.decimal(row, "qoe_qes_delivery_subtotal"),
                ExpansionFieldParser.decimal(row, "qoe_qes_other_fees"),
                ExpansionFieldParser.text(row, "qoe_crn_psn_name"),
                ExpansionFieldParser.text(row, "qoe_cor_nickname"));
    }
}

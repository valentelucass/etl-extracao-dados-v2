package br.com.esl.etl.v2.modulos.consolidacoes.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Source boundary; a missing column differs from an explicit null. */
public record ConsolidacaoDataExportDto(JsonNode data) {
    public static final Set<String> FIELDS =
            Set.of(
                    "ctd_fgs_crn_psn_nickname",
                    "sequence_code",
                    "issue_date",
                    "ctd_rhr_cpy_nickname",
                    "ctd_rhr_cpy_document",
                    "document_number",
                    "ctd_fgs_fhe_cte_number",
                    "charged_value",
                    "subtotal",
                    "ctd_fgs_subtotal",
                    "ctd_fgs_total",
                    "ctd_fgs_taxed_weight",
                    "ctd_fgs_fte_has_delivery_receipt",
                    "bill_status",
                    "ctd_fgs_service_at",
                    "ctd_fgs_corporation_sequence_number",
                    "ctd_fgs_invoices_volumes",
                    "ctd_fgs_invoices_mapping",
                    "ctd_fgs_dos_cty_name",
                    "ctd_sdr_document",
                    "ctd_sdr_nickname",
                    "ctd_fgs_oas_cty_name",
                    "ctd_rpt_document",
                    "ctd_rpt_nickname",
                    "ctd_rpt_charge_tde",
                    "ctd_fgs_dpn_delivery_prediction_at",
                    "ctd_fgs_finished_at",
                    "ctd_fgs_dpn_finished_within_deadline",
                    "ctd_fgs_fte_lce_ore_description",
                    "ctd_fgs_fte_foe_ore_description",
                    "ctd_fgs_fte_foe_occurrence_at",
                    "ctd_fgs_d_t_created_at",
                    "invoices_volumes",
                    "real_weight",
                    "taxed_weight",
                    "invoices_value",
                    "ctd_diy_cin_tda_value",
                    "ctd_ant_document",
                    "charged_value_details");

    public ConsolidacaoDataExportDto {
        MappedReportFields.requireExact(data, FIELDS);
        data = data.deepCopy();
    }

    @Override
    public JsonNode data() {
        return data.deepCopy();
    }

    @Override
    public String toString() {
        return "ConsolidacaoDataExportDto[redacted]";
    }
}

package br.com.esl.etl.v2.modulos.consolidacoes.aplicacao;

import br.com.esl.etl.v2.modulos.consolidacoes.domain.ConsolidacaoObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;

/** Preserves original wire and presence while parsing the observed types. */
public final class ConsolidacaoDataExportMapper {
    public ConsolidacaoObservation map(final JsonNode row) {
        final var data = new ConsolidacaoDataExportDto(row).data();
        return new ConsolidacaoObservation(
                ExpansionFieldParser.text(data, "ctd_fgs_crn_psn_nickname"),
                ExpansionFieldParser.integer(data, "sequence_code"),
                ExpansionFieldParser.date(data, "issue_date"),
                ExpansionFieldParser.text(data, "ctd_rhr_cpy_nickname"),
                ExpansionFieldParser.text(data, "ctd_rhr_cpy_document"),
                ExpansionFieldParser.text(data, "document_number"),
                ExpansionFieldParser.integer(data, "ctd_fgs_fhe_cte_number"),
                ExpansionFieldParser.decimal(data, "charged_value"),
                ExpansionFieldParser.decimal(data, "subtotal"),
                ExpansionFieldParser.decimal(data, "ctd_fgs_subtotal"),
                ExpansionFieldParser.decimal(data, "ctd_fgs_total"),
                ExpansionFieldParser.decimal(data, "ctd_fgs_taxed_weight"),
                ExpansionFieldParser.bool(data, "ctd_fgs_fte_has_delivery_receipt"),
                ExpansionFieldParser.text(data, "bill_status"),
                ExpansionFieldParser.instant(data, "ctd_fgs_service_at"),
                ExpansionFieldParser.integer(data, "ctd_fgs_corporation_sequence_number"),
                ExpansionFieldParser.integer(data, "ctd_fgs_invoices_volumes"),
                ExpansionFieldParser.strings(data, "ctd_fgs_invoices_mapping"),
                ExpansionFieldParser.text(data, "ctd_fgs_dos_cty_name"),
                ExpansionFieldParser.text(data, "ctd_sdr_document"),
                ExpansionFieldParser.text(data, "ctd_sdr_nickname"),
                ExpansionFieldParser.text(data, "ctd_fgs_oas_cty_name"),
                ExpansionFieldParser.text(data, "ctd_rpt_document"),
                ExpansionFieldParser.text(data, "ctd_rpt_nickname"),
                ExpansionFieldParser.bool(data, "ctd_rpt_charge_tde"),
                ExpansionFieldParser.instant(data, "ctd_fgs_dpn_delivery_prediction_at"),
                ExpansionFieldParser.instant(data, "ctd_fgs_finished_at"),
                ExpansionFieldParser.bool(data, "ctd_fgs_dpn_finished_within_deadline"),
                ExpansionFieldParser.text(data, "ctd_fgs_fte_lce_ore_description"),
                ExpansionFieldParser.text(data, "ctd_fgs_fte_foe_ore_description"),
                ExpansionFieldParser.instant(data, "ctd_fgs_fte_foe_occurrence_at"),
                ExpansionFieldParser.instant(data, "ctd_fgs_d_t_created_at"),
                ExpansionFieldParser.integer(data, "invoices_volumes"),
                ExpansionFieldParser.decimal(data, "real_weight"),
                ExpansionFieldParser.decimal(data, "taxed_weight"),
                ExpansionFieldParser.decimal(data, "invoices_value"),
                ExpansionFieldParser.decimal(data, "ctd_diy_cin_tda_value"),
                MappedReportFields.text(data, "ctd_ant_document"),
                ConsolidacaoChargesFieldParser.parse(data));
    }
}

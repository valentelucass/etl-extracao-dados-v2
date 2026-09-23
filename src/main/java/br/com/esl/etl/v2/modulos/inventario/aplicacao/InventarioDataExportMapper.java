package br.com.esl.etl.v2.modulos.inventario.aplicacao;

import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;

public final class InventarioDataExportMapper {
    public InventarioObservation map(final int occurrence, final JsonNode envelope) {
        if (occurrence < 1 || occurrence > 100) {
            throw new IllegalArgumentException("EXP_OCCURRENCE_BOUND");
        }
        final var dto = new InventarioDataExportDto(envelope.get("data"));
        return new InventarioObservation(
                ExpansionFieldParser.integer(dto.data(), "cnr_c_s_fit_corporation_sequence_number"),
                ExpansionFieldParser.instant(dto.data(), "cnr_c_s_fit_dpn_delivery_prediction_at"),
                ExpansionFieldParser.instant(dto.data(), "cnr_c_s_fit_dpn_performance_finished_at"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_dyn_drt_nickname"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_dyn_name"),
                ExpansionFieldParser.instant(dto.data(), "cnr_c_s_fit_fte_lce_occurrence_at"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_fte_lce_ore_description"),
                ExpansionFieldParser.strings(dto.data(), "cnr_c_s_fit_invoices_mapping"),
                ExpansionFieldParser.decimal(dto.data(), "cnr_c_s_fit_invoices_value"),
                ExpansionFieldParser.integer(dto.data(), "cnr_c_s_fit_invoices_volumes"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_pyr_nickname"),
                ExpansionFieldParser.decimal(dto.data(), "cnr_c_s_fit_real_weight"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_rpt_ads_cty_name"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_rpt_nickname"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_sdr_ads_cty_name"),
                ExpansionFieldParser.text(dto.data(), "cnr_c_s_fit_sdr_nickname"),
                ExpansionFieldParser.decimal(dto.data(), "cnr_c_s_fit_taxed_weight"),
                ExpansionFieldParser.decimal(dto.data(), "cnr_c_s_fit_total_cubic_volume"),
                ExpansionFieldParser.integer(dto.data(), "cnr_c_s_read_volumes"),
                ExpansionFieldParser.text(dto.data(), "cnr_cis_eoe_psn_name"),
                ExpansionFieldParser.text(dto.data(), "cnr_crn_psn_nickname"),
                ExpansionFieldParser.instant(dto.data(), "finished_at"),
                ExpansionFieldParser.integer(dto.data(), "sequence_code"),
                ExpansionFieldParser.instant(dto.data(), "started_at"),
                ExpansionFieldParser.text(dto.data(), "status"),
                ExpansionFieldParser.text(dto.data(), "type"));
    }
}

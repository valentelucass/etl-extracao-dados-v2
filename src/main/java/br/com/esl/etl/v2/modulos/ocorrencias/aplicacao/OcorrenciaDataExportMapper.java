package br.com.esl.etl.v2.modulos.ocorrencias.aplicacao;

import br.com.esl.etl.v2.modulos.ocorrencias.domain.OcorrenciaObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;

/** Preserves original wire and presence while parsing the observed types. */
public final class OcorrenciaDataExportMapper {
    public OcorrenciaObservation map(final JsonNode row) {
        final var data = new OcorrenciaDataExportDto(row).data();
        return new OcorrenciaObservation(
                ExpansionFieldParser.integer(data, "ice_f_e_fit_corporation_sequence_number"),
                ExpansionFieldParser.text(data, "ice_f_e_ioe_number"),
                ExpansionFieldParser.integer(data, "ice_fit_fhe_cte_number"),
                ExpansionFieldParser.integer(data, "ice_ore_code"),
                ExpansionFieldParser.text(data, "ice_ore_description"),
                ExpansionFieldParser.instant(data, "occurrence_at"),
                ExpansionFieldParser.bool(data, "on_mobile"),
                ExpansionFieldParser.text(data, "ice_uer_name"),
                ExpansionFieldParser.instant(data, "ice_fit_service_at"),
                ExpansionFieldParser.instant(data, "ice_fit_dpn_delivery_prediction_at"),
                ExpansionFieldParser.instant(data, "occurrence_schedule_at"),
                ExpansionFieldParser.instant(data, "ice_fit_dpn_performance_finished_at"),
                ExpansionFieldParser.text(data, "delivery_status"),
                ExpansionFieldParser.text(data, "ice_fit_fln_cln_nickname"),
                ExpansionFieldParser.bool(data, "ice_fit_dpn_manual_schedule"),
                ExpansionFieldParser.instant(data, "ice_ttt_solution_deadline"),
                ExpansionFieldParser.instant(data, "ice_ttt_solution_at"),
                MappedReportFields.text(data, "ice_ttt_solution_type"),
                MappedReportFields.text(data, "ice_ttt_soe_description"),
                MappedReportFields.text(data, "ice_ttt_solution_comments"));
    }
}

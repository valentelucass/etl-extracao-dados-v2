package br.com.esl.etl.v2.modulos.ocorrencias.aplicacao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Source boundary; a missing column differs from an explicit null. */
public record OcorrenciaDataExportDto(JsonNode data) {
    public static final Set<String> FIELDS =
            Set.of(
                    "ice_f_e_fit_corporation_sequence_number",
                    "ice_f_e_ioe_number",
                    "ice_fit_fhe_cte_number",
                    "ice_ore_code",
                    "ice_ore_description",
                    "occurrence_at",
                    "on_mobile",
                    "ice_uer_name",
                    "ice_fit_service_at",
                    "ice_fit_dpn_delivery_prediction_at",
                    "occurrence_schedule_at",
                    "ice_fit_dpn_performance_finished_at",
                    "delivery_status",
                    "ice_fit_fln_cln_nickname",
                    "ice_fit_dpn_manual_schedule",
                    "ice_ttt_solution_deadline",
                    "ice_ttt_solution_at",
                    "ice_ttt_solution_type",
                    "ice_ttt_soe_description",
                    "ice_ttt_solution_comments");

    public OcorrenciaDataExportDto {
        MappedReportFields.requireExact(data, FIELDS);
        data = data.deepCopy();
    }

    @Override
    public JsonNode data() {
        return data.deepCopy();
    }

    @Override
    public String toString() {
        return "OcorrenciaDataExportDto[redacted]";
    }
}

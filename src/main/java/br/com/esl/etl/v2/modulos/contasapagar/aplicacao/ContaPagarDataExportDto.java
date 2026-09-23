package br.com.esl.etl.v2.modulos.contasapagar.aplicacao;

import com.fasterxml.jackson.databind.JsonNode;

/** Boundary DTO for the data member of the explicit synthetic capture envelope. */
public record ContaPagarDataExportDto(JsonNode data) {
    public ContaPagarDataExportDto {
        if (data == null || !data.isObject()) {
            throw new IllegalArgumentException("EXP_DATA_OBJECT_REQUIRED");
        }
    }
}

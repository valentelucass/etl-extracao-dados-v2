package br.com.esl.etl.v2.modulos.raster.aplicacao;

import com.fasterxml.jackson.databind.JsonNode;

/** JSON is restricted to the source boundary; mapper emits domain values with presence. */
public record RasterTripDto(JsonNode value) {
    public RasterTripDto {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("RAS_TRIP_OBJECT");
        }
    }
}

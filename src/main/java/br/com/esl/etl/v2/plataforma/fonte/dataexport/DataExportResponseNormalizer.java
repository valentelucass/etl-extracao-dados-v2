package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Normaliza os formatos array, envelope {@code data} e objeto unitário do Data Export. */
public final class DataExportResponseNormalizer {

    public DataExportPageResponse normalize(final JsonNode response) {
        Objects.requireNonNull(response, "A resposta Data Export é obrigatória.");
        rejectErrorEnvelope(response);
        final JsonNode data = response.has("data") ? response.get("data") : response;
        if (data == null || data.isNull()) {
            throw new IllegalStateException("A resposta Data Export não contém registros válidos.");
        }
        if (data.isArray()) {
            final List<JsonNode> records = new ArrayList<>();
            data.forEach(records::add);
            return new DataExportPageResponse(records);
        }
        if (data.isObject()) {
            validateObjectRecord(data);
            return new DataExportPageResponse(List.of(data));
        }
        throw new IllegalStateException(
                "A resposta Data Export deve conter array ou objeto de registros.");
    }

    private void rejectErrorEnvelope(final JsonNode response) {
        if (response.isObject() && (response.has("error") || response.has("errors"))) {
            throw new IllegalStateException(
                    "A resposta Data Export contém um envelope de erro, não uma página de registros.");
        }
    }

    private void validateObjectRecord(final JsonNode data) {
        if (data.isEmpty()) {
            throw new IllegalStateException(
                    "A resposta Data Export contém um objeto de registros vazio.");
        }
        if (data.has("error") || data.has("errors")) {
            throw new IllegalStateException(
                    "A resposta Data Export contém um envelope de erro, não um registro.");
        }
    }
}

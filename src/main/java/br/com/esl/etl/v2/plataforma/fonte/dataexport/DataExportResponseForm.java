package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Forma estrutural da resposta bem-sucedida, sem reter valores do payload. */
public enum DataExportResponseForm {
    ROOT_ARRAY,
    ROOT_OBJECT,
    ENVELOPE_DATA_ARRAY,
    ENVELOPE_DATA_OBJECT;

    /** Classifica somente uma resposta que já será normalizada como página válida. */
    public static DataExportResponseForm from(final JsonNode response) {
        Objects.requireNonNull(response, "A resposta Data Export é obrigatória.");
        final boolean hasEnvelope = response.isObject() && response.has("data");
        final JsonNode data = hasEnvelope ? response.get("data") : response;
        if (data == null || data.isNull()) {
            throw new IllegalArgumentException(
                    "A resposta Data Export não contém dados classificáveis.");
        }
        if (data.isArray()) {
            return hasEnvelope ? ENVELOPE_DATA_ARRAY : ROOT_ARRAY;
        }
        if (data.isObject()) {
            return hasEnvelope ? ENVELOPE_DATA_OBJECT : ROOT_OBJECT;
        }
        throw new IllegalArgumentException(
                "A resposta Data Export não possui forma de página classificável.");
    }

    public boolean isArray() {
        return this == ROOT_ARRAY || this == ENVELOPE_DATA_ARRAY;
    }

    public String recordRoot() {
        return this == ENVELOPE_DATA_ARRAY || this == ENVELOPE_DATA_OBJECT ? "/data" : "$";
    }

    public ContractResponse.Cardinality rootCardinality() {
        return isArray() ? ContractResponse.Cardinality.ARRAY : ContractResponse.Cardinality.OBJECT;
    }
}

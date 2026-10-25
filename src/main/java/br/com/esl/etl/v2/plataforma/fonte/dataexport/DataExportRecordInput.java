package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Optional;

/** One defensively copied record and, when selected, its original bounded String slice. */
public final class DataExportRecordInput {
    private final JsonNode value;
    private final Optional<String> rawJson;

    DataExportRecordInput(final JsonNode value, final Optional<String> rawJson) {
        this.value = Objects.requireNonNull(value);
        this.rawJson = Objects.requireNonNull(rawJson);
    }

    public JsonNode value() {
        return value;
    }

    public Optional<String> rawJson() {
        return rawJson;
    }

    @Override
    public String toString() {
        return "DataExportRecordInput[rawAvailable=" + rawJson.isPresent() + "]";
    }
}

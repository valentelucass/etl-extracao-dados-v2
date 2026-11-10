package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Explicit current metadata revision; previous frozen descriptors remain unchanged. */
public final class ReportMetadataCatalog {
    private final JsonNode entities;

    public ReportMetadataCatalog() throws IOException {
        try (var input =
                getClass()
                        .getClassLoader()
                        .getResourceAsStream(
                                "contracts/dataexport/receiving-catalog-0766-v1.json")) {
            if (input == null) {
                throw new IllegalStateException("REFRESH_METADATA_RESOURCE");
            }
            entities = CurlSnapshotWire.JSON.readTree(input).required("entities");
        }
    }

    public Set<String> validate(final ReportSnapshotTemplate template, final JsonNode info) {
        final var expected = entity(template);
        final var actualFields = new HashSet<String>();
        info.path("fields").fieldNames().forEachRemaining(actualFields::add);
        final var expectedFields = new HashSet<String>();
        expected.required("fields").forEach(value -> expectedFields.add(value.asText()));
        if (!actualFields.equals(expectedFields)
                || !info.path("filters").equals(expected.path("filters"))
                || !info.path("required_date_filters").equals(expected.path("requiredDateFilters"))
                || !info.path("default_order").equals(expected.path("defaultOrder"))) {
            throw new IllegalArgumentException("REFRESH_METADATA_DRIFT");
        }
        return Set.copyOf(actualFields);
    }

    public int fieldCount(final ReportSnapshotTemplate template) {
        return entity(template).required("fields").size();
    }

    private JsonNode entity(final ReportSnapshotTemplate template) {
        for (final var entity : entities) {
            if (entity.path("entity").asText().equals(template.name())
                    && entity.path("reportId").asInt() == template.template().templateId()) {
                return entity;
            }
        }
        throw new IllegalArgumentException("REFRESH_METADATA_TEMPLATE");
    }
}

package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Synthetic offline rows from reviewed wire rules; not stored provider values or authority. */
final class ObservedExpansionSchemaFixtures {
    private ObservedExpansionSchemaFixtures() {}

    static ObservedExpansionCapturePlan plan(final ObservedExpansionCaptureTemplate template) {
        return new ObservedExpansionCapturePlan(
                UUID.randomUUID(),
                template,
                "EXTERNAL_OFFLINE_SOURCE",
                "EXTERNAL_OFFLINE_TENANT",
                new BusinessDateRange(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)),
                ZoneId.of("America/Sao_Paulo"),
                DataExportTransport.GET_WITH_BODY,
                ObservedExpansionCapturePlan.OrderBy.absent(),
                ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY,
                50,
                ObservedExpansionCaptureLimits.proposedProofBounds());
    }

    static ObservedExpansionSchemaCatalog.Schema schema(
            final ObservedExpansionCaptureTemplate template) {
        return ObservedExpansionSchemaCatalog.schema(
                ObservedExpansionSchemaCatalog.select(template));
    }

    static ObservedExpansionSchemaGuard.BoundSchema bound(final ObservedExpansionCapturePlan plan) {
        return ObservedExpansionSchemaGuard.bind(
                plan,
                ObservedExpansionSchemaCatalog.select(plan.template()),
                plan.template().templateId(),
                schema(plan.template()).metadata());
    }

    static ObjectNode row(final ObservedExpansionCaptureTemplate template) {
        final var row = JsonNodeFactory.instance.objectNode();
        for (final var field : schema(template).fields().values()) {
            if (field.unknownNonNull()) {
                row.putNull(field.path());
            } else if (field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.ARRAY)) {
                row.putArray(field.path()).add("offline").add("offline");
            } else if (field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.STRING)) {
                row.put(field.path(), "offline");
            } else if (field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.BOOLEAN)) {
                row.put(field.path(), true);
            } else if (field.wireTypes().contains(ObservedExpansionSchemaCatalog.Wire.INTEGER)) {
                row.put(field.path(), 1);
            } else {
                throw new AssertionError("Uncovered synthetic fixture kind");
            }
        }
        return row;
    }

    static ObservedExpansionRawPage raw(
            final ObservedExpansionCapturePlan plan, final ObjectNode row) {
        return raw(plan, "[" + row + "]");
    }

    static ObservedExpansionRawPage raw(
            final ObservedExpansionCapturePlan plan, final String document) {
        return new ObservedExpansionRawPage(
                plan.request(1), Instant.EPOCH, document.getBytes(StandardCharsets.UTF_8));
    }
}

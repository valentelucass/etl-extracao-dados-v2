package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Offline synthetic test data only; never a provider response or capture authority. */
final class ObservedExpansionCoreFixtures {
    private ObservedExpansionCoreFixtures() {}

    static ObservedExpansionCapturePlan plan(final ObservedExpansionCaptureTemplate template) {
        return plan(template, ObservedExpansionCaptureLimits.proposedProofBounds());
    }

    static ObservedExpansionCapturePlan plan(
            final ObservedExpansionCaptureTemplate template,
            final ObservedExpansionCaptureLimits limits) {
        return new ObservedExpansionCapturePlan(
                UUID.randomUUID(),
                template,
                "OFFLINE_SOURCE",
                "OFFLINE_TENANT",
                new BusinessDateRange(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)),
                ZoneId.of("America/Sao_Paulo"),
                DataExportTransport.GET_WITH_BODY,
                ObservedExpansionCapturePlan.OrderBy.absent(),
                ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY,
                1,
                limits);
    }

    static ObservedExpansionRawPage raw(
            final ObservedExpansionCapturePlan plan, final int page, final String body) {
        return new ObservedExpansionRawPage(
                plan.request(page),
                Instant.parse("2026-10-24T12:00:00Z"),
                body.getBytes(StandardCharsets.UTF_8));
    }

    static String rows(final String item, final int count) {
        return "[" + String.join(",", java.util.Collections.nCopies(count, item)) + "]";
    }

    static ObservedExpansionProjectedField field(
            final ObservedExpansionProjectedRecord row, final String path) {
        return row.fields().stream()
                .filter(field -> field.path().equals(path))
                .findFirst()
                .orElseThrow();
    }

    static JsonNode json(final ObservedExpansionPageProjection page) throws Exception {
        return new ObjectMapper().readTree(page.projectionJson());
    }

    static JsonNode jsonField(final ObservedExpansionPageProjection page, final String path)
            throws Exception {
        for (final JsonNode field : json(page).path("records").get(0).path("fields")) {
            if (field.path("path").asText().equals(path)) {
                return field;
            }
        }
        throw new AssertionError("Missing expected field");
    }
}

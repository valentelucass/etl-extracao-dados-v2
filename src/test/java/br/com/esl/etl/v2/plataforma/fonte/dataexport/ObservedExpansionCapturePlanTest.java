package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionCoreFixtures.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ObservedExpansionCapturePlanTest {
    @Test
    void capPreservesDateIntersectionWithoutUpdatedAtOrOrderInference() {
        final var plan = plan(ObservedExpansionCaptureTemplate.CAP);
        final var body = plan.request(1).jsonBody();
        assertEquals(8636, plan.template().templateId());
        final var filters = body.path("search").path("accounting_debits");
        assertEquals("2026-10-17 - 2026-10-17", filters.path("issue_date").asText());
        assertEquals(filters.path("issue_date"), filters.path("created_at"));
        assertEquals(2, filters.size());
        assertFalse(body.has("order_by"));
        assertEquals("1", body.path("per").asText());
        assertEquals("DATA_EXPORT", plan.sourceKind());
        assertEquals("NOT_PERFORMED_BY_CORE", plan.sourceSchemaValidation());
    }

    @Test
    void eachOtherTemplateHasOnlyItsDeclaredBusinessDatePath() {
        final var expected =
                List.of(
                        List.of(
                                ObservedExpansionCaptureTemplate.FAT,
                                4924,
                                "freights",
                                "service_at"),
                        List.of(
                                ObservedExpansionCaptureTemplate.INV,
                                10633,
                                "check_in_orders",
                                "started_at"),
                        List.of(
                                ObservedExpansionCaptureTemplate.SIN,
                                6392,
                                "insurance_claims",
                                "opening_at_date"));
        for (final List<?> row : expected) {
            final var plan = plan((ObservedExpansionCaptureTemplate) row.get(0));
            final var body = plan.request(2).jsonBody();
            assertEquals(row.get(1), plan.template().templateId());
            assertEquals(1, body.path("search").size());
            assertEquals(1, body.path("search").path((String) row.get(2)).size());
            assertEquals(
                    "2026-10-17 - 2026-10-17",
                    body.path("search")
                            .path((String) row.get(2))
                            .path((String) row.get(3))
                            .asText());
            assertEquals("2", body.path("page").asText());
        }
    }

    @Test
    void requestReturnsFreshTreesAndOrderListCannotBeMutated() {
        final var items = new ArrayList<>(List.of("sequence_code asc"));
        final var order = new ObservedExpansionCapturePlan.OrderBy(items);
        items.clear();
        final var original = plan(ObservedExpansionCaptureTemplate.SIN);
        final var plan =
                replace(
                        original,
                        "OFFLINE_SOURCE",
                        "OFFLINE_TENANT",
                        order,
                        DataExportTransport.GET_WITH_BODY,
                        50);
        final ObjectNode body = plan.request(1).jsonBody();
        body.removeAll();
        assertEquals("sequence_code asc", plan.request(1).jsonBody().path("order_by").asText());
        assertThrows(UnsupportedOperationException.class, () -> order.expressions().clear());
        assertEquals("50", plan.request(1).jsonBody().path("per").asText());
    }

    @Test
    void arbitraryOrderingDoesNotBecomeAProviderKey() {
        final var original = plan(ObservedExpansionCaptureTemplate.SIN);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replace(
                                original,
                                "OFFLINE_SOURCE",
                                "OFFLINE_TENANT",
                                ObservedExpansionCapturePlan.OrderBy.explicit("invented_root asc"),
                                DataExportTransport.GET_WITH_BODY,
                                1));
    }

    @Test
    void requestsRejectUnboundedOrAlternativePolicies() {
        final var original = plan(ObservedExpansionCaptureTemplate.CAP);
        assertThrows(IllegalArgumentException.class, () -> original.request(0));
        assertThrows(IllegalArgumentException.class, () -> original.request(5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replace(
                                original,
                                "OFFLINE_SOURCE",
                                "OFFLINE_TENANT",
                                original.orderBy(),
                                DataExportTransport.POST_JSON,
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replace(
                                original,
                                "OFFLINE_SOURCE",
                                "OFFLINE_TENANT",
                                original.orderBy(),
                                original.transport(),
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        replace(
                                original,
                                "OFFLINE_SOURCE",
                                "OFFLINE_TENANT",
                                original.orderBy(),
                                original.transport(),
                                101));
    }

    @Test
    void scopeIsNotSilentlyTrimmedOrReplacedWithSyntheticNamespace() {
        final var original = plan(ObservedExpansionCaptureTemplate.CAP);
        for (final String scope :
                List.of(" ", " OFFLINE_SOURCE", "SYNTHETIC_SOURCE", "a\nb", "x".repeat(129))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            replace(
                                    original,
                                    scope,
                                    "OFFLINE_TENANT",
                                    original.orderBy(),
                                    original.transport(),
                                    1));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            replace(
                                    original,
                                    "OFFLINE_SOURCE",
                                    scope,
                                    original.orderBy(),
                                    original.transport(),
                                    1));
        }
    }

    @Test
    void limitsCannotRaiseProposedAbsoluteCeilings() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ObservedExpansionCaptureLimits(
                                8 * 1024 * 1024 + 1, 1024, 32L * 1024 * 1024, 1024, 1, 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCaptureLimits(1, 1024, 1, 1024, 1, 1, 5, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCaptureLimits(1, 1024, 1, 1024, 1001, 2000, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCaptureLimits(1, 1024, 1, 1024, 1, 2001, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCaptureLimits(1, 1024, 1, 1024, 1, 1, 1, 100001));
    }

    private static ObservedExpansionCapturePlan replace(
            final ObservedExpansionCapturePlan original,
            final String source,
            final String tenant,
            final ObservedExpansionCapturePlan.OrderBy order,
            final DataExportTransport transport,
            final int per) {
        return new ObservedExpansionCapturePlan(
                UUID.randomUUID(),
                original.template(),
                source,
                tenant,
                new BusinessDateRange(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 17)),
                ZoneId.of("America/Sao_Paulo"),
                transport,
                order,
                original.envelope(),
                per,
                original.limits());
    }
}

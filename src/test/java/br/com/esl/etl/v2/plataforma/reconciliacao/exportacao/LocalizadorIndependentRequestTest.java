package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalizadorIndependentRequestTest {
    @Test
    void serializesOnlyTheServiceDateThenPagePerWithNoOrderOrEmptyAuxiliaryFilters() {
        final var request = LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18");
        LocalizadorIndependentRequest.validate(request);
        final var uri =
                LocalizadorIndependentRequest.query(
                        URI.create("https://synthetic.invalid/api/analytics/reports/8656/data"),
                        request,
                        1,
                        100);
        assertEquals(
                "search%5Bfreights%5D%5Bservice_at%5D=2026-10-12%20-%202026-10-18&page=1&per=100",
                uri.getRawQuery());
        assertFalse(uri.toString().contains("order_by"));
        assertFalse(uri.toString().contains("updated_at"));
        final var plan = LocalizadorIndependentRequest.plan(request);
        assertEquals(8, plan.path("filters").size());
        assertEquals("", plan.path("updatedAtFilter").textValue());
        assertFalse(plan.path("sourceQueryAuthorized").booleanValue());
        assertFalse(plan.path("sourceRootIdentityQualified").booleanValue());
    }

    @Test
    void everyAuxiliaryFilterIsExplicitlyEmptyAndNoHistoricalSimpleNameIsSubstituted() {
        for (final String field : LocalizadorIndependentRequest.FILTERS) {
            if (LocalizadorIndependentRequest.SERVICE_AT_FILTER.equals(field)) {
                continue;
            }
            final var request = LocalizadorCaptureFixture.request("2026-10-12", "2026-10-12");
            ((com.fasterxml.jackson.databind.node.ObjectNode) request.required("filters"))
                    .put(field, "authored");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorIndependentRequest.validate(request));
        }
        final var request = LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18");
        final var filters =
                (com.fasterxml.jackson.databind.node.ObjectNode) request.required("filters");
        filters.remove(LocalizadorIndependentRequest.UPDATED_AT_FILTER);
        filters.put("by_updated_at", "");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorIndependentRequest.validate(request));
    }

    @Test
    void periodsQueriesAndProfilesStayFiniteAndCannotUseThePendingAuthoringScope() {
        LocalizadorIndependentRequest.validate(
                LocalizadorCaptureFixture.request("2026-10-12", "2026-10-12"));
        for (final var request :
                List.of(
                        LocalizadorCaptureFixture.request("2026-10-12", "2026-10-19"),
                        LocalizadorCaptureFixture.request("2026-02-30", "2026-03-01"),
                        LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18")
                                .put("schema", "LOCALIZADOR_REQUEST_AUTHORING_V1"),
                        LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18")
                                .put("order_by", "id asc"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorIndependentRequest.validate(request));
        }
        final var request = LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18");
        final var endpoint =
                URI.create("https://synthetic.invalid/api/analytics/reports/8656/data");
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorIndependentRequest.query(endpoint, request, 17, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizadorIndependentRequest.query(endpoint, request, 1, 101));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LocalizadorIndependentRequest.query(
                                URI.create(
                                        "https://synthetic.invalid/api/analytics/reports/8656/data?x=1"),
                                request,
                                1,
                                100));
    }

    @Test
    void serviceAtKeepsOffsetCivilDayAndAllFractionsWithoutConvertingToUtc() throws Exception {
        final var request = LocalizadorCaptureFixture.request("2026-10-12", "2026-10-18");
        for (final String value :
                List.of(
                        "2026-10-12T00:00:00.000000001+14:00",
                        "2026-10-18T23:59:59.123456789-03:00")) {
            final var row = LocalizadorCaptureFixture.row(1).put("service_at", value);
            LocalizadorIndependentRequest.verifyServiceAt(row, request);
            assertEquals(value, row.path("service_at").textValue());
        }
        for (final String value :
                List.of(
                        "2026-10-11T23:59:59-03:00",
                        "2026-10-19T00:00:00Z",
                        "2026-10-13",
                        "2026-10-13T12:00:00",
                        " 2026-10-13T12:00:00Z",
                        "2026-02-30T12:00:00Z")) {
            final var row = LocalizadorCaptureFixture.row(1).put("service_at", value);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LocalizadorIndependentRequest.verifyServiceAt(row, request));
        }
        assertTrue(LocalizadorIndependentRequest.PROFILE.endsWith("SERVICE_AT_NATIVE26_V1"));
    }
}

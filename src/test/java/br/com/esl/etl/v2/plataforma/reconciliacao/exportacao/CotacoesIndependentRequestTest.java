package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CotacoesIndependentRequestTest {
    @Test
    void threeFullWeeksSerializeOnlyTheExactRequestedAtDateQueryWithoutOrderBy() {
        final var queries = new HashSet<String>();
        for (final var dates :
                List.of(
                        new String[] {"2026-10-12", "2026-10-18"},
                        new String[] {"2026-10-19", "2026-10-25"},
                        new String[] {"2026-10-26", "2026-11-01"})) {
            final var request = CotacoesCaptureFixture.request(dates[0], dates[1]);
            final var uri = CotacoesIndependentRequest.query(endpoint(), request, 1, 100);
            assertTrue(queries.add(uri.toASCIIString()));
            assertTrue(uri.getRawQuery().startsWith("search%5Bquotes%5D%5Brequested_at%5D="));
            assertTrue(uri.getRawQuery().endsWith("&page=1&per=100"));
            assertFalse(uri.getRawQuery().contains("order"));
            assertEquals(6, request.path("filters").size());
            assertEquals(37, CotacoesCaptureFixtureFields.count());
        }
    }

    @Test
    void missingExtraAndNonemptyAuxiliaryFiltersCannotSilentlyChangeTheScope() {
        for (final String mutation : List.of("missing", "extra", "value")) {
            final var request = CotacoesCaptureFixture.request("2026-10-12", "2026-10-18");
            final var filters = (ObjectNode) request.required("filters");
            if ("missing".equals(mutation)) {
                filters.remove("quotes[user_id]");
            } else if ("extra".equals(mutation)) {
                filters.put("quotes[unknown]", "");
            } else {
                filters.put("quotes[customer_id]", "restricted");
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesIndependentRequest.validate(request));
        }
    }

    @Test
    void wrongEntityReportUuidZonePeriodOrFilterDatesAreRejected() {
        for (final String field :
                List.of("entity", "reportId", "attemptId", "zone", "periodStart", "filters")) {
            final var request = CotacoesCaptureFixture.request("2026-10-12", "2026-10-18");
            switch (field) {
                case "reportId" -> request.put(field, 6399);
                case "entity" -> request.put(field, "MANIFESTOS");
                case "attemptId" -> request.put(field, "not-a-uuid");
                case "zone" -> request.put(field, "UTC");
                case "periodStart" -> request.put(field, "2026-10-19");
                default ->
                        ((ObjectNode) request.required("filters"))
                                .put(
                                        CotacoesIndependentRequest.REQUESTED_AT_FILTER,
                                        "2026-10-13 - 2026-10-18");
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesIndependentRequest.validate(request));
        }
    }

    @Test
    void endpointAndPageCeilingsRejectOtherReportsQueriesFragmentsAndPageSeventeen() {
        final var request = CotacoesCaptureFixture.request("2026-10-12", "2026-10-18");
        for (final String uri :
                List.of(
                        "https://capture.invalid/api/analytics/reports/6399/data",
                        endpoint() + "?page=1",
                        endpoint() + "#fragment",
                        "http://capture.invalid/api/analytics/reports/6906/data")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CotacoesIndependentRequest.query(URI.create(uri), request, 1, 100));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacoesIndependentRequest.query(endpoint(), request, 17, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacoesIndependentRequest.query(endpoint(), request, 1, 101));
    }

    private static URI endpoint() {
        return URI.create("https://capture.invalid/api/analytics/reports/6906/data");
    }

    private static final class CotacoesCaptureFixtureFields {
        static int count() {
            try {
                return CotacoesCaptureFixture.contract().path("response").path("fields").size();
            } catch (final Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }
}

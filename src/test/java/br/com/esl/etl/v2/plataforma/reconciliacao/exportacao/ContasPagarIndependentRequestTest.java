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

class ContasPagarIndependentRequestTest {
    @Test
    void threeFullWeeksSerializeOnlyIssueDateAndKeepCreatedAtExplicitlyEmpty() {
        final var queries = new HashSet<String>();
        for (final var dates :
                List.of(
                        new String[] {"2026-10-12", "2026-10-18"},
                        new String[] {"2026-10-19", "2026-10-25"},
                        new String[] {"2026-10-26", "2026-11-01"})) {
            final var request = ContasPagarCaptureFixture.request(dates[0], dates[1]);
            final var uri = ContasPagarIndependentRequest.query(endpoint(), request, 1, 100);
            assertTrue(queries.add(uri.toASCIIString()));
            final String period = dates[0] + "%20-%20" + dates[1];
            assertEquals(
                    "search%5Baccounting_debits%5D%5Bissue_date%5D="
                            + period
                            + "&page=1&per=100&order_by=issue_date%20desc",
                    uri.getRawQuery());
            assertFalse(uri.getRawQuery().contains("created_at"));
            assertEquals(
                    "",
                    request.path("filters")
                            .path(ContasPagarIndependentRequest.CREATED_AT_FILTER)
                            .textValue());
            assertEquals(9, request.path("filters").size());
            assertEquals(28, ContasPagarCaptureFixtureFields.count());
            assertFalse(
                    ContasPagarIndependentRequest.plan(request)
                            .path("sourceRootIdentityQualified")
                            .booleanValue());
            assertEquals(
                    ContasPagarIndependentRequest.FILTER_SCOPE_POLICY,
                    ContasPagarIndependentRequest.plan(request)
                            .path("filterScopePolicy")
                            .textValue());
            assertFalse(
                    ContasPagarIndependentRequest.plan(request)
                            .path("createdAtScopeEnforced")
                            .booleanValue());
        }
    }

    @Test
    void missingExtraAndNonemptyAuxiliaryFiltersCannotSilentlyChangeTheScope() {
        for (final String mutation : List.of("missing", "extra", "value")) {
            final var request = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
            final var filters = (ObjectNode) request.required("filters");
            if ("missing".equals(mutation)) {
                filters.remove("scopes[from_competence_period_string]");
            } else if ("extra".equals(mutation)) {
                filters.put("accounting_debits[unknown]", "");
            } else {
                filters.put("accounting_debits[corporation_id]", "restricted");
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(request));
        }
    }

    @Test
    void wrongEntityReportUuidZonePeriodOrFilterDatesAreRejected() {
        for (final String field :
                List.of("entity", "reportId", "attemptId", "zone", "periodStart", "filters")) {
            final var request = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
            switch (field) {
                case "reportId" -> request.put(field, 6399);
                case "entity" -> request.put(field, "MANIFESTOS");
                case "attemptId" -> request.put(field, "not-a-uuid");
                case "zone" -> request.put(field, "UTC");
                case "periodStart" -> request.put(field, "2026-10-19");
                default ->
                        ((ObjectNode) request.required("filters"))
                                .put(
                                        ContasPagarIndependentRequest.ISSUE_DATE_FILTER,
                                        "2026-10-13 - 2026-10-18");
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(request));
        }
    }

    @Test
    void issueDateMustBindTheWeekAndCreatedAtMustBePresentEmptyNeverFilled() {
        for (final String value :
                List.of("", "2026-10-19 - 2026-10-25", "2026-02-30 - 2026-03-01")) {
            final var request = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
            ((ObjectNode) request.required("filters"))
                    .put(ContasPagarIndependentRequest.ISSUE_DATE_FILTER, value);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(request));
        }
        for (final String filter :
                List.of(
                        ContasPagarIndependentRequest.ISSUE_DATE_FILTER,
                        ContasPagarIndependentRequest.CREATED_AT_FILTER)) {
            final var missing = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
            ((ObjectNode) missing.required("filters")).remove(filter);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(missing));
        }
        for (final String value :
                List.of("2026-10-12 - 2026-10-18", " ", "2026-10-01 - 2026-11-01")) {
            final var request = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
            ((ObjectNode) request.required("filters"))
                    .put(ContasPagarIndependentRequest.CREATED_AT_FILTER, value);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(request));
        }
    }

    @Test
    void endpointAndPageCeilingsRejectOtherReportsQueriesFragmentsAndPageSeventeen() {
        final var request = ContasPagarCaptureFixture.request("2026-10-12", "2026-10-18");
        for (final String uri :
                List.of(
                        "https://capture.invalid/api/analytics/reports/6399/data",
                        endpoint() + "?page=1",
                        endpoint() + "#fragment",
                        "http://capture.invalid/api/analytics/reports/8636/data")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.query(URI.create(uri), request, 1, 100));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarIndependentRequest.query(endpoint(), request, 17, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContasPagarIndependentRequest.query(endpoint(), request, 1, 101));
    }

    @Test
    void intervalAcceptsOneToSevenDaysAndRefusesEightDaysOrAnInvertedRange() {
        for (final String end : List.of("2026-10-12", "2026-10-13", "2026-10-18")) {
            ContasPagarIndependentRequest.validate(
                    ContasPagarCaptureFixture.request("2026-10-12", end));
        }
        for (final String end : List.of("2026-10-11", "2026-10-19", "2026-11-12")) {
            final var request = ContasPagarCaptureFixture.request("2026-10-12", end);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ContasPagarIndependentRequest.validate(request));
        }
    }

    private static URI endpoint() {
        return URI.create("https://capture.invalid/api/analytics/reports/8636/data");
    }

    private static final class ContasPagarCaptureFixtureFields {
        static int count() {
            try {
                return ContasPagarCaptureFixture.contract().path("fields").size();
            } catch (final Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }
}

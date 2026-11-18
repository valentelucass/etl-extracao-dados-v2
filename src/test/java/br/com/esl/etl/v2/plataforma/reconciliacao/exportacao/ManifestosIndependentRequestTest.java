package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManifestosIndependentRequestTest {
    private static final URI ENDPOINT =
            URI.create("https://example.invalid/api/analytics/reports/6399/data");

    @Test
    void exactFilterCatalogAndServiceDateComeFromQualifiedInfoResource() throws Exception {
        try (var stream =
                getClass()
                        .getResourceAsStream(
                                "/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json")) {
            assertNotNull(stream);
            final var contract = ExportValidationSupport.JSON.readTree(stream);
            assertEquals(6399, contract.path("templateId").intValue());
            final var expected = new HashSet<String>();
            int fields = 0;
            for (final var metadata : contract.required("metadata")) {
                if ("DATA_FILTER".equals(metadata.path("kind").asText())) {
                    final String[] path = metadata.required("path").textValue().split("\\.", -1);
                    assertEquals(2, path.length);
                    expected.add(path[0] + "[" + path[1] + "]");
                    if ("manifests.service_date".equals(metadata.path("path").asText())) {
                        assertEquals("DATE", metadata.path("declaredType").asText());
                    }
                } else if ("DATA_FIELD".equals(metadata.path("kind").asText())) {
                    fields++;
                }
            }
            assertEquals(91, fields);
            assertEquals(18, expected.size());
            assertEquals(expected, ManifestosIndependentRequest.FILTERS);
            assertTrue(expected.contains(ManifestosIndependentRequest.SERVICE_DATE_FILTER));
            assertFalse(contract.path("acceptance").path("sourceQueryAuthorized").asBoolean());
            assertFalse(
                    contract.path("requestEvidence")
                            .path("newRequestSemanticsApproved")
                            .asBoolean());
        }
    }

    @Test
    void planKeepsAllControlsAndExplicitlyWithholdsContentCollectionAndExportQualification() {
        final var request = request();
        final var plan = ManifestosIndependentRequest.plan(request);
        assertEquals("INDEPENDENT_EXPORT_PLAN_V1", plan.path("schema").asText());
        assertEquals(ManifestosIndependentRequest.PROFILE, plan.path("profile").asText());
        assertEquals("MANIFESTOS", plan.path("entity").asText());
        assertEquals(6399, plan.path("reportId").intValue());
        assertEquals(request.path("attemptId"), plan.path("attemptId"));
        assertEquals(request.path("periodStart"), plan.path("periodStart"));
        assertEquals(request.path("periodEnd"), plan.path("periodEnd"));
        assertEquals("America/Sao_Paulo", plan.path("zone").asText());
        assertEquals("GET_WITH_QUERY", plan.path("transport").asText());
        assertEquals("GET", plan.path("method").asText());
        assertEquals("/api/analytics/reports/6399/info", plan.path("infoPath").asText());
        assertEquals("/api/analytics/reports/6399/data", plan.path("dataPath").asText());
        assertEquals("INDEPENDENT_UI_EXPORT_XLSX", plan.path("referenceRoute").asText());
        assertEquals(request.path("filters"), plan.path("filters"));
        assertEquals(18, plan.path("filters").size());
        for (final String flag :
                List.of(
                        "contentQualified",
                        "sourceQueryAuthorized",
                        "fileExportQualified",
                        "paginationQualified",
                        "mainResumeAllowed",
                        "runtimeAdopted",
                        "snapshotIdentityProven")) {
            assertTrue(plan.path(flag).isBoolean(), flag);
            assertFalse(plan.path(flag).booleanValue(), flag);
        }
        for (final String counter : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, plan.required(counter).intValue(), counter);
        }
        assertFalse(plan.has("body"));
        assertFalse(plan.has("path"));
        assertEquals(64, plan.path("localPageCeiling").intValue());
        assertEquals(100, plan.path("localPerCeiling").intValue());
    }

    @Test
    void plannedFiltersAreDetachedFromMutableRequest() {
        final var request = request();
        final var plan = ManifestosIndependentRequest.plan(request);
        filters(request).put("manifests[status]", "unqualified");
        assertEquals("", plan.path("filters").path("manifests[status]").textValue());
    }

    @Test
    void otherEntitySchemaProfileOrZoneCannotBorrowTheManifestosContract() {
        for (final String[] change :
                new String[][] {
                    {"entity", "COLETAS"},
                    {"entity", "FRETES"},
                    {"schema", "COLETAS_INDEPENDENT_REQUEST_V1"},
                    {"schema", "INDEPENDENT_EXPORT_REQUEST_V1"},
                    {"profile", ColetasIndependentRequest.PROFILE},
                    {"profile", IndependentExportRequest.PROFILE},
                    {"profile", "MANIFESTOS_6399_NATIVE91_V2"},
                    {"zone", "UTC"}
                }) {
            final var request = request().put(change[0], change[1]);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.plan(request),
                    change[0]);
        }
    }

    @Test
    void reportIdentityMustBeTheExactIntegralTemplateWithoutCoercionOrOverflow() throws Exception {
        for (final String report :
                List.of("6908", "6389", "\"6399\"", "6399.0", "4294973695", "null", "true")) {
            final var request = request();
            request.set("reportId", ExportValidationSupport.JSON.readTree(report));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request),
                    report);
        }
    }

    @Test
    void missingIdentityAndInvalidAttemptAreRefused() {
        for (final String field :
                List.of("schema", "profile", "entity", "reportId", "zone", "attemptId")) {
            final var request = request();
            request.remove(field);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request),
                    field);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosIndependentRequest.validate(request().put("attemptId", "invalid")));
        assertThrows(
                IllegalArgumentException.class, () -> ManifestosIndependentRequest.validate(null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosIndependentRequest.validate(
                                ExportValidationSupport.JSON.createArrayNode()));
    }

    @Test
    void everyObservedFilterIsRequiredEvenWhenEmpty() {
        for (final String name : ManifestosIndependentRequest.FILTERS) {
            final var request = request();
            filters(request).remove(name);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request),
                    name);
        }
    }

    @Test
    void extraUnqualifiedOrWronglyPrefixedNamesCannotReplaceTheQualifiedControls() {
        for (final String name :
                List.of(
                        "service_date",
                        "manifests.service_date",
                        "manifests[with_ciot]",
                        "manifests[mobile_receipt]",
                        "picks[request_date]",
                        "freights[service_at]")) {
            final var extra = request();
            filters(extra).put(name, "");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(extra),
                    name);
            final var replacement = request();
            filters(replacement).remove("scopes[with_ciot]");
            filters(replacement).put(name, "");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(replacement),
                    name);
        }
    }

    @Test
    void noAuxiliaryFilterHasQualifiedNonemptyValueSemantics() {
        for (final String name : ManifestosIndependentRequest.FILTERS) {
            if (!ManifestosIndependentRequest.SERVICE_DATE_FILTER.equals(name)) {
                final var request = request();
                filters(request).put(name, "unqualified");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ManifestosIndependentRequest.validate(request),
                        name);
            }
        }
    }

    @Test
    void filterValuesMustBeBoundedTextAndMayNotCoerceJsonValues() throws Exception {
        for (final String name :
                List.of(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "scopes[with_ciot]")) {
            for (final String value : List.of("null", "false", "0", "[]", "{}")) {
                final var request = request();
                filters(request).set(name, ExportValidationSupport.JSON.readTree(value));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ManifestosIndependentRequest.validate(request),
                        value);
            }
            final var request = request();
            filters(request).put(name, "x".repeat(1025));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request));
        }
    }

    @Test
    void filtersMustBePresentAsAnObject() throws Exception {
        for (final String value : List.of("null", "[]", "\"filters\"")) {
            final var request = request();
            request.set("filters", ExportValidationSupport.JSON.readTree(value));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request));
        }
        final var request = request();
        request.remove("filters");
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosIndependentRequest.validate(request));
    }

    @Test
    void reversedOversizedInvalidAndNonIsoRequestPeriodsAreRefused() {
        for (final String[] period :
                new String[][] {
                    {"2024-02-02", "2024-02-01"}, {"2024-02-01", "2024-03-04"},
                    {"2023-02-29", "2023-03-01"}, {"2024-02-01", "2024-02-30"},
                    {"01/02/2024", "2024-02-02"}, {"2024-2-01", "2024-02-02"},
                    {"+10000-02-01", "+10000-02-02"}, {"2024-02-01T00:00:00", "2024-02-02"}
                }) {
            final var request = period(period[0], period[1]);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request));
        }
        for (final String field : List.of("periodStart", "periodEnd")) {
            final var request = request();
            request.remove(field);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request));
        }
    }

    @Test
    void serviceDateMustMatchBothEndsAndUseOneOfTheTwoStrictDatePresentations() {
        for (final String value :
                List.of(
                        "2024-02-01 - 2024-02-03",
                        "2024-01-31 - 2024-02-02",
                        "2024-02-02 - 2024-02-01",
                        "31/02/2024 - 02/02/2024",
                        "",
                        " ",
                        "2024-02-01/2024-02-02",
                        "2024-02-01 - 02/02/2024")) {
            final var request = request();
            filters(request).put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, value);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.validate(request),
                    value);
        }
    }

    @Test
    void sameDayLeapDayAndMaximumLocalPeriodAreAcceptedForPlanning() {
        assertDoesNotThrow(
                () -> ManifestosIndependentRequest.validate(period("2024-02-29", "2024-02-29")));
        assertDoesNotThrow(
                () -> ManifestosIndependentRequest.validate(period("2024-02-01", "2024-03-03")));
        final var localized = request();
        filters(localized)
                .put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "01/02/2024 - 02/02/2024");
        assertDoesNotThrow(() -> ManifestosIndependentRequest.validate(localized));
    }

    @Test
    void queryUsesOnlyServiceDateAndExplicitPaginationWithNoOrderOrBody() {
        assertEquals(
                "search%5Bmanifests%5D%5Bservice_date%5D=2024-02-01%20-%202024-02-02&page=1&per=50",
                ManifestosIndependentRequest.query(ENDPOINT, request(), 1, 50).getRawQuery());
        final var localized = request();
        filters(localized)
                .put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, "01/02/2024 - 02/02/2024");
        assertEquals(
                ManifestosIndependentRequest.query(ENDPOINT, request(), 1, 50),
                ManifestosIndependentRequest.query(ENDPOINT, localized, 1, 50));
    }

    @Test
    void paginationHasLocalBoundsWithoutClaimingProviderQualification() {
        assertTrue(
                ManifestosIndependentRequest.query(ENDPOINT, request(), 1, 1)
                        .getRawQuery()
                        .endsWith("page=1&per=1"));
        assertTrue(
                ManifestosIndependentRequest.query(ENDPOINT, request(), 64, 100)
                        .getRawQuery()
                        .endsWith("page=64&per=100"));
        for (final int[] bounds :
                new int[][] {
                    {0, 50},
                    {-1, 50},
                    {65, 50},
                    {Integer.MAX_VALUE, 50},
                    {1, 0},
                    {1, -1},
                    {1, 101},
                    {1, Integer.MAX_VALUE}
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ManifestosIndependentRequest.query(
                                    ENDPOINT, request(), bounds[0], bounds[1]));
        }
    }

    @Test
    void requestCannotSmuggleIgnoredPaginationOrAnotherTransportIntoThePlan() {
        for (final String field : List.of("page", "per", "order_by", "transport", "body")) {
            final var request = request().put(field, "unqualified");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ManifestosIndependentRequest.plan(request),
                    field);
        }
    }

    @Test
    void endpointCannotSwitchTemplateTransportOrInjectUnvalidatedQueryControls() {
        for (final String endpoint :
                List.of(
                        "https://example.invalid/api/analytics/reports/6908/data",
                        "https://example.invalid/api/analytics/reports/6389/export",
                        "https://example.invalid/api/analytics/reports/6399/export",
                        ENDPOINT + "?page=0",
                        ENDPOINT + "?search%5Bmanifests%5D%5Bstatus%5D=open",
                        ENDPOINT + "#fragment",
                        "http://example.invalid/api/analytics/reports/6399/data",
                        "https://user@example.invalid/api/analytics/reports/6399/data",
                        "/api/analytics/reports/6399/data")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ManifestosIndependentRequest.query(
                                    URI.create(endpoint), request(), 1, 50));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosIndependentRequest.query(null, request(), 1, 50));
    }

    @Test
    void queryRevalidatesRequestBeforeOmittingEmptyAuxiliaryControls() {
        final var request = request();
        filters(request).put("manifests[status]", "unqualified");
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosIndependentRequest.query(ENDPOINT, request, 1, 50));
        final var mixed = request().put("reportId", 6908);
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosIndependentRequest.query(ENDPOINT, mixed, 1, 50));
    }

    private static ObjectNode request() {
        return period("2024-02-01", "2024-02-02");
    }

    private static ObjectNode period(final String start, final String end) {
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_INDEPENDENT_REQUEST_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("entity", "MANIFESTOS")
                        .put("reportId", 6399)
                        .put("attemptId", UUID.randomUUID().toString())
                        .put("periodStart", start)
                        .put("periodEnd", end)
                        .put("zone", "America/Sao_Paulo");
        final var filters = request.putObject("filters");
        ManifestosIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
        filters.put(ManifestosIndependentRequest.SERVICE_DATE_FILTER, start + " - " + end);
        return request;
    }

    private static ObjectNode filters(final ObjectNode request) {
        return (ObjectNode) request.required("filters");
    }
}

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit 6908 current Data Export contract; never GraphQL or an implicit historical substitution.
 */
public final class ColetasIndependentRequest {
    public static final String PROFILE = "COLETAS_6908_NATIVE31_V1";
    public static final Set<String> FILTERS =
            Set.of(
                    "picks[finish_date]",
                    "picks[service_date]",
                    "picks[request_date]",
                    "picks[corporation_id]",
                    "picks[status]",
                    "scopes[by_updated_at]");

    private ColetasIndependentRequest() {}

    public static void validate(final JsonNode request) {
        require(
                "COLETAS_INDEPENDENT_REQUEST_V1".equals(text(request, "schema"))
                        && PROFILE.equals(text(request, "profile"))
                        && request.path("reportId").asInt() == 6908
                        && "COLETAS".equals(text(request, "entity"))
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "COLETAS_EXPLICIT_REQUEST");
        UUID.fromString(text(request, "attemptId"));
        final var start = LocalDate.parse(text(request, "periodStart"));
        final var end = LocalDate.parse(text(request, "periodEnd"));
        require(!end.isBefore(start) && end.isBefore(start.plusDays(32)), "COLETAS_PERIOD_BOUND");
        final var filters = request.required("filters");
        final var actual = new HashSet<String>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(filters.isObject() && actual.equals(FILTERS), "COLETAS_ALL_SIX_INFO_FILTERS");
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        entry.getValue().isTextual()
                                                && entry.getValue().textValue().length() <= 1024
                                                && ("picks[request_date]".equals(entry.getKey())
                                                        || entry.getValue().textValue().isEmpty()),
                                        "COLETAS_AUXILIARY_FILTER_REQUIRES_QUALIFICATION"));
        require(
                (start + " - " + end)
                        .equals(
                                IndependentExportRequest.canonicalPeriod(
                                        filters.path("picks[request_date]").asText())),
                "COLETAS_PERIOD_FILTER_BINDING");
    }

    public static boolean sameFilters(final JsonNode expected, final JsonNode observed) {
        if (!expected.isObject()
                || !observed.isObject()
                || expected.size() != 6
                || observed.size() != 6) {
            return false;
        }
        for (final String name : FILTERS) {
            final var left = expected.path(name);
            final var right = observed.path(name);
            if (!left.isTextual() || !right.isTextual()) {
                return false;
            }
            if ("picks[request_date]".equals(name)) {
                if (!IndependentExportRequest.canonicalPeriod(left.textValue())
                        .equals(IndependentExportRequest.canonicalPeriod(right.textValue()))) {
                    return false;
                }
            } else if (!left.equals(right)) {
                return false;
            }
        }
        return true;
    }

    public static ObjectNode plan(final JsonNode request) {
        validate(request);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_PLAN_V1")
                        .put("profile", PROFILE)
                        .put("attemptId", text(request, "attemptId"))
                        .put("transport", "GET_WITH_QUERY")
                        .put("infoPath", "/api/analytics/reports/6908/info")
                        .put("dataPath", "/api/analytics/reports/6908/data")
                        .put("referenceRoute", "INDEPENDENT_UI_EXPORT_XLSX")
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("mainResumeAllowed", false)
                        .put("snapshotIdentityProven", false);
        result.set("filters", request.required("filters"));
        return result;
    }

    public static ObjectNode body(final JsonNode request, final int page, final int per) {
        validate(request);
        require(page > 0 && page <= 64 && per > 0 && per <= 100, "COLETAS_PAGE_BOUND");
        final var body = ExportValidationSupport.JSON.createObjectNode();
        body.putObject("search")
                .putObject("picks")
                .put(
                        "request_date",
                        IndependentExportRequest.canonicalPeriod(
                                request.path("filters").path("picks[request_date]").asText()));
        body.put("page", Integer.toString(page)).put("per", Integer.toString(per));
        return body;
    }

    public static java.net.URI query(
            final java.net.URI endpoint, final JsonNode request, final int page, final int per) {
        body(
                request, page,
                per); // Reuse the exact bounded request/filter validation; no GET body is sent.
        final var filters =
                new java.util.LinkedHashMap<
                        br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath,
                        br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFilterValue>();
        filters.put(
                new br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath(
                        "picks", "request_date"),
                new br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange(
                        LocalDate.parse(text(request, "periodStart")),
                        LocalDate.parse(text(request, "periodEnd"))));
        return new br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportQueryEncoder()
                .appendQuery(
                        endpoint,
                        filters,
                        page,
                        per,
                        java.util.List.of(),
                        java.time.ZoneId.of(text(request, "zone")));
    }
}

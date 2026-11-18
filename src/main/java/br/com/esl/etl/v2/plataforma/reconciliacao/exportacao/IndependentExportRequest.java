package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Explicit file-export contract, separate from the historical GET /data contract. */
public final class IndependentExportRequest {
    public static final String PROFILE = "FRETES_6389_EXPORT_XLSX_V1";
    public static final Set<String> FRETES_FILTERS =
            Set.of(
                    "freights[service_at]",
                    "scopes[by_updated_at]",
                    "scopes[from_freight_delivered_at]",
                    "freights[freight_classification_id]",
                    "freights[status]",
                    "scopes[from_ctes_issue_at]",
                    "scopes[from_nfse_issued_at]",
                    "freights[payer_id]",
                    "scopes[from_delivery_prediction_at]",
                    "fit_freight_location[current_location_id]",
                    "fit_destination_city[state_id]",
                    "freights[courtesy]",
                    "freights[type]",
                    "freights[emission_type]",
                    "freights[corporation_sequence_number]",
                    "freights[corporation_id]");

    private IndependentExportRequest() {}

    /** Only the two observed date-filter presentations; every other filter remains literal. */
    public static String canonicalPeriod(final String value) {
        final DateTimeFormatter format;
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} - [0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            format = DateTimeFormatter.ISO_LOCAL_DATE;
        } else {
            require(
                    value.matches("[0-9]{2}/[0-9]{2}/[0-9]{4} - [0-9]{2}/[0-9]{2}/[0-9]{4}"),
                    "EXPORT_OBSERVED_DATE_FILTER_FORMAT");
            format =
                    DateTimeFormatter.ofPattern("dd/MM/uuuu")
                            .withResolverStyle(ResolverStyle.STRICT);
        }
        final String[] dates = value.split(" - ", -1);
        return LocalDate.parse(dates[0], format) + " - " + LocalDate.parse(dates[1], format);
    }

    public static boolean filtersEquivalent(final JsonNode left, final JsonNode right) {
        if (!left.isObject() || !right.isObject() || left.size() != 16 || right.size() != 16) {
            return false;
        }
        for (final String name : FRETES_FILTERS) {
            final var a = left.path(name);
            final var b = right.path(name);
            if (!a.isTextual() || !b.isTextual()) {
                return false;
            }
            if ("freights[service_at]".equals(name)) {
                if (!canonicalPeriod(a.textValue()).equals(canonicalPeriod(b.textValue()))) {
                    return false;
                }
            } else if (!a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    public static JsonNode canonicalNativeFilters(final JsonNode filters) {
        require(
                filters.isObject()
                        && filters.path("freights").isObject()
                        && filters.path("freights").path("service_at").isTextual(),
                "EXPORT_NATIVE_FILTER_SHAPE");
        final ObjectNode copy = filters.deepCopy();
        ((ObjectNode) copy.required("freights"))
                .put(
                        "service_at",
                        canonicalPeriod(filters.path("freights").path("service_at").textValue()));
        return copy;
    }

    public static void validate(final JsonNode request) {
        require(
                "INDEPENDENT_EXPORT_REQUEST_V1".equals(text(request, "schema")),
                "EXPORT_REQUEST_SCHEMA");
        UUID.fromString(text(request, "attemptId"));
        require(
                PROFILE.equals(text(request, "profile"))
                        && "FRETES".equals(text(request, "entity"))
                        && request.path("reportId").asInt() == 6389,
                "EXPORT_EXPLICIT_PROFILE_REQUIRED");
        final LocalDate start = LocalDate.parse(text(request, "periodStart"));
        final LocalDate end = LocalDate.parse(text(request, "periodEnd"));
        require(!end.isBefore(start) && end.isBefore(start.plusDays(32)), "EXPORT_PERIOD_BOUND");
        ZoneId.of(text(request, "zone"));
        final var filters = request.required("filters");
        require(filters.isObject(), "EXPORT_FILTER_OBJECT");
        final Set<String> actual = new HashSet<>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FRETES_FILTERS), "EXPORT_ALL_FILTERS_REQUIRED");
        filters.elements()
                .forEachRemaining(
                        value ->
                                require(
                                        value.isTextual() && value.textValue().length() <= 1024,
                                        "EXPORT_FILTER_STRING"));
        require(
                (start + " - " + end).equals(filters.path("freights[service_at]").asText()),
                "EXPORT_PERIOD_FILTER_BINDING");
        // Auxiliary filter syntax has not been qualified for this export profile. Preserve all
        // sixteen observed controls; reject a nonempty unqualified filter instead of dropping it.
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        "freights[service_at]".equals(entry.getKey())
                                                || entry.getValue().textValue().isEmpty(),
                                        "EXPORT_FILTER_NEEDS_QUALIFICATION"));
    }

    public static ObjectNode body(final JsonNode request) {
        validate(request);
        final var body = ExportValidationSupport.JSON.createObjectNode();
        body.put("export_to_ftp", false);
        body.putObject("search")
                .putObject("freights")
                .put(
                        "service_at",
                        request.path("filters").path("freights[service_at]").textValue());
        return body;
    }

    public static ObjectNode plan(final JsonNode request) {
        final var result = ExportValidationSupport.JSON.createObjectNode();
        result.put("schema", "INDEPENDENT_EXPORT_PLAN_V1");
        result.put("attemptId", text(request, "attemptId"));
        result.put("profile", PROFILE);
        result.put("method", "POST");
        result.put("path", "/api/analytics/reports/6389/export");
        result.set("body", body(request));
        result.set("observedFiltersRequiredAtUiClick", request.required("filters"));
        result.put("uiState", "PENDING_OPERATOR_SESSION_AND_CLICK_OBSERVATION");
        result.put("requestTogetherInSameCycle", true);
        result.put("snapshotIdentityProven", false);
        result.put("Source", 0);
        result.put("HTTP", 0);
        result.put("SQL", 0);
        result.put("mainResumeAllowed", false);
        return result;
    }
}

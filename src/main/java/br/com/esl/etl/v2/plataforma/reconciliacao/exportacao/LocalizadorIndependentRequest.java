package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFilterValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportQueryEncoder;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit Root-selected 8656 service-date scope; serialization alone grants no Source or Native
 * presentation.
 */
public final class LocalizadorIndependentRequest {
    public static final String PROFILE = "LOCALIZADOR_8656_SERVICE_AT_NATIVE26_V1";
    public static final String FILTER_SCOPE_POLICY = "SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1";
    public static final String SERVICE_AT_FILTER = "freights[service_at]";
    public static final String UPDATED_AT_FILTER = "scopes[by_updated_at]";
    public static final Set<String> HISTORICAL_FILTER_NAMES =
            Set.of(
                    "by_updated_at",
                    "current_location_id",
                    "key",
                    "number",
                    "payer_id",
                    "service_at",
                    "status",
                    "type");
    public static final Set<String> FILTERS =
            Set.of(
                    SERVICE_AT_FILTER,
                    UPDATED_AT_FILTER,
                    "fit_freight_location[current_location_id]",
                    "freights[payer_id]",
                    "freights[type]",
                    "freights[status]",
                    "fit_fis_invoice[number]",
                    "fit_fis_invoice[key]");
    private static final String DATA_PATH = "/api/analytics/reports/8656/data";
    private static final int MAX_PAGE = 16;
    private static final int MAX_PER = 100;
    private static final Set<String> REQUEST_FIELDS =
            Set.of(
                    "schema",
                    "profile",
                    "entity",
                    "reportId",
                    "attemptId",
                    "periodStart",
                    "periodEnd",
                    "zone",
                    "filters");

    private LocalizadorIndependentRequest() {}

    public static void validate(final JsonNode request) {
        require(request != null && request.isObject(), "LOCALIZADOR_REQUEST_OBJECT");
        final var requestFields = new HashSet<String>();
        request.fieldNames().forEachRemaining(requestFields::add);
        require(requestFields.equals(REQUEST_FIELDS), "LOCALIZADOR_EXPLICIT_REQUEST_FIELDS");
        require(
                "LOCALIZADOR_INDEPENDENT_REQUEST_V1".equals(text(request, "schema"))
                        && PROFILE.equals(text(request, "profile"))
                        && "LOCALIZADOR".equals(text(request, "entity"))
                        && request.path("reportId").isIntegralNumber()
                        && request.path("reportId").canConvertToInt()
                        && request.path("reportId").intValue() == 8656
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "LOCALIZADOR_EXPLICIT_REQUEST");
        UUID.fromString(text(request, "attemptId"));
        final String startText = text(request, "periodStart");
        final String endText = text(request, "periodEnd");
        require(
                startText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && endText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "LOCALIZADOR_PERIOD_FORMAT");
        final LocalDate start = date(startText);
        final LocalDate end = date(endText);
        require(
                !end.isBefore(start) && end.isBefore(start.plusDays(7)),
                "LOCALIZADOR_PERIOD_BOUND");
        final var filters = request.required("filters");
        require(filters.isObject(), "LOCALIZADOR_FILTER_OBJECT");
        final var actual = new HashSet<String>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FILTERS), "LOCALIZADOR_ALL_EIGHT_UI_FILTERS");
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        entry.getValue().isTextual()
                                                && entry.getValue().textValue().length() <= 1024
                                                && (SERVICE_AT_FILTER.equals(entry.getKey())
                                                        || entry.getValue().textValue().isEmpty()),
                                        "LOCALIZADOR_AUXILIARY_FILTER_REQUIRES_QUALIFICATION"));
        try {
            require(
                    (start + " - " + end)
                            .equals(
                                    IndependentExportRequest.canonicalPeriod(
                                            filters.path(SERVICE_AT_FILTER).textValue())),
                    "LOCALIZADOR_SERVICE_AT_PERIOD_FILTER_BINDING");
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("LOCALIZADOR_PERIOD_FILTER_FORMAT", invalid);
        }
    }

    public static ObjectNode plan(final JsonNode request) {
        validate(request);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_PLAN_V1")
                        .put("profile", PROFILE)
                        .put("attemptId", text(request, "attemptId"))
                        .put("entity", "LOCALIZADOR")
                        .put("reportId", 8656)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("zone", text(request, "zone"))
                        .put("transport", "GET_WITH_QUERY")
                        .put("method", "GET")
                        .put("infoPath", "/api/analytics/reports/8656/info")
                        .put("dataPath", DATA_PATH)
                        .put("infoContractResource", LocalizadorObservedWire26.RESOURCE)
                        .put("referenceRoute", "INDEPENDENT_UI_EXPORT_XLSX")
                        .put("localPageCeiling", MAX_PAGE)
                        .put("localPerCeiling", MAX_PER)
                        .put("paginationQualified", false)
                        .put("contentQualified", false)
                        .put("orderByIncluded", false)
                        .put("sourceRootIdentityQualified", false)
                        .put("filterScopePolicy", FILTER_SCOPE_POLICY)
                        .put("updatedAtFilter", "")
                        .put("updatedAtScopeEnforced", false)
                        .put("nativeConfigurationObserved", false)
                        .put("dateFilterSemanticsObserved", false)
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("sourceQueryAuthorized", false)
                        .put("fileExportQualified", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("mainResumeAllowed", false)
                        .put("runtimeAdopted", false)
                        .put("snapshotIdentityProven", false);
        result.set("filters", request.required("filters").deepCopy());
        return result;
    }

    /** Serialization only. This URI does not grant admission or source-query authority. */
    public static URI query(
            final URI endpoint, final JsonNode request, final int page, final int per) {
        validate(request);
        require(
                page > 0 && page <= MAX_PAGE && per > 0 && per <= MAX_PER,
                "LOCALIZADOR_PAGE_BOUND");
        require(
                endpoint != null
                        && "https".equals(endpoint.getScheme())
                        && endpoint.getHost() != null
                        && (endpoint.getPort() == -1 || endpoint.getPort() == 443)
                        && DATA_PATH.equals(endpoint.getRawPath())
                        && endpoint.getRawQuery() == null
                        && endpoint.getRawFragment() == null
                        && endpoint.getRawUserInfo() == null,
                "LOCALIZADOR_EXPLICIT_DATA_ENDPOINT");
        // Root selected the current UI service-date scope; all seven other controls remain
        // explicitly empty.
        final var filters = new LinkedHashMap<SearchPath, DataExportFilterValue>();
        final var interval =
                new BusinessDateRange(
                        LocalDate.parse(text(request, "periodStart")),
                        LocalDate.parse(text(request, "periodEnd")));
        filters.put(new SearchPath("freights", "service_at"), interval);
        return new DataExportQueryEncoder()
                .appendQuery(
                        endpoint, filters, page, per, List.of(), ZoneId.of(text(request, "zone")));
    }

    static void verifyServiceAt(final JsonNode row, final JsonNode request) {
        final JsonNode value = row.required("service_at");
        require(
                value.isTextual()
                        && value.textValue()
                                .matches(
                                        "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
                                                + "(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})"),
                "LOCALIZADOR_SERVICE_AT_EXACT_OFFSET_REQUIRED");
        final LocalDate day;
        try {
            day = java.time.OffsetDateTime.parse(value.textValue()).toLocalDate();
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("LOCALIZADOR_SERVICE_AT_INVALID_OFFSET", invalid);
        }
        require(
                !day.isBefore(date(text(request, "periodStart")))
                        && !day.isAfter(date(text(request, "periodEnd"))),
                "LOCALIZADOR_SERVICE_AT_OUTSIDE_OFFSET_CIVIL_SCOPE");
    }

    private static LocalDate date(final String value) {
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("LOCALIZADOR_PERIOD_FORMAT", invalid);
        }
    }
}

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportQueryEncoder;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Planning only for the observed 10633 GET contract; no content or collection qualification. */
public final class InventarioIndependentRequest {
    public static final String PROFILE = "INVENTARIO_10633_NATIVE26_V1";
    public static final String STARTED_AT_FILTER = "check_in_orders[started_at]";
    public static final Set<String> FILTERS =
            Set.of(
                    STARTED_AT_FILTER,
                    "check_in_orders[check_in_type]",
                    "check_in_orders[corporation_id]",
                    "check_in_orders[sequence_code]",
                    "check_in_orders[updated_at]",
                    "scopes[from_invoice_number]",
                    "scopes[from_volume_code]");
    private static final String DATA_PATH = "/api/analytics/reports/10633/data";
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

    private InventarioIndependentRequest() {}

    public static void validate(final JsonNode request) {
        require(request != null && request.isObject(), "INVENTARIO_REQUEST_OBJECT");
        final var requestFields = new HashSet<String>();
        request.fieldNames().forEachRemaining(requestFields::add);
        require(requestFields.equals(REQUEST_FIELDS), "INVENTARIO_EXPLICIT_REQUEST_FIELDS");
        require(
                "INVENTARIO_INDEPENDENT_REQUEST_V1".equals(text(request, "schema"))
                        && PROFILE.equals(text(request, "profile"))
                        && "INVENTARIO".equals(text(request, "entity"))
                        && request.path("reportId").isIntegralNumber()
                        && request.path("reportId").canConvertToInt()
                        && request.path("reportId").intValue() == 10633
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "INVENTARIO_EXPLICIT_REQUEST");
        UUID.fromString(text(request, "attemptId"));
        final String startText = text(request, "periodStart");
        final String endText = text(request, "periodEnd");
        require(
                startText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && endText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "INVENTARIO_PERIOD_FORMAT");
        final LocalDate start = date(startText);
        final LocalDate end = date(endText);
        require(!end.isBefore(start) && end.isBefore(start.plusDays(7)), "INVENTARIO_PERIOD_BOUND");
        final var filters = request.required("filters");
        require(filters.isObject(), "INVENTARIO_FILTER_OBJECT");
        final var actual = new HashSet<String>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FILTERS), "INVENTARIO_ALL_SEVEN_INFO_FILTERS");
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        entry.getValue().isTextual()
                                                && entry.getValue().textValue().length() <= 1024
                                                && (STARTED_AT_FILTER.equals(entry.getKey())
                                                        || entry.getValue().textValue().isEmpty()),
                                        "INVENTARIO_AUXILIARY_FILTER_REQUIRES_QUALIFICATION"));
        try {
            require(
                    (start + " - " + end)
                            .equals(
                                    IndependentExportRequest.canonicalPeriod(
                                            filters.path(STARTED_AT_FILTER).textValue())),
                    "INVENTARIO_PERIOD_FILTER_BINDING");
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("INVENTARIO_PERIOD_FILTER_FORMAT", invalid);
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
                        .put("entity", "INVENTARIO")
                        .put("reportId", 10633)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("zone", text(request, "zone"))
                        .put("transport", "GET_WITH_QUERY")
                        .put("method", "GET")
                        .put("infoPath", "/api/analytics/reports/10633/info")
                        .put("dataPath", DATA_PATH)
                        .put(
                                "infoContractResource",
                                "contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json")
                        .put("referenceRoute", "INDEPENDENT_UI_EXPORT_XLSX")
                        .put("localPageCeiling", MAX_PAGE)
                        .put("localPerCeiling", MAX_PER)
                        .put("paginationQualified", false)
                        .put("contentQualified", false)
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
        require(page > 0 && page <= MAX_PAGE && per > 0 && per <= MAX_PER, "INVENTARIO_PAGE_BOUND");
        require(
                endpoint != null
                        && "https".equals(endpoint.getScheme())
                        && endpoint.getHost() != null
                        && DATA_PATH.equals(endpoint.getRawPath())
                        && endpoint.getRawQuery() == null
                        && endpoint.getRawFragment() == null
                        && endpoint.getRawUserInfo() == null,
                "INVENTARIO_EXPLICIT_DATA_ENDPOINT");
        // Empty controls stay in the plan; only the qualified nonempty started-at filter is
        // serialized.
        return new DataExportQueryEncoder()
                .appendQuery(
                        endpoint,
                        Map.of(
                                new SearchPath("check_in_orders", "started_at"),
                                new BusinessDateRange(
                                        LocalDate.parse(text(request, "periodStart")),
                                        LocalDate.parse(text(request, "periodEnd")))),
                        page,
                        per,
                        List.of(),
                        ZoneId.of(text(request, "zone")));
    }

    private static LocalDate date(final String value) {
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("INVENTARIO_PERIOD_FORMAT", invalid);
        }
    }
}

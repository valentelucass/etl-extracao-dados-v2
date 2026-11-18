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

/** Planning only for the observed 6392 GET contract; no content or collection qualification. */
public final class SinistrosIndependentRequest {
    public static final String PROFILE = "SINISTROS_6392_NATIVE44_V1";
    public static final String OPENING_AT_DATE_FILTER = "insurance_claims[opening_at_date]";
    public static final Set<String> FILTERS =
            Set.of(
                    OPENING_AT_DATE_FILTER,
                    "insurance_claims[status]",
                    "insurance_claims[corporation_id]",
                    "scopes[by_freight_payer_id]",
                    "scopes[by_invoice_number]",
                    "scopes[by_freight_document_number]");
    private static final String DATA_PATH = "/api/analytics/reports/6392/data";
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

    private SinistrosIndependentRequest() {}

    public static void validate(final JsonNode request) {
        require(request != null && request.isObject(), "SINISTROS_REQUEST_OBJECT");
        final var requestFields = new HashSet<String>();
        request.fieldNames().forEachRemaining(requestFields::add);
        require(requestFields.equals(REQUEST_FIELDS), "SINISTROS_EXPLICIT_REQUEST_FIELDS");
        require(
                "SINISTROS_INDEPENDENT_REQUEST_V1".equals(text(request, "schema"))
                        && PROFILE.equals(text(request, "profile"))
                        && "SINISTROS".equals(text(request, "entity"))
                        && request.path("reportId").isIntegralNumber()
                        && request.path("reportId").canConvertToInt()
                        && request.path("reportId").intValue() == 6392
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "SINISTROS_EXPLICIT_REQUEST");
        UUID.fromString(text(request, "attemptId"));
        final String startText = text(request, "periodStart");
        final String endText = text(request, "periodEnd");
        require(
                startText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && endText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "SINISTROS_PERIOD_FORMAT");
        final LocalDate start = date(startText);
        final LocalDate end = date(endText);
        require(!end.isBefore(start) && end.isBefore(start.plusDays(7)), "SINISTROS_PERIOD_BOUND");
        final var filters = request.required("filters");
        require(filters.isObject(), "SINISTROS_FILTER_OBJECT");
        final var actual = new HashSet<String>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FILTERS), "SINISTROS_ALL_SIX_INFO_FILTERS");
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        entry.getValue().isTextual()
                                                && entry.getValue().textValue().length() <= 1024
                                                && (OPENING_AT_DATE_FILTER.equals(entry.getKey())
                                                        || entry.getValue().textValue().isEmpty()),
                                        "SINISTROS_AUXILIARY_FILTER_REQUIRES_QUALIFICATION"));
        try {
            require(
                    (start + " - " + end)
                            .equals(
                                    IndependentExportRequest.canonicalPeriod(
                                            filters.path(OPENING_AT_DATE_FILTER).textValue())),
                    "SINISTROS_PERIOD_FILTER_BINDING");
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("SINISTROS_PERIOD_FILTER_FORMAT", invalid);
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
                        .put("entity", "SINISTROS")
                        .put("reportId", 6392)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("zone", text(request, "zone"))
                        .put("transport", "GET_WITH_QUERY")
                        .put("method", "GET")
                        .put("infoPath", "/api/analytics/reports/6392/info")
                        .put("dataPath", DATA_PATH)
                        .put(
                                "infoContractResource",
                                "contracts/dataexport/expansion-6392-observed-wire-20261025-v1.json")
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
        require(page > 0 && page <= MAX_PAGE && per > 0 && per <= MAX_PER, "SINISTROS_PAGE_BOUND");
        require(
                endpoint != null
                        && "https".equals(endpoint.getScheme())
                        && endpoint.getHost() != null
                        && DATA_PATH.equals(endpoint.getRawPath())
                        && endpoint.getRawQuery() == null
                        && endpoint.getRawFragment() == null
                        && endpoint.getRawUserInfo() == null,
                "SINISTROS_EXPLICIT_DATA_ENDPOINT");
        // Empty controls stay in the plan; only the qualified nonempty opening-date filter is
        // serialized.
        return new DataExportQueryEncoder()
                .appendQuery(
                        endpoint,
                        Map.of(
                                new SearchPath("insurance_claims", "opening_at_date"),
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
            throw new IllegalArgumentException("SINISTROS_PERIOD_FORMAT", invalid);
        }
    }
}

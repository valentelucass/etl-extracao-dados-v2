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
 * Explicit 8636 comparison scope; serialization does not grant Source or establish Native
 * presentation.
 */
public final class ContasPagarIndependentRequest {
    public static final String PROFILE = "CONTAS_A_PAGAR_8636_ISSUE_DATE_NATIVE28_V1";
    public static final String FILTER_SCOPE_POLICY = "ISSUE_DATE_WITH_EXPLICIT_EMPTY_CREATED_AT_V1";
    public static final String ISSUE_DATE_FILTER = "accounting_debits[issue_date]";
    public static final String CREATED_AT_FILTER = "accounting_debits[created_at]";
    public static final Set<String> FILTERS =
            Set.of(
                    ISSUE_DATE_FILTER,
                    CREATED_AT_FILTER,
                    "accounting_debits[accounting_planning_management_id]",
                    "accounting_debits[corporation_id]",
                    "accounting_debits[document]",
                    "accounting_debits[paid]",
                    "accounting_debits[receiver_id]",
                    "accounting_debits[type]",
                    "scopes[from_competence_period_string]");
    private static final String DATA_PATH = "/api/analytics/reports/8636/data";
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

    private ContasPagarIndependentRequest() {}

    public static void validate(final JsonNode request) {
        require(request != null && request.isObject(), "CONTAS_A_PAGAR_REQUEST_OBJECT");
        final var requestFields = new HashSet<String>();
        request.fieldNames().forEachRemaining(requestFields::add);
        require(requestFields.equals(REQUEST_FIELDS), "CONTAS_A_PAGAR_EXPLICIT_REQUEST_FIELDS");
        require(
                "CONTAS_A_PAGAR_INDEPENDENT_REQUEST_V1".equals(text(request, "schema"))
                        && PROFILE.equals(text(request, "profile"))
                        && "CONTAS_A_PAGAR".equals(text(request, "entity"))
                        && request.path("reportId").isIntegralNumber()
                        && request.path("reportId").canConvertToInt()
                        && request.path("reportId").intValue() == 8636
                        && "America/Sao_Paulo".equals(text(request, "zone")),
                "CONTAS_A_PAGAR_EXPLICIT_REQUEST");
        UUID.fromString(text(request, "attemptId"));
        final String startText = text(request, "periodStart");
        final String endText = text(request, "periodEnd");
        require(
                startText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && endText.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "CONTAS_A_PAGAR_PERIOD_FORMAT");
        final LocalDate start = date(startText);
        final LocalDate end = date(endText);
        require(
                !end.isBefore(start) && end.isBefore(start.plusDays(7)),
                "CONTAS_A_PAGAR_PERIOD_BOUND");
        final var filters = request.required("filters");
        require(filters.isObject(), "CONTAS_A_PAGAR_FILTER_OBJECT");
        final var actual = new HashSet<String>();
        filters.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FILTERS), "CONTAS_A_PAGAR_ALL_NINE_INFO_FILTERS");
        filters.fields()
                .forEachRemaining(
                        entry ->
                                require(
                                        entry.getValue().isTextual()
                                                && entry.getValue().textValue().length() <= 1024
                                                && (ISSUE_DATE_FILTER.equals(entry.getKey())
                                                        || entry.getValue().textValue().isEmpty()),
                                        "CONTAS_A_PAGAR_AUXILIARY_FILTER_REQUIRES_QUALIFICATION"));
        try {
            require(
                    (start + " - " + end)
                            .equals(
                                    IndependentExportRequest.canonicalPeriod(
                                            filters.path(ISSUE_DATE_FILTER).textValue())),
                    "CONTAS_A_PAGAR_ISSUE_DATE_PERIOD_FILTER_BINDING");
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("CONTAS_A_PAGAR_PERIOD_FILTER_FORMAT", invalid);
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
                        .put("entity", "CONTAS_A_PAGAR")
                        .put("reportId", 8636)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("zone", text(request, "zone"))
                        .put("transport", "GET_WITH_QUERY")
                        .put("method", "GET")
                        .put("infoPath", "/api/analytics/reports/8636/info")
                        .put("dataPath", DATA_PATH)
                        .put(
                                "infoContractResource",
                                "contracts/dataexport/expansion-8636-observed-wire-20261025-v1.json")
                        .put("referenceRoute", "INDEPENDENT_UI_EXPORT_XLSX")
                        .put("localPageCeiling", MAX_PAGE)
                        .put("localPerCeiling", MAX_PER)
                        .put("paginationQualified", false)
                        .put("contentQualified", false)
                        .put("orderBy", "issue_date desc")
                        .put("sourceRootIdentityQualified", false)
                        .put("filterScopePolicy", FILTER_SCOPE_POLICY)
                        .put("createdAtFilter", "")
                        .put("createdAtScopeEnforced", false)
                        .put("nativeConfigurationObserved", false)
                        .put("dateFilterSemanticsObserved", false)
                        .put("issueDateScopePolicy", "ISO_LOCAL_DATE_V1")
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
                "CONTAS_A_PAGAR_PAGE_BOUND");
        require(
                endpoint != null
                        && "https".equals(endpoint.getScheme())
                        && endpoint.getHost() != null
                        && DATA_PATH.equals(endpoint.getRawPath())
                        && endpoint.getRawQuery() == null
                        && endpoint.getRawFragment() == null
                        && endpoint.getRawUserInfo() == null,
                "CONTAS_A_PAGAR_EXPLICIT_DATA_ENDPOINT");
        // This new scope selects all issued occurrences; created_at and the other controls remain
        // explicitly empty.
        final var filters = new LinkedHashMap<SearchPath, DataExportFilterValue>();
        final var interval =
                new BusinessDateRange(
                        LocalDate.parse(text(request, "periodStart")),
                        LocalDate.parse(text(request, "periodEnd")));
        filters.put(new SearchPath("accounting_debits", "issue_date"), interval);
        return new DataExportQueryEncoder()
                .appendQuery(
                        endpoint,
                        filters,
                        page,
                        per,
                        List.of("issue_date desc"),
                        ZoneId.of(text(request, "zone")));
    }

    private static LocalDate date(final String value) {
        try {
            return LocalDate.parse(value);
        } catch (final DateTimeException invalid) {
            throw new IllegalArgumentException("CONTAS_A_PAGAR_PERIOD_FORMAT", invalid);
        }
    }
}

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceNumbers;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceOrderNumbers;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline full-row comparison. Local sequence candidates are values; all physical occurrences
 * remain keyless.
 */
public final class FaturasClienteContentAdapter {
    /** Authored enumeration only; observed Native order is supplied by the pinned catalog. */
    static final List<String> ORDER =
            List.of(
                    "comments",
                    "corporation_sequence_number",
                    "courtesy",
                    "emission_type",
                    "finished_at",
                    "fit_ant_ant_name",
                    "fit_ant_discount_value",
                    "fit_ant_document",
                    "fit_ant_ils_atn_transaction_date",
                    "fit_ant_ils_due_date",
                    "fit_ant_ils_original_due_date",
                    "fit_ant_interest_value",
                    "fit_ant_issue_date",
                    "fit_ant_tat_account_number",
                    "fit_ant_tat_agency_number",
                    "fit_ant_tat_bnk_name",
                    "fit_ant_tat_bro_description",
                    "fit_ant_tat_custom_instruction",
                    "fit_ant_value",
                    "fit_crn_psn_nickname",
                    "fit_d_t_created_at",
                    "fit_diy_sae_name",
                    "fit_dyn_drt_nickname",
                    "fit_fhe_cte_issued_at",
                    "fit_fhe_cte_key",
                    "fit_fhe_cte_number",
                    "fit_fhe_cte_status",
                    "fit_fhe_cte_status_result",
                    "fit_fsn_name",
                    "fit_fte_foe_ore_description",
                    "fit_fte_has_delivery_receipt",
                    "fit_fte_invoices_order_number",
                    "fit_nse_number",
                    "fit_pyr_cor_billing_cycle",
                    "fit_pyr_cor_billing_due_in_days",
                    "fit_pyr_document",
                    "fit_pyr_name",
                    "fit_rpt_document",
                    "fit_rpt_name",
                    "fit_sdr_document",
                    "fit_sdr_name",
                    "fit_sps_slr_psn_name",
                    "id",
                    "invoices_mapping",
                    "nfse_number",
                    "payment_type",
                    "reference_number",
                    "service_at",
                    "service_type",
                    "status",
                    "total",
                    "type");

    static final Map<String, String> KINDS =
            Map.ofEntries(
                    Map.entry("comments", "text"),
                    Map.entry("corporation_sequence_number", "integralNumber"),
                    Map.entry("courtesy", "boolean"),
                    Map.entry("emission_type", "text"),
                    Map.entry("finished_at", "timestamp"),
                    Map.entry("fit_ant_ant_name", "text"),
                    Map.entry("fit_ant_discount_value", "decimalString"),
                    Map.entry("fit_ant_document", "text"),
                    Map.entry("fit_ant_ils_atn_transaction_date", "date"),
                    Map.entry("fit_ant_ils_due_date", "date"),
                    Map.entry("fit_ant_ils_original_due_date", "date"),
                    Map.entry("fit_ant_interest_value", "decimalString"),
                    Map.entry("fit_ant_issue_date", "date"),
                    Map.entry("fit_ant_tat_account_number", "text"),
                    Map.entry("fit_ant_tat_agency_number", "text"),
                    Map.entry("fit_ant_tat_bnk_name", "text"),
                    Map.entry("fit_ant_tat_bro_description", "text"),
                    Map.entry("fit_ant_tat_custom_instruction", "text"),
                    Map.entry("fit_ant_value", "decimalString"),
                    Map.entry("fit_crn_psn_nickname", "text"),
                    Map.entry("fit_d_t_created_at", "timestamp"),
                    Map.entry("fit_diy_sae_name", "text"),
                    Map.entry("fit_dyn_drt_nickname", "text"),
                    Map.entry("fit_fhe_cte_issued_at", "timestamp"),
                    Map.entry("fit_fhe_cte_key", "text"),
                    Map.entry("fit_fhe_cte_number", "integralNumber"),
                    Map.entry("fit_fhe_cte_status", "text"),
                    Map.entry("fit_fhe_cte_status_result", "text"),
                    Map.entry("fit_fsn_name", "text"),
                    Map.entry("fit_fte_foe_ore_description", "text"),
                    Map.entry("fit_fte_has_delivery_receipt", "boolean"),
                    Map.entry("fit_fte_invoices_order_number", "stringArray"),
                    Map.entry("fit_nse_number", "integralNumber"),
                    Map.entry("fit_pyr_cor_billing_cycle", "text"),
                    Map.entry("fit_pyr_cor_billing_due_in_days", "integralNumber"),
                    Map.entry("fit_pyr_document", "text"),
                    Map.entry("fit_pyr_name", "text"),
                    Map.entry("fit_rpt_document", "text"),
                    Map.entry("fit_rpt_name", "text"),
                    Map.entry("fit_sdr_document", "text"),
                    Map.entry("fit_sdr_name", "text"),
                    Map.entry("fit_sps_slr_psn_name", "text"),
                    Map.entry("id", "integralNumber"),
                    Map.entry("invoices_mapping", "stringArray"),
                    Map.entry("nfse_number", "text"),
                    Map.entry("payment_type", "text"),
                    Map.entry("reference_number", "text"),
                    Map.entry("service_at", "timestamp"),
                    Map.entry("service_type", "text"),
                    Map.entry("status", "text"),
                    Map.entry("total", "decimalString"),
                    Map.entry("type", "text"));
    static final Set<String> ENUMS =
            Set.of(
                    "emission_type",
                    "fit_fhe_cte_status",
                    "fit_fhe_cte_status_result",
                    "fit_pyr_cor_billing_cycle",
                    "payment_type",
                    "service_type",
                    "status",
                    "type");
    private static final Set<String> BOOLEANS = Set.of("courtesy", "fit_fte_has_delivery_receipt");
    private static final Set<String> ARRAYS =
            Set.of("invoices_mapping", "fit_fte_invoices_order_number");
    static final Set<String> TOTAL_FIELDS =
            Set.of("fit_ant_discount_value", "fit_ant_interest_value", "fit_ant_value", "total");
    private static final String WHOLE_SECOND = "SOURCE_OFFSET_CIVIL_WHOLE_SECOND_V1";
    private static final String TRUNCATE_FRACTION = "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1";

    record Raw(JsonNode value, String numericLexeme) {}

    private FaturasClienteContentAdapter() {}

    private static boolean allowedKind(final String field, final String kind) {
        return KINDS.get(field).equals(kind)
                || ("text".equals(KINDS.get(field))
                        && ENUMS.contains(field)
                        && "enum".equals(kind));
    }

    private static void verifyBooleanLabels(final JsonNode labels) {
        require(labels.isObject(), "FATURAS_CLIENTE_EXPLICIT_BOOLEAN_LABEL_OBJECT");
        labels.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    BOOLEANS.contains(entry.getKey())
                                            && entry.getValue().isObject()
                                            && entry.getValue().size() == 2
                                            && entry.getValue().has("true")
                                            && entry.getValue().has("false"),
                                    "FATURAS_CLIENTE_BOOLEAN_LABEL_FIELD_SCOPE");
                            for (final var label : entry.getValue()) {
                                require(
                                        label.isTextual()
                                                && !label.textValue().isEmpty()
                                                && label.textValue().length() <= 256,
                                        "FATURAS_CLIENTE_BOOLEAN_LABEL_BOUND");
                            }
                            require(
                                    !entry.getValue()
                                            .get("true")
                                            .equals(entry.getValue().get("false")),
                                    "FATURAS_CLIENTE_BOOLEAN_LABELS_DISTINCT");
                        });
    }

    public static JsonNode catalog(
            final ExportValidationSupport.Pin pin, final ExportValidationSupport.Budget budget)
            throws IOException {
        final var catalog = pin.json(65536, budget);
        verifyCatalog(catalog);
        return catalog.deepCopy();
    }

    static void verifyCatalog(final JsonNode catalog) {
        require(
                catalog != null
                        && catalog.isObject()
                        && "FATURAS_CLIENTE_NATIVE52_CANDIDATE_CATALOG_V1"
                                .equals(text(catalog, "schema"))
                        && FaturasClienteIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "FATURAS_POR_CLIENTE".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 4924)
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 52
                        && catalog.path("totalColumnCount").isIntegralNumber()
                        && catalog.path("totalColumnCount").canConvertToInt()
                        && catalog.path("totalColumnCount").intValue() >= 0
                        && catalog.path("totalColumnCount").intValue() <= 52
                        && Set.of("AUTHORED_SYNTHETIC", "OBSERVED_NATIVE_CANDIDATE")
                                .contains(text(catalog, "catalogNature"))
                        && catalog.path("presentationQualified").isBoolean()
                        && !catalog.get("presentationQualified").booleanValue()
                        && catalog.path("contentAccepted").isBoolean()
                        && !catalog.get("contentAccepted").booleanValue()
                        && catalog.path("rootIdentityQualified").isBoolean()
                        && !catalog.get("rootIdentityQualified").booleanValue()
                        && catalog.path("keylessPhysicalOccurrences").isBoolean()
                        && catalog.get("keylessPhysicalOccurrences").booleanValue()
                        && !catalog.has("idColumnIndex"),
                "FATURAS_CLIENTE_EXPLICIT_KEYLESS52_CATALOG");
        final var fields = new HashSet<String>();
        int coordinate = 0;
        int totals = 0;
        for (final var field : catalog.required("columns")) {
            final String source = text(field, "sourceField");
            require(
                    field.isObject()
                            && exactInt(field.path("nativeColumn"), ++coordinate)
                            && fields.add(source)
                            && KINDS.containsKey(source)
                            && allowedKind(source, text(field, "kind"))
                            && !text(field, "title").isBlank()
                            && text(field, "title").length() <= 256
                            && field.path("total").isBoolean(),
                    "FATURAS_CLIENTE_EXACT52_COORDINATE_FIELD_KIND");
            verifyPolicy(field);
            if (field.get("total").booleanValue()) {
                require(
                        TOTAL_FIELDS.contains(source),
                        "FATURAS_CLIENTE_DECLARED_FINANCIAL_TOTAL_FIELDS_ONLY");
                totals++;
            }
        }
        require(
                fields.equals(KINDS.keySet())
                        && totals == catalog.path("totalColumnCount").intValue(),
                "FATURAS_CLIENTE_ALL52_FIELDS_AND_DECLARED_TOTALS");
        verifyLabels(catalog.path("enumLabels"));
        verifyEnumAbsentCodes(catalog);
        verifyBooleanLabels(catalog.path("booleanLabels"));
    }

    private static void verifyEnumAbsentCodes(final JsonNode catalog) {
        if (!catalog.has("enumAbsentCodes")) {
            return;
        }
        final var absent = catalog.get("enumAbsentCodes");
        require(absent.isObject(), "FATURAS_CLIENTE_ENUM_ABSENT_OBJECT");
        absent.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    ENUMS.contains(entry.getKey())
                                            && entry.getValue().isArray()
                                            && entry.getValue().size() <= 128,
                                    "FATURAS_CLIENTE_ENUM_ABSENT_FIELD_SCOPE");
                            final var codes = new HashSet<String>();
                            for (final var code : entry.getValue()) {
                                require(
                                        code.isTextual()
                                                && !code.textValue().isEmpty()
                                                && code.textValue().length() <= 128
                                                && codes.add(code.textValue())
                                                && !catalog.path("enumLabels")
                                                        .path(entry.getKey())
                                                        .has(code.textValue()),
                                        "FATURAS_CLIENTE_ENUM_ABSENT_CODE_BOUND_DISJOINT");
                            }
                        });
    }

    private static void verifyLabels(final JsonNode labels) {
        require(labels.isObject(), "FATURAS_CLIENTE_EXPLICIT_ENUM_LABEL_OBJECT");
        labels.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    ENUMS.contains(entry.getKey())
                                            && entry.getValue().isObject()
                                            && entry.getValue().size() <= 128,
                                    "FATURAS_CLIENTE_ENUM_LABEL_FIELD_SCOPE");
                            entry.getValue()
                                    .fields()
                                    .forEachRemaining(
                                            label ->
                                                    require(
                                                            label.getKey().length() <= 128
                                                                    && label.getValue().isTextual()
                                                                    && !label.getValue()
                                                                            .textValue()
                                                                            .isEmpty()
                                                                    && label.getValue()
                                                                                    .textValue()
                                                                                    .length()
                                                                            <= 256,
                                                            "FATURAS_CLIENTE_ENUM_LABEL_BOUND"));
                        });
    }

    private static boolean exactInt(final JsonNode value, final int expected) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() == expected;
    }

    private static void verifyPolicy(final JsonNode field) {
        final String kind = text(field, "kind");
        require(
                Set.of("STRICT", "NULL_NATIVE_ABSENT", "NULL_NATIVE_BLANK")
                                .contains(text(field, "nullPresentation"))
                        && Set.of("STRICT", "EMPTY_NATIVE_ABSENT")
                                .contains(text(field, "emptyStringPresentation")),
                "FATURAS_CLIENTE_EXPLICIT_ABSENCE_POLICIES");
        require(
                !field.has("enumAbsentCodes")
                        && !field.has("textEncoding")
                        && (!field.has("booleanEncoding") || "boolean".equals(kind))
                        && !field.has("sourceNumberEncoding"),
                "FATURAS_CLIENTE_NO_UNOBSERVED_INHERITED_ENCODING");
        final boolean numeric =
                Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind);
        if (numeric) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").startsWith("XLSX:1900:")
                            && text(field, "nativeCarrier").length() <= 256,
                    "FATURAS_CLIENTE_EXPLICIT_NATIVE_CARRIER");
        } else {
            require(
                    !field.has("nativeCarrier")
                            && !"NULL_NATIVE_BLANK".equals(text(field, "nullPresentation")),
                    "FATURAS_CLIENTE_TEXT_POLICY_NO_NATIVE_NUMERIC_CARRIER");
        }
        if ("decimalString".equals(kind)) {
            require(
                    "DIRECT_BINARY64_NEAREST_EVEN_V1".equals(text(field, "nativeNumberEncoding")),
                    "FATURAS_CLIENTE_EXPLICIT_DIRECT_BINARY64");
        } else {
            require(
                    !field.has("nativeNumberEncoding"),
                    "FATURAS_CLIENTE_NO_UNUSED_NUMBER_ENCODING");
        }
        if ("timestamp".equals(kind)) {
            require(
                    Set.of(WHOLE_SECOND, TRUNCATE_FRACTION)
                                    .contains(text(field, "sourceTimestampEncoding"))
                            && "NATIVE_DATE_TIME_NEAREST_SECOND_V1"
                                    .equals(text(field, "nativeTimestampEncoding")),
                    "FATURAS_CLIENTE_EXPLICIT_TIMESTAMP_ENCODING");
        } else {
            require(
                    !field.has("sourceTimestampEncoding") && !field.has("nativeTimestampEncoding"),
                    "FATURAS_CLIENTE_NO_UNUSED_TIMESTAMP_ENCODING");
        }
        if ("stringArray".equals(kind)) {
            require(
                    ARRAYS.contains(text(field, "sourceField"))
                            && (!field.has("arrayTextEncoding")
                                    || field.path("arrayTextEncoding").isTextual())
                            && Set.of("JOIN_LITERAL_V1", "POSTGRES_TEXT_ARRAY_LITERAL_V1")
                                    .contains(
                                            field.path("arrayTextEncoding")
                                                    .asText("JOIN_LITERAL_V1"))
                            && (!"POSTGRES_TEXT_ARRAY_LITERAL_V1"
                                            .equals(field.path("arrayTextEncoding").asText())
                                    || ("fit_fte_invoices_order_number"
                                                    .equals(text(field, "sourceField"))
                                            && ",".equals(text(field, "arraySeparator"))
                                            && "STRICT"
                                                    .equals(text(field, "emptyArrayPresentation"))))
                            && field.path("arraySeparator").isTextual()
                            && field.get("arraySeparator").textValue().length() <= 16
                            && Set.of("STRICT", "EMPTY_ARRAY_NATIVE_ABSENT")
                                    .contains(text(field, "emptyArrayPresentation"))
                            && "STRICT".equals(text(field, "emptyStringPresentation")),
                    "FATURAS_CLIENTE_EXPLICIT_NF_ARRAY_POLICY");
        } else {
            require(
                    !field.has("arraySeparator")
                            && !field.has("emptyArrayPresentation")
                            && !field.has("arrayTextEncoding"),
                    "FATURAS_CLIENTE_ARRAY_POLICY_ONLY_FOR_NF");
        }
        require(
                "boolean".equals(kind)
                        ? Set.of("NATIVE_BOOLEAN_V1", "NATIVE_TEXT_LABELS_V1")
                                .contains(text(field, "booleanEncoding"))
                        : !field.has("booleanEncoding"),
                "FATURAS_CLIENTE_EXPLICIT_BOOLEAN_ENCODING");
        if ("service_at".equals(text(field, "sourceField"))) {
            require(
                    "STRICT".equals(text(field, "nullPresentation"))
                            && "STRICT".equals(text(field, "emptyStringPresentation")),
                    "FATURAS_CLIENTE_SCOPE_DATES_STRICT");
        }
    }

    public static ObjectNode compare(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Pin catalogPin,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return compareWithPostcheckProbe(request, source, workbook, catalogPin, budget, () -> {});
    }

    static ObjectNode compareWithPostcheckProbe(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Pin catalogPin,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        final var result =
                compareWithCatalog(
                        request,
                        source,
                        workbook,
                        budget,
                        catalog(catalogPin, budget),
                        beforePostcheck);
        catalogPin.read(65536, budget);
        result.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
        result.put("catalogSha256", catalogPin.sha256()).put("catalogPinnedExplicitly", true);
        return result;
    }

    static ObjectNode compareWithCatalog(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget,
            final JsonNode catalog,
            final Runnable beforePostcheck)
            throws Exception {
        FaturasClienteIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        verifyCatalog(catalog);
        final JsonNode fields = catalog.required("columns");
        final List<ExactExportTable.Column> columns = new ArrayList<>();
        final List<ExactExportTable.Column> rawColumns = new ArrayList<>();
        for (final var field : fields) {
            final int index = field.get("nativeColumn").intValue();
            final boolean total = field.get("total").booleanValue();
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "title"),
                            index,
                            index,
                            total,
                            total
                                    ? ("integralNumber".equals(text(field, "kind"))
                                            ? "INTEGER_EXACT"
                                            : "BINARY64_UNITS_129")
                                    : "NONE"));
            rawColumns.add(new ExactExportTable.Column(text(field, "title"), index, index, false));
        }
        final byte[] sourceBytes = source.read(8388608, budget);
        final var nativeRows =
                BoundedExportWorkbook.readFaturasClienteRows(
                        workbook.read(8388608, budget), columns, budget);
        require(
                "1900".equals(nativeRows.epoch()) && !nativeRows.rows().isEmpty(),
                "FATURAS_CLIENTE_NATIVE_ROWS_EPOCH1900");
        final var left = new KeylessExactExportTable(columns, budget);
        final var right = new KeylessExactExportTable(columns, budget);
        final var leftOriginal = new KeylessExactExportTable(rawColumns, budget);
        final var rightOriginal = new KeylessExactExportTable(rawColumns, budget);
        readSource(sourceBytes, admitted, fields, catalog, left, leftOriginal, budget);
        for (final var row : nativeRows.rows()) {
            require(row.cells().size() == 52, "FATURAS_CLIENTE_ALL52_NATIVE_CELLS");
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            for (int index = 0; index < fields.size(); index++) {
                final var field = fields.get(index);
                final var value = nativeValue(field, row.cells().get(index), catalog);
                verifyPeriodValue(field, value, admitted);
                values.add(value);
            }
            right.add(values);
            rightOriginal.add(row.cells());
        }
        for (final var table : List.of(left, right, leftOriginal, rightOriginal)) {
            table.seal();
        }
        final var compared = left.compare(right);
        final boolean equal =
                compared.apiRows() > 0
                        && compared.contentEqual()
                        && compared.apiRows() == compared.uiRows()
                        && compared.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && compared.apiOnlyOccurrences() == 0
                        && compared.uiOnlyOccurrences() == 0;
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("columns", 52)
                        .put("catalogNature", text(catalog, "catalogNature"))
                        .put("catalogBindingPoliciesQualified", false)
                        .put(
                                "status",
                                equal
                                        ? "CONTENT_EQUAL_UNDER_CANDIDATE_PRESENTATION"
                                        : "PENDING_DIFFERENCE_INVESTIGATION")
                        .put("totalColumns", catalog.path("totalColumnCount").intValue())
                        .put("sourceRows", compared.apiRows())
                        .put("uiRows", compared.uiRows())
                        .put("contentEqualUnderCandidatePresentation", equal)
                        .put("allOccurrencesCompared", true)
                        .put("keylessPhysicalOccurrences", true)
                        .put("rootIdentityQualified", false)
                        .put("idOrMinutaCandidateUsedAsIdentity", false)
                        .put("deduplicationPerformed", false)
                        .put("serviceAtVerifiedForEveryRowBothSides", true)
                        .put("updatedAtScopeEnforced", false)
                        .put("filterScopePolicy", "SERVICE_AT_WITH_EXPLICIT_EMPTY_UPDATED_AT_V1")
                        .put("updatedAtFilter", "")
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("floatArithmeticUsed", false)
                        .put("epsilonUsed", false)
                        .put("diagnosticOnly", true)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("productionAuthorized", false)
                        .put("snapshotIdentityProven", false)
                        .put("sourceSha256", source.sha256())
                        .put("workbookSha256", workbook.sha256());
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(compared));
        result.putObject("fullValueProjection").set("api", left.fullValueProjection());
        ((ObjectNode) result.get("fullValueProjection")).set("ui", right.fullValueProjection());
        result.putObject("originalValueProjection").set("api", leftOriginal.fullValueProjection());
        ((ObjectNode) result.get("originalValueProjection"))
                .set("ui", rightOriginal.fullValueProjection());
        final var diagnostics = result.putArray("columnDiagnostics");
        for (final var field : fields) {
            diagnostics
                    .addObject()
                    .put("nativeColumn", field.path("nativeColumn").intValue())
                    .put("sourceField", text(field, "sourceField"))
                    .put("kind", text(field, "kind"))
                    .put("total", field.path("total").booleanValue())
                    .put("nullPresentation", text(field, "nullPresentation"))
                    .put("emptyStringPresentation", text(field, "emptyStringPresentation"))
                    .put(
                            "emptyArrayPresentation",
                            field.path("emptyArrayPresentation").asText("NONE"))
                    .put("arraySeparator", field.path("arraySeparator").asText("NONE"))
                    .put(
                            "arrayTextEncoding",
                            field.path("arrayTextEncoding").asText("JOIN_LITERAL_V1"))
                    .put(
                            "sourceTimestampEncoding",
                            field.path("sourceTimestampEncoding").asText("NONE"))
                    .put(
                            "nativeTimestampEncoding",
                            field.path("nativeTimestampEncoding").asText("NONE"))
                    .put("nativeNumberEncoding", field.path("nativeNumberEncoding").asText("NONE"));
        }
        result.put("arrayPresentationMayBeNonInjective", true);
        beforePostcheck.run();
        source.read(8388608, budget);
        workbook.read(8388608, budget);
        verifyCatalog(catalog);
        result.put("pinsVerifiedBeforeAndAfter", true);
        return result;
    }

    private static void readSource(
            final byte[] bytes,
            final JsonNode request,
            final JsonNode fields,
            final JsonNode catalog,
            final KeylessExactExportTable values,
            final KeylessExactExportTable original,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                bytes.length > 0 && bytes[bytes.length - 1] == '\n',
                "FATURAS_CLIENTE_COMPLETE_JSONL_FINAL_LF");
        int rows = 0;
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(bytes),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(
                        !line.isBlank() && line.length() <= 1049600 && ++rows <= 12000,
                        "FATURAS_CLIENTE_SOURCE_LINE_BOUND");
                final var record = record(line);
                require(
                        record.keySet().equals(KINDS.keySet()),
                        "FATURAS_CLIENTE_SOURCE_EXACT52_FIELDS");
                final List<ExactExportTable.Atom> presented = new ArrayList<>();
                final List<ExactExportTable.Atom> raw = new ArrayList<>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    raw.add(original(value));
                    final var key = sourceValue(field, value, catalog);
                    verifyPeriodValue(field, key, request);
                    presented.add(key);
                }
                original.add(raw);
                values.add(presented);
            }
        }
        require(rows > 0, "FATURAS_CLIENTE_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(
                    parser.nextToken() == JsonToken.START_OBJECT,
                    "FATURAS_CLIENTE_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "FATURAS_CLIENTE_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(
                        names.add(name) && parser.nextToken() != null,
                        "FATURAS_CLIENTE_ENVELOPE_DUPLICATE_OR_MISSING");
                if ("record".equals(name)) {
                    require(
                            parser.currentToken() == JsonToken.START_OBJECT,
                            "FATURAS_CLIENTE_RECORD_OBJECT");
                    result = new HashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        require(
                                parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 52,
                                "FATURAS_CLIENTE_RECORD_FIELD_BOUND");
                        final String field = parser.currentName();
                        final var token = parser.nextToken();
                        require(
                                token != null
                                        && (token.isScalarValue()
                                                || (ARRAYS.contains(field)
                                                        && token == JsonToken.START_ARRAY)),
                                "FATURAS_CLIENTE_SCALAR_OR_INVOICES_ARRAY_VALUE");
                        final String numeric = token.isNumeric() ? parser.getText() : null;
                        final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
                        require(
                                value != null
                                        && result.putIfAbsent(field, new Raw(value, numeric))
                                                == null,
                                "FATURAS_CLIENTE_RECORD_DUPLICATE");
                    }
                } else if ("entity".equals(name)) {
                    entity = ExportValidationSupport.JSON.readTree(parser);
                } else if ("templateId".equals(name)) {
                    template = ExportValidationSupport.JSON.readTree(parser);
                } else {
                    throw new IllegalArgumentException("FATURAS_CLIENTE_ENVELOPE_UNKNOWN_FIELD");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "FATURAS_POR_CLIENTE".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 4924),
                    "FATURAS_CLIENTE_ENVELOPE_BINDING");
        }
        return result;
    }

    static ExactExportTable.Atom sourceValue(
            final JsonNode field, final Raw raw, final JsonNode catalog) {
        verifyPolicy(field);
        final var value = raw.value();
        if (value.isNull()) {
            return new ExactExportTable.Atom(
                    "NULL_NATIVE_ABSENT".equals(text(field, "nullPresentation"))
                            ? "ABSENT"
                            : "NULL",
                    "");
        }
        if (value.isTextual()
                && value.textValue().isEmpty()
                && "EMPTY_NATIVE_ABSENT".equals(text(field, "emptyStringPresentation"))) {
            return new ExactExportTable.Atom("ABSENT", "");
        }
        return switch (text(field, "kind")) {
            case "integralNumber" -> {
                require(
                        value.isNumber() && raw.numericLexeme() != null,
                        "FATURAS_CLIENTE_SOURCE_INTEGER_WIRE_TYPE");
                yield integer(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "FATURAS_CLIENTE_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(value.textValue());
            }
            case "date" -> {
                require(value.isTextual(), "FATURAS_CLIENTE_SOURCE_DATE_TYPE");
                yield literal(QualifiedColetasModel.sourceCivil(value.textValue()));
            }
            case "timestamp" -> {
                require(value.isTextual(), "FATURAS_CLIENTE_SOURCE_TIMESTAMP_TYPE");
                yield literal(
                        sourceTimestamp(value.textValue(), text(field, "sourceTimestampEncoding")));
            }
            case "boolean" -> {
                require(value.isBoolean(), "FATURAS_CLIENTE_SOURCE_BOOLEAN_TYPE");
                yield "NATIVE_BOOLEAN_V1".equals(text(field, "booleanEncoding"))
                        ? ExactExportTable.Atom.json(value)
                        : literal(
                                label(
                                        catalog.path("booleanLabels"),
                                        text(field, "sourceField"),
                                        value.booleanValue() ? "true" : "false"));
            }
            case "enum" -> {
                require(value.isTextual(), "FATURAS_CLIENTE_SOURCE_ENUM_TYPE");
                if (enumCodeIsAbsent(catalog, text(field, "sourceField"), value.textValue())) {
                    yield new ExactExportTable.Atom("ABSENT", "");
                }
                yield literal(
                        label(
                                catalog.path("enumLabels"),
                                text(field, "sourceField"),
                                value.textValue()));
            }
            case "text" -> {
                require(value.isTextual(), "FATURAS_CLIENTE_SOURCE_TEXT_TYPE");
                yield literal(value.textValue());
            }
            case "stringArray" -> {
                require(
                        value.isArray()
                                && value.size()
                                        <= ("invoices_mapping".equals(text(field, "sourceField"))
                                                ? FaturaInvoiceNumbers.MAXIMUM_ITEMS
                                                : FaturaInvoiceOrderNumbers.MAXIMUM_ITEMS)
                                && value.toString().length() <= 4000,
                        "FATURAS_CLIENTE_NF_ARRAY_TYPE_BOUND");
                if (value.isEmpty()
                        && "EMPTY_ARRAY_NATIVE_ABSENT"
                                .equals(text(field, "emptyArrayPresentation"))) {
                    yield new ExactExportTable.Atom("ABSENT", "");
                }
                final var items = new ArrayList<String>();
                for (final var item : value) {
                    require(
                            item.isTextual() && item.textValue().length() <= 256,
                            "FATURAS_CLIENTE_NF_ARRAY_STRING_BOUND");
                    items.add(item.textValue());
                }
                yield literal(
                        "POSTGRES_TEXT_ARRAY_LITERAL_V1"
                                        .equals(field.path("arrayTextEncoding").asText())
                                ? postgresTextArray(items)
                                : String.join(field.get("arraySeparator").textValue(), items));
            }
            default -> throw new IllegalArgumentException("FATURAS_CLIENTE_SOURCE_KIND");
        };
    }

    /**
     * One-dimensional text-array output, preserving every ordered string item.
     * https://www.postgresql.org/docs/current/arrays.html#ARRAYS-IO
     */
    private static String postgresTextArray(final List<String> values) {
        final var encoded = new ArrayList<String>();
        for (final String value : values) {
            boolean quoted = value.isEmpty() || "NULL".equalsIgnoreCase(value);
            for (int index = 0; index < value.length(); index++) {
                final char ch = value.charAt(index);
                quoted |= "{},\"\\ \t\n\r\u000B\f".indexOf(ch) >= 0;
            }
            final String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
            encoded.add(quoted ? "\"" + escaped + "\"" : value);
        }
        return "{" + String.join(",", encoded) + "}";
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicy(field);
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "FATURAS_CLIENTE_NATIVE_ABSENT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "FATURAS_CLIENTE_NATIVE_BLANK_CARRIER");
            return "NULL_NATIVE_BLANK".equals(text(field, "nullPresentation"))
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        final String kind = text(field, "kind");
        if (Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind)) {
            require(
                    "NUMBER".equals(atom.type())
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "FATURAS_CLIENTE_NATIVE_NUMERIC_TYPE_CARRIER");
            return switch (kind) {
                case "integralNumber" -> integer(atom.value());
                case "date" -> literal(QualifiedColetasModel.nativeCivil(atom.value()));
                case "timestamp" -> literal(QualifiedColetasModel.nativeTimestamp(atom.value()));
                default -> encoded(atom.value());
            };
        }
        if ("boolean".equals(kind) && "NATIVE_BOOLEAN_V1".equals(text(field, "booleanEncoding"))) {
            require(
                    "BOOLEAN".equals(atom.type()) && atom.carrier().isEmpty(),
                    "FATURAS_CLIENTE_NATIVE_BOOLEAN_TYPE_CARRIER");
            return new ExactExportTable.Atom("BOOLEAN", atom.value());
        }
        require(
                "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                "FATURAS_CLIENTE_NATIVE_TEXT_TYPE_CARRIER");
        if ("enum".equals(kind) || "boolean".equals(kind)) {
            boolean observed = false;
            for (final var value :
                    catalog.path("boolean".equals(kind) ? "booleanLabels" : "enumLabels")
                            .path(text(field, "sourceField"))) {
                observed |= value.isTextual() && atom.value().equals(value.textValue());
            }
            require(observed, "FATURAS_CLIENTE_NATIVE_ENUM_LABEL_UNOBSERVED");
        }
        return literal(atom.value());
    }

    private static String label(final JsonNode object, final String field, final String code) {
        final var value = object.path(field).path(code);
        require(value.isTextual(), "FATURAS_CLIENTE_UNOBSERVED_LABEL");
        return value.textValue();
    }

    private static boolean enumCodeIsAbsent(
            final JsonNode catalog, final String field, final String code) {
        for (final var candidate : catalog.path("enumAbsentCodes").path(field)) {
            if (candidate.isTextual() && code.equals(candidate.textValue())) {
                return true;
            }
        }
        return false;
    }

    static String sourceTimestamp(final String value) {
        return sourceTimestamp(value, WHOLE_SECOND);
    }

    static String sourceTimestamp(final String value, final String encoding) {
        require(
                Set.of(WHOLE_SECOND, TRUNCATE_FRACTION).contains(encoding),
                "FATURAS_CLIENTE_SOURCE_TIMESTAMP_ENCODING");
        require(
                value != null
                        && value.length() <= 126
                        && value.matches(
                                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})"),
                "FATURAS_CLIENTE_SOURCE_OFFSET_TIMESTAMP");
        final var parsed = OffsetDateTime.parse(value);
        require(
                !WHOLE_SECOND.equals(encoding) || parsed.getNano() == 0,
                "FATURAS_CLIENTE_SOURCE_WHOLE_SECOND_TIMESTAMP");
        // The declared presentation keeps the original civil clock; offset remains in the raw axis.
        return parsed.toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"));
    }

    private static void verifyPeriodValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode request) {
        final String source = text(field, "sourceField");
        if (!"service_at".equals(source)) {
            return;
        }
        require(
                "TEXT".equals(atom.type()) && atom.value().length() >= 10,
                "FATURAS_CLIENTE_SCOPE_VALUE_REQUIRED");
        final var day = LocalDate.parse(atom.value().substring(0, 10));
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "FATURAS_CLIENTE_SCOPE_VALUE_OUTSIDE_PERIOD");
    }

    static ExactExportTable.Atom original(final Raw value) {
        if (value.value().isArray()) {
            return new ExactExportTable.Atom("TEXT", value.value().toString(), "JSON:ARRAY");
        }
        return value.numericLexeme() == null
                ? ExactExportTable.Atom.json(value.value())
                : new ExactExportTable.Atom(
                        "NUMBER",
                        value.numericLexeme(),
                        value.value().isIntegralNumber() ? "JSON:INTEGER" : "JSON:NUMBER");
    }

    private static ExactExportTable.Atom integer(final String lexeme) {
        ExactBinary64.ratio(lexeme);
        final var number = new BigDecimal(lexeme).toBigIntegerExact();
        require(number.abs().bitLength() <= 63, "FATURAS_CLIENTE_INTEGER_VALUE_BOUND");
        return new ExactExportTable.Atom("NUMBER", number.toString());
    }

    private static ExactExportTable.Atom encoded(final String lexeme) {
        return encoded(ExactBinary64.encode(lexeme));
    }

    private static ExactExportTable.Atom encoded(final ExactBinary64.Encoding value) {
        return new ExactExportTable.Atom(
                "NUMBER",
                value.units().signum() == 0 && value.bits().startsWith("8")
                        ? "-0"
                        : value.units().toString(),
                "BINARY64:" + value.bits());
    }

    private static ExactExportTable.Atom literal(final String value) {
        return new ExactExportTable.Atom("TEXT", value);
    }
}

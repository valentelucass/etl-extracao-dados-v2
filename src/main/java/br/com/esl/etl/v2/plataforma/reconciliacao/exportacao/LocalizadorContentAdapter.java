package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

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
public final class LocalizadorContentAdapter {
    static final List<String> ORDER =
            List.of(
                    "fit_crn_psn_nickname",
                    "corporation_sequence_number",
                    "invoices_mapping",
                    "type",
                    "fit_fsn_name",
                    "service_at",
                    "fit_dpn_delivery_prediction_at",
                    "finished_at",
                    "fit_fln_cln_nickname",
                    "fit_fln_status",
                    "fit_o_n_name",
                    "fit_o_n_drt_nickname",
                    "fit_dyn_name",
                    "fit_dyn_drt_nickname",
                    "fit_pyr_nickname",
                    "fit_rpt_name",
                    "invoices_volumes",
                    "taxed_weight",
                    "invoices_value",
                    "total",
                    "service_type",
                    "fit_fte_lce_ore_description",
                    "fit_fte_lce_occurrence_at",
                    "fit_d_t_created_at",
                    "fit_dos_cty_sae_code",
                    "fit_dos_cty_name");
    static final Map<String, String> KINDS =
            Map.ofEntries(
                    Map.entry("fit_crn_psn_nickname", "text"),
                    Map.entry("corporation_sequence_number", "integralNumber"),
                    Map.entry("invoices_mapping", "stringArray"),
                    Map.entry("type", "enum"),
                    Map.entry("fit_fsn_name", "text"),
                    Map.entry("service_at", "timestamp"),
                    Map.entry("fit_dpn_delivery_prediction_at", "timestamp"),
                    Map.entry("finished_at", "timestamp"),
                    Map.entry("fit_fln_cln_nickname", "text"),
                    Map.entry("fit_fln_status", "enum"),
                    Map.entry("fit_o_n_name", "text"),
                    Map.entry("fit_o_n_drt_nickname", "text"),
                    Map.entry("fit_dyn_name", "text"),
                    Map.entry("fit_dyn_drt_nickname", "text"),
                    Map.entry("fit_pyr_nickname", "text"),
                    Map.entry("fit_rpt_name", "text"),
                    Map.entry("invoices_volumes", "integralNumber"),
                    Map.entry("taxed_weight", "decimalString"),
                    Map.entry("invoices_value", "decimalString"),
                    Map.entry("total", "decimalString"),
                    Map.entry("service_type", "enum"),
                    Map.entry("fit_fte_lce_ore_description", "text"),
                    Map.entry("fit_fte_lce_occurrence_at", "timestamp"),
                    Map.entry("fit_d_t_created_at", "timestamp"),
                    Map.entry("fit_dos_cty_sae_code", "text"),
                    Map.entry("fit_dos_cty_name", "text"));
    private static final Set<String> ENUMS = Set.of("type", "fit_fln_status", "service_type");
    private static final Set<String> TOTAL_FIELDS =
            Set.of("invoices_volumes", "taxed_weight", "invoices_value", "total");
    private static final String WHOLE_SECOND = "SOURCE_OFFSET_CIVIL_WHOLE_SECOND_V1";
    private static final String TRUNCATE_FRACTION = "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1";

    record Raw(JsonNode value, String numericLexeme) {}

    private LocalizadorContentAdapter() {}

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
                        && "LOCALIZADOR_NATIVE26_CANDIDATE_CATALOG_V1"
                                .equals(text(catalog, "schema"))
                        && LocalizadorIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "LOCALIZADOR".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 8656)
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 26
                        && catalog.path("totalColumnCount").isIntegralNumber()
                        && catalog.path("totalColumnCount").canConvertToInt()
                        && catalog.path("totalColumnCount").intValue() >= 0
                        && catalog.path("totalColumnCount").intValue() <= 26
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
                "LOCALIZADOR_EXPLICIT_KEYLESS26_CATALOG");
        final var fields = new HashSet<String>();
        int coordinate = 0;
        int totals = 0;
        for (final var field : catalog.required("columns")) {
            final String source = text(field, "sourceField");
            require(
                    field.isObject()
                            && exactInt(field.path("nativeColumn"), ++coordinate)
                            && fields.add(source)
                            && ORDER.get(coordinate - 1).equals(source)
                            && KINDS.get(source).equals(text(field, "kind"))
                            && !text(field, "title").isBlank()
                            && text(field, "title").length() <= 256
                            && field.path("total").isBoolean(),
                    "LOCALIZADOR_EXACT26_COORDINATE_FIELD_KIND");
            verifyPolicy(field);
            if (field.get("total").booleanValue()) {
                require(
                        TOTAL_FIELDS.contains(source),
                        "LOCALIZADOR_FOUR_DECLARED_TOTAL_FIELDS_ONLY");
                totals++;
            }
        }
        require(
                fields.equals(KINDS.keySet())
                        && totals == catalog.path("totalColumnCount").intValue(),
                "LOCALIZADOR_ALL26_FIELDS_AND_DECLARED_TOTALS");
        verifyLabels(catalog.path("enumLabels"));
        verifyEnumAbsentCodes(catalog);
    }

    private static void verifyEnumAbsentCodes(final JsonNode catalog) {
        if (!catalog.has("enumAbsentCodes")) {
            return;
        }
        final var absent = catalog.get("enumAbsentCodes");
        require(absent.isObject(), "LOCALIZADOR_ENUM_ABSENT_OBJECT");
        absent.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    ENUMS.contains(entry.getKey())
                                            && entry.getValue().isArray()
                                            && entry.getValue().size() <= 128,
                                    "LOCALIZADOR_ENUM_ABSENT_FIELD_SCOPE");
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
                                        "LOCALIZADOR_ENUM_ABSENT_CODE_BOUND_DISJOINT");
                            }
                        });
    }

    private static void verifyLabels(final JsonNode labels) {
        require(labels.isObject(), "LOCALIZADOR_EXPLICIT_ENUM_LABEL_OBJECT");
        labels.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    ENUMS.contains(entry.getKey())
                                            && entry.getValue().isObject()
                                            && entry.getValue().size() <= 128,
                                    "LOCALIZADOR_ENUM_LABEL_FIELD_SCOPE");
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
                                                            "LOCALIZADOR_ENUM_LABEL_BOUND"));
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
                "LOCALIZADOR_EXPLICIT_ABSENCE_POLICIES");
        require(
                !field.has("enumAbsentCodes")
                        && !field.has("textEncoding")
                        && !field.has("booleanEncoding")
                        && !field.has("sourceNumberEncoding"),
                "LOCALIZADOR_NO_UNOBSERVED_INHERITED_ENCODING");
        final boolean numeric =
                Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind);
        if (numeric) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").startsWith("XLSX:1900:")
                            && text(field, "nativeCarrier").length() <= 256,
                    "LOCALIZADOR_EXPLICIT_NATIVE_CARRIER");
        } else {
            require(
                    !field.has("nativeCarrier")
                            && !"NULL_NATIVE_BLANK".equals(text(field, "nullPresentation")),
                    "LOCALIZADOR_TEXT_POLICY_NO_NATIVE_NUMERIC_CARRIER");
        }
        if ("decimalString".equals(kind)) {
            require(
                    "DIRECT_BINARY64_NEAREST_EVEN_V1".equals(text(field, "nativeNumberEncoding")),
                    "LOCALIZADOR_EXPLICIT_DIRECT_BINARY64");
        } else {
            require(!field.has("nativeNumberEncoding"), "LOCALIZADOR_NO_UNUSED_NUMBER_ENCODING");
        }
        if ("timestamp".equals(kind)) {
            require(
                    Set.of(WHOLE_SECOND, TRUNCATE_FRACTION)
                                    .contains(text(field, "sourceTimestampEncoding"))
                            && "NATIVE_DATE_TIME_NEAREST_SECOND_V1"
                                    .equals(text(field, "nativeTimestampEncoding")),
                    "LOCALIZADOR_EXPLICIT_TIMESTAMP_ENCODING");
        } else {
            require(
                    !field.has("sourceTimestampEncoding") && !field.has("nativeTimestampEncoding"),
                    "LOCALIZADOR_NO_UNUSED_TIMESTAMP_ENCODING");
        }
        if ("stringArray".equals(kind)) {
            require(
                    "invoices_mapping".equals(text(field, "sourceField"))
                            && field.path("arraySeparator").isTextual()
                            && field.get("arraySeparator").textValue().length() <= 16
                            && Set.of("STRICT", "EMPTY_ARRAY_NATIVE_ABSENT")
                                    .contains(text(field, "emptyArrayPresentation"))
                            && "STRICT".equals(text(field, "emptyStringPresentation")),
                    "LOCALIZADOR_EXPLICIT_NF_ARRAY_POLICY");
        } else {
            require(
                    !field.has("arraySeparator") && !field.has("emptyArrayPresentation"),
                    "LOCALIZADOR_ARRAY_POLICY_ONLY_FOR_NF");
        }
        if ("service_at".equals(text(field, "sourceField"))) {
            require(
                    "STRICT".equals(text(field, "nullPresentation"))
                            && "STRICT".equals(text(field, "emptyStringPresentation")),
                    "LOCALIZADOR_SCOPE_DATES_STRICT");
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
        LocalizadorIndependentRequest.validate(request);
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
                BoundedExportWorkbook.readLocalizadorRows(
                        workbook.read(8388608, budget), columns, budget);
        require(
                "1900".equals(nativeRows.epoch()) && !nativeRows.rows().isEmpty(),
                "LOCALIZADOR_NATIVE_ROWS_EPOCH1900");
        final var left = new KeylessExactExportTable(columns, budget);
        final var right = new KeylessExactExportTable(columns, budget);
        final var leftOriginal = new KeylessExactExportTable(rawColumns, budget);
        final var rightOriginal = new KeylessExactExportTable(rawColumns, budget);
        readSource(sourceBytes, admitted, fields, catalog, left, leftOriginal, budget);
        for (final var row : nativeRows.rows()) {
            require(row.cells().size() == 26, "LOCALIZADOR_ALL26_NATIVE_CELLS");
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
                        .put("schema", "LOCALIZADOR_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", LocalizadorIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("columns", 26)
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
                        .put("minutaCandidateUsedAsIdentity", false)
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
                "LOCALIZADOR_COMPLETE_JSONL_FINAL_LF");
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
                        "LOCALIZADOR_SOURCE_LINE_BOUND");
                final var record = record(line);
                require(
                        record.keySet().equals(KINDS.keySet()),
                        "LOCALIZADOR_SOURCE_EXACT26_FIELDS");
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
        require(rows > 0, "LOCALIZADOR_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "LOCALIZADOR_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "LOCALIZADOR_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(
                        names.add(name) && parser.nextToken() != null,
                        "LOCALIZADOR_ENVELOPE_DUPLICATE_OR_MISSING");
                if ("record".equals(name)) {
                    require(
                            parser.currentToken() == JsonToken.START_OBJECT,
                            "LOCALIZADOR_RECORD_OBJECT");
                    result = new HashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        require(
                                parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 26,
                                "LOCALIZADOR_RECORD_FIELD_BOUND");
                        final String field = parser.currentName();
                        final var token = parser.nextToken();
                        require(
                                token != null
                                        && (token.isScalarValue()
                                                || ("invoices_mapping".equals(field)
                                                        && token == JsonToken.START_ARRAY)),
                                "LOCALIZADOR_SCALAR_OR_INVOICES_ARRAY_VALUE");
                        final String numeric = token.isNumeric() ? parser.getText() : null;
                        final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
                        require(
                                value != null
                                        && result.putIfAbsent(field, new Raw(value, numeric))
                                                == null,
                                "LOCALIZADOR_RECORD_DUPLICATE");
                    }
                } else if ("entity".equals(name)) {
                    entity = ExportValidationSupport.JSON.readTree(parser);
                } else if ("templateId".equals(name)) {
                    template = ExportValidationSupport.JSON.readTree(parser);
                } else {
                    throw new IllegalArgumentException("LOCALIZADOR_ENVELOPE_UNKNOWN_FIELD");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "LOCALIZADOR".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 8656),
                    "LOCALIZADOR_ENVELOPE_BINDING");
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
                        "LOCALIZADOR_SOURCE_INTEGER_WIRE_TYPE");
                yield integer(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "LOCALIZADOR_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(value.textValue());
            }
            case "date" -> {
                require(value.isTextual(), "LOCALIZADOR_SOURCE_DATE_TYPE");
                yield literal(QualifiedColetasModel.sourceCivil(value.textValue()));
            }
            case "timestamp" -> {
                require(value.isTextual(), "LOCALIZADOR_SOURCE_TIMESTAMP_TYPE");
                yield literal(
                        sourceTimestamp(value.textValue(), text(field, "sourceTimestampEncoding")));
            }
            case "enum" -> {
                require(value.isTextual(), "LOCALIZADOR_SOURCE_ENUM_TYPE");
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
                require(value.isTextual(), "LOCALIZADOR_SOURCE_TEXT_TYPE");
                yield literal(value.textValue());
            }
            case "stringArray" -> {
                require(value.isArray() && value.size() <= 1000, "LOCALIZADOR_NF_ARRAY_TYPE_BOUND");
                if (value.isEmpty()
                        && "EMPTY_ARRAY_NATIVE_ABSENT"
                                .equals(text(field, "emptyArrayPresentation"))) {
                    yield new ExactExportTable.Atom("ABSENT", "");
                }
                final var items = new ArrayList<String>();
                for (final var item : value) {
                    require(
                            item.isTextual() && item.textValue().length() <= 1024,
                            "LOCALIZADOR_NF_ARRAY_STRING_BOUND");
                    items.add(item.textValue());
                }
                yield literal(String.join(field.get("arraySeparator").textValue(), items));
            }
            default -> throw new IllegalArgumentException("LOCALIZADOR_SOURCE_KIND");
        };
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicy(field);
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "LOCALIZADOR_NATIVE_ABSENT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "LOCALIZADOR_NATIVE_BLANK_CARRIER");
            return "NULL_NATIVE_BLANK".equals(text(field, "nullPresentation"))
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        final String kind = text(field, "kind");
        if (Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind)) {
            require(
                    "NUMBER".equals(atom.type())
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "LOCALIZADOR_NATIVE_NUMERIC_TYPE_CARRIER");
            return switch (kind) {
                case "integralNumber" -> integer(atom.value());
                case "date" -> literal(QualifiedColetasModel.nativeCivil(atom.value()));
                case "timestamp" -> literal(QualifiedColetasModel.nativeTimestamp(atom.value()));
                default -> encoded(atom.value());
            };
        }
        require(
                "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                "LOCALIZADOR_NATIVE_TEXT_TYPE_CARRIER");
        if ("enum".equals(kind)) {
            boolean observed = false;
            for (final var value : catalog.path("enumLabels").path(text(field, "sourceField"))) {
                observed |= value.isTextual() && atom.value().equals(value.textValue());
            }
            require(observed, "LOCALIZADOR_NATIVE_ENUM_LABEL_UNOBSERVED");
        }
        return literal(atom.value());
    }

    private static String label(final JsonNode object, final String field, final String code) {
        final var value = object.path(field).path(code);
        require(value.isTextual(), "LOCALIZADOR_UNOBSERVED_LABEL");
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
                "LOCALIZADOR_SOURCE_TIMESTAMP_ENCODING");
        require(
                value != null
                        && value.length() <= 126
                        && value.matches(
                                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})"),
                "LOCALIZADOR_SOURCE_OFFSET_TIMESTAMP");
        final var parsed = OffsetDateTime.parse(value);
        require(
                !WHOLE_SECOND.equals(encoding) || parsed.getNano() == 0,
                "LOCALIZADOR_SOURCE_WHOLE_SECOND_TIMESTAMP");
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
                "LOCALIZADOR_SCOPE_VALUE_REQUIRED");
        final var day = LocalDate.parse(atom.value().substring(0, 10));
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "LOCALIZADOR_SCOPE_VALUE_OUTSIDE_PERIOD");
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
        require(number.abs().bitLength() <= 63, "LOCALIZADOR_INTEGER_VALUE_BOUND");
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

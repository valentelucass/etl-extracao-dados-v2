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

/** Offline full-row comparison. Parcel candidates are values, never synthetic root identifiers. */
public final class ContasPagarContentAdapter {
    static final Map<String, String> KINDS =
            Map.ofEntries(
                    Map.entry("ant_aln_name", "text"), Map.entry("ant_ces_acr_name", "text"),
                    Map.entry("ant_ces_value", "decimalString"),
                            Map.entry("ant_crn_psn_nickname", "text"),
                    Map.entry("ant_ils_atn_liquidation_date", "date"),
                            Map.entry("ant_ils_atn_reconciled", "boolean"),
                    Map.entry("ant_ils_atn_transaction_date", "date"),
                            Map.entry("ant_ils_comments", "text"),
                    Map.entry("ant_ils_expense_description", "text"),
                            Map.entry("ant_ils_pas_ant_classification", "text"),
                    Map.entry("ant_ils_pas_ant_name", "text"),
                            Map.entry("ant_ils_pas_value", "decimalString"),
                    Map.entry("ant_ils_sequence_code", "integralNumber"),
                            Map.entry("ant_rir_name", "text"),
                    Map.entry("ant_uer_name", "text"), Map.entry("comments", "text"),
                    Map.entry("competence_month", "integralNumber"),
                            Map.entry("competence_year", "integralNumber"),
                    Map.entry("created_at", "timestamp"),
                            Map.entry("discount_value", "decimalString"),
                    Map.entry("document", "text"), Map.entry("interest_value", "decimalString"),
                    Map.entry("issue_date", "date"), Map.entry("paid", "boolean"),
                    Map.entry("paid_value", "decimalString"), Map.entry("type", "enum"),
                    Map.entry("value", "decimalString"),
                            Map.entry("value_to_pay", "decimalString"));
    private static final Set<String> ENUMS = Set.of("type");
    private static final Set<String> BOOLEANS = Set.of("paid", "ant_ils_atn_reconciled");
    private static final String CLASSIFICATION = "ant_ils_pas_ant_classification";
    private static final String CLASSIFICATION_ENCODING = "OBSERVED_CLASSIFICATION_LABELS_V1";
    private static final String WHOLE_SECOND = "SOURCE_OFFSET_CIVIL_WHOLE_SECOND_V1";
    private static final String TRUNCATE_FRACTION = "SOURCE_OFFSET_CIVIL_TRUNCATE_FRACTION_V1";
    private static final String SOURCE_DECIMAL16 =
            "SOURCE_BINARY64_DECIMAL16_HALF_EVEN_BINARY64_V1";

    record Raw(JsonNode value, String numericLexeme) {}

    private ContasPagarContentAdapter() {}

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
                        && "CONTAS_A_PAGAR_NATIVE28_CANDIDATE_CATALOG_V1"
                                .equals(text(catalog, "schema"))
                        && ContasPagarIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "CONTAS_A_PAGAR".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 8636)
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 28
                        && catalog.path("totalColumnCount").isIntegralNumber()
                        && catalog.path("totalColumnCount").canConvertToInt()
                        && catalog.path("totalColumnCount").intValue() >= 0
                        && catalog.path("totalColumnCount").intValue() <= 28
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
                "CONTAS_PAGAR_EXPLICIT_KEYLESS28_CATALOG");
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
                            && KINDS.get(source).equals(text(field, "kind"))
                            && !text(field, "title").isBlank()
                            && text(field, "title").length() <= 256
                            && field.path("total").isBoolean(),
                    "CONTAS_PAGAR_EXACT28_COORDINATE_FIELD_KIND");
            verifyPolicy(field);
            if (field.get("total").booleanValue()) {
                require(
                        "decimalString".equals(text(field, "kind")),
                        "CONTAS_PAGAR_FINANCIAL_TOTAL_FIELDS_ONLY");
                totals++;
            }
        }
        require(
                fields.equals(KINDS.keySet())
                        && totals == catalog.path("totalColumnCount").intValue(),
                "CONTAS_PAGAR_ALL28_FIELDS_AND_DECLARED_TOTALS");
        verifyLabels(catalog.path("enumLabels"), ENUMS, false);
        verifyLabels(catalog.path("booleanLabels"), BOOLEANS, true);
        verifyClassificationLabels(catalog);
    }

    private static void verifyLabels(
            final JsonNode labels, final Set<String> allowed, final boolean bool) {
        require(labels.isObject(), "CONTAS_PAGAR_EXPLICIT_LABEL_OBJECT");
        labels.fields()
                .forEachRemaining(
                        entry -> {
                            require(
                                    allowed.contains(entry.getKey())
                                            && entry.getValue().isObject()
                                            && entry.getValue().size() <= 128,
                                    "CONTAS_PAGAR_LABEL_FIELD_SCOPE");
                            final var keys = new HashSet<String>();
                            entry.getValue()
                                    .fields()
                                    .forEachRemaining(
                                            label -> {
                                                require(
                                                        keys.add(label.getKey())
                                                                && label.getKey().length() <= 128
                                                                && label.getValue().isTextual()
                                                                && !label.getValue()
                                                                        .textValue()
                                                                        .isEmpty()
                                                                && label.getValue()
                                                                                .textValue()
                                                                                .length()
                                                                        <= 256,
                                                        "CONTAS_PAGAR_LABEL_BOUND");
                                            });
                            require(
                                    !bool || keys.equals(Set.of("true", "false")),
                                    "CONTAS_PAGAR_BOTH_BOOLEAN_LABELS");
                            if (bool) {
                                require(
                                        !entry.getValue()
                                                .get("true")
                                                .equals(entry.getValue().get("false")),
                                        "CONTAS_PAGAR_BOOLEAN_LABELS_DISTINCT");
                            }
                        });
    }

    private static boolean classificationEncoding(final JsonNode field) {
        return CLASSIFICATION_ENCODING.equals(field.path("textEncoding").asText());
    }

    private static void verifyClassificationLabels(final JsonNode catalog) {
        boolean selected = false;
        for (final var field : catalog.required("columns")) {
            selected |= classificationEncoding(field);
        }
        require(
                selected == catalog.has("classificationLabels"),
                "CONTAS_PAGAR_CLASSIFICATION_LABELS_POLICY_SCOPE");
        if (!selected) {
            return;
        }
        final var labels = catalog.get("classificationLabels");
        require(
                labels.isObject() && !labels.isEmpty() && labels.size() <= 128,
                "CONTAS_PAGAR_CLASSIFICATION_FLAT_LABEL_OBJECT");
        labels.fields()
                .forEachRemaining(
                        label ->
                                require(
                                        !label.getKey().isEmpty()
                                                && label.getKey().length() <= 128
                                                && label.getValue().isTextual()
                                                && !label.getValue().textValue().isEmpty()
                                                && label.getValue().textValue().length() <= 256,
                                        "CONTAS_PAGAR_CLASSIFICATION_LABEL_BOUND"));
    }

    private static String classificationLabel(final JsonNode catalog, final String code) {
        final var mapped = catalog.path("classificationLabels").path(code);
        require(
                mapped.isTextual() && !mapped.textValue().isEmpty(),
                "CONTAS_PAGAR_CLASSIFICATION_LABEL_UNOBSERVED");
        return mapped.textValue();
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
                "CONTAS_PAGAR_EXPLICIT_ABSENCE_POLICIES");
        require(
                !field.has("textEncoding")
                        || ("text".equals(kind)
                                && CLASSIFICATION.equals(text(field, "sourceField"))
                                && classificationEncoding(field)),
                "CONTAS_PAGAR_CLASSIFICATION_TEXT_ENCODING_SCOPE");
        require(
                !field.has("sourceNumberEncoding")
                        || ("decimalString".equals(kind)
                                && "ant_ces_value".equals(text(field, "sourceField"))
                                && SOURCE_DECIMAL16.equals(text(field, "sourceNumberEncoding"))),
                "CONTAS_PAGAR_SOURCE_NUMBER_ENCODING_SCOPE");
        final boolean numeric =
                Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind);
        if (numeric) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").startsWith("XLSX:1900:")
                            && text(field, "nativeCarrier").length() <= 256,
                    "CONTAS_PAGAR_EXPLICIT_NATIVE_CARRIER");
        } else {
            require(
                    !field.has("nativeCarrier")
                            && !"NULL_NATIVE_BLANK".equals(text(field, "nullPresentation")),
                    "CONTAS_PAGAR_TEXT_POLICY_NO_NATIVE_NUMERIC_CARRIER");
        }
        if ("decimalString".equals(kind)) {
            require(
                    "DIRECT_BINARY64_NEAREST_EVEN_V1".equals(text(field, "nativeNumberEncoding")),
                    "CONTAS_PAGAR_EXPLICIT_DIRECT_BINARY64");
        } else {
            require(!field.has("nativeNumberEncoding"), "CONTAS_PAGAR_NO_UNUSED_NUMBER_ENCODING");
        }
        if ("timestamp".equals(kind)) {
            require(
                    Set.of(WHOLE_SECOND, TRUNCATE_FRACTION)
                                    .contains(text(field, "sourceTimestampEncoding"))
                            && "NATIVE_DATE_TIME_NEAREST_SECOND_V1"
                                    .equals(text(field, "nativeTimestampEncoding")),
                    "CONTAS_PAGAR_EXPLICIT_TIMESTAMP_ENCODING");
        } else {
            require(
                    !field.has("sourceTimestampEncoding") && !field.has("nativeTimestampEncoding"),
                    "CONTAS_PAGAR_NO_UNUSED_TIMESTAMP_ENCODING");
        }
        require(
                "boolean".equals(kind)
                        ? Set.of("NATIVE_BOOLEAN_V1", "NATIVE_TEXT_LABELS_V1")
                                .contains(text(field, "booleanEncoding"))
                        : !field.has("booleanEncoding"),
                "CONTAS_PAGAR_EXPLICIT_BOOLEAN_ENCODING");
        if ("issue_date".equals(text(field, "sourceField"))) {
            require(
                    "STRICT".equals(text(field, "nullPresentation"))
                            && "STRICT".equals(text(field, "emptyStringPresentation")),
                    "CONTAS_PAGAR_SCOPE_DATES_STRICT");
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
        ContasPagarIndependentRequest.validate(request);
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
                            total ? "BINARY64_UNITS_129" : "NONE"));
            rawColumns.add(new ExactExportTable.Column(text(field, "title"), index, index, false));
        }
        final byte[] sourceBytes = source.read(8388608, budget);
        final var nativeRows =
                BoundedExportWorkbook.readContasPagarRows(
                        workbook.read(8388608, budget), columns, budget);
        require(
                "1900".equals(nativeRows.epoch()) && !nativeRows.rows().isEmpty(),
                "CONTAS_PAGAR_NATIVE_ROWS_EPOCH1900");
        final var left = new KeylessExactExportTable(columns, budget);
        final var right = new KeylessExactExportTable(columns, budget);
        final var leftOriginal = new KeylessExactExportTable(rawColumns, budget);
        final var rightOriginal = new KeylessExactExportTable(rawColumns, budget);
        readSource(sourceBytes, admitted, fields, catalog, left, leftOriginal, budget);
        for (final var row : nativeRows.rows()) {
            require(row.cells().size() == 28, "CONTAS_PAGAR_ALL28_NATIVE_CELLS");
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
                        .put("schema", "CONTAS_A_PAGAR_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("columns", 28)
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
                        .put("parcelCandidateUsedAsIdentity", false)
                        .put("deduplicationPerformed", false)
                        .put("issueDateVerifiedForEveryRowBothSides", true)
                        .put("createdAtScopeEnforced", false)
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
                "CONTAS_PAGAR_COMPLETE_JSONL_FINAL_LF");
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
                        !line.isBlank() && line.length() <= 262144 && ++rows <= 12000,
                        "CONTAS_PAGAR_SOURCE_LINE_BOUND");
                final var record = record(line);
                require(
                        record.keySet().equals(KINDS.keySet()),
                        "CONTAS_PAGAR_SOURCE_EXACT28_FIELDS");
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
        require(rows > 0, "CONTAS_PAGAR_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "CONTAS_PAGAR_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "CONTAS_PAGAR_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(
                        names.add(name) && parser.nextToken() != null,
                        "CONTAS_PAGAR_ENVELOPE_DUPLICATE_OR_MISSING");
                if ("record".equals(name)) {
                    require(
                            parser.currentToken() == JsonToken.START_OBJECT,
                            "CONTAS_PAGAR_RECORD_OBJECT");
                    result = new HashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        require(
                                parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 28,
                                "CONTAS_PAGAR_RECORD_FIELD_BOUND");
                        final String field = parser.currentName();
                        final var token = parser.nextToken();
                        require(
                                token != null && token.isScalarValue(),
                                "CONTAS_PAGAR_SCALAR_RECORD_VALUE");
                        final String numeric = token.isNumeric() ? parser.getText() : null;
                        final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
                        require(
                                value != null
                                        && result.putIfAbsent(field, new Raw(value, numeric))
                                                == null,
                                "CONTAS_PAGAR_RECORD_DUPLICATE");
                    }
                } else if ("entity".equals(name)) {
                    entity = ExportValidationSupport.JSON.readTree(parser);
                } else if ("templateId".equals(name)) {
                    template = ExportValidationSupport.JSON.readTree(parser);
                } else {
                    throw new IllegalArgumentException("CONTAS_PAGAR_ENVELOPE_UNKNOWN_FIELD");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "CONTAS_A_PAGAR".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 8636),
                    "CONTAS_PAGAR_ENVELOPE_BINDING");
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
                        "CONTAS_PAGAR_SOURCE_INTEGER_WIRE_TYPE");
                yield integer(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "CONTAS_PAGAR_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(sourceNumber(field, value.textValue()));
            }
            case "date" -> {
                require(value.isTextual(), "CONTAS_PAGAR_SOURCE_DATE_TYPE");
                yield literal(QualifiedColetasModel.sourceCivil(value.textValue()));
            }
            case "timestamp" -> {
                require(value.isTextual(), "CONTAS_PAGAR_SOURCE_TIMESTAMP_TYPE");
                yield literal(
                        sourceTimestamp(value.textValue(), text(field, "sourceTimestampEncoding")));
            }
            case "boolean" -> {
                require(value.isBoolean(), "CONTAS_PAGAR_SOURCE_BOOLEAN_TYPE");
                yield "NATIVE_BOOLEAN_V1".equals(text(field, "booleanEncoding"))
                        ? ExactExportTable.Atom.json(value)
                        : literal(
                                label(
                                        catalog.path("booleanLabels"),
                                        text(field, "sourceField"),
                                        value.booleanValue() ? "true" : "false"));
            }
            case "enum" -> {
                require(value.isTextual(), "CONTAS_PAGAR_SOURCE_ENUM_TYPE");
                yield literal(
                        label(
                                catalog.path("enumLabels"),
                                text(field, "sourceField"),
                                value.textValue()));
            }
            case "text" -> {
                require(value.isTextual(), "CONTAS_PAGAR_SOURCE_TEXT_TYPE");
                yield literal(
                        classificationEncoding(field)
                                ? classificationLabel(catalog, value.textValue())
                                : value.textValue());
            }
            default -> throw new IllegalArgumentException("CONTAS_PAGAR_SOURCE_KIND");
        };
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicy(field);
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "CONTAS_PAGAR_NATIVE_ABSENT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "CONTAS_PAGAR_NATIVE_BLANK_CARRIER");
            return "NULL_NATIVE_BLANK".equals(text(field, "nullPresentation"))
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        final String kind = text(field, "kind");
        if (Set.of("integralNumber", "decimalString", "date", "timestamp").contains(kind)) {
            require(
                    "NUMBER".equals(atom.type())
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "CONTAS_PAGAR_NATIVE_NUMERIC_TYPE_CARRIER");
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
                    "CONTAS_PAGAR_NATIVE_BOOLEAN_TYPE_CARRIER");
            return new ExactExportTable.Atom("BOOLEAN", atom.value());
        }
        require(
                "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                "CONTAS_PAGAR_NATIVE_TEXT_TYPE_CARRIER");
        if ("enum".equals(kind) || "boolean".equals(kind)) {
            final var labels =
                    catalog.path("boolean".equals(kind) ? "booleanLabels" : "enumLabels")
                            .path(text(field, "sourceField"));
            boolean observed = false;
            for (final var value : labels) {
                observed |= value.isTextual() && atom.value().equals(value.textValue());
            }
            require(observed, "CONTAS_PAGAR_NATIVE_LABEL_UNOBSERVED");
        }
        if (classificationEncoding(field)) {
            boolean observed = false;
            for (final var label : catalog.path("classificationLabels")) {
                observed |= label.isTextual() && atom.value().equals(label.textValue());
            }
            require(observed, "CONTAS_PAGAR_NATIVE_CLASSIFICATION_LABEL_UNOBSERVED");
        }
        return literal(atom.value());
    }

    private static String label(final JsonNode object, final String field, final String code) {
        final var value = object.path(field).path(code);
        require(value.isTextual(), "CONTAS_PAGAR_UNOBSERVED_LABEL");
        return value.textValue();
    }

    static String sourceTimestamp(final String value) {
        return sourceTimestamp(value, WHOLE_SECOND);
    }

    static String sourceTimestamp(final String value, final String encoding) {
        require(
                Set.of(WHOLE_SECOND, TRUNCATE_FRACTION).contains(encoding),
                "CONTAS_PAGAR_SOURCE_TIMESTAMP_ENCODING");
        require(
                value != null
                        && value.length() <= 128
                        && value.matches(
                                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})"),
                "CONTAS_PAGAR_SOURCE_OFFSET_TIMESTAMP");
        final var parsed = OffsetDateTime.parse(value);
        require(
                !WHOLE_SECOND.equals(encoding) || parsed.getNano() == 0,
                "CONTAS_PAGAR_SOURCE_WHOLE_SECOND_TIMESTAMP");
        // The declared presentation keeps the original civil clock; offset remains in the raw axis.
        return parsed.toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss"));
    }

    private static void verifyPeriodValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode request) {
        final String source = text(field, "sourceField");
        if (!"issue_date".equals(source)) {
            return;
        }
        require(
                "TEXT".equals(atom.type()) && atom.value().length() >= 10,
                "CONTAS_PAGAR_SCOPE_VALUE_REQUIRED");
        final var day = LocalDate.parse(atom.value().substring(0, 10));
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "CONTAS_PAGAR_SCOPE_VALUE_OUTSIDE_PERIOD");
    }

    static ExactExportTable.Atom original(final Raw value) {
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
        require(number.abs().bitLength() <= 63, "CONTAS_PAGAR_INTEGER_VALUE_BOUND");
        return new ExactExportTable.Atom("NUMBER", number.toString());
    }

    static ExactBinary64.Encoding sourceNumber(final JsonNode field, final String lexeme) {
        verifyPolicy(field);
        // Primary codec reconstructs EXACT binary64 decimal (not BigDecimal.valueOf),
        // then sixteen-significant HALF_EVEN and exact nearest-even reencoding.
        return field.has("sourceNumberEncoding")
                ? CotacoesNativeNumericEncoding.g16(lexeme)
                : ExactBinary64.encode(lexeme);
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

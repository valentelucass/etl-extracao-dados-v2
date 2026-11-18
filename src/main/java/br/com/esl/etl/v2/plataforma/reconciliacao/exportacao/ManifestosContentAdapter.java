package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Bounded candidate diagnostics. Native/raw occurrences remain separate from visual keys. */
public final class ManifestosContentAdapter {
    static final String CATALOG_SHA =
            "d4fe97c3e8ab474130c52a6b67fa2bd93ffa89093ec415f13db9a49dd437003c";
    private static final String NULL_OMISSION = "SCOPED_NULL_NATIVE_OMISSION_OR_BLANK";
    private static final String EMPTY_OMISSION = "SCOPED_EMPTY_NATIVE_OMISSION";
    private static final String NULL_NO = "BOOLEAN_NULL_LABEL_NAO";
    private static final String CLOSED_AT_NULL_BLANK =
            "MANIFESTOS_COLUMN12_NULL_TIMESTAMP_BLANK_V1";
    // Only the observed trailer-capacity NULL / native ABSENT pairs; no computed numeric zero.
    private static final String ABSENCE_TOTAL_84 = "MANIFESTOS_COLUMN84_ABSENT_ONLY_BAGS_EQUAL_V1";
    private static final String ABSENCE_TOTAL = "MANIFESTOS_COLUMN86_ABSENT_ONLY_BAGS_EQUAL_V1";
    private static final LocalDateTime EPOCH = LocalDate.of(1899, 12, 30).atStartOfDay();

    record Raw(JsonNode value, String numericLexeme) {}

    private record EffectiveTotals(
            List<Boolean> equal, List<String> policies, int numericEqual, int absenceEqual) {}

    private ManifestosContentAdapter() {}

    public static JsonNode catalog() throws IOException {
        try (var input =
                ManifestosContentAdapter.class.getResourceAsStream(
                        "/contracts/dataexport/manifestos-native91-v1.json")) {
            require(input != null, "MANIFESTOS_CATALOG_REQUIRED");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && CATALOG_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "MANIFESTOS_CATALOG_PIN");
            try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
                final JsonNode catalog = ExportValidationSupport.JSON.readTree(parser);
                require(catalog != null && parser.nextToken() == null, "MANIFESTOS_CATALOG_EOF");
                verifyCatalog(catalog);
                return catalog;
            }
        }
    }

    static void verifyCatalog(final JsonNode catalog) {
        require(
                "MANIFESTOS_NATIVE91_CATALOG_V1".equals(text(catalog, "schema"))
                        && ManifestosIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "MANIFESTOS".equals(text(catalog, "entity"))
                        && catalog.path("reportId").intValue() == 6399
                        && catalog.path("idColumnIndex").intValue() == 2
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 91
                        && catalog.path("presentationQualified").isBoolean()
                        && !catalog.get("presentationQualified").booleanValue()
                        && catalog.path("contentAccepted").isBoolean()
                        && !catalog.get("contentAccepted").booleanValue(),
                "MANIFESTOS_CANDIDATE_CATALOG_SCOPE");
        final var items = new HashSet<String>();
        final var fields = new HashSet<String>();
        int ordinal = 0;
        int totals = 0;
        for (final var field : catalog.required("columns")) {
            require(
                    field.path("nativeColumn").intValue() == ++ordinal
                            && items.add(text(field, "itemId"))
                            && fields.add(text(field, "sourceField"))
                            && field.path("total").isBoolean(),
                    "MANIFESTOS_CATALOG_ITEM_COORDINATE");
            final String kind = text(field, "kind");
            if (ordinal == 12
                    || CLOSED_AT_NULL_BLANK.equals(field.path("nullPresentation").asText())) {
                require(closedAtNullBlank(field), "MANIFESTOS_CLOSED_AT_NULL_EXACT_SCOPE");
            }
            if (ordinal == 46) {
                require(
                        emptyEnumOmission(field) && "NONE".equals(text(field, "nullPresentation")),
                        "MANIFESTOS_SCOPED_EMPTY_ENUM_OMISSION");
            }
            if (ordinal == 84 || ordinal == 86) {
                require(absenceTotalPolicy(field), "MANIFESTOS_ABSENCE_TOTAL_EXACT_SCOPE");
            } else {
                require(!field.has("emptyAggregatePolicy"), "MANIFESTOS_ABSENCE_TOTAL_EXACT_SCOPE");
            }
            require(
                    List.of("text", "integer", "decimal", "timestamp", "boolean", "enum", "array")
                            .contains(kind),
                    "MANIFESTOS_CATALOG_KIND");
            final String nullPolicy = text(field, "nullPresentation");
            require(
                    List.of("NONE", NULL_OMISSION, NULL_NO, CLOSED_AT_NULL_BLANK)
                                    .contains(nullPolicy)
                            && (!NULL_NO.equals(nullPolicy)
                                    || "mft_fis_fit_mey_mobile_receipt"
                                            .equals(text(field, "sourceField")))
                            && ("NONE".equals(text(field, "emptyStringPresentation"))
                                    || EMPTY_OMISSION.equals(text(field, "emptyStringPresentation"))
                                            && ("mft_aoe_comments"
                                                            .equals(text(field, "sourceField"))
                                                    || emptyEnumOmission(field))),
                    "MANIFESTOS_SCOPED_PRESENTATION_POLICY");
            if (field.get("total").booleanValue()) {
                totals++;
                require(
                        ("decimal".equals(kind)
                                        && "BINARY64_UNITS_129".equals(text(field, "totalKind")))
                                || ("integer".equals(kind)
                                        && "DECIMAL_EXACT".equals(text(field, "totalKind"))),
                        "MANIFESTOS_EXPLICIT_TOTAL_MODEL");
            } else {
                require("NONE".equals(text(field, "totalKind")), "MANIFESTOS_NON_TOTAL_MODEL");
            }
        }
        require(
                totals == catalog.path("totalColumnCount").intValue()
                        && "sequence_code"
                                .equals(catalog.path("columns").get(2).path("sourceField").asText())
                        && "closed_at"
                                .equals(
                                        catalog.path("columns")
                                                .get(11)
                                                .path("sourceField")
                                                .asText())
                        && "finished_at"
                                .equals(
                                        catalog.path("columns")
                                                .get(12)
                                                .path("sourceField")
                                                .asText()),
                "MANIFESTOS_EXACT_TOTALS_KEY_AND_ARRIVAL_CLOSING");
        require(
                "EXPLICIT_WHOLE_SECOND_DISPLAY_PRESERVE_RAW_FRACTIONS"
                                .equals(text(catalog, "timestampPrecision"))
                        && "OBSERVE_CANONICAL_ENCODING_SEPARATELY_FROM_FORMATTED_WHOLE_SECOND_KEY"
                                .equals(text(catalog, "nativeTimestampEncodingPolicy"))
                        && "NESTED_ORDERED_STRINGS_JOIN_COLON_SPACE_NO_SORT_OR_DEDUPLICATION"
                                .equals(text(catalog, "arrayPresentation"))
                        && "SCOPED_EMPTY_ARRAY_NATIVE_OMISSION"
                                .equals(text(catalog, "emptyArrayPresentation")),
                "MANIFESTOS_EXPLICIT_PRECISION_AND_ARRAY_POLICY");
    }

    public static ObjectNode compare(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return compareWithPostcheckProbe(request, source, workbook, budget, () -> {});
    }

    /**
     * Package-private deterministic integrity probe; the public entry point always supplies no
     * action.
     */
    static ObjectNode compareWithPostcheckProbe(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        ManifestosIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        ManifestosIndependentRequest.validate(admitted);
        final JsonNode catalog = catalog();
        final JsonNode fields = catalog.required("columns");
        final List<ExactExportTable.Column> columns = new ArrayList<>();
        final List<ExactExportTable.Column> rawColumns = new ArrayList<>();
        for (final var field : fields) {
            final int coordinate = field.required("nativeColumn").intValue();
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "title"),
                            coordinate,
                            coordinate,
                            field.required("total").booleanValue(),
                            text(field, "totalKind")));
            rawColumns.add(
                    new ExactExportTable.Column(
                            text(field, "title"), coordinate, coordinate, false));
        }
        final byte[] sourceBytes = source.read(8388608, budget);
        final byte[] workbookBytes = workbook.read(8388608, budget);
        final var nativeRows =
                BoundedExportWorkbook.readManifestosRows(workbookBytes, columns, budget);
        require(!nativeRows.isEmpty(), "MANIFESTOS_NATIVE_ROWS_REQUIRED");
        final var sourceTable = new ExactExportTable(columns, 2, budget);
        final var nativeTable = new ExactExportTable(columns, 2, budget);
        final var sourceRaw = new ExactExportTable(rawColumns, 2, budget);
        final var nativeRaw = new ExactExportTable(rawColumns, 2, budget);
        readSource(sourceBytes, fields, catalog, sourceTable, sourceRaw, budget);
        int nativeTimestampCells = 0;
        int noncanonicalTimestampEncodings = 0;
        for (final var row : nativeRows) {
            require("1900".equals(row.epoch()), "MANIFESTOS_QUALIFIED_EPOCH_1900_ONLY");
            final List<ExactExportTable.Atom> visual = new ArrayList<>();
            final List<ExactExportTable.Atom> raw = new ArrayList<>();
            for (final var field : fields) {
                final var atom =
                        row.cells()
                                .getOrDefault(
                                        field.required("nativeColumn").intValue(),
                                        new ExactExportTable.Atom("ABSENT", ""));
                raw.add(atom);
                final var visualAtom = nativeValue(field, atom, catalog);
                visual.add(visualAtom);
                if ("timestamp".equals(text(field, "kind")) && "NUMBER".equals(atom.type())) {
                    nativeTimestampCells++;
                    if (!canonicalWholeSecondEncoding(atom.value(), visualAtom.value())) {
                        noncanonicalTimestampEncodings++;
                    }
                }
            }
            nativeRaw.add(raw);
            nativeTable.add(visual);
        }
        for (final var table : List.of(sourceTable, nativeTable, sourceRaw, nativeRaw)) {
            table.seal();
        }
        final var comparison = sourceTable.compare(nativeTable);
        final var totals = effectiveTotals(fields, sourceTable, nativeTable, comparison);
        final boolean contentEqual =
                comparison.apiRows() > 0
                        && comparison.apiRows() == comparison.uiRows()
                        && comparison.fullTuplesEqual()
                        && comparison.columnsEqual().size() == 91
                        && comparison.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && comparison.apiOnlyOccurrences() == 0
                        && comparison.uiOnlyOccurrences() == 0
                        && comparison.changedIds() == 0
                        && comparison.changedMultiplicityIds() == 0
                        && comparison.apiOnlyIds() == 0
                        && comparison.uiOnlyIds() == 0
                        && comparison.apiDistinctIds() == comparison.uiDistinctIds()
                        && totals.equal().size() == 50
                        && totals.equal().stream().allMatch(Boolean::booleanValue);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("catalogSha256", CATALOG_SHA)
                        .put("columns", fields.size())
                        .put("totalColumns", catalog.required("totalColumnCount").intValue())
                        .put("sourceRows", comparison.apiRows())
                        .put("uiRows", comparison.uiRows())
                        .put("allOccurrencesCompared", true)
                        .put("pendingCellDecisions", 0)
                        .put("contentEqualUnderCandidatePresentation", contentEqual)
                        .put(
                                "status",
                                contentEqual
                                        ? "CONTENT_EQUAL_UNDER_CANDIDATE_PRESENTATION"
                                        : "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR")
                        .put("diagnosticOnly", true)
                        .put("presentationPoliciesQualified", false)
                        .put("contentAccepted", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("deduplicationPerformed", false)
                        .put("serviceDateFilterVerified", false)
                        .put("snapshotIdentityProven", false)
                        .put("globalIdentityProven", false)
                        .put("sourceCompletenessProven", false)
                        .put("sourceQueryAuthorized", false)
                        .put("fileExportQualified", false)
                        .put("temporalCausalityProven", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("runtimeAdopted", false)
                        .put("dashboardConsumptionVerified", false)
                        .put("persistenceAuthorized", false)
                        .put("productionAuthorized", false)
                        .put("rawPayloadAndExtraFieldsRetainedInPinnedSource", true)
                        .put("sourceSha256", source.sha256())
                        .put("workbookSha256", workbook.sha256());
        result.putObject("limits")
                .put("maximumRowsPerSide", 12000)
                .put("maximumBytesPerInput", 8388608)
                .put("maximumJsonLineCharacters", 262144)
                .put("maximumLogicalCellVisits", 1200000)
                .put("cellVisitModel", "NATIVE_READ_PLUS_TWO_AXES_PER_SIDE")
                .put("budgetSharedWithCaller", true)
                .put("earlierBudgetRefusalPossible", true);
        result.put("nativeTimestampCells", nativeTimestampCells)
                .put(
                        "nativeTimestampNoncanonicalWholeSecondEncodings",
                        noncanonicalTimestampEncodings)
                .put("nativeTimestampEncodingEqualityRequiredForVisualKey", false)
                .put("originalTimestampEncodingRetained", true);
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        result.set(
                "totalComparisonsEqual", ExportValidationSupport.JSON.valueToTree(totals.equal()));
        result.set(
                "totalComparisonPolicies",
                ExportValidationSupport.JSON.valueToTree(totals.policies()));
        result.put("numericTotalsEqual", totals.numericEqual())
                .put("absenceOnlyTotalsEqual", totals.absenceEqual())
                .put("emptyAggregatePolicy", ABSENCE_TOTAL)
                .put("absenceEqualityIsNumericZero", false);
        result.putArray("emptyAggregatePolicies").add(ABSENCE_TOTAL_84).add(ABSENCE_TOTAL);
        final var projection = result.putObject("fullValueProjection");
        projection.set("api", sourceTable.fullValueProjection());
        projection.set("ui", nativeTable.fullValueProjection());
        final var original = result.putObject("originalValueProjection");
        original.set("api", sourceRaw.fullValueProjection());
        original.set("ui", nativeRaw.fullValueProjection());
        final var bindings = result.putArray("columnDiagnostics");
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index);
            bindings.addObject()
                    .put("index", index)
                    .put("nativeColumn", field.path("nativeColumn").intValue())
                    .put("itemId", text(field, "itemId"))
                    .put("sourceField", text(field, "sourceField"))
                    .put("title", text(field, "title"))
                    .put("equal", comparison.columnsEqual().get(index))
                    .put("changedIds", comparison.changedIdsByColumn().get(index));
        }
        beforePostcheck.run();
        source.read(8388608, budget);
        workbook.read(8388608, budget);
        catalog();
        result.put("pinsVerifiedBeforeAndAfter", true);
        return result;
    }

    private static EffectiveTotals effectiveTotals(
            final JsonNode fields,
            final ExactExportTable source,
            final ExactExportTable nativeTable,
            final ExactExportTable.Comparison comparison) {
        final var equal = new ArrayList<Boolean>();
        final var policies = new ArrayList<String>();
        int numeric = 0;
        int absence = 0;
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index);
            if (field.required("total").booleanValue()) {
                final boolean numericEqual = comparison.totalsEqual().get(equal.size());
                final boolean absenceEqual =
                        !numericEqual
                                && absenceTotalPolicy(field)
                                && source.absentOnlyBagEquals(nativeTable, index);
                equal.add(numericEqual || absenceEqual);
                policies.add(
                        absenceEqual
                                ? text(field, "emptyAggregatePolicy")
                                : "NUMERIC_READY_REQUIRED");
                numeric += numericEqual ? 1 : 0;
                absence += absenceEqual ? 1 : 0;
            }
        }
        return new EffectiveTotals(List.copyOf(equal), List.copyOf(policies), numeric, absence);
    }

    static boolean absenceTotalPolicy(final JsonNode field) {
        if (!field.path("nativeColumn").isIntegralNumber()
                || !field.path("nativeColumn").canConvertToInt()
                || !"decimal".equals(field.path("kind").asText())
                || !field.path("total").isBoolean()
                || !field.path("total").booleanValue()
                || !"BINARY64_UNITS_129".equals(field.path("totalKind").asText())
                || !NULL_OMISSION.equals(field.path("nullPresentation").asText())
                || !"NONE".equals(field.path("emptyStringPresentation").asText())) {
            return false;
        }
        final int column = field.path("nativeColumn").intValue();
        return column == 84
                        && "mft_tl1_weight_capacity".equals(field.path("sourceField").asText())
                        && ABSENCE_TOTAL_84.equals(field.path("emptyAggregatePolicy").asText())
                || column == 86
                        && "mft_tl2_weight_capacity".equals(field.path("sourceField").asText())
                        && ABSENCE_TOTAL.equals(field.path("emptyAggregatePolicy").asText());
    }

    private static boolean emptyEnumOmission(final JsonNode field) {
        return field.path("nativeColumn").isIntegralNumber()
                && field.path("nativeColumn").intValue() == 46
                && "enum".equals(field.path("kind").asText())
                && "mft_mdr_contract_type".equals(field.path("sourceField").asText())
                && EMPTY_OMISSION.equals(field.path("emptyStringPresentation").asText());
    }

    private static void readSource(
            final byte[] bytes,
            final JsonNode fields,
            final JsonNode catalog,
            final ExactExportTable visual,
            final ExactExportTable original,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                bytes.length > 0 && bytes[bytes.length - 1] == '\n',
                "MANIFESTOS_COMPLETE_JSONL_FINAL_LF");
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
                        "MANIFESTOS_SOURCE_LINE_BOUND");
                final var record = record(line);
                final List<ExactExportTable.Atom> values = new ArrayList<>();
                final List<ExactExportTable.Atom> raw = new ArrayList<>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    require(value != null, "MANIFESTOS_REQUIRED_SOURCE_FIELD");
                    raw.add(original(value));
                    values.add(sourceValue(field, value, catalog));
                }
                original.add(raw);
                visual.add(values);
            }
        }
        require(rows > 0, "MANIFESTOS_SOURCE_ROWS_REQUIRED");
    }

    private static Map<String, Raw> record(final String line) throws IOException {
        final var result = new HashMap<String, Raw>();
        JsonNode entity = null;
        JsonNode template = null;
        boolean recordSeen = false;
        int members = 0;
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "MANIFESTOS_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && ++members <= 512,
                        "MANIFESTOS_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                final var token = parser.nextToken();
                if ("record".equals(name)) {
                    require(
                            !recordSeen && token == JsonToken.START_OBJECT,
                            "MANIFESTOS_SOURCE_RECORD_OBJECT");
                    recordSeen = true;
                    result.putAll(readRecord(parser));
                } else {
                    require(token != null, "MANIFESTOS_ENVELOPE_VALUE");
                    final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
                    if ("entity".equals(name)) {
                        entity = value;
                    } else if ("templateId".equals(name)) {
                        template = value;
                    }
                }
            }
            require(
                    parser.nextToken() == null
                            && recordSeen
                            && entity != null
                            && entity.isTextual()
                            && "MANIFESTOS".equals(entity.textValue())
                            && template != null
                            && template.isIntegralNumber()
                            && template.canConvertToInt()
                            && template.intValue() == 6399,
                    "MANIFESTOS_SOURCE_ENVELOPE_BINDING");
        }
        return result;
    }

    static Map<String, Raw> readRecord(final JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "MANIFESTOS_SOURCE_RECORD_OBJECT");
        final var result = new HashMap<String, Raw>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(
                    parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 512,
                    "MANIFESTOS_SOURCE_MEMBER_BOUND");
            final String field = parser.currentName();
            final var valueToken = parser.nextToken();
            require(valueToken != null, "MANIFESTOS_SOURCE_VALUE");
            final String numeric = valueToken.isNumeric() ? parser.getText() : null;
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && result.putIfAbsent(field, new Raw(value, numeric)) == null,
                    "MANIFESTOS_SOURCE_DUPLICATE_OR_MISSING_VALUE");
        }
        return result;
    }

    static ExactExportTable.Atom sourceValue(
            final JsonNode field, final Raw raw, final JsonNode catalog) {
        final var value = raw.value();
        if (value.isNull()) {
            final String policy = text(field, "nullPresentation");
            require(!"NONE".equals(policy), "MANIFESTOS_SOURCE_NULL_NOT_QUALIFIED");
            if (CLOSED_AT_NULL_BLANK.equals(policy)) {
                require(closedAtNullBlank(field), "MANIFESTOS_CLOSED_AT_NULL_EXACT_SCOPE");
            }
            if (absenceTotalPolicy(field)) {
                return new ExactExportTable.Atom("ABSENT", "");
            }
            return NULL_NO.equals(policy) ? literal("Não") : new ExactExportTable.Atom("NULL", "");
        }
        return switch (text(field, "kind")) {
            case "integer" -> {
                require(value.isNumber(), "MANIFESTOS_SOURCE_INTEGER_TYPE");
                yield integer(raw.numericLexeme());
            }
            case "decimal" -> {
                require(value.isTextual(), "MANIFESTOS_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(value.textValue());
            }
            case "boolean" -> {
                require(value.isBoolean(), "MANIFESTOS_SOURCE_BOOLEAN_TYPE");
                yield literal(value.booleanValue() ? "Sim" : "Não");
            }
            case "enum" -> {
                require(value.isTextual(), "MANIFESTOS_SOURCE_ENUM_TYPE");
                if (value.textValue().isEmpty() && emptyEnumOmission(field)) {
                    yield new ExactExportTable.Atom("ABSENT", "");
                }
                final var label =
                        catalog.path("enumLabels")
                                .path(text(field, "sourceField"))
                                .path(value.textValue());
                require(label.isTextual(), "MANIFESTOS_SOURCE_ENUM_UNOBSERVED");
                yield literal(label.textValue());
            }
            case "timestamp" -> {
                require(value.isTextual(), "MANIFESTOS_SOURCE_TIMESTAMP_TYPE");
                final String civil = QualifiedColetasModel.sourceTimestamp(value.textValue());
                require(
                        !LocalDateTime.parse(civil).isBefore(EPOCH.plusDays(61)),
                        "MANIFESTOS_SOURCE_TIMESTAMP_RANGE");
                yield literal(civil);
            }
            case "array" -> literal(arrayText(value));
            case "text" -> {
                require(value.isTextual(), "MANIFESTOS_SOURCE_TEXT_TYPE");
                yield value.textValue().isEmpty()
                                && EMPTY_OMISSION.equals(text(field, "emptyStringPresentation"))
                        ? new ExactExportTable.Atom("NULL", "")
                        : literal(value.textValue());
            }
            default -> throw new IllegalArgumentException("MANIFESTOS_SOURCE_KIND_UNQUALIFIED");
        };
    }

    private static String arrayText(final JsonNode value) {
        require(value.isArray() && value.size() <= 12000, "MANIFESTOS_NESTED_ARRAY_REQUIRED");
        final List<String> strings = new ArrayList<>();
        int length = 0;
        for (final var group : value) {
            require(group.isArray() && group.size() <= 12000, "MANIFESTOS_ARRAY_GROUP_REQUIRED");
            for (final var child : group) {
                require(
                        child.isTextual() && strings.size() < 12000,
                        "MANIFESTOS_ARRAY_TEXT_CHILD_REQUIRED");
                length += child.textValue().length() + (strings.isEmpty() ? 0 : 2);
                require(length <= 32768, "MANIFESTOS_ARRAY_PRESENTATION_BOUND");
                strings.add(child.textValue());
            }
        }
        return String.join(": ", strings);
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom cell, final JsonNode catalog) {
        final String kind = text(field, "kind");
        if (CLOSED_AT_NULL_BLANK.equals(field.path("nullPresentation").asText())) {
            require(closedAtNullBlank(field), "MANIFESTOS_CLOSED_AT_NULL_EXACT_SCOPE");
            if ("ABSENT".equals(cell.type())
                    || "BLANK".equals(cell.type())
                    || "NULL".equals(cell.type())) {
                require(
                        "BLANK".equals(cell.type())
                                && cell.value().isEmpty()
                                && cell.carrier().equals(text(catalog, "nativeTimestampCarrier")),
                        "MANIFESTOS_CLOSED_AT_NATIVE_BLANK_CARRIER");
                return new ExactExportTable.Atom("NULL", "");
            }
        }
        if (absenceTotalPolicy(field) && "ABSENT".equals(cell.type())) {
            require(
                    cell.value().isEmpty() && cell.carrier().isEmpty(),
                    "MANIFESTOS_NATIVE_ABSENCE_CARRIER");
            return new ExactExportTable.Atom("ABSENT", "");
        }
        if (emptyEnumOmission(field) && "ABSENT".equals(cell.type())) {
            return new ExactExportTable.Atom("ABSENT", "");
        }
        if ("ABSENT".equals(cell.type()) || "BLANK".equals(cell.type())) {
            if (NULL_OMISSION.equals(text(field, "nullPresentation"))) {
                if ("BLANK".equals(cell.type())) {
                    require(
                            cell.carrier()
                                    .equals(
                                            text(
                                                    catalog,
                                                    "timestamp".equals(kind)
                                                            ? "nativeTimestampCarrier"
                                                            : "nativeDecimalCarrier")),
                            "MANIFESTOS_NULL_BLANK_CARRIER");
                }
                return new ExactExportTable.Atom("NULL", "");
            }
            require(
                    "array".equals(kind) && "ABSENT".equals(cell.type()),
                    "MANIFESTOS_NATIVE_OMISSION_NOT_QUALIFIED");
            return literal("");
        }
        return switch (kind) {
            case "integer", "decimal", "timestamp" -> {
                require("NUMBER".equals(cell.type()), "MANIFESTOS_NATIVE_NUMERIC_TYPE");
                final String expected =
                        text(
                                catalog,
                                "timestamp".equals(kind)
                                        ? "nativeTimestampCarrier"
                                        : "integer".equals(kind)
                                                ? "nativeIntegerCarrier"
                                                : "nativeDecimalCarrier");
                require(expected.equals(cell.carrier()), "MANIFESTOS_NATIVE_EXACT_CARRIER");
                if ("integer".equals(kind)) {
                    yield integer(cell.value());
                }
                if ("decimal".equals(kind)) {
                    require(
                            QualifiedColetasModel.carrierRole("decimal", cell.carrier()),
                            "MANIFESTOS_DECIMAL_MODEL");
                    yield encoded(cell.value());
                }
                require(
                        QualifiedColetasModel.carrierRole("timestamp", cell.carrier()),
                        "MANIFESTOS_TIMESTAMP_MODEL");
                final String civil = QualifiedColetasModel.nativeTimestamp(cell.value());
                yield literal(civil);
            }
            case "text", "array", "enum", "boolean" -> {
                require("TEXT".equals(cell.type()), "MANIFESTOS_NATIVE_LITERAL_TEXT_REQUIRED");
                if ("boolean".equals(kind)) {
                    require(
                            List.of("Sim", "Não").contains(cell.value()),
                            "MANIFESTOS_NATIVE_BOOLEAN_LABEL");
                } else if ("enum".equals(kind)) {
                    boolean observed = false;
                    for (final var label :
                            catalog.path("enumLabels").required(text(field, "sourceField"))) {
                        observed |= label.isTextual() && label.textValue().equals(cell.value());
                    }
                    require(observed, "MANIFESTOS_NATIVE_ENUM_UNOBSERVED");
                }
                yield literal(cell.value());
            }
            default -> throw new IllegalArgumentException("MANIFESTOS_NATIVE_KIND_UNQUALIFIED");
        };
    }

    private static boolean closedAtNullBlank(final JsonNode field) {
        final var coordinate = field.path("nativeColumn");
        return coordinate.isIntegralNumber()
                && coordinate.canConvertToInt()
                && coordinate.intValue() == 12
                && "item_113640".equals(field.path("itemId").asText())
                && "closed_at".equals(field.path("sourceField").asText())
                && "timestamp".equals(field.path("kind").asText())
                && field.path("total").isBoolean()
                && !field.get("total").booleanValue()
                && "NONE".equals(field.path("totalKind").asText())
                && CLOSED_AT_NULL_BLANK.equals(field.path("nullPresentation").asText())
                && "NONE".equals(field.path("emptyStringPresentation").asText());
    }

    static boolean canonicalWholeSecondEncoding(final String serial, final String civil) {
        final long seconds = ChronoUnit.SECONDS.between(EPOCH, LocalDateTime.parse(civil));
        return ExactBinary64.encode(serial)
                .bits()
                .equals(
                        ExactBinary64.positiveRatioBits(
                                BigInteger.valueOf(seconds), BigInteger.valueOf(86400)));
    }

    static ExactExportTable.Atom original(final Raw raw) throws IOException {
        final var value = raw.value();
        if (raw.numericLexeme() != null) {
            return new ExactExportTable.Atom(
                    "NUMBER",
                    raw.numericLexeme(),
                    value.isIntegralNumber() ? "JSON:INTEGER" : "JSON:NUMBER");
        }
        if (value.isArray()) {
            return new ExactExportTable.Atom(
                    "TEXT", ExportValidationSupport.JSON.writeValueAsString(value), "JSON:ARRAY");
        }
        return ExactExportTable.Atom.json(value);
    }

    private static ExactExportTable.Atom integer(final String lexeme) {
        ExactBinary64.ratio(lexeme);
        return new ExactExportTable.Atom(
                "NUMBER", new BigDecimal(lexeme).toBigIntegerExact().toString());
    }

    private static ExactExportTable.Atom encoded(final String lexeme) {
        final var encoding = ExactBinary64.encode(lexeme);
        return new ExactExportTable.Atom(
                "NUMBER",
                encoding.units().signum() == 0 && encoding.bits().startsWith("8")
                        ? "-0"
                        : encoding.units().toString(),
                "BINARY64:" + encoding.bits());
    }

    private static ExactExportTable.Atom literal(final String value) {
        return new ExactExportTable.Atom("TEXT", value);
    }
}

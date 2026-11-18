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
import java.util.Set;

/**
 * Offline candidate: all 26 occurrences per row, original atoms and presentation keys kept
 * separately.
 */
public final class InventarioContentAdapter {
    private static final LocalDateTime EPOCH = LocalDate.of(1899, 12, 30).atStartOfDay();
    private static final Set<String> NULL_POLICIES =
            Set.of("STRICT", "NULL_NATIVE_ABSENT", "NULL_NATIVE_BLANK");
    private static final Set<String> EMPTY_POLICIES = Set.of("STRICT", "EMPTY_NATIVE_ABSENT");
    private static final Set<String> KINDS =
            Set.of(
                    "integer",
                    "numericDecimal",
                    "decimalString",
                    "timestamp",
                    "date",
                    "enum",
                    "text",
                    "stringArray");
    private static final List<String> UI_TITLES =
            List.of(
                    "Nº Minuta",
                    "Pagador",
                    "Remetente",
                    "Origem",
                    "Destinatário",
                    "Destino",
                    "Região Entrega",
                    "Filial Entregadora",
                    "N° Ordem",
                    "Filial",
                    "Tipo",
                    "Data/Hora início",
                    "Data/Hora fim",
                    "Status",
                    "Conferente",
                    "Notas Fiscais",
                    "Valor de NF",
                    "Peso Real",
                    "M³",
                    "Peso Taxado",
                    "Volumes",
                    "Volumes Lidos",
                    "Previsão de Entrega",
                    "Finalização da Performance",
                    "Data Última Ocorrência",
                    "Ocorrência");
    private static final Set<String> OBSERVED_SOURCE_FIELDS =
            Set.of(
                    "cnr_c_s_fit_corporation_sequence_number",
                    "cnr_c_s_fit_dpn_delivery_prediction_at",
                    "cnr_c_s_fit_dpn_performance_finished_at",
                    "cnr_c_s_fit_dyn_drt_nickname",
                    "cnr_c_s_fit_dyn_name",
                    "cnr_c_s_fit_fte_lce_occurrence_at",
                    "cnr_c_s_fit_fte_lce_ore_description",
                    "cnr_c_s_fit_invoices_mapping",
                    "cnr_c_s_fit_invoices_value",
                    "cnr_c_s_fit_invoices_volumes",
                    "cnr_c_s_fit_pyr_nickname",
                    "cnr_c_s_fit_real_weight",
                    "cnr_c_s_fit_rpt_ads_cty_name",
                    "cnr_c_s_fit_rpt_nickname",
                    "cnr_c_s_fit_sdr_ads_cty_name",
                    "cnr_c_s_fit_sdr_nickname",
                    "cnr_c_s_fit_taxed_weight",
                    "cnr_c_s_fit_total_cubic_volume",
                    "cnr_c_s_read_volumes",
                    "cnr_cis_eoe_psn_name",
                    "cnr_crn_psn_nickname",
                    "finished_at",
                    "sequence_code",
                    "started_at",
                    "status",
                    "type");

    record Raw(JsonNode value, String numericLexeme) {}

    private InventarioContentAdapter() {}

    /**
     * The caller supplies an explicitly pinned candidate; no production Native model is built in.
     */
    public static JsonNode catalog(
            final ExportValidationSupport.Pin pin, final ExportValidationSupport.Budget budget)
            throws IOException {
        final JsonNode catalog = pin.json(65536, budget);
        verifyCatalog(catalog);
        return catalog.deepCopy();
    }

    static void verifyCatalog(final JsonNode catalog) {
        require(
                catalog != null
                        && catalog.isObject()
                        && "INVENTARIO_NATIVE26_CANDIDATE_CATALOG_V1"
                                .equals(text(catalog, "schema"))
                        && InventarioIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "INVENTARIO".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 10633)
                        && exactInt(catalog.path("idColumnIndex"), 8)
                        && catalog.path("totalColumnCount").isIntegralNumber()
                        && catalog.path("totalColumnCount").canConvertToInt()
                        && catalog.path("totalColumnCount").intValue() >= 0
                        && catalog.path("totalColumnCount").intValue() <= 26
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 26
                        && Set.of("AUTHORED_SYNTHETIC", "OBSERVED_NATIVE_CANDIDATE")
                                .contains(text(catalog, "catalogNature"))
                        && catalog.path("presentationQualified").isBoolean()
                        && !catalog.get("presentationQualified").booleanValue()
                        && catalog.path("contentAccepted").isBoolean()
                        && !catalog.get("contentAccepted").booleanValue(),
                "INVENTARIO_CANDIDATE_CATALOG_SCOPE");
        final var names = new HashSet<String>();
        int coordinate = 0;
        int totals = 0;
        for (final var field : catalog.required("columns")) {
            require(
                    field.isObject()
                            && exactInt(field.path("nativeColumn"), ++coordinate)
                            && names.add(text(field, "sourceField"))
                            && field.path("total").isBoolean()
                            && UI_TITLES.get(coordinate - 1).equals(text(field, "title")),
                    "INVENTARIO_CATALOG_EXACT26_COORDINATES_TITLES");
            verifyPolicies(field);
            if (field.path("total").booleanValue()) {
                require(
                        Set.of("numericDecimal", "decimalString").contains(text(field, "kind")),
                        "INVENTARIO_CATALOG_NUMERIC_TOTAL_ONLY");
                totals++;
            }
            if (coordinate == 9) {
                require(
                        "sequence_code".equals(text(field, "sourceField"))
                                && "integer".equals(text(field, "kind")),
                        "INVENTARIO_CATALOG_SEQUENCE_COORDINATE");
            }
            if (coordinate == 12) {
                require(
                        "started_at".equals(text(field, "sourceField"))
                                && "timestamp".equals(text(field, "kind")),
                        "INVENTARIO_CATALOG_SCOPE_COORDINATE");
            }
        }
        require(
                names.equals(OBSERVED_SOURCE_FIELDS)
                        && totals == catalog.path("totalColumnCount").intValue(),
                "INVENTARIO_CATALOG_ALL26_FIELDS_AND_DECLARED_TOTALS");
        final var enums = catalog.path("enumLabels");
        require(enums.isObject(), "INVENTARIO_CATALOG_EXPLICIT_ENUM_OBJECT");
        final var entries = enums.fields();
        while (entries.hasNext()) {
            final var entry = entries.next();
            require(
                    Set.of("status", "type").contains(entry.getKey())
                            && entry.getValue().isObject()
                            && entry.getValue().size() <= 128,
                    "INVENTARIO_CATALOG_SCOPED_ENUM");
            final var labels = entry.getValue().fields();
            while (labels.hasNext()) {
                final var label = labels.next();
                require(
                        label.getKey().length() <= 128
                                && label.getValue().isTextual()
                                && !label.getValue().textValue().isEmpty()
                                && label.getValue().textValue().length() <= 256,
                        "INVENTARIO_CATALOG_ENUM_LABEL");
            }
        }
    }

    private static boolean exactInt(final JsonNode node, final int value) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == value;
    }

    private static void verifyPolicies(final JsonNode field) {
        final String kind = text(field, "kind");
        final String nullPolicy = text(field, "nullPresentation");
        final String emptyPolicy = text(field, "emptyStringPresentation");
        require(
                KINDS.contains(kind)
                        && NULL_POLICIES.contains(nullPolicy)
                        && EMPTY_POLICIES.contains(emptyPolicy),
                "INVENTARIO_EXPLICIT_FIELD_POLICY");
        require(
                !Set.of("started_at", "sequence_code").contains(text(field, "sourceField"))
                        || "STRICT".equals(nullPolicy) && "STRICT".equals(emptyPolicy),
                "INVENTARIO_SCOPE_KEY_STRICT");
        final boolean numeric =
                Set.of("integer", "numericDecimal", "decimalString", "timestamp", "date")
                        .contains(kind);
        if (numeric) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").startsWith("XLSX:1900:")
                            && text(field, "nativeCarrier").length() <= 256,
                    "INVENTARIO_EXPLICIT_NATIVE_CARRIER");
        } else {
            require(
                    !field.has("nativeCarrier") && !"NULL_NATIVE_BLANK".equals(nullPolicy),
                    "INVENTARIO_NONNUMERIC_NO_NATIVE_CARRIER");
        }
        if (Set.of("numericDecimal", "decimalString").contains(kind)) {
            require(
                    "DIRECT_BINARY64_NEAREST_EVEN_V1".equals(text(field, "nativeNumberEncoding")),
                    "INVENTARIO_EXPLICIT_NUMERIC_ENCODING");
        } else {
            require(!field.has("nativeNumberEncoding"), "INVENTARIO_NO_UNUSED_NUMERIC_ENCODING");
        }
        if ("stringArray".equals(kind)) {
            require(
                    "cnr_c_s_fit_invoices_mapping".equals(text(field, "sourceField"))
                            && field.path("arraySeparator").isTextual()
                            && field.get("arraySeparator").textValue().length() <= 16
                            && "STRICT".equals(emptyPolicy),
                    "INVENTARIO_EXPLICIT_ARRAY_PRESENTATION");
        } else {
            require(!field.has("arraySeparator"), "INVENTARIO_NO_UNUSED_ARRAY_SEPARATOR");
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
        result.put("catalogSha256", catalogPin.sha256()).put("catalogPinnedExplicitly", true);
        result.set("catalogPin", ExportValidationSupport.JSON.valueToTree(catalogPin));
        return result;
    }

    /**
     * Authored-catalogue test seam. The public entry point always loads an explicit pinned
     * candidate.
     */
    static ObjectNode compareWithCatalog(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget,
            final JsonNode catalog,
            final Runnable beforePostcheck)
            throws Exception {
        InventarioIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        InventarioIndependentRequest.validate(admitted);
        verifyCatalog(catalog);
        final JsonNode fields = catalog.required("columns");
        final List<ExactExportTable.Column> columns = new ArrayList<>();
        final List<ExactExportTable.Column> rawColumns = new ArrayList<>();
        for (final var field : fields) {
            final int column = field.get("nativeColumn").intValue();
            final boolean total = field.get("total").booleanValue();
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "title"),
                            column,
                            column,
                            total,
                            total ? "BINARY64_UNITS_129" : "NONE"));
            rawColumns.add(
                    new ExactExportTable.Column(text(field, "title"), column, column, false));
        }
        final byte[] sourceBytes = source.read(8388608, budget);
        final byte[] workbookBytes = workbook.read(8388608, budget);
        final var nativeTable =
                BoundedExportWorkbook.readInventarioRows(workbookBytes, columns, budget);
        require("1900".equals(nativeTable.epoch()), "INVENTARIO_QUALIFIED_EPOCH_1900_ONLY");
        require(!nativeTable.rows().isEmpty(), "INVENTARIO_NATIVE_ROWS_REQUIRED");
        final var sourceValues = new ExactExportTable(columns, 8, budget);
        final var nativeValues = new ExactExportTable(columns, 8, budget);
        final var sourceOriginal = new ExactExportTable(rawColumns, 8, budget);
        final var nativeOriginal = new ExactExportTable(rawColumns, 8, budget);
        readSource(sourceBytes, admitted, fields, catalog, sourceValues, sourceOriginal, budget);
        int timestamps = 0;
        int noncanonical = 0;
        for (final var row : nativeTable.rows()) {
            budget.check();
            require(row.cells().size() == 26, "INVENTARIO_ALL26_NATIVE_CELLS");
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            for (int index = 0; index < fields.size(); index++) {
                final var field = fields.get(index);
                final var atom = row.cells().get(index);
                final var value = nativeValue(field, atom, catalog);
                values.add(value);
                if (index == 11) {
                    verifyStartedAt(value, admitted);
                }
                if ("timestamp".equals(text(field, "kind")) && "NUMBER".equals(atom.type())) {
                    timestamps++;
                    noncanonical +=
                            canonicalWholeSecondEncoding(atom.value(), value.value()) ? 0 : 1;
                }
            }
            nativeOriginal.add(row.cells());
            nativeValues.add(values);
        }
        for (final var table :
                List.of(sourceValues, nativeValues, sourceOriginal, nativeOriginal)) {
            table.seal();
        }
        final var comparison = sourceValues.compare(nativeValues);
        final boolean equal =
                comparison.apiRows() > 0
                        && comparison.apiRows() == comparison.uiRows()
                        && comparison.fullTuplesEqual()
                        && comparison.columnsEqual().size() == 26
                        && comparison.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && comparison.apiOnlyOccurrences() == 0
                        && comparison.uiOnlyOccurrences() == 0
                        && comparison.changedIds() == 0
                        && comparison.changedMultiplicityIds() == 0
                        && comparison.apiOnlyIds() == 0
                        && comparison.uiOnlyIds() == 0
                        && comparison.apiDistinctIds() == comparison.uiDistinctIds()
                        && comparison.totalsEqual().size()
                                == catalog.path("totalColumnCount").intValue()
                        && comparison.totalsEqual().stream().allMatch(Boolean::booleanValue);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INVENTARIO_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", InventarioIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put(
                                "catalogSha256",
                                ExportValidationSupport.sha256(
                                        ExportValidationSupport.canonicalBytes(catalog)))
                        .put("catalogNature", text(catalog, "catalogNature"))
                        .put("catalogBindingPoliciesQualified", false)
                        .put("columns", 26)
                        .put("totalColumns", catalog.path("totalColumnCount").intValue())
                        .put("sourceRows", comparison.apiRows())
                        .put("uiRows", comparison.uiRows())
                        .put("allOccurrencesCompared", true)
                        .put("pendingCellDecisions", 0)
                        .put("contentEqualUnderCandidatePresentation", equal)
                        .put(
                                "status",
                                equal
                                        ? "CONTENT_EQUAL_UNDER_CANDIDATE_PRESENTATION"
                                        : "PENDING_DIFFERENCE_INVESTIGATION")
                        .put("diagnosticOnly", true)
                        .put("presentationPoliciesQualified", false)
                        .put("contentAccepted", false)
                        .put("trustedSourceCompleteness", false)
                        .put("sourceCompletenessProven", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("deduplicationPerformed", false)
                        .put("startedAtDateVerifiedForEveryRowBothSides", true)
                        .put("snapshotIdentityProven", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("productionAuthorized", false)
                        .put("persistenceAuthorized", false)
                        .put("runtimeAdopted", false)
                        .put("sourceQueryAuthorized", false)
                        .put("rawPayloadAndExtraFieldsRetainedInPinnedSource", true)
                        .put("sourceSha256", source.sha256())
                        .put("workbookSha256", workbook.sha256())
                        .put("nativeTimestampCells", timestamps)
                        .put("nativeTimestampNoncanonicalWholeSecondEncodings", noncanonical)
                        .put("originalTimestampEncodingRetained", true)
                        .put("sourceArrayOrderAndDuplicatesRetainedInOriginalProjection", true)
                        .put("arrayPresentationMayBeNonInjective", true)
                        .put("nativeTimestampEncodingEqualityRequiredForVisualKey", false)
                        .put("floatArithmeticUsed", false)
                        .put("epsilonUsed", false);
        result.putObject("limits")
                .put("maximumRowsPerSide", 12000)
                .put("maximumBytesPerInput", 8388608)
                .put("maximumJsonLineCharacters", 262144)
                .put("maximumLogicalCellVisits", 1200000)
                .put("cellVisitModel", "NATIVE_READ_PLUS_TWO_AXES_PER_SIDE")
                .put("budgetSharedWithCaller", true)
                .put("earlierBudgetRefusalPossible", true);
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        final var projection = result.putObject("fullValueProjection");
        projection.set("api", sourceValues.fullValueProjection());
        projection.set("ui", nativeValues.fullValueProjection());
        final var original = result.putObject("originalValueProjection");
        original.set("api", sourceOriginal.fullValueProjection());
        original.set("ui", nativeOriginal.fullValueProjection());
        final var diagnostics = result.putArray("columnDiagnostics");
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index);
            diagnostics
                    .addObject()
                    .put("index", index)
                    .put("nativeColumn", index + 1)
                    .put("sourceField", text(field, "sourceField"))
                    .put("title", text(field, "title"))
                    .put("kind", text(field, "kind"))
                    .put("nullPresentation", text(field, "nullPresentation"))
                    .put("emptyStringPresentation", text(field, "emptyStringPresentation"))
                    .put("nativeNumberEncoding", field.path("nativeNumberEncoding").asText("NONE"))
                    .put("equal", comparison.columnsEqual().get(index))
                    .put("changedIds", comparison.changedIdsByColumn().get(index));
        }
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
            final ExactExportTable values,
            final ExactExportTable original,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                bytes.length > 0 && bytes[bytes.length - 1] == '\n',
                "INVENTARIO_COMPLETE_JSONL_FINAL_LF");
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
                        "INVENTARIO_SOURCE_LINE_BOUND");
                final var record = record(line);
                final var expectedNames = new HashSet<String>();
                fields.forEach(field -> expectedNames.add(text(field, "sourceField")));
                require(record.keySet().equals(expectedNames), "INVENTARIO_SOURCE_EXACT26_FIELDS");
                final List<ExactExportTable.Atom> presented = new ArrayList<>();
                final List<ExactExportTable.Atom> raw = new ArrayList<>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    require(value != null, "INVENTARIO_REQUIRED_SOURCE_FIELD");
                    raw.add(original(value));
                    final var key = sourceValue(field, value, catalog);
                    presented.add(key);
                    if ("started_at".equals(text(field, "sourceField"))) {
                        verifyStartedAt(key, request);
                    }
                }
                original.add(raw);
                values.add(presented);
            }
        }
        require(rows > 0, "INVENTARIO_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "INVENTARIO_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "INVENTARIO_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(names.add(name), "INVENTARIO_ENVELOPE_DUPLICATE");
                final var token = parser.nextToken();
                require(token != null, "INVENTARIO_ENVELOPE_VALUE");
                switch (name) {
                    case "record" -> result = readRecord(parser);
                    case "entity" -> entity = ExportValidationSupport.JSON.readTree(parser);
                    case "templateId" -> template = ExportValidationSupport.JSON.readTree(parser);
                    default ->
                            throw new IllegalArgumentException(
                                    "INVENTARIO_ENVELOPE_UNKNOWN_MEMBER");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "INVENTARIO".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 10633),
                    "INVENTARIO_SOURCE_ENVELOPE_BINDING");
        }
        return result;
    }

    private static Map<String, Raw> readRecord(final JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "INVENTARIO_SOURCE_RECORD_OBJECT");
        final var result = new HashMap<String, Raw>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(
                    parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 512,
                    "INVENTARIO_SOURCE_MEMBER_BOUND");
            final String name = parser.currentName();
            final var token = parser.nextToken();
            require(token != null, "INVENTARIO_SOURCE_VALUE");
            final String numeric = token.isNumeric() ? parser.getText() : null;
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && result.putIfAbsent(name, new Raw(value, numeric)) == null,
                    "INVENTARIO_SOURCE_DUPLICATE_OR_MISSING_VALUE");
        }
        return result;
    }

    static ExactExportTable.Atom sourceValue(
            final JsonNode field, final Raw raw, final JsonNode catalog) {
        verifyPolicies(field);
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
            case "integer" -> {
                require(
                        value.isIntegralNumber()
                                && value.canConvertToLong()
                                && value.longValue() > 0,
                        "INVENTARIO_SOURCE_POSITIVE_INTEGER_KEY");
                yield integer(raw.numericLexeme());
            }
            case "numericDecimal" -> {
                require(
                        value.isNumber() && raw.numericLexeme() != null,
                        "INVENTARIO_SOURCE_NUMERIC_DECIMAL_TYPE");
                yield encoded(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "INVENTARIO_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(value.textValue());
            }
            case "timestamp" -> {
                require(value.isTextual(), "INVENTARIO_SOURCE_TIMESTAMP_TYPE");
                final String civil = QualifiedColetasModel.sourceTimestamp(value.textValue());
                require(
                        !LocalDateTime.parse(civil).isBefore(EPOCH.plusDays(61)),
                        "INVENTARIO_SOURCE_TIMESTAMP_RANGE");
                yield literal(civil);
            }
            case "date" -> {
                require(value.isTextual(), "INVENTARIO_SOURCE_DATE_TYPE");
                final String civil = QualifiedColetasModel.sourceCivil(value.textValue());
                require(
                        !LocalDate.parse(civil).isBefore(EPOCH.toLocalDate().plusDays(61)),
                        "INVENTARIO_SOURCE_DATE_RANGE");
                yield literal(civil);
            }
            case "enum" -> {
                require(value.isTextual(), "INVENTARIO_SOURCE_ENUM_TYPE");
                final var label =
                        catalog.path("enumLabels")
                                .path(text(field, "sourceField"))
                                .path(value.textValue());
                require(label.isTextual(), "INVENTARIO_SOURCE_ENUM_UNOBSERVED");
                yield literal(label.textValue());
            }
            case "text" -> {
                require(value.isTextual(), "INVENTARIO_SOURCE_TEXT_TYPE");
                yield literal(value.textValue());
            }
            case "stringArray" -> {
                require(
                        value.isArray() && value.size() <= 1024,
                        "INVENTARIO_SOURCE_ARRAY_TYPE_BOUND");
                final var items = new ArrayList<String>();
                for (final var item : value) {
                    require(item.isTextual(), "INVENTARIO_SOURCE_ARRAY_STRING_ITEM");
                    items.add(item.textValue());
                }
                yield literal(String.join(field.get("arraySeparator").textValue(), items));
            }
            default -> throw new IllegalArgumentException("INVENTARIO_SOURCE_KIND_UNQUALIFIED");
        };
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicies(field);
        final String kind = text(field, "kind");
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "INVENTARIO_NATIVE_ABSENT_EXACT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "INVENTARIO_NATIVE_BLANK_EXACT_CARRIER");
            final String policy = text(field, "nullPresentation");
            return "NULL_NATIVE_BLANK".equals(policy)
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        return switch (kind) {
            case "integer", "numericDecimal", "decimalString", "timestamp", "date" -> {
                require("NUMBER".equals(atom.type()), "INVENTARIO_NATIVE_NUMERIC_TYPE");
                final String carrier = text(field, "nativeCarrier");
                require(carrier.equals(atom.carrier()), "INVENTARIO_NATIVE_EXACT_CARRIER");
                yield switch (kind) {
                    case "integer" -> integer(atom.value());
                    case "date" -> literal(QualifiedColetasModel.nativeCivil(atom.value()));
                    case "timestamp" ->
                            literal(QualifiedColetasModel.nativeTimestamp(atom.value()));
                    default -> encoded(atom.value());
                };
            }
            case "text", "enum", "stringArray" -> {
                require(
                        "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                        "INVENTARIO_NATIVE_LITERAL_TEXT_REQUIRED");
                if ("enum".equals(kind)) {
                    boolean observed = false;
                    for (final var label :
                            catalog.path("enumLabels").path(text(field, "sourceField"))) {
                        observed |= label.isTextual() && label.textValue().equals(atom.value());
                    }
                    require(observed, "INVENTARIO_NATIVE_ENUM_UNOBSERVED");
                }
                yield literal(atom.value());
            }
            default -> throw new IllegalArgumentException("INVENTARIO_NATIVE_KIND_UNQUALIFIED");
        };
    }

    private static void verifyStartedAt(final ExactExportTable.Atom atom, final JsonNode request) {
        require(
                "TEXT".equals(atom.type()) && atom.value().length() == 19,
                "INVENTARIO_STARTED_AT_VALUE_REQUIRED");
        final LocalDate day = LocalDateTime.parse(atom.value()).toLocalDate();
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "INVENTARIO_STARTED_AT_OUTSIDE_PERIOD");
    }

    static ExactExportTable.Atom original(final Raw raw) {
        if (raw.numericLexeme() != null) {
            return new ExactExportTable.Atom(
                    "NUMBER",
                    raw.numericLexeme(),
                    raw.value().isIntegralNumber() ? "JSON:INTEGER" : "JSON:NUMBER");
        }
        if (raw.value().isArray()) {
            return new ExactExportTable.Atom("TEXT", raw.value().toString(), "JSON:ARRAY");
        }
        return ExactExportTable.Atom.json(raw.value());
    }

    private static ExactExportTable.Atom integer(final String lexeme) {
        ExactBinary64.ratio(lexeme);
        final var value = new BigDecimal(lexeme).toBigIntegerExact();
        require(
                value.signum() > 0 && value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0,
                "INVENTARIO_NATIVE_POSITIVE_INTEGER_KEY");
        return new ExactExportTable.Atom("NUMBER", value.toString());
    }

    private static ExactExportTable.Atom encoded(final String lexeme) {
        return encoded(ExactBinary64.encode(lexeme));
    }

    private static ExactExportTable.Atom encoded(final ExactBinary64.Encoding encoding) {
        return new ExactExportTable.Atom(
                "NUMBER",
                encoding.units().signum() == 0 && encoding.bits().startsWith("8")
                        ? "-0"
                        : encoding.units().toString(),
                "BINARY64:" + encoding.bits());
    }

    static boolean canonicalWholeSecondEncoding(final String serial, final String civil) {
        final long seconds = ChronoUnit.SECONDS.between(EPOCH, LocalDateTime.parse(civil));
        return ExactBinary64.encode(serial)
                .bits()
                .equals(
                        ExactBinary64.positiveRatioBits(
                                BigInteger.valueOf(seconds), BigInteger.valueOf(86400)));
    }

    private static ExactExportTable.Atom literal(final String value) {
        return new ExactExportTable.Atom("TEXT", value);
    }
}

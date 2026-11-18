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
 * Offline candidate: all 37 occurrences per row, original atoms and presentation keys kept
 * separately.
 */
public final class CotacoesContentAdapter {
    static final String CATALOG_SHA =
            "d06364a246101181de1f69b6a91dfeb2bd33f86887ed4d76e2579aea430d27d6";
    static final String NUMBER_CARRIER = "XLSX:1900:builtin:0:apply=null:base=0:fmtId=0";
    static final String TIMESTAMP_CARRIER =
            "XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164";
    static final String DATE_CARRIER = "XLSX:1900:DD/MM/YYYY:apply=1:base=0:fmtId=165";
    private static final LocalDateTime EPOCH = LocalDate.of(1899, 12, 30).atStartOfDay();
    private static final Set<String> NULL_POLICIES =
            Set.of(
                    "STRICT",
                    "NULL_NATIVE_ABSENT",
                    "DATE_NULL_NATIVE_BLANK",
                    "TIMESTAMP_NULL_NATIVE_BLANK");
    private static final Set<String> EMPTY_POLICIES = Set.of("STRICT", "EMPTY_NATIVE_ABSENT");
    // Generated solely from the pinned catalogue, never from business rows or workbook contents.
    private static final List<String> FIELD_BINDINGS =
            List.of(
                    "qoe_crn_psn_name|Empresa|text",
                    "requested_at|Data Cotação|timestamp",
                    "sequence_code|N° Cotação|integer",
                    "qoe_qes_fon_name|Tipo de operação|text",
                    "qoe_cor_document|CNPJ/CPF Cliente|text",
                    "qoe_cor_name|Cliente Pagador|text",
                    "qoe_qes_ony_name|Cidade Origem|text",
                    "qoe_qes_ony_sae_code|UF Origem|text",
                    "qoe_qes_diy_name|Cidade Destino|text",
                    "qoe_qes_diy_sae_code|UF Destino|text",
                    "qoe_qes_cre_name|Tabela|text",
                    "qoe_qes_invoices_volumes|Volume|numericDecimal",
                    "qoe_qes_taxed_weight|Peso taxado|decimalString",
                    "qoe_qes_invoices_value|Valor NF|decimalString",
                    "qoe_qes_total|Valor frete|decimalString",
                    "qoe_qes_status|Status|enum",
                    "qoe_qes_fit_fhe_cte_issued_at|CT-e/Data de emissão|timestamp",
                    "qoe_qes_fit_nse_issued_at|Nfse/Data de emissão|date",
                    "qoe_uer_name|Usuário|text",
                    "qoe_crn_psn_nickname|Filial|text",
                    "qoe_qes_sdr_document|Remetente/CNPJ|text",
                    "qoe_qes_sdr_nickname|Remetente/Nome fantasia|text",
                    "qoe_qes_rpt_document|Destinatário/CNPJ|text",
                    "qoe_qes_rpt_nickname|Destinatário/Nome fantasia|text",
                    "qoe_cor_nickname|Pagador/Nome fantasia|text",
                    "qoe_qes_origin_postal_code|CEP Origem|text",
                    "qoe_qes_destination_postal_code|CEP Destino|text",
                    "qoe_qes_real_weight|Peso real|decimalString",
                    "qoe_qes_disapprove_comments|Observações|text",
                    "qoe_qes_freight_comments|Observações para o frete|text",
                    "qoe_qes_fit_fdt_subtotal|Descontos/Subtotal parcelas|decimalString",
                    "requester_name|Solicitante|text",
                    "qoe_qes_itr_subtotal|Trechos/ITR|decimalString",
                    "qoe_qes_tde_subtotal|Trechos/TDE|decimalString",
                    "qoe_qes_collect_subtotal|Trechos/Coleta|decimalString",
                    "qoe_qes_delivery_subtotal|Trechos/Entrega|decimalString",
                    "qoe_qes_other_fees|Trechos/Outros valores|decimalString");
    private static final Set<Integer> TOTAL_COLUMNS =
            Set.of(12, 13, 14, 15, 28, 31, 33, 34, 35, 36, 37);

    record Raw(JsonNode value, String numericLexeme) {}

    private CotacoesContentAdapter() {}

    public static JsonNode catalog() throws IOException {
        try (var input =
                CotacoesContentAdapter.class.getResourceAsStream(
                        "/contracts/dataexport/cotacoes-native37-v1.json")) {
            require(input != null, "COTACOES_CATALOG_REQUIRED");
            final byte[] bytes = input.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && CATALOG_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "COTACOES_CATALOG_PIN");
            try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
                final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
                require(value != null && parser.nextToken() == null, "COTACOES_CATALOG_EOF");
                verifyCatalog(value);
                return value;
            }
        }
    }

    static void verifyCatalog(final JsonNode catalog) {
        require(
                catalog != null
                        && catalog.isObject()
                        && "COTACOES_NATIVE37_CATALOG_V1".equals(text(catalog, "schema"))
                        && CotacoesIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "COTACOES".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 6906)
                        && exactInt(catalog.path("idColumnIndex"), 2)
                        && exactInt(catalog.path("totalColumnCount"), 11)
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 37
                        && catalog.path("presentationQualified").isBoolean()
                        && !catalog.get("presentationQualified").booleanValue()
                        && catalog.path("contentAccepted").isBoolean()
                        && !catalog.get("contentAccepted").booleanValue(),
                "COTACOES_CANDIDATE_CATALOG_SCOPE");
        final var names = new HashSet<String>();
        int coordinate = 0;
        for (final var field : catalog.required("columns")) {
            require(
                    field.isObject()
                            && exactInt(field.path("nativeColumn"), ++coordinate)
                            && names.add(text(field, "sourceField"))
                            && field.path("total").isBoolean()
                            && FIELD_BINDINGS
                                    .get(coordinate - 1)
                                    .equals(
                                            text(field, "sourceField")
                                                    + "|"
                                                    + text(field, "title")
                                                    + "|"
                                                    + text(field, "kind"))
                            && field.get("total").booleanValue()
                                    == TOTAL_COLUMNS.contains(coordinate),
                    "COTACOES_CATALOG_EXACT37_BINDINGS");
            verifyPolicies(field);
        }
        final var enums = catalog.path("enumLabels");
        require(enums.isMissingNode() || enums.isObject(), "COTACOES_CATALOG_ENUM_OBJECT");
        if (enums.isObject()) {
            final var entries = enums.fields();
            while (entries.hasNext()) {
                final var entry = entries.next();
                require(
                        "qoe_qes_status".equals(entry.getKey())
                                && entry.getValue().isObject()
                                && entry.getValue().size() <= 128,
                        "COTACOES_CATALOG_SCOPED_ENUM");
                final var labels = entry.getValue().fields();
                while (labels.hasNext()) {
                    final var label = labels.next();
                    require(
                            label.getKey().length() <= 128
                                    && label.getValue().isTextual()
                                    && !label.getValue().textValue().isEmpty()
                                    && label.getValue().textValue().length() <= 256,
                            "COTACOES_CATALOG_ENUM_LABEL");
                }
            }
        }
    }

    private static boolean exactInt(final JsonNode node, final int value) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == value;
    }

    private static void verifyPolicies(final JsonNode field) {
        CotacoesNativeNumericEncoding.policy(field);
        final String kind = text(field, "kind");
        final String nullPolicy = text(field, "nullPresentation");
        final String emptyPolicy = text(field, "emptyStringPresentation");
        require(
                NULL_POLICIES.contains(nullPolicy) && EMPTY_POLICIES.contains(emptyPolicy),
                "COTACOES_EXPLICIT_FIELD_POLICY");
        require(
                !Set.of("requested_at", "sequence_code").contains(text(field, "sourceField"))
                        || "STRICT".equals(nullPolicy) && "STRICT".equals(emptyPolicy),
                "COTACOES_SCOPE_KEY_STRICT");
        if ("DATE_NULL_NATIVE_BLANK".equals(nullPolicy)
                || "TIMESTAMP_NULL_NATIVE_BLANK".equals(nullPolicy)) {
            final boolean date = "DATE_NULL_NATIVE_BLANK".equals(nullPolicy);
            require(
                    (date ? "date" : "timestamp").equals(kind)
                            && field.path("nativeNullCarrier").isTextual()
                            && (date ? DATE_CARRIER : TIMESTAMP_CARRIER)
                                    .equals(field.get("nativeNullCarrier").textValue()),
                    "COTACOES_NULL_BLANK_EXACT_CARRIER_POLICY");
        } else {
            require(!field.has("nativeNullCarrier"), "COTACOES_NO_UNUSED_NULL_CARRIER");
        }
        if ("EMPTY_NATIVE_ABSENT".equals(emptyPolicy)) {
            require(
                    Set.of("text", "enum", "decimalString", "timestamp", "date").contains(kind),
                    "COTACOES_EMPTY_STRING_SOURCE_KIND");
        }
    }

    public static ObjectNode compare(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return compareWithPostcheckProbe(request, source, workbook, budget, () -> {});
    }

    static ObjectNode compareWithPostcheckProbe(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        return compareWithCatalog(request, source, workbook, budget, catalog(), beforePostcheck);
    }

    /** Authored-catalogue test seam. The public entry point always loads the pinned resource. */
    static ObjectNode compareWithCatalog(
            final JsonNode request,
            final ExportValidationSupport.Pin source,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Budget budget,
            final JsonNode catalog,
            final Runnable beforePostcheck)
            throws Exception {
        CotacoesIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        CotacoesIndependentRequest.validate(admitted);
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
                BoundedExportWorkbook.readCotacoesRows(workbookBytes, columns, budget);
        require("1900".equals(nativeTable.epoch()), "COTACOES_QUALIFIED_EPOCH_1900_ONLY");
        require(!nativeTable.rows().isEmpty(), "COTACOES_NATIVE_ROWS_REQUIRED");
        final var sourceValues = new ExactExportTable(columns, 2, budget);
        final var nativeValues = new ExactExportTable(columns, 2, budget);
        final var sourceOriginal = new ExactExportTable(rawColumns, 2, budget);
        final var nativeOriginal = new ExactExportTable(rawColumns, 2, budget);
        readSource(sourceBytes, admitted, fields, catalog, sourceValues, sourceOriginal, budget);
        int timestamps = 0;
        int noncanonical = 0;
        for (final var row : nativeTable.rows()) {
            budget.check();
            require(row.cells().size() == 37, "COTACOES_ALL37_NATIVE_CELLS");
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            for (int index = 0; index < fields.size(); index++) {
                final var field = fields.get(index);
                final var atom = row.cells().get(index);
                final var value = nativeValue(field, atom, catalog);
                values.add(value);
                if (index == 1) {
                    verifyRequestedAt(value, admitted);
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
                        && comparison.columnsEqual().size() == 37
                        && comparison.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && comparison.apiOnlyOccurrences() == 0
                        && comparison.uiOnlyOccurrences() == 0
                        && comparison.changedIds() == 0
                        && comparison.changedMultiplicityIds() == 0
                        && comparison.apiOnlyIds() == 0
                        && comparison.uiOnlyIds() == 0
                        && comparison.apiDistinctIds() == comparison.uiDistinctIds()
                        && comparison.totalsEqual().size() == 11
                        && comparison.totalsEqual().stream().allMatch(Boolean::booleanValue);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", CotacoesIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("catalogSha256", CATALOG_SHA)
                        .put("columns", 37)
                        .put("totalColumns", 11)
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
                        .put("requestedAtDateVerifiedForEveryRowBothSides", true)
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
                    .put("nativeNumberEncoding", CotacoesNativeNumericEncoding.policy(field))
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
                "COTACOES_COMPLETE_JSONL_FINAL_LF");
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
                        "COTACOES_SOURCE_LINE_BOUND");
                final var record = record(line);
                final List<ExactExportTable.Atom> presented = new ArrayList<>();
                final List<ExactExportTable.Atom> raw = new ArrayList<>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    require(value != null, "COTACOES_REQUIRED_SOURCE_FIELD");
                    raw.add(original(value));
                    final var key = sourceValue(field, value, catalog);
                    presented.add(key);
                    if ("requested_at".equals(text(field, "sourceField"))) {
                        verifyRequestedAt(key, request);
                    }
                }
                original.add(raw);
                values.add(presented);
            }
        }
        require(rows > 0, "COTACOES_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "COTACOES_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "COTACOES_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(names.add(name), "COTACOES_ENVELOPE_DUPLICATE");
                final var token = parser.nextToken();
                require(token != null, "COTACOES_ENVELOPE_VALUE");
                switch (name) {
                    case "record" -> result = readRecord(parser);
                    case "entity" -> entity = ExportValidationSupport.JSON.readTree(parser);
                    case "templateId" -> template = ExportValidationSupport.JSON.readTree(parser);
                    default ->
                            throw new IllegalArgumentException("COTACOES_ENVELOPE_UNKNOWN_MEMBER");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "COTACOES".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 6906),
                    "COTACOES_SOURCE_ENVELOPE_BINDING");
        }
        return result;
    }

    private static Map<String, Raw> readRecord(final JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "COTACOES_SOURCE_RECORD_OBJECT");
        final var result = new HashMap<String, Raw>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(
                    parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 512,
                    "COTACOES_SOURCE_MEMBER_BOUND");
            final String name = parser.currentName();
            final var token = parser.nextToken();
            require(token != null, "COTACOES_SOURCE_VALUE");
            final String numeric = token.isNumeric() ? parser.getText() : null;
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && result.putIfAbsent(name, new Raw(value, numeric)) == null,
                    "COTACOES_SOURCE_DUPLICATE_OR_MISSING_VALUE");
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
                        "COTACOES_SOURCE_POSITIVE_INTEGER_KEY");
                yield integer(raw.numericLexeme());
            }
            case "numericDecimal" -> {
                require(
                        value.isNumber() && raw.numericLexeme() != null,
                        "COTACOES_SOURCE_NUMERIC_DECIMAL_TYPE");
                yield encoded(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "COTACOES_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(CotacoesNativeNumericEncoding.source(field, value.textValue()));
            }
            case "timestamp" -> {
                require(value.isTextual(), "COTACOES_SOURCE_TIMESTAMP_TYPE");
                final String civil = QualifiedColetasModel.sourceTimestamp(value.textValue());
                require(
                        !LocalDateTime.parse(civil).isBefore(EPOCH.plusDays(61)),
                        "COTACOES_SOURCE_TIMESTAMP_RANGE");
                yield literal(civil);
            }
            case "date" -> {
                require(value.isTextual(), "COTACOES_SOURCE_DATE_TYPE");
                final String civil = QualifiedColetasModel.sourceCivil(value.textValue());
                require(
                        !LocalDate.parse(civil).isBefore(EPOCH.toLocalDate().plusDays(61)),
                        "COTACOES_SOURCE_DATE_RANGE");
                yield literal(civil);
            }
            case "enum" -> {
                require(value.isTextual(), "COTACOES_SOURCE_ENUM_TYPE");
                final var label =
                        catalog.path("enumLabels")
                                .path(text(field, "sourceField"))
                                .path(value.textValue());
                require(label.isTextual(), "COTACOES_SOURCE_ENUM_UNOBSERVED");
                yield literal(label.textValue());
            }
            case "text" -> {
                require(value.isTextual(), "COTACOES_SOURCE_TEXT_TYPE");
                yield literal(value.textValue());
            }
            default -> throw new IllegalArgumentException("COTACOES_SOURCE_KIND_UNQUALIFIED");
        };
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicies(field);
        final String kind = text(field, "kind");
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "COTACOES_NATIVE_ABSENT_EXACT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    ("date".equals(kind)
                                    ? DATE_CARRIER
                                    : "timestamp".equals(kind) ? TIMESTAMP_CARRIER : NUMBER_CARRIER)
                            .equals(atom.carrier()),
                    "COTACOES_NATIVE_BLANK_EXACT_CARRIER");
            final String policy = text(field, "nullPresentation");
            return "DATE_NULL_NATIVE_BLANK".equals(policy)
                            || "TIMESTAMP_NULL_NATIVE_BLANK".equals(policy)
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        return switch (kind) {
            case "integer", "numericDecimal", "decimalString", "timestamp", "date" -> {
                require("NUMBER".equals(atom.type()), "COTACOES_NATIVE_NUMERIC_TYPE");
                final String carrier =
                        "date".equals(kind)
                                ? DATE_CARRIER
                                : "timestamp".equals(kind) ? TIMESTAMP_CARRIER : NUMBER_CARRIER;
                require(carrier.equals(atom.carrier()), "COTACOES_NATIVE_EXACT_CARRIER");
                yield switch (kind) {
                    case "integer" -> integer(atom.value());
                    case "date" -> literal(QualifiedColetasModel.nativeCivil(atom.value()));
                    case "timestamp" ->
                            literal(QualifiedColetasModel.nativeTimestamp(atom.value()));
                    default -> encoded(atom.value());
                };
            }
            case "text", "enum" -> {
                require(
                        "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                        "COTACOES_NATIVE_LITERAL_TEXT_REQUIRED");
                if ("enum".equals(kind)) {
                    boolean observed = false;
                    for (final var label :
                            catalog.path("enumLabels").path(text(field, "sourceField"))) {
                        observed |= label.isTextual() && label.textValue().equals(atom.value());
                    }
                    require(observed, "COTACOES_NATIVE_ENUM_UNOBSERVED");
                }
                yield literal(atom.value());
            }
            default -> throw new IllegalArgumentException("COTACOES_NATIVE_KIND_UNQUALIFIED");
        };
    }

    private static void verifyRequestedAt(
            final ExactExportTable.Atom atom, final JsonNode request) {
        require(
                "TEXT".equals(atom.type()) && atom.value().length() == 19,
                "COTACOES_REQUESTED_AT_VALUE_REQUIRED");
        final LocalDate day = LocalDateTime.parse(atom.value()).toLocalDate();
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "COTACOES_REQUESTED_AT_OUTSIDE_PERIOD");
    }

    static ExactExportTable.Atom original(final Raw raw) {
        if (raw.numericLexeme() != null) {
            return new ExactExportTable.Atom(
                    "NUMBER",
                    raw.numericLexeme(),
                    raw.value().isIntegralNumber() ? "JSON:INTEGER" : "JSON:NUMBER");
        }
        return ExactExportTable.Atom.json(raw.value());
    }

    private static ExactExportTable.Atom integer(final String lexeme) {
        ExactBinary64.ratio(lexeme);
        final var value = new BigDecimal(lexeme).toBigIntegerExact();
        require(
                value.signum() > 0 && value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0,
                "COTACOES_NATIVE_POSITIVE_INTEGER_KEY");
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

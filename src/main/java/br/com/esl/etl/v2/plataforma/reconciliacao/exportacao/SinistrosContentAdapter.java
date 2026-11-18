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
 * Offline candidate: all 44 occurrences per row, original atoms and presentation keys kept
 * separately.
 */
public final class SinistrosContentAdapter {
    private static final LocalDateTime EPOCH = LocalDate.of(1899, 12, 30).atStartOfDay();
    private static final Set<String> NULL_POLICIES =
            Set.of("STRICT", "NULL_NATIVE_ABSENT", "NULL_NATIVE_BLANK");
    private static final Set<String> EMPTY_POLICIES = Set.of("STRICT", "EMPTY_NATIVE_ABSENT");
    private static final Set<String> KINDS =
            Set.of(
                    "positiveIntegralNumber",
                    "numericDecimal",
                    "decimalString",
                    "timestamp",
                    "date",
                    "time",
                    "enum",
                    "text",
                    "stringArray");
    static final List<String> BINDINGS =
            List.of(
                    "sequence_code|Nº do Sinistro|positiveIntegralNumber",
                    "opening_at_date|Data abertura|date",
                    "occurrence_at_date|Data ocorrência|date",
                    "occurrence_at_time|Hora ocorrência|time",
                    "expected_solution_date|Previsão de solução|date",
                    "insurance_claim_location|Local do sinistro|text",
                    "informed_by|Informado por|text",
                    "rctrc|RCTRC|stringArray",
                    "rctac|RCTAC|stringArray",
                    "rcfdc|RCFDC|stringArray",
                    "insurance_claim_commentary|Comentário do sinistro|text",
                    "list_of_claimed_products|Relação dos produtos sinistrados|text",
                    "customer_communication_contact_name|Contato/Nome|text",
                    "customer_communication_date|Contato/Data|date",
                    "customer_communication_time|Contato/Hora|time",
                    "brokers_case_number|Nº do processo da corretora|text",
                    "bo_number|Nº do BO|text",
                    "policy_number|Nº da apólice|text",
                    "finished_at_date|Data finalização|date",
                    "finished_at_time|Hora finalização|time",
                    "finished_commentary|Comentário da finalização|text",
                    "internal_description|Descrição interna|text",
                    "invoices_count|Quantidade de documentos|numericDecimal",
                    "icm_fis_fit_corporation_sequence_number|Minuta|numericDecimal",
                    "icm_fis_ioe_number|Nota Fiscal/Número|text",
                    "invoices_volumes|Volumes|numericDecimal",
                    "invoices_weight|Peso|decimalString",
                    "invoices_value|Valor NF|decimalString",
                    "icm_fis_fit_pyr_nickname|Pagador do frete/Nome fantasia|text",
                    "customer_debits_subtotal|valor a pagar ao cliente|decimalString",
                    "customer_credit_entries_subtotal|Valor nota de crédito ao cliente|decimalString",
                    "responsible_credits_subtotal|Valor a receber do responsável|decimalString",
                    "responsible_debit_entries_subtotal|Valor nota de débito do responsável|decimalString",
                    "insurer_credits_subtotal|Valor a receber da seguradora|decimalString",
                    "insurance_claim_total|Resultado final|decimalString",
                    "icm_crn_psn_nickname|Pessoa/Nome fantasia|text",
                    "icm_dvr_iil_name|Pessoa/Nome|text",
                    "icm_fer_name|Usuário/Nome|text",
                    "icm_vie_license_plate|Veículo/Placa|text",
                    "icm_ttt_ore_description|Ocorrência/Ocorrência|text",
                    "icm_ttt_ore_code|Ocorrência/Código|numericDecimal",
                    "icm_ttt_treatment_at|Tratativa/Data tratativa|timestamp",
                    "icm_ttt_dealing_type|Tratativa/Tipo tratativa|enum",
                    "icm_ttt_solution_type|Tratativa/Solução|enum");
    private static final List<String> UI_TITLES =
            BINDINGS.stream().map(value -> value.split("\\|", -1)[1]).toList();
    private static final Set<String> OBSERVED_SOURCE_FIELDS =
            Set.copyOf(BINDINGS.stream().map(value -> value.split("\\|", -1)[0]).toList());
    private static final Set<String> ENUM_FIELDS =
            Set.of("icm_ttt_dealing_type", "icm_ttt_solution_type");

    record Raw(JsonNode value, String numericLexeme) {}

    private SinistrosContentAdapter() {}

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
                        && "SINISTROS_NATIVE44_CANDIDATE_CATALOG_V1".equals(text(catalog, "schema"))
                        && SinistrosIndependentRequest.PROFILE.equals(text(catalog, "profile"))
                        && "SINISTROS".equals(text(catalog, "entity"))
                        && exactInt(catalog.path("reportId"), 6392)
                        && exactInt(catalog.path("idColumnIndex"), 0)
                        && catalog.path("totalColumnCount").isIntegralNumber()
                        && catalog.path("totalColumnCount").canConvertToInt()
                        && catalog.path("totalColumnCount").intValue() >= 0
                        && catalog.path("totalColumnCount").intValue() <= 44
                        && catalog.path("columns").isArray()
                        && catalog.path("columns").size() == 44
                        && Set.of("AUTHORED_SYNTHETIC", "OBSERVED_NATIVE_CANDIDATE")
                                .contains(text(catalog, "catalogNature"))
                        && catalog.path("presentationQualified").isBoolean()
                        && !catalog.get("presentationQualified").booleanValue()
                        && catalog.path("contentAccepted").isBoolean()
                        && !catalog.get("contentAccepted").booleanValue(),
                "SINISTROS_CANDIDATE_CATALOG_SCOPE");
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
                    "SINISTROS_CATALOG_EXACT44_COORDINATES_TITLES");
            final String[] binding = BINDINGS.get(coordinate - 1).split("\\|", -1);
            require(
                    binding[0].equals(text(field, "sourceField"))
                            && binding[2].equals(text(field, "kind")),
                    "SINISTROS_EXACT44_FIELD_KIND_BINDING");
            verifyPolicies(field);
            if (field.path("total").booleanValue()) {
                require(
                        Set.of("numericDecimal", "decimalString").contains(text(field, "kind")),
                        "SINISTROS_CATALOG_NUMERIC_TOTAL_ONLY");
                totals++;
            }
            if (coordinate == 1) {
                require(
                        "sequence_code".equals(text(field, "sourceField"))
                                && "positiveIntegralNumber".equals(text(field, "kind")),
                        "SINISTROS_CATALOG_SEQUENCE_COORDINATE");
            }
            if (coordinate == 2) {
                require(
                        "opening_at_date".equals(text(field, "sourceField"))
                                && "date".equals(text(field, "kind")),
                        "SINISTROS_CATALOG_SCOPE_COORDINATE");
            }
        }
        require(
                names.equals(OBSERVED_SOURCE_FIELDS)
                        && totals == catalog.path("totalColumnCount").intValue(),
                "SINISTROS_CATALOG_ALL44_FIELDS_AND_DECLARED_TOTALS");
        final var enums = catalog.path("enumLabels");
        require(enums.isObject(), "SINISTROS_CATALOG_EXPLICIT_ENUM_OBJECT");
        final var entries = enums.fields();
        while (entries.hasNext()) {
            final var entry = entries.next();
            require(
                    ENUM_FIELDS.contains(entry.getKey())
                            && entry.getValue().isObject()
                            && entry.getValue().size() <= 128,
                    "SINISTROS_CATALOG_SCOPED_ENUM");
            final var labels = entry.getValue().fields();
            while (labels.hasNext()) {
                final var label = labels.next();
                require(
                        label.getKey().length() <= 128
                                && label.getValue().isTextual()
                                && !label.getValue().textValue().isEmpty()
                                && label.getValue().textValue().length() <= 256,
                        "SINISTROS_CATALOG_ENUM_LABEL");
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
                "SINISTROS_EXPLICIT_FIELD_POLICY");
        require(
                !Set.of("opening_at_date", "sequence_code").contains(text(field, "sourceField"))
                        || "STRICT".equals(nullPolicy) && "STRICT".equals(emptyPolicy),
                "SINISTROS_SCOPE_KEY_STRICT");
        final boolean numeric =
                Set.of(
                                "positiveIntegralNumber",
                                "numericDecimal",
                                "decimalString",
                                "timestamp",
                                "date",
                                "time")
                        .contains(kind);
        if (numeric) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").startsWith("XLSX:1900:")
                            && text(field, "nativeCarrier").length() <= 256,
                    "SINISTROS_EXPLICIT_NATIVE_CARRIER");
        } else {
            require(
                    !field.has("nativeCarrier") && !"NULL_NATIVE_BLANK".equals(nullPolicy),
                    "SINISTROS_NONNUMERIC_NO_NATIVE_CARRIER");
        }
        if (Set.of("numericDecimal", "decimalString").contains(kind)) {
            require(
                    "DIRECT_BINARY64_NEAREST_EVEN_V1".equals(text(field, "nativeNumberEncoding")),
                    "SINISTROS_EXPLICIT_NUMERIC_ENCODING");
        } else {
            require(!field.has("nativeNumberEncoding"), "SINISTROS_NO_UNUSED_NUMERIC_ENCODING");
        }
        if ("time".equals(kind)) {
            final String sourceClock = text(field, "sourceTimeEncoding");
            final String nativeClock = text(field, "nativeTimeEncoding");
            require(
                    "ISO_LOCAL_TIME_SECOND_V1".equals(sourceClock)
                                    && QualifiedSinistrosModel44.NATIVE_CLOCK_ENCODING.equals(
                                            nativeClock)
                            || "SOURCE_OFFSET_DATE_CLOCK_SECOND_V1".equals(sourceClock)
                                    && QualifiedSinistrosModel44.NATIVE_DATE_CLOCK_ENCODING.equals(
                                            nativeClock),
                    "SINISTROS_EXPLICIT_TIME_PRESENTATION");
        } else {
            require(
                    !field.has("sourceTimeEncoding") && !field.has("nativeTimeEncoding"),
                    "SINISTROS_NO_UNUSED_TIME_ENCODING");
        }
        if ("stringArray".equals(kind)) {
            require(
                    Set.of("rctrc", "rctac", "rcfdc").contains(text(field, "sourceField"))
                            && field.path("arraySeparator").isTextual()
                            && field.get("arraySeparator").textValue().length() <= 16
                            && Set.of("STRICT", "EMPTY_ARRAY_NATIVE_ABSENT")
                                    .contains(text(field, "emptyArrayPresentation"))
                            && "STRICT".equals(emptyPolicy),
                    "SINISTROS_EXPLICIT_ARRAY_PRESENTATION");
        } else {
            require(
                    !field.has("arraySeparator") && !field.has("emptyArrayPresentation"),
                    "SINISTROS_NO_UNUSED_ARRAY_PRESENTATION");
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
        SinistrosIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        SinistrosIndependentRequest.validate(admitted);
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
                BoundedExportWorkbook.readSinistrosRows(workbookBytes, columns, budget);
        require("1900".equals(nativeTable.epoch()), "SINISTROS_QUALIFIED_EPOCH_1900_ONLY");
        require(!nativeTable.rows().isEmpty(), "SINISTROS_NATIVE_ROWS_REQUIRED");
        final var sourceValues = new ExactExportTable(columns, 0, budget);
        final var nativeValues = new ExactExportTable(columns, 0, budget);
        final var sourceOriginal = new ExactExportTable(rawColumns, 0, budget);
        final var nativeOriginal = new ExactExportTable(rawColumns, 0, budget);
        readSource(sourceBytes, admitted, fields, catalog, sourceValues, sourceOriginal, budget);
        int timestamps = 0;
        int noncanonical = 0;
        for (final var row : nativeTable.rows()) {
            budget.check();
            require(row.cells().size() == 44, "SINISTROS_ALL44_NATIVE_CELLS");
            final List<ExactExportTable.Atom> values = new ArrayList<>();
            for (int index = 0; index < fields.size(); index++) {
                final var field = fields.get(index);
                final var atom = row.cells().get(index);
                final var value = nativeValue(field, atom, catalog);
                values.add(value);
                if (index == 1) {
                    verifyOpeningDate(value, admitted);
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
                        && comparison.columnsEqual().size() == 44
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
                        .put("schema", "SINISTROS_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", SinistrosIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put(
                                "catalogSha256",
                                ExportValidationSupport.sha256(
                                        ExportValidationSupport.canonicalBytes(catalog)))
                        .put("catalogNature", text(catalog, "catalogNature"))
                        .put("catalogBindingPoliciesQualified", false)
                        .put("columns", 44)
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
                        .put("openingDateVerifiedForEveryRowBothSides", true)
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
                        .put("originalClockDateOffsetAndNativeEncodingRetained", true)
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
                    .put("sourceTimeEncoding", field.path("sourceTimeEncoding").asText("NONE"))
                    .put("nativeTimeEncoding", field.path("nativeTimeEncoding").asText("NONE"))
                    .put(
                            "emptyArrayPresentation",
                            field.path("emptyArrayPresentation").asText("NONE"))
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
                "SINISTROS_COMPLETE_JSONL_FINAL_LF");
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
                        "SINISTROS_SOURCE_LINE_BOUND");
                final var record = record(line);
                final var expectedNames = new HashSet<String>();
                fields.forEach(field -> expectedNames.add(text(field, "sourceField")));
                require(record.keySet().equals(expectedNames), "SINISTROS_SOURCE_EXACT44_FIELDS");
                final List<ExactExportTable.Atom> presented = new ArrayList<>();
                final List<ExactExportTable.Atom> raw = new ArrayList<>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    require(value != null, "SINISTROS_REQUIRED_SOURCE_FIELD");
                    raw.add(original(value));
                    final var key = sourceValue(field, value, catalog);
                    presented.add(key);
                    if ("opening_at_date".equals(text(field, "sourceField"))) {
                        verifyOpeningDate(key, request);
                    }
                }
                original.add(raw);
                values.add(presented);
            }
        }
        require(rows > 0, "SINISTROS_SOURCE_ROWS_REQUIRED");
    }

    static Map<String, Raw> record(final String line) throws IOException {
        Map<String, Raw> result = null;
        JsonNode entity = null;
        JsonNode template = null;
        final var names = new HashSet<String>();
        try (var parser = ExportValidationSupport.JSON.createParser(line)) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "SINISTROS_SOURCE_ENVELOPE");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(
                        parser.currentToken() == JsonToken.FIELD_NAME && names.size() < 3,
                        "SINISTROS_ENVELOPE_MEMBER");
                final String name = parser.currentName();
                require(names.add(name), "SINISTROS_ENVELOPE_DUPLICATE");
                final var token = parser.nextToken();
                require(token != null, "SINISTROS_ENVELOPE_VALUE");
                switch (name) {
                    case "record" -> result = readRecord(parser);
                    case "entity" -> entity = ExportValidationSupport.JSON.readTree(parser);
                    case "templateId" -> template = ExportValidationSupport.JSON.readTree(parser);
                    default ->
                            throw new IllegalArgumentException("SINISTROS_ENVELOPE_UNKNOWN_MEMBER");
                }
            }
            require(
                    parser.nextToken() == null
                            && names.equals(Set.of("entity", "templateId", "record"))
                            && result != null
                            && entity != null
                            && entity.isTextual()
                            && "SINISTROS".equals(entity.textValue())
                            && template != null
                            && exactInt(template, 6392),
                    "SINISTROS_SOURCE_ENVELOPE_BINDING");
        }
        return result;
    }

    private static Map<String, Raw> readRecord(final JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "SINISTROS_SOURCE_RECORD_OBJECT");
        final var result = new HashMap<String, Raw>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(
                    parser.currentToken() == JsonToken.FIELD_NAME && result.size() < 512,
                    "SINISTROS_SOURCE_MEMBER_BOUND");
            final String name = parser.currentName();
            final var token = parser.nextToken();
            require(token != null, "SINISTROS_SOURCE_VALUE");
            final String numeric = token.isNumeric() ? parser.getText() : null;
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(
                    value != null && result.putIfAbsent(name, new Raw(value, numeric)) == null,
                    "SINISTROS_SOURCE_DUPLICATE_OR_MISSING_VALUE");
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
            case "positiveIntegralNumber" -> {
                require(
                        value.isNumber() && raw.numericLexeme() != null,
                        "SINISTROS_SOURCE_POSITIVE_INTEGER_KEY");
                yield integer(raw.numericLexeme());
            }
            case "numericDecimal" -> {
                require(
                        value.isNumber() && raw.numericLexeme() != null,
                        "SINISTROS_SOURCE_NUMERIC_DECIMAL_TYPE");
                yield encoded(raw.numericLexeme());
            }
            case "decimalString" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_DECIMAL_STRING_TYPE");
                yield encoded(value.textValue());
            }
            case "timestamp" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_TIMESTAMP_TYPE");
                final String civil = QualifiedColetasModel.sourceTimestamp(value.textValue());
                require(
                        !LocalDateTime.parse(civil).isBefore(EPOCH.plusDays(61)),
                        "SINISTROS_SOURCE_TIMESTAMP_RANGE");
                yield literal(civil);
            }
            case "date" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_DATE_TYPE");
                final String civil = QualifiedColetasModel.sourceCivil(value.textValue());
                require(
                        !LocalDate.parse(civil).isBefore(EPOCH.toLocalDate().plusDays(61)),
                        "SINISTROS_SOURCE_DATE_RANGE");
                yield literal(civil);
            }
            case "time" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_TIME_TYPE");
                yield literal(
                        QualifiedSinistrosModel44.sourceTime(
                                value.textValue(), text(field, "sourceTimeEncoding")));
            }
            case "enum" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_ENUM_TYPE");
                final var label =
                        catalog.path("enumLabels")
                                .path(text(field, "sourceField"))
                                .path(value.textValue());
                require(label.isTextual(), "SINISTROS_SOURCE_ENUM_UNOBSERVED");
                yield literal(label.textValue());
            }
            case "text" -> {
                require(value.isTextual(), "SINISTROS_SOURCE_TEXT_TYPE");
                yield literal(value.textValue());
            }
            case "stringArray" -> {
                require(
                        value.isArray() && value.size() <= 1024,
                        "SINISTROS_SOURCE_ARRAY_TYPE_BOUND");
                if (value.isEmpty()
                        && "EMPTY_ARRAY_NATIVE_ABSENT"
                                .equals(text(field, "emptyArrayPresentation"))) {
                    yield new ExactExportTable.Atom("ABSENT", "");
                }
                final var items = new ArrayList<String>();
                for (final var item : value) {
                    require(item.isTextual(), "SINISTROS_SOURCE_ARRAY_STRING_ITEM");
                    items.add(item.textValue());
                }
                yield literal(String.join(field.get("arraySeparator").textValue(), items));
            }
            default -> throw new IllegalArgumentException("SINISTROS_SOURCE_KIND_UNQUALIFIED");
        };
    }

    static ExactExportTable.Atom nativeValue(
            final JsonNode field, final ExactExportTable.Atom atom, final JsonNode catalog) {
        verifyPolicies(field);
        final String kind = text(field, "kind");
        if ("ABSENT".equals(atom.type())) {
            require(atom.carrier().isEmpty(), "SINISTROS_NATIVE_ABSENT_EXACT_CARRIER");
            return atom;
        }
        if ("BLANK".equals(atom.type())) {
            require(
                    field.path("nativeCarrier").isTextual()
                            && text(field, "nativeCarrier").equals(atom.carrier()),
                    "SINISTROS_NATIVE_BLANK_EXACT_CARRIER");
            final String policy = text(field, "nullPresentation");
            return "NULL_NATIVE_BLANK".equals(policy)
                    ? new ExactExportTable.Atom("NULL", "")
                    : atom;
        }
        return switch (kind) {
            case "positiveIntegralNumber",
                    "numericDecimal",
                    "decimalString",
                    "timestamp",
                    "date",
                    "time" -> {
                require("NUMBER".equals(atom.type()), "SINISTROS_NATIVE_NUMERIC_TYPE");
                final String carrier = text(field, "nativeCarrier");
                require(carrier.equals(atom.carrier()), "SINISTROS_NATIVE_EXACT_CARRIER");
                yield switch (kind) {
                    case "positiveIntegralNumber" -> integer(atom.value());
                    case "date" -> literal(QualifiedColetasModel.nativeCivil(atom.value()));
                    case "time" ->
                            literal(
                                    QualifiedSinistrosModel44.nativeTime(
                                            atom.value(), text(field, "nativeTimeEncoding")));
                    case "timestamp" ->
                            literal(QualifiedColetasModel.nativeTimestamp(atom.value()));
                    default -> encoded(atom.value());
                };
            }
            case "text", "enum", "stringArray" -> {
                require(
                        "TEXT".equals(atom.type()) && atom.carrier().isEmpty(),
                        "SINISTROS_NATIVE_LITERAL_TEXT_REQUIRED");
                if ("enum".equals(kind)) {
                    boolean observed = false;
                    for (final var label :
                            catalog.path("enumLabels").path(text(field, "sourceField"))) {
                        observed |= label.isTextual() && label.textValue().equals(atom.value());
                    }
                    require(observed, "SINISTROS_NATIVE_ENUM_UNOBSERVED");
                }
                yield literal(atom.value());
            }
            default -> throw new IllegalArgumentException("SINISTROS_NATIVE_KIND_UNQUALIFIED");
        };
    }

    private static void verifyOpeningDate(
            final ExactExportTable.Atom atom, final JsonNode request) {
        require(
                "TEXT".equals(atom.type()) && atom.value().length() == 10,
                "SINISTROS_OPENING_DATE_VALUE_REQUIRED");
        final LocalDate day = LocalDate.parse(atom.value());
        require(
                !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                        && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                "SINISTROS_OPENING_DATE_OUTSIDE_PERIOD");
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
                "SINISTROS_NATIVE_POSITIVE_INTEGER_KEY");
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

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Source0 only: the actual current mapper re-reads every immutable RAW occurrence. */
public final class ContasPagarRawMapperQualifier {
    private static final String SHAPE_SHA =
            "b16ce324fe63d57ddc51af0ed02466faddde6568c0503fdc2d2a1065e120d051";
    private static final ContaPagarDataExportMapper MAPPER = new ContaPagarDataExportMapper();

    private ContasPagarRawMapperQualifier() {}

    static ExportValidationSupport.Pin pin(final JsonNode value) {
        final var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        require(
                keys.equals(Set.of("path", "bytes", "sha256"))
                        && value.path("bytes").isIntegralNumber()
                        && value.path("bytes").canConvertToLong(),
                "CONTAS_A_PAGAR_REQUALIFICATION_EXACT_PIN");
        return new ExportValidationSupport.Pin(
                Path.of(text(value, "path")),
                text(value, "sha256").toLowerCase(Locale.ROOT),
                value.path("bytes").longValue());
    }

    public static ObjectNode qualify(
            final ExportValidationSupport.Pin requestPin,
            final ExportValidationSupport.Pin sourcePin,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final JsonNode request = requestPin.json(262144, budget);
        ContasPagarIndependentRequest.validate(request);
        final Map<String, JsonNode> shape = shape();
        final byte[] raw = sourcePin.read(8388608, budget);
        require(
                raw.length > 0 && raw[raw.length - 1] == '\n',
                "CONTAS_A_PAGAR_REQUALIFICATION_FINAL_LF");
        long rows = 0;
        long rejected = 0;
        long typeRejected = 0;
        long mapperRejected = 0;
        final var reasons = new TreeMap<String, Long>();
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(raw),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(
                        !line.isBlank() && line.length() <= 262144 && ++rows <= 12000,
                        "CONTAS_A_PAGAR_REQUALIFICATION_ROW_BOUND");
                final JsonNode envelope;
                try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                    envelope = ExportValidationSupport.JSON.readTree(parser);
                    require(
                            envelope != null && parser.nextToken() == null,
                            "CONTAS_A_PAGAR_REQUALIFICATION_ROW_JSON_EOF");
                }
                final var envelopeKeys = new HashSet<String>();
                envelope.fieldNames().forEachRemaining(envelopeKeys::add);
                require(
                        envelope.isObject()
                                && envelopeKeys.equals(Set.of("entity", "templateId", "record"))
                                && "CONTAS_A_PAGAR".equals(text(envelope, "entity"))
                                && envelope.path("templateId").isIntegralNumber()
                                && envelope.path("templateId").canConvertToInt()
                                && envelope.path("templateId").intValue() == 8636,
                        "CONTAS_A_PAGAR_REQUALIFICATION_ENVELOPE");
                final JsonNode row = envelope.required("record");
                final var actual = new HashSet<String>();
                row.fieldNames().forEachRemaining(actual::add);
                require(
                        row.isObject() && row.size() == 28 && actual.equals(shape.keySet()),
                        "CONTAS_A_PAGAR_REQUALIFICATION_ALL28_FIELDS");
                boolean rowTypesValid = true;
                for (final var field : shape.entrySet()) {
                    budget.cell();
                    if (!qualified(row.get(field.getKey()), field.getValue())) {
                        rowTypesValid = false;
                        reasons.merge(field.getKey() + ":WIRE_TYPE", 1L, Long::sum);
                    }
                }
                verifyIssueDate(row, request);
                boolean mappedValid;
                final ObjectNode mapperEnvelope = ExportValidationSupport.JSON.createObjectNode();
                mapperEnvelope.set("data", row);
                try {
                    mappedValid = MAPPER.map(1, mapperEnvelope).valid();
                    if (!mappedValid) {
                        reasons.merge("CURRENT_MAPPER_OBSERVATION_INVALID", 1L, Long::sum);
                    }
                } catch (final IllegalArgumentException failure) {
                    mappedValid = false;
                    reasons.merge("CURRENT_MAPPER_OBSERVATION_REFUSED", 1L, Long::sum);
                }
                typeRejected += rowTypesValid ? 0 : 1;
                mapperRejected += mappedValid ? 0 : 1;
                rejected += rowTypesValid && mappedValid ? 0 : 1;
            }
        }
        require(rows > 0, "CONTAS_A_PAGAR_REQUALIFICATION_NONEMPTY_CAPTURE_REQUIRED");
        sourcePin.read(8388608, budget);
        requestPin.read(262144, budget);
        shape();
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CONTAS_A_PAGAR_RAW_REQUALIFICATION_ENTRY_V1")
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("success", rejected == 0)
                        .put("rows", rows)
                        .put("rejectedRows", rejected)
                        .put("rowTypeRejectedRows", typeRejected)
                        .put("mapperRejectedRows", mapperRejected)
                        .put("rowTypesQualified", typeRejected == 0)
                        .put("mapperQualified", mapperRejected == 0)
                        .put("currentMapperInvocations", rows)
                        .put("allRawRowsReadToEof", true)
                        .put("recordTypesPreservedWhenReconstructingMapperEnvelope", true)
                        .put("rawBytesReserializedOrReplaced", false)
                        .put("deduplicationPerformed", false)
                        .put("sourceWireContractSha256", SHAPE_SHA)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("attemptId", text(request, "attemptId"))
                        .put("issueDateVerifiedEveryRow", true)
                        .put("createdAtScopeEnforced", false)
                        .put("createdAtValuePreserved", true)
                        .put("filterScopePolicy", ContasPagarIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("createdAtFilter", "")
                        .put("issueDateScopePolicy", "ISO_LOCAL_DATE_V1")
                        .put("dateFilterSemanticsObserved", false)
                        .put("sourceRootIdentityQualified", false)
                        .put("installmentSequenceUsedAsRootIdentity", false)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("businessValuesPublished", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false);
        result.set("refusalCounts", ExportValidationSupport.JSON.valueToTree(reasons));
        result.set("sourcePin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        return result;
    }

    private static void verifyIssueDate(final JsonNode row, final JsonNode request) {
        final var start = LocalDate.parse(text(request, "periodStart"));
        final var end = LocalDate.parse(text(request, "periodEnd"));
        final JsonNode issue = row.required("issue_date");
        require(
                issue.isTextual() && issue.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "CONTAS_A_PAGAR_REQUALIFICATION_ISSUE_DATE_EXACT_ISO");
        final LocalDate issueDay;
        try {
            issueDay = LocalDate.parse(issue.textValue());
        } catch (final DateTimeParseException invalid) {
            throw new IllegalArgumentException(
                    "CONTAS_A_PAGAR_REQUALIFICATION_ISSUE_DATE_INVALID", invalid);
        }
        require(
                !issueDay.isBefore(start) && !issueDay.isAfter(end),
                "CONTAS_A_PAGAR_REQUALIFICATION_ISSUE_DATE_SCOPE");
    }

    private static Map<String, JsonNode> shape() throws Exception {
        try (var stream =
                ContasPagarRawMapperQualifier.class.getResourceAsStream(
                        "/contracts/dataexport/expansion-8636-observed-wire-20261107-p3.json")) {
            require(stream != null, "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_CONTRACT_REQUIRED");
            final byte[] bytes = stream.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && SHAPE_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_CONTRACT_PIN");
            final JsonNode contract;
            try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
                contract = ExportValidationSupport.JSON.readTree(parser);
                require(
                        contract != null && parser.nextToken() == null,
                        "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_CONTRACT_EOF");
            }
            require(
                    contract.path("templateId").intValue() == 8636
                            && "$".equals(contract.path("recordRoot").asText())
                            && "ARRAY".equals(contract.path("rootCardinality").asText()),
                    "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_CONTRACT_SCOPE");
            final var fields = new HashMap<String, JsonNode>();
            for (final var field : contract.required("fields")) {
                require(
                        field.path("required").isBoolean()
                                && field.path("required").booleanValue()
                                && fields.putIfAbsent(text(field, "path"), field) == null,
                        "CONTAS_A_PAGAR_REQUALIFICATION_REQUIRED_UNIQUE_FIELDS");
            }
            require(fields.size() == 28, "CONTAS_A_PAGAR_REQUALIFICATION_SOURCE_CONTRACT28");
            return Map.copyOf(fields);
        }
    }

    private static boolean qualified(final JsonNode value, final JsonNode rule) {
        if (value == null) {
            return false;
        }
        final String type = type(value);
        boolean accepted = false;
        for (final var allowed : rule.path("wireTypes")) {
            accepted |= type.equals(allowed.asText());
        }
        if (!accepted) {
            return false;
        }
        if (value.isArray()) {
            for (final var item : value) {
                boolean itemAccepted = false;
                for (final var allowed : rule.path("arrayItemTypes")) {
                    itemAccepted |= type(item).equals(allowed.asText());
                }
                if (!itemAccepted) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String type(final JsonNode value) {
        return value.isNull()
                ? "NULL"
                : value.isIntegralNumber()
                        ? "INTEGER"
                        : value.isNumber()
                                ? "NUMBER"
                                : value.isTextual()
                                        ? "STRING"
                                        : value.isArray()
                                                ? "ARRAY"
                                                : value.isObject() ? "OBJECT" : "BOOLEAN";
    }
}

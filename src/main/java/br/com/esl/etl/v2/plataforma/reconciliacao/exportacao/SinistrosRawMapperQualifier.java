package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Source0 only: the actual current mapper re-reads every immutable RAW occurrence. */
public final class SinistrosRawMapperQualifier {
    private static final String SHAPE_SHA =
            "1082b9d5be12ea9107830f5b287bb324d886efd83e313a7ae8977384e203a90d";
    private static final SinistroDataExportMapper MAPPER = new SinistroDataExportMapper();

    private SinistrosRawMapperQualifier() {}

    static ExportValidationSupport.Pin pin(final JsonNode value) {
        final var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        require(
                keys.equals(Set.of("path", "bytes", "sha256"))
                        && value.path("bytes").isIntegralNumber()
                        && value.path("bytes").canConvertToLong(),
                "SINISTROS_REQUALIFICATION_EXACT_PIN");
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
        SinistrosIndependentRequest.validate(request);
        final Map<String, JsonNode> shape = shape();
        final byte[] raw = sourcePin.read(8388608, budget);
        require(
                raw.length > 0 && raw[raw.length - 1] == '\n',
                "SINISTROS_REQUALIFICATION_FINAL_LF");
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
                        "SINISTROS_REQUALIFICATION_ROW_BOUND");
                final JsonNode envelope;
                try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                    envelope = ExportValidationSupport.JSON.readTree(parser);
                    require(
                            envelope != null && parser.nextToken() == null,
                            "SINISTROS_REQUALIFICATION_ROW_JSON_EOF");
                }
                final var envelopeKeys = new HashSet<String>();
                envelope.fieldNames().forEachRemaining(envelopeKeys::add);
                require(
                        envelope.isObject()
                                && envelopeKeys.equals(Set.of("entity", "templateId", "record"))
                                && "SINISTROS".equals(text(envelope, "entity"))
                                && envelope.path("templateId").isIntegralNumber()
                                && envelope.path("templateId").canConvertToInt()
                                && envelope.path("templateId").intValue() == 6392,
                        "SINISTROS_REQUALIFICATION_ENVELOPE");
                final JsonNode row = envelope.required("record");
                final var actual = new HashSet<String>();
                row.fieldNames().forEachRemaining(actual::add);
                require(
                        row.isObject() && row.size() == 44 && actual.equals(shape.keySet()),
                        "SINISTROS_REQUALIFICATION_ALL44_FIELDS");
                boolean rowTypesValid = true;
                for (final var field : shape.entrySet()) {
                    budget.cell();
                    if (!qualified(row.get(field.getKey()), field.getValue())) {
                        rowTypesValid = false;
                        reasons.merge(field.getKey() + ":WIRE_TYPE", 1L, Long::sum);
                    }
                }
                final JsonNode sequence = row.required("sequence_code");
                require(sequence.isNumber(), "SINISTROS_REQUALIFICATION_SEQUENCE_NUMERIC");
                final java.math.BigInteger key;
                try {
                    key = sequence.decimalValue().toBigIntegerExact();
                } catch (final ArithmeticException invalid) {
                    throw new IllegalArgumentException(
                            "SINISTROS_REQUALIFICATION_SEQUENCE_INTEGRAL", invalid);
                }
                require(
                        key.signum() > 0
                                && key.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) <= 0,
                        "SINISTROS_REQUALIFICATION_SEQUENCE_POSITIVE_LONG");
                require(
                        row.path("opening_at_date").isTextual()
                                && row.path("opening_at_date")
                                        .textValue()
                                        .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                        "SINISTROS_REQUALIFICATION_OPENING_AT_DATE_EXACT_ISO");
                final LocalDate day = LocalDate.parse(row.path("opening_at_date").textValue());
                require(
                        !day.isBefore(LocalDate.parse(text(request, "periodStart")))
                                && !day.isAfter(LocalDate.parse(text(request, "periodEnd"))),
                        "SINISTROS_REQUALIFICATION_OPENING_AT_DATE_SCOPE");
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
        require(rows > 0, "SINISTROS_REQUALIFICATION_NONEMPTY_CAPTURE_REQUIRED");
        sourcePin.read(8388608, budget);
        requestPin.read(262144, budget);
        shape();
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "SINISTROS_RAW_REQUALIFICATION_ENTRY_V1")
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
                        .put("openingAtDateVerifiedEveryRow", true)
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

    private static Map<String, JsonNode> shape() throws Exception {
        try (var stream =
                SinistrosRawMapperQualifier.class.getResourceAsStream(
                        "/contracts/dataexport/expansion-6392-observed-wire-20261025-v1.json")) {
            require(stream != null, "SINISTROS_REQUALIFICATION_SOURCE_CONTRACT_REQUIRED");
            final byte[] bytes = stream.readNBytes(65537);
            require(
                    bytes.length <= 65536
                            && SHAPE_SHA.equals(ExportValidationSupport.sha256(bytes)),
                    "SINISTROS_REQUALIFICATION_SOURCE_CONTRACT_PIN");
            final JsonNode contract;
            try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
                contract = ExportValidationSupport.JSON.readTree(parser);
                require(
                        contract != null && parser.nextToken() == null,
                        "SINISTROS_REQUALIFICATION_SOURCE_CONTRACT_EOF");
            }
            require(
                    contract.path("templateId").intValue() == 6392
                            && "$".equals(contract.path("recordRoot").asText())
                            && "ARRAY".equals(contract.path("rootCardinality").asText()),
                    "SINISTROS_REQUALIFICATION_SOURCE_CONTRACT_SCOPE");
            final var fields = new HashMap<String, JsonNode>();
            for (final var field : contract.required("fields")) {
                require(
                        field.path("required").isBoolean()
                                && field.path("required").booleanValue()
                                && fields.putIfAbsent(text(field, "path"), field) == null,
                        "SINISTROS_REQUALIFICATION_REQUIRED_UNIQUE_FIELDS");
            }
            require(fields.size() == 44, "SINISTROS_REQUALIFICATION_SOURCE_CONTRACT44");
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

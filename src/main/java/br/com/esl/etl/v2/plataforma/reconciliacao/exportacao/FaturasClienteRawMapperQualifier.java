package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** Source0 only: the actual current mapper re-reads every immutable RAW occurrence. */
public final class FaturasClienteRawMapperQualifier {
    static final int MAXIMUM_LINE_UTF16 = 1_049_600;
    private static final String SHAPE_SHA = FaturasClienteObservedWire52.P2_SHA256;
    private static final FaturaClienteDataExportMapper MAPPER = new FaturaClienteDataExportMapper();

    private FaturasClienteRawMapperQualifier() {}

    static ExportValidationSupport.Pin pin(final JsonNode value) {
        final var keys = new HashSet<String>();
        value.fieldNames().forEachRemaining(keys::add);
        require(
                keys.equals(Set.of("path", "bytes", "sha256"))
                        && value.path("bytes").isIntegralNumber()
                        && value.path("bytes").canConvertToLong(),
                "FATURAS_CLIENTE_REQUALIFICATION_EXACT_PIN");
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
        FaturasClienteIndependentRequest.validate(request);
        final var shape = FaturasClienteObservedWire52.observed20261108P2();
        final byte[] raw = sourcePin.read(8388608, budget);
        require(
                raw.length > 0 && raw[raw.length - 1] == '\n',
                "FATURAS_CLIENTE_REQUALIFICATION_FINAL_LF");
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
                        !line.isBlank() && line.length() <= MAXIMUM_LINE_UTF16 && ++rows <= 12000,
                        "FATURAS_CLIENTE_REQUALIFICATION_ROW_BOUND");
                final JsonNode envelope;
                try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                    envelope = ExportValidationSupport.JSON.readTree(parser);
                    require(
                            envelope != null && parser.nextToken() == null,
                            "FATURAS_CLIENTE_REQUALIFICATION_ROW_JSON_EOF");
                }
                final var envelopeKeys = new HashSet<String>();
                envelope.fieldNames().forEachRemaining(envelopeKeys::add);
                require(
                        envelope.isObject()
                                && envelopeKeys.equals(Set.of("entity", "templateId", "record"))
                                && "FATURAS_POR_CLIENTE".equals(text(envelope, "entity"))
                                && envelope.path("templateId").isIntegralNumber()
                                && envelope.path("templateId").canConvertToInt()
                                && envelope.path("templateId").intValue() == 4924,
                        "FATURAS_CLIENTE_REQUALIFICATION_ENVELOPE");
                final JsonNode row = envelope.required("record");
                for (int column = 0; column < 52; column++) {
                    budget.cell();
                }
                for (final var value : row) {
                    if (value.isArray()) {
                        for (final var item : value) {
                            budget.cell();
                        }
                    }
                }
                final boolean rowTypesValid =
                        FaturasClienteObservedWire52.qualified(row, shape, reasons);
                FaturasClienteIndependentRequest.verifyServiceAt(row, request);
                final var mapperEnvelope = ExportValidationSupport.JSON.createObjectNode();
                mapperEnvelope.set("data", row);
                boolean mappedValid;
                try {
                    mappedValid = MAPPER.map((int) ((rows - 1) % 100) + 1, mapperEnvelope).valid();
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
        require(rows > 0, "FATURAS_CLIENTE_REQUALIFICATION_NONEMPTY_CAPTURE_REQUIRED");
        sourcePin.read(8388608, budget);
        requestPin.read(262144, budget);
        FaturasClienteObservedWire52.observed20261108P2();
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_RAW_REQUALIFICATION_ENTRY_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
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
                        .put("serviceAtVerifiedEveryRow", true)
                        .put("serviceAtScopeEnforced", true)
                        .put("serviceAtOriginalPreserved", true)
                        .put(
                                "filterScopePolicy",
                                FaturasClienteIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("updatedAtFilter", "")
                        .put("updatedAtScopeEnforced", false)
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("sourceAcquisitionVerified", false)
                        .put("nativePresentationQualified", false)
                        .put("sourceRootIdentityQualified", false)
                        .put("corporationSequenceUsedAsGlobalIdentity", false)
                        .put("idUsedAsGlobalIdentity", false)
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

    static long verifyOriginalResponses(
            final JsonNode responses,
            final ExportValidationSupport.Pin sourcePin,
            final java.util.List<ExportValidationSupport.Pin> held,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                responses.isArray() && responses.size() > 1 && responses.size() <= 17,
                "FATURAS_CLIENTE_REQUALIFICATION_RESPONSE_BOUND");
        final var expected = new java.io.ByteArrayOutputStream();
        final byte[] prefix =
                "{\"entity\":\"FATURAS_POR_CLIENTE\",\"templateId\":4924,\"record\":"
                        .getBytes(StandardCharsets.UTF_8);
        long rows = 0;
        int index = 0;
        final var shape = FaturasClienteObservedWire52.observed20261108P2();
        for (final var response : responses) {
            budget.check();
            require(
                    response.path("status").isIntegralNumber()
                            && response.path("status").intValue() == 200
                            && response.path("page").isIntegralNumber()
                            && response.path("page").intValue() == index,
                    "FATURAS_CLIENTE_REQUALIFICATION_CONTIGUOUS_RESPONSES");
            final var bodyPin = pin(response.required("rawBodyPin"));
            held.add(bodyPin);
            final byte[] body = bodyPin.read(index == 0 ? 262144 : 8388608, budget);
            if (index == 0) {
                FaturasClienteObservedWire52.validateInfoNames(
                        FaturasClienteObservedWire52.strict(body), shape);
            } else {
                int pageRows = 0;
                try (var parser = ExportValidationSupport.JSON.createParser(body)) {
                    require(
                            parser.nextToken() == com.fasterxml.jackson.core.JsonToken.START_ARRAY,
                            "FATURAS_CLIENTE_REQUALIFICATION_ORIGINAL_ROOT_ARRAY");
                    while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                        budget.cell();
                        require(
                                parser.currentToken()
                                        == com.fasterxml.jackson.core.JsonToken.START_OBJECT,
                                "FATURAS_CLIENTE_REQUALIFICATION_ORIGINAL_OBJECT");
                        final int first =
                                Math.toIntExact(parser.currentTokenLocation().getByteOffset());
                        final JsonNode row = ExportValidationSupport.JSON.readTree(parser);
                        final int last = Math.toIntExact(parser.currentLocation().getByteOffset());
                        require(
                                row != null
                                        && row.isObject()
                                        && first >= 0
                                        && last > first
                                        && last <= body.length
                                        && body[first] == '{'
                                        && body[last - 1] == '}',
                                "FATURAS_CLIENTE_REQUALIFICATION_ORIGINAL_OFFSETS");
                        final byte[] record =
                                FaturasClienteIntervalCapture.compactRecord(body, first, last);
                        require(
                                ++rows <= 12000
                                        && expected.size() + prefix.length + record.length + 2
                                                <= 8388608,
                                "FATURAS_CLIENTE_REQUALIFICATION_LITERAL_BOUNDS");
                        expected.write(prefix);
                        expected.write(record);
                        expected.write('}');
                        expected.write('\n');
                        pageRows++;
                    }
                    require(
                            parser.nextToken() == null,
                            "FATURAS_CLIENTE_REQUALIFICATION_ORIGINAL_EOF");
                }
                require(
                        index == responses.size() - 1 ? pageRows == 0 : pageRows > 0,
                        "FATURAS_CLIENTE_REQUALIFICATION_ACTUAL_TERMINAL_ONLY");
            }
            index++;
        }
        require(
                java.util.Arrays.equals(expected.toByteArray(), sourcePin.read(8388608, budget)),
                "FATURAS_CLIENTE_REQUALIFICATION_RAW_EQUALS_ALL_ORIGINAL_COMPACTED_RECORDS");
        return rows;
    }
}

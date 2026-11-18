package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Explicit supplemental consumer. Never changes P2, Producer02 receipts, retained bytes or
 * operational selection.
 */
public final class CotacoesP3RawQualifier {
    public static final String SHAPE_SHA =
            "58410f96dc76a344b734801f16fed076fcfa3cf06e308f6aff70a06d3d311926";
    public static final String P2_SHA =
            "8e6fc68811ef08e707dfbedd21818efa9bb8c2943d9d253183640cb5ae17799c";
    private static final String RESOURCE =
            "/contracts/dataexport/cotacoes-6906-observed-p3-offline-supplement-20261107.json";
    private static final String P2_RESOURCE =
            "/contracts/dataexport/cotacoes-6906-observed-p2-info-qualified-20261025.json";
    private static final String NFSE = "/qoe_qes_fit_nse_issued_at";
    static final Set<String> NEW_NULLABLE =
            Set.of(
                    "qoe_qes_collect_subtotal",
                    "qoe_qes_delivery_subtotal",
                    "qoe_qes_destination_postal_code",
                    "qoe_qes_diy_name",
                    "qoe_qes_diy_sae_code",
                    "qoe_qes_fon_name",
                    "qoe_qes_freight_comments",
                    "qoe_qes_invoices_value",
                    "qoe_qes_invoices_volumes",
                    "qoe_qes_itr_subtotal",
                    "qoe_qes_ony_name",
                    "qoe_qes_ony_sae_code",
                    "qoe_qes_origin_postal_code",
                    "qoe_qes_other_fees",
                    "qoe_qes_real_weight",
                    "qoe_qes_status",
                    "qoe_qes_taxed_weight",
                    "qoe_qes_tde_subtotal",
                    "qoe_qes_total");

    private CotacoesP3RawQualifier() {}

    public static JsonNode contract() throws IOException {
        final JsonNode base = resource(P2_RESOURCE, P2_SHA);
        final JsonNode candidate = resource(RESOURCE, SHAPE_SHA);
        require(
                "COTACOES_OBSERVED_RESPONSE_SUPPLEMENT_P3_V1".equals(text(candidate, "schema"))
                        && "DATA_EXPORT".equals(text(candidate, "sourceKind"))
                        && candidate.path("templateId").isIntegralNumber()
                        && candidate.get("templateId").intValue() == 6906
                        && P2_RESOURCE.equals(
                                candidate.path("baseContract").path("resource").asText())
                        && P2_SHA.equals(candidate.path("baseContract").path("sha256").asText())
                        && candidate.required("metadata").equals(base.required("metadata")),
                "COTACOES_P3_BASE_AND_METADATA");
        final JsonNode expected = base.required("response").deepCopy();
        int changed = 0;
        boolean nfse = false;
        for (final var field : expected.required("fields")) {
            final var rule = (ObjectNode) field;
            final String path = text(rule, "path");
            if (NEW_NULLABLE.contains(path.substring(1))) {
                require(
                        rule.path("nullable").isBoolean() && !rule.get("nullable").booleanValue(),
                        "COTACOES_P3_ONLY_NEW_OBSERVED_NULLS");
                rule.put("nullable", true);
                changed++;
            }
            if (NFSE.equals(path)) {
                require(
                        rule.path("nullable").booleanValue()
                                && rule.path("jsonTypes").isArray()
                                && rule.get("jsonTypes").isEmpty(),
                        "COTACOES_P3_NFSE_PRIOR_NULL_ONLY");
                rule.put("cardinality", "SCALAR").put("wireFormat", "EXACT_ISO_CIVIL_DATE");
                rule.putArray("jsonTypes").add("STRING");
                nfse = true;
            }
        }
        require(
                changed == 19 && nfse && expected.equals(candidate.required("response")),
                "COTACOES_P3_EXACT_SUPPLEMENT_NO_OTHER_WIDENING");
        require(
                candidate.path("supplement").path("qualifiedAll21Days").isBoolean()
                        && !candidate.get("supplement").get("qualifiedAll21Days").booleanValue()
                        && candidate.path("acceptance").path("runtimeAdopted").isBoolean()
                        && !candidate.get("acceptance").get("runtimeAdopted").booleanValue(),
                "COTACOES_P3_CANDIDATE_ONLY");
        return candidate;
    }

    private static JsonNode resource(final String name, final String sha) throws IOException {
        try (var stream = CotacoesP3RawQualifier.class.getResourceAsStream(name)) {
            require(stream != null, "COTACOES_P3_RESOURCE_REQUIRED");
            final byte[] bytes = stream.readNBytes(65537);
            require(
                    bytes.length <= 65536 && sha.equals(ExportValidationSupport.sha256(bytes)),
                    "COTACOES_P3_RESOURCE_PIN");
            return json(bytes);
        }
    }

    public static boolean qualifiedValue(final JsonNode value, final JsonNode rule) {
        require(value != null && rule != null, "COTACOES_P3_RULE_REQUIRED");
        if (value.isNull()) {
            return rule.required("nullable").booleanValue();
        }
        final String type =
                value.isTextual()
                        ? "STRING"
                        : value.isIntegralNumber()
                                ? "INTEGER"
                                : value.isNumber()
                                        ? "NUMBER"
                                        : value.isBoolean()
                                                ? "BOOLEAN"
                                                : value.isArray() ? "ARRAY" : "OBJECT";
        boolean admitted = false;
        for (final var expected : rule.required("jsonTypes")) {
            admitted |= type.equals(expected.textValue());
        }
        if (admitted && value.isNumber()) {
            new ExactExportTable.Atom("NUMBER", value.asText());
        }
        if (admitted && rule.has("wireFormat")) {
            require(
                    NFSE.equals(text(rule, "path"))
                            && "EXACT_ISO_CIVIL_DATE".equals(text(rule, "wireFormat")),
                    "COTACOES_P3_EXACT_SCOPED_WIRE_FORMAT");
            if (!value.isTextual()) {
                return false;
            }
            try {
                QualifiedColetasModel.sourceCivil(value.textValue());
            } catch (final IllegalArgumentException invalid) {
                return false;
            }
        }
        return admitted;
    }

    /**
     * JSON pin permits the real zero-byte retention witness; terminalArrayPin must still prove
     * actual [].
     */
    public static ObjectNode qualify(
            final JsonNode request,
            final JsonNode sourcePayloadPin,
            final JsonNode terminalArrayPin,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return qualifyWithPostcheckProbe(
                request, sourcePayloadPin, terminalArrayPin, budget, () -> {});
    }

    static ObjectNode qualifyWithPostcheckProbe(
            final JsonNode request,
            final JsonNode sourcePayloadPin,
            final JsonNode terminalArrayPin,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        CotacoesIndependentRequest.validate(request);
        final JsonNode admitted = request.deepCopy();
        final JsonNode contract = contract();
        final var rules = new HashMap<String, JsonNode>();
        for (final var rule : contract.required("response").required("fields")) {
            require(
                    "RECORD".equals(text(rule, "scope"))
                            && "REQUIRED".equals(text(rule, "presence"))
                            && rules.putIfAbsent(text(rule, "path").substring(1), rule) == null,
                    "COTACOES_P3_UNIQUE_REQUIRED_RULES");
        }
        require(rules.size() == 37, "COTACOES_P3_EXACT37_RULES");
        final byte[] source = payload(sourcePayloadPin, budget);
        final var terminalPin = normalizedPin(terminalArrayPin);
        final JsonNode terminal = terminalPin.json(8388608, budget);
        require(
                terminal.isArray() && terminal.isEmpty(),
                "COTACOES_P3_ACTUAL_TERMINAL_EMPTY_ARRAY");
        final Map<String, Long> refusals = new TreeMap<>();
        long rows = 0;
        long rejectedRows = 0;
        if (source.length > 0) {
            require(source[source.length - 1] == '\n', "COTACOES_P3_JSONL_FINAL_LF");
            try (var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new ByteArrayInputStream(source),
                                    StandardCharsets.UTF_8
                                            .newDecoder()
                                            .onMalformedInput(CodingErrorAction.REPORT)
                                            .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    budget.check();
                    require(
                            !line.isBlank() && line.length() <= 262144 && ++rows <= 12000,
                            "COTACOES_P3_JSONL_ROW_BOUND");
                    final JsonNode envelope = json(line.getBytes(StandardCharsets.UTF_8));
                    fields(envelope, Set.of("entity", "templateId", "record"));
                    require(
                            "COTACOES".equals(text(envelope, "entity"))
                                    && envelope.path("templateId").isIntegralNumber()
                                    && envelope.get("templateId").canConvertToInt()
                                    && envelope.get("templateId").intValue() == 6906,
                            "COTACOES_P3_RAW_ENVELOPE_BINDING");
                    final JsonNode record = envelope.required("record");
                    fields(record, rules.keySet());
                    boolean qualified = true;
                    for (final var rule : rules.entrySet()) {
                        budget.cell();
                        final JsonNode value = record.get(rule.getKey());
                        if (!qualifiedValue(value, rule.getValue())) {
                            qualified = false;
                            refusals.merge(
                                    "/"
                                            + rule.getKey()
                                            + (value.isNull() ? ":NULL" : ":TYPE_OR_FORMAT"),
                                    1L,
                                    Long::sum);
                        }
                    }
                    final JsonNode key = record.get("sequence_code");
                    if (!key.isIntegralNumber()
                            || !key.canConvertToLong()
                            || key.longValue() <= 0) {
                        qualified = false;
                        refusals.merge("/sequence_code:POSITIVE_LONG_INTEGER", 1L, Long::sum);
                    }
                    try {
                        final LocalDate day = requestedDate(record.get("requested_at"));
                        if (day.isBefore(LocalDate.parse(text(admitted, "periodStart")))
                                || day.isAfter(LocalDate.parse(text(admitted, "periodEnd")))) {
                            throw new IllegalArgumentException("COTACOES_P3_REQUESTED_OUTSIDE");
                        }
                    } catch (final IllegalArgumentException invalid) {
                        qualified = false;
                        refusals.merge("/requested_at:INVALID_OR_OUTSIDE_PERIOD", 1L, Long::sum);
                    }
                    rejectedRows += qualified ? 0 : 1;
                }
            }
        }
        beforePostcheck.run();
        payload(sourcePayloadPin, budget);
        terminalPin.read(8388608, budget);
        contract();
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_P3_RAW_QUALIFICATION_V1")
                        .put("profile", CotacoesIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("p3ShapeSha256", SHAPE_SHA)
                        .put("historicalP2Sha256", P2_SHA)
                        .put("rows", rows)
                        .put("rejectedRows", rejectedRows)
                        .put("rowTypesQualified", rejectedRows == 0)
                        .put("allRawRowsReadToEof", true)
                        .put("actualTerminalArrayVerified", true)
                        .put("emptyPayloadWitnessVerified", rows == 0)
                        .put("pinsVerifiedBeforeAndAfter", true)
                        .put("sourceBytesModified", false)
                        .put("producerReceiptsModified", false)
                        .put("deduplicationPerformed", false)
                        .put("trustedSourceCompleteness", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("mainResumeAllowed", false)
                        .put("runtimeAdopted", false)
                        .put("productionAuthorized", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        result.set("refusalCounts", ExportValidationSupport.JSON.valueToTree(refusals));
        result.set("sourcePayloadPin", sourcePayloadPin.deepCopy());
        result.set("terminalArrayPin", terminalArrayPin.deepCopy());
        return result;
    }

    private static ExportValidationSupport.Pin normalizedPin(final JsonNode node) {
        fields(node, Set.of("path", "sha256", "bytes"));
        require(
                node.path("bytes").isIntegralNumber() && node.get("bytes").canConvertToLong(),
                "COTACOES_P3_PIN_SIZE");
        return new ExportValidationSupport.Pin(
                Path.of(text(node, "path")),
                text(node, "sha256").toLowerCase(java.util.Locale.ROOT),
                node.get("bytes").longValue());
    }

    private static byte[] payload(final JsonNode node, final ExportValidationSupport.Budget budget)
            throws IOException {
        fields(node, Set.of("path", "sha256", "bytes"));
        require(
                node.path("bytes").isIntegralNumber() && node.get("bytes").canConvertToLong(),
                "COTACOES_P3_PAYLOAD_SIZE");
        if (node.get("bytes").longValue() != 0) {
            return normalizedPin(node).read(8388608, budget);
        }
        final Path path = Path.of(text(node, "path"));
        require(
                path.isAbsolute() && path.normalize().equals(path),
                "COTACOES_P3_EMPTY_ABSOLUTE_PATH");
        for (Path part = path; part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "COTACOES_P3_EMPTY_LINK_REFUSED");
        }
        budget.check();
        require(
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS),
                "COTACOES_P3_EMPTY_REGULAR_FILE");
        final byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes(1);
        }
        require(
                bytes.length == 0
                        && ExportValidationSupport.sha256(bytes)
                                .equals(text(node, "sha256").toLowerCase(java.util.Locale.ROOT)),
                "COTACOES_P3_EMPTY_PIN_CHANGED");
        budget.check();
        return bytes;
    }

    private static void fields(final JsonNode node, final Set<String> expected) {
        require(node != null && node.isObject(), "COTACOES_P3_OBJECT_REQUIRED");
        final var names = new HashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        require(names.equals(expected), "COTACOES_P3_EXACT_FIELDS");
    }

    private static JsonNode json(final byte[] bytes) throws IOException {
        try (var parser = ExportValidationSupport.JSON.createParser(bytes)) {
            final JsonNode value = ExportValidationSupport.JSON.readTree(parser);
            require(value != null && parser.nextToken() == null, "COTACOES_P3_JSON_EOF");
            return value;
        }
    }

    private static LocalDate requestedDate(final JsonNode value) {
        require(value.isTextual(), "COTACOES_P3_REQUESTED_TEXT");
        final String literal = value.textValue().trim();
        final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        try {
            return Instant.parse(literal).atZone(zone).toLocalDate();
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(literal).atZoneSameInstant(zone).toLocalDate();
        } catch (final DateTimeParseException ignored) {
        }
        for (final DateTimeFormatter format :
                List.of(
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                .withResolverStyle(ResolverStyle.STRICT),
                        DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return format == DateTimeFormatter.ISO_LOCAL_DATE
                        ? LocalDate.parse(literal, format)
                        : LocalDateTime.parse(literal, format).toLocalDate();
            } catch (final DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("COTACOES_P3_REQUESTED_INVALID");
    }
}

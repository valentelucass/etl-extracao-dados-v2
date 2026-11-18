package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Executed only by the Root controls worker in its proven physical Original. No Source/SQL. */
public final class HistoricalContentControlPreparation {
    private HistoricalContentControlPreparation() {}

    public static ObjectNode prepare(
            final JsonNode plan, final ExportValidationSupport.Budget budget) throws Exception {
        require(
                "INDEPENDENT_EXPORT_HISTORICAL_CONTROLS_PLAN_V1".equals(text(plan, "schema"))
                        && plan.path("historicalContentOnly").asBoolean(),
                "HISTORICAL_CONTROLS_PLAN_SCOPE");
        final var context =
                ExportValidationSupport.Pin.from(plan.required("executionContextPin"))
                        .json(262144, budget);
        require(
                "ROOT_JAVA_HISTORICAL_CONTROLS_BOUND0728_V1".equals(text(context, "schema"))
                        && context.path("OriginalPid").asLong()
                                == ProcessHandle.current().parent().orElseThrow().pid()
                        && context.path("physicalOriginalObserved").asBoolean()
                        && context.path("OriginalHardMaximumBytes").asLong() == 1073741824L
                        && context.path("Source").asInt(-1) == 0
                        && context.path("HTTP").asInt(-1) == 0
                        && context.path("SQL").asInt(-1) == 0,
                "HISTORICAL_CONTROLS_ROOT_ORIGINAL_PARENT");
        final var catalogPin = ExportValidationSupport.Pin.from(plan.required("catalogPin"));
        final var catalog = catalogPin.json(262144, budget);
        require(
                "ROOT_JAVA_REALPAIR_CATALOG0725_V3_FINAL".equals(text(catalog, "schema")),
                "HISTORICAL_CONTROLS_CATALOG");
        JsonNode pair = null;
        for (final var candidate : catalog.required("pairs")) {
            if (text(plan, "pairId").equals(text(candidate, "pairId"))) {
                require(pair == null, "HISTORICAL_CONTROLS_UNIQUE_PAIR");
                pair = candidate;
            }
        }
        require(
                pair != null
                        && pair.path("historicalContentAccepted").asBoolean()
                        && !pair.path("replayPreparationBlocked").asBoolean(),
                "HISTORICAL_CONTROLS_REJECTED_PAIR");
        final String replay = text(plan, "replayId");
        UUID.fromString(replay);
        final boolean coletas = "COLETAS".equals(text(pair, "entity"));
        require(coletas || "FRETES".equals(text(pair, "entity")), "HISTORICAL_CONTROLS_ENTITY");
        final String profile =
                coletas ? ColetasIndependentRequest.PROFILE : IndependentExportRequest.PROFILE;
        final var request =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTENT_REQUEST_V1")
                        .put("replayId", replay)
                        .put("pairId", text(pair, "pairId"))
                        .put("profile", profile)
                        .put("entity", text(pair, "entity"))
                        .put("reportId", pair.path("reportId").asInt())
                        .put("zone", "America/Sao_Paulo")
                        .put("periodStart", text(pair, "periodStart"))
                        .put("periodEnd", text(pair, "periodEnd"));
        final var filters = request.putObject("filters");
        (coletas ? ColetasIndependentRequest.FILTERS : IndependentExportRequest.FRETES_FILTERS)
                .stream().sorted().forEach(key -> filters.put(key, ""));
        filters.put(
                coletas ? "picks[request_date]" : "freights[service_at]",
                text(pair, "periodStart") + " - " + text(pair, "periodEnd"));
        final var config =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONFIG_V1")
                        .put("profile", profile)
                        .put("zone", "America/Sao_Paulo")
                        .put("historicalContentOnly", true);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_EXPORT_HISTORICAL_CONTROLS_RESULT_V1")
                        .put("status", "HISTORICAL_CONTROLS_PREPARED_NOT_CONTENT_REPLAYED")
                        .put("pairId", text(pair, "pairId"))
                        .put("replayId", replay)
                        .put("historicalContentOnly", true)
                        .put("OriginalPid", context.path("OriginalPid").asLong())
                        .put("controlsMetadataOnly", true)
                        .put("businessCellsOrIdsExported", false)
                        .put("businessTotalsExported", false)
                        .put("contentEqualityAsserted", false)
                        .put("entityQualified", false)
                        .put("mainAdopted", false)
                        .put("mainResumeAllowed", false)
                        .put("dashboardConsumptionVerified", false)
                        .put("acquisitionEvidenceAuthenticationVerified", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        result.set("request", request);
        result.set("configuration", config);
        result.set("catalogPin", plan.required("catalogPin"));
        result.set(
                "mapping", coletas ? ColetasCommonAdapter.catalog() : ExportColumnCatalog.fretes());
        if (coletas) {
            coletas(plan, pair, replay, result, budget);
        }
        catalogPin.read(262144, budget);
        return result;
    }

    private static void coletas(
            final JsonNode plan,
            final JsonNode pair,
            final String replay,
            final ObjectNode result,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var qualificationPin = rootPin(pair.required("pythonAcceptance"));
        final var accepted = qualificationPin.json(262144, budget);
        require(
                accepted.path("accepted").asBoolean()
                        && accepted.path("periodQualified").asBoolean()
                        && accepted.path("all31AssociatedTogether").asBoolean()
                        && accepted.path("mechanismCases").asInt() == 131,
                "HISTORICAL_CONTROLS_ORIGINAL_131_ACCEPTANCE");
        final var source =
                rootPin(pair.path("businessPins").required("projectedSourceMappedJsonl"));
        final var workbook = rootPin(pair.path("businessPins").required("excel"));
        final byte[] sourceBytes = source.read(8388608, budget);
        require(
                sourceBytes[sourceBytes.length - 1] == '\n',
                "HISTORICAL_CONTROLS_SOURCE_CLOSED_LF");
        int headerEnd = -1;
        for (int index = 0; index < Math.min(sourceBytes.length, 1048576); index++) {
            if (sourceBytes[index] == '\n') {
                headerEnd = index;
                break;
            }
        }
        require(headerEnd > 0, "HISTORICAL_CONTROLS_HEADER1M");
        final byte[] header = Arrays.copyOf(sourceBytes, headerEnd);
        final String format = text(plan, "sourceFormat");
        require(
                List.of("MAPPED_PAYLOAD_JSONL_V1", "COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2")
                        .contains(format),
                "HISTORICAL_CONTROLS_EXPLICIT_SOURCE_FORMAT");
        final var fields = ColetasCommonAdapter.catalog().required("columns");
        if ("COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2".equals(format)) {
            final var head = ExportValidationSupport.JSON.readTree(header);
            require(
                    "COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2".equals(text(head, "schema"))
                            && "MAPPED_PAYLOAD".equals(text(head, "stage"))
                            && head.path("header").path("template_id").asInt() == 6908,
                    "HISTORICAL_CONTROLS_REAL_STAGE05_HEADER");
            final Set<String> expected = new HashSet<>();
            fields.forEach(field -> expected.add(text(field, "itemId")));
            final Set<String> actual = new HashSet<>();
            head.path("header").path("binding_evidence").fieldNames().forEachRemaining(actual::add);
            require(actual.equals(expected), "HISTORICAL_CONTROLS_REAL_ALL31_HEADER_BINDINGS");
        }
        final var qualified = qualifiedPolicyRecord(pair, budget);
        final var timestampPolicy = timestampPolicy(qualified, budget);
        final var policy =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COLETAS_COMMON31_POLICY_V1")
                        .put("attemptId", replay)
                        .put("sourceFormat", format)
                        .put("sourceSha256", source.sha256())
                        .put("workbookSha256", workbook.sha256());
        if ("COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2".equals(format)) {
            policy.put("closedProjectionHeaderSha256", ExportValidationSupport.sha256(header));
        }
        final var nullItems = policy.putArray("nullOmissionItems");
        qualified
                .required("nullOmissionFieldAllowlist")
                .forEach(
                        item -> {
                            // Identity is required before projection, so the narrower Java policy
                            // never admits null IDs.
                            if (!"item_221149".equals(item.asText())) {
                                nullItems.add(item);
                            }
                        });
        policy.set("dateBlankItems", qualified.required("dateNullBlankFieldAllowlist"));
        policy.set("timestampIntegerSecondDisplayItems", timestampPolicy.required("items"));
        policy.set("qualificationPin", ExportValidationSupport.JSON.valueToTree(qualificationPin));
        final var nativeColumns = new ArrayList<ExactExportTable.Column>();
        fields.forEach(
                field ->
                        nativeColumns.add(
                                new ExactExportTable.Column(
                                        text(field, "name"),
                                        field.path("nativeColumn").asInt(),
                                        field.path("nativeColumn").asInt(),
                                        false)));
        final var nativeTable =
                BoundedExportWorkbook.readCommonRows(
                        workbook.read(8388608, budget), nativeColumns, budget);
        require("1900".equals(nativeTable.epoch()), "HISTORICAL_CONTROLS_QUALIFIED1900_EPOCH");
        final var presentation =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COLETAS_COMMON31_PRESENTATION_EVIDENCE_V1")
                        .put("sourceSha256", source.sha256())
                        .put("workbookSha256", workbook.sha256())
                        .put(
                                "catalogSha256",
                                "87841247dc337e37f25c7cef8f1b12f026ba9f1de80ee83ab38cf70c09e56a9c");
        final var carriers = presentation.putObject("columnCarriers");
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index);
            final String kind = text(field, "kind");
            if (!List.of("date", "timestamp", "decimal").contains(kind)) {
                continue;
            }
            final var observed = new TreeSet<String>();
            for (final var row : nativeTable.rows()) {
                budget.check();
                final String carrier = row.cells().get(index).carrier();
                if (carrier != null && !carrier.isEmpty()) {
                    require(
                            QualifiedColetasModel.carrierRole(kind, carrier),
                            "HISTORICAL_CONTROLS_EXISTING131_CARRIER_QUALIFIER");
                    observed.add(carrier);
                    require(observed.size() <= 64, "HISTORICAL_CONTROLS_CARRIER_BOUND");
                }
            }
            final var admitted = carriers.putArray(text(field, "itemId"));
            observed.forEach(admitted::add);
        }
        source.read(8388608, budget);
        workbook.read(8388608, budget);
        qualificationPin.read(262144, budget);
        recordedBytes(
                pair.path("businessPins").required("fullComparisonReport").required("pin"),
                16777216,
                budget);
        result.set("policy", policy);
        result.set("presentation", presentation);
        result.put("frozen131PolicyRecordVerified", true)
                .put("realHeaderAdmitted", "COLETAS_PRIVATE_INCREMENTAL_STAGE05_V2".equals(format))
                .put("carrierQualifier", "UNCHANGED_QUALIFIED_COLETAS_MODEL_CARRIER_ROLE")
                .put("sourceFormat", format)
                .put("parserRAWClaimed", false);
    }

    /**
     * Both preparation and comparison use the exact frozen 131 policy record, never a caller list.
     */
    static void requireQualifiedPolicy(
            final JsonNode policy, final JsonNode pair, final ExportValidationSupport.Budget budget)
            throws Exception {
        final var qualified = qualifiedPolicyRecord(pair, budget);
        final var timestamp = timestampPolicy(qualified, budget);
        final var allowedNull = ExportValidationSupport.JSON.createArrayNode();
        qualified
                .required("nullOmissionFieldAllowlist")
                .forEach(
                        item -> {
                            if (!"item_221149".equals(item.asText())) {
                                allowedNull.add(item);
                            }
                        });
        require(
                allowedNull.equals(policy.required("nullOmissionItems"))
                        && qualified
                                .required("dateNullBlankFieldAllowlist")
                                .equals(policy.required("dateBlankItems"))
                        && timestamp
                                .required("items")
                                .equals(policy.required("timestampIntegerSecondDisplayItems")),
                "HISTORICAL_CONTROLS_POLICY_LISTS_EXACT_FROZEN131_DECISIONS");
    }

    private static JsonNode qualifiedPolicyRecord(
            final JsonNode pair, final ExportValidationSupport.Budget budget) throws Exception {
        final var report = pair.path("businessPins").required("fullComparisonReport");
        require(
                report.path("complete").asBoolean()
                        && report.path("logicalBytes").asLong() <= 134217728
                        && report.path("records").asLong() <= 100000,
                "HISTORICAL_CONTROLS_REPORT_BOUNDS");
        final byte[] compressed = recordedBytes(report.required("pin"), 16777216, budget);
        final var logicalDigest = MessageDigest.getInstance("SHA-256");
        long logicalBytes = 0, lines = 0;
        JsonNode qualified = null;
        try (var stream =
                new BufferedInputStream(
                        new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            final var line = new ByteArrayOutputStream();
            for (int next = stream.read(); next != -1; next = stream.read()) {
                if ((logicalBytes & 65535) == 0) {
                    budget.check();
                }
                logicalDigest.update((byte) next);
                logicalBytes++;
                require(
                        logicalBytes <= report.path("logicalBytes").asLong(),
                        "HISTORICAL_CONTROLS_REPORT_LOGICAL_BOUND");
                if (next == '\n') {
                    lines++;
                    final byte[] data = line.toByteArray();
                    // Only this qualified control record is parsed/exported; no tuple/ID/value is
                    // retained.
                    if (new String(data, StandardCharsets.UTF_8)
                            .contains("JOINT31_131_POLICY_AND_ADVERSARIAL_GUARDS")) {
                        final var candidate = ExportValidationSupport.JSON.readTree(data);
                        if ("JOINT31_131_POLICY_AND_ADVERSARIAL_GUARDS"
                                .equals(candidate.path("recordKind").asText())) {
                            require(qualified == null, "HISTORICAL_CONTROLS_POLICY_ONCE");
                            qualified = candidate;
                        }
                    }
                    line.reset();
                    require(
                            lines <= report.path("records").asLong(),
                            "HISTORICAL_CONTROLS_REPORT_RECORD_BOUND");
                } else {
                    require(line.size() < 1048576, "HISTORICAL_CONTROLS_REPORT_LINE1M");
                    line.write(next);
                }
            }
            require(line.size() == 0, "HISTORICAL_CONTROLS_REPORT_EOF_LF");
        }
        require(
                qualified != null
                        && logicalBytes == report.path("logicalBytes").asLong()
                        && lines == report.path("records").asLong()
                        && HexFormat.of()
                                .formatHex(logicalDigest.digest())
                                .equals(text(report, "logicalSha256").toLowerCase(Locale.ROOT))
                        && qualified.path("allExistingDecisionsReused").asBoolean()
                        && !qualified.path("ordinalPairingApplied").asBoolean()
                        && !qualified.path("deduplicationApplied").asBoolean(),
                "HISTORICAL_CONTROLS_COMPLETE_QUALIFIED131_REPORT");
        final var fields = ColetasCommonAdapter.catalog().required("columns");
        final var expectedItems = ExportValidationSupport.JSON.createArrayNode();
        fields.forEach(field -> expectedItems.add(text(field, "itemId")));
        require(
                expectedItems.equals(qualified.required("tupleItemOrder"))
                        && qualified.path("zeroPendingRequiredForQualification").isBoolean()
                        && qualified.path("zeroPendingRequiredForQualification").asBoolean()
                        && !qualified.path("originalAtomsAndAxesChanged").asBoolean(),
                "HISTORICAL_CONTROLS_EXACT_QUALIFIED31_ORDER_AND_DECISIONS");
        require(
                qualified
                                .required("timestampDisplayPolicy")
                                .equals(
                                        pair.path("independentEvidence")
                                                .required("timestampPolicy"))
                        && qualified
                                .required("timestampDisplayAcceptance")
                                .equals(
                                        pair.path("independentEvidence")
                                                .required("independentTimestampDisplayAcceptance")),
                "HISTORICAL_CONTROLS_EXACT_EXISTING_TIMESTAMP_QUALIFICATION");
        // A matching descriptor is not proof that the original acceptance still exists unchanged.
        require(
                rootPin(qualified.required("timestampDisplayAcceptance"))
                        .json(262144, budget)
                        .isObject(),
                "HISTORICAL_CONTROLS_TIMESTAMP_ACCEPTANCE_PINNED_OBJECT");
        return qualified;
    }

    private static JsonNode timestampPolicy(
            final JsonNode qualified, final ExportValidationSupport.Budget budget)
            throws Exception {
        final var timestampPolicy =
                rootPin(qualified.required("timestampDisplayPolicy")).json(262144, budget);
        require(
                timestampPolicy.path("approved").asBoolean()
                        && timestampPolicy
                                .path("twoTimestampColumnsQualifiedAtWorkbookPrecision")
                                .asBoolean()
                        && "1900".equals(text(timestampPolicy, "epoch"))
                        && "164".equals(text(timestampPolicy, "numFmtId"))
                        && "1".equals(text(timestampPolicy, "applyNumberFormat")),
                "HISTORICAL_CONTROLS_TIMESTAMP_POLICY_REQUIRED");
        return timestampPolicy;
    }

    private static ExportValidationSupport.Pin rootPin(final JsonNode pin) {
        require(text(pin, "sha256").matches("[A-F0-9]{64}"), "HISTORICAL_CONTROLS_ROOT_PIN_CASE");
        return new ExportValidationSupport.Pin(
                Path.of(text(pin, "path")),
                text(pin, "sha256").toLowerCase(Locale.ROOT),
                pin.path("bytes").longValue());
    }

    private static byte[] recordedBytes(
            final JsonNode pin, final int maximum, final ExportValidationSupport.Budget budget)
            throws Exception {
        final long size = pin.path("bytes").asLong();
        final var path = Path.of(text(pin, "path"));
        require(
                path.isAbsolute()
                        && size > 0
                        && size <= maximum
                        && text(pin, "sha256").matches("[A-F0-9]{64}"),
                "HISTORICAL_CONTROLS_RECORDED_REPORT_PIN");
        for (var at = path; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "HISTORICAL_CONTROLS_REPORT_LINK");
        }
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(Math.toIntExact(size + 1));
        }
        budget.check();
        require(
                bytes.length == size
                        && ExportValidationSupport.sha256(bytes)
                                .equals(text(pin, "sha256").toLowerCase(Locale.ROOT)),
                "HISTORICAL_CONTROLS_RECORDED_REPORT_CHANGED");
        return bytes;
    }
}

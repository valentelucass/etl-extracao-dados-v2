package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNAPPROVED CANDIDATE. Read actual permanent capture, join independently scoped facts, retain
 * every physical occurrence, generate the bridge input itself, and exercise the typed bridge. No
 * Source process, SQL, production publication or authority is implemented by this component.
 */
public final class CapturedDashboardInputProducer {
    private static final List<String> COLETAS_FIELDS =
            List.of(
                    "id",
                    "sequence_code",
                    "request_date",
                    "service_date",
                    "finish_date",
                    "status",
                    "invoices_volumes",
                    "invoices_weight",
                    "taxed_weight",
                    "invoices_value",
                    "pck_mik_mft_sequence_code",
                    "pck_cor_nickname",
                    "pck_pds_cty_name",
                    "pck_pds_cty_sae_code",
                    "pck_pds_postal_code",
                    "pck_prn_name",
                    "pck_crn_psn_nickname",
                    "pck_uer_name",
                    "cancellation_reason");
    private static final Map<String, String> FRETES_FIELDS =
            Map.ofEntries(
                    Map.entry("id", "INTEGER"),
                    Map.entry("sequence_code", "INTEGER"),
                    Map.entry("corporation_sequence_number", "INTEGER"),
                    Map.entry("invoices_volumes", "INTEGER"),
                    Map.entry("taxed_weight", "DECIMAL"),
                    Map.entry("service_at", "TEMPORAL"),
                    Map.entry("fit_pyr_name", "TEXT"),
                    Map.entry("fit_sdr_nickname", "TEXT"),
                    Map.entry("fit_rpt_nickname", "TEXT"),
                    Map.entry("fit_ony_sae_code", "TEXT"),
                    Map.entry("fit_diy_sae_code", "TEXT"),
                    Map.entry("fit_dos_cty_name", "TEXT"),
                    Map.entry("fit_crn_psn_nickname", "TEXT"),
                    Map.entry("fit_fsn_name", "TEXT"));
    private static final String UNIQUE = "UNIQUE_TYPED_SOURCE_ID";
    private static final String UNIFORM = "UNIFORM_CONSUMER_PROJECTION_PER_TYPED_ID";
    private static final Map<String, String> FRETES_RULE_FIELDS =
            Map.ofEntries(
                    Map.entry("total", "DECIMAL"),
                    Map.entry("courtesy", "BOOLEAN"),
                    Map.entry("status", "TEXT"),
                    Map.entry("type", "TEXT"),
                    Map.entry("fit_pyr_document", "TEXT"),
                    Map.entry("fit_fhe_cte_number", "INTEGER"),
                    Map.entry("fit_fhe_cte_series", "INTEGER"),
                    Map.entry("fit_fhe_cte_key", "TEXT"),
                    Map.entry("fit_fhe_cte_issued_at", "RAW_ATOM"),
                    Map.entry("nfse_number", "TEXT"));

    private CapturedDashboardInputProducer() {}

    private static final class Group {
        private final String key;
        private final ObjectNode source;
        private final JsonNode originalProjection;
        private final List<ObjectNode> occurrences = new ArrayList<>();
        private boolean uniform = true;

        private Group(final String key, final ObjectNode source, final JsonNode projection) {
            this.key = key;
            this.source = source;
            this.originalProjection = projection;
        }
    }

    public static void validateRecipe(final JsonNode selection, final JsonNode recipe) {
        require(
                "CAPTURED_DASHBOARD_INPUT_RECIPE_V1".equals(text(recipe, "schema"))
                        && recipe.required("profile").equals(selection.required("profile"))
                        && recipe.required("requestPin").equals(selection.required("requestPin"))
                        && recipe.required("mappingPin").equals(selection.required("mappingPin"))
                        && recipe.required("sourceConfigurationSha256")
                                .equals(selection.required("sourceConfigurationSha256")),
                "DASHBOARD_CAPTURE_RECIPE_SCOPE");
        require(
                UNIQUE.equals(text(recipe, "grainModel"))
                        || UNIFORM.equals(text(recipe, "grainModel")),
                "DASHBOARD_EXPLICIT_GRAIN_MODEL_REQUIRED");
        final var root = Path.of(text(selection, "outputDirectory")).toAbsolutePath().normalize();
        final var run = Path.of(text(recipe, "runDirectory"));
        require(
                run.isAbsolute()
                        && run.normalize().startsWith(root)
                        && !run.normalize().equals(root),
                "DASHBOARD_RECIPE_PRIVATE_OUTPUT_SCOPE");
    }

    public static ObjectNode produceAndCheck(
            final JsonNode pair, final ExportValidationSupport.Budget budget) throws Exception {
        require(!pair.has("dashboardBridgePin"), "DASHBOARD_ONE_INPUT_ROUTE_REQUIRED");
        final var recipePin =
                ExportValidationSupport.Pin.from(pair.required("dashboardInputRecipePin"));
        final var recipe = recipePin.json(262144, budget);
        final var requestPin = ExportValidationSupport.Pin.from(pair.required("requestPin"));
        final var request = requestPin.json(262144, budget);
        final var admissionPin = ExportValidationSupport.Pin.from(pair.required("admissionPin"));
        final var admission = admissionPin.json(262144, budget);
        final Map<Path, ExportValidationSupport.Pin> inputs = new LinkedHashMap<>();
        track(inputs, recipePin);
        track(inputs, requestPin);
        track(inputs, admissionPin);
        for (final String field : List.of("configPin", "policyPin")) {
            if (pair.has(field)) {
                track(inputs, ExportValidationSupport.Pin.from(pair.required(field)));
            }
        }
        for (final String side : List.of("api", "ui")) {
            for (final String field : List.of("dataPin", "evidencePin")) {
                if (pair.path(side).has(field)) {
                    track(
                            inputs,
                            ExportValidationSupport.Pin.from(pair.path(side).required(field)));
                }
            }
        }
        for (final String field :
                List.of(
                        "requestPin",
                        "mappingPin",
                        "sourceConfigurationSha256",
                        "dashboardInputRecipePin")) {
            require(
                    pair.required(field).equals(admission.required(field)),
                    "DASHBOARD_RECIPE_PAIR_ADMISSION");
        }
        require(
                "CAPTURED_DASHBOARD_INPUT_RECIPE_V1".equals(text(recipe, "schema"))
                        && recipe.required("requestPin").equals(pair.required("requestPin"))
                        && recipe.required("mappingPin").equals(pair.required("mappingPin"))
                        && recipe.required("sourceConfigurationSha256")
                                .equals(pair.required("sourceConfigurationSha256"))
                        && recipe.required("profile").equals(request.required("profile")),
                "DASHBOARD_ACTUAL_CAPTURE_RECIPE_BINDING");
        final boolean coletas = ColetasIndependentRequest.PROFILE.equals(text(request, "profile"));
        require(
                coletas || IndependentExportRequest.PROFILE.equals(text(request, "profile")),
                "DASHBOARD_CAPTURE_PROFILE");
        final var capturePin =
                ExportValidationSupport.Pin.from(pair.path("api").required("dataPin"));
        require(
                recipe.has("referenceContextPin") != recipe.has("factsSnapshotPin"),
                "DASHBOARD_ONE_GOVERNANCE_INPUT_ROUTE");
        final boolean deriveReferences = recipe.has("referenceContextPin");
        final var factsPin =
                ExportValidationSupport.Pin.from(
                        recipe.required(
                                deriveReferences ? "referenceContextPin" : "factsSnapshotPin"));
        final var mappingPin = ExportValidationSupport.Pin.from(recipe.required("mappingPin"));
        final var mapping = mappingPin.json(262144, budget);
        track(inputs, capturePin);
        track(inputs, factsPin);
        track(inputs, mappingPin);
        final var groups = new LinkedHashMap<String, Group>();
        final var issues = new LinkedHashMap<String, Long>();
        final long[] physical = {0};
        final var start = LocalDate.parse(text(request, "periodStart"));
        final var end = LocalDate.parse(text(request, "periodEnd"));
        final byte[] captured = capturePin.read(8388608, budget);
        if (coletas) {
            readColetas(captured, groups, physical, issues, start, end, budget);
        } else {
            final var columns = IndependentExportValidation.columns(mapping);
            final Map<String, Integer> columnBySource = new LinkedHashMap<>();
            mapping.required("columns")
                    .forEach(
                            column -> {
                                final String pointer = text(column, "sourceJsonPointer");
                                if (pointer.startsWith("/") && pointer.indexOf('/', 1) < 0) {
                                    columnBySource.put(
                                            pointer.substring(1), column.path("apiColumn").asInt());
                                }
                            });
            final Map<String, String> sourceFields = new LinkedHashMap<>(FRETES_FIELDS);
            if (deriveReferences) {
                sourceFields.putAll(FRETES_RULE_FIELDS);
            }
            require(
                    columnBySource.keySet().containsAll(sourceFields.keySet()),
                    "DASHBOARD_ALL_SOURCE_BINDINGS");
            final var rules = recipe.required("nativeSourceBindings");
            // No JavaScript, spreadsheet formulas, UTC guessing or business rounding.
            BoundedExportWorkbook.scanCapturedFretes(
                    captured,
                    columns,
                    budget,
                    start,
                    end,
                    row -> {
                        physical[0]++;
                        require(physical[0] <= 12000, "DASHBOARD_CAPTURE_ROW_BOUND");
                        try {
                            final var source = ExportValidationSupport.JSON.createObjectNode();
                            final var original = ExportValidationSupport.JSON.createObjectNode();
                            for (final var entry : sourceFields.entrySet()) {
                                final var atom =
                                        row.cells()
                                                .getOrDefault(
                                                        columnBySource.get(entry.getKey()),
                                                        new ExactExportTable.Atom("ABSENT", ""));
                                original.set(
                                        entry.getKey(),
                                        ExportValidationSupport.JSON.valueToTree(atom));
                                source.set(
                                        entry.getKey(),
                                        nativeValue(
                                                entry.getKey(),
                                                entry.getValue(),
                                                atom,
                                                row.epoch(),
                                                rules,
                                                recipe,
                                                inputs,
                                                budget));
                            }
                            final var locator =
                                    ExportValidationSupport.JSON
                                            .createObjectNode()
                                            .put("format", "XLSX_NATIVE")
                                            .put("sheet", row.sheet())
                                            .put("physicalRow", row.physicalRow())
                                            .put("allPhysicalColumnsRetainedInCapture", true)
                                            .put("physicalColumns", row.headers().size());
                            add(groups, source, original, locator);
                        } catch (final IllegalArgumentException pending) {
                            issue(issues, safeReason(pending));
                        } catch (final Exception pending) {
                            issue(issues, "DASHBOARD_NATIVE_BINDING_EVIDENCE_PENDING");
                        }
                    });
        }
        final var snapshot = factsPin.json(8388608, budget);
        require(
                (deriveReferences
                                        ? "DASHBOARD_RAW_REFERENCE_CONTEXT_V1"
                                        : "DASHBOARD_GOVERNED_FACT_SNAPSHOT_V1")
                                .equals(text(snapshot, "schema"))
                        && snapshot.required("requestPin").equals(pair.required("requestPin"))
                        && snapshot.required("sourceConfigurationSha256")
                                .equals(pair.required("sourceConfigurationSha256"))
                        && snapshot.required("profile").equals(request.required("profile"))
                        && text(snapshot, "periodStart").equals(text(request, "periodStart"))
                        && text(snapshot, "periodEnd").equals(text(request, "periodEnd"))
                        && (coletas ? "DATAEXPORT6908.id" : "DATAEXPORT6389.id")
                                .equals(text(snapshot, "idNamespace")),
                "DASHBOARD_INDEPENDENT_FACT_SCOPE");
        final Map<String, JsonNode> facts = new LinkedHashMap<>();
        if (!deriveReferences) {
            require(
                    snapshot.path("records").isArray() && snapshot.get("records").size() <= 12000,
                    "DASHBOARD_FACT_BOUND");
            for (final var fact : snapshot.get("records")) {
                final var id = fact.required("idAtom");
                require(
                        "INTEGER".equals(text(id, "type"))
                                && text(id, "value").matches("[1-9][0-9]{0,127}"),
                        "DASHBOARD_TYPED_FACT_ID_REQUIRED");
                require(
                        facts.putIfAbsent("INTEGER:" + text(id, "value"), fact) == null,
                        "DASHBOARD_DUPLICATE_FACT_KEY");
            }
        }
        final String grain = text(recipe, "grainModel");
        require(
                UNIQUE.equals(grain) || UNIFORM.equals(grain),
                "DASHBOARD_EXPLICIT_GRAIN_MODEL_REQUIRED");
        final var grainPin = ExportValidationSupport.Pin.from(recipe.required("grainEvidencePin"));
        final var grainEvidence = grainPin.json(262144, budget);
        track(inputs, grainPin);
        require(
                "DASHBOARD_GRAIN_BINDING_V1".equals(text(grainEvidence, "schema"))
                        && grain.equals(text(grainEvidence, "model"))
                        && grainEvidence.required("requestPin").equals(pair.required("requestPin"))
                        && grainEvidence
                                .required("consumerContractPin")
                                .equals(recipe.required("consumerContractPin")),
                "DASHBOARD_GRAIN_EVIDENCE_SCOPE");
        final var consumerPin =
                ExportValidationSupport.Pin.from(recipe.required("consumerContractPin"));
        require(
                consumerContract(text(request, "profile")).equals(consumerPin.json(262144, budget)),
                "DASHBOARD_CURRENT_CONSUMER_CONTRACT_REQUIRED");
        track(inputs, consumerPin);
        final var run = Path.of(text(recipe, "runDirectory"));
        require(
                run.isAbsolute() && Files.isDirectory(run.getParent()) && !Files.exists(run),
                "DASHBOARD_FRESH_PRODUCER_RUN");
        for (Path part = run.getParent(); part != null; part = part.getParent()) {
            require(!Files.isSymbolicLink(part), "DASHBOARD_PRODUCER_LINK");
        }
        Files.createDirectory(run);
        final var materialized = new ArrayList<ObjectNode>();
        final Map<String, JsonNode> referenceCache = new LinkedHashMap<>();
        final var bindingChunks = new ArrayList<ExportValidationSupport.Pin>();
        final var memberChunk = ExportValidationSupport.JSON.createArrayNode();
        int chunkNumber = 0;
        long consumed = 0;
        for (final var group : groups.values()) {
            budget.check();
            consumed += group.occurrences.size();
            if (!group.uniform || UNIQUE.equals(grain) && group.occurrences.size() != 1) {
                issue(issues, "DASHBOARD_AMBIGUOUS_OR_UNADMITTED_GRAIN");
                continue;
            }
            final JsonNode matched;
            if (deriveReferences) {
                try {
                    matched =
                            LegacyReferenceGovernanceAssembler.derive(
                                    pair,
                                    recipe,
                                    group.source,
                                    snapshot,
                                    factsPin,
                                    capturePin,
                                    run.resolve(
                                            "derived-governance-"
                                                    + (materialized.size() + 1)
                                                    + ".json"),
                                    pin -> track(inputs, pin),
                                    referenceCache,
                                    budget);
                } catch (final IllegalArgumentException unavailable) {
                    issue(issues, safeReason(unavailable));
                    continue;
                }
            } else {
                matched = facts.get(group.key);
            }
            if (matched == null) {
                issue(issues, "DASHBOARD_GOVERNED_FACT_MISSING");
                continue;
            }
            final var governance = (ObjectNode) matched.required("governance").deepCopy();
            require(
                    !governance.has("sourceBindingEvidencePin")
                            && !governance.has("sourceServiceAt"),
                    "DASHBOARD_EXTERNAL_FACT_CANNOT_IMPERSONATE_CAPTURE");
            require(
                    group.key.substring("INTEGER:".length()).equals(text(governance, "sourceKey")),
                    "DASHBOARD_FACT_ID_BINDING");
            final var provenancePin =
                    ExportValidationSupport.Pin.from(matched.required("provenancePin"));
            final var provenance = provenancePin.json(262144, budget);
            track(inputs, provenancePin);
            require(
                    "DASHBOARD_REFERENCE_FACT_PROVENANCE_V1".equals(text(provenance, "schema"))
                            && (deriveReferences
                                            ? "DERIVED_FROM_CAPTURE_AND_REFERENCE_SNAPSHOTS"
                                            : "GOVERNED_REFERENCE_NOT_CAPTURED_API")
                                    .equals(text(provenance, "originKind"))
                            && matched.required("idAtom").equals(provenance.required("idAtom"))
                            && recipe.required("requestPin")
                                    .equals(provenance.required("requestPin"))
                            && recipe.required("profile").equals(provenance.required("profile"))
                            && text(request, "periodStart").equals(text(provenance, "periodStart"))
                            && text(request, "periodEnd").equals(text(provenance, "periodEnd"))
                            && provenance
                                    .required("governanceSha256")
                                    .asText()
                                    .equals(
                                            ExportValidationSupport.sha256(
                                                    ExportValidationSupport.canonicalBytes(
                                                            governance)))
                            && recipe.required("sourceConfigurationSha256")
                                    .equals(provenance.required("sourceConfigurationSha256")),
                    "DASHBOARD_REFERENCE_FACT_PROVENANCE_SCOPE");
            for (final String field : List.of("exclusionEvidencePin", "snapshotEvidencePin")) {
                final var pin = ExportValidationSupport.Pin.from(governance.required(field));
                pin.json(262144, budget);
                track(inputs, pin);
            }
            final var governedScope = governance.required(coletas ? "logisticsRegion" : "billing");
            final var governedScopePin =
                    ExportValidationSupport.Pin.from(governedScope.required("evidencePin"));
            governedScopePin.json(262144, budget);
            track(inputs, governedScopePin);
            if (!coletas) {
                governance.set("sourceServiceAt", group.source.required("service_at"));
            }
            // This is a derived observation, not a Root signature or semantic approval.
            final var member =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("sourceKey", text(governance, "sourceKey"));
            member.set("occurrences", ExportValidationSupport.JSON.valueToTree(group.occurrences));
            member.put(
                    "originalConsumerProjectionSha256",
                    ExportValidationSupport.sha256(
                            ExportValidationSupport.canonicalBytes(group.originalProjection)));
            member.put("allOtherFieldsRetainedInOriginalCapture", true);
            final var row = ExportValidationSupport.JSON.createObjectNode();
            row.set("source", group.source);
            row.set("governance", governance);
            row.set("capturedSourceMember", member);
            materialized.add(row);
            memberChunk.add(member);
            if (memberChunk.size() == 128) {
                final var binding = binding(pair, recipePin, capturePin, factsPin, memberChunk);
                final var pin =
                        writeJson(
                                run.resolve("source-binding-" + ++chunkNumber + ".json"),
                                binding,
                                262144,
                                budget);
                bindingChunks.add(pin);
                attachBinding(materialized, materialized.size() - memberChunk.size(), pin);
                memberChunk.removeAll();
            }
        }
        if (!memberChunk.isEmpty()) {
            final var pin =
                    writeJson(
                            run.resolve("source-binding-" + ++chunkNumber + ".json"),
                            binding(pair, recipePin, capturePin, factsPin, memberChunk),
                            262144,
                            budget);
            bindingChunks.add(pin);
            attachBinding(materialized, materialized.size() - memberChunk.size(), pin);
        }
        if (consumed != physical[0]) {
            issue(issues, "DASHBOARD_UNPROJECTED_CAPTURE_OCCURRENCES");
        }
        if (physical[0] == 0) {
            issue(issues, "DASHBOARD_EMPTY_CAPTURE_REQUIRES_PUBLICATION_POLICY");
        }
        final var report =
                IndependentDashboardBridge.contract()
                        .put("producerSchema", "CAPTURED_DASHBOARD_INPUT_PRODUCTION_V1")
                        .put("sourceRows", physical[0])
                        .put("sourceDistinctIds", groups.size())
                        .put("preparedEntityRows", materialized.size())
                        .put("externalFactRecords", facts.size())
                        .put("governanceDerivedFromRawReferenceDatasets", deriveReferences)
                        .put("captureWasReadAndPinned", true)
                        .put("inputWasProducedFromActualCapture", false)
                        .put("inputWasConsumedByTypedBridge", false)
                        .put("authorityCreated", false)
                        .put("referenceFactAuthenticationVerified", false)
                        .put("semanticApprovalIssued", false)
                        .put("everyOccurrenceProjectedAndJoined", issues.isEmpty())
                        .put("sourceDistinctIdCountComplete", consumed == physical[0])
                        .put(
                                "captureOrigin",
                                coletas
                                        ? "MAPPED_PAYLOAD_JSONL"
                                        : "XLSX_NATIVE_PROJECTION_NOT_SUPPLIER_JSON");
        report.set("capturePin", ExportValidationSupport.JSON.valueToTree(capturePin));
        report.put("attemptId", text(request, "attemptId"))
                .put("profile", text(request, "profile"));
        report.set("requestPin", pair.required("requestPin"));
        report.set("apiDataPin", pair.path("api").required("dataPin"));
        report.set("uiDataPin", pair.path("ui").required("dataPin"));
        report.set("pairAdmissionPin", pair.required("admissionPin"));
        report.set("sourceConfigurationSha256", pair.required("sourceConfigurationSha256"));
        report.set("recipePin", ExportValidationSupport.JSON.valueToTree(recipePin));
        report.set("governanceInputPin", ExportValidationSupport.JSON.valueToTree(factsPin));
        report.set("sourceBindingPins", ExportValidationSupport.JSON.valueToTree(bindingChunks));
        report.set("pendingReasonCounts", ExportValidationSupport.JSON.valueToTree(issues));
        if (!issues.isEmpty()) {
            report.put("status", "PENDING_CAPTURE_TO_GOVERNED_INPUT");
            sealInputs(inputs, budget);
            report.put("producerInputPinsRechecked", inputs.size())
                    .put("producerInputSealVerified", true);
            final var pin =
                    writeJson(run.resolve("production.pending.json"), report, 262144, budget);
            report.set("producerResultPin", ExportValidationSupport.JSON.valueToTree(pin));
            return report;
        }
        // Validate existing consumer mapping before publishing the generated input as complete.
        final var typed = new ArrayList<IndependentDashboardBridge.PreparedRow>();
        for (final var row : materialized) {
            final var prepared =
                    coletas
                            ? IndependentDashboardBridge.coleta(
                                    row.get("source"), row.get("governance"))
                            : IndependentDashboardBridge.frete(
                                    row.get("source"), row.get("governance"));
            requireConsumerColumns(prepared, consumerContract(text(request, "profile")));
            typed.add(prepared);
        }
        IndependentDashboardBridge.requireUniquePublicationGrain(
                typed, coletas ? "ID" : "frete_id");
        final var rowsPin =
                writeRows(run.resolve("capture-governed-input.jsonl"), materialized, budget);
        final var plan =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "INDEPENDENT_DASHBOARD_BRIDGE_PLAN_V1")
                        .put("profile", text(request, "profile"))
                        .put("outputPath", run.resolve("consumer-typed-stage.jsonl").toString());
        plan.set("requestPin", pair.required("requestPin"));
        plan.set("apiDataPin", pair.path("api").required("dataPin"));
        plan.set("uiDataPin", pair.path("ui").required("dataPin"));
        plan.set("rowsPin", ExportValidationSupport.JSON.valueToTree(rowsPin));
        final var planPin =
                writeJson(run.resolve("generated-bridge-plan.json"), plan, 262144, budget);
        final var generatedPair = pair.deepCopy();
        ((ObjectNode) generatedPair)
                .set("dashboardBridgePin", ExportValidationSupport.JSON.valueToTree(planPin));
        final var consumedBridge = IndependentDashboardBridge.prepare(generatedPair, budget);
        report.put("status", "TYPED_INPUT_PRODUCED_AND_CONSUMED_PUBLICATION_PENDING");
        report.put("inputWasProducedFromActualCapture", true)
                .put("inputWasConsumedByTypedBridge", true);
        report.put("consumerFormatCompatibilityChecked", true)
                .put("productionPersistencePerformed", false);
        report.set("generatedRowsPin", ExportValidationSupport.JSON.valueToTree(rowsPin));
        report.set("generatedBridgePlanPin", ExportValidationSupport.JSON.valueToTree(planPin));
        report.set("consumerStage", consumedBridge);
        sealInputs(inputs, budget);
        report.put("producerInputPinsRechecked", inputs.size())
                .put("producerInputSealVerified", true);
        final var resultPin =
                writeJson(run.resolve("production.complete.json"), report, 262144, budget);
        report.set("producerResultPin", ExportValidationSupport.JSON.valueToTree(resultPin));
        return report;
    }

    private static void readColetas(
            final byte[] capture,
            final Map<String, Group> groups,
            final long[] physical,
            final Map<String, Long> issues,
            final LocalDate start,
            final LocalDate end,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                capture.length > 0 && capture[capture.length - 1] == '\n',
                "DASHBOARD_COMPLETE_JSONL");
        for (final byte value : capture) {
            require(value != '\r', "DASHBOARD_CAPTURE_LF_ONLY_FOR_EXACT_SPANS");
        }
        final var fields = ColetasCommonAdapter.catalog().required("columns");
        final Map<String, Integer> positions = new LinkedHashMap<>();
        int index = 0;
        for (final var field : fields) {
            positions.put(text(field, "sourceField"), index++);
        }
        long offset = 0;
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(capture),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(
                                                java.nio.charset.CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(
                        ++physical[0] <= 12000 && !line.isBlank() && line.length() <= 262144,
                        "DASHBOARD_JSONL_ROW_BOUND");
                final byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
                final var locator =
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("format", "MAPPED_PAYLOAD_JSONL")
                                .put("ordinal", physical[0])
                                .put("byteStart", offset)
                                .put("bytes", bytes.length)
                                .put("rowSha256", ExportValidationSupport.sha256(bytes));
                offset += bytes.length;
                try {
                    final JsonNode raw;
                    try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                        raw = ExportValidationSupport.JSON.readTree(parser);
                        require(
                                raw != null && raw.isObject() && parser.nextToken() == null,
                                "DASHBOARD_SOURCE_OBJECT");
                    }
                    final var date = LocalDate.parse(text(raw, "request_date"));
                    require(!date.isBefore(start) && !date.isAfter(end), "DASHBOARD_SOURCE_PERIOD");
                    final var policy =
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("sourceFormat", "MAPPED_PAYLOAD_JSONL_V1");
                    final var atoms =
                            ColetasSourceReader.read(bytes, fields, policy, budget).get(0).atoms();
                    final var projection = ExportValidationSupport.JSON.createObjectNode();
                    COLETAS_FIELDS.forEach(
                            field -> projection.set(field, atoms.get(positions.get(field))));
                    add(groups, (ObjectNode) raw, projection, locator);
                } catch (final IllegalArgumentException pending) {
                    issue(issues, safeReason(pending));
                }
            }
        }
        require(offset == capture.length, "DASHBOARD_EXACT_CAPTURE_ROW_SPANS");
    }

    private static void add(
            final Map<String, Group> groups,
            final ObjectNode source,
            final JsonNode originalProjection,
            final ObjectNode locator) {
        require(
                source.path("id").isIntegralNumber()
                        && source.path("id").bigIntegerValue().signum() > 0,
                "DASHBOARD_CAPTURE_INTEGER_ID");
        final String key = "INTEGER:" + source.get("id").bigIntegerValue();
        final var current =
                groups.computeIfAbsent(key, ignored -> new Group(key, source, originalProjection));
        current.uniform &= current.originalProjection.equals(originalProjection);
        current.occurrences.add(locator);
    }

    private static JsonNode nativeValue(
            final String field,
            final String kind,
            final ExactExportTable.Atom atom,
            final String epoch,
            final JsonNode rules,
            final JsonNode recipe,
            final Map<Path, ExportValidationSupport.Pin> inputs,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        if ("RAW_ATOM".equals(kind)) {
            return ExportValidationSupport.JSON.valueToTree(atom);
        }
        if ("ABSENT".equals(atom.type()) || "BLANK".equals(atom.type())) {
            final var policy = rules.path("nullBindings").path(field);
            require(
                    "NATIVE_NONVALUE_TO_EXPLICIT_NULL_V1".equals(policy.path("model").asText())
                            && policy.path("allowedNativePresence").isArray()
                            && java.util.stream.StreamSupport.stream(
                                            policy.get("allowedNativePresence").spliterator(),
                                            false)
                                    .anyMatch(value -> atom.type().equals(value.asText())),
                    "DASHBOARD_FIELD_NULL_BINDING_PENDING");
            final var proofPin = ExportValidationSupport.Pin.from(policy.required("evidencePin"));
            final var proof = proofPin.json(262144, budget);
            track(inputs, proofPin);
            requireScopedNativeProof(proof, recipe, field);
            require(
                    "DASHBOARD_SOURCE_NULL_BINDING_V1".equals(text(proof, "schema"))
                            && policy.required("model").equals(proof.required("model"))
                            && policy.required("allowedNativePresence")
                                    .equals(proof.required("allowedNativePresence")),
                    "DASHBOARD_FIELD_NULL_EVIDENCE_SCOPE");
            return ExportValidationSupport.JSON.nullNode();
        }
        if ("TEXT".equals(kind)) {
            require("TEXT".equals(atom.type()), "DASHBOARD_CAPTURE_TEXT_TYPE");
            return ExportValidationSupport.JSON.getNodeFactory().textNode(atom.value());
        }
        if ("BOOLEAN".equals(kind)) {
            require(
                    "BOOLEAN".equals(atom.type()),
                    "DASHBOARD_CAPTURE_BOOLEAN_PRESENTATION_BINDING_PENDING");
            return ExportValidationSupport.JSON
                    .getNodeFactory()
                    .booleanNode("true".equals(atom.value()));
        }
        if ("TEMPORAL".equals(kind)) {
            final var rule = rules.required("serviceAt");
            final var proofPin = ExportValidationSupport.Pin.from(rule.required("evidencePin"));
            final var proof = proofPin.json(262144, budget);
            track(inputs, proofPin);
            requireScopedNativeProof(proof, recipe, field);
            require(
                    "EXCEL_1900_CIVIL_DAY_AS_DECLARED_LOCAL_MIDNIGHT_V1".equals(text(rule, "model"))
                            && "DASHBOARD_SOURCE_TEMPORAL_BINDING_V1".equals(text(proof, "schema"))
                            && rule.required("model").equals(proof.required("model"))
                            && "CIVIL_DATE".equals(text(proof, "sourceSemanticType"))
                            && "DAY".equals(text(proof, "capturedPrecision"))
                            && "DECLARED_LOCAL_MIDNIGHT"
                                    .equals(text(proof, "derivedTargetRepresentation"))
                            && "America/Sao_Paulo".equals(text(proof, "targetZone"))
                            && proof.required("sourceClockObserved").isBoolean()
                            && !proof.get("sourceClockObserved").booleanValue()
                            && atom.carrier().equals(text(proof, "nativeCarrier"))
                            && "service_at".equals(text(proof, "sourceField"))
                            && proof.required("mappingPin").equals(recipe.required("mappingPin"))
                            && "1900".equals(epoch)
                            && "NUMBER".equals(atom.type()),
                    "DASHBOARD_SERVICE_AT_SEMANTIC_BINDING_PENDING");
            // Midnight is a declared target representation of a captured DAY, never a captured
            // clock.
            final var date = LocalDate.parse(QualifiedColetasModel.nativeCivil(atom.value()));
            final var local = date.atStartOfDay();
            final var zone = ZoneId.of("America/Sao_Paulo");
            final var offsets = zone.getRules().getValidOffsets(local);
            require(offsets.size() == 1, "DASHBOARD_CIVIL_ANCHOR_OFFSET_AMBIGUOUS");
            return ExportValidationSupport.JSON
                    .getNodeFactory()
                    .textNode(local.atOffset(offsets.get(0)).toString());
        }
        require("NUMBER".equals(atom.type()), "DASHBOARD_CAPTURE_NUMBER_TYPE");
        final var number = new BigDecimal(atom.value());
        return "INTEGER".equals(kind)
                ? ExportValidationSupport.JSON
                        .getNodeFactory()
                        .numberNode(number.toBigIntegerExact())
                : ExportValidationSupport.JSON.getNodeFactory().numberNode(number);
    }

    private static ObjectNode binding(
            final JsonNode pair,
            final ExportValidationSupport.Pin recipe,
            final ExportValidationSupport.Pin capture,
            final ExportValidationSupport.Pin facts,
            final JsonNode members) {
        final var value =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CAPTURED_SOURCE_BINDING_V1")
                        .put("authorityCreated", false)
                        .put("semanticApprovalIssued", false)
                        .put("nativeClockObserved", false)
                        .put("extrasAndAllOccurrencesRetainedInOriginalCapture", true);
        value.set("requestPin", pair.required("requestPin"));
        value.set("sourceConfigurationSha256", pair.required("sourceConfigurationSha256"));
        value.set("recipePin", ExportValidationSupport.JSON.valueToTree(recipe));
        value.set("capturePin", ExportValidationSupport.JSON.valueToTree(capture));
        value.set("governanceInputPin", ExportValidationSupport.JSON.valueToTree(facts));
        if (pair.has("dashboardInputRecipePin")) {
            value.put("temporalRepresentationGovernedOnlyByPinnedRecipe", true);
        }
        value.set("members", members.deepCopy());
        return value;
    }

    private static void requireScopedNativeProof(
            final JsonNode proof, final JsonNode recipe, final String field) {
        require(
                field.equals(text(proof, "sourceField"))
                        && recipe.required("profile").equals(proof.required("profile"))
                        && recipe.required("requestPin").equals(proof.required("requestPin"))
                        && recipe.required("mappingPin").equals(proof.required("mappingPin"))
                        && recipe.required("sourceConfigurationSha256")
                                .equals(proof.required("sourceConfigurationSha256")),
                "DASHBOARD_NATIVE_PROOF_SCOPE");
    }

    /**
     * Source-code contract only; never a claim about an installed SQL schema or successful
     * roundtrip.
     */
    public static ObjectNode consumerContract(final String profile) {
        final boolean coletas = ColetasIndependentRequest.PROFILE.equals(profile);
        require(
                coletas || IndependentExportRequest.PROFILE.equals(profile),
                "DASHBOARD_CONSUMER_PROFILE");
        final var contract =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_CURRENT_CODE_CONTRACT_V1")
                        .put("profile", profile)
                        .put(
                                "target",
                                coletas ? "dbo.vw_coletas_powerbi" : "dbo.fato_fretes_faturamento")
                        .put("installedDatabaseVerified", false)
                        .put("authorityCreated", false);
        final var fields = contract.putObject("columns");
        if (coletas) {
            columns(
                    fields,
                    "TEXT",
                    "ID",
                    "Status",
                    "Cliente",
                    "Cidade",
                    "UF",
                    "Região da Coleta",
                    "Região Logística",
                    "Filial",
                    "Usuario",
                    "Motivo Cancel.");
            columns(fields, "BIGINT", "Coleta", "Numero Manifesto");
            columns(fields, "DATE", "Solicitacao", "Agendamento", "Finalizacao");
            columns(fields, "BOOLEAN", "Excluída na Origem");
            columns(fields, "INTEGER", "Volumes");
            columns(fields, "DECIMAL", "Peso Real", "Peso Taxado", "Valor NF");
            columns(fields, "LOCAL_DATE_TIME", "Data de extracao");
        } else {
            columns(fields, "BIGINT", "frete_id", "numero_minuta", "numero_cte", "cte_id");
            columns(
                    fields,
                    "OFFSET_DATE_TIME",
                    "data_frete",
                    "data_emissao_cte",
                    "data_referencia_faturamento");
            columns(fields, "DECIMAL", "receita_bruta", "valor_frete", "peso_taxado");
            columns(fields, "INTEGER", "volumes");
            columns(
                    fields,
                    "TEXT",
                    "pagador_nome",
                    "remetente_nome",
                    "destinatario_nome",
                    "origem_uf",
                    "destino_uf",
                    "destino_cidade",
                    "filial_nome",
                    "classificacao_nome",
                    "responsavel_regiao_destino",
                    "responsavel_regiao_destino_key",
                    "status_frete",
                    "tipo_frete",
                    "modal",
                    "nfse_number");
            columns(
                    fields,
                    "BOOLEAN",
                    "is_cortesia",
                    "is_elegivel_faturamento",
                    "excluido_na_origem");
            columns(fields, "DATE", "data_referencia_faturamento_date");
            columns(fields, "LOCAL_DATE_TIME", "snapshot_em");
        }
        return contract;
    }

    private static void columns(final ObjectNode fields, final String type, final String... names) {
        for (final String name : names) {
            fields.put(name, type);
        }
    }

    private static void requireConsumerColumns(
            final IndependentDashboardBridge.PreparedRow row, final JsonNode contract) {
        final var expected = new java.util.HashSet<String>();
        contract.required("columns").fieldNames().forEachRemaining(expected::add);
        require(
                row.target().equals(text(contract, "target"))
                        && row.columns().keySet().equals(expected),
                "DASHBOARD_ALL_CURRENT_CONSUMER_COLUMNS");
        row.columns()
                .forEach(
                        (field, value) -> {
                            if (value == null) {
                                return;
                            }
                            final boolean valid =
                                    switch (contract.path("columns").path(field).asText()) {
                                        case "TEXT" -> value instanceof String;
                                        case "BIGINT" -> value instanceof Long;
                                        case "INTEGER" -> value instanceof Integer;
                                        case "BOOLEAN" -> value instanceof Boolean;
                                        case "DECIMAL" -> value instanceof BigDecimal;
                                        case "DATE" -> value instanceof LocalDate;
                                        case "LOCAL_DATE_TIME" ->
                                                value instanceof java.time.LocalDateTime;
                                        case "OFFSET_DATE_TIME" ->
                                                value instanceof java.time.OffsetDateTime;
                                        default -> false;
                                    };
                            require(valid, "DASHBOARD_CURRENT_CONSUMER_NATIVE_TYPE");
                        });
    }

    private static void attachBinding(
            final List<ObjectNode> rows, final int start, final ExportValidationSupport.Pin pin) {
        for (int index = start; index < rows.size(); index++) {
            ((ObjectNode) rows.get(index).required("governance"))
                    .set("sourceBindingEvidencePin", ExportValidationSupport.JSON.valueToTree(pin));
        }
    }

    private static void track(
            final Map<Path, ExportValidationSupport.Pin> inputs,
            final ExportValidationSupport.Pin pin) {
        final var previous = inputs.putIfAbsent(pin.path(), pin);
        require(previous == null || previous.equals(pin), "DASHBOARD_INPUT_PATH_PIN_CONFLICT");
    }

    private static void sealInputs(
            final Map<Path, ExportValidationSupport.Pin> inputs,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        for (final var pin : inputs.values()) {
            pin.read(8388608, budget);
        }
    }

    private static void issue(final Map<String, Long> issues, final String code) {
        require(issues.containsKey(code) || issues.size() < 32, "DASHBOARD_FINITE_PENDING_REASONS");
        issues.merge(code, 1L, Long::sum);
    }

    private static String safeReason(final Exception failure) {
        final String code = failure.getMessage();
        return code != null && code.matches("[A-Z][A-Z0-9_]{0,119}")
                ? code
                : "DASHBOARD_SOURCE_PROJECTION_PENDING";
    }

    static ExportValidationSupport.Pin writeJson(
            final Path destination,
            final JsonNode value,
            final int maximum,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return writeBytes(
                destination,
                ExportValidationSupport.JSON.writeValueAsBytes(value),
                maximum,
                budget);
    }

    private static ExportValidationSupport.Pin writeRows(
            final Path destination,
            final List<ObjectNode> rows,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var buffer = new java.io.ByteArrayOutputStream();
        for (final var row : rows) {
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(row);
            require(
                    bytes.length <= 262143 && (long) buffer.size() + bytes.length + 1 <= 8388608,
                    "DASHBOARD_PREPARED_INPUT_BOUND");
            buffer.write(bytes);
            buffer.write('\n');
            budget.check();
        }
        return writeBytes(destination, buffer.toByteArray(), 8388608, budget);
    }

    private static ExportValidationSupport.Pin writeBytes(
            final Path destination,
            final byte[] bytes,
            final int maximum,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                bytes.length > 0 && bytes.length <= maximum && !Files.exists(destination),
                "DASHBOARD_FRESH_BOUNDED_ARTIFACT");
        budget.check();
        final var partial = destination.resolveSibling(destination.getFileName() + ".partial");
        try (var file =
                FileChannel.open(
                        partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
        budget.check();
        Files.move(partial, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        final var pin =
                new ExportValidationSupport.Pin(
                        destination, ExportValidationSupport.sha256(bytes), bytes.length);
        pin.read(maximum, budget);
        return pin;
    }
}

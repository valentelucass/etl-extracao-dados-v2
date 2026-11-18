package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored synthetic captures only. These tests never authorize Root/Source or execute SQL. */
class CapturedDashboardInputProducerTest {
    @TempDir Path directory;
    private int sequence;

    private JsonNode pin(final byte[] bytes) throws Exception {
        final var path = directory.resolve("synthetic-" + ++sequence);
        Files.write(path, bytes);
        final var value =
                new ExportValidationSupport.Pin(
                        path, ExportValidationSupport.sha256(bytes), bytes.length);
        return ExportValidationSupport.JSON.readTree(
                ExportValidationSupport.JSON.writeValueAsBytes(value));
    }

    private JsonNode pin(final JsonNode value) throws Exception {
        return pin(ExportValidationSupport.JSON.writeValueAsBytes(value));
    }

    private record Operational(ObjectNode receipt, ExportValidationSupport.Pin selection) {}

    private Operational receipt(final Fixture f, final ObjectNode producer) throws Exception {
        final var resultPath = directory.resolve("operational-result.json");
        final var selection =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("profile", f.request.path("profile").asText())
                        .put("sourceConfigurationSha256", "a".repeat(64))
                        .put("resultPath", resultPath.toString())
                        .put("maximumDurationSeconds", 240);
        selection.set("requestPin", f.requestPin);
        selection.set("dashboardInputRecipePin", f.pair.required("dashboardInputRecipePin"));
        final var selectionPin = ExportValidationSupport.Pin.from(pin(selection));
        final var diagnostic =
                f.pair.deepCopy()
                        .put("schema", "INDEPENDENT_EXPORT_RESULT_V1")
                        .put("status", "CONTENT_EQUAL_FOR_THIS_PAIR")
                        .put("attemptId", f.request.path("attemptId").asText())
                        .put("profile", f.request.path("profile").asText());
        if (producer != null) {
            diagnostic.set("dashboardBridge", producer);
        }
        final var journalOutcome =
                ExportValidationSupport.Pin.from(
                        pin(
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("syntheticJournalOutcome", true)));
        final var clock =
                java.time.Clock.fixed(
                        java.time.Instant.parse("2036-03-27T15:00:00Z"), java.time.ZoneOffset.UTC);
        return new Operational(
                OperationalExportReceipt.persist(
                        resultPath,
                        diagnostic,
                        selectionPin,
                        journalOutcome,
                        clock.instant(),
                        clock,
                        new ExportValidationSupport.Budget(Duration.ofSeconds(20))),
                selectionPin);
    }

    private final class Fixture {
        private final boolean fretes;
        private final ObjectNode request;
        private final JsonNode requestPin;
        private final JsonNode mappingPin;
        private final ObjectNode pair = ExportValidationSupport.JSON.createObjectNode();
        private final ObjectNode recipe = ExportValidationSupport.JSON.createObjectNode();
        private final ObjectNode snapshot = ExportValidationSupport.JSON.createObjectNode();
        private final ObjectNode governance = ExportValidationSupport.JSON.createObjectNode();
        private final Path run = directory.resolve("producer");
        private byte[] captured;
        private boolean omitFacts;
        private Consumer<ObjectNode> provenanceMutation = ignored -> {};

        Fixture(final boolean fretes, final List<ObjectNode> sources) throws Exception {
            this.fretes = fretes;
            request =
                    fretes
                            ? new ExportPairFixture(directory).request
                            : ColetasCommon31SyntheticFixture.request();
            requestPin = pin(request);
            mappingPin =
                    pin(fretes ? ExportColumnCatalog.fretes() : ColetasCommonAdapter.catalog());
            if (fretes) {
                captured = fretesWorkbook(false);
            } else {
                final var lines = new StringBuilder();
                sources.forEach(row -> lines.append(row).append('\n'));
                captured = lines.toString().getBytes(StandardCharsets.UTF_8);
            }
            pair.put("sourceConfigurationSha256", "a".repeat(64));
            pair.set("requestPin", requestPin);
            pair.set("mappingPin", mappingPin);
            pair.putObject("ui")
                    .set(
                            "dataPin",
                            pin("AUTHORED_UI_REFERENCE_ONLY".getBytes(StandardCharsets.UTF_8)));
            recipe.put("schema", "CAPTURED_DASHBOARD_INPUT_RECIPE_V1")
                    .put("runDirectory", run.toString())
                    .put("sourceConfigurationSha256", "a".repeat(64))
                    .put("grainModel", "UNIQUE_TYPED_SOURCE_ID");
            recipe.set("profile", request.required("profile"));
            recipe.set("requestPin", requestPin);
            recipe.set("mappingPin", mappingPin);
            recipe.set(
                    "consumerContractPin",
                    pin(
                            CapturedDashboardInputProducer.consumerContract(
                                    request.path("profile").asText())));
            snapshot.put("schema", "DASHBOARD_GOVERNED_FACT_SNAPSHOT_V1")
                    .put("sourceConfigurationSha256", "a".repeat(64))
                    .put("idNamespace", fretes ? "DATAEXPORT6389.id" : "DATAEXPORT6908.id");
            snapshot.set("requestPin", requestPin);
            snapshot.set("profile", request.required("profile"));
            snapshot.set("periodStart", request.required("periodStart"));
            snapshot.set("periodEnd", request.required("periodEnd"));
            final var observation =
                    pin(ExportValidationSupport.JSON.createObjectNode().put("synthetic", true));
            governance
                    .put("schema", "INDEPENDENT_DASHBOARD_GOVERNANCE_V1")
                    .put("sourceKey", "101")
                    .put("excludedAtSource", false)
                    .put("publicationSnapshotUtc", "2036-03-27T15:00:00Z");
            governance.set("exclusionEvidencePin", observation);
            governance.set("snapshotEvidencePin", observation);
            if (!fretes) {
                final var source = ColetasCommon31SyntheticFixture.sourceRow();
                final var region =
                        governance
                                .putObject("logisticsRegion")
                                .put("sourceKey", "101")
                                .put("name", "SYNTHETIC GOVERNED REGION")
                                .put("validFrom", "2036-01-01")
                                .put("validThrough", "2036-12-31");
                region.set("postalCode", source.required("pck_pds_postal_code"));
                region.set("city", source.required("pck_pds_cty_name"));
                region.set("state", source.required("pck_pds_cty_sae_code"));
                region.set("evidencePin", observation);
            } else {
                governance.put("courtesy", false);
                for (final String field :
                        List.of(
                                "responsavel_regiao_destino",
                                "responsavel_regiao_destino_key",
                                "status_frete",
                                "tipo_frete",
                                "modal")) {
                    governance.put(field, "SYNTHETIC GOVERNED");
                }
                final var billing =
                        governance
                                .putObject("billing")
                                .put("referenceAt", "2025-04-01T00:00:00-03:00")
                                .put("grossRevenue", "123.456789")
                                .put("freightValue", "100.000001")
                                .put("eligible", true)
                                .put("governedRevision", 1)
                                .putNull("cteId")
                                .putNull("cteNumber")
                                .putNull("nfseNumber")
                                .putNull("cteIssuedAt");
                billing.set("evidencePin", observation);
                final var proof =
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("schema", "DASHBOARD_SOURCE_TEMPORAL_BINDING_V1")
                                .put("model", "EXCEL_1900_CIVIL_DAY_AS_DECLARED_LOCAL_MIDNIGHT_V1")
                                .put("sourceSemanticType", "CIVIL_DATE")
                                .put("sourceField", "service_at")
                                .put("capturedPrecision", "DAY")
                                .put("derivedTargetRepresentation", "DECLARED_LOCAL_MIDNIGHT")
                                .put("targetZone", "America/Sao_Paulo")
                                .put("sourceClockObserved", false)
                                .put("nativeCarrier", "XLSX:1900:builtin:0")
                                .put("sourceConfigurationSha256", "a".repeat(64));
                proof.set("requestPin", requestPin);
                proof.set("mappingPin", mappingPin);
                proof.set("profile", request.required("profile"));
                final var rule = recipe.putObject("nativeSourceBindings").putObject("serviceAt");
                rule.set("model", proof.required("model"));
                rule.set("evidencePin", pin(proof));
            }
        }

        private ObjectNode seal() throws Exception {
            pair.putObject("api").set("dataPin", pin(captured));
            final var records = snapshot.putArray("records");
            if (!omitFacts) {
                final var fact = records.addObject();
                fact.set("governance", governance);
                final var id = fact.putObject("idAtom").put("type", "INTEGER").put("value", "101");
                final var provenance =
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("schema", "DASHBOARD_REFERENCE_FACT_PROVENANCE_V1")
                                .put("originKind", "GOVERNED_REFERENCE_NOT_CAPTURED_API")
                                .put("sourceConfigurationSha256", "a".repeat(64))
                                .put(
                                        "governanceSha256",
                                        ExportValidationSupport.sha256(
                                                ExportValidationSupport.canonicalBytes(
                                                        governance)));
                provenance.set("idAtom", id);
                provenance.set("requestPin", requestPin);
                provenance.set("profile", request.required("profile"));
                provenance.set("periodStart", request.required("periodStart"));
                provenance.set("periodEnd", request.required("periodEnd"));
                provenanceMutation.accept(provenance);
                fact.set("provenancePin", pin(provenance));
            }
            if (!recipe.has("referenceContextPin")) {
                recipe.set("factsSnapshotPin", pin(snapshot));
            }
            final var grain =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "DASHBOARD_GRAIN_BINDING_V1");
            grain.set("model", recipe.required("grainModel"));
            grain.set("requestPin", requestPin);
            grain.set("consumerContractPin", recipe.required("consumerContractPin"));
            recipe.set("grainEvidencePin", pin(grain));
            pair.set("dashboardInputRecipePin", pin(recipe));
            pair.set("admissionPin", pin(pair));
            return pair;
        }

        private ObjectNode produce() throws Exception {
            return CapturedDashboardInputProducer.produceAndCheck(
                    seal(), new ExportValidationSupport.Budget(Duration.ofSeconds(20)));
        }
    }

    @Test
    void actualCapturedRowsAreJoinedProducedAndConsumedByTheTypedBridge() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        final var result = f.produce();
        assertEquals(
                "TYPED_INPUT_PRODUCED_AND_CONSUMED_PUBLICATION_PENDING",
                result.path("status").asText());
        assertTrue(result.path("everyOccurrenceProjectedAndJoined").asBoolean());
        final var rows = ExportValidationSupport.Pin.from(result.required("generatedRowsPin"));
        final var generated =
                ExportValidationSupport.JSON.readTree(Files.readAllLines(rows.path()).get(0));
        assertEquals(ColetasCommon31SyntheticFixture.sourceRow(), generated.required("source"));
        final var stage =
                ExportValidationSupport.Pin.from(result.path("consumerStage").required("stagePin"));
        final var typed =
                ExportValidationSupport.JSON.readTree(Files.readAllLines(stage.path()).get(0));
        assertEquals(21, typed.required("columns").size());
        assertEquals("101", typed.path("columns").path("ID").asText());
        assertFalse(result.path("SQLPublicationPerformed").asBoolean());
        assertFalse(result.path("consumerRoundtripVerified").asBoolean());
        assertFalse(result.path("authorityCreated").asBoolean());
    }

    @Test
    void missingReferenceFactsRemainPendingWithoutAnyReadyInput() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        f.omitFacts = true;
        final var result = f.produce();
        assertEquals(
                1,
                result.path("pendingReasonCounts").path("DASHBOARD_GOVERNED_FACT_MISSING").asInt());
        assertFalse(result.path("inputWasProducedFromActualCapture").asBoolean());
        assertFalse(result.path("inputWasConsumedByTypedBridge").asBoolean());
        assertFalse(Files.exists(f.run.resolve("capture-governed-input.jsonl")));
        assertFalse(Files.exists(f.run.resolve("consumer-typed-stage.jsonl")));
    }

    @Test
    void aFactSnapshotForAnotherPeriodCannotSupplyTheJoin() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        f.snapshot.put("periodEnd", "2036-03-27");
        assertThrows(IllegalArgumentException.class, f::produce);
        assertFalse(Files.exists(f.run));
    }

    @Test
    void aRecipeForAnotherRequestIsRefusedBeforeWritingAStage() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        f.recipe.set(
                "requestPin",
                pin(ColetasCommon31SyntheticFixture.request().put("periodStart", "2036-03-19")));
        assertThrows(IllegalArgumentException.class, f::produce);
        assertFalse(Files.exists(f.run));
    }

    @Test
    void uniqueIdGrainRefusesExpandedOccurrencesWithoutLosingTheCapture() throws Exception {
        final var source = ColetasCommon31SyntheticFixture.sourceRow();
        final var f = new Fixture(false, List.of(source, source));
        final var original = f.captured.clone();
        final var result = f.produce();
        assertEquals(2, result.path("sourceRows").asInt());
        assertEquals(1, result.path("sourceDistinctIds").asInt());
        assertEquals(
                1,
                result.path("pendingReasonCounts")
                        .path("DASHBOARD_AMBIGUOUS_OR_UNADMITTED_GRAIN")
                        .asInt());
        assertArrayEquals(
                original,
                Files.readAllBytes(
                        ExportValidationSupport.Pin.from(result.required("capturePin")).path()));
        assertFalse(Files.exists(f.run.resolve("capture-governed-input.jsonl")));
    }

    @Test
    void admittedUniformProjectionRetainsBothOccurrencesAndDifferentExtras() throws Exception {
        final var a = ColetasCommon31SyntheticFixture.sourceRow().put("supplier_extra", "FIRST");
        final var b = ColetasCommon31SyntheticFixture.sourceRow().put("supplier_extra", "SECOND");
        final var f = new Fixture(false, List.of(a, b));
        f.recipe.put("grainModel", "UNIFORM_CONSUMER_PROJECTION_PER_TYPED_ID");
        final var result = f.produce();
        assertEquals(1, result.path("preparedEntityRows").asInt());
        final var binding =
                ExportValidationSupport.Pin.from(result.required("sourceBindingPins").get(0))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(10)));
        assertEquals(2, binding.path("members").get(0).path("occurrences").size());
        assertArrayEquals(
                f.captured,
                Files.readAllBytes(
                        ExportValidationSupport.Pin.from(binding.required("capturePin")).path()));
    }

    @Test
    void conflictingConsumerValuesCannotBeCollapsedUnderTheUniformModel() throws Exception {
        final var a = ColetasCommon31SyntheticFixture.sourceRow();
        final var b = a.deepCopy().put("invoices_volumes", 3);
        final var f = new Fixture(false, List.of(a, b));
        f.recipe.put("grainModel", "UNIFORM_CONSUMER_PROJECTION_PER_TYPED_ID");
        final var result = f.produce();
        assertEquals(
                1,
                result.path("pendingReasonCounts")
                        .path("DASHBOARD_AMBIGUOUS_OR_UNADMITTED_GRAIN")
                        .asInt());
        assertFalse(Files.exists(f.run.resolve("consumer-typed-stage.jsonl")));
    }

    @Test
    void changedCaptureBytesInvalidateThePinBeforeAnyJoin() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        final var pair = f.seal();
        Files.writeString(
                ExportValidationSupport.Pin.from(pair.path("api").required("dataPin")).path(),
                "CHANGED\n");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CapturedDashboardInputProducer.produceAndCheck(
                                pair, new ExportValidationSupport.Budget(Duration.ofSeconds(10))));
        assertFalse(Files.exists(f.run));
    }

    @Test
    void provenanceCannotAttestAnotherGovernancePayload() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        f.provenanceMutation = proof -> proof.put("governanceSha256", "b".repeat(64));
        assertThrows(IllegalArgumentException.class, f::produce);
        assertFalse(Files.exists(f.run.resolve("consumer-typed-stage.jsonl")));
    }

    @Test
    void textAndNumericIdsCannotJoinByStringCoercion() throws Exception {
        final var f =
                new Fixture(
                        false,
                        List.of(ColetasCommon31SyntheticFixture.sourceRow().put("id", "101")));
        final var result = f.produce();
        assertEquals(
                1, result.path("pendingReasonCounts").path("DASHBOARD_CAPTURE_INTEGER_ID").asInt());
        assertFalse(Files.exists(f.run.resolve("capture-governed-input.jsonl")));
    }

    @Test
    void aSourceRowOutsideTheCapturedRequestPeriodNeverBecomesConsumerInput() throws Exception {
        final var f =
                new Fixture(
                        false,
                        List.of(
                                ColetasCommon31SyntheticFixture.sourceRow()
                                        .put("request_date", "2036-03-27")));
        final var result = f.produce();
        assertEquals(1, result.path("pendingReasonCounts").path("DASHBOARD_SOURCE_PERIOD").asInt());
        assertFalse(Files.exists(f.run.resolve("consumer-typed-stage.jsonl")));
    }

    @Test
    void aConsumerContractMissingARealColumnIsRefused() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        final var contract =
                CapturedDashboardInputProducer.consumerContract(ColetasIndependentRequest.PROFILE);
        ((ObjectNode) contract.required("columns")).remove("Região Logística");
        f.recipe.set("consumerContractPin", pin(contract));
        assertThrows(IllegalArgumentException.class, f::produce);
        assertFalse(Files.exists(f.run));
    }

    @Test
    void freightInputUsesCapturedServiceDayAndIndependentBillingPeriod() throws Exception {
        final var f = new Fixture(true, List.of());
        final var result = f.produce();
        assertEquals(
                "TYPED_INPUT_PRODUCED_AND_CONSUMED_PUBLICATION_PENDING",
                result.path("status").asText());
        final var stage =
                ExportValidationSupport.Pin.from(result.path("consumerStage").required("stagePin"));
        final var row =
                ExportValidationSupport.JSON
                        .readTree(Files.readAllLines(stage.path()).get(0))
                        .required("columns");
        assertEquals(
                java.time.OffsetDateTime.parse("2025-03-03T00:00:00-03:00"),
                java.time.OffsetDateTime.parse(row.path("data_frete").asText()));
        assertEquals("2025-04-01", row.path("data_referencia_faturamento_date").asText());
        assertTrue(row.has("responsavel_regiao_destino_key"));
        assertFalse(row.has("valor_icms"));
        assertEquals(9001, row.path("numero_minuta").asInt());
        assertFalse(result.path("semanticApprovalIssued").asBoolean());
    }

    @Test
    void missingFreightConsumerKeyCannotBeHiddenByAReadyStatus() throws Exception {
        final var f = new Fixture(true, List.of());
        f.governance.remove("responsavel_regiao_destino_key");
        assertThrows(IllegalArgumentException.class, f::produce);
        assertFalse(Files.exists(f.run.resolve("capture-governed-input.jsonl")));
    }

    @Test
    void anUnqualifiedFreightTimeModelCannotInventASourceClock() throws Exception {
        final var f = new Fixture(true, List.of());
        ((ObjectNode) f.recipe.required("nativeSourceBindings").required("serviceAt"))
                .put("model", "UNKNOWN");
        final var result = f.produce();
        assertEquals(
                1,
                result.path("pendingReasonCounts")
                        .path("DASHBOARD_SERVICE_AT_SEMANTIC_BINDING_PENDING")
                        .asInt());
        assertFalse(Files.exists(f.run.resolve("consumer-typed-stage.jsonl")));
    }

    @Test
    void nativeFreightFractionalDaysRemainUnqualified() throws Exception {
        final var f = new Fixture(true, List.of());
        f.captured = fretesWorkbook(true);
        final var result = f.produce();
        assertEquals("PENDING_CAPTURE_TO_GOVERNED_INPUT", result.path("status").asText());
        assertFalse(Files.exists(f.run.resolve("capture-governed-input.jsonl")));
    }

    @Test
    void actualLocalConsumerReadsEveryTypedValueAndPersistsABoundReadbackReceipt()
            throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        final var producer = f.produce();
        final var operational = receipt(f, producer);
        final var result =
                CapturedDashboardDeliveryOutcome.describe(
                        operational.receipt(),
                        operational.selection(),
                        ColetasIndependentRequest.PROFILE,
                        new ExportValidationSupport.Budget(Duration.ofSeconds(20)));
        assertTrue(result.path("actualLocalStageReadbackVerified").asBoolean());
        assertEquals(1, result.path("typedRowsReadBack").asInt());
        assertEquals(operational.receipt().required("executionId"), result.required("executionId"));
        assertFalse(result.path("governedDeliveryComplete").asBoolean());
        assertFalse(result.path("dashboardConsumptionVerified").asBoolean());
        final var readback =
                ExportValidationSupport.Pin.from(result.required("localReadbackReceiptPin"));
        assertEquals(
                "LOCAL_TYPED_STAGE_READ_BACK_PUBLICATION_PENDING",
                readback.json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(10)))
                        .path("status")
                        .asText());
    }

    @Test
    void changedTypedStageCannotProduceAConsumerReadbackReceipt() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        final var producer = f.produce();
        final var operational = receipt(f, producer);
        final var stage =
                ExportValidationSupport.Pin.from(
                        producer.path("consumerStage").required("stagePin"));
        Files.writeString(stage.path(), "CHANGED\n");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CapturedDashboardDeliveryOutcome.describe(
                                operational.receipt(),
                                operational.selection(),
                                ColetasIndependentRequest.PROFILE,
                                new ExportValidationSupport.Budget(Duration.ofSeconds(20))));
        assertFalse(
                Files.exists(
                        directory.resolve("operational-result.json.local-stage-readback.json")));
    }

    @Test
    void comparisonOnlyReceiptDoesNotAcknowledgeALocalDelivery() throws Exception {
        final var f = new Fixture(false, List.of(ColetasCommon31SyntheticFixture.sourceRow()));
        f.seal();
        final var operational = receipt(f, null);
        final var result =
                CapturedDashboardDeliveryOutcome.describe(
                        operational.receipt(),
                        operational.selection(),
                        ColetasIndependentRequest.PROFILE,
                        new ExportValidationSupport.Budget(Duration.ofSeconds(20)));
        assertEquals("PENDING_CAPTURE_GOVERNED_INPUT_PRODUCTION", result.path("status").asText());
        assertFalse(result.path("actualLocalStageReadbackVerified").asBoolean());
        assertFalse(result.path("governedDeliveryComplete").asBoolean());
    }

    private JsonNode dataset(final Fixture f, final String kind, final ObjectNode... records)
            throws Exception {
        final var value =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_RAW_REFERENCE_DATASET_V1")
                        .put("kind", kind)
                        .put("completeForDeclaredJoinScope", true);
        value.set("requestPin", f.requestPin);
        final var rows = value.putArray("records");
        for (final var record : records) {
            rows.add(record);
        }
        return pin(value);
    }

    @Test
    void coletasGovernanceIsDerivedFromRawDimensionLifecycleAndManifestReferences()
            throws Exception {
        final var source =
                ColetasCommon31SyntheticFixture.sourceRow()
                        .put("pck_pds_postal_code", "12345678")
                        .put("pck_pds_cty_sae_code", "SP");
        final var f = new Fixture(false, List.of(source));
        final var context =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_RAW_REFERENCE_CONTEXT_V1")
                        .put("sourceConfigurationSha256", "a".repeat(64))
                        .put("idNamespace", "DATAEXPORT6908.id");
        context.set("requestPin", f.requestPin);
        context.set("profile", f.request.required("profile"));
        context.set("periodStart", f.request.required("periodStart"));
        context.set("periodEnd", f.request.required("periodEnd"));
        final var identity =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DATAEXPORT_LEGACY_IDENTITY_BINDING_V1")
                        .put("sourceNamespace", "DATAEXPORT6908.id")
                        .put("sourceConfigurationSha256", "a".repeat(64));
        identity.set("mappingPin", f.mappingPin);
        context.set("legacyIdentityBindingPin", pin(identity));
        final var datasets = context.putObject("datasets");
        datasets.set(
                "lifecycle",
                dataset(
                        f,
                        "lifecycle",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("sourceKey", "101")
                                .put("excludedAtSource", false)
                                .put("confirmedAbsences", 0)));
        datasets.set(
                "publication",
                dataset(
                        f,
                        "publication",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("requestSha256", f.requestPin.path("sha256").asText())
                                .put("snapshotUtc", "2036-03-27T15:00:00Z")
                                .put("eventKind", "ACTUAL_STAGE_PREPARATION_CLOCK")));
        datasets.set(
                "logisticsRegions",
                dataset(
                        f,
                        "logisticsRegions",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("id", 1)
                                .put("cep_inicio", "12340000")
                                .put("cep_fim", "12349999")
                                .put("regiao_logistica", "SYNTHETIC RAW RULE REGION")));
        datasets.set(
                "manifestos",
                dataset(
                        f,
                        "manifestos",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("id", 1)
                                .put("pick_sequence_code", 7)
                                .put("sequence_code", 9002)
                                .put("excludedAtSource", false)));
        f.recipe.set("referenceContextPin", pin(context));
        final var result = f.produce();
        assertTrue(result.path("governanceDerivedFromRawReferenceDatasets").asBoolean());
        assertEquals(0, result.path("externalFactRecords").asInt());
        final var stage =
                ExportValidationSupport.Pin.from(result.path("consumerStage").required("stagePin"));
        final var columns =
                ExportValidationSupport.JSON
                        .readTree(Files.readAllLines(stage.path()).get(0))
                        .required("columns");
        assertEquals("SYNTHETIC RAW RULE REGION", columns.path("Região Logística").asText());
        assertEquals(9002, columns.path("Numero Manifesto").asInt());
        assertFalse(result.path("semanticApprovalIssued").asBoolean());
    }

    @Test
    void freightStageDerivesAmountsCalendarMinutaAndBranchFromCapturedAndRawReferences()
            throws Exception {
        final var f = new Fixture(true, List.of());
        f.captured = fretesWorkbook(false, true);
        final var context =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DASHBOARD_RAW_REFERENCE_CONTEXT_V1")
                        .put("sourceConfigurationSha256", "a".repeat(64))
                        .put("idNamespace", "DATAEXPORT6389.id");
        context.set("requestPin", f.requestPin);
        context.set("profile", f.request.required("profile"));
        context.set("periodStart", f.request.required("periodStart"));
        context.set("periodEnd", f.request.required("periodEnd"));
        final var identity =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "DATAEXPORT_LEGACY_IDENTITY_BINDING_V1")
                        .put("sourceNamespace", "DATAEXPORT6389.id")
                        .put("sourceConfigurationSha256", "a".repeat(64));
        identity.set("mappingPin", f.mappingPin);
        context.set("legacyIdentityBindingPin", pin(identity));
        final var datasets = context.putObject("datasets");
        datasets.set(
                "lifecycle",
                dataset(
                        f,
                        "lifecycle",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("sourceKey", "101")
                                .put("excludedAtSource", false)));
        datasets.set(
                "publication",
                dataset(
                        f,
                        "publication",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("requestSha256", f.requestPin.path("sha256").asText())
                                .put("snapshotUtc", "2036-03-27T15:00:00Z")
                                .put("eventKind", "ACTUAL_STAGE_PREPARATION_CLOCK")));
        final var raw =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("sourceKey", "101")
                        .put("modal", "SYNTHETIC MODAL");
        raw.putObject("documents")
                .put("cteId", 301)
                .put("cteIssuedAt", "2025-03-03T00:00:00-03:00");
        raw.putObject("rawFrete").put("subtotal", new java.math.BigDecimal("100.25"));
        datasets.set("rawFretes", dataset(f, "rawFretes", raw));
        datasets.set(
                "calendar",
                dataset(
                        f,
                        "calendar",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("sourceDay", "2025-03-03")
                                .put("referenceDay", "2025-02-28")
                                .put("previousBusinessDaySnapshotVerified", true)));
        datasets.set(
                "localizacao",
                dataset(
                        f,
                        "localizacao",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("sequenceNumber", 9001)
                                .put("destinationBranchNickname", "SYNTHETIC BRANCH")
                                .put("activeJoinSnapshotComplete", true)
                                .put("excludedAtSource", false)
                                .put("invoicesVolumes", 3)));
        datasets.set(
                "rankedFiscal",
                dataset(
                        f,
                        "rankedFiscal",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("cteKey", "SYNTHETICSOURCE")
                                .put("statusCte", "authorized")
                                .putNull("statusCteResult")
                                .put("rankedSnapshotComplete", true)));
        datasets.set(
                "branchAssignment",
                dataset(
                        f,
                        "branchAssignment",
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("pagador_documento_key", "SYNTHETICSOURCE")
                                .put("ativo", true)
                                .put("filial_destino_nome", "ASSIGNED BRANCH")));
        f.recipe.set("referenceContextPin", pin(context));
        final var result = f.produce();
        assertTrue(result.path("governanceDerivedFromRawReferenceDatasets").asBoolean());
        assertEquals(0, result.path("externalFactRecords").asInt());
        final var stage =
                ExportValidationSupport.Pin.from(result.path("consumerStage").required("stagePin"));
        final var columns =
                ExportValidationSupport.JSON
                        .readTree(Files.readAllLines(stage.path()).get(0))
                        .required("columns");
        assertEquals(9001, columns.path("numero_minuta").asInt());
        assertEquals(3, columns.path("volumes").asInt());
        assertEquals("ASSIGNED BRANCH", columns.path("filial_nome").asText());
        assertEquals("2025-02-28", columns.path("data_referencia_faturamento_date").asText());
        assertEquals(
                new java.math.BigDecimal("100.25"), columns.path("valor_frete").decimalValue());
        assertEquals(1, columns.path("receita_bruta").asInt());
    }

    private static byte[] fretesWorkbook(final boolean fractional) throws Exception {
        return fretesWorkbook(fractional, false);
    }

    static byte[] fretesWorkbook(final boolean fractional, final boolean references)
            throws Exception {
        final String ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        final var catalog = ExportColumnCatalog.fretes().required("columns");
        final var sheet =
                new StringBuilder("<worksheet xmlns=\"")
                        .append(ns)
                        .append("\"><sheetData><row r=\"1\">");
        for (int index = 0; index < catalog.size(); index++) {
            sheet.append("<c r=\"")
                    .append(letters(index + 1))
                    .append("1\" t=\"inlineStr\"><is><t>")
                    .append(xml(catalog.get(index).path("name").asText()))
                    .append("</t></is></c>");
        }
        sheet.append("</row><row r=\"2\">");
        final long serial =
                ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.of(2025, 3, 3));
        for (int index = 0; index < catalog.size(); index++) {
            final String field = catalog.get(index).path("sourceJsonPointer").asText();
            final boolean numericDocument =
                    references
                            && List.of(
                                            "/fit_fhe_cte_number",
                                            "/fit_fhe_cte_series",
                                            "/fit_fhe_cte_issued_at")
                                    .contains(field);
            final boolean text =
                    field.startsWith("/fit_")
                                    && !numericDocument
                                    && !catalog.get(index).path("total").asBoolean()
                            || references
                                    && List.of("/status", "/type", "/nfse_number").contains(field);
            sheet.append("<c r=\"").append(letters(index + 1)).append("2\"");
            if (references && "/courtesy".equals(field)) {
                sheet.append(" t=\"b\"><v>0</v></c>");
            } else if (text) {
                sheet.append(" t=\"inlineStr\"><is><t>")
                        .append(
                                "/status".equals(field)
                                        ? "pending"
                                        : "/type".equals(field)
                                                ? "Freight::Normal"
                                                : "SYNTHETIC SOURCE")
                        .append("</t></is></c>");
            } else {
                sheet.append("><v>")
                        .append(
                                "/id".equals(field)
                                        ? "101"
                                        : "/corporation_sequence_number".equals(field)
                                                ? "9001"
                                                : references
                                                                && "/fit_fhe_cte_issued_at"
                                                                        .equals(field)
                                                        ? Long.toString(serial)
                                                        : "/service_at".equals(field)
                                                                ? serial + (fractional ? ".5" : "")
                                                                : "1")
                        .append("</v></c>");
            }
        }
        sheet.append("</row></sheetData></worksheet>");
        final var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            part(zip, "[Content_Types].xml", "<Types/>");
            part(
                    zip,
                    "xl/workbook.xml",
                    "<workbook xmlns=\""
                            + ns
                            + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                            + "<workbookPr date1904=\"0\"/><sheets>"
                            + "<sheet name=\"Synthetic\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(
                    zip,
                    "xl/_rels/workbook.xml.rels",
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            part(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return bytes.toByteArray();
    }

    private static void part(final ZipOutputStream zip, final String name, final String value)
            throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String letters(final int column) {
        int at = column;
        final var value = new StringBuilder();
        while (at > 0) {
            value.insert(0, (char) ('A' + (at - 1) % 26));
            at = (at - 1) / 26;
        }
        return value.toString();
    }
}

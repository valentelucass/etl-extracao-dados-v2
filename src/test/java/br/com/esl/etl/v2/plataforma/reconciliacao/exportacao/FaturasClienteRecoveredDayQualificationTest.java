package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored offline provenance fixtures only; these are not attestations of actual Source runs. */
class FaturasClienteRecoveredDayQualificationTest {
    @TempDir Path directory;

    @Test
    void recoveredDataUsesCurrentConsumerWithoutRewritingFailedProducerOrPretendingSourceSuccess()
            throws Exception {
        try (var f = new Fixture(directory, false)) {
            final byte[] before = Files.readAllBytes(f.capturePath);
            final byte[] raw = Files.readAllBytes(f.sourcePin.path());
            final var receipt = f.execute();
            assertTrue(receipt.path("success").booleanValue());
            assertEquals(1, receipt.path("recoveredRetainedCaptureCount").intValue());
            assertFalse(receipt.path("sourceCaptureQualified").booleanValue());
            final var diagnostic = read(f.lastDiagnostic);
            final var current = diagnostic.required("captures").get(0);
            assertTrue(current.path("mapperQualified").booleanValue());
            assertTrue(current.path("rowTypesQualified").booleanValue());
            assertTrue(current.path("historicalDataCompletenessVerified").booleanValue());
            assertTrue(current.path("producerFailed").booleanValue());
            assertFalse(current.path("sourceAcquisitionVerified").booleanValue());
            assertFalse(current.path("producerDeadlineQualified").booleanValue());
            assertFalse(current.path("producerMapperQualified").booleanValue());
            assertFalse(current.path("producerRowTypesQualified").booleanValue());
            assertTrue(current.required("producerSummaryPin").isNull());
            assertFalse(
                    current.required("producerTestPin")
                            .equals(current.required("consumerTestPin")));
            assertEquals(2, current.path("currentMapperInvocations").intValue());
            assertEquals(0, receipt.path("Source").intValue());
            assertEquals(0, receipt.path("HTTP").intValue());
            assertFalse(receipt.path("contentAccepted").booleanValue());
            assertFalse(receipt.toString().contains("private-synthetic-payer"));
            assertArrayEquals(before, Files.readAllBytes(f.capturePath));
            assertArrayEquals(raw, Files.readAllBytes(f.sourcePin.path()));
        }
    }

    @Test
    void legacyInputCannotOptIntoRecoveryByAddingAnEntryKindOrFabricatingASummary()
            throws Exception {
        try (var f = new Fixture(directory, false)) {
            f.input.put("schema", "ROOT_FATURAS_CLIENTE_RAW_REQUALIFICATION_INPUT_V1");
            assertThrows(IllegalArgumentException.class, f::execute);
            f.input.put("schema", FaturasClienteRecoveredDayQualification.INPUT_SCHEMA);
            f.entry.set(
                    "producerSummaryPin",
                    ExportValidationSupport.JSON.valueToTree(f.pin(f.capturePath)));
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    @Test
    void neitherCompletenessNorCallerCanPromoteHistoricalDeadlineOrSourceFlags() throws Exception {
        for (final String name : List.of("sourceCaptureQualified", "producerDeadlineQualified")) {
            try (var f = new Fixture(directory, false)) {
                f.proof.put(name, true);
                f.refresh();
                assertThrows(IllegalArgumentException.class, f::execute, name);
            }
        }
        try (var f = new Fixture(directory, false)) {
            f.capture.put("captureComplete", true);
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    @Test
    void failedProducerAndBothSourceZeroProofsRequireActualClosedRestoredMatchingRoot()
            throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            try (var f = new Fixture(directory, false)) {
                final var closure =
                        kind == 0 ? f.failedClosure : kind == 1 ? f.auditClosure : f.proofClosure;
                closure.put("slotRetained", true);
                f.refresh();
                assertThrows(IllegalArgumentException.class, f::execute);
            }
        }
        try (var f = new Fixture(directory, false)) {
            f.proofClosure.put("executingRootUUID", UUID.randomUUID().toString());
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    @Test
    void aTrueTerminalFlagCannotReplaceReopeningTheActualLastArrayAndLiteralRaw() throws Exception {
        try (var f = new Fixture(directory, false)) {
            ((ObjectNode) f.capture.required("responses").get(2))
                    .set(
                            "rawBodyPin",
                            ExportValidationSupport.JSON.valueToTree(
                                    f.fixture.pin(FaturasClienteCaptureFixture.array(f.row))));
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
        try (var f = new Fixture(directory, false)) {
            final byte[] changed = Files.readAllBytes(f.sourcePin.path());
            changed[changed.length - 1] = ' ';
            Files.write(f.sourcePin.path(), changed);
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    @Test
    void actualInfoAndContiguousResponsePagesRemainMandatoryUnderRecovery() throws Exception {
        try (var f = new Fixture(directory, false)) {
            final var info = FaturasClienteCaptureFixture.info();
            ((ObjectNode) info.required("fields")).remove("comments");
            ((ObjectNode) f.capture.required("responses").get(0))
                    .set(
                            "rawBodyPin",
                            ExportValidationSupport.JSON.valueToTree(f.fixture.pin(info)));
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
        try (var f = new Fixture(directory, false)) {
            ((ObjectNode) f.capture.required("responses").get(1)).put("page", 7);
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    @Test
    void currentWireAndActualMapperRejectRowsEvenWhenHistoricalDataCompletenessIsTrue()
            throws Exception {
        try (var f = new Fixture(directory, true)) {
            final var receipt = f.execute();
            assertFalse(receipt.path("success").booleanValue());
            assertFalse(receipt.path("rowTypesQualified").booleanValue());
            assertFalse(receipt.path("mapperQualified").booleanValue());
            assertEquals(2, receipt.path("rejectedRows").intValue());
            assertEquals(2, read(f.lastDiagnostic).path("currentMapperInvocations").intValue());
            assertTrue(f.proof.path("dataCompletenessQualified").booleanValue());
        }
    }

    @Test
    void duplicateRawWrongProofBindingAndAResponseOutsideTheSourceWindowCannotBeRecovered()
            throws Exception {
        try (var f = new Fixture(directory, false)) {
            ((com.fasterxml.jackson.databind.node.ArrayNode) f.input.required("captures"))
                    .add(f.entry.deepCopy());
            assertThrows(IllegalArgumentException.class, f::execute);
        }
        try (var f = new Fixture(directory, false)) {
            f.proof.set(
                    "requestPin",
                    ExportValidationSupport.JSON.valueToTree(
                            f.fixture.pin(
                                    FaturasClienteCaptureFixture.request(
                                            "2026-10-29", "2026-10-29"))));
            f.writeProofOnly();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
        try (var f = new Fixture(directory, false)) {
            ((ObjectNode) f.capture.required("responses").get(2))
                    .put(
                            "completedAt",
                            FaturasClienteCaptureFixture.NOW.plusSeconds(211).toString());
            f.refresh();
            assertThrows(IllegalArgumentException.class, f::execute);
        }
    }

    private static ObjectNode read(final Path path) throws Exception {
        return (ObjectNode) ExportValidationSupport.JSON.readTree(Files.readAllBytes(path));
    }

    private static final class Fixture implements AutoCloseable {
        final FaturasClienteCaptureFixture fixture;
        final ObjectNode row,
                capture,
                input,
                entry,
                proof,
                failedClosure,
                auditClosure,
                proofClosure,
                audit;
        final ExportValidationSupport.Pin requestPin,
                sourcePin,
                producerTestPin,
                producerUnitPin,
                executionPin;
        final ExportValidationSupport.Pin workerPin, configurationPin, driverPin;
        final Path capturePath,
                sourceResultPath,
                failedClosurePath,
                auditPath,
                auditClosurePath,
                proofPath,
                proofClosurePath;
        Path lastDiagnostic;

        Fixture(final Path parent, final boolean invalidRow) throws Exception {
            fixture = new FaturasClienteCaptureFixture(parent, false);
            final var oneDay = FaturasClienteCaptureFixture.request("2026-10-30", "2026-10-30");
            oneDay.put("attemptId", fixture.request.path("attemptId").textValue());
            fixture.request.setAll(oneDay);
            row = FaturasClienteCaptureFixture.row(1);
            row.put("service_at", "2026-10-30T12:00:00-03:00");
            row.put("fit_pyr_name", "private-synthetic-payer");
            if (invalidRow) {
                row.put("comments", true);
            }
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(
                            FaturasClienteCaptureFixture.array(row, row)));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            capture = fixture.capture(2, 100, 10).deepCopy();
            final var complete = ExportValidationSupport.Pin.from(capture.required("dataPin"));
            final var partial = fixture.root.resolve("retained.partial.jsonl");
            Files.write(partial, Files.readAllBytes(complete.path()));
            sourcePin = pin(partial);
            capture.remove("dataPin");
            capture.put("status", "PARTIAL_FATURAS_CLIENTE_CAPTURE_DEADLINE_PARTIAL")
                    .put("captureComplete", false)
                    .put("payloadPromoted", false)
                    .put("wireOutcomeUnknown", false)
                    .put("rowTypesQualified", false)
                    .put("mapperQualified", false)
                    .put("emptyInterval", false)
                    .put("HTTP", 3)
                    .put("httpAdmitted", 3)
                    .put("startedAt", FaturasClienteCaptureFixture.NOW.toString())
                    .put("finishedAt", FaturasClienteCaptureFixture.NOW.plusSeconds(218).toString())
                    .put("retainedPayloadPath", partial.toString())
                    .put("rowTypesContractSha256", FaturasClienteObservedWire52.SHA256);
            capture.set("partialDataPin", tree(sourcePin));
            requestPin = fixture.pin(fixture.request);
            final String actor = UUID.randomUUID().toString();
            producerUnitPin =
                    fixture.pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("syntheticOldUnit", true)
                                    .set(
                                            "counts",
                                            ExportValidationSupport.JSON
                                                    .createObjectNode()
                                                    .put("methods", 1)));
            producerTestPin = fixture.pin(test(producerUnitPin).put("syntheticTestCount", 1));
            sourceResultPath = fixture.root.resolve("private-result.json");
            capturePath = fixture.root.resolve("capture.stdout.log");
            final var context =
                    fixture.pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("privateRoot", fixture.root.toString())
                                    .put("executingRootUUID", actor));
            final var execution =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_FATURAS_CLIENTE_SOURCE_EXECUTION_INPUT_V1")
                            .put("resultPath", sourceResultPath.toString());
            execution.set("requestPin", tree(requestPin));
            execution.set("codeUnitPin", tree(producerUnitPin));
            execution.set("physicalContextPin", tree(context));
            executionPin = fixture.pin(execution);
            capture.set("executionInputPin", tree(executionPin));
            capture.set("codeUnitPin", tree(producerUnitPin));
            capture.set("requestPin", tree(requestPin));
            final var config = ExportValidationSupport.JSON.createObjectNode();
            config.set("executionInput", tree(executionPin));
            config.set("physicalContext", tree(context));
            config.set("request", tree(requestPin));
            config.set("test", tree(producerTestPin));
            configurationPin = fixture.pin(config);
            final var driver =
                    ExportValidationSupport.JSON.createObjectNode().put("success", false);
            driver.set("testPin", tree(producerTestPin));
            driver.set("codeUnitPin", tree(producerUnitPin));
            driverPin = fixture.pin(driver);
            final var worker =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("executingRootUUID", actor)
                            .put("allChildrenClosedKnown", true);
            worker.putArray("processes")
                    .addObject()
                    .put("closedKnown", true)
                    .put("stdinClosed", true)
                    .put("pipesEof", true)
                    .put("exit", 3);
            workerPin = save(fixture.root.resolve("worker-result.json"), worker);
            failedClosure =
                    closure(actor, false)
                            .put("schema", "ROOT_FATURAS_CLIENTE_FINITE_SOURCE_CLOSURE0753_V1")
                            .put("SourceMaximum", 1)
                            .put("HTTPMaximum", 17);
            failedClosure.putObject("failure").put("message", "FRESH_NATIVE_INVENTORY_PASS");
            failedClosurePath = fixture.root.resolve("parent-result.json");
            final var auditFolder = Files.createDirectory(fixture.root.resolve("audit59"));
            final var proofFolder = Files.createDirectory(fixture.root.resolve("proof69"));
            auditPath = auditFolder.resolve("result.json");
            auditClosurePath = auditFolder.resolve("parent-result.json");
            proofPath = proofFolder.resolve("result.json");
            proofClosurePath = proofFolder.resolve("parent-result.json");
            auditClosure = closure(actor, true);
            proofClosure = closure(actor, true);
            audit =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put(
                                    "schema",
                                    "ROOT_FATURAS_CLIENTE_TERMINAL_FAILED_WIRE59_PHYSICAL_OBSERVATION_V1")
                            .put("physicalHttpStarted", 3)
                            .put("physicalHttpCompleted", 3)
                            .put("declinedBeforeCurlStart", 0)
                            .put("wireOperationDirectoryCount", 3)
                            .put("independentlyCountedRows", 2);
            yes(
                    audit,
                    "success",
                    "physicalOutcomeKnown",
                    "allStartedOperationsClosedKnown",
                    "originalResponsePinSetEqualsPhysicalBodies",
                    "completeWireDirectoryEnumeration",
                    "noUnmatchedWireDirectory",
                    "sharedUnknownLatchAbsent",
                    "shared429LatchAbsent",
                    "actualTerminalEmptyArrayVerified",
                    "allOriginalResponseBodiesReadToEof",
                    "allRetainedOriginalBytesReadToEof",
                    "retainedOriginalBytesMatchEveryResponseRecord",
                    "infoFields52Filters18Verified",
                    "serviceAtVerifiedForEveryRetainedRow",
                    "pinsVerifiedBeforeAndAfter",
                    "captureDeadlinePartialRemainsRejected",
                    "lastResponseWithinDeclaredSourceBudget");
            no(
                    audit,
                    "historicalProducerFactsRewritten",
                    "historicalProducerCaptureComplete",
                    "historicalProducerPayloadPromoted",
                    "historicalProducerWireOutcomeUnknown",
                    "sourceCaptureQualified",
                    "producerDeadlineQualified");
            proof =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put(
                                    "schema",
                                    "FATURAS_CLIENTE_RETAINED_DAY_DATA_COMPLETENESS_SOURCE0_V1")
                            .put("executingRootUUID", actor)
                            .put("profile", FaturasClienteIndependentRequest.PROFILE)
                            .put("entity", "FATURAS_POR_CLIENTE")
                            .put("reportId", 4924)
                            .put("captureKind", "RECOVERED_RETAINED_DAY_V1")
                            .put("counterScope", "INDEPENDENT_RETAINED_DATA_INTEGRITY_ONLY_V1")
                            .put("periodStart", "2026-10-30")
                            .put("periodEnd", "2026-10-30")
                            .put("day", "2026-10-30")
                            .put("rows", 2)
                            .put("retainedBytes", sourcePin.bytes())
                            .put("currentMapperInvocations", 0)
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0)
                            .put("historicalProducerStatus", capture.path("status").textValue())
                            .put("historicalParentFailureCode", "FRESH_NATIVE_INVENTORY_PASS");
            proof.putNull("producerSummaryPin");
            yes(
                    proof,
                    "success",
                    "dataCompletenessQualified",
                    "pinsVerifiedBeforeAndAfter",
                    "allOriginalResponseBodiesReadToEof",
                    "allOriginalResponseRecordBytesMatchRetainedRaw",
                    "actualTerminalArrayVerified");
            no(
                    proof,
                    "sourceCaptureQualified",
                    "producerDeadlineQualified",
                    "currentMapperQualificationPerformed",
                    "historicalProducerFactsRewritten",
                    "contentAccepted",
                    "entityQualified",
                    "mainResumeAllowed",
                    "partialRecordsSelected",
                    "bytesRenamedOrRewritten",
                    "deduplicationPerformed");
            final var currentUnit =
                    fixture.pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("syntheticCurrentUnit", true));
            final var catalog =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "FATURAS_CLIENTE_NATIVE52_CANDIDATE_CATALOG_V1")
                            .put("profile", FaturasClienteIndependentRequest.PROFILE)
                            .put("entity", "FATURAS_POR_CLIENTE")
                            .put("reportId", 4924);
            final var columns = catalog.putArray("columns");
            for (int i = 0; i < 52; i++) {
                columns.addObject().put("syntheticOrdinal", i + 1);
            }
            input =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", FaturasClienteRecoveredDayQualification.INPUT_SCHEMA)
                            .put("privateRoot", fixture.root.toString());
            input.set("consumerTestPin", tree(fixture.pin(test(currentUnit))));
            input.set("codeUnitPin", tree(currentUnit));
            input.set("catalogPin", tree(fixture.pin(catalog)));
            entry = input.putArray("captures").addObject();
            entry.put("captureKind", "RECOVERED_RETAINED_DAY_V1").putNull("producerSummaryPin");
            refresh();
        }

        void refresh() throws Exception {
            final var cap = save(capturePath, capture);
            final var srcResult = save(sourceResultPath, capture);
            final var failed = save(failedClosurePath, failedClosure);
            final var auditInput =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_FATURAS_CLIENTE_TERMINAL_FAILED_WIRE59_INPUT_V1");
            auditInput.set("executionInputPin", tree(executionPin));
            auditInput.set("captureReceiptPin", tree(cap));
            auditInput.set("closurePin", tree(failed));
            auditInput.set("workerPin", tree(workerPin));
            auditInput.set("configurationPin", tree(configurationPin));
            auditInput.set("driverPin", tree(driverPin));
            auditInput.set("producerTestPin", tree(producerTestPin));
            final var auditInputPin = fixture.pin(auditInput);
            audit.set("inputPin", tree(auditInputPin));
            audit.set("producerCaptureReceiptPin", tree(cap));
            audit.set("producerClosurePin", tree(failed));
            audit.set("producerWorkerPin", tree(workerPin));
            audit.set("sourceResultPin", tree(srcResult));
            audit.set("requestPin", tree(requestPin));
            audit.set("retainedSourcePin", tree(sourcePin));
            final var actualAudit = save(auditPath, audit);
            final var closedAudit = save(auditClosurePath, auditClosure);
            final var proofInput =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put(
                                    "schema",
                                    "ROOT_FATURAS_CLIENTE_RETAINED_DAY_DATA_COMPLETENESS69_INPUT_V1");
            proofInput.set("audit59InputPin", tree(auditInputPin));
            proofInput.set("audit59ReceiptPin", tree(actualAudit));
            proofInput.set("audit59ClosurePin", tree(closedAudit));
            proof.set("inputPin", tree(fixture.pin(proofInput)));
            proof.set("audit59InputPin", tree(auditInputPin));
            proof.set("audit59ReceiptPin", tree(actualAudit));
            proof.set("audit59ClosurePin", tree(closedAudit));
            proof.set("producerCaptureReceiptPin", tree(cap));
            proof.set("producerClosurePin", tree(failed));
            proof.set("producerWorkerPin", tree(workerPin));
            proof.set("producerConfigurationPin", tree(configurationPin));
            proof.set("producerDriverPin", tree(driverPin));
            proof.set("executionInputPin", tree(executionPin));
            proof.set("sourceResultPin", tree(srcResult));
            proof.set("producerTestPin", tree(producerTestPin));
            proof.set("producerCodeUnitPin", tree(producerUnitPin));
            proof.set("requestPin", tree(requestPin));
            proof.set("sourcePin", tree(sourcePin));
            for (final var name :
                    Set.of(
                            "CaptureComplete",
                            "PayloadPromoted",
                            "WireOutcomeUnknown",
                            "RowTypesQualified",
                            "MapperQualified")) {
                final String original = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                proof.put("historicalProducer" + name, capture.required(original).booleanValue());
            }
            entry.set("captureReceiptPin", tree(cap));
            entry.set("producerClosurePin", tree(failed));
            entry.set("requestPin", tree(requestPin));
            entry.set("sourcePin", tree(sourcePin));
            writeProofOnly();
        }

        void writeProofOnly() throws Exception {
            entry.set("dataCompletenessReceiptPin", tree(save(proofPath, proof)));
            entry.set("dataCompletenessClosurePin", tree(save(proofClosurePath, proofClosure)));
        }

        ObjectNode execute() throws Exception {
            lastDiagnostic = fixture.root.resolve("qualification-" + UUID.randomUUID() + ".json");
            return FaturasClienteMapperQualificationMain.execute(
                    fixture.pin(input),
                    lastDiagnostic,
                    new ExportValidationSupport.Budget(Duration.ofSeconds(80)));
        }

        ExportValidationSupport.Pin pin(final Path path) throws Exception {
            final byte[] bytes = Files.readAllBytes(path);
            return new ExportValidationSupport.Pin(
                    path, ExportValidationSupport.sha256(bytes), bytes.length);
        }

        ExportValidationSupport.Pin save(final Path path, final JsonNode value) throws Exception {
            Files.write(path, FaturasClienteCaptureFixture.json(value));
            return pin(path);
        }

        private static JsonNode tree(final ExportValidationSupport.Pin value) {
            return ExportValidationSupport.JSON.valueToTree(value);
        }

        private static ObjectNode test(final ExportValidationSupport.Pin unit) {
            final var result = ExportValidationSupport.JSON.createObjectNode().put("mode", "TEST");
            yes(result, "success", "syntheticTestsPassed", "originalClosedKnown", "stateRestored");
            result.set("unit", tree(unit));
            return result;
        }

        private static ObjectNode closure(final String actor, final boolean success) {
            final var result =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1")
                            .put("executingRootUUID", actor)
                            .put("success", success)
                            .put("OriginalCalls", 1)
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0);
            yes(result, "originalClosedKnown", "stateRestored", "OriginalJobStarted");
            no(result, "slotRetained");
            return result;
        }

        private static void yes(final ObjectNode node, final String... names) {
            for (final var name : names) {
                node.put(name, true);
            }
        }

        private static void no(final ObjectNode node, final String... names) {
            for (final var name : names) {
                node.put(name, false);
            }
        }

        @Override
        public void close() throws java.io.IOException {
            fixture.close();
        }
    }
}

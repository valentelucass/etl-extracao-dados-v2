package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored metadata and synthetic transport; never a real Producer/Consumer authority receipt. */
class ContasPagarMapperQualificationMainTest {
    @TempDir Path directory;

    @Test
    void currentConsumerQualifiesRawWhileTheAuthoredHistoricalProducerFalseFactRemainsIntact()
            throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var input = input(fixture);
            final var producerPin =
                    ContasPagarRawMapperQualifier.pin(
                            input.path("captures").get(0).path("producerSummaryPin"));
            final byte[] before = Files.readAllBytes(producerPin.path());
            final var inputPin = fixture.pin(input);
            final Path target = fixture.root.resolve("new-consumer-qualification.json");
            final var out = new ByteArrayOutputStream();
            final var err = new ByteArrayOutputStream();
            assertEquals(
                    0,
                    ContasPagarMapperQualificationMain.run(
                            new String[] {
                                "qualify",
                                "--input",
                                inputPin.path().toString(),
                                "--sha256",
                                inputPin.sha256(),
                                "--output",
                                target.toString()
                            },
                            new PrintStream(out),
                            new PrintStream(err)));
            assertEquals("", err.toString());
            assertFalse(out.toString().contains("authored-document"));
            final var receipt = ExportValidationSupport.JSON.readTree(out.toByteArray());
            assertEquals(
                    "CONTAS_A_PAGAR_OFFLINE_REQUALIFICATION_EXECUTED_RECEIPT_V1",
                    receipt.path("schema").textValue());
            assertTrue(receipt.path("success").booleanValue());
            final var current = ExportValidationSupport.JSON.readTree(Files.readAllBytes(target));
            assertEquals(
                    "CONTAS_A_PAGAR_SOURCE_REQUALIFICATION_V1", current.path("schema").textValue());
            assertEquals(1, current.path("captureCount").intValue());
            assertEquals(1, current.path("currentMapperInvocations").intValue());
            assertEquals(0, current.path("rejectedRows").intValue());
            final var capture = current.path("captures").get(0);
            assertFalse(capture.path("producerMapperQualified").booleanValue());
            assertTrue(capture.path("mapperQualified").booleanValue());
            assertTrue(capture.path("actualTerminalArrayVerified").booleanValue());
            assertTrue(capture.path("producerPhysicalClosureVerified").booleanValue());
            assertFalse(capture.path("historicalProducerFactRewritten").booleanValue());
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    before, Files.readAllBytes(producerPin.path()));
        }
    }

    @Test
    void duplicateRawOrRequestCannotInflateTheQualificationCoverage() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var input = input(fixture);
            ((com.fasterxml.jackson.databind.node.ArrayNode) input.path("captures"))
                    .add(input.path("captures").get(0).deepCopy());
            final var target = fixture.root.resolve("refused.json");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarMapperQualificationMain.execute(
                                    fixture.pin(input), target, budget()));
            assertFalse(Files.exists(target));
        }
    }

    @Test
    void unclosedConsumerTestOrWrongRawBindingCannotPublishAQualification() throws Exception {
        for (final String mutation : new String[] {"test", "raw", "closure"}) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                final var input = input(fixture);
                if ("test".equals(mutation)) {
                    final var receipt =
                            ExportValidationSupport.JSON.createObjectNode().put("success", false);
                    input.set(
                            "consumerTestPin",
                            ExportValidationSupport.JSON.valueToTree(fixture.pin(receipt)));
                } else if ("raw".equals(mutation)) {
                    ((ObjectNode) input.path("captures").get(0))
                            .set(
                                    "sourcePin",
                                    ExportValidationSupport.JSON.valueToTree(
                                            fixture.pin(new byte[] {'{', '}', '\n'})));
                } else {
                    final var closure =
                            fixture.pin(
                                    ExportValidationSupport.JSON
                                            .createObjectNode()
                                            .put("success", false)
                                            .put("authoredMetadataOnly", true));
                    ((ObjectNode) input.path("captures").get(0))
                            .set(
                                    "producerClosurePin",
                                    ExportValidationSupport.JSON.valueToTree(closure));
                }
                final var target = fixture.root.resolve("refused.json");
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ContasPagarMapperQualificationMain.execute(
                                        fixture.pin(input), target, budget()));
                assertFalse(Files.exists(target));
            }
        }
    }

    @Test
    void qualificationBatchMustContainOneToThreeCaptureEntries() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var original = input(fixture);
            for (final int count : new int[] {0, 4}) {
                final var changed = original.deepCopy();
                final var captures = changed.putArray("captures");
                for (int index = 0; index < count; index++) {
                    captures.add(original.path("captures").get(0).deepCopy());
                }
                final var target = fixture.root.resolve("refused-batch-" + count + ".json");
                assertEquals(
                        "CONTAS_A_PAGAR_REQUALIFICATION_ONE_TO_THREE_CAPTURES",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () ->
                                                ContasPagarMapperQualificationMain.execute(
                                                        fixture.pin(changed), target, budget()))
                                .getMessage());
                assertFalse(Files.exists(target));
            }
        }
    }

    @Test
    void existingOutputAndInputOutputCollisionNeverOverwriteFiles() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var inputPin = fixture.pin(input(fixture));
            final byte[] before = Files.readAllBytes(inputPin.path());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ContasPagarMapperQualificationMain.execute(
                                    inputPin, inputPin.path(), budget()));
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    before, Files.readAllBytes(inputPin.path()));
        }
    }

    @Test
    void actualBodyTerminalContiguityRowCountsAndPhysicalCountersCannotBeForgedByFlags()
            throws Exception {
        for (final String mutation :
                new String[] {
                    "terminal", "page", "rows", "counters", "scope", "createdScope", "createdFilter"
                }) {
            try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
                final var input = input(fixture);
                final var entry = (ObjectNode) input.path("captures").get(0);
                final var producer =
                        (ObjectNode)
                                ContasPagarRawMapperQualifier.pin(
                                                entry.required("producerSummaryPin"))
                                        .json(262144, budget());
                final var result =
                        (ObjectNode)
                                ContasPagarRawMapperQualifier.pin(producer.required("sourceResult"))
                                        .json(262144, budget());
                switch (mutation) {
                    case "terminal" ->
                            ((ObjectNode) result.path("responses").get(2))
                                    .set(
                                            "rawBodyPin",
                                            ExportValidationSupport.JSON.valueToTree(
                                                    fixture.pin(
                                                            ContasPagarCaptureFixture.array(
                                                                    ContasPagarCaptureFixture.row(
                                                                            1)))));
                    case "page" -> ((ObjectNode) result.path("responses").get(1)).put("page", 2);
                    case "rows" -> {
                        result.put("rows", 2);
                        producer.put("rows", 2);
                    }
                    case "scope" -> result.put("filterScopePolicy", "LEGACY_TWO_DATE_FILTERS");
                    case "createdScope" -> result.put("createdAtScopeEnforced", true);
                    case "createdFilter" ->
                            result.put("createdAtFilter", "2026-10-12 - 2026-10-18");
                    default -> {
                        producer.remove("physicalSourceCountersFinal");
                        producer.put("sourceCountersFinal", true);
                    }
                }
                producer.set(
                        "sourceResult",
                        ExportValidationSupport.JSON.valueToTree(fixture.pin(result)));
                entry.set(
                        "producerSummaryPin",
                        ExportValidationSupport.JSON.valueToTree(fixture.pin(producer)));
                final var target = fixture.root.resolve("refused-" + mutation + ".json");
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ContasPagarMapperQualificationMain.execute(
                                        fixture.pin(input), target, budget()));
                assertFalse(Files.exists(target));
            }
        }
    }

    @Test
    void aKnownSameRunClosureWithRetainedSlotStillCannotQualify() throws Exception {
        try (var fixture = new ContasPagarCaptureFixture(directory, false)) {
            final var input = input(fixture);
            final var entry = (ObjectNode) input.path("captures").get(0);
            final var closurePin =
                    ContasPagarRawMapperQualifier.pin(entry.required("producerClosurePin"));
            final var closure = (ObjectNode) closurePin.json(262144, budget());
            closure.put("slotRetained", true);
            final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(closure);
            Files.write(closurePin.path(), bytes);
            entry.set(
                    "producerClosurePin",
                    ExportValidationSupport.JSON.valueToTree(
                            new ExportValidationSupport.Pin(
                                    closurePin.path(),
                                    ExportValidationSupport.sha256(bytes),
                                    bytes.length)));
            final var target = fixture.root.resolve("refused-slot.json");
            assertEquals(
                    "CONTAS_A_PAGAR_REQUALIFICATION_PRODUCER_SLOT_EMPTY",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            ContasPagarMapperQualificationMain.execute(
                                                    fixture.pin(input), target, budget()))
                            .getMessage());
            assertFalse(Files.exists(target));
        }
    }

    private static ObjectNode input(final ContasPagarCaptureFixture fixture) throws Exception {
        final var row = ContasPagarCaptureFixture.row(1).put("document", "authored-document");
        fixture.port.pages.add(
                ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array(row)));
        fixture.port.pages.add(ContasPagarCaptureFixture.json(ContasPagarCaptureFixture.array()));
        final var captured = fixture.capture(2, 100, 12000);
        final var sourcePin = ContasPagarRawMapperQualifier.pin(captured.required("dataPin"));
        final var requestPin = fixture.pin(fixture.request);
        // These are authored historical metadata, not an executed previous Producer receipt.
        captured.put("mapperQualified", false).put("mapperRejectedOccurrences", 1);
        final var sourceResultPin = fixture.pin(captured);
        final var producerTestPin =
                fixture.pin(
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("authoredProducerTest", true));
        final var producer =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_CONTAS_A_PAGAR_FRESH_PART_CAPTURE_CLOSED_CANDIDATE_V1")
                        .put("success", true)
                        .put("physicalSourceCountersFinal", true)
                        .put("allOccurrencesRetained", true)
                        .put("executingRootUUID", fixture.attempt().toString())
                        .put("terminalEmptyPageObserved", true)
                        .put("mapperQualified", false)
                        .put("rows", 1);
        producer.set("sourceResult", ExportValidationSupport.JSON.valueToTree(sourceResultPin));
        producer.set("request", ExportValidationSupport.JSON.valueToTree(requestPin));
        producer.set("dataPin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        producer.set("test", ExportValidationSupport.JSON.valueToTree(producerTestPin));
        final var producerPin = fixture.pin(producer);
        final var producerClosure =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_CONTAS_A_PAGAR_FINITE_SOURCE_CLOSURE0751_V1")
                        .put("executingRootUUID", fixture.attempt().toString())
                        .put("success", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true)
                        .put("slotRetained", false)
                        .put("OriginalJobStarted", true)
                        .put("OriginalCalls", 1)
                        .put("SourceMaximum", 1)
                        .put("HTTPMaximum", 17)
                        .put("SQL", 0)
                        .put("authoredMetadataOnly", true);
        final byte[] closureBytes = ExportValidationSupport.JSON.writeValueAsBytes(producerClosure);
        final Path closurePath = fixture.root.resolve("parent-result.json");
        Files.write(closurePath, closureBytes, java.nio.file.StandardOpenOption.CREATE_NEW);
        final var producerClosurePin =
                new ExportValidationSupport.Pin(
                        closurePath,
                        ExportValidationSupport.sha256(closureBytes),
                        closureBytes.length);
        final var unitPin =
                fixture.pin(
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("authoredConsumerUnit", true));
        final var test =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("success", true)
                        .put("syntheticTestsPassed", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true)
                        .put("authoredMetadataOnly", true);
        test.set("unit", ExportValidationSupport.JSON.valueToTree(unitPin));
        final var catalog =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CONTAS_A_PAGAR_NATIVE28_CANDIDATE_CATALOG_V1")
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("entity", "CONTAS_A_PAGAR")
                        .put("reportId", 8636)
                        .put("authoredMetadataOnly", true);
        for (int index = 0; index < 28; index++) {
            catalog.withArray("columns").addObject();
        }
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_CONTAS_A_PAGAR_RAW_REQUALIFICATION_INPUT_V1")
                        .put("privateRoot", fixture.root.toString());
        input.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(test)));
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        input.set("catalogPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(catalog)));
        final var entry = input.putArray("captures").addObject();
        entry.set("producerSummaryPin", ExportValidationSupport.JSON.valueToTree(producerPin));
        entry.set(
                "producerClosurePin", ExportValidationSupport.JSON.valueToTree(producerClosurePin));
        entry.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        entry.set("sourcePin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        return input;
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}

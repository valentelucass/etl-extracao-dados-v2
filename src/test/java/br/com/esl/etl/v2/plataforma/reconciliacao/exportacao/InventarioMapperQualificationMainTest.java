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
class InventarioMapperQualificationMainTest {
    @TempDir Path directory;

    @Test
    void currentConsumerQualifiesRawWhileTheAuthoredHistoricalProducerFalseFactRemainsIntact()
            throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var input = input(fixture);
            final var producerPin =
                    InventarioRawMapperQualifier.pin(
                            input.path("captures").get(0).path("producerSummaryPin"));
            final byte[] before = Files.readAllBytes(producerPin.path());
            final var inputPin = fixture.pin(input);
            final Path target = fixture.root.resolve("new-consumer-qualification.json");
            final var out = new ByteArrayOutputStream();
            final var err = new ByteArrayOutputStream();
            assertEquals(
                    0,
                    InventarioMapperQualificationMain.run(
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
            assertFalse(out.toString().contains("authored-invoice"));
            final var receipt = ExportValidationSupport.JSON.readTree(out.toByteArray());
            assertEquals(
                    "INVENTARIO_OFFLINE_REQUALIFICATION_EXECUTED_RECEIPT_V1",
                    receipt.path("schema").textValue());
            assertTrue(receipt.path("success").booleanValue());
            final var current = ExportValidationSupport.JSON.readTree(Files.readAllBytes(target));
            assertEquals(
                    "INVENTARIO_SOURCE_REQUALIFICATION_V1", current.path("schema").textValue());
            assertEquals(1, current.path("captureCount").intValue());
            assertEquals(1, current.path("currentMapperInvocations").intValue());
            assertEquals(0, current.path("rejectedRows").intValue());
            final var capture = current.path("captures").get(0);
            assertFalse(capture.path("producerMapperQualified").booleanValue());
            assertTrue(capture.path("mapperQualified").booleanValue());
            assertTrue(capture.path("actualTerminalArrayVerified").booleanValue());
            assertFalse(capture.path("historicalProducerFactRewritten").booleanValue());
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    before, Files.readAllBytes(producerPin.path()));
        }
    }

    @Test
    void duplicateRawOrRequestCannotInflateTheQualificationCoverage() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var input = input(fixture);
            ((com.fasterxml.jackson.databind.node.ArrayNode) input.path("captures"))
                    .add(input.path("captures").get(0).deepCopy());
            final var target = fixture.root.resolve("refused.json");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioMapperQualificationMain.execute(
                                    fixture.pin(input), target, budget()));
            assertFalse(Files.exists(target));
        }
    }

    @Test
    void unclosedConsumerTestOrWrongRawBindingCannotPublishAQualification() throws Exception {
        for (final String mutation : new String[] {"test", "raw"}) {
            try (var fixture = new InventarioCaptureFixture(directory, false)) {
                final var input = input(fixture);
                if ("test".equals(mutation)) {
                    final var receipt =
                            ExportValidationSupport.JSON.createObjectNode().put("success", false);
                    input.set(
                            "consumerTestPin",
                            ExportValidationSupport.JSON.valueToTree(fixture.pin(receipt)));
                } else {
                    ((ObjectNode) input.path("captures").get(0))
                            .set(
                                    "sourcePin",
                                    ExportValidationSupport.JSON.valueToTree(
                                            fixture.pin(new byte[] {'{', '}', '\n'})));
                }
                final var target = fixture.root.resolve("refused.json");
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                InventarioMapperQualificationMain.execute(
                                        fixture.pin(input), target, budget()));
                assertFalse(Files.exists(target));
            }
        }
    }

    @Test
    void existingOutputAndInputOutputCollisionNeverOverwriteFiles() throws Exception {
        try (var fixture = new InventarioCaptureFixture(directory, false)) {
            final var inputPin = fixture.pin(input(fixture));
            final byte[] before = Files.readAllBytes(inputPin.path());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            InventarioMapperQualificationMain.execute(
                                    inputPin, inputPin.path(), budget()));
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                    before, Files.readAllBytes(inputPin.path()));
        }
    }

    private static ObjectNode input(final InventarioCaptureFixture fixture) throws Exception {
        final var row = InventarioCaptureFixture.row(1);
        final var invoices = row.putArray("cnr_c_s_fit_invoices_mapping");
        for (int index = 0; index < 33; index++) {
            invoices.add(index % 2 == 0 ? "authored-invoice-A" : "authored-invoice-B");
        }
        fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array(row)));
        fixture.port.pages.add(InventarioCaptureFixture.json(InventarioCaptureFixture.array()));
        final var captured = fixture.capture(2, 100, 12000);
        final var sourcePin = InventarioRawMapperQualifier.pin(captured.required("dataPin"));
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
                        .put("schema", "ROOT_INVENTARIO_FRESH_PART_CAPTURE_CLOSED_CANDIDATE_V1")
                        .put("success", true)
                        .put("sourceCountersFinal", true)
                        .put("allOccurrencesRetained", true)
                        .put("terminalEmptyPageObserved", true)
                        .put("mapperQualified", false)
                        .put("rows", 1);
        producer.set("sourceResult", ExportValidationSupport.JSON.valueToTree(sourceResultPin));
        producer.set("request", ExportValidationSupport.JSON.valueToTree(requestPin));
        producer.set("dataPin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        producer.set("test", ExportValidationSupport.JSON.valueToTree(producerTestPin));
        final var producerPin = fixture.pin(producer);
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
                        .put("schema", "INVENTARIO_NATIVE26_CANDIDATE_CATALOG_V1")
                        .put("entity", "INVENTARIO")
                        .put("reportId", 10633)
                        .put("authoredMetadataOnly", true);
        for (int index = 0; index < 26; index++) {
            catalog.withArray("columns").addObject();
        }
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_INVENTARIO_RAW_REQUALIFICATION_INPUT_V1")
                        .put("privateRoot", fixture.root.toString());
        input.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(test)));
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        input.set("catalogPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(catalog)));
        final var entry = input.putArray("captures").addObject();
        entry.set("producerSummaryPin", ExportValidationSupport.JSON.valueToTree(producerPin));
        entry.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        entry.set("sourcePin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        return input;
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }
}

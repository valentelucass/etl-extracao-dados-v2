package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalizadorMapperQualificationMainTest {
    @TempDir Path directory;

    @Test
    void currentConsumerRequalifiesOriginalRecordsWithoutRewritingDifferentHistoricalProducerFlags()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var input = input(fixture, true);
            final var entry = input.required("captures").get(0);
            final var producerPin =
                    LocalizadorRawMapperQualifier.pin(entry.required("producerSummaryPin"));
            final byte[] original = Files.readAllBytes(producerPin.path());
            final var resultPath = fixture.root.resolve("result.json");
            final var receipt =
                    LocalizadorMapperQualificationMain.execute(
                            fixture.pin(input), resultPath, budget());
            assertTrue(receipt.path("success").booleanValue());
            assertTrue(receipt.path("mapperQualified").booleanValue());
            assertTrue(receipt.path("rowTypesQualified").booleanValue());
            assertEquals(2, receipt.path("rows").intValue());
            final var result = ExportValidationSupport.JSON.readTree(Files.readString(resultPath));
            final var current = result.required("captures").get(0);
            assertFalse(current.path("producerMapperQualified").booleanValue());
            assertFalse(current.path("producerRowTypesQualified").booleanValue());
            assertTrue(current.path("mapperQualified").booleanValue());
            assertTrue(current.path("rowTypesQualified").booleanValue());
            assertTrue(
                    current.path("allOriginalResponseRecordBytesMatchRetainedRaw").booleanValue());
            assertFalse(current.path("historicalProducerFactRewritten").booleanValue());
            assertFalse(result.path("contentAccepted").booleanValue());
            assertArrayEquals(original, Files.readAllBytes(producerPin.path()));
        }
    }

    @Test
    void wrongPhysicalClosureOrDuplicateCaptureCannotBePromotedByValidRowTypesAlone()
            throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var input = input(fixture, false);
            final var captures =
                    (com.fasterxml.jackson.databind.node.ArrayNode) input.required("captures");
            captures.add(captures.get(0).deepCopy());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorMapperQualificationMain.execute(
                                    fixture.pin(input),
                                    fixture.root.resolve("duplicate.json"),
                                    budget()));
            captures.remove(1);
            final Path closurePath =
                    LocalizadorRawMapperQualifier.pin(
                                    captures.get(0).required("producerClosurePin"))
                            .path();
            final var closure =
                    (ObjectNode)
                            ExportValidationSupport.JSON.readTree(Files.readString(closurePath));
            closure.put("stateRestored", false);
            final byte[] bytes = LocalizadorCaptureFixture.json(closure);
            Files.write(closurePath, bytes);
            ((ObjectNode) captures.get(0))
                    .set(
                            "producerClosurePin",
                            ExportValidationSupport.JSON.valueToTree(
                                    new ExportValidationSupport.Pin(
                                            closurePath,
                                            ExportValidationSupport.sha256(bytes),
                                            bytes.length)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorMapperQualificationMain.execute(
                                    fixture.pin(input),
                                    fixture.root.resolve("unclosed.json"),
                                    budget()));
        }
    }

    @Test
    void anAuthoringSchemaOrMissingConsumerTestIsRefusedBeforeAnyOutput() throws Exception {
        try (var fixture = new LocalizadorCaptureFixture(directory, false)) {
            final var input = input(fixture, false);
            input.put("schema", "ROOT_LOCALIZADOR_RAW_AUTHORING_QUALIFICATION_INPUT_V1");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorMapperQualificationMain.execute(
                                    fixture.pin(input),
                                    fixture.root.resolve("bad-schema.json"),
                                    budget()));
            input.put("schema", "ROOT_LOCALIZADOR_RAW_REQUALIFICATION_INPUT_V1");
            input.remove("consumerTestPin");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorMapperQualificationMain.execute(
                                    fixture.pin(input),
                                    fixture.root.resolve("missing-test.json"),
                                    budget()));
            assertFalse(Files.exists(fixture.root.resolve("missing-test.json")));
        }
    }

    @Test
    void malformedUsageReturnsOnlyAStableCodeAndNeverPrivatePayload() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                LocalizadorMapperQualificationMain.run(
                        new String[] {"qualify"}, new PrintStream(out), new PrintStream(err)));
        assertEquals("", out.toString());
        assertEquals("LOCALIZADOR_REQUALIFICATION_USAGE" + System.lineSeparator(), err.toString());
    }

    private static ObjectNode input(
            final LocalizadorCaptureFixture fixture, final boolean historicalRefusal)
            throws Exception {
        final var row = LocalizadorCaptureFixture.row(1);
        fixture.port.pages.add(
                LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array(row, row)));
        fixture.port.pages.add(LocalizadorCaptureFixture.json(LocalizadorCaptureFixture.array()));
        final var source = fixture.capture(2, 100, 10).deepCopy();
        if (historicalRefusal) {
            source.put("mapperQualified", false).put("rowTypesQualified", false);
        }
        final var sourcePin = ExportValidationSupport.Pin.from(source.required("dataPin"));
        final var requestPin = fixture.pin(fixture.request);
        final var sourceResultPin = fixture.pin(source);
        final var unitPin =
                fixture.pin(
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("syntheticCurrentUnit", true));
        final var test =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("mode", "TEST")
                        .put("success", true)
                        .put("syntheticTestsPassed", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true);
        test.set("unit", ExportValidationSupport.JSON.valueToTree(unitPin));
        final var testPin = fixture.pin(test);
        final var catalog =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "LOCALIZADOR_NATIVE26_CANDIDATE_CATALOG_V1")
                        .put("profile", LocalizadorIndependentRequest.PROFILE)
                        .put("entity", "LOCALIZADOR")
                        .put("reportId", 8656)
                        .put("syntheticFixtureOnly", true);
        final var columns = catalog.putArray("columns");
        for (int index = 0; index < 26; index++) {
            columns.addObject().put("syntheticOrdinal", index + 1);
        }
        final var producerTestPin =
                fixture.pin(
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("syntheticOldTest", true));
        final String root = UUID.randomUUID().toString();
        final var producer =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_LOCALIZADOR_FRESH_PART_CAPTURE_CLOSED_CANDIDATE_V1")
                        .put("executingRootUUID", root)
                        .put("success", true)
                        .put("physicalSourceCountersFinal", true)
                        .put("allOccurrencesRetained", true)
                        .put("terminalEmptyPageObserved", true)
                        .put("rows", 2)
                        .put("mapperQualified", !historicalRefusal)
                        .put("rowTypesQualified", !historicalRefusal);
        producer.set("request", ExportValidationSupport.JSON.valueToTree(requestPin));
        producer.set("dataPin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        producer.set("sourceResult", ExportValidationSupport.JSON.valueToTree(sourceResultPin));
        producer.set("test", ExportValidationSupport.JSON.valueToTree(producerTestPin));
        final var closure =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_LOCALIZADOR_FINITE_SOURCE_CLOSURE0752_V1")
                        .put("executingRootUUID", root)
                        .put("success", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true)
                        .put("OriginalJobStarted", true)
                        .put("slotRetained", false)
                        .put("OriginalCalls", 1)
                        .put("SourceMaximum", 1)
                        .put("HTTPMaximum", 17)
                        .put("SQL", 0);
        final Path producerPath = fixture.root.resolve("capture-summary.json");
        final Path closurePath = fixture.root.resolve("parent-result.json");
        final byte[] producerBytes = LocalizadorCaptureFixture.json(producer);
        final byte[] closureBytes = LocalizadorCaptureFixture.json(closure);
        Files.write(producerPath, producerBytes);
        Files.write(closurePath, closureBytes);
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_LOCALIZADOR_RAW_REQUALIFICATION_INPUT_V1")
                        .put("privateRoot", fixture.root.toString());
        input.set("consumerTestPin", ExportValidationSupport.JSON.valueToTree(testPin));
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unitPin));
        input.set("catalogPin", ExportValidationSupport.JSON.valueToTree(fixture.pin(catalog)));
        final var entry = input.putArray("captures").addObject();
        entry.set(
                "producerSummaryPin",
                ExportValidationSupport.JSON.valueToTree(
                        new ExportValidationSupport.Pin(
                                producerPath,
                                ExportValidationSupport.sha256(producerBytes),
                                producerBytes.length)));
        entry.set(
                "producerClosurePin",
                ExportValidationSupport.JSON.valueToTree(
                        new ExportValidationSupport.Pin(
                                closurePath,
                                ExportValidationSupport.sha256(closureBytes),
                                closureBytes.length)));
        entry.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        entry.set("sourcePin", ExportValidationSupport.JSON.valueToTree(sourcePin));
        return input;
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(80));
    }
}

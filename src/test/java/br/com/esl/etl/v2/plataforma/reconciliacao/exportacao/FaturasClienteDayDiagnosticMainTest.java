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
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteDayDiagnosticMainTest {
    @TempDir Path directory;

    @Test
    void actualReceiptKeepsFourProjectionsPrivateAndCannotApproveTheWeek() throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, false);
        final var output = directory.resolve("day-result.json");
        final var receipt = execute(fixture, input, output);
        assertEquals(
                "FATURAS_CLIENTE_OFFLINE_DAY_DIAGNOSTIC_EXECUTED_RECEIPT_V1",
                receipt.path("schema").textValue());
        assertTrue(receipt.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(receipt.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(receipt.path("periodComparisonComplete").booleanValue());
        assertFalse(receipt.path("contentAccepted").booleanValue());
        assertFalse(receipt.has("fullValueProjection"));
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(2097152, FaturasClienteContentFixture.budget());
        assertTrue(diagnostic.required("fullValueProjection").has("api"));
        assertTrue(diagnostic.required("originalValueProjection").has("ui"));
        assertEquals(52, diagnostic.required("columnDiagnostics").size());
        assertEquals(0, receipt.path("Source").intValue());
        assertEquals(0, receipt.path("HTTP").intValue());
        assertEquals(0, receipt.path("SQL").intValue());

        final var uppercaseFullTest =
                (ObjectNode)
                        ExportValidationSupport.Pin.from(input.required("consumerTestPin"))
                                .json(1048576, FaturasClienteContentFixture.budget());
        final var uppercaseUnit = (ObjectNode) uppercaseFullTest.required("unit");
        uppercaseUnit.put(
                "sha256",
                uppercaseUnit.required("sha256").textValue().toUpperCase(java.util.Locale.ROOT));
        final var uppercaseInput = input.deepCopy();
        uppercaseInput.set(
                "consumerTestPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "uppercase-fulltest.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(
                                        uppercaseFullTest))));
        final var uppercaseReceipt =
                execute(fixture, uppercaseInput, directory.resolve("uppercase-day-result.json"));
        assertTrue(
                uppercaseReceipt.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(uppercaseReceipt.path("periodComparisonComplete").booleanValue());
        assertEquals(input.required("codeUnitPin"), uppercaseReceipt.required("codeUnitPin"));
        assertThrows(IllegalArgumentException.class, () -> execute(fixture, input, output));
    }

    @Test
    void emptyDayRequiresClosedSameRunProofAndExactCurrentConsumerWithoutVacuousMapperFlags()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, true);
        final var proofDir = Files.createDirectory(directory.resolve("authored-empty-proof"));
        final var proof = emptyProof(input);
        final var proofPin = write(proofDir.resolve("result.json"), proof);
        final var closure =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1")
                        .put("success", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true)
                        .put("OriginalJobStarted", true)
                        .put("slotRetained", false)
                        .put("OriginalCalls", 1)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        final var closurePin = write(proofDir.resolve("parent-result.json"), closure);
        input.set("emptyDayProofPin", ExportValidationSupport.JSON.valueToTree(proofPin));
        input.set("emptyDayProofClosurePin", ExportValidationSupport.JSON.valueToTree(closurePin));
        final var receipt = execute(fixture, input, directory.resolve("empty-day.json"));
        assertTrue(receipt.path("dayContentEqualUnderCandidatePresentation").booleanValue());
        assertTrue(receipt.path("emptyDaySourceProofVerified").booleanValue());
        assertEquals(0, receipt.path("sourceDayRows").longValue());
        assertFalse(receipt.has("mapperQualified"));
        final var changed = input.deepCopy();
        changed.set(
                "consumerTestPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "different-test.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(
                                        ExportValidationSupport.Pin.from(
                                                        input.required("consumerTestPin"))
                                                .json(
                                                        1048576,
                                                        FaturasClienteContentFixture.budget())))));
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, changed, directory.resolve("different-consumer.json")));
        input.putNull("emptyDayProofPin");
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, input, directory.resolve("unproved-empty.json")));
    }

    @Test
    void immutableControlAndSourcePinsCannotBeReboundOrMixedWithEmptyProofAuthority()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, false);
        final var manifest =
                fixture.pin("control.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        input.put("day", "2026-10-14");
        final var clock = Clock.systemUTC();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FaturasClienteDayDiagnosticMain.execute(
                                input,
                                manifest,
                                directory.resolve("rebound.json"),
                                clock.instant(),
                                clock,
                                FaturasClienteContentFixture.budget()));
        assertFalse(Files.exists(directory.resolve("rebound.json")));
        input.put("day", "2026-10-13");
        input.set("emptyDayProofPin", input.required("catalogPin").deepCopy());
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, input, directory.resolve("mixed-authority.json")));
        input.putNull("emptyDayProofPin");
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("sourcePins"))
                .add(input.required("sourcePins").get(0).deepCopy());
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, input, directory.resolve("duplicate-source.json")));
    }

    @Test
    void wrongCliAndExtraInputFieldsRefuseBeforeAnyPrivateResultIsWritten() throws Exception {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                FaturasClienteDayDiagnosticMain.run(
                        new String[] {"capture"}, new PrintStream(out), new PrintStream(err)));
        assertEquals(0, out.size());
        assertTrue(err.toString(java.nio.charset.StandardCharsets.UTF_8).contains("USAGE"));
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, false).put("bucketIndex", 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, input, directory.resolve("extra.json")));
    }

    private ObjectNode input(final FaturasClienteContentFixture fixture, final boolean empty)
            throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_OFFLINE_DAY_DIAGNOSTIC_INPUT_V1")
                        .put("privateRoot", directory.toString())
                        .put("day", empty ? "2026-10-12" : "2026-10-13")
                        .putNull("emptyDayProofPin")
                        .putNull("emptyDayProofClosurePin");
        final var sources = input.putArray("sourcePins");
        if (!empty) {
            sources.add(
                    ExportValidationSupport.JSON.valueToTree(
                            fixture.pin("raw.jsonl", fixture.sourceBytes())));
        }
        input.set(
                "requestPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "request.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(fixture.request))));
        input.set(
                "workbookPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin("native.xlsx", fixture.workbookBytes())));
        input.set(
                "catalogPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "catalog.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(fixture.catalog))));
        final var unit =
                fixture.pin(
                        "authored-unit.json",
                        "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final var test =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("success", true)
                        .put("syntheticTestsPassed", true)
                        .put("originalClosedKnown", true)
                        .put("stateRestored", true);
        test.set("unit", ExportValidationSupport.JSON.valueToTree(unit));
        input.set(
                "consumerTestPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "authored-test.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(test))));
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(unit));
        return input;
    }

    private static ObjectNode emptyProof(final ObjectNode input) {
        // Authored metadata exercises the consumer boundary, never claims an actual acquisition.
        final var proof =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_EMPTY_DAY_SOURCE0_QUALIFICATION_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("entity", "FATURAS_POR_CLIENTE")
                        .put("reportId", 4924)
                        .put("periodStart", "2026-10-12")
                        .put("periodEnd", "2026-10-12");
        for (final String name :
                List.of(
                        "success",
                        "pinsVerifiedBeforeAndAfter",
                        "emptySourceDayQualified",
                        "actualTerminalEmptyArrayVerified",
                        "allOriginalResponseBodiesReadToEof",
                        "originalEmptyPayloadReadToEof",
                        "producerFlagsPreserved")) {
            proof.put(name, true);
        }
        for (final String name :
                List.of("historicalProducerFactsRewritten", "contentAccepted", "entityQualified")) {
            proof.put(name, false);
        }
        for (final String name :
                List.of(
                        "sourceRows",
                        "retainedBytes",
                        "currentMapperInvocations",
                        "rowTypesEvaluatedOnRows",
                        "Source",
                        "HTTP",
                        "SQL")) {
            proof.put(name, 0);
        }
        proof.set("consumerTestPin", input.required("consumerTestPin").deepCopy());
        proof.set("codeUnitPin", input.required("codeUnitPin").deepCopy());
        return proof;
    }

    private ObjectNode execute(
            final FaturasClienteContentFixture fixture, final ObjectNode input, final Path output)
            throws Exception {
        final var manifest =
                fixture.pin("control.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final var clock = Clock.systemUTC();
        return FaturasClienteDayDiagnosticMain.execute(
                input,
                manifest,
                output,
                clock.instant(),
                clock,
                FaturasClienteContentFixture.budget());
    }

    private static ExportValidationSupport.Pin write(final Path path, final ObjectNode value)
            throws Exception {
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(value);
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }
}

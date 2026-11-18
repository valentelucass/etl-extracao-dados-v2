package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesP3QualificationMainTest {
    @TempDir Path directory;

    private record Fixture(ObjectNode input, ExportValidationSupport.Pin pin) {}

    @Test
    void twentyOneArbitraryConsecutiveDaysIncludingLeapDayEmptyAndFullHavePrivatePinnedResults()
            throws Exception {
        final var fixture = fixture();
        final Path target = directory.resolve("qualification.json");
        final var receipt = execute(fixture, target);
        assertTrue(receipt.path("success").booleanValue());
        assertTrue(receipt.path("typesQualifiedAll21").booleanValue());
        assertTrue(receipt.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertEquals(21, receipt.path("captureCount").intValue());
        assertEquals(21, receipt.path("results").size());
        assertEquals(14, receipt.path("rows").intValue());
        assertEquals(0, receipt.path("rejectedRows").intValue());
        assertFalse(receipt.path("businessValuesPublished").booleanValue());
        final var proof = ExportValidationSupport.JSON.readTree(Files.readAllBytes(target));
        assertEquals("2028-02-20", proof.path("results").get(0).path("periodStart").asText());
        assertEquals("2028-03-11", proof.path("results").get(20).path("periodEnd").asText());
        assertTrue(proof.path("results").get(0).path("emptyPayloadWitnessVerified").booleanValue());
        assertTrue(proof.path("results").get(1).path("allRawRowsReadToEof").booleanValue());
        assertTrue(proof.path("results").get(1).has("requestPin"));
        assertEquals(
                ExportValidationSupport.sha256(Files.readAllBytes(target)),
                receipt.path("diagnosticSha256").asText());
        assertTrue(Files.isRegularFile(target.resolveSibling("qualification.json.receipt.json")));
        for (final String flag :
                List.of(
                        "sourceCompletenessProven",
                        "contentAccepted",
                        "entityQualified",
                        "mainResumeAllowed",
                        "productionAuthorized")) {
            assertTrue(receipt.path(flag).isBoolean());
            assertFalse(receipt.path(flag).booleanValue());
        }
    }

    @Test
    void exactlyTwentyOneEntriesAndExactManifestAndEntryFieldsAreRequired() throws Exception {
        final var original = fixture();
        for (final String mutation : List.of("missing", "extra", "entry-extra", "authority")) {
            final var input = original.input().deepCopy();
            final var captures = (ArrayNode) input.required("captures");
            if ("missing".equals(mutation)) {
                captures.remove(20);
            } else if ("extra".equals(mutation)) {
                captures.add(captures.get(20).deepCopy());
            } else if ("entry-extra".equals(mutation)) {
                ((ObjectNode) captures.get(0)).put("sourceComplete", true);
            } else {
                input.put("productionAuthorized", true);
            }
            final var changed = repin(input);
            assertThrows(IllegalArgumentException.class, () -> execute(changed, output()));
        }
    }

    @Test
    void duplicatedReorderedAndSkippedDailyDatesCannotPassConsecutiveScope() throws Exception {
        for (final String mutation : List.of("duplicate", "reordered", "skipped")) {
            final var fixture = fixture();
            final var captures = (ArrayNode) fixture.input().required("captures");
            if ("duplicate".equals(mutation)) {
                captures.set(1, captures.get(0).deepCopy());
            } else if ("reordered".equals(mutation)) {
                final JsonNode first = captures.get(0).deepCopy();
                captures.set(0, captures.get(1).deepCopy());
                captures.set(1, first);
            } else {
                final var request = CotacoesCaptureFixture.request("2028-02-22", "2028-02-22");
                ((ObjectNode) captures.get(1))
                        .set("requestPin", pinJson(CotacoesCaptureFixture.json(request)));
            }
            final var changed = repin(fixture.input());
            assertEquals(
                    "COTACOES_P3_BATCH_CONSECUTIVE_UNIQUE_DAYS",
                    assertThrows(IllegalArgumentException.class, () -> execute(changed, output()))
                            .getMessage());
        }
    }

    @Test
    void inputMustMatchItsPinnedManifestAndEveryRequestMustBeDailyCotacoes() throws Exception {
        final var fixture = fixture();
        fixture.input().put("privateRoot", directory.getParent().toString());
        assertEquals(
                "COTACOES_P3_BATCH_INPUT_PIN_BINDING",
                assertThrows(IllegalArgumentException.class, () -> execute(fixture, output()))
                        .getMessage());
        final var daily = fixture();
        final var request = CotacoesCaptureFixture.request("2028-02-20", "2028-02-21");
        entry(daily, 0).set("requestPin", pinJson(CotacoesCaptureFixture.json(request)));
        final var nondaily = repin(daily.input());
        assertEquals(
                "COTACOES_P3_BATCH_DAILY_REQUEST",
                assertThrows(IllegalArgumentException.class, () -> execute(nondaily, output()))
                        .getMessage());
        request.put("reportId", 6399);
        entry(daily, 0).set("requestPin", pinJson(CotacoesCaptureFixture.json(request)));
        final var foreign = repin(daily.input());
        assertThrows(IllegalArgumentException.class, () -> execute(foreign, output()));
    }

    @Test
    void badLastCaptureIsReadAndReportedWithoutApprovingItsTypesOrDiscardingEarlierResults()
            throws Exception {
        final var fixture = fixture();
        final var row =
                CotacoesCaptureFixture.row(21)
                        .put("requested_at", "2028-03-11T12:34:56-03:00")
                        .putNull("qoe_cor_document");
        entry(fixture, 20).set("payloadPin", pinJson(line(row)));
        final var changed = repin(fixture.input());
        final var receipt = execute(changed, output());
        assertTrue(receipt.path("success").booleanValue());
        assertFalse(receipt.path("typesQualifiedAll21").booleanValue());
        assertEquals(21, receipt.path("results").size());
        assertEquals(1, receipt.path("rejectedRows").intValue());
        assertFalse(receipt.path("results").get(20).path("typesQualified").booleanValue());
        assertTrue(receipt.path("results").get(1).path("typesQualified").booleanValue());
    }

    @Test
    void pinTamperBeforeAndAfterQualificationCannotPublishSuccessfulReceipt() throws Exception {
        final var before = fixture();
        final Path first = Path.of(entry(before, 1).path("payloadPin").path("path").asText());
        Files.writeString(first, "changed", StandardCharsets.UTF_8);
        final Path target = output();
        assertThrows(IllegalArgumentException.class, () -> execute(before, target));
        assertFalse(Files.exists(target));
        final var after = fixture();
        final Path payload = Path.of(entry(after, 1).path("payloadPin").path("path").asText());
        assertEquals(
                "EXPORT_PIN_CHANGED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        CotacoesP3QualificationMain.executeWithPostcheckProbe(
                                                after.input(),
                                                after.pin(),
                                                target,
                                                budget(),
                                                () -> {
                                                    try {
                                                        Files.writeString(
                                                                payload,
                                                                "changed",
                                                                StandardCharsets.UTF_8);
                                                    } catch (final java.io.IOException failure) {
                                                        throw new UncheckedIOException(failure);
                                                    }
                                                }))
                        .getMessage());
        assertFalse(Files.exists(target));
    }

    @Test
    void zeroByteRetentionNeedsActualEmptyArrayAndFullPayloadNeedsEofNotAValidPrefix()
            throws Exception {
        final var empty = fixture();
        entry(empty, 0)
                .set("terminalPin", pinJson("{\"data\":[]}".getBytes(StandardCharsets.UTF_8)));
        final var changedEmpty = repin(empty.input());
        assertEquals(
                "COTACOES_P3_ACTUAL_TERMINAL_EMPTY_ARRAY",
                assertThrows(IllegalArgumentException.class, () -> execute(changedEmpty, output()))
                        .getMessage());
        final var full = fixture();
        final Path source = Path.of(entry(full, 1).path("payloadPin").path("path").asText());
        final byte[] bytes = Files.readAllBytes(source);
        final var output = new ByteArrayOutputStream();
        output.write(bytes);
        output.write("{}\n".getBytes(StandardCharsets.UTF_8));
        entry(full, 1).set("payloadPin", pinJson(output.toByteArray()));
        final var changedFull = repin(full.input());
        assertThrows(IllegalArgumentException.class, () -> execute(changedFull, output()));
    }

    @Test
    void aggregateEightMibIsCheckedBeforeReadingDeclaredOversizeBusinessInputs() throws Exception {
        final var fixture = fixture();
        ((ObjectNode) entry(fixture, 0).required("payloadPin")).put("bytes", 8388608);
        final var changed = repin(fixture.input());
        assertEquals(
                "COTACOES_P3_BATCH_AGGREGATE_8MIB",
                assertThrows(IllegalArgumentException.class, () -> execute(changed, output()))
                        .getMessage());
    }

    @Test
    void freshPrivateOutputsCannotOverwriteEscapeOrCollideWithInputs() throws Exception {
        final var fixture = fixture();
        final Path existing = output();
        Files.writeString(existing, "existing", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> execute(fixture, existing));
        assertEquals("existing", Files.readString(existing, StandardCharsets.UTF_8));
        final Path receiptOnly = output();
        Files.writeString(
                receiptOnly.resolveSibling(receiptOnly.getFileName() + ".receipt.json"),
                "existing",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> execute(fixture, receiptOnly));
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(fixture, directory.getParent().resolve("outside.json")));
        assertThrows(IllegalArgumentException.class, () -> execute(fixture, fixture.pin().path()));
    }

    @Test
    void cliReturnsOnlyPublicMetadataAndSourceZeroReceiptWithoutBusinessValues() throws Exception {
        final var fixture = fixture();
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int code =
                CotacoesP3QualificationMain.run(
                        new String[] {
                            "qualify",
                            "--input",
                            fixture.pin().path().toString(),
                            "--sha256",
                            fixture.pin().sha256(),
                            "--output",
                            output().toString()
                        },
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(0, code);
        assertEquals("", err.toString(StandardCharsets.UTF_8));
        final String publicText = out.toString(StandardCharsets.UTF_8);
        assertFalse(publicText.contains(directory.toString()));
        assertFalse(publicText.contains("raw-qoe"));
        assertFalse(publicText.contains("sequence_code"));
        final var receipt = ExportValidationSupport.JSON.readTree(publicText);
        for (final String counter : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, receipt.path(counter).intValue());
        }
        assertTrue(receipt.path("diagnosticOnly").booleanValue());
        assertFalse(receipt.path("businessValuesPublished").booleanValue());
    }

    @Test
    void cliUsageAndMissingPrivateFileFailureAreSanitized() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
        final var stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        assertEquals(2, CotacoesP3QualificationMain.run(new String[] {"capture"}, stdout, stderr));
        assertEquals("COTACOES_P3_BATCH_USAGE", err.toString(StandardCharsets.UTF_8).strip());
        err.reset();
        assertEquals(
                2,
                CotacoesP3QualificationMain.run(
                        new String[] {
                            "qualify",
                            "--input",
                            directory.resolve("private-secret-missing.json").toString(),
                            "--sha256",
                            "0".repeat(64),
                            "--output",
                            output().toString()
                        },
                        stdout,
                        stderr));
        assertEquals(
                "COTACOES_P3_BATCH_REFUSED_PRIVATE_DETAILS_WITHHELD",
                err.toString(StandardCharsets.UTF_8).strip());
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    private Fixture fixture() throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_P3_BATCH_QUALIFICATION_INPUT_V1")
                        .put("privateRoot", directory.toString());
        final var captures = input.putArray("captures");
        for (int index = 0; index < 21; index++) {
            final String day = LocalDate.of(2028, 2, 20).plusDays(index).toString();
            final var request = CotacoesCaptureFixture.request(day, day);
            final var row =
                    CotacoesCaptureFixture.row(index + 1)
                            .put("requested_at", day + "T12:34:56-03:00")
                            .put("qoe_qes_fit_nse_issued_at", day);
            final var entry = captures.addObject();
            entry.set("requestPin", pinJson(CotacoesCaptureFixture.json(request)));
            entry.set("payloadPin", pinJson(index % 3 == 0 ? new byte[0] : line(row)));
            entry.set("terminalPin", pinJson(" [] \n".getBytes(StandardCharsets.UTF_8)));
        }
        return repin(input);
    }

    private Fixture repin(final ObjectNode input) throws Exception {
        return new Fixture(
                input,
                ExportValidationSupport.Pin.from(
                        pinJson(ExportValidationSupport.JSON.writeValueAsBytes(input))));
    }

    private ObjectNode pinJson(final byte[] bytes) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + ".json").toAbsolutePath();
        Files.write(path, bytes);
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("path", path.toString())
                .put("bytes", bytes.length)
                .put("sha256", ExportValidationSupport.sha256(bytes));
    }

    private static ObjectNode entry(final Fixture fixture, final int index) {
        return (ObjectNode) fixture.input().required("captures").get(index);
    }

    private ObjectNode execute(final Fixture fixture, final Path target) throws Exception {
        return CotacoesP3QualificationMain.execute(
                fixture.input(), fixture.pin(), target, budget());
    }

    private Path output() {
        return directory.resolve(UUID.randomUUID() + ".qualification.json");
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(150));
    }

    private static byte[] line(final JsonNode record) throws Exception {
        final var envelope =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("entity", "COTACOES")
                        .put("templateId", 6906);
        envelope.set("record", record);
        return (ExportValidationSupport.JSON.writeValueAsString(envelope) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}

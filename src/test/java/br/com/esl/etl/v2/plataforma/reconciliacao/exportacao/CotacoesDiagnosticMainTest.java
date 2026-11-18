package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CotacoesDiagnosticMainTest {
    @TempDir Path directory;

    record Input(
            CotacoesContentFixture fixture, ObjectNode value, ExportValidationSupport.Pin pin) {}

    @Test
    void observedCatalogDifferencePublishesPinnedPrivateDiagnosticAndBoundedSource0Receipt()
            throws Exception {
        final var input = input();
        final Path target = directory.resolve("diagnostic.json");
        final var receipt = execute(input, target);
        assertEquals(
                "COTACOES_OFFLINE_DIAGNOSTIC_EXECUTED_RECEIPT_V1", receipt.path("schema").asText());
        assertEquals(37, receipt.path("columns").intValue());
        assertEquals(11, receipt.path("totalColumns").intValue());
        assertTrue(receipt.path("diagnosticOnly").booleanValue());
        for (final String flag :
                List.of(
                        "trustedSourceCompleteness",
                        "contentAccepted",
                        "entityQualified",
                        "productionAuthorized",
                        "mainResumeAllowed",
                        "contentEqualUnderCandidatePresentation")) {
            assertTrue(receipt.path(flag).isBoolean());
            assertFalse(receipt.path(flag).booleanValue());
        }
        for (final String field : List.of("Source", "HTTP", "SQL")) {
            assertEquals(0, receipt.path(field).intValue());
        }
        assertFalse(receipt.has("fullValueProjection"));
        assertFalse(receipt.has("originalValueProjection"));
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(2097152, CotacoesContentFixture.budget());
        assertTrue(diagnostic.has("fullValueProjection"));
        assertTrue(diagnostic.has("originalValueProjection"));
        assertTrue(diagnostic.path("pinsVerifiedBeforeAndAfter").booleanValue());
        assertTrue(Files.isRegularFile(target.resolveSibling("diagnostic.json.receipt.json")));
        final var reloaded =
                ExportValidationSupport.Pin.from(receipt.required("receiptPin"))
                        .json(262144, CotacoesContentFixture.budget());
        assertEquals(
                ExportValidationSupport.JSON.readTree(
                        ExportValidationSupport.JSON.writeValueAsBytes(
                                receipt.required("diagnosticPin"))),
                reloaded.required("diagnosticPin"));
    }

    @Test
    void mainReturnsDifferenceCodeAndWritesNoPrivateValuesOrProjectionToStandardOutput()
            throws Exception {
        final var input = input();
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final int code =
                CotacoesDiagnosticMain.run(
                        new String[] {
                            "compare",
                            "--input",
                            input.pin().path().toString(),
                            "--sha256",
                            input.pin().sha256(),
                            "--output",
                            directory.resolve("cli.json").toString()
                        },
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(3, code);
        final var receipt = ExportValidationSupport.JSON.readTree(out.toByteArray());
        assertEquals("PENDING_DIFFERENCE_INVESTIGATION", receipt.path("status").asText());
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("authored-"));
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("columnBags"));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void existingOutputAndReceiptAreNeverReplacedAndOutsidePrivateRootRefuses() throws Exception {
        final var input = input();
        final Path target = directory.resolve("existing.json");
        Files.writeString(target, "authored-existing", StandardCharsets.UTF_8);
        assertEquals(
                "COTACOES_FRESH_PRIVATE_DIAGNOSTIC_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> execute(input, target))
                        .getMessage());
        assertEquals("authored-existing", Files.readString(target, StandardCharsets.UTF_8));
        final Path receiptOnly = directory.resolve("receipt-only.json");
        Files.writeString(
                receiptOnly.resolveSibling("receipt-only.json.receipt.json"),
                "existing",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> execute(input, receiptOnly));
        final Path outside = directory.getParent().resolve("outside-cotacoes-diagnostic.json");
        assertThrows(IllegalArgumentException.class, () -> execute(input, outside));
        assertFalse(Files.exists(receiptOnly));
    }

    @Test
    void inputSchemaExtraAuthorityAndAlteredPinnedInputAreRefusedBeforePublishing()
            throws Exception {
        final var input = input();
        final Path target = directory.resolve("not-published.json");
        input.value().put("sourceCompletenessProven", true);
        assertEquals(
                "COTACOES_DIAGNOSTIC_INPUT_SCOPE",
                assertThrows(IllegalArgumentException.class, () -> execute(input, target))
                        .getMessage());
        input.value().remove("sourceCompletenessProven");
        input.value().put("privateRoot", directory.getParent().toString());
        assertEquals(
                "COTACOES_DIAGNOSTIC_INPUT_PIN_BINDING",
                assertThrows(IllegalArgumentException.class, () -> execute(input, target))
                        .getMessage());
        assertFalse(Files.exists(target));
    }

    @Test
    void changedSourcePinAndBadRequestNeverProduceAReceipt() throws Exception {
        final var input = input();
        final var source = ExportValidationSupport.Pin.from(input.value().required("sourcePin"));
        Files.writeString(source.path(), "tampered", StandardCharsets.UTF_8);
        final Path target = directory.resolve("tampered.json");
        assertEquals(
                "EXPORT_PIN_CHANGED",
                assertThrows(IllegalArgumentException.class, () -> execute(input, target))
                        .getMessage());
        assertFalse(Files.exists(target));
        final var other = input();
        other.fixture().request.put("reportId", 6399);
        final var request =
                other.fixture()
                        .pin(
                                "wrong-request.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(
                                        other.fixture().request));
        other.value().set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        final var modified =
                new Input(
                        other.fixture(),
                        other.value(),
                        other.fixture()
                                .pin(
                                        "wrong-input.json",
                                        ExportValidationSupport.JSON.writeValueAsBytes(
                                                other.value())));
        assertThrows(
                IllegalArgumentException.class,
                () -> execute(modified, directory.resolve("wrong-request-result.json")));
    }

    @Test
    void invalidCliAndPrivateExceptionDetailsStaySanitized() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
        final var stderr = new PrintStream(err, true, StandardCharsets.UTF_8);
        assertEquals(2, CotacoesDiagnosticMain.run(new String[] {"capture"}, stdout, stderr));
        assertEquals("COTACOES_DIAGNOSTIC_USAGE", err.toString(StandardCharsets.UTF_8).strip());
        err.reset();
        assertEquals(
                2,
                CotacoesDiagnosticMain.run(
                        new String[] {
                            "compare",
                            "--input",
                            directory.resolve("private-missing.json").toString(),
                            "--sha256",
                            "0".repeat(64),
                            "--output",
                            directory.resolve("result.json").toString()
                        },
                        stdout,
                        stderr));
        assertEquals(
                "COTACOES_DIAGNOSTIC_REFUSED_PRIVATE_DETAILS_WITHHELD",
                err.toString(StandardCharsets.UTF_8).strip());
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    private Input input() throws Exception {
        final var fixture = new CotacoesContentFixture(directory, 1);
        // Real catalogue maps source NULL to absence; an observed nonempty Native label must still
        // differ.
        fixture.records.get(0).putNull("qoe_qes_status");
        fixture.nativeRows.get(0).put(16, CotacoesContentFixture.text("Pendente"));
        final var pins = fixture.pins();
        final var request =
                fixture.pin(
                        "request.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.request));
        final var value =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "COTACOES_OFFLINE_DIAGNOSTIC_INPUT_V1")
                        .put("privateRoot", directory.toString());
        value.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        value.set("sourcePin", ExportValidationSupport.JSON.valueToTree(pins.source()));
        value.set("workbookPin", ExportValidationSupport.JSON.valueToTree(pins.workbook()));
        return new Input(
                fixture,
                value,
                fixture.pin("input.json", ExportValidationSupport.JSON.writeValueAsBytes(value)));
    }

    private ObjectNode execute(final Input input, final Path target) throws Exception {
        final Instant now = Instant.parse("2026-11-07T03:00:00Z");
        return CotacoesDiagnosticMain.execute(
                input.value(),
                input.pin(),
                target,
                now,
                Clock.fixed(now, ZoneOffset.UTC),
                CotacoesContentFixture.budget());
    }
}

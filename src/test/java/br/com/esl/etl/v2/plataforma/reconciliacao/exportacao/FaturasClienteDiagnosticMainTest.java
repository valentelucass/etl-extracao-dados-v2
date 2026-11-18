package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FaturasClienteDiagnosticMainTest {
    @TempDir Path directory;

    private ObjectNode input(
            final FaturasClienteContentFixture fixture,
            final FaturasClienteContentFixture.Pins pins) {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_OFFLINE_DIAGNOSTIC_INPUT_V1")
                        .put("privateRoot", directory.toString());
        input.set("sourcePin", ExportValidationSupport.JSON.valueToTree(pins.source()));
        input.set("workbookPin", ExportValidationSupport.JSON.valueToTree(pins.workbook()));
        input.set("catalogPin", ExportValidationSupport.JSON.valueToTree(pins.catalog()));
        return input;
    }

    @Test
    void writesPrivateFullDiagnosticsAndOnlyBoundedSource0ReceiptWithFalseAuthority()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, fixture.pins());
        final var request =
                fixture.pin(
                        "request.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.request));
        input.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        final var manifest =
                fixture.pin("input.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final var clock = Clock.systemUTC();
        final var receipt =
                FaturasClienteDiagnosticMain.execute(
                        input,
                        manifest,
                        directory.resolve("diagnostic.json"),
                        clock.instant(),
                        clock,
                        FaturasClienteContentFixture.budget());
        assertEquals(
                "FATURAS_CLIENTE_OFFLINE_DIAGNOSTIC_EXECUTED_RECEIPT_V1",
                receipt.path("schema").textValue());
        assertTrue(receipt.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(52, receipt.path("columns").intValue());
        assertEquals(4, receipt.path("totalColumns").intValue());
        assertEquals(0, receipt.path("HTTP").intValue());
        assertEquals(0, receipt.path("Source").intValue());
        assertFalse(receipt.path("entityQualified").booleanValue());
        assertFalse(receipt.has("fullValueProjection"));
        assertTrue(receipt.has("diagnosticPin") && receipt.has("receiptPin"));
    }

    @Test
    void manifestMutationCannotRebindTheActualExecutedInputOrOverwritePrivateOutput()
            throws Exception {
        final var fixture = new FaturasClienteContentFixture(directory, 1);
        final var input = input(fixture, fixture.pins());
        final var request =
                fixture.pin(
                        "request.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.request));
        input.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        final var manifest =
                fixture.pin("input.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        input.put("privateRoot", directory.resolve("invented").toString());
        final var clock = Clock.systemUTC();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FaturasClienteDiagnosticMain.execute(
                                input,
                                manifest,
                                directory.resolve("diagnostic.json"),
                                clock.instant(),
                                clock,
                                FaturasClienteContentFixture.budget()));
        assertFalse(java.nio.file.Files.exists(directory.resolve("diagnostic.json")));
    }

    @Test
    void wrongCliVerbRefusesBeforeReadingAnyBusinessPath() {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                FaturasClienteDiagnosticMain.run(
                        new String[] {"capture"}, new PrintStream(out), new PrintStream(err)));
        assertEquals(0, out.size());
        assertTrue(err.toString(java.nio.charset.StandardCharsets.UTF_8).contains("USAGE"));
    }
}

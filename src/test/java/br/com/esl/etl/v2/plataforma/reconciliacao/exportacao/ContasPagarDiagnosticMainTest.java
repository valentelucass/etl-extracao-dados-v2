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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContasPagarDiagnosticMainTest {
    @TempDir Path directory;

    @Test
    void mainReadsThePinnedAuthoredCatalogAndPrintsOnlyMetadataWhileValuesStayInTheDiagnostic()
            throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        final var input = input(fixture);
        final var pin =
                fixture.pin("manifest.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final Path output =
                directory.resolve(UUID.randomUUID() + "-private-diagnostic.json").toAbsolutePath();
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                0,
                ContasPagarDiagnosticMain.run(
                        new String[] {
                            "compare",
                            "--input",
                            pin.path().toString(),
                            "--sha256",
                            pin.sha256(),
                            "--output",
                            output.toString()
                        },
                        new PrintStream(out),
                        new PrintStream(err)));
        assertEquals("", err.toString());
        assertFalse(out.toString().contains("Authored pending"));
        assertFalse(out.toString().contains("Authored A"));
        final var receipt = ExportValidationSupport.JSON.readTree(out.toByteArray());
        assertEquals("AUTHORED_SYNTHETIC", receipt.path("catalogNature").textValue());
        assertEquals(28, receipt.path("columns").intValue());
        assertEquals(7, receipt.path("totalColumns").intValue());
        assertEquals(0, receipt.path("Source").intValue());
        assertEquals(0, receipt.path("HTTP").intValue());
        assertFalse(receipt.path("contentAccepted").booleanValue());
        assertTrue(receipt.path("catalogPin").isObject());
        final var diagnostic = ExportValidationSupport.JSON.readTree(Files.readAllBytes(output));
        assertTrue(diagnostic.path("comparison").path("fullTuplesEqual").booleanValue());
        final var apiProof = diagnostic.path("fullValueProjection").path("api");
        assertTrue(apiProof.path("completeAllValues").booleanValue());
        assertEquals(28, apiProof.path("columnBags").size());
        assertEquals(apiProof, diagnostic.path("fullValueProjection").path("ui"));
    }

    @Test
    void aMissingCatalogPinOrExtraAuthorityCannotInvokeAComparison() throws Exception {
        for (final String mutation : new String[] {"missing", "authority"}) {
            final var fixture = new ContasPagarContentFixture(directory, 1);
            final var input = input(fixture);
            if ("missing".equals(mutation)) {
                input.remove("catalogPin");
            } else {
                input.put("Source", 1);
            }
            final var pin =
                    fixture.pin(
                            "manifest.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
            final var out = new ByteArrayOutputStream();
            final var err = new ByteArrayOutputStream();
            final Path output =
                    directory.resolve(UUID.randomUUID() + "-diagnostic.json").toAbsolutePath();
            assertEquals(
                    2,
                    ContasPagarDiagnosticMain.run(
                            new String[] {
                                "compare",
                                "--input",
                                pin.path().toString(),
                                "--sha256",
                                pin.sha256(),
                                "--output",
                                output.toString()
                            },
                            new PrintStream(out),
                            new PrintStream(err)));
            assertEquals("", out.toString());
            assertFalse(Files.exists(output));
        }
    }

    @Test
    void diagnosticsDoNotOverwriteAnExistingResultOrAcceptAPathOutsideThePrivateRoot()
            throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        final var input = input(fixture);
        final var pin =
                fixture.pin("manifest.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final Path existing = directory.resolve("existing.json").toAbsolutePath();
        Files.writeString(existing, "retained");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContasPagarDiagnosticMain.execute(
                                input,
                                pin,
                                existing,
                                java.time.Instant.now(),
                                java.time.Clock.systemUTC(),
                                ContasPagarContentFixture.budget()));
        assertEquals("retained", Files.readString(existing));
        final Path outside =
                directory.getParent().resolve(UUID.randomUUID() + "-outside.json").toAbsolutePath();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContasPagarDiagnosticMain.execute(
                                input,
                                pin,
                                outside,
                                java.time.Instant.now(),
                                java.time.Clock.systemUTC(),
                                ContasPagarContentFixture.budget()));
        assertFalse(Files.exists(outside));
    }

    @Test
    void aRealDifferencePublishesADiagnosticWithNonzeroStatusAndWithoutClaimingAcceptance()
            throws Exception {
        final var fixture = new ContasPagarContentFixture(directory, 1);
        fixture.nativeRows.get(0).put(22, ContasPagarContentFixture.text("Authored other"));
        final var input = input(fixture);
        final var pin =
                fixture.pin("manifest.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final Path output =
                directory.resolve(UUID.randomUUID() + "-diagnostic.json").toAbsolutePath();
        final var out = new ByteArrayOutputStream();
        assertEquals(
                3,
                ContasPagarDiagnosticMain.run(
                        new String[] {
                            "compare",
                            "--input",
                            pin.path().toString(),
                            "--sha256",
                            pin.sha256(),
                            "--output",
                            output.toString()
                        },
                        new PrintStream(out),
                        new PrintStream(new ByteArrayOutputStream())));
        final var receipt = ExportValidationSupport.JSON.readTree(out.toByteArray());
        assertFalse(receipt.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(receipt.path("entityQualified").booleanValue());
        assertTrue(Files.exists(output));
    }

    private static ObjectNode input(final ContasPagarContentFixture fixture) throws Exception {
        final var pins = fixture.pins();
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "CONTAS_A_PAGAR_OFFLINE_DIAGNOSTIC_INPUT_V1")
                        .put(
                                "privateRoot",
                                fixture.directory.toAbsolutePath().normalize().toString());
        input.set(
                "requestPin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                "request.json",
                                ExportValidationSupport.JSON.writeValueAsBytes(fixture.request))));
        input.set("sourcePin", ExportValidationSupport.JSON.valueToTree(pins.source()));
        input.set("workbookPin", ExportValidationSupport.JSON.valueToTree(pins.workbook()));
        input.set("catalogPin", ExportValidationSupport.JSON.valueToTree(pins.catalog()));
        return input;
    }
}

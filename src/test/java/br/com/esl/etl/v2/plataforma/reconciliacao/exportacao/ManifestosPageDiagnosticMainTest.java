package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosPageDiagnosticMainTest {
    @TempDir Path directory;

    @Test
    void pageModeWritesPinnedPrivateEvidenceAndPrintsOnlyItsSummaryAndReceipt() throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 1);
        final var capture = fixture.pin(fixture.capture(1, fixture.data.records));
        final var output = fixture.privateRoot.resolve("page.json");
        final var receipt = run(fixture, "page", fixture.pageInput(capture), output, 0);
        assertTrue(receipt.path("summary").path("pageValuesEqual").booleanValue());
        assertFalse(receipt.has("privateRootEvidence"));
        assertFalse(receipt.has("aggregateShards"));
        final var result =
                ExportValidationSupport.Pin.from(receipt.required("resultPin"))
                        .json(8388608, ManifestosContentFixture.budget());
        assertEquals(1, result.path("privateRootEvidence").path("sourceRoots").size());
        assertTrue(result.has("executionBinding"));
        assertEquals(0, receipt.path("HTTP").intValue());
        assertFalse(receipt.path("contentAccepted").booleanValue());
    }

    @Test
    void dayModeWritesClosedEvidenceWithoutSendingHttpOrPromotingEntityAcceptance()
            throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 2);
        final var receipt =
                run(
                        fixture,
                        "day",
                        fixture.completeInput(),
                        fixture.privateRoot.resolve("day.json"),
                        0);
        assertTrue(receipt.path("summary").path("comparisonPassed").booleanValue());
        assertEquals(50, receipt.path("summary").path("numericTotalsEqual").intValue());
        assertEquals(0, receipt.path("Source").intValue());
        assertEquals(0, receipt.path("SQL").intValue());
        assertFalse(receipt.path("entityQualified").booleanValue());
    }

    @Test
    void diagnosticRefusesOutputOutsideDeclaredPrivateRootAndNeverOverwritesAnExistingResult()
            throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 1);
        final var input = fixture.pageInput(fixture.pin(fixture.capture(1, fixture.data.records)));
        final var outside = directory.resolve("outside.json").toAbsolutePath();
        run(fixture, "page", input, outside, 2);
        assertFalse(Files.exists(outside));
        final var output = fixture.privateRoot.resolve("page.json");
        run(fixture, "page", input, output, 0);
        final var hash = ExportValidationSupport.sha256(Files.readAllBytes(output));
        run(fixture, "page", input, output, 2);
        assertEquals(hash, ExportValidationSupport.sha256(Files.readAllBytes(output)));
    }

    @Test
    void failedPageComparisonIsPrivateExitThreeAndUnclosedCaptureProducesNoResult()
            throws Exception {
        final var fixture = new ManifestosDayFixture(directory, 1);
        fixture.data.nativeRows.get(0).put(91, ManifestosContentFixture.text("Sim"));
        fixture.refreshWorkbook();
        final var capture = fixture.pin(fixture.capture(1, fixture.data.records));
        final var receipt =
                run(
                        fixture,
                        "page",
                        fixture.pageInput(capture),
                        fixture.privateRoot.resolve("different.json"),
                        3);
        assertFalse(receipt.path("summary").path("pageValuesEqual").booleanValue());
        final var input = fixture.pageInput(capture);
        input.set(
                "captureClosurePin",
                ExportValidationSupport.JSON.valueToTree(
                        fixture.pin(
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("success", true)
                                        .put("originalClosedKnown", false)
                                        .put("stateRestored", true)
                                        .put("slotRetained", false))));
        final var output = fixture.privateRoot.resolve("refused.json");
        run(fixture, "page", input, output, 2);
        assertFalse(Files.exists(output));
    }

    private static ObjectNode run(
            final ManifestosDayFixture fixture,
            final String mode,
            final ObjectNode input,
            final Path output,
            final int status)
            throws Exception {
        final var pin = fixture.pin(input);
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                status,
                ManifestosPageDiagnosticMain.run(
                        new String[] {
                            mode,
                            "--input",
                            pin.path().toString(),
                            "--sha256",
                            pin.sha256(),
                            "--output",
                            output.toString()
                        },
                        new PrintStream(out),
                        new PrintStream(err)),
                err.toString());
        if (status == 2) {
            assertEquals("", out.toString());
            return ExportValidationSupport.JSON.createObjectNode();
        }
        assertEquals("", err.toString());
        return (ObjectNode) ExportValidationSupport.JSON.readTree(out.toByteArray());
    }
}

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosOfflineDiagnosticTest {
    @TempDir Path directory;
    private static final Instant START = Instant.parse("2026-11-06T13:04:21Z");
    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    @Test
    void receiptKeepsValuesPrivateAndPersistsFullComparisonWithoutQualification() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 2);
        final var input = input(fixture);
        final var target = directory.resolve("diagnostic.json");
        final var receipt = execute(fixture, input, target);
        assertTrue(receipt.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertFalse(receipt.path("contentAccepted").booleanValue());
        assertFalse(receipt.path("productionAuthorized").booleanValue());
        assertEquals(91, receipt.required("columns").intValue());
        assertEquals(50, receipt.required("totalColumns").intValue());
        assertFalse(receipt.has("fullValueProjection"));
        assertFalse(receipt.has("columnDiagnostics"));
        assertFalse(receipt.toString().contains("authored-"));
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(2097152, ManifestosContentFixture.budget());
        assertTrue(diagnostic.has("fullValueProjection"));
        assertTrue(diagnostic.has("originalValueProjection"));
        assertEquals(91, diagnostic.required("columnDiagnostics").size());
        ExportValidationSupport.Pin.from(receipt.required("receiptPin"))
                .read(262144, ManifestosContentFixture.budget());
    }

    @Test
    void mobileDifferenceIsPublishedAsPendingAndNeverAccepted() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 2);
        fixture.nativeRows.get(1).put(91, ManifestosContentFixture.text("Sim"));
        final var result = execute(fixture, input(fixture), directory.resolve("difference.json"));
        assertFalse(result.path("contentEqualUnderCandidatePresentation").booleanValue());
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR",
                result.required("status").textValue());
        assertFalse(result.path("entityQualified").booleanValue());
        assertFalse(result.path("mainResumeAllowed").booleanValue());
    }

    @Test
    void changedSourcePinProducesNoDiagnosticOrReceipt() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final var input = input(fixture);
        Files.writeString(
                ExportValidationSupport.Pin.from(input.required("sourcePin")).path(), "changed\n");
        final var target = directory.resolve("refused.json");
        assertThrows(Exception.class, () -> execute(fixture, input, target));
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(target.resolveSibling("refused.json.receipt.json")));
    }

    @Test
    void existingReceiptPreventsAnyDiagnosticPublication() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final var input = input(fixture);
        final var target = directory.resolve("occupied.json");
        final var receipt = target.resolveSibling("occupied.json.receipt.json");
        Files.writeString(receipt, "existing receipt");
        assertThrows(Exception.class, () -> execute(fixture, input, target));
        assertFalse(Files.exists(target));
        assertEquals("existing receipt", Files.readString(receipt));
    }

    @Test
    void undeclaredInputCannotAddAuthorityOrPublishFiles() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final var input = input(fixture).put("productionAuthorized", true);
        final var target = directory.resolve("extra.json");
        assertThrows(Exception.class, () -> execute(fixture, input, target));
        assertFalse(Files.exists(target));
    }

    private ObjectNode input(final ManifestosContentFixture fixture) throws Exception {
        final var pins = fixture.pins();
        final var request =
                fixture.pin(
                        "request.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.request));
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_OFFLINE_DIAGNOSTIC_INPUT_V1");
        input.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        input.set("sourcePin", ExportValidationSupport.JSON.valueToTree(pins.source()));
        input.set("workbookPin", ExportValidationSupport.JSON.valueToTree(pins.workbook()));
        return input;
    }

    private ObjectNode execute(
            final ManifestosContentFixture fixture, final ObjectNode input, final Path target)
            throws Exception {
        final var manifest =
                fixture.pin("input.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        return ManifestosOfflineDiagnostic.execute(
                input, manifest, target, START, CLOCK, ManifestosContentFixture.budget());
    }
}

package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** DRAFT: P2 is not installed or selected until Root's complete-profile review. */
class FaturasClienteP2WireContractTest {
    @TempDir Path directory;

    @Test
    void archivedP1ResourceAndShapeRemainLiteralWhileP2AddsOnlyReviewedTypeUnions()
            throws Exception {
        assertEquals(
                "4bdc78464659c30367ba8d97ed7e9a5f88824503beaa05aaff621c5e97354405",
                FaturasClienteObservedWire52.SHA256);
        try (var source =
                FaturasClienteP2WireContractTest.class.getResourceAsStream(
                        "/" + FaturasClienteObservedWire52.RESOURCE)) {
            assertEquals(
                    FaturasClienteObservedWire52.SHA256,
                    ExportValidationSupport.sha256(source.readAllBytes()));
        }
        final var archived = FaturasClienteObservedWire52.shape();
        final var current = FaturasClienteObservedWire52.observed20261108P2();
        assertEquals(archived.fields(), current.fields());
        assertEquals(archived.filters(), current.filters());
        final Set<String> changed =
                Set.of(
                        "/comments",
                        "/fit_ant_ils_atn_transaction_date",
                        "/fit_dyn_drt_nickname",
                        "/fit_nse_number",
                        "/fit_pyr_cor_billing_cycle",
                        "/fit_pyr_cor_billing_due_in_days",
                        "/fit_sps_slr_psn_name",
                        "/reference_number");
        for (final String path : archived.paths().keySet()) {
            if (!changed.contains(path)) {
                assertEquals(archived.paths().get(path), current.paths().get(path));
            } else {
                final var before = archived.paths().get(path).deepCopy();
                final var after = current.paths().get(path).deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) before).remove("wireTypes");
                ((com.fasterxml.jackson.databind.node.ObjectNode) after).remove("wireTypes");
                ((com.fasterxml.jackson.databind.node.ObjectNode) before).remove("unknownNonNull");
                ((com.fasterxml.jackson.databind.node.ObjectNode) after).remove("unknownNonNull");
                assertEquals(before, after);
            }
        }
    }

    @Test
    void reviewedObservedTypesQualifyOnlyUnderP2WithoutInventingOtherTypesOrMissingPaths()
            throws Exception {
        final var archived = FaturasClienteObservedWire52.shape();
        final var current = FaturasClienteObservedWire52.observed20261108P2();
        final var row = FaturasClienteCaptureFixture.row(1);
        row.putNull("comments");
        row.put("fit_ant_ils_atn_transaction_date", "2026-10-13");
        row.putNull("fit_dyn_drt_nickname");
        row.put("fit_nse_number", 7);
        row.putNull("fit_pyr_cor_billing_due_in_days");
        row.putNull("reference_number");
        row.putNull("fit_sps_slr_psn_name");
        assertFalse(FaturasClienteObservedWire52.qualified(row, archived, new TreeMap<>()));
        assertTrue(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        assertTrue(new FaturaClienteDataExportMapper().map(1, envelope).valid());
        for (final String name :
                Set.of(
                        "comments",
                        "fit_ant_ils_atn_transaction_date",
                        "fit_dyn_drt_nickname",
                        "fit_nse_number",
                        "fit_pyr_cor_billing_cycle",
                        "fit_pyr_cor_billing_due_in_days",
                        "fit_sps_slr_psn_name",
                        "reference_number")) {
            final var invalid = row.deepCopy();
            invalid.put(name, true);
            final Map<String, Long> reasons = new TreeMap<>();
            assertFalse(FaturasClienteObservedWire52.qualified(invalid, current, reasons));
            assertEquals(1L, reasons.get("/" + name + ":BOOLEAN"));
        }
        row.remove("comments");
        assertFalse(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
    }

    @Test
    void previouslyUnknownNfseIntegerIsCurrentEvidenceAndStringRemainsUnobserved()
            throws Exception {
        final var row = FaturasClienteCaptureFixture.row(1);
        row.put("fit_nse_number", 7);
        final var historical = new TreeMap<String, Long>();
        assertFalse(
                FaturasClienteObservedWire52.qualified(
                        row, FaturasClienteObservedWire52.shape(), historical));
        assertEquals(1L, historical.get("/fit_nse_number:INTEGER"));
        assertTrue(
                FaturasClienteObservedWire52.qualified(
                        row, FaturasClienteObservedWire52.observed20261108P2(), new TreeMap<>()));
        row.put("fit_nse_number", "7");
        final var stillUnobserved = new TreeMap<String, Long>();
        assertFalse(
                FaturasClienteObservedWire52.qualified(
                        row, FaturasClienteObservedWire52.observed20261108P2(), stillUnobserved));
        assertEquals(1L, stillUnobserved.get("/fit_nse_number:STRING"));
    }

    @Test
    void reviewedBillingCycleNullIsP2OnlyWhileStringRawAndUnobservedTypesStayStrict()
            throws Exception {
        final var row = FaturasClienteCaptureFixture.row(1);
        final var archived = FaturasClienteObservedWire52.shape();
        final var current = FaturasClienteObservedWire52.observed20261108P2();
        row.putNull("fit_pyr_cor_billing_cycle");
        final var historical = new TreeMap<String, Long>();
        assertFalse(FaturasClienteObservedWire52.qualified(row, archived, historical));
        assertEquals(1L, historical.get("/fit_pyr_cor_billing_cycle:NULL"));
        assertTrue(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        final var nullable = new FaturaClienteDataExportMapper().map(1, envelope);
        assertTrue(nullable.valid());
        assertEquals("NULL", nullable.fitPyrCorBillingCycle().presence().name());
        assertNull(nullable.fitPyrCorBillingCycle().raw());
        assertNull(nullable.fitPyrCorBillingCycle().value());
        row.put("fit_pyr_cor_billing_cycle", "  SYNTHETIC-cycle  ");
        assertTrue(FaturasClienteObservedWire52.qualified(row, archived, new TreeMap<>()));
        assertTrue(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        final var literal = new FaturaClienteDataExportMapper().map(1, envelope);
        assertTrue(literal.valid());
        assertEquals("  SYNTHETIC-cycle  ", literal.fitPyrCorBillingCycle().raw());
        assertEquals("  SYNTHETIC-cycle  ", literal.fitPyrCorBillingCycle().value());
        row.put("fit_pyr_cor_billing_cycle", true);
        assertFalse(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        row.put("fit_pyr_cor_billing_cycle", 1);
        assertFalse(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        row.putArray("fit_pyr_cor_billing_cycle").add("synthetic");
        assertFalse(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
        row.remove("fit_pyr_cor_billing_cycle");
        assertFalse(FaturasClienteObservedWire52.qualified(row, current, new TreeMap<>()));
    }

    @Test
    void currentP2CaptureAndRequalifierReadAllFiftyItemsWithoutReplacingLiteralRaw()
            throws Exception {
        try (var fixture = new FaturasClienteCaptureFixture(directory, false)) {
            final var row = FaturasClienteCaptureFixture.row(1);
            row.putNull("comments");
            row.put("fit_ant_ils_atn_transaction_date", "2026-10-13");
            row.putNull("fit_dyn_drt_nickname");
            row.put("fit_nse_number", 7);
            row.putNull("fit_pyr_cor_billing_due_in_days");
            row.putNull("reference_number");
            row.putNull("fit_sps_slr_psn_name");
            for (final String name : Set.of("invoices_mapping", "fit_fte_invoices_order_number")) {
                final var array = row.putArray(name);
                for (int index = 0; index < 50; index++) {
                    array.add(index % 2 == 0 ? "B" : "A");
                }
            }
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array(row)));
            fixture.port.pages.add(
                    FaturasClienteCaptureFixture.json(FaturasClienteCaptureFixture.array()));
            final var captured = fixture.capture(2, 100, 10);
            assertEquals("FATURAS_CLIENTE_SOURCE_COMPLETE", captured.path("status").textValue());
            assertTrue(captured.path("rowTypesQualified").booleanValue());
            assertTrue(captured.path("mapperQualified").booleanValue());
            final var source = ExportValidationSupport.Pin.from(captured.required("dataPin"));
            final byte[] original = Files.readAllBytes(source.path());
            final var qualified =
                    FaturasClienteRawMapperQualifier.qualify(
                            fixture.pin(fixture.request),
                            source,
                            new ExportValidationSupport.Budget(Duration.ofSeconds(80)));
            assertTrue(qualified.path("success").booleanValue());
            assertEquals(1, qualified.path("rows").intValue());
            assertEquals(0, qualified.path("rejectedRows").intValue());
            assertEquals(
                    FaturasClienteObservedWire52.P2_SHA256,
                    qualified.path("sourceWireContractSha256").textValue());
            assertArrayEquals(original, Files.readAllBytes(source.path()));
        }
    }
}

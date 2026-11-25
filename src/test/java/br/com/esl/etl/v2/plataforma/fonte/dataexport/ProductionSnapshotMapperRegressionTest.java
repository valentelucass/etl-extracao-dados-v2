package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.SnapshotMapperCatalog;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductionSnapshotMapperRegressionTest {
    @Test
    void costCenterAllocationsRetainAllDigitsFromTextAndNumericWire() throws Exception {
        for (final String literal :
                List.of("12.345678901234567", "-98.765432109876543", "0." + "1".repeat(38))) {
            for (final String wire : List.of("\"" + literal + "\"", literal)) {
                final var row = CurlSnapshotWire.JSON.createObjectNode();
                row.set("ant_ces_value", CurlSnapshotWire.JSON.readTree(wire));
                final var observation = payable(row);
                assertTrue(observation.valid());
                assertEquals(
                        0, new BigDecimal(literal).compareTo(observation.antCesValue().value()));
                assertEquals(
                        row.required("ant_ces_value").asText(), observation.antCesValue().raw());
            }
        }
        final var row =
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("ant_ces_value", "12.3456789012345670");
        assertEquals("12.3456789012345670", payable(row).antCesValue().raw());
        assertEquals(new BigDecimal("12.3456789012345670"), payable(row).antCesValue().value());
    }

    @Test
    void invalidAllocationsAndOtherMonetaryFieldsStillRejectTheRow() {
        final var row = CurlSnapshotWire.JSON.createObjectNode();
        for (final String invalid :
                List.of(
                        "1.2x",
                        "1e-15",
                        "1,25",
                        "1".repeat(21) + ".123456789012345",
                        "12." + "1".repeat(37),
                        "0." + "0".repeat(38) + "1")) {
            final var field = payable(row.put("ant_ces_value", invalid)).antCesValue();
            assertFalse(field.valid());
            assertEquals("INVALID_ANT_CES_VALUE", field.issue());
            assertEquals(invalid, field.raw());
        }
        row.put("ant_ces_value", "12.345678901234567").put("paid_value", "12.345678901234567");
        final var observation = payable(row);
        assertTrue(observation.antCesValue().valid());
        assertFalse(observation.valid());
        assertEquals("INVALID_PAID_VALUE", observation.paidValue().issue());
    }

    private static ContaPagarObservation payable(final ObjectNode row) {
        return (ContaPagarObservation)
                SnapshotMapperCatalog.map(
                        ReportSnapshotTemplate.CONTAS_A_PAGAR,
                        new DataExportRecordInput(row, Optional.empty()));
    }

    @Test
    void aCustomerInvoiceKeepsAll282NotesAndOrderNumbers() {
        final var row = CurlSnapshotWire.JSON.createObjectNode();
        final var invoices = row.putArray("invoices_mapping");
        final var orders = row.putArray("fit_fte_invoices_order_number");
        for (int index = 0; index < 282; index++) {
            invoices.add(index % 2 == 0 ? "000001" : "000002");
            orders.add(index % 2 == 0 ? "0000000001" : "0000000002");
        }
        final var mapped =
                (br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation)
                        SnapshotMapperCatalog.map(
                                ReportSnapshotTemplate.FATURAS_POR_CLIENTE,
                                new DataExportRecordInput(row, Optional.empty()));
        assertTrue(mapped.valid());
        assertEquals(282, mapped.invoicesMapping().value().items().size());
        assertEquals(282, mapped.fitFteInvoicesOrderNumber().value().items().size());
        assertEquals(invoices.toString(), mapped.invoicesMapping().raw());
        assertEquals(orders.toString(), mapped.fitFteInvoicesOrderNumber().raw());
    }

    @Test
    void aPickupCanBeDeletedBeforeItsRequestedDate() {
        final var row =
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("id", 1234)
                        .put("sequence_code", 34)
                        .put("request_date", "2026-08-28")
                        .put("deleted_at", "2026-08-27T15:55:28-03:00");
        assertTrue(
                SnapshotMapperCatalog.map(
                                ReportSnapshotTemplate.COLETAS_EXCLUIDAS,
                                new DataExportRecordInput(row, Optional.empty()))
                        .valid());
        row.put("request_date", "bad-date");
        assertFalse(
                SnapshotMapperCatalog.map(
                                ReportSnapshotTemplate.COLETAS_EXCLUIDAS,
                                new DataExportRecordInput(row, Optional.empty()))
                        .valid());
    }
}

package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.SnapshotMapperCatalog;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductionSnapshotMapperRegressionTest {
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

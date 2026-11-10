package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.DataExportSnapshotMain;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SnapshotLiteralBridge0762Test {
    private static final String RECORD =
            "{\"corporation_sequence_number\":1,\"service_at\":\"2026-11-06\","
                    + "\"invoices_volumes\":0,\"taxed_weight\":12.340000000,\"total\":1.00}";

    @Test
    void bootstrapUsesTheLiteralSliceAndPreservesEveryDuplicate() throws Exception {
        final var ordinals = new ArrayList<Long>();
        final var result = capture(RECORD, true, ordinals);
        assertTrue(result.success());
        assertEquals(2, result.typedRows());
        assertEquals(0, result.rejectedRows());
        assertEquals(java.util.List.of(1L, 2L), ordinals);
    }

    @Test
    void missingOriginalSliceRefusesNumericQualification() throws Exception {
        final var result = capture(RECORD, false, new ArrayList<>());
        assertEquals(0, result.typedRows());
        assertEquals(2, result.rejectedRows());
        assertTrue(result.terminal());
    }

    @Test
    void fractionalIntegerLexemeCannotBecomeAnInventedIntegralToken() throws Exception {
        final var result =
                capture(
                        RECORD.replace("\"invoices_volumes\":0", "\"invoices_volumes\":1.0"),
                        true,
                        new ArrayList<>());
        assertEquals(0, result.typedRows());
        assertEquals(2, result.rejectedRows());
    }

    private static ReportSnapshotExtractor.Result capture(
            final String record, final boolean literal, final ArrayList<Long> ordinals)
            throws Exception {
        final String document = "[" + record + "," + record + "]";
        final var data = CurlSnapshotWire.JSON.readTree(document);
        final var slices = DataExportRawRecordSlices.capture(document, data.size(), 100000);
        final var info = CurlSnapshotWire.JSON.createObjectNode();
        final var fields = info.putObject("fields");
        data.get(0).fieldNames().forEachRemaining(name -> fields.put(name, "title"));
        info.putArray("filters").addObject().put("table", "freights").put("field", "service_at");
        final var wire =
                new ReportSnapshotExtractor.Wire() {
                    @Override
                    public JsonNode info(final ReportSnapshotTemplate template) {
                        return info;
                    }

                    @Override
                    public JsonNode page(
                            final ReportSnapshotTemplate template,
                            final BusinessDateRange dates,
                            final int page,
                            final int per) {
                        return page == 1 ? data : CurlSnapshotWire.JSON.createArrayNode();
                    }

                    @Override
                    public DataExportRecordInput recordInput(final JsonNode row, final int index) {
                        return new DataExportRecordInput(
                                row,
                                literal ? Optional.of(slices.record(index)) : Optional.empty());
                    }
                };
        final var day = LocalDate.of(2026, 11, 6);
        return DataExportSnapshotMain.capture(
                ReportSnapshotTemplate.LOCALIZACAO_CARGAS,
                new BusinessDateRange(day, day),
                wire,
                (capture, ordinal, row, observation) -> ordinals.add(ordinal),
                new ReportSnapshotExtractor.Limits(4, 100, 100));
    }
}

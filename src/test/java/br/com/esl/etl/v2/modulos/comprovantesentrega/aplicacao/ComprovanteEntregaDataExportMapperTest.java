package br.com.esl.etl.v2.modulos.comprovantesentrega.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.DataExportSnapshotMain;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ComprovanteEntregaDataExportMapperTest {
    private static final ReportSnapshotTemplate TEMPLATE =
            ReportSnapshotTemplate.COMPROVANTES_ENTREGA;
    private static final String INVOICES = "fag_fhs_fit_invoices_mapping";
    private static final String CTE = "fag_fhs_fit_fhe_cte_number";
    private static final String DRAFT = "fag_fhs_fit_corporation_sequence_number";

    @Test
    void preservesExpandedRowsInTheSameArchiveAndLongInvoiceLists() throws Exception {
        final var json = CurlSnapshotWire.JSON;
        final JsonNode contract;
        try (var source = getClass().getResourceAsStream("/" + TEMPLATE.contractResource())) {
            contract = json.readTree(source);
        }
        final var info = json.createObjectNode();
        info.set("filters", contract.required("filters"));
        info.set("required_date_filters", contract.required("requiredDateFilters"));
        info.set("default_order", contract.required("defaultOrder"));
        final var fields = info.putObject("fields");
        final var row = json.createObjectNode();
        for (final var field : contract.required("fields")) {
            final var name = field.required("name").asText();
            fields.putObject(name);
            row.putNull(name);
        }
        row.put("sequence_code", 10).put(DRAFT, 20);
        final var invoices = row.putArray(INVOICES);
        for (int n = 0; n < 87; n++) {
            invoices.add("0000" + n);
        }
        final var page = json.createArrayNode().add(row).add(row.deepCopy().put(DRAFT, 21));
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
                            final int number,
                            final int per) {
                        return number == 1 ? page : json.createArrayNode();
                    }
                };
        final var retained = new ArrayList<JsonNode>();
        final var date = LocalDate.of(2026, 11, 6);
        final var result =
                DataExportSnapshotMain.capture(
                        TEMPLATE,
                        new BusinessDateRange(date, date),
                        wire,
                        (id, ordinal, raw, observation) -> retained.add(raw),
                        new ReportSnapshotExtractor.Limits(2, 1, 20));
        assertTrue(result.success());
        assertEquals(2, result.typedRows());
        assertEquals(23, result.fields());
        assertEquals(87, retained.get(0).required(INVOICES).size());
        assertTrue(retained.get(0).required(CTE).isNull());
        assertFalse(ReportSnapshotTemplate.receivingTemplates().contains(TEMPLATE));

        final var mapper = new ComprovanteEntregaDataExportMapper();
        assertFalse(mapper.map(row.deepCopy().put("sequence_code", 0)).valid());
        assertFalse(mapper.map(row.deepCopy().put(INVOICES, "00000")).valid());
    }
}

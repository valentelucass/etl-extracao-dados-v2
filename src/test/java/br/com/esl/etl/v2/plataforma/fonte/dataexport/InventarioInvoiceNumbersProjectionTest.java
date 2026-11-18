package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioInvoicesFieldParser;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioInvoiceNumbers;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class InventarioInvoiceNumbersProjectionTest {
    @Test
    void realProjectorAndSidecarSerializerCarryAllThirtyThreeItemsWithTheirPositionsAndRepetitions()
            throws Exception {
        final var plan = ObservedExpansionCoreFixtures.plan(ObservedExpansionCaptureTemplate.INV);
        final var row = ObservedExpansionSchemaFixtures.row(ObservedExpansionCaptureTemplate.INV);
        for (final String field :
                new String[] {
                    "started_at",
                    "finished_at",
                    "cnr_c_s_fit_dpn_delivery_prediction_at",
                    "cnr_c_s_fit_dpn_performance_finished_at",
                    "cnr_c_s_fit_fte_lce_occurrence_at"
                }) {
            row.put(field, "2026-10-17T12:00:00-03:00");
        }
        for (final String field :
                new String[] {
                    "cnr_c_s_fit_invoices_value",
                    "cnr_c_s_fit_real_weight",
                    "cnr_c_s_fit_taxed_weight",
                    "cnr_c_s_fit_total_cubic_volume"
                }) {
            row.put(field, "1.0");
        }
        final var expected = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 33; index++) {
            expected.add(index % 2 == 0 ? "A" : "B");
        }
        final var projector = new ObservedExpansionPageProjector(plan);
        final var projection =
                projector.projectNext(ObservedExpansionCoreFixtures.raw(plan, 1, "[" + row + "]"));
        assertTrue(projection.records().get(0).mapperValid());
        final var field =
                ObservedExpansionCoreFixtures.field(
                        projection.records().get(0), InventarioInvoicesFieldParser.FIELD);
        assertTrue(field.value().value() instanceof InventarioInvoiceNumbers);
        final var encoded =
                ObservedExpansionCoreFixtures.jsonField(
                        projection, InventarioInvoicesFieldParser.FIELD);
        assertEquals(expected, encoded.path("typed"));
        assertEquals(
                expected, JsonMapper.builder().build().readTree(encoded.path("raw").textValue()));
        assertEquals(33, encoded.path("typed").size());
    }
}

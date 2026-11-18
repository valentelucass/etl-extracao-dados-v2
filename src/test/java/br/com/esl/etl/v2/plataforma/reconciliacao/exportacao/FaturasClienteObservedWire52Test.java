package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class FaturasClienteObservedWire52Test {
    @Test
    void archived52Fields18FiltersRemainDistinctFromThe53FieldMapper() throws Exception {
        final var shape = FaturasClienteObservedWire52.shape();
        assertEquals(52, shape.fields().size());
        assertEquals(18, shape.filters().size());
        assertFalse(shape.fields().contains("third_party_ctes_value"));
        final var row = FaturasClienteCaptureFixture.row(1);
        assertTrue(FaturasClienteObservedWire52.qualified(row, shape, new TreeMap<>()));
        final var envelope = ExportValidationSupport.JSON.createObjectNode();
        envelope.set("data", row);
        final var mapped =
                new br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao
                                .FaturaClienteDataExportMapper()
                        .map(1, envelope);
        assertTrue(mapped.valid());
        assertEquals(
                br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence.ABSENT,
                mapped.thirdPartyCtesValue().presence());
    }

    @Test
    void directQualifiedNamesAndTableFieldDescriptorsBindTheExactSameInfoSet() throws Exception {
        final var shape = FaturasClienteObservedWire52.shape();
        assertEquals(
                FaturasClienteObservedWire52.INFO_NAME_BINDING_VARIANT,
                FaturasClienteObservedWire52.validateInfoNames(
                        FaturasClienteCaptureFixture.info(), shape));
        assertEquals(
                FaturasClienteObservedWire52.INFO_NAME_BINDING_VARIANT,
                FaturasClienteObservedWire52.validateInfoNames(
                        FaturasClienteCaptureFixture.currentQualifiedInfo(), shape));
    }

    @Test
    void wrongQualifierDuplicateMissingOrUnqualifiedInfoCannotPassByCardinality() throws Exception {
        final var shape = FaturasClienteObservedWire52.shape();
        for (final String mutation : java.util.List.of("wrong", "duplicate", "missing", "simple")) {
            final var info = FaturasClienteCaptureFixture.info();
            final var filters =
                    (com.fasterxml.jackson.databind.node.ArrayNode) info.required("filters");
            switch (mutation) {
                case "wrong" ->
                        filters.set(
                                0,
                                ExportValidationSupport.JSON
                                        .getNodeFactory()
                                        .textNode("wrong.service_at"));
                case "duplicate" -> filters.add(filters.get(0));
                case "missing" -> filters.remove(0);
                default ->
                        filters.set(
                                0,
                                ExportValidationSupport.JSON
                                        .getNodeFactory()
                                        .textNode("service_at"));
            }
            final Class<? extends RuntimeException> expected =
                    "duplicate".equals(mutation)
                            ? IllegalStateException.class
                            : IllegalArgumentException.class;
            assertThrows(
                    expected, () -> FaturasClienteObservedWire52.validateInfoNames(info, shape));
        }
    }

    @Test
    void nullOnlyUnknownsAndArrayItemTypesStayFieldSpecific() throws Exception {
        final var shape = FaturasClienteObservedWire52.shape();
        for (final String field :
                java.util.List.of("fit_nse_number", "fit_ant_ils_atn_transaction_date")) {
            final var row = FaturasClienteCaptureFixture.row(1);
            row.put(field, "authored");
            final var refusals = new TreeMap<String, Long>();
            assertFalse(FaturasClienteObservedWire52.qualified(row, shape, refusals));
            assertEquals(1L, refusals.get("/" + field + ":STRING"));
            row.putNull(field);
            assertTrue(FaturasClienteObservedWire52.qualified(row, shape, new TreeMap<>()));
        }
        final ObjectNode row = FaturasClienteCaptureFixture.row(1);
        row.putArray("fit_fte_invoices_order_number").addNull();
        assertFalse(FaturasClienteObservedWire52.qualified(row, shape, new TreeMap<>()));
    }
}

package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioInvoicesFieldParser;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class InventarioInvoiceNumbersRoundTripTest {
    @Test
    void devClosedTypedObservationDecoderRestoresThirtyThreeOrderedInvoiceNumbers()
            throws Exception {
        final var json = JsonMapper.builder().build();
        final var row = json.createObjectNode().put("started_at", "2026-10-17T12:00:00-03:00");
        final var array = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 33; index++) {
            array.add(index % 2 == 0 ? "A" : "B");
        }
        final var envelope = json.createObjectNode();
        envelope.set("data", row);
        final var observed = new InventarioDataExportMapper().map(1, envelope);
        assertTrue(observed.valid());
        final var values = json.createObjectNode();
        for (final var component : InventarioObservation.class.getRecordComponents()) {
            final var value = (ExpansionValue<?>) component.getAccessor().invoke(observed);
            final var field =
                    values.putObject(component.getName())
                            .put("presence", value.presence().name())
                            .put("wire", value.wire().name());
            if (value.raw() == null) {
                field.putNull("raw");
            } else {
                field.put("raw", value.raw());
            }
            if (value.value() == null) {
                field.putNull("typed");
            } else {
                field.put("typed", value.value().toString());
            }
            if (value.issue() == null) {
                field.putNull("issue");
            } else {
                field.put("issue", value.issue());
            }
        }
        final var decode =
                DevLocalEngineObservations.class.getDeclaredMethod(
                        "decode", Class.class, JsonNode.class);
        decode.setAccessible(true);
        final var restored =
                (InventarioObservation) decode.invoke(null, InventarioObservation.class, values);
        assertEquals(observed, restored);
        assertEquals(33, restored.cnrCSFitInvoicesMapping().value().items().size());
        assertEquals("B", restored.cnrCSFitInvoicesMapping().value().items().get(31));
    }

    @Test
    void devReaderKeepsTheGenericExpansionStringsThirtyTwoItemBoundary() throws Exception {
        final var typed =
                DevLocalEngineObservations.class.getDeclaredMethod(
                        "typed", Class.class, String.class, String.class);
        typed.setAccessible(true);
        final var array = JsonMapper.builder().build().createArrayNode();
        for (int index = 0; index < 33; index++) {
            array.add("");
        }
        final var refused =
                assertThrows(
                        InvocationTargetException.class,
                        () -> typed.invoke(null, ExpansionStrings.class, "", array.toString()));
        assertTrue(refused.getCause() instanceof IllegalArgumentException);
        assertEquals("DEV_LOCAL_TYPED_ARRAY", refused.getCause().getMessage());
    }
}

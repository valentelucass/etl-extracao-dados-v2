package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceNumbers;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceOrderNumbers;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

/** DRAFT: no DB/runtime is needed; exercise the actual FAT branch of the closed decoder. */
class FaturasInvoiceNumbersRoundTripTest {
    @Test
    void faturasDecoderRoundTripsFiftyItemsInBothFieldsWithOrderDuplicatesAndOriginalRaw()
            throws Exception {
        final var json = JsonMapper.builder().build();
        final var row = json.createObjectNode();
        final var invoices = row.putArray("invoices_mapping");
        final var orders = row.putArray("fit_fte_invoices_order_number");
        for (int index = 0; index < 50; index++) {
            invoices.add(index % 2 == 0 ? "B" : "A");
            orders.add(index % 2 == 0 ? "  \u00c1\ud83d\ude00  " : "A");
        }
        final var envelope = json.createObjectNode();
        envelope.set("data", row);
        final var observed = new FaturaClienteDataExportMapper().map(1, envelope);
        assertTrue(observed.valid());
        final var restored = decode(projection(observed));
        assertEquals(observed, restored);
        assertEquals(invoices.toString(), restored.invoicesMapping().raw());
        assertEquals(orders.toString(), restored.fitFteInvoicesOrderNumber().raw());
        assertEquals(50, restored.invoicesMapping().value().items().size());
        assertEquals(50, restored.fitFteInvoicesOrderNumber().value().items().size());
        assertEquals("B", restored.invoicesMapping().value().items().get(48));
        assertEquals("A", restored.invoicesMapping().value().items().get(49));
    }

    @Test
    void faturasRoundTripPreservesNullEmptyAndQuarantinedArrayDispositions() throws Exception {
        final var json = JsonMapper.builder().build();
        for (int variant = 0; variant < 3; variant++) {
            final var row = json.createObjectNode();
            if (variant == 0) {
                row.putNull("invoices_mapping");
                row.putNull("fit_fte_invoices_order_number");
            } else if (variant == 1) {
                row.putArray("invoices_mapping");
                row.putArray("fit_fte_invoices_order_number");
            } else {
                row.putArray("invoices_mapping").addNull();
                row.putArray("fit_fte_invoices_order_number").add(1);
            }
            final var envelope = json.createObjectNode();
            envelope.set("data", row);
            final var observed = new FaturaClienteDataExportMapper().map(1, envelope);
            assertEquals(observed, decode(projection(observed)));
            if (variant == 2) {
                assertNull(decode(projection(observed)).invoicesMapping().value());
            }
        }
    }

    @Test
    void faturasClosedDecoderRefusesInvalidItemTypesAndTrailingJsonInBothFields() throws Exception {
        final var typed =
                DevLocalEngineObservations.class.getDeclaredMethod(
                        "typed", Class.class, String.class, String.class);
        typed.setAccessible(true);
        for (final Class<?> type :
                new Class<?>[] {FaturaInvoiceNumbers.class, FaturaInvoiceOrderNumbers.class}) {
            for (final String invalid : new String[] {"[1]", "[null]", "[] []", "true"}) {
                final var failure =
                        assertThrows(
                                InvocationTargetException.class,
                                () -> typed.invoke(null, type, "", invalid));
                if ("[] []".equals(invalid)) {
                    assertTrue(failure.getCause() instanceof JsonProcessingException);
                } else {
                    assertTrue(failure.getCause() instanceof IllegalArgumentException);
                    assertEquals("DEV_LOCAL_TYPED_ARRAY", failure.getCause().getMessage());
                }
            }
        }
    }

    @Test
    void faturasClosedDecoderRetainsTheRawFourThousandGuard() throws Exception {
        final var typed =
                DevLocalEngineObservations.class.getDeclaredMethod(
                        "typed", Class.class, String.class, String.class);
        typed.setAccessible(true);
        final String overRaw = "[\"" + "A".repeat(3997) + "\"]";
        for (final Class<?> type :
                new Class<?>[] {FaturaInvoiceNumbers.class, FaturaInvoiceOrderNumbers.class}) {
            final var failure =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> typed.invoke(null, type, "", overRaw));
            assertEquals("EXP_RAW_BOUND", failure.getCause().getMessage());
        }
    }

    private static FaturaClienteObservation decode(final ObjectNode values) throws Exception {
        final var method =
                DevLocalEngineObservations.class.getDeclaredMethod(
                        "decode", Class.class, JsonNode.class);
        method.setAccessible(true);
        return (FaturaClienteObservation)
                method.invoke(null, FaturaClienteObservation.class, values);
    }

    private static ObjectNode projection(final FaturaClienteObservation observation)
            throws Exception {
        final var values = JsonMapper.builder().build().createObjectNode();
        for (final var component : FaturaClienteObservation.class.getRecordComponents()) {
            final var value = (ExpansionValue<?>) component.getAccessor().invoke(observation);
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
        return values;
    }
}

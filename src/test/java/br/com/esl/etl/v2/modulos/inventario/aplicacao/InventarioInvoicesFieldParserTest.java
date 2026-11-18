package br.com.esl.etl.v2.modulos.inventario.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.inventario.domain.InventarioInvoiceNumbers;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventarioInvoicesFieldParserTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void
            observedThirtyThreeItemShapeIsAcceptedOnlyByTheInventarioFieldWithoutChangingOrderOrDuplicates() {
        final var row = JSON.createObjectNode();
        final var array = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 33; index++) {
            array.add(index % 3 == 0 ? "same" : "invoice-" + index);
        }
        final var result = InventarioInvoicesFieldParser.parse(row);
        assertTrue(result.valid());
        assertEquals(33, result.value().items().size());
        assertEquals("same", result.value().items().get(0));
        assertEquals("invoice-1", result.value().items().get(1));
        assertEquals("same", result.value().items().get(3));
        assertEquals(array.toString(), result.raw());
        assertFalse(ExpansionFieldParser.strings(row, InventarioInvoicesFieldParser.FIELD).valid());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExpansionStrings(Collections.nCopies(33, "")));
    }

    @Test
    void absentNullAndEmptyArrayRemainThreeDifferentObservations() {
        final var row = JSON.createObjectNode();
        assertEquals(Presence.ABSENT, InventarioInvoicesFieldParser.parse(row).presence());
        row.putNull(InventarioInvoicesFieldParser.FIELD);
        final var nulled = InventarioInvoicesFieldParser.parse(row);
        assertEquals(Presence.NULL, nulled.presence());
        assertEquals(Wire.NULL, nulled.wire());
        row.putArray(InventarioInvoicesFieldParser.FIELD);
        final var empty = InventarioInvoicesFieldParser.parse(row);
        assertEquals(Presence.VALUE, empty.presence());
        assertEquals(List.of(), empty.value().items());
        assertEquals("[]", empty.raw());
    }

    @Test
    void itemCount1024IsBoundedAnd1025IsQuarantinedWithTheOriginalArray() {
        final var row = JSON.createObjectNode();
        final var array = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 1024; index++) {
            array.add("");
        }
        assertTrue(InventarioInvoicesFieldParser.parse(row).valid());
        array.add("");
        final var refused = InventarioInvoicesFieldParser.parse(row);
        assertFalse(refused.valid());
        assertEquals(array.toString(), refused.raw());
        assertThrows(
                IllegalArgumentException.class,
                () -> new InventarioInvoiceNumbers(Collections.nCopies(1025, "")));
    }

    @Test
    void itemBoundIs256Utf16UnitsAndTheSharedRaw4000LimitStillApplies() {
        final var row = JSON.createObjectNode();
        row.putArray(InventarioInvoicesFieldParser.FIELD).add("a".repeat(256));
        assertTrue(InventarioInvoicesFieldParser.parse(row).valid());
        row.putArray(InventarioInvoicesFieldParser.FIELD).add("a".repeat(257));
        assertFalse(InventarioInvoicesFieldParser.parse(row).valid());
        row.putArray(InventarioInvoicesFieldParser.FIELD).add("\uD83D\uDE00".repeat(128));
        assertTrue(InventarioInvoicesFieldParser.parse(row).valid());
        row.putArray(InventarioInvoicesFieldParser.FIELD).add("\uD83D\uDE00".repeat(129));
        assertFalse(InventarioInvoicesFieldParser.parse(row).valid());
        final var array = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 17; index++) {
            array.add("a".repeat(250));
        }
        assertTrue(array.toString().length() > 4000);
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> InventarioInvoicesFieldParser.parse(row))
                        .getMessage());
    }

    @Test
    void nonArraysAndNonStringItemsRetainWireAndRawButNeverBecomeValid() {
        final var row = JSON.createObjectNode().put(InventarioInvoicesFieldParser.FIELD, "[]");
        final var text = InventarioInvoicesFieldParser.parse(row);
        assertFalse(text.valid());
        assertEquals(Wire.STRING, text.wire());
        assertEquals("[]", text.raw());
        row.putArray(InventarioInvoicesFieldParser.FIELD).add("A").add(7);
        final var mixed = InventarioInvoicesFieldParser.parse(row);
        assertFalse(mixed.valid());
        assertEquals(Wire.ARRAY, mixed.wire());
        assertEquals("[\"A\",7]", mixed.raw());
    }

    @Test
    void typedItemsAreAnImmutableCopyRatherThanALiveInputCollection() {
        final var source = new ArrayList<>(List.of("B", "A", "B"));
        final var result = new InventarioInvoiceNumbers(source);
        source.clear();
        assertEquals(List.of("B", "A", "B"), result.items());
        assertThrows(UnsupportedOperationException.class, () -> result.items().add("C"));
    }
}

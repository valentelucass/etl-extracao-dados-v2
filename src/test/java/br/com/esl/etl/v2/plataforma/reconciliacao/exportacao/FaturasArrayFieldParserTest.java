package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturasArrayFieldParser;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceNumbers;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceOrderNumbers;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FaturasArrayFieldParserTest {
    @Test
    void bothFaturasFieldsKeepFiftyItemsOrderDuplicatesAndLiteralCanonicalRaw() {
        final var json = JsonMapper.builder().build();
        final var row = json.createObjectNode();
        for (final String name : List.of("invoices_mapping", "fit_fte_invoices_order_number")) {
            final var array = row.putArray(name);
            for (int index = 0; index < 50; index++) {
                array.add(index % 3 == 0 ? "B" : "A");
            }
        }
        final var envelope = json.createObjectNode();
        envelope.set("data", row);
        final var observation = new FaturaClienteDataExportMapper().map(1, envelope);
        assertTrue(observation.valid());
        assertEquals(50, observation.invoicesMapping().value().items().size());
        assertEquals(50, observation.fitFteInvoicesOrderNumber().value().items().size());
        assertEquals(
                row.required("invoices_mapping").toString(), observation.invoicesMapping().raw());
        assertEquals(
                row.required("fit_fte_invoices_order_number").toString(),
                observation.fitFteInvoicesOrderNumber().raw());
        assertEquals("B", observation.invoicesMapping().value().items().get(0));
        assertEquals("A", observation.invoicesMapping().value().items().get(1));
        assertEquals("A", observation.invoicesMapping().value().items().get(2));
        assertEquals("B", observation.invoicesMapping().value().items().get(48));
    }

    @Test
    void eachFieldAcceptsTheLargestArrayThatFitsAndRefusesTheNextItem() {
        final var json = JsonMapper.builder().build();
        final var row = json.createObjectNode();
        final var invoices = row.putArray("invoices_mapping");
        for (int index = 0; index < FaturaInvoiceNumbers.MAXIMUM_ITEMS; index++) {
            invoices.add("");
        }
        final var orders = row.putArray("fit_fte_invoices_order_number");
        for (int index = 0; index < FaturaInvoiceOrderNumbers.MAXIMUM_ITEMS; index++) {
            orders.add("");
        }
        assertEquals(
                FaturaInvoiceNumbers.MAXIMUM_ITEMS,
                FaturasArrayFieldParser.invoiceNumbers(row).value().items().size());
        assertEquals(
                FaturaInvoiceOrderNumbers.MAXIMUM_ITEMS,
                FaturasArrayFieldParser.orderNumbers(row).value().items().size());
        invoices.add("");
        orders.add("");
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(IllegalArgumentException.class,
                                () -> FaturasArrayFieldParser.invoiceNumbers(row)).getMessage());
        assertEquals(
                "EXP_RAW_BOUND",
                assertThrows(IllegalArgumentException.class,
                                () -> FaturasArrayFieldParser.orderNumbers(row)).getMessage());
    }

    @Test
    void absentNullAndEmptyArraysRemainThreeDifferentRepresentations() {
        final var row = JsonMapper.builder().build().createObjectNode();
        assertEquals(
                ExpansionValue.Presence.ABSENT,
                FaturasArrayFieldParser.invoiceNumbers(row).presence());
        row.putNull("invoices_mapping");
        assertEquals(
                ExpansionValue.Presence.NULL,
                FaturasArrayFieldParser.invoiceNumbers(row).presence());
        row.putArray("invoices_mapping");
        final var empty = FaturasArrayFieldParser.invoiceNumbers(row);
        assertEquals(ExpansionValue.Presence.VALUE, empty.presence());
        assertEquals(ExpansionValue.Wire.ARRAY, empty.wire());
        assertEquals("[]", empty.raw());
        assertEquals(List.of(), empty.value().items());
    }

    @Test
    void unobservedArrayItemAndWholeFieldTypesKeepTheOriginalQuarantineDisposition() {
        final var json = JsonMapper.builder().build();
        for (final String name : List.of("invoices_mapping", "fit_fte_invoices_order_number")) {
            final var row = json.createObjectNode();
            final var array = row.putArray(name);
            for (int index = 0; index < 49; index++) {
                array.add("A");
            }
            array.addNull();
            final var invalid =
                    name.equals("invoices_mapping")
                            ? FaturasArrayFieldParser.invoiceNumbers(row)
                            : FaturasArrayFieldParser.orderNumbers(row);
            final var original = ExpansionFieldParser.strings(row, name);
            assertEquals(original.presence(), invalid.presence());
            assertEquals(original.wire(), invalid.wire());
            assertEquals(original.raw(), invalid.raw());
            assertEquals(original.issue(), invalid.issue());
            assertNull(invalid.value());
            row.put(name, true);
            final var whole =
                    name.equals("invoices_mapping")
                            ? FaturasArrayFieldParser.invoiceNumbers(row)
                            : FaturasArrayFieldParser.orderNumbers(row);
            assertFalse(whole.valid());
            assertEquals(ExpansionValue.Wire.BOOLEAN, whole.wire());
            assertEquals("true", whole.raw());
        }
    }

    @Test
    void unicodeWhitespaceEscapesAndRepeatedValuesArePreservedWithoutNormalization() {
        final var row = JsonMapper.builder().build().createObjectNode();
        final var array = row.putArray("invoices_mapping");
        final List<String> expected = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            final String text = index % 2 == 0 ? "  \u00c1\ud83d\ude00\\\"\n  " : "  ";
            expected.add(text);
            array.add(text);
        }
        final var parsed = FaturasArrayFieldParser.invoiceNumbers(row);
        assertTrue(parsed.valid());
        assertEquals(expected, parsed.value().items());
        assertEquals(array.toString(), parsed.raw());
    }

    @Test
    void itemUtf16BoundaryRemainsTwoHundredFiftySixForBothFaturasFields() {
        final var row = JsonMapper.builder().build().createObjectNode();
        row.putArray("invoices_mapping").add("A".repeat(256));
        row.putArray("fit_fte_invoices_order_number").add("A".repeat(256));
        assertTrue(FaturasArrayFieldParser.invoiceNumbers(row).valid());
        assertTrue(FaturasArrayFieldParser.orderNumbers(row).valid());
        row.putArray("invoices_mapping").add("A".repeat(255) + "\ud83d\ude00");
        row.putArray("fit_fte_invoices_order_number").add("A".repeat(255) + "\ud83d\ude00");
        assertFalse(FaturasArrayFieldParser.invoiceNumbers(row).valid());
        assertFalse(FaturasArrayFieldParser.orderNumbers(row).valid());
    }

    @Test
    void rawFourThousandRemainsIndependentOfTheReviewedItemCount() {
        final var row = JsonMapper.builder().build().createObjectNode();
        row.putArray("invoices_mapping").add("A".repeat(3996));
        final var atBound = FaturasArrayFieldParser.invoiceNumbers(row);
        assertEquals(4000, atBound.raw().length());
        assertFalse(atBound.valid());
        assertNotNull(atBound.issue());
        row.putArray("invoices_mapping").add("A".repeat(3997));
        final var refusal =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FaturasArrayFieldParser.invoiceNumbers(row));
        assertEquals("EXP_RAW_BOUND", refusal.getMessage());
    }

    @Test
    void genericThirtyTwoLimitAndImmutableFaturasListsRemainIndependent() {
        final var row = JsonMapper.builder().build().createObjectNode();
        final var array = row.putArray("invoices_mapping");
        for (int index = 0; index < 33; index++) {
            array.add("A");
        }
        assertFalse(ExpansionFieldParser.strings(row, "invoices_mapping").valid());
        assertTrue(FaturasArrayFieldParser.invoiceNumbers(row).valid());
        final var authored = new ArrayList<>(List.of("B", "A", "A"));
        final var value = new FaturaInvoiceNumbers(authored);
        authored.set(0, "changed");
        assertEquals(List.of("B", "A", "A"), value.items());
        assertThrows(UnsupportedOperationException.class, () -> value.items().add("C"));
    }
}

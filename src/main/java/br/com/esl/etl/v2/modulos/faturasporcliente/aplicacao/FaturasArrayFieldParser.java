package br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceNumbers;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaInvoiceOrderNumbers;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.OrderedExpansionStrings;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Only the two FAT arrays may exceed the unchanged shared32 contract. Raw4000 stays mandatory. */
public final class FaturasArrayFieldParser {
    private FaturasArrayFieldParser() {}

    public static ExpansionValue<FaturaInvoiceNumbers> invoiceNumbers(final JsonNode row) {
        return parse(
                row,
                "invoices_mapping",
                FaturaInvoiceNumbers.MAXIMUM_ITEMS,
                FaturaInvoiceNumbers::new);
    }

    public static ExpansionValue<FaturaInvoiceOrderNumbers> orderNumbers(final JsonNode row) {
        return parse(
                row,
                "fit_fte_invoices_order_number",
                FaturaInvoiceOrderNumbers.MAXIMUM_ITEMS,
                FaturaInvoiceOrderNumbers::new);
    }

    private static <T extends OrderedExpansionStrings> ExpansionValue<T> parse(
            final JsonNode row,
            final String field,
            final int maximum,
            final Function<List<String>, T> constructor) {
        // Reuse unchanged presence/wire/raw4000/disposition behavior for every original input.
        final var initial = ExpansionFieldParser.strings(row, field);
        if (initial.value() != null) {
            return new ExpansionValue<>(
                    initial.presence(),
                    initial.wire(),
                    initial.raw(),
                    constructor.apply(initial.value().items()),
                    null);
        }
        final var node = row.get(field);
        if (initial.issue() == null
                || node == null
                || !node.isArray()
                || node.size() <= 32
                || node.size() > maximum) {
            return new ExpansionValue<>(
                    initial.presence(), initial.wire(), initial.raw(), null, initial.issue());
        }
        try {
            final var values = new ArrayList<String>(node.size());
            for (final JsonNode item : node) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("FATURAS_ARRAY_STRING_REQUIRED");
                }
                values.add(item.textValue());
            }
            return new ExpansionValue<>(
                    initial.presence(),
                    initial.wire(),
                    initial.raw(),
                    constructor.apply(values),
                    null);
        } catch (final IllegalArgumentException refused) {
            return new ExpansionValue<>(
                    initial.presence(), initial.wire(), initial.raw(), null, initial.issue());
        }
    }
}

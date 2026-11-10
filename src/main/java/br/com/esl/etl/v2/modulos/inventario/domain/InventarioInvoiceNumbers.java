package br.com.esl.etl.v2.modulos.inventario.domain;

import br.com.esl.etl.v2.plataforma.expansao.OrderedExpansionStrings;
import java.util.List;

/** Inventário10633 invoice occurrences. Order, spelling and repetitions remain unchanged. */
public record InventarioInvoiceNumbers(List<String> items) implements OrderedExpansionStrings {
    public static final int MAXIMUM_ITEMS = 1024;
    public static final int MAXIMUM_ITEM_UTF16_LENGTH = 256;

    public InventarioInvoiceNumbers {
        items = List.copyOf(items);
        if (items.size() > MAXIMUM_ITEMS
                || items.stream().anyMatch(item -> item.length() > MAXIMUM_ITEM_UTF16_LENGTH)) {
            throw new IllegalArgumentException("INVENTARIO_INVOICE_MAPPING_BOUND");
        }
    }
}

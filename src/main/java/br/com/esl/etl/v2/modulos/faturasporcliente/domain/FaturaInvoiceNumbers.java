package br.com.esl.etl.v2.modulos.faturasporcliente.domain;

import br.com.esl.etl.v2.plataforma.expansao.OrderedExpansionStrings;
import java.util.List;

/** Ordered strings supplied by the Faturas por Cliente report (4924). */
public record FaturaInvoiceNumbers(List<String> items) implements OrderedExpansionStrings {
    // A compact JSON array of 1,333 empty strings uses the entire 4,000-character raw field.
    public static final int MAXIMUM_ITEMS = 1_333;
    public static final int MAXIMUM_ITEM_UTF16_LENGTH = 256;

    public FaturaInvoiceNumbers {
        items = List.copyOf(items);
        if (items.size() > MAXIMUM_ITEMS
                || items.stream().anyMatch(item -> item.length() > MAXIMUM_ITEM_UTF16_LENGTH)) {
            throw new IllegalArgumentException("FATURAS_ARRAY_FIELD_BOUND");
        }
    }
}

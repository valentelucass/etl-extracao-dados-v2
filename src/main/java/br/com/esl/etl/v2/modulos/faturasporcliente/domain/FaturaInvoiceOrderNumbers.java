package br.com.esl.etl.v2.modulos.faturasporcliente.domain;

import br.com.esl.etl.v2.plataforma.expansao.OrderedExpansionStrings;
import java.util.List;

/** Faturas4924 only. DRAFT: the field bound is pending all actual daily profiles. */
public record FaturaInvoiceOrderNumbers(List<String> items) implements OrderedExpansionStrings {
    public static final int MAXIMUM_ITEMS = 110;
    public static final int MAXIMUM_ITEM_UTF16_LENGTH = 256;

    public FaturaInvoiceOrderNumbers {
        items = List.copyOf(items);
        if (items.size() > MAXIMUM_ITEMS
                || items.stream().anyMatch(item -> item.length() > MAXIMUM_ITEM_UTF16_LENGTH)) {
            throw new IllegalArgumentException("FATURAS_ARRAY_FIELD_BOUND");
        }
    }
}

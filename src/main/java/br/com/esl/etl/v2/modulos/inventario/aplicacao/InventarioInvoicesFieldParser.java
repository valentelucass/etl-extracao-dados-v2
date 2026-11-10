package br.com.esl.etl.v2.modulos.inventario.aplicacao;

import br.com.esl.etl.v2.modulos.inventario.domain.InventarioInvoiceNumbers;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;

/** Field-scoped parser; the shared ExpansionStrings32 contract and raw4000 bound stay intact. */
public final class InventarioInvoicesFieldParser {
    public static final String FIELD = "cnr_c_s_fit_invoices_mapping";

    private InventarioInvoicesFieldParser() {}

    public static ExpansionValue<InventarioInvoiceNumbers> parse(final JsonNode row) {
        final JsonNode node = row.get(FIELD);
        if (node == null) {
            return new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        }
        if (node.isNull()) {
            return new ExpansionValue<>(Presence.NULL, Wire.NULL, null, null, null);
        }
        final Wire wire =
                Wire.valueOf(
                        node.getNodeType().name().equals("NUMBER")
                                ? node.isIntegralNumber() ? "INTEGER" : "NUMBER"
                                : node.getNodeType().name());
        final String raw = node.isTextual() ? node.textValue() : node.toString();
        if (raw.length() > 4000) {
            throw new IllegalArgumentException("EXP_RAW_BOUND");
        }
        try {
            if (!node.isArray() || node.size() > InventarioInvoiceNumbers.MAXIMUM_ITEMS) {
                throw new IllegalArgumentException("INVENTARIO_INVOICE_MAPPING_ARRAY_BOUND");
            }
            final var items = new ArrayList<String>(node.size());
            for (final JsonNode item : node) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException(
                            "INVENTARIO_INVOICE_MAPPING_STRING_REQUIRED");
                }
                items.add(item.textValue());
            }
            return new ExpansionValue<>(
                    Presence.VALUE, wire, raw, new InventarioInvoiceNumbers(items), null);
        } catch (final IllegalArgumentException invalid) {
            return new ExpansionValue<>(
                    Presence.VALUE, wire, raw, null, "INVALID_CNR_C_S_FIT_INVOICES_MAPPING");
        }
    }
}

package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Parsers scoped to the newly mapped reports; existing global parser limits stay unchanged. */
public final class MappedReportFields {
    private MappedReportFields() {}

    public static void requireExact(final JsonNode row, final Set<String> fields) {
        if (row == null || !row.isObject()) {
            throw new IllegalArgumentException("MAPPED_REPORT_ROW_OBJECT");
        }
        final var actual = new HashSet<String>();
        row.fieldNames().forEachRemaining(actual::add);
        if (!fields.equals(actual)) {
            throw new IllegalArgumentException("MAPPED_REPORT_FIELD_DRIFT");
        }
    }

    /** Not a guessed business type: keep any non-null JSON value and its wire kind intact. */
    public static ExpansionValue<String> opaque(final JsonNode row, final String field) {
        return parse(row, field, node -> node.isTextual() ? node.textValue() : node.toString());
    }

    /** Strict textual wire with the existing 4000-character raw bound, without truncation. */
    public static ExpansionValue<String> text(final JsonNode row, final String field) {
        return parse(
                row,
                field,
                node -> {
                    if (!node.isTextual()) {
                        throw new IllegalArgumentException("MAPPED_REPORT_TEXT_REQUIRED");
                    }
                    return node.textValue();
                });
    }

    public static <T> ExpansionValue<T> parse(
            final JsonNode row, final String field, final Function<JsonNode, T> parser) {
        final var node = row.get(field);
        if (node == null) {
            return new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        }
        if (node.isNull()) {
            return new ExpansionValue<>(Presence.NULL, Wire.NULL, null, null, null);
        }
        final Wire wire =
                Wire.valueOf(
                        node.isNumber()
                                ? (node.isIntegralNumber() ? "INTEGER" : "NUMBER")
                                : node.getNodeType().name());
        final String raw = node.isTextual() ? node.textValue() : node.toString();
        if (raw.length() > 4000) {
            throw new IllegalArgumentException("EXP_RAW_BOUND");
        }
        try {
            return new ExpansionValue<>(Presence.VALUE, wire, raw, parser.apply(node), null);
        } catch (final IllegalArgumentException invalid) {
            return new ExpansionValue<>(Presence.VALUE, wire, raw, null, "MAPPED_REPORT_INVALID");
        }
    }
}

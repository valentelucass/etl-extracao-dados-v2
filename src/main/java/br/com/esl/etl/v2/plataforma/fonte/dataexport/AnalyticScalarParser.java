package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Function;

/** Exact SQL integer ranges for the explicitly declared analytical attribute snapshots. */
public final class AnalyticScalarParser {
    private AnalyticScalarParser() {}

    public static ExpansionValue<Long> longInteger(final JsonNode row, final String name) {
        return integer(row, name, Long::valueOf);
    }

    public static ExpansionValue<Integer> integer(final JsonNode row, final String name) {
        return integer(row, name, Integer::valueOf);
    }

    private static <T> ExpansionValue<T> integer(
            final JsonNode row, final String name, final Function<String, T> parser) {
        final var field = ExpansionFieldParser.integer(row, name);
        if (field.value() == null) {
            return new ExpansionValue<>(
                    field.presence(), field.wire(), field.raw(), null, field.issue());
        }
        try {
            return new ExpansionValue<>(
                    field.presence(), field.wire(), field.raw(), parser.apply(field.value()), null);
        } catch (final NumberFormatException failure) {
            return new ExpansionValue<>(
                    field.presence(), field.wire(), field.raw(), null, "ANA_INTEGER_RANGE");
        }
    }

    public static ExpansionValue<String> text(
            final JsonNode row, final String name, final int maximum) {
        final var field = ExpansionFieldParser.text(row, name);
        if (field.value() != null && field.value().length() > maximum) {
            return new ExpansionValue<>(
                    field.presence(), field.wire(), field.raw(), null, "ANA_TEXT_BOUND");
        }
        return field;
    }
}

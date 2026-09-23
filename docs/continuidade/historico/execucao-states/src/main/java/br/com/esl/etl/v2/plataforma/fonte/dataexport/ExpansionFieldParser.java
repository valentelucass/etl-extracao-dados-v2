package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.function.Function;

/** Strict parsers preserve invalid raw fields with a typed quarantine reason; no lossy coercion. */
public final class ExpansionFieldParser {
    private ExpansionFieldParser() {}

    public static ExpansionValue<String> text(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isTextual());
                    require(node.textValue().length() <= 1024);
                    return node.textValue();
                });
    }

    public static ExpansionValue<String> integer(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isIntegralNumber() || node.isTextual());
                    final String raw = node.asText();
                    require(raw.matches("-?(0|[1-9][0-9]{0,37})"));
                    return raw;
                });
    }

    public static ExpansionValue<String> identifier(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isTextual() || node.isIntegralNumber());
                    require(node.asText().length() <= 128);
                    return node.asText();
                });
    }

    public static ExpansionValue<Boolean> bool(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isBoolean());
                    return node.booleanValue();
                });
    }

    public static ExpansionValue<BigDecimal> decimal(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isTextual() || node.isNumber());
                    final String raw = node.asText();
                    require(raw.matches("-?(0|[1-9][0-9]{0,19})(\\.[0-9]{1,8})?"));
                    final BigDecimal value =
                            new BigDecimal(raw).setScale(8, RoundingMode.UNNECESSARY);
                    require(value.precision() <= 28);
                    return value;
                });
    }

    public static ExpansionValue<LocalDate> date(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(
                            node.isTextual()
                                    && node.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
                    return LocalDate.parse(node.textValue());
                });
    }

    public static ExpansionValue<Instant> instant(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isTextual());
                    return OffsetDateTime.parse(node.textValue()).toInstant();
                });
    }

    public static ExpansionValue<LocalTime> time(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isTextual());
                    return LocalTime.parse(node.textValue());
                });
    }

    public static ExpansionValue<ExpansionStrings> strings(final JsonNode row, final String name) {
        return parse(
                row,
                name,
                node -> {
                    require(node.isArray() && node.size() <= 32);
                    final var items = new ArrayList<String>(node.size());
                    for (final var item : node) {
                        require(item.isTextual());
                        items.add(item.textValue());
                    }
                    return new ExpansionStrings(items);
                });
    }

    private static <T> ExpansionValue<T> parse(
            final JsonNode row, final String name, final Function<JsonNode, T> parser) {
        final JsonNode node = row.get(name);
        if (node == null) {
            return new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        }
        if (node.isNull()) {
            return new ExpansionValue<>(Presence.NULL, Wire.NULL, null, null, null);
        }
        final Wire wire =
                Wire.valueOf(
                        node.getNodeType().name().equals("NUMBER")
                                ? (node.isIntegralNumber() ? "INTEGER" : "NUMBER")
                                : node.getNodeType().name());
        final String raw = node.isTextual() ? node.textValue() : node.toString();
        if (raw.length() > 4000) {
            throw new IllegalArgumentException("EXP_RAW_BOUND");
        }
        try {
            return new ExpansionValue<>(Presence.VALUE, wire, raw, parser.apply(node), null);
        } catch (final IllegalArgumentException | java.time.DateTimeException failure) {
            return new ExpansionValue<>(
                    Presence.VALUE,
                    wire,
                    raw,
                    null,
                    "INVALID_" + name.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static void require(final boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("EXP_FIELD_INVALID");
        }
    }
}

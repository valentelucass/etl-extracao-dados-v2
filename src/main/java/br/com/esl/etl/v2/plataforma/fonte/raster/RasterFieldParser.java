package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.domain.RasterTime;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/** Strict, bounded parsing; alias conflicts and malformed values never become successful nulls. */
public final class RasterFieldParser {
    private RasterFieldParser() {}

    public static JsonNode select(final JsonNode node, final String... aliases) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("RAS_OBJECT_REQUIRED");
        }
        JsonNode selected = null;
        for (final String alias : aliases) {
            final JsonNode candidate = node.get(alias);
            if (candidate != null) {
                if (selected != null && !selected.equals(candidate)) {
                    throw new IllegalArgumentException("RAS_ALIAS_CONFLICT");
                }
                selected = candidate;
            }
        }
        return selected;
    }

    public static ExpansionValue<String> text(final JsonNode node, final String... aliases) {
        return parse(
                node,
                value -> {
                    require(value.isTextual() && value.textValue().length() <= 1024);
                    return value.textValue();
                },
                aliases);
    }

    public static ExpansionValue<String> identifier(final JsonNode node, final String... aliases) {
        return parse(
                node,
                value -> {
                    require(value.isTextual() || value.isIntegralNumber());
                    require(value.asText().matches("-?(0|[1-9][0-9]{0,37})"));
                    return value.asText();
                },
                aliases);
    }

    public static ExpansionValue<Integer> count(final JsonNode node, final String... aliases) {
        return parse(
                node,
                value -> {
                    require(value.isTextual() || value.isIntegralNumber());
                    require(value.asText().matches("-?(0|[1-9][0-9]{0,37})"));
                    return new BigInteger(value.asText()).intValueExact();
                },
                aliases);
    }

    public static ExpansionValue<BigDecimal> decimal(final JsonNode node, final String... aliases) {
        return parse(
                node,
                value -> {
                    require(value.isTextual() || value.isNumber());
                    if (value.isTextual()) {
                        require(
                                value.textValue()
                                        .matches("-?(0|[1-9][0-9]{0,19})(\\.[0-9]{1,8})?"));
                    }
                    final BigDecimal exact =
                            value.isNumber()
                                    ? value.decimalValue()
                                    : new BigDecimal(value.textValue());
                    require(exact.scale() <= 8 && (long) exact.precision() - exact.scale() <= 20);
                    final BigDecimal parsed = exact.setScale(8, RoundingMode.UNNECESSARY);
                    require(parsed.precision() <= 28);
                    return parsed;
                },
                aliases);
    }

    public static ExpansionValue<RasterTime> time(
            final JsonNode node, final ZoneId zone, final String... aliases) {
        java.util.Objects.requireNonNull(zone);
        return parse(
                node,
                value -> {
                    require(value.isTextual());
                    final String raw = value.textValue();
                    if (raw.equals("1900-01-01")) {
                        return new RasterTime(null, null, true);
                    }
                    final String iso = raw.replace(' ', 'T');
                    try {
                        final var offset = OffsetDateTime.parse(iso);
                        if (offset.toLocalDate().equals(java.time.LocalDate.of(1900, 1, 1))) {
                            return new RasterTime(null, null, true);
                        }
                        return new RasterTime(
                                offset.toInstant(), offset.getOffset().getTotalSeconds(), false);
                    } catch (final DateTimeParseException noOffset) {
                        final var civil = LocalDateTime.parse(iso);
                        if (civil.toLocalDate().equals(java.time.LocalDate.of(1900, 1, 1))) {
                            return new RasterTime(null, null, true);
                        }
                        final var offsets = zone.getRules().getValidOffsets(civil);
                        require(offsets.size() == 1);
                        return new RasterTime(
                                civil.toInstant(offsets.get(0)),
                                offsets.get(0).getTotalSeconds(),
                                false);
                    }
                },
                aliases);
    }

    private static <T> ExpansionValue<T> parse(
            final JsonNode node, final Function<JsonNode, T> parser, final String... aliases) {
        final JsonNode value = select(node, aliases);
        if (value == null) {
            return new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null);
        }
        if (value.isNull()) {
            return new ExpansionValue<>(Presence.NULL, Wire.NULL, null, null, null);
        }
        final String raw = value.isTextual() ? value.textValue() : value.toString();
        require(raw.length() <= 4000);
        final Wire wire =
                value.isTextual()
                        ? Wire.STRING
                        : value.isIntegralNumber()
                                ? Wire.INTEGER
                                : value.isNumber()
                                        ? Wire.NUMBER
                                        : value.isBoolean()
                                                ? Wire.BOOLEAN
                                                : value.isArray() ? Wire.ARRAY : Wire.OBJECT;
        try {
            return new ExpansionValue<>(Presence.VALUE, wire, raw, parser.apply(value), null);
        } catch (final IllegalArgumentException
                | ArithmeticException
                | java.time.DateTimeException failure) {
            return new ExpansionValue<>(Presence.VALUE, wire, raw, null, "RAS_INVALID_FIELD");
        }
    }

    private static void require(final boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("RAS_FIELD_CONTRACT");
        }
    }
}

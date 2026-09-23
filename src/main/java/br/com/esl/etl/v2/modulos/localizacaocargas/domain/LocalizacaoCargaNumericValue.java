package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Parsers numéricos LOC-04: ASCII, sem locale, coerção ou arredondamento. */
public final class LocalizacaoCargaNumericValue {
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");

    private LocalizacaoCargaNumericValue() {}

    public static LocalizacaoCargaFieldValue<Integer> integer(
            final String path,
            final LocalizacaoCargaAttributePresence presence,
            final String rawJson,
            final String wireText) {
        if (presence != LocalizacaoCargaAttributePresence.VALUE) {
            return missing(path, presence);
        }
        Integer typed = null;
        if (wireText != null && wireText.length() <= 10 && INTEGER.matcher(wireText).matches()) {
            try {
                typed = Integer.valueOf(wireText);
            } catch (final NumberFormatException ignored) {
            }
        }
        return value(path, rawJson, typed);
    }

    public static LocalizacaoCargaFieldValue<BigDecimal> decimal(
            final String path,
            final LocalizacaoCargaAttributePresence presence,
            final String rawJson,
            final String wireText) {
        if (presence != LocalizacaoCargaAttributePresence.VALUE) {
            return missing(path, presence);
        }
        BigDecimal typed = null;
        if (wireText != null && DECIMAL.matcher(wireText).matches()) {
            try {
                final BigDecimal parsed = new BigDecimal(wireText);
                final long integerDigits = Math.max(0L, (long) parsed.precision() - parsed.scale());
                if (parsed.scale() <= 9 && parsed.precision() <= 38 && integerDigits <= 29) {
                    typed = parsed;
                }
            } catch (final NumberFormatException ignored) {
            }
        }
        return value(path, rawJson, typed);
    }

    private static <T> LocalizacaoCargaFieldValue<T> missing(
            final String path, final LocalizacaoCargaAttributePresence presence) {
        return new LocalizacaoCargaFieldValue<>(
                presence,
                path,
                null,
                presence == LocalizacaoCargaAttributePresence.ABSENT
                        ? LocalizacaoCargaParseState.NOT_PRESENT
                        : LocalizacaoCargaParseState.EXPLICIT_NULL,
                null,
                "DATAEXPORT_8656");
    }

    private static <T> LocalizacaoCargaFieldValue<T> value(
            final String path, final String rawJson, final T typed) {
        return new LocalizacaoCargaFieldValue<>(
                LocalizacaoCargaAttributePresence.VALUE,
                path,
                rawJson,
                typed == null
                        ? LocalizacaoCargaParseState.INVALID
                        : LocalizacaoCargaParseState.VALID,
                typed,
                "DATAEXPORT_8656");
    }
}

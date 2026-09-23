package br.com.esl.etl.v2.modulos.localizacaocargas.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.List;

/** Parser temporal sem heurística de locale; datas civis usam America/Sao_Paulo. */
public final class LocalizacaoCargaTemporalValue {
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String PATH = "/service_at";

    private LocalizacaoCargaTemporalValue() {}

    public static LocalizacaoCargaFieldValue<Instant> absent() {
        return new LocalizacaoCargaFieldValue<>(
                LocalizacaoCargaAttributePresence.ABSENT,
                PATH,
                null,
                LocalizacaoCargaParseState.NOT_PRESENT,
                null,
                "DATAEXPORT_8656");
    }

    public static LocalizacaoCargaFieldValue<Instant> explicitNull() {
        return new LocalizacaoCargaFieldValue<>(
                LocalizacaoCargaAttributePresence.NULL,
                PATH,
                null,
                LocalizacaoCargaParseState.EXPLICIT_NULL,
                null,
                "DATAEXPORT_8656");
    }

    public static LocalizacaoCargaFieldValue<Instant> parse(
            final String rawJson, final String wireText) {
        final Instant parsed = strictInstant(wireText);
        return new LocalizacaoCargaFieldValue<>(
                LocalizacaoCargaAttributePresence.VALUE,
                PATH,
                rawJson,
                parsed == null
                        ? LocalizacaoCargaParseState.INVALID
                        : LocalizacaoCargaParseState.VALID,
                parsed,
                "DATAEXPORT_8656");
    }

    private static Instant strictInstant(final String raw) {
        if (raw == null || raw.isEmpty() || !raw.equals(raw.trim())) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return atUnambiguousSourceOffset(
                    LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (final DateTimeParseException ignored) {
        }
        try {
            return atUnambiguousSourceOffset(
                    LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay());
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }

    private static Instant atUnambiguousSourceOffset(final LocalDateTime value) {
        final ZoneRules rules = SOURCE_ZONE.getRules();
        final List<java.time.ZoneOffset> offsets = rules.getValidOffsets(value);
        return offsets.size() == 1 ? value.toInstant(offsets.get(0)) : null;
    }
}

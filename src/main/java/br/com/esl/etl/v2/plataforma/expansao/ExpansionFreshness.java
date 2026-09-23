package br.com.esl.etl.v2.plataforma.expansao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Exact comparable boundary: a civil day ends exclusively at the next local day's start. */
public record ExpansionFreshness(long second, int nano, boolean exclusive)
        implements Comparable<ExpansionFreshness> {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public ExpansionFreshness {
        if (nano < 0 || nano > 999_999_999) {
            throw new IllegalArgumentException("EXP_NANO_BOUND");
        }
    }

    public static ExpansionFreshness instant(final Instant value) {
        return value == null
                ? null
                : new ExpansionFreshness(value.getEpochSecond(), value.getNano(), false);
    }

    public static ExpansionFreshness civilEnd(final LocalDate value) {
        if (value == null) {
            return null;
        }
        final Instant boundary = value.plusDays(1).atStartOfDay(ZONE).toInstant();
        return new ExpansionFreshness(boundary.getEpochSecond(), boundary.getNano(), true);
    }

    public static ExpansionFreshness civilStart(final LocalDate value) {
        return value == null ? null : instant(value.atStartOfDay(ZONE).toInstant());
    }

    public static ExpansionFreshness maximum(final ExpansionFreshness... values) {
        if (values.length > 8) {
            throw new IllegalArgumentException("EXP_FRESHNESS_BOUND");
        }
        ExpansionFreshness result = null;
        for (final var value : values) {
            if (value != null && (result == null || value.compareTo(result) > 0)) {
                result = value;
            }
        }
        return result;
    }

    @Override
    public int compareTo(final ExpansionFreshness other) {
        final int seconds = Long.compare(second, other.second);
        if (seconds != 0) {
            return seconds;
        }
        final int nanos = Integer.compare(nano, other.nano);
        return nanos != 0 ? nanos : Boolean.compare(other.exclusive, exclusive);
    }
}

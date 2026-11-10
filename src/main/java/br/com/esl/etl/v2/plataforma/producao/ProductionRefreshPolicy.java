package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Bounded work per invocation; a historical floor is never a rolling retention policy. */
public record ProductionRefreshPolicy(
        LocalDate requestedStart,
        int initialBatchDays,
        int reconciliationBatchDays,
        int changedDaysPerBatch,
        int manifestScanDays) {
    public ProductionRefreshPolicy {
        if (requestedStart == null
                || initialBatchDays < 1
                || initialBatchDays > 8
                || reconciliationBatchDays < 1
                || reconciliationBatchDays > 8
                || changedDaysPerBatch < 1
                || changedDaysPerBatch > 8
                || manifestScanDays < 1
                || manifestScanDays > 120) {
            throw new IllegalArgumentException("REFRESH_POLICY_BOUND");
        }
    }

    public static ProductionRefreshPolicy from(final JsonNode config, final LocalDate today) {
        final int days = integer(config, "historyDays", 90, 90, 3660);
        final var start =
                config.hasNonNull("historyStart")
                        ? LocalDate.parse(config.required("historyStart").asText())
                        : today.minusDays(days - 1L);
        if (ChronoUnit.DAYS.between(start, today) < 89
                || ChronoUnit.DAYS.between(start, today) > 3660) {
            throw new IllegalArgumentException("REFRESH_MINIMUM_NINETY_DAYS");
        }
        if (integer(config, "waitAfterCompletionMinutes", 60, 60, 60) != 60) {
            throw new IllegalArgumentException("REFRESH_SIXTY_MINUTES_REQUIRED");
        }
        return new ProductionRefreshPolicy(
                start,
                integer(config, "initialBatchDays", 8, 1, 8),
                integer(config, "reconciliationBatchDays", 8, 1, 8),
                integer(config, "changedDaysPerBatch", 8, 1, 8),
                integer(config, "manifestScanDays", 120, 1, 120));
    }

    private static int integer(
            final JsonNode config,
            final String field,
            final int fallback,
            final int minimum,
            final int maximum) {
        final var value = config.get(field);
        if (value == null) {
            return fallback;
        }
        if (!value.isIntegralNumber()
                || !value.canConvertToInt()
                || value.intValue() < minimum
                || value.intValue() > maximum) {
            throw new IllegalArgumentException("REFRESH_CONFIGURATION_INTEGER");
        }
        return value.intValue();
    }

    public record UpdateWindow(SourceDateTimeRange query, Instant commitThrough) {}

    public static UpdateWindow nextWindow(
            final Instant committed, final Instant cutoff, final boolean dateOnly) {
        return nextWindow(committed, cutoff, dateOnly, 1);
    }

    public static UpdateWindow nextWindow(
            final Instant committed,
            final Instant cutoff,
            final boolean dateOnly,
            final int maximumHours) {
        if (maximumHours < 1 || maximumHours > 24) {
            throw new IllegalArgumentException("REFRESH_UPDATE_WINDOW_BOUND");
        }
        if (committed.getNano() != 0 || cutoff.getNano() != 0) {
            throw new IllegalArgumentException("REFRESH_SECOND_PRECISION_REQUIRED");
        }
        if (!committed.isBefore(cutoff)) {
            return null;
        }
        if (!dateOnly) {
            final var end = committed.plus(Duration.ofHours(maximumHours));
            final var through = end.isAfter(cutoff) ? cutoff : end;
            return new UpdateWindow(
                    new SourceDateTimeRange(committed.minusSeconds(120), through), through);
        }
        final var day = committed.atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
        final var cutoffDay = cutoff.atZone(ReportUpdateContract.SOURCE_ZONE).toLocalDate();
        final var endDay = day.plusDays(1).isAfter(cutoffDay) ? cutoffDay : day.plusDays(1);
        final var nextDay =
                endDay.plusDays(1).atStartOfDay(ReportUpdateContract.SOURCE_ZONE).toInstant();
        return new UpdateWindow(
                new SourceDateTimeRange(
                        day.minusDays(1).atStartOfDay(ReportUpdateContract.SOURCE_ZONE).toInstant(),
                        nextDay.minusSeconds(1)),
                nextDay.isAfter(cutoff) ? cutoff : nextDay);
    }

    public static Instant nextInvocation(final Instant completed) {
        return completed.plus(Duration.ofMinutes(60));
    }
}

package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Partition instants and inclusive source dates stay distinct, including a declared lookback. */
public record LaboratoryCaptureWindow(
        Instant start,
        Instant endExclusive,
        BusinessDateRange dates,
        RuntimeWindowStrategy strategy) {
    public LaboratoryCaptureWindow {
        Objects.requireNonNull(start);
        Objects.requireNonNull(endExclusive);
        Objects.requireNonNull(dates);
        Objects.requireNonNull(strategy);
        if (!start.isBefore(endExclusive)
                || Duration.between(start, endExclusive).compareTo(Duration.ofDays(32)) > 0
                || dates.startInclusive().plusDays(32).isBefore(dates.endInclusive())) {
            throw new IllegalArgumentException("QUAL_CAPTURE_WINDOW_BOUND");
        }
    }

    static LaboratoryCaptureWindow day(
            final LocalDate date, final ZoneId zone, final RuntimeWindowStrategy strategy) {
        return new LaboratoryCaptureWindow(
                date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
                new BusinessDateRange(date, date),
                strategy);
    }

    static LaboratoryCaptureWindow planned(
            final RuntimeTemporalPlanner.Window window, final ZoneId zone) {
        return new LaboratoryCaptureWindow(
                window.partitionStart(),
                window.endExclusive(),
                new BusinessDateRange(
                        window.extractionStart().atZone(zone).toLocalDate(),
                        window.endExclusive().minusNanos(1).atZone(zone).toLocalDate()),
                RuntimeWindowStrategy.INTERVAL);
    }
}

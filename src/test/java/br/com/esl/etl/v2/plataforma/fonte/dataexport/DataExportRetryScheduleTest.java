package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExportRetryScheduleTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void respectsDeltaAndRfc1123RetryAfter() {
        final DataExportRetrySchedule schedule = schedule(Duration.ofSeconds(1), 0.0d);

        final DataExportRetryDelay delta = schedule.resolve(1, Optional.of("3"));
        assertEquals(Duration.ofSeconds(3), delta.duration());
        assertEquals(DataExportRetryDelaySource.RETRY_AFTER_DELTA, delta.source());
        assertTrue(delta.serverDirected());

        final DataExportRetryDelay date =
                schedule.resolve(1, Optional.of("Sun, 30 Aug 2026 15:00:04 GMT"));
        assertEquals(Duration.ofSeconds(4), date.duration());
        assertEquals(DataExportRetryDelaySource.RETRY_AFTER_DATE, date.source());
    }

    @Test
    void fallsBackForInvalidHeaderAndAppliesDeterministicCappedJitter() {
        final DataExportRetrySchedule schedule = schedule(Duration.ofSeconds(1), 0.5d);

        final DataExportRetryDelay delay = schedule.resolve(1, Optional.of("invalid"));

        assertEquals(Duration.ofMillis(1_100), delay.duration());
        assertEquals(DataExportRetryDelaySource.BACKOFF, delay.source());
        assertEquals(Duration.ofSeconds(5), schedule.resolve(10, Optional.empty()).duration());
    }

    @Test
    void keepsServerProvenanceWhenLocalBackoffIsLonger() {
        final DataExportRetrySchedule schedule = schedule(Duration.ofSeconds(2), 0.0d);

        final DataExportRetryDelay delay = schedule.resolve(1, Optional.of("1"));

        assertEquals(Duration.ofSeconds(2), delay.duration());
        assertEquals(DataExportRetryDelaySource.BACKOFF, delay.source());
        assertTrue(delay.serverDirected());
    }

    @Test
    void rejectsRetryAfterAboveEffectiveCapWithoutEchoingIt() {
        final DataExportRetrySchedule schedule =
                new DataExportRetrySchedule(
                        new DataExportRetryPolicy(3, Duration.ofMillis(10), Duration.ofSeconds(5)),
                        CLOCK,
                        () -> 0.0d,
                        Duration.ofSeconds(2));

        final DataExportRetryAfterLimitExceededException delta =
                assertThrows(
                        DataExportRetryAfterLimitExceededException.class,
                        () -> schedule.resolve(1, Optional.of("3")));
        assertTrue(delta.getMessage().contains("excede"));
        assertThrows(
                DataExportRetryAfterLimitExceededException.class,
                () ->
                        schedule.resolve(
                                1, Optional.of("999999999999999999999999999999999999999999999")));
        assertThrows(
                DataExportRetryAfterLimitExceededException.class,
                () -> schedule.resolve(1, Optional.of("Sun, 30 Aug 2026 15:00:03 GMT")));
    }

    @Test
    void rejectsInvalidJitterSource() {
        final DataExportRetrySchedule schedule = schedule(Duration.ofSeconds(1), 1.0d);

        assertThrows(IllegalStateException.class, () -> schedule.resolve(1, Optional.empty()));
    }

    private static DataExportRetrySchedule schedule(
            final Duration initialDelay, final double jitter) {
        return new DataExportRetrySchedule(
                new DataExportRetryPolicy(4, initialDelay, Duration.ofSeconds(5)),
                CLOCK,
                () -> jitter);
    }
}

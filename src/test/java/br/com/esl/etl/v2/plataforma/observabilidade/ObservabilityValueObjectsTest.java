package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ObservabilityValueObjectsTest {

    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void hashesIdentifiersBeforeCorrelationAndNeverRendersTheOriginal() {
        final UUID executionId = UUID.fromString("00000000-0000-4000-8000-000000000625");
        final CorrelationReference first = CorrelationReference.fromExecutionId(executionId);
        final CorrelationReference second = CorrelationReference.fromExecutionId(executionId);

        assertEquals(first, second);
        assertEquals(64, first.sha256().length());
        assertFalse(first.toString().contains(executionId.toString()));
        assertThrows(
                IllegalArgumentException.class, () -> new CorrelationReference("A".repeat(64)));
        assertThrows(
                IllegalArgumentException.class, () -> CorrelationReference.fromTechnicalScope(" "));
    }

    @Test
    void validatesAlertsAndHealthWithoutFreeText() {
        final CorrelationReference correlation =
                CorrelationReference.fromTechnicalScope("ALERT_SCOPE");
        final OperationalAlert alert =
                new OperationalAlert(
                        correlation,
                        1,
                        AlertSeverity.CRITICAL,
                        "DQ_FAILED",
                        "quality-owner",
                        2,
                        NOW);
        assertEquals("quality-owner", alert.ownerRole());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalAlert(
                                correlation,
                                0,
                                AlertSeverity.INFO,
                                "DQ_FAILED",
                                "quality-owner",
                                1,
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalAlert(
                                correlation,
                                1,
                                AlertSeverity.INFO,
                                "DQ_FAILED",
                                "Named Person",
                                1,
                                NOW));

        final PlatformHealthSnapshot up =
                new PlatformHealthSnapshot(
                        PlatformHealthStatus.UP, "PLATFORM_READY", 0, 0, 0, 0, NOW);
        assertEquals(PlatformHealthStatus.UP, up.status());
        assertEquals(
                "DQ_INCOMPLETE",
                new PlatformHealthSnapshot(
                                PlatformHealthStatus.DOWN, "DQ_INCOMPLETE", 1, 1, 1, 1, NOW)
                        .reasonCode());
        assertEquals(
                "DQ_FAILED",
                new PlatformHealthSnapshot(PlatformHealthStatus.DOWN, "DQ_FAILED", 0, 1, 1, 1, NOW)
                        .reasonCode());
        assertEquals(
                "QUARANTINE_SLA_EXCEEDED",
                new PlatformHealthSnapshot(
                                PlatformHealthStatus.DOWN,
                                "QUARANTINE_SLA_EXCEEDED",
                                0,
                                0,
                                1,
                                1,
                                NOW)
                        .reasonCode());
        assertEquals(
                PlatformHealthStatus.DEGRADED,
                new PlatformHealthSnapshot(
                                PlatformHealthStatus.DEGRADED, "RUNNING_STALE", 0, 0, 0, 1, NOW)
                        .status());
        assertEquals(
                PlatformHealthStatus.DOWN,
                PlatformHealthSnapshot.down("SQL_UNAVAILABLE", NOW).status());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformHealthSnapshot(
                                PlatformHealthStatus.UP, "PLATFORM_READY", 0, 1, 0, 0, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformHealthSnapshot(
                                PlatformHealthStatus.DEGRADED, "RUNNING_STALE", 0, 1, 0, 1, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformHealthSnapshot(
                                PlatformHealthStatus.DOWN, "PLATFORM_READY", 0, 0, 0, 0, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlatformHealthSnapshot(
                                PlatformHealthStatus.DOWN, "SQL_UNAVAILABLE", -1, 0, 0, 0, NOW));
    }

    @Test
    void rejectsInstantsThatWouldBeRoundedOrOverflowSqlServerDatetime() {
        final CorrelationReference correlation =
                CorrelationReference.fromTechnicalScope("TIME_SCOPE");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalAlert(
                                correlation,
                                1,
                                AlertSeverity.WARNING,
                                "CLOCK_INVALID",
                                "operations-owner",
                                1,
                                NOW.plusNanos(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OperationalAlert(
                                correlation,
                                1,
                                AlertSeverity.WARNING,
                                "CLOCK_INVALID",
                                "operations-owner",
                                1,
                                Instant.parse("+10000-01-01T00:00:00Z")));
        assertThrows(
                IllegalArgumentException.class,
                () -> zeroMetric(Optional.empty(), NOW.plusNanos(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> zeroMetric(Optional.of(NOW.minusNanos(1)), NOW));
    }

    private static ExecutionMetricsSnapshot zeroMetric(
            final Optional<Instant> watermark, final Instant capturedAt) {
        return new ExecutionMetricsSnapshot(
                UUID.fromString("00000000-0000-4000-8000-000000000627"),
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                watermark,
                capturedAt);
    }
}

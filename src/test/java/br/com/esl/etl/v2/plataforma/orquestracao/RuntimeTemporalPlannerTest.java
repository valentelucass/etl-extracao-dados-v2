package br.com.esl.etl.v2.plataforma.orquestracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeTemporalPlannerTest {
    private static RuntimeTemporalPolicy policy(
            final String zone,
            final LocalTime boundary,
            final RuntimeTemporalPolicy.Cadence cadence,
            final int backlog,
            final List<RuntimeTemporalPolicy.Blackout> blackouts) {
        return new RuntimeTemporalPolicy(
                "synthetic-temporal-v1",
                ZoneId.of(zone),
                ExecutionMode.BACKFILL,
                RuntimeWindowStrategy.INTERVAL,
                cadence,
                boundary,
                Duration.ofHours(2),
                Duration.ofHours(1),
                Duration.ofHours(4),
                Duration.ofHours(2),
                1,
                backlog,
                16,
                1,
                blackouts);
    }

    @Test
    void previousCivilMonthUsesExclusiveEndAndLeapFebruary() {
        final var planner = new RuntimeTemporalPlanner();
        final var policy =
                policy(
                        "America/Sao_Paulo",
                        LocalTime.MIDNIGHT,
                        RuntimeTemporalPolicy.Cadence.CIVIL_MONTH,
                        3,
                        List.of());
        final var feb = planner.previousCivilMonth(policy, Instant.parse("2024-03-01T04:00:00Z"));
        assertEquals(Instant.parse("2024-02-01T03:00:00Z"), feb.partitionStart());
        assertEquals(Instant.parse("2024-03-01T03:00:00Z"), feb.endExclusive());
        assertEquals(
                Duration.ofDays(29), Duration.between(feb.partitionStart(), feb.endExclusive()));
        final var dec = planner.previousCivilMonth(policy, Instant.parse("2025-01-01T04:00:00Z"));
        assertEquals(Instant.parse("2024-12-01T03:00:00Z"), dec.partitionStart());
        assertEquals(dec.partitionStart().minusSeconds(7200), dec.extractionStart());
        assertEquals(dec.endExclusive().plusSeconds(3600), dec.dueAt());
        assertEquals(dec.dueAt().plusSeconds(7200), dec.deadlineAt());
        assertFalse(policy.toString().contains("America"));
    }

    @Test
    void catchesUpInOrderAndExposesUnconsumedBacklogWithoutSkippingBlackout() {
        final var planner = new RuntimeTemporalPlanner();
        final var daily =
                policy(
                        "Etc/UTC",
                        LocalTime.MIDNIGHT,
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        2,
                        List.of());
        final var result =
                planner.plan(
                        daily, LocalDate.of(2026, 1, 1), Instant.parse("2026-01-10T12:00:00Z"));
        assertEquals(2, result.windows().size());
        assertTrue(result.backlogRemaining());
        assertFalse(result.blockedByBlackout());
        assertEquals(
                result.windows().get(0).endExclusive(), result.windows().get(1).partitionStart());
        final var blackout =
                new RuntimeTemporalPolicy.Blackout(
                        LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));
        final var blocked =
                planner.plan(
                        policy(
                                "Etc/UTC",
                                LocalTime.MIDNIGHT,
                                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                                4,
                                List.of(blackout)),
                        LocalDate.of(2026, 1, 1),
                        Instant.parse("2026-01-10T12:00:00Z"));
        assertEquals(1, blocked.windows().size());
        assertTrue(blocked.blockedByBlackout());
        assertTrue(blocked.backlogRemaining());
        assertThrows(UnsupportedOperationException.class, () -> result.windows().clear());
    }

    @Test
    void refusesDstGapAndOverlapInsteadOfSilentlyChoosingAnOffset() {
        final var planner = new RuntimeTemporalPlanner();
        final var gap =
                policy(
                        "America/New_York",
                        LocalTime.of(2, 30),
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        4,
                        List.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planner.plan(
                                gap,
                                LocalDate.of(2024, 3, 9),
                                Instant.parse("2024-03-15T12:00:00Z")));
        final var overlap =
                policy(
                        "America/New_York",
                        LocalTime.of(1, 30),
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        4,
                        List.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planner.plan(
                                overlap,
                                LocalDate.of(2024, 11, 2),
                                Instant.parse("2024-11-08T12:00:00Z")));
        final var midnight =
                policy(
                        "America/New_York",
                        LocalTime.MIDNIGHT,
                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                        4,
                        List.of());
        assertEquals(
                23,
                Duration.between(
                                planner.plan(
                                                midnight,
                                                LocalDate.of(2024, 3, 10),
                                                Instant.parse("2024-03-11T12:00:00Z"))
                                        .windows()
                                        .get(0)
                                        .partitionStart(),
                                Instant.parse("2024-03-11T04:00:00Z"))
                        .toHours());
    }

    @Test
    void honorsStabilizationAndRejectsInvalidBoundaries() {
        final var planner = new RuntimeTemporalPlanner();
        final var monthly =
                policy(
                        "Etc/UTC",
                        LocalTime.MIDNIGHT,
                        RuntimeTemporalPolicy.Cadence.CIVIL_MONTH,
                        4,
                        List.of());
        assertThrows(
                IllegalStateException.class,
                () -> planner.previousCivilMonth(monthly, Instant.parse("2026-02-01T00:30:00Z")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planner.plan(
                                monthly,
                                LocalDate.of(2026, 1, 2),
                                Instant.parse("2026-02-01T12:00:00Z")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planner.previousCivilMonth(
                                policy(
                                        "Etc/UTC",
                                        LocalTime.MIDNIGHT,
                                        RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                                        2,
                                        List.of()),
                                Instant.now()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        policy(
                                "Etc/UTC",
                                LocalTime.MIDNIGHT,
                                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                                0,
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        policy(
                                "UTC",
                                LocalTime.MIDNIGHT,
                                RuntimeTemporalPolicy.Cadence.CIVIL_DAY,
                                2,
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeTemporalPolicy.Blackout(LocalDate.now(), LocalDate.now()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeTemporalPlanner.Window(
                                Instant.EPOCH,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                Instant.EPOCH));
    }
}

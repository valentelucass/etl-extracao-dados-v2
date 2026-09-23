package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedWindowRepartitionerTest {

    @Test
    void splitsProven422IntoSmallerContiguousAlignedWindows() {
        final BoundedWindowRepartitioner repartitioner = repartitioner(2, Duration.ofDays(1));
        final RepartitionWindow original =
                new RepartitionWindow(
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-05T00:00:00Z"));

        final RepartitionSplit split =
                repartitioner.split(FailureKind.WINDOW_TOO_LARGE_HTTP_422, original);

        assertEquals(split.first().endExclusive(), split.second().start());
        assertEquals(original.start(), split.first().start());
        assertEquals(original.endExclusive(), split.second().endExclusive());
        assertTrue(split.first().duration().compareTo(original.duration()) < 0);
        assertEquals(1, repartitioner.usedRepartitions());
    }

    @Test
    void refusesUnclassified422MisalignmentMinimumAndExhaustedBudget() {
        final Instant start = Instant.parse("2026-08-01T00:00:00Z");
        final BoundedWindowRepartitioner repartitioner = repartitioner(1, Duration.ofDays(1));
        assertReason(
                RepartitionRefusalReason.CATEGORY_NOT_PROVEN,
                () ->
                        repartitioner.split(
                                FailureKind.UNCLASSIFIED_HTTP_422,
                                new RepartitionWindow(start, start.plus(Duration.ofDays(4)))));
        assertReason(
                RepartitionRefusalReason.WINDOW_NOT_ALIGNED,
                () ->
                        repartitioner.split(
                                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                new RepartitionWindow(start, start.plus(Duration.ofHours(25)))));
        assertReason(
                RepartitionRefusalReason.MINIMUM_PARTITION_REACHED,
                () ->
                        repartitioner.split(
                                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                new RepartitionWindow(start, start.plus(Duration.ofDays(1)))));

        repartitioner.split(
                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                new RepartitionWindow(start, start.plus(Duration.ofDays(2))));
        assertReason(
                RepartitionRefusalReason.BUDGET_EXHAUSTED,
                () ->
                        repartitioner.split(
                                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                new RepartitionWindow(start, start.plus(Duration.ofDays(2)))));
    }

    @Test
    void validatesWindowAndUnit() {
        final Instant instant = Instant.parse("2026-08-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new RepartitionWindow(instant, instant));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedWindowRepartitioner(1, Duration.ZERO));
        assertReason(
                RepartitionRefusalReason.WINDOW_OUT_OF_RANGE,
                () ->
                        new BoundedWindowRepartitioner(1, Duration.ofNanos(1))
                                .split(
                                        FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                        new RepartitionWindow(Instant.MIN, Instant.MAX)));
    }

    @Test
    void repeatedSplitsTerminateWithExactSemiOpenCoverageForEverySupportedUnitCount() {
        final Instant start = Instant.parse("2026-08-01T00:00:00Z");
        final Duration unit = Duration.ofMinutes(1);

        for (int unitCount = 2;
                unitCount <= EslResiliencePolicy.MAX_REPARTITIONS + 1;
                unitCount++) {
            final RepartitionWindow original =
                    new RepartitionWindow(start, start.plus(unit.multipliedBy(unitCount)));
            final BoundedWindowRepartitioner repartitioner = repartitioner(unitCount - 1, unit);
            final ArrayDeque<RepartitionWindow> pending = new ArrayDeque<>();
            final List<RepartitionWindow> leaves = new ArrayList<>();
            pending.add(original);

            while (!pending.isEmpty()) {
                final RepartitionWindow candidate = pending.removeFirst();
                if (candidate.duration().equals(unit)) {
                    leaves.add(candidate);
                } else {
                    final RepartitionSplit split =
                            repartitioner.split(FailureKind.WINDOW_TOO_LARGE_HTTP_422, candidate);
                    pending.add(split.first());
                    pending.add(split.second());
                }
            }

            leaves.sort(Comparator.comparing(RepartitionWindow::start));
            assertEquals(unitCount, leaves.size());
            assertEquals(unitCount - 1, repartitioner.usedRepartitions());
            Instant cursor = original.start();
            for (final RepartitionWindow leaf : leaves) {
                assertEquals(cursor, leaf.start());
                assertEquals(unit, leaf.duration());
                assertTrue(leaf.start().isBefore(leaf.endExclusive()));
                cursor = leaf.endExclusive();
            }
            assertEquals(original.endExclusive(), cursor);
        }
    }

    private static BoundedWindowRepartitioner repartitioner(final int budget, final Duration unit) {
        return new BoundedWindowRepartitioner(budget, unit);
    }

    private static void assertReason(
            final RepartitionRefusalReason reason, final Runnable operation) {
        final RepartitionRefusedException exception =
                assertThrows(RepartitionRefusedException.class, operation::run);
        assertEquals(reason, exception.reason());
    }
}

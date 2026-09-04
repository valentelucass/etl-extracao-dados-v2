package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ExecutionMetricsAccumulatorTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000624");
    private static final Instant NOW = Instant.parse("2026-08-31T10:00:00Z");

    @Test
    void accumulatesOnlyFixedReconciledDimensionsAndCreatesAnImmutableSnapshot() {
        final ExecutionMetricsAccumulator metrics = new ExecutionMetricsAccumulator(EXECUTION_ID);
        metrics.recordPage(100, 4);
        metrics.recordPage(20, 1);
        metrics.recordStagingSummary(4, 1, 3, 1, 0, 1, 3);
        metrics.recordApplication(1, 1, 0, 1, 1);
        metrics.recordProgress(5_000, Optional.of(NOW.minus(1, ChronoUnit.HOURS)));
        metrics.recordRetry(false);
        metrics.recordRetry(true);

        final ExecutionMetricsSnapshot snapshot = metrics.snapshot(1, 250, NOW);

        assertEquals(EXECUTION_ID, snapshot.executionId());
        assertEquals(2, snapshot.pages());
        assertEquals(120, snapshot.responseBytes());
        assertEquals(5, snapshot.physicalRows());
        assertEquals(4, snapshot.distinctRootKeys());
        assertEquals(1, snapshot.duplicateRows());
        assertEquals(3, snapshot.validRows());
        assertEquals(1, snapshot.quarantinedRootKeys());
        assertEquals(1, snapshot.quarantinedStageRows());
        assertEquals(3, snapshot.candidateRows());
        assertEquals(1, snapshot.staleNoopRows());
        assertEquals(2, snapshot.retryAttempts());
        assertEquals(1, snapshot.rateLimitResponses());
        assertEquals(5_000, snapshot.sourceLagMilliseconds());
        assertTrue(snapshot.watermark().isPresent());
        assertFalse(snapshot.toString().contains(EXECUTION_ID.toString()));
    }

    @Test
    void rejectsPartialDivergentRepeatedAndOverflowedSummaries() {
        final ExecutionMetricsAccumulator metrics = new ExecutionMetricsAccumulator(EXECUTION_ID);
        assertThrows(IllegalArgumentException.class, () -> metrics.recordPage(-1, 0));
        assertThrows(IllegalStateException.class, () -> metrics.snapshot(1, 0, NOW));

        metrics.recordPage(1, 1);
        metrics.recordStagingSummary(1, 0, 1, 0, 0, 0, 1);
        assertThrows(
                IllegalStateException.class,
                () -> metrics.recordStagingSummary(1, 0, 1, 0, 0, 0, 1));
        metrics.recordApplication(1, 0, 0, 0, 0);
        metrics.recordProgress(0, Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionMetricsSnapshot(
                                EXECUTION_ID,
                                1,
                                0,
                                1,
                                1,
                                1,
                                1,
                                0,
                                1,
                                1,
                                0,
                                1,
                                1,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                0,
                                Optional.empty(),
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionMetricsSnapshot(
                                EXECUTION_ID,
                                1,
                                0,
                                0,
                                0,
                                1,
                                1,
                                0,
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
                                Optional.empty(),
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionMetricsSnapshot(
                                EXECUTION_ID,
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
                                0,
                                Optional.empty(),
                                NOW));

        final ExecutionMetricsAccumulator overflowing =
                new ExecutionMetricsAccumulator(EXECUTION_ID);
        overflowing.recordPage(Long.MAX_VALUE, 0);
        assertThrows(ArithmeticException.class, () -> overflowing.recordPage(1, 0));
        overflowing.recordStagingSummary(0, 0, 0, 0, 0, 0, 0);
        overflowing.recordApplication(0, 0, 0, 0, 0);
        overflowing.recordProgress(0, Optional.empty());
        final ExecutionMetricsSnapshot afterOverflow = overflowing.snapshot(1, 0, NOW);
        assertEquals(1, afterOverflow.pages());
        assertEquals(Long.MAX_VALUE, afterOverflow.responseBytes());
    }

    @Test
    void serializesConcurrentPageAndRetryCountersWithoutLostUpdates()
            throws InterruptedException, ExecutionException {
        final ExecutionMetricsAccumulator metrics = new ExecutionMetricsAccumulator(EXECUTION_ID);
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                final boolean rateLimited = index % 2 == 0;
                tasks.add(
                        () -> {
                            metrics.recordPage(1, 1);
                            metrics.recordRetry(rateLimited);
                            return null;
                        });
            }
            executor.invokeAll(tasks)
                    .forEach(
                            future -> {
                                try {
                                    future.get();
                                } catch (final InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(exception);
                                } catch (final ExecutionException exception) {
                                    throw new IllegalStateException(exception.getCause());
                                }
                            });
        } finally {
            executor.shutdownNow();
        }
        metrics.recordStagingSummary(100, 0, 100, 0, 0, 0, 100);
        metrics.recordApplication(0, 0, 0, 100, 0);
        metrics.recordProgress(0, Optional.empty());

        final ExecutionMetricsSnapshot snapshot = metrics.snapshot(1, 0, NOW);
        assertEquals(100, snapshot.pages());
        assertEquals(100, snapshot.responseBytes());
        assertEquals(100, snapshot.physicalRows());
        assertEquals(100, snapshot.retryAttempts());
        assertEquals(50, snapshot.rateLimitResponses());
    }
}

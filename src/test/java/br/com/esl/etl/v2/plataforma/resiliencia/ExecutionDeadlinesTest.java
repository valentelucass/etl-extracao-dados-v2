package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionDeadlinesTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void enforcesRequestStepAndCycleWithMonotonicSnapshots() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final ExecutionDeadlines deadlines =
                ExecutionDeadlines.start(Duration.ofSeconds(10), ticker, CancellationToken.none());
        final ExecutionDeadlines.Step step =
                deadlines.beginStep(Duration.ofSeconds(5), Duration.ofSeconds(1));
        final ExecutionDeadlines.Step.Request request = step.beginRequest();

        ticker.advance(Duration.ofSeconds(1));
        final ResilienceTimeoutException requestTimeout =
                assertThrows(ResilienceTimeoutException.class, request::remainingTime);
        assertEquals(ResilienceTimeoutScope.REQUEST, requestTimeout.scope());

        ticker.advance(Duration.ofSeconds(4));
        final ResilienceTimeoutException stepTimeout =
                assertThrows(ResilienceTimeoutException.class, step::checkpoint);
        assertEquals(ResilienceTimeoutScope.STEP, stepTimeout.scope());

        ticker.advance(Duration.ofSeconds(5));
        final ResilienceTimeoutException cycleTimeout =
                assertThrows(ResilienceTimeoutException.class, deadlines::checkpointCycle);
        assertEquals(ResilienceTimeoutScope.CYCLE, cycleTimeout.scope());
    }

    @Test
    void delayUsesActualTickerProgressAndChecksCancellation() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final CancellationSignal signal = new CancellationSignal();
        final ExecutionDeadlines.Step step =
                ExecutionDeadlines.start(Duration.ofSeconds(10), ticker, signal)
                        .beginStep(Duration.ofSeconds(5), Duration.ofSeconds(1));
        final AtomicInteger sleeps = new AtomicInteger();

        step.awaitDelay(
                Duration.ofMillis(120),
                requested -> {
                    final int attempt = sleeps.incrementAndGet();
                    ticker.advance(attempt == 1 ? requested.dividedBy(2) : requested);
                });
        assertTrue(sleeps.get() > 2);
        assertTrue(ticker.readNanos() >= Duration.ofMillis(120).toNanos());

        signal.cancel();
        assertThrows(
                ResilienceCancelledException.class,
                () -> step.awaitDelay(Duration.ofMillis(1), ticker::advance));
    }

    @Test
    void rejectsOverflowingDelayAndInterruptedSleep() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final ExecutionDeadlines.Step step =
                ExecutionDeadlines.start(Duration.ofSeconds(10), ticker, CancellationToken.none())
                        .beginStep(Duration.ofSeconds(5), Duration.ofSeconds(1));

        assertThrows(
                IllegalArgumentException.class,
                () -> step.awaitDelay(Duration.ofSeconds(Long.MAX_VALUE), ticker::advance));
        assertFalse(Thread.currentThread().isInterrupted());
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        step.awaitDelay(
                                Duration.ofMillis(1),
                                ignored -> {
                                    throw new InterruptedException("synthetic");
                                }));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void rejectsInvalidHierarchyAndTickerRegression() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final ExecutionDeadlines deadlines =
                ExecutionDeadlines.start(Duration.ofSeconds(5), ticker, CancellationToken.none());

        assertThrows(
                IllegalArgumentException.class,
                () -> deadlines.beginStep(Duration.ofSeconds(6), Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> deadlines.beginStep(Duration.ofSeconds(4), Duration.ofSeconds(5)));

        ticker.set(-1L);
        assertThrows(IllegalStateException.class, deadlines::checkpointCycle);
    }
}

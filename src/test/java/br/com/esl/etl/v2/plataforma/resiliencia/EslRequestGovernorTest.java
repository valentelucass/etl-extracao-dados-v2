package br.com.esl.etl.v2.plataforma.resiliencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EslRequestGovernorTest {

    @Test
    void serializesConcurrentWorkloadsAndReleasesPermitIdempotently() throws Exception {
        final EslRequestGovernor governor =
                new EslRequestGovernor(
                        ResilienceTestSupport.policy(),
                        MonotonicTicker.systemTicker(),
                        ResilienceSleeper.threadSleeper());
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(CancellationToken.none());
        final EslRequestGovernor.Cycle.Workload coletas = cycle.beginWorkload(EslWorkload.COLETAS);
        final EslRequestGovernor.Cycle.Workload fretes = cycle.beginWorkload(EslWorkload.FRETES);
        final EslRequestGovernor.RequestPermit first = coletas.acquire();
        final CountDownLatch secondStarted = new CountDownLatch(1);
        final CountDownLatch secondAcquired = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> second =
                    executor.submit(
                            () -> {
                                secondStarted.countDown();
                                try (EslRequestGovernor.RequestPermit permit = fretes.acquire()) {
                                    permit.checkpoint();
                                    secondAcquired.countDown();
                                }
                            });

            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
            assertFalse(secondAcquired.await(100, TimeUnit.MILLISECONDS));
            first.close();
            first.close();
            assertTrue(secondAcquired.await(2, TimeUnit.SECONDS));
            second.get(2, TimeUnit.SECONDS);
            assertEquals(1, governor.availablePermits());
            assertThrows(IllegalStateException.class, first::checkpoint);
        } finally {
            first.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void sharesSourceAndWorkloadBudgetsWithoutDeadlineReset() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final EslRequestGovernor governor =
                new EslRequestGovernor(
                        ResilienceTestSupport.policy(2, 1, 2), ticker, ticker::advance);
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(CancellationToken.none());
        final EslRequestGovernor.Cycle.Workload coletas = cycle.beginWorkload(EslWorkload.COLETAS);

        try (EslRequestGovernor.RequestPermit permit = coletas.acquire()) {
            permit.checkpoint();
            assertEquals(1, coletas.requests());
        }
        assertSame(coletas, cycle.beginWorkload(EslWorkload.COLETAS));
        final ResilienceBudgetExceededException workloadBudget =
                assertThrows(ResilienceBudgetExceededException.class, coletas::acquire);
        assertEquals(ResilienceBudgetScope.WORKLOAD, workloadBudget.scope());
        assertEquals(1, governor.availablePermits());

        final EslRequestGovernor.Cycle.Workload fretes = cycle.beginWorkload(EslWorkload.FRETES);
        assertNotSame(coletas, fretes);
        try (EslRequestGovernor.RequestPermit permit = fretes.acquire()) {
            permit.checkpoint();
            assertEquals(2, cycle.sourceRequests());
        }
        final ResilienceBudgetExceededException sourceBudget =
                assertThrows(
                        ResilienceBudgetExceededException.class,
                        cycle.beginWorkload(EslWorkload.MANIFESTOS)::acquire);
        assertEquals(ResilienceBudgetScope.SOURCE, sourceBudget.scope());
    }

    @Test
    void observesGlobalIntervalAndRateLimitEmbargo() {
        final ResilienceTestSupport.MutableTicker ticker =
                new ResilienceTestSupport.MutableTicker();
        final EslResiliencePolicy base = ResilienceTestSupport.policy();
        final EslResiliencePolicy policy =
                new EslResiliencePolicy(
                        Duration.ofMillis(10),
                        1,
                        base.maxRequestsPerCycle(),
                        base.maxRequestsPerWorkload(),
                        base.requestTimeout(),
                        base.stepTimeout(),
                        base.cycleTimeout(),
                        base.maxRetryAfter(),
                        base.maxRepartitions(),
                        base.circuitFailureThreshold(),
                        base.circuitCooldown());
        final EslRequestGovernor governor = new EslRequestGovernor(policy, ticker, ticker::advance);
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(CancellationToken.none());

        try (EslRequestGovernor.RequestPermit permit =
                cycle.beginWorkload(EslWorkload.COLETAS).acquire()) {
            permit.checkpoint();
            assertEquals(1, cycle.sourceRequests());
        }
        governor.imposeRateLimitEmbargo(Duration.ofMillis(25));
        try (EslRequestGovernor.RequestPermit permit =
                cycle.beginWorkload(EslWorkload.FRETES).acquire()) {
            permit.checkpoint();
            assertTrue(ticker.readNanos() >= Duration.ofMillis(25).toNanos());
        }
    }

    @Test
    void cancelsAWaiterAndDoesNotLeakThePermit() throws Exception {
        final CancellationSignal signal = new CancellationSignal();
        final EslRequestGovernor governor =
                new EslRequestGovernor(
                        ResilienceTestSupport.policy(),
                        MonotonicTicker.systemTicker(),
                        ResilienceSleeper.threadSleeper());
        final EslRequestGovernor.Cycle cycle = governor.beginCycle(signal);
        final EslRequestGovernor.RequestPermit held =
                cycle.beginWorkload(EslWorkload.COLETAS).acquire();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> waiting =
                    executor.submit(() -> cycle.beginWorkload(EslWorkload.FRETES).acquire());
            signal.cancel();
            final ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> waiting.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ResilienceCancelledException);
            held.close();
            assertEquals(1, governor.availablePermits());
        } finally {
            held.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void keepsOneRepartitionBudgetAndUnitPerWorkload() {
        final EslRequestGovernor.Cycle.Workload workload =
                new EslRequestGovernor(
                                ResilienceTestSupport.policy(),
                                MonotonicTicker.systemTicker(),
                                ResilienceSleeper.threadSleeper())
                        .beginCycle(CancellationToken.none())
                        .beginWorkload(EslWorkload.COLETAS);

        assertSame(
                workload.repartitioner(Duration.ofDays(1)),
                workload.repartitioner(Duration.ofDays(1)));
        assertThrows(
                IllegalStateException.class, () -> workload.repartitioner(Duration.ofHours(1)));
    }

    @Test
    void cancellationDominatesRepartitionWithoutConsumingItsBudget() {
        final CancellationSignal signal = new CancellationSignal();
        final EslRequestGovernor.Cycle.Workload workload =
                new EslRequestGovernor(
                                ResilienceTestSupport.policy(),
                                MonotonicTicker.systemTicker(),
                                ResilienceSleeper.threadSleeper())
                        .beginCycle(signal)
                        .beginWorkload(EslWorkload.COLETAS);
        final BoundedWindowRepartitioner repartitioner = workload.repartitioner(Duration.ofDays(1));
        final Instant start = Instant.parse("2026-08-01T00:00:00Z");
        signal.cancel();

        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        repartitioner.split(
                                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                                new RepartitionWindow(start, start.plus(Duration.ofDays(2)))));
        assertEquals(0, repartitioner.usedRepartitions());
    }
}

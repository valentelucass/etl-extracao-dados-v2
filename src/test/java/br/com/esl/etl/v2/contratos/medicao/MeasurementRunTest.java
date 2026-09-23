package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class MeasurementRunTest {

    @Test
    void executesAStreamerAroundTheRealPageConsumerAndBuildsAllThreeEnvelopes() {
        final MeasurementPlan plan = MeasurementPlan.graphQl(16);
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final AtomicReference<Object> lastProducedPage = new AtomicReference<>();
        final AtomicReference<Object> lastConsumedPage = new AtomicReference<>();
        final MeasurementStreamer<Object> streamer =
                consumer -> {
                    for (int pageNumber = 1; pageNumber <= plan.dataPages(); pageNumber++) {
                        final Object page = new Object();
                        lastProducedPage.set(page);
                        gauge.beforeFetch();
                        gauge.pageFetched(page, 32L);
                        gauge.pageConsumed(page, plan.recordsPerPage());
                        consumer.accept(page);
                        gauge.pageReleased(page);
                    }
                };

        final MeasurementRun run =
                MeasurementRun.execute(plan, gauge, streamer, lastConsumedPage::set);

        assertSame(lastProducedPage.get(), lastConsumedPage.get());
        assertEquals(16L, run.evidence().fetchedPages());
        assertEquals(128L, run.evidence().records());
        assertEquals(
                MeasurementAssessment.Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY,
                run.assessment().reason());
        assertTrue(run.diagnostics().heapBeforeBytes() >= 0L);
        assertTrue(run.diagnostics().heapAfterBytes() >= 0L);
        assertTrue(run.diagnostics().heapPeakBytes() >= run.diagnostics().heapBeforeBytes());
        assertTrue(run.diagnostics().heapPeakBytes() >= run.diagnostics().heapAfterBytes());
        assertTrue(run.diagnostics().durationNanos() >= 0L);
    }

    @Test
    void samplesHeapAroundEveryConsumedPageButNeverUsesItAsAnEvaluationGate() {
        final MeasurementPlan plan = MeasurementPlan.graphQl(16);
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final AtomicInteger heapSample = new AtomicInteger();
        final LongSupplier heapUsedBytes =
                () -> {
                    final int sample = heapSample.getAndIncrement();
                    if (sample == 0) {
                        return 100L;
                    }
                    if (sample == 1) {
                        return 200L;
                    }
                    if (sample == 33) {
                        return 125L;
                    }
                    return 150L;
                };
        final AtomicInteger nanoSample = new AtomicInteger();
        final LongSupplier nanoTime = () -> nanoSample.getAndIncrement() == 0 ? 10L : 42L;
        final MeasurementStreamer<Object> streamer =
                consumer -> {
                    for (int pageNumber = 1; pageNumber <= plan.dataPages(); pageNumber++) {
                        final Object page = new Object();
                        gauge.beforeFetch();
                        gauge.pageFetched(page, 32L);
                        gauge.pageConsumed(page, plan.recordsPerPage());
                        consumer.accept(page);
                        gauge.pageReleased(page);
                    }
                };

        final MeasurementRun run =
                MeasurementRun.execute(
                        plan,
                        gauge,
                        streamer,
                        ignored -> {},
                        new MeasurementEvaluator(),
                        nanoTime,
                        heapUsedBytes);

        assertEquals(34, heapSample.get());
        assertEquals(100L, run.diagnostics().heapBeforeBytes());
        assertEquals(125L, run.diagnostics().heapAfterBytes());
        assertEquals(200L, run.diagnostics().heapPeakBytes());
        assertEquals(32L, run.diagnostics().durationNanos());
        assertTrue(run.assessment().proven());
    }
}

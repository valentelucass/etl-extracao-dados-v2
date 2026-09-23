package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ExecutionWidePageRetentionMutantTest {

    @Test
    void retainsRealPageObjectsAcrossTheRunAndIsRejectedByGenericMetrics() throws Exception {
        final MeasurementPlan plan = MeasurementPlan.graphQl(16);
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final ExecutionWidePageRetentionMutant mutant = new ExecutionWidePageRetentionMutant(gauge);
        final AtomicReference<Object> firstRealPage = new AtomicReference<>();
        final MeasurementStreamer<Object> streamer =
                consumer -> {
                    for (int pageNumber = 1; pageNumber <= plan.dataPages(); pageNumber++) {
                        final Object realPage = new Object();
                        firstRealPage.compareAndSet(null, realPage);
                        gauge.beforeFetch();
                        gauge.pageFetched(realPage, 32L);
                        gauge.pageConsumed(realPage, plan.recordsPerPage());
                        consumer.accept(realPage);
                        gauge.pageReleased(realPage);
                    }
                };

        try {
            final MeasurementRun run = MeasurementRun.execute(plan, gauge, streamer, mutant);

            assertEquals(16, mutant.retainedPageCount());
            assertSame(firstRealPage.get(), mutant.retainedPageAt(0));
            final Field retainedPages =
                    ExecutionWidePageRetentionMutant.class.getDeclaredField("retainedPages");
            retainedPages.setAccessible(true);
            assertInstanceOf(ArrayList.class, retainedPages.get(mutant));
            assertEquals(1L, run.evidence().maxInFlightPages());
            assertEquals(0L, run.evidence().finalInFlightPages());
            assertEquals(16L, run.evidence().acquisitions());
            assertEquals(16L, run.evidence().releases());
            assertEquals(16L, run.evidence().maxRetainedPages());
            assertEquals(16L, run.evidence().finalRetainedPages());
            assertEquals(
                    MeasurementAssessment.Disposition.REJECTED, run.assessment().disposition());
            assertEquals(
                    MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
                    run.assessment().reason());
        } finally {
            mutant.clear();
        }

        assertEquals(0, mutant.retainedPageCount());
        final MeasurementEvidence cleared = gauge.snapshot(plan);
        assertEquals(16L, cleared.maxRetainedPages());
        assertEquals(0L, cleared.finalRetainedPages());
        assertEquals(
                MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
                new MeasurementEvaluator().evaluate(cleared).reason());
    }
}

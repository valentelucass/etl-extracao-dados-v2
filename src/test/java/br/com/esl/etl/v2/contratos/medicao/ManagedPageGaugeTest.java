package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedPageGaugeTest {

    @Test
    void reconcilesTheDataExportTerminalPageAndProvesAnExactPeakOfOne() {
        final MeasurementPlan plan = MeasurementPlan.dataExport(16);
        final ManagedPageGauge gauge = new ManagedPageGauge();

        for (int pageNumber = 1; pageNumber <= plan.dataPages(); pageNumber++) {
            final Object page = new Object();
            gauge.beforeFetch();
            gauge.pageFetched(page, 64L);
            gauge.pageConsumed(page, plan.recordsPerPage());
            gauge.pageReleased(page);
        }
        final Object terminalPage = new Object();
        gauge.beforeFetch();
        gauge.pageFetched(terminalPage, 2L);
        gauge.pageReleased(terminalPage);

        final MeasurementEvidence evidence = gauge.snapshot(plan);
        assertEquals(17L, evidence.fetchedPages());
        assertEquals(16L, evidence.consumedPages());
        assertEquals(128L, evidence.records());
        assertEquals(1_026L, evidence.bytes());
        assertEquals(1L, evidence.maxInFlightPages());
        assertEquals(0L, evidence.finalInFlightPages());
        assertEquals(17L, evidence.acquisitions());
        assertEquals(17L, evidence.releases());
        assertEquals(0L, evidence.maxRetainedPages());
        assertEquals(0L, evidence.finalRetainedPages());
    }

    @Test
    void refusesTheNextFetchBeforeThePreviousPageIsReleased() {
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final Object firstPage = new Object();
        gauge.beforeFetch();
        gauge.pageFetched(firstPage, 32L);

        assertThrows(IllegalStateException.class, gauge::beforeFetch);

        gauge.pageConsumed(firstPage, 8L);
        gauge.pageReleased(firstPage);
        gauge.beforeFetch();
    }

    @Test
    void matchesTheActivePageByIdentityInsteadOfEquals() {
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final EqualPage fetchedPage = new EqualPage(7);
        final EqualPage equalButDifferentPage = new EqualPage(7);
        gauge.beforeFetch();
        gauge.pageFetched(fetchedPage, 32L);

        assertThrows(
                IllegalArgumentException.class,
                () -> gauge.pageConsumed(equalButDifferentPage, 8L));
        assertThrows(
                IllegalArgumentException.class, () -> gauge.pageReleased(equalButDifferentPage));

        gauge.pageConsumed(fetchedPage, 8L);
        gauge.pageReleased(fetchedPage);
    }

    @Test
    void rejectsOutOfOrderDuplicateAndInvalidEvents() {
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final Object page = new Object();

        assertThrows(IllegalStateException.class, () -> gauge.pageConsumed(page, 8L));
        assertThrows(IllegalStateException.class, () -> gauge.pageReleased(page));
        assertThrows(IllegalArgumentException.class, () -> gauge.pageFetched(page, -1L));

        gauge.pageFetched(page, 16L);
        assertThrows(IllegalStateException.class, () -> gauge.pageFetched(new Object(), 16L));
        gauge.pageConsumed(page, 8L);
        assertThrows(IllegalStateException.class, () -> gauge.pageConsumed(page, 8L));
        gauge.pageReleased(page);
        assertThrows(IllegalStateException.class, () -> gauge.pageReleased(page));
    }

    @Test
    void keepsExecutionWideRetentionIndependentFromInFlightOwnership() {
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final Object page = new Object();
        gauge.beforeFetch();
        gauge.pageFetched(page, 32L);
        gauge.pageConsumed(page, 8L);
        gauge.pageRetained(page);
        gauge.pageReleased(page);

        final MeasurementEvidence whileRetained = gauge.snapshot(MeasurementPlan.graphQl(16));
        assertEquals(0L, whileRetained.finalInFlightPages());
        assertEquals(1L, whileRetained.maxRetainedPages());
        assertEquals(1L, whileRetained.finalRetainedPages());

        gauge.retainedPageReleased(page);
        final MeasurementEvidence afterRetentionRelease =
                gauge.snapshot(MeasurementPlan.graphQl(16));
        assertEquals(0L, afterRetentionRelease.finalInFlightPages());
        assertEquals(1L, afterRetentionRelease.maxRetainedPages());
        assertEquals(0L, afterRetentionRelease.finalRetainedPages());
    }

    @Test
    void keepsOnlyOneActivePageAndConstantSizeCounters() {
        for (final java.lang.reflect.Field field : ManagedPageGauge.class.getDeclaredFields()) {
            final Class<?> type = field.getType();
            if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
                throw new AssertionError("ManagedPageGauge não pode acumular coleções.");
            }
        }
    }

    private record EqualPage(int number) {}
}

package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SequenceMeasurementsTest {
    @Test
    void boundsTraceWhileCountingEveryTransportAndBatchEvent() {
        final var measured = new SequenceMeasurements(AnalyticScenarioObserver.NONE);
        final var lane = measured.forInput(AnalyticScenarioObserver.Input.FRE);
        for (int index = 0; index < 1000; index++) {
            lane.beforeFetch();
            lane.pageBytes(37);
            lane.batchStarted(2);
            lane.batchStaged(2);
        }
        lane.captureClosed();
        final var result = measured.snapshot();
        assertEquals(1000, result.pages());
        assertEquals(37000, result.bytes());
        assertEquals(2000, result.records());
        assertEquals(37, result.maximumBatchFetchedBytes());
        assertTrue(result.samples().size() <= 128);
        assertTrue(result.sampledEvents() > 2000);
        assertTrue(result.maximumHeapBytes() >= result.minimumHeapBytes());
        assertEquals("START", result.samples().get(0).point());
        assertEquals("FINISH", result.samples().get(result.samples().size() - 1).point());
    }

    @Test
    void retainsDelegateFailureAndRejectsUnboundedMeasurementLabels() {
        final var measured =
                new SequenceMeasurements(
                        new AnalyticScenarioObserver() {
                            @Override
                            public void batchStarted(final int records) {
                                throw new IllegalStateException("DELEGATE_BOUND");
                            }
                        });
        assertEquals(
                "DELEGATE_BOUND",
                assertThrows(
                                IllegalStateException.class,
                                () ->
                                        measured.forInput(AnalyticScenarioObserver.Input.COL)
                                                .batchStarted(1))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> measured.sample("unbounded"));
    }
}

package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MeasurementDiagnosticsTest {

    @Test
    void storesJvmHeapAndDurationOnlyAsASeparateDiagnosticEnvelope() {
        final MeasurementDiagnostics diagnostics =
                new MeasurementDiagnostics(1_000L, 1_100L, 1_250L, 75L);

        assertEquals(1_000L, diagnostics.heapBeforeBytes());
        assertEquals(1_100L, diagnostics.heapAfterBytes());
        assertEquals(1_250L, diagnostics.heapPeakBytes());
        assertEquals(75L, diagnostics.durationNanos());
    }

    @Test
    void rejectsNegativeValuesAndAPeakBelowEitherEndpoint() {
        assertThrows(
                IllegalArgumentException.class, () -> new MeasurementDiagnostics(-1L, 0L, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class, () -> new MeasurementDiagnostics(0L, -1L, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class, () -> new MeasurementDiagnostics(0L, 0L, -1L, 0L));
        assertThrows(
                IllegalArgumentException.class, () -> new MeasurementDiagnostics(0L, 0L, 0L, -1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementDiagnostics(100L, 90L, 99L, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementDiagnostics(90L, 100L, 99L, 1L));
    }
}

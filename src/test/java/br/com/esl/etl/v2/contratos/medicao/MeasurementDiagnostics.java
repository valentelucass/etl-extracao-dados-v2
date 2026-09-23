package br.com.esl.etl.v2.contratos.medicao;

/** Diagnóstico variável da JVM, separado da evidência usada como gate estrutural. */
public record MeasurementDiagnostics(
        long heapBeforeBytes, long heapAfterBytes, long heapPeakBytes, long durationNanos) {

    public MeasurementDiagnostics {
        if (heapBeforeBytes < 0L
                || heapAfterBytes < 0L
                || heapPeakBytes < 0L
                || durationNanos < 0L) {
            throw new IllegalArgumentException("Diagnósticos de medição não podem ser negativos.");
        }
        if (heapPeakBytes < heapBeforeBytes || heapPeakBytes < heapAfterBytes) {
            throw new IllegalArgumentException(
                    "O pico de heap não pode ser inferior aos extremos.");
        }
    }
}

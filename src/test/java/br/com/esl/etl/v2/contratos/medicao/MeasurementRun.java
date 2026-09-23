package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Une evidência, diagnóstico e assessment sem misturar seus papéis. */
public record MeasurementRun(
        MeasurementEvidence evidence,
        MeasurementDiagnostics diagnostics,
        MeasurementAssessment assessment) {

    public MeasurementRun {
        Objects.requireNonNull(evidence, "A evidência da execução é obrigatória.");
        Objects.requireNonNull(diagnostics, "O diagnóstico da execução é obrigatório.");
        Objects.requireNonNull(assessment, "O assessment da execução é obrigatório.");
        if (!assessment.equals(new MeasurementEvaluator().evaluate(evidence))) {
            throw new IllegalArgumentException("O assessment diverge da evidência estrutural.");
        }
    }

    public static <P> MeasurementRun execute(
            final MeasurementPlan plan,
            final ManagedPageGauge gauge,
            final MeasurementStreamer<P> streamer,
            final Consumer<? super P> pageConsumer) {
        return execute(
                plan,
                gauge,
                streamer,
                pageConsumer,
                new MeasurementEvaluator(),
                System::nanoTime,
                MeasurementRun::usedHeapBytes);
    }

    static <P> MeasurementRun execute(
            final MeasurementPlan plan,
            final ManagedPageGauge gauge,
            final MeasurementStreamer<P> streamer,
            final Consumer<? super P> pageConsumer,
            final MeasurementEvaluator evaluator,
            final LongSupplier nanoTime,
            final LongSupplier heapUsedBytes) {
        final MeasurementPlan requiredPlan =
                Objects.requireNonNull(plan, "O plano da execução é obrigatório.");
        final ManagedPageGauge requiredGauge =
                Objects.requireNonNull(gauge, "O gauge da execução é obrigatório.");
        final MeasurementStreamer<P> requiredStreamer =
                Objects.requireNonNull(streamer, "O streamer da execução é obrigatório.");
        final Consumer<? super P> requiredConsumer =
                Objects.requireNonNull(pageConsumer, "O consumidor da execução é obrigatório.");
        final MeasurementEvaluator requiredEvaluator =
                Objects.requireNonNull(evaluator, "O evaluator da execução é obrigatório.");
        final LongSupplier requiredNanoTime =
                Objects.requireNonNull(nanoTime, "A fonte de duração é obrigatória.");
        final HeapSampler heapSampler =
                new HeapSampler(
                        Objects.requireNonNull(
                                heapUsedBytes, "A fonte de diagnóstico de heap é obrigatória."));

        final long heapBefore = heapSampler.initialSample();
        final long startedAt = requiredNanoTime.getAsLong();
        requiredStreamer.stream(
                page -> {
                    heapSampler.sample();
                    try {
                        requiredConsumer.accept(page);
                    } finally {
                        heapSampler.sample();
                    }
                });
        final long durationNanos = elapsedNanos(startedAt, requiredNanoTime.getAsLong());
        final long heapAfter = heapSampler.sample();
        final MeasurementEvidence evidence = requiredGauge.snapshot(requiredPlan);
        final MeasurementDiagnostics diagnostics =
                new MeasurementDiagnostics(
                        heapBefore, heapAfter, heapSampler.peakBytes(), durationNanos);
        return new MeasurementRun(evidence, diagnostics, requiredEvaluator.evaluate(evidence));
    }

    private static long usedHeapBytes() {
        final Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static long elapsedNanos(final long startedAt, final long finishedAt) {
        final long elapsed = finishedAt - startedAt;
        return Math.max(0L, elapsed);
    }

    private static final class HeapSampler {

        private final LongSupplier heapUsedBytes;
        private long peakBytes;

        private HeapSampler(final LongSupplier heapUsedBytes) {
            this.heapUsedBytes = heapUsedBytes;
        }

        private long initialSample() {
            final long initial = nonNegativeSample();
            peakBytes = initial;
            return initial;
        }

        private long sample() {
            final long current = nonNegativeSample();
            peakBytes = Math.max(peakBytes, current);
            return current;
        }

        private long peakBytes() {
            return peakBytes;
        }

        private long nonNegativeSample() {
            final long sample = heapUsedBytes.getAsLong();
            if (sample < 0L) {
                throw new IllegalArgumentException("A amostra de heap não pode ser negativa.");
            }
            return sample;
        }
    }
}

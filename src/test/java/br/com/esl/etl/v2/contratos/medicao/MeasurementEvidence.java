package br.com.esl.etl.v2.contratos.medicao;

import java.util.Objects;

/** Evidência estrutural O(1), sem página, payload, cursor ou diagnóstico da JVM. */
public record MeasurementEvidence(
        MeasurementPlan plan,
        long fetchedPages,
        long consumedPages,
        long records,
        long bytes,
        long maxInFlightPages,
        long finalInFlightPages,
        long acquisitions,
        long releases,
        long maxRetainedPages,
        long finalRetainedPages) {

    public MeasurementEvidence {
        Objects.requireNonNull(plan, "O plano da evidência é obrigatório.");
        requireNonNegative("fetchedPages", fetchedPages);
        requireNonNegative("consumedPages", consumedPages);
        requireNonNegative("records", records);
        requireNonNegative("bytes", bytes);
        requireNonNegative("maxInFlightPages", maxInFlightPages);
        requireNonNegative("finalInFlightPages", finalInFlightPages);
        requireNonNegative("acquisitions", acquisitions);
        requireNonNegative("releases", releases);
        requireNonNegative("maxRetainedPages", maxRetainedPages);
        requireNonNegative("finalRetainedPages", finalRetainedPages);
        if (finalInFlightPages > maxInFlightPages) {
            throw new IllegalArgumentException("O estado final em voo excede seu pico.");
        }
        if (finalRetainedPages > maxRetainedPages) {
            throw new IllegalArgumentException("O estado final retido excede seu pico.");
        }
    }

    private static void requireNonNegative(final String field, final long value) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " não pode ser negativo.");
        }
    }
}

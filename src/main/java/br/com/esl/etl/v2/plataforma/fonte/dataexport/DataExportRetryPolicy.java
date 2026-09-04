package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Duration;
import java.util.Objects;

/** Política limitada de retentativa para indisponibilidade transitória da fonte. */
public record DataExportRetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public static final int MAX_ATTEMPTS = 10;
    public static final Duration MAX_DELAY = Duration.ofMinutes(5);
    public static final long MAX_DELAY_MILLIS = MAX_DELAY.toMillis();

    public DataExportRetryPolicy {
        Objects.requireNonNull(initialDelay, "O atraso inicial é obrigatório.");
        Objects.requireNonNull(maxDelay, "O atraso máximo é obrigatório.");
        if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("O número de tentativas está fora do permitido.");
        }
        if (initialDelay.isNegative()
                || maxDelay.isNegative()
                || initialDelay.compareTo(MAX_DELAY) > 0
                || maxDelay.compareTo(MAX_DELAY) > 0
                || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("Os atrasos de retry são inválidos.");
        }
    }

    public Duration delayAfterFailure(final int failedAttempt) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("A tentativa com falha deve ser maior que zero.");
        }
        final int shift = Math.min(failedAttempt - 1, 20);
        final long multiplier = 1L << shift;
        final long initialMillis = initialDelay.toMillis();
        final long cappedMillis;
        try {
            cappedMillis =
                    Math.min(Math.multiplyExact(initialMillis, multiplier), maxDelay.toMillis());
        } catch (final ArithmeticException exception) {
            return maxDelay;
        }
        return Duration.ofMillis(cappedMillis);
    }
}

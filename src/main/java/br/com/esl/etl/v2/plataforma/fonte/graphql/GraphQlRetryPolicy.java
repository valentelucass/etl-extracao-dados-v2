package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.time.Duration;
import java.util.Objects;

/** Retry idempotente limitado; envelope, contrato e paginação inválidos nunca entram aqui. */
public record GraphQlRetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public static final int ABSOLUTE_MAXIMUM_ATTEMPTS = 10;
    public static final Duration ABSOLUTE_MAXIMUM_DELAY = Duration.ofMinutes(5);

    public GraphQlRetryPolicy {
        Objects.requireNonNull(initialDelay, "O atraso inicial é obrigatório.");
        Objects.requireNonNull(maxDelay, "O atraso máximo é obrigatório.");
        if (maxAttempts < 1 || maxAttempts > ABSOLUTE_MAXIMUM_ATTEMPTS) {
            throw new IllegalArgumentException("O número de tentativas GraphQL é inválido.");
        }
        if (initialDelay.isNegative()
                || maxDelay.isNegative()
                || initialDelay.compareTo(maxDelay) > 0
                || maxDelay.compareTo(ABSOLUTE_MAXIMUM_DELAY) > 0) {
            throw new IllegalArgumentException("Os atrasos de retry GraphQL são inválidos.");
        }
    }

    Duration delayAfterFailure(final int failedAttempt, final Duration maximumAllowed) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("A tentativa com falha deve ser positiva.");
        }
        Objects.requireNonNull(maximumAllowed, "O teto compartilhado é obrigatório.");
        final Duration effectiveMaximum =
                maxDelay.compareTo(maximumAllowed) <= 0 ? maxDelay : maximumAllowed;
        final int shift = Math.min(failedAttempt - 1, 20);
        final long multiplier = 1L << shift;
        try {
            return Duration.ofMillis(
                    Math.min(
                            Math.multiplyExact(initialDelay.toMillis(), multiplier),
                            effectiveMaximum.toMillis()));
        } catch (final ArithmeticException exception) {
            return effectiveMaximum;
        }
    }
}

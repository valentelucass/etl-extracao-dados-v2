package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import java.time.Duration;
import java.util.Objects;

/** Limites de abertura e recuperação do circuit breaker por template. */
public record DataExportCircuitBreakerPolicy(int failureThreshold, Duration cooldown) {

    public DataExportCircuitBreakerPolicy {
        Objects.requireNonNull(cooldown, "O período de recuperação do circuito é obrigatório.");
        if (failureThreshold < 1
                || failureThreshold > EslResiliencePolicy.MAX_CIRCUIT_FAILURE_THRESHOLD) {
            throw new IllegalArgumentException(
                    "O limite de falhas do circuito está fora do permitido.");
        }
        if (cooldown.isZero()
                || cooldown.isNegative()
                || cooldown.compareTo(ExecutionDeadlines.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "O período de recuperação do circuito está fora do permitido.");
        }
    }

    public static DataExportCircuitBreakerPolicy from(final EslResiliencePolicy resiliencePolicy) {
        Objects.requireNonNull(resiliencePolicy, "A política ESL é obrigatória.");
        return new DataExportCircuitBreakerPolicy(
                resiliencePolicy.circuitFailureThreshold(), resiliencePolicy.circuitCooldown());
    }
}

package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.time.Duration;
import java.util.Objects;

/** Limites de abertura e recuperação do circuit breaker por template. */
public record DataExportCircuitBreakerPolicy(int failureThreshold, Duration cooldown) {

    public DataExportCircuitBreakerPolicy {
        Objects.requireNonNull(cooldown, "O período de recuperação do circuito é obrigatório.");
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "O limite de falhas do circuito deve ser maior que zero.");
        }
        if (cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException(
                    "O período de recuperação do circuito deve ser positivo.");
        }
    }

    public static DataExportCircuitBreakerPolicy from(final EslResiliencePolicy resiliencePolicy) {
        Objects.requireNonNull(resiliencePolicy, "A política ESL é obrigatória.");
        return new DataExportCircuitBreakerPolicy(
                resiliencePolicy.circuitFailureThreshold(), resiliencePolicy.circuitCooldown());
    }
}

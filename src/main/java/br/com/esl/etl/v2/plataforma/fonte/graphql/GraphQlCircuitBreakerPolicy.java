package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.time.Duration;
import java.util.Objects;

/** Limites de circuito por documento GraphQL fechado. */
public record GraphQlCircuitBreakerPolicy(int failureThreshold, Duration cooldown) {

    public GraphQlCircuitBreakerPolicy {
        Objects.requireNonNull(cooldown, "O cooldown do circuito é obrigatório.");
        if (failureThreshold < 1 || cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException("A política de circuito GraphQL é inválida.");
        }
    }

    public static GraphQlCircuitBreakerPolicy from(final EslResiliencePolicy resiliencePolicy) {
        final EslResiliencePolicy required =
                Objects.requireNonNull(resiliencePolicy, "A política ESL é obrigatória.");
        return new GraphQlCircuitBreakerPolicy(
                required.circuitFailureThreshold(), required.circuitCooldown());
    }
}

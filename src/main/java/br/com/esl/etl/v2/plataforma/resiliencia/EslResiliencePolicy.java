package br.com.esl.etl.v2.plataforma.resiliencia;

import java.time.Duration;
import java.util.Objects;

/** Limites explícitos e conservadores compartilhados por uma instância da origem ESL. */
public record EslResiliencePolicy(
        Duration minimumRequestInterval,
        int maxInFlight,
        int maxRequestsPerCycle,
        int maxRequestsPerWorkload,
        Duration requestTimeout,
        Duration stepTimeout,
        Duration cycleTimeout,
        Duration maxRetryAfter,
        int maxRepartitions,
        int circuitFailureThreshold,
        Duration circuitCooldown) {

    public static final Duration MAX_REQUEST_INTERVAL = Duration.ofMinutes(1);
    public static final int MAX_REQUESTS_PER_CYCLE = 1_000_000;
    public static final int MAX_REQUESTS_PER_WORKLOAD = 100_000;
    public static final int MAX_REPARTITIONS = 32;
    public static final int MAX_CIRCUIT_FAILURE_THRESHOLD = 100;

    public EslResiliencePolicy {
        minimumRequestInterval =
                requireNonNegative(minimumRequestInterval, "O intervalo global é obrigatório.");
        requestTimeout = requirePositive(requestTimeout, "O timeout de request é obrigatório.");
        stepTimeout = requirePositive(stepTimeout, "O timeout de passo é obrigatório.");
        cycleTimeout = requirePositive(cycleTimeout, "O timeout de ciclo é obrigatório.");
        maxRetryAfter = requireNonNegative(maxRetryAfter, "O teto de Retry-After é obrigatório.");
        circuitCooldown = requirePositive(circuitCooldown, "O cooldown do circuito é obrigatório.");
        if (minimumRequestInterval.compareTo(MAX_REQUEST_INTERVAL) > 0) {
            throw new IllegalArgumentException("O intervalo global está fora do permitido.");
        }
        if (maxInFlight != 1) {
            throw new IllegalArgumentException(
                    "A origem ESL deve operar com exatamente uma requisição em voo.");
        }
        if (maxRequestsPerCycle < 1 || maxRequestsPerCycle > MAX_REQUESTS_PER_CYCLE) {
            throw new IllegalArgumentException("O orçamento da origem está fora do permitido.");
        }
        if (maxRequestsPerWorkload < 1
                || maxRequestsPerWorkload > MAX_REQUESTS_PER_WORKLOAD
                || maxRequestsPerWorkload > maxRequestsPerCycle) {
            throw new IllegalArgumentException("O orçamento do workload está fora do permitido.");
        }
        if (requestTimeout.compareTo(stepTimeout) > 0 || stepTimeout.compareTo(cycleTimeout) > 0) {
            throw new IllegalArgumentException(
                    "Os timeouts devem respeitar request <= passo <= ciclo.");
        }
        if (maxRetryAfter.compareTo(stepTimeout) >= 0) {
            throw new IllegalArgumentException(
                    "O teto de Retry-After deve ser menor que o timeout do passo.");
        }
        if (maxRepartitions < 0 || maxRepartitions > MAX_REPARTITIONS) {
            throw new IllegalArgumentException(
                    "O orçamento de reparticionamento está fora do permitido.");
        }
        if (circuitFailureThreshold < 1
                || circuitFailureThreshold > MAX_CIRCUIT_FAILURE_THRESHOLD) {
            throw new IllegalArgumentException("O limite do circuito está fora do permitido.");
        }
        if (circuitCooldown.compareTo(cycleTimeout) > 0) {
            throw new IllegalArgumentException(
                    "O cooldown do circuito não pode exceder o timeout do ciclo.");
        }
    }

    private static Duration requirePositive(final Duration value, final String message) {
        Objects.requireNonNull(value, message);
        if (value.isZero()
                || value.isNegative()
                || value.compareTo(ExecutionDeadlines.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("A duração está fora do limite permitido.");
        }
        return value;
    }

    private static Duration requireNonNegative(final Duration value, final String message) {
        Objects.requireNonNull(value, message);
        if (value.isNegative() || value.compareTo(ExecutionDeadlines.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("A duração está fora do limite permitido.");
        }
        return value;
    }
}

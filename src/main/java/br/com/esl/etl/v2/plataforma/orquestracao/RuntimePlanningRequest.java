package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.SqlText;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Entrada delimitada do planejador para um único ciclo persistível. */
public final class RuntimePlanningRequest {

    private final UUID cycleId;
    private final String environment;
    private final ImmutableFingerprint plan;
    private final Instant plannedAt;
    private final List<RuntimeExecutionRequest> executions;

    public RuntimePlanningRequest(
            final UUID cycleId,
            final String environment,
            final ImmutableFingerprint plan,
            final Instant plannedAt,
            final RuntimeExecutionRequest... executions) {
        this.cycleId = Objects.requireNonNull(cycleId, "O ciclo é obrigatório.");
        this.environment = requiredEnvironment(environment);
        this.plan = Objects.requireNonNull(plan, "O fingerprint do plano é obrigatório.");
        this.plannedAt =
                Objects.requireNonNull(plannedAt, "O instante de planejamento é obrigatório.");
        if (executions == null || executions.length == 0 || executions.length > 64) {
            throw new IllegalArgumentException(
                    "O plano exige entre uma e 64 execuções explícitas.");
        }
        this.executions =
                Arrays.stream(executions)
                        .map(
                                execution ->
                                        Objects.requireNonNull(
                                                execution, "A execução é obrigatória."))
                        .toList();
    }

    public UUID cycleId() {
        return cycleId;
    }

    public String environment() {
        return environment;
    }

    public ImmutableFingerprint plan() {
        return plan;
    }

    public Instant plannedAt() {
        return plannedAt;
    }

    void forEachExecution(final java.util.function.Consumer<RuntimeExecutionRequest> consumer) {
        executions.forEach(Objects.requireNonNull(consumer, "O consumidor é obrigatório."));
    }

    private static String requiredEnvironment(final String value) {
        if (value == null || value.length() > 32) {
            throw new IllegalArgumentException("O ambiente é obrigatório.");
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("O ambiente é obrigatório.");
        }
        return normalized;
    }
}

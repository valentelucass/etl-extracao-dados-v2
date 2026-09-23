package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Plano imutável e com tamanho limitado; consumidores percorrem itens sem receber coleção mutável.
 */
public final class RuntimeExecutionPlan {

    private final UUID cycleId;
    private final String environment;
    private final ImmutableFingerprint fingerprint;
    private final Instant plannedAt;
    private final List<RuntimeExecutionPlanItem> items;

    RuntimeExecutionPlan(
            final UUID cycleId,
            final String environment,
            final ImmutableFingerprint fingerprint,
            final Instant plannedAt,
            final List<RuntimeExecutionPlanItem> items) {
        this.cycleId = Objects.requireNonNull(cycleId, "O ciclo é obrigatório.");
        this.environment = Objects.requireNonNull(environment, "O ambiente é obrigatório.");
        this.fingerprint =
                Objects.requireNonNull(fingerprint, "O fingerprint do plano é obrigatório.");
        this.plannedAt =
                Objects.requireNonNull(plannedAt, "O instante de planejamento é obrigatório.");
        if (items == null || items.isEmpty() || items.size() > 64) {
            throw new IllegalArgumentException(
                    "O plano possui uma quantidade inválida de execuções.");
        }
        this.items = List.copyOf(items);
    }

    public UUID cycleId() {
        return cycleId;
    }

    public String environment() {
        return environment;
    }

    public ImmutableFingerprint fingerprint() {
        return fingerprint;
    }

    public Instant plannedAt() {
        return plannedAt;
    }

    public int executionCount() {
        return items.size();
    }

    public void forEach(final Consumer<RuntimeExecutionPlanItem> consumer) {
        items.forEach(Objects.requireNonNull(consumer, "O consumidor é obrigatório."));
    }
}

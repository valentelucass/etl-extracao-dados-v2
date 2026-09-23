package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.resiliencia.CycleOutcome;
import java.util.Map;
import java.util.Objects;

/** No máximo 64 resumos de workloads; nenhum registro ou chave de negócio é retido. */
public final class RuntimeDispatchResult {
    private final Map<RuntimeWorkloadId, RuntimeExecutionResult> results;
    private final CycleOutcome outcome;

    RuntimeDispatchResult(
            final Map<RuntimeWorkloadId, RuntimeExecutionResult> results,
            final CycleOutcome outcome) {
        if (results.isEmpty() || results.size() > 64) {
            throw new IllegalArgumentException("Quantidade de resultados inválida.");
        }
        this.results = Map.copyOf(results);
        this.outcome = Objects.requireNonNull(outcome, "O agregado é obrigatório.");
    }

    public RuntimeExecutionResult result(final RuntimeWorkloadId id) {
        return Objects.requireNonNull(results.get(id), "Workload ausente do resultado.");
    }

    public CycleOutcome outcome() {
        return outcome;
    }
}

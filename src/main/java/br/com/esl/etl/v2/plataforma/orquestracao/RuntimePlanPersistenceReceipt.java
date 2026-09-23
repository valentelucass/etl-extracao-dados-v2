package br.com.esl.etl.v2.plataforma.orquestracao;

import java.util.Objects;
import java.util.UUID;

/** Resumo sanitizado e O(1) do registro de um plano no control plane. */
public record RuntimePlanPersistenceReceipt(
        UUID cycleId, long recoveredExecutions, int registeredSources, int plannedExecutions) {

    public RuntimePlanPersistenceReceipt {
        cycleId = Objects.requireNonNull(cycleId, "O ciclo é obrigatório.");
        if (recoveredExecutions < 0 || registeredSources < 0 || plannedExecutions <= 0) {
            throw new IllegalArgumentException("O recibo de planejamento é inválido.");
        }
    }
}

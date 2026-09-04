package br.com.esl.etl.v2.plataforma.resiliencia;

import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import java.util.Objects;

/** Agregador limitado: mantém somente contagens e a pior categoria já observada. */
public final class CycleOutcomeAccumulator {

    private long publishedEntities;
    private long degradedEntities;
    private long failedEntities;
    private long blockedEntities;
    private long cancelledEntities;
    private long skippedEntities;
    private RuntimeExitCategory exitCategory = RuntimeExitCategory.SUCCESS;

    public synchronized void recordPublished() {
        publishedEntities = Math.incrementExact(publishedEntities);
    }

    public synchronized void recordDecision(final FailureDecision decision) {
        Objects.requireNonNull(decision, "A decisão de falha é obrigatória.");
        final RuntimeExitCategory combined = exitCategory.combine(decision.exitCategory());
        if (decision.terminalState().isPresent()) {
            increment(decision.terminalState().orElseThrow());
        }
        exitCategory = combined;
    }

    public synchronized CycleOutcome snapshot() {
        return new CycleOutcome(
                publishedEntities,
                degradedEntities,
                failedEntities,
                blockedEntities,
                cancelledEntities,
                skippedEntities,
                overallState(),
                exitCategory);
    }

    private void increment(final ExecutionState state) {
        switch (state) {
            case DEGRADED -> degradedEntities = Math.incrementExact(degradedEntities);
            case FAILED -> failedEntities = Math.incrementExact(failedEntities);
            case BLOCKED -> blockedEntities = Math.incrementExact(blockedEntities);
            case CANCELLED -> cancelledEntities = Math.incrementExact(cancelledEntities);
            case SKIPPED, NOT_APPLICABLE -> skippedEntities = Math.incrementExact(skippedEntities);
            default ->
                    throw new IllegalArgumentException(
                            "A decisão não representa um resultado de falha agregável.");
        }
    }

    private ExecutionState overallState() {
        if (failedEntities > 0 || blockedEntities > 0) {
            return ExecutionState.FAILED;
        }
        if (cancelledEntities > 0) {
            return ExecutionState.CANCELLED;
        }
        if (degradedEntities > 0 || exitCategory == RuntimeExitCategory.DEGRADED) {
            return ExecutionState.DEGRADED;
        }
        if (publishedEntities > 0) {
            return ExecutionState.PUBLISHED;
        }
        if (skippedEntities > 0) {
            return ExecutionState.SKIPPED;
        }
        return ExecutionState.PLANNED;
    }
}
